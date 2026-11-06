package com.tacz.guns.client.resource.serialize;

import java.lang.reflect.Type;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.*;
import com.tacz.guns.mcshim.GsonHelper;

import cpw.mods.fml.common.registry.GameData;

public class ItemStackSerializer implements JsonDeserializer<ItemStack> {

    @Override
    public ItemStack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        if (!json.isJsonObject()) {
            throw new JsonSyntaxException("Expected " + json + " to be a ItemStack because it's not an object");
        }
        JsonObject jsonObject = json.getAsJsonObject();
        // TODO(port): 上游 ItemStackSerializer.java:14 用 CraftingHelper.getItemStack 解析 1.20 物品 JSON，1.7.10 无对应，此处按
        // item+count+damage+nbt 降级解析
        NBTTagCompound nbt = readNbt(jsonObject);
        // 一、nbt 字段本身就是一整份 ItemStack NBT（带 id）时，直接交给原版反序列化
        if (nbt != null && nbt.hasKey("id")) {
            ItemStack fromNbt = ItemStack.loadItemStackFromNBT(nbt);
            if (fromNbt != null) {
                return fromNbt;
            }
        }
        // 二、1.20 物品 JSON 的常规形态：item（注册名）+ count + damage，nbt 作为附加 tag
        String itemName = GsonHelper.getNullableString(jsonObject, "item");
        if (itemName == null || itemName.isEmpty()) {
            // 无法解析时返回 null（上游 1.20 会抛异常，这里按任务要求降级为 null，由调用方决定回退）
            return null;
        }
        Item item = GameData.getItemRegistry()
            .getObject(itemName);
        if (item == null) {
            return null;
        }
        int count = Math.max(GsonHelper.getInt(jsonObject, "count", 1), 1);
        int damage = Math.max(GsonHelper.getInt(jsonObject, "damage", 0), 0);
        ItemStack stack = new ItemStack(item, count, damage);
        if (nbt != null) {
            stack.setTagCompound(nbt);
        }
        return stack;
    }

    /**
     * 读取 1.20 物品 JSON 的 {@code "nbt"} 字段。
     * <p>
     * TODO(port): 上游用 {@code TagParser} 解析 SNBT 字符串；1.7.10 只有
     * {@code net.minecraft.nbt.JsonToNBT#func_150315_a(String)}（MCP stable_12 未反混淆该方法名），
     * 语法同为 SNBT，解析失败返回 null。
     */
    @Nullable
    private static NBTTagCompound readNbt(JsonObject jsonObject) {
        JsonElement nbtElement = jsonObject.get("nbt");
        if (nbtElement == null || !nbtElement.isJsonPrimitive()) {
            // 非字符串形态（1.20 也允许直接给 JSON 对象）暂不解析，返回 null 走 item+count+damage 分支
            return null;
        }
        try {
            NBTBase parsed = JsonToNBT.func_150315_a(nbtElement.getAsString());
            return parsed instanceof NBTTagCompound ? (NBTTagCompound) parsed : null;
        } catch (Exception e) {
            return null;
        }
    }
}
