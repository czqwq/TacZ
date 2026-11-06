// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.Vector3fKeyframe
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Vector3fc;

public class Vector3fKeyframe extends BaseKeyframe<Vector3fc> implements InterpolatableKeyframe<Vector3fc> {

    private final Vector3fc pre;
    private final Vector3fc post;
    private final Interpolator<Vector3fc> interpolator;

    public Vector3fKeyframe(float timeS, Vector3fc pre, Vector3fc post, Interpolator<Vector3fc> interpolator) {
        super(timeS);
        this.pre = pre;
        this.post = post;
        this.interpolator = interpolator;
    }

    public Vector3fc getPre() {
        return this.pre;
    }

    public Vector3fc getPost() {
        return this.post;
    }

    public Interpolator<Vector3fc> getInterpolator() {
        return this.interpolator;
    }

    public Vector3fc getValue() {
        return this.pre;
    }
}
