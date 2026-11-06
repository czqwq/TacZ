package com.tacz.guns.mcshim;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.util.ResourceLocation;

/**
 * 对应 1.20.1 的 {@code net.minecraft.resources.FileToIdConverter}。
 *
 * <h2>1.20 的语义（本类逐行对齐）</h2>
 * 1.20 里它把「文件路径」和「资源 id」互相转换，规则是：
 * 
 * <pre>
 *   idToFile(ResourceLocation(id)) = ResourceLocation(id.getResourceDomain(), prefix + "/" + id.getPath() + extension)
 *   fileToId(ResourceLocation(f))  = ResourceLocation(f.getNamespace(), 从 f.getPath() 去掉 prefix + "/" 前缀与 extension 后缀)
 * </pre>
 * 
 * 例：{@code new FileToIdConverter("index/guns", ".json")}
 * <ul>
 * <li>{@code idToFile(tacz:ak47)} → {@code tacz:index/guns/ak47.json}</li>
 * <li>{@code fileToId(tacz:index/guns/ak47.json)} → {@code tacz:ak47}</li>
 * </ul>
 * 而 {@code ResourceManager} 里的文件路径是 {@code data/tacz/index/guns/ak47.json}，
 * 去掉根段 {@code data} 后正好对应 {@code tacz:index/guns/ak47.json}（见
 * {@link ResourceManager#idFromPath(String)}），所以
 * {@code listMatchingResources} 能直接工作。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 纯字符串运算，无平台依赖。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code FileToIdConverter} 是 {@code record}；本类是普通类（<b>不用</b> {@code record}，
 * 避免 jvmDowngrader 在 record 上的边界情况）。</li>
 * <li>{@code listMatchingResources(ResourceManager)} 的返回类型从 1.20 的
 * {@code Map<ResourceLocation, Resource>} 保持一致。</li>
 * <li>{@code listMatchingResourceStacks} 未移植（上游未用到）。</li>
 * <li>{@code jsonResources(String dir)} 是 1.7.10 侧的便利静态方法，等价
 * {@code new FileToIdConverter(dir, ".json")}。</li>
 * </ul>
 */
public class FileToIdConverter {

    private final String prefix;
    private final String extension;

    /** 对应 1.20 {@code new FileToIdConverter(String prefix, String extension)}。 */
    public FileToIdConverter(String prefix, String extension) {
        this.prefix = normalizePrefix(prefix);
        this.extension = extension.startsWith(".") ? extension : (extension.isEmpty() ? "" : "." + extension);
    }

    /** 对应 1.20 {@code FileToIdConverter#json(String prefix)}：扩展名 {@code ".json"}。 */
    public static FileToIdConverter json(String prefix) {
        return new FileToIdConverter(prefix, ".json");
    }

    /** 1.7.10 便利方法：等价 {@code json(dir)}。 */
    public static FileToIdConverter jsonResources(String dir) {
        return json(dir);
    }

    /** 对应 1.20 {@code FileToIdConverter#registry(String registry)}（同 {@code json}）。 */
    public static FileToIdConverter registry(String registry) {
        return json(registry);
    }

    private static String normalizePrefix(String prefix) {
        String value = prefix == null ? ""
            : prefix.trim()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /** 前缀目录，例如 {@code "index/guns"}。 */
    public String prefix() {
        return prefix;
    }

    /** 扩展名，例如 {@code ".json"}。 */
    public String extension() {
        return extension;
    }

    /** 对应 1.20 {@code FileToIdConverter#idToFile(ResourceLocation)}。 */
    public ResourceLocation idToFile(ResourceLocation id) {
        String path = prefix.isEmpty() ? id.getResourcePath() : prefix + "/" + id.getResourcePath();
        return new ResourceLocation(id.getResourceDomain(), path + extension);
    }

    /**
     * 对应 1.20 {@code FileToIdConverter#fileToId(ResourceLocation)}。
     *
     * <p>
     * 若传入路径不以本转换器的 prefix 开头，1.20 会原样返回（只去扩展名）；本类同样。
     */
    public ResourceLocation fileToId(ResourceLocation file) {
        String path = file.getResourcePath();
        if (!prefix.isEmpty() && path.startsWith(prefix + "/")) {
            path = path.substring(prefix.length() + 1);
        }
        if (!extension.isEmpty() && path.endsWith(extension)) {
            path = path.substring(0, path.length() - extension.length());
        }
        return new ResourceLocation(file.getResourceDomain(), path);
    }

    /** 对应 1.20 {@code FileToIdConverter#resourcePath(...)} 的 1.7.10 形态：拼出「带前缀的相对路径」。 */
    public String resourcePath(ResourceLocation file) {
        return file.getResourcePath();
    }

    /**
     * 对应 1.20 {@code FileToIdConverter#listMatchingResources(ResourceManager)}：
     * 列出 {@code prefix} 目录下所有资源，并把 key 转成「去掉 prefix 与扩展名」的 id。
     */
    public Map<ResourceLocation, Resource> listMatchingResources(ResourceManager resourceManager) {
        return listMatchingResources(resourceManager, id -> true);
    }

    /** 带过滤条件的 {@link #listMatchingResources(ResourceManager)}。 */
    public Map<ResourceLocation, Resource> listMatchingResources(ResourceManager resourceManager,
        Predicate<ResourceLocation> filter) {
        Map<ResourceLocation, Resource> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources(prefix, filter)
            .entrySet()) {
            result.put(fileToId(entry.getKey()), entry.getValue());
        }
        return result;
    }

    @Override
    public String toString() {
        return "FileToIdConverter[" + prefix + ", " + extension + "]";
    }
}
