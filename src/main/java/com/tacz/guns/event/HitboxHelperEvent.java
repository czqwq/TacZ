package com.tacz.guns.event;

import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.util.HitboxHelper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 hitbox 延迟补偿的数据采集：每 tick 缓存玩家位置/命中箱/速度，玩家登出时清理。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/HitboxHelperEvent.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}，<b>注册到 FML 事件总线</b>
 * （{@code FMLCommonHandler.instance().bus()}）：1.7.10 的 {@link TickEvent} 与 FML 的
 * {@link PlayerEvent}（{@code PlayerLoggedOutEvent} 等）都从 FML 总线发出，不是
 * {@code MinecraftForge.EVENT_BUS}；</li>
 * <li>{@code net.minecraftforge.event.TickEvent} → {@code cpw.mods.fml.common.gameevent.TickEvent}；
 * {@code net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent} →
 * {@code cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent}；</li>
 * <li>{@code LogicalSide} → {@link Side}；{@code event.getEntity()} → 直接读 {@code event.player}
 * （FML PlayerEvent 只有 {@code public final EntityPlayer player} 字段）；</li>
 * <li>{@code Player} → {@code EntityPlayer}（{@code HitboxHelper} 的 1.7.10 签名同样换成 EntityPlayer）。</li>
 * </ul>
 */
public class HitboxHelperEvent {

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new HitboxHelperEvent());
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!OtherConfig.SERVER_HITBOX_LATENCY_FIX.get()) {
            return;
        }
        if (event.side == Side.SERVER && event.phase == TickEvent.Phase.END) {
            HitboxHelper.onPlayerTick(event.player);
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        HitboxHelper.onPlayerLoggedOut(event.player);
    }
}
