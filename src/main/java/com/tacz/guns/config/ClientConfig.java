package com.tacz.guns.config;

import java.io.File;

import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.client.ResourceConfig;
import com.tacz.guns.config.client.SoundConfig;
import com.tacz.guns.config.client.ZoomConfig;

/**
 * 客户端（CLIENT）配置，对应上游 1.20.1
 * {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/ClientConfig.java}（{@code ModConfig.Type.CLIENT}）。
 *
 * <p>
 * 只在客户端加载：由 {@code ClientProxy#preInit} 调用，服务端永远不会触碰
 * {@link com.tacz.guns.config.client.RenderConfig}（它引用了客户端枚举
 * {@code com.tacz.guns.client.renderer.crosshair.CrosshairType}）。
 *
 * <p>
 * 1.7.10 差异：文件名 {@value #FILE_NAME}，落在 {@code config/} 下（上游 {@code config/tacz-client.toml}）。
 */
public class ClientConfig {

    public static final String FILE_NAME = "tacz-client.cfg";

    public static TaczConfigSpec CLIENT_CONFIG_SPEC;

    public static TaczConfigSpec init(File configFile) {
        TaczConfigSpec spec = new TaczConfigSpec(configFile);
        ConfigBuilder builder = new ConfigBuilder(spec);
        KeyConfig.init(builder);
        RenderConfig.init(builder);
        ResourceConfig.init(builder);
        SoundConfig.init(builder);
        ZoomConfig.init(builder);
        CLIENT_CONFIG_SPEC = builder.build();
        return CLIENT_CONFIG_SPEC;
    }
}
