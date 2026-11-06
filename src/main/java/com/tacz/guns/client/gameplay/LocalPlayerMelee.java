package com.tacz.guns.client.gameplay;

import javax.annotation.Nullable;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerMelee;
import com.tacz.guns.resource.pojo.data.attachment.MeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunDefaultMeleeData;

import cpw.mods.fml.relauncher.Side;

/**
 * 客户端近战（刺刀/枪托/推击）逻辑。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gameplay/LocalPlayerMelee.java}。
 * 平台替换：{@code LocalPlayer → EntityPlayerSP}、{@code getMainHandItem() → getHeldItem()}、
 * {@code LogicalSide.CLIENT → Side.CLIENT}。无降级点。
 */
public class LocalPlayerMelee {

    public static final String MELEE_STOCK_ANIMATION = "melee_stock";
    private final LocalPlayerDataHolder data;
    private final EntityPlayerSP player;

    public LocalPlayerMelee(LocalPlayerDataHolder data, EntityPlayerSP player) {
        this.data = data;
        this.player = player;
    }

    public void melee() {
        // 检查状态锁及蓄力状态
        if (data.clientStateLock || data.isCharging) {
            return;
        }
        // 暂定为主手
        ItemStack mainHandItem = player.getHeldItem();
        if (!(mainHandItem != null && mainHandItem.getItem() instanceof IGun iGun)) {
            return;
        }
        GunDisplayInstance display = TimelessAPI.getGunDisplay(mainHandItem)
            .orElse(null);
        if (display == null) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(mainHandItem);
        // 先检查枪口有没有近战属性
        ResourceLocation muzzleId = iGun.getAttachmentId(mainHandItem, AttachmentType.MUZZLE);
        MeleeData muzzleMeleeData = getMeleeData(muzzleId);
        if (muzzleMeleeData != null) {
            this.doMuzzleMelee(display);
            return;
        }

        ResourceLocation stockId = iGun.getAttachmentId(mainHandItem, AttachmentType.STOCK);
        MeleeData stockMeleeData = getMeleeData(stockId);
        if (stockMeleeData != null) {
            this.doStockMelee(display);
            return;
        }

        TimelessAPI.getClientGunIndex(gunId)
            .ifPresent(index -> {
                GunDefaultMeleeData defaultMeleeData = index.getGunData()
                    .getMeleeData()
                    .getDefaultMeleeData();
                if (defaultMeleeData == null) {
                    return;
                }
                String animationType = defaultMeleeData.getAnimationType();
                if (MELEE_STOCK_ANIMATION.equals(animationType)) {
                    this.doStockMelee(display);
                    return;
                }
                this.doPushMelee(display);
            });
    }

    private boolean prepareMelee() {
        // 锁上状态锁
        data.lockState(operator -> operator.getSynMeleeCoolDown() > 0);
        // 触发近战事件
        GunMeleeEvent gunMeleeEvent = new GunMeleeEvent(player, player.getHeldItem(), Side.CLIENT);
        return !MinecraftForge.EVENT_BUS.post(gunMeleeEvent);
    }

    private void doMuzzleMelee(GunDisplayInstance display) {
        if (prepareMelee()) {
            SoundPlayManager.playMeleeBayonetSound(player, display);
            // 发送执行近战的数据包，通知服务器
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerMelee());
            // 动画状态机转移状态
            AnimationStateMachine<?> animationStateMachine = display.getAnimationStateMachine();
            if (animationStateMachine != null) {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_BAYONET_MUZZLE);
            }
        }
    }

    private void doStockMelee(GunDisplayInstance display) {
        if (prepareMelee()) {
            SoundPlayManager.playMeleeStockSound(player, display);
            // 发送执行近战的数据包，通知服务器
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerMelee());
            // 动画状态机转移状态
            AnimationStateMachine<?> animationStateMachine = display.getAnimationStateMachine();
            if (animationStateMachine != null) {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_BAYONET_STOCK);
            }
        }
    }

    private void doPushMelee(GunDisplayInstance display) {
        if (prepareMelee()) {
            // 播放音效
            SoundPlayManager.playMeleePushSound(player, display);
            // 发送执行近战的数据包，通知服务器
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerMelee());
            // 动画状态机转移状态
            AnimationStateMachine<?> animationStateMachine = display.getAnimationStateMachine();
            if (animationStateMachine != null) {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_BAYONET_PUSH);
            }
        }
    }

    @Nullable
    private MeleeData getMeleeData(ResourceLocation attachmentId) {
        if (DefaultAssets.isEmptyAttachmentId(attachmentId)) {
            return null;
        }
        return TimelessAPI.getClientAttachmentIndex(attachmentId)
            .map(
                index -> index.getData()
                    .getMeleeData())
            .orElse(null);
    }
}
