// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.Vector3fCubicSplineInterpolator
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public class Vector3fCubicSplineInterpolator implements Interpolator<Vector3fc> {

    public static final Vector3fCubicSplineInterpolator INSTANCE = new Vector3fCubicSplineInterpolator();

    private Vector3fCubicSplineInterpolator() {}

    public Vector3fc interpolate(InterpolatableChannel<Vector3fc> channel, int indexFrom, int indexTo, float alpha) {
        int size = channel.getKeyFrameCount();
        int prev = indexFrom == 0 ? 0 : indexFrom - 1;
        int next = indexTo == size - 1 ? indexTo : indexTo + 1;
        Vector3fc vecPrev = channel.getKeyFrame(prev)
            .getPost();
        Vector3fc vecFrom = channel.getKeyFrame(indexFrom)
            .getPre();
        Vector3fc vecTo = channel.getKeyFrame(indexTo)
            .getPre();
        Vector3fc vecNext = channel.getKeyFrame(next)
            .getPre();
        float x = cubicSpline(vecPrev.x(), vecFrom.x(), vecTo.x(), vecNext.x(), alpha);
        float y = cubicSpline(vecPrev.y(), vecFrom.y(), vecTo.y(), vecNext.y(), alpha);
        float z = cubicSpline(vecPrev.z(), vecFrom.z(), vecTo.z(), vecNext.z(), alpha);
        return new Vector3f(x, y, z);
    }

    public Vector3fc interpolate(InterpolatableChannel<Vector3fc> channel, int indexFrom, int indexTo, float alpha,
        IEvaluationContext ctx) {
        int size = channel.getKeyFrameCount();
        int prev = indexFrom == 0 ? 0 : indexFrom - 1;
        int next = indexTo == size - 1 ? indexTo : indexTo + 1;
        Vector3fc vecPrev = channel.getKeyFrame(prev)
            .getPost(ctx);
        Vector3fc vecFrom = channel.getKeyFrame(indexFrom)
            .getPre(ctx);
        Vector3fc vecTo = channel.getKeyFrame(indexTo)
            .getPre(ctx);
        Vector3fc vecNext = channel.getKeyFrame(next)
            .getPre(ctx);
        float x = cubicSpline(vecPrev.x(), vecFrom.x(), vecTo.x(), vecNext.x(), alpha);
        float y = cubicSpline(vecPrev.y(), vecFrom.y(), vecTo.y(), vecNext.y(), alpha);
        float z = cubicSpline(vecPrev.z(), vecFrom.z(), vecTo.z(), vecNext.z(), alpha);
        return new Vector3f(x, y, z);
    }

    public Interpolator.Priority getPriority() {
        return Interpolator.Priority.HIGH;
    }

    private static float cubicSpline(float p, float x, float y, float n, float alpha) {
        float v0 = (y - p) * 0.5F;
        float v1 = (n - x) * 0.5F;
        float t2 = alpha * alpha;
        float t3 = alpha * t2;
        float h1 = 2.0F * t3 - 3.0F * t2 + 1.0F;
        float h2 = -2.0F * t3 + 3.0F * t2;
        float h3 = t3 - 2.0F * t2 + alpha;
        float h4 = t3 - t2;
        return h1 * x + h2 * y + h3 * v0 + h4 * v1;
    }
}
