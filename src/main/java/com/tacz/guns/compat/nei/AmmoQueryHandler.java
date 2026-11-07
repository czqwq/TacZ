package com.tacz.guns.compat.nei;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI 的「弹药 → 适用枪械」页面（上游 1.20.1 的 {@code compat/jei/category/AmmoQueryCategory}）。
 *
 * <p>
 * 语义：产物 = 弹药，材料 = 使用它的枪械列表。与 {@link GunAmmoQueryHandler} 共用
 * {@link NeiQueryData.Snapshot#gunAmmo} 的映射，所以两个方向的查询结果一致（上游注释同样强调这一点）。
 */
public class AmmoQueryHandler extends TemplateRecipeHandler {

    static final String HANDLER_ID = "tacz:nei/ammo_query";
    private static final int PRIMARY_X = 2;
    private static final int PRIMARY_Y = 0;
    private static final int LABEL_X = 22;
    private static final int LABEL_Y = 5;

    @Override
    public String getHandlerId() {
        return HANDLER_ID;
    }

    @Override
    public String getRecipeName() {
        return NeiRender.translate("jei.tacz.ammo_query.title");
    }

    @Override
    public String getGuiTexture() {
        return "nei:textures/gui/recipebg.png";
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        if (result == null) {
            return;
        }
        for (NeiQueryData.AmmoGuns entry : NeiQueryData.get().ammoGuns) {
            if (!NeiStacks.sameResult(entry.ammo(), result)) {
                continue;
            }
            List<List<ItemStack>> pages = NeiQueryData.splitPages(entry.guns());
            for (int page = 0; page < pages.size(); page++) {
                arecipes.add(new CachedAmmoPage(entry, pages.get(page), page + 1, pages.size()));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) {
            return;
        }
        for (NeiQueryData.AmmoGuns entry : NeiQueryData.get().ammoGuns) {
            List<List<ItemStack>> pages = NeiQueryData.splitPages(entry.guns());
            boolean used = false;
            for (List<ItemStack> page : pages) {
                for (ItemStack gun : page) {
                    if (NeiStacks.sameResult(gun, ingredient)) {
                        used = true;
                        break;
                    }
                }
                if (used) {
                    break;
                }
            }
            if (used) {
                for (int page = 0; page < pages.size(); page++) {
                    arecipes.add(new CachedAmmoPage(entry, pages.get(page), page + 1, pages.size()));
                }
            }
        }
    }

    @Override
    public void drawBackground(int recipe) {
        NeiRender.drawPanel();
        NeiRender.drawSlot(PRIMARY_X, PRIMARY_Y);
        CachedAmmoPage cached = (CachedAmmoPage) arecipes.get(recipe);
        for (PositionedStack stack : cached.ingredients) {
            NeiRender.drawSlot(stack.relx, stack.rely);
        }
    }

    @Override
    public void drawForeground(int recipe) {
        CachedAmmoPage cached = (CachedAmmoPage) arecipes.get(recipe);
        NeiRender.drawText(cached.label, LABEL_X, LABEL_Y);
        if (cached.pageCount > 1) {
            NeiRender.drawTextRight(
                NeiRender.translate("jei.tacz.ammo_query.page", cached.pageNumber, cached.pageCount),
                NeiRender.PANEL_WIDTH - 4,
                NeiRender.FOOTER_Y);
        }
    }

    /** 一页「弹药 → 适用枪械」。 */
    public class CachedAmmoPage extends CachedRecipe {

        private final PositionedStack ammo;
        private final List<PositionedStack> ingredients = new ArrayList<>();
        private final String label;
        private final int pageNumber;
        private final int pageCount;

        CachedAmmoPage(NeiQueryData.AmmoGuns entry, List<ItemStack> guns, int pageNumber, int pageCount) {
            this.ammo = new PositionedStack(entry.ammo(), PRIMARY_X, PRIMARY_Y);
            this.label = NeiRender.translate(
                "jei.tacz.ammo_query.guns",
                entry.guns()
                    .size());
            this.pageNumber = pageNumber;
            this.pageCount = pageCount;
            for (int i = 0; i < guns.size(); i++) {
                ingredients.add(
                    new PositionedStack(guns.get(i), NeiRender.gridX(i % NeiRender.GRID_COLUMNS), NeiRender.gridY(i)));
            }
        }

        @Override
        @Nullable
        public PositionedStack getResult() {
            return ammo;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return ingredients;
        }
    }
}
