// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/SoundEffectKeyframes.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import net.minecraft.util.ResourceLocation;

import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;

public class SoundEffectKeyframes {

    private final Double2ObjectRBTreeMap<ResourceLocation> keyframes;

    public SoundEffectKeyframes(Double2ObjectRBTreeMap<ResourceLocation> keyframes) {
        this.keyframes = keyframes;
    }

    public Double2ObjectRBTreeMap<ResourceLocation> getKeyframes() {
        return keyframes;
    }
}
