// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/cache/MeshCachePolicy.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache;

import java.time.Duration;
import java.util.Objects;

/** 无引用几何的保留时间与 LRU 限额；仍有引用的网格不参与淘汰。 */
public record MeshCachePolicy(Duration idleRetention, int maxIdleEntries, long maxIdleBytes) {

    public static final MeshCachePolicy DEFAULT = new MeshCachePolicy(
        Duration.ofSeconds(300),
        128,
        256L * 1024L * 1024L);

    public MeshCachePolicy {
        Objects.requireNonNull(idleRetention, "idleRetention");
        if (idleRetention.isNegative() || maxIdleEntries < 0 || maxIdleBytes < 0)
            throw new IllegalArgumentException("Negative mesh cache limit");
        idleRetention.toNanos(); // 拒绝无法用单调时钟纳秒值表示的时长。
    }
}
