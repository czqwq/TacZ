package com.tacz.guns.client.renderer.item;

import net.minecraftforge.client.event.GuiScreenEvent;

import com.tacz.guns.util.RenderDistance;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 记录「屏幕本体正在绘制」的深度，供 VBO/缓存路径判断当前是否处于 GUI 绘制阶段。
 *
 * <h2>1.20 → 1.7.10 对照</h2>
 * <ul>
 * <li>上游挂在 {@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)} 上监听
 * {@code ScreenEvent.Render.Pre / Post}。1.7.10 既没有 {@code @Mod.EventBusSubscriber}
 * 也没有 {@code ScreenEvent}；等价事件是
 * {@link GuiScreenEvent.DrawScreenEvent.Pre} / {@link GuiScreenEvent.DrawScreenEvent.Post}。</li>
 * <li>上游注释说「{@code ForgeHooksClient} 即使 {@code Pre} 取消也会 post {@code Post}」；
 * 1.7.10 的 {@code DrawScreenEvent.Pre} 是 {@code @Cancelable}，{@code GuiScreen} 的调用点同样保证成对出现，
 * 因此这里保留「计数器不可为负」的保护。</li>
 * </ul>
 *
 * <h2>1.7.10 事件总线的两条硬约束（本类为什么长这样）</h2>
 * <ol>
 * <li>必须 {@code register(<b>实例</b>)}：{@code cpw.mods.fml.common.eventhandler.EventBus#register(Object)}
 * 用 {@code target.getClass().getMethods()} 找 {@code @SubscribeEvent}；传 {@code Xxx.class} 会去
 * {@code java.lang.Class} 上找，一个处理器都注册不上（静默失效，事件永不到达）。</li>
 * <li>{@code @SubscribeEvent} <b>必须是实例方法</b>：FML 1.7.10 的 {@code ASMEventHandler}
 * 生成回调字节码时固定用 {@code INVOKEVIRTUAL}（{@code ASMEventHandler.java:115}），
 * 静态方法会在派发时抛 {@code IncompatibleClassChangeError}。</li>
 * </ol>
 * 因此本类提供公开无参构造器 + <b>实例</b>方法，注册方式为
 * {@code MinecraftForge.EVENT_BUS.register(new ScreenItemVboContext())}
 * （由 {@code client/init/ClientSetupEvent} 负责）。
 */
public final class ScreenItemVboContext {

    private static int depth;

    public ScreenItemVboContext() {}

    public static boolean isActive() {
        return depth > 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void beforeScreen(GuiScreenEvent.DrawScreenEvent.Pre event) {
        depth++;
        syncLodBypass();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (depth > 0) {
            depth--;
        }
        syncLodBypass();
    }

    /**
     * 把「当前是否在画界面」同步给 LOD 判定。
     *
     * <p>
     * 上游 {@code ScreenItemVboContext#isActive()} 被
     * {@code RenderDistance#inRenderHighPolyModelDistance} 直接读取，从而保证**界面里的物品预览
     * 永远用高模**。本移植两侧都写好了、但**没人把它们接上**：
     * {@link com.tacz.guns.util.RenderDistance#setScreenDrawActive(boolean)} 原本 0 调用者 ⇒
     * {@code screenDrawActive} 恒 {@code false}。
     *
     * <p>
     * 玩家可见后果：改装界面 / 展示柜预览里，若模型位移超出
     * {@code RenderConfig.GUN_LOD_RENDER_DISTANCE}，会显示**低模**（上游不会）。
     *
     * <p>
     * 与上游的差异：上游是「按需查询」（{@code isActive()} 每次调用取实时值），这里因为
     * {@link com.tacz.guns.util.RenderDistance} 位于 {@code util/}、不能反查 client 类，
     * 改为「进出界面时推送」。语义等价（推送窗口恰好覆盖整个 GUI 绘制期），代价是两次静态字段写。
     */
    private static void syncLodBypass() {
        RenderDistance.setScreenDrawActive(depth > 0);
    }
}
