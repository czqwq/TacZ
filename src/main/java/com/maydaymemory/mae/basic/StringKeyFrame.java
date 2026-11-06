// Vendored from mae 1.1.4 (com.maydaymemory:mae), the animation-evaluation library that
// simplebedrockmodel 2.6.1 bundles via JarJar. Upstream ships no source distribution, so this
// file is a faithful decompilation (IntelliJ Fernflower) of the published 1.1.4 bytecode.
// upstream class: com.maydaymemory.mae.basic.StringKeyFrame
// 1.7.10 port: unchanged (pure Java, depends only on JOML) - no TODO(L2) markers needed.
package com.maydaymemory.mae.basic;

public class StringKeyFrame extends BaseKeyframe<String> {

    private final String value;

    public StringKeyFrame(String value, float timeS) {
        super(timeS);
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }
}
