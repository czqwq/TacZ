package com.tacz.guns.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 帧间插值系数（partialTicks）的客户端缓存。
 *
 * <p>
 * <b>为什么需要它</b>：上游 1.20.1 到处用 {@code Minecraft#getFrameTime()}（= {@code deltaTracker} 的
 * partial tick）。1.7.10 里这个值在 {@code Minecraft.timer.renderPartialTicks}，
 * 而 {@code Minecraft.timer} 是 <b>private</b> 字段（{@code net.minecraft.util.Timer} 本身是 public，
 * {@code Timer.renderPartialTicks} 也是 public，只有持有者字段不可见），
 * 且生产环境字段名会被 SRG 混淆，反射不可靠。
 *
 * <p>
 * 因此改为在 {@link TickEvent.RenderTickEvent}（每帧开始，{@code Phase.START}）里缓存一份，
 * 任何需要帧间插值的地方调用 {@link #getPartialTick()}。
 *
 * <p>
 * <b>注册方式</b>：{@code ClientProxy.init} 里
 * {@code FMLCommonHandler.instance().bus().register(ClientTickHelper.INSTANCE)}。
 * （{@code RenderTickEvent} 是 cpw 的 {@code TickEvent}，走 FML 总线，<b>不是</b>
 * {@code MinecraftForge.EVENT_BUS}；且 1.7.10 的 {@code EventBus#register(Object)} 用
 * {@code target.getClass()} 反射找 {@code @SubscribeEvent} 方法，<b>传 Class 对象什么都不会注册</b>，
 * 所以这里必须按 {@link #INSTANCE} 注册。）
 *
 * <p>
 * 注意：1.20 的 {@code getFrameTime()} 在同一帧的渲染阶段是常量，本实现在
 * {@code Phase.START} 时更新，语义一致。
 */
@SideOnly(Side.CLIENT)
public final class ClientTickHelper {

    /** 供 FML 事件总线按实例注册（见类 javadoc 的「注册方式」）。 */
    public static final ClientTickHelper INSTANCE = new ClientTickHelper();

    /** 初值取 1.0F，与 1.20 {@code DeltaTracker.Timer#getGameTimeDeltaPartialTick} 的静止默认一致。 */
    private static volatile float partialTick = 1.0F;

    private ClientTickHelper() {}

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            partialTick = event.renderTickTime;
        }
    }

    /**
     * 对应 1.20 {@code Minecraft#getFrameTime()}。
     *
     * @return 当前帧的帧间插值系数，范围通常 {@code [0, 1]}
     */
    public static float getPartialTick() {
        return partialTick;
    }
}
