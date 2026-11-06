package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 切换开火模式键（G）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/FireSelectKey.java}。
 * 平台替换：{@code KeyMapping → KeyBinding}、{@code LocalPlayer → EntityPlayerSP}。
 */
@SideOnly(Side.CLIENT)
public class FireSelectKey {

    public static final KeyBinding FIRE_SELECT_KEY = new KeyBinding(
        "key.tacz.fire_select.desc",
        Keyboard.KEY_G,
        "key.category.tacz");

    private FireSelectKey() {}

    public static boolean onFireSelectControllerPress(boolean isPress) {
        return isPress && onFireSelectPress();
    }

    public static boolean onFireSelectPress() {
        if (!isInGame()) {
            return false;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return false;
        }
        if (IGun.mainHandHoldGun(player)) {
            IClientPlayerGunOperator.fromLocalPlayer(player)
                .fireSelect();
            return true;
        }
        return false;
    }
}
