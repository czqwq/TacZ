package com.tacz.guns.client.gui.components.smith;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.RenderSystem;

/** 枪械工作台配方结果按钮。 */
public class ResultButton extends GuiButton {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        GunMod.MOD_ID,
        "textures/gui/gun_smith_table.png");

    private final ItemStack stack;
    private final Consumer<GuiButton> onPress;
    private boolean isSelected = false;
    private boolean hovered;

    public ResultButton(int x, int y, ItemStack stack, Consumer<GuiButton> onPress) {
        super(0, x, y, 94, 16, "");
        this.stack = stack;
        this.onPress = onPress;
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

        GuiGraphics gui = GuiGraphics.create();
        RenderSystem.enableDepthTest();

        if (this.isSelected) {
            if (this.hovered) {
                gui.blit(
                    TEXTURE,
                    this.xPosition - 1,
                    this.yPosition - 1,
                    52,
                    229,
                    this.width + 2,
                    this.height + 2,
                    256,
                    256);
            } else {
                gui.blit(TEXTURE, this.xPosition, this.yPosition, 53, 230, this.width, this.height, 256, 256);
            }
        } else {
            if (this.hovered) {
                gui.blit(
                    TEXTURE,
                    this.xPosition - 1,
                    this.yPosition - 1,
                    52,
                    211,
                    this.width + 2,
                    this.height + 2,
                    256,
                    256);
            } else {
                gui.blit(TEXTURE, this.xPosition, this.yPosition, 53, 212, this.width, this.height, 256, 256);
            }
        }
        gui.renderItem(this.stack, this.xPosition + 1, this.yPosition);

        // 1.20 ItemStack#getHoverName() → 1.7.10 ItemStack#getDisplayName()（已带格式码）
        String hoverName = this.stack == null ? "" : this.stack.getDisplayName();
        // 对应上游 Button#renderScrollingString(gui, font, hoverName, x+20, y+4, x+92, y+13, 0xFFFFFF)
        // （AbstractWidget.java:126-142）：装得下就**居中**画，装不下才裁剪+滚动；这里把滚动降级为截断。
        int regionLeft = this.xPosition + 20;
        int regionRight = this.xPosition + 92;
        int textY = this.yPosition + 5;
        if (mc.fontRenderer.getStringWidth(hoverName) <= regionRight - regionLeft) {
            gui.drawCenteredString(
                mc.fontRenderer,
                new ChatComponentText(hoverName),
                (regionLeft + regionRight) / 2,
                textY,
                0xFFFFFF);
        } else {
            String trimmed = mc.fontRenderer.trimStringToWidth(hoverName, regionRight - regionLeft);
            gui.drawString(mc.fontRenderer, new ChatComponentText(trimmed), regionLeft, textY, 0xFFFFFF);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            this.onPress();
            return true;
        }
        return false;
    }

    /** 对应 1.20 {@code Button#onPress()}：选中自身并回调。 */
    public void onPress() {
        this.isSelected = true;
        if (this.onPress != null) {
            this.onPress.accept(this);
        }
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    public void renderTooltips(Consumer<ItemStack> consumer) {
        if (this.hovered && this.stack != null && this.stack.getItem() != null) {
            consumer.accept(this.stack);
        }
    }
}
