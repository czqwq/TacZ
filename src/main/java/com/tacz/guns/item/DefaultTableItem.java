package com.tacz.guns.item;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.tacz.guns.GunMod;

/**
 * 搬运自上游 1.20.1 {@code item/DefaultTableItem.java}。
 *
 * <p>
 * 1.7.10 适配：上游 {@code BlockItem} → 1.7.10 {@code ItemBlock}（见父类 {@link GunSmithTableItem}）。
 */
public class DefaultTableItem extends GunSmithTableItem {

    public static final ResourceLocation ID = new ResourceLocation(GunMod.MOD_ID, "gun_smith_table");

    public DefaultTableItem(Block block) {
        super(block);
    }

    @Override
    @NotNull
    public ResourceLocation getBlockId(ItemStack block) {
        return ID;
    }

    @Override
    public void setBlockId(ItemStack block, @Nullable ResourceLocation blockId) {
        // 默认块的 id 无效，锁定为 "tacz:gun_smith_table"
    }
}
