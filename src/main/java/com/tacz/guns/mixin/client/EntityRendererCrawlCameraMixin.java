package com.tacz.guns.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;

/**
 * 趴下（crawl）的**相机高度**：把 1.7.10 {@code EntityRenderer#orientCamera} 里写死的眼高 1.62 降到 1.20 的 0.4。
 *
 * <p>
 * <b>上游依据</b>：1.20.1 {@code Player#getStandingEyeHeight(Pose.SWIMMING, dims) = 0.4F}（{@code Player.java:1906-1917}），
 * 站立是 1.62 ⇒ 相机（第一/第三人称同源）整体下移 1.22。
 *
 * <p>
 * <b>为什么必须注入 {@code orientCamera}</b>：1.7.10 的相机高度公式是
 * {@code float f1 = renderViewEntity.yOffset - 1.62F;}（{@code EntityRenderer.java:555}）+ 末尾
 * {@code glTranslatef(0, f1, 0)}（{@code :645}），其中 {@code yOffset} 同时参与 {@code Entity#setPosition} 的
 * 包围盒锚点（{@code Entity.java:352}），对玩家它恒为 1.62 ⇒ <b>眼高被写死成 1.62</b>，既非 {@code getEyeHeight()}
 * 也随 yOffset 联动（改 yOffset 会同时移动 posY 与包围盒，相机相对包围盒底仍是 1.62）。因此唯一干净落点是
 * 在 {@code orientCamera} 里把这第一个 {@code float} 局部量（f1）加上下移量：camera = boxBottom + 1.62 - drop。
 *
 * <p>
 * 只影响本帧相机（f1 是局部变量），不改实体状态、不需要 finally 还原。蹲下/睡觉等原版分支不受影响
 * （趴下与它们互斥：{@code LivingEntityCrawl#canCrawl} 排除睡觉）。
 */
@Mixin(EntityRenderer.class)
public class EntityRendererCrawlCameraMixin {

    /** 1.20 站立眼高（{@code Player.java:1915}）。 */
    private static final float STANDING_EYE_HEIGHT = 1.62F;
    /** 1.20 趴下眼高（{@code Player.java:1911}）。 */
    private static final float CRAWLING_EYE_HEIGHT = 0.4F;

    /**
     * 修改 {@code orientCamera} 的第一个 float 局部变量（{@code f1 = yOffset - 1.62F}）。
     *
     * <p>
     * {@code require = 0}：{@code EntityRenderer#orientCamera} 是 OptiFine/Angelica 一类渲染优化模组最常重写的
     * 方法之一；若被改写导致 0 命中，宁可「趴下相机高度不生效」也不能让游戏起不来（同 {@code LivingEntityKnockbackMixin} 的取舍）。
     * {@code expect = 1} 保留为诊断口径（开 {@code -Dmixin.debug.injectors=true} 时 0 命中会抛错，便于实机判定）。
     *
     * @param original 原值 {@code yOffset - 1.62F}
     * @return 趴下时加大 f1，使最终相机位置下移 {@code 1.62 - 0.4}
     */
    @ModifyVariable(method = "orientCamera", at = @At("STORE"), ordinal = 0, require = 0, expect = 1)
    private float tacz$lowerCameraWhenCrawling(float original) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        // instanceof 而非直接 fromLocalPlayer 强转：若 LocalPlayerMixin 未 apply（其它 mixin 环境），
        // 每帧的相机回调里抛 CCE 会直接崩客户端；这里只降级为「相机不降」。
        if (!(player instanceof IClientPlayerGunOperator operator) || !operator.isCrawl()) {
            return original;
        }
        return original + (STANDING_EYE_HEIGHT - CRAWLING_EYE_HEIGHT);
    }
}
