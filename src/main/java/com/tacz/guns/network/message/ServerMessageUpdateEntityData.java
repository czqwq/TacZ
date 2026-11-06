package com.tacz.guns.network.message;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;

import com.tacz.guns.entity.sync.core.DataEntry;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.handler.codec.DecoderException;

/**
 * 服务端 → 客户端：同步实体被同步字段（1.20 是 {@code SynchedEntityData} 风格，1.7.10 用自研管理器）。
 * 上游：{@code .../network/message/ServerMessageUpdateEntityData.java}
 *
 * <p>
 * 1.7.10 差异：{@code FriendlyByteBuf} → {@link PacketBuffer}（{@code DataEntry#write/read} 由 common-game
 * 的 {@code entity/sync/core} 按同样方式改造）；{@code Minecraft.getInstance().level.getEntity(id)} →
 * {@link com.tacz.guns.network.ClientSideHooks.Impl#getEntityById(int)}。
 */
public class ServerMessageUpdateEntityData extends MessageBase {

    private int entityId;
    private List<DataEntry<?, ?>> entries;

    public ServerMessageUpdateEntityData(int entityId, List<DataEntry<?, ?>> entries) {
        this.entityId = entityId;
        this.entries = entries;
    }

    public ServerMessageUpdateEntityData() {
        this.entries = new ArrayList<>();
    }

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(entries.size());
        for (DataEntry<?, ?> entry : entries) {
            entry.write(buf);
        }
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.entityId = buf.readVarInt();
        int size = buf.readVarInt();
        // 1.7.10 差异（防越界/防 OOM）：1.20 的 FriendlyByteBuf 由 Netty 保证包长，decode 里不校验数量；
        // 1.7.10 的 simpleimpl 同样不校验，但这里直接 new ArrayList<>(size) 会按报文里的任意数字分配内存。
        // 每个 DataEntry 至少占 2 字节（varint key + 1 字节值），因此用剩余可读字节数做上界判非法。
        if (size < 0 || size > buf.readableBytes()) {
            throw new DecoderException("Invalid synced entity data entry count: " + size);
        }
        List<DataEntry<?, ?>> read = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            read.add(DataEntry.read(buf));
        }
        this.entries = read;
    }

    private void onHandle() {
        Entity entity = ClientSideHooks.get()
            .getEntityById(entityId);
        if (entity == null) {
            return;
        }
        SyncedEntityData instance = SyncedEntityData.instance();
        for (DataEntry<?, ?> entry : entries) {
            instance.set(entity, entry.getKey(), entry.getValue());
        }
    }

    public static void handle(ServerMessageUpdateEntityData message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, message::onHandle);
    }
}
