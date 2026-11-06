package com.tacz.guns.compat.cloth;

import java.lang.reflect.Constructor;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiScreen;

import com.tacz.guns.GunMod;
import com.tacz.guns.init.CompatRegistry;

import cpw.mods.fml.common.Loader;

/**
 * Cloth Config（{@code cloth_config}）兼容：把 TACZ 的配置挂到「模组列表 → 配置」入口。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/cloth/}
 * —— {@code MenuIntegration}（{@code ConfigBuilder} 组装）+ {@code client/*ClothConfig} /
 * {@code common/*ClothConfig}（15 个配置项的界面定义）+ {@code widget/*}（自定义控件）。
 *
 * <p>
 * <b>1.7.10 形态</b>：Cloth Config 是 1.14+ 的模组，1.7.10 <b>没有</b>同版本 API
 * （没有 {@code ConfigBuilder}/{@code ConfigCategory}/{@code AbstractConfigListEntry}），
 * 也没有 1.20 的扩展点 {@code ModLoadingContext#registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory)}。
 * 因此那 10 个界面定义类已删除（逐条原因见 {@code docs/PORT_STATUS.md}），本类保留上游入口名与语义：
 * <ul>
 * <li>{@link #registerModsPage()}：探测 Cloth Config；没有就什么都不注册（等价上游「缺库时给出提示页」的路径）。
 * 1.7.10 的配置界面挂点是 {@code cpw.mods.fml.client.IModGuiFactory}（须写在 {@code @Mod(guiFactory=...)} 上），
 * 属编译期声明，无法在这里反射补挂 —— TODO(L2)。</li>
 * <li>{@link #getConfigScreen(GuiScreen)}：有 Cloth → TODO(L2) 反射构建；没有 → 反射构造本工程自带的
 * 缺库提示页 {@code client/gui/compat/ClothConfigScreen}（该类的构造器目前是 {@code protected}，
 * 这里通过反射调用，不引入跨包可见性依赖）。</li>
 * </ul>
 *
 * <p>
 * 调用点：{@code init/CompatRegistry#enqueueCompat()}（{@link #registerModsPage()}，反射调用）、
 * {@code client/input/ConfigKey}（{@link #getConfigScreen(GuiScreen)}）。
 */
public final class MenuIntegration {

    /** Cloth Config 的入口类（只要它可加载就认为 API 在）。 */
    private static final String PROBE_CLASS = "me.shedaniel.clothconfig2.api.ConfigBuilder";
    /** 本工程的「缺库提示页」（客户端类，用字符串反射以免 compat 反向依赖实现细节）。 */
    private static final String FALLBACK_SCREEN_CLASS = "com.tacz.guns.client.gui.compat.ClothConfigScreen";

    /** Cloth Config 是否可用。1.7.10 上恒为 {@code false}。 */
    public static final boolean LOADED = Loader.isModLoaded(CompatRegistry.CLOTH_CONFIG) && probe();

    private MenuIntegration() {}

    /** 对应上游 {@code MenuIntegration#registerModsPage()}。 */
    public static void registerModsPage() {
        if (!LOADED) {
            // 上游：缺库时由 ClothConfigScreen.registerNoClothConfigPage() 提供提示页（见 init/CompatRegistry）
            return;
        }
        // TODO(L2): 1.7.10 无 registerExtensionPoint；出现 1.7.10 版 Cloth Config 后，
        // 通过 IModGuiFactory（@Mod(guiFactory=...)）+ 反射 ConfigBuilder 接线。
        GunMod.LOGGER.info("Detected Cloth Config, but 1.7.10 has no compatible config-screen extension point");
    }

    /**
     * 对应上游 {@code MenuIntegration#getConfigScreen(@Nullable Screen parent)}。
     *
     * @return 永远非 {@code null} 时才是可显示的界面；返回 {@code parent} 表示「没有配置界面可开」
     */
    @Nullable
    public static GuiScreen getConfigScreen(@Nullable GuiScreen parent) {
        if (LOADED) {
            // TODO(L2): 反射 ConfigBuilder.create() → setTitle → setParentScreen → build()（上游同流程）
            return parent;
        }
        GuiScreen fallback = createMissingLibraryScreen(parent);
        return fallback != null ? fallback : parent;
    }

    /** 反射构造缺库提示页 {@code ClothConfigScreen(GuiScreen)}（构造器 protected，故走反射）。 */
    @Nullable
    private static GuiScreen createMissingLibraryScreen(@Nullable GuiScreen parent) {
        try {
            Class<?> screenType = Class.forName(FALLBACK_SCREEN_CLASS, true, MenuIntegration.class.getClassLoader());
            if (!GuiScreen.class.isAssignableFrom(screenType)) {
                return null;
            }
            Constructor<?> constructor = screenType.getDeclaredConstructor(GuiScreen.class);
            constructor.setAccessible(true);
            Object screen = constructor.newInstance(parent);
            return (GuiScreen) screen;
        } catch (Throwable ignored) {
            // 客户端类尚未就绪 / 签名变化：退回「不开界面」
            return null;
        }
    }

    /** 反射探测 {@link #PROBE_CLASS}（{@code initialize=false}，不触发对方静态初始化）。 */
    private static boolean probe() {
        try {
            Class.forName(PROBE_CLASS, false, MenuIntegration.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
