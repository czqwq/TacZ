package com.tacz.guns.compat.kubejs;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.GunItemManager;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.Event;

/**
 * KubeJS 兼容入口。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/kubejs/}（19 个文件）
 * —— {@code TimelessKubeJSPlugin extends KubeJSPlugin}（{@code init}/{@code registerEvents}/
 * {@code registerBindings}/{@code registerTypeWrappers}/{@code registerRecipeSchemas}/
 * {@code registerRecipeComponents}）+ {@code custom/*}（{@code ItemBuilder} 子类）+
 * {@code events/*}（{@code EventGroup}/{@code EventJS} 包装，把 13 个 TACZ 事件转发给脚本）+
 * {@code recipe/*}（{@code RecipeSchema}/{@code RecipeJS}）+
 * {@code util/*}（{@code ItemStackJS}/{@code NBT} 双向转换）。
 *
 * <p>
 * <b>1.7.10 形态</b>：KubeJS 只支持 1.16.5+（其 API 建立在 {@code dev.latvian.mods.rhino} 与
 * 1.20 的注册表/配方体系上），1.7.10 <b>没有</b>对应版本，也没有可反射的等价 API。
 * 除本入口外，其余 18 个纯 KubeJS 绑定文件已删除（逐条原因见 {@code docs/PORT_STATUS.md}）——
 * 它们全部以 KubeJS 的类型作为<b>方法签名的一部分</b>（{@code EventHandler}/{@code ItemStackJS}/
 * {@code RecipeSchema}…），在无该库时无法保持签名，只能删。
 *
 * <p>
 * 本类保留上游入口名与「探测 + 静默降级」语义：
 * <ul>
 * <li>{@link #KUBEJS_MODID} / {@link #LOADED}：模组在 <b>且</b> {@code KubeJSPlugin} 可加载
 * （1.7.10 恒 {@code false}）。</li>
 * <li>{@link #postKubeJSEvent(Event)}：上游 {@code TimelessKubeJSEventRegister#postKubeJSEvent(Event)}
 * 的同义门面 —— 返回值 {@code true} 表示「事件未被脚本处理」。
 * {@code api/event/common/KubeJSGunEventPoster} 的 3 个 default 方法是它未来的调用点
 * （L2 接线：把 default 方法体改成 {@code if (!TimelessKubeJSPlugin.postKubeJSEvent(this)) { ... }}）。</li>
 * <li>{@link #registerGunType(String, AbstractGunItem)}：上游 {@code registerGunType(String, RegistryObject)}
 * 在 1.7.10 的形态（无 {@code RegistryObject}，直接收 {@code AbstractGunItem}）——
 * 这是<b>真的接上了</b>：转发到 {@link GunItemManager#registerGunItem(String, AbstractGunItem)}。</li>
 * </ul>
 */
public final class TimelessKubeJSPlugin {

    /** 上游同名字段。 */
    public static final String KUBEJS_MODID = "kubejs";
    private static final String PROBE_CLASS = "dev.latvian.mods.kubejs.KubeJSPlugin";

    /** KubeJS 是否可用。1.7.10 上恒为 {@code false}。 */
    public static final boolean LOADED = Loader.isModLoaded(KUBEJS_MODID) && probe();

    private TimelessKubeJSPlugin() {}

    /**
     * 对应上游 {@code TimelessKubeJSPlugin#init()}（KubeJS 的插件加载回调）。
     *
     * <p>
     * 1.7.10 上没有 KubeJS 的插件发现机制（上游是 {@code kubejs.plugins.txt} 服务文件，
     * 1.20 专属元数据已随移植删除），本方法只做探测与记录。
     */
    public static void init() {
        if (!LOADED) {
            return;
        }
        // TODO(L2): 出现 1.7.10 版 KubeJS 后，反射接线上游的 registerEvents/registerBindings/
        // registerTypeWrappers/registerRecipeSchemas/registerRecipeComponents
        GunMod.LOGGER.info("Detected KubeJS, but 1.7.10 has no KubeJS API; script bindings stay off");
    }

    /**
     * 对应上游 {@code TimelessKubeJSEventRegister#postKubeJSEvent(Event)}。
     *
     * @return {@code true} 表示「没有脚本处理器接管该事件」（= KubeJS 缺席时的语义）
     */
    public static boolean postKubeJSEvent(Event event) {
        // 1.7.10 无 KubeJS：永远「未处理」，调用方按原逻辑继续
        return true;
    }

    /**
     * 对应上游 {@code TimelessKubeJSPlugin#registerGunType(String, RegistryObject)}。
     *
     * <p>
     * 1.7.10 无 {@code RegistryObject}；{@code GunItemManager} 直接以「类型名 → {@code AbstractGunItem}」
     * 登记（见 {@code docs/PORT_STATUS.md} 的 common-api 跨包契约 4）。
     */
    public static void registerGunType(String typeName, AbstractGunItem gunItem) {
        if (typeName == null || gunItem == null) {
            return;
        }
        GunItemManager.registerGunItem(typeName, gunItem);
    }

    /** 反射探测 {@link #PROBE_CLASS}（{@code initialize=false}，不触发对方静态初始化）。 */
    private static boolean probe() {
        try {
            Class.forName(PROBE_CLASS, false, TimelessKubeJSPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
