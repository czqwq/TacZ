package com.tacz.guns.config.client;

import com.tacz.guns.config.BooleanConfigValue;
import com.tacz.guns.config.ConfigBuilder;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/client/KeyConfig.java} 的 1.7.10 版本。
 */
public class KeyConfig {

    public static BooleanConfigValue HOLD_TO_AIM;
    public static BooleanConfigValue HOLD_TO_CRAWL;
    public static BooleanConfigValue AUTO_RELOAD;

    public static void init(ConfigBuilder builder) {
        builder.push("key");

        builder.comment("True if you want to hold the right mouse button to aim");
        HOLD_TO_AIM = builder.define("HoldToAim", true);

        builder.comment("True if you want to hold the crawl button to crawl");
        HOLD_TO_CRAWL = builder.define("HoldToCrawl", true);

        builder.comment("Try to reload automatically when the gun is empty");
        AUTO_RELOAD = builder.define("AutoReload", false);

        builder.pop();
    }

    private KeyConfig() {}
}
