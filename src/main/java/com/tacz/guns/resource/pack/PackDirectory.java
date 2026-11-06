package com.tacz.guns.resource.pack;

import org.jetbrains.annotations.Nullable;

/**
 * 「包内目录 + 扩展名」二元组，等价于上游的 {@code net.minecraft.resources.FileToIdConverter}。
 *
 * <p>
 * 上游 {@code FileToIdConverter} 的职责只有两件：记住 {@code prefix} 与 {@code extension}，
 * 然后用 {@code listMatchingResources(ResourceManager)} / {@code fileToId(ResourceLocation)} 做
 * 「列举 → 推导 id」。1.7.10 没有 {@code ResourceManager}，前一半由
 * {@link GunPackResources} 在 {@link IGunPackSource} 上完成，后一半（id 推导）也已在
 * {@link GunPackResources} 内实现，因此这里只需要一个纯数据载体。
 *
 * <p>
 * 之所以不直接用 {@code mcshim.FileToIdConverter}：那份 shim 的核心方法是
 * {@code listMatchingResources(ResourceManager)}，而 1.7.10 的 {@code ResourceManager} 背板
 * 依然是本包的 {@link IGunPackSource}（枪包不在原版资源包系统里），
 * 绕一圈没有收益，反而多一层必需的适配。
 */
public record PackDirectory(String prefix, String extension) {

    public PackDirectory {
        if (prefix == null || prefix.isEmpty()) {
            prefix = "";
        }
        if (extension == null || extension.isEmpty()) {
            extension = ".json";
        }
    }

    /** 对应 {@code FileToIdConverter.json(prefix)}。 */
    public static PackDirectory json(String prefix) {
        return new PackDirectory(prefix, ".json");
    }

    public static PackDirectory of(String prefix, String extension) {
        return new PackDirectory(prefix, extension);
    }

    /**
     * 推导 id 的 path 部分（相对 {@link #prefix()}、去掉 {@link #extension()}）。
     *
     * <p>
     * 与 {@code FileToIdConverter#fileToId} 等价；{@code path} 是「相对根目录」的完整路径，
     * 例如 {@code "display/guns/ak47.json"}。不匹配返回 {@code null}。
     */
    public @Nullable String toIdPath(String path) {
        String fullPrefix = prefix.isEmpty() ? "" : prefix + "/";
        if (!path.startsWith(fullPrefix) || !path.endsWith(extension)) {
            return null;
        }
        String idPath = path.substring(fullPrefix.length(), path.length() - extension.length());
        return idPath.isEmpty() ? null : idPath;
    }
}
