// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/network/message/ServerMessageSwapItem.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.network.message;

import net.minecraftforge.common.MinecraftForge;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.event.SwapItemWithOffHand;

/**
 * 空负载的「与副手交换物品」通知包。
 *
 * <p>
 * TODO(L2): 上游是 1.20 的 record，并用 {@code FriendlyByteBuf} + {@code NetworkEvent.Context}
 * 实现 SimpleChannel 的 encode/decode/handle（handle 里 post {@code SwapItemWithOffHand} 事件）。
 * 1.7.10 无副手、无 FriendlyByteBuf（用 {@code ByteBuf}/{@code PacketBuffer}）、无 NetworkEvent，
 * 且 SBM 的网络层已整体降级（见 {@code NetworkHandler}），因此本类只保留 record 语义的数据载体，
 * 三个静态方法签名保留但降级为「无缓冲操作 + 直接 post 事件」。
 */
public record ServerMessageSwapItem() {

    /** TODO(L2): 无 FriendlyByteBuf，空负载无需写；保留签名供上层调用。 */
    public static void encode(ServerMessageSwapItem message, Object buf) {}

    public static ServerMessageSwapItem decode(Object buf) {
        return new ServerMessageSwapItem();
    }

    /** TODO(L2): 1.7.10 无 NetworkEvent.Context；直接在调用线程 post 事件（等价于上游 enqueueWork）。 */
    public static void handle(ServerMessageSwapItem message, Object context) {
        MinecraftForge.EVENT_BUS.post(new SwapItemWithOffHand());
    }
}
