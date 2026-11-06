package com.tacz.guns.config;

import java.io.File;

import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.config.common.OtherConfig;

/**
 * 通用（COMMON）配置，对应上游 1.20.1
 * {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/CommonConfig.java}（{@code ModConfig.Type.COMMON}）。
 *
 * <p>
 * 1.7.10 差异：上游由 Forge 的 {@code ModConfig} 负责文件与加载时机，这里改由 {@link ConfigManager} 在
 * {@code CommonProxy#preInit} 里显式创建 + {@link TaczConfigSpec#load()}，文件名 {@value #FILE_NAME}，
 * 落在 {@code config/} 下（上游为 {@code config/tacz-common.toml}）。
 */
public final class CommonConfig {

    public static final String FILE_NAME = "tacz-common.cfg";

    public static TaczConfigSpec COMMON_CONFIG_SPEC;

    private CommonConfig() {}

    public static TaczConfigSpec init(File configFile) {
        TaczConfigSpec spec = new TaczConfigSpec(configFile);
        ConfigBuilder builder = new ConfigBuilder(spec);
        GunConfig.init(builder);
        AmmoConfig.init(builder);
        OtherConfig.init(builder);
        COMMON_CONFIG_SPEC = builder.build();
        return COMMON_CONFIG_SPEC;
    }
}
