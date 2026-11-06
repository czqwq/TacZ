package com.tacz.guns.client.resource.pojo.animation.bedrock;

import java.util.TreeMap;

import net.minecraft.util.ResourceLocation;

/**
 * 基岩版动画 {@code sound_effects} 通道关键帧。
 * <p>
 * TODO(port): 上游 SoundEffectKeyframes.java:7 是 {@code Double2ObjectRBTreeMap<ResourceLocation>}（fastutil，键为时间（秒））。
 * 1.7.10 不引入 fastutil，机械替换时误写成 {@code TreeMap<ResourceLocation>}（少了键类型），
 * 此处按 fastutil 语义改回 {@link TreeMap}&lt;{@link Double}, {@link ResourceLocation}&gt;。
 */
public class SoundEffectKeyframes {

    private final TreeMap<Double, ResourceLocation> keyframes;

    public SoundEffectKeyframes(TreeMap<Double, ResourceLocation> keyframes) {
        this.keyframes = keyframes;
    }

    public TreeMap<Double, ResourceLocation> getKeyframes() {
        return keyframes;
    }
}
