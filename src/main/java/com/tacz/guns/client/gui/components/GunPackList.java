package com.tacz.guns.client.gui.components;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.client.tooltip.PackInfoText;
import com.tacz.guns.mcshim.GuiGraphics;

/**
 * 枪械工作台的枪包/条件过滤器列表。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>{@code ContainerObjectSelectionList} → {@link GuiSlot}（1.7.10 唯一的分页列表基类），
 * {@code addEntry} 的行为改成维护一个 {@code entries} 列表。</li>
 * <li>{@code EditBox} → {@link GuiTextField}。1.7.10 没有 {@code setHint}，
 * 提示文本由 {@link #drawSlot} 在输入框为空时自己画；也没有 {@code setResponder}，
 * 改为在 {@link #keyTyped} 里比较文本是否变化后回调父屏幕。</li>
 * <li>{@code AbstractButton} 复选框 → 自定义 {@link Checkbox}（继承 {@link GuiButton}）。</li>
 * <li>1.7.10 无 narration，{@code updateWidgetNarration} 直接删除。</li>
 * </ul>
 */
public class GunPackList extends GuiSlot {

    private final Minecraft minecraft;
    private final GunSmithTableScreen parent;
    private final List<Checkbox> gunPackList = new ArrayList<>();
    private final Set<String> selectedNamespaces = new HashSet<>();
    private final Checkbox byHandCheckbox;
    private final Checkbox allCheckbox;
    private final GuiTextField byName;
    /** 行内容：{@link GuiTextField} 或 {@link Checkbox}。 */
    private final List<Object> entries = new ArrayList<>();
    private boolean clearingFilters;
    /** 事件路径已派发点击、需丢弃同帧 {@link GuiSlot} 轮询重复派发（见 {@link #mouseClicked(int, int)}）。 */
    private boolean pollClickSuppressed;

    public GunPackList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight,
        Map<ResourceLocation, List<ResourceLocation>> recipes, GunSmithTableScreen parent) {
        super(minecraft, width, height, top, bottom, itemHeight);
        this.minecraft = minecraft;
        this.parent = parent;
        Set<String> namespaces = new HashSet<>();
        for (List<ResourceLocation> entry : recipes.values()) {
            entry.forEach((resourceLocation) -> namespaces.add(resourceLocation.getResourceDomain()));
        }

        this.byName = new GuiTextField(minecraft.fontRenderer, 3, 0, 94, 10);
        this.byName.setMaxStringLength(256);
        this.byName.setEnableBackgroundDrawing(false);
        this.entries.add(this.byName);

        this.byHandCheckbox = new Checkbox(
            0,
            0,
            10,
            10,
            new ChatComponentTranslation("gui.tacz.gun_smith_table.filter.handgun"),
            false) {

            @Override
            public void onPress() {
                super.onPress();
                parent.initGui();
                parent.setIndexPage(0);
            }
        };
        this.entries.add(this.byHandCheckbox);

        this.allCheckbox = new Checkbox(
            0,
            0,
            10,
            10,
            new ChatComponentTranslation("gui.tacz.gun_smith_table.filter.all"),
            true) {

            @Override
            public void onPress() {
                super.onPress();
                gunPackList.forEach((checkbox) -> checkbox.selected = this.selected);
                updateSelectedNamespaces();
            }
        };
        this.entries.add(this.allCheckbox);

        for (String namespace : namespaces) {
            PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(namespace);
            // 1.7.10 防御：packInfo.getName() 可能为 null（第三方包 gunpack_info.json 显式写 `"name": null`
            // 会被 gson 覆盖成 null），而 Checkbox 构造器里立刻调 message.getFormattedText()，
            // 那会走 ChatComponentTranslation#initializeFromFormat(null) → NPE → 整个工作台界面开不出来。
            // 空安全回落顺序见 PackInfoText 的类注释（与 CreativePackFilters 的既有处理一致）。
            IChatComponent name = PackInfoText.name(packInfo, namespace);

            Checkbox checkbox = new Checkbox(0, 0, 10, 10, name, namespace, true) {

                @Override
                public void onPress() {
                    super.onPress();
                    allCheckbox.selected = gunPackList.stream()
                        .allMatch(Checkbox::selected);
                    updateSelectedNamespaces();
                }
            };
            this.gunPackList.add(checkbox);
            this.selectedNamespaces.add(namespace);
            this.entries.add(checkbox);
        }
    }

    public String getSearchText() {
        return this.byName.getText();
    }

    public boolean isByHandSelected() {
        return this.byHandCheckbox.selected;
    }

    public void setByHandSelected(boolean selected) {
        this.byHandCheckbox.selected = selected;
    }

    /** Clears user filters without rebuilding the parent screen. */
    public void clearFilters() {
        this.byHandCheckbox.selected = false;
        this.allCheckbox.selected = true;
        this.selectedNamespaces.clear();
        for (Checkbox checkbox : this.gunPackList) {
            checkbox.selected = true;
            this.selectedNamespaces.add(checkbox.getId());
        }
        this.clearingFilters = true;
        try {
            this.byName.setText("");
        } finally {
            this.clearingFilters = false;
        }
    }

    public Set<String> namespaceList() {
        return this.selectedNamespaces;
    }

    public void updateSelectedNamespaces() {
        this.selectedNamespaces.clear();
        this.gunPackList.forEach((checkbox) -> {
            if (checkbox.selected) {
                this.selectedNamespaces.add(checkbox.getId());
            }
        });
        this.parent.initGui();
        this.parent.setIndexPage(0);
    }

    /** 对应 1.20 {@code updateSize(width, height, y0, y1)}。 */
    public void updateSize(int width, int height, int top, int bottom) {
        this.func_148122_a(width, height, top, bottom);
    }

    /** 对应 1.20 {@code setLeftPos(int)}。 */
    public void setLeftPos(int left) {
        this.setSlotXBoundsFromLeft(left);
    }

    /**
     * 把键盘输入转发给搜索框。
     *
     * @return 是否消耗了本次输入
     */
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!this.byName.isFocused()) {
            return false;
        }
        String before = this.byName.getText();
        if (this.byName.textboxKeyTyped(typedChar, keyCode)) {
            if (!this.clearingFilters && !before.equals(this.byName.getText())) {
                // 对应 1.20 EditBox#setResponder
                this.parent.initGui();
                this.parent.setIndexPage(0);
            }
            return true;
        }
        return false;
    }

    public void updateCursorCounter() {
        this.byName.updateCursorCounter();
    }

    /** 对应 1.20 点击列表以外控件时 {@code EditBox} 失焦（1.7.10 的 {@link GuiTextField} 不会自动失焦）。 */
    public void clearSearchFocus() {
        this.byName.setFocused(false);
    }

    /**
     * 事件驱动的行点击（对应 1.20 {@code Screen#mouseClicked} → 列表子控件的派发）。
     *
     * <p>
     * <b>1.7.10 修正（P2，真 bug）</b>：{@link GuiSlot#drawScreen} 里的点击判定用的是
     * {@code k1 = this.width / 2 - this.getListWidth() / 2}（<b>没有加 {@code this.left}</b>），
     * 而本列表通过 {@code setLeftPos(guiLeft)} 挂在屏幕中部（left≈(width-344)/2，通常 &gt;134）⇒
     * 行点击的 x 判定（要求绝对 mouseX ∈ [0,134]）永远不成立，搜索框/复选框**点不动**。
     * 1.7.10 原版里带 left 偏移的列表（如资源包界面）都是走 {@code GuiListExtended#func_148179_a}
     * 这条事件路径、而不是 drawScreen 轮询，所以原版没暴露该问题。
     *
     * <p>
     * 这里按 {@code func_148124_c}（正确带 {@code this.left} 的命中计算）派发一次 {@code elementClicked}；
     * 同帧 {@code GuiSlot#drawScreen} 的轮询在小窗口（guiLeft&lt;134）下可能再派发一次，用
     * {@link #pollClickSuppressed} 去重。
     *
     * @return 是否消费了本次点击
     */
    public boolean mouseClicked(int mouseX, int mouseY) {
        int row = this.func_148124_c(mouseX, mouseY);
        if (row < 0) {
            return false;
        }
        this.pollClickSuppressed = false;
        this.elementClicked(row, false, mouseX, mouseY);
        this.pollClickSuppressed = true;
        return true;
    }

    // ================================================================================================
    // GuiSlot 实现
    // ================================================================================================

    @Override
    protected int getSize() {
        return this.entries.size();
    }

    @Override
    protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        // 已被事件路径的 mouseClicked 处理过 → 丢弃同帧轮询的重复派发
        if (this.pollClickSuppressed) {
            this.pollClickSuppressed = false;
            return;
        }
        Object entry = this.entries.get(index);
        if (entry instanceof Checkbox checkbox) {
            // 对应 1.20 Screen#mouseClicked 把焦点切给被点控件：点复选框时搜索框失焦，
            // 否则后续键盘输入会继续灌进搜索框（1.7.10 GuiTextField 不会自动失焦）。
            this.byName.setFocused(false);
            checkbox.mousePressed(this.minecraft, mouseX, mouseY);
        } else if (entry instanceof GuiTextField field) {
            field.mouseClicked(mouseX, mouseY, 0);
        }
    }

    @Override
    protected boolean isSelected(int index) {
        return false;
    }

    @Override
    protected void drawContainerBackground(Tessellator tessellator) {
        // 1.7.10 GuiSlot 默认会铺一层 dirt 背景；本列表要透在枪械工作台面板上，故不画
    }

    @Override
    protected void drawBackground() {
        // 背景在 drawScreen 里一次画完（见下）
    }

    @Override
    protected void drawSlot(int index, int x, int y, int height, Tessellator tessellator, int mouseX, int mouseY) {
        Object entry = this.entries.get(index);
        if (entry instanceof GuiTextField field) {
            field.xPosition = x;
            field.yPosition = y;
            field.drawTextBox();
            // 1.7.10 GuiTextField 没有 setHint，这里自己画提示；
            // 与 1.20 EditBox#renderWidget 一致：仅「空且未聚焦」时显示（EditBox.java:407-408），
            // 否则光标会叠在提示文字上。颜色用 EditBox 的 hint 色 0xFFA0A0A0。
            if (field.getText()
                .isEmpty() && !field.isFocused()) {
                GuiGraphics graphics = GuiGraphics.create();
                graphics.drawString(
                    this.minecraft.fontRenderer,
                    new ChatComponentTranslation("gui.tacz.gun_smith_table.filter.search"),
                    x + 1,
                    y + 1,
                    0xFFA0A0A0);
            }
        } else if (entry instanceof Checkbox checkbox) {
            checkbox.xPosition = x;
            checkbox.yPosition = y;
            checkbox.drawButton(this.minecraft, mouseX, mouseY);
        }
    }

    @Override
    public int getListWidth() {
        return this.width;
    }

    @Override
    protected int getScrollBarX() {
        return this.left + this.width - 2;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GuiGraphics graphics = GuiGraphics.create();
        // 1.7.10 修正（P2，观感/越界）：1.7.10 GuiSlot#drawScreen 会
        // ① 用 overlayBackground 在 [0, top] / [bottom, height] 画 dirt 纹理带（上游 1.20 用
        // setRenderTopAndBottom(false)/setRenderBackground(false) 关掉了这两条）；
        // ② 不裁剪行内容，滚动到中间时首/末行会溢出列表边界（上游用 enableScissor）。
        // 这里把整个 GuiSlot 的绘制裁到列表矩形内，一次性解决两条；列表外的区域保持工作台面板/世界原样。
        // 依据：GuiSlot.java:339-425（overlayBackground/无裁剪）、AbstractSelectionList.java:211,247-248（enableScissor）。
        graphics.enableScissor(this.left, this.top, this.left + this.width, this.bottom);
        try {
            graphics.fill(this.left, this.top, this.left + this.width, this.bottom, 0x80000000);
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            graphics.disableScissor();
            // 事件路径的点击去重标记只在「点击后那一帧的轮询」里有效，帧末清掉避免误吞下一次轮询点击
            this.pollClickSuppressed = false;
        }
    }

    /**
     * 复选框。
     *
     * <p>
     * 1.20 的 {@code AbstractButton} 在这里降级为 {@link GuiButton}：{@code renderWidget} →
     * {@code drawButton}，{@code active} → {@code enabled}，{@code alpha} 恒为 1
     * （1.7.10 没有控件的淡入淡出）。
     */
    public static class Checkbox extends GuiButton {

        /**
         * 1.7.10 没有该贴图，改用几何图元自绘，见 {@link #drawButton(Minecraft, int, int)}。
         *
         * <p>
         * <b>1.7.10 修正（真 bug）</b>：上游 1.20.1 用 Forge 提供的
         * {@code textures/gui/checkbox.png}（32×32，2×2 的 10×10 态：
         * {@code (0,0)} 未选中·未悬停 / {@code (10,0)} 悬停 / {@code (0,10)} 选中 / {@code (10,10)} 选中·悬停；
         * 上游 {@code GunPackList.java:202,252}）。1.7.10 原版的
         * {@code assets/minecraft/textures/gui/} 只有
         * {@code book / demo_background / icons / options_background / resource_packs / stream_indicator / widgets}.png
         * （实测列举，<b>没有</b> {@code checkbox.png}），也没有任何复选框控件，
         * 原样保留会让 {@code TextureManager#bindTexture} 走缺图分支 → 复选框变紫黑方块。
         * 因此本类不再绑定任何贴图，直接用 {@code GuiGraphics#fill} 画方框与勾选块。
         */
        private static final int COLOR_BORDER = 0xFF8B8B8B;
        private static final int COLOR_HOVER_BORDER = 0xFFFFFFFF;
        private static final int COLOR_FILL = 0xFF202020;
        private static final int COLOR_CHECK = 0xFF55FF55;

        protected boolean selected;
        protected final boolean showLabel;
        private String id;

        public Checkbox(int x, int y, int width, int height, IChatComponent message, String id, boolean selected) {
            this(x, y, width, height, message, selected, true);
            this.id = id;
        }

        public Checkbox(int x, int y, int width, int height, IChatComponent message, boolean selected) {
            this(x, y, width, height, message, selected, true);
        }

        public Checkbox(int x, int y, int width, int height, IChatComponent message, boolean selected,
            boolean showLabel) {
            super(0, x, y, width, height, message == null ? "" : message.getFormattedText());
            this.selected = selected;
            this.showLabel = showLabel;
        }

        public String getId() {
            return this.id;
        }

        public void onPress() {
            this.selected = !this.selected;
        }

        public boolean selected() {
            return this.selected;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) {
                return;
            }
            boolean hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
            this.field_146123_n = hovered;
            GuiGraphics graphics = GuiGraphics.create();
            // 上游 graphcis.blit(TEXTURE, x, y, u, v, 10, 10, 32, 32) 的 2×2 状态贴图 → 1.7.10 图元自绘：
            // 10×10 外框（悬停变亮）+ 内填充 + 选中时画勾选块。
            int x = this.xPosition;
            int y = this.yPosition;
            graphics.fill(x, y, x + 10, y + 10, hovered ? COLOR_HOVER_BORDER : COLOR_BORDER);
            graphics.fill(x + 1, y + 1, x + 9, y + 9, COLOR_FILL);
            if (this.selected) {
                graphics.fill(x + 2, y + 2, x + 8, y + 8, COLOR_CHECK);
            }
            if (this.showLabel) {
                graphics.drawString(
                    mc.fontRenderer,
                    this.displayString,
                    this.xPosition + 24,
                    this.yPosition + (this.height - 8) / 2,
                    14737632);
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
    }
}
