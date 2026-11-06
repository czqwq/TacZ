package com.tacz.guns.client.tooltip;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.tacz.guns.GunMod;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.RenderSystem;

/**
 * 配件 tooltip 的「受支持枪械」图标网格（对应上游
 * {@code client/tooltip/ClientAttachmentItemTooltip.java:127-142} 的 {@code renderImage}）。
 *
 * <p>
 * 1.7.10 的物品 tooltip 只有 {@code ItemTooltipEvent} → {@code List<String>} 纯文本通道，没有 1.20 的
 * {@code TooltipComponent} 位图能力。本类分两步补画：
 * <ol>
 * <li>{@link #submit}：tooltip 生成时记下 {@link ClientAttachmentItemTooltip} 与文本行（行里已由
 * {@link ClientAttachmentItemTooltip#getLegacyTooltipLines()} 预留出网格区域）；</li>
 * <li>{@link #render}：{@code GuiScreenEvent.DrawScreenEvent.Post}（FML 侧由 {@code TooltipEvent} 转发）时，
 * 按 {@code GuiScreen#drawHoveringText} 的同一公式（{@code GuiScreen.java:178-195}）算出刚画出的框位置，
 * 在预留区域内画高亮条 + 每行 16 个 16×18 的枪械图标。</li>
 * </ol>
 *
 * <p>
 * 未选 mixin：{@code DrawScreenEvent.Post} 在 1.7.10 于 {@code EntityRenderer} 里紧跟在
 * {@code currentScreen.drawScreen(...)} 之后 post（{@code EntityRenderer.java:1136-1138}），对
 * {@code GuiContainer} 来说 tooltip 正是在 {@code drawScreen} 末尾画的（{@code GuiContainer.java:183-187}）。
 */
public final class AttachmentGunGridOverlay {

    /** 网格列数：上游 {@code renderImage} 每行 16 个。 */
    public static final int COLUMNS = 16;
    /** 网格宽度上限：上游 {@code getWidth(Font)} 在枪数 > 15 时封顶 260。 */
    public static final int MAX_WIDTH = 260;

    /** 图标单元宽度（16×16 图标，无间隙）。 */
    private static final int CELL_WIDTH = 16;
    /** 图标行距：上游 {@code y = i / 16 * 18}。 */
    private static final int ROW_HEIGHT = 18;
    /** 高亮条高度：上游 {@code fill(..., mouseY + minY, ..., mouseY + minY + 11, ...)}。 */
    private static final int BAR_HEIGHT = 11;
    /** tooltip 与补画的时效：tooltip 生成与绘制在同一渲染帧内（留 1s 容忍首帧模型加载等卡顿）。 */
    private static final long MAX_AGE_MS = 1000L;

    /** 网格布局：由 tooltip 生成时按当前屏幕算出（列数随屏宽收窄，避免横向溢出）。 */
    public record GridLayout(int columns, int shown, int total) {}

    private record Pending(ItemStack source, ClientAttachmentItemTooltip tooltip, GridLayout layout, List<String> lines,
        long time) {}

    /** 本次待补画的 tooltip；同一帧最多一个。 */
    private static @Nullable Pending pending;
    /** 本帧最后一次 {@code ItemTooltipEvent} 的物品：只有它对应的 tooltip 才是刚画出来的。 */
    private static @Nullable ItemStack lastTooltipStack;
    private static boolean loggedSuccess;
    private static boolean loggedFailure;

    private AttachmentGunGridOverlay() {}

    /**
     * 为 {@code rows} 行图标预留的空文本行数（tooltip 行高 10 像素）。
     *
     * <p>
     * 推导：高亮条下沿 = 支持枪械行 + 2 + 11；第一行图标在其后 4 像素（{@code barTop + 15}），
     * 网格底 = 支持行 + 18*rows + 11；其后要么还有一行（枪包名 / 「(+N)」截断行），要么就是框底 ——
     * 前者要求 {@code 10*R ≥ 18*rows + 3}，后者要求框高再多 2 像素，取两者较大值：
     * {@code R = ceil((18*rows + 5) / 10) = (18*rows + 14) / 10}。
     */
    public static int reservedLines(int rows) {
        return (18 * rows + 14) / 10;
    }

    /** {@code DrawScreenEvent.Pre}：清掉上一帧的残留。 */
    public static void clearFrame() {
        pending = null;
        lastTooltipStack = null;
    }

    /** {@code ItemTooltipEvent} 开始：记录本帧最后一次 tooltip 对应的物品。 */
    public static void beginTooltip(@Nullable ItemStack stack) {
        lastTooltipStack = stack;
    }

    /** tooltip 生成完成：登记待补画的网格。 */
    public static void submit(ItemStack source, ClientAttachmentItemTooltip tooltip, GridLayout layout,
        List<String> lines) {
        pending = new Pending(source, tooltip, layout, lines, Minecraft.getSystemTime());
    }

    /**
     * {@code DrawScreenEvent.Post}：补画网格。
     *
     * <p>
     * 绝不抛异常：该事件在 {@code EntityRenderer} 的 try 块里，异常会变成「渲染界面」崩溃报告。
     */
    public static void render(int mouseX, int mouseY, @Nullable GuiScreen screen) {
        Pending current = pending;
        if (current == null) {
            return;
        }
        pending = null;
        if (screen == null || !GuiScreen.isShiftKeyDown()) {
            return;
        }
        // 本帧之后又生成过别的物品 tooltip ⇒ 刚画的框不是这个 tooltip 的，宁可不画也不乱画
        if (current.source() != lastTooltipStack) {
            return;
        }
        if (Minecraft.getSystemTime() - current.time() > MAX_AGE_MS) {
            return;
        }
        try {
            draw(current, mouseX, mouseY, screen);
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                GunMod.LOGGER.warn("[TACZ] attachment gun grid render failed (skipped)", t);
            }
        }
    }

    private static void draw(Pending current, int mouseX, int mouseY, GuiScreen screen) {
        List<String> lines = current.lines();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        if (font == null) {
            return;
        }
        int maxWidth = 0;
        for (String line : lines) {
            int width = font.getStringWidth(line == null ? "" : line);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        // 与 GuiScreen#drawHoveringText 相同的框定位公式
        int lineCount = lines.size();
        int totalHeight = lineCount > 1 ? 8 + 2 + (lineCount - 1) * 10 : 8;
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + maxWidth > screen.width) {
            x -= 28 + maxWidth;
        }
        if (y + totalHeight + 6 > screen.height) {
            y = screen.height - totalHeight - 6;
        }
        int supportIndex = indexOfLine(
            lines,
            current.tooltip()
                .getSupportText());
        if (supportIndex < 0) {
            return;
        }
        int supportTop = supportIndex == 0 ? y : y + 2 + 10 * supportIndex;
        int barTop = supportIndex == 0 ? supportTop : supportTop - 2;

        GridLayout layout = current.layout();
        List<ItemStack> guns = current.tooltip()
            .getShowGuns();
        int columns = Math.max(1, layout.columns());
        int shown = Math.min(layout.shown(), guns.size());
        GuiGraphics graphics = GuiGraphics.create();
        graphics.setZLevel(300.0F);
        try {
            // 与 GuiScreen#drawHoveringText 的绘制状态一致：tooltip 是关掉深度/光照画上去的，
            // 补画也照做，否则会被此前 GUI 内容写下的深度值挡住、或被光照染色。
            RenderSystem.disableDepthTest();
            RenderSystem.disableLighting();
            graphics.fill(x, barTop, x + maxWidth, barTop + BAR_HEIGHT, 0x8F00B0FF);
            graphics.drawString(
                font,
                current.tooltip()
                    .getSupportText(),
                x + 2,
                barTop + 2,
                0xE3F2FD);
            int firstRowTop = barTop + 15;
            for (int i = 0; i < shown; i++) {
                int iconX = x + (i % columns) * CELL_WIDTH + 2;
                int iconY = firstRowTop + (i / columns) * ROW_HEIGHT;
                // 逐个图标 push/pop：物品自定义渲染器（GunItemRendererWrapper 的 INVENTORY 分支）若漏 pop 矩阵，
                // 不兜底会让后面的图标逐格偏移（创造模式物品栏的同类错位见 r6-itemicon 任务）。
                GL11.glPushMatrix();
                try {
                    graphics.renderItem(guns.get(i), iconX, iconY);
                } finally {
                    GL11.glPopMatrix();
                }
            }
            if (!loggedSuccess) {
                loggedSuccess = true;
                // 只打一次：实机判读「网格是否真的补画了」以及布局是否与屏幕匹配
                GunMod.LOGGER.info(
                    "[TACZ] attachment gun grid drawn: {}/{} guns, {} columns, box=({},{}) {}x{}",
                    shown,
                    layout.total(),
                    columns,
                    x,
                    y,
                    maxWidth,
                    totalHeight);
            }
        } finally {
            restoreGuiState();
        }
    }

    /**
     * 定位「支持枪械：」行。
     *
     * <p>
     * 两边都先去掉格式码再比：{@code IChatComponent#getFormattedText()} 每段末尾会补 {@code §r}
     * （{@code ChatComponentStyle.java:104-119}），而 tooltip 行又被 {@code GuiScreen#renderToolTip}
     * 加了 {@code §7} 前缀（{@code GuiScreen.java:126-136}）。
     */
    private static int indexOfLine(List<String> lines, @Nullable String supportText) {
        if (supportText == null) {
            return -1;
        }
        String plainText = EnumChatFormatting.getTextWithoutFormattingCodes(supportText);
        // 从后往前找：「支持枪械：」行在描述行之后，且它后面不会再出现同样的文本
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line != null && plainText.equals(EnumChatFormatting.getTextWithoutFormattingCodes(line))) {
                return i;
            }
        }
        return -1;
    }

    /** 恢复 {@code GuiScreen#drawHoveringText} 退出时的 GL 状态（{@code GuiScreen.java:225-231}）。 */
    private static void restoreGuiState() {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }
}
