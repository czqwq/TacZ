package com.tacz.guns.client.renderer.other;

/**
 * 可变的相机角度三元组，用来替代 1.20 的 {@code net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles}。
 *
 * <h2>为什么需要它</h2>
 * 上游 {@code AnimateGeoItemRenderer#applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles, ...)}
 * 与 {@code applyItemInHandCameraAnimation(BeforeRenderHandEvent, ...)} 依赖
 * {@code ViewportEvent.ComputeCameraAngles}（1.20 Forge 的相机角度事件，带
 * {@code getYaw/getPitch/getRoll + setYaw/setPitch/setRoll}）。
 * <b>1.7.10 没有任何等价事件</b>：{@code EntityViewRenderEvent} 只有
 * {@code FogDensity / RenderFogEvent / FogColors}，没有相机角度；1.7.10 的 yaw/pitch 由
 * {@code EntityRenderer#orientCamera} 内部直接读实体字段，没有可注入的角度容器。
 *
 * <p>
 * 因此移植侧用一个<b>纯数据容器</b>承载同样的语义：调用方（{@code client/event/CameraSetupEvent}）
 * 把要参与动画的 yaw/pitch/roll 填进来，动画算完后再写回它自己的相机状态。
 * 这样上游「累加角度」的计算逻辑（{@code AnimateGeoItemRenderer} 里由四元数反解 yaw/pitch/roll）
 * 一行都不用改。
 *
 * <p>
 * 字段语义与上游 getter 完全一致，单位角度制（deg）。
 */
public final class CameraAngles {

    private float yaw;
    private float pitch;
    private float roll;

    public CameraAngles() {}

    public CameraAngles(float yaw, float pitch, float roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    /** 对应上游 {@code ComputeCameraAngles#getYaw()}。 */
    public float getYaw() {
        return yaw;
    }

    /** 对应上游 {@code ComputeCameraAngles#setYaw(float)}。 */
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    /** 对应上游 {@code ComputeCameraAngles#getPitch()}。 */
    public float getPitch() {
        return pitch;
    }

    /** 对应上游 {@code ComputeCameraAngles#setPitch(float)}。 */
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    /** 对应上游 {@code ComputeCameraAngles#getRoll()}。 */
    public float getRoll() {
        return roll;
    }

    /** 对应上游 {@code ComputeCameraAngles#setRoll(float)}。 */
    public void setRoll(float roll) {
        this.roll = roll;
    }
}
