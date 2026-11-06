# r3-05 · 接线/死代码审计 —— 全量机器清单（data）

> 本文件是 `09-wiring-deadcode.md` 的**全量机器清单**，由 `build/wiring/*.ps1` 只读脚本生成（未跑 gradle）。
> 生成时间：r3 审查当轮；`src/**` 未做任何修改。

## A1 · 事件注册全表（51 个 @SubscribeEvent 类）

| # | 类 | 需要总线 | 实际注册总线 | 结论 | 注册点 | 事件类型 |
|---:|---|---|---|---|---|---|
| 1 | `com.tacz.guns.client.gui.overlay.DisplayCaseLockHintOverlay` | FML | NONE | **MISSING(P3)** | 仅 ClientSetupEvent:418 取 ::render 当 HUD 层；onClientTick 无注册（类内 javadoc 自述，expireAt 墙钟兜底） | TickEvent.ClientTickEvent |
| 2 | `com.tacz.guns.client.animation.screen.RefitTransform` | FML | FML | **OK** | guns/client/animation/screen/RefitTransform.java:52: .register(INSTANCE); | TickEvent.RenderTickEvent |
| 3 | `com.tacz.guns.client.ClientTickHelper` | FML | FML | **OK** | guns/client/ClientProxy.java:188: .register(ClientTickHelper.INSTANCE); | TickEvent.RenderTickEvent |
| 4 | `com.tacz.guns.client.event.CameraSetupEvent` | BOTH | BOTH | **OK** | guns/client/ClientProxy.java:107: [string-list:BOTH] "com.tacz.guns.client.event.CameraSet… | BeforeRenderHandEvent;FOVUpdateEvent;Gun… |
| 5 | `com.tacz.guns.client.event.ClientGunTimeResetEvent` | FML | FML | **OK** | guns/client/ClientProxy.java:86: [string-list:FML] "com.tacz.guns.client.event.ClientGunTi… | FMLNetworkEvent.ClientDisconnectionFromS… |
| 6 | `com.tacz.guns.client.event.ClientHitMark` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:124: [string-list:FORGE] "com.tacz.guns.client.event.ClientHi… | EntityHurtByGunEvent.Post;EntityKillByGu… |
| 7 | `com.tacz.guns.client.event.ClientPreventGunClick` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:126: [string-list:FORGE] "com.tacz.guns.client.event.ClientPr… | PlayerInteractEvent |
| 8 | `com.tacz.guns.client.event.CommonNetworkCacheEvent` | FML | FML | **OK** | guns/client/ClientProxy.java:88: [string-list:FML] "com.tacz.guns.client.event.CommonNetwo… | FMLNetworkEvent.ClientConnectedToServerE… |
| 9 | `com.tacz.guns.client.event.FirstPersonRenderGunEvent` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:130: [string-list:FORGE] "com.tacz.guns.client.event.FirstPer… | GunFireEvent;RenderItemInHandBobEvent.Bo… |
| 10 | `com.tacz.guns.client.event.GunDisplayCaseClientInteractionEvent` | FML | FML | **OK** | guns/client/ClientProxy.java:96: [string-list:FML] "com.tacz.guns.client.event.GunDisplayC… | TickEvent.ClientTickEvent |
| 11 | `com.tacz.guns.client.event.GunDisplayCaseHighlightEvent` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:132: [string-list:FORGE] "com.tacz.guns.client.event.GunDispl… | DrawBlockHighlightEvent |
| 12 | `com.tacz.guns.client.event.GunPegboardHighlightEvent` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:133: [string-list:FORGE] "com.tacz.guns.client.event.GunPegbo… | DrawBlockHighlightEvent |
| 13 | `com.tacz.guns.client.event.InventoryEvent` | BOTH | BOTH | **OK** | guns/client/ClientProxy.java:109: [string-list:BOTH] "com.tacz.guns.client.event.Inventory… | FMLNetworkEvent.ClientDisconnectionFromS… |
| 14 | `com.tacz.guns.client.event.PlayerEnterWorld` | FML | FML | **OK** | guns/client/ClientProxy.java:90: [string-list:FML] "com.tacz.guns.client.event.PlayerEnter… | PlayerEvent.PlayerLoggedInEvent |
| 15 | `com.tacz.guns.client.event.PlayerHurtByGunEvent` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:135: [string-list:FORGE] "com.tacz.guns.client.event.PlayerHu… | EntityHurtByGunEvent.Post |
| 16 | `com.tacz.guns.client.event.PreventsHotbarEvent` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:137: [string-list:FORGE] "com.tacz.guns.client.event.Prevents… | RenderGameOverlayEvent.Pre |
| 17 | `com.tacz.guns.client.event.RefreshClonePlayerDataEvent` | FML | FML | **OK** | guns/client/ClientProxy.java:92: [string-list:FML] "com.tacz.guns.client.event.RefreshClon… | TickEvent.ClientTickEvent |
| 18 | `com.tacz.guns.client.event.ReloadResourceEvent` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:139: [string-list:FORGE] "com.tacz.guns.client.event.ReloadRe… | TextureStitchEvent.Post |
| 19 | `com.tacz.guns.client.event.RenderCrosshairEvent` | BOTH | BOTH | **OK** | guns/client/ClientProxy.java:111: [string-list:BOTH] "com.tacz.guns.client.event.RenderCro… | RenderGameOverlayEvent.Pre;TickEvent.Ren… |
| 20 | `com.tacz.guns.client.event.RenderHeadShotAABB` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:141: [string-list:FORGE] "com.tacz.guns.client.event.RenderHe… | RenderLivingEvent.Post |
| 21 | `com.tacz.guns.client.event.TickAnimationEvent` | FML | FML | **OK** | guns/client/ClientProxy.java:94: [string-list:FML] "com.tacz.guns.client.event.TickAnimati… | TickEvent.ClientTickEvent;TickEvent.Rend… |
| 22 | `com.tacz.guns.client.event.TooltipEvent` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:143: [string-list:FORGE] "com.tacz.guns.client.event.TooltipE… | ItemTooltipEvent |
| 23 | `com.tacz.guns.client.input.AimKey` | FML | FML | **OK** | guns/client/ClientProxy.java:197: .register(AimKey.INSTANCE); | TickEvent.ClientTickEvent |
| 24 | `com.tacz.guns.client.input.CrawlKey` | FML | FML | **OK** | guns/client/ClientProxy.java:200: .register(CrawlKey.INSTANCE); | TickEvent.ClientTickEvent |
| 25 | `com.tacz.guns.client.input.GunInputHandler` | FML | FML | **OK** | guns/client/ClientProxy.java:191: .register(GunInputHandler.INSTANCE); | TickEvent.ClientTickEvent |
| 26 | `com.tacz.guns.client.input.ReloadKey` | FML | FML | **OK** | guns/client/ClientProxy.java:203: .register(ReloadKey.INSTANCE); | TickEvent.PlayerTickEvent |
| 27 | `com.tacz.guns.client.input.ShootKey` | FML | FML | **OK** | guns/client/ClientProxy.java:194: .register(ShootKey.INSTANCE); | TickEvent.ClientTickEvent |
| 28 | `com.tacz.guns.client.renderer.item.ScreenItemVboContext` | FORGE | FORGE | **OK** | guns/client/ClientProxy.java:209: MinecraftForge.EVENT_BUS.register(new com.tacz.guns.clie… | GuiScreenEvent.DrawScreenEvent.Post;GuiS… |
| 29 | `com.tacz.guns.client.renderer.scope.ScopeLensRenderer` | FML | FML | **OK** | ScopeLensRenderer.java:213 init() -> :268 registerDeferredDeleteTicker() -> FMLCommonHandl… | TickEvent.RenderTickEvent |
| 30 | `com.tacz.guns.client.resource.ClientGunPackReloader` | FML | FML | **OK** | guns/client/resource/ClientGunPackReloader.java:92: .register(INSTANCE); | TickEvent.ClientTickEvent |
| 31 | `com.tacz.guns.client.sound.GunPackResourcePack` | BOTH | BOTH | **OK** | GunPackResourcePack.java:245 registerBusOnce() -> Forge(SoundLoadHandler); :467 .register(… | cpw.mods.fml.common.gameevent.TickEvent.… |
| 32 | `com.tacz.guns.client.sound.SoundPlayManager` | FML | FML | **OK** | guns/client/ClientProxy.java:84: [string-list:FML] "com.tacz.guns.client.sound.SoundPlayMa… | TickEvent.ClientTickEvent |
| 33 | `com.tacz.guns.event.ammo.BellRing` | FORGE | FORGE | **OK** | guns/event/EventHandlers.java:53: BellRing.register(); \|\| guns/event/ammo/BellRing.java:… | AmmoHitBlockEvent |
| 34 | `com.tacz.guns.event.ammo.DestroyGlassBlock` | FORGE | FORGE | **OK** | guns/event/EventHandlers.java:52: DestroyGlassBlock.register(); \|\| guns/event/ammo/Destr… | AmmoHitBlockEvent |
| 35 | `com.tacz.guns.event.AmmoStorageInteractionEvent` | FORGE | FORGE | **OK** | guns/event/AmmoStorageInteractionEvent.java:39: MinecraftForge.EVENT_BUS.register(new Ammo… | PlayerInteractEvent |
| 36 | `com.tacz.guns.event.EntityDamageEvent` | FORGE | FORGE | **OK** | guns/event/EntityDamageEvent.java:37: MinecraftForge.EVENT_BUS.register(new EntityDamageEv… | LivingHurtEvent |
| 37 | `com.tacz.guns.event.GunDisplayCaseInteractionEvent` | FORGE | FORGE | **OK** | guns/event/EventHandlers.java:45: GunDisplayCaseInteractionEvent.register(); \|\| guns/eve… | PlayerInteractEvent |
| 38 | `com.tacz.guns.event.GunPegboardInteractionEvent` | FORGE | FORGE | **OK** | guns/event/EventHandlers.java:46: GunPegboardInteractionEvent.register(); \|\| guns/event/… | PlayerInteractEvent |
| 39 | `com.tacz.guns.event.GunSoundAngerEvent` | FORGE | FORGE | **OK** | guns/event/EventHandlers.java:47: GunSoundAngerEvent.register(); \|\| guns/event/GunSoundA… | GunFireEvent |
| 40 | `com.tacz.guns.event.HitboxHelperEvent` | FML | FML | **OK** | guns/event/EventHandlers.java:58: HitboxHelperEvent.register(); \|\| guns/event/HitboxHelp… | PlayerEvent.PlayerLoggedOutEvent;TickEve… |
| 41 | `com.tacz.guns.event.PlayerRespawnEvent` | FML | FML | **OK** | guns/event/EventHandlers.java:60: PlayerRespawnEvent.register(); \|\| guns/event/PlayerRes… | PlayerEvent.PlayerRespawnEvent |
| 42 | `com.tacz.guns.event.PreventGunClick` | FORGE | FORGE | **OK** | guns/event/EventHandlers.java:48: PreventGunClick.register(); \|\| guns/event/PreventGunCl… | PlayerInteractEvent |
| 43 | `com.tacz.guns.event.ServerTickEvent` | FML | FML | **OK** | guns/event/EventHandlers.java:59: ServerTickEvent.register(); \|\| guns/event/ServerTickEv… | TickEvent.ServerTickEvent |
| 44 | `com.tacz.guns.event.SyncBaseTimestamp` | FORGE | FORGE | **OK** | guns/event/EventHandlers.java:49: SyncBaseTimestamp.register(); \|\| guns/event/SyncBaseTi… | EntityJoinWorldEvent |
| 45 | `com.tacz.guns.event.SyncedEntityDataEvent` | BOTH | BOTH | **OK** | guns/event/EventHandlers.java:51: SyncedEntityDataEvent.register(); \|\| guns/event/Synced… | EntityJoinWorldEvent;PlayerEvent.Clone;P… |
| 46 | `com.tacz.guns.event.TravelToDimensionEvent` | FML | FML | **OK** | guns/event/EventHandlers.java:61: TravelToDimensionEvent.register(); \|\| guns/event/Trave… | PlayerEvent.PlayerChangedDimensionEvent |
| 47 | `com.tacz.guns.init.CommonRegistry` | FORGE | NONE | **OK** | FIXED: register() 改为 no-op + 注释（CommonRegistry.java:164-168），由 ModAttributes.Handler 实例承担 | EntityEvent.EntityConstructing |
| 48 | `com.tacz.guns.init.ModAttributes` | FORGE | FORGE | **OK** | ModAttributes.java:41 MinecraftForge.EVENT_BUS.register(new Handler()) | EntityEvent.EntityConstructing |
| 49 | `com.tacz.guns.network.NetworkTaskQueue` | FML | FML | **OK** | guns/CommonProxy.java:126: .register(new NetworkTaskQueue()); | TickEvent.ClientTickEvent;TickEvent.Serv… |
| 50 | `com.tacz.guns.network.SyncConfigSyncManager` | FML | FML | **OK** | guns/CommonProxy.java:129: .register(new SyncConfigSyncManager()); | PlayerEvent.PlayerLoggedInEvent |
| 51 | `com.tacz.guns.resource.CommonAssetsManager` | BOTH | BOTH | **OK** | guns/resource/CommonAssetsManager.java:504: MinecraftForge.EVENT_BUS.register(eventHandler… | PlayerEvent.PlayerLoggedInEvent;WorldEve… |

> 合计：51 类；其中 OK=50，非 OK=1。

## A2 · 静态注入点全表

### A2-1 全部 `public static void setXxx(...)`（26 个，排除 api/**）

| 文件 | 方法 | 调用点数 | 调用点 |
|---|---|---:|---|
| `guns/client/animation/third/InnerThirdPersonManager.java` | `setRotationAnglesHead` | 2 | guns/mixin/client/HumanoidModelMixin.java:87: private void setRotationAnglesHead(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, \|\… |
| `guns/client/input/AimKey.java` | `setControllerAimDown` | 0 | — |
| `guns/client/input/CrawlKey.java` | `setControllerCrawlDown` | 0 | — |
| `guns/client/model/DisplayGunModelInstance.java` | `setVisible` | 15 | guns/client/model/DisplayGunModelInstance.java:104: if (kind == Kind.GUN) setVisible(getBone(position), false); \|\| guns/client/model/DisplayGunModelInstance.j… |
| `guns/client/renderer/item/ItemPreviewRenderContext.java` | `setAlpha` | 4 | guns/client/renderer/block/GunDisplayCaseRenderer.java:221: ItemPreviewRenderContext.setAlpha(alpha); \|\| guns/client/renderer/block/GunDisplayCaseRenderer.jav… |
| `guns/client/resource/pojo/display/gun/RangefinderManager.java` | `setCurrentConfig` | 1 | guns/client/model/functional/TextShowRender.java:58: RangefinderManager.setCurrentConfig(textShow.getRangefinder()); |
| `guns/command/sub/ConvertCommand.java` | `setConverter` | 1 | guns/client/ClientProxy.java:252: com.tacz.guns.command.sub.ConvertCommand.setConverter(com.tacz.guns.resource.PackConvertor::convert); |
| `guns/command/sub/ReloadCommand.java` | `setClientReloader` | 1 | guns/client/ClientProxy.java:227: com.tacz.guns.command.sub.ReloadCommand.setClientReloader( |
| `guns/compat/ar/ARCompat.java` | `setRenderAfterFunction` | 0 | — |
| `guns/compat/ar/ARCompat.java` | `setRenderBeforeFunction` | 0 | — |
| `guns/compat/ar/ARCompat.java` | `setRenderingLevel` | 0 | — |
| `guns/compat/ar/ARCompat.java` | `setRenderLayer` | 0 | — |
| `guns/inventory/AmmoStorageMenu.java` | `setContentSender` | 0 | — |
| `guns/item/GunTooltipPart.java` | `setHideFlags` | 1 | guns/command/sub/HideTooltipPartCommand.java:76: GunTooltipPart.setHideFlags(stack, mask); |
| `guns/mcshim/EntityStateHelper.java` | `setForcedPose` | 1 | guns/mcshim/EntityStateHelper.java:94: /** 设置强制姿势（对应 1.20 {@code Entity#setForcedPose(Pose)}）。传 {@code null} 清除。 */ |
| `guns/mcshim/RenderSystem.java` | `setProjectionMatrix` | 0 | — |
| `guns/mcshim/RenderSystem.java` | `setShader` | 0 | — |
| `guns/mcshim/RenderSystem.java` | `setShaderColor` | 14 | guns/client/event/RenderCrosshairEvent.java:175: RenderSystem.setShaderColor(1F, 1F, 1F, 0.9f); \|\| guns/client/event/RenderCrosshairEvent.java:211: RenderSyst… |
| `guns/mcshim/RenderSystem.java` | `setShaderLightDirections` | 0 | — |
| `guns/mcshim/RenderSystem.java` | `setShaderTexture` | 0 | — |
| `guns/mcshim/ResourceManager.java` | `setPackSources` | 1 | guns/resource/GunPackLoader.java:145: com.tacz.guns.mcshim.ResourceManager.setPackSources(GunPackLoader::packSourcesForShim); |
| `guns/util/LaserColorUtil.java` | `setClientDefaultColorResolver` | 0 | — |
| `guns/util/RenderDistance.java` | `setScreenDrawActive` | 0 | — |
| `guns/util/SlotAdapterHelper.java` | `setSlotAdapter` | 1 | guns/network/message/ClientMessageSetSlotAdapter.java:122: SlotAdapterHelper.setSlotAdapter(gunItem, message.attachmentType, message.adapterId); |
| `guns/util/time/GunTime.java` | `setTickGate` | 1 | guns/util/time/GunTime.java:89: setTickGate(clientTickGate); |
| `guns/util/VariantHelper.java` | `setVariant` | 1 | guns/network/message/ClientMessageSetAttachmentVariant.java:108: VariantHelper.setVariant(attachmentItem, message.variantId); |

### A2-2 静态钩子字段（`public static` 非 final、函数式/引用类型，5 个）

| 文件 | 字段 | 类型 | 外部赋值 | 赋值点 |
|---|---|---|---:|---|
| `guns/client/gui/components/CreativePackFilters.java` | `containerAccessProvider` | Function<GuiContainerCreative, ContainerAccess> | 1 | guns/mixin/client/CreativeModeInventoryScreenMixin.java:218: CreativePackFilters.containerAccessProvider = CreativePackFilterHooks.CONTAINER_ACCESS; |
| `guns/client/model/bedrock/BedrockModel.java` | `dummyModel` | BedrockModel | 0 |  |
| `guns/entity/EntityBulletClientHooks.java` | `ammoParticleSpawner` | Consumer<Entity> | 1 | guns/client/ClientProxy.java:238: com.tacz.guns.entity.EntityBulletClientHooks.ammoParticleSpawner = bullet -> com.tacz.guns.client.particle.AmmoParticleSpawner |
| `guns/item/ItemClientHooks.java` | `itemNameProvider` | Function<ItemStack, String> | 1 | guns/client/init/ClientSetupEvent.java:330: ItemClientHooks.itemNameProvider = ClientSetupEvent::resolveItemName; |
| `guns/item/ItemClientHooks.java` | `nameColorProvider` | ToIntFunction<ItemStack> | 0 |  |
| `guns/item/ItemClientHooks.java` | `registerItemRenderer` | java.util.function.Consumer<Item> | 1 | guns/client/init/ClientSetupEvent.java:240: ItemClientHooks.registerItemRenderer = ClientSetupEvent::registerItemRendererFor; |

### A2-3 注入点汇总统计

- setter 总数 **26**；0 调用点 **13**；1 调用点 **9**；其余有 ≥2 调用点。
- 0 调用点的 setter：

## A3 · 管理器生命周期全表（120 个 lifecycle 方法）

| 结论 | 数量 |
|---|---:|
| 有外部调用点 | 120 |
| 0 外部调用点 | 0 |
| 合计 | 120 |

0 外部调用点的 lifecycle 方法：**无**（r2 发现的 `GunSmithTableRecipe.clear()` / `RefitTransform` 已在本轮被修/已确认）。

## A4 · 注册表 / 枚举值 生产者-消费者

### A4-1 `DataType`（15 个枚举值）

| 枚举值 | 引用数 | 引用点 |
|---|---:|---|
| `GUN_DATA` | 1 | guns/resource/manager/GunDataManager.java:22: super(DataType.GUN_DATA, GunData.class, CommonAssetsManager.GSON, "data/guns", "GunDataLoader"); |
| `ATTACHMENT_DATA` | 1 | guns/resource/manager/AttachmentDataManager.java:22: DataType.ATTACHMENT_DATA, |
| `AMMO_INDEX` | 1 | guns/resource/CommonAssetsManager.java:200: new CommonDataManager<>(DataType.AMMO_INDEX, CommonAmmoIndex.class, GSON, "index/ammo", "AmmoIndexLoader")); |
| `GUN_INDEX` | 1 | guns/resource/manager/GunIndexManager.java:25: super(DataType.GUN_INDEX, CommonGunIndex.class, CommonAssetsManager.GSON, "index/guns", "GunIndexLoader"); |
| `ATTACHMENT_INDEX` | 1 | guns/resource/CommonAssetsManager.java:204: DataType.ATTACHMENT_INDEX, |
| `RECIPES` | 2 | guns/resource/manager/GunSmithTableRecipeManager.java:92: return DataType.RECIPES; \|\| guns/resource/network/CommonNetworkCache.java:217: Map<ResourceLocation, String> recipes = cache.get(DataType.RE… |
| `RECIPE_FILTER` | 1 | guns/resource/manager/RecipeFilterManager.java:101: return DataType.RECIPE_FILTER; |
| `ATTACHMENT_TAGS` | 2 | guns/resource/manager/AttachmentsTagManager.java:122: return DataType.ATTACHMENT_TAGS; \|\| guns/resource/network/CommonNetworkCache.java:300: if (type == DataType.ATTACHMENT_TAGS) { |
| `ALLOW_ATTACHMENT_TAGS` | 0 | **无（死枚举值）** |
| `BLOCK_DATA` | 1 | guns/resource/CommonAssetsManager.java:196: new CommonDataManager<>(DataType.BLOCK_DATA, BlockData.class, GSON, "data/blocks", "BlockDataLoader")); |
| `BLOCK_INDEX` | 1 | guns/resource/CommonAssetsManager.java:238: DataType.BLOCK_INDEX, |
| `ATTACHMENT_VARIANT_DATA` | 1 | guns/resource/CommonAssetsManager.java:180: DataType.ATTACHMENT_VARIANT_DATA, |
| `ATTACHMENT_VARIANT_INDEX` | 1 | guns/resource/CommonAssetsManager.java:211: DataType.ATTACHMENT_VARIANT_INDEX, |
| `SLOT_ADAPTER_DATA` | 1 | guns/resource/CommonAssetsManager.java:187: DataType.SLOT_ADAPTER_DATA, |
| `SLOT_ADAPTER_INDEX` | 1 | guns/resource/CommonAssetsManager.java:225: DataType.SLOT_ADAPTER_INDEX, |

### A4-2 网络消息 生产者/处理器（46 个消息类）

无生产者（从未被 `new`）的消息：

| 消息 | 文件 | 网络注册 | 有 handler | 判定 |
|---|---|---|---:|---|
| `AbstractShooterGunEventMessage` | `guns/network/message/event/AbstractShooterGunEventMessage.java` | 0 | 0 | 抽象基类，不发送（正常） |
| `ServerMessageAmmoStorageContents` | `guns/network/message/ServerMessageAmmoStorageContents.java` | 1 | 1 | **真缺口**：上游由 AmmoStorageMenu 发送，本移植无发送点 → 弹药库明细列表恒空 |
| `ServerMessageLevelUp` | `guns/network/message/ServerMessageLevelUp.java` | 1 | 1 | 上游亦无生产者（升级逻辑上游本就注释）→ 登记 |
| `ServerMessageSwapItem` | `guns/network/message/ServerMessageSwapItem.java` | 1 | 1 | 上游由 1.20 mixin 发送；1.7.10 无副手 → 无害 |
| `ServerMessageSyncConfig` | `guns/network/message/ServerMessageSyncConfig.java` | 1 | 1 | 有静态工厂生产者（`fromConfig`/`snapshot`/`createMessages`/`readCache`）→ 已接线 |
| `ServerMessageSyncedEntityDataMapping` | `guns/network/message/handshake/ServerMessageSyncedEntityDataMapping.java` | 1 | 1 | 有静态工厂生产者（`fromConfig`/`snapshot`/`createMessages`/`readCache`）→ 已接线 |
| `ServerMessageSyncGunPack` | `guns/network/message/ServerMessageSyncGunPack.java` | 1 | 1 | 有静态工厂生产者（`fromConfig`/`snapshot`/`createMessages`/`readCache`）→ 已接线 |

## A5 · 配置项读取点（88 个字段）

- 配置字段总数：**88**
- `.get()/.set()` 0 次：**2**（见下）
- 1 次：43；≥2 次：43

| 0 次读取的配置项 | 判定 |
|---|---|
| `DEFAULT_PACK_DEBUG`（`guns/config/common/OtherConfig.java`） | 上游亦无读取点（1.20 只出现在 Cloth 配置界面）→ 上游本来就死 |
| `CLIENT_GUN_PACK_DOWNLOAD_URLS`（`guns/config/sync/SyncConfig.java`） | 上游亦无读取点（1.20 定义后未使用）→ 上游本来就死 |

## B · 死代码清单

### B-1 整类 0 外部引用（排除 api/**、@Mod 入口、mixin 目录内 json 注册类后）

| 类 | 文件 | 分类 | 依据 |
|---|---|---|---|
| `com.tacz.guns.api.resource.JsonResourceLoader` | `guns/api/resource/JsonResourceLoader.java` | A 上游本来就死 | 上游文件存在且全仓 0 引用 |
| `com.tacz.guns.client.animation.statemachine.ThrowableAnimationStateContext` | `guns/client/animation/statemachine/ThrowableAnimationStateContext.java` | A 上游本来就死 | 上游文件存在且全仓 0 引用 |
| `com.tacz.guns.client.gui.GunPackProgressScreen` | `guns/client/gui/GunPackProgressScreen.java` | A 上游本来就死 | 上游 0 调用者（r2 已核对） |
| `com.tacz.guns.client.renderer.external.ExternalGunEffects` | `guns/client/renderer/external/ExternalGunEffects.java` | **B 移植后变死** | 上游由 `ExternalGunItemVboRenderer:76` 调用；本移植 VBO 路径恒 UNSUPPORTED（已登记降级）故永不执行 |
| `com.tacz.guns.client.sound.GunSoundPreload` | `guns/client/sound/GunSoundPreload.java` | A 上游本来就死 | 上游文件存在且全仓 0 引用 |
| `com.tacz.guns.command.sub.ListPackCommand` | `guns/command/sub/ListPackCommand.java` | A 上游本来就死 | 上游遗留未实现类（`/tacz list`） |
| `com.tacz.guns.compat.ar.BedrockPartLightingMode` | `guns/compat/ar/BedrockPartLightingMode.java` | A 上游仅 AR mixin 使用 | 本移植无 AR → 随 compat 决策失效 |
| `com.tacz.guns.compat.jei.GunModPlugin` | `guns/compat/jei/GunModPlugin.java` | C 决策保留占位 | 1.7.10 无 JEI；类注释说明保留「落点」 |
| `com.tacz.guns.compat.kubejs.TimelessKubeJSPlugin` | `guns/compat/kubejs/TimelessKubeJSPlugin.java` | C 决策保留占位 | 1.7.10 无 KubeJS |
| `com.tacz.guns.compat.optifine.OptifineCompat` | `guns/compat/optifine/OptifineCompat.java` | A 上游本来就死 | 上游全仓 0 引用 |
| `com.tacz.guns.resource.modifier.custom.AimInaccuracyModifier` | `guns/resource/modifier/custom/AimInaccuracyModifier.java` | A 上游本来就死 | 上游 `@Deprecated` 且 id 已重定向 |
| `com.tacz.guns.resource.VersionChecker` | `guns/resource/VersionChecker.java` | A 上游本来就死 | 上游全仓 0 引用 |

> `mixin/**` 下 5 个类不在上表：它们只被 `mixins.tacz.json` 以字符串引用（机器扫描看不到），实际生效。

### B-2 public/protected 方法 0 语法引用（631 条的分类统计）

| 分类 | 数量 | 说明 |
|---|---:|---|
| A 上游同样 0 语法引用（含 POJO getter/setter、反射消费、接口默认实现） | 471 | Gson/反射消费的 POJO 除外仍需人工判断，但**上游也是同样形态** |
| SHIM-UNUSED（`mcshim/**` 未被消费的 shim API） | 132 | 1.7.10 无对应机制，shim 方法保留 API 形状但调用点被直接 GL 替代 |
| COMPAT-DISABLED（`compat/**`） | 28 | 缺库静默跳过（用户裁决） |
| **B 上游有调用、移植后变死** | **0** | 计入方法引用（`::name`）后重新扫描 = 0 |

### B-3 常量 0 读取（37 条，已剔除「仅同文件内使用」的误报）

| # | 常量 | 文件 | 判定 |
|---:|---|---|---|
| 1 | `SMITH_TABLE_TEXTURE_LOCATION` | `guns/client/resource/InternalAssetLoader.java` | 同文件内只有 MODEL_LOCATION 在用；纹理路径由贴图管理器按名解析 → 无害遗留 |
| 2 | `NO_TRANSFORMS` | `guns/client/resource/pojo/display/block/BlockItemTransforms.java` | POJO 默认值 → 无害 |
| 3 | `DEFAULT_PRELOAD_NAMES` | `guns/client/sound/GunSoundPreload.java` | 所属类上游亦 0 引用（GunSoundPreload 上游本来就死） |
| 4 | `AIM_FIRE_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 5 | `AIM_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 6 | `CROUCH_LOWER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 7 | `CROUCH_WALK_LOWER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 8 | `CROUCH_WALK_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 9 | `HOLD_LOWER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 10 | `HOLD_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 11 | `LIE_AIM` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 12 | `LIE_AIM_FIRE` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 13 | `LIE_MOVE` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 14 | `LIE_NORMAL_FIRE` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 15 | `LIE_RELOAD` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 16 | `MELEE_2_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 17 | `MELEE_3_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 18 | `MELEE_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 19 | `NORMAL_FIRE_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 20 | `RELOAD_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 21 | `RIDE_LOWER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 22 | `RUN_LOWER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 23 | `RUN_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 24 | `WALK_LOWER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 25 | `WALK_UPPER` | `guns/compat/playeranimator/AnimationName.java` | compat 域动画名（缺库静默跳过）→ 无害 |
| 26 | `CHARACTER` | `guns/entity/sync/core/Serializers.java` | 同上 |
| 27 | `DOUBLE` | `guns/entity/sync/core/Serializers.java` | MrCrayfish 框架工具类；上游同样未用 → 上游亦死 |
| 28 | `ITEM_STACK` | `guns/entity/sync/core/Serializers.java` | 同上 |
| 29 | `RESOURCE_LOCATION` | `guns/entity/sync/core/Serializers.java` | 同上 |
| 30 | `STRING` | `guns/entity/sync/core/Serializers.java` | 同上 |
| 31 | `BLOOD_STRIKE_HEIGHT` | `guns/init/ModPainting.java` | 同上 |
| 32 | `BLOOD_STRIKE_WIDTH` | `guns/init/ModPainting.java` | 1.20 painting 变体尺寸；1.7.10 无画注册表（已登记降级）→ 登记 |
| 33 | `PROPERTY_NAME` | `guns/item/AmmoBoxItem.java` | 上游用于 1.20 ItemProperties（本移植无物品属性系统；子弹盒外观已登记降级）→ 登记 |
| 34 | `LOOT_TABLE_INJECTOR` | `guns/loot/LootTableInjectorModifier.java` | 上游 GLM 名；本移植改事件注入，常量留作对照 → 上游亦死 |
| 35 | `FULL_DARK` | `guns/mcshim/LightTexture.java` | shim 常量 → 无害 |
| 36 | `MAX_LIGHT_COORDS` | `guns/mcshim/LightTexture.java` | shim 常量（1.7.10 无此机制）→ 无害 |
| 37 | `MAX_SKY_LIGHT_COORDS` | `guns/mcshim/LightTexture.java` | shim 常量 → 无害 |

### B-4 死接口 / 死 import / 不可达分支

- 死 import：**0**（821 文件全量扫，import 的简单名在文件内只出现 1 次的为 0）
- 死接口：机器扫描命中 4 个，**全部误报**（`IDataSerializer`/`PackSource`/`IUnflushableWrapper`/`CreativeContainerAccessor` 的实现为匿名类/`new X(){}` 或仅作方法参数类型，`implements` 关键字不出现）
- 不可达分支（`if (true)/(false)`、`|| true`、`&& false`）：**0**（r2 已扫，本轮复核仍为 0）

## C · r2 已修项本轮复核（现状 = 已修）

| r2 编号 | 位置 | 本轮现状 | 证据 |
|---|---|---|---|
| r2-06 P0 | 工作台配方 0 条 | **已修** | 新增 `resource/manager/GunSmithTableRecipeManager`（160 行，扫描 `data/<ns>/recipes`→`deserializeAndRegister`→`clear()`→`DataType.RECIPES` 网络同步），在 `CommonAssetsManager:245` 注册进重载管线 |
| r2-06 P1 | `RefitTransform` tick 未注册 | **已修** | 类改为「首次 `init()` 自注册」：`RefitTransform.java:45 ensureRegistered()` → FML 总线注册实例（`:50-52`），`GunRefitScreen:109` 调 `init()` |
| r2-06 P2 | 战利品注入未接线 | **已修** | 新增 `event/LootInjectionEvent`（:310 调 `LootTableInjectorModifier.inject(...)`），`EventHandlers:58` 注册 |
| r2-06 P2 | `/tacz convert` 未注入 | **已修** | `ClientProxy:252 ConvertCommand.setConverter(PackConvertor::convert)` |
| r2-06 P2 | `PegboardStress.placeBoards` 恒 return 0 | **本轮并发已修** | `PegboardStressCommand.java:228 placeBoards` 已实装：`:238 world.setBlock(...)` → `:245 instanceof GunPegboardBlockEntity` → `:255 setItems(contents)` |
| r2-06 P1（补充） | `CommonRegistry.register(Class.class)` 死注册 | **已修** | `CommonRegistry.java:164-168` 改为 no-op + 注释说明；`onEntityConstructing` 由 `ModAttributes.Handler`（实例）承担 |
| r3 本轮 | `GunTime` 暂停闸门未安装 | **已修**（他人并发） | `GunInputHandler:126 GunTime.installClientTickGate(() -> !mc.isGamePaused())` |


