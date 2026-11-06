package com.tacz.guns.api.item.nbt;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.util.ResourceLocationHelper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.util.datafixer.AttachmentIdFix;

public interface GunItemDataAccessor extends IGun {

    String GUN_ID_TAG = "GunId";
    String GUN_FIRE_MODE_TAG = "GunFireMode";
    String GUN_HAS_BULLET_IN_BARREL = "HasBulletInBarrel";
    String GUN_CURRENT_AMMO_COUNT_TAG = "GunCurrentAmmoCount";
    String GUN_ATTACHMENT_BASE = "Attachment";
    String GUN_EXP_TAG = "GunLevelExp";
    String GUN_DUMMY_AMMO = "DummyAmmo";
    String GUN_MAX_DUMMY_AMMO = "MaxDummyAmmo";
    String GUN_ATTACHMENT_LOCK = "AttachmentLock";
    String GUN_DISPLAY_ID_TAG = "GunDisplayId";
    String LASER_COLOR_TAG = "LaserColor";
    String GUN_OVERHEAT_TAG = "HeatAmount";
    String GUN_OVERHEAT_LOCK_TAG = "OverHeated";

    @Override
    default boolean useDummyAmmo(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        return nbt.hasKey(GUN_DUMMY_AMMO, Constants.NBT.TAG_INT);
    }

    @Override
    default int getDummyAmmoAmount(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        return Math.max(0, nbt.getInteger(GUN_DUMMY_AMMO));
    }

    @Override
    default void setDummyAmmoAmount(ItemStack gun, int amount) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        nbt.setInteger(GUN_DUMMY_AMMO, Math.max(amount, 0));
    }

    @Override
    default void addDummyAmmoAmount(ItemStack gun, int amount) {
        if (!useDummyAmmo(gun)) {
            return;
        }
        int maxDummyAmmo = Integer.MAX_VALUE;
        if (hasMaxDummyAmmo(gun)) {
            maxDummyAmmo = getMaxDummyAmmoAmount(gun);
        }
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        long updatedAmount = Math.min((long) getDummyAmmoAmount(gun) + amount, maxDummyAmmo);
        nbt.setInteger(GUN_DUMMY_AMMO, (int) Math.max(updatedAmount, 0));
    }

    @Override
    default boolean hasMaxDummyAmmo(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        return nbt.hasKey(GUN_MAX_DUMMY_AMMO, Constants.NBT.TAG_INT);
    }

    @Override
    default int getMaxDummyAmmoAmount(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        return Math.max(0, nbt.getInteger(GUN_MAX_DUMMY_AMMO));
    }

    @Override
    default void setMaxDummyAmmoAmount(ItemStack gun, int amount) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        nbt.setInteger(GUN_MAX_DUMMY_AMMO, Math.max(amount, 0));
    }

    @Override
    default boolean hasAttachmentLock(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (nbt.hasKey(GUN_ATTACHMENT_LOCK, Constants.NBT.TAG_BYTE)) {
            return nbt.getBoolean(GUN_ATTACHMENT_LOCK);
        }
        return false;
    }

    @Override
    default void setAttachmentLock(ItemStack gun, boolean lock) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        nbt.setBoolean(GUN_ATTACHMENT_LOCK, lock);
    }

    @Override
    @Nonnull
    default ResourceLocation getGunId(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (nbt.hasKey(GUN_ID_TAG, Constants.NBT.TAG_STRING)) {
            ResourceLocation gunId = ResourceLocationHelper.tryParse(nbt.getString(GUN_ID_TAG));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_GUN_ID);
        }
        return DefaultAssets.EMPTY_GUN_ID;
    }

    @Override
    default void setGunId(ItemStack gun, @Nullable ResourceLocation gunId) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (gunId != null) {
            nbt.setString(GUN_ID_TAG, gunId.toString());
        }
    }

    @Override
    @NotNull
    default ResourceLocation getGunDisplayId(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (nbt.hasKey(GUN_DISPLAY_ID_TAG, Constants.NBT.TAG_STRING)) {
            ResourceLocation gunDisplayId = ResourceLocationHelper.tryParse(nbt.getString(GUN_DISPLAY_ID_TAG));
            return Objects.requireNonNullElse(gunDisplayId, DefaultAssets.DEFAULT_GUN_DISPLAY_ID);
        }
        return DefaultAssets.DEFAULT_GUN_DISPLAY_ID;
    }

    @Override
    default void setGunDisplayId(ItemStack gun, ResourceLocation displayId) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (displayId != null) {
            nbt.setString(GUN_DISPLAY_ID_TAG, displayId.toString());
        }
    }

    @Override
    default int getLevel(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (nbt.hasKey(GUN_EXP_TAG, Constants.NBT.TAG_INT)) {
            return getLevel(nbt.getInteger(GUN_EXP_TAG));
        }
        return 0;
    }

    @Override
    default int getExp(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (nbt.hasKey(GUN_EXP_TAG, Constants.NBT.TAG_INT)) {
            return nbt.getInteger(GUN_EXP_TAG);
        }
        return 0;
    }

    @Override
    default int getExpToNextLevel(ItemStack gun) {
        int exp = getExp(gun);
        int level = getLevel(exp);
        if (level >= getMaxLevel()) {
            return 0;
        }
        int nextLevelExp = getExp(level + 1);
        return nextLevelExp - exp;
    }

    @Override
    default int getExpCurrentLevel(ItemStack gun) {
        int exp = getExp(gun);
        int level = getLevel(exp);
        if (level <= 0) {
            return exp;
        } else {
            return exp - getExp(level - 1);
        }
    }

    @Override
    default FireMode getFireMode(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (nbt.hasKey(GUN_FIRE_MODE_TAG, Constants.NBT.TAG_STRING)) {
            return FireMode.fromName(nbt.getString(GUN_FIRE_MODE_TAG));
        }
        return FireMode.UNKNOWN;
    }

    @Override
    default void setFireMode(ItemStack gun, @Nullable FireMode fireMode) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (fireMode != null) {
            nbt.setString(GUN_FIRE_MODE_TAG, fireMode.name());
            return;
        }
        nbt.setString(GUN_FIRE_MODE_TAG, FireMode.UNKNOWN.name());
    }

    @Override
    default int getCurrentAmmoCount(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (nbt.hasKey(GUN_CURRENT_AMMO_COUNT_TAG, Constants.NBT.TAG_INT)) {
            return nbt.getInteger(GUN_CURRENT_AMMO_COUNT_TAG);
        }
        return 0;
    }

    @Override
    default void setCurrentAmmoCount(ItemStack gun, int ammoCount) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        nbt.setInteger(GUN_CURRENT_AMMO_COUNT_TAG, Math.max(ammoCount, 0));
    }

    @Override
    default void reduceCurrentAmmoCount(ItemStack gun) {
        // 只在不使用背包直读的情况下减少 AmmoCount
        if (!useInventoryAmmo(gun)) {
            setCurrentAmmoCount(gun, getCurrentAmmoCount(gun) - 1);
        }
    }

    @Override
    @Nullable
    default NBTTagCompound getAttachmentTag(ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return null;
        }
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        String key = GUN_ATTACHMENT_BASE + type.name();
        if (nbt.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound allItemStackTag = nbt.getCompoundTag(key);
            if (allItemStackTag.hasKey("tag", Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound attachmentTag = allItemStackTag.getCompoundTag("tag");
                // 1.7.10 无 Item#verifyTagAfterLoad 钩子：读取时懒修复旧版配件 id（上游 AttachmentItem#verifyTagAfterLoad，
                // 见 util/datafixer/AttachmentIdFix）。getCompoundTag 返回 gun NBT 内的引用，就地修复且幂等。
                AttachmentIdFix.updateAttachmentIdInTag(attachmentTag);
                return attachmentTag;
            }
        }
        return null;
    }

    @Override
    @Nullable
    default ItemStack getBuiltinAttachment(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return null;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
            .orElse(null);
        if (index != null) {
            var builtin = index.getGunData()
                .getBuiltInAttachments();
            if (builtin.containsKey(type)) {
                ResourceLocation attachmentId = builtin.get(type)
                    .getDisplayAttachment();
                if (attachmentId != null) {
                    return AttachmentItemBuilder.create()
                        .setId(attachmentId)
                        .build();
                }
            }
        }
        return null;
    }

    @Override
    @Nullable
    default ItemStack getAttachment(ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return null;
        }
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        String key = GUN_ATTACHMENT_BASE + type.name();
        if (nbt.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
            return ItemStackNbtHelper.load(nbt.getCompoundTag(key));
        }
        return null;
    }

    @Override
    @NotNull
    default ResourceLocation getBuiltInAttachmentId(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
            .orElse(null);
        if (index != null) {
            var builtin = index.getGunData()
                .getBuiltInAttachments();
            if (builtin.containsKey(type)) {
                ResourceLocation attachmentId = builtin.get(type)
                    .getDisplayAttachment();
                if (attachmentId != null) {
                    return attachmentId;
                }
            }
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    @Override
    @Nonnull
    default ResourceLocation getAttachmentId(ItemStack gun, AttachmentType type) {
        NBTTagCompound attachmentTag = this.getAttachmentTag(gun, type);
        if (attachmentTag != null) {
            return AttachmentItemDataAccessor.getAttachmentIdFromTag(attachmentTag);
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    @Override
    default void installAttachment(@Nonnull ItemStack gun, @Nonnull ItemStack attachment) {
        if (!allowAttachment(gun, attachment)) {
            return;
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        if (iAttachment == null) {
            return;
        }
        AttachmentType attachmentType = iAttachment.getType(attachment);
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        String key = GUN_ATTACHMENT_BASE + attachmentType.name();
        NBTTagCompound attachmentTag = new NBTTagCompound();
        ItemStackNbtHelper.save(attachment, attachmentTag);
        nbt.setTag(key, attachmentTag);
    }

    @Override
    default void unloadAttachment(@Nonnull ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return;
        }
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        String key = GUN_ATTACHMENT_BASE + type.name();
        NBTTagCompound attachmentTag = new NBTTagCompound();
        ItemStackNbtHelper.writeEmptyTag(attachmentTag);
        nbt.setTag(key, attachmentTag);
    }

    @Override
    default float getAimingZoom(ItemStack gunItem) {
        float zoom = 1;
        ResourceLocation scopeId = this.getAttachmentId(gunItem, AttachmentType.SCOPE);
        boolean builtin = false;
        if (scopeId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeId = getBuiltInAttachmentId(gunItem, AttachmentType.SCOPE);
            builtin = true;
        }
        if (!DefaultAssets.isEmptyAttachmentId(scopeId)) {
            NBTTagCompound attachmentTag = this.getAttachmentTag(gunItem, AttachmentType.SCOPE);
            int zoomNumber = builtin ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(attachmentTag);
            ResourceLocation resolvedScopeId = scopeId;
            float[] zooms = TimelessAPI.getClientAttachmentVariantIndex(getAttachment(gunItem, AttachmentType.SCOPE))
                .map(ClientAttachmentVariantIndex::getZoom)
                .orElseGet(
                    () -> TimelessAPI.getClientAttachmentIndex(resolvedScopeId)
                        .map(ClientAttachmentIndex::getZoom)
                        .orElse(null));
            if (zooms != null) {
                zoom = zooms[zoomNumber % zooms.length];
            }
        } else {
            zoom = TimelessAPI.getGunDisplay(gunItem)
                .map(GunDisplayInstance::getIronZoom)
                .orElse(1f);
        }
        return zoom;
    }

    @Override
    default boolean hasBulletInBarrel(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (nbt.hasKey(GUN_HAS_BULLET_IN_BARREL, Constants.NBT.TAG_BYTE)) {
            return nbt.getBoolean(GUN_HAS_BULLET_IN_BARREL);
        }
        return false;
    }

    @Override
    default void setBulletInBarrel(ItemStack gun, boolean bulletInBarrel) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        nbt.setBoolean(GUN_HAS_BULLET_IN_BARREL, bulletInBarrel);
    }

    @Override
    default boolean hasCustomLaserColor(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        return nbt.hasKey(LASER_COLOR_TAG, Constants.NBT.TAG_INT);
    }

    @Override
    default int getLaserColor(ItemStack gun) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        if (!hasCustomLaserColor(gun)) {
            return 0xFF0000;
        }
        return nbt.getInteger(LASER_COLOR_TAG);
    }

    @Override
    default void setLaserColor(ItemStack gun, int color) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(gun);
        nbt.setInteger(LASER_COLOR_TAG, color);
    }

    /**
     * Heat Data
     */
    @Override
    default boolean hasHeatData(ItemStack gun) {
        return ItemStackNbtHelper.getOrCreateTag(gun)
            .hasKey(GUN_OVERHEAT_TAG, Constants.NBT.TAG_FLOAT);
    }

    @Override
    default boolean isOverheatLocked(ItemStack gun) {
        return ItemStackNbtHelper.getOrCreateTag(gun)
            .getBoolean(GUN_OVERHEAT_LOCK_TAG);
    }

    @Override
    default void setOverheatLocked(ItemStack gun, boolean locked) {
        ItemStackNbtHelper.getOrCreateTag(gun)
            .setBoolean(GUN_OVERHEAT_LOCK_TAG, locked);
    }

    @Override
    default float getHeatAmount(ItemStack gun) {
        if (hasHeatData(gun)) return ItemStackNbtHelper.getOrCreateTag(gun)
            .getFloat(GUN_OVERHEAT_TAG);
        return 0f;
    }

    @Override
    default void setHeatAmount(ItemStack gun, float amount) {
        ItemStackNbtHelper.getOrCreateTag(gun)
            .setFloat(GUN_OVERHEAT_TAG, amount >= 0 ? amount : 0f);
    }

    @Override
    default float lerpRPM(ItemStack gun) {
        return TimelessAPI.getCommonGunIndex(getGunId(gun))
            .map(
                index -> index.getGunData()
                    .getHeatData())
            .map(heatData -> {
                float heatPercentage = (getHeatAmount(gun) / heatData.getHeatMax());
                return Mth.lerp(heatPercentage, heatData.getMinRpmMod(), heatData.getMaxRpmMod());
            })
            .orElse(1f);
    }

    @Override
    default float lerpInaccuracy(ItemStack gun) {
        return TimelessAPI.getCommonGunIndex(getGunId(gun))
            .map(
                index -> index.getGunData()
                    .getHeatData())
            .map(heatData -> {
                float heatPercentage = (getHeatAmount(gun) / heatData.getHeatMax());
                return Mth.lerp(heatPercentage, heatData.getMinInaccuracy(), heatData.getMaxInaccuracy());
            })
            .orElse(1f);
    }
}
