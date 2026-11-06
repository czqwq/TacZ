// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/render/ParticleRenderType.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.mcshim.DefaultVertexFormat;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 粒子用的两种 {@link RenderType} 工厂：叠加（additive）与自发光（emissive）。
 *
 * <p>
 * TODO(L2): 上游 v1/particle/render/ParticleRenderType.java 继承 {@code RenderType} 以访问
 * protected 的 {@code RenderStateShard} 常量，并用
 * {@code CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)...createCompositeState()}
 * 组合出 RenderType。1.7.10 没有 shader / RenderStateShard / CompositeState（固定管线，无 shader 程序），
 * 因此本类：
 * <ul>
 * <li>不再继承 {@code RenderType}（mcshim 的 RenderType 构造器签名不兼容，且没有 protected 常量可借），
 * 改为纯静态工厂类；</li>
 * <li>用 mcshim {@link RenderType.State} 表达同样的 GL 状态：
 * {@code ADDITIVE_TRANSPARENCY → additive = true}、{@code NO_CULL → cull = false}、
 * {@code LIGHTMAP → lightmap = true}、{@code COLOR_WRITE → depthWrite = false}、
 * {@code COLOR_DEPTH_WRITE → depthWrite = true}；</li>
 * <li>shader 状态（{@code RENDERTYPE_ENTITY_TRANSLUCENT_SHADER} /
 * {@code ..._EMISSIVE_SHADER}）在 1.7.10 无对应物，降级为固定管线「禁用光照 + 顶点色」，
 * 自发光与叠加的差别只剩下是否写深度（与上游一致）；</li>
 * <li>{@code Util.memoize(...)} 在 1.7.10 不存在，改用 {@link ConcurrentHashMap} 做同样的
 * 「按纹理缓存」语义（1.20 的 Util.memoize 也是同一目的）。</li>
 * </ul>
 */
public final class ParticleRenderType {

    private static final Map<ResourceLocation, RenderType> ADDITIVE_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> EMISSIVE_CACHE = new ConcurrentHashMap<>();

    /**
     * 1.7.10 移植修正（SBM vendor）：{@code RenderType} 的名字必须带上贴图。
     *
     * <p>
     * 依据：{@code RenderType.create(...)} 直接 {@code new RenderType(...)}，<b>不</b>经过
     * {@code RenderType.CACHE}；而 {@code mcshim.RenderType#equals/hashCode}
     * （{@code mcshim/RenderType.java:738-753}）只比较 {@code name/format/mode/bufferSize}。
     * 若两种贴图都叫 {@code "particle_additive"}，它们就 {@code equals} ⇒
     * {@code MultiBufferSource.BufferSource#getBuffer} 的 {@code HashMap<RenderType, BufferBuilder>}
     * （{@code mcshim/MultiBufferSource.java:66-81}）会把第二种贴图的顶点塞进第一种贴图的
     * builder，最终 {@code endBatch()} 只按<b>第一个</b> {@code RenderType} 绑一次纹理
     * （{@code mcshim/RenderType.java:416-424}）⇒ <b>所有 additive/emissive 粒子都被画成第一张贴图</b>。
     * 把贴图拼进 name 即让不同贴图分属不同批次。
     */
    private static String nameOf(String kind, ResourceLocation texture) {
        return kind + ":" + texture;
    }

    private ParticleRenderType() {}

    public static RenderType additiveParticle(ResourceLocation texture) {
        return ADDITIVE_CACHE.computeIfAbsent(texture, ParticleRenderType::createAdditiveParticle);
    }

    public static RenderType emissiveParticle(ResourceLocation texture) {
        return EMISSIVE_CACHE.computeIfAbsent(texture, ParticleRenderType::createEmissiveParticle);
    }

    public static RenderType createAdditiveParticle(ResourceLocation texture) {
        RenderType.State state = new RenderType.State();
        state.texture = texture;
        state.additive = true;
        state.translucent = true;
        state.cull = false;
        state.lightmap = true;
        state.alphaTest = false;
        state.lighting = false;
        // 上游 COLOR_WRITE：只写颜色，不写深度。
        state.depthWrite = false;
        return RenderType.create(
            nameOf("particle_additive", texture),
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            state);
    }

    /**
     * 类似 TACZ 镭射的自发光 RenderType：NEW_ENTITY format + emissive shader + 叠加混合。
     * 自发光不依赖光照传递，且与枪体同 format，AR 加速路径下可与枪体同一加速层被模板剔除。
     */
    public static RenderType createEmissiveParticle(ResourceLocation texture) {
        RenderType.State state = new RenderType.State();
        state.texture = texture;
        state.additive = true;
        state.translucent = true;
        state.cull = false;
        state.lightmap = true;
        state.alphaTest = false;
        state.lighting = false;
        // 上游 COLOR_DEPTH_WRITE：颜色与深度都写。
        state.depthWrite = true;
        return RenderType.create(
            nameOf("particle_emissive", texture),
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            state);
    }
}
