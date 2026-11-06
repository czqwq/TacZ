package com.tacz.guns.client.gui;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.components.FlatColorButton;
import com.tacz.guns.client.gui.components.GunPackList;
import com.tacz.guns.client.gui.components.refit.IComponentTooltip;
import com.tacz.guns.client.gui.components.smith.ResultButton;
import com.tacz.guns.client.gui.components.smith.TypeButton;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.client.tooltip.PackInfoText;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageCraft;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.util.ContentPackOrder;
import com.tacz.guns.util.time.GunTime;

/**
 * 枪械工作台界面。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>{@code AbstractContainerScreen} → {@link GuiContainer}：
 * {@code renderBg}→{@code drawGuiContainerBackgroundLayer}，
 * {@code renderLabels}→{@code drawGuiContainerForegroundLayer}，
 * {@code leftPos/topPos}→{@code guiLeft/guiTop}，
 * {@code imageWidth/imageHeight}→{@code xSize/ySize}。</li>
 * <li><b>配方来源</b>：1.7.10 没有 {@code RecipeManager}，改用
 * {@link GunSmithTableRecipe#getAll()} / {@link GunSmithTableRecipe#getById(ResourceLocation)}
 * （TACZ 自己的配方表，已由 {@code crafting/**} 提供）。</li>
 * <li>{@code Minecraft.getInstance().level.getRecipeManager()} 相关调用全部删除；
 * {@code ItemStack.getHoverName()}→{@code getDisplayName()}、
 * {@code getCount()}→{@code stackSize}、{@code ItemStack.EMPTY}→{@code null}。</li>
 * <li>{@code ImageButton}（1.20 的 {@code net.minecraft.client.gui.components.ImageButton}）
 * 1.7.10 无对应，这里用内部类 {@link SmithImageButton} 复刻它的
 * {@code (x,y,w,h,u,v,hoverVOffset,texture,onPress)} 语义。</li>
 * <li>{@code font.split(Component, int)} → {@code FontRenderer#listFormattedStringToWidth}；
 * {@code FormattedCharSequence} → {@code String}。</li>
 * <li>{@code renderLeftModel}：上游 {@code ItemRenderer#renderStatic} + {@code Lighting} +
 * {@code GlStateManager} 在 1.7.10 无对应，降级为「同一区域内的放大 2D 物品预览」，
 * 不再真正绕 Y 轴旋转（见方法内 TODO）。</li>
 * <li>{@code ConfirmLinkScreen}（打开枪包链接）1.7.10 无对应，降级为直接用
 * {@code java.awt.Desktop#browse} 打开。</li>
 * <li>{@code mouseScrolled(double,double,double)} → {@code handleMouseInput()} +
 * {@code Mouse.getEventDWheel()}。</li>
 * </ul>
 */
public class GunSmithTableScreen extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/gui/gun_smith_table.png");
    private static final ResourceLocation SIDE = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/gui/gun_smith_table_side.png");
    private static final Comparator<RecipeSortKey> RECIPE_ORDER = Comparator
        .comparing(RecipeSortKey::namespace, ContentPackOrder.NAMESPACE_ORDER)
        .thenComparingInt(RecipeSortKey::sort)
        .thenComparing(RecipeSortKey::recipeId);

    private final LinkedHashMap<ResourceLocation, TabConfig> recipeKeys = Maps.newLinkedHashMap();
    private final Map<ResourceLocation, List<ResourceLocation>> recipes = Maps.newLinkedHashMap();

    private int typePage;
    private ResourceLocation selectedType = null;
    private List<ResourceLocation> selectedRecipeList = new ArrayList<>();

    private int indexPage;
    private @Nullable GunSmithTableRecipe selectedRecipe;
    private @Nullable HashMap<Integer, Integer> playerIngredientCount;

    private int scale = 70;
    private boolean filterEnabled = false;
    private GunPackList filterList;
    private boolean autoByHandFilterApplied = false;
    private List<String> emptyByHandFilterMessage = new ArrayList<>();
    private int emptyByHandFilterMessageY;

    public GunSmithTableScreen(GunSmithTableMenu menu, InventoryPlayer inventory, IChatComponent title) {
        super(menu);
        this.xSize = 344;
        this.ySize = 186;
        this.classifyRecipes();
        this.typePage = 0;
        this.indexPage = 0;
        this.selectedRecipe = this.getSelectedRecipe(
            this.selectedRecipeList != null && !this.selectedRecipeList.isEmpty() ? this.selectedRecipeList.get(0)
                : null);
        this.getPlayerIngredientCount(this.selectedRecipe);
    }

    public static void drawModCenteredString(GuiGraphics gui, FontRenderer font, IChatComponent component, int x, int y,
        int color) {
        String text = component == null ? "" : component.getFormattedText();
        gui.drawString(font, text, x - font.getStringWidth(text) / 2, y, color, false);
    }

    /** 1.20 的 {@code this.menu} 在 1.7.10 是 {@code this.inventorySlots}。 */
    private GunSmithTableMenu menu() {
        return (GunSmithTableMenu) this.inventorySlots;
    }

    private void classifyRecipes() {
        this.recipes.clear();
        this.recipeKeys.clear();
        GunSmithTableMenu menu = this.menu();
        ResourceLocation blockId = menu.getBlockId();
        if (blockId == null) {
            return;
        }
        Map<ResourceLocation, List<ResourceLocation>> recipes = Maps.newLinkedHashMap();
        Map<ResourceLocation, TabConfig> recipeKeys = Maps.newLinkedHashMap();

        TimelessAPI.getCommonBlockIndex(blockId)
            .ifPresent(blockIndex -> {
                List<TabConfig> tabs = blockIndex.getData()
                    .getTabs();
                if (DefaultAssets.DEFAULT_BLOCK_ID.equals(blockId) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
                    tabs = TabConfig.DEFAULT_TABS;
                }
                for (TabConfig tab : tabs) {
                    recipes.put(tab.id(), Lists.newArrayList());
                    recipeKeys.put(tab.id(), tab);
                }
            });

        List<Pair<ResourceLocation, ResourceLocation>> recipeIds = Lists.newArrayList();
        Map<ResourceLocation, RecipeSortKey> recipeSortKeys = new HashMap<>();

        // TODO(port): 上游用 Minecraft.getInstance().level.getRecipeManager()
        // .getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())；1.7.10 无 RecipeManager，
        // 改用 TACZ 自己的配方表 GunSmithTableRecipe#getAll()。
        Set<String> namespaces = this.filterList != null ? this.filterList.namespaceList() : null;
        for (GunSmithTableRecipe recipe : GunSmithTableRecipe.getAll()) {
            ResourceLocation id = recipe.getId();
            if (namespaces != null && !namespaces.contains(id.getResourceDomain())) {
                continue;
            }
            if (!isSuitableForMainHand(recipe)) {
                continue;
            }
            if (!isNameMatch(recipe)) {
                continue;
            }

            ResourceLocation groupName = recipe.getResult()
                .getGroup();
            if (recipeKeys.containsKey(groupName)) {
                recipeIds.add(Pair.of(groupName, id));
                recipeSortKeys.put(id, getRecipeSortKey(recipe));
            }
        }

        TimelessAPI.getCommonBlockIndex(blockId)
            .map(blockIndex -> {
                if (blockId.equals(DefaultAssets.DEFAULT_BLOCK_ID) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
                    return null;
                }
                RecipeFilter filter = blockIndex.getFilter();
                if (filter != null) {
                    return filter.filter(recipeIds, Pair::getValue);
                }
                return null;
            })
            .orElse(recipeIds)
            .forEach(entry -> {
                ResourceLocation groupName = entry.getLeft();
                if (recipeKeys.containsKey(groupName)) {
                    recipes.computeIfAbsent(groupName, g -> Lists.newArrayList())
                        .add(entry.getRight());
                }
            });

        Comparator<ResourceLocation> recipeOrder = Comparator
            .comparing(recipeSortKeys::get, Comparator.nullsLast(RECIPE_ORDER));
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : recipes.entrySet()) {
            if (!entry.getValue()
                .isEmpty()) {
                entry.getValue()
                    .sort(recipeOrder);
                this.recipes.put(entry.getKey(), entry.getValue());
                this.recipeKeys.put(entry.getKey(), recipeKeys.get(entry.getKey()));
            }
        }

        if (!this.recipeKeys.containsKey(this.selectedType)) {
            this.selectedType = null;
            this.selectedRecipeList = null;
            this.indexPage = 0;
        }

        if (!this.recipeKeys.keySet()
            .isEmpty()) {
            if (this.selectedType == null) {
                this.selectedType = this.recipeKeys.keySet()
                    .iterator()
                    .next();
            }
        }

        if (this.selectedType != null) {
            this.selectedRecipeList = this.recipes.get(this.selectedType);
        }
    }

    @Nullable
    private static RecipeSortKey getRecipeSortKey(GunSmithTableRecipe recipe) {
        ItemStack output = recipe.getOutput();
        if (output == null || output.getItem() == null) {
            return null;
        }
        if (output.getItem() instanceof IGun gun) {
            ResourceLocation id = gun.getGunId(output);
            return TimelessAPI.getCommonGunIndex(id)
                .map(
                    index -> new RecipeSortKey(
                        id.getResourceDomain(),
                        index.getSort(),
                        recipe.getId()
                            .toString()))
                .orElse(null);
        }
        if (output.getItem() instanceof IAmmo ammo) {
            ResourceLocation id = ammo.getAmmoId(output);
            return TimelessAPI.getCommonAmmoIndex(id)
                .map(
                    index -> new RecipeSortKey(
                        id.getResourceDomain(),
                        index.getSort(),
                        recipe.getId()
                            .toString()))
                .orElse(null);
        }
        if (output.getItem() instanceof IAttachment attachment) {
            ResourceLocation id = attachment.getAttachmentId(output);
            return TimelessAPI.getCommonAttachmentIndex(id)
                .map(
                    index -> new RecipeSortKey(
                        id.getResourceDomain(),
                        index.getSort(),
                        recipe.getId()
                            .toString()))
                .orElse(null);
        }
        if (output.getItem() instanceof IBlock block) {
            ResourceLocation id = block.getBlockId(output);
            // Block indexes have no sort field; use recipe IDs within each namespace.
            return TimelessAPI.getCommonBlockIndex(id)
                .map(
                    index -> new RecipeSortKey(
                        id.getResourceDomain(),
                        0,
                        recipe.getId()
                            .toString()))
                .orElse(null);
        }
        return null;
    }

    private record RecipeSortKey(String namespace, int sort, String recipeId) {}

    private boolean isNameMatch(GunSmithTableRecipe recipe) {
        if (this.filterList != null && StringUtils.isNotBlank(this.filterList.getSearchText())) {
            String searchText = this.filterList.getSearchText()
                .toLowerCase();
            // 1.7.10 防御（P2，打字即崩）：第三方枪包可以写出无法解析的 `result.id`，
            // 此时 GunSmithTableResult#getResult() 返回 null；而本方法在搜索框**每输入一个字符**
            // 都会跑到（GunPackList#keyTyped → parent.initGui()），原先直接调
            // `recipe.getResult().getResult().getDisplayName()` → NPE。
            // 解析不了就退回用配方 id 参与匹配（信息不丢，且不会崩）。
            GunSmithTableResult tableResult = recipe.getResult();
            ItemStack result = tableResult == null ? null : tableResult.getResult();
            if (result == null || result.getItem() == null) {
                ResourceLocation recipeId = recipe.getId();
                return recipeId != null && recipeId.toString()
                    .toLowerCase()
                    .contains(searchText);
            }
            IChatComponent name = new ChatComponentText(result.getDisplayName());
            return name.getUnformattedText()
                .toLowerCase()
                .contains(searchText);
        }
        return true;
    }

    private boolean isSuitableForMainHand(GunSmithTableRecipe recipe) {
        if (this.filterList != null && this.filterList.isByHandSelected()) {
            ItemStack result = recipe.getResult()
                .getResult();

            Minecraft minecraft = Minecraft.getMinecraft();
            ItemStack stack = minecraft.thePlayer != null ? minecraft.thePlayer.getHeldItem() : null;
            if (stack != null && stack.getItem() instanceof IGun igun) {
                if (result != null && result.getItem() instanceof IAmmo iAmmo) {
                    return iAmmo.isAmmoOfGun(stack, result);
                }
                if (result != null && result.getItem() instanceof IAttachment) {
                    return igun.allowAttachment(stack, result);
                }
                return false;
            }
            if (stack != null && stack.getItem() instanceof IAttachment) {
                if (result != null && result.getItem() instanceof IGun iGun) {
                    return iGun.allowAttachment(result, stack);
                }
                return false;
            }
            if (stack != null && stack.getItem() instanceof IAmmo iAmmo) {
                if (result != null && result.getItem() instanceof IGun) {
                    return iAmmo.isAmmoOfGun(result, stack);
                }
                return false;
            }
        }
        return true;
    }

    private boolean shouldFilterByMainHand() {
        Minecraft minecraft = Minecraft.getMinecraft();
        ItemStack stack = minecraft.thePlayer != null ? minecraft.thePlayer.getHeldItem() : null;
        Item item = stack == null ? null : stack.getItem();
        return item instanceof IGun || item instanceof IAttachment || item instanceof IAmmo;
    }

    private void updateSelectedRecipeAfterFiltering() {
        if (this.selectedRecipeList == null || this.selectedRecipeList.isEmpty()) {
            this.selectedRecipe = null;
            this.playerIngredientCount = null;
            return;
        }
        boolean selectedRecipeExists = this.selectedRecipe != null
            && this.selectedRecipeList.contains(this.selectedRecipe.getId());
        if (!selectedRecipeExists) {
            this.selectedRecipe = this.getSelectedRecipe(this.selectedRecipeList.get(0));
        }
        this.getPlayerIngredientCount(this.selectedRecipe);
    }

    private void applyRequestedRecipeSelection() {
        GunSmithTableMenu menu = this.menu();
        ResourceLocation recipeId = menu.consumeRecipeSelection();
        // TODO(port): 上游 menu.canSelectRecipe(recipeId, level.getRecipeManager())；
        // 1.7.10 无 RecipeManager，改为单参（需要 inventory 包同步改造）。
        if (recipeId == null || !menu.canSelectRecipe(recipeId)) {
            return;
        }
        GunSmithTableRecipe recipe = this.getSelectedRecipe(recipeId);
        if (recipe == null) {
            return;
        }
        ResourceLocation type = recipe.getTab();
        List<ResourceLocation> recipeList = this.recipes.get(type);
        if (recipeList == null || !recipeList.contains(recipeId)) {
            // User filters can hide a valid recipe; table restrictions still apply in classifyRecipes.
            this.filterList.clearFilters();
            this.classifyRecipes();
            recipeList = this.recipes.get(type);
        }
        if (recipeList == null || !recipeList.contains(recipeId)) {
            return;
        }
        this.selectedType = type;
        this.selectedRecipeList = recipeList;
        this.selectedRecipe = recipe;
        this.typePage = new ArrayList<>(this.recipeKeys.keySet()).indexOf(type) / 7;
        this.indexPage = recipeList.indexOf(recipeId) / 6;
        this.filterEnabled = false;
    }

    public void setIndexPage(int indexPage) {
        this.indexPage = indexPage;
    }

    @Nullable
    private GunSmithTableRecipe getSelectedRecipe(ResourceLocation recipeId) {
        if (recipeId == null) {
            return null;
        }
        // TODO(port): 上游 RecipeManager#byKey(recipeId)；1.7.10 用 TACZ 自己的配方表
        return GunSmithTableRecipe.getById(recipeId);
    }

    private void getPlayerIngredientCount(GunSmithTableRecipe recipe) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null || recipe == null) {
            return;
        }
        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        int size = ingredients.size();
        this.playerIngredientCount = new HashMap<>(size);
        InventoryPlayer inventory = player.inventory;
        for (int i = 0; i < size; i++) {
            GunSmithTableIngredient ingredient = ingredients.get(i);
            int count = 0;
            for (ItemStack stack : inventory.mainInventory) {
                // 1.7.10 的 GunSmithTableIngredient 自带 test(ItemStack)（含 ore dict 匹配）
                if (ingredient.test(stack)) {
                    count = count + stack.stackSize;
                }
            }
            this.playerIngredientCount.put(i, count);
        }
    }

    public void updateIngredientCount() {
        if (this.selectedRecipe != null) {
            this.getPlayerIngredientCount(this.selectedRecipe);
        }
        this.initGui();
    }

    @Override
    public void initGui() {
        super.initGui();
        if (this.filterList == null) {
            this.filterList = new GunPackList(
                this.mc,
                134,
                // 1.7.10 GuiSlot 的 width/height 是**屏幕**逻辑尺寸（原版所有子类都传 Screen#width/height）；
                // 上游 1.20 传 imageHeight 是因为 AbstractSelectionList 的 height 只参与已禁用的背景绘制，
                // 而 1.7.10 的 GuiSlot#drawScreen 会用 height 画底部 overlayBackground → 传 ySize 会在
                // 面板底部/外部画出一条 dirt 纹理带（见 GunPackList#drawScreen 的 scissor）。
                this.height,
                this.guiTop,
                this.guiTop + this.ySize + 1,
                15,
                this.recipes,
                this);
        }
        if (!this.autoByHandFilterApplied && RenderConfig.AUTO_SELECT_GUN_SMITH_TABLE_FILTER.get()) {
            this.filterList.setByHandSelected(this.shouldFilterByMainHand());
            this.autoByHandFilterApplied = true;
        }
        this.filterList.updateSize(134, this.height, this.guiTop, this.guiTop + this.ySize + 1);
        this.filterList.setLeftPos(this.guiLeft);

        this.classifyRecipes();
        this.applyRequestedRecipeSelection();
        this.updateSelectedRecipeAfterFiltering();
        this.buttonList.clear();

        this.addTypePageButtons();
        this.addTypeButtons();
        this.addIndexPageButtons();
        this.addIndexButtons();
        this.add(new FlatColorButton(this.guiLeft - 10, this.guiTop, 9, 9, new ChatComponentText("F"), b -> {
            this.filterEnabled = !this.filterEnabled;
            this.initGui();
        }).setTooltips("gui.tacz.gun_smith_table.filter"));
        if (!this.filterEnabled) {
            // 过滤面板开启时上游也不加缩放/URL 按钮（1.20 GunSmithTableScreen#init 的 if/else）
            this.addScaleButtons();
            this.addUrlButton();
        }
        // 1.7.10 的 GuiSlot 不在 buttonList 里，由 drawScreen 手动绘制（见 drawScreen）
        this.addEmptyByHandFilterHint();
        if (this.emptyByHandFilterMessage.isEmpty()) {
            this.addCraftButton();
        }
    }

    /** 对应 1.20 {@code addRenderableWidget}。 */
    private <T extends GuiButton> T add(T button) {
        this.buttonList.add(button);
        return button;
    }

    private void addEmptyByHandFilterHint() {
        this.emptyByHandFilterMessage = new ArrayList<>();
        if (!this.recipes.isEmpty() || !this.filterList.isByHandSelected() || !this.shouldFilterByMainHand()) {
            return;
        }
        // **1.7.10 修正（显示错误，P1）**：`gui.tacz.gun_smith_table.filter.hand.empty` 的值里带
        // 一个字面 `\n`（assets/tacz/lang/en_US.lang:123 =
        // "No matching recipes\nHeld item filter is on"）。1.7.10 的 Locale#loadLocaleData
        // （Locale.java:106-126）只做 `IOUtils.readLines` + `Splitter.on('=')`，**不处理任何转义**，
        // 所以它是「反斜杠 + n」两个可打印字符；而 FontRenderer#listFormattedStringToWidth 只按宽度折行，
        // 不识别字面 `\n` → 原样把 `\n` 画出来。
        // 上游 1.20 的 font.split(Component, 184) 里 JSON 的 `\n` 是真换行，所以能正确断成两行。
        // 裁决见 docs/PORT_STATUS.md:39/190（保留字面量，client 显示前 split("\\n")）。
        List<String> lines = new ArrayList<>();
        for (String segment : new ChatComponentTranslation("gui.tacz.gun_smith_table.filter.hand.empty")
            .getFormattedText()
            .split("\\\\n", -1)) {
            lines.addAll(this.fontRendererObj.listFormattedStringToWidth(segment, 184));
        }
        this.emptyByHandFilterMessage = lines;
        int messageHeight = this.emptyByHandFilterMessage.size() * (this.fontRendererObj.FONT_HEIGHT + 2);
        this.emptyByHandFilterMessageY = this.guiTop + (this.ySize + 1 - messageHeight - 10 - 18) / 2;
        this.add(
            new FlatColorButton(
                this.guiLeft + 190,
                this.emptyByHandFilterMessageY + messageHeight + 10,
                100,
                18,
                new ChatComponentTranslation("gui.tacz.gun_smith_table.filter.hand.clear"),
                b -> {
                    this.filterList.setByHandSelected(false);
                    this.typePage = 0;
                    this.indexPage = 0;
                    this.initGui();
                }));
    }

    private void addCraftButton() {
        final boolean hasRecipe = this.selectedRecipe != null;
        SmithImageButton craftButton = this.add(
            new SmithImageButton(
                this.guiLeft + 289,
                this.guiTop + 162,
                48,
                18,
                138,
                164,
                hasRecipe ? 18 : 0,
                TEXTURE,
                b -> {
                    if (this.selectedRecipe != null && this.playerIngredientCount != null) {
                        // 检查是否能合成，不能就不发包
                        List<GunSmithTableIngredient> inputs = this.selectedRecipe.getInputs();
                        int size = inputs.size();
                        for (int i = 0; i < size; i++) {
                            if (i >= this.playerIngredientCount.size()) {
                                return;
                            }
                            Integer hasCount = this.playerIngredientCount.get(i);
                            int needCount = inputs.get(i)
                                .getCount();
                            boolean isCreative = Minecraft.getMinecraft().thePlayer != null
                                && Minecraft.getMinecraft().thePlayer.capabilities.isCreativeMode;
                            // 拥有数量小于需求数量，不发包
                            if ((hasCount == null ? 0 : hasCount) < needCount && !isCreative) {
                                return;
                            }
                        }
                        NetworkHandler.CHANNEL
                            .sendToServer(new ClientMessageCraft(this.selectedRecipe.getId(), this.menu().windowId));
                    }
                }));
        craftButton.enabled = hasRecipe;
    }

    private void addUrlButton() {
        this.add(new SmithImageButton(this.guiLeft + 112, this.guiTop + 164, 18, 18, 149, 211, 18, TEXTURE, b -> {
            if (this.selectedRecipe == null) {
                return;
            }
            ItemStack output = this.selectedRecipe.getOutput();
            Item item = output == null ? null : output.getItem();
            ResourceLocation id;
            if (item instanceof IGun iGun) {
                id = iGun.getGunId(output);
            } else if (item instanceof IAttachment iAttachment) {
                id = iAttachment.getAttachmentId(output);
            } else if (item instanceof IAmmo iAmmo) {
                id = iAmmo.getAmmoId(output);
            } else {
                return;
            }

            PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
            if (packInfo == null) {
                return;
            }
            openUrl(packInfo.getUrl());
        }));
    }

    /**
     * 打开外部链接。
     *
     * <p>
     * TODO(port): 上游用 1.20 的 {@code ConfirmLinkScreen} 做二次确认，1.7.10 没有该界面；
     * 这里直接调用 {@code java.awt.Desktop#browse}（1.7.10 模组打开链接的常见做法）。
     */
    private static void openUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return;
        }
        try {
            Desktop.getDesktop()
                .browse(URI.create(url));
        } catch (Exception ignored) {
            // 无桌面环境 / 非法 URI 时静默跳过
        }
    }

    private void addIndexButtons() {
        if (this.selectedRecipeList == null || this.selectedRecipeList.isEmpty()) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            int finalIndex = i + this.indexPage * 6;
            if (finalIndex >= this.selectedRecipeList.size()) {
                break;
            }
            int yOffset = this.guiTop + 66 + 17 * i;
            ResourceLocation recipeId = this.selectedRecipeList.get(finalIndex);
            GunSmithTableRecipe recipe = getSelectedRecipe(recipeId);
            if (recipe == null) {
                continue;
            }
            ResultButton button = add(new ResultButton(this.guiLeft + 144, yOffset, recipe.getOutput(), b -> {
                this.selectedRecipe = recipe;
                this.getPlayerIngredientCount(this.selectedRecipe);
                this.initGui();
            }));
            if (this.selectedRecipe != null && recipe.getId()
                .equals(this.selectedRecipe.getId())) {
                button.setSelected(true);
            }
        }
    }

    private void addTypeButtons() {
        List<TabConfig> list = Arrays.asList(
            this.recipeKeys.values()
                .toArray(new TabConfig[0]));
        for (int i = 0; i < 7; i++) {
            int typeIndex = this.typePage * 7 + i;
            if (typeIndex >= this.recipes.size()) {
                return;
            }
            TabConfig tabConfig = list.get(typeIndex);
            ResourceLocation type = tabConfig.id();
            int xOffset = this.guiLeft + 157 + 24 * i;

            ItemStack icon = tabConfig.icon();

            TypeButton typeButton = new TypeButton(xOffset, this.guiTop + 2, icon, tabConfig.getName(), b -> {
                this.selectedType = type;
                this.selectedRecipeList = this.recipes.get(type);
                this.indexPage = 0;
                this.selectedRecipe = getSelectedRecipe(
                    this.selectedRecipeList.isEmpty() ? null : this.selectedRecipeList.get(0));
                this.getPlayerIngredientCount(this.selectedRecipe);
                this.initGui();
            });
            // 上游 GunSmithTableScreen.java:523 typeButton.setTooltip(Tooltip.create(tabConfig.getName()));
            // 1.7.10 用 TypeButton#renderTooltip（IComponentTooltip），由本类 drawScreen 末尾统一绘制。
            if (this.selectedType.equals(type)) {
                typeButton.setSelected(true);
            }
            this.add(typeButton);
        }
    }

    private void addIndexPageButtons() {
        if (this.selectedRecipeList == null || this.selectedRecipeList.isEmpty()) {
            return;
        }
        this.add(new SmithImageButton(this.guiLeft + 143, this.guiTop + 56, 96, 6, 40, 166, 6, TEXTURE, b -> {
            if (this.indexPage > 0) {
                this.indexPage--;
                this.initGui();
            }
        }));
        this.add(new SmithImageButton(this.guiLeft + 143, this.guiTop + 171, 96, 6, 40, 186, 6, TEXTURE, b -> {
            if (this.selectedRecipeList != null && !this.selectedRecipeList.isEmpty()) {
                int maxIndexPage = (this.selectedRecipeList.size() - 1) / 6;
                if (this.indexPage < maxIndexPage) {
                    this.indexPage++;
                    this.initGui();
                }
            }
        }));
    }

    private void addTypePageButtons() {
        if (this.recipes.isEmpty()) {
            return;
        }
        this.add(new SmithImageButton(this.guiLeft + 136, this.guiTop + 4, 18, 20, 0, 162, 20, TEXTURE, b -> {
            if (this.typePage > 0) {
                this.typePage--;
                this.initGui();
            }
        }));
        this.add(new SmithImageButton(this.guiLeft + 327, this.guiTop + 4, 18, 20, 20, 162, 20, TEXTURE, b -> {
            int maxIndexPage = (this.recipes.size() - 1) / 7;
            if (this.typePage < maxIndexPage) {
                this.typePage++;
                this.initGui();
            }
        }));
    }

    private void addScaleButtons() {
        this.add(
            new SmithImageButton(
                this.guiLeft + 5,
                this.guiTop + 5,
                10,
                10,
                188,
                173,
                10,
                TEXTURE,
                b -> this.scale = Math.min(this.scale + 20, 200)));
        this.add(
            new SmithImageButton(
                this.guiLeft + 17,
                this.guiTop + 5,
                10,
                10,
                200,
                173,
                10,
                TEXTURE,
                b -> this.scale = Math.max(this.scale - 20, 10)));
        this.add(
            new SmithImageButton(
                this.guiLeft + 29,
                this.guiTop + 5,
                10,
                10,
                212,
                173,
                10,
                TEXTURE,
                b -> this.scale = 70));
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        this.mouseScrolled(mouseX, mouseY, wheel);
    }

    /**
     * 对应 1.20 {@code mouseScrolled(double, double, double)}；1.7.10 由 {@link #handleMouseInput()} 调用。
     */
    public boolean mouseScrolled(int mouseX, int mouseY, int delta) {
        if (this.selectedRecipeList != null && !this.selectedRecipeList.isEmpty()
            && mouseX > this.guiLeft + 143
            && mouseX < this.guiLeft + 143 + 94
            && mouseY > this.guiTop + 66
            && mouseY < this.guiTop + 66 + 85) {
            if (delta > 0) {
                this.indexPage = Math.max(0, this.indexPage - 1);
            } else {
                int maxIndexPage = (this.selectedRecipeList.size() - 1) / 6;
                this.indexPage = Math.min(maxIndexPage, this.indexPage + 1);
            }
            this.initGui();
            return true;
        }
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.filterList != null) {
            this.filterList.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.filterEnabled && this.filterList != null && this.filterList.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /**
     * 上游 1.20 {@code AbstractButton#isValidClickButton(int)} 只接受左键（{@code button == 0}）；
     * 1.7.10 {@code GuiScreen#handleMouseInput} 对任意鼠标键都调 {@code mouseClicked}，
     * {@code GuiButton#mousePressed} 也不判键号 → 右键会误触 F/缩放/翻页/合成按钮。
     * {@link GunSmithTableMenu} 没有容器槽位（无原版右键分堆语义），因此按上游只把左键继续往下传。
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return;
        }
        // 过滤列表（GuiSlot）不在 buttonList 里：上游是列表子控件，这里手动派发（见 GunPackList#mouseClicked）。
        if (this.filterEnabled && this.filterList != null) {
            if (this.filterList.mouseClicked(mouseX, mouseY)) {
                return;
            }
            // 点列表以外区域 → 搜索框失焦（对应 1.20 Screen#mouseClicked 的焦点切换）
            this.filterList.clearSearchFocus();
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        super.drawScreen(mouseX, mouseY, partialTick);
        GuiGraphics graphics = GuiGraphics.create();
        if (this.filterEnabled && this.filterList != null) {
            this.filterList.drawScreen(mouseX, mouseY, partialTick);
        }
        FontRenderer font = this.fontRendererObj;
        drawModCenteredString(
            graphics,
            font,
            new ChatComponentTranslation("gui.tacz.gun_smith_table.preview"),
            this.guiLeft + 108,
            this.guiTop + 5,
            0x555555);
        if (!this.emptyByHandFilterMessage.isEmpty()) {
            int hintY = this.emptyByHandFilterMessageY;
            for (String line : this.emptyByHandFilterMessage) {
                graphics
                    .drawString(font, line, this.guiLeft + 240 - font.getStringWidth(line) / 2, hintY, 0x444444, false);
                hintY += font.FONT_HEIGHT + 2;
            }
            return;
        }
        if (this.selectedType != null) {
            TabConfig config = this.recipeKeys.get(this.selectedType);
            if (config != null) {
                graphics.drawString(font, config.getName(), this.guiLeft + 150, this.guiTop + 32, 0x555555, false);
            }
        }
        graphics.drawString(
            font,
            new ChatComponentTranslation("gui.tacz.gun_smith_table.ingredient"),
            this.guiLeft + 254,
            this.guiTop + 50,
            0x555555,
            false);
        drawModCenteredString(
            graphics,
            font,
            new ChatComponentTranslation("gui.tacz.gun_smith_table.craft"),
            this.guiLeft + 312,
            this.guiTop + 167,
            this.selectedRecipe != null ? 0xFFFFFF : 0x888888);
        if (!this.filterEnabled && this.selectedRecipe != null) {
            this.renderLeftModel(this.selectedRecipe);
            this.renderPackInfo(graphics, this.selectedRecipe);
            ItemStack resultStack = this.selectedRecipe.getResult()
                .getResult();
            int resultCount = resultStack == null ? 0 : resultStack.stackSize;
            graphics.drawString(
                font,
                new ChatComponentTranslation("gui.tacz.gun_smith_table.count", resultCount),
                this.guiLeft + 254,
                this.guiTop + 140,
                0x555555,
                false);
        }
        if (this.selectedRecipeList != null && !this.selectedRecipeList.isEmpty()) {
            renderIngredient(graphics);
        }
        for (GuiButton widget : new ArrayList<>(this.buttonList)) {
            if (widget instanceof ResultButton resultButton) {
                resultButton.renderTooltips(stack -> graphics.renderTooltip(font, stack, mouseX, mouseY));
            } else if (widget instanceof FlatColorButton flatColorButton) {
                flatColorButton.renderToolTip(graphics, this, mouseX, mouseY);
            } else if (widget instanceof IComponentTooltip componentTooltip) {
                // 分类页签（TypeButton）的名称提示：等价上游 setTooltip(Tooltip.create(tabConfig.getName()))。
                componentTooltip
                    .renderTooltip(components -> graphics.renderComponentTooltip(font, components, mouseX, mouseY));
            }
        }
        this.renderIngredientTooltip(graphics, mouseX, mouseY);
    }

    private void renderPackInfo(GuiGraphics gui, GunSmithTableRecipe recipe) {
        ItemStack output = recipe.getOutput();
        Item item = output == null ? null : output.getItem();
        ResourceLocation id;
        if (item instanceof IGun iGun) {
            id = iGun.getGunId(output);
        } else if (item instanceof IAttachment iAttachment) {
            id = iAttachment.getAttachmentId(output);
        } else if (item instanceof IAmmo iAmmo) {
            id = iAmmo.getAmmoId(output);
        } else {
            return;
        }

        PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
        FontRenderer font = this.fontRendererObj;
        if (packInfo != null) {
            gui.pose()
                .pushPose();
            gui.pose()
                .scale(0.75F, 0.75F, 1.0F);
            // 1.7.10 防御：packInfo.getName() 可能为 null（第三方包显式写 `"name": null`），
            // 而本方法选中配方后每帧都跑，`new ChatComponentTranslation(null).getFormattedText()` 会 NPE
            // （ChatComponentTranslation#initializeFromFormat → Pattern#matcher(null)）。
            // `id` 也一并判空：三个 `getXxxId` 虽标注 @Nonnull，但这里宁可退化成「无 namespace」。
            IChatComponent nameText = PackInfoText.name(packInfo, id == null ? null : id.getResourceDomain());
            gui.drawString(
                font,
                nameText,
                (int) ((this.guiLeft + 6) / 0.75F),
                (int) ((this.guiTop + 122) / 0.75F),
                0x555555,
                false);
            gui.pose()
                .popPose();

            gui.pose()
                .pushPose();
            gui.pose()
                .scale(0.5F, 0.5F, 1.0F);

            int offsetX = (this.guiLeft + 6) * 2;
            int offsetY = (this.guiTop + 123) * 2;
            int nameWidth = font.getStringWidth(nameText.getFormattedText());
            // version / license / date 只参与字符串拼接：null 会印出字面 "null"（vnull / License: null），
            // 用 PackInfoText.safe(...) 归一成空串（gson 对显式 `"xxx": null` 会覆盖非 null 默认值）。
            IChatComponent ver = new ChatComponentText(
                EnumChatFormatting.UNDERLINE + "v" + PackInfoText.safe(packInfo.getVersion()));
            gui.drawString(font, ver, (int) (offsetX + nameWidth * 0.75F / 0.5F + 5), offsetY, 0x555555, false);
            offsetY += 14;

            String descKey = packInfo.getDescription();
            if (StringUtils.isNoneBlank(descKey)) {
                IChatComponent desc = new ChatComponentTranslation(descKey);
                List<String> split = font.listFormattedStringToWidth(desc.getFormattedText(), 245);
                for (String charSequence : split) {
                    gui.drawString(font, charSequence, offsetX, offsetY, 0x555555, false);
                    offsetY += font.FONT_HEIGHT;
                }
                offsetY += 3;
            }

            gui.drawString(
                font,
                new ChatComponentText(
                    new ChatComponentTranslation("gui.tacz.gun_smith_table.license").getUnformattedText()
                        + EnumChatFormatting.DARK_GRAY
                        + PackInfoText.safe(packInfo.getLicense())),
                offsetX,
                offsetY,
                0x555555,
                false);
            offsetY += 12;

            // 1.7.10 防御：PackInfo 的字段默认值非 null，但枪包 gunpack_info.json 显式写 `"authors": null`
            // 会被 gson 覆盖成 null（与本工程「空值=null」的既有约定同类）；本方法在选中任意配方后**每帧**
            // 都会跑，一旦 NPE 就是「工作台一打开就崩」，因此这里补一次判空（其余字段只参与字符串拼接，天然安全）。
            List<String> authors = packInfo.getAuthors();
            if (authors != null && !authors.isEmpty()) {
                gui.drawString(
                    font,
                    new ChatComponentText(
                        new ChatComponentTranslation("gui.tacz.gun_smith_table.authors").getUnformattedText()
                            + EnumChatFormatting.DARK_GRAY
                            + StringUtils.join(authors, ", ")),
                    offsetX,
                    offsetY,
                    0x555555,
                    false);
                offsetY += 12;
            }

            gui.drawString(
                font,
                new ChatComponentText(
                    new ChatComponentTranslation("gui.tacz.gun_smith_table.date").getUnformattedText()
                        + EnumChatFormatting.DARK_GRAY
                        + PackInfoText.safe(packInfo.getDate())),
                offsetX,
                offsetY,
                0x555555,
                false);

            gui.pose()
                .popPose();
        } else {
            ResourceLocation recipeId = recipe.getId();
            gui.drawString(
                font,
                new ChatComponentText(
                    EnumChatFormatting.DARK_RED
                        + new ChatComponentTranslation("gui.tacz.gun_smith_table.error").getUnformattedText()),
                this.guiLeft + 6,
                this.guiTop + 122,
                0xAF0000,
                false);
            gui.drawString(
                font,
                new ChatComponentText(
                    EnumChatFormatting.DARK_RED
                        + new ChatComponentTranslation("gui.tacz.gun_smith_table.error.id", recipeId.toString())
                            .getUnformattedText()),
                this.guiLeft + 6,
                this.guiTop + 134,
                0xFFFFFF,
                false);
            PackInfo errorPackInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
            if (errorPackInfo != null) {
                gui.drawString(
                    font,
                    new ChatComponentText(
                        EnumChatFormatting.DARK_RED
                            + new ChatComponentTranslation(errorPackInfo.getName()).getUnformattedText()),
                    this.guiLeft + 6,
                    this.guiTop + 146,
                    0xAF0000,
                    false);
            }
        }
    }

    private void renderIngredient(GuiGraphics gui) {
        if (this.selectedRecipe == null) {
            return;
        }
        List<GunSmithTableIngredient> inputs = this.selectedRecipe.getInputs();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 2; j++) {
                int index = i * 2 + j;
                if (index >= inputs.size()) {
                    return;
                }
                int offsetX = this.guiLeft + 254 + 45 * j;
                int offsetY = this.guiTop + 62 + 17 * i;

                GunSmithTableIngredient smithTableIngredient = inputs.get(index);
                // 1.20 的 Ingredient#getItems() → 1.7.10 GunSmithTableIngredient#getMatchingStacks()
                List<ItemStack> items = smithTableIngredient.getMatchingStacks();
                if (items.isEmpty()) {
                    continue;
                }
                int itemIndex = ((int) (GunTime.nowMillis() / 1_000)) % items.size();
                ItemStack item = items.get(itemIndex);

                gui.renderFakeItem(item, offsetX, offsetY);

                gui.pose()
                    .pushPose();
                gui.pose()
                    .translate(0.0F, 0.0F, 200.0F);
                gui.pose()
                    .scale(0.5F, 0.5F, 1.0F);
                int count = smithTableIngredient.getCount();
                if (Minecraft.getMinecraft().thePlayer != null
                    && Minecraft.getMinecraft().thePlayer.capabilities.isCreativeMode) {
                    gui.drawString(
                        this.fontRendererObj,
                        String.format("%d/∞", count),
                        (offsetX + 17) * 2,
                        (offsetY + 10) * 2,
                        0xFFFFFF,
                        false);
                } else {
                    Integer hasCount = 0;
                    if (this.playerIngredientCount != null && index < this.playerIngredientCount.size()) {
                        hasCount = this.playerIngredientCount.get(index);
                    }
                    int color = count <= (hasCount == null ? 0 : hasCount) ? 0xFFFFFF : 0xFF0000;
                    gui.drawString(
                        this.fontRendererObj,
                        String.format("%d/%d", count, hasCount == null ? 0 : hasCount),
                        (offsetX + 17) * 2,
                        (offsetY + 10) * 2,
                        color,
                        false);
                }
                gui.pose()
                    .popPose();
            }
        }
    }

    private void renderIngredientTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        if (this.selectedRecipe == null) {
            return;
        }
        List<GunSmithTableIngredient> inputs = this.selectedRecipe.getInputs();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 2; j++) {
                int index = i * 2 + j;
                if (index >= inputs.size()) {
                    return;
                }
                int offsetX = this.guiLeft + 254 + 45 * j;
                int offsetY = this.guiTop + 62 + 17 * i;
                if (mouseX < offsetX || mouseX >= offsetX + 16 || mouseY < offsetY || mouseY >= offsetY + 16) {
                    continue;
                }

                List<ItemStack> items = inputs.get(index)
                    .getMatchingStacks();
                if (items.isEmpty()) {
                    return;
                }
                int itemIndex = ((int) (GunTime.nowMillis() / 1_000)) % items.size();
                ItemStack item = items.get(itemIndex);
                if (item != null && item.getItem() != null) {
                    gui.renderComponentTooltip(this.fontRendererObj, getIngredientTooltip(item), mouseX, mouseY, item);
                }
                return;
            }
        }
    }

    private List<IChatComponent> getIngredientTooltip(ItemStack item) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        boolean advanced = Minecraft.getMinecraft().gameSettings != null
            && Minecraft.getMinecraft().gameSettings.advancedItemTooltips;
        List<IChatComponent> tooltip = new ArrayList<>();
        for (Object line : item.getTooltip(player, advanced)) {
            tooltip.add(new ChatComponentText(String.valueOf(line)));
        }
        // TODO(port): 上游遍历 CreativeModeTabRegistry.getSortedCreativeModeTabs() 找「包含该物品的 tab」
        // 并把 tab 名插入第 1 行；1.7.10 CreativeTabs 没有 contains(ItemStack)，
        // 这里退化为只看物品自己声明的创造模式页签。
        CreativeTabs ownTab = item.getItem() == null ? null
            : item.getItem()
                .getCreativeTab();
        if (ownTab != null && !ownTab.hasSearchBar()) {
            tooltip.add(
                Math.min(1, tooltip.size()),
                new ChatComponentText(
                    EnumChatFormatting.BLUE
                        + new ChatComponentTranslation(ownTab.getTranslatedTabLabel()).getUnformattedText()));
        }
        return tooltip;
    }

    /**
     * 左侧 3D 预览。
     *
     * <p>
     * TODO(port): 上游 {@code GunSmithTableScreen#renderLeftModel} 用 1.20 的
     * {@code Minecraft#getItemRenderer().renderStatic(..., ItemDisplayContext.FIXED, ...)} +
     * {@code com.mojang.blaze3d.platform.Lighting.setupForFlatItems()} +
     * {@code RenderSystem.getModelViewStack()/applyModelViewMatrix()} 做<b>真正绕 Y 轴旋转的 3D 预览</b>。
     * 1.7.10 没有 {@code ItemRenderer#renderStatic}/{@code Lighting}/{@code PoseStack}，
     * 这里降级为「在同一裁剪区域内、按同样缩放渲染放大的 2D 物品预览」，
     * 旋转与光照被丢弃（物品仍然可见、缩放按钮仍然生效）。
     */
    @SuppressWarnings("deprecation")
    private void renderLeftModel(GunSmithTableRecipe recipe) {
        ItemStack output = recipe.getOutput();
        if (output == null || output.getItem() == null) {
            return;
        }
        int xPos = this.guiLeft + 60;
        int yPos = this.guiTop + 50;
        int startX = this.guiLeft + 3;
        int startY = this.guiTop + 16;
        int width = 128;
        int height = 99;

        GuiGraphics graphics = GuiGraphics.create();
        graphics.enableScissor(startX, startY, startX + width, startY + height);
        float modelScale = this.scale / 70.0F;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(xPos, yPos, 200.0F);
            GL11.glScalef(modelScale, modelScale, modelScale);
            RenderHelper.enableGUIStandardItemLighting();
            RenderItem.getInstance()
                .renderItemAndEffectIntoGUI(this.fontRendererObj, this.mc.getTextureManager(), output, -8, -8);
            RenderHelper.disableStandardItemLighting();
        } finally {
            GL11.glPopMatrix();
            graphics.disableScissor();
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {}

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        GuiGraphics gui = GuiGraphics.create();
        gui.blit(SIDE, this.guiLeft, this.guiTop, 0, 0, 134, 187);
        if (!this.emptyByHandFilterMessage.isEmpty()) {
            // Reuse the ingredient panel's frame as one panel without interior dividers.
            gui.blitNineSliced(TEXTURE, this.guiLeft + 136, this.guiTop, 208, this.ySize + 1, 4, 96, 107, 112, 19);
        } else {
            gui.blit(TEXTURE, this.guiLeft + 136, this.guiTop + 27, 0, 0, 208, 160);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * 1.7.10 版 {@code net.minecraft.client.gui.components.ImageButton}。
     *
     * <p>
     * 复刻上游用到的 {@code (x, y, width, height, u, v, hoverVOffset, texture, onPress)} 语义：
     * 悬停时 {@code v} 增加 {@code hoverVOffset}。
     */
    private static class SmithImageButton extends GuiButton {

        private final int u;
        private final int v;
        private final int hoverVOffset;
        private final ResourceLocation texture;
        private final Consumer<GuiButton> onPress;
        private boolean hovered;

        private SmithImageButton(int x, int y, int width, int height, int u, int v, int hoverVOffset,
            ResourceLocation texture, Consumer<GuiButton> onPress) {
            super(0, x, y, width, height, "");
            this.u = u;
            this.v = v;
            this.hoverVOffset = hoverVOffset;
            this.texture = texture;
            this.onPress = onPress;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
            this.field_146123_n = this.hovered;
            GuiGraphics graphics = GuiGraphics.create();
            int vOffset = this.hovered ? this.v + this.hoverVOffset : this.v;
            graphics
                .blit(this.texture, this.xPosition, this.yPosition, this.u, vOffset, this.width, this.height, 256, 256);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                if (this.onPress != null) {
                    this.onPress.accept(this);
                }
                return true;
            }
            return false;
        }
    }
}
