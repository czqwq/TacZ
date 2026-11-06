// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/runtime/ModelRayTraceResult.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.github.mcmodderanchor.simplebedrockmodel.port.Vec3;

/**
 * The nearest cube-volume hit along a model ray segment.
 * <p>
 * {@link #location()} and {@link #normal()} are world-space values. {@link #attachmentOffset()} and
 * {@link #attachmentNormal()} are expressed in the attachment bone/group's local space, while both transform
 * matrices map their local coordinates into model space without including the world-space model origin.
 * A hit that begins strictly inside a cube keeps {@code t = 0}, but has no entry surface, so both normal values
 * are {@code null}.
 */
public record ModelRayTraceResult(Vec3 location, double t, int attachmentBoneIndex, int cubeIndex,
    @Nullable Vec3 normal, Vec3 attachmentOffset, @Nullable Vec3 attachmentNormal, Matrix4f attachmentTransform,
    Matrix4f cubeTransform) {

    public ModelRayTraceResult {
        location = Objects.requireNonNull(location, "location");
        attachmentOffset = Objects.requireNonNull(attachmentOffset, "attachmentOffset");
        attachmentTransform = new Matrix4f(Objects.requireNonNull(attachmentTransform, "attachmentTransform"));
        cubeTransform = new Matrix4f(Objects.requireNonNull(cubeTransform, "cubeTransform"));
    }

    /**
     * Compatibility constructor for callers that only require the original hit identifiers.
     */
    public ModelRayTraceResult(Vec3 location, double t, int attachmentBoneIndex, int cubeIndex) {
        this(location, t, attachmentBoneIndex, cubeIndex, null, Vec3.ZERO, null, new Matrix4f(), new Matrix4f());
    }

    @Override
    public Matrix4f attachmentTransform() {
        return new Matrix4f(attachmentTransform);
    }

    @Override
    public Matrix4f cubeTransform() {
        return new Matrix4f(cubeTransform);
    }
}
