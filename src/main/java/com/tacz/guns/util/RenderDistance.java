package com.tacz.guns.util;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.tacz.guns.config.client.RenderConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 判断「当前绘制点离镜头够不够近」，用于在远处切换低模（LOD）。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>上游 {@code inRenderHighPolyModelDistance(PoseStack)} 从
 * {@code poseStack.last().pose()}（JOML {@code Matrix4f}）读平移量 {@code m30/m31/m32}，
 * 与 {@code RenderConfig.GUN_LOD_RENDER_DISTANCE} 平方比较。</li>
 * <li>1.7.10 <b>没有 {@code PoseStack}/{@code Matrix4f} 渲染栈</b>（渲染走 OpenGL 固定管线，
 * 变换直接压在 {@code GL_MODELVIEW} 上）。因此：<b>形参保留为 {@link Object}</b>
 * （只为让上游调用点 {@code inRenderHighPolyModelDistance(poseStack)} 原样编译），
 * 实际数值改从 {@code GL_MODELVIEW_MATRIX} 的平移分量（元素 12/13/14）读取 ——
 * 这正是 1.7.10 管线里与上游 {@code poseStack.last().pose()} 等价的量。</li>
 * <li>{@code ScreenItemVboContext.isActive()}（client 类，util 不允许编译期依赖）→
 * 本类的静态钩子 {@link #setScreenDrawActive(boolean)}：
 * 「界面里正在主动绘制物品」时由 client 层置为 {@code true}，
 * 语义与上游「当前界面绘制绕过 LOD」完全一致。</li>
 * <li>{@code net.minecraftforge.api.distmarker.@OnlyIn(Dist.CLIENT)} → {@link SideOnly} + {@link Side#CLIENT}。</li>
 * <li>降级点：上游能精确拿到「绘制该模型时」的矩阵；1.7.10 只能在调用时刻读当前
 * {@code GL_MODELVIEW}。若调用点在自己 push 变换之前就调用本方法，结果会偏大/偏小，
 * 因此约定<b>调用点必须在已应用模型位移之后调用</b>（client 层移植时注意）。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class RenderDistance {

    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    /** 等价上游 {@code ScreenItemVboContext.isActive()}。 */
    private static volatile boolean screenDrawActive;

    private RenderDistance() {}

    /** 由 client 层在「界面主动绘制物品」期间置位（等价上游 {@code ScreenItemVboContext}）。 */
    public static void setScreenDrawActive(boolean active) {
        screenDrawActive = active;
    }

    public static boolean isScreenDrawActive() {
        return screenDrawActive;
    }

    public static boolean inRenderHighPolyModelDistance(Object poseStack) {
        // Only the active screen draw bypasses LOD; an open screen still renders the world first.
        if (screenDrawActive) {
            return true;
        }
        int distance = RenderConfig.GUN_LOD_RENDER_DISTANCE.get();
        if (distance <= 0) {
            return false;
        }
        double viewDistance = viewDistanceSq();
        return viewDistance < (double) distance * (double) distance;
    }

    /** 无参重载（1.7.10 侧不需要矩阵栈时可直接调用）。 */
    public static boolean inRenderHighPolyModelDistance() {
        return inRenderHighPolyModelDistance((Object) null);
    }

    /** 读取当前 {@code GL_MODELVIEW} 平移量的平方（等价 {@code m30² + m31² + m32²}）。 */
    private static double viewDistanceSq() {
        MODELVIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        double x = MODELVIEW.get(12);
        double y = MODELVIEW.get(13);
        double z = MODELVIEW.get(14);
        return x * x + y * y + z * z;
    }
}
