package com.tacz.guns.client.renderer.block;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.mcshim.DefaultVertexFormat;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 展示枪械「自发光」通道的材质类型：与 {@code RenderType#entityCutout} 共用同一张贴图与顶点格式，
 * 但关掉固定管线光照、并忽略顶点 lightmap，用来复现上游
 * {@code RENDERTYPE_ENERGY_SWIRL_SHADER} 的「不被场景亮度压暗」效果。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/renderer/block/DisplayGunRenderType.java}
 *
 * <h3>1.7.10 差异（本类必须整体重写）</h3>
 * <ul>
 * <li>上游 {@code extends RenderStateShard} 并用
 * {@code RenderType.create(name, format, mode, 256, true, false, CompositeState.builder()...createCompositeState(true))}
 * （上游 26-47 行）。1.7.10 <b>没有</b> {@code RenderStateShard} / {@code CompositeState} / shader，
 * 因此本类改 {@code extends com.tacz.guns.mcshim.RenderType}，用 shim 的 {@code State} 状态包
 * （公开字段）表达同样的状态组合，见 {@link #emissive(ResourceLocation)}。</li>
 * <li>上游的 {@code constructor(String name, Runnable setup, Runnable clear)} 在 1.7.10 无对应物：
 * shim 的 {@code RenderType} 状态由 {@code State} 字段描述、由 {@code setupRenderState()} 统一施加，
 * 不接受任意 Runnable。构造函数改为透传 shim 的受保护构造。</li>
 * </ul>
 */
public class DisplayGunRenderType extends RenderType {

    /** 上游 {@code CACHE}：同贴图同实例，这是 {@code BufferSource} 按类型分批的前提。 */
    private static final Map<ResourceLocation, RenderType> CACHE = new HashMap<>();

    protected DisplayGunRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
        boolean affectsCrumbling, boolean sortOnUpload, State state) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, state);
    }

    /**
     * 对应上游 {@code emissive(ResourceLocation)}：贴图相同即为同一材质，因此带缓存。
     *
     * <p>
     * // TODO(L2): 上游 DisplayGunRenderType.java:26-47 做的是
     * // {@code CompositeState.builder().setShaderState(RENDERTYPE_ENERGY_SWIRL_SHADER).setTextureState(...)
     * // .setTransparencyState(NO_TRANSPARENCY).setCullState(CULL).setTexturingState(identity uv)
     * // .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setOutputState(ITEM_ENTITY_TARGET)
     * // .setWriteMaskState(COLOR_DEPTH_WRITE)}；
     * // 1.7.10 无 shader / 无 RenderStateShard / 无 FBO 输出目标（ITEM_ENTITY_TARGET），
     * // 此处改用 mcshim.RenderType.State 近似：不透明 + 写深度 + 背面裁剪 + 关光照 + 忽略 lightmap；
     * // 「自发光」在固定管线上就等价于「不被 GL_LIGHTING 与 lightmap 压暗」，
     * // ITEM_ENTITY_TARGET 是 1.7.10 RenderItem 自己管的 framebuffer，本类不碰。
     */
    public static RenderType emissive(ResourceLocation texture) {
        return CACHE.computeIfAbsent(texture, DisplayGunRenderType::createEmissive);
    }

    private static RenderType createEmissive(ResourceLocation texture) {
        State state = new State();
        state.texture = texture;
        state.translucent = false; // 上游 NO_TRANSPARENCY
        state.cull = true; // 上游 CULL
        state.depthTest = true;
        state.depthWrite = true; // 上游 COLOR_DEPTH_WRITE
        state.alphaTest = false; // 上游 NO_TRANSPARENCY 不裁 alpha
        state.lighting = false; // 自发光：固定管线光照必须关掉
        state.lightmap = false; // 自发光：不乘顶点 lightmap
        return create(
            "tacz_display_gun_emissive",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            false,
            state);
    }
}
