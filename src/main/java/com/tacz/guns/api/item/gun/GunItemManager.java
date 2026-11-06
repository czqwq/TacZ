package com.tacz.guns.api.item.gun;

import java.util.Collection;
import java.util.Map;

import com.google.common.collect.Maps;

/**
 * 枪械物品实例管理器。
 * <p>
 * 1.7.10 移植说明：1.20.1 上游用 {@code DeferredRegister} + {@code RegistryObject}，
 * 1.7.10 没有这两者（注册走 {@code GameRegistry.registerItem}，注册结果是 Item 实例本身）。
 * 因此这里直接存取 {@link AbstractGunItem} 实例，方法语义与上游一致：
 * <ul>
 * <li>{@code registerGunItem(String, RegistryObject)} → {@link #registerGunItem(String, AbstractGunItem)}；</li>
 * <li>{@code getGunItemRegistryObject(String).get()} → {@link #getGunItem(String)}（已不再需要 {@code .get()}）；</li>
 * <li>{@code getAllGunItems()} 返回 Item 实例集合（上游返回 RegistryObject 集合，调用方无需再 {@code .get()}）。</li>
 * </ul>
 */
public class GunItemManager {

    private static final Map<String, AbstractGunItem> GUN_ITEM_MAP = Maps.newHashMap();

    /**
     * 建议在 {@code GameRegistry.registerItem} 之后调用本方法登记枪械变种。
     */
    public static void registerGunItem(String name, AbstractGunItem gunItem) {
        GUN_ITEM_MAP.put(name, gunItem);
    }

    public static AbstractGunItem getGunItem(String key) {
        return GUN_ITEM_MAP.get(key);
    }

    public static Collection<? extends AbstractGunItem> getAllGunItems() {
        return GUN_ITEM_MAP.values();
    }
}
