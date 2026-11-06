// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/network/NetworkHandler.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * SBM 的网络层（1.20 用 {@code SimpleChannel} 注册 1 个 {@code S_SLICED_PACKET}，
 * 内容是「与副手交换物品」通知）。
 *
 * <p>
 * TODO(L2): 1.7.10 <b>没有副手</b>（1.9 才加入 offhand），因此这个包本身没有语义；
 * 同时 1.7.10 的网络是 {@code cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper}
 * （IMessage/IMessageHandler，没有 {@code registerMessage(int, Class, encoder, decoder, handler, Optional)} 这套 API）。
 * 本类因此降级为「保留上游方法签名、内部空实现」的占位：
 * <ul>
 * <li>{@link #init()} 不再注册任何包（上游由 {@code SimpleBedrockModel} 构造器调用，此处同样保留可调用性）；</li>
 * <li>四个 send 方法保留签名但什么都不做（如需真正联网，请按 1.7.10 的 SimpleNetworkWrapper 重建）；</li>
 * <li>{@code PacketId} 枚举原样保留（上游常量 100）。</li>
 * </ul>
 * TACZ 不依赖这些方法（仅 SBM 自身的切枪副手特效用），故不影响枪械玩法。
 */
public class NetworkHandler {

    private static final String VERSION = "0.1.0";

    public static void init() {
        // TODO(L2): 1.7.10 无副手、无 SimpleChannel，见类注释。
    }

    public static void sendToClientPlayer(Object message, EntityPlayer player) {}

    /**
     * 发送给所有监听此实体的玩家
     */
    public static void sendToTrackingEntityAndSelf(Entity centerEntity, Object message) {}

    public static void sendToAllPlayers(Object message) {}

    public static void sendToTrackingEntity(Object message, final Entity centerEntity) {}

    public static void sendToDimension(Object message, final Entity centerEntity) {}

    public enum PacketId {

        S_SLICED_PACKET(100),;

        private final int id;

        PacketId(int id) {
            this.id = id;
        }

        int value() {
            return id;
        }
    }
}
