package com.tacz.guns.block;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.block.entity.GunPegboardBlockEntity;

/**
 * 枪架的槽位命中判定与槽位 AABB（折面时第二面槽位从 {@code SLOTS_PER_FACE} 起编号）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/GunPegboardSlotHelper.java}
 *
 * <p>
 * 1.7.10 差异：{@code BlockState} -> metadata（编码见 {@link GunPegboardBlock}），
 * {@code BlockPos} -> 三个 int，{@code AABB} -> {@link AxisAlignedBB}。几何数值与上游逐位一致。
 */
public final class GunPegboardSlotHelper {

    /** 板厚（3/16），与上游同名常量一致。 */
    private static final double PLATE_THICKNESS = 3.0 / 16.0;
    private static final double LINE_OFFSET = 0.004;
    private static final double LINE_DEPTH = 0.0125;

    private GunPegboardSlotHelper() {}

    /** 上游 {@code hasCorner(BlockState)}。 */
    public static boolean hasCorner(int metadata) {
        return GunPegboardBlock.getShape(metadata) != PegboardShape.STRAIGHT;
    }

    /** 上游 {@code getSlotFacing(BlockState, int)}。 */
    public static ForgeDirection getSlotFacing(int metadata, int slot) {
        ForgeDirection facing = GunPegboardBlock.getFacing(metadata);
        if (slot < GunPegboardBlockEntity.SLOTS_PER_FACE || !hasCorner(metadata)) {
            return facing;
        }
        return GunPegboardBlock.getShape(metadata)
            .getCornerFacing(facing);
    }

    /** 上游 {@code isSlotActive(BlockState, int)}。 */
    public static boolean isSlotActive(int metadata, int slot) {
        if (slot < 0 || slot >= GunPegboardBlockEntity.SLOT_COUNT) {
            return false;
        }
        boolean secondFace = slot >= GunPegboardBlockEntity.SLOTS_PER_FACE;
        if (!hasCorner(metadata)) {
            return !secondFace;
        }
        int column = slot % GunPegboardBlockEntity.GRID_SIZE;
        boolean left = GunPegboardBlock.getShape(metadata) == PegboardShape.CORNER_LEFT;
        int blockedColumn = left == secondFace ? 0 : GunPegboardBlockEntity.GRID_SIZE - 1;
        return column != blockedColumn;
    }

    /** 上游 {@code getSlot(BlockState, BlockPos, Vec3)}：命中点 -> 槽位号，-1 表示没命中有效槽位。 */
    public static int getSlot(int metadata, int x, int y, int z, Vec3 hitLocation) {
        return getSlotFromRelative(metadata, hitLocation.xCoord - x, hitLocation.yCoord - y, hitLocation.zCoord - z);
    }

    /** 上游 {@code getSlot} 的“方块内相对坐标”版本（0..1）。 */
    public static int getSlotFromRelative(int metadata, double relX, double relY, double relZ) {
        ForgeDirection facing = GunPegboardBlock.getFacing(metadata);
        int faceOffset = 0;
        if (hasCorner(metadata)) {
            if (!onDisplayFace(facing, relX, relZ)) {
                facing = getSlotFacing(metadata, GunPegboardBlockEntity.SLOTS_PER_FACE);
                if (!onDisplayFace(facing, relX, relZ)) {
                    return -1;
                }
                faceOffset = GunPegboardBlockEntity.SLOTS_PER_FACE;
            }
        }
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
        int grid = GunPegboardBlockEntity.GRID_SIZE;
        int col = Math.min((int) (u * grid), grid - 1);
        int row = Math.min((int) (relY * grid), grid - 1);
        int slot = faceOffset + row * grid + col;
        return isSlotActive(metadata, slot) ? slot : -1;
    }

    /** 上游 {@code onDisplayFace(Direction, double, double)}。 */
    private static boolean onDisplayFace(ForgeDirection facing, double relX, double relZ) {
        double depth;
        switch (facing) {
            case SOUTH:
                depth = relZ;
                break;
            case EAST:
                depth = relX;
                break;
            case WEST:
                depth = 1.0 - relX;
                break;
            default:
                depth = 1.0 - relZ;
                break;
        }
        return Math.abs(depth - PLATE_THICKNESS) < 0.001;
    }

    /** 上游 {@code getSlotAabb(BlockState, BlockPos, int)}。 */
    public static AxisAlignedBB getSlotAabb(int metadata, int x, int y, int z, int slot) {
        int grid = GunPegboardBlockEntity.GRID_SIZE;
        int row = (slot % GunPegboardBlockEntity.SLOTS_PER_FACE) / grid;
        int col = slot % grid;
        double minU = col / (double) grid;
        double maxU = (col + 1) / (double) grid;
        double minY = row / (double) grid;
        double maxY = (row + 1) / (double) grid;
        ForgeDirection facing = getSlotFacing(metadata, slot);
        double x0;
        double x1;
        double z0;
        double z1;
        switch (facing) {
            case SOUTH:
                x0 = 1.0 - maxU;
                x1 = 1.0 - minU;
                z0 = PLATE_THICKNESS + LINE_OFFSET;
                z1 = PLATE_THICKNESS + LINE_OFFSET + LINE_DEPTH;
                break;
            case EAST:
                x0 = PLATE_THICKNESS + LINE_OFFSET;
                x1 = PLATE_THICKNESS + LINE_OFFSET + LINE_DEPTH;
                z0 = minU;
                z1 = maxU;
                break;
            case WEST:
                x0 = 1.0 - PLATE_THICKNESS - LINE_OFFSET - LINE_DEPTH;
                x1 = 1.0 - PLATE_THICKNESS - LINE_OFFSET;
                z0 = 1.0 - maxU;
                z1 = 1.0 - minU;
                break;
            default:
                x0 = minU;
                x1 = maxU;
                z0 = 1.0 - PLATE_THICKNESS - LINE_OFFSET - LINE_DEPTH;
                z1 = 1.0 - PLATE_THICKNESS - LINE_OFFSET;
                break;
        }
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x + x0, y + minY, z + z0, x + x1, y + maxY, z + z1);
        return box.expand(0.002D, 0.002D, 0.002D);
    }
}
