package com.tacz.guns.block;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;

/**
 * 展示柜的槽位命中判定与槽位 AABB。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/GunDisplayCaseSlotHelper.java}
 *
 * <p>
 * 1.7.10 差异：{@code BlockState} -> metadata（编码见 {@link GunDisplayCaseBlock}），
 * {@code BlockPos} -> 三个 int，{@code AABB} -> {@link AxisAlignedBB}（{@code inflate} -> {@code expand}，
 * 且 {@code offset} 会原地修改，所以这里一律先现算再平移）。几何数值与上游逐位一致。
 */
public final class GunDisplayCaseSlotHelper {

    /** 背板厚度（3/16），与上游同名常量一致。 */
    private static final double BACK_PANEL_THICKNESS = 3.0 / 16.0;
    private static final double LINE_OFFSET = 0.004;
    private static final double LINE_DEPTH = 0.0125;

    private GunDisplayCaseSlotHelper() {}

    /** 上游 {@code getSlot(BlockState, BlockPos, Vec3)}：命中点 -> 槽位号，-1 表示不在展示面上。 */
    public static int getSlot(int metadata, int x, int y, int z, Vec3 hitLocation) {
        return getSlotFromRelative(metadata, hitLocation.xCoord - x, hitLocation.yCoord - y, hitLocation.zCoord - z);
    }

    /** 上游 {@code getSlot} 的“方块内相对坐标”版本（0..1），供 {@code onBlockActivated} 直接用。 */
    public static int getSlotFromRelative(int metadata, double relX, double relY, double relZ) {
        ForgeDirection facing = GunDisplayCaseBlock.getFacing(metadata);
        double u;
        switch (facing) {
            case SOUTH:
                u = 1.0 - relX;
                break;
            case EAST:
                u = relZ;
                break;
            case WEST:
                u = 1.0 - relZ;
                break;
            default:
                u = relX;
                break;
        }
        if (u < 0 || u >= 1 || relY < 0 || relY >= 1) {
            return -1;
        }
        int grid = GunDisplayCaseBlockEntity.GRID_SIZE;
        int col = Math.min((int) (u * grid), grid - 1);
        int row = Math.min((int) (relY * grid), grid - 1);
        return row * grid + col;
    }

    /** 上游 {@code getSlotAabb(BlockState, BlockPos, int)}：槽位在世界坐标下的 AABB。 */
    public static AxisAlignedBB getSlotAabb(int metadata, int x, int y, int z, int slot) {
        int grid = GunDisplayCaseBlockEntity.GRID_SIZE;
        int row = slot / grid;
        int col = slot % grid;
        double minU = col / (double) grid;
        double maxU = (col + 1) / (double) grid;
        double minY = row / (double) grid;
        double maxY = (row + 1) / (double) grid;
        ForgeDirection facing = GunDisplayCaseBlock.getFacing(metadata);
        double x0;
        double x1;
        double z0;
        double z1;
        switch (facing) {
            case SOUTH:
                x0 = 1.0 - maxU;
                x1 = 1.0 - minU;
                z0 = BACK_PANEL_THICKNESS + LINE_OFFSET;
                z1 = BACK_PANEL_THICKNESS + LINE_OFFSET + LINE_DEPTH;
                break;
            case EAST:
                x0 = BACK_PANEL_THICKNESS + LINE_OFFSET;
                x1 = BACK_PANEL_THICKNESS + LINE_OFFSET + LINE_DEPTH;
                z0 = minU;
                z1 = maxU;
                break;
            case WEST:
                x0 = 1.0 - BACK_PANEL_THICKNESS - LINE_OFFSET - LINE_DEPTH;
                x1 = 1.0 - BACK_PANEL_THICKNESS - LINE_OFFSET;
                z0 = 1.0 - maxU;
                z1 = 1.0 - minU;
                break;
            default:
                x0 = minU;
                x1 = maxU;
                z0 = 1.0 - BACK_PANEL_THICKNESS - LINE_OFFSET - LINE_DEPTH;
                z1 = 1.0 - BACK_PANEL_THICKNESS - LINE_OFFSET;
                break;
        }
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x + x0, y + minY, z + z0, x + x1, y + maxY, z + z1);
        // 上游 new AABB(...).inflate(0.002)
        return box.expand(0.002D, 0.002D, 0.002D);
    }
}
