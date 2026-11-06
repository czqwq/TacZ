package com.tacz.guns.client.resource.pojo.display.attachment;

import net.minecraft.util.ResourceLocation;

import com.google.gson.annotations.SerializedName;

public class AttachmentLod {

    @SerializedName("model")
    private ResourceLocation modelLocation;
    @SerializedName("texture")
    protected ResourceLocation modelTexture;

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public ResourceLocation getModelTexture() {
        return modelTexture;
    }
}
