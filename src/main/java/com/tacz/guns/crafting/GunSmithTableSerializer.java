package com.tacz.guns.crafting;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonObject;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;

/**
 * 工作台配方序列化器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/crafting/GunSmithTableSerializer.java}
 * 
 * <pre>
 * {@code public class GunSmithTableSerializer implements RecipeSerializer<GunSmithTableRecipe>}
 * </pre>
 * 
 * 上游实现 {@code RecipeSerializer} 的 {@code fromJson}/{@code fromNetwork}/{@code toNetwork}
 * （要为客户端同步整份配方）。
 *
 * <p>
 * <b>1.7.10 差异</b>：1.7.10 没有 {@code RecipeSerializer} 接口、也没有「配方随 datapack 加载」的管线
 * （{@code GameRegistry.addRecipe} 只在服务端代码里调用，客户端共用同一份代码 ⇒ 不需要序列化同步）。
 * 因此本类降级成<b>静态 Gson 解析工具</b>，保留类名与 {@code deserialize} 入口：
 * <ul>
 * <li>{@code CommonAssetsManager.GSON} 已注册 {@code GunSmithTableIngredient} / {@code GunSmithTableResult}
 * 的适配器（见 {@code resource/CommonAssetsManager}），因此这里只做「json → {@link TableRecipe} →
 * {@link GunSmithTableRecipe}」两段；</li>
 * <li>{@code fromNetwork}/{@code toNetwork} 删除（1.7.10 无 {@code FriendlyByteBuf} 配方同步）；</li>
 * <li>gson 版本是 1.7.10 环境的 <b>2.2.4</b>：没有 {@code JsonParser#parseString}、
 * 没有 {@code Gson#fromJson(JsonElement, TypeToken)} 之外的现代重载；这里只用
 * {@code Gson#fromJson(JsonElement, Class)}（2.2.4 起即有）。</li>
 * </ul>
 */
public final class GunSmithTableSerializer {

    private GunSmithTableSerializer() {}

    /**
     * 解析一条工作台配方。
     *
     * @param id         配方 id（枪包里的 {@code data/<ns>/recipes/misc/<name>.json} 对应的 id）
     * @param jsonObject 配方 json（{@code materials[]} + {@code result}）
     * @return 解析成功返回配方；{@code GSON} 返回 {@code null} 时返回 {@code null}（上游同语义）
     */
    @Nullable
    public static GunSmithTableRecipe deserialize(ResourceLocation id, JsonObject jsonObject) {
        TableRecipe tableRecipe = CommonAssetsManager.GSON.fromJson(jsonObject, TableRecipe.class);
        if (tableRecipe == null) {
            return null;
        }
        return new GunSmithTableRecipe(id, tableRecipe);
    }

    /**
     * 解析并登记到 {@link GunSmithTableRecipe} 的静态表（1.7.10 替代 {@code RecipeManager} 的索引）。
     *
     * @return 解析失败返回 {@code null}
     */
    @Nullable
    public static GunSmithTableRecipe deserializeAndRegister(ResourceLocation id, JsonObject jsonObject) {
        GunSmithTableRecipe recipe = deserialize(id, jsonObject);
        if (recipe != null) {
            GunSmithTableRecipe.register(recipe);
        }
        return recipe;
    }

    /** 解析 {@code result} 子树（供只需产物的调用方，如调试/校验）。 */
    public static GunSmithTableResult deserializeResult(JsonObject jsonObject) {
        return GunSmithTableResult.fromJson(jsonObject.get("result"));
    }
}
