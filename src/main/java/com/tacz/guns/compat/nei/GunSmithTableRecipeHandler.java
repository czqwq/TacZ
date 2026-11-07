package com.tacz.guns.compat.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.item.GunSmithTableItem;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.pojo.data.block.TabConfig;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * NEI 的「枪械工作台配方」页面（上游 1.20.1 的 {@code compat/jei/category/GunSmithTableCategory}）。
 *
 * <p>
 * 上游给每个 {@code CommonBlockIndex}（枪械工作台 / 弹药工作台 / 配件工作台）各注册一个 JEI
 * {@code RecipeType}，配方按该工作台的 {@code RecipeFilter} 与 {@code BlockData#getTabs()} 过滤；
 * NEI 侧这里是「一个 {@link TemplateRecipeHandler} 实例 = 一个工作台类型」，按同样规则过滤
 * {@link GunSmithTableRecipe#getAll()}。
 *
 * <p>
 * <b>为什么覆写 {@link #newInstance()}（实机报错 {@code NoSuchMethodException: &lt;init&gt;()}）</b>：
 * NEI 的 {@code TemplateRecipeHandler#newInstance()} 用<b>反射无参构造器</b>复制 handler 去做配方查询
 * （{@code templateRecipeHandler.newInstance(): getClass().getConstructor().newInstance()}），而本 handler
 * 必须携带 {@code blockId} 才能确定工作台类型 ⇒ 没有可用的无参构造器。NEI 为此留了扩展点：
 * 覆写 {@code newInstance()} 返回「携带同一参数的新实例」即可，GT5U 的
 * {@code gregtech/nei/GTNEIDefaultHandler#newInstance()} 就是同一写法。
 *
 * <p>
 * <b>为什么注册 transfer rect（催化剂 U 查询依赖它）</b>：对工作台物品按 U 时 NEI 走
 * {@code TemplateRecipeHandler#getUsageAndCatalystHandler}：先查 {@code RecipeCatalysts} 命中催化剂，
 * 然后<b>必须</b>能从 {@code transferRects} 里找到一条查询全部配方的入口，否则只打一条
 * "failed to load catalyst handler, implement loadTransferRects" 并退化成普通 usage 查询（空页）。
 * 因此构造时登记一条「箭头区域 → 本工作台全部配方」的 {@link RecipeTransferRect}。
 */
public class GunSmithTableRecipeHandler extends TemplateRecipeHandler {

    /** 产物格位置（166x65 面板右侧）。 */
    private static final int RESULT_X = 144;
    private static final int RESULT_Y = 24;
    /** 材料 → 产物箭头。 */
    private static final int ARROW_X = 126;
    private static final int ARROW_Y = 27;
    private static final int ARROW_WIDTH = 14;
    private static final int ARROW_HEIGHT = 12;
    /** 材料格：一行最多 6 个，超过 6 个分两行（上游 JEI 布局的分行阈值同样是 7）。 */
    private static final int INPUT_START_X = 8;
    private static final int INPUT_SPACING = 20;
    private static final int INPUT_SINGLE_ROW_Y = 24;
    private static final int INPUT_ROW_1_Y = 7;
    private static final int INPUT_ROW_2_Y = 27;
    private static final int INPUTS_PER_ROW = 6;
    private static final String HANDLER_ID_PREFIX = "tacz:nei/gun_smith_table/";

    /** 默认包的三个工作台 id（枪包数据尚未同步时的兜底）。 */
    private static final ResourceLocation[] DEFAULT_TABLE_IDS = { new ResourceLocation("tacz", "gun_smith_table"),
        new ResourceLocation("tacz", "workbench_a"), new ResourceLocation("tacz", "workbench_c") };

    private final ResourceLocation blockId;
    private final String handlerId;
    @Nullable
    private ItemStack cachedTableIcon;
    @Nullable
    private List<GunSmithTableRecipe> cachedRecipes;

    private GunSmithTableRecipeHandler(ResourceLocation blockId) {
        this.blockId = blockId;
        this.handlerId = handlerIdFor(blockId);
        // 注意：不能改成覆写 loadTransferRects() —— TemplateRecipeHandler 构造器就会调用它，
        // 而那时本类的 blockId/handlerId 字段还没有赋值（Java 先 super() 后字段初始化）。
        // 本 rect 不注册到任何 GUI（getRecipeTransferRectGuis() = null），只需要留在 transferRects 里
        // 供 getUsageAndCatalystHandler / 点击箭头时读取，所以在构造完成后补登即可。
        this.transferRects.add(
            new RecipeTransferRect(new Rectangle(ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT), handlerId));
    }

    /**
     * 当前枪包里的全部工作台类型 id；枪包数据尚未同步时退回默认三个 id。
     *
     * <p>
     * 排序保证 handler / tab 顺序稳定（与 {@code NEIHandlerInfo} 的注册顺序一致）。
     */
    static List<ResourceLocation> tableBlockIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonBlockIndex> entry : TimelessAPI.getAllCommonBlockIndex()) {
            if (isGunSmithTable(entry.getValue())) {
                ids.add(entry.getKey());
            }
        }
        if (ids.isEmpty()) {
            for (ResourceLocation tableId : DEFAULT_TABLE_IDS) {
                ids.add(tableId);
            }
        }
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        return ids;
    }

    /** 由 {@link NEITaczConfig} 在 NEI 插件加载时调用；一个工作台类型一个实例。 */
    static List<GunSmithTableRecipeHandler> createAll() {
        List<GunSmithTableRecipeHandler> handlers = new ArrayList<>();
        for (ResourceLocation tableId : tableBlockIds()) {
            handlers.add(new GunSmithTableRecipeHandler(tableId));
        }
        return handlers;
    }

    /** 该工作台的 NEI handler id（{@link codechicken.nei.recipe.HandlerInfo} 与催化剂都用它做键）。 */
    static String handlerIdFor(ResourceLocation blockId) {
        return HANDLER_ID_PREFIX + blockId.toString();
    }

    /**
     * 工作台方块物品（用于登记 NEI 催化剂与页签图标）。
     *
     * <p>
     * 工作台索引未加载时（NEI 在 FML LoadComplete 就发 handler 信息事件，早于第一次资源重载）退回
     * {@link GameRegistry#findItem}：方块物品在 preInit 已注册，ItemStack 的有效性不受索引影响
     * （方块物品的模型/贴图是真正绘制时才按 NBT 里的 block id 去查索引）。
     */
    @Nullable
    static ItemStack tableIconFor(ResourceLocation blockId) {
        CommonBlockIndex index = TimelessAPI.getCommonBlockIndex(blockId)
            .orElse(null);
        Item blockItem = index != null ? index.getBlock()
            : GameRegistry.findItem(blockId.getResourceDomain(), blockId.getResourcePath());
        if (!(blockItem instanceof ItemBlock)) {
            return null;
        }
        return BlockItemBuilder.create(blockItem)
            .setId(blockId)
            .build();
    }

    /** 工作台显示名（优先用枪包 index 里的 name 键，没有索引时退回内置 lang 键）。 */
    static String recipeNameFor(ResourceLocation blockId) {
        CommonBlockIndex index = TimelessAPI.getCommonBlockIndex(blockId)
            .orElse(null);
        if (index != null && index.getPojo()
            .getName() != null) {
            return StatCollector.translateToLocal(
                index.getPojo()
                    .getName());
        }
        String path = blockId.getResourcePath();
        if ("workbench_a".equals(path)) {
            return StatCollector.translateToLocal("tacz.block.ammo_workbench.name");
        }
        if ("workbench_c".equals(path)) {
            return StatCollector.translateToLocal("tacz.block.attachment_workbench.name");
        }
        return StatCollector.translateToLocal("tacz.block." + path + ".name");
    }

    /** 工作台方块物品（用于登记 NEI 催化剂：对工作台物品按 U 可看它支持的全部配方）。 */
    @Nullable
    ItemStack getTableIcon() {
        if (cachedTableIcon == null) {
            cachedTableIcon = tableIconFor(blockId);
        }
        return cachedTableIcon;
    }

    /**
     * NEI 用反射无参构造器复制 handler，本 handler 需要携带 blockId ⇒ 按当前 blockId 重建。
     * （{@link TemplateRecipeHandler#newInstance()} 的官方扩展点，参见 GT5U {@code GTNEIDefaultHandler}。）
     */
    @Override
    public TemplateRecipeHandler newInstance() {
        return new GunSmithTableRecipeHandler(blockId);
    }

    @Override
    public String getHandlerId() {
        return handlerId;
    }

    @Override
    public String getRecipeName() {
        return recipeNameFor(blockId);
    }

    @Override
    public String getGuiTexture() {
        // drawBackground 完全自绘，仅需一个合法贴图路径满足抽象方法；用 NEI 自带的通用配方背景。
        return "nei:textures/gui/recipebg.png";
    }

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        // "all" 是 NEI 的「全部配方」页；handlerId 是 transferRect 的 outputId
        //（点箭头 / 对工作台物品按 U 看该工作台全部配方时走这里）。
        if ("all".equals(outputId) || handlerId.equals(outputId)) {
            for (GunSmithTableRecipe recipe : getTableRecipes()) {
                arecipes.add(new CachedGunSmithRecipe(recipe));
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
        for (GunSmithTableRecipe recipe : getTableRecipes()) {
            if (NeiStacks.sameResult(recipe.getOutput(), result)) {
                arecipes.add(new CachedGunSmithRecipe(recipe));
            }
        }
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        if (ingredient == null) {
            return;
        }
        for (GunSmithTableRecipe recipe : getTableRecipes()) {
            for (GunSmithTableIngredient input : recipe.getInputs()) {
                if (input.test(ingredient)) {
                    arecipes.add(new CachedGunSmithRecipe(recipe));
                    break;
                }
            }
        }
    }

    @Override
    public void drawBackground(int recipe) {
        NeiRender.drawPanel();
        CachedGunSmithRecipe cached = (CachedGunSmithRecipe) arecipes.get(recipe);
        NeiRender.drawSlot(RESULT_X, RESULT_Y);
        for (PositionedStack ingredient : cached.ingredients) {
            NeiRender.drawSlot(ingredient.relx, ingredient.rely);
        }
        NeiRender.drawArrow(ARROW_X, ARROW_Y);
    }

    @Override
    public void drawForeground(int recipe) {
        // 背景已自绘，不沿用 TemplateRecipeHandler 的贴图绘制。
    }

    /**
     * 取当前工作台类型支持的配方。
     *
     * <p>
     * 与上游 {@code GunModPlugin#registerRecipes} 逐条对应：
     * {@code blockIndex.getFilter().filter(recipes, GunSmithTableRecipe::getId)}，再剔除
     * {@code result.getGroup()} 不在 {@code BlockData#getTabs()} 里的配方。
     */
    private List<GunSmithTableRecipe> getTableRecipes() {
        if (cachedRecipes != null) {
            return cachedRecipes;
        }
        CommonBlockIndex index = TimelessAPI.getCommonBlockIndex(blockId)
            .orElse(null);
        RecipeFilter filter = index == null ? null : index.getFilter();
        List<ResourceLocation> tabs = new ArrayList<>();
        if (index != null) {
            for (TabConfig tab : index.getData()
                .getTabs()) {
                tabs.add(tab.id());
            }
        }
        List<GunSmithTableRecipe> recipes = new ArrayList<>();
        for (GunSmithTableRecipe recipe : GunSmithTableRecipe.getAll()) {
            if (recipe.getOutput() == null) {
                // 产物未初始化（RawGunTableResult 对应的枪/配件索引缺失）⇒ 无可显示内容
                continue;
            }
            if (filter != null && !filter.contains(recipe.getId())) {
                continue;
            }
            if (!tabs.isEmpty() && !tabs.contains(
                recipe.getResult()
                    .getGroup())) {
                continue;
            }
            recipes.add(recipe);
        }
        recipes.sort(
            (a, b) -> a.getId()
                .toString()
                .compareTo(
                    b.getId()
                        .toString()));
        cachedRecipes = recipes;
        return recipes;
    }

    /** 与 {@link GunSmithTableItem} 无关，仅用于跳过非工作台方块索引（如其它模组注册的方块索引）。 */
    static boolean isGunSmithTable(@Nullable CommonBlockIndex index) {
        return index != null && index.getBlock() instanceof GunSmithTableItem;
    }

    /** 一条工作台配方在 NEI 页面上的缓存形态。 */
    public class CachedGunSmithRecipe extends CachedRecipe {

        private final List<PositionedStack> ingredients = new ArrayList<>();
        @Nullable
        private final PositionedStack result;

        CachedGunSmithRecipe(GunSmithTableRecipe recipe) {
            ItemStack output = recipe.getOutput();
            this.result = output == null ? null : new PositionedStack(output, RESULT_X, RESULT_Y);
            List<GunSmithTableIngredient> inputs = recipe.getInputs();
            int size = inputs.size();
            for (int i = 0; i < size; i++) {
                List<ItemStack> stacks = NeiStacks.ingredientStacks(inputs.get(i));
                if (stacks.isEmpty()) {
                    // 材料无法解析（上游对未知注册名的语义是 AIR 材料，配方保留但无解）⇒ 不画该格
                    continue;
                }
                int x;
                int y;
                if (size <= INPUTS_PER_ROW) {
                    x = INPUT_START_X + INPUT_SPACING * i;
                    y = INPUT_SINGLE_ROW_Y;
                } else {
                    x = INPUT_START_X + INPUT_SPACING * (i % INPUTS_PER_ROW);
                    y = i < INPUTS_PER_ROW ? INPUT_ROW_1_Y : INPUT_ROW_2_Y;
                }
                ingredients.add(new PositionedStack(stacks, x, y));
            }
        }

        @Override
        public List<PositionedStack> getIngredients() {
            return ingredients;
        }

        @Override
        @Nullable
        public PositionedStack getResult() {
            return result;
        }
    }
}
