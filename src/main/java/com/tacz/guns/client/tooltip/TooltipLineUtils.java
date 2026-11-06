package com.tacz.guns.client.tooltip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
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

    /** tooltip 框与屏幕边缘的最小间距（1.20 {@code DefaultTooltipPositioner.java:24} 的 {@code Math.max(..., 4)}）。 */
    public static final int TOOLTIP_MARGIN = 4;

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

    /**
     * tooltip 框高（像素），公式与 {@code GuiScreen#drawHoveringText} 相同：
     * 第 0 行高 8，之后每行 10，行数 &gt; 1 时再加 2（{@code GuiScreen.java:180-185}）。
     */
    public static int boxHeight(int lineCount) {
        return lineCount > 1 ? 8 + 2 + (lineCount - 1) * 10 : 8;
    }

    /**
     * tooltip 框左上角坐标：先按 1.7.10 {@code GuiScreen#drawHoveringText} 的原始公式算，
     * 再补上 1.20 {@code DefaultTooltipPositioner} 的边界 clamp。
     *
     * <p>
     * 1.7.10 原版只有「右侧溢出翻转 + 底部 clamp」，<b>没有左/上边界 clamp</b>
     * （{@code GuiScreen.java:187-195}）：翻转后 {@code j2 = x + 12 - (28 + 框宽)} 在鼠标位于屏幕
     * 中/右侧时可以为负 —— 框左侧被裁掉。1.20 的 {@code DefaultTooltipPositioner#positionTooltip}
     * （{@code DefaultTooltipPositioner.java:22-31}）在翻转后还会 {@code Math.max(..., 4)}，
     * 这里按同一语义补 clamp（y 同理，1.20 只 clamp 底部，这里多 clamp 顶部以保证框不越出屏幕上沿）。
     *
     * @return {@code {x, y}}（框内容左上角，边框在其外 3~4 像素）
     */
    public static int[] hoveringTextOrigin(int mouseX, int mouseY, int boxWidth, int boxHeight, int scaledWidth,
        int scaledHeight) {
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + boxWidth > scaledWidth) {
            x -= 28 + boxWidth;
        }
        if (y + boxHeight + 6 > scaledHeight) {
            y = scaledHeight - boxHeight - 6;
        }
        return new int[] { Math.max(x, TOOLTIP_MARGIN), Math.max(y, TOOLTIP_MARGIN) };
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
     * 把整个 tooltip 行表就地折行到 {@code maxWidth}（1.7.10 平台兜底）。
     *
     * <p>
     * 1.7.10 的 tooltip 排版器 {@code GuiScreen#drawHoveringText} 的框宽 = 最长行，<b>不做任何换行</b>
     * （{@code GuiScreen.java:156-232}），所以只要有一行超过屏幕就会横向出屏。本方法对<b>最终</b>行表
     * 逐行折行（覆盖 TACZ 富 tooltip、原版物品名、附魔/属性行、自定义 lore、其它模组物品），
     * 与上游 1.20「tooltip 宽度受组件宽度约束」的效果对齐。
     *
     * <p>
     * 纯空白行（r6 给配件图标网格预留的撑宽行 / 空行）不折：它们只参与「框宽/行高」计算。
     */
    public static void wrapLines(List<String> lines, @Nullable FontRenderer font, int maxWidth) {
        if (lines == null || lines.isEmpty() || font == null || maxWidth < 1) {
            return;
        }
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (line == null || line.isEmpty() || isLayoutBlank(line) || font.getStringWidth(line) <= maxWidth) {
                index++;
                continue;
            }
            List<String> wrapped = wrapLine(font, line, maxWidth);
            lines.remove(index);
            lines.addAll(index, wrapped);
            index += wrapped.size();
        }
    }

    /** {@link #wrapLines} 的单行版本：保证返回非空（极端宽度下不推进时原样返回，由调用方按 1 行推进）。 */
    private static List<String> wrapLine(FontRenderer font, String line, int maxWidth) {
        List<String> wrapped = font.listFormattedStringToWidth(line, maxWidth);
        if (wrapped.isEmpty()) {
            return Collections.singletonList(line);
        }
        if (wrapped.size() == 1 && wrapped.get(0)
            .equals(line)) {
            // 理论上不会发生（maxWidth ≥ 40 时 FontRenderer 至少能断开一个字符）；
            // 真发生时直接放弃该行的折行，避免「没推进 → 无限循环」。
            return Collections.singletonList(line);
        }
        return new ArrayList<>(wrapped);
    }

    /** 去掉格式码后全是空白 ⇒ 布局行（撑宽/占高），不参与折行。 */
    private static boolean isLayoutBlank(String line) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(line)
            .trim()
            .isEmpty();
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
