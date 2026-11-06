package com.tacz.guns.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.tacz.guns.mcshim.GsonHelper;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 枪械工作台配方的一项材料。
 *
 * <p>
 * 上游：tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/crafting/GunSmithTableIngredient.java
 * （{@code Ingredient ingredient} + {@code int count}）。
 *
 * <p>
 * 1.7.10 差异：1.7.10 完全没有 {@code net.minecraft.world.item.crafting.Ingredient}（item/tag/多选一匹配器是
 * 1.13+ 才有），唯一等价物是 {@link ItemStack} + {@link OreDictionary} 矿词名，故用两个互斥字段表达上游一个
 * {@code Ingredient}：{@link #item}（对应 {@code {"item":"ns:path"}}）、{@link #tag}（对应
 * {@code {"tag":"forge:ingots/iron"}}，先经 {@link #toOreDictionaryName(String)} 折算成 1.7.10 矿词名）、
 * {@link #count}（需求数量，仅在走矿词回退且面额不同时才折算，见 {@link #getCount()}）。
 *
 * <p>
 * 匹配语义与上游 {@code Ingredient#test} 对齐：只比较「物品 + damage」（矿词项遍历
 * {@link OreDictionary#getOres(String)} 的全部候选），<b>不比较 NBT</b>（上游 item ingredient 同样如此）。
 *
 * <p>
 * 三级解析链（1.20 tag → 1.7.10 可匹配物）：① {@link #TAG_TO_VANILLA_ITEM}：既无矿词、又只有一个唯一等价物品的
 * tag 直接给具体物品；② {@link #toOreDictionaryName(String)} → 1.7.10 矿词名，装了 GT/IC2 等时原样走矿词，
 * 成本与上游一致；③ {@link OreDictionaryFallback}：矿词在该环境没有候选时回退等价原版物品，纯原版也能把这 55 处
 * 材料凑齐。第 3 级是<b>平台差异</b>而非上游语义，只在矿词判空时生效，每次命中打一条 info 日志。
 *
 * <p>
 * 跨包契约：{@code resource/serialize/GunSmithTableIngredientSerializer} 调用
 * {@link #fromJson(JsonElement, int)}，item/tag 子树的解析在本类完成（docs/PORT_STATUS.md「跨包契约」段）。
 */
public class GunSmithTableIngredient {

    private static final String ITEM = "item";
    private static final String TAG = "tag";

    /**
     * 1.20 tag → 1.7.10 <b>具体物品</b>的折算表（这些 tag 在 1.7.10 既没有矿词、又只有一个唯一等价物品）。
     *
     * <p>
     * {@link #toOreDictionaryName(String)} 只处理「有对应矿词」的 tag，而 {@code forge:gunpowder} /
     * {@code forge:leather} / {@code forge:rods/blaze} 在 1.7.10 的 {@code OreDictionary} 里<b>没有任何登记</b>
     * （build/rfg/minecraft-src/java/net/minecraftforge/oredict/OreDictionary.java 的静态注册块只有
     * ingotIron/ingotGold/gemDiamond/gemLapis/gemQuartz/dustRedstone/dustGlowstone/blockGlass/stickWood/
     * logWood/plankWood/nuggetGold…，没有 gunpowder / leather / blaze）。不折算则这些材料解析成 {@code item=null}
     * 且矿词无候选 ⇒ {@link #test} 恒 false ⇒ 整条配方永远做不出来（默认包的 25 条弹药配方全卡在
     * {@code forge:gunpowder}）。
     *
     * <p>
     * 只收「1.7.10 原版确有该物品」的三项；{@code forge:gems/amethyst}/{@code forge:ingots/netherite}/
     * {@code forge:nuggets/iron}/{@code forge:ingots/copper} 折出来的是矿词（装了 GT/IC2 才存在），不能硬折成
     * 具体物品（会破坏 GTNH 的原成本），改由 {@link OreDictionaryFallback} 在矿词无候选时回退。
     */
    private static final Map<String, String> TAG_TO_VANILLA_ITEM = Map.of(
        "forge:gunpowder",
        "minecraft:gunpowder",
        "forge:leather",
        "minecraft:leather",
        "forge:rods/blaze",
        "minecraft:blaze_rod");

    /**
     * 1.16+ 才有的原版物品 → 1.7.10 替代表（枪包 json 是按 1.20.1 写的）。
     *
     * <p>
     * 默认枪包 199 条配方里有 11 条直接写了 1.7.10 不存在的物品 id，{@link #findItemStack} 返回 {@code null}
     * 后本类抛 {@code JsonSyntaxException: Unknown item '...'} ⇒ 整条配方被跳过，这些配件在工作台里连列表都不出现：
     * {@code crying_obsidian} ×6（ammo_mod_fmj/he/hp/i/overpressure_recoil/slug）、{@code shroomlight} ×3
     * （scope_reap_ir / scope_xl50 / sight_infitac_fmp13）、{@code observer} ×3（scope_xl50 / scope_xm157 /
     * sight_uh1）、{@code end_crystal} ×1（ammo_mod_he）、{@code copper_block} ×1（ammo_mod_slug）。
     *
     * <p>
     * 替身取 1.7.10 原版里同名系 / 同功能类别的唯一物品：crying_obsidian → obsidian；shroomlight → glowstone
     * （唯一的发光方块类材料）；observer → daylight_detector（唯一的光照探测元件，与光学配件语义最近）；
     * end_crystal → ender_eye（物品形态是 1.9 才加的，同属末地系材料）；copper_block → iron_block（无铜，
     * 同为「9 锭 = 1 块」的金属块）。
     *
     * <p>
     * 与 {@link OreDictionaryFallback} 的分工：本表处理 {@code "item"} 字段里完全不存在的注册名（不替换就整条
     * 配方加载失败），矿词侧由 {@code OreDictionaryFallback} 处理（不替换则配方能显示却永远做不出）。
     * <b>这是平台差异，不是上游语义</b>：替换只在原物品于 1.7.10 完全不存在时发生并记 debug 日志；
     * 若日后接入注册了这些物品的模组，{@link #findItemStack} 会优先命中原物品，本表自然失效。
     */
    private static final Map<String, String> MISSING_VANILLA_ITEM_SUBSTITUTION = Map.of(
        "minecraft:crying_obsidian",
        "minecraft:obsidian",
        "minecraft:shroomlight",
        "minecraft:glowstone",
        "minecraft:observer",
        "minecraft:daylight_detector",
        // 实机日志追加的两项（21:56 实测 environment 仍然缺）：
        // end_crystal 的**物品**是 1.9 才加的（1.7.10 只有 EntityEnderCrystal 实体）→ 用同族的末影之眼；
        // copper_block 在纯原版 1.7.10 不存在（GTNH 里由 GT 提供，但最小测试环境没有）→ 用铁块（同为「金属块」类材料）。
        "minecraft:end_crystal",
        "minecraft:ender_eye",
        "minecraft:copper_block",
        "minecraft:iron_block");

    /** 代表物品（数量 1）；矿词材料且有候选时缓存第一个候选。 */
    @Nullable
    private final ItemStack item;
    /** 1.7.10 矿词名；仅当配方写的是 {@code tag} 时非空。 */
    @Nullable
    private final String tag;
    private final int count;

    public GunSmithTableIngredient(ItemStack item, int count) {
        if (item == null || item.getItem() == null) {
            throw new IllegalArgumentException("GunSmithTableIngredient item must not be empty");
        }
        this.item = single(item);
        this.tag = null;
        this.count = Math.max(1, count);
    }

    public GunSmithTableIngredient(String tag, int count) {
        if (tag == null || tag.isEmpty()) {
            throw new IllegalArgumentException("GunSmithTableIngredient tag must not be empty");
        }
        // 该矿词在本环境没有候选（纯原版没装提供它的模组）→ 后面走 OreDictionaryFallback 回退
        boolean oreMissing = !OreDictionaryFallback.isRegistered(tag);
        this.item = resolveTagItem(tag);
        this.tag = tag;
        // 走回退时按「面额」折算需求数量：1.7.10 没有 iron_nugget，nuggetIron 退到铁锭必须 9:1
        // （18 铁粒 = 2 铁锭），否则默认包 ammo/12g.json 会从 2 锭的成本涨成 18 锭。其余矿词 1:1。
        // 装了 GT/IC2 等提供原矿词时 oreMissing=false，数量与上游 json 完全一致。
        this.count = Math.max(1, oreMissing ? OreDictionaryFallback.scaleRequestedCount(tag, count) : count);
    }

    /**
     * 上游 {@code Ingredient#fromJson} 的 1.7.10 版
     * （{@code {"item":"ns:path"}} 或 {@code {"tag":"ore:name"}}）。
     *
     * @param element 上游 {@code materials[].item} 子树
     * @param count   已读取的数量（{@code materials[].count}）
     */
    public static GunSmithTableIngredient fromJson(JsonElement element, int count) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonSyntaxException("Expected " + element + " to be an item object");
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has(TAG) && object.get(TAG)
            .isJsonPrimitive()) {
            String tag = GsonHelper.getAsString(object, TAG);
            // 1.7.10 无对应矿词、但有唯一原版物品的 tag → 直接走「具体物品」分支（tag=null）
            String direct = TAG_TO_VANILLA_ITEM.get(tag.toLowerCase(Locale.ROOT));
            if (direct != null) {
                ItemStack stack = findItemStack(direct);
                if (stack != null) {
                    return new GunSmithTableIngredient(stack, count);
                }
            }
            return new GunSmithTableIngredient(toOreDictionaryName(tag), count);
        }
        if (object.has(ITEM)) {
            JsonElement itemElement = object.get(ITEM);
            String itemName = itemElement.isJsonPrimitive() ? itemElement.getAsString()
                : GsonHelper.getAsString(itemElement.getAsJsonObject(), ITEM);
            ItemStack stack = findItemStack(itemName);
            if (stack == null) {
                // 1.16+ 原版物品在 1.7.10 不存在 → 用登记过的替身（见 MISSING_VANILLA_ITEM_SUBSTITUTION）
                String substitute = MISSING_VANILLA_ITEM_SUBSTITUTION.get(itemName);
                if (substitute != null) {
                    stack = findItemStack(substitute);
                    if (stack != null) {
                        LogManager.getLogger("TACZ")
                            .debug(
                                "[TACZ] recipe ingredient {} does not exist in 1.7.10, substituted with {}",
                                itemName,
                                substitute);
                    }
                }
            }
            if (stack == null) {
                throw new JsonSyntaxException("Unknown item '" + itemName + "'");
            }
            return new GunSmithTableIngredient(stack, count);
        }
        throw new JsonSyntaxException("Expected " + object + " to have a item or tag member");
    }

    /** 便捷构造：直接给 {@link Item}。 */
    public static GunSmithTableIngredient of(Item item, int count) {
        return new GunSmithTableIngredient(new ItemStack(item), count);
    }

    /**
     * 上游 {@code getIngredient()}。
     *
     * <p>
     * 1.7.10 差异：上游返回 {@code Ingredient}，1.7.10 没有该类型，因此返回「代表栈」（数量 1）。
     * 矿词材料在解析时缓存第一个候选；矿词当前无任何物品时返回 {@code null}
     * （上游此情形下 {@code Ingredient} 也是一个不匹配任何物品的空匹配器）。
     */
    @Nullable
    public ItemStack getIngredient() {
        return item;
    }

    /** 具体物品（{@code getIngredient()} 的清晰别名；可能为 {@code null}）。 */
    @Nullable
    public ItemStack getItem() {
        return item;
    }

    /** 1.7.10 矿词名；具体物品材料返回 {@code null}。 */
    @Nullable
    public String getTag() {
        return tag;
    }

    /**
     * 需求数量（{@code materials[].count}）。
     *
     * <p>
     * 走 {@link OreDictionaryFallback} 回退时，可能与配方 json 里写的数字不同：
     * {@code nuggetIron} 退到铁锭按 9:1 折算（json 的 18 → 2 个铁锭），其余矿词原样。
     * 装 GT/IC2 等提供原矿词时与上游 json 完全一致。
     */
    public int getCount() {
        return count;
    }

    /**
     * 上游 {@code Ingredient#test(ItemStack)}。
     *
     * <p>
     * 矿词分支的语义必须与 {@link #getMatchingStacks()} <b>完全一致</b>：
     * 矿词有候选 → 逐个候选比对；矿词没有候选 → 比对「代表物品」（此时代表物品可能来自
     * {@link OreDictionaryFallback} 的回退，例如 {@code gemAmethyst} → 下界石英）。
     * 两条路径任一不一致，表现都是「配方能显示、材料也够，就是做不出来」。
     */
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        if (tag != null) {
            List<ItemStack> candidates = OreDictionary.getOres(tag);
            if (candidates.isEmpty()) {
                // 矿词在 1.7.10 没有登记时（或该环境没装提供它的模组，见 OreDictionaryFallback），
                // 必须退回到「代表物品」匹配。
                // 这条回退与 getMatchingStacks() 的空矿词分支语义一致。
                return item != null && itemMatches(item, stack);
            }
            for (ItemStack candidate : candidates) {
                if (itemMatches(candidate, stack)) {
                    return true;
                }
            }
            return false;
        }
        return item != null && itemMatches(item, stack);
    }

    /**
     * 上游 {@code Ingredient#getItems()} 的 1.7.10 版：可匹配的全部代表栈（数量 1）。
     *
     * <p>
     * 矿词无候选时返回回退物品（{@link OreDictionaryFallback}）或「tag 原样等于物品名」的兜底物品；
     * 两者都没有才返回空表 —— GUI 用空表表示「这项材料在本环境无解」
     * （{@code client/gui/GunSmithTableScreen#renderIngredient} 会直接跳过不画）。
     */
    public List<ItemStack> getMatchingStacks() {
        if (tag != null) {
            List<ItemStack> ores = OreDictionary.getOres(tag);
            if (ores.isEmpty()) {
                return item == null ? List.<ItemStack>of() : List.of(item);
            }
            List<ItemStack> result = new ArrayList<>(ores.size());
            for (ItemStack ore : ores) {
                result.add(single(ore));
            }
            return result;
        }
        return item == null ? List.<ItemStack>of() : List.of(item);
    }

    // ================================================================================================
    // 1.20 tag → 1.7.10 矿词名 折算
    // ================================================================================================

    /**
     * 把上游 1.20 的 tag id 折算成 1.7.10 的矿词名。
     *
     * <p>
     * 差异原因：1.7.10 只有 {@code OreDictionary}，命名体系与 1.20 的 item tag 不同
     * （{@code forge:ingots/iron} → {@code ingotIron}，{@code minecraft:planks} → {@code plankWood} 等）。
     * 常见 {@code forge:<复数>/<单数>} 按 1.7.10 Forge {@code OreDictionary} 的命名规则换算
     * （{@code ingots}→{@code ingot}、{@code gems}→{@code gem}、{@code storage_blocks}→{@code block}、
     * {@code ores}→{@code ore}、{@code dusts}→{@code dust}、{@code nuggets}→{@code nugget}、{@code rods}→{@code stick}）；
     * 未识别的写法原样返回，由 {@link OreDictionary} 自行判定。
     */
    public static String toOreDictionaryName(String tag) {
        if (tag == null || tag.isEmpty()) {
            return tag;
        }
        String lower = tag.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "minecraft:planks":
                return "plankWood";
            case "minecraft:logs":
            case "minecraft:logs_that_burn":
                return "logWood";
            case "minecraft:wooden_slabs":
                return "slabWood";
            case "minecraft:wooden_stairs":
                return "stairWood";
            case "forge:glass":
            case "forge:glass/silica":
                return "blockGlass";
            case "forge:rods/wooden":
                return "stickWood";
            default:
                break;
        }
        int slash = lower.indexOf('/');
        if (slash <= 0) {
            return tag;
        }
        String prefix = lower.substring(0, slash);
        String name = lower.substring(slash + 1);
        int colon = prefix.indexOf(':');
        if (colon >= 0) {
            prefix = prefix.substring(colon + 1);
        }
        String stem;
        switch (prefix) {
            case "ingots":
                stem = "ingot";
                break;
            case "gems":
                stem = "gem";
                break;
            case "storage_blocks":
                stem = "block";
                break;
            case "ores":
                stem = "ore";
                break;
            case "dusts":
                stem = "dust";
                break;
            case "nuggets":
                stem = "nugget";
                break;
            case "rods":
                stem = "stick";
                break;
            default:
                return tag;
        }
        if (name.isEmpty()) {
            return tag;
        }
        return stem + capitalize(name);
    }

    private static String capitalize(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean upper = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_' || c == '/' || c == '.') {
                upper = true;
                continue;
            }
            builder.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return builder.toString();
    }

    /**
     * 矿词材料的代表物品：矿词已有候选则取第一个；否则先查 {@link OreDictionaryFallback} 的等价原版物品回退表，
     * 再对 {@code minecraft:<name>} 形式退回 1.7.10 原版物品（例如 {@code minecraft:wool}，1.7.10 没有 wool 矿词）。
     *
     * <p>
     * 两条兜底的分工（键互不相交，先后无所谓，这里先查表只为让主路径一眼可见）：
     * <ul>
     * <li>{@link OreDictionaryFallback}：键是<b>不带前缀的矿词名</b>（{@code gemAmethyst} /
     * {@code ingotNetherite} / {@code nuggetIron} / {@code ingotCopper}），处理「纯原版缺候选、
     * 装了 GT/IC2 就该用真材料」的矿词；</li>
     * <li>{@code minecraft:} 前缀：处理「tag 原样就是物品注册名」的写法（{@code minecraft:wool}），
     * 直接按 ItemBlock 查（1.7.10 的方块物品同样在 item registry 里，见
     * {@link OreDictionaryFallback#findItemStack(String)}）。</li>
     * </ul>
     */
    @Nullable
    private static ItemStack resolveTagItem(String oreName) {
        List<ItemStack> ores = OreDictionary.getOres(oreName);
        if (!ores.isEmpty()) {
            return single(ores.get(0));
        }
        ItemStack fallback = OreDictionaryFallback.fallbackItem(oreName);
        if (fallback != null) {
            return fallback;
        }
        if (oreName.startsWith("minecraft:")) {
            return findItemStack(oreName);
        }
        return null;
    }

    /**
     * 按注册名取物品（1.7.10 无 {@code ForgeRegistries}，用 {@link GameRegistry#findItem}）；
     * 实现与 {@link OreDictionaryFallback#findItemStack(String)} 同一份，直接复用避免两处口径漂移。
     */
    @Nullable
    private static ItemStack findItemStack(String registryName) {
        return OreDictionaryFallback.findItemStack(registryName);
    }

    /** 物品 + damage 匹配（{@link OreDictionary#WILDCARD_VALUE} 视为通配）。 */
    private static boolean itemMatches(@Nullable ItemStack expected, @Nullable ItemStack actual) {
        if (expected == null || actual == null || expected.getItem() == null || actual.getItem() == null) {
            return false;
        }
        if (expected.getItem() != actual.getItem()) {
            return false;
        }
        int damage = expected.getItemDamage();
        return damage == OreDictionary.WILDCARD_VALUE || damage == actual.getItemDamage();
    }

    private static ItemStack single(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    /** 调试用：全部候选的「注册名@damage」。 */
    public List<String> describeCandidates() {
        List<ItemStack> stacks = getMatchingStacks();
        List<String> names = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            names.add(describe(stack));
        }
        if (names.isEmpty()) {
            names.add("none");
        }
        return names;
    }

    private static String describe(@Nullable ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "null";
        }
        return stack.getItem()
            .getUnlocalizedName() + "@"
            + stack.getItemDamage();
    }

    @Override
    public String toString() {
        return "GunSmithTableIngredient{" + (tag != null ? "tag=" + tag : "item=" + describe(item))
            + ", count="
            + count
            + '}';
    }
}
