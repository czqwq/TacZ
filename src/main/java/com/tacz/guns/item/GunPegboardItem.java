package com.tacz.guns.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;

/**
 * 搬运自上游 1.20.1 {@code item/GunPegboardItem.java}（extends BlockItem implements DyeableLeatherItem）。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code BlockItem} → {@code ItemBlock}，颜色不再走 {@code DyeableLeatherItem} 接口，
 * 直接读/写 1.7.10 的 {@code display.color} NBT（与 1.20.1 的 DyeableLeatherItem NBT 结构一致，
 * 保持存档格式兼容）；</li>
 * <li>上游 {@code place(BlockPlaceContext)} 的「未染色方块自动继承相邻已染色方块颜色」逻辑搬到
 * {@link #placeBlockAt} 里（1.7.10 放置完成回调），相邻方块坐标由放置坐标 + 点击面反推；</li>
 * <li>1.7.10 无法在炼药锅里给物品染色（1.8+ 才支持），上游 `CommonRegistry` 的炼药锅交互无对应，
 * 已在 PORT_STATUS 记录差异（颜色仍可通过 NBT / 相邻继承获得）。</li>
 * </ul>
 */
public class GunPegboardItem extends ItemBlock {

    private static final String DISPLAY_TAG = "display";
    private static final String COLOR_TAG = "color";

    public GunPegboardItem(Block block) {
        super(block);
        this.setMaxStackSize(1);
    }

    public boolean hasCustomColor(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(DISPLAY_TAG, 10)) {
            return false;
        }
        NBTTagCompound display = tag.getCompoundTag(DISPLAY_TAG);
        return display.hasKey(COLOR_TAG, 99);
    }

    public int getColor(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(DISPLAY_TAG, 10)) {
            return 0xFFFFFF;
        }
        NBTTagCompound display = tag.getCompoundTag(DISPLAY_TAG);
        return display.hasKey(COLOR_TAG, 99) ? display.getInteger(COLOR_TAG) : 0xFFFFFF;
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        int color = hasCustomColor(stack) ? getColor(stack) : -1;
        boolean inheritedColor = false;
        if (color < 0 && player != null && !player.isSneaking()) {
            // 1.7.10 没有 BlockPlaceContext，点击的相邻方块坐标 = 放置坐标向点击面反方向偏移一格
            ForgeDirection direction = ForgeDirection.getOrientation(side)
                .getOpposite();
            int targetX = x + direction.offsetX;
            int targetY = y + direction.offsetY;
            int targetZ = z + direction.offsetZ;
            if (world.getBlock(targetX, targetY, targetZ) instanceof GunPegboardBlock) {
                TileEntity tileEntity = world.getTileEntity(targetX, targetY, targetZ);
                if (tileEntity instanceof GunPegboardBlockEntity target) {
                    color = target.getColor();
                    inheritedColor = color >= 0;
                }
            }
        }
        boolean placed = super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
        if (placed) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof GunPegboardBlockEntity pegboard
                && world.getBlock(x, y, z) == this.field_150939_a) {
                pegboard.setColor(color, inheritedColor);
            }
        }
        return placed;
    }

    @Override
    public int getColorFromItemStack(ItemStack stack, int renderPass) {
        return hasCustomColor(stack) ? getColor(stack) : 0xFFFFFF;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        // 1.7.10 1.20.1 的 Component.translatable 换成 StatCollector；lang key 与上游完全一致
        return EnumChatFormatting.RESET + StatCollector.translateToLocal(this.getUnlocalizedName(stack) + ".name")
            .trim();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.tacz.gun_pegboard.dye"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tooltip.tacz.gun_pegboard.link_color"));
    }
}
