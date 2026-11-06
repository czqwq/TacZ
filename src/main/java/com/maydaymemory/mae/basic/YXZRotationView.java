// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.YXZRotationView
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

import com.maydaymemory.mae.util.MathUtil;

public class YXZRotationView implements RotationView {

    private Vector3fc angle;
    private Quaternionfc quaternion;

    public YXZRotationView(Vector3fc angle) {
        this.angle = angle;
    }

    public YXZRotationView(Quaternionfc quaternion) {
        this.quaternion = quaternion;
    }

    public Vector3fc asEulerAngle() {
        if (this.angle == null) {
            Matrix3f m = (new Matrix3f()).set(this.quaternion);
            this.angle = MathUtil.extractEulerAnglesYXZ(m);
        }

        return this.angle;
    }

    public Quaternionfc asQuaternion() {
        if (this.quaternion == null) {
            this.quaternion = (new Quaternionf()).rotateYXZ(this.angle.y(), this.angle.x(), this.angle.z());
        }

        return this.quaternion;
    }

    public String toString() {
        if (this.angle != null) {
            return "YXZRotationView{(radians)" + this.angle + '}';
        } else {
            return this.quaternion != null ? "YXZRotationView{(radians)=" + this.asEulerAngle() + '}' : null;
        }
    }
}
