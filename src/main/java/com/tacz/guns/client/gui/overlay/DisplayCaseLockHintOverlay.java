package com.tacz.guns.client.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械展示柜上锁提示 HUD。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>1.7.10 没有 {@code IGuiOverlay}/{@code ForgeGui}，入口改为
 * {@link #render(GuiGraphics, int, int)}，由事件在 {@code RenderGameOverlayEvent} 里调用。</li>
 * <li>上游的 {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)} 在 1.7.10 由
 * {@code ClientProxy} 显式 {@code MinecraftForge.EVENT_BUS.register(...)}，注解删除；
 * {@link SubscribeEvent} 换成 1.7.10 的 {@code cpw.mods.fml.common.eventhandler.SubscribeEvent}。</li>
 * <li>{@code Component} → {@link IChatComponent}；{@code ChatFormatting.YELLOW.getColor()} →
 * 常量 {@code 0xFFFF55}（1.7.10 的 {@code EnumChatFormatting} 没有 {@code getColor()}）。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class DisplayCaseLockHintOverlay {

    private static final int DISPLAY_TICKS = 40;
    private static final int Y_OFFSET = 32;
    private static final int YELLOW = 0xFFFF55;
    private static IChatComponent message = new ChatComponentText("");
    private static int ticksRemaining;
    /**
     * 显示截止时刻（{@link GunTime#nowMillis()} 基准）。
     *
     * <p>
     * 与 {@link #ticksRemaining} 取「先到者」：tick 递减由 {@link #onClientTick} 负责（已注册到 FML 总线，
     * 见 {@code client/init/ClientSetupEvent#registerOverlays()}）；本字段是与事件无关的墙钟兜底，
     * 保证「提示一定会消失」不依赖 tick 速率。
     */
    private static long expireAt;

    public static void show(IChatComponent component) {
        message = component == null ? new ChatComponentText("") : component;
        ticksRemaining = DISPLAY_TICKS;
        expireAt = GunTime.nowMillis() + (long) DISPLAY_TICKS * 50L;
    }

    public void render(GuiGraphics graphics, int width, int height) {
        if (ticksRemaining <= 0 || GunTime.nowMillis() >= expireAt) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        graphics.drawString(
            font,
            message,
            (int) ((width - font.getStringWidth(message.getFormattedText())) / 2.0F),
            (int) (height / 2.0F + Y_OFFSET),
            YELLOW,
            false);
    }

    /**
     * 逐 tick 递减提示剩余时间。
     *
     * <p>
     * 必须是实例方法：FML 1.7.10 的 {@code ASMEventHandler} 用 {@code INVOKEVIRTUAL} 回调，
     * {@code public static} 处理器会抛 {@code IncompatibleClassChangeError}。
     * 注册点：{@code client/init/ClientSetupEvent#registerOverlays()}。
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ticksRemaining > 0) {
            ticksRemaining--;
        }
    }
}
