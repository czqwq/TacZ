package com.tacz.guns.client.gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.ChatComponentTranslation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerUnloadGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;

/**
 * 客户端主动卸弹。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gameplay/LocalPlayerUnload.java}。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code LocalPlayer} → {@link EntityPlayerSP}、{@code getMainHandItem()} → {@code getHeldItem()}；</li>
 * <li>{@code Component.translatable(...)} + {@code displayClientMessage(comp, true)}（悬浮 action bar）→
 * {@link ChatComponentTranslation} + {@code addChatMessage}。
 * <b>1.7.10 没有 action bar</b>（{@code displayClientMessage} 的 {@code actionBar=true} 分支），
 * 降级为普通聊天栏提示；服务端 {@code ClientMessagePlayerUnloadGun} 已是同样处理（同一降级口径）。</li>
 * <li>{@code player.isSpectator()} → 1.7.10 无旁观者模式，条件删除；</li>
 * <li>{@code player.isAlive()} → {@code isEntityAlive()}、{@code player.isSprinting()} 同名；</li>
 * <li>{@code getInventory().selected} → {@code inventory.currentItem}；</li>
 * <li>{@code Minecraft#gameMode.ensureHasSentCarriedItem()} → 直接发 {@code C09PacketHeldItemChange}
 * （1.7.10 的等价方法 {@code PlayerControllerMP#syncCurrentPlayItem()} 是 private，见
 * {@code PlayerControllerMP.java:345-353}）。</li>
 * </ul>
 */
public class LocalPlayerUnload {

    private final LocalPlayerDataHolder data;
    private final EntityPlayerSP player;

    public LocalPlayerUnload(LocalPlayerDataHolder data, EntityPlayerSP player) {
        this.data = data;
        this.player = player;
    }

    public void unloadAmmo() {
        if (isLocalIdle()) {
            showCannotUnload();
            return;
        }
        ItemStack stack = player.getHeldItem();
        if (!(stack != null && stack.getItem() instanceof AbstractGunItem gun)) {
            showCannotUnload();
            return;
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (isSyncIdle(operator)) {
            showCannotUnload();
            return;
        }
        var gunId = gun.getGunId(stack);
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(gunId)
            .orElse(null);
        if (index == null) {
            showCannotUnload();
            return;
        }
        if (!index.canUnloadAmmo() || gun.useInventoryAmmo(stack)) {
            player.addChatMessage(new ChatComponentTranslation("message.tacz.unload.unsupported"));
            return;
        }
        if (gun.getCurrentAmmoCount(stack) <= 0 && (index.getGunData()
            .getBolt() == Bolt.OPEN_BOLT || !gun.hasBulletInBarrel(stack))) {
            player.addChatMessage(new ChatComponentTranslation("message.tacz.unload.no_ammo"));
            return;
        }

        data.lockState();
        data.autoReloadSuppressed = true;
        data.chargeProgress = 0;
        TimelessAPI.getGunDisplay(stack)
            .ifPresent(display -> {
                SoundPlayManager.stopPlayGunSound();
                var stateMachine = display.getAnimationStateMachine();
                if (stateMachine != null) {
                    stateMachine.trigger(GunAnimationConstant.INPUT_INSPECT_BREAK_OUT);
                }
            });
        ensureHasSentCarriedItem();
        NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerUnloadGun(player.inventory.currentItem, gunId));
    }

    /** 复刻 1.7.10 {@code PlayerControllerMP#syncCurrentPlayItem()}（private）。 */
    private void ensureHasSentCarriedItem() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() != null) {
            mc.getNetHandler()
                .addToSendQueue(new C09PacketHeldItemChange(player.inventory.currentItem));
        }
    }

    private void showCannotUnload() {
        player.addChatMessage(new ChatComponentTranslation("message.tacz.unload.cannot"));
    }

    private boolean isSyncIdle(IGunOperator operator) {
        return operator.getSynReloadState()
            .getStateType()
            .isReloading() || operator.getSynIsBolting()
            || operator.getSynShootCoolDown() != 0
            || operator.getSynDrawCoolDown() != 0
            || operator.getSynMeleeCoolDown() != 0
            || operator.getSynSprintTime() > 0
            || IClientPlayerGunOperator.fromLocalPlayer(player)
                .getClientShootCoolDown() > 0;
    }

    private boolean isLocalIdle() {
        // 1.7.10 无旁观者模式：上游 isSpectator() 一项删除
        return !player.isEntityAlive() || data.clientStateLock
            || data.isCharging
            || data.isBolting
            || data.clientReloadStarting
            || !data.isShootRecorded
            || data.hasPendingShoot()
            || data.clientIsAiming
            || data.clientAimingProgress > 0
            || player.isSprinting();
    }
}
