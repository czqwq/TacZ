package com.tacz.guns.resource.pojo.data.loot;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.mcshim.GsonHelper;

/**
 * 枪包里的战利品注入声明（{@code data/<ns>/tacz_loot_injectors/*.json}）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/pojo/data/loot/LootTableInjection.java}，
 * 形态是 {@code record LootTableInjection(List<ResourceLocation> lootTables, LootTable lootTable)}，
 * 用 {@code Deserializers.createLootTableSerializer()} + {@code ForgeHooks.loadLootTable} 把 json 解析成原版
 * {@code LootTable}，再由 {@code LootTableInjectorModifier}（{@code LootModifier}）在开箱时注入。
 *
 * <p>
 * <b>1.7.10 差异（已记录，不是遗漏）</b>：1.7.10 没有 {@code LootTable} / {@code LootContext} /
 * {@code LootModifier} / 战利品表 json 体系，只有 {@code WeightedRandomChestContent} + {@code ChestGenHooks}。
 * 因此这里<b>不再解析成 {@code LootTable}，而是原样保留 json</b>（{@link #json()}），
 * 交给 {@code com.tacz.guns.loot/**} 用「掉落事件」方式降级实现。
 * 上游的字段校验（必须声明 {@code loot_table}/{@code loot_tables} 与 {@code pools}）保持不变。
 */
public record LootTableInjection(List<ResourceLocation> lootTables, JsonObject json) {

    public static LootTableInjection fromJson(ResourceLocation fileId, JsonElement element) {
        JsonObject object = GsonHelper.convertToJsonObject(element, "loot injection");
        List<ResourceLocation> lootTables = readLootTables(fileId, object);
        if (!object.has("pools")) {
            throw new JsonParseException("Loot injection " + fileId + " must define pools");
        }
        return new LootTableInjection(lootTables, object);
    }

    private static List<ResourceLocation> readLootTables(ResourceLocation fileId, JsonObject object) {
        List<ResourceLocation> lootTables = new ArrayList<>();
        if (object.has("loot_tables")) {
            for (JsonElement table : GsonHelper.getAsJsonArray(object, "loot_tables")) {
                lootTables.add(new ResourceLocation(GsonHelper.convertToString(table, "loot table")));
            }
        } else if (object.has("loot_table")) {
            lootTables.add(new ResourceLocation(GsonHelper.getAsString(object, "loot_table")));
        } else {
            throw new JsonParseException("Loot injection " + fileId + " must define loot_table or loot_tables");
        }
        return List.copyOf(lootTables);
    }
}
