// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/render/EmissiveMeshRenderTypes.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.mcshim.DefaultVertexFormat;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 带深度和 alpha 裁剪的发光材质；shader 不使用法线方向计算明暗。
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 上游 {@code EmissiveMeshRenderTypes} 继承 1.20 的 {@code RenderStateShard}，用
 * {@code RenderType.CompositeState.builder()...} 组合六个状态分片，并用 {@code Util.memoize(...)}
 * 按纹理缓存。1.7.10 <b>没有</b> {@code RenderStateShard} / {@code CompositeState} /
 * {@code Util} / {@code TextureStateShard} / {@code TexturingStateShard}（这些是 1.17+ 的
 * 「渲染状态分片」体系），也没有 shader。
 *
 * <p>
 * 因此这里改为纯静态工厂 + {@code mcshim RenderType.State}（见 {@code BedrockModelRenderTypes}
 * 的同类做法），逐项对照：
 * <ul>
 * <li>{@code RENDERTYPE_ENERGY_SWIRL_SHADER}：1.7.10 无 shader；它的作用只是「不做法线明暗」，
 * 在固定管线上等价于<b>关闭</b> {@code GL_LIGHTING}，由顶点色 + lightmap 决定外观 →
 * {@code state.lighting = false}；</li>
 * <li>{@code TextureStateShard(texture, false, false)} → {@code state.texture = texture}；</li>
 * <li>{@code NO_TRANSPARENCY} → {@code state.translucent = false}（混合关闭）；</li>
 * <li>{@code CULL / NO_CULL} → {@code state.cull}（QUADS 裁剪、TRIANGLES 不裁剪，与上游一致）；</li>
 * <li>{@code TexturingStateShard(identity, resetTextureMatrix, resetTextureMatrix)}：
 * 1.7.10 固定管线没有纹理矩阵，{@code RenderSystem#resetTextureMatrix()} 本身是 no-op → 忽略；</li>
 * <li>{@code LIGHTMAP} → {@code state.lightmap = true}；{@code OVERLAY} 在 1.7.10 顶点格式里
 * 没有槽位 → 忽略；</li>
 * <li>{@code COLOR_DEPTH_WRITE} → {@code state.depthWrite = true}；</li>
 * <li>{@code createCompositeState(true)}（带 alpha 裁剪）→ {@code state.alphaTest = true}；</li>
 * <li>{@code Util.memoize(...)} → {@link ConcurrentHashMap} 缓存，key 为「纹理 + 拓扑」。</li>
 * </ul>
 *
 * <h2>公开签名变化</h2>
 * <p>
 * 本类不再 {@code extends RenderStateShard}（1.7.10 无该类），但<b>保留同名构造器
 * {@code EmissiveMeshRenderTypes(String, Runnable, Runnable)}</b>，只是把上游「转交父类」改为
 * 存字段；{@link #create}/{@link #cutout} 的签名与语义完全不变。
 */
public class EmissiveMeshRenderTypes {

    /** 上游 {@code Util.memoize(texture -> create(texture, QUADS))} 的 1.7.10 替代。 */
    private static final Map<ResourceLocation, RenderType> QUADS = new ConcurrentHashMap<>();
    /** 同上，TRIANGLES 拓扑。 */
    private static final Map<ResourceLocation, RenderType> TRIANGLES = new ConcurrentHashMap<>();

    private final String name;
    private final Runnable setup;
    private final Runnable clear;

    public EmissiveMeshRenderTypes(String name, Runnable setup, Runnable clear) {
        this.name = name;
        this.setup = setup;
        this.clear = clear;
    }

    /** 上游构造器对应的两个访问器（1.20 里由 {@code RenderStateShard} 持有）。 */
    public String name() {
        return name;
    }

    /** 上游 {@code RenderStateShard#setupState}。 */
    public void setupState() {
        if (setup != null) setup.run();
    }

    /** 上游 {@code RenderStateShard#clearState}。 */
    public void clearState() {
        if (clear != null) clear.run();
    }

    public static RenderType cutout(ResourceLocation texture, VertexFormat.Mode mode) {
        return switch (mode) {
            case QUADS -> QUADS.computeIfAbsent(texture, key -> create(key, VertexFormat.Mode.QUADS));
            case TRIANGLES -> TRIANGLES.computeIfAbsent(texture, key -> create(key, VertexFormat.Mode.TRIANGLES));
            default -> throw new IllegalArgumentException("Unsupported emissive mode: " + mode);
        };
    }

    public static RenderType create(ResourceLocation texture, VertexFormat.Mode mode) {
        // TODO(L2): 上游 v2/client/mesh/render/EmissiveMeshRenderTypes.java:39 用 1.20 的
        // RenderType.create(..., CompositeState.builder()...)；1.7.10 无 RenderStateShard/CompositeState/
        // shader，改为 mcshim RenderType.create(..., RenderType.State)，逐项映射见类注释。
        RenderType.State state = new RenderType.State();
        state.texture = texture;
        state.translucent = false;
        state.alphaTest = true;
        state.cull = mode == VertexFormat.Mode.QUADS;
        state.lightmap = true;
        state.lighting = false;
        state.depthWrite = true;
        state.depthTest = true;
        return RenderType.create("sbm_emissive_cutout", DefaultVertexFormat.NEW_ENTITY, mode, 256, true, false, state);
    }

    /** 未使用但保留上游 {@code Function} 形状的辅助方法：把 {@link #cutout} 包装成 {@code Function}。 */
    public static Function<ResourceLocation, RenderType> cutoutFunction(VertexFormat.Mode mode) {
        return texture -> cutout(texture, mode);
    }
}
