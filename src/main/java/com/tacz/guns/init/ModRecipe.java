package com.tacz.guns.init;

import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.RecipeSorter;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.AmmoStorageUpgradeRecipe;
import com.tacz.guns.crafting.GunPegboardDyeRecipe;
import com.tacz.guns.crafting.OreDictionaryFallback;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 搬运自上游 1.20.1 {@code init/ModRecipe.java} + {@code data/tacz/recipes/*.json}（18 条）。
 *
 * <p>
 * <b>1.7.10 无 datapack / RecipeSerializer / RecipeType</b>：上游 18 个配方 JSON 全部改为代码注册
 * （逐条映射见 {@code tmp/port-staging/RECIPE_MAP.md}）。
 *
 * <p>
 * <b>1.7.10 矿词配方的真实机制（重要）</b>：
 * <ul>
 * <li>原版 {@code CraftingManager#addShapedRecipe} / {@code GameRegistry.addRecipe(ItemStack, Object...)}
 * <b>不解析 String 矿词</b>：它在 key→ItemStack 的 HashMap 里只接受 Item/Block/ItemStack，
 * String 矿词项会存成 null，随后 {@code hashmap.get(char).copy()} 直接 NPE（用户实测崩溃栈即此）；
 * 1.8+ 的 {@code CraftingHelper} 在 1.7.10 不存在。</li>
 * <li>1.7.10 的正确路径是 Forge 的 {@link ShapedOreRecipe}/{@link ShapelessOreRecipe}
 * （{@code RecipeSorter} 已为它们注册排序规则），因此本类**所有**带矿词或混合物品/矿词的配方都走
 * {@code new ShapedOreRecipe(...)} 再 {@code GameRegistry.addRecipe(IRecipe)}。</li>
 * <li>矿词条目本身：原版矿词（logWood/ingotIron/plankWood/blockGlass/gemDiamond…）在
 * {@code OreDictionary} 类初始化时注册；模组矿词（ingotCopper 等）由其它模组在 preInit~init 注册。
 * 因此 {@link #register()} 由 {@code CommonProxy#init} 调用（不在 preInit），且每条配方注册前用
 * {@link #hasAll(String...)} 预检，缺失矿词时 warn + 跳过该条，不让单条坏配方吃掉整批。</li>
 * </ul>
 *
 * <p>
 * 1.7.10 不存在的上游物品 id 替换（RECIPE_MAP 中标粗，逐条对照
 * {@code build/rfg/minecraft-src/java/net/minecraft/init/{Items,Blocks}.java} 的注册名核实）：
 * {@code iron_trapdoor}→铁块、{@code chiseled_quartz_block}→石英块 meta 1、
 * {@code quartz_slab}→石台阶 meta 7、{@code charcoal}→煤炭 meta 1。
 * {@code forge:ingots/copper} → 矿词 {@code ingotCopper}；纯原版无此矿词时按
 * {@link OreDictionaryFallback} 回退到铁锭（而不是整条跳过），装了 GT/IC2 时仍走矿词。
 *
 * <p>
 * 18 条配方引用的<b>其余</b>材料全部是 1.7.10 原版就有的注册名或矿词，无需替换：
 * {@code minecraft:iron_block/glass/chest/redstone_torch/iron_bars/minecart/glowstone_dust/flint/
 * sugar/redstone/quartz_stairs}、{@code Items.iron_ingot/gold_ingot/diamond/coal(meta 1)} 与矿词
 * {@code logWood/plankWood/ingotIron/ingotGold/gemDiamond/blockGlass}（后者全部由
 * {@code OreDictionary#initVanillaEntries}
 * （{@code build/rfg/minecraft-src/java/net/minecraftforge/oredict/OreDictionary.java:59-134}）在类初始化时注册）。
 */
public class ModRecipe {

    public static void register() {
        registerRecipeSorters();
        registerVanillaRecipes();
        registerCustomRecipes();
    }

    /**
     * 把本模组的自研 {@link IRecipe} 登记进 Forge 的 {@link RecipeSorter}。
     *
     * <p>
     * 1.7.10 依据：{@code net/minecraftforge/oredict/RecipeSorter.java:192-218}（{@code getPriority}）
     * 对未登记的配方类会打印 {@code "Unknown recipe class! ..."} 并返回优先级 0；而
     * {@code RecipeSorter.sortCraftManager()} 只在 {@code FMLLoadCompleteEvent}
     * （{@code ForgeModContainer.java:295-300}）执行一次，所以登记必须在那之前
     * （本方法由 {@code CommonProxy#init} 调用，早于 loadComplete）。
     * 不登记的实际后果：优先级 0 = 排在所有已登记配方之后，且日志刷警告。
     *
     * <p>
     * 依赖串取自 1.7.10 原版 {@code minecraft:shaped / forge:shapedore} 的既有排序约定
     * （{@code RecipeSorter.java:115-124}）：两个自研配方都按 3×3 形状匹配，排在原版 shaped 之后，
     * 保证原版配方先命中（"原版优先" 是本移植的既定策略）。
     */
    private static void registerRecipeSorters() {
        RecipeSorter.register(
            "tacz:ammo_storage_upgrade",
            AmmoStorageUpgradeRecipe.class,
            RecipeSorter.Category.SHAPED,
            "after:minecraft:shaped");
        RecipeSorter.register(
            "tacz:gun_pegboard_dye",
            GunPegboardDyeRecipe.class,
            RecipeSorter.Category.SHAPELESS,
            "after:minecraft:shapeless");
    }

    // ------------------------------------------------------------------------------------------
    // 原版工作台配方（14 条）
    // ------------------------------------------------------------------------------------------

    private static void registerVanillaRecipes() {
        // ammo_storage：8 铁块 → 铁弹药库
        addShaped("ammo_storage", output(ModBlocks.AMMO_STORAGE, 1), "III", "I I", "III", 'I', Blocks.iron_block);

        // gun_smith_table：原木 + 铁 + 铁块
        if (hasAll("logWood", "ingotIron")) {
            addShaped(
                "gun_smith_table",
                output(ModItems.GUN_SMITH_TABLE, 1),
                "LLL",
                "IBI",
                "I I",
                'L',
                "logWood",
                'I',
                "ingotIron",
                'B',
                Blocks.iron_block);
        }

        // ammo_workbench（工作台 A）：铜锭 + 铁锭 + 弹药盒 → 带 BlockId NBT
        // 1.7.10：ingotCopper 只有 GT/IC2 之类的模组注册；纯原版没有铜，按 OreDictionaryFallback
        // 回退到等价原版物品（铁锭）而不是整条跳过 —— 否则纯原版环境连弹药工作台都造不出来。
        // 装了 GT 时 oreOrFallback 返回矿词名，成本与上游一致。
        Object copper = OreDictionaryFallback.oreOrFallback("ingotCopper");
        if (copper != null && hasAll("ingotIron")) {
            addShaped(
                "ammo_workbench",
                withBlockId(output(ModItems.WORKBENCH_111, 1), "tacz:ammo_workbench"),
                "CC",
                "IB",
                'C',
                copper,
                'I',
                "ingotIron",
                'B',
                ModItems.AMMO_BOX);
        } else {
            GunMod.LOGGER.warn("[TACZ] 1.7.10 环境既无矿词 ingotCopper 也无回退物，跳过 ammo_workbench 配方注册");
        }

        // attachment_workbench（工作台 C）：玻璃 + 红石火把 + 铁锭 + 箱子
        if (hasAll("blockGlass", "ingotIron")) {
            addShaped(
                "attachment_workbench",
                withBlockId(output(ModItems.WORKBENCH_121, 1), "tacz:attachment_workbench"),
                "GCG",
                "G G",
                "IBI",
                'G',
                "blockGlass",
                'C',
                Blocks.redstone_torch,
                'I',
                "ingotIron",
                'B',
                Blocks.chest);
        }

        // 铁/金/钻石 弹药盒：Level NBT
        if (hasAll("ingotIron", "plankWood")) {
            addShaped(
                "iron_ammo_box",
                withAmmoBoxLevel(output(ModItems.AMMO_BOX, 1), 0),
                "PIP",
                "P P",
                "PPP",
                'I',
                "ingotIron",
                'P',
                "plankWood");
        }
        if (hasAll("ingotGold", "plankWood")) {
            addShaped(
                "gold_ammo_box",
                withAmmoBoxLevel(output(ModItems.AMMO_BOX, 1), 1),
                "PIP",
                "P P",
                "PPP",
                'I',
                "ingotGold",
                'P',
                "plankWood");
        }
        if (hasAll("gemDiamond", "plankWood")) {
            addShaped(
                "diamond_ammo_box",
                withAmmoBoxLevel(output(ModItems.AMMO_BOX, 1), 2),
                "PIP",
                "P P",
                "PPP",
                'I',
                "gemDiamond",
                'P',
                "plankWood");
        }

        // 挂板（iron_trapdoor 1.7.10 不存在 → 铁块）
        addShaped(
            "gun_pegboard",
            output(ModItems.GUN_PEGBOARD, 9),
            "IBI",
            "BTB",
            "IBI",
            'I',
            Items.iron_ingot,
            'B',
            Blocks.iron_bars,
            'T',
            Blocks.iron_block);
        addShaped(
            "glow_gun_pegboard",
            output(ModItems.GLOW_GUN_PEGBOARD, 9),
            "IGI",
            "BTB",
            "IGI",
            'I',
            Items.iron_ingot,
            'G',
            Items.glowstone_dust,
            'B',
            Blocks.iron_bars,
            'T',
            Blocks.iron_block);

        // 枪械展示柜
        if (hasAll("ingotIron", "blockGlass")) {
            addShaped(
                "gun_display_case",
                output(ModItems.GUN_DISPLAY_CASE, 4),
                "III",
                "IGI",
                "III",
                'I',
                "ingotIron",
                'G',
                "blockGlass");
        }

        // 靶标
        if (hasAll("ingotIron", "plankWood")) {
            addShaped(
                "target",
                output(ModItems.TARGET, 1),
                "III",
                "IRI",
                " P ",
                'I',
                "ingotIron",
                'R',
                Items.redstone,
                'P',
                "plankWood");
        }

        // 标靶车
        addShaped(
            "target_minecart",
            output(ModItems.TARGET_MINECART, 1),
            "A",
            "B",
            'A',
            ModItems.TARGET,
            'B',
            Items.minecart);

        // 石像：quartz_slab → 石台阶 meta 7；chiseled_quartz_block → 石英块 meta 1
        addShaped(
            "statue",
            output(ModItems.STATUE, 1),
            " S ",
            "QCQ",
            " C ",
            'S',
            new ItemStack(Blocks.stone_slab, 1, 7),
            'Q',
            Blocks.quartz_stairs,
            'C',
            new ItemStack(Blocks.quartz_block, 1, 1));

        // 火药：charcoal → 煤炭 meta 1
        addShapeless(
            "gunpowder",
            new ItemStack(Items.gunpowder, 3),
            Items.flint,
            Items.sugar,
            Items.sugar,
            new ItemStack(Items.coal, 1, 1),
            new ItemStack(Items.coal, 1, 1),
            new ItemStack(Items.coal, 1, 1));
    }

    // ------------------------------------------------------------------------------------------
    // 自研配方（4 条）
    // ------------------------------------------------------------------------------------------

    private static void registerCustomRecipes() {
        // ammo_storage_gold / ammo_storage_diamond：上游 tacz:ammo_storage_upgrade（CustomRecipe）
        registerCustom("ammo_storage_gold", () -> AmmoStorageUpgradeRecipe.createRecipe("ammo_storage_gold"));
        registerCustom("ammo_storage_diamond", () -> AmmoStorageUpgradeRecipe.createRecipe("ammo_storage_diamond"));
        // gun_pegboard_dye：上游 tacz:gun_pegboard_dye（CustomRecipe）
        registerCustom("gun_pegboard_dye", GunPegboardDyeRecipe::new);
        // misc/blood_strike_1：上游 tacz:gun_smith_table_crafting —— <b>不在这里注册</b>。
        // 工作台配方不进 CraftingManager（GunSmithTableRecipe 不实现 IRecipe，塞进去会让原版工作台
        // ClassCastException），统一由 resource 层扫描枪包 data/<ns>/recipes/** 后登记进静态表。
        // 原 TODO(port)「等 GunPackLoader#readPackData("recipes") 接线」已于 r2 完成：
        // 见 resource/manager/GunSmithTableRecipeManager（在 CommonAssetsManager 重载管线里注册）。
        // 注：data/tacz/recipes/misc/blood_strike_1.json 存在但读不到——readPackData 只扫枪包来源
        // （GunPackLoader.java:206-222），jar 根 data/ 不是包来源；产物是 1.7.10 无的画变体，材料等价
        // 原版画配方，已裁决不注册、不损失可获得物。
    }

    // ------------------------------------------------------------------------------------------
    // 注册辅助（1.7.10 矿词/坏配方隔离）
    // ------------------------------------------------------------------------------------------

    /**
     * 1.7.10：带 String 矿词的配方必须用 Forge 的 {@link ShapedOreRecipe} 注册；
     * 原版 {@code GameRegistry.addRecipe(ItemStack, Object...)} 不支持矿词（会在 CraftingManager 里 NPE）。
     */
    private static void addShaped(String name, @Nullable ItemStack output, Object... params) {
        if (output == null || output.getItem() == null) {
            GunMod.LOGGER.warn("[TACZ] 配方 {} 的产物物品尚未注册，跳过该配方", name);
            return;
        }
        try {
            GameRegistry.addRecipe(new ShapedOreRecipe(output, params));
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ] 注册 shaped 配方 {} 失败，已跳过：{}", name, e.toString());
        }
    }

    private static void addShapeless(String name, @Nullable ItemStack output, Object... params) {
        if (output == null || output.getItem() == null) {
            GunMod.LOGGER.warn("[TACZ] 配方 {} 的产物物品尚未注册，跳过该配方", name);
            return;
        }
        try {
            GameRegistry.addRecipe(new ShapelessOreRecipe(output, params));
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ] 注册 shapeless 配方 {} 失败，已跳过：{}", name, e.toString());
        }
    }

    private static void registerCustom(String name, Supplier<IRecipe> factory) {
        try {
            IRecipe recipe = factory.get();
            if (recipe == null) {
                GunMod.LOGGER.warn("[TACZ] 自定义配方 {} 创建失败（依赖物品未注册），已跳过", name);
                return;
            }
            GameRegistry.addRecipe(recipe);
        } catch (RuntimeException e) {
            GunMod.LOGGER.warn("[TACZ] 注册自定义配方 {} 失败，已跳过：{}", name, e.toString());
        }
    }

    /** 预检矿词是否都已注册（1.7.10 的 OreDictionary 原版条目在类初始化时注册、模组条目稍后注册）。 */
    private static boolean hasAll(String... oreNames) {
        boolean allPresent = true;
        for (String oreName : oreNames) {
            if (OreDictionary.getOres(oreName)
                .isEmpty()) {
                GunMod.LOGGER.warn("[TACZ] 矿词 {} 尚未注册，跳过依赖它的配方", oreName);
                allPresent = false;
            }
        }
        return allPresent;
    }

    @Nullable
    private static ItemStack output(Item item, int count) {
        if (item == null) {
            return null;
        }
        return new ItemStack(item, count);
    }

    @Nullable
    private static ItemStack output(Block block, int count) {
        if (block == null) {
            return null;
        }
        Item item = Item.getItemFromBlock(block);
        return item == null ? null : new ItemStack(item, count);
    }

    @Nullable
    private static ItemStack withBlockId(@Nullable ItemStack stack, String blockId) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("BlockId", blockId);
        stack.setTagCompound(tag);
        return stack;
    }

    @Nullable
    private static ItemStack withAmmoBoxLevel(@Nullable ItemStack stack, int level) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Level", level);
        stack.setTagCompound(tag);
        return stack;
    }
}
