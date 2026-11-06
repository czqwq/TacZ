package com.tacz.guns.network.message.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：在客户端投递 {@link GunDrawEvent}（掏枪前后物品）。
 * 上游：{@code .../network/message/event/ServerMessageGunDraw.java}
 */
public class ServerMessageGunDraw extends MessageBase {

    private int entityId;
    private ItemStack previousGunItem;
    private ItemStack currentGunItem;

    public ServerMessageGunDraw(int entityId, ItemStack previousGunItem, ItemStack currentGunItem) {
        this.entityId = entityId;
        this.previousGunItem = previousGunItem;
        this.currentGunItem = currentGunItem;
    }

    public ServerMessageGunDraw() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeVarInt(entityId);
        buf.writeItem(previousGunItem);
        buf.writeItem(currentGunItem);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.entityId = buf.readVarInt();
        this.previousGunItem = buf.readItem();
        this.currentGunItem = buf.readItem();
    }

    private void doClientEvent() {
        Entity entity = ClientSideHooks.get()
            .getEntityById(entityId);
        if (entity instanceof EntityLivingBase) {
            MinecraftForge.EVENT_BUS
                .post(new GunDrawEvent((EntityLivingBase) entity, previousGunItem, currentGunItem, Side.CLIENT));
        }
    }

    public static void handle(ServerMessageGunDraw message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, message::doClientEvent);
    }
}
