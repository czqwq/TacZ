package com.tacz.guns.client.resource.pojo.animation.bedrock;

import java.util.TreeMap;

import javax.annotation.Nullable;

import org.joml.Vector3f;

/**
 * 基岩版动画一条通道的关键帧集合，键为时间（秒）。
 * <p>
 * 平台差异：上游 {@code AnimationKeyframes.java:11} 用 fastutil 的
 * {@code Double2ObjectRBTreeMap<Keyframe>}（升序红黑树）；1.7.10 不引入 fastutil，
 * 用语义等价的 {@link TreeMap}&lt;Double, Keyframe&gt;（同样按键升序遍历）。
 */
public class AnimationKeyframes {

    private final TreeMap<Double, Keyframe> keyframes;

    public AnimationKeyframes(TreeMap<Double, Keyframe> keyframes) {
        this.keyframes = keyframes;
    }

    public TreeMap<Double, Keyframe> getKeyframes() {
        return keyframes;
    }

    public record Keyframe(@Nullable Vector3f pre, @Nullable Vector3f post, @Nullable Vector3f data,
        @Nullable String lerpMode) {}
}
