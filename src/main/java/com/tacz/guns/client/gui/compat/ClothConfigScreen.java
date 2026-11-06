package com.tacz.guns.client.gui.compat;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import org.apache.commons.lang3.StringUtils;

import com.tacz.guns.mcshim.GuiGraphics;

/**
 * 缺少 Cloth Config 时的提示界面。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>Cloth Config 是 1.14+ 的模组，1.7.10 上没有可用的同版本 API。
 * 本类整份改为<b>反射式可选兼容</b>：只用 {@code Class.forName} 探测
 * {@code me.shedaniel.clothconfig2.*} 是否存在，缺失时静默跳过，
 * <b>不留任何 cloth 的 import</b>。</li>
 * <li>1.7.10 没有 {@code ConfigScreenHandler.ConfigScreenFactory} /
 * {@code ModLoadingContext#registerExtensionPoint} 这套 Forge 扩展点，
 * {@link #registerNoClothConfigPage()} 退化为「探测 + 记录」，不注册任何东西；
 * 需要挂配置界面的地方（1.7.10 是 {@code IModGuiFactory}）由 compat 层自行决定。
 * TODO(port): 上游 {@code ModLoadingContext.get().registerExtensionPoint(...)} 在 1.7.10
 * 的等价物是 {@code cpw.mods.fml.client.IModGuiFactory}，当前工程未接。</li>
 * <li>{@code MultiLineLabel}（1.20）→ {@code FontRenderer#listFormattedStringToWidth} 自己折行。</li>
 * <li>{@code ConfirmLinkScreen}（1.20）→ 1.7.10 无对应，直接开浏览器。</li>
 * </ul>
 */
public class ClothConfigScreen extends GuiScreen {

    public static final String CLOTH_CONFIG_URL = "https://www.curseforge.com/minecraft/mc-mods/cloth-config";
    /** 只用于反射探测：Cloth Config 的入口类之一。 */
    private static final String CLOTH_CONFIG_PROBE_CLASS = "me.shedaniel.clothconfig2.ClothConfig";

    private final GuiScreen lastScreen;
    private List<String> messageLines = new ArrayList<>();
    private int messageY;

    protected ClothConfigScreen(GuiScreen lastScreen) {
        super();
        this.lastScreen = lastScreen;
    }

    /** Cloth Config 是否装在客户端（纯反射探测，缺失时不抛异常）。 */
    public static boolean isClothConfigLoaded() {
        try {
            Class.forName(CLOTH_CONFIG_PROBE_CLASS, false, ClothConfigScreen.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 对应上游 {@code registerNoClothConfigPage()}。
     *
     * <p>
     * 1.7.10 没有 Forge 的配置界面扩展点，这里只做探测；没装 Cloth Config 时
     * 什么都不注册（与上游「缺库时提供一个提示页」的意图一致：需要提示时手动
     * {@code new ClothConfigScreen(parent)}）。
     */
    public static void registerNoClothConfigPage() {
        if (isClothConfigLoaded()) {
            return;
        }
        // TODO(port): 上游在此注册 ConfigScreenHandler.ConfigScreenFactory；1.7.10 无该扩展点
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int posX = (this.width - 200) / 2;
        int posY = this.height / 2;
        IChatComponent tips = new ChatComponentTranslation("gui.tacz.cloth_config_warning.tips");
        this.messageLines = this.fontRendererObj.listFormattedStringToWidth(tips.getFormattedText(), 300);
        this.messageY = 80;
        this.buttonList.add(
            new GuiButton(
                0,
                posX,
                posY - 15,
                200,
                20,
                new ChatComponentTranslation("gui.tacz.cloth_config_warning.download").getFormattedText()) {

                @Override
                public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
                    if (super.mousePressed(mc, mouseX, mouseY)) {
                        openUrl();
                        return true;
                    }
                    return false;
                }
            });
        this.buttonList.add(new GuiButton(1, posX, posY + 50, 200, 20, I18n.format("gui.back")) {

            @Override
            public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
                if (super.mousePressed(mc, mouseX, mouseY)) {
                    mc.displayGuiScreen(ClothConfigScreen.this.lastScreen);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        this.drawDefaultBackground();
        GuiGraphics gui = GuiGraphics.create();
        FontRenderer font = this.fontRendererObj;
        int y = this.messageY;
        for (String line : this.messageLines) {
            gui.drawCenteredString(font, line, this.width / 2, y, 0xFFFFFF);
            y += font.FONT_HEIGHT + 2;
        }
        super.drawScreen(mouseX, mouseY, partialTick);
    }

    /** 打开 Cloth Config 的下载页（1.7.10 无 {@code ConfirmLinkScreen}，直接开浏览器）。 */
    private static void openUrl() {
        if (StringUtils.isBlank(CLOTH_CONFIG_URL)) {
            return;
        }
        try {
            Desktop.getDesktop()
                .browse(URI.create(CLOTH_CONFIG_URL));
        } catch (Exception ignored) {
            // 无桌面环境时静默跳过
        }
    }

    /** 未使用但保留：为后续可能接入的 {@code IModGuiFactory} 提供父屏幕。 */
    @SuppressWarnings("unused")
    public GuiScreen getParentScreen() {
        return this.lastScreen;
    }

    /** 未使用但保留：便于 compat 层判断是否应该展示提示页。 */
    @SuppressWarnings("unused")
    private static IChatComponent warningTitle() {
        return new ChatComponentText("Cloth Config API");
    }
}
