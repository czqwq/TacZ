// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/shape/EmitterShapeEntityAABB.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.shape;

import java.util.Random;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;

public record EmitterShapeEntityAABB(MolangExpression[] offset, boolean surfaceOnly,
    @Nullable MolangExpression[] direction, DirectionMode directionMode) implements EmitterShape {

    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        float ox = (float) offset[0].evaluate(ctx);
        float oy = (float) offset[1].evaluate(ctx);
        float oz = (float) offset[2].evaluate(ctx);
        p.x = ox + (random.nextFloat() * 2 - 1) * 0.5f;
        p.y = oy + random.nextFloat();
        p.z = oz + (random.nextFloat() * 2 - 1) * 0.5f;
    }
}
