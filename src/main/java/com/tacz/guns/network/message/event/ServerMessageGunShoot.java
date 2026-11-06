package com.tacz.guns.network.message.event;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.common.GunShootEvent;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：在客户端投递 {@link GunShootEvent}。
 * 上游：{@code .../network/message/event/ServerMessageGunShoot.java}
 */
public class ServerMessageGunShoot extends AbstractShooterGunEventMessage {

    public ServerMessageGunShoot(int shooterId, ItemStack gunItemStack) {
        super(shooterId, gunItemStack);
    }

    public ServerMessageGunShoot() {}

    @Override
    protected void postEvent(EntityLivingBase shooter) {
        MinecraftForge.EVENT_BUS.post(new GunShootEvent(shooter, gunItemStack, Side.CLIENT));
    }

    public static void handle(ServerMessageGunShoot message, MessageContext ctx) {
        handleOnClient(message::doClientEvent, ctx);
    }
}
