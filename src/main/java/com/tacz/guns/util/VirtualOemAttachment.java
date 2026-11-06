package com.tacz.guns.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;
import com.tacz.guns.resource.pojo.data.gun.BuiltInAttachment;

/**
 * 「虚拟 OEM 配件」判定：枪械 built_in_attachments 里列出的、可以免改装直接挂上的配件，
 * 以及它们的 NBT 标记。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}（1.7.10 同名类）。</li>
 * <li>{@code net.minecraft.world.item.ItemStack} → {@link ItemStack}；
 * {@code ItemStack.EMPTY} → {@code null}；{@code getOrCreateTag()/getTag()} 走
 * {@link ItemStackNbtHelper}。</li>
 * <li>{@code CompoundTag.contains(key, Tag.TAG_BYTE) → NBTTagCompound.hasKey(key, 1)}（1 = TAG_Byte）；
 * {@code putBoolean → setBoolean}，{@code getBoolean} 同名。</li>
 * <li>上游的 {@code var} 局部量都是 {@code BuiltInAttachment}，1.7.10 侧写成显式类型以降低降级风险。</li>
 * <li>NBT key 字符串 {@code "VirtualOemAttachment"} <b>一个字都没改</b>。</li>
 * </ul>
 */
public final class VirtualOemAttachment {

    private static final String VIRTUAL_OEM_TAG = "VirtualOemAttachment";

    private VirtualOemAttachment() {}

    public static boolean isAllowedVirtualOem(ResourceLocation gunId, ResourceLocation attachmentId,
        AttachmentType type) {
        return TimelessAPI.getCommonGunIndex(gunId)
            .map(index -> {
                BuiltInAttachment builtInAttachment = index.getGunData()
                    .getBuiltInAttachments()
                    .get(type);
                return builtInAttachment != null && builtInAttachment.getAttachments()
                    .contains(attachmentId);
            })
            .orElse(false);
    }

    public static boolean shouldShowIcon(ResourceLocation gunId, ResourceLocation attachmentId, AttachmentType type) {
        return TimelessAPI.getCommonGunIndex(gunId)
            .map(index -> {
                BuiltInAttachment builtInAttachment = index.getGunData()
                    .getBuiltInAttachments()
                    .get(type);
                // 回退显示的配件已在枪上，不重复列入改装选项。
                return builtInAttachment != null && builtInAttachment.getAttachments()
                    .contains(attachmentId)
                    && builtInAttachment.isShowIcon()
                    && !attachmentId.equals(builtInAttachment.getDisplayAttachment());
            })
            .orElse(false);
    }

    public static boolean isBuiltinAttachment(ResourceLocation attachmentId) {
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
            .map(
                index -> index.getPojo()
                    .isBuiltinAttachment())
            .orElse(false);
    }

    public static void mark(ItemStack attachmentStack) {
        ItemStackNbtHelper.getOrCreateTag(attachmentStack)
            .setBoolean(VIRTUAL_OEM_TAG, true);
    }

    public static boolean isMarked(ItemStack attachmentStack) {
        NBTTagCompound tag = ItemStackNbtHelper.getTag(attachmentStack);
        return tag != null && tag.hasKey(VIRTUAL_OEM_TAG, 1) && tag.getBoolean(VIRTUAL_OEM_TAG);
    }
}
