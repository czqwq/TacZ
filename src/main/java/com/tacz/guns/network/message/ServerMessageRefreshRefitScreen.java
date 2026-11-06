package com.tacz.guns.network.message;

import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：让改装界面重新初始化（换配件后刷新）。
 * 上游：{@code .../network/message/ServerMessageRefreshRefitScreen.java}（无字段）
 *
 * <p>
 * 1.7.10 差异：上游在客户端执行
 * {@code Minecraft.getInstance().screen instanceof GunRefitScreen ? screen.init() : ...} 并且刷新
 * {@code AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem())}；
 * 这里改走 {@link com.tacz.guns.network.ClientSideHooks.Impl#refreshRefitScreen}，由 ClientProxy 注入实现
 * （1.7.10 对应 {@code Minecraft.currentScreen instanceof GunRefitScreen}）。
 */
public class ServerMessageRefreshRefitScreen extends MessageBase {

    public static void handle(ServerMessageRefreshRefitScreen message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(
            ctx.side,
            () -> ClientSideHooks.get()
                .refreshRefitScreen(message));
    }
}
