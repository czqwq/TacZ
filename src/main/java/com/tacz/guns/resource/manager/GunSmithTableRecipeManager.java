package com.tacz.guns.resource.manager;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.GunSmithTableSerializer;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.network.DataType;

/**
 * 枪械工作台配方加载器（{@code data/<namespace>/recipes/**}）。
 *
 * <p>
 * <b>上游形态</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/ModRecipe.java:18-24} 把
 * {@code GunSmithTableSerializer} 注册成 {@code gun_smith_table_crafting} 这个
 * {@code RecipeSerializer}，再由原版 {@code RecipeManager} 解析数据包里的
 * {@code data/<ns>/recipes/**}；{@code GunSmithTableRecipe} 因此天然进入
 * {@code RecipeManager} 的索引，客户端用 {@code RecipeManager#byKey} 查、JEI 直接读。
 *
 * <p>
 * <b>1.7.10 差异（本类存在的理由）</b>：没有 datapack、没有 {@code RecipeManager}、
 * 也没有「配方随资源包加载」的管线，因此改由本管理器在枪包重载管线里显式读盘：
 * <ol>
 * <li>{@link GunPackLoader#readPackData(String)} 扫出 {@code data/<ns>/recipes/**} 的全部 json
 * （id 推导规则与上游 {@code FileToIdConverter} 一致：去掉 {@code data/<ns>/recipes/} 前缀与扩展名，
 * 于是 {@code data/tacz/recipes/gun/ak47.json} → {@code tacz:gun/ak47}）；</li>
 * <li>逐条交给 {@link GunSmithTableSerializer#deserializeAndRegister}，登记进
 * {@link GunSmithTableRecipe} 的静态表（1.7.10 版 {@code RecipeManager}）。</li>
 * </ol>
 *
 * <p>
 * <b>为什么是 {@link INetworkCacheReloadListener} 而不是「在 {@code CommonAssetsManager} 里内联一段」</b>：
 * 多人服客户端的物品栏界面同样要显示配方列表，而 1.7.10 客户端的枪包数据只来自
 * {@code ServerMessageSyncGunPack} 下发的 json 快照（{@code CommonNetworkCache}）。
 * 实现该接口后，本次读到的原始 json 会自动进入
 * {@link com.tacz.guns.resource.CommonAssetsManager#getNetworkCache()} →
 * {@code ServerMessageSyncGunPack.createMessages} → 客户端
 * {@code CommonNetworkCache#fromNetwork}，{@link DataType#RECIPES} 这个枚举值终于有了生产者。
 * 若改成内联一段，服务端本地能读到配方，但多人服客户端仍然是空列表。
 *
 * <p>
 * <b>重载语义</b>：每次 {@link #reload()} 先 {@link GunSmithTableRecipe#clear()} 再登记，
 * 因此 {@code /tacz reload} 后不会残留已被删除/改名的枪包配方。
 * {@link GunSmithTableRecipe#initAll()}（把 {@code RawGunTableResult} 展开成真正的
 * {@code ItemStack}）**不在这里做**：它依赖 gun/attachment index，必须等本次重载的全部管理器都跑完，
 * 由 {@link com.tacz.guns.resource.CommonAssetsManager#initLazyRecipes()} 在管线末尾调用。
 */
public class GunSmithTableRecipeManager implements INetworkCacheReloadListener {

    /** 枪包配方 json 的 {@code type} 字段（= 上游 {@code ModRecipe} 注册的 serializer id）。 */
    public static final String RECIPE_TYPE = "tacz:gun_smith_table_crafting";

    /** 扫描目录（相对 {@code data/<namespace>/}，与 {@code PackDirectory} 的语义一致）。 */
    public static final String DIRECTORY = "recipes";

    /** 下发给客户端的原始 json 快照（键 = 配方 id）。 */
    private Map<ResourceLocation, String> networkCache = Map.of();

    @Override
    public void reload() {
        Map<ResourceLocation, JsonObject> data = GunPackLoader.readPackData(DIRECTORY);
        int loaded = loadRecipes(data, "gun pack");
        Map<ResourceLocation, String> cache = new LinkedHashMap<>(data.size());
        for (Map.Entry<ResourceLocation, JsonObject> entry : data.entrySet()) {
            cache.put(
                entry.getKey(),
                entry.getValue()
                    .toString());
        }
        this.networkCache = cache;
        GunMod.LOGGER.info(
            "[TACZ] gun smith table recipes: {} scanned / {} registered ({} for network sync)",
            data.size(),
            loaded,
            cache.size());
    }

    @Override
    public Map<ResourceLocation, String> getNetworkCache() {
        return networkCache;
    }

    @Override
    public DataType getType() {
        return DataType.RECIPES;
    }

    /**
     * 清空并重新登记一批配方 json（服务端本地枪包与客户端网络快照共用同一段逻辑）。
     *
     * <p>
     * 容错：单条配方解析失败只 {@code warn} 并跳过（带配方 id），绝不中断整轮重载——
     * 一条坏配方不该让整张工作台变空。
     *
     * @param data   配方 id → 配方 json
     * @param source 日志用的来源描述（{@code "gun pack"} / {@code "network"}）
     * @return 成功登记进 {@link GunSmithTableRecipe} 的条数
     */
    public static int loadRecipes(Map<ResourceLocation, JsonObject> data, String source) {
        GunSmithTableRecipe.clear();
        int registered = 0;
        int failed = 0;
        int skipped = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : data.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonObject json = entry.getValue();
            try {
                if (!isGunSmithTableRecipe(json)) {
                    // 上游由 RecipeManager 按 serializer type 分流；1.7.10 只能自己判，避免把别的配方类型当工作台配方
                    skipped++;
                    continue;
                }
                GunSmithTableRecipe recipe = GunSmithTableSerializer.deserializeAndRegister(id, json);
                if (recipe == null) {
                    failed++;
                    GunMod.LOGGER.warn("[TACZ] recipe {} from {} deserialized to null, skipped", id, source);
                    continue;
                }
                registered++;
            } catch (JsonSyntaxException syntax) {
                // 配方格式/物品缺失是可预期的「单条坏配方」，不是程序错误 —— 只打一条**不带栈**的 warn。
                // 之前统一走 RuntimeException 分支，导致 11 条配方各打一份 70 行的 gson 栈，把真正的问题埋掉。
                failed++;
                GunMod.LOGGER.warn("[TACZ] recipe {} from {} skipped: {}", id, source, syntax.getMessage());
            } catch (RuntimeException exception) {
                failed++;
                GunMod.LOGGER.warn("[TACZ] failed to load gun smith table recipe {} from {}", id, source, exception);
            }
        }
        if (failed > 0 || skipped > 0) {
            GunMod.LOGGER.warn(
                "[TACZ] recipe loading from {} finished with {} failure(s), {} non-smith-table recipe(s) skipped",
                source,
                failed,
                skipped);
        }
        return registered;
    }

    /**
     * 判断这条 json 是不是工作台配方。
     *
     * <p>
     * {@code type} 字段缺失时按「是」处理（宽容：老枪包/手写配方常常省这个字段，而
     * {@code GunSmithTableSerializer} 只认 {@code materials} + {@code result} 两段，缺 type 也能解析）；
     * 存在但不是 {@value #RECIPE_TYPE} 的一律跳过。
     */
    private static boolean isGunSmithTableRecipe(@Nullable JsonObject json) {
        if (json == null || !json.has("type")
            || !json.get("type")
                .isJsonPrimitive()) {
            return json != null && json.has("materials") && json.has("result");
        }
        return RECIPE_TYPE.equals(
            json.get("type")
                .getAsString());
    }
}
