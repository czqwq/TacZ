package com.tacz.guns.config.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.config.BooleanConfigValue;
import com.tacz.guns.config.ConfigBuilder;
import com.tacz.guns.config.DoubleConfigValue;
import com.tacz.guns.config.IntConfigValue;
import com.tacz.guns.config.ListConfigValue;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/common/AmmoConfig.java} 的 1.7.10 版本。
 *
 * <p>
 * 配置项名称 / 默认值 / 范围 / 注释与上游一致，仅替换配置类型。
 *
 * <p>
 * 1.7.10 差异（数据层面，不是本类引入的）：
 * <ul>
 * <li>上游默认值里的 {@code #forge:glass} / {@code #forge:glass_panes} 是 <b>1.20 tag</b>；1.7.10 没有
 * {@code BlockTags}，改由 {@code config/util/AmmoBlockConfigRead} 把已知 tag 名展开成方块列表
 * （见该类的 {@code EXPANDED_VANILLA_TAGS}），未识别项按方块 id 解析，解析失败只打日志。</li>
 * <li>列表项校验器的逐元素校验（{@code defineListAllowEmpty(name, def, validator)}）在 1.7.10
 * {@code Property} 上没有等价物，validator 只用于保持上游签名，不参与运行时过滤。</li>
 * </ul>
 */
public class AmmoConfig {

    public static final List<String> DEFAULT_DESTROY_BLOCKS = Arrays.asList("#forge:glass", "#forge:glass_panes");
    public static final List<String> DEFAULT_BULLET_DAMAGE_IGNORE_LIST = Arrays
        .asList("minecraft:painting", "minecraft:item_frame", "minecraft:glow_item_frame");

    public static BooleanConfigValue EXPLOSIVE_AMMO_DESTROYS_BLOCK;
    public static BooleanConfigValue EXPLOSIVE_AMMO_FIRE;
    public static BooleanConfigValue EXPLOSIVE_AMMO_KNOCK_BACK;
    public static IntConfigValue EXPLOSIVE_AMMO_VISIBLE_DISTANCE;
    public static ListConfigValue PASS_THROUGH_BLOCKS;
    public static BooleanConfigValue DESTROY_GLASS;
    public static ListConfigValue DESTROY_BLOCKS;
    public static DoubleConfigValue DESTROY_BLOCK_HARDNESS_THRESHOLD;
    public static BooleanConfigValue IGNITE_BLOCK;
    public static BooleanConfigValue IGNITE_ENTITY;
    public static BooleanConfigValue IGNORE_TEAM_FRIENDLY_FIRE;
    public static BooleanConfigValue ENABLE_BULLET_DAMAGE_IGNORE_LIST;
    public static ListConfigValue BULLET_DAMAGE_IGNORE_LIST;
    public static DoubleConfigValue GLOBAL_BULLET_SPEED_MODIFIER;

    public static void init(ConfigBuilder builder) {
        builder.push("ammo");

        builder.comment("Warning: Ammo with explosive properties can break blocks");
        EXPLOSIVE_AMMO_DESTROYS_BLOCK = builder.define("ExplosiveAmmoDestroysBlock", true);

        builder.comment("Warning: Ammo with explosive properties can set the surroundings on fire");
        EXPLOSIVE_AMMO_FIRE = builder.define("ExplosiveAmmoFire", false);

        builder.comment("Ammo with explosive properties can add knockback effect");
        EXPLOSIVE_AMMO_KNOCK_BACK = builder.define("ExplosiveAmmoKnockBack", true);

        builder.comment("The distance at which the explosion effect can be seen");
        EXPLOSIVE_AMMO_VISIBLE_DISTANCE = builder
            .defineInRange("ExplosiveAmmoVisibleDistance", 192, 0, Integer.MAX_VALUE);

        builder.comment(
            "Blocks that bullets can pass through. Accepts block IDs and tags prefixed with #, e.g. minecraft:glass or #forge:glass.");
        PASS_THROUGH_BLOCKS = builder
            .defineListAllowEmpty("PassThroughBlocks", new ArrayList<>(), value -> value instanceof String);

        builder.comment(
            "Allow bullets to destroy blocks listed in DestroyBlocks without drops. The name is retained for compatibility; the list is not limited to glass.");
        DESTROY_GLASS = builder.define("DestroyGlass", true);

        builder.comment(
            "Blocks that bullets can destroy when DestroyGlass is enabled, subject to DestroyBlockHardnessThreshold. Accepts block IDs and tags prefixed with #, e.g. minecraft:glass or #forge:glass.");
        DESTROY_BLOCKS = builder.defineListAllowEmpty(
            "DestroyBlocks",
            new ArrayList<>(DEFAULT_DESTROY_BLOCKS),
            value -> value instanceof String);

        builder.comment(
            "Maximum block hardness for direct bullet destruction. Blocks above this threshold or with negative hardness (unbreakable) are never destroyed, even if listed in DestroyBlocks. Default: 1.0; vanilla glass has hardness 0.3.");
        DESTROY_BLOCK_HARDNESS_THRESHOLD = builder
            .defineInRange("DestroyBlockHardnessThreshold", 1.0, 0.0, Double.MAX_VALUE);

        builder.comment("Whether a ammo can ignite the block");
        IGNITE_BLOCK = builder.define("IgniteBlock", true);

        builder.comment("Whether a ammo can ignite the entity");
        IGNITE_ENTITY = builder.define("IgniteEntity", true);

        builder.comment(
            "Bullets pass through members of the shooter's scoreboard team when friendly fire is disabled, without consuming pierce count");
        IGNORE_TEAM_FRIENDLY_FIRE = builder.define("IgnoreTeamFriendlyFire", false);

        builder.comment(
            "Enable the entity bullet damage ignore list. Listed entities are ignored by bullet hit detection and take no damage or knockback from ammo explosions.");
        ENABLE_BULLET_DAMAGE_IGNORE_LIST = builder.define("EnableBulletDamageIgnoreList", false);

        builder.comment(
            "Entity type IDs protected from bullets and ammo explosions when EnableBulletDamageIgnoreList is enabled.");
        BULLET_DAMAGE_IGNORE_LIST = builder.defineListAllowEmpty(
            "BulletDamageIgnoreList",
            new ArrayList<>(DEFAULT_BULLET_DAMAGE_IGNORE_LIST),
            AmmoConfig::isValidResourceLocation);

        builder.comment(
            "Global bullet speed modifier, the init speed of the bullet will be multiplied by this value, default is 2.0");
        builder
            .comment("This is to compensate the side effects introduced while fixing the shooter variable input issue");
        GLOBAL_BULLET_SPEED_MODIFIER = builder.defineInRange("GlobalBulletSpeedModifier", 2.0, 0.01, 20);

        builder.pop();
    }

    /**
     * 上游用 {@code ResourceLocation.tryParse(id) != null} 做校验；1.7.10 的 {@link ResourceLocation} 没有
     * tryParse，构造失败会抛 {@link IllegalArgumentException}，因此在这里包一层。
     */
    private static boolean isValidResourceLocation(Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        try {
            new ResourceLocation((String) value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private AmmoConfig() {}
}
