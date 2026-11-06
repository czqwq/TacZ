package com.tacz.guns.client.gui.components.refit;

import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.RenderSystem;

/**
 * 枪械配件槽。
 *
 * <p>
 * 上游继承 1.20 {@code Button}（{@code renderWidget} + {@code OnPress}），
 * 1.7.10 改为 {@link GuiButton}（{@link #drawButton} + 构造期回调）。
 */
public class GunAttachmentSlot extends GuiButton implements IStackTooltip {

    private final AttachmentType type;
    private final InventoryPlayer inventory;
    private final int gunItemIndex;
    private final String nameKey;
    private final Consumer<GuiButton> onPress;
    private boolean selected = false;
    /** 1.7.10 用 null 表示空栈。 */
    private ItemStack attachmentItem;
    private boolean hovered;

    public GunAttachmentSlot(int x, int y, AttachmentType type, int gunItemIndex, InventoryPlayer inventory,
        Consumer<GuiButton> onPress) {
        super(0, x, y, GunRefitScreen.SLOT_SIZE, GunRefitScreen.SLOT_SIZE, "");
        this.type = type;
        this.inventory = inventory;
        this.gunItemIndex = gunItemIndex;
        this.nameKey = String.format(
            "tooltip.tacz.attachment.%s",
            type.name()
                .toLowerCase(Locale.US));
        this.onPress = onPress;
    }

    @Override
    public void renderTooltip(Consumer<ItemStack> consumer) {
        if (this.hovered && !isEmpty(this.attachmentItem)) {
            consumer.accept(this.attachmentItem);
        }
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
            && mouseX < this.xPosition + this.width
            && mouseY < this.yPosition + this.height;
        this.field_146123_n = this.hovered;

        GuiGraphics graphics = GuiGraphics.create();
        int x = this.xPosition;
        int y = this.yPosition;
        if (this.hovered) {
            int yOffset = y + 20;
            if (this.selected && !isEmpty(this.attachmentItem)) {
                yOffset = y + 30;
            }
            graphics.drawCenteredString(
                mc.fontRenderer,
                new ChatComponentTranslation(this.nameKey),
                x + this.width / 2,
                yOffset,
                0xFFFFFF);
        }
        ItemStack gunItem = this.inventory.getStackInSlot(this.gunItemIndex);
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return;
        }

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        // 渲染外框
        if (this.hovered || this.selected) {
            graphics.blit(
                GunRefitScreen.SLOT_TEXTURE,
                x,
                y,
                0,
                0,
                this.width,
                this.height,
                GunRefitScreen.SLOT_SIZE,
                GunRefitScreen.SLOT_SIZE);
        } else {
            graphics.blit(
                GunRefitScreen.SLOT_TEXTURE,
                x + 1,
                y + 1,
                1,
                1,
                this.width - 2,
                this.height - 2,
                GunRefitScreen.SLOT_SIZE,
                GunRefitScreen.SLOT_SIZE);
        }
        // 渲染内部物品，或者空置时的 icon
        this.attachmentItem = iGun.getAttachment(gunItem, this.type);
        if (!isEmpty(this.attachmentItem)) {
            graphics.renderItem(this.attachmentItem, x + 1, y + 1);
        } else {
            int xOffset = GunRefitScreen.getSlotTextureXOffset(gunItem, this.type);
            graphics.blit(
                GunRefitScreen.ICONS_TEXTURE,
                x + 2,
                y + 2,
                this.width - 4,
                this.height - 4,
                xOffset,
                0,
                GunRefitScreen.ICON_UV_SIZE,
                GunRefitScreen.ICON_UV_SIZE,
                GunRefitScreen.getSlotsTextureWidth(),
                GunRefitScreen.ICON_UV_SIZE);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            if (this.onPress != null) {
                this.onPress.accept(this);
            }
            return true;
        }
        return false;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public AttachmentType getType() {
        return this.type;
    }

    public ItemStack getAttachmentItem() {
        ItemStack gunItem = this.inventory.getStackInSlot(this.gunItemIndex);
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return null;
        }
        return iGun.getAttachment(gunItem, this.type);
    }

    public boolean isAllow() {
        ItemStack gunItem = this.inventory.getStackInSlot(this.gunItemIndex);
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return false;
        }
        return iGun.allowAttachmentType(gunItem, this.type);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getItem() == null;
    }
}
