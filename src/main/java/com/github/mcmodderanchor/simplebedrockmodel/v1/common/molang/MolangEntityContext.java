// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/molang/MolangEntityContext.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.molang;

import net.minecraft.entity.Entity;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.binding.QueryBinding;
import com.tacz.guns.mcshim.Mth;

/**
 * 代表一个实体。预置了一些常用查询。
 * <p>
 * 1.7.10 移植说明：本类的每个查询都直接落到 {@code Entity}/{@code World} 的 1.7.10 方法上。
 * 1.7.10 没有 1.20 的若干实体状态标志（游泳 / 静默 / 无重力 / 发光），这几个查询按
 * {@code TODO(L2)} 降级为常量 0.0，其余查询语义与上游一致。
 *
 * @param <T> 实体类型
 */
@SuppressWarnings("unused")
public class MolangEntityContext<T extends Entity> extends MolangContext<T> {

    public MolangEntityContext() {}

    public MolangEntityContext(T entity) {
        super(entity);
    }

    // ==================== Entity 状态 ====================

    /** TODO(L2): 1.20 {@code Entity#onGround()} → 1.7.10 的 {@code onGround} 字段。 */
    @QueryBinding("is_on_ground")
    public double isOnGround() {
        T e = getEntity();
        return e != null && e.onGround ? 1.0 : 0.0;
    }

    /** TODO(L2): 1.20 {@code Entity#isAlive()} → 1.7.10 {@code isEntityAlive()}。 */
    @QueryBinding("is_alive")
    public double isAlive() {
        T e = getEntity();
        return e != null && e.isEntityAlive() ? 1.0 : 0.0;
    }

    @QueryBinding("is_in_water")
    public double isInWater() {
        T e = getEntity();
        return e != null && e.isInWater() ? 1.0 : 0.0;
    }

    /**
     * TODO(L2): 1.20 {@code Entity#isInWaterRainOrBubble()} 在 1.7.10 无对应。
     * 1.7.10 用「在水中，或所在方块能看到天空且世界在下雨」近似（不含气泡柱，1.7.10 没有气泡柱）。
     */
    @QueryBinding("is_in_water_or_rain")
    public double isInWaterOrRain() {
        T e = getEntity();
        if (e == null) {
            return 0.0;
        }
        if (e.isInWater()) {
            return 1.0;
        }
        return e.worldObj.isRaining()
            && e.worldObj.canBlockSeeTheSky(Mth.floor(e.posX), Mth.floor(e.posY), Mth.floor(e.posZ)) ? 1.0 : 0.0;
    }

    /** TODO(L2): 1.20 {@code Entity#isInLava()} → 1.7.10 {@code handleLavaMovement()}。 */
    @QueryBinding("is_in_lava")
    public double isInLava() {
        T e = getEntity();
        return e != null && e.handleLavaMovement() ? 1.0 : 0.0;
    }

    /** TODO(L2): 1.20 {@code Entity#isOnFire()} → 1.7.10 {@code isBurning()}。 */
    @QueryBinding("is_on_fire")
    public double isOnFire() {
        T e = getEntity();
        return e != null && e.isBurning() ? 1.0 : 0.0;
    }

    @QueryBinding("is_sprinting")
    public double isSprinting() {
        T e = getEntity();
        return e != null && e.isSprinting() ? 1.0 : 0.0;
    }

    /** TODO(L2): 1.20 {@code Entity#isCrouching()} → 1.7.10 {@code isSneaking()}（同一语义）。 */
    @QueryBinding("is_sneaking")
    public double isSneaking() {
        T e = getEntity();
        return e != null && e.isSneaking() ? 1.0 : 0.0;
    }

    /** TODO(L2): 1.7.10 无游泳姿态（1.13 才加），恒返回 0.0。 */
    @QueryBinding("is_swimming")
    public double isSwimming() {
        return 0.0;
    }

    /** TODO(L2): 1.20 {@code Entity#isPassenger()} → 1.7.10 {@code isRiding()}。 */
    @QueryBinding("is_riding")
    public double isRiding() {
        T e = getEntity();
        return e != null && e.isRiding() ? 1.0 : 0.0;
    }

    /** TODO(L2): 1.7.10 无 {@code isSilent()}（1.10 才加），恒返回 0.0。 */
    @QueryBinding("is_silent")
    public double isSilent() {
        return 0.0;
    }

    /** TODO(L2): 1.7.10 无 {@code isNoGravity()}（1.14 才加），恒返回 0.0。 */
    @QueryBinding("is_no_gravity")
    public double isNoGravity() {
        return 0.0;
    }

    @QueryBinding("is_invisible")
    public double isInvisible() {
        T e = getEntity();
        return e != null && e.isInvisible() ? 1.0 : 0.0;
    }

    /** TODO(L2): 1.7.10 无发光标记（1.13 才加），恒返回 0.0。 */
    @QueryBinding("is_glowing")
    public double isGlowing() {
        return 0.0;
    }

    // ==================== Entity 属性 ====================

    /** TODO(L2): 1.20 {@code Entity#tickCount} → 1.7.10 {@code ticksExisted}。 */
    @QueryBinding("life_time")
    public double lifeTime() {
        T e = getEntity();
        return e != null ? (double) e.ticksExisted : 0.0;
    }

    /** TODO(L2): 1.20 {@code Entity#getYRot()} → 1.7.10 {@code rotationYaw} 字段。 */
    @QueryBinding("body_y_rotation")
    public double bodyYRotation() {
        T e = getEntity();
        return e != null ? (double) e.rotationYaw : 0.0;
    }

    // ==================== 位置 ====================

    /** TODO(L2): 1.20 {@code Entity#getX()} → 1.7.10 {@code posX} 字段。 */
    @QueryBinding("position_x")
    public double positionX() {
        T e = getEntity();
        return e != null ? e.posX : 0.0;
    }

    @QueryBinding("position_y")
    public double positionY() {
        T e = getEntity();
        return e != null ? e.posY : 0.0;
    }

    @QueryBinding("position_z")
    public double positionZ() {
        T e = getEntity();
        return e != null ? e.posZ : 0.0;
    }

    // ==================== Level / 时间天气 ====================

    /** TODO(L2): 1.20 {@code Entity#level()} → 1.7.10 {@code worldObj}。 */
    @QueryBinding("time_of_day")
    public double timeOfDay() {
        T e = getEntity();
        return e != null ? (double) (e.worldObj.getWorldTime() % 24000L) : 0.0;
    }

    /** TODO(L2): 1.20 {@code Level#getGameTime()} → 1.7.10 {@code getTotalWorldTime()}。 */
    @QueryBinding("time_stamp")
    public double timeStamp() {
        T e = getEntity();
        return e != null ? (double) e.worldObj.getTotalWorldTime() : 0.0;
    }

    /** TODO(L2): 1.20 {@code Level#isDay()} → 1.7.10 {@code isDaytime()}。 */
    @QueryBinding("is_day")
    public double isDay() {
        T e = getEntity();
        return e != null && e.worldObj.isDaytime() ? 1.0 : 0.0;
    }

    @QueryBinding("is_night")
    public double isNight() {
        T e = getEntity();
        return e != null && !e.worldObj.isDaytime() ? 1.0 : 0.0;
    }

    @QueryBinding("is_raining")
    public double isRaining() {
        T e = getEntity();
        return e != null && e.worldObj.isRaining() ? 1.0 : 0.0;
    }

    @QueryBinding("is_thundering")
    public double isThundering() {
        T e = getEntity();
        return e != null && e.worldObj.isThundering() ? 1.0 : 0.0;
    }

    @QueryBinding("moon_phase")
    public double moonPhase() {
        T e = getEntity();
        return e != null ? (double) e.worldObj.getMoonPhase() : 0.0;
    }
}
