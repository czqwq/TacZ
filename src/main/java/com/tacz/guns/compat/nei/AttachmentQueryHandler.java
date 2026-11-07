package com.tacz.guns.compat.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI 的「配件 → 兼容枪械」页面（上游 1.20.1 的 {@code compat/jei/category/AttachmentQueryCategory}）。
 *
 * <p>
 * 语义：产物 = 配件，材料 = 兼容它的枪械（按枪械分类 {@code GunTabType} 分组，与上游
 * {@code AttachmentQueryEntry} 的分组一致）。
 */
public class AttachmentQueryHandler extends TemplateRecipeHandler {

    static final String HANDLER_ID = "tacz:nei/attachment_query";
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
        return NeiRender.translate("jei.tacz.attachment_query.title");
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
        for (NeiQueryData.AttachmentGuns entry : NeiQueryData.get().attachmentGuns) {
            if (!NeiStacks.sameResult(entry.attachment(), result)) {
                continue;
            }
            List<List<ItemStack>> pages = NeiQueryData.splitPages(entry.guns());
            for (int page = 0; page < pages.size(); page++) {
                arecipes.add(new CachedAttachmentPage(entry, pages.get(page), pages.size()));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) {
            return;
        }
        for (NeiQueryData.AttachmentGuns entry : NeiQueryData.get().attachmentGuns) {
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
                    arecipes.add(new CachedAttachmentPage(entry, pages.get(page), pages.size()));
                }
            }
        }
    }

    @Override
    public void drawBackground(int recipe) {
        NeiRender.drawPanel();
        NeiRender.drawSlot(PRIMARY_X, PRIMARY_Y);
        CachedAttachmentPage cached = (CachedAttachmentPage) arecipes.get(recipe);
        for (PositionedStack stack : cached.ingredients) {
            NeiRender.drawSlot(stack.relx, stack.rely);
        }
    }

    @Override
    public void drawForeground(int recipe) {
        CachedAttachmentPage cached = (CachedAttachmentPage) arecipes.get(recipe);
        NeiRender.drawText(cached.label, LABEL_X, LABEL_Y);
        if (cached.pageCount > 1) {
            // 上游 AttachmentQueryCategory 超出单页上限时在右下角画 "More..."（本移植用分页承载，
            // 因此每页都提示还有更多；不发明 attachment_query.page 这种上游不存在的 lang 键）。
            NeiRender.drawTextRight(
                NeiRender.translate("jei.tacz.attachment_query.more"),
                NeiRender.PANEL_WIDTH - 4,
                NeiRender.FOOTER_Y);
        }
    }

    /** 一页「配件 → 兼容枪械」。 */
    public class CachedAttachmentPage extends CachedRecipe {

        private final PositionedStack attachment;
        private final List<PositionedStack> ingredients = new ArrayList<>();
        private final String label;
        private final int pageCount;

        CachedAttachmentPage(NeiQueryData.AttachmentGuns entry, List<ItemStack> guns, int pageCount) {
            this.attachment = new PositionedStack(entry.attachment(), PRIMARY_X, PRIMARY_Y);
            this.label = NeiRender.translate(
                "tacz.type." + entry.tab()
                    .name()
                    .toLowerCase(Locale.ROOT) + ".name");
            this.pageCount = pageCount;
            for (int i = 0; i < guns.size(); i++) {
                ingredients.add(
                    new PositionedStack(guns.get(i), NeiRender.gridX(i % NeiRender.GRID_COLUMNS), NeiRender.gridY(i)));
            }
        }

        @Override
        @Nullable
        public PositionedStack getResult() {
            return attachment;
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return ingredients;
        }
    }
}
