// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/ParticleInitialSpin.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getMolang;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonObject;

public record ParticleInitialSpin(MolangExpression rotation, MolangExpression rotationRate)
    implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return -490;
    }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang()
            .getContext();
        p.rotation = (float) rotation.evaluate(ctx);
        p.rotationRate = (float) rotationRate.evaluate(ctx);
    }

    public static ParticleInitialSpin fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        return new ParticleInitialSpin(
            molang.compile(getMolang(obj, "rotation", "0")),
            molang.compile(getMolang(obj, "rotation_rate", "0")));
    }
}
