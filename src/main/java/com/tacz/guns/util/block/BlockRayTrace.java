package com.tacz.guns.util.block;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.BlockPane;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.tacz.guns.config.util.AmmoBlockConfigRead;
import com.tacz.guns.mcshim.Mth;

/**
 * 带「可穿透方块」过滤的方块射线检测。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>上游签名 {@code rayTraceBlocks(Level, ClipContext)} 依赖 1.20 的
 * {@code ClipContext / BlockHitResult / VoxelShape / BlockState / FluidState}，
 * 1.7.10 <b>都没有</b>。因此这里保留「算法」而重写「接口」：
 * 
 * <pre>
 * rayTraceBlocks(World world, Vec3 start, Vec3 end)
 * </pre>
 * 
 * 返回 1.7.10 的 {@link MovingObjectPosition}（无命中返回 {@code null}，
 * {@code typeOfHit == BLOCK} 时 {@code blockX/blockY/blockZ/sideHit/hitVec} 全部有效）。</li>
 * <li>上游的 <b>DDA 逐格步进算法原样保留</b>（与 1.20 {@code BlockGetter#traverseBlocks} 逐行同构，
 * 注意源码里 {@code startX/endX} 的命名与几何方向是反的，这是上游原样，未改)。</li>
 * <li><b>穿透判定</b>：上游 {@code AmmoBlockConfigRead.canPassThrough(BlockState)} →
 * 1.7.10 {@link AmmoBlockConfigRead#canPassThrough(Block, int)}（另有无 meta 重载）。</li>
 * <li><b>子弹忽略方块</b>：上游是方块标签 {@code tacz:bullet_ignore}
 * （{@code BlockState#is(ModBlocks.BULLET_IGNORE_BLOCKS)}，内容见
 * {@code src/main/resources/data/tacz/tags/blocks/bullet_ignore.json}：
 * {@code iron_bars} / {@code #minecraft:fences} / {@code #minecraft:fence_gates} / {@code #minecraft:leaves}）。
 * 1.7.10 <b>没有 tag 系统</b>，因此分两层：
 * ① 标签里的原版方块由 {@link #isVanillaIgnoredBlock(Block)} 按类型直接判定；
 * ② 其它模组方块走 {@link #registerIgnoreBlock(Block)} 填充的静态集合。</li>
 * <li><b>形状</b>：上游用 {@code ClipContext#getBlockShape}（COLLIDER）与
 * {@code getFluidShape}；1.7.10 的等价物是
 * {@link Block#getCollisionBoundingBoxFromPool(World, int, int, int)}（返回 {@code null} 表示无碰撞箱）
 * 与 {@link AxisAlignedBB#calculateIntercept(Vec3, Vec3)}。
 * 上游调用方凡是用 {@code ClipContext.Fluid.NONE} 的，1.7.10 侧自然满足（流体没有碰撞箱），
 * 因此<b>不再单独处理流体形状</b>。</li>
 * <li>{@code Mth} → {@link Mth}（mcshim）；{@code BlockPos} → 三个 int；
 * {@code BlockState} → {@link Block} + metadata。</li>
 * </ul>
 */
public final class BlockRayTrace {

    /**
     * 上游 {@code tacz:bullet_ignore} 方块标签的 1.7.10 替代（1.7.10 没有 tag 系统）。
     *
     * <p>
     * 上游默认标签内容里的原版方块已由 {@link #isVanillaIgnoredBlock(Block)} 直接覆盖，所以当前
     * <b>没有调用者</b>；本方法保留是为了对齐上游 tag 的「可扩展」语义，供 init 层/兼容层添加其它模组方块。
     */
    private static final Set<Block> IGNORE_BLOCKS = Collections.synchronizedSet(new HashSet<>());

    private BlockRayTrace() {}

    /** 注册「子弹直接穿过」的方块（1.7.10 替代 {@code tacz:bullet_ignore} 标签）。 */
    public static void registerIgnoreBlock(Block block) {
        if (block != null) {
            IGNORE_BLOCKS.add(block);
        }
    }

    /** 清空忽略表（枪包/配置重载时可用）。 */
    public static void clearIgnoreBlocks() {
        IGNORE_BLOCKS.clear();
    }

    /** 该方块是否被标记为「子弹忽略」。 */
    public static boolean isIgnoredBlock(Block block) {
        return block != null && (IGNORE_BLOCKS.contains(block) || isVanillaIgnoredBlock(block));
    }

    /**
     * 上游默认标签 {@code data/tacz/tags/blocks/bullet_ignore.json} 的内容（1.7.10 没有 tag 系统，
     * 直接按方块类型判定）：
     * 
     * <pre>
     * minecraft:iron_bars / #minecraft:fences / #minecraft:fence_gates / #minecraft:leaves
     * </pre>
     * 
     * 没有这一步时，子弹会打到栅栏/铁栏杆/树叶上（上游是穿过去的）。
     */
    private static boolean isVanillaIgnoredBlock(Block block) {
        if (block instanceof BlockFence || block instanceof BlockFenceGate) {
            return true;
        }
        // iron_bars 与 glass_pane 都是 BlockPane，靠 Material 区分（见 BlockPane#opacity 同理）
        if (block instanceof BlockPane && block.getMaterial() == Material.iron) {
            return true;
        }
        return block instanceof BlockLeavesBase;
    }

    /**
     * 等价上游 {@code rayTraceBlocks(Level, ClipContext)}（{@code ClipContext.Block.COLLIDER} +
     * {@code ClipContext.Fluid.NONE}）。
     *
     * @return 命中的方块；未命中返回 {@code null}
     */
    @Nullable
    public static MovingObjectPosition rayTraceBlocks(World level, Vec3 startVec, Vec3 endVec) {
        return performRayTrace(level, startVec, endVec, (world, x, y, z) -> {
            Block block = world.getBlock(x, y, z);
            int metadata = world.getBlockMetadata(x, y, z);
            // 这里添加判断方块是否可以穿透，如果可以穿透则返回 null
            if (AmmoBlockConfigRead.canPassThrough(block, metadata)) {
                return null;
            }
            // tag（1.7.10 退化为注册表集合）
            if (isIgnoredBlock(block)) {
                return null;
            }
            return getBlockHitResult(world, x, y, z, block, startVec, endVec);
        });
    }

    @Nullable
    private static MovingObjectPosition getBlockHitResult(World world, int x, int y, int z, Block block, Vec3 startVec,
        Vec3 endVec) {
        if (block == null || block.getMaterial() == Material.air) {
            return null;
        }
        AxisAlignedBB blockShape = block.getCollisionBoundingBoxFromPool(world, x, y, z);
        if (blockShape == null) {
            return null;
        }
        MovingObjectPosition intercept = blockShape.calculateIntercept(startVec, endVec);
        if (intercept == null) {
            return null;
        }
        return new MovingObjectPosition(x, y, z, intercept.sideHit, intercept.hitVec);
    }

    private interface HitFactory {

        @Nullable
        MovingObjectPosition hit(World world, int x, int y, int z);
    }

    @Nullable
    private static MovingObjectPosition performRayTrace(World world, Vec3 startVec, Vec3 endVec,
        HitFactory hitFunction) {
        if (world == null || startVec == null || endVec == null) {
            return null;
        }
        if (!startVec.equals(endVec)) {
            double startX = Mth.lerp(-0.0000001, endVec.xCoord, startVec.xCoord);
            double startY = Mth.lerp(-0.0000001, endVec.yCoord, startVec.yCoord);
            double startZ = Mth.lerp(-0.0000001, endVec.zCoord, startVec.zCoord);
            double endX = Mth.lerp(-0.0000001, startVec.xCoord, endVec.xCoord);
            double endY = Mth.lerp(-0.0000001, startVec.yCoord, endVec.yCoord);
            double endZ = Mth.lerp(-0.0000001, startVec.zCoord, endVec.zCoord);

            int blockX = Mth.floor(endX);
            int blockY = Mth.floor(endY);
            int blockZ = Mth.floor(endZ);

            MovingObjectPosition t = hitFunction.hit(world, blockX, blockY, blockZ);
            if (t != null) {
                return t;
            }

            double deltaX = startX - endX;
            double deltaY = startY - endY;
            double deltaZ = startZ - endZ;
            int signX = Mth.sign(deltaX);
            int signY = Mth.sign(deltaY);
            int signZ = Mth.sign(deltaZ);
            double d9 = signX == 0 ? Double.MAX_VALUE : (double) signX / deltaX;
            double d10 = signY == 0 ? Double.MAX_VALUE : (double) signY / deltaY;
            double d11 = signZ == 0 ? Double.MAX_VALUE : (double) signZ / deltaZ;
            double d12 = d9 * (signX > 0 ? 1.0D - Mth.frac(endX) : Mth.frac(endX));
            double d13 = d10 * (signY > 0 ? 1.0D - Mth.frac(endY) : Mth.frac(endY));
            double d14 = d11 * (signZ > 0 ? 1.0D - Mth.frac(endZ) : Mth.frac(endZ));

            while (d12 <= 1.0D || d13 <= 1.0D || d14 <= 1.0D) {
                if (d12 < d13) {
                    if (d12 < d14) {
                        blockX += signX;
                        d12 += d9;
                    } else {
                        blockZ += signZ;
                        d14 += d11;
                    }
                } else if (d13 < d14) {
                    blockY += signY;
                    d13 += d10;
                } else {
                    blockZ += signZ;
                    d14 += d11;
                }

                MovingObjectPosition t1 = hitFunction.hit(world, blockX, blockY, blockZ);
                if (t1 != null) {
                    return t1;
                }
            }
        }
        return null;
    }
}
