package com.tacz.guns.client.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerAim;
import com.tacz.guns.resource.modifier.custom.AdsModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.time.GunTime;

/**
 * 客户端瞄准状态机。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gameplay/LocalPlayerAim.java}。
 *
 * <p>
 * 平台替换（相对上游）：{@code LocalPlayer} → {@link EntityPlayerSP}、
 * {@code getMainHandItem()} → {@code getHeldItem()}、
 * {@code net.minecraft.world.item.ItemStack} → {@link ItemStack}、
 * {@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}、
 * {@code net.minecraft.util.Mth} → {@link Mth}（mcshim，内部转 {@code MathHelper}）。
 * 无降级点。
 */
public class LocalPlayerAim {

    private final LocalPlayerDataHolder data;
    private final EntityPlayerSP player;

    public LocalPlayerAim(LocalPlayerDataHolder data, EntityPlayerSP player) {
        this.data = data;
        this.player = player;
    }

    public void aim(boolean isAim) {
        // 暂定为主手（1.7.10 无双持）
        ItemStack mainHandItem = player.getHeldItem();
        if (!(mainHandItem != null && mainHandItem.getItem() instanceof IGun iGun)) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(mainHandItem);
        TimelessAPI.getClientGunIndex(gunId)
            .ifPresent(gunIndex -> {
                if (isAim && shouldBlockAim(gunIndex.getGunData())) {
                    if (data.clientIsAiming) {
                        data.clientIsAiming = false;
                        NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerAim(false));
                    }
                    return;
                }
                data.clientIsAiming = isAim;
                // 发送切换瞄准状态的数据包，通知服务器
                NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerAim(isAim));
            });
    }

    public float getClientAimingProgress(float partialTicks) {
        return Mth.lerp(partialTicks, LocalPlayerDataHolder.oldAimingProgress, data.clientAimingProgress);
    }

    public boolean isAim() {
        return data.clientIsAiming;
    }

    public void tickAimingProgress() {
        ItemStack mainHandItem = player.getHeldItem();
        // 如果主手物品不是枪械，则取消瞄准状态并将 aimingProgress 归零，返回。
        if (!(mainHandItem != null && mainHandItem.getItem() instanceof IGun iGun)) {
            data.clientAimingProgress = 0;
            LocalPlayerDataHolder.oldAimingProgress = 0;
            return;
        }
        // 如果正在收枪，则不能瞄准
        if (GunTime.nowMillis() - data.clientDrawTimestamp < 0) {
            data.clientIsAiming = false;
        }
        ResourceLocation gunId = iGun.getGunId(mainHandItem);
        TimelessAPI.getCommonGunIndex(gunId)
            .ifPresentOrElse(index -> {
                if (shouldBlockAim(index.getGunData())) {
                    data.clientIsAiming = false;
                }
                float alphaProgress = this.getAlphaProgress(index.getGunData());
                this.aimProgressCalculate(alphaProgress);
            }, () -> {
                data.clientAimingProgress = 0;
                LocalPlayerDataHolder.oldAimingProgress = 0;
            });
    }

    private void aimProgressCalculate(float alphaProgress) {
        LocalPlayerDataHolder.oldAimingProgress = data.clientAimingProgress;
        if (data.clientIsAiming) {
            // 处于执行瞄准状态，增加 aimingProgress
            data.clientAimingProgress += alphaProgress;
            if (data.clientAimingProgress > 1) {
                data.clientAimingProgress = 1;
            }
        } else {
            // 处于取消瞄准状态，减小 aimingProgress
            data.clientAimingProgress -= alphaProgress;
            if (data.clientAimingProgress < 0) {
                data.clientAimingProgress = 0;
            }
        }
        data.clientAimingTimestamp = GunTime.nowMillis();
    }

    private boolean shouldBlockAim(GunData gunData) {
        IGunOperator operator = IGunOperator.fromLivingEntity(this.player);
        boolean isReloading = operator.getSynReloadState()
            .getStateType()
            .isReloading();
        boolean isReloadStarting = data.clientReloadStarting;
        boolean isBolting = data.isBolting || operator.getSynIsBolting();
        return data.isTimedStateLocked() || (gunData.isForceAimExitOnReload() && (isReloading || isReloadStarting))
            || (gunData.isForceAimExitOnBolt() && isBolting);
    }

    private float getAlphaProgress(GunData gunData) {
        float aimTime = gunData.getAimTime();
        IGunOperator operator = IGunOperator.fromLivingEntity(this.player);
        if (operator.getCacheProperty() != null) {
            aimTime = operator.getCacheProperty()
                .<Float>getCache(AdsModifier.ID);
        }
        aimTime = Math.max(0, aimTime);
        return (GunTime.nowMillis() - data.clientAimingTimestamp + 1) / (aimTime * 1000);
    }
}
