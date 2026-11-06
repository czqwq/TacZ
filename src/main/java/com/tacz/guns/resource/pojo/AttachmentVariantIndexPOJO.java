package com.tacz.guns.resource.pojo;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.google.gson.annotations.SerializedName;

public class AttachmentVariantIndexPOJO {

    @SerializedName("name")
    private String name;

    @SerializedName("tooltip")
    @Nullable
    private String tooltip;

    @SerializedName("display")
    private ResourceLocation display;

    @SerializedName("data")
    private ResourceLocation data;

    @SerializedName("targets")
    private List<ResourceLocation> targets = new ArrayList<>();

    @SerializedName("target_tags")
    private List<ResourceLocation> targetTags = new ArrayList<>();

    @SerializedName("sort")
    private int sort;

    public String getName() {
        return name;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }

    public ResourceLocation getDisplay() {
        return display;
    }

    public ResourceLocation getData() {
        return data;
    }

    public List<ResourceLocation> getTargets() {
        return targets == null ? List.of() : targets;
    }

    public List<ResourceLocation> getTargetTags() {
        return targetTags == null ? List.of() : targetTags;
    }

    public int getSort() {
        return sort;
    }
}
