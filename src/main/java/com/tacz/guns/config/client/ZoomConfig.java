package com.tacz.guns.config.client;

import com.tacz.guns.config.ConfigBuilder;
import com.tacz.guns.config.DoubleConfigValue;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/client/ZoomConfig.java} 的 1.7.10 版本。
 */
public class ZoomConfig {

    public static DoubleConfigValue SCREEN_DISTANCE_COEFFICIENT;
    public static DoubleConfigValue ZOOM_SENSITIVITY_BASE_MULTIPLIER;

    public static void init(ConfigBuilder builder) {
        builder.push("Zoom");

        builder.comment("Screen distance coefficient for zoom, using MDV standard, default is MDV133");
        SCREEN_DISTANCE_COEFFICIENT = builder.defineInRange("ScreenDistanceCoefficient", 1.33D, 0.0D, 3.0D);

        builder.comment("Zoom sensitivity is multiplied by this factor");
        ZOOM_SENSITIVITY_BASE_MULTIPLIER = builder.defineInRange("ZoomSensitivityBaseMultiplier", 1.0D, 0.0D, 2.0D);

        builder.pop();
    }

    private ZoomConfig() {}
}
