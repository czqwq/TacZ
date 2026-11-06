package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.VariantHelper;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：设置配件皮肤（variant）。
 * 上游：{@code .../network/message/ClientMessageSetAttachmentVariant.java}
 *
 * <p>
 * 1.7.10 差异：{@code ItemStack.EMPTY} → {@code null}；{@code Inventory} →
 * {@link InventoryPlayer}；{@code inventory.getItem(i)} → {@link InventoryPlayer#getStackInSlot(int)}；
 * 槽位越界检查见 {@link ClientMessageRefitGun} 的同名说明。
 */
public class ClientMessageSetAttachmentVariant extends MessageBase {

    private int gunSlotIndex;
    private AttachmentType attachmentType;
    @Nullable
    private ResourceLocation variantId;

    public ClientMessageSetAttachmentVariant(int gunSlotIndex, AttachmentType attachmentType,
        @Nullable ResourceLocation variantId) {
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentType = attachmentType;
        this.variantId = variantId;
    }

    public ClientMessageSetAttachmentVariant() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeInt(gunSlotIndex);
        buf.writeEnum(attachmentType);
        buf.writeBoolean(variantId != null);
        if (variantId != null) {
            buf.writeResourceLocation(variantId);
        }
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.gunSlotIndex = buf.readInt();
        this.attachmentType = buf.readEnum(AttachmentType.class);
        this.variantId = buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    public static void handle(ClientMessageSetAttachmentVariant message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> handleServer(message, ctx.getServerHandler().playerEntity));
    }

    private static void handleServer(ClientMessageSetAttachmentVariant message, @Nullable EntityPlayerMP player) {
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
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null || gun.hasAttachmentLock(gunItem)) {
            return;
        }
        ItemStack attachmentItem = gun.getAttachment(gunItem, message.attachmentType);
        // 上游是 !attachmentItem.isEmpty()：1.7.10 空栈为 null
        if (attachmentItem == null) {
            return;
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (attachment == null) {
            return;
        }
        ItemStack beforeRefit = gunItem.copy();
        ResourceLocation attachmentId = attachment.getAttachmentId(attachmentItem);
        if (message.variantId == null) {
            VariantHelper.removeVariant(attachmentItem);
        } else {
            boolean valid = TimelessAPI.getCommonAttachmentVariantIndex(message.variantId)
                .map(index -> index.appliesTo(attachmentId))
                .orElse(false);
            if (!valid) {
                return;
            }
            VariantHelper.setVariant(attachmentItem, message.variantId);
        }
        gun.installAttachment(gunItem, attachmentItem);
        GunRefitTrigger.INSTANCE.trigger(player, beforeRefit, gunItem);
        AttachmentPropertyManager.postChangeEvent(player, gunItem);
        player.inventoryContainer.detectAndSendChanges();
        NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
    }
}
