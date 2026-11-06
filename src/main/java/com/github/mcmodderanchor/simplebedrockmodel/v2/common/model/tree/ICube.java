// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/tree/ICube.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.tree;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public sealed interface ICube permits CubeBox,CubePerFace {

    float x();

    float y();

    float z();

    float width();

    float height();

    float depth();

    float inflate();

    float @Nullable [] pivot();

    @Nullable
    Quaternionf rotation();

    default boolean hasRotation() {
        return pivot() != null && rotation() != null;
    }
}
