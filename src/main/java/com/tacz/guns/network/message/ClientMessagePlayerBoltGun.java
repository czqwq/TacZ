package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：栓动/上膛动作完成。
 * 上游：{@code .../network/message/ClientMessagePlayerBoltGun.java}（无字段）
 */
public class ClientMessagePlayerBoltGun extends MessageBase {

    public ClientMessagePlayerBoltGun() {}

    public static void handle(ClientMessagePlayerBoltGun message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP entity = ctx.getServerHandler().playerEntity;
            if (entity == null) {
                return;
            }
            IGunOperator.fromLivingEntity(entity)
                .bolt();
        });
    }
}
