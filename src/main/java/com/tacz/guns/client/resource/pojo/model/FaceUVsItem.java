package com.tacz.guns.client.resource.pojo.model;

import net.minecraftforge.common.util.ForgeDirection;

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

    public static FaceUVsItem singleSouthFace() {
        FaceUVsItem faces = new FaceUVsItem();
        faces.north = FaceItem.EMPTY;
        faces.east = FaceItem.EMPTY;
        faces.west = FaceItem.EMPTY;
        faces.south = FaceItem.single16X();
        faces.up = FaceItem.EMPTY;
        faces.down = FaceItem.EMPTY;
        return faces;
    }

    /**
     * TODO(port): 上游 FaceUVsItem.java:31 用 {@code net.minecraft.core.Direction}（1.20）。
     * 1.7.10 用 {@link ForgeDirection}，六个方向常量同名（EAST/WEST/NORTH/SOUTH/UP/DOWN），
     * 另有 1.20 不存在的 UNKNOWN，一并落到 default 分支。
     */
    public FaceItem getFace(ForgeDirection direction) {
        switch (direction) {
            case EAST:
                return west == null ? FaceItem.EMPTY : west;
            case WEST:
                return east == null ? FaceItem.EMPTY : east;
            case NORTH:
                return north == null ? FaceItem.EMPTY : north;
            case SOUTH:
                return south == null ? FaceItem.EMPTY : south;
            case UP:
                return down == null ? FaceItem.EMPTY : down;
            case DOWN:
            default:
                return up == null ? FaceItem.EMPTY : up;
        }
    }
}
