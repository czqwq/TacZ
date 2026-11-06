package com.tacz.guns.entity.shooter;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import com.tacz.guns.config.common.GunConfig;

/**
 * 弹药检查策略：创造模式玩家是否免检 / 免消耗。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityAmmoCheck.java}。
 * 构造器 {@code (EntityLivingBase)} 被 {@code LivingEntityMixin} 直接调用。
 *
 * <p>
 * 平台替换（相对上游）：
 * {@code LivingEntity → EntityLivingBase}、{@code Player → EntityPlayer}、
 * {@code Player#isCreative() → EntityPlayer#capabilities.isCreativeMode}
 * （1.20 的 {@code abilities.instabuild} 在 1.7.10 对应 {@code PlayerCapabilities#isCreativeMode}）。
 */
public class LivingEntityAmmoCheck {

    private final EntityLivingBase shooter;

    public LivingEntityAmmoCheck(EntityLivingBase shooter) {
        this.shooter = shooter;
    }

    public boolean needCheckAmmo() {
        if (shooter instanceof EntityPlayer player) {
            return !player.capabilities.isCreativeMode;
        }
        return true;
    }

    public boolean consumesAmmoOrNot() {
        if (shooter instanceof EntityPlayer player) {
            return !player.capabilities.isCreativeMode || GunConfig.CREATIVE_PLAYER_CONSUME_AMMO.get();
        }
        return true;
    }
}
