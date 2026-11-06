package com.tacz.guns.api.event.common;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.Side;

/**
 * 生物开始更换枪械弹药时触发的事件。
 */
public class GunReloadEvent extends Event implements KubeJSGunEventPoster<GunReloadEvent> {

    private final EntityLivingBase entity;
    private final ItemStack gunItemStack;
    private final Side logicalSide;

    public GunReloadEvent(EntityLivingBase entity, ItemStack gunItemStack, Side side) {
        this.entity = entity;
        this.gunItemStack = gunItemStack;
        this.logicalSide = side;
        postEventToKubeJS(this);
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    public EntityLivingBase getEntity() {
        return entity;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public Side getLogicalSide() {
        return logicalSide;
    }
}
