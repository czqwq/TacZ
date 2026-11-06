// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/rate/EmitterRateManual.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.rate;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getMolang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IEmitterComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 手动发射模式。对应 "minecraft:emitter_rate_manual"。不自动发射粒子，由外部 API 触发。
 */
public record EmitterRateManual(MolangExpression maxParticles) implements RateComponent, IEmitterComponent {

    @Override
    public int order() {
        return 530;
    }

    @Override
    public boolean requireUpdate() {
        return false;
    }

    public static EmitterRateManual fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        return new EmitterRateManual(molang.compile(getMolang(obj, "max_particles", "50")));
    }
}
