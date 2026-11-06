// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.ArrayPose
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import java.util.ArrayList;
import java.util.Collections;

import com.maydaymemory.mae.util.DirtyTrackingArrayList;

public class ArrayPose extends DirtyTrackingArrayList<BoneTransform> implements Pose {

    public ArrayPose(ArrayList<BoneTransform> boneTransforms) {
        super(boneTransforms);
    }

    public Iterable<BoneTransform> getBoneTransforms() {
        return this.innerList;
    }

    protected boolean onRefresh() {
        Collections.sort(this.innerList);
        return true;
    }
}
