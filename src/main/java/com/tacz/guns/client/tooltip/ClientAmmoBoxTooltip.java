package com.tacz.guns.client.tooltip;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.google.common.collect.Lists;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.inventory.tooltip.AmmoBoxTooltip;
import com.tacz.guns.mcshim.GuiGraphics;

/**
 * 弹药盒 tooltip 的 1.7.10 客户端实现。
 *
 * <h2>类角色映射（1.20.1 → 1.7.10）</h2>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/tooltip/ClientAmmoBoxTooltip.java}
 *
 * <pre>
 * public class ClientAmmoBoxTooltip implements ClientTooltipComponent
 * </pre>
 *
 * 上游由 {@code RegisterClientTooltipComponentFactoriesEvent} 注册为 {@code AmmoBoxTooltip} 的渲染器，
 * 1.20 的 tooltip 排版器按 {@code getHeight()} / {@code getWidth(Font)} 预留空间后回调
 * {@code renderText} / {@code renderImage}。
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
 * <li>新增 1.7.10 专属出口 {@link #appendHoverText(List)} / {@link #getLegacyTooltipLines()}，
 * 把「图标 + 弹药名 + 数量」两行折算成 tooltip 行。</li>
 * </ul>
 */
public class ClientAmmoBoxTooltip {

    private final ItemStack ammo;
    private final IChatComponent count;
    private final IChatComponent ammoName;

    public ClientAmmoBoxTooltip(AmmoBoxTooltip tooltip) {
        this.ammo = tooltip.getAmmo();
        ItemStack ammoBox = tooltip.getAmmoBox();
        if (ammoBox != null && ammoBox.getItem() instanceof IAmmoBox box && box.isCreative(ammoBox)) {
            this.count = new ChatComponentText("∞");
        } else {
            this.count = new ChatComponentTranslation("tooltip.tacz.ammo_box.count", tooltip.getCount());
        }
        // 上游 ItemStack#getHoverName()；1.7.10 的等价物是 ItemStack#getDisplayName()
        this.ammoName = hoverName(this.ammo);
    }

    /**
     * 对应上游 {@code getHeight()}。上游固定 28 像素（16 像素图标 + 两行 10 像素文字）。
     */
    public int getHeight() {
        return 28;
    }

    /** 对应上游 {@code getWidth(Font)}；1.20 {@code Font} → 1.7.10 {@link FontRenderer}。 */
    public int getWidth(FontRenderer font) {
        return Math.max(stringWidth(font, ammoName), stringWidth(font, count)) + 22;
    }

    /**
     * 对应上游 {@code renderText(Font, int, int, Matrix4f, MultiBufferSource.BufferSource)}。
     *
     * <p>
     * 文字横向偏移 {@code +20} 是为左侧 16 像素物品图标预留的位置，与上游一致。
     */
    public void renderText(FontRenderer font, int pX, int pY) {
        font.drawString(ammoName.getFormattedText(), pX + 20, pY + 4, 0xffaa00);
        font.drawString(count.getFormattedText(), pX + 20, pY + 15, 0x666666);
    }

    /**
     * 对应上游 {@code renderImage(Font, int, int, GuiGraphics)}：画出弹药图标。
     *
     * <p>
     * 1.7.10 的 {@link GuiGraphics} 是移植层 shim，内部走
     * {@code RenderItem#renderItemAndEffectIntoGUI}，语义与上游一致。
     */
    public void renderImage(FontRenderer font, int pX, int pY, GuiGraphics guiGraphics) {
        guiGraphics.renderItem(ammo, pX, pY + 5);
    }

    /**
     * 1.7.10 桥接：把本组件折算成 tooltip 文本行（弹药名 + 数量），用于
     * {@code Item#addInformation} 一类纯文本 tooltip。
     *
     * <p>
     * 行序与 {@link #renderText} 一致。
     *
     * <p>
     * TODO(L2): 上游 client/tooltip/ClientAmmoBoxTooltip.java:69-71 的 {@code renderImage} 还会在文字左侧
     * 画一枚 16×16 弹药物品图标。1.7.10 的 {@code List<String>}/{@code List<IChatComponent>} tooltip
     * 没有位图能力，此处不再产出占位行——图标对应的信息（弹药名）本来就在第一行文字里，
     * 因此没有信息损失；需要图标的 GUI 直接调 {@link #renderImage}。
     *
     * @param tooltip 追加目标
     */
    public void appendHoverText(List<IChatComponent> tooltip) {
        tooltip.add(ammoName);
        tooltip.add(count);
    }

    /** {@link #appendHoverText(List)} 的 {@code List<String>} 形态，可直接用于 {@code addInformation}。 */
    public List<String> getLegacyTooltipLines() {
        List<String> lines = Lists.newArrayList();
        lines.add(ammoName.getFormattedText());
        lines.add(count.getFormattedText());
        return lines;
    }

    /** 1.7.10 的 {@code Font} 没有 {@code width(...)}，等价物是 {@link FontRenderer#getStringWidth(String)}。 */
    private static int stringWidth(FontRenderer font, IChatComponent component) {
        return font.getStringWidth(component.getFormattedText());
    }

    /**
     * 1.20 {@code ItemStack#getHoverName()} 的 1.7.10 等价物。
     *
     * <p>
     * 1.7.10 没有 {@code ItemStack.EMPTY}（空栈用 {@code null} 表示），因此这里补了空栈分支；
     * 上游空栈 ({@code ItemStack.EMPTY}) 时 {@code getHoverName()} 得到 {@code Items.AIR} 的名字 "Air"，
     * 此处用同样的字面量回落（1.7.10 的 {@code en_US.lang} 里没有 {@code tile.air.name} 这一条，
     * 走翻译键只会原样显示键名）。
     */
    private static IChatComponent hoverName(@Nullable ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return new ChatComponentText("Air");
        }
        return new ChatComponentText(stack.getDisplayName());
    }
}
