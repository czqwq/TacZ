// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.QuaternionRotationInterpolator
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public class QuaternionRotationInterpolator implements Interpolator<Rotation> {

    public Rotation interpolate(InterpolatableChannel<Rotation> channel, int indexFrom, int indexTo, float alpha) {
        InterpolatableKeyframe<Rotation> k1 = channel.getKeyFrame(indexFrom);
        InterpolatableKeyframe<Rotation> k2 = channel.getKeyFrame(indexTo);
        Rotation r1 = k1.getPost();
        Rotation r2 = k2.getPre();
        if (r1.isQuaternion() && r2.isQuaternion()) {
            Quaternionfc q1 = r1.getQuaternion();
            Quaternionfc q2 = r2.getQuaternion();
            return new Rotation(q1.slerp(q2, alpha, new Quaternionf()));
        } else {
            throw new IllegalArgumentException("Rotation must be quaternion when using quaternion interpolator");
        }
    }

    public Rotation interpolate(InterpolatableChannel<Rotation> channel, int indexFrom, int indexTo, float alpha,
        IEvaluationContext ctx) {
        InterpolatableKeyframe<Rotation> k1 = channel.getKeyFrame(indexFrom);
        InterpolatableKeyframe<Rotation> k2 = channel.getKeyFrame(indexTo);
        Rotation r1 = k1.getPost(ctx);
        Rotation r2 = k2.getPre(ctx);
        if (r1.isQuaternion() && r2.isQuaternion()) {
            Quaternionfc q1 = r1.getQuaternion();
            Quaternionfc q2 = r2.getQuaternion();
            return new Rotation(q1.slerp(q2, alpha, new Quaternionf()));
        } else {
            throw new IllegalArgumentException("Rotation must be quaternion when using quaternion interpolator");
        }
    }

    public Interpolator.Priority getPriority() {
        return Interpolator.Priority.MEDIUM;
    }
}
