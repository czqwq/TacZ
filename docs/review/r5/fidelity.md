# r5-B · 上游保真与签名/注解对齐审查（api/util/command/config/init/event/mcshim/入口）

> 作用域（只读 + 授权写）：`api/**`（不含 `api/item/nbt/**`）、`util/**`、`command/**`、`config/**`、
> `init/**`、`event/**`、`advancement/**`、`sound/**`、`debug/**`、`mcshim/**`、`GunMod.java`、`CommonProxy.java`。
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（799 文件）；1.7.10 平台准绳：
> `build/rfg/minecraft-src/java/**`。历史结论（r2-05/06/09、r3）已登记的不重复报，只做复核。
> **未运行 gradle**；全部结论为静态证据（IDEA 索引 / 源码对读 / 调用点扫描）。

---

## 1. 结论速览

| 维度 | 结论 |
|---|---|
| **YSM 编译期硬契约**（`docs/YSM_COMPAT_HANDOFF.md` 12 行） | **全部仍在、签名兼容**（§2 逐符号，含行号）。mixin 侧 `EntityLivingBase implements IGunOperator, KnockBackModifier`、`EntityPlayerSP implements IClientPlayerGunOperator` 均在 `mixins.tacz.json` 生效列表内。 |
| **api/** 注解语义** | `@SideOnly(CLIENT)` 21 处（19 与上游 `@OnlyIn` 一一对应 + 2 处移植侧防御性新增，见 §3.2）/ `@Deprecated` 11 处 / `@Cancelable` 5 处与上游一一对应，无缺失。上游 `getName` 的 `@OnlyIn(CLIENT)` 正确落在移植侧 `getItemStackDisplayName` 上。 |
| **event/** 注册** | 20 个文件全部有注册点：16 个监听器类 + `EventHandlers` 注册入口 + 2 个生命周期回调（`CommonLoadPack`/`LoadingConfigEvent`）+ 1 个被 `AttachmentPropertyManager` 直接调用的 `ChangeGunPropertyEvent`。**双总线判定全部正确**（`TickEvent`/FML `PlayerEvent` → FML 总线；Forge 事件/TACZ 自定义事件 → `MinecraftForge.EVENT_BUS`）；`@SubscribeEvent` **全部 public 非 static 实例方法**（0 处 ICCE 风险）。 |
| **init/** 调用链** | 16 个 `Mod*` 的 `register()` 全部有真实调用点（`CommonProxy#preInit/init/postInit` + `CommonRegistry#commonSetup`）；`ModAttributes` 的 `RangedAttribute` 通过 `EntityConstructing` 真挂到 `BaseAttributeMap`（`ModAttributes.java:52-64` + `CommonRegistry.java:152-164` 双保险、幂等）。`ModParticles/ModSounds/ModContainer/ModLootModifiers/CapabilityRegistry/ModPainting` 空实现均有「1.7.10 无对应注册表 + 等价落地在何处」的类注释，等价路径存在（非半成品）。 |
| **command/** | 8 个子命令名/参数名/权限等级与上游逐字一致（`attachment_lock/debug/dummy/overwrite/reload/hide_tooltip_part/convert/config` + `debug pegboard`）；`RootCommand` 权限 2 = 上游 `hasPermission(2)`。 |
| **config/** | 配置定义类 13 个（COMMON/SERVER/CLIENT/PRE + common 3 + client 5 + sync 1）+ `config/util` 读取器 4 个 + 基础设施 10 个（共 27 文件）；89 键（r2 已机检逐键一致）本轮抽核 `SyncConfig` 21 项与上游逐项同名同默认值同范围；SERVER 配置同步**两端都接**：`SyncConfigSyncManager:49-59`（登录下发）↔ `ServerMessageSyncConfig:98-120`（`apply()` 覆盖内存）↔ `SyncConfig.applyFromServer:196`（不写本地文件，与上游只读语义一致）。 |
| **mcshim/** | 39 文件。**no-op 方法（shader/线程/矩阵/注册表 shim）经全仓调用点扫描 = 0 外部调用者 ⇒ 无功能失效**；真正在用的 shim（`PoseStack/BufferBuilder/BufferUploader/RenderType/MultiBufferSource/PacketBuffer/LightTexture/GsonHelper/Mth`）为功能实现或已记录降级。降级清单见 §5。 |
| **本轮修掉** | 3 条：`ConfigManager.saveAll()` 无调用点（配置改动不落盘）、`PreLoadConfig.save()` 无调用点（`/tacz overwrite` 重启即失效）、`init/ModRecipe` 一处事实错误注释（Lead 转派）。 |
| **未修（登记）** | 1 条 P3：`ConfigManager.reloadAll()` 无调用点（1.7.10 无 Forge 配置文件监视器；无安全自动落点，理由见 §6）。 |

**最严重的一条**：F-1/F-2 属「功能入口存在但没人调」的半实现——玩家/服主在游戏内改的配置（`/tacz config`、`/tacz overwrite`）**重启后全部丢失**，与上游 Forge 的「服务端停止时统一落盘」不一致。已按上游语义修复。

---

## 2. YSM 契约逐符号对账（独立复核，未采信前轮报告）

| # | 契约符号 | 移植侧位置 | 结论 |
|---|---|---|---|
| 1 | `TimelessAPI#getCommonGunIndex(ResourceLocation): Optional<CommonGunIndex>` | `api/TimelessAPI.java:158` | ✅ |
| 2 | `IGun#getIGunOrNull(ItemStack)` static @Nullable | `api/item/IGun.java:30-39` | ✅（1.7.10 空栈 = `null`） |
| 3 | `IGun#mainhandHoldGun(LivingEntity)` static | `api/item/IGun.java:44-48`（`@Deprecated`，上游同名同注解） | ✅ |
| 4 | `IGun#getMainHandFireMode(LivingEntity)` static | `api/item/IGun.java:76-85` | ✅ |
| 5 | `IGun#getGunId(ItemStack)` instance | `api/item/IGun.java:140-141` | ✅ |
| 6 | `GunTabType` 枚举（PISTOL/RPG/RIFLE…） | `api/item/GunTabType.java:5-29`（8 常量 + `@SerializedName`） | ✅ 常量名/序列化名一致 |
| 7 | `FireMode`（AUTO/SEMI/BURST/UNKNOWN + `fromName`） | `api/item/gun/FireMode.java:9-43` | ✅ |
| 8 | `IGunOperator#fromLivingEntity` + `getSyn*` 6 个 | `api/entity/IGunOperator.java:18`、`:25/:30/:35/:45/:50`（另 `getSynIsBolting:40`/`getSynIsAiming:57`/`getSynSprintTime:63` 也在） | ✅ 仅 `LivingEntity→EntityLivingBase`（平台不可避） |
| 9 | `IClientPlayerGunOperator#getClientShootCoolDown()` | `api/client/gameplay/IClientPlayerGunOperator.java:98` | ✅ |
| 10 | `ReloadState#getCountDown()` | `api/entity/ReloadState.java:42` | ✅ |
| 11 | `GunFireEvent#getShooter` / `GunMeleeEvent#getShooter` / `GunReloadEvent#getEntity`，**双端 post** | 类在 `api/event/common/**`；post 点 9 处 = 上游 9 处：`ModernKineticGunScriptAPI:210`、`entity/shooter/LivingEntityMelee:73`/`LivingEntityReload:89`、`client/gameplay/LocalPlayerMelee:97`/`LocalPlayerReload:108`/`LocalPlayerShoot:364`、`network/message/event/ServerMessageGun{Fire,Melee,Reload}:26` | ✅ `LogicalSide→cpw Side`（交接文档 §4 已写明） |
| 12 | `MuzzleFlashRender.isSelf` / `ShellRender.isSelf` = `public static boolean` | `client/model/functional/MuzzleFlashRender.java:47` / `ShellRender.java:39`（只读复核，属 C 范围） | ✅ 可直接赋值 |
| — | `CommonGunIndex#getType()` 返回 `pistol/rifle/rpg…` 字符串 | `resource/index/CommonGunIndex.java:168` | ✅ 值来自 `GunTabType @SerializedName` |

> **结论：12/12 通过，无 `NoSuchMethodError` / `NoClassDefFoundError` 风险**（外部模组在 1.7.10 侧仍需按交接文档 §4 换 `EntityLivingBase`/`Side` 等平台类型）。
> 另复核 mixin 生效性：`src/main/resources/mixins.tacz.json:8,14` 列有 `common.LivingEntityMixin`、`client.LocalPlayerMixin`，且 `LivingEntityMixin.java:57` = `implements IGunOperator, KnockBackModifier`、`LocalPlayerMixin.java:104` = `implements IClientPlayerGunOperator` ⇒ `fromLivingEntity`/`fromLocalPlayer` 的强转链完整。

---

## 3. 关键类符号对账

### 3.1 入口与生命周期（GunMod / CommonProxy）

| 上游 | 移植侧 | 判定 |
|---|---|---|
| `GunMod()` 构造器：3× `registerConfig`、`packType`、13 个 `DeferredRegister`、kubejs、`registerDefaultExtraGunPack`、`AttachmentPropertyManager.registerModifier` | `GunMod`（`@Mod` + `@SidedProxy` + 四段转发）+ `CommonProxy#preInit` 逐条对应（`CommonProxy.java:75-133`） | ✅ 顺序与上游一致（blocks→tabs→items→entities→recipe 之外的内容注册→attributes→particles→paintings） |
| `CommonRegistry` 四个 MOD 总线事件（`FMLCommonSetupEvent`/`FMLLoadCompleteEvent`/`EntityAttributeModificationEvent`/`AddPackFindersEvent`） | `CommonRegistry#commonSetup/markLoadComplete/registerAttributes` + `EntityConstructing` 处理器 + `GunPackLoader#reloadPackList` | ✅ 每个都有落点（`AddPackFindersEvent` → `CommonProxy.java:112`） |
| `RegisterCommandsEvent` | `CommandRegistry#registerCommands(FMLServerStartingEvent)`（`CommonProxy.java:165-170`） | ✅ |
| 服务端停止时的配置落盘（Forge `ConfigTracker#unloadConfigs`） | **原缺失 → 本轮补**：`GunMod#serverStopping` + `CommonProxy#serverStopping → ConfigManager.saveAll()` | ✅ 已修（F-1） |

### 3.2 api/** 注解语义（逐类机检 + 上游对读）

| 注解 | 上游 api 数量/位置 | 移植侧 | 判定 |
|---|---|---|---|
| `@OnlyIn(Dist.CLIENT)` → `@SideOnly(Side.CLIENT)` | 19 处：`TimelessAPI` 14 方法 + `AbstractGunItem#getName` + `IAttachmentModifier` 3 方法 + `IClientPlayerGunOperator` 类 | 21 处：19 处一一对应（`TimelessAPI:37..143`、`AbstractGunItem#getItemStackDisplayName:459`、`IAttachmentModifier:76/85/102`、`IClientPlayerGunOperator:16`）+ **2 处移植侧防御性新增**（`ThirdPersonManager:24`、`IThirdPersonAnimation:16` 类级；这两类引用 client-only `ModelPart`，专服本不该加载）。副作用：专服调用 `TimelessAPI#registerThirdPersonAnimation` 会 `NoClassDefFoundError`（上游 1.20 专服不会，但该 API 只可能被客户端调用） | ✅ 无遗漏；2 处新增已在此登记 |
| `@Deprecated` | 11 处（`IGun` 2、`IAttachment` 2、`TimelessAPI` 2、`GunProperties`、`AbstractGunItem`、`ResourceManager`、`AttachmentItemBuilder`、`JsonResourceLoader` 类） | 11 处同位置 | ✅ |
| `@Cancelable`（`cpw...eventhandler.Cancelable`） | 5 处（`EntityHurtByGunEvent` + `RenderLevelBobEvent`×2 + `RenderItemInHandBobEvent`×2） | 5 处 | ✅ |
| `@Nullable` | 多处（`IGun`/`IAttachment`/`IGunOperator`/`EntityHurtByGunEvent` 等，含参数与返回值） | 移植侧只多不少（含移植新增空安全注解；`IGun#getIGunOrNull` 参数/返回均标） | ✅ |

> 说明：`api/client/**` 中 `ObjectAnimationRunner` 等类引用 `net.minecraft.client.Minecraft` 但**没有** `@SideOnly` —— 与上游一致（上游这些类也无 `@OnlyIn`），且只被 client 代码引用，专服不加载，**不构成漂移**。
> `api/vmlib/LuaGunAnimationConstant` 引用 `client.animation.statemachine.GunAnimationConstant`：后者是**纯常量类且无 `@SideOnly`**（`GunAnimationConstant.java:6-24`），专服可正常加载；调用点只在 `ClientAssetsManager:134-135`（客户端）⇒ 无专服风险。

### 3.3 command/**（子命令逐条）

| 上游子命令 | 移植侧 | 参数名 | 权限 | 判定 |
|---|---|---|---|---|
| `attachment_lock <target> <AttachmentLock:boolean>` | `AttachmentLockCommand`（`ATTACHMENT_LOCK_NAME="attachment_lock"`，参数 `target`/`AttachmentLock`） | 一致 | 继承 root=2 | ✅ |
| `debug <enable:boolean>` + `debug pegboard …` | `DebugCommand`（`DEBUG_NAME="debug"`，`ENABLE="enable"`；`pegboard` 转 `PegboardStressCommand`） | 一致 | 2 | ✅ |
| `dummy <target> <dummyAmount:int>` | `DummyAmmoCommand`（`DUMMY_NAME="dummy"`） | 一致 | 2 | ✅ |
| `overwrite <enable:boolean>` | `OverwriteCommand`（反向语义 `set(!enable)` 保留） | 一致 | 2 | ✅（本轮补落盘，F-2） |
| `reload` | `ReloadCommand`（客户端回调注入 + 服务端 `reloadAllPack`） | — | 2 | ✅ |
| `hide_tooltip_part <target> <mask>` | `HideTooltipPartCommand` | 一致 | 2 | ✅ |
| `convert` | `ConvertCommand`（`setConverter` 已由 `ClientProxy` 注入） | — | 2 | ✅ |
| `config <key:ConfigKey> <state:boolean>` | `ConfigCommand`（`ConfigKey` 3 个枚举 + lang key 与上游一致） | 一致 | 2 | ✅ |

### 3.4 config/**（项名/默认值/范围）

- 89 键：r2-05 §D 已机检「逐键存在且默认值/范围一致」（仅 2 键写法等价、1 键 `List<List<String>>→List<String>` 已记录），本轮**抽核** `SyncConfig` 21 项（`config/sync/SyncConfig.java:27-58` ↔ 上游同名文件 `:10-39`）逐项同名同默认同范围，`ServerMessageSyncConfig` 21 项字段/读写顺序与 `SyncConfig` 一一对应（`ServerMessageSyncConfig.java:29-49/123-170`），无漏项（`CLIENT_GUN_PACK_DOWNLOAD_URLS` 上游已 `@Deprecated` 且无消费方，未同步，已注释）。
- `PreLoadConfig`：`gunpack.DefaultPackDebug` 键名/category/默认值/注释与上游一致；文件名 `tacz-pre.cfg` vs `tacz-pre.toml`、`<gamedir>/tacz/` 位置均已在类注释记录。
- `ConfigManager`：COMMON→SERVER→(CLIENT) 顺序、`LoadingConfigEvent` 的 4 个 `*ConfigRead.init()` 钩子、`isLoaded()` 语义均与上游对应。

### 3.5 mcshim/**（39 类，功能/降级判定）

| shim | 1.7.10 实现 | 判定 |
|---|---|---|
| `PoseStack`（含 `Pose`/`pushToGL`/`popFromGL`） | JOML 右乘，与 1.20 同构 | 功能实现 |
| `BufferBuilder`/`BufferBuilderVertexConsumer`/`BufferUploader`/`MultiBufferSource` | Java float[] 攒顶点 → `Tessellator` 逐顶点重灌（无 VBO） | 功能实现（性能降级，已记录） |
| `RenderType`（875 行） | GL 状态包（setup/clear 快照还原） | 功能实现（r2-02 已修 `clearRenderState` 污染） |
| `LightTexture` | `pack(block,sky)=(sky<<20) OR (block<<4)`（1.7.10 原生布局，可直接喂 `Tessellator#setBrightness`） | 功能实现（布局差异已记录） |
| `PacketBuffer` | 1.7.10 线上格式（NBT short+gzip、ItemStack itemId/count/damage、`readBlockPos→int[3]`） | 功能实现（差异已记录） |
| `Mth`/`GsonHelper`/`Tesselator`/`VertexFormat`/`DefaultVertexFormat`/`Axis`/`Lighting`/`GlStateManager` | 直通/等价实现 | 功能实现 |
| `Resource`/`ResourceManager`/`FileToIdConverter`/`PackSource`/`PreparableReloadListener`/`SimplePreparableReloadListener`/`ProfilerFiller` | 自建枪包资源索引 + profiler 薄封装 | 功能实现（r5-data-wiring 域在用） |
| `EntityPose`/`EntityStateHelper`/`AnimationPose`/`DummyPose`/`HumanoidArm`/`InteractionHand`/`ItemDisplayContext`/`InputConstants`/`SoundEvent`/`OverlayTexture` | 枚举/数据/语义 shim | 功能实现（`DummyPose` 与上游「空姿态单例」语义一致，`AnimateGeoItemRenderer:634/654` 在用） |

**no-op 且 0 外部调用者（无功能损失，属「保留 API 形状」）**：`RenderSystem#setShader/getShader/getVertexSorting/setShaderLightDirections/resetTextureMatrix/initRenderer/replayQueue/assertOnRenderThread/recordRenderCall/getModelViewStack/applyModelViewMatrix`、`BufferUploader#drawWithShader`、`MultiBufferSource#immediateWithBuffers`、`RenderType#end()`、`EntityRenderers#register/registerAll`、`BlockEntityRenderers#register/registerAll`。证据：全仓 `RenderSystem\.<方法>`/`EntityRenderers\.`/`BlockEntityRenderers\.` 扫描只命中自身与 javadoc（`GunSmithTableScreen:1162` 是注释里的示例）；真实渲染器注册走 1.7.10 原生 API（`client/init/ModEntitiesRender.java:52-63`：`RenderingRegistry.registerEntityRenderingHandler` + `ClientRegistry.bindTileEntitySpecialRenderer`）。

---

## 4. 发现表

| # | 级别 | 现象 | 根因 | 证据（本工程 ↔ 上游） | 状态 | 修法 |
|---|---|---|---|---|---|---|
| **F-1** | **P2** | 游戏内改的配置（`/tacz config` 改 `SyncConfig`；COMMON/SERVER 任意内存改动）**重启后丢失**，配置文件不更新 | `ConfigManager.saveAll()` **全仓 0 调用者**；1.7.10 `Configuration` 不像 1.20 Forge 那样在服务端停止时自动落盘 | 本工程 `config/ConfigManager.java:68-78`（方法存在、无 caller）↔ 上游 Forge `ConfigTracker#unloadConfigs` 在 `ServerStoppingEvent` 落盘（1.20 行为，无源码行可指，属平台机制）；`/tacz config` 写内存见 `command/sub/ConfigCommand.java:71-79` | **已修** | `GunMod` 增加 `@Mod.EventHandler serverStopping(FMLServerStoppingEvent)` → `CommonProxy#serverStopping` → `ConfigManager.saveAll()`（镜像上游「服务端停止落盘」时机）。改动：`GunMod.java:83-95`、`CommonProxy.java:172-180` |
| **F-2** | **P2** | `/tacz overwrite <bool>`（决定启动时是否保留用户改过的默认枪包目录）**重启后失效**：`PreLoadConfig.override` 只改内存，`tacz-pre.cfg` 不更新 | `PreLoadConfig.save()` **全仓 0 调用者**：上游唯一调用点是 Cloth Config 界面关闭时（`compat/cloth/MenuIntegration.java:31`），1.7.10 按用户决策只保留 compat 反射骨架、无该界面 ⇒ 命令侧必须自己落盘 | 本工程 `config/PreLoadConfig.java:70-74`（save 存在、无 caller）、`command/sub/OverwriteCommand.java:60`（只 set）↔ 上游 `compat/cloth/MenuIntegration.java:31` | **已修** | `OverwriteCommand.processCommand` 在 `override.set(...)` 后直接 `PreLoadConfig.save()`（一行注释说明上游由 Cloth 界面落盘）。改动：`command/sub/OverwriteCommand.java:60-62` |
| **F-3** | P3 | `init/ModRecipe` 注释称 `data/tacz/recipes/misc/blood_strike_1.json`「磁盘上不存在」，**与事实不符**（文件确实在 `src/main/resources/data/tacz/recipes/misc/`），会误导下一轮把它当「已解决」或去补加载 | 真正原因：`GunPackLoader#readPackData("recipes")` 只扫**枪包来源**（`GunPackLoader.java:206-222` 的 `INSTANCE.getPackSources()`），jar 根 `data/` 不是包来源；且该配方产物是 `minecraft:painting`+`EntityTag.variant=tacz:blood_strike_1`（1.7.10 无画变体），材料（8 木棍+1 羊毛）与 1.7.10 原版画配方等价，裁决不注册、不损失可获得物 | 本工程 `init/ModRecipe.java:311-313` ↔ `src/main/resources/data/tacz/recipes/misc/blood_strike_1.json:1-33`、`resource/GunPackLoader.java:206-222` | **已修**（Lead 转派） | 注释改为真实原因（扫描范围 + 产物等价原版画配方），**未新增配方代码** |
| **F-4** | P3 | `ConfigManager.reloadAll()` 全仓 0 调用者：手工改 `config/tacz-*.cfg` 后无热重载入口 | 上游由 Forge 的配置文件监视器（`ModConfigEvent.Reloading`）自动触发；1.7.10 无该机制。**不接入 `/tacz reload` 的原因**：命令的 `getSide().isClient()` 分支在集成服也走客户端路径，且客户端 reload 会把「服务端同步下来的 SERVER 值」用本地文件值覆盖（与 F-1 修复的语义冲突），有回归风险 | 本工程 `config/ConfigManager.java:57-66`（方法存在、无 caller）↔ 上游 Forge 机制（无源码行） | **未修（登记）** | 保留方法（供日后命令层/调试调用），需产品裁决是否提供「/tacz reloadconfig」；本轮不发明上游没有的命令语义 |
| **F-5** | 信息 | mcshim 中 shader/线程/矩阵/注册表 shim 是 no-op 或恒返回值 | 1.7.10 无 `ShaderInstance`/RenderThread/`EntityType` 注册表 | 见 §3.5；全部 no-op 方法 **0 外部调用者** | **非缺陷** | 无需修；调用方均走 1.7.10 原生 API（已核实 `ModEntitiesRender:52-63`） |
| **F-6** | 信息 | `util/ResourceScanner` 全仓 0 调用者（上游有 5 个 consumer：`JsonDataManager`/`LazyJsonDataManager`/`LootInjectionManager`/`AttachmentsTagManager`/`RecipeFilterManager`） | 1.7.10 无 datapack `ResourceManager`；机制已由 `resource/pack/GunPackResources` + `GunPackLoader#readPackData` 承接（5 个 manager 均已改用后者） | 本工程 `util/ResourceScanner.java:46-52`（@Deprecated + 类注释写明替代物）↔ 上游 5 个 manager 的 `ResourceScanner.scanDirectory` 调用 | **非缺陷（机制替换已记录）** | 无需修 |

> 复核确认「不是问题」的关键项：`event/**` 20/20 注册与总线（见 §1）；`init/**` 16/16 `register()` 调用链；`ModAttributes` 属性真注册（`RangedAttribute` + `EntityConstructing`）；`sound/SoundManager` 22 个音效常量名与上游逐字一致、`sendSoundToNearby` 有调用点（`ModernKineticGunScriptAPI:264` ↔ 上游 `:209`）；`advancement/{TargetHitTrigger,GunRefitTrigger}` 的降级事件广播**有真实调用点**（`TargetBlock:314`、`TargetMinecart:105`、5 个 `ClientMessage*`）；`debug/GunMeleeDebug` 有调用点（`ModernKineticGunItem:643`，仅 DEBUG 模式）。

---

## 5. mcshim 降级清单（「no-op 但被非 no-op 场景调用」专项）

**结论：不存在「no-op 却被真实功能路径调用」的 shim** —— 所有 no-op 方法的外部调用点 = 0。逐类如下（用于日后核对，避免重复排查）：

| shim 方法 | 行为 | 外部调用者 | 是否导致功能失效 |
|---|---|---|---|
| `RenderSystem#setShader/getShader/getVertexSorting/setShaderLightDirections` | no-op / null | **0** | 否（GLSL 需求走 `client/renderer/scope/ScopeLensProgram` 自建 program） |
| `RenderSystem#assertOnRenderThread/recordRenderCall/isOnRenderThread` | no-op / 立即执行 / true | **0** | 否 |
| `RenderSystem#getModelViewStack/applyModelViewMatrix` | 假 PoseStack（与 GL 脱钩） | **0**（`GunSmithTableScreen:1162` 仅是注释引用） | 否 |
| `RenderSystem#resetTextureMatrix/initRenderer/replayQueue` | no-op | **0** | 否 |
| `BufferUploader#drawWithShader` | 转 `draw()` | **0** | 否 |
| `MultiBufferSource#immediateWithBuffers` | 忽略预置 buffer | **0** | 否 |
| `RenderType#end()` | no-op（@Deprecated，改 `begin()` 返回 builder 的 `end()`） | **0** | 否 |
| `EntityRenderers/BlockEntityRenderers#register/registerAll` | 只登记后清空（转接表） | **0**（真实注册走 `ModEntitiesRender`） | 否 |
| `Lighting#setupForEntityInInventory` | 退化为 `enableStandardItemLighting`（上游是独立 GUI 光照） | 有调用点（GUI 物品渲染） | **轻微视觉差异**，类注释已记录 |
| `BufferUploader#draw` | 丢弃顶点 overlay（受伤红闪）通道 | 有（模型渲染） | 1.7.10 顶点格式无该槽位，已记录 |
| `RenderType` 可裁剪族 `cull=false` | 安全超集（绕序不符会整片消失，故不裁剪） | 有 | 已登记为平台偏离（r2 结论） |
| `LightTexture#pack` 布局 | 1.7.10 原生 `(sky<<20) OR (block<<4)` | 有 | 功能正确（不能套 1.20 布局），已记录 |
| `PacketBuffer#readEnum` 越界 | 返回 null（1.20 抛异常） | 有（网络） | 坏包更宽容，已记录 |

---

## 6. 已修清单 / 未修清单 / 覆盖清单 / 需实机验证

### 6.1 已修（4 文件，`lint_files(error)` 全 0）

| 文件 | 改动 |
|---|---|
| `src/main/java/com/tacz/guns/GunMod.java` | +`FMLServerStoppingEvent` 转发（`serverStopping`） |
| `src/main/java/com/tacz/guns/CommonProxy.java` | +`serverStopping(FMLServerStoppingEvent)` → `ConfigManager.saveAll()` |
| `src/main/java/com/tacz/guns/command/sub/OverwriteCommand.java` | +`PreLoadConfig.save()`（`/tacz overwrite` 落盘） |
| `src/main/java/com/tacz/guns/init/ModRecipe.java` | 修正 `blood_strike_1` 注释的事实错误（Lead 转派，未改加载逻辑/未加配方） |

### 6.2 未修（+理由）

1. **F-4 `ConfigManager.reloadAll()` 无调用点**：1.7.10 无配置文件监视器；接入 `/tacz reload` 会把客户端「服务端同步值」覆盖回本地文件值（回归风险），需产品裁决是否新增 `/tacz reloadconfig`。方法保留、语义正确。
2. **`SyncConfigSyncManager` 只在 `PlayerLoggedInEvent` 同步 SERVER 配置**（`network/SyncConfigSyncManager.java:49-59`）：`/tacz config` 改动不推给已在线客户端。上游 Forge 同样只在登录/配置重载时同步 ⇒ **保真，非缺陷**（若要做运行期广播，属新增语义，需裁决）。
3. **`api/item/nbt/**` 未审**：属 task-1（r5-nbt-sync）写作用域。

### 6.3 覆盖清单（诚实）

- **逐文件读完**：`GunMod`、`CommonProxy`、`event/**` 20/20、`init/**` 16/16、`command/**` 12/12、`advancement/**` 2/2、`sound/**` 1/1、`debug/**` 1/1、`config/{ConfigManager,ConfigBuilder,TaczConfigSpec,ConfigValue,BooleanConfigValue,PreLoadConfig,CommonConfig,ServerConfig,ClientConfig,sync/SyncConfig,common/OtherConfig,util/AmmoBlockConfigRead}`。
- **按类/方法抽查 + 调用点扫描**：`api/**` 关键契约类（TimelessAPI/IGun/IGunOperator/GunTabType/FireMode/ReloadState/IClientPlayerGunOperator/AbstractGunItem/event.common）+ 注解全量机检（`@SideOnly`/`@Deprecated`/`@Cancelable`/`@Nullable`）；`util/**` 37 文件中读 15（`RefitDebug/HitboxHelper/GetJarResources/ResourceScanner/ContentPackOrder/…`）+ 其余用调用点扫描与 r2/r3 结论复核；`mcshim/**` 39 文件中读 20（RenderSystem/BufferUploader/MultiBufferSource/ResourceManager/FileToIdConverter/PoseStack/RenderType 关键段/EntityRenderers/BlockEntityRenderers/GlStateManager/InputConstants/ProfilerFiller/Lighting/Axis/ItemDisplayContext/EntityStateHelper/DummyPose/LightTexture/…）+ 全量 no-op 正则扫描与外部调用点扫描。
- **未逐行读**：`api/client/animation/**`（47 文件，r2 已机检 + 本轮只核注解/客户端隔离）、`util/**` 剩余 22 文件内部实现、`mcshim/**` 剩余 19 文件内部实现、`config/**` 其余 client/common 配置类的逐键比对（采用 r2-05 §D 机检结论 + 本轮抽核）。
- **未运行 gradle / runClient**：本轮结论均为静态证据；下列需实机确认。

### 6.4 需实机验证（本轮修复项）

1. **配置落盘**：进服 → `/tacz config serverShootCooldownCheck false` → 正常退出世界/关服 → 检查 `config/tacz-server.cfg` 中 `ServerShootCooldownCheck=false` 已写入 → 重进后仍为 false。
2. **overwrite 落盘**：`/tacz overwrite true` → 退出 → 检查 `<gamedir>/tacz/tacz-pre.cfg` 的 `[gunpack] DefaultPackDebug=true`（注意命令是反向语义：`true` → `override=true` → 保留用户改动）→ 重进后行为保持。
3. **无回归**：正常开服/关服日志无 `serverStopping` 相关异常；`ConfigManager.saveAll()` 在专用服务端不触碰客户端配置（`ClientConfig.CLIENT_CONFIG_SPEC == null` 分支跳过）。

---

## 7. 附：本轮修复的代码位置（供 Lead 复核 diff）

- `GunMod.java:83-95`：新增 `@Mod.EventHandler serverStopping(FMLServerStoppingEvent)`。
- `CommonProxy.java:172-180`：新增 `serverStopping` → `ConfigManager.saveAll()`。
- `OverwriteCommand.java:60-62`：`PreLoadConfig.save()`。
- `ModRecipe.java:311-313`：注释事实纠正。
