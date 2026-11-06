package com.tacz.guns.entity.shooter;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.event.common.GunFinishReloadEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunFinishReload;
import com.tacz.guns.network.message.event.ServerMessageGunReload;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.relauncher.Side;

/**
 * 换弹（reload）逻辑与换弹状态机。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityReload.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder, LivingEntityDrawGun, LivingEntityShoot)}，
 * 以及 {@code reload()} / {@code cancelReload()} / {@code tickReloadState()} 被 {@code LivingEntityMixin} 直接调用。
 *
 * <p>
 * 平台替换（相对上游）：
 * {@code LivingEntity → EntityLivingBase}、{@code LogicalSide → cpw.mods.fml.relauncher.Side}、
 * {@code ItemStack.EMPTY → null}、{@code ItemStack#isEmpty() → 空栈判定（stack == null || stack.getItem() == null）}、
 * {@code Entity#getId() → getEntityId()}。
 */
public class LivingEntityReload {

    private final EntityLivingBase shooter;
    private final ShooterDataHolder data;
    private final LivingEntityDrawGun draw;
    private final LivingEntityShoot shoot;

    public LivingEntityReload(EntityLivingBase shooter, ShooterDataHolder data, LivingEntityDrawGun draw,
        LivingEntityShoot shoot) {
        this.shooter = shooter;
        this.data = data;
        this.draw = draw;
        this.shoot = shoot;
    }

    public void reload() {
        // 1.7.10：Supplier#get() 可能返回 null（空手），用 getCurrentGunItem() 归一化后判空。
        ItemStack currentGunItem = data.getCurrentGunItem();
        if (currentGunItem == null) {
            return;
        }
        if (!(currentGunItem.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        ResourceLocation gunId = gunItem.getGunId(currentGunItem);
        TimelessAPI.getCommonGunIndex(gunId)
            .ifPresent(gunIndex -> {
                // 检查是否为背包直读
                if (gunItem.useInventoryAmmo(currentGunItem)) {
                    return;
                }
                // 检查换弹是否还未完成
                if (data.reloadStateType.isReloading()) {
                    return;
                }
                // 检查是否正在开火冷却
                if (shoot.getShootCoolDown() != 0) {
                    return;
                }
                // 检查是否在切枪
                if (draw.getDrawCoolDown() != 0) {
                    return;
                }
                // 检查是否在拉栓
                if (data.isBolting) {
                    return;
                }
                // 检查弹药
                IGunOperator gunOperator = IGunOperator.fromLivingEntity(shooter);
                if (gunOperator.needCheckAmmo() && !gunItem.canReload(shooter, currentGunItem)) {
                    return;
                }
                // 触发装弹事件
                if (MinecraftForge.EVENT_BUS.post(new GunReloadEvent(shooter, currentGunItem, Side.SERVER))) {
                    return;
                }
                NetworkHandler
                    .sendToTrackingEntity(new ServerMessageGunReload(shooter.getEntityId(), currentGunItem), shooter);
                Bolt boltType = gunIndex.getGunData()
                    .getBolt();
                int ammoCount = gunItem.getCurrentAmmoCount(currentGunItem)
                    + (gunItem.hasBulletInBarrel(currentGunItem) && boltType != Bolt.OPEN_BOLT ? 1 : 0);
                if (ammoCount <= 0) {
                    // 初始化空仓换弹的 tick 的状态
                    data.reloadStateType = ReloadState.StateType.EMPTY_RELOAD_FEEDING;
                } else {
                    // 初始化战术换弹的 tick 的状态
                    data.reloadStateType = ReloadState.StateType.TACTICAL_RELOAD_FEEDING;
                }
                data.reloadTimestamp = GunTime.nowMillis();
                // 调用枪械逻辑
                if (!gunItem.startReload(data, currentGunItem, shooter)) {
                    data.reloadStateType = ReloadState.StateType.NOT_RELOADING;
                    data.reloadTimestamp = -1;
                } else if (gunIndex.getGunData()
                    .isForceAimExitOnReload()) {
                        gunOperator.aim(false);
                    }
            });
    }

    public void cancelReload() {
        // 1.7.10：Supplier#get() 可能返回 null（空手），用 getCurrentGunItem() 归一化后判空。
        ItemStack currentGunItem = data.getCurrentGunItem();
        if (currentGunItem == null) {
            return;
        }
        if (!(currentGunItem.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        // 检查是否在换弹
        if (!data.reloadStateType.isReloading()) {
            return;
        }
        gunItem.interruptReload(data, currentGunItem, shooter);
    }

    public ReloadState tickReloadState() {
        ReloadState result = new ReloadState();
        // 如果没有在换弹，直接返回
        if (data.reloadTimestamp == -1) {
            return result;
        }
        ItemStack currentGunItem = data.getCurrentGunItem();
        boolean finishedReload = false;
        // 调用枪械逻辑，默认换弹和脚本换弹共用结束事件。
        // 上游此处还有 !currentGunItem.isEmpty()；1.7.10 空栈即 null，且已经被下面的 instanceof 判定挡住
        // （能走进来说明 stack 非 null 且是 AbstractGunItem），语义等价。
        if (currentGunItem != null && currentGunItem.getItem() instanceof AbstractGunItem abstractGunItem
            && TimelessAPI.getCommonGunIndex(abstractGunItem.getGunId(currentGunItem))
                .isPresent()) {
            result = abstractGunItem.tickReload(data, currentGunItem, shooter);
            finishedReload = data.reloadTimestamp != -1 && data.reloadStateType.isReloading()
                && !result.getStateType()
                    .isReloading();
        }
        // 将 tick 的结果保存到 data holder
        data.reloadStateType = result.getStateType();
        if (!result.getStateType()
            .isReloading()) {
            data.reloadTimestamp = -1;
        }
        // 状态更新后再通知监听器。
        if (finishedReload) {
            MinecraftForge.EVENT_BUS.post(new GunFinishReloadEvent(shooter, currentGunItem, Side.SERVER));
            NetworkHandler.sendToTrackingEntityAndSelf(
                shooter,
                new ServerMessageGunFinishReload(shooter.getEntityId(), currentGunItem));
        }
        return result;
    }
}
