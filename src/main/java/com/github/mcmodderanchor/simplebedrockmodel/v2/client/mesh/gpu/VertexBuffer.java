// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: (1.20.1 vanilla) com.mojang.blaze3d.vertex.VertexBuffer —— 上游直接引用原版类，
// 本文件是 1.7.10 侧的等价替代物，因此没有上游同名文件。
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.mcshim.BufferBuilder;
import com.tacz.guns.mcshim.BufferUploader;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 1.7.10 版的 {@code com.mojang.blaze3d.vertex.VertexBuffer}。
 *
 * <h2>为什么需要这个类</h2>
 * <p>
 * 上游 {@code v2/client/mesh/**} 用 1.20 原版的 {@code VertexBuffer} 把烘焙好的顶点常驻显存，
 * 每帧只做 {@code bind() → draw()}。1.7.10 <b>没有</b>这个类：它的渲染走
 * {@code Tessellator} 固定管线，没有「顶点缓冲对象 + 顶点数组对象」的托管封装。
 *
 * <h2>1.7.10 用的是什么</h2>
 * <p>
 * 本类退化成「<b>CPU 侧顶点快照</b>」：
 * <ul>
 * <li>{@link #upload(BufferBuilder.RenderedBuffer)} 只是<b>保留引用</b>（RenderedBuffer 本身就是
 * 一份 Java 堆上的 float 数组快照，见 {@code mcshim BufferBuilder}），不上传任何 GL 对象；</li>
 * <li>{@link #draw()} 调用 {@link BufferUploader#draw(BufferBuilder.RenderedBuffer)}，
 * 逐顶点重新灌进 {@code Tessellator} 再 {@code glDrawArrays}；</li>
 * <li>{@link #vertexBufferId} 退化为一个<b>进程内单调递增的身份号</b>（不是 GL 缓冲名），
 * 仅供 {@code MeshIntegerAttributes}/{@code WorldMeshBuffers} 做「绑定的几何是否换过」的比较。</li>
 * </ul>
 *
 * <h2>降级点（性能）</h2>
 * <ul>
 * <li><b>没有显存常驻</b>：每帧都要把整块几何重新塞进 {@code Tessellator}，
 * 顶点量大的网格比 1.20 慢一个数量级。1.7.10 无法在不引入自有 VBO 着色器管线的前提下改善。</li>
 * <li>{@link #bind()} / {@link #unbind()} 是 no-op（没有可绑定的 GL 对象）。</li>
 * <li>没有 index buffer、没有顶点格式切换（{@code Tessellator} 的 8-int 槽位是固定的）。</li>
 * <li>{@link #close()} 丢弃 CPU 快照；{@link #isInvalid()} 在关闭后恒为 {@code true}。</li>
 * </ul>
 */
public class VertexBuffer implements AutoCloseable {

    /** 对应 1.20 {@code VertexBuffer.Usage}：1.7.10 无 GL 缓冲，仅作语义标记。 */
    public enum Usage {
        STATIC,
        DYNAMIC
    }

    private static int nextIdentity = 1;

    /**
     * 对应 1.20 {@code VertexBuffer#vertexBufferId}。
     *
     * <p>
     * <b>降级点</b>：1.20 里这是 {@code glGenBuffers()} 得到的 GL 缓冲名；1.7.10 没有 VBO，
     * 这里退化为进程内唯一的身份号（&gt;0），只用于「绑定的几何是否换了」这类相等性判断，
     * <b>不能</b>传给任何 {@code glBindBuffer}。
     */
    public int vertexBufferId = nextIdentity++;

    private final Usage usage;
    @Nullable
    private BufferBuilder.RenderedBuffer data;
    @Nullable
    private VertexFormat format;
    private boolean closed;

    public VertexBuffer(Usage usage) {
        this.usage = usage;
    }

    /** 对应 1.20 {@code VertexBuffer#bind()}：1.7.10 无 GL 对象可绑，no-op。 */
    public void bind() {
        // 1.7.10 无 VBO/VAO，绑定没有意义。
    }

    /** 对应 1.20 {@code VertexBuffer#unbind()}：no-op。 */
    public static void unbind() {
        // 1.7.10 无 VBO/VAO，解绑没有意义。
    }

    /**
     * 对应 1.20 {@code VertexBuffer#upload(BufferBuilder.RenderedBuffer)}。
     *
     * <p>
     * <b>降级点</b>：1.20 把它拷进 native 内存并 {@code glBufferData}；本实现只保留快照引用，
     * 由 {@link #draw()} 逐顶点重灌。旧快照直接丢弃（1.20 的 RenderedBuffer 由
     * {@code VertexBuffer} 负责 {@code release()}，本实现里 {@code release()} 本身是 no-op）。
     */
    public void upload(BufferBuilder.RenderedBuffer buffer) {
        if (closed) throw new IllegalStateException("Upload to a closed VertexBuffer");
        this.data = buffer;
        this.format = buffer.format();
    }

    /** 对应 1.20 {@code VertexBuffer#draw()}：把 CPU 快照通过 {@code Tessellator} 画出去。 */
    public void draw() {
        if (closed || data == null || data.isEmpty()) return;
        // TODO(L2): 上游 1.20 VertexBuffer#draw() 直接 glDrawArrays/glDrawElements（顶点已在显存）；
        // 1.7.10 无 VBO，改为 BufferUploader 逐顶点重灌 Tessellator，语义等价但慢得多。
        BufferUploader.draw(data);
    }

    /** 对应 1.20 {@code VertexBuffer#getFormat()}：返回最近一次上传的格式。 */
    @Nullable
    public VertexFormat getFormat() {
        return format;
    }

    /**
     * 对应 1.20 {@code VertexBuffer#isInvalid()}。
     *
     * <p>
     * 本实现里等于「已 {@link #close()}」。注意：<b>刚构造、还没上传</b>的实例是<b>有效</b>的
     * （1.20 里新构造的 {@code VertexBuffer} 已经拿到了 GL 缓冲名），
     * 因此「还没上传」由 {@link #isEmpty()} 表达，不能混进 {@code isInvalid()}。
     */
    public boolean isInvalid() {
        return closed;
    }

    /** 是否还有可绘制的顶点。 */
    public boolean isEmpty() {
        return data == null || data.isEmpty();
    }

    /** 上传时使用的 {@link Usage}（1.7.10 无实际效果，仅供调试打印）。 */
    public Usage usage() {
        return usage;
    }

    /** 对应 1.20 {@code VertexBuffer#close()}：丢弃 CPU 快照。 */
    @Override
    public void close() {
        this.closed = true;
        this.data = null;
        this.format = null;
    }
}
