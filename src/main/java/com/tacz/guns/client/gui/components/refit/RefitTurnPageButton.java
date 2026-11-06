package com.tacz.guns.client.gui.components.refit;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.RenderSystem;

/** 配件列表翻页按钮。1.7.10 版把 {@code Button.OnPress} 换成构造期回调。 */
public class RefitTurnPageButton extends GuiButton implements IComponentTooltip {

    private final boolean isUpPage;
    private final Consumer<GuiButton> onPress;
    private boolean hovered;

    public RefitTurnPageButton(int x, int y, boolean isUpPage, Consumer<GuiButton> onPress) {
        super(0, x, y, 18, 8, "");
        this.isUpPage = isUpPage;
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

        GuiGraphics graphics = GuiGraphics.create();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();

        int x = this.xPosition;
        int y = this.yPosition;
        int yOffset = this.isUpPage ? 0 : 80;
        if (this.hovered) {
            graphics
                .blit(GunRefitScreen.TURN_PAGE_TEXTURE, x, y, this.width, this.height, 0, yOffset, 180, 80, 180, 160);
        } else {
            graphics.blit(
                GunRefitScreen.TURN_PAGE_TEXTURE,
                x + 1,
                y + 1,
                this.width - 2,
                this.height - 2,
                10,
                yOffset + 10,
                180 - 20,
                80 - 20,
                180,
                160);
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

    @Override
    public void renderTooltip(Consumer<List<IChatComponent>> consumer) {
        if (this.hovered) {
            String key = this.isUpPage ? "tooltip.tacz.page.previous" : "tooltip.tacz.page.next";
            consumer.accept(Collections.singletonList(new ChatComponentTranslation(key)));
        }
    }
}
