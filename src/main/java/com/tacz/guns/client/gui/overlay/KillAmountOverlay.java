package com.tacz.guns.client.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.util.time.GunTime;

/**
 * 连杀数提示 HUD。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：1.7.10 没有 {@code IGuiOverlay}/{@code ForgeGui}，
 * 入口改为 {@link #render(GuiGraphics, int, int)}；{@code Minecraft#player}→{@code thePlayer}，
 * {@code font}→{@code fontRenderer}，{@code LocalPlayer}→{@code EntityPlayerSP}，
 * 1.7.10 无旁观者模式（{@code isSpectator} 判断删除）。
 */
public class KillAmountOverlay {

    private static long killTimestamp = -1L;
    private static int killAmount = 0;

    public void render(GuiGraphics graphics, int width, int height) {
        if (!RenderConfig.KILL_AMOUNT_ENABLE.get()) {
            return;
        }
        int timeout = (int) (RenderConfig.KILL_AMOUNT_DURATION_SECOND.get() * 1000);
        float colorCount = 30;

        long remainTime = GunTime.nowMillis() - killTimestamp;
        if (!GunTime.isWithin(killTimestamp, timeout)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (!(player instanceof IClientPlayerGunOperator)) {
            return;
        }
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof IGun)) {
            return;
        }

        String text;
        if (killAmount < 10) {
            text = "\u2620 x 0" + killAmount;
        } else {
            text = "\u2620 x " + killAmount;
        }
        int fontWith = mc.fontRenderer.getStringWidth(text);
        double fadeOutTime = timeout / 3.0 * 2;
        float hue = (1 - Math.min((killAmount / colorCount), 1)) * 0.15f;
        int alpha = 0xFF;
        if (remainTime > fadeOutTime) {
            alpha = 0xFF - (int) ((remainTime - fadeOutTime) / (timeout - fadeOutTime) * 0xF0);
        }
        int color = Mth.hsvToRgb(hue, 0.75f, 1) + (alpha << 24);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        graphics.pose()
            .pushPose();
        graphics.pose()
            .scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(mc.fontRenderer, text, (int) (width - fontWith / 2.0f), (height - 45) * 2 - 1, color);
        graphics.pose()
            .popPose();
        RenderSystem.disableBlend();
    }

    public static void markTimestamp() {
        int timeout = (int) (RenderConfig.KILL_AMOUNT_DURATION_SECOND.get() * 1000);
        if (!GunTime.isWithin(killTimestamp, timeout)) {
            killAmount = 0;
        }
        killTimestamp = GunTime.nowMillis();
        killAmount += 1;
    }

    public static void resetTimestamp() {
        killTimestamp = -1L;
        killAmount = 0;
    }
}
