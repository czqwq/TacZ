package com.tacz.guns.util.datafixer;

import static com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor.ATTACHMENT_ID_TAG;
import static com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor.getAttachmentIdFromTag;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.google.common.collect.ImmutableMap;
import com.tacz.guns.api.DefaultAssets;

/**
 * 旧版配件 id 的存档修复表（{@code muzzle_silence_*} → {@code muzzle_silencer_*}）。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.nbt.CompoundTag} → {@link NBTTagCompound}；
 * {@code tag.putString(key, value) → tag.setString(key, value)}。</li>
 * <li>{@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}（1.7.10 同名类）。</li>
 * <li>上游是 {@code import static ...*;} 加一条重复的显式 static import；
 * 1.7.10 侧改为两条明确的 static import（{@code ATTACHMENT_ID_TAG} 与
 * {@code getAttachmentIdFromTag}），<b>引用的常量与字符串一字未改</b>。</li>
 * <li>1.7.10 的 {@code getAttachmentIdFromTag} 已被移植为 {@code @Nonnull} + 永不为 null，
 * 因此 {@code old.equals(...)} 不需要额外判空，与上游一致。</li>
 * </ul>
 */
public final class AttachmentIdFix {

    private AttachmentIdFix() {}

    public static final Map<ResourceLocation, ResourceLocation> OLD_TO_NEW;
    static {
        OLD_TO_NEW = ImmutableMap.<ResourceLocation, ResourceLocation>builder()
            .put(
                new ResourceLocation("tacz", "muzzle_silence_knight_qd"),
                new ResourceLocation("tacz", "muzzle_silencer_knight_qd"))
            .put(
                new ResourceLocation("tacz", "muzzle_silence_mirage"),
                new ResourceLocation("tacz", "muzzle_silencer_mirage"))
            .put(
                new ResourceLocation("tacz", "muzzle_silence_phantom_s1"),
                new ResourceLocation("tacz", "muzzle_silencer_phantom_s1"))
            .put(
                new ResourceLocation("tacz", "muzzle_silence_ptilopsis"),
                new ResourceLocation("tacz", "muzzle_silencer_ptilopsis"))
            .put(
                new ResourceLocation("tacz", "muzzle_silence_ursus"),
                new ResourceLocation("tacz", "muzzle_silencer_ursus"))
            .put(
                new ResourceLocation("tacz", "muzzle_silence_vulture"),
                new ResourceLocation("tacz", "muzzle_silencer_vulture"))
            .build();
    }

    // 预留 boolean 返回值用于未来使用，"尽可能避免使用 void ，除非这个操作不能提供任何有用的信息"
    public static boolean updateAttachmentIdInTag(NBTTagCompound tag) {
        ResourceLocation old = getAttachmentIdFromTag(tag);
        if (!old.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            ResourceLocation fixed = updateAttachmentId(old);
            if (!old.equals(fixed)) {
                tag.setString(ATTACHMENT_ID_TAG, fixed.toString());
                return true;
            }
        }
        return false;
    }

    public static ResourceLocation updateAttachmentId(ResourceLocation old) {
        return OLD_TO_NEW.getOrDefault(old, old);
    }
}
