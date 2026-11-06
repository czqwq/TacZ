// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/ParticleEffectKeyframes.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;

public class ParticleEffectKeyframes {

    private final Double2ObjectRBTreeMap<ParticleEffectData> keyframes;

    public ParticleEffectKeyframes(Double2ObjectRBTreeMap<ParticleEffectData> keyframes) {
        this.keyframes = keyframes;
    }

    public Double2ObjectRBTreeMap<ParticleEffectData> getKeyframes() {
        return keyframes;
    }
}
