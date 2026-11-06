package com.tacz.guns.entity.sync.core;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTBase;

import org.apache.commons.lang3.Validate;

import com.tacz.guns.mcshim.PacketBuffer;

/**
 * 搬运自上游 1.20.1 {@code entity/sync/core/DataEntry.java}（MrCrayfish Framework，LGPL）。
 *
 * <p>
 * 1.7.10 适配：{@code FriendlyByteBuf} → {@link PacketBuffer}，{@code Tag} → {@link NBTBase}。
 * 网络/持久化字段顺序与上游一致（两侧都是本模组）。
 */
public class DataEntry<E extends Entity, T> {

    private final SyncedDataKey<E, T> key;
    private T value;
    private boolean dirty;

    public DataEntry(SyncedDataKey<E, T> key) {
        this.key = key;
        this.value = key.defaultValueSupplier()
            .get();
    }

    public static DataEntry<?, ?> read(PacketBuffer buffer) {
        SyncedDataKey<?, ?> key = SyncedEntityData.instance()
            .getKey(buffer.readVarInt());
        Validate.notNull(key, "Synced key does not exist for id");
        DataEntry<?, ?> entry = new DataEntry<>(key);
        entry.readValue(buffer);
        return entry;
    }

    public SyncedDataKey<E, T> getKey() {
        return this.key;
    }

    public T getValue() {
        return this.value;
    }

    public void setValue(T value, boolean dirty) {
        this.value = value;
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void clean() {
        this.dirty = false;
    }

    public void write(PacketBuffer buffer) {
        int id = SyncedEntityData.instance()
            .getInternalId(this.key);
        buffer.writeVarInt(id);
        this.key.serializer()
            .write(buffer, this.value);
    }

    public void readValue(PacketBuffer buffer) {
        this.value = this.getKey()
            .serializer()
            .read(buffer);
    }

    public NBTBase writeValue() {
        return this.key.serializer()
            .write(this.value);
    }

    public void readValue(NBTBase nbt) {
        this.value = this.getKey()
            .serializer()
            .read(nbt);
    }
}
