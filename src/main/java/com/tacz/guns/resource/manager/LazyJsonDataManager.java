package com.tacz.guns.resource.manager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.IGunPackReloadListener;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.PackDirectory;

/**
 * 通用数据管理器，采用懒加载策略：先只登记「id → 包内路径」，第一次 {@code getData} 时才解析。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/LazyJsonDataManager.java}，
 * 形态是 {@code extends SimplePreparableReloadListener<PreparedResult<T>>}。
 *
 * <p>
 * 1.7.10 差异：把「{@code prepare} 扫盘 + {@code apply} 登记」折叠成同步的 {@link #reload()}；
 * 懒加载语义（{@code dataMap} / {@code lazyLoaderMap} / {@code failedData} / {@code allResourceIds}）
 * 与 {@link #getData} 的并发保护逻辑与上游逐行保持一致。
 * {@code GsonHelper.fromJson(gson, reader, JsonElement.class, true)} 换成
 * {@link GunPackResources#readElement}（gson 2.2.4 的 {@code JsonParser#parse(Reader)} 本身就是 lenient）。
 */
public class LazyJsonDataManager<T> implements IGunPackReloadListener {

    protected final Map<ResourceLocation, T> dataMap = Maps.newHashMap();
    protected final Map<ResourceLocation, Supplier<LoadResult<T>>> lazyLoaderMap = Maps.newHashMap();
    protected final Set<ResourceLocation> failedData = new HashSet<>();
    protected final Set<ResourceLocation> allResourceIds = new HashSet<>();

    private final Gson gson;
    private final Class<T> dataClass;
    private final Marker marker;
    private final PackDirectory directory;
    private final String root;
    private final Predicate<ResourceLocation> eagerLoadPredicate;

    public LazyJsonDataManager(Class<T> dataClass, Gson pGson, PackDirectory directory, String marker) {
        this(dataClass, pGson, directory, marker, GunPackResources.DATA_ROOT, id -> true);
    }

    public LazyJsonDataManager(Class<T> dataClass, Gson pGson, String directory, String marker,
        Predicate<ResourceLocation> eagerLoadPredicate) {
        this(dataClass, pGson, PackDirectory.json(directory), marker, GunPackResources.DATA_ROOT, eagerLoadPredicate);
    }

    public LazyJsonDataManager(Class<T> dataClass, Gson pGson, PackDirectory directory, String marker,
        Predicate<ResourceLocation> eagerLoadPredicate) {
        this(dataClass, pGson, directory, marker, GunPackResources.DATA_ROOT, eagerLoadPredicate);
    }

    /**
     * @param root {@link GunPackResources#DATA_ROOT}（服务端）或 {@link GunPackResources#ASSETS_ROOT}（客户端）
     */
    public LazyJsonDataManager(Class<T> dataClass, Gson pGson, PackDirectory directory, String marker, String root,
        Predicate<ResourceLocation> eagerLoadPredicate) {
        this.gson = pGson;
        this.dataClass = dataClass;
        this.marker = MarkerManager.getMarker(marker);
        this.directory = directory;
        this.root = root;
        this.eagerLoadPredicate = eagerLoadPredicate;
    }

    @Override
    public void reload() {
        Map<ResourceLocation, GunPackResources.PackedResource> scannedResources = GunPackResources
            .scanDirectoryResources(
                GunPackLoader.INSTANCE.getPackSources(),
                root,
                directory.prefix(),
                directory.extension());
        Map<ResourceLocation, PreparedEntry<T>> preparedEntries = Maps
            .newHashMapWithExpectedSize(scannedResources.size());
        for (Map.Entry<ResourceLocation, GunPackResources.PackedResource> entry : scannedResources.entrySet()) {
            ResourceLocation id = entry.getKey();
            GunPackResources.PackedResource resourcePath = entry.getValue();
            // 给默认包的东西直接加载好
            if (shouldEagerLoad(id)) {
                JsonElement sourceElement = readResourceElement(resourcePath);
                if (sourceElement == null) {
                    preparedEntries.put(id, PreparedEntry.failure());
                } else {
                    preparedEntries.put(id, PreparedEntry.eager(sourceElement));
                }
            } else {
                preparedEntries.put(id, PreparedEntry.lazy(() -> loadResource(id, resourcePath)));
            }
        }
        apply(new PreparedResult<>(preparedEntries, Set.copyOf(scannedResources.keySet())));
    }

    protected void apply(PreparedResult<T> pObject) {
        dataMap.clear();
        lazyLoaderMap.clear();
        failedData.clear();
        allResourceIds.clear();
        allResourceIds.addAll(pObject.allResourceIds());
        for (Map.Entry<ResourceLocation, PreparedEntry<T>> entry : pObject.entries()
            .entrySet()) {
            ResourceLocation id = entry.getKey();
            PreparedEntry<T> preparedEntry = entry.getValue();
            if (preparedEntry.failed()) {
                failedData.add(id);
            } else if (preparedEntry.loader() != null) {
                lazyLoaderMap.put(id, preparedEntry.loader());
            } else {
                LoadResult<T> loaded = loadResourceFromElement(id, preparedEntry.sourceElement());
                if (loaded.failed()) {
                    failedData.add(id);
                } else if (loaded.data() != null) {
                    dataMap.put(id, loaded.data());
                }
            }
        }
    }

    protected @Nullable T parseJson(JsonElement element) {
        return gson.fromJson(element, getDataClass());
    }

    protected void postLoad(T data) {}

    protected final LoadResult<T> loadResource(ResourceLocation id, GunPackResources.PackedResource resourcePath) {
        JsonElement sourceElement = readResourceElement(resourcePath);
        if (sourceElement == null) {
            return LoadResult.failure();
        }
        return loadResourceFromElement(id, sourceElement);
    }

    protected final LoadResult<T> loadResourceFromElement(ResourceLocation id, JsonElement sourceElement) {
        try {
            T data = parseJson(sourceElement);
            if (data != null) {
                postLoad(data);
            }
            return LoadResult.success(data);
        } catch (JsonParseException | IllegalArgumentException e) {
            GunMod.LOGGER.error(marker, "Failed to load data file {}", id, e);
            return LoadResult.failure();
        }
    }

    protected final @Nullable JsonElement readResourceElement(GunPackResources.PackedResource resourcePath) {
        JsonElement element = GunPackResources.readElement(resourcePath.source(), resourcePath.path(), gson);
        if (element == null) {
            GunMod.LOGGER.error(marker, "Failed to read raw data file {}", resourcePath.path());
        }
        return element;
    }

    public Class<T> getDataClass() {
        return dataClass;
    }

    public Marker getMarker() {
        return marker;
    }

    public Gson getGson() {
        return gson;
    }

    public T getData(ResourceLocation id) {
        T data = dataMap.get(id);
        if (data != null || failedData.contains(id)) {
            return data;
        }
        Supplier<LoadResult<T>> loader = lazyLoaderMap.get(id);
        if (loader == null) {
            return null;
        }
        synchronized (this) {
            data = dataMap.get(id);
            if (data != null || failedData.contains(id)) {
                return data;
            }
            loader = lazyLoaderMap.remove(id);
            if (loader == null) {
                return null;
            }
            LoadResult<T> loaded = loader.get();
            if (loaded.failed()) {
                failedData.add(id);
                return null;
            }
            data = loaded.data();
            if (data != null) {
                dataMap.put(id, data);
            }
            failedData.remove(id);
            return data;
        }
    }

    public Map<ResourceLocation, T> getAllData() {
        return dataMap;
    }

    public Set<ResourceLocation> getAllIds() {
        return allResourceIds;
    }

    protected final boolean shouldEagerLoad(ResourceLocation id) {
        return eagerLoadPredicate.test(id);
    }

    protected final PackDirectory getFileToIdConverter() {
        return directory;
    }

    protected record LoadResult<T> (@Nullable T data, boolean failed) {

        protected static <T> LoadResult<T> success(@Nullable T data) {
            return new LoadResult<>(data, false);
        }

        protected static <T> LoadResult<T> failure() {
            return new LoadResult<>(null, true);
        }
    }

    protected record PreparedEntry<T> (@Nullable JsonElement sourceElement, @Nullable Supplier<LoadResult<T>> loader,
        boolean failed) {

        protected static <T> PreparedEntry<T> eager(@Nullable JsonElement sourceElement) {
            return new PreparedEntry<>(sourceElement, null, false);
        }

        protected static <T> PreparedEntry<T> lazy(Supplier<LoadResult<T>> loader) {
            return new PreparedEntry<>(null, loader, false);
        }

        protected static <T> PreparedEntry<T> failure() {
            return new PreparedEntry<>(null, null, true);
        }
    }

    protected record PreparedResult<T> (Map<ResourceLocation, PreparedEntry<T>> entries,
        Set<ResourceLocation> allResourceIds) {}
}
