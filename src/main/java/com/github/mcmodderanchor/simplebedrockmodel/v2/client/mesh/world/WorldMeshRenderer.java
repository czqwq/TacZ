// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/world/WorldMeshRenderer.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;

import com.github.mcmodderanchor.simplebedrockmodel.port.AABB;
import com.github.mcmodderanchor.simplebedrockmodel.port.Camera;
import com.github.mcmodderanchor.simplebedrockmodel.port.Vec3;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.config.WorldMeshConfig;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshCachePolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.MeshVertexFormat;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.WorldMeshBuffers;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.gpu.WorldMeshPart;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.render.MeshBatchRenderer;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexFormat;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 世界空间网格绘制的库侧入口（原型）。
 *
 * <p>
 * 职责：渲染组登记、世界阶段调度、剔除与统计；绘制交给共用 MeshBatchRenderer。{@link WorldMeshGroup} 管理对象、缓存、策略和失效，
 * 调用方只提供几何描述与对象状态。底层句柄和上传入口不作为公共 API。
 * </p>
 *
 * <h2>1.7.10 移植说明（事件与相机）</h2>
 * <p>
 * 上游把绘制固定在 1.20 的 {@code RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES}，
 * 并从事件里拿到 {@code Camera}、{@code Frustum}、{@code PoseStack} 与投影矩阵。
 * 1.7.10 <b>没有</b> {@code RenderLevelStageEvent}，等价阶段是
 * {@link RenderWorldLastEvent}（在 {@code EntityRenderer} 画完世界之后触发，
 * GL_MODELVIEW 已处于相机空间），因此：
 * <ul>
 * <li>{@code event.getCamera()} → {@link Camera#getMainCamera(float)}（用一个 partialTicks，
 * 从 {@code renderViewEntity} 插值出相机位置／角度，见 {@code port.Camera} 注释）；</li>
 * <li>{@code event.getFrustum()} → 就地 {@code new Frustrum()} +
 * {@code setPosition(cameraPos)}（1.7.10 的视锥是「按需构造、从当前 GL 矩阵算裁剪平面」的
 * {@code net.minecraft.client.renderer.culling.Frustrum}，注意类名少一个 u），
 * 可见性判定用 {@code isBoundingBoxInFrustum(AxisAlignedBB)}；</li>
 * <li>{@code event.getPoseStack().last().pose()} → {@code RenderSystem.getModelViewMatrix()}
 * （mcshim 版直接读 GL_MODELVIEW 栈，返回 {@code FloatBuffer}，用
 * {@code new Matrix4f(FloatBuffer)} 按列主序换算）；</li>
 * <li>{@code event.getProjectionMatrix()} → {@code RenderSystem.getProjectionMatrix()}（同样换算）。</li>
 * </ul>
 *
 * <h2>其它降级点</h2>
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → 静态块显式注册（1.7.10 无该注解／无 {@code Dist}）：
 * {@code TickEvent.ClientTickEvent} 与 {@code WorldEvent.Unload}（取代 1.20 的
 * {@code LevelEvent.Unload}）分别注册到 FML 总线与 Forge 总线；{@code RenderWorldLastEvent}
 * 注册到 Forge 总线。</li>
 * <li>{@code Minecraft#level} → {@code Minecraft#theWorld}。</li>
 * <li>{@code RenderType#getChunkLayerId()}（1.20 的「区块渲染层序号」）在 mcshim 里不存在，
 * 见 {@link #materialOrder(RenderType)}。</li>
 * </ul>
 */
public class WorldMeshRenderer {

    private static final Map<String, WorldMeshGroup<?>> MESH_GROUPS = new LinkedHashMap<>();

    public enum Invalidation {
        WORLD,
        RESOURCES,
        SHUTDOWN
    }

    private static final WorldMeshMetrics METRICS = new WorldMeshMetrics();
    public static final WorldMeshBuffers BUFFERS = new WorldMeshBuffers(METRICS);

    private static final List<CollectedShard> COLLECTED = new ArrayList<>();
    private static final LinkedHashMap<RenderType, List<CollectedShard>> GROUPS = new LinkedHashMap<>();
    private static final List<RenderType> GROUP_ORDER = new ArrayList<>();
    private static final Matrix4f SCRATCH_VIEW = new Matrix4f();
    private static int collectedCount;

    private static boolean enabled = true;
    private static long stateUpdateTick;
    private static long worldDrawSerial;
    private static WorldClient level;

    /** 顶点格式漂移的探测间隔；光影开关会让 Iris/AR 换掉 NEW_ENTITY 对应的格式。 */
    private static final int FORMAT_CHECK_FRAMES = 60;
    private static int formatCheckCountdown = FORMAT_CHECK_FRAMES;
    private static VertexFormat probedFormat;

    /*
     * 对应 1.20 的 {@code @Mod.EventBusSubscriber(... bus = Bus.FORGE)}。
     * <p>
     * TODO(L2): 上游 v2/client/mesh/world/WorldMeshRenderer.java:54 用注解声明式注册；
     * 1.7.10 没有该注解（也没有 {@code Dist}），改为显式注册：
     * <ul>
     * <li>{@code TickEvent.ClientTickEvent} → {@code FMLCommonHandler.instance().bus()}；</li>
     * <li>{@code WorldEvent.Unload} / {@code RenderWorldLastEvent} → {@code MinecraftForge.EVENT_BUS}。</li>
     * </ul>
     */
    static {
        WorldMeshRenderer listener = new WorldMeshRenderer();
        FMLCommonHandler.instance()
            .bus()
            .register(listener);
        MinecraftForge.EVENT_BUS.register(listener);
    }

    /**
     * 显式触发本类的事件注册（1.7.10 无 {@code @Mod.EventBusSubscriber}，需类被初始化一次）。
     * <p>
     * 静态块在「本类首次被使用」时已经完成注册，因此本方法<b>不是必需</b>的；保留它是为了让
     * TACZ 的 {@code ClientProxy#init} 可以显式调用一次（若启用世界网格批次渲染），
     * 从而保证即使在没有任何网格组注册的情况下渲染/卸载事件也已挂上。
     */
    public static void register() {
        // 空方法：类初始化（静态块）已完成注册
    }

    /** 绘制枚举结果：句柄 + 本实例的原点与包围盒。去重后同一个句柄会被多个实例重复枚举。 */
    public static class CollectedShard {

        WorldMeshPart handle;
        Vec3 origin;
        Matrix4f localTransform;
        AABB bounds;
        /** 归一后的实例光照：INSTANCE 使用实例值，FIXED 归零。 */
        int light;
    }

    public WorldMeshRenderer() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static long nextWorldDrawSerial() {
        return worldDrawSerial + 1;
    }

    public static long currentWorldDrawSerial() {
        return worldDrawSerial;
    }

    /** 运行时开关；关闭时丢弃几何缓存，保留渲染组对象，重新打开后会重新捕获。 */
    public static void setEnabled(boolean value) {
        RenderSystem.assertOnRenderThread();
        if (value == enabled) {
            return;
        }
        enabled = value;
        if (!value) {
            invalidate(Invalidation.SHUTDOWN);
        }
    }

    public static <K> WorldMeshGroup<K> createGroup(String id, WorldMeshStrategy defaultStrategy) {
        return createGroup(id, defaultStrategy, EnumSet.allOf(WorldMeshStrategy.class));
    }

    /** 在渲染线程创建渲染组；同 ID 不允许重复创建，关闭后可重新创建。 */
    public static <K> WorldMeshGroup<K> createGroup(String id, WorldMeshStrategy defaultStrategy,
        Set<WorldMeshStrategy> supported) {
        return createGroup(id, defaultStrategy, supported, MeshCachePolicy.DEFAULT);
    }

    /** LRU 限额只约束空闲几何；active／pending 网格仍持有引用，不参与淘汰。 */
    public static <K> WorldMeshGroup<K> createGroup(String id, WorldMeshStrategy defaultStrategy,
        Set<WorldMeshStrategy> supported, MeshCachePolicy cachePolicy) {
        RenderSystem.assertOnRenderThread();
        if (MESH_GROUPS.containsKey(id)) throw new IllegalArgumentException("Duplicate group: " + id);
        WorldMeshGroup<K> group = new WorldMeshGroup<>(id, defaultStrategy, supported, cachePolicy);
        MESH_GROUPS.put(id, group);
        group.applyPolicy();
        return group;
    }

    public static void unregister(WorldMeshGroup<?> group) {
        if (!MESH_GROUPS.remove(group.id(), group)) return;
        BUFFERS.removeOwner(group.id());
        clearFrameData();
        if (MESH_GROUPS.isEmpty()) {
            BUFFERS.clear();
        }
    }

    /** 修改客户端配置中的策略选择，并立即更新已注册渲染组。 */
    public static void setStrategy(StrategyOverride value) {
        RenderSystem.assertOnRenderThread();
        WorldMeshConfig.STRATEGY.set(Objects.requireNonNull(value, "value"));
        WorldMeshConfig.STRATEGY.save();
        for (WorldMeshGroup<?> group : MESH_GROUPS.values()) group.applyPolicy();
    }

    public static List<WorldMeshGroupStats> groupStats() {
        RenderSystem.assertOnRenderThread();
        return MESH_GROUPS.values()
            .stream()
            .map(WorldMeshGroup::stats)
            .toList();
    }

    /** 保留逻辑对象，重新解析几何描述并重建缓存。 */
    public static void clearCaches() {
        invalidate(Invalidation.RESOURCES);
    }

    /**
     * 顶点格式漂移自愈：每隔 {@link #FORMAT_CHECK_FRAMES} 帧用一次 1 个 quad 的探针确认"现在
     * {@code NEW_ENTITY} 会被哪个格式接手"，一变就整体失效重烘。
     *
     * <p>
     * 为什么需要它：Iris 在有光影包时无条件打开扩展顶点格式，AR 还会把 Iris 的 ENTITY 换成自己的
     * 扩展版本。已上传的 VBO 不会自动跟随格式变化；这里只检测格式对象变化，
     * 不能覆盖所有 shader 语义变化或兼容性问题。资源内容重载由独立监听处理。
     * </p>
     *
     * <p>
     * 1.7.10：顶点布局由 {@code Tessellator} 固定，不会漂移，这段自愈只是保留上游结构
     * （1.7.10 侧 {@code MeshVertexFormat.probe()} 恒返回同一格式，永不触发失效）。
     */
    public static void checkFormatDrift() {
        if (--formatCheckCountdown > 0) {
            return;
        }
        formatCheckCountdown = FORMAT_CHECK_FRAMES;
        VertexFormat probed = MeshVertexFormat.probe();
        if (probed == null) {
            return;
        }
        if (probedFormat == null) {
            probedFormat = probed;
            return;
        }
        if (probedFormat != probed) {
            probedFormat = probed;
            METRICS.recordFormatChange();
            invalidate(Invalidation.RESOURCES);
        }
    }

    /** 失效全部渲染组缓存；世界切换、资源重载与运行开关关闭都会走这里。 */
    public static void invalidate(Invalidation kind) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(kind, "kind");
        BUFFERS.clear();
        for (WorldMeshGroup<?> group : MESH_GROUPS.values()) {
            group.invalidate(kind);
        }
        clearFrameData();
    }

    public static void clearFrameData() {
        COLLECTED.clear();
        GROUPS.clear();
        GROUP_ORDER.clear();
        collectedCount = 0;
        METRICS.clearFrame();
    }

    /** 按需获取统计快照；与渲染状态一样，应在渲染线程读取。 */
    public static WorldMeshStats stats() {
        RenderSystem.assertOnRenderThread();
        return METRICS.snapshot(enabled, MESH_GROUPS.size(), BUFFERS.size(), BUFFERS.pool);
    }

    public static long stateUpdateTick() {
        return stateUpdateTick;
    }

    @SubscribeEvent
    @ApiStatus.Internal
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!MESH_GROUPS.isEmpty() && event.phase == TickEvent.Phase.END) stateUpdateTick++;
    }

    /**
     * TODO(L2): 上游 v2/client/mesh/world/WorldMeshRenderer.java:239 收 1.20 的
     * {@code LevelEvent.Unload}；1.7.10 的等价事件是 {@link WorldEvent.Unload}（字段 {@code world}），
     * 客户端判定用 {@code World#isRemote}。
     */
    @SubscribeEvent
    @ApiStatus.Internal
    public void onLevelUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) return;
        Runnable unload = () -> {
            if (event.world == level) {
                level = null;
                invalidate(Invalidation.WORLD);
            }
        };
        if (RenderSystem.isOnRenderThread()) {
            unload.run();
        } else {
            RenderSystem.recordRenderCall(unload::run);
        }
    }

    /**
     * 在渲染组登记实例前同步世界身份；可能触发 WORLD 失效，故须先调用再写入实例数据。
     * 返回是否存在客户端世界，不代表渲染器开启或上传就绪。
     */
    public static boolean ensureLevel() {
        RenderSystem.assertOnRenderThread();
        WorldClient current = Minecraft.getMinecraft().theWorld;
        if (current == level) {
            return current != null;
        }
        level = current;
        invalidate(Invalidation.WORLD);
        return current != null;
    }

    /**
     * 对应 1.20 的 {@code @SubscribeEvent onRenderStage(RenderLevelStageEvent)}。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/world/WorldMeshRenderer.java:270 收
     * {@code RenderLevelStageEvent}（固定 {@code AFTER_BLOCK_ENTITIES} 阶段）；1.7.10 没有该事件，
     * 改为 {@link RenderWorldLastEvent}（世界绘制完成、GL_MODELVIEW 仍在相机空间）。
     * 相机／视锥／矩阵均从 1.7.10 的全局渲染状态里取，见类注释。
     */
    @SubscribeEvent
    @ApiStatus.Internal
    public void onRenderStage(RenderWorldLastEvent event) {
        if (MESH_GROUPS.isEmpty()) {
            return;
        }
        worldDrawSerial++;
        if (!ensureLevel() || MESH_GROUPS.isEmpty()) return;
        if (!enabled) {
            for (WorldMeshGroup<?> group : MESH_GROUPS.values()) group.refreshObjects();
            return;
        }
        long now = System.nanoTime();
        METRICS.beginFrame(now);
        checkFormatDrift();
        long passStart = now;

        for (WorldMeshGroup<?> group : MESH_GROUPS.values()) {
            group.prepareFrame();
        }

        // TODO(L2): 上游是 event.getCamera()（1.20 的 Camera 实例）；1.7.10 用 port.Camera 的
        // 「按 partialTicks 从 renderViewEntity 插值」替身（见 port.Camera 注释）。
        Camera camera = Camera.getMainCamera(event.partialTicks);
        Vec3 cameraPosition = camera.getPosition();
        // TODO(L2): 上游是 event.getFrustum()；1.7.10 没有事件自带的视锥，
        // 就地构造 net.minecraft.client.renderer.culling.Frustrum（注意类名的拼写）
        // 并用相机位置作为剔除原点。
        Frustrum frustum = new Frustrum();
        frustum.setPosition(cameraPosition.x, cameraPosition.y, cameraPosition.z);
        // TODO(L2): 上游 event.getPoseStack().last().pose() 直接是 Matrix4f；1.7.10 的 mcshim
        // RenderSystem#getModelViewMatrix() 读 GL_MODELVIEW 栈并返回 FloatBuffer，
        // 用 Matrix4f(FloatBuffer) 按列主序换算。
        Matrix4f baseView = new Matrix4f(RenderSystem.getModelViewMatrix());
        // TODO(L2): 上游 event.getProjectionMatrix()；同上换算。
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

        collectedCount = 0;
        for (WorldMeshGroup<?> group : MESH_GROUPS.values()) {
            group.collect(WorldMeshRenderer::collect);
        }

        for (List<CollectedShard> group : GROUPS.values()) {
            group.clear();
        }
        GROUP_ORDER.clear();

        int pending = 0;
        int failed = 0;
        int culled = 0;
        int shards = 0;
        for (int i = 0; i < collectedCount; i++) {
            CollectedShard shard = COLLECTED.get(i);
            WorldMeshPart handle = shard.handle;
            if (!handle.isAlive()) {
                continue;
            }
            if (handle.uploadFailed()) {
                failed++;
                continue;
            }
            if (!handle.isUploaded()) {
                pending++;
                continue;
            }
            // TODO(L2): 上游 v2/client/mesh/world/WorldMeshRenderer.java:327 调
            // Frustum#isVisible(AABB)；1.7.10 的 Frustrum 只有
            // isBoundingBoxInFrustum(AxisAlignedBB)，用 port.AABB#toAxisAlignedBB() 转换。
            if (shard.bounds != null && !frustum.isBoundingBoxInFrustum(shard.bounds.toAxisAlignedBB())) {
                culled++;
                continue;
            }
            RenderType material = handle.material();
            List<CollectedShard> group = GROUPS.computeIfAbsent(material, ignored -> new ArrayList<>());
            if (group.isEmpty()) {
                GROUP_ORDER.add(material);
            }
            group.add(shard);
            shards++;
        }

        METRICS.recordPrepared(shards, culled, pending, failed);
        if (GROUP_ORDER.isEmpty()) {
            METRICS.endFrame(0, 0, passStart);
            return;
        }

        GROUP_ORDER.sort(Comparator.comparingInt(WorldMeshRenderer::materialOrder));

        MeshBatchRenderer batch = new MeshBatchRenderer(projection);
        try (batch) {
            for (RenderType type : GROUP_ORDER) {
                List<CollectedShard> shardsOfType = GROUPS.get(type);
                shardsOfType.sort(
                    Comparator.comparingInt((CollectedShard shard) -> shard.handle.id())
                        .thenComparingInt(shard -> shard.light)
                        .thenComparingDouble(
                            shard -> shard.bounds == null ? 0.0 : shard.bounds.distanceToSqr(cameraPosition)));
                if (!batch.beginMaterial(type, baseView)) continue;
                WorldMeshPart previous = null;
                int previousLight = 0;
                for (CollectedShard shard : shardsOfType) {
                    WorldMeshPart handle = shard.handle;
                    batch.bind(handle.buffer());
                    if (previous != handle || previousLight != shard.light) {
                        if (!BUFFERS.prepareDraw(handle, shard.light)) continue;
                        previous = handle;
                        previousLight = shard.light;
                    }
                    Vec3 origin = shard.origin;
                    SCRATCH_VIEW.set(baseView)
                        .translate(
                            (float) (origin.x - cameraPosition.x),
                            (float) (origin.y - cameraPosition.y),
                            (float) (origin.z - cameraPosition.z))
                        .mul(shard.localTransform);
                    batch.drawWorld(SCRATCH_VIEW, shard.localTransform);
                }
            }
        }
        METRICS.endFrame(batch.draws(), batch.setups(), passStart);
    }

    public static void collect(WorldMeshPart handle, Vec3 origin, Matrix4f localTransform, AABB worldBounds,
        int packedLight) {
        CollectedShard shard;
        if (collectedCount < COLLECTED.size()) {
            shard = COLLECTED.get(collectedCount);
        } else {
            shard = new CollectedShard();
            COLLECTED.add(shard);
        }
        shard.handle = handle;
        shard.origin = origin;
        shard.localTransform = localTransform;
        shard.bounds = worldBounds;
        // FIXED / MUTABLE 使用网格自身的光照，忽略实例值；
        // UNIFORM 从整数参数表读取实例光照，按光照排序可减少同一 VAO 的参数偏移更新。
        shard.light = handle.lightMode() == WorldMeshPart.LightMode.FIXED
            || handle.lightMode() == WorldMeshPart.LightMode.MUTABLE ? 0 : packedLight;
        collectedCount++;
    }

    /**
     * 绘制顺序 = 材质在 1.20 里的区块渲染层次序。
     *
     * <p>
     * TODO(L2): 上游 v2/client/mesh/world/WorldMeshRenderer.java:403 用
     * {@code RenderType#getChunkLayerId()}（1.20 的 {@code ChunkRenderTypeSet} 序号）；
     * mcshim 的 {@code RenderType} 没有该方法，改为按渲染类型名字给一个稳定次序
     * （半透明类靠后，与 1.20 的「实体 → 半透明」顺序一致），未知类型回落到 100。
     */
    public static int materialOrder(RenderType type) {
        if (type == null) return 100;
        String name = type.name();
        if (name == null) return 100;
        if (name.startsWith("entity_cutout") || name.startsWith("entity_solid")) return 0;
        if (name.startsWith("sbm_emissive") || name.startsWith("bedrock_poly_mesh")) return 1;
        if (name.startsWith("entity_translucent")) return 2;
        if (name.startsWith("item_entity_translucent")) return 3;
        return 100;
    }
}
