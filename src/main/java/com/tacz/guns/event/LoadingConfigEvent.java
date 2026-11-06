package com.tacz.guns.event;

import com.tacz.guns.config.util.AmmoBlockConfigRead;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import com.tacz.guns.config.util.InteractKeyConfigRead;

/**
 * 配置加载/重载后，刷新各「配置缓存读取器」的静态表。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/LoadingConfigEvent.java}
 * （监听 {@code ModConfigEvent.Loading} 与 {@code ModConfigEvent.Reloading}）。
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>1.7.10 没有 {@code ModConfigEvent}（{@code ForgeConfigSpec} 时代的 mod 事件总线），
 * 也没有「按 spec / 文件名」区分；改为普通静态方法 {@link #reloadConfig()}，
 * 由 {@code EventHandlers.registerAll()} 在配置加载（{@code ConfigManager}）之后调用；</li>
 * <li>上游按 {@code CommonConfig.COMMON_CONFIG_SPEC} 与 {@code "tacz-server.toml"} 文件名分流；
 * 1.7.10 的公共配置（{@code tacz-common.cfg}）与这些读取器用到的项都在同一份配置里，
 * 因此不再分流，四个读取器统一 init；</li>
 * <li>上游 {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...ClientGunPackDownloadManager::downloadClientGunPack)}
 * 属于客户端行为，1.7.10 侧从 common 包不可引用 client 包，见下方 TODO。</li>
 * </ul>
 */
public class LoadingConfigEvent {

    /**
     * 上游 {@code onLoadingConfig}/{@code onReloadingConfig} 两个分支合并后的 1.7.10 形态。
     * 可重复调用（读取器内部会重建缓存表）。
     */
    public static void reloadConfig() {
        AmmoBlockConfigRead.init();
        HeadShotAABBConfigRead.init();
        InteractKeyConfigRead.init();
        GunPegboardItemConfigRead.init();
    }
}
