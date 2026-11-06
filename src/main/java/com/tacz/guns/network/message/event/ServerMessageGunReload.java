package com.tacz.guns.network.message.event;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.common.GunReloadEvent;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：在客户端投递 {@link GunReloadEvent}。
 * 上游：{@code .../network/message/event/ServerMessageGunReload.java}
 */
public class ServerMessageGunReload extends AbstractShooterGunEventMessage {

    public ServerMessageGunReload(int shooterId, ItemStack gunItemStack) {
        super(shooterId, gunItemStack);
    }

    public ServerMessageGunReload() {}

    @Override
    protected void postEvent(EntityLivingBase shooter) {
        MinecraftForge.EVENT_BUS.post(new GunReloadEvent(shooter, gunItemStack, Side.CLIENT));
    }

    public static void handle(ServerMessageGunReload message, MessageContext ctx) {
        handleOnClient(message::doClientEvent, ctx);
    }
}
