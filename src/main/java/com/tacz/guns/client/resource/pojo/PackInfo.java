package com.tacz.guns.client.resource.pojo;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;

public class PackInfo {

    @SerializedName("version")
    private String version = "1.0.0";

    @SerializedName("name")
    private String name = "custom.tacz.error.no_name";

    @SerializedName("desc")
    private String description = "";

    @SerializedName("license")
    private String license = "All Rights Reserved";

    @SerializedName("authors")
    private List<String> authors = Lists.newArrayList();

    @SerializedName("date")
    private String date = "1919-08-10";

    @SerializedName("url")
    private String url;

    @SerializedName("logo")
    private String logo;

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLicense() {
        return license;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public String getDate() {
        return date;
    }

    public String getUrl() {
        return url;
    }

    /** Resolves an optional texture path relative to this content pack's namespace. */
    @Nullable
    public ResourceLocation getLogo(String namespace) {
        if (logo == null || logo.isBlank()) {
            return null;
        }
        return tryParse(logo.contains(":") ? logo : namespace + ":" + logo);
    }

    /**
     * TODO(port): 上游 PackInfo.java:69 用 {@code ResourceLocation.tryParse}（1.20 API）。
     * 1.7.10 没有 tryParse，约定用 try/catch 包 {@code new ResourceLocation(s)}，解析失败返回 null。
     */
    @Nullable
    private static ResourceLocation tryParse(@Nullable String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(location);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
