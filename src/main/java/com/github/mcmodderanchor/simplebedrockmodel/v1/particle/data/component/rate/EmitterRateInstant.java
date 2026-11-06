// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/rate/EmitterRateInstant.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.rate;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getMolang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IEmitterComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 一次性发射。对应 "minecraft:emitter_rate_instant"。
 */
public record EmitterRateInstant(MolangExpression amount) implements RateComponent {

    @Override
    public int order() {
        return 530;
    }

    @Override
    public boolean requireUpdate() {
        return true;
    }

    @Override
    public IEmitterComponent createRuntime() {
        return new Runtime(amount);
    }

    public static EmitterRateInstant fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        return new EmitterRateInstant(molang.compile(getMolang(obj, "num_particles", "10")));
    }

    // ===== Runtime =====

    static final class Runtime implements IEmitterComponent {

        private final MolangExpression amount;
        private boolean hasEmitted;

        Runtime(MolangExpression amount) {
            this.amount = amount;
        }

        @Override
        public void apply(ParticleEmitterInstance emitter) {
            this.hasEmitted = false;
        }

        @Override
        public void update(ParticleEmitterInstance emitter) {
            if (hasEmitted || !emitter.isActive()) return;
            MolangContext<?> ctx = emitter.getMolang()
                .getContext();
            int count = (int) amount.evaluate(ctx);
            for (int i = 0; i < count; i++) {
                emitter.spawnParticle();
            }
            hasEmitted = true;
        }
    }
}
