package com.tacz.guns.block;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * 枪架/展示板的折面形状。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/block/PegboardShape.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>上游实现 {@code net.minecraft.util.StringRepresentable}（1.18+ 的枚举序列化接口），1.7.10 无此接口，
 * 只保留 {@link #getSerializedName()} 方法本身（NBT {@code SlotShape} 存的就是这个名字，
 * 见 {@code GunPegboardBlockEntity}）。</li>
 * <li>{@code Direction#getCounterClockWise()/getClockWise()} -> {@link BlockMetadata#counterClockwise} /
 * {@link BlockMetadata#clockwise}（ForgeDirection 没有这两个方法）。</li>
 * </ul>
 */
public enum PegboardShape {

    STRAIGHT("straight"),
    CORNER_LEFT("corner_left"),
    CORNER_RIGHT("corner_right");

    private final String name;

    PegboardShape(String name) {
        this.name = name;
    }

    /**
     * 上游 {@code getSerializedName()}。NBT {@code SlotShape} 与网络更新都用这个字符串，
     * <b>不得改动</b>（存档兼容）。
     */
    public String getSerializedName() {
        return name;
    }

    /** 上游 {@code getCornerFacing(Direction)}：折面所在的方向（主面朝向的逆/顺时针）。 */
    public ForgeDirection getCornerFacing(ForgeDirection facing) {
        switch (this) {
            case CORNER_LEFT:
                return BlockMetadata.counterClockwise(facing);
            case CORNER_RIGHT:
                return BlockMetadata.clockwise(facing);
            case STRAIGHT:
            default:
                return facing;
        }
    }

    /** 按 metadata 位域取值（编码见 {@link GunPegboardBlock} 类注释）。 */
    public static PegboardShape byMeta(int value) {
        switch (value) {
            case 1:
                return CORNER_LEFT;
            case 2:
                return CORNER_RIGHT;
            case 0:
            default:
                return STRAIGHT;
        }
    }

    /** 转 metadata 位域值（0..2）。 */
    public int toMeta() {
        switch (this) {
            case CORNER_LEFT:
                return 1;
            case CORNER_RIGHT:
                return 2;
            case STRAIGHT:
            default:
                return 0;
        }
    }
}
