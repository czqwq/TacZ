package com.tacz.guns.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;

/**
 * 枪械工作台配方（<b>不是</b>原版配方）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/crafting/GunSmithTableRecipe.java}
 * 
 * <pre>
 * {@code public class GunSmithTableRecipe implements Recipe<Inventory>}
 * </pre>
 * 
 * 上游把工作台配方塞进原版 {@code Recipe} 体系（{@code RecipeType} + {@code RecipeSerializer} + datapack json），
 * 只为了蹭 {@code RecipeManager} 的索引与 JEI 的读取；{@code matches}/{@code assemble} 恒为 false/EMPTY，
 * 真正的合成逻辑在 {@code GunSmithTableMenu#doCraft}。
 *
 * <p>
 * <b>1.7.10 差异（重要）</b>：
 * <ul>
 * <li>1.7.10 的 {@code net.minecraft.item.crafting.IRecipe} 只有 4 个方法，会被
 * {@code CraftingManager#findMatchingRecipe} <b>强转</b>后逐个 {@code matches}——
 * 把工作台配方塞进 {@code CraftingManager#getRecipeList()} 会在每次打开工作台时
 * {@code ClassCastException}。因此本类<b>不实现</b> {@code IRecipe}，而是一个纯数据类。</li>
 * <li>上游 {@code RecipeType}/{@code RecipeSerializer}/{@code getId()}/{@code getSerializer()}
 * {@code getType()} 全部删除（1.7.10 无配方注册表）。{@link #getId()} 保留为普通 getter。</li>
 * <li>上游靠 {@code RecipeManager} 提供 {@code byKey(id)} 检索；1.7.10 没有该管线，
 * 因此本类自带一个静态登记表（{@link #register}/{@link #getById}/{@link #getAll}），
 * 由 {@code init/ModRecipe}（枪包加载完之后）把 json 解析出来的配方登记进来。</li>
 * <li>{@code ItemStack getResultItem(RegistryAccess)} → {@link #getOutput()}
 * （1.7.10 的 {@code ItemStack} 可为 {@code null}）。</li>
 * <li>上游 {@code TagsUpdatedEvent} 触发的延迟 {@code init()} 保留（{@link #init()}），
 * 由 {@code CommonAssetsManager#initLazyRecipes()} / {@link #initAll()} 在注册表就绪后调用。</li>
 * </ul>
 *
 * <p>
 * 其余字段（{@code id} / {@code result} / {@code inputs}）与全部 getter 名称保持上游。
 */
public class GunSmithTableRecipe {

    /** 1.7.10 的「工作台配方索引」（替代上游 {@code RecipeManager}）。 */
    private static final Map<ResourceLocation, GunSmithTableRecipe> REGISTRY = new LinkedHashMap<>();

    private final ResourceLocation id;
    private final GunSmithTableResult result;
    private final List<GunSmithTableIngredient> inputs;

    public GunSmithTableRecipe(ResourceLocation id, GunSmithTableResult result, List<GunSmithTableIngredient> inputs) {
        this.id = id;
        this.result = result;
        this.inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    public GunSmithTableRecipe(ResourceLocation id, TableRecipe tableRecipe) {
        this(id, tableRecipe.getResult(), tableRecipe.getMaterials());
    }

    public ResourceLocation getId() {
        return this.id;
    }

    /**
     * 上游 {@code getResultItem(RegistryAccess)} 的等价物（1.7.10 无注册表访问）。
     * 与上游 {@code GunSmithTableRecipe.java:50-52} 一致：<b>返回副本</b>，否则调用方的 {@code splitStack} 会拆空配方持有的栈。
     */
    @Nullable
    public ItemStack getOutput() {
        ItemStack output = result.getResult();
        return output == null ? null : output.copy();
    }

    public List<GunSmithTableIngredient> getInputs() {
        return inputs;
    }

    public GunSmithTableResult getResult() {
        return this.result;
    }

    /** 延迟初始化（上游 {@code TagsUpdatedEvent} 时调用）。 */
    public void init() {
        result.init();
    }

    public ResourceLocation getTab() {
        return result.getGroup();
    }

    // ================================================================================================
    // 静态登记表（1.7.10 替代 RecipeManager）
    // ================================================================================================

    /** 登记一条工作台配方（同一 id 重复登记时后者覆盖前者，与 {@code RecipeManager} 的 map 语义一致）。 */
    public static void register(GunSmithTableRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return;
        }
        REGISTRY.put(recipe.getId(), recipe);
    }

    @Nullable
    public static GunSmithTableRecipe getById(@Nullable ResourceLocation recipeId) {
        return recipeId == null ? null : REGISTRY.get(recipeId);
    }

    /** 全部工作台配方（只读）。 */
    public static Collection<GunSmithTableRecipe> getAll() {
        return List.copyOf(REGISTRY.values());
    }

    /** 全部工作台配方的 id（只读）。 */
    public static List<ResourceLocation> getAllIds() {
        return new ArrayList<>(REGISTRY.keySet());
    }

    /** 清空登记表（枪包重载时调用）。 */
    public static void clear() {
        REGISTRY.clear();
    }

    /**
     * 对所有已登记配方执行 {@link #init()}（材料表就绪后调用一次）。
     *
     * <p>
     * 逐条 {@code try/catch}：{@link #init()} 会查 gun/attachment index 并构造 {@code ItemStack}
     * （{@code RawGunTableResult#init}），某个枪包的 index 异常不应该让<b>其余</b>配方的产物全部为空
     * —— 否则一次坏包就会让整张工作台「有列表但做不出任何东西」。
     */
    public static void initAll() {
        for (GunSmithTableRecipe recipe : REGISTRY.values()) {
            try {
                recipe.init();
            } catch (RuntimeException exception) {
                GunMod.LOGGER.warn(
                    "[TACZ] failed to init gun smith table recipe {}, its output stays empty",
                    recipe.id,
                    exception);
            }
        }
    }

    @Override
    public String toString() {
        return "GunSmithTableRecipe{" + id + ", inputs=" + inputs.size() + '}';
    }
}
