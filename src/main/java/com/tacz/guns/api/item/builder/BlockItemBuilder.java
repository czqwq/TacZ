package com.tacz.guns.api.item.builder;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IBlock;

/**
 * 方块物品构建器。
 * <p>
 * 1.7.10 移植说明：1.20.1 的 {@code ItemLike} 是 Block/Item 的公共父接口，1.7.10 没有这个概念
 * （Block 与 Item 完全分离，方块物品是 {@code ItemBlock}）。因此这里提供两个入口：
 * {@link #create(Item)} 用于已有的方块物品 Item，{@link #create(Block)} 用于直接传方块
 * （内部经 {@link Item#getItemFromBlock(Block)} 转成 ItemBlock）。
 */
public final class BlockItemBuilder {

    private int count = 1;
    private ResourceLocation blockId = DefaultAssets.DEFAULT_BLOCK_ID;
    private final Item blockItem;

    private BlockItemBuilder(Item blockItem) {
        this.blockItem = blockItem;
    }

    public static BlockItemBuilder create(Item blockItem) {
        return new BlockItemBuilder(blockItem);
    }

    public static BlockItemBuilder create(Block block) {
        return new BlockItemBuilder(Item.getItemFromBlock(block));
    }

    public BlockItemBuilder setCount(int count) {
        this.count = Math.max(count, 1);
        return this;
    }

    public BlockItemBuilder setId(ResourceLocation id) {
        this.blockId = id;
        return this;
    }

    public ItemStack build() {
        ItemStack block = new ItemStack(blockItem, this.count);
        if (block.getItem() instanceof IBlock iBlock) {
            iBlock.setBlockId(block, this.blockId);
        }
        return block;
    }
}
