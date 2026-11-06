package com.tacz.guns.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

/**
 * 把「函数式处理器」桥接到 1.7.10 的 {@link IMessageHandler}，避免给每个包写一个 Handler 内部类。
 *
 * <p>
 * 上游（1.20.1）的注册是一行一个方法引用：
 * 
 * <pre>
 * CHANNEL.registerMessage(
 *     id,
 *     XxxMessage.class,
 *     XxxMessage::encode,
 *     XxxMessage::decode,
 *     XxxMessage::handle,
 *     Optional.of(NetworkDirection.PLAY_TO_SERVER));
 * </pre>
 * 
 * 1.7.10 的 {@code SimpleNetworkWrapper#registerMessage(IMessageHandler, Class, int, Side)} 需要
 * 一个处理器实例 + 一个 {@link cpw.mods.fml.relauncher.Side}（= 接收侧），语义一一对应。
 *
 * <p>
 * 返回 {@code null} 表示不回复：上游的 {@code NetworkEvent.Context#setPacketHandled(true)} 在 1.7.10
 * 由 simpleimpl 自己负责，不需要显式标记。
 */
public class SimpleMessageDispatcher<REQ extends IMessage> implements IMessageHandler<REQ, IMessage> {

    private final Handler<REQ> handler;

    public SimpleMessageDispatcher(Handler<REQ> handler) {
        this.handler = handler;
    }

    @Override
    public IMessage onMessage(REQ message, MessageContext ctx) {
        handler.handle(message, ctx);
        return null;
    }

    /**
     * 对应上游消息类里的 {@code static void handle(XxxMessage message, Supplier<NetworkEvent.Context>)}。
     */
    public interface Handler<REQ extends IMessage> {

        void handle(REQ message, MessageContext ctx);
    }
}
