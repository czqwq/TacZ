package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：请求校准 base timestamp（网络时基同步握手的第一步）。
 * 上游：{@code .../network/message/ClientMessageSyncBaseTimestamp.java}（无字段）
 *
 * <p>
 * 差异：上游在 Netty 线程立刻取 {@code GunTime.nowMillis()} 再 {@code enqueueWork}；
 * 1.7.10 用 {@link NetworkTaskQueue}，时间戳仍按上游在<b>入队前</b>取，保证语义一致。
 */
public class ClientMessageSyncBaseTimestamp extends MessageBase {

    private static final Marker MARKER = MarkerManager.getMarker("SYNC_BASE_TIMESTAMP");

    public ClientMessageSyncBaseTimestamp() {}

    public static void handle(ClientMessageSyncBaseTimestamp message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        long timestamp = GunTime.nowMillis();
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP entity = ctx.getServerHandler().playerEntity;
            if (entity == null) {
                return;
            }
            ShooterDataHolder dataHolder = IGunOperator.fromLivingEntity(entity)
                .getDataHolder();
            dataHolder.baseTimestamp = timestamp;
            GunMod.LOGGER.debug(MARKER, "Update server base timestamp: {}", dataHolder.baseTimestamp);
        });
    }
}
