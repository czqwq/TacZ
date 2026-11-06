package com.tacz.guns.config.client;

import com.tacz.guns.config.BooleanConfigValue;
import com.tacz.guns.config.ConfigBuilder;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/client/ResourceConfig.java} 的 1.7.10 版本。
 */
public class ResourceConfig {

    public static BooleanConfigValue ENABLE_LAZY_CLIENT_ASSET_LOAD;

    public static void init(ConfigBuilder builder) {
        builder.push("resource");

        builder.comment(
            "Build heavy TACZ client assets such as models and animation state machines on demand.",
            "Inventory items are pre-warmed in the background when possible.",
            "If a render needs an asset before warmup finishes, the render thread will wait for it once.");
        ENABLE_LAZY_CLIENT_ASSET_LOAD = builder.define("EnableLazyClientAssetLoad", true);

        builder.pop();
    }

    private ResourceConfig() {}
}
