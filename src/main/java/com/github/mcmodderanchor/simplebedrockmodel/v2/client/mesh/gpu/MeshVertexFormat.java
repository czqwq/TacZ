// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/gpu/MeshVertexFormat.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.tacz.guns.mcshim.BufferBuilder;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 探测渲染兼容钩子提供的实际 BufferBuilder 顶点布局。
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 上游的探针用 1.20 的 {@code BufferBuilder#endOrDiscardIfEmpty()} 与
 * {@code RenderedBuffer#drawState().format()}。1.7.10 的 mcshim {@code BufferBuilder} 没有前者
 * （{@code end()} 在空缓冲时已返回 {@code null}），{@code RenderedBuffer} 也没有 {@code DrawState}，
 * 格式直接由 {@code RenderedBuffer#format()} 给出。打包版本的 {@code overlayCoords(int)} /
 * {@code uv2(int)} 在 mcshim 里是 {@code overlay(int)} / {@code lightmap(int)}。
 *
 * <p>
 * <b>探测在 1.7.10 上没有实际用途</b>：1.7.10 的顶点布局由 {@code Tessellator} 固定，
 * 不会因为光影包而漂移，因此 {@link #probe()} 只是原样返回 {@code MeshSink.FORMAT}。
 */
public class MeshVertexFormat {

    public MeshVertexFormat() {}

    /** 用最小代价问一次"当前生效的顶点格式"：构造 1 个 quad 再丢弃，不碰 GL。 */
    public static VertexFormat probe() {
        BufferBuilder probe = new BufferBuilder(1024);
        probe.begin(VertexFormat.Mode.QUADS, MeshSink.FORMAT);
        for (int i = 0; i < 4; i++) {
            probe.vertex(0.0, 0.0, 0.0)
                .color(255, 255, 255, 255)
                .uv(0.0F, 0.0F)
                // TODO(L2): 上游 v2/client/mesh/gpu/MeshVertexFormat.java:24 用打包版 overlayCoords(int)；
                // mcshim 把它拆成 overlayCoords(int,int)，打包版叫 overlay(int)。
                .overlay(0)
                // TODO(L2): 上游第 25 行用打包版 uv2(int)；mcshim 里打包版叫 lightmap(int)。
                .lightmap(0)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
        }
        // TODO(L2): 上游 v2/client/mesh/gpu/MeshVertexFormat.java:29 用 BufferBuilder#endOrDiscardIfEmpty()；
        // mcshim 只有 end()，且空缓冲时同样返回 null。
        BufferBuilder.RenderedBuffer rendered = probe.end();
        if (rendered == null) {
            return null;
        }
        // TODO(L2): 上游第 33 行取 rendered.drawState().format()；mcshim 的 RenderedBuffer 没有 DrawState，
        // 格式直接由 format() 给出。
        VertexFormat format = rendered.format();
        rendered.release();
        return format;
    }

}
