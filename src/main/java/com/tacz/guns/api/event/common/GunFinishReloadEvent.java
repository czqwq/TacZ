package com.tacz.guns.api.event.common;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.Side;

/**
 * 换弹流程结束或被打断后触发一次。
 * 不可取消的结束通知，此时弹药已经结算；阻止换弹请取消 {@link GunReloadEvent}。
 */
public class GunFinishReloadEvent extends Event implements KubeJSGunEventPoster<GunFinishReloadEvent> {

    private final EntityLivingBase entity;
    private final ItemStack gunItemStack;
    private final Side logicalSide;

    public GunFinishReloadEvent(EntityLivingBase entity, ItemStack gunItemStack, Side side) {
        this.entity = entity;
        this.gunItemStack = gunItemStack;
        this.logicalSide = side;
        postEventToKubeJS(this);
    }

    @Nullable
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
