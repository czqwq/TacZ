package com.tacz.guns.config;

import java.io.File;

import com.tacz.guns.config.util.AmmoBlockConfigRead;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import com.tacz.guns.config.util.InteractKeyConfigRead;

/**
 * 配置加载的统一入口 —— 1.7.10 里没有 Forge 的 {@code ModConfig} 自动装载，只能自己按顺序建/读/存。
 *
 * <p>
 * 对应上游（1.20.1）的分散逻辑：
 * <ul>
 * <li>{@code GunMod()} 构造器里的 {@code ModLoadingContext.registerConfig(COMMON/SERVER/CLIENT, ...)}：
 * 在 1.7.10 由 {@link #initCommon(File)} / {@link #initClient(File)} 完成（时机在 {@code CommonProxy#preInit}）</li>
 * <li>{@code event/LoadingConfigEvent} 的 {@code ModConfigEvent.Loading/Reloading} 钩子：COMMON 配置加载后
 * 重算 {@link AmmoBlockConfigRead}；SERVER 配置加载后重算 {@link HeadShotAABBConfigRead} /
 * {@link InteractKeyConfigRead} / {@link GunPegboardItemConfigRead}。1.7.10 由本类在 load 之后直接调用。</li>
 * </ul>
 *
 * <p>
 * 加载顺序（与上游一致）：COMMON → SERVER → （客户端）CLIENT。
 */
public final class ConfigManager {

    private ConfigManager() {}

    /**
     * 服务端 / 通用侧初始化：COMMON + SERVER。必须在 {@code CommonProxy#preInit} 里、任何配置读之前调用。
     */
    public static void initCommon(File configDir) {
        CommonConfig.init(new File(configDir, CommonConfig.FILE_NAME))
            .load();
        ServerConfig.init(new File(configDir, ServerConfig.FILE_NAME))
            .load();
        // 首次启动把默认值写进文件（Forge 的行为），也让用户能看到全部可改项
        CommonConfig.COMMON_CONFIG_SPEC.save();
        ServerConfig.SERVER_CONFIG_SPEC.save();
        afterCommonLoad();
        afterServerLoad();
    }

    /**
     * 客户端侧初始化：CLIENT 配置（服务端不加载，因为 RenderConfig 引用了客户端枚举 CrosshairType）。
     */
    public static void initClient(File configDir) {
        ClientConfig.init(new File(configDir, ClientConfig.FILE_NAME))
            .load();
        ClientConfig.CLIENT_CONFIG_SPEC.save();
    }

    /**
     * 热重载（对应上游 {@code ModConfigEvent.Reloading}）。COMMON 与 SERVER 都重新读文件并刷新缓存值。
     */
    public static void reloadAll() {
        if (CommonConfig.COMMON_CONFIG_SPEC != null) {
            CommonConfig.COMMON_CONFIG_SPEC.load();
            afterCommonLoad();
        }
        if (ServerConfig.SERVER_CONFIG_SPEC != null) {
            ServerConfig.SERVER_CONFIG_SPEC.load();
            afterServerLoad();
        }
    }

    public static void saveAll() {
        if (CommonConfig.COMMON_CONFIG_SPEC != null) {
            CommonConfig.COMMON_CONFIG_SPEC.save();
        }
        if (ServerConfig.SERVER_CONFIG_SPEC != null) {
            ServerConfig.SERVER_CONFIG_SPEC.save();
        }
        if (ClientConfig.CLIENT_CONFIG_SPEC != null) {
            ClientConfig.CLIENT_CONFIG_SPEC.save();
        }
    }

    /**
     * 对应上游 {@code LoadingConfigEvent#onLoadingConfig} 里
     * {@code event.getConfig().getSpec() == CommonConfig.COMMON_CONFIG_SPEC} 分支。
     */
    private static void afterCommonLoad() {
        AmmoBlockConfigRead.init();
    }

    /**
     * 对应上游 {@code LoadingConfigEvent} 里 {@code CONFIG_NAME.equals(fileName)}（即 tacz-server.toml）分支。
     */
    private static void afterServerLoad() {
        HeadShotAABBConfigRead.init();
        InteractKeyConfigRead.init();
        GunPegboardItemConfigRead.init();
    }
}
