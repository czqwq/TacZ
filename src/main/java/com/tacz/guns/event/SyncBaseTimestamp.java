package com.tacz.guns.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSyncBaseTimestamp;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 玩家进入世界时下发服务端基准时间戳（{@link ServerMessageSyncBaseTimestamp}），用于客户端时钟对齐。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/SyncBaseTimestamp.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}；{@code @SubscribeEvent} 换包；</li>
 * <li>{@code EntityJoinLevelEvent} → {@link EntityJoinWorldEvent}（字段 {@code entity} / {@code world}）；</li>
 * <li>{@code Player} → {@link EntityPlayerMP}（上游用 {@code instanceof Player} 再强转 {@code ServerPlayer}，
 * 1.7.10 直接判定服务端玩家类，客户端 {@code EntityPlayerSP} 天然被排除）；</li>
 * <li>{@code Level#isClientSide()} → {@code World#isRemote}；</li>
 * <li>{@code NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(...), msg)} →
 * {@code NetworkHandler.sendToClientPlayer(msg, player)}（1.7.10 SimpleNetworkWrapper 封装）。</li>
 * </ul>
 */
public class SyncBaseTimestamp {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new SyncBaseTimestamp());
    }

    @SubscribeEvent
    public void onPlayerJoinWorld(EntityJoinWorldEvent event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        Entity entity = event.entity;
        if (entity instanceof EntityPlayerMP player) {
            NetworkHandler.sendToClientPlayer(new ServerMessageSyncBaseTimestamp(), player);
        }
    }
}
