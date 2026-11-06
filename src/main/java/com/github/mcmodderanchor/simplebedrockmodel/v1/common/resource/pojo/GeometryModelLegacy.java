// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/GeometryModelLegacy.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion.ClientOnly;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion.NeedForRootMotion;
import com.google.gson.annotations.SerializedName;

public class GeometryModelLegacy {

    @SerializedName("bones")
    @NeedForRootMotion
    private BonesItem[] bones;

    @SerializedName("textureheight")
    @ClientOnly
    private int textureHeight;

    @SerializedName("texturewidth")
    @ClientOnly
    private int textureWidth;

    @SerializedName("visible_bounds_height")
    @ClientOnly
    private float visibleBoundsHeight;

    @SerializedName("visible_bounds_width")
    @ClientOnly
    private float visibleBoundsWidth;

    @SerializedName("visible_bounds_offset")
    @ClientOnly
    private float[] visibleBoundsOffset;

    public BonesItem[] getBones() {
        return bones;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public float getVisibleBoundsHeight() {
        return visibleBoundsHeight;
    }

    public float getVisibleBoundsWidth() {
        return visibleBoundsWidth;
    }

    public float[] getVisibleBoundsOffset() {
        return visibleBoundsOffset;
    }

    public void deco() {
        if (bones == null) {
            return;
        }
        for (BonesItem bonesItem : this.bones) {
            if (bonesItem.getCubes() == null) {
                continue;
            }
            for (CubesItem cubesItem : bonesItem.getCubes()) {
                if (!cubesItem.isHasMirror()) {
                    cubesItem.setMirror(bonesItem.isMirror());
                }
            }
        }
    }
}
