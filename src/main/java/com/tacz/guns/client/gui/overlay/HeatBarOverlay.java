package com.tacz.guns.client.gui.overlay;

import java.text.DecimalFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;

/**
 * 过热条 HUD。
 *
 * <p>
 * <b>移植说明</b>（1.20.1 → 1.7.10）：
 * <ul>
 * <li>1.7.10 没有 {@code IGuiOverlay}/{@code ForgeGui}，入口改为
 * {@link #render(GuiGraphics, int, int)}（由 {@code RenderGameOverlayEvent.Post} 调用）。</li>
 * <li>{@code gui.getGuiTicks()}（ForgeGui）→ {@code Minecraft#ingameGUI.getUpdateCounter()}。</li>
 * <li>{@code net.minecraft.util.FastColor.ARGB32.lerp}（1.19+）在 1.7.10 无对应，
 * 改为本类内联的等价 ARGB 插值。</li>
 * <li>{@code Minecraft#fontFilterFishy} 在 1.7.10 无对应，改用 {@code fontRenderer}。</li>
 * <li>{@code Minecraft#player}→{@code thePlayer}，{@code getMainHandItem()}→{@code getHeldItem()}。</li>
 * </ul>
 */
public class HeatBarOverlay {

    private static final ResourceLocation HEATBASE = new ResourceLocation(GunMod.MOD_ID, "textures/hud/heat_base.png");
    private static final DecimalFormat HEAT_FORMAT_PERCENT = new DecimalFormat("0.0%");
    private static float heatScale = 0.25f;

    public void render(GuiGraphics graphics, int width, int height) {
        if (!RenderConfig.GUN_HUD_ENABLE.get()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (!(player instanceof IClientPlayerGunOperator)) {
            return;
        }
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(stack);
        GunData gunData = TimelessAPI.getClientGunIndex(gunId)
            .map(ClientGunIndex::getGunData)
            .orElse(null);
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack)
            .orElse(null);
        if (gunData == null || display == null) {
            return;
        }

        if (gunData.getHeatData() != null && iGun.hasHeatData(stack)) {
            graphics.pose()
                .pushPose();
            GunHeatData heatData = gunData.getHeatData();
            float percent = iGun.getHeatAmount(stack) / heatData.getHeatMax();

            float scaleValue = ((iGun.getHeatAmount(stack) / heatData.getHeatMax()) / 8f) + 0.75f;

            if (heatScale < scaleValue) {
                heatScale += 0.05f;
            }
            if (heatScale > scaleValue) {
                heatScale -= 0.025f;
            }
            if (heatScale > scaleValue - 0.03 && heatScale < scaleValue + 0.055) {
                heatScale = scaleValue;
            }
            graphics.pose()
                .scale(heatScale, heatScale, 1.0F);

            boolean locked = iGun.isOverheatLocked(stack);
            int tickCount = mc.ingameGUI.getUpdateCounter();
            renderOverheat(percent, graphics, (int) (width / heatScale), (int) (height / heatScale), locked, tickCount);
            graphics.pose()
                .popPose();
        }
    }

    public void renderOverheat(float heatPercentage, GuiGraphics pGraphics, int w, int h, boolean locked,
        int tickCount) {
        int barColor = getHeatColor(heatPercentage, locked, tickCount);
        pGraphics.fill(w / 2 - 30, h / 2 + 30, w / 2 - 30 + (int) (heatPercentage * 60), h / 2 + 34, barColor);
        if (locked) {
            if (tickCount % 20 < 10) {
                pGraphics.setColor(1, 0.1f, 0.1f, 1);
            } else {
                pGraphics.setColor(1, 1, 0.1f, 1);
            }
        }
        pGraphics.blit(HEATBASE, w / 2 - 64, h / 2 - 44, 0, 0, 128, 128, 128, 128);
        pGraphics.setColor(1, 1, 1, 1);

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String percentString = locked ? "!OVERHEAT!" : HEAT_FORMAT_PERCENT.format(heatPercentage);
        int color = locked ? (tickCount % 20 < 10 ? 0xFFFF0000 : 0xFFFFFF00) : 0xFFFFFFFF;

        pGraphics
            .drawString(font, percentString, w / 2 - (font.getStringWidth(percentString) / 2), h / 2 + 38, color, true);
    }

    public static int getHeatColor(float percent, boolean locked, int tickCount) {
        if (locked) {
            return tickCount % 20 < 10 ? 0x9FFF0000 : 0x9FFFFF00;
        }
        if (percent < 0.4) {
            return 0x9FFFFFFF;
        }
        int color;
        if (percent <= 0.65) {
            color = lerpArgb(percent * 4 - 1.6f, 0x9FFFFFFF, 0x9FFFFF00);
        } else {
            color = lerpArgb((percent - 0.65f) / 0.35f, 0x9FFFFF00, 0x9FFF0000);
        }
        return color;
    }

    /**
     * 对应 1.20 {@code FastColor.ARGB32.lerp(delta, start, end)}：逐通道
     * {@code Mth.lerpInt}（{@code start + floor(delta * (end - start))}）。
     */
    private static int lerpArgb(float delta, int start, int end) {
        int a = Mth.floor((start >>> 24) + delta * ((end >>> 24) - (start >>> 24)));
        int r = Mth.floor((start >> 16 & 0xFF) + delta * ((end >> 16 & 0xFF) - (start >> 16 & 0xFF)));
        int g = Mth.floor((start >> 8 & 0xFF) + delta * ((end >> 8 & 0xFF) - (start >> 8 & 0xFF)));
        int b = Mth.floor((start & 0xFF) + delta * ((end & 0xFF) - (start & 0xFF)));
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }
}
