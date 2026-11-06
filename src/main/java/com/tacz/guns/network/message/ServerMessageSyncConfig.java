package com.tacz.guns.network.message;

import java.util.ArrayList;
import java.util.List;

import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：同步 {@code config/sync/SyncConfig}（上游的 {@code ModConfig.Type.SERVER}）。
 *
 * <p>
 * <b>这是移植特有类</b>（上游 1.20.1 没有这个类）：1.20 里 Forge 会在玩家登录时自动把 SERVER 配置同步给客户端；
 * 1.7.10 没有该机制，因此手写一条同步包，由 {@code network/SyncConfigSyncManager} 在
 * {@code PlayerLoggedInEvent} 时发给该玩家。
 *
 * <p>
 * 客户端收到后调用 {@link #apply()}，只覆盖内存缓存、<b>不</b>写本地文件 ——
 * 与上游「SERVER 配置在客户端只读」的语义一致。
 * 字段顺序与 {@link SyncConfig} 的配置项一一对应，改动时必须两端同步。
 */
public class ServerMessageSyncConfig extends MessageBase {

    private List<String> interactKeyWhitelistBlocks = new ArrayList<>();
    private List<String> interactKeyWhitelistEntities = new ArrayList<>();
    private List<String> interactKeyBlacklistBlocks = new ArrayList<>();
    private List<String> interactKeyBlacklistEntities = new ArrayList<>();
    private List<String> gunPegboardWhitelistItems = new ArrayList<>();
    private List<String> gunPegboardBlacklistItems = new ArrayList<>();
    private List<String> headShotAABB = new ArrayList<>();
    private boolean enableTableFilter;
    private boolean serverShootNetworkV;
    private boolean serverShootCooldownV;
    private boolean useHipFireInaccuracyWhileAirborne;
    private boolean enableCrawl;
    private boolean enableCrawlWithoutGun;
    private int bulletHitBlockEffectSendDistance;
    private int ammoBoxStackSize;
    private double damageBaseMultiplier;
    private double armorIgnoreBaseMultiplier;
    private double headShotBaseMultiplier;
    private double weightSpeedMultiplier;
    private double crawlMaxUpAngle;
    private double crawlMaxDownAngle;

    public ServerMessageSyncConfig() {}

    /**
     * 用当前服务端配置构造一份完整快照（只应在服务端调用）。
     */
    public static ServerMessageSyncConfig fromConfig() {
        ServerMessageSyncConfig message = new ServerMessageSyncConfig();
        message.interactKeyWhitelistBlocks = copyOf(SyncConfig.INTERACT_KEY_WHITELIST_BLOCKS.get());
        message.interactKeyWhitelistEntities = copyOf(SyncConfig.INTERACT_KEY_WHITELIST_ENTITIES.get());
        message.interactKeyBlacklistBlocks = copyOf(SyncConfig.INTERACT_KEY_BLACKLIST_BLOCKS.get());
        message.interactKeyBlacklistEntities = copyOf(SyncConfig.INTERACT_KEY_BLACKLIST_ENTITIES.get());
        message.gunPegboardWhitelistItems = copyOf(SyncConfig.GUN_PEGBOARD_WHITELIST_ITEMS.get());
        message.gunPegboardBlacklistItems = copyOf(SyncConfig.GUN_PEGBOARD_BLACKLIST_ITEMS.get());
        message.headShotAABB = copyOf(SyncConfig.HEAD_SHOT_AABB.get());
        message.enableTableFilter = Boolean.TRUE.equals(SyncConfig.ENABLE_TABLE_FILTER.get());
        message.serverShootNetworkV = Boolean.TRUE.equals(SyncConfig.SERVER_SHOOT_NETWORK_V.get());
        message.serverShootCooldownV = Boolean.TRUE.equals(SyncConfig.SERVER_SHOOT_COOLDOWN_V.get());
        message.useHipFireInaccuracyWhileAirborne = Boolean.TRUE
            .equals(SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.get());
        message.enableCrawl = Boolean.TRUE.equals(SyncConfig.ENABLE_CRAWL.get());
        message.enableCrawlWithoutGun = Boolean.TRUE.equals(SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.get());
        message.bulletHitBlockEffectSendDistance = safeInt(SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.get());
        message.ammoBoxStackSize = safeInt(SyncConfig.AMMO_BOX_STACK_SIZE.get());
        message.damageBaseMultiplier = safeDouble(SyncConfig.DAMAGE_BASE_MULTIPLIER.get());
        message.armorIgnoreBaseMultiplier = safeDouble(SyncConfig.ARMOR_IGNORE_BASE_MULTIPLIER.get());
        message.headShotBaseMultiplier = safeDouble(SyncConfig.HEAD_SHOT_BASE_MULTIPLIER.get());
        message.weightSpeedMultiplier = safeDouble(SyncConfig.WEIGHT_SPEED_MULTIPLIER.get());
        message.crawlMaxUpAngle = safeDouble(SyncConfig.CRAWL_MAX_UP_ANGLE.get());
        message.crawlMaxDownAngle = safeDouble(SyncConfig.CRAWL_MAX_DOWN_ANGLE.get());
        return message;
    }

    private static List<String> copyOf(List<String> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value.intValue();
    }

    private static double safeDouble(Double value) {
        return value == null ? 0D : value.doubleValue();
    }

    /**
     * 客户端应用服务端下发值（只改内存）。由 {@link SyncConfig#applyFromServer} 调用。
     */
    public void apply() {
        SyncConfig.INTERACT_KEY_WHITELIST_BLOCKS.set(interactKeyWhitelistBlocks);
        SyncConfig.INTERACT_KEY_WHITELIST_ENTITIES.set(interactKeyWhitelistEntities);
        SyncConfig.INTERACT_KEY_BLACKLIST_BLOCKS.set(interactKeyBlacklistBlocks);
        SyncConfig.INTERACT_KEY_BLACKLIST_ENTITIES.set(interactKeyBlacklistEntities);
        SyncConfig.GUN_PEGBOARD_WHITELIST_ITEMS.set(gunPegboardWhitelistItems);
        SyncConfig.GUN_PEGBOARD_BLACKLIST_ITEMS.set(gunPegboardBlacklistItems);
        SyncConfig.HEAD_SHOT_AABB.set(headShotAABB);
        SyncConfig.ENABLE_TABLE_FILTER.set(enableTableFilter);
        SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.set(bulletHitBlockEffectSendDistance);
        SyncConfig.SERVER_SHOOT_NETWORK_V.set(serverShootNetworkV);
        SyncConfig.SERVER_SHOOT_COOLDOWN_V.set(serverShootCooldownV);
        SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.set(useHipFireInaccuracyWhileAirborne);
        SyncConfig.DAMAGE_BASE_MULTIPLIER.set(damageBaseMultiplier);
        SyncConfig.ARMOR_IGNORE_BASE_MULTIPLIER.set(armorIgnoreBaseMultiplier);
        SyncConfig.HEAD_SHOT_BASE_MULTIPLIER.set(headShotBaseMultiplier);
        SyncConfig.WEIGHT_SPEED_MULTIPLIER.set(weightSpeedMultiplier);
        SyncConfig.AMMO_BOX_STACK_SIZE.set(ammoBoxStackSize);
        SyncConfig.ENABLE_CRAWL.set(enableCrawl);
        SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.set(enableCrawlWithoutGun);
        SyncConfig.CRAWL_MAX_UP_ANGLE.set(crawlMaxUpAngle);
        SyncConfig.CRAWL_MAX_DOWN_ANGLE.set(crawlMaxDownAngle);
    }

    @Override
    protected void write(PacketBuffer buf) {
        writeList(buf, interactKeyWhitelistBlocks);
        writeList(buf, interactKeyWhitelistEntities);
        writeList(buf, interactKeyBlacklistBlocks);
        writeList(buf, interactKeyBlacklistEntities);
        writeList(buf, gunPegboardWhitelistItems);
        writeList(buf, gunPegboardBlacklistItems);
        writeList(buf, headShotAABB);
        buf.writeBoolean(enableTableFilter);
        buf.writeBoolean(serverShootNetworkV);
        buf.writeBoolean(serverShootCooldownV);
        buf.writeBoolean(useHipFireInaccuracyWhileAirborne);
        buf.writeBoolean(enableCrawl);
        buf.writeBoolean(enableCrawlWithoutGun);
        buf.writeInt(bulletHitBlockEffectSendDistance);
        buf.writeInt(ammoBoxStackSize);
        buf.writeDouble(damageBaseMultiplier);
        buf.writeDouble(armorIgnoreBaseMultiplier);
        buf.writeDouble(headShotBaseMultiplier);
        buf.writeDouble(weightSpeedMultiplier);
        buf.writeDouble(crawlMaxUpAngle);
        buf.writeDouble(crawlMaxDownAngle);
    }

    @Override
    protected void read(PacketBuffer buf) {
        interactKeyWhitelistBlocks = readList(buf);
        interactKeyWhitelistEntities = readList(buf);
        interactKeyBlacklistBlocks = readList(buf);
        interactKeyBlacklistEntities = readList(buf);
        gunPegboardWhitelistItems = readList(buf);
        gunPegboardBlacklistItems = readList(buf);
        headShotAABB = readList(buf);
        enableTableFilter = buf.readBoolean();
        serverShootNetworkV = buf.readBoolean();
        serverShootCooldownV = buf.readBoolean();
        useHipFireInaccuracyWhileAirborne = buf.readBoolean();
        enableCrawl = buf.readBoolean();
        enableCrawlWithoutGun = buf.readBoolean();
        bulletHitBlockEffectSendDistance = buf.readInt();
        ammoBoxStackSize = buf.readInt();
        damageBaseMultiplier = buf.readDouble();
        armorIgnoreBaseMultiplier = buf.readDouble();
        headShotBaseMultiplier = buf.readDouble();
        weightSpeedMultiplier = buf.readDouble();
        crawlMaxUpAngle = buf.readDouble();
        crawlMaxDownAngle = buf.readDouble();
    }

    private static void writeList(PacketBuffer buf, List<String> list) {
        if (list == null) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(list.size());
        for (String value : list) {
            buf.writeUtf(value == null ? "" : value);
        }
    }

    private static List<String> readList(PacketBuffer buf) {
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>(Math.max(0, Math.min(size, 4096)));
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }

    public static void handle(ServerMessageSyncConfig message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> SyncConfig.applyFromServer(message));
    }
}
