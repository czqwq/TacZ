package com.tacz.guns.compat.nei;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI 的「枪械 → 使用弹药」页面（上游 1.20.1 的 {@code compat/jei/category/GunAmmoQueryCategory}）。
 *
 * <p>
 * 语义：产物 = 枪械，材料 = 它使用的弹药。因此：
 * <ul>
 * <li>对枪械按 R → 显示它使用的弹药；</li>
 * <li>对弹药按 U → 显示所有使用该弹药的枪械（每把枪一页，兼容上游「双向共用一份映射」的口径）。</li>
 * </ul>
 */
public class GunAmmoQueryHandler extends TemplateRecipeHandler {

    static final String HANDLER_ID = "tacz:nei/gun_ammo_query";
    private static final int GUN_X = 74;
    private static final int GUN_Y = 0;
    private static final int AMMO_X = 74;
    private static final int AMMO_Y = 39;
    private static final int LABEL_Y = 25;

    @Override
    public String getHandlerId() {
        return HANDLER_ID;
    }

    @Override
    public String getRecipeName() {
        return NeiRender.translate("jei.tacz.gun_ammo_query.title");
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
        for (NeiQueryData.GunAmmo entry : NeiQueryData.get().gunAmmo) {
            if (NeiStacks.sameResult(entry.gun(), result)) {
                arecipes.add(new CachedGunAmmo(entry));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) {
            return;
        }
        for (NeiQueryData.GunAmmo entry : NeiQueryData.get().gunAmmo) {
            if (NeiStacks.sameResult(entry.ammo(), ingredient)) {
                arecipes.add(new CachedGunAmmo(entry));
            }
        }
    }

    @Override
    public void drawBackground(int recipe) {
        NeiRender.drawPanel();
        NeiRender.drawSlot(GUN_X, GUN_Y);
        NeiRender.drawSlot(AMMO_X, AMMO_Y);
    }

    @Override
    public void drawForeground(int recipe) {
        NeiRender.drawTextCentered(NeiRender.translate("jei.tacz.gun_ammo_query.ammo"), LABEL_Y);
    }

    /** 一条「枪械使用某弹药」的 NEI 配方。 */
    public class CachedGunAmmo extends CachedRecipe {

        private final PositionedStack gun;
        private final List<PositionedStack> ingredients = new ArrayList<>();

        CachedGunAmmo(NeiQueryData.GunAmmo entry) {
            this.gun = new PositionedStack(entry.gun(), GUN_X, GUN_Y);
            this.ingredients.add(new PositionedStack(entry.ammo(), AMMO_X, AMMO_Y));
        }

        @Override
        @Nullable
        public PositionedStack getResult() {
            return gun;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return ingredients;
        }
    }
}
