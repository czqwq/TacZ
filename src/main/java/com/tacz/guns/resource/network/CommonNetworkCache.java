package com.tacz.guns.resource.network;

import java.util.*;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.*;
import com.tacz.guns.resource.manager.AttachmentDataManager;
import com.tacz.guns.resource.manager.GunDataManager;
import com.tacz.guns.resource.manager.GunSmithTableRecipeManager;
import com.tacz.guns.resource.pojo.AttachmentVariantIndexPOJO;
import com.tacz.guns.resource.pojo.SlotAdapterIndexPOJO;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.attachment.SlotAdapterData;
import com.tacz.guns.resource.pojo.data.attachment.VariantData;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;

/**
 * 网络位置的缓存。
 *
 * <p>
 * 用于存储从服务端下发（{@code ServerMessageSyncGunPack}）的网络数据。多人游戏时
 * {@link com.tacz.guns.resource.CommonAssetsManager#get()} 会切到本实现。
 *
 * <p>
 * <b>1.7.10 修正</b>：本段 javadoc 原为 GBK/UTF-8 乱码（上游原文是「网络位置的缓存 / 用于存储从网络获取的数据」），
 * 按上游 {@code tmp/TACZ-1.20.1/.../resource/network/CommonNetworkCache.java} 的类注释还原。
 */
public enum CommonNetworkCache implements ICommonResourceProvider {

    INSTANCE;

    public Map<ResourceLocation, GunData> gunData = new HashMap<>();
    public Map<ResourceLocation, AttachmentData> attachmentData = new HashMap<>();
    public Map<ResourceLocation, VariantData> attachmentVariantData = new HashMap<>();
    public Map<ResourceLocation, SlotAdapterData> slotAdapterData = new HashMap<>();
    public Map<ResourceLocation, RecipeFilter> recipeFilter = new HashMap<>();
    public Map<ResourceLocation, BlockData> blockData = new HashMap<>();
    public Map<ResourceLocation, CommonGunIndex> gunIndex = new HashMap<>();
    public Map<ResourceLocation, CommonAmmoIndex> ammoIndex = new HashMap<>();
    public Map<ResourceLocation, CommonAttachmentIndex> attachmentIndex = new HashMap<>();
    public Map<ResourceLocation, CommonAttachmentVariantIndex> attachmentVariantIndex = new HashMap<>();
    public Map<ResourceLocation, CommonSlotAdapterIndex> slotAdapterIndex = new HashMap<>();
    public Map<ResourceLocation, CommonBlockIndex> blockIndex = new HashMap<>();
    public Map<ResourceLocation, Set<String>> attachmentTags = new HashMap<>();
    public Map<ResourceLocation, Set<String>> allowAttachmentTags = new HashMap<>();

    @Nullable
    @Override
    public GunData getGunData(ResourceLocation id) {
        return gunData.get(id);
    }

    @Nullable
    @Override
    public AttachmentData getAttachmentData(ResourceLocation attachmentId) {
        return attachmentData.get(attachmentId);
    }

    @Nullable
    @Override
    public VariantData getAttachmentVariantData(ResourceLocation variantDataId) {
        return attachmentVariantData.get(variantDataId);
    }

    @Nullable
    @Override
    public SlotAdapterData getSlotAdapterData(ResourceLocation adapterDataId) {
        return slotAdapterData.get(adapterDataId);
    }

    @Nullable
    @Override
    public BlockData getBlockData(ResourceLocation id) {
        return blockData.get(id);
    }

    @Nullable
    @Override
    public RecipeFilter getRecipeFilter(ResourceLocation id) {
        return recipeFilter.get(id);
    }

    @Nullable
    @Override
    public CommonGunIndex getGunIndex(ResourceLocation id) {
        return gunIndex.get(id);
    }

    @Override
    public @Nullable CommonAmmoIndex getAmmoIndex(ResourceLocation ammoId) {
        return ammoIndex.get(ammoId);
    }

    @Override
    public @Nullable CommonAttachmentIndex getAttachmentIndex(ResourceLocation attachmentId) {
        return attachmentIndex.get(attachmentId);
    }

    @Override
    public @Nullable CommonAttachmentVariantIndex getAttachmentVariantIndex(ResourceLocation variantId) {
        return attachmentVariantIndex.get(variantId);
    }

    @Override
    public @Nullable CommonSlotAdapterIndex getSlotAdapterIndex(ResourceLocation adapterId) {
        return slotAdapterIndex.get(adapterId);
    }

    @Override
    public @Nullable CommonBlockIndex getBlockIndex(ResourceLocation blockId) {
        return blockIndex.get(blockId);
    }

    @Override
    public @Nullable LuaTable getScript(ResourceLocation scriptId) {
        return null;
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonGunIndex>> getAllGuns() {
        return gunIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonAmmoIndex>> getAllAmmos() {
        return ammoIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonAttachmentIndex>> getAllAttachments() {
        return attachmentIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonAttachmentVariantIndex>> getAllAttachmentVariants() {
        return attachmentVariantIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonSlotAdapterIndex>> getAllSlotAdapters() {
        return slotAdapterIndex.entrySet();
    }

    @Override
    public Set<Map.Entry<ResourceLocation, CommonBlockIndex>> getAllBlocks() {
        return blockIndex.entrySet();
    }

    @Override
    public Set<String> getAttachmentTags(ResourceLocation registryName) {
        return attachmentTags.get(registryName);
    }

    @Override
    public Set<String> getAllowAttachmentTags(ResourceLocation registryName) {
        return allowAttachmentTags.get(registryName);
    }

    public void clear() {
        gunData.clear();
        attachmentData.clear();
        attachmentVariantData.clear();
        slotAdapterData.clear();
        gunIndex.clear();
        ammoIndex.clear();
        attachmentIndex.clear();
        attachmentVariantIndex.clear();
        slotAdapterIndex.clear();
        blockIndex.clear();
        recipeFilter.clear();
        blockData.clear();
        attachmentTags.clear();
        allowAttachmentTags.clear();
        AllowAttachmentTagMatcher.resetCache();
        // 1.7.10 差异：工作台配方是 GunSmithTableRecipe 的静态登记表（1.20 由 RecipeManager 持有），
        // 与上面这些 map 不同源，必须一起清，否则换服后残留上一台服务器的配方。
        GunSmithTableRecipe.clear();
    }

    public void fromNetwork(Map<DataType, Map<ResourceLocation, String>> cache) {
        clear();
        Map<DataType, Map<ResourceLocation, String>> delayed = new HashMap<>();
        for (Map.Entry<DataType, Map<ResourceLocation, String>> entry : cache.entrySet()) {
            switch (entry.getKey()) {
                case GUN_INDEX:
                case AMMO_INDEX:
                case ATTACHMENT_INDEX:
                case ATTACHMENT_VARIANT_INDEX:
                case SLOT_ADAPTER_INDEX:
                case BLOCK_INDEX:
                    delayed.put(entry.getKey(), entry.getValue());
                    break;
                // 1.7.10 差异：配方产物要查 gun/attachment index（RawGunTableResult#init），
                // 必须等全部 index 都装完才能解析，所以放到本方法末尾单独处理。
                case RECIPES:
                    break;
                default:
                    fromNetwork(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<DataType, Map<ResourceLocation, String>> entry : delayed.entrySet()) {
            fromNetwork(entry.getKey(), entry.getValue());
        }
        // 工作台配方：解析成 GunSmithTableRecipe 并展开产物（1.20 由客户端 RecipeManager 提供，1.7.10 自建静态表）
        Map<ResourceLocation, String> recipes = cache.get(DataType.RECIPES);
        if (recipes != null) {
            registerRecipes(recipes);
        }
    }

    /**
     * 把服务端下发的配方 json 登记进 {@link com.tacz.guns.crafting.GunSmithTableRecipe} 的静态表。
     *
     * <p>
     * 与 {@code GunSmithTableRecipeManager#loadRecipes} 共用同一段解析/容错逻辑，只是这里先进来的是
     * 「id → json 字符串」（网络缓存形态）。解析完立刻 {@code initAll()}：
     * 此刻 index 已就绪，产物栈可以查 {@code TimelessAPI.getCommonGunIndex}。
     */
    private void registerRecipes(Map<ResourceLocation, String> data) {
        Map<ResourceLocation, JsonObject> parsed = new HashMap<>(data.size());
        for (Map.Entry<ResourceLocation, String> entry : data.entrySet()) {
            try {
                JsonObject json = CommonAssetsManager.GSON.fromJson(entry.getValue(), JsonObject.class);
                if (json != null) {
                    parsed.put(entry.getKey(), json);
                }
            } catch (RuntimeException exception) {
                GunMod.LOGGER.warn("Failed to parse recipe json from network for {}", entry.getKey(), exception);
            }
        }
        GunSmithTableRecipeManager.loadRecipes(parsed, "network");
        try {
            GunSmithTableRecipe.initAll();
        } catch (RuntimeException exception) {
            GunMod.LOGGER.error("Failed to init gun smith table recipes from network", exception);
        }
    }

    private <T> T parse(String json, Class<T> dataClass) {
        return CommonAssetsManager.GSON.fromJson(json, dataClass);
    }

    private AttachmentData parseAttachmentData(String json) {
        AttachmentData data = CommonAssetsManager.GSON.fromJson(json, AttachmentData.class);
        JsonElement element = CommonAssetsManager.GSON.fromJson(json, JsonElement.class);
        if (data != null) {
            AttachmentDataManager.initModifiers(data, element, CommonAssetsManager.GSON);
        }
        return data;
    }

    private GunData parseGunData(String json) {
        GunData data = CommonAssetsManager.GSON.fromJson(json, GunData.class);
        JsonElement element = CommonAssetsManager.GSON.fromJson(json, JsonElement.class);
        GunDataManager.initExclusiveAttachmentModifiers(data, element);
        return data;
    }

    private void resolveAttachmentTags(Map<ResourceLocation, String> data) {
        for (Map.Entry<ResourceLocation, String> entry : data.entrySet()) {
            // 1.7.10 差异：gson 2.2.4 没有 fromJson(JsonElement, TypeToken<T>) 重载，用 Type 版本
            List<String> parsed = CommonAssetsManager.GSON
                .fromJson(entry.getValue(), new TypeToken<List<String>>() {}.getType());
            Set<String> ordered = parsed == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(parsed));
            if (entry.getKey()
                .getResourcePath()
                .startsWith("allow_attachments/")
                && entry.getKey()
                    .getResourcePath()
                    .length() > 18) {
                // 1.7.10 差异：ResourceLocation 没有 withPath()，同 namespace 重建一个新 id
                ResourceLocation gunId = new ResourceLocation(
                    entry.getKey()
                        .getResourceDomain(),
                    entry.getKey()
                        .getResourcePath()
                        .substring(18));
                allowAttachmentTags.put(gunId, ordered);
            } else {
                attachmentTags.put(entry.getKey(), ordered);
            }
        }
    }

    private void fromNetwork(DataType type, Map<ResourceLocation, String> data) {
        if (type == DataType.ATTACHMENT_TAGS) {
            resolveAttachmentTags(data);
            return;
        }
        for (Map.Entry<ResourceLocation, String> entry : data.entrySet()) {
            try {
                switch (type) {
                    case GUN_DATA -> gunData.put(entry.getKey(), parseGunData(entry.getValue()));
                    case GUN_INDEX -> gunIndex.put(entry.getKey(), parse(entry.getValue(), CommonGunIndex.class));
                    case AMMO_INDEX -> ammoIndex.put(entry.getKey(), parse(entry.getValue(), CommonAmmoIndex.class));
                    case ATTACHMENT_DATA -> attachmentData.put(entry.getKey(), parseAttachmentData(entry.getValue()));
                    case ATTACHMENT_INDEX -> attachmentIndex
                        .put(entry.getKey(), parse(entry.getValue(), CommonAttachmentIndex.class));
                    case ATTACHMENT_VARIANT_DATA -> attachmentVariantData
                        .put(entry.getKey(), parse(entry.getValue(), VariantData.class));
                    case ATTACHMENT_VARIANT_INDEX -> attachmentVariantIndex.put(
                        entry.getKey(),
                        CommonAttachmentVariantIndex
                            .getInstance(parse(entry.getValue(), AttachmentVariantIndexPOJO.class)));
                    case SLOT_ADAPTER_DATA -> slotAdapterData
                        .put(entry.getKey(), parse(entry.getValue(), SlotAdapterData.class));
                    case SLOT_ADAPTER_INDEX -> slotAdapterIndex.put(
                        entry.getKey(),
                        CommonSlotAdapterIndex.getInstance(parse(entry.getValue(), SlotAdapterIndexPOJO.class)));
                    case BLOCK_INDEX -> blockIndex.put(entry.getKey(), parse(entry.getValue(), CommonBlockIndex.class));
                    case RECIPE_FILTER -> recipeFilter.put(entry.getKey(), parse(entry.getValue(), RecipeFilter.class));
                    case BLOCK_DATA -> blockData.put(entry.getKey(), parse(entry.getValue(), BlockData.class));
                }
            } catch (IllegalArgumentException | JsonParseException exception) {
                GunMod.LOGGER
                    .warn("Failed to parse data from network for {} with id {}", type, entry.getKey(), exception);
            }
        }
    }
}
