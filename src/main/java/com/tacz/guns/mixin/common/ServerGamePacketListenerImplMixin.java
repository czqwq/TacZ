package com.tacz.guns.mixin.common;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tacz.guns.api.entity.IGunOperator;

/**
 * 搬运自上游 1.20.1 {@code mixin/common/ServerGamePacketListenerImplMixin.java}。
 *
 * <p>
 * 1.7.10 适配：{@code ServerGamePacketListenerImpl} → {@code NetHandlerPlayServer}；
 * 疾跑入口 {@code handlePlayerCommand} → {@code processEntityAction(C0BPacketEntityAction)}；
 * 上游 MixinExtras {@code @WrapOperation} → 核心 Mixin {@code @Redirect}（语义等价）。
 */
@Mixin(NetHandlerPlayServer.class)
public class ServerGamePacketListenerImplMixin {

    @Redirect(
        method = "processEntityAction",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayerMP;setSprinting(Z)V"))
    public void cancelSprintCommand(EntityPlayerMP player, boolean sprint) {
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        player.setSprinting(gunOperator.getProcessedSprintStatus(sprint));
    }
}
