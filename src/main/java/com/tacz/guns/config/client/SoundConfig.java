package com.tacz.guns.config.client;

import com.tacz.guns.config.BooleanConfigValue;
import com.tacz.guns.config.ConfigBuilder;
import com.tacz.guns.config.IntConfigValue;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/client/SoundConfig.java} 的 1.7.10 版本。
 */
public class SoundConfig {

    public static IntConfigValue HIT_SOUND_CONCURRENCY_LIMIT;
    public static IntConfigValue DEFAULT_SOUND_CONCURRENCY_LIMIT;
    public static IntConfigValue HIGH_FREQUENCY_SOUND_CONCURRENCY_LIMIT;
    public static BooleanConfigValue FIRST_PERSON_ANIMATION_SOUND_TRACKING;

    public static void init(ConfigBuilder builder) {
        builder.push("sound");

        builder.comment("Max active hit marker sounds for the same entity and sound id. 0 disables this limit.");
        HIT_SOUND_CONCURRENCY_LIMIT = builder.defineInRange("HitSoundConcurrencyLimit", 1, 0, 128);

        builder.comment("Max active normal gun sounds for the same entity and sound id. 0 disables this limit.");
        DEFAULT_SOUND_CONCURRENCY_LIMIT = builder.defineInRange("DefaultSoundConcurrencyLimit", 2, 0, 128);

        builder.comment(
            "Max active high-frequency gun sounds, such as shooting and animation keyframe sounds, for the same entity and sound id. 0 disables this limit.");
        HIGH_FREQUENCY_SOUND_CONCURRENCY_LIMIT = builder.defineInRange("HighFrequencySoundConcurrencyLimit", 4, 0, 128);

        builder.comment(
            "Use a non-relative entity-tracking world sound source for first-person animation keyframe sounds. This can improve compatibility with physical sound mods, but may introduce slight stereo drift while moving.");
        FIRST_PERSON_ANIMATION_SOUND_TRACKING = builder.define("FirstPersonAnimationSoundTracking", false);

        builder.pop();
    }

    private SoundConfig() {}
}
