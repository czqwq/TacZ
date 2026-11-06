package com.tacz.guns.entity.sync.core;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.mcshim.PacketBuffer;

/**
 * 搬运自上游 1.20.1 {@code entity/sync/core/Serializers.java}（MrCrayfish Framework，LGPL）。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>网络：{@code FriendlyByteBuf} → {@link PacketBuffer}（mcshim）；</li>
 * <li>NBT：{@code Tag/ByteTag/...} → {@link NBTBase}/{@code NBTTagXxx}（1.7.10 无 Tag 接口）；
 * 原始类型读取走 {@code NBTBase.NBTPrimitive} 的 {@code func_150290_f/func_150289_e/
 *       func_150287_d/func_150291_c/func_150286_g}，字符串走 {@code NBTTagString#func_150285_a_}；</li>
 * <li><b>BLOCK_POS 序列化器删除</b>：1.7.10 没有 {@code BlockPos}（PORT_PLAN 约定 BlockPos→x,y,z 三个 int），
 * 本模组内无调用点，故删除并记录差异。</li>
 * </ul>
 */
public class Serializers {

    public static final IDataSerializer<Boolean> BOOLEAN = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, Boolean value) {
            buf.writeBoolean(value);
        }

        @Override
        public Boolean read(PacketBuffer buf) {
            return buf.readBoolean();
        }

        @Override
        public NBTBase write(Boolean value) {
            return new NBTTagByte((byte) (value ? 1 : 0));
        }

        @Override
        public Boolean read(NBTBase tag) {
            return ((NBTBase.NBTPrimitive) tag).func_150290_f() != 0;
        }
    };

    public static final IDataSerializer<Byte> BYTE = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, Byte value) {
            buf.writeByte(value);
        }

        @Override
        public Byte read(PacketBuffer buf) {
            return buf.readByte();
        }

        @Override
        public NBTBase write(Byte value) {
            return new NBTTagByte(value);
        }

        @Override
        public Byte read(NBTBase tag) {
            return ((NBTBase.NBTPrimitive) tag).func_150290_f();
        }
    };

    public static final IDataSerializer<Short> SHORT = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, Short value) {
            buf.writeShort(value);
        }

        @Override
        public Short read(PacketBuffer buf) {
            return buf.readShort();
        }

        @Override
        public NBTBase write(Short value) {
            return new NBTTagShort(value);
        }

        @Override
        public Short read(NBTBase tag) {
            return ((NBTBase.NBTPrimitive) tag).func_150289_e();
        }
    };

    public static final IDataSerializer<Integer> INTEGER = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, Integer value) {
            buf.writeVarInt(value);
        }

        @Override
        public Integer read(PacketBuffer buf) {
            return buf.readVarInt();
        }

        @Override
        public NBTBase write(Integer value) {
            return new NBTTagInt(value);
        }

        @Override
        public Integer read(NBTBase tag) {
            return ((NBTBase.NBTPrimitive) tag).func_150287_d();
        }
    };

    public static final IDataSerializer<Long> LONG = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, Long value) {
            buf.writeLong(value);
        }

        @Override
        public Long read(PacketBuffer buf) {
            return buf.readLong();
        }

        @Override
        public NBTBase write(Long value) {
            return new NBTTagLong(value);
        }

        @Override
        public Long read(NBTBase tag) {
            return ((NBTBase.NBTPrimitive) tag).func_150291_c();
        }
    };

    public static final IDataSerializer<Float> FLOAT = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, Float value) {
            buf.writeFloat(value);
        }

        @Override
        public Float read(PacketBuffer buf) {
            return buf.readFloat();
        }

        @Override
        public NBTBase write(Float value) {
            return new NBTTagFloat(value);
        }

        @Override
        public Float read(NBTBase tag) {
            // 1.7.10 NBTPrimitive 只暴露统一的 double getter，再转 float（与上游值等价）
            return (float) ((NBTBase.NBTPrimitive) tag).func_150286_g();
        }
    };

    public static final IDataSerializer<Double> DOUBLE = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, Double value) {
            buf.writeDouble(value);
        }

        @Override
        public Double read(PacketBuffer buf) {
            return buf.readDouble();
        }

        @Override
        public NBTBase write(Double value) {
            return new NBTTagDouble(value);
        }

        @Override
        public Double read(NBTBase tag) {
            return ((NBTBase.NBTPrimitive) tag).func_150286_g();
        }
    };

    public static final IDataSerializer<Character> CHARACTER = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, Character value) {
            buf.writeChar(value);
        }

        @Override
        public Character read(PacketBuffer buf) {
            return buf.readChar();
        }

        @Override
        public NBTBase write(Character value) {
            return new NBTTagInt(value);
        }

        @Override
        public Character read(NBTBase tag) {
            return (char) ((NBTBase.NBTPrimitive) tag).func_150287_d();
        }
    };

    public static final IDataSerializer<String> STRING = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, String value) {
            buf.writeUtf(value);
        }

        @Override
        public String read(PacketBuffer buf) {
            return buf.readUtf();
        }

        @Override
        public NBTBase write(String value) {
            return new NBTTagString(value);
        }

        @Override
        public String read(NBTBase tag) {
            return ((NBTTagString) tag).func_150285_a_();
        }
    };

    public static final IDataSerializer<NBTTagCompound> TAG_COMPOUND = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, NBTTagCompound value) {
            buf.writeNbt(value);
        }

        @Override
        public NBTTagCompound read(PacketBuffer buf) {
            return buf.readNbt();
        }

        @Override
        public NBTBase write(NBTTagCompound value) {
            return value;
        }

        @Override
        public NBTTagCompound read(NBTBase tag) {
            return (NBTTagCompound) tag;
        }
    };

    public static final IDataSerializer<UUID> UUID = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, UUID value) {
            buf.writeUUID(value);
        }

        @Override
        public UUID read(PacketBuffer buf) {
            return buf.readUUID();
        }

        @Override
        public NBTBase write(UUID value) {
            NBTTagCompound compound = new NBTTagCompound();
            compound.setLong("Most", value.getMostSignificantBits());
            compound.setLong("Least", value.getLeastSignificantBits());
            return compound;
        }

        @Override
        public UUID read(NBTBase tag) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            return new UUID(compound.getLong("Most"), compound.getLong("Least"));
        }
    };

    public static final IDataSerializer<ItemStack> ITEM_STACK = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, ItemStack value) {
            buf.writeItem(value);
        }

        @Override
        public ItemStack read(PacketBuffer buf) {
            return buf.readItem();
        }

        @Override
        public NBTBase write(ItemStack value) {
            // 1.7.10：null 表示空栈（1.20 的 ItemStack.EMPTY 不存在）
            NBTTagCompound compound = new NBTTagCompound();
            if (value != null && value.getItem() != null) {
                value.writeToNBT(compound);
            }
            return compound;
        }

        @Override
        public ItemStack read(NBTBase tag) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            if (!compound.hasKey("id")) {
                return null;
            }
            return ItemStack.loadItemStackFromNBT(compound);
        }
    };

    public static final IDataSerializer<ResourceLocation> RESOURCE_LOCATION = new IDataSerializer<>() {

        @Override
        public void write(PacketBuffer buf, ResourceLocation value) {
            buf.writeResourceLocation(value);
        }

        @Override
        public ResourceLocation read(PacketBuffer buf) {
            return buf.readResourceLocation();
        }

        @Override
        public NBTBase write(ResourceLocation value) {
            return new NBTTagString(value.toString());
        }

        @Override
        public ResourceLocation read(NBTBase tag) {
            return new ResourceLocation(((NBTTagString) tag).func_150285_a_());
        }
    };
}
