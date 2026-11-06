package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：玩家开火。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/network/message/ClientMessagePlayerShoot.java}
 *
 * <p>
 * 字段语义与上游一致：{@code timestamp} 是相对 base timestamp 的偏移时间戳，{@code chargeProgress} 是蓄力进度。
 * 差异：上游 {@code static encode/decode} 由基类 {@link MessageBase#write}/{@link MessageBase#read} 取代；
 * 上游 {@code context.enqueueWork} → {@link NetworkTaskQueue#enqueue(Side, Runnable)}
 * （1.7.10 的包体已由主线程分发，这里只是延后到本站 tick 末尾，见 {@link NetworkTaskQueue}）。
 */
public class ClientMessagePlayerShoot extends MessageBase {

    /**
     * 这里的 timestamp 应该是基于 base timestamp 的相对值
     */
    private long timestamp;
    private float chargeProgress;

    public ClientMessagePlayerShoot() {}

    public ClientMessagePlayerShoot(long timestamp) {
        this(timestamp, 0f);
    }

    public ClientMessagePlayerShoot(long timestamp, float chargeProgress) {
        this.timestamp = timestamp;
        this.chargeProgress = chargeProgress;
    }

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeLong(timestamp);
        buf.writeFloat(chargeProgress);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.timestamp = buf.readLong();
        this.chargeProgress = buf.readFloat();
    }

    public static void handle(ClientMessagePlayerShoot message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP entity = ctx.getServerHandler().playerEntity;
            if (entity == null) {
                return;
            }
            // 1.7.10：Entity#rotationPitch / rotationYaw 对应上游 entity::getXRot / entity::getYRot
            IGunOperator.fromLivingEntity(entity)
                .shoot(() -> entity.rotationPitch, () -> entity.rotationYaw, message.timestamp, message.chargeProgress);
        });
    }
}
