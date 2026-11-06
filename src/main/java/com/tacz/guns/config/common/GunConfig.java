package com.tacz.guns.config.common;

import com.tacz.guns.config.BooleanConfigValue;
import com.tacz.guns.config.ConfigBuilder;
import com.tacz.guns.config.IntConfigValue;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/common/GunConfig.java} 的 1.7.10 版本。
 *
 * <p>
 * 配置项名称、默认值、取值范围、注释全部与上游一致；只是把 {@code ForgeConfigSpec.Builder/IntValue/
 * BooleanValue} 换成本工程的 {@link ConfigBuilder}/{@link IntConfigValue}/{@link BooleanConfigValue}
 * （底层是 1.7.10 {@code net.minecraftforge.common.config.Configuration}）。
 */
public class GunConfig {

    public static final int MAX_GUN_SOUND_MOB_ANGER_DISTANCE = 64;

    public static IntConfigValue DEFAULT_GUN_FIRE_SOUND_DISTANCE;
    public static IntConfigValue DEFAULT_GUN_SILENCE_SOUND_DISTANCE;
    public static IntConfigValue DEFAULT_GUN_OTHER_SOUND_DISTANCE;
    public static BooleanConfigValue ENABLE_GUN_SOUND_ANGER;
    public static IntConfigValue DEFAULT_GUN_SOUND_NEUTRAL_MOB_ANGER_DISTANCE;
    public static IntConfigValue DEFAULT_GUN_SOUND_HOSTILE_MOB_ANGER_DISTANCE;
    public static BooleanConfigValue CREATIVE_PLAYER_CONSUME_AMMO;
    public static BooleanConfigValue AUTO_RELOAD_WHEN_RESPAWN;

    public static void init(ConfigBuilder builder) {
        builder.push("gun");

        builder.comment("The default fire sound range (block)");
        DEFAULT_GUN_FIRE_SOUND_DISTANCE = builder
            .defineInRange("DefaultGunFireSoundDistance", 64, 0, Integer.MAX_VALUE);

        builder.comment("The silencer default fire sound range (block)");
        DEFAULT_GUN_SILENCE_SOUND_DISTANCE = builder
            .defineInRange("DefaultGunSilenceSoundDistance", 16, 0, Integer.MAX_VALUE);

        builder.comment("The range (block) of other gun sound, reloading sound etc.");
        DEFAULT_GUN_OTHER_SOUND_DISTANCE = builder
            .defineInRange("DefaultGunOtherSoundDistance", 16, 0, Integer.MAX_VALUE);

        builder.comment("Whether or not gunfire sound angers nearby mobs");
        ENABLE_GUN_SOUND_ANGER = builder.define("EnableGunSoundAnger", true);

        builder.comment("The default range (block) for gunfire sound to anger neutral mobs");
        DEFAULT_GUN_SOUND_NEUTRAL_MOB_ANGER_DISTANCE = builder
            .defineInRange("DefaultGunSoundNeutralMobAngerDistance", 24, 0, MAX_GUN_SOUND_MOB_ANGER_DISTANCE);

        builder.comment("The default range (block) for gunfire sound to attract hostile mobs");
        DEFAULT_GUN_SOUND_HOSTILE_MOB_ANGER_DISTANCE = builder
            .defineInRange("DefaultGunSoundHostileMobAngerDistance", 32, 0, MAX_GUN_SOUND_MOB_ANGER_DISTANCE);

        builder.comment("Whether or not the player will consume ammo in creative mode");
        CREATIVE_PLAYER_CONSUME_AMMO = builder.define("CreativePlayerConsumeAmmo", true);

        builder.comment("Auto reload all the guns in player inventory, useful for pvp servers");
        AUTO_RELOAD_WHEN_RESPAWN = builder.define("AutoReloadWhenRespawn", false);

        builder.pop();
    }

    private GunConfig() {}
}
