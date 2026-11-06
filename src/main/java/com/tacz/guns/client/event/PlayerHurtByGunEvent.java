package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * 玩家自己中枪时，按枪械数据调整受伤晃动（受击镜头摇晃倍率）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/PlayerHurtByGunEvent.java}。
 * 上游 {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)} → 1.7.10 由 {@code ClientProxy} 显式注册到
 * {@code MinecraftForge.EVENT_BUS}。
 *
 * <p>
 * 平台替换：{@code LogicalSide} → cpw {@link Side}；{@code LocalPlayer} → {@link EntityPlayerSP}；
 * {@code Minecraft#player} → {@code thePlayer}。无其它差异。
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
public class PlayerHurtByGunEvent {

    @SubscribeEvent
    public void onPlayerHurtByGun(EntityHurtByGunEvent.Post event) {
        Side logicalSide = event.getLogicalSide();
        if (logicalSide != Side.CLIENT) {
            return;
        }
        Entity hurtEntity = event.getHurtEntity();
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        // 当受伤的是自己的时候，触发受伤晃动的调整参数
        if (player != null && player.equals(hurtEntity)) {
            ResourceLocation gunId = event.getGunId();
            TimelessAPI.getCommonGunIndex(gunId)
                .ifPresent(index -> {
                    float tweakMultiplier = index.getGunData()
                        .getHurtBobTweakMultiplier();
                    GunHurtBobTweak.markTimestamp(tweakMultiplier);
                });
        }
    }
}
