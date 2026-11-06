package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：请求近战（枪托砸击）。
 * 上游：{@code .../network/message/ClientMessagePlayerMelee.java}（无字段）
 */
public class ClientMessagePlayerMelee extends MessageBase {

    public static void handle(ClientMessagePlayerMelee message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP entity = ctx.getServerHandler().playerEntity;
            if (entity == null) {
                return;
            }
            IGunOperator.fromLivingEntity(entity)
                .melee();
        });
    }
}
