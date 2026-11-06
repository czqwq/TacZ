package com.tacz.guns.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.tacz.guns.init.ModBlocks;

/**
 * 搬运自上游 1.20.1 {@code item/AmmoStorageBlockItem.java}（extends BlockItem）。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code BlockItem} → {@link ItemBlock}；{@code getBlock()} → {@link #field_150939_a}；</li>
 * <li>上游用 1.20 的 {@code BlockItem#getBlockEntityData(stack)}（NBT "BlockEntityTag"）在物品与方块实体之间转移
 * 弹药库存。1.7.10 没有内置机制，因此这里在 {@link #placeBlockAt} 里手动把 "BlockEntityTag" 写回 TileEntity；
 * 破坏方块时的反向写入由 {@code block/AmmoStorageBlock} 负责（契约见 docs/PORT_GAME.md）；</li>
 * <li>{@code appendHoverText} → {@link #addInformation}；{@code getName} → {@link #getItemStackDisplayName}；</li>
 * <li>{@code getTooltipImage}（1.20 的 tooltip 组件）在 1.7.10 无对应，已删除并记录差异
 * （弹药内容预览 tooltip 属 L2 客户端 GUI/tooltip 范畴）。</li>
 * </ul>
 */
public class AmmoStorageBlockItem extends ItemBlock {

    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

    public AmmoStorageBlockItem(Block block) {
        super(block);
        this.setMaxStackSize(1);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String name = super.getItemStackDisplayName(stack);
        if (this.field_150939_a == ModBlocks.AMMO_STORAGE_GOLD) {
            return EnumChatFormatting.YELLOW + name;
        }
        if (this.field_150939_a == ModBlocks.AMMO_STORAGE_DIAMOND) {
            return EnumChatFormatting.AQUA + name;
        }
        return name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.tacz.ammo_storage.description"));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.tacz.ammo_storage.description.extra"));
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        boolean placed = super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
        if (placed) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            NBTTagCompound blockEntityTag = getBlockEntityTag(stack);
            if (tileEntity != null && blockEntityTag != null) {
                // 1.7.10：TileEntity#readFromNBT 只应读取方块实体自身数据，不能带入外层 tag
                NBTTagCompound copy = (NBTTagCompound) blockEntityTag.copy();
                tileEntity.readFromNBT(copy);
                tileEntity.xCoord = x;
                tileEntity.yCoord = y;
                tileEntity.zCoord = z;
                tileEntity.markDirty();
                world.markBlockForUpdate(x, y, z);
            }
        }
        return placed;
    }

    @Nullable
    private static NBTTagCompound getBlockEntityTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(BLOCK_ENTITY_TAG, 10)) {
            return null;
        }
        return tag.getCompoundTag(BLOCK_ENTITY_TAG);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.onUpdate(stack, world, entity, slot, selected);
    }
}
