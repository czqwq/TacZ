package com.tacz.guns.api.item;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public interface IBlock {

    /**
     * 获取方块 ID
     *
     * @param block 输入物品
     * @return 方块 ID
     */
    ResourceLocation getBlockId(ItemStack block);

    /**
     * 设置方块 ID
     */
    void setBlockId(ItemStack block, @Nullable ResourceLocation blockId);
}
