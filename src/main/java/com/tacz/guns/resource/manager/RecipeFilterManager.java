package com.tacz.guns.resource.manager;

import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.PackDirectory;

/**
 * 配方过滤（{@code data/<ns>/recipe_filters}）管理器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/RecipeFilterManager.java}。
 * 1.7.10 差异：两段式折叠为同步 {@link #reload()}，同名过滤器的 {@code merge} 语义与上游一致。
 */
public class RecipeFilterManager implements INetworkCacheReloadListener {

    private final Map<ResourceLocation, RecipeFilter> filters = Maps.newHashMap();

    private final Gson gson;
    private final Marker marker;

    private final PackDirectory fileToIdConverter;
    protected Map<ResourceLocation, String> networkCache = ImmutableMap.of();

    public RecipeFilterManager() {
        this.gson = CommonAssetsManager.GSON;
        this.marker = MarkerManager.getMarker("RecipeFilter");
        this.fileToIdConverter = PackDirectory.json("recipe_filters");
    }

    @Override
    public void reload() {
        Map<ResourceLocation, List<JsonElement>> scanned = GunPackResources.scanDirectoryAll(
            GunPackLoader.INSTANCE.getPackSources(),
            GunPackResources.DATA_ROOT,
            fileToIdConverter.prefix(),
            fileToIdConverter.extension(),
            gson);
        apply(scanned);
    }

    protected void apply(Map<ResourceLocation, List<JsonElement>> pObject) {
        filters.clear();

        ImmutableMap.Builder<ResourceLocation, String> builder = ImmutableMap.builder();

        for (Map.Entry<ResourceLocation, List<JsonElement>> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();

            for (JsonElement element : entry.getValue()) {
                try {
                    RecipeFilter data = parseJson(element);
                    filters.compute(id, (key, value) -> {
                        if (value == null) {
                            return data;
                        } else {
                            value.merge(data);
                            return value;
                        }
                    });
                } catch (JsonParseException | IllegalArgumentException e) {
                    GunMod.LOGGER.error(marker, "Failed to load data file {}", id, e);
                }
            }

            if (filters.containsKey(id)) {
                builder.put(id, gson.toJson(filters.get(id)));
            }
        }

        this.networkCache = builder.build();
    }

    private RecipeFilter parseJson(JsonElement element) {
        return gson.fromJson(element, RecipeFilter.class);
    }

    @Override
    public Map<ResourceLocation, String> getNetworkCache() {
        return networkCache;
    }

    @Override
    public DataType getType() {
        return DataType.RECIPE_FILTER;
    }

    public Map<ResourceLocation, RecipeFilter> getFilters() {
        return filters;
    }

    public RecipeFilter getFilter(ResourceLocation id) {
        return filters.get(id);
    }
}
