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
 * 主动卸弹键（上游 {@code KeyModifier.ALT} + R）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/input/UnloadKey.java}。
 *
 * <p>
 * 平台替换：{@code KeyMapping → KeyBinding}（{@code GLFW_KEY_R → Keyboard.KEY_R}）、
 * {@code LocalPlayer → EntityPlayerSP}。
 * <b>{@code KeyModifier.ALT} 在 1.7.10 不存在</b> → 由
 * {@link GunInputHandler#isKeyDown(KeyBinding, int)} 在轮询时额外要求 Alt 按住
 * （{@link GunInputHandler#MODIFIER_ALT}），语义与上游一致：单独按 R 是换弹，Alt+R 是卸弹。
 */
@SideOnly(Side.CLIENT)
public class UnloadKey {

    public static final KeyBinding UNLOAD_KEY = new KeyBinding(
        "key.tacz.unload.desc",
        Keyboard.KEY_R,
        "key.category.tacz");

    private UnloadKey() {}

    public static void onUnloadPress() {
        if (!isInGame()) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && player.isEntityAlive()) {
            IClientPlayerGunOperator.fromLocalPlayer(player)
                .unloadAmmo();
        }
    }
}
