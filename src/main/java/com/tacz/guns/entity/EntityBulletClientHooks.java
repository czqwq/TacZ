package com.tacz.guns.entity;

import java.util.function.Consumer;

import net.minecraft.entity.Entity;

import org.jetbrains.annotations.Nullable;

/**
 * common 侧 → client 侧子弹视觉钩子（1.7.10 编译期边界要求：common 包不得 import {@code com.tacz.guns.client.**}）。
 *
 * <p>
 * 上游 1.20.1 {@code EntityKineticBullet#tick} 里用
 * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AmmoParticleSpawner.addParticle(this))}
 * 生成曳光弹/弹道粒子（见上游 EntityKineticBullet.java:250-252）。1.7.10 无 {@code DistExecutor}，
 * 改为本钩子：{@link EntityKineticBullet#onUpdate()} 在客户端每 tick 调一次
 * {@link #spawnAmmoParticle(Entity)}，若客户端未注入实现则静默跳过。
 *
 * <p>
 * client 侧注入点（client-port 工作流）：在 ClientProxy / 客户端粒子管理器初始化时赋值
 * 
 * <pre>
 * {@code
 * EntityBulletClientHooks.ammoParticleSpawner = bullet -> AmmoParticleSpawner.addParticle((EntityKineticBullet) bullet);
 * }
 * </pre>
 */
public final class EntityBulletClientHooks {

    /**
     * 客户端弹道粒子生成器（入参为 {@link EntityKineticBullet}）。null 表示客户端未注入，跳过。
     */
    @Nullable
    public static Consumer<Entity> ammoParticleSpawner = null;

    private EntityBulletClientHooks() {}

    public static void spawnAmmoParticle(Entity bullet) {
        Consumer<Entity> consumer = ammoParticleSpawner;
        if (consumer != null && bullet != null) {
            consumer.accept(bullet);
        }
    }
}
