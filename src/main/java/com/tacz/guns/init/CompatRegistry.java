package com.tacz.guns.init;

import com.tacz.guns.GunMod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;

/**
 * 移植说明：可选模组兼容的注册入口。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/CompatRegistry.java}）：
 * {@code @Mod.EventBusSubscriber(bus = MOD)} + {@code InterModEnqueueEvent}，在 {@code enqueueWork} 里用
 * {@code ModList.get().isLoaded(modId)} + {@code DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)} 分发到
 * {@code compat/**}（Cloth Config / Oculus / CarryOn 等）。
 *
 * <p>
 * <b>1.7.10 形态</b>：
 * <ul>
 * <li>{@code ModList.get().isLoaded(id)} → {@link Loader#isModLoaded(String)}</li>
 * <li>{@code DistExecutor} → 1.7.10 无等价物。本类<b>不 import 任何 {@code client/**} / {@code compat/**} 类</b>
 * （L1 编译边界 + 用户决策「反射式可选兼容骨架」）：所有兼容调用都走
 * {@link #invokeOptional(String, String)} 反射，类缺失/方法缺失时静默跳过。</li>
 * <li>{@code InterModEnqueueEvent} → 由 {@code CommonProxy#postInit} 在其它模组加载完成后调用
 * {@link #enqueueCompat()}。</li>
 * </ul>
 *
 * <p>
 * <b>光影门面</b>：上游只探测 {@code oculus}（1.20 的 Iris 移植）。1.7.10 的 Iris 等价物是
 * <b>Angelica</b>（modid {@value #ANGELICA}），两者共用 {@code compat/oculus/OculusCompat} 门面，
 * 因此本类对 {@value #OCULUS} 与 {@value #ANGELICA} 各探测一次，都会触发同一个
 * {@code OculusCompat#initCompat()}（反射调用，缺失时静默跳过）。
 */
public class CompatRegistry {

    public static final String CLOTH_CONFIG = "cloth_config";
    public static final String OCULUS = "oculus";
    /**
     * 1.7.10 的 Iris（光影）等价物是 Angelica，modid {@code angelica}；两者共用
     * {@code compat/oculus/OculusCompat} 门面（其 {@code LOADED = oculus || angelica}），
     * 因此本类必须也探测 {@code angelica}，否则装 Angelica 时 {@code initCompat()} 不会被调用。
     */
    public static final String ANGELICA = "angelica";
    public static final String CARRY_ON_ID = "carryon";

    private CompatRegistry() {}

    /**
     * 对应上游 {@code onEnqueue(InterModEnqueueEvent)}：在其它模组就绪后按 ID 逐个探测。
     */
    public static void enqueueCompat() {
        // 上游：DistExecutor.safeRunWhenOn(CLIENT, () -> MenuIntegration::registerModsPage)
        checkModLoad(
            CLOTH_CONFIG,
            () -> invokeOptional("com.tacz.guns.compat.cloth.MenuIntegration", "registerModsPage"));
        // 上游：FMLEnvironment.dist == CLIENT → ClothConfigScreen.registerNoClothConfigPage()
        // 1.7.10：必须加物理侧闸门 —— 专用服务器上没有 GuiScreen，Class.forName 会以
        // NoClassDefFoundError 被 invokeOptional 静默吞掉，但每次开服都会刷一条 WARN。
        if (FMLCommonHandler.instance()
            .getSide()
            .isClient()) {
            invokeOptional("com.tacz.guns.client.gui.compat.ClothConfigScreen", "registerNoClothConfigPage");
        }
        // 上游：OculusCompat::initCompat
        checkModLoad(OCULUS, () -> invokeOptional("com.tacz.guns.compat.oculus.OculusCompat", "initCompat"));
        // 1.7.10 的 Iris 等价物是 Angelica（modid "angelica"），两者共用 OculusCompat 门面：
        // OculusCompat 的 LOADED = oculus || angelica，但 initCompat() 仍需由这里主动触发。
        checkModLoad(ANGELICA, () -> invokeOptional("com.tacz.guns.compat.oculus.OculusCompat", "initCompat"));
        // 上游：BlackList::addBlackList（纯数据逻辑，服务端也安全）
        checkModLoad(CARRY_ON_ID, () -> invokeOptional("com.tacz.guns.compat.carryon.BlackList", "addBlackList"));
    }

    /**
     * 与 {@link #enqueueCompat()} 同义的入口名（1.7.10 侧的 init 包统一用 {@code register()} 命名，
     * 便于 {@code CommonProxy} 按 {@code CapabilityRegistry#register}/{@code ModPainting#register} 的同一套形状调用）。
     */
    public static void register() {
        enqueueCompat();
    }

    /**
     * 上游 {@code checkModLoad(modId, runnable)} 的等价物（{@code ModList} → {@code Loader}）。
     */
    public static void checkModLoad(String modId, Runnable runnable) {
        if (Loader.isModLoaded(modId)) {
            try {
                runnable.run();
            } catch (Throwable t) {
                // 反射式可选兼容：缺失/不兼容时静默跳过（AGENTS.md 决策 2）
                GunMod.LOGGER.warn("Skipping compat hook for mod '{}'", modId, t);
            }
        }
    }

    /**
     * 反射调用无参静态方法（1.7.10 版 {@code DistExecutor} / 可选依赖的通用形态）。
     *
     * @return 是否成功调用
     */
    public static boolean invokeOptional(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            type.getMethod(methodName)
                .invoke(null);
            return true;
        } catch (ClassNotFoundException e) {
            // 目标类未移植 / 不在编译集合里：静默跳过
            return false;
        } catch (Throwable t) {
            GunMod.LOGGER.warn("Failed to invoke optional compat hook {}.{}()", className, methodName, t);
            return false;
        }
    }
}
