package com.tacz.guns.client;

import java.io.IOException;
import java.lang.reflect.Constructor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.CommonProxy;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.init.ClientSetupEvent;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.CrawlKey;
import com.tacz.guns.client.input.GunInputHandler;
import com.tacz.guns.client.input.ReloadKey;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.particle.AmmoParticleSpawner;
import com.tacz.guns.client.particle.HitBlockEffectHandler;
import com.tacz.guns.client.renderer.item.ScreenItemVboContext;
import com.tacz.guns.client.resource.ClientGunPackReloader;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.sound.GunPackResourcePack;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.command.sub.ConvertCommand;
import com.tacz.guns.command.sub.ReloadCommand;
import com.tacz.guns.config.ConfigManager;
import com.tacz.guns.entity.EntityBulletClientHooks;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.init.CompatRegistry;
import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.message.ServerMessageAmmoStorageContents;
import com.tacz.guns.network.message.ServerMessageBulletHitBlock;
import com.tacz.guns.network.message.ServerMessageCraft;
import com.tacz.guns.network.message.ServerMessageGunUnload;
import com.tacz.guns.network.message.ServerMessageRefreshRefitScreen;
import com.tacz.guns.network.message.ServerMessageSound;
import com.tacz.guns.network.message.ServerMessageSwapItem;
import com.tacz.guns.network.message.ServerMessageSyncBaseTimestamp;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.PackConvertor;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端代理（{@code @SidedProxy(clientSide = "com.tacz.guns.client.ClientProxy")}）。
 *
 * <p>
 * 对应上游 1.20.1 的「客户端专属注册」全集：{@code client/init/ClientSetupEvent}（MOD 总线的 7 个事件）、
 * {@code @Mod.EventBusSubscriber(CLIENT)} 的各类事件处理类、以及
 * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} 的分端分发。
 *
 * <p>
 * 1.7.10 的三条硬性事实决定了本类的形态：① <b>事件总线只有两条</b> ——
 * {@code cpw.mods.fml.common.gameevent.TickEvent.*} / {@code cpw PlayerEvent.*} / {@code FMLNetworkEvent.*} 走
 * {@code FMLCommonHandler.instance().bus()}，{@code net.minecraftforge.client.event.*} /
 * {@code net.minecraftforge.event.entity.*} 走 {@code MinecraftForge.EVENT_BUS}；
 * ② <b>注册必须按实例</b> —— {@code EventBus#register(Object)} 用 {@code target.getClass()} 反射找
 * {@code @SubscribeEvent} 方法，传 {@code Xxx.class} 会去找 {@code java.lang.Class} 上的方法 ⇒ 什么都不会注册，
 * 故自带 {@code INSTANCE} 的类直接注册实例、只暴露静态处理方法的类由
 * {@link #registerBusHandler(String, boolean)} 反射构造实例再注册；
 * ③ <b>没有 {@code DistExecutor}</b> —— common 侧（{@code network/message/**}）只依赖 {@link ClientSideHooks}
 * 接口，本类在 {@code preInit} 注入实现（{@code Hooks}），从而保持「common 不 import client」的编译边界。
 *
 * <p>
 * L1 边界：{@code CommonProxy} 与本类之间只有继承关系；{@code @SidedProxy} 用字符串引用本类，因此本类可以在客户端包
 * 被临时排除出编译时不影响 common 侧。
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    /**
     * 需要挂到 {@code FMLCommonHandler.instance().bus()} 的客户端事件类。
     *
     * <p>
     * 判定依据：类里订阅的是 {@code cpw.mods.fml.common.gameevent.TickEvent.*} /
     * {@code cpw PlayerEvent.*} / {@code FMLNetworkEvent.*}（这三族事件只 post 到 FML 总线）。
     * 清单由 {@code client-event} 工作流定稿（每个类都提供 public 无参构造）。
     */
    private static final String[] FML_BUS_CLASSES = {
        // FMLNetworkEvent.ClientDisconnectionFromServerEvent
        "com.tacz.guns.client.event.ClientGunTimeResetEvent",
        // FMLNetworkEvent.ClientConnectedToServerEvent + ClientDisconnectionFromServerEvent
        "com.tacz.guns.client.event.CommonNetworkCacheEvent",
        // cpw PlayerEvent.PlayerLoggedInEvent
        "com.tacz.guns.client.event.PlayerEnterWorld",
        // TickEvent.ClientTickEvent
        "com.tacz.guns.client.event.RefreshClonePlayerDataEvent",
        // TickEvent.ClientTickEvent + TickEvent.RenderTickEvent
        "com.tacz.guns.client.event.TickAnimationEvent",
        // TickEvent.ClientTickEvent（替代 1.7.10 不存在的 InputEvent，做使用键按下沿检测）
        "com.tacz.guns.client.event.GunDisplayCaseClientInteractionEvent", };

    /**
     * 需要同时挂到<b>两条</b>总线的客户端事件类（类里既有 Forge 事件、又有 cpw Tick 事件）。
     *
     * <p>
     * 双总线注册是安全的：两条 {@code EventBus} 实例彼此独立，各自只回调自己注册的 listener，
     * 事件只 post 到其中一条总线，不会重复触发。
     */
    private static final String[] BOTH_BUS_CLASSES = {
        // Forge: GunFireEvent / BeforeRenderHandEvent / FOVUpdateEvent；FML: TickEvent.RenderTickEvent
        "com.tacz.guns.client.event.CameraSetupEvent",
        // Forge: SwapItemWithOffHand；FML: TickEvent.ClientTickEvent
        "com.tacz.guns.client.event.InventoryEvent",
        // Forge: RenderGameOverlayEvent.Pre(CROSSHAIRS)；FML: TickEvent.RenderTickEvent
        "com.tacz.guns.client.event.RenderCrosshairEvent", };

    /**
     * 需要挂到 {@code MinecraftForge.EVENT_BUS} 的客户端事件类。
     *
     * <p>
     * 判定依据：类里订阅的是 {@code net.minecraftforge.client.event.*} /
     * {@code net.minecraftforge.event.entity.*} 或 TACZ 自己的 {@code api.event.common.*}（都 post 到 Forge 总线）。
     * 缺失的类会被 {@link #registerBusHandler} 静默跳过并打 warn，
     * <b>不会让 runClient 起不来</b>（这是 1.7.10 侧替代「MOD 总线自动发现」的必要代价）。
     */
    private static final String[] FORGE_BUS_CLASSES = {
        // EntityHurtByGunEvent.Post / EntityKillByGunEvent
        "com.tacz.guns.client.event.ClientHitMark",
        // PlayerInteractEvent（只挂 Forge 总线）
        "com.tacz.guns.client.event.ClientPreventGunClick",
        // RenderHandEvent（上游本就注释掉，注册也无害）
        "com.tacz.guns.client.event.FirstPersonRenderEvent",
        // GunFireEvent / RenderItemInHandBobEvent.BobView
        "com.tacz.guns.client.event.FirstPersonRenderGunEvent",
        // DrawBlockHighlightEvent
        "com.tacz.guns.client.event.GunDisplayCaseHighlightEvent",
        "com.tacz.guns.client.event.GunPegboardHighlightEvent",
        // EntityHurtByGunEvent.Post
        "com.tacz.guns.client.event.PlayerHurtByGunEvent",
        // RenderGameOverlayEvent.Pre（ElementType.HOTBAR）
        "com.tacz.guns.client.event.PreventsHotbarEvent",
        // TextureStitchEvent.Post
        "com.tacz.guns.client.event.ReloadResourceEvent",
        // RenderLivingEvent.Post
        "com.tacz.guns.client.event.RenderHeadShotAABB",
        // ItemTooltipEvent
        "com.tacz.guns.client.event.TooltipEvent", };

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // 1) CLIENT 配置（上游 registerConfig(ModConfig.Type.CLIENT, ...)）
        ConfigManager.initClient(event.getModConfigurationDirectory());

        // 2) 向 common 侧注入客户端钩子（1.7.10 版 DistExecutor）
        ClientSideHooks.set(new Hooks());

        // 3) 把枪包 assets 挂成 IResourcePack（**必须早于任何贴图 stitch，也早于 TextureManager 重载监听器注册**）
        //
        // 1.20 的枪包靠 Forge 的 AddPackFindersEvent / PackRepository 暴露给 Minecraft#getResourceManager；
        // 1.7.10 没有资源包自动发现，SimpleReloadableResourceManager 只认显式 registerResourcePack 进去的包
        // → 枪包内的贴图 / 粒子图 / 模型贴图在 1.7.10 会**全部解析失败**（getResource 返回 null）。
        // GunPackResourcePack.ensureInstalled() 就是把 GunPackLoader 扫出来的枪包挂进
        // Minecraft#getResourceManager()，内部会比对已挂载列表，重复调用安全。
        //
        // 1.7.10 时序（为什么必须在 preInit 而不是 init）：
        // FMLClientHandler.beginMinecraftLoading 在 preInit（Loader.preinitializeMods）→ Minecraft.startGame
        // 里才注册 TextureManager/SoundHandler 等重载监听器（Minecraft.java:524/528）。而
        // SimpleReloadableResourceManager#reloadResources() 每次都先 clearResources() 再按「传入包列表」重挂，
        // 该列表不含本资源包（Minecraft.java:636-654）→ 只要重载发生时本包还没挂上，贴图 stitch 就会缺图；
        // 首次重载发生在 FMLClientHandler.finishMinecraftLoading:327。故本包必须挂进「重载监听器注册之前」，
        // 并在每次重载前由监听器重挂（见 GunPackResourcePack 的重载钩子）。
        // 数据来源已就绪：super.preInit → CommonProxy.preInit 里已调 GunPackLoader.INSTANCE.reloadPackList()。
        GunPackResourcePack.ensureInstalled();

        // 4) GTNHLib JSON 模型系统：登记本模组的 modid，允许它在资源重载时扫描本模组 jar 的
        // blockstates/models 并把模型引用到的贴图注册进方块图集（com.gtnewhorizon.gtnhlib.client.model）。
        // 必须早于第一次资源重载的模型扫描（preInit 早于 FMLClientHandler.finishMinecraftLoading 的 refreshResources）。
        com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry.registerModid(com.tacz.guns.GunMod.MOD_ID);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // 3) 枪包 IResourcePack 已在 preInit 挂载（见 preInit 第 3 步：必须早于 TextureManager 重载监听器注册）。
        // 此处不再重复挂载；GunPackResourcePack 自身在每次原版资源重载时重挂（幂等）。

        // 4) 客户端注册面（上游 client/init/ClientSetupEvent 的全部 MOD 总线事件 + 键位 + 渲染器 + HUD）
        ClientSetupEvent.onClientSetup();

        // 5) 事件总线：本工作流自带 INSTANCE 的处理器
        FMLCommonHandler.instance()
            .bus()
            .register(ClientTickHelper.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(GunInputHandler.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(ShootKey.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(AimKey.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(CrawlKey.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(ReloadKey.INSTANCE);
        // 音效清理 tick（SoundPlayManager 本体只有静态方法，@SubscribeEvent 在内部类 ClientTickHandler 上；
        // 原来按类名注册 SoundPlayManager 注册不到任何监听器 → 改为注册它自己的单例，幂等）。
        FMLCommonHandler.instance()
            .bus()
            .register(SoundPlayManager.TICK_HANDLER);

        // 6) Forge 总线：本工作流之外、但必须由客户端入口注册的实例处理器
        // ScreenItemVboContext 用 GuiScreenEvent.DrawScreenEvent.Pre/Post 维护「当前是否在画 GUI」的
        // 静态状态（被 util/RenderDistance 读取）；它只有 @SubscribeEvent static 方法，
        // 必须按实例注册（见类 javadoc 第 2 条）
        MinecraftForge.EVENT_BUS.register(new ScreenItemVboContext());

        // 7) 事件总线：跨工作流的客户端事件类（上游 @Mod.EventBusSubscriber 的 1.7.10 等价注册）
        // 三组映射由 client-event 工作流定稿：FML 族 / Forge 族 / 两类事件都订的类（两边都注册）
        for (String className : FML_BUS_CLASSES) {
            registerBusHandler(className, true);
        }
        for (String className : FORGE_BUS_CLASSES) {
            registerBusHandler(className, false);
        }
        for (String className : BOTH_BUS_CLASSES) {
            registerBusHandlerBoth(className);
        }

        // 8) 命令层的客户端回调注入（`/tacz reload` 的客户端分支）：
        // 上游在 ReloadCommand 里直接箭头到客户端资源管理器；1.7.10 的 command/** 不允许 import client 包，
        // 因此改成「命令侧留回调位、客户端侧注入」。这里用反射调用，避免 ClientProxy 与
        // client/resource/ClientAssetsManager 的移植进度强耦合（缺失时只打 warn）。
        ReloadCommand.setClientReloader(
            () -> CompatRegistry.invokeOptional("com.tacz.guns.client.resource.ClientAssetsManager", "reloadAllPack"));

        // 9) 弹道粒子回调注入（1.7.10 版 DistExecutor）
        //
        // 上游在 EntityKineticBullet 的 tick 里用
        // DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AmmoParticleSpawner.addParticle(this))
        // 直接触发客户端粒子；1.7.10 的 common 侧（entity/**）不允许 import client 包，
        // 因此 EntityBulletClientHooks 只持有 Consumer<Entity> 钩子，由客户端入口在这里赋值。
        // 不赋值 → Hooks.spawnAmmoParticle 内部判空后静默跳过 → 子弹粒子不显示（不崩）。
        EntityBulletClientHooks.ammoParticleSpawner = bullet -> AmmoParticleSpawner
            .addParticle((EntityKineticBullet) bullet);

        // 10) `/tacz convert` 的客户端转换器注入。
        //
        // 上游：`command/sub/ConvertCommand` 里用 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
        // () -> () -> PackConvertor.convert(source))` 直接调客户端实现。
        // 1.7.10 的 command/** 不允许 import client 包，因此沿用与 ReloadCommand 同款「命令侧留回调位、
        // 客户端侧注入」的写法。
        //
        // r2 修复：此处原本是 TODO(L2)，理由是「PackConvertor 仍是 1.20.1 签名（convert(CommandSourceStack)）」。
        // 该理由**已过期** —— `resource/PackConvertor.java:62` 现在就是
        // `public static void convert(net.minecraft.command.ICommandSender source)`，
        // 也就是说只差这一行注入。不补的话 `/tacz convert` 只会打一条 warn 然后什么都不做。
        ConvertCommand.setConverter(PackConvertor::convert);

        // 11) 开发调试桥（tools/mcdev 使用；见 docs/MCDEV_HARNESS.md）。
        // 惰性门控：未设置 -Dtacz.devbridge.port 时连类都不加载，对生产环境零影响。
        if (System.getProperty(com.tacz.debug.DevBridge.PROP_PORT) != null) {
            com.tacz.debug.DevBridge.startIfEnabled();
        }

        // 12) NEI 联动：把配方页签信息（NEI `HandlerInfo`：图标 + mod 名）的监听器提前挂到 Forge 总线。
        //
        // 为什么必须在 init 阶段（而不是等 NEI 插件加载）：NEI 在 FMLLoadCompleteEvent 就
        // `GuiRecipeTab.loadHandlerInfo()` 并 post `NEIRegisterHandlerInfosEvent`，而本模组的 NEI 插件
        // 要等**进世界**后由 NEI 后台线程实例化（那时注册监听器已晚）；并且 Forge 1.7.10 的
        // `EventBus#register` 会读 `Loader#activeModContainer()`，在 mod 加载期之外调用会刷一条
        // "Unable to determine registrant mod" 的 ERROR。
        // NEI 缺席时 `checkModLoad` 直接跳过，不加载 `compat.nei` 的任何类（保持可选依赖语义）。
        CompatRegistry.checkModLoad(
            "NotEnoughItems",
            () -> CompatRegistry.invokeOptional("com.tacz.guns.compat.nei.NeiHandlerInfos", "registerListener"));
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        // 10) 客户端枪包资源管理器接线（1.7.10 版 RegisterClientReloadListenersEvent）
        //
        // 上游：client/init/ClientSetupEvent#onClientResourceReload
        // （tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/init/ClientSetupEvent.java:95-104）
        // → ClientAssetsManager.INSTANCE.reloadAndRegister(event::registerReloadListener)
        // 1.7.10：没有该事件，等价物是 IResourceManagerReloadListener（ClientGunPackReloader）。
        //
        // 时机正确性（为什么 postInit 安全）：FML 的加载顺序是
        // FMLClientHandler#finishMinecraftLoading → Loader#initializeMods（INITIALIZATION → POSTINITIALIZATION，
        // 即 init/postInit 事件）→ client.refreshResources()（FMLClientHandler.java:311/327 同一线程顺序执行）。
        // 所以本步注册的监听器**一定**会被紧接着的第二次 refreshResources（也就加载期最后一次）回调；
        // 且此时 TextureManager/SoundHandler 等原版监听器已先注册，本监听器排在它们之后 ——
        // 纹理 stitch（TextureStitchEvent.Post → InternalAssetLoader）先完成，一次装载即可拿到全部依赖。
        // 顺序契约：数据管理器 → ClientIndexManager.reload()（注册在列表末尾）。
        // 缺失这一步的后果（实测崩溃）：ClientAssetsManager.packInfo 恒 null，
        // 打开创造模式物品栏时 CreativePackFilters.updateTab → getPackInfo 抛 NullPointerException。
        ClientGunPackReloader.init();

        // 11) 枪包语言注入：上游靠 mixin/client/LanguageMixin 注入 1.20 的 I18n，
        // 1.7.10 无 I18n/mixin 概念 → 由 GunPackLoader 走 LanguageRegistry 注入（common-resource 提供）。
        // 放在 postInit 是因为此刻 Minecraft#getLanguageManager() 一定已就绪。
        try {
            String locale = Minecraft.getMinecraft()
                .getLanguageManager()
                .getCurrentLanguage()
                .getLanguageCode();
            GunPackLoader.injectGunPackLanguage(locale);
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[TACZ] failed to inject gun pack language", t);
        }
    }

    /**
     * 反射构造一个实例并注册到指定总线（见类 javadoc 第 2 条：1.7.10 必须按实例注册）。
     *
     * <p>
     * 这样做而不是直接 {@code Xxx.class}：
     * <ul>
     * <li>跨工作流（{@code client/event/**} 归 {@code client-event}，{@code client/sound/**} 归
     * {@code client-misc}）的类，其构造器可见性不由我控制；</li>
     * <li>缺失/签名不符时只打 warn，<b>不会让客户端启动失败</b>——这是「先能起、再打磨」的硬要求。</li>
     * </ul>
     *
     * @param className 全限定类名
     * @param fmlBus    {@code true} 注册到 {@code FMLCommonHandler.instance().bus()}，否则
     *                  {@code MinecraftForge.EVENT_BUS}
     */
    private static void registerBusHandler(String className, boolean fmlBus) {
        Object instance = newHandler(className);
        if (instance == null) {
            return;
        }
        if (fmlBus) {
            FMLCommonHandler.instance()
                .bus()
                .register(instance);
        } else {
            MinecraftForge.EVENT_BUS.register(instance);
        }
    }

    /**
     * 同一个实例同时注册到<b>两条</b>总线（{@link #BOTH_BUS_CLASSES} 用）。
     *
     * <p>
     * 复用同一实例而不是反射两次：让每个类在两条总线上共享同一份状态（若有），
     * 也避免重复构造带来的副作用。
     */
    private static void registerBusHandlerBoth(String className) {
        Object instance = newHandler(className);
        if (instance == null) {
            return;
        }
        FMLCommonHandler.instance()
            .bus()
            .register(instance);
        MinecraftForge.EVENT_BUS.register(instance);
    }

    /** 反射构造一个事件处理器实例；失败返回 {@code null} 并打 warn。 */
    private static Object newHandler(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable t) {
            GunMod.LOGGER.warn("[TACZ] client handler not registered (will be skipped): {}", className, t);
            return null;
        }
    }

    /**
     * 客户端钩子实现 —— 对应上游散落在各消息包 {@code @OnlyIn(Dist.CLIENT)} 分支里的逻辑。
     */
    private static final class Hooks implements ClientSideHooks.Impl {

        @Override
        public Entity getEntityById(int id) {
            // 上游 ClientLevel#getEntity(int) 只要求 level 非空（不依赖本地玩家是否已创建）；
            // 1.7.10 对应物是 Minecraft#theWorld（客户端世界）→ World#getEntityByID
            // （build/rfg/minecraft-src/java/net/minecraft/world/World.java:3575，WorldClient 实现）。
            World world = Minecraft.getMinecraft().theWorld;
            return world == null ? null : world.getEntityByID(id);
        }

        @Override
        public void playSound(ServerMessageSound message) {
            SoundPlayManager.playMessageSound(message);
        }

        @Override
        public void gunUnload(ServerMessageGunUnload message) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player != null) {
                SoundPlayManager.playUnloadSound(player, message.getGunId(), message.getGunDisplayId());
            }
        }

        @Override
        public void bulletHitBlock(ServerMessageBulletHitBlock message) {
            // 上游 HitBlockEffectHandler.handle(level, Vec3, Direction, BlockPos, ammoId, gunId, displayId, incendiary)
            // 1.7.10：Direction → ForgeDirection、BlockPos → 三个 int；实参顺序与上游一致
            HitBlockEffectHandler.handle(
                Minecraft.getMinecraft().theWorld,
                message.getHitX(),
                message.getHitY(),
                message.getHitZ(),
                message.getDirection(),
                message.getBlockX(),
                message.getBlockY(),
                message.getBlockZ(),
                message.getAmmoId(),
                message.getGunId(),
                message.getGunDisplayId(),
                message.isIncendiary());
        }

        @Override
        public void craft(ServerMessageCraft message) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (player != null && player.openContainer != null
                && player.openContainer.windowId == message.getMenuId()
                && screen instanceof GunSmithTableScreen) {
                ((GunSmithTableScreen) screen).updateIngredientCount();
            }
        }

        @Override
        public void refreshRefitScreen(ServerMessageRefreshRefitScreen message) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (player != null && screen instanceof GunRefitScreen) {
                ((GunRefitScreen) screen).initGui();
                // 刷新客户端配件数据
                AttachmentPropertyManager.postChangeEvent(player, player.getHeldItem());
            }
        }

        @Override
        public void syncBaseTimestamp(ServerMessageSyncBaseTimestamp message) {
            // 由 updateBaseTimestamp 承担
        }

        @Override
        public void updateBaseTimestamp(long timestamp) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            LocalPlayerDataHolder dataHolder = IClientPlayerGunOperator.fromLocalPlayer(player)
                .getDataHolder();
            dataHolder.clientBaseTimestamp = timestamp;
            GunMod.LOGGER.debug("Update client base timestamp: {} (now: {})", timestamp, GunTime.nowMillis());
        }

        @Override
        public void syncGunPack(ServerMessageSyncGunPack message) {
            try {
                byte[] uncompressed = ServerMessageSyncGunPack.acceptPart(message.toPart());
                if (uncompressed == null) {
                    return;
                }
                // 上游 ServerMessageSyncGunPack#doSync（tmp/TACZ-1.20.1/.../ServerMessageSyncGunPack.java:98-100）：
                // 非内存连接（= 多人服）时先清掉本地的 CommonAssetsManager 实例，让 CommonAssetsManager.get()
                // 走 CommonNetworkCache —— 即用服务端下发的数据，而不是客户端本地枪包数据。
                // 1.7.10 没有 Connection#isMemoryConnection，用 Minecraft#isSingleplayer() 等价判断。
                if (!Minecraft.getMinecraft()
                    .isSingleplayer()) {
                    CommonAssetsManager.clearInstance();
                }
                // 收齐整份快照后再重建索引（与上游一致）
                CommonNetworkCache.INSTANCE.fromNetwork(ServerMessageSyncGunPack.readCache(uncompressed));
                ClientIndexManager.reload();
                // 枪包内容已换新：GunPackResourcePack 的「该贴图/声音资源是否存在」缓存必须作废，
                // 否则换包后仍按旧包结果判定（getResource 命中旧记录 → 贴图/音效错或丢失）。
                // 上游 1.20 靠 PackRepository 重建 pack 列表天然刷新，1.7.10 需显式清理。
                GunPackResourcePack.invalidateCache();
            } catch (IOException | RuntimeException e) {
                ServerMessageSyncGunPack.clearPendingSync();
                GunMod.LOGGER.error("Failed to synchronize gun pack data", e);
                // 上游 ServerMessageSyncGunPack#doSync:104-107：同步失败 = 双方枪包数据不一致，
                // 直接 connection.disconnect("Failed to synchronize gun pack data")。
                // 1.7.10 等价物是客户端 NetworkManager#closeChannel(IChatComponent)
                // （build/rfg/minecraft-src/java/net/minecraft/network/NetworkManager.java:261-267）。
                NetHandlerPlayClient handler = Minecraft.getMinecraft()
                    .getNetHandler();
                if (handler != null) {
                    handler.getNetworkManager()
                        .closeChannel(new ChatComponentText("Failed to synchronize gun pack data"));
                }
            }
        }

        @Override
        public void updateAmmoStorage(ServerMessageAmmoStorageContents message) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player != null && player.openContainer instanceof AmmoStorageMenu menu
                && player.openContainer.windowId == message.containerId()) {
                menu.setContents(message.capacityStacks(), message.usedStacks(), message.entries());
            }
        }

        @Override
        public void swapItemWithOffHand(ServerMessageSwapItem message) {
            // no-op：1.7.10 无双持（SwapItemWithOffHand 上游事件由 1.9+ 的副手交换触发）
        }
    }
}
