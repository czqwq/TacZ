package com.tacz.guns.api.item.nbt;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 1.7.10 移植辅助：ItemStack 的 1.20.1 NBT API 平移。
 * <p>
 * 上游（1.20.1）ItemStack 提供 {@code getOrCreateTag()/getTag()/hasTag()/isEmpty()/save(CompoundTag)}，
 * 1.7.10 均无。这里给出语义等价实现，供 api/** 与其它工作流复用。
 * <p>
 * <b>重要语义</b>：1.7.10 里 ItemStack 可以为 {@code null}（例如空的物品栏槽位），
 * 所以本类所有方法都接受 null：
 * <ul>
 * <li>{@link #getOrCreateTag(ItemStack)} 对 null / 无 tag 的栈返回一个<b>新建的 tag</b>；
 * 对 null 栈来说这个 tag 是脱离栈的临时对象（读默认为空、写被丢弃），保证调用方不会 NPE；</li>
 * <li>{@link #isEmpty(ItemStack)} 等价于 1.20 的 {@code ItemStack#isEmpty()}；</li>
 * <li>没有 {@code ItemStack.EMPTY} 空栈单例，1.7.10 用 {@code null} 表示空栈。</li>
 * </ul>
 */
public final class ItemStackNbtHelper {

    private ItemStackNbtHelper() {}

    /**
     * 等价于 1.20 的 {@code ItemStack#getOrCreateTag()}。
     */
    public static NBTTagCompound getOrCreateTag(@Nullable ItemStack stack) {
        if (stack == null) {
            return new NBTTagCompound();
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    /**
     * 等价于 1.20 的 {@code ItemStack#getTag()}，可能为 null。
     */
    @Nullable
    public static NBTTagCompound getTag(@Nullable ItemStack stack) {
        return stack == null ? null : stack.getTagCompound();
    }

    /**
     * 等价于 1.20 的 {@code ItemStack#hasTag()}。
     */
    public static boolean hasTag(@Nullable ItemStack stack) {
        return stack != null && stack.hasTagCompound();
    }

    /**
     * 等价于 1.20 的 {@code ItemStack#isEmpty()}：1.7.10 用 null 表示空栈。
     */
    public static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.getItem() == null;
    }

    /**
     * 等价于 1.20 的 {@code ItemStack#save(CompoundTag)}：
     * 1.7.10 的 {@link ItemStack#writeToNBT(NBTTagCompound)} 会写入 id/Count/Damage/tag 键。
     * 空栈（null）无法写入，调用方需自行决定写什么（例如 {@link #writeEmptyTag(NBTTagCompound)}）。
     */
    public static void save(@Nullable ItemStack stack, NBTTagCompound tag) {
        if (!isEmpty(stack)) {
            stack.writeToNBT(tag);
        }
    }

    /**
     * 等价于 1.20 的 {@code ItemStack.EMPTY.save(tag)}：写入 1.7.10 的空栈表示（id=0）。
     */
    public static void writeEmptyTag(NBTTagCompound tag) {
        tag.setShort("id", (short) 0);
        tag.setByte("Count", (byte) 0);
        tag.setShort("Damage", (short) 0);
    }

    /**
     * 等价于 1.20 的 {@code ItemStack.of(CompoundTag)}：1.7.10 用
     * {@link ItemStack#loadItemStackFromNBT(NBTTagCompound)}，空栈返回 null。
     */
    @Nullable
    public static ItemStack load(@Nullable NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("id")) {
            return null;
        }
        return ItemStack.loadItemStackFromNBT(tag);
    }

    /**
     * 判定 tag 是否是「空栈」标记（id=0），用于卸载配件的占位。
     */
    public static boolean isPlaceholderTag(@Nullable NBTTagCompound tag) {
        return tag == null || !tag.hasKey("id") || tag.getShort("id") == 0;
    }
}
