package com.tacz.guns.client.renderer.block;

import java.util.Optional;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械工作台（L1/L2/L3 三个等级共用）的基岩模型渲染器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/renderer/block/GunSmithTableRenderer.java}
 *
 * <h3>注册信息（{@code ClientRegistry.registerTileEntityRenderers()} 用）</h3>
 * 
 * <pre>
 * ClientRegistry
 *     .registerTileEntity(GunSmithTableBlockEntity.class, "tacz_gun_smith_table", new GunSmithTableRenderer());
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntityRenderer<GunSmithTableBlockEntity>} → {@link TileEntitySpecialRenderer}：
 * 1.7.10 的方块实体渲染器是「非泛型 + 单个 {@code renderTileEntityAt(TileEntity, x, y, z, partialTicks)}」，
 * 且<b>不</b>自动把实体坐标压进 {@code GL_MODELVIEW}（见 {@code TileEntityRendererDispatcher#renderTileEntity}，
 * 它只设 lightmap、传相对相机坐标）。因此本类自己 {@code GL11.glPushMatrix()} +
 * {@code glTranslatef(x, y, z)}，并把一个<b>单位位姿</b>的 {@link PoseStack} 交给
 * {@link #render}（相对变换由 {@code render} 自己叠加）。</li>
 * <li>上游构造器的 {@code BlockEntityRendererProvider.Context} 在 1.7.10 是
 * {@link TileEntityRendererDispatcher#instance}（由 {@code func_147497_a} 注入到
 * {@code TileEntitySpecialRenderer.field_147501_a}），因此构造器改为<b>无参</b>。</li>
 * <li>{@code blockEntity.getBlockState()} → {@code world.getBlockMetadata(x, y, z)}，
 * 朝向/根判定改读 {@link AbstractGunSmithTableBlock#getFacing(int)} /
 * {@link AbstractGunSmithTableBlock#isRoot(int)}。</li>
 * <li>{@code RenderType} / {@code PoseStack} / {@code ItemDisplayContext} 全部换成 {@code mcshim} 同名类；
 * {@code com.mojang.math.Axis} → {@code mcshim.Axis}。</li>
 * <li>{@code MultiBufferSource} 由本类按 {@code MultiBufferSource.immediate()} 自建（1.7.10 的
 * {@code renderTileEntityAt} 没有 buffer 参数），绘制完由 {@code BedrockModel#render} 内部
 * {@code endBatch} 落盘。</li>
 * <li>{@code getViewDistance()} / {@code shouldRenderOffScreen()} 在 1.7.10 的 TESR 上<b>没有</b>对应钩子：
 * 视距与「离屏也渲染」由 {@code TileEntity#getMaxRenderDistanceSquared()} /
 * {@code TileEntity#getRenderBoundingBox()} 决定，{@link GunSmithTableBlockEntity} 已自行重写
 * {@code getRenderBoundingBox()}，故本类不再重复声明。
 * // TODO(L2): 上游 GunSmithTableRenderer.java:77-80 的
 * // {@code shouldRenderOffScreen()} 与上游 90 行附近的 {@code getViewDistance()} 在 1.7.10
 * // 无 TESR 钩子可挂，语义改由上述两个 TileEntity 方法承担（见 bullet 前文）。</li>
 * </ul>
 *
 * <p>
 * 依赖 block/entity 侧提供 1.7.10 TileEntity 形态（{@code GunSmithTableBlockEntity extends TileEntity}、
 * {@code getId()} 返回 {@link ResourceLocation}）。当前该包已是 1.7.10 形态。
 */
@SideOnly(Side.CLIENT)
public class GunSmithTableRenderer extends TileEntitySpecialRenderer {

    /**
     * 复用的顶点批次：{@code BufferSource#endBatch()} 之后内部 builder 与批次表都会清空，
     * 可跨调用/跨帧复用，避免每帧重新分配 {@code BufferBuilder} 的大数组。
     */
    private final MultiBufferSource.BufferSource bufferIn = MultiBufferSource.immediate();

    public GunSmithTableRenderer() {}

    public Optional<ClientBlockIndex> getIndex(GunSmithTableBlockEntity blockEntity) {
        ResourceLocation id = blockEntity.getId();
        if (id == null || id.equals(DefaultAssets.EMPTY_BLOCK_ID)) {
            return Optional.empty();
        }
        return TimelessAPI.getClientBlockIndex(id);
    }

    public static Optional<ClientBlockIndex> getIndex(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof IBlock iBlock) {
            ResourceLocation id = iBlock.getBlockId(stack);
            if (id == null || id.equals(DefaultAssets.EMPTY_BLOCK_ID)) {
                return Optional.empty();
            }
            return TimelessAPI.getClientBlockIndex(id);
        }
        return Optional.empty();
    }

    /**
     * 1.7.10 入口：执行 1.20 {@code BlockEntityRenderDispatcher} 替渲染器做的那一步
     * （把 {@code x/y/z} 压进 {@code GL_MODELVIEW}），再以<b>单位位姿</b>转调上游同名形状的
     * {@link #render}。批次在 GL 仍平移到方块位置时 flush，因此位姿不需要重复进 {@code PoseStack}。
     */
    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof GunSmithTableBlockEntity blockEntity)) {
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
            render(blockEntity, partialTicks, new PoseStack(), bufferIn, packedLight, OverlayTexture.NO_OVERLAY);
        } finally {
            // BedrockModel#render 已经 endBatch 过；这里再 flush 一次防止异常路径遗留顶点。
            RenderHelperCompat.endBatch(bufferIn);
            GL11.glPopMatrix();
        }
    }

    /** 上游 {@code render(GunSmithTableBlockEntity, float, PoseStack, MultiBufferSource, int, int)}。 */
    public void render(GunSmithTableBlockEntity blockEntity, float partialTick, PoseStack poseStack,
        MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn) {
        getIndex(blockEntity).ifPresent(index -> {
            BedrockModel model = index.getModel();
            ResourceLocation texture = index.getTexture();
            if (model == null) {
                return;
            }
            World world = blockEntity.getWorldObj();
            if (world == null) {
                return;
            }
            Block block = world.getBlock(blockEntity.xCoord, blockEntity.yCoord, blockEntity.zCoord);
            if (block instanceof AbstractGunSmithTableBlock tableBlock) {
                int metadata = world.getBlockMetadata(blockEntity.xCoord, blockEntity.yCoord, blockEntity.zCoord);
                if (!tableBlock.isRoot(metadata)) {
                    return;
                }
                ForgeDirection facing = tableBlock.getFacing(metadata);
                poseStack.pushPose();
                poseStack.translate(0.5, 1.5, 0.5);
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                poseStack.mulPose(Axis.YN.rotationDegrees(tableBlock.parseRotation(facing)));
                RenderType renderType = RenderConfig.BLOCK_ENTITY_TRANSLUCENT.get()
                    ? RenderType.entityTranslucent(texture)
                    : RenderType.entityCutout(texture);
                model.render(poseStack, ItemDisplayContext.NONE, renderType, combinedLightIn, combinedOverlayIn);
                poseStack.popPose();
            }
        });
    }
}
