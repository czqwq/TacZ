// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/baked/BakedQuadData.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

public record BakedQuadData(float[] positions, float[] normals, float[] uvs, int quadCount) {

    public static final int POSITION_STRIDE = 12;
    public static final int NORMAL_STRIDE = 3;
    public static final int UV_STRIDE = 8;

    public static final BakedQuadData EMPTY = new BakedQuadData(new float[0], new float[0], new float[0], 0);

    public BakedQuadData {
        positions = positions.clone();
        normals = normals.clone();
        uvs = uvs.clone();
    }
}
