package com.tacz.guns.client.animation.screen;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;

import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 改装界面开合 / 配件切换的动画插值状态。
 *
 * <p>
 * 平台差异：上游用 {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)} 自动注册；1.7.10 没有该注解，
 * 改为首次 {@link #init()} 时自注册到 <b>FML 总线</b>（{@link TickEvent.RenderTickEvent} 只从
 * {@code FMLCommonHandler.instance().bus()} 发出，见 {@code FMLCommonHandler.java:247/260}），
 * 且处理器必须是实例方法（FML 的 {@code ASMEventHandler} 用 {@code INVOKEVIRTUAL} 回调，
 * 静态处理器会抛 {@code IncompatibleClassChangeError}，{@code ASMEventHandler.java:115}）。
 */
public class RefitTransform {

    /** FML 事件总线要求实例注册（静态处理器会 ICCE，见类注释）。 */
    private static final RefitTransform INSTANCE = new RefitTransform();
    private static boolean registered = false;

    /** 首次使用时把 {@link #INSTANCE} 注册到 FML 总线（幂等）。 */
    private static void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    // 以下参数、变量用于改装窗口动画插值
    private static final float REFIT_SCREEN_TRANSFORM_TIMES = 0.25f;
    private static float refitScreenTransformProgress = 1;
    private static long refitScreenTransformTimestamp = -1;
    private static AttachmentType oldTransformType = AttachmentType.NONE;
    private static AttachmentType currentTransformType = AttachmentType.NONE;
    private static float refitScreenOpeningProgress = 0;
    private static long refitScreenOpeningTimestamp = -1;

    public static void init() {
        ensureRegistered();
        refitScreenTransformProgress = 1;
        refitScreenTransformTimestamp = GunTime.nowMillis();
        oldTransformType = AttachmentType.NONE;
        currentTransformType = AttachmentType.NONE;
    }

    public static void resetTimestamps() {
        refitScreenTransformProgress = 1;
        refitScreenTransformTimestamp = -1;
        refitScreenOpeningProgress = 0;
        refitScreenOpeningTimestamp = -1;
        oldTransformType = AttachmentType.NONE;
        currentTransformType = AttachmentType.NONE;
    }

    private static boolean isRefitScreenOpen() {
        // 1.20: Minecraft.getMinecraft().currentScreen；1.7.10: Minecraft#currentScreen
        return Minecraft.getMinecraft().currentScreen instanceof GunRefitScreen;
    }

    public static boolean isActive() {
        return isRefitScreenOpen() || refitScreenOpeningProgress > 0;
    }

    public static boolean shouldSuppressMouseTurn() {
        return !isRefitScreenOpen() && refitScreenOpeningProgress > 0;
    }

    public static float getOpeningProgress() {
        return refitScreenOpeningProgress;
    }

    @Nonnull
    public static AttachmentType getOldTransformType() {
        return Objects.requireNonNullElse(oldTransformType, AttachmentType.NONE);
    }

    @Nonnull
    public static AttachmentType getCurrentTransformType() {
        return Objects.requireNonNullElse(currentTransformType, AttachmentType.NONE);
    }

    public static float getTransformProgress() {
        return refitScreenTransformProgress;
    }

    public static boolean changeRefitScreenView(AttachmentType attachmentType) {
        if (refitScreenTransformProgress != 1 || refitScreenOpeningProgress != 1) {
            return false;
        }
        oldTransformType = currentTransformType;
        currentTransformType = attachmentType;
        refitScreenTransformProgress = 0;
        refitScreenTransformTimestamp = GunTime.nowMillis();
        return true;
    }

    @SubscribeEvent
    public void tickInterpolation(TickEvent.RenderTickEvent event) {
        // tick opening progress
        if (refitScreenOpeningTimestamp == -1) {
            refitScreenOpeningTimestamp = GunTime.nowMillis();
        }
        if (isRefitScreenOpen()) {
            refitScreenOpeningProgress += (GunTime.nowMillis() - refitScreenOpeningTimestamp)
                / (REFIT_SCREEN_TRANSFORM_TIMES * 1000);
            if (refitScreenOpeningProgress > 1) {
                refitScreenOpeningProgress = 1;
            }
        } else {
            refitScreenOpeningProgress -= (GunTime.nowMillis() - refitScreenOpeningTimestamp)
                / (REFIT_SCREEN_TRANSFORM_TIMES * 1000);
            if (refitScreenOpeningProgress < 0) {
                refitScreenOpeningProgress = 0;
            }
        }
        refitScreenOpeningTimestamp = GunTime.nowMillis();
        // tick transform progress
        if (refitScreenTransformTimestamp == -1) {
            refitScreenTransformTimestamp = GunTime.nowMillis();
        }
        refitScreenTransformProgress += (GunTime.nowMillis() - refitScreenTransformTimestamp)
            / (REFIT_SCREEN_TRANSFORM_TIMES * 1000);
        if (refitScreenTransformProgress > 1) {
            refitScreenTransformProgress = 1;
        }
        refitScreenTransformTimestamp = GunTime.nowMillis();
    }
}
