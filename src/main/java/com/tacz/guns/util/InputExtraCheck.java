package com.tacz.guns.util;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 判断「现在是否真的在游戏里操作」——用于按键处理时提前退出（没有任何 GUI / 加载界面 / 窗口失焦）。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code Minecraft.getInstance()} → {@link Minecraft#getMinecraft()}。</li>
 * <li>{@code mc.getOverlay()}（1.20 的加载/重载覆盖层 {@code Overlay}）：1.7.10 <b>没有</b>覆盖层抽象，
 * 最接近的判据是「当前屏幕」{@code currentScreen}（加载世界时是 {@code GuiDownloadTerrain}）与
 * 「世界/玩家是否就绪」，因此用 {@code currentScreen != null} + {@code theWorld/thePlayer == null}
 * 一起覆盖上游 {@code getOverlay() != null} 的意图。</li>
 * <li>{@code mc.screen} → 1.7.10 的 {@link Minecraft#currentScreen}。</li>
 * <li>{@code mc.mouseHandler.isMouseGrabbed()} → LWJGL 2 的 {@link Mouse#isGrabbed()}
 * （1.7.10 用的是 LWJGL2，没有 {@code MouseHandler} 类）。</li>
 * <li>{@code mc.isWindowActive()} → LWJGL 2 的 {@link Display#isActive()}。</li>
 * <li>{@code net.minecraftforge.api.distmarker.@OnlyIn(Dist.CLIENT)} → 1.7.10 的
 * {@link SideOnly} + {@link Side#CLIENT}。</li>
 * <li>{@code isInGame()} 被 {@code client/input} 下十余个按键类静态导入，<b>方法名与签名保持不变</b>。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class InputExtraCheck {

    public static boolean isInGame() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return false;
        }
        // 不能是加载界面（1.7.10 无 Overlay 抽象，用「当前屏幕 + 世界/玩家是否就绪」覆盖）
        if (mc.currentScreen != null) {
            return false;
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        // 当前窗口捕获鼠标操作
        if (!Mouse.isGrabbed()) {
            return false;
        }
        // 选择了当前窗口
        return Display.isActive();
    }
}
