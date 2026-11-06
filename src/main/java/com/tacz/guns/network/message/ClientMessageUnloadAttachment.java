package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.tacz.guns.advancement.GunRefitTrigger;
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
 * 客户端 → 服务端：从枪上卸下配件。
 * 上游：{@code .../network/message/ClientMessageUnloadAttachment.java}
 *
 * <p>
 * 1.7.10 差异：{@code ItemStack.EMPTY} → {@code null}；{@code inventory.add(stack)} →
 * {@link InventoryPlayer#addItemStackToInventory(ItemStack)}（返回 {@code boolean}，语义一致）；
 * 槽位越界检查见 {@link ClientMessageRefitGun} 的同名说明。
 */
public class ClientMessageUnloadAttachment extends MessageBase {

    private int gunSlotIndex;
    private AttachmentType attachmentType;

    public ClientMessageUnloadAttachment(int gunSlotIndex, AttachmentType attachmentType) {
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentType = attachmentType;
    }

    public ClientMessageUnloadAttachment() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeInt(gunSlotIndex);
        buf.writeEnum(attachmentType);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.gunSlotIndex = buf.readInt();
        this.attachmentType = buf.readEnum(AttachmentType.class);
    }

    public static void handle(ClientMessageUnloadAttachment message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return;
            }
            InventoryPlayer inventory = player.inventory;
            if (message.gunSlotIndex < 0 || message.gunSlotIndex >= inventory.getSizeInventory()) {
                return;
            }
            // readEnum 越界抛 IAE（1.20 语义）；此处 null 检查为双保险。
            if (message.attachmentType == null) {
                return;
            }
            ItemStack gunItem = inventory.getStackInSlot(message.gunSlotIndex);
            IGun iGun = IGun.getIGunOrNull(gunItem);
            if (iGun == null) {
                return;
            }
            // 服务端校验配件锁
            if (iGun.hasAttachmentLock(gunItem)) {
                return;
            }
            ItemStack attachmentItem = iGun.getAttachment(gunItem, message.attachmentType);
            boolean marked = VirtualOemAttachment.isMarked(attachmentItem);
            if (attachmentItem == null || (!marked && !inventory.addItemStackToInventory(attachmentItem))) {
                return;
            }
            ItemStack beforeRefit = gunItem.copy();
            iGun.unloadAttachment(gunItem, message.attachmentType);
            GunRefitTrigger.INSTANCE.trigger(player, beforeRefit, gunItem);
            // 刷新配件数据
            AttachmentPropertyManager.postChangeEvent(player, gunItem);
            // 如果卸载的是扩容弹匣，吐出所有子弹
            if (message.attachmentType == AttachmentType.EXTENDED_MAG) {
                iGun.dropAllAmmo(player, gunItem);
            }
            player.inventoryContainer.detectAndSendChanges();
            NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
        });
    }
}
