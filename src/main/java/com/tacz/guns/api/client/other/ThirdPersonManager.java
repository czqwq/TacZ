package com.tacz.guns.api.client.other;

import java.util.Map;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLivingBase;

import com.google.common.collect.Maps;
import com.tacz.guns.mcshim.Mth;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 简单的第三人称持枪动画 Manager。
 * <p>
 * 1.7.10 移植说明：{@code ModelPart}（1.20.1）→ {@link ModelRenderer}；字段映射
 * {@code yRot→rotateAngleY}、{@code xRot→rotateAngleX}、{@code zRot→rotateAngleZ}、
 * {@code x/y/z→rotationPointX/rotationPointY/rotationPointZ}（均为相对父骨骼的位置，1.20 与 1.7.10 语义一致）。
 * 1.20.1 {@code HumanoidModel#setupAttackAnimation} 与 1.7.10 {@code ModelBiped#setRotationAngles}
 * 都会在挥臂分支里按身体朝向重置手臂的 x/z 位置，注入点都在该分支之后，故不需要客户端额外复位。
 */
@SideOnly(Side.CLIENT)
public final class ThirdPersonManager {

    private static final Map<String, IThirdPersonAnimation> CACHE = Maps.newHashMap();
    private static final String RESERVED_DEFAULT_NAME = "default";
    private static final IThirdPersonAnimation DEFAULT = new IThirdPersonAnimation() {

        @Override
        public void animateGunHold(EntityLivingBase entity, ModelRenderer rightArm, ModelRenderer leftArm,
            ModelRenderer body, ModelRenderer head) {
            rightArm.rotateAngleY = -0.3F + head.rotateAngleY;
            leftArm.rotateAngleY = 0.8F + head.rotateAngleY;
            rightArm.rotateAngleX = -1.4F + head.rotateAngleX;
            leftArm.rotateAngleX = -1.4F + head.rotateAngleX;
        }

        @Override
        public void animateGunAim(EntityLivingBase entity, ModelRenderer rightArm, ModelRenderer leftArm,
            ModelRenderer body, ModelRenderer head, float aimProgress) {
            float lerp1 = Mth.lerp(aimProgress, 0.3f, 0.35f);
            float lerp2 = Mth.lerp(aimProgress, 1.4f, 1.6f);
            rightArm.rotateAngleY = -lerp1 + head.rotateAngleY;
            leftArm.rotateAngleY = 0.8F + head.rotateAngleY;
            rightArm.rotateAngleX = -lerp2 + head.rotateAngleX;
            leftArm.rotateAngleX = -lerp2 + head.rotateAngleX;
        }
    };

    private static final String MINI_GUN_NAME = "minigun";
    private static final IThirdPersonAnimation MINI_GUN = new IThirdPersonAnimation() {

        @Override
        public void animateGunHold(EntityLivingBase entity, ModelRenderer rightArm, ModelRenderer leftArm,
            ModelRenderer body, ModelRenderer head) {
            body.rotateAngleY = head.rotateAngleY + 0.8f;

            double cosTheta = Math.cos(-body.rotateAngleY);
            double sinTheta = Math.sin(-body.rotateAngleY);

            float x = rightArm.rotationPointX;
            rightArm.rotationPointX = (float) (x * cosTheta);
            rightArm.rotationPointZ = (float) (x * sinTheta);

            rightArm.rotateAngleY = -1.0F + body.rotateAngleY;
            rightArm.rotateAngleX = -0.1F + body.rotateAngleX;

            float x2 = leftArm.rotationPointX;
            leftArm.rotationPointX = (float) (x2 * cosTheta);
            leftArm.rotationPointZ = (float) (x2 * sinTheta);

            leftArm.rotateAngleY = -0.1F + body.rotateAngleY;
            leftArm.rotateAngleX = -1F + body.rotateAngleX;
        }

        @Override
        public void animateGunAim(EntityLivingBase entity, ModelRenderer rightArm, ModelRenderer leftArm,
            ModelRenderer body, ModelRenderer head, float aimProgress) {
            this.animateGunHold(entity, rightArm, leftArm, body, head);
        }
    };

    public static void registerDefault() {
        CACHE.put(RESERVED_DEFAULT_NAME, DEFAULT);
        CACHE.put(MINI_GUN_NAME, MINI_GUN);
    }

    public static void register(String name, IThirdPersonAnimation animation) {
        if (name.equals(RESERVED_DEFAULT_NAME)) {
            return;
        }
        CACHE.put(name, animation);
    }

    public static IThirdPersonAnimation getAnimation(String name) {
        return CACHE.getOrDefault(name, DEFAULT);
    }
}
