// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.RotationKeyframe
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

public class RotationKeyframe extends BaseKeyframe<Rotation> implements InterpolatableKeyframe<Rotation> {

    private final Rotation pre;
    private final Rotation post;
    private final Interpolator<Rotation> interpolator;

    public RotationKeyframe(float timeS, Rotation pre, Rotation post, Interpolator<Rotation> interpolator) {
        super(timeS);
        this.pre = pre;
        this.post = post;
        this.interpolator = interpolator;
    }

    public Rotation getPre() {
        return this.pre;
    }

    public Rotation getPost() {
        return this.post;
    }

    public Interpolator<Rotation> getInterpolator() {
        return this.interpolator;
    }

    public Rotation getValue() {
        return this.getPre();
    }
}
