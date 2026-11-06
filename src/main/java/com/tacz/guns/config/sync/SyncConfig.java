package com.tacz.guns.config.sync;

import java.util.ArrayList;

import com.tacz.guns.config.BooleanConfigValue;
import com.tacz.guns.config.ConfigBuilder;
import com.tacz.guns.config.DoubleConfigValue;
import com.tacz.guns.config.IntConfigValue;
import com.tacz.guns.config.ListConfigValue;
import com.tacz.guns.network.message.ServerMessageSyncConfig;

/**
 * 上游 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/sync/SyncConfig.java} 的 1.7.10 版本。
 *
 * <p>
 * 这是「服务端配置」：上游靠 Forge 的 {@code ModConfig.Type.SERVER} 自动同步给客户端，因此 client/** 里
 * 直接 {@code SyncConfig.XXX.get()} 就能拿到服务端的值。1.7.10 没有这个机制，同步改为：
 * <ol>
 * <li>玩家登录时（{@code network/SyncConfigSyncManager} 监听 {@code PlayerLoggedInEvent}）服务端把本类全部项
 * 打包成 {@code network/message/ServerMessageSyncConfig} 发给该玩家；</li>
 * <li>客户端收到后调用 {@link #applyFromServer}，只覆盖内存中的值，不写本地文件（与上游 Forge 行为一致：服务端
 * 配置在客户端处于只读状态）；</li>
 * <li>所有「客户端要看服务端值」的地方继续用 {@code SyncConfig.XXX.get()}，调用点无需区分。</li>
 * </ol>
 * 未登录单机（集成服务器）时客户端与服务端是同一进程，值本来就一致，无需同步。
 */
public class SyncConfig {

    // 交互键的判断是在客户端执行的，但是需要服务端来控制
    public static ListConfigValue INTERACT_KEY_WHITELIST_BLOCKS;
    public static ListConfigValue INTERACT_KEY_WHITELIST_ENTITIES;
    public static ListConfigValue INTERACT_KEY_BLACKLIST_BLOCKS;
    public static ListConfigValue INTERACT_KEY_BLACKLIST_ENTITIES;
    public static ListConfigValue GUN_PEGBOARD_WHITELIST_ITEMS;
    public static ListConfigValue GUN_PEGBOARD_BLACKLIST_ITEMS;
    public static BooleanConfigValue ENABLE_TABLE_FILTER;
    public static IntConfigValue BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE;
    public static BooleanConfigValue SERVER_SHOOT_NETWORK_V;
    public static BooleanConfigValue SERVER_SHOOT_COOLDOWN_V;
    public static BooleanConfigValue USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE;

    // 三个全局系数，用于客户端枪械文本提示，需要同步
    public static DoubleConfigValue DAMAGE_BASE_MULTIPLIER;
    public static DoubleConfigValue ARMOR_IGNORE_BASE_MULTIPLIER;
    public static DoubleConfigValue HEAD_SHOT_BASE_MULTIPLIER;
    public static DoubleConfigValue WEIGHT_SPEED_MULTIPLIER;

    // 需要同步到客户端，方便客户端 debug 显示碰撞箱
    public static ListConfigValue HEAD_SHOT_AABB;
    // 子弹盒存储上限需要客户端显示支持
    public static IntConfigValue AMMO_BOX_STACK_SIZE;
    // 客户端需要下载的枪械包（上游为 List<List<String>>，1.7.10 的 Configuration 只有一维字符串列表，
    // 因此收敛为 List<String>，每项按 "url|sha1|name" 形式书写；该功能上游已标注 Deprecated，
    // 客户端下载管理器也在 1.7.10 侧未移植）
    public static ListConfigValue CLIENT_GUN_PACK_DOWNLOAD_URLS;
    // 禁用趴下战术动作
    public static BooleanConfigValue ENABLE_CRAWL;
    public static BooleanConfigValue ENABLE_CRAWL_WITHOUT_GUN;
    // 持枪趴下时的最大仰角和俯角，单位为度，需要同步到客户端。
    public static DoubleConfigValue CRAWL_MAX_UP_ANGLE;
    public static DoubleConfigValue CRAWL_MAX_DOWN_ANGLE;

    public static void init(ConfigBuilder builder) {
        interactKey(builder);
        gunPegboard(builder);
        baseMultiplier(builder);
        misc(builder);
    }

    public static void interactKey(ConfigBuilder builder) {
        builder.push("interact_key");

        builder.comment("These whitelist blocks can be interacted with when the interact key is pressed");
        INTERACT_KEY_WHITELIST_BLOCKS = builder
            .defineListAllowEmpty("InteractKeyWhitelistBlocks", new ArrayList<>(), value -> value instanceof String);

        builder.comment("These whitelist entities can be interacted with when the interact key is pressed");
        INTERACT_KEY_WHITELIST_ENTITIES = builder
            .defineListAllowEmpty("InteractKeyWhitelistEntities", new ArrayList<>(), value -> value instanceof String);

        builder.comment("These blacklist blocks can be interacted with when the interact key is pressed");
        INTERACT_KEY_BLACKLIST_BLOCKS = builder
            .defineListAllowEmpty("InteractKeyBlacklistBlocks", new ArrayList<>(), value -> value instanceof String);

        builder.comment("These blacklist entities can be interacted with when the interact key is pressed");
        INTERACT_KEY_BLACKLIST_ENTITIES = builder
            .defineListAllowEmpty("InteractKeyBlacklistEntities", new ArrayList<>(), value -> value instanceof String);

        builder.pop();
    }

    private static void gunPegboard(ConfigBuilder builder) {
        builder.push("gun_pegboard");

        builder.comment(
            "Extra item ids allowed on the gun pegboard, in addition to TACZ guns, attachments, ammo, and ammo boxes");
        GUN_PEGBOARD_WHITELIST_ITEMS = builder
            .defineListAllowEmpty("GunPegboardWhitelistItems", new ArrayList<>(), value -> value instanceof String);

        builder.comment(
            "Item ids denied on the gun pegboard. This list takes priority over the default allowed TACZ item types and whitelist");
        GUN_PEGBOARD_BLACKLIST_ITEMS = builder
            .defineListAllowEmpty("GunPegboardBlacklistItems", new ArrayList<>(), value -> value instanceof String);

        builder.pop();
    }

    private static void baseMultiplier(ConfigBuilder builder) {
        builder.push("base_multiplier");

        builder.comment("All base damage number is multiplied by this factor");
        DAMAGE_BASE_MULTIPLIER = builder.defineInRange("DamageBaseMultiplier", 1, 0, Double.MAX_VALUE);

        builder.comment("All armor ignore damage number is multiplied by this factor");
        ARMOR_IGNORE_BASE_MULTIPLIER = builder.defineInRange("ArmorIgnoreBaseMultiplier", 1, 0, Double.MAX_VALUE);

        builder.comment("All head shot damage number is multiplied by this factor");
        HEAD_SHOT_BASE_MULTIPLIER = builder.defineInRange("HeadShotBaseMultiplier", 1, 0, Double.MAX_VALUE);

        builder.comment(
            "The movement speed will decrease per kg of weight. 0.015 means 1.5% speed decrease per kg. Set a negative value to disable this feature");
        WEIGHT_SPEED_MULTIPLIER = builder.defineInRange("WeightSpeedMultiplier", 0.015, -1, Double.MAX_VALUE);

        builder.pop();
    }

    private static void misc(ConfigBuilder builder) {
        builder.push("misc");

        builder.comment("The entity's head hitbox during the headshot");
        builder.comment("Format: touhou_little_maid:maid [-0.5, 1.0, -0.5, 0.5, 1.5, 0.5]");
        HEAD_SHOT_AABB = builder
            .defineListAllowEmpty("HeadShotAABB", new ArrayList<>(), value -> value instanceof String);

        builder.comment("The maximum stack size of ammo that the ammo box can hold");
        AMMO_BOX_STACK_SIZE = builder.defineInRange("AmmoBoxStackSize", 3, 1, Integer.MAX_VALUE);

        builder.comment("Deprecated. Use vanilla server resource pack");
        CLIENT_GUN_PACK_DOWNLOAD_URLS = builder
            .defineListAllowEmpty("ClientGunPackDownloadUrls", new ArrayList<>(), value -> value instanceof String);

        builder.comment("Whether or not players are allowed to use the crawl feature");
        ENABLE_CRAWL = builder.define("EnableCrawl", true);

        builder.comment("Allow players to crawl without holding a TACZ gun. EnableCrawl must also be enabled");
        ENABLE_CRAWL_WITHOUT_GUN = builder.define("EnableCrawlWithoutGun", true);

        builder.comment(
            "Maximum upward look angle in degrees while crawling with a gun. Set both crawl angle limits to 90 for the full vanilla pitch range");
        CRAWL_MAX_UP_ANGLE = builder.defineInRange("CrawlMaxUpAngle", 45.0D, 0.0D, 90.0D);

        builder.comment("Maximum downward look angle in degrees while crawling with a gun");
        CRAWL_MAX_DOWN_ANGLE = builder.defineInRange("CrawlMaxDownAngle", 30.0D, 0.0D, 90.0D);

        builder.comment(
            "Use only move or stand hip-fire inaccuracy while not on the ground, including jumping and falling. Ignore aiming, prone, and sneaking inaccuracy");
        USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE = builder.define("UseHipFireInaccuracyWhileAirborne", false);

        builder.comment("Enable the recipe limit of default gunsmith table or not");
        ENABLE_TABLE_FILTER = builder.define("EnableDefaultGunSmithTableFilter", true);

        builder
            .comment("The farthest distance for the server to send bullet hit block effect packets, 0 means no limit");
        BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE = builder
            .defineInRange("BulletHitBlockEffectSendDistance", 128, 0, Integer.MAX_VALUE);

        builder.comment("[Debug Option] Do server-side network check while shooting or not");
        SERVER_SHOOT_NETWORK_V = builder.define("ServerShootNetworkCheck", true);

        builder.comment(
            "[Debug Option] Do server-side shoot cooldown check or not."
                + " WARNING: Close this will disable the shoot cooldown check in server-side at all,"
                + " which may lead to potential for cheating."
                + " Only consider to close this when you can't shoot at all sometimes.");
        SERVER_SHOOT_COOLDOWN_V = builder.define("ServerShootCooldownCheck", true);
        builder.pop();
    }

    /**
     * 客户端收到 {@code ServerMessageSyncConfig} 后调用：把服务端的值写进本地内存缓存。
     * <p>
     * 注意：<b>不</b>调用 {@code save()}，因此本地 {@code tacz-server.cfg} 不会被服务端值覆盖
     * （与上游 Forge 的 SERVER 配置在客户端只读的语义一致）。
     */
    public static void applyFromServer(ServerMessageSyncConfig message) {
        message.apply();
    }

    private SyncConfig() {}
}
