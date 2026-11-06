package com.tacz.guns.mcshim;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.platform.GlStateManager}（<b>仅上游实际用到的子集</b>）。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 本类在 1.7.10 侧<b>不存在</b>（它是 1.8 引入的「带状态缓存的 GL 门面」）。
 * 1.7.10 直接调 {@code org.lwjgl.opengl.GL11/GL12/GL14/GL15/GL30}，因此本类的
 * {@code _xxx} 方法全部是<b>直通</b>，不做状态缓存 —— 语义上等价于 1.20 关闭状态缓存的情形。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li><b>没有状态缓存</b>：1.20 的 {@code GlStateManager} 会缓存 blend/depth/cull 等状态，
 * 连续设同一个值不重复发 GL 调用。本类每次都真发。功能等价，性能略差。</li>
 * <li>{@link SourceFactor}/{@link DestFactor} 是 1.20 的混合因子枚举，本类提供同名枚举，
 * {@code glValue()} 就是 {@code GL11} 常量，可直接传给
 * {@link RenderSystem#blendFunc(int, int)}。</li>
 * <li>{@code _pushMatrix/_popMatrix} 请直接改用 {@code GL11.glPushMatrix()/glPopMatrix()}，
 * 或 {@link PoseStack#pushToGL()}/{@link PoseStack#popFromGL()}；本类也提供同名直通方法。</li>
 * <li>1.7.10 的 LWJGL2 <b>没有</b> {@code GL30} 的部分函数绑定（VAO/FBO 的
 * {@code _glBindVertexArray} 等）；本类不做实现，调用方需要时直接
 * {@code GL30.glBindVertexArray(...)}（LWJGL2 的 {@code org.lwjgl.opengl.GL30} 有这些函数）。</li>
 * </ul>
 */
public final class GlStateManager {

    private GlStateManager() {}

    /** 对应 1.20 {@code GlStateManager.SourceFactor}。 */
    public enum SourceFactor {

        ZERO(GL11.GL_ZERO),
        ONE(GL11.GL_ONE),
        SRC_COLOR(GL11.GL_SRC_COLOR),
        ONE_MINUS_SRC_COLOR(GL11.GL_ONE_MINUS_SRC_COLOR),
        SRC_ALPHA(GL11.GL_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA),
        DST_ALPHA(GL11.GL_DST_ALPHA),
        ONE_MINUS_DST_ALPHA(GL11.GL_ONE_MINUS_DST_ALPHA),
        DST_COLOR(GL11.GL_DST_COLOR),
        ONE_MINUS_DST_COLOR(GL11.GL_ONE_MINUS_DST_COLOR);

        private final int glValue;

        SourceFactor(int glValue) {
            this.glValue = glValue;
        }

        public int glValue() {
            return glValue;
        }
    }

    /** 对应 1.20 {@code GlStateManager.DestFactor}。 */
    public enum DestFactor {

        ZERO(GL11.GL_ZERO),
        ONE(GL11.GL_ONE),
        SRC_COLOR(GL11.GL_SRC_COLOR),
        ONE_MINUS_SRC_COLOR(GL11.GL_ONE_MINUS_SRC_COLOR),
        SRC_ALPHA(GL11.GL_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA),
        DST_ALPHA(GL11.GL_DST_ALPHA),
        ONE_MINUS_DST_ALPHA(GL11.GL_ONE_MINUS_DST_ALPHA),
        DST_COLOR(GL11.GL_DST_COLOR),
        ONE_MINUS_DST_COLOR(GL11.GL_ONE_MINUS_DST_COLOR),
        CONSTANT_ALPHA(GL11.GL_CONSTANT_ALPHA),
        ONE_MINUS_CONSTANT_ALPHA(GL11.GL_ONE_MINUS_CONSTANT_ALPHA);

        private final int glValue;

        DestFactor(int glValue) {
            this.glValue = glValue;
        }

        public int glValue() {
            return glValue;
        }
    }

    /** 直通 {@code GL11.glPushMatrix()}。 */
    public static void _pushMatrix() {
        GL11.glPushMatrix();
    }

    /** 直通 {@code GL11.glPopMatrix()}。 */
    public static void _popMatrix() {
        GL11.glPopMatrix();
    }

    /** 直通 {@code GL11.glBindTexture(GL_TEXTURE_2D, id)}。 */
    public static void _bindTexture(int textureId) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    /** 返回当前激活纹理单元（{@code GL_TEXTURE0} 起算的绝对值），直通 {@code glGetInteger(GL_ACTIVE_TEXTURE)}。 */
    public static int _getActiveTexture() {
        return GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    }

    /** 直通 {@code GL11.glEnable(GL_SCISSOR_TEST)}。 */
    public static void _enableScissorTest() {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    /** 直通 {@code GL11.glDisable(GL_SCISSOR_TEST)}。 */
    public static void _disableScissorTest() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** 直通 {@code GL11.glEnable(GL_DEPTH_TEST)}。 */
    public static void _enableDepthTest() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** 直通 {@code GL11.glDisable(GL_DEPTH_TEST)}。 */
    public static void _disableDepthTest() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    /** 直通 {@code GL11.glEnable(GL_BLEND)}。 */
    public static void _enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }

    /** 直通 {@code GL11.glDisable(GL_BLEND)}。 */
    public static void _disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    /** 直通 {@code GL11.glEnable(GL_CULL_FACE)}。 */
    public static void _enableCull() {
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    /** 直通 {@code GL11.glDisable(GL_CULL_FACE)}。 */
    public static void _disableCull() {
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    /** 直通 {@code GL11.glTexImage2D}。 */
    public static void _texImage2D(int target, int level, int internalFormat, int width, int height, int border,
        int format, int type, ByteBuffer pixels) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    /** 直通 {@code GL11.glTexSubImage2D}。 */
    public static void _texSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height,
        int format, int type, ByteBuffer pixels) {
        GL11.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
    }

    /** 直通 {@code GL11.glCopyTexSubImage2D}。 */
    public static void _glCopyTexSubImage2D(int target, int level, int xOffset, int yOffset, int x, int y, int width,
        int height) {
        GL11.glCopyTexSubImage2D(target, level, xOffset, yOffset, x, y, width, height);
    }

    /** 直通 {@code GL30.glBindFramebuffer}（LWJGL2 的 {@code org.lwjgl.opengl.GL30}）。 */
    public static void _glBindFramebuffer(int target, int framebuffer) {
        GL30.glBindFramebuffer(target, framebuffer);
    }

    /** 直通 {@code GL30.glBindRenderbuffer}。 */
    public static void _glBindRenderbuffer(int target, int renderbuffer) {
        GL30.glBindRenderbuffer(target, renderbuffer);
    }

    /** 直通 {@code GL30.glFramebufferTexture2D}。 */
    public static void _glFramebufferTexture2D(int target, int attachment, int textureTarget, int textureId,
        int level) {
        GL30.glFramebufferTexture2D(target, attachment, textureTarget, textureId, level);
    }

    /** 直通 {@code GL30.glBlitFramebuffer}。 */
    public static void _glBlitFrameBuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1,
        int dstY1, int mask, int filter) {
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    /** 直通 {@code GL30.glBindVertexArray}。 */
    public static void _glBindVertexArray(int array) {
        GL30.glBindVertexArray(array);
    }

    /** 直通 {@code GL15.glBindBuffer}。 */
    public static void _glBindBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }

    /** 直通 {@code GL11.glColor4f}。 */
    public static void _color4f(float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
    }

    /** 直通 {@code GL11.glDepthMask}。 */
    public static void _depthMask(boolean flag) {
        GL11.glDepthMask(flag);
    }

    /** 直通 {@code GL11.glEnable(GL_ALPHA_TEST)}。 */
    public static void _enableAlphaTest() {
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    /** 直通 {@code GL11.glDisable(GL_ALPHA_TEST)}。 */
    public static void _disableAlphaTest() {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
    }

    /** 直通 {@code GL11.glAlphaFunc}。 */
    public static void _alphaFunc(int func, float reference) {
        GL11.glAlphaFunc(func, reference);
    }
}
