// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/pojo/BedrockAnimationPOJO.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo;

import java.util.Map;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.exclusion.NeedForRootMotion;
import com.google.gson.annotations.SerializedName;

public class BedrockAnimationPOJO {

    @SerializedName("loop")
    private boolean loop;

    @SerializedName("animation_length")
    private double animationLength;

    @SerializedName("bones")
    @NeedForRootMotion
    private Map<String, AnimationBone> bones;

    @SerializedName("sound_effects")
    private SoundEffectKeyframes soundEffects;

    @SerializedName("particle_effects")
    private ParticleEffectKeyframes particleEffects;

    @SerializedName("timeline")
    private TimelineKeyframes timeline;

    public boolean isLoop() {
        return loop;
    }

    public double getAnimationLength() {
        return animationLength;
    }

    public Map<String, AnimationBone> getBones() {
        return bones;
    }

    public SoundEffectKeyframes getSoundEffects() {
        return soundEffects;
    }

    public ParticleEffectKeyframes getParticleEffects() {
        return particleEffects;
    }

    public TimelineKeyframes getTimeline() {
        return timeline;
    }
}
