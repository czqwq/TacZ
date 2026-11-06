package com.tacz.guns.util;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.ItemStackNbtHelper;
import com.tacz.guns.api.util.ResourceLocationHelper;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.index.CommonSlotAdapterIndex;

/**
 * 「槽位适配器（slot adapter）」的 NBT 读写与可用性判定。
 * 槽位适配器允许某个配件槽在特定枪上转接别的配件。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.nbt.CompoundTag/Tag} → {@link NBTTagCompound}/{@code NBTBase}：
 * {@code getCompound → getCompoundTag}、{@code put(k, v) → setTag(k, v)}、
 * {@code contains(k, type) → hasKey(k, type)}、{@code adapters.isEmpty() → hasNoTags()}、
 * 类型常量 {@code Tag.TAG_STRING/TAG_COMPOUND} → {@link Constants.NBT}。</li>
 * <li>{@code net.minecraft.world.item.ItemStack} → {@link ItemStack}；{@code stack.isEmpty()} →
 * {@link ItemStackNbtHelper#isEmpty}；{@code getOrCreateTag/getTag} 走 {@link ItemStackNbtHelper}。</li>
 * <li>{@code ResourceLocation.tryParse} 不存在 → {@link ResourceLocationHelper#tryParse}。</li>
 * <li>{@code List.of()} → {@link Collections#emptyList()}；
 * {@code stream().map(Map.entry(...)).toList()} → 直接构建 {@link LinkedHashMap}
 * （1.7.10 侧不需要中间 List，顺序语义一致）。</li>
 * <li><b>跨包契约（已按网络包 {@code ClientMessageSetSlotAdapter} 的实际调用定稿）</b>：
 * {@link #getAvailableSlotAdapters(ItemStack, AttachmentType)} 返回
 * {@code Map<ResourceLocation, SlotAdapter>}，{@link SlotAdapter#allowsAttachment(ResourceLocation)}
 * 即上游 {@code CommonSlotAdapterIndex#allowsAttachment} 的同名转发。</li>
 * <li>额外提供 {@link #getAvailableSlotAdapterIndices(ItemStack, AttachmentType)}（返回
 * {@code List<Map.Entry<ResourceLocation, CommonSlotAdapterIndex>>}），供客户端改装界面
 * （上游 {@code GunRefitScreen} 直接遍历 index）使用；1.7.10 侧把「界面需要 index、
 * 网络包需要 SlotAdapter」两件事分开，语义与上游一致。</li>
 * <li><b>1.7.10 新增判空</b>：{@code CommonAssetsManager.get()} 在客户端未同步时可能为 null。</li>
 * <li>NBT key 字符串 {@code "SlotAdapters"}、标签前缀 {@code "#"}、
 * 适配器标签路径前缀 {@code "adapter/"} <b>一个字都没改</b>。</li>
 * </ul>
 */
public final class SlotAdapterHelper {

    private static final String SLOT_ADAPTERS_TAG = "SlotAdapters";
    private static final String TAG_PREFIX = "#";
    private static final String ADAPTER_TAG_PATH_PREFIX = "adapter/";

    private SlotAdapterHelper() {}

    /**
     * 一个可用的槽位适配器。1.7.10 侧是对 {@link CommonSlotAdapterIndex} 的轻量包装，
     * 只暴露网络包需要的 {@link #allowsAttachment(ResourceLocation)}。
     */
    public static final class SlotAdapter {

        private final CommonSlotAdapterIndex index;

        public SlotAdapter(CommonSlotAdapterIndex index) {
            this.index = index;
        }

        /** 该适配器是否接受给定配件 id（等价上游 {@code CommonSlotAdapterIndex#allowsAttachment}）。 */
        public boolean allowsAttachment(ResourceLocation attachmentId) {
            return index != null && index.allowsAttachment(attachmentId);
        }

        public CommonSlotAdapterIndex getIndex() {
            return index;
        }

        public AttachmentType getSlot() {
            return index == null ? AttachmentType.NONE : index.getSlot();
        }

        public int getSort() {
            return index == null ? 0 : index.getSort();
        }
    }

    public static void setSlotAdapter(ItemStack holder, AttachmentType type, ResourceLocation adapterId) {
        if (ItemStackNbtHelper.isEmpty(holder) || type == null || adapterId == null) {
            return;
        }
        NBTTagCompound root = ItemStackNbtHelper.getOrCreateTag(holder);
        NBTTagCompound adapters = root.getCompoundTag(SLOT_ADAPTERS_TAG);
        adapters.setString(slotKey(type), adapterId.toString());
        root.setTag(SLOT_ADAPTERS_TAG, adapters);
    }

    @Nullable
    public static ResourceLocation getSlotAdapter(ItemStack holder, AttachmentType type) {
        if (ItemStackNbtHelper.isEmpty(holder) || type == null) {
            return null;
        }
        NBTTagCompound root = ItemStackNbtHelper.getTag(holder);
        if (root == null || !root.hasKey(SLOT_ADAPTERS_TAG, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        NBTTagCompound adapters = root.getCompoundTag(SLOT_ADAPTERS_TAG);
        String key = slotKey(type);
        if (!adapters.hasKey(key, Constants.NBT.TAG_STRING)) {
            return null;
        }
        return ResourceLocationHelper.tryParse(adapters.getString(key));
    }

    public static void removeSlotAdapter(ItemStack holder, AttachmentType type) {
        if (ItemStackNbtHelper.isEmpty(holder) || type == null) {
            return;
        }
        NBTTagCompound root = ItemStackNbtHelper.getTag(holder);
        if (root == null || !root.hasKey(SLOT_ADAPTERS_TAG, Constants.NBT.TAG_COMPOUND)) {
            return;
        }
        NBTTagCompound adapters = root.getCompoundTag(SLOT_ADAPTERS_TAG);
        adapters.removeTag(slotKey(type));
        if (adapters.hasNoTags()) {
            root.removeTag(SLOT_ADAPTERS_TAG);
        } else {
            root.setTag(SLOT_ADAPTERS_TAG, adapters);
        }
    }

    public static boolean allowsDirectAttachment(ItemStack gun, ResourceLocation attachmentId) {
        ResourceLocation hostGunId = getHostGunId(gun);
        return hostGunId != null && attachmentId != null && AllowAttachmentTagMatcher.match(hostGunId, attachmentId);
    }

    public static Map<ResourceLocation, SlotAdapter> getAvailableSlotAdapters(ItemStack gun,
        AttachmentType attachmentType) {
        List<Map.Entry<ResourceLocation, CommonSlotAdapterIndex>> entries = getAvailableSlotAdapterIndices(
            gun,
            attachmentType);
        if (entries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<ResourceLocation, SlotAdapter> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, CommonSlotAdapterIndex> entry : entries) {
            result.put(entry.getKey(), new SlotAdapter(entry.getValue()));
        }
        return result;
    }

    /**
     * 客户端改装界面用：直接拿到可用的 {@link CommonSlotAdapterIndex} 列表（顺序稳定）。
     */
    public static List<Map.Entry<ResourceLocation, CommonSlotAdapterIndex>> getAvailableSlotAdapterIndices(
        ItemStack gun, AttachmentType attachmentType) {
        ResourceLocation hostGunId = getHostGunId(gun);
        if (hostGunId == null || attachmentType == null || attachmentType == AttachmentType.NONE) {
            return Collections.emptyList();
        }
        ICommonResourceProvider provider = CommonAssetsManager.get();
        if (provider == null) {
            return Collections.emptyList();
        }
        Set<String> allowList = provider.getAllowAttachmentTags(hostGunId);
        if (allowList == null || allowList.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashMap<ResourceLocation, CommonSlotAdapterIndex> candidates = new LinkedHashMap<>();
        Set<ResourceLocation> visitedTags = new HashSet<>();
        for (String value : allowList) {
            ResourceLocation tagId = parseTag(value);
            if (tagId == null || !tagId.getResourcePath()
                .startsWith(ADAPTER_TAG_PATH_PREFIX)) {
                continue;
            }
            expandAdapterTag(provider, tagId, attachmentType, visitedTags, candidates);
        }
        List<Map.Entry<ResourceLocation, CommonSlotAdapterIndex>> result = new ArrayList<>(candidates.size());
        for (Map.Entry<ResourceLocation, CommonSlotAdapterIndex> entry : candidates.entrySet()) {
            result.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean isSlotAdapterAvailable(ItemStack gun, AttachmentType attachmentType,
        ResourceLocation adapterId) {
        if (adapterId == null) {
            return false;
        }
        return getAvailableSlotAdapters(gun, attachmentType).containsKey(adapterId);
    }

    @Nullable
    public static ResourceLocation getSelectedAvailableSlotAdapter(ItemStack gun, AttachmentType attachmentType) {
        ResourceLocation selected = getSlotAdapter(gun, attachmentType);
        if (selected == null) {
            return null;
        }
        return isSlotAdapterAvailable(gun, attachmentType, selected) ? selected : null;
    }

    public static boolean allowsAttachment(ItemStack gun, AttachmentType attachmentType,
        ResourceLocation attachmentId) {
        if (attachmentType == null || attachmentType == AttachmentType.NONE || attachmentId == null) {
            return false;
        }
        ResourceLocation selected = getSelectedAvailableSlotAdapter(gun, attachmentType);
        if (selected == null) {
            return allowsDirectAttachment(gun, attachmentId);
        }
        return TimelessAPI.getCommonSlotAdapterIndex(selected)
            .map(index -> index.allowsAttachment(attachmentId))
            .orElse(false);
    }

    @Nullable
    public static ResourceLocation getEffectiveSlotAdapter(ItemStack gun, AttachmentType attachmentType,
        ResourceLocation attachmentId) {
        ResourceLocation selected = getSelectedAvailableSlotAdapter(gun, attachmentType);
        if (selected == null || attachmentId == null) {
            return null;
        }
        return TimelessAPI.getCommonSlotAdapterIndex(selected)
            .filter(index -> index.allowsAttachment(attachmentId))
            .map(index -> selected)
            .orElse(null);
    }

    public static boolean allowsSlotAdapters(ItemStack holder) {
        return getHostGunId(holder) != null;
    }

    private static void expandAdapterTag(ICommonResourceProvider provider, ResourceLocation tagId,
        AttachmentType attachmentType, Set<ResourceLocation> visitedTags,
        LinkedHashMap<ResourceLocation, CommonSlotAdapterIndex> candidates) {
        if (!visitedTags.add(tagId)) {
            return;
        }
        Set<String> content = provider.getAttachmentTags(tagId);
        if (content == null || content.isEmpty()) {
            return;
        }
        for (String value : content) {
            ResourceLocation nestedTag = parseTag(value);
            if (nestedTag != null) {
                expandAdapterTag(provider, nestedTag, attachmentType, visitedTags, candidates);
                continue;
            }
            ResourceLocation adapterId = ResourceLocationHelper.tryParse(value);
            if (adapterId == null || candidates.containsKey(adapterId)) {
                continue;
            }
            TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                .filter(index -> index.getSlot() == attachmentType)
                .ifPresent(index -> candidates.put(adapterId, index));
        }
    }

    @Nullable
    private static ResourceLocation parseTag(String value) {
        if (value == null || !value.startsWith(TAG_PREFIX)) {
            return null;
        }
        return ResourceLocationHelper.tryParse(value.substring(TAG_PREFIX.length()));
    }

    private static String slotKey(AttachmentType type) {
        return type.name();
    }

    @Nullable
    private static ResourceLocation getHostGunId(ItemStack holder) {
        IGun gun = IGun.getIGunOrNull(holder);
        return gun == null ? null : gun.getGunId(holder);
    }
}
