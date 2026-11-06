// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/render/CameraStateCache.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render;

/**
 * 缓存本帧的相机 roll 与世界/手部投影 FOV，供第一人称粒子从手部空间换算到世界空间。
 *
 * <p>
 * TODO(L2): 上游 v1/particle/render/CameraStateCache.java:21-37 用两个 Forge 事件采样：
 * <ul>
 * <li>{@code ViewportEvent.ComputeCameraAngles#getRoll()} —— 1.7.10 <b>完全没有</b>相机 roll
 * （{@code ViewportEvent}/{@code EntityViewRenderEvent} 都没有 roll 通道，1.7.10 的相机只有 yaw/pitch），
 * 因此 {@link #getCameraRollDegrees()} 恒为 0。</li>
 * <li>{@code ViewportEvent.ComputeFov} 的 {@code usedConfiguredFov()}（区分「世界投影」与「手部投影」两套 FOV）
 * —— 1.7.10 没有 FOVModifier 事件，也<b>没有独立的手部投影 FOV</b>：第一人称手部与世界共用同一个
 * 投影矩阵（{@code GameSettings#fovSetting}）。因此手部→世界的深度比例恒为 1.0，
 * {@link #getFirstPersonToWorldDepthScale()} 返回 1.0、{@link #getWorldParticleScale()} 返回 1.0，
 * 与上游「handFov == worldFov」时的结果完全一致。</li>
 * </ul>
 * 本类保留上游全部公开方法与字段语义，TACZ 侧调用点无需改动。
 */
public final class CameraStateCache {

    private static float cameraRoll;
    private static float worldFov = 70.0f;
    private static float handFov = 70.0f;

    private CameraStateCache() {}

    /**
     * 1.7.10 没有相机 roll 事件通道，此方法保留以兼容上游可能的主动写入。
     */
    public static void setCameraRoll(float roll) {
        cameraRoll = roll;
    }

    /**
     * 获取当前帧的 camera roll（度）。
     */
    public static float getCameraRollDegrees() {
        return cameraRoll;
    }

    /**
     * 获取当前帧的 camera roll（弧度）。
     */
    public static float getCameraRollRadians() {
        return (float) Math.toRadians(cameraRoll);
    }

    /**
     * 返回从手部投影空间映射到世界投影空间所需的视图 Z 轴缩放。
     * 1.7.10 两套投影相同，因此恒为 1.0。
     */
    public static float getFirstPersonToWorldDepthScale() {
        if (handFov <= 0.0f || worldFov <= 0.0f) {
            return 1.0f;
        }
        double handTan = Math.tan(Math.toRadians(handFov) / 2.0);
        double worldTan = Math.tan(Math.toRadians(worldFov) / 2.0);
        if (Math.abs(worldTan) < 1.0e-6) {
            return 1.0f;
        }
        return (float) (handTan / worldTan);
    }

    /** Current world-particle size multiplier for matching the hand projection. */
    public static float getWorldParticleScale() {
        float handToWorld = getFirstPersonToWorldDepthScale();
        return Float.isFinite(handToWorld) && handToWorld > 1.0e-4f ? 1.0f / handToWorld : 1.0f;
    }
}
