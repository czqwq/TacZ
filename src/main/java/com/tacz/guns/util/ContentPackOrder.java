package com.tacz.guns.util;

import java.util.Comparator;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;

/**
 * 内容包（枪包）的命名空间排序规则：{@code tacz} 自己永远排最前，其余按字典序。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}（1.7.10 同名类）。</li>
 * <li>1.20 的 {@code ResourceLocation.getNamespace()} 在 1.7.10 叫
 * {@link ResourceLocation#getResourceDomain()}，其余逻辑逐行一致。</li>
 * <li>这是 {@code api/**} 的编译期契约类，<b>公开类名与 {@link #byNamespace()} 签名保持不变</b>。</li>
 * </ul>
 */
public final class ContentPackOrder {

    public static final Comparator<String> NAMESPACE_ORDER = Comparator
        .comparingInt((String namespace) -> GunMod.MOD_ID.equals(namespace) ? 0 : 1)
        .thenComparing(Comparator.naturalOrder());

    private ContentPackOrder() {}

    public static <T> Comparator<Map.Entry<ResourceLocation, T>> byNamespace() {
        return Comparator.comparing(
            entry -> entry.getKey()
                .getResourceDomain(),
            NAMESPACE_ORDER);
    }
}
