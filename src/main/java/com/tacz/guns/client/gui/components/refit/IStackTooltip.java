package com.tacz.guns.client.gui.components.refit;

import java.util.function.Consumer;

import net.minecraft.item.ItemStack;

/**
 * 1.7.10 的 {@link ItemStack} 用 {@code null} 表示空栈（没有 {@code ItemStack.EMPTY}），
 * 实现方需要自行判空。
 */
public interface IStackTooltip {

    /**
     * 添加此接口，会调用此渲染文本提示
     *
     * @param consumer 需要渲染文本提示的物品
     */
    void renderTooltip(Consumer<ItemStack> consumer);
}
