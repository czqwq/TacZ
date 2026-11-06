package com.tacz.guns.network.message;

import net.minecraft.util.ResourceLocation;

import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 仅在服务端确认本次主动卸弹实际减少了枪内弹药后发送给操作者。
 * 上游：{@code .../network/message/ServerMessageGunUnload.java}
 *
 * <p>
 * 1.7.10 差异：上游在客户端分支里直接调 {@code SoundPlayManager.playUnloadSound(...)}
 * （client 包），这里改走 {@link ClientSideHooks}（common 不能编译期依赖 client）。
 */
public class ServerMessageGunUnload extends MessageBase {

    private ResourceLocation gunId;
    private ResourceLocation gunDisplayId;

    public ServerMessageGunUnload(ResourceLocation gunId, ResourceLocation gunDisplayId) {
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
    }

    public ServerMessageGunUnload() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeResourceLocation(gunId);
        buf.writeResourceLocation(gunDisplayId);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.gunId = buf.readResourceLocation();
        this.gunDisplayId = buf.readResourceLocation();
    }

    public ResourceLocation getGunId() {
        return gunId;
    }

    public ResourceLocation getGunDisplayId() {
        return gunDisplayId;
    }

    public static void handle(ServerMessageGunUnload message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(
            ctx.side,
            () -> ClientSideHooks.get()
                .gunUnload(message));
    }
}
