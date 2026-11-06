package com.tacz.guns.inventory.tooltip;

import net.minecraft.item.ItemStack;

/**
 * 弹药盒 tooltip 的数据载体。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/inventory/tooltip/AmmoBoxTooltip.java}
 * 
 * <pre>
 * {@code public class AmmoBoxTooltip implements TooltipComponent}
 * </pre>
 *
 * <p>
 * <b>1.7.10 差异</b>：1.7.10 没有 {@code net.minecraft.world.inventory.tooltip.TooltipComponent}，
 * 也没有「把数据组件交给 GUI 渲染」的 tooltip 管线（上游靠 {@code RegisterClientTooltipComponentFactoriesEvent}
 * + {@code TooltipComponent}）。因此本类降级为<b>纯数据类</b>：字段 / 构造器 / getter 与上游一一对应，
 * 由 L2 客户端（{@code client/tooltip/**}）自行绘制。
 * {@code net.minecraft.world.item.ItemStack} → {@link ItemStack}（1.7.10 同名类，包不同）。
 */
public class AmmoBoxTooltip {

    private final ItemStack ammoBox;
    private final ItemStack ammo;
    private final int count;

    public AmmoBoxTooltip(ItemStack ammoBox, ItemStack ammo, int count) {
        this.ammoBox = ammoBox;
        this.ammo = ammo;
        this.count = count;
    }

    public ItemStack getAmmoBox() {
        return ammoBox;
    }

    public ItemStack getAmmo() {
        return ammo;
    }

    public int getCount() {
        return count;
    }
}
