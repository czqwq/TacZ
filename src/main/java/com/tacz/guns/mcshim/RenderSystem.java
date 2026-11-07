package com.tacz.guns.mcshim;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.OpenGlHelper;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.systems.RenderSystem}。
 *
 * <p>
 * 1.7.10 没有 {@code RenderSystem}（1.17 才引入的「把 GL 调用排队到渲染线程」门面），渲染代码直接调
 * {@code org.lwjgl.opengl.GL11/GL12/GL13/GL14}。本类是一个<b>纯直通</b>门面：每个方法体就是一次或几次 GL 调用，
 * 不做状态缓存、不做线程排队。
 *
 * <p>
 * 行为差异：{@code assertOnRenderThread} / {@code isOnRenderThread} / {@code recordRenderCall} 分别降级为 no-op /
 * 恒返回 true / 立即执行（1.7.10 渲染只发生在客户端主线程，没有 RenderThread 概念）；{@code setShader} /
 * {@code getShader} —— 1.7.10 不能切换 shader program，非空 shader 一律记录警告后忽略；
 * {@code applyModelViewMatrix} / {@code getModelViewMatrix} / {@code getProjectionMatrix} /
 * {@code setProjectionMatrix} 直接读写 GL 矩阵栈（{@code glMatrixMode/glLoadMatrixf}），<b>会真实改变管线</b>
 * （比 1.20 的「只改 CPU 缓存」更重），调用点必须成对 restore；{@code getModelViewStack()} 返回一个「假」
 * {@link PoseStack}，只用于接住上游 {@code modelView.mulPoseMatrix(...)}，真正的效果在
 * {@link #applyModelViewMatrix()} 时才进 GL（本 shim 唯一语义不够的地方，已在方法注释标注）；
 * 4 参 {@code blendFunc} 委托 {@link OpenGlHelper#glBlendFunc(int, int, int, int)}（内部用
 * {@code GL14.glBlendFuncSeparate}）；{@code enableLighting}/{@code disableTexture} 等是 1.20 没有、但 1.7.10
 * 固定管线必需的补充。
 */
public final class RenderSystem {

    // ================================================================================================
    // 线程相关（1.7.10 无渲染线程）
    // ================================================================================================

    private RenderSystem() {}

    /** 对应 1.20 {@code RenderSystem#assertOnRenderThread()} / {@code assertOnRenderThreadOrInit()}：1.7.10 恒为 no-op。 */
    public static void assertOnRenderThread() {}

    /** 对应 1.20 {@code RenderSystem#assertOnRenderThreadOrInit()}：no-op。 */
    public static void assertOnRenderThreadOrInit() {}

    /** 对应 1.20 {@code RenderSystem#isOnRenderThread()}：1.7.10 恒返回 true（渲染只在客户端主线程发生）。 */
    public static boolean isOnRenderThread() {
        return true;
    }

    /** 对应 1.20 {@code RenderSystem#isOnRenderThreadOrInit()}：恒返回 true。 */
    public static boolean isOnRenderThreadOrInit() {
        return true;
    }

    /** 对应 1.20 {@code RenderSystem#recordRenderCall(RenderCall)}：1.7.10 立即执行。 */
    public static void recordRenderCall(Runnable renderCall) {
        renderCall.run();
    }

    // ================================================================================================
    // 颜色 / 混合 / 深度 / 裁剪
    // ================================================================================================

    /**
     * 对应 1.20 {@code RenderSystem#setShaderColor(float, float, float, float)} → {@code GL11.glColor4f}。
     *
     * <p>
     * <b>语义差异（重要）</b>：1.20 这是 shader 的 color uniform，会与顶点色<b>相乘</b>作用于所有绘制；
     * 1.7.10 固定管线只有「当前颜色」{@code glColor4f}，而 {@link BufferUploader} /
     * {@link TessellatorVertexConsumer} 绘制的几何体<b>总是</b>带逐顶点颜色（缺省白色），
     * 固定管线在颜色数组启用时会忽略当前颜色 ⇒ 本方法对这类几何体<b>没有染色效果</b>。
     * 需要半透明/染色时请把 alpha/颜色写进顶点色（或 {@link GuiGraphics#setColor}）；
     * 只有「不带颜色属性」的 1.7.10 {@code Tessellator} 直绘路径才吃 {@code glColor4f}。
     * 这是平台降级，已登记；已有调用点（如枪板预览 alpha）同时通过逐顶点 alpha 实现。
     */
    public static void setShaderColor(float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
    }

    /** 对应 1.20 {@code RenderSystem#enableBlend()} → {@code GL11.glEnable(GL_BLEND)}。 */
    public static void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }

    /** 对应 1.20 {@code RenderSystem#disableBlend()} → {@code GL11.glDisable(GL_BLEND)}。 */
    public static void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    /** 对应 1.20 {@code RenderSystem#blendFunc(int, int)} → {@code GL11.glBlendFunc}。 */
    public static void blendFunc(int srcFactor, int dstFactor) {
        GL11.glBlendFunc(srcFactor, dstFactor);
    }

    /**
     * 对应 1.20 {@code RenderSystem#blendFuncSeparate(int, int, int, int)} →
     * {@link OpenGlHelper#glBlendFunc(int, int, int, int)}（内部 {@code GL14.glBlendFuncSeparate}）。
     */
    public static void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        OpenGlHelper.glBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    /**
     * 对应 1.20 {@code RenderSystem#defaultBlendFunc()}。
     *
     * <p>
     * 1.20.1 的实现是 {@code blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO)}
     * —— <b>RGB 走 alpha 混合、Alpha 通道直接取源</b>（不是「RGB 与 Alpha 用同一组因子」）。
     * 1.7.10 的原版代码惯用 {@code OpenGlHelper.glBlendFunc(770, 771, 1, 0)}（即本方法），
     * 与 1.20 完全一致。
     */
    public static void defaultBlendFunc() {
        blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    /** 对应 1.20 {@code RenderSystem#depthMask(boolean)} → {@code GL11.glDepthMask}。 */
    public static void depthMask(boolean flag) {
        GL11.glDepthMask(flag);
    }

    /** 对应 1.20 {@code RenderSystem#enableDepthTest()}。 */
    public static void enableDepthTest() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** 对应 1.20 {@code RenderSystem#disableDepthTest()}。 */
    public static void disableDepthTest() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /** 对应 1.20 {@code RenderSystem#depthFunc(int)} → {@code GL11.glDepthFunc}。 */
    public static void depthFunc(int depthFunc) {
        GL11.glDepthFunc(depthFunc);
    }

    /** 对应 1.20 {@code RenderSystem#clearDepth(double)} → {@code GL11.glClearDepth}。 */
    public static void clearDepth(double depth) {
        GL11.glClearDepth(depth);
    }

    /** 对应 1.20 {@code RenderSystem#enableCull()}。 */
    public static void enableCull() {
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    /** 对应 1.20 {@code RenderSystem#disableCull()}。 */
    public static void disableCull() {
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    /** 对应 1.20 {@code RenderSystem#colorMask(boolean, boolean, boolean, boolean)} → {@code GL11.glColorMask}。 */
    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GL11.glColorMask(red, green, blue, alpha);
    }

    /** 对应 1.20 {@code RenderSystem#polygonOffset(float, float)} → {@code GL11.glPolygonOffset}。 */
    public static void polygonOffset(float factor, float units) {
        GL11.glPolygonOffset(factor, units);
    }

    /** 对应 1.20 {@code RenderSystem#enablePolygonOffset()}。 */
    public static void enablePolygonOffset() {
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    /** 对应 1.20 {@code RenderSystem#disablePolygonOffset()}。 */
    public static void disablePolygonOffset() {
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    /** 对应 1.20 {@code RenderSystem#lineWidth(float)} → {@code GL11.glLineWidth}。 */
    public static void lineWidth(float width) {
        GL11.glLineWidth(width);
    }

    /** 对应 1.20 {@code RenderSystem#enableScissor(int, int, int, int)}：1.7.10 需自行换算 GUI 缩放，此处用原始像素。 */
    public static void enableScissor(int x, int y, int width, int height) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, width, height);
    }

    /** 对应 1.20 {@code RenderSystem#disableScissor()}。 */
    public static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** 对应 1.20 {@code RenderSystem#clear(int, boolean)}：{@code GL11.glClear}。 */
    public static void clear(int mask, boolean getError) {
        GL11.glClear(mask);
        if (getError) {
            // 1.20 在 getError 时读 GL 错误；1.7.10 没有 glGetError 的托管封装，直接读一下丢弃即可。
            GL11.glGetError();
        }
    }

    /** 对应 1.20 {@code RenderSystem#clearColor(float, float, float, float)} → {@code GL11.glClearColor}。 */
    public static void clearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
    }

    /** 对应 1.20 {@code RenderSystem#clearStencil(int)}。 */
    public static void clearStencil(int stencil) {
        GL11.glClearStencil(stencil);
    }

    /** 对应 1.20 {@code RenderSystem#stencilFunc(int, int, int)} → {@code GL11.glStencilFunc}。 */
    public static void stencilFunc(int func, int ref, int mask) {
        GL11.glStencilFunc(func, ref, mask);
    }

    /** 对应 1.20 {@code RenderSystem#stencilMask(int)} → {@code GL11.glStencilMask}。 */
    public static void stencilMask(int mask) {
        GL11.glStencilMask(mask);
    }

    /** 对应 1.20 {@code RenderSystem#stencilOp(int, int, int)} → {@code GL11.glStencilOp}。 */
    public static void stencilOp(int sfail, int dpfail, int dppass) {
        GL11.glStencilOp(sfail, dpfail, dppass);
    }

    /** 对应 1.20 {@code RenderSystem#viewport(int, int, int, int)} → {@code GL11.glViewport}。 */
    public static void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    /**
     * 对应 1.20 {@code RenderSystem#setShaderTexture(int, int)} → 绑定到指定纹理单元（{@code GL13.glActiveTexture} +
     * {@code GL11.glBindTexture}）。
     */
    public static void setShaderTexture(int textureUnit, int textureId) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    /** 对应 1.20 {@code RenderSystem#getShaderTexture(int)}：返回当前单元绑定的纹理 id。 */
    public static int getShaderTexture(int textureUnit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    /** 对应 1.20 {@code RenderSystem#activeTexture(int)} → {@code GL13.glActiveTexture}。 */
    public static void activeTexture(int texture) {
        GL13.glActiveTexture(texture);
    }

    /** 对应 1.20 {@code RenderSystem#enableTexture()}：1.7.10 固定管线必需。 */
    public static void enableTexture() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** 对应 1.20 {@code RenderSystem#disableTexture()}：1.7.10 固定管线必需。 */
    public static void disableTexture() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    /** 对应 1.20 {@code RenderSystem#enableLighting()}（1.20 由 shader 处理光照，1.7.10 是固定管线 GL_LIGHTING）。 */
    public static void enableLighting() {
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    /** 对应 1.20 {@code RenderSystem#disableLighting()}。 */
    public static void disableLighting() {
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    /** 对应 1.20 {@code RenderSystem#enableColorLogicOp()} / {@code GL11.GL_COLOR_LOGIC_OP}。 */
    public static void enableColorLogicOp() {
        GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
    }

    /** 对应 1.20 {@code RenderSystem#disableColorLogicOp()}。 */
    public static void disableColorLogicOp() {
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
    }

    /** 对应 1.20 {@code RenderSystem#logicOp(int)} → {@code GL11.glLogicOp}。 */
    public static void logicOp(int op) {
        GL11.glLogicOp(op);
    }

    /**
     * 对应 1.20 {@code RenderSystem#enableWireframe()} / {@code polygonMode(GL_LINE)}（GL12 需要 GL11.glPolygonMode 的
     * desktop 版本）。
     */
    public static void enableWireframe() {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
    }

    /** 对应 1.20 {@code RenderSystem#disableWireframe()}。 */
    public static void disableWireframe() {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
    }

    /** 对应 1.20 {@code RenderSystem#setShaderLightDirections}：1.7.10 无 shader，恒 no-op。 */
    public static void setShaderLightDirections(Object... directions) {
        // 1.7.10 无 shader program，无光照方向可设。
    }

    // ================================================================================================
    // 矩阵（1.7.10 直接操作 GL 矩阵栈）
    // ================================================================================================

    /** 1.7.10 侧「假」的 modelview 栈，仅用于承接上游 {@code modelView.mulPoseMatrix(...)}。 */
    private static PoseStack MODEL_VIEW = new PoseStack();

    private static final FloatBuffer MODEL_VIEW_CACHE = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION_CACHE = BufferUtils.createFloatBuffer(16);

    /**
     * 对应 1.20 {@code RenderSystem#getModelViewStack()}。
     *
     * <p>
     * <b>降级点</b>：1.20 返回的是渲染线程持有的真实 modelview 栈；1.7.10 的 modelview 在 GL 里，
     * 没法在不打断当前矩阵模式的情况下读出来，所以这里返回一个<b>与 GL 脱钩</b>的 {@link PoseStack}。
     * 上游典型用法 {@code RenderSystem.getModelViewStack().pushPose(); ... mulPoseMatrix(m); ... }
     * 需要改写为 {@code PoseStack p = RenderSystem.getModelViewStack(); p.pushToGL(); ... p.popFromGL();}，
     * 见 {@link #applyModelViewMatrix()}。
     */
    public static PoseStack getModelViewStack() {
        return MODEL_VIEW;
    }

    /** 把 {@link #getModelViewStack()} 的栈顶矩阵乘进 GL_MODELVIEW。 */
    public static void applyModelViewMatrix() {
        int previous = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glMultMatrix(MODEL_VIEW.stackTopAsFloatBuffer());
        GL11.glMatrixMode(previous);
    }

    /**
     * 对应 1.20 {@code RenderSystem#getModelViewMatrix()}：读取当前 GL_MODELVIEW。
     *
     * <p>
     * <b>1.7.10 坑（实测崩溃：{@code BufferUnderflowException}）</b>：LWJGL2 的
     * {@code GL11#glGetFloat(int, FloatBuffer)} <b>只把数据写到 buffer 当前位置、不推进 position</b>
     * （vanilla {@code ClippingHelperImpl#init} 因此写成 {@code clear() → glGetFloat → flip().limit(16)}，
     * 见 {@code build/rfg/minecraft-src/java/net/minecraft/client/renderer/culling/ClippingHelperImpl.java:42-50}）。
     * 只调 {@code flip()} 会把 limit 设成 position=0 ⇒ 返回空 buffer，调用方 {@code get(float[16])} 直接崩。
     * 这里照抄 vanilla 的 {@code flip().limit(16)}，两种语义（推不推进 position）下都能得到 16 个有效元素。
     *
     * @return 只读快照（长度 16，列主序）
     */
    public static FloatBuffer getModelViewMatrix() {
        int previous = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        MODEL_VIEW_CACHE.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW_CACHE);
        MODEL_VIEW_CACHE.flip();
        MODEL_VIEW_CACHE.limit(16);
        GL11.glMatrixMode(previous);
        return MODEL_VIEW_CACHE;
    }

    /**
     * 对应 1.20 {@code RenderSystem#getProjectionMatrix()}：读取当前 GL_PROJECTION。
     *
     * <p>
     * buffer position 语义同 {@link #getModelViewMatrix()}（{@code flip().limit(16)}）。
     */
    public static FloatBuffer getProjectionMatrix() {
        int previous = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        PROJECTION_CACHE.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION_CACHE);
        PROJECTION_CACHE.flip();
        PROJECTION_CACHE.limit(16);
        GL11.glMatrixMode(previous);
        return PROJECTION_CACHE;
    }

    /**
     * 对应 1.20 {@code RenderSystem#setProjectionMatrix(Matrix4f)}：写入 GL_PROJECTION。
     */
    public static void setProjectionMatrix(Matrix4f matrix, boolean unused) {
        float[] values = new float[16];
        matrix.get(values);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(values);
        buffer.flip();
        int previous = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrix(buffer);
        GL11.glMatrixMode(previous);
    }

    /** 对应 1.20 {@code RenderSystem#resetTextureMatrix()}：1.7.10 固定管线无纹理矩阵需求，no-op。 */
    public static void resetTextureMatrix() {}

    /** 对应 1.20 {@code RenderSystem#initRenderer()}：1.7.10 无需初始化 blase3d，no-op。 */
    public static void initRenderer() {}

    /** 对应 1.20 {@code RenderSystem#replayQueue()}：1.7.10 无队列，no-op。 */
    public static void replayQueue() {}

    /**
     * 对应 1.20 {@code RenderSystem#setShader(Supplier)} / {@code setShader(ShaderInstance)}。
     *
     * <p>
     * <b>降级点</b>：1.7.10 无 ShaderInstance。这里接受 {@code Object} 以便上游调用点至少能编译，
     * 运行期不切换任何 GL program。需要用 GLSL 的场景请走 {@code client/renderer/**} 里自建的着色器管理。
     */
    public static void setShader(Object shader) {
        // TODO(port): 1.20 RenderSystem#setShader —— 1.7.10 无 ShaderInstance，此处降级为 no-op
    }

    /**
     * 对应 1.20 {@code RenderSystem#getShader()}：1.7.10 恒返回 {@code null}。
     */
    public static Object getShader() {
        return null;
    }

    /**
     * 对应 1.20 {@code RenderSystem#getVertexSorting()}。
     *
     * <p>
     * <b>降级点</b>：1.7.10 的 {@code Tessellator} 用 {@code QuadComparator} 做面排序，
     * 没有可选的 {@code VertexSorting} 策略，恒返回 {@code null}。
     */
    public static Object getVertexSorting() {
        return null;
    }
}
