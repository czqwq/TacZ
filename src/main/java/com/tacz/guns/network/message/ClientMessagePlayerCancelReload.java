package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：取消换弹。
 * 上游：{@code .../network/message/ClientMessagePlayerCancelReload.java}（无字段）
 */
public class ClientMessagePlayerCancelReload extends MessageBase {

    public ClientMessagePlayerCancelReload() {}

    public static void handle(ClientMessagePlayerCancelReload message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP entity = ctx.getServerHandler().playerEntity;
            if (entity == null) {
                return;
            }
            IGunOperator.fromLivingEntity(entity)
                .cancelReload();
        });
    }
}
