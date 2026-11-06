// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.BoneTransformFactory
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public interface BoneTransformFactory {

    BoneTransform createBoneTransform(int var1, Vector3fc var2, Vector3fc var3, Vector3fc var4);

    BoneTransform createBoneTransform(int var1, Vector3fc var2, Quaternionfc var3, Vector3fc var4);
}
