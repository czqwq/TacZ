package com.tacz.guns.util;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 渲染相关的小工具（模板测试开关 + OptiFine 探测）。
 *
 * <p>
 * <b>移植说明（本文件是重灾区，请务必读完）</b>：
 * <ul>
 * <li>上游 {@code util/RenderHelper.java} 几乎全部是 <b>1.20 渲染管线</b>：
 * {@code PoseStack}、{@code MultiBufferSource}、{@code RenderType}、{@code VertexConsumer}、
 * {@code Tesselator.getInstance().getBuilder()}、{@code BufferUploader}、
 * {@code com.mojang.blaze3d.systems.RenderSystem}、{@code GlStateManager}、
 * {@code EntityRenderDispatcher}/{@code PlayerRenderer}、{@code ItemDisplayContext}、
 * {@code HumanoidArm}、{@code LocalPlayer}、{@code AnimationRenderContext}/{@code PlayerModelRenderState}
 * （client 包）、{@code ARCompat}/{@code OculusCompat}（compat 包）。这些类型在 1.7.10
 * <b>全部不存在</b>（渲染走 OpenGL 固定管线 + {@code Tessellator}），且本包（util）按 L1 编译边界
 * <b>不允许</b>编译期依赖 {@code com.tacz.guns.client.*} 与 mcshim 的渲染外壳类型。</li>
 * <li>因此本类只保留<b>能独立于渲染管线表达</b>的部分：
 * {@link #enableItemEntityStencilTest()} / {@link #disableItemEntityStencilTest()} /
 * {@link #isOptifineInstalled()}。其余方法<b>整体删除</b>，
 * 由 client 层（{@code client/renderer}、{@code client/gui}）在移植时按各自场景重写。
 * 下面列出被删除方法与其在 1.7.10 的落点，避免后面接手的同学找不到：</li>
 * </ul>
 *
 * <pre>
 * 上游方法                                            1.7.10 落点
 * ----------------------------------------------------------------------------------------------
 * blit(PoseStack, float x, float y, ...)               client/gui 用 Tessellator + GL11 固定管线自己实现
 * innerBlit(PoseStack/Matrix4f, ...)                   同上（没有 VertexConsumer/BufferUploader）
 * getBufferSource(ItemDisplayContext)                  client 层自建「按 RenderType 分桶」的缓冲池；
 *                                                      mcshim.MultiBufferSource 由 client 包使用，util 不得引用
 * renderFirstPersonArm(LocalPlayer, HumanoidArm, ...)  client/renderer 直接调 RendererLivingEntity#renderRightHand
 * endBatch(MultiBufferSource[, RenderType])            client 层自己 flush（1.7.10 没有 batch 概念）
 * </pre>
 *
 * <p>
 * 上游 {@code enableItemEntityStencilTest()} 的两条分支：
 * <ul>
 * <li>OptiFine 分支：1.20 里靠 {@code GL30.glGetFramebufferAttachmentParameteri} 把深度纹理
 * 重新挂成 {@code GL_DEPTH24_STENCIL8}。1.7.10 的 OptiFine 自己管理 framebuffer/shader 管线，
 * 这段代码 <b>没有可移植性</b>：本类只保留「OptiFine 已安装」的探测，
 * {@code client/renderer/scope/MainFramebufferStencil} 见到 OptiFine 会永久回退（不覆盖其 FBO）。</li>
 * <li>非 OptiFine 分支：{@code Minecraft.getInstance().getMainRenderTarget().enableStencil()}。
 * 1.7.10 主 FBO 默认没有 stencil 附件，由 {@code client/renderer/scope/MainFramebufferStencil} 在运行期
 * 补挂 {@code GL_DEPTH24_STENCIL8}（等价 Forge 1.20.1 {@code RenderTarget#enableStencil}，
 * 见 {@code tmp/MinecraftForge-1.20.1/patches/.../RenderTarget.java.patch:34-55}）；补挂失败才降级。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class RenderHelper {

    private static final boolean IS_OPTIFINE_INSTALLED = isClassFound("net.optifine.Config");

    private RenderHelper() {}

    /** OptiFine 是否已安装（等价上游 {@code IS_OPTIFINE_INSTALLED}）。 */
    public static boolean isOptifineInstalled() {
        return IS_OPTIFINE_INSTALLED;
    }

    /**
     * 开启「物品实体渲染用的模板测试」。
     *
     * <p>
     * 上游此处会保证主渲染目标有 stencil 缓冲（非 OptiFine 分支调
     * {@code MainRenderTarget#enableStencil()}）。1.7.10 的对应实现是
     * {@code client/renderer/scope/MainFramebufferStencil}：默认主 FBO 无 stencil 时，在运行期给它补挂
     * {@code GL_DEPTH24_STENCIL8}（等价 Forge 1.20.1 {@code RenderTarget#enableStencil}）；
     * 补挂失败（GL &lt; 3.0 / core profile / OptiFine / 深度附件已被光影接管）才降级。
     * 本方法只负责开关 {@code GL_STENCIL_TEST}，缓冲由上述 client 层在 {@code renderWorld} 前确保。
     */
    public static void enableItemEntityStencilTest() {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
    }

    public static void disableItemEntityStencilTest() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private static boolean isClassFound(String className) {
        try {
            Class.forName(
                className,
                false,
                Thread.currentThread()
                    .getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
