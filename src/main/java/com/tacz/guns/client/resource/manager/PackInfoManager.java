package com.tacz.guns.client.resource.manager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Maps;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.mcshim.GsonHelper;
import com.tacz.guns.mcshim.PackSource;
import com.tacz.guns.mcshim.ResourceManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.IGunPackReloadListener;
import com.tacz.guns.resource.pack.GunPackResources;

/**
 * 枪包元数据（{@code gunpack_info.json}）管理器。
 *
 * <p>
 * <b>移植说明</b>：上游 {@code extends SimplePreparableReloadListener<Map<String, PackInfo>>}，
 * 挂在 1.20 的资源重载管线上（两段式 prepare/apply + 后台线程）。
 * 1.7.10 既没有该管线也没有 datapack，common-resource 已把所有枪包管理器统一成同步单段的
 * {@link IGunPackReloadListener#reload()}，本类照此改造：一次性扫描所有枪包里的
 * {@code assets/<namespace>/gunpack_info.json}，以 <b>namespace</b> 为键
 * （上游 {@code prepare()} 也是以 {@code manager.getNamespaces()} 的 namespace 为键）。
 * 键必须是 namespace 而不是枪包目录名：{@code ClientAssetsManager#getPackInfo(String)} 的调用方
 * （{@code CreativePackFilters} / {@code GunPackList} / 各 tooltip）传的都是物品的 namespace。
 *
 * <p>
 * 枪包来源列表复用 {@link ResourceManager#currentPackSources()}——它由 {@code GunPackLoader}
 * 通过 {@link ResourceManager#setPackSources} 注入，保证客户端与 common 侧看到同一份枪包列表，
 * 全仓不会出现第二套枪包扫描逻辑。
 */
public class PackInfoManager implements IGunPackReloadListener {

    private static final Marker MARKER = MarkerManager.getMarker("PackInfoLoader");
    private static final String PACK_INFO_NAME = "gunpack_info.json";
    /** 客户端资源根（1.20 的 {@code PackType.CLIENT_RESOURCES} 只看得到 {@code assets/}）。 */
    private static final String ASSETS_PREFIX = GunPackResources.ASSETS_ROOT + "/";
    private final Map<String, PackInfo> dataMap = Maps.newHashMap();
    private long reloadVersion;

    @Override
    public void reload() {
        Map<String, PackInfo> output = Maps.newHashMap();

        List<PackSource> sources = ResourceManager.currentPackSources();
        if (sources != null) {
            for (PackSource source : sources) {
                // 上游：for (String ns : manager.getNamespaces())
                // manager.getResource(new ResourceLocation(ns, "gunpack_info.json"))
                // 在 1.20 的客户端资源管理器里，这会命中 <pack>/assets/<namespace>/gunpack_info.json
                // （getResource 用完整 path 直接找文件，不剥扩展名）。
                // 1.7.10：直接在枪包里列举 assets/ 下的 gunpack_info.json 并从路径取 namespace，
                // 与「按 namespace 逐个取资源」等价；同 namespace 由后扫到的包覆盖先扫到的
                // （与 GunPackResources 的覆盖规则一致）。
                for (String path : source.listFiles(ASSETS_PREFIX)) {
                    String namespace = namespaceOfPackInfo(path);
                    if (namespace == null) {
                        continue;
                    }
                    try (InputStream stream = source.open(path)) {
                        if (stream == null) {
                            continue;
                        }
                        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                            PackInfo packInfo = GsonHelper
                                .fromJson(CommonAssetsManager.GSON, reader, PackInfo.class, true);
                            if (packInfo != null) {
                                output.put(namespace, packInfo);
                            }
                        }
                    } catch (IllegalArgumentException | JsonParseException exception) {
                        // 1.20 的 log4j 有带 Throwable 的多参重载，1.7.10 自带的 log4j 2.0-beta9 没有，
                        // 这里降级成不带 Throwable 的形式。
                        GunMod.LOGGER.error(MARKER, "Couldn't parse pack info for namespace '{}'", namespace);
                    } catch (IOException exception) {
                        GunMod.LOGGER.error(MARKER, "Couldn't read pack info for namespace '{}'", namespace);
                    }
                }
            }
        }

        dataMap.clear();
        dataMap.putAll(output);
        reloadVersion++;
    }

    /**
     * 判定 {@code assets/<namespace>/gunpack_info.json} 形态并取出 namespace。
     *
     * @return 不是该形态（层级不对 / 文件名不符）时返回 {@code null}
     */
    private static @Nullable String namespaceOfPackInfo(String path) {
        String suffix = "/" + PACK_INFO_NAME;
        if (!path.startsWith(ASSETS_PREFIX) || !path.endsWith(suffix)) {
            return null;
        }
        String rest = path.substring(ASSETS_PREFIX.length(), path.length() - suffix.length());
        // gunpack_info.json 必须直接位于 assets/<namespace>/ 下，不能再有子目录
        if (rest.isEmpty() || rest.indexOf('/') >= 0) {
            return null;
        }
        return rest;
    }

    public PackInfo getData(String namespace) {
        return dataMap.get(namespace);
    }

    public long getReloadVersion() {
        return reloadVersion;
    }
}
