package com.tacz.guns.config.common;

import com.tacz.guns.config.BooleanConfigValue;
import com.tacz.guns.config.ConfigBuilder;
import com.tacz.guns.config.DoubleConfigValue;
import com.tacz.guns.config.IntConfigValue;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/common/OtherConfig.java} 的 1.7.10 版本。
 *
 * <p>
 * 配置项名称 / 默认值 / 范围 / 注释与上游一致，仅替换配置类型
 * （{@code ForgeConfigSpec.*} → 本工程 {@code ConfigBuilder} 系列）。
 */
public class OtherConfig {

    public static BooleanConfigValue DEFAULT_PACK_DEBUG;
    public static IntConfigValue TARGET_SOUND_DISTANCE;
    public static DoubleConfigValue SERVER_HITBOX_OFFSET;
    public static BooleanConfigValue SERVER_HITBOX_LATENCY_FIX;
    public static DoubleConfigValue SERVER_HITBOX_LATENCY_MAX_SAVE_MS;

    public static void init(ConfigBuilder builder) {
        builder.push("other");

        builder
            .comment("Deprecated and ignored: use [gunpack].DefaultPackDebug in <game directory>/tacz/tacz-pre.cfg.");
        DEFAULT_PACK_DEBUG = builder.define("DefaultPackDebug", false);

        builder.comment("The farthest sound distance of the target, including minecarts type");
        TARGET_SOUND_DISTANCE = builder.defineInRange("TargetSoundDistance", 128, 0, Integer.MAX_VALUE);

        serverConfig(builder);

        builder.pop();
    }

    /**
     * 这些配置不加入 cloth config api 中
     */
    private static void serverConfig(ConfigBuilder builder) {
        builder.comment("DEV: Server hitbox offset (If the hitbox is ahead, fill in a negative number)");
        SERVER_HITBOX_OFFSET = builder.defineInRange("ServerHitboxOffset", 3, -Double.MAX_VALUE, Double.MAX_VALUE);

        builder.comment("Server hitbox latency fix");
        SERVER_HITBOX_LATENCY_FIX = builder.define("ServerHitboxLatencyFix", true);

        builder.comment("The maximum latency (in milliseconds) for the server hitbox latency fix saved");
        SERVER_HITBOX_LATENCY_MAX_SAVE_MS = builder
            .defineInRange("ServerHitboxLatencyMaxSaveMs", 1000, 250, Double.MAX_VALUE);
    }

    private OtherConfig() {}
}
