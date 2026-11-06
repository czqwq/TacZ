package com.tacz.guns.client.event;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.FOVUpdateEvent;

import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.CameraStateCache;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.client.ClientTickHelper;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.renderer.other.CameraAngles;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.resource.pojo.display.gun.AdsCurve;
import com.tacz.guns.client.resource.pojo.display.gun.AdsFov;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.EntityPose;
import com.tacz.guns.mcshim.EntityStateHelper;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.RecoilModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.math.Easing;
import com.tacz.guns.util.math.MathUtil;
import com.tacz.guns.util.math.SecondOrderDynamics;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 相机相关：<b>瞄准镜 FOV 缩放</b>、<b>相机后坐力</b>、<b>相机动画（状态机）</b>。
 *
 * <p>
 * 上游：tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/CameraSetupEvent.java
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)}）。
 *
 * <p>
 * 事件映射：{@code ViewportEvent.ComputeCameraAngles} <b>不存在</b>（1.7.10 的
 * {@code EntityViewRenderEvent} 只有 FogDensity/RenderFog/FogColors）⇒ 用 {@link TickEvent.RenderTickEvent} +
 * 直接改 {@code renderViewEntity} 的 {@code rotationPitch/rotationYaw}（{@link #applyCameraRecoil}、
 * {@link #applyLevelCameraAnimation}）；{@code ViewportEvent.ComputeFov}（世界）与
 * {@code ComputeFovModifierEvent} 都用 {@link FOVUpdateEvent}（{@code EntityRenderer.java:673} 的世界投影走
 * {@code getFOVModifier(partial, true)}），用 {@code priority = HIGHEST} 保证先于镜片缩放的 handler 执行；
 * {@code ComputeFov}（手部，{@code usedConfiguredFov() == false}）<b>无事件</b> ⇒ 结果放 {@link #ITEM_MODEL_FOV}，
 * 由 {@code mixin/client/GameRendererMixin} 在 {@code getFOVModifier} 的 {@code useFovSetting == false} 分支覆盖
 * 硬编码的 70°（{@code EntityRenderer.java:770}/{@code :482}）；{@code BeforeRenderHandEvent} 同名事件，由 mixin
 * 在 {@code ItemRenderer#renderItemInFirstPerson} HEAD 抛出。
 *
 * <p>
 * 其余降级点：{@code IClientItemExtensions#getCustomRenderer()} → {@code MinecraftForgeClient#getItemRenderer(
 * stack, EQUIPPED_FIRST_PERSON)} + {@code instanceof AnimateGeoItemRenderer}；越肩视角分支删除（1.7.10 无该模组，
 * {@code ShoulderSurfingCompat#isInstalled()} 恒 false），相机后坐力直接施加到玩家朝向；
 * {@code Minecraft#player} → {@code thePlayer}；{@code Options#bobView()} → {@code GameSettings#viewBobbing}；
 * {@code getAbilities().flying} → {@code capabilities.isFlying}；{@code getPose()==SWIMMING && !isSwimming()} →
 * {@code EntityStateHelper#getPose(player)}（姿势覆盖表见 {@code EntityStateHelper.ENTITY_POSE_OVERRIDES}）；
 * {@code Minecraft#getFrameTime()} → {@link ClientTickHelper#getPartialTick()}（1.7.10 {@code Minecraft.timer}
 * 是 private 且生产环境是 SRG 名）。
 *
 * <p>
 * <b>1.7.10 特有</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法} 这里改为实例方法（类名/方法名/参数/语义不变）。
 * 又因 {@code EventBus#register(Class)} 在 1.7.10 无效，本类提供 public 无参构造，由 {@code ClientProxy}
 * 反射 {@code new} 后注册到正确的总线。
 */
public class CameraSetupEvent {

    /**
     * 用于平滑 FOV 变化
     */
    public static final SecondOrderDynamics WORLD_FOV_DYNAMICS = new SecondOrderDynamics(0.5f, 1.2f, 0.5f, 0);
    public static final SecondOrderDynamics ITEM_MODEL_FOV_DYNAMICS = new SecondOrderDynamics(0.5f, 1.2f, 0.5f, 0);
    /**
     * 1.7.10 手部投影的 FOV（度）。上游是 {@code ComputeFov} 事件里「非 configured fov」的那一半；1.7.10 没有该
     * 事件，故由 {@link #applyGunModelFovModifying} 算出后放这里，供 {@code GameRendererMixin} 在
     * {@code getFOVModifier} 的手部 pass 分支覆盖 70°。
     */
    public static float ITEM_MODEL_FOV = 70.0F;
    private static PolynomialSplineFunction pitchSplineFunction;
    private static PolynomialSplineFunction yawSplineFunction;
    private static long shootTimeStamp = -1L;
    private static double xRotO = 0;
    private static double yRotO = 0;
    /**
     * 当前挂在 {@code player.rotation*} / {@code player.prevRotation*} 上的相机动画偏移（= 加上去、还没扣回的量）。
     *
     * <p>
     * 1.7.10 没有相机角度事件，能改相机的通道只有实体朝向：相机取自插值式 {@code prev + (rotation - prev) * partialTicks}
     * （{@code EntityRenderer.java:641-642}），{@code prev} 每 tick 更新一次（{@code EntityLivingBase.java:340-341}）。
     * 因此偏移必须<b>同时加到 rotation 与 prevRotation</b>：只加 rotation 时相机只看到
     * {@code 偏移 * partialTicks}，而 partialTicks 每个 tick 归零（{@code Minecraft.java:1019-1025}）
     * ⇒ 偏移每 50ms 被抹掉一次，表现为「换弹 / 拉栓时屏幕剧烈抖动」（枪包 {@code camera} 通道正是这两种动画）。
     * 同步平移后相机读到的偏移与上游 {@code ComputeCameraAngles} 完全一致。
     *
     * <p>
     * 记账必须自愈：END 无条件扣回记录的量（幂等），START 开头先扣回上一帧可能残留的量，
     * 故偏移不会跨帧累积，最坏只是本帧少一次动画。
     */
    private static float cameraAnimationAppliedYaw = 0;
    private static float cameraAnimationAppliedPitch = 0;
    private static float cameraAnimationAppliedPrevPitch = 0;
    /** 是否有偏移正挂在 rotation 上（{@link #revertCameraAnimationOffset()} 的幂等哨兵）。 */
    private static boolean cameraAnimationOffsetPresent = false;

    /**
     * 上游 {@code applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles)}。
     *
     * <p>
     * 1.7.10 没有相机角度事件，改为在 {@code RenderTickEvent.START} 把动画角度叠加到
     * {@code renderViewEntity} 的朝向，并在同一帧的 {@code END} 还原 —— 这样得到的就是
     * 「只影响本帧相机、不污染实体状态」的语义（等价上游对事件的 setYaw/setPitch）。
     * <b>{@code roll}</b> 走上游同款通道
     * {@link CameraStateCache#setCameraRoll(float)}（1.7.10 的 {@code EntityRenderer} 没有可注入的
     * 相机滚转容器，由 {@code mixin/client/GameRendererMixin} 在 {@code orientCamera} RETURN 处
     * {@code glRotatef} 消费）；同一通道还被 {@code MuzzleParticleManager}（第一人称粒子→世界变换）
     * 与 {@code GunItemRendererWrapper}（手持物 {@code rotateZ} 补偿）读取，因此每帧必须写。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void applyLevelCameraAnimation(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // 无条件扣回记录中的偏移（幂等）。见 cameraAnimationAppliedYaw 的字段注释：
            // 新写法不依赖「START↔END 严格配对」，因此 END 漏执行也不会让偏移留在 rotation 上。
            revertCameraAnimationOffset();
            return;
        }
        // START 开头**先自愈**：若上一帧的偏移还挂着（END 漏执行 / START 跑了两次只 END 一次 /
        // START 与 END 跨帧错配），这里先无条件扣掉，保证任何时刻每个偏移最多只存在一帧。
        // ⇒ 这正是「换弹/切枪时第一/第三人称都剧烈抖动」的根因修复：
        // 旧写法依赖配对，一旦错配，偏移逐帧累积在 player.rotationYaw/Pitch 上，
        // 而第三人称相机也读同一对字段 ⇒ 两个视角一起抖。
        revertCameraAnimationOffset();
        // 手部 FOV 必须**无条件**每帧算一次（r4 修复）。
        // 上游 {@code applyGunModelFovModifying(ViewportEvent.ComputeFov)} 是一个**独立**的
        // ComputeFov 订阅者，与 bobView 开关无关；本移植因为 1.7.10 没有手部 FOV 事件，把它塞在
        // 这个方法末尾，于是继承了上面的 `viewBobbing` 早退与 `player == null` 早退 ⇒
        // **玩家关掉「视角摇晃」后 ITEM_MODEL_FOV 永远停在初值 70°**，开镜时手部投影不缩放
        // （枪模型大小/眼睛到镜片的距离全错）。这里把它提到所有闸门之前。
        EntityPlayerSP fovPlayer = Minecraft.getMinecraft().thePlayer;
        if (fovPlayer != null) {
            applyGunModelFovModifying(fovPlayer, ClientTickHelper.getPartialTick());
        }
        if (!Minecraft.getMinecraft().gameSettings.viewBobbing) {
            // 上游在 bobView 关闭时直接 return，相机动画（含 roll）整帧不生效 → 通道写 0
            CameraStateCache.setCameraRoll(0.0F);
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            CameraStateCache.setCameraRoll(0.0F);
            return;
        }
        ItemStack stack = KeepingItemRenderer.getRenderer()
            .getCurrentItem();
        // 尝试调用物品的自定义相机动画
        IItemRenderer renderer = getItemRenderer(stack);
        if (renderer instanceof AnimateGeoItemRenderer<?, ?>geoRenderer) {
            float originYaw = player.rotationYaw;
            float originPitch = player.rotationPitch;
            float originPrevYaw = player.prevRotationYaw;
            float originPrevPitch = player.prevRotationPitch;
            CameraAngles angles = new CameraAngles(originYaw, originPitch, 0);
            geoRenderer.applyLevelCameraAnimation(angles, stack, player);
            float deltaYaw = angles.getYaw() - originYaw;
            float deltaPitch = Mth.clamp(angles.getPitch(), -90.0F, 90.0F) - originPitch;
            if (deltaYaw != 0.0F || deltaPitch != 0.0F) {
                // rotation 与 prevRotation 必须同步平移：相机 = prev + (rotation - prev) * partialTicks
                // （EntityRenderer.java:641-642），只改 rotation 会让偏移每 tick 被 partialTicks 归零一次。
                float newPitch = Mth.clamp(originPitch + deltaPitch, -90.0F, 90.0F);
                float newPrevPitch = Mth.clamp(originPrevPitch + deltaPitch, -90.0F, 90.0F);
                player.rotationYaw = originYaw + deltaYaw;
                player.prevRotationYaw = originPrevYaw + deltaYaw;
                player.rotationPitch = newPitch;
                player.prevRotationPitch = newPrevPitch;
                // 只记录真正加上去的量（含夹紧后的实际值），revert 时逐一减掉
                cameraAnimationAppliedYaw = deltaYaw;
                cameraAnimationAppliedPitch = newPitch - originPitch;
                cameraAnimationAppliedPrevPitch = newPrevPitch - originPrevPitch;
                cameraAnimationOffsetPresent = true;
            }
            // 相机 roll：1.7.10 的 EntityRenderer 没有相机滚转通道，roll 通过上游同款通道
            // CameraStateCache 交给 mixin/client/GameRendererMixin（orientCamera RETURN 处 glRotatef）。
            // 该值同时被 MuzzleParticleManager（第一人称粒子→世界）与
            // GunItemRendererWrapper（手持物 rotateZ 补偿）消费，因此这里必须每帧写。
            CameraStateCache.setCameraRoll(angles.getRoll());
        } else {
            // 没有枪械自定义渲染器/动画时清零，避免上一帧的 roll 残留
            CameraStateCache.setCameraRoll(0.0F);
        }
    }

    /**
     * 上游 {@code applyItemInHandCameraAnimation(BeforeRenderHandEvent)}：与上游完全同源
     * （事件、委托目标、参数都一致；1.7.10 的事件由 {@code ItemInHandRendererMixin} 抛出，
     * {@code event.getPoseStack()} 会真的叠到固定管线 MODELVIEW）。
     */
    @SubscribeEvent
    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event) {
        if (!Minecraft.getMinecraft().gameSettings.viewBobbing) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        ItemStack stack = KeepingItemRenderer.getRenderer()
            .getCurrentItem();
        // 尝试调用物品的自定义相机动画
        IItemRenderer renderer = getItemRenderer(stack);
        if (renderer instanceof AnimateGeoItemRenderer<?, ?>geoRenderer) {
            geoRenderer.applyItemInHandCameraAnimation(event, stack, player);
        }
    }

    /**
     * 上游 {@code applyScopeMagnification(ViewportEvent.ComputeFov)}，出场条件 {@code usedConfiguredFov()}
     * 在 1.7.10 恒为 true（{@code FOVUpdateEvent} 只会由世界投影触发）。
     *
     * <p>
     * <b>量纲换算</b>：1.7.10 的 {@code FOVUpdateEvent.fov/newfov} 是 fov <b>倍率</b>
     * （{@code EntityPlayerSP#getFOVMultiplier}），最终角度 = {@code gameSettings.fovSetting * 倍率}
     * （{@code EntityRenderer#getFOVModifier(partial, true)}）。上游拿到的是角度，所以这里先乘
     * fovSetting 换回角度、算完再除回去。
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void applyScopeMagnification(FOVUpdateEvent event) {
        float fovSetting = Minecraft.getMinecraft().gameSettings.fovSetting;
        if (fovSetting <= 0.0F) {
            return;
        }
        EntityPlayerSP player = event.entity;
        if (player == null) {
            return;
        }
        // event.newfov 已被更高优先级的 handler（applyGunModelFovModifying 的 MovementFov 分支）改写时以此为准
        float fovMultiplier = event.newfov;
        float currentFovDegrees = fovMultiplier * fovSetting;
        ItemStack stack = KeepingItemRenderer.getRenderer()
            .getCurrentItem();
        if (stack == null || !(stack.getItem() instanceof IGun iGun)) {
            float fov = WORLD_FOV_DYNAMICS.update(currentFovDegrees);
            event.newfov = fov / fovSetting;
            return;
        }
        float zoom = iGun.getAimingZoom(stack);
        AdsFov.Channel curve = TimelessAPI.getGunDisplay(stack)
            .map(GunDisplayInstance::getAdsFov)
            .map(AdsFov::getWorld)
            .orElse(null);
        float fovProgress = getFovProgress(player, ClientTickHelper.getPartialTick(), curve);
        float targetFov = (float) MathUtil.magnificationToFov(1 + (zoom - 1) * fovProgress, currentFovDegrees);
        float smoothedFov = WORLD_FOV_DYNAMICS.update(targetFov);
        float resultFov = curve == null || curve.isSmooth() ? smoothedFov : targetFov;
        event.newfov = resultFov / fovSetting;
    }

    /**
     * 上游 {@code applyGunModelFovModifying(ViewportEvent.ComputeFov)}（手部物品的 FOV）。
     *
     * <p>
     * <b>降级</b>（{@code // TODO(L2)}）：1.7.10 没有「手部 FOV」独立事件，手部投影是
     * {@code EntityRenderer#renderHand} 里写死的 {@code getFOVModifier(partial,false) = 70°}
     * （{@code EntityRenderer.java:770}）。本方法保留上游<b>全部计算</b>（瞄具 fov / zoom_number /
     * ads_path / 二阶平滑），结果写入 {@link #ITEM_MODEL_FOV}，由
     * {@code mixin/client/GameRendererMixin} 在 {@code getFOVModifier} 的手部 pass 分支取用并覆盖 70°。
     * 这样开镜时手部模型的 FOV 与上游一致。
     */
    private static void applyGunModelFovModifying(EntityPlayerSP player, float partialTick) {
        ItemStack stack = KeepingItemRenderer.getRenderer()
            .getCurrentItem();
        float baseFov = 70.0F;
        if (stack == null || !(stack.getItem() instanceof IGun iGun)) {
            ITEM_MODEL_FOV = ITEM_MODEL_FOV_DYNAMICS.update(baseFov);
            return;
        }
        ResourceLocation scopeItemId = iGun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (scopeItemId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeItemId = iGun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        }
        Optional<GunDisplayInstance> gunDisplay = TimelessAPI.getGunDisplay(stack);
        float modelFov = gunDisplay.map(GunDisplayInstance::getModelFov)
            .orElse(baseFov);
        NBTTagCompound scopeTag = iGun.getAttachmentTag(stack, AttachmentType.SCOPE);
        int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(scopeTag);
        Optional<ClientAttachmentVariantIndex> scopeVariant = TimelessAPI
            .getClientAttachmentVariantIndex(iGun.getAttachment(stack, AttachmentType.SCOPE));
        ResourceLocation resolvedScopeItemId = scopeItemId;
        // 常驻状态使用枪械本身 model_fov；开镜状态优先使用配件 fov，若无则使用枪械本身 zoom_model_fov。
        float modifiedFov = scopeVariant.map(index -> {
            float[] viewsFov = index.getViewsFov();
            return viewsFov[zoomNumber % viewsFov.length];
        })
            .orElseGet(
                () -> TimelessAPI.getClientAttachmentIndex(resolvedScopeItemId)
                    .map(index -> {
                        float[] viewsFov = index.getViewsFov();
                        return viewsFov[zoomNumber % viewsFov.length];
                    })
                    .orElse(
                        gunDisplay.map(GunDisplayInstance::getZoomModelFov)
                            .orElse(modelFov)));
        AdsFov.Channel curve = gunDisplay.map(GunDisplayInstance::getAdsFov)
            .map(AdsFov::getModel)
            .orElse(null);
        float fovProgress = getFovProgress(player, partialTick, curve);
        float targetFov = Mth.lerp(fovProgress, modelFov, modifiedFov);
        float smoothedFov = ITEM_MODEL_FOV_DYNAMICS.update(targetFov);
        ITEM_MODEL_FOV = curve == null || curve.isSmooth() ? smoothedFov : targetFov;
    }

    private static float getFovProgress(EntityLivingBase entity, float partialTick, AdsCurve curve) {
        float progress = entity instanceof EntityPlayerSP localPlayer
            ? IClientPlayerGunOperator.fromLocalPlayer(localPlayer)
                .getClientAimingProgress(partialTick)
            : IGunOperator.fromLivingEntity(entity)
                .getSynAimingProgress();
        if (curve == null) {
            return (float) Easing.easeInExpo(progress);
        }
        return curve.evaluate(progress);
    }

    @SubscribeEvent
    public void initialCameraRecoil(GunFireEvent event) {
        if (event.getLogicalSide()
            .isClient()) {
            EntityLivingBase shooter = event.getShooter();
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (!shooter.equals(player)) {
                return;
            }
            ItemStack mainHandItem = player.getHeldItem();
            if (mainHandItem == null || !(mainHandItem.getItem() instanceof IGun iGun)) {
                return;
            }
            AttachmentCacheProperty cacheProperty = IGunOperator.fromLivingEntity(player)
                .getCacheProperty();
            if (cacheProperty == null) {
                return;
            }
            ResourceLocation gunId = iGun.getGunId(mainHandItem);
            Optional<ClientGunIndex> gunIndexOptional = TimelessAPI.getClientGunIndex(gunId);
            if (gunIndexOptional.isEmpty()) {
                return;
            }
            ClientGunIndex gunIndex = gunIndexOptional.get();
            GunData gunData = gunIndex.getGunData();
            // 获取所有配件对摄像机后坐力的修改
            ParameterizedCachePair<Float, Float> attachmentRecoilModifier = cacheProperty.getCache(RecoilModifier.ID);
            IClientPlayerGunOperator clientPlayerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
            float partialTicks = ClientTickHelper.getPartialTick();
            float aimingProgress = clientPlayerGunOperator.getClientAimingProgress(partialTicks);
            float zoom = iGun.getAimingZoom(mainHandItem);
            float aimingRecoilModifier = 1 - aimingProgress + aimingProgress / (float) Math.min(Math.sqrt(zoom), 1.5);
            // 如果是趴下，那么后坐力按 data 设计减少（默认为降低一半）
            // 1.7.10: isSwimming() 无对应 → 用 !isInWater() 表达「非游泳」；爬行的姿势覆盖见 EntityStateHelper
            if (!player.isInWater() && EntityStateHelper.getPose(player) == EntityPose.SWIMMING) {
                aimingRecoilModifier = aimingRecoilModifier * gunData.getCrawlRecoilMultiplier();
            }
            pitchSplineFunction = gunData.getRecoil()
                .genPitchSplineFunction(
                    (float) attachmentRecoilModifier.left()
                        .eval(aimingRecoilModifier));
            yawSplineFunction = gunData.getRecoil()
                .genYawSplineFunction(
                    (float) attachmentRecoilModifier.right()
                        .eval(aimingRecoilModifier));
            shootTimeStamp = GunTime.nowMillis();
            xRotO = 0;
            yRotO = 0;
        }
    }

    /**
     * 上游 {@code applyCameraRecoil(ViewportEvent.ComputeCameraAngles)}。
     *
     * <p>
     * 1.7.10 无相机角度事件 → 在 {@code RenderTickEvent.START} 直接改实体的
     * {@code rotationPitch/rotationYaw}（与上游 {@code player.setXRot/setYRot} 分支逐行对应；
     * 1.7.10 的相机朝向就是实体朝向，因此效果等价）。
     * 越肩视角分支不可达（无该模组），见类注释。
     */
    @SubscribeEvent
    public void applyCameraRecoil(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        long timeTotal = GunTime.nowMillis() - shootTimeStamp;
        if (shootTimeStamp >= 0 && timeTotal >= 0
            && pitchSplineFunction != null
            && pitchSplineFunction.isValidPoint(timeTotal)) {
            double value = pitchSplineFunction.value(timeTotal);
            player.rotationPitch -= (float) (value - xRotO);
            xRotO = value;
        }
        if (shootTimeStamp >= 0 && timeTotal >= 0
            && yawSplineFunction != null
            && yawSplineFunction.isValidPoint(timeTotal)) {
            double value = yawSplineFunction.value(timeTotal);
            player.rotationYaw -= (float) (value - yRotO);
            yRotO = value;
        }
    }

    /**
     * 把挂在 {@code player.rotation*} / {@code prevRotation*} 上的相机动画偏移扣回去（幂等）。
     *
     * <p>
     * 语义：逐一减掉「当初真正加上去的量」（含夹紧后的实际值），然后清空记录。
     * 因为记录的就是实际增量，所以不管 START/END 怎么错配、期间鼠标又改了多少，扣回去都不会累积。
     */
    private static void revertCameraAnimationOffset() {
        if (!cameraAnimationOffsetPresent) {
            return;
        }
        float yaw = cameraAnimationAppliedYaw;
        float pitch = cameraAnimationAppliedPitch;
        float prevPitch = cameraAnimationAppliedPrevPitch;
        cameraAnimationOffsetPresent = false;
        cameraAnimationAppliedYaw = 0.0F;
        cameraAnimationAppliedPitch = 0.0F;
        cameraAnimationAppliedPrevPitch = 0.0F;
        EntityPlayerSP player = Minecraft.getMinecraft() == null ? null : Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        player.rotationYaw -= yaw;
        player.prevRotationYaw -= yaw;
        player.rotationPitch = Mth.clamp(player.rotationPitch - pitch, -90.0F, 90.0F);
        player.prevRotationPitch = Mth.clamp(player.prevRotationPitch - prevPitch, -90.0F, 90.0F);
    }

    public static void resetTimestamp() {
        shootTimeStamp = -1;
        pitchSplineFunction = null;
        yawSplineFunction = null;
        xRotO = 0;
        yRotO = 0;
        cameraAnimationOffsetPresent = false;
        cameraAnimationAppliedYaw = 0.0F;
        cameraAnimationAppliedPitch = 0.0F;
        cameraAnimationAppliedPrevPitch = 0.0F;
        // 相机 roll 通道清零（断线/切世界时不能把上一局的 roll 带过来）
        CameraStateCache.setCameraRoll(0.0F);
    }

    /**
     * 上游 {@code onComputeMovementFov(ComputeFovModifierEvent)}：持枪时去掉「移动速度属性」带来的
     * FOV 变化（{@code RenderConfig.DISABLE_MOVEMENT_ATTRIBUTE_FOV}）。
     *
     * <p>
     * 1.7.10 的 {@code FOVUpdateEvent.newfov} 与 1.20 的 fov modifier <b>同量纲</b>（都是倍率，
     * 见 {@code EntityPlayerSP#getFOVMultiplier}），因此计算式逐行相同。
     * 用 {@code priority = HIGHEST} 复刻「ComputeFovModifierEvent 早于 ComputeFov」的顺序。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onComputeMovementFov(FOVUpdateEvent event) {
        if (!RenderConfig.DISABLE_MOVEMENT_ATTRIBUTE_FOV.get()) return;
        EntityPlayerSP player = event.entity;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getHeldItem();
        if (mainHandItem != null && mainHandItem.getItem() instanceof AbstractGunItem) {
            float f = 1.0f;
            if (player.capabilities.isFlying) {
                f *= 1.1F;
            }
            event.newfov = player.isSprinting() ? 1.15f * f : f;
        }
    }

    /** 1.7.10 的「物品自定义渲染器」查询（替代 {@code IClientItemExtensions#getCustomRenderer()}）。 */
    private static IItemRenderer getItemRenderer(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON);
    }
}
