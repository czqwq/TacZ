// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/resource/BedrockModelResource.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.BoneIndexProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.animation.BedrockAnimation;

public record BedrockModelResource(BoneIndexProvider model, ModelType kind,
    Map<ResourceLocation, List<BedrockAnimation>> animations) {

    public BedrockModelResource {
        animations = Map.copyOf(animations);
    }

    @Nullable
    public List<BedrockAnimation> getAnimations(ResourceLocation animationId) {
        return animations.get(animationId);
    }

    @UnmodifiableView
    public Map<ResourceLocation, List<BedrockAnimation>> getAllAnimations() {
        return Collections.unmodifiableMap(animations);
    }
}
