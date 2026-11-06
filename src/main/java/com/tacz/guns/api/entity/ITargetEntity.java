package com.tacz.guns.api.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;

import com.tacz.guns.util.TacHitResult;

/**
 * 用于进行一些并非 {@link EntityLivingBase} 但是可被子弹击中的特殊实体的处理。
 * <p>
 * 1.7.10 移植说明：上游签名 {@code onProjectileHit(Entity, EntityHitResult, DamageSource, float)} 中的
 * {@code EntityHitResult} 由 {@link TacHitResult} 等价替代（{@code extends MovingObjectPosition}，
 * 携带 {@code entityHit}/{@code hitVec}），参数顺序与上游一致。
 */
public interface ITargetEntity {

    /**
     * @param projectile 弹射物实体
     * @param result     击中实体的命中结果
     * @param source     伤害源类型
     * @param damage     伤害值
     */
    void onProjectileHit(Entity projectile, TacHitResult result, DamageSource source, float damage);
}
