package com.tacz.guns.crafting.result;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.GsonHelper;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import com.tacz.guns.resource.pojo.data.recipe.GunResult;
import com.tacz.guns.resource.serialize.ItemStackJsonParser;

/**
 * 枪械工作台配方的产物。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/crafting/result/GunSmithTableResult.java}
 * 字段（{@code result} / {@code group} / {@code raw}）与 getter（{@code getResult()} / {@code getGroup()}）、
 * {@code init()} 全部保持上游，只有「空栈」与 JSON 解析的形态变化：
 * <ul>
 * <li>{@code ItemStack.EMPTY} → {@code null}（1.7.10 的空栈表示）；</li>
 * <li>上游的 json 解析在 {@code resource/serialize/GunSmithTableResultSerializer}（用
 * {@code CraftingHelper#getItemStack}/{@code getNBT}）；1.7.10 没有 {@code CraftingHelper}，
 * 因此解析动作收敛到 {@link #fromJson(JsonElement)}，序列化器只做 Gson 适配（跨包契约）。</li>
 * </ul>
 */
public class GunSmithTableResult {

    public static final String GUN = "gun";
    public static final String AMMO = "ammo";
    public static final String ATTACHMENT = "attachment";
    public static final String CUSTOM = "custom";

    @Nullable
    private ItemStack result = null;
    @Nullable
    private ResourceLocation group = null;

    @Nullable
    private RawGunTableResult raw = null;

    public GunSmithTableResult(@Nullable ItemStack result, @Nullable ResourceLocation group) {
        this.result = result;
        this.group = group == null ? TabConfig.TAB_EMPTY : group;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw) {
        this.raw = raw;
    }

    public GunSmithTableResult(@NotNull RawGunTableResult raw, @Nullable ResourceLocation group) {
        this.raw = raw;
        this.group = group == null ? TabConfig.TAB_EMPTY : group;
    }

    /** 延迟初始化：把 {@link RawGunTableResult} 展开成真正的 {@link ItemStack}（上游同名方法）。 */
    public void init() {
        if (raw != null) {
            GunSmithTableResult initialized = RawGunTableResult.init(raw);
            this.result = initialized.getResult();
            if (group == null || group.equals(TabConfig.TAB_EMPTY)) {
                this.group = initialized.getGroup();
            }
            this.raw = null;
        }
    }

    /** 产物（1.7.10 用 {@code null} 表示空栈，对应上游 {@code ItemStack.EMPTY}）。 */
    @Nullable
    public ItemStack getResult() {
        return result;
    }

    public ResourceLocation getGroup() {
        return group;
    }

    // ================================================================================================
    // JSON（上游 GunSmithTableResultSerializer#deserialize 的等价物）
    // ================================================================================================

    /**
     * 解析枪包里的配方产物。
     *
     * <p>
     * 逐行对应上游 {@code GunSmithTableResultSerializer#deserialize}：
     * {@code type} 分支（gun/ammo/attachment 走 {@link RawGunTableResult} 延迟构建、custom 直接解析成
     * {@code ItemStack}）、{@code count}、{@code nbt}、{@code group}（不含命名空间时补 {@code tacz:}）。
     */
    public static GunSmithTableResult fromJson(@Nullable JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return new GunSmithTableResult((ItemStack) null, TabConfig.TAB_EMPTY);
        }
        JsonObject jsonObject = json.getAsJsonObject();
        String typeName = GsonHelper.getAsString(jsonObject, "type");
        int count = 1;
        NBTTagCompound extraTag = null;
        ResourceLocation tabOverride = null;
        if (jsonObject.has("count")) {
            count = Math.max(GsonHelper.getAsInt(jsonObject, "count"), 1);
        }
        if (jsonObject.has("nbt")) {
            extraTag = parseNbt(jsonObject.get("nbt"));
        }
        if (jsonObject.has("group")) {
            String rawGroup = GsonHelper.getAsString(jsonObject, "group");
            if (!rawGroup.contains(":")) {
                rawGroup = GunMod.MOD_ID + ":" + rawGroup;
            }
            tabOverride = new ResourceLocation(rawGroup);
        }

        switch (typeName) {
            case GUN:
            case AMMO:
            case ATTACHMENT: {
                RawGunTableResult raw = new RawGunTableResult(
                    typeName,
                    new ResourceLocation(GsonHelper.getAsString(jsonObject, "id")),
                    count);
                if (extraTag != null) {
                    raw.setNbt(extraTag);
                }
                if (GUN.equals(typeName)) {
                    GunResult gunResult = CommonAssetsManager.GSON.fromJson(jsonObject, GunResult.class);
                    if (gunResult != null) {
                        raw.setExtraData(gunResult);
                    }
                }
                return new GunSmithTableResult(raw, tabOverride);
            }
            case CUSTOM: {
                ItemStack itemStack = ItemStackJsonParser
                    .getItemStack(GsonHelper.getAsJsonObject(jsonObject, "item"), true);
                return new GunSmithTableResult(itemStack, tabOverride);
            }
            default:
                return new GunSmithTableResult((ItemStack) null, TabConfig.TAB_EMPTY);
        }
    }

    /**
     * 上游 {@code CraftingHelper#getNBT(JsonElement)} 的 1.7.10 版（{@code JsonToNBT}）。
     *
     * @return 解析失败返回 {@code null}（上游此情形会抛异常；这里记日志后按「无 nbt」处理，避免整包配方加载失败）
     */
    @Nullable
    private static NBTTagCompound parseNbt(JsonElement element) {
        try {
            NBTBase tag = JsonToNBT.func_150315_a(element.toString());
            return tag instanceof NBTTagCompound compound ? compound : null;
        } catch (Exception exception) {
            GunMod.LOGGER.warn("Failed to parse recipe result nbt {}", element, exception);
            return null;
        }
    }
}
