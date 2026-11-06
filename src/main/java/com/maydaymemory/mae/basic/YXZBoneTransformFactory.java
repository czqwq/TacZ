// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.YXZBoneTransformFactory
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import org.joml.Quaternionfc;
import org.joml.Vector3fc;

public class YXZBoneTransformFactory implements BoneTransformFactory {

    public BoneTransform createBoneTransform(int boneIndex, Vector3fc translation, Vector3fc rotation,
        Vector3fc scale) {
        return new BoneTransform(boneIndex, translation, new YXZRotationView(rotation), scale);
    }

    public BoneTransform createBoneTransform(int boneIndex, Vector3fc translation, Quaternionfc rotation,
        Vector3fc scale) {
        return new BoneTransform(boneIndex, translation, new YXZRotationView(rotation), scale);
    }
}
