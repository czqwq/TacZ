# r5-D · 数据加载 / 注册接线 / 网络对称性 / 客户端 GUI-HUD 死代码审查

> Owner：`r5-data-wiring`（共享任务板 `task-4`）
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）
> 1.7.10 准绳：`build/rfg/minecraft-src/java/**`
> 纪律：**未运行任何 gradle 任务**；检索只用 IDEA MCP / read / grep；改动文件的 `lint_files(errorsOnly)` = **0 error**。
> 历史报告已登记项**不重复报**，只在 §5「已修复核」写一行。

---

## 1. 结论速览

| 链路 | 结论 | 关键证据 |
|---|---|---|
| **客户端注册/接线** | **闭合**。`ClientSetupEvent.onClientSetup()` 19 个 step 全部有真实实现（1 个是**有据的 no-op**）；6 个自带 `INSTANCE` 的处理器 + 3 组反射总线清单（7 FML / 11 Forge / 3 BOTH）全部在 `ClientProxy#init` 真调；3 处「common→client」钩子（粒子/命令转换/命令重载）全部注入 | `ClientProxy.java:181/187-206/212/230/241/255`、`ClientSetupEvent.java:125-202` |
| **键位** | **闭合**。12 个 `KeyBinding` 全部 `ClientRegistry.registerKeyBinding`；11 个走 `GunInputHandler` 按下沿分发、`ShootKey` 另由自身 tick 轮询（连发）。默认键码与 1.7.10 原版**无冲突**（唯一撞车 -100/-99 已由 `detachMouseBindingsFromVanillaKeyBindingHash` 摘出静态 hash） | `GunInputHandler.java:114-129/170-199`、Aim/Crawl/Reload/Shoot/… |
| **网络（43+2 包）** | **闭合且对称**。45 个 `read/write` 逐字段核对，**45/45 完全对称**；主 channel 42 个 discriminator 与上游 `NetworkHandler.java:40-127` **顺序逐位一致**（错位=串包的根因不存在）；43 号为移植特有 `ServerMessageSyncConfig`。45 个 `handle` 全在正确侧 + 全部 `NetworkTaskQueue.enqueue`（唯 2 个例外均为纯日志/上游同形态） | §3 全表、`NetworkHandler.java:112-362` |
| **资源加载** | **闭合**。枪包 8 个 `data/<ns>/` 目录，7 个有 reader（`advancements` = 1.7.10 无 advancement 体系，已登记降级）；jar 根 `data/tacz/` 5 个目录全部有 1.7.10 承载物。**遗留 1 条**：`recipes/misc/blood_strike_1.json` 仍无人加载（其产物是 1.20 painting 变体，1.7.10 无法表达） | §4 #4/#5 |
| **客户端资源索引** | **闭合**。7 类 index（gun/ammo/attachment/attachment_variant/slot_adapter/block）+ display/model/animation/script/gltf/packInfo 全部有 loader→caller；`ClientAssetsManager` 11 个 getter 全部有 null 护栏（r1 P1-2 复核对齐） | `ClientAssetsManager.java:139-214/228-314`、`ClientIndexManager.java` |
| **HUD / GUI** | **闭合**。5 个 HUD 层的「状态源」逐个可追（`KillAmountOverlay` 有 `markTimestamp`+`resetTimestamp` 生产者；`HeatBar/GunHud/InteractKeyText` 每帧读活状态；`DisplayCaseLockHint` 有 tick 注册）；无「画了但不更新」 | §2 表尾、`ClientHitMark.java:109`、`ClientGunTimeResetEvent.java:73` |
| **音效** | **闭合**。display 的 `sounds: {"shoot":"tacz:ak47/ak47_shoot"}` → `FileToIdConverter("tacz_sounds",".ogg")` → `assets/tacz/tacz_sounds/tacz/ak47/ak47_shoot.ogg`（实测文件在）；`sounds.json` 只有 `gun`/`target_block_hit` 2 个 key，正好覆盖「模板事件 + 回退」两条分支 | `SoundPlayManager.java:88/193-232`、`assets/tacz/sounds.json` |
| **compat（13 个骨架）** | **闭合**。4 个由 `CompatRegistry` 反射调用（ClothConfig/Oculus/Angelica/CarryOn），3 个由 `ClientSetupEvent` 直调（ShoulderSurfing/Controllable/AR）+1 个本轮补上（PlayerAnimator），4 个占位或上游本就 0 引用，1 个是接口。全部反射式、缺失静默 | `CompatRegistry.java:50-70`、`ClientSetupEvent.java:181-190` |

**最严重的四条（按级别）**

1. **P2 空注册（已修）**：`ClientProxy` 的 `FML_BUS_CLASSES` 里有 `"com.tacz.guns.client.sound.SoundPlayManager"`，但该类**没有任何 `@SubscribeEvent` 实例方法**（`onClientTick` 是 `static`，真正的处理器是内部类 `ClientTickHandler`）→ `EventBus#register` 走完全部方法、一个监听器都注册不到（`EventBus.java:60-95`）。后果：音效清理 tick 只能靠「首次播放时自注册」兜底。
2. **P3 注释与事实相反（已修，跨域 1 条未修）**：`client/init/ModContainerScreen` 的 javadoc 写着「`inventory/GuiHandler#getClientGuiElement` 当前返回 `null`…工作台/弹药箱界面打开时客户端会拿到 null（不会崩，但没界面）」——实际早已实现（`inventory/GuiHandler.java:116-138` 返回两个 Screen）。同理 `init/ModRecipe.java:311` 声称 `misc/blood_strike_1.json`「在磁盘上并不存在」，**该文件实际存在**（跨域，未改）。
3. **P3 漏调用（已修）**：`compat/playeranimator/PlayerAnimatorCompat#init()` **0 调用点**，而类 javadoc 明确写「调用点：…`client/init/ClientSetupEvent`」。
4. **P2 数据遗留（未修，有据）**：jar 根 `data/tacz/recipes/misc/blood_strike_1.json` 无人加载（r4-02 §5.1 登记，本轮确认现状未变）。**修它也不会恢复上游语义**：产物是 `minecraft:painting` + `EntityTag.variant = tacz:blood_strike_1`，而 1.7.10 的 `ItemPainting` 只有 damage 0..25 的固定画作索引、无变体注册表（`init/ModPainting` 已作为降级空实现登记）。

---

## 2. 接线矩阵

### 2.1 `client/init` + `ClientProxy`（重点）

| # | 注册项 | 注册点 | 消费者 / 驱动 | 结论 |
|---:|---|---|---|---|
| 1 | `ThirdPersonManager.registerDefault` | `ClientSetupEvent.java:125` | `InnerThirdPersonManager` 读硬编码第三人称动画表 | 闭合 |
| 2 | `GunPegboardColors.setup` | `ClientSetupEvent.java:128` | 挂板方块/物品染色 | 闭合 |
| 3 | `ModContainerScreen.clientSetup` | `ClientSetupEvent.java:131` | **有据的 no-op**：1.7.10 的落点是 `inventory/GuiHandler#getClientGuiElement`（已实现，`GuiHandler.java:116-138`） | 闭合（本轮**修注释**） |
| 4 | `ModEntitiesRender.register`（TESR + 实体渲染器） | `ClientSetupEvent.java:134` → `ModEntitiesRender.java:52-63` | 5 个 TESR（`GunSmithTable/Target/Statue/GunPegboard/GunDisplayCase`）+ 2 个实体渲染器（`EntityKineticBullet/TargetMinecart`） | 闭合 |
| 5 | `IItemRenderer` 注册（7 个 Item 实例） | `ClientSetupEvent.java:140` → `:259-274` | `MinecraftForgeClient` + 第一人称分派链 `FirstPersonRenderHandler.registerRenderer`（`:306-309`，不登记则第一人称枪不渲染） | 闭合 |
| 6 | `ItemClientHooks.itemNameProvider` | `ClientSetupEvent.java:360` | `ItemClientHooks.getItemName` → 弹药/配件/工作台物品名 | 闭合 |
| 7 | `LaserColorUtil.setClientDefaultColorResolver` | `ClientSetupEvent.java:158-161` | `HSVSliderGroup` 激光滑条初值 | 闭合 |
| 8 | `GunPackLoader.injectAllGunPackLanguages` | `ClientSetupEvent.java:172`（另有 `ClientProxy.java:287` 的 `injectGunPackLanguage(locale)` 单语种兜底） | `LanguageRegistry#injectLanguage` → 原版 `LanguageManager#mergeLanguageTable` | 闭合（**两个入口都被调**，非空转） |
| 9 | `ParticleFactoryRegistry.register` | `ClientSetupEvent.java:175` → `ParticleFactoryRegistry.java:40-42` | **有据的 no-op**：1.7.10 无 `ParticleType` 注册表，粒子由 `client/particle/**` 直接 `addEffect` | 闭合 |
| 10 | `GunInputHandler.registerKeyMappings`（12 键 + `GunTime` 闸门） | `ClientSetupEvent.java:178` | `GameSettings#keyBindings`（控制界面可改键）+ `GunInputHandler.onClientTick` | 闭合 |
| 11 | `ShoulderSurfingCompat.init` / `ControllableCompat.init` / `ARCompat.init` | `ClientSetupEvent.java:181/184/187` | 各自反射探测 | 闭合 |
| 12 | **`PlayerAnimatorCompat.init`** | `ClientSetupEvent.java:190` | 反射探测；**本轮新增**（此前 0 调用点） | **本轮修** |
| 13 | `ScopeLensRenderer.init` / `ScopeThermalRenderer.init` | `ClientSetupEvent.java:195/196` | 着色器/FBO 编译；热成像渲染由 `ClientSetupEvent#onRenderWorldLast` 每帧驱动 | 闭合 |
| 14 | 5 个 HUD 层 + `DisplayCaseLockHint` 的 tick | `ClientSetupEvent.java:443-464`（`RenderGameOverlayEvent.Post` 的 ALL / CROSSHAIRS 两档） | `onRenderOverlay`（`:524`）；`lockHint` 另注册到 FML 总线（`:461-463`） | 闭合（r3 P3 复核在） |
| 15 | `ClientSetupEvent.INSTANCE` → Forge 总线 | `ClientSetupEvent.java:202` | `RenderWorldLastEvent` → `ScopeThermalRenderer.render()`（`:516-518`） | 闭合 |
| 16 | `ClientTickHelper.INSTANCE` | `ClientProxy.java:187` | 客户端时钟推进 | 闭合 |
| 17 | `GunInputHandler/AimKey/CrawlKey/ReloadKey/ShootKey .INSTANCE` | `ClientProxy.java:190-203` | 各自 `ClientTickEvent` | 闭合 |
| 18 | **`SoundPlayManager.TICK_HANDLER`** | `ClientProxy.java:204-206` | 音效清理 tick；**本轮新增**（原为空注册，见 §4 #1） | **本轮修** |
| 19 | `ScreenItemVboContext`（实例） | `ClientProxy.java:212` | 推 `RenderDistance.setScreenDrawActive`（r3 P2 复核在） | 闭合 |
| 20 | `FML_BUS_CLASSES` 7 个 / `FORGE_BUS_CLASSES` 11 个 / `BOTH_BUS_CLASSES` 3 个 | `ClientProxy.java:214-221` | 21 个事件类，逐个有 `@SubscribeEvent` | 闭合 |
| 21 | `ReloadCommand.setClientReloader` | `ClientProxy.java:230` | `/tacz reload` 客户端分支 | 闭合 |
| 22 | `EntityBulletClientHooks.ammoParticleSpawner` | `ClientProxy.java:241` | 子弹粒子 | 闭合 |
| 23 | `ConvertCommand.setConverter` | `ClientProxy.java:255` | `/tacz convert` | 闭合 |
| 24 | `ClientGunPackReloader.init()`（重载监听器 + FML 总线自注册） | `ClientProxy.java:277` → `ClientGunPackReloader.java:63-93` | `IResourceManagerReloadListener` 每次原版资源重载 → `reloadAll()`；`ParticleDefinitionReloader` 追加在列表末尾（`:74`） | 闭合 |
| 25 | `GunPackResourcePack.ensureInstalled()` | `ClientProxy.java:172`（preInit，必须早于 stitch） | 枪包贴图/ogg 挂进原版资源包体系 | 闭合 |
| 26 | `MuzzleFlashRender.isSelf` / `ShellRender.isSelf` | `GunItemRendererWrapper.java:274-275/316-317`（第一人称，`try/finally` 复位）+ `mixin/client/ItemInHandLayerMixin.java:180-192`（第三人称 self）；mixin 已在 `mixins.tacz.json:18` 注册 | `MuzzleFlashRender.java:193`、`ShellRender.java:188` 的 `if (!isSelf) return` | 闭合 |
| 27 | `ClientItemRendererRegistrar` | **该类不存在**（只有 `item/ItemClientHooks.java:22` 的 javadoc 引用），实际注入点是 `ClientSetupEvent:258` | — | 注释漂移（跨域 `item/**`，§5 未修清单 #2） |

### 2.2 HUD「画了但不更新 / 更新但不画」逐一核对

| HUD | 每帧数据源 | 状态更新驱动 | 结论 |
|---|---|---|---|
| `GunHudOverlay` | 每帧读 `IGun.getCurrentAmmoCount` / `ClientAmmoCount` | 自带 1s 缓存（`checkAmmoTimestamp`） | 闭合 |
| `HeatBarOverlay` | 每帧读 `iGun.getHeatAmount` / `isOverheatLocked` | 无独立状态（`heatScale` 自平滑） | 闭合 |
| `KillAmountOverlay` | `killTimestamp` / `killAmount` | 生产者 `ClientHitMark.java:109 markTimestamp()`；重置 `ClientGunTimeResetEvent.java:73 resetTimestamp()` | 闭合 |
| `InteractKeyTextOverlay` | 每帧 `objectMouseOver` | 无状态 | 闭合 |
| `DisplayCaseLockHintOverlay` | `ticksRemaining` / `expireAt` | `show()` 置位（方块交互）、`onClientTick` 递减（FML 总线，`ClientSetupEvent:461`）、墙钟兜底 | 闭合（本轮**修注释**，原注释仍写「本类从未被注册到任何事件总线」） |

### 2.3 `compat/**`（13 个骨架）

| 类 | 调用方 | 缺失库行为 | 结论 |
|---|---|---|---|
| `cloth/MenuIntegration` | `CompatRegistry.java:52-54`（`checkModLoad(cloth_config)` + 反射） | 静默 | 闭合 |
| `oculus/OculusCompat` | `CompatRegistry.java:64/67`（oculus + angelica 各一次） | 静默；`LOADED=false` 时各方法走降级分支 | 闭合 |
| `carryon/BlackList` | `CompatRegistry.java:69` | 静默 | 闭合 |
| `ar/ARCompat` | `ClientSetupEvent.java:187` | 静默 | 闭合 |
| `controllable/ControllableCompat` | `ClientSetupEvent.java:184` + `ShootKey.java:80` | 静默 | 闭合 |
| `shouldersurfing/ShoulderSurfingCompat` | `ClientSetupEvent.java:181` | 静默 | 闭合 |
| `playeranimator/PlayerAnimatorCompat` | `ClientSetupEvent.java:190`（本轮补）+ `InnerThirdPersonManager.java:25/34`、`PlayerModelRenderState.java:70` | 静默（`LOADED` 常量 + 全方法 stub） | **本轮修** |
| `oculus/IUnflushableWrapper` | `OculusCompat#endBatch` 流程用（接口，非骨架入口） | — | 闭合（不适用） |
| `ar/BedrockPartLightingMode` | 上游仅 AR mixin 用；本移植无 AR mixin | 静默 | 有据降级 |
| `jei/GunModPlugin` / `kubejs/TimelessKubeJSPlugin` | 1.7.10 无 JEI / KubeJS；占位保留（AGENTS 决策 2） | 静默 | 有据占位 |
| `optifine/OptifineCompat` | 上游全仓 0 引用（r3 B-1 已登记） | 静默 | 有据降级 |
| `playeranimator/AnimationName` | `PlayerAnimatorCompat` 使用的动画名常量 | — | 闭合 |

`CompatRegistry.register()` 由 `CommonProxy.java:158` 调用；`invokeOptional` 对 `ClassNotFoundException` 返回 `false` 不告警、其它 `Throwable` 只 `warn`（`CompatRegistry.java:99-112`）→ **不抛异常**。

---

## 3. 网络包对称表

字段顺序：`W` = `write(PacketBuffer)` 顺序，`R` = `read(PacketBuffer)` 顺序（经 `MessageBase#fromBytes/toBytes`）。全部 45 条 **W 与 R 逐字段同序同类型**。

| # | discriminator | 包 | 字段（W 顺序） | 对称 | 结论 |
|---:|---:|---|---|---|---|
| 1 | 1 | `ClientMessagePlayerShoot` | long, float | ✓ | 对称 |
| 2 | 2 | `ClientMessagePlayerReloadGun` | （无字段） | ✓ | 对称 |
| 3 | 3 | `ClientMessagePlayerCancelReload` | （无字段） | ✓ | 对称 |
| 4 | 4 | `ClientMessagePlayerFireSelect` | （无字段） | ✓ | 对称 |
| 5 | 5 | `ClientMessagePlayerAim` | bool | ✓ | 对称 |
| 6 | 6 | `ClientMessagePlayerCrawl` | bool | ✓ | 对称 |
| 7 | 7 | `ClientMessagePlayerDrawGun` | （无字段） | ✓ | 对称 |
| 8 | 8 | `ServerMessageSound` | varInt, RL, RL, utf, float, float, int | ✓ | 对称 |
| 9 | 9 | `ServerMessageBulletHitBlock` | 3×double, enum, blockPos(3×int), 3×utf, bool | ✓ | 对称（`null` → `""` 单向归一，消费端 `new ResourceLocation("")` 被 `catch` 兜住返回默认 true，见 §5 未修 #4） |
| 10 | 10 | `ClientMessageCraft` | RL, varInt | ✓ | 对称 |
| 11 | 11 | `ServerMessageCraft` | varInt | ✓ | 对称 |
| 12 | 12 | `ClientMessagePlayerZoom` | （无字段） | ✓ | 对称 |
| 13 | 13 | `ClientMessageRefitGun` | int, int, enum | ✓ | 对称 |
| 14 | 14 | `ClientMessageInstallVirtualAttachment` | int, enum, RL | ✓ | 对称 |
| 15 | 15 | `ServerMessageRefreshRefitScreen` | （无字段） | ✓ | 对称 |
| 16 | 16 | `ClientMessageUnloadAttachment` | int, enum | ✓ | 对称 |
| 17 | 17 | `ServerMessageSwapItem` | （无字段） | ✓ | 对称（1.7.10 无双持，客户端 hook no-op，有据） |
| 18 | 18 | `ClientMessagePlayerBoltGun` | （无字段） | ✓ | 对称 |
| 19 | 19 | `ServerMessageLevelUp` | itemStack, int | ✓ | 对称（`handle` 是 TODO no-op；**上游亦无生产者**，保真） |
| 20 | 20 | `ServerMessageGunHurt` | 3×int, 2×RL, float, bool, float | ✓ | 对称 |
| 21 | 21 | `ServerMessageGunKill` | 3×int, 2×RL, float, bool, float | ✓ | 对称 |
| 22 | 22 | `ServerMessageUpdateEntityData` | varInt, varInt(size), N×`DataEntry` | ✓ | 对称（带 `size` 上界防 OOM） |
| 23 | 23 | `ServerMessageSyncGunPack` | UUID, 3×varInt, byteArray | ✓ | 对称；`readByteArray(CHUNK_SIZE)` 与 `GunPackSync.compressAndSplit` 的分片尺寸（256 KiB）一致 |
| 24 | 24 | `ClientMessagePlayerMelee` | （无字段） | ✓ | 对称 |
| 25 | 25 | `ServerMessageGunDraw` | varInt, item, item | ✓ | 对称 |
| 26 | 26 | `ServerMessageGunFire` | varInt, item（`AbstractShooterGunEventMessage`） | ✓ | 对称 |
| 27 | 27 | `ServerMessageGunFireSelect` | 同上 | ✓ | 对称 |
| 28 | 28 | `ServerMessageGunMelee` | 同上 | ✓ | 对称 |
| 29 | 29 | `ServerMessageGunReload` | 同上 | ✓ | 对称 |
| 30 | 30 | `ServerMessageGunShoot` | 同上 | ✓ | 对称 |
| 31 | 31 | `ServerMessageSyncBaseTimestamp` | （无字段） | ✓ | 对称 |
| 32 | 32 | `ClientMessageSyncBaseTimestamp` | （无字段） | ✓ | 对称 |
| 33 | 33 | `ClientMessageLaserColor` | map(enum→int), bool, int, int | ✓ | 对称（`readMap` 返回 `HashMap` 不保序，但写侧是 `EnumMap` 且读后重建 `EnumMap`，无语义差） |
| 34 | 34 | `ClientMessageAmmoStorageInteract` | blockPos(3×int) | ✓ | 对称 |
| 35 | 35 | `ClientMessageSetAttachmentVariant` | int, enum, bool(+可选 RL) | ✓ | 对称（可选字段用 bool 前缀，写法两端一致） |
| 36 | 36 | `ClientMessageSetSlotAdapter` | int, enum, bool(+可选 RL) | ✓ | 对称 |
| 37 | 37 | `ClientMessageAmmoStorageClick` | varInt, varInt, enum | ✓ | 对称 |
| 38 | 38 | `ServerMessageAmmoStorageContents` | varInt, varInt, varLong, varInt(size), N×(varInt, item, varLong, varLong) | ✓ | 对称 |
| 39 | 39 | `ServerMessageGunFinishReload` | varInt, item | ✓ | 对称 |
| 40 | 40 | `ServerMessageGunFirePost` | varInt, item | ✓ | 对称 |
| 41 | 41 | `ClientMessagePlayerUnloadGun` | varInt, RL | ✓ | 对称 |
| 42 | 42 | `ServerMessageGunUnload` | RL, RL | ✓ | 对称 |
| **43** | **43** | `ServerMessageSyncConfig` | 7×list(utf) + 6×bool + 2×int + 6×double | ✓ | 对称；**移植特有**（1.7.10 无 SERVER 配置自动同步），`null` 列表写 `varInt(0)`、读回空列表 |
| H1 | 1 | `Acknowledge`（handshake channel） | （无字段） | ✓ | 对称 |
| H2 | 2 | `ServerMessageSyncedEntityDataMapping`（handshake channel） | int(size), N×(RL, RL, varInt) | ✓ | 对称 |

**discriminator 与上游一致性**：上游 `NetworkHandler.java:40-127` 的 42 个 `registerMessage(ID_COUNT.getAndIncrement(), …)` 顺序与上表 1..42 **逐位相同**（`ID_COUNT` 从 0 开始、1.7.10 侧从 1 开始，但因两侧同一套代码、且 simpleimpl 的 discriminator 只要求自洽，无可观察差异）。handshake 上游顺序也是 `Acknowledge → SyncedEntityDataMapping`（`NetworkHandler.java:129-130`），与移植一致。

**`handle` 落点**：43 个主 channel 包中，41 个 `NetworkTaskQueue.enqueue(...)`（服务端/客户端线程执行）；例外 2 个是 `Acknowledge`（只打 debug 日志）与 `ServerMessageLevelUp`（TODO no-op），二者都不碰世界状态。`ctx.side` 检查 **45/45 全有**。

---

## 4. 发现表

| # | 级别 | 现象 | 根因 | 证据（本工程 ↔ 上游/平台） | 状态 | 修法 |
|---:|---|---|---|---|---|---|
| 1 | **P2** | `SoundPlayManager` 的客户端 tick **空注册**：音效并发追踪记录（`TRACKED_GUN_SOUNDS`）的清理只能等「首次播放音效后自注册」才启动 | `ClientProxy.FML_BUS_CLASSES` 按**类名**注册 `SoundPlayManager`，但该类只有 `static onClientTick`；唯一的 `@SubscribeEvent` 在内部类 `ClientTickHandler` 上，而 `EventBus#register` 只扫 `target.getClass()` 的实例方法 → 一个监听器都注册不到 | 本工程 `ClientProxy.java:82-96`（改前含该字符串）、`SoundPlayManager.java:615-628/650/668-680`；平台 `EventBus.java:45-95`（`listeners.containsKey(target)` + `for (Method m : getMethods())` + `@SubscribeEvent` 判定） | **已修** | 从字符串表移除该类，改为 `FMLCommonHandler.instance().bus().register(SoundPlayManager.TICK_HANDLER)`（`ClientProxy.java:204-206`）。幂等已核实：`EventBus.register` 若 `listeners` 已含该实例直接 return（`EventBus.java:47-50`），且 `ensureTickHandlerRegistered()` 首次播放时会因该 key 已存在而跳过 |
| 2 | **P3** | `ModContainerScreen` javadoc 断言「`GuiHandler#getClientGuiElement` 返回 null → 工作台/弹药箱打开时没界面」，会让下一轮把它当 P0 重做 | `GuiHandler` 的客户端分支后来已实现，但 `client/init` 的锚点注释没跟着改 | 本工程 `client/init/ModContainerScreen.java:28-33`（改前）↔ `inventory/GuiHandler.java:116-138`（`GUN_SMITH_TABLE → GunSmithTableScreen`、`AMMO_STORAGE → AmmoStorageScreen`） | **已修** | 注释改为「真正的分发点在 `inventory/GuiHandler.java:116`，上游 `MenuScreens.register` 的语义由那里承担」 |
| 3 | **P3** | `PlayerAnimatorCompat.init()` **0 调用点**，而类 javadoc 写着「调用点：…`client/init/ClientSetupEvent`」 | 该 compat 骨架的注册步骤在 `ClientSetupEvent` 里漏了（AR/Controllable/ShoulderSurfing 三个都在，唯独缺它） | 本工程 `compat/playeranimator/PlayerAnimatorCompat.java:46-48/66-73`（`grep PlayerAnimatorCompat.` 只命中 `InnerThirdPersonManager:25/34`、`PlayerModelRenderState:70`，均不含 `init`） | **已修** | `ClientSetupEvent.java:190` 补 `step("PlayerAnimatorCompat.init", …::init)`（`step` 自带 try/catch，缺失库不影响其它步骤） |
| 4 | **P2（跨域）** | jar 根 `data/tacz/recipes/misc/blood_strike_1.json`（8 木棍 + 1 羊毛 → 血染画）**无人加载** | `readPackData("recipes")` 只扫**枪包来源**（`<gamedir>/tacz/**`），jar 根 `data/` 不是包来源；`ModRecipe` 的 `registerCustomRecipes()` 也没补这一条 | 本工程 `resource/GunPackLoader.java:206-222`、`init/ModRecipe.java:300-313`（注释声称该 json「在磁盘上并不存在」）、`src/main/resources/data/tacz/recipes/misc/blood_strike_1.json`（**实际存在**）；来源：`docs/review/r4/02-recipes.md` §5.1 | **未修（本轮确认现状未变）** | **不建议按「加第 4 类包来源」修**：产物是 `minecraft:painting` + `EntityTag.variant = tacz:blood_strike_1`，1.7.10 的 `ItemPainting` 只有 damage 0..25 的固定画作索引、无变体注册表（`init/ModPainting` 已是降级空实现）。若之后要它「至少能出现在工作台列表里」，最小改法是在 `init/ModRecipe#registerCustomRecipes`（**不在本任务写作用域**）补一条 `GunSmithTableRecipe`，产物给 `new ItemStack(Items.painting)`；同时**必须改掉 `ModRecipe.java:311` 的事实错误注释** |
| 5 | P3 | `readEnum` 相关注释与实现不符（r4-C-5 只修了 3/5 处，剩 3 处仍写「越界返回 null」） | `mcshim.PacketBuffer#readEnum` 早已改成**抛 `IllegalArgumentException`**（`PacketBuffer.java:397-405`，注释还写着「不要改回返回 null」），调用点注释没跟上 | 本工程 `ClientMessageAmmoStorageClick.java:78`、`ClientMessageLaserColor.java:91`、`ServerMessageBulletHitBlock.java:158`（改前）；已修好的 3 处：`ClientMessageInstallVirtualAttachment:91`、`ClientMessageSetAttachmentVariant:97`、`ClientMessageUnloadAttachment:81` | **已修** | 3 处注释统一压成一行「readEnum 越界抛 IAE（1.20 语义）；此处 null 检查为双保险」。功能零变化：那些 `== null` 分支现在是纯双保险（不可达但无害） |
| 6 | P3 | `DisplayCaseLockHintOverlay` 的 `expireAt` / `onClientTick` 注释仍写「**本类从未被注册到任何事件总线**…`ticksRemaining` 一直是 40」 | r3 已把注册补进 `ClientSetupEvent#registerOverlays`，注释没回收 | 本工程 `client/gui/overlay/DisplayCaseLockHintOverlay.java:43-52/74-81`（改前）↔ `client/init/ClientSetupEvent.java:461-463`（实际注册） | **已修** | 两段注释改成「已注册到 FML 总线，墙钟是与事件无关的兜底」 |
| 7 | P3 | `SoundPlayManager#ensureTickHandlerRegistered` 注释写「注册点必须写在 `ClientProxy`（**属于其它工作流，尚未移植**）」 | 同上，`ClientProxy` 早已存在且现在显式注册了 `TICK_HANDLER` | `SoundPlayManager.java:652-661`（改前）↔ `ClientProxy.java:204-206` | **已修** | 注释改为「双保险：`ClientProxy#init` 已注册，本方法只是兜底」 |
| 8 | P3（跨域） | `item/ItemClientHooks` javadoc 写「client 侧注入点：`client/init/ClientItemRendererRegistrar#registerAll()`」，**该类不存在** | 实现时改成了 `ClientSetupEvent#registerItemRenderers`，注释没改 | `item/ItemClientHooks.java:21-22` ↔ `client/init/ClientSetupEvent.java:256-274` | **未修（`item/**` 不在本任务写作用域）** | 把 `ItemClientHooks.java:21-22` 的类名换成 `com.tacz.guns.client.init.ClientSetupEvent#registerItemRenderers` |
| 9 | 记录 | `ClientMessageLaserColor` 的 `colorMap` 是 `EnumMap`，写侧 `writeMap` 遍历顺序 = 枚举序；读侧 `readMap` 返回 `HashMap`（`PacketBuffer.java:422-437` 明确不保序），随后逐项 `put` 回 `EnumMap` | 平台差异（1.20 `readMap` 也是 HashMap） | `ClientMessageLaserColor.java:80-98`、`PacketBuffer.java:411-437` | 无需修 | 语义等价：`EnumMap` 的迭代序由枚举决定，与插入序无关 |
| 10 | 记录 | `ServerMessageBulletHitBlock` 写侧把 `null` id 归一成 `""`，读侧不还原成 `null` | 1.7.10 `readUtf` 无法区分「空串」与「未写」，写侧只能靠占位 | `ServerMessageBulletHitBlock.java:87-89/103-105`；消费端 `BulletHoleParticle.java:126-140` 用 `new ResourceLocation("")` → `catch (Exception)` → 返回 `true` | 无需修 | 与上游「无 ammoId 时子弹孔默认开」等价；若改成可区分 null 需改线上格式（跨版本不兼容），收益不足以付这个风险 |

---

## 5. 清单

### 5.1 本轮已修（7 个文件）

| 文件 | 改动 | 级别 |
|---|---|---|
| [`client/ClientProxy.java`](src/main/java/com/tacz/guns/client/ClientProxy.java) | `FML_BUS_CLASSES` 移除空注册的 `SoundPlayManager`；改为显式注册 `SoundPlayManager.TICK_HANDLER`（`ClientProxy.java:204-206`） | P2 |
| [`client/init/ModContainerScreen.java`](src/main/java/com/tacz/guns/client/init/ModContainerScreen.java) | javadoc 改为「客户端界面分发点在 `inventory/GuiHandler.java:116`，已实现」（原注释断言返回 null / 没界面） | P3 |
| [`client/init/ClientSetupEvent.java`](src/main/java/com/tacz/guns/client/init/ClientSetupEvent.java) | ① 补 `step("PlayerAnimatorCompat.init", …)`（`:190`）；② 时序注释 `preInit` → `init`（该 step 由 `ClientProxy#init` 调用） | P3 |
| [`client/gui/overlay/DisplayCaseLockHintOverlay.java`](src/main/java/com/tacz/guns/client/gui/overlay/DisplayCaseLockHintOverlay.java) | 两处「从未注册到事件总线」的过期注释改成「已注册 + 墙钟兜底」 | P3 |
| [`client/sound/SoundPlayManager.java`](src/main/java/com/tacz/guns/client/sound/SoundPlayManager.java) | 「注册点尚未移植」的过期注释改成「`ClientProxy#init` 已注册，本方法兜底」 | P3 |
| [`network/message/ClientMessageAmmoStorageClick.java`](src/main/java/com/tacz/guns/network/message/ClientMessageAmmoStorageClick.java) | `readEnum` 注释纠正（r4-C-5 尾项） | P3 |
| [`network/message/ClientMessageLaserColor.java`](src/main/java/com/tacz/guns/network/message/ClientMessageLaserColor.java) | 同上（3 行→1 行） | P3 |
| [`network/message/ServerMessageBulletHitBlock.java`](src/main/java/com/tacz/guns/network/message/ServerMessageBulletHitBlock.java) | 同上（2 行→1 行） | P3 |

`lint_files(errorsOnly)` 对以上 8 个文件 = **0 error**。

### 5.2 未修（+理由）

| # | 项 | 级别 | 不修理由 |
|---:|---|---|---|
| 1 | jar 根 `data/tacz/recipes/misc/blood_strike_1.json` 无人加载 | P2 | 产物是 1.20 painting **变体**，1.7.10 无变体注册表（`ModPainting` 已是降级空实现）；补加载只会让工作台多出一条产出「无变体原版画」的配方。真正的修点（`init/ModRecipe#registerCustomRecipes`）**不在本任务写作用域** |
| 2 | `item/ItemClientHooks` javadoc 引用不存在的 `ClientItemRendererRegistrar` | P3 | `item/**` 不在本任务写作用域（归属 NBT/物品域） |
| 3 | `init/ModRecipe.java:311` 「该 json 在磁盘上并不存在」事实错误 | P3 | `init/**` 不在本任务写作用域；且与 #1 同一条，应由同一人一并处理 |
| 4 | `ServerMessageBulletHitBlock` 的 `null` → `""` 单向归一 | 记录 | 改成可区分 `null` 要改线上格式（跨 build 不兼容），且现状与上游语义等价（见 §4 #10） |
| 5 | `ServerMessageLevelUp#handle` 是 TODO no-op、`GunLevelUpToast` 0 引用 | 记录 | 上游也没有生产者（升级逻辑上游本就注释掉），非移植回归 |
| 6 | `ALLOW_ATTACHMENT_TAGS` 枚举值 0 引用 | 记录 | 上游同样 0 引用（r3 A4-1 / r2-09 §二 已定案「保真，非缺陷」） |

### 5.3 已修复核（历史项，本轮回代码确认「修复在且正确」）

| 历史项 | 现状 | 证据 |
|---|---|---|
| r2 P0-4 `registerEventHandlers(Class)` 注册不到 | ✅ 已改为实例注册 | `CommonAssetsManager.java:504` |
| r3-05 P1 弹药库明细列表无生产者 | ✅ `ContentSender` 已注入 | `NetworkHandler.java:358-361` |
| r3-05 P2 `RenderDistance.setScreenDrawActive` 0 调用者 | ✅ `ScreenItemVboContext` 已推 | `ClientProxy.java:212` |
| r3-05 P2 `LaserColorUtil` 默认色恒红 | ✅ 解析器已注入 | `ClientSetupEvent.java:158-161` |
| r3-05 P3 `DisplayCaseLockHintOverlay#onClientTick` 未注册 | ✅ 已注册到 FML 总线 | `ClientSetupEvent.java:461-463` |
| r4-C-1 `FlatColorButton` tooltip 在改装界面不画 | ✅ 分支在 | `GunRefitScreen.java` 的 `instanceof FlatColorButton` 分支、`FlatColorButton.java:83-91` |
| r4-C-5 `readEnum` 注释与实现不符 | ⚠ 3/5 已修、3 处仍错 | 本轮补齐（§4 #5） |
| r1 P0-2 `bedrockAnimation` 缺 `ASSETS_ROOT` | ✅ 6 参构造 + ASSETS_ROOT | `ClientAssetsManager.java:200-208` |
| r1 P1-2 display/model getter 无 null 护栏 | ✅ 11 个 getter 全有 | `ClientAssetsManager.java:228-314` |
| r2 语言注入只用 `preInit` 一次 | ✅ 改注入全部语种 | `ClientSetupEvent.java:172` + `GunPackLoader.java:263-303` |
| r2 P2-2 重载监听器时机 | ✅ `postInit` 注册 + 自身重挂 | `ClientProxy.java:277`、`ClientGunPackReloader.java:76-93` |

### 5.4 覆盖清单（诚实）

**逐文件过目（本任务写作用域内的部分）**
- `network/**`：**45/45 消息类 + `MessageBase` + `NetworkHandler` + `NetworkTaskQueue`（读过接口契约）+ `ClientSideHooks` + `SyncConfigSyncManager`（读调用点）**。
- `client/init/**`：5/5 文件全读。
- `client/ClientProxy.java`：全读。
- `client/input/**`：`GunInputHandler` / `ShootKey` / `AimKey` 全读；其余 9 个按键类**只核了默认键码与 `onXxxPress` 的存在性**（未逐行读实现）。
- `client/sound/**`：`SoundPlayManager` 全读；`GunPackResourcePack`/`GunSoundInstance` 只读调用点与 TODO。
- `client/gui/**`：5 个 overlay 全读、`FlatColorButton`/`GunRefitScreen`/`GunSmithTableScreen` 只核注册与 tooltip 分支；`AmmoStorageScreen` 只核发送点。
- `client/tooltip/**`：只核 `TooltipEvent` 的 5 条接线（未逐行读绘制）。
- `compat/**`：13/13 类**核了「有没有调用方 + 缺失库行为」**，未逐行读反射实现（`OculusCompat`/`PlayerAnimatorCompat` 读了入口段）。
- `resource/**`：`GunPackLoader` 全读、`CommonAssetsManager` 读注册管线与网络缓存、`pack/**` 5 文件读契约、各 manager **只核目录名与 `DataType` 生产者-消费者**；`resource/modifier/**`、`resource/filter/**`、`resource/pojo/**` **未读**（r2/09-resource-review 已深度覆盖）。
- `client/resource/**`：`ClientAssetsManager` / `ClientIndexManager` / `ClientGunPackReloader` / `ClientAssetLoadDispatcher` 核了「每个 index 有 loader 且有 caller」与 getter 护栏；**94 个 POJO 未逐字段审计**（r1/02-client-resource 与 r3 已覆盖 gson 兼容与 null 默认值）。

**未覆盖 / 未证**
- 没有任何实机/运行时验证（纪律禁止 gradle，也未 runClient）。
- `client/gameplay/**` 13 文件只做了「发送点 ↔ 消息类」的交叉核对，**未审状态机逻辑**（r2/04-gameplay 已覆盖）。
- `client/resource/**` 的「字段读进 POJO 但 0 处使用」只抽查了 `ExternalModelAssets` / `ClientAssetLoadTask` / `display` 主链路，**未做全字段机械扫描**。

### 5.5 需实机验证项

1. **（P2 修复）音效清理**：连续开火 30s 后停止 → 内存/声音实例不应堆积；日志无 `Unable to play unknown soundEvent`。若 `TICK_HANDLER` 注册抛异常，会看到 `Failed to register client tick handler for gun sound cleanup`。
2. **（P3 修复）Player Animator 探测**：未装库时启动日志**不应**出现 `Detected Player Animator`；装了 1.7.10 版 playeranimator 才出现（且其余动画仍走原版路径）。
3. **（网络）多人服**：进服后应看到 `Prepared gun pack sync: N bytes -> M compressed bytes, P part(s)`，且枪有名字/模型/图标（证明 43 个包的 discriminator 没串、分片收齐）。分片 >1 时尤其要验（用大第三方枪包）。
4. **（HUD）**连杀提示：击杀后出现、约 3s 淡出、切枪后不再显示；`/tacz reload` 或断线重连后不再残留。
5. **（注册）**第一人称枪械、第三人称他人持枪、展示柜/挂板/靶子/雕像/工作台 5 个 TESR 全部可见；展示柜上锁提示 2s 内消失。
6. **（资源）**第三方枪包（zip 与目录两种）放进 `<gamedir>/tacz/` 后：`/tacz reload` → 贴图/音效/物品名全部生效（证明 `GunPackResourcePack` + 语言注入 + `ClientIndexManager.reload` 三段都通）。
