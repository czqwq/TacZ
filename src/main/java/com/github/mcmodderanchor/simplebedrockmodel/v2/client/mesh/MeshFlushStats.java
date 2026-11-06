// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/MeshFlushStats.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

/** 单次 flush 的增量统计；endBatch 只返回最后一次排空的统计。 */
public record MeshFlushStats(int queuedItems, int drawnParts, int materialSetups, int bufferBinds,
    int discardedItems) {}
