package com.tacz.guns.network.message;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.SlotAdapterHelper;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：设置槽位适配器（slot adapter，例如把某槽改成可装其它类型配件）。
 * 上游：{@code .../network/message/ClientMessageSetSlotAdapter.java}
 *
 * <p>
 * 1.7.10 差异：{@code ItemStack.EMPTY} → {@code null}；{@code Inventory} → {@link InventoryPlayer}；
 * {@code inventory.getContainerSize()} → {@link InventoryPlayer#getSizeInventory()}；
 * 槽位越界检查见 {@link ClientMessageRefitGun} 的同名说明。
 * <p>
 * 依赖契约（common-infra 自己的 util）：{@link SlotAdapterHelper#getAvailableSlotAdapters(ItemStack, AttachmentType)}
 * 返回 {@code Map<ResourceLocation, SlotAdapter>}，{@code SlotAdapter#allowsAttachment(ResourceLocation)}。
 */
public class ClientMessageSetSlotAdapter extends MessageBase {

    private int gunSlotIndex;
    private AttachmentType attachmentType;
    @Nullable
    private ResourceLocation adapterId;

    public ClientMessageSetSlotAdapter(int gunSlotIndex, AttachmentType attachmentType,
        @Nullable ResourceLocation adapterId) {
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentType = attachmentType;
        this.adapterId = adapterId;
    }

    public ClientMessageSetSlotAdapter() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeInt(gunSlotIndex);
        buf.writeEnum(attachmentType);
        buf.writeBoolean(adapterId != null);
        if (adapterId != null) {
            buf.writeResourceLocation(adapterId);
        }
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.gunSlotIndex = buf.readInt();
        this.attachmentType = buf.readEnum(AttachmentType.class);
        this.adapterId = buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    public static void handle(ClientMessageSetSlotAdapter message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> handleServer(message, ctx.getServerHandler().playerEntity));
    }

    private static void handleServer(ClientMessageSetSlotAdapter message, @Nullable EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        // readEnum 越界抛 IAE（1.20 语义），null 也要挡（NONE 之外的第二种非法值）。
        if (message.attachmentType == null || message.attachmentType == AttachmentType.NONE) {
            return;
        }
        InventoryPlayer inventory = player.inventory;
        if (message.gunSlotIndex < 0 || message.gunSlotIndex >= inventory.getSizeInventory()) {
            return;
        }
        ItemStack gunItem = inventory.getStackInSlot(message.gunSlotIndex);
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null || gun.hasAttachmentLock(gunItem)
            || !gun.allowAttachmentType(gunItem, message.attachmentType)) {
            return;
        }

        ItemStack attachmentItem = gun.getAttachment(gunItem, message.attachmentType);
        ResourceLocation attachmentId = null;
        if (attachmentItem != null) {
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (attachment == null || attachment.getType(attachmentItem) != message.attachmentType) {
                return;
            }
            attachmentId = attachment.getAttachmentId(attachmentItem);
        }

        ItemStack beforeRefit = gunItem.copy();
        if (message.adapterId == null) {
            if (attachmentId != null && !SlotAdapterHelper.allowsDirectAttachment(gunItem, attachmentId)) {
                return;
            }
            SlotAdapterHelper.removeSlotAdapter(gunItem, message.attachmentType);
        } else {
            Map<ResourceLocation, SlotAdapterHelper.SlotAdapter> available = SlotAdapterHelper
                .getAvailableSlotAdapters(gunItem, message.attachmentType);
            SlotAdapterHelper.SlotAdapter adapter = available.get(message.adapterId);
            boolean validSelection = adapter != null
                && (attachmentId == null || adapter.allowsAttachment(attachmentId));
            if (!validSelection) {
                return;
            }
            SlotAdapterHelper.setSlotAdapter(gunItem, message.attachmentType, message.adapterId);
        }

        AttachmentPropertyManager.postChangeEvent(player, gunItem);
        GunRefitTrigger.INSTANCE.trigger(player, beforeRefit, gunItem);
        player.inventoryContainer.detectAndSendChanges();
        NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
    }
}
