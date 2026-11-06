package com.tacz.guns.item;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRail;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRailPowered;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.tacz.guns.entity.TargetMinecart;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 搬运自上游 1.20.1 {@code item/TargetMinecartItem.java}。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code useOn(UseOnContext)} → {@link Item#onItemUse}（1.7.10 的放置钩子，携带
 * x/y/z/side/hit 坐标）；</li>
 * <li>轨道判定：上游用 {@code BlockTags.RAILS} 标签 → 1.7.10 用 {@code block instanceof BlockRailBase}
 * （1.7.10 所有铁轨都继承该类）；</li>
 * <li>上升轨道的高度修正：上游用 {@code RailShape.isAscending()}；1.7.10 无 RailShape 枚举，
 * 普通铁轨/充能铁轨 metadata 2~5 表示 ascend 形状（见 {@code BlockRail#onBlockPlacedBy}），
 * 据此补偿 0.5 高度；</li>
 * <li>{@code ItemStack#shrink(1)} → {@code --stack.stackSize}；{@code Level#addFreshEntity} →
 * {@code World#spawnEntityInWorld}；自定义名称（上游 {@code setCustomName}）→ 1.7.10
 * {@code EntityMinecart#setMinecartName}（同一个 {@code CustomName} 存档键）。</li>
 * </ul>
 */
public class TargetMinecartItem extends Item {

    public TargetMinecartItem() {
        this.setMaxStackSize(1);
    }

    /**
     * 1.7.10 必须有 {@link net.minecraft.util.IIcon}：上游 1.20.1 的 target_minecart 模型引用
     * {@code tacz:item/target_minecart}，assets-port 已复制到 {@code assets/tacz/textures/items/target_minecart.png}。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.itemIcon = register.registerIcon("tacz:target_minecart");
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        Block block = world.getBlock(x, y, z);
        if (!(block instanceof BlockRailBase)) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        double yOffset = 0;
        // 1.7.10 普通铁轨 metadata 2~5 为 ascend 形状；充能铁轨低 3 位同理（高位可能是 powered 标记）
        if (block instanceof BlockRail) {
            int shape = metadata & 7;
            if (shape >= 2 && shape <= 5) {
                yOffset = 0.5;
            }
        } else if (block instanceof BlockRailPowered) {
            int shape = metadata & 7;
            if (shape >= 2 && shape <= 5) {
                yOffset = 0.5;
            }
        }
        TargetMinecart minecart = new TargetMinecart(
            world,
            (double) x + 0.5D,
            (double) y + 0.0625D + yOffset,
            (double) z + 0.5D);
        // 上游 targetMinecart.setCustomName(itemstack.getHoverName())（TargetMinecartItem.java:39-41）：1.7.10 的
        // 等价物是 EntityMinecart#setMinecartName —— 存档键同为 "CustomName"
        // （build/rfg/minecraft-src/java/net/minecraft/entity/item/EntityMinecart.java:875,894-896），
        // 也用于矿车被破坏时的掉落物显示名（同文件:223-225）与容器标题。
        if (stack.hasDisplayName()) {
            minecart.setMinecartName(stack.getDisplayName());
        }
        world.spawnEntityInWorld(minecart);
        --stack.stackSize;
        return true;
    }
}
