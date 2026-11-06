// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/model/BedrockMesh.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.model;

import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.VertexConsumer;

public interface BedrockMesh {

    void compileTriangles(PoseStack.Pose pose, VertexConsumer consumer, int lightmap, int overlay, float red,
        float green, float blue, float alpha);

    float width();

    float height();

    float depth();

    float x();

    float y();

    float z();
}
