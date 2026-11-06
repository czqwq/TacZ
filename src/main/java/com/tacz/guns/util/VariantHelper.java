package com.tacz.guns.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;
import com.tacz.guns.api.util.ResourceLocationHelper;

/**
 * 配件「变体（Variant）」NBT 的读写工具。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.nbt.CompoundTag/Tag} → {@link NBTTagCompound}/{@code NBTBase}：
 * {@code putString → setString}、{@code getString → getString}、
 * {@code contains(key, Tag.TAG_STRING) → hasKey(key, 8)}、{@code remove → removeTag}。</li>
 * <li>{@code net.minecraft.world.item.ItemStack} → {@link ItemStack}。</li>
 * <li>{@code ItemStack.EMPTY} → <b>{@code null}</b>（本工程统一约定），判空统一走
 * {@link ItemStackNbtHelper#isEmpty(ItemStack)}；{@code getOrCreateTag()/getTag()}
 * 走 {@link ItemStackNbtHelper#getOrCreateTag}/{@link ItemStackNbtHelper#getTag}。</li>
 * <li>{@code ResourceLocation.tryParse} 1.7.10 不存在 → {@link ResourceLocationHelper#tryParse}。</li>
 * <li>NBT key 字符串 {@code "Variant"} <b>一个字都没改</b>，保证存档兼容。</li>
 * </ul>
 */
public final class VariantHelper {

    private static final String VARIANT_TAG = "Variant";

    private VariantHelper() {}

    public static void setVariant(ItemStack attachment, ResourceLocation variantId) {
        if (ItemStackNbtHelper.isEmpty(attachment) || variantId == null) {
            return;
        }
        ItemStackNbtHelper.getOrCreateTag(attachment)
            .setString(VARIANT_TAG, variantId.toString());
    }

    @Nullable
    public static ResourceLocation getVariant(ItemStack attachment) {
        if (ItemStackNbtHelper.isEmpty(attachment)) {
            return null;
        }
        NBTTagCompound tag = ItemStackNbtHelper.getTag(attachment);
        // 1.7.10 NBTTagCompound 没有「按类型 contains」，hasKey(key, type) 的 8 即 TAG_String。
        if (tag == null || !tag.hasKey(VARIANT_TAG, 8)) {
            return null;
        }
        return ResourceLocationHelper.tryParse(tag.getString(VARIANT_TAG));
    }

    public static boolean hasVariant(ItemStack attachment) {
        return getVariant(attachment) != null;
    }

    public static void removeVariant(ItemStack attachment) {
        if (ItemStackNbtHelper.isEmpty(attachment)) {
            return;
        }
        NBTTagCompound tag = ItemStackNbtHelper.getTag(attachment);
        if (tag != null) {
            tag.removeTag(VARIANT_TAG);
        }
    }
}
