package com.tacz.guns.mcshim;

import java.util.Random;

import net.minecraft.util.MathHelper;

/**
 * 对应 1.20.1 的 {@code net.minecraft.util.Mth}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 的等价类叫 {@link MathHelper}，但方法名完全不同（{@code clamp_int/clamp_float/clamp_double}、
 * {@code floor_float/floor_double}、{@code ceiling_float_int}、{@code sqrt_float/sqrt_double}、
 * {@code wrapAngleTo180_float}、{@code truncateDoubleToInt}），而且缺 {@code lerp/frac/sign/atan2/rotLerp/
 * positiveModulo/hsvToRgb/lerp2/lerp3}。本类<b>全部委托</b> {@link MathHelper}，缺的直接用
 * {@link Math} 实现（公式与 1.20 {@code Mth} 逐行一致）。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>{@code floor}/{@code ceil} 有 float/double 两个重载，1.20 也是这样；1.7.10 侧委托
 * {@code MathHelper.floor_float/floor_double/ceiling_float_int/ceiling_double_int}（注意
 * 1.7.10 的 {@code floor_double} 用 {@code (int)} 截断后修正，与 {@link Math#floor} 在
 * 溢出区间行为不同，这里保留 1.7.10 语义以与引擎其它代码一致）。</li>
 * <li>{@code hsvToRgb} 是 1.20 从 {@code java.awt.Color.HSBtoRGB} 抄来的整数实现，本类逐行移植。</li>
 * <li>1.20 的 {@code RandomSource} 相关方法（{@code nextInt} 等）不在此类，1.7.10 直接用
 * {@link java.util.Random}。</li>
 * </ul>
 */
public final class Mth {

    /** 1 度对应多少弧度，对应 1.20 {@code Mth.DEG_TO_RAD}。 */
    public static final float DEG_TO_RAD = (float) Math.PI / 180.0F;
    /** 1 弧度对应多少度，对应 1.20 {@code Mth.RAD_TO_DEG}。 */
    public static final float RAD_TO_DEG = 180.0F / (float) Math.PI;
    /** 对应 1.20 {@code Mth.EPSILON}。 */
    public static final float EPSILON = 1.0E-5F;
    /** 对应 1.20 {@code Mth.SQRT_OF_TWO}。 */
    public static final float SQRT_OF_TWO = (float) Math.sqrt(2.0);

    private Mth() {}

    // ---------------------------------------------------------------- clamp

    /** 对应 1.20 {@code Mth#clamp(int, int, int)} → {@link MathHelper#clamp_int(int, int, int)}。 */
    public static int clamp(int value, int min, int max) {
        return MathHelper.clamp_int(value, min, max);
    }

    /** 对应 1.20 {@code Mth#clamp(long, long, long)}。1.7.10 无对应，用 Math.min/max。 */
    public static long clamp(long value, long min, long max) {
        return Math.min(max, Math.max(min, value));
    }

    /** 对应 1.20 {@code Mth#clamp(float, float, float)} → {@link MathHelper#clamp_float(float, float, float)}。 */
    public static float clamp(float value, float min, float max) {
        return MathHelper.clamp_float(value, min, max);
    }

    /** 对应 1.20 {@code Mth#clamp(double, double, double)} → {@link MathHelper#clamp_double(double, double, double)}。 */
    public static double clamp(double value, double min, double max) {
        return MathHelper.clamp_double(value, min, max);
    }

    // ---------------------------------------------------------------- abs / sign

    /** 对应 1.20 {@code Mth#abs(float)} → {@link MathHelper#abs(float)}；注意它<b>不回绕</b>负零。 */
    public static float abs(float value) {
        return MathHelper.abs(value);
    }

    /** 对应 1.20 {@code Mth#abs(int)} → {@link MathHelper#abs_int(int)}。 */
    public static int abs(int value) {
        return MathHelper.abs_int(value);
    }

    /** 对应 1.20 {@code Mth#sign(double)}：返回 -1 / 0 / 1 的 int。 */
    public static int sign(double value) {
        if (value == 0.0) {
            return 0;
        }
        return value > 0.0 ? 1 : -1;
    }

    /** 对应 1.20 {@code Mth#sign(float)}。 */
    public static int sign(float value) {
        if (value == 0.0F) {
            return 0;
        }
        return value > 0.0F ? 1 : -1;
    }

    // ---------------------------------------------------------------- floor / ceil / round

    /** 对应 1.20 {@code Mth#floor(double)} → {@link MathHelper#floor_double(double)}。 */
    public static int floor(double value) {
        return MathHelper.floor_double(value);
    }

    /** 对应 1.20 {@code Mth#floor(float)} → {@link MathHelper#floor_float(float)}。 */
    public static int floor(float value) {
        return MathHelper.floor_float(value);
    }

    /** 对应 1.20 {@code Mth#floorLong(double)} → {@link MathHelper#floor_double_long(double)}。 */
    public static long floorLong(double value) {
        return MathHelper.floor_double_long(value);
    }

    /** 对应 1.20 {@code Mth#ceil(double)} → {@link MathHelper#ceiling_double_int(double)}。 */
    public static int ceil(double value) {
        return MathHelper.ceiling_double_int(value);
    }

    /** 对应 1.20 {@code Mth#ceil(float)} → {@link MathHelper#ceiling_float_int(float)}。 */
    public static int ceil(float value) {
        return MathHelper.ceiling_float_int(value);
    }

    /** 对应 1.20 {@code Mth#lfloor(double)}（= floor）。 */
    public static int lfloor(double value) {
        return MathHelper.floor_double(value);
    }

    // ---------------------------------------------------------------- 三角函数 / 平方根

    /** 对应 1.20 {@code Mth#sin(float)} → {@link MathHelper#sin(float)}（内部查表，精度 65536 分之一）。 */
    public static float sin(float value) {
        return MathHelper.sin(value);
    }

    /** 对应 1.20 {@code Mth#cos(float)} → {@link MathHelper#cos(float)}。 */
    public static float cos(float value) {
        return MathHelper.cos(value);
    }

    /** 对应 1.20 {@code Mth#sqrt(float)} → {@link MathHelper#sqrt_float(float)}。 */
    public static float sqrt(float value) {
        return MathHelper.sqrt_float(value);
    }

    /** 对应 1.20 {@code Mth#sqrt(double)} → {@link MathHelper#sqrt_double(double)}。 */
    public static float sqrt(double value) {
        return MathHelper.sqrt_double(value);
    }

    /** 对应 1.20 {@code Mth#invSqrt(float)}：{@code 1/sqrt(x)}。 */
    public static float invSqrt(float value) {
        return 1.0F / MathHelper.sqrt_float(value);
    }

    /**
     * 对应 1.20 {@code Mth#fastInvSqrt(double)}（1.20.1 里已 {@code @Deprecated}，是 Quake 魔数近似）。
     *
     * <p>
     * <b>裁决（r7-util 转派）：保留精确实现、不移植魔数近似。</b>上游 {@code MathUtil#normalizeQuaternion}
     * （{@code tmp/TACZ-1.20.1/.../util/math/MathUtil.java:192}）写的是 {@code Mth.fastInvSqrt(f)}，而 1.20.1 的
     * {@code Mth} 只有 {@code fastInvSqrt(double)}（{@code Mth.java:395-402}，{@code @Deprecated}），float 实参被
     * 隐式加宽 ⇒ 上游是「误用弃用近似函数」；近似误差约 1e-3，只影响四元数归一化的末位。
     * 本 shim 返回精确 {@code 1/sqrt}（更稳定、可复现），差异远小于动画/渲染可观测阈值，登记为**有据偏离**。
     */
    public static float fastInvSqrt(float value) {
        return 1.0F / MathHelper.sqrt_float(value);
    }

    /**
     * 对应 1.20 {@code Mth#fastInvCubeRoot(float)}：1/cbrt(x) 的两次牛顿迭代近似。
     * 逐行照抄 1.20.1 的实现（{@code Mth.java:404-410}），{@link PoseStack#scale} 的法线归一化依赖它。
     */
    public static float fastInvCubeRoot(float value) {
        int i = Float.floatToIntBits(value);
        i = 1419967116 - i / 3;
        float guess = Float.intBitsToFloat(i);
        guess = 0.6666667F * guess + 1.0F / (3.0F * guess * guess * value);
        return 0.6666667F * guess + 1.0F / (3.0F * guess * guess * value);
    }

    /** 对应 1.20 {@code Mth#invSqrt(double)}。 */
    public static double invSqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    /** 对应 1.20 {@code Mth#atan2(double, double)}：直接 {@link Math#atan2}。 */
    public static double atan2(double y, double x) {
        return Math.atan2(y, x);
    }

    // ---------------------------------------------------------------- lerp / frac

    /** 对应 1.20 {@code Mth#lerp(float delta, float start, float end)}。 */
    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    /** 对应 1.20 {@code Mth#lerp(double delta, double start, double end)}。 */
    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    /** 对应 1.20 {@code Mth#lerp2(double deltaX, double deltaY, double x0y0, double x1y0, double x0y1, double x1y1)}。 */
    public static double lerp2(double deltaX, double deltaY, double x0y0, double x1y0, double x0y1, double x1y1) {
        return lerp(deltaY, lerp(deltaX, x0y0, x1y0), lerp(deltaX, x0y1, x1y1));
    }

    /** 对应 1.20 {@code Mth#lerp3(double, double, double, 8 个角值)}。 */
    public static double lerp3(double deltaX, double deltaY, double deltaZ, double x0y0z0, double x1y0z0, double x0y1z0,
        double x1y1z0, double x0y0z1, double x1y0z1, double x0y1z1, double x1y1z1) {
        return lerp(
            deltaZ,
            lerp2(deltaX, deltaY, x0y0z0, x1y0z0, x0y1z0, x1y1z0),
            lerp2(deltaX, deltaY, x0y0z1, x1y0z1, x0y1z1, x1y1z1));
    }

    /** 对应 1.20 {@code Mth#lerpDiscrete(float delta, int start, int end)}。 */
    public static int lerpDiscrete(float delta, int start, int end) {
        return start + (int) (delta * (float) (end - start));
    }

    /** 对应 1.20 {@code Mth#inverseLerp(double value, double start, double end)}。 */
    public static double inverseLerp(double value, double start, double end) {
        return (value - start) / (end - start);
    }

    /** 对应 1.20 {@code Mth#frac(float)}：取小数部分。 */
    public static float frac(float value) {
        return value - (float) MathHelper.floor_float(value);
    }

    /** 对应 1.20 {@code Mth#frac(double)}。 */
    public static double frac(double value) {
        return value - (double) MathHelper.floor_double(value);
    }

    // ---------------------------------------------------------------- 取模 / 角度

    /** 对应 1.20 {@code Mth#positiveModulo(float, float)}：结果恒 ≥ 0（与 Java {@code %} 不同）。 */
    public static float positiveModulo(float numerator, float denominator) {
        return (numerator % denominator + denominator) % denominator;
    }

    /** 对应 1.20 {@code Mth#positiveModulo(double, double)}。 */
    public static double positiveModulo(double numerator, double denominator) {
        return (numerator % denominator + denominator) % denominator;
    }

    /** 对应 1.20 {@code Mth#wrapDegrees(float)} → {@link MathHelper#wrapAngleTo180_float(float)}。 */
    public static float wrapDegrees(float value) {
        return MathHelper.wrapAngleTo180_float(value);
    }

    /** 对应 1.20 {@code Mth#wrapDegrees(double)} → {@link MathHelper#wrapAngleTo180_double(double)}。 */
    public static double wrapDegrees(double value) {
        return MathHelper.wrapAngleTo180_double(value);
    }

    /** 对应 1.20 {@code Mth#rotLerp(float delta, float start, float end)}：按最短弧插值角度。 */
    public static float rotLerp(float delta, float start, float end) {
        float diff = end - start;
        while (diff < -180.0F) {
            diff += 360.0F;
        }
        while (diff >= 180.0F) {
            diff -= 360.0F;
        }
        return start + delta * diff;
    }

    /** 对应 1.20 {@code Mth#rotLerpRad(float delta, float start, float end)}。 */
    public static float rotLerpRad(float delta, float start, float end) {
        float diff = end - start;
        while (diff < -(float) Math.PI) {
            diff += (float) Math.PI * 2.0F;
        }
        while (diff >= (float) Math.PI) {
            diff -= (float) Math.PI * 2.0F;
        }
        return start + delta * diff;
    }

    // ---------------------------------------------------------------- 杂项

    /** 对应 1.20 {@code Mth#square(float)}。 */
    public static float square(float value) {
        return value * value;
    }

    /** 对应 1.20 {@code Mth#cube(float)}。 */
    public static float cube(float value) {
        return value * value * value;
    }

    /** 对应 1.20 {@code Mth#equal(float, float)}：差值小于 {@link #EPSILON} 视为相等。 */
    public static boolean equal(float a, float b) {
        return Math.abs(b - a) < EPSILON;
    }

    /** 对应 1.20 {@code Mth#equal(double, double)}。 */
    public static boolean equal(double a, double b) {
        return Math.abs(b - a) < 1.0E-5;
    }

    /** 对应 1.20 {@code Mth#nextInt(RandomSource, int, int)}：1.7.10 用 {@link java.util.Random}。 */
    public static int nextInt(Random random, int min, int max) {
        // 1.20 在 min >= max 时直接返回 min；1.7.10 的 getRandomIntegerInRange 会 nextInt(负数) 抛异常。
        if (min >= max) {
            return min;
        }
        return MathHelper.getRandomIntegerInRange(random, min, max);
    }

    /** 对应 1.20 {@code Mth#nextFloat(RandomSource, float, float)}。 */
    public static float nextFloat(Random random, float min, float max) {
        return MathHelper.randomFloatClamp(random, min, max);
    }

    /**
     * 对应 1.20 {@code Mth#hsvToRgb(float hue, float saturation, float value)}：返回 0xRRGGBB。
     * 逐行移植 1.20 的整数 HSV→RGB 实现（等价于 {@code java.awt.Color.HSBtoRGB}）。
     */
    public static int hsvToRgb(float hue, float saturation, float value) {
        int h = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - (float) h;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - f * saturation);
        float t = value * (1.0F - (1.0F - f) * saturation);
        float r;
        float g;
        float b;
        switch (h) {
            case 0:
                r = value;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = value;
                b = p;
                break;
            case 2:
                r = p;
                g = value;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = value;
                break;
            case 4:
                r = t;
                g = p;
                b = value;
                break;
            case 5:
                r = value;
                g = p;
                b = q;
                break;
            default:
                throw new RuntimeException(
                    "Something went wrong when converting from HSV to RGB. Input was " + hue
                        + ", "
                        + saturation
                        + ", "
                        + value);
        }
        int ir = clamp((int) (r * 255.0F), 0, 255);
        int ig = clamp((int) (g * 255.0F), 0, 255);
        int ib = clamp((int) (b * 255.0F), 0, 255);
        return ir << 16 | ig << 8 | ib;
    }
}
