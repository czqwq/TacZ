package com.tacz.guns;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tacz.guns.api.resource.ResourceManager;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

/**
 * TACZ 1.7.10 入口点。
 *
 * <p>
 * 上游 1.20.1 的入口是 {@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/GunMod.java}：构造器里
 * <ol>
 * <li>注册三个 {@code ModConfig}（COMMON / SERVER / CLIENT）</li>
 * <li>{@code GunPackLoader.INSTANCE.packType = dist.isClient() ? CLIENT_RESOURCES : SERVER_DATA}</li>
 * <li>把各个 {@code DeferredRegister} 挂到 mod 事件总线（顺序：BLOCKS, TILE_ENTITIES, TABS, ITEMS,
 * ENTITY_TYPES, RECIPE_SERIALIZERS, RECIPE_TYPES, LOOT_MODIFIER_SERIALIZERS, CONTAINER_TYPE, SOUNDS,
 * PARTICLE_TYPES, ATTRIBUTES, PAINTINGS）</li>
 * <li>kubejs 兼容（{@code ModList.get().isLoaded("kubejs")}，本工程改为反射式 compat 骨架）</li>
 * <li>{@code registerDefaultExtraGunPack()} + {@code AttachmentPropertyManager.registerModifier()}</li>
 * </ol>
 *
 * <p>
 * 1.7.10 没有 {@code DeferredRegister} / {@code FMLJavaModLoadingContext} / {@code Dist} / {@code ModConfig}，
 * 对应物是 {@code GameRegistry} 系列静态注册 + 生命周期事件 + {@code SidedProxy} 分流。
 * 因此本类只保留入口要素（{@code @Mod} 注解、公开常量、{@code SidedProxy}、模组实例）与四段式生命周期转发，
 * <b>全部注册动作下沉到 {@link CommonProxy}（通用/服务端）与 {@code com.tacz.guns.client.ClientProxy}（客户端）</b>；
 * 调用顺序在 {@link CommonProxy} 的 javadoc 里逐条对应上游位置。
 */
@Mod(
    modid = GunMod.MOD_ID,
    name = "Timeless and Classics Guns: Zero",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]")
public class GunMod {

    public static final String MOD_ID = "tacz";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /**
     * 默认模型包文件夹，对应 assets/tacz/custom/tacz_default_gun
     */
    public static final String DEFAULT_GUN_PACK_NAME = "tacz_default_gun";

    /**
     * 模组实例。1.7.10 在 {@code NetworkRegistry#registerGuiHandler(mod, handler)}、
     * {@code EntityPlayer#openGui(mod, ...)} 里需要 mod 对象；上游 1.20.1 用 {@code ModLoadingContext} 拿容器，
     * 因此不需要这个字段（移植特有字段，供 common-game 的 GUI/容器使用）。
     */
    public static GunMod instance;

    @SidedProxy(clientSide = "com.tacz.guns.client.ClientProxy", serverSide = "com.tacz.guns.CommonProxy")
    public static CommonProxy proxy;

    public GunMod() {
        instance = this;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        instance = this;
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    /**
     * 上游 1.20 Forge 在服务端停止时统一落盘配置（{@code ConfigTracker#unloadConfigs}）；
     * 1.7.10 没有该机制，由 {@link CommonProxy#serverStopping} 显式保存。
     */
    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        proxy.serverStopping(event);
    }

    /**
     * 上游 {@code GunMod#registerDefaultExtraGunPack()} 的等价物：把 jar 内
     * {@code assets/tacz/custom/tacz_default_gun} 注册为「启动时导出到 {@code <gamedir>/tacz/} 的额外枪包」。
     *
     * <p>
     * 1.7.10 差异：上游在构造器里调用（此时 mod 目录已就绪）；1.7.10 放在 {@code preInit}
     * （由 {@link CommonProxy#preInit} 调用），因为导出动作依赖游戏目录/配置目录初始化。
     */
    public static void registerDefaultExtraGunPack() {
        String jarDefaultPackPath = String.format("/assets/%s/custom/%s", MOD_ID, DEFAULT_GUN_PACK_NAME);
        ResourceManager.registerExportResource(GunMod.class, jarDefaultPackPath);
    }
}
