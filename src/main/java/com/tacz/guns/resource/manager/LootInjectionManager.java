package com.tacz.guns.resource.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.IGunPackReloadListener;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.PackDirectory;
import com.tacz.guns.resource.pojo.data.loot.LootTableInjection;

/**
 * 战利品注入管理器（{@code data/<ns>/tacz_loot_injectors}）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/LootInjectionManager.java}。
 * 1.7.10 差异：两段式折叠为同步 {@link #reload()}；
 * {@link LootTableInjection} 本身也因 1.7.10 无战利品表体系而降级（见该类注释）。
 */
public class LootInjectionManager implements IGunPackReloadListener {

    private final Map<ResourceLocation, List<LootTableInjection>> injections = Maps.newHashMap();
    private final Gson gson = CommonAssetsManager.GSON;
    private final Marker marker = MarkerManager.getMarker("LootInjection");
    private final PackDirectory fileToIdConverter = PackDirectory.json("tacz_loot_injectors");

    @Override
    public void reload() {
        Map<ResourceLocation, List<JsonElement>> object = GunPackResources.scanDirectoryAll(
            GunPackLoader.INSTANCE.getPackSources(),
            GunPackResources.DATA_ROOT,
            fileToIdConverter.prefix(),
            fileToIdConverter.extension(),
            gson);
        injections.clear();
        for (Map.Entry<ResourceLocation, List<JsonElement>> entry : object.entrySet()) {
            ResourceLocation id = entry.getKey();
            for (JsonElement element : entry.getValue()) {
                try {
                    LootTableInjection injection = LootTableInjection.fromJson(id, element);
                    for (ResourceLocation lootTable : injection.lootTables()) {
                        injections.computeIfAbsent(lootTable, key -> new ArrayList<>())
                            .add(injection);
                    }
                } catch (JsonParseException | IllegalArgumentException e) {
                    GunMod.LOGGER.error(marker, "Failed to load loot injection {}", id, e);
                }
            }
        }
    }

    public List<LootTableInjection> getInjections(ResourceLocation lootTable) {
        return injections.getOrDefault(lootTable, Collections.emptyList());
    }
}
