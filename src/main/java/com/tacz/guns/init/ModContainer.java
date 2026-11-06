package com.tacz.guns.init;

/**
 * 搬运自上游 1.20.1 {@code init/ModContainer.java}。
 *
 * <p>
 * <b>1.7.10 无 {@code MenuType} 注册表</b>（也没有 {@code IForgeMenuType}/{@code NetworkHooks.openScreen}）：
 * 容器由 {@code CommonProxy.preInit} 里的
 * {@code NetworkRegistry.INSTANCE.registerGuiHandler(GunMod.instance, new inventory.GuiHandler())} 统一管理，
 * GUI 数字 id 定义在 {@code com.tacz.guns.inventory.GuiId}：
 * <ul>
 * <li>{@code GUN_SMITH_TABLE} → {@code inventory.GunSmithTableMenu} / {@code inventory.GuiHandler}</li>
 * <li>{@code AMMO_STORAGE} → {@code inventory.AmmoStorageMenu} / {@code inventory.GuiHandler}</li>
 * </ul>
 * 因此本类 1.7.10 不需要注册任何东西，仅保留上游类名与 {@code register()} 调用点（CommonProxy 顺序不变）。
 * 上游依据：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/ModContainer.java}。
 */
public class ModContainer {

    public static void register() {
        // 1.7.10 无 MenuType；1 个 IGuiHandler + GuiId 常量即等价（见类 javadoc）
    }
}
