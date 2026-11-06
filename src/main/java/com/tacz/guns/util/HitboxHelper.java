package com.tacz.guns.util;

import java.util.LinkedList;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import com.tacz.guns.api.entity.ITargetEntity;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.mcshim.Mth;

/**
 * 服务端 hitbox 延迟补偿：缓存玩家最近若干 tick 的位置 / 命中箱 / 速度，并按 ping 回看历史。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.world.entity.player.Player} → {@link EntityPlayer}；
 * {@code net.minecraft.server.level.ServerPlayer} → {@link EntityPlayerMP}。</li>
 * <li>{@code net.minecraft.world.phys.AABB} → {@link AxisAlignedBB}；
 * {@code net.minecraft.world.phys.Vec3} → {@link Vec3}（字段 {@code x/y/z} → {@code xCoord/yCoord/zCoord}）。</li>
 * <li>{@code net.minecraft.util.Mth} → {@link Mth}（1.7.10 的 {@code MathHelper} 缺 lerp/frac/sign，
 * 本工程在 mcshim 里提供了等价实现）。</li>
 * <li>{@code entity.position()} → {@link Vec3#createVectorHelper} ＋ {@code posX/posY/posZ}；
 * {@code entity.getX()/xOld} → {@code posX/prevPosX}（{@code y/z} 同理）。</li>
 * <li>{@code AABB.expandTowards(dx,dy,dz)} → {@link AxisAlignedBB#addCoord(double, double, double)}；
 * {@code AABB.move(Vec3)} → {@link AxisAlignedBB#offset(double, double, double)}；
 * {@code Vec3.multiply(x,y,z)} → 本文件私有的 {@link #scale(Vec3, double, double, double)}。</li>
 * <li>{@code entity.getVehicle()} → {@code entity.ridingEntity}；
 * {@code ServerPlayer.latency} → {@link EntityPlayerMP#ping}（1.7.10 同名 public 字段，单位 ms）。</li>
 * <li>{@code entity.isCrouching()} → {@link Entity#isSneaking()}（1.7.10 只有「潜行」这一个蹲伏状态）。</li>
 * <li><b>降级点：{@code isSpectator()}</b>。1.20 的旁观者模式（gamemode 3）在 1.7.10 <b>不存在</b>
 * （1.8 才加入），因此 {@code player.isSpectator()} 恒为 {@code false}，
 * {@link #onPlayerTick} 里「旁观者清空缓存」的分支成为死分支，保留结构以便未来接入权限插件。</li>
 * <li>签名对齐点：{@code event/HitboxHelperEvent} 已按 {@code onPlayerTick(EntityPlayer)} /
 * {@code onPlayerLoggedOut(EntityPlayer)} 调用；{@code InaccuracyType} 调用
 * {@code getPlayerVelocity(EntityPlayer)}（注意 1.7.10 的 {@link Vec3} 求长度是
 * {@code lengthVector()} 而不是 {@code length()}）。</li>
 * </ul>
 */
public final class HitboxHelper {

    // 玩家位置缓存表
    private static final WeakHashMap<EntityPlayer, LinkedList<Vec3>> PLAYER_POSITION = new WeakHashMap<>();
    // 玩家命中箱缓存表
    private static final WeakHashMap<EntityPlayer, LinkedList<AxisAlignedBB>> PLAYER_HITBOXES = new WeakHashMap<>();
    // 玩家速度缓存表
    private static final WeakHashMap<EntityPlayer, LinkedList<Vec3>> PLAYER_VELOCITY = new WeakHashMap<>();
    // 命中箱缓存 Tick 上限
    private static final int SAVE_TICK = Mth
        .floor(OtherConfig.SERVER_HITBOX_LATENCY_MAX_SAVE_MS.get() / 1000 * 20 + 0.5);

    public static void onPlayerTick(EntityPlayer player) {
        if (isSpectator(player)) {
            PLAYER_POSITION.remove(player);
            PLAYER_HITBOXES.remove(player);
            PLAYER_VELOCITY.remove(player);
            return;
        }
        LinkedList<Vec3> positions = PLAYER_POSITION.computeIfAbsent(player, p -> new LinkedList<>());
        LinkedList<AxisAlignedBB> boxes = PLAYER_HITBOXES.computeIfAbsent(player, p -> new LinkedList<>());
        LinkedList<Vec3> velocities = PLAYER_VELOCITY.computeIfAbsent(player, p -> new LinkedList<>());
        positions.addFirst(Vec3.createVectorHelper(player.posX, player.posY, player.posZ));
        boxes.addFirst(player.boundingBox);
        velocities.addFirst(getPlayerVelocity(player));
        // Position 用于速度计算，所以只需要缓存 2 个位置
        if (positions.size() > 2) {
            positions.removeLast();
        }
        // 命中箱和速度缓存数量限制
        if (boxes.size() > SAVE_TICK) {
            boxes.removeLast();
            velocities.removeLast();
        }
    }

    public static void onPlayerLoggedOut(EntityPlayer player) {
        PLAYER_POSITION.remove(player);
        PLAYER_HITBOXES.remove(player);
        PLAYER_VELOCITY.remove(player);
    }

    public static Vec3 getPlayerVelocity(EntityPlayer entity) {
        LinkedList<Vec3> positions = PLAYER_POSITION.computeIfAbsent(entity, player -> new LinkedList<>());
        if (positions.size() > 1) {
            Vec3 currPos = positions.getFirst();
            Vec3 prevPos = positions.getLast();
            return Vec3.createVectorHelper(
                currPos.xCoord - prevPos.xCoord,
                currPos.yCoord - prevPos.yCoord,
                currPos.zCoord - prevPos.zCoord);
        }
        return Vec3.createVectorHelper(0, 0, 0);
    }

    public static AxisAlignedBB getBoundingBox(EntityPlayer entity, int ping) {
        if (PLAYER_HITBOXES.containsKey(entity)) {
            LinkedList<AxisAlignedBB> boxes = PLAYER_HITBOXES.get(entity);
            int index = Mth.clamp(ping, 0, boxes.size() - 1);
            return boxes.get(index);
        }
        return entity.boundingBox;
    }

    public static Vec3 getVelocity(EntityPlayer entity, int ping) {
        if (PLAYER_VELOCITY.containsKey(entity)) {
            LinkedList<Vec3> velocities = PLAYER_VELOCITY.get(entity);
            int index = Mth.clamp(ping, 0, velocities.size() - 1);
            return velocities.get(index);
        }
        return getPlayerVelocity(entity);
    }

    public static AxisAlignedBB getFixedBoundingBox(Entity entity, Entity owner) {
        AxisAlignedBB boundingBox = entity.boundingBox;
        Vec3 velocity = Vec3.createVectorHelper(
            entity.posX - entity.prevPosX,
            entity.posY - entity.prevPosY,
            entity.posZ - entity.prevPosZ);
        // hitbox 延迟补偿。只有射击者是玩家（且被击中者也是玩家）才进行此类延迟补偿计算
        if (OtherConfig.SERVER_HITBOX_LATENCY_FIX.get() && entity instanceof EntityPlayerMP
            && owner instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            EntityPlayerMP serverPlayerOwner = (EntityPlayerMP) owner;
            int ping = Mth.floor((serverPlayerOwner.ping / 1000.0) * 20.0 + 0.5);
            boundingBox = getBoundingBox(player, ping);
            velocity = getVelocity(player, ping);
        }
        // 应用蹲伏导致的 hitbox 变形
        double expandHeight = entity instanceof EntityPlayer && !entity.isSneaking() ? 0.0625 : 0.0;
        boundingBox = boundingBox.addCoord(0, expandHeight, 0);
        // 根据速度一定程度地扩展 hitbox
        boundingBox = boundingBox.addCoord(velocity.xCoord, velocity.yCoord, velocity.zCoord);
        // 玩家 hitbox 修正，可以通过 Config 调整
        double playerHitboxOffset = OtherConfig.SERVER_HITBOX_OFFSET.get();
        if (entity instanceof EntityPlayerMP) {
            if (entity.ridingEntity != null) {
                boundingBox = offset(
                    boundingBox,
                    scale(velocity, playerHitboxOffset / 2, playerHitboxOffset / 2, playerHitboxOffset / 2));
            }
            boundingBox = offset(
                boundingBox,
                scale(velocity, playerHitboxOffset, playerHitboxOffset, playerHitboxOffset));
        }
        // 给所有实体统一应用的 Hitbox 偏移，其数值为实验得出的定值。
        if (entity.ridingEntity != null || entity instanceof ITargetEntity) {
            boundingBox = offset(boundingBox, scale(velocity, -2.5, -2.5, -2.5));
        }
        boundingBox = offset(boundingBox, scale(velocity, -5, -5, -5));
        return boundingBox;
    }

    /** 1.7.10 的 {@link Vec3} 没有 {@code multiply(x,y,z)}，这里用 {@code addVector} 组合出等价的缩放。 */
    private static Vec3 scale(Vec3 vec, double x, double y, double z) {
        return Vec3.createVectorHelper(vec.xCoord * x, vec.yCoord * y, vec.zCoord * z);
    }

    /** 等价 1.20 的 {@code AABB.move(Vec3)}。 */
    private static AxisAlignedBB offset(AxisAlignedBB box, Vec3 vec) {
        return box.offset(vec.xCoord, vec.yCoord, vec.zCoord);
    }

    /**
     * 1.7.10 没有旁观者模式（1.8 才加入），因此恒为 {@code false}。
     *
     * <p>
     * TODO(port)：若未来通过权限插件模拟旁观者，可在此接入。
     */
    private static boolean isSpectator(EntityPlayer player) {
        return false;
    }
}
