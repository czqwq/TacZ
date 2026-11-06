package com.tacz.guns.client.event;

import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * 联机时清空并重建客户端枪包缓存（专用服务器下枪包数据由服务端下发）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/CommonNetworkCacheEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)}）。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code ClientPlayerNetworkEvent.LoggingIn} → {@link FMLNetworkEvent.ClientConnectedToServerEvent}；
 * 上游的 {@code event.getConnection().isMemoryConnection()}（内存连接=单人/局域网内嵌服务端）→
 * 1.7.10 的 {@code event.isLocal}（{@code NetworkManager#isLocalChannel()}，语义等价）；</li>
 * <li>{@code ClientPlayerNetworkEvent.LoggingOut} →
 * {@link FMLNetworkEvent.ClientDisconnectionFromServerEvent}；</li>
 * <li>这两个 FML 网络事件注册在 {@code FMLCommonHandler.instance().bus()}，不是
 * {@code MinecraftForge.EVENT_BUS}（{@code client/ClientProxy} 已按此注册）。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 特有（相对上游）</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（{@code cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115}）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在事件派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法(...)} 在这里改为 {@code public void 处理方法(...)}
 * （类名 / 方法名 / 参数 / 语义一律不变；非处理方法的静态工具方法与字段仍保持 {@code static}）。
 * 本类也因为「{@code EventBus#register(Class)} 在 1.7.10 无效」而提供 public 无参构造，
 * 由 {@code client/ClientProxy} 反射 {@code new} 后注册到正确的总线。
 */
public class CommonNetworkCacheEvent {

    @SubscribeEvent
    public void onClientPlayerLoggingIn(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        ServerMessageSyncGunPack.clearPendingSync();
        // 上游：event.getConnection() == null || event.getConnection().isMemoryConnection() → return
        if (event.isLocal) {
            return;
        }
        CommonAssetsManager.clearInstance();
        CommonNetworkCache.INSTANCE.clear();
        ClientIndexManager.clear();
    }

    @SubscribeEvent
    public void onClientPlayerLoggingOut(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ServerMessageSyncGunPack.clearPendingSync();
    }
}
