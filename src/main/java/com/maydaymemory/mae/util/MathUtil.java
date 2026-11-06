// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.util.MathUtil
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.util;

import org.joml.Math;
import org.joml.Matrix3fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class MathUtil {

    private static final double a1 = 0.254829592;
    private static final double a2 = -0.284496736;
    private static final double a3 = 1.421413741;
    private static final double a4 = -1.453152027;
    private static final double a5 = 1.061405429;
    private static final double p = 0.3275911;

    public static Quaternionf nlerpShortestPath(Quaternionfc q1, Quaternionfc q2, float t) {
        float dot = q1.x() * q2.x() + q1.y() * q2.y() + q1.z() * q2.z() + q1.w() * q2.w();
        Quaternionf q2copy;
        if (dot < 0.0F) {
            q2copy = new Quaternionf(-q2.x(), -q2.y(), -q2.z(), -q2.w());
        } else {
            q2copy = new Quaternionf(q2);
        }

        return q1.nlerp(q2copy, t, q2copy);
    }

    public static float toSecond(long nanos) {
        return (float) nanos / 1.0E9F;
    }

    public static long toNanos(float second) {
        return (long) ((double) second * (double) 1.0E9F);
    }

    public static Vector3f logUnit(Quaternionfc q) {
        float x = q.x();
        float y = q.y();
        float z = q.z();
        float w = q.w();
        double vLenSq = (double) (x * x + y * y + z * z);
        double clampedW = Math.max((double) -1.0F, Math.min((double) 1.0F, (double) w));
        double theta = Math.acos(clampedW);
        if (vLenSq < 1.0E-12) {
            return new Vector3f(0.0F, 0.0F, 0.0F);
        } else {
            double scale = theta / Math.sqrt(vLenSq);
            return new Vector3f(
                (float) ((double) x * scale),
                (float) ((double) y * scale),
                (float) ((double) z * scale));
        }
    }

    public static Quaternionf exp(Vector3fc v) {
        float x = v.x();
        float y = v.y();
        float z = v.z();
        double theta = (double) Math.sqrt(x * x + y * y + z * z);
        if (theta < 1.0E-12) {
            return new Quaternionf(0.0F, 0.0F, 0.0F, 1.0F);
        } else {
            double sinTheta = Math.sin(theta);
            double scale = sinTheta / theta;
            return new Quaternionf((double) x * scale, (double) y * scale, (double) z * scale, Math.cos(theta));
        }
    }

    public static double erf(double x) {
        double sign = x >= (double) 0.0F ? (double) 1.0F : (double) -1.0F;
        x = Math.abs(x);
        double t = (double) 1.0F / ((double) 1.0F + 0.3275911 * x);
        double y = (double) 1.0F
            - ((((1.061405429 * t + -1.453152027) * t + 1.421413741) * t + -0.284496736) * t + 0.254829592) * t
                * Math.exp(-x * x);
        return sign * y;
    }

    public static Vector3f extractEulerAnglesZYX(Matrix3fc matrix) {
        Vector3f euler = new Vector3f();
        float r13 = matrix.m02();
        float r23 = matrix.m12();
        float r33 = matrix.m22();
        float r11 = matrix.m00();
        float r12 = matrix.m01();
        float EPSILON = 1.0E-5F;
        r13 = Math.clamp(-1.0F, 1.0F, r13);
        if (Math.abs(r13) < 0.99999F) {
            euler.y = Math.asin(-r13);
            euler.x = Math.atan2(r23, r33);
            euler.z = Math.atan2(r12, r11);
        } else {
            float r21 = matrix.m10();
            float r22 = matrix.m11();
            if (Math.abs(r11) < 1.0E-5F && Math.abs(r12) < 1.0E-5F
                && Math.abs(r23) < 1.0E-5F
                && Math.abs(r33) < 1.0E-5F) {
                euler.y = (float) (r13 > 0.0F ? (-java.lang.Math.PI / 2D) : (java.lang.Math.PI / 2D));
                euler.z = Math.atan2(-r21, r22);
                euler.x = 0.0F;
            } else {
                euler.y = Math.asin(-r13);
                euler.x = Math.atan2(r23, r33);
                euler.z = Math.atan2(r12, r11);
            }
        }

        return euler;
    }

    public static Vector3f extractEulerAnglesYXZ(Matrix3fc matrix) {
        Vector3f euler = new Vector3f();
        float r32 = matrix.m21();
        float r12 = matrix.m01();
        float r22 = matrix.m11();
        float r31 = matrix.m20();
        float r33 = matrix.m22();
        float EPSILON = 1.0E-5F;
        r32 = Math.clamp(-1.0F, 1.0F, r32);
        if (Math.abs(r32) < 0.99999F) {
            euler.x = Math.asin(-r32);
            euler.y = Math.atan2(r31, r33);
            euler.z = Math.atan2(r12, r22);
        } else if (Math.abs(r31) < 1.0E-5F && Math.abs(r33) < 1.0E-5F
            && Math.abs(r12) < 1.0E-5F
            && Math.abs(r22) < 1.0E-5F) {
                euler.x = (float) (r32 > 0.0F ? (-java.lang.Math.PI / 2D) : (java.lang.Math.PI / 2D));
                float r21 = matrix.m10();
                float r11 = matrix.m00();
                euler.z = (float) java.lang.Math.atan2((double) (-r21), (double) r11);
                euler.y = 0.0F;
            } else {
                euler.x = (float) java.lang.Math.asin((double) (-r32));
                euler.y = (float) java.lang.Math.atan2((double) r31, (double) r33);
                euler.z = (float) java.lang.Math.atan2((double) r12, (double) r22);
            }

        return euler;
    }
}
