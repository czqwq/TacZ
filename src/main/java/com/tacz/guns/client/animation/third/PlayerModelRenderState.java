package com.tacz.guns.client.animation.third;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;

import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.mcshim.HumanoidArm;

/**
 * Restore the shared player model after a vanilla or mod-provided hand draw.
 *
 * <p>
 * <b>移植说明（1.20.1 → 1.7.10）</b>：上游基于 {@code net.minecraft.client.model.PlayerModel<?>} +
 * {@code ModelPart}/{@code PartPose}。1.7.10 没有这些类型：
 * <ul>
 * <li>玩家模型就是 {@link ModelBiped}（没有单独的 {@code PlayerModel} 类）。</li>
 * <li>部件是 {@link ModelRenderer} 的可变 public 字段（{@code rotationPointX/Y/Z}、{@code rotateAngleX/Y/Z}、
 * {@code offsetX/Y/Z}、{@code showModel}、{@code isHidden}），没有 {@code storePose()/loadPose()}，
 * 所以这里手写快照/还原。</li>
 * <li>1.20 {@code PlayerModel} 的 {@code hat/jacket/leftSleeve/rightSleeve/leftPants/rightPants}
 * 在 1.7.10 的 {@link ModelBiped} 里**只有 {@code bipedHeadwear}（对应 hat）有直接对应物**，
 * 皮肤外层（jacket/sleeve/pants）是渲染时用同一份几何贴不同 UV 实现的，没有独立字段。
 * 因此这些部件的快照被省略，多出的层由 {@code RenderPlayer} 自行处理。</li>
 * <li>1.20 的 {@code attackTime} / {@code swimAmount} / {@code leftArmPose} / {@code rightArmPose}
 * 在 1.7.10 {@link ModelBiped} 里没有对应物，降级为「不保存」；可用的替代量
 * {@code isSneak} / {@code isRiding} / {@code isChild} / {@code heldItemLeft} / {@code heldItemRight} /
 * {@code aimedBow} 全部保存与还原。</li>
 * </ul>
 */
public final class PlayerModelRenderState implements AutoCloseable {

    private final ModelBiped model;
    private final List<PartState> parts;

    private final boolean isSneak;
    private final boolean isRiding;
    private final boolean isChild;
    private final int heldItemLeft;
    private final int heldItemRight;
    private final boolean aimedBow;

    private final Runnable restoreAnimation;

    public PlayerModelRenderState(ModelBiped model) {
        this.model = model;
        List<PartState> collected = new ArrayList<PartState>();
        collect(collected, model.bipedHead);
        collect(collected, model.bipedHeadwear);
        collect(collected, model.bipedBody);
        collect(collected, model.bipedRightArm);
        collect(collected, model.bipedLeftArm);
        collect(collected, model.bipedRightLeg);
        collect(collected, model.bipedLeftLeg);
        collect(collected, model.bipedEars);
        collect(collected, model.bipedCloak);
        this.parts = collected;

        // TODO(port): 上游还会保存 attackTime / swimAmount / leftArmPose / rightArmPose（PlayerModel 的字段），
        // 1.7.10 ModelBiped 无对应字段，此处按「不保存」降级。
        this.isSneak = model.isSneak;
        this.isRiding = model.isRiding;
        this.isChild = model.isChild;
        this.heldItemLeft = model.heldItemLeft;
        this.heldItemRight = model.heldItemRight;
        this.aimedBow = model.aimedBow;

        this.restoreAnimation = PlayerAnimatorCompat.captureModelState(model);
    }

    private static void collect(List<PartState> out, ModelRenderer part) {
        if (part != null) {
            out.add(new PartState(part));
        }
    }

    public void prepareArm(HumanoidArm arm, boolean visible) {
        // A RenderArmEvent replacement can draw sleeve geometry before vanilla initializes it.
        // Vanilla's fallback still applies the player's skin-part settings in renderHand().
        if (arm == HumanoidArm.RIGHT) {
            if (model.bipedRightArm != null) {
                model.bipedRightArm.showModel = visible;
            }
        } else {
            if (model.bipedLeftArm != null) {
                model.bipedLeftArm.showModel = visible;
            }
        }
    }

    @Override
    public void close() {
        if (restoreAnimation != null) {
            restoreAnimation.run();
        }
        for (PartState part : parts) {
            part.restore();
        }
        model.isSneak = isSneak;
        model.isRiding = isRiding;
        model.isChild = isChild;
        model.heldItemLeft = heldItemLeft;
        model.heldItemRight = heldItemRight;
        model.aimedBow = aimedBow;
    }

    /**
     * 1.20 用 {@code ModelPart#storePose()/loadPose(PartPose)} 做快照；
     * 1.7.10 {@link ModelRenderer} 没有这层，直接逐字段拷贝。
     */
    private static final class PartState {

        private final ModelRenderer part;
        private final float rotationPointX;
        private final float rotationPointY;
        private final float rotationPointZ;
        private final float rotateAngleX;
        private final float rotateAngleY;
        private final float rotateAngleZ;
        private final float offsetX;
        private final float offsetY;
        private final float offsetZ;
        private final boolean showModel;
        private final boolean isHidden;

        private PartState(ModelRenderer part) {
            this.part = part;
            this.rotationPointX = part.rotationPointX;
            this.rotationPointY = part.rotationPointY;
            this.rotationPointZ = part.rotationPointZ;
            this.rotateAngleX = part.rotateAngleX;
            this.rotateAngleY = part.rotateAngleY;
            this.rotateAngleZ = part.rotateAngleZ;
            this.offsetX = part.offsetX;
            this.offsetY = part.offsetY;
            this.offsetZ = part.offsetZ;
            this.showModel = part.showModel;
            this.isHidden = part.isHidden;
        }

        private void restore() {
            part.rotationPointX = rotationPointX;
            part.rotationPointY = rotationPointY;
            part.rotationPointZ = rotationPointZ;
            part.rotateAngleX = rotateAngleX;
            part.rotateAngleY = rotateAngleY;
            part.rotateAngleZ = rotateAngleZ;
            part.offsetX = offsetX;
            part.offsetY = offsetY;
            part.offsetZ = offsetZ;
            part.showModel = showModel;
            part.isHidden = isHidden;
        }
    }
}
