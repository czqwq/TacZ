package com.tacz.guns.client.gui;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.IProgressUpdate;

import com.tacz.guns.mcshim.GuiGraphics;

/**
 * 枪包下载进度界面。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>{@code Screen} → {@link GuiScreen}：{@code init()}→{@code initGui()}，
 * {@code render(GuiGraphics,...)}→{@code drawScreen(int,int,float)}，
 * {@code onClose()}→{@code mc.displayGuiScreen(null)}。</li>
 * <li>1.20 的 {@code net.minecraft.util.ProgressListener} 在 1.7.10 <b>不存在</b>，
 * 对应物是 {@link IProgressUpdate}（方法名完全不同）。这里实现 {@link IProgressUpdate}，
 * 同时把上游的 {@code progressStart/progressStage/...} 保留为普通公开方法，
 * 调用方按需选一套。</li>
 * <li>{@code GameNarrator.NO_TITLE} 与 {@code addRenderableWidget} 在 1.7.10 无对应：
 * 标题不再需要，控件直接进 {@code buttonList}。</li>
 * </ul>
 */
public class GunPackProgressScreen extends GuiScreen implements IProgressUpdate {

    private static final int BUTTON_ID_DOWNLOAD = 0;

    private @Nullable IChatComponent header;
    private @Nullable IChatComponent stage;
    private int progress;
    private boolean stop;

    public GunPackProgressScreen() {
        // 上游 super(GameNarrator.NO_TITLE)：1.7.10 没有旁白系统，不需要标题
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(
            new GuiButton(
                BUTTON_ID_DOWNLOAD,
                (this.width - 200) / 2,
                120,
                200,
                20,
                new ChatComponentTranslation("gui.tacz.client_gun_pack_downloader.background_download")
                    .getFormattedText()));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_ID_DOWNLOAD) {
            this.stop();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        if (this.stop) {
            this.mc.displayGuiScreen((GuiScreen) null);
            return;
        }
        this.drawDefaultBackground();
        GuiGraphics gui = GuiGraphics.create();
        if (this.header != null) {
            gui.drawCenteredString(this.fontRendererObj, this.header, this.width / 2, 70, 16777215);
        }
        if (this.stage != null && this.progress > 0) {
            IChatComponent text = this.stage.createCopy()
                .appendSibling(new ChatComponentText(" " + this.progress + "%"));
            gui.drawCenteredString(this.fontRendererObj, text, this.width / 2, 90, 16777215);
        }
        super.drawScreen(mouseX, mouseY, partialTick);
    }

    // ================================================================================================
    // 上游（1.20 ProgressListener）的公开方法，原签名保留，只把 Component 换成 IChatComponent
    // ================================================================================================

    public void progressStartNoAbort(IChatComponent component) {
        this.progressStart(component);
    }

    public void progressStart(IChatComponent header) {
        this.header = new ChatComponentTranslation("gui.tacz.client_gun_pack_downloader.downloading");
    }

    public void progressStage(IChatComponent component) {
        this.stage = component;
        this.progressStagePercentage(0);
    }

    public void progressStagePercentage(int progress) {
        this.progress = progress;
    }

    public void stop() {
        this.stop = true;
    }

    // ================================================================================================
    // 1.7.10 IProgressUpdate 适配（等价语义，方法名照搬原版）
    // ================================================================================================

    /** 对应上游 {@code progressStage}。 */
    @Override
    public void displayProgressMessage(String message) {
        this.stage = new ChatComponentText(message);
    }

    /** 对应上游 {@code progressStartNoAbort}。 */
    @Override
    public void resetProgressAndMessage(String message) {
        this.header = new ChatComponentText(message);
    }

    /** 对应上游 {@code progressStart} 的第二段文本。 */
    @Override
    public void resetProgresAndWorkingMessage(String message) {
        this.stage = new ChatComponentText(message);
    }

    /** 对应上游 {@code progressStagePercentage}。 */
    @Override
    public void setLoadingProgress(int progress) {
        this.progress = progress;
    }

    /** 1.7.10 的「取消」入口，语义等同上游 {@code stop()}。 */
    @Override
    public void func_146586_a() {
        this.stop();
    }
}
