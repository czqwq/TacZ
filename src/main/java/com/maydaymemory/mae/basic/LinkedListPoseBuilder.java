// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.LinkedListPoseBuilder
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import java.util.LinkedList;

public class LinkedListPoseBuilder implements PoseBuilder {

    private LinkedList<BoneTransform> list = new LinkedList();

    public void addBoneTransform(BoneTransform boneTransform) {
        if (this.list == null) {
            throw new IllegalStateException(
                "Cannot add BoneTransform: the builder has already been finalized via toPose().");
        } else if (!this.list.isEmpty()
            && ((BoneTransform) this.list.getLast()).boneIndex() >= boneTransform.boneIndex()) {
                throw new IllegalArgumentException("Bone index must be in strictly ascending order");
            } else {
                this.list.addLast(boneTransform);
            }
    }

    public Pose toPose() {
        if (this.list != null) {
            LinkedListPose pose = new LinkedListPose(this.list);
            this.list = null;
            return pose;
        } else {
            throw new IllegalStateException("ArrayPoseBuilder is single-use. Please create a new builder instance.");
        }
    }
}
