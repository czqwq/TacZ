package com.tacz.guns.mcshim;

import org.joml.Quaternionf;

/**
 * 对应 1.20.1 的 {@code com.mojang.math.Axis}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.20 的 {@code Axis} 是「单轴 ±1 方向的单位向量 + 转四元数」的枚举，直接用 JOML 的
 * {@link Quaternionf#rotationAxis(float, float, float, float)}。1.7.10 侧同理 —— JOML 已经是
 * 本工程的正式依赖（{@code dependencies.gradle} 里 shade 了 {@code org.joml:joml:1.10.5}），
 * 所以本类与 1.20 的实现<b>逐行一致</b>，没有任何降级。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 还提供 {@code Axis#NEGATIVE_X}/{@code POSITIVE_X} 等别名与 {@code getCoordinate()}；
 * 1.20.1 的实际名字是 {@code XP/YP/ZP/XN/YN/ZN}（本类照抄），别名未移植。</li>
 * <li>1.20 的 {@code Quaternionf} 与 JOML 1.10.5 的完全相同，因此
 * {@link #rotationDegrees(float)} 的返回值可以直接交给
 * {@link PoseStack#mulPose(Quaternionf)}。</li>
 * </ul>
 *
 * <p>
 * 注：{@code PoseStack#mulPose(Quaternionf)} 每次都会写入一个新的 {@link Quaternionf}，
 * 因此本类的方法<b>不做</b>实例复用缓存 —— 与 1.20 一致（1.20 的 {@code Axis#rotationDegrees}
 * 也是每次 {@code new Quaternionf(this.rotation, ...)}）。
 */
public enum Axis {

    /** +X 轴。 */
    XP(1.0F, 0.0F, 0.0F),
    /** -X 轴。 */
    XN(-1.0F, 0.0F, 0.0F),
    /** +Y 轴。 */
    YP(0.0F, 1.0F, 0.0F),
    /** -Y 轴。 */
    YN(0.0F, -1.0F, 0.0F),
    /** +Z 轴。 */
    ZP(0.0F, 0.0F, 1.0F),
    /** -Z 轴。 */
    ZN(0.0F, 0.0F, -1.0F);

    private final float x;
    private final float y;
    private final float z;

    Axis(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** 对应 1.20 {@code Axis#rotationDegrees(float)}：角度版。 */
    public Quaternionf rotationDegrees(float degrees) {
        return new Quaternionf().rotationAxis(degrees * ((float) Math.PI / 180.0F), x, y, z);
    }

    /** 对应 1.20 {@code Axis#rotation(float)}：弧度版。 */
    public Quaternionf rotation(float radians) {
        return new Quaternionf().rotationAxis(radians, x, y, z);
    }

    /** 对应 1.20 {@code Axis#rotationRadians(float)}：{@link #rotation(float)} 的同义名。 */
    public Quaternionf rotationRadians(float radians) {
        return rotation(radians);
    }

    /** 轴向量 X 分量。 */
    public float x() {
        return x;
    }

    /** 轴向量 Y 分量。 */
    public float y() {
        return y;
    }

    /** 轴向量 Z 分量。 */
    public float z() {
        return z;
    }
}
