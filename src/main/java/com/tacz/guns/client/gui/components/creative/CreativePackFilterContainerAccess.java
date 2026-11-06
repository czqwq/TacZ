package com.tacz.guns.client.gui.components.creative;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.tacz.guns.client.gui.components.CreativePackFilters;
import com.tacz.guns.mixin.client.CreativeContainerAccessor;

/**
 * {@link CreativePackFilters.ContainerAccess} 的实现（<b>普通顶层类，不是 Mixin</b>）。
 *
 * <p>
 * 为什么它是顶层类、又为什么放在 {@code com.tacz.guns.client.gui.components.creative} 而不是
 * {@code com.tacz.guns.mixin.client}，见 {@link CreativePackFilterHooks} 的类 javadoc
 * （两条独立硬约束：① mixin 类内禁止嵌套类 → 必须提成顶层；② 非 {@code @Mixin} 类不得留在
 * {@code com.tacz.guns.mixin.**} → 否则 {@code IllegalClassLoadError}）。
 *
 * <p>
 * 职责很窄：把 {@link CreativeContainerAccessor}（{@code @Mixin(targets =
 * "…GuiContainerCreative$ContainerCreative")} + {@code @Accessor("itemList")} /
 * {@code @Invoker("scrollTo")}）包成 {@link CreativePackFilters.ContainerAccess}，
 * 让 {@code client/gui} 侧不必接触包私有内部类、也不必按 MCP 名反射。
 *
 * <p>
 * {@code public}（含构造器与方法）是<b>必须</b>的：本类由 {@link CreativePackFilterHooks} 实例化，
 * 而那个调用点在 Mixin 合并之后位于 {@code net.minecraft.client.gui.inventory} 包内的
 * {@code GuiContainerCreative} 里 —— 包内可见会抛 {@code IllegalAccessError}。
 */
public final class CreativePackFilterContainerAccess implements CreativePackFilters.ContainerAccess {

    private final CreativeContainerAccessor accessor;

    public CreativePackFilterContainerAccess(CreativeContainerAccessor accessor) {
        this.accessor = accessor;
    }

    /** 对应 {@code ContainerCreative#itemList}（{@code GuiContainerCreative.java:1006}）。 */
    @Override
    @Nonnull
    public List<ItemStack> itemList() {
        return this.accessor.tacz$getItemList();
    }

    /** 对应 {@code ContainerCreative#scrollTo(float)}（{@code GuiContainerCreative.java:1038}）。 */
    @Override
    public void scrollTo(float value) {
        this.accessor.tacz$scrollTo(value);
    }
}
