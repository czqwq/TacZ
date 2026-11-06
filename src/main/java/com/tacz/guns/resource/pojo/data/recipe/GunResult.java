package com.tacz.guns.resource.pojo.data.recipe;

import java.util.EnumMap;

import net.minecraft.util.ResourceLocation;

import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.item.attachment.AttachmentType;

public class GunResult {

    @SerializedName("ammo_count")
    private int ammoCount = 0;

    @SerializedName("attachments")
    private EnumMap<AttachmentType, ResourceLocation> attachments = Maps.newEnumMap(AttachmentType.class);

    public int getAmmoCount() {
        return ammoCount;
    }

    public EnumMap<AttachmentType, ResourceLocation> getAttachments() {
        return attachments;
    }
}
