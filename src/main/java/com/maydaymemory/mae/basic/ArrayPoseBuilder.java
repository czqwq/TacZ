// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.ArrayPoseBuilder
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import java.util.ArrayList;

public class ArrayPoseBuilder implements PoseBuilder {

    private ArrayList<BoneTransform> list;

    public ArrayPoseBuilder() {
        this.list = new ArrayList();
    }

    public ArrayPoseBuilder(int initialCapacity) {
        this.list = new ArrayList(initialCapacity);
    }

    public void addBoneTransform(BoneTransform boneTransform) {
        if (this.list == null) {
            throw new IllegalStateException(
                "Cannot add BoneTransform: the builder has already been finalized via toPose().");
        } else if (!this.list.isEmpty()
            && ((BoneTransform) this.list.get(this.list.size() - 1)).boneIndex() >= boneTransform.boneIndex()) {
                throw new IllegalArgumentException("Bone index must be in strictly ascending order");
            } else {
                this.list.add(boneTransform);
            }
    }

    public Pose toPose() {
        if (this.list != null) {
            ArrayPose pose = new ArrayPose(this.list);
            this.list = null;
            return pose;
        } else {
            throw new IllegalStateException("ArrayPoseBuilder is single-use. Please create a new builder instance.");
        }
    }
}
