// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/common/model/baked/BakedGeometryChunk.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked;

import org.jetbrains.annotations.ApiStatus;

public final class BakedGeometryChunk {

    private final int attachBoneIndex;
    private final BakedQuadData quads;
    private final BakedVertexData vertices;
    private final String[] sourceBones;

    // TODO(L2): 上游有 `private AcceleratedBedrockGeometryCache cache`（AR 加速网格缓存）；1.7.10 无 AR，删除。

    public BakedGeometryChunk(int attachBoneIndex, BakedQuadData quads, BakedVertexData vertices,
        String[] sourceBones) {
        this.attachBoneIndex = attachBoneIndex;
        this.quads = quads == null ? BakedQuadData.EMPTY : quads;
        this.vertices = vertices == null ? BakedVertexData.EMPTY : vertices;
        this.sourceBones = sourceBones.clone();
    }

    public int attachBoneIndex() {
        return attachBoneIndex;
    }

    public BakedQuadData quads() {
        return quads;
    }

    public BakedVertexData vertices() {
        return vertices;
    }

    @ApiStatus.Internal
    // TODO(L2): 上游 getOrCreateCache() 返回 AR 的加速网格缓存；1.7.10 无 AR，该访问器删除。

    public String[] sourceBones() {
        return sourceBones.clone();
    }

    public int quadCount() {
        return quads.quadCount();
    }

    public int vertexCount() {
        return quads.quadCount() * 4 + vertices.vertexCount();
    }

    public boolean hasQuads() {
        return quads.quadCount() > 0;
    }

    public boolean hasVertices() {
        return vertices.vertexCount() > 0;
    }

    public boolean isRootAttached() {
        return attachBoneIndex < 0;
    }
}
