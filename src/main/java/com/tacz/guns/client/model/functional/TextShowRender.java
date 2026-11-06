package com.tacz.guns.client.model.functional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;

import org.apache.commons.lang3.StringUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.papi.PapiManager;
import com.tacz.guns.client.resource.pojo.display.gun.RangefinderManager;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.VertexConsumer;

public class TextShowRender implements IFunctionalRenderer {

    private final BedrockModel bedrockModel;
    private final TextShow textShow;
    private final ItemStack gunStack;

    public TextShowRender(BedrockModel bedrockModel, TextShow textShow, ItemStack gunStack) {
        this.bedrockModel = bedrockModel;
        this.textShow = textShow;
        this.gunStack = gunStack;
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
        if (!transformType.firstPerson()) {
            return;
        }
        String text;
        try {
            RangefinderManager.setCurrentConfig(textShow.getRangefinder());
            text = PapiManager.getTextShow(textShow.getTextKey(), gunStack);
        } finally {
            RangefinderManager.clearCurrentConfig();
        }
        if (StringUtils.isBlank(text)) {
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

        // 和枪械模型共用顶点缓冲的都需要代理到渲染结束后渲染
        bedrockModel.delegateRender((poseStack1, vertexBuffer1, transformType1, light1, overlay1) -> {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            boolean shadow = textShow.isShadow();
            int color = textShow.getColorInt();
            float scale = textShow.getScale();
            int packLight = LightTexture.pack(textShow.getTextLight(), textShow.getTextLight());
            int width = font.getStringWidth(text);
            int xOffset;
            switch (textShow.getAlign()) {
                case CENTER -> xOffset = width / 2;
                case RIGHT -> xOffset = width;
                default -> xOffset = 0;
            }

            PoseStack poseStack2 = new PoseStack();
            poseStack2.last()
                .normal()
                .mul(normal);
            poseStack2.last()
                .pose()
                .mul(pose);
            poseStack2.scale(2 / 300f * scale, -2 / 300f * scale, -2 / 300f);

            // TODO(port): 上游 TextShowRender.java:76 用 1.20 的
            // Font#drawInBatch(text, x, y, color, shadow, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, packLight)：
            // 把字形顶点按矩阵写进共享顶点缓冲，并且每个顶点带自己的 lightmap 坐标。
            // 1.7.10 的 FontRenderer 没有「按矩阵 + 顶点缓冲」的 API，只有
            // drawString(String, int, int, int, boolean) —— 走固定管线 + 立即 Tessellator 绘制。
            // 因此这里改成：把 PoseStack 刷进 GL 固定管线（pushToGL）→ 平移到文字基线 → 直接 drawString。
            // 语义差异：lightmap 从「逐顶点」降级为「全局 glTexCoord1 光照贴图坐标」（1.7.10 唯一的做法），
            // 并且 drawString 的坐标必须是整数像素，所以 x 偏移用 glTranslatef 传（保留浮点精度）。
            // 1.7.10 的 shim MultiBufferSource 是立即模式的，先把已排队的枪体顶点冲刷掉，保证文字画在其后。
            RenderHelperCompat.endBatch(bufferSource);

            float lightU = packLight & 0xFFFF;
            float lightV = (packLight >>> 16) & 0xFFFF;
            boolean lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
            poseStack2.pushToGL();
            GL11.glTranslatef(-xOffset, -font.FONT_HEIGHT / 2f, 0.0F);
            try {
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU, lightV);
                font.drawString(text, 0, 0, color, shadow);
            } finally {
                // 硬还原到 1.7.10 的默认状态：光照贴图复位成满亮度，混合关掉，光照按进入前的状态恢复。
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
                GL11.glDisable(GL11.GL_BLEND);
                if (lightingEnabled) {
                    GL11.glEnable(GL11.GL_LIGHTING);
                } else {
                    GL11.glDisable(GL11.GL_LIGHTING);
                }
                poseStack2.popFromGL();
            }
        });
    }
}
