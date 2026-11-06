package com.tacz.guns.entity.shooter;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.index.CommonGunIndex;

/**
 * 主动卸弹（unload ammo）逻辑。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityUnload.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder, LivingEntityDrawGun, LivingEntityShoot, LivingEntityMelee)}
 * 与 {@code unloadAmmo()} 被 {@code LivingEntityMixin} 直接调用。
 *
 * <p>
 * 平台替换（相对上游）：
 * {@code LivingEntity → EntityLivingBase}、{@code level().isClientSide() → worldObj.isRemote}、
 * {@code isAlive() → isEntityAlive()}、{@code getMainHandItem() → getHeldItem()}、{@code var → 显式类型}。
 * {@code isSpectator()} 在 1.7.10 无对应（无旁观者模式），该条件降级为不检查。
 */
public class LivingEntityUnload {

    private final EntityLivingBase shooter;
    private final ShooterDataHolder data;
    private final LivingEntityDrawGun draw;
    private final LivingEntityShoot shoot;
    private final LivingEntityMelee melee;

    public LivingEntityUnload(EntityLivingBase shooter, ShooterDataHolder data, LivingEntityDrawGun draw,
        LivingEntityShoot shoot, LivingEntityMelee melee) {
        this.shooter = shooter;
        this.data = data;
        this.draw = draw;
        this.shoot = shoot;
        this.melee = melee;
    }

    public void unloadAmmo() {
        // TODO(port): 上游还有 shooter.isSpectator()（旁观者模式），1.7.10 无该概念，等价条件不存在。
        if (shooter.worldObj.isRemote || !shooter.isEntityAlive()
            || data.currentGunItem == null
            || data.reloadStateType.isReloading()
            || data.isBolting
            || data.meleePrepTickCount >= 0
            || draw.getDrawCoolDown() != 0
            || shoot.getShootCoolDown() != 0
            || melee.getMeleeCoolDown() != 0
            || data.isAiming
            || data.aimingProgress > 0
            || data.sprintTimeS > 0
            || shooter.isSprinting()) {
            return;
        }
        ItemStack stack = shooter.getHeldItem();
        // 上游用引用比较确认「主手物品就是 data 记录的那一个 ItemStack 实例」，1.7.10 语义相同。
        // 1.7.10：Supplier#get() 可能返回 null（空手/切枪过渡），必须先经 getCurrentGunItem() 归一化，
        // 否则这里的 .get() 直接 NPE（上游为恒非 null 的 ItemStack.EMPTY）。
        if (stack != data.getCurrentGunItem() || stack == null
            || !(stack.getItem() instanceof AbstractGunItem gun)
            || gun.useInventoryAmmo(stack)) {
            return;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
            .orElse(null);
        if (index == null || !index.canUnloadAmmo()) {
            return;
        }
        gun.unloadAmmo(data, stack, shooter);
    }
}
