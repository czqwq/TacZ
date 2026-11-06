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

/** 卸下配件按钮。1.7.10 版把 {@code Button.OnPress} 换成构造期回调。 */
public class RefitUnloadButton extends GuiButton implements IComponentTooltip {

    private final Consumer<GuiButton> onPress;
    private boolean hovered;

    public RefitUnloadButton(int x, int y, Consumer<GuiButton> onPress) {
        super(0, x, y, 8, 8, "");
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
        if (this.hovered) {
            graphics.blit(GunRefitScreen.UNLOAD_TEXTURE, x, y, this.width, this.height, 0, 0, 80, 80, 160, 80);
        } else {
            graphics.blit(GunRefitScreen.UNLOAD_TEXTURE, x, y, this.width, this.height, 80, 0, 80, 80, 160, 80);
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
            consumer.accept(Collections.singletonList(new ChatComponentTranslation("tooltip.tacz.refit.unload")));
        }
    }
}
