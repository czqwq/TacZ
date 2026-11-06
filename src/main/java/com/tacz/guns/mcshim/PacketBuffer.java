package com.tacz.guns.mcshim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

/**
 * 对应 1.20.1 的 {@code net.minecraft.network.FriendlyByteBuf}。
 *
 * <p>
 * 1.7.10 已有 {@code net.minecraft.network.PacketBuffer}（extends ByteBuf，提供 writeVarIntToBuffer /
 * readVarIntFromBuffer / writeItemStackToBuffer / writeNBTTagCompoundToBuffer / writeStringToBuffer / …），
 * 但<b>方法名与 1.20 完全不同</b>。本类继承它（自动获得全套 ByteBuf 委托），再补上 1.20 的方法名；
 * 构造时不复制底层 buffer，与 {@code ByteBufUtils} 的行为一致。
 *
 * <p>
 * 行为差异 / 降级点：{@code ResourceLocation} 写 {@code toString()}（与 1.20 完全相同）；{@code UUID} 写两个 long
 * （同）；{@code NBT} 用 {@code CompressedStreamTools.compress}（gzip + short 长度前缀）⇒ <b>线上格式与 1.20
 * 不同</b>，但两侧都是本模组、一致即可，null 用 {@code length = -1}；{@code ItemStack} 用
 * {@code short itemId + byte count + short damage + NBT}，null 用 {@code id = -1}；1.7.10 没有 {@code BlockPos}
 * ⇒ {@link #readBlockPos()} 返回 {@code int[3]}（线上格式与 1.20 的三个 big-endian int 相同）；
 * {@code writeEnum/readEnum} 写 VarInt ordinal、越界抛 {@link IllegalArgumentException}（1.20 无 null 分支）；
 * {@code readMap} 返回 {@link HashMap}（不保留顺序）；{@code writeUtf/readUtf} UTF-8 + VarInt 长度、默认上限
 * 32767 字符、超限抛 {@code DecoderException}；{@code writeCollection/readList/readOptional/readEither} 未移植
 * （上游与本工程均未用到）。
 *
 * <p>
 * <b>服务端安全</b>：只依赖 {@code net.minecraft.network/item/nbt/util} 与 netty，不使用任何 client /
 * {@code com.mojang.blaze3d} 类型，可在服务端逻辑里直接用。
 */
public class PacketBuffer extends net.minecraft.network.PacketBuffer {

    /** 默认 UTF 上限，与 1.20 {@code FriendlyByteBuf#writeUtf(String)} 一致。 */
    public static final int MAX_STRING_LENGTH = 32767;
    /** 单次 {@code readByteArray()} 的默认长度上限（1 MiB），避免坏包导致 OOM。 */
    public static final int DEFAULT_MAX_BYTE_ARRAY = 1024 * 1024;

    private final ByteBuf raw;

    /** 包一层，不复制；{@code buf} 不能为 null。 */
    public PacketBuffer(ByteBuf buf) {
        super(buf);
        this.raw = buf;
    }

    /**
     * 返回被包裹的原始 buffer（对应 1.20 {@code FriendlyByteBuf} 里「拿到底层 ByteBuf」的需求）。
     *
     * <p>
     * 注意这里覆盖了 1.7.10 {@code PacketBuffer#unwrap()}（那个方法穿透到更内层），
     * 语义改为「返回本类构造时传入的 buffer」，这才是调用方想要的。
     */
    @Override
    public ByteBuf unwrap() {
        return raw;
    }

    // ================================================================================================
    // VarInt / VarLong
    // ================================================================================================

    /** 对应 1.20 {@code FriendlyByteBuf#writeVarInt(int)}（LEB128，与 1.7.10 原生编码一致）。 */
    public PacketBuffer writeVarInt(int value) {
        writeVarIntToBuffer(value);
        return this;
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readVarInt()}。 */
    public int readVarInt() {
        return readVarIntFromBuffer();
    }

    /** 对应 1.20 {@code FriendlyByteBuf#writeVarLong(long)}。 */
    public PacketBuffer writeVarLong(long value) {
        while ((value & ~0x7FL) != 0L) {
            writeByte((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        writeByte((int) value);
        return this;
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readVarLong()}。 */
    public long readVarLong() {
        long value = 0L;
        int shift = 0;
        byte current;
        do {
            current = readByte();
            value |= (long) (current & 0x7F) << shift++;
            if (shift > 10) {
                throw new DecoderException("VarLong too big");
            }
        } while ((current & 0x80) == 0x80);
        return value;
    }

    // ================================================================================================
    // String (UTF)
    // ================================================================================================

    /** 对应 1.20 {@code FriendlyByteBuf#writeUtf(String)}。 */
    public PacketBuffer writeUtf(String value) {
        return writeUtf(value, MAX_STRING_LENGTH);
    }

    /** 对应 1.20 {@code FriendlyByteBuf#writeUtf(String, int maxLength)}。 */
    public PacketBuffer writeUtf(String value, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                "String too big (was " + value.length() + " chars, max " + maxLength + ")");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxLength * 3) {
            throw new IllegalArgumentException(
                "String too big (was " + bytes.length + " bytes encoded, max " + (maxLength * 3) + ")");
        }
        writeVarInt(bytes.length);
        writeBytes(bytes);
        return this;
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readUtf()}。 */
    public String readUtf() {
        return readUtf(MAX_STRING_LENGTH);
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readUtf(int maxLength)}。 */
    public String readUtf(int maxLength) {
        int length = readVarInt();
        if (length > maxLength * 3) {
            throw new DecoderException(
                "The received encoded string buffer length is longer than" + " maximum allowed ("
                    + length
                    + " > "
                    + (maxLength * 3)
                    + ")");
        }
        if (length < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        }
        byte[] bytes = new byte[length];
        readBytes(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > maxLength) {
            throw new DecoderException(
                "The received string length is longer than maximum allowed (" + value.length()
                    + " > "
                    + maxLength
                    + ")");
        }
        return value;
    }

    // ================================================================================================
    // ResourceLocation
    // ================================================================================================

    /** 对应 1.20 {@code FriendlyByteBuf#writeResourceLocation(ResourceLocation)}。 */
    public PacketBuffer writeResourceLocation(ResourceLocation value) {
        return writeUtf(value.toString());
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readResourceLocation()}。 */
    public ResourceLocation readResourceLocation() {
        return new ResourceLocation(readUtf(32767));
    }

    // ================================================================================================
    // UUID
    // ================================================================================================

    /** 对应 1.20 {@code FriendlyByteBuf#writeUUID(UUID)}。 */
    public PacketBuffer writeUUID(UUID value) {
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
        return this;
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readUUID()}。 */
    public UUID readUUID() {
        return new UUID(readLong(), readLong());
    }

    // ================================================================================================
    // NBT
    // ================================================================================================

    /**
     * 对应 1.20 {@code FriendlyByteBuf#writeNbt(CompoundTag)}：{@code null} 安全。
     *
     * <p>
     * 1.7.10 编码为 {@code short length + gzip bytes}（{@link CompressedStreamTools#compress}），
     * {@code null} 写 {@code length = -1}。
     */
    public PacketBuffer writeNbt(@Nullable NBTTagCompound tag) {
        try {
            writeNBTTagCompoundToBuffer(tag);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write NBT to packet buffer", e);
        }
        return this;
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readNbt()}：可能返回 {@code null}。 */
    @Nullable
    public NBTTagCompound readNbt() {
        try {
            return readNBTTagCompoundFromBuffer();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read NBT from packet buffer", e);
        }
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readNbt(NbtAccounter)}（1.7.10 无 accounter，忽略上限参数）。 */
    @Nullable
    public NBTTagCompound readNbt(long sizeLimit) {
        return readNbt();
    }

    /**
     * 按「未压缩的原始 NBT 流」读写（1.7.10 侧 {@code CompressedStreamTools.compress} 的逆操作不可省略，
     * 因此这里提供 {@code sizeLimit} 重载以便限长；实际实现仍走 {@code NBTSizeTracker}）。
     */
    @Nullable
    public NBTTagCompound readNbtWithLimit(long sizeLimit) {
        short length = readShort();
        if (length < 0) {
            return null;
        }
        byte[] bytes = new byte[length];
        readBytes(bytes);
        try {
            return CompressedStreamTools.func_152457_a(bytes, new NBTSizeTracker(sizeLimit));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read NBT from packet buffer", e);
        }
    }

    // ================================================================================================
    // ItemStack
    // ================================================================================================

    /** 对应 1.20 {@code FriendlyByteBuf#writeItemStack(ItemStack)}（{@code null} 安全）。 */
    public PacketBuffer writeItemStack(@Nullable ItemStack stack) {
        try {
            writeItemStackToBuffer(stack);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write ItemStack to packet buffer", e);
        }
        return this;
    }

    /** 对应 1.20 {@code FriendlyByteBuf#writeItem(ItemStack)}：与 {@link #writeItemStack} 等价。 */
    public PacketBuffer writeItem(@Nullable ItemStack stack) {
        return writeItemStack(stack);
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readItemStack()}：空栈返回 {@code null}。 */
    @Nullable
    public ItemStack readItemStack() {
        try {
            return readItemStackFromBuffer();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read ItemStack from packet buffer", e);
        }
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readItem()}：与 {@link #readItemStack()} 等价。 */
    @Nullable
    public ItemStack readItem() {
        return readItemStack();
    }

    // ================================================================================================
    // byte[]
    // ================================================================================================

    /** 对应 1.20 {@code FriendlyByteBuf#writeByteArray(byte[])}。 */
    public PacketBuffer writeByteArray(byte[] array) {
        writeVarInt(array.length);
        writeBytes(array);
        return this;
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readByteArray()}。 */
    public byte[] readByteArray() {
        return readByteArray(DEFAULT_MAX_BYTE_ARRAY);
    }

    /** 对应 1.20 {@code FriendlyByteBuf#readByteArray(int maxLength)}。 */
    public byte[] readByteArray(int maxLength) {
        int length = readVarInt();
        if (length > maxLength) {
            throw new DecoderException("ByteArray with size " + length + " is bigger than allowed " + maxLength);
        }
        if (length < 0) {
            throw new DecoderException("Negative ByteArray size " + length);
        }
        byte[] array = new byte[length];
        readBytes(array);
        return array;
    }

    // ================================================================================================
    // BlockPos（1.7.10 用 int[3]）
    // ================================================================================================

    /**
     * 对应 1.20 {@code FriendlyByteBuf#writeBlockPos(BlockPos)}。
     *
     * <p>
     * 1.7.10 无 {@code BlockPos}，用三个 int 代替；线上格式与 1.20 相同（三个 big-endian int）。
     */
    public PacketBuffer writeBlockPos(int x, int y, int z) {
        writeInt(x);
        writeInt(y);
        writeInt(z);
        return this;
    }

    /** {@link #writeBlockPos(int, int, int)} 的数组重载（读 {@code pos[0..2]}）。 */
    public PacketBuffer writeBlockPos(int[] pos) {
        return writeBlockPos(pos[0], pos[1], pos[2]);
    }

    /**
     * 对应 1.20 {@code FriendlyByteBuf#readBlockPos()}。
     *
     * @return 长度固定为 3 的数组：{@code [0]=x, [1]=y, [2]=z}
     */
    public int[] readBlockPos() {
        return new int[] { readInt(), readInt(), readInt() };
    }

    // ================================================================================================
    // Enum
    // ================================================================================================

    /** 对应 1.20 {@code FriendlyByteBuf#writeEnum(Enum)}：写 ordinal 的 VarInt。 */
    public <T extends Enum<T>> PacketBuffer writeEnum(T value) {
        writeVarInt(value.ordinal());
        return this;
    }

    /**
     * 对应 1.20 {@code FriendlyByteBuf#readEnum(Class)}。
     *
     * <p>
     * 1.20.1 的实现就是一句
     * {@code return enumClass.getEnumConstants()[this.readVarInt()];} —— 非法 ordinal
     * （负数或 ≥ 常量个数）会从数组下标直接抛 {@link ArrayIndexOutOfBoundsException}，
     * <b>没有 null 分支</b>。本类保持同样的「解码期即失败」语义，只是把越界改成带可读信息的
     * {@link IllegalArgumentException}（同为 RuntimeException，栈里能直接看到类名与 ordinal，
     * 便于定位是哪个包/哪个枚举坏了）。
     *
     * <p>
     * <b>不要改回返回 null</b>：null 会存进各网络消息的枚举字段，随后在服务端 tick 的
     * {@code switch} / {@code EnumMap.put} / {@code ForgeDirection} 使用点才 NPE，
     * 比在解码阶段失败更晚也更难定位。解码异常在 1.7.10 simpleimpl 里走 NetworkManager 的
     * 异常路径（记录并断开该连接），不会把服务端 tick 带崩 —— 这正是 1.20 的行为。
     */
    public <T extends Enum<T>> T readEnum(Class<T> clazz) {
        int ordinal = readVarInt();
        T[] values = clazz.getEnumConstants();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException(
                "Invalid enum ordinal " + ordinal + " for " + clazz.getName() + " (0.." + (values.length - 1) + ")");
        }
        return values[ordinal];
    }

    // ================================================================================================
    // Map
    // ================================================================================================

    /** 对应 1.20 {@code FriendlyByteBuf#writeMap(Map, writer, writer)}。 */
    public <K, V> PacketBuffer writeMap(Map<K, V> map, BiConsumer<PacketBuffer, K> keyWriter,
        BiConsumer<PacketBuffer, V> valueWriter) {
        writeVarInt(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            keyWriter.accept(this, entry.getKey());
            valueWriter.accept(this, entry.getValue());
        }
        return this;
    }

    /**
     * 对应 1.20 {@code FriendlyByteBuf#readMap(reader, reader)}。
     *
     * <p>
     * <b>差异</b>：返回 {@link HashMap}，不保留写入顺序。
     */
    public <K, V> Map<K, V> readMap(Function<PacketBuffer, K> keyReader, Function<PacketBuffer, V> valueReader) {
        int size = readVarInt();
        Map<K, V> map = new HashMap<>(Math.max(4, size));
        for (int i = 0; i < size; i++) {
            K key = keyReader.apply(this);
            V value = valueReader.apply(this);
            map.put(key, value);
        }
        return map;
    }

    // ================================================================================================
    // 1.7.10 特有条目状态同步辅助（上游 1.20 用 SynchedEntityData，移植后用本方法成对读写）
    // ================================================================================================

    /** 读一个「可变长」的 longs 数组（上游 {@code ServerMessageAmmoStorageContents} 这类包用）。 */
    public long[] readLongArray() {
        int length = readVarInt();
        long[] array = new long[length];
        for (int i = 0; i < length; i++) {
            array[i] = readVarLong();
        }
        return array;
    }

    /** 写一个「可变长」的 longs 数组。 */
    public PacketBuffer writeLongArray(long[] array) {
        writeVarInt(array.length);
        for (long value : array) {
            writeVarLong(value);
        }
        return this;
    }
}
