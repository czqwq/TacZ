package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.VirtualOemAttachment;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：在改装界面把背包里的配件装到枪上。
 * 上游：{@code .../network/message/ClientMessageRefitGun.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code ItemStack.EMPTY} → {@code null}（本工程统一约定）</li>
 * <li>{@code player.getInventory()} → {@link EntityPlayerMP#inventory}；
 * {@code inventory.getItem(i)} → {@link InventoryPlayer#getStackInSlot(int)}；
 * {@code inventory.setItem(i, stack)} → {@link InventoryPlayer#setInventorySlotContents(int, ItemStack)}</li>
 * <li>{@code player.inventoryMenu.broadcastChanges()} → {@code player.inventoryContainer.detectAndSendChanges()}</li>
 * <li>{@code GunRefitTrigger}（1.20 advancement trigger）在 1.7.10 无对应，由 common-game 提供同签名空实现，
 * 调用点保持上游形态</li>
 * <li>槽位越界检查：1.20 的 {@code Inventory#getItem(i)} 对越界返回 {@code ItemStack.EMPTY}，
 * 1.7.10 的 {@link InventoryPlayer#getStackInSlot(int)} 越界即 AIOOBE，而槽位来自客户端报文，故在服务端夹紧</li>
 * </ul>
 */
public class ClientMessageRefitGun extends MessageBase {

    private int attachmentSlotIndex;
    private int gunSlotIndex;
    private AttachmentType attachmentType;

    public ClientMessageRefitGun(int attachmentSlotIndex, int gunSlotIndex, AttachmentType attachmentType) {
        this.attachmentSlotIndex = attachmentSlotIndex;
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentType = attachmentType;
    }

    public ClientMessageRefitGun() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeInt(attachmentSlotIndex);
        buf.writeInt(gunSlotIndex);
        buf.writeEnum(attachmentType);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.attachmentSlotIndex = buf.readInt();
        this.gunSlotIndex = buf.readInt();
        this.attachmentType = buf.readEnum(AttachmentType.class);
    }

    public static void handle(ClientMessageRefitGun message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return;
            }
            InventoryPlayer inventory = player.inventory;
            int inventorySize = inventory.getSizeInventory();
            if (message.gunSlotIndex < 0 || message.gunSlotIndex >= inventorySize
                || message.attachmentSlotIndex < 0
                || message.attachmentSlotIndex >= inventorySize) {
                return;
            }
            ItemStack attachmentItem = inventory.getStackInSlot(message.attachmentSlotIndex);
            ItemStack gunItem = inventory.getStackInSlot(message.gunSlotIndex);
            IGun iGun = IGun.getIGunOrNull(gunItem);
            if (iGun == null) {
                return;
            }
            // 服务端校验配件锁
            if (iGun.hasAttachmentLock(gunItem)) {
                return;
            }
            if (!iGun.allowAttachment(gunItem, attachmentItem)) {
                return;
            }
            // 使用配件物品自身的真实类型，而非客户端传入的 attachmentType
            IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (iAttachment == null) {
                return;
            }
            AttachmentType realType = iAttachment.getType(attachmentItem);
            ItemStack oldAttachmentItem = iGun.getAttachment(gunItem, realType);
            ItemStack beforeRefit = gunItem.copy();
            iGun.installAttachment(gunItem, attachmentItem);
            GunRefitTrigger.INSTANCE.trigger(player, beforeRefit, gunItem);
            // 刷新配件数据
            AttachmentPropertyManager.postChangeEvent(player, gunItem);
            inventory.setInventorySlotContents(
                message.attachmentSlotIndex,
                VirtualOemAttachment.isMarked(oldAttachmentItem) ? null : oldAttachmentItem);
            // 如果卸载的是扩容弹匣，吐出所有子弹
            if (realType == AttachmentType.EXTENDED_MAG) {
                iGun.dropAllAmmo(player, gunItem);
            }
            player.inventoryContainer.detectAndSendChanges();
            NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
        });
    }
}
