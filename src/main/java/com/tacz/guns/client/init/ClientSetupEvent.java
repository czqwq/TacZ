package com.tacz.guns.client.init;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPGeoItemRenderer;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.client.gui.overlay.DisplayCaseLockHintOverlay;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.client.gui.overlay.HeatBarOverlay;
import com.tacz.guns.client.gui.overlay.InteractKeyTextOverlay;
import com.tacz.guns.client.gui.overlay.KillAmountOverlay;
import com.tacz.guns.client.input.GunInputHandler;
import com.tacz.guns.client.renderer.item.AmmoItemRenderer;
import com.tacz.guns.client.renderer.item.AttachmentItemRenderer;
import com.tacz.guns.client.renderer.item.BlockModelGuiItemRenderer;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.renderer.item.GunSmithTableItemRenderer;
import com.tacz.guns.client.renderer.scope.ScopeLensRenderer;
import com.tacz.guns.client.renderer.scope.ScopeThermalRenderer;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.compat.ar.ARCompat;
import com.tacz.guns.compat.controllable.ControllableCompat;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.shouldersurfing.ShoulderSurfingCompat;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.GunSmithTableItem;
import com.tacz.guns.item.ItemClientHooks;
import com.tacz.guns.mcshim.GuiGraphics;
import com.tacz.guns.resource.GunPackLoader;
import com.tacz.guns.util.LaserColorUtil;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端初始化 + HUD 叠加层注册。
 *
 * <p>
 * 上游：tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/init/ClientSetupEvent.java
 * （{@code @Mod.EventBusSubscriber(bus = MOD, value = CLIENT)}，订阅 7 个 MOD 总线事件）。
 *
 * <p>
 * 上游处理点 → 1.7.10 落点：{@code RegisterShadersEvent} → 无该事件，镜片/热成像着色器由
 * {@code ScopeLensRenderer#init()} 在 {@link #onClientSetup()} 显式编译（内部会连带 {@code ScopeThermalRenderer#init()}）；
 * {@code RegisterKeyMappingsEvent} → {@link GunInputHandler#registerKeyMappings()}；{@code
 * RegisterClientTooltipComponentFactoriesEvent} → 无 tooltip 组件工厂，由 {@code ItemTooltipEvent} + 手工绘制
 * 承担；{@code RegisterGuiOverlaysEvent} → {@code RenderGameOverlayEvent.Post}（{@link #onRenderOverlay}）+
 * {@code Pre(CROSSHAIRS)}（{@link #onRenderOverlayPre}，补「准星被替换时 Post 不触发」的场景）；
 * {@code FMLClientSetupEvent} → {@link #onClientSetup()}；{@code RegisterClientReloadListenersEvent} →
 * {@code IResourceManagerReloadListener} + {@code TextureStitchEvent.Post}。
 *
 * <p>
 * <b>注册形态</b>：{@code EventBus#register(Object)} 用 {@code target.getClass()} 反射找 {@code @SubscribeEvent}
 * 方法，<b>传 {@code XxxClass.class} 不会注册任何东西</b> ⇒ 本类提供 {@link #INSTANCE}。
 */
@SideOnly(Side.CLIENT)
public final class ClientSetupEvent {

    /** 供事件总线按实例注册（见类 javadoc 的「注册形态」）。 */
    public static final ClientSetupEvent INSTANCE = new ClientSetupEvent();

    /** 显示在最上层（{@code ElementType.ALL} 的 Post 阶段绘制）的 HUD。 */
    private final List<HudLayer> aboveAll = new ArrayList<>();
    /** 显示在准星之上（{@code ElementType.CROSSHAIRS} 的 Post 阶段绘制）的提示。 */
    private final List<HudLayer> aboveCrosshair = new ArrayList<>();

    private ClientSetupEvent() {}

    /**
     * 对应上游 {@code FMLClientSetupEvent} 里的 {@code event.enqueueWork(...)} 序列。
     * 1.7.10 没有「延迟到客户端就绪再执行」的 enqueue 队列，客户端初始化本身就是单线程一次性的，
     * 因此这里直接顺序执行。
     */
    public static void onClientSetup() {
        // 注册自己的的硬编码第三人称动画（上游 ThirdPersonManager::registerDefault）
        step("ThirdPersonManager.registerDefault", ThirdPersonManager::registerDefault);

        // 挂板染色（上游 RegisterColorHandlersEvent.Block/.Item + ItemProperties）
        step("GunPegboardColors.setup", GunPegboardColors::setup);

        // 容器界面（上游 MenuScreens.register）
        step("ModContainerScreen.clientSetup", ModContainerScreen::clientSetup);

        // 实体 / 方块实体渲染器（上游 EntityRenderersEvent.RegisterRenderers）
        step("ModEntitiesRender.register", ModEntitiesRender::register);

        // 物品渲染器（上游 Item#initializeClient / IClientItemExtensions#getCustomRenderer）
        // **P0**：1.7.10 若不显式 MinecraftForgeClient.registerItemRenderer，物品只会渲染成 2D 图标，
        // 枪械因此退回 ModernKineticGunItem#registerIcons 的扁平占位（bow/compass/arrow），
        // 手上与第三人称都不会出现 3D 模型。
        step("Item renderers", ClientSetupEvent::registerItemRenderers);

        // 物品显示名（上游 Item#getName 的枪包索引分支）—— 由 item/ItemClientHooks 钩子注入
        step("Item name providers", ClientSetupEvent::registerItemNameProviders);

        // 激光颜色「默认色」解析器。
        //
        // 上游 `LaserColorUtil#getLaserColor` 的两处回退是**静态类型**调用：
        // - 配件：`TimelessAPI.getClientAttachmentIndex(id).map(ClientAttachmentIndex::getLaserConfig)
        // .map(LaserConfig::getDefaultColor).orElse(0xFF0000)`
        // - 枪械：`TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getLaserConfig)
        // .map(LaserConfig::getDefaultColor).orElse(0xFF0000)`
        // `util/LaserColorUtil` 位于 common 侧、不能 import client 类，所以留了
        // `setClientDefaultColorResolver` 钩子 —— 而它原本 **0 调用者** ⇒ 默认色恒 `0xFF0000`（恒红）。
        // 受影响的是改装界面激光颜色滑条的**初始色**（`HSVSliderGroup:85/88`）；
        // 光束渲染走另一条重载（`getLaserColor(stack, laserConfig)`，反射取 `getDefaultColor()`），颜色是对的。
        //
        // 解析器是惰性求值（每次调用才查索引），所以这里在 init 注册不影响「索引稍后才装载」的时序。
        step(
            "LaserColorUtil client resolver",
            () -> LaserColorUtil.setClientDefaultColorResolver(ClientSetupEvent::resolveLaserDefaultColor));

        // 枪包语言注入（上游靠 mixin/client/LanguageMixin 注入 1.20 的 I18n；1.7.10 走 Forge LanguageRegistry）
        //
        // **必须注入全部语种，不能只注入当前语种**：LanguageManager 是 Minecraft 构造器里注册的
        // 第一个资源重载监听器，它的 onResourceManagerReload 会按「当前语种」从 LanguageRegistry 取表
        // 再 merge 进 locale（LanguageManager.java:88）；mod 的监听器排它之后，所以「切完语言那一刻」再注入
        // 在 1.7.10 上不可达（除非 mixin Locale，而 LanguageMixin 目前有意未启用）。
        // 一次性把所有枪包的所有语种灌进 LanguageRegistry 后，玩家在 GuiLanguage 里怎么切都能取到对应表。
        // 时机：本方法由 ClientProxy.init 调用，晚于 GunPackLoader#reloadPackList，
        // 早于加载期最后一次 refreshResources（FMLClientHandler 先 initializeMods 再 refreshResources）。
        step("Gun pack languages", GunPackLoader::injectAllGunPackLanguages);

        // 粒子工厂（上游 RegisterParticleProvidersEvent）
        step("ParticleFactoryRegistry.register", ParticleFactoryRegistry::register);

        // 键位（上游 RegisterKeyMappingsEvent）
        step("GunInputHandler.registerKeyMappings", GunInputHandler::registerKeyMappings);

        // 与 Shoulder Surfing Reloaded 的兼容（1.7.10 侧是反射探测骨架，缺失库静默跳过）
        step("ShoulderSurfingCompat.init", ShoulderSurfingCompat::init);

        // 与 Controllable 的兼容
        step("ControllableCompat.init", ControllableCompat::init);

        // 与 Accelerated Rendering 的兼容
        step("ARCompat.init", ARCompat::init);

        // 与 Player Animator 的兼容（上游 PlayerAnimatorCompat#init，反射探测，缺失库静默跳过）
        step("PlayerAnimatorCompat.init", PlayerAnimatorCompat::init);

        // 镜片 / 热成像渲染器的着色器与 FBO 初始化。
        // 上游对应 RegisterShadersEvent#registerShader，1.7.10 没有该事件，
        // 改为在客户端初始化期显式 init()（client-render 定稿：init() 取代 registerShader(...)）。
        // 注意：ScopeThermalRenderer.init() 已由 ScopeLensRenderer.init() 内部调用（ScopeLensRenderer.java:223），
        // 这里不能再单独调一次，否则热掩膜 shader 会被编译两次（r7-scope §5-1 已核实）。
        step("ScopeLensRenderer.init", ScopeLensRenderer::init);

        // HUD 叠加层（上游 RegisterGuiOverlaysEvent）+ 本类作为 Forge 总线处理器注册
        step("HUD overlays", () -> INSTANCE.registerOverlays());

        // 本类同时承载 RenderWorldLastEvent（世界渲染末尾）→ ScopeThermalRenderer.render()
        step("register ClientSetupEvent.INSTANCE", () -> MinecraftForge.EVENT_BUS.register(INSTANCE));

        GunMod.LOGGER.info("[TACZ] client setup finished");
    }

    /**
     * 逐个执行注册步骤，单个步骤失败只记 error 并继续。
     *
     * <p>
     * <b>为什么这里要兜异常（移植特有）</b>：1.7.10 的客户端初始化没有上游 MOD 总线的
     * 「单订阅者异常隔离」——任何一处注册抛异常都会让 {@code FMLInitializationEvent} 直接失败、
     * 客户端起不来。而本类调用的渲染器/粒子/compat 分属多个并行工作流，
     * 用一个 try/catch 把「某一块没做好」和「整个客户端起不来」解耦，
     * 同时把出问题的步骤名打进日志，便于定位。
     */
    private static void step(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            GunMod.LOGGER.error("[TACZ] client setup step failed (skipped): {}", name, t);
        }
    }

    /**
     * 注册自定义物品渲染器（{@link IItemRenderer}）。
     *
     * <p>
     * 上游 1.20.1：物品在 {@code Item#initializeClient(IClientItemExtensions)} 里给自己挂
     * {@code BlockEntityWithoutLevelRenderer}（{@code AbstractGunItem} / {@code AmmoItem} /
     * {@code AttachmentItem} / {@code GunSmithTableItem}），由 Forge 自动接管背包图标、手持、第三人称三种渲染。
     * 1.7.10 没有 {@code IClientItemExtensions} / BEWLR，也没有「物品自己声明渲染器」的机制，唯一入口是
     * {@link MinecraftForgeClient#registerItemRenderer(Item, IItemRenderer)}，<b>必须显式注册</b>。
     * 不注册的后果：物品退回 {@code Item#registerIcons} 的扁平占位图标（{@code ModernKineticGunItem} → bow、
     * {@code AttachmentItem} → compass、{@code AmmoItem} → arrow），且<b>手持与第三人称完全没有 3D 模型</b>。
     *
     * <p>
     * 注册清单是全仓 {@code implements IItemRenderer} 的 4 个类（都有 public 无参构造）：
     * {@code GunItemRendererWrapper}（{@code extends AnimateGeoItemRenderer}）→ 枪械、{@code AmmoItemRenderer} →
     * 弹药、{@code AttachmentItemRenderer} → 配件、{@code GunSmithTableItemRenderer} → 枪械工作台
     * （{@code ItemBlock}）。顺带接上 {@link ItemClientHooks#registerItemRenderer} 这个「common → client」钩子，
     * 使 common 侧调用 {@code ItemClientHooks.registerRenderer(item)} 能真正生效。
     */
    private static void registerItemRenderers() {
        // 注入 common 侧钩子（同一套分发逻辑，避免两处重复维护）
        ItemClientHooks.registerItemRenderer = ClientSetupEvent::registerItemRendererFor;

        // 枪械 / 弹药 / 配件：这三者都没有子类，按物品身份精确匹配即可（与上游 3 处 initializeClient 对应）
        registerItemRendererFor(ModItems.MODERN_KINETIC_GUN);
        registerItemRendererFor(ModItems.AMMO);
        registerItemRendererFor(ModItems.ATTACHMENT);
        // 枪械工作台：上游是 GunSmithTableItem#initializeClient（**按类**生效），
        // 1.7.10 的 MinecraftForgeClient.registerItemRenderer 是**按 Item 实例**登记，
        // 所以 4 个 GunSmithTableItem 实例（default + workbench_a/b/c）都要各注册一次。
        registerItemRendererFor(ModItems.GUN_SMITH_TABLE);
        registerItemRendererFor(ModItems.WORKBENCH_111);
        registerItemRendererFor(ModItems.WORKBENCH_211);
        registerItemRendererFor(ModItems.WORKBENCH_121);

        // 方块物品的 GUI 3D 图标：上游 item model parent = tacz:block/<x>（3D 方块图标）。
        // 1.7.10 没有 JSON 方块模型体系，ItemBlock 默认退回 block atlas（64×64 UV 图集的碎块），
        // 这里把模型 JSON 烘成四边形按 1.20 的 GUI 帧画进槽位，见 BlockModelGuiItemRenderer。
        BlockModelGuiItemRenderer.register(ModItems.GUN_PEGBOARD, new ResourceLocation("tacz", "block/gun_pegboard"));
        BlockModelGuiItemRenderer
            .register(ModItems.GLOW_GUN_PEGBOARD, new ResourceLocation("tacz", "block/glow_gun_pegboard"));
        BlockModelGuiItemRenderer.register(ModItems.AMMO_STORAGE, new ResourceLocation("tacz", "block/ammo_storage"));
        BlockModelGuiItemRenderer
            .register(ModItems.AMMO_STORAGE_GOLD, new ResourceLocation("tacz", "block/ammo_storage_gold"));
        BlockModelGuiItemRenderer
            .register(ModItems.AMMO_STORAGE_DIAMOND, new ResourceLocation("tacz", "block/ammo_storage_diamond"));
        BlockModelGuiItemRenderer
            .register(ModItems.GUN_DISPLAY_CASE, new ResourceLocation("tacz", "block/display_case_item"));
    }

    /**
     * 按物品身份选择并注册 {@link IItemRenderer}。
     *
     * <p>
     * 每个物品独立 {@code try/catch}：某个渲染器构造失败时只跳过它、不影响其余三个
     * （{@code 1.7.10} 的注册是一次性的，一个异常中断会让后面全部缺失）。
     *
     * @param item 目标物品；{@code null}（{@code ModItems} 未注册）时只告警跳过
     */
    private static void registerItemRendererFor(Item item) {
        if (item == null) {
            GunMod.LOGGER.warn("[TACZ] item renderer skipped: item is null (ModItems 尚未注册？)");
            return;
        }
        try {
            IItemRenderer renderer = createItemRenderer(item);
            if (renderer == null) {
                // 非常规物品（例如玩家自己加的物品走了 ItemClientHooks.registerRenderer），
                // 没有对应渲染器是正常的，不告警。
                return;
            }
            MinecraftForgeClient.registerItemRenderer(item, renderer);
            // 第一人称另有一条独立的分派链：`FirstPersonRenderHandler#onRenderHand` 通过 `getRenderer(stack)`
            // 取渲染器，判据是「物品自己 instanceof IFPGeoItemRenderer」或「在 RENDERERS 里显式登记」。
            // GunItemRendererWrapper implements IFPGeoItemRenderer，但**物品**并不实现该接口，
            // 必须在这里把刚构造的实例登记进去；不登记的后果是第一人称枪械完全不渲染
            // （getRenderer 恒 empty ⇒ onRenderHand 直接 endBatch 并 return，既不画枪也不 cancel 事件）。
            if (renderer instanceof IFPGeoItemRenderer fpRenderer) {
                FirstPersonRenderHandler.registerRenderer(item, fpRenderer);
            }
        } catch (Throwable t) {
            GunMod.LOGGER.error("[TACZ] item renderer registration failed: {}", item, t);
        }
    }

    /** 物品 → 渲染器（与上游各 {@code Item#initializeClient} 的分派等价）。 */
    private static @Nullable IItemRenderer createItemRenderer(Item item) {
        if (item == ModItems.MODERN_KINETIC_GUN) {
            return new GunItemRendererWrapper();
        }
        if (item == ModItems.AMMO) {
            return new AmmoItemRenderer();
        }
        if (item == ModItems.ATTACHMENT) {
            return new AttachmentItemRenderer();
        }
        // 上游 GunSmithTableItem#initializeClient 是**类级**的：default（DefaultTableItem）
        // 与 workbench_a/b/c 三个变体都属于这个类，1.7.10 必须按 instanceof 分派才等价。
        // （DefaultTableItem extends GunSmithTableItem，因此也命中这一支。）
        if (item instanceof GunSmithTableItem) {
            return new GunSmithTableItemRenderer();
        }
        return null;
    }

    /**
     * 注入「物品显示名取枪包索引」的钩子（{@link ItemClientHooks#itemNameProvider}）。
     *
     * <p>
     * <b>上游语义（逐个对应，均以 {@code Component.translatable(index.getName())} 优先、
     * 取不到索引时回退 {@code super.getName(stack)}）</b>：
     * <ul>
     * <li>{@code item/AmmoItem.java:53-60}：{@code TimelessAPI.getClientAmmoIndex(getAmmoId(stack))}
     * → {@code index.getName()}，否则 {@code super.getName(stack)}；</li>
     * <li>{@code item/AttachmentItem.java:43-50}：同上，索引换成 {@code getClientAttachmentIndex}；</li>
     * <li>{@code item/GunSmithTableItem.java:66-73}：同上，索引换成 {@code getClientBlockIndex}；</li>
     * <li>{@code api/item/gun/AbstractGunItem.java:340-346}：枪械的同类逻辑 —— 这一条
     * <b>本工程已由 api 侧自行实现</b>（{@code AbstractGunItem#getItemStackDisplayName} 直接查
     * {@code getClientGunIndex}），且它不经过本钩子，因此这里不需要处理枪械。</li>
     * </ul>
     *
     * <p>
     * <b>1.7.10 差异</b>：上游返回 {@code Component}，1.7.10 的
     * {@link Item#getItemStackDisplayName(ItemStack)} 返回 {@code String}，
     * 因此钩子返回的是<b>已本地化</b>的字符串（{@link StatCollector#translateToLocal(String)}），
     * 与 {@code AbstractGunItem} 的既有写法一致。索引里的 {@code name} 是 <b>lang key</b>，
     * 若不翻译会直接把 key 显示出来。
     *
     * <p>
     * <b>契约（必须遵守，否则调用方无法回退）</b>：索引缺失 / id 为 null / 任何异常 → 返回 {@code null}
     * （{@link ItemClientHooks#getItemName} 判 null 后回退服务端名），<b>不返回空串、不抛异常</b>。
     */
    private static void registerItemNameProviders() {
        ItemClientHooks.itemNameProvider = ClientSetupEvent::resolveItemName;

        // nameColorProvider 保持 null（TODO(L2)，有据）：
        // 1) 全仓 grep `getNameColor` → **0 个调用点**（只有 item/ItemClientHooks 自身的定义），
        // 注入一个没有消费者的值等于死代码；
        // 2) 上游的名称染色是 `Component#withStyle(ChatFormatting.X)`（AmmoBoxItem.java:211-225、
        // AmmoStorageBlockItem.java:39-46），而 1.7.10 的 `Item#getItemStackDisplayName` 只返回 String，
        // **没有任何「给物品名上色」的 API**（`Item#getColorFromItemStack` 是图标顶点染色，不是文本）；
        // 3) 这两个物品在本工程里已经各自用 `EnumChatFormatting` 前缀自己实现了颜色
        // （AmmoBoxItem#getItemStackDisplayName:133、AmmoStorageBlockItem#getItemStackDisplayName:45），
        // 语义已达成，无需再经钩子。
        // 若日后真的出现消费者，再按上游 4 处 withStyle 的 EnumChatFormatting→RGB 表补上。
    }

    /**
     * 按物品类型查枪包索引里的名字 lang key，并本地化。
     *
     * @return 已本地化的显示名；索引不可用一律返回 {@code null}（让调用方回退服务端名）
     */
    private static @Nullable String resolveItemName(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Item item = stack.getItem();
        if (item == null) {
            return null;
        }
        try {
            // 上游 item/AmmoItem.java:53
            if (item instanceof IAmmo ammo) {
                ResourceLocation ammoId = ammo.getAmmoId(stack);
                if (ammoId == null) {
                    return null;
                }
                return TimelessAPI.getClientAmmoIndex(ammoId)
                    .map(ClientAmmoIndex::getName)
                    .map(StatCollector::translateToLocal)
                    .orElse(null);
            }
            // 上游 item/AttachmentItem.java:43
            if (item instanceof IAttachment attachment) {
                ResourceLocation attachmentId = attachment.getAttachmentId(stack);
                if (attachmentId == null) {
                    return null;
                }
                return TimelessAPI.getClientAttachmentIndex(attachmentId)
                    .map(ClientAttachmentIndex::getName)
                    .map(StatCollector::translateToLocal)
                    .orElse(null);
            }
            // 上游 item/GunSmithTableItem.java:66
            if (item instanceof IBlock block) {
                ResourceLocation blockId = block.getBlockId(stack);
                if (blockId == null) {
                    return null;
                }
                return TimelessAPI.getClientBlockIndex(blockId)
                    .map(ClientBlockIndex::getName)
                    .map(StatCollector::translateToLocal)
                    .orElse(null);
            }
        } catch (Throwable t) {
            // 索引尚未加载 / 结构异常：绝不让显示名计算把渲染线程打断
            GunMod.LOGGER.warn("[TACZ] failed to resolve gun pack display name, falling back", t);
        }
        return null;
    }

    /**
     * 对应上游 {@code RegisterGuiOverlaysEvent#registerAboveAll / registerAbove}。
     *
     * <p>
     * 1.7.10 没有 {@code IGuiOverlay} 注册表，HUD 只能订阅 {@code RenderGameOverlayEvent}：
     * <ul>
     * <li>{@code registerAboveAll(...)} → {@code RenderGameOverlayEvent.Post(ElementType.ALL)}
     * （1.7.10 {@code EntityRenderer#renderGameOverlay} 末尾会以 {@code ALL} 投递 Post）；</li>
     * <li>{@code registerAbove(CROSSHAIR.id(), ...)} → {@code RenderGameOverlayEvent.Post(ElementType.CROSSHAIRS)}
     * （准星绘制完之后、其它 HUD 之前）。</li>
     * </ul>
     */
    private void registerOverlays() {
        if (!aboveAll.isEmpty() || !aboveCrosshair.isEmpty()) {
            return;
        }
        aboveAll.add(new HudLayer(new GunHudOverlay()::render, "tac_gun_hud_overlay"));
        aboveAll.add(new HudLayer(new HeatBarOverlay()::render, "tac_heat_bar"));
        aboveAll.add(new HudLayer(new KillAmountOverlay()::render, "tac_kill_amount_overlay"));
        aboveCrosshair.add(new HudLayer(new InteractKeyTextOverlay()::render, "tac_interact_key_overlay"));
        // 展示柜上锁提示：既当 HUD 层，也需要它的 tick 递减。
        //
        // 该类原本 `onClientTick` 是 `public static` + `@SubscribeEvent`，而**全仓从未注册**过它
        // ⇒ `ticksRemaining` 永不递减（r2 已加墙钟 `expireAt` 兜底，所以「提示永不消失」已不再是 bug，
        // 但 tick 计数仍是死的）。这里把它的 tick 方法改成**实例方法**并注册到 **FML 总线**：
        // `TickEvent.ClientTickEvent` 只从 `FMLCommonHandler.instance().bus()` 发出
        // （`MinecraftForge.EVENT_BUS` 收不到），且 FML 1.7.10 的 `ASMEventHandler` 用
        // `INVOKEVIRTUAL` 回调，**静态方法会抛 `IncompatibleClassChangeError`**。
        DisplayCaseLockHintOverlay lockHint = new DisplayCaseLockHintOverlay();
        aboveCrosshair.add(new HudLayer(lockHint::render, "tac_display_case_lock_hint"));
        FMLCommonHandler.instance()
            .bus()
            .register(lockHint);
    }

    /**
     * 查客户端索引拿「激光默认色」（对应上游两处 `LaserConfig::getDefaultColor` 的静态类型调用）。
     *
     * <p>
     * 由 {@link com.tacz.guns.util.LaserColorUtil#setClientDefaultColorResolver} 在
     * {@link #onClientSetup()} 里注入；<b>任何异常 / 索引缺失都回退</b>
     * {@link com.tacz.guns.util.LaserColorUtil#DEFAULT_LASER_COLOR}，因为这是 GUI 每帧调用路径。
     *
     * @param stack 正在查询的枪械或配件物品栈
     * @return 打包的 0xRRGGBB 颜色
     */
    private static int resolveLaserDefaultColor(ItemStack stack) {
        int fallback = LaserColorUtil.DEFAULT_LASER_COLOR;
        if (stack == null || stack.getItem() == null) {
            return fallback;
        }
        try {
            // 上游 `LaserColorUtil` 对配件走 getClientAttachmentIndex、对枪械走 getGunDisplay
            if (stack.getItem() instanceof IAttachment attachment) {
                ResourceLocation attachmentId = attachment.getAttachmentId(stack);
                if (attachmentId == null) {
                    return fallback;
                }
                return TimelessAPI.getClientAttachmentIndex(attachmentId)
                    .map(ClientAttachmentIndex::getLaserConfig)
                    .map(LaserConfig::getDefaultColor)
                    .orElse(fallback);
            }
            return TimelessAPI.getGunDisplay(stack)
                .map(GunDisplayInstance::getLaserConfig)
                .map(LaserConfig::getDefaultColor)
                .orElse(fallback);
        } catch (Throwable t) {
            // GUI 每帧路径：绝不能让颜色解析把渲染打断
            return fallback;
        }
    }

    /**
     * 世界渲染末尾：先给镜片 pass 拷一份「干净世界」快照，再渲染热成像掩膜。
     *
     * <p>
     * 上游 1.20 在 {@code GameRendererMixin}/{@code ScopeLensRenderer} 的渲染管线里内联调用；
     * 1.7.10 的等价落点是 {@code RenderWorldLastEvent}（{@code EntityRenderer#renderWorld} 末尾投递，
     * 世界已画完、手部/HUD 尚未开始；见 {@code EntityRenderer.java:1429-1437}）。
     *
     * <p>
     * r6-2 起这里先调用 {@link ScopeLensRenderer#prepareWorldCopy()}：1.7.10 默认没有 stencil 附件，
     * 模型侧遮罩失效，镜身会盖住准星区；只有在「手部/枪械绘制之前」拷下世界画面，镜片圆盘才能采到
     * 干净的世界（否则会采到镜身，实机表现为「开镜看不见世界」——截图 docs/review/r6/screenshots/e）。
     *
     * <p>
     * {@code ScopeThermalRenderer#render()} 内部自带 {@code enabled()} 守卫
     * （着色器是否编译成功 / 配置开关 / 是否第一人称 / 是否 hideGUI / Oculus 阴影 pass），
     * 未启用时直接 return，因此每帧无条件调用是安全的。
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        ScopeLensRenderer.prepareWorldCopy();
        ScopeThermalRenderer.render();
    }

    /**
     * 对应上游 {@code RegisterGuiOverlaysEvent} 注册的那些 {@code render(GuiGraphics, int, int)} 调用。
     *
     * <p>
     * <b>门条件与引擎一致（r7-crosshair F2）</b>：1.7.10 {@code EntityRenderer.java:1111} 是
     * {@code if (!hideGUI || currentScreen != null)} 才调 {@code renderGameOverlay}，即「GUI 打开时 HUD 仍然渲染
     * （画在 GUI 之下）」；上游 1.20 {@code GameRenderer} 同形。因此这里只在「hideGUI 且没有 GUI」时早退。
     *
     * <p>
     * <b>aboveCrosshair 层为什么还要一个 Pre 处理器（r7-crosshair F1）</b>：
     * 1.7.10 {@code GuiIngameForge.renderCrosshairs}（{@code :218-228}）是
     * {@code if (pre(CROSSHAIRS)) return; 画原版准星; post(CROSSHAIRS);}，
     * 而 {@code RenderCrosshairEvent} 持枪时会取消 {@code Pre(CROSSHAIRS)}（上游语义：替换原版准星）
     * ⇒ <b>{@code Post(CROSSHAIRS)} 永不触发</b>。上游 1.20 对每个 overlay 独立 post，无此耦合。
     * 修法：再加一个 {@code Pre(CROSSHAIRS)} 的 {@code receiveCanceled = true + LOWEST} 处理器 ——
     * 只在「已被取消」时补画（此时原版方法在 cancel 处直接 return，故此刻已晚于替换准星，顺序正确）；
     * 未被取消时仍由 {@code Post(CROSSHAIRS)} 在「原版准星之后」绘制。
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        List<HudLayer> layers;
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
            layers = aboveAll;
        } else if (event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            // Post 事件在 1.7.10 不可取消（isCancelable()=false），因此这里只需处理「未取消」路径：
            // Pre(CROSSHAIRS) 被取消时 renderCrosshairs 提前 return，Post 根本不会发出（见 onRenderOverlayPre）。
            layers = aboveCrosshair;
        } else {
            return;
        }
        renderHudLayers(layers, event.resolution);
    }

    /**
     * 持枪时 {@code Pre(CROSSHAIRS)} 被 {@code RenderCrosshairEvent} 取消 ⇒ 原版 {@code renderCrosshairs}
     * 在 cancel 处直接 return、{@code Post(CROSSHAIRS)} 不会发出。此处理器在 LOWEST（晚于替换准星的 NORMAL
     * 优先级处理器）补画「准星之上」层，等价上游「紧跟 CROSSHAIR 层之后」的层序。
     */
    @SubscribeEvent(receiveCanceled = true, priority = EventPriority.LOWEST)
    public void onRenderOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS || !event.isCanceled()) {
            return;
        }
        renderHudLayers(aboveCrosshair, event.resolution);
    }

    /** 画一组 HUD 层（含引擎门条件，见 {@link #onRenderOverlay} 的类注释）。 */
    private void renderHudLayers(List<HudLayer> layers, ScaledResolution resolution) {
        if (layers.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || mc.thePlayer == null) {
            return;
        }
        // 引擎语义（EntityRenderer.java:1111 / 1.20 GameRenderer）：hideGUI 且无 GUI 时整段 HUD 不画
        if (mc.gameSettings.hideGUI && mc.currentScreen == null) {
            return;
        }
        if (resolution == null) {
            return;
        }
        GuiGraphics graphics = GuiGraphics.create();
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        for (HudLayer layer : layers) {
            layer.overlay()
                .render(graphics, width, height);
        }
    }

    /** HUD 叠加层（1.20 {@code IGuiOverlay} 的 1.7.10 替代物）。 */
    public interface Overlay {

        void render(GuiGraphics graphics, int width, int height);
    }

    /** 上游 {@code RegisterGuiOverlaysEvent#registerAboveAll(String id, IGuiOverlay)} 的形态。 */
    private record HudLayer(Overlay overlay, String id) {}
}
