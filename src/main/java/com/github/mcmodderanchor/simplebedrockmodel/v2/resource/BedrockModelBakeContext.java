// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/resource/BedrockModelBakeContext.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;

public record BedrockModelBakeContext(ResourceLocation modelId, BedrockModelPOJO modelPojo,
    List<BedrockAnimationFile> animationFiles) {

    public BedrockModelBakeContext {
        animationFiles = List.copyOf(animationFiles);
    }

    public Set<String> collectAnimatedBones() {
        LinkedHashSet<String> bones = new LinkedHashSet<>();
        for (BedrockAnimationFile animationFile : animationFiles) {
            bones.addAll(BakerOptions.collectAnimatedBones(animationFile));
        }
        return Set.copyOf(bones);
    }

    public BakerOptions optionsFromAnimations() {
        Collection<String> animatedBones = collectAnimatedBones();
        if (animatedBones.isEmpty()) {
            return BakerOptions.defaults();
        }
        return BakerOptions.ofAnimatedBones(Set.copyOf(animatedBones));
    }
}
