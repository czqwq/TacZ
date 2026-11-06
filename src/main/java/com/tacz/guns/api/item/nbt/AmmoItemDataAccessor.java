package com.tacz.guns.api.item.nbt;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.util.ResourceLocationHelper;

public interface AmmoItemDataAccessor extends IAmmo {

    String AMMO_ID_TAG = "AmmoId";

    @Override
    @Nonnull
    default ResourceLocation getAmmoId(ItemStack ammo) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(ammo);
        if (nbt.hasKey(AMMO_ID_TAG, Constants.NBT.TAG_STRING)) {
            ResourceLocation gunId = ResourceLocationHelper.tryParse(nbt.getString(AMMO_ID_TAG));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_AMMO_ID);
        }
        return DefaultAssets.EMPTY_AMMO_ID;
    }

    @Override
    default void setAmmoId(ItemStack ammo, @Nullable ResourceLocation ammoId) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(ammo);
        if (ammoId != null) {
            nbt.setString(AMMO_ID_TAG, ammoId.toString());
            return;
        }
        nbt.setString(AMMO_ID_TAG, DefaultAssets.DEFAULT_AMMO_ID.toString());
    }

    @Override
    default boolean isAmmoOfGun(ItemStack gun, ItemStack ammo) {
        // 1.20 的 ItemStack 永不为 null（空栈是 ItemStack.EMPTY，getItem() 返回 AIR）；
        // 1.7.10 用 null 表示空栈，直接调用 gun.getItem() 会在空手/空槽位时 NPE。
        // 与同类 AmmoBoxItemDataAccessor#isAmmoBoxOfGun 的写法保持一致。
        if (ItemStackNbtHelper.isEmpty(gun) || ItemStackNbtHelper.isEmpty(ammo)) {
            return false;
        }
        if (gun.getItem() instanceof IGun iGun && ammo.getItem() instanceof IAmmo iAmmo) {
            ResourceLocation gunId = iGun.getGunId(gun);
            ResourceLocation ammoId = iAmmo.getAmmoId(ammo);
            return TimelessAPI.getCommonGunIndex(gunId)
                .map(
                    gunIndex -> gunIndex.getGunData()
                        .getAmmoId()
                        .equals(ammoId))
                .orElse(false);
        }
        return false;
    }
}
