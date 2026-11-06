package com.tacz.guns.client.sound;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.FileToIdConverter;
import com.tacz.guns.mcshim.PackSource;
import com.tacz.guns.mcshim.ResourceManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 「把枪包里的 {@code assets/**} 接进原版资源包体系」的移植适配层（<b>上游没有这个类</b>，移植特有）。
 *
 * <p>
 * 上游 1.20.1 把每个枪包包装成 {@code PathPackResources} + {@code NamespaceFilteredPackResources}，
 * 经 {@code AddPackFindersEvent} 以 {@code Pack.Position.BOTTOM} 注册进 {@code PackRepository}
 * （参考 tmp/TACZ-1.20.1/.../resource/GunPackLoader.java:93-134），枪包资源对原版资源管理器可见。
 * 1.7.10 没有该入口，{@code GunPackLoader} 只能把枪包包成 {@link PackSource}，而
 * {@code Minecraft#getResourceManager()} 看不到它 —— 那正是 {@code TextureManager#bindTexture} 与
 * {@code SoundManager#getURLForSoundResource} 唯一的数据来源。
 *
 * <p>
 * 做法：① 实现 {@link IResourcePack}，把 {@code assets/<ns>/<path>} 按 {@code <ns>:<path>} 暴露；
 * ② 用 {@code SimpleReloadableResourceManager#reloadResourcePack} 挂载（该实现从后往前找 ⇒ 后挂优先；
 * 本包不存在的路径返回 false，不遮蔽原有资源）；③ 原版每次 {@code refreshResources()} 都会
 * {@code clearResources()} 重建管理器（SimpleReloadableResourceManager.java:89-118）⇒ 通过
 * {@link GunPackResourcePackReloadListener} 在 preInit 注册（排在 {@code TextureManager} 之前），保证贴图
 * stitch 时枪包已在管理器里；另留 {@code SoundLoadEvent} 与每 40 tick 的 {@link #validateInstalled()} 探针兜底；
 * ④ {@link #ensureInstalled()} 比对 namespace 集合，变化就重挂。
 *
 * <p>
 * 优先级：上游是 Position.BOTTOM（最低），本包在 1.7.10 是「后挂 ⇒ 最高」。实测与模组 jar 只在
 * {@code assets/tacz/lang/*} 重叠，而语言文件的上游语义就是「枪包覆盖本体」，
 * {@code LanguageManager#loadLocaleData} 走 {@code getAllResources} 后加载者胜 ⇒ 结果与上游一致。
 *
 * <p>
 * 降级点：只读枪包（{@link ResourceManager#currentPackSources()}）；
 * {@code getPackMetadata}/{@code getPackImage} 返回 null（只被资源包选择界面用到）；索引构建要遍历
 * 枪包 {@code assets/**}，只在首次使用 / 资源重载时发生一次。
 *
 * <p>
 * 与 1.20 pack 系统的核心差异：① {@code reloadResourcePack} 只追加包、不通知 reload listener，故本类
 * <b>不</b>调 {@code refreshResources()}（会先 clearResources 把本包清掉，形成重载循环）；
 * ② 枪包贴图是 {@code SimpleTexture} 独立贴图而非图集 sprite，首次 bindTexture 时懒加载；
 * ③ missing texture 会被 {@code TextureManager#loadTexture} 永久缓存 ⇒ 重挂后
 * {@link #scheduleTextureReload} 延迟一 tick 调 {@code onResourceManagerReload} 重试；
 * ④ {@code SimpleTexture} 缺省 {@code GL_REPEAT}，1.20 默认 {@code GL_CLAMP_TO_EDGE} ⇒ 见
 * {@link #SYNTHESIZED_TEXTURE_METADATA}。
 */
@SideOnly(Side.CLIENT)
public final class GunPackResourcePack implements IResourcePack {

    /** 枪包内音效目录（相对 {@code assets/<namespace>/}），与上游 {@code FileToIdConverter("tacz_sounds", ".ogg")} 一致。 */
    public static final String SOUND_DIRECTORY = "tacz_sounds";

    private static final FileToIdConverter TACZ_SOUND_LISTER = new FileToIdConverter(SOUND_DIRECTORY, ".ogg");
    private static final String ASSETS_PREFIX = "assets/";
    /** 本模组自己的 namespace：即使枪包里一个文件都没有，也把它声明成「有内容」，避免空包。 */
    private static final String DEFAULT_DOMAIN = GunMod.MOD_ID;

    /** 1.7.10 贴图元数据后缀（{@code SimpleTexture#loadTexture} 从同名 {@code .mcmeta} 读 clamp/blur）。 */
    private static final String METADATA_SUFFIX = ".mcmeta";
    /**
     * 为枪包贴图<b>合成</b>的 {@code .mcmeta}。
     *
     * <p>
     * TODO(L2) 移植特有：1.20 默认 {@code GL_CLAMP_TO_EDGE}；1.7.10 的
     * {@code TextureUtil#uploadTextureImageAllocate(..., clamp=false)} 设成 {@code GL_REPEAT}，
     * 枪包贴图「零厚度面 + 边缘一像素」会采到对侧像素（模型边缘杂色亮线）。
     * 枪包自身不带 {@code .mcmeta}，故在 {@link #getInputStream} 里合成一份；包内自带者优先。
     */
    private static final byte[] SYNTHESIZED_TEXTURE_METADATA = "{\"texture\":{\"blur\":false,\"clamp\":true}}"
        .getBytes(StandardCharsets.US_ASCII);
    /** 只给图片合成元数据。 */
    private static final String PNG_SUFFIX = ".png";

    private static final GunPackResourcePack INSTANCE = new GunPackResourcePack();

    /**
     * Forge 事件总线处理器单例（见 {@link #registerBusOnce()}）。
     *
     * <p>
     * 必须是<b>实例</b>：FML 1.7.10 的 {@code ASMEventHandler} 固定用 {@code INVOKEVIRTUAL} 调用目标方法
     * （ASMEventHandler:115），静态 {@code @SubscribeEvent} 会 {@code IncompatibleClassChangeError}。
     */
    private static final SoundLoadHandler SOUND_LOAD_HANDLER = new SoundLoadHandler();

    /** 已挂到原版资源管理器上的 namespace 快照（{@code null} 表示还没挂）。 */
    @Nullable
    private static Set<String> installedDomains;
    /** 是否已把 {@link #onSoundLoad} 注册到 Forge 事件总线。 */
    private static boolean busRegistered;
    /**
     * 是否已把 {@link #RELOAD_LISTENER} 注册进 {@code IReloadableResourceManager}（见 {@link #registerReloadListenerOnce()}）。
     */
    private static boolean reloadListenerRegistered;
    /** 资源重载监听器单例（主路径：每轮原版资源重载都重挂本包）。 */
    private static final GunPackResourcePackReloadListener RELOAD_LISTENER = new GunPackResourcePackReloadListener();
    /** 监听器是否在原版图集 stitch <b>之后</b>才注册（见 {@link #registerReloadListenerOnce()} 的时机自检）。 */
    private static boolean registeredAfterAtlasCreation;
    /** 枪包资源索引，{@code null} 表示待重建。 */
    @Nullable
    private static PackIndex packIndex;
    /** 自检探针（从索引里挑的第一个 {@code .png}），{@code null} 表示枪包内没有贴图。 */
    @Nullable
    private static ResourceLocation probeLocation;
    /** 自检计时器（见 {@link TickHandler#onClientTick}）。 */
    private static int tickCounter;
    /** 已就「挂载失效」告警过的次数上限内的标记，避免刷屏。 */
    private static boolean selfHealWarned;

    private GunPackResourcePack() {}

    // ================================================================================================
    // 对外接口
    // ================================================================================================

    /**
     * 对应上游 {@code SoundPlayManager#hasTaczSoundResource(Minecraft, ResourceLocation)}：
     * 判断枪包里是否存在该音效 id 对应的 {@code tacz_sounds/<id>.ogg}。
     *
     * @param soundId 逻辑音效 id（例如 {@code tacz:aa12/aa12_draw}），namespace 原样保留
     */
    public static boolean hasGunSoundResource(ResourceLocation soundId) {
        return index().files()
            .contains(TACZ_SOUND_LISTER.idToFile(soundId));
    }

    /**
     * 逻辑音效 id → 枪包内 ogg 的资源位置
     * （例如 {@code tacz:aa12/aa12_draw} → {@code tacz:tacz_sounds/aa12/aa12_draw.ogg}）。
     */
    public static ResourceLocation gunSoundFile(ResourceLocation soundId) {
        return TACZ_SOUND_LISTER.idToFile(soundId);
    }

    /** 枪包重载后丢弃索引（由 {@link SoundPlayManager#clearSoundResourceCache()} 调用）。 */
    public static void invalidateCache() {
        packIndex = null;
        // 探针与自愈告警也必须跟着换：换包后旧的探针贴图可能已不存在，
        // 否则 validateInstalled() 会永远判定「失效」并每 40 tick 重挂一次。
        probeLocation = null;
        selfHealWarned = false;
    }

    /**
     * 确保本资源包已经以「当前索引到的全部 namespace」挂进原版资源管理器（幂等、只比对 namespace 集合）。
     *
     * <p>
     * <b>必须在 preInit 调用</b>：{@code Minecraft.java:519} 已创建 {@code mcResourceManager}，而
     * {@code :524} 才注册 {@code TextureManager}；preInit 落在 {@code :522} → {@code FMLClientHandler.java:208}，
     * 这样首次调用里注册的 {@link GunPackResourcePackReloadListener} 才能排到 {@code TextureManager} 之前，
     * 贴图 stitch 那一刻枪包已在管理器里。之后每次枪包重载再调一次。
     */
    public static void ensureInstalled() {
        registerBusOnce();
        // 关键：把「每轮资源重载都重挂」的监听器装进 IReloadableResourceManager。
        // 必须在这里（preInit）做，理由见 GunPackResourcePackReloadListener 的类注释。
        registerReloadListenerOnce();
        Set<String> domains = index().domains();
        if (installedDomains == null || !installedDomains.containsAll(domains)) {
            install(domains);
        }
        // 补救：若首次调用晚于原版图集 stitch（宿主把本方法放在 init 而非 preInit），这次 install 只是
        // 「补挂」⇒ 排一次 TextureManager 重载，让贴图重试。放在 install 之后且不受上面的短路影响。
        if (registeredAfterAtlasCreation) {
            registeredAfterAtlasCreation = false;
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null) {
                scheduleTextureReload(minecraft);
            }
        }
    }

    /**
     * 把 {@link GunPackResourcePackReloadListener} 注册进 {@code IReloadableResourceManager}（只做一次）。
     *
     * <p>
     * <b>为什么这是主路径而不是 SoundLoadEvent</b>：{@code reloadResources} 先 {@code clearResources()}
     * 再只重挂传入的包列表（SimpleReloadableResourceManager.java:89-118），而
     * {@code Minecraft#refreshResources()} 的列表里没有本包（Minecraft.java:636-654）⇒ 每轮原版重载都会
     * 把我们清掉。监听器里重挂才是上游「pack 在资源重载中始终挂载」的语义。
     *
     * <p>
     * {@code registerReloadListener} 会<b>同步立刻回调一次</b>（SimpleReloadableResourceManager.java:125-132）；
     * 本方法先把 {@code reloadListenerRegistered} 置真再注册，回调里再走到这里会直接返回，不会递归。
     */
    private static void registerReloadListenerOnce() {
        if (reloadListenerRegistered) {
            return;
        }
        reloadListenerRegistered = true;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            // 客户端还没起来：允许下次重试
            reloadListenerRegistered = false;
            return;
        }
        IResourceManager resourceManager = minecraft.getResourceManager();
        if (!(resourceManager instanceof IReloadableResourceManager)) {
            // TODO(L2): 1.20 的 ResourceManager 本身就是可重载的；换实现时枪包资源不随资源重载存活，
            // 只能退回 SoundLoadEvent + 40 tick 自愈两条兜底。
            reloadListenerRegistered = false;
            GunMod.LOGGER.warn(
                "[TACZ ResourcePack] ResourceManager is not IReloadableResourceManager ({}), "
                    + "gun pack assets will not survive a vanilla resource reload",
                resourceManager == null ? "null"
                    : resourceManager.getClass()
                        .getName());
            return;
        }
        try {
            ((IReloadableResourceManager) resourceManager).registerReloadListener(RELOAD_LISTENER);
            // 时机自检：{@code Minecraft.java:585} 才创建 textureMapBlocks，{@code :590/:592} 立刻 stitch。
            // 若非 null 说明本方法晚于图集 stitch（宿主把 ensureInstalled() 放在了 init），只能靠末尾那次贴图重载补救。
            if (minecraft.getTextureMapBlocks() != null) {
                registeredAfterAtlasCreation = true;
                GunMod.LOGGER.warn(
                    "[TACZ ResourcePack] gun pack reload listener registered AFTER the vanilla atlas was stitched "
                        + "(Minecraft.java:585-592); gun pack textures will be one reload late. "
                        + "Move GunPackResourcePack.ensureInstalled() to ClientProxy#preInit "
                        + "(it must run between Minecraft.java:522 and :585).");
            } else {
                GunMod.LOGGER.info("[TACZ ResourcePack] registered gun pack reload listener");
            }
        } catch (RuntimeException e) {
            reloadListenerRegistered = false;
            GunMod.LOGGER.warn("[TACZ ResourcePack] failed to register gun pack reload listener", e);
        }
    }

    /**
     * 资源重载回调：强制重挂（{@link GunPackResourcePackReloadListener} 调）。
     *
     * <p>
     * <b>必须幂等且可重入</b>：{@code registerReloadListener} 会同步立刻回调一次
     * （SimpleReloadableResourceManager.java:125-132），之后每轮 {@code reloadResources} 的
     * {@code notifyReloadListeners()} 再各回调一次。先把 {@link #installedDomains} 置 {@code null} 再调
     * {@link #ensureInstalled()}：此刻 domain managers 刚被 {@code clearResources()} 清空
     * （:89-97），旧快照已失效，必须走「重挂」分支。递归不会发生（由 {@link #reloadListenerRegistered} 守住）。
     *
     * <p>
     * <b>不要</b>在这里调 {@code Minecraft#refreshResources()} 或 {@code ClientAssetsManager#reloadAllPack()}：
     * 前者在 {@code notifyReloadListeners()} 过程中再次 {@code clearResources()}（递归重载），后者是另一条
     * 独立的枪包数据重载链（{@code ClientGunPackReloader}，接在 postInit）。本回调只管「资源包挂载」。
     *
     * <p>
     * {@code installedDomains} 已置空 ⇒ {@link #install(Set)} 里 {@code wasInstalled} 为假，不会在重载
     * 通知链里再叠一层 {@code ProgressManager} 进度条。
     */
    static void onResourceManagerReload(IResourceManager resourceManager) {
        if (resourceManager == null) {
            return;
        }
        // 上一轮挂载记录作废：domain 管理器已被 clearResources() 清空
        installedDomains = null;
        ensureInstalled();
    }

    /**
     * 自检 + 自愈（每 40 tick，仅在进入世界后）：原版每次资源重载都会重建 domain 管理器并丢掉挂上的包。
     * 正常路径靠 {@link GunPackResourcePackReloadListener} 与 {@link #onSoundLoad} 两条重挂链，但它们都依赖
     * 「有人通知」。这里补一层不信任何事件、只信资源管理器的校验：拿索引里第一张 {@code .png} 当探针，
     * 读不到就判定挂载失效，重新挂载并安排一次贴图重载。
     */
    public static void validateInstalled() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null) {
            return;
        }
        ResourceLocation probe = probeLocation();
        if (probe == null) {
            // 枪包里一张贴图都没有：没有可校验的对象（发不出声/贴图本来就不该存在）
            return;
        }
        IResourceManager resourceManager = minecraft.getResourceManager();
        if (resourceManager == null) {
            return;
        }
        boolean present;
        try {
            resourceManager.getResource(probe);
            present = true;
        } catch (IOException | RuntimeException e) {
            present = false;
        }
        if (present) {
            // 恢复正常后允许下一次失效再告警一次
            selfHealWarned = false;
            return;
        }
        if (!selfHealWarned) {
            selfHealWarned = true;
            GunMod.LOGGER.warn(
                "[TACZ ResourcePack] gun pack resource is not reachable ({}), re-installing the resource pack. "
                    + "Gun pack textures/sounds were probably dropped by a resource reload.",
                probe);
        }
        installedDomains = null;
        install(index().domains());
    }

    /** 从索引里挑一个稳定的探针（第一个 {@code .png}）。 */
    @Nullable
    private static ResourceLocation probeLocation() {
        ResourceLocation cached = probeLocation;
        if (cached != null) {
            return cached;
        }
        for (ResourceLocation location : index().files()) {
            if (location.getResourcePath()
                .endsWith(PNG_SUFFIX)) {
                probeLocation = location;
                return location;
            }
        }
        return null;
    }

    /**
     * FML 总线上的自检 tick（{@code TickEvent.ClientTickEvent} 只 post 到 FML 总线）。
     *
     * <p>
     * 必须**实例**方法 + {@code @SubscribeEvent}，见 {@link #SOUND_LOAD_HANDLER} 的说明。
     */
    public static final class TickHandler {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            tickCounter++;
            if (tickCounter % 40 == 0) {
                validateInstalled();
            }
        }
    }

    private static final TickHandler TICK_HANDLER = new TickHandler();

    // ================================================================================================
    // 挂载
    // ================================================================================================

    private static void registerBusOnce() {
        if (busRegistered) {
            return;
        }
        busRegistered = true;
        try {
            MinecraftForge.EVENT_BUS.register(SOUND_LOAD_HANDLER);
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn(
                "[TACZ ResourcePack] Failed to register SoundLoadEvent handler; gun pack assets will not survive a resource reload",
                e);
        }
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(TICK_HANDLER);
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ ResourcePack] Failed to register self-healing tick handler", e);
        }
    }

    /**
     * 对应 {@code SoundHandler#onResourceManagerReload} 里的 {@code sndManager.reloadSoundSystem()}：
     * 原版资源重载后重新挂载本包（旧挂载已随 domain 管理器重建失效）。
     * 由 {@link SoundLoadHandler} 调用，本方法自身<b>不加</b> {@code @SubscribeEvent}（原因见 {@link #SOUND_LOAD_HANDLER}）。
     */
    public static void onSoundLoad(SoundLoadEvent event) {
        // 上一轮挂载的记录作废：domain 管理器已被 SimpleReloadableResourceManager 重建
        installedDomains = null;
        invalidateCache();
        install(index().domains());
    }

    /** 见 {@link #SOUND_LOAD_HANDLER}。 */
    public static final class SoundLoadHandler {

        @SubscribeEvent
        public void onSoundLoad(SoundLoadEvent event) {
            GunPackResourcePack.onSoundLoad(event);
        }
    }

    private static void install(Set<String> domains) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        IResourceManager resourceManager = minecraft.getResourceManager();
        if (!(resourceManager instanceof SimpleReloadableResourceManager)) {
            // TODO(L2): 上游没有这一步（1.20 的 ResourceManager 本身就是 MultiPackResourceManager）。
            // 1.7.10 只有 SimpleReloadableResourceManager 提供 reloadResourcePack(IResourcePack)。
            GunMod.LOGGER.warn(
                "[TACZ ResourcePack] ResourceManager is not SimpleReloadableResourceManager ({}), gun pack assets will not load.",
                resourceManager == null ? "null"
                    : resourceManager.getClass()
                        .getName());
            return;
        }
        boolean wasInstalled = installedDomains != null;
        ((SimpleReloadableResourceManager) resourceManager).reloadResourcePack(INSTANCE);
        installedDomains = domains;
        logDiagnostics(resourceManager, wasInstalled);
        // 重挂时之前被 TextureManager 缓存成 missing texture（紫黑块）的贴图不会自己重试，必须重载一遍。
        // 只在「已经进游戏」时做：启动期贴图尚未绑定，没必要弹进度条。
        if (wasInstalled && minecraft.theWorld != null) {
            scheduleTextureReload(minecraft);
        }
    }

    /**
     * 打出一次「枪包资源是否真的可读」的证据日志（只在挂载时打，不刷屏）：
     * 对模型贴图 / 其 {@code .mcmeta} / 枪包 ogg / 模组自身贴图各探一次。
     */
    private static void logDiagnostics(IResourceManager resourceManager, boolean reinstall) {
        try {
            List<PackSource> sources = ResourceManager.currentPackSources();
            StringBuilder names = new StringBuilder();
            for (PackSource source : sources) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(source.getName());
            }
            PackIndex current = index();
            GunMod.LOGGER.info(
                "[TACZ ResourcePack] {} gun pack resource pack: packs={} [{}] domains={} indexedFiles={}",
                reinstall ? "Re-installed" : "Installed",
                sources.size(),
                names,
                current.domains(),
                current.files()
                    .size());
            if (sources.isEmpty()) {
                GunMod.LOGGER.warn(
                    "[TACZ ResourcePack] No gun pack source found -> gun pack textures/sounds/models CANNOT load. "
                        + "Check that <gamedir>/tacz/tacz_default_gun exists.");
            }
            probe(resourceManager, "textures/gun/uv/ak47.png");
            probe(resourceManager, "textures/gun/uv/ak47.png.mcmeta");
            probe(resourceManager, "tacz_sounds/dry_fire.ogg");
            probe(resourceManager, "textures/items/ammo_box.png");
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ ResourcePack] diagnostics failed", e);
        }
    }

    /** 见 {@link #logDiagnostics}。 */
    private static void probe(IResourceManager resourceManager, String path) {
        ResourceLocation location = new ResourceLocation(GunMod.MOD_ID, path);
        try {
            resourceManager.getResource(location);
            GunMod.LOGGER.info("[TACZ ResourcePack] probe HIT  {}", location);
        } catch (IOException | RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ ResourcePack] probe MISS {}", location);
        }
    }

    /**
     * 让 {@code TextureManager} 把所有已缓存贴图重新 {@code loadTexture} 一次
     * （{@code TextureManager#onResourceManagerReload} 是 1.7.10 唯一的公开入口）。
     * 延迟到下一 tick：避免在 listener 通知过程中嵌套 {@code ProgressManager} 进度条，也避免重入 install 链。
     */
    private static void scheduleTextureReload(Minecraft minecraft) {
        try {
            minecraft.func_152344_a(() -> {
                Minecraft client = Minecraft.getMinecraft();
                if (client == null || client.getTextureManager() == null) {
                    return;
                }
                IResourceManager resourceManager = client.getResourceManager();
                if (resourceManager == null) {
                    return;
                }
                GunMod.LOGGER.info("[TACZ ResourcePack] reloading cached textures so gun pack textures are retried");
                client.getTextureManager()
                    .onResourceManagerReload(resourceManager);
            });
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ ResourcePack] failed to schedule texture reload", e);
        }
    }

    // ================================================================================================
    // 枪包资源索引
    // ================================================================================================

    /**
     * 建立「枪包内全部 {@code assets/<ns>/**}」的索引。数据源与 {@code client/resource/**} 一致
     * （{@link ResourceManager#currentPackSources()}，由 {@code GunPackLoader} 注入），不会出现第二套扫描逻辑。
     */
    private static PackIndex index() {
        PackIndex cached = packIndex;
        if (cached != null) {
            return cached;
        }
        Set<ResourceLocation> files = new LinkedHashSet<>();
        Set<String> domains = new LinkedHashSet<>();
        domains.add(DEFAULT_DOMAIN);
        for (PackSource source : ResourceManager.currentPackSources()) {
            for (String path : source.listFiles(ASSETS_PREFIX)) {
                ResourceLocation id = toResourceId(path);
                if (id == null) {
                    continue;
                }
                if (files.add(id)) {
                    domains.add(id.getResourceDomain());
                }
            }
        }
        PackIndex index = new PackIndex(files, domains);
        packIndex = index;
        return index;
    }

    /**
     * 包内路径 → 资源 id（{@code assets/<ns>/<path>} → {@code <ns>:<path>}）。
     *
     * @return 不是 {@code assets/<ns>/<path>} 时返回 {@code null}
     */
    @Nullable
    private static ResourceLocation toResourceId(String path) {
        if (!path.startsWith(ASSETS_PREFIX)) {
            return null;
        }
        String rest = path.substring(ASSETS_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return null;
        }
        String namespace = rest.substring(0, slash);
        String resourcePath = rest.substring(slash + 1);
        if (namespace.isEmpty() || resourcePath.isEmpty()) {
            return null;
        }
        return new ResourceLocation(namespace, resourcePath);
    }

    /** 见 {@link #index()}。 */
    private record PackIndex(Set<ResourceLocation> files, Set<String> domains) {}

    // ================================================================================================
    // IResourcePack
    // ================================================================================================

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        // 枪包贴图的「合成 .mcmeta」：见 SYNTHESIZED_TEXTURE_METADATA。包内自带真实 .mcmeta 时走下方正常路径。
        if (isSynthesizedMetadata(location)) {
            return new ByteArrayInputStream(SYNTHESIZED_TEXTURE_METADATA);
        }
        String path = ASSETS_PREFIX + location.getResourceDomain() + "/" + location.getResourcePath();
        List<PackSource> sources = ResourceManager.currentPackSources();
        // 与 GunPackResources 的优先级一致：后扫到的枪包覆盖先扫到的，所以从后往前找。
        for (int i = sources.size() - 1; i >= 0; i--) {
            InputStream stream = sources.get(i)
                .open(path);
            if (stream != null) {
                return stream;
            }
        }
        throw new FileNotFoundException(location.toString());
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        if (location == null) {
            return false;
        }
        String path = location.getResourcePath();
        if (path.isEmpty()) {
            return false;
        }
        PackIndex current = index();
        if (path.endsWith(METADATA_SUFFIX)) {
            // 真实的 .mcmeta 优先；没有就为枪包 .png 合成一份（clamp + no blur）
            return current.files()
                .contains(location) || isSynthesizedMetadata(location);
        }
        return current.files()
            .contains(location);
    }

    /**
     * 是否该为这个 {@code .mcmeta} 路径合成元数据（包内没有真实文件、且同名 {@code .png} 是枪包贴图）。
     */
    private static boolean isSynthesizedMetadata(ResourceLocation location) {
        String path = location.getResourcePath();
        if (!path.endsWith(METADATA_SUFFIX)) {
            return false;
        }
        String basePath = path.substring(0, path.length() - METADATA_SUFFIX.length());
        if (!basePath.endsWith(PNG_SUFFIX)) {
            return false;
        }
        ResourceLocation base = new ResourceLocation(location.getResourceDomain(), basePath);
        PackIndex current = index();
        return current.files()
            .contains(base)
            && !current.files()
                .contains(location);
    }

    @Override
    public Set<String> getResourceDomains() {
        return Collections.unmodifiableSet(index().domains());
    }

    /** 不参与资源包选择界面，因此没有 pack.mcmeta。 */
    @Override
    public @Nullable IMetadataSection getPackMetadata(IMetadataSerializer serializer, String packFormat)
        throws IOException {
        return null;
    }

    /** 不参与资源包选择界面，因此没有图标。 */
    @Override
    public @Nullable BufferedImage getPackImage() throws IOException {
        return null;
    }

    @Override
    public String getPackName() {
        return "TACZ Gun Packs";
    }
}
