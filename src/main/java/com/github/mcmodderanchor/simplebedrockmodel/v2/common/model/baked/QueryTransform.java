// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/baked/QueryTransform.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import org.joml.Matrix4f;

public record QueryTransform(String name, int attachBoneIndex, Matrix4f localTransform) {

    public QueryTransform {
        localTransform = new Matrix4f(localTransform);
    }
}
