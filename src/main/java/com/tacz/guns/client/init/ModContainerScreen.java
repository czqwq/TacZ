package com.tacz.guns.client.init;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 容器（Container）→ 界面（GuiScreen）的绑定。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/init/ModContainerScreen.java}，
 * 在 {@code FMLClientSetupEvent} 里
 * {@code MenuScreens.register(GunSmithTableMenu.TYPE, GunSmithTableScreen::new)} +
 * {@code MenuScreens.register(ModContainer.AMMO_STORAGE_MENU.get(), AmmoStorageScreen::new)}。
 *
 * <p>
 * <b>1.7.10 无需任何注册</b>：1.7.10 没有 {@code MenuType} 注册表，也没有 {@code MenuScreens}；
 * 「哪个 Container 配哪个 GuiScreen」由
 * {@code cpw.mods.fml.common.network.IGuiHandler#getClientGuiElement(int id, EntityPlayer, World, int, int, int)}
 * <b>按 gui id 当场返回</b>。本工程的对应实现是
 * {@link com.tacz.guns.inventory.GuiHandler}（由 {@code CommonProxy.preInit} 里
 * {@code NetworkRegistry.INSTANCE.registerGuiHandler(GunMod.instance, new GuiHandler())} 注册）。
 *
 * <p>
 * 因此本类在 1.7.10 侧<b>保持零逻辑</b>，只作为「上游这个注册点在哪」的定位锚点保留，
 * 由 {@link ClientSetupEvent#onClientSetup()} 调用（调用本身是无副作用的）。
 *
 * <p>
 * 真正的客户端界面分发点在 {@code inventory/GuiHandler#getClientGuiElement}（已实现：
 * {@code GUN_SMITH_TABLE → GunSmithTableScreen}、{@code AMMO_STORAGE → AmmoStorageScreen}，
 * 见 {@code inventory/GuiHandler.java:116}）。上游 {@code MenuScreens.register} 的语义由那里承担。
 */
@SideOnly(Side.CLIENT)
public final class ModContainerScreen {

    private ModContainerScreen() {}

    /** 对应上游 {@code FMLClientSetupEvent} 里的 {@code MenuScreens.register(...)}；1.7.10 无对应物。 */
    public static void clientSetup() {
        // 1.7.10 的等价注册点是 inventory/GuiHandler#getClientGuiElement，见类 javadoc
    }
}
