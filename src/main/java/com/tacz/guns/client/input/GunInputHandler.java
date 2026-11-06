package com.tacz.guns.client.input;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.util.InputExtraCheck;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 键位注册 + 按键分发。
 *
 * <p>
 * 上游：tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/GunInputHandler.java，用
 * {@code @Mod.EventBusSubscriber(CLIENT)} 订阅 {@code RegisterKeyMappingsEvent} / {@code InputEvent.Key} /
 * {@code InputEvent.MouseButton.Pre/.Post}。
 *
 * <p>
 * 1.7.10 映射：{@code KeyMapping} → {@link KeyBinding}（构造器 {@code (description, keyCode, category)}），注册走
 * {@link ClientRegistry#registerKeyBinding}（内部只是把 binding 追加进 {@code GameSettings#keyBindings}）；
 * <b>1.7.10 没有输入事件</b> ⇒ 改为在 {@link TickEvent.ClientTickEvent}（{@code Phase.START}）里对每个绑定做边缘
 * 检测，用 {@link GameSettings#isKeyDown(KeyBinding)} 取当前物理按住状态（{@code keyCode < 0 ?
 * Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode)}，GameSettings.java:319-322），与上一 tick 比较
 * 得到 press / release 沿；{@code KeyConflictContext}/{@code KeyModifier} 在 1.7.10 不存在 ⇒ 用
 * {@link #MODIFIER_ALT} 表达上游 {@code KeyModifier.ALT}，「只在游戏内生效」由
 * {@code InputExtraCheck#isInGame()} 保底。
 *
 * <p>
 * <b>已知限制</b>：{@code KeyBinding} 构造时会把 {@code keyCode → binding} 写进一个 {@code IntHashMap}，而
 * {@code Minecraft#runTick} 每次按键只对「该 keyCode 对应的那一个 binding」更新 {@code pressed}/{@code pressTime}
 * ⇒ <b>任何与原生键位同 keyCode 的自定义绑定都会顶掉原生键位</b>。默认键位里只有 {@code ConfigKey} 原本要占
 * {@code T}（= 原生聊天键），故它的 1.7.10 默认值降级为「未绑定」。
 *
 * <p>
 * <b>鼠标键必须单独处理</b>：{@code ShootKey.SHOOT_KEY = -100}（同 {@code keyBindAttack}）、
 * {@code AimKey.AIM_KEY = -99}（同 {@code keyBindUseItem}）不能降级，而构造器把静态 {@code hash} 的这两个槽位改成
 * TACZ 绑定后，{@code KeyBinding#setKeyBindState}/{@code #onTick}（{@code Minecraft#runTick} 用 {@code j - 100}
 * 调用，Minecraft.java:1776-1786）只会更新 TACZ 绑定 ⇒ 原版左键（攻击/挖掘）、右键（使用/放置/开箱/进食）
 * <b>整局失效</b>。修复见 {@link #detachMouseBindingsFromVanillaKeyBindingHash()}。
 */
@SideOnly(Side.CLIENT)
public final class GunInputHandler {

    /** 1.20 {@code KeyModifier.NONE} 的 1.7.10 等价物。 */
    public static final int MODIFIER_NONE = 0;
    /** 1.20 {@code KeyModifier.ALT} 的 1.7.10 等价物（{@code KeyModifier} 在 1.7.10 不存在）。 */
    public static final int MODIFIER_ALT = 1;

    /**
     * 供事件总线按实例注册（{@code EventBus#register(Object)} 用 {@code target.getClass()} 反射，
     * 传 Class 对象不会注册任何东西）。
     */
    public static final GunInputHandler INSTANCE = new GunInputHandler();

    private static final List<PressBinding> PRESS_BINDINGS = new ArrayList<>();
    /**
     * keyCode → 上一 tick 的<b>原始</b>按住状态（不含修饰键），用于按下沿检测。
     *
     * <p>
     * 必须按 keyCode 记录而不是按 binding：同一 keyCode 可能被多个 binding 共用（{@code MeleeKey}/{@code ZoomKey}
     * 默认都是 V；{@code ReloadKey}/{@code UnloadKey} 都是 R），上游 1.20 一次 {@code InputEvent.Key} 只派发一次，
     * {@code onPress(key)} 内部再按 {@code isActiveAndMatches(key)} 决定哪些 binding 响应。
     */
    private static final Map<Integer, Boolean> LAST_KEY_DOWN = new HashMap<>();

    private GunInputHandler() {}

    static {
        // 顺序与上游 PRESS_BINDINGS 完全一致
        press(UnloadKey.UNLOAD_KEY, MODIFIER_ALT, UnloadKey::onUnloadPress);
        press(AimKey.AIM_KEY, MODIFIER_NONE, AimKey::onAimPress);
        press(CrawlKey.CRAWL_KEY, MODIFIER_NONE, CrawlKey::onCrawlPress);
        press(ReloadKey.RELOAD_KEY, MODIFIER_NONE, ReloadKey::onReloadPress);
        press(InspectKey.INSPECT_KEY, MODIFIER_NONE, InspectKey::onInspectPress);
        press(FireSelectKey.FIRE_SELECT_KEY, MODIFIER_NONE, FireSelectKey::onFireSelectPress);
        press(MeleeKey.MELEE_KEY, MODIFIER_NONE, MeleeKey::onMeleePress);
        press(ZoomKey.ZOOM_KEY, MODIFIER_NONE, ZoomKey::onZoomPress);
        press(InteractKey.INTERACT_KEY, MODIFIER_NONE, InteractKey::onInteractPress);
        press(RefitKey.REFIT_KEY, MODIFIER_NONE, RefitKey::onRefitPress);
        press(ConfigKey.OPEN_CONFIG_KEY, MODIFIER_ALT, ConfigKey::onOpenConfig);
    }

    private static void press(KeyBinding keyMapping, int modifier, Runnable action) {
        PRESS_BINDINGS.add(new PressBinding(keyMapping, modifier, action));
    }

    /** 对应上游 {@code RegisterKeyMappingsEvent}。 */
    public static void registerKeyMappings() {
        for (PressBinding binding : PRESS_BINDINGS) {
            ClientRegistry.registerKeyBinding(binding.keyMapping());
        }
        ClientRegistry.registerKeyBinding(ShootKey.SHOOT_KEY);
        // 必须在两个鼠标绑定构造完成之后立刻恢复原版键位（见方法注释）
        detachMouseBindingsFromVanillaKeyBindingHash();
        // 安装 GunTime 的暂停闸门（幂等；单机暂停时停表，见 GunTime#installClientTickGate）。
        // 放在这里而不是 client/init：client/init 不在本轮写作用域，而本方法正是客户端初始化期的
        // 输入层入口（ClientSetupEvent#onClientSetup → step("GunInputHandler.registerKeyMappings")），
        // 此时 Minecraft 单例已构造完成、theWorld 可能为 null，但 isGamePaused 在菜单里恒 false。
        // 专用服务端不会加载本类（@SideOnly(CLIENT)），因此 GunTime 默认「永远在走」不受影响。
        GunTime.installClientTickGate(
            () -> !Minecraft.getMinecraft()
                .isGamePaused());
    }

    /**
     * 把 TACZ 的「鼠标键」绑定从 {@code KeyBinding} 的静态 {@code keybindArray} 里摘掉，让静态 {@code hash} 恢复为
     * 原版绑定，从而不破坏原版左/右键。
     *
     * <p>
     * 问题链：{@code KeyBinding} 构造器执行 {@code keybindArray.add(this); hash.addKey(keyCode, this)}
     * （KeyBinding.java:82-91，同一 keyCode 后加入者覆盖先加入者）；原版攻击/使用键在 {@code GameSettings} 构造器里
     * 创建（GameSettings.java:211/213，keyCode 分别 {@code -100}/{@code -99}），早于所有模组的
     * {@code ClientSetupEvent}；{@code Minecraft#runTick} 用 {@code KeyBinding.setKeyBindState(j - 100, state)} /
     * {@code onTick(j - 100)}（Minecraft.java:1776-1786）按 keyCode 从 {@code hash} 里查唯一 binding 再改
     * pressed/pressTime（KeyBinding.java:28-52）⇒ TACZ 的 {@link ShootKey#SHOOT_KEY}（{@code -100}）与
     * {@link AimKey#AIM_KEY}（{@code -99}）一构造，原版 {@code keyBindAttack}/{@code keyBindUseItem} 的
     * pressed/pressTime 就不再更新 ⇒ 原版左键（攻击/挖方块）与右键（使用/放置/开箱/进食/拉弓）全部失效。
     *
     * <p>
     * 修法：把两个绑定从 {@code keybindArray} 移除后调 {@link KeyBinding#resetKeyBindingArrayAndHash()} 重建
     * {@code hash}，原版 {@code -100/-99} 重新成为唯一映射。TACZ 自己的开火/瞄准不依赖 pressed/pressTime ——
     * {@link #isKeyDown(KeyBinding, int)} 走 {@code GameSettings#isKeyDown}，直接读
     * {@code Mouse.isButtonDown(keyCode + 100)}（GameSettings.java:319-322），与 {@code hash} 无关。
     * 后续「控制」界面改键/恢复默认会再调 {@code resetKeyBindingArrayAndHash()}（GuiControls.java:79/97/135、
     * GameSettings.java:1079），此时两个绑定已不在 {@code keybindArray} 里，重建结果仍正确，
     * <b>不需要每 tick 重复修补</b>。绑定仍保留在 {@code GameSettings#keyBindings}（ClientRegistry.java:47-50 追加），
     * 故「控制」界面依旧可见/可改键、options.txt 依旧保存。
     *
     * <p>
     * {@code keybindArray} 是 {@code private static final List}，1.7.10 没有访问器 ⇒ 反射按<b>字段类型</b>定位
     * （静态 List，该类里唯一），而不是按字段名 —— 避免 MCP/SRG 名差异。
     */
    private static void detachMouseBindingsFromVanillaKeyBindingHash() {
        try {
            Field arrayField = null;
            for (Field field : KeyBinding.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && List.class.isAssignableFrom(field.getType())) {
                    arrayField = field;
                    break;
                }
            }
            if (arrayField == null) {
                GunMod.LOGGER.error("[TACZ] KeyBinding.keybindArray not found; vanilla attack/use keys may be broken");
                return;
            }
            arrayField.setAccessible(true);
            Object value = arrayField.get(null);
            if (!(value instanceof List<?>bindings)) {
                GunMod.LOGGER.error("[TACZ] KeyBinding.keybindArray has unexpected type {}; skip key fix", value);
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> rawBindings = (List<Object>) bindings;
            rawBindings.remove(ShootKey.SHOOT_KEY);
            rawBindings.remove(AimKey.AIM_KEY);
            // 重建 hash：从 keybindArray 重新 add，原版 -100/-99 恢复为唯一映射
            KeyBinding.resetKeyBindingArrayAndHash();
        } catch (Throwable t) {
            // 修复失败也不能让客户端初始化中断（键盘/其它键位仍可用，只是鼠标键冲突仍在）
            GunMod.LOGGER.error("[TACZ] failed to detach mouse key bindings from vanilla key hash", t);
        }
    }

    /**
     * 当前物理按键（含修饰键）是否按住 —— 对应上游 {@code KeyMapping#isDown()}。
     *
     * @param keyMapping 绑定
     * @param modifier   {@link #MODIFIER_NONE} 或 {@link #MODIFIER_ALT}
     */
    public static boolean isKeyDown(KeyBinding keyMapping, int modifier) {
        if (keyMapping == null || keyMapping.getKeyCode() == 0) {
            return false;
        }
        if (modifier == MODIFIER_ALT && !isAltDown()) {
            return false;
        }
        return GameSettings.isKeyDown(keyMapping);
    }

    /** 不带修饰键的按住判定（对应上游 {@code KeyMapping#isDown()}）。 */
    public static boolean isKeyDown(KeyBinding keyMapping) {
        return isKeyDown(keyMapping, MODIFIER_NONE);
    }

    /** 1.7.10 的 Alt 状态（{@code KeyModifier.ALT} 的等价判定）。 */
    public static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    /**
     * 对应上游 {@code InputEvent.Key} + {@code InputEvent.MouseButton.Pre/.Post}。
     *
     * <p>
     * 每 tick（{@code Phase.START}）做一次边缘检测：松开沿什么都不做（1.7.10 的按键状态由原版管理，不需要手工
     * {@code setDown(false)}），按下沿先尝试「关闭改装界面」，再按上游顺序分发。
     *
     * <p>
     * 必须对<b>全部</b> binding 做原始 keyCode 边缘检测（同一 keyCode 只算一次按下，见 {@link #LAST_KEY_DOWN}），
     * 再按上游形态 {@code onPress(pressedKey)} 只派发 keyCode 匹配的 binding —— 上游
     * {@code GunInputHandler#onPress(InputConstants.Key)} 用 {@code keyMapping.isActiveAndMatches(key)} 只匹配
     * 本次按下的 key，命中即 break 的实现会重复触发已按住的键、并让同 tick 的第 2 个键延迟一 tick。
     *
     * <p>
     * <b>V 键（Melee / Zoom 同键）是上游默认设计</b>：{@code MeleeKey} 只在未瞄准时近战、{@code ZoomKey} 只在瞄准时
     * 切换倍率（两者默认都是 {@code GLFW_KEY_V}，MeleeKey.java:31 / ZoomKey.java:31），同一次按下两个 handler 都会
     * 收到（上游 GunInputHandler.java:80-92 的 for 循环无 break），因此<b>不需要改默认键</b>。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        for (PressBinding binding : PRESS_BINDINGS) {
            int keyCode = binding.keyMapping()
                .getKeyCode();
            if (keyCode == Keyboard.KEY_NONE) {
                continue;
            }
            // 原始按键状态（不含 Alt 修饰）：对应上游「收到这个 key 的按下事件」。
            // 修饰键在派发阶段由 isKeyDown(binding, modifier) 判定，等价上游 KeyMapping#isActiveAndMatches。
            boolean down = GameSettings.isKeyDown(binding.keyMapping());
            boolean wasDown = Boolean.TRUE.equals(LAST_KEY_DOWN.put(keyCode, down));
            if (!down || wasDown) {
                continue;
            }
            // 上游 onKeyPress：先尝试 closeRefitScreen(key)，被消费则不再派发
            if (closeRefitScreen(binding)) {
                continue;
            }
            onPress(binding);
        }
    }

    /**
     * 对应上游 {@code onPress(InputConstants.Key)}：只派发与按下的 key 匹配的 binding，按上游顺序。
     *
     * <p>
     * 上游 {@code KeyMapping#isActiveAndMatches(key)} = keyCode 相同且该 binding 的修饰键当前生效；
     * 1.7.10 没有 {@code InputConstants.Key}，用 {@code pressed.keyMapping().getKeyCode()} 做 key 匹配。
     */
    private void onPress(PressBinding pressed) {
        int pressedKeyCode = pressed.keyMapping()
            .getKeyCode();
        for (PressBinding binding : PRESS_BINDINGS) {
            // 某个动作可能打开界面，后续动作不能继续穿透到游戏中。
            if (!InputExtraCheck.isInGame()) {
                return;
            }
            // 上游 isActiveAndMatches(key)：keyCode 相同 + 该 binding 的修饰键当前生效
            if (binding.keyMapping()
                .getKeyCode() != pressedKeyCode) {
                continue;
            }
            if (!isKeyDown(binding.keyMapping(), binding.modifier())) {
                continue;
            }
            binding.action()
                .run();
            // 即使卸弹条件不满足，也不能让 Alt+R 穿透成普通换弹。
            if (binding.keyMapping() == UnloadKey.UNLOAD_KEY) {
                return;
            }
        }
    }

    /**
     * 对应上游 {@code closeRefitScreen(InputConstants.Key)}：改装界面开着时按下改装键即关闭。
     *
     * <p>
     * 上游还检查 {@code mc.getOverlay() != null}（加载覆盖层）与 {@code mc.isWindowActive()}；
     * 1.7.10 无覆盖层抽象，用「当前屏幕是 {@link GunRefitScreen}」+ {@code Display.isActive()} 覆盖同样的意图。
     */
    private static boolean closeRefitScreen(PressBinding binding) {
        if (binding.keyMapping() != RefitKey.REFIT_KEY) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GunRefitScreen)) {
            return false;
        }
        if (!Display.isActive()) {
            return false;
        }
        // Minecraft#displayGuiScreen 会在切换时自动调用旧屏幕的 onGuiClosed()
        mc.displayGuiScreen(null);
        return true;
    }

    /** 上游 {@code record PressBinding(KeyMapping keyMapping, Runnable action)} + 1.7.10 的修饰键。 */
    private record PressBinding(KeyBinding keyMapping, int modifier, Runnable action) {}
}
