package com.tacz.guns.resource.pojo.data.gun;

import java.util.Map;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.util.HitboxHelper;

/**
 * 不准确度状态（站立 / 移动 / 潜行 / 趴下 / 瞄准）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/pojo/data/gun/InaccuracyType.java}。
 *
 * <p>
 * <b>1.7.10 差异（姿势判定）</b>：上游用 {@code LivingEntity#getPose()} 与
 * {@code Pose.SWIMMING}/{@code Pose.CROUCHING}。1.7.10 没有任何 Pose 概念，可用的等价状态只有：
 * <ul>
 * <li>{@code Pose.CROUCHING}（潜行）→ {@link EntityLivingBase#isSneaking()}，语义完全等价；</li>
 * <li>{@code Pose.SWIMMING}（上游借它表示「趴下」）→ <b>1.7.10 无法表达</b>：
 * 1.7.10 没有爬行/趴下姿态，也没有 {@code isSwimming()}。因此 {@link #LIE} 在 1.7.10 上
 * 永远不会被返回，对应的 {@code lie} 不准确度配置项成为死配置（默认值仍保留，便于枪包向前兼容）。</li>
 * </ul>
 *
 * <p>
 * 移动判定：上游用 {@code walkDist - walkDistO}，1.7.10 对应物是
 * {@code distanceWalkedModified - prevDistanceWalkedModified}。
 */
public enum InaccuracyType {

    /**
     * 站立不动
     */
    @SerializedName("stand")
    STAND,
    /**
     * 移动
     */
    @SerializedName("move")
    MOVE,
    /**
     * 潜行，认为是其他 FPS 游戏中的半蹲
     */
    @SerializedName("sneak")
    SNEAK,
    /**
     * 趴下。1.7.10 无此姿态，永远不返回（见类注释）
     */
    @SerializedName("lie")
    LIE,
    /**
     * 瞄准状态
     */
    @SerializedName("aim")
    AIM;

    /**
     * 获取当前的不准确度状态
     *
     * @param livingEntity 射手
     * @return 不准度情况
     */
    public static InaccuracyType getInaccuracyType(EntityLivingBase livingEntity) {
        if (!livingEntity.onGround && SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.get()) {
            return isMove(livingEntity) ? InaccuracyType.MOVE : InaccuracyType.STAND;
        }
        float aimingProgress = IGunOperator.fromLivingEntity(livingEntity)
            .getSynAimingProgress();
        // 瞄准优先于地面姿态和移动状态。
        if (aimingProgress == 1.0f) {
            return InaccuracyType.AIM;
        }
        // 1.7.10 差异：CROUCHING（上游 getPose()）等价于 isSneaking()；
        // SWIMMING（上游用来表示趴下）在 1.7.10 不存在，故不判定 LIE。
        if (livingEntity.isSneaking()) {
            return InaccuracyType.SNEAK;
        }
        if (isMove(livingEntity)) {
            return InaccuracyType.MOVE;
        }
        return InaccuracyType.STAND;
    }

    public static Map<InaccuracyType, Float> getDefaultInaccuracy() {
        Map<InaccuracyType, Float> inaccuracy = Maps.newHashMap();
        inaccuracy.put(InaccuracyType.STAND, 5f);
        inaccuracy.put(InaccuracyType.MOVE, 5.75f);
        inaccuracy.put(InaccuracyType.SNEAK, 3.5f);
        inaccuracy.put(InaccuracyType.LIE, 2.5f);
        inaccuracy.put(InaccuracyType.AIM, 0.15f);
        return inaccuracy;
    }

    private static boolean isMove(EntityLivingBase livingEntity) {
        // 1.7.10 差异：walkDist / walkDistO → distanceWalkedModified / prevDistanceWalkedModified
        double distance = Math.abs(livingEntity.distanceWalkedModified - livingEntity.prevDistanceWalkedModified);
        if (livingEntity instanceof EntityPlayer player) {
            // 1.7.10 差异：net.minecraft.util.Vec3 没有 length()，对应物是 lengthVector()
            distance = HitboxHelper.getPlayerVelocity(player)
                .lengthVector();
        }
        return distance > 0.05f;
    }

    public boolean isAim() {
        return this == AIM;
    }
}
