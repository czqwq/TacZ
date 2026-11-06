package com.tacz.guns.block;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * 1.20.1 {@code BlockState} 属性 -> 1.7.10 {@code metadata} 的公共编解码工具。
 *
 * <p>
 * 上游每个方块用 {@code StateDefinition} + {@code Property} 保存状态；1.7.10 只有 4 bit 的
 * metadata（{@code World#getBlockMetadata}），因此本包把各属性压进位域，每个方块自己的编码表写在
 * 对应方块的类注释里。本类只提供各方块共用的部分：
 * <ul>
 * <li><b>水平朝向 2 bit</b>：编码顺序与 1.20.1 {@code Direction#get2DDataValue()} 完全一致
 * （0=SOUTH、1=WEST、2=NORTH、3=EAST，依据
 * {@code tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/core/Direction.java:28-31}
 * 枚举构造器的第 3 个参数 {@code data2d}）。这样上游任何依赖 {@code get2DDataValue()} 的算法
 * （如 {@code AbstractGunSmithTableBlock#parseRotation}）在 1.7.10 上数值不变；
 * <b>不能</b>用 {@code ForgeDirection} 的 ordinal 顺序（DOWN,UP,NORTH,SOUTH,WEST,EAST），那会让
 * NORTH/SOUTH 与 WEST/EAST 互换 ⇒ 所有方块渲染旋转错位；</li>
 * <li>1.20.1 {@code Direction#getClockWise()} / {@code getCounterClockWise()} 的 1.7.10 等价物
 * （不用 {@code ForgeDirection#getRotation}：它的旋转方向依赖 axis 参数，不直观）；</li>
 * <li>1.20.1 {@code BlockPlaceContext#getHorizontalDirection()} 的等价物（放置者水平朝向）；</li>
 * <li>{@code Block.box(x1,y1,z1,x2,y2,z2)}（1/16 单位）-> {@link AxisAlignedBB} 的换算。</li>
 * </ul>
 *
 * <p>
 * <b>注意</b>：1.7.10 的 {@link AxisAlignedBB#offset} 会<b>原地修改</b>自身，而
 * {@link AxisAlignedBB#getBoundingBox} / {@link AxisAlignedBB#copy} 返回新对象。所以形状一律现算新
 * AABB（{@link #at} 内部先 {@code copy()}），避免共享静态 AABB 被移动。
 *
 * <p>
 * 本类在 1.20.1 上游没有对应文件，是移植新增的胶水代码
 * （依据 {@code docs/PORT_PLAN.md} 3.1 节「BlockState -> Block / metadata」一行）。
 */
public final class BlockMetadata {

    /** 水平朝向占用的 bit 掩码（低 2 bit）。 */
    public static final int FACING_MASK = 0x3;

    /** 16 分之 1，{@code Block.box} 的换算因子。 */
    public static final double PIXEL = 1.0D / 16.0D;

    private BlockMetadata() {}

    /**
     * 1.20.1 {@code Direction#get2DDataValue()} 的等价物；非水平朝向退化为 SOUTH(0)。
     *
     * <p>
     * 编码取自 {@code tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/core/Direction.java:28-31}：
     * {@code NORTH(2,3,2)} / {@code SOUTH(3,2,0)} / {@code WEST(4,5,1)} / {@code EAST(5,4,3)}，
     * 构造器第 3 个参数即 {@code data2d} ⇒ <b>SOUTH=0、WEST=1、NORTH=2、EAST=3</b>。
     */
    public static int facing2D(ForgeDirection direction) {
        switch (direction) {
            case SOUTH:
                return 0;
            case WEST:
                return 1;
            case NORTH:
                return 2;
            case EAST:
                return 3;
            default:
                return 0;
        }
    }

    /**
     * 1.20.1 {@code Direction#from2DDataValue(int)} 的等价物；入参取 metadata 低 2 bit。
     *
     * <p>
     * 与 {@link #facing2D(ForgeDirection)} 互为逆表（见该方法的上游依据）。
     */
    public static ForgeDirection facing(int meta) {
        switch (meta & FACING_MASK) {
            case 0:
                return ForgeDirection.SOUTH;
            case 1:
                return ForgeDirection.WEST;
            case 2:
                return ForgeDirection.NORTH;
            case 3:
            default:
                return ForgeDirection.EAST;
        }
    }

    /** 写入水平朝向，保留高位属性。 */
    public static int withFacing(int meta, ForgeDirection direction) {
        return (meta & ~FACING_MASK) | (facing2D(direction) & FACING_MASK);
    }

    /**
     * 1.20.1 {@code BlockPlaceContext#getHorizontalDirection()} 的等价物：放置者面朝的水平方向。
     * 与 {@code BlockFurnace}/{@code BlockChest} 等原版方块使用的 yaw -> 朝向换算完全一致。
     */
    public static ForgeDirection horizontalDirection(EntityLivingBase placer) {
        int index = MathHelper.floor_double((double) (placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        switch (index) {
            case 0:
                return ForgeDirection.SOUTH;
            case 1:
                return ForgeDirection.WEST;
            case 2:
                return ForgeDirection.NORTH;
            case 3:
            default:
                return ForgeDirection.EAST;
        }
    }

    /** 1.20.1 {@code Direction#getClockWise()}（俯视顺时针 90 度）。 */
    public static ForgeDirection clockwise(ForgeDirection direction) {
        switch (direction) {
            case NORTH:
                return ForgeDirection.EAST;
            case EAST:
                return ForgeDirection.SOUTH;
            case SOUTH:
                return ForgeDirection.WEST;
            case WEST:
                return ForgeDirection.NORTH;
            default:
                return direction;
        }
    }

    /** 1.20.1 {@code Direction#getCounterClockWise()}（俯视逆时针 90 度）。 */
    public static ForgeDirection counterClockwise(ForgeDirection direction) {
        switch (direction) {
            case NORTH:
                return ForgeDirection.WEST;
            case WEST:
                return ForgeDirection.SOUTH;
            case SOUTH:
                return ForgeDirection.EAST;
            case EAST:
                return ForgeDirection.NORTH;
            default:
                return direction;
        }
    }

    /** 读第 {@code bit} 位（0 起）。 */
    public static boolean flag(int meta, int bit) {
        return (meta & (1 << bit)) != 0;
    }

    /** 写第 {@code bit} 位并返回新 metadata。 */
    public static int withFlag(int meta, int bit, boolean value) {
        return value ? (meta | (1 << bit)) : (meta & ~(1 << bit));
    }

    /** 从 {@code shift} 位起读 {@code mask} 位（{@code mask} 未左移）。 */
    public static int bits(int meta, int shift, int mask) {
        return (meta >>> shift) & mask;
    }

    /** 从 {@code shift} 位起写 {@code mask} 位（{@code mask} 未左移），返回新 metadata。 */
    public static int withBits(int meta, int shift, int mask, int value) {
        int cleared = meta & ~(mask << shift);
        return cleared | ((value & mask) << shift);
    }

    /**
     * 1.20.1 {@code Block.box(x1,y1,z1,x2,y2,z2)} 的等价物：入参是 1/16 单位，返回方块<b>局部</b>坐标
     * （0..1）的 AABB。每次调用都产生新对象。
     */
    public static AxisAlignedBB box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return AxisAlignedBB.getBoundingBox(x1 * PIXEL, y1 * PIXEL, z1 * PIXEL, x2 * PIXEL, y2 * PIXEL, z2 * PIXEL);
    }

    /** 把方块局部坐标的盒子平移到世界坐标（返回新对象，不改入参）。 */
    public static AxisAlignedBB at(AxisAlignedBB localBox, int x, int y, int z) {
        return localBox.copy()
            .offset(x, y, z);
    }

    /** 1.20.1 {@code AABB#inflate(double)} 的等价物（返回新对象）。 */
    public static AxisAlignedBB inflate(AxisAlignedBB box, double amount) {
        return box.expand(amount, amount, amount);
    }

    /** 多个局部盒子合并后的外包盒（等价于上游 {@code Shapes.or(a, b, ...)} 的 {@code bounds()}）。 */
    public static AxisAlignedBB union(AxisAlignedBB... boxes) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (AxisAlignedBB box : boxes) {
            minX = Math.min(minX, box.minX);
            minY = Math.min(minY, box.minY);
            minZ = Math.min(minZ, box.minZ);
            maxX = Math.max(maxX, box.maxX);
            maxY = Math.max(maxY, box.maxY);
            maxZ = Math.max(maxZ, box.maxZ);
        }
        return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
