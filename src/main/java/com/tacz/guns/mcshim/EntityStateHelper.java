package com.tacz.guns.mcshim;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 由 1.7.10 的离散实体状态推导 {@link EntityPose}。
 *
 * <h2>对应 1.20 的什么</h2>
 * 1.20 的 {@code Entity#getPose()} 内部逻辑（{@code Entity#updatePlayerPose} /
 * {@code LivingEntity#getPose}）大致是：
 * 
 * <pre>
 *   if (isPassenger)                     → STANDING（骑乘时不改）
 *   if (isFallFlying)                    → FALL_FLYING
 *   if (isSleeping)                      → SLEEPING
 *   if (isSwimming)                      → SWIMMING
 *   if (isAutoSpinAttack)                → SPIN_ATTACK
 *   if (isCrouching / isShiftKeyDown)    → CROUCHING
 *   if (isInWater &amp;&amp; sprinting)         → SWIMMING
 *   else                                 → STANDING
 * </pre>
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 没有 {@code getPose()}，只能用下面这些布尔拼出来：
 * <ul>
 * <li>{@code EntityPlayer#isPlayerSleeping()} → {@link EntityPose#SLEEPING}</li>
 * <li>{@code Entity#isSneaking()} → {@link EntityPose#CROUCHING}</li>
 * <li>{@code Entity#isInWater()} + {@code Entity#isSprinting()} → {@link EntityPose#SWIMMING}</li>
 * <li>{@code EntityLivingBase#getHealth() <= 0} → {@link EntityPose#DYING}</li>
 * <li>其余 → {@link EntityPose#STANDING}</li>
 * </ul>
 *
 * <h2>行为差异 / 降级点（重要）</h2>
 * <ul>
 * <li><b>「爬行」（crawl）在 1.7.10 上没有原生状态</b>：上游用
 * {@code player.setForcedPose(Pose.SWIMMING)} 表示趴下，1.7.10 里没有这个字段。
 * 本类提供 {@link #ENTITY_POSE_OVERRIDES} 钩子：移植后的 crawl 逻辑把自己的状态塞进这张表，
 * {@link #getPose} 会优先返回它。这样 {@code Pose.SWIMMING} 用来表示「趴下」的上游语义得以保留。</li>
 * <li>{@link EntityPose#FALL_FLYING}/{@link EntityPose#SPIN_ATTACK}/{@link EntityPose#LONG_JUMPING}
 * 在 1.7.10 永远推不出来。</li>
 * <li>顺序与 1.20 不同：1.20 先判 fall-flying，再判 sleeping；本类先判 sleeping（因为 1.7.10 睡觉时
 * 一定不是游泳）。对 1.20 可出现的组合没有差异。</li>
 * </ul>
 */
public final class EntityStateHelper {

    /**
     * 外部强制姿势钩子：key 是实体实例（建议用 {@code IdentityHashMap} 语义，实际就是按对象身份），
     * value 是要强制返回的姿势。值为 {@code null} 表示不覆盖。
     *
     * <p>
     * 移植特有的产物：1.7.10 没有 {@code Entity#setForcedPose(Pose)}。上游的
     * {@code LivingEntityCrawl} / {@code LocalPlayerCrawl} 用它表示「趴下」，移植后改为读写本表。
     * <b>请在实体离开世界时移除对应条目，避免内存泄漏。</b>
     */
    public static final Map<Entity, EntityPose> ENTITY_POSE_OVERRIDES = Collections
        .synchronizedMap(new WeakHashMap<>());

    private EntityStateHelper() {}

    /**
     * 由 1.7.10 实体状态推导 1.20 语义的 {@link EntityPose}。
     */
    public static EntityPose getPose(Entity entity) {
        if (entity == null) {
            return EntityPose.STANDING;
        }
        EntityPose forced = ENTITY_POSE_OVERRIDES.get(entity);
        if (forced != null) {
            return forced;
        }
        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isPlayerSleeping()) {
            return EntityPose.SLEEPING;
        }
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).getHealth() <= 0.0F) {
            return EntityPose.DYING;
        }
        if (entity.isSneaking()) {
            return EntityPose.CROUCHING;
        }
        if (entity.isInWater() && entity.isSprinting()) {
            return EntityPose.SWIMMING;
        }
        return EntityPose.STANDING;
    }

    /** {@link #getPose(Entity)} 的 {@link EntityLivingBase} 重载（1.20 上游签名用的是 LivingEntity）。 */
    public static EntityPose getPose(EntityLivingBase entity) {
        return getPose((Entity) entity);
    }

    /** 设置强制姿势（对应 1.20 {@code Entity#setForcedPose(Pose)}）。传 {@code null} 清除。 */
    public static void setForcedPose(Entity entity, EntityPose pose) {
        if (entity == null) {
            return;
        }
        if (pose == null) {
            ENTITY_POSE_OVERRIDES.remove(entity);
        } else {
            ENTITY_POSE_OVERRIDES.put(entity, pose);
        }
    }

    /** 读取强制姿势（对应 1.20 {@code Entity#getForcedPose()}）。 */
    public static EntityPose getForcedPose(Entity entity) {
        return entity == null ? null : ENTITY_POSE_OVERRIDES.get(entity);
    }

    /** 实体是否处于趴下/游泳姿势（1.20 的 {@code isVisuallySwimming()} 语义）。 */
    public static boolean isVisuallySwimming(Entity entity) {
        return getPose(entity) == EntityPose.SWIMMING;
    }

    /** 实体是否蹲下（1.20 的 {@code Pose.CROUCHING}）。 */
    public static boolean isCrouching(Entity entity) {
        return getPose(entity) == EntityPose.CROUCHING;
    }
}
