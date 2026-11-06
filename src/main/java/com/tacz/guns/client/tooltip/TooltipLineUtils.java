package com.tacz.guns.client.tooltip;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.IChatComponent;

import org.apache.commons.lang3.StringUtils;

/**
 * 1.7.10 tooltip 的行宽工具。
 *
 * <p>
 * 1.20 的 tooltip 组件按 {@code getWidth(Font)} 预留空间；1.7.10 只有
 * {@code GuiScreen#drawHoveringText(List, int, int, FontRenderer)} 的纯文本行，宽度完全由行文本决定。
 * 本类提供「按可用宽度折行」与「撑宽用的空白行」两个入口，保证任何一行都不超过
 * {@code ScaledResolution#getScaledWidth() - 20}。
 */
public final class TooltipLineUtils {

    /** tooltip 框相对内容左右各 3 像素（{@code GuiScreen.java:200-204} 的边框）。 */
    private static final int BOX_PADDING = 6;

    /** 行宽硬上限：与枪械描述折行的 300（上游 {@code ClientGunTooltip} 的 {@code font.split(..., 300)}）一致。 */
    private static final int MAX_LINE_WIDTH = 300;

    private TooltipLineUtils() {}

    /** 当前 GUI 缩放下单行可用的最大像素宽度：{@code min(300, scaledWidth - 20 - 6)}。 */
    public static int maxLineWidth() {
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        return Math.min(MAX_LINE_WIDTH, Math.max(40, resolution.getScaledWidth() - 20 - BOX_PADDING));
    }

    /** 当前 GUI 缩放的逻辑高度。 */
    public static int scaledHeight() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight).getScaledHeight();
    }

    /** 追加一行；超宽时按像素折行（{@code FontRenderer#listFormattedStringToWidth} 保留 § 格式码）。 */
    public static void addWrapped(List<String> lines, FontRenderer font, @Nullable IChatComponent component,
        int maxWidth) {
        if (component != null) {
            addWrapped(lines, font, component.getFormattedText(), maxWidth);
        }
    }

    /** 追加一行；超宽时按像素折行。不超宽时原样一行，避免无谓的样式重排。 */
    public static void addWrapped(List<String> lines, FontRenderer font, @Nullable String formattedText, int maxWidth) {
        if (formattedText == null || formattedText.isEmpty()) {
            return;
        }
        if (font.getStringWidth(formattedText) <= maxWidth) {
            lines.add(formattedText);
            return;
        }
        lines.addAll(font.listFormattedStringToWidth(formattedText, maxWidth));
    }

    /**
     * 生成像素宽度 ≥ {@code width} 的空白行。
     *
     * <p>
     * 用于给「图标网格」预留 tooltip 框宽度：{@code GuiScreen#drawHoveringText} 的框宽 = 最宽行，
     * 纯文本 tooltip 只能靠一行空格把框撑到网格需要的 260 像素。
     */
    public static String spaces(FontRenderer font, int width) {
        int spaceWidth = Math.max(1, font.getStringWidth(" "));
        return StringUtils.repeat(" ", (width + spaceWidth - 1) / spaceWidth);
    }
}
