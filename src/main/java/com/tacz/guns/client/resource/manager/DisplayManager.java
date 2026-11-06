package com.tacz.guns.client.resource.manager;

import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.resource.pojo.display.IDisplay;
import com.tacz.guns.resource.manager.JsonDataManager;
import com.tacz.guns.resource.pack.PackDirectory;

/**
 * 通用数据管理器<br>
 * 从资源包/数据包中读取json文件并解析为数据
 * 
 * @param <T> 数据类型
 */
public class DisplayManager<T extends IDisplay> extends JsonDataManager<T> {

    public DisplayManager(Class<T> dataClass, Gson pGson, String directory, String marker) {
        super(dataClass, pGson, PackDirectory.json(directory), marker);
    }

    public DisplayManager(Class<T> dataClass, Gson pGson, PackDirectory PackDirectory, String marker) {
        super(dataClass, pGson, PackDirectory, marker);
    }

    /**
     * 1.7.10 移植补充：枪包资源不在原版资源包体系内，需显式指定扫描根（{@code GunPackResources.ASSETS_ROOT}）。
     * 与 {@link JsonDataManager#JsonDataManager(Class, Gson, PackDirectory, String, String)} 的 5 参形态对齐。
     */
    public DisplayManager(Class<T> dataClass, Gson pGson, PackDirectory packDirectory, String marker, String root) {
        super(dataClass, pGson, packDirectory, marker, root);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> pObject) {
        dataMap.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement element = entry.getValue();
            try {
                T data = getGson().fromJson(element, getDataClass());
                if (data != null) {
                    data.init();
                    dataMap.put(id, data);
                }
            } catch (JsonParseException | IllegalArgumentException e) {
                GunMod.LOGGER.error(getMarker(), "Failed to load data file {}", id, e);
            }
        }
    }

}
