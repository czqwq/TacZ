package com.tacz.guns.client.tooltip;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.google.common.collect.Lists;
import com.tacz.guns.inventory.tooltip.AmmoStorageTooltip;
import com.tacz.guns.mcshim.GuiGraphics;

/**
 * 弹药库 tooltip 的 1.7.10 客户端实现。
 *
 * <h2>类角色映射（1.20.1 → 1.7.10）</h2>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/tooltip/ClientAmmoStorageTooltip.java}
 *
 * <pre>
 * public class ClientAmmoStorageTooltip implements ClientTooltipComponent
 * </pre>
 *
 * 上游由 {@code RegisterClientTooltipComponentFactoriesEvent} 把 {@code AmmoStorageTooltip} 注册成
 * 它的渲染器；1.20 的 tooltip 排版器按 {@code getHeight()} / {@code getWidth(Font)} 预留空间，
 * 再回调 {@code renderText} / {@code renderImage}（每条目 28 像素：16 像素图标 + 两行文字）。
 *
 * <p>
 * 1.7.10 <b>没有</b> {@code TooltipComponent} / {@code ClientTooltipComponent} 体系，
 * 物品提示只有 {@code Item#addInformation(ItemStack, EntityPlayer, List, boolean)} 这条纯文本通道。
 * 因此本类：
 * <ul>
 * <li><b>去掉</b> {@code implements ClientTooltipComponent}，变成普通类；</li>
 * <li><b>保留</b>上游的 {@link #getHeight()} / {@link #getWidth(FontRenderer)} / {@link #renderText} /
 * {@link #renderImage}（{@code Font} → {@link FontRenderer}；{@code Matrix4f} +
 * {@code MultiBufferSource.BufferSource} 在 1.7.10 无对应，改为直接绘制）；</li>
 * <li>新增 1.7.10 专属出口 {@link #appendHoverText(List)} / {@link #getLegacyTooltipLines()}。</li>
 * </ul>
 */
public class ClientAmmoStorageTooltip {

    private static final int ENTRY_HEIGHT = 28;

    /** 上游 {@code getHidden()} 用的翻译键（1.11+ 的原版潜影盒文本）。 */
    private static final String MORE_KEY = "container.shulkerBox.more";

    private final AmmoStorageTooltip tooltip;

    public ClientAmmoStorageTooltip(AmmoStorageTooltip tooltip) {
        this.tooltip = tooltip;
    }

    /** 对应上游 {@code getHeight()}。 */
    public int getHeight() {
        int height = tooltip.getEntries()
            .size() * ENTRY_HEIGHT;
        return tooltip.getHidden() > 0 ? height + 10 : height;
    }

    /** 对应上游 {@code getWidth(Font)}；1.20 {@code Font} → 1.7.10 {@link FontRenderer}。 */
    public int getWidth(FontRenderer font) {
        int width = 0;
        for (AmmoStorageTooltip.Entry entry : tooltip.getEntries()) {
            width = Math.max(width, stringWidth(font, entry.getAmmoName()));
            width = Math.max(width, stringWidth(font, getCount(entry)));
        }
        if (tooltip.getHidden() > 0) {
            width = Math.max(width, stringWidth(font, getHidden()));
        }
        return width + 22;
    }

    /**
     * 对应上游 {@code renderText(Font, int, int, Matrix4f, MultiBufferSource.BufferSource)}。
     *
     * <p>
     * 横向 {@code +20} 偏移是为左侧 16 像素物品图标预留的空间，与上游一致。
     */
    public void renderText(FontRenderer font, int x, int y) {
        int yOffset = y;
        for (AmmoStorageTooltip.Entry entry : tooltip.getEntries()) {
            IChatComponent ammoName = entry.getAmmoName();
            if (ammoName != null) {
                font.drawString(ammoName.getFormattedText(), x + 20, yOffset + 4, 0xffaa00);
            }
            font.drawString(getCount(entry).getFormattedText(), x + 20, yOffset + 15, 0x666666);
            yOffset += ENTRY_HEIGHT;
        }
        if (tooltip.getHidden() > 0) {
            font.drawString(getHidden().getFormattedText(), x, yOffset, 0x9e9e9e);
        }
    }

    /**
     * 对应上游 {@code renderImage(Font, int, int, GuiGraphics)}：逐条目画出弹药图标。
     *
     * <p>
     * 1.7.10 侧走 {@link GuiGraphics#renderItem}（shim，内部 {@code RenderItem}），语义与上游一致。
     */
    public void renderImage(FontRenderer font, int x, int y, GuiGraphics guiGraphics) {
        int yOffset = y;
        for (AmmoStorageTooltip.Entry entry : tooltip.getEntries()) {
            guiGraphics.renderItem(entry.ammo(), x, yOffset + 5);
            yOffset += ENTRY_HEIGHT;
        }
    }

    /**
     * 1.7.10 桥接：把本组件折算成 tooltip 文本行。
     *
     * <p>
     * 行序与 {@link #renderText} 一致：每个条目两行（弹药名、数量），最后是「还有 N 种」提示。
     *
     * <p>
     * TODO(L2): 上游 client/tooltip/ClientAmmoStorageTooltip.java:87-93 的 {@code renderImage} 每条目还会
     * 在文字左侧画一枚 16×16 弹药图标。1.7.10 的 {@code List<String>}/{@code List<IChatComponent>} tooltip
     * 没有位图能力，此处不再产出占位行——图标对应的信息（弹药名）本来就在同一组的第一行文字里，
     * 因此没有信息损失；需要图标的 GUI 直接调 {@link #renderImage}。
     *
     * @param tooltip 追加目标
     */
    public void appendHoverText(List<IChatComponent> tooltip) {
        for (AmmoStorageTooltip.Entry entry : this.tooltip.getEntries()) {
            IChatComponent ammoName = entry.getAmmoName();
            if (ammoName != null) {
                tooltip.add(ammoName);
            }
            tooltip.add(getCount(entry));
        }
        if (this.tooltip.getHidden() > 0) {
            tooltip.add(getHidden());
        }
    }

    /** {@link #appendHoverText(List)} 的 {@code List<String>} 形态，可直接用于 {@code addInformation}。 */
    public List<String> getLegacyTooltipLines() {
        List<String> lines = Lists.newArrayList();
        for (AmmoStorageTooltip.Entry entry : tooltip.getEntries()) {
            lines.add(
                entry.getAmmoName() == null ? ""
                    : entry.getAmmoName()
                        .getFormattedText());
            lines.add(getCount(entry).getFormattedText());
        }
        if (tooltip.getHidden() > 0) {
            lines.add(getHidden().getFormattedText());
        }
        return lines;
    }

    /** 1.7.10 的 {@code Font} 没有 {@code width(...)}，等价物是 {@link FontRenderer#getStringWidth(String)}。 */
    private static int stringWidth(FontRenderer font, @Nullable IChatComponent component) {
        return component == null ? 0 : font.getStringWidth(component.getFormattedText());
    }

    private static IChatComponent getCount(AmmoStorageTooltip.Entry entry) {
        return new ChatComponentTranslation("tooltip.tacz.ammo_box.count", entry.count());
    }

    private IChatComponent getHidden() {
        // TODO(L2): 上游 client/tooltip/ClientAmmoStorageTooltip.java:100 直接用
        // Component.translatable("container.shulkerBox.more", hidden)（原版 1.11+ 潜影盒的「还有 N 项」文本）。
        // 1.7.10 没有潜影盒，因而也没有这条翻译，StatCollector 会原样吐出键名；
        // 此处先用 canTranslate 判断，缺失时退回与上游同样的英文格式串。
        IChatComponent more = StatCollector.canTranslate(MORE_KEY)
            ? new ChatComponentTranslation(MORE_KEY, tooltip.getHidden())
            : new ChatComponentText(String.format("and %d more...", tooltip.getHidden()));
        return more.setChatStyle(new ChatStyle().setItalic(Boolean.TRUE));
    }
}
