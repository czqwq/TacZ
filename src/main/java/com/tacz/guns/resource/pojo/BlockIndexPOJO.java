package com.tacz.guns.resource.pojo;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.GunMod;

public class BlockIndexPOJO {

    /** {@link #id} 的默认值（与字段初始化值同义；单独抽成常量避免 getter 里每次 new）。 */
    private static final ResourceLocation DEFAULT_ID = new ResourceLocation(GunMod.MOD_ID, "gun_smith_table");

    @SerializedName("name")
    private String name;

    @SerializedName("display")
    private ResourceLocation display;

    @SerializedName("data")
    private ResourceLocation data;

    @SerializedName("id")
    private ResourceLocation id = DEFAULT_ID;

    @SerializedName("stack_size")
    private int stackSize;

    @SerializedName("tooltip")
    @Nullable
    private String tooltip;

    public String getName() {
        return name;
    }

    /**
     * <b>为什么 getter 还要判空</b>（移植特有防御，记差异）：json 显式写 {@code "id": null} 时 gson 会把
     * 字段初始化值 {@link #DEFAULT_ID} 覆盖成 null，而
     * {@code CommonBlockIndex#checkIndex:31-35} 直接对它调 {@code getResourceDomain()} →
     * <b>加载期 NPE</b>；该 NPE 不在 {@code JsonDataManager#apply} 的
     * {@code catch (JsonParseException | IllegalArgumentException)} 覆盖范围内，会一路冒到
     * {@code CommonAssetsManager#reload()} → 「Failed to reload gun pack data」+ 重新抛出 → 服务端起不来。
     * 判空后回落默认值，语义与「字段没写」一致。
     */
    public ResourceLocation getId() {
        return id == null ? DEFAULT_ID : id;
    }

    public ResourceLocation getDisplay() {
        return display;
    }

    public int getStackSize() {
        return stackSize;
    }

    public ResourceLocation getData() {
        return data;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }
}
