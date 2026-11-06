package com.tacz.guns.resource.pojo.data.attachment;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.google.gson.annotations.SerializedName;

public class EffectData {

    @SerializedName("id")
    @Nullable
    private ResourceLocation effectId;

    @SerializedName("time")
    private int time = 0;

    @SerializedName("amplifier")
    private int amplifier = 0;

    @SerializedName("hide_particles")
    private boolean hideParticles = false;

    @Nullable
    public ResourceLocation getEffectId() {
        return effectId;
    }

    public int getTime() {
        return time;
    }

    public int getAmplifier() {
        return amplifier;
    }

    public boolean isHideParticles() {
        return hideParticles;
    }
}
