package com.tacz.guns.entity.sync.core;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * 搬运自上游 1.20.1 {@code entity/sync/core/SyncedClassKey.java}（MrCrayfish Framework，LGPL）。
 *
 * <p>
 * 1.7.10 适配：{@code LivingEntity} → {@link EntityLivingBase}；
 * {@code ResourceLocation} 换 1.7.10 包。注意 1.7.10 的 {@code new ResourceLocation("living_entity")}
 * 会解析成 {@code minecraft:living_entity}，与上游语义等价（内部 key 名不写入存档/NBT）。
 */
public record SyncedClassKey<E extends Entity> (Class<E> entityClass, ResourceLocation id) {

    public static final SyncedClassKey<EntityLivingBase> LIVING_ENTITY = new SyncedClassKey<>(
        EntityLivingBase.class,
        new ResourceLocation("living_entity"));

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SyncedClassKey<?> that = (SyncedClassKey<?>) o;
        return this.entityClass.getName()
            .equals(that.entityClass.getName());
    }

    @Override
    public int hashCode() {
        return this.entityClass.getName()
            .hashCode();
    }
}
