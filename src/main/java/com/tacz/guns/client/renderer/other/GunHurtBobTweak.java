package com.tacz.guns.client.renderer.other;

import net.minecraft.client.entity.EntityPlayerSP;

import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.util.time.GunTime;

/**
 * 把「被枪打中」的受伤晃动与原版受伤晃动区分开：只有确实是枪械伤害造成的才用本类的幅度。
 *
 * <h2>1.7.10 平台替换</h2>
 * <ul>
 * <li>上游 {@code LocalPlayer} → 1.7.10 {@link EntityPlayerSP}（客户端玩家）。</li>
 * <li>上游 {@code Entity#hurtTime} 是 {@code float}、{@code Entity#hurtDuration} 是 {@code float}（1.20）；
 * 1.7.10 {@code EntityLivingBase} 里是 {@code public int hurtTime} 与 {@code public int maxHurtTime}，
 * 因此这里先转 {@code float} 再参与插值，数学与上游逐字一致。</li>
 * <li>上游 {@code Entity#getHurtDir()} → 1.7.10 {@code EntityLivingBase#attackedAtYaw}
 * （同一个「受击方向相对实体朝向的偏航角」，只是 1.7.10 是公开字段）。</li>
 * <li>{@code Mth} → {@link Mth}、{@code com.mojang.math.Axis} → {@link Axis}、
 * {@code PoseStack} → {@link PoseStack}（JOML 矩阵栈）。</li>
 * </ul>
 */
public class GunHurtBobTweak {

    private static long hurtByGunTimeStamp = -1L;
    private static float lastTweakMultiplier = 0.05f;

    /**
     * 上游同名方法：{@code true} 表示本帧已被本类接管（调用方不要再走原版受伤晃动）。
     */
    public static boolean onHurtBobTweak(EntityPlayerSP player, PoseStack matrixStack, float partialTicks) {
        // 原版受伤的时长是 500 ms，所以如果大于 500 ms，那么说明不是子弹造成的伤害了
        if (!GunTime.isWithin(hurtByGunTimeStamp, 500)) {
            // 返回 false，让程序调用原版受伤晃动
            return false;
        }
        float zRot = (float) player.hurtTime - partialTicks;
        if (zRot < 0) {
            return true;
        }
        zRot /= (float) player.maxHurtTime;
        zRot = Mth.sin(zRot * zRot * zRot * zRot * (float) Math.PI);
        float yRot = player.attackedAtYaw;

        yRot = yRot * lastTweakMultiplier;
        zRot = zRot * lastTweakMultiplier;

        matrixStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        matrixStack.mulPose(Axis.XP.rotationDegrees(-zRot * 14.0F));
        matrixStack.mulPose(Axis.YP.rotationDegrees(yRot));
        return true;
    }

    public static void markTimestamp(float tweakMultiplier) {
        hurtByGunTimeStamp = GunTime.nowMillis();
        lastTweakMultiplier = tweakMultiplier;
    }

    public static void resetTimestamp() {
        hurtByGunTimeStamp = -1L;
        lastTweakMultiplier = 0.05f;
    }
}
