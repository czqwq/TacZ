package com.tacz.guns.api.item.nbt;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.util.ResourceLocationHelper;

public interface BlockItemDataAccessor extends IBlock {

    String BLOCK_ID = "BlockId";

    @Override
    @Nonnull
    default ResourceLocation getBlockId(ItemStack block) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(block);
        if (nbt.hasKey(BLOCK_ID, Constants.NBT.TAG_STRING)) {
            ResourceLocation gunId = ResourceLocationHelper.tryParse(nbt.getString(BLOCK_ID));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_BLOCK_ID);
        }
        return DefaultAssets.EMPTY_BLOCK_ID;
    }

    @Override
    default void setBlockId(ItemStack block, @Nullable ResourceLocation blockId) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(block);
        if (blockId != null) {
            nbt.setString(BLOCK_ID, blockId.toString());
            return;
        }
        nbt.setString(BLOCK_ID, DefaultAssets.EMPTY_BLOCK_ID.toString());
    }

}
