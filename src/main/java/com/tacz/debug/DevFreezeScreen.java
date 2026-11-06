package com.tacz.debug;

import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 调试用「冻结屏」：把它设为当前 GUI 会让 1.7.10 客户端进入暂停（{@code doesGuiPauseGame() == true}），
 * 于是世界 tick / 实体 / 粒子全部冻结、而渲染照常进行。
 *
 * <p>
 * 这是渲染回归测试的关键：冻结 + 固定相机 + 固定 tick 之后，同一场景每帧像素基本一致，
 * 可对帧缓冲做稳定比对；不冻结则云/粒子/动画每帧都在动。
 *
 * <p>
 * 不绘制任何东西（GUI 用默认空实现），所以帧缓冲里只有世界，不会混入暂停菜单 UI。
 */
@SideOnly(Side.CLIENT)
public final class DevFreezeScreen extends GuiScreen {

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 调试用：不画任何 UI，保证帧缓冲内容只有世界渲染结果
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // 调试用：吃掉按键，避免 ESC 退出冻结
    }
}
