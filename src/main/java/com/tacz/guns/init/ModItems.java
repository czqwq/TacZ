package com.tacz.guns.init;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.GunItemManager;
import com.tacz.guns.item.AmmoBoxItem;
import com.tacz.guns.item.AmmoItem;
import com.tacz.guns.item.AttachmentItem;
import com.tacz.guns.item.ModernKineticGunItem;
import com.tacz.guns.item.TargetMinecartItem;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 搬运自上游 1.20.1 {@code init/ModItems.java}。
 *
 * <p>
 * 1.7.10 适配：
 * <ul>
 * <li>{@code DeferredRegister<Item>}/{@code RegistryObject} → 普通 public static 字段 +
 * {@link GameRegistry#registerItem}（外部代码直接用 {@code ModItems.XXX}，不再有 {@code .get()}）；</li>
 * <li>方块物品由 {@link ModBlocks#register()} 用 {@code registerBlock(..., itemClass, ...)} 创建，
 * 这里通过 {@link Item#getItemFromBlock} 取回实例并暴露为字段（调用顺序：ModBlocks 先于 ModItems）；</li>
 * <li>{@code RegisterEvent} 里的 {@code GunItemManager.registerGunItem} → 在 {@link #register()} 尾部直接调用。</li>
 * </ul>
 */
public class ModItems {

    public static Item MODERN_KINETIC_GUN;

    public static Item AMMO;
    public static Item ATTACHMENT;

    public static Item GUN_SMITH_TABLE;
    public static Item WORKBENCH_111;
    public static Item WORKBENCH_211;
    public static Item WORKBENCH_121;

    public static Item TARGET;
    public static Item STATUE;
    public static Item GUN_PEGBOARD;
    public static Item GLOW_GUN_PEGBOARD;
    public static Item AMMO_STORAGE;
    public static Item AMMO_STORAGE_GOLD;
    public static Item AMMO_STORAGE_DIAMOND;
    public static Item GUN_DISPLAY_CASE;
    public static Item AMMO_BOX;
    public static Item TARGET_MINECART;

    public static void register() {
        if (MODERN_KINETIC_GUN != null) {
            return;
        }
        // 上游 DeferredRegister 里 TABS 在 ITEMS 之前；1.7.10 标签页只需先建立对象（内容懒加载）
        ModCreativeTabs.register();

        MODERN_KINETIC_GUN = registerItem(
            "modern_kinetic_gun",
            new ModernKineticGunItem(),
            ModCreativeTabs.GUN_RIFLE_TAB);
        AMMO = registerItem("ammo", new AmmoItem(), ModCreativeTabs.AMMO_TAB);
        ATTACHMENT = registerItem("attachment", new AttachmentItem(), ModCreativeTabs.ATTACHMENT_SCOPE_TAB);
        AMMO_BOX = registerItem("ammo_box", new AmmoBoxItem(), ModCreativeTabs.OTHER_TAB);
        TARGET_MINECART = registerItem("target_minecart", new TargetMinecartItem(), ModCreativeTabs.OTHER_TAB);

        // 方块物品：ModBlocks.register() 已注册（含自定义 ItemBlock 子类）
        GUN_SMITH_TABLE = Item.getItemFromBlock(ModBlocks.GUN_SMITH_TABLE);
        WORKBENCH_111 = Item.getItemFromBlock(ModBlocks.WORKBENCH_111);
        WORKBENCH_211 = Item.getItemFromBlock(ModBlocks.WORKBENCH_211);
        WORKBENCH_121 = Item.getItemFromBlock(ModBlocks.WORKBENCH_121);
        TARGET = Item.getItemFromBlock(ModBlocks.TARGET);
        STATUE = Item.getItemFromBlock(ModBlocks.STATUE);
        GUN_PEGBOARD = Item.getItemFromBlock(ModBlocks.GUN_PEGBOARD);
        GLOW_GUN_PEGBOARD = Item.getItemFromBlock(ModBlocks.GLOW_GUN_PEGBOARD);
        AMMO_STORAGE = Item.getItemFromBlock(ModBlocks.AMMO_STORAGE);
        AMMO_STORAGE_GOLD = Item.getItemFromBlock(ModBlocks.AMMO_STORAGE_GOLD);
        AMMO_STORAGE_DIAMOND = Item.getItemFromBlock(ModBlocks.AMMO_STORAGE_DIAMOND);
        GUN_DISPLAY_CASE = Item.getItemFromBlock(ModBlocks.GUN_DISPLAY_CASE);

        // 上游 RegisterEvent：把 "modern_kinetic" 逻辑枪注册进 GunItemManager
        if (MODERN_KINETIC_GUN instanceof AbstractGunItem abstractGunItem) {
            GunItemManager.registerGunItem(ModernKineticGunItem.TYPE_NAME, abstractGunItem);
        }
    }

    private static Item registerItem(String name, Item item, CreativeTabs tab) {
        item.setUnlocalizedName("tacz." + name);
        item.setCreativeTab(tab);
        GameRegistry.registerItem(item, name);
        return item;
    }
}
