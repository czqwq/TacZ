// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/debug/MeshRenderDebug.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.debug;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.VertexBuffer;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 开发环境对照入口；不持有 GPU 资源，也不提供可切换的生产后端配置。
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 上游用 {@code FMLEnvironment.production}（1.13+ FML 的加载环境标志）判断「是否生产环境」，
 * 只在开发环境里允许挂调试用的顶点属性对照实现。1.7.10 的 FML <b>没有</b> {@code FMLEnvironment}
 * （也没有等价的公开常量），因此本移植直接把 {@link #withDrawAttributes} 视为生产环境：
 * <b>恒抛</b> {@code IllegalStateException}。
 *
 * <p>
 * 后果（有意为之，不是缺陷）：{@link #attributes} 永远保持 {@code null}，
 * {@code StaticMeshRenderer}/{@code StaticMeshCache}/{@code StaticMeshBufferSource} 里所有
 * 「调试对照路径」分支都恒为未启用，只走普通顶点路径。这与上游「生产环境不启用调试后端」一致。
 *
 * <p>
 * 字段 {@code buffer} 的类型从 1.20 原版 {@code com.mojang.blaze3d.vertex.VertexBuffer}
 * 换成同包新增的 {@link VertexBuffer}（见该类注释）。
 */
@ApiStatus.Internal
public class MeshRenderDebug {

    public interface DrawAttributes {

        public void beginItem();

        public void setupPart(VertexBuffer buffer, VertexFormat format, boolean instanceLight, int lightIndex,
            int overlayIndex, int packedLight, int packedOverlay);

        public void endItem();
    }

    public static DrawAttributes attributes;

    public MeshRenderDebug() {}

    public static void withDrawAttributes(@Nullable DrawAttributes override, Runnable action) {
        RenderSystem.assertOnRenderThread();
        // TODO(L2): 上游 v2/client/mesh/debug/MeshRenderDebug.java:36 用
        // net.minecraftforge.fml.loading.FMLEnvironment#production；1.7.10 无该类，恒视为生产环境。
        throw new IllegalStateException("Development instrumentation only");
    }

}
