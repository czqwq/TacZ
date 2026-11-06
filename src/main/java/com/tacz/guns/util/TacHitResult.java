package com.tacz.guns.util;

import net.minecraft.util.MovingObjectPosition;

import com.tacz.guns.entity.EntityKineticBullet;

/**
 * 带「是否爆头」标记的实体命中结果。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>上游继承 1.20 的 {@code net.minecraft.world.phys.EntityHitResult}
 * （构造参数为 {@code (Entity, Vec3)}，并提供 {@code getEntity()}）。</li>
 * <li>1.7.10 <b>没有</b> {@code EntityHitResult}/{@code HitResult} 体系，
 * 对应的「射线命中结果」是 {@link MovingObjectPosition}，
 * 其 {@code typeOfHit = ENTITY} 的构造器签名正好也是 {@code (Entity, Vec3)}，
 * 并且 {@link MovingObjectPosition#entityHit} 就是上游 {@code getEntity()} 的等价物。</li>
 * <li>因此这里改为 {@code extends MovingObjectPosition}，<b>保留类名、构造签名与
 * {@link #isHeadshot()}</b>；{@code getEntity()} 由父类的 public 字段
 * {@code entityHit} 代替（1.7.10 惯例）。</li>
 * <li>语义差异：1.20 的 {@code EntityHitResult} 还携带 {@code getLocation()}，
 * 1.7.10 侧由 public 字段 {@code hitVec} 提供，值相同。</li>
 * <li>可安全作为 {@code api/entity/ITargetEntity} 的 {@code EntityHitResult} 等价物：
 * 本类在 common 包、不依赖任何 client 类，且实际实例只由 {@code EntityKineticBullet} 的实体命中路径构造。</li>
 * </ul>
 */
public class TacHitResult extends MovingObjectPosition {

    private final boolean headshot;

    public TacHitResult(EntityKineticBullet.EntityResult result) {
        // 上游：super(result.getEntity(), result.getHitPos())。
        // 1.7.10 的 EntityResult（entity 包）同样返回 net.minecraft.util.Vec3，签名逐字对齐。
        super(result.getEntity(), result.getHitPos());
        this.headshot = result.isHeadshot();
    }

    public boolean isHeadshot() {
        return this.headshot;
    }
}
