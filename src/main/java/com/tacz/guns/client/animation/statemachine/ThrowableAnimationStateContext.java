package com.tacz.guns.client.animation.statemachine;

import net.minecraft.item.ItemStack;

public class ThrowableAnimationStateContext extends ItemAnimationStateContext {

    /**
     * 移植说明：上游用 {@code null} 表示「没有物品」；1.7.10 没有 {@code EMPTY} 哨兵，
     * 全仓统一用 {@code null} 表示空（见 {@code docs/PORT_PLAN.md} 的裁决）。
     */
    private ItemStack currentItem = null;
    private int usingTick = 0;
    private boolean using = false;

    public void setCurrentItem(ItemStack currentItem) {
        this.currentItem = currentItem;
    }

    /**
     * @return 当前物品堆叠数；{@code null} 或空物品返回 0（上游 {@code null.getCount()} 返回 0，语义一致）
     */
    public int getStackCount() {
        // TODO(port): 上游 ThrowableAnimationStateContext.java:15 用 currentItem.getCount()；
        // 1.7.10 的字段是 stackSize，且没有 EMPTY 哨兵，因此要先判空。
        return currentItem == null || currentItem.getItem() == null ? 0 : currentItem.stackSize;
    }

    public int getUsingTick() {
        return usingTick;
    }

    public void setUsingTick(int throwTime) {
        this.usingTick = throwTime;
    }

    public boolean isUsing() {
        return using;
    }

    public void setUsing(boolean using) {
        this.using = using;
    }
}
