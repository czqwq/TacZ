package com.tacz.guns.resource.pojo;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.google.gson.annotations.SerializedName;

public class AmmoIndexPOJO {

    @SerializedName("name")
    private String name;

    @SerializedName("display")
    private ResourceLocation display;

    @SerializedName("stack_size")
    private int stackSize;

    @SerializedName("tooltip")
    @Nullable
    private String tooltip;

    @SerializedName("sort")
    private int sort;

    public String getName() {
        return name;
    }

    public ResourceLocation getDisplay() {
        return display;
    }

    public int getStackSize() {
        return stackSize;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }

    public int getSort() {
        return sort;
    }
}
