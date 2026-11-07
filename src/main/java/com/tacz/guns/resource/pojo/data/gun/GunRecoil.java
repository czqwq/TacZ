package com.tacz.guns.resource.pojo.data.gun;

import javax.annotation.Nullable;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import com.google.gson.annotations.SerializedName;

public class GunRecoil {

    private static final SplineInterpolator INTERPOLATOR = new SplineInterpolator();

    @SerializedName("pitch")
    @Nullable
    private GunRecoilKeyFrame[] pitch;

    @SerializedName("yaw")
    @Nullable
    private GunRecoilKeyFrame[] yaw;

    public GunRecoilKeyFrame[] getPitch() {
        return pitch;
    }

    public void setPitch(@Nullable GunRecoilKeyFrame[] pitch) {
        this.pitch = pitch;
    }

    public GunRecoilKeyFrame[] getYaw() {
        return yaw;
    }

    public void setYaw(@Nullable GunRecoilKeyFrame[] yaw) {
        this.yaw = yaw;
    }

    /**
     * 返回经过随机取值、缩放后摄像机垂直后坐力的样条插值函数。
     *
     * @param modifier 配件对后坐力的修改
     * @return 样条插值函数
     */
    @Nullable
    public PolynomialSplineFunction genPitchSplineFunction(float modifier) {
        return getSplineFunction(pitch, modifier);
    }

    /**
     * 返回经过随机取值、缩放后摄像机水平后坐力的样条插值函数。
     *
     * @param modifier 配件对后坐力的修改
     * @return 样条插值函数
     */
    @Nullable
    public PolynomialSplineFunction genYawSplineFunction(float modifier) {
        return getSplineFunction(yaw, modifier);
    }

    private PolynomialSplineFunction getSplineFunction(GunRecoilKeyFrame[] keyFrames, float modifier) {
        if (keyFrames == null || keyFrames.length == 0) {
            return null;
        }
        // 移植补充（对非法枪包数据的防御，不改变合法数据的语义）：
        // 本方法会先补一个 (time=0, value=0) 的点，因此样条点数 = keyFrames.length + 1；
        // commons-math3 的 SplineInterpolator.interpolate 要求点数 >= 3，只有 1 个关键帧时
        // 上游（1.20.1 同文件同实现）会在 CameraSetupEvent#initialCameraRecoil 里抛
        // NumberIsTooSmallException: number of points (2)，被 Forge 事件总线记为
        // "Exception caught during firing event GunFireEvent"（后坐力完全不生效）。
        // 1 帧后坐力无法构成曲线，语义上等同于「没有后坐力数据」，故与空数组一样返回 null；
        // 消费方 CameraSetupEvent#applyCameraRecoil 已判空（同上游）。
        // 实测数据：Applied Armorer 的 special_melee_task_manager 的 recoil.pitch / recoil.yaw
        // 各只有 {"time":0,"value":[0,0]} —— 见 docs/review/r8/README.md。
        if (keyFrames.length < 2) {
            return null;
        }
        double[] values = new double[keyFrames.length + 1];
        double[] times = new double[keyFrames.length + 1];
        times[0] = 0;
        values[0] = 0;
        for (int i = 0; i < keyFrames.length; i++) {
            times[i + 1] = keyFrames[i].getTime() * 1000 + 30;
        }
        for (int i = 0; i < keyFrames.length; i++) {
            float[] value = keyFrames[i].getValue();
            values[i + 1] = (value[0] + Math.random() * (value[1] - value[0])) * modifier;
        }
        return INTERPOLATOR.interpolate(times, values);
    }
}
