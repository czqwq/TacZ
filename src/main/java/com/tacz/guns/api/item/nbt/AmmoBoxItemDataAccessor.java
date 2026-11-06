package com.tacz.guns.api.item.nbt;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;

public interface AmmoBoxItemDataAccessor extends IAmmoBox {

    String AMMO_ID_TAG = "AmmoId";
    String AMMO_COUNT_TAG = "AmmoCount";
    String CREATIVE_TAG = "Creative";
    String ALL_TYPE_CREATIVE_TAG = "AllTypeCreative";
    String LEVEL_TAG = "Level";

    @Override
    default ResourceLocation getAmmoId(ItemStack ammoBox) {
        NBTTagCompound tag = ItemStackNbtHelper.getOrCreateTag(ammoBox);
        if (tag.hasKey(AMMO_ID_TAG, Constants.NBT.TAG_STRING)) {
            return new ResourceLocation(tag.getString(AMMO_ID_TAG));
        }
        return DefaultAssets.EMPTY_AMMO_ID;
    }

    @Override
    default void setAmmoId(ItemStack ammoBox, ResourceLocation ammoId) {
        NBTTagCompound tag = ItemStackNbtHelper.getOrCreateTag(ammoBox);
        tag.setString(AMMO_ID_TAG, ammoId.toString());
    }

    @Override
    default int getAmmoCount(ItemStack ammoBox) {
        NBTTagCompound tag = ItemStackNbtHelper.getOrCreateTag(ammoBox);
        if (isAllTypeCreative(ammoBox) || isCreative(ammoBox)) {
            return Integer.MAX_VALUE;
        }
        if (tag.hasKey(AMMO_COUNT_TAG, Constants.NBT.TAG_INT)) {
            return tag.getInteger(AMMO_COUNT_TAG);
        }
        return 0;
    }

    @Override
    default void setAmmoCount(ItemStack ammoBox, int count) {
        NBTTagCompound tag = ItemStackNbtHelper.getOrCreateTag(ammoBox);
        if (isCreative(ammoBox)) {
            tag.setInteger(AMMO_COUNT_TAG, Integer.MAX_VALUE);
            return;
        }
        tag.setInteger(AMMO_COUNT_TAG, count);
    }

    @Override
    default boolean isAmmoBoxOfGun(ItemStack gun, ItemStack ammoBox) {
        if (ItemStackNbtHelper.isEmpty(gun) || ItemStackNbtHelper.isEmpty(ammoBox)) {
            return false;
        }
        if (gun.getItem() instanceof IGun iGun && ammoBox.getItem() instanceof IAmmoBox iAmmoBox) {
            if (isAllTypeCreative(ammoBox)) {
                return true;
            }
            ResourceLocation ammoId = iAmmoBox.getAmmoId(ammoBox);
            if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                return false;
            }
            ResourceLocation gunId = iGun.getGunId(gun);
            return TimelessAPI.getCommonGunIndex(gunId)
                .map(
                    gunIndex -> gunIndex.getGunData()
                        .getAmmoId()
                        .equals(ammoId))
                .orElse(false);
        }
        return false;
    }

    @Override
    default ItemStack setAmmoLevel(ItemStack ammoBox, int level) {
        NBTTagCompound tag = ItemStackNbtHelper.getOrCreateTag(ammoBox);
        tag.setInteger(LEVEL_TAG, Math.max(level, 0));
        return ammoBox;
    }

    @Override
    default int getAmmoLevel(ItemStack ammoBox) {
        NBTTagCompound tag = ItemStackNbtHelper.getOrCreateTag(ammoBox);
        if (tag.hasKey(LEVEL_TAG, Constants.NBT.TAG_INT)) {
            return tag.getInteger(LEVEL_TAG);
        }
        return 0;
    }

    @Override
    default boolean isCreative(ItemStack ammoBox) {
        NBTTagCompound tag = ItemStackNbtHelper.getTag(ammoBox);
        if (tag != null && tag.hasKey(CREATIVE_TAG, Constants.NBT.TAG_BYTE)) {
            return tag.getBoolean(CREATIVE_TAG);
        }
        return false;
    }

    @Override
    default boolean isAllTypeCreative(ItemStack ammoBox) {
        NBTTagCompound tag = ItemStackNbtHelper.getTag(ammoBox);
        if (tag != null && tag.hasKey(ALL_TYPE_CREATIVE_TAG, Constants.NBT.TAG_BYTE)) {
            return tag.getBoolean(ALL_TYPE_CREATIVE_TAG);
        }
        return false;
    }

    @Override
    default ItemStack setCreative(ItemStack ammoBox, boolean isAllType) {
        NBTTagCompound tag = ItemStackNbtHelper.getOrCreateTag(ammoBox);
        if (isAllType) {
            // 移除可能存在的创造模式标签
            if (tag.hasKey(CREATIVE_TAG, Constants.NBT.TAG_BYTE)) {
                tag.removeTag(CREATIVE_TAG);
            }
            tag.setBoolean(ALL_TYPE_CREATIVE_TAG, true);
            return ammoBox;
        }
        // 移除可能存在的全类型标签
        if (tag.hasKey(ALL_TYPE_CREATIVE_TAG, Constants.NBT.TAG_BYTE)) {
            tag.removeTag(ALL_TYPE_CREATIVE_TAG);
        }
        tag.setBoolean(CREATIVE_TAG, true);
        return ammoBox;
    }
}
