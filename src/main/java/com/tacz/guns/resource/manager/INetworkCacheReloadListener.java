package com.tacz.guns.resource.manager;

import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.resource.IGunPackReloadListener;
import com.tacz.guns.resource.network.DataType;

/**
 * 需要把自己的原始 json 缓存下发给客户端的重载监听器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/manager/INetworkCacheReloadListener.java}，
 * 它额外继承了 {@code net.minecraft.server.packs.resources.PreparableReloadListener}。
 * 1.7.10 没有那套重载管线，改为继承本移植工程自研的
 * {@link com.tacz.guns.resource.IGunPackReloadListener}，其余契约（{@link #getNetworkCache()} /
 * {@link #getType()}）与上游一致。
 */
public interface INetworkCacheReloadListener extends IGunPackReloadListener {

    Map<ResourceLocation, String> getNetworkCache();

    DataType getType();
}
