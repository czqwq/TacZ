// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.ArrayClipChannel
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import javax.annotation.Nonnull;

public class ArrayClipChannel<T> extends ArrayAnimationChannelBase<Keyframe<T>> implements ClipChannel<T> {

    public ArrayClipChannel(@Nonnull ArrayList<Keyframe<T>> keyframes) {
        super(keyframes);
    }

    public ArrayClipChannel() {
        super(new ArrayList());
    }

    public Iterable<Keyframe<T>> clip(float fromTimeS, float toTimeS) {
        this.assertNotDirty();
        if (fromTimeS == toTimeS) {
            return Collections.emptyList();
        } else {
            int indexFrom;
            int indexTo;
            if (fromTimeS > toTimeS) {
                indexFrom = this.findIndexBefore(toTimeS, false);
                indexTo = this.findIndexBefore(fromTimeS, false);
            } else {
                indexFrom = this.findIndexBefore(fromTimeS, true);
                indexTo = this.findIndexBefore(toTimeS, true);
            }

            if (indexFrom == indexTo) {
                return Collections.emptyList();
            } else {
                List<Keyframe<T>> subList = this.subList(indexFrom + 1, indexTo + 1);
                return (Iterable<Keyframe<T>>) (fromTimeS > toTimeS ? reversed(subList) : subList);
            }
        }
    }

    public static <T> Iterable<T> reversed(List<T> list) {
        return () -> new Iterator<T>() {

            private final ListIterator<T> it = list.listIterator(list.size());

            public boolean hasNext() {
                return this.it.hasPrevious();
            }

            public T next() {
                return (T) this.it.previous();
            }
        };
    }
}
