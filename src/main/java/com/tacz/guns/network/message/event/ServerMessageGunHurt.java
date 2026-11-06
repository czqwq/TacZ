package com.tacz.guns.network.message.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：在客户端投递 {@link EntityHurtByGunEvent.Post}。
 * 上游：{@code .../network/message/event/ServerMessageGunHurt.java}
 *
 * <p>
 * 1.7.10 差异：{@code ClientLevel#getEntity(id)} → {@link com.tacz.guns.network.ClientSideHooks.Impl#get()}（客户端注入
 * {@code worldObj.getEntityByID}）；{@code LogicalSide.CLIENT} → {@link Side#CLIENT}
 * （lead 裁决：1.7.10 统一用 {@code cpw.mods.fml.relauncher.Side}）。
 */
public class ServerMessageGunHurt extends MessageBase {

    private int bulletId;
    private int hurtEntityId;
    private int attackerId;
    private ResourceLocation gunId;
    private ResourceLocation gunDisplayId;
    private float amount;
    private boolean isHeadShot;
    private float headshotMultiplier;

    public ServerMessageGunHurt(int bulletId, int hurtEntityId, int attackerId, ResourceLocation gunId,
        ResourceLocation gunDisplayId, float amount, boolean isHeadShot, float headshotMultiplier) {
        this.bulletId = bulletId;
        this.hurtEntityId = hurtEntityId;
        this.attackerId = attackerId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.amount = amount;
        this.isHeadShot = isHeadShot;
        this.headshotMultiplier = headshotMultiplier;
    }

    public ServerMessageGunHurt() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeInt(bulletId);
        buf.writeInt(hurtEntityId);
        buf.writeInt(attackerId);
        buf.writeResourceLocation(gunId);
        buf.writeResourceLocation(gunDisplayId);
        buf.writeFloat(amount);
        buf.writeBoolean(isHeadShot);
        buf.writeFloat(headshotMultiplier);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.bulletId = buf.readInt();
        this.hurtEntityId = buf.readInt();
        this.attackerId = buf.readInt();
        this.gunId = buf.readResourceLocation();
        this.gunDisplayId = buf.readResourceLocation();
        this.amount = buf.readFloat();
        this.isHeadShot = buf.readBoolean();
        this.headshotMultiplier = buf.readFloat();
    }

    private void onHurt() {
        @Nullable
        Entity bullet = ClientSideHooks.get()
            .getEntityById(bulletId);
        @Nullable
        Entity hurtEntity = ClientSideHooks.get()
            .getEntityById(hurtEntityId);
        Entity attackerEntity = ClientSideHooks.get()
            .getEntityById(attackerId);
        @Nullable
        EntityLivingBase attacker = attackerEntity instanceof EntityLivingBase ? (EntityLivingBase) attackerEntity
            : null;
        // 上游 Pair<DamageSource, DamageSource> 在客户端为 null（服务端伤害源不可序列化），保持一致
        MinecraftForge.EVENT_BUS.post(
            new EntityHurtByGunEvent.Post(
                bullet,
                hurtEntity,
                attacker,
                gunId,
                gunDisplayId,
                amount,
                null,
                isHeadShot,
                headshotMultiplier,
                Side.CLIENT));
    }

    public static void handle(ServerMessageGunHurt message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, message::onHurt);
    }
}
