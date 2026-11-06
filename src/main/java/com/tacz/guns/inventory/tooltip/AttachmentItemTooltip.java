package com.tacz.guns.inventory.tooltip;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.item.attachment.AttachmentType;

/**
 * 配件 tooltip 的数据载体。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/inventory/tooltip/AttachmentItemTooltip.java}
 * 
 * <pre>
 * {@code public class AttachmentItemTooltip implements TooltipComponent}
 * </pre>
 *
 * <p>
 * <b>1.7.10 差异</b>：去掉 {@code TooltipComponent} 接口，保留字段 / 构造器 / getter 的纯数据类；
 * {@code net.minecraft.world.item.ItemStack} → {@link ItemStack}，
 * {@code net.minecraft.resources.ResourceLocation} → {@link ResourceLocation}。
 */
public class AttachmentItemTooltip {

    private final ItemStack attachmentItem;
    private final ResourceLocation attachmentId;
    private final AttachmentType type;

    public AttachmentItemTooltip(ResourceLocation attachmentId, AttachmentType type, ItemStack attachmentItem) {
        this.attachmentId = attachmentId;
        this.type = type;
        this.attachmentItem = attachmentItem;
    }

    public ResourceLocation getAttachmentId() {
        return attachmentId;
    }

    public AttachmentType getType() {
        return type;
    }

    public ItemStack getAttachmentItem() {
        return attachmentItem;
    }
}
