// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.BoneTransform
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import javax.annotation.Nonnull;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public class BoneTransform implements Comparable<BoneTransform> {

    private final int boneIndex;
    @Nonnull
    private final Vector3fc translation;
    @Nonnull
    private final RotationView rotation;
    @Nonnull
    private final Vector3fc scale;
    public static final BoneTransform IDENTITY_TRANSFORM;
    public static final Vector3f ZERO_VECTOR;
    public static final BoneTransform IDENTITY_VELOCITY;

    public BoneTransform(int boneIndex, Vector3fc translation, RotationView rotation, Vector3fc scale) {
        this.boneIndex = boneIndex;
        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;
    }

    public int compareTo(@Nonnull BoneTransform transform) {
        return Integer.compare(this.boneIndex, transform.boneIndex);
    }

    public int boneIndex() {
        return this.boneIndex;
    }

    public Vector3fc translation() {
        return this.translation;
    }

    public RotationView rotation() {
        return this.rotation;
    }

    public Vector3fc scale() {
        return this.scale;
    }

    static {
        IDENTITY_TRANSFORM = new BoneTransform(
            -1,
            new Vector3f(0.0F, 0.0F, 0.0F),
            RotationView.IDENTITY,
            new Vector3f(1.0F, 1.0F, 1.0F));
        ZERO_VECTOR = new Vector3f(0.0F, 0.0F, 0.0F);
        IDENTITY_VELOCITY = new BoneTransform(
            -1,
            ZERO_VECTOR,
            new RotationVelocityRotationView(ZERO_VECTOR),
            ZERO_VECTOR);
    }
}
