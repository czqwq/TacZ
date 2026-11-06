package com.tacz.guns.client.model.functional;

import net.minecraft.client.Minecraft;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.mcshim.HumanoidArm;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.VertexConsumer;

/**
 * 第一人称左手：用原版皮肤手臂顶替枪模型自带的左手 cube。
 *
 * <p>
 * {@code FunctionalBedrockPart#render} 在 {@code functionalRenderer} 非 {@code null} 时<b>只</b>执行功能渲染器、
 * 不再 {@code compile} 自己的 cube（与上游逐行同构），因此「画不画手」只能在注册处决定，无法在本方法里提前 return。
 *
 * <p>
 * 平台差异：手臂几何来自 1.7.10 的 {@code ModelBiped}（截面 4×12×4 px，与 1.20 {@code PlayerModel}
 * 逐值相同）。这里传出去的 PoseStack 是「枪的手骨帧 × Rz(180°)」，语义与上游一致，但 1.7.10 不能直接
 * 照画（肩端会落到相机附近/背后）⇒ 由 {@code RenderHelperCompat#renderFirstPersonArm} 用「上游腕点 +
 * 原版第一人称手臂肩点」两端重建手臂位姿，见该方法 javadoc。
 */
public class LeftHandRender implements IFunctionalRenderer {

    private final BedrockAnimatedModel bedrockGunModel;

    public LeftHandRender(BedrockAnimatedModel bedrockGunModel) {
        this.bedrockGunModel = bedrockGunModel;
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
        if (transformType.firstPerson()) {
            if (!bedrockGunModel.getRenderHand()) {
                return;
            }
            // 移植说明：上游用 com.mojang.math.Axis.ZP.rotationDegrees(180f)，等价于
            // new Quaternionf().rotationZ((float) Math.toRadians(180))。见 BedrockPart#translateAndRotateAndScale 的说明。
            poseStack.mulPose(new Quaternionf().rotationZ((float) Math.PI));
            Matrix3f normal = new Matrix3f(
                poseStack.last()
                    .normal());
            Matrix4f pose = new Matrix4f(
                poseStack.last()
                    .pose());
            // 和枪械模型共用顶点缓冲的都需要代理到渲染结束后渲染
            bedrockGunModel.delegateRender((poseStack1, vertexBuffer1, transformType1, light1, overlay1) -> {
                PoseStack poseStack2 = new PoseStack();
                poseStack2.last()
                    .normal()
                    .mul(normal);
                poseStack2.last()
                    .pose()
                    .mul(pose);
                // 移植说明：Minecraft.getMinecraft().thePlayer 在 1.7.10 是 Minecraft.getMinecraft().thePlayer。
                RenderHelperCompat.renderFirstPersonArm(
                    Minecraft.getMinecraft().thePlayer,
                    HumanoidArm.LEFT,
                    poseStack2,
                    light1,
                    bufferSource);
                if (!OculusCompat.endBatch(bufferSource)) {
                    RenderHelperCompat.endBatch(bufferSource);
                }
            });
        }
    }
}
