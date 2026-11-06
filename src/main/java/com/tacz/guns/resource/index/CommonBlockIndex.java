package com.tacz.guns.resource.index;

import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;

import com.google.common.base.Preconditions;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.pojo.BlockIndexPOJO;
import com.tacz.guns.resource.pojo.data.block.BlockData;

import cpw.mods.fml.common.registry.GameRegistry;

public class CommonBlockIndex {

    private BlockIndexPOJO pojo;
    private ItemBlock block;
    private BlockData data;
    private RecipeFilter filter;

    public static CommonBlockIndex getInstance(BlockIndexPOJO gunIndexPOJO) throws IllegalArgumentException {
        CommonBlockIndex index = new CommonBlockIndex();
        index.pojo = gunIndexPOJO;
        checkIndex(gunIndexPOJO, index);
        checkData(gunIndexPOJO, index);
        return index;
    }

    private static void checkIndex(BlockIndexPOJO block, CommonBlockIndex index) {
        ResourceLocation id = index.pojo.getId();
        Preconditions.checkArgument(block != null, "index object file is empty");
        // 1.7.10 差异：上游用 ForgeRegistries.ITEMS.getValue(id) 查注册表，
        // 1.7.10 的对应物是 GameRegistry.findItem(domain, path)（无注册表对象）。
        Item item = GameRegistry.findItem(id.getResourceDomain(), id.getResourcePath());
        if (!(item instanceof ItemBlock itemBlock)) {
            throw new IllegalArgumentException("ItemBlock not found for " + block.getName());
        }
        index.block = itemBlock;
    }

    private static void checkData(BlockIndexPOJO block, CommonBlockIndex index) {
        ResourceLocation pojoData = block.getData();
        Preconditions.checkArgument(pojoData != null, "index object missing pojoData field");
        BlockData data = CommonAssetsManager.get()
            .getBlockData(pojoData);
        Preconditions.checkArgument(data != null, "there is no corresponding data file");
        RecipeFilter recipeFilter = CommonAssetsManager.get()
            .getRecipeFilter(data.getFilter());
        Preconditions.checkArgument(recipeFilter != null, "there is no corresponding data file");
        index.data = data;
        index.filter = recipeFilter;
    }

    public BlockIndexPOJO getPojo() {
        return pojo;
    }

    public ItemBlock getBlock() {
        return block;
    }

    public BlockData getData() {
        return data;
    }

    public RecipeFilter getFilter() {
        return filter;
    }
}
