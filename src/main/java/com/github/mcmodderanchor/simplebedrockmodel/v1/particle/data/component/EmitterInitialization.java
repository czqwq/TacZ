// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/EmitterInitialization.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getMolang;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonObject;

public record EmitterInitialization(@Nullable MolangExpression creationExpression,
    @Nullable MolangExpression perUpdateExpression) implements IEmitterComponentDefinition, IEmitterComponent {

    @Override
    public int order() {
        return 505;
    }

    @Override
    public boolean requireUpdate() {
        return perUpdateExpression != null;
    }

    @Override
    public void apply(ParticleEmitterInstance emitter) {
        if (creationExpression != null) {
            creationExpression.evaluate(
                emitter.getMolang()
                    .getContext());
        }
    }

    @Override
    public void update(ParticleEmitterInstance emitter) {
        if (perUpdateExpression != null) {
            perUpdateExpression.evaluate(
                emitter.getMolang()
                    .getContext());
        }
    }

    public static EmitterInitialization fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        MolangExpression creation = null;
        MolangExpression perUpdate = null;
        if (obj.has("creation_expression")) {
            String expr = getMolang(obj, "creation_expression", "");
            if (!expr.isEmpty()) creation = molang.compile(expr);
        }
        if (obj.has("per_update_expression")) {
            String expr = getMolang(obj, "per_update_expression", "");
            if (!expr.isEmpty()) perUpdate = molang.compile(expr);
        }
        return new EmitterInitialization(creation, perUpdate);
    }
}
