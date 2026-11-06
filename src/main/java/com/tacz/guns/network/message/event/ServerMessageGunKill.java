package com.tacz.guns.network.message.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：在客户端投递 {@link EntityKillByGunEvent}。
 * 上游：{@code .../network/message/event/ServerMessageGunKill.java}
 */
public class ServerMessageGunKill extends MessageBase {

    private int bulletId;
    private int killEntityId;
    private int attackerId;
    private ResourceLocation gunId;
    private ResourceLocation gunDisplayId;
    private boolean isHeadShot;
    private float baseDamage;
    private float headshotMultiplier;

    public ServerMessageGunKill(int bulletId, int killEntityId, int attackerId, ResourceLocation gunId,
        ResourceLocation gunDisplayId, float baseDamage, boolean isHeadShot, float headshotMultiplier) {
        this.bulletId = bulletId;
        this.killEntityId = killEntityId;
        this.attackerId = attackerId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.baseDamage = baseDamage;
        this.isHeadShot = isHeadShot;
        this.headshotMultiplier = headshotMultiplier;
    }

    public ServerMessageGunKill() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeInt(bulletId);
        buf.writeInt(killEntityId);
        buf.writeInt(attackerId);
        buf.writeResourceLocation(gunId);
        buf.writeResourceLocation(gunDisplayId);
        buf.writeFloat(baseDamage);
        buf.writeBoolean(isHeadShot);
        buf.writeFloat(headshotMultiplier);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.bulletId = buf.readInt();
        this.killEntityId = buf.readInt();
        this.attackerId = buf.readInt();
        this.gunId = buf.readResourceLocation();
        this.gunDisplayId = buf.readResourceLocation();
        this.baseDamage = buf.readFloat();
        this.isHeadShot = buf.readBoolean();
        this.headshotMultiplier = buf.readFloat();
    }

    private void onKill() {
        @Nullable
        Entity bullet = ClientSideHooks.get()
            .getEntityById(bulletId);
        Entity killed = ClientSideHooks.get()
            .getEntityById(killEntityId);
        @Nullable
        EntityLivingBase killedEntity = killed instanceof EntityLivingBase ? (EntityLivingBase) killed : null;
        Entity attackerEntity = ClientSideHooks.get()
            .getEntityById(attackerId);
        @Nullable
        EntityLivingBase attacker = attackerEntity instanceof EntityLivingBase ? (EntityLivingBase) attackerEntity
            : null;
        MinecraftForge.EVENT_BUS.post(
            new EntityKillByGunEvent(
                bullet,
                killedEntity,
                attacker,
                gunId,
                gunDisplayId,
                baseDamage,
                null,
                isHeadShot,
                headshotMultiplier,
                Side.CLIENT));
    }

    public static void handle(ServerMessageGunKill message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, message::onKill);
    }
}
