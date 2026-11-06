package com.tacz.guns.network.message.handshake;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.tacz.guns.GunMod;
import com.tacz.guns.network.MessageBase;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 握手确认包。
 * 上游：{@code .../network/message/handshake/Acknowledge.java}（继承 {@code LoginIndexHolder}，走 1.20 的
 * login-packet 握手 + {@code HandshakeHandler.indexFirst}）。
 *
 * <p>
 * 1.7.10 差异（重要）：1.7.10 的 {@code SimpleNetworkWrapper} <b>没有</b> login-packet / handshake 机制，
 * 因此 {@code LoginIndexHolder} 那套「login 阶段索引 + 一定顺序」的语义无法保留。改为：
 * 服务端在玩家登录时（{@code FMLCommonSetupEvent} 之后的 {@code PlayerLoggedInEvent}）按普通 PLAY 包发送
 * {@link ServerMessageSyncedEntityDataMapping}，客户端收到后回本包作为 ACK。语义等价于「服务端先把映射表推给客户端，
 * 客户端确认」，但不再保证发生在 login 阶段（可能在进入世界后的头几 tick）。
 */
public class Acknowledge extends MessageBase {

    public static final Marker ACKNOWLEDGE = MarkerManager.getMarker("HANDSHAKE_ACKNOWLEDGE");

    public Acknowledge() {}

    public static void handle(Acknowledge message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        GunMod.LOGGER.debug(ACKNOWLEDGE, "Received acknowledgement from client");
    }
}
