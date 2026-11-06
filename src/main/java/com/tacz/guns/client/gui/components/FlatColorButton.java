package com.tacz.guns.client.gui.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.tacz.guns.mcshim.GuiGraphics;

/**
 * 扁平配色按钮。
 *
 * <p>
 * 上游继承 1.20 的 {@code net.minecraft.client.gui.components.Button}，本类改为
 * {@link GuiButton}：
 * <ul>
 * <li>构造期的 {@code Button.builder(...).bounds(...).build()} / {@code OnPress} 回调
 * 改为「构造时传入 {@code Consumer<GuiButton>}」，并在 {@link #mousePressed} 里触发
 * （1.7.10 的 {@code GuiScreen#actionPerformed} 需要屏幕侧按 id 分派，
 * 而这里的每个按钮回调都不同，所以直接在按钮内部触发）。</li>
 * <li>{@code renderWidget(GuiGraphics,...)} 改为 {@link #drawButton(Minecraft, int, int)}。</li>
 * <li>文本提示（tooltip）不能在 {@code drawButton} 里画（会被后画的按钮盖住），
 * 由屏幕在全部控件画完后调用 {@link #renderToolTip}。</li>
 * </ul>
 */
public class FlatColorButton extends GuiButton {

    private boolean isSelect = false;
    private List<IChatComponent> tooltips;
    private final Consumer<GuiButton> onPress;
    /** 上一帧鼠标是否悬停（{@link GuiButton#field_146123_n} 由父类 drawButton 维护，这里自算）。 */
    private boolean hovered;

    public FlatColorButton(int x, int y, int width, int height, IChatComponent message, Consumer<GuiButton> onPress) {
        super(0, x, y, width, height, message == null ? "" : message.getFormattedText());
        this.onPress = onPress;
    }

    public FlatColorButton setTooltips(String key) {
        this.tooltips = Collections.singletonList(new ChatComponentTranslation(key));
        return this;
    }

    public FlatColorButton setTooltips(List<IChatComponent> tooltips) {
        this.tooltips = tooltips;
        return this;
    }

    public FlatColorButton setTooltips(IChatComponent... tooltips) {
        List<IChatComponent> list = new ArrayList<>();
        Collections.addAll(list, tooltips);
        this.tooltips = list;
        return this;
    }

    public void setSelect(boolean select) {
        this.isSelect = select;
    }

    public boolean isSelected() {
        return this.isSelect;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    /**
     * 在屏幕所有控件绘制完成后调用，画出悬停文本提示。
     *
     * <p>
     * 上游签名是 {@code renderToolTip(GuiGraphics, Screen, int, int)}；1.7.10 用 {@link GuiScreen}。
     */
    public void renderToolTip(GuiGraphics graphics, GuiScreen screen, int mouseX, int mouseY) {
        if (this.hovered && this.tooltips != null && !this.tooltips.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (IChatComponent component : this.tooltips) {
                lines.add(component == null ? "" : component.getFormattedText());
            }
            // 1.7.10 的 GuiScreen#drawHoveringText 是 protected，这里走 GuiGraphics 里同算法的公开实现
            graphics.drawHoveringText(lines, mouseX, mouseY, Minecraft.getMinecraft().fontRenderer);
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
        int fill = this.isSelect ? 0xAF3A3A3A : 0xAF222222;
        graphics.fillGradient(
            this.xPosition,
            this.yPosition,
            this.xPosition + this.width,
            this.yPosition + this.height,
            fill,
            fill);
        if (this.hovered) {
            int border = 0xFFF3EFE0;
            graphics.fillGradient(
                this.xPosition,
                this.yPosition + 1,
                this.xPosition + 1,
                this.yPosition + this.height - 1,
                border,
                border);
            graphics.fillGradient(
                this.xPosition,
                this.yPosition,
                this.xPosition + this.width,
                this.yPosition + 1,
                border,
                border);
            graphics.fillGradient(
                this.xPosition + this.width - 1,
                this.yPosition + 1,
                this.xPosition + this.width,
                this.yPosition + this.height - 1,
                border,
                border);
            graphics.fillGradient(
                this.xPosition,
                this.yPosition + this.height - 1,
                this.xPosition + this.width,
                this.yPosition + this.height,
                border,
                border);
        }
        drawTrimmedCentered(
            graphics,
            mc.fontRenderer,
            this.xPosition + this.width / 2,
            this.yPosition + (this.height - 8) / 2,
            // 上游 FlatColorButton#renderWidget 固定传 0xF3EFE0（不区分 active），见上游 :57
            0xF3EFE0);
    }

    /** 对应 1.20 {@code AbstractButton#renderScrollingString}：这里降级为「超宽时截断后居中」。 */
    private void drawTrimmedCentered(GuiGraphics graphics, FontRenderer font, int centerX, int y, int color) {
        String text = this.displayString == null ? "" : this.displayString;
        // 1.7.10 修正（显示错误）：上游 renderScrollingString 是「裁剪窗口里滚动」，
        // 只要文字比按钮窄就一定会完整显示。这里原先无条件用 `width - 4` 做上限，
        // 于是 GunSmithTableScreen 的 9×9 过滤按钮（文字 "F"，宽 6px > 9-4=5）被
        // trimStringToWidth 截成空串 → 按钮上永远看不到字。
        // 改成「装得下就原样画，装不下才截断」。
        int textWidth = font.getStringWidth(text);
        String trimmed = textWidth <= this.width ? text : font.trimStringToWidth(text, Math.max(0, this.width - 4));
        graphics.drawCenteredString(font, new ChatComponentText(trimmed), centerX, y, color);
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
}
