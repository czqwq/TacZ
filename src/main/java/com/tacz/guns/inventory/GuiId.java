package com.tacz.guns.inventory;

/**
 * 1.7.10 的 GUI id 常量。
 *
 * <p>
 * 上游 1.20.1 没有「GUI id」这个概念：每个界面是一个 {@code MenuType}（{@code init/ModContainer} 里
 * 用 {@code DeferredRegister} 注册，客户端用 {@code MenuScreens.register} 绑定 Screen），
 * 服务端通过 {@code Player#openMenu(MenuProvider)} 打开。
 *
 * <p>
 * 1.7.10 的做法是「整数 id + {@link cpw.mods.fml.common.network.IGuiHandler}」：
 * <ul>
 * <li>模组在初始化阶段 {@code NetworkRegistry.INSTANCE.registerGuiHandler(GunMod.instance, handler)}；</li>
 * <li>服务端调 {@code player.openGui(mod, guiId, world, x, y, z)}，FML 用 guiId 回调
 * {@link GuiHandler#getServerGuiElement(int, net.minecraft.entity.player.EntityPlayer,
 * net.minecraft.world.World, int, int, int)} 拿 {@code Container}，
 * 同时把 {@code Container.windowId} 设成 {@code EntityPlayerMP#getNextWindowId()} 的结果；</li>
 * <li>客户端收 {@code FMLMessage.OpenGui} 包后回调
 * {@link GuiHandler#getClientGuiElement(int, net.minecraft.entity.player.EntityPlayer,
 * net.minecraft.world.World, int, int, int)} 拿 {@code GuiScreen}（L2 由 client-port 实现）。</li>
 * </ul>
 *
 * <p>
 * 因此这里的常量就是「窗口类型」的唯一标识，必须与 {@link GuiHandler} 里的 switch 分支一一对应；
 * 新增 GUI 时三处同时改：本类、{@link GuiHandler}、{@code CommonProxy}（注册 handler）。
 *
 * <p>
 * 上游对照：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/ModContainer.java}
 * （{@code GUN_SMITH_TABLE_MENU} / {@code AMMO_STORAGE_MENU} 两个 {@code MenuType}）。
 */
public final class GuiId {

    /** 枪械工作台（{@code tacz:gun_smith_table} 与三个 {@code workbench_*}）——对应上游 {@code GunSmithTableMenu}。 */
    public static final int GUN_SMITH_TABLE = 0;

    /** 弹药库（{@code tacz:ammo_storage} 及 gold / diamond 变体）——对应上游 {@code AmmoStorageMenu}。 */
    public static final int AMMO_STORAGE = 1;

    private GuiId() {}
}
