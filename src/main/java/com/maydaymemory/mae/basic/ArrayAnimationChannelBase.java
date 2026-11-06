// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.ArrayAnimationChannelBase
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import java.util.ArrayList;
import java.util.Collections;

import javax.annotation.Nonnull;

import com.maydaymemory.mae.util.DirtyTrackingArrayList;

public abstract class ArrayAnimationChannelBase<T extends Keyframe<?>> extends DirtyTrackingArrayList<T>
    implements AnimationChannel {

    public ArrayAnimationChannelBase(@Nonnull ArrayList<T> initialList) {
        super(initialList);
    }

    protected boolean onRefresh() {
        Collections.sort(this.innerList);
        return true;
    }

    public float getEndTimeS() {
        this.assertNotDirty();
        return this.innerList.isEmpty() ? 0.0F : ((Keyframe) this.innerList.get(this.innerList.size() - 1)).getTimeS();
    }

    public int findIndexBefore(float timeS, boolean open) {
        this.assertNotDirty();
        if (this.innerList.isEmpty()) {
            return -1;
        } else {
            int low = 0;
            int high = this.innerList.size() - 1;
            int result = -1;

            while (low <= high) {
                int mid = low + high >>> 1;
                T t = (T) (this.innerList.get(mid));
                if (!(t.getTimeS() < timeS) && (t.getTimeS() != timeS || open)) {
                    high = mid - 1;
                } else {
                    result = mid;
                    low = mid + 1;
                }
            }

            return result;
        }
    }

    protected void assertNotDirty() {
        if (this.isDirty()) {
            throw new AssertionError(
                "The channel is dirty. Please make sure to call the refresh method after manipulating keyframes.");
        }
    }
}
