package com.tacz.guns.client.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;

/**
 * 客户端检视（inspect）逻辑。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gameplay/LocalPlayerInspect.java}。
 * 平台替换：{@code LocalPlayer → EntityPlayerSP}、{@code getMainHandItem() → getHeldItem()}、
 * {@code IClientItemExtensions#getCustomRenderer() → MinecraftForgeClient.getItemRenderer(stack,
 * ItemRenderType.EQUIPPED_FIRST_PERSON)}。无降级点。
 */
public class LocalPlayerInspect {

    private final LocalPlayerDataHolder data;
    private final EntityPlayerSP player;

    public LocalPlayerInspect(LocalPlayerDataHolder data, EntityPlayerSP player) {
        this.data = data;
        this.player = player;
    }

    private static IItemRenderer getItemRenderer(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON);
    }

    public void inspect() {
        // 暂定只有主手可以检视
        ItemStack mainHandItem = player.getHeldItem();

        if (!(mainHandItem != null && mainHandItem.getItem() instanceof IGun iGun)) {
            if (getItemRenderer(mainHandItem) instanceof AnimateGeoItemRenderer<?, ?>renderer) {
                renderer.triggerAnimation(mainHandItem, GunAnimationConstant.INPUT_INSPECT);
            }
            return;
        }
        // 检查状态锁及蓄力状态
        if (data.clientStateLock || data.isCharging) {
            return;
        }
        GunData gunData = TimelessAPI.getClientGunIndex(iGun.getGunId(mainHandItem))
            .map(ClientGunIndex::getGunData)
            .orElse(null);
        if (gunData == null) {
            return;
        }
        TimelessAPI.getGunDisplay(mainHandItem)
            .ifPresent(gunIndex -> {
                Bolt boltType = gunData.getBolt();
                boolean noAmmo;
                if (boltType == Bolt.OPEN_BOLT) {
                    noAmmo = iGun.useInventoryAmmo(mainHandItem)
                        ? ClientAmmoCount.getCached(player, mainHandItem, iGun) <= 0
                        : iGun.getCurrentAmmoCount(mainHandItem) <= 0;
                } else {
                    noAmmo = !iGun.hasBulletInBarrel(mainHandItem);
                }
                // 触发 inspect，停止播放声音
                SoundPlayManager.stopPlayGunSound();
                SoundPlayManager.playInspectSound(player, gunIndex, noAmmo);
                var animationStateMachine = gunIndex.getAnimationStateMachine();
                if (animationStateMachine != null) {
                    animationStateMachine.trigger(GunAnimationConstant.INPUT_INSPECT);
                }
            });
    }
}
