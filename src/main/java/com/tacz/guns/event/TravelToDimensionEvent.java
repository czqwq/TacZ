package com.tacz.guns.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 修正玩家跨维度时枪械数据不刷新的问题（服务端刷新）。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/TravelToDimensionEvent.java}
 * （监听 {@code EntityTravelToDimensionEvent}，对任意 {@code LivingEntity} 生效）。
 * <p>
 * 1.7.10 差异（<b>降级</b>）：1.7.10 Forge <b>不存在</b> {@code EntityTravelToDimensionEvent}，
 * 只有 FML 的 {@link PlayerEvent.PlayerChangedDimensionEvent}（仅玩家、且是「已经换完维度」之后触发）。
 * 因此：
 * <ul>
 * <li>只处理<b>玩家</b>（其它生物跨维度时不会刷新，见 TODO）；</li>
 * <li>触发时机从「传送前」变成「传送后」，{@code initialData()} 的时机晚一步（数据仍是同一份，语义可接受）；</li>
 * <li>{@code LivingEntity} → {@link EntityPlayer}；{@code getMainHandItem()} → {@code getHeldItem()}
 * （1.7.10 无双持，EMPTY 用 {@code null} 判空）；</li>
 * <li>注册到 <b>FML 事件总线</b>（{@code FMLCommonHandler.instance().bus()}），
 * FML 的 PlayerEvent 不走 {@code MinecraftForge.EVENT_BUS}。</li>
 * </ul>
 * <p>
 * TODO(port): 上游 TravelToDimensionEvent.java:17-22 对所有 {@code LivingEntity} 生效（例如枪械掉进传送门、
 * 刷怪生物持枪跨维度）。1.7.10 里非玩家实体跨维度没有公共事件，需在 {@code com.tacz.guns.mixin} 里
 * Mixin {@code Entity#travelToDimension(int)} 调用 {@code IGunOperator.fromLivingEntity(...).initialData()}
 * 才能补齐；在该 Mixin 落地前，非玩家实体不刷新。
 */
public class TravelToDimensionEvent {

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new TravelToDimensionEvent());
    }

    @SubscribeEvent
    public void onTravelToDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        EntityPlayer player = event.player;
        ItemStack mainHand = player.getHeldItem();
        if (mainHand != null && mainHand.getItem() != null && mainHand.getItem() instanceof IGun) {
            IGunOperator.fromLivingEntity(player)
                .initialData();
        }
    }
}
