package com.tacz.guns.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.GsonHelper;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.loot.LootTableInjection;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 枪包战利品注入器（{@code data/<ns>/tacz_loot_injectors/*.json}）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/loot/LootTableInjectorModifier.java}
 * 
 * <pre>
 * {@code public class LootTableInjectorModifier extends LootModifier implements IGlobalLootModifier}
 * </pre>
 * 
 * 上游是 Forge 的 <b>global loot modifier</b>：注册 {@code Codec} + {@code LootModifierSerializer}，
 * 由 Forge 在每次 {@code LootTable} 结算后回调 {@code doApply(generatedLoot, LootContext)}，
 * 用 {@code LootContext#getQueriedLootTableId()} 找到注入项，再调
 * {@code LootTableInjection#createStacks(LootContext)}（原版 {@code LootTable} 掷骰）。
 *
 * <p>
 * <b>1.7.10 差异（本类为什么变成静态工具类）</b>：
 * <ul>
 * <li>1.7.10 没有 {@code LootModifier} / {@code IGlobalLootModifier} / {@code LootContext} /
 * {@code LootTable} / 战利品表 json 体系，也没有 {@code global_loot_modifiers.json} 的挂载点。
 * 因此不实现任何 Forge 1.20 接口，改为<b>静态注入器</b>：由 1.7.10 侧的掉落事件
 * （{@code LivingDropsEvent} / {@code BlockEvent.HarvestDropsEvent} / 箱子战利品 {@code ChestGenHooks}）
 * 在拿到「表 id + 当前产出列表」时调用 {@link #inject(ResourceLocation, List)}。</li>
 * <li>{@link LootTableInjection} 本身也已降级为「保留原始 json」（见该类注释），
 * 不再解析成 {@code LootTable}；因此这里实现一个 <b>1.7.10 版最小掷骰器</b>
 * {@link #createStacks(LootTableInjection)}，只支持枪包实际用到的子集：
 * {@code pools[].rolls}（数字或 {@code {"min","max"}}）、{@code entries[]}
 * 的 {@code minecraft:item}（{@code name}/可选 {@code weight}）、
 * {@code functions[]} 的 {@code set_count}（数字或 {@code {"min","max"}}）与 {@code set_nbt}。
 * <b>不支持</b>：{@code empty}/{@code loot_table}/{@code tag} 类型 entry、{@code conditions}、
 * {@code set_damage}/{@code enchant_*} 等函数——这些在 1.20 由原版实现，1.7.10 需要在
 * {@code loot/**} 里逐个补，属已知差异（不是遗漏）。</li>
 * <li>表 id 的对应关系（1.20 {@code mineshaft} / {@code entities/zombie} ↔ 1.7.10
 * {@code ChestGenHooks} 分类）没有通用映射，因此本类不做事件注册；由
 * {@code event/LootInjectionEvent} 决定「哪个 1.7.10 事件对应哪个表 id」后调用本类
 * （r3 已接线：箱子 {@code ChestGenHooks} / 实体 {@code LivingDropsEvent} /
 * 方块 {@code HarvestDropsEvent} / 新世界奖励箱 {@code WorldEvent.CreateSpawnPosition}）。</li>
 * </ul>
 */
public final class LootTableInjectorModifier {

    /** 上游 {@code data/tacz/loot_modifiers/loot_table_injector.json} 里的 modifier 名。 */
    public static final String LOOT_TABLE_INJECTOR = "loot_table_injector";

    private static final Random RANDOM = new Random();

    private LootTableInjectorModifier() {}

    /**
     * 上游 {@code doApply}：把该战利品表配置的注入项追加到产出列表。
     *
     * @param lootTableId   当前结算的战利品表 id
     * @param generatedLoot 当前产出（会被原地追加）
     * @return 追加的条目数（0 表示没有任何注入）
     */
    public static int inject(@Nullable ResourceLocation lootTableId, @Nullable List<ItemStack> generatedLoot) {
        if (lootTableId == null || generatedLoot == null) {
            return 0;
        }
        CommonAssetsManager manager = CommonAssetsManager.getInstance();
        if (manager == null) {
            return 0;
        }
        return inject(manager.getLootTableInjections(lootTableId), generatedLoot);
    }

    /**
     * 同 {@link #inject(ResourceLocation, List)}，但由调用方直接给出注入项列表。
     *
     * <p>
     * 1.7.10 新增：{@code event/LootInjectionEvent} 需要绕过 {@code CommonAssetsManager} 的时序
     * （新世界奖励箱在 {@code FMLServerStartingEvent} 之前生成），使用兜底
     * {@code LootInjectionManager} 取到注入项后调用本方法。
     *
     * @param injections    注入项（可为空/null）
     * @param generatedLoot 当前产出（会被原地追加）
     * @return 追加的条目数
     */
    public static int inject(@Nullable List<LootTableInjection> injections, @Nullable List<ItemStack> generatedLoot) {
        if (injections == null || injections.isEmpty() || generatedLoot == null) {
            return 0;
        }
        int added = 0;
        for (LootTableInjection injection : injections) {
            for (ItemStack stack : createStacks(injection)) {
                if (stack != null && stack.getItem() != null && stack.stackSize > 0) {
                    generatedLoot.add(stack);
                    added++;
                }
            }
        }
        return added;
    }

    /**
     * 同 {@link #inject}，但传入 1.7.10 的 {@code EntityItem}/{@code WeightedRandomChestContent} 之前的纯 {@code ItemStack} 列表。
     */
    public static List<ItemStack> createStacks(@Nullable LootTableInjection injection) {
        if (injection == null || injection.json() == null) {
            return Collections.emptyList();
        }
        JsonObject root = injection.json();
        if (!root.has("pools")) {
            return Collections.emptyList();
        }
        List<ItemStack> result = new ArrayList<>();
        for (JsonElement poolElement : GsonHelper.getAsJsonArray(root, "pools")) {
            if (poolElement == null || !poolElement.isJsonObject()) {
                continue;
            }
            JsonObject pool = poolElement.getAsJsonObject();
            int rolls = readRolls(pool);
            List<WeightedEntry> entries = readEntries(pool);
            if (entries.isEmpty()) {
                continue;
            }
            for (int roll = 0; roll < rolls; roll++) {
                WeightedEntry picked = pick(entries);
                if (picked == null) {
                    continue;
                }
                ItemStack stack = buildStack(picked);
                if (stack != null && stack.getItem() != null && stack.stackSize > 0) {
                    result.add(stack);
                }
            }
        }
        return result;
    }

    private static int readRolls(JsonObject pool) {
        if (!pool.has("rolls")) {
            return 1;
        }
        JsonElement rolls = pool.get("rolls");
        if (rolls.isJsonPrimitive()) {
            return Math.max(0, rolls.getAsInt());
        }
        if (rolls.isJsonObject()) {
            JsonObject object = rolls.getAsJsonObject();
            int min = GsonHelper.getAsInt(object, "min", 1);
            int max = GsonHelper.getAsInt(object, "max", min);
            return min >= max ? Math.max(0, min) : min + RANDOM.nextInt(max - min + 1);
        }
        return 1;
    }

    private static List<WeightedEntry> readEntries(JsonObject pool) {
        if (!pool.has("entries")) {
            return Collections.emptyList();
        }
        List<WeightedEntry> entries = new ArrayList<>();
        for (JsonElement entryElement : GsonHelper.getAsJsonArray(pool, "entries")) {
            if (entryElement == null || !entryElement.isJsonObject()) {
                continue;
            }
            JsonObject entry = entryElement.getAsJsonObject();
            String type = GsonHelper.getAsString(entry, "type", "");
            if (!"minecraft:item".equals(type) || !entry.has("name")) {
                // 1.7.10 降级子集只支持具体物品 entry（见类注释）
                continue;
            }
            int weight = Math.max(1, GsonHelper.getAsInt(entry, "weight", 1));
            entries.add(
                new WeightedEntry(
                    new ResourceLocation(GsonHelper.getAsString(entry, "name")),
                    weight,
                    entry.has("functions") ? entry.getAsJsonArray("functions") : null));
        }
        return entries;
    }

    @Nullable
    private static WeightedEntry pick(List<WeightedEntry> entries) {
        int total = 0;
        for (WeightedEntry entry : entries) {
            total += entry.weight;
        }
        if (total <= 0) {
            return null;
        }
        int roll = RANDOM.nextInt(total);
        for (WeightedEntry entry : entries) {
            roll -= entry.weight;
            if (roll < 0) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    @Nullable
    private static ItemStack buildStack(WeightedEntry entry) {
        ResourceLocation id = entry.itemId;
        Item item = GameRegistry.findItem(id.getResourceDomain(), id.getResourcePath());
        if (item == null) {
            GunMod.LOGGER.warn("Loot injector references unknown item {}", id);
            return null;
        }
        ItemStack stack = new ItemStack(item);
        if (entry.functions == null) {
            return stack;
        }
        for (JsonElement functionElement : entry.functions) {
            if (functionElement == null || !functionElement.isJsonObject()) {
                continue;
            }
            JsonObject function = functionElement.getAsJsonObject();
            String name = GsonHelper.getAsString(function, "function", "");
            switch (name) {
                case "minecraft:set_count":
                case "set_count":
                    stack.stackSize = Math.max(1, readCount(function));
                    break;
                case "minecraft:set_nbt":
                case "set_nbt":
                    NBTTagCompound tag = parseNbt(function);
                    if (tag != null) {
                        stack.setTagCompound(tag);
                    }
                    break;
                default:
                    // 其它函数（set_damage / enchant_* 等）在 1.7.10 无对应实现，见类注释
                    break;
            }
        }
        return stack;
    }

    private static int readCount(JsonObject function) {
        if (!function.has("count")) {
            return 1;
        }
        JsonElement count = function.get("count");
        if (count.isJsonPrimitive()) {
            return count.getAsInt();
        }
        if (count.isJsonObject()) {
            JsonObject object = count.getAsJsonObject();
            int min = GsonHelper.getAsInt(object, "min", 1);
            int max = GsonHelper.getAsInt(object, "max", min);
            return min >= max ? min : min + RANDOM.nextInt(max - min + 1);
        }
        return 1;
    }

    @Nullable
    private static NBTTagCompound parseNbt(JsonObject function) {
        if (!function.has("tag")) {
            return null;
        }
        try {
            NBTBase tag = JsonToNBT.func_150315_a(GsonHelper.getAsString(function, "tag"));
            return tag instanceof NBTTagCompound compound ? compound : null;
        } catch (Exception exception) {
            GunMod.LOGGER.warn("Failed to parse loot injector nbt", exception);
            return null;
        }
    }

    /** 1.7.10 版的加权条目（上游由原版 {@code LootPoolEntry} 承担）。 */
    private static final class WeightedEntry {

        private final ResourceLocation itemId;
        private final int weight;
        @Nullable
        private final JsonArray functions;

        private WeightedEntry(ResourceLocation itemId, int weight, @Nullable JsonArray functions) {
            this.itemId = itemId;
            this.weight = weight;
            this.functions = functions;
        }
    }
}
