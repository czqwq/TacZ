package com.tacz.guns.api.item.gun;

import java.util.Locale;

import javax.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public enum FireMode {

    /**
     * 全自动
     */
    @SerializedName("auto")
    AUTO,
    /**
     * 半自动
     */
    @SerializedName("semi")
    SEMI,
    /**
     * 多连发
     */
    @SerializedName("burst")
    BURST,
    /**
     * 未知的其他情况？
     */
    @SerializedName("unknown")
    UNKNOWN;

    public static FireMode fromName(@Nullable String name) {
        if (name == null) {
            return UNKNOWN;
        }
        String n = name.toUpperCase(Locale.ENGLISH);
        return switch (n) {
            case "AUTO" -> AUTO;
            case "SEMI" -> SEMI;
            case "BURST" -> BURST;
            default -> UNKNOWN;
        };
    }
}
