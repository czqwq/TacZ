package com.tacz.guns.client.gui.components.refit;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.RenderSystem;

/**
 * 背包里的可选配件槽。
 *
 * <p>
 * 1.20 {@code Inventory} → 1.7.10 {@link InventoryPlayer}
 * （{@code getContainerSize()}→{@code getSizeInventory()}、{@code getItem(i)}→{@code getStackInSlot(i)}）。
 */
public class InventoryAttachmentSlot extends GuiButton implements IStackTooltip {

    private final int slotIndex;
    private final InventoryPlayer inventory;
    /** 虚拟原厂配件用的展示栈；1.7.10 用 null 表示「没有」。 */
    private final ItemStack displayStack;
    private final Consumer<GuiButton> onPress;
    private boolean hovered;

    public InventoryAttachmentSlot(int x, int y, int slotIndex, InventoryPlayer inventory,
        Consumer<GuiButton> onPress) {
        this(x, y, slotIndex, inventory, null, onPress);
    }

    public InventoryAttachmentSlot(int x, int y, ItemStack displayStack, InventoryPlayer inventory,
        Consumer<GuiButton> onPress) {
        this(x, y, -1, inventory, displayStack, onPress);
    }

    private InventoryAttachmentSlot(int x, int y, int slotIndex, InventoryPlayer inventory, ItemStack displayStack,
        Consumer<GuiButton> onPress) {
        super(0, x, y, 18, 18, "");
        this.slotIndex = slotIndex;
        this.inventory = inventory;
        this.displayStack = displayStack;
        this.onPress = onPress;
    }

    @Override
    public void renderTooltip(Consumer<ItemStack> consumer) {
        if (this.hovered) {
            ItemStack item = getItemStack();
            if (item != null && item.getItem() != null) {
                consumer.accept(item);
            }
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
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();

        int x = this.xPosition;
        int y = this.yPosition;
        if (this.hovered) {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, this.width, this.height, 18, 18);
        } else {
            graphics.blit(GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, this.width - 2, this.height - 2, 18, 18);
        }
        graphics.renderItem(getItemStack(), x + 1, y + 1);

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

    public int getSlotIndex() {
        return this.slotIndex;
    }

    public ItemStack getItemStack() {
        if (this.displayStack != null && this.displayStack.getItem() != null) {
            return this.displayStack;
        }
        if (0 <= this.slotIndex && this.slotIndex < this.inventory.getSizeInventory()) {
            return this.inventory.getStackInSlot(this.slotIndex);
        }
        return null;
    }
}
