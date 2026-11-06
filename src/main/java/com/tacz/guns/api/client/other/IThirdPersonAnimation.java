package com.tacz.guns.api.client.other;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLivingBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 第三人称持枪动画接口。
 * <p>
 * 1.7.10 移植说明：上游参数类型是 {@code net.minecraft.client.model.geom.ModelPart}（1.20.1），
 * 1.7.10 的等价物是 {@link ModelRenderer}（字段映射：{@code xRot→rotateAngleX}、{@code yRot→rotateAngleY}、
 * {@code zRot→rotateAngleZ}、{@code x/y/z→rotationPointX/rotationPointY/rotationPointZ}）。
 */
@SideOnly(Side.CLIENT)
public interface IThirdPersonAnimation {

    /**
     * 第三人称动画：主手持有枪械时
     *
     * @param entity   持有枪械的实体
     * @param rightArm 右手模型
     * @param leftArm  左手模型
     * @param body     身体模型
     * @param head     头部模型
     */
    void animateGunHold(EntityLivingBase entity, ModelRenderer rightArm, ModelRenderer leftArm, ModelRenderer body,
        ModelRenderer head);

    /**
     * 第三人称动画：枪械瞄准时
     *
     * @param entity      持有枪械的实体
     * @param rightArm    右手模型
     * @param leftArm     左手模型
     * @param body        身体模型
     * @param head        头部模型
     * @param aimProgress 瞄准进度 0-1
     */
    void animateGunAim(EntityLivingBase entity, ModelRenderer rightArm, ModelRenderer leftArm, ModelRenderer body,
        ModelRenderer head, float aimProgress);
}
