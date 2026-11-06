package com.tacz.guns.client.model.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import org.joml.Quaternionf;

import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.VertexConsumer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BedrockPart {

    @Nullable
    public final String name;
    public final List<BedrockCube> cubes = new ArrayList<>();
    public final List<BedrockPart> children = new ArrayList<>();
    public float x;
    public float y;
    public float z;
    public float xRot;
    public float yRot;
    public float zRot;
    public float offsetX;
    public float offsetY;
    public float offsetZ;
    public boolean visible = true;
    public boolean illuminated = false;
    public boolean mirror;
    /**
     * 通常用于动画旋转。
     */
    public Quaternionf additionalQuaternion = new Quaternionf(0, 0, 0, 1);
    public float xScale = 1;
    public float yScale = 1;
    public float zScale = 1;
    protected BedrockPart parent;
    private float initRotX;
    private float initRotY;
    private float initRotZ;

    public BedrockPart(@Nullable String name) {
        this.name = name;
    }

    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void render(PoseStack poseStack, ItemDisplayContext transformType, VertexConsumer consumer, int light,
        int overlay) {
        this.render(poseStack, transformType, consumer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void render(PoseStack poseStack, ItemDisplayContext transformType, VertexConsumer consumer, int light,
        int overlay, float red, float green, float blue, float alpha) {
        render(
            poseStack,
            transformType,
            consumer,
            light,
            overlay,
            red,
            green,
            blue,
            alpha,
            RenderHelperCompat.getBufferSource(transformType));
    }

    public void render(PoseStack poseStack, ItemDisplayContext transformType, VertexConsumer consumer, int light,
        int overlay, float red, float green, float blue, float alpha, @Nullable MultiBufferSource bufferSource) {
        int cubePackedLight = light;
        VertexConsumer cubeConsumer = consumer;
        if (illuminated) {
            // 最大亮度
            cubePackedLight = LightTexture.pack(15, 15);
            cubeConsumer = UnshadedVertexConsumer.wrap(consumer);
        }
        if (this.visible) {
            if (!this.cubes.isEmpty() || !this.children.isEmpty()) {
                poseStack.pushPose();
                this.translateAndRotateAndScale(poseStack);
                this.compile(poseStack.last(), cubeConsumer, cubePackedLight, overlay, red, green, blue, alpha);

                for (BedrockPart part : this.children) {
                    part.render(
                        poseStack,
                        transformType,
                        cubeConsumer,
                        cubePackedLight,
                        overlay,
                        red,
                        green,
                        blue,
                        alpha,
                        bufferSource);
                }

                poseStack.popPose();
            }
        }
    }

    public void translateAndRotateAndScale(PoseStack poseStack) {
        poseStack.translate(this.offsetX, this.offsetY, this.offsetZ);
        poseStack.translate((this.x / 16.0F), (this.y / 16.0F), (this.z / 16.0F));
        // 移植说明：上游用 com.mojang.math.Axis（1.20）。Axis.ZP.rotation(r) 的实现就是
        // new Quaternionf().rotationZ(r)（Y→rotationY，X→rotationX，角度为弧度），
        // 这里直接用 JOML 等价的构造，避免依赖 mcshim.Axis 的方法子集。
        if (this.zRot != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationZ(this.zRot));
        }
        if (this.yRot != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationY(this.yRot));
        }
        if (this.xRot != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationX(this.xRot));
        }
        poseStack.mulPose(additionalQuaternion);
        poseStack.scale(xScale, yScale, zScale);
    }

    public void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, float red, float green,
        float blue, float alpha) {
        for (BedrockCube bedrockCube : this.cubes) {
            bedrockCube.compile(pose, consumer, light, overlay, red, green, blue, alpha);
        }
    }

    public BedrockCube getRandomCube(Random random) {
        return this.cubes.get(random.nextInt(this.cubes.size()));
    }

    public boolean isEmpty() {
        return this.cubes.isEmpty();
    }

    public void setInitRotationAngle(float x, float y, float z) {
        this.initRotX = x;
        this.initRotY = y;
        this.initRotZ = z;
    }

    public float getInitRotX() {
        return initRotX;
    }

    public float getInitRotY() {
        return initRotY;
    }

    public float getInitRotZ() {
        return initRotZ;
    }

    public void addChild(BedrockPart model) {
        this.children.add(model);
    }

    public BedrockPart getParent() {
        return parent;
    }
}
