// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/baked/BakedCubeGeometry.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

/**
 * Retained cube geometry associated with one folded runtime attachment bone.
 */
public record BakedCubeGeometry(int attachBoneIndex, @Nullable LocalCubeBounds bounds, BakedCube[] cubes) {

    public BakedCubeGeometry {
        cubes = Objects.requireNonNull(cubes, "cubes")
            .clone();
        if (cubes.length > 0 && bounds == null) {
            throw new IllegalArgumentException("bounds is required when cubes are present");
        }
    }

    @Override
    public BakedCube[] cubes() {
        return cubes.clone();
    }
}
