package com.tacz.guns.event;

import com.tacz.guns.util.CycleTaskHelper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 驱动 {@link CycleTaskHelper} 的循环任务队列。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/ServerTickEvent.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}，注册到 <b>FML 事件总线</b>
 * （{@code FMLCommonHandler.instance().bus()}）：1.7.10 的 {@link TickEvent} 从 FML 总线发出；</li>
 * <li>{@code net.minecraftforge.event.TickEvent} → {@code cpw.mods.fml.common.gameevent.TickEvent}。</li>
 * </ul>
 * 与上游一致：不区分 {@code Phase}，因此每个服务端 tick 会 tick 两次（上游同样如此，保持行为一致）。
 */
public class ServerTickEvent {

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerTickEvent());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // 更新 CycleTaskHelper 中的任务
        CycleTaskHelper.tick();
    }
}
