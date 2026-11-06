// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/ParticleEffectDataKeyframe.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.maydaymemory.mae.basic.BaseKeyframe;

public class ParticleEffectDataKeyframe extends BaseKeyframe<ParticleEffectData> {

    private final ParticleEffectData particleEffectData;

    public ParticleEffectDataKeyframe(float timeS, ParticleEffectData particleEffectData) {
        super(timeS);
        this.particleEffectData = particleEffectData;
    }

    @Override
    public ParticleEffectData getValue() {
        return particleEffectData;
    }
}
