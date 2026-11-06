package com.tacz.guns.client.renderer.block;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.renderer.item.DisplayItemRenderContext;
import com.tacz.guns.client.renderer.item.ItemPreviewRenderContext;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderSystem;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械挂板（折面时两面共 2 x 4x4 = 32 格）的渲染器：逐格画物品 + 瞄准空格时画放置预览。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/renderer/block/GunPegboardRenderer.java}
 *
 * <h3>注册信息（{@code ClientRegistry.registerTileEntityRenderers()} 用）</h3>
 * 
 * <pre>
 * ClientRegistry.registerTileEntity(GunPegboardBlockEntity.class, "tacz_gun_pegboard", new GunPegboardRenderer());
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntityRenderer<GunPegboardBlockEntity>} → {@link TileEntitySpecialRenderer}；
 * 渲染器自己 {@code GL11.glPushMatrix()} + {@code glTranslatef(x, y, z)}（1.7.10 的
 * {@link TileEntityRendererDispatcher#renderTileEntity} 不做 GL 平移，只设 lightmap
 * 并传相对相机坐标）。构造器去掉 {@code BlockEntityRendererProvider.Context}，改为无参。</li>
 * <li>{@code GunPegboardSlotHelper#getSlotFacing(BlockState, int)} →
 * {@link GunPegboardSlotHelper#getSlotFacing(int, int)}（1.7.10 用 metadata）；
 * {@code #getSlot(BlockState, BlockPos, Vec3)} →
 * {@link GunPegboardSlotHelper#getSlot(int, int, int, int, net.minecraft.util.Vec3)}。</li>
 * <li>{@code blockEntity.getBlockState().getBlock() instanceof GunPegboardBlock pegboard} →
 * {@code world.getBlock(x, y, z) instanceof GunPegboardBlock}（1.7.10 没有 BlockState，
 * 方块实例从世界按坐标取；{@code glowing} 本来就是 {@link GunPegboardBlock#isGlowing()} 的
 * 实例字段，不是 metadata 状态，见该方块类注释）。</li>
 * <li>{@code Minecraft.getInstance()/player/level} → {@link Minecraft#getMinecraft()} /
 * {@code thePlayer} / {@code theWorld}；{@code hitResult instanceof BlockHitResult} →
 * {@link MovingObjectPosition}；{@code getItemInHand(MAIN_HAND)} → {@code getHeldItem()}；
 * {@code isShiftKeyDown()} → {@code isSneaking()}。</li>
 * <li>{@code Minecraft#getItemRenderer().renderStatic(...)} → {@link StaticItemRenderHelper#renderStatic}。</li>
 * <li>{@code getViewDistance()} / {@code shouldRenderOffScreen()}：1.7.10 的 TESR 没有这两个钩子，
 * 剔除交给 {@code TileEntity#getRenderBoundingBox()} / {@code getMaxRenderDistanceSquared()}
 * （{@link GunPegboardBlockEntity} 已重写 {@code getRenderBoundingBox()}）。</li>
 * <li>空物品：上游 {@code ItemStack.EMPTY} → 1.7.10 用 {@code null}。</li>
 * </ul>
 *
 * <p>
 * 依赖 block/entity 侧提供 1.7.10 TileEntity 形态。当前该包已是 1.7.10 形态。
 */
@SideOnly(Side.CLIENT)
public class GunPegboardRenderer extends TileEntitySpecialRenderer {

    private static final double ITEM_FRAME_ITEM_OFFSET = 0.4375D - 3.0D / 16.0D;
    private static final float PREVIEW_ALPHA = 0.65F;

    /**
     * 复用的顶点批次：{@code BufferSource#endBatch()} 之后内部 builder 与批次表都会清空，
     * 可跨调用/跨帧复用，避免每帧重新分配 {@code BufferBuilder} 的大数组。
     */
    private final MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate();

    public GunPegboardRenderer() {}

    /**
     * 1.7.10 入口：执行 1.20 {@code BlockEntityRenderDispatcher} 替渲染器做的那一步
     * （把 {@code x/y/z} 压进 {@code GL_MODELVIEW}），再以<b>单位位姿</b>转调上游同名形状的
     * {@link #render}。
     *
     * <p>
     * 位姿只用 GL 承载（与 1.7.10 原版 TESR 一致）：批次路径（{@code MultiBufferSource}）在
     * {@code endBatch()} 时才把顶点交给 {@code Tessellator}，那时 GL 已经平移到方块位置；
     * 立即路径（{@link StaticItemRenderHelper}）内部再 {@code pushToGL()} 叠加<b>相对</b>位姿，
     * 因此这里不能再把 {@code x/y/z} 也放进 {@code PoseStack}（否则会重复平移一次）。
     */
    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof GunPegboardBlockEntity blockEntity)) {
            return;
        }
        World world = blockEntity.getWorldObj();
        if (world == null) {
            return;
        }
        int packedLight = world
            .getLightBrightnessForSkyBlocks(blockEntity.xCoord, blockEntity.yCoord, blockEntity.zCoord, 0);
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, (float) z);
        try {
            render(blockEntity, partialTicks, new PoseStack(), buffer, packedLight, OverlayTexture.NO_OVERLAY);
        } finally {
            RenderHelperCompat.endBatch(buffer);
            GL11.glPopMatrix();
        }
    }

    /** 上游 {@code render(GunPegboardBlockEntity, float, PoseStack, MultiBufferSource, int, int)}。 */
    public void render(GunPegboardBlockEntity blockEntity, float partialTick, PoseStack poseStack,
        MultiBufferSource buffer, int packedLight, int packedOverlay) {
        World world = blockEntity.getWorldObj();
        if (world == null) {
            return;
        }
        int metadata = world.getBlockMetadata(blockEntity.xCoord, blockEntity.yCoord, blockEntity.zCoord);
        for (int slot = 0; slot < GunPegboardBlockEntity.SLOT_COUNT; slot++) {
            ItemStack stack = blockEntity.getItem(slot);
            if (stack == null) {
                continue;
            }
            if (DisplayGunMeshGroup.tryRender(blockEntity, slot, packedOverlay)) {
                continue;
            }
            ForgeDirection facing = GunPegboardSlotHelper.getSlotFacing(metadata, slot);
            int row = (slot % GunPegboardBlockEntity.SLOTS_PER_FACE) / GunPegboardBlockEntity.GRID_SIZE;
            int col = slot % GunPegboardBlockEntity.GRID_SIZE;
            double x = (col + 0.5D) / GunPegboardBlockEntity.GRID_SIZE - 0.5D;
            double y = (row + 0.5D) / GunPegboardBlockEntity.GRID_SIZE - 0.5D;

            int itemLight = isGlowing(blockEntity) ? LightTexture.pack(15, 15) : packedLight;
            renderPegboardItem(
                stack,
                world,
                facing,
                x,
                y,
                blockEntity.getRotation(slot),
                poseStack,
                buffer,
                itemLight,
                packedOverlay,
                1.0F);
        }
        renderPlacementPreview(blockEntity, world, metadata, poseStack, buffer, packedLight, packedOverlay);
    }

    private static void renderPlacementPreview(GunPegboardBlockEntity blockEntity, World world, int metadata,
        PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || world == null
            || player.isSneaking()
            || minecraft.objectMouseOver == null
            || minecraft.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit.blockX != blockEntity.xCoord || hit.blockY != blockEntity.yCoord || hit.blockZ != blockEntity.zCoord) {
            return;
        }
        ItemStack held = player.getHeldItem();
        if (held == null || !GunPegboardItemConfigRead.canPlace(held)) {
            return;
        }
        int slot = GunPegboardSlotHelper
            .getSlot(metadata, blockEntity.xCoord, blockEntity.yCoord, blockEntity.zCoord, hit.hitVec);
        if (slot < 0 || blockEntity.getItem(slot) != null) {
            return;
        }
        int row = (slot % GunPegboardBlockEntity.SLOTS_PER_FACE) / GunPegboardBlockEntity.GRID_SIZE;
        int col = slot % GunPegboardBlockEntity.GRID_SIZE;
        double x = (col + 0.5D) / GunPegboardBlockEntity.GRID_SIZE - 0.5D;
        double y = (row + 0.5D) / GunPegboardBlockEntity.GRID_SIZE - 0.5D;
        ForgeDirection facing = GunPegboardSlotHelper.getSlotFacing(metadata, slot);
        int itemLight = isGlowing(blockEntity) ? LightTexture.pack(15, 15) : packedLight;
        ItemStack preview = held.copy();
        preview.stackSize = 1;
        renderPegboardItem(preview, world, facing, x, y, 0, poseStack, buffer, itemLight, packedOverlay, PREVIEW_ALPHA);
    }

    private static void renderPegboardItem(ItemStack stack, World world, ForgeDirection facing, double x, double y,
        int rotation, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay, float alpha) {
        poseStack.pushPose();
        boolean preview = alpha < 1.0F;
        try {
            DisplayItemTransforms.apply(poseStack, facing, x, y, ITEM_FRAME_ITEM_OFFSET, rotation);
            if (preview) {
                RenderSystem.enableBlend();
                // 上游 RenderSystem#defaultBlendFunc() 在 1.7.10 没有可读常量，等价地显式设置一次。
                RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                ItemPreviewRenderContext.setAlpha(alpha);
            }
            DisplayItemRenderContext.render(
                () -> StaticItemRenderHelper.renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    preview ? OverlayTexture.NO_OVERLAY : packedOverlay,
                    poseStack,
                    buffer,
                    world,
                    0));
        } finally {
            // 必须是 finally：ItemPreviewRenderContext.alpha 是<b>静态</b>字段，
            // 一旦在异常路径上漏复位，整个进程内后续所有物品渲染都会走 entityTranslucent
            // （消费方见 AttachmentRender / AmmoItemRenderer / AttachmentItemRenderer /
            // GunItemRendererWrapper），表现为「所有枪永久半透明」。
            if (preview) {
                ItemPreviewRenderContext.setAlpha(1.0F);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.disableBlend();
            }
            poseStack.popPose();
        }
    }

    private static boolean isGlowing(GunPegboardBlockEntity blockEntity) {
        World world = blockEntity.getWorldObj();
        if (world == null) {
            return false;
        }
        return world
            .getBlock(blockEntity.xCoord, blockEntity.yCoord, blockEntity.zCoord) instanceof GunPegboardBlock pegboard
            && pegboard.isGlowing();
    }

    /**
     * 上游 {@code getViewDistance()}。
     *
     * <p>
     * // TODO(L2): 上游 GunPegboardRenderer.java:142-157 用
     * // {@code getViewDistance()} / {@code shouldRenderOffScreen()} / {@code shouldRender(BlockEntity, Vec3)}
     * // 三个 BlockEntityRenderer 钩子做视距与离屏剔除；1.7.10 的 {@code TileEntitySpecialRenderer}
     * // 没有这三个钩子，剔除改由 {@code TileEntity#getRenderBoundingBox()} /
     * // {@code TileEntity#getMaxRenderDistanceSquared()} 承担（{@link GunPegboardBlockEntity}
     * // 已重写 {@code getRenderBoundingBox()}），此处保留同名方法仅为对齐上游语义。
     */
    public int getViewDistance() {
        return RenderConfig.TARGET_RENDER_DISTANCE.get();
    }
}
