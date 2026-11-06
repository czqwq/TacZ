package com.tacz.guns.client.resource.pojo.animation.bedrock;

import java.util.TreeMap;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;

/**
 * 基岩版动画 {@code particle_effects} 通道关键帧。
 * <p>
 * 键为时间（秒），值为 {@link ParticleEffectData}（效果 id + 定位骨骼名）。
 * <p>
 * TODO(port): 上游 ParticleEffectKeyframes.java:12 是 {@code Double2ObjectRBTreeMap<ParticleEffectData>}（fastutil）。
 * 1.7.10 不引入 fastutil，机械替换时误写成 {@code TreeMap<ParticleEffectData>}（少了键类型），
 * 此处按 fastutil 语义改回 {@link TreeMap}&lt;{@link Double}, {@link ParticleEffectData}&gt;。
 */
public class ParticleEffectKeyframes {

    private final TreeMap<Double, ParticleEffectData> keyframes;

    public ParticleEffectKeyframes(TreeMap<Double, ParticleEffectData> keyframes) {
        this.keyframes = keyframes;
    }

    public TreeMap<Double, ParticleEffectData> getKeyframes() {
        return keyframes;
    }
}
