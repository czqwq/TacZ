package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.VirtualOemAttachment;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：安装「虚拟 OEM 配件」（不占用背包格子的枪包内置配件）。
 * 上游：{@code .../network/message/ClientMessageInstallVirtualAttachment.java}
 *
 * <p>
 * 1.7.10 差异：{@code ItemStack.EMPTY} → {@code null}；
 * {@code inventory.getFreeSlot()} → {@link InventoryPlayer#getFirstEmptyStack()}（返回 -1 表示满）；
 * {@code inventory.add(stack)} → {@link InventoryPlayer#addItemStackToInventory(ItemStack)}；
 * 槽位越界检查见 {@link ClientMessageRefitGun} 的同名说明。
 */
public class ClientMessageInstallVirtualAttachment extends MessageBase {

    private int gunSlotIndex;
    private AttachmentType attachmentType;
    private ResourceLocation attachmentId;

    public ClientMessageInstallVirtualAttachment(int gunSlotIndex, AttachmentType attachmentType,
        ResourceLocation attachmentId) {
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentType = attachmentType;
        this.attachmentId = attachmentId;
    }

    public ClientMessageInstallVirtualAttachment() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeInt(gunSlotIndex);
        buf.writeEnum(attachmentType);
        buf.writeResourceLocation(attachmentId);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.gunSlotIndex = buf.readInt();
        this.attachmentType = buf.readEnum(AttachmentType.class);
        this.attachmentId = buf.readResourceLocation();
    }

    public static void handle(ClientMessageInstallVirtualAttachment message, MessageContext ctx) {
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
            if (iGun == null || iGun.hasAttachmentLock(gunItem)) {
                return;
            }
            if (!VirtualOemAttachment
                .isAllowedVirtualOem(iGun.getGunId(gunItem), message.attachmentId, message.attachmentType)) {
                return;
            }
            ItemStack virtualAttachment = AttachmentItemBuilder.create()
                .setId(message.attachmentId)
                .build();
            IAttachment iAttachment = IAttachment.getIAttachmentOrNull(virtualAttachment);
            if (iAttachment == null || iAttachment.getType(virtualAttachment) != message.attachmentType
                || !iGun.allowAttachment(gunItem, virtualAttachment)) {
                return;
            }
            ItemStack oldAttachmentItem = iGun.getAttachment(gunItem, message.attachmentType);
            if (oldAttachmentItem != null && !VirtualOemAttachment.isMarked(oldAttachmentItem)
                && inventory.getFirstEmptyStack() == -1) {
                return;
            }
            VirtualOemAttachment.mark(virtualAttachment);
            ItemStack beforeRefit = gunItem.copy();
            iGun.installAttachment(gunItem, virtualAttachment);
            GunRefitTrigger.INSTANCE.trigger(player, beforeRefit, gunItem);
            if (oldAttachmentItem != null && !VirtualOemAttachment.isMarked(oldAttachmentItem)) {
                inventory.addItemStackToInventory(oldAttachmentItem);
            }
            AttachmentPropertyManager.postChangeEvent(player, gunItem);
            player.inventoryContainer.detectAndSendChanges();
            NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
        });
    }
}
