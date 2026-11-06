package com.tacz.guns.network;

import com.tacz.guns.mcshim.PacketBuffer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * 所有网络包体的公共基类：把 1.7.10 {@link IMessage} 的 {@code ByteBuf} 出入口
 * 适配成 1.20.1 {@code FriendlyByteBuf} 风格的 {@link PacketBuffer} 读写。
 *
 * <p>
 * 上游（1.20.1）每个消息类都是「纯数据类 + static {@code encode/decode/handle}」，
 * 由 {@code SimpleChannel#registerMessage} 用方法引用注册；1.7.10 的
 * {@code SimpleNetworkWrapper} 要求包体自己实现 {@link IMessage#fromBytes}/{@link IMessage#toBytes}。
 * 本类就是这层适配：
 * <ul>
 * <li>{@link #read(PacketBuffer)} / {@link #write(PacketBuffer)} 里写上游 encode/decode 的正文，
 * 方法名与字段语义与上游一致；</li>
 * <li>{@link #fromBytes}/{@link #toBytes} 声明为 final，禁止子类再碰裸 {@link ByteBuf}。</li>
 * </ul>
 *
 * <p>
 * 注意：{@code PacketBuffer} 只是包装，不复制底层 buffer（见 {@code mcshim.PacketBuffer}）。
 */
public abstract class MessageBase implements IMessage {

    @Override
    public final void fromBytes(ByteBuf buf) {
        read(new PacketBuffer(buf));
    }

    @Override
    public final void toBytes(ByteBuf buf) {
        write(new PacketBuffer(buf));
    }

    /**
     * 对应上游 {@code static XxxMessage decode(FriendlyByteBuf)}：从包里读出字段。
     */
    protected void read(PacketBuffer buf) {
        // 无字段的包不覆写
    }

    /**
     * 对应上游 {@code static void encode(XxxMessage, FriendlyByteBuf)}：把字段写进包。
     */
    protected void write(PacketBuffer buf) {
        // 无字段的包不覆写
    }
}
