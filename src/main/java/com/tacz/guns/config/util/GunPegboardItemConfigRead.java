package com.tacz.guns.config.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.sync.SyncConfig;

/**
 * 把 {@code gun_pegboard.*} 配置解析成物品白名单/黑名单，并判断某物品能否放到枪架/展示柜上。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/config/util/GunPegboardItemConfigRead.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>上游的 {@code TagKey<Item>}（{@code tacz:gun_pegboard/whitelist}）在 1.7.10 无对应，语义丢失，只保留配置列表。</li>
 * <li>物品 id 用 {@link Item#itemRegistry} 查询（上游 {@code ForgeRegistries.ITEMS}）。</li>
 * <li>1.7.10 的 {@link ItemStack} 可能为 {@code null}（{@code ItemStack.EMPTY} 在本工程统一改为 null）。</li>
 * </ul>
 */
public final class GunPegboardItemConfigRead {

    private static final List<ResourceLocation> WHITELIST = new ArrayList<>();
    private static final List<ResourceLocation> BLACKLIST = new ArrayList<>();

    private GunPegboardItemConfigRead() {}

    public static void init() {
        WHITELIST.clear();
        BLACKLIST.clear();
        handleConfigData(SyncConfig.GUN_PEGBOARD_WHITELIST_ITEMS.get(), WHITELIST);
        handleConfigData(SyncConfig.GUN_PEGBOARD_BLACKLIST_ITEMS.get(), BLACKLIST);
    }

    public static boolean canPlace(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        // 1.7.10 的 RegistryNamespaced#getNameForObject 返回 String（1.20 返回 ResourceLocation）
        String itemName = Item.itemRegistry.getNameForObject(stack.getItem());
        if (itemName == null || itemName.isEmpty()) {
            return false;
        }
        ResourceLocation itemId;
        try {
            itemId = new ResourceLocation(itemName);
        } catch (RuntimeException e) {
            return false;
        }
        if (BLACKLIST.contains(itemId)) {
            return false;
        }
        if (WHITELIST.contains(itemId)) {
            return true;
        }
        Item item = stack.getItem();
        return item instanceof IGun || item instanceof IAttachment || item instanceof IAmmo || item instanceof IAmmoBox;
    }

    private static void handleConfigData(List<String> configData, List<ResourceLocation> storeList) {
        if (configData == null) {
            return;
        }
        for (String data : configData) {
            if (data == null || data.trim()
                .isEmpty()) {
                continue;
            }
            try {
                storeList.add(new ResourceLocation(data.trim()));
            } catch (RuntimeException e) {
                GunMod.LOGGER.warn("Ignoring invalid item id '{}' in gun_pegboard config", data);
            }
        }
    }
}
