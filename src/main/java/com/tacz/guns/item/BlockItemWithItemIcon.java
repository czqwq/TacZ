package com.tacz.guns.item;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 1.7.10 方块物品用「物品图标」而不是方块图集图标。
 *
 * <p>
 * 上游这几件是 {@code item/generated} + {@code tacz:item/<x>} 的 2D 图标
 * （{@code models/item/target.json} / {@code models/item/statue.json}），1.7.10 的
 * {@link ItemBlock} 默认取方块图集里的 64×64 UV 图集 → 创造栏/背包里显示成雪花碎块。
 * 这里把 sprite 切到 {@code assets/tacz/textures/items/<x>.png}，并让
 * {@code getSpriteNumber()} 返回 1，渲染回到 {@code RenderItem} 的 2D 分支
 * （{@code build/rfg/minecraft-src/.../RenderItem.java:426} 要求 spriteNumber==0 才走方块 3D 分支）。
 */
public class BlockItemWithItemIcon extends ItemBlock {

    private final String iconName;

    protected BlockItemWithItemIcon(Block block, String iconName) {
        super(block);
        this.iconName = iconName;
    }

    /** 物品图集在 {@code textures/items/} 下，故必须返回 1。 */
    @Override
    @SideOnly(Side.CLIENT)
    public int getSpriteNumber() {
        return 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.itemIcon = register.registerIcon(this.iconName);
    }

    /** {@link ItemBlock} 默认返回方块图标，这里改回物品图标。 */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int metadata) {
        return this.itemIcon;
    }

    /**
     * 上游 {@code getStateForPlacement(...)} 要求「上方那一格可替换」，否则返回 null ⇒ 放置直接取消、
     * 物品不消耗（{@code TargetBlock.java:169-179}、{@code StatueBlock.java:87-97}）。
     *
     * <p>
     * 1.7.10 的等价落点是这里：{@code ItemBlock#onItemUse} 用 {@code placeBlockAt} 的返回值决定
     * 是否扣物品（{@code build/rfg/minecraft-src/java/net/minecraft/item/ItemBlock.java:116-119}），
     * 而本方法拿到的坐标已经是「最终放置坐标」。返回 false ⇒ 方块不放、物品不扣，
     * {@code TargetBlock}/{@code StatueBlock} 里 {@code onBlockPlacedBy} 的兜底分支因此不再触发。
     */
    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        if (!world.getBlock(x, y + 1, z)
            .isReplaceable(world, x, y + 1, z)) {
            return false;
        }
        return super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata);
    }

    /** 标靶方块物品（上游 {@code models/item/target.json} → {@code tacz:item/target}）。 */
    public static class TargetBlockItem extends BlockItemWithItemIcon {

        public TargetBlockItem(Block block) {
            super(block, "tacz:target");
        }
    }

    /** 雕像方块物品（上游 {@code models/item/statue.json} → {@code tacz:item/statue}）。 */
    public static class StatueBlockItem extends BlockItemWithItemIcon {

        public StatueBlockItem(Block block) {
            super(block, "tacz:statue");
        }
    }
}
