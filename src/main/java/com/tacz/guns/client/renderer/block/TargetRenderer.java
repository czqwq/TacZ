package com.tacz.guns.client.renderer.block;

import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.tacz.guns.block.BlockMetadata;
import com.tacz.guns.block.entity.TargetBlockEntity;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 标靶方块（含「上下两块拼成的双格标靶」）的基岩模型渲染器：靶面朝受击方向倒下 + 玩家头像。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/renderer/block/TargetRenderer.java}
 *
 * <h3>注册信息（{@code ClientRegistry.registerTileEntityRenderers()} 用）</h3>
 * 
 * <pre>
 * ClientRegistry.registerTileEntity(TargetBlockEntity.class, "tacz_target", new TargetRenderer());
 * </pre>
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code BlockEntityRenderer<TargetBlockEntity>} → {@link TileEntitySpecialRenderer}，
 * 渲染器自己 {@code GL11.glPushMatrix()} + {@code glTranslatef(x, y, z)}（1.7.10 的
 * {@code TileEntityRendererDispatcher} 不替渲染器做 GL 平移，只设 lightmap 并传相对相机坐标）。</li>
 * <li>构造器去掉 {@code BlockEntityRendererProvider.Context}（1.7.10 由
 * {@link TileEntityRendererDispatcher#instance} / {@code field_147501_a} 提供上下文），改为无参。</li>
 * <li>{@code blockState.getValue(TargetBlock.FACING)} → {@link TargetBlockEntity#getFacing()}
 * （1.7.10 的朝向存在 metadata 低 2 bit，由该方块实体按
 * {@code BlockMetadata.facing(...)} 解出，编码与 {@code Direction#get2DDataValue()} 一致）。</li>
 * <li>{@code facing.get2DDataValue()} → {@link BlockMetadata#facing2D(ForgeDirection)}。</li>
 * <li>玩家皮肤：1.7.10 无 {@code SkinManager#getInsecureSkinInformation} /
 * {@code registerTexture} 的 1.20 命名，改调 {@code Minecraft#func_152342_ad()}（SkinManager）的
 * {@code func_152788_a(GameProfile)} / {@code func_152792_a(MinecraftProfileTexture, Type)}，
 * 与 1.7.10 原版 {@code TileEntitySkullRenderer} 取皮肤的路径完全一致。</li>
 * <li>{@code DefaultPlayerSkin.getDefaultSkin(UUIDUUID)} 在 1.7.10 <b>不存在</b>（1.8 才引入
 * UUID→Steve/Alex 的默认皮肤选择），改用 {@link AbstractClientPlayer#locationStevePng}。</li>
 * <li>{@code getViewDistance()} / {@code shouldRenderOffScreen()}：1.7.10 TESR 无此钩子，
 * 由 {@code TileEntity#getRenderBoundingBox()} / {@code getMaxRenderDistanceSquared()} 承担
 * （{@link TargetBlockEntity} 已重写 {@code getRenderBoundingBox()}）。</li>
 * </ul>
 *
 * <p>
 * 依赖 block/entity 侧提供 1.7.10 TileEntity 形态（{@code TargetBlockEntity extends TileEntity}、
 * {@code getFacing()} 返回 {@link ForgeDirection}、{@code oRot/rot} 为 public float、
 * {@code getOwner()} 返回 {@link GameProfile}）。当前该包已是 1.7.10 形态。
 */
@SideOnly(Side.CLIENT)
public class TargetRenderer extends TileEntitySpecialRenderer {

    private static final String UPPER_NAME = "target_upper";
    private static final String HEAD_NAME = "head";

    /**
     * 复用的顶点批次：{@code BufferSource#endBatch()} 之后内部 builder 与批次表都会清空，
     * 可跨调用/跨帧复用，避免每帧重新分配 {@code BufferBuilder} 的大数组。
     */
    private final MultiBufferSource.BufferSource bufferIn = MultiBufferSource.immediate();

    public TargetRenderer() {}

    public static Optional<BedrockModel> getModel() {
        return InternalAssetLoader.getBedrockModel(InternalAssetLoader.TARGET_MODEL_LOCATION);
    }

    /**
     * 1.7.10 入口：执行 1.20 {@code BlockEntityRenderDispatcher} 替渲染器做的那一步
     * （把 {@code x/y/z} 压进 {@code GL_MODELVIEW}），再以<b>单位位姿</b>转调上游同名形状的
     * {@link #render}。批次在 GL 仍平移到方块位置时 flush，因此位姿不需要重复进 {@code PoseStack}。
     */
    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TargetBlockEntity blockEntity)) {
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

    /** 上游 {@code render(TargetBlockEntity, float, PoseStack, MultiBufferSource, int, int)}。 */
    public void render(TargetBlockEntity blockEntity, float partialTick, PoseStack poseStack,
        MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn) {
        getModel().ifPresent(model -> {
            ForgeDirection facing = blockEntity.getFacing();
            BedrockPart headModel = model.getNode(HEAD_NAME);
            BedrockPart upperModel = model.getNode(UPPER_NAME);
            // 1.7.10 加固（硬崩溃防护）：{@code BedrockModel#getNode} 是 @Nullable
            // （BedrockModel.java:472-479：{@code modelMap.get(name)} 缺失即返回 null）。
            // 上游 1.20（TargetRenderer.java:44-48）直接解引用，依赖内置模型一定含 head/target_upper；
            // 但移植侧 bedrock 模型走「legacy/new 版本二选一」的加载分支，模型文件不匹配时
            // loadLegacyModel/loadNewModel 会提前 return 得到空 modelMap ⇒ getNode 全为 null，
            // 此时 TESR 每帧都会 NPE。同包 TargetMinecartRenderer.java:110 已有同样的判空，这里补齐。
            if (headModel == null || upperModel == null) {
                return;
            }
            float deg = -Mth.lerp(partialTick, blockEntity.oRot, blockEntity.rot);
            upperModel.xRot = (float) Math.toRadians(deg);
            headModel.visible = false;

            poseStack.pushPose();
            poseStack.translate(0.5, 0.225, 0.5);
            poseStack.mulPose(Axis.YN.rotationDegrees(BlockMetadata.facing2D(facing) * 90));
            poseStack.mulPose(Axis.ZN.rotationDegrees(180));
            poseStack.translate(0, -1.275, 0.0125);
            RenderType renderType = RenderType.entityTranslucent(InternalAssetLoader.TARGET_TEXTURE_LOCATION);
            model.render(poseStack, ItemDisplayContext.NONE, renderType, combinedLightIn, combinedOverlayIn);
            // 上游 BedrockModel#render 内部就 endBatch；这里显式 flush 一次，保证「靶体先画、
            // 头像后画」的绘制顺序，并让本类的正确性不依赖 util/RenderHelper 的接线状态。
            RenderHelperCompat.endBatch(bufferIn);
            if (blockEntity.getOwner() != null) {
                poseStack.translate(0, 1.25, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(deg));
                ResourceLocation skin = resolveSkin(blockEntity.getOwner());
                headModel.visible = true;
                RenderType skullRenderType = RenderType.entityCutout(skin);
                headModel.render(
                    poseStack,
                    ItemDisplayContext.NONE,
                    bufferIn.getBuffer(skullRenderType),
                    combinedLightIn,
                    OverlayTexture.NO_OVERLAY);
            }
            poseStack.popPose();
        });
    }

    /**
     * 1.7.10 侧「玩家皮肤 ResourceLocation」的解析，照抄原版 {@code TileEntitySkullRenderer#func_152674_a}
     * 里 {@code skullType == 3}（玩家头）那一段。
     *
     * <p>
     * // TODO(L2): 上游 TargetRenderer.java:62-71 做的是
     * // {@code minecraft.getSkinManager().getInsecureSkinInformation(owner)} 取
     * // {@code MinecraftProfileTexture.Type.SKIN}，命中则 {@code registerTexture(...)}，否则
     * // {@code DefaultPlayerSkin.getDefaultSkin(UUIDUtil.getOrCreatePlayerUUID(owner))}；
     * // 1.7.10 无 {@code DefaultPlayerSkin}（1.8 才引入 UUID→Steve/Alex 选择）也没有
     * // {@code UUIDUtil}，此处改用 {@link AbstractClientPlayer#locationStevePng} 兜底，
     * // 即「无皮肤档案的玩家一律用 Steve」，与 1.7.10 原版头颅渲染一致。
     */
    private static ResourceLocation resolveSkin(GameProfile owner) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraft.func_152342_ad()
            .func_152788_a(owner);
        if (map != null && map.containsKey(MinecraftProfileTexture.Type.SKIN)) {
            return minecraft.func_152342_ad()
                .func_152792_a(map.get(MinecraftProfileTexture.Type.SKIN), MinecraftProfileTexture.Type.SKIN);
        }
        return AbstractClientPlayer.locationStevePng;
    }

    /** 上游 {@code getViewDistance()}：1.7.10 由 {@code TileEntity#getMaxRenderDistanceSquared()} 承担。 */
    public int getViewDistance() {
        return RenderConfig.TARGET_RENDER_DISTANCE.get();
    }
}
