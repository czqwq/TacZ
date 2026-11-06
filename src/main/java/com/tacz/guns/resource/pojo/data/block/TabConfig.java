package com.tacz.guns.resource.pojo.data.block;

import java.lang.reflect.Type;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.mcshim.GsonHelper;
import com.tacz.guns.resource.serialize.ItemStackJsonParser;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 枪械工作台 / 改装台的分类标签配置。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/pojo/data/block/TabConfig.java}。
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code Component} → {@link IChatComponent} + {@link ChatComponentTranslation}（1.20 才有 {@code Component}）。</li>
 * <li>{@code CraftingHelper#getItemStack(JsonObject, boolean)} → {@link ItemStackJsonParser#getItemStack}
 * （1.7.10 的 Forge 没有 {@code CraftingHelper}）。</li>
 * <li>{@code ModItems.GUN_SMITH_TABLE.get().getDefaultInstance()} → {@code GameRegistry.findItem(...)}，
 * 避免与 {@code init/ModItems} 的注册对象形态耦合（1.7.10 无 {@code RegistryObject}）。</li>
 * </ul>
 */
public record TabConfig(ResourceLocation id, String name, ItemStack icon) {

    public static final ResourceLocation TAB_AMMO = new ResourceLocation(GunMod.MOD_ID, "ammo");

    public static final ResourceLocation TAB_PISTOL = new ResourceLocation(GunMod.MOD_ID, "pistol");
    public static final ResourceLocation TAB_SNIPER = new ResourceLocation(GunMod.MOD_ID, "sniper");
    public static final ResourceLocation TAB_DMR = new ResourceLocation(GunMod.MOD_ID, "dmr");
    public static final ResourceLocation TAB_RIFLE = new ResourceLocation(GunMod.MOD_ID, "rifle");
    public static final ResourceLocation TAB_SHOTGUN = new ResourceLocation(GunMod.MOD_ID, "shotgun");
    public static final ResourceLocation TAB_SMG = new ResourceLocation(GunMod.MOD_ID, "smg");
    public static final ResourceLocation TAB_RPG = new ResourceLocation(GunMod.MOD_ID, "rpg");
    public static final ResourceLocation TAB_MG = new ResourceLocation(GunMod.MOD_ID, "mg");

    public static final ResourceLocation TAB_SCOPE = new ResourceLocation(GunMod.MOD_ID, "scope");
    public static final ResourceLocation TAB_MUZZLE = new ResourceLocation(GunMod.MOD_ID, "muzzle");
    public static final ResourceLocation TAB_STOCK = new ResourceLocation(GunMod.MOD_ID, "stock");
    public static final ResourceLocation TAB_GRIP = new ResourceLocation(GunMod.MOD_ID, "grip");
    public static final ResourceLocation TAB_EXTENDED_MAG = new ResourceLocation(GunMod.MOD_ID, "extended_mag");
    public static final ResourceLocation TAB_LASER = new ResourceLocation(GunMod.MOD_ID, "laser");

    public static final ResourceLocation TAB_MISC = new ResourceLocation(GunMod.MOD_ID, "misc");
    public static final ResourceLocation TAB_EMPTY = new ResourceLocation(GunMod.MOD_ID, "empty");

    public static final List<TabConfig> DEFAULT_TABS = List.of(
        new TabConfig(
            TabConfig.TAB_AMMO,
            "tacz.type.ammo.name",
            AmmoItemBuilder.create()
                .setId(DefaultAssets.DEFAULT_AMMO_ID)
                .build()),
        new TabConfig(
            TabConfig.TAB_PISTOL,
            "tacz.type.pistol.name",
            GunItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "glock_17"))
                .forceBuild()),
        new TabConfig(
            TabConfig.TAB_SNIPER,
            "tacz.type.sniper.name",
            GunItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "ai_awp"))
                .forceBuild()),
        new TabConfig(
            TabConfig.TAB_DMR,
            "tacz.type.dmr.name",
            GunItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "mk14"))
                .forceBuild()),
        new TabConfig(
            TabConfig.TAB_RIFLE,
            "tacz.type.rifle.name",
            GunItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "ak47"))
                .forceBuild()),
        new TabConfig(
            TabConfig.TAB_SHOTGUN,
            "tacz.type.shotgun.name",
            GunItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "db_short"))
                .forceBuild()),
        new TabConfig(
            TabConfig.TAB_SMG,
            "tacz.type.smg.name",
            GunItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "hk_mp5a5"))
                .forceBuild()),
        new TabConfig(
            TabConfig.TAB_RPG,
            "tacz.type.rpg.name",
            GunItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "rpg7"))
                .forceBuild()),
        new TabConfig(
            TabConfig.TAB_MG,
            "tacz.type.mg.name",
            GunItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "m249"))
                .forceBuild()),
        new TabConfig(
            TabConfig.TAB_SCOPE,
            "tacz.type.scope.name",
            AttachmentItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "scope_acog_ta31"))
                .build()),
        new TabConfig(
            TabConfig.TAB_MUZZLE,
            "tacz.type.muzzle.name",
            AttachmentItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "muzzle_compensator_trident"))
                .build()),
        new TabConfig(
            TabConfig.TAB_STOCK,
            "tacz.type.stock.name",
            AttachmentItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "stock_militech_b5"))
                .build()),
        new TabConfig(
            TabConfig.TAB_GRIP,
            "tacz.type.grip.name",
            AttachmentItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "grip_magpul_afg_2"))
                .build()),
        new TabConfig(
            TabConfig.TAB_EXTENDED_MAG,
            "tacz.type.extended_mag.name",
            AttachmentItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "extended_mag_3"))
                .build()),
        new TabConfig(
            TabConfig.TAB_LASER,
            "tacz.type.laser.name",
            AttachmentItemBuilder.create()
                .setId(new ResourceLocation(GunMod.MOD_ID, "laser_compact"))
                .build()),
        new TabConfig(TabConfig.TAB_MISC, "tacz.type.misc.name", gunSmithTableIcon()));

    /** 1.7.10 差异：物品没有 {@code RegistryObject}，按注册名查。注册尚未完成时返回 {@code null}（= 空栈）。 */
    private static ItemStack gunSmithTableIcon() {
        Item item = GameRegistry.findItem(GunMod.MOD_ID, "gun_smith_table");
        return item == null ? null : new ItemStack(item);
    }

    public static class Deserializer implements JsonDeserializer<TabConfig> {

        @Override
        public TabConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("TabConfig must be a JSON object");
            }
            JsonObject object = json.getAsJsonObject();
            if (!object.has("id") || !object.get("id")
                .isJsonPrimitive()) {
                throw new JsonParseException("TabConfig must have an id");
            }
            ResourceLocation id = context.deserialize(object.get("id"), ResourceLocation.class);
            ItemStack icon = ItemStackJsonParser.getItemStack(GsonHelper.getAsJsonObject(object, "icon"), true);
            String name = GsonHelper.getAsString(object, "name", "tacz.type.unknown.name");
            return new TabConfig(id, name, icon);
        }
    }

    @NotNull
    public IChatComponent getName() {
        return new ChatComponentTranslation(name == null ? "tacz.type.unknown.name" : name);
    }
}
