package com.tacz.guns.compat.nei;

import net.minecraft.util.StatCollector;

import codechicken.lib.gui.GuiDraw;

/**
 * NEI 枪械相关配方页面的绘制工具。
 *
 * <p>
 * 上游 1.20.1 用 JEI（{@code compat/jei/category/*}）显示 5 类页面：枪械工作台配方、枪械↔弹药、
 * 弹药↔枪械、配件↔枪械、枪械↔配件。1.7.10 没有 JEI，等价物是 NEI（NotEnoughItems），API 完全不同
 * （{@code codechicken.nei.api.IConfigureNEI} + {@code TemplateRecipeHandler}）。
 *
 * <p>
 * <b>为什么不用 NEI 的 {@code recipebg.png}</b>：{@code TemplateRecipeHandler#drawBackground} 默认按
 * 166x65 从贴图偏移 (5,11) 画一块「原版工作台背景」，只适合原版 3x3 配方。本移植的页面既有任意数量材料
 * （最多 11 格），又有 9x2 的物品格列表，因此改为<b>直接用 {@link GuiDraw#drawRect} 画 MC 风格面板/物品槽</b>，
 * 不依赖任何新贴图资源（也避免新增 png 二进制资产）。尺寸保持 NEI 默认 {@code HandlerInfo} 的 166x65，
 * 这样不需要改 NEI 的 handlers.csv / 反射改 HandlerInfo。
 *
 * <p>
 * 注意 {@code codechicken.lib.gui.GuiDraw#drawRect(int, int, int, int, int)} 的参数是
 * <b>(x, y, width, height, color)</b>（不是 x2/y2），见 CCC 源码 {@code GuiDraw.java} 的
 * {@code drawRect → drawGradientRect → gui.drawGradientRect(x, y, x + w, y + h, ...)}。
 */
final class NeiRender {

    /** NEI {@code HandlerInfo.DEFAULT_WIDTH}。 */
    static final int PANEL_WIDTH = 166;
    /** NEI {@code HandlerInfo.DEFAULT_HEIGHT}。 */
    static final int PANEL_HEIGHT = 65;
    /** 一格物品槽的边长（含边框）。 */
    static final int SLOT_SIZE = 18;
    /** 列表类页面的列数（9 列 x 18 = 162，左右各留 2px）。 */
    static final int GRID_COLUMNS = 9;
    /** 列表类页面的第一行/第二行的 y（行高 18，第二行底边 39+18=57）。 */
    static final int GRID_ROW_1_Y = 20;
    static final int GRID_ROW_2_Y = 38;
    /** 列表第一列 x。 */
    static final int GRID_START_X = 2;
    /** 页脚文字（第 x/y 页）的 y。 */
    static final int FOOTER_Y = 57;

    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOR_PANEL_SHADOW = 0xFF555555;
    private static final int COLOR_SLOT_BORDER = 0xFF373737;
    private static final int COLOR_SLOT_INNER = 0xFF8B8B8B;
    private static final int COLOR_TEXT = 0xFF404040;

    private NeiRender() {}

    /** 画 166x{@link #PANEL_HEIGHT} 的 MC 风格面板（带 1px 立体边框）。 */
    static void drawPanel() {
        drawRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT, COLOR_PANEL);
        drawRect(0, 0, PANEL_WIDTH, 1, COLOR_PANEL_HIGHLIGHT);
        drawRect(0, 0, 1, PANEL_HEIGHT, COLOR_PANEL_HIGHLIGHT);
        drawRect(0, PANEL_HEIGHT - 1, PANEL_WIDTH, 1, COLOR_PANEL_SHADOW);
        drawRect(PANEL_WIDTH - 1, 0, 1, PANEL_HEIGHT, COLOR_PANEL_SHADOW);
    }

    /** 画一个 18x18 的凹陷物品槽（与 NEI/原版 GUI 的槽位观感一致）。 */
    static void drawSlot(int x, int y) {
        drawRect(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_SLOT_BORDER);
        drawRect(x + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2, COLOR_SLOT_INNER);
    }

    /** 画一个向右的箭头（材料 → 产物），占 14x12。 */
    static void drawArrow(int x, int y) {
        int color = 0xFF6E6E6E;
        drawRect(x, y + 4, 9, 3, color);
        drawRect(x + 9, y + 2, 3, 7, color);
        drawRect(x + 12, y + 4, 2, 3, color);
    }

    private static void drawRect(int x, int y, int width, int height, int color) {
        GuiDraw.drawRect(x, y, width, height, color);
    }

    /** 居中绘制一行文字（相对面板宽度）。 */
    static void drawTextCentered(String text, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GuiDraw.drawString(text, (PANEL_WIDTH - GuiDraw.getStringWidth(text)) / 2, y, COLOR_TEXT, false);
    }

    /** 右对齐绘制一行文字（页脚页码用）。 */
    static void drawTextRight(String text, int rightX, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GuiDraw.drawString(text, rightX - GuiDraw.getStringWidth(text), y, COLOR_TEXT, false);
    }

    static void drawText(String text, int x, int y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GuiDraw.drawString(text, x, y, COLOR_TEXT, false);
    }

    /** 列表格子的 x 坐标（第 column 列，0 起）。 */
    static int gridX(int column) {
        return GRID_START_X + column * SLOT_SIZE;
    }

    /** 列表格子的 y 坐标（第 index 个格子，0 起，9 个一行）。 */
    static int gridY(int index) {
        return index / GRID_COLUMNS == 0 ? GRID_ROW_1_Y : GRID_ROW_2_Y;
    }

    /** 与上游 {@code Component.translatable(...)} 等价的 1.7.10 本地化。 */
    static String translate(String key, Object... args) {
        return args == null || args.length == 0 ? StatCollector.translateToLocal(key)
            : StatCollector.translateToLocalFormatted(key, args);
    }
}
