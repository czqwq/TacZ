package com.tacz.guns.resource.serialize;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.mcshim.GsonHelper;

/**
 * 工作台配方材料（{@code materials[]} 的每一项）反序列化器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/serialize/GunSmithTableIngredientSerializer.java}，
 * 内部用 {@code net.minecraft.world.item.crafting.Ingredient#fromJson} 解析 {@code "item"} 字段。
 *
 * <p>
 * <b>1.7.10 差异</b>：1.7.10 完全没有 {@code Ingredient} 这个类型，
 * 材料要么是具体物品（{@code {"item":"ns:path"}}）要么是矿物词典名（{@code {"tag":"forge:ingots/iron"}}），
 * 承载它的 {@link GunSmithTableIngredient} 由 {@code crafting/**} 工作流重新设计。
 * 因此这里把 {@code item} 子树的解析<b>委托给 {@link GunSmithTableIngredient}</b>，
 * 本类只负责读出 {@code count} 与结构校验（跨包契约见 docs/PORT_STATUS.md）。
 */
public class GunSmithTableIngredientSerializer implements JsonDeserializer<GunSmithTableIngredient> {

    @Override
    public GunSmithTableIngredient deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            if (!jsonObject.has("item")) {
                throw new JsonSyntaxException("Expected " + jsonObject + " must has a item member");
            }
            int count = 1;
            if (jsonObject.has("count")) {
                count = Math.max(GsonHelper.getAsInt(jsonObject, "count"), 1);
            }
            return GunSmithTableIngredient.fromJson(jsonObject.get("item"), count);
        } else {
            throw new JsonSyntaxException("Expected " + json + " to be a Pair because it's not an object");
        }
    }
}
