package com.tacz.guns.api.util;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

/**
 * 1.7.10 移植辅助：{@code ResourceLocation.tryParse(String)} 的等价实现。
 * <p>
 * 1.20.1 的 {@code ResourceLocation.tryParse} 在字符串非法时返回 null；
 * 1.7.10 的 {@link ResourceLocation#ResourceLocation(String)} 会抛
 * {@link IllegalArgumentException}（无法解析出命名空间时）。这两个语义不同，全仓需要统一，
 * 因此集中在 api 提供本方法（api 允许被其它包依赖，且不引入 1.20 import）。
 */
public final class ResourceLocationHelper {

    private ResourceLocationHelper() {}

    /**
     * 等价于 1.20 的 {@code ResourceLocation.tryParse(String)}：非法输入返回 null。
     */
    @Nullable
    public static ResourceLocation tryParse(@Nullable String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }
        try {
            ResourceLocation rl = new ResourceLocation(location);
            // 1.7.10 的构造器对 "tacz:" 之类的输入可能不抛异常但产生空路径，这里按 tryParse 语义判空。
            return (rl.getResourcePath() == null || rl.getResourcePath()
                .isEmpty()) ? null : rl;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 等价于 1.20 的 {@code ResourceLocation.parse(String)}：非法输入回退到 fallback。
     */
    public static ResourceLocation parseOrDefault(@Nullable String location, ResourceLocation fallback) {
        ResourceLocation rl = tryParse(location);
        return rl == null ? fallback : rl;
    }

    /**
     * 把 1.7.10 的实体注册名（{@link net.minecraft.entity.EntityList#getEntityString}）解析成 {@link ResourceLocation}。
     * <p>
     * 依据：1.7.10 原版实体的注册名是类名驼峰（{@code "ItemFrame"}，见
     * {@code build/rfg/minecraft-src/java/net/minecraft/entity/EntityList.java:258-261}），
     * FML 注册的模组实体名是 {@code "<modid>.<entityName>"}（{@code EntityRegistry.java:164}
     * {@code String.format("%s.%s", mod.getModId(), entityName)}），两者都不是 1.20 的 {@code "modid:path"}。
     * <ul>
     * <li>null/空 → null；</li>
     * <li>含 {@code :} → 直接按 {@link #tryParse(String)} 解析（原样，不做大小写变换）；</li>
     * <li>含 {@code .} 且无 {@code :} → 把<b>最后一个</b> {@code .} 换成 {@code :}；</li>
     * <li>其他 → {@code "minecraft:" + 小写化}（原版类名 → 1.20 id 的多词名差异如
     * {@code "ItemFrame"→"item_frame"} 由调用方补充，本方法不做映射）。</li>
     * </ul>
     * 幂等：对已带命名空间的结果（含本方法返回值）再次调用，结果不变。
     * 注意 FML 分支不做小写化：需要 1.20 风格全小写 id 的调用方应先自行
     * {@code name.toLowerCase(Locale.ROOT)} 再传入。
     */
    @Nullable
    public static ResourceLocation tryParseEntityName(@Nullable String entityName) {
        if (entityName == null || entityName.isEmpty()) {
            return null;
        }
        String name = entityName.trim();
        if (name.isEmpty()) {
            return null;
        }
        if (!name.contains(":")) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                name = name.substring(0, dot) + ':' + name.substring(dot + 1);
            } else {
                name = "minecraft:" + name.toLowerCase(Locale.ROOT);
            }
        }
        return tryParse(name);
    }

    /**
     * 兼容别名：语义与 {@link #tryParseEntityName(String)} 完全一致（供尚未统一命名的调用方使用）。
     */
    @Nullable
    public static ResourceLocation tryParseNormalized(@Nullable String entityName) {
        return tryParseEntityName(entityName);
    }
}
