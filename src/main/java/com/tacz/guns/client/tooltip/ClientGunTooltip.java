package com.tacz.guns.client.tooltip;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.google.common.collect.Lists;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.client.input.RefitKey;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.client.resource.pojo.display.gun.AmmoCountStyle;
import com.tacz.guns.client.resource.pojo.display.gun.DamageStyle;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.inventory.tooltip.GunTooltip;
import com.tacz.guns.item.GunTooltipPart;
import com.tacz.guns.item.ModernKineticGunItem;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import com.tacz.guns.util.AttachmentDataUtils;

/**
 * 枪械 tooltip 的 1.7.10 客户端实现（本包最大的一块，水平布局与上游逐行对应）。
 *
 * <h2>类角色映射（1.20.1 → 1.7.10）</h2>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/tooltip/ClientGunTooltip.java}
 *
 * <pre>
 * public class ClientGunTooltip implements ClientTooltipComponent {
 *     public int getHeight()
 *     public int getWidth(Font font)
 *     public void renderText(Font font, int pX, int pY, Matrix4f matrix4f, MultiBufferSource.BufferSource bs)
 *     public void renderImage(Font font, int pX, int pY, GuiGraphics guiGraphics)
 * }
 * </pre>
 *
 * 上游由 {@code RegisterClientTooltipComponentFactoriesEvent} 把 {@code GunTooltip} 注册成它的渲染器；
 * 1.20 的 tooltip 排版器先问 {@code getHeight()/getWidth(Font)} 预留矩形，再回调
 * {@code renderText}（画文字，带 {@code Matrix4f} 与 {@code MultiBufferSource} 批处理）与
 * {@code renderImage}（在左侧画 16 像素弹药图标）。
 *
 * <p>
 * 1.7.10 <b>没有</b> {@code TooltipComponent} / {@code ClientTooltipComponent}，也没有
 * {@code net.minecraft.client.gui.screens.inventory.tooltip.*}；物品提示只有
 * {@code Item#addInformation(ItemStack, EntityPlayer, List, boolean)} 这条<b>纯文本</b>通道。
 * 于是本类：
 * <ul>
 * <li><b>去掉</b> {@code implements ClientTooltipComponent}，退化为普通类（构造器签名
 * {@link #ClientGunTooltip(GunTooltip)} 保持不变，{@code ClientGunTooltip::new} 仍可用作工厂）；</li>
 * <li><b>保留</b>上游的 {@link #getHeight()} / {@link #getWidth(FontRenderer)}：
 * 布局数值（10 像素行高、24/34 像素段高、{@code maxWidth}）一个不变，
 * 只是 {@code Font} → {@link FontRenderer}（两边量纲都是像素）；</li>
 * <li>{@link #renderText} / {@link #renderImage} 保留同名方法，签名改为
 * {@code (FontRenderer, int x, int y)} / {@code (FontRenderer, int x, int y, GuiGraphics)}：
 * 1.7.10 没有 {@code Matrix4f} 与 {@code MultiBufferSource}（固定管线立即绘制），
 * 文字走 {@link FontRenderer#drawString(String, int, int, int)}，图标走
 * {@link GuiGraphics#renderItem}（shim）；行距、缩进（{@code +20} 给图标让位）、颜色全部沿用上游；</li>
 * <li>新增 {@link #appendHoverText(List)} / {@link #getLegacyTooltipLines()}：
 * 把上面两段绘制折算成 tooltip 行，这是 1.7.10 {@code addInformation} 唯一能表达的形态。
 * 上游 {@code renderText} 的「行绘制色」（0xffaa00 / 0x777777 / 0xffffff）在这里折算成
 * {@code ChatStyle} 颜色（见 {@link #getText()} 内注释），文字效果与上游一致；
 * 弹药图标是唯一无法表达的位图元素（其信息＝第一行弹药名，故无信息损失）。</li>
 * </ul>
 */
public class ClientGunTooltip {

    private static final DecimalFormat FORMAT = new DecimalFormat("#.##%");
    private static final DecimalFormat FORMAT_P_D1 = new DecimalFormat("#.#%");
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("#.##");
    private static final DecimalFormat CURRENT_AMMO_FORMAT_PERCENT = new DecimalFormat("0%");

    /**
     * 独头弹标签。
     *
     * <p>
     * TODO(L2): 上游 client/tooltip/ClientGunTooltip.java:42 静态导入
     * {@code ModernKineticGunItem.DefaultPropertyModification.SLUGS}（那里是 {@link ResourceLocation}）。
     * 本仓 item/ModernKineticGunItem.java 里同名常量当前是 {@code String}，
     * 而 {@code AllowAttachmentTagMatcher#matchTag} 需要 {@link ResourceLocation}，
     * 这里用 {@code String.valueOf} 归一后重建，两种形态都指向同一个标签 id {@code tacz:intrinsic/slug}。
     */
    private static final ResourceLocation SLUGS = new ResourceLocation(
        String.valueOf(ModernKineticGunItem.DefaultPropertyModification.SLUGS));

    private final ItemStack gun;
    private final IGun iGun;
    private final CommonGunIndex gunIndex;
    private final @Nullable GunDisplayInstance display;
    private final ItemStack ammo;
    /** 上游是 {@code List<FormattedCharSequence>}；1.7.10 没有该类，用「已带 § 格式码的行」代替。 */
    private @Nullable List<String> desc;
    private IChatComponent ammoName;
    private IChatComponent ammoCountText;
    private @Nullable IChatComponent gunType;
    private IChatComponent damage;
    private IChatComponent armorIgnore;
    private IChatComponent headShotMultiplier;
    private IChatComponent weight;
    private IChatComponent tips;
    private IChatComponent levelInfo;
    private @Nullable IChatComponent packInfo;

    private int maxWidth;

    public ClientGunTooltip(GunTooltip tooltip) {
        this.gun = tooltip.getGun();
        this.iGun = tooltip.getIGun();
        ResourceLocation ammoId = tooltip.getAmmoId();
        this.gunIndex = tooltip.getGunIndex();
        this.display = TimelessAPI.getGunDisplay(gun)
            .orElse(null);
        this.ammo = AmmoItemBuilder.create()
            .setId(ammoId)
            .build();
        this.maxWidth = 0;
        this.getText();
    }

    /** 对应上游 {@code getHeight()}：段高与上游完全一致（10 像素行高 + 段间 4/2 像素）。 */
    public int getHeight() {
        int height = 0;
        if (shouldShow(GunTooltipPart.DESCRIPTION) && this.desc != null) {
            height += 10 * this.desc.size() + 2;
        }
        if (shouldShow(GunTooltipPart.AMMO_INFO)) {
            height += 24;
        }
        if (shouldShow(GunTooltipPart.BASE_INFO)) {
            height += 34;
        }
        if (shouldShow(GunTooltipPart.EXTRA_DAMAGE_INFO)) {
            height += 34;
        }
        if (shouldShow(GunTooltipPart.UPGRADES_TIP)) {
            height += 14;
        }
        if (shouldShow(GunTooltipPart.PACK_INFO)) {
            height += 14;
        }
        return height;
    }

    /** 对应上游 {@code getWidth(Font)}；1.20 {@code Font} → 1.7.10 {@link FontRenderer}。 */
    public int getWidth(FontRenderer font) {
        return this.maxWidth;
    }

    private void getText() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        BulletData bulletData = gunIndex.getBulletData();
        GunData gunData = gunIndex.getGunData();

        if (shouldShow(GunTooltipPart.DESCRIPTION)) {
            @Nullable
            String tooltip = gunIndex.getPojo()
                .getTooltip();
            if (tooltip != null) {
                // 上游 font.split(Component.translatable(tooltip), 300)：
                // 1.7.10 的等价物是 FontRenderer#listFormattedStringToWidth(String, int)（按宽度折行）。
                // 平台差异：上游的 300 是固定值（1.20 的排版器随后还会把整框收进屏幕），1.7.10 没有排版器，
                // 所以这里改用 TooltipLineUtils#maxLineWidth()（= min(300, scaledWidth-26)），
                // 保证小窗口/大 GUI 缩放下也不会比屏幕宽。
                // **1.7.10 修正（显示错误，P1）**：`.lang` 是 1.7.10 的纯文本格式，
                // Locale#loadLocaleData 逐行读 `key=value` 后**不做任何转义处理**
                // （build/rfg/minecraft-src/java/net/minecraft/client/resources/Locale.java:106-126，
                // 只有 IOUtils.readLines + Splitter.on('=')），所以上游 JSON 里的真换行
                // 在 1.7.10 是**字面两字符 `\` + `n`**（docs/PORT_STATUS.md 第 39/190 行的裁决：
                // 保留字面量，由 client 侧显示前 split("\\n")）。
                // 默认枪包里 tacz.gun.<id>.desc / tacz.attachment.<id>.desc 大量使用 `\n`
                // （如 custom/tacz_default_gun/.../lang/en_US.lang:141,261-266），
                // 不先按字面量切分就会把 `\n` 当正文画出来，而且按宽度折行而不是按作者意图断句。
                int maxLineWidth = TooltipLineUtils.maxLineWidth();
                List<String> split = new ArrayList<>();
                for (String segment : StatCollector.translateToLocal(tooltip)
                    .split("\\\\n", -1)) {
                    split.addAll(font.listFormattedStringToWidth(segment, maxLineWidth));
                }
                if (split.size() > 3) {
                    this.desc = split.subList(0, 3);
                } else {
                    this.desc = split;
                }
                for (String sequence : this.desc) {
                    this.maxWidth = Math.max(font.getStringWidth(sequence), this.maxWidth);
                }
            }
        }

        if (shouldShow(GunTooltipPart.AMMO_INFO)) {
            // 上游 renderText 的绘制色：弹药名 0xffaa00（GOLD）、弹药数 0x777777（DARK_GRAY）。
            // 1.7.10 的纯文本 tooltip 用 ChatStyle 表达同一颜色（formatting code 不占宽度，getWidth 不变）。
            this.ammoName = hoverName(this.ammo).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
            this.maxWidth = Math.max(stringWidth(font, this.ammoName) + 22, this.maxWidth);

            int barrelBulletAmount = (iGun.hasBulletInBarrel(gun) && gunIndex.getGunData()
                .getBolt() != Bolt.OPEN_BOLT) ? 1 : 0;
            int maxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(gun, gunIndex.getGunData())
                + barrelBulletAmount;
            int currentAmmoCount = iGun.getCurrentAmmoCount(this.gun) + barrelBulletAmount;

            if (!iGun.useDummyAmmo(gun)) {
                if (display != null && display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
                    this.ammoCountText = new ChatComponentText(
                        CURRENT_AMMO_FORMAT_PERCENT
                            .format((float) currentAmmoCount / (maxAmmoCount == 0 ? 1f : maxAmmoCount)))
                                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY));
                } else {
                    this.ammoCountText = new ChatComponentText("%d/%d".formatted(currentAmmoCount, maxAmmoCount))
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY));
                }
            } else {
                int dummyAmmoAmount = iGun.getDummyAmmoAmount(gun);
                if (display != null && display.getAmmoCountStyle() == AmmoCountStyle.PERCENT) {
                    String p = CURRENT_AMMO_FORMAT_PERCENT
                        .format((float) currentAmmoCount / (maxAmmoCount == 0 ? 1f : maxAmmoCount));
                    this.ammoCountText = new ChatComponentText("%s (%d)".formatted(p, dummyAmmoAmount))
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY));
                } else {
                    this.ammoCountText = new ChatComponentText(
                        "%d/%d (%d)".formatted(currentAmmoCount, maxAmmoCount, dummyAmmoAmount))
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY));
                }

            }
            if (iGun.useInventoryAmmo(gun)) {
                this.ammoCountText = new ChatComponentTranslation("tooltip.tacz.gun.inventory_mode")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW));
            }
            this.maxWidth = Math.max(stringWidth(font, this.ammoCountText) + 22, this.maxWidth);
        }

        if (shouldShow(GunTooltipPart.BASE_INFO)) {
            int expToNextLevel = iGun.getExpToNextLevel(gun);
            int expCurrentLevel = iGun.getExpCurrentLevel(gun);
            int level = iGun.getLevel(gun);
            // 上游 renderText：等级/类型/伤害行的基础色是 0x777777（DARK_GRAY），带样式的片段各自覆盖。
            ChatStyle baseStyle = new ChatStyle().setColor(EnumChatFormatting.DARK_GRAY);
            if (level >= iGun.getMaxLevel()) {
                String levelText = String.format("%d (MAX)", level);
                this.levelInfo = new ChatComponentTranslation("tooltip.tacz.gun.level").setChatStyle(baseStyle)
                    .appendSibling(
                        new ChatComponentText(levelText)
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_PURPLE)));
            } else {
                String levelText = String
                    .format("%d (%.1f%%)", level, expCurrentLevel / (expToNextLevel + expCurrentLevel) * 100f);
                this.levelInfo = new ChatComponentTranslation("tooltip.tacz.gun.level").setChatStyle(baseStyle)
                    .appendSibling(
                        new ChatComponentText(levelText)
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.YELLOW)));
            }
            this.maxWidth = Math.max(stringWidth(font, this.levelInfo), this.maxWidth);

            String tabKey = "tacz.type." + gunIndex.getType() + ".name";
            this.gunType = new ChatComponentTranslation("tooltip.tacz.gun.type").setChatStyle(baseStyle)
                .appendSibling(
                    new ChatComponentTranslation(tabKey)
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
            this.maxWidth = Math.max(stringWidth(font, this.gunType), this.maxWidth);

            double damage = AttachmentDataUtils.getDamageWithAttachment(gun, gunData);
            boolean hasSlugInstalled = AllowAttachmentTagMatcher
                .matchTag(SLUGS, iGun.getAttachmentId(gun, AttachmentType.EXTENDED_MAG));
            int bulletAmount = hasSlugInstalled ? 1
                : gunData.getBulletData()
                    .getBulletAmount();
            IChatComponent value;
            if (display != null && display.getDamageStyle() == DamageStyle.PER_PROJECTILE && bulletAmount > 1) {
                value = new ChatComponentText(
                    DAMAGE_FORMAT.format(
                        damage) + " (" + DAMAGE_FORMAT.format(damage / bulletAmount) + "x" + bulletAmount + ")")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA));
            } else {
                value = new ChatComponentText(DAMAGE_FORMAT.format(damage))
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA));
            }
            if (bulletData.getExplosionData() != null
                && (AttachmentDataUtils.isExplodeEnabled(gun, gunData) || bulletData.getExplosionData()
                    .isExplode())) {
                ChatStyle explosionStyle = new ChatStyle().setColor(EnumChatFormatting.AQUA);
                IChatComponent extra = new ChatComponentText(
                    " + " + DAMAGE_FORMAT.format(
                        bulletData.getExplosionData()
                            .getDamage() * SyncConfig.DAMAGE_BASE_MULTIPLIER.get())).setChatStyle(explosionStyle);
                extra.appendSibling(
                    new ChatComponentTranslation("tooltip.tacz.gun.explosion").setChatStyle(explosionStyle));
                value.appendSibling(extra);
            }
            this.damage = new ChatComponentTranslation("tooltip.tacz.gun.damage").setChatStyle(baseStyle)
                .appendSibling(value);
            this.maxWidth = Math.max(stringWidth(font, this.damage), this.maxWidth);
        }

        if (shouldShow(GunTooltipPart.EXTRA_DAMAGE_INFO)) {
            @Nullable
            ExtraDamage extraDamage = bulletData.getExtraDamage();
            // 上游 renderText：穿甲/爆头两行绘制色 0xffaa00（GOLD），纯文本 tooltip 用 ChatStyle 表达。
            ChatStyle goldStyle = new ChatStyle().setColor(EnumChatFormatting.GOLD);
            if (extraDamage != null) {
                double armorDamagePercent = AttachmentDataUtils.getArmorIgnoreWithAttachment(gun, gunData);
                double headShotMultiplierPercent = AttachmentDataUtils.getHeadshotMultiplier(gun, gunData);

                armorDamagePercent = Mth.clamp(armorDamagePercent, 0.0F, 1.0F);

                this.armorIgnore = new ChatComponentTranslation(
                    "tooltip.tacz.gun.armor_ignore",
                    FORMAT.format(armorDamagePercent)).setChatStyle(goldStyle);
                this.headShotMultiplier = new ChatComponentTranslation(
                    "tooltip.tacz.gun.head_shot_multiplier",
                    FORMAT.format(headShotMultiplierPercent)).setChatStyle(goldStyle);
            } else {
                this.armorIgnore = new ChatComponentTranslation("tooltip.tacz.gun.armor_ignore", FORMAT.format(0))
                    .setChatStyle(goldStyle);
                this.headShotMultiplier = new ChatComponentTranslation(
                    "tooltip.tacz.gun.head_shot_multiplier",
                    FORMAT.format(1)).setChatStyle(goldStyle);
            }

            double weightFactor = SyncConfig.WEIGHT_SPEED_MULTIPLIER.get();
            double weight = AttachmentDataUtils.getWightWithAttachment(gun, gunData);
            this.weight = new ChatComponentTranslation(
                "tooltip.tacz.gun.movement_speed",
                FORMAT_P_D1.format(-weightFactor * weight))
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED));

            this.maxWidth = Math.max(stringWidth(font, this.armorIgnore), this.maxWidth);
            this.maxWidth = Math.max(stringWidth(font, this.headShotMultiplier), this.maxWidth);
            this.maxWidth = Math.max(stringWidth(font, this.weight), this.maxWidth);
        }

        if (shouldShow(GunTooltipPart.UPGRADES_TIP)) {
            // TODO(L2): 上游 client/tooltip/ClientGunTooltip.java:264 用
            // Component.keybind(RefitKey.REFIT_KEY.getName()) 渲染「当前绑定键」。1.7.10 没有 keybind 组件，
            // 等价物是 Keyboard.getKeyName(KeyBinding#getKeyCode())（与 client/gui/overlay/InteractKeyTextOverlay
            // 里同一个写法）。
            String boundKey = Keyboard.getKeyName(RefitKey.REFIT_KEY.getKeyCode());
            String keyName = (boundKey == null ? "" : boundKey).toUpperCase(Locale.ENGLISH);
            this.tips = new ChatComponentTranslation("tooltip.tacz.gun.tips", keyName).setChatStyle(
                new ChatStyle().setColor(EnumChatFormatting.YELLOW)
                    .setItalic(Boolean.TRUE));
            this.maxWidth = Math.max(stringWidth(font, this.tips), this.maxWidth);
        }

        if (shouldShow(GunTooltipPart.PACK_INFO)) {
            ResourceLocation gunId = iGun.getGunId(gun);
            PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(gunId);
            if (packInfoObject != null) {
                // 空安全：第三方包 gunpack_info.json 显式写 `"name": null` 会让 getName() 返回 null，
                // 而 new ChatComponentTranslation(null).getFormattedText() 会 NPE（见 PackInfoText 类注释）。
                this.packInfo = PackInfoText.name(packInfoObject, gunId.getResourceDomain())
                    .setChatStyle(
                        new ChatStyle().setColor(EnumChatFormatting.BLUE)
                            .setItalic(Boolean.TRUE));
                this.maxWidth = Math.max(stringWidth(font, this.packInfo), this.maxWidth);
            }
        }
    }

    /**
     * 对应上游 {@code renderText(Font, int, int, Matrix4f, MultiBufferSource.BufferSource)}。
     *
     * <p>
     * y 偏移、缩进、颜色逐行照搬上游；1.7.10 没有批处理缓冲，直接 {@link FontRenderer#drawString}。
     */
    public void renderText(FontRenderer font, int pX, int pY) {
        int yOffset = pY;

        if (shouldShow(GunTooltipPart.DESCRIPTION) && this.desc != null) {
            yOffset += 2;
            for (String sequence : this.desc) {
                font.drawString(sequence, pX, yOffset, 0xaaaaaa);
                yOffset += 10;
            }
        }

        if (shouldShow(GunTooltipPart.AMMO_INFO)) {
            yOffset += 4;

            // 弹药名
            font.drawString(this.ammoName.getFormattedText(), pX + 20, yOffset, 0xffaa00);

            // 弹药数
            font.drawString(this.ammoCountText.getFormattedText(), pX + 20, yOffset + 10, 0x777777);

            yOffset += 20;
        }

        if (shouldShow(GunTooltipPart.BASE_INFO)) {
            yOffset += 4;

            // 等级信息
            font.drawString(this.levelInfo.getFormattedText(), pX, yOffset, 0x777777);
            yOffset += 10;

            // 枪械类型
            if (this.gunType != null) {
                font.drawString(this.gunType.getFormattedText(), pX, yOffset, 0x777777);
                yOffset += 10;
            }

            // 伤害
            font.drawString(this.damage.getFormattedText(), pX, yOffset, 0x777777);
            yOffset += 10;
        }

        if (shouldShow(GunTooltipPart.EXTRA_DAMAGE_INFO)) {
            yOffset += 4;

            // 穿甲伤害
            font.drawString(this.armorIgnore.getFormattedText(), pX, yOffset, 0xffaa00);
            yOffset += 10;

            // 爆头伤害
            font.drawString(this.headShotMultiplier.getFormattedText(), pX, yOffset, 0xffaa00);
            yOffset += 10;

            font.drawString(this.weight.getFormattedText(), pX, yOffset, 0xffffff);
            yOffset += 10;
        }

        if (shouldShow(GunTooltipPart.UPGRADES_TIP)) {
            yOffset += 4;

            // Z 键说明
            font.drawString(this.tips.getFormattedText(), pX, yOffset, 0xffffff);
            yOffset += 10;
        }

        if (shouldShow(GunTooltipPart.PACK_INFO)) {
            // 枪包名
            if (packInfo != null) {
                yOffset += 4;
                font.drawString(this.packInfo.getFormattedText(), pX, yOffset, 0xffffff);
            }
        }
    }

    /**
     * 对应上游 {@code renderImage(Font, int, int, GuiGraphics)}：在文字块左侧画弹药图标。
     *
     * <p>
     * 上游该方法里重新取了一次 {@code IGun.getIGunOrNull(this.gun)} 做空判断，此处原样保留；
     * 1.7.10 侧走 {@link GuiGraphics#renderItem}（shim，内部 {@code RenderItem}）。
     */
    public void renderImage(FontRenderer pFont, int pX, int pY, GuiGraphics guiGraphics) {
        IGun iGun = IGun.getIGunOrNull(this.gun);
        if (iGun == null) {
            return;
        }
        if (shouldShow(GunTooltipPart.AMMO_INFO)) {
            int yOffset = pY;
            if (shouldShow(GunTooltipPart.DESCRIPTION) && this.desc != null) {
                yOffset += this.desc.size() * 10 + 2;
            }
            guiGraphics.renderItem(ammo, pX, yOffset + 4);
        }
    }

    /**
     * 1.7.10 桥接：把本组件折算成 tooltip 文本行（文本顺序与 {@link #renderText} 完全一致）。
     *
     * <p>
     * TODO(L2): 上游 client/tooltip/ClientGunTooltip.java:470-482 的 {@code renderImage} 在弹药信息左侧
     * 画一枚 16×16 的弹药物品图标。1.7.10 的 {@code List<String>}/{@code List<IChatComponent>} tooltip
     * 没有位图能力，此处不再产出占位行——图标对应的信息（弹药名）本来就在第一行文字里，
     * 因此没有信息损失；需要图标的 GUI 直接调 {@link #renderImage}。
     *
     * @param tooltip 追加目标
     */
    public void appendHoverText(List<IChatComponent> tooltip) {
        if (shouldShow(GunTooltipPart.DESCRIPTION) && this.desc != null) {
            for (String line : this.desc) {
                tooltip
                    .add(new ChatComponentText(line).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
            }
        }
        if (shouldShow(GunTooltipPart.AMMO_INFO)) {
            tooltip.add(this.ammoName);
            tooltip.add(this.ammoCountText);
        }
        if (shouldShow(GunTooltipPart.BASE_INFO)) {
            tooltip.add(this.levelInfo);
            if (this.gunType != null) {
                tooltip.add(this.gunType);
            }
            tooltip.add(this.damage);
        }
        if (shouldShow(GunTooltipPart.EXTRA_DAMAGE_INFO)) {
            tooltip.add(this.armorIgnore);
            tooltip.add(this.headShotMultiplier);
            tooltip.add(this.weight);
        }
        if (shouldShow(GunTooltipPart.UPGRADES_TIP)) {
            tooltip.add(this.tips);
        }
        if (shouldShow(GunTooltipPart.PACK_INFO) && packInfo != null) {
            tooltip.add(this.packInfo);
        }
    }

    /** {@link #appendHoverText(List)} 的 {@code List<String>} 形态，可直接用于 {@code addInformation}。 */
    public List<String> getLegacyTooltipLines() {
        List<IChatComponent> components = Lists.newArrayList();
        appendHoverText(components);
        List<String> lines = Lists.newArrayList();
        for (IChatComponent component : components) {
            lines.add(component == null ? "" : component.getFormattedText());
        }
        return lines;
    }

    private boolean shouldShow(GunTooltipPart part) {
        return (GunTooltipPart.getHideFlags(this.gun) & part.getMask()) == 0;
    }

    /** 1.7.10 的 {@code Font} 没有 {@code width(...)}，等价物是 {@link FontRenderer#getStringWidth(String)}。 */
    private static int stringWidth(FontRenderer font, @Nullable IChatComponent component) {
        return component == null ? 0 : font.getStringWidth(component.getFormattedText());
    }

    /**
     * 1.20 {@code ItemStack#getHoverName()} 的 1.7.10 等价物。
     *
     * <p>
     * 1.7.10 没有 {@code ItemStack.EMPTY}（空栈用 {@code null} 表示）：上游空栈时拿到 {@code Items.AIR}
     * 的名字 "Air"，此处同样回落到该字面量（1.7.10 的 {@code en_US.lang} 没有 {@code tile.air.name}）。
     */
    private static IChatComponent hoverName(@Nullable ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return new ChatComponentText("Air");
        }
        return new ChatComponentText(stack.getDisplayName());
    }
}
