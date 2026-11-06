package com.tacz.guns.network.message;

import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：通知枪械工作台界面刷新材料计数。
 * 上游：{@code .../network/message/ServerMessageCraft.java}
 *
 * <p>
 * 1.7.10 差异：上游客户端分支判断 {@code Minecraft.getInstance().screen instanceof GunSmithTableScreen}
 * 再调 {@code screen.updateIngredientCount()}；这里改走 {@link ClientSideHooks}，
 * 由 ClientProxy 注入（1.7.10 对应 {@code Minecraft.currentScreen instanceof GunSmithTableScreen}）。
 */
public class ServerMessageCraft extends MessageBase {

    private int menuId;

    public ServerMessageCraft(int menuId) {
        this.menuId = menuId;
    }

    public ServerMessageCraft() {}

    public int getMenuId() {
        return menuId;
    }

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeVarInt(menuId);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.menuId = buf.readVarInt();
    }

    public static void handle(ServerMessageCraft message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(
            ctx.side,
            () -> ClientSideHooks.get()
                .craft(message));
    }
}
