package com.tacz.guns.inventory.tooltip;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;

/**
 * 弹药库 tooltip 的数据载体。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/inventory/tooltip/AmmoStorageTooltip.java}
 * 
 * <pre>
 * {@code public class AmmoStorageTooltip implements TooltipComponent}
 * </pre>
 *
 * <p>
 * <b>1.7.10 差异</b>：去掉 {@code TooltipComponent} 接口（1.7.10 无 tooltip 组件管线），
 * 保留字段 {@code entries}/{@code hidden} 与全部 getter，纯数据类，L2 客户端自行绘制。
 * 内部 record 的 {@code getAmmoName()} 返回 {@link IChatComponent}
 * （1.7.10 没有 {@code Component}；{@code ItemStack#func_151000_E()} 是 1.7.10 的 {@code getHoverName()}）。
 */
public class AmmoStorageTooltip {

    private final List<Entry> entries;
    private final int hidden;

    public AmmoStorageTooltip(List<Entry> entries, int hidden) {
        this.entries = entries;
        this.hidden = hidden;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int getHidden() {
        return hidden;
    }

    public record Entry(ItemStack ammo, long count) {

        /** 上游 {@code ammo.getHoverName()}；1.7.10 对应 {@code ItemStack#func_151000_E()}。 */
        public IChatComponent getAmmoName() {
            return ammo == null ? null : ammo.func_151000_E();
        }
    }
}
