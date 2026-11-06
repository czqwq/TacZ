// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.BaseKeyframe
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import javax.annotation.Nonnull;

public abstract class BaseKeyframe<T> implements Keyframe<T> {

    protected final float timeS;

    public BaseKeyframe(float timeS) {
        this.timeS = timeS;
    }

    public int compareTo(@Nonnull Keyframe<T> other) {
        return Float.compare(this.timeS, other.getTimeS());
    }

    public float getTimeS() {
        return this.timeS;
    }
}
