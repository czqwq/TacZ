package com.tacz.guns.api;

import com.google.common.reflect.TypeToken;

public record GunProperty<T> (String name, Class<T> type) {

    public static <T> GunProperty<T> of(String name, Class<T> type) {
        // 上游用 net.minecraft.Util#make（1.20.1）注册后返回；1.7.10 的 Util 没有此方法，
        // 这里按同样语义内联：构造 → 注册进 ALL → 返回。
        GunProperty<T> property = new GunProperty<>(name, type);
        GunProperties.ALL.put(name, property);
        return property;
    }

    @SuppressWarnings("unchecked")
    public static <T> GunProperty<T> of(String name, TypeToken<T> type) {
        GunProperty<T> property = new GunProperty<>(name, (Class<T>) type.getRawType());
        GunProperties.ALL.put(name, property);
        return property;
    }
}
