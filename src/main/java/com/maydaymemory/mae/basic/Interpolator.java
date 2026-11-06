// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.Interpolator
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

public interface Interpolator<T> {

    T interpolate(InterpolatableChannel<T> var1, int var2, int var3, float var4);

    default T interpolate(InterpolatableChannel<T> channel, int indexFrom, int indexTo, float alpha,
        IEvaluationContext ctx) {
        return (T) this.interpolate(channel, indexFrom, indexTo, alpha);
    }

    Priority getPriority();

    public static enum Priority {

        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH;

        // $FF: synthetic method
        private static Priority[] $values() {
            return new Priority[] { VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH };
        }
    }
}
