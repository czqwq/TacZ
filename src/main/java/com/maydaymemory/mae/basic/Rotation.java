// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.Rotation
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public class Rotation {

    private final Vector3fc eulerAngles;
    private final Quaternionfc quaternion;

    public Rotation(Vector3fc eulerAngles) {
        this.eulerAngles = eulerAngles;
        this.quaternion = null;
    }

    public Rotation(Quaternionfc quaternion) {
        this.eulerAngles = null;
        this.quaternion = quaternion;
    }

    public Vector3fc getEulerAngles() {
        return this.eulerAngles;
    }

    public Quaternionfc getQuaternion() {
        return this.quaternion;
    }

    public boolean isEulerAngles() {
        return this.eulerAngles != null;
    }

    public boolean isQuaternion() {
        return this.quaternion != null;
    }
}
