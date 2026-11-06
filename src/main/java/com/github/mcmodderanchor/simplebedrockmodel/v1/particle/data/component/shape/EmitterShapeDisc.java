// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/shape/EmitterShapeDisc.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.shape;

import java.util.Random;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangContext;
import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;

public record EmitterShapeDisc(MolangExpression[] offset, MolangExpression radius, PlaneNormal planeNormal,
    boolean surfaceOnly, @Nullable MolangExpression[] direction, DirectionMode directionMode) implements EmitterShape {

    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        float ox = (float) offset[0].evaluate(ctx);
        float oy = (float) offset[1].evaluate(ctx);
        float oz = (float) offset[2].evaluate(ctx);
        float r = (float) radius.evaluate(ctx);
        float angle = (float) (random.nextFloat() * Math.PI * 2);
        float dist = surfaceOnly ? r : r * (float) Math.sqrt(random.nextFloat());
        float lx = dist * (float) Math.cos(angle);
        float lz = dist * (float) Math.sin(angle);
        switch (planeNormal) {
            case Y -> {
                p.x = ox + lx;
                p.y = oy;
                p.z = oz + lz;
            }
            case X -> {
                p.x = ox;
                p.y = oy + lx;
                p.z = oz + lz;
            }
            case Z -> {
                p.x = ox + lx;
                p.y = oy + lz;
                p.z = oz;
            }
            default -> {
                p.x = ox + lx;
                p.y = oy;
                p.z = oz + lz;
            }
        }
    }
}
