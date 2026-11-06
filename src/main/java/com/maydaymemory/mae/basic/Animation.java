// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.Animation
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import javax.annotation.Nullable;

import org.joml.Vector3fc;

public interface Animation {

    String getName();

    void setTranslationChannel(int var1, @Nullable InterpolatableChannel<? extends Vector3fc> var2);

    void setScaleChannel(int var1, @Nullable InterpolatableChannel<? extends Vector3fc> var2);

    void setRotationChannel(int var1, @Nullable InterpolatableChannel<? extends Rotation> var2);

    Pose evaluate(float var1);

    default Pose evaluate(float timeS, IEvaluationContext ctx) {
        return this.evaluate(timeS);
    }

    void setClipChannel(String var1, @Nullable ClipChannel<?> var2);

    @Nullable
    <T> Iterable<Keyframe<T>> clip(String var1, float var2, float var3);

    void setCurve(String var1, @Nullable InterpolatableChannel<?> var2);

    @Nullable
    <T> T evaluateCurve(String var1, float var2);

    float getEndTimeS();
}
