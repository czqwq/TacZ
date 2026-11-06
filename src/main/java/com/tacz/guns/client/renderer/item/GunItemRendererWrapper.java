package com.tacz.guns.client.renderer.item;

import static com.tacz.guns.client.particle.ThirdPersonMuzzleParticleManager.currentEntity;
import static com.tacz.guns.client.particle.ThirdPersonMuzzleParticleManager.onMuzzleRendered;
import static com.tacz.guns.mcshim.ItemDisplayContext.*;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.ClientTickHelper;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.particle.MuzzleParticleManager;
import com.tacz.guns.client.renderer.other.CameraAngles;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.InteractionHand;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;
import com.tacz.guns.util.RenderDistance;
import com.tacz.guns.util.math.MathUtil;

/**
 * 负责主要的枪械动画模型渲染。额外的效果见 {@link com.tacz.guns.client.event.FirstPersonRenderGunEvent}
 */
public class GunItemRendererWrapper extends AnimateGeoItemRenderer<BedrockGunModel, GunAnimationStateContext> {

    private static final SlotModel SLOT_GUN_MODEL = new SlotModel();
    private static BedrockGunModel lastModel = null;
    public static final Vector3f muzzleRenderOffset = new Vector3f();

    public GunItemRendererWrapper() {
        super();
    }

    @Override
    public GunAnimationStateContext initContext(ItemStack stack, EntityPlayer player, float partialTick) {
        GunAnimationStateContext context = new GunAnimationStateContext();
        this.updateContext(context, stack, player, partialTick);
        return context;
    }

    @Override
    public void updateContext(GunAnimationStateContext context, ItemStack stack, EntityPlayer player,
        float partialTick) {
        context.setPartialTicks(partialTick);
        context.setCurrentGunItem(stack);
    }

    @Override
    public void tryInit(ItemStack stack, EntityPlayer player, float partialTick) {
        super.tryInit(stack, player, partialTick);
    }

    @Override
    public void tryExit(ItemStack stack, long putAwayTime) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.processContextIfExist(context -> {
            context.setPutAwayTime(putAwayTime / 1000F);
            context.setCurrentGunItem(stack);
        });
        if (stateMachine.isInitialized()) {
            stateMachine.trigger(GunAnimationConstant.INPUT_PUT_AWAY);
            // KeepingItemRenderer.getRenderer().keep(stack, putAwayTime);
            stateMachine.exit();
            stateMachine.setExitingTime(putAwayTime + 50);
        }
    }

    @Override
    public long getPutAwayTime(ItemStack stack) {
        if (stack.getItem() instanceof IGun iGun) {
            return TimelessAPI.getCommonGunIndex(iGun.getGunId(stack))
                .map(
                    index -> (long) (index.getGunData()
                        .getPutAwayTime() * 1000L))
                .orElse(0L);
        }
        return 0;
    }

    @Nullable
    @Override
    public LuaAnimationStateMachine<GunAnimationStateContext> getStateMachine(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack)
            .map(GunDisplayInstance::getAnimationStateMachine)
            .orElse(null);
    }

    @Override
    public BedrockGunModel getModel(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack)
            .map(GunDisplayInstance::getGunModel)
            .orElse(null);
    }

    @Override
    public ResourceLocation getTextureLocation(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack)
            .map(GunDisplayInstance::getModelTexture)
            .orElse(null);
    }

    @Override
    public void applyLevelCameraAnimation(CameraAngles event, ItemStack stack, EntityPlayerSP player) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        Optional.ofNullable(getModel(stack))
            .ifPresent(model -> {
                if (lastModel != model) {
                    // 切换枪械模型的时候清理一下摄像机动画数据，以避免上一次播放到一半的摄像机动画影响观感。
                    model.cleanCameraAnimationTransform();
                    lastModel = model;
                }
                IClientPlayerGunOperator clientPlayerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
                float partialTicks = ClientTickHelper.getPartialTick();
                float aimingProgress = clientPlayerGunOperator.getClientAimingProgress(partialTicks);
                float zoom = iGun.getAimingZoom(stack);
                float multiplier = 1 - aimingProgress + aimingProgress / (float) Math.sqrt(zoom);
                this.applyLevelCameraAnimation(event, stack, multiplier);
            });
    }

    @Override
    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, EntityPlayerSP player) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        Optional.ofNullable(getModel(stack))
            .ifPresent(model -> {
                PoseStack poseStack = event.getPoseStack();
                IClientPlayerGunOperator clientPlayerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
                float partialTicks = ClientTickHelper.getPartialTick();
                float aimingProgress = clientPlayerGunOperator.getClientAimingProgress(partialTicks);
                float zoom = iGun.getAimingZoom(stack);
                float multiplier = 1 - aimingProgress + aimingProgress / (float) Math.sqrt(zoom);
                Quaternionf quaternion = MathUtil
                    .multiplyQuaternion(model.getCameraAnimationObject().rotationQuaternion, multiplier);
                poseStack.mulPose(quaternion);
                // 截至目前，摄像机动画数据已消费完毕。是否有更好的清理动画数据的方法？
                model.cleanCameraAnimationTransform();
            });
    }

    @Override
    public void renderFirstPerson(EntityPlayerSP player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack,
        MultiBufferSource bufferSource, int light, float partialTick) {
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }

        TimelessAPI.getGunDisplay(stack)
            .ifPresent(display -> {
                BedrockGunModel gunModel = display.getGunModel();
                var animationStateMachine = display.getAnimationStateMachine();
                if (gunModel == null) {
                    return;
                }

                // 在渲染之前，先更新动画，让动画数据写入模型
                List<ParticleEffectData> pendingParticles = null;
                if (animationStateMachine != null) {
                    animationStateMachine
                        .processContextIfExist(context -> { updateContext(context, stack, player, partialTick); });
                    animationStateMachine.update();
                    // 消费本帧动画 particle_effects 关键帧触发的粒子请求
                    pendingParticles = animationStateMachine.getAnimationController()
                        .consumePendingParticles();
                }

                poseStack.pushPose();
                // 逆转原版施加在手上的延滞效果，改为写入模型动画数据中。
                // 1.7.10 有上游 `Entity#xBobO/xBob`、`yBobO/yBob` 的逐值等价物
                // `EntityPlayerSP#prevRenderArmPitch/renderArmPitch`、`prevRenderArmYaw/renderArmYaw`
                // （`EntityPlayerSP.java:70-73`，一阶滞后系数 0.5，与 1.20 `LocalPlayer#serverAiStep` 的
                // `xBob += (getXRot()-xBob)*0.5F` 相同）。取 0 会让 xRot/yRot 变成**绝对** pitch/yaw，
                // 被下面 ×0.1/×0.05/tanh 放大成随朝向变化的固定偏移。
                float xRotOffset = Mth.lerp(partialTick, player.prevRenderArmPitch, player.renderArmPitch);
                float yRotOffset = Mth.lerp(partialTick, player.prevRenderArmYaw, player.renderArmYaw);
                // 视图朝向取**不插值**的当前值：1.20 `LocalPlayer#getViewXRot/getViewYRot`
                // （LocalPlayer.java:190-196）对本地玩家直接返回 getXRot()/getYRot()，只有 xBob 那对延滞量插值。
                float xRot = player.rotationPitch - xRotOffset;
                float yRot = player.rotationYaw - yRotOffset;
                // 补回 1.7.10 手部 pass 被整体跳过的那对原版延滞旋转，使下面的 -0.1 抵消项仍然成立。
                applyVanillaFirstPersonHandLag(poseStack, xRot, yRot);
                poseStack.mulPose(Axis.XP.rotationDegrees(xRot * -0.1F));
                poseStack.mulPose(Axis.YP.rotationDegrees(yRot * -0.1F));
                BedrockPart rootNode = gunModel.getRootNode();
                if (rootNode != null) {
                    xRot = (float) Math.tanh(xRot / 25) * 25;
                    yRot = (float) Math.tanh(yRot / 25) * 25;
                    rootNode.offsetX += yRot * 0.1F / 16F / 3F;
                    rootNode.offsetY += -xRot * 0.1F / 16F / 3F;
                    rootNode.additionalQuaternion.mul(Axis.XP.rotationDegrees(xRot * 0.05F));
                    rootNode.additionalQuaternion.mul(Axis.YP.rotationDegrees(yRot * 0.05F));
                }
                // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)
                poseStack.translate(0, 1.5f, 0);
                // 基岩版模型是上下颠倒的，需要翻转过来。
                poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
                // 应用持枪姿态变换，如第一人称摄像机定位
                FirstPersonRenderGunEvent.applyFirstPersonGunTransform(player, stack, poseStack, gunModel, partialTick);

                InteractionHand particleHand = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND;
                gunModel.setParticleWindowRenderer(new IFunctionalRenderer() {

                    @Override
                    public void render(PoseStack poseStack1, VertexConsumer vertexBuffer1,
                        ItemDisplayContext transformType1, int light1, int overlay1) {}

                    @Override
                    public void render(PoseStack poseStack1, VertexConsumer vertexBuffer1,
                        ItemDisplayContext transformType1, int light1, int overlay1, MultiBufferSource particleBuffer) {
                        Matrix4f basePose = MuzzleParticleManager.getBasePose(particleHand);
                        if (basePose == null) {
                            return;
                        }
                        poseStack1.pushPose();
                        poseStack1.last()
                            .pose()
                            .set(basePose);
                        // 枪口照明只作用于模型；烟雾仍读取原始环境光，火光粒子自身保持全亮。
                        FirstPersonRenderHandler
                            .renderParticlesNow(particleHand, poseStack1, particleBuffer, light, partialTick);
                        poseStack1.popPose();
                    }
                });

                // 开启第一人称弹壳和火焰渲染
                MuzzleFlashRender.isSelf = true;
                ShellRender.isSelf = true;
                // 如果正在打开改装界面，则取消手臂渲染
                boolean renderHand = gunModel.getRenderHand();
                // 整段必须 try/finally。
                // 这三个状态里有两个是**全局静态字段**（YSM 也会写它们），一个是模型上的渲染开关：
                // · MuzzleFlashRender.isSelf / ShellRender.isSelf 泄漏 ⇒ 之后第三人称渲染**别人**的枪
                // 时也会画本机的火光/弹壳（它们只在 !isSelf 时 return）；
                // · gunModel.setRenderHand(false) 泄漏 ⇒ 再次打开改装界面后手臂**永久消失**；
                // · poseStack 少一次 popPose ⇒ 内层矩阵残留（下次渲染错位）。
                // gunModel.render 会走到 Lua 状态机回调 / IFunctionalRenderer / 粒子系统，
                // 任何一处抛异常都会跳过原来的顺序复位（r2 对 ItemPreviewRenderContext.alpha
                // 修过同一类问题）。
                try {
                    if (RefitTransform.getOpeningProgress() != 0) {
                        gunModel.setRenderHand(false);
                    }
                    InteractionHand hand = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? InteractionHand.OFF_HAND
                        : InteractionHand.MAIN_HAND;
                    MuzzleParticleManager.spawnAndBind(
                        FirstPersonRenderHandler.getParticleSystem(),
                        poseStack,
                        gunModel,
                        hand,
                        pendingParticles);
                    // 调用枪械模型渲染
                    RenderType renderType = display.enablesTransparency()
                        ? RenderType.entityTranslucent(display.getModelTexture())
                        : RenderType.entityCutout(display.getModelTexture());
                    int modelLight = MuzzleParticleManager.getFirstPersonModelLight(stack, light);
                    gunModel
                        .render(poseStack, stack, ctx, renderType, modelLight, OverlayTexture.NO_OVERLAY, bufferSource);
                    // 缓存枪口位置，为第一人称曳光弹渲染作准备
                    cacheMuzzlePosition(poseStack, gunModel);
                    // 创建并绑定第一人称枪口 snowstorm 粒子（火光/烟雾）
                } finally {
                    // 恢复手臂渲染
                    gunModel.setRenderHand(renderHand);
                    // 渲染完成后，将动画数据从模型中清除，不对其他视角下的模型渲染产生影响
                    poseStack.popPose();
                    gunModel.cleanAnimationTransform();
                    // 关闭第一人称弹壳和火焰渲染
                    MuzzleFlashRender.isSelf = false;
                    ShellRender.isSelf = false;
                }
            });
    }

    /**
     * 第一人称粒子由本渲染器在枪体渲染的模板窗口内自行渲染（让粒子被镜内剔除），
     * SBM 因此跳过事件末尾的自动粒子渲染，避免双重绘制。
     */
    @Override
    public boolean renderParticlesInModelWindow() {
        return true;
    }

    private static void cacheMuzzlePosition(PoseStack poseStack, BedrockGunModel gunModel) {
        if (gunModel.getMuzzleFlashPosPath() != null) {
            // 计算出枪口相对于摄像机中心的坐标
            poseStack.pushPose();
            for (BedrockPart bedrockPart : gunModel.getMuzzleFlashPosPath()) {
                bedrockPart.translateAndRotateAndScale(poseStack);
            }
            Matrix4f pose = poseStack.last()
                .pose();
            double itemRenderFov = CameraSetupEvent.ITEM_MODEL_FOV_DYNAMICS.get();
            double levelRenderFov = CameraSetupEvent.WORLD_FOV_DYNAMICS.get();
            poseStack.popPose();
            // 缓存转换后的偏移坐标。手部与世界的投影 FOV 不同，这里与枪口粒子使用同一套补偿：
            // 横向偏移 (x, y) 乘 tan(worldFov/2)/tan(modelFov/2)，让曳光弹起点落在模型 FOV 下枪口的
            // 屏幕位置上，而深度 z 保持真实，不再把起点搬到更近/更远的深度上。
            double itemTan = Math.tan(itemRenderFov / 2 * Math.PI / 180);
            double levelTan = Math.tan(levelRenderFov / 2 * Math.PI / 180);
            float lateral = itemTan > 1.0e-6 ? (float) (levelTan / itemTan) : 1.0f;
            muzzleRenderOffset.set(pose.m30() * lateral, pose.m31() * lateral, pose.m32());
        }
    }

    /**
     * SBM 第一人称粒子系统在渲染枪体前调用：捕获当前 pass 的基准矩阵，供粒子绑定变换使用。
     */
    @Override
    public void updateParticleEmitterTransforms(FirstPersonParticleSystem system, PoseStack poseStack,
        InteractionHand hand) {
        MuzzleParticleManager.captureBasePose(
            hand,
            poseStack.last()
                .pose());
    }

    @Override
    public void renderByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType,
        @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        if (!(stack.getItem() instanceof IGun)) {
            return;
        }
        poseStack.pushPose();

        TimelessAPI.getGunDisplay(stack)
            .ifPresentOrElse(gunIndex -> {
                // 第一人称就不渲染了，交给别的地方
                if (transformType == FIRST_PERSON_LEFT_HAND || transformType == FIRST_PERSON_RIGHT_HAND) {
                    return;
                }
                // 第三人称副手也不渲染了
                if (transformType == THIRD_PERSON_LEFT_HAND) {
                    return;
                }
                // GUI 特殊渲染
                if (transformType == GUI) {
                    renderSlotTexture(poseStack, pBuffer, pPackedLight, pPackedOverlay, gunIndex.getSlotTexture());
                    return;
                }

                // vbo
                boolean outsideHighPolyDistance = !RenderDistance.inRenderHighPolyModelDistance(poseStack);
                StaticGunRenderResult vbo = ExternalGunItemVboRenderer.tryRender(
                    stack,
                    transformType,
                    outsideHighPolyDistance,
                    poseStack,
                    pBuffer,
                    pPackedLight,
                    pPackedOverlay);

                if (vbo.status() != StaticGunRenderResult.Status.UNSUPPORTED) {
                    return;
                }

                // 回退
                BedrockGunModel gunModel;
                ResourceLocation gunTexture;
                Pair<BedrockGunModel, ResourceLocation> lodModel = gunIndex.getLodModel();
                boolean useLod = lodModel != null && outsideHighPolyDistance;
                if (!useLod) {
                    gunModel = gunIndex.getGunModel();
                    gunTexture = gunIndex.getModelTexture();
                } else {
                    gunModel = lodModel.getLeft();
                    gunTexture = lodModel.getRight();
                }
                if (gunModel == null) {
                    renderSlotTexture(poseStack, pBuffer, pPackedLight, pPackedOverlay, gunIndex.getSlotTexture());
                    return;
                }
                GunItemTransforms.apply(
                    transformType,
                    gunIndex.getTransform()
                        .getScale(),
                    gunModel,
                    poseStack);
                // 渲染枪械模型
                float alpha = ItemPreviewRenderContext.alpha();
                RenderType renderType = ItemPreviewRenderContext.isPreview() ? RenderType.entityTranslucent(gunTexture)
                    : RenderType.entityCutout(gunTexture);

                gunModel.render(
                    poseStack,
                    stack,
                    transformType,
                    renderType,
                    pPackedLight,
                    pPackedOverlay,
                    1.0F,
                    1.0F,
                    1.0F,
                    alpha,
                    pBuffer);

                // 第三人称持枪渲染：捕获枪口世界坐标，触发第三人称枪口粒子
                if (transformType == THIRD_PERSON_RIGHT_HAND) {
                    captureThirdPersonMuzzle(poseStack, gunModel);
                }
            }, () -> {
                // 没有这个 gunID，渲染个错误材质提醒别人
                renderSlotTexture(
                    poseStack,
                    pBuffer,
                    pPackedLight,
                    pPackedOverlay,
                    InternalAssetLoader.ENTITY_EMPTY_TEXTURE);
            });

        poseStack.popPose();
    }

    // 捕获实际的世界位置
    public static void captureThirdPersonMuzzle(PoseStack poseStack, BedrockGunModel gunModel) {
        if (currentEntity == null) return;
        List<BedrockPart> muzzlePath = gunModel.getMuzzleFlashPosPath();
        if (muzzlePath == null) return;
        poseStack.pushPose();
        for (BedrockPart part : muzzlePath) {
            part.translateAndRotateAndScale(poseStack);
        }
        captureThirdPersonMuzzle(
            poseStack.last()
                .pose());
        poseStack.popPose();
    }

    /**
     * 把「物品局部空间里的枪口变换」换算成<b>世界空间</b>并交给第三人称枪口粒子。
     *
     * <p>
     * 上游 1.20（{@code GunItemRendererWrapper.java:386-414}）的 {@code pose} 来自 BEWLR 的 PoseStack，
     * 里面已含「相机旋转 × 相机相对实体帧」：{@code EntityRenderDispatcher#render}（1.20 :148-150）
     * 先 push 实体相对相机的位移/朝向，{@code LivingEntityRenderer} 叠 {@code Ry(180-yaw)}、{@code S(-1,-1,1)}、
     * {@code T(0,-1.501,0)}，{@code ItemInHandLayer#renderArmWithItem}（1.20 :54-61）再叠手臂帧 ⇒
     * 上游用 {@code T(camPos)·R_cam⁻¹·pose} 就得到枪口世界变换。
     *
     * <p>
     * 1.7.10：实体帧/手臂帧/纸片帧都在 {@code GL_MODELVIEW} 里，而 TACZ 的 {@code PoseStack} 是
     * {@code AnimateGeoItemRenderer#renderItem} 新建的空栈（只含物品帧）⇒ 先用本帧视图矩阵还原：
     *
     * <pre>
     *   MODEL       = VIEW⁻¹ · GL_MODELVIEW    （VIEW 由 GameRendererMixin 在 setupCameraTransform RETURN 抓）
     *   muzzleWorld = T(RenderManager.renderPos) · MODEL · pose
     * </pre>
     *
     * {@code RenderManager.renderPos} 是 {@code renderViewEntity} 的插值位置（{@code RenderGlobal.java:459-482}），
     * 正是 MODEL 里 {@code T(entity - renderPos)} 的基准（{@code RenderManager.java:262-278}）⇒ 与上游
     * {@code + camPos} 同一种合成方式，得到绝对世界坐标。
     *
     * <p>
     * <b>不能</b>用「相机 yaw/pitch + 眼位」近似（修前做法）：第三人称相机是后拉过的
     * （{@code EntityRenderer.java:629}），且 VIEW 里还含 view-bob（:694）、hurt（:690）、roll、
     * 传送门扭曲（:697-713）—— 漏掉任一项枪口粒子就会与枪口脱钩。VBO 快速路径
     * （{@code ExternalGunEffects#renderThirdPerson}）也在同一个 GL 实体帧里调用本方法，
     * 语义一致（该路径 1.7.10 恒 UNSUPPORTED，见 {@code ExternalGunItemVboRenderer}）。
     */
    public static void captureThirdPersonMuzzle(Matrix4f pose) {
        if (currentEntity == null) return;
        Matrix4f model = RenderHelperCompat.viewMatrixInverse()
            .mul(RenderHelperCompat.currentModelViewMatrix());
        Matrix4f muzzleWorld = model.mul(pose);
        muzzleWorld.m30(muzzleWorld.m30() + (float) RenderManager.renderPosX);
        muzzleWorld.m31(muzzleWorld.m31() + (float) RenderManager.renderPosY);
        muzzleWorld.m32(muzzleWorld.m32() + (float) RenderManager.renderPosZ);
        onMuzzleRendered(currentEntity, muzzleWorld);
    }

    private static void renderSlotTexture(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
        int packedOverlay, ResourceLocation texture) {
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
        SLOT_GUN_MODEL.renderToBuffer(
            poseStack,
            buffer,
            packedLight,
            packedOverlay,
            1.0F,
            1.0F,
            1.0F,
            ItemPreviewRenderContext.alpha());
    }

}
