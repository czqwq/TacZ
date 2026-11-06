// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1: v1/client/handler/FirstPersonRenderHandler.java
// 1.7.10 移植差异见类 javadoc 与 TODO(L2)。

package com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.port.Camera;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.PausedClientAnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.event.SwapItemWithOffHand;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPGeoItemRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.firstperson.FirstPersonParticleSystem;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.render.CameraStateCache;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.client.event.RenderItemInHandBobEvent;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.particle.MuzzleParticleManager;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.client.renderer.scope.ScopeLensRenderer;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.InteractionHand;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * 第一人称手持物品渲染 + 动画状态机。
 *
 * <p>
 * TODO(L2)（1.7.10 平台差异）：
 * <ul>
 * <li>`@Mod.EventBusSubscriber` 不存在，改为静态块里注册到两个 EventBus：tick/网络事件走
 * `FMLCommonHandler.instance().bus()`，渲染事件走 `MinecraftForge.EVENT_BUS`。</li>
 * <li>1.7.10 无副手，`getOffhandItem()` 恒为 null；副手状态机保留但永不激活。</li>
 * <li>`ItemStack.EMPTY -> null`（用 null 表示空栈），`isEmpty()` -> `isEmpty(stack)`。</li>
 * <li>`RenderHandEvent` 只给 `partialTicks`/`context`/`renderPass`，无 `PoseStack`/`MultiBufferSource`/
 * `packedLight`/hand，`onRenderHand` 自行构造并推到 GL。</li>
 * <li>无 `IClientItemExtensions`：改为「物品自身 implements IFPGeoItemRenderer」或 `registerRenderer` 显式注册。</li>
 * </ul>
 */
public class FirstPersonRenderHandler {

    private static final Map<Item, IFPGeoItemRenderer> RENDERERS = new HashMap<>();

    /** 1.7.10 的「自定义物品渲染器」注册点，替代 1.20 的 `IClientItemExtensions`（TODO(L2)，见类注释）。 */
    public static void registerRenderer(Item item, IFPGeoItemRenderer renderer) {
        RENDERERS.put(item, renderer);
    }

    /** 触发类初始化（完成事件总线注册）。1.7.10 无 `@Mod.EventBusSubscriber`，需显式调用一次。 */
    public static void init() {}

    static {
        // TODO(L2): 替代 1.20 的 @Mod.EventBusSubscriber(Dist.CLIENT)（见类注释）。
        FirstPersonRenderHandler self = new FirstPersonRenderHandler();
        FMLCommonHandler.instance()
            .bus()
            .register(self);
        MinecraftForge.EVENT_BUS.register(self);
    }
    private static final AnimationClock CLOCK = AnimationClocks.client();

    /** 全局第一人称粒子系统。所有自定义物品共享此实例，统一管理粒子生命周期和渲染。 */
    private static final FirstPersonParticleSystem PARTICLE_SYSTEM = new FirstPersonParticleSystem();
    private static long lastParticleTickNanos = 0L;

    /**
     * 单手第一人称渲染状态。主副手各持一份，互不干扰。
     */
    private static final class HandRenderState {

        /** 该手当前真实物品（切换检测基准）。 */
        ItemStack realItem = null;
        /** 当前活跃 instance。 */
        IFPAnimationInstance activeInstance = null;
        /** 收枪过渡中的旧 instance。 */
        IFPAnimationInstance previousInstance = null;
        /** 过渡完成后要切入的目标物品。 */
        ItemStack pendingTarget = null;
        /** 是否处于收枪过渡。 */
        boolean transitioning = false;
        /** 过渡目标是否为自定义物品（驱动 vanilla height 目标值）。 */
        boolean nextIsCustom = false;
        /** 当前活跃 instance 的渲染变体键（{@link IFPGeoItemRenderer#getRenderVariantKey}）。 */
        Object variantKey = null;
        long switchStartTime = 0L;
        long currentSheatheDuration = 0L;

        void reset() {
            realItem = null;
            activeInstance = null;
            previousInstance = null;
            pendingTarget = null;
            transitioning = false;
            nextIsCustom = false;
            variantKey = null;
            switchStartTime = 0L;
            currentSheatheDuration = 0L;
        }
    }

    private static final HandRenderState MAIN_STATE = new HandRenderState();
    private static final HandRenderState OFF_STATE = new HandRenderState();

    /** 主手选中槽（仅主手有槽位概念）。 */
    private static int realSelectedSlot = -1;

    // 上一刻「主手是否霸占副手视野」的镜像，翻转时重建副手实例。
    private static boolean mainOccupiedOffhand = false;

    private static boolean forceHandSwapFlag = false;
    private static boolean wasFirstPerson = true;

    private static HandRenderState stateFor(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? OFF_STATE : MAIN_STATE;
    }

    private static InteractionHand handForState(HandRenderState state) {
        return state == OFF_STATE ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    // ================================================================================================
    // 事件处理器：**必须是实例方法**。FML 1.7.10 的 ASMEventHandler 用 INVOKEVIRTUAL 回调，
    // `public static` 处理器派发时抛 IncompatibleClassChangeError，而 EventBus#post 会捕获它
    // 只打一条 error 日志 ⇒ 表现为「注册成功但处理器永不执行」。
    // ================================================================================================

    @SubscribeEvent
    public void onPlayerLoggedOut(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        reset();
    }

    public static void reset() {
        realSelectedSlot = -1;
        forceHandSwapFlag = false;
        mainOccupiedOffhand = false;
        wasFirstPerson = true;
        MAIN_STATE.reset();
        OFF_STATE.reset();
        PARTICLE_SYSTEM.clear();
        lastParticleTickNanos = 0L;
        // MuzzleParticleManager 持有静态表（待创建请求/已绑定发射器/基准矩阵），不清理会让重连后的
        // 首帧凭空放一次枪口火光。reset() 幂等。
        MuzzleParticleManager.reset();
        // 时钟归零（上游 ClientAnimationClockTicker#onLoggingOut 语义），避免 logicalNanos 跨存档累积。
        PausedClientAnimationClock.getInstance()
            .reset();
    }

    @SubscribeEvent
    public void onRenderHand(SwapItemWithOffHand event) {
        forceHandSwapFlag = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !CLOCK.shouldTick()) {
            return;
        }

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }

        boolean isFirstPerson = Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
        if (wasFirstPerson && !isFirstPerson) {
            // 离开第一人称后无 RenderHandEvent 更新定位器，保留 emitter 会以最后一帧变换持续发射。
            PARTICLE_SYSTEM.discardEmitters();
        }
        wasFirstPerson = isFirstPerson;

        int newSlot = player.inventory.currentItem;
        ItemStack newMain = player.getHeldItem();
        // TODO(L2): 1.7.10 没有副手，getOffhandItem() 无对应物，恒为 null（副手状态机保留但永不激活）。
        ItemStack newOff = null;

        // 交换主副手（F 键）：两手物品已对调，强制两手都「先收枪再切枪」。
        boolean swap = forceHandSwapFlag;
        forceHandSwapFlag = false;

        // 主手：选中槽变化 + 物品变化 + 渲染变体键变化检测（槽位是主手独有概念）。
        boolean mainSlotChanged = newSlot != realSelectedSlot;
        boolean mainItemChanged = !isSameItemStacks(MAIN_STATE.realItem, newMain);
        boolean mainVariantChanged = variantKeyChanged(MAIN_STATE, newMain, InteractionHand.MAIN_HAND);

        realSelectedSlot = newSlot;
        // 存拷贝而非引用：物品被取走时槽位 ItemStack 的 count 会被原地改成 0，存引用会让
        // isSameItemStacks 因「同一对象」短路而漏判「持有物消失」。
        if (mainItemChanged) {
            // 1.7.10 空手 = null（1.20 是 ItemStack.EMPTY），直接 copy() 会 NPE。
            MAIN_STATE.realItem = isEmpty(newMain) ? null : newMain.copy();
        }
        // swap 与切格子是明确的外部切换事件，即便值层面无差异也需强制重建。
        boolean mainForce = swap || mainSlotChanged;
        if (mainForce || mainItemChanged || mainVariantChanged) {
            beginSwitch(MAIN_STATE, newMain, mainForce);
        }

        // 仅在检测到物品变化时刷新副手拷贝（同主手，避免取走物品时引用同变漏判）。
        boolean offRealChanged = !isSameItemStacks(OFF_STATE.realItem, newOff);
        if (offRealChanged) {
            // 同主手：1.7.10 的 newOff 恒为 null（无副手），若不判空则这条一旦被触发就 NPE。
            OFF_STATE.realItem = isEmpty(newOff) ? null : newOff.copy();
        }

        // 必须在副手切换处理之前计算：霸占期间副手不可见，否则会静默掏枪、解除后凭空「弹出」。
        boolean occupiedNow = mainHandBlocksOffhand();
        boolean occupyFlip = occupiedNow != mainOccupiedOffhand;
        mainOccupiedOffhand = occupiedNow;

        if (occupiedNow) {
            // 主手霸占副手视野：丢弃副手实例（不可见，收枪动画无意义），且不掏新枪。
            if (occupyFlip) {
                discardOffhandInstance();
                // 副手被遮挡后没有可用的模型渲染基准，不能保留附着型本地粒子。
                PARTICLE_SYSTEM.discardEmitters(InteractionHand.OFF_HAND);
            }
        } else if (occupyFlip) {
            // 霸占刚解除：副手按当前物品全新掏枪（可见的掏枪动画）。
            if (!isEmpty(newOff)) {
                beginSwitch(OFF_STATE, newOff, true);
            }
        } else {
            // 未被霸占的常规路径：副手物品变化 / 渲染变体键变化 / swap 时切换。
            boolean offVariantChanged = variantKeyChanged(OFF_STATE, newOff, InteractionHand.OFF_HAND);
            if (swap || offRealChanged || offVariantChanged) {
                beginSwitch(OFF_STATE, newOff, swap);
            }
        }

        if (MAIN_STATE.activeInstance != null) {
            MAIN_STATE.activeInstance.updateItem(newMain);
        }
        if (OFF_STATE.activeInstance != null) {
            OFF_STATE.activeInstance.updateItem(newOff);
        }

        tickStates(MAIN_STATE, InteractionHand.MAIN_HAND);
        tickStates(OFF_STATE, InteractionHand.OFF_HAND);
    }

    /**
     * 检测某手「渲染变体键」是否变化（物品同一但呈现形态需切换）。
     */
    private static boolean variantKeyChanged(HandRenderState state, ItemStack heldItem, InteractionHand hand) {
        if (state.activeInstance == null || isEmpty(heldItem)) {
            return false;
        }
        Object newKey = getRenderVariantKey(heldItem, hand);
        return !Objects.equals(newKey, state.variantKey);
    }

    private static Object getRenderVariantKey(ItemStack stack, InteractionHand hand) {
        return getRenderer(stack).map(r -> r.getRenderVariantKey(stack, hand))
            .orElse(null);
    }

    /**
     * 切换某只手的物品 / 渲染变体：旧 instance 为自定义物品时进入收枪过渡，否则直接切到新 instance。
     *
     * @param force 是否穿透幂等短路
     */
    private static void beginSwitch(HandRenderState state, ItemStack newStack, boolean force) {
        InteractionHand hand = handForState(state);
        Object newVariantKey = getRenderVariantKey(newStack, hand);
        state.pendingTarget = newStack;
        state.nextIsCustom = isCustomItem(newStack);

        if (state.transitioning) {
            return;
        }

        // 幂等保护：同一持有物 + 同一渲染变体时不重复切换，避免连续掏两次。
        if (!force && !isEmpty(newStack)
            && state.activeInstance != null
            && isSameItemStacks(state.activeInstance.currentItem(), newStack)
            && Objects.equals(state.variantKey, newVariantKey)) {
            return;
        }

        boolean oldIsCustom = state.activeInstance != null;

        state.previousInstance = state.activeInstance;
        state.activeInstance = createInstance(newStack, hand);
        state.variantKey = newVariantKey;

        if (oldIsCustom) {
            state.transitioning = true;
            state.switchStartTime = CLOCK.nowMillis();
            state.currentSheatheDuration = calculateSheatheDuration(state.previousInstance.currentItem());
            state.previousInstance.triggerPutAway();
            // 零时长收枪没有后续动画帧可供 tickStates 收尾，必须立即完成切换。
            if (state.currentSheatheDuration <= 0L) {
                finishTransition(state, hand);
            }
        } else {
            state.transitioning = false;
        }
    }

    private static void tickStates(HandRenderState state, InteractionHand hand) {
        if (state.transitioning && getSheatheProgress(state) >= 1.0f) {
            finishTransition(state, hand);
        }
    }

    /** 结束收枪过渡；零时长收枪也走这里，不依赖下一帧状态 tick。 */
    private static void finishTransition(HandRenderState state, InteractionHand hand) {
        // previousInstance 不再渲染，旧 emitter 失去模型空间基准，必须在此废弃。
        PARTICLE_SYSTEM.discardEmitters(hand);
        state.transitioning = false;
        // 用真实物品（而非可能过期的 pendingTarget）重建 instance。
        state.activeInstance = createInstance(state.realItem, hand);
        state.variantKey = getRenderVariantKey(state.realItem, hand);
        state.previousInstance = null;
    }

    /** 直接丢弃副手渲染实例（不播收枪过渡）：被遮挡时收枪动画不可见。 */
    private static void discardOffhandInstance() {
        OFF_STATE.activeInstance = null;
        OFF_STATE.previousInstance = null;
        OFF_STATE.pendingTarget = null;
        OFF_STATE.transitioning = false;
        OFF_STATE.variantKey = null;
    }

    @SubscribeEvent
    public void tickAnimation(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        // ============================================================================================
        // **推进客户端动画时钟**：上游 ClientAnimationClockTicker（v1/client/event/）在 vendor 时
        // 整体丢失，而 logicalNanos 只在 PausedClientAnimationClock#update() 里推进 ⇒ nowMillis/nowNanos
        // 恒为 0 ⇒ 收枪过渡永不完成（枪模型残留）、粒子生命曲线冻结。
        // 必须每帧 START 无条件推进一次，且在 shouldTick() 早退与任何 nowNanos() 读取之前。
        // ============================================================================================
        PausedClientAnimationClock.getInstance()
            .update();
        // 诊断帧号推进（仅 DEBUG_RENDER 打开时有效，发布版不执行）。
        if (RenderHelperCompat.DEBUG_RENDER) {
            RenderHelperCompat.beginDebugFrame();
        }
        if (!CLOCK.shouldTick()) {
            return;
        }

        // 统一 tick 所有第一人称粒子（不依赖具体物品）
        long now = CLOCK.nowNanos();
        float dt = lastParticleTickNanos == 0L ? 0f : (now - lastParticleTickNanos) / 1_000_000_000f;
        dt = Math.min(dt, 0.1f);
        lastParticleTickNanos = now;
        PARTICLE_SYSTEM.tick(dt);

        tickHandAnimation(MAIN_STATE, InteractionHand.MAIN_HAND, event.renderTickTime);
        tickHandAnimation(OFF_STATE, InteractionHand.OFF_HAND, event.renderTickTime);
    }

    private static void tickHandAnimation(HandRenderState state, InteractionHand hand, float renderTickTime) {
        IFPAnimationInstance ani = state.transitioning ? state.previousInstance : state.activeInstance;
        if (ani == null || !canActuallyRenderInHand(ani, hand)) {
            return;
        }

        ani.triggerDraw();
        ani.tick(renderTickTime);
    }

    private static boolean canActuallyRenderInHand(IFPAnimationInstance ani, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND && mainHandBlocksOffhand()) {
            return false;
        }

        ItemStack stack = ani.currentItem();
        if (isEmpty(stack)) {
            return false;
        }

        return getRenderer(stack).map(renderer -> renderer.canRenderInHand(stack, hand))
            .orElse(false);
    }

    /**
     * 第一人称手持物品渲染入口。
     *
     * <p>
     * TODO(L2): 1.7.10 的 RenderHandEvent 只给 partialTicks/context/renderPass，无 PoseStack/
     * MultiBufferSource/packedLight，且只有主手一次 pass ⇒ hand 固定 MAIN_HAND、PoseStack 与
     * MultiBufferSource 由本方法新建，光照取玩家所在位置。
     *
     * <p>
     * <b>GL 矩阵不变式（scope capture 依赖它）</b>：本方法把 GL_MODELVIEW 归位后，在整个枪械渲染 pass 内
     * **恒为单位矩阵**（除各自成对的临时 {@code pushToGL/popToGL}），GL_PROJECTION = 手部投影
     * （ITEM_MODEL_FOV）。相机类效果（受伤摇晃、BeforeRenderHandEvent 的相机动画）一律乘进
     * {@code poseStack}，不得写 GL_MODELVIEW —— 上游 1.20 手部 pass 的 ModelViewMat 恒为单位矩阵
     * （{@code GameRenderer#renderItemInHand:998-1003} 先 {@code posestack.setIdentity()} 再
     * {@code bobHurt(poseStack)}，{@code ItemInHandRenderer} 全程不 applyModelViewMatrix）。
     * {@code ScopeLensRenderer.capture} 按 spec §8.2 断言 B 读取这两个矩阵；一旦 ModelViewMat ≠ I，
     * 目镜圆盘的投影 bounds 会算到相机后面（behindCamera=true）⇒ 镜片不登记、开镜黑屏。
     */
    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return;
        }
        // 只在第一人称画手持物：vanilla 的守卫在 renderHand 内（EntityRenderer.java:794），
        // 而 RenderHandEvent 是无条件派发的（同文件 :1434）。
        if (mc.gameSettings.thirdPersonView != 0) {
            return;
        }
        // 对齐 vanilla 的另外两个守卫（EntityRenderer.java:794）：hideGUI（F1）与 isPlayerSleeping。
        // 这里 return 而不 cancel，让原版 renderHand 照常画 overlay / 恢复状态。
        if (mc.gameSettings.hideGUI || (mc.renderViewEntity != null && mc.renderViewEntity.isPlayerSleeping())) {
            return;
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;
        PoseStack poseStack = new PoseStack();
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate();
        // lightmap 取玩家所在位置的光，同 vanilla 手部 pass（ItemRenderer.java:287）：注意取脚部位置，
        // 不含 eyeHeight。写死 FULL_BRIGHT 会让枪在洞穴/夜里和正午一样亮。
        int packedLight;
        if (player.worldObj == null) {
            // 世界尚未就绪（极早期/断线瞬间）：退回满亮度，避免 NPE。
            packedLight = LightTexture.FULL_BRIGHT;
        } else {
            packedLight = player.worldObj.getLightBrightnessForSkyBlocks(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ),
                0);
        }

        // ---------------------------------------------------------------------------------------
        // 必需：把 GL_MODELVIEW 归位，并让它在**整个枪械渲染 pass 内保持单位矩阵**。
        // RenderHandEvent 在 EntityRenderer.java:1434 派发，此刻 GL_MODELVIEW 仍是「世界+相机」视图矩阵；
        // 而 PoseStack 已把顶点烘成相机空间坐标，不归位会被视图矩阵再乘一次 ⇒ 枪体画到世界原点并随视角自转。
        // glLoadIdentity 取自 vanilla 手部 pass（EntityRenderer.java:778-786）；viewBobbing 已由
        // FirstPersonRenderGunEvent 主动取消，装备升降由枪械切枪动画接管。
        //
        // 为什么必须保持**单位**矩阵（而不只是「相机空间」）：上游第一人称手部 pass 的 ModelViewMat 恒为单位矩阵
        // （1.20 GameRenderer#renderItemInHand:998-1003 先 posestack.setIdentity() 再 bobHurt/bobView，
        // ItemInHandRenderer 全程不 applyModelViewMatrix）⇒ 顶点坐标系 == 相机空间，模型侧任何矩阵都只走 PoseStack。
        // 本工程 ScopeLensRenderer.capture（spec §8.2 断言 B：proj.m11 = 1/tan(ITEM_MODEL_FOV/2)、
        // ModelViewMat == I）按此前提读 GL_PROJECTION/GL_MODELVIEW；若把受伤摇晃、相机动画等直接乘到
        // GL_MODELVIEW 上，capture 读到的 ModelViewMat 不再与模型顶点坐标系一致，目镜圆盘的投影 bounds 会
        // 落到相机后面（behindCamera=true）⇒ 镜片不登记、黑镜。**所以相机类效果一律写进下面这个 poseStack，
        // 不得写 GL_MODELVIEW**（临时 pushToGL/popFromGL 由各自 finally 成对还原则不在此限）。
        // ---------------------------------------------------------------------------------------
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        boolean lightmapOn = false;
        boolean projectionPushed = false;
        try {
            // -----------------------------------------------------------------------------------
            // 必需：建立「手部投影矩阵」。
            // cancel RenderHandEvent 会让 EntityRenderer#renderHand 整段不执行，其 :755-770 的
            // glLoadIdentity + gluPerspective(getFOVModifier(p,false), ...) 从未建立 ⇒ 此刻 GL_PROJECTION
            // 还是 setupCameraTransform:673 用世界 FOV 建的那份（开镜时枪体被放大 ~zoom 倍）。
            // 照抄 vanilla :755-770：push 投影 → LoadIdentity → gluPerspective(ITEM_MODEL_FOV, ...)，
            // finally 里成对 pop；只 push/pop 不改永久状态，非枪械路径不受影响。
            // -----------------------------------------------------------------------------------
            projectionPushed = pushHandProjection();

            // 原版手部 pass 会先清深度（EntityRenderer.java:1436）；cancel 后需在此补上，
            // 否则贴墙/草地时枪体被世界几何裁掉。
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            // -----------------------------------------------------------------------------------
            // 补偿：打开 lightmap 纹理单元（unit 1）。
            // vanilla 的 enableLightmap/disableLightmap（EntityRenderer.java:796/:798）都在 renderHand 内
            // ⇒ cancel 后不执行。而 Tessellator#draw 只开 unit 1 的 GL_TEXTURE_COORD_ARRAY、不开
            // GL_TEXTURE_2D，那步正是 enableLightmap 干的 ⇒ 不动它顶点 lightmap 坐标被忽略，
            // 最终颜色 = 贴图 × 顶点色 ⇒ 第一人称枪恒亮。
            // -----------------------------------------------------------------------------------
            mc.entityRenderer.enableLightmap(event.partialTicks);
            lightmapOn = true;

            // 上游在下面每条早退分支上都先 renderParticlesIfAny(event) 再 return（粒子发射器可能比 instance
            // 活得久，例如收枪过渡期间仍在冷却的烟雾）。这里逐条对齐；早退即 return，不会与事件末尾那次重复绘制。

            // 副手渲染前先看主手是否禁止副手渲染（1.7.10 只有主手 pass，但状态机保持一致）。
            if (hand == InteractionHand.OFF_HAND && mainHandBlocksOffhand()) {
                renderParticlesIfAny(hand, poseStack, bufferSource, packedLight, event.partialTicks);
                bufferSource.endBatch();
                event.setCanceled(true);
                return;
            }

            IFPAnimationInstance inst = getActiveAnimationInstance(hand);
            if (inst == null) {
                renderParticlesIfAny(hand, poseStack, bufferSource, packedLight, event.partialTicks);
                bufferSource.endBatch();
                return;
            }

            ItemStack stack = inst.currentItem();
            if (isEmpty(stack)) {
                renderParticlesIfAny(hand, poseStack, bufferSource, packedLight, event.partialTicks);
                bufferSource.endBatch();
                return;
            }

            Optional<IFPGeoItemRenderer> optRenderer = getRenderer(stack);
            if (optRenderer.isEmpty()) {
                renderParticlesIfAny(hand, poseStack, bufferSource, packedLight, event.partialTicks);
                bufferSource.endBatch();
                return;
            }

            IFPGeoItemRenderer renderer = optRenderer.get();

            // 物品自身判定能否在该手渲染（如双手长枪放副手时不在副手渲染）。
            if (!renderer.canRenderInHand(stack, hand)) {
                renderParticlesIfAny(hand, poseStack, bufferSource, packedLight, event.partialTicks);
                bufferSource.endBatch();
                event.setCanceled(true);
                return;
            }

            // 补偿 (a0)：第一人称受伤摇晃。vanilla 手部 pass 自己会调 hurtCameraEffect
            // （EntityRenderer.java:787），cancel RenderHandEvent 后整段 renderHand 跳过 ⇒ 世界晃、枪不晃。
            // 上游 1.20 在 GameRenderer#renderItemInHand:998-1003 把 bobHurt 叠在**手部 PoseStack** 上
            // （poseStack.setIdentity() → bobHurt(poseStack) → renderHandsWithItems(poseStack)），
            // 所以这里也写 poseStack，不写 GL_MODELVIEW（见 onRenderHand 顶部不变式）。
            // 只在「确定接管并 cancel」的枪械路径上调用（非枪械路径原版仍会自己跑一次 :787）。
            // 不复刻 setupViewBobbing：第一人称 viewBobbing 已由 FirstPersonRenderGunEvent 取消并交给枪械动画。
            compensateHurtCameraEffect(player, event.partialTicks, poseStack);

            ItemDisplayContext transformType = hand == InteractionHand.OFF_HAND
                ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

            // 补偿 (a)：BeforeRenderHandEvent 必须在 renderFirstPerson 之前（上游是「叠相机动画 → 再画手」）。
            // 与上游同理，事件拿到的是**同一个 poseStack**（1.20 ItemInHandRenderer#renderHandsWithItems HEAD
            // 抛事件时用的就是手部 PoseStack），因此这里把枪模用的 poseStack 直接交给订阅者。
            compensateBeforeRenderHandEvent(poseStack);

            // 更新该手粒子发射器变换（主副手各自在自己的 pass 内捕获基准、绑定枪口）。
            // 必须在「hurt + 相机动画」都写进 poseStack **之后**捕获：上游 1.20 的入口 poseStack 本身就含
            // bobHurt/bobView（GameRenderer:998-1003）与 BeforeRenderHandEvent 的相机动画（TACZ 的
            // ItemInHandRendererMixin 在 renderHandsWithItems HEAD 抛事件）⇒ 上游 basePose = 受伤·相机动画；
            // 粒子窗口渲染时把 poseStack 设回 basePose、GL_MODELVIEW 为单位矩阵，最终顶点变换与修复前
            // （basePose=单位、GL=受伤·相机动画）逐项相同。
            renderer.updateParticleEmitterTransforms(PARTICLE_SYSTEM, poseStack, hand);

            renderer.renderFirstPerson(
                player,
                stack,
                transformType,
                poseStack,
                bufferSource,
                packedLight,
                event.partialTicks);
            bufferSource.endBatch();
            event.setCanceled(true);

            // 渲染器声明在模型渲染窗口内自行渲染粒子时，跳过事件末尾的自动渲染，避免双重绘制
            if (!renderer.renderParticlesInModelWindow()) {
                renderParticlesIfAny(hand, poseStack, bufferSource, packedLight, event.partialTicks);
                bufferSource.endBatch();
            }

            // 补偿 (b)(c)：镜片 → 原版 overlay，复刻 vanilla 顺序（手 :797 → 镜片/overlay :805），
            // 且只在 cancel 路径调用（否则与 GameRendererMixin 对 renderHand 的注入重复绘制）。
            // lightmap 必须在镜片/overlay 之前关掉，与 vanilla :798 disableLightmap 时机一致。
            if (lightmapOn) {
                mc.entityRenderer.disableLightmap(event.partialTicks);
                lightmapOn = false;
            }
            compensateScopeLenses();
            compensateVanillaOverlays(event.partialTicks, poseStack);
        } finally {
            // 异常路径兜底：lightmap 必须成对关闭（EntityRenderer.java:819-824），否则 GL_TEXTURE_2D 泄漏。
            if (lightmapOn) {
                mc.entityRenderer.disableLightmap(event.partialTicks);
            }
            // 投影矩阵优先还原（投影栈比 MODELVIEW 栈更浅，先 pop 它再 pop MODELVIEW 更安全）。
            if (projectionPushed) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
        }
    }

    /**
     * 建立手部投影矩阵（vanilla EntityRenderer#renderHand:755-770 的等价物），依据见 onRenderHand。
     * FOV 取 CameraSetupEvent.ITEM_MODEL_FOV，远平面公式同 EntityRenderer.java:657。
     *
     * @return 恒为 true；调用方据此决定是否需要 pop
     */
    private static boolean pushHandProjection() {
        Minecraft mc = Minecraft.getMinecraft();
        int chunks = mc.gameSettings == null ? 0 : mc.gameSettings.renderDistanceChunks;
        // renderDistanceChunks 的初值是 -1（GameSettings.java:66），未初始化时给一个安全值。
        float farPlaneDistance = (chunks > 0 ? chunks : 12) * 16.0F;
        float aspect = mc.displayHeight <= 0 ? 1.0F : (float) mc.displayWidth / (float) mc.displayHeight;
        float fov = CameraSetupEvent.ITEM_MODEL_FOV;
        if (!(fov > 1.0F) || !Float.isFinite(fov)) {
            // 未初始化/异常值：退回 1.7.10 手部投影的默认 70°（EntityRenderer.java:482 的 70.0F）。
            fov = 70.0F;
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        Project.gluPerspective(fov, aspect, NEAR_PLANE, farPlaneDistance * 2.0F);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        return true;
    }

    /**
     * 手部 pass 的近平面（格）。
     *
     * <p>
     * vanilla 用 0.05F（EntityRenderer.java:770），但其手臂锚点在身前 0.6 格外；本模组默认枪包的
     * lefthand_pos 锚点只有 0.09 格，手方块 0.25 格深 ⇒ 会被近裁面切掉「缺面」。
     * 降到 0.01F：进入本 pass 时已 glClear(GL_DEPTH_BUFFER_BIT)，不与世界几何共享深度精度，无 z-fighting。
     * 置回 0.05F 即单点回退。
     */
    public static float NEAR_PLANE = 0.01F;

    /**
     * 补偿 (a0)：把原版手部 pass 的 hurtCameraEffect 复刻到<b>枪模 PoseStack</b> 上。
     *
     * <p>
     * <b>为什么写 PoseStack 而不是 GL_MODELVIEW</b>（r7 实机证据：受伤时 ScopeLensRenderer 读到
     * {@code modelViewIsIdentity=false} ⇒ 目镜 bounds {@code behindCamera=true} ⇒ 镜片不登记 = 黑镜）：
     * 上游 1.20 的 hurt 摇晃本来就叠在<b>手部 PoseStack</b> 上 ——
     * {@code GameRenderer#renderItemInHand:998-1003}：{@code posestack.setIdentity(); posestack.pushPose();
     * this.bobHurt(posestack, f); ... renderHandsWithItems(f, posestack, ...)}。1.7.10 的等价物就是这里
     * 传入的 {@code poseStack}（{@code renderFirstPerson} 的入参）；GL_MODELVIEW 在 pass 内必须保持单位矩阵
     * （见 {@link #onRenderHand} 顶部不变式）。
     *
     * <p>
     * 三步与修正后的 GameRendererMixin.java:266-342 同款（GL 版 {@code glRotatef} 与 {@code mulPose} 都是
     * 右乘、角度符号相同，逐句等价）：
     * <ol>
     * <li>GunHurtBobTweak.onHurtBobTweak —— 枪械伤害专用微幅晃动（GunHurtBobTweak.java:41-66，仅 500ms），
     * 命中即 return，不再叠加原版晃动。</li>
     * <li>RenderItemInHandBobEvent.BobHurt —— 上游 bobHurt 第二段。1.7.10 renderHand 用
     * getFOVModifier(p,false)（EntityRenderer.java:770），useFovSetting==false ⇒ 走手部事件。</li>
     * <li>原版 hurtCameraEffect 等价实现（EntityRenderer.java:507-528），受伤倾斜角按
     * GameRendererMixin :324-342 同款倍率缩放，与世界相机一致。</li>
     * </ol>
     *
     * <p>
     * 视觉等价的依据：修复前这些旋转乘在 GL_MODELVIEW 上（左乘最终顶点），现在按同样顺序右乘进
     * poseStack（顶点由 PoseStack 烘出、GL 为单位矩阵）⇒ 最终顶点变换完全相同。
     *
     * @param poseStack 枪模使用的 PoseStack（{@code renderFirstPerson} 的入参），本方法只在其上右乘旋转
     */
    private static void compensateHurtCameraEffect(EntityPlayerSP player, float partialTicks, PoseStack poseStack) {
        Minecraft mc = Minecraft.getMinecraft();
        // 守卫取自 GameRendererMixin#onBobHurt(:268-269)：只看「渲染视角实体是不是本地玩家」。
        // 死亡分支也必须走（原版 hurtCameraEffect 里包含死亡镜头翻滚），故这里<b>不</b>用 health 拦住整个方法。
        if (mc.renderViewEntity != player) {
            return;
        }
        // 异常兜底：把 poseStack 栈顶恢复到进入本方法时的状态（含法线矩阵），避免半套旋转把这一帧画歪。
        Matrix4f poseBackup = new Matrix4f(
            poseStack.last()
                .pose());
        Matrix3f normalBackup = new Matrix3f(
            poseStack.last()
                .normal());
        boolean alive = player.getHealth() > 0.0F;
        try {
            // ---- 第 1 步：枪械伤害专用晃动（原版 bobHurt 第一段；mixin :269-277 有 health>0 守卫） ----
            if (alive) {
                // 上游同款：GunHurtBobTweak 直接往传入的 PoseStack 上叠（不再走临时栈 + applyToModelView）。
                if (GunHurtBobTweak.onHurtBobTweak(player, poseStack, partialTicks)) {
                    return;
                }
            }
            // ---- 第 2 步：手部受伤摇晃事件（原版 bobHurt 第二段；mixin 里无守卫，无条件派发） ----
            if (MinecraftForge.EVENT_BUS.post(new RenderItemInHandBobEvent.BobHurt())) {
                return;
            }
            // ---- 第 3 步：原版 hurtCameraEffect 等价实现（EntityRenderer.java:507-528） ----
            if (!alive) {
                // 死亡镜头翻滚（:513-517）
                float deathTicks = (float) player.deathTime + partialTicks;
                poseStack.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (deathTicks + 200.0F)));
            }
            float hurtFactor = (float) player.hurtTime - partialTicks;
            if (hurtFactor >= 0.0F && player.maxHurtTime > 0) {
                // :521-522
                hurtFactor /= (float) player.maxHurtTime;
                hurtFactor = Mth.sin(hurtFactor * hurtFactor * hurtFactor * hurtFactor * (float) Math.PI);
                float attackedYaw = player.attackedAtYaw;
                // :524 / :526：受击方向的 yaw 去回正
                poseStack.mulPose(Axis.YP.rotationDegrees(-attackedYaw));
                // :525：受伤倾斜角（唯一被 mixin @ModifyArg 缩放的那一段）
                float tilt = -hurtFactor * 14.0F;
                ItemStack held = player.getHeldItem();
                if (held != null && held.getItem() instanceof AbstractGunItem) {
                    // 与 GameRendererMixin#tacz$scaleHeldGunHurtTilt(:330-340) 逐行同构：
                    // 正面/背面受击以俯仰为主，额外减半；侧面保持配置倍率，斜向平滑过渡。
                    float frontBackWeight = Math.abs(Mth.sin(attackedYaw * Mth.DEG_TO_RAD));
                    float directionMultiplier = 1.0F - 0.5F * frontBackWeight;
                    tilt *= RenderConfig.GUN_HURT_BOB_MULTIPLIER.get()
                        .floatValue() * directionMultiplier;
                }
                poseStack.mulPose(Axis.ZP.rotationDegrees(tilt));
                poseStack.mulPose(Axis.YP.rotationDegrees(attackedYaw));
            }
        } catch (Throwable t) {
            // 受伤摇晃属可选观感，任何失败都不能打断第一人称渲染；同时把 poseStack 恢复到进入前状态。
            poseStack.last()
                .pose()
                .set(poseBackup);
            poseStack.last()
                .normal()
                .set(normalBackup);
            SimpleBedrockModel.LOGGER.warn("[SBM] hurt camera effect compensation failed", t);
        }
    }

    /**
     * 补偿 (a)：补发 BeforeRenderHandEvent，并把相机动画写进<b>枪模 PoseStack</b>。
     *
     * <p>
     * 1.7.10 cancel RenderHandEvent 会跳过整个 EntityRenderer#renderHand（EntityRenderer.java:1434），
     * 于是 renderItemInFirstPerson（:797）不再执行，mixin/client/ItemInHandRendererMixin.java:113-119
     * 的注入失效 ⇒ CameraSetupEvent#applyItemInHandCameraAnimation 链路断开。
     *
     * <p>
     * 与上游同构的三步，只是第三步从 {@code applyToModelView()}（乘进 GL_MODELVIEW）改成**直接把枪模
     * PoseStack 交给订阅者**：1.20 里事件拿到的就是手部 PoseStack，订阅者（
     * {@code CameraSetupEvent#applyItemInHandCameraAnimation} → {@code AnimateGeoItemRenderer}/>
     * {@code GunItemRendererWrapper#applyItemInHandCameraAnimation}）只在上面 {@code mulPose} 一次。
     * 这样 GL_MODELVIEW 保持单位矩阵，ScopeLensRenderer.capture 读到的 ModelViewMat 与顶点坐标系一致
     * （否则受伤/相机动画帧会 {@code modelViewIsIdentity=false} ⇒ 镜片 capture 失败、黑镜）。
     *
     * @param poseStack 枪模使用的 PoseStack（{@code renderFirstPerson} 的入参），订阅者在其上右乘
     */
    private static void compensateBeforeRenderHandEvent(PoseStack poseStack) {
        Matrix4f poseBackup = new Matrix4f(
            poseStack.last()
                .pose());
        Matrix3f normalBackup = new Matrix3f(
            poseStack.last()
                .normal());
        try {
            MinecraftForge.EVENT_BUS.post(new BeforeRenderHandEvent(poseStack));
        } catch (Throwable t) {
            // 单个补偿失败不能打断第一人称渲染（相机动画是可选效果）；同时回滚订阅者可能已写入的半套变换。
            poseStack.last()
                .pose()
                .set(poseBackup);
            poseStack.last()
                .normal()
                .set(normalBackup);
            SimpleBedrockModel.LOGGER.warn("[SBM] BeforeRenderHandEvent compensation failed", t);
        }
    }

    /**
     * 补偿 (b)：画瞄具镜片。
     *
     * <p>
     * ScopeLensRenderer.render() 的唯一调用点是 GameRendererMixin.java:237-242（注入在
     * EntityRenderer#renderHand 的 renderOverlays 之前），cancel 后 renderHand 整体不执行 ⇒ 镜片永不绘制。
     * 该方法自包含 GL 状态（自己 push/loadIdentity，成对 pop），无害。
     */
    private static void compensateScopeLenses() {
        try {
            ScopeLensRenderer.render();
        } catch (Throwable t) {
            SimpleBedrockModel.LOGGER.warn("[SBM] scope lens compensation failed", t);
        }
    }

    /**
     * 补偿 (c)：画原版第一人称全屏 overlay（火焰 / 方块内 / 水下 / 传送门 / 南瓜头）。
     *
     * <p>
     * 由 ItemRenderer#renderOverlays(float)（ItemRenderer.java:559）在 EntityRenderer.java:805 调用，
     * 同在 renderHand 内部 ⇒ cancel 后丢失。只画 overlay，不重画手持物。
     *
     * <p>
     * <b>为什么这里要临时把 poseStack 乘回 GL_MODELVIEW</b>：vanilla 的 overlay 在 renderHand 内绘制，
     * 此刻 MODELVIEW 还带着 hurtCameraEffect 的旋转；本实现把相机类效果挪进了 PoseStack（见
     * {@link #compensateHurtCameraEffect}），所以这里 push 一次、把 poseStack 顶（= 受伤·相机动画）乘回
     * GL 再画 overlay、finally pop —— 视觉与 vanilla 一致，且仍是**临时 push/pop**，不破坏
     * 「pass 内 GL_MODELVIEW 恒为单位矩阵」不变式（capture 早已发生）。
     */
    private static void compensateVanillaOverlays(float partialTicks, PoseStack poseStack) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.entityRenderer == null || mc.entityRenderer.itemRenderer == null
                || mc.thePlayer == null
                || mc.theWorld == null) {
                return;
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            try {
                // 栈顶此时 = 受伤·相机动画（renderFirstPerson 内部 push/pop 已成对还原）。
                poseStack.applyToModelView();
                mc.entityRenderer.itemRenderer.renderOverlays(partialTicks);
            } finally {
                GL11.glPopMatrix();
            }
        } catch (Throwable t) {
            SimpleBedrockModel.LOGGER.warn("[SBM] vanilla first-person overlay compensation failed", t);
        }
    }

    /** 渲染当前 pass 这只手的活跃粒子（两手 poseStack 基准不同，不可跨手共用）。 */
    private static void renderParticlesIfAny(InteractionHand hand, PoseStack poseStack, MultiBufferSource bufferSource,
        int light, float partialTick) {
        renderParticlesNow(hand, poseStack, bufferSource, light, partialTick);
    }

    /** 立即渲染指定手的活跃粒子，供枪体模板窗口内调用；poseStack / bufferSource 需与该手渲染一致。 */
    public static void renderParticlesNow(InteractionHand hand, PoseStack poseStack, MultiBufferSource bufferSource,
        int light, float partialTick) {
        if (PARTICLE_SYSTEM.getParticleCount() == 0) {
            return;
        }

        // TODO(L2): 1.20 的 Minecraft#gameRenderer.getMainCamera() → port.Camera 的等价替身。
        Camera camera = Camera.getMainCamera(partialTick);
        float cameraPitchRad = (float) Math.toRadians(camera.getXRot());
        float cameraRollRad = CameraStateCache.getCameraRollRadians();
        Matrix4f cameraRotation = buildCameraRotation(camera, cameraRollRad);

        PARTICLE_SYSTEM
            .render(hand, poseStack, bufferSource, light, partialTick, cameraPitchRad, cameraRollRad, cameraRotation);
    }

    /** 用摄像机 pitch/yaw/roll 构建视图旋转矩阵：X(pitch) → Y(yaw+180) → Z(roll)，与 Minecraft 内部一致。 */
    private static Matrix4f buildCameraRotation(Camera camera, float rollRadians) {
        return new Matrix4f().rotationX((float) Math.toRadians(camera.getXRot()))
            .rotateY((float) Math.toRadians(camera.getYRot() + 180f))
            .rotateZ(rollRadians);
    }

    public static boolean shouldLockVanilla() {
        return shouldLockVanilla(InteractionHand.MAIN_HAND);
    }

    /**
     * 该手是否处于收枪过渡（需要钉住 vanilla height、屏蔽原版升降动画）。
     */
    public static boolean shouldLockVanilla(InteractionHand hand) {
        return stateFor(hand).transitioning;
    }

    public static float getTargetHeight() {
        return getTargetHeight(InteractionHand.MAIN_HAND);
    }

    public static float getTargetHeight(InteractionHand hand) {
        return stateFor(hand).nextIsCustom ? 1.0F : 0.0F;
    }

    /** 获取主手当前活跃动画 instance（过渡期返回正在收枪的旧 instance）。 */
    public static IFPAnimationInstance getActiveAnimationInstance() {
        return getActiveAnimationInstance(InteractionHand.MAIN_HAND);
    }

    /**
     * 获取指定手的当前活跃动画 instance（过渡期返回正在收枪的旧 instance）。
     */
    public static IFPAnimationInstance getActiveAnimationInstance(InteractionHand hand) {
        HandRenderState state = stateFor(hand);
        return state.transitioning ? state.previousInstance : state.activeInstance;
    }

    /**
     * 摄像机使用的当前可渲染动画 instance：主手优先，再回退到可渲染的副手。
     * 不可见的实例不参与相机动画或原版 viewBobbing 的取消。
     */
    public static IFPAnimationInstance getActiveAnimationInstanceForCamera() {
        IFPAnimationInstance main = getActiveAnimationInstance(InteractionHand.MAIN_HAND);
        if (main != null && canActuallyRenderInHand(main, InteractionHand.MAIN_HAND)) {
            return main;
        }

        IFPAnimationInstance off = getActiveAnimationInstance(InteractionHand.OFF_HAND);
        return off != null && canActuallyRenderInHand(off, InteractionHand.OFF_HAND) ? off : null;
    }

    /**
     * 获取全局第一人称粒子系统。所有自定义物品渲染器通过此方法添加发射器。
     */
    public static FirstPersonParticleSystem getParticleSystem() {
        return PARTICLE_SYSTEM;
    }

    private static IFPAnimationInstance createInstance(ItemStack stack, InteractionHand hand) {
        return getRenderer(stack)
            .map(r -> r.createAnimationInstance(stack, Minecraft.getMinecraft().renderViewEntity, hand))
            .orElse(null);
    }

    private static boolean isCustomItem(ItemStack stack) {
        return getRenderer(stack).isPresent();
    }

    /** 该物品是否拥有自定义第一人称渲染器（由本系统接管渲染，如枪械）。 */
    public static boolean hasCustomRenderer(ItemStack stack) {
        return getRenderer(stack).isPresent();
    }

    /** 按本系统语义判断两堆是否「同一持有物」：枪械走渲染器 isSameItem（NBT 变化不算换物品）。 */
    public static boolean isSameHeldItem(ItemStack a, ItemStack b) {
        return isSameItemStacks(a, b);
    }

    private static long calculateSheatheDuration(ItemStack stack) {
        return getRenderer(stack).map(r -> r.getPutAwayDuration(stack))
            .orElse(0L);
    }

    private static float getSheatheProgress(HandRenderState state) {
        if (state.currentSheatheDuration <= 0) {
            return 1.0f;
        }
        long elapsed = CLOCK.nowMillis() - state.switchStartTime;
        return Math.min(1.0f, (float) elapsed / state.currentSheatheDuration);
    }

    /** 主手当前活跃 instance 是否霸占副手视野（取 occupiesView()，过渡期也稳定）。 */
    private static boolean mainHandBlocksOffhand() {
        IFPAnimationInstance mainInst = getActiveAnimationInstance(InteractionHand.MAIN_HAND);
        return mainInst != null && mainInst.occupiesView();
    }

    /**
     * TODO(L2): 1.20 走 IClientItemExtensions.of(item).getCustomRenderer()；1.7.10 无该扩展点，
     * 改为「物品自身 implements IFPGeoItemRenderer，或已显式注册」。
     */
    private static Optional<IFPGeoItemRenderer> getRenderer(ItemStack stack) {
        if (isEmpty(stack)) {
            return Optional.empty();
        }
        if (stack.getItem() instanceof IFPGeoItemRenderer renderer) {
            return Optional.of(renderer);
        }
        IFPGeoItemRenderer registered = RENDERERS.get(stack.getItem());
        return Optional.ofNullable(registered);
    }

    /**
     * TODO(L2): 1.7.10 用 {@code null} 表示空栈（1.20 的 {@code ItemStack.EMPTY}），
     * 对应 1.20 的 {@code ItemStack#isEmpty()}。
     */
    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getItem() == null || stack.stackSize <= 0;
    }

    /**
     * TODO(L2): 1.20 {@code ItemStack.isSameItem(a,b)} 在 1.7.10 无静态版本，等价写法为
     * 「两栈非空且 Item 相同」。
     */
    private static boolean sameItemFallback(ItemStack oldStack, ItemStack newStack) {
        return !isEmpty(oldStack) && !isEmpty(newStack) && oldStack.getItem() == newStack.getItem();
    }

    private static boolean isSameItemStacks(ItemStack oldStack, ItemStack newStack) {
        if (oldStack == newStack) {
            return true;
        }
        if (isEmpty(oldStack) && isEmpty(newStack)) {
            return true;
        }
        if (isEmpty(oldStack) || isEmpty(newStack)) {
            return false;
        }

        return getRenderer(oldStack).map(r -> r.isSameItem(oldStack, newStack))
            .orElseGet(() -> sameItemFallback(oldStack, newStack));
    }
}
