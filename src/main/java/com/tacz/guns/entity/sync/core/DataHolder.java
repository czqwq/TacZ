package com.tacz.guns.entity.sync.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;

/**
 * 搬运自上游 1.20.1 {@code entity/sync/core/DataHolder.java}（MrCrayfish Framework，LGPL）。
 *
 * <p>
 * 1.7.10 适配：{@code Entity#level().isClientSide()} → {@code entity.worldObj.isRemote}。
 * {@code dataMap} 保持 public，供 {@code event/SyncedEntityDataEvent}（player clone 时整体搬运）使用。
 */
public class DataHolder {

    public Map<SyncedDataKey<?, ?>, DataEntry<?, ?>> dataMap = new HashMap<>();
    private boolean dirty = false;

    @SuppressWarnings("unchecked")
    public <E extends Entity, T> boolean set(E entity, SyncedDataKey<?, ?> key, T value) {
        DataEntry<E, T> entry = (DataEntry<E, T>) this.dataMap.computeIfAbsent(key, DataEntry::new);
        // 1.7.10 防御：上游 1.20 的 synced key 默认值都是非 null 对象（如 ItemStack.EMPTY），
        // 而本工程约定空值可以是 null；若将来新增「默认值 null」的 key，直接 equals 会 NPE。
        // 改 Objects.equals 后语义与上游一致（null==null 视为无变化）。
        if (!Objects.equals(entry.getValue(), value)) {
            boolean dirty = !entity.worldObj.isRemote && entry.getKey()
                .syncMode() != SyncedDataKey.SyncMode.NONE;
            entry.setValue(value, dirty);
            this.dirty = dirty;
            return true;
        }
        return false;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <E extends Entity, T> T get(SyncedDataKey<E, T> key) {
        return (T) this.dataMap.computeIfAbsent(key, DataEntry::new)
            .getValue();
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void clean() {
        this.dirty = false;
        this.dataMap.forEach((key, entry) -> entry.clean());
    }

    public List<DataEntry<?, ?>> gatherDirty() {
        return this.dataMap.values()
            .stream()
            .filter(DataEntry::isDirty)
            .filter(
                entry -> entry.getKey()
                    .syncMode() != SyncedDataKey.SyncMode.NONE)
            .collect(Collectors.toList());
    }

    public List<DataEntry<?, ?>> gatherAll() {
        return this.dataMap.values()
            .stream()
            .filter(
                entry -> entry.getKey()
                    .syncMode() != SyncedDataKey.SyncMode.NONE)
            .collect(Collectors.toList());
    }
}
