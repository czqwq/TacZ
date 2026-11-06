// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/resource/BedrockAnimationEntry.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.resource;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.resource.RawResourceLoader;

public record BedrockAnimationEntry(RawResourceLoader rawLoader, @Nullable ResourceLocation modelId,
    @Nullable BedrockAnimationFactory factory, boolean lazy, boolean createRuntimeAnimations) {}
