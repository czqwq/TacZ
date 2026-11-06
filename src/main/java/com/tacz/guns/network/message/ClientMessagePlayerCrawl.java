package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：趴下状态变化。
 * 上游：{@code .../network/message/ClientMessagePlayerCrawl.java}（字段 {@code isCrawl}）
 */
public class ClientMessagePlayerCrawl extends MessageBase {

    private boolean isCrawl;

    public ClientMessagePlayerCrawl(boolean isCrawl) {
        this.isCrawl = isCrawl;
    }

    public ClientMessagePlayerCrawl() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeBoolean(isCrawl);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.isCrawl = buf.readBoolean();
    }

    public static void handle(ClientMessagePlayerCrawl message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP entity = ctx.getServerHandler().playerEntity;
            if (entity == null) {
                return;
            }
            // 进入条件由服务端趴下逻辑统一验证；禁用功能后仍允许退出。
            IGunOperator.fromLivingEntity(entity)
                .crawl(message.isCrawl);
        });
    }
}
