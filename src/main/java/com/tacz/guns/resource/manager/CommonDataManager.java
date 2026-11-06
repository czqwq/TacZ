package com.tacz.guns.resource.manager;

import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pack.PackDirectory;

/**
 * 服务端侧数据管理器：加载数据的同时把原始 json 缓存下来，用于向客户端同步。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/CommonDataManager.java}。
 * 1.7.10 差异：{@code apply(Map, ResourceManager, ProfilerFiller)} 折叠为
 * {@code apply(Map)}（原因见 {@link JsonDataManager}）；其余语义与上游一致。
 *
 * @param <T> 数据类型
 */
public class CommonDataManager<T> extends JsonDataManager<T> implements INetworkCacheReloadListener {

    private final DataType type;
    protected Map<ResourceLocation, String> networkCache = ImmutableMap.of();

    public CommonDataManager(DataType type, Class<T> dataClass, Gson pGson, String directory, String marker) {
        super(dataClass, pGson, directory, marker);
        this.type = type;
    }

    public CommonDataManager(DataType type, Class<T> dataClass, Gson pGson, PackDirectory directory, String marker) {
        super(dataClass, pGson, directory, marker);
        this.type = type;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject) {
        super.apply(pObject);

        ImmutableMap.Builder<ResourceLocation, String> builder = ImmutableMap.builder();
        pObject.forEach((id, element) -> builder.put(id, element.toString()));
        this.networkCache = builder.build();
    }

    public void clear() {
        this.dataMap.clear();
    }

    @Override
    public Map<ResourceLocation, String> getNetworkCache() {
        return this.networkCache;
    }

    @Override
    public DataType getType() {
        return this.type;
    }
}
