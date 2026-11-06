package com.tacz.guns.mixin.client;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.mcshim.EntityPose;
import com.tacz.guns.mcshim.EntityStateHelper;

/**
 * 趴下（crawl）的**客户端姿态/体型**：给本地玩家补上 1.20 {@code Pose.SWIMMING} 的可见效果。
 *
 * <p>
 * <b>上游效果（{@code Player#setForcedPose(Pose.SWIMMING)} 触发）</b>：
 * ① 包围盒高度 1.8 → 0.6（{@code Player.POSES} 的 SWIMMING = 0.6×0.6，宽不变，玩家本来就 0.6）；
 * ② 姿态进入 {@code EntityStateHelper#ENTITY_POSE_OVERRIDES}，供 {@code MouseHandlerMixin}（趴下俯仰限制）
 * 与 {@code CameraSetupEvent}（趴下后坐力系数）读取 —— 这两处目前因「没人写覆盖表」而恒不生效；
 * ③ 相机眼高 1.62 → 0.4（由 {@link EntityRendererCrawlCameraMixin} 承担）。
 *
 * <p>
 * <b>为什么用客户端 mixin 而不是改 {@code LocalPlayerCrawl}</b>：client 侧 crawl 状态的唯一权威是
 * {@link IClientPlayerGunOperator#isCrawl()}（{@code LocalPlayerCrawl#isCrawling}，由按键链路维护并发包），
 * 本类只读它；不复制状态、不需要跨模块改动。服务端体型/眼高由 {@code mixin/common/LivingEntityMixin} 用
 * {@code ShooterDataHolder#isCrawling} 同步落地。
 *
 * <p>
 * 1.7.10 包围盒锚点：{@code Entity#setPosition} 用 {@code [posY-yOffset+ySize, +height]}（{@code Entity.java:352}），
 * 客户端玩家 {@code yOffset=1.62} ⇒ 只改 {@code height} 时底边（脚）不动、向上收缩，与 1.20 的
 * {@code refreshDimensions} 对玩家「只缩不高抬」的语义一致（1.20 对 Player 不做 findFreePosition）。
 */
@Mixin(EntityPlayer.class)
public class PlayerCrawlPoseMixin {

    /** 1.20 玩家站立高（{@code Player.STANDING_DIMENSIONS} = 0.6 × 1.8）。 */
    private static final float STANDING_HEIGHT = 1.8F;
    /** 1.20 SWIMMING/CROUCHING 之外的趴下高（{@code Player.POSES} 的 SWIMMING = 0.6 × 0.6）。 */
    private static final float CRAWLING_HEIGHT = 0.6F;

    /** 本实体当前是否已按趴下改过体型（避免每 tick 重复写）。 */
    @Unique
    private boolean tacz$crawlHeightApplied;

    /**
     * 在 {@code EntityPlayer#onLivingUpdate} 的 RETURN 注入：此时 {@code EntityPlayerSP#onLivingUpdate} 的 HEAD
     * 已驱动过 {@code LocalPlayerCrawl#tickCrawl}（{@code LocalPlayerMixin}），crawl 状态是本 tick 最新的。
     */
    @Inject(method = "onLivingUpdate", at = @At("RETURN"))
    private void tacz$updateCrawlPose(CallbackInfo ci) {
        if (!((Object) this instanceof EntityPlayerSP player)
            || !(player instanceof IClientPlayerGunOperator operator)) {
            return;
        }
        boolean crawling = operator.isCrawl();
        // ① 姿势覆盖表：1.20 setForcedPose 的等价物（MouseHandlerMixin / CameraSetupEvent 的消费者）
        EntityStateHelper.setForcedPose(player, crawling ? EntityPose.SWIMMING : null);
        // ② 体型：只改高度（宽 0.6 两态相同），底边锚点不动
        if (crawling == this.tacz$crawlHeightApplied) {
            return;
        }
        this.tacz$crawlHeightApplied = crawling;
        Entity self = (Entity) (Object) this;
        self.height = crawling ? CRAWLING_HEIGHT : STANDING_HEIGHT;
        self.boundingBox.maxY = self.boundingBox.minY + (double) self.height;
    }
}
