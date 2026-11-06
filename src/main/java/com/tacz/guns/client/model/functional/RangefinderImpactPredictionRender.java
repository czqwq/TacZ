package com.tacz.guns.client.model.functional;

import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.pojo.display.gun.RangefinderManager;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.VertexConsumer;

public class RangefinderImpactPredictionRender implements IFunctionalRenderer {

    private static final float MODEL_TEXT_SCALE = 2.0f / 300.0f;
    private static final float FALLBACK_MODEL_PROJECTION_HALF_HEIGHT = 80.0f;
    private static final float HALF_SIZE = 3.0f;
    private static final float LINE_WIDTH = 1.0f;
    private static final int FULL_BRIGHT = LightTexture.pack(15, 15);

    private final BedrockModel bedrockModel;

    public RangefinderImpactPredictionRender(BedrockModel bedrockModel) {
        this.bedrockModel = bedrockModel;
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light,
        int overlay) {
        render(
            poseStack,
            vertexBuffer,
            transformType,
            light,
            overlay,
            RenderHelperCompat.getBufferSource(transformType));
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light,
        int overlay, MultiBufferSource bufferSource) {
        if (!transformType.firstPerson() || !RangefinderManager.shouldRenderImpactPrediction()) {
            return;
        }

        // 移植说明：上游用 com.mojang.math.Axis.ZP.rotationDegrees(180f)，
        // JOML 等价写法为 new Quaternionf().rotationZ((float) Math.toRadians(180))。
        poseStack.mulPose(new Quaternionf().rotationZ((float) Math.PI));
        Matrix3f normal = new Matrix3f(
            poseStack.last()
                .normal());
        Matrix4f pose = new Matrix4f(
            poseStack.last()
                .pose());

        bedrockModel.delegateRender((poseStack1, vertexBuffer1, transformType1, light1, overlay1) -> {
            PoseStack poseStack2 = new PoseStack();
            poseStack2.last()
                .normal()
                .mul(normal);
            poseStack2.last()
                .pose()
                .mul(pose);
            poseStack2.scale(MODEL_TEXT_SCALE, -MODEL_TEXT_SCALE, -MODEL_TEXT_SCALE);

            float yOffset = getCalibratedYOffset(
                poseStack2.last()
                    .pose());
            renderBox(
                poseStack2,
                yOffset,
                RangefinderManager.getImpactPredictionColor(),
                RangefinderManager.getImpactPredictionScale(),
                bufferSource);
        });
    }

    private static float getCalibratedYOffset(Matrix4f pose) {
        // 移植说明：上游用 Minecraft.getMinecraft().getWindow().getGuiScaledHeight()。
        // 1.7.10 的 Minecraft#displayHeight 就是「GUI 缩放后的高度」（与 1.20 的 GuiScaledHeight 同义）。
        int height = Minecraft.getMinecraft().displayHeight;
        float targetY = height / 2.0f + RangefinderManager.getImpactProjectionY() * height / 2.0f;
        float originY = projectGuiY(pose, 0.0f);
        float unitY = projectGuiY(pose, 1.0f);
        float pixelsPerLocalY = unitY - originY;
        if (!Float.isFinite(originY) || !Float.isFinite(unitY) || Math.abs(pixelsPerLocalY) < 1.0E-4f) {
            return RangefinderManager.getImpactProjectionY() * FALLBACK_MODEL_PROJECTION_HALF_HEIGHT;
        }
        return Mth.clamp((targetY - originY) / pixelsPerLocalY, -1000.0f, 1000.0f);
    }

    /**
     * 把模型空间的局部点投影到 GUI 像素 Y。
     *
     * <p>
     * 移植说明：上游用 {@code RenderSystem.getProjectionMatrix()}（1.20 的 CPU 侧投影矩阵缓存，JOML {@code Matrix4f}）。
     * mcshim 的同名方法返回的是 GL 投影矩阵的 {@link FloatBuffer} 快照（列主序），
     * 为避免依赖 JOML 的 BF→矩阵转换 API，这里直接用列主序下标做点积：
     * 行 r、列 c 的元素是 {@code m[c * 4 + r]}。
     */
    private static float projectGuiY(Matrix4f pose, float localY) {
        int height = Minecraft.getMinecraft().displayHeight;
        Vector4f point = new Vector4f(0.0f, localY, 0.0f, 1.0f);
        point.mul(pose);

        FloatBuffer projection = RenderSystem.getProjectionMatrix();
        float[] m = new float[16];
        projection.duplicate()
            .get(m);

        float x = point.x();
        float y = point.y();
        float z = point.z();
        float w = point.w();
        float clipY = m[1] * x + m[5] * y + m[9] * z + m[13] * w;
        float clipW = m[3] * x + m[7] * y + m[11] * z + m[15] * w;
        if (clipW <= 0.0f) {
            return Float.NaN;
        }
        float ndcY = clipY / clipW;
        return (1.0f - ndcY) * height / 2.0f;
    }

    /**
     * 用 {@link Tessellator} 直绘四条边框。
     *
     * <p>
     * 移植说明：上游 {@code ImpactPredictionRenderState} 用
     * {@code RenderType.create(...)} + {@code RenderType.CompositeState.builder()} + {@code RenderStateShard}
     * 构造一个「加法混合 + NO_CULL + 视图偏移 Z + 镭射贴图 + POSITION_COLOR_TEX_LIGHTMAP」的渲染层。
     * 1.7.10 没有 RenderType/ShaderState 那一整套，platform-shim 也不搬，
     * 所以这里把那一层的 GL 状态**手工**在固定管线上复现（并成对还原）：
     * <ul>
     * <li>{@code LIGHTNING_ADDITIVE_TRANSPARENCY} → {@code glBlendFunc(SRC_ALPHA, ONE)}（1.7.10 无 blendFuncSeparate 需求，
     * alpha 通道同 RGB，等价于 1.20 的 blendFuncSeparate(SRC_ALPHA, ONE, ONE, ZERO)）。</li>
     * <li>{@code VIEW_OFFSET_Z_LAYERING} → {@code glPolygonOffset(-1, -10)} + POLYGON_OFFSET_FILL。</li>
     * <li>{@code NO_CULL} → 关闭 GL_CULL_FACE。</li>
     * <li>{@code COLOR_DEPTH_WRITE} → 深度写入保持开启（默认）。</li>
     * <li>lightmap 满亮度 → {@code Tessellator#setBrightness(FULL_BRIGHT)}。</li>
     * </ul>
     */
    private static void renderBox(PoseStack poseStack, float yOffset, int color, float scale,
        MultiBufferSource bufferSource) {
        int a = color >>> 24 & 0xFF;
        int r = color >>> 16 & 0xFF;
        int g = color >>> 8 & 0xFF;
        int b = color & 0xFF;

        float halfSize = HALF_SIZE * scale;
        float left = -halfSize;
        float right = halfSize;
        float top = yOffset - halfSize;
        float bottom = yOffset + halfSize;

        // 1.7.10 的 shim MultiBufferSource 是立即模式的：先把已排队的枪体顶点冲刷掉，保证边框画在其后。
        RenderHelperCompat.endBatch(bufferSource);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(BeamRenderer.LASER_BEAM_TEXTURE);

        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);

        poseStack.pushToGL();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1.0F, -10.0F);

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.setBrightness(FULL_BRIGHT);
            tessellator.setColorRGBA(r, g, b, a);
            quad(tessellator, left, top, right, top + LINE_WIDTH);
            quad(tessellator, left, bottom - LINE_WIDTH, right, bottom);
            quad(tessellator, left, top, left + LINE_WIDTH, bottom);
            quad(tessellator, right - LINE_WIDTH, top, right, bottom);
            tessellator.draw();
        } finally {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (!blend) {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (cull) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            }
            if (lighting) {
                GL11.glEnable(GL11.GL_LIGHTING);
            }
            poseStack.popFromGL();
        }
    }

    private static void quad(Tessellator tessellator, float minX, float minY, float maxX, float maxY) {
        tessellator.addVertexWithUV(minX, minY, 0.0D, 0.0F, 0.0F);
        tessellator.addVertexWithUV(minX, maxY, 0.0D, 0.0F, 1.0F);
        tessellator.addVertexWithUV(maxX, maxY, 0.0D, 1.0F, 1.0F);
        tessellator.addVertexWithUV(maxX, minY, 0.0D, 1.0F, 0.0F);
    }
}
