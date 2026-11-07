package com.tacz.guns.compat.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

import com.tacz.guns.crafting.AmmoStorageUpgradeRecipe;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI 的「弹药库升级」配方页（铁弹药库 + 8×金锭 → 金弹药库；金弹药库 + 8×钻石 → 钻石弹药库）。
 *
 * <p>
 * 上游 1.20.1 在 JEI 里把 {@link AmmoStorageUpgradeRecipe} 展开成原版 {@code ShapedRecipe} 再塞进
 * {@code RecipeTypes.CRAFTING}（{@code GunModPlugin#registerRecipes} 里那句
 * {@code AmmoStorageUpgradeRecipe.createRecipe(...).map(...)}）。1.7.10 侧该配方是自定义
 * {@code IRecipe}，NEI 内置的 {@code ShapedRecipeHandler} 不认（只认 {@code ShapedRecipes}/
 * {@code ShapedOreRecipe}/{@code ShapelessRecipes}），因此单开一个 handler 展示同样的 3×3 形状。
 *
 * <p>
 * 布局直接用原版工作台坐标：3×3 在 (25,6) 起、箭头在 (90,27)、产物在 (119,24)，与 NEI 原版工作台
 * 页面观感一致。材料/产物的 NBT（{@code BlockEntityTag} 库存）不参与显示，成品格只展示方块物品本身。
 */
public class AmmoStorageUpgradeHandler extends TemplateRecipeHandler {

    static final String HANDLER_ID = "tacz:nei/ammo_storage_upgrade";
    private static final int GRID_X = 25;
    private static final int GRID_Y = 6;
    private static final int RESULT_X = 119;
    private static final int RESULT_Y = 24;
    private static final int ARROW_X = 90;
    private static final int ARROW_Y = 27;
    private static final int ARROW_WIDTH = 14;
    private static final int ARROW_HEIGHT = 12;

    @Override
    public void loadTransferRects() {
        // 让「点箭头 / 对弹药库物品按 U」能打开全部升级配方。这里只用到静态常量，
        // 可以安全地覆写 loadTransferRects()（构造器调用它时本类实例字段还没初始化，
        // 携带实例字段的 GunSmithTableRecipeHandler 只能在构造器体内补登，见那个类）。
        this.transferRects
            .add(new RecipeTransferRect(new Rectangle(ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT), HANDLER_ID));
    }

    @Override
    public String getHandlerId() {
        return HANDLER_ID;
    }

    @Override
    public String getRecipeName() {
        // 上游这个页面合并进原版 CRAFTING，页名就是「工作台」；本页在 1.7.10 单开，
        // 用铁弹药库的物品名当页名（tile.tacz.ammo_storage.name，随语言表变化）。
        ItemStack storage = stack(findItem("ammo_storage"));
        return storage == null ? StatCollector.translateToLocal("tile.tacz.ammo_storage.name")
            : storage.getDisplayName();
    }

    @Override
    public String getGuiTexture() {
        return "nei:textures/gui/recipebg.png";
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        // "all" 是 NEI 的「全部配方」页；HANDLER_ID 是 transferRect 的 outputId。
        if ("all".equals(outputId) || HANDLER_ID.equals(outputId)) {
            for (Upgrade upgrade : getUpgrades()) {
                arecipes.add(new CachedUpgrade(upgrade));
            }
            return;
        }
        super.loadCraftingRecipes(outputId, results);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result == null) {
            return;
        }
        for (Upgrade upgrade : getUpgrades()) {
            if (upgrade.result() != null && upgrade.result()
                .getItem() == result.getItem()) {
                arecipes.add(new CachedUpgrade(upgrade));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) {
            return;
        }
        for (Upgrade upgrade : getUpgrades()) {
            if (matches(upgrade.from(), ingredient) || matches(upgrade.material(), ingredient)) {
                arecipes.add(new CachedUpgrade(upgrade));
            }
        }
    }

    @Override
    public void drawBackground(int recipe) {
        NeiRender.drawPanel();
        CachedUpgrade cached = (CachedUpgrade) arecipes.get(recipe);
        for (PositionedStack stack : cached.ingredients) {
            NeiRender.drawSlot(stack.relx, stack.rely);
        }
        NeiRender.drawSlot(RESULT_X, RESULT_Y);
        NeiRender.drawArrow(ARROW_X, ARROW_Y);
    }

    private static boolean matches(@Nullable ItemStack expected, ItemStack actual) {
        return expected != null && expected.getItem() == actual.getItem()
            && (expected.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || expected.getItemDamage() == actual.getItemDamage());
    }

    /** 与 {@code ModRecipe#registerCustomRecipes} 里的两条升级配方一一对应。 */
    private static List<Upgrade> getUpgrades() {
        List<Upgrade> upgrades = new ArrayList<>();
        Item iron = findItem("ammo_storage");
        Item gold = findItem("ammo_storage_gold");
        Item diamond = findItem("ammo_storage_diamond");
        if (iron != null && gold != null) {
            upgrades.add(new Upgrade(stack(iron), stack(Items.gold_ingot), stack(gold)));
        }
        if (gold != null && diamond != null) {
            upgrades.add(new Upgrade(stack(gold), stack(Items.diamond), stack(diamond)));
        }
        return upgrades;
    }

    @Nullable
    private static Item findItem(String path) {
        return cpw.mods.fml.common.registry.GameRegistry.findItem("tacz", path);
    }

    @Nullable
    private static ItemStack stack(@Nullable Item item) {
        return item == null ? null : new ItemStack(item);
    }

    private record Upgrade(@Nullable ItemStack from, @Nullable ItemStack material, @Nullable ItemStack result) {}

    /** 一条弹药库升级配方的 NEI 形态。 */
    public class CachedUpgrade extends CachedRecipe {

        private final List<PositionedStack> ingredients = new ArrayList<>();
        @Nullable
        private final PositionedStack result;

        CachedUpgrade(Upgrade upgrade) {
            this.result = upgrade.result() == null ? null : new PositionedStack(upgrade.result(), RESULT_X, RESULT_Y);
            if (upgrade.from() != null) {
                // 中心格（3×3 第 5 格）
                ingredients.add(new PositionedStack(upgrade.from(), GRID_X + 18, GRID_Y + 18));
            }
            if (upgrade.material() != null) {
                for (int slot = 0; slot < 9; slot++) {
                    if (slot == 4) {
                        continue;
                    }
                    ingredients.add(
                        new PositionedStack(
                            upgrade.material()
                                .copy(),
                            GRID_X + 18 * (slot % 3),
                            GRID_Y + 18 * (slot / 3)));
                }
            }
        }

        @Override
        @Nullable
        public PositionedStack getResult() {
            return result;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return ingredients;
        }
    }
}
