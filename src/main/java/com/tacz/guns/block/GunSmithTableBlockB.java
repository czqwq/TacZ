package com.tacz.guns.block;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 双方块的枪械工作台，2x1x1（foot 朝 facing 方向长出 head）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/GunSmithTableBlockB.java}
 *
 * <h3>1.20.1 BlockState 属性 -> 1.7.10 metadata 编码</h3>
 * 
 * <pre>
 * 属性（1.20.1）   位置      取值
 * FACING           meta 0-1  0=SOUTH 1=WEST 2=NORTH 3=EAST
 * PART（BedPart）  meta 2    0=FOOT 1=HEAD
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code getStateForPlacement} 的“第二格必须可替换”前置条件在 1.7.10 由 ItemBlock 侧预检
 * （见 {@link AbstractGunSmithTableBlock#isReplaceable}），{@link #onBlockPlacedBy} 里只做兜底退款。</li>
 * <li>{@code updateShape} / {@code playerWillDestroy} -> {@link #onNeighborBlockChange} /
 * {@link #onBlockHarvested}（与原版 {@code BlockBed} 同一套 1.7.10 钩子）。</li>
 * </ul>
 */
public class GunSmithTableBlockB extends AbstractGunSmithTableBlock {

    /** 上游 {@code PART} 属性占用的 metadata 位。 */
    private static final int PART_HEAD_BIT = 2;

    public GunSmithTableBlockB() {
        super();
        // 与上游注册名一致（另一个实例由 init/ModBlocks 改名为 workbench_b）
        this.setBlockName("gun_smith_table");
    }

    /** 上游 {@code PART == BedPart.HEAD}。 */
    public static boolean isHead(int metadata) {
        return BlockMetadata.flag(metadata, PART_HEAD_BIT);
    }

    /** 写入 PART 位，返回新 metadata。 */
    public static int withHead(int metadata, boolean head) {
        return BlockMetadata.withFlag(metadata, PART_HEAD_BIT, head);
    }

    /** 上游 {@code getNeighbourDirection(BedPart, Direction)}（GunSmithTableBlockB.java:32-34）。 */
    public static ForgeDirection getNeighbourDirection(boolean head, ForgeDirection facing) {
        return head ? facing.getOpposite() : facing;
    }

    /**
     * 上游 {@code getStateForPlacement(...)}（GunSmithTableBlockB.java:43-52）+ {@code setPlacedBy(...)}
     * （GunSmithTableBlockB.java:55-63）：facing = 玩家水平朝向俯视顺时针 90 度，并在 facing 方向放出 head。
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (placer == null || world.isRemote) {
            super.onBlockPlacedBy(world, x, y, z, placer, stack);
            return;
        }
        ForgeDirection facing = BlockMetadata.clockwise(BlockMetadata.horizontalDirection(placer));
        int headX = x + facing.offsetX;
        int headZ = z + facing.offsetZ;
        if (!isReplaceable(world, headX, y, headZ)) {
            // 上游此情形由 getStateForPlacement 返回 null，根本不放置
            refundAndRemove(world, x, y, z, placer, stack);
            return;
        }
        int footMetadata = BlockMetadata.withFacing(world.getBlockMetadata(x, y, z), facing);
        world.setBlockMetadataWithNotify(x, y, z, footMetadata, 2);
        world.setBlock(headX, y, headZ, this, withHead(footMetadata, true), 3);
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
    }

    /**
     * 上游 {@code updateShape(...)}（GunSmithTableBlockB.java:83-89）：相邻的另一半不匹配时整块消失。
     */
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote) {
            return;
        }
        int metadata = world.getBlockMetadata(x, y, z);
        ForgeDirection neighbourDirection = getNeighbourDirection(isHead(metadata), BlockMetadata.facing(metadata));
        int nx = x + neighbourDirection.offsetX;
        int nz = z + neighbourDirection.offsetZ;
        boolean paired = world.getBlock(nx, y, nz) == this
            && isHead(world.getBlockMetadata(nx, y, nz)) != isHead(metadata);
        if (!paired) {
            // 上游返回 Blocks.AIR：没有掉落
            removeWithoutDrop(world, x, y, z);
        }
    }

    /**
     * 上游 {@code playerWillDestroy(...)}（GunSmithTableBlockB.java:66-80）：
     * 创造模式下拆 foot 时，head 静默消失。
     */
    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int metadata, EntityPlayer player) {
        super.onBlockHarvested(world, x, y, z, metadata, player);
        if (player != null && player.capabilities.isCreativeMode && !world.isRemote && !isHead(metadata)) {
            ForgeDirection neighbourDirection = getNeighbourDirection(false, BlockMetadata.facing(metadata));
            int nx = x + neighbourDirection.offsetX;
            int nz = z + neighbourDirection.offsetZ;
            if (world.getBlock(nx, y, nz) == this && isHead(world.getBlockMetadata(nx, y, nz))) {
                removeWithoutDrop(world, nx, y, nz);
            }
        }
    }

    /** 上游 {@code isRoot(BlockState)}：foot 是主方块（GunSmithTableBlockB.java:91-94）。 */
    @Override
    public boolean isRoot(int metadata) {
        return !isHead(metadata);
    }

    /** 上游 {@code getRootPos(...)}（GunSmithTableBlockB.java:101-104）。 */
    @Override
    public ChunkCoordinates getRootPos(int x, int y, int z, int metadata) {
        if (!isHead(metadata)) {
            return new ChunkCoordinates(x, y, z);
        }
        ForgeDirection direction = getNeighbourDirection(true, BlockMetadata.facing(metadata));
        return new ChunkCoordinates(x + direction.offsetX, y, z + direction.offsetZ);
    }

    /** 上游 {@code parseRotation(Direction)}（GunSmithTableBlockB.java:96-99）。 */
    @Override
    public float parseRotation(ForgeDirection direction) {
        return 90 - 90 * BlockMetadata.facing2D(direction);
    }
}
