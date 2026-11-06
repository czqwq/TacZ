package com.tacz.guns.inventory.tooltip;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;

/**
 * 枪械 tooltip 的数据载体。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/inventory/tooltip/GunTooltip.java}
 * 
 * <pre>
 * {@code public class GunTooltip implements TooltipComponent}
 * </pre>
 *
 * <p>
 * <b>1.7.10 差异</b>：去掉 {@code TooltipComponent} 接口，保留字段 / 构造器 / getter 的纯数据类；
 * {@code net.minecraft.world.item.ItemStack} → {@link ItemStack}，
 * {@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}。
 */
public class GunTooltip {

    private final ItemStack gun;
    private final IGun iGun;
    private final ResourceLocation ammoId;
    private final CommonGunIndex gunIndex;

    public GunTooltip(ItemStack gun, IGun iGun, ResourceLocation ammoId, CommonGunIndex gunIndex) {
        this.gun = gun;
        this.iGun = iGun;
        this.ammoId = ammoId;
        this.gunIndex = gunIndex;
    }

    public ItemStack getGun() {
        return gun;
    }

    public IGun getIGun() {
        return iGun;
    }

    public ResourceLocation getAmmoId() {
        return ammoId;
    }

    public CommonGunIndex getGunIndex() {
        return gunIndex;
    }
}
