package com.tacz.guns.crafting;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 「1.7.10 矿词<b>没有候选</b>」时的等价原版物品回退表（移植特有机制，不是上游语义）。
 *
 * <p>
 * <b>为什么需要这张表</b>：枪包配方写的是 1.20 的 item tag（{@code forge:gems/amethyst} 等），
 * {@link GunSmithTableIngredient#toOreDictionaryName(String)} 把它们折成 1.7.10 矿词名
 * （{@code gemAmethyst} / {@code ingotNetherite} / {@code nuggetIron} / {@code ingotCopper}）。
 * 这四个矿词在 1.7.10 <b>原版</b>没有登记 —— 实读
 * {@code build/rfg/minecraft-src/java/net/minecraftforge/oredict/OreDictionary.java:59-134}
 * 的 {@code initVanillaEntries()} 静态注册块，原版只有
 * {@code logWood/plankWood/stickWood/ingotIron/ingotGold/nuggetGold/gemDiamond/gemEmerald/gemQuartz/
 * gemLapis/dustRedstone/dustGlowstone/blockGlass/…}，<b>没有</b> amethyst / netherite / iron nugget / copper。
 * 上一轮的形态是「解析成矿词名，无候选就 {@code item=null}」⇒ 配方能显示但
 * {@code test()} 恒 {@code false}，玩家看得到却永远凑不齐料（默认包共 <b>55 处材料引用</b>受影响：
 * {@code ingots/copper}×30、{@code gems/amethyst}×20、{@code ingots/netherite}×4、{@code nuggets/iron}×1）。
 *
 * <p>
 * <b>回退只在「矿词此刻没有任何候选」时才启用</b>（{@link #isRegistered(String)} 判空），因此：
 * <ul>
 * <li>装了 GT / IC2 等注册 {@code ingotCopper}、{@code gemAmethyst}、{@code ingotNetherite}、
 * {@code nuggetIron} 的整合包（GTNH 就是）<b>完全走原矿词</b>，材料成本与上游一致；</li>
 * <li>纯原版 1.7.10 环境退到下面登记的等价物，配方至少能合成出来。</li>
 * </ul>
 * 判定时机是枪包配方加载期（{@code resource/manager/GunSmithTableRecipeManager} 在
 * {@code CommonAssetsManager#reload()} 里跑，此时其它模组早已在 preInit~init 注册完矿词），所以判空是安全的。
 *
 * <p>
 * 本类同时被 {@link GunSmithTableIngredient}（枪包 {@code data/<ns>/recipes/**}，199 条）与
 * {@code init/ModRecipe}（代码注册的 18 条原版工作台配方，{@code forge:ingots/copper} → {@code ingotCopper}）
 * 复用，保证两处回退口径一致。
 */
public final class OreDictionaryFallback {

    private static final Logger LOGGER = LogManager.getLogger("TACZ");

    /**
     * 矿词名 → 回退原版物品注册名。
     *
     * <p>
     * 逐项理由写在 {@code static { }} 块里每一行 {@code put} 的上方注释；选择原则与
     * {@code GunSmithTableIngredient#MISSING_VANILLA_ITEM_SUBSTITUTION} 一致：
     * <b>同材质系 / 同功能类别</b>，且必须是 1.7.10 <b>确实存在</b>的物品
     * （登记名逐个对照过 {@code build/rfg/minecraft-src/java/net/minecraft/item/Item.java:102-275}
     * 的 {@code itemRegistry.addObject(...)}）。
     */
    private static final Map<String, String> EMPTY_ORE_FALLBACK = new LinkedHashMap<>();

    /**
     * 回退物品相对原材料的「面额」换算：1 个回退物品相当于多少个原需求单位。
     *
     * <p>
     * 只有 {@code nuggetIron} 需要：1.7.10 没有 {@code iron_nugget}，铁的最小单位是<b>锭</b>，
     * 而 {@code 9 nugget = 1 ingot}。若按 1:1 回退，默认包 {@code ammo/12g.json}（18 个铁粒）
     * 会变成 18 个铁锭 = 原成本 ×9；在这里按 9:1 折算（18 → 2 锭）才是<b>等价</b>替换。
     * 其余矿词的原材料与回退物品面额相同，不登记即默认 1。
     */
    private static final Map<String, Integer> FALLBACK_COUNT_DIVISOR = new LinkedHashMap<>();

    /** 已打过日志的矿词名（{@code /tacz reload} 会重复解析，避免每次重载刷 55 行）。 */
    private static final Set<String> LOGGED = new LinkedHashSet<>();

    static {
        // ---- forge:gems/amethyst（默认包 20 条：19 个瞄准镜/激光配件 + gun/aug） ----
        // 紫水晶碎片（1.17+ 紫水晶洞产物）→ 下界石英（minecraft:quartz，Item.java:255 "quartz"）。
        // 理由：1.7.10 原版里与「紫水晶碎片」同属「非金属水晶类宝石」的只有下界石英 ——
        // 钻石/绿宝石贵重一个数量级、青金石在原版主要是染料矿；且 1.20 的光学元件（observer /
        // daylight_detector）本身就是石英系的，用来做瞄准镜/激光最贴近。
        // 成本变化：紫水晶洞可无限再生（略便宜）→ 石英需要跑一趟下界（略贵），数量不变。
        EMPTY_ORE_FALLBACK.put("gemAmethyst", "minecraft:quartz");

        // ---- forge:ingots/netherite（默认包 4 条：m95 / m107 / hm50b / minigun） ----
        // 下界合金锭 → 金锭（minecraft:gold_ingot，Item.java:114 "gold_ingot"）。
        // 理由：下界合金锭的上游合成材料本身就是「4 金锭 + 4 下界合金碎片」，同属 ingot 形态，
        // 是 1.7.10 里唯一「与下界合金同源」的锭；不建议退到钻石（形态不同、且是宝石不是金属）。
        // 成本变化：显著下降（省掉远古残骸/下界挖掘这一整段），工艺等级下降；
        // 装 GTNH 等提供 ingotNetherite 的整合包时走原矿词，成本不变。
        EMPTY_ORE_FALLBACK.put("ingotNetherite", "minecraft:gold_ingot");

        // ---- forge:nuggets/iron（默认包 1 条：ammo/12g.json，count=18） ----
        // 铁粒 → 铁锭（minecraft:iron_ingot，Item.java:113 "iron_ingot"），并按 9:1 折算数量（见下表）。
        // 理由：1.7.10 没有铁粒，同材质的最小单位就是锭；18 铁粒 = 2 铁锭，折算后<b>成本完全不变</b>。
        // （备选的 minecraft:gold_nugget 面额相同但换了金属，会改变材料语义，故不采用。）
        EMPTY_ORE_FALLBACK.put("nuggetIron", "minecraft:iron_ingot");
        FALLBACK_COUNT_DIVISOR.put("nuggetIron", 9);

        // ---- forge:ingots/copper（默认包 30 条：25 个弹药物 + 4 个瞄准镜 + gun/m1866；
        // 另外 init/ModRecipe 的 ammo_workbench 也用它） ----
        // 铜锭 → 铁锭（minecraft:iron_ingot）。
        // 理由：1.7.10 原版没有任何铜质材料，弹壳/线管的「贱金属」位只能由铁锭接替
        // （金锭贵重一个档次、且是下界合金的回退物，避免与上面那条撞车）。
        // 成本变化：上升一档（铁锭比铜锭贵约 1~2 倍），数量不变；
        // 装 GT / IC2 时走原矿词 ingotCopper，成本不变。
        EMPTY_ORE_FALLBACK.put("ingotCopper", "minecraft:iron_ingot");
    }

    private OreDictionaryFallback() {}

    /** 矿词在<b>此刻</b>是否已有候选（1.7.10 原版矿词在类初始化时就有，模组矿词由各模组 preInit~init 注册）。 */
    public static boolean isRegistered(@Nullable String oreName) {
        return oreName != null && !oreName.isEmpty()
            && !OreDictionary.getOres(oreName)
                .isEmpty();
    }

    /**
     * 矿词无候选时给出等价原版物品（数量 1）；没有登记或物品不存在时返回 {@code null}。
     *
     * <p>
     * 逐项理由见 {@link #EMPTY_ORE_FALLBACK} 的登记注释。第一次命中时打一条 info（带矿词名与回退物品），
     * 之后同一矿词不再重复打 —— 这条日志就是「本环境走了回退」的唯一证据，实机排查时 grep 它。
     */
    @Nullable
    public static ItemStack fallbackItem(@Nullable String oreName) {
        if (oreName == null) {
            return null;
        }
        String itemName = EMPTY_ORE_FALLBACK.get(oreName);
        if (itemName == null) {
            return null;
        }
        ItemStack stack = findItemStack(itemName);
        if (stack == null) {
            LOGGER.warn(
                "[TACZ] ore dictionary fallback {} -> {} is missing, recipe material stays unresolvable",
                oreName,
                itemName);
            return null;
        }
        if (LOGGED.add(oreName)) {
            LOGGER.info(
                "[TACZ] ore dictionary {} has no entry in this environment, recipe material falls back to {}",
                oreName,
                itemName);
        }
        return stack;
    }

    /**
     * 给 {@code ShapedOreRecipe}/{@code ShapelessOreRecipe} 用的材料项：
     * 矿词已注册 → 返回矿词名（{@link String}，保持原成本与 GT 兼容）；
     * 未注册但有回退 → 返回回退 {@link ItemStack}；两者都不成立 → {@code null}（调用方应跳过该配方）。
     */
    @Nullable
    public static Object oreOrFallback(@Nullable String oreName) {
        if (oreName == null || oreName.isEmpty()) {
            return null;
        }
        if (isRegistered(oreName)) {
            return oreName;
        }
        return fallbackItem(oreName);
    }

    /**
     * 需求数量的等价换算（仅 {@code nuggetIron} 有 9:1，其余原样返回）。
     *
     * @param oreName   矿词名
     * @param requested 配方 json 里写的数量
     * @return 折算后应该收取的数量（向上取整，至少 1）
     */
    public static int scaleRequestedCount(@Nullable String oreName, int requested) {
        Integer divisor = oreName == null ? null : FALLBACK_COUNT_DIVISOR.get(oreName);
        if (divisor == null || divisor <= 1) {
            return requested;
        }
        return Math.max(1, (requested + divisor - 1) / divisor);
    }

    /**
     * 按注册名取物品（1.7.10 无 {@code ForgeRegistries}，用 {@link GameRegistry#findItem}）。
     *
     * <p>
     * 方块的物品形态同样命中：{@code Item.registerItems()}
     * （{@code build/rfg/minecraft-src/java/net/minecraft/item/Item.java:277-424}）按<b>方块注册名</b>
     * 为每个方块登记了 ItemBlock（{@code itemRegistry.addObject(Block.getIdFromBlock(block), s, object)}），
     * 所以 {@code minecraft:wool} / {@code minecraft:planks} 这类「tag 直接等于方块名」的写法也能解析。
     */
    @Nullable
    public static ItemStack findItemStack(String registryName) {
        int colon = registryName.indexOf(':');
        String domain = colon < 0 ? "minecraft" : registryName.substring(0, colon);
        String path = colon < 0 ? registryName : registryName.substring(colon + 1);
        Item item = GameRegistry.findItem(domain, path);
        return item == null ? null : new ItemStack(item);
    }
}
