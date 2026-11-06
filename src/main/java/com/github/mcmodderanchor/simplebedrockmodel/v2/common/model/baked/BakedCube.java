// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/baked/BakedCube.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import java.util.Objects;

import org.joml.Matrix4f;

/**
 * Retained local cube geometry for a baked attachment group.
 */
public record BakedCube(float x, float y, float z, float width, float height, float depth, Matrix4f localTransform) {

    public BakedCube {
        localTransform = new Matrix4f(Objects.requireNonNull(localTransform, "localTransform"));
    }

    @Override
    public Matrix4f localTransform() {
        return new Matrix4f(localTransform);
    }
}
