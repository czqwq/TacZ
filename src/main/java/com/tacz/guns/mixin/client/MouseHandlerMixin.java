package com.tacz.guns.mixin.client;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.config.client.ZoomConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.mcshim.EntityPose;
import com.tacz.guns.mcshim.EntityStateHelper;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.util.math.Easing;
import com.tacz.guns.util.math.MathUtil;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/MouseHandlerMixin.java}。
 *
 * <p>
 * <b>上游用途</b>：开镜时按放大倍率压低鼠标灵敏度（{@code reduceSensitivity}），并在趴下（crawl）时
 * 限制俯仰角范围（{@code getCrawlPitch}）。
 *
 * <p>
 * <b>1.7.10 等价改写（目标类变了，务必看）</b>：
 * <ol>
 * <li>1.7.10 <b>没有 {@code MouseHandler}</b>。鼠标输入的读取在 {@code net.minecraft.util.MouseHelper}
 * （只有 {@code deltaX/deltaY}），而「把鼠标位移应用到视角」的那一步是
 * {@code EntityRenderer#updateCameraAndRender}({@code EntityRenderer.java:1041-1066}，SRG {@code func_78480_b})
 * 里的 {@code this.mc.thePlayer.setAngles(f3, f4 * b0)}。</li>
 * <li>{@code EntityPlayerSP} 本身<b>没有</b> {@code setAngles}（{@code grep} 结果：定义在
 * {@code net.minecraft.entity.Entity#setAngles(float,float)}，{@code Entity.java:359-379}，
 * 且带 {@code @SideOnly(CLIENT)}）；它的语义与上游 {@code LocalPlayer#turn(DD)} 完全一致
 * （{@code rotationYaw += yaw * 0.15}、{@code rotationPitch -= pitch * 0.15}、pitch 夹到 ±90）。</li>
 * <li>所以本 Mixin 直接 {@code @Mixin(Entity.class)}，对 {@code setAngles} 的两个 float 形参做
 * {@link ModifyVariable}。这样既避开 {@code @Redirect} 需要硬编码 INVOKE 描述符（1.7.10 的
 * {@code thePlayer} 字段类型是 {@code EntityPlayerSP}，但方法声明在 {@code Entity}，匹配不稳定）的风险，
 * 又天然覆盖 {@code updateCameraAndRender} 里的两个调用点（smoothCamera 分支与常规分支）。</li>
 * <li>{@code Minecraft#options.fov()} → {@code Minecraft#gameSettings.fovSetting}；
 * {@code Minecraft#getInstance()} → {@code Minecraft#getMinecraft()}；
 * {@code player.getMainHandItem()} → {@code player.getHeldItem()}（1.7.10 空手返回 {@code null}）。</li>
 * <li>{@code player.getPose()}/{@code Pose.SWIMMING}/{@code player.isSwimming()} →
 * {@code EntityStateHelper#getPose(player)} / {@code EntityPose.SWIMMING} / {@code player.isInWater()}。
 * 1.7.10 没有姿势系统，「趴下」由 {@code EntityStateHelper.ENTITY_POSE_OVERRIDES} 表达（见该类 javadoc），
 * 因此这里的判断是「不在水里 && 姿势被强制为 SWIMMING」= 陆上趴下，与上游语义一致。</li>
 * <li>{@code player.getXRot()} → {@code player.rotationPitch}；{@code Mth.clamp} →
 * {@link Mth#clamp(double, double, double)}。</li>
 * </ol>
 *
 * <p>
 * <b>注意</b>：两个 {@code @ModifyVariable} 各自独立计算一次 {@code denominator}（上游只算一次），
 * 因为 Mixin 的变量修改回调之间无法共享局部变量。计算量是一帧两次的常量级浮点运算 + 若干 map 查表，
 * 与渲染开销相比可忽略；<b>不要</b>为了复用而引入静态缓存，会被多实例/多线程干扰。
 *
 * <p>
 * <b>生效状态</b>：功能等价，无降级。
 */
@Mixin(Entity.class)
public class MouseHandlerMixin {

    /**
     * 压低 yaw 灵敏度（上游 {@code LocalPlayer#turn} 的第一个参数）。
     *
     * <p>
     * {@code index = 1} 是 1.7.10 {@code Entity#setAngles(float yaw, float pitch)} 里 {@code yaw} 的
     * 局部变量槽位（槽 0 是 {@code this}）。
     */
    @ModifyVariable(method = "setAngles", at = @At("HEAD"), index = 1)
    private float tacz$reduceYawSensitivity(float yaw) {
        if (!((Object) this instanceof EntityPlayerSP player)) {
            return yaw;
        }
        return (float) ((double) yaw * tacz$aimingSensitivityDenominator(player));
    }

    /**
     * 压低 pitch 灵敏度并叠加趴下俯仰限制（上游 {@code LocalPlayer#turn} 的第二个参数）。
     *
     * <p>
     * {@code index = 2} 是 {@code pitch} 的槽位。
     */
    @ModifyVariable(method = "setAngles", at = @At("HEAD"), index = 2)
    private float tacz$reducePitchSensitivity(float pitch) {
        if (!((Object) this instanceof EntityPlayerSP player)) {
            return pitch;
        }
        double scaled = (double) pitch * tacz$aimingSensitivityDenominator(player);
        return (float) tacz$getCrawlPitch(player, scaled);
    }

    /**
     * 上游 {@code MouseHandlerMixin#reduceSensitivity} 里从「放大倍率 + 开镜进度 + 配置」推导出的
     * 分母（{@code finalYaw = yaw * denominator}）。主手不是枪时返回 1.0（不改灵敏度）。
     */
    @Unique
    private static double tacz$aimingSensitivityDenominator(EntityPlayerSP player) {
        ItemStack mainHandItem = player.getHeldItem();
        IGun iGun = IGun.getIGunOrNull(mainHandItem);
        if (iGun == null) {
            return 1.0D;
        }
        ResourceLocation scopeId = iGun.getAttachmentId(mainHandItem, AttachmentType.SCOPE);
        if (scopeId == null || DefaultAssets.EMPTY_ATTACHMENT_ID.equals(scopeId)) {
            scopeId = iGun.getBuiltInAttachmentId(mainHandItem, AttachmentType.SCOPE);
        }
        float zoomLevel = 1.0F;
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            // 缩放倍率（无瞄具时用枪械 display 里的机瞄倍率）
            zoomLevel = TimelessAPI.getGunDisplay(mainHandItem)
                .map(GunDisplayInstance::getIronZoom)
                .orElse(1.0F);
        } else {
            Optional<ClientAttachmentIndex> optional = TimelessAPI.getClientAttachmentIndex(scopeId);
            if (optional.isPresent()) {
                float[] zoom = optional.get()
                    .getZoom();
                if (zoom != null && zoom.length > 0) {
                    // 与上游一致：attachmentTag 为 null（原厂内置瞄具没有物品 NBT）时
                    // getZoomNumberFromTag 返回 0 ⇒ 取 zoom[0]，不能跳过（否则内置瞄具灵敏度系数恒 1）。
                    NBTTagCompound attachmentTag = iGun.getAttachmentTag(mainHandItem, AttachmentType.SCOPE);
                    zoomLevel = zoom[AttachmentItemDataAccessor.getZoomNumberFromTag(attachmentTag) % zoom.length];
                }
            }
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        float progress = IGunOperator.fromLivingEntity(player)
            .getSynAimingProgress();
        float fovProgress = (float) Easing.easeInExpo(progress);
        // 开镜灵敏度系数
        double sensitivityMultiplier = ZoomConfig.ZOOM_SENSITIVITY_BASE_MULTIPLIER.get();
        sensitivityMultiplier = 1 + (sensitivityMultiplier - 1) * progress;
        // 两种状态下的 fov 计算
        double originalFov = minecraft.gameSettings.fovSetting;
        double currentFov = MathUtil.magnificationToFov(1 + (zoomLevel - 1) * fovProgress, originalFov);
        // 荧幕距离系数，MC 和 COD 一样使用 MDV 标准，默认为 MDV133（系数为 1.33）
        double coefficient = ZoomConfig.SCREEN_DISTANCE_COEFFICIENT.get();
        return MathUtil.zoomSensitivityRatio(currentFov, originalFov, coefficient) * sensitivityMultiplier;
    }

    /**
     * 上游 {@code MouseHandlerMixin#getCrawlPitch}。
     *
     * <p>
     * 1.7.10 差异：{@code !player.isSwimming() && player.getPose() == Pose.SWIMMING}
     * → {@code !player.isInWater() && EntityStateHelper.getPose(player) == EntityPose.SWIMMING}，
     * 理由见类 javadoc。
     */
    @Unique
    private static double tacz$getCrawlPitch(EntityPlayerSP player, double pitch) {
        boolean crawling = !player.isInWater() && EntityStateHelper.getPose(player) == EntityPose.SWIMMING;
        if (!crawling) {
            return pitch;
        }
        double minPitch = -SyncConfig.CRAWL_MAX_UP_ANGLE.get();
        double maxPitch = SyncConfig.CRAWL_MAX_DOWN_ANGLE.get();
        double currentPitch = player.rotationPitch;
        double minInput = Math.min(0, (minPitch - currentPitch) / 0.15F);
        double maxInput = Math.max(0, (maxPitch - currentPitch) / 0.15F);
        return Mth.clamp(pitch, minInput, maxInput);
    }
}
