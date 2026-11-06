package com.tacz.guns.client.model.papi;

import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gameplay.ClientAmmoCount;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;

public class AmmoCountPapi implements Function<ItemStack, String> {

    public static final String NAME = "ammo_count";

    @Override
    public String apply(ItemStack stack) {
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(stack);
            ClientGunIndex gunIndex = TimelessAPI.getClientGunIndex(gunId)
                .orElse(null);
            if (gunIndex == null) {
                return "";
            }
            GunData gunData = gunIndex.getGunData();
            if (gunData == null) {
                return "";
            }
            // 移植说明：上游 AmmoCountPapi.java:32 用 Minecraft.getMinecraft().thePlayer（EntityPlayerSP）与
            // player.getHeldItem()。1.7.10 对应 Minecraft.getMinecraft().thePlayer（EntityPlayerSP）
            // 与 getHeldItem()；两者都是「当前手持物品栈」，语义一致。
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            int ammoCount = iGun.useInventoryAmmo(stack) && player != null && player.getHeldItem() == stack
                ? ClientAmmoCount.getCached(player, stack, iGun)
                : iGun.getCurrentAmmoCount(stack);
            ammoCount += iGun.hasBulletInBarrel(stack) && gunData.getBolt() != Bolt.OPEN_BOLT ? 1 : 0;
            return "" + ammoCount;
        }
        return "";
    }
}
