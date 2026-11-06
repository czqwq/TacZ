// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.BasicAnimation
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public class BasicAnimation implements Animation {

    private final String name;
    private float endTimeS = -1.0F;
    private final ArrayList<ChannelBunch> channels = new ArrayList();
    @Nullable
    private Object2ObjectArrayMap<String, ClipChannel<?>> clipChannels;
    @Nullable
    private Object2ObjectArrayMap<String, InterpolatableChannel<?>> curves;
    private final Supplier<PoseBuilder> poseBuilderSupplier;
    private final BoneTransformFactory transformFactory;
    private static final Vector3f IDENTITY_TRANSLATION = new Vector3f(0.0F, 0.0F, 0.0F);
    private static final Quaternionf IDENTITY_ROTATION = new Quaternionf(0.0F, 0.0F, 0.0F, 1.0F);
    private static final Vector3f IDENTITY_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);

    public BasicAnimation(String name, BoneTransformFactory boneTransformFactory,
        Supplier<PoseBuilder> poseBuilderSupplier) {
        this.name = name;
        this.poseBuilderSupplier = poseBuilderSupplier;
        this.transformFactory = boneTransformFactory;
    }

    public String getName() {
        return this.name;
    }

    public void setTranslationChannel(int boneIndex, @Nullable InterpolatableChannel<? extends Vector3fc> channel) {
        this.getOrCreateChannelBunchAnd(boneIndex, (channelBunch) -> channelBunch.translationChannel = channel);
        this.endTimeS = -1.0F;
    }

    public void setScaleChannel(int boneIndex, @Nullable InterpolatableChannel<? extends Vector3fc> channel) {
        this.getOrCreateChannelBunchAnd(boneIndex, (channelBunch) -> channelBunch.scaleChannel = channel);
        this.endTimeS = -1.0F;
    }

    public void setRotationChannel(int boneIndex, @Nullable InterpolatableChannel<? extends Rotation> channel) {
        this.getOrCreateChannelBunchAnd(boneIndex, (channelBunch) -> channelBunch.rotationChannel = channel);
        this.endTimeS = -1.0F;
    }

    public Pose evaluate(float timeS) {
        return this.evaluate(timeS, (IEvaluationContext) null);
    }

    public Pose evaluate(float timeS, IEvaluationContext ctx) {
        if (ctx != null) {
            ctx.prepareEvaluation(timeS);
        }

        PoseBuilder poseBuilder = (PoseBuilder) this.poseBuilderSupplier.get();

        for (ChannelBunch channelBunch : this.channels) {
            InterpolatableChannel<? extends Vector3fc> translationChannel = channelBunch.translationChannel;
            InterpolatableChannel<? extends Rotation> rotationChannel = channelBunch.rotationChannel;
            InterpolatableChannel<? extends Vector3fc> scaleChannel = channelBunch.scaleChannel;
            Vector3fc translation = (Vector3fc) (translationChannel == null ? IDENTITY_TRANSLATION
                : (Vector3fc) translationChannel.compute(timeS, ctx));
            Rotation rotation = rotationChannel == null ? null : (Rotation) rotationChannel.compute(timeS, ctx);
            Vector3fc scale = (Vector3fc) (scaleChannel == null ? IDENTITY_SCALE
                : (Vector3fc) scaleChannel.compute(timeS, ctx));
            BoneTransform boneTransform;
            if (rotation == null) {
                boneTransform = this.transformFactory
                    .createBoneTransform(channelBunch.boneIndex, translation, (Quaternionfc) IDENTITY_ROTATION, scale);
            } else if (rotation.isEulerAngles()) {
                boneTransform = this.transformFactory
                    .createBoneTransform(channelBunch.boneIndex, translation, rotation.getEulerAngles(), scale);
            } else {
                boneTransform = this.transformFactory
                    .createBoneTransform(channelBunch.boneIndex, translation, rotation.getQuaternion(), scale);
            }

            poseBuilder.addBoneTransform(boneTransform);
        }

        return poseBuilder.toPose();
    }

    public void setClipChannel(String channelName, ClipChannel<?> channel) {
        if (this.clipChannels == null) {
            this.clipChannels = new Object2ObjectArrayMap();
        }

        this.clipChannels.put(channelName, channel);
        this.endTimeS = -1.0F;
    }

    @Nullable
    public <T> Iterable<Keyframe<T>> clip(String channelName, float fromTimeS, float toTimeS) {
        if (this.clipChannels == null) {
            return null;
        } else {
            ClipChannel<?> channel = (ClipChannel) this.clipChannels.get(channelName);
            return channel == null ? null : (Iterable<Keyframe<T>>) (Iterable<?>) channel.clip(fromTimeS, toTimeS);
        }
    }

    public void setCurve(String curveName, @Nullable InterpolatableChannel<?> curve) {
        if (this.curves == null) {
            this.curves = new Object2ObjectArrayMap();
        }

        this.curves.put(curveName, curve);
    }

    @Nullable
    public <T> T evaluateCurve(String curveName, float timeS) {
        if (this.curves == null) {
            return null;
        } else {
            InterpolatableChannel<?> curve = (InterpolatableChannel) this.curves.get(curveName);
            return (T) (curve == null ? null : curve.compute(timeS));
        }
    }

    public float getEndTimeS() {
        if (this.endTimeS == -1.0F) {
            this.endTimeS = 0.0F;

            for (ChannelBunch channelBunch : this.channels) {
                if (channelBunch.translationChannel != null) {
                    this.endTimeS = Math.max(channelBunch.translationChannel.getEndTimeS(), this.endTimeS);
                }

                if (channelBunch.rotationChannel != null) {
                    this.endTimeS = Math.max(channelBunch.rotationChannel.getEndTimeS(), this.endTimeS);
                }

                if (channelBunch.scaleChannel != null) {
                    this.endTimeS = Math.max(channelBunch.scaleChannel.getEndTimeS(), this.endTimeS);
                }
            }

            if (this.clipChannels != null) {
                ObjectIterator var3 = this.clipChannels.values()
                    .iterator();

                while (var3.hasNext()) {
                    ClipChannel<?> channel = (ClipChannel) var3.next();
                    if (channel != null) {
                        this.endTimeS = Math.max(channel.getEndTimeS(), this.endTimeS);
                    }
                }
            }

            if (this.curves != null) {
                ObjectIterator var4 = this.curves.values()
                    .iterator();

                while (var4.hasNext()) {
                    InterpolatableChannel<?> channel = (InterpolatableChannel) var4.next();
                    if (channel != null) {
                        this.endTimeS = Math.max(channel.getEndTimeS(), this.endTimeS);
                    }
                }
            }
        }

        return this.endTimeS;
    }

    private void getOrCreateChannelBunchAnd(int boneIndex, Consumer<ChannelBunch> consumer) {
        if (!this.channels.isEmpty()
            && ((ChannelBunch) this.channels.get(this.channels.size() - 1)).boneIndex >= boneIndex) {
            ChannelBunch lastChannelBunch = (ChannelBunch) this.channels.get(this.channels.size() - 1);
            if (lastChannelBunch.boneIndex == boneIndex) {
                consumer.accept(lastChannelBunch);
            } else {
                ChannelBunch channelBunch = new ChannelBunch(boneIndex);
                int i = Collections.binarySearch(this.channels, channelBunch);
                if (i < 0) {
                    i = -i - 1;
                    this.channels.add(i, channelBunch);
                    consumer.accept(channelBunch);
                } else {
                    consumer.accept((ChannelBunch) this.channels.get(i));
                }

            }
        } else {
            ChannelBunch channelBunch = new ChannelBunch(boneIndex);
            consumer.accept(channelBunch);
            this.channels.add(channelBunch);
        }
    }

    private static class ChannelBunch implements Comparable<ChannelBunch> {

        private final int boneIndex;
        @Nullable
        private InterpolatableChannel<? extends Vector3fc> translationChannel;
        @Nullable
        private InterpolatableChannel<? extends Rotation> rotationChannel;
        @Nullable
        private InterpolatableChannel<? extends Vector3fc> scaleChannel;

        public ChannelBunch(int boneIndex) {
            this.boneIndex = boneIndex;
        }

        public int compareTo(ChannelBunch o) {
            return Integer.compare(this.boneIndex, o.boneIndex);
        }
    }
}
