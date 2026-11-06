package com.tacz.guns.compat.shouldersurfing;

import cpw.mods.fml.common.Loader;

/**
 * Shoulder Surfing Reloaded（{@code shouldersurfing}）兼容门面。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/shouldersurfing/}
 * —— {@code ShoulderSurfingCompat}（门面）+ {@code ShoulderSurfingCompatInner}
 * （{@code Perspective.current() == SHOULDER_SURFING && !InputHandler.FREE_LOOK.isDown()}）+
 * {@code ShoulderSurfingPlugin}（{@code IShoulderSurfingPlugin} 注册点）。
 *
 * <p>
 * <b>1.7.10 形态</b>：Shoulder Surfing Reloaded 的 API 是 1.16+ 的（{@code Perspective} 枚举 +
 * {@code InputHandler.FREE_LOOK}），1.7.10 <b>没有</b>对应版本。{@code ShoulderSurfingCompatInner} /
 * {@code ShoulderSurfingPlugin} 已删除（逐条原因见 {@code docs/PORT_STATUS.md}）；
 * 本类保留上游同名门面，并把 Inner 里的判定改成<b>纯反射</b>（不 import 任何 SS 类型）：
 * 探测到 {@code Perspective.current()} 与 {@code InputHandler.FREE_LOOK} 就按上游语义算，
 * 探测不到就静默返回 {@code false}（= 「不强制显示准星」，与没装 SS 时一致）。
 *
 * <p>
 * 调用点：{@code client/event/CameraSetupEvent}（{@link #isInstalled()}）、
 * {@code client/event/RenderCrosshairEvent}（{@link #showCrosshair()}）、
 * {@code client/init/ClientSetupEvent}（{@link #init()}）。
 */
public final class ShoulderSurfingCompat {

    private static final String MOD_ID = "shouldersurfing";
    private static final String PERSPECTIVE_CLASS = "com.github.exopandora.shouldersurfing.api.model.Perspective";
    private static final String INPUT_HANDLER_CLASS = "com.github.exopandora.shouldersurfing.client.InputHandler";
    private static final String SHOULDER_SURFING_CONSTANT = "SHOULDER_SURFING";
    private static final String FREE_LOOK_FIELD = "FREE_LOOK";

    /** SS 是否可用（模组在 + {@code Perspective} 可加载）。1.7.10 上恒为 {@code false}。 */
    public static final boolean LOADED = Loader.isModLoaded(MOD_ID) && probe();

    private ShoulderSurfingCompat() {}

    /** 对应上游 {@code ShoulderSurfingCompat#init()}（在 {@code FMLClientSetupEvent} 的 {@code enqueueWork} 里调用）。 */
    public static void init() {
        // 上游这里只做 ModList 探测；本移植的探测在静态初始化（LOADED）里已完成，无需额外动作。
        // TODO(L2): 出现 1.7.10 版 SS 后，在此反射注册上游 ShoulderSurfingPlugin 的 registrar。
    }

    /** 对应上游 {@code ShoulderSurfingCompat#showCrosshair()}：越肩视角下是否强制显示准星。 */
    public static boolean showCrosshair() {
        if (!LOADED) {
            return false;
        }
        try {
            Class<?> perspectiveType = Class
                .forName(PERSPECTIVE_CLASS, false, ShoulderSurfingCompat.class.getClassLoader());
            Object current = perspectiveType.getMethod("current")
                .invoke(null);
            if (!(current instanceof Enum)) {
                return false;
            }
            if (!SHOULDER_SURFING_CONSTANT.equals(((Enum<?>) current).name())) {
                return false;
            }
            // 上游：!InputHandler.FREE_LOOK.isDown()
            Class<?> inputHandlerType = Class
                .forName(INPUT_HANDLER_CLASS, false, ShoulderSurfingCompat.class.getClassLoader());
            Object freeLook = inputHandlerType.getField(FREE_LOOK_FIELD)
                .get(null);
            if (freeLook == null) {
                return true;
            }
            Object isDown = freeLook.getClass()
                .getMethod("isDown")
                .invoke(freeLook);
            return !Boolean.TRUE.equals(isDown);
        } catch (Throwable ignored) {
            // 1.7.10 / 任何 API 不匹配：按「没装 SS」处理
            return false;
        }
    }

    /** 对应上游 {@code ShoulderSurfingCompat#isInstalled()}。 */
    public static boolean isInstalled() {
        return LOADED;
    }

    /** 反射探测 {@link #PERSPECTIVE_CLASS}（{@code initialize=false}，不触发对方静态初始化）。 */
    private static boolean probe() {
        try {
            Class.forName(PERSPECTIVE_CLASS, false, ShoulderSurfingCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
