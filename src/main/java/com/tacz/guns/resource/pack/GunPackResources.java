package com.tacz.guns.resource.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tacz.guns.GunMod;

/**
 * 在 1.7.10 上复刻上游 {@code com.tacz.guns.util.ResourceScanner}
 * （{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/util/ResourceScanner.java}）的「按目录扫描 json」能力。
 *
 * <p>
 * 上游靠 {@code net.minecraft.server.packs.resources.ResourceManager} +
 * {@code net.minecraft.resources.FileToIdConverter} 实现，1.7.10 两者都不存在，
 * 因此这里改用 {@link IGunPackSource} 自行遍历。
 *
 * <p>
 * <b>与上游的一致性</b>：
 * <ul>
 * <li>id 推导规则与 {@code FileToIdConverter#fileToId} 相同：去掉 {@code <root>/<namespace>/<dir>/} 前缀与扩展名，
 * 剩下的相对路径就是 id 的 path。例如 {@code data/tacz/index/guns/ak47.json} 配 {@code dir = "index/guns"}
 * 得到 {@code tacz:ak47}；{@code data/tacz/tacz_tags/attachments/allow_attachments/ak47.json} 配
 * {@code dir = "tacz_tags/attachments"} 得到 {@code tacz:allow_attachments/ak47}。</li>
 * <li>解析走 lenient 模式，允许 json 注释（上游 {@code GsonHelper.fromJson(..., true)} 的语义）。
 * gson 2.2.4 的 {@code JsonParser#parse(Reader)} 内部就会把 reader 切成 lenient，
 * 所以这里直接用 {@code new JsonParser().parse(reader)}，不引入额外 shim。</li>
 * </ul>
 *
 * <p>
 * <b>与上游的差异</b>：上游按资源包优先级从高到低只取一份（{@code listMatchingResources}）；
 * 1.7.10 这边枪包之间没有原版优先级模型，因此<b>按 {@link IGunPackSource} 列表顺序扫描，后扫到的覆盖先扫到的</b>
 * （{@link com.tacz.guns.resource.GunPackLoader} 按包名排序，保证结果可复现）。
 */
public final class GunPackResources {

    /** 服务端数据根目录，对应上游 {@code PackType.SERVER_DATA} 只能看到的 {@code data/}。 */
    public static final String DATA_ROOT = "data";
    /** 客户端资源根目录，对应上游 {@code PackType.CLIENT_RESOURCES} 只能看到的 {@code assets/}。 */
    public static final String ASSETS_ROOT = "assets";

    private GunPackResources() {}

    /** 扫描 {@code data/<namespace>/<dir>/**}{@code .json}，同 id 后扫到的覆盖先扫到的。 */
    public static Map<ResourceLocation, JsonElement> scanDataDirectory(List<IGunPackSource> sources, String dir,
        Gson gson) {
        return scanDirectory(sources, DATA_ROOT, dir, ".json", gson);
    }

    /** 扫描 {@code assets/<namespace>/<dir>/**}{@code .json}，同 id 后扫到的覆盖先扫到的。 */
    public static Map<ResourceLocation, JsonElement> scanAssetsDirectory(List<IGunPackSource> sources, String dir,
        Gson gson) {
        return scanDirectory(sources, ASSETS_ROOT, dir, ".json", gson);
    }

    /**
     * 扫描指定根目录下的所有 {@code extension} 文件。
     *
     * @param root      {@link #DATA_ROOT} 或 {@link #ASSETS_ROOT}
     * @param dir       相对 namespace 的目录（如 {@code "index/guns"}）
     * @param extension 扩展名，含点（如 {@code ".json"}、{@code ".lua"}）
     */
    public static Map<ResourceLocation, JsonElement> scanDirectory(List<IGunPackSource> sources, String root,
        String dir, String extension, Gson gson) {
        Map<ResourceLocation, JsonElement> output = new LinkedHashMap<>();
        for (IGunPackSource source : sources) {
            for (String path : listFiles(source, root, dir, extension)) {
                ResourceLocation id = fileToId(path, root, dir, extension);
                if (id == null) {
                    continue;
                }
                JsonElement element = readElement(source, path, gson);
                if (element != null) {
                    output.put(id, element);
                }
            }
        }
        return output;
    }

    /**
     * 扫描指定根目录下的所有 {@code extension} 文件，<b>保留每个包各一份</b>。
     *
     * <p>
     * 对应上游 {@code ResourceScanner#scanDirectoryAll}（{@code listMatchingResourceStacks}）：
     * 标签、配方过滤、战利品注入这几类数据需要把多个枪包的同名文件合并，而不是覆盖。
     */
    public static Map<ResourceLocation, List<JsonElement>> scanDirectoryAll(List<IGunPackSource> sources, String root,
        String dir, String extension, Gson gson) {
        Map<ResourceLocation, List<JsonElement>> output = new LinkedHashMap<>();
        for (IGunPackSource source : sources) {
            for (String path : listFiles(source, root, dir, extension)) {
                ResourceLocation id = fileToId(path, root, dir, extension);
                if (id == null) {
                    continue;
                }
                JsonElement element = readElement(source, path, gson);
                if (element != null) {
                    output.computeIfAbsent(id, key -> new ArrayList<>())
                        .add(element);
                }
            }
        }
        return output;
    }

    /**
     * 只登记 id 与包内路径的对应关系，不读内容。
     *
     * <p>
     * 对应上游 {@code ResourceScanner#scanDirectoryResources}，供
     * {@link com.tacz.guns.resource.manager.LazyJsonDataManager} 做懒加载。
     */
    public static Map<ResourceLocation, PackedResource> scanDirectoryResources(List<IGunPackSource> sources,
        String root, String dir, String extension) {
        Map<ResourceLocation, PackedResource> output = new LinkedHashMap<>();
        for (IGunPackSource source : sources) {
            for (String path : listFiles(source, root, dir, extension)) {
                ResourceLocation id = fileToId(path, root, dir, extension);
                if (id != null) {
                    output.put(id, new PackedResource(source, path));
                }
            }
        }
        return output;
    }

    /** 解析一个 json 文件；读失败或语法错误返回 {@code null} 并记日志（与上游一致：单文件失败不影响整体加载）。 */
    public static @Nullable JsonElement readElement(IGunPackSource source, String path, Gson gson) {
        try (Reader reader = openReader(source, path)) {
            if (reader == null) {
                return null;
            }
            return new JsonParser().parse(reader);
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            GunMod.LOGGER.error("Couldn't parse data file {} from {}", path, source.getName(), exception);
            return null;
        }
    }

    /** 打开包内文件的 UTF-8 reader；不存在返回 {@code null}。 */
    public static @Nullable Reader openReader(IGunPackSource source, String path) {
        InputStream stream = source.open(path);
        if (stream == null) {
            return null;
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    /** 在给定包列表里按优先级（后覆盖前）找到 {@code path} 对应的第一个包并打开。 */
    public static @Nullable Reader openReader(List<IGunPackSource> sources, String path) {
        for (int i = sources.size() - 1; i >= 0; i--) {
            Reader reader = openReader(sources.get(i), path);
            if (reader != null) {
                return reader;
            }
        }
        return null;
    }

    private static List<String> listFiles(IGunPackSource source, String root, String dir, String extension) {
        String prefix = root + "/";
        // IGunPackSource 的 prefix 只到 namespace 之上，这里逐 namespace 展开
        List<String> namespaces = listNamespaces(source, root);
        if (namespaces.isEmpty()) {
            return List.of();
        }
        List<String> output = new ArrayList<>();
        for (String namespace : namespaces) {
            String base = dir.isEmpty() ? prefix + namespace : prefix + namespace + "/" + dir;
            for (String path : source.listFiles(base)) {
                if (path.endsWith(extension)) {
                    output.add(path);
                }
            }
        }
        return output;
    }

    private static List<String> listNamespaces(IGunPackSource source, String root) {
        List<String> namespaces = new ArrayList<>();
        String prefix = root + "/";
        for (String path : source.listFiles(prefix)) {
            if (!path.startsWith(prefix)) {
                continue;
            }
            int slash = path.indexOf('/', prefix.length());
            if (slash < 0) {
                continue;
            }
            String namespace = path.substring(prefix.length(), slash);
            if (!namespaces.contains(namespace)) {
                namespaces.add(namespace);
            }
        }
        return namespaces;
    }

    private static @Nullable ResourceLocation fileToId(String path, String root, String dir, String extension) {
        String prefix = root + "/";
        if (!path.startsWith(prefix) || !path.endsWith(extension)) {
            return null;
        }
        String rest = path.substring(prefix.length(), path.length() - extension.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String namespace = rest.substring(0, slash);
        String idPath = rest.substring(slash + 1);
        if (!dir.isEmpty()) {
            String dirPrefix = dir + "/";
            if (!idPath.startsWith(dirPrefix)) {
                return null;
            }
            idPath = idPath.substring(dirPrefix.length());
        }
        if (idPath.isEmpty()) {
            return null;
        }
        return new ResourceLocation(namespace, idPath);
    }

    /** 一个尚未读取的包内资源：所属枪包 + 包内完整相对路径。 */
    public record PackedResource(IGunPackSource source, String path) {}
}
