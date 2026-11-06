package com.tacz.guns.entity.shooter;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.item.gun.AbstractGunItem;

/**
 * 过热（heat）每 tick 结算入口。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityHeat.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder)} 与 {@code tickHeat()} 被 {@code LivingEntityMixin} 直接调用。
 *
 * <p>
 * 平台替换（相对上游）：{@code LivingEntity → EntityLivingBase}、
 * {@code getMainHandItem() → getHeldItem()}、{@code var → 显式类型}。
 */
public class LivingEntityHeat {

    private final EntityLivingBase shooter;
    private final ShooterDataHolder data;

    public LivingEntityHeat(EntityLivingBase shooter, ShooterDataHolder dataHolder) {
        this.shooter = shooter;
        this.data = dataHolder;
    }

    public void tickHeat() {
        ItemStack gunStack = shooter.getHeldItem();
        if (gunStack != null && gunStack.getItem() instanceof AbstractGunItem gunItem) {
            gunItem.tickHeat(data, gunStack, shooter);
        }
    }
}
