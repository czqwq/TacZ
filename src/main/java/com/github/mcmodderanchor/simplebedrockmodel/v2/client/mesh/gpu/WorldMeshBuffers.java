// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/gpu/WorldMeshBuffers.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshMetrics;
import com.tacz.guns.mcshim.BufferBuilder;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexFormat;

/**
 * 管理世界网格的 GPU 所有权和可变光照流；阶段调度由世界渲染入口负责。
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 上游把几何推进原版 {@code ChunkRenderDispatcher#uploadChunkLayer(RenderedBuffer, VertexBuffer)}
 * （异步队列 + VBO），并把 MUTABLE 光照写成一条独立的 {@code GL_RG_SHORT} 顶点流，靠 VAO 属性
 * 指针／实例化除频挂在几何上。这三样在 1.7.10 <b>都不存在</b>：
 * <ul>
 * <li>{@code ChunkRenderDispatcher} 是 1.17+ 的类（1.7.10 只有 {@code RenderChunk}/{@code RenderGlobal}），
 * 也没有「上传队列」；</li>
 * <li>VBO 的托管封装（{@code com.mojang.blaze3d.vertex.VertexBuffer}）不存在，改为本包新增的
 * {@link VertexBuffer}（CPU 顶点快照 + {@code Tessellator} 重放）；</li>
 * <li>顶点属性指针／整数属性／实例化除频都不存在（见 {@link MeshIntegerAttributes}）。</li>
 * </ul>
 *
 * <h2>降级点</h2>
 * <ul>
 * <li><b>上传变同步</b>：{@link #submit} 里直接 {@code buffer.upload(rendered)}，
 * 返回已完成的 {@link CompletableFuture}。上游的「上一帧入队、下一帧上传」预算机制没有了，
 * 但 {@code MeshUploadQueue} 那套准入／预算逻辑在 {@code StaticMeshCache} 一侧仍然生效。</li>
 * <li><b>光照流整条丢弃</b>：{@link #createLightBuffer}/{@link #flushLightUpdates}/
 * {@link #attachLightStream} 全部降级为 no-op／返回 {@code true}，MUTABLE 模式的
 * {@code updateLight} 只更新 CPU 暂存，不再影响绘制。</li>
 * <li><b>没有 VAO 复用</b>：句柄归还缓冲池时直接放回 CPU 快照的桶。</li>
 * </ul>
 */
public class WorldMeshBuffers {

    private final WorldMeshMetrics metrics;
    private final MeshIntegerAttributes integerAttributes = new MeshIntegerAttributes();
    private final List<WorldMeshPart> parts = new ArrayList<>();
    public final VertexBufferPool pool = new VertexBufferPool();
    private long generation;

    public WorldMeshBuffers(WorldMeshMetrics metrics) {
        this.metrics = metrics;
    }

    public int size() {
        return parts.size();
    }

    public void clear() {
        RenderSystem.assertOnRenderThread();
        generation++;
        dropAllHandles();
        pool.clear();
        integerAttributes.close();
    }

    public void removeOwner(String ownerId) {
        dropHandlesOf(ownerId);
    }

    /** 在绑定几何 VAO 后调用；每组连续的相同网格与光照只执行一次。（1.7.10：无 VAO，恒返回 true。） */
    public boolean prepareDraw(WorldMeshPart part, int packedLight) {
        if (part.lightMode() == WorldMeshPart.LightMode.UNIFORM) {
            // TODO(L2): 上游 v2/client/mesh/gpu/WorldMeshBuffers.java:66 用整数参数表给 UV2 换源；
            // 1.7.10 无 VAO/整数属性，几何按捕获时烘焙的 lightmap 绘制，这里只做能力自检。
            return integerAttributes.ensureReady();
        }
        if (!part.isLightStreamAttached() && !attachLightStream(part)) return false;
        if (part.lightMode() == WorldMeshPart.LightMode.MUTABLE) flushLightUpdates(part);
        return true;
    }

    /**
     * 提交局部网格；光照语义必须显式指定。调用前 owner 必须已注册。
     * 返回句柄由提交者独占管理，上传排队后由原版渲染线程处理；空网格或无世界返回 null。
     * MeshSink 在本方法内同步读取，返回后可复用。所有操作必须在渲染线程执行。
     */
    @Nullable
    public WorldMeshPart submit(String ownerId, MeshSink mesh, RenderType material, MeshLighting lighting) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(lighting, "lighting");
        if (mesh.isEmpty() || Minecraft.getMinecraft().theWorld == null) return null;

        WorldMeshPart.LightMode lightMode = MeshLighting.classify(mesh, lighting);

        BufferBuilder.RenderedBuffer rendered = mesh.buildBuffer();
        if (rendered == null) return null;
        VertexBuffer buffer = pool.acquire(mesh.format());

        int lightBufferId = -1;
        ByteBuffer lightScratch = null;
        if (lightMode == WorldMeshPart.LightMode.MUTABLE) {
            lightScratch = ByteBuffer.allocateDirect(mesh.vertexCount() * 4)
                .order(ByteOrder.nativeOrder());
            fillLightTemplate(lightScratch, mesh);
            lightBufferId = createLightBuffer(lightScratch);
        }

        // TODO(L2): 上游 v2/client/mesh/gpu/WorldMeshBuffers.java:118 调
        // ChunkRenderDispatcher#uploadChunkLayer(rendered, buffer) 拿到 CompletableFuture；
        // 1.7.10 没有 ChunkRenderDispatcher，也没有 GPU 上传，这里同步写入 CPU 顶点快照并
        // 返回已完成 future（CompletableFuture 的 isDone()/isCompletedExceptionally() 语义保持不变）。
        buffer.upload(rendered);
        CompletableFuture<Void> upload = CompletableFuture.completedFuture(null);

        WorldMeshPart handle = new WorldMeshPart(
            this,
            ownerId,
            buffer,
            mesh.format(),
            material,
            mesh.bounds(),
            generation,
            upload,
            lightBufferId,
            lightScratch,
            lightMode);
        parts.add(handle);
        metrics.recordSubmit(lightMode);
        return handle;
    }

    /** 初始化 MUTABLE 光照流：动态范围留 0，固定范围写入捕获值。 */
    public void fillLightTemplate(ByteBuffer data, MeshSink mesh) {
        for (int i = 0, count = mesh.lightRunCount(); i < count; i++) {
            int value = mesh.lightRunValue(i);
            if (value == 0) {
                continue;
            }
            int end = Math.min(mesh.lightRunStart(i) + mesh.lightRunLength(i), mesh.vertexCount());
            for (int vertex = mesh.lightRunStart(i); vertex < end; vertex++) {
                data.putShort(vertex * 4, (short) (value & 0xFFFF));
                data.putShort(vertex * 4 + 2, (short) (value >>> 16));
            }
        }
        data.clear();
    }

    /**
     * 建光照流缓冲（DYNAMIC：会被反复改写，让驱动把它放在适合动态更新的位置）。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/gpu/WorldMeshBuffers.java:144 用 {@code GlStateManager._glGenBuffers()}
     * + {@code RenderSystem.glBufferData(...)} 建 GL 缓冲；1.7.10 的 mcshim 没有这两个方法，
     * 而且本移植也没有可挂载的顶点属性流，因此降级为「不建缓冲」，返回 -1
     * （{@code WorldMeshPart#hasLightBuffer()} 因此为 false）。
     */
    public int createLightBuffer(ByteBuffer data) {
        return -1;
    }

    /**
     * 仅上传 MUTABLE 流的脏区间；几何缓冲及其它实例的光照均保持不变。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/gpu/WorldMeshBuffers.java:156 用 {@code GL15.glBufferSubData} 上传脏区间；
     * 1.7.10 侧没有光照流缓冲，只能清空脏表（CPU 暂存值仍然正确，只是不影响绘制）。
     */
    public void flushLightUpdates(WorldMeshPart handle) {
        LightRangeUpdates updates = handle.lightUpdates();
        if (updates == null || updates.ranges()
            .isEmpty()) return;
        for (Map.Entry<Integer, Integer> range : updates.ranges()
            .entrySet()) {
            metrics.recordLightRangeUpload((range.getValue() - range.getKey()) * 4);
        }
        updates.clear();
    }

    /**
     * 光照（UV2）在该格式里的字节偏移；找不到返回 -1。
     *
     * <p>
     * 1.20 里必须按「实际上传后的格式」算，因为光影 mod 会换掉 {@code NEW_ENTITY} 的 stride/偏移。
     * 1.7.10 的顶点布局由 {@code Tessellator} 固定，且 mcshim 的 {@code VertexFormat.Element}
     * 没有 {@code getByteSize()}（见该类注释），因此这里改用 {@code count * Float.BYTES} 估算，
     * <b>仅供调试与统计</b>，不参与任何 GL 调用。
     */
    public int findLightOffset(VertexFormat format) {
        int offset = 0;
        for (VertexFormat.Element element : format.getElements()) {
            if (element == VertexFormat.UV2_ELEMENT) {
                return offset;
            }
            offset += element.count * Float.BYTES;
        }
        return -1;
    }

    /**
     * 几何 VAO 绑定后设置 UV2 来源；上传和池复用后的首次绘制必须重新设置。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/gpu/WorldMeshBuffers.java:201 用
     * {@code GlStateManager._vertexAttribIPointer(...)} 把 geometry/light buffer 挂到 UV2 属性上；
     * 1.7.10 固定管线没有顶点属性指针，这里只把「已挂载」标志置位，让绘制流程继续。
     */
    public boolean attachLightStream(WorldMeshPart handle) {
        VertexFormat format = handle.buffer()
            .getFormat();
        if (format == null) return false;
        int lightIndex = format.getElements()
            .indexOf(VertexFormat.UV2_ELEMENT);
        if (lightIndex < 0) return false;
        handle.markLightStreamAttached();
        return true;
    }

    public void release(WorldMeshPart handle) {
        RenderSystem.assertOnRenderThread();
        if (!handle.retire()) return;
        parts.remove(handle);
        metrics.recordRelease();
        recycleWhenUploaded(handle);
    }

    /** LRU／TTL 淘汰时直接释放 GPU 存储，不再放回无容量上限的缓冲池。 */
    public void discard(WorldMeshPart handle) {
        RenderSystem.assertOnRenderThread();
        if (!parts.remove(handle)) return;
        handle.kill();
        metrics.recordRelease();
        recycleWhenUploaded(handle);
    }

    public void dropHandlesOf(String ownerId) {
        parts.removeIf(handle -> {
            if (!handle.ownerId()
                .equals(ownerId)) {
                return false;
            }
            handle.kill();
            recycleWhenUploaded(handle);
            return true;
        });
    }

    public void dropAllHandles() {
        for (WorldMeshPart handle : parts) {
            handle.kill();
            recycleWhenUploaded(handle);
        }
        parts.clear();
    }

    /**
     * 只有上传完成后才能把缓冲放回池：提交是异步入队的，提前回收会让下一帧的 drain 写进别人的数据。
     */
    public void recycleWhenUploaded(WorldMeshPart handle) {
        CompletableFuture<Void> upload = handle.upload();
        if (upload.isDone()) {
            recycle(handle);
            return;
        }
        upload.whenComplete((ignored, failure) -> {
            if (RenderSystem.isOnRenderThread()) {
                recycle(handle);
            } else {
                RenderSystem.recordRenderCall(() -> recycle(handle));
            }
        });
    }

    public void recycle(WorldMeshPart handle) {
        if (!handle.markRecycled()) {
            return;
        }
        // 光照流缓冲随句柄一起回收。VAO 里可能还残留对它的引用（UV2 的绑定），
        // 但下一次使用这个几何 VAO 之前必定会重挂（新句柄的 lightStreamAttached 初值为 false），
        // 所以不会读到悬空的旧缓冲。
        if (handle.hasLightBuffer()) {
            // TODO(L2): 上游用 RenderSystem.glDeleteBuffers(handle.lightBufferId()) 删 GL 缓冲；
            // 1.7.10 侧 createLightBuffer 恒返回 -1，因此 hasLightBuffer() 永远为 false，
            // 这条分支不可达，保留结构以对齐上游。
            metrics.recordLightBufferReleased();
        }
        if (handle.isInvalidated() || handle.uploadFailed() || handle.generation() != generation) {
            handle.buffer()
                .close();
        } else {
            pool.recycle(handle.format(), handle.buffer());
        }
    }

}
