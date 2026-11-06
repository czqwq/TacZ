// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.RotationVelocityRotationView
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public class RotationVelocityRotationView implements RotationView {

    private final Vector3fc velocity;
    private Quaternionf quaternionf;

    public RotationVelocityRotationView(Vector3fc velocity) {
        this.velocity = velocity;
    }

    public Vector3fc asEulerAngle() {
        return this.velocity;
    }

    public Quaternionfc asQuaternion() {
        if (this.quaternionf == null) {
            this.quaternionf = new Quaternionf(this.velocity.x(), this.velocity.y(), this.velocity.z(), 0.0F);
        }

        return this.quaternionf;
    }
}
