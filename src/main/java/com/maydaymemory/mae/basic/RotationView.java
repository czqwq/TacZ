// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.RotationView
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public interface RotationView {

    RotationView IDENTITY = new RotationView() {

        private final Vector3f angles = new Vector3f();
        private final Quaternionf quaternion = new Quaternionf();

        public Vector3fc asEulerAngle() {
            return this.angles;
        }

        public Quaternionfc asQuaternion() {
            return this.quaternion;
        }
    };

    Vector3fc asEulerAngle();

    Quaternionfc asQuaternion();
}
