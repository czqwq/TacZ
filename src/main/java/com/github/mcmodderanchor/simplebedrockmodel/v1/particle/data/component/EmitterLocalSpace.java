// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/EmitterLocalSpace.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getBoolean;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.google.gson.JsonObject;

/**
 * 发射器局部空间组件。对应 "minecraft:emitter_local_space"。
 */
public record EmitterLocalSpace(boolean position, boolean rotation, boolean velocity)
    implements IEmitterComponentDefinition, IEmitterComponent {

    @Override
    public int order() {
        return 400;
    }

    @Override
    public void apply(ParticleEmitterInstance emitter) {
        emitter.setLocalSpaceFlags(position, position && rotation, velocity);
    }

    public static EmitterLocalSpace fromJson(JsonObject obj) {
        return new EmitterLocalSpace(
            getBoolean(obj, "position", false),
            getBoolean(obj, "rotation", false),
            getBoolean(obj, "velocity", false));
    }
}
