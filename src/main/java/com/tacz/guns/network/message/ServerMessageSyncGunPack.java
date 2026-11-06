package com.tacz.guns.network.message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.network.GunPackSync;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

/**
 * 服务端 → 客户端：把枪包数据（json 缓存）压缩分片下发。
 * 上游：{@code .../network/message/ServerMessageSyncGunPack.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code FriendlyByteBuf} → {@link PacketBuffer}；{@code buf.release()} 由 1.7.10 侧显式
 * {@code raw.release()} 完成（simpleimpl 不负责 buffer 生命周期）</li>
 * <li>{@code GunPackSync.Part} 原来是 record，1.7.10 侧由 common-resource 改成普通类；
 * 这里改成只用「构造传输需要的 5 个值 + getter」的写法：
 * {@code GunPackSync.Part(UUID, int index, int compressedSize, int uncompressedSize, byte[] data)}</li>
 * <li>客户端落地：{@code ClientIndexManager.reload()} 是 client 包 → 走
 * {@link com.tacz.guns.network.ClientSideHooks.Impl#syncGunPack}；{@code CommonAssetsManager}/{@code CommonNetworkCache}
 * 是 common 包，
 * 但为了让客户端能「收齐再重建索引」，整段放到 hook 实现里</li>
 * <li>上游用 {@code Connection} 做「是否同一连接」与断线；1.7.10 的 simpleimpl 消息里拿不到
 * {@code NetworkManager}，因此退化为「不校验连接身份」，只保留统一的异常处理</li>
 * <li>{@code stream().toList()} → {@code collect(Collectors.toList())}（Java 8）</li>
 * </ul>
 * <p>
 * 依赖契约（common-resource）：{@code resource/network/GunPackSync} 需保留
 * {@code Part}、{@code Receiver}（{@code accept(Part)} 返回 {@code byte[]} 或 null、{@code clear()}）、
 * {@code compressAndSplit(byte[])}、常量 {@code CHUNK_SIZE} / {@code MAX_SYNC_SIZE}；
 * {@code resource/network/DataType} 为枚举；{@code CommonNetworkCache.INSTANCE.fromNetwork(map)};
 * {@code CommonAssetsManager.clearInstance()}；{@code resource/network/CommonNetworkCache}。
 */
public class ServerMessageSyncGunPack extends MessageBase {

    private static final Marker MARKER = MarkerManager.getMarker("GUN_PACK_SYNC");
    private static final GunPackSync.Receiver RECEIVER = new GunPackSync.Receiver();

    private UUID transferId;
    private int index;
    private int compressedSize;
    private int uncompressedSize;
    private byte[] data;

    public ServerMessageSyncGunPack(GunPackSync.Part part) {
        this.transferId = part.transferId();
        this.index = part.index();
        this.compressedSize = part.compressedSize();
        this.uncompressedSize = part.uncompressedSize();
        this.data = part.data();
    }

    public ServerMessageSyncGunPack() {}

    /** 资源重载后仅调用一次，所有玩家复用同一组压缩分片。 */
    public static List<ServerMessageSyncGunPack> createMessages(Map<DataType, Map<ResourceLocation, String>> cache) {
        ByteBuf raw = Unpooled.buffer(256, GunPackSync.MAX_SYNC_SIZE);
        try {
            PacketBuffer buf = new PacketBuffer(raw);
            buf.writeMap(
                cache,
                PacketBuffer::writeEnum,
                (buffer, map) -> buffer.writeMap(
                    map,
                    PacketBuffer::writeResourceLocation,
                    (buffer1, json) -> buffer1.writeUtf(json, GunPackSync.MAX_SYNC_SIZE)));
            byte[] uncompressed = new byte[buf.readableBytes()];
            raw.readBytes(uncompressed);
            List<GunPackSync.Part> parts = GunPackSync.compressAndSplit(uncompressed);
            GunMod.LOGGER.info(
                MARKER,
                "Prepared gun pack sync: {} bytes -> {} compressed bytes, {} part(s)",
                uncompressed.length,
                parts.get(0)
                    .compressedSize(),
                parts.size());
            List<ServerMessageSyncGunPack> messages = new ArrayList<>(parts.size());
            for (GunPackSync.Part part : parts) {
                messages.add(new ServerMessageSyncGunPack(part));
            }
            return messages;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare gun pack sync", e);
        } finally {
            raw.release();
        }
    }

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeUUID(transferId);
        buf.writeVarInt(index);
        buf.writeVarInt(compressedSize);
        buf.writeVarInt(uncompressedSize);
        buf.writeByteArray(data);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.transferId = buf.readUUID();
        this.index = buf.readVarInt();
        this.compressedSize = buf.readVarInt();
        this.uncompressedSize = buf.readVarInt();
        this.data = buf.readByteArray(GunPackSync.CHUNK_SIZE);
    }

    /**
     * 上游 {@code clearPendingSync()}：资源重载时清空分片接收器。
     */
    public static void clearPendingSync() {
        RECEIVER.clear();
    }

    public GunPackSync.Part toPart() {
        return new GunPackSync.Part(transferId, index, compressedSize, uncompressedSize, data);
    }

    /**
     * 客户端接收分片；由 {@code ClientProxy} 注入的 hook 调用（读到分片 → 收齐 → 重建索引）。
     */
    public static byte[] acceptPart(GunPackSync.Part part) throws IOException {
        return RECEIVER.accept(part);
    }

    /**
     * 解析压缩后的缓存内容（对应上游 {@code readCache(byte[])}）。公开给客户端 hook 使用。
     */
    public static Map<DataType, Map<ResourceLocation, String>> readCache(byte[] uncompressed) {
        ByteBuf raw = Unpooled.wrappedBuffer(uncompressed);
        try {
            PacketBuffer buf = new PacketBuffer(raw);
            int types = buf.readVarInt();
            if (types < 0 || types > DataType.values().length) {
                throw new DecoderException("Invalid gun pack sync data type count: " + types);
            }
            Map<DataType, Map<ResourceLocation, String>> cache = new EnumMap<>(DataType.class);
            for (int i = 0; i < types; i++) {
                DataType type = buf.readEnum(DataType.class);
                int entries = buf.readVarInt();
                if (entries < 0 || entries > buf.readableBytes() / 2) {
                    throw new DecoderException("Invalid gun pack sync entry count: " + entries);
                }
                Map<ResourceLocation, String> data = new HashMap<>();
                for (int j = 0; j < entries; j++) {
                    ResourceLocation id = buf.readResourceLocation();
                    if (data.put(id, buf.readUtf(GunPackSync.MAX_SYNC_SIZE)) != null) {
                        throw new DecoderException("Duplicate gun pack sync resource: " + id);
                    }
                }
                if (cache.put(type, data) != null) {
                    throw new DecoderException("Duplicate gun pack sync data type: " + type);
                }
            }
            if (buf.readableBytes() > 0) {
                throw new DecoderException("Trailing gun pack sync data");
            }
            return cache;
        } finally {
            raw.release();
        }
    }

    public static void handle(ServerMessageSyncGunPack message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(
            ctx.side,
            () -> ClientSideHooks.get()
                .syncGunPack(message));
    }
}
