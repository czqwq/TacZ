package com.tacz.guns.network.message;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：同步激光颜色（枪身 + 各配件）。
 * 上游：{@code .../network/message/ClientMessageLaserColor.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code buf.writeMap(...)} 的 key 写入器改为 {@code PacketBuffer::writeEnum}（1.7.10 无
 * {@code FriendlyByteBuf}，用 mcshim 的同名方法）</li>
 * <li>{@code HashMap} → {@link EnumMap}：上游 decode 用 {@code readMap} 返回 HashMap，
 * 本工程 readMap 返回 {@code HashMap}，这里显式转回 EnumMap 保证枚举序（与原 writeMap 的顺序语义一致）</li>
 * <li>Java 16 的 {@code instanceof} 模式匹配 → 显式强转</li>
 * </ul>
 */
public class ClientMessageLaserColor extends MessageBase {

    private final Map<AttachmentType, Integer> colorMap = new EnumMap<>(AttachmentType.class);
    private boolean applyGunColor = false;
    private int gunColor = 0;

    private int gunSlotIndex = -1;

    /**
     * <b>必须是 {@code public} 无参构造器</b>（否则实机 ICE）。
     *
     * <p>
     * FML 的 {@code FMLIndexedMessageToMessageCodec#decode}（{@code :75}）用
     * <b>{@code Class.newInstance()}</b> 反序列化消息，而它<b>只能调 public 无参构造器</b>；
     * 写成 {@code private} 会在收到该包时抛
     * {@code IllegalAccessException: ... cannot access a member of class ... with modifiers "private"}，
     * 并且 netty 会把它升级成 {@code There was a critical exception handling a packet on channel tacz}
     * （实测：每次改激光颜色都会刷十几条栈）。
     * 同类要求的还有「不能被 {@code record}/{@code final 字段} 挡住的 setter 式反序列化」，
     * 见 {@code MessageBase} 的类注释。
     */
    public ClientMessageLaserColor() {}

    public ClientMessageLaserColor(@NotNull ItemStack gun, int gunSlotIndex) {
        if (gun != null && gun.getItem() instanceof IGun) {
            IGun iGun = (IGun) gun.getItem();
            for (AttachmentType type : AttachmentType.values()) {
                ItemStack attachment = iGun.getAttachment(gun, type);
                if (attachment != null && attachment.getItem() instanceof IAttachment) {
                    IAttachment iAttachment = (IAttachment) attachment.getItem();
                    if (iAttachment.hasCustomLaserColor(attachment)) {
                        colorMap.put(type, iAttachment.getLaserColor(attachment));
                    }
                }
            }
            if (iGun.hasCustomLaserColor(gun)) {
                this.gunColor = iGun.getLaserColor(gun);
                this.applyGunColor = true;
            }
            this.gunSlotIndex = gunSlotIndex;
        }
    }

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeMap(colorMap, PacketBuffer::writeEnum, PacketBuffer::writeInt);
        buf.writeBoolean(applyGunColor);
        buf.writeInt(gunColor);
        buf.writeInt(gunSlotIndex);
    }

    @Override
    protected void read(PacketBuffer buf) {
        Map<AttachmentType, Integer> decoded = buf
            .readMap(buffer -> buffer.readEnum(AttachmentType.class), PacketBuffer::readInt);
        // readEnum 越界抛 IAE（1.20 语义）；此处 null 检查为双保险（EnumMap 遇 null key 会 NPE）。
        for (Map.Entry<AttachmentType, Integer> entry : decoded.entrySet()) {
            if (entry.getKey() != null) {
                colorMap.put(entry.getKey(), entry.getValue());
            }
        }
        this.applyGunColor = buf.readBoolean();
        this.gunColor = buf.readInt();
        this.gunSlotIndex = buf.readInt();
    }

    public static void handle(ClientMessageLaserColor message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || message.gunSlotIndex == -1) {
                return;
            }
            InventoryPlayer inventory = player.inventory;
            // 1.7.10 差异：1.20 的 Inventory#getItem(i) 对越界返回 ItemStack.EMPTY，
            // 1.7.10 的 InventoryPlayer#getStackInSlot(i) 直接 mainInventory[i] / armorInventory[i] —— 越界即 AIOOBE。
            // 槽位来自客户端报文，必须先在服务端夹紧范围（约定同 ClientMessageSetSlotAdapter:82-84）。
            if (message.gunSlotIndex < 0 || message.gunSlotIndex >= inventory.getSizeInventory()) {
                return;
            }
            ItemStack gunItem = inventory.getStackInSlot(message.gunSlotIndex);
            IGun iGun = IGun.getIGunOrNull(gunItem);
            if (iGun != null) {
                for (Map.Entry<AttachmentType, Integer> entry : message.colorMap.entrySet()) {
                    AttachmentType type = entry.getKey();
                    int color = entry.getValue() == null ? 0 : entry.getValue();
                    ItemStack attachment = iGun.getAttachment(gunItem, type);
                    if (attachment != null && attachment.getItem() instanceof IAttachment) {
                        ((IAttachment) attachment.getItem()).setLaserColor(attachment, color);
                    }
                }
                if (message.applyGunColor) {
                    iGun.setLaserColor(gunItem, message.gunColor);
                }
            }
        });
    }
}
