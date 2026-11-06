// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.ZYXRotationView
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import com.maydaymemory.mae.util.MathUtil;

public class ZYXRotationView implements RotationView {

    private Vector3fc angle;
    private Quaternionfc quaternion;

    public ZYXRotationView(Vector3fc angle) {
        this.angle = angle;
    }

    public ZYXRotationView(Quaternionfc quaternion) {
        this.quaternion = quaternion;
    }

    public Vector3fc asEulerAngle() {
        if (this.angle == null) {
            Matrix3f matrix = (new Matrix3f()).set(this.quaternion);
            this.angle = MathUtil.extractEulerAnglesZYX(matrix);
        }

        return this.angle;
    }

    public Quaternionfc asQuaternion() {
        if (this.quaternion == null) {
            this.quaternion = (new Quaternionf()).rotateZYX(this.angle.z(), this.angle.y(), this.angle.x());
        }

        return this.quaternion;
    }

    public String toString() {
        if (this.angle != null) {
            return "ZYXRotationView{(radians)" + this.angle + '}';
        } else {
            return this.quaternion != null ? "ZYXRotationView{(radians)=" + this.asEulerAngle() + '}' : null;
        }
    }
}
