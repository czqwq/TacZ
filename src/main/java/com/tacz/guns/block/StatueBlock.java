package com.tacz.guns.block;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.StatueBlockEntity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 雕像方块：1x2x1，下方（LOWER）持有一个方块实体用于展示枪械模型。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/StatueBlock.java}
 *
 * <h3>1.20.1 BlockState 属性 -> 1.7.10 metadata 编码</h3>
 * 
 * <pre>
 * 属性（1.20.1）             位置      取值
 * FACING                     meta 0-1  0=NORTH 1=SOUTH 2=WEST 3=EAST
 * HALF（DoubleBlockHalf）    meta 2    0=LOWER 1=UPPER
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>只有 LOWER 有方块实体（上游 {@code newBlockEntity} 对 UPPER 返回 null，1.7.10 用
 * {@code hasTileEntity(metadata)} 表达）。</li>
 * <li>{@code getTicker(...)} 的客户端粒子 tick 由 {@code StatueBlockEntity#updateEntity()} 在客户端执行
 * （1.7.10 没有 {@code BlockEntityTicker}）。</li>
 * <li>{@code updateShape} -> {@link #onNeighborBlockChange}（拆一半另外一半跟着没，与原版 {@code BlockDoor}
 * 同一套钩子）。</li>
 * <li>{@code getDrops} 只对 LOWER 生效（上游 loot 表 {@code statue.json} 的 {@code half=lower} 条件）。</li>
 * <li>{@code onBlockExploded(BlockState, ...)} 在 1.7.10 的签名是
 * {@code onBlockExploded(World, int, int, int, Explosion)}，上游只是 super 调用，故省略。</li>
 * <li>{@code PushReaction.DESTROY} -> {@link #getMobilityFlag()}。</li>
 * </ul>
 */
public class StatueBlock extends Block {

    /** 上游 {@code HALF} 属性占用的 metadata 位。 */
    private static final int HALF_UPPER_BIT = 2;

    public StatueBlock() {
        super(Material.rock);
        this.setStepSound(Block.soundTypeStone);
        this.setHardness(2.0F);
        this.setResistance(3.0F);
        this.setBlockName("statue");
        this.setBlockTextureName("tacz:block/statue");
    }

    /**
     * 1.7.10 方块必须有 IIcon（1.20.1 走 JSON block model）：{@code Block#registerBlockIcons} 默认空实现，
     * 不注册时 {@code blockIcon} 恒为 null → TextureMap 会产出 MISSING_ICON 缺失贴图。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.blockIcon = register.registerIcon("tacz:statue");
    }

    // ------------------------------------------------------------------------------------------
    // metadata 编解码
    // ------------------------------------------------------------------------------------------

    public static ForgeDirection getFacing(int metadata) {
        return BlockMetadata.facing(metadata);
    }

    public static int withFacing(int metadata, ForgeDirection facing) {
        return BlockMetadata.withFacing(metadata, facing);
    }

    /** 上游 {@code HALF == DoubleBlockHalf.UPPER}。 */
    public static boolean isUpper(int metadata) {
        return BlockMetadata.flag(metadata, HALF_UPPER_BIT);
    }

    public static int withUpper(int metadata, boolean upper) {
        return BlockMetadata.withFlag(metadata, HALF_UPPER_BIT, upper);
    }

    // ------------------------------------------------------------------------------------------
    // 方块实体
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code newBlockEntity} 只对 LOWER 返回实体（StatueBlock.java:54-58）。 */
    @Override
    public boolean hasTileEntity(int metadata) {
        return !isUpper(metadata);
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return isUpper(metadata) ? null : new StatueBlockEntity();
    }

    // ------------------------------------------------------------------------------------------
    // 放置
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code getStateForPlacement(...)}（StatueBlock.java:87-97）+ {@code setPlacedBy(...)}（99-108）。 */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
        if (world.isRemote || placer == null) {
            return;
        }
        if (!world.getBlock(x, y + 1, z)
            .isReplaceable(world, x, y + 1, z)) {
            // 上游此情形由 getStateForPlacement 返回 null，根本不放置
            world.setBlockToAir(x, y, z);
            return;
        }
        int lowerMetadata = BlockMetadata.withFacing(
            world.getBlockMetadata(x, y, z),
            BlockMetadata.horizontalDirection(placer)
                .getOpposite());
        world.setBlockMetadataWithNotify(x, y, z, lowerMetadata, 2);
        world.setBlock(x, y + 1, z, this, withUpper(lowerMetadata, true), 3);
    }

    /** 上游 {@code updateShape(...)}（StatueBlock.java:110-124）：拆一半另外一半跟着没。 */
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote) {
            return;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        int neighbourY = y + (isUpper(metadata) ? -1 : 1);
        boolean paired = world.getBlock(x, neighbourY, z) == this
            && isUpper(world.getBlockMetadata(x, neighbourY, z)) != isUpper(metadata);
        if (!paired) {
            // 上游返回 Blocks.AIR：没有掉落
            world.setBlockToAir(x, y, z);
        }
    }

    // ------------------------------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code use(...)}（StatueBlock.java:60-85）：手持枪 -> 放上去；空手 -> 取下来。 */
    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float subX,
        float subY, float subZ) {
        if (world.isRemote) {
            return true;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        int lowerY = isUpper(metadata) ? y - 1 : y;
        TileEntity tileEntity = world.getTileEntity(x, lowerY, z);
        if (!(tileEntity instanceof StatueBlockEntity statue)) {
            return true;
        }
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() instanceof IGun) {
            statue.setGun(held);
            // 上游 stack.shrink(1) 无创造模式判断（StatueBlock.java:73-74）：这里同样无条件扣 1，
            // 扣到 0 时清空手持槽位（1.7.10 的 --stackSize 不会自动清槽）。
            --held.stackSize;
            if (held.stackSize <= 0) {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
            return true;
        }
        if (held == null) {
            statue.dropItem();
            return true;
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 掉落
    // ------------------------------------------------------------------------------------------

    /**
     * 上游 {@code onRemove(...)}（StatueBlock.java:126-135）：方块被真正移除时把展示的枪丢出来。
     *
     * <p>
     * 1.7.10 只能用 {@code breakBlock}：它在方块已换掉、但方块实体仍存在的时机被调用
     * （{@code build/rfg/minecraft-src/java/net/minecraft/world/chunk/Chunk.java:653-664}），
     * 因此 {@code getBlock(x,y,z) != this} 等价于上游 {@code !state.is(newState.getBlock())}。
     * 缺这段代码时，雕像被破坏会连枪一起永久消失。上半块没有方块实体，自然不重复掉落。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        if (world.getBlock(x, y, z) != this) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof StatueBlockEntity statue) {
                statue.dropItem();
            }
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    /** 上游 loot 表 {@code statue.json}：只有 {@code half=lower} 掉落。 */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<>();
        if (!isUpper(metadata)) {
            drops.add(new ItemStack(Item.getItemFromBlock(this)));
        }
        return drops;
    }

    // ------------------------------------------------------------------------------------------
    // 渲染 / 活塞
    // ------------------------------------------------------------------------------------------

    /** 上游 {@code RenderShape.ENTITYBLOCK_ANIMATED}（由 TESR 渲染）。 */
    @Override
    public int getRenderType() {
        return -1;
    }

    /** 上游 {@code noOcclusion()}。 */
    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /** 上游 {@code PushReaction.DESTROY}。 */
    @Override
    public int getMobilityFlag() {
        return 1;
    }
}
