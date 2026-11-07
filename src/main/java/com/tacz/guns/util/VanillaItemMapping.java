package com.tacz.guns.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 1.20.1 原版物品 id → 1.7.10 注册名（+ damage）的折算表（<b>移植特有机制，不是上游语义</b>）。
 *
 * <p>
 * <b>为什么需要</b>：上游 1.20.1 的配方/战利品表/json 里写的是 1.13 扁平化之后的物品 id
 * （{@code minecraft:blue_dye}、{@code minecraft:oak_planks}、{@code minecraft:firework_rocket}）。
 * 上游在 1.20 注册表里能直接查到；1.7.10 的物品表是扁平化之前的形态（{@code minecraft:dye} + damage 4、
 * {@code minecraft:planks} + damage 0、{@code minecraft:fireworks}），名字对不上，
 * {@link GameRegistry#findItem(String, String)} 直接返回 {@code null}。
 *
 * <p>
 * 上游解析点（用于对照）：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/serialize/
 * GunSmithTableIngredientSerializer.java:23}（{@code Ingredient.fromJson}）、
 * {@code resource/serialize/ItemStackJsonParser.java:30}（{@code CraftingHelper#getItemStack}）；
 * 再往下是 {@code ItemStack}/{@code BuiltInRegistries.ITEM} 按 {@code ResourceLocation} 查表。
 *
 * <p>
 * <b>折算原则</b>：只做「同一个物品在扁平化前后换了写法」的等价折算（染料名 → {@code dye} + damage、
 * 木头种类名 → {@code planks}/{@code log} + damage、{@code firework_rocket} → {@code fireworks} 等），
 * <b>不做材质替换</b>（例如 {@code netherite_ingot} 不在本表里，替换见
 * {@code crafting/GunSmithTableIngredient#MISSING_VANILLA_ITEM_SUBSTITUTION}）。
 * 折算表<b>优先于</b>原样查表：扁平化后的 id 可能与 1.7.10 里另一个物品同名（如 1.20 的
 * {@code minecraft:melon} 是西瓜方块，1.7.10 的 {@code minecraft:melon} 是西瓜片），必须先折算。
 *
 * <p>
 * 折算命中保持静默（枪包重载频繁，逐条打日志会刷屏）；排查时可在 {@link #resolve(String)} 临时加日志。
 */
public final class VanillaItemMapping {

    /**
     * 1.20 物品 id → 1.7.10 物品描述。
     *
     * <p>
     * 描述格式：{@code "minecraft:<注册名>"} 或 {@code "minecraft:<注册名>#<damage>"}；
     * 注册名与 damage 的对应关系逐项来自 1.7.10 反编译源码
     * （{@code build/rfg/minecraft-src/java/net/minecraft/item/Item.java} 的 {@code itemRegistry.addObject(...)}
     * 与 {@code net/minecraft/block/Block.java} 的方块道具注册）。
     */
    private static final Map<String, String> FLATTENED_1_20_TO_1_7_10 = new HashMap<>();

    /** 1.7.10 的 16 种颜色 damage 顺序（wool/carpet/stained_glass/… 通用，1.20 的颜色名与之逐个对应）。 */
    private static final String[] LEGACY_COLOR_DAMAGE = { "white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black" };

    /** 1.7.10 的木头种类顺序（planks/wooden_slab/sapling/leaves/log 的 damage 0…5）。 */
    private static final String[] WOOD_TYPES = { "oak", "spruce", "birch", "jungle", "acacia", "dark_oak" };

    static {
        // ---- 染料：1.20 每种颜色一个 id，1.7.10 统一是 minecraft:dye + damage ----
        // 1.7.10 damage 顺序：0=墨囊(黑)/1=玫瑰红/2=仙人掌绿/3=可可豆(棕)/4=青金石/5=紫/6=青/7=淡灰/8=灰/
        // 9=粉/10=黄绿/11=蒲公英黄/12=淡蓝/13=品红/14=橙/15=骨粉(白)
        // 注意：1.7.10 的 damage 顺序与 1.20 的颜色名顺序不同，逐色显式列出以免看错。
        put("minecraft:white_dye", "minecraft:dye#15");
        put("minecraft:orange_dye", "minecraft:dye#14");
        put("minecraft:magenta_dye", "minecraft:dye#13");
        put("minecraft:light_blue_dye", "minecraft:dye#12");
        put("minecraft:yellow_dye", "minecraft:dye#11");
        put("minecraft:lime_dye", "minecraft:dye#10");
        put("minecraft:pink_dye", "minecraft:dye#9");
        put("minecraft:gray_dye", "minecraft:dye#8");
        put("minecraft:light_gray_dye", "minecraft:dye#7");
        put("minecraft:cyan_dye", "minecraft:dye#6");
        put("minecraft:purple_dye", "minecraft:dye#5");
        put("minecraft:blue_dye", "minecraft:dye#4");
        put("minecraft:brown_dye", "minecraft:dye#3");
        put("minecraft:green_dye", "minecraft:dye#2");
        put("minecraft:red_dye", "minecraft:dye#1");
        put("minecraft:black_dye", "minecraft:dye#0");
        // 1.12 之前的部分染料另有独立 id；1.20 枪包里仍可能沿用旧写法
        put("minecraft:lapis_lazuli", "minecraft:dye#4");
        put("minecraft:ink_sac", "minecraft:dye#0");
        put("minecraft:cocoa_beans", "minecraft:dye#3");
        put("minecraft:bone_meal", "minecraft:dye#15");

        // ---- 16 色羊毛/地毯/染色玻璃/玻璃板/染色陶瓦/床：1.20 一个颜色一个 id，1.7.10 统一 damage ----
        for (int damage = 0; damage < LEGACY_COLOR_DAMAGE.length; damage++) {
            String color = LEGACY_COLOR_DAMAGE[damage];
            put("minecraft:" + color + "_wool", "minecraft:wool#" + damage);
            put("minecraft:" + color + "_carpet", "minecraft:carpet#" + damage);
            put("minecraft:" + color + "_stained_glass", "minecraft:stained_glass#" + damage);
            put("minecraft:" + color + "_stained_glass_pane", "minecraft:stained_glass_pane#" + damage);
            put("minecraft:" + color + "_terracotta", "minecraft:stained_hardened_clay#" + damage);
            // 1.7.10 的床不分颜色（ItemBed 只有一个 id），任意颜色都折到 minecraft:bed
            put("minecraft:" + color + "_bed", "minecraft:bed");
        }
        put("minecraft:terracotta", "minecraft:hardened_clay");

        // ---- 木头系列：1.20 种类进 id，1.7.10 走 damage（planks/wooden_slab/sapling/log/leaves） ----
        for (int damage = 0; damage < WOOD_TYPES.length; damage++) {
            String wood = WOOD_TYPES[damage];
            put("minecraft:" + wood + "_planks", "minecraft:planks#" + damage);
            put("minecraft:" + wood + "_slab", "minecraft:wooden_slab#" + damage);
            put("minecraft:" + wood + "_sapling", "minecraft:sapling#" + damage);
            // 1.7.10：oak/spruce/birch/jungle 在 leaves，acacia/dark_oak 在 leaves2（各自 damage 0/1）
            if (damage < 4) {
                put("minecraft:" + wood + "_leaves", "minecraft:leaves#" + damage);
                put("minecraft:" + wood + "_log", "minecraft:log#" + damage);
            } else {
                put("minecraft:" + wood + "_leaves", "minecraft:leaves2#" + (damage - 4));
                put("minecraft:" + wood + "_log", "minecraft:log2#" + (damage - 4));
            }
            // 楼梯：1.7.10 六种木头楼梯同名注册（oak_stairs/spruce_stairs/…），原样可查，不必折算
            // 门/活板门/栅栏/栅栏门/按钮/压力板：1.7.10 只有橡木版（wooden_door/trapdoor/fence/…）
            put("minecraft:" + wood + "_door", "minecraft:wooden_door");
            put("minecraft:" + wood + "_trapdoor", "minecraft:trapdoor");
            put("minecraft:" + wood + "_fence", "minecraft:fence");
            put("minecraft:" + wood + "_fence_gate", "minecraft:fence_gate");
            put("minecraft:" + wood + "_button", "minecraft:wooden_button");
            put("minecraft:" + wood + "_pressure_plate", "minecraft:wooden_pressure_plate");
        }
        put("minecraft:oak_sign", "minecraft:sign");
        put("minecraft:oak_boat", "minecraft:boat");

        // ---- 石类/建材：1.13 扁平化改名 ----
        put("minecraft:stone_slab", "minecraft:stone_slab#0");
        put("minecraft:smooth_stone_slab", "minecraft:stone_slab#0");
        put("minecraft:sandstone_slab", "minecraft:stone_slab#1");
        put("minecraft:cobblestone_slab", "minecraft:stone_slab#3");
        put("minecraft:brick_slab", "minecraft:stone_slab#4");
        put("minecraft:stone_brick_slab", "minecraft:stone_slab#5");
        put("minecraft:nether_brick_slab", "minecraft:stone_slab#6");
        put("minecraft:quartz_slab", "minecraft:stone_slab#7");
        put("minecraft:smooth_stone", "minecraft:stone#0");
        put("minecraft:cobblestone_stairs", "minecraft:stone_stairs");
        put("minecraft:bricks", "minecraft:brick_block");
        put("minecraft:stone_bricks", "minecraft:stonebrick");
        put("minecraft:nether_bricks", "minecraft:nether_brick");
        put("minecraft:nether_brick", "minecraft:netherbrick");
        put("minecraft:chiseled_sandstone", "minecraft:sandstone#1");
        put("minecraft:cut_sandstone", "minecraft:sandstone#2");
        put("minecraft:grass_block", "minecraft:grass");
        put("minecraft:short_grass", "minecraft:tallgrass");
        put("minecraft:tall_grass", "minecraft:tallgrass");
        put("minecraft:dead_bush", "minecraft:deadbush");
        put("minecraft:cobweb", "minecraft:web");
        put("minecraft:lily_pad", "minecraft:waterlily");
        put("minecraft:sugar_cane", "minecraft:reeds");
        put("minecraft:powered_rail", "minecraft:golden_rail");
        put("minecraft:redstone_wire", "minecraft:redstone");

        // ---- 花/作物/食物：1.13 扁平化改名 ----
        put("minecraft:dandelion", "minecraft:yellow_flower");
        put("minecraft:poppy", "minecraft:red_flower#0");
        put("minecraft:blue_orchid", "minecraft:red_flower#1");
        put("minecraft:allium", "minecraft:red_flower#2");
        put("minecraft:azure_bluet", "minecraft:red_flower#3");
        put("minecraft:red_tulip", "minecraft:red_flower#4");
        put("minecraft:orange_tulip", "minecraft:red_flower#5");
        put("minecraft:white_tulip", "minecraft:red_flower#6");
        put("minecraft:pink_tulip", "minecraft:red_flower#7");
        put("minecraft:oxeye_daisy", "minecraft:red_flower#8");
        put("minecraft:melon", "minecraft:melon_block");
        put("minecraft:melon_slice", "minecraft:melon");
        put("minecraft:jack_o_lantern", "minecraft:lit_pumpkin");
        put("minecraft:carved_pumpkin", "minecraft:pumpkin");
        put("minecraft:cod", "minecraft:fish#0");
        put("minecraft:salmon", "minecraft:fish#1");
        put("minecraft:tropical_fish", "minecraft:fish#2");
        put("minecraft:pufferfish", "minecraft:fish#3");
        put("minecraft:cooked_cod", "minecraft:cooked_fished#0");
        put("minecraft:cooked_salmon", "minecraft:cooked_fished#1");

        // ---- 杂项改名 ----
        put("minecraft:note_block", "minecraft:noteblock");
        put("minecraft:firework_rocket", "minecraft:fireworks");
        put("minecraft:firework_star", "minecraft:firework_charge");
    }

    private VanillaItemMapping() {}

    /**
     * 解析一个 1.20 物品 id（可省略命名空间，缺省 {@code minecraft}）。
     *
     * <p>
     * 先查折算表（{@link #FLATTENED_1_20_TO_1_7_10}），没有再按原注册名查表。
     * 折算只会发生一次（表值都是 1.7.10 真实注册名），不存在链式折算。
     *
     * @return 物品不存在时返回 {@code null}
     */
    public static @Nullable ItemStack resolve(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        String normalized = normalize(itemId);
        String mapped = FLATTENED_1_20_TO_1_7_10.get(normalized);
        if (mapped != null) {
            return resolveSpec(mapped);
        }
        return resolveSpec(normalized);
    }

    /**
     * 按「注册名[#damage]」描述解析物品（供折算表的表值与替换表使用）。
     *
     * @param spec 形如 {@code "minecraft:dye#4"} 或 {@code "minecraft:iron_ingot"}
     * @return 物品不存在 / 描述非法时返回 {@code null}
     */
    public static @Nullable ItemStack resolveSpec(@Nullable String spec) {
        if (spec == null || spec.isEmpty()) {
            return null;
        }
        String itemName = spec;
        int damage = 0;
        int hash = spec.indexOf('#');
        if (hash >= 0) {
            itemName = spec.substring(0, hash);
            try {
                damage = Integer.parseInt(spec.substring(hash + 1));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        String normalized = normalize(itemName);
        int colon = normalized.indexOf(':');
        String domain = normalized.substring(0, colon);
        String path = normalized.substring(colon + 1);
        Item item = GameRegistry.findItem(domain, path);
        return item == null ? null : new ItemStack(item, 1, damage);
    }

    /**
     * 补全省略的命名空间（1.20 的 {@code ResourceLocation}/{@code Ingredient} 对无冒号 id 默认 {@code minecraft}）。
     */
    public static String normalize(String itemId) {
        return itemId.indexOf(':') < 0 ? "minecraft:" + itemId : itemId;
    }

    private static void put(String itemId, String legacySpec) {
        FLATTENED_1_20_TO_1_7_10.put(itemId, legacySpec);
    }
}
