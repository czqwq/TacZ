// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/baked/BakedVertexData.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

public record BakedVertexData(float[] positions, float[] normals, float[] uvs, int vertexCount) {

    public static final int POSITION_STRIDE = 3;
    public static final int NORMAL_STRIDE = 3;
    public static final int UV_STRIDE = 2;

    public static final BakedVertexData EMPTY = new BakedVertexData(new float[0], new float[0], new float[0], 0);

    public BakedVertexData {
        positions = positions.clone();
        normals = normals.clone();
        uvs = uvs.clone();
    }
}
