package com.tacz.guns.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.CameraStateCache;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.client.event.RenderLevelBobEvent;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.client.renderer.scope.ScopeLensRenderer;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.PoseStack;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/GameRendererMixin.java}，对应 1.7.10 的 EntityRenderer。
 *
 * <p>
 * 注入点对照（上游 → 1.7.10，均已实现）：{@code renderLevel}HEAD → {@code renderWorld}HEAD
 * （EntityRenderer.java:1183）；镜片 → {@code renderHand} 里 {@code ItemRenderer#renderOverlays(F)V}
 * 之前（:805）；{@code bobHurt}HEAD → {@code hurtCameraEffect}HEAD（:507）；{@code bobHurt} 第 3 个
 * rotationDegrees 的 {@code @ModifyArg} → {@code hurtCameraEffect} 第 3 个 {@code glRotatef}（:525）；
 * {@code bobView}HEAD → {@code setupViewBobbing}HEAD（:533）；{@code getFov}HEAD →
 * {@code getFOVModifier(float,boolean)}HEAD（:473）。
 *
 * <p>
 * 唯一降级：上游 {@code renderLevel} 的 {@code ProfilerFiller.popPush} ordinal=2（shader 镜片）在
 * 1.7.10 没有对应段名（只有 {@code mcProfiler.endStartSection}，段名也不同），无法复刻 ⇒
 * {@code tacz$drawShaderScopeLenses} 保持注释。它也不能与 {@code tacz$drawVanillaScopeLenses} 同时启用：
 * {@code ScopeLensRenderer.render()} 不清理 {@code LENSES}（只有 {@code beginFrame()} 清理，
 * ScopeLensRenderer.java:131），会双绘 + 多一次 copyScreen。
 *
 * <p>
 * 新增 ⑦ 相机 roll（1.7.10 无相机角度事件）：见 {@link #tacz$applyCameraRoll}。
 */
@Mixin(EntityRenderer.class)
public abstract class GameRendererMixin {

    /**
     * 上游用 {@code getFov} 的 {@code pUseFovSetting} 判断当前在渲染 Level 还是手持物。
     * 1.7.10 的 {@code getFOVModifier(float, boolean)} 第二参数同义（true=世界，false=手持物）。
     */
    @Unique
    private boolean tacz$useFovSetting;

    /** 上游 {@code @Shadow abstract Minecraft getMinecraft()}。 */
    @Unique
    private Minecraft tacz$minecraft() {
        // 1.7.10 的 EntityRenderer 没有 getMinecraft()，只有 private 字段 mc；用单例代替
        return Minecraft.getMinecraft();
    }

    // ================================================================================================
    // ① 镜片渲染（①②③ 的电平说明见类 javadoc 表格）
    // ================================================================================================

    /**
     * 1.7.10 对应上游 {@code renderLevel} HEAD（EntityRenderer.java:1183）。
     * 每帧开始收集镜片几何并清空上一帧残留。
     */
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void tacz$beginScopeFrame(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        ScopeLensRenderer.beginFrame();
    }

    /**
     * <b>有意保持注释、不启用</b>：{@code renderHand} HEAD 版的镜片绘制（上游 shader 路径挂点）。
     * 1.7.10 无 shader 路径；且 {@code ScopeLensRenderer.render()} 不清理 {@code LENSES}
     * （ScopeLensRenderer.java:131），与 {@link #tacz$drawVanillaScopeLenses} 同时启用会双绘 +
     * 多一次全屏 copyScreen。将来若要启用，必须先给 render() 加「本帧已画」幂等保护。
     */
    //
    // @Inject(method = "renderHand", at = @At("HEAD"))
    // private void tacz$drawShaderScopeLenses(float partialTicks, int pass, CallbackInfo ci) {
    // ScopeLensRenderer.render();
    // }

    /**
     * 1.7.10 对应上游 {@code renderItemInHand} 里 {@code ScreenEffectRenderer} 之后绘制原版（无 shader）镜片。
     *
     * <p>
     * 选 {@code EntityRenderer.java:805} 的 {@code renderOverlays} INVOKE 之前：此时手持物已画完（:797）、
     * 屏幕特效还没画 ⇒ 镜片在手之上、特效之下，与上游一致。该点位于
     * {@code thirdPersonView == 0 && !sleeping} 分支内（:803），第三人称不渲染镜片。
     */
    @Inject(
        method = "renderHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderOverlays(F)V"))
    private void tacz$drawVanillaScopeLenses(float partialTicks, int pass, CallbackInfo ci) {
        ScopeLensRenderer.render();
    }

    // ================================================================================================
    // ② 受伤视角摇晃接管
    // ================================================================================================

    /**
     * 1.7.10 对应上游 {@code bobHurt} HEAD 注入。
     *
     * <p>
     * 差异：1.7.10 没有 {@code Camera}，受伤摇晃全部是 {@code hurtCameraEffect} 里直接的
     * {@code GL11.glRotatef}，所以只能「取消整个方法」。
     *
     * <p>
     * 顺序同上游：① 相机实体为活着的本地玩家时先试 {@code GunHurtBobTweak.onHurtBobTweak}（上游 bobHurt
     * 第一段），命中（被枪击后 500ms 内）就把 PoseStack 旋转经 {@link PoseStack#applyToModelView()} 灌进
     * 固定管线并 cancel；② 否则按 {@link #switchRenderType} 记录的 pass 派发
     * {@code RenderItemInHandBobEvent.BobHurt} / {@code RenderLevelBobEvent.BobHurt}，被 cancel 则取消原版。
     */
    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    public void onBobHurt(float partialTicks, CallbackInfo ci) {
        EntityLivingBase cameraEntity = Minecraft.getMinecraft().renderViewEntity;
        if (cameraEntity instanceof EntityPlayerSP player && player.getHealth() > 0.0F) {
            PoseStack poseStack = new PoseStack();
            if (GunHurtBobTweak.onHurtBobTweak(player, poseStack, partialTicks)) {
                // 上游在这里是直接 mulPose 到相机矩阵；1.7.10 没有矩阵栈，等价做法是叠到 GL_MODELVIEW
                poseStack.applyToModelView();
                ci.cancel();
                return;
            }
        }
        if (tacz$postBobHurt()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean tacz$postBobHurt() {
        if (!this.tacz$useFovSetting) {
            return MinecraftForge.EVENT_BUS.post(new RenderItemInHandBobEvent.BobHurt());
        }
        return MinecraftForge.EVENT_BUS.post(new RenderLevelBobEvent.BobHurt());
    }

    /**
     * 上游对 {@code bobHurt} 里第 3 个 {@code Axis.rotationDegrees}（受伤倾斜 {@code -f * 14.0F}）做
     * {@code @ModifyArg}。1.7.10 对应 {@code hurtCameraEffect}（EntityRenderer.java:507-528）里第 3 个
     * {@code GL11.glRotatef}（:525）：第 1 个是死亡旋转（:516）、第 2 个是受击方向 yaw（:524）、
     * 第 4 个是 yaw 回正（:526），所以 {@code ordinal = 2} 精确命中倾斜角。
     *
     * <p>
     * 平台差异：{@code isFirstPerson()} → {@code thirdPersonView == 0}；{@code getCameraEntity()} →
     * {@code renderViewEntity}；{@code isDeadOrDying()} → {@code getHealth() <= 0}；
     * {@code getMainHandItem()} → {@code getHeldItem()}（空手为 null）；{@code getHurtDir()} →
     * {@code attackedAtYaw}（同为「度」）。
     *
     * <p>
     * <b>{@code @At(remap = false)} 是必需的</b>：{@code GL11} 属 LWJGL2，不在混淆表里（{@code mcp-srg.srg}
     * 只覆盖 Minecraft/Forge），Mixin AP 默认会对 {@code @At#target} 做映射查找，查不到就报
     * {@code Unable to locate method mapping for @At(INVOKE.<target>) 'Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V'}。
     * 运行期匹配的仍是同一条 {@code Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V}，注入语义不变。
     */
    @ModifyArg(
        method = "hurtCameraEffect",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2, remap = false),
        index = 0)
    private float tacz$scaleHeldGunHurtTilt(float angle) {
        Minecraft minecraft = this.tacz$minecraft();
        if (minecraft.gameSettings.thirdPersonView == 0 && minecraft.renderViewEntity instanceof EntityPlayerSP player
            && player.getHealth() > 0.0F) {
            ItemStack held = player.getHeldItem();
            if (held != null && held.getItem() instanceof AbstractGunItem) {
                // 正面和背面受击以俯仰为主，额外减半；侧面保持配置倍率，斜向平滑过渡。
                float frontBackWeight = Math.abs(Mth.sin(player.attackedAtYaw * Mth.DEG_TO_RAD));
                float directionMultiplier = 1.0F - 0.5F * frontBackWeight;
                return angle * RenderConfig.GUN_HURT_BOB_MULTIPLIER.get()
                    .floatValue() * directionMultiplier;
            }
        }
        return angle;
    }

    // ================================================================================================
    // ③ 走路视角摇晃接管
    // ================================================================================================

    /** 1.7.10 对应上游 {@code bobView} HEAD 注入；{@code setupViewBobbing} 见 EntityRenderer.java:533。 */
    @Inject(method = "setupViewBobbing", at = @At("HEAD"), cancellable = true)
    public void onBobView(float partialTicks, CallbackInfo ci) {
        boolean cancel;
        if (!this.tacz$useFovSetting) {
            cancel = MinecraftForge.EVENT_BUS.post(new RenderItemInHandBobEvent.BobView());
        } else {
            cancel = MinecraftForge.EVENT_BUS.post(new RenderLevelBobEvent.BobView());
        }
        if (cancel) {
            ci.cancel();
        }
    }

    // ================================================================================================
    // ④ Level / Hand 渲染阶段判定
    // ================================================================================================

    /**
     * 按 {@code getFOVModifier} 的 {@code useFovSetting} 判断当前在渲染 Level 还是 HandWithItem。
     * 不对 {@code renderItemInHand} 直接 mixin 的理由同上游：装了 OptiFine 后该方法被大幅修改。
     *
     * <p>
     * <b>⚠ 必须是 {@code CallbackInfoReturnable<Float>}</b>：目标 {@code getFOVModifier(float, boolean)}
     * （EntityRenderer.java:473）返回 {@code float}，用 {@code CallbackInfo} 会报
     * {@code InvalidInjectionException: ... CallbackInfoReturnable is required!}。
     * 本方法只记录参数、不调用 {@code setReturnValue}。
     */
    @Inject(method = "getFOVModifier", at = @At("HEAD"))
    public void switchRenderType(float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        this.tacz$useFovSetting = useFovSetting;
    }

    // ================================================================================================
    // ⑤ 手部投影 fov 覆盖（= 上游 ViewportEvent.ComputeFov 的「手部 fov」那一路）
    // ================================================================================================

    /**
     * 1.7.10 对应上游 {@code CameraSetupEvent#applyGunModelFovModifying}
     * （{@code usedConfiguredFov() == false} 那一路，即<b>手部 pass</b>的 fov）。
     *
     * <p>
     * <b>为什么必须用 Mixin</b>：1.7.10 的手部投影硬编码在 {@code EntityRenderer#renderHand}
     * （{@code EntityRenderer.java:770} 的 {@code gluPerspective(getFOVModifier(p, false), ...)}），而
     * {@code getFOVModifier(p, false)} 恒为 70.0F（:482）；Forge 的 {@code FOVUpdateEvent} 只在世界路径触发
     * ⇒ 没有任何事件能改手部投影。
     *
     * <p>
     * <b>为什么只改 {@code !useFovSetting}</b>：{@code getFOVModifier} 只有两个调用点 ——
     * {@code setupCameraTransform}（:673，true，世界）与 {@code renderHand}（:770，false，手部）；世界那条
     * 已由 CameraSetupEvent 走 {@code FOVUpdateEvent} 处理，两边都改会双重缩放。
     *
     * <p>
     * 数值取 {@code CameraSetupEvent#ITEM_MODEL_FOV}，整值替换，与上游 {@code event.setFOV(...)} 语义一致
     * （水下/死亡对手部 fov 的微调同上游一样被覆盖）。
     */
    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    public void tacz$overrideHandFov(float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        // 1.20 ViewportEvent.ComputeFov(usedConfiguredFov() == false)：手部投影 fov。
        if (!useFovSetting) {
            cir.setReturnValue(CameraSetupEvent.ITEM_MODEL_FOV);
        }
    }

    // ================================================================================================
    // ⑦ 相机 roll（补 PORT_STATUS 的 D1 缺口：上游 ViewportEvent.ComputeCameraAngles#setRoll）
    // ================================================================================================

    /**
     * 1.7.10 对应上游 {@code CameraSetupEvent#applyCameraRoll / ComputeCameraAngles} 的 roll 通道。
     *
     * <p>
     * <b>为什么必须 Mixin</b>：1.7.10 Forge 只有 {@code EntityViewRenderEvent} 三个子类
     * （FogColors/RenderFog/FogDensity），<b>没有相机角度事件</b>；roll 在
     * {@code EntityRenderer#orientCamera(float)} 里只由原版自己写（{@code prevCamRoll}/{@code camRoll}），
     * 模组无法参与。
     *
     * <p>
     * 插入点：{@code orientCamera} 的 RETURN ⇒ 落在 setupCameraTransform 的 hurtCameraEffect(690) →
     * setupViewBobbing(694) → 传送门扭曲 → orientCamera(715) 之后，即「view-bob 之后、世界绘制之前」；
     * {@code glRotatef(roll, 0, 0, 1)} 对应上游 {@code Axis.ZP.rotationDegrees(roll)}。
     *
     * <p>
     * 数值复用上游同款通道 {@code CameraStateCache}（1.20 由 {@code ComputeCameraAngles} 写入）：写入端是
     * {@link CameraSetupEvent#applyLevelCameraAnimation}（每帧算完 roll 后 {@code setCameraRoll}，含无动画时
     * 清零），消费者是本注入 + {@code MuzzleParticleManager}/{@code GunItemRendererWrapper}。
     *
     * <p>
     * 不影响手部 pass：{@code renderHand} 自己 glLoadIdentity 后只调
     * {@code hurtCameraEffect}/{@code setupViewBobbing}（:778-792），不调 {@code orientCamera}。
     */
    @Inject(method = "orientCamera", at = @At("RETURN"))
    public void tacz$applyCameraRoll(float partialTicks, CallbackInfo ci) {
        float roll = CameraStateCache.getCameraRollDegrees();
        if (roll != 0.0F) {
            GL11.glRotatef(roll, 0.0F, 0.0F, 1.0F);
        }
    }

    // ================================================================================================
    // ⑧ 每帧抓纯视图矩阵（第三人称枪口世界坐标用）
    // ================================================================================================

    /**
     * 在 {@code setupCameraTransform} RETURN 抓「纯视图矩阵」。
     *
     * <p>
     * 依据：{@code setupCameraTransform} 以 {@code glLoadIdentity()} 开头（{@code EntityRenderer.java:683}），
     * 之后只叠加相机效果（{@code hurtCameraEffect}:690 / {@code setupViewBobbing}:694 / 传送门扭曲 /
     * {@code orientCamera}:715，含第三人称后拉 :629 与本类 ⑦ 的 roll）⇒ RETURN 处的 {@code GL_MODELVIEW}
     * <b>只含相机</b>；世界几何与实体都是随后才 push/pop 各自的 MODEL 变换。
     *
     * <p>
     * 消费者：{@code GunItemRendererWrapper#captureThirdPersonMuzzle}。它需要「相机相对实体帧」，而 1.7.10 的
     * 实体/手臂帧在 {@code GL_MODELVIEW} 里、TACZ 的 {@code PoseStack} 是新建空栈，只能靠
     * {@code MODEL = VIEW⁻¹ · GL_MODELVIEW} 还原（见 {@link RenderHelperCompat#viewMatrixInverse()}）。
     * 上游 1.20 不需要这一步：它的 PoseStack 本身就含「相机旋转 × 相机相对实体帧」。
     */
    @Inject(method = "setupCameraTransform", at = @At("RETURN"))
    public void tacz$captureViewMatrix(float partialTicks, int renderPass, CallbackInfo ci) {
        RenderHelperCompat.captureViewMatrix();
    }

}
