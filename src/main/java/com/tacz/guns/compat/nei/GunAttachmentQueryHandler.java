package com.tacz.guns.compat.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI 的「枪械 → 兼容配件」页面（上游 1.20.1 的 {@code compat/jei/category/GunAttachmentQueryCategory}）。
 *
 * <p>
 * 语义：产物 = 枪械，材料 = 该枪允许安装的配件（按配件槽位类型 {@code AttachmentType} 分组，与上游
 * {@code GunAttachmentQueryEntry} 一致；每页最多 18 个，超出分页）。
 */
public class GunAttachmentQueryHandler extends TemplateRecipeHandler {

    static final String HANDLER_ID = "tacz:nei/gun_attachment_query";
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
        return NeiRender.translate("jei.tacz.gun_attachment_query.title");
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
        for (NeiQueryData.GunAttachments entry : NeiQueryData.get().gunAttachments) {
            if (!NeiStacks.sameResult(entry.gun(), result)) {
                continue;
            }
            List<List<ItemStack>> pages = NeiQueryData.splitPages(entry.attachments());
            for (int page = 0; page < pages.size(); page++) {
                arecipes.add(new CachedGunAttachmentPage(entry, pages.get(page), page + 1, pages.size()));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) {
            return;
        }
        for (NeiQueryData.GunAttachments entry : NeiQueryData.get().gunAttachments) {
            List<List<ItemStack>> pages = NeiQueryData.splitPages(entry.attachments());
            boolean used = false;
            for (List<ItemStack> page : pages) {
                for (ItemStack attachment : page) {
                    if (NeiStacks.sameResult(attachment, ingredient)) {
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
                    arecipes.add(new CachedGunAttachmentPage(entry, pages.get(page), page + 1, pages.size()));
                }
            }
        }
    }

    @Override
    public void drawBackground(int recipe) {
        NeiRender.drawPanel();
        NeiRender.drawSlot(PRIMARY_X, PRIMARY_Y);
        CachedGunAttachmentPage cached = (CachedGunAttachmentPage) arecipes.get(recipe);
        for (PositionedStack stack : cached.ingredients) {
            NeiRender.drawSlot(stack.relx, stack.rely);
        }
    }

    @Override
    public void drawForeground(int recipe) {
        CachedGunAttachmentPage cached = (CachedGunAttachmentPage) arecipes.get(recipe);
        NeiRender.drawText(cached.label, LABEL_X, LABEL_Y);
        if (cached.pageCount > 1) {
            NeiRender.drawTextRight(
                NeiRender.translate("jei.tacz.gun_attachment_query.page", cached.pageNumber, cached.pageCount),
                NeiRender.PANEL_WIDTH - 4,
                NeiRender.FOOTER_Y);
        }
    }

    /** 一页「枪械 → 某槽位类型的兼容配件」。 */
    public class CachedGunAttachmentPage extends CachedRecipe {

        private final PositionedStack gun;
        private final List<PositionedStack> ingredients = new ArrayList<>();
        private final String label;
        private final int pageNumber;
        private final int pageCount;

        CachedGunAttachmentPage(NeiQueryData.GunAttachments entry, List<ItemStack> attachments, int pageNumber,
            int pageCount) {
            this.gun = new PositionedStack(entry.gun(), PRIMARY_X, PRIMARY_Y);
            String typeName = NeiRender.translate(
                "tacz.type." + entry.type()
                    .name()
                    .toLowerCase(Locale.ROOT) + ".name");
            this.label = NeiRender.translate(
                "jei.tacz.gun_attachment_query.group",
                typeName,
                entry.attachments()
                    .size());
            this.pageNumber = pageNumber;
            this.pageCount = pageCount;
            for (int i = 0; i < attachments.size(); i++) {
                ingredients.add(
                    new PositionedStack(
                        attachments.get(i),
                        NeiRender.gridX(i % NeiRender.GRID_COLUMNS),
                        NeiRender.gridY(i)));
            }
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
