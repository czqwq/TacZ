// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/AnimationBone.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import com.google.gson.annotations.SerializedName;

public class AnimationBone {

    @SerializedName("position")
    private AnimationKeyframes position;

    @SerializedName("rotation")
    private AnimationKeyframes rotation;

    @SerializedName("scale")
    private AnimationKeyframes scale;

    public AnimationKeyframes getPosition() {
        return position;
    }

    public AnimationKeyframes getRotation() {
        return rotation;
    }

    public AnimationKeyframes getScale() {
        return scale;
    }
}
