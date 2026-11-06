// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/GeometryModelNew.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion.ClientOnly;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion.NeedForRootMotion;
import com.google.gson.annotations.SerializedName;

public class GeometryModelNew {

    @SerializedName("description")
    @ClientOnly
    private Description description;

    @SerializedName("bones")
    @NeedForRootMotion
    private BonesItem[] bones;

    public Description getDescription() {
        return description;
    }

    public BonesItem[] getBones() {
        return bones;
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
