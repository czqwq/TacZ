// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/MeshSubmitResult.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

/** 在 submit 中选定网格，随后调用方才能生成与之对应的动态效果。 */
public enum MeshSubmitResult {

    QUEUED,
    QUEUED_PREVIOUS,
    PENDING,
    UNSUPPORTED;

    public boolean owned() {
        return this != UNSUPPORTED;
    }

    public boolean hasMesh() {
        return this == QUEUED || this == QUEUED_PREVIOUS;
    }

    public boolean usedPrevious() {
        return this == QUEUED_PREVIOUS;
    }
}
