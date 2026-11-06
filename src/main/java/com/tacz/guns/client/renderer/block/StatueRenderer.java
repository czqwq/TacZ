package com.tacz.guns.client.renderer.block;

import java.util.Optional;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.tacz.guns.block.BlockMetadata;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.item.DisplayItemRenderContext;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械雕像方块的基岩模型渲染器：雕像本体 + 悬浮旋转展示的枪械物品。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/renderer/block/StatueRenderer.java}
 *
 * <h3>注册信息（{@code ClientRegistry.registerTileEntityRenderers()} 用）</h3>
 * 
 * <pre>
 * ClientRegistry.registerTileEntity(StatueBlockEntity.class, "tacz_statue", new StatueRenderer());
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntityRenderer<StatueBlockEntity>} → {@link TileEntitySpecialRenderer}；
 * 渲染器自己 {@code GL11.glPushMatrix()} + {@code glTranslatef(x, y, z)}（1.7.10 的
 * {@code TileEntityRendererDispatcher} 不做 GL 平移）。</li>
 * <li>构造器去掉 {@code BlockEntityRendererProvider.Context}，改为无参（上下文可由
 * {@link TileEntityRendererDispatcher#instance} 取得）。</li>
 * <li>{@code blockEntity.getLevel()} → {@code blockEntity.getWorldObj()}；
 * {@code getBlockState().getValue(TargetBlock.FACING)} → {@link StatueBlockEntity#getFacing()}；
 * {@code facing.get2DDataValue()} → {@link BlockMetadata#facing2D(ForgeDirection)}。</li>
 * <li>{@code Minecraft#getItemRenderer().renderStatic(...)} 在 1.7.10 不存在 →
 * {@link StaticItemRenderHelper#renderStatic}（Forge {@code IItemRenderer} 优先，原版图标兜底）。</li>
 * <li>{@code Vec3.atCenterOf(pos.above()).closerThan(cameraPos, d)} → {@link Vec3#createVectorHelper}
 * + 手写距离比较；且 1.7.10 的方块实体渲染器<b>没有</b> {@code shouldRender(BlockEntity, Vec3)} 钩子，
 * 剔除由 {@code TileEntity#getRenderBoundingBox()} / {@code getMaxRenderDistanceSquared()} 负责
 * （{@link StatueBlockEntity} 已重写 {@code getRenderBoundingBox()}）。{@link #shouldRender} 保留为
 * 可测的纯函数，供日后接入 {@code RenderWorldLastEvent} 或按需剔除时复用。</li>
 * </ul>
 *
 * <p>
 * 依赖 block/entity 侧提供 1.7.10 TileEntity 形态（{@code StatueBlockEntity extends TileEntity}、
 * {@code getFacing()} 返回 {@link ForgeDirection}、{@code getGunItem()} 空格返回 {@code null}）。
 * 当前该包已是 1.7.10 形态。
 */
@SideOnly(Side.CLIENT)
public class StatueRenderer extends TileEntitySpecialRenderer {

    /**
     * 复用的顶点批次：{@code BufferSource#endBatch()} 之后内部 builder 与批次表都会清空，
     * 可跨调用/跨帧复用，避免每帧重新分配 {@code BufferBuilder} 的大数组。
     */
    private final MultiBufferSource.BufferSource bufferIn = MultiBufferSource.immediate();

    public StatueRenderer() {}

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.STATUE_MODEL_LOCATION);
    }

    /**
     * 1.7.10 入口：执行 1.20 {@code BlockEntityRenderDispatcher} 替渲染器做的那一步
     * （把 {@code x/y/z} 压进 {@code GL_MODELVIEW}），再以<b>单位位姿</b>转调上游同名形状的
     * {@link #render}。批次在 GL 仍平移到方块位置时 flush；{@link StaticItemRenderHelper}
     * 内部再叠加<b>相对</b>位姿，因此 {@code x/y/z} 只进 GL、不进 {@code PoseStack}。
     */
    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof StatueBlockEntity blockEntity)) {
            return;
        }
        World world = blockEntity.getWorldObj();
        if (world == null) {
            return;
        }
        int combinedLightIn = world
            .getLightBrightnessForSkyBlocks(blockEntity.xCoord, blockEntity.yCoord, blockEntity.zCoord, 0);
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) y, (float) z);
        try {
            render(blockEntity, partialTicks, new PoseStack(), bufferIn, combinedLightIn, OverlayTexture.NO_OVERLAY);
        } finally {
            RenderHelperCompat.endBatch(bufferIn);
            GL11.glPopMatrix();
        }
    }

    /** 上游 {@code render(StatueBlockEntity, float, PoseStack, MultiBufferSource, int, int)}。 */
    public void render(StatueBlockEntity blockEntity, float partialTick, PoseStack poseStack,
        MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn) {
        getModel().ifPresent(model -> {
            World level = blockEntity.getWorldObj();
            if (level == null) {
                return;
            }

            poseStack.pushPose();
            {
                ForgeDirection facing = blockEntity.getFacing();

                poseStack.translate(0.5, 1.5, 0.5);

                poseStack.mulPose(Axis.YN.rotationDegrees((BlockMetadata.facing2D(facing) + 2) % 4 * 90));
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));

                RenderType renderType = RenderConfig.BLOCK_ENTITY_TRANSLUCENT.get()
                    ? RenderType.entityTranslucent(getTextureLocation())
                    : RenderType.entityCutout(getTextureLocation());
                model.render(poseStack, ItemDisplayContext.NONE, renderType, combinedLightIn, combinedOverlayIn);
                // 上游 BedrockModel#render 内部就 endBatch；这里显式 flush 一次，保证「雕像先画、
                // 枪械后画」的绘制顺序，并让本类的正确性不依赖 util/RenderHelper 的接线状态。
                RenderHelperCompat.endBatch(bufferIn);

                poseStack.scale(0.5f, 0.5f, 0.5f);
                poseStack.translate(0, -0.875, -1.2);
                poseStack.mulPose(Axis.ZP.rotationDegrees(180));

                double offset = Math.sin(GunTime.nowMillis() / 500.0) * 0.1;
                poseStack.translate(0, offset, 0);

                ItemStack stack = blockEntity.getGunItem();

                // 上游这里是 Minecraft#getItemRenderer().renderStatic(stack, FIXED, 满亮度, NO_OVERLAY, ...)。
                DisplayItemRenderContext.render(
                    () -> StaticItemRenderHelper.renderStatic(
                        stack,
                        ItemDisplayContext.FIXED,
                        LightTexture.pack(15, 15),
                        OverlayTexture.NO_OVERLAY,
                        poseStack,
                        bufferIn,
                        level,
                        0));
            }
            poseStack.popPose();
        });
    }

    public static ResourceLocation getTextureLocation() {
        return InternalAssetLoader.STATUE_TEXTURE_LOCATION;
    }

    /** 上游 {@code getViewDistance()}：1.7.10 由 {@code TileEntity#getMaxRenderDistanceSquared()} 承担。 */
    public int getViewDistance() {
        return RenderConfig.TARGET_RENDER_DISTANCE.get();
    }

    /**
     * 上游 {@code shouldRender(StatueBlockEntity, Vec3)}：以「雕像头顶那一格的中心」为球心、
     * {@link #getViewDistance()} 为半径做球体裁剪。
     *
     * <p>
     * // TODO(L2): 上游 StatueRenderer.java:100-106 由 1.20 的 BlockEntityRenderDispatcher 每帧调用；
     * // 1.7.10 的 TESR 没有逐实例 shouldRender 钩子（只有 TileEntity#getRenderBoundingBox() 的
     * // AABB 视锥剔除 + getMaxRenderDistanceSquared() 的距离剔除），因此本方法保留为纯函数，
     * // 实际剔除交给 {@link StatueBlockEntity#getRenderBoundingBox()}。
     */
    public boolean shouldRender(StatueBlockEntity statueBlockEntity, Vec3 cameraPos) {
        Vec3 center = Vec3.createVectorHelper(
            statueBlockEntity.xCoord + 0.5D,
            statueBlockEntity.yCoord + 1.5D,
            statueBlockEntity.zCoord + 0.5D);
        double dx = center.xCoord - cameraPos.xCoord;
        double dy = center.yCoord - cameraPos.yCoord;
        double dz = center.zCoord - cameraPos.zCoord;
        double range = this.getViewDistance();
        return dx * dx + dy * dy + dz * dz < range * range;
    }
}
