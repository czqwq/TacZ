package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.GunSmithTableScreen;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 在枪械合成台 / 改装界面里隐藏原版 HUD（上游取消的是这两类界面下的<b>所有</b>原版 overlay，
 * 不止快捷栏；类名沿用上游）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/PreventsHotbarEvent.java}。
 * 上游 {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)} → 1.7.10 由 {@code ClientProxy} 显式注册到
 * {@code MinecraftForge.EVENT_BUS}。
 *
 * <p>
 * 平台替换：{@code RenderGuiOverlayEvent.Pre}（上游<b>不按 overlay 类型过滤</b>，合成台/改装界面打开时
 * 取消<b>所有</b>原版 overlay 的 Pre）→ {@link RenderGameOverlayEvent.Pre}：
 * 1.7.10 的 {@code GuiIngameForge#renderGameOverlay} 先投递 {@code Pre(ALL)} 并据此整体短路
 * （{@code GuiIngameForge.java:105}），因此「合成台/改装界面打开时取消全部原版 HUD」=
 * 对每个 {@code Pre} 事件直接 {@code setCanceled(true)}（含 ALL，效果是整套原版 HUD 不画）；
 * {@code Minecraft#screen} → {@code currentScreen}；{@code Screen} → {@link GuiScreen}。
 *
 * <p>
 * <b>1.7.10 特有（相对上游）</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（{@code cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115}）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在事件派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法(...)} 在这里改为 {@code public void 处理方法(...)}
 * （类名 / 方法名 / 参数 / 语义一律不变；非处理方法的静态工具方法与字段仍保持 {@code static}）。
 * 本类也因为「{@code EventBus#register(Class)} 在 1.7.10 无效」而提供 public 无参构造，
 * 由 {@code client/ClientProxy} 反射 {@code new} 后注册到正确的总线。
 */
public class PreventsHotbarEvent {

    @SubscribeEvent
    public void onRenderHotbarEvent(RenderGameOverlayEvent.Pre event) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        // 上游无 event.getOverlay() 类型判断：这两类界面打开时取消所有原版 overlay。
        // 1.7.10 取消 Pre(ALL) 会让 GuiIngameForge#renderGameOverlay 整体短路，等价「原版 HUD 全部不画」；
        // 但该短路点在 :105，早于 :108 的 setupOverlayRendering（GUI 正交帧）与 :109/:118 的混合基线 ⇒
        // 必须在取消前自己补上，否则后续 currentScreen.drawScreen 仍在世界透视矩阵下绘制（GUI 全不可见）。
        if (screen instanceof GunSmithTableScreen || screen instanceof GunRefitScreen) {
            if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.entityRenderer != null) {
                    mc.entityRenderer.setupOverlayRendering();
                }
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            }
            event.setCanceled(true);
        }
    }
}
