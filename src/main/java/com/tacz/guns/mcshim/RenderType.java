package com.tacz.guns.mcshim;

import java.nio.FloatBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * 1.7.10 版的 {@code RenderType}：一个「<b>GL 状态包</b> + 顶点格式描述」。
 *
 * <p>
 * 1.20.1 的 {@code RenderType} 是「{@code RenderStateShard} 组合 + shader + 纹理 + 输出目标」的绘制管线描述；
 * 1.7.10 没有 shader/状态分片、只有全局固定管线状态，因此本类退化成一组「要绑的纹理 + 混合/裁剪/深度/alpha 测试」
 * 字段：{@link #setupRenderState()} 把它们翻译成 GL11/GL12/GL14 调用，{@link #clearRenderState()} 复原成「实体渲染前
 * 的常规状态」，{@link MultiBufferSource.BufferSource} 在 {@code endBatch()} 里对每个类型执行
 * {@code setupRenderState() → BufferUploader.draw() → clearRenderState()}。
 *
 * <p>
 * 行为差异：<b>没有 shader</b> —— 1.20 的 {@code entityCutout}/{@code entityTranslucent} 对应
 * {@code rendertype_entity_*} 等着色器，1.7.10 只能靠 {@code GL_ALPHA_TEST}/{@code GL_BLEND}/{@code glColor4f}
 * 近似，光照由 {@code GL_LIGHTING} 或逐顶点颜色承担（本类默认<b>关闭</b> {@code GL_LIGHTING}，与 1.20 entity shader
 * 一致）。<b>没有 {@code RenderStateShard}</b> ⇒ 上游 {@code DisplayGunRenderType} 之类的类必须改用本类静态工厂或
 * {@link #create}。<b>没有 FBO/输出目标切换</b>（{@code RenderItem} 自己管 framebuffer）。<b>没有面排序</b>
 * （{@code sortOnUpload} 只是存着）。{@code exceptionOutOfMemory}/{@code crumbling}/{@code outline} 未移植。
 *
 * <p>
 * <b>未提供 {@code energySwirl(ResourceLocation, float, float)}（有据降级，r7-model 转派登记）</b>：1.20 的
 * {@code energySwirl} 靠 {@code rendertype_energy_swirl} shader 做「UV 随时间滚动」；1.7.10 固定管线下由调用方用
 * {@code GL_TEXTURE} 矩阵等价实现（{@code EntityBulletRenderer} 的曳光弹/{@code MuzzleFlashRender} 已按此处理，
 * 见各自类注释的 TODO）。本类<b>不</b>提供该工厂，避免调用点误以为拿到了滚动 UV。
 *
 * <p>
 * 按纹理的静态工厂用 {@code ConcurrentHashMap} 缓存实例；{@code equals/hashCode} <b>不覆写</b>（与 1.20 同构，
 * 按实例身份比较）—— 详见类尾注释。
 */
public class RenderType {

    // ================================================================================================
    // 静态工厂
    // ================================================================================================

    private static final Map<String, RenderType> CACHE = new ConcurrentHashMap<>();

    /**
     * 不透明、带 alpha 裁剪、双面（不裁剪）的实体材质，对应 1.20 {@code entityCutoutNoCull}。
     *
     * <p>
     * {@code State.opaqueCutout(cull, polygonOffset)} 第 1 参是 cull、第 2 参是 polygonOffset；「带 Z 偏移」有独立的
     * {@link #entityCutoutNoCullZOffset} 工厂，故这里 {@code opaqueCutout(false, false)}。
     *
     * <p>
     * <b>cull 一律保持 false（双面）</b>：打开 cull 后模型的三角面绕序一旦与 {@code glFrontFace} 约定不一致就会
     * <b>整片消失</b>，{@code cull=false} 是它的安全超集（不缺面，最多多画背面）。这是<b>已登记的平台偏离</b>，
     * 要改必须在实机上逐模型验证绕序。
     */
    public static RenderType entityCutoutNoCull(ResourceLocation texture) {
        return entityCutoutNoCull(texture, true);
    }

    /** 对应 1.20 {@code entityCutoutNoCull(ResourceLocation, boolean affectsCrumbling)}。 */
    public static RenderType entityCutoutNoCull(ResourceLocation texture, boolean affectsCrumbling) {
        return cached(
            "entity_cutout_no_cull:" + texture + ":" + affectsCrumbling,
            texture,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            // 参数序：opaqueCutout(cull, polygonOffset) —— NoCull 且无 Z 偏移（原为 (false, true)，见上）
            State.opaqueCutout(false, false));
    }

    /**
     * 对应 1.20 {@code entityCutoutNoCullZOffset}。原实现 {@code opaqueCutout(true, true)} 把第 1 参（cull）写成了
     * {@code true}、与 {@code NoCull} 名字矛盾（该开的是第 2 参 polygonOffset），已改为
     * {@code opaqueCutout(false, true)}。当前全仓 0 调用点，属消除潜在陷阱。
     */
    public static RenderType entityCutoutNoCullZOffset(ResourceLocation texture) {
        return cached(
            "entity_cutout_no_cull_z_offset:" + texture,
            texture,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            // 参数序：opaqueCutout(cull, polygonOffset) —— NoCull + 开 Z 偏移（原为 (true, true)）
            State.opaqueCutout(false, true));
    }

    /** 对应 1.20 {@code itemEntityTranslucentCull}（掉落物半透明）。 */
    public static RenderType itemEntityTranslucentCull(ResourceLocation texture) {
        return cached(
            "item_entity_translucent_cull:" + texture,
            texture,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            State.translucent(false, true));
    }

    /** 对应 1.20 {@code entityTranslucentCull}。 */
    public static RenderType entityTranslucentCull(ResourceLocation texture) {
        return cached(
            "entity_translucent_cull:" + texture,
            texture,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            State.translucent(false, true));
    }

    /** 对应 1.20 {@code entityTranslucent(ResourceLocation)}。 */
    public static RenderType entityTranslucent(ResourceLocation texture) {
        return entityTranslucent(texture, true);
    }

    /** 对应 1.20 {@code entityTranslucent(ResourceLocation, boolean affectsCrumbling)}。 */
    public static RenderType entityTranslucent(ResourceLocation texture, boolean affectsCrumbling) {
        return cached(
            "entity_translucent:" + texture + ":" + affectsCrumbling,
            texture,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            State.translucent(false, false));
    }

    /** 对应 1.20 {@code entitySolid}（不透明、不裁剪、无 alpha 测试）。 */
    public static RenderType entitySolid(ResourceLocation texture) {
        return cached(
            "entity_solid:" + texture,
            texture,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            State.solid());
    }

    /** 对应 1.20 {@code entityCutout}（不透明 + alpha 裁剪 + 背面裁剪）。 */
    public static RenderType entityCutout(ResourceLocation texture) {
        return cached(
            "entity_cutout:" + texture,
            texture,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            State.opaqueCutout(false, false));
    }

    /** 对应 1.20 {@code entityNoOutline}。 */
    public static RenderType entityNoOutline(ResourceLocation texture) {
        return entityCutoutNoCull(texture);
    }

    /** 对应 1.20 {@code entityShadow}。 */
    public static RenderType entityShadow(ResourceLocation texture) {
        return cached(
            "entity_shadow:" + texture,
            texture,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            State.translucent(false, false));
    }

    /** 对应 1.20 {@code entityGlint}：附魔光效。1.7.10 无 glint 纹理，降级为半透明白色叠加。 */
    public static RenderType entityGlint() {
        return cached(
            "entity_glint",
            null,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            State.translucent(false, false));
    }

    /** 对应 1.20 {@code armorEntityGlint}。 */
    public static RenderType armorEntityGlint() {
        return entityGlint();
    }

    /** 对应 1.20 {@code translucent()}：无纹理的半透明材质（顶点色决定外观）。 */
    public static RenderType translucent() {
        return cached(
            "translucent",
            null,
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            State.translucent(true, false));
    }

    /** 对应 1.20 {@code translucentMovingBlock}。 */
    public static RenderType translucentMovingBlock() {
        return cached(
            "translucent_moving_block",
            null,
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            State.translucent(true, false));
    }

    /** 对应 1.20 {@code translucentMovingLevel()}。 */
    public static RenderType translucentMovingLevel() {
        return translucentMovingBlock();
    }

    /** 对应 1.20 {@code lines()}：线段，无纹理、顶点色。 */
    public static RenderType lines() {
        return cached(
            "lines",
            null,
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            State.lines(2.0F, true));
    }

    /** 对应 1.20 {@code debugLineStrip(double lineWidth)}。 */
    public static RenderType debugLineStrip(double lineWidth) {
        return cached(
            "debug_line_strip:" + lineWidth,
            null,
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.DEBUG_LINE_STRIP,
            State.lines((float) lineWidth, false));
    }

    /** 对应 1.20 {@code debugLine(double lineWidth)}。 */
    public static RenderType debugLine(double lineWidth) {
        return cached(
            "debug_line:" + lineWidth,
            null,
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.DEBUG_LINES,
            State.lines((float) lineWidth, false));
    }

    /** 对应 1.20 {@code debugFilledBox()}。 */
    public static RenderType debugFilledBox() {
        return cached(
            "debug_filled_box",
            null,
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_STRIP,
            State.lines(1.0F, false));
    }

    /** 对应 1.20 {@code eyes(ResourceLocation)}：末影人/蜘蛛眼发光。 */
    public static RenderType eyes(ResourceLocation texture) {
        return cached(
            "eyes:" + texture,
            texture,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            State.emissive());
    }

    /** 对应 1.20 {@code beaconBeam(ResourceLocation, boolean translucent)}。 */
    public static RenderType beaconBeam(ResourceLocation texture, boolean useTranslucentBlend) {
        return cached(
            "beacon_beam:" + texture + ":" + useTranslucentBlend,
            texture,
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            useTranslucentBlend ? State.emissiveTranslucent() : State.emissive());
    }

    /** 对应 1.20 {@code lightning()}：闪电（无纹理、加色混合）。 */
    public static RenderType lightning() {
        return cached("lightning", null, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, State.additive());
    }

    /** 对应 1.20 {@code waterMask()}。 */
    public static RenderType waterMask() {
        return cached("water_mask", null, DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, State.mask());
    }

    /** 对应 1.20 {@code text(ResourceLocation)}。 */
    public static RenderType text(ResourceLocation texture) {
        return cached(
            "text:" + texture,
            texture,
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            State.text(false));
    }

    /** 对应 1.20 {@code textSeeThrough(ResourceLocation)}。 */
    public static RenderType textSeeThrough(ResourceLocation texture) {
        return cached(
            "text_see_through:" + texture,
            texture,
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            State.text(true));
    }

    /** 对应 1.20 {@code textIntensity(ResourceLocation)}。 */
    public static RenderType textIntensity(ResourceLocation texture) {
        return text(texture);
    }

    /** 对应 1.20 {@code textIntensitySeeThrough(ResourceLocation)}。 */
    public static RenderType textIntensitySeeThrough(ResourceLocation texture) {
        return textSeeThrough(texture);
    }

    /** 对应 1.20 {@code gui()}：GUI 覆盖层（无深度测试）。 */
    public static RenderType gui() {
        return cached("gui", null, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, State.gui(false));
    }

    /** 对应 1.20 {@code guiOverlay()}：GUI 覆盖层（无深度测试、无深度写）。 */
    public static RenderType guiOverlay() {
        return cached(
            "gui_overlay",
            null,
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            State.gui(true));
    }

    /**
     * 1.20 {@code RenderType#create(String, VertexFormat, Mode, int, boolean, boolean, CompositeState)} 的<b>简化版</b>：
     * 用本类的 {@link State} 取代 1.20 的 {@code CompositeState}。上游 {@code DisplayGunRenderType} 用的是
     * {@code CompositeState.builder().setShaderState(...)} 那套 {@code RenderStateShard} API，它在 1.7.10
     * <b>不存在</b>，请改用本方法或直接使用静态工厂。
     */
    public static RenderType create(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
        boolean affectsCrumbling, boolean sortOnUpload, State state) {
        return new RenderType(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, state);
    }

    /**
     * 最常用的简化 {@code create}：只关心「纹理 + 是否半透明 + 是否裁剪 + 是否写深度」。
     */
    public static RenderType create(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
        boolean affectsCrumbling, boolean sortOnUpload, ResourceLocation texture, boolean translucent, boolean cull,
        boolean depthWrite) {
        State state = new State();
        state.texture = texture;
        state.translucent = translucent;
        state.cull = cull;
        state.depthWrite = depthWrite;
        state.depthTest = true;
        state.lightmap = true;
        state.alphaTest = !translucent;
        return create(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, state);
    }

    private static RenderType cached(String key, ResourceLocation texture, VertexFormat format, VertexFormat.Mode mode,
        State state) {
        return CACHE.computeIfAbsent(key, ignored -> {
            State copy = state.copy();
            copy.texture = texture;
            return new RenderType(key, format, mode, 256, true, false, copy);
        });
    }

    // ================================================================================================
    // 实例
    // ================================================================================================

    private final String name;
    private final VertexFormat format;
    private final VertexFormat.Mode mode;
    private final int bufferSize;
    private final boolean affectsCrumbling;
    private final boolean sortOnUpload;
    private final State state;

    /**
     * 进 {@link #setupRenderState()} 时快照的 GL 状态，{@link #clearRenderState()} 原样还原。
     *
     * <p>
     * 1.7.10 的固定管线状态是<b>全局</b>的，而 1.20 的 {@code RenderType} 状态是<b>括号作用域</b>。把
     * {@code clearRenderState()} 写成「强制恢复到一个固定基线」会污染外层：{@code EntityRenderer} 只在
     * {@code RenderGlobal#renderEntities} 之前调一次 {@code RenderHelper#enableStandardItemLighting()}
     * （{@code EntityRenderer.java:1297}），而 {@code RendererLivingEntity#doRender} 自己不再开光照 ⇒
     * {@code glDisable(GL_LIGHTING)} 会让之后渲染的所有实体（含玩家自己的盔甲层）失去方向光、变平变亮；
     * {@code glDisable(GL_ALPHA_TEST)} 会让后续 cutout 贴图不再裁剪；{@code glEnable(GL_CULL_FACE)} /
     * {@code glDepthMask(true)} 与调用方假设相反。因此改为「进入时快照、退出时还原」；缺快照的外部调用仍退回旧基线。
     */
    private final GlStateSnapshot savedState = new GlStateSnapshot();
    private boolean stateSaved;

    protected RenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
        boolean affectsCrumbling, boolean sortOnUpload, State state) {
        this.name = name;
        this.format = format;
        this.mode = mode;
        this.bufferSize = bufferSize;
        this.affectsCrumbling = affectsCrumbling;
        this.sortOnUpload = sortOnUpload;
        this.state = state == null ? new State() : state;
    }

    /** 此类型不等于 1.20 的 {@code >= 0} 表示「不透明」，1.7.10 用 {@link State#translucent} 表达。 */
    public String name() {
        return name;
    }

    /** 顶点格式（只有描述意义，见 {@link VertexFormat}）。 */
    public VertexFormat format() {
        return format;
    }

    /** 绘制模式（{@code Tessellator#startDrawing} 要的参数就是 {@code mode().glMode()}）。 */
    public VertexFormat.Mode mode() {
        return mode;
    }

    /** 1.20 用它预分配 VBO；1.7.10 只用于构造 {@link BufferBuilder}。 */
    public int bufferSize() {
        return bufferSize;
    }

    /** 是否影响方块破坏进度（1.20 语义，1.7.10 保留但未使用）。 */
    public boolean affectsCrumbling() {
        return affectsCrumbling;
    }

    /** 是否上传时排序（1.20 语义，1.7.10 未实现面排序）。 */
    public boolean sortOnUpload() {
        return sortOnUpload;
    }

    /** 这一类型要绑的纹理，{@code null} 表示不绑纹理。 */
    public ResourceLocation texture() {
        return state.texture;
    }

    /** 是否是半透明类型。 */
    public boolean isTranslucent() {
        return state.translucent;
    }

    // ================================================================================================
    // GL 状态应用
    // ================================================================================================

    /** 对应 1.20 {@code RenderType#setupRenderState()}（1.20 里是 {@code RenderStateShard} 组合）。 */
    public void setupRenderState() {
        this.savedState.capture();
        this.stateSaved = true;
        if (state.texture != null) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(state.texture);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        if (state.cull) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }

        if (state.depthTest) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        // 1.20 `WriteMaskStateShard` 语义（RenderStateShard.java:645-669）：`COLOR_DEPTH_WRITE=(true,true)`
        // 的 setup **什么都不做**；只有「不写颜色/不写深度」的类型才去 *关闭* 对应掩码，且 clearRenderState
        // 只在它关过的时候才恢复。这里**不能**恒写 `glDepthMask(state.depthWrite)`：
        // TACZ 上游 `BedrockAttachmentModel#renderOcularStencil`（:240-258）/`renderOcularAndDivision`（:271-305）
        // 依赖调用方 `RenderSystem.depthMask(false)` 在整个 stencil 写/INVERT 期间保持有效；恒写 true 会让
        // 目镜 stencil 几何写入枪模深度，随后 z=-90 的 INVERT 圆被深度测试整圆裁掉（r7 实机复核⑫证据：
        // `stencilBefore=1, stencilAfter=1, stencilAfterNoDepthTest=254`）⇒ 镜片不出现/黑镜。
        // 因此这里只在 `depthWrite == false` 时关闭深度写入，`true` 时保持调用方当前值不变。
        if (!state.depthWrite) {
            GL11.glDepthMask(false);
        }

        if (state.alphaTest) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        } else {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }

        if (state.translucent) {
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(
                state.blendSrc,
                state.blendDst,
                state.blendSrcAlpha != 0 ? state.blendSrcAlpha : state.blendSrc,
                state.blendDstAlpha != 0 ? state.blendDstAlpha : state.blendDst);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }

        if (state.additive) {
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        }

        if (state.lighting) {
            GL11.glEnable(GL11.GL_LIGHTING);
        } else {
            GL11.glDisable(GL11.GL_LIGHTING);
        }

        if (state.lineWidth > 0.0F) {
            GL11.glLineWidth(state.lineWidth);
        }

        if (state.polygonOffset) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(state.polygonOffsetFactor, state.polygonOffsetUnits);
        } else {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
    }

    /**
     * 对应 1.20 {@code RenderType#clearRenderState()}：把 {@link #setupRenderState()} 改过的 GL 状态<b>原样还原</b>。
     * 1.20 里这一步是「unbind 本类型的 RenderStateShard」由下一层状态包接管，1.7.10 没有分层状态包，只能靠自己
     * 快照/恢复；写成「强制恢复成某个固定基线」会污染外层（最严重是把 {@code GL_LIGHTING} 关掉，见 {@link #savedState}）。
     * 只有缺快照（没有配对 {@code setupRenderState()}）时才退回旧的固定基线。
     */
    public void clearRenderState() {
        if (this.stateSaved) {
            this.stateSaved = false;
            this.savedState.restore();
            return;
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** {@link #setupRenderState()} 的 1.20 旧别名。 */
    public void setup() {
        setupRenderState();
    }

    /** {@link #clearRenderState()} 的 1.20 旧别名。 */
    public void clear() {
        clearRenderState();
    }

    /**
     * 1.20 {@code RenderType#begin(int, VertexFormat)}（已废弃）的等价物：返回一个已 begin 好的新 {@link BufferBuilder}。
     * 1.20 里同一个 {@code RenderType} 有共享 builder，本类每调用一次都新建 —— 1.7.10 没有跨帧的 builder 生命周期。
     */
    @Deprecated
    public BufferBuilder begin(int drawMode, VertexFormat vertexFormat) {
        BufferBuilder builder = new BufferBuilder(4096);
        builder.begin(modeForGlMode(drawMode), vertexFormat == null ? format : vertexFormat);
        return builder;
    }

    /**
     * 1.20 {@code RenderType#end()}（已废弃）的等价物。<b>降级点</b>：1.20 的 {@code end()} 会把 builder 顶点 snap 成
     * {@code RenderedBuffer} 供 {@code BufferUploader} 使用，1.7.10 没有共享 builder ⇒ 本方法为 no-op。
     * 请改用 {@link #begin} 返回的 builder 的 {@code end()} + {@link BufferUploader#draw}。
     */
    @Deprecated
    public void end() {}

    private static VertexFormat.Mode modeForGlMode(int drawMode) {
        for (VertexFormat.Mode candidate : VertexFormat.Mode.values()) {
            if (candidate.glMode() == drawMode) {
                return candidate;
            }
        }
        return VertexFormat.Mode.QUADS;
    }

    @Override
    public String toString() {
        return name;
    }

    // ================================================================================================
    // GlStateSnapshot：setupRenderState ↔ clearRenderState 的括号还原
    // ================================================================================================

    /**
     * 一组固定管线 GL 状态的快照，让 {@link #setupRenderState()} / {@link #clearRenderState()} 成为严格配对的「括号」。
     * 覆盖 = {@link #setupRenderState()} 实际会改的每一项：{@code GL_TEXTURE_2D}、{@code GL_CULL_FACE}、
     * {@code GL_DEPTH_TEST}、{@code glDepthMask}、{@code GL_ALPHA_TEST}、{@code GL_BLEND}（含混合因子）、
     * {@code GL_LIGHTING}、{@code GL_POLYGON_OFFSET_FILL}、{@code glLineWidth}、{@code glColor4f} 当前颜色。
     * 不复原 {@code glAlphaFunc} 参考值（本类恒写 {@code GL_GREATER, 0.1}，即 1.7.10 原版实体渲染基线），
     * 也不复原 stencil / colorMask / 视口 —— 这些不是本类改写的项。
     */
    static final class GlStateSnapshot {

        private boolean texture2d;
        private boolean cull;
        private boolean depthTest;
        private boolean depthMask;
        private boolean alphaTest;
        private boolean blend;
        private int blendSrc;
        private int blendDst;
        private boolean lighting;
        private boolean polygonOffset;
        private float lineWidth;
        // LWJGL2 的 `GL11.glGetFloat(int, FloatBuffer)` 对所有 pname 都做「缓冲区容量 >= 16」的保守
        // 校验（`BufferChecks.checkBuffer(params, 16)`，见 LWJGL2 GL11.java:1358 调用处报错
        // "Number of remaining buffer elements is 4, must be at least 16"），即使 GL_CURRENT_COLOR
        // 只写 4 个分量。容量必须 >= 16，否则 HUD 热栏画枪（AnimateGeoItemRenderer → MultiBufferSource
        // → setupRenderState → capture）必崩（r8 实机 23:17 crash report 的证据）。
        private final FloatBuffer color = BufferUtils.createFloatBuffer(16);

        void capture() {
            this.texture2d = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            this.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            this.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            this.alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            this.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            this.blendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
            this.blendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);
            this.lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
            this.polygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
            this.lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
            this.color.clear();
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, this.color);
        }

        void restore() {
            toggle(GL11.GL_TEXTURE_2D, this.texture2d);
            toggle(GL11.GL_CULL_FACE, this.cull);
            toggle(GL11.GL_DEPTH_TEST, this.depthTest);
            GL11.glDepthMask(this.depthMask);
            toggle(GL11.GL_ALPHA_TEST, this.alphaTest);
            if (this.blend) {
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(this.blendSrc, this.blendDst, this.blendSrc, this.blendDst);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            toggle(GL11.GL_LIGHTING, this.lighting);
            toggle(GL11.GL_POLYGON_OFFSET_FILL, this.polygonOffset);
            GL11.glLineWidth(this.lineWidth);
            if (this.color.limit() >= 4) {
                GL11.glColor4f(this.color.get(0), this.color.get(1), this.color.get(2), this.color.get(3));
            }
        }

        private static void toggle(int cap, boolean enabled) {
            if (enabled) {
                GL11.glEnable(cap);
            } else {
                GL11.glDisable(cap);
            }
        }
    }

    // ================================================================================================
    // State：1.7.10 侧的「RenderStateShard 组合」替代物
    // ================================================================================================

    /**
     * 1.20 {@code RenderType.CompositeState} 的 1.7.10 替代物：一组 GL 状态开关。没有 {@code builder()} 链式 API
     * （那需要 {@code RenderStateShard} 类体系），直接改字段即可。
     */
    public static final class State {

        /** 纹理，{@code null} = 不绑纹理。 */
        public ResourceLocation texture;
        /** 是否半透明（开 {@code GL_BLEND}）。 */
        public boolean translucent;
        /** 是否背面裁剪。 */
        public boolean cull = true;
        /** 是否写深度。 */
        public boolean depthWrite = true;
        /** 是否做深度测试。 */
        public boolean depthTest = true;
        /** 是否做 alpha 裁剪（{@code glAlphaFunc(GL_GREATER, 0.1)}）。 */
        public boolean alphaTest = true;
        /** 是否启用固定管线光照。 */
        public boolean lighting;
        /** 是否为加色混合（闪电等）。 */
        public boolean additive;
        /** 是否启用多边形偏移。 */
        public boolean polygonOffset;
        /** 偏移系数。 */
        public float polygonOffsetFactor = -1.0F;
        /** 偏移单位。 */
        public float polygonOffsetUnits = -1.0F;
        /** 光照贴图（1.7.10 由顶点 lightmap 承担，这里只作语义标记）。 */
        public boolean lightmap = true;
        /** 线宽（{@code <= 0} 表示不改）。 */
        public float lineWidth;
        /** 混合源因子。 */
        public int blendSrc = GL11.GL_SRC_ALPHA;
        /** 混合目标因子。 */
        public int blendDst = GL11.GL_ONE_MINUS_SRC_ALPHA;
        /** 分离混合的 alpha 源因子（0 表示沿用 {@link #blendSrc}）。 */
        public int blendSrcAlpha;
        /** 分离混合的 alpha 目标因子（0 表示沿用 {@link #blendDst}）。 */
        public int blendDstAlpha;

        public State() {}

        public State copy() {
            State copy = new State();
            copy.texture = texture;
            copy.translucent = translucent;
            copy.cull = cull;
            copy.depthWrite = depthWrite;
            copy.depthTest = depthTest;
            copy.alphaTest = alphaTest;
            copy.lighting = lighting;
            copy.additive = additive;
            copy.polygonOffset = polygonOffset;
            copy.polygonOffsetFactor = polygonOffsetFactor;
            copy.polygonOffsetUnits = polygonOffsetUnits;
            copy.lightmap = lightmap;
            copy.lineWidth = lineWidth;
            copy.blendSrc = blendSrc;
            copy.blendDst = blendDst;
            copy.blendSrcAlpha = blendSrcAlpha;
            copy.blendDstAlpha = blendDstAlpha;
            return copy;
        }

        /** 不透明 + 不裁剪 + 无 alpha 测试。 */
        static State solid() {
            State state = new State();
            state.alphaTest = false;
            state.cull = false;
            return state;
        }

        /** 不透明 + alpha 裁剪。 */
        static State opaqueCutout(boolean cull, boolean polygonOffset) {
            State state = new State();
            state.cull = cull;
            state.alphaTest = true;
            state.polygonOffset = polygonOffset;
            return state;
        }

        /** 半透明 + alpha 裁剪。 */
        static State translucent(boolean cull, boolean alphaTest) {
            State state = new State();
            state.translucent = true;
            state.cull = cull;
            state.alphaTest = alphaTest;
            state.depthWrite = false;
            return state;
        }

        /** 线段。 */
        static State lines(float width, boolean depthTest) {
            State state = new State();
            state.alphaTest = false;
            state.translucent = true;
            state.depthWrite = false;
            state.depthTest = depthTest;
            state.cull = false;
            state.lightmap = false;
            state.lineWidth = width;
            state.blendSrc = GL11.GL_SRC_ALPHA;
            state.blendDst = GL11.GL_ONE_MINUS_SRC_ALPHA;
            return state;
        }

        /** 自发光（眼睛/信标，全亮 + 半透明）。 */
        static State emissive() {
            State state = new State();
            state.alphaTest = false;
            state.cull = false;
            state.lightmap = false;
            return state;
        }

        /** 自发光 + 半透明。 */
        static State emissiveTranslucent() {
            State state = emissive();
            state.translucent = true;
            state.depthWrite = false;
            state.blendSrc = GL11.GL_SRC_ALPHA;
            state.blendDst = GL11.GL_ONE;
            state.blendSrcAlpha = GL11.GL_ONE;
            state.blendDstAlpha = GL11.GL_ZERO;
            return state;
        }

        /** 加色混合。 */
        static State additive() {
            State state = new State();
            state.additive = true;
            state.translucent = true;
            state.alphaTest = false;
            state.cull = false;
            state.depthWrite = false;
            state.lightmap = false;
            return state;
        }

        /** 遮罩（只写深度、不写颜色）。 */
        static State mask() {
            State state = new State();
            state.alphaTest = false;
            state.cull = false;
            state.lightmap = false;
            state.depthWrite = true;
            return state;
        }

        /** 文字。 */
        static State text(boolean seeThrough) {
            State state = new State();
            state.alphaTest = false;
            state.translucent = true;
            state.cull = false;
            state.depthTest = !seeThrough;
            state.depthWrite = false;
            state.lightmap = true;
            return state;
        }

        /** GUI。 */
        static State gui(boolean overlay) {
            State state = new State();
            state.alphaTest = false;
            state.translucent = true;
            state.cull = false;
            state.depthTest = !overlay;
            state.depthWrite = false;
            state.lightmap = false;
            return state;
        }
    }

    // ================================================================================================
    // equals/hashCode：故意不覆写（与 1.20 一致，按对象身份比较）
    // ================================================================================================

    // 1.20.1 的 RenderType 不覆写 equals/hashCode（见 tmp/minecraft-src-1.20.1/.../RenderType.java），
    // MultiBufferSource.BufferSource 的分批因此按「RenderType 实例身份」而不是「name 相同」。
    // 本类早期按 name+bufferSize 判等，会让「同名不同纹理」的实例合并成一个批次并只绑第一个纹理
    // （例如 EmissiveMeshRenderTypes / MolangWorldParticleRenderType 用常量名 + 纹理状态）；
    // 现在与 1.20 同构：实例身份相等。按纹理缓存的静态工厂仍返回同一实例，分批效率不变。
}
