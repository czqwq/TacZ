package com.tacz.guns.network.message.event;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.common.GunFireSelectEvent;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：在客户端投递 {@link GunFireSelectEvent}。
 * 上游：{@code .../network/message/event/ServerMessageGunFireSelect.java}
 */
public class ServerMessageGunFireSelect extends AbstractShooterGunEventMessage {

    public ServerMessageGunFireSelect(int shooterId, ItemStack gunItemStack) {
        super(shooterId, gunItemStack);
    }

    public ServerMessageGunFireSelect() {}

    @Override
    protected void postEvent(EntityLivingBase shooter) {
        MinecraftForge.EVENT_BUS.post(new GunFireSelectEvent(shooter, gunItemStack, Side.CLIENT));
    }

    public static void handle(ServerMessageGunFireSelect message, MessageContext ctx) {
        handleOnClient(message::doClientEvent, ctx);
    }
}
