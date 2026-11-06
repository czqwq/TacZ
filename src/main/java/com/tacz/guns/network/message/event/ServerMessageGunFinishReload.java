package com.tacz.guns.network.message.event;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.common.GunFinishReloadEvent;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：在客户端投递 {@link GunFinishReloadEvent}。
 * 上游：{@code .../network/message/event/ServerMessageGunFinishReload.java}
 */
public class ServerMessageGunFinishReload extends AbstractShooterGunEventMessage {

    public ServerMessageGunFinishReload(int shooterId, ItemStack gunItemStack) {
        super(shooterId, gunItemStack);
    }

    public ServerMessageGunFinishReload() {}

    @Override
    protected void postEvent(EntityLivingBase shooter) {
        MinecraftForge.EVENT_BUS.post(new GunFinishReloadEvent(shooter, gunItemStack, Side.CLIENT));
    }

    public static void handle(ServerMessageGunFinishReload message, MessageContext ctx) {
        handleOnClient(message::doClientEvent, ctx);
    }
}
