// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/cache/StaticMeshCache.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.jetbrains.annotations.ApiStatus;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshGeometryProvider;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.MeshSink;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.debug.MeshRenderDebug;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshIntegerAttributes;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshVertexFormat;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.VertexBuffer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.VertexBufferPool;
import com.tacz.guns.mcshim.BufferBuilder;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexFormat;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 共享静态几何的准备任务、帧预算、近期驻留与按所有者失效。
 *
 * <h2>1.7.10 移植说明</h2>
 * <p>
 * 上游用 {@code @Mod.EventBusSubscriber(... bus = Bus.FORGE)} 自动注册
 * {@code TickEvent.RenderTickEvent}/{@code LevelEvent.Unload} 两个静态监听器。1.7.10 的 FML
 * <b>没有</b> {@code @Mod.EventBusSubscriber}（也没有 {@code Dist}），因此改为在静态块里
 * 用 {@code FMLCommonHandler.instance().bus().register(...)}（tick 事件，1.7.10 走 FML 总线）
 * 与 {@code MinecraftForge.EVENT_BUS.register(...)}（世界事件，走 Forge 总线）注册本类的单例实例。
 * 方法体与注解参数语义保持上游原样。
 *
 * <p>
 * 其余降级点：
 * <ul>
 * <li>{@code GLCapabilities}（LWJGL3）→ {@code ContextCapabilities}（LWJGL2），
 * 由 {@link MeshIntegerAttributes#currentContext()} 提供（含无上下文保护）；</li>
 * <li>{@code LevelEvent.Unload#getLevel()} → {@link WorldEvent.Unload#world}；</li>
 * <li>{@code Minecraft#level} → {@code Minecraft#theWorld}；</li>
 * <li>{@code RenderedBuffer#drawState().format()} → {@code RenderedBuffer#format()}；</li>
 * <li>{@code RenderedBuffer#vertexBuffer()/indexBuffer()}（native 缓冲视图）不存在，
 * 字节数改用「顶点数 × 格式字节数」估算；</li>
 * <li>上传不再涉及 VAO/VBO 绑定（1.7.10 没有），见 {@code PendingUpload#upload()}；</li>
 * <li>{@code com.mojang.blaze3d.vertex.VertexBuffer} → 1.7.10 侧的
 * {@code gpu.VertexBuffer}（CPU 顶点快照）。</li>
 * </ul>
 */
public class StaticMeshCache {

    public enum Preparation {
        READY,
        PENDING,
        UNSUPPORTED
    }

    private static final int MAX_ENTRIES = 128;
    private static final long MAX_BYTES = 256L * 1024L * 1024L;
    private static final int FORMAT_CHECK_DRAWS = 60;
    private static VertexBufferPool POOL = new VertexBufferPool();
    public static final MeshIntegerAttributes INTEGER_ATTRIBUTES = new MeshIntegerAttributes();
    public static final Map<CacheKey, Entry> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    public static final MeshPreparationPolicy POLICY = MeshPreparationPolicy.DEFAULT;
    private static final MeshPathFailures<ResourceLocation> FAILURES = new MeshPathFailures<>();
    private static final MeshUploadQueue<Entry> UPLOADS = new MeshUploadQueue<>(POLICY, System::nanoTime);
    private static final MeshCacheResidency<CacheKey, Entry> RESIDENCY = new MeshCacheResidency<>(
        CACHE,
        MAX_ENTRIES,
        MAX_BYTES,
        System::nanoTime,
        entry -> entry.references,
        Entry::preparing,
        entry -> entry.bytes,
        StaticMeshCache::discard);

    private static WorldClient level;
    private static Object geometryContext;
    private static VertexFormat probedFormat;
    private static int formatCheckCountdown = FORMAT_CHECK_DRAWS;
    public static long generation;
    private static long frame;
    private static int capturesInFrame;
    private static long captureNanosInFrame, capturedBytesInFrame, capturesTotal;
    private static long residentBytes, reservedBytes;

    public static class Part {

        public final VertexBuffer buffer;
        public final VertexFormat requestedFormat;
        public final RenderType material;
        public final CompletableFuture<Void> upload;
        public final boolean instanceLight;
        /** 1.20 里是 {@code GLCapabilities}；1.7.10 用 {@code ContextCapabilities}（可为 null）。 */
        public final Object context;
        public final MeshIntegerAttributes.PartState attributes = new MeshIntegerAttributes.PartState();
        public ResourceLocation owner;
        public long residentBytes;

        public Part(VertexBuffer buffer, VertexFormat requestedFormat, RenderType material,
            CompletableFuture<Void> upload, boolean instanceLight) {
            this.buffer = buffer;
            this.requestedFormat = requestedFormat;
            this.material = material;
            this.upload = upload;
            this.instanceLight = instanceLight;
            // TODO(L2): 上游 v2/client/mesh/cache/StaticMeshCache.java:104 用 GL.getCapabilities()
            // （LWJGL3 GLCapabilities）；1.7.10 是 LWJGL2，等价物见 MeshIntegerAttributes.currentContext()。
            this.context = MeshIntegerAttributes.currentContext();
        }
    }

    public record CacheKey(ResourceLocation owner, Object geometry) {}

    public static class Entry {

        public final CacheKey key;
        public int references;
        public List<Part> parts;
        public long bytes;
        public long retryAtNanos;
        public int failures;
        public boolean unsupported;
        public long capturedGeneration;
        public boolean reserved;

        public Entry(CacheKey key) {
            this.key = key;
        }

        public boolean ready() {
            if (parts == null || parts.isEmpty()) return false;
            for (Part part : parts) if (!part.upload.isDone() || part.upload.isCompletedExceptionally()) return false;
            return true;
        }

        public boolean failed() {
            if (parts != null) for (Part part : parts) if (part.upload.isCompletedExceptionally()) return true;
            return false;
        }

        public boolean preparing() {
            return !unsupported && (parts == null || !ready() && !failed());
        }
    }

    public record PreparationStats(long frame, int entries, int ready, int pendingParts, long pendingBytes,
        long residentBytes, long reservedBytes, int captures, long capturedBytes, long captureNanos, int uploads,
        long uploadedBytes, long uploadNanos, long capturesTotal) {}

    public static PreparationStats preparationStats() {
        RenderSystem.assertOnRenderThread();
        return new PreparationStats(
            frame,
            CACHE.size(),
            (int) CACHE.values()
                .stream()
                .filter(Entry::ready)
                .count(),
            UPLOADS.pendingParts(),
            UPLOADS.pendingBytes(),
            residentBytes,
            reservedBytes,
            capturesInFrame,
            capturedBytesInFrame,
            captureNanosInFrame,
            UPLOADS.uploadedParts(),
            UPLOADS.uploadedBytes(),
            UPLOADS.uploadNanos(),
            capturesTotal);
    }

    public static boolean isOwnerEnabled(ResourceLocation owner) {
        return FAILURES.enabled(owner);
    }

    public static long currentFrame() {
        return frame;
    }

    /** 首次异常记录完整调用栈并取消该 owner；清理失败时传播异常，禁止带着损坏状态继续渲染。 */
    public static void disableOwner(ResourceLocation owner, String stage, RuntimeException failure) {
        if (!FAILURES.disable(owner)) return;
        boolean cleaned = true;
        try {
            invalidateOwner(owner);
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
            cleaned = false;
        }
        SimpleBedrockModel.LOGGER.error(
            "Static mesh owner {} disabled after failure in {}. "
                + "Reload resources or change worlds to reset this path.",
            owner,
            stage,
            failure);
        if (!cleaned) throw failure;
    }

    /**
     * 渲染线程上的轻量能力预检，供需要接管待准备网格的接入方使用。
     * 返回 false 表示缺少 GL 能力，或光照／覆盖坐标超出参数表范围，应使用旧渲染路径。
     * 此处不捕获几何，也不分配或上传参数表；故障后端由条件分支拒绝。
     */
    public static boolean supportsInstanceAttributes(int packedLight, int packedOverlay) {
        RenderSystem.assertOnRenderThread();
        Object current = MeshIntegerAttributes.currentContext();
        if (geometryContext != current) {
            if (geometryContext != null) {
                // 丢弃旧池的记录；不能在新上下文中删除或复用旧上下文的 GL 对象标识。
                POOL = new VertexBufferPool();
                clear();
            }
            geometryContext = current;
        }
        return INTEGER_ATTRIBUTES.supports(MeshIntegerAttributes.currentContext(), packedLight, packedOverlay);
    }

    public static Entry acquire(CacheKey key) {
        Entry entry = CACHE.computeIfAbsent(key, Entry::new);
        pin(entry);
        return entry;
    }

    public static void pin(Entry entry) {
        RESIDENCY.request(entry.key, entry);
        entry.references++;
    }

    public static void releaseEntry(Entry entry, Duration idleRetention) {
        if (entry.references <= 0) throw new IllegalStateException("Mesh reference already released");
        entry.references--;
        RESIDENCY.retention(entry.key, entry, idleRetention);
    }

    public static void discard(Entry entry) {
        RESIDENCY.forget(entry.key, entry);
        CACHE.remove(entry.key, entry);
        retire(entry, true);
    }

    public static void failEntry(Entry entry) {
        if (entry.parts == null) return;
        retire(entry, true);
        entry.retryAtNanos = System.nanoTime() + retryDelay(entry.failures++);
    }

    public static boolean prepareRequest(Object key, MeshGeometryProvider provider, PoseStack pose, int packedLight,
        int packedOverlay, Duration retention) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(retention, "retention");
        if (retention.isNegative()) throw new IllegalArgumentException("Negative idle retention");
        retention.toNanos();
        WorldClient current = Minecraft.getMinecraft().theWorld;
        if (current == null || !supportsInstanceAttributes(packedLight, packedOverlay)) return false;
        if (current != level) {
            clear();
            level = current;
        }
        if (--formatCheckCountdown <= 0) {
            formatCheckCountdown = FORMAT_CHECK_DRAWS;
            VertexFormat format = MeshVertexFormat.probe();
            if (format != null && probedFormat != null && probedFormat != format) clear();
            if (format != null) probedFormat = format;
        }
        return true;
    }

    public static Preparation prepareTarget(Entry entry, MeshGeometryProvider provider) {
        if (!isOwnerEnabled(entry.key.owner())) return Preparation.UNSUPPORTED;
        if (entry.unsupported) return Preparation.UNSUPPORTED;
        if (entry.failed()) {
            retire(entry, true);
            entry.parts = null;
            entry.retryAtNanos = System.nanoTime() + retryDelay(entry.failures++);
        }
        if (entry.parts == null) {
            long now = System.nanoTime();
            if (now < entry.retryAtNanos || !captureBudgetAvailable(now)
                || UPLOADS.pendingBytes() >= POLICY.pendingBytes()) return Preparation.PENDING;
            long start = System.nanoTime();
            boolean captured = capture(entry, entry.key, provider);
            capturesInFrame++;
            captureNanosInFrame += System.nanoTime() - start;
            if (!captured) {
                if (!isOwnerEnabled(entry.key.owner())) return Preparation.UNSUPPORTED;
                if (entry.unsupported) return Preparation.UNSUPPORTED;
                entry.retryAtNanos = System.nanoTime() + retryDelay(entry.failures++);
                return Preparation.PENDING;
            }
        }
        if (!entry.ready()) return Preparation.PENDING;
        if (!layoutSupported(entry)) return Preparation.UNSUPPORTED;
        if (MeshRenderDebug.attributes == null && !INTEGER_ATTRIBUTES.ensureReady()) return Preparation.PENDING;

        return Preparation.READY; // 网格已就绪，可立即绘制，也可由批次持有引用后入队。
    }

    public static boolean captureBudgetAvailable(long now) {
        return capturesInFrame == 0
            || capturesInFrame < POLICY.maxCaptures() && captureNanosInFrame < POLICY.captureNanos();
    }

    public static long retryDelay(int failures) {
        return Math.min(5_000_000_000L, 100_000_000L << Math.min(failures, 5));
    }

    public static boolean capture(Entry entry, Object key, MeshGeometryProvider provider) {
        if (!isOwnerEnabled(entry.key.owner())) return false;
        if (entry.parts != null) return true;
        long capturedGeneration = generation;
        GeometryCollector collector = new GeometryCollector();
        List<Part> parts = new ArrayList<>();
        List<PendingUpload> uploads = new ArrayList<>();
        try {
            if (!provider.capture(collector)) return false;
            Map<GeometryCollector.Pass, MeshSink> meshes = collector.snapshot();
            if (meshes.isEmpty()) {
                entry.unsupported = true;
                return false;
            }
            long bytes = 0;
            for (Map.Entry<GeometryCollector.Pass, MeshSink> pass : meshes.entrySet()) {
                MeshSink mesh = pass.getValue();
                boolean dynamic = false;
                boolean fixed = false;
                for (int i = 0; i < mesh.lightRunCount(); i++) {
                    if (mesh.lightRunValue(i) == 0) dynamic = true;
                    else fixed = true;
                }
                if (dynamic && fixed) {
                    uploads.forEach(PendingUpload::cancel);
                    parts.forEach(part -> release(part, capturedGeneration, true));
                    SimpleBedrockModel.LOGGER.warn("Immediate mesh has mixed per-vertex lighting: {}", key);
                    entry.unsupported = true;
                    return false;
                }
                BufferBuilder.RenderedBuffer rendered = mesh.buildBuffer();
                if (rendered == null) continue;
                // 当前调用内编码，以保存兼容钩子的分类数据；跨帧队列只持有独立上传数据。
                // TODO(L2): 上游 v2/client/mesh/cache/StaticMeshCache.java:338 用 rendered.drawState().format()；
                // mcshim 的 RenderedBuffer 没有 DrawState，格式直接由 format() 给出。
                VertexFormat format = rendered.format();
                Part part;
                try {
                    part = new Part(
                        POOL.acquire(format),
                        format,
                        pass.getKey()
                            .material(),
                        new CompletableFuture<>(),
                        dynamic);
                } catch (RuntimeException failure) {
                    rendered.release();
                    throw failure;
                }
                part.owner = entry.key.owner();
                parts.add(part);
                PendingUpload upload;
                try {
                    upload = new PendingUpload(entry, part, rendered, capturedGeneration);
                } catch (RuntimeException failure) {
                    rendered.release();
                    throw failure;
                }
                uploads.add(upload);
                bytes += upload.bytes();
            }
            if (parts.isEmpty()) return false;
            if (capturedGeneration != generation || CACHE.get(entry.key) != entry) {
                uploads.forEach(PendingUpload::cancel);
                parts.forEach(part -> release(part, capturedGeneration, true));
                return false;
            }
            entry.parts = parts;
            entry.bytes = bytes;
            entry.capturedGeneration = capturedGeneration;
            entry.failures = 0;
            if (!UPLOADS.enqueue(
                entry,
                uploads,
                () -> CACHE.get(entry.key) == entry && entry.capturedGeneration == generation,
                failure -> {
                    disableOwner(entry.key.owner(), "upload " + entry.key.geometry(), failure);
                    if (failure.getSuppressed().length != 0) throw failure;
                })) throw new IllegalStateException("Mesh preparation already queued");
            capturedBytesInFrame += bytes;
            capturesTotal++;
            return true;
        } catch (RuntimeException exception) {
            uploads.forEach(PendingUpload::cancel);
            parts.forEach(part -> part.upload.cancel(false));
            parts.forEach(part -> release(part, capturedGeneration, true));
            entry.parts = null;
            entry.bytes = 0;
            disableOwner(entry.key.owner(), "capture " + key, exception);
            return false;
        }
    }

    private static boolean admit(Entry entry) {
        if (entry.reserved) return true;
        while (reservedBytes > 0 && entry.bytes > POLICY.residentBytes() - reservedBytes) {
            if (!RESIDENCY.reclaimIdle()) return false;
        }
        // 至少允许一个完整几何单独驻留，避免大模型上传部分 Part 后永久等待。
        reservedBytes += entry.bytes;
        entry.reserved = true;
        return true;
    }

    private static class PendingUpload implements MeshUploadQueue.Upload {

        private final Entry entry;
        private final Part part;
        private final long generation;
        private final long bytes;
        private BufferBuilder.RenderedBuffer data;

        PendingUpload(Entry entry, Part part, BufferBuilder.RenderedBuffer data, long generation) {
            this.entry = entry;
            this.part = part;
            this.data = data;
            this.generation = generation;
            // TODO(L2): 上游 v2/client/mesh/cache/StaticMeshCache.java:421 用 data.vertexBuffer().remaining()
            // + data.indexBuffer().remaining() 读 native 缓冲的真实字节数；mcshim 的 RenderedBuffer 只有
            // Java 数组，没有这两个视图，改为「顶点数 × 格式字节数」估算（只用于驻留预算，语义足够）。
            VertexFormat format = data.format();
            this.bytes = (long) data.vertexCount() * (format == null ? 0 : format.getVertexSize());
        }

        @Override
        public long bytes() {
            return bytes;
        }

        @Override
        public boolean admitted() {
            return admit(entry);
        }

        @Override
        public void upload() {
            BufferBuilder.RenderedBuffer payload = data;
            data = null;
            boolean consumed = false;
            try {
                if (part.buffer.isInvalid()) throw new IllegalStateException("Upload target retired");
                part.buffer.bind();
                consumed = true;
                // TODO(L2): 上游 v2/client/mesh/cache/StaticMeshCache.java:437-452 在 VAO/VBO 上做
                // glBindVertexArray + glBindBuffer + glGetError 的上传与绑定复原；1.7.10 没有 VAO/VBO，
                // VertexBuffer#upload 只是保存 CPU 顶点快照，因此这里不再触碰任何 GL 状态。
                part.buffer.upload(payload); // VertexBuffer 负责在 finally 中释放 RenderedBuffer。
                if (generation != StaticMeshCache.generation || CACHE.get(entry.key) != entry) {
                    part.upload.cancel(false);
                } else {
                    part.residentBytes = bytes;
                    residentBytes += bytes;
                    part.upload.complete(null);
                }
            } catch (RuntimeException failure) {
                part.upload.completeExceptionally(failure);
                if (!consumed) payload.release();
                throw failure;
            }
        }

        @Override
        public void cancel() {
            if (data != null) {
                data.release();
                data = null;
            }
            part.upload.cancel(false);
        }
    }

    public static boolean layoutSupported(Entry entry) {
        for (Part part : entry.parts) {
            if (!INTEGER_ATTRIBUTES.layoutSupported(part.attributes, part.buffer)) return false;
        }
        return true;
    }

    public static void retire(Entry entry, boolean invalidated) {
        UPLOADS.cancel(entry);
        if (entry.reserved) {
            reservedBytes -= entry.bytes;
            entry.reserved = false;
        }
        if (entry.parts == null) return;
        long retiringGeneration = generation;
        for (Part part : entry.parts) release(part, retiringGeneration, invalidated);
        entry.parts = null;
        entry.bytes = 0;
    }

    public static void release(Part part, long retiringGeneration, boolean invalidated) {
        if (part.upload.isDone()) {
            releaseUploaded(part, retiringGeneration, invalidated || part.upload.isCompletedExceptionally());
            return;
        }
        // 不让 CompletableFuture 吞掉清理异常；真正的清理在明确的渲染线程入口执行。
        part.upload.whenComplete(
            (ignored, failure) -> RenderSystem
                .recordRenderCall(() -> releaseUploaded(part, retiringGeneration, invalidated || failure != null)));
    }

    private static void releaseUploaded(Part part, long retiringGeneration, boolean invalidated) {
        residentBytes -= part.residentBytes;
        part.residentBytes = 0;
        if (part.context != MeshIntegerAttributes.currentContext()) return;
        try {
            if (invalidated || retiringGeneration != generation) part.buffer.close();
            else POOL.recycle(part.requestedFormat, part.buffer);
        } catch (RuntimeException failure) {
            if (part.owner != null) disableOwner(part.owner, "GPU release", failure);
            else SimpleBedrockModel.LOGGER.error("Static mesh GPU release failed", failure);
            throw failure;
        }
    }

    /*
     * 对应 1.20 的 {@code @Mod.EventBusSubscriber(... bus = Bus.FORGE)} + {@code @SubscribeEvent}
     * 静态监听器。
     * <p>
     * TODO(L2): 上游 v2/client/mesh/cache/StaticMeshCache.java:47 用 {@code @Mod.EventBusSubscriber}
     * 声明式注册；1.7.10 没有该注解（也没有 {@code Dist}），改为在静态块里显式注册本类实例：
     * <ul>
     * <li>{@code TickEvent.RenderTickEvent} 在 1.7.10 走 FML 总线
     * （{@code FMLCommonHandler.instance().bus()}）；</li>
     * <li>{@code WorldEvent.Unload}（取代 1.20 的 {@code LevelEvent.Unload}）走 Forge 总线
     * （{@code MinecraftForge.EVENT_BUS}）。</li>
     * </ul>
     * 事件优先级 {@code HIGHEST} 对 RenderTick 仍然生效。
     */
    static {
        StaticMeshCache listener = new StaticMeshCache();
        FMLCommonHandler.instance()
            .bus()
            .register(listener);
        MinecraftForge.EVENT_BUS.register(listener);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @ApiStatus.Internal
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        beginFrame();
    }

    /** 帧开始上传上一帧保存的数据；实例命令仍由本帧调用重新提交。 */
    public static void beginFrame() {
        RenderSystem.assertOnRenderThread();
        frame++;
        capturesInFrame = 0;
        captureNanosInFrame = capturedBytesInFrame = 0;
        UPLOADS.beginFrame(frame);
        WorldClient current = Minecraft.getMinecraft().theWorld;
        if (current != level) {
            clear();
            level = current;
        }
        if (current == null) return;
        supportsInstanceAttributes(0, 0);
        RESIDENCY.beginFrame(frame);
        UPLOADS.drain();
    }

    /**
     * TODO(L2): 上游 v2/client/mesh/cache/StaticMeshCache.java:563 收 1.20 的
     * {@code LevelEvent.Unload}（用 {@code event.getLevel().isClientSide()} 过滤）；
     * 1.7.10 的等价事件是 {@link WorldEvent.Unload}，字段 {@code world}，
     * 客户端判定用 {@code World#isRemote}。
     */
    @SubscribeEvent
    @ApiStatus.Internal
    public void onLevelUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) return;
        Runnable action = () -> {
            if (event.world == level) {
                clear();
                level = null;
            }
        };
        if (RenderSystem.isOnRenderThread()) {
            action.run();
        } else {
            RenderSystem.recordRenderCall(action::run);
        }
    }

    /** 失效指定接入方的缓存几何，并取消引用这些几何的待绘制命令。 */
    public static void invalidateOwner(ResourceLocation owner) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(owner, "owner");
        List<Entry> retiring = CACHE.values()
            .stream()
            .filter(
                entry -> entry.key.owner()
                    .equals(owner))
            .toList();
        for (Entry entry : retiring) {
            discard(entry);
        }
    }

    /** 资源重载或渲染上下文丢失时，失效全部物品网格。 */
    public static void clear() {
        RenderSystem.assertOnRenderThread();
        Object current = MeshIntegerAttributes.currentContext();
        if (geometryContext != null && geometryContext != current) POOL = new VertexBufferPool();
        generation++;
        FAILURES.reset();
        RESIDENCY.clear();
        UPLOADS.clear();
        for (Entry entry : List.copyOf(CACHE.values())) {
            retire(entry, true);
        }
        CACHE.clear();
        POOL.clear();
        INTEGER_ATTRIBUTES.close();
        probedFormat = null;
        formatCheckCountdown = FORMAT_CHECK_DRAWS;
    }
}
