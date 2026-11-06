package com.tacz.guns.network.message;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.tacz.guns.GunMod;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：下发服务端时间戳，客户端据此校准 base timestamp，并回一条
 * {@link ClientMessageSyncBaseTimestamp} 完成三次握手。
 * 上游：{@code .../network/message/ServerMessageSyncBaseTimestamp.java}（无字段）
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>上游 {@code context.enqueueWork} → {@link NetworkTaskQueue#enqueue}</li>
 * <li>上游客户端分支直接 {@code Minecraft.getInstance().player} + {@code LocalPlayerDataHolder}
 * （client 包）→ 改走 {@link com.tacz.guns.network.ClientSideHooks.Impl#updateBaseTimestamp(long)}</li>
 * <li>上游用 {@code NetworkHandler.CHANNEL.reply(...)}（1.20 Forge 的上下文回复机制）；
 * 1.7.10 的 simpleimpl 没有 reply-from-context，改为在客户端显式
 * {@code sendToServer(new ClientMessageSyncBaseTimestamp())}</li>
 * </ul>
 */
public class ServerMessageSyncBaseTimestamp extends MessageBase {

    private static final Marker MARKER = MarkerManager.getMarker("SYNC_BASE_TIMESTAMP");

    public ServerMessageSyncBaseTimestamp() {}

    public static void handle(ServerMessageSyncBaseTimestamp message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        long timestamp = GunTime.nowMillis();
        GunMod.LOGGER.debug(MARKER, "Received server base timestamp: {}", timestamp);
        NetworkTaskQueue.enqueue(
            ctx.side,
            () -> ClientSideHooks.get()
                .updateBaseTimestamp(timestamp));
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageSyncBaseTimestamp());
    }
}
