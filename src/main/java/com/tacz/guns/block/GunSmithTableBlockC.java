package com.tacz.guns.block;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

/**
 * 双方块的枪械工作台，1x2x1（lower + upper 垂直堆叠）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/GunSmithTableBlockC.java}
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
 * <li>{@code getStateForPlacement} 的“上方必须可替换”前置条件由 ItemBlock 侧预检
 * （{@link AbstractGunSmithTableBlock#isReplaceable}），{@link #onBlockPlacedBy} 里只做兜底退款。</li>
 * <li>{@code updateShape} / {@code playerWillDestroy} -> {@link #onNeighborBlockChange} /
 * {@link #onBlockHarvested}（与原版 {@code BlockDoor} 同一套 1.7.10 钩子）。</li>
 * </ul>
 */
public class GunSmithTableBlockC extends AbstractGunSmithTableBlock {

    /** 上游 {@code HALF} 属性占用的 metadata 位。 */
    private static final int HALF_UPPER_BIT = 2;

    public GunSmithTableBlockC() {
        super();
        // 与上游注册名一致
        this.setBlockName("workbench_c");
    }

    /** 上游 {@code HALF == DoubleBlockHalf.UPPER}。 */
    public static boolean isUpper(int metadata) {
        return BlockMetadata.flag(metadata, HALF_UPPER_BIT);
    }

    /** 写入 HALF 位，返回新 metadata。 */
    public static int withUpper(int metadata, boolean upper) {
        return BlockMetadata.withFlag(metadata, HALF_UPPER_BIT, upper);
    }

    /**
     * 上游 {@code getStateForPlacement(...)}（GunSmithTableBlockC.java:40-49）+ {@code setPlacedBy(...)}
     * （GunSmithTableBlockC.java:52-60）：facing = 玩家水平朝向的反方向，上方放出 upper。
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (placer == null || world.isRemote) {
            super.onBlockPlacedBy(world, x, y, z, placer, stack);
            return;
        }
        if (!isReplaceable(world, x, y + 1, z)) {
            // 上游此情形由 getStateForPlacement 返回 null，根本不放置
            refundAndRemove(world, x, y, z, placer, stack);
            return;
        }
        int lowerMetadata = BlockMetadata.withFacing(
            world.getBlockMetadata(x, y, z),
            BlockMetadata.horizontalDirection(placer)
                .getOpposite());
        world.setBlockMetadataWithNotify(x, y, z, lowerMetadata, 2);
        world.setBlock(x, y + 1, z, this, withUpper(lowerMetadata, true), 3);
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
    }

    /** 上游 {@code updateShape(...)}（GunSmithTableBlockC.java:63-76）：拆一半另外一半跟着没。 */
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
            removeWithoutDrop(world, x, y, z);
        }
    }

    /**
     * 上游 {@code playerWillDestroy(...)}（GunSmithTableBlockC.java:79-93）：
     * 创造模式下拆 upper 时，lower 静默消失。
     */
    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int metadata, EntityPlayer player) {
        super.onBlockHarvested(world, x, y, z, metadata, player);
        if (player != null && player.capabilities.isCreativeMode && !world.isRemote && isUpper(metadata)) {
            if (world.getBlock(x, y - 1, z) == this && !isUpper(world.getBlockMetadata(x, y - 1, z))) {
                removeWithoutDrop(world, x, y - 1, z);
            }
        }
    }

    /** 上游 {@code isRoot(BlockState)}：lower 是主方块（GunSmithTableBlockC.java:96-98）。 */
    @Override
    public boolean isRoot(int metadata) {
        return !isUpper(metadata);
    }

    /** 上游 {@code getRootPos(...)}（GunSmithTableBlockC.java:101-103）。 */
    @Override
    public ChunkCoordinates getRootPos(int x, int y, int z, int metadata) {
        return isUpper(metadata) ? new ChunkCoordinates(x, y - 1, z) : new ChunkCoordinates(x, y, z);
    }
}
