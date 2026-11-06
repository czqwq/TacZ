package com.tacz.guns.util;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.tuple.Pair;

import com.tacz.guns.api.util.ResourceLocationHelper;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.ICommonResourceProvider;

/**
 * 「枪 → 允许的配件」与「配件标签 → 配件」两张表的展开与缓存。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}（1.7.10 同名类）；
 * {@code ResourceLocation.tryParse} 不存在，改用 {@link ResourceLocationHelper#tryParse}（全仓统一实现）。</li>
 * <li>上游的 {@code record Cache(...)} 改成<b>普通嵌套类</b>（本工程避免 {@code record}），
 * 但保留 {@code allowAttachmentCache()} / {@code tagMatchCache()} 两个同名访问器，调用点不用改。</li>
 * <li>{@code Set.of()} → {@link Collections#emptySet()}；
 * {@code Set.copyOf(attachments)} → {@code Collections.unmodifiableSet(new HashSet<>(...))}
 * （{@code Set.copyOf} 是 Java 10+，虽然 jvmDowngrader 能降级，这里改成等价写法更稳）。</li>
 * <li>{@code var provider} → 显式 {@link ICommonResourceProvider}。</li>
 * <li><b>1.7.10 新增判空</b>：1.20 的 {@code CommonAssetsManager.get()} 永不为 null，
 * 但 1.7.10 移植版在「客户端连多人服务器、还没收到同步数据」时返回 {@code null}
 * （见 {@code CommonAssetsManager} 的注释）。此处判空后返回空集，语义等价于「尚无任何标签数据」。</li>
 * <li>{@code Pair} 仍用 {@code org.apache.commons.lang3.tuple.Pair}（1.7.10 自带 commons-lang3），
 * 访问器 {@code getLeft()/getRight()} 与上游一致。</li>
 * <li>这是 {@code api/**} 的编译期契约类（{@code AbstractGunItem} / {@code CommonSlotAdapterIndex} 等直接调用），
 * <b>公开类名与方法签名保持不变</b>。</li>
 * </ul>
 */
public final class AllowAttachmentTagMatcher {

    private static final String TAG_PREFIX = "#";
    private static volatile Cache cache = new Cache();

    /**
     * 1.20 的 {@code record Cache(...)} 的普通类版本，方法名保持 {@code allowAttachmentCache()/tagMatchCache()}。
     */
    public static final class Cache {

        private final Map<ResourceLocation, Set<ResourceLocation>> allowAttachmentCache;
        private final Map<ResourceLocation, Set<ResourceLocation>> tagMatchCache;

        public Cache(Map<ResourceLocation, Set<ResourceLocation>> allowAttachmentCache,
            Map<ResourceLocation, Set<ResourceLocation>> tagMatchCache) {
            this.allowAttachmentCache = allowAttachmentCache;
            this.tagMatchCache = tagMatchCache;
        }

        public Cache() {
            this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        }

        public Map<ResourceLocation, Set<ResourceLocation>> allowAttachmentCache() {
            return allowAttachmentCache;
        }

        public Map<ResourceLocation, Set<ResourceLocation>> tagMatchCache() {
            return tagMatchCache;
        }
    }

    public static boolean match(ResourceLocation gunId, ResourceLocation attachmentId) {
        return cache.allowAttachmentCache()
            .computeIfAbsent(gunId, AllowAttachmentTagMatcher::resolveAllowed)
            .contains(attachmentId);
    }

    public static boolean match0(Pair<ResourceLocation, ResourceLocation> record) {
        return resolveAllowed(record.getLeft()).contains(record.getRight());
    }

    private static Set<ResourceLocation> resolveAllowed(ResourceLocation gunId) {
        ICommonResourceProvider provider = CommonAssetsManager.get();
        if (provider == null) {
            return Collections.emptySet();
        }
        return expand(provider.getAllowAttachmentTags(gunId), null, provider::getAttachmentTags);
    }

    /**
     * 匹配配件是否有指定的标签。
     * 目前内部用于独头弹特殊标签的判断，
     * 也能方便到外部（附属，整合包等）制作它们的特殊标签。
     *
     * @param tag          tacz 配件标签
     * @param attachmentId 配件 id
     * @return 配件 id 是否有这个配件标签
     * @since 1.1.7
     */
    public static boolean matchTag(ResourceLocation tag, ResourceLocation attachmentId) {
        return cache.tagMatchCache()
            .computeIfAbsent(tag, AllowAttachmentTagMatcher::resolveTag)
            .contains(attachmentId);
    }

    public static boolean matchTag0(Pair<ResourceLocation, ResourceLocation> record) {
        return resolveTag(record.getLeft()).contains(record.getRight());
    }

    private static Set<ResourceLocation> resolveTag(ResourceLocation tag) {
        ICommonResourceProvider provider = CommonAssetsManager.get();
        if (provider == null) {
            return Collections.emptySet();
        }
        return expand(provider.getAttachmentTags(tag), tag, provider::getAttachmentTags);
    }

    /** Expand a complete root independently; caching partial child results is incorrect for cycles. */
    static Set<ResourceLocation> expand(Set<String> content, ResourceLocation rootTag,
        Function<ResourceLocation, Set<String>> getTag) {
        if (content == null || content.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ResourceLocation> attachments = new HashSet<>();
        Set<ResourceLocation> visited = new HashSet<>();
        if (rootTag != null) {
            visited.add(rootTag);
        }
        ArrayDeque<Iterator<String>> pending = new ArrayDeque<>();
        pending.push(content.iterator());
        while (!pending.isEmpty()) {
            Iterator<String> entries = pending.peek();
            if (!entries.hasNext()) {
                pending.pop();
                continue;
            }
            String entry = entries.next();
            if (entry.startsWith(TAG_PREFIX)) {
                ResourceLocation tagId = ResourceLocationHelper.tryParse(entry.substring(TAG_PREFIX.length()));
                if (tagId == null || !visited.add(tagId)) {
                    continue;
                }
                Set<String> child = getTag.apply(tagId);
                if (child != null && !child.isEmpty()) {
                    pending.push(child.iterator());
                }
            } else {
                ResourceLocation id = ResourceLocationHelper.tryParse(entry);
                if (id != null) {
                    attachments.add(id);
                }
            }
        }
        return Collections.unmodifiableSet(attachments);
    }

    public static void resetCache() {
        // In-flight computations may finish in the old cache, but cannot repopulate the new one.
        cache = new Cache();
    }
}
