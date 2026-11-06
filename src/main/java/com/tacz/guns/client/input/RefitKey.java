package com.tacz.guns.client.input;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 枪械改装界面键（Z）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/RefitKey.java}。
 * 平台替换：{@code KeyMapping → KeyBinding}、{@code LocalPlayer → EntityPlayerSP}、
 * {@code mc.setScreen(...) → mc.displayGuiScreen(...)}。
 */
@SideOnly(Side.CLIENT)
public class RefitKey {

    public static final KeyBinding REFIT_KEY = new KeyBinding(
        "key.tacz.refit.desc",
        Keyboard.KEY_Z,
        "key.category.tacz");

    private RefitKey() {}

    public static void onRefitPress() {
        if (!isInGame()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !IGun.mainHandHoldGun(player)) {
            return;
        }
        if (IClientPlayerGunOperator.fromLocalPlayer(player)
            .isCharging()) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(player.getHeldItem());
        if (iGun != null && iGun.hasAttachmentLock(player.getHeldItem())) {
            return;
        }
        mc.displayGuiScreen(new GunRefitScreen());
    }
}
