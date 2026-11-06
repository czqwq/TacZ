// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.DynamicVector3fKeyframe
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.maydaymemory.mae.util.FloatSupplier;

public class DynamicVector3fKeyframe extends BaseKeyframe<Vector3fc> implements InterpolatableKeyframe<Vector3fc> {

    private final FloatSupplier x1;
    private final FloatSupplier y1;
    private final FloatSupplier z1;
    private final FloatSupplier x2;
    private final FloatSupplier y2;
    private final FloatSupplier z2;
    private final Interpolator<Vector3fc> interpolator;

    public DynamicVector3fKeyframe(float timeS, FloatSupplier x1, FloatSupplier y1, FloatSupplier z1, FloatSupplier x2,
        FloatSupplier y2, FloatSupplier z2, Interpolator<Vector3fc> interpolator) {
        super(timeS);
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.interpolator = interpolator;
    }

    public Vector3fc getPre() {
        return new Vector3f(this.x1.getAsFloat(), this.y1.getAsFloat(), this.z1.getAsFloat());
    }

    public Vector3fc getPost() {
        return new Vector3f(this.x2.getAsFloat(), this.y2.getAsFloat(), this.z2.getAsFloat());
    }

    public Interpolator<Vector3fc> getInterpolator() {
        return this.interpolator;
    }

    public Vector3fc getValue() {
        return this.getPre();
    }
}
