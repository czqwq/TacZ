// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/resource/BedrockModelEntry.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import java.util.List;
import java.util.function.Function;

import net.minecraft.util.ResourceLocation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;

public record BedrockModelEntry(RawResourceLoader rawLoader, ResourceLocation sourceId, ModelType kind,
    Function<BedrockModelBakeContext, BakerOptions> optionsFactory, List<ResourceLocation> animationSourceIds,
    boolean lazy) {

    public BedrockModelEntry {
        animationSourceIds = List.copyOf(animationSourceIds);
    }
}
