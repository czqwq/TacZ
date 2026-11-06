package com.tacz.guns.client.animation.third;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;

/**
 * 第三人称持枪手臂姿态的公共入口。
 *
 * <p>
 * <b>移植说明</b>：上游的 {@code net.minecraft.client.model.geom.ModelPart} 在 1.7.10 对应
 * {@link net.minecraft.client.model.ModelRenderer}（由 common-api 的
 * {@code IThirdPersonAnimation} / {@code ThirdPersonManager} 已按此改造）。
 */
public class InnerThirdPersonManager {

    public static void setRotationAnglesHead(EntityLivingBase entityIn, ModelRenderer rightArm, ModelRenderer leftArm,
        ModelRenderer body, ModelRenderer head, float limbSwingAmount) {
        if (AnimationRenderContext.isFirstPersonHand(entityIn) || !PlayerAnimatorCompat.canPlayAnimation(entityIn)) {
            return;
        }
        if (entityIn instanceof IGunOperator) {
            IGunOperator operator = (IGunOperator) entityIn;
            // 1.20: entityIn.getHeldItem()；1.7.10 对应 EntityLivingBase#getHeldItem()
            ItemStack mainHandItem = entityIn.getHeldItem();
            TimelessAPI.getGunDisplay(mainHandItem)
                .ifPresent(display -> {
                    if (!PlayerAnimatorCompat.hasPlayerAnimator3rd(entityIn, display)) {
                        playVanillaAnimation(entityIn, rightArm, leftArm, body, head, operator, display);
                    }
                });
        }
    }

    private static void playVanillaAnimation(EntityLivingBase entityIn, ModelRenderer rightArm, ModelRenderer leftArm,
        ModelRenderer body, ModelRenderer head, IGunOperator operator, GunDisplayInstance display) {
        String animation = display.getThirdPersonAnimation();
        float aimingProgress = operator.getSynAimingProgress();
        if (aimingProgress <= 0) {
            ThirdPersonManager.getAnimation(animation)
                .animateGunHold(entityIn, rightArm, leftArm, body, head);
        } else {
            ThirdPersonManager.getAnimation(animation)
                .animateGunAim(entityIn, rightArm, leftArm, body, head, aimingProgress);
        }
    }
}
