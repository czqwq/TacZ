package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：瞄准状态变化。
 * 上游：{@code .../network/message/ClientMessagePlayerAim.java}（字段 {@code isAim}）
 */
public class ClientMessagePlayerAim extends MessageBase {

    private boolean isAim;

    public ClientMessagePlayerAim(boolean isAim) {
        this.isAim = isAim;
    }

    public ClientMessagePlayerAim() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeBoolean(isAim);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.isAim = buf.readBoolean();
    }

    public static void handle(ClientMessagePlayerAim message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP entity = ctx.getServerHandler().playerEntity;
            if (entity == null) {
                return;
            }
            IGunOperator.fromLivingEntity(entity)
                .aim(message.isAim);
        });
    }
}
