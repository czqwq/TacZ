package com.tacz.guns.network.message;

import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：主手/副手物品交换事件（通知客户端重建枪械状态）。
 * 上游：{@code .../network/message/ServerMessageSwapItem.java}（无字段）
 *
 * <p>
 * 本包不需要 {@link com.tacz.guns.network.ClientSideHooks}：{@link SwapItemWithOffHand} 属于
 * {@code api/client/event}（common 侧类），事件只在客户端分支投递，语义与上游一致。
 */
public class ServerMessageSwapItem extends MessageBase {

    public ServerMessageSwapItem() {}

    public static void handle(ServerMessageSwapItem message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> MinecraftForge.EVENT_BUS.post(new SwapItemWithOffHand()));
    }
}
