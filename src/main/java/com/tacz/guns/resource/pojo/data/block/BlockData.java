package com.tacz.guns.resource.pojo.data.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.GunMod;

public class BlockData {

    /** {@link #filter} 的默认值（与字段初始化值同义；单独抽成常量避免 getter 里每次 new）。 */
    private static final ResourceLocation DEFAULT_FILTER = new ResourceLocation(GunMod.MOD_ID, "default");

    @NotNull
    @SerializedName("filter")
    private ResourceLocation filter = DEFAULT_FILTER;

    @SerializedName("tabs")
    private List<TabConfig> tabs = new ArrayList<>();

    /**
     * <b>为什么 getter 还要判空</b>（移植特有防御，记差异）：字段上写了 {@code = DEFAULT_FILTER}，
     * 但 gson 在 json 里出现<b>显式 null</b>（{@code "filter": null}）时，会把这个默认值**覆盖成 null**
     * （{@code ReflectiveTypeAdapterFactory} 读到 {@code JsonToken.NULL} 就 {@code field.set(obj, null)}，
     * 2.2.4 与 2.10 行为一致）。而 {@code CommonBlockIndex#checkData:48-50} 会拿它去查
     * {@code getRecipeFilter(...)}，null 会让那条 {@code Preconditions} 以「没有对应数据文件」的误导性信息失败。
     * 判空后回落默认值，语义与「字段没写」完全一致。
     */
    @NotNull
    public ResourceLocation getFilter() {
        return filter == null ? DEFAULT_FILTER : filter;
    }

    /**
     * <b>为什么 getter 还要判空</b>（移植特有防御，记差异）：同 {@link #getFilter()}，
     * json 显式 {@code "tabs": null} 会把 {@code new ArrayList<>()} 覆盖成 null，
     * 于是这里的 {@code tabs.isEmpty()} 直接 NPE。这条 getter 在
     * {@code GunSmithTableScreen#classifyRecipes} 的**每帧**路径上，而
     * {@code CommonBlockIndex#checkData} 并不校验 tabs，所以 NPE 会一直带到渲染期。
     */
    @NotNull
    public List<TabConfig> getTabs() {
        return tabs == null || tabs.isEmpty() ? TabConfig.DEFAULT_TABS : tabs;
    }
}
