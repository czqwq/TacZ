// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/ParticleDescription.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data;

import net.minecraft.util.ResourceLocation;

/**
 * 粒子效果描述符，对应 JSON 中的 "description" 块。
 * 包含 identifier、基础渲染参数（material）和纹理信息。
 */
public class ParticleDescription {

    private final ResourceLocation identifier;
    private final Material material;
    private final ResourceLocation texture;
    /** flipbook 纹理的列数，0 表示非 flipbook */
    private final int textureWidth;
    /** flipbook 纹理的行数 */
    private final int textureHeight;

    public ParticleDescription(ResourceLocation identifier, Material material, ResourceLocation texture,
        int textureWidth, int textureHeight) {
        this.identifier = identifier;
        this.material = material;
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public ResourceLocation getIdentifier() {
        return identifier;
    }

    public Material getMaterial() {
        return material;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public boolean isFlipbook() {
        return textureWidth > 0 && textureHeight > 0;
    }

    public enum Material {
        PARTICLES_BLEND,
        PARTICLES_OPAQUE,
        PARTICLES_ALPHA,
        PARTICLES_ADD,
        ENERGY_SWIRL,
        PARTICLES_EMISSIVE
    }
}
