package com.tacz.guns.api.event.common;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.Side;

/**
 * 生物射击时触发的事件。扣动一次扳机只会触发一次，但可能产生多次成功击发通知
 * {@link GunFirePostEvent}（如枪械处于 Burst 模式）。
 */
public class GunShootEvent extends Event implements KubeJSGunEventPoster<GunShootEvent> {

    private final EntityLivingBase shooter;
    private final ItemStack gunItemStack;
    private final Side logicalSide;

    public GunShootEvent(EntityLivingBase shooter, ItemStack gunItemStack, Side side) {
        this.shooter = shooter;
        this.gunItemStack = gunItemStack;
        this.logicalSide = side;
        postEventToKubeJS(this);
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    public EntityLivingBase getShooter() {
        return shooter;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public Side getLogicalSide() {
        return logicalSide;
    }
}
