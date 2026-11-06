package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerZoom;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 倍镜倍率调整键（V，仅在瞄准时有效）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/ZoomKey.java}。
 * 平台替换：{@code KeyMapping → KeyBinding}、{@code LocalPlayer → EntityPlayerSP}。
 */
@SideOnly(Side.CLIENT)
public class ZoomKey {

    public static final KeyBinding ZOOM_KEY = new KeyBinding("key.tacz.zoom.desc", Keyboard.KEY_V, "key.category.tacz");

    private ZoomKey() {}

    public static boolean onZoomControllerPress(boolean isPress) {
        return isPress && onZoomPress();
    }

    public static boolean onZoomPress() {
        if (!isInGame()) {
            return false;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return false;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator.isAim()) {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerZoom());
            return true;
        }
        return false;
    }
}
