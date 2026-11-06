package com.tacz.guns.network.message;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：播放一条由枪包配置的音效（不需要原版 SoundEvent 注册）。
 * 上游：{@code .../network/message/ServerMessageSound.java}
 *
 * <p>
 * 1.7.10 差异：上游客户端分支调 {@code SoundPlayManager.playMessageSound(...)}（client 包）→
 * 改走 {@link com.tacz.guns.network.ClientSideHooks.Impl#playSound}（1.7.10 对应 {@code Minecraft#getSoundHandler()}
 * 播放动态音效）。
 */
public class ServerMessageSound extends MessageBase {

    private int entityId;
    private ResourceLocation gunId;
    private ResourceLocation gunDisplayId;
    private String soundName;
    private float volume;
    private float pitch;
    private int distance;

    public ServerMessageSound(int entityId, ResourceLocation gunId, ResourceLocation gunDisplayId, String soundName,
        float volume, float pitch, int distance) {
        this.entityId = entityId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.soundName = soundName;
        this.volume = volume;
        this.pitch = pitch;
        this.distance = distance;
    }

    public ServerMessageSound(int entityId, ResourceLocation gunId, String soundName, float volume, float pitch,
        int distance) {
        this(entityId, gunId, DefaultAssets.DEFAULT_GUN_DISPLAY_ID, soundName, volume, pitch, distance);
    }

    public ServerMessageSound() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeVarInt(entityId);
        buf.writeResourceLocation(gunId);
        buf.writeResourceLocation(gunDisplayId);
        buf.writeUtf(soundName);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
        buf.writeInt(distance);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.entityId = buf.readVarInt();
        this.gunId = buf.readResourceLocation();
        this.gunDisplayId = buf.readResourceLocation();
        this.soundName = buf.readUtf();
        this.volume = buf.readFloat();
        this.pitch = buf.readFloat();
        this.distance = buf.readInt();
    }

    public int getEntityId() {
        return entityId;
    }

    public ResourceLocation getGunId() {
        return gunId;
    }

    public ResourceLocation getGunDisplayId() {
        return gunDisplayId;
    }

    public String getSoundName() {
        return soundName;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }

    public int getDistance() {
        return distance;
    }

    public static void handle(ServerMessageSound message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(
            ctx.side,
            () -> ClientSideHooks.get()
                .playSound(message));
    }
}
