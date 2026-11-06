# r3-05 · 专项审查：「接线未接线」与「死代码」

> **作用域**：`src/main/java/com/tacz/**` 全仓**只读**（821 文件 / 约 10.9 万行）；写作用域仅本文件与 `09-wiring-deadcode-data.md`。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（799 文件）。
> **方法**：只读脚本全量取证（`build/wiring/*.ps1`，产物 CSV/清单在 `build/wiring/`），**未跑 gradle**。
> **复核时间点（重要）**：本轮审查期间有多名 teammate 并发修改（mixin-fixer 改 `mixin/**`+`CommonRegistry`、gun-logic 改 `GunTime` 闸门、他人修 r2 配方与 RefitTransform 等）。
> 本报告所有结论都以**写报告前最后一次全仓状态快照**为准；r2 遗留项与「本轮并发已修」项已分别标注，避免重复派单。

## 结论速览

| 维度 | 数量 | 说明 |
|---|---:|---|
| **真缺口（需修，且目前无人认领）** | **4** | 1×P1 + 2×P2 + 1×P3（见下表） |
| r2-06 遗留、**本轮并发已修**（写报告前最后一次快照复核） | 1 | `PegboardStressCommand#placeBoards`——已从「恒 return 0」变成真实现（`world.setBlock` + `GunPegboardBlockEntity#setItems`） |
| r2-06 / r3 并发**已修**并复核 | 8 | 配方 0 条、RefitTransform、战利品注入、`/tacz convert`、`CommonRegistry` Class 注册、`GunTime` 闸门、`ReloadCommand` 注入、`PegboardStress` 放置分支 |
| 上游亦如此（**登记即可**） | 9 类 + 4 项 | 详见「三、视角B」的 A 类 |
| 死代码：整类 0 引用 | 12 | 其中真回归 **1**（`ExternalGunEffects`，且属既有 VBO 降级的下游衍生） |
| 死代码：public/protected 方法 0 语法引用 | 631 | 分类后 **B 类（上游有调用、移植后变死）= 0** |
| 死代码：常量 0 读取 | 37 | B 类 = 0（其余上游亦死/ shim / compat / 误报） |
| 死 import / 不可达分支 / 传 Class 注册 | 0 / 0 / 0 | 传 `register(Xxx.class)` 的 live 代码**已归零** |

### 最该立刻修、且目前无人认领的 3 条

1. **P1 —— 弹药库界面的「弹药明细列表」永远为空**：
   `inventory/AmmoStorageMenu.java:180 setContentSender(...)` **0 调用者**，因此 `detectAndSendChanges`（`:241`）永远不发送；
   对应网络包 `ServerMessageAmmoStorageContents` **全仓无生产者**（已注册进网络、客户端 handler 也在，就是没人发）。
   上游 `AmmoStorageMenu.java:113` 会 `sendToClientPlayer(new ServerMessageAmmoStorageContents(...))`。
   玩家可见：弹药库 GUI 只能看到「已用/容量」两根进度条，**弹药列表恒空**，无法查看/取出具体弹药（`AmmoStorageScreen:82/163` 全依赖 `menu.getEntries()`）。
   **最小修法**：`network/NetworkHandler#init()` 末尾加一行
   `AmmoStorageMenu.setContentSender((player, windowId, cap, used, entries) -> { if (player instanceof EntityPlayerMP mp) NetworkHandler.sendToClientPlayer(new ServerMessageAmmoStorageContents(windowId, cap, used, entries), mp); });`
2. **P2 —— GUI 内绘制物品不绕过 LOD（低模可能出现在界面里）**：
   `util/RenderDistance.java:47 setScreenDrawActive(...)` **0 调用者**；上游等价物是 `ScreenItemVboContext.isActive()`（GUI 绘制期间返回 true，LOD 判定 `:62` 直接放行）。
   本移植的 `client/renderer/item/ScreenItemVboContext.java` 仍在统计 `DrawScreenEvent` 深度（`isActive()` `:41`），但**没人把它接到 `RenderDistance`**。
   玩家可见：改装界面 / 展示柜预览里，若模型位移超出 `GUN_LOD_RENDER_DISTANCE`，会显示低模（上游保证 GUI 内始终高模）。
   **最小修法**：在 `ScreenItemVboContext.beforeScreen/afterScreen` 里同步 `RenderDistance.setScreenDrawActive(ScreenItemVboContext.isActive())`（或 `ClientSetupEvent` 的 tick step 里每帧同步）。
3. **P2 —— 改装界面的激光颜色滑条默认色恒红**：
   `util/LaserColorUtil.java:60 setClientDefaultColorResolver(...)` **0 调用者** → `getLaserColor(stack)`（`:90-119`）对无自定义色的枪/配件永远返回 `DEFAULT_LASER_COLOR = 0xFF0000`，
   而上游是 `TimelessAPI.getGunDisplay(stack).map(GunDisplayInstance::getLaserConfig).map(LaserConfig::getDefaultColor)`。
   注意：**光束渲染本身用的另一条重载**（`getLaserColor(stack, laserConfig)`，`BeamRenderer:81`）走反射 `defaultColorOf(...)`，颜色正确；
   受影响的是 `client/gui/components/refit/HSVSliderGroup.java:85/88`（改装界面激光颜色滑条的初始颜色）。
   **最小修法**：`ClientSetupEvent` 的某个 step 里注入
   `LaserColorUtil.setClientDefaultColorResolver(stack -> ...从 ClientIndex 取 LaserConfig.getDefaultColor()...)`。

> 另一条 P3 见「二、A1」：`DisplayCaseLockHintOverlay#onClientTick` 未注册（已有墙钟兜底，提示不会永久停留，仅 tick 计数不递减）。

---

## 一、r2 已修项复核（避免重复报，也验证「同类修法」有效）

| r2 编号 | 原问题 | 本轮现状（快照） | 证据 |
|---|---|---|---|
| r2-06 **P0** | 枪械工作台配方 0 条 | **已修** | 新增 `resource/manager/GunSmithTableRecipeManager.java`（160 行）：`reload()` 里 `GunPackLoader.readPackData("recipes")` → `GunSmithTableSerializer.deserializeAndRegister` → `clear()` → 原始 json 进 `DataType.RECIPES` 网络缓存；在 `CommonAssetsManager:245` 注册进重载管线，管线末尾 `:253 initLazyRecipes()` 展开产物 |
| r2-06 **P1** | `RefitTransform.tickInterpolation` 未注册 | **已修** | 改为「首次 `init()` 自注册」：`RefitTransform.java:45 ensureRegistered()` → `FMLCommonHandler.instance().bus().register(INSTANCE)`（`:50-52`）；`GunRefitScreen:109` 调 `init()` |
| r2-06 **P2** | 战利品注入未接线 | **已修** | 新增 `event/LootInjectionEvent.java`（`:310` 调 `LootTableInjectorModifier.inject(...)`），`EventHandlers:58` 注册；覆盖方块破坏掉落 + 新世界奖励箱 |
| r2-06 **P2** | `/tacz convert` 客户端未注入 | **已修** | `client/ClientProxy.java:252 ConvertCommand.setConverter(PackConvertor::convert)` |
| r2-06 补充 | `CommonRegistry.register(CommonRegistry.class)`（传 Class 空注册） | **已修** | `CommonRegistry.java:164-168` 改为 no-op + 注释；属性挂载由 `ModAttributes.Handler`（实例注册，`ModAttributes.java:41`）承担 |
| r3 并发 | `GunTime` 暂停闸门未安装 | **已修** | `GunInputHandler.java:126 GunTime.installClientTickGate(() -> !Minecraft.getMinecraft().isGamePaused())` |
| r3 并发 | `ReloadCommand.setClientReloader` 未注入 | **已修** | `client/ClientProxy.java:227 ReloadCommand.setClientReloader(...)` |
| r2-06 **P2 遗留** | `PegboardStressCommand#placeBoards` 恒 `return 0` | **本轮并发已修**（写报告前最后快照确认） | `PegboardStressCommand.java:228 placeBoards(...)` 已实装：`:238 world.setBlock(..., ModBlocks.GUN_PEGBOARD, metadata, 3)` → `:245 getTileEntity instanceof GunPegboardBlockEntity` → `:255 pegboard.setItems(contents)`，失败逐格回消息并返回已放置数 |

> 结论：r2 抓到的 5 类问题里 **5 类已修且修法正确**（都是「补一行接线/补一个注册/实装已写好的对照代码」），本轮审查范围内没有再发现它们的同类未修项。

---

## 二、视角 A：五类「接线」映射表

### A1 事件注册（51 个 `@SubscribeEvent` 类 → 50 正确 + 1 P3）

全量表见 `09-wiring-deadcode-data.md` §A1（含「需要总线 / 实际注册总线 / 注册点 file:line / 事件类型」）。

- **50/51 注册到正确总线**。1.7.10 的双总线规则被正确执行：
  `TickEvent.*` / `PlayerEvent.*` / `FMLNetworkEvent.*` 一律挂 `FMLCommonHandler.instance().bus()`（如 `ClientTickHelper`、`SoundPlayManager`、`SyncBaseTimestamp`……），
  `net.minecraftforge.*` / TACZ 自定义事件挂 `MinecraftForge.EVENT_BUS`；**同时有两类事件的 4 个类**（`CameraSetupEvent`/`InventoryEvent`/`RenderCrosshairEvent`/`SyncedEntityDataEvent`/`CommonAssetsManager`）挂两条总线，与 `ClientProxy` 的 `BOTH_BUS_CLASSES` 清单一致。
- **`register(Xxx.class)`（传 Class = 空注册）全仓扫描：live 代码 0 处**（唯一一处 `CommonRegistry` 已在本轮被修，`CommonAssetsManager` 早已改成注册实例）。
- **1 处真缺口（P3）**：`client/gui/overlay/DisplayCaseLockHintOverlay.java:76 onClientTick` 是 `@SubscribeEvent`，但全仓**没有** `EVENT_BUS.register(...)`（只有 `ClientSetupEvent:418` 取了 `::render` 当 HUD 层）。
  类内 javadoc `:43-52` 已自述该问题并用墙钟 `expireAt`（`:53/58`）兜底 → 「展示柜已上锁」提示**不会永久停留**，但 `ticksRemaining` 永不递减。
  最小修法：`ClientSetupEvent`（或 `ClientProxy`）里 `MinecraftForge.EVENT_BUS.register(new DisplayCaseLockHintOverlay())`，或删掉该 tick 方法改纯墙钟。
- **上游事件类映射**：上游 62 个事件类中，
  - 51 个在移植里有对应处理类（本表）；
  - 7 个改为「显式调用」而非事件（`GunRefitScreen`/`GunPegboardColors`/`ModContainerScreen`/`ScopeThermalRenderer`/`TimelessKubeJSPlugin`/`CommonLoadPack`/`LoadingConfigEvent`）——逐一核对均有真实调用点（`EventHandlers:64-65`、`ClientSetupEvent` step、`GuiHandler` 等）；
  - 1 个（`compat/playeranimator/animation/AnimationManager`）随「1.7.10 无 PlayerAnimator」决策整体删除；
  - 其余为上游 **MOD 总线 setup 类**（16 个，见下）。
- **MOD 总线 setup 类 16 → 16 有显式入口**（1.7.10 无 MOD 事件总线，等价物是 `CommonProxy`/`ClientSetupEvent` 里的显式调用）：
  `ClientSetupEvent.onClientSetup`（ClientSetupEvent 自身）、`ModEntitiesRender.register`、`ParticleFactoryRegistry.register`、`GunInputHandler.registerKeyMappings`、`GunPegboardColors.setup`、`ModContainerScreen.clientSetup`、`CommonRegistry.commonSetup`、`CapabilityRegistry.register`、`ModItems.register`、`ModRecipe.register`、`CommandRegistry.registerCommands`、`CompatRegistry.enqueueCompat`（由 `CommonProxy:158 register()` 调）、`CommonLoadPack.loadGunPack`、`LoadingConfigEvent.reloadConfig`（`EventHandlers:64-65`）、`ReloadResourceEvent`（Forge 总线）、`CommonAssetsManager`（双方总线）。
  逐条核对 callers 均 ≥1（`09-wiring-deadcode-data` §A1 同法扫描；表在 `build/wiring/mod-bus-classes.csv`）。

### A2 静态注入点（27 个 setter + 6 个钩子字段 + 1 个 Impl 接口）

- store 全表见 data §A2-1。**26 个 `public static void setXxx(...)`**（排除 `api/**`），其中：
  - **13 个 0 调用点**：`ARCompat` 4 个（无 AR 模组 → 无害）、`AimKey/CrawlKey#setController*Down` 2 个（无 Controllable → 无害）、`mcshim/RenderSystem` 4 个（shim API → 无害）、
    **真缺口 3 个**：`AmmoStorageMenu#setContentSender`（P1）、`LaserColorUtil#setClientDefaultColorResolver`（P2/P3）、`RenderDistance#setScreenDrawActive`（P2）；
  - 9 个 1 调用点：`ConvertCommand#setConverter`（已接线，`ClientProxy:252`）、`ReloadCommand#setClientReloader`（已接线，`ClientProxy:227`）、`SlotAdapterHelper#setSlotAdapter`（`ClientMessageSetSlotAdapter:122`）、`VariantHelper#setVariant`（`ClientMessageSetAttachmentVariant:108`）、`ResourceManager#setPackSources`（`GunPackLoader:145`）、`GunTooltipPart#setHideFlags`（`HideTooltipPartCommand:76`）、`RangefinderManager#setCurrentConfig`（`TextShowRender:58`）、`GunTime#setTickGate`（`GunTime:89` 内部、由 `installClientTickGate` 调用）、`ItemPreviewRenderContext#setAlpha`（展示柜/挂板渲染 4 处）。
- **静态钩子字段**（`public static` 非 final 函数式字段）6 个，**5 个有赋值**：
  `ItemClientHooks.itemNameProvider`（`ClientSetupEvent:330`）、`ItemClientHooks.registerItemRenderer`（`ClientSetupEvent:240`）、`EntityBulletClientHooks.ammoParticleSpawner`（`ClientProxy:238`）、`CreativePackFilters.containerAccessProvider`（`CreativeModeInventoryScreenMixin:218`）、`ClientSideHooks.impl`（`ClientProxy` 注入 `Hooks`）；
  唯一未赋值的是 `ItemClientHooks.nameColorProvider`——r2 已核对为**有意留 null**（1.7.10 无「给物品名上色」API，`ClientSetupEvent:316` 有说明）。
- **`ClientSideHooks.Impl`（10 个默认空方法）**：`ClientProxy.Hooks`（`:343-469`）**全部覆写**（`getEntityById`/`playSound`/`bulletHitBlock`/`craft`/`gunUnload`/`refreshRefitScreen`/`updateBaseTimestamp`/`updateAmmoStorage`/`syncGunPack`/`swapItemWithOffHand`）；
  其中 `syncBaseTimestamp`（`ClientSideHooks:56`）覆写体为空且在真路径上无人调用（真路径走 `updateBaseTimestamp`，见 `ServerMessageSyncBaseTimestamp:46`）→ **死接口方法（无害）**，建议删或标 `@Deprecated`。

### A3 管理器生命周期（120 个 `reload/init/load/clear/initAll/register*/markLoadComplete` 方法）

机器全表见 data §A3。由于 `register/init/reload` 同名方法多，机器「按名计数」只能证明「有人调同名方法」，因此**关键管理器逐个人工核对了真实调用点**：

| 管理器入口 | 真实调用点 |
|---|---|
| `GunPackLoader#reloadPackList` | `CommonProxy:112`（preInit）、`CommonAssetsManager:444/546`（reload/reloadAllPack）、`GunPackLoader:167`（构造） |
| `CommonAssetsManager#reload` | `CommonProxy:167`（服务端启动） |
| `CommonAssetsManager#reloadAllPack` | `ReloadCommand:85`（`/tacz reload`）、`ClientAssetsManager:329`（客户端重载入口） |
| `CommonAssetsManager#initLazyRecipes` | `CommonAssetsManager:253`（重载管线末尾，晚于所有 index） |
| `CommonAssetsManager#registerEventHandlers` | `CommonProxy:115`（双方总线注册实例） |
| `ClientAssetsManager#reloadAndRegister` | `ClientGunPackReloader:69`（`IResourceManagerReloadListener`） |
| `ClientIndexManager#reload` | `ClientProxy:456`、`ClientGunPackReloader`（注册在监听器列表末尾，顺序契约有注释） |
| `ClientGunPackReloader#init` | `ClientProxy:274`（postInit） |
| `AttachmentPropertyManager#registerModifier` | `CommonProxy:113` |
| `CommonRegistry#markLoadComplete` | `CommonProxy:155`（postInit） |

结论：**生命周期 0 孤儿**；r2 发现的 `GunSmithTableRecipe#clear()` 也已在 `GunSmithTableRecipeManager.loadRecipes:107` 被调用（每次重载先清空）。

### A4 注册表 / 枚举值 生产者-消费者

- **`DataType`（15 个枚举值）**：14 个有真实生产者+消费者；`ALLOW_ATTACHMENT_TAGS` **0 引用**——但**上游也是 0 引用**（上游 `AttachmentsTagManager.getType()` 同样返回 `ATTACHMENT_TAGS`，该枚举值在 1.2.0 就是遗留）→ 登记即可，非缺口。
- **网络消息（46 个 `ServerMessage*/ClientMessage*`）**：41 个「有生产者+有 handler」；其余 7 个"无 `new`"逐一核过：
  - **`ServerMessageAmmoStorageContents` = 真缺口 P1**（见结论速览第 1 条）；
  - `ServerMessageLevelUp`：上游也无生产者（1.2.0 升级逻辑上游自己注释掉了）→ 登记；
  - `ServerMessageSwapItem`：上游由 1.20 的 `ServerPlayNetHandlerMixin` 发送；1.7.10 无副手 → 无害；
  - `ServerMessageSyncConfig` / `ServerMessageSyncedEntityDataMapping` / `ServerMessageSyncGunPack`：走**静态工厂**（`fromConfig()` / `snapshot()` / `createMessages()`），生产者存在（`SyncConfigSyncManager:56/58`、`CommonAssetsManager:252`）→ 已接线；
  - `AbstractShooterGunEventMessage`：抽象基类（正常）。
- **`KeyBinding` 12 个声明 → 12 个注册**：`GunInputHandler.PRESS_BINDINGS` 11 个（`GunInputHandler:96-106`）+ `ShootKey.SHOOT_KEY`（`:118`），`registerKeyMappings` 由 `ClientSetupEvent` step 调用。
- **`IItemRenderer`**：7 个物品（枪/弹药/配件/工作台×4）在 `ClientSetupEvent:243-252 registerItemRendererFor(...)` 逐个 `MinecraftForgeClient.registerItemRenderer` 注册（r2 P0 的修法仍在）。
- **`EntityRenderers` / `BlockEntityRenderers`**：`ModEntitiesRender.register()`（`:52-63`）注册 2 个实体渲染器（`EntityKineticBullet`/`TargetMinecart`）+ 5 个 TESR（`GunSmithTable`/`Target`/`Statue`/`GunPegboard`/`GunDisplayCase`），由 `ClientSetupEvent` step 调用；实体/方块实体总数与 `init/ModEntities`、`init/ModBlocks` 一一对应，无遗漏。
- **`GunItemManager#registerGunItem`**：唯一调用点 `ModItems:83`（`modItems` 注册后逐个登记枪械物品）→ 有生产者，无孤儿。

### A5 配置项（88 个 `*Config` 字段）

- 88 个字段中 **86 个有 `.get()` 读取点**（43 个恰 1 处、43 个 ≥2 处）；**2 个 0 读取**：
  `OtherConfig.DEFAULT_PACK_DEBUG`、`SyncConfig.CLIENT_GUN_PACK_DOWNLOAD_URLS` —— **上游同样 0 读取**（前者只出现在 Cloth 配置界面，后者在 1.2.0 定义后未使用）→ 登记即可，**不是移植缺口**。
- 未发现「功能写死了却留着配置项」的新增案例（r2 提到的 `STATIC_ITEM_GUN_VBO`/`DISPLAY_GUN_VBO` 等 VBO 相关开关属于已知降级，配置仍被读取以保持语义/清缓存副作用）。

---

## 三、视角 B：死代码清单（三分类）

### B-1 整类 0 外部引用：12 类

| 分类 | 类 | 判定 |
|---|---|---|
| **B 移植后变死（1）** | `client/renderer/external/ExternalGunEffects.java`（88 行，完整移植） | 上游由 `ExternalGunItemVboRenderer:76` 调用；本移植 VBO 路径按设计恒返回 `UNSUPPORTED`（`ExternalGunItemVboRenderer` 类注释已登记降级），所以该类**永远不可达**。属既有降级的衍生，**不是新 bug**，但应在类注释里补一句「本类在 1.7.10 不可达」 |
| **A 上游本来就死（9）** | `api/resource/JsonResourceLoader`、`client/animation/statemachine/ThrowableAnimationStateContext`、`client/gui/GunPackProgressScreen`、`client/sound/GunSoundPreload`、`command/sub/ListPackCommand`、`compat/optifine/OptifineCompat`、`resource/VersionChecker`、`resource/modifier/custom/AimInaccuracyModifier`（上游 `@Deprecated`）、`compat/ar/BedrockPartLightingMode`（上游仅 AR mixin 用） | 均核过上游：同文件存在且全仓 0 引用（或仅 compat 使用）→ **登记即可** |
| **C 决策保留占位（2）** | `compat/jei/GunModPlugin`、`compat/kubejs/TimelessKubeJSPlugin` | 用户裁决「compat 反射式可选兼容、缺库静默跳过」；类注释写明保留原因 |

> `mixin/**` 下 5 个类不在上表（机器看不到 json 字符串引用）：`AbstractButtonMixin`/`MinecraftAttackMixin`/`MouseHandlerMixin`/`ServerGamePacketListenerImplMixin`/`ServerPlayerMixin` 均在当前 `mixins.tacz.json` 内，**生效**。

### B-2 public/protected 方法 0 语法引用：631 条（分类后 B=0）

| 分类 | 数量 | 说明 |
|---|---:|---|
| A 上游同样 0 语法引用 | 471 | 含 POJO getter/setter（Gson 反射消费）、Lua API（脚本反射消费）、接口默认实现——**上游同形态**，非缺口 |
| SHIM-UNUSED | 132 | `mcshim/**` 保留 API 形状但调用点被直接 GL/1.7.10 API 替代（如 `RenderSystem#setShader` 等），已登记降级 |
| COMPAT-DISABLED | 28 | `compat/**` 缺库静默跳过 |
| **B 上游有调用、移植后变死** | **0** | 计入方法引用 `::name` 后重新扫描为 0 |

> 方法与常量扫描的关键改进：r2 的「refs≤1」漏掉了**方法引用**（`Foo::bar`，Lombok/`Consumer` 注入全靠它）与**同名跨类调用**，本轮把两者都计入后，「移植后变死」从 215 条候选降到 **0**（原 215 条全是 `mcshim`/`compat` 与 POJO/Lua 噪声）。

### B-3 常量 0 读取：37 条（B=0）

全部 37 条与判定见 data §B-3。分布：`compat/playeranimator/AnimationName` 22 条（compat 禁用）、`mcshim` 8 条（shim 常量）、`Serializers` 5 条（MrCrayfish 框架类，上游同样未用）、`ModPainting` 2 条（1.7.10 无画注册表，已登记）、`AmmoBoxItem.PROPERTY_NAME`（1.20 物品属性，已登记）、`LootTableInjectorModifier.LOOT_TABLE_INJECTOR`（上游 GLM 名，本移植改事件注入）、`InternalAssetLoader.SMITH_TABLE_TEXTURE_LOCATION`（纹理走贴图管理器按名解析）、`NO_TRANSFORMS`（POJO 默认值）。**无一条是「移植删了使用点」**。

### B-4 死接口 / 死 import / 不可达分支：0

- 死 import：**0**（821 文件全量：import 的简单名在文件内只出现 1 次的为 0）。
- 死接口：机器命中 4 个**全部误报**（`IDataSerializer`/`PackSource`/`IUnflushableWrapper`/`CreativeContainerAccessor` 的实现是匿名类或仅作参数类型）。
- 不可达分支：`if (true)/(false)`、`|| true`、`&& false`、`return` 后语句 —— **0**。

### B-5 「有意不列进 JSON」的 mixin 复核（Lead 指定）

| Mixin | 当前是否在 `mixins.tacz.json` | 复核结论 |
|---|---|---|
| `client/LanguageMixin` | **不在**（有意） | **结论仍成立**。① 上游该 Mixin 的两个 `@Inject` 本来就是整段注释、且引用的 `ClientAssetManager#getLanguages` 在 1.2.0 已不存在（死代码）；② 1.7.10 语言注入的**替代实现已落地并接线**：`GunPackLoader.injectAllGunPackLanguages()`（`:263-303`，把每个枪包每个语种灌进 `LanguageRegistry`）由 `ClientSetupEvent:154 step("Gun pack languages", ...)` 调用；`injectGunPackLanguage(current)` 另由 `ClientProxy:284` postInit 调一次（冗余但无害）。③ 因此**不需要**启用 LanguageMixin，也不存在「语言注入信息不一致」风险：语言链路与 mixin 完全解耦。 |
| `client/SoundManagerPreparationsMixin` | **不在**（有意） | **结论仍成立**。① 1.7.10 没有 `SoundManager$Preparations`（类注释已有完整落点分析）；② 替代实现已落地：`client/sound/GunSoundInstance.java:179-208` 反射拿 `SoundHandler#sndRegistry`（按**字段类型**定位，SRG 安全），为每条枪包音效幂等注册 `SoundEventAccessorComposite` + `SoundPoolEntry`（指向 `tacz_sounds/**.ogg`）；`GunPackResourcePack` 负责让枪包 ogg 在 `IResourceManager` 可见；`SoundPlayManager:823` 还有「找不到 tacz_sounds 就回退原版 sound event」的兜底。③ 默认枪包 1704 个 ogg 走该路径，**不需要**这个 Mixin。 |

> 顺带核对当前 `mixins.tacz.json` = 16 项（common 4 + client 12），与磁盘 18 个 mixin 类的差集正好是上表 2 个；本轮 mixin-fixer 新增的 `common.LivingEntityKnockbackMixin`、`client.MinecraftAttackMixin` **都已列入 json**，无「写了 mixin 但忘了登记」的情况。

---

## 四、r2 已修项是否还有「同类遗漏」

把 r2 的 5 条问题抽象成 3 种形态，本轮对每种做了**全量**（不再抽样）：

| 形态 | r2 案例 | 本轮全量扫描方法 | 剩余 |
|---|---|---|---|
| ①「入口/工厂存在，但没有调用者」 | 配方管理器、战利品注入、`/tacz convert` | 全部 `setXxx` 注入点（26）+ 全部管理器 lifecycle（120）+ 全部网络消息生产者（46）+ 全部 `public static` 单例入口 | **1 条新**（`AmmoStorageMenu.setContentSender`）；r2 遗留的 `PegboardStress.placeBoards` 本轮已被并发修好 |
| ②「事件类存在，但没注册到（正确）总线」 | `RefitTransform` | 全部 `@SubscribeEvent` 类（51）+ 上游 62 事件类映射 + 双总线规则逐条比对 | **1 条 P3**（`DisplayCaseLockHintOverlay`，已有墙钟兜底） |
| ③「注册 API 用错形态（传 Class / 静态处理器）」 | `CommonRegistry`、`CommonAssetsManager` | 全仓 `register(Xxx.class)` 模式扫描 + 所有 `@SubscribeEvent` 方法是否 `static`（静态会 ICCE） | **0**（唯一一处已修；`RefitTransform` 也已按实例注册） |

结论：**三类形态已基本收敛**，剩余问题全部集中在「静态注入点」这一类，且都不涉及主战斗链路（弹药库 GUI / 调试命令 / GUI LOD / 激光默认色）。

---

## 五、未覆盖说明

1. **反射/字符串驱动的注册**：`ClientProxy` 的 3 个 handler 字符串清单、`CompatRegistry` 的 `Class.forName`、`ItemClientHooks` 等已人工核对；但任何**运行期动态拼接**的类名（如脚本/配置驱动）静态扫描无法覆盖。
2. **B-2 的 471 条「上游同样 0 语法引用」未逐条人工读**：其中 POJO（Gson）、Lua API（脚本反射）已按形态归类排除；剩余少数普通方法理论上仍可能有真死代码，但**上游同样死**，非移植回归。
3. **Lua 脚本调用面**：`resources/**/*.lua` 里对 Java API（`api/util/Lua*`、`ModernKineticGunScriptAPI`、`GunAnimationStateContext` 等）的调用不在 Java 静态扫描内——这些类**未计入死代码**。
4. **`mixin/**` 本轮只读**（mixin-fixer 正在改）：只核对了 json 与文件的一致性、以及两个「有意不列」类的必要性；未审 mixin 注入点本身（r2-07 已审）。
5. **未跑 gradle / runClient**：本报告全部结论为**静态调用链证据**（0 调用者 / 未注册 / 无生产者 / 无赋值）。真缺口 1（弹药库列表）与真缺口 3（GUI LOD）建议各用一条实机用例确认：打开弹药库看列表是否为空、切到改装界面看枪模是否低模。
6. 上游「移植缺失方法」305 条沿用 `r2-06` 结论，本轮未重扫（不在本任务范围）。
