package com.tacz.guns.client.tooltip;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.google.common.collect.Lists;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.inventory.tooltip.BlockItemTooltip;
import com.tacz.guns.mcshim.GuiGraphics;

/**
 * 方块物品 tooltip 的 1.7.10 客户端实现。
 *
 * <h2>类角色映射（1.20.1 → 1.7.10）</h2>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/tooltip/ClientBlockItemTooltip.java}
 *
 * <pre>
 * public class ClientBlockItemTooltip implements ClientTooltipComponent
 * </pre>
 *
 * 由 {@code RegisterClientTooltipComponentFactoriesEvent} 把 {@code BlockItemTooltip} 这个
 * {@code TooltipComponent} 注册成它的渲染器；1.20 的 tooltip 排版器按
 * {@code getHeight()} / {@code getWidth(Font)} 预留空间，再回调 {@code renderText} / {@code renderImage}。
 *
 * <p>
 * 1.7.10 <b>没有</b> {@code TooltipComponent} / {@code ClientTooltipComponent}，也没有
 * {@code net.minecraft.client.gui.screens.inventory.tooltip.*}；物品提示只有
 * {@code Item#addInformation(ItemStack, EntityPlayer, List, boolean)} 这一条<b>纯文本</b>通道。
 * 因此本类：
 * <ul>
 * <li><b>去掉</b> {@code implements ClientTooltipComponent}，退化成普通类；</li>
 * <li><b>保留</b>上游同名方法 {@link #getHeight()} / {@link #getWidth(FontRenderer)} / {@link #renderText} /
 * {@link #renderImage}，语义与量纲不变（{@code Font} → {@link FontRenderer}；
 * {@code Matrix4f} + {@code MultiBufferSource} → 无，1.7.10 是固定管线立即绘制）；</li>
 * <li>新增 1.7.10 专属出口 {@link #appendHoverText(List)} / {@link #getLegacyTooltipLines()}：
 * 把上游的绘制结果折算成 tooltip 行，供 {@code addInformation} 或
 * {@link GuiGraphics#renderComponentTooltip} 使用。</li>
 * </ul>
 *
 * <p>
 * <b>注意</b>：弹药库方块物品（{@code item/AmmoStorageBlockItem}）<b>不</b>经过本类 —— 它的
 * 「内容预览」由 {@code client/event/TooltipEvent#ammoStorageTooltip} 直接产出文本行
 * （读物品 NBT {@code BlockEntityTag.Entries}，最多 5 条 + "and N more..."，与上游
 * {@code AmmoStorageBlockItem#getTooltipImage} 等价；只缺每条目左侧的 16×16 图标，属位图降级）。
 */
public class ClientBlockItemTooltip {

    private final ResourceLocation blockId;
    private final List<IChatComponent> components = Lists.newArrayList();
    private @Nullable IChatComponent packInfo;

    public ClientBlockItemTooltip(BlockItemTooltip tooltip) {
        this.blockId = tooltip.getBlockId();
        this.addText();
        this.addPackInfo();
    }

    private void addPackInfo() {
        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(blockId);
        if (packInfoObject != null) {
            // 空安全：getName() 可能为 null（第三方包显式写 `"name": null`），见 PackInfoText 类注释。
            packInfo = PackInfoText.name(packInfoObject, blockId.getResourceDomain())
                .setChatStyle(
                    new ChatStyle().setColor(EnumChatFormatting.BLUE)
                        .setItalic(Boolean.TRUE));
        }
    }

    /** 对应上游 {@code getHeight()}，1.7.10 侧同样返回「像素」。 */
    public int getHeight() {
        return components.size() * 10 + (packInfo != null ? 16 : 0);
    }

    /** 对应上游 {@code getWidth(Font)}；1.20 {@code Font} → 1.7.10 {@link FontRenderer}。 */
    public int getWidth(FontRenderer font) {
        int[] width = new int[] { 0 };
        if (packInfo != null) {
            width[0] = Math.max(width[0], stringWidth(font, packInfo) + 4);
        }
        components.forEach(c -> width[0] = Math.max(width[0], stringWidth(font, c)));
        return width[0];
    }

    /**
     * 对应上游 {@code renderText(Font, int, int, Matrix4f, MultiBufferSource.BufferSource)}。
     *
     * <p>
     * 1.7.10 没有 {@code Matrix4f} 与 {@code MultiBufferSource}，位置直接由 {@code pX/pY} 决定，
     * 文字走 {@link FontRenderer#drawString(String, int, int, int)}。
     */
    public void renderText(FontRenderer font, int pX, int pY) {
        int yOffset = pY;
        for (IChatComponent component : this.components) {
            font.drawString(component.getFormattedText(), pX, yOffset, 0xffaa00);
            yOffset += 10;
        }
        // 枪包名
        if (packInfo != null) {
            font.drawString(this.packInfo.getFormattedText(), pX, yOffset + 6, 0xffffff);
        }
    }

    /**
     * 对应上游 {@code renderImage(Font, int, int, GuiGraphics)}。
     *
     * <p>
     * 上游这次实现本来就是空方法（方块物品没有图标块），1.7.10 侧保持空实现。
     */
    public void renderImage(FontRenderer font, int mouseX, int mouseY, GuiGraphics gui) {}

    /**
     * 1.7.10 桥接：把本组件折算成 tooltip 文本行。
     *
     * <p>
     * 上游只有「{@code getHeight()} 预留空间 + {@code renderText} 自绘」这一条路径；1.7.10 的 tooltip 是
     * 由 {@code GuiScreen#drawHoveringText} 统一排版的纯文本行，因此这里产出与 {@code renderText}
     * 完全同序的行（每个组件一行、最后是枪包名），颜色已写进
     * {@link IChatComponent} 的 {@link ChatStyle}。
     *
     * @param tooltip 追加目标（1.7.10 {@code Item#addInformation} 的签名是 {@code List<String>}，
     *                需要字符串时请用 {@link #getLegacyTooltipLines()}）
     */
    public void appendHoverText(List<IChatComponent> tooltip) {
        tooltip.addAll(this.components);
        if (packInfo != null) {
            tooltip.add(packInfo);
        }
    }

    /** {@link #appendHoverText(List)} 的 {@code List<String>} 形态，直接可用于 {@code addInformation}。 */
    public List<String> getLegacyTooltipLines() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int maxLineWidth = TooltipLineUtils.maxLineWidth();
        List<String> lines = Lists.newArrayList();
        for (IChatComponent component : this.components) {
            // 枪包作者写的描述可能是一整行长文本（上游 1.20 靠 tooltip 排版器自动收进屏幕，
            // 1.7.10 只有 drawHoveringText 的纯文本行）→ 按屏幕宽度折行，避免横向出屏。
            TooltipLineUtils.addWrapped(lines, font, component, maxLineWidth);
        }
        TooltipLineUtils.addWrapped(lines, font, this.packInfo, maxLineWidth);
        return lines;
    }

    /** 1.7.10 的 {@code Font} 没有 {@code width(...)}，等价物是 {@link FontRenderer#getStringWidth(String)}。 */
    private static int stringWidth(FontRenderer font, @Nullable IChatComponent component) {
        return component == null ? 0 : font.getStringWidth(component.getFormattedText());
    }

    private void addText() {
        TimelessAPI.getClientBlockIndex(blockId)
            .ifPresent(index -> {
                @Nullable
                String tooltipKey = index.getTooltipKey();
                if (tooltipKey != null) {
                    // 1.20 I18n.get(key) → 1.7.10 StatCollector.translateToLocal(key)
                    String text = StatCollector.translateToLocal(tooltipKey);
                    // 与 ClientAttachmentItemTooltip 相同的 1.7.10 修正（见那边的长注释）：
                    // .lang 的换行是字面 `\n` 两字符（Locale#loadLocaleData 不处理转义），
                    // 上游的 split("\n")（真换行）在 1.7.10 是空操作。两种形态都接受。
                    String[] split = text.split("\\\\n|\\r?\\n");
                    Arrays.stream(split)
                        .forEach(
                            s -> components.add(
                                new ChatComponentText(s)
                                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY))));
                }
            });
    }
}
