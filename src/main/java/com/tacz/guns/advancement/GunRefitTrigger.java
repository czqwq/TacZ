package com.tacz.guns.advancement;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * 搬运自上游 1.20.1 {@code advancement/GunRefitTrigger.java}（SimpleCriterionTrigger）。
 *
 * <p>
 * <b>1.7.10 差异（记录）</b>：1.7.10 无 advancement 体系，上游 trigger 语义降级为 Forge 事件广播。
 * 调用点（network/message/ClientMessageRefitGun 等）保持
 * {@code GunRefitTrigger.INSTANCE.trigger(player, beforeRefit, gunItem)} 不变，
 * 内部 post {@link GunRefitEvent}，供 Achievement/其它模组监听。
 */
public class GunRefitTrigger {

    public static final GunRefitTrigger INSTANCE = new GunRefitTrigger();
    public static final ResourceLocation ID = new ResourceLocation("tacz", "gun_refit");

    /**
     * 1.7.10 无 CriteriaTriggers 注册表，保留空实现以对齐上游调用顺序。
     */
    public static void register() {
        // 1.7.10 无 advancement criterion 注册；订阅 GunRefitEvent 即可
    }

    public void trigger(EntityPlayerMP player, ItemStack before, ItemStack after) {
        // 上游：ItemStack.matches(before, after) 不相等才触发；1.7.10 用 ItemStack.areItemStacksEqual
        if (!ItemStack.areItemStacksEqual(before, after)) {
            MinecraftForge.EVENT_BUS.post(new GunRefitEvent(player, before, after));
        }
    }

    /**
     * 1.7.10 降级事件：替代 1.20 的 {@code SimpleCriterionTrigger#trigger}。
     */
    public static class GunRefitEvent extends Event {

        private final EntityPlayerMP player;
        private final ItemStack before;
        private final ItemStack after;

        public GunRefitEvent(EntityPlayerMP player, ItemStack before, ItemStack after) {
            this.player = player;
            this.before = before;
            this.after = after;
        }

        public EntityPlayerMP getPlayer() {
            return player;
        }

        public ItemStack getBefore() {
            return before;
        }

        public ItemStack getAfter() {
            return after;
        }
    }
}
