package com.tacz.guns.network.message.handshake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.tacz.guns.GunMod;
import com.tacz.guns.entity.sync.core.SyncedDataKey;
import com.tacz.guns.entity.sync.core.SyncedEntityData;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：下发 {@code SyncedEntityData} 的 key ↔ 内部 id 映射表。
 * 上游：{@code .../network/message/handshake/ServerMessageSyncedEntityDataMapping.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>不再是 login packet：见 {@link Acknowledge} 的说明（1.7.10 无 login-packet 机制），
 * 改为玩家登录后由服务端主动发送的 PLAY 包</li>
 * <li>上游 {@code handle} 里用 {@code CountDownLatch} 阻塞 Netty 线程、等主线程处理完再 {@code reply}；
 * 1.7.10 用 {@link NetworkTaskQueue}（不阻塞网络线程），处理完再 {@code sendToServer(new Acknowledge())}</li>
 * <li>{@code Pair.of} → {@code Pair.of}（commons-lang3，与 api 层统一）</li>
 * <li>{@code Component.literal} → {@code ChatComponentText}（仅在需要断线时使用；1.7.10 的
 * {@code NetHandlerPlayServer#kickPlayerFromServer(String)}）</li>
 * </ul>
 * <p>
 * 依赖契约（common-game）：{@code entity/sync/core/SyncedDataKey} 保留
 * {@code classKey().id()} / {@code id()}；{@code SyncedEntityData.instance()} 保留
 * {@code getKeys()} / {@code getInternalId(key)} / {@code updateMappings(message)}。
 */
public class ServerMessageSyncedEntityDataMapping extends MessageBase {

    public static final Marker HANDSHAKE = MarkerManager.getMarker("TACZ_HANDSHAKE");
    private Map<ResourceLocation, List<Pair<ResourceLocation, Integer>>> keyMap;

    public ServerMessageSyncedEntityDataMapping() {
        this.keyMap = Collections.emptyMap();
    }

    private ServerMessageSyncedEntityDataMapping(Map<ResourceLocation, List<Pair<ResourceLocation, Integer>>> keyMap) {
        this.keyMap = keyMap;
    }

    /**
     * 上游把「写映射表」放在 {@code encode(message, buffer)} 里（忽略传入的 message，直接读服务端注册表）。
     * 1.7.10 侧改为构造时取快照 + {@link #write} 写快照，语义等价且更适合 1.7.10 的实例式序列化。
     */
    public static ServerMessageSyncedEntityDataMapping snapshot() {
        return new ServerMessageSyncedEntityDataMapping(null);
    }

    @Override
    protected void write(PacketBuffer buf) {
        Set<SyncedDataKey<?, ?>> keys = SyncedEntityData.instance()
            .getKeys();
        buf.writeInt(keys.size());
        for (SyncedDataKey<?, ?> key : keys) {
            int id = SyncedEntityData.instance()
                .getInternalId(key);
            buf.writeResourceLocation(
                key.classKey()
                    .id());
            buf.writeResourceLocation(key.id());
            buf.writeVarInt(id);
        }
    }

    @Override
    protected void read(PacketBuffer buf) {
        int size = buf.readInt();
        Map<ResourceLocation, List<Pair<ResourceLocation, Integer>>> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation classId = buf.readResourceLocation();
            ResourceLocation keyId = buf.readResourceLocation();
            int id = buf.readVarInt();
            map.computeIfAbsent(classId, c -> new ArrayList<>())
                .add(Pair.of(keyId, id));
        }
        this.keyMap = map;
    }

    public Map<ResourceLocation, List<Pair<ResourceLocation, Integer>>> getKeyMap() {
        return this.keyMap;
    }

    public static void handle(ServerMessageSyncedEntityDataMapping message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        GunMod.LOGGER.debug(HANDSHAKE, "Received synced key mappings from server");
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            if (!SyncedEntityData.instance()
                .updateMappings(message)) {
                GunMod.LOGGER.error(HANDSHAKE, "Received unknown synced data keys from server");
            }
            NetworkHandler.HANDSHAKE_CHANNEL.sendToServer(new Acknowledge());
        });
    }
}
