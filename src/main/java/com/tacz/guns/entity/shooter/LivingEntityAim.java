package com.tacz.guns.entity.shooter;

import java.util.Optional;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.custom.AdsModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.time.GunTime;

/**
 * 瞄准 / 开镜 / 疾跑惩罚的服务端逻辑。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntityAim.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder)} 与四个公开方法被 {@code LivingEntityMixin} 直接调用，签名不得漂移。
 *
 * <p>
 * 平台替换（相对上游）：
 * {@code LivingEntity → EntityLivingBase}、{@code CompoundTag → NBTTagCompound}、
 * {@code net.minecraft.resources.ResourceLocation → net.minecraft.util.ResourceLocation}、
 * {@code EntityLivingBase#isCrouching() → isSneaking()}（1.7.10 同名语义为「潜行」，无独立 crouching 概念）、
 * {@code Optional#ifPresentOrElse}（Java 9 API）展开为等价的 if/else，避免依赖 jvmDowngrader 的 JDK API 覆盖范围。
 */
public class LivingEntityAim {

    private final EntityLivingBase shooter;
    private final ShooterDataHolder data;

    public LivingEntityAim(EntityLivingBase shooter, ShooterDataHolder data) {
        this.shooter = shooter;
        this.data = data;
    }

    public void aim(boolean isAim) {
        if (isAim && shouldBlockAim()) {
            data.isAiming = false;
            return;
        }
        data.isAiming = isAim;
    }

    private boolean shouldBlockAim() {
        // 1.7.10：data.currentGunItem（Supplier）的 get() 可能返回 null（空手），
        // 必须先经 ShooterDataHolder#getCurrentGunItem() 归一化再判空。上游 1.20 恒为 ItemStack.EMPTY。
        ItemStack currentGunItem = data.getCurrentGunItem();
        if (currentGunItem == null) {
            return false;
        }
        if (!(currentGunItem.getItem() instanceof IGun iGun)) {
            return false;
        }
        ResourceLocation gunId = iGun.getGunId(currentGunItem);
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(gunId);
        if (gunIndexOptional.isEmpty()) {
            return false;
        }
        GunData gunData = gunIndexOptional.get()
            .getGunData();
        return (gunData.isForceAimExitOnReload() && data.reloadStateType.isReloading())
            || (gunData.isForceAimExitOnBolt() && data.isBolting);
    }

    public void zoom() {
        ItemStack currentGunItem = data.getCurrentGunItem();
        if (currentGunItem == null) {
            return;
        }
        if (!(currentGunItem.getItem() instanceof IGun iGun)) {
            return;
        }
        ResourceLocation scopeId = iGun.getAttachmentId(currentGunItem, AttachmentType.SCOPE);
        NBTTagCompound scopeTag = iGun.getAttachmentTag(currentGunItem, AttachmentType.SCOPE);
        if (!DefaultAssets.isEmptyAttachmentId(scopeId) && scopeTag != null) {
            TimelessAPI.getCommonAttachmentIndex(scopeId)
                .ifPresent(index -> {
                    int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
                    ++zoomNumber;
                    // 避免上溢变成负的
                    zoomNumber = zoomNumber % (Integer.MAX_VALUE - 1);
                    AttachmentItemDataAccessor.setZoomNumberToTag(scopeTag, zoomNumber);
                });
        }
    }

    public void tickAimingProgress() {
        // currentGunItem 如果为 null，则取消瞄准状态并将 aimingProgress 归零。
        // 1.7.10：必须用 getCurrentGunItem()（get() 可能返回 null，空手即 NPE，见 ShooterDataHolder 注释）。
        ItemStack currentGunItem = data.getCurrentGunItem();
        if (currentGunItem == null || !(currentGunItem.getItem() instanceof IGun iGun)) {
            data.aimingProgress = 0;
            data.aimingTimestamp = GunTime.nowMillis();
            return;
        }
        // 如果获取不到 gunIndex，则取消瞄准状态并将 aimingProgress 归零，返回。
        ResourceLocation gunId = iGun.getGunId(currentGunItem);
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(gunId);
        if (gunIndexOptional.isEmpty()) {
            data.aimingProgress = 0;
            return;
        }
        GunData gunData = gunIndexOptional.get()
            .getGunData();
        float aimTime = gunData.getAimTime();
        if (this.data.cacheProperty != null) {
            aimTime = this.data.cacheProperty.<Float>getCache(AdsModifier.ID);
        }
        aimTime = Math.max(0, aimTime);
        float alphaProgress = (GunTime.nowMillis() - data.aimingTimestamp + 1) / (aimTime * 1000);
        if (data.isAiming) {
            // 处于执行瞄准状态，增加 aimingProgress
            data.aimingProgress += alphaProgress;
            if (data.aimingProgress > 1) {
                data.aimingProgress = 1;
            }
        } else {
            // 处于取消瞄准状态，减小 aimingProgress
            data.aimingProgress -= alphaProgress;
            if (data.aimingProgress < 0) {
                data.aimingProgress = 0;
            }
        }
        data.aimingTimestamp = GunTime.nowMillis();
    }

    public void tickSprint() {
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        ReloadState reloadState = operator.getSynReloadState();
        if (data.isAiming || (reloadState.getStateType()
            .isReloading()
            && !reloadState.getStateType()
                .isReloadFinishing())) {
            shooter.setSprinting(false);
        }
        if (data.sprintTimestamp == -1) {
            data.sprintTimestamp = GunTime.nowMillis();
        }
        ItemStack gunItem = data.getCurrentGunItem();
        if (gunItem == null) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return;
        }
        // 上游用 Optional#ifPresentOrElse；Java 9 API，这里展开成等价 if/else
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem));
        if (gunIndexOptional.isPresent()) {
            float gunSprintTime = gunIndexOptional.get()
                .getGunData()
                .getSprintTime();
            if (shooter.isSprinting() && !shooter.isSneaking()) {
                data.sprintTimeS += (GunTime.nowMillis() - data.sprintTimestamp) / 1000f;
                if (data.sprintTimeS > gunSprintTime) {
                    data.sprintTimeS = gunSprintTime;
                }
            } else {
                data.sprintTimeS -= (GunTime.nowMillis() - data.sprintTimestamp) / 1000f;
                if (data.sprintTimeS < 0) {
                    data.sprintTimeS = 0;
                }
            }
        } else {
            data.sprintTimeS = 0;
        }
        data.sprintTimestamp = GunTime.nowMillis();
    }
}
