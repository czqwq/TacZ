package com.tacz.guns.client.tooltip;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.FontRenderer;

import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.GuiGraphics;

/**
 * 1.7.10 的 tooltip 绘制补丁：<b>按可用宽度折行 + 1.20 等价的边界 clamp</b>。
 *
 * <h2>为什么需要本类</h2>
 * 1.7.10 原版
 * {@code GuiScreen#drawHoveringText}（{@code build/rfg/minecraft-src/java/net/minecraft/client/gui/GuiScreen.java:156-232}）
 * 与上游 1.20.1 的 tooltip 排版器有两处行为差异：
 * <ol>
 * <li><b>不折行</b>：框宽 = 最长一行的像素宽（{@code GuiScreen.java:164-176}），行文本超出屏幕就直接画出去。
 * 上游 1.20 的 tooltip 组件按 {@code getWidth(Font)} 预留、整体受排版器约束（{@code GuiGraphics#renderTooltipInternal}，
 * {@code tmp/minecraft-src-1.20.1/.../GuiGraphics.java:575-616}），配合 {@code ClientTooltipComponent} 的宽度声明
 * 不会横向出屏；因此这里对最终行表兜底折行（{@link TooltipLineUtils#wrapLines}）。</li>
 * <li><b>无左侧/顶部边界 clamp</b>：{@code GuiScreen.java:187-195} 只有「右侧溢出翻转 + 底部 clamp」：
 * 翻转 {@code j2 -= 28 + k} 后 {@code j2} 在鼠标位于屏幕中/右侧时可以为负（框左半被裁掉）。
 * 上游 1.20.1 {@code DefaultTooltipPositioner#positionTooltip}（{@code DefaultTooltipPositioner.java:22-31}）在翻转后
 * 还会 {@code Math.max(x - 24 - width, 4)}，即框与屏幕左边缘至少留 4 px。本类按同一语义补 clamp
 * （{@link TooltipLineUtils#hoveringTextOrigin}；y 同时补顶部 clamp，1.20 只 clamp 底部）。</li>
 * </ol>
 *
 * <h2>调用点</h2>
 * 由 {@code mixin/client/GuiScreenMixin} 在 {@code GuiScreen#drawHoveringText} 的 HEAD 调用；
 * 返回 {@code true} 表示已由本类画完（调用方取消原方法），{@code false} 表示异常降级、让原版照常画。
 * 这样<b>所有</b>经 {@code GuiScreen#renderToolTip}/{@code drawCreativeTabHoveringText} 的 tooltip
 * （原版背包/创造栏/容器界面/其它模组物品）都走同一条折行 + clamp 路径。
 *
 * <p>
 * 绘制复用 {@link GuiGraphics#drawHoveringText}（1.20 移植层 shim，内部即 1.7.10 原版算法的等价实现），
 * 本类只负责「折行后的行表 + 已 clamp 的坐标」：把最终坐标反推成 shim 的入参（{@code x-12, y+12}），
 * 使 shim 内部的翻转/底部 clamp 都不再触发，从而画在本类算出的位置上。
 */
public final class GuiTooltipRenderer {

    /** 异常只记一次（tooltip 每帧都可能触发，避免刷屏）。 */
    private static boolean loggedFailure;

    private GuiTooltipRenderer() {}

    /**
     * 折行 + clamp 后绘制 tooltip。
     *
     * @param textLines 调用方传入的行表（不就地修改，内部复制）
     * @param mouseX    鼠标屏幕坐标 X
     * @param mouseY    鼠标屏幕坐标 Y
     * @param font      绘制字体
     * @return {@code true} = 已绘制；{@code false} = 未处理（调用方应回退原版实现）
     */
    public static boolean renderHoveringText(@Nullable List<String> textLines, int mouseX, int mouseY,
        @Nullable FontRenderer font) {
        if (textLines == null || textLines.isEmpty() || font == null) {
            return false;
        }
        try {
            List<String> lines = new ArrayList<>(textLines.size());
            for (String line : textLines) {
                lines.add(line == null ? "" : line);
            }
            // 1) 兜底折行（纯空白布局行不折，见 TooltipLineUtils#wrapLines）
            TooltipLineUtils.wrapLines(lines, font, TooltipLineUtils.maxLineWidth());
            // 2) 用折行后的行表复算框宽/框高，再按 1.7.10 公式 + 1.20 clamp 求位置
            int boxWidth = 0;
            for (String line : lines) {
                int width = font.getStringWidth(line);
                if (width > boxWidth) {
                    boxWidth = width;
                }
            }
            int boxHeight = TooltipLineUtils.boxHeight(lines.size());
            GuiGraphics graphics = GuiGraphics.create();
            int[] origin = TooltipLineUtils.hoveringTextOrigin(
                mouseX,
                mouseY,
                boxWidth,
                boxHeight,
                graphics.scaledWidth(),
                graphics.scaledHeight());
            // 3) 反推入参：shim 用 1.7.10 原公式从 (x, y) 计算内容原点，这里让它正好落在 origin 上
            graphics.drawHoveringText(lines, origin[0] - 12, origin[1] + 12, font);
            return true;
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                GunMod.LOGGER.warn("[TACZ] wrapped tooltip render failed, falling back to vanilla drawHoveringText", t);
            }
            return false;
        }
    }
}
