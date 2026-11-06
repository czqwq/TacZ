package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.client.gui.overlay.KillAmountOverlay;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.entity.TargetMinecart;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * 命中反馈（准心命中标记 + 命中音效 + 标靶车伤害计数）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/ClientHitMark.java}。
 * 上游用 {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)} 注册；1.7.10 没有该注记，
 * 改为由 {@code client/ClientProxy} 显式 {@code MinecraftForge.EVENT_BUS.register(ClientHitMark.class)}。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code net.minecraftforge.fml.LogicalSide} → cpw 的 {@link Side}（api 事件 {@code getLogicalSide()}
 * 在 1.7.10 版本返回 {@code Side}，见 api 跨包契约）；</li>
 * <li>{@code LocalPlayer} → {@link EntityPlayerSP}，{@code Minecraft#player} → {@code thePlayer}；</li>
 * <li>{@code player.distanceTo(entity)} → {@link Entity#getDistanceToEntity(Entity)}；</li>
 * <li>{@code Component.translatable(key, args)} → {@link ChatComponentTranslation}；</li>
 * <li><b>降级</b>：{@code LocalPlayer#displayClientMessage(Component, true)}（action bar）在 1.7.10 无对应
 * API，改用 {@code addChatMessage} 打到聊天栏，见 {@code message.tacz.target_minecart.hit} 的调用点
 * （{@code // TODO(L2)}）。</li>
 * </ul>
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
public class ClientHitMark {

    public static long lastHitTimestamp = -1;
    public static float damageAmount = 0;

    @SubscribeEvent
    public void onEntityHurt(EntityHurtByGunEvent.Post event) {
        Side logicalSide = event.getLogicalSide();
        if (logicalSide != Side.CLIENT) {
            return;
        }
        EntityLivingBase attacker = event.getAttacker();
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        Entity hurtEntity = event.getHurtEntity();
        if (player != null && player.equals(attacker) && hurtEntity != null) {
            ResourceLocation gunId = event.getGunId();
            ResourceLocation gunDisplayId = event.getGunDisplayId();
            RenderCrosshairEvent.markHitTimestamp();
            if (event.isHeadShot()) {
                RenderCrosshairEvent.markHeadShotTimestamp();
                TimelessAPI.getGunDisplay(gunDisplayId, gunId)
                    .ifPresent(index -> SoundPlayManager.playHeadHitSound(player, index));
            } else {
                TimelessAPI.getGunDisplay(gunDisplayId, gunId)
                    .ifPresent(index -> SoundPlayManager.playFleshHitSound(player, index));
            }

            if (hurtEntity instanceof TargetMinecart) {
                if (GunTime.isWithin(lastHitTimestamp, RenderConfig.DAMAGE_COUNTER_RESET_TIME.get())) {
                    damageAmount += event.getAmount();
                } else {
                    damageAmount = event.getAmount();
                }
                float distance = player.getDistanceToEntity(hurtEntity);
                // TODO(L2): 上游 LocalPlayer#displayClientMessage(Component, true) 是 action bar（物品栏上方提示）。
                // 1.7.10 没有 action bar API（GuiIngame 只有聊天栏 / 标题），这里降级为聊天栏消息。
                player.addChatMessage(
                    new ChatComponentTranslation(
                        "message.tacz.target_minecart.hit",
                        String.format("%.1f", damageAmount),
                        String.format("%.2f", distance)));

                lastHitTimestamp = GunTime.nowMillis();
            }
        }
    }

    @SubscribeEvent
    public void onEntityKill(EntityKillByGunEvent event) {
        Side logicalSide = event.getLogicalSide();
        if (logicalSide != Side.CLIENT) {
            return;
        }
        EntityLivingBase attacker = event.getAttacker();
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && player.equals(attacker)) {
            RenderCrosshairEvent.markKillTimestamp();
            KillAmountOverlay.markTimestamp();
            TimelessAPI.getGunDisplay(event.getGunDisplayId(), event.getGunId())
                .ifPresent(index -> SoundPlayManager.playKillSound(player, index));
            if (event.isHeadShot()) {
                RenderCrosshairEvent.markHeadShotTimestamp();
            }
        }
    }

    public static void resetTimestamp() {
        lastHitTimestamp = -1;
        damageAmount = 0;
    }
}
