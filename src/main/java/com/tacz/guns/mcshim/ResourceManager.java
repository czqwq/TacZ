package com.tacz.guns.mcshim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.util.ResourceLocation;

/**
 * 对应 1.20.1 的 {@code net.minecraft.server.packs.resources.ResourceManager}（TACZ 实际用到的子集）。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 只有客户端专用的 {@code net.minecraft.client.resources.IResourceManager}
 * （{@code getResource/getAllResources/getResourceDomains}），而且它<b>只能看到资源包里的
 * {@code assets/**}</b>，看不到 {@code data/**}（1.20 的 datapack 概念），
 * 也不认识 TACZ 自己的「枪包目录/zip」。所以本类不复用 {@code IResourceManager}，
 * 而是由 {@link PackSource} 注入数据源后<b>自建索引</b>。
 *
 * <h2>索引与 id 推导规则（与 {@code FileToIdConverter} 一致）</h2>
 * <p>
 * 对 {@link PackSource#listFiles(String)} 返回的每个路径，按下面的规则算出 {@link ResourceLocation}：
 * 
 * <pre>
 *   data/tacz/index/guns/ak47.json
 *     └┬─┘ └┬─┘ └─────┬──────┘└──┬─┘
 *      │    │         │          └── 去扩展名后的 path 部分
 *      │    │         └───────────── 保留（不含首尾 '/'）
 *      │    └─────────────────────── namespace
 *      └──────────────────────────── 丢弃根段（data / assets / 其它）
 *
 *   ⇒ id = tacz:index/guns/ak47
 * </pre>
 * 
 * 也就是说：<b>第 0 段永远是「根」（数据包/资源包类型），第 1 段是 namespace，其余段去掉扩展名后是 path</b>。
 * 于是 {@code FileToIdConverter("index/guns", ".json").fileToId(new ResourceLocation("tacz","index/guns/ak47"))}
 * 得到 {@code tacz:ak47} —— 与上游「{@code data/tacz/index/guns/ak47.json → tacz:ak47}」一致。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code ResourceManager} 是接口，由 {@code MultiPackResourceManager} 实现，
 * 支持 {@code PackType}（SERVER_DATA / CLIENT_RESOURCES）区分、pack 优先级、mcmeta。
 * 本类是<b>具体类</b>，不区分 {@code PackType}（根段直接丢弃），不做 mcmeta。</li>
 * <li>没有重载/卸载的动态重扫协议：数据源变化后调 {@link #invalidate()} 再访问即可重建索引。</li>
 * <li>{@link #getResourceStack(ResourceLocation)} 的顺序 = 注入的 {@link PackSource} 列表顺序
 * （先注入的先入栈，即「优先级高」的放前面由调用方决定）。</li>
 * </ul>
 */
public class ResourceManager {

    // ================================================================================================
    // 数据源注入
    // ================================================================================================

    private static volatile Supplier<List<PackSource>> packSourceSupplier = Collections::emptyList;

    /**
     * 注入数据源（由 L1 的 {@code GunPackLoader} 在扫描完成后调用）。
     *
     * <p>
     * 用 {@code Supplier} 而不是固定 {@code List}，因为重扫会换新列表。
     */
    public static void setPackSources(Supplier<List<PackSource>> supplier) {
        packSourceSupplier = supplier == null ? Collections::emptyList : supplier;
    }

    /** 当前数据源列表的只读快照。 */
    public static List<PackSource> currentPackSources() {
        List<PackSource> list = packSourceSupplier.get();
        return list == null ? Collections.emptyList() : list;
    }

    // ================================================================================================
    // 实例
    // ================================================================================================

    private final Map<ResourceLocation, List<Resource>> index = new LinkedHashMap<>();
    private boolean built;

    public ResourceManager() {}

    /** 让索引在下次访问时重建（数据源变化后调用）。 */
    public void invalidate() {
        synchronized (this) {
            index.clear();
            built = false;
        }
    }

    private Map<ResourceLocation, List<Resource>> index() {
        synchronized (this) {
            if (!built) {
                for (PackSource source : currentPackSources()) {
                    for (String path : source.listFiles("")) {
                        ResourceLocation id = idFromPath(path);
                        if (id == null) {
                            continue;
                        }
                        index.computeIfAbsent(id, ignored -> new ArrayList<>())
                            .add(new Resource(id, source, path));
                    }
                }
                built = true;
            }
            return index;
        }
    }

    /**
     * 路径 → {@link ResourceLocation}（规则见类 javadoc）。
     *
     * @return 段数 &lt; 2 或为空路径时返回 {@code null}
     */
    public static ResourceLocation idFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String[] segments = normalized.split("/");
        if (segments.length < 2) {
            return null;
        }
        String namespace = segments[1];
        StringBuilder builder = new StringBuilder();
        for (int i = 2; i < segments.length; i++) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(segments[i]);
        }
        String resourcePath = stripExtension(builder.toString());
        if (namespace.isEmpty() || resourcePath.isEmpty()) {
            return null;
        }
        return new ResourceLocation(namespace, resourcePath);
    }

    private static String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return dot > slash ? path.substring(0, dot) : path;
    }

    /** 对应 1.20 {@code ResourceManager#getResource(ResourceLocation)}：取优先级最高的那一份。 */
    public Optional<Resource> getResource(ResourceLocation id) {
        List<Resource> stack = index().get(id);
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(stack.get(0));
    }

    /** 对应 1.20 {@code ResourceManager#getResourceStack(ResourceLocation)}：按注入顺序返回全部同名资源。 */
    public List<Resource> getResourceStack(ResourceLocation id) {
        List<Resource> stack = index().get(id);
        return stack == null ? Collections.emptyList() : Collections.unmodifiableList(stack);
    }

    /**
     * 对应 1.20 {@code ResourceManager#listResources(String path, Predicate)}。
     *
     * @param path   资源 id 的 <b>path</b> 前缀，<b>不带</b> namespace，例如 {@code "index/guns"}
     * @param filter 额外的 id 过滤（可为 {@code null}）
     * @return 有序 map（按索引建立顺序，即注入顺序 + 路径顺序）
     */
    public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
        String prefix = path == null ? "" : path;
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        Map<ResourceLocation, Resource> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> entry : index().entrySet()) {
            ResourceLocation id = entry.getKey();
            String resourcePath = id.getResourcePath();
            if (!prefix.isEmpty() && !resourcePath.equals(prefix) && !resourcePath.startsWith(prefix + "/")) {
                continue;
            }
            if (filter != null && !filter.test(id)) {
                continue;
            }
            List<Resource> stack = entry.getValue();
            if (!stack.isEmpty()) {
                result.put(id, stack.get(0));
            }
        }
        return result;
    }

    /** 对应 1.20 {@code ResourceManager#listResources(String, String, Predicate)}（{@code packType} 被忽略）。 */
    public Map<ResourceLocation, Resource> listResources(String packType, String path,
        Predicate<ResourceLocation> filter) {
        return listResources(path, filter);
    }

    /** 对应 1.20 {@code ResourceManager#listResourceStacks(String, Predicate)}。 */
    public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
        Map<ResourceLocation, List<Resource>> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : listResources(path, filter).entrySet()) {
            result.put(entry.getKey(), getResourceStack(entry.getKey()));
        }
        return result;
    }

    /** 对应 1.20 {@code ResourceManager#getNamespaces()}：所有资源 id 的 namespace 集合。 */
    public Set<String> getNamespaces() {
        Set<String> namespaces = new LinkedHashSet<>();
        for (ResourceLocation id : index().keySet()) {
            namespaces.add(id.getResourceDomain());
        }
        return namespaces;
    }

    /** 索引里资源 id 的总数（调试用）。 */
    public int size() {
        return index().size();
    }
}
