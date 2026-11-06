package com.tacz.guns.client.gui.components.creative;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainerCreative;

import com.tacz.guns.client.gui.components.CreativePackFilters;
import com.tacz.guns.mixin.client.CreativeContainerAccessor;

/**
 * {@code mixin/client/CreativeModeInventoryScreenMixin} 的回调/工厂宿主（<b>普通类，不是 Mixin</b>）。
 *
 * <h2>为什么这个类必须放在 {@code com.tacz.guns.client.gui.components.creative} 而不是 mixin 包</h2>
 * {@code mixins.tacz.json} 的 {@code "package"} 是 {@code com.tacz.guns.mixin}，Mixin 会把该包<b>及其子包</b>
 * 下的<b>所有</b>类都当成 mixin 处理；任何非 {@code @Mixin} 的普通类在该包下的类被
 * {@code LaunchClassLoader} 加载时会被 Mixin 拦截并抛：
 * 
 * <pre>
 * java.lang.NoClassDefFoundError: com/tacz/guns/mixin/client/CreativePackFilterHooks
 *   Caused by: IllegalClassLoadError: ... is in a defined mixin package com.tacz.guns.mixin.*
 * </pre>
 * 
 * 这条限制与「mixin 类不能有嵌套类（NESTING）」是两件独立的事，两者都必须遵守：
 * <ol>
 * <li>Mixin 类里<b>不能</b>有嵌套/内部/匿名类（否则类文件带 {@code NestMembers} →
 * Mixin 0.8.7 {@code LanguageFeatures} 要求 {@code NESTING} = JAVA_11，与 {@code JAVA_8} 冲突）；</li>
 * <li>被提出来的这些辅助类<b>不能</b>留在 {@code com.tacz.guns.mixin.**} 下（否则被当 mixin 拦截）。</li>
 * </ol>
 *
 * <h2>为什么需要它（两个约束）</h2>
 *
 * <h3>约束 A：Mixin 类里不能声明嵌套类</h3>
 * 见上：会触发 {@code NESTING} 校验失败。所以原本写成
 * {@code CreativeModeInventoryScreenMixin} 内部 {@code private static final class} 的两个 helper
 * 必须提成顶层类。
 *
 * <h3>约束 B：Mixin 类里不能写捕获 {@code this} 的 lambda / 方法引用</h3>
 * Mixin 类不会被实例化、也不会成为目标类的父类，{@code this::foo} 生成的合成方法与
 * {@code invokedynamic} 绑定会以 Mixin 类为 owner，运行期把一个
 * {@code GuiContainerCreative} 当成 {@code CreativeModeInventoryScreenMixin} 用
 * → {@code ClassCastException} / {@code NoSuchMethodError}（上游
 * {@code CreativeModeInventoryScreenMixin} 的注释也踩过这个坑）。
 *
 * <p>
 * 本类同时解决两条：它是<b>普通顶层类</b>（可以安全使用 lambda / 方法引用，Mixin 不会校验它，
 * 也不在本工程的 mixin 包里），又通过静态工厂把创建动作移出 Mixin。
 * 传入的 {@code buttons} 只是 {@code List} 引用，不持有 GUI 实例、不形成环，无内存泄漏。
 *
 * <h2>为什么类与成员必须是 {@code public}</h2>
 * 调用它的代码在 Mixin 合并之后<b>位于目标类 {@code GuiContainerCreative} 里</b>
 * （包 {@code net.minecraft.client.gui.inventory}），与本类不同包 ——
 * 若类或成员包内可见，运行期会抛 {@code IllegalAccessError}。
 *
 * <h2>提供的东西</h2>
 * <ul>
 * <li>{@link #buttonAdder(List)}：{@link CreativePackFilters#init(Consumer, net.minecraft.creativetab.CreativeTabs)}
 * 的 {@code Consumer<GuiButton>}（等价上游 {@code addRenderableWidget(widget)}）。</li>
 * <li>{@link #CONTAINER_ACCESS}：{@link CreativePackFilters#containerAccessProvider} 的实现
 * （用 {@link CreativeContainerAccessor} 的 {@code @Accessor}/{@code @Invoker} 取
 * {@code ContainerCreative#itemList/scrollTo}，替代生产环境必然失败的 MCP 名反射）。</li>
 * </ul>
 */
public final class CreativePackFilterHooks {

    private CreativePackFilterHooks() {}

    /**
     * 构造「把按钮收进给定列表」的 {@code Consumer}。
     *
     * <p>
     * 这里的 lambda 位于<b>普通类</b>中（不是 Mixin 类、也不在 mixin 包），
     * 所以不会触发约束 A / B 的任何问题。
     *
     * @param buttons 目标按钮列表（由 Mixin 用 {@code @Unique} 字段自持，等价 1.20 的 widget 层）
     * @return {@code Consumer<GuiButton>}，每收到一个按钮就 append 到 {@code buttons}
     */
    public static Consumer<GuiButton> buttonAdder(List<GuiButton> buttons) {
        return buttons::add;
    }

    /**
     * {@link CreativePackFilters#containerAccessProvider} 的实例
     * （= {@link #createAccess(GuiContainerCreative)} 的方法引用）。
     */
    public static final Function<GuiContainerCreative, CreativePackFilters.ContainerAccess> CONTAINER_ACCESS = CreativePackFilterHooks::createAccess;

    /**
     * 由 GUI 取出容器访问器。
     *
     * <p>
     * 用 {@code instanceof} 判断 Mixin 是否真的应用上了：Mixin 没应用（例如
     * {@code mixins.tacz.json} 漏配 {@code client.CreativeContainerAccessor}）时
     * {@code instanceof} 恰好为 {@code false}，这里返回 {@code null}，
     * {@link CreativePackFilters} 自动退回旧反射路径 —— <b>只降级、不崩溃</b>。
     */
    private static CreativePackFilters.ContainerAccess createAccess(GuiContainerCreative screen) {
        if (screen == null || !(screen.inventorySlots instanceof CreativeContainerAccessor accessor)) {
            return null;
        }
        return new CreativePackFilterContainerAccess(accessor);
    }
}
