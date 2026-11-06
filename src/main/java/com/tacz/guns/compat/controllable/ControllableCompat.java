package com.tacz.guns.compat.controllable;

import net.minecraft.item.ItemStack;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.gun.FireMode;

import cpw.mods.fml.common.Loader;

/**
 * Controllable（手柄支持，{@code controllable}）兼容门面。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/controllable/}
 * —— {@code ControllableCompat}（门面）+ {@code ControllableInner}（按键绑定 {@code ButtonBinding} × 9、
 * 手柄输入派发 {@code ControllerEvents.INPUT}、每 tick 的扳机状态、开枪震动 {@code controller.rumble(...)}）。
 *
 * <p>
 * <b>1.7.10 形态</b>：MrCrayfish's Controllable 只支持 1.12.2+，1.7.10 <b>没有</b>对应版本，
 * 其绑定/事件 API（{@code BindingRegistry}、{@code IBindingContext}、{@code ControllerEvents}、
 * {@code com.mrcrayfish.framework}）在 1.7.10 全不存在。{@code ControllableInner} 已删除
 * （逐条原因见 {@code docs/PORT_STATUS.md}）。
 *
 * <p>
 * 本类保留上游同名同签名门面：
 * <ul>
 * <li>{@link #init()}：探测；1.7.10 上无库 → 空操作（上游「没装 Controllable」的同一条路径）。</li>
 * <li>{@link #onGunShoot(ItemStack, FireMode)}：<b>真的接上了</b> —— 震动走纯反射
 * {@code Controllable.getController().rumble(lowFreq, highFreq, timeMs)}，
 * 缺库/缺方法时静默跳过。缺省震动参数与上游 {@code ControllableInner#rumbleShoot} 的兜底值一致
 * （AUTO：0.15/0.25/80ms，其余：0.25/0.5/100ms）。</li>
 * </ul>
 *
 * <p>
 * TODO(L2): 上游的「按枪械 {@code ControllableData} 覆盖震动参数」依赖
 * {@code TimelessAPI.getGunDisplay(item).getControllableData()}；1.7.10 侧待 client 资源索引就绪后补。
 * TODO(L2): 手柄按键绑定 / 扳机状态（{@code AimKey}/{@code ShootKey} 的 controller 变体）
 * 需要一套 1.7.10 的手柄输入源，目前 {@code ShootKey#shootControllerTick} 保持上游「恒 false」形态。
 */
public final class ControllableCompat {

    private static final String MOD_ID = "controllable";
    private static final String CONTROLLABLE_CLASS = "com.mrcrayfish.controllable.Controllable";

    /** Controllable 是否可用（模组在 + 入口类可加载）。1.7.10 上恒为 {@code false}。 */
    public static final boolean LOADED = Loader.isModLoaded(MOD_ID) && probe();

    private ControllableCompat() {}

    /** 对应上游 {@code ControllableCompat#init()}（在 {@code FMLClientSetupEvent} 的 {@code enqueueWork} 里调用）。 */
    public static void init() {
        if (!LOADED) {
            return;
        }
        // TODO(L2): 反射注册上游 ControllableInner 的 9 个 ButtonBinding + ControllerEvents.INPUT + TickEvents.END_CLIENT
        GunMod.LOGGER
            .info("Detected Controllable, but 1.7.10 has no compatible binding API; controller input stays off");
    }

    /** 对应上游 {@code ControllableCompat#onGunShoot(ItemStack, FireMode)}：开枪时让手柄震动。 */
    public static void onGunShoot(ItemStack gunItem, FireMode fireMode) {
        if (!LOADED) {
            return;
        }
        // 上游 ControllableInner#rumbleShoot 的兜底参数（没有该枪械专属 ControllableData 时用）
        boolean auto = fireMode == FireMode.AUTO;
        float lowFrequency = auto ? 0.15F : 0.25F;
        float highFrequency = auto ? 0.25F : 0.5F;
        int timeInMs = auto ? 80 : 100;
        try {
            Class<?> controllableType = Class
                .forName(CONTROLLABLE_CLASS, false, ControllableCompat.class.getClassLoader());
            Object controller = controllableType.getMethod("getController")
                .invoke(null);
            if (controller == null) {
                return;
            }
            controller.getClass()
                .getMethod("rumble", float.class, float.class, int.class)
                .invoke(controller, lowFrequency, highFrequency, timeInMs);
        } catch (Throwable ignored) {
            // 缺库 / API 不匹配：静默跳过
        }
    }

    /** 反射探测 {@link #CONTROLLABLE_CLASS}（{@code initialize=false}，不触发对方静态初始化）。 */
    private static boolean probe() {
        try {
            Class.forName(CONTROLLABLE_CLASS, false, ControllableCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
