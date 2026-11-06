package com.tacz.guns.client.resource;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.ParticleDefinitionReloader;
import com.tacz.guns.resource.IGunPackReloadListener;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端枪包资源重载驱动器（1.7.10 的 {@code RegisterClientReloadListenersEvent} 等价物）。
 *
 * <p>
 * <b>上游对照</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/init/ClientSetupEvent.java:95-104}
 * 的 {@code onClientResourceReload(RegisterClientReloadListenersEvent)}：把
 * {@link ClientAssetsManager} 内部全部数据管理器注册进原版资源重载管线
 * （{@code ClientAssetsManager.INSTANCE.reloadAndRegister(event::registerReloadListener)}），
 * 由原版在资源重载时按注册顺序回调。
 *
 * <p>
 * <b>1.7.10 落点</b>：没有 {@code RegisterClientReloadListenersEvent}，等价物是
 * {@link IResourceManagerReloadListener}（{@code SimpleReloadableResourceManager} 的监听器列表）。
 * 本类在客户端 {@code postInit} 由 {@code ClientProxy} 调用一次：
 * <ol>
 * <li>先把 {@link ClientAssetsManager} 的管理器建出来（{@code reloadAndRegister}）；</li>
 * <li>把本类注册进 {@link IReloadableResourceManager}：注册会<b>同步立刻回调一次</b>，但那一刻
 * {@code TextureStitchEvent.Post} 尚未发生（{@code InternalAssetLoader} 的默认模型/动画还没装载），
 * 所以跳过这次回调；真正首装由紧接着的 {@code Minecraft#refreshResources()}（FML 在
 * {@code FMLClientHandler#finishMinecraftLoading} 末尾调用）触发 —— 此时本监听器排在
 * {@code TextureManager} 之后，纹理与内置资产全部就绪。</li>
 * <li>首个客户端 tick 兜底一次：若上述重载链因任何原因没有回调，这里补装，避免「静默无枪包数据」。</li>
 * </ol>
 *
 * <p>
 * <b>顺序契约</b>（与上游一致）：数据管理器 → {@code ClientIndexManager.reload()}（注册在列表末尾）。
 * 之后每次原版资源重载（F3+T、{@code /tacz reload}）都会再走一遍本类。
 */
@SideOnly(Side.CLIENT)
public final class ClientGunPackReloader implements IResourceManagerReloadListener {

    private static final ClientGunPackReloader INSTANCE = new ClientGunPackReloader();
    private static final List<IGunPackReloadListener> LISTENERS = new ArrayList<>();

    private static boolean initialised;
    private static boolean loadedOnce;
    /** {@code true} 时丢弃 {@code registerReloadListener} 的同步首回调（见类 javadoc 第 2 条）。 */
    private static boolean skipNextCallback;

    private ClientGunPackReloader() {}

    /** 由 {@code ClientProxy#postInit} 调用；必须晚于 {@code GunPackLoader.INSTANCE.reloadPackList()}。 */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        ClientAssetsManager.INSTANCE.reloadAndRegister(LISTENERS::add);
        // 移植补充：枪包内的 snowstorm 粒子定义（assets/<ns>/particle_definitions/*.json）。
        // 上游由 simplebedrockmodel 模组自己注册重载监听器装载（见 ParticleDefinitionReloader 类注释）；
        // SBM 在本工程是 vendor 库、没有模组入口，故由这里补一条。追加在列表末尾，
        // 不改变 ClientAssetsManager 已定稿的「数据管理器 → ClientIndexManager::reload」顺序。
        LISTENERS.add(ParticleDefinitionReloader.INSTANCE);

        IResourceManager resourceManager = Minecraft.getMinecraft()
            .getResourceManager();
        if (resourceManager instanceof IReloadableResourceManager reloadableResourceManager) {
            try {
                skipNextCallback = true;
                reloadableResourceManager.registerReloadListener(INSTANCE);
            } finally {
                skipNextCallback = false;
            }
        } else {
            // 理论上不会发生（Minecraft 始终用 SimpleReloadableResourceManager）；退化为直接同步装载。
            reloadAll();
        }

        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    /**
     * 按 {@link ClientAssetsManager} 的注册顺序执行一次全部客户端枪包数据装载。
     *
     * <p>
     * 单个管理器失败不能拖垮整个客户端（1.7.10 的枪包内容没有数据包隔离，坏一个 json 不应导致黑屏），
     * 因此逐个 try/catch，失败打 error 后继续。
     */
    public static void reloadAll() {
        if (LISTENERS.isEmpty()) {
            return;
        }
        loadedOnce = true;
        // 先补一轮语言：数据管理器会按语言表判定部分内容（如 ClientAttachmentIndex 用
        // StatCollector 查 %ammo_count% 标记），必须晚于枪包资源包重挂、早于数据装载。
        refreshGunPackLanguages();
        for (IGunPackReloadListener listener : LISTENERS) {
            try {
                listener.reload();
            } catch (Throwable throwable) {
                GunMod.LOGGER.error(
                    "[TACZ] client gun pack reload listener {} failed",
                    listener.getClass()
                        .getName(),
                    throwable);
            }
        }
    }

    /**
     * 1.7.10 时序补偿：枪包资源包由 {@code GunPackResourcePackReloadListener} 在本轮原版重载通知链里重挂，
     * 而 {@code LanguageManager} 是 Minecraft 构造器里注册的<b>第一个</b>重载监听器（{@code Minecraft.java:521}），
     * 先于重挂执行 ⇒ 本轮 Locale 只读得到模组 jar 里的基础 lang，枪包 lang（同名 key 的覆盖、以及只存在于
     * 枪包的新 key）不会被加载。上游 1.20 里枪包本身就是资源包列表的一员，语言与其余资源在同一次重载中
     * 按「枪包覆盖本体」的优先级加载；这里在数据装载前主动重跑一次 {@code LanguageManager} 的回调作为等价补偿
     * （此时枪包已重挂进 {@code IResourceManager}，{@code getAllResources} 的「后者覆盖前者」即上游优先级）。
     */
    private static void refreshGunPackLanguages() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getLanguageManager() == null || minecraft.getResourceManager() == null) {
            return;
        }
        try {
            minecraft.getLanguageManager()
                .onResourceManagerReload(minecraft.getResourceManager());
        } catch (RuntimeException exception) {
            GunMod.LOGGER.error("Failed to reload languages after re-installing the gun pack resource pack", exception);
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        if (skipNextCallback) {
            return;
        }
        reloadAll();
    }

    /** 首个客户端 tick 兜底（正常路径不会走到）。 */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (loadedOnce || event.phase != TickEvent.Phase.END) {
            return;
        }
        reloadAll();
    }

    /** 当前是否至少装载过一次（供调试/诊断打印）。 */
    public static boolean isLoadedOnce() {
        return loadedOnce;
    }
}
