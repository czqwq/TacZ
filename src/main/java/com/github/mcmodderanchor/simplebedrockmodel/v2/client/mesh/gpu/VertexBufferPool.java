// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/gpu/VertexBufferPool.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import com.tacz.guns.mcshim.VertexFormat;

/**
 * 静态缓冲池。按顶点格式分桶：{@code VertexBuffer.upload} 在格式变化时会先 clear 再 setup 当前 VAO 的
 * 属性指针，跨格式复用会互相破坏 VAO 状态，所以桶必须按格式隔离。
 *
 * <p>
 * 池只回收"整个 VertexBuffer"，不做子分配。所有方法都必须在渲染线程调用。
 * </p>
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 上游用的是 1.20 原版 {@code com.mojang.blaze3d.vertex.VertexBuffer}；1.7.10 没有该类，
 * 改用同包新增的 {@link VertexBuffer}（CPU 顶点快照 + {@code Tessellator} 重放，
 * 详见该类注释）。桶仍然按 {@link VertexFormat} 隔离 —— 即使 1.7.10 没有 VAO 状态，
 * 按格式分桶也能保证「同格式复用」这一上游语义不变。
 */
public class VertexBufferPool {

    private final Map<VertexFormat, ArrayDeque<VertexBuffer>> idle = new HashMap<>();
    private int created;
    private int reused;

    public VertexBuffer acquire(VertexFormat format) {
        ArrayDeque<VertexBuffer> bucket = this.idle.get(format);
        VertexBuffer buffer = bucket == null ? null : bucket.pollLast();
        if (buffer == null) {
            this.created++;
            return new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        this.reused++;
        return buffer;
    }

    public void recycle(VertexFormat format, VertexBuffer buffer) {
        this.idle.computeIfAbsent(format, ignored -> new ArrayDeque<>())
            .addLast(buffer);
    }

    public void clear() {
        for (ArrayDeque<VertexBuffer> bucket : this.idle.values()) {
            for (VertexBuffer buffer : bucket) {
                buffer.close();
            }
        }
        this.idle.clear();
    }

    public int created() {
        return this.created;
    }

    public int reused() {
        return this.reused;
    }

    public int idleCount() {
        int count = 0;
        for (ArrayDeque<VertexBuffer> bucket : this.idle.values()) {
            count += bucket.size();
        }
        return count;
    }
}
