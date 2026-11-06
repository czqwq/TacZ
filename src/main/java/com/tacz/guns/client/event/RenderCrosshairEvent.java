package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.GlStateManager;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 准心渲染：持枪时替换原版准心、绘制命中/击杀标记。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/RenderCrosshairEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)}，
 * 监听 {@code RenderGuiOverlayEvent.Pre}（{@code VanillaGuiOverlay.CROSSHAIR}）与
 * {@code TickEvent.RenderTickEvent}）。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code RenderGuiOverlayEvent.Pre + VanillaGuiOverlay.CROSSHAIR.id()} →
 * {@link RenderGameOverlayEvent.Pre} + {@code ElementType.CROSSHAIRS}（{@code receiveCanceled = true}
 * 保持上游语义）；{@code event.getWindow()} → 事件的 {@code resolution}（{@link
 * net.minecraft.client.gui.ScaledResolution}），{@code getGuiScaledWidth/Height} →
 * {@code getScaledWidth/Height}；{@code event.getGuiGraphics()} → {@link GuiGraphics#create()}；
 * {@code event.getPartialTick()} → 字段 {@code partialTicks}；</li>
 * <li>{@code Options#getCameraType().isFirstPerson()} → {@code GameSettings#thirdPersonView == 0}；
 * {@code Options#hideGui} → {@code GameSettings#hideGUI}；</li>
 * <li>{@code Minecraft#screen} → {@code currentScreen}，{@code Screen} → {@link GuiScreen}；</li>
 * <li>{@code RenderSystem.blendFunc(GlStateManager.SourceFactor, DestFactor)} 的 1.20 枚举重载
 * → {@code mcshim.RenderSystem.blendFunc(int, int)} + {@code glValue()}；
 * {@code RenderSystem.setShaderColor(r,g,b,a)} 的「染色」语义 → 额外调用
 * {@link GuiGraphics#setColor}（mcshim 的 {@code blit} 用顶点色 tint，内部会把 glColor 复位成白色）；</li>
 * <li>{@code Minecraft#getWindow()} 与 {@code MultiPlayerGameMode#getPlayerMode()} 删除：
 * 1.7.10 的窗口尺寸由 {@code ScaledResolution} 提供，且 <b>没有旁观者模式</b>（1.8+ 才有），
 * 所以 {@code GameType.SPECTATOR} 分支无从表达（{@code // TODO(L2)} 在调用点）；
 * {@code player.isSpectator()} 同理。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 特有（相对上游）</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（{@code cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115}）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在事件派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法(...)} 在这里改为 {@code public void 处理方法(...)}
 * （类名 / 方法名 / 参数 / 语义一律不变；非处理方法的静态工具方法与字段仍保持 {@code static}）。
 * 本类也因为「{@code EventBus#register(Class)} 在 1.7.10 无效」而提供 public 无参构造，
 * 由 {@code client/ClientProxy} 反射 {@code new} 后注册到正确的总线。
 */
public class RenderCrosshairEvent {

    private static final ResourceLocation HIT_ICON = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/crosshair/hit/hit_marker.png");
    private static final long KEEP_TIME = 300;
    private static boolean isRefitScreen = false;
    private static long hitTimestamp = -1L;
    private static long killTimestamp = -1L;
    private static long headShotTimestamp = -1L;

    /**
     * 当玩家手上拿着枪时，播放特定动画、或瞄准时需要隐藏准心
     */
    @SubscribeEvent(receiveCanceled = true)
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        // 上游还有 player.isSpectator()；1.7.10 无旁观者模式
        if (player == null) {
            return;
        }
        if (!IGun.mainHandHoldGun(player)) {
            return;
        }
        // 全面替换成自己的
        event.setCanceled(true);
        GuiGraphics graphics = GuiGraphics.create();
        int width = event.resolution.getScaledWidth();
        int height = event.resolution.getScaledHeight();
        // 击中显示
        renderHitMarker(graphics, width, height);
        // 换弹进行时取消准心渲染
        ReloadState reloadState = IGunOperator.fromLivingEntity(player)
            .getSynReloadState();
        if (reloadState.getStateType()
            .isReloading()) {
            return;
        }
        // 打开枪械改装界面的时候，取消准心渲染
        if (isRefitScreen) {
            return;
        }
        // 播放的动画需要隐藏准心时，取消准心渲染
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof IGun)) {
            return;
        }

        IClientPlayerGunOperator playerGunOperator = IClientPlayerGunOperator.fromLocalPlayer(player);
        float partialTicks = event.partialTicks;
        TimelessAPI.getGunDisplay(stack)
            .ifPresent(gunIndex -> {
                // 瞄准快要完成时，取消准心渲染
                if (playerGunOperator.getClientAimingProgress(partialTicks) > 0.9) {
                    // 枪包可以强制显示准星
                    boolean forceShow = gunIndex.isShowCrosshair();
                    // 越肩视角可以强制显示准星
                    boolean shoulderSurfingForceShow = ShoulderSurfingCompat.showCrosshair();
                    // 两个强制都没有时，那么才允许隐藏
                    if (!forceShow && !shoulderSurfingForceShow) {
                        return;
                    }
                }

                AnimationStateMachine<?> animationStateMachine = gunIndex.getAnimationStateMachine();
                if (animationStateMachine == null) {
                    renderCrosshair(graphics, width, height);
                    return;
                }
                AnimationStateContext context = animationStateMachine.getContext();
                if (context == null || !context.shouldHideCrossHair()) {
                    renderCrosshair(graphics, width, height);
                }
            });
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        // 奇迹的是，RenderGameOverlayEvent.PreLayer 事件中，screen 还未被赋值...
        isRefitScreen = Minecraft.getMinecraft().currentScreen instanceof GunRefitScreen;
    }

    private static void renderCrosshair(GuiGraphics graphics, int width, int height) {
        GameSettings options = Minecraft.getMinecraft().gameSettings;
        // 越肩视角可以强制显示准星
        boolean shoulderSurfingForceShow = ShoulderSurfingCompat.showCrosshair();
        if (options.thirdPersonView != 0 && !shoulderSurfingForceShow) {
            return;
        }
        if (options.hideGUI) {
            return;
        }
        // TODO(L2): 上游还有 `gameMode.getPlayerMode() == GameType.SPECTATOR → return`；
        // 1.7.10 没有旁观者模式（1.8+），无法表达，此处省略。

        ResourceLocation location = CrosshairType.getTextureLocation(RenderConfig.CROSSHAIR_TYPE.get());

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA.glValue(),
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.glValue());
        RenderSystem.setShaderColor(1F, 1F, 1F, 0.9f);
        // 上游 RenderSystem.setShaderColor 的 tint 在 mcshim 里由 GuiGraphics#setColor 承担
        // （mcshim 的 blit 用顶点色 tint，内部会把 glColor 复位成白色），两者一起设置以保证染色
        graphics.setColor(1F, 1F, 1F, 0.9f);
        float x = width / 2f - 8;
        float y = height / 2f - 8;
        graphics.blit(location, (int) x, (int) y, 0, 0, 16, 16, 16, 16);
    }

    private static void renderHitMarker(GuiGraphics graphics, int width, int height) {
        long remainHitTime = GunTime.nowMillis() - hitTimestamp;
        long remainKillTime = GunTime.nowMillis() - killTimestamp;
        float offset = RenderConfig.HIT_MARKET_START_POSITION.get()
            .floatValue();
        float fadeTime;

        if (!GunTime.isWithin(killTimestamp, KEEP_TIME)) {
            if (!GunTime.isWithin(hitTimestamp, KEEP_TIME)) {
                return;
            } else {
                fadeTime = remainHitTime;
            }
        } else {
            // 最大位移为 4 像素
            offset += (remainKillTime * 4f) / KEEP_TIME;
            fadeTime = remainKillTime;
        }

        float x = width / 2f - 8;
        float y = height / 2f - 8;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA.glValue(),
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.glValue());
        if (!GunTime.isWithin(headShotTimestamp, KEEP_TIME)) {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1 - fadeTime / KEEP_TIME);
            graphics.setColor(1F, 1F, 1F, 1 - fadeTime / KEEP_TIME);
        } else {
            RenderSystem.setShaderColor(1F, 0, 0, 1 - fadeTime / KEEP_TIME);
            graphics.setColor(1F, 0, 0, 1 - fadeTime / KEEP_TIME);
        }

        graphics.blit(HIT_ICON, (int) (x - offset), (int) (y - offset), 0, 0, 8, 8, 16, 16);
        graphics.blit(HIT_ICON, (int) (x + 8 + offset), (int) (y - offset), 8, 0, 8, 8, 16, 16);
        graphics.blit(HIT_ICON, (int) (x - offset), (int) (y + 8 + offset), 0, 8, 8, 8, 16, 16);
        graphics.blit(HIT_ICON, (int) (x + 8 + offset), (int) (y + 8 + offset), 8, 8, 8, 8, 16, 16);
    }

    public static void markHitTimestamp() {
        RenderCrosshairEvent.hitTimestamp = GunTime.nowMillis();
    }

    public static void markKillTimestamp() {
        RenderCrosshairEvent.killTimestamp = GunTime.nowMillis();
    }

    public static void markHeadShotTimestamp() {
        RenderCrosshairEvent.headShotTimestamp = GunTime.nowMillis();
    }

    public static void resetTimestamps() {
        hitTimestamp = -1L;
        killTimestamp = -1L;
        headShotTimestamp = -1L;
    }
}
