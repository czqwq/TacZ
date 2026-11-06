package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 检视键（H）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/InspectKey.java}。
 * 平台替换：{@code KeyMapping → KeyBinding}、{@code LocalPlayer → EntityPlayerSP}。
 */
@SideOnly(Side.CLIENT)
public class InspectKey {

    public static final KeyBinding INSPECT_KEY = new KeyBinding(
        "key.tacz.inspect.desc",
        Keyboard.KEY_H,
        "key.category.tacz");

    private InspectKey() {}

    public static boolean onInspectPress() {
        if (isInGame()) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return false;
            }
            IClientPlayerGunOperator.fromLocalPlayer(player)
                .inspect();
            return true;
        }
        return false;
    }

    public static boolean onInspectControllerPress(boolean isPress) {
        return isPress && onInspectPress();
    }
}
