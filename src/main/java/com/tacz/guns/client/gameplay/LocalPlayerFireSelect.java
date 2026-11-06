package com.tacz.guns.client.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.event.common.GunFireSelectEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerFireSelect;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;

import cpw.mods.fml.relauncher.Side;

/**
 * 客户端切换开火模式。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gameplay/LocalPlayerFireSelect.java}。
 * 平台替换：{@code LocalPlayer → EntityPlayerSP}、{@code getMainHandItem() → getHeldItem()}、
 * {@code LogicalSide.CLIENT → Side.CLIENT}。无降级点。
 */
public class LocalPlayerFireSelect {

    private final LocalPlayerDataHolder data;
    private final EntityPlayerSP player;

    public LocalPlayerFireSelect(LocalPlayerDataHolder data, EntityPlayerSP player) {
        this.data = data;
        this.player = player;
    }

    public void fireSelect() {
        // 检查状态锁及蓄力状态
        if (data.clientStateLock || data.isCharging) {
            return;
        }
        // 暂定为主手（1.7.10 无双持）
        ItemStack mainHandItem = player.getHeldItem();
        if (!(mainHandItem != null && mainHandItem.getItem() instanceof IGun iGun)) {
            return;
        }
        if (MinecraftForge.EVENT_BUS.post(new GunFireSelectEvent(player, mainHandItem, Side.CLIENT))) {
            return;
        }

        TimelessAPI.getGunDisplay(mainHandItem)
            .ifPresent(gunIndex -> {
                // 播放音效
                SoundPlayManager.playFireSelectSound(player, gunIndex);
                // 发送切换开火模式的数据包，通知服务器
                NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerFireSelect());
                // 客户端切换开火模式
                if (iGun instanceof AbstractGunItem logicGun) {
                    logicGun.fireSelect(null, mainHandItem);
                }
                AttachmentPropertyManager.postChangeEvent(player, mainHandItem);
                // 动画状态机转移状态
                AnimationStateMachine<?> animationStateMachine = gunIndex.getAnimationStateMachine();
                if (animationStateMachine != null) {
                    animationStateMachine.trigger(GunAnimationConstant.INPUT_FIRE_SELECT);
                }
            });
    }
}
