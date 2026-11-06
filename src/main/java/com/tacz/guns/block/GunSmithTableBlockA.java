package com.tacz.guns.block;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

/**
 * 单方块的枪械工作台。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/GunSmithTableBlockA.java}
 *
 * <h3>1.20.1 BlockState -> 1.7.10 metadata</h3>
 * 
 * <pre>
 * FACING  meta 0-1  0=NORTH 1=SOUTH 2=WEST 3=EAST（见 AbstractGunSmithTableBlock）
 * </pre>
 *
 * <p>
 * 1.7.10 差异：上游 {@code getStateForPlacement(BlockPlaceContext)} 在放置阶段返回带朝向的
 * BlockState；1.7.10 没有放置上下文，改为在 {@link #onBlockPlacedBy} 里用放置者朝向补写 metadata
 * （与 {@code BlockFurnace} 等原版方块一致）。
 */
public class GunSmithTableBlockA extends AbstractGunSmithTableBlock {

    public GunSmithTableBlockA() {
        super();
        // 与上游注册名一致；init/ModBlocks 可用 setBlockName 覆盖
        this.setBlockName("workbench_a");
    }

    /** 上游 {@code getStateForPlacement(...)}（GunSmithTableBlockA.java:13-16）。 */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (placer != null && !world.isRemote) {
            int metadata = BlockMetadata.withFacing(
                world.getBlockMetadata(x, y, z),
                BlockMetadata.horizontalDirection(placer)
                    .getOpposite());
            world.setBlockMetadataWithNotify(x, y, z, metadata, 2);
        }
        super.onBlockPlacedBy(world, x, y, z, placer, stack);
    }

    /** 上游 {@code isRoot(BlockState)}：单方块永远是自己（GunSmithTableBlockA.java:19-21）。 */
    @Override
    public boolean isRoot(int metadata) {
        return true;
    }

    /** 上游 {@code getRootPos(...)}：单方块（GunSmithTableBlockA.java:24-26）。 */
    @Override
    public ChunkCoordinates getRootPos(int x, int y, int z, int metadata) {
        return new ChunkCoordinates(x, y, z);
    }
}
