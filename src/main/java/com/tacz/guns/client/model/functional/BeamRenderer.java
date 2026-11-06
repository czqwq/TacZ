package com.tacz.guns.client.model.functional;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.util.LaserColorUtil;

public class BeamRenderer {

    public static final ResourceLocation LASER_BEAM_TEXTURE = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/entity/beam.png");
    private static final LaserConfig DEFAULT_LASER_CONFIG = new LaserConfig();
    /** 满亮度 lightmap，对应 1.20 里 RenderType 的 {@code LIGHTMAP} 状态 + brightness 15/15。 */
    private static final int FULL_BRIGHT = LightTexture.pack(15, 15);

    public static void renderLaserBeam(ItemStack stack, PoseStack poseStack, ItemDisplayContext transformType,
        @Nonnull List<BedrockPart> path, MultiBufferSource bufferSource) {
        if (stack == null
            || !transformType.firstPerson() && !(transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            return;
        }

        if (ARCompat.shouldAccelerate()
            && renderLaserBeamAccelerated(stack, poseStack, transformType, path, bufferSource)) {
            return;
        }

        renderLaserBeamUnaccelerated(stack, poseStack, transformType, path, bufferSource);
    }

    /** Resolve the legacy bone path before drawing an unaccelerated beam. */
    public static void renderLaserBeamUnaccelerated(ItemStack stack, PoseStack poseStack,
        ItemDisplayContext transformType, @Nonnull List<BedrockPart> path, MultiBufferSource bufferSource) {
        if (stack == null
            || !transformType.firstPerson() && transformType != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            return;
        }

        poseStack.pushPose();
        for (int i = 0; i < path.size(); ++i) {
            path.get(i)
                .translateAndRotateAndScale(poseStack);
        }
        renderLaserBeamUnaccelerated(stack, poseStack, transformType, bufferSource);
        poseStack.popPose();
    }

    /** Draw at an already resolved emitter pose, including SBM bone anchors. */
    public static void renderLaserBeamUnaccelerated(ItemStack stack, PoseStack poseStack,
        ItemDisplayContext transformType, MultiBufferSource bufferSource) {
        if (stack == null
            || !transformType.firstPerson() && transformType != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            return;
        }
        LaserConfig laserConfig = getLaserConfig(stack);

        int color = LaserColorUtil.getLaserColor(stack, laserConfig);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // 1.7.10 的 shim MultiBufferSource 是立即模式的：先把已排队的枪体顶点冲刷掉，保证镭射画在其后。
        RenderHelperCompat.endBatch(bufferSource);

        stringVertex(
            transformType.firstPerson() ? -laserConfig.getLength() : -laserConfig.getLengthThird(),
            transformType.firstPerson() ? laserConfig.getWidth() : laserConfig.getWidthThird(),
            poseStack,
            r,
            g,
            b,
            RenderConfig.ENABLE_LASER_FADE_OUT.get());
    }

    public static boolean renderLaserBeamAccelerated(ItemStack stack, PoseStack poseStack,
        ItemDisplayContext transformType, @Nonnull List<BedrockPart> path) {
        return renderLaserBeamAccelerated(
            stack,
            poseStack,
            transformType,
            path,
            RenderHelperCompat.getBufferSource(transformType));
    }

    public static boolean renderLaserBeamAccelerated(ItemStack stack, PoseStack poseStack,
        ItemDisplayContext transformType, @Nonnull List<BedrockPart> path, MultiBufferSource bufferSource) {
        // TODO(port): 上游 BeamRenderer.java:86-122 走 AcceleratedRendering 模组的加速管线
        // （ARCompat#isAccelerated / ARCompat#renderLaser 把镭射写进 NEW_ENTITY 格式的层缓冲）。
        // AcceleratedRendering 是 1.20 专属模组，1.7.10 不存在，ARCompat.LOADED 恒为 false，
        // 因此这里直接返回 false —— 调用方 renderLaserBeam 会回退到 unaccelerated 分支，
        // 与上游「不支持加速时回退」的既有语义一致。方法保留以免破坏公开 API。
        return false;
    }

    private static LaserConfig getLaserConfig(ItemStack stack) {
        if (stack == null) {
            return DEFAULT_LASER_CONFIG;
        }

        if (stack.getItem() instanceof IAttachment iAttachment) {
            return TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(stack))
                .map(ClientAttachmentIndex::getLaserConfig)
                .orElse(DEFAULT_LASER_CONFIG);
        }

        if (stack.getItem() instanceof IGun) {
            return TimelessAPI.getGunDisplay(stack)
                .map(GunDisplayInstance::getLaserConfig)
                .orElse(DEFAULT_LASER_CONFIG);
        }

        return DEFAULT_LASER_CONFIG;
    }

    /**
     * 四条贴图四边形组成的十字形激光束。
     *
     * <p>
     * 移植说明：上游 {@code BeamRenderer#stringVertex} 把顶点写进
     * {@code bufferSource.getBuffer(LaserBeamRenderState.getLaserBeam())}，该 RenderType 的顶点格式是
     * {@code POSITION_COLOR_TEX_LIGHTMAP}。1.7.10 没有 RenderType，这里改为
     * {@link Tessellator} 直绘：把 {@code poseStack} 的栈顶矩阵刷进固定管线（{@code pushToGL}），
     * 顶点仍用局部坐标，GL 负责变换 —— 与 1.20 里由 shader 的 ModelViewMat 变换顶点等价。
     * GL 状态由 {@link LaserBeamRenderState} 成对施加/还原。
     */
    private static void stringVertex(float z, float width, PoseStack poseStack, int r, int g, int b, boolean fadeOut) {
        float halfWidth = width / 2;
        int endAlpha = fadeOut ? 0 : 255;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(LASER_BEAM_TEXTURE);

        LaserBeamRenderState.applyGlState();
        poseStack.pushToGL();
        LaserBeamRenderState.applyZOffset();
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setBrightness(FULL_BRIGHT);

            // 面 1
            beamVertex(tessellator, -halfWidth, -halfWidth, 0, 0, 0, r, g, b, 255);
            beamVertex(tessellator, -halfWidth, halfWidth, 0, 0, 1, r, g, b, 255);
            beamVertex(tessellator, -halfWidth, halfWidth, z, 1, 1, r, g, b, endAlpha);
            beamVertex(tessellator, -halfWidth, -halfWidth, z, 1, 0, r, g, b, endAlpha);

            // 面 2
            beamVertex(tessellator, -halfWidth, halfWidth, 0, 0, 0, r, g, b, 255);
            beamVertex(tessellator, halfWidth, halfWidth, 0, 0, 1, r, g, b, 255);
            beamVertex(tessellator, halfWidth, halfWidth, z, 1, 1, r, g, b, endAlpha);
            beamVertex(tessellator, -halfWidth, halfWidth, z, 1, 0, r, g, b, endAlpha);

            // 面 3
            beamVertex(tessellator, halfWidth, halfWidth, 0, 0, 0, r, g, b, 255);
            beamVertex(tessellator, halfWidth, -halfWidth, 0, 0, 1, r, g, b, 255);
            beamVertex(tessellator, halfWidth, -halfWidth, z, 1, 1, r, g, b, endAlpha);
            beamVertex(tessellator, halfWidth, halfWidth, z, 1, 0, r, g, b, endAlpha);

            // 面 4
            beamVertex(tessellator, halfWidth, -halfWidth, 0, 0, 1, r, g, b, 255);
            beamVertex(tessellator, -halfWidth, -halfWidth, 0, 0, 1, r, g, b, 255);
            beamVertex(tessellator, -halfWidth, -halfWidth, z, 1, 1, r, g, b, endAlpha);
            beamVertex(tessellator, halfWidth, -halfWidth, z, 1, 0, r, g, b, endAlpha);

            tessellator.draw();
        } finally {
            poseStack.popFromGL();
            LaserBeamRenderState.restoreGlState();
        }
    }

    private static void beamVertex(Tessellator tessellator, float x, float y, float z, float u, float v, int r, int g,
        int b, int a) {
        tessellator.setColorRGBA(r, g, b, a);
        tessellator.addVertexWithUV(x, y, z, u, v);
    }

    /**
     * 1.7.10 版的「镭射渲染层」：把上游 {@code LaserBeamRenderState}（1.20 {@code RenderStateShard} +
     * {@code RenderType.create(POSITION_COLOR_TEX_LIGHTMAP, QUADS, ...)}）逐条降级为固定管线状态。
     * <ul>
     * <li>{@code LIGHTNING_ADDITIVE_TRANSPARENCY}：{@code glBlendFuncSeparate(SRC_ALPHA, ONE, ONE, ZERO)}，同参。</li>
     * <li>{@code VIEW_OFFSET_Z_LAYERING}：上游给 modelview 乘 {@code scale(0.99975586)}，
     * 由 {@link #applyZOffset()} 在 {@code pushToGL()} 之后施加（GL 矩阵右乘，与上游等价）。</li>
     * <li>{@code OUTPUT(ITEM_ENTITY_TARGET)}：1.7.10 只有主帧缓冲，无操作。</li>
     * <li>{@code LIGHTMAP}：由调用方 {@code Tessellator#setBrightness(FULL_BRIGHT)} 提供。</li>
     * <li>{@code COLOR_DEPTH_WRITE} / {@code NO_CULL}：深度写入默认开启；CULL 关闭。</li>
     * </ul>
     * 上游的 {@code getLaserBeam()} / {@code getLaserBeamEntity()} 返回 1.20 RenderType，1.7.10 无对应类型，
     * 故不再提供（这两个方法只有 BeamRenderer 自己用）。
     */
    public static class LaserBeamRenderState {

        /** 对应 1.20 {@code VIEW_OFFSET_Z_LAYERING} 的 modelview 缩放值（{@code RenderStateShard.java:290}）。 */
        private static final float VIEW_OFFSET_Z_SCALE = 0.99975586F;

        /** 进入时保存的 GL 状态，供 {@link #restoreGlState()} 硬还原。 */
        private static boolean savedLighting;
        private static boolean savedCull;
        private static boolean savedBlend;

        public static void applyGlState() {
            savedLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
            savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            savedBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        }

        /** 必须在 {@link PoseStack#pushToGL()} 之后调用，把 modelview 向摄像机方向缩一点（对应上游 layer 状态）。 */
        public static void applyZOffset() {
            GL11.glScalef(VIEW_OFFSET_Z_SCALE, VIEW_OFFSET_Z_SCALE, VIEW_OFFSET_Z_SCALE);
        }

        public static void restoreGlState() {
            // 混合函数没有可读回状态，统一还原成原版预设的「半透明」（entityTranslucent）默认。
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (!savedBlend) {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (savedCull) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            }
            if (savedLighting) {
                GL11.glEnable(GL11.GL_LIGHTING);
            }
        }
    }
}
