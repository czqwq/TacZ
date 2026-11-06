// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: (new file - 1.7.10 port support, no upstream counterpart)
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.port;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import org.joml.Quaternionf;

/**
 * 1.20.1 {@code com.github.mcmodderanchor.simplebedrockmodel.port.Camera} 的替身。
 *
 * <p>
 * TODO(L2): 1.20 有一个真正的 {@code Camera} 实例（由 {@code GameRenderer} 每帧
 * {@code setup(...)}），提供 {@code getPosition()}/{@code getXRot()}/{@code getYRot()}/
 * {@code rotation()}。1.7.10 <b>没有</b> {@code Camera} 类，同样的数据来自
 * {@code Minecraft#renderViewEntity} + {@code ActiveRenderInfo} 的插值结果，
 * 以及 {@code Entity#rotationPitch/rotationYaw}。
 * 本类因此是「按需从 1.7.10 全局状态取值」的适配器：每次调用即时读当前渲染视角实体，
 * 用 {@code timer.renderPartialTicks} 做插值（等价于 1.7.10 {@code ActiveRenderInfo} 的做法）。
 * <p>
 * 与 1.20 的差异：1.20 的 Camera 在一帧内是冻结快照，本类每次调用都重新取值（一帧内 partialTicks
 * 不变，所以结果一致）；另外 1.20 的 {@code Camera#getLookVector()}/{@code getUpVector()} 未移植（上游未使用）。
 */
public class Camera {

    private final Minecraft mc;
    private final float partialTicks;

    /**
     * 以 partialTicks = 1.0 取值：即取渲染视角实体<b>当前 tick 的位置</b>。
     * 适合 tick 阶段（发射器 tick）调用；渲染阶段请用 {@link #getMainCamera(float)} 传入真实 partialTicks。
     */
    public Camera() {
        this(1.0F);
    }

    /** 等价于 1.20 {@code Minecraft#gameRenderer.getMainCamera()}（tick 阶段用）。 */
    public static Camera getMainCamera() {
        return new Camera(1.0F);
    }

    /**
     * TODO(L2): 1.20 的 Camera 是 {@code Minecraft#gameRenderer.getMainCamera()} 返回的实例。
     * 1.7.10 没有 Camera，也无法从 {@code Minecraft} 读到 partialTicks（{@code timer} 字段是 private，
     * 1.7.10 的调用链是 {@code EntityRenderer} 把 partialTicks 一路传下来），
     * 因此改为由调用方显式传入 partialTicks，并用 {@link #getMainCamera(float)} 作为
     * {@code Minecraft.getMinecraft().gameRenderer.getMainCamera()} 的等价替身。
     */
    public Camera(float partialTicks) {
        this.mc = Minecraft.getMinecraft();
        this.partialTicks = partialTicks;
    }

    /** 对应 1.20 {@code Minecraft#gameRenderer.getMainCamera()}（1.7.10 用带 partialTicks 的等价替身）。 */
    public static Camera getMainCamera(float partialTicks) {
        return new Camera(partialTicks);
    }

    /** 对应 1.20 {@code Camera#getPosition()}：渲染视角实体的眼睛位置（插值后）。 */
    public Vec3 getPosition() {
        Entity view = this.mc.renderViewEntity;
        if (view == null) {
            return Vec3.ZERO;
        }
        double x = view.lastTickPosX + (view.posX - view.lastTickPosX) * this.partialTicks;
        double y = view.lastTickPosY + (view.posY - view.lastTickPosY) * this.partialTicks + view.getEyeHeight();
        double z = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * this.partialTicks;
        return new Vec3(x, y, z);
    }

    /** 对应 1.20 {@code Camera#getXRot()}（俯仰角，度）。 */
    public float getXRot() {
        Entity view = this.mc.renderViewEntity;
        if (view == null) {
            return 0.0F;
        }
        return view.prevRotationPitch + (view.rotationPitch - view.prevRotationPitch) * this.partialTicks;
    }

    /** 对应 1.20 {@code Camera#getYRot()}（偏航角，度）。 */
    public float getYRot() {
        Entity view = this.mc.renderViewEntity;
        if (view == null) {
            return 0.0F;
        }
        return view.prevRotationYaw + (view.rotationYaw - view.prevRotationYaw) * this.partialTicks;
    }

    /**
     * 对应 1.20 {@code Camera#rotation()}：相机朝向的四元数。
     * 按 1.20 的构造顺序（先绕 Y 轴 yaw，再绕 X 轴 pitch）生成。
     */
    public Quaternionf rotation() {
        return new Quaternionf()
            .rotationYXZ((float) Math.toRadians(-this.getYRot()), (float) Math.toRadians(this.getXRot()), 0.0F);
    }
}
