package com.tacz.guns.event;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.tacz.guns.api.item.IGun;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 主手拿枪时禁止左键交互/破坏方块（避免枪械左键逻辑与挖掘冲突）。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/PreventGunClick.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}；{@code @SubscribeEvent} 换包；</li>
 * <li>{@code PlayerInteractEvent.LeftClickBlock} → 单一 {@link PlayerInteractEvent} +
 * {@code event.action == Action.LEFT_CLICK_BLOCK}；</li>
 * <li>{@code getItemInHand(InteractionHand.MAIN_HAND)} → {@code getHeldItem()}（1.7.10 无双持）。</li>
 * </ul>
 */
public class PreventGunClick {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new PreventGunClick());
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        // 只要主手有枪，那么禁止交互
        ItemStack itemInHand = event.entityPlayer.getHeldItem();
        if (itemInHand != null && itemInHand.getItem() instanceof IGun) {
            event.setCanceled(true);
        }
    }
}
