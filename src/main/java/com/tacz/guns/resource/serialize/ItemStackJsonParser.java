package com.tacz.guns.resource.serialize;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.tacz.guns.mcshim.GsonHelper;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 把上游 1.20 的物品 json 描述（{@code {"item":"ns:path","count":N,"nbt":{...}}}）解析成 1.7.10 的
 * {@link ItemStack}。
 *
 * <p>
 * 上游用 {@code net.minecraftforge.common.crafting.CraftingHelper#getItemStack(JsonObject, boolean)}
 * （例如 {@code resource/pojo/data/block/TabConfig.java:74}、
 * {@code resource/serialize/GunSmithTableResultSerializer.java:61}）。
 *
 * <p>
 * <b>1.7.10 差异</b>：1.7.10 的 Forge 没有 {@code CraftingHelper}，也没有
 * {@code Ingredient}/{@code ForgeRegistries}，物品只能按 {@code GameRegistry.findItem(domain, path)} 查。
 * 这里实现最小子集：{@code item} 字段走注册名查表，{@code count} 与 {@code nbt} 可缺省。
 * <b>不支持</b>上游的 {@code tag}（ore dictionary）写法——那部分由
 * {@code com.tacz.guns.crafting#GunSmithTableIngredient} 自行处理（见 docs/PORT_STATUS.md 跨包契约）。
 */
public final class ItemStackJsonParser {

    private ItemStackJsonParser() {}

    /**
     * @param object 形如 {@code {"item":"tacz:modern_kinetic_gun","count":1,"nbt":{...}}}
     * @return 解析出的 {@code ItemStack}；物品不存在时抛 {@link com.google.gson.JsonSyntaxException}
     */
    public static ItemStack getItemStack(JsonObject object, boolean readNbt) {
        String itemName = GsonHelper.getAsString(object, "item");
        ItemStack stack = getItemStack(new ResourceLocation(itemName), readNbt ? object.get("nbt") : null);
        if (stack == null) {
            throw new JsonSyntaxException("Unknown item '" + itemName + "'");
        }
        if (object.has("count")) {
            stack.stackSize = Math.max(GsonHelper.getAsInt(object, "count"), 1);
        }
        return stack;
    }

    /** 按注册名取 {@link ItemStack}，物品不存在返回 {@code null}。 */
    public static @Nullable ItemStack getItemStack(ResourceLocation itemId, @Nullable JsonElement nbt) {
        Item item = GameRegistry.findItem(itemId.getResourceDomain(), itemId.getResourcePath());
        if (item == null) {
            return null;
        }
        ItemStack stack = new ItemStack(item);
        if (nbt != null && nbt.isJsonObject()) {
            try {
                NBTBase tag = JsonToNBT.func_150315_a(nbt.toString());
                if (tag instanceof NBTTagCompound compound) {
                    stack.setTagCompound(compound);
                }
            } catch (Exception exception) {
                throw new JsonSyntaxException("Failed to parse item nbt: " + nbt, exception);
            }
        }
        return stack;
    }

    /**
     * 把 {@code {"tag":"forge:ingots/iron"}} 之类的 ore dictionary 写法取成名字；
     * 非 tag 写法返回 {@code null}。留给 {@code crafting/**} 侧使用。
     */
    public static @Nullable String getOreDictionaryName(JsonObject object) {
        return object.has("tag") ? GsonHelper.getAsString(object, "tag") : null;
    }
}
