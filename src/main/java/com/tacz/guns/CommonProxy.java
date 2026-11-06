package com.tacz.guns;

import com.tacz.guns.config.ConfigManager;
import com.tacz.guns.event.EventHandlers;
import com.tacz.guns.init.CapabilityRegistry;
import com.tacz.guns.init.CommandRegistry;
import com.tacz.guns.init.CommonRegistry;
import com.tacz.guns.init.CompatRegistry;
import com.tacz.guns.init.ModAttributes;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModContainer;
import com.tacz.guns.init.ModCreativeTabs;
import com.tacz.guns.init.ModEntities;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModLootModifiers;
import com.tacz.guns.init.ModPainting;
import com.tacz.guns.init.ModParticles;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.init.ModSounds;
import com.tacz.guns.inventory.GuiHandler;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.network.SyncConfigSyncManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.resource.GunPackType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

/**
 * 通用 / 服务端侧代理（{@code @SidedProxy(serverSide = ...)}）。
 *
 * <p>
 * 上游 1.20.1 没有代理层：注册分散在 {@code GunMod()} 构造器（{@code DeferredRegister} + 三个 config +
 * 枪包注册）、{@code init/CommonRegistry}（mod 事件总线上的 {@code FMLCommonSetupEvent} /
 * {@code FMLLoadCompleteEvent} / {@code EntityAttributeModificationEvent} / {@code AddPackFindersEvent}）、
 * 以及 {@code client/init/ClientSetupEvent}（客户端专属）。
 * 1.7.10 必须用 {@link cpw.mods.fml.common.SidedProxy} 把两侧切开，因此这里把它重组成四个阶段。
 *
 * <h2>preInit —— 对应上游 {@code GunMod()} 构造器 + mod 事件总线的注册顺序</h2>
 * <ol>
 * <li>{@link ConfigManager#initCommon}：上游构造器第 1 段
 * （{@code registerConfig(COMMON/SERVER/CLIENT)}，三个 config 对象）</li>
 * <li>枪包类型/默认枪包：上游构造器第 2 段（{@code GunPackLoader.INSTANCE.packType}）与第 5 段
 * （{@code registerDefaultExtraGunPack()}）。1.7.10 没有 {@code PackType}（CLIENT_RESOURCES / SERVER_DATA），
 * 枪包一律从 {@code <gamedir>/tacz/} 扫描，由 {@code GunPackLoader} 自行决定，
 * 见 {@code docs/PORT_STATUS.md} 的「跨包契约」段</li>
 * <li>内容注册：上游构造器第 3 段（{@code DeferredRegister} 的注册顺序 = blocks → tile entities → tabs →
 * items → entities → recipes → container → sounds → particles → attributes → paintings；
 * blocks 必须在 items 之前，1.7.10 的 {@code ItemBlock} 注册依赖方块已存在）。
 * 1.7.10 的 {@code ModXxx} 类都是「{@code public static void register()}」形态，由这里按上游顺序显式调用。</li>
 * <li>{@link CommonRegistry#registerAttributes()}：上游 {@code EntityAttributeModificationEvent}
 * （给所有生物挂 {@code BULLET_RESISTANCE}）</li>
 * <li>{@link AttachmentPropertyManager#registerModifier()}：上游构造器最后一段</li>
 * <li>Forge 事件总线注册：{@code event/EventHandlers.registerAll()}（上游的
 * {@code @Mod.EventBusSubscriber} 类）、{@link NetworkTaskQueue}（1.7.10 版 {@code enqueueWork}）、
 * {@link SyncConfigSyncManager}（1.7.10 版 SERVER 配置同步 + 握手）</li>
 * <li>GUI handler 注册（1.7.10 特有：{@code NetworkRegistry#registerGuiHandler}），
 * 上游用 {@code MenuType} + {@code NetworkHooks.openScreen}</li>
 * </ol>
 *
 * <h2>init —— 对应上游 {@code CommonRegistry#onSetupEvent}（{@code FMLCommonSetupEvent}）</h2>
 * <ol>
 * <li>{@link NetworkHandler#init()}</li>
 * <li>{@code ModSyncedEntityData.init()}</li>
 * <li>{@code TargetHitTrigger.register()} / {@code GunRefitTrigger.register()}（1.7.10 无 advancement，
 * 由 common-game 提供同签名空实现，见其类注释）</li>
 * </ol>
 *
 * <h2>postInit</h2>
 * {@link CommonRegistry#markLoadComplete()}：对应上游 {@code FMLLoadCompleteEvent} 的
 * {@code LOAD_COMPLETE} 标志（{@code CommonRegistry#isLoadComplete()}）。
 *
 * <h2>serverStarting</h2>
 * {@link CommandRegistry#registerCommands}：对应上游 {@code RegisterCommandsEvent}（brigadier 命令树）。
 *
 * <p>
 * <b>本类严禁 import {@code com.tacz.guns.client.*}</b>（L1 编译边界：客户端包未完成时会被整体排除）。
 * 需要触发客户端行为时走 {@code com.tacz.guns.network.ClientSideHooks}，由 {@code ClientProxy} 注入实现。
 */
public class CommonProxy {

    /**
     * 对应上游 {@code GunMod()} 构造器 + {@code FMLPreInitializationEvent}。
     */
    public void preInit(FMLPreInitializationEvent event) {
        // 1) 配置最先加载：上游构造器里的 registerConfig(COMMON/SERVER/CLIENT)
        // （1.7.10 的 CLIENT 配置在 ClientProxy#preInit 里单独加载，服务端不碰渲染配置）
        ConfigManager.initCommon(event.getModConfigurationDirectory());

        // 2) 枪包作用侧：上游 GunPackLoader.INSTANCE.packType = dist.isClient() ? CLIENT_RESOURCES : SERVER_DATA
        // 1.7.10 无 PackType，用 resource/GunPackType 枚举记录（加载行为两侧一致，见该类注释）
        GunPackLoader.INSTANCE.packType = GunPackType.current();

        // 3) 内容注册（上游 DeferredRegister 顺序：blocks → tile entities → tabs → items → entities →
        // recipes → container → sounds → particles → attributes → paintings）
        //
        // 1.7.10：所有 Mod* 注册类都是「普通 public static 字段 + register()」，由这里按上游顺序显式调用。
        ModBlocks.register();
        // 上游 DeferredRegister 里 TABS 在 ITEMS 之前，因此标签页先于物品建立
        ModCreativeTabs.register();
        ModItems.register();
        ModEntities.register();
        ModContainer.register();
        ModSounds.register();
        ModAttributes.register();
        ModParticles.register();
        ModLootModifiers.register();
        // 本工作流已完成、可以立即调用的内容注册类：
        ModPainting.register();

        // 4) 上游 EntityAttributeModificationEvent：给所有生物挂上 BULLET_RESISTANCE
        CommonRegistry.registerAttributes();
        // 1.7.10 用 EntityConstructing 事件逐个实体挂属性 → 需要把 CommonRegistry 挂到事件总线
        CommonRegistry.register();
        // 上游 RegisterCapabilitiesEvent（1.7.10 无 capability 系统 → 空实现，保留上游调用形状）
        CapabilityRegistry.register();

        // 5) 上游构造器最后两段：默认枪包导出 + 配件属性修改器
        GunMod.registerDefaultExtraGunPack();
        // 枪包目录扫描（必须在 registerDefaultExtraGunPack 之后：先导出内置默认枪包再扫；幂等可重复调）
        // 对应上游 AddPackFindersEvent → GunPackLoader.INSTANCE；1.7.10 无该事件，由这里显式触发
        GunPackLoader.INSTANCE.reloadPackList();
        AttachmentPropertyManager.registerModifier();
        // 资源管理器事件监听（PlayerLoggedIn 下发枪包数据 / WorldUnload 清理），common-resource 提供
        CommonAssetsManager.registerEventHandlers();

        // 6) 1.7.10 事件总线（上游 @Mod.EventBusSubscriber 的等价物）
        EventHandlers.registerAll();
        // 1.7.10 的 cpw.mods.fml.common.gameevent.*（TickEvent / PlayerEvent）由 FML 私有总线发出，
        // 与 MinecraftForge.EVENT_BUS 是<b>两个不同实例</b>（FMLCommonHandler.java:90/97）。
        // 这两个处理器订阅的全是 cpw 事件，必须挂 FML 总线，否则永不回调：
        // NetworkTaskQueue 不排空 → 所有网络包「收到但不生效」；SyncConfigSyncManager 登录时不同步配置/枪包。
        // （对应类内部也有构造器自注册兜底；EventBus#register 幂等，不会重复回调。）
        FMLCommonHandler.instance()
            .bus()
            .register(new NetworkTaskQueue());
        FMLCommonHandler.instance()
            .bus()
            .register(new SyncConfigSyncManager());

        // 7) 1.7.10 特有的 GUI handler 注册（上游是 MenuType + NetworkHooks.openScreen）
        NetworkRegistry.INSTANCE.registerGuiHandler(GunMod.instance, new GuiHandler());
    }

    /**
     * 对应上游 {@code init/CommonRegistry#onSetupEvent}（{@code FMLCommonSetupEvent}，{@code event.enqueueWork}）。
     *
     * <p>
     * 实际动作在 {@link CommonRegistry#commonSetup()}：{@code NetworkHandler::init} →
     * {@code ModSyncedEntityData::init} → {@code TargetHitTrigger::register} → {@code GunRefitTrigger::register}
     * （顺序与上游 {@code enqueueWork} 一致），坩埚交互在 1.7.10 无对应物（见该类注释）。
     */
    public void init(FMLInitializationEvent event) {
        CommonRegistry.commonSetup();
        // 1.7.10 配方必须放在 init 阶段：Forge 的原版矿词（logWood / ingotIron …）在 preInit 之后才注册，
        // preInit 里用 String 矿词做配方项会取到 null 并让 CraftingManager 抛 NPE（实测崩溃，整批配方被丢弃）。
        // 另外 1.7.10 带矿词的配方必须走 Forge ShapedOreRecipe（见 ModRecipe 类注释）。
        ModRecipe.register();
    }

    /**
     * 对应上游 {@code CommonRegistry#onLoadComplete}（{@code FMLLoadCompleteEvent}）+ {@code InterModEnqueueEvent}。
     */
    public void postInit(FMLPostInitializationEvent event) {
        CommonRegistry.markLoadComplete();
        // 上游 InterModEnqueueEvent（其它模组构造完成后才探测）→ 1.7.10 落在 postInit；
        // 内部全是 Loader.isModLoaded + 反射调用，缺失库静默跳过（AGENTS.md 决策 2）
        CompatRegistry.register();
    }

    /**
     * 对应上游 {@code RegisterCommandsEvent}（{@code CommandRegistry}）。
     */
    public void serverStarting(FMLServerStartingEvent event) {
        // 上游 AddReloadListenerEvent / 服务端启动时重载枪包数据
        // （内部会先 GunPackLoader.INSTANCE.reloadPackList()，重复调无害）
        CommonAssetsManager.reload();
        CommandRegistry.registerCommands(event);
    }

    /**
     * 对应上游 Forge 在服务端停止时的配置落盘（{@code ConfigTracker#unloadConfigs}）。
     * <p>
     * 1.7.10 的 {@code Configuration} 不会自动保存：{@code ConfigValue#set} 只改内存 + {@code Property}，
     * 必须显式 {@code save()}。不落盘时 {@code /tacz config} 与 {@code /tacz overwrite} 的改动重启即丢。
     */
    public void serverStopping(FMLServerStoppingEvent event) {
        ConfigManager.saveAll();
    }
}
