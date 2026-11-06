package com.tacz.guns.entity.sync.core;

import net.minecraft.nbt.NBTBase;

import com.tacz.guns.mcshim.PacketBuffer;

/**
 * 搬运自上游 1.20.1 {@code entity/sync/core/IDataSerializer.java}（MrCrayfish Framework，LGPL）。
 *
 * <p>
 * 1.7.10 适配：{@code FriendlyByteBuf} → {@link PacketBuffer}（mcshim），
 * {@code net.minecraft.nbt.Tag} → {@link NBTBase}（1.7.10 无 Tag 接口，用 NBTBase 层次）。
 */
public interface IDataSerializer<T> {

    void write(PacketBuffer buf, T value);

    T read(PacketBuffer buf);

    NBTBase write(T value);

    T read(NBTBase nbt);
}
