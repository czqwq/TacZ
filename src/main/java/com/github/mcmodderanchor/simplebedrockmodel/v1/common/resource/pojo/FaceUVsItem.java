// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/FaceUVsItem.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.Nullable;

import com.google.gson.annotations.SerializedName;

public class FaceUVsItem {

    @SerializedName("down")
    private FaceItem down;
    @SerializedName("east")
    private FaceItem east;
    @SerializedName("north")
    private FaceItem north;
    @SerializedName("south")
    private FaceItem south;
    @SerializedName("up")
    private FaceItem up;
    @SerializedName("west")
    private FaceItem west;

    public FaceUVsItem(FaceItem down, FaceItem east, FaceItem north, FaceItem south, FaceItem up, FaceItem west) {
        this.down = down;
        this.east = east;
        this.north = north;
        this.south = south;
        this.up = up;
        this.west = west;
    }

    /**
     * TODO(L2): 上游 v1/common/resource/pojo/FaceUVsItem.java:36 用 {@code net.minecraft.core.Direction}；
     * 1.7.10 没有 {@code Direction}（1.13+ 才把 EnumFacing 改名），改用 {@link EnumFacing}，
     * 六个常量名（DOWN/UP/NORTH/SOUTH/EAST/WEST）与 1.20 的 Direction 完全一致，行为等价。
     */
    @Nullable
    public FaceItem getFace(EnumFacing direction) {
        return switch (direction) {
            case EAST -> east;
            case WEST -> west;
            case NORTH -> north;
            case SOUTH -> south;
            case UP -> up;
            default -> down;
        };
    }
}
