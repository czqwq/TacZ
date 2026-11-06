package com.tacz.guns.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.tacz.guns.network.message.ServerMessageSyncConfig;
import com.tacz.guns.network.message.handshake.ServerMessageSyncedEntityDataMapping;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 玩家登录时的网络同步（1.7.10 版「login 阶段」的替代）。
 *
 * <p>
 * 上游（1.20.1）用两个机制：
 * <ol>
 * <li>{@code HandshakeHandler} + login packet 把 {@code SyncedEntityData} 的 key 映射表在 <b>login 阶段</b>推给客户端
 * （{@code NetworkHandler#registerHandshakeMessage}）；</li>
 * <li>Forge 自动把 {@code ModConfig.Type.SERVER} 的配置同步给客户端。</li>
 * </ol>
 * 1.7.10 的 {@code SimpleNetworkWrapper} 两者都没有，因此本类在 {@code PlayerLoggedInEvent} 里补做：
 * <ul>
 * <li>发 {@link ServerMessageSyncedEntityDataMapping}（客户端回 {@code Acknowledge} 完成握手）</li>
 * <li>发 {@link ServerMessageSyncConfig}（覆盖客户端内存里的 {@code SyncConfig} 值）</li>
 * </ul>
 * 差异记录：同步发生在玩家进入世界之后（而不是 login 阶段），最多晚 1~2 tick；
 * 客户端在收到之前使用本地配置文件的值（与上游「SERVER 配置在拿到服务端值前用默认值」的降级行为一致）。
 *
 * <p>
 * 由 {@code CommonProxy#preInit} 注册到事件总线。
 *
 * <p>
 * <b>1.7.10 差异（必须知道）</b>：{@link PlayerEvent.PlayerLoggedInEvent} 是 <b>FML 事件总线</b>上的事件，
 * 由 {@code FMLCommonHandler#firePlayerLoggedIn} 用 {@code FMLCommonHandler#bus()} 发出
 * （{@code build/rfg/minecraft-src/java/cpw/mods/fml/common/FMLCommonHandler.java:550}），
 * 而 {@code bus()} 返回的是 FML 私有的 {@code new EventBus()}（同文件 {@code :90/:97}），
 * 不是 {@code MinecraftForge.EVENT_BUS}。只挂在 Forge 总线上会导致本处理器永不触发 ——
 * 客户端拿不到枪包数据、也拿不到服务端配置。因此构造器里兜底注册到 FML 总线
 * （{@code EventBus#register} 对同一实例幂等，见 {@code EventBus.java:47-50}）。
 */
public class SyncConfigSyncManager {

    public SyncConfigSyncManager() {
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        // 1) SyncedEntityData 映射表（上游 login handshake 的等价物）
        NetworkHandler.HANDSHAKE_CHANNEL.sendTo(ServerMessageSyncedEntityDataMapping.snapshot(), player);
        // 2) SERVER 配置（上游 Forge 自动同步的等价物）
        NetworkHandler.sendToClientPlayer(ServerMessageSyncConfig.fromConfig(), player);
    }
}
