package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.client.gui.overlay.KillAmountOverlay;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.particle.MuzzleParticleManager;
import com.tacz.guns.client.particle.ThirdPersonMuzzleParticleManager;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.client.renderer.scope.ScopeLensRenderer;
import com.tacz.guns.client.resource.pojo.display.gun.RangefinderManager;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * Clears timestamps tied to a client world after SBM resets its animation clock.
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/ClientGunTimeResetEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)}、
 * 监听 {@code ClientPlayerNetworkEvent.LoggingOut}）。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code ClientPlayerNetworkEvent.LoggingOut} → FML 的
 * {@link FMLNetworkEvent.ClientDisconnectionFromServerEvent}（注册在
 * {@code FMLCommonHandler.instance().bus()}）；{@code EventPriority.LOWEST} 保持不变；</li>
 * <li>{@code LocalPlayer} → {@link EntityPlayerSP}，{@code Minecraft#player} → {@code thePlayer}。</li>
 * </ul>
 * 逐条调用与上游一一对应（顺序也保持一致，因为部分重置依赖前一步的清理）。
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
public final class ClientGunTimeResetEvent {

    /**
     * 上游是 private 构造（纯静态工具类）；1.7.10 的事件注册<b>必须传实例</b>
     * （{@code EventBus#register(Class)} 只会去 {@code java.lang.Class} 上找 {@code @SubscribeEvent}，
     * 静态处理器不触发），因此改为 public 无参构造，供 {@code ClientProxy} 注册时 {@code new}。
     */
    public ClientGunTimeResetEvent() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLoggingOut(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ScopeLensRenderer.close();
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            LocalPlayerDataHolder dataHolder = IClientPlayerGunOperator.fromLocalPlayer(player)
                .getDataHolder();
            dataHolder.cancelShootFutures();
            dataHolder.resetAmmoCountCache();
        }
        GunTime.resetEpoch();
        LocalPlayerDataHolder.clientClickButtonTimestamp = -1L;
        RefitTransform.resetTimestamps();
        RenderCrosshairEvent.resetTimestamps();
        KillAmountOverlay.resetTimestamp();
        ClientHitMark.resetTimestamp();
        GunHudOverlay.resetTimestamp();
        FirstPersonRenderGunEvent.resetTimestamps();
        CameraSetupEvent.resetTimestamp();
        MuzzleFlashRender.resetTimestamps();
        MuzzleParticleManager.resetMuzzleLight();
        ThirdPersonMuzzleParticleManager.resetTimestamps();
        RangefinderManager.resetTimestamps();
        GunHurtBobTweak.resetTimestamp();
    }
}
