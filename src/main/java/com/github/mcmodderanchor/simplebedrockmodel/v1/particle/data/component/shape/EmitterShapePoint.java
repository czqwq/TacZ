// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/shape/EmitterShapePoint.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.shape;

import java.util.Random;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;

public record EmitterShapePoint(MolangExpression[] offset, @Nullable MolangExpression[] direction,
    DirectionMode directionMode) implements EmitterShape {

    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        p.x = (float) offset[0].evaluate(ctx);
        p.y = (float) offset[1].evaluate(ctx);
        p.z = (float) offset[2].evaluate(ctx);
    }
}
