package com.tacz.guns.entity.sync.core;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 搬运自上游 1.20.1 {@code entity/sync/core/SyncedDataKey.java}（MrCrayfish Framework，LGPL）。
 *
 * <p>
 * 1.7.10 适配：{@code Entity} 换 1.7.10 包、{@code ResourceLocation} 换 1.7.10 包、
 * fastutil {@code Pair} → commons-lang3 {@link Pair}（lead 裁决）。record 由 jvmDowngrader 降级到 Java 8。
 */
public record SyncedDataKey<E extends Entity, T> (Pair<ResourceLocation, ResourceLocation> pairKey, ResourceLocation id,
    SyncedClassKey<E> classKey, IDataSerializer<T> serializer, Supplier<T> defaultValueSupplier, boolean save,
    boolean persistent, SyncMode syncMode) {

    public static <E extends Entity, T> Builder<E, T> builder(SyncedClassKey<E> entityClass,
        IDataSerializer<T> serializer) {
        return new Builder<>(entityClass, serializer);
    }

    public void setValue(E entity, T value) {
        SyncedEntityData.instance()
            .set(entity, this, value);
    }

    public T getValue(E entity) {
        return SyncedEntityData.instance()
            .get(entity, this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SyncedDataKey<?, ?> that = (SyncedDataKey<?, ?>) o;
        return Objects.equals(this.pairKey, that.pairKey);
    }

    @Override
    public int hashCode() {
        return this.pairKey.hashCode();
    }

    public enum SyncMode {

        /** 不同步，仅服务端可用。 */
        NONE(false, false),
        /** 同步给所有玩家（含数据持有者）；非玩家实体只发给 tracking 玩家。 */
        ALL(true, true),
        /** 只同步给 tracking 玩家，不发给数据持有者自己。 */
        TRACKING_ONLY(true, false),
        /** 只发给数据持有者自己，不发给 tracking 玩家。 */
        SELF_ONLY(false, true);

        final boolean tracking;
        final boolean self;

        SyncMode(boolean tracking, boolean self) {
            this.tracking = tracking;
            this.self = self;
        }

        public boolean isTracking() {
            return this.tracking;
        }

        public boolean isSelf() {
            return this.self;
        }
    }

    public static class Builder<E extends Entity, T> {

        private final SyncedClassKey<E> classKey;
        private final IDataSerializer<T> serializer;
        private ResourceLocation id;
        private Supplier<T> defaultValueSupplier;
        private boolean save = false;
        private boolean persistent = true;
        private SyncMode syncMode = SyncMode.ALL;

        private Builder(SyncedClassKey<E> classKey, IDataSerializer<T> serializer) {
            this.classKey = classKey;
            this.serializer = serializer;
        }

        public SyncedDataKey<E, T> build() {
            Validate.notNull(this.id, "Missing 'id' when building synced data key");
            Validate.notNull(this.defaultValueSupplier, "Missing 'defaultValueSupplier' when building synced data key");
            Pair<ResourceLocation, ResourceLocation> pairKey = Pair.of(this.classKey.id(), this.id);
            return new SyncedDataKey<>(
                pairKey,
                this.id,
                this.classKey,
                this.serializer,
                this.defaultValueSupplier,
                this.save,
                this.persistent,
                this.syncMode);
        }

        public Builder<E, T> id(ResourceLocation id) {
            this.id = id;
            return this;
        }

        public Builder<E, T> id(String id) {
            this.id = new ResourceLocation(id);
            return this;
        }

        @Deprecated
        public Builder<E, T> key(String key) {
            return id(key);
        }

        public Builder<E, T> defaultValueSupplier(Supplier<T> defaultValueSupplier) {
            this.defaultValueSupplier = defaultValueSupplier;
            return this;
        }

        public Builder<E, T> saveToFile() {
            this.save = true;
            return this;
        }

        public Builder<E, T> resetOnDeath() {
            this.persistent = false;
            return this;
        }

        public Builder<E, T> syncMode(SyncMode mode) {
            this.syncMode = mode;
            return this;
        }
    }
}
