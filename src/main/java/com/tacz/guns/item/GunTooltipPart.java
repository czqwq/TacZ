package com.tacz.guns.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;

/**
 * 搬运自上游 1.20.1 {@code item/GunTooltipPart.java}。
 *
 * <p>
 * 1.7.10 适配：NBT 访问 API 由 {@code CompoundTag} 换成 {@link NBTTagCompound}；
 * 上游 {@code Item#getDefaultTooltipHideFlags(ItemStack)} 是 1.8+ Forge API，1.7.10 没有，
 * 因此兜底默认值取 0（行为差异记录：1.7.10 无法通过 Item 默认值隐藏 tooltip 段落）。
 */
public enum GunTooltipPart {

    DESCRIPTION,
    AMMO_INFO,
    BASE_INFO,
    EXTRA_DAMAGE_INFO,
    UPGRADES_TIP,
    PACK_INFO;

    private final int mask = 1 << this.ordinal();

    public int getMask() {
        return this.mask;
    }

    public static int getHideFlags(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        if (tag != null && tag.hasKey("HideFlags", Constants.NBT.TAG_ANY_NUMERIC)) {
            return tag.getInteger("HideFlags");
        }
        // TODO(port): 上游回退 stack.getItem().getDefaultTooltipHideFlags(stack)（1.8+），1.7.10 无对应 API
        return 0;
    }

    public static void setHideFlags(ItemStack stack, int mask) {
        ItemStackNbtHelper.getOrCreateTag(stack)
            .setInteger("HideFlags", mask);
    }
}
