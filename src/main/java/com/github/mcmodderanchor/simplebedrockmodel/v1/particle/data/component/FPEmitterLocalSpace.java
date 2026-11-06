// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/FPEmitterLocalSpace.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getBoolean;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.google.gson.JsonObject;

public record FPEmitterLocalSpace(boolean position, boolean rotation, boolean velocity, boolean toWorld)
    implements IEmitterComponentDefinition, IEmitterComponent {

    @Override
    public int order() {
        return 410;
    }

    @Override
    public void apply(ParticleEmitterInstance emitter) {
        emitter.enableFPMode(position, position && rotation, velocity, toWorld);
    }

    public static FPEmitterLocalSpace fromJson(JsonObject obj) {
        return new FPEmitterLocalSpace(
            getBoolean(obj, "position", false),
            getBoolean(obj, "rotation", false),
            getBoolean(obj, "velocity", false),
            getBoolean(obj, "to_world", false));
    }
}
