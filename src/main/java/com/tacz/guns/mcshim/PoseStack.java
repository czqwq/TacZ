package com.tacz.guns.mcshim;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.renderer.Tessellator;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * 对应 1.20.1 的 {@code com.mojang.blaze3d.vertex.PoseStack}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 没有 JOML 也没有矩阵栈的概念，渲染全靠 OpenGL 固定管线的 GL_MODELVIEW 栈。
 * 本类是纯 CPU 侧的 JOML 4x4 矩阵栈（{@link Matrix4f}）＋一个法线矩阵栈（{@link Matrix3f}），
 * <b>不</b>接管 GL 状态。把栈顶矩阵送进固定管线有两个途径：
 * <ul>
 * <li>{@link #applyToRender()} / {@link #applyToModelView()}：内部 {@code GL11.glMultMatrix}，
 * 把栈顶矩阵乘进当前的 GL_MODELVIEW（对应 1.20 里 PoseStack 与 shader 的 ModelViewMat 天然一致的语义）。</li>
 * <li>{@link #pushToGL()} / {@link #popFromGL()}：成对使用 GL 自带的矩阵栈，让后续
 * {@code Tessellator} 顶点自动被 GL 变换（对应 1.20 中「顶点由 shader 矩阵变换」的语义）。</li>
 * </ul>
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code PoseStack} 由 {@code Pose} 持有 {@code Matrix4f pose} + {@code Matrix3f normal}，
 * 并在每次 {@code mulPose} 时同步维护法线矩阵（用 3x3 的逆转置近似）。本类完全照抄该行为。</li>
 * <li>{@code clear()} 在 1.20 已废弃（返回 boolean），本类保留以便上游调用点原样保留。</li>
 * <li>栈深上限：1.20 用 {@code ArrayDeque} 无限制；本类同样无限制，但 GL 固定管线矩阵栈深只有
 * {@code GL_MAX_MODELVIEW_STACK_DEPTH}（通常 32），{@link #pushToGL()} 超深会 GL 报错。</li>
 * </ul>
 */
public class PoseStack {

    private final Deque<Pose> poseStack = new ArrayDeque<>();

    public PoseStack() {
        Pose pose = new Pose();
        pose.pose.identity();
        pose.normal.identity();
        this.poseStack.addLast(pose);
    }

    /**
     * 对应 1.20 {@code PoseStack#translate(double, double, double)}。
     */
    public void translate(double x, double y, double z) {
        this.last()
            .pose()
            .translate((float) x, (float) y, (float) z);
    }

    /**
     * 对应 1.20 {@code PoseStack#translate(float, float, float)}。
     */
    public void translate(float x, float y, float z) {
        this.last()
            .pose()
            .translate(x, y, z);
    }

    /**
     * 对应 1.20 {@code PoseStack#scale(float, float, float)}。
     *
     * <p>
     * 与 1.20 逐行同构：法线矩阵取「逆转置方向 + 行列式归一到 1」，因此<b>等比缩放时法线矩阵不变</b>
     * （1.20 用 {@code Mth.fastInvCubeRoot} 做归一；照抄除 1/x 会让等比缩放也改法线长度）。
     */
    public void scale(float x, float y, float z) {
        Pose pose = this.last();
        pose.pose()
            .scale(x, y, z);
        if (x == y && y == z) {
            if (x > 0.0F) {
                return;
            }
            pose.normal()
                .scale(-1.0F);
        }

        float invX = 1.0F / x;
        float invY = 1.0F / y;
        float invZ = 1.0F / z;
        float invCubeRoot = Mth.fastInvCubeRoot(invX * invY * invZ);
        pose.normal()
            .scale(invCubeRoot * invX, invCubeRoot * invY, invCubeRoot * invZ);
    }

    /**
     * 对应 1.20 {@code PoseStack#mulPose(Quaternionf)}。
     */
    public void mulPose(Quaternionf quaternion) {
        this.last()
            .pose()
            .rotate(quaternion);
        this.last()
            .normal()
            .rotate(quaternion);
    }

    /**
     * 对应 1.20 {@code PoseStack#mulPoseMatrix(Matrix4f)}。
     *
     * <p>
     * 1.20 只乘 pose，不动 normal（调用方自行保证法线正确）。本类保持一致。
     */
    public void mulPoseMatrix(Matrix4f matrix) {
        this.last()
            .pose()
            .mul(matrix);
    }

    /**
     * 对应 1.20 {@code PoseStack#rotateAround(Quaternionf, float, float, float)}。
     * 语义：先把原点平移到 (x,y,z)，再旋转，再平移回去。
     */
    public void rotateAround(Quaternionf quaternion, float x, float y, float z) {
        this.last()
            .pose()
            .rotateAround(quaternion, x, y, z);
        this.last()
            .normal()
            .rotate(quaternion);
    }

    /**
     * 对应 1.20 {@code PoseStack#pushPose()}。
     */
    public void pushPose() {
        Pose pose = new Pose();
        pose.pose.set(
            this.last()
                .pose());
        pose.normal.set(
            this.last()
                .normal());
        this.poseStack.addLast(pose);
    }

    /**
     * 对应 1.20 {@code PoseStack#popPose()}。
     */
    public void popPose() {
        if (this.poseStack.size() <= 1) {
            // 1.20 会抛 NoSuchElementException；这里给出更可读的信息便于定位渲染栈失衡。
            throw new IllegalStateException("PoseStack underflow: popPose() without matching pushPose()");
        }
        this.poseStack.removeLast();
    }

    /**
     * 对应 1.20 {@code PoseStack#setIdentity()}：把栈顶的位姿与法线矩阵复位成单位矩阵。
     */
    public void setIdentity() {
        Pose pose = this.last();
        pose.pose()
            .identity();
        pose.normal()
            .identity();
    }

    /**
     * 对应 1.20 {@code PoseStack#clear()}（1.20 已 @Deprecated）。
     *
     * <p>
     * <b>注意语义</b>：1.20 的这个方法返回「栈是否只有初始 Pose」（即 {@code size() == 1}），<b>并不</b>
     * 清空栈；本类照抄该查询语义（早期实现会真的弹栈，与上游不同）。
     */
    @Deprecated
    public boolean clear() {
        return this.poseStack.size() == 1;
    }

    /**
     * 对应 1.20 {@code PoseStack#last()}。
     */
    public Pose last() {
        return this.poseStack.getLast();
    }

    /**
     * 对应 1.20 {@code PoseStack#isEmpty()}。
     */
    public boolean isEmpty() {
        return this.poseStack.size() == 1;
    }

    // ================================================================================================
    // 以下是 1.7.10 特有的辅助方法（1.20 不需要，因为 shader 直接吃 last().pose()）
    // ================================================================================================

    /**
     * 把栈顶矩阵乘进当前 GL_MODELVIEW（{@code GL11.glMultMatrixf}）。
     *
     * <p>
     * 用于固定管线绘制前：先由 MC 把相机/实体变换压进 GL_MODELVIEW，再叠上本栈累积的变换。
     * 多次调用会累积（GL 语义），<b>不要</b>在同一个 GL 状态里重复调用。
     */
    public void applyToModelView() {
        FloatBuffer buffer = stackTopAsFloatBuffer();
        GL11.glMultMatrix(buffer);
    }

    /**
     * {@link #applyToModelView()} 的别名，语义与 1.20 中「PoseStack 决定最终顶点位置」等价。
     */
    public void applyToRender() {
        applyToModelView();
    }

    /**
     * 用 GL 自带的 MODELVIEW 栈保存当前状态，然后把栈顶矩阵乘进去。
     *
     * <p>
     * 必须与 {@link #popFromGL()} 成对调用，否则 GL 矩阵栈会漂移。
     */
    public void pushToGL() {
        GL11.glPushMatrix();
        applyToModelView();
    }

    /**
     * 与 {@link #pushToGL()} 成对，恢复 GL_MODELVIEW。
     */
    public void popFromGL() {
        GL11.glPopMatrix();
    }

    /**
     * 把 Bone 变换直接烘到 {@link Tessellator#setTranslation(double, double, double)}
     *
     * <p>
     * 1.7.10 的 {@link Tessellator} 只支持平移偏移，不支持一般线性变换；
     * 需要完整矩阵时必须用 {@link #pushToGL()}。此处只取栈顶平移分量。
     */
    public void applyTranslationToTessellator(Tessellator tessellator) {
        Matrix4f pose = last().pose();
        tessellator.setTranslation(pose.m30(), pose.m31(), pose.m32());
    }

    /**
     * 用栈顶矩阵变换一个位置向量（等价 1.20 里 {@code matrix.transformPosition(v)}）。
     */
    public Vector3f transformPosition(Vector3f dest) {
        return last().pose()
            .transformPosition(dest);
    }

    /**
     * 用栈顶法线矩阵变换一个法线向量（等价 1.20 里 {@code normalMatrix.transform(v)}）。
     */
    public Vector3f transformNormal(Vector3f dest) {
        return last().normal()
            .transform(dest);
    }

    /**
     * 把栈顶 {@link Matrix4f} 转成列主序（OpenGL 约定）的 {@link FloatBuffer}。
     * JOML 的 {@code get(float[])} 已经是列主序，可直接交给 {@code glLoadMatrixf/glMultMatrixf}。
     */
    public FloatBuffer stackTopAsFloatBuffer() {
        float[] values = new float[16];
        last().pose()
            .get(values);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(values);
        buffer.flip();
        return buffer;
    }

    /**
     * 对应 1.20.1 {@code PoseStack.Pose}。
     *
     * <p>
     * 1.20 里两个矩阵字段是 final 的 {@code Matrix4f}/{@code Matrix3f}，每次 pushPose 新建一个 Pose。
     * 本类保持一致（上游有 {@code static final PoseStack.Pose POSE = new PoseStack().last()} 这种用法，
     * 依赖 Pose 是可长期持有的对象）。
     *
     * <p>
     * <b>降级点</b>：1.20 的 {@code Pose} 还有 {@code copy()} 与 {@code Pose(Matrix4f, Matrix3f)} 构造，
     * 上游没用到，此处不提供。
     */
    public static final class Pose {

        final Matrix4f pose = new Matrix4f();
        final Matrix3f normal = new Matrix3f();

        Pose() {}

        public Matrix4f pose() {
            return this.pose;
        }

        public Matrix3f normal() {
            return this.normal;
        }
    }
}
