// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/StaticMeshRenderer.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh;

import static com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache.*;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshCachePolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.StaticMeshCache;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshGeometryProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.debug.MeshRenderDebug;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshIntegerAttributes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshBatchRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshDrawTransform;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 临时共享静态网格的即时绘制入口，以及显式渲染阶段批次入口。
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 结构、枚举与方法名与上游一致；平台缺口只影响三处细节：
 * <ul>
 * <li>1.20 的 {@code RenderSystem#getModelViewMatrix()}/{@code getProjectionMatrix()} 返回
 * {@code org.joml.Matrix4f}，而 1.7.10 的 mcshim 版返回 {@code java.nio.FloatBuffer}
 * （直接读 GL 矩阵栈）。这里用 {@code new Matrix4f(FloatBuffer)}（按列主序读 16 个 float）
 * 换算，语义与 1.20 的矩阵完全相同；</li>
 * <li>{@code com.mojang.blaze3d.vertex.VertexFormatElement} 与
 * {@code DefaultVertexFormat.ELEMENT_UV1/ELEMENT_UV2} 不存在，改用
 * {@code mcshim VertexFormat.Element} / {@code VertexFormat.UV1_ELEMENT}/{@code UV2_ELEMENT}；</li>
 * <li>{@code part.buffer} 的类型是 1.7.10 侧的 {@code gpu.VertexBuffer}（1.20 原版 {@code VertexBuffer}
 * 不存在），见该类注释。</li>
 * </ul>
 */
public class StaticMeshRenderer {

    public enum BatchOrder {
        SUBMISSION,
        MATERIAL
    }

    public StaticMeshRenderer() {}

    /** 阶段所有者负责协调原版缓冲，并在改变阶段状态前关闭批次。 */
    public static StaticMeshBufferSource openBatch(ResourceLocation owner, BatchOrder order,
        BooleanSupplier isCurrent) {
        RenderSystem.assertOnRenderThread();
        if (MeshRenderDebug.attributes != null) throw new IllegalStateException("Batch uses production attributes");
        return new StaticMeshBufferSource(
            Objects.requireNonNull(owner),
            Objects.requireNonNull(order),
            Objects.requireNonNull(isCurrent));
    }

    public static StaticMeshBufferSource openBatch(ResourceLocation owner, BatchOrder order) {
        return openBatch(owner, order, () -> true);
    }

    public static boolean supportsInstanceAttributes(int packedLight, int packedOverlay) {
        return StaticMeshCache.supportsInstanceAttributes(packedLight, packedOverlay);
    }

    public static void invalidateOwner(ResourceLocation owner) {
        StaticMeshCache.invalidateOwner(owner);
    }

    public static boolean isOwnerEnabled(ResourceLocation owner) {
        return StaticMeshCache.isOwnerEnabled(owner);
    }

    public static StaticMeshCache.PreparationStats preparationStats() {
        return StaticMeshCache.preparationStats();
    }

    public static MeshDrawResult tryDraw(ResourceLocation owner, Object key, MeshGeometryProvider provider,
        PoseStack pose, int light, int overlay) {
        return tryDraw(owner, key, null, provider, pose, light, overlay, MeshCachePolicy.DEFAULT.idleRetention());
    }

    /** 在当前调用中准备网格，旧几何只查询；PENDING 接管本次调用，也包括部分绘制失败。 */
    public static MeshDrawResult tryDraw(ResourceLocation owner, Object key, @Nullable Object previousKey,
        MeshGeometryProvider provider, PoseStack pose, int light, int overlay, Duration retention) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(owner, "owner");
        if (!isOwnerEnabled(owner)) return MeshDrawResult.UNSUPPORTED;
        if (!prepareRequest(key, provider, pose, light, overlay, retention)) return MeshDrawResult.UNSUPPORTED;
        Entry target = acquire(new CacheKey(owner, key));
        Entry previous = previousKey == null || previousKey.equals(key) ? null
            : CACHE.get(new CacheKey(owner, previousKey));
        if (previous != null) pin(previous);
        MeshDrawResult result;
        try {
            Preparation prepared = prepareTarget(target, provider);
            if (prepared == Preparation.READY) {
                // 开始绘制后不能尝试其他网格，因为可能已经提交了部分绘制。
                result = draw(target, pose, light, overlay) ? MeshDrawResult.DRAWN : MeshDrawResult.PENDING;
            } else if (previous != null && previous.ready()
                && layoutSupported(previous)
                && (MeshRenderDebug.attributes != null || INTEGER_ATTRIBUTES.ensureReady())) {
                    result = draw(previous, pose, light, overlay) ? MeshDrawResult.DRAWN_PREVIOUS
                        : MeshDrawResult.PENDING;
                } else {
                    result = prepared == Preparation.UNSUPPORTED ? MeshDrawResult.UNSUPPORTED : MeshDrawResult.PENDING;
                }
        } catch (RuntimeException failure) {
            releaseEntry(target, retention);
            if (previous != null) releaseEntry(previous, retention);
            disableOwner(owner, "immediate request " + key, failure);
            if (MeshRenderDebug.attributes != null || failure.getSuppressed().length != 0) throw failure;
            return MeshDrawResult.PENDING;
        }
        releaseEntry(target, retention);
        if (previous != null) releaseEntry(previous, retention);
        return result;
    }

    /**
     * TODO(L2): 上游 v2/client/mesh/StaticMeshRenderer.java:112 的参数类型是 1.20 的
     * {@code com.mojang.blaze3d.vertex.VertexFormatElement}；1.7.10 无该类，
     * 改用 mcshim 的 {@link VertexFormat.Element}（<b>签名变化</b>，语义不变）。
     */
    public static int attributeIndex(VertexFormat format, VertexFormat.Element element) {
        List<VertexFormat.Element> elements = format.getElements();
        for (int i = 0; i < elements.size(); i++) if (elements.get(i) == element) return i;
        return -1;
    }

    public static boolean draw(Entry entry, PoseStack pose, int packedLight, int packedOverlay) {
        MeshRenderDebug.DrawAttributes attributes = MeshRenderDebug.attributes;
        int previous = -1;
        boolean debugStarted = false;
        try {
            if (attributes != null) {
                attributes.beginItem();
                debugStarted = true;
            } else {
                previous = INTEGER_ATTRIBUTES.beginItem();
            }
            boolean drawn = drawParts(entry, pose, packedLight, packedOverlay, attributes);
            if (debugStarted) attributes.endItem();
            else INTEGER_ATTRIBUTES.endItem(previous);
            return drawn;
        } catch (RuntimeException exception) {
            try {
                if (debugStarted) attributes.endItem();
                else if (previous >= 0) INTEGER_ATTRIBUTES.endItem(previous);
            } catch (RuntimeException cleanup) {
                exception.addSuppressed(cleanup);
            }
            disableOwner(entry.key.owner(), "immediate draw " + entry.key.geometry(), exception);
            if (attributes != null || exception.getSuppressed().length != 0) throw exception;
            return false;
        }
    }

    public static boolean drawParts(Entry entry, PoseStack pose, int packedLight, int packedOverlay,
        @Nullable MeshRenderDebug.DrawAttributes attributes) {
        // TODO(L2): 上游 v2/client/mesh/StaticMeshRenderer.java:148 直接 new MeshDrawTransform(
        // RenderSystem.getModelViewMatrix(), pose.last().pose())；1.7.10 的 mcshim
        // RenderSystem#getModelViewMatrix() 返回 java.nio.FloatBuffer（读 GL 矩阵栈），
        // 这里用 Matrix4f(FloatBuffer) 按列主序换算成 JOML 矩阵，语义与 1.20 一致。
        MeshDrawTransform transform = new MeshDrawTransform(
            new Matrix4f(RenderSystem.getModelViewMatrix()),
            pose.last()
                .pose());
        long lightOffset = MeshIntegerAttributes.parameterOffset(packedLight);
        long overlayOffset = MeshIntegerAttributes.parameterOffset(packedOverlay);
        // TODO(L2): 上游同处用 RenderSystem.getProjectionMatrix()（1.20 返回 Matrix4f）；
        // 1.7.10 返回 FloatBuffer，同样用 Matrix4f(FloatBuffer) 换算。
        MeshBatchRenderer batch = new MeshBatchRenderer(new Matrix4f(RenderSystem.getProjectionMatrix()));
        try {
            for (Part part : entry.parts) {
                if (!batch.beginMaterial(part.material, transform.modelView)) {
                    throw new IllegalStateException("No active shader for " + part.material);
                }
                batch.bind(part.buffer);
                if (attributes != null) {
                    part.attributes.invalidate();
                    VertexFormat format = part.buffer.getFormat();
                    attributes.setupPart(
                        part.buffer,
                        format,
                        part.instanceLight,
                        // TODO(L2): 上游用 DefaultVertexFormat.ELEMENT_UV2 / ELEMENT_UV1；
                        // 1.7.10 的 mcshim 里叫 VertexFormat.UV2_ELEMENT / UV1_ELEMENT。
                        attributeIndex(format, VertexFormat.UV2_ELEMENT),
                        attributeIndex(format, VertexFormat.UV1_ELEMENT),
                        packedLight,
                        packedOverlay);
                } else {
                    INTEGER_ATTRIBUTES.setupPart(part.attributes, part.instanceLight, lightOffset, overlayOffset);
                }
                batch.drawItem(transform);
                batch.finishMaterial();
            }
            batch.close();
        } catch (RuntimeException failure) {
            batch.abort(failure);
            throw failure;
        }
        return true;
    }

}
