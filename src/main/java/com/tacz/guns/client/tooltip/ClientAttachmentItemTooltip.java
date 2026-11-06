package com.tacz.guns.client.tooltip;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.apache.commons.lang3.StringUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.inventory.tooltip.AttachmentItemTooltip;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.resource.modifier.custom.RecoilPushModifier;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilPushData;
import com.tacz.guns.util.VirtualOemAttachment;

/**
 * 配件 tooltip 的 1.7.10 客户端实现。
 *
 * <h2>类角色映射（1.20.1 → 1.7.10）</h2>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/tooltip/ClientAttachmentItemTooltip.java}
 *
 * <pre>
 * public class ClientAttachmentItemTooltip implements ClientTooltipComponent
 * </pre>
 *
 * 上游由 {@code RegisterClientTooltipComponentFactoriesEvent} 把 {@code AttachmentItemTooltip} 注册成
 * 它的渲染器；1.20 的 tooltip 排版器按 {@code getHeight()} / {@code getWidth(Font)} 预留空间，
 * 再回调 {@code renderText} / {@code renderImage}。本类是五类 tooltip 里唯一带「图片块」的：
 * 按住 Shift 时会在文字下方画一条高亮条 + 16 列「受支持枪械」图标网格。
 *
 * <p>
 * 1.7.10 <b>没有</b> {@code TooltipComponent} / {@code ClientTooltipComponent} 体系，
 * 物品提示只有 {@code Item#addInformation(ItemStack, EntityPlayer, List, boolean)} 这条纯文本通道。
 * 因此本类：
 * <ul>
 * <li><b>去掉</b> {@code implements ClientTooltipComponent}，变成普通类；</li>
 * <li><b>保留</b>上游的 {@link #getHeight()} / {@link #getWidth(FontRenderer)} / {@link #renderText} /
 * {@link #renderImage}（{@code Font} → {@link FontRenderer}；{@code Matrix4f} +
 * {@code MultiBufferSource.BufferSource} 在 1.7.10 无对应，改为直接绘制；{@code Screen#hasShiftDown()}
 * → {@link GuiScreen#isShiftKeyDown()}）；</li>
 * <li>新增 1.7.10 专属出口 {@link #appendHoverText(List)} / {@link #getLegacyTooltipLines()}：
 * Shift 展开时在文本行里预留网格区域（空行 + 空白撑宽行），再由 {@link AttachmentGunGridOverlay}
 * 在 {@code DrawScreenEvent.Post} 补画图标网格。</li>
 * </ul>
 */
public class ClientAttachmentItemTooltip {

    private static final String BUILTIN_ATTACHMENT_TOOLTIP_KEY = "tooltip.tacz.attachment.builtin";

    private static final Cache<ResourceLocation, List<ItemStack>> CACHE = CacheBuilder.newBuilder()
        .expireAfterAccess(5, TimeUnit.SECONDS)
        .build();

    /** 原版 16 色的 RGB 值，用于把上游的 RGB 颜色近似到 {@link EnumChatFormatting}（见 {@link #nearestFormatting}）。 */
    private static final int[] VANILLA_COLOR_RGB = new int[] { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000,
        0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    private final ResourceLocation attachmentId;
    private final List<IChatComponent> components = Lists.newArrayList();
    private final IChatComponent tips = new ChatComponentTranslation("tooltip.tacz.attachment.yaw.shift");
    private final IChatComponent support = new ChatComponentTranslation("tooltip.tacz.attachment.yaw.support");
    private @Nullable IChatComponent packInfo;
    private List<ItemStack> showGuns = Lists.newArrayList();
    private final @Nullable ItemStack attachment;
    /** Shift 展开时的图标网格布局；{@code null} 表示本帧不画网格（纯文本行）。 */
    private @Nullable AttachmentGunGridOverlay.GridLayout gunGridLayout;

    public ClientAttachmentItemTooltip(AttachmentItemTooltip tooltip) {
        this.attachmentId = tooltip.getAttachmentId();
        this.attachment = tooltip.getAttachmentItem();
        this.addText(tooltip.getType());
        this.loadShowGuns();
        this.addPackInfo();
    }

    private void addPackInfo() {
        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(attachmentId);
        if (packInfoObject != null) {
            // 空安全：getName() 可能为 null（第三方包显式写 `"name": null`），见 PackInfoText 类注释。
            packInfo = PackInfoText.name(packInfoObject, attachmentId.getResourceDomain())
                .setChatStyle(
                    new ChatStyle().setColor(EnumChatFormatting.BLUE)
                        .setItalic(Boolean.TRUE));
        }
    }

    private static List<ItemStack> getAllAllowGuns(List<ItemStack> output, ResourceLocation attachmentId) {
        ItemStack attachment = AttachmentItemBuilder.create()
            .setId(attachmentId)
            .build();
        TimelessAPI.getAllCommonGunIndex()
            .forEach(entry -> {
                ResourceLocation gunId = entry.getKey();
                ItemStack gun = GunItemBuilder.create()
                    .setId(gunId)
                    .build();
                // 1.7.10 没有 ItemStack.EMPTY：GunItemBuilder#build 在索引缺失时返回 null
                if (gun == null || !(gun.getItem() instanceof IGun iGun)) {
                    return;
                }
                if (iGun.allowAttachment(gun, attachment)) {
                    output.add(gun);
                }
            });
        return output;
    }

    /** 对应上游 {@code getHeight()}。 */
    public int getHeight() {
        if (!GuiScreen.isShiftKeyDown()) {
            return components.size() * 10 + 28;
        }
        return (showGuns.size() - 1) / 16 * 18 + 50 + components.size() * 10;
    }

    /** 对应上游 {@code getWidth(Font)}；1.20 {@code Font} → 1.7.10 {@link FontRenderer}。 */
    public int getWidth(FontRenderer font) {
        int[] width = new int[] { 0 };
        if (packInfo != null) {
            width[0] = Math.max(width[0], stringWidth(font, packInfo) + 4);
        }
        components.forEach(c -> width[0] = Math.max(width[0], stringWidth(font, c)));
        if (!GuiScreen.isShiftKeyDown()) {
            return Math.max(width[0], stringWidth(font, tips) + 4);
        } else {
            width[0] = Math.max(width[0], stringWidth(font, support) + 4);
        }
        if (showGuns.size() > 15) {
            return Math.max(width[0], 260);
        }
        return Math.max(width[0], showGuns.size() * 16 + 4);
    }

    /**
     * 对应上游 {@code renderText(Font, int, int, Matrix4f, MultiBufferSource.BufferSource)}。
     *
     * <p>
     * 1.7.10 没有批处理缓冲与矩阵栈，位置直接由 {@code pX/pY} 决定。
     */
    public void renderText(FontRenderer font, int pX, int pY) {
        int yOffset = pY;
        for (IChatComponent component : this.components) {
            font.drawString(component.getFormattedText(), pX, yOffset, 0xffaa00);
            yOffset += 10;
        }
        if (!GuiScreen.isShiftKeyDown()) {
            font.drawString(tips.getFormattedText(), pX, pY + 5 + this.components.size() * 10, 0x9e9e9e);
            yOffset += 10;
        } else {
            yOffset += (showGuns.size() - 1) / 16 * 18 + 32;
        }
        // 枪包名
        if (packInfo != null) {
            font.drawString(this.packInfo.getFormattedText(), pX, yOffset + 8, 0xffffff);
        }
    }

    /**
     * 对应上游 {@code renderImage(Font, int, int, GuiGraphics)}：Shift 状态下的高亮条与枪械图标网格。
     *
     * <p>
     * 1.7.10 的 {@link GuiGraphics} 是移植层 shim（{@code fill} / {@code drawString} /
     * {@code renderItem}），方法形态与上游一致，因此这里逻辑原样保留。
     */
    public void renderImage(FontRenderer font, int mouseX, int mouseY, GuiGraphics gui) {
        if (!GuiScreen.isShiftKeyDown()) {
            return;
        }
        int minY = components.size() * 10 + 3;
        int maxX = getWidth(font);
        gui.fill(mouseX, mouseY + minY, mouseX + maxX, mouseY + minY + 11, 0x8F00b0ff);
        gui.drawString(font, support, mouseX + 2, mouseY + minY + 2, 0xe3f2fd);

        for (int i = 0; i < showGuns.size(); i++) {
            ItemStack stack = showGuns.get(i);
            int x = i % 16 * 16 + 2;
            int y = i / 16 * 18 + minY + 15;
            gui.renderItem(stack, mouseX + x, mouseY + y);
        }
    }

    /**
     * 1.7.10 桥接：把本组件折算成 tooltip 文本行。
     *
     * <p>
     * 行序与 {@link #renderText} / {@link #renderImage} 一致：先插件描述行，再 Shift 提示（或受支持枪械），
     * 最后枪包名。Shift 展开时「受支持枪械」不是枪名列表，而是留给
     * {@link AttachmentGunGridOverlay} 补画的图标网格区域（见 {@link #appendGunGridLines}）。
     */
    public void appendHoverText(List<IChatComponent> tooltip) {
        for (String line : getLegacyTooltipLines()) {
            tooltip.add(new ChatComponentText(line));
        }
    }

    /** {@link #appendHoverText(List)} 的 {@code List<String>} 形态，可直接用于 {@code addInformation}。 */
    public List<String> getLegacyTooltipLines() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int maxLineWidth = TooltipLineUtils.maxLineWidth();
        List<String> lines = Lists.newArrayList();
        this.gunGridLayout = null;
        for (IChatComponent component : this.components) {
            TooltipLineUtils.addWrapped(lines, font, component, maxLineWidth);
        }
        if (!GuiScreen.isShiftKeyDown()) {
            TooltipLineUtils.addWrapped(lines, font, tips, maxLineWidth);
        } else {
            TooltipLineUtils.addWrapped(lines, font, support, maxLineWidth);
            if (!this.showGuns.isEmpty()) {
                appendGunGridLines(lines, font, maxLineWidth);
            }
        }
        // 枪包名在网格之后
        TooltipLineUtils.addWrapped(lines, font, packInfo, maxLineWidth);
        return lines;
    }

    /**
     * 生成「受支持枪械」的文本占位，并算出图标网格布局（另见 {@link AttachmentGunGridOverlay}）。
     *
     * <p>
     * 布局公式（像素）：预留空行 {@code R = (18*rows + 14) / 10}（推导见
     * {@link AttachmentGunGridOverlay#reservedLines(int)}）；整框高 {@code 10*总行数 ≤ scaledHeight - 40}，
     * 列数 {@code min(16, (min(行宽上限, 260) - 4) / 16)}。高度放不下时逐行减少，
     * 连一行图标都放不下则退化为「枪名截断成一行」的纯文本摘要。
     */
    private void appendGunGridLines(List<String> lines, FontRenderer font, int maxLineWidth) {
        int total = this.showGuns.size();
        int packLines = this.packInfo != null ? 1 : 0;
        int fixedLines = lines.size() + packLines;
        int maxLines = (TooltipLineUtils.scaledHeight() - 40) / 10;
        // 高亮条要用「支持枪械：」这一行的位置定位；标题被折行（超长翻译/空翻译）时画不了，退化为纯文本
        boolean supportOnOneLine = !lines.isEmpty() && getSupportText().equals(lines.get(lines.size() - 1));
        if (supportOnOneLine) {
            int contentMax = Math.min(maxLineWidth, AttachmentGunGridOverlay.MAX_WIDTH);
            int columns = Math.max(1, Math.min(AttachmentGunGridOverlay.COLUMNS, (contentMax - 4) / 16));
            for (int rows = (total + columns - 1) / columns; rows >= 1; rows--) {
                int shown = Math.min(total, rows * columns);
                int truncationLines = shown < total ? 1 : 0;
                int reserved = AttachmentGunGridOverlay.reservedLines(rows);
                if (fixedLines + reserved + truncationLines <= maxLines) {
                    this.gunGridLayout = new AttachmentGunGridOverlay.GridLayout(columns, shown, total);
                    // 框宽与上游 getWidth(Font) 对齐：max(文本宽, 实际占用列数*16 + 4)
                    int usedColumns = Math.min(columns, total);
                    String spacer = TooltipLineUtils.spaces(font, usedColumns * 16 + 4);
                    for (int i = 0; i < reserved; i++) {
                        lines.add(spacer);
                    }
                    if (truncationLines > 0) {
                        lines.add(EnumChatFormatting.GRAY + "…… (+" + (total - shown) + ")");
                    }
                    return;
                }
            }
        }
        if (fixedLines + 1 <= maxLines) {
            lines.add(fallbackGunNamesLine(font, maxLineWidth));
        }
    }

    /** 网格放不下时的纯文本兜底：全部枪名拼成一行，超宽按像素截断并标注总数。 */
    private String fallbackGunNamesLine(FontRenderer font, int maxLineWidth) {
        List<String> names = Lists.newArrayList();
        for (ItemStack gun : this.showGuns) {
            names.add(gun == null || gun.getItem() == null ? "?" : gun.getDisplayName());
        }
        String joined = String.join(", ", names);
        if (font.getStringWidth(joined) > maxLineWidth) {
            String suffix = " …… (+" + this.showGuns.size() + ")";
            String trimmed = font.trimStringToWidth(joined, Math.max(0, maxLineWidth - font.getStringWidth(suffix)));
            joined = (trimmed.isEmpty() ? "……" : trimmed) + suffix;
        }
        return EnumChatFormatting.GRAY + joined;
    }

    private void loadShowGuns() {
        try {
            this.showGuns = CACHE.get(attachmentId, () -> getAllAllowGuns(Lists.newArrayList(), attachmentId));
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static String rgbToHex(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return String.format("#%02X%02X%02X", r, g, b);
    }

    /**
     * 把上游的 RGB 颜色近似到 1.7.10 支持的 16 色。
     *
     * <p>
     * TODO(L2): 上游 client/tooltip/ClientAttachmentItemTooltip.java:261,267 用
     * {@code Style.EMPTY.withColor(int rgb)} 给「激光颜色」行上真彩色。1.7.10 的
     * {@code IChatComponent}（ChatStyle）只支持 {@link EnumChatFormatting} 的 16 色，没有 RGB 通道，
     * 此处降级为「取 RGB 距离最近的 16 色之一」；屏幕上显示的颜色会与上游略有偏差，
     * 但行文本里的 {@code #RRGGBB} 仍然是精确值。
     */
    private static EnumChatFormatting nearestFormatting(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        EnumChatFormatting[] values = EnumChatFormatting.values();
        EnumChatFormatting best = EnumChatFormatting.WHITE;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < VANILLA_COLOR_RGB.length; i++) {
            int candidate = VANILLA_COLOR_RGB[i];
            int dr = ((candidate >> 16) & 0xFF) - r;
            int dg = ((candidate >> 8) & 0xFF) - g;
            int db = (candidate & 0xFF) - b;
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = values[i];
            }
        }
        return best;
    }

    /** 本帧要补画的图标网格布局；{@code null} 表示走纯文本行。供 {@link AttachmentGunGridOverlay} 读取。 */
    public @Nullable AttachmentGunGridOverlay.GridLayout getGridLayout() {
        return this.gunGridLayout;
    }

    /** 网格里要画的枪械（{@link #getGridLayout()} 非 null 时才有意义）。 */
    public List<ItemStack> getShowGuns() {
        return this.showGuns;
    }

    /** 「支持枪械：」的本地化文本：同时用于 tooltip 行定位与高亮条文字。 */
    public String getSupportText() {
        return this.support.getFormattedText();
    }

    /** 1.7.10 的 {@code Font} 没有 {@code width(...)}，等价物是 {@link FontRenderer#getStringWidth(String)}。 */
    private static int stringWidth(FontRenderer font, @Nullable IChatComponent component) {
        return component == null ? 0 : font.getStringWidth(component.getFormattedText());
    }

    private void addText(AttachmentType type) {
        if (VirtualOemAttachment.isBuiltinAttachment(attachmentId)) {
            // 上游 renderText 对无样式组件用绘制色 0xffaa00（GOLD），纯文本 tooltip 用 ChatStyle 表达。
            components.add(
                new ChatComponentTranslation(BUILTIN_ATTACHMENT_TOOLTIP_KEY)
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD)));
        }
        TimelessAPI.getClientAttachmentIndex(attachmentId)
            .ifPresent(index -> {
                AttachmentData data = index.getData();

                @Nullable
                String tooltipKey = index.getTooltipKey();
                if (tooltipKey != null) {
                    // 1.20 I18n.get(key) → 1.7.10 StatCollector.translateToLocal(key)
                    String text = StatCollector.translateToLocal(tooltipKey);
                    // **1.7.10 修正（显示错误，P1）**：上游写的是 `text.split("\n")`，
                    // 在 1.20 里 I18n 返回的 JSON 值带**真换行**，split 有效；
                    // 1.7.10 的 .lang 由 Locale#loadLocaleData 逐行读入、不做转义
                    // （Locale.java:106-126），换行是**字面两字符 `\` + `n`**
                    // （裁决见 docs/PORT_STATUS.md 第 39/190 行：client 显示前 split("\\n")）。
                    // 默认枪包 tacz.attachment.<id>.desc 大量含 `\n`
                    // （custom/tacz_default_gun/.../lang/en_US.lang:227,261-266），
                    // 不改正则就会把 `\n` 当正文显示。两种形态都接受，避免二次回归。
                    String[] split = text.split("\\\\n|\\r?\\n");
                    Arrays.stream(split)
                        .forEach(
                            s -> components.add(
                                new ChatComponentText(s)
                                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY))));
                }

                if (data.getModifier()
                    .get(RecoilPushModifier.ID) != null
                    && data.getModifier()
                        .get(RecoilPushModifier.ID)
                        .getValue() instanceof GunRecoilPushData) {
                    components.add(
                        new ChatComponentTranslation("tooltip.tacz.attachment.recoil_push")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
                }

                if (data.hasFlashHider()) {
                    if (data.getFlashHider() < 1.0f) {
                        components.add(
                            new ChatComponentTranslation("tooltip.tacz.attachment.flash_hider.decrease")
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
                    } else if (data.getFlashHider() > 1.0f) {
                        components.add(
                            new ChatComponentTranslation("tooltip.tacz.attachment.flash_hider.increase")
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                    }
                }
                if (data.hasSmokeHider()) {
                    if (data.getSmokeHider() < 1.0f) {
                        components.add(
                            new ChatComponentTranslation("tooltip.tacz.attachment.smoke_hider.decrease")
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
                    } else if (data.getSmokeHider() > 1.0f) {
                        components.add(
                            new ChatComponentTranslation("tooltip.tacz.attachment.smoke_hider.increase")
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                    }
                }

                // 1.7.10 没有 ItemStack.EMPTY：attachment 可能为 null（见 AttachmentItemTooltip 数据类注释）
                final ItemStack attachmentStack = attachment;
                if (attachmentStack != null && attachmentStack.getItem() instanceof IAttachment iAttachment) {
                    TimelessAPI.getClientAttachmentIndex(attachmentId)
                        .ifPresent(attachmentIndex -> {
                            Integer laserColor = null;
                            if (iAttachment.hasCustomLaserColor(attachmentStack)) {
                                laserColor = iAttachment.getLaserColor(attachmentStack);
                            } else if (attachmentIndex.getLaserConfig() != null) {
                                laserColor = attachmentIndex.getLaserConfig()
                                    .getDefaultColor();
                            }
                            if (laserColor != null) {
                                int color = laserColor;
                                components.add(
                                    new ChatComponentTranslation("tooltip.tacz.attachment.laser.color", rgbToHex(color))
                                        .setChatStyle(new ChatStyle().setColor(nearestFormatting(color))));
                            }
                        });
                }

                if (type == AttachmentType.SCOPE) {
                    float[] zoom = index.getZoom();
                    if (zoom != null) {
                        String[] zoomText = new String[zoom.length];
                        for (int i = 0; i < zoom.length; i++) {
                            zoomText[i] = "x" + zoom[i];
                        }
                        String zoomJoinText = StringUtils.join(zoomText, ", ");
                        components.add(
                            new ChatComponentTranslation("tooltip.tacz.attachment.zoom", zoomJoinText)
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD)));
                    }
                }

                if (index.hasRangefinder()) {
                    components.add(
                        new ChatComponentTranslation("tooltip.tacz.attachment.rangefinder")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
                }
                if (index.hasImpactPrediction()) {
                    components.add(
                        new ChatComponentTranslation("tooltip.tacz.attachment.fire_control")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
                }

                String thermalTooltipKey = index.getThermalImagingTooltipKey();
                if (type == AttachmentType.SCOPE && thermalTooltipKey != null) {
                    components.add(
                        new ChatComponentTranslation(thermalTooltipKey)
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
                }
                if (type == AttachmentType.SCOPE && index.hasAmmoCounter()) {
                    components.add(
                        new ChatComponentTranslation("tooltip.tacz.attachment.ammo_counter")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
                }

                if (type == AttachmentType.EXTENDED_MAG) {
                    int magLevel = data.getExtendedMagLevel();
                    if (magLevel == 1) {
                        components.add(
                            new ChatComponentTranslation("tooltip.tacz.attachment.extended_mag_level_1")
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
                    } else if (magLevel == 2) {
                        components.add(
                            new ChatComponentTranslation("tooltip.tacz.attachment.extended_mag_level_2")
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.BLUE)));
                    } else if (magLevel == 3) {
                        components.add(
                            new ChatComponentTranslation("tooltip.tacz.attachment.extended_mag_level_3")
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE)));
                    }
                }

                data.getModifier()
                    .forEach((key, value) -> {
                        List<IChatComponent> result = value.getComponents();
                        components.addAll(result);
                    });
            });
    }
}
