// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.Vector3fLinearInterpolator
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public class Vector3fLinearInterpolator implements Interpolator<Vector3fc> {

    public static Vector3fLinearInterpolator INSTANCE = new Vector3fLinearInterpolator();

    private Vector3fLinearInterpolator() {}

    public Vector3fc interpolate(InterpolatableChannel<Vector3fc> channel, int indexFrom, int indexTo, float alpha) {
        InterpolatableKeyframe<Vector3fc> keyFrameFrom = channel.getKeyFrame(indexFrom);
        InterpolatableKeyframe<Vector3fc> keyFrameTo = channel.getKeyFrame(indexTo);
        Vector3fc vecFrom = keyFrameFrom.getPost();
        Vector3fc vecTo = keyFrameTo.getPre();
        return vecFrom.lerp(vecTo, alpha, new Vector3f());
    }

    public Vector3fc interpolate(InterpolatableChannel<Vector3fc> channel, int indexFrom, int indexTo, float alpha,
        IEvaluationContext ctx) {
        InterpolatableKeyframe<Vector3fc> keyFrameFrom = channel.getKeyFrame(indexFrom);
        InterpolatableKeyframe<Vector3fc> keyFrameTo = channel.getKeyFrame(indexTo);
        Vector3fc vecFrom = keyFrameFrom.getPost(ctx);
        Vector3fc vecTo = keyFrameTo.getPre(ctx);
        return vecFrom.lerp(vecTo, alpha, new Vector3f());
    }

    public Interpolator.Priority getPriority() {
        return Interpolator.Priority.MEDIUM;
    }
}
