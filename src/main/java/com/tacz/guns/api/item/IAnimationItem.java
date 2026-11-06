package com.tacz.guns.api.item;

import net.minecraft.item.ItemStack;

public interface IAnimationItem {

    /**
     * 返回物品是否需要重新初始化状态机或属性
     * 
     * @param stack1 物品2
     * @param stack2 物品2
     * @return 是否需要重新初始化
     */
    boolean isSame(ItemStack stack1, ItemStack stack2);

    /**
     * 忽略堆叠数量比较两个物品（1.20.1 的 {@code ItemStack.isSameItemSameTags}）。
     * <p>
     * 1.7.10 移植说明：{@link ItemStack#areItemStacksEqual(ItemStack, ItemStack)} 会连 stackSize 一起比较，
     * 不满足「忽略数量」的语义，因此这里手写 item + NBT 的比较（上游 isSameItemSameTags 不含 damage）。
     */
    static boolean matchesIgnoreCount(ItemStack pStack, ItemStack pOther) {
        if (pStack == pOther) {
            return true;
        }
        if (pStack == null || pOther == null) {
            return false;
        }
        if (pStack.getItem() != pOther.getItem()) {
            return false;
        }
        return ItemStack.areItemStackTagsEqual(pStack, pOther);
    }
}
