// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.ArrayInterpolatableChannel
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import java.util.ArrayList;

import javax.annotation.Nonnull;

public class ArrayInterpolatableChannel<T> extends ArrayAnimationChannelBase<InterpolatableKeyframe<T>>
    implements InterpolatableChannel<T> {

    public ArrayInterpolatableChannel(@Nonnull ArrayList<InterpolatableKeyframe<T>> keyframes) {
        super(keyframes);
    }

    public ArrayInterpolatableChannel() {
        super(new ArrayList());
    }

    public T compute(float timeS) {
        return (T) this.compute(timeS, (IEvaluationContext) null);
    }

    public T compute(float timeS, IEvaluationContext ctx) {
        this.assertNotDirty();
        int index = this.findIndexBefore(timeS, false);
        int indexNext = Math.min(this.innerList.size() - 1, index + 1);
        if (index == -1) {
            return (T) (this.isEmpty() ? null
                : ((InterpolatableKeyframe) this.get(0)).getInterpolator()
                    .interpolate(this, 0, 0, 0.0F, ctx));
        } else {
            InterpolatableKeyframe<T> keyframe = (InterpolatableKeyframe) this.get(index);
            if (index == indexNext) {
                return keyframe.getInterpolator()
                    .interpolate(this, index, index, 0.0F, ctx);
            } else {
                InterpolatableKeyframe<T> keyframeNext = (InterpolatableKeyframe) this.get(indexNext);
                float alphaTime = timeS - keyframe.getTimeS();
                float spanTime = keyframeNext.getTimeS() - keyframe.getTimeS();
                float alpha = alphaTime / spanTime;
                Interpolator<T> interpolator = keyframe.getInterpolator();
                Interpolator<T> interpolatorNext = keyframeNext.getInterpolator();
                return (T) (interpolator.getPriority()
                    .compareTo(interpolatorNext.getPriority()) >= 0
                        ? interpolator.interpolate(this, index, indexNext, alpha, ctx)
                        : interpolatorNext.interpolate(this, index, indexNext, alpha, ctx));
            }
        }
    }

    public InterpolatableKeyframe<T> getKeyFrame(int index) {
        return (InterpolatableKeyframe) this.get(index);
    }

    public int getKeyFrameCount() {
        return this.size();
    }
}
