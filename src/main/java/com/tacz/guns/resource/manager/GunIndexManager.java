package com.tacz.guns.resource.manager;

import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.network.DataType;

/**
 * 枪械索引（{@code data/<ns>/index/guns}）管理器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/GunIndexManager.java}。
 * 必须在 {@link GunDataManager} 之后重载（索引要查数据），该顺序由
 * {@link CommonAssetsManager#reload()} 保证。
 */
public class GunIndexManager extends CommonDataManager<CommonGunIndex> {

    public GunIndexManager() {
        super(DataType.GUN_INDEX, CommonGunIndex.class, CommonAssetsManager.GSON, "index/guns", "GunIndexLoader");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects) {
        super.apply(objects);

        ImmutableMap.Builder<ResourceLocation, String> builder = ImmutableMap.builder();
        objects.forEach((id, element) -> {
            CommonGunIndex index = getData(id);
            if (index == null) {
                builder.put(id, element.toString());
                return;
            }
            // 因为不会下发脚本，手动同步能否卸载
            // 1.7.10 差异：gson 2.2.4 的 JsonObject#deepCopy() 不是 public，
            // 这里手工浅拷贝一份再追加字段（只加一个布尔字段，不动子树，等价于上游 deepCopy）
            JsonObject source = element.getAsJsonObject();
            JsonObject synced = new JsonObject();
            source.entrySet()
                .forEach(entry -> synced.add(entry.getKey(), entry.getValue()));
            synced.addProperty("has_unload_ammo_script", index.hasUnloadAmmoScript());
            builder.put(id, synced.toString());
        });
        networkCache = builder.build();
    }
}
