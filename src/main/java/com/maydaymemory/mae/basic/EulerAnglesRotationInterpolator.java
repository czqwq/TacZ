// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.EulerAnglesRotationInterpolator
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import javax.annotation.Nonnull;

import org.joml.Vector3fc;

public class EulerAnglesRotationInterpolator implements Interpolator<Rotation> {

    private final Interpolator<Vector3fc> interpolator;

    public EulerAnglesRotationInterpolator(Interpolator<Vector3fc> interpolator) {
        this.interpolator = interpolator;
    }

    public Rotation interpolate(InterpolatableChannel<Rotation> channel, int indexFrom, int indexTo, float alpha) {
        return new Rotation(
            this.interpolator.interpolate(new ChannelWrapper(channel, this.interpolator), indexFrom, indexTo, alpha));
    }

    public Rotation interpolate(InterpolatableChannel<Rotation> channel, int indexFrom, int indexTo, float alpha,
        IEvaluationContext ctx) {
        return new Rotation(
            this.interpolator
                .interpolate(new ChannelWrapper(channel, this.interpolator), indexFrom, indexTo, alpha, ctx));
    }

    public Interpolator.Priority getPriority() {
        return this.interpolator.getPriority();
    }

    private static class ChannelWrapper implements InterpolatableChannel<Vector3fc> {

        private final InterpolatableChannel<Rotation> channel;
        private final Interpolator<Vector3fc> interpolator;

        public ChannelWrapper(InterpolatableChannel<Rotation> channel, Interpolator<Vector3fc> interpolator) {
            this.channel = channel;
            this.interpolator = interpolator;
        }

        public Vector3fc compute(float timeS) {
            Rotation compute = this.channel.compute(timeS);
            if (!compute.isEulerAngles()) {
                throw new IllegalArgumentException(
                    "Rotation must be euler angles when using EulerAnglesRotationInterpolator");
            } else {
                return compute.getEulerAngles();
            }
        }

        public InterpolatableKeyframe<Vector3fc> getKeyFrame(int index) {
            InterpolatableKeyframe<Rotation> initialKeyframe = this.channel.getKeyFrame(index);
            return new KeyframeWrapper(initialKeyframe, this.interpolator);
        }

        public int getKeyFrameCount() {
            return this.channel.getKeyFrameCount();
        }

        public float getEndTimeS() {
            return this.channel.getEndTimeS();
        }
    }

    private static class KeyframeWrapper implements InterpolatableKeyframe<Vector3fc> {

        private final InterpolatableKeyframe<Rotation> initialKeyframe;
        private final Interpolator<Vector3fc> interpolator;

        public KeyframeWrapper(InterpolatableKeyframe<Rotation> initialKeyframe, Interpolator<Vector3fc> interpolator) {
            this.initialKeyframe = initialKeyframe;
            this.interpolator = interpolator;
        }

        public Vector3fc getPre() {
            Rotation pre = this.initialKeyframe.getPre();
            if (!pre.isEulerAngles()) {
                throw new IllegalArgumentException(
                    "Rotation must be euler angles when using EulerAnglesRotationInterpolator");
            } else {
                return pre.getEulerAngles();
            }
        }

        public Vector3fc getPost() {
            Rotation post = this.initialKeyframe.getPost();
            if (!post.isEulerAngles()) {
                throw new IllegalArgumentException(
                    "Rotation must be euler angles when using EulerAnglesRotationInterpolator");
            } else {
                return post.getEulerAngles();
            }
        }

        public Vector3fc getPre(IEvaluationContext ctx) {
            Rotation pre = this.initialKeyframe.getPre(ctx);
            if (!pre.isEulerAngles()) {
                throw new IllegalArgumentException(
                    "Rotation must be euler angles when using EulerAnglesRotationInterpolator");
            } else {
                return pre.getEulerAngles();
            }
        }

        public Vector3fc getPost(IEvaluationContext ctx) {
            Rotation post = this.initialKeyframe.getPost(ctx);
            if (!post.isEulerAngles()) {
                throw new IllegalArgumentException(
                    "Rotation must be euler angles when using EulerAnglesRotationInterpolator");
            } else {
                return post.getEulerAngles();
            }
        }

        public Interpolator<Vector3fc> getInterpolator() {
            return this.interpolator;
        }

        public float getTimeS() {
            return this.initialKeyframe.getTimeS();
        }

        public int compareTo(@Nonnull Keyframe<Vector3fc> other) {
            return Float.compare(this.getTimeS(), other.getTimeS());
        }

        public Vector3fc getValue() {
            return this.getPre();
        }
    }
}
