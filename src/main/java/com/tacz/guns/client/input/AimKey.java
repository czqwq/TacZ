package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.KeyConfig;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 瞄准键（鼠标右键）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/AimKey.java}。
 * 平台替换：{@code KeyMapping → KeyBinding}（鼠标键用 {@code -100 + 1}）、
 * {@code LocalPlayer → EntityPlayerSP}、{@code KeyMapping#isDown() → GunInputHandler#isKeyDown}。
 */
@SideOnly(Side.CLIENT)
public class AimKey {

    /** 供事件总线按实例注册。 */
    public static final AimKey INSTANCE = new AimKey();

    public static final KeyBinding AIM_KEY = new KeyBinding("key.tacz.aim.desc", -100 + 1, "key.category.tacz");
    private static boolean controllerAimDown;

    private AimKey() {}

    public static boolean onAimPress() {
        return onAimInput(true);
    }

    /**
     * 合并键鼠和手柄的按住状态，同时保留切换武器时的瞄准预输入。
     */
    @SubscribeEvent
    public void onAimHoldingPreInput(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !KeyConfig.HOLD_TO_AIM.get()) {
            return;
        }
        onAimInput(false);
    }

    public static boolean onAimControllerPress(boolean isPress) {
        controllerAimDown = isPress;
        return onAimInput(isPress);
    }

    public static void setControllerAimDown(boolean isDown) {
        controllerAimDown = isDown;
    }

    private static boolean onAimInput(boolean isPress) {
        if (!isInGame()) {
            return false;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return false;
        }
        if (!(player instanceof IClientPlayerGunOperator operator)) {
            return false;
        }
        if (!IGun.mainHandHoldGun(player)) {
            return false;
        }
        boolean holdToAim = KeyConfig.HOLD_TO_AIM.get();
        if (!isPress && !holdToAim) {
            return false;
        }
        boolean aim = holdToAim ? GunInputHandler.isKeyDown(AIM_KEY) || controllerAimDown : !operator.isAim();
        if (operator.isAim() != aim) {
            operator.aim(aim);
        }
        return true;
    }

    @SubscribeEvent
    public void cancelAim(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!isInGame()) {
            controllerAimDown = false;
        }
        EntityPlayerSP player = mc.thePlayer;
        if (!(player instanceof IClientPlayerGunOperator operator)) {
            return;
        }
        // 1.7.10 无旁观者模式，只剩「不在游戏内」一项
        if (operator.isAim() && !isInGame()) {
            IClientPlayerGunOperator.fromLocalPlayer(player)
                .aim(false);
        }
    }
}
