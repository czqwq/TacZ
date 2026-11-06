package com.tacz.guns.client.renderer.block;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.joml.Matrix4f;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.DisplayGunSlot;
import com.tacz.guns.block.entity.GunDisplayBlockEntity;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.renderer.external.ExternalGunAppearance;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;

/**
 * 每个悬挂物独立登记；稳定身份由所属方块实体创建和持有。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/renderer/block/DisplayGunMeshGroup.java}
 *
 * <h3>1.7.10 差异（VBO 网格组整体降级为立即绘制）</h3>
 * <ul>
 * <li>上游用 simplebedrockmodel v2 的
 * {@code WorldMeshRenderer.createGroup(...)} /
 * {@code WorldMeshGroup#track/claimForWorldDraw/markDirty} /
 * {@code MeshRenderableAdapter} / {@code MeshCachePolicy}（{@code TTL_300}）
 * 把「枪械几何」缓存成 GPU VBO 网格、在世界绘制阶段按实例矩阵批量提交。
 * 1.7.10 <b>没有</b>这套 GPU 几何缓存/提交机制，且本工程按
 * {@code docs/PORT_STATUS.md} 的裁决（simplebedrockmodel 处置）把
 * {@code v2.client.mesh.**} 降级为「立即绘制」。
 * // TODO(L2): 上游 DisplayGunMeshGroup.java:40-43,50-115,148-165 做的是
 * // {@code WorldMeshRenderer.createGroup("tacz:display_guns", WorldMeshStrategy.INSTANCE, ..., TTL_300)}
 * // + {@code MeshRenderableAdapter}（isValid/isVisible/origin/localTransform/packedLight/geometryKey/
 * // collectGeometry）+ {@code group.track/claimForWorldDraw}；
 * // 1.7.10 无 VBO 网格组、无 {@code MeshCachePolicy}、无「世界绘制阶段批量提交」，
 * // 此处改用「在方块实体渲染器里直接立即画一遍」近似：
 * // {@link #tryRender} 用 {@link DisplayGunGeometry#capture} 把同一份外观几何写进
 * // {@link MultiBufferSource} 并当场 flush，几何/外观/LOD/光照选择逻辑全部保留，
 * // 只丢掉跨帧的 VBO 复用（每帧重算顶点，帧开销比上游高，语义等价）。</li>
 * <li>上游 {@code ADAPTER.origin(DisplayGunSlot)} 返回方块角点、{@code localTransform} 返回相对于该角点的
 * 实例矩阵，由世界网格渲染器组合。1.7.10 的方块实体渲染器在<b>进入</b> {@code renderTileEntityAt}
 * 时 GL_MODELVIEW 已经处在方块角点（渲染器自己 {@code translate(x, y, z)}），
 * 因此只需应用 {@link #localTransform(DisplayGunSlot)} 这一个相对矩阵。</li>
 * <li>{@code BlockEntity#isRemoved()} → {@link TileEntity#isInvalid()}；
 * {@code getLevel()} → {@code getWorldObj()}；{@code Minecraft.getInstance().level} →
 * {@code Minecraft.getMinecraft().theWorld}；{@code getBlockPos().distToCenterSqr(Vec3)} →
 * 手写「方块中心到相机的距离平方」，相机坐标取
 * {@link TileEntityRendererDispatcher#field_147560_j} / {@code field_147561_k} / {@code field_147558_l}
 * （1.7.10 {@code TileEntityRendererDispatcher} 缓存的插值相机位置，等价于 1.20
 * {@code Camera#getPosition()}）。</li>
 * <li>{@code LevelRenderer#getLightColor(Level, BlockPos)} → 1.7.10 的
 * {@code World#getLightBrightnessForSkyBlocks(x, y, z, 0)}（同一份打包 lightmap 值）。</li>
 * </ul>
 *
 * <p>
 * 依赖 {@code client/renderer/external/ExternalGunAppearance}（仍为 1.20 形态，未移植）
 * 与 block/entity 侧的 1.7.10 TileEntity 形态（{@code DisplayGunSlot} /
 * {@code GunDisplayBlockEntity} 已是 1.7.10 形态）。
 */
public class DisplayGunMeshGroup {

    private static final double ITEM_DEPTH = 0.25D;
    private static boolean configWasEnabled;
    private static final Map<DisplayGunSlot, CachedAppearance> APPEARANCES = new WeakHashMap<>();
    /**
     * 复用的顶点批次（1.7.10 特化）：{@code MultiBufferSource.BufferSource#endBatch()} 之后内部
     * builder 与批次表都会清空，因此可以跨调用/跨帧复用，避免每槽每帧重新分配 {@code BufferBuilder}
     * 的大数组（4096 顶点 ≈ 200KB）。方块实体渲染是单线程的，静态复用安全。
     */
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate();

    private DisplayGunMeshGroup() {}

    private record CachedAppearance(long appearanceRevision, long resourceRevision,
        ExternalGunAppearance.Resolution resolution) {}

    /**
     * 方块实体渲染器对当前槽位调用；返回 true 表示由本类接管绘制。
     *
     * <p>
     * 上游语义是「返回 true 表示由 SBM 接管，包括等待模型或 VBO 就绪」；1.7.10 立即绘制版本里
     * 模型未就绪（{@code resolution.pending()}）时同样返回 true —— 与上游一致地让调用方
     * 不要回退到开销较大的普通渲染路径，避免同一格在等待期被两套路径各画一次。
     */
    public static boolean tryRender(GunDisplayBlockEntity owner, int index, int overlay) {
        if (!RenderConfig.DISPLAY_GUN_VBO.get()) {
            if (configWasEnabled) {
                APPEARANCES.clear();
            }
            configWasEnabled = false;
            return false;
        }
        configWasEnabled = true;
        TileEntity blockEntity = owner.getBlockEntity();
        World world = blockEntity.getWorldObj();
        if (overlay != OverlayTexture.NO_OVERLAY || world == null || world != Minecraft.getMinecraft().theWorld) {
            return false;
        }

        ItemStack stack = owner.getItem(index);
        if (stack == null || IGun.getIGunOrNull(stack) == null) {
            return false;
        }

        DisplayGunSlot slot = owner.getDisplaySlot(index);
        ExternalGunAppearance.Snapshot current = snapshot(slot);
        CachedAppearance cached = APPEARANCES.get(slot);
        if (current == null) {
            return cached != null && cached.resolution()
                .pending();
        }

        // 上游此处是 group.markDirty(slot)（让 VBO 网格失效）；立即绘制没有网格缓存，
        // 只需消费掉变化标记，避免 revision 一直停在「未通知」状态。
        slot.consumeChange();

        int packedLight = packedLight(owner, blockEntity, world);
        PoseStack pose = new PoseStack();
        pose.mulPoseMatrix(localTransform(slot));
        pose.pushToGL();
        try {
            boolean rendered = DisplayGunGeometry.capture(current, ItemDisplayContext.FIXED, BUFFERS, packedLight);
            RenderHelperCompat.endBatch(BUFFERS);
            return rendered;
        } finally {
            pose.popFromGL();
        }
    }

    public static ExternalGunAppearance.Snapshot snapshot(DisplayGunSlot slot) {
        GunDisplayBlockEntity owner = slot.owner();
        TileEntity blockEntity = owner.getBlockEntity();
        if (blockEntity.isInvalid()) {
            return null;
        }
        ItemStack stack = owner.getItem(slot.index());
        if (stack == null) {
            return null;
        }

        long appearanceRevision = slot.appearanceRevision();
        long resourceRevision = ClientIndexManager.resourceRevision();
        CachedAppearance cached = APPEARANCES.get(slot);
        if (cached == null || cached.appearanceRevision() != appearanceRevision
            || cached.resourceRevision() != resourceRevision
            || cached.resolution()
                .pending()) {
            cached = new CachedAppearance(appearanceRevision, resourceRevision, ExternalGunAppearance.resolve(stack));
            APPEARANCES.put(slot, cached);
        }
        ExternalGunAppearance.Variants variants = cached.resolution()
            .variants();
        if (variants == null) {
            return null;
        }
        if (variants.near() == variants.far()) {
            return variants.near();
        }

        int range = RenderConfig.GUN_LOD_RENDER_DISTANCE.get();
        boolean useLod = range == 0 || distanceSqToCamera(blockEntity) >= (double) range * range;
        return variants.select(useLod);
    }

    /**
     * 上游 {@code ADAPTER.localTransform(DisplayGunSlot)}：槽位在方块内的实例矩阵（相对方块角点）。
     */
    public static Matrix4f localTransform(DisplayGunSlot slot) {
        GunDisplayBlockEntity owner = slot.owner();
        int index = slot.index();
        int gridSize = owner.getGridSize();
        int localIndex = index % (gridSize * gridSize);
        double x = ((localIndex % gridSize) + 0.5D) / gridSize - 0.5D;
        double y = ((localIndex / gridSize) + 0.5D) / gridSize - 0.5D;
        PoseStack pose = new PoseStack();
        DisplayItemTransforms.apply(pose, owner.getSlotFacing(index), x, y, ITEM_DEPTH, owner.getRotation(index));
        return new Matrix4f(
            pose.last()
                .pose());
    }

    /**
     * 上游 {@code ADAPTER.packedLight(DisplayGunSlot)}：展示发光时满亮度，否则取方块位置的 lightmap
     * （1.20 的 {@code LevelRenderer#getLightColor} → 1.7.10 的
     * {@code World#getLightBrightnessForSkyBlocks}，同一份打包值）。
     */
    private static int packedLight(GunDisplayBlockEntity owner, TileEntity blockEntity, World world) {
        if (owner.isDisplayGlowing()) {
            return LightTexture.pack(15, 15);
        }
        return world.getLightBrightnessForSkyBlocks(blockEntity.xCoord, blockEntity.yCoord, blockEntity.zCoord, 0);
    }

    /**
     * 方块中心到相机的距离平方；相机坐标取 1.7.10
     * {@code TileEntityRendererDispatcher} 缓存的插值相机位置。
     */
    private static double distanceSqToCamera(TileEntity blockEntity) {
        TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
        double dx = blockEntity.xCoord + 0.5D - dispatcher.field_147560_j;
        double dy = blockEntity.yCoord + 0.5D - dispatcher.field_147561_k;
        double dz = blockEntity.zCoord + 0.5D - dispatcher.field_147558_l;
        return dx * dx + dy * dy + dz * dz;
    }
}
