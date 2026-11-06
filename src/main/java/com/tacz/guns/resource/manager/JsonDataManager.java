package com.tacz.guns.resource.manager;

import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.NotNull;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.IGunPackReloadListener;
import com.tacz.guns.resource.pack.GunPackResources;
import com.tacz.guns.resource.pack.IGunPackSource;
import com.tacz.guns.resource.pack.PackDirectory;

/**
 * 通用数据管理器：从枪包里读取 json 并解析为数据。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/JsonDataManager.java}，
 * 形态是 {@code extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>>}，
 * {@code prepare} 扫盘、{@code apply} 解析，由原版资源重载管线两段式调度。
 *
 * <p>
 * 1.7.10 差异：没有 {@code SimplePreparableReloadListener} / {@code ResourceManager} /
 * {@code ProfilerFiller}，也没有后台线程池，两段式折叠为同步的 {@link #reload()}
 * （{@code prepare} 的结果直接喂给 {@code apply}）。<b>解析逻辑与 getter 名与上游完全一致</b>。
 *
 * <p>
 * 该类会一次性加载所有数据；重资产数据请用 {@link LazyJsonDataManager}（沿用上游建议）。
 */
public class JsonDataManager<T> implements IGunPackReloadListener {

    protected final Map<ResourceLocation, T> dataMap = Maps.newHashMap();

    private final Gson gson;
    private final Class<T> dataClass;
    private final Marker marker;
    private final PackDirectory directory;
    private final String root;

    /**
     * 服务端数据（{@code data/<namespace>/<directory>}）的便捷构造，签名与上游一致。
     */
    public JsonDataManager(Class<T> dataClass, Gson pGson, String directory, String marker) {
        this(dataClass, pGson, PackDirectory.json(directory), marker, GunPackResources.DATA_ROOT);
    }

    public JsonDataManager(Class<T> dataClass, Gson pGson, PackDirectory directory, String marker) {
        this(dataClass, pGson, directory, marker, GunPackResources.DATA_ROOT);
    }

    /**
     * @param root {@link GunPackResources#DATA_ROOT}（服务端）或 {@link GunPackResources#ASSETS_ROOT}（客户端）
     */
    public JsonDataManager(Class<T> dataClass, Gson pGson, PackDirectory directory, String marker, String root) {
        this.gson = pGson;
        this.dataClass = dataClass;
        this.marker = MarkerManager.getMarker(marker);
        this.directory = directory;
        this.root = root;
    }

    /**
     * 上游 {@code prepare} + {@code apply} 的合并形态。
     *
     * <p>
     * 1.7.10 没有可挂载的资源重载管线，因此由 {@link com.tacz.guns.resource.CommonAssetsManager#reload()}
     * 按上游注册顺序串行触发。
     */
    @Override
    public void reload() {
        apply(GunPackResources.scanDirectory(packSources(), root, directory.prefix(), directory.extension(), gson));
    }

    protected void apply(Map<ResourceLocation, JsonElement> pObject) {
        dataMap.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement element = entry.getValue();
            try {
                T data = parseJson(element);
                if (data != null) {
                    dataMap.put(id, data);
                }
            } catch (JsonParseException | IllegalArgumentException e) {
                GunMod.LOGGER.error(marker, "Failed to load data file {}", id, e);
            }
        }
    }

    protected T parseJson(JsonElement element) {
        return gson.fromJson(element, getDataClass());
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
        return dataMap.get(id);
    }

    public Map<ResourceLocation, T> getAllData() {
        return dataMap;
    }

    /** 当前枪包列表。每次取新的，保证 {@code GunPackLoader#reloadPackList()} 之后能读到新包。 */
    protected @NotNull List<IGunPackSource> packSources() {
        return GunPackLoader.INSTANCE.getPackSources();
    }

    protected PackDirectory getPackDirectory() {
        return directory;
    }

    protected String getRoot() {
        return root;
    }
}
