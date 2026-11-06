package com.tacz.guns.network.message.event;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.common.GunFirePostEvent;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：在客户端投递 {@link GunFirePostEvent}。
 * 上游：{@code .../network/message/event/ServerMessageGunFirePost.java}
 */
public class ServerMessageGunFirePost extends AbstractShooterGunEventMessage {

    public ServerMessageGunFirePost(int shooterId, ItemStack gunItemStack) {
        super(shooterId, gunItemStack);
    }

    public ServerMessageGunFirePost() {}

    @Override
    protected void postEvent(EntityLivingBase shooter) {
        MinecraftForge.EVENT_BUS.post(new GunFirePostEvent(shooter, gunItemStack, Side.CLIENT));
    }

    public static void handle(ServerMessageGunFirePost message, MessageContext ctx) {
        handleOnClient(message::doClientEvent, ctx);
    }
}
