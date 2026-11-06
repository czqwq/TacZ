// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/model/ISodiumVertexWriter.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.model;

import org.joml.Vector3f;

/**
 * Sodium 顶点写入器的公共接口（1.20 上游用 {@code ModelVertex.write} + LWJGL3 {@code MemoryStack}）。
 *
 * <p>
 * TODO(L2): 上游 v1/common/model/ISodiumVertexWriter.java:15/17/21/26 依赖
 * {@code net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex} 与 LWJGL3 的
 * {@code MemoryUtil.nmemAlignedAlloc}/{@code MemoryStack}。1.7.10 两者都没有：
 * <ul>
 * <li>{@link #STRIDE} 保留 1.20 ModelVertex 的 32 字节步长常量（仅作参考值）；</li>
 * <li>{@link #SCRATCH_BUFFER} 降级为 0（无本地内存），{@link #emitVertex}/{@link #flush}
 * 抛 {@link UnsupportedOperationException}——它们的唯一调用方是 Sodium 路径，而在 1.7.10
 * Sodium 路径在 1.7.10 不存在（compat 目录已按主 Agent 要求删除），永远不会被调用；</li>
 * <li>{@link #packNormal}/{@link #prepareNormals} 是纯数学，原样保留（与 1.20 位编码一致）。</li>
 * </ul>
 */
public interface ISodiumVertexWriter {

    int STRIDE = 32;
    int SIZE = 6 * 4;
    long SCRATCH_BUFFER = 0L;

    int[] NORMALS = new int[6];

    default void emitVertex(long ptr, float x, float y, float z, int color, float u, float v, int packedOverlay,
        int packedLight, int normal) {
        throw new UnsupportedOperationException("ISodiumVertexWriter.emitVertex: Sodium is not available on 1.7.10");
    }

    default void flush(Object writer, int vertexCount) {
        throw new UnsupportedOperationException("ISodiumVertexWriter.flush: Sodium is not available on 1.7.10");
    }

    default int packNormal(float x, float y, float z) {
        int normX = (int) (x * 127.0f) & 255;
        int normY = (int) (y * 127.0f) & 255;
        int normZ = (int) (z * 127.0f) & 255;

        return (normZ << 16) | (normY << 8) | normX;
    }

    default void prepareNormals(Vector3f[] normals) {
        for (int i = 0; i < normals.length; i++) {
            NORMALS[i] = packNormal(normals[i].x, normals[i].y, normals[i].z);
        }
    }
}
