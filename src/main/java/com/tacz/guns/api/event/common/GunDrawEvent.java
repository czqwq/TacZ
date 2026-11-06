package com.tacz.guns.api.event.common;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.Side;

/**
 * 生物开始切换枪械时触发的事件
 */
public class GunDrawEvent extends Event implements KubeJSGunEventPoster<GunDrawEvent> {

    private final EntityLivingBase entity;
    private final ItemStack previousGunItem;
    private final ItemStack currentGunItem;
    private final Side logicalSide;

    public GunDrawEvent(EntityLivingBase entity, ItemStack previousGunItem, ItemStack currentGunItem, Side side) {
        this.entity = entity;
        this.previousGunItem = previousGunItem;
        this.currentGunItem = currentGunItem;
        this.logicalSide = side;
        postEventToKubeJS(this);
    }

    public EntityLivingBase getEntity() {
        return entity;
    }

    public ItemStack getPreviousGunItem() {
        return previousGunItem;
    }

    public ItemStack getCurrentGunItem() {
        return currentGunItem;
    }

    public Side getLogicalSide() {
        return logicalSide;
    }
}
