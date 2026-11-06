// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/client/renderer/BedrockModelRenderTypes.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.mcshim.DefaultVertexFormat;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * poly mesh（Bedrock 多边形网格）用的 {@link RenderType} 工厂。
 *
 * <p>
 * TODO(L2): 上游继承 {@code RenderType} 以访问 protected 的 {@code RenderStateShard} 常量，并用
 * {@code CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)...} 组合渲染类型。
 * 1.7.10 没有 shader / RenderStateShard / CompositeState（固定管线），因此本类改为纯静态工厂：
 * <ul>
 * <li>{@code RENDERTYPE_ENTITY_CUTOUT_SHADER} → 固定管线「无混合 + alpha 裁剪」，
 * 对应 {@code RenderType.State} 的 {@code alphaTest = true, translucent = false}；</li>
 * <li>{@code NO_TRANSPARENCY → translucent = false}；{@code NO_CULL → cull = false}；
 * {@code LIGHTMAP → lightmap = true}；{@code OVERLAY} 在 1.7.10 顶点格式里没有槽位，忽略；</li>
 * <li>{@code Util.memoize(...)} 在 1.7.10 不存在，改用 {@link ConcurrentHashMap} 做同样的按纹理缓存。</li>
 * </ul>
 */
public final class BedrockModelRenderTypes {

    private static final Map<ResourceLocation, RenderType> POLY_MESH_CUTOUT = new ConcurrentHashMap<>();

    private BedrockModelRenderTypes() {}

    public static RenderType polyMeshCutout(ResourceLocation texture) {
        return POLY_MESH_CUTOUT.computeIfAbsent(texture, BedrockModelRenderTypes::createPolyMeshCutout);
    }

    private static RenderType createPolyMeshCutout(ResourceLocation texture) {
        RenderType.State state = new RenderType.State();
        state.texture = texture;
        // 上游 NO_TRANSPARENCY + alpha 裁剪（createCompositeState(true)）
        state.translucent = false;
        state.alphaTest = true;
        state.cull = false;
        state.lightmap = true;
        state.lighting = false;
        state.depthWrite = true;
        return RenderType.create(
            "bedrock_poly_mesh_cutout",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            256,
            true,
            false,
            state);
    }
}
