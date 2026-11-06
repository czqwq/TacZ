package com.tacz.guns.client.gui.toast;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.tacz.guns.mcshim.GuiGraphics;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械升级「toast」提示。
 *
 * <p>
 * <b>降级说明</b>：1.7.10 <b>没有 toast 系统</b>（{@code Toast}/{@code ToastComponent}
 * 是 1.12+ 的 GUI 机制）。本类保留类名与构造签名作为「一条升级提示」的数据载体，
 * 实际展示改为<b>往玩家聊天栏发一条格式化消息</b>（{@link #show()}）。
 *
 * <p>
 * TODO(port): 上游 {@code ServerMessageLevelUp} 现在调用
 * {@code Minecraft.getInstance().getToasts().addToast(new GunLevelUpToast(...))}；
 * 1.7.10 侧请改成在客户端主线程调用 {@code new GunLevelUpToast(...).show()}。
 * 如果后续要做真正的 HUD 弹窗，可以在这个类里加一个
 * {@code render(GuiGraphics, int width, int height, long timeSinceVisible)} 由 overlay 调用。
 */
@SideOnly(Side.CLIENT)
public class GunLevelUpToast {

    private final IChatComponent title;
    private final IChatComponent subTitle;
    private final ItemStack icon;

    public GunLevelUpToast(ItemStack icon, IChatComponent titleComponent, @Nullable IChatComponent subtitle) {
        this.icon = icon;
        this.title = titleComponent;
        this.subTitle = subtitle;
    }

    public IChatComponent getTitle() {
        return this.title;
    }

    public IChatComponent getSubTitle() {
        return this.subTitle;
    }

    public ItemStack getIcon() {
        return this.icon;
    }

    /** 1.7.10 的展示方式：直接发聊天消息（对应上游 toast 的标题 + 副标题两行）。 */
    public void show() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        if (player == null) {
            return;
        }
        if (this.title != null) {
            player.addChatComponentMessage(
                new ChatComponentText(EnumChatFormatting.YELLOW + this.title.getUnformattedText()));
        }
        if (this.subTitle != null) {
            player.addChatComponentMessage(new ChatComponentText(this.subTitle.getUnformattedText()));
        }
        if (this.icon != null && this.icon.getItem() != null) {
            player.addChatComponentMessage(
                new ChatComponentTranslation("gui.tacz.gun_level_up.toast", this.icon.getDisplayName()));
        }
    }

    /**
     * 1.20 {@code Toast#render} 的占位实现：1.7.10 没有 toast 容器，本方法不被调用。
     *
     * @return 始终 {@code false}（表示「没有内容可画」）
     */
    public boolean render(GuiGraphics graphics, long timeSinceLastVisible) {
        // 1.7.10 无 toast 渲染管线；如需 HUD 弹窗请由 overlay 调用方自行实现
        return false;
    }
}
