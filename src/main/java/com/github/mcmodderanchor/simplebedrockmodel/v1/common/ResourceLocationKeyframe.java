// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/ResourceLocationKeyframe.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common;

import net.minecraft.util.ResourceLocation;

import com.maydaymemory.mae.basic.BaseKeyframe;

public class ResourceLocationKeyframe extends BaseKeyframe<ResourceLocation> {

    private final ResourceLocation resourceLocation;

    public ResourceLocationKeyframe(float timeS, ResourceLocation resourceLocation) {
        super(timeS);
        this.resourceLocation = resourceLocation;
    }

    @Override
    public ResourceLocation getValue() {
        return resourceLocation;
    }
}
