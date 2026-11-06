package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.resource.pojo.data.gun.Bolt;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 换弹键（R）+ 空仓自动换弹。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/ReloadKey.java}。
 * 平台替换：{@code KeyMapping → KeyBinding}（{@code GLFW_KEY_R → Keyboard.KEY_R}）、
 * {@code LocalPlayer → EntityPlayerSP}、{@code tickCount → ticksExisted}、
 * {@code LogicalSide.CLIENT → Side.CLIENT}。
 */
@SideOnly(Side.CLIENT)
public class ReloadKey {

    /** 供事件总线按实例注册。 */
    public static final ReloadKey INSTANCE = new ReloadKey();

    public static final KeyBinding RELOAD_KEY = new KeyBinding(
        "key.tacz.reload.desc",
        Keyboard.KEY_R,
        "key.category.tacz");

    private ReloadKey() {}

    public static boolean onReloadPress() {
        if (isInGame()) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return false;
            }
            ItemStack held = player.getHeldItem();
            if (held != null && held.getItem() instanceof IGun iGun) {
                // 如果使用背包直读，且没有换弹冷却机制，则在输入时就屏蔽换弹
                if (iGun.useInventoryAmmo(held)) {
                    return false;
                }
                IClientPlayerGunOperator.fromLocalPlayer(player)
                    .reload();
                return true;
            }
        }
        return false;
    }

    public static boolean onReloadControllerPress(boolean isPress) {
        return isPress && onReloadPress();
    }

    @SubscribeEvent
    public void autoReload(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.side != Side.CLIENT) {
            return;
        }

        if (!KeyConfig.AUTO_RELOAD.get()) {
            return;
        }

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player.ticksExisted % 5 != 0) {
            return;
        }
        if (!(player instanceof IClientPlayerGunOperator operator)) {
            return;
        }
        if (operator.getDataHolder().autoReloadSuppressed) {
            return;
        }
        ItemStack currentGunItem = player.getHeldItem();
        if (currentGunItem != null && currentGunItem.getItem() instanceof IGun iGun) {
            // 如果使用背包直读，且没有换弹冷却机制，则在输入时就屏蔽换弹
            if (iGun.useInventoryAmmo(currentGunItem)) {
                return;
            }
            boolean flag = TimelessAPI.getCommonGunIndex(iGun.getGunId(currentGunItem))
                .map(
                    gunIndex -> gunIndex.getGunData()
                        .getBolt() != Bolt.OPEN_BOLT)
                .orElse(false);

            int ammoCount = iGun.getCurrentAmmoCount(currentGunItem)
                + (iGun.hasBulletInBarrel(currentGunItem) && flag ? 1 : 0);
            if (ammoCount > 0) {
                return;
            }
            operator.reload();
        }
    }
}
