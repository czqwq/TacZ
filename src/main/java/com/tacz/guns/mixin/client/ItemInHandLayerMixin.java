package com.tacz.guns.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.util.MathHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.particle.ThirdPersonMuzzleParticleManager;
import com.tacz.guns.client.renderer.other.HumanoidOffhandRender;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;

/**
 * 1.7.10 版上游 {@code mixin/client/ItemInHandLayerMixin}（注入 {@code ItemInHandLayer}）。
 *
 * <p>
 * <b>上游用途</b>：① {@code renderArmWithItem} HEAD 记录「正在渲染第三人称持枪层的实体」
 * （{@code ThirdPersonMuzzleParticleManager.currentEntity}），本机玩家时置
 * {@code MuzzleFlashRender.isSelf / ShellRender.isSelf}；② {@code render} TAIL 复位这些状态，
 * 并调用 {@code HumanoidOffhandRender.renderGun(...)} 渲染挂枪。
 *
 * <p>
 * <b>1.7.10 等价改写</b>：1.7.10 没有 RenderLayer 体系，玩家第三人称持物渲染由
 * {@code RenderPlayer#renderEquippedItems(AbstractClientPlayer, float)} 内联完成
 * （{@code build/rfg/.../RenderPlayer.java:313-414}），故用 HEAD/RETURN 包住这一整段。
 * 注入必须写全描述符：{@code RenderPlayer:520} 还有桥接方法
 * {@code renderEquippedItems(EntityLivingBase;F)V}，只写方法名会让 Mixin 无法确定唯一目标。
 * 1.7.10 第三人称只渲染右手（{@code RenderPlayer:318}），上游「主手持枪时取消左手」没有对应分支；
 * 上游 {@code ItemInHandLayer#render} 在两手持物皆空时直接 return、不调用 {@code renderGun}，
 * 1.7.10 无双持槽 ⇒ 以主手判空等价。
 */
@Mixin(RenderPlayer.class)
public class ItemInHandLayerMixin {

    // ⚠ Mixin 硬规则：`@Mixin` 类里只允许 private static 字段，否则 MixinPreProcessorStandard#validateField
    // 会拒绝该 Mixin（InvalidMixinException），整个 client mixin 配置 apply 失败 ⇒ 启动崩。
    // 跨类共享的诊断/状态请放普通类的 public static，不要放这里。

    /**
     * 对应上游 {@code ItemInHandLayer#renderArmWithItem} HEAD：进入「玩家第三人称持物渲染」。
     *
     * <p>
     * 首参类型必须与目标描述符逐字一致（{@code AbstractClientPlayer}，父类不接受），
     * 且<b>目标方法的全部形参必须照抄</b>（含用不到的 {@code partialTicks}）——{@code @Inject}
     * 处理器签名 = 目标形参 + {@code CallbackInfo}，少写一个形参运行期会直接
     * {@code InvalidInjectionException}（= 启动崩）。1.7.10 的
     * {@code RenderPlayer#renderEquippedItems(AbstractClientPlayer, float)} 见 {@code RenderPlayer.java:313}。
     */
    @Inject(method = "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V", at = @At("HEAD"))
    private void renderArmWithItemHead(AbstractClientPlayer livingEntity, float partialTicks, CallbackInfo ci) {
        // 记录正在渲染第三人称持枪层的实体，供 BEWLR 捕获枪口世界坐标
        ThirdPersonMuzzleParticleManager.currentEntity = livingEntity;
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && livingEntity == player) {
            MuzzleFlashRender.isSelf = true;
            ShellRender.isSelf = true;
        }
    }

    /**
     * 对应上游 {@code ItemInHandLayer#render} TAIL + {@code renderArmWithItem} TAIL：
     * 离开「玩家第三人称持物渲染」并渲染挂枪。
     */
    @Inject(method = "renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V", at = @At("RETURN"))
    private void renderTail(AbstractClientPlayer livingEntity, float partialTicks, CallbackInfo ci) {
        MuzzleFlashRender.isSelf = false;
        ShellRender.isSelf = false;
        // 第三人称持枪层渲染结束，清除当前实体关联
        ThirdPersonMuzzleParticleManager.currentEntity = null;
        // 上游 ItemInHandLayer#render 在两手持物皆空时不执行（TAIL 也不执行）；1.7.10 无双持槽 ⇒ 只判主手。
        if (livingEntity.getHeldItem() == null) {
            return;
        }
        // packedLight：1.20 来自 ItemInHandLayer#render 形参，1.7.10 的 RenderPlayer#renderEquippedItems 无此参数。
        // 用世界亮度的 Entity 渲染全链路打包值；OpenGlHelper.lastBrightnessX/Y 只是上一次 setLightmapTextureCoords
        // 的残值，会随渲染顺序漂移，故不采用。
        HumanoidOffhandRender.renderGun(
            livingEntity,
            new PoseStack(),
            MultiBufferSource.immediate(),
            livingEntity.worldObj.getLightBrightnessForSkyBlocks(
                MathHelper.floor_double(livingEntity.posX),
                MathHelper.floor_double(livingEntity.posY),
                MathHelper.floor_double(livingEntity.posZ),
                0));
    }
}
