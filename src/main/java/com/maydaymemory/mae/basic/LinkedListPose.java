// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.LinkedListPose
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import java.util.LinkedList;

public class LinkedListPose implements Pose {

    private final LinkedList<BoneTransform> list;

    public LinkedListPose(LinkedList<BoneTransform> list) {
        this.list = list;
    }

    public void pushBack(BoneTransform transform) {
        if (!this.list.isEmpty() && ((BoneTransform) this.list.getLast()).boneIndex() >= transform.boneIndex()) {
            throw new IllegalArgumentException("Bone index must be in strictly ascending order");
        } else {
            this.list.addLast(transform);
        }
    }

    public void pushFront(BoneTransform transform) {
        if (!this.list.isEmpty() && ((BoneTransform) this.list.getFirst()).boneIndex() <= transform.boneIndex()) {
            throw new IllegalArgumentException("Bone index must be in strictly ascending order");
        } else {
            this.list.addFirst(transform);
        }
    }

    public Iterable<BoneTransform> getBoneTransforms() {
        return this.list;
    }
}
