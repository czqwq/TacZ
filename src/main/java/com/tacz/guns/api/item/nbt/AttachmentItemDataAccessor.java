package com.tacz.guns.api.item.nbt;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.util.ResourceLocationHelper;

public interface AttachmentItemDataAccessor extends IAttachment {

    String ATTACHMENT_ID_TAG = "AttachmentId";
    String SKIN_ID_TAG = "Skin";
    String ZOOM_NUMBER_TAG = "ZoomNumber";
    String LASER_COLOR_TAG = "LaserColor";

    // 仅检查给定的 NBTTagCompound 是否具有配件 ID ，不校验其是否存在
    static boolean isAttachmentLike(NBTTagCompound tag) {
        return tag.hasKey(ATTACHMENT_ID_TAG, Constants.NBT.TAG_STRING);
    }

    @Nonnull
    static ResourceLocation getAttachmentIdFromTag(@Nullable NBTTagCompound nbt) {
        if (nbt == null) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID;
        }
        if (isAttachmentLike(nbt)) {
            ResourceLocation attachmentId = ResourceLocationHelper.tryParse(nbt.getString(ATTACHMENT_ID_TAG));
            return Objects.requireNonNullElse(attachmentId, DefaultAssets.EMPTY_ATTACHMENT_ID);
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    static int getZoomNumberFromTag(@Nullable NBTTagCompound nbt) {
        if (nbt == null) {
            return 0;
        }
        if (nbt.hasKey(ZOOM_NUMBER_TAG, Constants.NBT.TAG_INT)) {
            return nbt.getInteger(ZOOM_NUMBER_TAG);
        }
        return 0;
    }

    static void setZoomNumberToTag(NBTTagCompound nbt, int zoomNumber) {
        nbt.setInteger(ZOOM_NUMBER_TAG, zoomNumber);
    }

    @Override
    @Nonnull
    default ResourceLocation getAttachmentId(ItemStack attachmentStack) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(attachmentStack);
        return getAttachmentIdFromTag(nbt);
    }

    @Override
    default void setAttachmentId(ItemStack attachmentStack, @Nullable ResourceLocation attachmentId) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(attachmentStack);
        if (attachmentId != null) {
            nbt.setString(ATTACHMENT_ID_TAG, attachmentId.toString());
        }
    }

    @Override
    @Nullable
    default ResourceLocation getSkinId(ItemStack attachmentStack) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(attachmentStack);
        if (nbt.hasKey(SKIN_ID_TAG, Constants.NBT.TAG_STRING)) {
            return ResourceLocationHelper.tryParse(nbt.getString(SKIN_ID_TAG));
        }
        return null;
    }

    @Override
    default void setSkinId(ItemStack attachmentStack, @Nullable ResourceLocation skinId) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(attachmentStack);
        if (skinId != null) {
            nbt.setString(SKIN_ID_TAG, skinId.toString());
        } else {
            nbt.removeTag(SKIN_ID_TAG);
        }
    }

    @Override
    default int getZoomNumber(ItemStack attachmentStack) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(attachmentStack);
        return getZoomNumberFromTag(nbt);
    }

    @Override
    default void setZoomNumber(ItemStack attachmentStack, int zoomNumber) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(attachmentStack);
        setZoomNumberToTag(nbt, zoomNumber);
    }

    @Override
    default boolean hasCustomLaserColor(ItemStack attachmentStack) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(attachmentStack);
        return nbt.hasKey(LASER_COLOR_TAG, Constants.NBT.TAG_INT);
    }

    @Override
    default int getLaserColor(ItemStack attachmentStack) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(attachmentStack);
        if (!hasCustomLaserColor(attachmentStack)) {
            return 0xFF0000;
        }
        return nbt.getInteger(LASER_COLOR_TAG);
    }

    @Override
    default void setLaserColor(ItemStack attachmentStack, int color) {
        NBTTagCompound nbt = ItemStackNbtHelper.getOrCreateTag(attachmentStack);
        nbt.setInteger(LASER_COLOR_TAG, color);
    }
}
