package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.gameplay.LocalPlayerSprint;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.compat.controllable.ControllableCompat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 开火键（鼠标左键）+ 全自动连发 / 蓄力驱动。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/ShootKey.java}。
 * 平台替换：{@code KeyMapping → KeyBinding}、{@code GLFW_MOUSE_BUTTON_LEFT → -100 + 0}
 * （1.7.10 用「负数 keyCode = 鼠标键」约定，见 {@code GameSettings.isKeyDown}：
 * {@code code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code)}）、
 * {@code LocalPlayer → EntityPlayerSP}、{@code KeyMapping#isDown() → GunInputHandler#isKeyDown}。
 */
@SideOnly(Side.CLIENT)
public class ShootKey {

    /** 供事件总线按实例注册（见 {@link GunInputHandler} 类 javadoc 的「注册形态」）。 */
    public static final ShootKey INSTANCE = new ShootKey();

    public static final KeyBinding SHOOT_KEY = new KeyBinding("key.tacz.shoot.desc", -100 + 0, "key.category.tacz");
    private static boolean lastTimeShootSuccess = false;
    private static boolean controllerShootDown = false;

    private ShootKey() {}

    @SubscribeEvent
    public void autoShoot(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isInGame()) {
            return;
        }
        LocalPlayerSprint.stopSprint = false;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getHeldItem();
        if (mainHandItem != null && mainHandItem.getItem() instanceof IGun iGun) {
            FireMode fireMode = iGun.getFireMode(mainHandItem);
            boolean isBurstAuto = fireMode == FireMode.BURST
                && TimelessAPI.getCommonGunIndex(iGun.getGunId(mainHandItem))
                    .map(
                        index -> index.getGunData()
                            .getBurstData()
                            .isContinuousShoot())
                    .orElse(false);
            IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
            boolean isShootDown = GunInputHandler.isKeyDown(SHOOT_KEY) || controllerShootDown;
            boolean canContinuouslyShoot = fireMode == FireMode.AUTO || isBurstAuto;
            boolean shouldCharge = isShootDown && (canContinuouslyShoot || !lastTimeShootSuccess);
            if (operator.chargeShoot(shouldCharge)) {
                LocalPlayerSprint.stopSprint = true;
                if (!canContinuouslyShoot && lastTimeShootSuccess) {
                    // 非全自动情况，禁止连续开火，也不应在按住上一枪扳机时继续蓄力
                    return;
                }
                if (operator.shoot() == ShootResult.SUCCESS) {
                    lastTimeShootSuccess = true;
                    ControllableCompat.onGunShoot(mainHandItem, fireMode);
                }
            }
            if (isShootDown) {
                LocalPlayerSprint.stopSprint = true;
            } else {
                lastTimeShootSuccess = false;
                SoundPlayManager.resetDryFireSound();
            }
        }
    }

    public static boolean shootControllerTick(boolean isShootDown) {
        controllerShootDown = isShootDown;
        return false;
    }

    /** 供手柄/其它输入源参考：本键是否按住。 */
    public static boolean isDown() {
        return GunInputHandler.isKeyDown(SHOOT_KEY);
    }
}
