package com.tacz.guns.event;

/**
 * 专用服务端启动时的枪包加载入口。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/CommonLoadPack.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>上游是 {@code @Mod.EventBusSubscriber(value = Dist.DEDICATED_SERVER, bus = MOD)} 监听
 * {@code FMLCommonSetupEvent}；1.7.10 没有 mod 事件总线，改为普通静态方法
 * {@link #loadGunPack()}，由 {@code EventHandlers.registerAll()}（{@code CommonProxy} 调用）触发；</li>
 * <li>上游本体本来就是空的（{@code DedicatedServerReloadManager.loadGunPack()} 被注释掉），
 * 这里保持同样的空实现，只保留接入点。</li>
 * </ul>
 * <p>
 * TODO(port): 上游 CommonLoadPack.java:13 {@code DedicatedServerReloadManager.loadGunPack()} 被注释。
 * 1.7.10 若要做「专用服务端独立加载枪包」，应由 resource 层的 GunPackLoader 在
 * {@code FMLServerAboutToStartEvent}（{@code cpw.mods.fml.common.event.FMLServerAboutToStartEvent}）里加载。
 */
public class CommonLoadPack {

    /**
     * 上游 {@code loadGunPack(FMLCommonSetupEvent)} 的 1.7.10 形态。
     * 只在专用服务端语义下有意义（上游是 {@code Dist.DEDICATED_SERVER} 专属）。
     */
    public static void loadGunPack() {
        // DedicatedServerReloadManager.loadGunPack();
    }
}
