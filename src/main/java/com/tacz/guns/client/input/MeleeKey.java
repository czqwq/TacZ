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
 * 近战键（V）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/MeleeKey.java}。
 * 平台替换：{@code KeyMapping → KeyBinding}、{@code LocalPlayer → EntityPlayerSP}。
 */
@SideOnly(Side.CLIENT)
public class MeleeKey {

    public static final KeyBinding MELEE_KEY = new KeyBinding(
        "key.tacz.melee.desc",
        Keyboard.KEY_V,
        "key.category.tacz");

    private MeleeKey() {}

    public static boolean onMeleeControllerPress(boolean isPress) {
        return isPress && onMeleePress();
    }

    public static boolean onMeleePress() {
        if (!isInGame()) {
            return false;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return false;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (!operator.isAim()) {
            operator.melee();
            return true;
        }
        return false;
    }
}
