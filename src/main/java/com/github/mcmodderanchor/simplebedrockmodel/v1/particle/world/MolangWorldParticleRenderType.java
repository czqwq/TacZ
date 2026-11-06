// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/world/MolangWorldParticleRenderType.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleDescription;
import com.tacz.guns.mcshim.DefaultVertexFormat;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 世界粒子的 {@code RenderType} 工厂：每个「材质 + 纹理」组合对应一个 {@link RenderType} 实例。
 * <p>
 * todo 目前没用 atlas，一个贴图一个，考虑优化 ？
 *
 * <p>
 * TODO(L2): 上游 v1/particle/world/MolangWorldParticleRenderType.java 实现 1.20 的
 * {@code net.minecraft.client.particle.ParticleRenderType} 接口（{@code begin(BufferBuilder, TextureManager)}
 * /{@code end(Tesselator)}），由 {@code ParticleEngine} 在每个 RenderType 的绘制前后调用。
 * <b>1.7.10 的 {@code EffectRenderer} 没有「按 RenderType 分组」的概念</b>（只有 4 个固定图层，
 * 且整个图层共用一次 {@code Tessellator} 会话，中途换纹理会失效），因此这里：
 * <ul>
 * <li>不再 implements 任何接口，改为返回 mcshim {@link RenderType}（内部持有同样的
 * 纹理 + 混合/深度状态），由 {@code WorldEmitterManager} 的渲染 pass 用
 * {@code MultiBufferSource} 按 RenderType 分组批量提交；</li>
 * <li>上游 {@code begin(...)} 的 GL 状态设置逐项映射到 {@link RenderType.State}：
 * {@code depthMask(false) → depthWrite = false}、{@code blendFunc(SRC_ALPHA, ONE) → additive = true}、
 * {@code blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA) → translucent = true}；</li>
 * <li>{@code gameRenderer.lightTexture().turnOnLightLayer()} 在 1.7.10 由{@code OpenGlHelper
 * .setLightmapTextureCoords} 完成，且光照贴图坐标逐顶点写入，无需在此开启；</li>
 * <li>{@code DefaultVertexFormat.PARTICLE}（pos+uv+color+lightmap，无法线）在 1.7.10 无对应；
 * 1.7.10 的顶点固定为 8 槽位，这里用 {@code DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR}，等价。</li>
 * </ul>
 */
public final class MolangWorldParticleRenderType {

    private static final Map<String, RenderType> CACHE = new ConcurrentHashMap<>();

    private MolangWorldParticleRenderType() {}

    /**
     * 获取或创建指定材质+纹理的 RenderType 实例。
     */
    public static RenderType get(ParticleDescription.Material material, ResourceLocation texture) {
        String key = material.name() + ":" + texture;
        return CACHE.computeIfAbsent(key, k -> create(material, texture));
    }

    /**
     * 清除缓存（资源重载时调用）。
     */
    public static void clearCache() {
        CACHE.clear();
    }

    private static RenderType create(ParticleDescription.Material material, ResourceLocation texture) {
        RenderType.State state = new RenderType.State();
        state.texture = texture;
        state.cull = false;
        state.lightmap = true;
        state.lighting = false;
        switch (material) {
            case PARTICLES_OPAQUE -> {
                state.alphaTest = false;
                state.translucent = false;
                state.depthWrite = true;
            }
            case PARTICLES_ALPHA, PARTICLES_BLEND -> {
                state.alphaTest = false;
                state.translucent = true;
                state.depthWrite = true;
                state.blendSrc = GL11.GL_SRC_ALPHA;
                state.blendDst = GL11.GL_ONE_MINUS_SRC_ALPHA;
            }
            case PARTICLES_ADD -> {
                // 上游 depthMask(false) + blendFunc(SRC_ALPHA, ONE)
                state.alphaTest = false;
                state.translucent = true;
                state.additive = true;
                state.depthWrite = false;
            }
            case ENERGY_SWIRL, PARTICLES_EMISSIVE -> {
                // 上游 depthMask(false) + blendFunc(SRC_ALPHA, ONE)
                state.alphaTest = false;
                state.translucent = true;
                state.additive = true;
                state.depthWrite = false;
            }
        }
        return RenderType.create(
            "molang_world_particle_" + material.name()
                .toLowerCase(),
            DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR,
            VertexFormat.Mode.QUADS,
            1024,
            false,
            false,
            state);
    }

    @Override
    public String toString() {
        return "MolangWorldParticleRenderType";
    }
}
