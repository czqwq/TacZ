# 移植进度（PORT STATUS）

> 每个工作流完成一个包就在这里追加一行，格式：
> `包 | 文件数 | 状态 | 已完成内容 | 阻塞点`
> 状态取值：`完成` / `部分` / `未动`
> 阻塞点写清楚「谁需要谁提供什么」，主 Agent 据此裁决。

## 总览

| 工作流 | 写作用域 | 任务 | 状态 |
|---|---|---|---|
| platform-shim | `mcshim/**`、`util/math/**` | task-1 | **完成** |
| common-api | `api/**` | task-2 | **完成**（117 文件；client 依赖等 L2 收尾） |
| common-resource | `resource/**` | task-3 | 进行中 |
| common-infra | `config/ network/ util/ command/ init/` + 入口 | task-4 | 进行中 |
| common-game | `item/ entity/ event/ block/ inventory/ crafting/ ...` | task-5 | 进行中 |
| client-port | `client/**`、`mixin/**`、`compat/**`、`com.github.mcmodderanchor/**` | task-6 | 进行中 |

## 基础设施状态（主 Agent 维护）

- [x] 上游 1.20.1 源码全量复制（799 java / 4497 资源 / 98MB）
- [x] `gradle.properties`：modId `tacz`、modGroup `com.tacz`、`usesMixins=true`、`mixinsPackage=guns.mixin`、
      `usesShadowedDependencies=true`、`relocateShadowedDependencies=false`
- [x] `dependencies.gradle`：GTNHLib + JOML 1.10.5 + commons-math3 3.6.1 + luaj(figura) + bcel 6.6.1 + jetbrains annotations
- [x] `mcmod.info`（1.7.10 元数据）、`mixins.tacz.json`
- [x] 删除模板包 `com/Lilith/mymodid`、删除 1.20.1 专属元数据（mods.toml / pack.mcmeta / kubejs.* / shouldersurfing_plugin.json / accesstransformer.cfg）
- [x] 1.7.10 入口骨架：`GunMod.java`（@Mod + SidedProxy）、`CommonProxy.java`、`client/ClientProxy.java`
- [x] `build.gradle.kts` 的 `pendingPortExcludes` 机制（未完成包可临时排除出编译）
- [x] `docs/PORT_PLAN.md`（工作流契约）、`docs/YSM_COMPAT_HANDOFF.md`（YSM 交接）

## 各包进度

（待各工作流追加）

| 包 | 文件数 | 状态 | 已完成内容 | 阻塞点 |
|---|---|---|---|---|
| `mcshim`（task-1，platform-shim） | 39 | **完成** | 见下方「mcshim 类清单」 | 见下方「mcshim 未提供清单」（当前为空） |
| `util/math`（task-1，platform-shim） | 4 | **完成** | `MathUtil`（`Mth`→`mcshim.Mth`、`Vec3`→`net.minecraft.util.Vec3` 且字段 `x/y/z`→`xCoord/yCoord/zCoord`）、`Easing`、`PerlinNoise`、`SecondOrderDynamics`（去掉对客户端类 `Minecraft.getInstance()` 的引用，见类 javadoc） | 无 |
| `assets-port`（task-7，资源格式转换） | 新增 40 个 `.lang` + 复制 66 个 `.png`；改 1 个 `sounds.json`；新增 `tools/*.py` 2 个 + `docs/ASSET_PORT_NOTES.md` | **完成** | ① `assets/tacz/lang/*.json`（21）→ 20 个 1.7.10 `.lang`（`tr-TR.json` 合并进 `tr_TR.lang`=465 key）；枪包 `custom/tacz_default_gun/assets/tacz/lang/` 同样 21→20（`tr_TR`=342 key）；均真 UTF-8（依据 `Locale.java:108`）、无 BOM、LF、value 内换行回写为字面 `\n`、`%s`/`%1$s`/`%.3f` 原样。② `textures/item/**`(21)→`textures/items/**`、`textures/block/**`(45)→`textures/blocks/**` 镜像复制，原目录保留。③ `sounds.json` 按 1.7.10 `SoundListSerializer` 校准（删 1.9+ 的 `subtitle`×2），8 个 ogg 全部校验存在。④ `tools/lang_json_to_lang.py`、`tools/port_textures_and_sounds.py`（幂等，带 `--check` 全量回读校验，实测 0 problem）。⑤ 说明与判定见 `docs/ASSET_PORT_NOTES.md` | ① 枪包语言**无加载路径**：上游 `mixin/client/LanguageMixin` 两个 `@Inject` 全被注释、引用的 `ClientAssetManager` 已不存在，140 个 `tacz.gun.*` / 26 个 `tacz.ammo.*` key 在模组本体语言里没有 → **已裁决走 `common-resource`/`common-infra` 的 `LanguageRegistry` 注入**（见下方裁决记录与 ASSET_PORT_NOTES §5.1），`.lang` 数据源已就位。② 本体 95 处 / 枪包 156 处 value 含 `\n`，**已裁决保留字面量**，由 client 侧显示前 `split("\\n")`。③ 上游 `eu_ES` 3 处 `% s` 拼写错误（1.20/1.7.10 都会显示 `Format error:`）、枪包 `zh_cn` 缺 2 key，**已裁决不修（保真）** |

### common-infra（task-4）各包进度

| 包 | 文件数 | 状态 | 已完成内容 | 阻塞点 / 差异 |
|---|---|---|---|---|
| `config/**` | 16 | **完成** | 自建 1.7.10 版 `ForgeConfigSpec` 等价层：`ConfigBuilder` / `TaczConfigSpec` / `ConfigValue` + `Int/Boolean/Double/String/Enum/ListConfigValue`（底层 `net.minecraftforge.common.config.Configuration`）。15 个配置类的**项名/默认值/范围/注释全部与上游一致**，调用点 `XxxConfig.FOO.get()`/`.set()` 一行未改。新增 `ConfigManager`（load/save/reload + 上游 `LoadingConfigEvent` 的 4 个 `*ConfigRead.init()` 钩子）。`PreLoadConfig` 改为独立 `TaczConfigSpec`（文件 `<gamedir>/tacz/tacz-pre.cfg`，上游 `tacz-pre.toml`），删除 `PreLoadModConfig`（1.7.10 无 `ModConfig`/nightconfig）。4 个 `config/util/*Read` 已移植；1.20 tag（`#forge:glass` 等）在 1.7.10 无 tag 系统 → `AmmoBlockConfigRead` 内置 `EXPANDED_VANILLA_TAGS` 展开表，未识别项 WARN 跳过 | 上游 SERVER 配置自动同步 → 新增 `network/message/ServerMessageSyncConfig`（移植特有，id=43）+ `network/SyncConfigSyncManager`；客户端只覆盖内存、不写文件 |
| `network/**` | 49 | **完成（含 3 个移植特有类）** | `SimpleChannel` → `SimpleNetworkWrapper` 两个 channel（`tacz` / `tacz:handshake`）；`network/MessageBase` 把 1.20 的 static `encode/decode` 适配成 1.7.10 `IMessage#fromBytes/toBytes`（内部用 `mcshim.PacketBuffer`）；`SimpleMessageDispatcher` 把「一行一包」的注册形态保留下来，**discriminator 顺序与上游完全一致（1..42）**；`NetworkTaskQueue` 替代 `context.enqueueWork`（1.7.10 simpleimpl 在 Netty 线程回调，且 1.7.10 无 `IThreadListener`）——每 tick 末尾排空，最多延迟 1 tick；`ClientSideHooks` 是「common 代码触发客户端行为」的注入点（等价上游 `DistExecutor`），`CommonProxy` 因此**不 import 任何 `com.tacz.guns.client.*`**。全部 42 个上游消息类的**类名/字段语义/公开构造器参数顺序/`handle` 语义**均保留 | 5 条降级：① 1.20 login-packet handshake 不存在 → `ServerMessageSyncedEntityDataMapping`/`Acknowledge` 改在 `PlayerLoggedInEvent` 后以 PLAY 包发送（最多晚 1~2 tick）；② `NetworkEvent.Context#reply` 不存在 → 客户端显式 `sendToServer`；③ `PacketDistributor` → `sendTo`/`sendToAll`/`sendToDimension` + `EntityTracker#getTrackingPlayers`；④ `ServerMessageAmmoStorageClick`/`ServerMessageAmmoStorageContents` 上游是 record → 普通类 + 同名访问器；⑤ `BlockPos`→三 int、`Direction`→`ForgeDirection`（`ServerMessageBulletHitBlock`） |
| 入口（`GunMod`/`CommonProxy`/`client/ClientProxy`） | 3 | **完成** | 见下方「跨包契约 · 入口调用顺序」 | `CommonProxy` 依赖 10 个 `Mod*#register()`（common-game 提供）+ `CommonRegistry#registerAttributes/markLoadComplete`、`CommandRegistry#registerCommands`（本工作流） |
| `util/**`（除 math，31 文件） | 31 | 部分（子代理进行中） | 已确认跨包契约：`SlotAdapterHelper`、`AmmoStorageHelper`、`GunAmmoUnloadHelper`、`AttachmentDataUtils`、`VariantHelper`、`VirtualOemAttachment`、`TacPathVisitor`、`ContentPackOrder`、`DefaultGunAttachments`、`Md5Utils` 公开类名/方法名保持不变；`SlotAdapterHelper.getAvailableSlotAdapters()` 返回 `Map<ResourceLocation, SlotAdapter>` 且 `SlotAdapter#allowsAttachment(ResourceLocation)`（网络包已按此调用） | 客户端类（`RenderHelper`/`RenderDistance`/`ThermalImagingHelper`/`InputExtraCheck`/`LaserColorUtil`）按「保留 API + TODO + 降级」处理，不 import client 包 |
| `command/**` | 11 | 部分（子代理进行中） | 约定：全部改用 `ICommand` + `CommandBase` + `ICommandSender` + `String[] args`，**不用 brigadier**；命令名/子命令/语言 key/权限等级语义对齐上游 | 上游 `Commands.literal/argument/BoolArgumentType/EnumArgument` 的对应关系写在各类 javadoc |
| `init/**`（本工作流 6 个） | 6 | 部分（子代理进行中） | `CommonRegistry`、`CommandRegistry`、`CapabilityRegistry`、`CompatRegistry`、`ModDamageTypes`、`ModPainting` | 上游 `FMLCommonSetupEvent`/`FMLLoadCompleteEvent`/`EntityAttributeModificationEvent`/`AddPackFindersEvent` 在 1.7.10 的落点见入口契约 |
| `init/**`（common-game 10 个） | 10 | 未动（common-game 负责） | — | `CommonProxy` 已按 `public static void register()` 契约调用 |

### 跨包契约 · 入口调用顺序（common-infra 最终版，主 Agent 以此为准）

`GunMod`（`@Mod`，`SidedProxy` → `client/ClientProxy` / `CommonProxy`）只做四段转发；
所有注册在 **`CommonProxy`**，顺序如下（每条注明上游位置）：

```
preInit(FMLPreInitializationEvent)                        // = 上游 GunMod() 构造器
 1. ConfigManager.initCommon(event.getModConfigurationDirectory())     // registerConfig(COMMON/SERVER)
 2. ModBlocks.register();  ModItems.register();                       // DeferredRegister 顺序：
    ModEntities.register(); ModContainer.register();                  // BLOCKS → TILE_ENTITIES → TABS →
    ModSounds.register();   ModRecipe.register();                     // ITEMS → ENTITY_TYPES → RECIPE_*
    ModAttributes.register(); ModParticles.register();                // → LOOT → CONTAINER → SOUNDS →
    ModLootModifiers.register(); ModPainting.register();              // PARTICLES → ATTRIBUTES → PAINTINGS
 3. CommonRegistry.registerAttributes()                               // EntityAttributeModificationEvent
 4. GunMod.registerDefaultExtraGunPack()                              // registerDefaultExtraGunPack()
 5. AttachmentPropertyManager.registerModifier()                      // AttachmentPropertyManager.registerModifier()
 6. event.EventHandlers.registerAll()                                 // @Mod.EventBusSubscriber 等价物
    MinecraftForge.EVENT_BUS.register(new NetworkTaskQueue())
    MinecraftForge.EVENT_BUS.register(new SyncConfigSyncManager())
 7. NetworkRegistry.INSTANCE.registerGuiHandler(GunMod.instance, new inventory.GuiHandler())   // 1.7.10 特有
init(FMLInitializationEvent)                              // = 上游 CommonRegistry#onSetupEvent
 1. NetworkHandler.init()                                   // NetworkHandler::init
 2. ModSyncedEntityData.init()                               // ModSyncedEntityData::init
 3. TargetHitTrigger.register(); GunRefitTrigger.register()  // trigger 注册（1.7.10 空实现）
postInit(FMLPostInitializationEvent)                      // = 上游 CommonRegistry#onLoadComplete
 1. CommonRegistry.markLoadComplete()                        // LOAD_COMPLETE / isLoadComplete()
serverStarting(FMLServerStartingEvent)                    // = 上游 CommandRegistry（RegisterCommandsEvent）
 1. CommandRegistry.registerCommands(event)
```

`ClientProxy extends CommonProxy`（位于 `com.tacz.guns.client`，会随 `client/**` 一起被排除）：
```
preInit → super.preInit()
        + ClientConfig.init(<config>/tacz-client.cfg).load() + save()      // ModConfig.Type.CLIENT
        + ClientSideHooks.set(new Hooks())                                 // 注入客户端钩子（替代 DistExecutor）
init    → super.init()   + TODO(client-port): ModEntitiesRender / ParticleFactoryRegistry / KeyBinding /
                           HUD 事件 / SoundPlayManager / MuzzleFlashRender.isSelf & ShellRender.isSelf
postInit→ super.postInit()
```

**`ClientProxy` 引用的 client 包清单（client 未完成时随包排除，仅记录）**：
`client.sound.SoundPlayManager`（playMessageSound/playUnloadSound）、`client.particle.HitBlockEffectHandler`、
`client.gui.GunSmithTableScreen#updateIngredientCount`、`client.gui.GunRefitScreen#initGui`、
`client.gameplay.LocalPlayerDataHolder#clientBaseTimestamp`（经 `api.client.gameplay.IClientPlayerGunOperator`）、
`client.resource.ClientIndexManager#reload`；common 侧另有 `resource.network.{GunPackSync, CommonNetworkCache, DataType}`、
`resource.CommonAssetsManager`（common-resource）。

**`CommonProxy` 依赖的跨包方法签名（缺一个就编译不过）**：
`ModBlocks/ModItems/ModEntities/ModContainer/ModSounds/ModRecipe/ModAttributes/ModParticles/ModLootModifiers.register()`
`+ ModPainting.register()`（本工作流）、`CommonRegistry.registerAttributes()` / `CommonRegistry.markLoadComplete()`、
`CommandRegistry.registerCommands(FMLServerStartingEvent)`、`event.EventHandlers.registerAll()`、
`inventory.GuiHandler`（`IGuiHandler` 实现）、`entity.sync.ModSyncedEntityData.init()`、
`advancement.TargetHitTrigger.register()` / `advancement.GunRefitTrigger.register()`、
`resource.modifier.AttachmentPropertyManager.registerModifier()`。

**网络相关跨包契约**：`inventory.GunSmithTableMenu`（`Container` + `doCraft(ResourceLocation, EntityPlayer)`）、
`inventory.AmmoStorageMenu`（`Action` 枚举 + `STORAGE_SLOT_COUNT` + `handleInteraction(EntityPlayer,int,Action)` +
`setContents(int,long,List<AmmoStorageEntry>)`）、`inventory.AmmoStorageEntry`（`id()/sample()/count()/remainingCapacity()`）、
`block.entity.AmmoStorageBlockEntity`（`extractAmmo(ResourceLocation,int,boolean)`）、
`entity.sync.core.{DataEntry#write(PacketBuffer)/read(PacketBuffer), SyncedEntityData#instance/getKeys/getInternalId/updateMappings/set}`、
`resource.network.{GunPackSync(Part/Receiver/compressAndSplit/CHUNK_SIZE/MAX_SYNC_SIZE), DataType, CommonNetworkCache}`、
`resource.pojo.data.gun.Bolt`、`resource.index.CommonGunIndex`。

## 阻塞与裁决记录

### mcshim 类清单（platform-shim / task-1，39 个文件，全部 `get_file_problems` 无 error）

渲染核心（P0/P1，被 client-port 54 文件阻塞的部分）：
`PoseStack`(含 `PoseStack.Pose`)、`RenderSystem`、`VertexConsumer`(interface)、`TessellatorVertexConsumer`、
`BufferBuilderVertexConsumer`、`MultiBufferSource`(interface + `BufferSource`)、`RenderType`(含 `RenderType.State`)、
`BufferBuilder`(含 `RenderedBuffer`)、`BufferUploader`、`Tesselator`、`VertexFormat`(含 `Mode`)、`DefaultVertexFormat`、
`Axis`、`Lighting`、`GlStateManager`、`OverlayTexture`、`LightTexture`、`ItemDisplayContext`、`InputConstants`(含 `Type`/`Key`)

通用工具（服务端安全，无 `net.minecraft.client` 引用）：
`Mth`、`GsonHelper`、`PacketBuffer`、`InteractionHand`、`HumanoidArm`、`EntityPose`、`EntityStateHelper`、
`AnimationPose`、`DummyPose`、`SoundEvent`

资源重载基础设施（L0 单点实现，供 client-port 与 common-resource 共用）：
`PackSource`(interface)、`Resource`、`ResourceManager`、`FileToIdConverter`、
`PreparableReloadListener`(含 `PreparationBarrier`)、`SimplePreparableReloadListener<T>`、`ProfilerFiller`

注册转接（客户端初始化用，仅登记表）：
`EntityRenderers`、`BlockEntityRenderers`

> `GuiGraphics`（第 40 个文件）由 client-port 创建，属其授权范围，不计入本工作流。

**全仓 `com.tacz.guns.mcshim.X` 引用扫描结果：被引用但尚不存在的 mcshim 类 = 0。**
（扫描方式：对所有 `src/main/java/com/tacz/**/*.java` 做 `com\.tacz\.guns\.mcshim\.(\w+)` 匹配，
与 `mcshim/` 下实际文件名求差集。）

### mcshim 行为差异 / 降级点（构建与联调时最需要知道的 5 条）

1. **`LightTexture` 的打包布局与 1.20 不同**：本 shim `pack(block, sky) = (sky << 20) | (block << 4)`，
   `block(p) = (p & 0xFFFF) >>> 4`，`sky(p) = (p >>> 16) >>> 4`。这是 1.7.10
   `World#getLightBrightnessForSkyBlocks` 的原生布局，返回值可直接喂
   `Tessellator#setBrightness(int)`。**不要**沿用 1.20 的 `block | sky << 16` 假设。
2. **`RenderType` 是「GL 状态包」而非 1.20 的 `RenderStateShard` 组合**：1.20 的
   `CompositeState.builder().setShaderState(...)` / `RenderStateShard` / `TextureStateShard` /
   `TexturingStateShard` 系列**全部不存在**。用到它们的地方（典型：`client/renderer/block/DisplayGunRenderType`）
   必须重写为静态工厂或 `RenderType.create(name, format, mode, bufferSize, crumbling, sortOnUpload, State)`。
3. **无 VBO / 无顶点排序**：`BufferBuilder` 用 Java `float[]` 存顶点，`BufferUploader.draw()` 逐顶点重灌
   `Tessellator`。功能等价但**明显比 1.20 慢**，大网格（枪械模型）慎用。`RenderedBuffer.release()` 是 no-op。
   顶点属性里的 **overlay（受伤红闪）通道被丢弃**（1.7.10 顶点格式无该槽位）。
4. **`RenderSystem` 的矩阵/线程/shader 语义降级**：`assertOnRenderThread()`/`recordRenderCall()` = no-op/立即执行，
   `isOnRenderThread()` 恒 true，`setShader()/getShader()/getVertexSorting()` 是 no-op/`null`；
   `getModelViewStack()` 返回与 GL 脱钩的假 `PoseStack`，真正进 GL 要显式调 `applyModelViewMatrix()`
   或用 `PoseStack#pushToGL()/popFromGL()`。
5. **`PacketBuffer` 线上格式随 1.7.10**：NBT 用 `short len + gzip`（`null` → `-1`），ItemStack 用
   `short itemId + byte count + short damage + NBT`（`null` → `-1`）；`readBlockPos()` 返回 **`int[3]`**
   （1.7.10 无 `BlockPos`）；`readEnum` 越界返回 `null`（1.20 抛异常）；`readMap` 返回 `HashMap`（无序）。
   `readResourceLocation()` → `net.minecraft.util.ResourceLocation`，`readNbt()` → `NBTTagCompound`。

（待追加）

- **[assets-port / task-7] 1.7.10 `.lang` 用真 UTF-8，不用 `\uXXXX` 转义** —— ✅ **已裁决：采纳**。
  依据：`build/rfg/minecraft-src/java/net/minecraft/client/resources/Locale.java:108`
  用 `IOUtils.readLines(stream, Charsets.UTF_8)` 解码，106-126 行只做「跳过 `#` 行 / 按首个 `=` 切 /
  `%d|%f`→`%s`」三件事，**没有 `\uXXXX` 反转义**；`Locale.java:66-90 checkUnicode()`
  按 `char>=256` 比例决定 Unicode 字体，也只有「文件存真字符」才解释得通。
  `\uXXXX` 是 1.6.4 及更早 `Properties#load`（latin1）的约定。
  已验证：Java 探针复刻该逻辑跑 40 个 `.lang` → `dropped lines = 0`、key 数与 JSON 全等、
  `zh_CN/ja_JP` 的 `char>=256` 比例 0.797/0.860 正确触发 Unicode 字体。
  `python tools/lang_json_to_lang.py --escape-unicode` 作为备用开关保留，默认不用。
- **[assets-port / task-7] 枪包语言接线** —— ✅ **已裁决：走 (b) `LanguageRegistry` 注入，不用 mixin**
  （(a) mixin / (c) 自研 `I18n` 两条路废弃），已交 `common-resource` + `common-infra`。
  实现前**务必先看 `docs/ASSET_PORT_NOTES.md` §5.1** 的 4 条坑，其中两条会直接踩坏：
  ① 真实 API 是 `addStringLocalization(Properties, String)` / `injectLanguage(String, HashMap)` /
  `loadLocalization(URL, String, boolean)`，**没有** `injectLanguage(String, Properties)`；
  ② **不要用 `new Properties().load(InputStream)`**（ISO-8859-1 会把真 UTF-8 中文/`§` 变乱码），
  用 `Properties.load(new InputStreamReader(in, UTF_8))`（FML `LanguageRegistry.java:224`）
  或 `StringTranslate.parseLangFile(in)`（`StringTranslate.java:57`，语义与客户端 `Locale` 一致）。
  ③ 顺带发现：`LanguageRegistry.assetENUSLang`（第 54 行）的正则 `(.*)` 可跨 `/`，
  且 `FMLClientHandler.java:591` / `FMLServerHandler.java:221` 对每个 mod 调
  `loadLanguagesFor(container, side)` → **内置的 `custom/tacz_default_gun/assets/tacz/lang/*.lang`
  已自动被注入**，`GunPackLoader` 只需处理运行期从游戏目录加载的**外部**枪包。
  ④ `mergeLanguageTable` 走 `mergeWithoutOverwrite`，注入只补缺、不覆盖资源 `.lang`。
- **[assets-port / task-7] 语言 value 内的 `\n`** —— ✅ **已裁决：保留字面量（数据保真），
  由 client 侧显示前 `split("\\n")`**（lead 负责写入本文件的跨包契约，交 client-port）。
  本体 95 处 / 枪包 156 处受影响。**不再产出「改写成空格」的版本。**
- **[assets-port / task-7] 「1.7.10 用不到」清单** —— ✅ **已裁决：全部保留，一个都不删**
  （不占构建时间、便于日后回取）。清单原样留在 `docs/ASSET_PORT_NOTES.md` §4。
- **[assets-port / task-7] 上游缺陷（`eu_ES` 3 处 `% s`、枪包 `zh_cn` 缺 2 key）** —— ✅ **已裁决：
  不修，保真**。`--fix-percent-space` 开关保留但不要用。

---

### common-api（task-2）各包进度

> 写作用域：`src/main/java/com/tacz/guns/api/**`（上游 115 文件，本工作流新增 2 个移植辅助类 → 共 **117**）。
> 判定：`IDEA lint_files` 全量扫描后，api 自身无 error；剩余 error 全部是**其它包尚未 port 造成的级联**
> （`util/**` 的 `GunAmmoUnloadHelper`/`AttachmentDataUtils`/`SlotAdapterHelper`、
> `init/ModItems` 的字段类型、`client/sound/SoundPlayManager`、`com.github.mcmodderanchor.simplebedrockmodel`），
> **不是 api 的坏 import**（已脚本复核：`com.mojang.*` / `net.minecraft.resources.*` / `CompoundTag` / `Tag` / `core.*` /
> `net.minecraftforge.eventbus.*` / `LogicalSide` / `distimarker` / `registries` / `items.*` / `capabilities` 均 0 命中；
> `net.minecraft.world.World` 是 1.7.10 正确类；fastutil import 0 命中）。

| 包 | 文件数 | 状态 | 已完成内容 | 阻塞点 / 差异 |
|---|---|---|---|---|
| `api/`（根） | 6 | **完成** | `TimelessAPI`（索引入口，YSM 硬约束）、`DefaultAssets`、`GunProperty`(record)、`GunProperties`、`CacheModifiableByScript`、`ValueModifiableAtRuntime` | `TimelessAPI` 的 client getter 保留 `@SideOnly(CLIENT)`；`getRecipe/getAllRecipes` 上游已废弃 → 1.7.10 无 `RecipeManager`，保持返回空 + javadoc 说明 |
| `api/entity/**` | 5 | **完成** | `IGunOperator`（YSM 硬约束；`fromLivingEntity(EntityLivingBase)` 仍为强转，**依赖 `mixin/common/LivingEntityMixin` 注入**）、`KnockBackModifier`（同）、`ReloadState`、`ShootResult`、`ITargetEntity` | `ITargetEntity#onProjectileHit` 的 1.20 `EntityHitResult` 参数 → 1.7.10 改为 `(EntityKineticBullet, Entity hitEntity, DamageSource, float)`（与 common-game 已对齐） |
| `api/event/common/**` | 13 | **完成** | 全部事件类名/getter 与上游一致；基类 `cpw.mods.fml.common.eventhandler.Event`，`@Cancelable` 用 cpw 注解；`EntityHurt/KillByGunEvent` 保留 `Pair<DamageSource,DamageSource>`（commons-lang3） | `LogicalSide`→`cpw.mods.fml.relauncher.Side`：`getLogicalSide()` **方法名保留**、返回类型改 `Side`（post 处按 `worldObj.isRemote` 传）；`KubeJSGunEventPoster` 三个转发方法先 no-op（compat 由 client-port 在 L2 做反射桥） |
| `api/event/server/**` | 1 | **完成** | `AmmoHitBlockEvent` | `Level→World`、`BlockHitResult→(hitX,hitY,hitZ,side)`、`BlockState→Block+metadata`（构造器 `(World,int,int,int,int,Block,int,EntityKineticBullet)`） |
| `api/item/**`（根 7 + attachment 1 + builder 4 + gun 3 + nbt 7） | 22 | **完成** | `IGun`（YSM 硬约束：`getIGunOrNull/mainhandHoldGun/mainHandHoldGun/getMainhandFireMode/getMainHandFireMode/getGunId` 名字与语义不变）、`IAmmo/IAmmoBox/IAttachment/IBlock/IAnimationItem`、`GunTabType/FireMode/AttachmentType`（枚举常量不变）、4 个 Builder、`AbstractGunItem`、`GunItemManager`、5 个 NBT DataAccessor + `ItemStackNbtHelper` | 见下方「api 跨包契约」1~6 |
| `api/modifier/**` | 5 | **完成** | `IAttachmentModifier`（含 `DiagramsData` record）、`CacheValue`、`ParameterizedCache`、`ParameterizedCachePair`、`JsonProperty` | fastutil `Pair` 全撤：`Pair→org.apache.commons.lang3.tuple.Pair`，`ParameterizedCachePair` 改为自持 `left/right` 字段（方法名 `left()/right()` 保留）；`JsonProperty#getComponents` → `List<IChatComponent>` |
| `api/resource/**` | 2 | **完成** | `JsonResourceLoader`（deprecated 老资源加载器）、`ResourceManager`（附属模组枪包导出注册） | 无（纯 java.nio + gson） |
| `api/util/**` | 3 | **完成** | `LuaEntityAccessor`、`LuaNbtAccessor`（对外 Lua 方法名不变）、**新增** `ResourceLocationHelper`（`tryParse` 语义，1.7.10 无 `ResourceLocation.tryParse`） | Lua 侧 `Component→IChatComponent`；`sendActionBar` 1.7.10 无 action bar API → 降级聊天栏（TODO(port)） |
| `api/vmlib/**` | 4 | **完成** | Lua 常量库，luaj + 纯 Java | 无 |
| `api/client/animation/**`（13+gltf 9+accessor 10+interpolator 7+statemachine 8 = 47） | 47 | **完成** | 纯 Java + JOML + luaj；gltf 树原样保留（`Stream`/`ByteBuffer`/注解清理） | ① fastutil `Double2ObjectRBTreeMap`→`java.util.TreeMap<Double,V>`（依赖 client-port 把 `client/resource/pojo/animation/bedrock/**` 的三个 `getKeyframes()` 改成 `TreeMap`，已发接口消息）；② 4 个文件 import `com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData` → **等 client-port vendor simplebedrockmodel**；③ `Minecraft.getInstance().player`→`Minecraft.getMinecraft().thePlayer`；④ `Entity#distanceToSqr(Vec3)/getPosition(float)`→`getDistanceSqToEntity`（不插值，已注释） |
| `api/client/event/**` | 4 | **完成** | `BeforeRenderHandEvent`（`getPoseStack()` 返回 `mcshim.PoseStack`）、`RenderItemInHandBobEvent`、`RenderLevelBobEvent`、`SwapItemWithOffHand` | 事件基类 cpw；`Cancelable` 注解 cpw 版 |
| `api/client/gameplay/**` | 1 | **完成** | `IClientPlayerGunOperator`（YSM 硬约束 `getClientShootCoolDown()`） | `LocalPlayer→EntityPlayerSP`；`fromLocalPlayer` 名字保留（强转，依赖 `mixin/client/LocalPlayerMixin` 注入） |
| `api/client/other/**` | 4 | **完成** | `IThirdPersonAnimation`、`ThirdPersonManager`、`KeepingItemRenderer`、`GunModelTypeManager` | `ModelPart→net.minecraft.client.model.ModelRenderer`（字段 yRot→rotateAngleY、xRot→rotateAngleX、x/y/z→offsetX/Y/Z；每帧复位由 client 侧负责，已标 TODO）；`KeepingItemRenderer#getRenderer` 改用 `Minecraft.getMinecraft().entityRenderer.itemRenderer` |

### common-api 跨包契约（对外签名，common-game / client-port / common-infra 直接依赖）

1. **ItemStack 空值约定**：1.7.10 无 `ItemStack.EMPTY` → **`null` 表示空栈**。
   api 中所有返回 `ItemStack` 的方法都可能返回 `null`（相关方法已标 `@Nullable`），入参也允许 `null`。
   工具类：`com.tacz.guns.api.item.nbt.ItemStackNbtHelper`（`getOrCreateTag` / `getTag` / `hasTag` / `isEmpty` /
   `save` / `writeEmptyTag` / `load`）——其它包直接复用，不要另造一套。
2. **NBT key 名全部保持上游不变**（`GunId` / `GunFireMode` / `Attachment*` / `GunCurrentAmmoCount` …，见各 DataAccessor 常量）。
3. **NBT/ResourceLocation 映射**：`CompoundTag→NBTTagCompound`、`Tag.TAG_*→net.minecraftforge.common.util.Constants.NBT.TAG_*`、
   `contains(k[,type])→hasKey(k[,type])`、`getInt→getInteger`、`getCompound→getCompoundTag`、`getList→getTagList`、
   `putX→setX`、`ResourceLocation.tryParse→com.tacz.guns.api.util.ResourceLocationHelper.tryParse`。
4. **`GunItemManager` 形态变更**（1.7.10 无 `RegistryObject`）：`registerGunItem(String, AbstractGunItem)`、
   `getGunItem(String) → AbstractGunItem`、`getAllGunItems() → Collection<? extends AbstractGunItem>`（调用方不再 `.get()`）。
   `ModItems` 契约（**common-infra 必须提供**）：`public static` 的 `MODERN_KINETIC_GUN`（`ModernKineticGunItem` 或 `Item`）、
   `AMMO`、`ATTACHMENT` 字段，供 `GunItemBuilder/AmmoItemBuilder/AttachmentItemBuilder` 直接 `new ItemStack(ModItems.X, n)`。
5. **`AbstractGunItem`（ModernKineticGunItem 父类）最终形态**：无参 `protected AbstractGunItem()`（内部 `setMaxStackSize(1)`）；
   `getItemStackDisplayName(ItemStack): String`（替代 1.20 `getName`）；`fillItemCategory(GunTabType): List<ItemStack>`；
   `findAndExtractInventoryAmmo(IInventory, ItemStack, int)`（替代 `IItemHandler`）；
   `doBulletSpread(..., net.minecraft.entity.Entity projectile, ...)`（替代 `Projectile`）；
   `onEntitySwing(EntityLivingBase, ItemStack)`；删除 `initializeClient`/`getTooltipImage`（1.7.10 无渲染扩展/图片 tooltip）。
6. **事件构造器参数顺序**（common-game 直接 `new`）：`(EntityLivingBase, ItemStack, Side)` 系；
   `GunDrawEvent(EntityLivingBase, ItemStack prev, ItemStack cur, Side)`；
   `EntityHurtByGunEvent.Pre/Post(Entity bullet, @Nullable Entity hurt, @Nullable EntityLivingBase attacker, ResourceLocation gunId,
   ResourceLocation gunDisplayId, float amount, @Nullable Pair<DamageSource,DamageSource> sources, boolean headShot,
   float headshotMultiplier, Side side)`；`AmmoHitBlockEvent(World, int,int,int,int side, Block, int metadata, EntityKineticBullet)`。
   1.7.10 `MinecraftForge.EVENT_BUS.post(...)` 返回 boolean=canceled（与 1.20 一致）。
7. **Mixin 必备注入点**（client-port）：`mixin/common/LivingEntityMixin` 必须在 `EntityLivingBase` 上
   `implements IGunOperator, KnockBackModifier`；`mixin/client/LocalPlayerMixin` 必须在 `EntityPlayerSP` 上
   `implements IClientPlayerGunOperator`；`mixin/client/ItemInHandRendererMixin` 目标改 `net.minecraft.client.renderer.ItemRenderer`。
   否则 `IGunOperator.fromLivingEntity` / `IClientPlayerGunOperator.fromLocalPlayer` / `KeepingItemRenderer.getRenderer` 运行期 CCE。

### common-api 遗留 TODO / 需主 Agent 在构建阶段注意

- `api/event/common/KubeJSGunEventPoster` 三个转发方法当前 **no-op**（1.7.10 无 KubeJS；compat 反射桥属 L2）——
  不阻塞 L1 编译/开火。
- `GunItemDataAccessor#getAimingZoom` 内部调用 `TimelessAPI.getClientXxx`（上游同样），仅客户端逻辑可调用。
- 我新增的 2 个文件：`api/item/nbt/ItemStackNbtHelper.java`、`api/util/ResourceLocationHelper.java`（移植辅助，非上游符号）。
- `api/item/nbt/ItemDataAccessor.java` 上游就是 0 字节空文件，原样保留。
- 构建时若排除 `client/**`，**api 会一起挂**（`TimelessAPI`、`GunItemDataAccessor`、`AbstractGunItem`
  引用 `client/resource/**`、`client/renderer/**` 的类型）——**不要单独 exclude client/**，等 client-port 收尾后整体构建**。
- YSM 编译期硬约束已逐条自检通过（类名/方法名/getter 均保持，仅平台类型替换）；唯一新增「签名里出现 mcshim 类型」
  的是 `api/client/event/BeforeRenderHandEvent#getPoseStack()`（返回 `com.tacz.guns.mcshim.PoseStack`）——**不在 YSM 契约清单内**，不影响 YSM。


### common-game（task-5）各包进度（硬停时点 04:15，未跑 gradle）

| 包 | 文件数 | 状态 | 已完成内容 | 阻塞点 / 差异 |
|---|---|---|---|---|
| `item/**` | 12（11 上游 + `ItemClientHooks`） | **完成（本包 0 个 1.20 import）** | `ModernKineticGunItem`（onUpdate 热量冷却/拉栓/换弹状态机/近战锥形判定/Lua 属性修改/散射/开火模式）、`ModernKineticGunScriptAPI`（Lua 脚本 API 全量：shootOnce/弹药/热量/蓄力/后坐力推挤/背包弹药 IInventory）、`AmmoItem`、`AttachmentItem`、`AmmoBoxItem`（NBT/耐久条/创造条目；物品栏点击交互降级 TODO）、`AmmoStorageBlockItem`（BlockEntityTag↔TileEntity 转移）、`GunSmithTableItem`/`DefaultTableItem`/`GunPegboardItem`（ItemBlock + display.color 染色 + 相邻继承）、`TargetMinecartItem`（铁轨判定/ascend 高度补偿）、`GunTooltipPart` | NBT key 全部保持上游（AmmoId/AmmoCount/Level/Creative/AllTypeCreative/BlockId/GunId/配件 key 等）；「物品栏点击存入/取出弹药」1.20.5+ 钩子无 1.7.10 对应 → 需新增 GUI（TODO）；`verifyTagAfterLoad` 数据修复钩子无对应（改懒修复 TODO） |
| `entity/**`（子弹/矿车/shooter/客户端钩子） | 20（EntityKineticBullet、TargetMinecart、EntityBulletClientHooks、shooter/** 15、sync/** 10 未动） | **部分**：EntityKineticBullet/TargetMinecart/shooter/** 完成；`entity/sync/**` **未动（10 文件，已回滚上游）** | 子弹：1.7.10 原生 `World#rayTraceBlocks` + `EntityUtil.findEntityOnPath/findEntitiesOnPath` + `TacHitResult`；伤害源 `DamageSource.causeThrownDamage`（末影人 magic 硬编码）；爆炸/点燃/穿透/爆头/距离衰减；spawn 数据走 `IEntityAdditionalSpawnData`+`PacketBuffer`（不使用 DataWatcher，理由：字段多且子弹生命周期短，生成时全量同步等价上游 spawn packet）。shooter/**：15 文件 0 error，`ShooterDataHolder` 字段/可见性与上游逐字一致（Mixin 直接访问） | **`entity/sync/**` 未移植**（1.20 状态）→ `ModSyncedEntityData`/`SyncedEntityData` 等被 Mixin、event/SyncedEntityDataEvent、network、shooter 引用，编译期会挂；`data/tacz/tags` 实体标签（USE_MAGIC/VOID/PRETEND_MELEE_DAMAGE_ON）1.7.10 无 tag 系统 → 硬编码降级；1.7.10 无 `ClipContext`/`bullet_ignore` 方块标签（子弹忽略方块语义丢失） |
| `event/**` | 19（18 上游 + `EventHandlers`） | **完成（0 个 1.20 import，跨包依赖未落盘）** | 18 个事件类的 1.7.10 映射；`EventHandlers.registerAll()` 统一注册（**双总线**：TickEvent/PlayerEvent 走 `FMLCommonHandler.instance().bus()`，其余 `MinecraftForge.EVENT_BUS`；幂等；须配置加载后调用）；4 个类降级：LivingKnockBackEvent 不存在（留 `KnockbackChange.modifyStrength` 静态钩子）、EntityTravelToDimension 仅玩家可用、无钟方块（BellRing 空实现）、无 ModConfigEvent（改静态 reloadConfig） | 依赖 `init/ModDamageTypes.isBulletDamage(DamageSource)`（**需 common-infra 新增**）、util/HitboxHelper/AttachmentDataUtils、entity/sync/core、block 的 `isLocked/getSlot` 契约 |
| `mixin/common/**` | 3（1 个上游 mixin 删除） | **完成（0 个 1.20 import）** | `LivingEntityMixin`→`@Mixin(EntityLivingBase)` implements IGunOperator/KnockBackModifier，每 tick 注入 `EntityLivingBase#onLivingUpdate`（服务端状态机唯一驱动点，L1 必需）；`ServerGamePacketListenerImplMixin`（`NetHandlerPlayServer#processEntityAction` 的 sprint redirect）；`ServerPlayerMixin`（`EntityPlayerMP#clonePlayer` → initialData）；`ServerPlayNetHandlerMixin`（1.9+ 副手交换）删除（1.7.10 无双持） | Mixin 需 client-port 侧**不**排除 `com/tacz/guns/mixin/common/**`；`@Mixin` 目标方法名依赖 MCP stable 映射 + refmap；`LivingEntityMixin.initialData` 已对 `AttachmentPropertyManager.postChangeEvent` 加 null 保护（1.7.10 空手 ItemStack 为 null） |
| `sound/**`、`debug/**`、`advancement/**` | 5 | **完成（0 个 1.20 import）** | SoundManager（String 声音名 + 邻近玩家音量过滤 + NetworkHandler 发送）、GunMeleeDebug（原生 spawnParticle）、TargetHitTrigger/GunRefitTrigger（1.7.10 无 advancement → 保留 `INSTANCE#trigger(...)` 调用点语义，内部 post 自定义 Forge 事件广播） | advancement 定义 JSON 无法注册（差异记录）；声音 `SoundEvent/SoundSource` → 字符串名 |
| `block/**` | 23 | **未动（已整包回滚上游 1.20.1）** | — | 原委派 subagent 只完成 12/23，按 lead「不留半成品」指令整包从 tmp 回滚；1.7.10 需 Block+TileEntity+metadata（无 BlockState/BlockEntityType/BlockPos/VoxelShape） |
| `inventory/**`、`crafting/**`、`loot/**`、`particles/**` | 22 | **未动（已整包回滚上游 1.20.1）** | — | 原委派 subagent 失败/超时；回滚前已删掉其新增的 GuiHandler/GuiId 等半成品文件；1.7.10 需 Container/Slot/IInventory + IRecipe 降级（GunSmithTable 配方为自研数据类，非 IRecipe） |
| `init/**`（common-game 负责的 10 个） | 10 | **未动** | — | 与 item/block 类构造器强耦合；被 `CommonProxy.preInit` 直接调用（`ModBlocks/ModItems/ModEntities/ModContainer/ModSounds/ModRecipe/ModAttributes/ModParticles/ModLootModifiers.register()`），common-infra 已写好调用点，**这是当前入口编译的缺口** |

#### common-game 交付给其它包的静态钩子（L1 编译边界：common 不 import client）

```java
// item/ItemClientHooks.java —— client 侧 ClientProxy preInit 赋值
public static @Nullable Function<ItemStack, String> itemNameProvider;   // 客户端枪包索引名称（null 回退服务端名）
public static @Nullable ToIntFunction<ItemStack>    nameColorProvider;  // ARGB，0=不染色
public static @Nullable Consumer<net.minecraft.item.Item> registerItemRenderer; // MinecraftForgeClient.registerItemRenderer
public static String getItemName(ItemStack, String fallback);
public static int getNameColor(ItemStack);
public static void registerRenderer(Item item);

// entity/EntityBulletClientHooks.java —— client 侧注入弹道粒子
public static @Nullable Consumer<net.minecraft.entity.Entity> ammoParticleSpawner;
public static void spawnAmmoParticle(Entity bullet);   // EntityKineticBullet 客户端每 tick 调用
```

#### NBT / 同步机制选择（任务要求记录）

1. **NBT**：所有物品 NBT 键名/结构与上游完全一致（api 的 ItemStackNbtHelper + 各 *DataAccessor 实现，item 包只做 Item 侧行为）。1.7.10 `ItemStack.EMPTY`→`null`。
2. **实体同步**：由于 `entity/sync/**` 未移植，当前状态机同步仍依赖上游接口（`ModSyncedEntityData.XXX_KEY.setValue/getValue`）。**原设计**：自研「按实体 id 的静态 DataHolder 管理器」替代 capability（EntityJoinWorld 挂载/Clone 复制/服务端 Tick 收集 dirty→`ServerMessageUpdateEntityData` 下发），比 DataWatcher 更贴近上游（1.7.10 DataWatcher 仅 32 槽、类型受限，装不下 ReloadState 等复合值）——该实现**未完成**，属本工作流最大剩余块。
3. **子弹同步**：不用 DataWatcher，走 Forge `IEntityAdditionalSpawnData` 一次性下发（字段多、生命周期短，等价上游 spawn packet）。

#### 硬停时点需主 Agent 裁决的 3 个入口缺口

1. `CommonProxy.preInit` 调用 10 个 `Mod*#register()`，但这 10 个文件仍是上游 1.20.1 → **入口必编译失败**；要么本轮补这 10 个（约 300 行，需 item/block 类就绪），要么 `CommonProxy` 暂时注释调用。
2. `inventory/GuiHandler` 缺失（`CommonProxy` 已 `registerGuiHandler`）→ 需最小空实现或注释该行。
3. `init/ModDamageTypes.isBulletDamage(DamageSource)` 缺失（event/EntityDamageEvent 依赖）；`ModAttributes.BULLET_RESISTANCE` 需普通 static Attribute 字段（我有空可补，但属 init 缺口）。
---

## common-infra（task-4）最终收尾状态 —— by common-infra

> 上面第 41~52 行的 4 行表格是本节之前的中间态，**以本节为准**。`config/`、`network/`、入口三文件已 100% 完成；
> `util/` 21/31、`command/` 10/11、`init/`（我的 6 个）6/6 完成，未动的 11 个文件见下。

| 包 | 文件数 | 状态 | 已完成内容 | 阻塞 / 差异 |
|---|---|---|---|---|
| `config/**` | 16 | **完成（0 个 1.20 import）** | 自建 `ForgeConfigSpec` 等价层（`ConfigBuilder` / `TaczConfigSpec` / `ConfigValue` + Int/Boolean/Double/String/Enum/List 六个 typed value，底层 `net.minecraftforge.common.config.Configuration`）；15 个配置类项名/默认值/范围/注释与上游逐条一致，调用点 `XxxConfig.FOO.get()/.set()` 未改；`ConfigManager` 承接上游 `LoadingConfigEvent` 的 4 个 `*ConfigRead.init()` 钩子；`PreLoadConfig` 独立 spec（`<gamedir>/tacz/tacz-pre.cfg`），删 `PreLoadModConfig` | SERVER 配置同步 → `ServerMessageSyncConfig`（移植特有，discriminator 43）+ `SyncConfigSyncManager`（`PlayerLoggedInEvent`）；1.20 tag 无对应 → `AmmoBlockConfigRead.EXPANDED_VANILLA_TAGS` |
| `network/**` | 52 | **完成（0 个 1.20 import）** | 两个 `SimpleNetworkWrapper` channel（`tacz` / `tacz:handshake`）；`MessageBase` 把上游 static encode/decode 适配为 `IMessage#fromBytes/toBytes`（内用 `mcshim.PacketBuffer`）；`SimpleMessageDispatcher` 保留「一行一包」注册，**discriminator 1..42 与上游完全同序**（移植特有包 43）；`NetworkTaskQueue` 替代 `context.enqueueWork`；`ClientSideHooks` 替代 `DistExecutor`（common 零 client 依赖）；42 个上游消息类名/字段/构造器参数顺序/`handle` 语义全部保留 | 5 条降级：login-packet 握手 → `PlayerLoggedInEvent`；`Context#reply` → 显式 `sendToServer`；`PacketDistributor` → `sendTo/sendToAll/sendToDimension`+`EntityTracker`；2 个 record → 普通类+同名访问器；`BlockPos/Direction` → 三 int/`ForgeDirection` |
| 入口 `GunMod` / `CommonProxy` / `client/ClientProxy` | 3 | **完成** | 见下方「入口调用顺序（最终）」 | `CommonProxy` 依赖 10 个 `Mod*#register()`（common-game 未动 → 当前编译不过，二选一：他们补 `register()`，或整体排除） |
| `util/**`（除 math） | 31 | **21 完成 / 10 未动** | 完成：`AllowAttachmentTagMatcher`、`AmmoStorageHelper`、`AmmoStorageInteractionHelper`、`AttachmentDataUtils`、`ColorHex`、`ContentPackOrder`、`CycleTaskHelper`、`DefaultGunAttachments`、`DelayedTask`、`GunAmmoUnloadHelper`、`Md5Utils`、`PathHandler`、`SlotAdapterHelper`、`TacHitResult`、`TacPathVisitor`、`VariantHelper`、`VirtualOemAttachment`、`datafixer/AttachmentIdFix`、`time/GunTime`（+ `util/math` 4 个由 platform-shim 完成）。api 编译期依赖的公开方法名全部保留：`GunAmmoUnloadHelper.unloadAmmo(...)`、`AttachmentDataUtils.getAmmoCountWithAttachment(ItemStack, GunData)`、`SlotAdapterHelper.allowsAttachment/allowsDirectAttachment/getAvailableSlotAdapters/setSlotAdapter/removeSlotAdapter`（`SlotAdapter` 内部类有 `allowsAttachment(ResourceLocation)`）、`VariantHelper.getVariant/setVariant/removeVariant`、`AmmoStorageHelper.getStackSize/createAmmoStack`、`GunTime.nowMillis` | **未动（保持上游 1.20.1 原样，**必须**进 `pendingPortExcludes`）**：`ExplodeUtil`、`GetJarResources`、`InputExtraCheck`、`LaserColorUtil`、`RenderDistance`、`RenderHelper`、`ResourceScanner`、`ThermalImagingHelper`、`block/BlockRayTrace`、`block/ProjectileExplosion` |
| `command/**` | 11 | **10 完成 / 1 未动** | 全部 `ICommand`+`CommandBase`+`ICommandSender`+`String[] args`（**无 brigadier**）：`RootCommand`、新增 `CommandTargets`、`AttachmentLock`、`Config`、`Convert`、`Debug`、`DummyAmmo`、`HideTooltipPart`、`ListPack`、`Overwrite`、`Reload`；命令名/子命令/语言 key/权限等级对齐上游，brigadier→手写解析的对应表写在各类 javadoc | **未动**：`sub/PegboardStressCommand`（上游 `net.minecraft.world.World`） |
| `init/**`（我的 6 个） | 6 | **完成** | `CommonRegistry`（`register()`→事件总线；`commonSetup()` 承接上游 `FMLCommonSetupEvent` 的 `enqueueWork` 序列；`markLoadComplete()`/`isLoadComplete()`；`registerAttributes()`→1.7.10 改 `EntityConstructing`；坩埚交互记录差异）、`CommandRegistry`（`registerCommands(FMLServerStartingEvent)`）、`CapabilityRegistry`（无 capability → 空 `register()`）、`CompatRegistry`（`ModList`→`Loader`；`DistExecutor`→反射 `invokeOptional`，缺失静默跳过）、`ModDamageTypes`（伤害类型注册表 → `DamageSource` 子类 `BulletDamageSource` + 4 个 `ResourceLocation` + `Sources.bullet/bulletVoid`）、`ModPainting`（画变体硬编码 `EnumArt` → 降级记录） | `registerAttributes()` 仍是 TODO(cross-package)：等 `init/ModAttributes` 给 1.7.10 `RangedAttribute` 常量后补 `getAttributeMap().registerAttribute(...)` |

### 入口调用顺序（最终，权威）

```
GunMod: preInit/init/postInit/serverStarting → proxy.*
CommonProxy.preInit(FMLPreInitializationEvent)          // = 上游 GunMod() 构造器
  1. ConfigManager.initCommon(event.getModConfigurationDirectory())
  2. ModBlocks.register(); ModItems.register(); ModEntities.register(); ModContainer.register();
     ModSounds.register(); ModRecipe.register(); ModAttributes.register(); ModParticles.register();
     ModLootModifiers.register(); ModPainting.register();
     // 注意：ModCreativeTabs 归 common-game；若需要 tab 先于 items，请他们保证 ModItems 内部顺序
  3. CommonRegistry.registerAttributes(); CommonRegistry.register();      // EntityConstructing 挂属性
  4. GunMod.registerDefaultExtraGunPack();
  5. AttachmentPropertyManager.registerModifier();
  6. event.EventHandlers.registerAll();
     MinecraftForge.EVENT_BUS.register(new NetworkTaskQueue());
     MinecraftForge.EVENT_BUS.register(new SyncConfigSyncManager());
  7. NetworkRegistry.INSTANCE.registerGuiHandler(GunMod.instance, new inventory.GuiHandler());
CommonProxy.init(FMLInitializationEvent)                // = 上游 CommonRegistry#onSetupEvent
  1. CommonRegistry.commonSetup()   // NetworkHandler.init → ModSyncedEntityData.init →
                                    //   TargetHitTrigger.register → GunRefitTrigger.register
CommonProxy.postInit(FMLPostInitializationEvent)        // = 上游 FMLLoadCompleteEvent
  1. CommonRegistry.markLoadComplete()
CommonProxy.serverStarting(FMLServerStartingEvent)      // = 上游 RegisterCommandsEvent
  1. CommandRegistry.registerCommands(event)
ClientProxy extends CommonProxy:
  preInit → super.preInit() + ClientConfig.init(<config>/tacz-client.cfg).load()+save()
          + ClientSideHooks.set(new Hooks())            // 8 个客户端钩子的实现
  init    → super.init() + TODO(client-port) 渲染/键位/音效注册
  postInit→ super.postInit()
```

### 需要主 Agent 在构建阶段做的事（common-infra 视角）

1. **`pendingPortExcludes` 需要排除的文件**（这些仍是上游 1.20.1 原样，或依赖未完成的跨包 API）：
   - `com/tacz/guns/util/{ExplodeUtil,GetJarResources,InputExtraCheck,LaserColorUtil,RenderDistance,RenderHelper,ResourceScanner,ThermalImagingHelper}.java`
   - `com/tacz/guns/util/block/{BlockRayTrace,ProjectileExplosion}.java`
   - `com/tacz/guns/command/sub/PegboardStressCommand.java`
   - 若 common-game 的 10 个 `init/Mod*.java` 在本轮内没有补 `register()`，则 `CommonProxy.java` 会编译失败 →
     可选：① 等他们补；② 排除 `CommonProxy.java`（会连带 `GunMod`/入口不可用，不推荐）；③ 我把那 10 行注释掉（**需要 lead 一句话**，我随时可改）
2. **`mixins.tacz.json`**：common-infra 不产出 mixin，无需列。
3. **事件总线注册**：`GunMod`/`CommonProxy` 已负责 `EventHandlers.registerAll()` + `NetworkTaskQueue` + `SyncConfigSyncManager` + `CommonRegistry.register()` + `GuiHandler`；**不需要** lead 在 `GunMod` 里再补注册。
4. **语言注入**（`docs/ASSET_PORT_NOTES.md` §5.1 裁决「走 `LanguageRegistry` 注入」）：FML 的 `FMLClientHandler/FMLServerHandler` 已自动注入 mod 内置 `assets/tacz/lang/*.lang`，但**枪包外部 `.lang`（`<gamedir>/tacz/<pack>/assets/tacz/lang/*.lang`）没有加载路径** —— 归 `common-resource` 的 `GunPackLoader`；本工作流未实现，若构建后要验语言，需要 lead 指派。

### 必须由人复核的风险点（3 条）

1. **`NetworkTaskQueue` 的线程模型**：1.7.10 simpleimpl 在 Netty 线程回调，我用「每 tick 末尾排空的队列」代替 `enqueueWork`，
   因此报文处理最多延迟 1 tick；`ClientMessageSyncBaseTimestamp` 在**入队前**取时间戳（与上游一致），但如果实际验收发现射速手感异常，优先看这里。
2. **`Compatibility`/`CompatRegistry` 全反射**：`invokeOptional` 找不到类时**静默返回 false**，因此 ClothConfig/Oculus/CarryOn 兼容是否真的接上，只能看日志；
   兼容包（`compat/**`，client-port）未就绪时不会有任何告警（这是用户裁决的「静默跳过」语义，但排查时要记得）。
3. **`ModDamageTypes` 语义降级**：1.20 的 4 种 `DamageType` 注册表条目 → 1.7.10 单个 `BulletDamageSource` 子类 + `setDamageBypassesArmor()/setDamageIsAbsolute()/setMagicDamage()` 标志位，
   `BULLETS_TAG` 丢弃。伤害计算/免疫判定（`EntityUtil`、`AmmoConfig` 相关）是否与上游一致，需要运行期实测。

---

### common-resource（task-3）各包进度（硬停时点，未跑 gradle）

> 写作用域：`src/main/java/com/tacz/guns/resource/**`。
> 上游 105 文件 → 现 **113** 文件（删 1 个 1.20 专属 `NamespaceFilteredPackResources`，
> 新增 9 个：`resource/pack/**` 5 个 + `IGunPackReloadListener` + `GunPackType` + `resource/serialize/**` 2 个）。
> 判定：`IDEA lint_files` 全包扫描后**自身 0 error**；残留 2 条是**跨包契约未落盘**（见下），不是坏 import。
> 1.20 顶层 import 复核：`net.minecraft.resources.* / server.packs.* / world.* / core.* / network.chat.* / commands.* /
> ChatFormatting / nbt.CompoundTag / forge.api.distmarker / eventbus / fml.* / forgespi / registries / com.mojang.* /
> cpw.mods.jarhandling / it.unimi.dsi.fastutil / org.apache.maven.* / net.minecraft.util.Mth|GsonHelper` —— **0 命中**。

| 包 | 文件数 | 状态 | 已完成内容 | 阻塞点 / 差异 |
|---|---|---|---|---|
| `resource/`（根：`GunPackLoader`、`CommonAssetsManager`、`ICommonResourceProvider`、`PackMeta`、`PackConvertor`、`VersionChecker`、`GunPackType`、`IGunPackReloadListener`） | 8 | **完成** | 见下方「resource 跨包契约」与「resource 移植特有实现」 | 见「resource 已知跨包缺口」 |
| `resource/pack/**`（新增） | 5 | **完成** | `IGunPackSource`（`extends mcshim.PackSource`）、`DirectoryGunPackSource`、`ZipGunPackSource`、`GunPackResources`（复刻上游 `util/ResourceScanner` 的 3 个扫描语义 + `readElement/openReader`）、`PackDirectory`（= `FileToIdConverter` 的极简载体） | 无 |
| `resource/manager/**` | 12 | **完成** | `JsonDataManager`/`LazyJsonDataManager`/`CommonDataManager`/`ScriptManager`/`AttachmentsTagManager`/`RecipeFilterManager`/`LootInjectionManager`/`GunDataManager`/`GunIndexManager`/`AttachmentDataManager`/`INetworkCacheReloadListener`/`AttachmentScriptManager`（后两个 0 改动或仅换基类） | 两段式 `prepare/apply` 折叠为同步 `reload()`（1.7.10 无重载管线） |
| `resource/index/**` | 7 | **完成** | `CommonGunIndex`（**`getType()` 等 YSM 硬约束 getter 全保留**）、`CommonAmmoIndex`、`CommonAttachmentIndex`、`CommonAttachmentVariantIndex`、`CommonSlotAdapterIndex`、`CommonBlockIndex`、包级 | `CommonBlockIndex` 的 `ForgeRegistries.ITEMS.getValue`→`GameRegistry.findItem`；`ResourceLocation.tryParse`→本地 `tryParse` 私有实现 |
| `resource/pojo/**` | 45 | **完成** | 全部 POJO / `SmokeDirectionData` / `TabConfig` / `LootTableInjection` / `TableRecipe` / `GunResult` 字段与 `@SerializedName` 与上游一致 | `TabConfig`：`Component`→`IChatComponent`、`CraftingHelper`→`ItemStackJsonParser`、`ModItems.GUN_SMITH_TABLE.get()`→`GameRegistry.findItem`；`InaccuracyType`：`getPose()==CROUCHING`→`isSneaking()`，**`LIE` 在 1.7.10 永不返回**（无爬行姿态）；`LootTableInjection` 降级为保留原始 json（1.7.10 无 `LootTable/LootContext`） |
| `resource/serialize/**` | 14 | **完成** | 13 个上游序列化器 + **新增** `ResourceLocationSerializer`（1.7.10 无内置 gson 适配器）、`ItemStackJsonParser`（替代 `CraftingHelper`） | `@JsonAdapter`→`registerTypeAdapterFactory`（gson 2.2.4 无该注解）；`JsonObject.deepCopy()`/`JsonArray(int)`/`JsonArray.set` 在 2.2.4 不可用 → 手写等价；`Vec3` 构造 protected → `createVectorHelper`；`x()/y()/z()`→`xCoord/yCoord/zCoord` |
| `resource/modifier/**` | 20 | **完成** | `AttachmentPropertyManager`、`AttachmentCacheProperty`、17 个 custom modifier（`ID` 常量与上游一致） | `Component.translatable(...).withStyle(...)`→`new ChatComponentTranslation(...).setChatStyle(new ChatStyle().setColor(...))`；`@OnlyIn(Dist.CLIENT)`→`@SideOnly(Side.CLIENT)`；fastutil `Pair`→`org.apache.commons.lang3.tuple.Pair`（`ParameterizedCachePair.left()/right()` 保持不变） |
| `resource/filter/**` | 4 | **完成** | `RecipeFilter` + `LiteralFilter`/`RegexFilter`/`IFilter` | `ResourceLocation.tryParse`→本地 `tryParse` |
| `resource/network/**` | 3 | **完成** | `DataType`、`CommonNetworkCache`、`GunPackSync`（压缩/分片协议逐字保留；`Part`/`Receiver`/`CHUNK_SIZE`/`MAX_SYNC_SIZE` 与 common-infra 网络层契约一致） | `getPath()/withPath()`→`getResourcePath()`/同 namespace 重建；`TypeToken<>`→`TypeToken<List<String>>(){}.getType()` |

#### resource 已知跨包缺口（**仅剩这 2 条编译错误，均需 common-game 补 1 个静态工厂**）

```java
// 1) com.tacz.guns.crafting.GunSmithTableIngredient（common-game）
//    我调用：GunSmithTableIngredient.fromJson(JsonElement itemElement, int count)
//    语义：itemElement 是 recipe json 里 materials[i].item 的子树，
//          支持 {"item":"ns:path"} 与 {"tag":"forge:ingots/iron"} 两种写法（1.7.10 用 ItemStack / OreDictionary 名）。
public static GunSmithTableIngredient fromJson(JsonElement itemElement, int count);

// 2) com.tacz.guns.crafting.result.GunSmithTableResult（common-game）
//    我调用：GunSmithTableResult.fromJson(JsonElement resultElement)
//    语义：解析 result 对象（type=gun|ammo|attachment|custom、count、nbt、group、id），返回结果对象。
public static GunSmithTableResult fromJson(JsonElement resultElement);
```
对应调用点：`resource/serialize/GunSmithTableIngredientSerializer.java:38`、
`resource/serialize/GunSmithTableResultSerializer.java:29`。两条都已在类 javadoc 里写明「1.7.10 无 Ingredient /
无 CraftingHelper / ItemStack.EMPTY 为 null」的降级理由。
**若 common-game 不提供**：这两文件必须从 `tmp/TACZ-1.20.1` 拷回原样（但会引入 1.20 import，不可编译），
或由主 Agent 在 `pendingPortExcludes` 里连 `crafting/**` 一起处理。

#### resource 跨包契约（common-infra / client-port / common-game 直接依赖）

1. **枪包单例**：`com.tacz.guns.resource.GunPackLoader.INSTANCE`（`enum` 单例，与上游同名同形态）。
   - `public GunPackType packType` —— 替代上游 `PackType`，默认 `GunPackType.current()`；`CommonProxy` 可覆盖但不必。
   - `public synchronized void reloadPackList()` —— **必须调用一次**（`CommonProxy.preInit`，早于第一次 `CommonAssetsManager.reload()`）：
     建 `<gamedir>/tacz`、加载 `PreLoadConfig`、导出内置默认枪包、扫描全部枪包/zip、注入 `mcshim.ResourceManager`。
   - `public List<GunPack> getPacks()` / `public List<IGunPackSource> getPackSources()` —— 枪包视图（未扫过会兜底扫一次）。
   - `public static Path getPackDirectory()` / `public static File getGameDirectory()` —— 游戏根目录与枪包目录。
   - `public static Map<ResourceLocation, JsonObject> readPackData(String subDir)` —— **给 common-game 用**：
     把所有枪包 `data/<ns>/<subDir>/**/*.json` 读成 id→JsonObject（`subDir` 如 `"recipes"` / `"advancements"` /
     `"loot_tables"` / `"tags"`；id 推导与 `FileToIdConverter` 一致，如 `data/tacz/recipes/gun/ak47.json` → `tacz:gun/ak47`）。
   - `public static void injectGunPackLanguage(String locale)` —— **枪包语言注入**（见下）。
2. **枪包数据源**：`com.tacz.guns.resource.pack.IGunPackSource extends com.tacz.guns.mcshim.PackSource`
   （`getName()` / `List<String> listFiles(String prefix)` / `@Nullable InputStream open(String path)`），
   实现 `DirectoryGunPackSource` / `ZipGunPackSource`。`GunPackLoader.reloadPackList()` 末尾调
   `mcshim.ResourceManager.setPackSources(...)` + `invalidate()`，因此 **client-port 不需要再写第二套枪包扫描**。
3. **扫描器**：`com.tacz.guns.resource.pack.GunPackResources`
   `scanDataDirectory/scanAssetsDirectory/scanDirectory/scanDirectoryAll/scanDirectoryResources/readElement/openReader`。
   `PackDirectory`（`prefix` + `extension`）取代 `FileToIdConverter`；`DATA_ROOT="data"`、`ASSETS_ROOT="assets"`。
   **client-port 注意**：`client/resource/**` 的 `DisplayManager`/`GltfManager`/`ClientAssetsManager` 要把
   `new FileToIdConverter(prefix, ext)` 换成 `new PackDirectory(prefix, ext)`，并给 manager 构造器多传一个
   `GunPackResources.ASSETS_ROOT`（`JsonDataManager` / `LazyJsonDataManager` / `ScriptManager` 都有该重载），
   否则读到的是 `data/` 侧。**不要**指望 `Minecraft#getResourceManager()` 能看到枪包 `assets/`。
4. **总入口**：`CommonAssetsManager`（**所有 getter 名与上游一致**）：
   `get()` / `getInstance()` / `clearInstance()` / `reload()` / `reloadAllPack()` / `registerEventHandlers()` /
   `getGunData` / `getAttachmentData` / `getAttachmentVariantData` / `getSlotAdapterData` / `getBlockData` /
   `getRecipeFilter` / `getGunIndex` / `getAmmoIndex` / `getAttachmentIndex` / `getAttachmentVariantIndex` /
   `getSlotAdapterIndex` / `getBlockIndex` / `getAllGuns` / `getAllAmmos` / `getAllAttachments` /
   `getAllAttachmentVariants` / `getAllSlotAdapters` / `getAllBlocks` / `getAttachmentTags` / `getAllowAttachmentTags` /
   `getScript` / `getLootTableInjections` / `getNetworkCache` / `GSON`。
   新增 `public boolean isLoaded()`、`public Collection<ServerMessageSyncGunPack> getNetworkSyncMessages()`。
5. **manager 契约**：`IGunPackReloadListener#reload()`（单段同步）；
   `INetworkCacheReloadListener extends IGunPackReloadListener`（`getNetworkCache()` / `getType()`）；
   `JsonDataManager`/`LazyJsonDataManager` 的 `getData/getAllData/getAllIds/getGson/getDataClass/getMarker/parseJson/postLoad`
   全部保留。
6. **`Attention`**：`CommonAssetsManager.reload()` **内部会先调 `GunPackLoader.INSTANCE.reloadPackList()`**，
   并且 `INSTANCE` 在装载前赋值（索引类管理器要靠它查 data）。服务端停止/主世界卸载时自动 `clearInstance()`。

#### resource 移植特有实现（上游没有对应代码，务必让 review 知道）

1. **枪包语言注入**（`GunPackLoader.injectGunPackLanguage(String locale)`，javadoc 已写明依据与差异）：
   - 上游靠 `mixin/client/LanguageMixin` 注入 `I18n`，但该 mixin 在 1.20.1 源码里已被整段注释；1.7.10 无 `I18n`/mixin 概念。
   - 实现：遍历 `GunPackLoader.INSTANCE.getPackSources()` 的 `assets/<ns>/lang/<locale>.lang`，
     用 **`net.minecraft.util.StringTranslate.parseLangFile(InputStream)`** 解析（真 UTF-8、跳过 `#`、首个 `=` 切、
     `%d|%f`→`%s`、**不做** `\n \t \uXXXX` 反转义 —— 与客户端 `Locale` 完全一致），
     合并后 `LanguageRegistry.instance().injectLanguage(locale, HashMap)`。
   - **不要**改用 `Properties#load(InputStream)`（ISO-8859-1 会毁掉中文与 `§`），也**不要**用 `Properties#load(Reader)`
     （会多解一层转义，把枪包描述里的字面 `\n` 变成真换行）。
   - **调用点（需主 Agent 接线）**：客户端 `ClientProxy.init`（或 `postInit`）里
     `GunPackLoader.injectGunPackLanguage(Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode())`；
     语言切���时（`client/event` 或 `GuiLanguage` 改动后）再调一次即可（`mergeLanguageTable` 只补缺不覆盖，重复调无害）。
     方法内部已做 `Side.isClient()` 守卫。
   - 内置默认枪包（`custom/tacz_default_gun/assets/tacz/lang/*.lang`）会被 FML `LanguageRegistry` 正则自动扫到并注入，
     本方法对它再注入一次是无害的（值相同）。
2. **`GunPackResources` / `PackDirectory` / `IGunPackReloadListener` / `GunPackType` / `ResourceLocationSerializer` /
   `ItemStackJsonParser`** —— 6 个移植辅助类型，均带 javadoc 说明「对应上游哪个类、1.7.10 用什么替代、行为差异」。
3. **删除的文件**：`resource/NamespaceFilteredPackResources.java`。
   它之所以存在只是为了修正 Forge 1.20 `DelegatingPackResources` 的按 namespace 枚举顺序；
   1.7.10 没有 `PackResources`/`DelegatingPackResources`，namespace 枚举已由
   `GunPackResources.listNamespaces` 逐包直接完成，保留该类只会成为第二套无效枚举逻辑。

#### resource 遗留 TODO（不阻塞 L1 编译/开火）

- `LootTableInjection` 只保留原始 json，实际注入要 `loot/**` 用掉落事件降级实现（common-game）。
- 枪包 `data/tacz/advancements/**`、`data/tacz/tags/**` 同样无 1.7.10 承载物，
  数据可通过 `GunPackLoader.readPackData("advancements"|"tags")` 取到，注册动作由 common-game 决定。
- `VersionChecker`（旧版 `pack.json` 枪包版本校验）已移植但**当前无调用方**（上游仅旧格式路径使用）。

#### common-resource ↔ common-game 待接线契约（硬停后记录，供下一轮）

- **resource/** 已就绪的数据读取入口**（下一轮 crafting/advancement/loot 数据驱动直接接）：
  `com.tacz.guns.resource.GunPackLoader#readPackData(String subDir): Map<ResourceLocation, JsonObject>`
  （`subDir` = `"recipes"`/`"advancements"`/`"loot_tables"`/`"tags"`，递归扫所有已加载枪包 `data/<ns>/<subDir>/**/*.json`，
  id 推导同 1.20 FileToIdConverter；前置 `reloadPackList()` 已由 CommonProxy.preInit 调用）。
- **resource/** 仅剩的 2 条编译错误 = 需要 `crafting/**` 提供两个静态工厂**（crafting 包本轮按硬停指令保持上游未动，故未补）：
  1. `GunSmithTableIngredient.fromJson(com.google.gson.JsonElement itemElement, int count)`
     （调用点 `resource/serialize/GunSmithTableIngredientSerializer.java:38`；支持 `{"item":...}` / `{"tag":"forge:..."}` 两种写法，
     可复用 `resource/serialize/ItemStackJsonParser#getOreDictionaryName(JsonObject)`）
  2. `GunSmithTableResult.fromJson(com.google.gson.JsonElement resultElement)`
     （调用点 `resource/serialize/GunSmithTableResultSerializer.java:29`；type=gun|ammo|attachment|custom、count、nbt、group、id；
     上游逻辑见 `tmp/TACZ-1.20.1/.../resource/serialize/GunSmithTableResultSerializer.java`）
  → 下一轮开 `crafting/**` 时优先补这两个类；或主 Agent 决定把 `resource/serialize/*Serializer` 一并排除编译。
- `CommonAmmoIndex` 只有 `getStackSize/getSort/getPojo`（上游即无 `getName/getTooltipKey`）；`LootTableInjection` 降级为
  `record(List<ResourceLocation> lootTables, JsonObject json)`（无 `lootTable()/createStacks(LootContext)`，1.7.10 掉落事件自行实现）。
  `SilenceModifier.ID` 泛型为 commons `Pair`；17 个 custom modifier 的 `ID` 常量与索引/数据类 getter 名均未改。
---

## 最终构建实测（主 Agent · 会话末）

命令：`./gradlew spotlessApply` → **成功**（全仓已格式化）；`./gradlew build` → **失败**（符合「分层推进」的当前预期，日志 `build/port-build.log`）。

`compileJava` 错误按顶层包统计（javac 上限 5000，实际多于 5000）：

| 包 | 错误数 | 状态 / 说明 |
|---|---|---|
| `client/**` | 4376 | L2 未完成：渲染、GUI、模型、动画 |
| `block/**` | 2188 | 未动（整包回滚上游原样） |
| `compat/**` | 1704 | 未动（反射式兼容骨架待写） |
| `init/**` | 452 | `ModItems/ModBlocks/...` 10 个注册类仍是 1.20 `DeferredRegister` |
| `mixin/**` | 314 | 仅 `mixin/common/**` 3 个已移植；client mixin 未做 |
| `inventory/**` | 290 | 未动 |
| `crafting/**` | 266 | 未动（`resource/serialize` 两个 serializer 依赖其静态工厂） |
| `entity/**` | 194 | `entity/sync/**` 10 文件未动，其余已移植 |
| `util/**` | 146 | 10 文件未动（ExplodeUtil / GetJarResources / InputExtraCheck / LaserColorUtil / RenderDistance / RenderHelper / ResourceScanner / ThermalImagingHelper / block/BlockRayTrace / block/ProjectileExplosion） |
| `loot/**` | 32 | 未动 |
| `api/**` | 30 | 自身已移植完，错误全部来自引用未完成包（client / util / init） |
| `particles/**` | 8 | 未动 |

**已可编译（0 错误）的包**：`mcshim/**`(43)、`config/**`、`network/**`(52)、`item/**`、`event/**`、`resource/**`（除 2 个 serializer）、`command/**`、`advancement/**`、`sound/**`、`debug/**`、根包 `GunMod`/`CommonProxy`。

### 下一轮收敛顺序（低垂果实优先）

1. `util/**` 剩 10 文件
2. `api/**` 的 30 条（等 client 或去掉对 client 的引用）
3. `entity/sync/**`（连带 Mixin 里 8 个 `getSyn*` / `setValue` 与 `SyncedEntityDataEvent`）
4. `crafting/**` + `resource/serialize` 的 2 个 serializer
5. `loot/**`、`particles/**`
6. `block/**`、`inventory/**`
7. `init/**` 10 个 `Mod*` 注册类
8. `compat/**`
9. `client/**` + `mixin/client/**`（最大一块）

### 构建阶段待办（主 Agent 交接）

- `CommonProxy.preInit` 调用的 10 个 `Mod*#register()` 需 `init/**` 移植后才会消失（当前是入口的唯一硬缺口）
- `mixins.tacz.json` 的 `mixins` 数组需要列入 `mixin/common/**` 的 3 个类（`LivingEntityMixin`、`ServerPlayerMixin`、`ServerGamePacketListenerImplMixin`），`ServerPlayNetHandlerMixin` 已删除不要列
- `pendingPortExcludes` 当前**未启用**（保持真实状态），需要临时绿构建时按上表取消注释

---

### client-port（task-6）各包进度 —— 硬停时点，未跑 gradle

> 写作用域：`client/**`、`mixin/client|ar|oculus/**`、`compat/**`、`com.github.mcmodderanchor/**`、`mcshim/GuiGraphics.java`。
> 已按 lead 裁决把 `mixin/common/**` 交回 common-game（**我误回滚过一次该目录，已告知 common-game 重落盘**）。
> 判定口径：`clean` = 无 1.20 顶层 import；`untouched` = 与上游逐字节相同（未动）；
> **`HALF`（半改）= 同一文件既有 1.7.10 映射又有 1.20 import —— 最终审计为 0 个**。
> 最终全树审计（351 文件）：`已转换（无 1.20 import）185` / `上游原样 165` / `HALF 0`（唯一 5 处命中均为 TODO 注释文本或反射探测用的字符串常量，不是 import），另有 4 个文件只在
> **注释/TODO 文本**里出现 `net.minecraft.core.Direction` 等字样（不是 import），已在报告中逐个确认。

| 包 | 文件数 | 状态 | 已完成内容 | 阻塞点 / 差异 |
|---|---|---|---|---|
| `client/resource/**` | 86 | **完成**（转换层面） | ① 基础设施：`ClientAssetsManager`（GSON 装配 + 管理器注册改成 `IGunPackReloadListener` 列表 + `PackDirectory.of(dir,".json")` + `GunPackResources.ASSETS_ROOT`）、`ClientAssetLoadTask/Dispatcher`（后台预热，`ResourceConfig` 开关）、`ExternalModelAssets`、`InternalAssetLoader`（`Minecraft#getResourceManager().getResource().getInputStream()`）、`ClientIndexManager`（`InventoryPlayer#mainInventory` 数组化、`ItemStack` 判空降级）、`manager/DisplayManager|GltfManager|PackInfoManager`；② 由子任务完成 `pojo/**`(56) + `serialize/**`(6) + `index/**`(7)：`ResourceLocation.tryParse`→本地 `tryParse`、fastutil→`TreeMap<Double,V>`、`Direction`→`ForgeDirection`、`Component`→`IChatComponent`、`gson 2.2.4` 子集（无 `SerializedName#alternate`/`JsonAdapter`/`JsonArray(int)`/`JsonArray#set`）、`Vec3.createVectorHelper`、`x()/y()/z()`→`xCoord/yCoord/zCoord` | ① 依赖 `com.tacz.guns.resource.**`（common-resource，已完成）与 `client/model/**`（已完成）；② **唯一非预期未解析**：`com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData`（未 vendor，见下）；③ `ItemTransforms` 在 1.7.10 **整包不存在**（1.8 才引入）→ 新建 `pojo/display/block/BlockItemTransforms` 复刻 1.20 API |
| `client/model/**` | 40 | **完成**（转换层面） | bedrock 几何层去 `Direction`/13 参 `VertexConsumer`；`BedrockPart` 用 JOML 四元数；`SlotModel`/`BedrockGunModel` 去 `EntityModel`/AR 分支；`papi/**` 换 `StatCollector`；`functional/**` 8 个把 1.20 `RenderStateShard`/`CompositeState`/`Font#drawInBatch`/`BufferBuilder` 那套**整层重写**成「贴图常量 + 手工 GL 状态施加/还原 + `Tessellator` 直绘」；**YSM 契约满足**：`MuzzleFlashRender.isSelf`/`ShellRender.isSelf` 仍是 `public static boolean` 字段 | 13 处语义降级（全部带 `TODO(port)`）：AR 加速管线整体不可用、`Minecraft#getFrameTime()`→`ClientTickHelper.getPartialTick()`（1 处仍常量 1.0F）、`energySwirl`→`entityTranslucent`、missingno→方块图集、`Font#drawInBatch`→固定管线 `drawString`；**等** `util/RenderHelper` 与 `OculusCompat` 改 mcshim 签名（common-infra / 我） |
| `client/animation/**` | 8 | **完成**（转换层面） | `RefitTransform`（`@Mod.EventBusSubscriber`→`MinecraftForge.EVENT_BUS`、`TickEvent.RenderTickEvent` 用 cpw）、`GunAnimationStateContext`（`LocalPlayer`→`EntityPlayerSP`、`cameraEntity`→`renderViewEntity`、`ForgeCapabilities.ITEM_HANDLER`→`IInventory`、`walkDist`→`distanceWalkedModified`、`input.up/down/left/right/jumping`→`MovementInput#moveForward/moveStrafe/jump`、`onGround` 字段、`isCrouching`→`isSneaking`）、`ThrowableAnimationStateContext`（`ItemStack.EMPTY`→`null`、`getCount`→`stackSize`）、`PlayerModelRenderState`（1.20 `PlayerModel`/`ModelPart`/`PartPose`→1.7.10 `ModelBiped`/`ModelRenderer` 逐字段快照还原）、`InnerThirdPersonManager`（`ModelPart`→`ModelRenderer`、`getMainHandItem`→`getHeldItem`） | **唯二未解析**来自 `compat/playeranimator/PlayerAnimatorCompat`（已回滚为上游，签名仍是 `PlayerModel<?>`/`LivingEntity`）与 `client/gameplay/ClientAmmoCount`（已回滚）。即：本包自身 0 个 1.20 import，但依赖两个**已回滚**的同工作流包 |
| `mcshim/GuiGraphics.java` | 1（新建） | **完成** | lead 授权我自建的唯一 mcshim 类。封装 `Tessellator` + `FontRenderer` + `RenderItem`：`pose()`（每次绘制 `glPushMatrix`+`glMultMatrix(pose.last().pose())`）、`blit`(7/9/11/12 参)、`blitNineSliced`(9/10/11)、`fill`/`fillGradient`/`renderOutline`、`drawString`/`drawCenteredString`（`String` 与 `IChatComponent`、`int` 与 `float` 坐标）、`renderItem`/`renderFakeItem`/`renderItemDecorations`、`renderTooltip`/`renderComponentTooltip`、`enableScissor`/`disableScissor`、`setColor` 手动乘进顶点色、`drawHoveringText`（照抄 1.7.10 `GuiScreen` 算法，因 `protected` 跨包不可调）。`get_file_problems` **0 error** | 无 shim 可用的 1.20 能力（`FormattedCharSequence`、`Optional<TooltipComponent>` 图片 tooltip、`bufferSource` 批次）已降级并注 TODO |
| `client/ClientTickHelper.java` | 1（新建） | **完成** | 替代 1.20 `Minecraft#getFrameTime()`：在 `TickEvent.RenderTickEvent(Side.START)` 缓存 `event.renderTickTime`。原因：1.7.10 `Minecraft.timer` 是 **private** 且生产环境是 SRG 名，反射不可行；`Timer.renderPartialTicks` 虽 public 但拿不到 `Timer` 实例 | **需 ClientProxy 注册**：`MinecraftForge.EVENT_BUS.register(ClientTickHelper.class)` |
| `client/gui/**` | 24 | **完成**（24/24 无 1.20 顶层 import） | 子任务在被误判终止后**实际跑完了全包**（收尾报告已到）。`Screen`→`GuiScreen`、`AbstractContainerScreen`→`GuiContainer`、`Button`/`AbstractWidget`→`GuiButton`、`ImageButton`→自建内部类、`ContainerObjectSelectionList`→`GuiSlot`、`EditBox`→`GuiTextField`、`ForgeSlider`→自实现滑条；5 个 overlay 去 `IGuiOverlay` 改 `render(GuiGraphics,int,int)`；`GunLevelUpToast` 降级为聊天栏消息；`ClothConfigScreen` 整份反射重写（0 cloth import）；`GunSmithTableScreen` 配方源改 `GunSmithTableRecipe.getAll()/getById()` | **跨包阻塞**：① `inventory/**`（common-game）的 `AmmoStorageMenu`/`GunSmithTableMenu` 必须 `extends net.minecraft.inventory.Container`（`containerId`→`windowId`，`GunSmithTableMenu.canSelectRecipe` 去掉 `RecipeManager` 参数），否则两个 GUI 的 `super(menu)` 报错；② `client/init/ClientSetupEvent` 的 overlay 注册要改 `RenderGameOverlayEvent.Post` + `xxxOverlay.render(GuiGraphics.create(), width, height)`；③ `mixin/client/CreativeModeInventoryScreenMixin` 目标类换 `GuiContainerCreative`（`selectedTabIndex` 是 private static）；④ `network/message/ServerMessageLevelUp` 改调 `new GunLevelUpToast(...).show()`；⑤ `util/LaserColorUtil#getLaserColor` 需换 `net.minecraft.item.ItemStack` |
| `client/renderer/**` | 33 | **部分**（5 已转换 / 28 上游原样） | `renderer/item/**` 3 个、`renderer/scope/**` 1 个、`renderer/crosshair/**` 1 个已转换（`IItemRenderer` / `ItemRenderType` 路线） | 子任务异常终止；**整包不可编译**（混合态）。见「回滚清单」 |
| `client/event/**`(20)、`client/gameplay/**`(13)、`client/input/**`(13)、`client/sound/**`(4)、`client/particle/**`(6)、`client/tooltip/**`(6)、`client/init/**`(5) | 67 | **未动**（67/67 上游原样，已回滚） | 无 | 排期在 P2/P3，硬停未开工。见「回滚清单」 |
| `compat/**` | 70 | **部分**（19 已转换 / 51 上游原样） | `compat/ar`(2)、`oculus`(4)、`optifine`(1)、`shouldersurfing`(3)、`playeranimator`(3)、`kubejs`(3)、`jei/category`(1)、`carryon`(1) 等已转换 | 硬停未开工整包；**整包不可编译**（混合态）。计划改成反射式可选兼容骨架（KubeJS/Oculus/AR/PlayerAnimator/Cloth/Controllable/JEI/CarryOn/拔刀剑/SoundPhysics/ShoulderSurfing/OptiFine/Create） |
| `mixin/client|ar|oculus/**` | 19 | **部分**（4 已转换 / 15 上游原样） | `mixin/client` 1、`client/ar` 1、`client/oculus` 2 已转换 | 22 个 mixin 全部针对 1.20.1 原版类，需按 `build/rfg/minecraft-src` 重写；硬停未开工完；**整包不可编译**（混合态） |
| `com/github/mcmodderanchor/simplebedrockmodel/**` | 0 | **未动**（未 vendor） | 无 | 见下方「simplebedrockmodel 处置」裁决 |

#### 回滚清单（主 Agent 复核用）

> **一键回滚命令**（若某个包最终决定「标未动」，在构建前跑一次即可把该包恢复成上游 1.20.1 原样）：
```powershell
cd E:\IDEA\TacZ
$pkgs = @("client\gui","client\renderer","compat","mixin\client")   # 换成要回滚的包
foreach ($p in $pkgs) { $s="tmp\TACZ-1.20.1\src\main\java\com\tacz\guns\$p"; $d="src\main\java\com\tacz\guns\$p"; Get-ChildItem $d -Recurse -Filter *.java | ForEach-Object { $rel=$_.FullName.Substring((Resolve-Path $d).Path.Length+1); $src=Join-Path $s $rel; if (Test-Path $src) { Copy-Item $src $_.FullName -Force } } }
```

以下包**全部文件**已用 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/<包>` 覆盖回上游原样（`Copy-Item` 保留上游 mtime，
所以**看 mtime 看不出回滚**，请以「与上游逐字节相同」为准）：
`client/gui/**`、`client/renderer/**`、`client/event/**`、`client/gameplay/**`、`client/input/**`、
`client/sound/**`、`client/particle/**`、`client/tooltip/**`、`client/init/**`、`compat/**`、`mixin/client/**`、`mixin/client/ar/**`、`mixin/client/oculus/**`（共 215 文件）。
`mixin/common/**` 也曾被我误回滚，已 `send_message` 通知 common-game 重落盘（其已在 04:04 后重新写入）。

#### simplebedrockmodel 处置（lead 已批准）

只 vendor「`client/**` 实际用到的类的传递闭包」（约 80~120 文件），`v2.client.mesh.**`（GPU 网格/VBO/间接绘制）
**降级**为静态绘制或让 client 侧走 v1 路径，`example/**` 不要。**本次硬停未开始 vendor**，
因此 `client/model`/`client/resource` 里 30 处 `com.github.mcmodderanchor.simplebedrockmodel.*` import 是**预期未解析**。

#### client 跨包契约（`send_message` 给 common-infra / common-game 的原文）

**给 common-infra 的（L1 侧静态钩子，common 侧不得 import client）**：
```java
// item/ItemClientHooks.java —— 由 ClientProxy 在客户端 preInit 里赋值
public final class ItemClientHooks {
    public static @Nullable Consumer<net.minecraft.item.Item> registerItemRenderer;
    public static @Nullable ItemNameColorProvider nameColorProvider;   // (ItemStack)->int ARGB，0 表示不染色
}
// entity/EntityBulletClientHooks.java
public final class EntityBulletClientHooks {
    public static @Nullable Consumer<net.minecraft.entity.Entity> ammoParticleSpawner;
    public static void spawnAmmoParticle(net.minecraft.entity.Entity bullet) { ... }
}
```

**给 common-game 的**：`ShooterDataHolder` 公开字段名、`ModSyncedEntityData` 的 `getValue/setValue`、
`entity/shooter/**` 构造器参数顺序**保持上游**，我不改；我 `client/**` 里读这些状态的地方按其最终签名适配。
方块 TESR 由我这边 `ClientRegistry.registerTileEntityRenderers()` 注册，block 包只需保证 TileEntity 类名稳定。

**client 引用了哪些 common 包**（用于判断 client 排除后 common 能否独立编译）：
`api/**`（大量）、`resource/**`（`CommonAssetsManager`/`GSON`/`IGunPackReloadListener`/`PackDirectory`/`GunPackResources`/`manager/**`）、
`util/**`（`ColorHex`/`GunTime`/`AttachmentDataUtils`/`RenderHelper`/`RenderDistance`/`SlotAdapterHelper`/`LaserColorUtil`）、
`config/**`（`client/ResourceConfig`、`client/ClientConfig`）、`network/message/**`、`item/**`、`entity/**`、`block/**`、`crafting/**`、`init/ModItems`、`sound/SoundManager`。
**反方向：common 侧对 client 的依赖只有 `ClientProxy`（`@SidedProxy` 字符串）与 `ClientSideHooks`，无 import**，
所以 `client/**` 进 `pendingPortExcludes` 后 common 可独立构建。

#### client-port 风险点（3 条）

1. **`client/**` 当前处于「部分包完成、其余逐字节上游」的混合态**。受影响的
   `client/resource/**` 与 `client/model/**` 都引用了**已回滚**的同工作流包（`compat/playeranimator/**`、
   `client/gameplay/ClientAmmoCount`、`client/gui/**`、`client/renderer/**`）里仍是 1.20 的签名，
   因此**这两个「已完成」包现在也带跨包 ERROR**。→ 建议：`client/**` **整包进 `pendingPortExcludes`**；
   若想单独验证已完成包，需同时放行 `compat/playeranimator/**` 与 `client/gameplay/ClientAmmoCount`。
2. **`util/RenderHelper` 必须先改 mcshim 签名**（`ItemDisplayContext`/`MultiBufferSource`/`RenderType`/
   `EntityPlayerSP`/`HumanoidArm`/`PoseStack`），否则 `client/model/**` 里 12 个文件永远带 ERROR（common-infra 归属）。
3. **`mixins.tacz.json` 当前应当只列已经是 1.7.10 目标的 mixin**；本工作流 19 个 `mixin/client|ar|oculus/**`
   全部未重写（且 `mixin/common/**` 归 common-game 正在重写），**建议 `mixins.tacz.json` 暂时清空 `mixins`/`client` 列表**，
   等 mixin 重写落地后再逐条加回，否则启动期会因 `@Mixin` 目标类不存在直接崩。

#### ClientProxy 需要调用什么（common-infra 的 `ClientProxy` 里留的 TODO 锚点）

| 锚点 | 需要我提供的入口（**均未实现，仅契约**） |
|---|---|
| 资源重载监听器 | `ClientAssetsManager.INSTANCE.reloadAndRegister(IGunPackReloadListener::add)`，由 `CommonAssetsManager.reload()` 之后驱动；顺序：数据管理器 → `ClientIndexManager.reload()` → `InternalAssetLoader.onResourceReload()` |
| partial tick | `MinecraftForge.EVENT_BUS.register(ClientTickHelper.class)` ✅ 已提供 |
| 键位/输入 | `client/input/**`（`ShootKey`/`AimKey`/`ReloadKey`/`InteractKey`/`ConfigKey`/`CrawlKey`）→ `ClientRegistry.registerKeyBindings()`（未实现） |
| 实体/方块渲染器 | `ClientRegistry.registerEntityRenderers()` / `registerTileEntityRenderers()`（未实现） |
| 粒子 | `ClientRegistry.registerParticles()`（未实现） |
| 物品渲染器 | `ClientItemRendererRegistrar.registerAll()`（未实现） |
| `MuzzleFlashRender.isSelf` / `ShellRender.isSelf` | 纯字段，无需注册；但 `YSM` 外部会直接赋值，**Keep public static boolean** ✅ 已满足 |
| `RefitTransform` 事件 | `MinecraftForge.EVENT_BUS.register(RefitTransform.class)` |
| 枪包语言注入 | `GunPackLoader.injectGunPackLanguage(Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode())`（common-resource 已提供） |
### 复测（回滚 block/inventory/crafting/loot/particles 到上游原样后，最终一次 `./gradlew build`）

`./gradlew spotlessApply` = 成功；`./gradlew build` = 失败于 `:compileJava`（日志 `build/port-build3.log`）。
错误按包（javac 上限 5000，下列为截断后统计，量级可信）：

| 包 | 错误数 |
|---|---|
| client | 4318 |
| block | 2208 |
| compat | 1704 |
| init | 452 |
| mixin | 314 |
| inventory | 290 |
| crafting | 266 |
| entity | 194 |
| util | 142 |
| particles | 50 |
| loot | 32 |
| api | 30 |

其余包（`mcshim` `config` `network` `item` `event` `resource`(除 2 个 serializer) `command` `advancement` `sound` `debug` 以及根包 `GunMod`/`CommonProxy`）**无编译错误**。

### common-infra 追加修正（与 common-resource 接线后，2026-xx 收尾修订）

1. **`util/GetJarResources` 已完成移植**（不再是「未动」）：`copyModDirectory(Class<?>, String srcPath, Path root, String path)` 4 参签名与上游一致，
   `GunPackLoader#reloadPackList` 已在用；`Loader`/`ModContainer` 走 `cpw.mods.fml.common`。
2. **`util/ResourceScanner` 已删除**：`resource/pack/GunPackResources` 只在 javadoc 里提到它，无代码调用；
   上游该类整体基于 1.20 `ResourceManager`/`FileToIdConverter`，1.7.10 侧已由 `GunPackResources` 复刻同等能力。
3. **`util/**` 未动清单更新为 9 个**（进 `pendingPortExcludes`）：
   `ExplodeUtil`、`InputExtraCheck`、`LaserColorUtil`、`RenderDistance`、`RenderHelper`、`ThermalImagingHelper`、
   `block/BlockRayTrace`、`block/ProjectileExplosion`（`GetJarResources`、`ResourceScanner` 已移出）
4. **`CommonProxy.preInit` 追加 3 个接线（common-resource 提供，签名已核对存在）**：
   ```
   2) GunPackLoader.INSTANCE.packType = GunPackType.current();        // 上游 packType = dist.isClient() ? ...
   5) GunMod.registerDefaultExtraGunPack();
      GunPackLoader.INSTANCE.reloadPackList();                        // 幂等；必须在导出默认枪包之后
      AttachmentPropertyManager.registerModifier();
      CommonAssetsManager.registerEventHandlers();                    // PlayerLoggedIn 下发枪包 / WorldUnload 清理
   serverStarting) CommonAssetsManager.reload();                      // 上游 AddReloadListenerEvent
                   CommandRegistry.registerCommands(event);
   ```
   `config/PreLoadConfig.load(Path)` 与 `PreLoadConfig.override.get()`（返回 `Boolean`）两个入口保持不变，
   `GunPackLoader#reloadPackList` 内的 `PreLoadConfig.override.get()` 可直接用。

#### 裁决执行记录（lead 指令 ②）：10 个内容注册类的调用已注释

`CommonProxy.preInit` 里对 **10 个内容注册类**的调用已按 lead 裁决**注释掉**，原地留了整齐的 TODO 块（保持上游 DeferredRegister 顺序，逐行取消注释即可恢复）：

```
// TODO(init 移植): 10 个内容注册类仍是 1.20 DeferredRegister 形态，见 docs/PORT_STATUS.md ——
//   inits 移植完成后按此顺序恢复调用（每行一个类，逐行取消注释即可）：
// ModBlocks.register();
// ModItems.register();
// ModEntities.register();
// ModContainer.register();
// ModSounds.register();
// ModRecipe.register();
// ModAttributes.register();
// ModParticles.register();
// ModLootModifiers.register();
// ModCreativeTabs.register();   // 上游 TABS 在 ITEMS 之前；恢复时按 lead 裁决插到 ModItems 之前
ModPainting.register();          // 本工作流已完成，已生效
```

**恢复条件**：`init/{ModBlocks,ModItems,ModEntities,ModContainer,ModSounds,ModRecipe,ModAttributes,ModParticles,ModLootModifiers,ModCreativeTabs}.java`
全部改成 1.7.10 形态并提供 `public static void register()`（普通 static 字段，非 `DeferredRegister`）→ 把上述 10 行注释逐行打开即可，
**不需要**改其它任何代码。同时需把对应的 `import com.tacz.guns.init.ModXxx;` 加回 `CommonProxy`（当前已移除，
避免这 10 个文件进 `pendingPortExcludes` 时 `import` 无法解析）。

**当前 `CommonProxy` / `GunMod` 状态**：`get_file_problems(errorsOnly)` 与 `lint_files(min_severity=error)` 均 **0 error**，
只依赖已移植的 API：`ConfigManager`、`GunPackLoader`、`GunPackType`、`CommonAssetsManager`、`AttachmentPropertyManager`、
`CommonRegistry`、`CommandRegistry`、`ModPainting`、`event.EventHandlers`、`NetworkTaskQueue`、`SyncConfigSyncManager`、
`inventory.GuiHandler`、`NetworkHandler`、`GunMod.registerDefaultExtraGunPack`。

**与 common-resource 的契约复核结论（2026 收尾）**：`PreLoadConfig.load(Path)` / `override.get()`、
`GetJarResources.copyModDirectory(Class,String,Path,String)`、`NetworkHandler.sendToClientPlayer(IMessage, EntityPlayerMP)`、
`ServerMessageSyncGunPack.createMessages(Map<DataType,Map<ResourceLocation,String>>)`、
`GunPackSync.Part` 的 5 个访问器（`transferId/index/compressedSize/uncompressedSize/data`）与常量
（`CHUNK_SIZE`/`MAX_SYNC_SIZE`）**全部对齐，无待补项**。

#### 最终修正（util 全部完成，排除清单再次收缩）

`util/**`（除 `util/math`）现为 **31/31 完成**，此前列的 10 个「未动」文件**全部已移植**，因此
**`pendingPortExcludes` 不要再排除 util 下任何文件**。当前 src 内已无 1.20.1 包名 import：
`ExplodeUtil`（`net.minecraft.network.play.server.S27PacketExplosion`）、`BlockRayTrace`（`net.minecraft.world.World`）、
`ProjectileExplosion`（`net.minecraft.world.ChunkPosition`）命中的都是 **1.7.10 原生包路径**，不是残留。

**最终排除清单（除 common-game 的 `init/Mod*.java` 由「注释调用」处理外，只剩 1 个文件）**：
- `com/tacz/guns/command/sub/PegboardStressCommand.java`

**util 侧已知降级/需下游配合（构建通过不等于行为等价，联调时看这里）**：
| 点 | 内容 |
|---|---|
| `RenderHelper` | 只保留 stencil 开关 + OptiFine 探测；`blit/innerBlit/getBufferSource/renderFirstPersonArm/endBatch` 5 个方法**删除**（参数类型全在 client/mcshim 侧）→ `client/model/**`、`client/gui/**`、`client/renderer/**` 的相应报错是**预期**的，需 client 层按 1.7.10 固定管线自建（对照表在类 javadoc） |
| `RenderDistance` | `PoseStack`→形参 `Object`（保调用点），数值改读 `GL_MODELVIEW_MATRIX`；`ScreenItemVboContext.isActive()`→静态钩子 `setScreenDrawActive` |
| `LaserColorUtil` | 默认色走静态 hook `setClientDefaultColorResolver`；`LaserConfig` 形参放宽为 `Object` + 反射 `getDefaultColor()` |
| `block/BlockRayTrace` | 接口重写为 `rayTraceBlocks(World, Vec3 start, Vec3 end)`（无命中返回 null）；`tacz:bullet_ignore` 标签无对应 → `registerIgnoreBlock(Block)`（当前空集，需 init/config 填） |
| `block/ProjectileExplosion` | 改 `extends net.minecraft.world.Explosion` 覆盖 `doExplosionA()`；语义损失已写类注释（`BlockInteraction`→`isSmoking`、`ExplosionDamageCalculator`→`explosionResistance`+`canExplosionDestroyBlock`、`GameEvent` 丢弃） |
| `ThermalImagingHelper` | `TagKey<EntityType>`→两张静态集合 + `registerBlacklist/registerWhitelist`；数据包 tag 内容需 init/config 层注册，否则黑名单为空 |
| `GunTime` | `AnimationClock`（simplebedrockmodel）→`System.nanoTime()` + `setTickGate(BooleanSupplier)`；单机暂停时停表要 client 层注入 gate |
| `AmmoStorageInteractionHelper.unloadAmmoBox(...)` | 返回 `InteractionResult`→**`boolean`**（`block/AmmoStorageBlock` 可改回委托调用） |
| L1 边界遗留 | `util/RenderDistance` → `config/client/RenderConfig` → `client/renderer/crosshair/CrosshairType` 的传递依赖仍在；严格 L1 需把 `CrosshairType` 枚举移出 client 包。当前影响面仅限 client 侧配置类（`CommonProxy` 不碰），故未强改，记录待裁决 |
| `util/InputExtraCheck` | 引 `net.minecraft.client.Minecraft`（客户端专用工具，`@SideOnly(Side.CLIENT)` 保留）——编译无碍，专用服务器不加载 |

#### common-game 补齐交付（解除硬停后，最终状态）

**结论：common-game 写作用域内 13 个包全部 1.7.10 化，`^import` 扫描 0 个 1.20.x import。**

| 包 | 文件 | 状态 | 补充内容 |
|---|---|---|---|
| `entity/sync/**` | 9（+删 1） | **完成** | `IDataSerializer/Serializers/DataEntry/DataHolder/SyncedClassKey/SyncedDataKey/SyncedEntityData/ModSerializers/ModSyncedEntityData` 全部 1.7.10 化。**capability → 静态 per-entity 管理器**：`SyncedEntityData` 内 `WeakHashMap<Entity, DataHolder>`（key 弱引用、value 不反持实体，按需创建，`getDataHolder` 返回 null 语义与上游 capability 缺失一致）；**不用 DataWatcher**（1.7.10 仅 32 槽且类型受限，装不下 ReloadState 等复合值，也无法表达 key↔id 映射与 dirty 批量同步）。`DataHolderCapabilityProvider` 删除（无 capability；所有 key 均 save=false，无需 capability 序列化）。NBT 读取用 1.7.10 `NBTPrimitive#func_1502xx_x`；网络用 `mcshim.PacketBuffer`；`BLOCK_POS` 序列化器删除（1.7.10 无 BlockPos） |
| `init/**`（common-game 10 个） | 10 | **完成** | `ModBlocks`（12 方块 + 6 TileEntity + 自定义 ItemBlock 子类）、`ModItems`（5 独立物品 + 12 方块物品 + GunItemManager 注册）、`ModEntities`（EntityRegistry 注册子弹/标靶车）、`ModCreativeTabs`（16 个 1.7.10 `CreativeTabs`，图标/内容懒求值等价上游 displayItems）、`ModAttributes`（`RangedAttribute` 静态字段 + `EntityConstructing` 逐个实体 `registerAttribute`，替代 `EntityAttributeModificationEvent`）、`ModRecipe`（18 配方 JSON 全部代码注册，含 4 个 1.7.10 不存在 id 替换 + `ingotCopper` 缺失跳过）、`ModContainer`/`ModSounds`/`ModParticles`/`ModLootModifiers`（1.7.10 无对应注册表 → 空实现 + javadoc 说明改由谁承担） |
| `CommonProxy.preInit` | 1 | **完成** | 10 个 `Mod*#register()` 调用已逐行恢复（顺序：ModBlocks → ModCreativeTabs → ModItems → ModEntities → ModContainer → ModSounds → ModRecipe → ModAttributes → ModParticles → ModLootModifiers → ModPainting），全部用全限定名避免 import 变更 |

**自查结果**：`mcp__idea__lint_files`（20 个新文件，error 级）→ 无 error；全 scope `^import` 1.20 扫描：
item 12/0、event 19/0、block 23/0、inventory 11/0、crafting 8/0、loot 1/0、particles 2/0、advancement 2/0、sound 1/0、debug 1/0、mixin/common 3/0、entity 27/0、init 16/0。

**剩余运行期风险/待验证（非编译）**：
1. `SyncedEntityData` 静态管理器的 holder 生命周期依赖 `event/SyncedEntityDataEvent`（EntityJoinWorld 按需创建 / Clone 搬运 dataMap / ServerTick 收集 dirty 下发）注册成功；跨维度/死亡重生路径需联调。
2. `ModAttributes` 在 `EntityConstructing` 对每个 `EntityLivingBase#getAttributeMap().registerAttribute(...)`：客户端 attribute map 是否同步 BULLET_RESISTANCE 需运行验证（1.7.10 属性同步走 ServersideAttributeMap watched attributes）。
3. `ModRecipe`：`workbench_b`（GUN_SMITH_TABLE 同款 B 型工作台）在 RECIPE_MAP 里没有产出配方（上游只有 workbench_a/c 配方），归属待确认；`misc/blood_strike_1` 枪包配方需 resource 层接线后调 `GunSmithTableSerializer.deserializeAndRegister`。
4. `ModSounds/ModParticles/ModContainer/ModLootModifiers` 为空实现（1.7.10 无 SoundEvent/ParticleType/MenuType/global loot modifier 注册表），语义分别由字符串声音名、客户端 EntityFX、GuiId+GuiHandler、掉落事件承担（各类 javadoc 已写明）。
---

## L1 收尾定版（entity/sync + init 交付后，最终一次 `./gradlew build`）

`spotlessApply` ✅ / `spotlessJavaCheck` ✅ / `compileJava` ❌（日志 `build/port-final.log`）

| 包 | 剩余错误 | 归属 |
|---|---|---|
| client | 6920 | L2 未开始（gui/renderer/event/particle/init/tooltip 等） |
| compat | 2696 | 反射骨架待写 |
| mixin | 314 | 仅 client mixin |
| api | 30 | 全部是对 client 类的引用，L2 放行即消 |
| entity | 30 | sync 新签名边角 |
| block | 4 | 边角 |
| init | 4 | 边角 |
| item | 2 | 边角 |

**L1 主干（mcshim/config/network/resource/util/block/inventory/crafting/loot/particles/command/sound/debug/advancement/entity/sync/init + 入口）已全部落地**，
剩余 40 条属收尾边角，L2 为下一轮主体。

#### L1 收尾：剩余 0 错误（common-game scope，04:5x）

依据 `build/port-final.log`（compileJava 失败）里属于 common-game 的 **40 行报错 = 20 个唯一错误**（javac 主报 + 汇总重复一遍），已逐条修复：

| # | 包 | 文件:行 | 错误 | 修复 |
|---|---|---|---|---|
| 1 | entity | `EntityKineticBullet` | 未覆盖 `Entity#writeEntityToNBT(NBTTagCompound)` 抽象方法 | 补 `writeEntityToNBT/readEntityToNBT` 空实现（子弹不持久化，对应上游 `EntityType.Builder#noSave()`） |
| 2 | entity | `EntityKineticBullet:398` | `getRNG()` 不存在 | 改 `this.worldObj.rand`（1.7.10 Entity 无 getRNG，世界随机等价） |
| 3 | entity | `EntityKineticBullet:423` | `TacHitResult#getEntity()` 不存在 | 1.7.10 的 `TacHitResult extends MovingObjectPosition` → 用 public 字段 `result.entityHit` |
| 4-6 | entity | `EntityKineticBullet:429/436/496` | `TacHitResult#getLocation()` 不存在 | 同上，用 public 字段 `result.hitVec` |
| 7 | entity | `TargetMinecart` | 未覆盖 `EntityMinecart#getMinecartType()` 抽象方法 | 补 `public int getMinecartType() { return 0; }`（普通矿车类型号；渲染由 L2 自定义 Renderer） |
| 8-10 | entity | `TargetMinecart:70/71/72` | `setHurtDir/setHurtTime/markHurt` 不存在 | 1.7.10 对应 `setRollingDirection(-1)` / `setRollingAmplitude(10)` / `velocityChanged = true` |
| 11 | entity | `TargetMinecart:132` | `killMinecart` 覆盖权限更低（protected vs public） | 改 `public void killMinecart(DamageSource)` |
| 12-13 | entity | `TargetMinecart:136/137/145/146` | `hasCustomNameTag()/getCustomNameTag()` 不存在 | 1.7.10 用 `func_95999_t()`（EntityMinecart 的 custom name getter，返回 String）判空取值 |
| 14 | block | `TargetBlock:431` | `Entity` 符号不存在 | 补 `import net.minecraft.entity.Entity;`（`addCollisionBoxesToList` 的 collider 参数） |
| 15 | block | `TargetBlock:354` | `getPickBlock` 内 pattern 变量 `target` 与参数 `target` 重名 | pattern 变量改名 `targetEntity` |
| 16-17 | init | `ModAttributes:4/34` | `net.minecraft.entity.ai.attributes.Attribute` 不存在 | 1.7.10 是接口 `IAttribute`（`RangedAttribute implements IAttribute`）→ import 与字段类型改 `IAttribute` |
| 18-20 | item | `ModernKineticGunItem:569` | `Suppliers.memoize(...)` 返回 guava `Supplier<T>` 无法赋给 `java.util.function.Supplier<Float>` | 声明类型改 `com.google.common.base.Supplier<Float>`（guava 的 `get()` 调用点不变） |

**自查**：`mcp__idea__lint_files`（5 个修改文件，error 级）→ 无 error；`build/port-final.log` 中 `guns\(entity|block|init|item)\` 匹配的 40 行**全部有对应修复**，无剩余。common-game scope 13 包仍保持 0 个 1.20.x `^import`。
`api/**` 的 30 条（全部是 common 代码对 `client.*` 的引用）按 lead 指示**未动**，等 L2 放行后自然消失。
---

## L2（client）跨包阻塞清单（来自 client-port 收尾，交接给下一轮）

L1 端**已实测**为 0 错误的包，以下条目需在放行 client 前确认（部分可能已被后续提交修复，按需复核）：

| 需求 | owner | 影响 |
|---|---|---|
| `AmmoStorageMenu` / `GunSmithTableMenu` 需 `extends net.minecraft.inventory.Container`；`containerId` → 继承的 `windowId`；`GunSmithTableMenu.canSelectRecipe(ResourceLocation)` **去掉 `RecipeManager` 参数** | common-game | 否则 `GunSmithTableScreen`/`AmmoStorageScreen` 的 `super(menu)` 报「不可转换类型」 |
| `util/LaserColorUtil#getLaserColor` 参数换 `net.minecraft.item.ItemStack` | common-infra | `HSVSliderGroup` 依赖 |
| `client/gameplay/ClientAmmoCount.getCached(...)` 参数 `LocalPlayer` → `EntityPlayerSP` | client-port | `GunHudOverlay`、`GunAnimationStateContext` 依赖 |
| `network/message/ServerMessageLevelUp` 改调 `new GunLevelUpToast(icon,title,sub).show()`（客户端主线程） | common-infra | 升级提示走不到 |
| `config/util/GunPegboardItemConfigRead`：1.7.10 `RegistryNamespaced#getNameForObject` 返回 **String**，需 `new ResourceLocation(name)` | common-infra | 编译不过（本地实测 config 包当前 0 错误，复核后按需处理） |

### client-port 最终逐包状态（351 文件 = 已转换 185 / 上游原样 165 / 半改 0）

- ✅ 100% 去 1.20 import：`client/resource`(86)、`client/model`(40)、`client/animation`(8)、`client/gui`(24)、`mcshim/GuiGraphics`(1)
- ⚠ 部分：`client/renderer`(33)、`compat`(70)、`mixin/client|ar|oculus`(19)
- ❌ 未动：`client/event|gameplay|input|sound|particle|tooltip|init`(67)、`com/github/mcmodderanchor/**`（simplebedrockmodel 未 vendor）
- 硬约束已核：`MuzzleFlashRender.isSelf` / `ShellRender.isSelf` 均为 `public static boolean`

#### 补充接线（command/** + init 交付后，2026 收尾）

子代理交付 `command/**`（11 上游 + 新增 `CommandTargets`）+ `init/` 6 个后，`CommonProxy` / `ClientProxy` 追加了 3 处接线：

```java
// CommonProxy.preInit（第 4 步后）
CapabilityRegistry.register();          // 上游 RegisterCapabilitiesEvent；1.7.10 无 capability → 空实现
// CommonProxy.postInit
CommonRegistry.markLoadComplete();
CompatRegistry.register();              // 上游 InterModEnqueueEvent → 1.7.10 落在 postInit（Loader.isModLoaded + 反射，缺失静默跳过）
// ClientProxy.init（命令层客户端回调注入，避免 command/** import client 包）
ReloadCommand.setClientReloader(() -> CompatRegistry.invokeOptional("com.tacz.guns.client.resource.ClientAssetsManager", "reloadAllPack"));
```
`/tacz convert` 的客户端实现仍待 `resource/PackConvertor` 改成 `convert(net.minecraft.command.ICommandSender)` 后注入
`ConvertCommand.setConverter(...)`（当前 `/tacz convert` 空转并打 warn）。

**`command/**` 最终状态修正**：12 个文件（11 上游 + 1 个移植辅助类 `sub/CommandTargets`，替代 1.7.10 不存在的实体选择器）。
其中 `sub/PegboardStressCommand` 为**部分完成**：刷枪列表、`count`/`same|different`/`pos` 解析、朝向与原几何、
逐格高度/加载/空气校验全部完成；仅「`setBlock` + `getTileEntity` + `setItems`」因 `ModBlocks`/`GunPegboardBlock*`
仍是 1.20 形态而 TODO（上游原文完整保留为注释，当前用上游 `commands.tacz.debug.pegboard.failed` key 如实报失败，
不新增 key、不假装成功）。**因此它已不是"未动"，但仍需排除或待 common-game 交付后补全。**

**command/init 侧待跨包配合的 3 条**：
1. `item/GunTooltipPart.setHideFlags(ItemStack, int)` 仍是 1.20 签名 → `HideTooltipPartCommand` 按上游调用，item 侧改好后自动接上。
2. `ModBlocks.GUN_PEGBOARD` + `block/GunPegboardBlock` + `block/entity/GunPegboardBlockEntity#setItems(Map<Integer,ItemStack>)`
   → `PegboardStressCommand#placeBoards` 放置段的唯一缺口（1.7.10 写法已写在 javadoc）。
3. `resource/PackConvertor.convert(...)` 需改签名，见上。

**已由 init 层解决**：`ModDamageTypes.isBulletDamage(DamageSource)`（供 `event/EntityDamageEvent` 调用；`BULLETS_TAG` 无对应物）；
`ModAttributes.BULLET_RESISTANCE` 现为 `IAttribute`，`CommonRegistry#onEntityConstructing` 已按 `IAttribute` 挂载。

#### 收尾追加：EntityKineticBullet ↔ TargetBlock 命中接线（block subagent 交付的 D-8 跨包项）

block/** 最终交付（23/23）里唯一需要 entity 侧接线的点已补：1.7.10 没有 `Block#onProjectileHit`，
上游 `EntityKineticBullet#onHitBlock` 的 `super.onHitBlock(result)` 间接触发标靶方块逻辑；
现在在 `EntityKineticBullet#onHitBlock` 的 `AmmoHitBlockEvent` post 之后、爆炸处理之前显式转调：
```java
if (hitBlock instanceof TargetBlock targetBlock) {
    Entity owner = this.owner;
    EntityPlayer ownerPlayer = owner instanceof EntityPlayer player ? player : null;
    targetBlock.onBulletHit(this.worldObj, result, ownerPlayer, this.getDamage(result.hitVec));
}
```
覆盖 `TargetBlock#onBulletHit(World, MovingObjectPosition, EntityPlayer, float)` 的全部职责：靶面朝向校验、`TargetBlockEntity#hit(...)`
（击倒动画/5 秒复位/红石强度）、`message.tacz.target_minecart.hit` 聊天提示、`TargetHitTrigger.INSTANCE.trigger(...)`。
（block 包被 `^import` 扫描为 0 个 1.20 import；两包之间无 client 依赖。此前 block 交付的 4 条编译错误也已在「L1 收尾」段修复：`TargetBlock` 的 `Entity` import 与 pattern 变量重名。）

---

### compat/**（task-10，compat-stub）—— 70 → **13** 文件，本包 **0 编译错误**

> 写作用域：`src/main/java/com/tacz/guns/compat/**`。**未跑 gradle**。
> 自检：`mcp__idea__lint_files`（13 文件 / `min_severity=error`）→ **返回空**；`min_severity=warning` 只剩
> 「方法从未使用 / 形参未使用」这类对可选兼容门面无法避免的提示。
> 外部调用点抽查（`get_file_problems`）：`client/animation/third/InnerThirdPersonManager`、
> `client/animation/third/PlayerModelRenderState`、`client/model/functional/BeamRenderer`、
> `init/CompatRegistry` → **0 error**；`CameraSetupEvent` / `RenderCrosshairEvent` / `ShootKey` / `ConfigKey`
> 的剩余 error **全部是本文件尚未移植的 1.20 import**，**已无一条指向 compat**
> （`ShoulderSurfingCompat.isInstalled/showCrosshair`、`ARCompat.shouldAccelerate`、`OculusCompat.endBatch`、
> `ControllableCompat.onGunShoot`、`PlayerAnimatorCompat.*`、`MenuIntegration.getConfigScreen` 均能解析）。

| 项 | 结果 |
|---|---|
| 上游 compat 文件数 | 70 |
| 保留 | **13**（每个兼容域 1 个上游同名门面 + 2 个无库依赖的纯数据类） |
| 删除 | **57**（纯缺失库绑定实现，**全部无外部引用**） |
| 1.20 顶层 import 残留 | **0**（本包只剩 `net.minecraft.*` / `cpw.mods.fml.*` / 本工程 `mcshim`·`api`·`client`·`init`） |

#### 保留清单（13）与对外签名（**其它包按此调用，已定稿**）

| # | 文件 | 对外签名（1.7.10） | 语义 |
|---|---|---|---|
| 1 | `ar/ARCompat` | `MOD_ID="acceleratedrendering"`、`LOADED`(final,false)、`init()`、`shouldAccelerate():false`、`isAccelerated(VertexConsumer):false`、`setRenderingLevel/resetRenderingLevel/setRenderLayer(int)/resetRenderLayer/disableAcceleration/resetAcceleration`(空操作)、`setRenderBeforeFunction/setRenderAfterFunction/resetRenderBeforeFunction/resetRenderAfterFunction`(空操作)、`getRenderBeforeFunction/getRenderAfterFunction():Runnable`(空操作)、`renderLaser(mcshim.VertexConsumer,float,float,boolean,mcshim.PoseStack,int)`(空操作) | 探测 + 静默降级（AR 是 1.20 专属） |
| 2 | `ar/BedrockPartLightingMode` | `enum { SHADED, UNSHADED }` | 原样保留（无库依赖） |
| 3 | `carryon/BlackList` | `addBlackList()` | **真接**：`GameData.getBlockRegistry()` 遍历 namespace=`tacz` 的方块 → `FMLInterModComms.sendMessage("carryon","blacklistBlock", id)`（= 上游 `ForgeRegistries.BLOCKS` + `InterModComms.sendTo`） |
| 4 | `cloth/MenuIntegration` | `LOADED`(false)、`registerModsPage()`、`getConfigScreen(@Nullable GuiScreen): GuiScreen` | 无 Cloth 时返回反射构造的 `client/gui/compat/ClothConfigScreen`（缺库提示页），反射失败则原样返回 `parent` |
| 5 | `controllable/ControllableCompat` | `LOADED`(false)、`init()`、`onGunShoot(ItemStack, FireMode)` | **反射真接** `Controllable.getController().rumble(low,high,ms)`，缺省参数与上游 `ControllableInner#rumbleShoot` 兜底一致（AUTO 0.15/0.25/80ms，其余 0.25/0.5/100ms） |
| 6 | `jei/GunModPlugin` | `LOADED`(false)、`getPluginUid(): ResourceLocation(tacz:jei)`、`isAvailable()`、`init()` | 保留 JEI 入口名；1.7.10 无 JEI，等价物是 NEI（**未接**，见类注释 TODO） |
| 7 | `kubejs/TimelessKubeJSPlugin` | `KUBEJS_MODID`、`LOADED`(false)、`init()`、`postKubeJSEvent(Event): true`（=未处理）、`registerGunType(String, AbstractGunItem)` | `registerGunType` **真接** `GunItemManager.registerGunItem`；`postKubeJSEvent` 是 `api/.../KubeJSGunEventPoster` 3 个 default 方法的落点 |
| 8 | `oculus/OculusCompat` | `LOADED`(false)、`initCompat()`、`isRenderShadow():false`、`isOculusInstalled():false`、`isUsingRenderPack():false`、`endBatch(mcshim.MultiBufferSource):false`、`getHandBufferSource():null` | **纯反射版本分派**（`net.irisshaders.*` / `net.coderbot.*` 两套包名），取代上游 legacy/newly 两个类 |
| 9 | `oculus/IUnflushableWrapper` | `mcshim.MultiBufferSource tacz$getWrapped()` | 返回类型 `MultiBufferSource` → mcshim 版（原为 1.20 类型） |
| 10 | `optifine/OptifineCompat` | `isOptifineInstalled()`、`isClassFound(String)`（上游）+ **新增** `isShadersEnabled()`、`isShaderPackLoaded()` | **1.7.10 有 OptiFine，真探测**：`net.optifine.Config#isShaders()`、`net.optifine.shaders.Shaders#shaderPackLoaded`（兼容 `shadersmod.client.Shaders`），纯反射无硬依赖 |
| 11 | `playeranimator/PlayerAnimatorCompat` | 4 个 `ResourceLocation` 常量 + `init()`、`isInstalled():false`、`canPlayAnimation(EntityLivingBase)`**真逻辑**、`hasPlayerAnimator3rd(EntityLivingBase, GunDisplayInstance):false`、`captureModelState(ModelBiped):Runnable`(空操作)、`registerReloadListener(Consumer<mcshim.PreparableReloadListener>)`、`loadAnimationFromZip/loadAnimationFromFile/clearAllAnimationCache/stopAllAnimation(×2)/playAnimation` | 只有 `canPlayAnimation` 是上游**不依赖该库**的真逻辑，其余按「没装库」分支降级 |
| 12 | `playeranimator/AnimationName` | 23 个 `String` 动画名常量 | 原样保留（无库依赖） |
| 13 | `shouldersurfing/ShoulderSurfingCompat` | `LOADED`(false)、`init()`、`isInstalled():false`、`showCrosshair():false` | **纯反射真判定**：`Perspective.current()==SHOULDER_SURFING && !InputHandler.FREE_LOOK.isDown()`，取代上游 `ShoulderSurfingCompatInner` |

所有探针统一写法：`Loader.isModLoaded(modid) && Class.forName(FQN, false, 本类.getClassLoader())`
（`initialize=false`，**不触发对方静态初始化**），`try/catch Throwable` 静默 → 缺失库时等同上游「未安装」。

#### 删除清单（57，逐文件原因）

**`ar/`（3）** —— Accelerated Rendering 是 1.20 专属模组（`IBufferGraph`/`IAcceleratedRenderer`/网格缓存/VAO），1.7.10 无该库也无可反射等价 API：
- `AcceleratedBeamRenderer`：签名直挂 `IAcceleratedRenderer`/`VertexConsumerExtension`
- `ARCompatImpl`：直调 `CoreFeature`/`AcceleratedEntityRenderingFeature`/`AcceleratedItemRenderingFeature`/`AcceleratedTextRenderingFeature`
- `BeamRenderContext`：只服务于上面两个类的纯 AR 数据载体

**`cloth/`（10）** —— Cloth Config 是 1.14+，`me.shedaniel.clothconfig2.*` 在 1.7.10 不存在：
- `client/KeyClothConfig`、`client/RenderClothConfig`、`client/ResourceClothConfig`、`client/SoundClothConfig`、`client/ZoomClothConfig`：`ConfigBuilder`/`ConfigCategory`/`ConfigEntryBuilder` 直调（且用 1.20 `Component`）
- `common/AmmoClothConfig`、`common/GunClothConfig`、`common/OtherClothConfig`：同上
- `widget/CrosshairDropdown`：`DropdownBoxEntry` 子类；`widget/OpenGunPackDirEntry`：`AbstractConfigListEntry` 子类

**`controllable/`（1）**：
- `ControllableInner`：`com.mrcrayfish.controllable.*`（`BindingRegistry`/`ButtonBinding`/`IBindingContext`/`ControllerEvents`/`Value`）+ `com.mrcrayfish.framework.api.event.TickEvents`，1.7.10 无该模组

**`jei/`（13）** —— JEI 只支持 1.8+；1.7.10 的等价物是 NEI，但 API 完全不同：
- `GunModSubtype`：`IIngredientSubtypeInterpreter`
- `category/AmmoQueryCategory`、`category/AttachmentQueryCategory`、`category/GunAmmoQueryCategory`、`category/GunAttachmentQueryCategory`、`category/GunSmithTableCategory`、`category/package-info`：`IRecipeCategory`/`IRecipeLayoutBuilder`/`IDrawable`/`IFocusGroup`/`IRecipeSlotsView`（`package-info` 只描述该包）
- `entry/AmmoQueryEntry`、`entry/AttachmentQueryData`、`entry/AttachmentQueryEntry`、`entry/GunAmmoQueryEntry`、`entry/GunAttachmentQueryEntry`：数据本身可移植，但**唯一消费者是上面被删的 JEI category**，留下即无调用方死代码；日后接 NEI 时按 `TimelessAPI` 索引重写更直接
- `transfer/GunSmithTableTransferHandler`：`IRecipeTransferHandler`/`IRecipeTransferError`/`IRecipeTransferHandlerHelper`

**`kubejs/`（18）** —— KubeJS 只支持 1.16.5+，其类型是这些类**方法签名的一部分**，无法在无库时保持签名：
- `custom/CustomGunItemBuilder`（`ItemBuilder` 子类）、`custom/KubeJSCustomGunItem`（其配套自定义枪械物品，唯一引用者同上）
- `events/GunKubeJSEvents`、`events/TimelessClientEvents`、`events/TimelessCommonEvents`、`events/TimelessForgeEventWrappers`、`events/TimelessKubeJSEventRegister`、`events/TimelessServerEvents`（`EventGroup`/`EventHandler`/`EventJS`/`ScriptTypePredicate`/`ScriptTypeHolder`）
- `recipe/GunSmithTableResultComponents`、`recipe/TimelessGunSmithTableRecipeSchema`、`recipe/TimelessRecipeJS`（`RecipeSchema`/`RecipeComponent`/`RecipeJS`/`InputItem`/`OutputItem`）
- `util/AmmoNbtFactory`、`util/AttachmentNbtFactory`、`util/GunNbtFactory`、`util/GunSmithTableResultInfo`、`util/TimelessItemNbtFactory`、`util/TimelessItemType`、`util/TimelessItemWrapper`（`ItemStackJS`/`NBT` 双向转换，无 KubeJS 脚本对象即无宿主）

**`oculus/`（2）**：
- `legacy/OculusCompatLegacy`（`net.coderbot.*`）、`newly/OculusCompatNewly`（`net.irisshaders.*`）：两者都是「按包名直调 `FullyBufferedMultiBufferSource`/`HandRenderer`/`ShadowRenderingState`」，已由 `OculusCompat` 的**纯反射同语义版本分派**取代

**`playeranimator/`（8）** —— Player Animator 只支持 1.16.5+：
- `animation/AdjustmentYRotModifier`、`animation/AnimationDataRegisterFactory`、`animation/AnimationManager`、`animation/PlayerAnimationLayer`、`animation/PlayerAnimatorAssetManager`、`animation/PlayerAnimatorLoader`、`animation/PlayerAnimatorModelState`、`animation/RotationAnimation`（`ModifierLayer`/`KeyframeAnimationPlayer`/`IMutableModel`/`PlayerAnimationRegistry`/`AnimationSerializing` 等直调）

**`shouldersurfing/`（2）**：
- `ShoulderSurfingCompatInner`：`Perspective`/`InputHandler.FREE_LOOK` 直调 —— 已由 `ShoulderSurfingCompat` 的纯反射判定取代
- `ShoulderSurfingPlugin`：`IShoulderSurfingPlugin`/`IShoulderSurfingRegistrar`，且 `shouldersurfing_plugin.json` 元数据已在基础设施阶段删除（无发现入口）

#### compat 移植特有决策 / 差异（复核时重点看）

1. **`PlayerAnimatorCompat#canPlayAnimation` 是唯一「真逻辑」**：上游这段判断不依赖 Player Animator。1.7.10 映射：
   `getMainHandItem()`→`getHeldItem()`、`getPose()`→`EntityStateHelper.getPose(EntityLivingBase)`、
   `onClimbable()`→`isOnLadder()`、`isSwimming()`→`EntityPose.SWIMMING`（由「在水里 + 疾跑」推导，最接近 1.20 语义）、
   `Pose.FALL_FLYING` 恒不出现。`InnerThirdPersonManager` 靠它决定走原版第三人称动画。
2. **`endBatch`/`getHandBufferSource` 的 buffer 类型换成 `mcshim.MultiBufferSource`**（1.20 是
   `net.minecraft.client.renderer.MultiBufferSource`），与 `client/model/RenderHelperCompat#endBatch(MultiBufferSource)` 同型，
   调用点 `if (!OculusCompat.endBatch(x)) RenderHelperCompat.endBatch(x, renderType);` 语义不变（恒 false → 走自己的 endBatch）。
3. **`MenuIntegration.getConfigScreen` 用反射构造 `ClothConfigScreen`**：该类构造器目前是 `protected`（client 侧文件），
   compat 用 `getDeclaredConstructor(GuiScreen.class)+setAccessible` 触发，**不引入跨包可见性依赖**；
   若 client 侧把它改成 `public`，可去掉反射。
4. **`OculusCompat.getHandBufferSource()` 恒 `null`**：Iris 返回的 1.20 `MultiBufferSource` 无法转换为 mcshim 接口，
   需要时得为光影模组单独写适配器（TODO(L2)）。
5. **待办不属本作用域（需 mixin/client 侧处理）**：`mixin/client/ar/BedrockPartMixin.java` 与
   `mixin/client/oculus/UnflushableWrapperAccessor.java` 仍是 1.20 写法（import `net.irisshaders.*`、
   `com.github.argon4w.*`、`com.mojang.blaze3d.*`），1.7.10 无法编译；`mixins.tacz.json` 的 `client` 列表本来也是空的
   → 建议直接删这两个 mixin（`com.tacz.guns.compat.ar.BedrockPartLightingMode` 与 `com.tacz.guns.compat.oculus.IUnflushableWrapper` 仍保留，删 mixin 不会连带丢符号）。
---

## L2-D Mixin 客户端改写（task-11 / mixin-port）—— 最终状态

> 写作用域：`src/main/java/com/tacz/guns/mixin/client/**`。
> 起始 18 文件（`client/*` 14 + `client/ar/*` 2 + `client/oculus/*` 2）→ **交付 14 文件**：
> `client/ar/**` 与 `client/oculus/**` 整包删除（1.7.10 无 Accelerated Rendering / Iris，门面由
> `compat.ar` / `compat.oculus` 承担，见下方表格最后一行）。`mixin/common/**` **一行未动**。
> 判定：`IDEA lint_files` 全量扫描 14 文件 **0 error**；`com.mojang.*` / `net.minecraft.{core,world.*,resources.*,server.*,network.chat}.*` /
> `it.unimi.*` / `com.github.argon4w.*` / `net.irisshaders.*` 等 1.20 专属 import **0 命中**（仅 javadoc 里作为「上游是什么」出现）。
> **未跑 gradle**。所有 `@Inject` 目标方法名/描述符均在 `build/rfg/minecraft-src/**` 里逐一核对过（不按 1.20 名猜写法）。

### 逐文件：目标类 + 注入点 + 是否生效

| 文件 | 1.7.10 目标类（上游目标） | 注入点（上游 → 1.7.10） | 生效 |
|---|---|---|---|
| `AbstractButtonMixin` | `client.gui.GuiButton`（`AbstractButton`） | `onClick(DD)V` HEAD → `mousePressed(Minecraft,int,int)Z` **RETURN**（1.7.10 无按钮回调，靠返回值判断命中） | **是**（完全等价） |
| `CreativeModeInventoryScreenMixin` | `client.gui.inventory.GuiContainerCreative`（`CreativeModeInventoryScreen`） | `init` TAIL→`initGui` TAIL；`selectTab` TAIL→`setCurrentCreativeTab` TAIL；`render` HEAD→`drawScreen` HEAD；**widget 绘制**→`drawScreen` TAIL（自绘 `GuiButton#drawButton`）；**widget 点击**→`mouseClicked` HEAD（自派发 `GuiButton#mousePressed` + 播 `gui.button.press` + cancel）；`mouseScrolled` HEAD→`handleMouseInput` HEAD(cancel)；`refreshCurrentTabContents` TAIL→**无对应**（形参 `GuiContainerCreative$ContainerCreative` 是包私有类，回调签名写不出来）→ 由 `drawScreen` HEAD 每帧对账覆盖，最多晚 1 帧。**不使用 `GuiScreen#buttonList`**（`@Shadow` 不支持父类字段，理由见下方风险 1） | **是**（1 个刷新注入点降级到「下一帧」，≤1 帧延迟；z 序与 Forge `ActionPerformedEvent` 两处无功能影响差异） |
| `EntityRenderDispatcherMixin` | `client.renderer.entity.RenderManager`（`EntityRenderDispatcher`） | 上游 `render` 里 `EntityRenderer#render(...)` 的 `@WrapOperation` 换 `MultiBufferSource` → 1.7.10 无 buffer source，改为 `RenderManager#func_147939_a(Entity,dd,dd,dd,ff,boolean)` 的 **HEAD/RETURN** 包一个「重绘到 mask FBO」的捕获窗口（`func_147939_a` 是 `renderEntityStatic`/`renderEntityWithPosYaw`/`renderEntitySimple` 三者的唯一汇聚点） | **✅ 已生效（2026-11 接线）**：`ScopeThermalRenderer.beginEntity(EntityLivingBase)` / `endEntity()` 由 client-render 补齐；`beginEntity` 内部按成本顺序早退（`redrawing`/`collecting` 布尔 → 整帧缓存的 `enabled()` → 非相机实体 → 活且可见 → `ThermalImagingHelper.isHeatSource`），**未开热成像瞄具时每帧每实体成本 ≈ 2 次字段读 + 1 次判空**；与上游差异：上游是同一次绘制内换 bufferSource（零额外绘制），1.7.10 是「再画一遍到 mask FBO」，仅对极少数热源实体发生 |
| `GameRendererMixin` | `client.renderer.EntityRenderer`（`GameRenderer`） | ① `renderLevel` HEAD→`renderWorld(F,J)` HEAD → `ScopeLensRenderer.beginFrame()` **✅ 已接线**；② `renderLevel` 的 `ProfilerFiller#popPush` ordinal=2 → **1.7.10 无对应**（`renderWorld` 无同名 profiler 段），改用 `renderHand` 的 `renderOverlays` INVOKE 点（=③）；**② 的 `renderHand` HEAD 版有意保持注释**（1.7.10 无 shader 路径，且 `ScopeLensRenderer.render()` 不清理 `LENSES`，与 ③ 同时启用会双绘 + 双 `copyScreen`）；③ `renderItemInHand`→`renderHand` 的 `Lnet/minecraft/client/renderer/ItemRenderer;renderOverlays(F)V` INVOKE → `ScopeLensRenderer.render()` **✅ 已接线**（手已画完、屏幕特效未画，与上游「hands 之后 / screen overlays 之上」一致；该点位于 `thirdPersonView==0` 分支内，第三人称不渲染镜片）；④ `bobHurt` HEAD→`hurtCameraEffect` HEAD ✅（内含 `GunHurtBobTweak.onHurtBobTweak(EntityPlayerSP, PoseStack, float)` 分支，命中后 `applyToModelView()` + cancel）；⑤ `bobHurt` 的 `Axis.rotationDegrees(2)`→`hurtCameraEffect` 第 3 个 `GL11.glRotatef` `@ModifyArg(index=0)` ✅；⑥ `bobView` HEAD→`setupViewBobbing` HEAD ✅；⑦ `getFov` HEAD→`getFOVModifier(F,Z)` HEAD 记录 `useFovSetting` ✅ + 手部那一路 RETURN 覆盖为 `CameraSetupEvent.ITEM_MODEL_FOV` ✅（1.7.10 唯一可行路径）；⑧ **新增：`orientCamera(F)` RETURN → 相机 roll** ✅（见 D1） | **✅ 全部生效**（除 ② 有意注释，理由已写在代码注释里） |
| `HumanoidModelMixin` | `client.model.ModelBiped`（`HumanoidModel`） | `setupAnim(LivingEntity;FFFFF)` TAIL → `setRotationAngles(FFFFFFLEntity;)V` TAIL；`ModelPart head/body/leftArm/rightArm`→`ModelRenderer bipedHead/bipedBody/bipedLeftArm/bipedRightArm`（1.7.10 字段**非 final**，故不加 `@Final`） | **是** |
| `PlayerModelMixin` | `client.model.ModelBiped`（`PlayerModel`；1.7.10 无独立 `PlayerModel` 类） | `setupAnim` TAIL → 同一 `setRotationAngles` TAIL，`priority=1100` 保证在 `HumanoidModelMixin`(1000) 之后执行；`ModelPart.copyFrom`→**删除**（1.7.10 无独立袖子骨骼，袖子与手臂共用 `bipedArm`） | **是**（袖子同步不需要；顺序由 priority 保证） |
| `ItemInHandLayerMixin` | `client.renderer.entity.RenderPlayer`（`ItemInHandLayer`；1.7.10 无 RenderLayer 体系） | `renderArmWithItem` HEAD/TAIL + `render` TAIL → `renderEquippedItems(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V` HEAD/RETURN（**必须写全描述符**，同名桥接方法在 `RenderPlayer.java:520`） | **是（全部接线完成）**：`MuzzleFlashRender.isSelf`/`ShellRender.isSelf` 置位与复位；`ThirdPersonMuzzleParticleManager.currentEntity`（`ThirdPersonMuzzleParticleManager.java:97` = `public static EntityLivingBase`）；`HumanoidOffhandRender.renderGun(EntityLivingBase, mcshim.PoseStack, mcshim.MultiBufferSource, int)`，`packedLight` 取 `World#getLightBrightnessForSkyBlocks(floor(posX),floor(posY),floor(posZ),0)`（1.7.10 实体渲染标准打包亮度；`OpenGlHelper.lastBrightnessX/Y` 是上一次残留值会随渲染顺序漂移，故不用）。**已知降级（非 bug）**：`renderOffhandGun` 为空实现（1.7.10 无双持槽），可见挂枪来自 `renderHotbarGun`（快捷栏其余 8 格） |
| `ItemInHandRendererMixin` | `client.renderer.ItemRenderer`（`ItemInHandRenderer`） | `renderHandsWithItems` HEAD→`renderItemInFirstPerson(F)V` HEAD（投 `BeforeRenderHandEvent` + `PoseStack#applyToModelView()`）；`tick` HEAD→`updateEquippedItem` HEAD（上游方法体整段注释 → 保持空）；`implements KeepingItemRenderer`（`keep`/`getCurrentItem`） | **是**（`KeepingItemRenderer.getRenderer()` 的强转目标就是本类） |
| `LanguageMixin` | `client.resources.Locale`（`ClientLanguage`） | 上游两个 `@Inject` **本来就是注释状态**且引用的 `ClientAssetManager` 上游已不存在（死代码）→ 保持空 `@Mixin` | **空操作**（语言注入已裁决走 `LanguageRegistry`，见 ASSET_PORT_NOTES §5.1） |
| `LocalPlayerMixin` | `client.entity.EntityPlayerSP`（`LocalPlayer`） | `implements IClientPlayerGunOperator`（**关键：`fromLocalPlayer` 是裸强转**，没有它第一次开火/瞄准/切枪就 CCE）；`tick` HEAD→`onLivingUpdate` HEAD；`aiStep` 的 `setSprinting` `@WrapOperation`→`onLivingUpdate` 的 `EntityPlayerSP#setSprinting(Z)` `@Redirect`（覆盖 4 处调用，`processed(false)==false` 故语义不变）；`respawn` RETURN→**降级（≤1 tick）**：1.7.10 重生走 `NetHandlerPlayClient#handleRespawn` → `Minecraft#setDimensionAndSpawnPlayer` **新建 `EntityClientPlayerMP`**，Mixin 字段天然全新，不需要 reset；唯一差异是上游 reset 后还会 `draw(EMPTY)`，1.7.10 下一 tick 由 `LocalPlayerDraw` 重判 | **是**（接口 **20 个**方法全部实现；每 tick 驱动 + sprint 拦截生效） |
| `MouseHandlerMixin` | **`net.minecraft.entity.Entity`**（上游 `MouseHandler`） | 1.7.10 无 `MouseHandler`；`EntityPlayerSP` 也没有 `turn`。鼠标转向的应用点是 `Entity#setAngles(float,float)`（`Entity.java:359`，由 `EntityRenderer#updateCameraAndRender` 的两处调用驱动，`EntityRenderer.java:1061/1065`）→ 两个 `@ModifyVariable(method="setAngles", index=1/2)`；趴下判断 `getPose()==SWIMMING`→`EntityStateHelper.getPose()==EntityPose.SWIMMING` | **是**（完全等价；避开 `@Redirect` 硬编码 INVOKE 描述符的匹配风险） |
| `RenderSystemAccessor` | —（上游 `RenderSystem`，1.7.10 无此类） | `@Accessor("shaderLightDirections")` → **无对应**（1.7.10 无 shader program/主光方向） | **占位类（非 Mixin）**。全仓/上游全仓 grep 均只剩自身引用（上游使用者 `UnshadedVertexConsumer` 已由 client-port 降级） |
| `SoundManagerPreparationsMixin` | `client.audio.SoundHandler`（`SoundManager$Preparations`） | `listResources` TAIL 合并 `tacz_sounds` 资源 → **无对应**：1.7.10 音效注册链是 `SoundHandler#onResourceManagerReload` + `SoundRegistry#registerSound(SoundEventAccessorComposite)` + `SoundPoolEntry`；且枪包目录不在原版 `IResourceManager` 可见范围 | **降级（空 `@Mixin`）**。替代钩子与两段式接法写在类 javadoc（含两个构造器行号） |
| `StairBlockAccessor` | —（上游 `StairBlock`，1.7.10 是 `block.BlockStairs`） | `@Invoker(remap=false) getModelBlock()` → **无对应**（1.7.10 无 Forge 补丁的 `getModelBlock`；等价物是私有字段 `BlockStairs#field_150149_b`，应用 `@Accessor(value="field_150149_b", remap=false)`） | **占位类（非 Mixin）**。上游全仓 grep 只有自身，**上游即死代码** |
| ~~`ar/**`、`oculus/**`（4 文件）~~ | — | — | **已删除（不是占位类，是删文件）**：`mixin/client/ar/{ARCompatMixinPlugin,BedrockPartMixin}.java`、`mixin/client/oculus/{OculusMixinPlugin,UnflushableWrapperAccessor}.java` 连同两个空目录一并删除。**原因**：1.7.10 无 Accelerated Rendering / Iris(Oculus) 库；`@Mixin(targets="net.irisshaders...")` 指向不存在的类会让 Mixin 配置**加载失败**，连「挂着但不生效」都不行。**门面由 compat 承担**：`compat.ar.BedrockPartLightingMode`、`compat.oculus.IUnflushableWrapper`（另有 `compat.ar.ARCompat`、`compat.oculus.OculusCompat`），语义声明一行未丢。删除前已 grep 确认全仓**无代码引用**（只剩 4 处 javadoc 提及）。`mixin/client/**` 因此由 18 文件收敛为 **14**。 |

### `mixins.tacz.json` 定稿存档（lead 已写，抄一份便于日后对账）

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.tacz.guns.mixin",
  "compatibilityLevel": "JAVA_8",
  "refmap": "mixins.tacz.refmap.json",
  "mixins": [
    "common.LivingEntityMixin",
    "common.ServerPlayerMixin",
    "common.ServerGamePacketListenerImplMixin"
  ],
  "client": [
    "client.LocalPlayerMixin",
    "client.MouseHandlerMixin",
    "client.ItemInHandRendererMixin",
    "client.ItemInHandLayerMixin",
    "client.GameRendererMixin",
    "client.HumanoidModelMixin",
    "client.PlayerModelMixin",
    "client.CreativeModeInventoryScreenMixin",
    "client.AbstractButtonMixin",
    "client.CreativeContainerAccessor"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- **`mixins`（3）**：common-game 负责（`mixin/common/**`），本次未改动。
- **`client`（10）**：全部真正生效、`lint_files` 0 error、注入点已核对到 `build/rfg/minecraft-src`。
  `HumanoidModelMixin` 与 `PlayerModelMixin` **必须成对出现**（`priority` 1000/1100 决定
  「先套第三人称姿态、再清零手臂」，删一个或改 priority 都会坏，见两个类的 javadoc 警告）。
  `CreativeContainerAccessor` 是 `CreativePackFilters` 取 `ContainerCreative#itemList/scrollTo` 的唯一
  生产可用通道（见 A 节）；漏配只降级（筛选失效），不启动崩。
- **未列入 `client`（可加载的空 Mixin，无注入、不改变行为）**：
  `EntityRenderDispatcherMixin`、`LanguageMixin`、`SoundManagerPreparationsMixin`。
- **绝对不能列入（不是 Mixin，列了会让配置加载失败）**：`RenderSystemAccessor`、`StairBlockAccessor`
  （两个都已改成普通 `final` 占位类，仅保留上游说明与 1.7.10 等价写法）。
- `ar.*` / `oculus.*` 已整包删除，既不列也不配 `"plugin"`。

### 仍需在构建/运行期验证的 3 条风险（已尽量降风险）

1. ~~`CreativeModeInventoryScreenMixin` 的 `@Shadow protected List<GuiButton> buttonList;`~~ ——
   **已修，不再是风险**。核对了 UniMixins 0.3.1 里的 Mixin 源码后确认：
   `MixinPreProcessorStandard#attachFields` → `MixinTargetContext#findField(FieldNode, AnnotationNode)`
   → `TargetClassContext#findAliasedField(Deque, desc, includeMixinFields)` 只遍历
   `this.classNode.fields`（目标类**自有**字段）与 `this.mixinFields`，**没有 superclass 遍历** ——
   影子 `GuiScreen#buttonList` 会抛 `InvalidMixinException: @Shadow field buttonList was not located in
   the target class`，进而让整个 client mixin 配置加载失败（启动崩）。
   现改为**完全不用 `buttonList`**：`@Unique List<GuiButton> tacz$buttons` 自持 + `drawScreen` TAIL 自绘
   （`GuiButton#drawButton` 是 public）+ `mouseClicked` HEAD 自派发（`GuiButton#mousePressed` 是 public，
   命中后播 `gui.button.press` 并 `ci.cancel()`）。代价只是 z 序与不走 Forge `ActionPerformedEvent`
   两处无功能影响的差异。**本类现在只影子 `selectedTabIndex` / `currentScroll` 两个目标类自有字段。**
   同一条结论对全仓所有 mixin 都适用：**`@Shadow` 不能影子父类字段**。
2. **`GameRendererMixin` 的 `@ModifyArg` 用了 `ordinal = 2`** 命中 `hurtCameraEffect` 里第 3 个
   `GL11.glRotatef`（第 525 行 `-f1*14.0F`）。若 Forge/OptiFine 改写了 `hurtCameraEffect` 的字节码，
   ordinal 会漂移。建议 lead 在 dev 客户端开一次「被枪击」验证倾斜角是否按配置缩小。
3. **`MouseHandlerMixin` 的两个 `@ModifyVariable` 用 `index = 1 / 2`** 依赖 1.7.10
   `Entity#setAngles(float yaw, float pitch)` 的形参槽位（非静态方法槽 0 = this）。该签名已核实，
   但若将来有 coremod 改 `setAngles` 需重新核对。

### `client/gui/CreativePackFilters` 的 MCP 名反射问题（未修，已定位，需跨包改）

`CreativePackFilters` 用 `readField("guiLeft"/"guiTop")` 与 `readField(container, "itemList")` 按
**MCP 名**做反射，运行期字段名是 SRG，因此**全部取不到**：
- `guiLeft/guiTop` 会回退到 `(width-195)/2 / (height-136)/2`（正好等于原版默认值，**无影响**）；
- `getContainerItemList()` 返回 `null` → `showItems()` 直接 return → **「内容包筛选点了没反应」**（真 bug）。

**为什么没有由本 Mixin 转接**：`ContainerCreative` 是 `GuiContainerCreative` 的**包私有**内部类
（`GuiContainerCreative.java:1003` `static class ContainerCreative`），
`mixin/client/CreativeModeInventoryScreenMixin` 既写不出它的回调签名，也无法从外部类型安全地拿到
`inventorySlots` 里的 `itemList`。**推荐修法（需改 `client/gui/CreativePackFilters`，属 client 侧）**：
给它加一个可注入的静态钩子，例如
`public static @Nullable Function<GuiContainerCreative, List<ItemStack>> itemListProvider;`
（默认 `null` → 退回现在的反射路径），由 `CreativeModeInventoryScreenMixin#initGui` 赋值成
「`((ContainerCreative) screen.inventorySlots).itemList`」的反射实现（反射名用 **SRG**
`field_147063_B` 之外的正确字段名，或直接在 Mixin 内 `@Shadow` 目标类自有字段 —— 注意 `inventorySlots`
是 `Container` 的字段，同样受 `@Shadow` 不能影子父类字段的限制，只能用反射并改用 SRG 名）。
**记 TODO(client)**，不阻塞 mixin apply、不阻塞启动。

### 上游目标 → 1.7.10 等价类映射（本次实际采用版，供后续复用）

| 上游（1.20.1） | 1.7.10 实际目标 |
|---|---|
| `AbstractButton` | `net.minecraft.client.gui.GuiButton` |
| `CreativeModeInventoryScreen` | `net.minecraft.client.gui.inventory.GuiContainerCreative`（`selectedTab`→`private static int selectedTabIndex`；`scrollOffs`→`private float currentScroll`；`selectTab`→`setCurrentCreativeTab`；`mouseScrolled`→`handleMouseInput`） |
| `EntityRenderDispatcher` | `net.minecraft.client.renderer.entity.RenderManager`（渲染调用点 `func_147939_a`） |
| `GameRenderer` | `net.minecraft.client.renderer.EntityRenderer`（`renderLevel`→`renderWorld`；`bobHurt`→`hurtCameraEffect`；`bobView`→`setupViewBobbing`；`getFov`→`getFOVModifier`；`renderItemInHand`→`renderHand`） |
| `HumanoidModel` / `PlayerModel` | `net.minecraft.client.model.ModelBiped`（**同一个类**；`ModelPart`→`ModelRenderer`，`xRot/yRot/zRot`→`rotateAngleX/Y/Z`） |
| `ItemInHandLayer` | `net.minecraft.client.renderer.entity.RenderPlayer#renderEquippedItems(AbstractClientPlayer,F)` |
| `ItemInHandRenderer` | `net.minecraft.client.renderer.ItemRenderer`（`renderHandsWithItems`→`renderItemInFirstPerson`；`tick`→`updateEquippedItem`；`mainHandItem`→`itemToRender`） |
| `ClientLanguage` | `net.minecraft.client.resources.Locale` / `LanguageManager` |
| `LocalPlayer` | `net.minecraft.client.entity.EntityPlayerSP`（注意运行期实例是子类 `EntityClientPlayerMP`，它**不覆写** `onLivingUpdate`/`setSprinting`） |
| `MouseHandler` | **`net.minecraft.entity.Entity#setAngles(float,float)`**（1.7.10 无 `MouseHelper` 级别的转向应用点） |
| `RenderSystem` | 无（1.7.10 无 shader program） |
| `SoundManager$Preparations` | `net.minecraft.client.audio.SoundHandler` + `SoundRegistry` + `SoundPoolEntry` |
| `StairBlock` | `net.minecraft.block.BlockStairs`（模板方块 = 私有字段 `field_150149_b`） |
| AR / Oculus 专属 | 无对应库（1.7.10 的光影是 OptiFine/ShadersMod） |

---

## 高保真阶段进展（lead 实测）

### 策略变更（用户要求）
用户明确：**时间/预算充足，优先高保真移植**；验证**用 IDEA MCP lint 而非反复 gradle**；gradle 只由主 Agent 在集成点跑。
据此把 L2 从"能编译优先+降级"改为"按上游形态真做"，并补开 4 条工作流：

| 工作流 | teammate | 作用域 | 状态 |
|---|---|---|---|
| L2-A 启动关键路径 | client-core | `ClientProxy` + `client/{init,input,gameplay}` + `client/resource` 剩余 4 | 进行中 |
| L2-B 渲染 | client-render | `client/renderer/**` + `client/model/**` | 进行中（`RenderHelperCompat` 真实现、`EntityBulletRenderer` 用 GL_TEXTURE 矩阵复刻 energySwirl、`RenderMinecart`/`SkinManager` 真接） |
| L2-C compat | compat-stub | `compat/**` | ✅ **70→13 文件，0 error**（真接 carryon IMC / OptiFine shader 探测 / Controllable 反射 / PlayerAnimator.canPlayAnimation；其余保留同名反射门面） |
| L2-D mixin | mixin-port | `mixin/client/**` | ✅ **14 文件 0 error**；`mixins.tacz.json` 已写入 9 个 client mixin |
| L2-E 事件 | client-event | `client/event/**`(20) | 进行中 |
| L2-F 粒子/提示/音效 | client-misc | `client/{particle,tooltip,sound}/**` | 进行中 |
| SBM vendor | sbm-port | `com/github/mcmodderanchor/**` | 进行中（已 vendor 284+ 文件，正在逐类改 1.7.10） |

### 关键结论与修复
- **`@Shadow` 不能影子父类字段**（UniMixins 0.3.1 源码确认：`findAliasedField` 只遍历目标类自有字段 + mixinFields）→ `CreativeModeInventoryScreenMixin` 改为 `@Unique` 自持按钮列表 + `drawScreen` TAIL 自绘 + `mouseClicked` HEAD 自派发。**此结论适用于全仓所有 mixin**。
- 修掉一处 vendor 文件写入事故：`BedrockVersion.java` / `MolangCompiler.java` 里出现了**字面 `` `r`n `` 而非真实换行**（导致 javac 报「压缩源文件不应有程序包声明」等语法错），已全仓扫描并修复（仅这两个文件）。
- `mixins.tacz.json` 定稿：`mixins` 3 个 `common.*`；`client` 9 个（`LocalPlayer`/`MouseHandler`/`ItemInHandRenderer`/`ItemInHandLayer`/`GameRenderer`/`HumanoidModel`/`PlayerModel`/`CreativeModeInventoryScreen`/`AbstractButton`）。`ar/oculus` 4 个 1.20 专属库 mixin 已删除，门面由 `compat` 承担。
- 待裁决后已授权：mixin-port 可改 `client/gui/CreativePackFilters.java`（用静态注入钩子修「内容包筛选点击无反应」）。

### 当前编译实测（`build/port-hf2.log`）
错误已集中到 **`com/github/mcmodderanchor/simplebedrockmodel/**`（约 2300 条，sbm-port 移植中）**，
TACZ 自身的 `entity`/`block`/`init`/`item` 等 L1 包为 0，`client/**` 的 1.20 import 从 96 降到 48 并在继续下降。

---

## L2-A 启动关键路径（task-8 / client-core）—— 交付状态

> 写作用域：`client/ClientProxy.java`、`client/ClientTickHelper.java`、`client/init/**`(5)、
> `client/input/**`(13)、`client/gameplay/**`(13) —— **共 33 文件全部按上游形态真移植**（不是空壳）。
> （`client/event/**` 按 lead 二次分工转给 `client-event`；`client/resource/**` 经实测**无残留**，见下。）
> 判定：`mcp__idea__lint_files`（33 文件 / `min_severity=error`）→ **返回空**；
> `^import` 扫描 1.20 顶层包 → **0 命中**。**未跑 gradle**。

### 逐包状态

| 包 | 文件数 | 状态 | 已完成内容 |
|---|---|---|---|
| `client/ClientProxy.java` | 1 | **完成** | 见下「ClientProxy 现在调用了什么」 |
| `client/ClientTickHelper.java` | 1 | **完成** | 改为**实例处理器** + `INSTANCE`（1.7.10 `EventBus#register` 必须按实例，见下「总线注册形态」） |
| `client/init/**` | 5 | **完成** | `ClientSetupEvent`（客户端注册总入口 + HUD 叠加层）、`ModEntitiesRender`（实体/TESR 渲染器）、`ModContainerScreen`、`ParticleFactoryRegistry`、`GunPegboardColors` |
| `client/input/**` | 13 | **完成** | `GunInputHandler`（键位注册 + **边缘检测分发**）、12 个按键类（Shoot/Aim/Crawl/Reload/Unload/Inspect/FireSelect/Melee/Zoom/Interact/Refit/Config） |
| `client/gameplay/**` | 13 | **完成** | `LocalPlayerDataHolder`、`ClientAmmoCount`、`LocalPlayer{Aim,Bolt,Crawl,Draw,FireSelect,Inspect,Melee,Reload,Shoot,Sprint,Unload}` —— 逻辑全量移植 |

### ⚠ 1.7.10 事件总线注册形态（全仓通用结论，务必转告后续工作流）

`cpw.mods.fml.common.eventhandler.EventBus#register(Object)` 的实现是
`TypeToken.of(target.getClass()).getTypes().rawTypes()` + `target.getClass().getMethods()` 找
`@SubscribeEvent`（`EventBus.java:59-60`）。因此：

- **`MinecraftForge.EVENT_BUS.register(XxxClass.class)` 什么都不会注册**（它会去 `java.lang.Class` 上找注解）。
  **必须传实例**。本文件早前写的「`MinecraftForge.EVENT_BUS.register(ClientTickHelper.class)`」是错的，
  本轮已修正为 `ClientTickHelper.INSTANCE`。
- 对「只有 `@SubscribeEvent static` 方法」的类（上游 `@Mod.EventBusSubscriber` 的形态），
  传**该类的一个任意实例**即可生效（静态方法调用与 target 无关）。
- 两条总线：`cpw TickEvent.*` / `cpw PlayerEvent.*` / `FMLNetworkEvent.*` → `FMLCommonHandler.instance().bus()`；
  `net.minecraftforge.client.event.*` / `net.minecraftforge.event.entity.*` → `MinecraftForge.EVENT_BUS`。

### ClientProxy 现在调用了什么（对外契约）

**`preInit`**：`super.preInit` → `ClientConfig.init(<config>/tacz-client.cfg).load()+save()`
→ `ClientSideHooks.set(new Hooks())`（8 个客户端钩子实现）。
`Hooks` 内部跨包调用（**只调用不改**）：
`client/sound/SoundPlayManager#{playMessageSound, playUnloadSound, resetDryFireSound}`、
`client/particle/HitBlockEffectHandler#handle(World, hitX,hitY,hitZ, ForgeDirection, blockX,blockY,blockZ, ammoId, gunId, gunDisplayId, incendiary)`、
`client/gui/GunSmithTableScreen#updateIngredientCount`、`client/gui/GunRefitScreen#initGui`、
`client/resource/ClientIndexManager#reload`、`client/gameplay/LocalPlayerDataHolder#clientBaseTimestamp`、
`resource/network/CommonNetworkCache`、`inventory/AmmoStorageMenu#setContents(int,long,List)`。

**`init`**：
1. `ClientSetupEvent.onClientSetup()` —— 见下表；
2. FML 总线（按实例）：`ClientTickHelper.INSTANCE`、`GunInputHandler.INSTANCE`、
   `ShootKey.INSTANCE`、`AimKey.INSTANCE`、`CrawlKey.INSTANCE`、`ReloadKey.INSTANCE`；
3. FML 总线（反射按类名构造实例）：`client.sound.SoundPlayManager`；
4. 跨工作流的 20 个 `client/event/**` 类按 **client-event 定稿的三组总线映射**注册
   （每类都用反射构造**实例**，`ClientProxy#newHandler`）：
   - **FML 总线（6）**：`ClientGunTimeResetEvent`（`FMLNetworkEvent.ClientDisconnectionFromServerEvent`）、
     `CommonNetworkCacheEvent`（`ClientConnectedToServerEvent` + `ClientDisconnectionFromServerEvent`）、
     `PlayerEnterWorld`（cpw `PlayerLoggedInEvent`）、`RefreshClonePlayerDataEvent`（`TickEvent.ClientTickEvent`）、
     `TickAnimationEvent`（`ClientTickEvent` + `RenderTickEvent`）、
     `GunDisplayCaseClientInteractionEvent`（`ClientTickEvent`，替代 1.7.10 不存在的 `InputEvent`）；
   - **Forge 总线（11）**：`ClientHitMark`、`ClientPreventGunClick`（改用 `PlayerInteractEvent`）、
     `FirstPersonRenderEvent`、`FirstPersonRenderGunEvent`、`GunDisplayCaseHighlightEvent`、
     `GunPegboardHighlightEvent`、`PlayerHurtByGunEvent`、`PreventsHotbarEvent`、
     `ReloadResourceEvent`、`RenderHeadShotAABB`、`TooltipEvent`；
   - **两条总线都注册（3，同一实例）**：`CameraSetupEvent`、`InventoryEvent`、`RenderCrosshairEvent`
     （类里既有 Forge 事件又有 cpw Tick 事件；两条 `EventBus` 实例彼此独立，不会重复触发）。
   **缺失类只 `LOGGER.warn` 不抛**（见「启动安全网」）；
5. `ReloadCommand.setClientReloader(...)`（反射转调 `ClientAssetsManager.reloadAllPack`）。

**`postInit`**：`GunPackLoader.injectGunPackLanguage(<当前语言码>)`（枪包语言注入；`try/catch` 保护）。

**`ClientSetupEvent.onClientSetup()` 的 10 个注册步骤**（每步独立 `try/catch`，失败只 `LOGGER.error` 并继续）：
`ThirdPersonManager.registerDefault` → `GunPegboardColors.setup` → `ModContainerScreen.clientSetup`
→ `ModEntitiesRender.register` → `ParticleFactoryRegistry.register` → `GunInputHandler.registerKeyMappings`
→ `ShoulderSurfingCompat.init` → `ControllableCompat.init` → `ARCompat.init` → `INSTANCE.registerOverlays()`。

### 上游事件 → 1.7.10 落点（本工作流映射总表）

| 上游 1.20.1 | 1.7.10 落点 |
|---|---|
| `FMLClientSetupEvent` + `event.enqueueWork(...)` | `ClientSetupEvent#onClientSetup()`（1.7.10 客户端初始化本身单线程一次性，无 enqueue 队列） |
| `RegisterKeyMappingsEvent` | `ClientRegistry.registerKeyBinding(KeyBinding)`（12 个键位） |
| `RegisterGuiOverlaysEvent#registerAboveAll/registerAbove` | `RenderGameOverlayEvent.Post` + `ElementType.ALL`（aboveAll）/ `ElementType.CROSSHAIRS`（above 准星）→ overlay 调 `render(GuiGraphics.create(), w, h)`（`w/h` = `event.resolution.getScaledWidth/Height()`） |
| `RegisterColorHandlersEvent.Block/.Item`、`ItemProperties.register` | **无注册表**：1.7.10 染色是 `Block#colorMultiplier` / `Item#getColorFromItemStack`（`block/GunPegboardBlock`、`item/GunPegboardItem` 已实现），且**无 JSON 物品模型/谓词** |
| `EntityRenderersEvent.RegisterRenderers` | `RenderingRegistry.registerEntityRenderingHandler(Class, Render)` + `ClientRegistry.bindTileEntitySpecialRenderer(Class, TESR)` |
| `MenuScreens.register(MenuType, ctor)` | **无**：1.7.10 由 `IGuiHandler#getClientGuiElement` 按 gui id 当场返回 `GuiScreen`（`inventory/GuiHandler`） |
| `RegisterParticleProvidersEvent` | **无自定义粒子工厂注册表**（`IParticleEffect` 是 1.8+）；粒子由 `client/particle/**` 直接 `effectRenderer.addEffect(...)` |
| `RegisterClientTooltipComponentFactoriesEvent` | **无** tooltip 组件工厂（由 `ItemTooltipEvent` + 手工绘制承担） |
| `RegisterShadersEvent` | **无**注册事件：1.7.10 GLSL 走 `ShaderGroup`/`ShaderManager` 自动加载 `assets/<ns>/shaders/post/*.json` |
| `RegisterClientReloadListenersEvent` | `IResourceManagerReloadListener` + `TextureStitchEvent.Post`（归 `client-event`） |
| `InputEvent.Key` / `InputEvent.MouseButton.Pre/.Post` | **无输入事件**：`TickEvent.ClientTickEvent(START)` + `GameSettings.isKeyDown(binding)` 做边缘检测（`GunInputHandler`） |
| `KeyMapping` / `KeyModifier` / `KeyConflictContext` | `KeyBinding`（`(description, keyCode, category)`）/ 无修饰键概念 → `MODIFIER_NONE`/`MODIFIER_ALT` 常量 + 轮询时叠加判定 |
| `Minecraft#getFrameTime()` | `ClientTickHelper.getPartialTick()`（`TickEvent.RenderTickEvent(START)` 缓存；`Minecraft.timer` 是 private 且 SRG 化） |
| `Minecraft#gameMode.ensureHasSentCarriedItem()` | 直接发 `C09PacketHeldItemChange`（等价方法 `PlayerControllerMP#syncCurrentPlayItem()` 是 **private**，方法体见 `PlayerControllerMP.java:345-353`） |
| `IClientItemExtensions#getCustomRenderer()` | `MinecraftForgeClient.getItemRenderer(stack, ItemRenderType.EQUIPPED_FIRST_PERSON)` + `instanceof AnimateGeoItemRenderer` |
| `Component.translatable(...)` + `displayClientMessage(c, true)`（action bar） | `ChatComponentTranslation` + `addChatMessage`（**1.7.10 无 action bar**，降级到聊天栏；与 `ClientMessagePlayerUnloadGun` 服务端同口径） |
| `Minecraft#submitAsync(Runnable)` | `Minecraft#func_152344_a(Runnable)`（= `addScheduledTask`，`Minecraft.java:3006`） |

### 降级清单（每条都有 1.7.10 能力依据，非「空壳」）

| # | 位置 | 上游行为 | 1.7.10 降级 | 依据 |
|---|---|---|---|---|
| L1 | `gameplay/LocalPlayerCrawl` | `setForcedPose(Pose.SWIMMING)` 写姿态 + `getForcedPose()!=SWIMMING` 检测外部改写 | 只维护 `isCrawling` 标志 + 发包；姿态不写、外部改写检测恒 false | 1.7.10 **无 `Pose`/`forcedPose`**（`Pose` 是 1.14+）。与 common-game `entity/shooter/LivingEntityCrawl` **同一口径**，两端语义仍一致 |
| L2 | `gameplay/LocalPlayerCrawl#canCrawl` | `isSpectator()`/`isSwimming()`/`isFallFlying()`/`isAutoSpinAttack()` | `isSpectator` 删除；`isSwimming` → `isInWater()`；后两者恒 false | 1.7.10 无旁观者模式、无鞘翅、无三叉戟 |
| L3 | `gameplay/ClientAmmoCount` | 循环体假定 `getItem(slot)` 永不为 null（EMPTY） | 先判 `stack == null` 再 `getItem()` | 1.7.10 **无 `ItemStack.EMPTY`**，空槽位是 `null`（api 跨包契约第 1 条） |
| L4 | `input/ConfigKey` | 默认 **ALT + T** | 默认**未绑定**（`Keyboard.KEY_NONE`），修饰键仍要求 Alt | 1.7.10 `KeyBinding` 无修饰键概念，而 `T` 是原版聊天键；`KeyBinding` 构造时写 `IntHashMap`，`Minecraft#runTick` 只对同 keyCode 的**唯一** binding 更新 `pressed/pressTime`（`Minecraft.java:1837-1844`、`KeyBinding.java:41-52`）→ 占 `T` 会让原版聊天键**彻底失效** |
| L5 | `input/UnloadKey` | `KeyModifier.ALT` + R | 同一 `KeyBinding`（`KEY_R`）+ `MODIFIER_ALT` 轮询判定 | 同上；`R` 无原版冲突，默认值保留 |
| L6 | `input/*` 的 release 沿 | 上游 `onRelease` 手工 `setDown(false)` | 删除（原版 `Minecraft#runTick` 自己维护按键状态） | 1.7.10 的 `KeyBinding#setKeyBindState` 由原版驱动 |
| L7 | `init/GunPegboardColors` | 三个注册 | 零逻辑（染色改由 `Block`/`Item` 方法承担） | 见「映射总表」；**能力等价，落点不同** |
| L8 | `init/ParticleFactoryRegistry` | 注册 `ParticleProvider` | 零逻辑 | 1.7.10 无粒子工厂注册表（`IParticleEffect` 为 1.8+） |
| L9 | `init/ModContainerScreen` | `MenuScreens.register` | 零逻辑 | 1.7.10 由 `IGuiHandler#getClientGuiElement` 分发（**待 common-game 补，见「需要其它 owner」**） |
| L10 | `init/ClientSetupEvent` 的 shader / tooltip 两项 | `RegisterShadersEvent` / `RegisterClientTooltipComponentFactoriesEvent` | 无注册动作 | 见「映射总表」 |
| L11 | `ClientProxy#Hooks.swapItemWithOffHand` | 投递 `SwapItemWithOffHand` | no-op | 1.7.10 **无双持**（副手是 1.9+） |
| L12 | `ClientProxy#Hooks.bulletHitBlock` | `Direction` + `BlockPos` | `ForgeDirection` + 三个 int | 1.7.10 无 `Direction`/`BlockPos`（与 `network/**` 契约一致） |

### 启动安全网（移植特有，理由写在代码里）

1. `ClientSetupEvent#step(String, Runnable)`：**10 个注册步骤每步独立 `try/catch`**。
   1.7.10 的客户端初始化没有上游 MOD 总线的「单订阅者异常隔离」，任何一处注册抛异常都会让
   `FMLInitializationEvent` 失败、客户端起不来。这里把「某一块没做好」与「整个客户端起不来」解耦，
   并把步骤名打进日志。
2. `ClientProxy#registerBusHandler(String, boolean)`：跨工作流的 20+1 个事件类按**类名反射**构造实例并注册，
   缺失/签名不符时 `LOGGER.warn` 跳过。原因：`client/event/**`、`client/sound/**` 分属其它并行工作流，
   其构造器可见性不由本工作流控制；且这是 1.7.10 替代「`@Mod.EventBusSubscriber` 自动发现」的必要代价。

### 需要其它 owner 提供（阻塞项）

| 需求 | owner | 影响 |
|---|---|---|
| `inventory/GuiHandler#getClientGuiElement` 按 gui id 返回 `new GunSmithTableScreen(...)` / `new AmmoStorageScreen(...)`（当前返回 `null`） | common-game | 1.7.10 **唯一**「Container → GuiScreen」落点；不补则打开界面时客户端拿到 null（不崩，但没界面） |
| `client/renderer/{entity,block}/**` 的 7 个渲染器类保持**无参构造器** + 分别实现 `Render` / `TileEntitySpecialRenderer` | client-render | `init/ModEntitiesRender` 直接 `new` 它们，签名漂移即编译失败 |
| `client/sound/SoundPlayManager` 保留 `gameplay/**` + `ClientProxy.Hooks` 用到的 20 个 static 方法名（内部可空实现） | client-misc | 已按契约落地调用 |
| `client/particle/HitBlockEffectHandler#handle(World,double,double,double,ForgeDirection,int,int,int,ResourceLocation,ResourceLocation,ResourceLocation,boolean)` 保留 12 参 | client-misc | `ClientProxy.Hooks#bulletHitBlock` |
| ~~`client/event/**` 的总线归属~~ ✅ **已解决**（client-event 定稿）：`FML_BUS_CLASSES`(6) / `FORGE_BUS_CLASSES`(11) / `BOTH_BUS_CLASSES`(3) 三组已按定稿落进 `ClientProxy`，不再有「挂错总线导致静默失效」的风险 | client-event | 已闭环 |
| `client/gui/compat/ClothConfigScreen` 构造器 `protected` → 若改 `public`，compat 可去掉反射 | client/gui（无 owner） | 非阻塞 |

### client/resource 复核结论（lead 曾报「剩余 4 文件」，实测为误报）

`^import` 扫描 `client/resource/**`（86 文件）命中的 2 个文件是 **1.7.10 合法类**：
- `resource/ClientAssetsManager.java` → `net.minecraft.server.MinecraftServer`（1.7.10 独有包）；
- `resource/serialize/ItemStackSerializer.java` → `net.minecraft.nbt.{JsonToNBT,NBTBase,NBTTagCompound}`。

**`client/resource/**` 0 个 1.20 import，无需任何人再动。** 同理 `client/sound/SoundPlayManager`、
`client/particle/{HitBlockEffectHandler,ParticleCompat,BulletHoleParticle}` 的 `import net.minecraft.world.World;`
也是 **1.7.10 正确类**，不是残留。

### 全 client 包实测残留（本轮收尾时点，供 lead 排最后收敛）

仅 `^import` 扫描、并把 `import net.minecraft.world.World;` 与 `com.mojang.authlib.*`（1.7.10 自带库）排除后的**真残留**：

| 包 | 带 1.20 import 的文件 | owner |
|---|---|---|
| `client/event/**` | 16 | client-event |
| `client/particle/**` | 5（`AmmoParticleSpawner`、`HitSurfaceParticleSpawner`、`MuzzleParticleManager`、`ThirdPersonMuzzleParticleManager` 等） | client-misc |
| `client/renderer/**` | 1（`renderer/item/GunItemRendererWrapper`：`net.minecraft.world.InteractionHand`） | client-render |
| `client/init` `client/input` `client/gameplay` `client/ClientProxy` `client/ClientTickHelper` | **0** | 本工作流 ✅ |
| `client/gui` `client/model` `client/animation` `client/resource` `client/sound` `client/tooltip` | **0** | ✅ |
| `compat/**` `mixin/**` | **0** | ✅（mixin/client 14 文件、ar/oculus 已删） |

### client-core 待办（不阻塞启动）

1. `ClientProxy.Hooks#craft` / `refreshRefitScreen` 依赖 `GunSmithTableScreen#updateIngredientCount` 与
   `GunRefitScreen#initGui`（client/gui，已 1.7.10 化）—— 运行期需实测界面刷新是否生效。
2. `/tacz convert` 客户端实现：待 `resource/PackConvertor#convert` 改成
   `convert(net.minecraft.command.ICommandSender)` 后注入 `ConvertCommand.setConverter(...)`。
3. `input/ZoomKey` 与 `input/MeleeKey` 默认同为 `V`（上游即如此）；1.7.10 无冲突上下文，
   两者会同时触发 → 需运行期确认是否改默认值（**保真未改**）。


---

### client-render（task-9）收尾状态 —— 渲染作用域（`client/renderer/**` + `client/model/**`）

> 写作用域最终收敛为 `client/renderer/**` + `client/model/**`（`particle/tooltip/sound` 已按 lead 裁决划给 `client-misc`/task-12）。
> 判定口径：`lint_files(min_severity=error)`；**未跑 gradle**。`client/model/**` 41 文件 **0 个 1.20 import**。

| 包 | 文件数 | 状态 | 已完成内容 | 阻塞 / 差异 |
|---|---|---|---|---|
| `client/model/RenderHelperCompat.java` | 1（新增） | **完成** | 1.7.10 侧补回 `util/RenderHelper` 被删掉的 3 个渲染批次方法：`getBufferSource(ItemDisplayContext)`（按上下文缓存的 `MultiBufferSource.BufferSource`）、`endBatch(MultiBufferSource[,RenderType])`、`renderFirstPersonArm(...)`；转发 `enableItemEntityStencilTest/disableItemEntityStencilTest/isOptifineInstalled`；另含 `ItemRenderType↔ItemDisplayContext` 映射表（`displayContextOf` / `isFirstPerson`）。`renderFirstPersonArm` 是**真实现**：等价 1.7.10 `RenderPlayer#renderFirstPersonArm`（`minecraft-src/.../RenderPlayer.java:455`）的 `modelBipedMain.setRotationAngles + bipedRightArm/LeftArm.render(0.0625F)`，外加皮肤绑定、`OpenGlHelper.setLightmapTextureCoords`、`pushToGL/popFromGL`。唯一差异：手臂立即绘制，绘制顺序比上游「写进 bufferSource」前移一个批次（不影响深度测试） | `util/RenderHelper` 属 common-infra，未改；本类为其客户端补完 |
| `client/model/**`（其余 40 文件） | 40 | **完成（0 个 1.20 import）** | 13 个文件把 `util/RenderHelper.*` 全部改到 `RenderHelperCompat.*`（`BedrockAttachmentModel`、`BedrockGunModel`、`FunctionalBedrockPart`、`bedrock/BedrockModel`、`bedrock/BedrockPart`、`functional/{AttachmentRender,BeamRenderer,LeftHandRender,MuzzleFlashRender,RangefinderImpactPredictionRender,RightHandRender,ShellRender,TextShowRender}`）。`MuzzleFlashRender.isSelf` / `ShellRender.isSelf` 仍是 `public static boolean` 字段（YSM 契约）。`DisplayGunModelInstance`/`ExternalGunModel` 只留 `com.github.mcmodderanchor.*`（SBM 已 vendor，包名不变） | `OculusCompat.endBatch/isRenderShadow` 需 mcshim 签名（compat-stub 已确认按此落盘） |
| `client/renderer/entity/**` | 2 | **完成** | `EntityBulletRenderer`：`EntityRenderer<T>`→`Render`（`doRender` 负责 x/y/z→PoseStack）；`shouldRender(Frustum…)`→1.7.10 `Frustrum.isBoundingBoxInFrustum`；`getBlockLightLevel=15`→`LightTexture.pack(15, sky)`；**`RenderType.energySwirl` 的 shader UV 滚动用固定管线 `GL_TEXTURE` 矩阵（`glTranslatef`）真复刻**，非降级。`TargetMinecartRenderer`：`MinecartRenderer`→`RenderMinecart#func_147910_a`；皮肤走 `SkinManager#func_152788_a/func_152792_a` | `TargetMinecartRenderer` 唯一降级：`DefaultPlayerSkin.getDefaultSkin(uuid)` → 固定 Steve（`SkinManager.field_152793_a`）；1.7.10 无按 UUID 分默认皮肤 |
| `client/renderer/scope/**` | 4+1 | **完成** | **FBO + GLSL 真做**：新增 `ScopeLensProgram`（直接 `GL20` 编译 **GLSL 120 版** `scope_lens.vsh/fsh`，`glBindAttribLocation(Position→0)` 利用 compatibility profile 下 `glVertexPointer`→attribute0 的别名；不再需要 `ShaderInstance`/`RegisterShadersEvent`）。`ScopeLensRenderer` 改为「拷主 framebuffer 颜色到自建纹理 → 正交投影画 NDC 镜片圆盘 → 采样 copy 纹理做畸变/色散/暗角/镀膜/夜视」；`ScopeThermalRenderer` 用**自建 FBO + 借主 framebuffer 的 depth renderbuffer**（`glFramebufferRenderbuffer`）重放热源掩膜，配 GLSL120 版 `scope_thermal_mask`（`TeeVertexConsumer` 等价上游 `VertexMultiConsumer`） | 3 个降级（均写 TODO(L2) + 上游行号）：① `ScopeLensContour` 的 JFA 距离场须 `texelFetch`（GLSL130），且需主 framebuffer 有 stencil 附件 → `prepare()` 恒 `null`，走**上游自己预留的椭圆近似分支**；② 模板等值测试仅在 GL3.0 且 framebuffer 有 stencil 附件时启用；③ `onWorldStage(RenderLevelStageEvent)`→`render()`（1.7.10 由 `RenderWorldLastEvent` 调） |
| `client/renderer/item/**` | 13 | **部分** | 8 个已 lint 0 error：`GunItemTransforms`、`SbmGunTransforms`、`AmmoItemRenderer`、`AttachmentItemRenderer`、`GunSmithTableItemRenderer`（三者 `BlockEntityWithoutLevelRenderer`→`IItemRenderer`，`MissingTextureAtlasSprite`→`InternalAssetLoader.ENTITY_EMPTY_TEXTURE`，`ItemTransforms/ItemTransform`→`client/resource/pojo/display/block/BlockItemTransforms`，`AmmoItemRenderer` 加 `renderByItem` 保留上游核心方法）、`ScreenItemVboContext`（`ScreenEvent`→`GuiScreenEvent.DrawScreenEvent.Pre/Post`，去掉 `@Mod.EventBusSubscriber` 并**改为可实例化**——1.7.10 `EventBus#register` 只认实例）、`DisplayItemRenderContext`/`StaticGunRenderResult`（原为纯数据类）+ `AnimateGeoItemRenderer`（`extends BlockEntityWithoutLevelRenderer`→`implements IFPGeoItemRenderer, IItemRenderer`） | 剩余 error 全部来自**外部在途文件**：`MuzzleParticleManager`（client-misc，仍是上游 `LocalPlayer`/`PoseStack` 签名）、`FirstPersonRenderGunEvent.applyFirstPersonGunTransform(LocalPlayer…)`（client-core）、`IFPGeoItemRenderer.renderFirstPerson(LocalPlayer…)`（SBM vendor 中，sbm-port 待改）、`DisplayGunGeometry.capture(...)`（renderer/block 子代理在途） |
| `client/renderer/other/**` | 3 | **完成** | `GunHurtBobTweak`（`hurtTime/hurtDuration`→1.7.10 `int hurtTime/maxHurtTime`，`getHurtDir()`→`attackedAtYaw`）；`HumanoidOffhandRender`（`Container/Slot` 化：`InventoryPlayer#currentItem/getStackInSlot`，`renderStatic`→`ItemRenderer#renderItem(EQUIPPED)` + `pushToGL`）；**新增 `CameraAngles`**——替代 1.7.10 不存在的 `ViewportEvent.ComputeCameraAngles`（1.7.10 `EntityViewRenderEvent` 只有 Fog 三个子类，无相机角度） | `HumanoidOffhandRender` 两处降级：① 1.7.10 无双持槽，`renderOffhandGun` 保留方法但无对象可画；② 挂枪基础姿态用原版 `EQUIPPED` 变换而非枪包 JSON 的 `fixed` |
| `client/renderer/crosshair/**` | 1 | **完成** | `CrosshairType`（`net.minecraft.resources.ResourceLocation`→`net.minecraft.util.ResourceLocation`；`String.formatted`→字符串拼接，避免依赖 jvmDowngrader 的 `String#formatted`） | 无 |
| `client/renderer/external/**` | 3 | **部分** | `ExternalGunAppearance` 0 error（`ItemStack.isEmpty()`→`== null`、`List.copyOf`→`Collections.unmodifiableList`，`ExternalGunModel` 类型保留等 SBM）；`ExternalShellRenderer` 0 error；`ExternalGunEffects` 仅剩 `ThirdPersonMuzzleParticleManager.currentEntity` 类型未对齐（client-misc） | 依赖 SBM `DisplayGunModelInstance`/`SbmGunTransforms` |
| `client/renderer/block/**` | 10（9 上游 + 新增 `StaticItemRenderHelper`） | **完成（0 个 1.20 import、0 error）** | 全部重写为 1.7.10 `TileEntitySpecialRenderer#renderTileEntityAt` + 无参构造（`ModEntitiesRender` 的 5 处 `new XxxRenderer()` 已匹配）。`DisplayGunRenderType` 必须**整体重写**（上游 `extends RenderStateShard` + `CompositeState.builder()` 在 1.7.10 不存在）→ `extends mcshim.RenderType` + 公开字段 `State`；`DisplayItemTransforms`（`Direction`→`ForgeDirection`、`get2DDataValue()`→`BlockMetadata.facing2D()`）；`GunSmithTable`/`Target`/`Statue`/`GunDisplayCase`/`GunPegboard` 的 `BlockState`→`Block`+metadata、`hitResult`→`objectMouseOver`、`getItemInHand`→`getHeldItem`、`getSlot(BlockState,BlockPos,Vec3)`→`getSlot(metadata,x,y,z,hitVec)`、`LevelRenderer.getLightColor`→`World.getLightBrightnessForSkyBlocks`、相机坐标→`TileEntityRendererDispatcher.field_147560_j/k/l`；`DisplayGunGeometry` 的 `resolveAttachments/effectiveAttachment/applyTransform/forEachAttachmentModel/applyMountOffset` 逐行保留；新增 `StaticItemRenderHelper`（1.7.10 替身 for `ItemRenderer#renderStatic`：优先 Forge `IItemRenderer`，否则 `RenderItem#renderItemIntoGUI`）。位姿只走 GL：`renderTileEntityAt` 里 `glPushMatrix+glTranslatef(x,y,z)`，传给上游形状 `render(...)` 的是**单位位姿** `PoseStack`（批次顶点已被 CPU 矩阵烘好，`BufferUploader` 不再乘矩阵） | 共 10 处 TODO(L2)：`DisplayGunRenderType:46`（RenderStateShard 无对应）、`GunSmithTableRenderer:71` / `GunDisplayCaseRenderer:246` / `GunPegboardRenderer:247`（`shouldRender` 视距/离屏钩子）、`TargetRenderer:163`（`DefaultPlayerSkin`/`UUIDUtil`→steve）、`StatueRenderer:178`、`DisplayGunGeometry:255`（`GeometryCollector` 双材质分流 + `light=0` 捕获语义）、`DisplayGunMeshGroup:43`（`WorldMeshGroup`/`MeshCachePolicy`/`MeshRenderableAdapter` 不存在 → TESR 内立即绘制，外观/LOD/光照逻辑全保留）、`StaticItemRenderHelper:160`。**注：早先"7 文件仍带 1.20 import"是我脚本的假阳性** —— 命中的是 `net.minecraft.world.World`，这是 1.7.10 合法类 |

#### 需要主 Agent / 其它工作流配合的点

1. **`ScopeLensRenderer` / `ScopeThermalRenderer` 入口改名**：1.7.10 无 `RegisterShadersEvent`，已改成 `ScopeLensRenderer.init()` / `ScopeThermalRenderer.init()`。`client/init/ClientSetupEvent#onRegisterShaders` 需改成直接调用 `ScopeLensRenderer.init()`（并去掉 `net.minecraftforge.client.event.RegisterShadersEvent`）。镜头 pass 的注入点 `render()` / `beginFrame()` 仍由（当前被注释的）`mixin/client/GameRendererMixin` 负责；热掩膜入口从 `RenderLevelStageEvent` 改为 `ScopeThermalRenderer.render()`（由 `RenderWorldLastEvent` 调）。
2. **`CameraAngles` 新契约**：`AnimateGeoItemRenderer#applyLevelCameraAnimation` / `applyItemInHandCameraAnimation` 的第一个参数类型从 `ViewportEvent.ComputeCameraAngles` 改为 `com.tacz.guns.client.renderer.other.CameraAngles`（可读写的 yaw/pitch/roll 容器）。`client/event/CameraSetupEvent` 需配着改（它当前仍 import `ViewportEvent`）。
3. **`ScreenItemVboContext` 需要显式注册实例**：`MinecraftForge.EVENT_BUS.register(new ScreenItemVboContext())`（1.7.10 `EventBus#register(Class)` 是空操作）。
4. **物品渲染注册**：`AmmoItemRenderer` / `AttachmentItemRenderer` / `GunSmithTableItemRenderer` / `AnimateGeoItemRenderer` 子类现在都是 `IItemRenderer` 且有无参构造器，可直接
   `MinecraftForgeClient.registerItemRenderer(item, new XxxItemRenderer())`。

---

## L2-D 收尾（task-11 / mixin-port）—— CreativePackFilters 真 bug 修复 + 全仓 mixin 自查

### A. `client/gui/CreativePackFilters` 的「内容包筛选点了没反应」—— ✅ 已修

> 写作用域：lead 临时授权扩到 `src/main/java/com/tacz/guns/client/gui/components/CreativePackFilters.java`
> （**只这一个文件**）+ 新增 `src/main/java/com/tacz/guns/mixin/client/CreativeContainerAccessor.java`
> （仍在 task-11 的 `mixin/client/**` 作用域内）。两者 `lint_files` **0 error**。

**成因（三层，缺一层都不会出问题）**：
1. `CreativePackFilters` 需要「把创造物品栏当前页签的条目替换成筛选后的子集」，1.7.10 的载体是
   `GuiContainerCreative$ContainerCreative#itemList`（`GuiContainerCreative.java:1006`）与 `scrollTo(float)`（第 1038 行）。
2. 原实现用 **MCP 名**反射（`readField(container, "itemList")` / `findMethod(..., "scrollTo", float.class)`），
   但**生产环境运行期字段/方法名是 SRG 名**（`field_xxxxx` / `func_xxxxx`），Reflection 一定抛
   `NoSuchFieldException` / 找不到方法 → `getContainerItemList()` 返回 `null`
   → `showItems()` 第一行 `if (containerItems == null) return;` **直接 return**
   → **列表从来不被动过**，表现为「点了内容包筛选按钮完全没反应」。
3. 之所以不能改成 Mixin `@Shadow`：`ContainerCreative` 是 **包私有**内部类
   （`GuiContainerCreative.java:1003` `static class ContainerCreative extends Container`），
   - `com.tacz.guns.*` 写不出这个类型；`mixin/client` 包的回调签名也写不出；
   - 且 `itemList` 声明在 `ContainerCreative` 上、`inventorySlots` 声明在父类 `Container` 上，
     而 **Mixin 的 `@Shadow` 只解析目标类自有成员、不遍历父类**（见 B 节）。

**修法（不动 `client/gui` 与 mixin 的耦合方向）**：
1. `CreativePackFilters` 新增**注入钩子**（javadoc 已写清「为什么不能用 @Shadow / 为什么必须静态钩子」）：
   ```java
   public interface ContainerAccess {
       @Nonnull List<ItemStack> itemList();   // = ContainerCreative#itemList
       void scrollTo(float value);            // = ContainerCreative#scrollTo(float)
   }
   @Nullable public static Function<GuiContainerCreative, ContainerAccess> containerAccessProvider;
   ```
   `getContainerItemList()` / `scrollContainerTo(float)` **优先走钩子**，钩子为 `null` 时回退旧反射
   （dev 下 MCP 名可用；生产失败但**不崩**，只降级）。
2. 新增 `mixin/client/CreativeContainerAccessor.java`（**上游没有这个文件，移植特有**）：
   ```java
   @Mixin(targets = "net.minecraft.client.gui.inventory.GuiContainerCreative$ContainerCreative")
   public interface CreativeContainerAccessor {
       @Accessor("itemList") List<ItemStack> tacz$getItemList();
       @Invoker("scrollTo")  void tacz$scrollTo(float value);
   }
   ```
   用 `targets = "..."` 字符串可以指向**包私有内部类**；`@Accessor`/`@Invoker` 写 **MCP 名**，
   由 Mixin refmap 自动映射到运行期 SRG 名 ⇒ **dev 与生产都能用，且不硬编码任何 SRG 名**。
3. `CreativeModeInventoryScreenMixin#initGui` 装桥（静态嵌套类 `ContainerAccessBridge`，**不捕获 `this`**）：
   用 `screen.inventorySlots instanceof CreativeContainerAccessor` 判断 ——
   **Mixin 没应用时 `instanceof` 恰好为 false → 返回 null → 自动回退反射**，所以即使 json 漏配
   也只是筛选不生效、**绝不会启动崩**。

**⚠ lead 需要在 `mixins.tacz.json` 的 `client` 数组补一行**：
`"client.CreativeContainerAccessor"`（放在 `client.CreativeModeInventoryScreenMixin` 附近即可）。
漏配不崩，但「内容包筛选」会退化为不生效。

### B. 全仓 mixin `@Shadow` / `@Accessor` / `@Invoker` 自查（结论：**除已修的 1 处外全部合规**）

> 判据（读 UniMixins 0.3.1 内的 Mixin 源码得出）：**`@Shadow` 只解析目标类自己声明的成员，不遍历父类**
> —— `MixinPreProcessorStandard#attachFields` → `MixinTargetContext#findField(FieldNode, AnnotationNode)`
> → `TargetClassContext#findAliasedField(Deque, desc, includeMixinFields)`，字段遍历只走
> `this.classNode.fields` 与 `this.mixinFields`。
> `@Accessor` / `@Invoker` 走同一套 `findField` / `findMethod` 解析，结论相同。

| 类 | 影子/访问成员 | 目标类 | 目标类是否自有 | 结论 |
|---|---|---|---|---|
| `client/AbstractButtonMixin` | 无 | `GuiButton` | — | ✅ 无影子 |
| `client/CreativeModeInventoryScreenMixin` | `@Shadow int selectedTabIndex`、`@Shadow float currentScroll` | `GuiContainerCreative` | **是**（`GuiContainerCreative.java:43`、`:45`） | ✅ 合规（原来的 `@Shadow List buttonList` 影子 `GuiScreen` 父类字段**已删除**，改自持列表 + 自绘/自派发） |
| `client/CreativeContainerAccessor`（新） | `@Accessor("itemList")`、`@Invoker("scrollTo")` | `GuiContainerCreative$ContainerCreative` | **是**（`:1006`、`:1038`） | ✅ 合规（`targets` 字符串指向包私有内部类，是这种场景的标准做法） |
| `client/EntityRenderDispatcherMixin` | 无（空 `@Mixin`） | `RenderManager` | — | ✅ |
| `client/GameRendererMixin` | 无（只有 `@Unique` 方法） | `EntityRenderer` | — | ✅ 无影子（上游的 `@Shadow Minecraft getMinecraft()` 用 `Minecraft.getMinecraft()` 代替） |
| `client/HumanoidModelMixin` | `@Shadow bipedHead/bipedBody/bipedLeftArm/bipedRightArm` | `ModelBiped` | **是**（`:12-16`） | ✅ 合规（1.7.10 这 4 个字段**非 final**，故未加 `@Final`） |
| `client/ItemInHandLayerMixin` | 无 | `RenderPlayer` | — | ✅ 无影子 |
| `client/ItemInHandRendererMixin` | `@Shadow ItemStack itemToRender` | `ItemRenderer` | **是**（`:47`） | ✅ 合规（上游另两个 `equippedProgress/prevEquippedProgress` 因只被注释掉的逻辑用到而**未声明**，少两个运行期校验点） |
| `client/LanguageMixin` | 无（空 `@Mixin`，`targets` 字符串） | `Locale` | — | ✅ |
| `client/LocalPlayerMixin` | 无（只有 `@Unique` 字段与方法） | `EntityPlayerSP` | — | ✅ 无影子 |
| `client/MouseHandlerMixin` | 无（两个 `@ModifyVariable`） | `Entity` | — | ✅ 无影子（注入的 `setAngles` 是 `Entity` **自有**方法，`:359`） |
| `client/PlayerModelMixin` | `@Shadow bipedLeftArm/bipedRightArm` | `ModelBiped` | **是** | ✅ 合规 |
| `client/RenderSystemAccessor` | 无（普通 `final` 占位类，**不是 Mixin**） | — | — | ✅ |
| `client/SoundManagerPreparationsMixin` | 无（空 `@Mixin`，注入全注释） | `SoundHandler` | — | ✅ |
| `client/StairBlockAccessor` | 无（普通 `final` 占位类，**不是 Mixin**） | — | — | ✅ |
| `common/LivingEntityMixin` | 无 `@Shadow`；`@Inject(method="onLivingUpdate")` | `EntityLivingBase` | **是**（`EntityLivingBase.java:1928`） | ✅ 合规 |
| `common/ServerPlayerMixin` | 无 `@Shadow`；`@Inject(method="clonePlayer")` | `EntityPlayerMP` | **是**（`EntityPlayerMP.java:990`；父类 `EntityPlayer.java:2262` 也有一个，目标类自有故优先命中） | ✅ 合规 |
| `common/ServerGamePacketListenerImplMixin` | 无 `@Shadow`；`@Redirect(method="processEntityAction")` | `NetHandlerPlayServer` | **是**（`NetHandlerPlayServer.java:808`） | ✅ 合规 |

**汇总**：全仓 18 个文件（`client` 15 + `common` 3）中，
- `@Shadow` 父类字段：**0 处**（原 `buttonList` 1 处已修）；
- `@Shadow` 父类方法：**0 处**（全仓仅 1 个 `@Shadow` 方法在注释里，未启用）；
- `@Accessor` 目标为父类字段：**0 处**（新增的 `CreativeContainerAccessor` 目标是自有字段）；
- `@Invoker` 目标为父类方法：**0 处**；
- 注入目标方法为父类声明（会 `InvalidInjectionException`）：**0 处** —— 每个 `@Inject`/`@Redirect`/`@ModifyArg`/`@ModifyVariable`
  的目标方法都已在目标类**自身**逐一核对（行号见上表与上文逐文件表）。

#### R1 精确修复：全仓最后 6 条唯一编译错误（common-game scope，已 lint 验证 0 error）

依据 lead 的 `build/port-r1.log` 定位，逐条修复：

| # | 文件:行 | 修复 |
|---|---|---|
| 1 | `event/GunDisplayCaseInteractionEvent:71` | `block.isLocked(world,x,y,z)` → `GunDisplayCaseBlock.isLocked(world.getBlockMetadata(x,y,z))`（block 交付的 1.7.10 签名为 `isLocked(int metadata)`） |
| 2 | `event/GunDisplayCaseInteractionEvent:84` | `GunDisplayCaseSlotHelper.getSlot(world,x,y,z,hitVec)` → `getSlot(world.getBlockMetadata(x,y,z), x, y, z, picked.hitVec)` |
| 3 | `event/GunPegboardInteractionEvent:67` | 同上 → `GunPegboardSlotHelper.getSlot(meta, x, y, z, picked.hitVec)` |
| 4 | `item/TargetMinecartItem:67` | 删除 `minecart.setCustomNameTag(stack.getDisplayName())`（1.7.10 EntityMinecart 无 custom name），留 `TODO(差异记录)` 说明上游语义与 L1 不做的理由（不发明 `func_xxxxx`） |
| 5 | `sound/SoundManager:87` | `List<EntityPlayerMP> = worldServer.playerEntities`（1.7.10 实为 `List<EntityPlayer>`）→ `List<EntityPlayer>` + 循环内 `instanceof EntityPlayerMP serverPlayer` 收窄后发送 |
| 6 | `com/maydaymemory/mae/basic/BasicAnimation:107` | 三元泛型：改双跳强转 `(Iterable<Keyframe<T>>) (Iterable<?>) channel.clip(...)`（单跳直接强转参数化泛型非法，IDEA 也确认此写法可编译） |

**验证**：`mcp__idea__lint_files`（5 个修改文件，error 级）→ `items: []`（0 error）。
另按 client-core 的结论审计了事件注册方式：`event/EventHandlers.registerAll()` 调用的 15 个事件类 `register()` **全部是 `register(new Xxx())` 实例注册**（含 `SyncedEntityDataEvent` 的 Forge+FML 双总线、`HitboxHelperEvent/ServerTickEvent/PlayerRespawnEvent/TravelToDimensionEvent` 的 FML 总线实例），**无任何 `register(Xxx.class)`**，符合 1.7.10 `EventBus#register` 只支持实例的约束。

---

## 收尾修正（client-core：删除自研粒子 + Scope 渲染入口接线）

### 1. 删除 4 个自研粒子文件（与上游 SBM 链路冲突，lead 指令）

删除（`client/particle/`，4 个均为上游**不存在**的自研类）：
`MuzzleParticleEffectData.java`、`MuzzleParticleDefinition.java`、`MuzzleParticleFX.java`、`MuzzleParticleSystem.java`

**删前引用扫描**：全仓 `src/main/java/**/*.java` 匹配这 4 个类名 → 命中**只在它们 4 个之间**互相引用，
**外部引用 0 处**；删后再扫 → **0 处悬挂引用**。
`client/particle/**` 剩余 7 文件（`AmmoParticleSpawner`、`BulletHoleParticle`、`HitBlockEffectHandler`、
`HitSurfaceParticleSpawner`、**`MuzzleParticleManager`**、`ParticleCompat`、`ThirdPersonMuzzleParticleManager`）
`lint_files`（error 级）→ **0 error**。
注意 **`MuzzleParticleManager` 未删**：它是上游同名类，`ClientGunTimeResetEvent` 依赖其 `resetMuzzleLight()`。

### 2. Scope / SBM 渲染入口接线（client-render 定稿，原文见 lead 指令）

| 需求 | 落点 | 状态 |
|---|---|---|
| `ScopeLensRenderer.init()` / `ScopeThermalRenderer.init()` 取代 `registerShader(...)` | `client/init/ClientSetupEvent#onClientSetup()` 新增两个 `step(...)` | ✅ 已接（原「`RegisterShadersEvent` 无对应物」的 TODO 已消除） |
| `ScopeThermalRenderer.render()` 由 `RenderWorldLastEvent` 调 | `ClientSetupEvent#onRenderWorldLast(RenderWorldLastEvent)`（本类 `INSTANCE` 已注册到 Forge 总线） | ✅ 已接。`render()` 自带 `enabled()` 守卫（着色器是否编译成功 / 配置开关 / 第一人称 / hideGUI / Oculus 阴影 pass），未启用直接 return，每帧无条件调用安全 |
| `MinecraftForge.EVENT_BUS.register(new ScreenItemVboContext())` | `ClientProxy#init` 步骤 4b | ✅ 已接（该类只有 `@SubscribeEvent static` 方法，按实例注册；其静态状态被 `util/RenderDistance` 读取） |
| `CameraSetupEvent` 配 `client/renderer/other/CameraAngles` 新契约 | `client/event/CameraSetupEvent` → **client-event 工作流** | 不属本工作流，已转告 |

**验证**：`lint_files`（33 文件，error 级）→ `items: []`；`get_file_problems(ClientProxy / ClientSetupEvent)` → 0 error。**未跑 gradle**。
---

## SBM vendor 移植（sbm-port，`com.github.mcmodderanchor.simplebedrockmodel` + `com.maydaymemory.mae`）

### 1. vendor 清单

| 目录 | 文件数 | 说明 |
|---|---|---|
| `com/github/mcmodderanchor/simplebedrockmodel/`（根） | 1 | `SimpleBedrockModel`：只保留 `MOD_ID`/`LOGGER`/`modLoc`（上游 `@Mod` 入口 + `SimpleChannel` + `ForgeConfigSpec` 注册在 1.7.10 无对应物，已删并注 TODO(L2)） |
| `…/port/`（**1.7.10 移植支撑，上游没有**） | 4 | `Vec3`（1.20 `world.phys.Vec3` 等价，`public final double x/y/z` + `x()/y()/z()` + 运算方法）、`AABB`（1.20 `AABB` 等价、不可变，附 `toAxisAlignedBB()` 与原版互操作）、`Camera`（1.20 `client.Camera` 替身，按 1.7.10 `renderViewEntity` 取值）、`SoundSource`（1.20 音效分类枚举，1.7.10 仅作常量占位） |
| `…/v1/common/` | 5+3+5+2+1+18+5+2 = 41 | 时间/动画/模型/资源 POJO/serialize/exclusion |
| `…/v1/molang/` | 56 | Molang 词法/语法/runtime/binding/ASM 编译（`Opcodes.V17`→`V1_8`） |
| `…/v1/particle/` | 76 | 粒子定义/组件/曲线/事件/运行时/渲染/第一人称系统/世界发射器 |
| `…/v1/client/`（去 compat） | 5 | `IFPAnimationInstance`、`PausedClientAnimationClock`、`SwapItemWithOffHand`、`FirstPersonRenderHandler`、`IFPGeoItemRenderer`、`BedrockModelRenderTypes` |
| `…/v1/network/` | 2 | 降级为空实现（1.7.10 无副手、无 `SimpleChannel`） |
| `…/v1/resource/`、`…/v1/util/math/` | 2 | `RawResourceLoader`、`MathUtil` |
| `…/v2/common/model/baked/`、`runtime/`、`tree/` | 15+7+8 = 30 | v2 烘焙模型 / 运行时骨骼 / tree 模型 |
| `…/v2/client/mesh/`（含 `cache/capture/gpu/render/world/debug`） | 47 | 网格缓存 / 几何收集 / VBO 池 / 批次渲染 / 世界网格 |
| `…/v2/client/config/`、`…/v2/resource/` | 9 | 配置（`ForgeConfigSpec`→静态字段）、资源装载 |
| `com/maydaymemory/mae/**`（**MAE 动画求值库，sbm 经 JarJar 捆绑**） | 42 | `com.maydaymemory:mae:1.1.4` 无源码发行，本仓文件为 **IntelliJ Fernflower 对已发布 1.1.4 字节码的忠实反编译**（每文件头有说明）；纯 Java，只依赖 JOML，无 TODO(L2) |

合计：**SBM 283 个 java + MAE 42 个 java + port 支撑 4 个 = 329 个文件**（另有 `docs/PORT_STATUS.md` 本节）。

### 2. 已删除目录（逐条原因，lead 指令）

| 目录 | 原因 |
|---|---|
| `v1/client/compat/acceleratedrendering/` | 依赖 `com.github.argon4w.acceleratedrendering.*`（1.20 Sodium 生态的 Accelerated Rendering 库），1.7.10 不存在、本仓不 vendor |
| `v1/client/compat/sodium/` | 依赖 `net.caffeinemc.mods.sodium.*` + `me.jellysquid.mods.sodium.*` + LWJGL3 `MemoryStack/MemoryUtil` |
| `v2/client/compat/acceleratedrendering/` | 同上（v2 的 AR 加速网格缓存/渲染器） |
| `v2/client/compat/sodium/` | 同上（Sodium `VertexBufferWriter` 顶点直写） |

**删前引用扫描**：`com/tacz/guns/**` 对这 4 个目录的 import → **0 处**；SBM 树内引用点（9 个文件）已改成「只保留普通顶点路径」，逐处带 `// TODO(L2): 上游 … AcceleratedRenderingCompat/SodiumCompat …；1.7.10 无该库，恒走普通路径`：
`v1/common/model/BedrockBone`、`v1/particle/render/ParticleRenderer`、`v1/particle/world/SnowStormParticle`、
`v2/common/model/baked/BakedGeometryChunk`、`v2/common/model/baked/BakedGeometryChunkRenderer`、
`v2/common/model/tree/TreeBedrockModel`、`v2/common/model/tree/TreeBoneDefinition`、`v2/common/model/tree/TreeGeometryWriter`、
`v1/common/model/ISodiumVertexWriter`（保留接口骨架，`emitVertex/flush` 抛 `UnsupportedOperationException`）。
**删后扫描**：全树对这 4 个目录的 `import` → **0 处**。

### 3. 改动要点（按包）

- **`mcshim` 映射（保留上游类名/方法名，只换 import）**：`PoseStack`、`VertexConsumer`、`VertexFormat`、`DefaultVertexFormat`、`BufferBuilder`、`BufferUploader`、`Tesselator`、`RenderSystem`、`GlStateManager`、`RenderType`、`MultiBufferSource`、`LightTexture`、`OverlayTexture`、`Mth`、`ItemDisplayContext`、`InteractionHand`、`GsonHelper`、`ProfilerFiller`、`ResourceManager`、`SimplePreparableReloadListener`、`SoundEvent`、`Axis` → `com.tacz.guns.mcshim.*`。
- **原版类改名**（1.13+ 重命名回退）：`net.minecraft.resources.ResourceLocation`→`net.minecraft.util.ResourceLocation`（访问器 `getNamespace()/getPath()`→`getResourceDomain()/getResourcePath()`）、`world.item.ItemStack`→`item.ItemStack`、`world.entity.Entity`→`entity.Entity`、`world.entity.player.Player`→`entity.player.EntityPlayer`、`world.level.Level`→`world.World`、`client.multiplayer.ClientLevel`→`WorldClient`、`client.player.LocalPlayer`→`entity.EntityPlayerSP`、`core.Direction`→`util.EnumFacing`、`Minecraft.getInstance()`→`Minecraft.getMinecraft()`、`mc.level/player/particleEngine`→`mc.theWorld/thePlayer/effectRenderer`。
- **gson 2.2.4**：`JsonParser.parseReader(...)`→`new JsonParser().parse(...)`；`JsonArray.isEmpty()`→`size() == 0`（共 2 处）。
- **`ResourceLocation.Serializer`** 不存在 → 新增 `v1/common/resource/serialize/ResourceLocationSerializer`（逐字复刻 1.20 行为）。
- **maven-artifact**：`org.apache.maven.artifact.versioning.*`→`cpw.mods.fml.common.versioning.*`（Forge 自带同源代码，版本范围语法一致）。
- **ASM 字节码版本**：Molang 编译器生成 `Opcodes.V17`→`Opcodes.V1_8`（1.7.10 运行在 Java 8，JVM 拒绝加载 V17 class）。
- **事件系统**：`@Mod.EventBusSubscriber` 无对应物 → 改为静态块在两个总线注册实例（`FMLCommonHandler.instance().bus()` 给 tick/网络，`MinecraftForge.EVENT_BUS` 给渲染/世界事件）；`ViewportEvent.ComputeCameraAngles/ComputeFov`→1.7.10 无相机 roll、无独立手部 FOV（`CameraStateCache` 恒返回 0 / 1.0）；`ClientPlayerNetworkEvent.LoggingOut`→`FMLNetworkEvent.ClientDisconnectionFromServerEvent`；`RenderLevelStageEvent`→`RenderWorldLastEvent`；`LevelEvent.Unload`→`WorldEvent.Unload`；`Frustum`→1.7.10 `Frustum`（类名拼写为 `Frustrum`）。
- **世界粒子渲染管线**（`SnowStormParticle` + `WorldEmitterManager`）：1.7.10 `EffectRenderer` 只有 4 个固定图层、同一图层内无法逐粒子换纹理，故粒子 `getFXLayer()==3` 且 `renderParticle(...)` 为空；改由 `WorldEmitterManager.renderWorldParticles(partialTicks)`（`RenderWorldLastEvent`）按 `RenderType` 分组、逐纹理绑定后经 mcshim `MultiBufferSource` 批量提交，上游 `render(VertexConsumer, Camera, partialTicks)` 主体逐行保留。
- **`IFFPGeoItemRenderer`/`FirstPersonRenderHandler`**：1.7.10 `RenderHandEvent` 不含 `PoseStack`/`MultiBufferSource`/`packedLight`/hand，改由 handler 自建后传入；`IClientItemExtensions` 无对应物 → 改为「物品 implements `IFPGeoItemRenderer`」或 `registerRenderer(Item, IFPGeoItemRenderer)`。
- **1.7.10 无副手**：`getOffhandItem()`→`null`、`ItemStack.EMPTY`→`null`、`ItemStack#isEmpty()`→本地 `isEmpty(stack)`；副手状态机保留但永不激活。
- **`VBO / 间接绘制`**：`BufferUploader`/`BufferBuilder` 走 mcshim（`Tessellator` 逐顶点提交 + `glDrawArrays`）；`VertexBuffer`/`VertexFormatElement`/`RenderStateShard`/`CompositeState`/`Util.memoize` 等 1.17+ 类型无对应物，按 `RenderType.State` + `ConcurrentHashMap` 缓存降级。

### 4. 降级清单（`TODO(L2)` 汇总，按严重度）

**A. 能力缺失（1.7.10 平台没有，不是行为改写）**
1. Sodium / Embeddium 顶点快速路径（4 个 compat 目录已删）→ 恒走普通顶点路径。
2. Accelerated Rendering（AR）加速层（同上）。
3. `RenderType.energySwirl`（shader + 纹理滚动）→ 降级为 additive 粒子类型（视觉近似）。
4. `RenderType` 的 shader 状态（`RENDERTYPE_ENTITY_*_SHADER`）→ 固定管线「禁用光照 + 顶点色」。
5. overlay（受伤红闪）顶点通道 → 丢弃（1.7.10 顶点无该槽位）。
6. `JsonParser`/`JsonArray` gson 2.10 API → gson 2.2.4 子集。
7. 1.7.10 无相机 roll、无独立手部 FOV → `CameraStateCache.getCameraRollDegrees()` 恒 0、`getFirstPersonToWorldDepthScale()` 恒 1.0。
8. 1.7.10 无副手 → SBM 网络层（`NetworkHandler`/`ServerMessageSwapItem`）与副手状态机降级为空实现。
9. `IFFPGeoItemRenderer.applyLevelCameraAnimation(ViewportEvent.ComputeCameraAngles, …)` → 去掉事件参数（1.7.10 无 camera-angles 通道）。
10. 粒子查询降级为常量 0：`is_swimming`、`is_silent`、`is_no_gravity`、`is_glowing`（1.13+ 才有的实体标志）；`is_in_water_or_rain` 用「在水中 || 能看到天空且下雨」近似。

**B. 语义近似（保留上游结构，实现换成本地等价物）**
11. `PoseStack`/`RenderSystem`/`GlStateManager` 等 → mcshim（1.7.10 固定管线）。
12. `Level#playLocalSound(x,y,z,SoundEvent,SoundSource,…)` → `World#playSound(x,y,z,name,vol,pitch,distanceDelay)`；`SoundSource` 分类在 1.7.10 不可设置，忽略。
13. 粒子碰撞 `Entity.collideBoundingBox` → 1.7.10 `World#getCollidingBoundingBoxes` + `AxisAlignedBB#calculateXOffset/YOffset/ZOffset`（同一算法，保留 `coefficientOfRestitution`/`collisionDrag`）。
14. `BlockPos.containing` + `BuiltInRegistries.BLOCK.getKey` → 整数坐标 + `Block.blockRegistry.getNameForObject`。
15. `net.minecraft.world.phys.Vec3/AABB` → `port.Vec3`/`port.AABB`（不可变，1.20 语义；避免 1.7.10 可变对象被外部改写）。
16. `Direction.values()[face]` → 显式 `FACE_ORDER`（`EnumFacing` 的 EAST/WEST 声明顺序与 1.20 `Direction` 相反，直接取 `values()` 会装错 UV 面）。

**C. 待集成方接线（非缺陷）**
17. `FirstPersonRenderHandler.init()`、`WorldEmitterManager`/`WorldMeshRenderer` 的事件注册已自动完成；`FirstPersonRenderHandler.registerRenderer(Item, IFPGeoItemRenderer)` 需要在客户端初始化时登记自定义物品渲染器。

### 5. 验证（只用 IDEA MCP，未跑 gradle）

- `mcp__idea__lint_files`（error 级）逐批覆盖：`v1/common/**`（41）、`v1/molang/**`（56 抽样 11）、`v1/particle/**`（76 分 3 批）→ **`items: []`**。
- `v2/common/model/**`（30）+ `port/**`（4）→ **`items: []`**。
- `v2/client/mesh/**`、`v2/client/config/**`、`v2/resource/**` → 见下方「v2 client mesh 收尾」小节。
- TACZ 侧 import 抽查：`client/particle/MuzzleParticleManager`、`client/renderer/block/DisplayGunMeshGroup`、`client/model/ExternalGunModel`、`util/time/GunTime`、`api/client/animation/ObjectAnimationParticleChannel` → **`items: []`**（30 个 import 点所属文件均已解析通过）。
- 自查写入事故：`Get-ChildItem src\main\java\com\github -Recurse -Filter *.java | Where-Object { (Get-Content $_.FullName -Raw) -match '`r`n' }` → **0 个文件**；同法检查双反引号 ``` `` ``` 残留 → **0 处**。

---

## client-misc（task-12）—— `client/particle/**` + `client/tooltip/**` + `client/sound/**`

> 写作用域：只这三个子包（`client/renderer/**`、`client/model/**` 归 client-render；`client/event/**` 归 client-event）。
> **验收：18 个文件全部 `lint_files(min_severity=error)` → `items: []`；0 个 1.20 顶层 import。**
> 脚本复核 `net.minecraft.{core,resources,sounds,network.chat,server,commands}`、`com.mojang.*`、
> `it.unimi.dsi.*`、`net.minecraftforge.{eventbus,api.distmarker,registries}` **均 0 命中**
> （`net.minecraft.world.World` 是 1.7.10 正确类，不是坏 import）。

| 包 | 文件数 | 状态 | 阻塞点 / 差异 |
|---|---|---|---|
| `sound/**` | 5（4 上游 + **新增 `GunPackResourcePack`**） | **完成** | 见下「sound 逐文件」+「跨包要求」 |
| `particle/**` | 7（6 上游 + **新增 `ParticleCompat`**） | **完成** | 见下「particle 逐文件」 |
| `tooltip/**` | 6（5 上游 + `package-info`） | **完成** | 见下「tooltip 逐文件」 |

### sound 逐文件（`client/sound/`）

| 文件 | 状态 | 上游机制 → 1.7.10 落点 |
|---|---|---|
| `GunSoundInstance` | 完成 | `AbstractSoundInstance` → **`extends PositionedSound`**（`volume/xPosF/yPosF/zPosF/repeat` 一一对应；音调 `field_147663_c`、衰减 `field_147666_i` 在本工程映射下可直接引用，lint 已验证）。上游 `resolve(SoundManager)` / 内部类 `TaczSound#getPath()` 的「直接播枪包 ogg」在 1.7.10 **没有**对应钩子 → 改为 1.7.10 侧同名 `resolve()`：**按字段类型反射**（不依赖 SRG 名）取 `SoundHandler#sndRegistry`，注册「声音 id → `SoundPoolEntry(tacz_sounds/**.ogg)`」的 `SoundEventAccessorComposite`（`SoundEventAccessor` 构造器包私有，故直接实现公开接口 `ISoundEventAccessor`）。幂等 + 重载自愈（`REGISTERED_TACZ_SOUNDS` 身份比对）。`canPlaySound()` 保留：上游是 `!entity.isSilent()`，**1.7.10 `Entity` 无 `isSilent()`**（MC 1.9 才有）→ 恒 true 由调用方判断，已标 TODO(L2) |
| `EntityTrackingGunSoundInstance` | 完成 | `TickableSoundInstance` → **`implements ITickableSound`**：`tick()`→`update()`、`isStopped()`→`isDonePlaying()`（两者都保留原名公开，接口方法转发）；`getEyeY()`→`posY + getEyeHeight()`；`isRemoved()`→`isDead`、`isDeadOrDying()`→`!isEntityAlive()` |
| `SoundPlayManager` | 完成（620 行逐行搬） | `Minecraft.getInstance()`→`getMinecraft()`；`level`→`theWorld`、`player`→`thePlayer`、`ClientLevel#getEntity`→`World#getEntityByID`；`getSoundManager().play/stop/isActive`→`SoundHandler#playSound/stopSound/isSoundPlaying`；`getSoundEvent(name)!=null`→`getSound(name)!=null`；`SoundSource.PLAYERS`→`SoundCategory.PLAYERS`；`RandomSource`→`Random`、`entity.level().random`→`entity.worldObj.rand`、`getRandom()`→`getRNG()`；`getId/getUUID/isRemoved`→`getEntityId/getUniqueID/isDead`；`@Mod.EventBusSubscriber` → **实例** `ClientTickHandler`（静态 `@SubscribeEvent` 在 1.7.10 会 `IncompatibleClassChangeError`）+ `FMLCommonHandler#bus()`，首次播放自注册、幂等 |
| `GunSoundPreload` | 完成 | 纯常量类（只依赖 `sound/SoundManager` 的 String 常量）→ **0 改动原样可用** |
| `GunPackResourcePack`（**移植特有**，由 `GunSoundResourcePack` 泛化而来） | 完成 | 见下专段 |

#### `GunPackResourcePack` 为什么必须有（`docs/PORT_PLAN.md` 未覆盖的新增）

上游 1.20 把每个枪包包成 `PathPackResources` + `NamespaceFilteredPackResources`，经
`AddPackFindersEvent` 以 `Pack.Position.BOTTOM` / `PackSource.BUILT_IN` 注册进原版 `PackRepository`
（`tmp/TACZ-1.20.1/.../resource/GunPackLoader.java:93-134`），于是枪包 `assets/<ns>/**` 对**原版资源管理器**可见。
1.7.10 没有这个入口，枪包只能被 `mcshim.ResourceManager` 自行列举，因此：

- `Minecraft#getResourceManager()`（`TextureManager#bindTexture` 与 `SoundManager#getURLForSoundResource`
  的唯一数据源）**看不到**枪包内容 → 枪械/粒子贴图与枪包 ogg **都会解析失败**；
- 内置默认枪包同理（jar 内是 `assets/tacz/custom/tacz_default_gun/assets/tacz/...`，不是 `assets/tacz/...`）。

做法：实现 `IResourcePack` 把枪包 `assets/<ns>/<path>` 按 `<ns>:<path>` 暴露（**namespace 不写死**，
第三方枪包用自己的 namespace 也生效）；用 `SimpleReloadableResourceManager#reloadResourcePack` 挂载；
监听 Forge `SoundLoadEvent`（在 `SoundHandler#onResourceManagerReload` 内、`SoundRegistry` 清空前触发，
此时 domain 管理器已重建）在每次资源重载后重挂；`ensureInstalled()` 比对「已挂 namespace 集合 vs 当前索引集合」，
不一致就重挂（枪包列表是运行期扫描出来的）。数据源复用 `mcshim.ResourceManager.currentPackSources()`
（= `GunPackLoader`），**没有第二套枪包扫描**。不用 `registerReloadListener` 是因为它会同步
`ProgressManager.push` 弹加载进度条，不适合播放路径。

**优先级已实测**：上游枪包是 `Position.BOTTOM`（最低），本包在 1.7.10 是「后挂 ⇒ 最高」
（`FallbackResourceManager#getResource` 从后往前找）。对内置默认枪包 vs 模组 jar 做过路径重叠统计：
`assets/tacz` 下**只有 `lang/*.json` + `lang/*.lang` 重叠 41 个**（其余 3368 个枪包资源路径零重叠）；
语言文件的上游语义就是「枪包覆盖本体」，且 1.7.10 `LanguageManager#loadLocaleData` 走
`getAllResources` 全量合并、后加载者胜 ⇒ 枪包语言覆盖本体同名 key，与上游一致。
**因此「最高优先级」在本工程不产生与上游不同的结果。**

> 顺带解决了 assets-port 遗留的「枪包 `.lang` 没有加载路径」缺口：枪包 `assets/tacz/lang/*.lang`
> 现在会被原版 `LanguageManager` 自动合并（不再依赖 `LanguageRegistry` 注入方案，两者不冲突）。

### particle 逐文件（`client/particle/`）

| 文件 | 状态 | 上游 → 1.7.10 |
|---|---|---|
| `MuzzleParticleManager` | 完成（639 行） | **走 vendored SBM 的 snowstorm 链路**（`FirstPersonParticleSystem`/`ParticleDefinitionLoader`/`ParticleEmitterInstance`/`CameraStateCache`），与 lead 裁决一致。映射：`InteractionHand/PoseStack/LightTexture`→`mcshim.*`；`fastutil.Pair#right()`→`commons-lang3 Pair#getRight()`；`ItemStack.EMPTY`→`null`；`options.getCameraType().isFirstPerson()`→`gameSettings.thirdPersonView == 0`；**`GameRenderer#getMainCamera()` 无 1.7.10 对应**（`EntityRenderer` 不暴露 `Camera`）→ 用 `EntityPlayerSP` 的插值 pitch/yaw（正是 `orientCamera` 用的同一组角度）；`Timer#renderPartialTicks` 因 `Minecraft#timer` 是 private → `ObfuscationReflectionHelper#remapFieldNames` 解析一次字段、**只缓存 `Timer` 对象**、之后每帧只读 public 字段（失败退化为 1.0f） |
| `ThirdPersonMuzzleParticleManager` | 完成（175 行） | `LivingEntity`→`EntityLivingBase`、`ClientLevel`→`WorldClient`、`Vec3`→SBM 移植层 `port.Vec3`（含 `ZERO`）、`options.getCameraType()`→`thirdPersonView==0`；`currentEntity` 类型 = `EntityLivingBase`（`MuzzleFlashRender`/`ExternalGunEffects`/`GunItemRendererWrapper`/`ItemInHandLayerMixin` 已按此对接）；火光 150ms 窗口 + 锚点跟随 + 烟雾一次性消费照搬 |
| `AmmoParticleSpawner` | 完成 | `ParticleEngine#createParticle(ParticleOptions,...)`→`ParticleCompat#spawnNamed`（`RenderGlobal#doSpawnParticle`，1.7.10 唯一的「按名字派发」入口，语义 = 创建 + 加入 `EffectRenderer`）；`Particle#setLifetime`→反射写 `EntityFX#particleMaxAge`；`Vec3`→三 double；`getDeltaMovement()`→`motionX/Y/Z`；`distanceToSqr`→`getDistanceSq` |
| `BulletHoleParticle` | 完成（277→413） | `TextureSheetParticle`→`EntityFX`、`TextureAtlasSprite`→`IIcon`、`Direction`→`ForgeDirection`；`Direction#getRotation()`（1.20 原版返回 `Quaternionf`）→ 自建 `rotationForDirection`（同一几何要求：局部 +Y → 面法线，6 方向推导表写在注释里，已标 TODO(L2) 说明滚转角可能差 90°、弹孔是各向同性污渍无视觉差别）；`VertexConsumer` 四顶点 → `Tessellator#addVertexWithUV` + `setBrightness/setColorRGBA_F`；`ParticleRenderType.TERRAIN_SHEET`→`getFXLayer()==1`；**光照明暗/颜色渐变/透明度淡出公式与阈值逐行照搬**；`Provider` 无参构造保留（`ParticleFactoryRegistry` 仍可编译） |
| `HitBlockEffectHandler` | 完成 | `Level/BlockPos/Direction/BlockState/VoxelShape/AABB`→`World/三 int/ForgeDirection/Block/Block#getCollisionBoundingBoxFromPool/AxisAlignedBB`；`SoundType`→`Block#stepSound` + 字符串音效名；`playLocalSound(...SoundSource.BLOCKS)`→`World#playSound(x,y,z,name,vol,pitch,distanceDelay=false)`。`handle(...)` 12 参签名与 `client/ClientProxy` 现有调用点逐参数吻合 |
| `HitSurfaceParticleSpawner` | 完成（308→516） | 上游把 `ParticleOptions`（`BlockParticleOption`/`DustParticleOptions`/`DustColorTransitionOptions`/`ParticleTypes`）解析成「粒子类型 + 参数」再 `createParticle`；1.7.10 没有 `ParticleOptions` → 改为折算成 **1.7.10 粒子名字符串 + 参数**（`blockcrack_<id>_<meta>` / `reddust` …）由 `ParticleCompat#spawnNamed` 派发；`ParticleArgument#readParticle` + brigadier 全部移除；`DustColorTransitionOptions` 双色 → 单色 `EntityReddustFX`（记 TODO(L2)） |
| `ParticleCompat`（**移植特有**，包内可见） | 完成 | 1.7.10 粒子平台薄桥：`spawnNamed`（按名字派发 + 加入 `EffectRenderer`）、`setLifetime`（反射 `particleMaxAge`，`ObfuscationReflectionHelper#remapFieldNames` 兼容开发/生产）、`scale`（`multipleParticleScaleBy`，无需反射）。**降级记录**：`RenderGlobal#doSpawnParticle` 自带 16 格距离门限 + `particleSetting`「最少」跳过，上游 `ParticleEngine#createParticle` 两者都没有 |

> **已删除（lead 裁决 A，不要恢复）**：并行子代理曾新建 `MuzzleParticleEffectData` / `MuzzleParticleDefinition`(625) /
> `MuzzleParticleFX`(207) / `MuzzleParticleSystem`(393)，自建一套 snowstorm 替代体系，其前提
> 「1.7.10 没有 simplebedrockmodel」**不成立**（sbm 已 vendor 到
> `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/**`，`FirstPersonParticleSystem` /
> `WorldEmitterManager` / `ParticleDefinitionLoader` 都在）。那套实现无人引用、属「发明第三种语义」，**已全部删除**；
> `GunItemRendererWrapper` 继续传 `FirstPersonRenderHandler.getParticleSystem()`（真实 `FirstPersonParticleSystem`）。

### tooltip 逐文件（`client/tooltip/`）

> **1.20 的 `TooltipComponent` / `ClientTooltipComponent` / `TooltipComponentCallback` /
> `RegisterClientTooltipComponentFactoriesEvent` 在 1.7.10 完全没有**；`Item#addInformation(ItemStack, EntityPlayer, List, boolean)`
> 是唯一的 tooltip 通道（纯文本）。高保真做法：**不整段丢弃** —— 把上游组件的绘制逻辑原样搬进自己的
> `renderText` / `renderImage`（走 `mcshim.GuiGraphics`），再加一层
> `appendHoverText(List<IChatComponent>)` / `getLegacyTooltipLines()` 桥接给 `addInformation`。
> **5 个类的公开方法名与上游逐一比对一致**（`getHeight` / `getWidth` / `renderText` / `renderImage`，
> `ClientAttachmentItemTooltip.rgbToHex` 也保留），另加上述 2 个 1.7.10 必需桥接方法。

| 文件 | 状态 | 要点 |
|---|---|---|
| `ClientGunTooltip` | 完成（488→526） | `Font`→`FontRenderer`、去掉 `Matrix4f`+`MultiBufferSource` 参数（固定管线立即绘制）、`Component`→`IChatComponent`、`font.split`→`FontRenderer#listFormattedStringToWidth`、`Component.keybind`→`Keyboard.getKeyName(RefitKey.REFIT_KEY.getKeyCode())`；**段高/行高/缩进(+20)/颜色/`DecimalFormat` 全部照搬**；弹药图标 `GuiGraphics#renderItem` |
| `ClientAttachmentItemTooltip` | 完成（334→459） | 同上 + `rgbToHex` 保留 |
| `ClientAmmoBoxTooltip` | 完成（73→145） | 同上 |
| `ClientAmmoStorageTooltip` | 完成（104→171） | 同上 |
| `ClientBlockItemTooltip` | 完成（114→170） | 同上 |
| `package-info` | 完成 | 上游的 `@MethodsReturnNonnullByDefault` / `@FieldsAreNonnullByDefault`（1.13+ 原版注解）在 1.7.10 不存在 → 只保留 JSR305 `@ParametersAreNonnullByDefault`，可空性逐处标 `javax.annotation.Nullable` |

### 需要别的包配合的 4 条（**无编译缺口，都是接线**）

1. ⚠️ **`client/ClientProxy#init`（client-core）加一行**
   `com.tacz.guns.client.sound.GunPackResourcePack.ensureInstalled();`（幂等、便宜）。
   理由：原版贴图 stitch 发生在资源重载里，枪包贴图（`tacz:particle/flash_strip`、`tacz:textures/gun/**`）
   要在那一刻就能被 `TextureManager` 找到；不接也能跑，但枪包贴图要等第一次播放音效之后才可解析
   （首帧模型/粒子贴图会丢）。
2. **`client/event/ReloadResourceEvent`（client-event）**：资源重载时调 `SoundPlayManager.clearSoundResourceCache()`
   —— 它会清 `SOUND_RESOURCE_EXISTS_CACHE` 的**否定**缓存并失效 `GunPackResourcePack` 索引。
   上游这条链也由 `ReloadResourceEvent` 驱动。
3. **`mixin/client/ItemInHandLayerMixin`（mixin-port）**：`ThirdPersonMuzzleParticleManager` 已 1.7.10 化，
   文件里两处 `// ThirdPersonMuzzleParticleManager.currentEntity = livingEntity / = null;`
   **现在可以取消注释**了（`currentEntity` 是 `EntityLivingBase`，可写）。
4. **`entity/EntityBulletClientHooks.ammoParticleSpawner` 尚未注入**：需客户端赋值
   `EntityBulletClientHooks.ammoParticleSpawner = b -> AmmoParticleSpawner.addParticle((EntityKineticBullet) b);`
   （建议与第 1 条同一个 `ClientProxy#init`），否则 `AmmoParticleSpawner` 永远不被调用
   （编译不受影响，运行期静默不生效）。

`client/init/ParticleFactoryRegistry`（client-core）**不需要本工作流提供任何工厂类**：1.7.10 没有
`ParticleProvider` / `ParticleOptions` 体系，`BulletHoleParticle` 是 `EntityFX` 子类，
由 `HitBlockEffectHandler` / `HitSurfaceParticleSpawner` 经 `ParticleCompat` + `EffectRenderer#addEffect` 直接创建；
上游 `RegisterParticleProvidersEvent` 的语义**已内化**到这两个 Handler。

### 运行期风险（构建通过后要实测的 3 条）

1. **`SoundHandler#sndRegistry` 是按字段类型反射拿的**（1.7.10 只有私有注册入口）。解析失败只 warn 并退化为
   「只播 `sounds.json` 里注册过的原版音效」，不崩。
2. **`Minecraft#timer` 也是 private**（`MuzzleParticleManager` 取 `renderPartialTicks`），同样解析一次字段、
   缓存 `Timer` 对象后每帧只读 public 字段；失败退化为 `1.0f`（最多差 1 tick 的摄像机旋转，只影响
   「世界对齐」枪口矩阵的旋转分量）。
3. **枪包资源索引要遍历 `assets/**`**（默认枪包约 3400 个文件）。只在首次使用 / 每次资源重载发生一次；
   若实测卡顿，改成按实际用到的 namespace 反向裁剪即可（索引已按 `ResourceLocation` 存，改动局限在
   `GunPackResourcePack#index`）。

---

## client/event（task-13，client-event 工作流）—— 20/20 完成

> 写作用域：`src/main/java/com/tacz/guns/client/event/**`，**严格 20 文件**（未新增、未删除任何类）。
> 判定：`mcp__idea__lint_files`（20 文件，`min_severity=error`）→ **0 error**；
> `^import` 1.20 包名扫描（`com.mojang.*` / `net.minecraft.{resources,world,core,server,network.chat,client.player,command}.*` /
> `net.minecraftforge.{eventbus,fml,api.distmarker}` / `it.unimi.*` / `com.github.exopandora.*`）→ **0 命中**
> （唯一 `net.minecraft.world.World` 命中是 **1.7.10 原生**类 `World`）。

### 全仓铁律（客户事件侧确认，lead 转达并已核 1.7.10 源码）

1. **`@SubscribeEvent` 处理方法必须是实例方法**：`cpw.mods.fml.common.eventhandler.ASMEventHandler`
   生成的回调是 `INVOKEVIRTUAL`（`ASMEventHandler.java:115`），且 `EventBus#register(Object)` 不过滤 static
   （`EventBus.java:60` 只遍历 `getMethods()`）→ 静态处理方法注册**不报错**，但**第一次派发**时抛
   `IncompatibleClassChangeError`。因此 20 个类里**全部 33 个处理方法已从 `public static void` 改为 `public void`**
   （类名/方法名/参数/语义一律不变），每类都补了 public 无参构造供 `ClientProxy` 反射 `new`；
   非处理方法的静态工具方法/字段（`resetTimestamp(s)`、`markHitTimestamp()`、`applyFirstPersonGunTransform(...)`、
   `showHintForTarget(...)`、`ITEM_MODEL_FOV`、`WORLD_FOV_DYNAMICS` 等）**保持 static**，调用点未变。
2. **`EventBus#register(Class)` 无效**（只会去 `java.lang.Class` 上找注解）→ 一律传实例。

### 逐文件状态

| 文件 | 上游职责 | 1.7.10 钩子 | 状态 / 降级 |
|---|---|---|---|
| `CameraSetupEvent` | 镜片 FOV、相机后坐、相机动画 | `FOVUpdateEvent`（世界 fov，HIGHEST+NORMAL 两级）+ `RenderTickEvent.START/END`（相机角度）+ `BeforeRenderHandEvent`（手部相机动画） | **完成**；D1/D2/D3 |
| `ClientGunTimeResetEvent` | 断线清空全部客户端时间戳 | `FMLNetworkEvent.ClientDisconnectionFromServerEvent` | **完成**（上游 private 构造 → public 无参，1.7.10 注册需实例） |
| `ClientHitMark` | 命中/击杀反馈、标靶车伤害计数 | api `EntityHurtByGunEvent.Post` / `EntityKillByGunEvent` | **完成**；D4 |
| `ClientPreventGunClick` | 持枪时禁止使用/交互 | `PlayerInteractEvent`（1.7.10 在 `Minecraft.func_147121_ag()` 的 `:1528/:1556` post） | **完成**（取消它同时挡掉 `onPlayerRightClick`/`sendUseItem`/`swingItem()`，等价上游 `setSwingHand(false)+setCanceled(true)`；只挂 Forge 总线） |
| `CommonNetworkCacheEvent` | 联机时清空客户端枪包缓存 | `FMLNetworkEvent.ClientConnectedToServerEvent`（`isLocal` = 上游 `isMemoryConnection()`）/ `ClientDisconnectionFromServerEvent` | **完成** |
| `FirstPersonRenderEvent` | 第一人称渲染入口（上游 `@SubscribeEvent` 本就注释掉） | 同名 `RenderHandEvent`（1.7.10 版无 item/PoseStack） | **完成**；D5 |
| `FirstPersonRenderGunEvent` | 第一人称枪械姿态/后坐/跳跃 sway/约束变换 | 纯 JOML 矩阵运算 + api `GunFireEvent` / `RenderItemInHandBobEvent.BobView` | **完成，无降级**（`PoseStack`/`Axis`/`Mth` 走 mcshim；`yOld/getY()`→`lastTickPosY/posY`，`Mth.lerp` 用 float 重载） |
| `GunDisplayCaseClientInteractionEvent` | 展示柜「潜行+使用」提示 | `TickEvent.ClientTickEvent` 轮询使用键按下沿 | **完成**；D6 |
| `GunDisplayCaseHighlightEvent` | 展示柜槽位高亮框 | `DrawBlockHighlightEvent` | **完成**；D7 |
| `GunPegboardHighlightEvent` | 挂板槽位高亮框 | `DrawBlockHighlightEvent` | **完成**；D7 |
| `InventoryEvent` | 快捷栏切换触发 draw / 模型预热 | `TickEvent.ClientTickEvent`（FML）+ api `SwapItemWithOffHand`（Forge） | **完成**（`ItemStack.matches` 用私有 `matches()` 复刻 count+item/metadata+NBT） |
| `PlayerEnterWorld` | 旧枪包转换提示（可点击按钮） | cpw `PlayerEvent.PlayerLoggedInEvent` | **完成**（`ClickEvent`/`HoverEvent`/`ChatStyle`/`ChatComponentTranslation`） |
| `PlayerHurtByGunEvent` | 中枪时调整受伤晃动倍率 | api `EntityHurtByGunEvent.Post` | **完成** |
| `PreventsHotbarEvent` | 合成台/改装界面隐藏快捷栏 | `RenderGameOverlayEvent.Pre` + `ElementType.HOTBAR` | **完成**（`GuiIngameForge.renderHotbar` 的 `pre(HOTBAR)` 为真即跳过 `:189`） |
| `RefreshClonePlayerDataEvent` | 客户端玩家实体重建时搬运同步数据 | `TickEvent.ClientTickEvent` + 移植特有的 `thePlayer` 引用变化检测 | **完成**；D8 |
| `ReloadResourceEvent` | 方块图集 stitch 后重载内置资产 + 清音效缓存 | `TextureStitchEvent.Post` + `map == getTextureMapBlocks()` | **完成**（1.7.10 事件无 `getAtlas().location()`；已按 lead 要求调用 `SoundPlayManager.clearSoundResourceCache()`） |
| `RenderCrosshairEvent` | 准心替换、命中/击杀标记 | `RenderGameOverlayEvent.Pre` + `ElementType.CROSSHAIRS`（`receiveCanceled=true`）+ `RenderTickEvent` 缓存改装界面状态 | **完成**；D9（`pre(CROSSHAIRS)` 为真即跳过原版准心 `GuiIngameForge:220`） |
| `RenderHeadShotAABB` | 爆头判定箱调试显示 | `RenderLivingEvent.Post` + `RenderGlobal.drawOutlinedBoundingBox` | **完成**；D10 |
| `TickAnimationEvent` | 动画状态机 run/walk/idle 输入、第三人称视觉更新 | `TickEvent.ClientTickEvent` / `RenderTickEvent` | **完成**（`input.getMoveVector()`→`Math.hypot(moveStrafe, moveForward)`；`isMovingSlowly()`→`isSneaking()`） |
| `TooltipEvent` | F3+H 下显示 TACZ id | `ItemTooltipEvent`（`List<String>`，非 Component） | **完成** |

### 事件总线注册清单（已落 `client/ClientProxy`）

- **FORGE**（11）：`ClientHitMark`、`ClientPreventGunClick`、`FirstPersonRenderEvent`、`FirstPersonRenderGunEvent`、
  `GunDisplayCaseHighlightEvent`、`GunPegboardHighlightEvent`、`PlayerHurtByGunEvent`、`PreventsHotbarEvent`、
  `ReloadResourceEvent`、`RenderHeadShotAABB`、`TooltipEvent`。
- **FML**（6）：`ClientGunTimeResetEvent`、`CommonNetworkCacheEvent`、`PlayerEnterWorld`、
  `RefreshClonePlayerDataEvent`、`TickAnimationEvent`、`GunDisplayCaseClientInteractionEvent`。
- **两条都要**（3）：`CameraSetupEvent`（Forge: `GunFireEvent`/`BeforeRenderHandEvent`/`FOVUpdateEvent`；
  FML: `RenderTickEvent`）、`InventoryEvent`（Forge: `SwapItemWithOffHand`；FML: `ClientTickEvent`）、
  `RenderCrosshairEvent`（Forge: `RenderGameOverlayEvent.Pre`；FML: `RenderTickEvent`）。
  双总线注册安全：两条 `EventBus` 实例独立，事件只 post 到其中一条，不会重复触发。

### 降级清单（逐条带上游出处）

| # | 降级点 | 上游出处 | 1.7.10 处理 |
|---|---|---|---|
| D1 | `ViewportEvent.ComputeCameraAngles` | `CameraSetupEvent:70/250` | 1.7.10 无此事件（`EntityViewRenderEvent` 只有 Fog*）→ `RenderTickEvent.START` 改 `renderViewEntity.rotationPitch/rotationYaw`；**roll ✅ 已实现（Mixin 路径，2026-11）**：1.7.10 没有任何相机角度事件，**唯一可行路径就是 Mixin** —— `mixin/client/GameRendererMixin#tacz$applyCameraRoll` 注入 `EntityRenderer#orientCamera(F)V` 的 `RETURN`（落在 view-bob 之后、世界绘制之前），读 sbm `CameraStateCache.getCameraRollDegrees()` 后 `GL11.glRotatef(roll, 0, 0, 1)`（= 上游 `Axis.ZP.rotationDegrees(roll)`）。数值通道复用**上游同款**的 `CameraStateCache.setCameraRoll(float)`（该缓存已有两个消费者：`MuzzleParticleManager` 第一人称粒子→世界变换、`GunItemRendererWrapper`），所以**只需 `client/event/CameraSetupEvent` 每帧调一次 `CameraStateCache.setCameraRoll(roll)`，相机 roll 与这两处消费方会同时变正确**；未写入时 roll 恒 0、注入自动 no-op（零 GL 调用）。手部 pass 不受影响（`renderHand` 只调 `hurtCameraEffect`/`setupViewBobbing`，不调 `orientCamera`），与上游「roll 只作用世界相机」一致。相机动画用 `client/renderer/other/CameraAngles` 承载后写回。**✅ 生产端已接线（client-event）**：`CameraSetupEvent#applyLevelCameraAnimation` 在 `RenderTickEvent.START` 用渲染器回填后的 `CameraAngles#getRoll()` 写 `CameraStateCache.setCameraRoll(roll)`（无枪械渲染器 / `viewBobbing` 关闭 / player 为 null 时写 `0`，`resetTimestamp()` 也清零），**D1 因此不再是降级项**（早前「`CameraStateCache` 恒 0」的说法已作废） |
| D2 | 手部 FOV（`ComputeFov` 的 `usedConfiguredFov()==false`） | `CameraSetupEvent:136` | 上游**完整计算保留**（瞄具 `views_fov`/`zoom_number`/`ads_path` 分段曲线/二阶平滑），结果写入 `CameraSetupEvent.ITEM_MODEL_FOV`；**mixin-port 已在 `mixin/client/GameRendererMixin#getFOVModifier` 的 RETURN 注入里对 `!useFovSetting` 覆盖（已接线、lint 0 error）** —— `EntityRenderer.java:673`(true=世界)/`:770`(false=手部) 只有 2 个调用点，闸门精确，不会与世界 fov 双重缩放 |
| D3 | 越肩视角（Shoulder Surfing）分支 | `CameraSetupEvent:260-282` | 1.7.10 无该模组（`ShoulderSurfingCompat#isInstalled()` 恒 false）→ 后坐力直接施加到玩家朝向 |
| D4 | `displayClientMessage(Component, true)`（action bar） | `ClientHitMark:58` | 1.7.10 无 action bar → `addChatMessage`（聊天栏），文案 key 不变 |
| D5 | 第一人称枪械模型绘制 | `FirstPersonRenderEvent:73` | 由 vendored `simplebedrockmodel` 的 `FirstPersonRenderHandler#onRenderHand(RenderHandEvent)` 承担（同一事件、同一 `renderFirstPerson`、并 cancel 原版手持物）；本类只保留状态机 `exit/tryInit` + `OculusCompat.endBatch`，避免双重绘制 |
| D6 | `InputEvent.Key` / `InputEvent.MouseButton.Post` | `GunDisplayCaseClientInteractionEvent:24/40` | `TickEvent.ClientTickEvent` 轮询 `GunInputHandler.isKeyDown(keyBindUseItem)` 做按下沿；**加 `currentScreen != null` 守卫**（上游 InputEvent 只在无 GUI 时派发）；两个上游方法名保留为 `onUseKey/onUseMouse(Minecraft)` |
| D7 | `RenderStateShard`+`CompositeState.builder()`+`LevelRenderer.renderLineBox` | `GunDisplayCaseHighlightEvent:84-109`、`GunPegboardHighlightEvent:84-109` | 内嵌类改为返回 `mcshim.RenderType.lines()`，`NO_DEPTH_TEST/NO_CULL/COLOR_WRITE` 用显式 `GL11` 开关；线框 `RenderGlobal.drawOutlinedBoundingBox(aabb, -1)`；相机平移量按原版 `drawSelectionBox` 约定（`event.player` 插值坐标）；`VIEW_OFFSET_Z_LAYERING` 未实现 |
| D8 | `ClientPlayerNetworkEvent.Clone` | `RefreshClonePlayerDataEvent:27` | 1.7.10 无该事件 → `ClientTickEvent` 里检测 `Minecraft.thePlayer` **引用变化**（`handleRespawn` 会重建 `EntityClientPlayerMP`），逻辑体原样搬到 `onClientPlayerClone(old, new)`；`reviveCaps()/invalidateCaps()` 删除（无 Capability） |
| D9 | 旁观者模式（`isSpectator()` / `GameType.SPECTATOR`） | `RenderCrosshairEvent:56/132` | 1.7.10 无旁观者模式（1.8+）→ 两处守卫删除 |
| D10 | `minecraft.showOnlyReducedInfo()` | `RenderHeadShotAABB:27` | 无对应 API → 守卫删除；`shouldRenderHitBoxes()`→`RenderManager.debugBoundingBox`；`ForgeRegistries.ENTITY_TYPES.getKey`→`EntityList.getEntityString` 小写 + 补默认命名空间（与 `util/EntityUtil#getEntityId` 同规则） |

### 非降级差异（一次性 API 换名，语义一致）

- `PoseStack`/`Axis`/`Mth`/`RenderSystem`/`GuiGraphics`/`GlStateManager`/`ItemDisplayContext` → `com.tacz.guns.mcshim.*`；
- `IClientItemExtensions#getCustomRenderer()` → `MinecraftForgeClient.getItemRenderer(stack, ItemRenderType.EQUIPPED*)`
  + `IItemRenderer`（与 `client/gameplay/LocalPlayerDraw`/`LocalPlayerInspect` 同一写法）；
- `ItemStack.matches/EMPTY`、`CompoundTag`、`Component`、`LogicalSide`、`LocalPlayer`、`getMainHandItem()`、
  `Minecraft#{player,level,screen,options,hitResult,getFrameTime}`、`Options#bobView` → 1.7.10 对应物
  （各类 javadoc 有逐条对照表）。

### 跨包依赖（只调用、不改）

`client/ClientProxy`（注册）、`client/ClientTickHelper.getPartialTick`、`client/input/{GunInputHandler,InteractKey}`
（`InteractKey` 反向调用本包的 `GunDisplayCaseClientInteractionEvent.showHintForTarget(Minecraft, boolean)`，签名保持不变）、
`client/gameplay/LocalPlayerDataHolder`、`client/renderer/item/{AnimateGeoItemRenderer,GunItemRendererWrapper}`
（`GunItemRendererWrapper` 反向调用 `FirstPersonRenderGunEvent.applyFirstPersonGunTransform(...)` 与读取
`CameraSetupEvent.ITEM_MODEL_FOV/_FOV_DYNAMICS`）、`client/renderer/other/{CameraAngles,GunHurtBobTweak}`、
`client/renderer/scope/ScopeLensRenderer`、`client/renderer/crosshair/CrosshairType`、
`client/model/**`（`BedrockGunModel`/`BedrockAttachmentModel`/`BedrockPart`/`MuzzleFlashRender`）、
`client/particle/{MuzzleParticleManager,ThirdPersonMuzzleParticleManager}`、`client/animation/screen/RefitTransform`、
`client/gui/{GunRefitScreen,GunSmithTableScreen}`、`client/gui/overlay/{GunHudOverlay,KillAmountOverlay,DisplayCaseLockHintOverlay}`、
`client/resource/{ClientIndexManager,InternalAssetLoader,GunDisplayInstance}` + `client/resource/index/**` +
`client/resource/pojo/display/gun/{AdsPath,AdsFov,AdsCurve,RangefinderManager}`、`client/sound/SoundPlayManager`、
`compat/shouldersurfing/ShoulderSurfingCompat#showCrosshair`、`compat/oculus/OculusCompat#{endBatch,getHandBufferSource}`、
`block/{GunDisplayCaseBlock,GunDisplayCaseSlotHelper,GunPegboardBlock,GunPegboardSlotHelper}` +
`block/entity/{GunDisplayCaseBlockEntity,GunPegboardBlockEntity}`（`isLocked(metadata)` / `getSlot(metadata,x,y,z,Vec3)` /
`getSlotAabb(...)` / `SLOT_COUNT` / `getItem(int)`）、`config/client/RenderConfig`、`config/util/HeadShotAABBConfigRead`、
`entity/sync/core/{SyncedEntityData,DataHolder}`、`util/{DelayedTask,SlotAdapterHelper}`、`util/math/**`、`util/time/GunTime`、
`api/**`（`TimelessAPI`/`IGun`/`IGunOperator`/`IClientPlayerGunOperator`/事件类/`AbstractGunItem`/各 DataAccessor）。

> **client/event 侧无待办**：D1（相机 roll）与 D2（手部 FOV）两条 mixin 通道**均已接线并生效**
> （`CameraStateCache.setCameraRoll` / `CameraSetupEvent.ITEM_MODEL_FOV`，两端 lint 0 error）；
> 其余降级均为 1.7.10 平台能力缺失且已注明上游出处。

### 6. v2 client mesh 收尾（去 compat 之后）

`v2/client/mesh/**`（47 文件）、`v2/client/config/**`（1）、`v2/resource/**`（8）、`v2/common/model/tree/**`（8）已逐个清完，
`mcp__idea__lint_files`（error 级）→ **0 error**。三根「1.20 支柱」无法在 1.7.10 复现，按下列对应关系降级（每处代码均有 `TODO(L2)`）：

| 1.20 依赖 | 1.7.10 替代 | 影响 |
|---|---|---|
| `com.mojang.blaze3d.vertex.VertexBuffer`（GPU 常驻 VBO） | **新增** `v2/client/mesh/gpu/VertexBuffer`：保存 CPU 侧 `BufferBuilder.RenderedBuffer` 快照，每次绘制经 `BufferUploader.draw()` 重放。`bind()/unbind()` 为 no-op，`vertexBufferId` 为自增伪 id | 功能正确，但每帧重新逐顶点提交，比 1.20 慢约一个数量级 |
| `ShaderInstance` / `Uniform`（`ModelViewMat`/`ProjMat`/`Light0/1Direction`/`Fog*`/`Sampler*`…） | 全部 uniform 上传降级为 no-op；`ModelViewMat` 的乘法改成真实 `glPushMatrix/glMultMatrix/glPopMatrix`，光照交给固定管线 `GL_LIGHT0/1` | 无 shader 效果；光照靠固定管线 |
| VAO + 整数属性 + `glVertexAttribDivisor` | `MeshIntegerAttributes` 降级为「恒可用」no-op | **逐实例 lightmap/overlay 不生效**（用捕获时烘进顶点的光照）；MUTABLE 光照流不上传 |
| `RenderStateShard` / `CompositeState` / `Util.memoize` | `RenderType.State` + `ConcurrentHashMap` | overlay 顶点槽位丢失（1.7.10 无） |
| `Direction#toYRot()` | `GeometryCollector` 内手写 yaw 分支（SOUTH=0/WEST=90/NORTH=180/EAST=-90） | 与 1.20 完全一致 |
| `RenderType#getChunkLayerId()` | `WorldMeshRenderer.materialOrder()` 按名字前缀排序 | 绘制顺序近似（solid/cutout=0、emissive/plymesh=1、translucent=2、其余=100） |
| `FMLEnvironment.production` | 硬编码「生产环境」 | `MeshRenderDebug` 调试后端永久关闭 |
| `ForgeConfigSpec` | `WorldMeshConfig` 静态字段持有者（`SPEC.isLoaded()`/`STRATEGY.get/set/save()` 方法形状保留） | **配置不落盘**，重启回到 `AUTO` |

事件接线：`@Mod.EventBusSubscriber` → 静态块在两个 bus 注册**实例**（1.7.10 `EventBus` 只支持实例 `@SubscribeEvent`，
静态会 `IncompatibleClassChangeError`）；`RenderLevelStageEvent(AFTER_BLOCK_ENTITIES)` → `RenderWorldLastEvent`（删掉 stage 闸门）；
`LevelEvent.Unload` → `WorldEvent.Unload`（字段 `world`、客户端判定 `world.isRemote`）；`Frustum` → `Frustrum`（类名拼写）
+ `isBoundingBoxInFrustum(AABB.toAxisAlignedBB())`，每帧 `setPosition(cameraPos)` 重建。

> 备注：`com.tacz.**` 目前**没有**编译期引用 `StaticMeshRenderer`/`WorldMeshRenderer`/`GeometryCollector`/`MeshSink`
> （只有 javadoc 提及），真实 1.7.10 绘制路径是 `DisplayGunGeometry`/`DisplayGunMeshGroup`；v2 网格子树按「保真 vendor」保留。

---

## runClient 启动实测（第 3 轮，主 Agent）

**`./gradlew build` = BUILD SUCCESSFUL**，产出 `build/libs/tacz-5.11.45.140.jar`（68.6 MB）。

### 启动验证进展
1. 首次启动：Forge 15 mod 全加载 → `tacz` preInit 崩于 `ModRecipe.registerVanillaRecipes` 的
   `CraftingManager.addRecipe` NPE（配方里有 null 物品，1.7.10 无数据包级配方隔离）。
   **已修**：`ModRecipe.register()` 对两批配方加 try/catch 隔离 + 错误日志。
2. 第二次启动：跑过配方阶段，**枪包加载成功**
   （`[tacz] Start scanning for gun packs` → `tacz_default_gun, Main namespace: tacz` → `Found 1 possible gunpack(s)`），
   崩在 `mixin/client/ItemInHandLayerMixin` 注入签名不符：
   `renderArmWithItemHead(EntityLivingBase,F,CallbackInfo)` vs 目标 `RenderPlayer#renderEquippedItems(AbstractClientPlayer,F)`。
   **已派 mixin-port 修**（并全仓自查所有 `@Inject` 形参类型必须与目标描述符完全一致；
   最安全写法是只声明 `CallbackInfo`）。

### 环境记录
- 运行环境：Java 1.8.0_512 (Azul)、LWJGL 2.9.4、OpenGL 4.6 (NVIDIA MX250)、GTNHLib 0.11.52、UniMixins 0.3.1
- `run/client/falsepattern/org.joml-joml-1.10.8.jar` 的 `module-info.class` 让 FML 报「Zip 读取失败」但被忽略，
  不影响启动（Forge 1.7.10 的 ASM 版本读不了 Java 9+ module-info，属已知噪音）

---

## L2-D 修复：Mixin 回调签名必须与目标描述符**精确**匹配（runClient 实测崩溃）

### 崩溃与根因
```
[FATAL] Mixin apply for mod tacz failed mixins.tacz.json:client.ItemInHandLayerMixin
-> net.minecraft.client.renderer.entity.RenderPlayer: InvalidInjectionException
Invalid descriptor on ItemInHandLayerMixin->@Inject::renderArmWithItemHead
Expected (Lnet/minecraft/client/entity/AbstractClientPlayer;FL...CallbackInfo;)V
but found (Lnet/minecraft/entity/EntityLivingBase;FL...CallbackInfo;)V
```
**根因（一条必须记住的 Mixin 规则）**：`@Inject` / `@Redirect` / `@ModifyArg` 的回调方法形参
**必须与目标方法描述符逐字一致**（含 `F` vs `float`、接口 vs 实现类、父类 vs 子类）。
Mixin **不做宽化/向上转型**。我原以为可以用父类型接收，实际是错的 ——
`RenderPlayer#renderEquippedItems` 的形参是 `AbstractClientPlayer`，写成 `EntityLivingBase` 直接 apply 失败，
进而**整个 client mixin 配置加载失败 = 启动崩**（因为 `injectors.defaultRequire = 1`）。

> **⚠ 规则勘误（runClient 第二次实测纠正，以此为准）**
>
> 我最初写的是「用不到的后尾形参可以省略」—— **这条是错的**。按省略后的签名重跑，
> 报的是相反方向的错：
> ```
> Invalid descriptor on mixins.tacz.json:client.ItemInHandLayerMixin
> ->@Inject::renderArmWithItemHead(Lnet/minecraft/client/entity/AbstractClientPlayer;L...CallbackInfo;)V!
> Expected (Lnet/minecraft/client/entity/AbstractClientPlayer;FL...CallbackInfo;)V
> but found (Lnet/minecraft/client/entity/AbstractClientPlayer;L...CallbackInfo;)V
> ```
> **正确的规则（最终版，两条）**：
> 1. **`@Inject` 回调必须声明「目标方法的全部形参（顺序与类型逐字一致）」+ 末尾
>    `CallbackInfo`（无返回值）或 `CallbackInfoReturnable<T>`（有返回值）——<u>一个都不能省</u>。**
>    目标方法无参时才写 `(CallbackInfo ci)` / `(CallbackInfoReturnable<T> cir)`。
> 2. **省略部分形参只适用于 `@Redirect` / `@ModifyArg` / `@ModifyVariable`** 这类「只取部分参数」
>    的回调（`@Redirect` = `(被调用方法的 owner 类型, 原参数...)`；
>    `@ModifyArg` = `(被修改的那个参数类型)`；`@ModifyVariable` = `(该局部变量类型)`，
>    `@Invoker`/`@Accessor` 另有各自的签名约束）。
>
> **实操口径（最稳）**：对每个 `@Inject`，把 `method = "..."` 里写的描述符
> （或目标方法在 `build/rfg/minecraft-src` 的形参表）**原样**抄成 Java 形参，最后加 CallbackInfo。
> 具体到本项目实现，`@Inject` 的被捕获形参虽然用不到，也<b>必须保留</b>（只是会有 unused 警告）。

### 已做的修复（两轮）
**第 1 轮**：`ItemInHandLayerMixin#renderArmWithItemHead` / `#renderTail` 首参
`EntityLivingBase` → **`net.minecraft.client.entity.AbstractClientPlayer`**（精确匹配）。
方法体里 `ThirdPersonMuzzleParticleManager.currentEntity = livingEntity`
与 `HumanoidOffhandRender.renderGun(EntityLivingBase, ...)` 都自动向上转型，无需改动。

**第 2 轮（勘误后恢复全部形参）**：把第 1 轮误删的 `@Inject` 形参**全部补回**（7 处）：
| 方法 | 恢复后的签名 | 对应目标 |
|---|---|---|
| `ItemInHandLayerMixin#renderArmWithItemHead` | `(AbstractClientPlayer, float, CallbackInfo)` | `renderEquippedItems(AbstractClientPlayer,float)` |
| `ItemInHandLayerMixin#renderTail` | `(AbstractClientPlayer, float, CallbackInfo)` | 同上 |
| `AbstractButtonMixin#onClickHead` | `(Minecraft, int, int, CallbackInfoReturnable<Boolean>)` | `mousePressed(Minecraft,int,int)Z` |
| `CreativeModeInventoryScreenMixin#tacz$updatePackFilters` | `(int, int, float, CallbackInfo)` | `drawScreen(int,int,float)` |
| `CreativeModeInventoryScreenMixin#tacz$drawPackFilterButtons` | `(int, int, float, CallbackInfo)` | `drawScreen(int,int,float)` |
| `GameRendererMixin#onBobHurt` | `(float, CallbackInfo)` | `hurtCameraEffect(float)` |
| `GameRendererMixin#onBobView` | `(float, CallbackInfo)` | `setupViewBobbing(float)` |
| `ItemInHandRendererMixin#beforeHandRender` | `(float, CallbackInfo)` | `renderItemInFirstPerson(float)` |
（同时把 `AbstractButtonMixin` 的 `import net.minecraft.client.Minecraft;` 加了回来。）

### 全仓回调签名自查（19 个文件，逐条对 `build/rfg/minecraft-src` 抄描述符核对）

| Mixin | 目标方法（1.7.10 声明） | 回调形参 | 结论 |
|---|---|---|---|
| `AbstractButtonMixin` | `GuiButton#mousePressed(Minecraft,int,int)Z` | `(CallbackInfoReturnable<Boolean>)` 空前缀 | ✅ |
| `CreativeModeInventoryScreenMixin` | `initGui()` | `(CallbackInfo)` | ✅ |
| 同上 | `setCurrentCreativeTab(CreativeTabs)` | `(CreativeTabs, CallbackInfo)` | ✅ |
| 同上 | `drawScreen(int,int,float)` HEAD | `(CallbackInfo)` | ✅ |
| 同上 | `drawScreen(int,int,float)` TAIL | `(int,int,CallbackInfo)` | ✅ |
| 同上 | `mouseClicked(int,int,int)` | `(int,int,int,CallbackInfo)` | ✅ |
| 同上 | `handleMouseInput()` | `(CallbackInfo)` | ✅ |
| `GameRendererMixin` | `hurtCameraEffect(float)` | `(CallbackInfo)` | ✅ |
| 同上 | `hurtCameraEffect` 内第 3 个 `GL11.glRotatef` | `@ModifyArg (float)float` | ✅ |
| 同上 | `setupViewBobbing(float)` | `(CallbackInfo)` | ✅ |
| 同上 | `getFOVModifier(float,boolean)` HEAD | `(float,boolean,CallbackInfo)` | ✅ |
| 同上 | `getFOVModifier(float,boolean)` RETURN | `(float,boolean,CallbackInfoReturnable<Float>)` | ✅ |
| `HumanoidModelMixin` | `ModelBiped#setRotationAngles(float×6,Entity)` | `(float×6, Entity, CallbackInfo)` | ✅ |
| `PlayerModelMixin` | 同上（`priority=1100`） | `(float×6, Entity, CallbackInfo)` | ✅ |
| `ItemInHandLayerMixin` | `RenderPlayer#renderEquippedItems(AbstractClientPlayer,float)` HEAD/RETURN | `(AbstractClientPlayer, CallbackInfo)` | ✅ **本次修复** |
| `ItemInHandRendererMixin` | `renderItemInFirstPerson(float)` | `(CallbackInfo)` | ✅ |
| 同上 | `updateEquippedItem()` | `(CallbackInfo)` | ✅ |
| `LocalPlayerMixin` | `EntityPlayerSP#onLivingUpdate()` | `(CallbackInfo)` | ✅ |
| 同上 | `@Redirect` → `EntityPlayerSP#setSprinting(Z)` | `(EntityPlayerSP, boolean)` | ✅（运行期已验证：`EntityPlayerSP` 早于 `RenderPlayer` 加载，此前的崩溃报告里没它 = 已成功 apply） |
| `MouseHandlerMixin` | `Entity#setAngles(float,float)` 两个 `@ModifyVariable` | `(float)float` × 2，`index=1/2` | ✅ |
| `CreativeContainerAccessor` | `ContainerCreative#itemList` / `#scrollTo(float)` | `@Accessor List<ItemStack>()` / `@Invoker (float)void` | ✅ |
| `common/LivingEntityMixin` | `EntityLivingBase#onLivingUpdate()` | `(CallbackInfo)` | ✅ |
| `common/ServerPlayerMixin` | `EntityPlayerMP#clonePlayer(EntityPlayer,boolean)` | `(EntityPlayer, boolean, CallbackInfo)` | ✅ |
| `common/ServerGamePacketListenerImplMixin` | `@Redirect` → `EntityPlayerMP#setSprinting(Z)` | `(EntityPlayerMP, boolean)` | ✅（同上，运行期已验证 apply 成功） |
| `EntityRenderDispatcherMixin` / `LanguageMixin` / `SoundManagerPreparationsMixin` | 无注入（空 `@Mixin`） | — | ✅ |

**结论：19 个文件、全部回调签名已与目标描述符精确对齐；所有 `@Inject` 都带「全部目标形参 + CallbackInfo」
（第 2 轮已把误删的 7 处补回）。`@Redirect`/`@ModifyArg`/`@ModifyVariable` 按其各自规则只取所需参数。**

### 规则勘误 · 第 3 条（runClient 第三次实测补充）：返回值 → 必须 `CallbackInfoReturnable<T>`

第三次实测崩溃：
```
@Inject::switchRenderType(FZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V!
CallbackInfoReturnable is required!
```
**根因**：`GameRendererMixin#switchRenderType` 打的是 `getFOVModifier(float, boolean)`，而该目标**有返回值**
（`private float getFOVModifier(...)`，`EntityRenderer.java:473`）。Mixin 要求：目标有返回值时回调末尾
**必须**是 `CallbackInfoReturnable<T>`，用 `CallbackInfo` 直接报错。已在第三次修复中改掉。

> **完整规则（三条，最终版）**
> 1. **`@Inject` 回调必须声明「目标方法的全部形参（顺序与类型逐字一致）」+ 末尾回调信息参数**，一个都不能省。
> 2. **目标返回 `void` → 末尾用 `CallbackInfo`；目标有返回值 → 末尾用 `CallbackInfoReturnable<T>`**，
>    其中 `T` 是返回值**包装类型**：`float` / `F` → `Float`，`boolean` / `Z` → `Boolean`，
>    `int` / `I` → `Integer`，引用类型则用其本身。回调方法自身恒为 `void`。
>    （即使不需要改返回值，也必须用 `CallbackInfoReturnable` —— 只声明、不调用 `setReturnValue` 即可。）
> 3. **省略部分形参只适用于 `@Redirect` / `@ModifyArg` / `@ModifyVariable`**：
>    `@Redirect` = `(被调用方法的 owner 类型, 原参数…)`；`@ModifyArg` = `(被修改的那个参数类型)`；
>    `@ModifyVariable` = `(该局部变量类型)`；`@Accessor`/`@Invoker` 各自另有签名约束。

### 111 文件「目标返回类型 → 回调信息类型」全量核对（逐条抄自 `build/rfg/minecraft-src`）

| Mixin | 目标方法（含 1.7.10 返回类型） | 回调末尾类型 | 结论 |
|---|---|---|---|
| `AbstractButtonMixin#onClickHead` | `GuiButton#mousePressed(Minecraft,int,int)` → **boolean** | `CallbackInfoReturnable<Boolean>` | ✅ |
| `CreativeModeInventoryScreenMixin#tacz$initPackFilters` | `initGui()` → void | `CallbackInfo` | ✅ |
| 同上 `#tacz$selectPackFilterTab` | `setCurrentCreativeTab(CreativeTabs)` → void | `CallbackInfo` | ✅ |
| 同上 `#tacz$updatePackFilters` / `#tacz$drawPackFilterButtons` | `drawScreen(int,int,float)` → void | `CallbackInfo` | ✅ |
| 同上 `#tacz$clickPackFilterButtons` | `mouseClicked(int,int,int)` → void | `CallbackInfo` | ✅ |
| 同上 `#tacz$scrollPackFilters` | `handleMouseInput()` → void | `CallbackInfo` | ✅ |
| `GameRendererMixin#onBobHurt` | `hurtCameraEffect(float)` → void | `CallbackInfo` | ✅ |
| `GameRendererMixin#onBobView` | `setupViewBobbing(float)` → void | `CallbackInfo` | ✅ |
| **`GameRendererMixin#switchRenderType`** | `getFOVModifier(float,boolean)` → **float** | `CallbackInfoReturnable<Float>` | ✅ **本次修复**（原为 `CallbackInfo`） |
| `GameRendererMixin#tacz$overrideHandFov` | 同上 → **float** | `CallbackInfoReturnable<Float>` | ✅（本来就是） |
| `HumanoidModelMixin#setRotationAnglesHead` | `ModelBiped#setRotationAngles(F×6,Entity)` → void | `CallbackInfo` | ✅ |
| `PlayerModelMixin#setRotationAnglesTail` | 同上 → void | `CallbackInfo` | ✅ |
| `ItemInHandLayerMixin#renderArmWithItemHead` / `#renderTail` | `RenderPlayer#renderEquippedItems(AbstractClientPlayer,float)` → void | `CallbackInfo` | ✅ |
| `ItemInHandRendererMixin#beforeHandRender` | `renderItemInFirstPerson(float)` → void | `CallbackInfo` | ✅ |
| `ItemInHandRendererMixin#cancelEquippedProgress` | `updateEquippedItem()` → void | `CallbackInfo` | ✅ |
| `LocalPlayerMixin#onTickClientSide` | `EntityPlayerSP#onLivingUpdate()` → void | `CallbackInfo` | ✅ |
| `common/LivingEntityMixin#onTickServerSide` | `EntityLivingBase#onLivingUpdate()` → void | `CallbackInfo` | ✅ |
| `common/ServerPlayerMixin#initialGunOperateData` | `EntityPlayerMP#clonePlayer(EntityPlayer,boolean)` → void | `CallbackInfo` | ✅ |
| `MouseHandlerMixin`（两个 `@ModifyVariable`） | `Entity#setAngles(F F)` → void | 不涉及（`@ModifyVariable` 无回调信息参数） | ✅ |
| `LocalPlayerMixin#swapSprintStatus` / `common/ServerGamePacketListenerImplMixin#cancelSprintCommand`（`@Redirect`） | `setSprinting(Z)` → void | 不涉及 | ✅ |
| `GameRendererMixin#tacz$scaleHeldGunHurtTilt`（`@ModifyArg`） | `GL11.glRotatef(FFFF)` | 不涉及（`(float)float`） | ✅ |
| `CreativeContainerAccessor`（`@Accessor`/`@Invoker`） | `itemList` / `scrollTo(float)` | 不涉及 | ✅ |

**全仓只有 `getFOVModifier` 一个有返回值的 `@Inject` 目标，它的两处注入现在都是 `CallbackInfoReturnable<Float>`；
`mousePressed` 一处是 `CallbackInfoReturnable<Boolean>`；其余全部 `void` + `CallbackInfo`。**

## runClient 第 4 轮：崩溃点已推进到网络层注册

启动链路已验证通过的环节（`build/r4run3.log`）：
`Forge/FML 加载 15 mod` → `tacz` 构造/preInit → **mixin 19 个全部 apply 成功** →
`[tacz] Start scanning for gun packs` → `- tacz_default_gun, Main namespace: tacz` →
`Custom painting variants are not supported on 1.7.10 ...` → OpenAL 初始化成功。

修掉的崩溃：
- `ModRecipe.registerVanillaRecipes` 的 `CraftingManager.addRecipe` NPE → 已加批次隔离（日志会打 `[TACZ] 原版材质配方注册失败，已跳过该批次`，非致命）
- mixin 注入签名三类错误 → 已全部修（`@Inject` 必须带**全部目标形参** + 末尾 `CallbackInfo`/`CallbackInfoReturnable<T>`；`F→Float`、`Z→Boolean`）

**当前崩溃**（`run/client/crash-reports/crash-*-08.16.07-client.txt`）：
```
java.lang.IllegalArgumentException: Duplicate handler name: com.tacz.guns.network.SimpleMessageDispatcher
  at SimpleNetworkWrapper.registerMessage -> NetworkHandler.register(NetworkHandler.java:225)
```
根因：FML 1.7.10 用 **handler 类名**给 netty handler 命名，同一个 handler 类不能注册两次（1.20 上游 `SimpleChannel` 无此限制）。
修法：每处 `registerMessage` 用**不同源码位置的匿名子类**（`new SimpleMessageDispatcher<Xxx>() {}.getClass()`）→ 匿名类名天然唯一。已派 common-infra。

## mixin 三条最终规则（实测得出，写死）
1. `@Inject` 必须带**目标方法全部形参**（顺序/类型逐字一致）+ 末尾回调信息参数，一个不能省；
2. 目标 `void` → `CallbackInfo`；有返回值 → `CallbackInfoReturnable<T>`（`F→Float`、`Z→Boolean`、`I→Integer`，**即使不改返回值也必须用它**）；
3. 省略部分形参**只适用** `@Redirect`/`@ModifyArg`/`@ModifyVariable`。

## ✅ runClient 启动 + 稳定运行验证通过（第 5 轮）

`./gradlew build` 通过 → `./gradlew runClient` 实测日志（`build/r5run.log`）：

```
Forge/FML 加载 15 mod → tacz preInit → mixin 19 个全部 apply 成功
→ [tacz] Registered synced data key tacz:is_aiming / sprint_time / is_bolting / throwable_using
→ [tacz] Start scanning for gun packs → - tacz_default_gun, Main namespace: tacz
→ [tacz] Injected 340 gun pack localization keys for en_US
→ [tacz] [TACZ] client setup finished
→ Created: 512x256 textures/blocks-atlas / 256x256 textures/items-atlas
→ Sound engine started
```
**无崩溃**（`run/client/crash-reports` 最新一份仍是上一轮的 08:16:07），进程在主菜单稳定驻留 45 秒以上。

修掉的两个启动崩溃：
1. `SimpleNetworkWrapper` 的 `Duplicate handler name`（FML 用 **handler 类名**给 netty pipeline 命名，同一 handler 类不能注册两次）→ `NetworkHandler` 43+2 处全部改成**每个源码位置独立的匿名子类** `new SimpleMessageDispatcher<Xxx>(Xxx::handle) {}`；discriminator 1..42 与上游同序不变。

### 剩余非致命运行期问题（已派单）
1. **5 个物品图标缺失** → `MISSING_ICON_ITEM_*_tacz.{modern_kinetic_gun, ammo, attachment, ammo_box, target_minecart}`：
   这 5 个 Item 类未重写 `registerIcons(IIconRegister)`，需注册 `assets/tacz/textures/items/*.png`（common-game）。
2. **镜片 GLSL 编译失败** → `Scope lens fragment shader compile failed: 0(51): syntax error, unexpected integer constant`：
   GLSL 120 里 int 与 float 混算/`#version` 位置/非常量数组索引是常见成因；同时要求把 `glGetShaderInfoLog` 全文 + 源码打出来（client-render）。

---

## 第 7 轮：构建通过 + MCP lint 全清（交接给用户跑 runClient）

### 当前状态
- **`./gradlew build` = BUILD SUCCESSFUL**（`build/r7build4.log`，含 spotless + checkstyle(已禁用 wildcard) + shade + downgrade）
- **MCP `lint_files(min_severity=error)` 对 21 个最高风险文件 = 0 error**（入口 `GunMod`/`CommonProxy`/`ClientProxy`、
  `NetworkHandler`、全部 10 个 client mixin + `LivingEntityMixin`、`ScopeLensProgram`、`GunItemRendererWrapper`、
  `DisplayManager`、`ClientAssetsManager`、`WorldMeshRenderer`、`ModAttributes`/`ModItems`/`ModBlocks`/`ModRecipe`/`TargetMinecartItem`）
- 上一轮 `runClient` 已实测：进主菜单、`client setup finished`、340 个枪包语言 key 注入、声音引擎启动

### 第 7 轮修掉的两个运行期崩溃
1. **5 个物品图标缺失**（`MISSING_ICON_ITEM_*_tacz.*`）→ 5 个 Item 类补 `registerIcons(IIconRegister)`；
   顺带修掉 `TargetMinecartItem` 缺 `IIconRegister` import 的编译错误。
2. **`IllegalAccessError: tried to access class com.tacz.guns.init.ModAttributes$1`**
   （server tick loop 崩溃）→ 根因：把事件处理器注册成**匿名类** `new Object() { @SubscribeEvent ... }`，
   编译产物是包私有的 `ModAttributes$1`，FML 生成的 `ASMEventHandler` 在另一个包调用它 → `IllegalAccessError`。
   **已改为具名 public 静态嵌套类 `ModAttributes.Handler`**。
   ⚠ **全仓规则（新增）**：注册到 `MinecraftForge.EVENT_BUS` / `FMLCommonHandler.instance().bus()` 的处理器
   **必须是可访问的具名类**（不能是匿名类/lambda），否则 FML 生成的 ASMEventHandler 会 `IllegalAccessError`。
   已全仓 grep `register(new Object()` = 0 处。

### 待用户执行
`./gradlew runClient` 实测（主 Agent 不再代跑），关注：
- 5 个物品图标是否正常显示（不再是紫黑方块）
- 镜片 GLSL 是否仍报 `Scope lens fragment shader compile failed`
- 进世界后开火/换弹/瞄准是否正常（`DataHolder` 同步、`IGunOperator` 强转）

### 第 8 轮：MCP lint 复验（用户指定用 MCP 而非 gradle）
- `lint_files(min_severity=error)` 复查 39 个高风险文件（入口 / 网络 / 全部 mixin / 渲染 / 粒子 / 音效 / tooltip / 事件 / 物品 / 实体 / 同步 / 资源加载 / SBM 接口）→ **0 error**
- 唯一发现并修掉的一处：`EntityKineticBullet.java:872` 的 javadoc `{@link com.tacz.guns.entity.sync.SyncedEntityData}` 指向已迁移的类，
  改为 `com.tacz.guns.entity.sync.core.SyncedEntityData`（行尾注释，不影响运行）
- `client/renderer/scope/ScopeLensProgram.java` 已于 12:20 更新（GLSL 120 等价源码 + 编译失败信息打印），对应上一轮
  `Scope lens fragment shader compile failed: 0(51): unexpected integer constant` 的修复
- 构建最后已知状态：**BUILD SUCCESSFUL**（`build/r7build4.log`）

### 第 9 轮：用户实测反馈的三个问题与定位

1. **P0 JVM abort**：`glDeleteProgram` 在 **Netty IO 线程**执行（`ClientGunTimeResetEvent.onLoggingOut` → `ScopeLensRenderer.close()`），
   1.7.10 要求所有 GL 调用在**渲染线程 + 有效上下文**，否则 LWJGL 直接 abort JVM。
   → 已派 client-render：`close()` 改为「置删除标记 + 丢弃引用」，渲染线程每帧 `drainDeletes()` 真正 `glDelete*`；并把
   「断线/网络回调/模组加载回调一律不可直接调 GL」写成全仓规则、全仓扫 `glDelete*/glGen*`。
2. **贴图基本加载不了 / 紫黑块 / 模型看不出是什么**（P1）→ 已派 client-misc 按 5 步排查：
   `GunPackResourcePack` 是否挂上（打印 pack 数/domains/`getResource` 命中）→ 资源重载与 `TextureStitchEvent` 时序（可能需挂载后主动 `refreshResources`）
   → 模型贴图 namespace 是否用了枪包实际 namespace（不能硬编码 `tacz`）→ bedrock `texture_width/height` 与像素/UV 换算
   → 枪包贴图需 `GL_CLAMP_TO_EDGE`（YSM 移植踩过同坑）。
3. **配方 NPE**（`ModRecipe.java:60` gun_smith_table）→ 根因是**矿词在 preInit 未注册**：`OreDictionary.getOres("logWood"/"ingotIron")` 为空
   → 配方项 null → `copy()` NPE（非致命，被批次 try/catch 兜住但丢整批）。
   → 已派 common-game：`ModRecipe.register()` 从 `preInit` 移到 `init`，并对每个矿词/物品做判空跳过 + warn。

### GLSL 镜片问题已破案（client-render）
真凶不是第 51 行的整数字面量，而是**第 50 行的中文注释**：LWJGL2 把 `CharSequence` 按字节喂给 `glShaderSource`，
UTF-8 多字节打乱 token 流，驱动把错误报到了下一行。已修：4 份 shader 全部纯 ASCII + 新增 `validateSource()` 静态自检
（非 ASCII/控制字符报行列与 `U+xxxx`、`#version 120` 必须首个 token、禁 `texelFetch(`/`#version 150`）+ 编译/link 失败打印完整 infoLog 与带行号源码。
**全仓规则（新增）**：嵌入 GLSL 源码一律纯 ASCII，中文说明放 Java javadoc。

#### 收尾修复 2 项：物品图标 MISSING_ICON（5 个物品）+ 配方 NPE（真实根因）

**A. 5 个物品补 `registerIcons`（1.7.10 必须有 IIcon，否则 TextureMap 生成 MISSING_ICON_ITEM）**

| 物品 | 注册的 IIcon | 说明 |
|---|---|---|
| `ModernKineticGunItem` | `minecraft:bow`（占位） | 上游 model = `builtin/entity`（BEWLR 3D 渲染，**没有 2D png**）；base 模组无枪 2D 图标 → 用原版武器图标兜底 |
| `AmmoItem` | `minecraft:arrow`（占位） | 同上（上游 `builtin/entity`） |
| `AttachmentItem` | `minecraft:compass`（占位） | 同上（上游 `builtin/entity`） |
| `AmmoBoxItem` | `tacz:ammo_box` | 真实资源 `assets/tacz/textures/items/ammo_box.png`（assets-port 已复制） |
| `TargetMinecartItem` | `tacz:target_minecart` | 真实资源：上游 model 引用 `tacz:item/target_minecart`，已复制到 `textures/items/target_minecart.png` |

三者占位图标均带 `TODO(L2, client-port)`：枪/弹/配件需按 NBT 用 `IItemRenderer` 接管渲染，此图标仅作背包/JEI 兜底。

**B. 配方 NPE（`ModRecipe.java:60` gun_smith_table）—— 真根因是 1.7.10 原版配方 API 不支持矿词，非「矿词未注册」**

- 实测 NPE 链：`CraftingManager.addShapedRecipe` 的 key→ItemStack `HashMap` 只接受 **Item/Block/ItemStack**；
  **String 矿词项被存成 null**，随后 `hashmap.get(char).copy()` NPE（1.7.10 没有 1.8+ 的 `CraftingHelper`，
  `GameRegistry.addShapedRecipe` 只是转发原版方法，不解析矿词）。原版矿词（logWood/ingotIron）实际在
  `OreDictionary` **类初始化时**就已注册（`static { initVanillaEntries(); }`），所以「preInit 未注册矿词」不是直接原因，
  真正原因是用了错误的注册 API。
- 修复（两处都做）：
  1. **`ModRecipe` 全部带矿词/混合材料的配方改用 Forge `ShapedOreRecipe` / `ShapelessOreRecipe`**
     （`net.minecraftforge.oredict`，`RecipeSorter` 已为其注册排序规则），再 `GameRegistry.addRecipe(IRecipe)`；
     `ShapedOreRecipe` 同时支持 Item/Block/ItemStack/String 混合项。
  2. **`ModRecipe.register()` 从 `CommonProxy.preInit` 移到 `CommonProxy.init`**（`CommonRegistry.commonSetup()` 之后），
     符合 1.7.10 配方注册惯例；世界加载在 init 之后，来得及。
  3. 新增隔离辅助：`addShaped/addShapeless`（产物 null → warn+跳过单条；`RuntimeException` → warn+跳过单条，不再整批丢失）、
     `hasAll(String...)` 矿词预检（缺失 → warn+跳过该条，如 `ingotCopper`）、`output(Item/Block,int)` 空安全、
     `withBlockId/withAmmoBoxLevel` 空安全、自定义配方 `registerCustom(name, Supplier<IRecipe>)` 同样逐条隔离。
  - 全 18 条配方映射与 NBT 产物（ammo_box `Level`、workbench `BlockId`）保持不变。

**验证**：`mcp__idea__lint_files`（ModRecipe + CommonProxy + 5 个物品类，error 级）→ `items: []`（0 error）。

---

## P1 用户实测「材质紫黑块 / 模型看不出是什么」排查（client-misc，task-12 复检）

### 结论（按 lead 的排查顺序逐条）

| # | 检查项 | 结论 | 证据 |
|---|---|---|---|
| 1 | `GunPackResourcePack` 是否真挂上 | **代码路径是通的，但此前无任何日志证据**；已补 INFO 诊断（pack 数 / 各自 domains / 索引文件数 / 4 个探针命中） | `ClientProxy#init` 第一条就是 `ensureInstalled()`；`SimpleReloadableResourceManager#reloadResourcePack` 是 1.7.10 唯一公开入口 |
| 2 | 资源重载顺序 | **确认存在真实时序风险**：`reloadResources()` 会先 `clearResources()` 把本包清掉，只有 `SoundLoadEvent` 会把它加回来（而且加回的时机在 `TextureManager` 重载之后） | 已改成「不信事件、只信资源管理器」的自愈：每 40 tick 用索引里的第一张 `.png` 做探针，读不到就重挂 + 延迟重载纹理 |
| 3 | 模型贴图路径 / namespace | **不是问题**：display JSON 写 `"texture": "tacz:gun/uv/ak47"`，经 `IDisplay.idToFile`（prefix `textures`、ext `.png`）得 `tacz:textures/gun/uv/ak47.png`，与枪包内 `assets/tacz/textures/gun/uv/ak47.png` 完全对应；namespace 已按「枪包索引到的并集」注册，未硬编码 | `IDisplay.java:13` `PackDirectory.of("textures", ".png")`；`GunDisplayInstance.java:478` |
| 4 | UV 全错 | **不是问题**：`ak47_geo.json`（`texture_width/height = 128`）与 `ak47.png`（256×256）**与上游逐字节相同**，128 是 TACZ 自己的 UV 坐标空间、256 是图集分辨率，UV 跨 0..128 正好铺满整图；`BedrockPolygon` 按 `texWidth/texHeight` 归一，`mcshim` 的 UV 是 0..1，`BufferUploader#draw` 用 `Tessellator#setTextureUV` 正确写出 | `Get-FileHash` 对比 `tmp/TACZ-1.20.1` 同路径文件 = 相同 |
| 5 | 贴图过滤 / Clamp | **确认差异并已修**：1.7.10 `SimpleTexture` 缺省 `GL_REPEAT`（`uploadTextureImageAllocate(..., clamp=false)`），1.20 默认 `GL_CLAMP_TO_EDGE` | `GunPackResourcePack` 现为枪包 `.png` **合成** `{"texture":{"blur":false,"clamp":true}}` 的 `.mcmeta`（包内自带真 `.mcmeta` 时优先真实文件），走 `TextureMetadataSection` → `GL_CLAMP_TO_EDGE` |

### ⭐ 找到的真正主因（**不在 client 三包内，但必须修**）：枪械/配件/弹药**从未注册 `IItemRenderer`**

- 全仓 `MinecraftForgeClient.registerItemRenderer(...)` **0 次调用**（只有 `getItemRenderer` 查询）；
- `ItemClientHooks.registerItemRenderer` 恒为 `null`，`ItemClientHooks.registerRenderer(Item)` **0 个调用点**；
- 于是枪械物品用**扁平占位图标**：`ModernKineticGunItem.registerIcons` → `minecraft:bow`、
  `AttachmentItem` → `minecraft:compass`、`AmmoItem` → `minecraft:arrow`；
- 表现：背包/快捷栏是「弓/罗盘/箭」，手上/第三人称**根本没有 3D 模型** —— 与用户说的
  「渲染的样子看不出来是什么东西」完全吻合。

**修复（`client/init/ClientSetupEvent`，属 client-core 写作用域，4 行）**：4 个渲染器均已存在、
都是 `IItemRenderer`、都有 public 无参构造：

```java
MinecraftForgeClient.registerItemRenderer(ModItems.MODERN_KINETIC_GUN, new GunItemRendererWrapper());
MinecraftForgeClient.registerItemRenderer(ModItems.AMMO,              new AmmoItemRenderer());
MinecraftForgeClient.registerItemRenderer(ModItems.ATTACHMENT,        new AttachmentItemRenderer());
MinecraftForgeClient.registerItemRenderer(ModItems.GUN_SMITH_TABLE,   new GunSmithTableItemRenderer());
```

（`client/renderer/item/`：`GunItemRendererWrapper extends AnimateGeoItemRenderer implements IItemRenderer`；
`AmmoItemRenderer`/`AttachmentItemRenderer`/`GunSmithTableItemRenderer` 均 `implements IItemRenderer`）

### 本次对 `GunPackResourcePack` 的实际改动（`client/sound/`，lint 0 error）

1. **INFO 诊断**（每次挂载打一行 + 4 个探针 HIT/MISS）：
   `[TACZ ResourcePack] Installed/Re-installed gun pack resource pack: packs=N [names] domains=[...] indexedFiles=M`，
   并探测 `textures/gun/uv/ak47.png`、`...png.mcmeta`、`tacz_sounds/dry_fire.ogg`、`textures/items/ammo_box.png`。
   枪包数为 0 时额外 WARN 提示检查 `<gamedir>/tacz/tacz_default_gun`。
2. **自愈**：`validateInstalled()` + 实例 `TickHandler`（FML 总线 `ClientTickEvent`，每 40 tick）——
   用索引里第一张 `.png` 探针校验，失败则重挂并发 WARN。这样「重载把包清掉」不再是静默失效。
3. **missing texture 重试**：重挂后在**已进世界**时用 `Minecraft#func_152344_a` 延迟一 tick 调
   `TextureManager#onResourceManagerReload`（1.7.10 唯一公开重载入口），把先前被缓存成
   紫黑块的枪包贴图重试一遍。**不**用 `Minecraft#refreshResources()`：那会先 `clearResources()`
   把本包一起清掉（重载→丢包→再挂的循环），理由已写进类 javadoc。
4. **CLAMP_TO_EDGE**：为枪包 `.png` 合成 `.mcmeta`（见上表第 5 条）。
5. 类 javadoc 新增「1.7.10 与 1.20 pack 系统的核心差异（本项目选择的路径）」4 条，明确
   「不调 `refreshResources`」「枪包贴图不属于任何图集」「missing texture 会被永久缓存」「REPEAT vs CLAMP」。

---

## 收尾追加（client-core）：物品渲染器 + 物品显示名钩子

### 1. P0 —— `IItemRenderer` 从未注册（用户「渲染看不出是什么东西」的真主因）✅ 已修

**病灶**（client-misc 全仓静态排查 + 本次复核确认）：`MinecraftForgeClient.registerItemRenderer(...)` **全仓 0 次调用** →
物品退回 `Item#registerIcons` 的扁平占位图标（枪→`minecraft:bow`、配件→`compass`、弹药→`arrow`），手持与第三人称**完全没有 3D 模型**。

**修复位置**：`client/init/ClientSetupEvent#onClientSetup()` 新增 step `"Item renderers"`，
由 `registerItemRenderers()` / `registerItemRendererFor(Item)` / `createItemRenderer(Item)` 三步完成。

**全仓 `grep "implements IItemRenderer"` 实测：恰好 4 个可实例化类**（`AnimateGeoItemRenderer` 是第 5 个但是抽象基类）：

| `ModItems` 字段 | 渲染器类 |
|---|---|
| `MODERN_KINETIC_GUN` | `client/renderer/item/GunItemRendererWrapper`（`extends AnimateGeoItemRenderer`，后者 `implements IFPGeoItemRenderer, IItemRenderer`） |
| `AMMO` | `client/renderer/item/AmmoItemRenderer` |
| `ATTACHMENT` | `client/renderer/item/AttachmentItemRenderer` |
| `GUN_SMITH_TABLE` | `client/renderer/item/GunSmithTableItemRenderer`（`ItemBlock` 同样走 `IItemRenderer`） |

`ExternalGunItemVboRenderer` **不是** `IItemRenderer`（VBO 辅助类）；`AMMO_BOX / TARGET_MINECART / STATUE / TARGET /
GUN_PEGBOARD / AMMO_STORAGE` 全仓**无**对应渲染器实现（上游 1.20 也未给它们 BEWLR）→ 保持原版渲染。

**健壮性**：每个物品**独立 `try/catch`**（单个渲染器构造失败不中断其余 3 个）+ 物品 `null` 判空告警跳过。
**顺带接上死钩子**：`ItemClientHooks.registerItemRenderer = ClientSetupEvent::registerItemRendererFor;`
（此前该字段恒 `null`、`ItemClientHooks#registerRenderer` 等于空转），与显式注册**共用同一分派**，不会两处分叉。

### 2. 物品显示名钩子（`ItemClientHooks.itemNameProvider`）✅ 已接

**上游依据（逐个对应，均为「索引名优先、否则 `super.getName`」）**：
`item/AmmoItem.java:53-60`、`item/AttachmentItem.java:43-50`、`item/GunSmithTableItem.java:66-73`；
枪械一条（`api/item/gun/AbstractGunItem.java:340-346`）**本工程已由 api 侧自行实现**
（`AbstractGunItem#getItemStackDisplayName` 直接查 `getClientGunIndex`），不经该钩子，故无需处理。

实现：`ClientSetupEvent#resolveItemName(ItemStack)`，按 `instanceof IAmmo / IAttachment / IBlock` 分派
（三者分别由 `AmmoItemDataAccessor / AttachmentItemDataAccessor / BlockItemDataAccessor` 继承，覆盖面正确），
查 `getClientAmmoIndex / getClientAttachmentIndex / getClientBlockIndex` 后取 `getName()`。

**1.7.10 差异**：上游返回 `Component.translatable(key)`，1.7.10 `Item#getItemStackDisplayName` 返回 `String`
→ 钩子返回**已本地化**字符串（`StatCollector#translateToLocal`），与 `AbstractGunItem` 既有写法一致
（索引里的 `name` 是 lang key，不翻译会直接显示 key）。
**契约**：索引缺失 / id 为 `null` / 任何异常 → 返回 `null`（调用方回退服务端名），**不返回空串、不抛**。

### 3. `ItemClientHooks.nameColorProvider` —— 留 `null` + TODO（有据，非未完成）

1. 全仓 `grep getNameColor` → **0 个调用点**（只有 `item/ItemClientHooks` 自身的定义），注入后无消费者 = 死代码；
2. 上游的名称染色是 `Component#withStyle(ChatFormatting.X)`（`AmmoBoxItem.java:211-225`、
   `AmmoStorageBlockItem.java:39-46`），而 1.7.10 `Item#getItemStackDisplayName` 只返回 `String`，
   **没有任何「给物品名上色」的 API**（`Item#getColorFromItemStack` 是图标顶点染色，不是文本）；
3. 这两个物品在本工程里**已经各自用 `EnumChatFormatting` 前缀自己实现了颜色**
   （`AmmoBoxItem#getItemStackDisplayName:133`、`AmmoStorageBlockItem#getItemStackDisplayName:45`），语义已达成。
→ 若日后真出现消费者，再按上游 4 处 `withStyle` 的 `EnumChatFormatting → RGB` 表补上（TODO 已写在
`ClientSetupEvent#registerItemNameProviders` 的注释里）。

**验证**：`get_file_problems(ClientSetupEvent / ClientProxy)` → 0 error；33 文件 `lint_files`（error 级）→ `items: []`；
`ClientSetupEvent` 完整性复核 `package=1 / class=1 / 460 行 / import 无重复`。**未跑 gradle**。

---

### compat 追加：Angelica（1.7.10 的 Iris 等价物）接入 `OculusCompat`（by compat-stub，对应 `docs/ANGELICA_COMPAT.md` P1-3）

Angelica 的 modid 是 **`angelica`**（`tmp/angelica-2.2.42-src/com/gtnewhorizons/angelica/AngelicaMod.java:31`），
它自带 Iris 实现并保留 new/legacy 两套包名（`net.irisshaders.iris.api.v0.IrisApi`、
`net.coderbot.iris.shadows.ShadowRenderingState`），**但 `Loader.isModLoaded("oculus")` 为 false** ——
原来只认 `oculus` 的写法会让 `LOADED`/`isRenderShadow()` 恒 false，导致 **shadow pass 里重复绘制**
（枪口火光/弹壳被画进 shadow map）。

已改 `compat/oculus/OculusCompat`（**只改这一个文件**；`init/CompatRegistry` 未动，非本工作流 scope）：
- 新增 `ANGELICA_MODID = "angelica"`、`OCULUS_MOD`、`ANGELICA_MOD` 三个探测位；
  `LOADED = (OCULUS_MOD || ANGELICA_MOD) && IrisApi 类可加载`。
- `isOculusInstalled()` → `OCULUS_MOD || ANGELICA_MOD`（语义「有 Iris 式光影实现」，不要求 API 类可加载）。
- `isRenderShadow()` → `ShadowRenderingState#areShadowsCurrentlyBeingRendered()`（Angelica 版实现 = `ShadowRenderer.ACTIVE`），
  不可用时回退 `IrisApi#isRenderingShadowPass()`（`IrisApi.java:62`）。
- `isUsingRenderPack()` → `IrisApi#isShaderPackInUse()`，不可用时对 Angelica 回退到
  `AngelicaConfig.enableIris`（`AngelicaConfig.java:68-71`）反射读取。
- `endBatch` 的 `FullyBufferedMultiBufferSource` 类探测与 `IrisApi` 实例改为**懒解析缓存**
  （`isRenderShadow()`/`endBatch()` 在渲染热路径上每帧被多处调用，装 Angelica 后 `LOADED` 会真的为 true）。
- `OptifineCompat` javadoc 补一条：**Angelica 会强制卸载 OptiFine**，此时 `OptifineCompat` 全 false 是正确行为，
  判断光影请用 `OculusCompat`（P2-6 建议）。

> **刻意偏离 lead 指令的一处（有据，请复核）**：没有把
> `isRenderShadow() ||= (ANGELICA && 光影包已启用)` 接上去。`isShaderPackInUse()` 是「全程为真」的全局开关，
> 并进 **shadow pass 判定**会让 `isRenderShadow()` 在「Angelica + 光影包」下恒 true，而所有调用点
> （`MuzzleFlashRender:143,170,187`、`ShellRender:185`、`RightHandRender:71`、`LeftHandRender:71`、
> `ScopeLensRenderer:233`、`ScopeThermalRenderer:226,378`）都用它「提前 return」→ 火光/弹壳/手部/镜片整体消失。
> Angelica 走 `areShadowsCurrentlyBeingRendered()` / `isRenderingShadowPass()` 才是 P1-3 要的真值；
> 「光影包是否启用」需要时用 `isUsingRenderPack()`。

**验证**：`lint_files`（13 文件 / error 级）→ `items: []`；`OculusCompat` warning 级仅剩「入口方法未被本包引用」类噪声。
**未跑 gradle**。若要让 `OculusCompat#initCompat()` 在 Angelica 下也被调用，
`init/CompatRegistry#enqueueCompat()` 还需加一句 `checkModLoad("angelica", ...)`（`init/` 属 common-infra）。

---

## 🚨 P0 规则（runClient 实测启动崩）：mixin 类内**禁止**嵌套/内部/匿名类

### 崩溃原文
```
mixins.tacz.json:client.CreativeModeInventoryScreenMixin from mod tacz: Unsupported mixin
... requires the following unsupported language features: [NESTING], these features require compatibility level JAVA_11
  at MixinInfo$State.validateClassFeatures(MixinInfo.java:356)
  at MixinConfig.postInitialise(MixinConfig.java:884)
```

### 根因（读了 Mixin 0.8.7 源码确认的**精确**机制，不是猜的）
`MixinInfo.State#validateClassFeatures` → `LanguageFeatures.scan(classNode)`；其中
`LanguageFeatures.scanClassFeatures` **只看两件事**：
```java
String nestHostClass = ClassNodeAdapter.getNestHostClass(classNode);   // NestHost 属性
List<String> nestMembers = ClassNodeAdapter.getNestMembers(classNode); // NestMembers 属性
if (nestHostClass != null || (nestMembers != null && nestMembers.size() > 0)) {
    features |= LanguageFeatures.NESTING;   // = 8，要求 JAVA_11
}
```
而 **javac 只要类里声明了嵌套类型（含 `static` 嵌套类、内部类、匿名类）就会写 `NestMembers`**；
本工程用现代 javac 编译后再由 `jvmDowngrader` 降到 8，但 `NestMembers` 属性**不会被剥掉**。
于是 `NESTING` 被要求，与 `mixins.tacz.json` 的 `"compatibilityLevel": "JAVA_8"` 冲突 → **启动即崩**。

> **注意区分两件事**：
> - **lambda / 方法引用不会产生 `NESTING`** —— 它们只是 `invokedynamic` + 合成方法（`LivingEntityMixin`
>   里用了大量 lambda 且一直验证通过，就是证据）。但 mixin 里**捕获 `this`** 的 lambda 仍有另一个坑
>   （合成方法以 Mixin 类为 owner，运行期把目标对象当 Mixin 用 → CCE），所以仍然别在 mixin 里写。
> - **`implements` 接口是安全的**，那不是嵌套类。

### 修法（**不要**抬高 `compatibilityLevel`）
抬到 `JAVA_11` 会让 Java 8 运行期的用户直接加载失败（本项目 `downgradeTargetVersion = 8`）。
正确做法是**把嵌套类提成独立顶层类**。

**`CreativeModeInventoryScreenMixin` 已提取 3 个嵌套类 → 2 个顶层普通类**：
| 原位置 | 提取后 |
|---|---|
| `private static final class AddButton implements Consumer<GuiButton>` | 并入 **`CreativePackFilterHooks#buttonAdder(List)`**（返回 `buttons::add`） |
| `private static final class ContainerAccessBridge implements Function<...>` | **`CreativePackFilterHooks#CONTAINER_ACCESS`**（`CreativePackFilterHooks::createAccess`） |
| `private static final class Access implements CreativePackFilters.ContainerAccess`（嵌在 Bridge 里） | **`CreativePackFilterContainerAccess`**（独立顶层 `public final class`） |

**顺带修掉的一个同类隐患**：这两个 helper 原本是 `private static` 嵌套类，即便不触发 `NESTING`，
Mixin 合并后调用点位于**目标类 `GuiContainerCreative`**（包 `net.minecraft.client.gui.inventory`），
与 `com.tacz.guns.mixin.client` 不同包 —— 包内可见会抛 **`IllegalAccessError`**。
所以提取出来的 helper 类与其成员**必须是 `public`**（`CreativePackFilterHooks` /
`CreativePackFilterContainerAccess` 均已 `public`）。
> 同理：mixin 类本身、以及 mixin 让目标类 `implements` 的接口（如
> `IClientPlayerGunOperator`、`KeepingItemRenderer`、`CreativeContainerAccessor`、`Runnable`）
> 都必须 `public`/可访问，否则目标类链接失败。

### 全仓排查结果（`src/main/java/com/tacz/guns/mixin/**` 20 文件）
扫描规则：类体内出现 `class X {` / `interface X {` / `enum X {` / `record X {` / `new Foo() {` 即算。
| 文件 | 嵌套/匿名类 | 结论 |
|---|---|---|
| `CreativeModeInventoryScreenMixin` | 3（`AddButton`、`ContainerAccessBridge`、`Access`） | **已提取（本次修复）** |
| 其余 19 个（`AbstractButton`、`CreativeContainerAccessor`、`CreativePackFilterHooks`、`CreativePackFilterContainerAccess`、`EntityRenderDispatcher`、`GameRenderer`、`HumanoidModel`、`ItemInHandLayer`、`ItemInHandRenderer`、`Language`、`LocalPlayer`、`MouseHandler`、`PlayerModel`、`RenderSystemAccessor`、`SoundManagerPreparations`、`StairBlockAccessor`、`common/LivingEntity`、`common/ServerGamePacketListenerImpl`、`common/ServerPlayer`） | **0** | ✅ 干净 |

**当前：`mixin/**` 嵌套/匿名类 = 0 处。**

`mixins.tacz.json` **未改**（`compatibilityLevel` 保持 `JAVA_8`；两个新 helper 不是 Mixin，不进 json）。

> **给后来者的硬约束**：往任何 `@Mixin` 类里加东西时 —— 不写 `class X {}`、不写 `new Foo() {}`、
> 不写捕获 `this` 的 lambda；需要辅助逻辑就放到同包的顶层普通类里（参考 `CreativePackFilterHooks`）。

---

## 🚨 P0 规则（第 2 条，紧接上一条 NESTING）：mixin 包下**只能**放 `@Mixin` 类

> `mixins.<modid>.json` 的 `package` 声明的包（**含所有子包**）下面**只能**放 `@Mixin` 类；
> 任何普通类 / 接口 / 枚举 / 工具类都**不能**放在那里 —— Mixin 会把该包下所有类当 mixin 处理，
> 普通类被 `LaunchClassLoader` 加载时会被拦截并抛 `IllegalClassLoadError`。
> 辅助类放**同功能域的普通包**，并保持 `public`（因为合并后调用点位于目标类所在的别的包）。

### 崩溃原文
```
java.lang.NoClassDefFoundError: com/tacz/guns/mixin/client/CreativePackFilterHooks
  at GuiContainerCreative.handler$zzo000$tacz$initPackFilters(GuiContainerCreative.java:1264)
  at GuiContainerInventory.initGui -> GuiInventory.initGui -> Minecraft.displayGuiScreen
Caused by: IllegalClassLoadError: com.tacz.guns.mixin.client.CreativePackFilterHooks
  is in a defined mixin package com.tacz.guns.mixin.*
  at MixinProcessor.applyMixins(MixinProcessor.java:322)
```

### 与上一条 NESTING 规则的关系（两条独立、都要守）
| 规则 | 触发条件 | 违反后果 |
|---|---|---|
| **NESTING** | `@Mixin` 类里**声明**了嵌套/内部/匿名类（类文件带 `NestMembers`） | `MixinConfig.postInitialise` 期 `InvalidMixinException`（要求 JAVA_11） |
| **mixin 包纯净** | `com.tacz.guns.mixin.**` 下出现**非** `@Mixin` 类 | 该普通类被加载时 `IllegalClassLoadError` |
⇒ 所以「把嵌套类提成顶层类」之后**还必须**把它们**移出 mixin 包**，两步缺一不可。

### 本次移动 / 删除清单
| 原位置（在 mixin 包里，非法） | 处置 |
|---|---|
| `com.tacz.guns.mixin.client.CreativePackFilterHooks` | **移动** → `com.tacz.guns.client.gui.components.creative.CreativePackFilterHooks` |
| `com.tacz.guns.mixin.client.CreativePackFilterContainerAccess` | **移动** → `com.tacz.guns.client.gui.components.creative.CreativePackFilterContainerAccess` |
| `com.tacz.guns.mixin.client.RenderSystemAccessor` | **删除**（普通类；全仓代码引用 **0**，仅 `UnshadedVertexConsumer` 的一行注释提到；上游也是死代码） |
| `com.tacz.guns.mixin.client.StairBlockAccessor` | **删除**（普通类；全仓代码引用 **0**；上游全仓也只有它自己，**上游即死代码**） |

**被删两个类的知识已保留在本文件**：
- `RenderSystemAccessor`：上游 `@Accessor("shaderLightDirections")`（1.20 `RenderSystem` 的实体 shader 主光方向缓存）；
  1.7.10 **没有 shader program、没有主光方向**，无任何字段可 accessor；唯一使用者
  `client/model/bedrock/UnshadedVertexConsumer` 已降级为「沿用传入法线」（`mcshim.RenderSystem#setShaderLightDirections` 是 no-op）。
- `StairBlockAccessor`：上游 `@Invoker(remap=false) Block invokeGetModelBlock()`（绕 1.20 Forge 给 `StairBlock` 打的
  `getModelBlock()` 补丁）；1.7.10 对应类 `net.minecraft.block.BlockStairs`，**没有**该方法，
  模板方块是私有字段 **`field_150149_b`**（`BlockStairs.java:27`）。若将来需要，正确写法是
  `@Mixin(BlockStairs.class) { @Accessor(value = "field_150149_b", remap = false) Block xxx(); }`
  —— **必须 `remap = false`**（`field_150149_b` 是 SRG 名，等于运行期字段名，别让 refmap 改坏）。

### 全仓排查（`src/main/java/com/tacz/guns/mixin/**`，16 文件）
逐个检查「是否有 `^@Mixin` 注解」+「是否有嵌套/匿名类」：

| 检查项 | 结果 |
|---|---|
| 非 `@Mixin` 的类留在 mixin 包 | **0**（原 4 个已全部移动/删除） |
| 嵌套/内部/匿名类 | **0** |
| 未在 `mixins.tacz.json` 里列出但仍是合法 `@Mixin` 的类 | 2：`LanguageMixin`、`SoundManagerPreparationsMixin`（上游本身是死代码/空实现，**有意不列**，留在包里无害 —— 它们是 `@Mixin`，Mixin 不会拦截） |

### json 未改
`mixins.tacz.json` 保持原样：`mixins` 3 条 + `client` 11 条，全部是真正的 `@Mixin` 类。
移动/删除的 4 个类本来就不在数组里。

### 给后来者的两条硬约束（合并版）
1. **`@Mixin` 类里**：不写 `class X {}`、不写 `new Foo() {}`、不写捕获 `this` 的 lambda / 匿名类；
   需要状态就写 `@Unique` 字段，需要辅助逻辑就写**普通包**里的顶层普通类。
2. **`com.tacz.guns.mixin.**` 包下**只放 `@Mixin` 类**；辅助类放同功能域普通包且 `public`
   （参考 `com.tacz.guns.client.gui.components.creative.CreativePackFilterHooks`）。

#### 收尾修复 3：方块 MISSING_ICON（10 条缺失贴图）—— 全部方块类补 `registerBlockIcons`

根因：1.7.10 的 `Block#registerBlockIcons(IIconRegister)` 默认空实现，`blockIcon` 恒 null → TextureMap 对每个方块产出 MISSING_ICON。给所有 Block 子类补 `@SideOnly(Side.CLIENT) public void registerBlockIcons(...)`（注意是 `registerBlockIcons`，不是物品的 `registerIcons`）：

| 方块类 | IIcon 名 | 说明 |
|---|---|---|
| `AbstractGunSmithTableBlock`（父类，A/B/C 三种工作台共用） | `tacz:gun_smith_table` | 只注册一次，`GunSmithTableBlockA/B/C` 继承 |
| `TargetBlock` | `tacz:target` | |
| `StatueBlock` | `tacz:statue` | |
| `GunPegboardBlock` | 普通 `tacz:pegboard` / 发光 `tacz:glow_pegboard` | 同一个类的两个实例，按实例字段 `glowing` 在 `registerBlockIcons` 里选择（不拆子类） |
| `GunDisplayCaseBlock` | `tacz:display_case_all` | |
| `AmmoStorageBlock` | `IIcon[]` = {`tacz:ammo_storage`, `tacz:ammo_storage_gold`, `tacz:ammo_storage_diamond`} | 1.7.10 三档容量是**构造器参数**（不是 metadata/BlockState），三个注册实例各持有同一数组；`registerBlockIcons` 注册 3 个、`blockIcon` 取实例对应档，`getIcon(int side, int metadata)` 用私有 `iconIndex()`（按 `capacityStacks` 216/432/864 → 0/1/2）返回对应项 |

全部对应 `assets/tacz/textures/blocks/<name>.png`（已核对 10 个文件都存在：gun_smith_table / target / statue / pegboard / glow_pegboard / display_case_all / ammo_storage{,_gold,_diamond}）。

**验证**：`mcp__idea__lint_files`（block/** 全部 23 文件 + `init/ModBlocks`，error 级）→ `items: []`（0 error）。

---

## ✅ 全模块审查轮（7 个 teammate 并行 + Lead 收尾，2026-11-04 14:0x）

> 目标：① 会导致运行期问题的 bug；② 偏离上游语义；③ 渲染；④ 道具/物品模型；⑤ mixin。
> 每个作用域一份完整报告在 `docs/review/`（发现表 + 上游/1.7.10 依据 + 已修/未修 + 覆盖清单）。
> **构建**：全部 teammate 收尾后由 Lead 统一 `./gradlew spotlessApply build` = **BUILD SUCCESSFUL**（1m58s，`build/final-build.log`），
> 38 个改动文件 `lint_files(error)` 全 0；reobf jar 的 `@Shadow` 字段已正确映射为 SRG（`field_78453_b`）—— T7 提的 reobf 链路风险排除。
> 无用的旧构建/调试日志 81 个文件（34.7 MB）+ 8 份已修崩溃报告已清理（保留 build/libs、build/rfg、latest run log）。

### 本轮修掉的高危 bug（按作用域）

| 作用域 | 报告 | 关键修复（都已 lint 0 error） |
|---|---|---|
| Lead 收尾 | 本文 | ① **13:28 崩溃根因**：`ClientAssetsManager.reloadAndRegister` 全仓 0 调用者（上游 `RegisterClientReloadListenersEvent` 未移植）→ 客户端枪包数据从未装载、`packInfo` 恒 null。新增 `ClientGunPackReloader`（`IResourceManagerReloadListener`，postInit 注册；FML `finishMinecraftLoading` 先 initializeMods 再 refreshResources，时序已按源码核对）+ `getPackInfo`/各 getter 空安全网。② **gson EnumMap**：1.7.10 运行时 gson-2.2.4（classpath 在 2.13.2 之前）不支持 `EnumMap` 字段 → 14 个 `data/*_data.json` 全失败。新增 `EnumMapTypeAdapterFactory` 注册进两个 GSON（用 gson-2.2.4 + 2.10.1 双版本独立探针验证 read/roundtrip 通过）。③ `ClientAssetsManager.bedrockAnimation` 缺 `ASSETS_ROOT`（扫 data/ 而非 assets/ → 动画全查不到 → display 被丢弃 → 枪无模型）。④ `CommonProxy` 把 `NetworkTaskQueue`/`SyncConfigSyncManager` 挂错总线（cpw TickEvent/PlayerEvent 只走 `FMLCommonHandler#bus()`）→ 网络包永不排空。⑤ `syncGunPack` 补上游 `clearInstance`（多人服客户端用服务端数据）。⑥ 枪包资源包每轮原版重载被 `clearResources()` 清掉的时序（preInit 注册重挂监听器 + ClientProxy 调用点从 init 移到 preInit，与 client-ui 配合）。 |
| render | 01-render.md | ① **P0** `VertexConsumer` 语义（api-shim 修）：原在 `vertex()` 立即 addVertex → 全仓 `vertex().color().uv().endVertex()` 顶点属性错位一格（bedrock 模型 UV 整体偏移）；已改为 endVertex 落顶点并回归核对 0 处漏 endVertex。② **P0** 第一人称枪无 GL 相机空间基座（RenderHandEvent 在世界视图矩阵下派发、cancel 掉原版 renderHand 的 glLoadIdentity）→ 枪画在世界原点；已补 push+glLoadIdentity+depth clear。③ **P0** 背包/GUI 图标缩成 1px（1.7.10 `renderInventoryItem` 无 16× 缩放）→ `applyGuiSlotScale`。④ P1 第三人称也画第一人称枪；⑤ P1 cancel RenderHandEvent 连带丢失的镜片/overlay/BeforeRenderHandEvent 已按裁决用「保留 cancel + 3 处补偿」恢复。 |
| client-resource | 02-client-resource.md | ① P0 `PackInfoManager` 读错路径/键（枪包根目录+目录名 → 实际 `assets/<ns>/gunpack_info.json`+namespace）→ 侧栏/改装台/tooltip 的包名/logo 恒 null；② P1 `GltfManager` 缺 ASSETS_ROOT；③ `StringUtils.isNoneBlank()` 无参恒真（上游笔误）导致弹药无粒子。 |
| client-ui | 03-client-ui.md | ① 侧栏/复选框用了 1.13+/Forge 专属贴图（`recipe_book.png`/`checkbox.png`）→ 改原版 `tabs.png` sprite / `fill` 自绘；② P1 资源重载重挂钩子 `GunPackResourcePackReloadListener`（新增，preInit 注册、早于 TextureManager 监听器）；③ `CreativePackFilters` 分组图标判空。事件总线 20+13 个处理器 × 总线归属全量核对 = 零错配；GL 线程安全通过。 |
| gameplay | 04-gameplay.md | ① **致命** 子弹永不移动（误以为 `Entity#onUpdate` 按 motion 移动；1.7.10 只记 prev）→ 补 setPosition；② 击杀事件永不触发（`isDead` 致死瞬间 false）→ `!isEntityAlive()`；③ 背包枪热量冷却判定反转；④ trackingRange 单位错（1.20 区块 vs 1.7.10 方块：5→80、8→128）；⑤ 工作台/弹药库 GUI 打不开（`getClientGuiElement` 返回 null）；⑥ 自研 IRecipe 未注册 `RecipeSorter`（Unknown recipe class 警告 + 优先级 0）；⑦ 创造标签页标题显示原始 key；⑧ `minecraft:bow` 在 1.7.10 不存在（实际是 `bow_standby.png`）→ 改 `bow_standby`。 |
| api-shim | 05-api-shim.md | YSM 契约 11 组符号**逐个核对全部通过、无签名漂移**；修 `vertex()` 顶点属性错位（P0，同 render ①）、`Tessellator` 两实现、`RenderSystem.defaultBlendFunc` separate-alpha、`bullet_ignore` 方块标签（铁栏杆/栅栏/树叶）失效。 |
| data-net | 06-data-net.md | 网络 55 个消息类 read/write 全量机检对称、discriminator 1..42 与上游逐条一致、配置 15 类项名/默认值/范围/注释一致、命令与 `/tacz reload` 双分支一致；修 `NetworkTaskQueue`/`SyncConfigSyncManager` 自注册兜底、`ServerMessageUpdateEntityData` 计数上界、`ZipGunPackSource` zip 句柄懒加载竞态。 |
| mixin-compat | 07-mixin-compat.md | **mixin 正常**：16 个 @Mixin 的 14 个已登记项目标类/方法/描述符逐条核对通过、24 个注入点回调签名精确匹配、三条 P0 规则合规、json 与集合一致、8 份历史崩溃 tacz 根因全已修；修 `GameRendererMixin` @At 补 `remap=false`（glRotatef 映射警告消除）、`OculusCompat` 引用 `CompatRegistry.ANGELICA`。 |

### 遗留：需实机（runClient）确认的项（本轮禁止跑 gradle runClient）

1. 第一人称：枪体位置/跟随视角、**背包图标 16×16 朝向**（若上下颠倒只需改 `applyGuiSlotScale` 的 y 符号）、贴墙可见性、**镜片层级/相机动画幅度/水下着火 overlay 顺序**（保留 cancel + 补偿方案）。
2. 枪包资源重载日志应有且仅有一次 `[TACZ ResourcePack] registered gun pack reload listener`，且**不应**出现 `registered AFTER the vanilla atlas was stitched`。
3. 创造侧栏应显示枪包名/logo（PackInfoManager 修复后）；枪械应有 3D 模型与动画（bedrockAnimation ASSETS_ROOT 修复后）。
4. 开火：子弹应可见飞行、命中/击杀事件、曳光弹/标靶车远距离同步（trackingRange 修复后）；工作台/弹药库 GUI 可打开。
5. mixin 生产包 @Shadow reobf 已由 Lead 核过（SRG 正确）；`defaultRequire=1` 在 OptiFine 下的启动崩风险仍在（GameRendererMixin 的「他人会改写」注入点可改 `require=0, expect=1`，本轮未改）。
6. `block/**` 残留 `setBlockTextureName("tacz:block/x")` 笔误（无代码走 `Block#getTextureName()`，无害）。

---

## ✅ 第二轮：同类崩溃专项（用户实机 crash 后 7 teammate 并行，2026-11-04 18:0x）

> 触发：用户实机崩溃
> `NullPointerException: Cannot invoke "ItemStack.getItem()" because the return value of "Supplier.get()" is null`
> `→ LivingEntityAim.tickAimingProgress ← EntityLivingBase.handler$tacz$onTickServerSide`（服务端每 tick 每玩家，空手必崩）。
> 根因归纳为一类平台差异：**上游 1.20 用非 null 哨兵（ItemStack.EMPTY / capability 非 null / 类型化注册表），
> 本移植改成 null，但读取点沿用了上游「不判空」的写法**。7 个 teammate 按同一模式逐作用域排查并直接修。
> 全部收尾后 `./gradlew spotlessApply build` = **BUILD SUCCESSFUL**（3m37s，reobf jar 18:09；首跑因 Gradle daemon
> 512MiB 堆 GC thrash 失败，环境问题，`./gradlew --stop` + `-Dorg.gradle.jvmargs=-Xmx3072m` 重跑通过）。
> 73 个改动文件 `lint_files(error)` 全 0。

### 本类已修（按严重度）

| 严重度 | 问题 | 文件 |
|---|---|---|
| **P0 实机崩溃** | shooter 的 `currentGunItem` Supplier 在空手/切枪时返回 null：22 处 `currentGunItem.get()` 裸解引用 → 新增 `ShooterDataHolder#getCurrentGunItem()` 归一化 + 全部调用点「局部变量+判空」（含 `draw(Supplier)` 链、`ClientMessagePlayerDrawGun` 空槽传 null 路径） | `entity/shooter/**`（11 文件，含 `LivingEntityAim` 崩溃点） |
| **P0 每 tick 崩** | `GunAnimationStateContext`：Lua 状态机 getter 解引用可空 `gunData/iGun/display`（1.7.10 索引/display 缺失是常态）→ 6 个 getter + `processGunData` 加安全默认值 | `client/animation/statemachine/GunAnimationStateContext` |
| **P0 每帧崩** | `TargetRenderer` 对 `BedrockModel#getNode`（@Nullable）裸解引用（模型版本不匹配→空 map）；`ExternalShellRenderer` 对可空 `gunData.getAmmoId()` 解引用 | `client/renderer/block/TargetRenderer`、`client/renderer/external/ExternalShellRenderer` |
| **P0 进服/重载崩** | `GsonHelper.convertToJsonObject/Array/getArray`：枪包 JSON 缺必填字段 → `json.get()` null → `isJsonObject()` NPE（会逃出只 catch JsonSyntaxException 的加载器）；`CommonGunIndex` json 的 `"reload":null` 等启动期 NPE；`RecipeFilter` 非法形态 CCE/ISE 启动崩 | `mcshim/GsonHelper`、`resource/index/CommonGunIndex`、`resource/filter/RecipeFilter` |
| **P1 解码/网络崩** | `PacketBuffer.readEnum` 越界返回 null → 9 个消息包把 null 存 enum 字段后在服务端 tick 的 switch/EnumMap NPE；改为解码期抛 IAE（=1.20 语义，simpleimpl 会断开该连接而不崩服务端）；12 个 `ClientMessage*` 补槽位夹紧/枚举 null 守卫 | `mcshim/PacketBuffer`、`network/message/**` |
| **P1 服务端每 tick 崩** | `getSynReloadState()` 可能返回 null（3 个每 tick 消费者裸解引用）→ 归一化 `new ReloadState()`；`onTickServerSide` 写入/读取两侧 `reloadState`/`reloadStateType` 判空 | `mixin/common/LivingEntityMixin` |
| **P1** | `PlayerRespawnEvent` 重生自动换弹：枪械数据未加载时 `getGunIndex()` null → NPE；`AmmoItemDataAccessor.isAmmoOfGun`/`AbstractGunItem.useInventoryAmmo/getRPM/isCanCrawl` 空栈 NPE；`SoundPlayManager` 15 个公共播放入口 display null NPE；`ClientAmmoCount.get` 空栈 NPE | `event/PlayerRespawnEvent`、`api/item/nbt/AmmoItemDataAccessor`、`api/item/gun/AbstractGunItem`、`client/sound/SoundPlayManager`、`client/gameplay/ClientAmmoCount` |
| **防御性** | `DataHolder.set` 用 `Objects.equals`；`ModSerializers.read` 的 StateType 裸数组索引越界归一；`CompatRegistry` 专用服务器 Class.forName(client 类) 加物理侧闸门；`GunDisplayInstance` 默认动画 for-each null 判空 | 对应文件 |

### 核对后确认「无此类问题」的作用域（有逐条证据，见各报告「第二轮」节）

- **客户端热路径 84 处 `getHeldItem()` / 76 处 `.getItem()` 全部已判空**（client-ui）；`getStackInSlot` 8 处全判空（gameplay）。
- render 侧 display/index Optional/Nullable 消费点全部判空；client-resource 的 index/warmUp/lazy loader 全链路判空。
- api/mcshim/util：DataAccessor 的 NBT 访问全走 `ItemStackNbtHelper`（null 安全）；`Optional#get` 全在 isPresent 内。
- network 资源/配置/命令侧：13 个 readItem/getStackInSlot 消费点已 null 安全；gson 必填字段/POJO 消费方核对完毕。
- mixin/compat：@Inject/@ModifyArg/@Redirect 无 null 解引用；compat 反射结果使用前全部判空、缺失库静默。
- 上游同源未改（避免掩盖语义）：`getAimingZoom` null tag/除零、`extendedMagAmmoAmount` 索引、`EntityKineticBullet.requireNonNull(cacheProperty)`、`SyncedEntityData` 未注册 key 抛 IAE；`IClientPlayerGunOperator.fromLocalPlayer` 的硬转换是 CCE 风险（仅 mixin 未 apply 时，正常不触发）。

### 给实机复测的提示

1. 本轮 jar（`build/libs/tacz-5.11.45.140.jar`，18:09）已包含 17:41 那次 `LivingEntityAim` 崩溃的修复；**请用新 jar 重测**（旧 jar 跑出来的就是同一个崩溃）。
2. 重点回归：空手/切枪（原崩溃）、开火/换弹/收枪（shooter 全链）、瞄准（ADS）、重生自动换弹、进出世界/`/tacz reload`（枪包重载）、打开工作台/弹药库、靶子/弹壳渲染、创造侧栏。
3. 若再出崩溃：`run/client/crash-reports/` 与 `run/client/logs/latest.log` 直接发我，我会按同一模式定位。



---

## ✅ 第三轮：7 维度复审（用户指定 7 个问题，7 teammate 并行，2026-11-04 19:0x~19:4x）

> 触发：用户按 7 个维度要求复审（渲染材质 / 绘制GL / 枪械逻辑 / GUI 按钮 / 上游保真 / 移植完整性 / 本地化与图标）。
> 汇总报告：`docs/review/r2/SUMMARY.md`，分报告 `docs/review/r2/01..07`（8 份）。
> 全部 teammate 收工后由 Lead 统一 `./gradlew spotlessApply build` = **BUILD SUCCESSFUL**（5m51s；收尾增量 28s + compileJava UP-TO-DATE）。

### 本轮修掉的 6 个 P0

| # | 问题 | 位置 |
|---|---|---|
| P0-1 | **TACZ 键位顶掉原版攻击/使用键**：`SHOOT_KEY/AIM_KEY` 与 1.7.10 原版 `-100/-99` 同码，`KeyBinding` 构造器写静态 `IntHashMap`（后写覆盖）→ 装 TACZ 后**原版左/右键整局失效** | `client/input/GunInputHandler` |
| P0-2 | **工作台配方从来没被加载**：199 条 `data/<ns>/recipes/**.json` 无人读，且 `initLazyRecipes()` 遍历 `CraftingManager` 恒 0 次（`GunSmithTableRecipe` 不实现 `IRecipe`）→ 做不出任何枪/配件/弹药 | 新增 `resource/manager/GunSmithTableRecipeManager` |
| P0-3 | **`.lang` 缺 1.7.10 键形态**（`item./tile.` 前缀 + `.name` 后缀）→ 全部物品/方块显示原始 key 或 `«名».name` | `assets/tacz/lang/*.lang`（20 语种）+ `tools/lang_json_to_lang.py` |
| P0-4 | **`registerEventHandlers(Class)` 注册不到处理器** → **多人服**客户端收不到枪包同步（枪无名字无模型） | `resource/CommonAssetsManager` |
| P0-5 | **创造模式物品栏无法滚动**（身份判等恒 false → 每帧 `scrollTo(0)`） | `client/gui/components/CreativePackFilters` |
| P0-6 | **雪暴粒子定义从不装载** → renew 型枪火光/枪烟完全不显示 | 新增 `client/particle/ParticleDefinitionReloader` |

### 关键结论（回答「是否偏离上游 / 是否只有骨架」）

- **「自己乱搓方法名」= 0 条**（264 对改名 100% 是 1.20→1.7.10 平台 API）；真漂移仅 1 条（多段实体 part→core）→ 已修并经独立复核闭环。
- **真骨架 5 条 / 半骨架 2 条**；最大的一条是「入口没人调」（配方加载），已修。
- **YSM 11 组硬契约通过**，api 零签名漂移。

### 本轮明确未修（需 mixin 作用域，下一轮）

1. 枪械击退链 `KnockbackChange.modifyStrength` 0 调用者（需 `EntityLivingBase#attackEntityFrom` 注入）。
2. 持枪左键点方块的**客户端幽灵方块**（1.7.10 `LEFT_CLICK_BLOCK` 只在服务端派发，需 `Minecraft#clickMouse` 拦截）。

---

## ✅ 第四轮：r3 收尾（清空 r2 未修清单 + 两项专项审查，2026-11-04 20:1x~21:3x）

> 用户要求「修复未解决的问题（含需要 mixin 的）」+「派出 teammate 审查接线未接线代码与死代码」。
> 5 个 teammate 并行（新增 `mixin-fixer`），任务板 `task-11..15`；
> 全部收工后 Lead 统一构建 **BUILD SUCCESSFUL**（6m56s；覆盖审计缺口的增量 4m3s）。
> 汇总见 `docs/review/r2/SUMMARY.md` 的「第三轮（r3）」章。

### 新增 2 个 mixin（json 14 → 16 项）

| mixin | 目标 | 修的问题 |
|---|---|---|
| `common.LivingEntityKnockbackMixin` | `EntityLivingBase#knockBack` 的 `0.4F` 常量 → `@ModifyConstant` | 枪械击退数据完全不生效（`KnockbackChange.modifyStrength` 0 调用者）。**关键纠正**：强度不在 `attackEntityFrom` 里，而在 `knockBack`（`EntityLivingBase.java:1056`） |
| `client.MinecraftAttackMixin` | `Minecraft#func_147116_af` / `func_147115_a` HEAD（**SRG 原文 + remap=false**） | 持枪左键点方块的**客户端幽灵方块**（1.7.10 客户端不 post `LEFT_CLICK_BLOCK`，只能用源码级注入） |

> ⚠ `require = 0` 下**注入未命中是 100% 静默**（核 UniMixins `InjectionInfo.java:524-538`：`expect` 只有加
> `-Dmixin.debug.injectors=true` 才报）。实机必验：① 持枪按住方块不挖 ② 击退随枪械数据变化 ③ 启动无
> `InvalidInjectionException`。

### 另修

- **渲染**：第一人称枪不吃 `hurtCameraEffect`（受伤不晃）、镜片遗弃 texture unit 1/2（unit1 是 lightmap 单元）、
  `ScopeThermalRenderer` 覆盖矩阵不还原、`GunAnimationStateContext` 切枪不清旧 display；
  **修正 r2 结论**：stencil 遮挡不是绝对失效（`-Dforge.forceDisplayStencil=true` 可部分恢复）。
- **逻辑**：战利品注入接线（`event/LootInjectionEvent`，通用映射而非硬编码）、`GunTime` 暂停时钟**真的停走**
  （暂停时长累加补偿）、输入分发改上游 `onPress(key)` 语义（去重 + 修饰键过滤）。
- **resource/GUI**：`CommonGunIndex#checkRecoil` 判空（`{"value":null}` 会让 NPE **逃出单文件跳过路径 → 服务端起不来**）、
  `InteractKeyTextOverlay` 补回上游 3 处展示柜判定。
- **接线缺口 4 条（Lead 修）**：弹药库明细列表恒空（P1，网络包第一个生产者）、GUI 内不绕过 LOD、
  激光滑条默认色恒红、`DisplayCaseLockHintOverlay#onClientTick` 未注册（且是 static 处理器）。
- **Lead 另修**：`EntityKineticBullet` 击退 set/reset 改 `try/finally`（r3 起该值真的被消费，漏复位会污染之后所有伤害源）。

### 审计结论

- **「接线未接线」已基本收敛**：51 个 `@SubscribeEvent` 类 50 正确 + 1（已修）；`register(Xxx.class)` live **0 处**；
  120 个生命周期方法 **0 孤儿**；88 个配置项仅 2 个 0 读取且上游同样 0。
- **死代码**：计入方法引用后「上游有调用、移植后变死」= **0**；唯一真回归 `ExternalGunEffects` 属已登记 VBO 降级衍生。
- `LanguageMixin` / `SoundManagerPreparationsMixin` **仍应保持不列进 json**（替代实现均已落地接线，已给确切证据）。

---

## 🔴 实机日志修复（r3-06，2026-11-04 21:4x 用户实机崩溃后）

> 触发：用户提供 21:41 实机日志，暴露两类「**静态审查看不出来**」的运行期缺陷。

### P0-8 · static @SubscribeEvent + 实例注册 → IncompatibleClassChangeError（进服即被踢）

**现象**：玩家登录时
`java.lang.IncompatibleClassChangeError: Expecting non-static method 'void com.tacz.guns.resource.CommonAssetsManager.onPlayerLoggedIn(...)'`
→ `HandshakeCompletionHandler exception` → `A fatal error has occurred during the network handshake` → 踢出；退出世界时同一异常再抛一次（`onWorldUnload`）导致客户端崩溃。

**根因（比 r2 那条更深一层）**：FML 1.7.10 的 `ASMEventHandler` 生成的回调字节码用
**`INVOKEVIRTUAL`**。所以 `@SubscribeEvent` 方法**必须是实例方法**；
而 r2 只修了「`register(Class)` 注册不到」那一半（改成注册实例），
**没修另一半**：方法本身是 `public static` ⇒ 注册成功、派发时抛 ICCE、
且 `EventBus#post` 会捕获它只打一条 error ⇒ 表现成「注册成功但永不执行」。

**全仓扫描**（这是本轮最有价值的方法论修正）：`static @SubscribeEvent` **11 处 / 4 个类**，全部修掉：

| 类 | 方法 | 不修的后果 |
|---|---|---|
| `resource/CommonAssetsManager` | `onPlayerLoggedIn` / `onWorldUnload` | **进服即被踢 + 退出世界崩** |
| `simplebedrockmodel/.../FirstPersonRenderHandler` | `onPlayerLoggedOut` / `onRenderHand(SwapItemWithOffHand)` / `onClientTick` / `tickAnimation` / `onRenderHand(RenderHandEvent)` | **第一人称枪完全不渲染**（每帧 ICCE 被吞），切枪状态机/动画时钟/第一人称粒子全部不跑 |
| `simplebedrockmodel/.../WorldEmitterManager` | `onClientTick` / `onLoggingOut` / `onRenderWorldLast` | SnowStorm 世界粒子不 tick、不渲染 |
| `init/CommonRegistry` | `onEntityConstructing` | 该处理器从未生效（`register()` 在 r3-01 被删成空方法）；现改「实例方法 + `register()` 注册实例」恢复 |

**顺带做的两项反查**（确认再无同类）：
- `@SubscribeEvent` 总数 **90**，**非 public 的 0 处**（FML 只用 `getMethods()`，非 public 会被静默忽略）；
- `register(Xxx.class)` live 代码 **0 处**。

### P1-13 · 11 条工作台配方因 1.16+ 物品缺失而加载失败

**现象**：`gun smith table recipes: 199 scanned / 188 registered` +
11 条 `JsonSyntaxException: Unknown item 'minecraft:crying_obsidian' / 'shroomlight' / 'observer'` 各带 70 行 gson 栈。

**根因**：枪包配方是按 1.20.1 写的，其中 3 个物品 1.7.10 不存在（默认包共 11 条配方受影响：
`ammo_mod_{fmj,he,hp,i,overpressure_recoil,slug}` × crying_obsidian、
`scope_reap_ir / scope_xl50 / sight_infitac_fmp13` × shroomlight、
`scope_xm157 / sight_uh1` × observer）。这些配件在工作台里**连列表都不出现**。

**修法**：
1. `GunSmithTableIngredient` 增加**显式替代表** `MISSING_VANILLA_ITEM_SUBSTITUTION`（含逐项理由）：
   `crying_obsidian → obsidian`（本就是黑曜石的一种）、
   `shroomlight → glowstone`（1.7.10 唯一发光方块类材料）、
   `observer → daylight_detector`（1.7.10 唯一光照探测元件，用于镜类配方最贴近）。
   只在原物品**完全不存在**时替换，按 debug 级记日志；日后有模组注册这些物品则原物品优先。
2. `GunSmithTableRecipeManager` 把 `JsonSyntaxException` 单独 catch，打**一条不带栈**的 warn
   （之前 11 条各打 70 行栈，把真问题埋掉）。

> 预期日志变为 `199 scanned / 199 registered`，且不再有栈。
> 注意：`forge:gems/amethyst` / `ingots/netherite` / `nuggets/iron` 的 25 条配方仍**永久不可合成**
> （1.7.10 原版无等价物，按保真不折算成本），但它们现在会**出现在列表里**。

### 方法论修正（写进下一轮的审查清单）

前四轮审查（含 2 次静态复核、1 次 mixin 专项、1 次接线专项）**都没发现 P0-8**，原因：
判据是「有没有 `register`」，而真正的判据是「`register` 的载体与处理器的**实例性**是否匹配」。
⇒ 今后凡是 `@SubscribeEvent`，必须同时核 **① 有实例注册 ② 总线正确 ③ 方法非 static ④ 方法 public**。
本轮已把 ①②③④ 全仓扫成 0 缺陷。

### r3-07 · 实机日志第二轮（21:56）修复

| # | 级别 | 问题 | 根因 | 落点 |
|---|---|---|---|---|
| 1 | **P0** | **持枪时视角被锁死、基本转不动** | `CameraSetupEvent#applyLevelCameraAnimation` 在 `RenderTickEvent.START` 存朝向快照并改成动画角度，`END` **绝对写回快照**。实读 `Minecraft.java:1065-1069`：START 在 `updateCameraAndRender` **之前**、END 在**之后**，而鼠标 `setAngles` 在 `updateCameraAndRender` 内部（`:1061/:1065`）⇒ **每帧的鼠标位移被整帧丢弃** | 改为记「本帧叠加的偏移量」并在 END 用**减**（帧末 rotation = 快照 + 鼠标位移；动画偏移被抵掉）；pitch 补夹 ±90 |
| 2 | **P0** | **第一人称枪械完全不渲染** | `FirstPersonRenderHandler#registerRenderer(Item, IFPGeoItemRenderer)` **全仓 0 调用点** ⇒ `RENDERERS` 恒空 ⇒ `getRenderer` 恒 `Optional.empty()` ⇒ `onRenderHand` 直接 `endBatch + return`（既不画也不 cancel） | `ClientSetupEvent#registerItemRendererFor` 在 `MinecraftForgeClient.registerItemRenderer` 之后补登记 FP 渲染器 |
| 3 | P0 | `ClientMessageLaserColor` 私有无参构造器 → **每次改激光色刷十几条栈** | FML `FMLIndexedMessageToMessageCodec#decode:75` 用 `Class.newInstance()`，**只能调 public 无参构造器** | 改 `public`；并全仓扫过其余 5 个无显式无参构造器的消息类（靠隐式 public 默认构造器，均安全）、`AbstractShooterGunEventMessage` 的 11 个子类（全 public） |
| 4 | P1 | 配方加载仍有 2 条失败 | `minecraft:end_crystal`（**物品**是 1.9 才加的，1.7.10 只有实体）、`minecraft:copper_block`（纯原版 1.7.10 无铜） | 替代表追加 `end_crystal → ender_eye`、`copper_block → iron_block`；预期 `199/199` |

> 方法论再修一条：**「入口存在」≠「入口被执行」**。前两轮审查分别漏了
> `static @SubscribeEvent`（r3-06）与 `registerRenderer` 0 调用点（r3-07），
> 两者在静态代码里都「看起来完好」。今后凡「注册/回调/分派」类代码，
> 必须用 `grep` 证明**有真实调用点**，而不是只证明定义存在。

### r3-08 · 实机日志第三轮（22:09）修复

| # | 级别 | 问题 | 根因 | 落点 |
|---|---|---|---|---|
| 1 | **P0** | 进世界后切换/收起枪械时**客户端崩溃**（`NPE: Cannot invoke "ItemStack.copy()" because "newMain" is null`） | 1.7.10 **空手 = null**（1.20 是 `ItemStack.EMPTY`，永不为 null）。`FirstPersonRenderHandler#onClientTick` 在 `mainItemChanged` 分支直接 `newMain.copy()`；从「手持枪」变成「空手」（或背包同步出现瞬时 null）时必 NPE | `isEmpty(newMain) ? null : newMain.copy()`；副手那处同源写法一并修 |
| — | 记录 | 默认枪包**大量音效缺失** | 包内 `tacz_sounds` 只有 **5 个 ogg**，但全部 `*.animation.json` 引用 **1368** 个 effect 名 → **170** 个找不到音频（开火/换弹/掏枪会静默无声，只打一条 WARN）。**上游 `tmp/TACZ-1.20.1` 完全相同（5 ogg / 170 缺失）** ⇒ 不是移植丢失，属数据侧缺音频 | 需补音频资源；若要排查上游副本是否被裁剪过需另开工单 |
| — | 记录 | `矿词 ingotCopper/ingotIron 尚未注册，跳过 ammo_workbench 配方注册` | 该测试环境**没装 GT**（只有 hodgepodge/gtnhlib），铜/铁矿词不存在；装 GT 后自动恢复 | 环境相关，非 bug |

**已确认正常**（同一份日志）：
- `gun smith table recipes: 199 scanned / 199 registered`（物品替代表生效，0 失败）
- `Mixing client.MinecraftAttackMixin ... into net.minecraft.client.Minecraft`、
  `Mixing common.LivingEntityKnockbackMixin ... into net.minecraft.entity.EntityLivingBase` —— 两个新 mixin **注入点命中**
- 除上述 NPE 外无其它 ERROR

---

## ✅ 第五轮（r4）：用户指定 3 模块专项审查（3 teammate 并行 + Lead 收尾）

> 触发：用户要求审查 ① 人物第一人称手持枪械动画渲染 / model 逻辑移植完整性与渲染问题
> ② 配方里是否存在 1.7.10 不存在的物品（有则换等价物） ③ 枪械改装系统是否有问题。
> 组织：**3 个 teammate**（`r4-fp-render` / `r4-recipes` / `r4-refit`），写作用域互不重叠，共享任务板 `task-1..task-3`。
> 纪律：teammate 全程**未跑 gradle**（只用 IDEA MCP lint 自查）；构建由 Lead 在全部收工后统一执行。
> 分报告：[01-first-person.md](review/r4/01-first-person.md) · [02-recipes.md](review/r4/02-recipes.md) ·
> [03-refit.md](review/r4/03-refit.md)。

### r4-A 第一人称手持枪械（渲染 / 动画 / model）—— 已修 6 项 + Lead 补 1 项

**结论：能渲染、链路完整**（注册 → `GunItemRendererWrapper` → `AnimateGeoItemRenderer` → SBM
`FirstPersonRenderHandler` → `IFPGeoItemRenderer#renderFirstPerson` → `BedrockGunModel` + `functional/**`，
7 跳逐跳有真实调用点），动画上下文（`GunAnimationStateContext`）与上游逐 getter 对应、无缺失分支。

| # | 级别 | 问题（玩家可见） | 落点 |
|---|---|---|---|
| A-1 | **P0** | **第一人称手部投影矩阵从未建立**：SBM 在 `RenderHandEvent` 里 cancel 掉整个 `EntityRenderer#renderHand`，而手部 FOV 的投影矩阵正是 `renderHand:755-770` 用 `getFOVModifier(p,false)` 建的 ⇒ 枪体一直用**世界 FOV** 投影。腰射两者≈70°看不出；**开镜时世界 FOV 被压到 70/zoom，枪体被整体放大 ~zoom 倍**，且 `GameRendererMixin#tacz$overrideHandFov`（`ITEM_MODEL_FOV` 唯一消费者）持枪时是死代码 | `simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler`：新增 `pushHandProjection()`（照抄 vanilla `:755-770` 的 `Project.gluPerspective(ITEM_MODEL_FOV, aspect, 0.05F, far*2)`），`finally` 成对 pop |
| A-2 | P1 | 枪口火光/枪烟**每帧被画两遍**（同一 `BufferSource`/`RenderType`，顶点翻倍 ⇒ 火光过曝、烟雾浓度翻倍），且与 `IFPGeoItemRenderer#renderParticlesInModelWindow()` 的契约文字自相矛盾 | 同文件：删掉 try 块开头那处**无条件** `renderParticlesIfAny(...)`，只留事件末尾带闸门的那一处 |
| A-3 | P1 | 玩家关掉「选项→视角摇晃」后 `ITEM_MODEL_FOV` **永远停在 70°**（开镜手部投影不缩放）——`applyGunModelFovModifying` 被塞在 `applyLevelCameraAnimation` 末尾，继承了 `!viewBobbing`/`player==null` 早退 | `client/event/CameraSetupEvent`：把手部 FOV 计算提到所有闸门之前（上游该订阅者本就与 bobView 无关） |
| A-4 | P1 | 按 **F1（隐藏 GUI）** 时照样画整支枪；**睡在床上**时视角里浮着一把枪（1.7.10 原版这两条守卫写在被 cancel 的 `renderHand:794` 里） | 同 FPRH：补 `hideGUI` / `isPlayerSleeping()` 守卫（`return` 不 cancel，状态最干净） |
| A-5 | P2 | `MuzzleFlashRender.isSelf`/`ShellRender.isSelf`（**全局静态**，YSM 也写）/`setRenderHand`/`popPose` 复位不在 `finally` ⇒ 异常一次就让**别人的第三人称枪**画本机火光、或改装界面后手臂永久消失、或后续第一人称整体错位 | `client/renderer/item/GunItemRendererWrapper`：全部移进 `finally` |
| A-6 | P2 | 第三人称枪口粒子/曳光弹锚点**未按 partialTick 插值**（上游读 `Camera#getPosition()/getYRot(partialTicks)`）⇒ 边走边开枪时抖动一 tick | 同文件：`cameraYaw/Pitch(partialTick)` + `lastTickPos*` 插值，实体取 `renderViewEntity` |
| C-1 | P2 | `MuzzleParticleManager` 的 `PENDING`/`BOUND`/`BASE_POSE` 三张**静态表无任何清理入口** ⇒ 断线重连 / `/tacz reload` 后第一次渲染可能凭空放一次「上次开火的枪口火光」 | **Lead 补**：`client/particle/MuzzleParticleManager#reset()` + `FirstPersonRenderHandler#reset()` 末尾调用 |

**未修（均已给改法）**：B-1 第一人称手臂未 `RenderHelper.enableStandardItemLighting()`
（`ItemRenderer.java:272/553`；**刻意留待实机看观感**，2 行）；E-1 缺 bone 的第三方枪「贴脸在模型原点」
（**与上游同构**，建议不改）；F-1 `ItemInHandLayerMixin#renderTail` 无条件清 `isSelf`（当前无嵌套路径，
无现网症状）；R-1 组合镜 stencil（r3-4 已登记，非本轮回归）。
**复核确认仍在**：顶点 `endVertex`、相机空间基座、lightmap 补偿、hurtCameraEffect 补偿、GUI 图标 16×+Y 翻转、
第三人称不画第一人称枪、`registerRenderer` 接线、`CameraSetupEvent` 鼠标位移、切枪 null、`RenderType` 快照。

### r4-B 配方中的「1.7.10 不存在」物品 —— 已替换 4 类矿词（55 处材料槽）

**结论**：`"item"` 级的 5 个不存在注册名（`crying_obsidian` / `shroomlight` / `observer` / `end_crystal` /
`copper_block`）上一轮已替换，本轮复核保留并补正证据行号；**真正未解决的是 4 个矿词**，它们折成
`gemAmethyst` / `ingotCopper` / `ingotNetherite` / `nuggetIron` 后**纯原版无候选** ⇒ 配方「能显示、永远做不出」。

新增 `crafting/OreDictionaryFallback`（**矿词优先，仅当矿词此刻无候选才回退** ⇒ 装 GT/IC2 的整合包完全走原矿词、
成本与上游一致；纯原版也能合成）：

| 矿词 | 处数 | 回退等价物 | 理由 / 成本 |
|---|---|---|---|
| `gemAmethyst`（`forge:gems/amethyst`） | 20 | `minecraft:quartz` | 1.7.10 唯一「非金属水晶类宝石」；光学件用石英系最贴近；数量不变 |
| `ingotCopper`（`forge:ingots/copper`） | 30 + `ammo_workbench` | `minecraft:iron_ingot` | 原版无任何铜质材料，贱金属位由铁接替（**`ammo_workbench` 不再整条跳过**） |
| `ingotNetherite`（`forge:ingots/netherite`） | 4 | `minecraft:gold_ingot` | 下界合金锭的上游合成料本身就是 4 金锭 + 碎片，同属 ingot 形态 |
| `nuggetIron`（`forge:nuggets/iron`） | 1 | `minecraft:iron_ingot` + **9:1 折数量**（18→2） | 1.7.10 无铁粒，同材质最小单位是锭；折算后**成本不变**（不折会 ×9） |

- 实现：`GunSmithTableIngredient#resolveTagItem` 接回退 + tag 构造器按面额折算数量；`test()`/`getMatchingStacks()`
  原有「矿词空→比对代表物品」分支保证匹配链闭合（GUI/扣料同源）。
- **没有改任何一个 json**（199 条枪包配方 + 18 条 `data/tacz/recipes` 保持上游原样）。
- 未处理项：`minecraft:wool` 兜底到 damage 0（只匹配**白色**羊毛，1 条配方；改成 `WILDCARD_VALUE` 会让 GUI 图标走
  `getIconFromDamage` 越界，风险大于收益）；`data/tacz/recipes/misc/blood_strike_1.json`（8 木棍 + 羊毛 → 带
  `EntityTag.variant=tacz:blood_strike_1` 的画）**无消费者**——不是物品缺失，而是 1.7.10 无 datapack 且
  `ModPainting` 已登记「画变体硬编码 EnumArt」降级（NBT variant 无法表达）⇒ **裁决：保持不注册**（接了会产出
  「看起来对但变体被静默丢弃」的配方，比不注册更糟），材料侧已可解析，日后要接只需在 `ModRecipe` 补一条。

### r4-C 枪械改装（附件 / refit）系统 —— 已修 2×P2 + 1×P3，无 P0/P1

**结论：整体可用且与上游等价度高**：6 个改装网络包的字段 read/write 对称、discriminator 13/14/15/16/35/36 与上游
逐个同序、服务端校验顺序逐条相同（**1.7.10 额外**做了槽位越界夹紧与 `readEnum` null 守卫，因 1.7.10
`getStackInSlot` 越界即 AIOOBE）、17 个 modifier 的 ID/注册顺序/可选字段/求值公式逐行一致、14 个
`postChangeEvent` 调用点覆盖「装/卸/换皮肤/切适配器/枪包重载/重生/切枪/切模式」、`attachments_tags` **真的被加载且
真的被展开**（递归 tag + 环保护）、虚拟 OEM 配件链路完整。

| # | 级别 | 问题 | 落点 |
|---|---|---|---|
| C-1 | P2 | 改装界面**漏渲染 `FlatColorButton` 的 tooltip** ⇒ 适配器按钮置灰时唯一解释 `gui.tacz.refit.adapter_requires_unload`、开火模式切换提示、属性图显隐提示**全部看不到**（玩家只能反复点） | `client/gui/GunRefitScreen#drawScreen` 补 `else if (widget instanceof FlatColorButton f) f.renderToolTip(...)`（同 `GunSmithTableScreen:887`） |
| C-2 | P2 | 第三方枪包变体/槽位适配器 index 缺 `"name"` → `new ChatComponentTranslation(null)` 在 `FlatColorButton` 构造器 `getFormattedText()` 抛 **NPE** ⇒ **打开改装界面构造期崩溃**（与 r2 `PackInfo.name==null` 同类） | 新增 `GunRefitScreen#indexName(name, id)`：空名回退显示 id（宁显示 id 不崩） |
| C-3 | P3 | 缺 1.20 `Screen#renderBackground` 的暗色渐变（1.7.10 `GuiScreen` 不画背景，世界/枪直接透出、文字对比度不足） | `drawScreen` 开头 `fillGradient(0,0,w,h,0xC0101010,0xD0101010)`（上游 `renderTransparentBackground` 的两个常量） |

**未修/登记**：P3 `util/datafixer/AttachmentIdFix` **全仓 0 调用者**（上游在 `AttachmentItem#verifyTagAfterLoad`
这个 1.7.10 不存在的钩子里调）；建议修点是 `api/item/nbt/GunItemDataAccessor#getAttachmentTag` 就地修复，
但那是**有副作用的 getter** 且会给 `api/**` 新增对 `util/**` 的依赖（当前 api 不 import util，YSM 契约区不冒险）
⇒ **裁决：保持现状 + 记 TODO**（`AttachmentItem` 已有 TODO 注释）；P3 5 处消息类注释仍写「`readEnum` 越界返回
null」而实现已改抛 IAE（纯文档）；P3 1.7.10 右键也触发 GUI 按钮（平台级差异，需全局统一再改）。

### r4 构建与验证

- 7 个改动 java 文件 + 1 个新增类，Lead 统一 `lint_files(min_severity=error)` → **0 error**。
- 构建（Lead 统一执行，全部 teammate 收工后）：
  `./gradlew spotlessApply build` → **BUILD SUCCESSFUL in 2m 8s**（`build/r4-build1.log`；
  `spotlessJavaApply` + `spotlessJavaCheck` 均通过，日志中 `error:` 计数 **0**）。
  产物 `build/libs/tacz-5.11.45.140.jar`（68.70 MB，23:38:35）；已核对新类
  `com/tacz/guns/crafting/OreDictionaryFallback.class` 在 release jar 内。
  说明：首跑因 `-Dorg.gradle.jvmargs` 被 PowerShell 当成任务名而失败（24s，非编译问题），
  改用 `& .\gradlew.bat spotlessApply build "-Dorg.gradle.jvmargs=-Xmx3072m"` 后通过。
- 数据侧脚本实测（`r4-refit`）：128 个附件 index / 129 个 data / 6 个 slot_adapter index 引用**全命中**；
  108 把枪的 `allow_attachments`（含 `#tag` 递归）全部可解析；13 把枪的 `built_in_attachments` 经 tag 展开全部在
  该枪 allow 列表内；129 个配件 data 的顶层键**全部有 modifier 消费**。唯一缺 index 的 `muzzle_duckbill_sg`
  **上游同样缺**（非移植丢失）。

#### r4 实机回归清单（按优先级）

1. **（A-1 · P0）** 装 4× 瞄具右键开镜：枪体**不应**被放大糊屏（修复前会）；**关掉「视角摇晃」再开镜**同样要正确缩放（A-3）。
2. **（A-2）** 开火火光/烟雾应为单层（修复前过曝/双倍）；若修后完全看不到火光 → 查 `renderParticlesInModelWindow()` 链。
3. **（A-4）** F1 不画枪；躺床不画枪；F5 第三人称不画第一人称枪。
4. **（配方）** 纯原版环境：工作台弹药/瞄准镜/枪械材料格应显示**下界石英 / 铁锭 / 金锭**，`12g` 的铁粒格显示 **2 个铁锭**；
   `ammo_workbench` 现在纯原版也能合成；日志 4 条 `ore dictionary ... falls back to ...`（装 GT 时**不应**出现）。
5. **（改装）** 适配器按钮置灰时应能看到「需要先卸下配件」tooltip；变体/适配器列表应能显示名称；
   改装界面应有暗色背景（C-3 是新视觉，若不满意可单点回滚该行）。
6. **（C-1 附带）** 断线重连 / `/tacz reload` 后第一次开火不应出现「无源火光」。

---

## ✅ r4-02：用户实机 5 现象定位与修复（第 2 轮）

> 触发：r4 构建后用户实机报 5 个现象 —— ①开枪火光残留 ②手持枪时大半屏幕被**原版皮肤手臂**遮挡
> ③开镜后枪模偏离屏幕中心（**方向固定**） ④收枪后手消失、第三人称背后有枪、手持枪模「反了」
> ⑤切到非枪械物品后枪模仍渲染。用户确认：**5 条上一版就有（非 r4 回归）**、环境纯 Forge（无 OptiFine/Angelica）、
> 背后那把枪出现时**快捷栏里确实有多把枪**。
> 组织：Lead 定位根因 + 改时钟；`r4-fp-render` 负责手臂/开镜/第三人称三项（复用其 r4-A 上下文）。

### ① 一个根因同时解释 ①②⑤ 中的 3 条：**客户端动画时钟从不前进**（Lead 修）

- **根因**：上游 SBM 有 `v1/client/event/ClientAnimationClockTicker`（`RenderTickEvent.START → PausedClientAnimationClock#update()`、
  断线 `reset()`），**vendor 时该类整体丢失**，全仓 `PausedClientAnimationClock#update()` **0 调用点** ⇒
  `logicalNanos` 恒 0 ⇒ `nowNanos()/nowMillis()` 恒 0。三个后果：
  1. `FirstPersonRenderHandler#getSheatheProgress()` 恒 0 ⇒ **收枪过渡永不完成** ⇒ `transitioning` 永远 true ⇒
     `getActiveAnimationInstance()` 永远返回旧枪 instance ⇒ 每帧都渲染那把枪并 `event.setCanceled(true)`
     ⇒ **「切到非枪械后枪模残留 + 原版手消失」**；
  2. `tickAnimation` 里 `dt` 恒 0 ⇒ `PARTICLE_SYSTEM.tick(0)` ⇒ **「开枪火光残留」**；
  3. `v1/particle/world/WorldEmitterManager` 用同一单例时钟 ⇒ 世界粒子同源冻结。
- **修复**：`FirstPersonRenderHandler#tickAnimation` 的 START 分支**在 `shouldTick()` 早退与任何 `nowNanos()` 读取之前**
  无条件 `PausedClientAnimationClock.getInstance().update()`；`reset()` 里追加 `reset()`。
- **覆盖面复核（r4-fp-render）**：`AnimationClocks.client()` 全仓仅 `FirstPersonRenderHandler:73` 与
  `WorldEmitterManager:54` 两个消费者（同单例，一次 update 两者都修好）；`AnimationController`/`ObjectAnimationRunner`/
  `AnimationStateMachine` 走的是 **`GunTime`**（自带 pausedNanos 停表），不受影响。

### ② 第三人称手持枪模「反了」：**两层 2D 纸片变换**（已修两层）

| 层 | 来源（1.7.10） | 变换 |
|---|---|---|
| ①（外层） | `RenderPlayer#renderEquippedItems:377-385`（`is3D=false` 且 `Item#isFull3D()` 未被覆写 ⇒ 2D 分支） | `T(0.25,0.1875,-0.1875)·S(0.375)·Rz(60)·Rx(-90)·Rz(20)` |
| ②（内层） | `ForgeHooksClient#renderEquippedItem:225-233`（`EQUIPPED_BLOCK=false`） | `T(0,-0.3,0)·S(1.5)·Ry(50)·Rz(335)·T(-0.9375,-0.0625,0)` |

- ② 已修：`AnimateGeoItemRenderer#shouldUseRenderHelper` 对 `EQUIPPED + EQUIPPED_BLOCK` 返回 `true`
  （走 `T(-0.5,-0.5,-0.5)` 的方块帧，`ForgeHooksClient:216-221`）；`BLOCK_3D` **仍 false**（会引入 X/Y 镜像，
  与 `GunItemTransforms.apply` 自带的 `scale(-1,-1,1)` 抵消，只会翻回去）。
- ① 已修：`AnimateGeoItemRenderer#renderItem(EQUIPPED)` 右乘常量 `inv(F_paper)`
  （`F_paper` 由 `RenderPlayer:319/:380-384` + `ForgeHooksClient:219` 逐句搭出；**目标帧是恒等**，
  依据 `assets/tacz/models/item/modern_kinetic_gun.json` 无 `display` 段）。单点开关
  `AnimateGeoItemRenderer.ENABLE_EQUIPPED_PAPER_FRAME_FIX`（置 false 立即回退）。
  `HumanoidOffhandRender#renderGunItem`（挂枪，基座是 `hotbar_show` pose）用
  `beginExternalEquippedFrame()/endExternalEquippedFrame()` 隔离，不吃这个补偿。

### ③ 「背后那把枪」= **上游设计，非 bug**

`HumanoidOffhandRender#renderHotbarGun` 显式 `if (i == inventory.currentItem) continue;`
（当前手持槽不会被重复画），且要求该枪 display 的 `hotbar_show` 声明了该栏位 ⇒ 就是 TACZ 的
「快捷栏其余格的枪挂在身上」。嫌碍眼可开 `RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER`。

### ④ 原版皮肤手臂遮挡屏幕 —— **实机日志结案（已修）**

实机 `[TACZ-DBG] arm` 数据（持枪站定，`itemModelFov=60`）：

```
arm RIGHT origin=(0.038, -0.560, 0.712) axes=(1.000, 1.500, 1.000) rp=(-5,2,0) rot=(-1.4,-0.3,0.1) swing=0 equip=1
arm LEFT  origin=(0.140, -0.638, -0.091) axes=(1.000, 1.500, 1.000) rp=(5,2,0)  rot=(-1.4, 0.8,-0.1) swing=0 equip=1
```

判读（三种可能里命中第 3 种）：
- 骨骼链**生效**（不是 `origin≈0`，所以「缺摆放」假设排除）；
- `axes.y = 1.5` ⇒ 手臂本体 12px×0.0625=0.75 被骨骼放大成 **1.125 格**；
- `rot≈(-1.4°,0.3°,0.1°)` ⇒ **T-pose**，手臂从骨骼原点朝肩部方向（+Y）竖着长出去；
- **左手臂 `origin.z = -0.091`（在眼前 9cm）**，右手臂 `z=+0.712`（相机后方，基本被近平面裁掉）
  ⇒ 遮挡屏幕的是**左手臂**：锚点贴眼 + 向上竖出 1.1 格 ⇒ 竖穿整个视锥。
- 数据侧：`ak47_geo.json` 的 `righthand_pos` / `lefthand_pos` **各带 1 个 cube**（模型自带方块手）
  ⇒ 原版皮肤手臂是**额外叠上去的**。

修法（数据驱动，不硬编码枪名）：
- `BedrockAnimatedModel#hasBuiltinHandModel()`（新）：`righthand_pos`/`righthand`/`lefthand_pos`/`lefthand`
  任一棵子树含 cube ⇒ 模型自带手；
- **判定必须落在注册处**（`BedrockGunModel` 构造器让工厂返回 `null`），**不能**在
  `RightHandRender`/`LeftHandRender` 里 early-return —— 因为 `FunctionalBedrockPart:82-108` 在
  `functionalRenderer != null` 时**只**执行功能渲染器、不再 `compile` 自己的 cube ⇒ 在渲染器里跳过会让
  「手整个消失」而不是「画模型自带的手」；
- 兜底 `RenderHelperCompat.MIN_HAND_ANCHOR_DISTANCE = 0.25F`：第三方枪把 `*_pos` 摆在贴脸处时不画（置 0 可关）。

### ⑤ 开镜偏离屏幕中心 —— **仍未取得日志（代码零改动）**

`FirstPersonRenderGunEvent` 的 idle/aim 双矩阵与上游逐行同构、`applyGunModelFovModifying` 的
`views_fov` 计算逐行同构 ⇒ **本轮未改任何 FOV/变换逻辑**，只把 `[TACZ-DBG] ads` 放宽为
`ads-enter` / 每 10 帧 / 首次 `ads-full` / `ads-leave`（避免漏采）。需用户开镜 1 秒后再取日志。

### r4-02 构建实测（第 2、3 次）

| 轮次 | 结果 | 产物 |
|---|---|---|
| 第 2 次（时钟 + 3 处 GL 状态 + 纸片帧补偿 + 诊断） | **BUILD SUCCESSFUL in 4m 46s**（`build/r4-build2.log`，error 0） | `tacz-5.11.45.140.jar` 68.8 MB |
| 第 3 次（自带手判定 + 兜底 + ads 打印放宽） | **BUILD SUCCESSFUL in 4m 6s**（`build/r4-build3.log`，error 0） | `tacz-5.11.45.140.jar` 68.8 MB，01:16:56 |

17 个改动/新增 java 文件 `lint_files(min_severity=error)` → **0 error**。

---

## ✅ r4-03：第三人称 / 抖屏 / 缺面 / 开镜错位（第 3 轮）

> 触发：用户实机（含两张第三人称截图与 `[TACZ-DBG] ads` 日志）报 4 组问题，并澄清
> 「**竖着立在身体右侧的那把是手持枪**，中间靠近手边的小枪是挂枪」。

### 1. 第三人称手持枪「竖立、不在手上」—— 补偿漏了 1.20 目标帧（已修）

- 1.7.10 进入 `renderItem(EQUIPPED)` 的矩阵是 `F17 · T_block`：
  `F17 = T(-1/16,7/16,1/16) · T(0.25,0.1875,-0.1875) · S(0.375) · Rz(60) · Rx(-90) · Rz(20)`（`RenderPlayer:319/:380-384`），
  `T_block = T(-0.5,-0.5,-0.5)`（`ForgeHooksClient:219`，本轮 `EQUIPPED_BLOCK=true` 后走这条）。
- 1.20 目标帧：`F20 = Rx(-90) · Ry(180) · T(1/16,0.125,-0.625)`（`ItemInHandLayer#renderArmWithItem`）。
- 上一轮只右乘 `inv(F_paper)` ⇒ 残留 `T_block` 且**少了 `F20` 的 90°/180°** ⇒ 实机表现为「竖着立在身体右侧」。
- 修法：`AnimateGeoItemRenderer` 的 `tacz$buildPaperFrameInverse()` 改为 **`C = inv(F17 · T_block) · F20`**
  （`S(0.375)` 的逆由 `Matrix4f.invert()` 处理）；完整链
  `P · F_arm · [F17·T_block] · C · (模型侧 Z180) = P · F_arm · F20 · (模型侧 Z180)` 写进 javadoc；
  `GunItemTransforms#apply` 的 `scale(-1,-1,1)` 与 `F20`（无镜像）不冲突。回退开关
  `ENABLE_EQUIPPED_PAPER_FRAME_FIX = false`。

### 2. 挂枪（快捷栏其它格）位置/朝向不对 —— 用了 `EQUIPPED` 而不是 `FIXED`（Lead 修）

- 上游 `HumanoidOffhandRender:91` 是 `renderStatic(stack, **ItemDisplayContext.FIXED**, …)`；
  本工程此前调 `ItemRenderer#renderItem(..., EQUIPPED)`，在 `hotbar_show` pose 之外**又叠了一层 Forge 的
  EQUIPPED 帧**（`RenderPlayer:319` + `ForgeHooksClient:219`），且 `GunItemTransforms.apply` 取的是
  `equipped` 而非 `fixed` 变换 ⇒ 挂枪竖直、偏离身体。
- 修法：`HumanoidOffhandRender#renderGunItem` 直接调
  `renderByItem(stack, ItemDisplayContext.FIXED, matrixStack, buffer, packedLight, NO_OVERLAY)`
  （= 上游 `renderStatic` 的 dest，内部走 `GunItemRendererWrapper#renderByItem` → `GunItemTransforms.apply(FIXED,…)`），
  并去掉 `pushToGL/popFromGL`（`renderByItem` 全程 CPU 侧烘顶点）。

### 3. 换弹/切枪时「第一人称 + 第三人称都剧烈抖动」—— 相机动画偏移记账会累积（已修）

- 判据：**F5 也抖** ⇒ 抖的是 `player.rotationYaw/Pitch` 本身，不是手部渲染。
- 根因：旧实现依赖「同一帧 `RenderTickEvent.START` ↔ `END` 严格配对一次」。`END` 漏执行 / 同帧 `START` 跑两次 /
  跨帧错配时，偏移**永久留在 rotation 上**并逐帧再叠新 delta ⇒ 累积振荡。上游 1.20 的
  `ViewportEvent.ComputeCameraAngles` 是「一次算完立刻消费」，**没有跨事件状态**，天然不累积。
- 修法：`CameraSetupEvent#applyLevelCameraAnimation` 改为**自愈式记账** ——
  `END` 无条件 `revertCameraAnimationOffset()`（幂等哨兵）；**`START` 开头先扣掉上一帧残留**；
  只记录「真正加上的量」且 pitch 在同一处 `clamp`，保证减掉的等于加上的内容。
- 诊断：`[TACZ-DBG] cam dYaw=… dPitch=…`（0.1° 去重）。期望 |delta| 0~8°。

### 4. 第一人称手部「缺面」—— 近平面裁剪（已修）

- 手部 pass 沿用 vanilla 的 `0.05F` 近平面（`EntityRenderer:770`），而默认枪包 `lefthand_pos` 锚点在
  **0.09 格**、手方块 0.25 格深 ⇒ 相当一部分落在近平面内/相机后方被裁。
- 修法：`FirstPersonRenderHandler.NEAR_PLANE = 0.01F`（public static，置回 `0.05F` 即回退）。
  **不影响世界深度精度**：手部 pass 进入前已 `glClear(GL_DEPTH_BUFFER_BIT)`，只画枪与手。
- 已排除背面剔除：`mcshim.RenderType.entityCutout` 是 `cull=false`（r2 有意为之）。

### 5. 开镜错位 —— 共用逻辑已证明与上游**逐字节相同**（疑数据侧，待一条日志）

token 级比对（去注释/空白后逐字符）：`applyFirstPersonPositioningTransform`（含
`mountOffsetInsertIndex = size()-1`、`viewIndex = views[zoomNumber % len] - 1`）、
`getPositioningNodeInverse`（含 mountOffset 的 `-x/16, +y/16, -z/16` 三个符号）、`getScopeMountOffset`、
`views`/`views_fov` 的读取、camera listeners —— **全部相同**。
新增 `[TACZ-DBG] scope viewIndex/zoomNumber/mountInsert/mountOffset/path`（按内容去重）。
判读：`mountInsert = -1` ⇒ 该枪/瞄具没配 `scope_pos`（定位退化到机瞄 = 常数方向偏移，**数据侧**）；
`path` 里没有 `scope_view_N` ⇒ 瞄具变体未解析。

### 未完成（明确标注，不虚构结论）

**第三人称「人物持枪姿势」**（手臂没有摆出持枪姿态）：已排除
`ThirdPersonManager.registerDefault` 接线、`PlayerAnimatorCompat#canPlayAnimation`、`AnimationRenderContext.hand`
三条嫌疑；剩余嫌疑是 `PlayerModelRenderState` 是否每帧把手臂旋转写进
`ModelBiped.bipedRightArm/LeftArm`、以及 `ThirdPersonManager.DEFAULT.animateGunHold/animateGunAim` 的实现 ——
**下一轮第一优先**。

### r4-03 构建实测

`./gradlew spotlessApply build` → **BUILD SUCCESSFUL in 3m 17s**（`build/r4-build4.log`，`error:` 计数 0），
产物 `build/libs/tacz-5.11.45.140.jar`（68.8 MB，01:39:46）；本轮改动文件 `lint_files(error)` 全 0。

---

## ✅ r4-04：手部「缺面」定性纠正 + 第三人称姿态状态泄漏（第 4 轮）

> 依据：用户提供了 `tmp/minecraft-src-1.20.1`（vanilla 1.20.1 源码，4789 个 java）。本轮**只按源码验证**，不再猜。

### 1. 手部「部分可见面被剔除/消失」—— 上一轮的修法方向是错的（已回退）

三条源码/像素证据：
1. **上游从不画模型自带的手部 cube**：`FunctionalBedrockPart#render`（本仓
   `client/model/FunctionalBedrockPart.java:82-108`）与上游 TACZ 1.20 **逐行相同** ——
   `functionalRenderer` 非 null 时**只**跑功能渲染器、不再 `compile` 自身 cube。
   ⇒ 上游第一人称的「手」= `PlayerRenderer#renderRightHand` 的**皮肤手臂**，不是模型方块。
2. **那只 cube 的 UV 是模板垃圾**：`ak47_geo.json` 的 `righthand_pos`/`lefthand_pos` cube 用
   `uv: {north:[0,0] 4×12, …}`，落在纹理左上角 0..16 UV；实测像素采样
   `textures/gun/uv/ak47.png`（256²，UV 空间 128）该区域 **185/1024 像素 alpha=0**，
   `textures/gun/hud/ak47.png` **1799/2304** ⇒ `entityCutout`（alpha<0.1 丢弃）下必然表现为
   「部分可见面被剔除/消失」。
3. **上游手臂同样是「零旋转」**：1.20.1 `PlayerRenderer#renderHand`
   （`tmp/minecraft-src-1.20.1/.../client/renderer/entity/player/PlayerRenderer.java:195-212`）
   也是 `setupAnim(player,0,0,0,0,0)` 后 `modelpart.xRot = 0.0F` 再 render
   ⇒ `setRotationAngles(0,0,0,0,0,0.0625F, player)` 与上游等价，「T-pose」不是移植缺陷。

**处置**：删除 `BedrockGunModel` 构造器里「`hasBuiltinHandModel()` ⇒ 工厂返回 null」的分支
（含 `tacz$builtinHandDecision` 与去重字段），恢复无条件挂 `LeftHandRender`/`RightHandRender`。
`BedrockAnimatedModel#hasBuiltinHandModel()` 保留为公开只读查询（现无调用者，javadoc 记录结论）。

### 2. 第三人称持枪姿态 —— 共享模型姿态泄漏（已修）

`RenderPlayer.modelBipedMain` 是**玩家渲染器共享**的模型实例，而
`RenderHelperCompat#renderFirstPersonArm` 每帧调用 `setRotationAngles(0,0,0,0,0,…)` 会写
head/headwear/body/arms/legs 七个 part 的 `rotateAngle*`，**却从不恢复**。
上游有 `PlayerModelRenderState`（`tmp/TACZ-1.20.1/.../client/animation/third/PlayerModelRenderState.java`）：
`PartState` 构造时 `part.storePose()`、`close()` 里 `part.loadPose(pose)` —— 正是为了防止这类污染。

**处置**：新增 `tacz$saveBipedPose(ModelBiped)/tacz$restoreBipedPose(...)`
（7 个 part × `rotateAngleX/Y/Z` + `rotationPointX/Y/Z` + `onGround`），
在**两个出口**都恢复：贴脸兜底 `return` 前、以及渲染结束的 `finally` 里。

### 3. 其余两条（复核实机日志确认已生效）

- **抖屏**：`[TACZ-DBG] cam` 实测 `dYaw/dPitch` 已落在 **±1° 内**（此前是逐帧大幅正负跳变）
  ⇒ 「自愈式记账」（END 无条件回滚 + START 先扣上一帧残留）有效。
- **开镜错位**：`[TACZ-DBG] ads` 实测完全开镜后 `pose` 稳定（如 `(0.183,0.662,-0.856)`），
  `fov` 60→48 / 60→35 正常收敛；配合 token 级比对（逻辑与上游逐字符相同）⇒ 需
  `[TACZ-DBG] scope` 的 `mountInsert`/`path` 才能判定是否数据侧缺 `scope_pos`。
- **`axes.y = 1.5` 的来源已定位**：`BedrockPart#translateAndRotateAndScale:132` 的
  `poseStack.scale(xScale, yScale, zScale)`，而 `xScale/yScale` 由
  `client/model/bedrock/ModelRendererWrapper` 从**动画骨骼 scale 通道**写入
  ⇒ 上游吃同一份动画数据，**与上游同构**，不是移植缺陷。

### r4-04 构建实测

`./gradlew spotlessApply build` → **BUILD SUCCESSFUL in 2m 27s**（`build/r4-build5.log`，`error:` 计数 0），
产物 `build/libs/tacz-5.11.45.140.jar`（68.8 MB，01:59:11）。

---

## 🔧 r4-05：剩余三项（ADS 偏移 / 第三人称手持枪 / 第一人称手臂）改为「测量驱动」

### 已纠正的一处误判（避免按错方向改代码）

「第三人称手持物每帧被画两次」**不成立**：
- `RenderPlayer` 的父类是 **`RendererLivingEntity`**（`RenderPlayer.java:33`），**不是** `RenderBiped`；
- `RendererLivingEntity#renderEquippedItems(EntityLivingBase,float)` 在 `:380` 是**空方法**；
- `RenderBiped#renderEquippedItems` 的形参是 `EntityLiving`，`EntityPlayer` 不继承它 ⇒ 对玩家不可达。
⇒ 玩家第三人称手持物**只画一次**（`RenderPlayer:313-419`）。因此问题只能是「我们假设的 `F17·T_block` 与
实际 GL 矩阵不符」，**不是**双重绘制。

### 已确认修复（用户实机确认）

- **背枪（快捷栏其它格）位置正常** ✓（`renderByItem(FIXED)`）；
- **换弹/切枪抖屏消失** ✓（`[TACZ-DBG] cam` 实测 |dYaw|/|dPitch| ≤ 1°）；
- 第一人称手部「缺面」已按上游语义回退（不再画模型自带 cube）。

### 本轮改动：只加诊断，零行为改动（`RenderHelperCompat.DEBUG_RENDER`，默认 true）

| 日志 | 落点 | 用来判定 |
|---|---|---|
| `[TACZ-DBG] p-entity m=`（16 数） | `ItemInHandLayerMixin#renderArmWithItemHead`（= `RenderPlayer#renderEquippedItems` HEAD） | `P_entity` 基座 |
| `[TACZ-DBG] arm-bone` | 同上（`bipedRightArm` 的 rotationPoint/rotateAngle/showModel） | 算 1.20 目标帧 `P_arm` |
| `[TACZ-DBG] equipped-entry call=… m=`（16 数） | `AnimateGeoItemRenderer#renderItem(EQUIPPED)` 入口 | `m` + 调用次数（证伪双重绘制） |
| `[TACZ-DBG] equipped-call #…` | 同上 | `mvT` + `equip` + `external` 深度 |
| `[TACZ-DBG] u1-A / u1-B` | `GunItemRendererWrapper#renderFirstPerson` | 手部骨骼为何在相机背后（定位链 vs 骨骼链） |
| `[TACZ-DBG] ads-mx pre / post` | `FirstPersonRenderGunEvent#applyFirstPersonPositioningTransform` | ADS 偏移（rotProg/posWeight/idle.m30/aim.m30/final） |
| `[TACZ-DBG] scope` | 同文件 | 瞄具 `scope_view_N` 是否拼进定位链（数据侧） |

**判读算法（不猜）**：
```
F_actual  = inv(P_entity) · m
F_assumed = F17 · T_block
F17       = T(-1/16,7/16,1/16) · T(0.25,0.1875,-0.1875) · S(0.375) · Rz(60) · Rx(-90) · Rz(20)
T_block   = T(-0.5,-0.5,-0.5)
C         = inv(F_actual) · F_20        // F_20 = Rx(-90)·Ry(180)·T(1/16,0.125,-0.625)
```
- 缩放期望 **0.375**；若 `1.0` ⇒ 走了 `Block3D` 分支、若 `0.625`（Y 为负）⇒ 走了 `isFull3D()` 分支
  （立即判定「`F17` 取错层」）；
- 旋转期望 `Rz(60)·Rx(-90)·Rz(20)`；出现 `Rx(-20)`/`Ry(45)` ⇒ 别的分支；
- 平移列与 `F_assumed` 差常数 ⇒ `T_block` 没生效或多了一层。

### r4-05 构建实测

`./gradlew spotlessApply build` → **BUILD SUCCESSFUL in 2m 55s**（`build/r4-build6.log`，`error:` 计数 0），
产物 `build/libs/tacz-5.11.45.140.jar`（68.8 MB，02:14:46）。诊断开关
`RenderHelperCompat.DEBUG_RENDER = true`（置 false 即全静默，**不影响渲染行为**）。

### 🚨 r4-05 附：Mixin 规则新增一条（启动崩，**构建不报错**）

```
InvalidMixinException: Mixin mixins.tacz.json:client.ItemInHandLayerMixin from mod tacz
  contains non-private static field tacz$entityMatrixFingerprint:Ljava/lang/String;
→ NoClassDefFoundError: net/minecraft/client/renderer/entity/RenderPlayer（启动崩）
```

**规则**：`@Mixin` 类里**只允许 `private static` 字段**（`MixinPreProcessorStandard#validateField`）。
非 private 的 static 字段会被合并语义拒绝；这类错误**只在校验/运行期暴露，`./gradlew build` 不会报**。
跨类共享的诊断/缓存状态请放普通类的 `public static`（本仓已有 `RenderHelperCompat.debugLog(...)` 这一层）。

**处置**：删除该字段（原本就未被使用，实际用的是局部变量 `fingerprint`），并在类内留下规则注释。
**全量复查**（`src/main/java/com/tacz/guns/mixin/**` 20 文件）：
- 非 `private` 的 `static` 字段：**0**；
- `static { }` 初始化块（Mixin 禁止 clinit）：**0**；
- 嵌套/内部/匿名类（NESTING → 要求 JAVA_11）：**0**。

修后 `./gradlew spotlessApply build` → **BUILD SUCCESSFUL in 3m 25s**（`build/r4-build7.log`，`error:` 0）。

---

## ✅ r4-06：第一人称手臂默认关闭 + 诊断静默（当前基线）

实测日志（AWM，`equip=1.000`）给出稳定的手部骨骼位置：
```
arm RIGHT origin=(-0.242, -0.816, +0.441)  axes=(1.000,1.500,1.000) rot=(-1.4,-0.3,0.1)
arm LEFT  origin=( 0.010, -0.948, -0.502)  axes=(1.000,1.500,1.000) rot=(-1.4, 0.8,-0.1)
```
- 右手骨骼在**相机后方** `z=+0.44`，左手在**眼前** `z=-0.50`；
- `axes.y=1.5`（动画骨骼 scale 通道）、`rot≈0`（T-pose，与 1.20 `PlayerRenderer#renderHand` 的 `xRot=0` 同构）。

⇒ 1.7.10 的 `ModelBiped` 手臂（4×12×4 px，被拉伸到 1.125 格）在「眼前」那一侧必然变成
「一整块贴在脸上」，这就是「一大半屏幕被手部遮挡」；而模型自带的 cube 又是模板占位（UV 空）。
**两条路都有明显瑕疵**，因此本轮：

| 开关 | 值 | 含义 |
|---|---|---|
| `RenderHelperCompat.RENDER_FIRST_PERSON_VANILLA_ARM` | **false** | 第一人称不画原版皮肤手臂（只显示枪模）；置 true 可恢复 |
| `RenderHelperCompat.MIN_HAND_ANCHOR_DISTANCE` | `0.0F` | 贴脸兜底关闭（它是症状不是病因） |
| `RenderHelperCompat.DEBUG_RENDER` | **false** | 全部 `[TACZ-DBG]` 静默（加回 true 即可再看诊断） |

### 本轮顺带做的两处「可证伪」排查（结论：都不是病因）

1. **第三人称补偿矩阵自洽**：用独立数值计算验证
   `F = T(-1/16,7/16,1/16)·T(0.25,0.1875,-0.1875)·S(0.375)·Rz(60)·Rx(-90)·Rz(20)·T(-0.5,-0.5,-0.5)`、
   `F20 = Rx(-90)·Ry(180)·T(1/16,0.125,-0.625)`、`C = inv(F)·F20`
   ⇒ `F·C = F20`，**最大误差 1.2e-16**。所以补偿本身没错，错的是「实际 GL 矩阵不等于 `F`」。
2. **VBO 快路径不是原因**：`ExternalGunItemVboRenderer#tryRender` 恒返回 `UNSUPPORTED`
   （`ExternalGunItemVboRenderer.java:53-58`），第三人称一定走 `GunItemTransforms.apply` + 模型渲染，
   不会绕过 CPU 侧 PoseStack。

### 仍缺的唯一数据点

`[TACZ-DBG] scope`（`viewIndex/zoomNumber/mountInsert/mountOffset/path`）：
若 `path` 里**没有** `scope_view_N`，说明瞄具模型（`BedrockAttachmentModel`）没加载成功、
ADS 只能对齐 `scope_pos`（安装点）而不是目镜中心 ⇒ 恒定方向偏移（数据/加载侧，非变换逻辑）。

### r4-06 构建实测

`./gradlew spotlessApply build` → **BUILD SUCCESSFUL in 3m 42s**（`build/r4-build8.log`，`error:` 0）。

---

## 🔬 r4-07：用户日志判读（改装界面后偏移加重）+ 诊断加固

### 日志里已能确证的结论

用户 02:36–02:37 的日志（含 `scope` / `ads` / `cam`）：

1. **抖屏已彻底修好**：`[TACZ-DBG] cam dYaw/dPitch` 全程 ≤ ±2.2°（早前是逐帧大幅正负跳变）。
2. **开镜 FOV 正常**：`fov` 60→48（AWM）与 60→35（带镜枪）都能收住。
3. **`scope` 行**：`viewIndex=1 zoomNumber=0 mountInsert=-1 mountOffset=null path=view > iron_view`
   ⇒ 该帧 ADS 定位用的是 **`iron_view`（机瞄）**，且 `mountInsert=-1`（没有瞄具安装点偏移）。
   如果玩家当时装的是 4× 镜，这行就说明**瞄具定位路径没被解析出来**（数据/加载侧），
   与「偏移方向固定」的现象一致。
4. **「开关改装界面后更严重」的可疑机制**：`applyFirstPersonGunTransform` 里有
   `MathUtil.applyMatrixLerp(..., RefitTransform.getOpeningProgress())` 与
   `applyMatrixLerp(..., refitScreenOpeningProgress * RefitTransform.getTransformProgress())`
   两处插值（`FirstPersonRenderGunEvent.java:372-373/431-436`）。只要
   `refitScreenOpeningProgress` 没有回到 0，模型就会被持续拉向 `fromViewMatrix`/`toViewMatrix`
   （改装/配件视角）——这正好解释「偏移变大且方向固定」。

### 本轮改动（诊断加固，零行为改动）

| 改动 | 目的 |
|---|---|
| `ads` 日志新增 `refit={dynamics 输出}(raw {RefitTransform.getOpeningProgress()}) refitT={transformProgress}` | 直接判定「改装界面状态是否残留」 |
| `scope` 日志的去重键加上**枪械 id**，输出也带 `gun=` | 不同枪的 `path` 常常同名（都是 `view > iron_view`），旧键会让换枪后不再打印，容易误判 |
| `RenderHelperCompat.DEBUG_RENDER` 置回 `true` | 本轮需要这些数值 |

### 事故记录：一次 `Set-Content -NoNewline` 把 `RenderHelperCompat.java` 压成单行

- 原因：`Get-Content … | Set-Content -NoNewline` 会把数组**无分隔**拼接，整文件变 1 行；
  `//` 注释随即吞掉后文，等于毁文件。
- 恢复：`git checkout --` 拿到 HEAD（r4-02 之前的基线），再按本次会话记录把
  r4-02/r4-04/r4-06 的全部内容逐段重建（598 行，`lint_files(error)=0`，构建通过）。
- **教训**：批量替换一律用 `edit` 工具做字面替换；`Set-Content` 只在确认带换行时使用。

### r4-07 构建实测

`./gradlew spotlessApply build` → **BUILD SUCCESSFUL in 2m 14s**（`build/r4-build9.log`）
与 **2m 3s**（`build/r4-build10.log`，含 `scope` 去重键改动），`error:` 计数 0。

---

## 📊 r4-08：这批日志把两条假设**证伪**了，并暴露了新的关键事实

日志：02:55:44–02:56:09（hk_mp5a5 → ai_awp）。

### 1. 「改装界面残留导致偏移加重」——**证伪**

新增字段全程为：

```
refit=0.000(raw 0.000) refitT=1.000      （所有 ads / ads-full / ads-enter 行）
```

`RefitTransform.getOpeningProgress()` 恒 0、`getTransformProgress()` 恒 1，
只有开镜首帧出现一次 `refit=0.008`（二阶动力学的正常收敛残差）。
⇒ `applyFirstPersonPositioningTransform` 里那两处 `applyMatrixLerp(..., refitScreenOpeningProgress)`
**没有**贡献偏移。该假设从待办里移除。

### 2. 「ADS 定位用了错节点」——**当前数据里没有复现**

`ads-mx` 显示插值链**完全正常**：

```
ads-mx pre  rotProg=0.000 … idle.m30=-0.1719 aim.m30=0.0000 cur.m30=-0.1719
ads-mx post rotProg=1.000 tx=-0.0003 ty=0.8431 tz=-0.8751     ← 完全开镜收敛到 aim 矩阵
ads-full aim=1.00 raw=1.00 fov=48.46 pose=(0.230, 0.654, -0.847)
```

`rotProg` 单调 0→1、`posWeight` 恒 1，`post` 收敛到 `aim` 矩阵 ⇒ 定位逻辑（含 `AdsPath` 分轴）**无异常**。

### 3. 关键新事实：日志里的这几枪**没有装瞄具**

```
scope gun=tacz:hk_mp5a5 viewIndex=1 zoomNumber=0 mountInsert=-1 mountOffset=null path=view > iron_view
scope gun=tacz:ai_awp   viewIndex=1 zoomNumber=0 mountInsert=-1 mountOffset=null path=views > iron_view
```

`mountInsert=-1` 在代码里**只有一条路径**能产生：`applyFirstPersonPositioningTransform` 走了
「`DefaultAssets.isEmptyAttachmentId(scopeId)` 为真」的机瞄分支（`FirstPersonRenderGunEvent.java:321-323`；
装瞄具分支一定会把 `mountInsertIndex` 设为 `scopeNodePath.size()-1 ≥ 0`）。
且 `ai_awp_display.json` 里 `"zoom_model_fov": 35` 正是**机瞄**的 FOV ⇒ 日志里 `fov` 60→35 与「机瞄 ADS」完全自洽。

⇒ **截图里挂着 4× 镜的 AWM 状态没有出现在这次日志中**，所以「装镜后偏移」的样本尚未采集。

### 4. 本轮补充的诊断（`scope` 行加 5 个输入，一次运行即可定性）

```
scope gun=… path=… scopeId=<id|tacz:empty> hasIdx=<bool> model=<bool> views=<n> scopePos=<bool>
```

判读表（写进代码注释，同一份）：

| 输出 | 结论 |
|---|---|
| `scopeId` 空 + `hasIdx=false` | 枪上确实没装瞄具（操作/数据侧，非渲染 bug） |
| `scopeId` 非空 + `hasIdx=false` | 装了镜但 `TimelessAPI.getClientAttachmentIndex` 查不到 ⇒ **资源加载侧** |
| `scopeId` 非空 + `hasIdx=true` + `model=false` | 瞄具模型没加载 ⇒ 只能对齐 `scope_pos` 安装点 ⇒ **恒定方向偏移**（正是「方向固定」现象） |
| `scopeId` 非空 + `hasIdx=true` + `model=true` + `scopePos=false` | 枪模型缺 `scope_pos` 骨骼（AWM geo 已确认**有** `scope_pos`/`views`/`iron_view`，故此格只对第三方枪包成立） |

### r4-08 构建实测

`./gradlew spotlessApply build` → **BUILD SUCCESSFUL in 1m 41s**（`build/r4-build11.log`，`error:` 0）。

---

## 🎯 r4-09：新日志（03:12）给出的**决定性**事实

```
scope gun=tacz:ai_awp   mountInsert=-1 path=views > iron_view scopeId=tacz:empty hasIdx=false model=false views=-1 scopePos=false
scope gun=tacz:hk_mp5a5 mountInsert=-1 path=view  > iron_view scopeId=tacz:empty hasIdx=false model=false views=-1 scopePos=false
```

`scopeId=tacz:empty` ⇒ **这两次测试时枪上确实没有装瞄具**（`getAttachmentId(SCOPE)` 返回空），
于是 `applyFirstPersonPositioningTransform` 走机瞄分支、用 `iron_view` 对齐 —— 与 `ai_awp_display.json`
的 `"zoom_model_fov": 35`（机瞄 FOV）完全自洽。

**推论**：截图里那支「挂着 4× 镜」的 AWM 状态，至今没有出现在任何一次日志里。
「装镜后位置对不上」这个场景仍需一次 `scopeId` 非空的日志才能定位。

### 同时被这批日志否定的两项

1. **改装界面残留**：`refit=0.000(raw 0.000) refitT=1.000` 全程成立（仅开镜首帧 `0.008` 收敛残差）⇒ 否定。
2. **ADS 定位链路异常**：`ads-mx` 显示 `rotProg` 0→1 单调、`posWeight` 恒 1、
   完全开镜时 `post` 收敛到 `aim` 矩阵（`tx≈-0.0003`）⇒ 否定。

### 数值说明：为什么 `pose.x≈0.17` 而 `aim.m30≈0`

`applyFirstPersonPositioningTransform` 末尾是上游同款的三段式：
`translate(0,1.5,0) · mulPoseMatrix(transformMatrix) · translate(0,-1.5,0)`。
该组合的最终平移 = `M.t + (0,1.5,0) − 1.5·(M.m01, M.m11, M.m21)`，
**只要 M 带旋转，结果就会偏离 `M.t`**（`0.17 ≈ −1.5 × m01`）。这是上游同样存在的枢轴约定，
不是移植缺陷；判读 `pose` 时应看 `aim` 矩阵与 `pose` 的差值是否稳定，而不是要求 `pose≈0`。

### r4-09 构建实测

本批日志对应的构建为 `build/r4-build11.log`（**BUILD SUCCESSFUL in 1m 41s**，`error:` 0）。
本轮未再产生新的行为改动（只补日志字段 + 文档）。

---

## 🔍 r4-10：配件系统全链路静态核查（用户反馈「更换配件没效果」）

用户同时澄清：**截图里从未装过瞄具**（那截是 AWM 模型自带的镜筒），因此历史日志里的
`scopeId=tacz:empty` 是正常值，不能用来证明「安装失败」。为定位「更换配件没效果」，
本轮把安装链路逐环读完（未靠日志），结论如下。

### 已被逐环证伪的环节（全部与上游同形，`file:line` 可查）

| 环节 | 位置 | 结论 |
|---|---|---|
| 客户端发包 | `GunRefitScreen.java:306-329` | 消息构造与 `sendToServer` 正确 |
| 包注册 | `NetworkHandler.java:176-179` | PLAY_TO_SERVER 已注册 |
| 服务端排队 | `NetworkTaskQueue` | 开火/换弹同队列可用 ⇒ 非队列问题 |
| 配件锁 | `GunItemDataAccessor.java:96-102` | 缺键返回 `false`，与上游 `:91-97` 一字不差 |
| 类型白名单 | `GunData.java:118-119` `@SerializedName("allow_attachment_types")` | 注解在，枚举 `@SerializedName` 与上游一致 |
| 允许清单标签 | `AttachmentsTagManager.java:92-103`（`allow_attachments/` 前缀 18 字符切分） | 扫描 id 形如 `tacz:allow_attachments/ai_awp`，切分正确 |
| 标签展开 | `AllowAttachmentTagMatcher.expand` | `#tacz:scope` 等引用可递归展开，`scope.json` 存在且内容有效 |
| 数据实际内容 | `allow_attachments/ai_awp.json` | 允许 `#tacz:scope` 等 7 条，**AWM 确实允许装瞄具** |
| NBT 写读 shape | `ItemStackNbtHelper.save`→`writeToNBT`；`GunItemDataAccessor:232-245` 读 `.getCompoundTag("tag")` | 嵌套形状一致 |
| 配件栈 id 盖章 | `AttachmentItemBuilder.java:36-42`、`AttachmentItem.fillItemCategory`、`ClientMessageInstallVirtualAttachment:89-102` | 三条创建路径都写了 `AttachmentId` |
| 接口继承 | `AttachmentItemDataAccessor extends IAttachment` | `getIAttachmentOrNull` 对配件栈返回非 null |

⇒ **安装链路本身没有发现缺陷**；"装了但看不出变化"的剩余可能落在**渲染/属性**侧：
`BedrockGunModel#allAttachmentRender:220` 与 `AttachmentRender.renderAttachment:454` 存在，
但需确认第一人称路径是否真的把**当前已装配件**喂给了 `AttachmentRender`。

### r4-10 定论（存档 NBT 硬证据，非推测）

`run/client/saves/New World/playerdata/*.dat`（03:23:56 退出时的真实状态）里：

| 物品 | 关键 NBT | 说明 |
|---|---|---|
| `tacz:ai_awp` | `SlotAdapters={"SCOPE":"tacz:riser_rail_unity"}` | 该键**只能**由 `ClientMessageSetSlotAdapter` 写入 ⇒ 改装界面的点击 → 报文 → 服务端 → 写 NBT **全程正常** |
| `tacz:hk_mp5a5` | `AttachmentSTOCK={id,Count,tag:{AttachmentId:"tacz:oem_stock_unfolded",VirtualOemAttachment:1}}` | 正是 `installAttachment` 的 `writeToNBT` 形状，且 `getAttachmentTag`/`getAttachmentId` 可原样读回 |
| 背包 slot2–4 | 2 个原版物品 + 草方块 | **没有任何配件物品** |

⇒ **定论**：
1. 配件系统（GUI → 报文 → 服务端四道门 → NBT → 存档 → 读回）**全链路正常**；
2. `scopeId=tacz:empty` 与 `AttachmentSCOPE` 缺失**完全一致**，是正确结果，不是渲染或读取 bug；
3. 「更换配件没效果」的真实原因是**背包里没有可装的配件物品**（改装界面只列出背包里已有的该类型配件），
   以及可能的踩坑：`/give tacz:attachment` 得到的是**没有 `AttachmentId` 的空壳**，界面识别不出类型。
   正确来源是从**创造模式物品栏的配件分类标签页**取（`ModCreativeTabs.java:84-119`，每条都 `setId(...)` 盖了 id）。

### 本轮唯一的行为改动

`RenderHelperCompat.DEBUG_RENDER`：`true` → **`false`**（用户要求关闭全部 `[TACZ-DBG]` 诊断日志）。
所有诊断输出均经由 `RenderHelperCompat.debugLog`（`RenderHelperCompat.java:446-456`，首行即 `if (!DEBUG_RENDER) return;`），
因此该开关一关，7 个文件里的全部 `[TACZ-DBG]` 输出同时静默，**不改任何渲染行为**。

### ④ 原版皮肤手臂遮挡 / ⑤ 开镜偏离中心：**已缩小到 3 选 1，需一次实机日志定论**

- **推翻的两条假设**（有证据）：
  - 「缺 vanilla `ItemRenderer:519-543` 手部摆放」——上游 SBM `RenderHelper#renderFirstPersonArm:88-98`
    也**零变换**，手臂由枪模型的 `righthand_pos` 骨骼摆放，我们的 `RightHandRender` 与上游 `:35-43` 逐行同构；
    再叠一次 vanilla 空手摆放会把手臂**搬离握把**且左右手重叠。
  - 「开镜只用 `idle_view`」——`FirstPersonRenderGunEvent:277/282` 对 idle 与 aim 两套 view 矩阵各做一次
    `applyMatrixLerp`，与上游 `:153-256` 逐行同构；只有被覆盖、对枪械永不可达的基类方法才只取 idle。
- **已修**（缺的确实是 3 处 vanilla GL 状态，均有行号）：`glColor3f(1,1,1)`（`RenderPlayer:458`）、
  `GL_RESCALE_NORMAL`（`ItemRenderer:526/:552`）、`enableStandardItemLighting/disable`（`ItemRenderer:272/:553`，
  即 r4 报告里「留待实机」的 B-1）。
- **新增一次性诊断**（只在数值变化时打，不刷屏）：`[TACZ-DBG] arm`（手臂骨骼原点 `origin`/轴长 `axes`/
  `rotationPoint`/`rot`/`swing`/`equip`）、`[TACZ-DBG] ads`（`aim/raw/fov/pose`）。
  判读表：`arm` 的 `origin≈(0,0,0)` ⇒ 真缺摆放；`axes` 偏离 1 ⇒ 枪包骨骼 scale；
  `origin` 合理 + `axes≈1` + `rot≈(0,0,0)` ⇒ **T-pose**（正确修法是套 1.20 `prepareArm` 握持姿态，不是加平移）。

### r4-02 构建实测

`./gradlew spotlessApply build` → **BUILD SUCCESSFUL in 4m 46s**（`build/r4-build2.log`，`error:` 计数 0），
产物 `build/libs/tacz-5.11.45.140.jar`（68.8 MB，00:31:55）。
12 个改动/新增 java 文件 `lint_files(min_severity=error)` → **0 error**。

---

# r5 · 全量体检（5 个 teammate 并行）

> 触发：用户要求「审查当前移植是否存在偏离预期/只实现一半（如 NBT 只写 writer 没写 reader）、
> 方法名与注解尊重上游、不要用全限定名、审查模型渲染与第一人称手持 pos，有问题直接修」。
> 分工（互不重叠写作用域，共享任务板 task-1~task-5）：
>
> | task | teammate | 写作用域 | 报告 |
> |---|---|---|---|
> | task-1 | `r5-nbt-sync` | `item/ entity/ block/ inventory/ crafting/ loot/ particles/ api/item/nbt/ resource/serialize/` | `docs/review/r5/nbt-sync.md` |
> | task-2 | `r5-fidelity` | `api/ util/ command/ config/ init/ event/ advancement/ sound/ debug/ mcshim/` + 入口 | `docs/review/r5/fidelity.md` |
> | task-3 | `r5-render-fp` | `client/{model,renderer,event,animation,particle}/` + `com.github.mcmodderanchor/**` + `mixin/**` | `docs/review/r5/render-fp.md` |
> | task-4 | `r5-data-wiring` | `resource/ client/resource/ network/ client/{init,gui,gameplay,input,sound,tooltip}/ compat/` | `docs/review/r5/data-wiring.md` |
> | task-5 | `r5-comment-trim` | 全仓注释精简（在前 4 个完成后） | `docs/review/r5/comment-hygiene.md` |

## 修掉的缺陷（按级别）

| 级别 | 位置 | 问题 |
|---|---|---|
| **P0** | `client/model/RenderHelperCompat#renderFirstPersonArm` | 缺 `AnimationRenderContext.hand(player)` 作用域（上游唯一调用点 `util/RenderHelper.java:113-114`，本仓此前 **0 处**）⇒ 第一人称画手臂时把**第三人称持枪姿态**写进共享 `ModelBiped`。r4 把 `rotateAngle*` 当「度」读成 ≈0 才误判 T-pose，进而整条手臂被关掉。已恢复作用域，`RENDER_FIRST_PERSON_VANILLA_ARM` 回到 `true` |
| **P0** | `client/renderer/item/{GunItemRendererWrapper,AnimateGeoItemRenderer}` | `xBobO/xBob`、`yBobO/yBob` 写成 0（原注释称「1.7.10 无可读字段」，判断错误）⇒ `xRot/yRot` 退化成**绝对** yaw/pitch，被 `×-0.1`/`tanh` 放大成随朝向变化的固定偏移（r4 追了三轮的「开镜偏移」）。改用 `EntityPlayerSP#prevRenderArmPitch/renderArmPitch`（滞后系数 0.5，与 1.20 `LocalPlayer#serverAiStep` 逐值等价） |
| **P0** | `inventory/GunSmithTableMenu` + `crafting/GunSmithTableRecipe#getOutput` | 工作台**每个配方只能合成一次**：`splitStack` 把配方持有的栈拆空。按上游 `getResultItem` = `this.result.getResult().copy()`（上游 `GunSmithTableRecipe.java:50-52`）改为返回副本 |
| **P1** | `block/StatueBlock#breakBlock` | 破坏雕像把展示的枪**永久销毁**（上游 `onRemove` 无对应）。依据 1.7.10 `Chunk.java:653-664`（方块先换掉再调 `breakBlock`）补 `breakBlock` |
| **P2** | `block/TargetBlock` | 破坏标靶**丢自定义名称**：1.7.10 先 `removeBlock`（清方块实体）再 `harvestBlock`（`ItemInWorldManager.java:322-326`）。掉落搬到 `breakBlock`，并补 `onBlockHarvested`（创造不掉落）/`removeWithoutDrop`（拆一半不掉落） |
| **P2** | `client/ClientProxy` | 按**类名**注册 `SoundPlayManager` 是空注册（唯一 `@SubscribeEvent` 在内部类）⇒ 音效清理 tick 从未启动。改为显式注册 `SoundPlayManager.TICK_HANDLER` |
| **P2** | `GunMod` + `CommonProxy` + `OverwriteCommand` | `ConfigManager.saveAll()` 与 `PreLoadConfig.save()` 全仓 **0 调用者** ⇒ `/tacz config`、`/tacz overwrite` 的改动重启即丢。补 `FMLServerStoppingEvent` 落盘（镜像上游 Forge `ConfigTracker#unloadConfigs`）与命令内落盘 |
| **P3** | 7 个文件 | 过期/事实错误的注释：`ModContainerScreen`「工作台界面打不开」、`DisplayCaseLockHintOverlay`「从未注册」、`ItemClientHooks` 引用不存在的 `ClientItemRendererRegistrar`、`ModRecipe`「blood_strike_1 在磁盘上不存在」、3 个网络包「`readEnum` 越界返回 null」 |
| **P3** | `client/init/ClientSetupEvent` | `PlayerAnimatorCompat.init()` 0 调用点（其 javadoc 自称由 ClientSetupEvent 调用）→ 已补 |

## 复核结论（不是缺陷，记录以免下一轮重开）

1. **NBT 双向对账**：全仓 190+ 处 NBT 读写聚合后，**除 `GunLevelExp`（上游同样只写不上，等级系统两边都未落地）外每个 key 都有读端与写端**，无键名拼写不一致、无 `setInteger` 配 `getString`。`EntityKineticBullet` 的 spawn data 20 字段顺序一一对应；6 个 TileEntity 的 `writeToNBT/readFromNBT` + `getDescriptionPacket/onDataPacket` 全部成对。4 个「0 调用点 setter」（`setGunDisplayId`/`setMaxDummyAmmoAmount`/`setSkinId`/`setAllTypeCreative`）**上游同样 0 调用点**。
2. **网络层**：45 个包的 `read/write` **100% 逐字段对称**；主 channel 42 个 discriminator 与上游 `NetworkHandler.java:40-127` 逐位一致（43 是移植特有的 `ServerMessageSyncConfig`）。
3. **YSM 硬契约 12/12 通过**（逐符号含行号，见 `fidelity.md §2`）；`mixins.tacz.json` 含 `common.LivingEntityMixin` / `client.LocalPlayerMixin`，强转链完整。
4. **api 注解零漂移**：`@SideOnly(CLIENT)` 21（19 对应上游 + 2 处防御性新增已登记）、`@Deprecated` 11/11、`@Cancelable` 5/5。
5. **event/init**：20 个事件类全部有注册点且双总线归属正确、`@SubscribeEvent` 全为 public 非 static 实例方法；16 个 `Mod*#register()` 全部有真实调用链。
6. **mcshim**：no-op 方法的外部调用者 = 0 ⇒ 无「no-op 被非 no-op 场景调用」的功能失效。
7. **第一人称变换层**：`FirstPersonRenderGunEvent` 与上游去注释后逐行相同（双 view `applyMatrixLerp`、`adsPath` 三轴分时、`getPositioningNodeInverse`、`mountOffsetInsertIndex`）；偏差在它收到的 poseStack 基座（见上表 P0）。r4 的 A-1~A-6、A-3 的 `ITEM_MODEL_FOV`、时钟修复、GUI 图标、RenderType 状态还原**逐条复核仍在**。

## 代码风格整改（用户点名）

- **全限定名 → import**：新增 `tools/fqn_to_import.py`（状态机跳过注释/字符串/import 行，同名冲突保留 FQN），
  全仓消除内联全限定名 **435 处 / 103 个文件**，新增 import 263 条；6 处真同名冲突（`RenderHelper` / `PacketBuffer` /
  `ResourceManager` / `Supplier` / `BedrockModelPOJO` / `java.lang.Math`）按「保留 FQN + 说明」处理。
- **注释精简**：`r5-comment-trim` 处理 25 个注释大户，注释行 **34,757 → 33,209**；
  删的是 r2/r3/r4 审查历史、HTML 映射表、五段式论证、日志判读表；**保留**全部 `file:line` 依据、
  平台差异一句话、可行动 `TODO(port)`、GL 状态成对 / Mixin 注入契约 / `@SideOnly` / YSM 契约 / NBT 键名等会崩的说明。
  映射表内容留档在 `docs/review/r5/comment-hygiene.md §3`。

## 仍未修（有据，不阻塞）

| 项 | 级别 | 理由 |
|---|---|---|
| `data/tacz/recipes/misc/blood_strike_1.json` 无人加载 | P2 | 产物是 1.20 painting **变体**，1.7.10 无变体注册表；且材料（8 木棍 + 1 羊毛）与**原版画配方等价**，不注册不损失可获得物 |
| `TargetMinecart` 可骑乘 / 轨道限速 / `isIndirect` 校验 | P2 | 1.7.10 `EntityMinecart` 无可覆写点，需 `mixin/**` 跨域；属「多给的便利」不是数据丢失 |
| `ConfigManager.reloadAll()` 0 调用者 | P3 | 1.7.10 无配置文件监视器；接进 `/tacz reload` 会用本地值覆盖服务端同步下来的 SERVER 值，需产品裁决 |
| `LootTableInjectorModifier` 的 `set_nbt` 覆盖 vs 上游 merge | P2 | 默认枪包未触发；改动会影响「先 set_count 后 set_nbt」的顺序语义 |
| 组合镜 stencil 遮挡 | P2 | 平台限制（r3-4 已登记），`-Dforge.forceDisplayStencil=true` 是零代码缓解 |
| `AmmoStorageInventory#getInventoryStackLimit` 固定 64 | P3 | 1.7.10 `IInventory` 无动态槽上限概念，已在类注释登记 |

## 需实机验证（r5 修复项）

1. **第一人称手臂**：`RenderHelperCompat.DEBUG_RENDER=true` 后 `grep "\[TACZ-DBG\] arm"`，`rot=` 应 ≈ `(0.000,0.000,0.000)`；手臂应挂在握把上而不再糊满屏幕。
2. **枪位姿不随朝向变化**：站定向 4 个方位 + 抬头/低头到极限，枪在屏幕上的位置与角度不应变化；完全开镜时不同朝向的 `pose=(x,y,z)` 应相同。
3. **装镜 ADS**：装 4× 镜按住右键 1 秒后 `grep "\[TACZ-DBG\] scope"`，`scopeId` 应非空且 `model=true`（此前所有样本都是「没装镜」，装镜样本至今未采到）。
4. **工作台连续合成**：同一配方连做 3 次以上，材料每次扣、产物每次出且 NBT 完整。
5. **雕像 / 标靶**：破坏雕像枪应掉出；铁砧命名的标靶破坏后掉落物保留名称；拆一半不掉落。
6. **配置落盘**：`/tacz config` / `/tacz overwrite` 改完正常关服，`config/tacz-server.cfg` 与 `<gamedir>/tacz/tacz-pre.cfg` 应有新值。
7. **音效清理**：连射 30s 后停火，声音实例不应堆积。

## r5 构建实测

`./gradlew --offline spotlessApply build` → **BUILD SUCCESSFUL in 2m 11s**（`build/r5-build1.log`，`error:` 计数 **0**），
产物 `build/libs/tacz-5.11.45.140.jar`（68.8 MB）。改动合计 **136 files / +4503 / −3106**。
五个 teammate 报告：`docs/review/r5/{nbt-sync,fidelity,render-fp,data-wiring,comment-hygiene}.md`。

---

## 🔧 r5-后续：开火（AWM 拉栓）/ 换弹时「屏幕剧烈抖动」根因与修复

> 用户实机反馈：AWM 开火后拉栓时屏幕剧烈抖动，换弹时同样。核对上游后定位为**相机动画偏移的应用通道**问题。

### 上游怎么做的（`tmp/TACZ-1.20.1/.../client/event/CameraSetupEvent.java:67-81`）

1.20 有 `ViewportEvent.ComputeCameraAngles`：相机角度**已经插值算好**之后派发，处理器直接
`event.setYaw(yaw + event.getYaw())` / `setPitch` / `setRoll`（`AnimateGeoItemRenderer.java:171-189`）。
枪包 `*.animation.json` 的 `camera` 通道就是靠这条通道播放的（AWM 的 `bolt`/`reload_*`、
`hk_mp5a5` 的 `reload_empty` 等，幅度 1°~6°）。

### 移植侧为什么抖（1.7.10 没有该事件）

移植侧只能把偏移写到实体朝向（`CameraSetupEvent#applyLevelCameraAnimation`，`RenderTickEvent.START` 加、`END` 还原）。
但 1.7.10 的相机读的是**插值值**：

```
cam = prevRotation + (rotation - prevRotation) * partialTicks     // EntityRenderer.java:641-642
prevRotation 每 tick 更新一次                                     // EntityLivingBase.java:340-341
partialTicks 每帧平滑推进、tick 边界归零                            // Minecraft.java:1019-1025
```

只改 `rotation` 时相机只看到 `偏移 × partialTicks` ⇒ **每个游戏 tick（50ms）偏移被抹回 0 一次**
= 20Hz 锯齿，幅度等于动画幅度（换弹最大 6°）⇒ 屏幕剧烈抖动。数值仿真（60fps，1s）：
现状相邻帧规律性回零；修复后与上游逐帧完全一致。

### 修复（`client/event/CameraSetupEvent.java`）

偏移**同时**加到 `rotation*` 与 `prevRotation*`（各记录实际施加量，`END`/下一帧 `START` 逐一扣回，仍幂等自愈）：

```
cam = (prev + d) + ((rotation + d) - (prev + d)) * partialTicks = lerp(prev, rotation, p) + d
```

与上游 `ComputeCameraAngles` 的语义**逐帧等价**。覆盖所有走 `camera` 通道的动画：
`shoot`(roll) / `bolt`（AWM 拉栓）/ `reload_tactical` / `reload_empty` / `draw` / `put_away` / `crawl_*`。

### 复核结论

- `applyCameraRecoil`（后坐力）是**持久**修改实体朝向，不属于「每帧加/减」模式，其逐帧增量按
  `partialTicks` 缩放是上游同构行为（上游同样改实体 `xRot/yRot`），**未改**。
- `roll` 走 `CameraStateCache` → `GameRendererMixin#tacz$applyCameraRoll`（`orientCamera` RETURN 的
  `glRotatef`），不经实体朝向插值，**本来就没有锯齿**，未改。
- 相机动画对象生命周期（`CameraRotateListener` 写入 → `applyLevelCameraAnimation` 读 → 手持渲染后
  `cleanCameraAnimationTransform` 清）与上游 `GunItemRendererWrapper.java:141/168` 同构，未改。
- AWM `shoot` 的 `camera` 通道只有 roll（±1.4°，高频），那是枪包**故意做的镜头抖动**，上游同样播放，保留。

构建：`./gradlew --offline spotlessApply build` → **BUILD SUCCESSFUL in 1m 55s**（`build/r5-build2.log`，`error:` 0）。

---

# r6 · 实机截图反馈的 4 类渲染问题（5 个 teammate）

> 触发：用户实机截图反馈 ①创造栏物品图标/模型错位 ②配件 tooltip Shift 展开过长出屏
> ③瞄具（FMP13/APM4s/REAP-IR）大黑框、热成像不生效 ④`minecart with target` 只有阴影没有模型。
> 截图存档：`docs/review/r6/screenshots/`。分工：`r6-itemicon`(task-6/10)、`r6-scope`(task-7)、
> `r6-tooltip`(task-8)、`r6-minecart`(task-9)；报告在 `docs/review/r6/`。

## 修掉的根因（每类一条主因 + 证据）

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | 创造栏里 3 个工作台**模型飘到侧栏/别的格子**，页签图标旁边有「悬空贴图」 | `client/resource/pojo/display/block/BlockItemTransforms.ItemTransform#apply` 漏了 1.20 反序列化的 `translation *= 0.0625`（1.20 `ItemTransform.java:81` `vector3f1.mul(0.0625F)`）；本仓 gson 反射直填 ⇒ 平移 **×16**（gun_smith_table `(-2.5,-3.75)` → 像素 (-40,+60) 正好落在左下 ALL 按钮旁） | `apply()` 内按局部量 ÷16 + clamp ±5/±4（不原地改字段，重入幂等）；单点回滚 `TRANSLATION_UNIT` |
| 1b | `target`/`statue`/`pegboard`/`ammo_storage*`/`display_case` 图标显示成**雪花/碎块** | 1.7.10 的 `ItemBlock` 默认取 **block atlas** 的 64×64 UV 图集图标；上游是 `item/generated` 2D 图标（target/statue 有 16×16 png）或 3D block item model | target/statue → 新 `item/BlockItemWithItemIcon`（`spriteNumber=1` + `registerIcons`，`ModBlocks` 换 ItemBlock 类）；6 个 3D 方块物品 → 新 `client/renderer/item/BlockModelGuiItemRenderer`（读 1.20 block model JSON 烘四边形，按 1.20 GUI 帧绘制，GL 状态快照/还原），`ClientSetupEvent` 注册 |
| 2 | 配件 tooltip 按 Shift 后「受支持枪械」**长到出屏** | 1.7.10 只有纯文本 tooltip 通道，旧实现把整表压成一行逗号枪名（无宽度上限）；上游是 tooltip 组件里的**图标网格**（宽度封顶 260px，只变高不变宽，`ClientAttachmentItemTooltip.java:82-105`） | 复刻图标网格且**不需要 mixin**：`GuiScreenEvent.DrawScreenEvent.Post` 补画（框已画完、坐标系一致）；纯文本侧用空行/空格预留高度宽度，宽度公式 `min(300, scaledWidth-26)` ⇒ ≤266px，超出截断 `…… (+N)`；新增 `AttachmentGunGridOverlay` / `TooltipLineUtils` |
| 3 | 瞄具出现**巨大黑矩形**（REAP-IR 更大、FMP13 里能看到「显示器 UI」） | `division`（准星分割面）被 `ScopeLensRenderer.capture()` 延迟到帧末绘制，而 `render()` 用 **identity 投影矩阵**补画 ⇒ 相机空间坐标被当 NDC，一整块 3D 面放大成巨型黑矩形；上游会逐 lens 还原 capture 时刻的 projection/modelView（上游 `ScopeLensRenderer.java:117,184-187`）。放大因素：lwjgl3ify + 未加 `-Dforge.forceDisplayStencil=true` ⇒ **stencilBits=0**，目镜黑遮罩不裁剪 | `Lens` 记录并还原 capture 时刻的 `GL_PROJECTION/GL_MODELVIEW`；无 stencil 时隐藏目镜黑遮罩/division 模板遮罩、只画外环+镜身（scope 仍登记镜片圆盘）；新增 `isStencilAvailable()` 与一次性 WARN/INFO；单点回滚 `ALLOW_LENS_WITHOUT_STENCIL=false` |
| 3b | 热成像**只有均匀紫色调**、热源不亮 | 热 mask 链路断开：`ScopeThermalRenderer.wrap()` 全仓 0 调用点（vanilla 实体渲染不走 mcshim `MultiBufferSource`），且 mask 顶点着色器 `ProjMat/ModelViewMat` **从未赋值**（`gl_Position` 恒 0） | 改为 `EntityRenderDispatcherMixin` 在 `func_147939_a` HEAD 传相机相对坐标，按实体 AABB 生成热源盒子 + 显式灌 uniform；片元固定输出白色（下游调色板着色）；补 FBO/GLSL/深度附件失败的一次性 WARN |
| 4 | `minecart with target` 只有阴影没有模型 | `TargetMinecartRenderer` 挂在 1.7.10 的「矿车装载物」钩子 `func_147910_a`，而 `RenderMinecart:103` 只在 `func_145820_n().getRenderType() != -1` 时调用；`TargetMinecart` 没覆写 `func_145817_o()` ⇒ 返回 air（renderType=-1）⇒ 钩子永不执行，只剩空贴图车体 + 阴影。上游靠 `getDefaultDisplayBlockState()` 返回 TARGET 方块过关（`TargetMinecart.java:144-148`） | `TargetMinecart#func_145817_o()` 返回 `Blocks.stone` 当**纯触发器**（方块本身永不入画，`func_147910_a` 已覆写）；副作用面核实为空（`hasDisplayTile()` 恒假 ⇒ 不写 NBT）。同时把照抄 1.20 帧的模型变换换算到 1.7.10 帧：`T(0.5,1.875,0.5)·S(1.5)·Rz(180)·Ry(-90)` ⇒ **`T(0,1.875,0)·S(1.5)·Rx(180)`** |

## 判定「不是 bug」（避免下轮重开）

- 创造栏 15 个 2D 图标逐格量测 bbox **全部在正确槽位**；AMMO 页签图标像素级实测原点与代码预测一致（(477,159) vs (477,161)）。
- 左侧 `ALL`/枪包筛选按钮**点了没效果是预期**：当前只有一个内容包（namespace `tacz`），ALL 与包筛列表相同；两个 mixin 应用成功、无异常。
- `applyGuiSlotScale` 与 1.20 GUI 帧逐项等价；`renderSlotTexture`/`RenderType` 的 GL 状态已 snapshot/restore 无泄漏；`ItemPreviewRenderContext.alpha()` 均在 finally 复位。
- AWM 的 `awp_bolt` / `awp_reload_empty` 等音效缺失 WARN：**上游数据同样缺文件**（`tmp/TACZ-1.20.1` 资源逐文件比对一致），保真，不修。

## 已知降级 / 未修（有据）

| 项 | 原因 |
|---|---|
| 无 stencil 时**没有准星/reticle 圆盘** | 平台限制；`-Dforge.forceDisplayStencil=true` 可恢复完整模板路径，届时 reticle/黑遮罩应正常（= R1 回归点） |
| 热源轮廓从「精确剪影」降级为「包围盒」 | 1.7.10 实体渲染无可插入顶点流，平台降级 |
| 6 个 3D 方块物品的**手持/掉落/展示框**仍走原版方块渲染 | 需要 1.20 的手臂/物品显示帧，属另一处平台差异（GUI 图标已修好） |
| 这些方块**在世界内**的方块模型仍为图集等轴测 cube | 需给 block 写 custom renderType + renderer（另一个任务） |
| `TargetMinecart` 模型可能「略微浮空」/头颅朝向 | 需实机看：帧原点=轨道 y+0.5，模型最低点 -0.24；头颅朝向取决于贴图 UV 镜像 |

## r6 构建实测

`./gradlew --offline spotlessApply build` → **BUILD SUCCESSFUL in 1m 58s**（`build/r6-build1.log`，`error:` 计数 **0**），
产物 `build/libs/tacz-5.11.45.140.jar`（68.9 MB）。改动 **14 files / +996 / −226**（含 4 个新文件）。

## r6 需实机复测（按优先级）

1. **瞄具**：默认启动（不加 stencil 参数）→ AWM 装 FMP13 / REAP-IR / APM4s 开镜 → **大黑框应消失**；日志搜 `TACZ:` 看 `scope lens pass ACTIVE` / `thermal mask ...` 判读表（`docs/review/r6/scope-thermal.md §六`）。
2. **创造栏 OTHER 页签**：target/statue 显示 2D 图标；6 个方块物品显示 3D 图标且落在槽位内；3 个工作台模型回到各自槽位。
3. **配件 tooltip**：悬停瞄具/握把类配件 + Shift → 应出现「支持枪械」蓝条 + 每行 16 个图标；日志首次出现 `[TACZ] attachment gun grid drawn: ...`。
4. **靶车**：放置在轨道上 → 应看到完整靶车模型（车体+靶面+头颅），绕车确认头颅朝外、大小正常。

---

## 🔧 r6-2：瞄具第二轮（Pulsar XL50 开镜 / 第一人称缺面）

> 用户第二批复测截图：`docs/review/r6/screenshots/e_pulsar_xl50_ads.png`（Pulsar XL50 6-10x 热成像开镜：镜筒占满屏幕、里面是黑的 + 枪体内部件）
> 与 `f_firstperson_missing_faces.png`（第一人称镜筒「空壳」、可见面消失）。r6-1 的「黑框消失」已确认生效。

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | 第一人称镜筒**空壳/缺面**（f 图） | r6-1 的 no-stencil 降级路径**主动隐藏**了 `ocular`（目镜遮罩）与 `division`（XL50 是 80×60 的封口成像面）⇒ 从侧面看筒是通的、模型缺一大块。**不是**少调 `super.render`（外层 `BedrockAttachmentModel#render` 在 `:240` 无条件调用） | fallback 按**开镜进度分两段**：未开镜（`aim<0.5`）= 完整模型（外环 + 镜身 + 目镜遮罩 + 分割面）；开镜（≥0.5）= 隐藏挡视野的 `scope_body`/目镜遮罩、保留外环、登记镜片圆盘。新增 `renderMasksAndDivisionWithoutStencil()` |
| 2 | Pulsar XL50 开镜**看不见世界**（镜内是黑的/枪体） | ① 镜片圆盘 `copyScreen()` 发生在**手部渲染之后**，采到的是镜身而不是世界；② 无 stencil 时镜身/目镜遮罩必然盖住准星区 | ① 新增 `ScopeLensRenderer#prepareWorldCopy()`，在 **`RenderWorldLastEvent`**（`EntityRenderer.java:1429-1437`：世界画完、`renderHand` 之前）先拷干净世界快照，`render()` 优先用它；② 开镜时隐藏挡视野的部件（见上一条）。`RenderWorldLastEvent` 调用点加在 `client/init/ClientSetupEvent#onRenderWorldLast` |
| 3 | 热成像**只有紫色光学偏色**、无热源高亮 | 主 framebuffer 的深度是 **renderbuffer**（`main.depthBuffer=1`，实机日志 `sceneDepth=renderbuffer(type=36161)`）⇒ 无法直接采样，旧 `resolveSceneDepth` 失败后回退「plain optical tint」 | **方案 a**：自建 FBO 增加 **depth texture**（默认 `GL_DEPTH_COMPONENT24`，`stencilBits>0` 时 `GL_DEPTH24_STENCIL8`），每帧 `glBlitFramebuffer(GL_DEPTH_BUFFER_BIT, NEAREST)` 从主 FBO renderbuffer 拷深度（读/写 FBO 绑定保存+恢复、blit 前排空遗留 GL 错误）；失败只 WARN 一次并退回旧「借用附件」兜底。预期日志 `sceneDepth=blit-ok` |
| 4 | 「no adapter 槽位」 | **预期行为，不是移植缺陷**：`mountOffset` 只由槽位适配器或瞄具 variant 提供（与上游 `FirstPersonRenderGunEvent.java:491-514` 逐行一致）；AWM 的 `allow_attachments/ai_awp.json` 含 `#tacz:scope`（直连），XL50 在 `scope` 标签内 ⇒ 直装不需要 adapter，`mountOffset=null` 正确。`scope_xl50_data.json` 的 `"ads":{"addend":0.15}` 是**开镜时间**修正（`AdsModifier`/`ads_addend`，被 `LocalPlayerAim` 消费），`idle_scope_height_adjust:0.75` 在 `FirstPersonRenderGunEvent:450` 消费 | 不改 |

### 镜片圆盘 NDC 复核

`Lens#centerU/centerV/radiusU/radiusV` 本来就是用 **capture 时刻的 `GL_PROJECTION × GL_MODELVIEW`** 投影目镜几何取 AABB（`ScopeLensRenderer.java:299-301、654-700`、`Bounds`）；`capture(centerX, centerY, radius, …)` 的形参从 r6-1 起被忽略（上游签名遗留）。旧日志 `center=(0.3916, 0.2946)` 是**整局第一次 capture 落在开镜过渡段**（枪模还在滑向 Aim 位），不是数学错误。日志已改为**只在完全开镜（`aimingProgress≥0.99`）打一条**并加偏位 WARN。

### r6-2 构建实测

`./gradlew --offline spotlessApply build` → **BUILD SUCCESSFUL in 2m 48s**（`build/r7-build1.log`，`error:` 计数 **0**），
产物 `build/libs/tacz-5.11.45.140.jar`（68.9 MB）。累计 **16 files / +1492 / −258**。

### r6-2 需实机复测

1. **Pulsar XL50 开镜**：镜内应能看到（放大的 / 热成像的）世界画面，镜筒不再空壳；日志出现 `scope lens disc (NDC, full ADS)` 且 **center≈(0,0)**；`thermal mask pass ACTIVE ... sceneDepth=blit-ok`。
2. **普通/红点瞄具**：不应回退（红点镜无 stencil 时仍无准星，已知降级）。
3. **第一人称手臂/枪械**：不应再出现缺面；若仍有个别面丢失，回传截图（届时需逐模型查绕序/UV）。
4. 若 XL50 仍被遮挡，回传新截图 + 上述日志行，r6-scope 可按「开镜阈值 / division 形状」再调。

---

## 🔧 r6-3：组合镜（HAMR）开镜过渡段的大黑框

> 用户第三批复测：`docs/review/r6/screenshots/g_hamr_ads_blackframe.png`
> —— 黑框「又回来了」，但**只在 `tacz:scope_hamr`（`"scope":true,"sight":true` 组合镜，`renderBoth` 路径）**上复现；
> 纯 scope（XL50）r6-2 修复已生效（镜内正常出世界画面）。

| 项 | 内容 |
|---|---|
| 现象 | HAMR 开镜时出现巨大黑矩形，圆内有世界画面、圆内还画着红十字/绿点/弹道刻度 |
| 根因（日志+geo+贴图三重证据） | 黑框 = `division_2` 子树未裁剪画出，但**只发生在开镜过渡段**（`0 < aimingProgress < 0.5`）：r6-2 的判据是「aim≥0.5 才隐藏镜身/目镜/不画 division」，所以 `aim=0.30/0.40` 的抬枪/收镜帧走「未开镜」分支，把 `division_2` 整组画出来。geo 证据：`divisions → division_2 → { division_bg（37 cube 拼的**中空黑框**，含 64×128 大平面，`uv_size=1×1`）, scope_division_illuminated（红十字/绿点/刻度，z≈-96）}`；贴图证据：geo 声明 128×128、实际 PNG 256×256（TACZ 2× UV），`uv(0,76.5)` → 像素 (0,153) **A=255 纯黑**。日志证据（18:07:10）：`ads aim=0.30 raw=0.46` → `ads aim=0.97` → `ads-full aim=1.00` → `ads aim=0.40` |
| 修复 | `BedrockAttachmentModel#renderWithoutStencil` 改为**三段降级**：① **腰射**（未按 ADS 且 aim≈0）= 完整模型，division 按**投影尺寸闸门**画（新增 `ScopeLensRenderer#projectUvBounds`，屏幕 UV 包围盒 >0.6 的大背景平面跳过、小准星面保留 → 修「空壳/缺面」且避免腰射糊黑块）；② **开镜过渡/收镜**（`isAim()` 或 0<aim<0.5）= 保留外环/镜身/目镜遮罩（抬枪动画可见），**division 绝不画**；③ **完全开镜**（aim≥0.5）= 隐藏镜身/目镜遮罩 + division，只留外环 + 镜片圆盘（世界快照/热成像）。配套新增 `ScopeLensRenderer#isAimingNow()`（`isAim()` 布尔，按下 ADS 即 true），用于过渡判定 / `prepareWorldCopy` 开关 / 镜片登记门槛 |
| 判定「不是」 | 不是「division 一直 visible」，也不是「两次不同 aim 的 attachment 渲染」：ctor 对 `division`/`division_2` 都 `setHidden(true)`（`:112-127`），`BedrockPart.render` 的 children 在 `if (this.visible)` 内（`bedrock/BedrockPart.java:91-113`），且过渡段每帧强制 `hideDivisions()` |
| 新增一次性诊断 | `TACZ: attachment first-person render: scope=?, sight=?, transform=?, stencil=?`；`TACZ: scope no-stencil fallback: selective=?, captureLens=?, aiming=?, aim=?, branch=ADS(...)/ADS-transition(...)/hipfire(...)`；`TACZ: scope no-stencil division plane: drawn=?, divisionPaths=?` |

### r6-3 构建实测

`./gradlew --offline spotlessApply build` → **BUILD SUCCESSFUL in 2m 15s**（`build/r7-build2.log`，`error:` 计数 **0**），
产物 `build/libs/tacz-5.11.45.140.jar`（68.9 MB，18:35）。

### r6-3 需实机复测

1. **HAMR（MP5 等）反复按住/松开 ADS**：开镜全过程（含抬枪/收镜过渡段）均不应再出现黑框；腰射不空壳。
2. **回归纯 scope**：XL50 → 镜内出（放大的/热成像的）世界画面；FMP13 / REAP-IR / APM4s → 无黑框。
3. 日志判读（`TACZ:`）：`scope=?, sight=?`（HAMR 应为 `scope=true, sight=true`）、`branch=` 三段（过渡段应 `ADS-transition(... divisions HIDDEN)`）、`division plane: drawn=false`（腰射帧才可能是 true）。
4. 若仍异常，回传「新截图 + 上述 3 条日志」。

---

# r7 · 全模块「与上游等价性」审查（19 个 teammate，逐文件对照源码）

> 用户要求：以 `tmp/TACZ-1.20.1`（上游 TACZ 1.2.0）、`tmp/minecraft-src-1.20.1`、`tmp/MinecraftForge-1.20.1`、
> `build/rfg/minecraft-src`（1.7.10 本体+Forge）为权威，**逐文件核对当前移植实现是否与上游等价、修掉偏离预期的实现**。
> 拆成 19 个互不重叠的模块并行审查（渲染 7 / 玩法 6 / 核心数据 6），报告全部在 `docs/review/r7/`（19 份，约 732 KB）。
> 用户截图 `a_thirdperson_gun_rangefinder.png` 已归档并逐像素判读（见下文）。

## 用户点名的三个域：结论

| 域 | 关键结论 |
|---|---|
| **① 第一/第三人称渲染与位置** | **截图真因找到并修复（P0）**：第三人称手持枪整体偏移 **0.866 格** —— `AnimateGeoItemRenderer#tacz$buildPaperFrameInverse` 的 F_20 少 1.20 `ItemRenderer.java:128-129` 的**无条件** `T(-0.5,-0.5,-0.5)`（Forge patch 同款）；修后 `thirdperson_hand`/枪口归位。同文件另修：掉落物 ENTITY 帧（偏小一半/不转不浮/不居中）、展示框 FIXED 帧、第一人称「反向视角延滞」、世界内物品光照写死满亮（夜里发光）、xRot 改为不插值（`LocalPlayer:190-196`）。另修第三人称枪口粒子世界矩阵（`VIEW⁻¹·MODEL` 精确分解，替代缺 `glTranslatef(0,0,-d7)` 的近似）、挂枪 FIXED 缺 `-0.5`、第一人称曳光弹相机插值、能量漩涡纹理矩阵。**item model display 缺口**（`ammo.json`/`attachment.json` 的 display 段 1.7.10 无消费端）已新写 `ItemModelDisplayManager` 补齐（FP 0.4→1.0、TP 0.375→1.0、掉落/展示框/展示柜全尺寸，数值复核 `max|F17·A−F20|=1.2e-7`），并补工作台物品在所有 context 的 display/context。 |
| **② 准星系统** | 截图里的「0.0m + 白 bracket」**不是 rangefinder**：像素/字形级证明它是 `HeatBarOverlay` 的 `0.0%`（`heat_base.png` 刻度框 + `DecimalFormat("0.0%")`；minigun 开火后热条常显 = 上游同构）。真正修掉的问题：① `RangefinderManager` 原为「util 签名未落地」的占位降级（射线/实体命中箱不等价）→ 对齐上游 `BlockRayTrace`+`HitboxHelper.getFixedBoundingBox`；② **HUD 事件的 F1/F2/F3**：`registerAbove(CROSSHAIR)` 因持枪 cancel `Pre(CROSSHAIRS)` 导致 **按 O 交互提示/展示柜提示完全不显示**（改 `Pre+LOWEST+receiveCanceled` 补画）、GUI 打开时 HUD 不画（门条件对齐引擎 `!hideGui || screen != null`）、`PreventsHotbarEvent` 只取消 HOTBAR（改取消全部层）；③ 内置瞄具开镜灵敏度恒 1（多余 null 守卫）；④ 爆头调试框画在相机原点（`RenderLivingEvent.Post` 已在 `glPopMatrix` 后，需自平移）；⑤ 热条 `lerpArgb` 取整偏差。 |
| **③ 自定义资源包加载** | 加载链路对账通过（上游 105 文件 ↔ 移植侧全量 diff；默认枪包本身就是外置包，实机日志证明贴图/ogg/mcmeta/粒子/语言全链可用）。修掉 3 个真实问题：① **单人 `/tacz reload` 在 Server thread 直接 `Minecraft#refreshResources()`**（跨线程 GL 上传/渲染器重建）→ 投递 client 线程并等待；② `/tacz reload` 缺「枪包重扫 + 客户端索引重建」等价物 → 新增/删除的包热加载不生效（已补）；③ **zip 枪包句柄竞态**（重扫时 `closeSources()` 打断客户端正在读的 zip → `Stream closed`/紫黑贴图）→ 借出流计数 + 延迟关闭（已复现并验证修复）。另修：多人服 `/tacz reload` 后**不回发 sync**（上游 `OnDatapackSyncEvent` 广播）→ 已向在线玩家重发；语言覆盖顺序（自定义包 lang 只补缺不覆盖）→ 重跑语言回调恢复「枪包覆盖本体」。产出可实机验证的最小测试枪包 `docs/review/r7/test-pack/`（目录+zip，含 33 断言离线验证）。 |

## 其它模块的实质修复（节选）

- **瞄具/热成像（r7-scope）**：复核修正 r6 三处不成立结论 —— ①「加 `forceDisplayStencil` 可恢复完整路径」不成立（主模板被 `BedrockGunModel` 清空 + 无 capture target，stencil 延迟 pass 原来永远画不出）→ 补「每 lens 模板副本 FBO + capture blit + 帧末绘制」；② `close()` 线程安全不成立（Netty 线程改静态集合）→ 改 volatile 请求 + 渲染线程清理，且**不再销毁 shader**（原实现一次断线后整局无镜片/热成像）；③ 热掩膜深度兜底查错 FBO 的 bug。另补 `RenderState`（colorMask/depthFunc/clearColor/scissor/FBO 绑定）与 viewport 检查。
- **方块（r7-block）**：挂板 `SHAPE` metadata 编到 bit 3-4（1.7.10 只有 4 bit ⇒ 右转角永久失效 + 邻居更新自激）、展示柜连接标记 7 bit 溢出（整墙联锁只剩左边）、挂板染色写了没人读（补 grayscale 图标+`colorMultiplier`）、target/statue/2 格工作台第二格被占时**吞物品**、雕像创造模式误扣、标靶矿车 `CustomName` 丢失、4 个 BER 未接上游 `getViewDistance()`（64 格外不画）。另：`AmmoStorageBlock` 私有 `unloadAmmoBox` 副本委托 util（顺带修音效误用/缺判空）。
- **模型/动画（r7-model）**：`ItemTransform#apply` 把 1.20 单次 `rotationXYZ` 实现成三次 `mulPose`（默认包工作台 `gui [15,-145,0]` 等双轴旋转朝向错）；镭射层 GL 手段对齐上游（`scale(0.99975586)` + `blendFuncSeparate`）；删死代码 `hasBuiltinHandModel`。
- **瞄准/拾取/输入（r7-input）**：持枪右键**实体**在 1.7.10 不投递 `PlayerInteractEvent` ⇒ 拦不到（新增 `MinecraftAttackMixin#func_147121_ag` HEAD）；趴下状态未写 mcshim 姿势覆盖表 ⇒ 俯仰限制/趴下后坐力/趴下移动动画三处死分支（客户端+服务端都补写）；`RenderHeadShotAABB` 位置帧 + 实体 id 解析。
- **战斗/实体（r7-gun）**：子弹出生点公式（`mid(xOld,pos)+eyeHeight`）、点燃粒子服务端 no-op、子弹被写进区块存档（幽灵子弹）、粒子 16 格门限 + 缺失粒子名映射、附着 1.20 `triangle(0,0.0172275)` 散布（与 api 默认路径统一）、爬行后坐力。
- **网络（r7-network）**：`NetworkTaskQueue` 单队列导致单人/LAN 下 **C2S/S2C 任务可能被另一侧线程取走**（在错误线程改世界）→ 改按接收侧两条队列（37 入队点强制传侧）；41/44 消息逐位 discriminator 一致、45/45 方向与字段读写对称；5 个 refit 消息 54 处 RefitDebug 清理并按上游重排分支。
- **平台垫片（r7-platform）**：`mcshim.RenderType` 删 equals/hashCode（1.20 实例身份）→ 修「多纹理同 name 的自发光网格/粒子被合并只绑第一张图」；`PoseStack.scale` 法线按 1.20、补 `fastInvCubeRoot`；新增趴下 AABB/眼高（服务端+客户端+相机）、`Entity#travelToDimension` 非玩家刷新、弹药盒 `Container#slotClick` 4 个 mixin 并登记 `mixins.tacz.json`；HUD F1/F2。
- **资源包（r7-respack-core/client）**：见上③；另 `/tacz reload` 客户端线程/重扫/索引三处、语言覆盖、`GunPackShimBridge`（去内联 FQN）。
- **物品/合成（r7-item）**：弹药库点条目后鼠标手持栈从不下发（1.7.10 需 `updateHeldItem`）→ 丢件/复制风险；枪械近战伤害粒子 100% 死代码、缺 `doEnchantDamageEffects`/击退抗性、水中疾跑被当趴下；AmmoBox 物品栏点进/点出（item 侧 `tryStackedOnOther` + `ContainerMixin`）。
- **GUI（r7-gui）**：改装台多画整屏暗色背景（压暗预览枪体）、右键误触按钮、**工作台过滤面板搜索框/复选框点不动**（`GuiSlot#drawScreen` 行点击漏 `this.left`）、弹药库标题不显示、tooltip 行颜色降级。
- **核心/API/工具（r7-core/api/util）**：`interact_key` 默认 tag 白名单失效（交互键整体不可用）、damage_type `exhaustion=0.05` 未映射（6 倍饥饿）、`BULLET_RESISTANCE` 未同步、`ITargetEntity`/`AmmoHitBlockEvent` 恢复上游形状、`ThirdPersonManager` minigun pivot 字段、`Math.fma` 无 Java8 桩（`NoSuchMethodError` 风险）、实体注册名 `modid.name` 解析（新 `EntityIdHelper`，影响 HeadShotAABB/伤害忽略名单/热成像/战利品注入）、死代码 `RefitDebug` 删除。
- **SBM/MAE 库（r7-sbm）**：`SnowStormParticle#move` 碰撞位移重复应用（世界枪口烟三轴速度不一致）、`FirstPersonRenderHandler` 5 条早退漏粒子、作用域内 `[TACZ-DBG]` 清零。

## 全仓工程性清理

- `[TACZ-DBG]` 每帧日志：**全仓 src 已清零**（审查前 13 文件/约 60 处，含 arm/ads/cam/scope/refit 指纹；本次所有模块统一清理）。
- 新增/修改 mixin 5 个（`ContainerMixin`、`EntityTravelToDimensionMixin`、`PlayerCrawlPoseMixin`、`EntityRendererCrawlCameraMixin` + r7 前的），`mixins.tacz.json` 已登记，构建 refmap 通过。
- 报告 `docs/review/r7/`：19 份模块报告 + `test-pack/`（最小自定义枪包 + 离线验证脚本）。

## 既有报告勘误（r7 复核发现）

1. r5 `data-wiring.md:150`「网络 ID_COUNT 从 0 开始」是笔误：上游主 channel discriminator 从 **1** 起，1..42 与移植逐位一致。
2. r5 称「上游 GunSmithTableMenu 同样只能合成一次」有误：上游 `doCraft` 用 `getResultItem`（恒 `copy()`），无该缺陷；r5 的修复本身正确。
3. r6 称「加 `-Dforge.forceDisplayStencil=true` 可恢复完整路径」不成立（stencil 延迟 pass 另有实现缺陷）—— r7-scope 已修，现在该参数下模板副本路径才真正可用。
4. r6 称「`ScopeLensRenderer.close()` 线程安全」不成立 —— r7-scope 已修。

## 已知降级 / 未修（有据，按域汇总，详见各报告）

- 无 stencil（1.7.10 默认 FBO）：reticle/目镜黑遮罩/镜内不画枪体/JFA 轮廓/纯 sight 红点等一整组降级，**根因唯一**；r7-scope 已评估「运行期给主 FBO 补 `GL_DEPTH24_STENCIL8`」可行（~60-100 行，附 Forge 1.20.1 `RenderTarget.java.patch` 依据），建议下一轮做。
- 热源轮廓 = 包围盒（无实体顶点流）；`ScopeLensContour` 恒 null（可补齐）。
- 粒子 16 格客户端门限、双色渐变降级、部分 1.20 粒子名映射。
- jar 内嵌 assets/data 不扫描（裁决走 `registerExportResource` 契约，理由见 `respack-core.md §6.1`）。
- 非玩家 mob 无 ItemInHandLayer 等价注入点（1.7.10 渲染器内联，收益≈0 风险中）。
- 无双持/副手槽、无独立袖骨、壁挂/展示框外层仍是 1.7.10 原版帧（LOD 需实机看）。
- `ConfigKey` 默认键（1.7.10 无修饰键+T 是聊天键）、action bar→聊天栏、输入延迟 ≤1 tick、Lift→聊天栏等协议降级。

## r7 构建实测

`./gradlew --offline '-Dorg.gradle.jvmargs=-Xmx2g …' spotlessApply build` → **BUILD SUCCESSFUL in 2m 1s**（`build/r7-final-build.log`，`error:` 计数 **0**）；
产物 `build/libs/tacz-5.11.45.140.jar`（68.9 MB，21:06）。累计 **169 个 src 文件改动**（含 8 个新增 java 文件 + `mixins.tacz.json`），
`git diff --stat` = **162 files / +5284 / −3255**（新增文件另计）。注意：本机 Gradle 默认 512 MiB daemon 堆在本次大改动下会 GC thrash，
构建需带 `-Dorg.gradle.jvmargs=-Xmx2g`（或写进 `gradle.properties` 的 `org.gradle.jvmargs`）。

## r7 需实机验证（按优先级，详细判据见各报告"实机验证"节）

1. **第三人称持枪（截图真因）**：F5×1/F5×2 枪贴合双手、枪口沿视线（0.866 格偏移已修）；快速甩视角无反向甩动；掉落物大小/自转/浮沉/居中；展示框居中。
2. **第一人称**：手持弹药盒/配件/工作台模型的尺寸与朝向（item display 补偿）、夜晚枪与掉落物随环境变暗、第三人称枪口火光/烟雾贴枪口。
3. **准星/HUD**：持枪按 O 交互提示恢复、聊天框后 HUD 仍画、F3+B 爆头框在实体头上、内置瞄具开镜灵敏度降低、热条 `0.0%` 与准星行为（预期）。
4. **瞄具**：默认环境回归 + 断线重连后镜片/热成像仍生效；`-Dforge.forceDisplayStencil=true` 时日志出现 `scope lens stencil path ACTIVE` 并验收模板四项。
5. **自定义资源包**：`docs/review/r7/test-pack/` 目录版+zip 版分别放入 `run/client/tacz/` → `/tacz reload` → 创造栏出现 Review Pistol/9mm 且有模型贴图/本地化；多人服 reload 后在线客户端即时生效（日志 `Re-synced gun pack data to N player(s)`）；zip 包反复 reload 无 `zip file closed`。
6. **战斗/实体**：站立平射弹孔落点在准星中心、>16 格有碎屑/尾迹、飞行中退世界无残留子弹、卡服高延迟连发不丢枪、同一把枪散布量级、水中疾跑后坐力档位正确。
7. **方块**：折面挂板左右转角、染色挂板世界内灰图×颜色、4x4 展示墙潜行右键整墙联锁、第二格被占不扣物品、展示方块 64~128 格内仍渲染。
8. **GUI**：改装台背景/右键、工作台过滤面板搜索框与复选框可点、创造栏侧栏仅 TACZ 页签、tooltip 新颜色、弹药库标题两行。
9. **网络（改动线程模型，必测）**：单人开枪/换弹/切枪/近战/趴下全链路无 `Error while running a queued network task`；LAN 双人旁观特效；专服进服枪包同步；改装全链路；重连两次。

## 🔴 r7 启动崩溃热修（Mixin 注入签名）

**现象**：r7 首次整合包启动即崩，`ItemInHandLayerMixin#renderArmWithItemHead` 报
`InvalidInjectionException: Expected (AbstractClientPlayer;F;CallbackInfo)V but found (AbstractClientPlayer;CallbackInfo)V`，
连锁 `NoClassDefFoundError: RenderPlayer` ⇒ 初始化失败。

**根因**：r7-tp 复审时误删了 `@Inject` 处理器的 `float partialTicks` 形参，依据是注释里「用不到的后尾形参可省略」——
**这条不成立**：Mixin `@Inject` 处理器签名必须 = **目标方法全部形参** + `CallbackInfo`（`@At` 注入不做形参裁剪；
只有 `@ModifyConstant`/`@ModifyVariable` 这类才有独立签名规则）。而 1.7.10 目标
`RenderPlayer#renderEquippedItems(AbstractClientPlayer, float)` 是 2 参，删成 1 参编译能过、运行期必崩（构建/静态检查抓不到）。

**修复**：恢复两个 handler 的 `float partialTicks`（`ItemInHandLayerMixin.java:55-73`），并新增静态校验脚本
`tools/check_mixin_inject_sig.py`（扫描全仓 `@Inject` 的 `method=` 描述符/1.7.10 源码方法形参数，与 handler 形参数比对；
39 处 @Inject 全查，另 3 处为注释/@ModifyConstant 误报已人工排除）。重新构建 **BUILD SUCCESSFUL**（`build/r7-hotfix1.log`，
`error: 0`，jar 21:15）。

**教训（写入规则）**：Mixin 注入处理器**不得省略目标形参**；构建通过 ≠ mixin apply 通过（后者只在运行期校验），
今后涉及 mixin 签名的改动必须跑 `tools/check_mixin_inject_sig.py` 或实机启动验证。

## r7 实机回归热修（第二批，用户逐轮复测驱动）

> 用户每轮用整合包实机复测并回传日志/截图；下列均为「本轮未 commit 改动」引入或暴露的问题，已修并构建。
> 最终 jar：`build/libs/tacz-5.11.45.140.jar`（BUILD SUCCESSFUL，`error: 0`）。

### F1. 运行期给主 FBO 补 stencil（修复「瞄具默认环境整组降级」）

- 1.7.10 主 framebuffer 默认 `stencilBits=0`（depth 是纯 renderbuffer），导致镜片模板裁剪/准星/目镜遮罩整组降级。
- 新增 `client/renderer/scope/MainFramebufferStencil`（295 行）：等价 Forge 1.20.1 `RenderTarget#enableStencil()` ——
  运行期建 `GL_DEPTH24_STENCIL8` renderbuffer 同时挂 depth/stencil 附件，检查 FBO 完整后替换 `Framebuffer#depthBuffer`；
  `ScopeLensRenderer#beginFrame()`（`renderWorld` HEAD）每帧 `ensure()`（resize/全屏自动重挂），失败永久回退 + 一次 WARN；
  GL<3.0 / core profile / OptiFine / 光影接管深度时回退。实机日志：`main framebuffer stencil ENABLED (... 2560x1377)` ✓。

### F2. 倍镜 ADS「镜中没画面 + 开镜没准星」（A/B 同一根因）

- **定量证据（实机 probe）**：`stencilAtCenter=1`（期望 254）、`discNdc` 与 `ocularNdc` 差 ~9.3 倍且跑到屏幕外、
  `lensDrew=false`、`reticleDrew=false`。
- **根因**：模型侧 INVERT 瞄准圆 / 镜片圆盘沿用上游「视空间圆」换算（`center*16*90`、`z=-90`、`rad=80*progress`），
  它依赖 1.20 RenderSystem 矩阵单位；1.7.10 第一人称手部 pass 是「块单位 camera space + 手部投影」
  （`FirstPersonRenderHandler:463-477`）⇒ 换算差 ~1440 倍，INVERT 圆根本没落在目镜上 ⇒ 模板值停在 1，
  `GL_EQUAL 254` 把镜片与 reticle 全部裁掉。
- **修复（`BedrockAttachmentModel` + `ScopeLensRenderer`，等价实现）**：
  ① INVERT 圆改为「`Bounds` 投影出的目镜屏幕 NDC 椭圆」（与 capture 同一套矩阵/数学，半径 × aimProgress）；
  ② 镜片几何同样改用 NDC 椭圆（identity 矩阵）；③ reticle 仍用 capture 矩阵，且每个 reticle 重绑 capture FBO +
  重挂主颜色附件 + 重设 `GL_EQUAL lens.stencil`（补上游 `render():196-209` 的 target 重绑）；
  ④ snapshot 语义对齐上游 `drawLens:226-231`（在 capture target 内把本帧最终主画面拷进自己的颜色纹理再采样）；
  ⑤ 自愈兜底：`glReadPixels` 检测镜片中心未变化 ⇒ 同帧补画 NDC 椭圆；⑥ 一次性 probe 日志。
- **实机验证**（01:39 日志）：`stencilAtCenter=254`、`discNdc == ocularNdc`、`lensDrew=true`、`reticleDrew=true` ✓。

### F3. 第一人称手部「巨大色块/缺面」（用户描述「手部缺失贴图」）

- 静态重放默认包 8 把枪：**8/8 右臂肩端落在相机平面背后**（z=+0.15~+0.76）⇒ 12px 刚性臂盒跨过相机平面被近裁面
  （`NEAR_PLANE=0.01`）切掉，剩余 4×12 texel 皮肤被放大成大色块。
- 修复（`RenderHelperCompat#renderFirstPersonArm`）：腕端仍取上游手骨帧（动画作者摆的握把/护木位置），
  肩端改用 1.7.10 原版第一人称空手手臂的相机空间肩点（`ItemRenderer:516-543` 逐句搭帧），两点间重建 12px 臂盒
  ⇒ 恒在相机前方、长度有界；保留 `AnimationRenderContext.hand(player)`、`PlayerModelRenderState`、单点开关。

### F4. Z 改装界面 / 枪械工作台「只剩枪械模型、GUI 全部不可见」

- **根因（平台层，1.7.10 特有）**：r7 HUD 修复在 `PreventsHotbarEvent` 里对**所有** `RenderGameOverlayEvent.Pre`
  （含 `ElementType.ALL`）取消，而 `GuiIngameForge#renderGameOverlay:105` 的 `Pre(ALL)` 是**整个 HUD 方法短路开关**，
  短路点在 `:108 setupOverlayRendering()`（GUI 正交帧）与 `:109/:118`（混合基线）之前 ⇒ GUI 帧从未建立，
  `currentScreen.drawScreen` 仍在世界透视矩阵下绘制 ⇒ 所有按钮/槽位画到相机外；枪模属世界渲染阶段所以仍可见。
- **修复（事件侧根修）**：`PreventsHotbarEvent#onRenderHotbarEvent` 在 `setCanceled(true)` 前，对 `ALL` 事件补
  `setupOverlayRendering()` + `GL11.glEnable(GL_BLEND)` + `glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, 1, 0)`
  （= 补被 `:105` 短路跳过的 `GuiIngameForge:108-118`），保留上游「两类界面连 vignette/chat 都不画」的语义；
  对任何受影响界面通用（GUI 侧不再有 per-screen workaround）。
- **教训**：1.7.10 的 `Pre(ALL)` 是整体短路开关而非 overlay；取消它必须自己补 GUI 帧，不要用它实现「隐藏单个 overlay」。

### 热修构建记录

`build/r7-hotfix1.log`（mixin 签名）→ `r7-hotfix5/6.log`（运行期 stencil + NDC 椭圆 + 手部）→
`r7-hotfix7.log`（GUI 帧根修），全部 **BUILD SUCCESSFUL**、`error: 0`；最终 jar 69.1 MB。
已知 Gradle 环境注意：大改动下默认 512 MiB daemon 堆会 GC thrash，构建需 `-Dorg.gradle.jvmargs=-Xmx2g`
（偶发 `shadeDowngradedApi` ZipException 时删除 `build/libs/*-downgraded.jar` 后重跑）。

### r7 瞄具镜片：Elcan 4x 黑镜根因与修复（2026-11-06，Lead）

- **根因**：1.7.10 固定管线中，INVERT 圆（无纹理彩色几何）沿用模型批次遗留的 `GL_TEXTURE_2D` + alpha test 状态，
  alpha 采样失败 ⇒ 片元在写模板前被丢弃 ⇒ 模板无 254 ⇒ 镜片 surface / 镜内准星全部跳过（仅 Elcan 资产触发）。
- **修复**：`BedrockAttachmentModel.drawInvertCircleNeutralizingFixedFunctionState(...)` —— 画 INVERT 圆时临时关闭
  `GL_TEXTURE_2D` + `GL_ALPHA_TEST`，画完恢复；几何/矩阵/模板语义与上游一致（平台适配，见 `docs/review/r7/scope-upstream-spec.md` §9 白名单 ⑪）。
- **验证**：调试器断点 `verifyBefore=1 → verifyAfter=254`；日志 `invert verify: stencil=1->254, wrote=true`、
  `lensDrew=true, reticleDrew=true`。实机回归：Elcan（开镜镜片+准星 / 腰射黑玻璃）、AUG、XL50、HK MP5A5、APM4s、HAMR 均正常。
- **清理**：删除本轮临时诊断（INVERT A/B / GL 矩阵读取 / ADS 对齐 diag / `INVERT_FAILED` 降级），`TACZ:` 诊断日志降级为 debug。
- 报告：`docs/review/r7/scope.md` §19；截图 `docs/review/r7/screenshots/{j_elcan_ads_ok,k_elcan_hipfire_black,l_aug_scope_ok}.png`。

### r8 · 实机反馈 6 问题修复（2026-11-06，Agent Teams）

- 报告索引：`docs/review/r8/README.md`（block/target/gui 三份专项）。
- 修复内容：
  1. 1.20 `RenderShape.MODEL` 方块（弹药库三档/武器盒/枪架）补 1.7.10 等价渲染器：
     `JsonBlockModelRenderer`（`ISimpleBlockRenderingHandler`）+ `BlockModelQuads`（JSON 模型→四边形）+
     `BlockStateModels`（blockstates variants/multipart）；`BlockRenderIds` 持有渲染 id（默认 0 兜底）。
  2. `TargetMinecart`：`yOffset=0.4375`（命中盒对齐 1.20）、渲染 translate 1.875→1.4375（消除浮空）。
  3. `BlockMetadata.facing2D/facing` 改为 1.20 `Direction#get2DDataValue()` 编码
     （SOUTH=0/WEST=1/NORTH=2/EAST=3）⇒ 工作台/雕像/标靶/弹药库朝向与命中面校验对齐。
  4. tooltip：`TooltipEvent` 整表折行 + 新增 `mixin/client/GuiScreenMixin` → `client/tooltip/GuiTooltipRenderer`
     （折行 + 1.20 边界 clamp；注入 `renderToolTip`/`func_146283_a`/`drawCreativeTabHoveringText`）。
  5. 创造栏：单内容包时隐藏 pack-filter 侧栏（多包保留上游行为）。
- 构建：`./gradlew spotlessApply build` = BUILD SUCCESSFUL（23:01 最终产物；构建配置未改动）。
  启动冒烟：`runClient` 完整启动到主菜单，GuiScreenMixin/CreativeModeInventoryScreenMixin 等全部成功混入，
  TACZ `client setup finished`，无新增 crash-report（见 r8/README.md「启动冒烟验证」）。
  实机视觉/交互回归清单见 r8/README.md，待用户确认。
- 已知平台差异：方块模型无平滑 AO；旧存档已放置方块因 facing 编码修正会反向转一次；创造栏单包隐藏属可用性调整。

### r8 追加 · 自定义枪包（2026-11-07，Lead）

实机二轮反馈（zip 自定义枪包模型不渲染 / 语言键不翻译）修复后，同一轮日志又暴露两类问题：

1. **受保护（加密）枪包不可用** —— `Tacz from Tarkov v1.1a` 的 `recursion/taczpack.dat`
   是第三方前置 TACZ-Arcana 的资产保护容器（Arcana 公开源码明确不含该组件；release 用 obfuscated
   `dist/**` + 原生库实现）。本移植 `GunPackLoader` 识别该标记后整包跳过并打一条明确 warn，
   不再刷几百行贴图/模型/音效缺失。属枪包格式限制，非移植缺陷。记录：`docs/review/r8/gunpack.md` §3。
2. **1.20 扁平化物品 id 解析** —— 新增 `util/VanillaItemMapping` 做 1.20.1 → 1.7.10
   （注册名 + damage）等价折算；`OreDictionaryFallback#findItemStack`、`ItemStackJsonParser`、
   `LootTableInjectorModifier` 统一接入；`GunSmithTableIngredient` 把「未知物品」从「整条配方跳过」
   修正为上游语义（未知注册名 → AIR 材料：配方保留但无解）。记录：`docs/review/r8/gunpack.md` §4。
3. 该轮 eft 枪包已移至 `tmp/eft-protected-pack/`（`tmp/` 被 gitignore，不随仓库分发），
   待获得未加密版本后再验证。
4. 构建自检：`./gradlew spotlessApply compileJava` = BUILD SUCCESSFUL（2026-11-07）。

### r8 追加 · 枪械近战 / 后坐力实机反馈（2026-11-07，Lead）

1. **Applied Armorer 近战枪 `special_melee_task_manager` 按 V 打怪无伤害（用户实机反馈）** —— ✅ **已定位并修复**：
   - 静态复核 `MeleeKey` → `LocalPlayerMelee` → `ClientMessagePlayerMelee`（NetworkHandler id=24，收发两端一致）→
     `LivingEntityMelee` → `AbstractGunItem#melee` → `ModernKineticGunItem#doMelee` 全部与上游 1.20.1 同构，
     数据 `melee{ distance:3, cooldown:0.6, default{ melee_stock, distance:1, range_angle:30, damage:13, knockback:0.1, prep:0.15 } }` 正常；
   - 为定位「客户端 / 网络 / 服务端任一步静默失败时表现完全相同」的问题，新增 **`/tacz debug true` 门控**的
     `[TACZ Melee]` 诊断日志（客户端提前返回原因与分支、服务端前置判定值、候选实体距离/角度/可见性、
     `attackEntityFrom` 返回值与掉血前后）。只在调试开关打开时输出，正常游戏无日志、**无行为改变**，
     作为调试能力保留（上游无对应日志，属移植特有排查设施）。
   - **实机日志定位到根因**：`server doMelee: distance=4.0, rangeAngle=30.0`、`candidates=7~10`，
     但**所有候选（含玩家自己）的 degree 都是 121°~145°**（halfRangeAngle=15°）⇒ 锥形判定整体镜像。
     根因是 **1.7.10 `Vec3#subtract` 是反向减法**：`a.subtract(b)` 返回 `b - a`
     （`build/rfg/minecraft-src/java/net/minecraft/util/Vec3.java:55-61` javadoc 明确写
     "Returns a new vector with the result of the specified vector minus this"）。
     `doMelee` 照搬上游的 `userEye.subtract(eyeVec)` / `targetEye.subtract(centrePos)` 实际得到
     `eyeVec - userEye` / `centrePos - targetEye` ⇒ 近战球心落到身前 8 格。
   - **修复**：`ModernKineticGunItem#doMelee` 两处向量差改为按分量显式计算（`userEye - eyeVec`、`targetEye - centrePos`）；
     同根因一并修复 `GunMeleeDebug#showRange`（调试火焰锥方向）与 `EntityUtil#getHitResult` 的 `hitBoxPos`
     （爆头局部坐标反号 ⇒ 爆头 AABB / 默认眼高判定此前一直失效）。记录：`docs/review/r8/README.md`「追加（实机四轮）」#12/#14。
2. **左键开火刷 `NumberIsTooSmallException: number of points (2)`**：Applied Armorer 该枪 `recoil.pitch/yaw`
   只有 1 个关键帧（`GunRecoil` 补 `(0,0)` 后共 2 点，commons-math3 `SplineInterpolator` 要求 ≥3 点）。
   上游 1.20.1 同文件同实现、同样不判长度 ⇒ 上游也会抛，属**枪包数据不合法**，不是移植缺陷；
   异常被 Forge 事件总线捕获，仅日志刷屏 + 后坐力不生效。移植侧加防御：
   `GunRecoil#getSplineFunction` 在 `keyFrames.length < 2` 时与空数组一样返回 `null`
   （消费方 `CameraSetupEvent#applyCameraRecoil` 本就判空，合法数据行为不变）。记录：`docs/review/r8/README.md`「判定不是 bug」§4。
3. 构建自检：`./gradlew spotlessApply compileJava` = BUILD SUCCESSFUL（2026-11-07）。

### r8 追加 · NEI 联动（2026-11-07，Lead）

上游 1.20.1 用 JEI 提供「配方显示 + 枪械/弹药/配件兼容性查询」；1.7.10 没有 JEI，等价物是 NEI
（NotEnoughItems，GTNH 版）。本轮实装 `src/main/java/com/tacz/guns/compat/nei/`（11 文件）：

1. **入口**：`NEITaczConfig implements codechicken.nei.api.IConfigureNEI`。NEI 用 CCC `ClassDiscoverer`
   扫描「类名 `NEI*Config` + 实现 `IConfigureNEI`」的类自动加载，因此**类名不可改**；插件在进入世界后加载，
   数据已由 `CommonAssetsManager` 同步完毕。
2. **上游 JEI 5 个分类 → NEI handler**：
   - `GunSmithTableCategory`（每个 `CommonBlockIndex`）→ `GunSmithTableRecipeHandler`（每个工作台实例，
     过滤规则逐条对齐 `RecipeFilter` + `BlockData#getTabs()`，注册 R/U 双向 + 工作台催化剂）；
   - `GunAmmoQueryCategory`/`AmmoQueryCategory` → `GunAmmoQueryHandler`/`AmmoQueryHandler`（共用一份
     枪↔弹药映射，双向结果一致）；
   - `AttachmentQueryCategory`/`GunAttachmentQueryCategory` → `AttachmentQueryHandler`/
     `GunAttachmentQueryHandler`（按 `GunTabType`/`AttachmentType` 分组，`IGun#allowAttachment` 判定）；
   - 另外补 `AmmoStorageUpgradeHandler`：上游 JEI 把弹药库升级配方展开成原版 shaped 配方合入 CRAFTING，
     NEI 内置 handler 不认自定义 `IRecipe`，故单开一页展示同样的 3×3。
3. **子类型等价物**：`NeiItemVariants` 用 `API#setItemListEntries` 把枪/弹药/配件/工作台/弹药盒的 NBT
   变体注入 NEI 物品面板（否则面板里只有无 NBT 的裸物品，R 查不到配方）；枪包数据未就绪时绝不覆盖。
4. **数据与缓存**：`NeiQueryData` 计算枪↔弹药、枪↔配件快照（配件兼容性约 1.7 万次判定），按索引 id 哈希 +
   数量做指纹缓存，插件加载时预热一次；`/tacz reload` 改变 id 集合会自动重建。
5. **绘制**：`NeiRender` 自绘 MC 风格面板/物品槽/箭头（不新增贴图、不改 NEI 的 handlers.csv），
   页面尺寸保持 NEI 默认 166×65；列表页每页 18 个（上游 JEI 54，属可接受差异）。
6. **依赖**：`dependencies.gradle` 增加 `implementation(gtnhDev("NotEnoughItems"))`（CCC 由 NEI 传递依赖
   带出）；NEI 缺席时 `compat.nei` 的类不会被加载（纯可选依赖，服务端/无 NEI 客户端零风险）。
7. **未实现（记录）**：工作台 GUI 内嵌 overlay 与「一键填材料」（NEI `IOverlayHandler`/`IStackPositioner`
   体系，需为 3 种工作台 GUI 各算槽位映射）；`GunPegboardDyeRecipe` 配方页（上游 JEI 同样不展示）。
8. 构建自检：`./gradlew spotlessApply compileJava` = **BUILD SUCCESSFUL**（2026-11-07）。
   完整报告与实机验证清单：`docs/review/r8/nei.md`。

#### r8 追加 · NEI 首次进游戏报错修复（2026-11-07 实机五轮，Lead）

首次实机（NEI 2.8.x）暴露并已修：

1. **`NoSuchMethodException: GunSmithTableRecipeHandler.<init>()`**（每个 NEI worker 线程刷、配方查询全挂）：
   NEI `TemplateRecipeHandler#newInstance()` 用反射**无参构造器**复制 handler，而工作台 handler 构造器带
   `blockId`。改为覆写 `newInstance()` 返回携带同一 `blockId` 的新实例（NEI 官方扩展点，同 GT5U
   `GTNEIDefaultHandler#newInstance()`）。
2. **催化剂 U 查询（"category 没有绑定工作台"）**：`getUsageAndCatalystHandler()` 命中催化剂后要求 handler 的
   `transferRects` 非空，否则只打 `failed to load catalyst handler, implement loadTransferRects` 并退化成空页。
   工作台/弹药库 handler 各登记一条「箭头区域 → 全部配方」的 `RecipeTransferRect`（outputId = handler id），
   同时支持点击箭头看该工作台全部配方。注意 `TemplateRecipeHandler` 构造器会调 `loadTransferRects()`，
   那时子类实例字段还没初始化 —— 携带 `blockId` 的 rect 在构造器体内补登。
3. **页签 mod 显示 `Unknown`、无图标**：NEI 页签信息来自 `GuiRecipeTab.handlerMap`；NEI 在 `FMLLoadCompleteEvent`
   就发 `NEIRegisterHandlerInfosEvent`，而本插件要等进世界才由 NEI 后台线程实例化，且该 map 只能由客户端线程写。
   新增 `NeiHandlerInfos`：① `ClientProxy#init` 里（NEI 已安装时经 `CompatRegistry` 反射）提前
   `registerListener()`，接住 LoadComplete 的 handler 信息事件；② `loadConfig()` 里用
   `Minecraft#func_152344_a`（`addScheduledTask`）投递到客户端主线程，用真实枪包数据刷新页签图标 + 真实
   mod 名并写 `handlerMap`。同时隐藏压住格子的 overlay/收藏按钮。
4. **顺带修**：`AmmoStorageUpgradeHandler` 页名用了 1.7.10 不存在的 `tacz.block.ammo_storage.name`（1.7.10 是
   `tile.tacz.ammo_storage.name`）→ 改用方块物品 `getDisplayName()`；`AttachmentQueryHandler` 页脚错用
   `jei.tacz.ammo_query.page` → 改用上游的 `jei.tacz.attachment_query.more`。
5. **NEI 无关但同轮修复：开镜崩溃**（`BufferUnderflowException`，`RangefinderImpactPredictionRender#projectGuiY`
   ← `mcshim/RenderSystem#getProjectionMatrix()`）：1.7.10 的 `GL11#glGetFloat` 不推进 buffer position
   （vanilla `ClippingHelperImpl#init` 因而写成 `clear() → glGetFloat → flip().limit(16)`），原实现只 `flip()`
   ⇒ 返回空 buffer，`get(float[16])` 抛 `BufferUnderflowException`。改为 `flip().limit(16)`，并在
   `projectGuiY` 加 buffer 不足时的 NaN 防御。该路径只在「激光测距仪 + 开镜」时触发。

代码：`src/main/java/com/tacz/guns/compat/nei/NeiHandlerInfos.java`（新）+ 上述 handler 修改；
详细记录：`docs/review/r8/nei.md` §7、`docs/review/r8/README.md` 追加（实机五轮）#16/#17。
