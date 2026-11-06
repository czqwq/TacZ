// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/cache/MeshPreparationPolicy.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache;

/** 渲染帧内捕获／上传软预算，以及准备数据与驻留几何的准入预算。 */
public record MeshPreparationPolicy(int maxCaptures, long captureNanos, int maxUploads, long uploadBytes,
    long uploadNanos, long pendingBytes, long residentBytes) {

    public static final MeshPreparationPolicy DEFAULT = new MeshPreparationPolicy(
        4,
        2_000_000L,
        4,
        8L * 1024 * 1024,
        2_000_000L,
        256L * 1024 * 1024,
        256L * 1024 * 1024);

    public MeshPreparationPolicy {
        if (maxCaptures <= 0 || captureNanos <= 0
            || maxUploads <= 0
            || uploadBytes <= 0
            || uploadNanos <= 0
            || pendingBytes <= 0
            || residentBytes <= 0) {
            throw new IllegalArgumentException("Mesh preparation budgets must be positive");
        }
    }
}
