package com.tacz.guns.advancement;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * 搬运自上游 1.20.1 {@code advancement/TargetHitTrigger.java}（SimpleCriterionTrigger）。
 *
 * <p>
 * <b>1.7.10 差异（记录）</b>：1.7.10 没有 advancement / criterion trigger / CriteriaTriggers 体系
 * （只有 {@code Achievement}，且其条件编译进代码）。上游 trigger 的语义在本移植里降级为
 * <b>Forge 事件广播</b>：调用点（靶标方块/标靶车）保持 {@code TargetHitTrigger.INSTANCE.trigger(player, distance)}
 * 不变，内部 post 一个 {@link TargetHitEvent}，供 Achievement/其它模组监听；具体 advancement JSON
 * （data/tacz/advancements/**）在 1.7.10 无法注册，作为已知差异记录在 docs/PORT_STATUS.md。
 */
public class TargetHitTrigger {

    public static final TargetHitTrigger INSTANCE = new TargetHitTrigger();
    public static final ResourceLocation ID = new ResourceLocation("tacz", "target_hit");

    /**
     * 1.7.10 无 CriteriaTriggers 注册表，保留空实现以对齐上游调用顺序（CommonProxy/CommonRegistry 会调用）。
     */
    public static void register() {
        // 1.7.10 无 advancement criterion 注册；事件监听者直接订阅 TargetHitEvent 即可
    }

    public void trigger(EntityPlayerMP player, double distance) {
        MinecraftForge.EVENT_BUS.post(new TargetHitEvent(player, distance));
    }

    /**
     * 1.7.10 降级事件：替代 1.20 的 {@code SimpleCriterionTrigger#trigger}。
     */
    public static class TargetHitEvent extends Event {

        private final EntityPlayerMP player;
        private final double distance;

        public TargetHitEvent(EntityPlayerMP player, double distance) {
            this.player = player;
            this.distance = distance;
        }

        public EntityPlayerMP getPlayer() {
            return player;
        }

        public double getDistance() {
            return distance;
        }
    }
}
