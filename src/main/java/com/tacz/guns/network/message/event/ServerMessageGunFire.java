package com.tacz.guns.network.message.event;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.common.GunFireEvent;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：在客户端投递 {@link GunFireEvent}。
 * 上游：{@code .../network/message/event/ServerMessageGunFire.java}
 */
public class ServerMessageGunFire extends AbstractShooterGunEventMessage {

    public ServerMessageGunFire(int shooterId, ItemStack gunItemStack) {
        super(shooterId, gunItemStack);
    }

    public ServerMessageGunFire() {}

    @Override
    protected void postEvent(EntityLivingBase shooter) {
        MinecraftForge.EVENT_BUS.post(new GunFireEvent(shooter, gunItemStack, Side.CLIENT));
    }

    public static void handle(ServerMessageGunFire message, MessageContext ctx) {
        handleOnClient(message::doClientEvent, ctx);
    }
}
