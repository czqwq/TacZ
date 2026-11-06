package com.tacz.guns.inventory;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.tacz.guns.GunMod;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.client.gui.AmmoStorageScreen;
import com.tacz.guns.client.gui.GunSmithTableScreen;

import cpw.mods.fml.common.network.IGuiHandler;

/**
 * 1.7.10 的 GUI 分发器（{@link IGuiHandler}），取代上游的 {@code MenuType} / {@code MenuProvider} / {@code IForgeMenuType}。
 *
 * <p>
 * 上游 1.20.1 的对应物分散在三处：
 * <ul>
 * <li>{@code init/ModContainer}：{@code DeferredRegister<MenuType<?>>} +
 * {@code IForgeMenuType.create((windowId, inv, data) -> ...)}，由网络包携带 {@code blockId}；</li>
 * <li>{@code block/entity/*BlockEntity#createMenu(int, Inventory, Player)}（实现了
 * {@code MenuProvider}，服务端 {@code player.openMenu(this)} 时被调用）；</li>
 * <li>{@code client/init/ModContainerScreen}：{@code MenuScreens.register(type, Screen::new)}。</li>
 * </ul>
 * 1.7.10 都没有，只有「一个 handler + 一个 int id + 方块坐标」，因此：
 * <ul>
 * <li>服务端：{@link #getServerGuiElement} 按 {@link GuiId} 在 {@code (x,y,z)} 处取 TileEntity 并构造
 * {@code Container}。<b>注意</b> 1.7.10 的 {@code Container} 构造器里没有 windowId：
 * FML 的 {@code FMLNetworkHandler.openGui} 会自己 {@code getNextWindowId()} 并写
 * {@code openContainer.windowId}，再 {@code addCraftingToCrafters(player)}；</li>
 * <li>客户端：{@link #getClientGuiElement} 返回 {@code GuiScreen}。本工作流只做 L1（服务端），
 * 因此这里返回 {@code null} 并留 {@code TODO(port L2, client-port)}。</li>
 * </ul>
 *
 * <p>
 * <b>注册</b>：由 {@code CommonProxy.preInit} 调
 * {@code NetworkRegistry.INSTANCE.registerGuiHandler(GunMod.instance, new GuiHandler())}。
 * 注册键既可以是 mod 实例也可以是 mod id 字符串——FML 内部用
 * {@code FMLCommonHandler#findContainerFor(Object)} 归一化成 {@code ModContainer} 再存表，
 * 两种写法等价（见 1.7.10 {@code NetworkRegistry#registerGuiHandler} 与
 * {@code FMLNetworkHandler#openGui}）。这里统一用 {@link GunMod#instance}，与注册处一致。
 */
public class GuiHandler implements IGuiHandler {

    /**
     * 打开一个 TACZ 的 GUI（上游 {@code ServerPlayer#openMenu(MenuProvider)} 的 1.7.10 对应物）。
     *
     * @param guiId {@link GuiId} 里的常量
     */
    public static void openGui(EntityPlayer player, World world, int x, int y, int z, int guiId) {
        if (player == null || world == null) {
            return;
        }
        player.openGui(GunMod.instance, guiId, world, x, y, z);
    }

    /** 打开枪械工作台（{@code x,y,z} 处必须是 {@link GunSmithTableBlockEntity}）。 */
    public static void openGunSmithTable(EntityPlayer player, World world, int x, int y, int z) {
        openGui(player, world, x, y, z, GuiId.GUN_SMITH_TABLE);
    }

    /** 打开弹药库（{@code x,y,z} 处必须是 {@link AmmoStorageBlockEntity}）。 */
    public static void openAmmoStorage(EntityPlayer player, World world, int x, int y, int z) {
        openGui(player, world, x, y, z, GuiId.AMMO_STORAGE);
    }

    @Override
    @Nullable
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        switch (id) {
            case GuiId.GUN_SMITH_TABLE:
                if (tileEntity instanceof GunSmithTableBlockEntity table) {
                    return new GunSmithTableMenu(player.inventory, table.getId());
                }
                break;
            case GuiId.AMMO_STORAGE:
                if (tileEntity instanceof AmmoStorageBlockEntity storage) {
                    return new AmmoStorageMenu(player.inventory, storage);
                }
                break;
            default:
                GunMod.LOGGER.warn("Unknown TACZ gui id {} at {}, {}, {}", id, x, y, z);
                break;
        }
        return null;
    }

    /**
     * 1.7.10 的客户端分发点：{@code IGuiHandler} 按 gui id <b>当场</b>返回 {@code GuiScreen}
     * （上游 1.20 是 {@code MenuScreens.register(type, Screen::new)}，见
     * {@code client/init/ModContainerScreen} 的类注释）。
     *
     * <p>
     * 客户端容器必须用「空数据」重建（1.7.10 只同步 windowId，不同步容器内容）：
     * {@link AmmoStorageMenu#AmmoStorageMenu(net.minecraft.entity.player.InventoryPlayer)}（storage=null）
     * 与 {@link GunSmithTableMenu#GunSmithTableMenu(net.minecraft.entity.player.InventoryPlayer, ResourceLocation)}
     * 都按 {@code @Nullable} 设计，客户端靠后续同步包/进度条填数据。
     * 工作台需要的 {@code blockId} 从客户端本地 TileEntity 读（方块实体随区块数据同步）。
     *
     * <p>
     * 与 {@code com.tacz.guns.client.**} 的耦合：本方法体引用客户端 GuiContainer 子类，
     * 但 1.7.10 的 {@code FMLNetworkHandler#openGui} 只在 {@code Side.CLIENT} 分支调用
     * {@code getClientGuiElement}（服务端分支走 {@code getServerGuiElement}），
     * JVM 的方法级惰性解析保证专用服务端不会加载这些类，因此与 {@code CommonProxy} 的
     * 「严禁 import client」约束不冲突（该约束针对的是服务端路径上的类）。
     */
    @Override
    @Nullable
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        switch (id) {
            case GuiId.GUN_SMITH_TABLE: {
                TileEntity tileEntity = world.getTileEntity(x, y, z);
                ResourceLocation blockId = tileEntity instanceof GunSmithTableBlockEntity table ? table.getId() : null;
                GunSmithTableMenu menu = new GunSmithTableMenu(player.inventory, blockId);
                return new GunSmithTableScreen(
                    menu,
                    player.inventory,
                    new ChatComponentTranslation("block.tacz.gun_smith_table"));
            }
            case GuiId.AMMO_STORAGE: {
                AmmoStorageMenu menu = new AmmoStorageMenu(player.inventory);
                return new AmmoStorageScreen(
                    menu,
                    player.inventory,
                    new ChatComponentTranslation("gui.tacz.ammo_storage.title"));
            }
            default:
                GunMod.LOGGER.warn("Unknown TACZ gui id {} at {}, {}, {}", id, x, y, z);
                return null;
        }
    }
}
