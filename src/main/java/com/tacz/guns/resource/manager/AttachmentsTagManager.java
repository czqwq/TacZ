package com.tacz.guns.resource.manager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.PackDirectory;

/**
 * 配件标签（{@code data/<ns>/tacz_tags/attachments}）管理器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/AttachmentsTagManager.java}，
 * 是 {@code extends SimplePreparableReloadListener<Map<ResourceLocation, List<JsonElement>>>}。
 * 1.7.10 差异：只有两段式折叠为同步 {@link #reload()}，合并语义（同名文件取并集、保持插入顺序）与上游一致。
 */
public class AttachmentsTagManager implements INetworkCacheReloadListener {

    private final Map<ResourceLocation, Set<String>> tags = Maps.newHashMap();
    private final Map<ResourceLocation, Set<String>> allowAttachments = Maps.newHashMap();

    private final Gson gson;
    private final Marker marker;

    private final PackDirectory fileToIdConverter;
    protected Map<ResourceLocation, String> networkCache = ImmutableMap.of();

    public AttachmentsTagManager() {
        this.gson = CommonAssetsManager.GSON;
        this.marker = MarkerManager.getMarker("AllowTagManager");
        this.fileToIdConverter = PackDirectory.json("tacz_tags/attachments");
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
        tags.clear();
        allowAttachments.clear();
        ImmutableMap.Builder<ResourceLocation, String> builder = ImmutableMap.builder();

        for (Map.Entry<ResourceLocation, List<JsonElement>> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();

            LinkedHashSet<String> merged = new LinkedHashSet<>();
            for (JsonElement element : entry.getValue()) {
                try {
                    List<String> data = parseJson(element);
                    if (data != null) {
                        if (data.stream()
                            .anyMatch(Objects::isNull)) {
                            throw new JsonParseException("Null value found in JSON data");
                        } else {
                            merged.addAll(data);
                        }
                    }
                } catch (JsonParseException e) {
                    GunMod.LOGGER.error(marker, "Failed to parse data file {}", id, e);
                }
            }

            Set<String> ordered = Collections.unmodifiableSet(new LinkedHashSet<>(merged));
            // 1.7.10 差异：ResourceLocation 没有 getPath()/withPath()，
            // 对应物是 getResourcePath()，构造同 namespace 的新 id。
            if (id.getResourcePath()
                .startsWith("allow_attachments/")
                && id.getResourcePath()
                    .length() > 18) {
                ResourceLocation gunId = new ResourceLocation(
                    id.getResourceDomain(),
                    id.getResourcePath()
                        .substring(18));
                allowAttachments.put(gunId, ordered);
            } else {
                tags.put(id, ordered);
            }

            builder.put(entry.getKey(), gson.toJson(ordered));
        }

        this.networkCache = builder.build();
    }

    private List<String> parseJson(JsonElement element) {
        return gson.fromJson(element, new TypeToken<List<String>>() {}.getType());
    }

    @Override
    public Map<ResourceLocation, String> getNetworkCache() {
        return networkCache;
    }

    @Override
    public DataType getType() {
        return DataType.ATTACHMENT_TAGS;
    }

    public Set<String> getAttachmentTags(ResourceLocation registryName) {
        return tags.get(registryName);
    }

    public Set<String> getAllowAttachmentTags(ResourceLocation registryName) {
        return allowAttachments.get(registryName);
    }
}
