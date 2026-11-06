# r7-input · 客户端输入 / gameplay / 事件 上游等价性审查

> Owner：`r7-input`（共享任务板 `task-19`）
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）
> 1.7.10 准绳：`build/rfg/minecraft-src/java/**`；1.20.1 原版 `tmp/minecraft-src-1.20.1`、Forge `tmp/MinecraftForge-1.20.1`
> 纪律：**未运行任何 gradle 任务**；改动文件 `lint_files(errorsOnly)` = **0 error**。
> 写作用域：`client/input/**`（13）、`client/gameplay/**`（13）、`client/event/**` 剩余 10 个、
> `mixin/client/{MinecraftAttackMixin,MouseHandlerMixin,LocalPlayerMixin}.java`，共 **39 个 Java 文件**；
> `client/event` 其余 10 个文件（fp/crosshair/block/gui 归属）**只做注册差集交叉核对**，语义不重复审。

---

## 一、摘要

| 链路 | 结论 | 关键证据 |
|---|---|---|
| **键位注册 / 默认键 / 冲突** | **闭合**。12 个 `KeyBinding` 全部 `ClientRegistry.registerKeyBinding`，默认键与上游逐一对应（`-100` 左键开火、`-99` 右键瞄准、R/Alt+R、V、Z、G、H、C、O），仅 `ConfigKey`（上游 ALT+T）降级为未绑定（有据）；`-100/-99` 与原版键位静态 hash 的冲突已用反射 detach + `resetKeyBindingArrayAndHash()` 修复，原版左/右键不被顶掉 | `GunInputHandler.java:105-180`、`ClientSetupEvent.java:178`、`build/rfg/.../KeyBinding.java:82-91`、`Minecraft.java:1776-1786` |
| **输入分发** | **等价**。1.7.10 无 `InputEvent`，改为 `ClientTickEvent(START)` 对全部 binding 做「原始 keyCode 按下沿」检测，再按上游 `onPress(key)` 形态只派发 keyCode 匹配者；同一 keyCode 的 V（Melee/Zoom）、R（Reload/Unload）按上游顺序分发、Alt+R 提前 return 阻止穿透 | `GunInputHandler.java:226-282` ↔ 上游 `GunInputHandler.java:45-119` |
| **开镜 / 射击 / 换弹 / 检视 / 切枪 / 近战 / 卸弹 / 拉栓** | **等价**。13 个 `client/gameplay` 类与上游逐行同构（方法级差集为空），仅平台替换 `LocalPlayer→EntityPlayerSP`、`ItemStack.EMPTY→null`、`LogicalSide→Side`、`submitAsync→func_152344_a` 等 | 逐文件表见 §3 |
| **客户端状态清理** | **等价**。`ClientGunTimeResetEvent`（断线）14 项重置逐条同序；死亡/换维度靠「客户端玩家实体被重建」+ `RefreshClonePlayerDataEvent` 搬运 `SyncedEntityData`/`clientBaseTimestamp` + 延迟 10 tick `initialData()` | `ClientGunTimeResetEvent.java:59-83`、`RefreshClonePlayerDataEvent.java:62-108` |
| **client/event 剩余 10 文件** | **等价**，其中 3 处真实偏离本轮已修：`RenderHeadShotAABB`（位置帧错误 + 实体 id 点号）、`PreventsHotbarEvent`（只隐藏快捷栏）、`TickAnimationEvent`（`isMovingSlowly` 漏 crawl） | §4 / §6 |
| **3 个 mixin** | **等价**。`LocalPlayerMixin`（接口实现 + 每 tick 驱动 + sprint 拦截）、`MouseHandlerMixin`（灵敏度 + 趴下俯仰）、`MinecraftAttackMixin`（左键攻击 + **本轮新增右键实体**）语义与上游 mixin/事件一致；历史客户端日志证明三个 mixin 均已成功 apply | `fml-client-latest.log`（2026-11-05）：`Mixing client.LocalPlayerMixin … into EntityPlayerSP`、`Mixing client.MouseHandlerMixin … into Entity`、`Mixing client.MinecraftAttackMixin … into Minecraft`，无 `InjectionError` |
| **事件注册差集（上游 vs 本仓）** | **无缺失**。上游 35 个客户端订阅类全部有等价注册落点；多出 1 项空注册（`FirstPersonRenderEvent`，上游 handler 本就注释掉，无害） | §5 全表 |
| **趴下（crawl）姿态** | **本轮补齐**。mcshim 的 `EntityStateHelper.ENTITY_POSE_OVERRIDES` 是 1.20 `Pose.SWIMMING`/`setForcedPose` 的等价物，但此前 `LocalPlayerCrawl` **从不写它** ⇒ `MouseHandlerMixin` 的趴下俯仰限制、`CameraSetupEvent` 的趴下后坐力系数、`TickAnimationEvent` 的趴下移动动画判定全是死分支。本轮接线后 3 处同时生效 | `LocalPlayerCrawl.java:83-86/122-125`、`MouseHandlerMixin.java:165-177`、`CameraSetupEvent.java:380-383` |

**最严重的两条（已修）**

1. **P2 功能错误（已修）**：`RenderHeadShotAABB` 在 `RenderLivingEvent.Post` 里直接画实体局部坐标 AABB，但 1.7.10 该事件是在 `RendererLivingEntity#doRender` 的 `glPopMatrix()` **之后**投递（`RendererLivingEntity.java:295/297`）⇒ 矩阵停在**相机帧**，爆头调试框会画在相机原点而不是实体头上（上游 1.20 事件 `PoseStack` 停在实体位置帧）。另外实体 id 用 `getEntityString().toLowerCase()` 解析，FML 模组实体 `"tacz.bullet"` 会被解析成 `minecraft:tacz.bullet`，配置永远命中不到。
2. **P2 行为偏离（已修）**：`ClientPreventGunClick` 的 1.7.10 等价物只覆盖 `PlayerInteractEvent`（右键方块/空气），**右键实体分支不投递该事件**（`Minecraft.java:1512-1517` 直接 `interactWithEntitySendPacket`）⇒ 持枪右键村民/马等仍会交互；上游取消的是整条「使用」输入映射（含实体，展示框除外）。

---

## 二、输入模块（`client/input/**`，13 文件）

### 2.1 注册与默认键

| 上游（1.20 `KeyMapping`） | 默认键 / 修饰 | 本仓（1.7.10 `KeyBinding`） | 结论 |
|---|---|---|---|
| `ShootKey.SHOOT_KEY` | 鼠标左键 / NONE | `-100 + 0` | 等价（鼠标键约定 `GameSettings.isKeyDown`：`code<0 → Mouse.isButtonDown(code+100)`） |
| `AimKey.AIM_KEY` | 鼠标右键 / NONE | `-100 + 1` | 等价 |
| `UnloadKey.UNLOAD_KEY` | R / **ALT** | `Keyboard.KEY_R` + 轮询时要求 Alt（`MODIFIER_ALT`） | 等价（1.7.10 无 `KeyModifier`，见 `GunInputHandler.java:62-65/188-206`） |
| `ReloadKey.RELOAD_KEY` | R / NONE | `KEY_R` | 等价 |
| `CrawlKey.CRAWL_KEY` | C | `KEY_C` | 等价 |
| `InspectKey.INSPECT_KEY` | H | `KEY_H` | 等价 |
| `FireSelectKey.FIRE_SELECT_KEY` | G | `KEY_G` | 等价 |
| `MeleeKey.MELEE_KEY` | V | `KEY_V` | 等价 |
| `ZoomKey.ZOOM_KEY` | V | `KEY_V` | 等价（上游同键；同一次按下两个 handler 都收到，见上游 `GunInputHandler.java:80-92` 无 break） |
| `InteractKey.INTERACT_KEY` | O | `KEY_O` | 等价 |
| `RefitKey.REFIT_KEY` | Z | `KEY_Z` | 等价 |
| `ConfigKey.OPEN_CONFIG_KEY` | **ALT+T** | `Keyboard.KEY_NONE`（未绑定） | **有据降级**（见 §7-1） |

注册清单：`GunInputHandler.registerKeyMappings()` 注册 11 个 `PRESS_BINDINGS` + `ShootKey` = 12 个，与上游 `GunInputHandler.java:38-43` 一一对应；由 `ClientSetupEvent#onClientSetup` 的 `step("GunInputHandler.registerKeyMappings")` 调用（`ClientSetupEvent.java:178`）。

**原版键位冲突修复（本仓特有，必须保留）**：1.7.10 `KeyBinding` 构造器把 `keyCode→binding` 写进静态 `IntHashMap`，同 keyCode 后加入者覆盖（`KeyBinding.java:82-91`）；`Minecraft#runTick` 每次按键只更新该 keyCode 对应唯一 binding（鼠标 `Minecraft.java:1781-1785`、键盘 `:1839-1843`）⇒ TACZ 的 `-100/-99` 会顶掉原版攻击/使用键。`GunInputHandler#detachMouseBindingsFromVanillaKeyBindingHash()` 把两个绑定从 `keybindArray` 移除后 `resetKeyBindingArrayAndHash()` 重建 hash（反射按「静态 List 字段」定位，不依赖 MCP/SRG 名），原版键位恢复；TACZ 自己的开火/瞄准走 `GameSettings#isKeyDown` 物理读，不依赖 hash。**历史客户端日志无 `KeyBinding.keybindArray not found` 报错 ⇒ 反射路径实测可用。**

### 2.2 分发语义（`GunInputHandler`）

| 上游 | 本仓 | 结论 |
|---|---|---|
| `InputEvent.Key` / `MouseButton.Pre/.Post`（事件驱动） | `ClientTickEvent(START)` 边缘检测（`GunInputHandler.java:226-250`） | 平台替换；**延迟 ≤1 tick（50ms）**，见 §7-2 |
| `onKeyPress`：RELEASE → `onRelease`；PRESS → `closeRefitScreen` 或 `onPress` | 松开沿不处理（1.7.10 按键状态由原版维护）；按下沿先 `closeRefitScreen` 再 `onPress`（`:226-250`） | 等价 |
| `onPress(key)`：按 `PRESS_BINDINGS` 顺序 `isActiveAndMatches(key)` 命中即执行；`UnloadKey` 后 `return` | 按顺序、keyCode 相同 + 修饰键生效才执行；`UnloadKey` 后 `return`（`:259-282`） | 等价 |
| `closeRefitScreen`：`GunRefitScreen` 打开时按改装键 → `refitScreen.onClose()` | `displayGuiScreen(null)`（触发 `onGuiClosed()`，本仓 `GunRefitScreen#onGuiClosed:746-757` 与上游 `onClose:425-436` 一样发 `ClientMessageLaserColor`） | 等价 |
| 同 keyCode 多 binding（V/R）只派发一次、循环无 break | `LAST_KEY_DOWN` 按 keyCode 记录，同一 keyCode 只产生一次按下沿；`onPress` 再分发全部匹配 binding（`:231-249`） | 等价（r2-R5 的「同 tick 双键」瑕疵在当前实现已随 keyCode 记录方式消除） |

### 2.3 各按键 handler 语义

| 文件 | 上游 ↔ 本仓 | 结论 |
|---|---|---|
| `ShootKey` | 全自动/连发蓄力、`lastTimeShootSuccess`、`stopSprint`、干火音效复位 | 逐行等价（仅 `isSpectator` 删除） |
| `AimKey` | 按住开镜/切换开镜、`HOLD_TO_AIM`、手柄合并、离场取消瞄准 | 逐行等价（仅 `isSpectator` 删除） |
| `CrawlKey` | `HOLD_TO_CRAWL`、`ENABLE_CRAWL(_WITHOUT_GUN)`、骑乘判定 | 等价（`isPassenger→isRiding`、`isSpectator` 删除） |
| `ReloadKey` | 手动换弹 + 空仓自动换弹（每 5 tick、`autoReloadSuppressed`、OPEN_BOLT 判空） | 逐行等价（`PlayerTickEvent.side==CLIENT` 与上游 `LogicalSide.CLIENT` 同义） |
| `InspectKey`/`FireSelectKey`/`MeleeKey`/`ZoomKey`/`UnloadKey`/`RefitKey` | 单行 handler，全部与上游同构 | 等价（`RefitKey` 的 `isCharging`/附件锁判定一致） |
| `InteractKey` | `objectMouseOver` 分派方块/实体；展示柜提示、弹药库发包、`canInteractBlock/Entity` 后「使用」 | 等价；`mc.startUseItem()` 在 1.7.10 无公开入口，复刻为 `PlayerControllerMP#onPlayerRightClick` / `#interactWithEntitySendPacket`（与 `Minecraft#func_147121_ag` 内分支相同），见 §7-3 |
| `ConfigKey` | Cloth Config 界面 / 下载提示 | 有据降级（默认键未绑定 + 1.7.10 无 Cloth Config，`MenuIntegration.LOADED` 恒 false） |

---

## 三、客户端 gameplay（`client/gameplay/**`，13 文件）

方法级机械差集（上游 `public/protected` 方法名 ↔ 本仓）= **空**；以下为逐文件语义核对结论。

| 文件 | 上游 ↔ 本仓要点 | 结论 |
|---|---|---|
| `LocalPlayerDataHolder` | 全部 public 字段名/语义逐字一致（Mixin 与 `LocalPlayerXxx` 直接访问）；`getCachedAmmoCount` 50ms 缓存、`tickStateLock` 250ms 超时、`reset()` 逐项 | 逐行等价 |
| `LocalPlayerAim` | `aim()` 发包去重/`shouldBlockAim`（换弹/拉栓/限时锁）、`tickAimingProgress` 的 `AdsModifier` 缓存、`getAlphaProgress` | 逐行等价 |
| `LocalPlayerShoot` | 蓄力（AUTO/HOLD/DELAY）、`preCheck`（点击按钮 50ms、换弹/切枪/拉栓/近战冷却、弹数/过热/手动上膛）、`doShoot` 异步连发（`scheduleAtFixedRate` + epoch/player/状态锁守卫 + `func_152344_a` 回主线程）、`GunShootEvent/GunFireEvent/GunFirePostEvent` 时机 | 逐行等价 |
| `LocalPlayerReload` | 取消换弹/换弹前置检查（背包直读、蓄力、100ms 射击冷却、弹药检查）、状态锁、事件、音效/动画 | 逐行等价 |
| `LocalPlayerBolt` | MANUAL_ACTION 判定、膛内/弹匣/背包弹、状态锁、发包、音效/动画 | 逐行等价 |
| `LocalPlayerDraw` | `resetData`、draw/putAway 时长、发包、`GunDrawEvent`、`AttachmentPropertyManager.postChangeEvent` | 等价（`ensureHasSentCarriedItem` 见 §7-4） |
| `LocalPlayerFireSelect` / `LocalPlayerMelee` / `LocalPlayerInspect` / `LocalPlayerSprint` / `LocalPlayerUnload` | 事件/状态锁/音效/动画顺序逐条一致 | 逐行等价（`Unload` 的 action bar 见 §7-5） |
| `ClientAmmoCount` | 背包直读弹药统计、9999 上限、创造模式弹药盒 | 等价（新增 `null` 判空：1.7.10 空槽是 `null`，上游 `ItemStack.EMPTY` 非 null） |
| `LocalPlayerCrawl` | 冷却 10 tick、`canCrawl`（存活/骑乘/跳跃/水中/离地/睡觉/持枪 `isCanCrawl`）、`setCrawling` 发包含义 | 等价；**姿态部分本轮补齐**（§6-4） |

**客户端状态清理（checklist 3）**：
- 瞄准进度：`LocalPlayerAim` 与上游一致；`oldAimingProgress` 插值 + `clientAimingTimestamp`。
- 射击预测：`LocalPlayerShoot.doShoot` 先发包再本机预测音效/动画，`isShootRecorded` 串行化，`epoch/player` 变化即取消异步任务（死亡/换维度/断线不会残留任务）。
- `ClientGunTimeResetEvent`（断线）：14 项重置与上游逐条同序（`ScopeLensRenderer.close` → dataHolder 清理 → `GunTime.resetEpoch` → 各 HUD/粒子/相机时间戳 → `GunHurtBobTweak`），**只处理断线**——与上游一致（SBM `PausedClientAnimationClock` 也只在 `LoggingOut` reset，见 `tmp/libs-src/.../ClientAnimationClockTicker.java:27-30`）；死亡/换维度由「客户端玩家实体被重建」+ 下面的 Clone 逻辑承担。
- `RefreshClonePlayerDataEvent`（换维度/换皮肤 respawn）：1.7.10 无 `ClientPlayerNetworkEvent.Clone` → `ClientTickEvent` 里检测 `thePlayer` 引用变化（1.7.10 `NetHandlerPlayClient#handleRespawn → Minecraft#setDimensionAndSpawnPlayer` 必 new 一个 `EntityClientPlayerMP`，`Minecraft.java:2393`）；搬运 `SyncedEntityData.dataMap`、`clientBaseTimestamp`，延迟 10 tick `initialData()`——与上游处理体逐行一致，仅触发方式平台化（≤1 tick 延迟）。
- `InventoryEvent`：快捷栏切换/物品变化 → `draw()`；`IAnimationItem.isSame` 或 `ItemStack.matches` 等价实现（1.7.10 手写 count+item+damage+NBT）；每 7/41 tick 预热模型；断线重置 `oldHotbarSelected`。

---

## 四、client/event 剩余 10 文件

| 文件 | 上游 | 本仓 | 结论 |
|---|---|---|---|
| `ClientPreventGunClick` | `InputEvent.InteractionKeyMappingTriggered`：交互键按下放行；主手持枪取消；展示框放行 | `PlayerInteractEvent(RIGHT_CLICK_BLOCK/AIR)` 取消（方块/空气）+ `MinecraftAttackMixin` 新增 `func_147121_ag` HEAD（实体分支，含交互键/展示框放行） | **本轮补实体分支**（§6-3） |
| `CommonNetworkCacheEvent` | `ClientPlayerNetworkEvent.LoggingIn/LoggingOut` | FML `ClientConnectedToServerEvent`（`isLocal` = `isMemoryConnection`）/`ClientDisconnectionFromServerEvent` | 等价 |
| `InventoryEvent` | `ClientTickEvent` + `SwapItemWithOffHand` + `LoggingOut` | 双总线注册（FML tick/断线、Forge api 事件），语义见 §3 | 等价 |
| `PlayerEnterWorld` | `PlayerLoggedInEvent` 提示旧枪包可 `/tacz convert` | FML `PlayerEvent.PlayerLoggedInEvent`；聊天组件/点击事件逐条对应 | 等价 |
| `PlayerHurtByGunEvent` | `EntityHurtByGunEvent.Post`（`logicalSide==CLIENT`、受伤者是自己）→ `GunHurtBobTweak.markTimestamp` | 同构（`LogicalSide→Side`） | 等价 |
| `PreventsHotbarEvent` | 合成台/改装界面打开时取消**所有** overlay 的 `Pre`（不判断 overlay id） | `RenderGameOverlayEvent.Pre` 对两类界面直接 `setCanceled(true)`（含 `Pre(ALL)` ⇒ `GuiIngameForge.java:105` 整体短路） | **本轮修**（§6-2） |
| `RefreshClonePlayerDataEvent` | `ClientPlayerNetworkEvent.Clone` | tick 引用变化检测（§3） | 等价（平台化） |
| `ReloadResourceEvent` | MOD 总线 `TextureStitchEvent.Post`（blocks atlas）→ `InternalAssetLoader.onResourceReload()` + `SoundPlayManager.clearSoundResourceCache()` | Forge 总线 `TextureStitchEvent.Post` + `event.map == getTextureMapBlocks()` 身份判定 | 等价 |
| `RenderHeadShotAABB` | `RenderLivingEvent.Post`：F3+B + 配置开关；`ForgeRegistries.ENTITY_TYPES.getKey` 取 id；`HeadShotAABBConfigRead` 取箱；无配置则 `eyeHeight±0.25`、扩张 0.01；`RenderType.lines()` 画黄框 | 见 §6-1（位置帧 + 实体 id 两处本轮修） | **本轮修** |
| `TickAnimationEvent` | tick：`isMovingSlowly`/sprint/输入向量 → RUN/WALK/IDLE；RenderTick：第三人称 `visualUpdate`（含 `tryInit`） | 同构；`isMovingSlowly` 补 crawl 判定（§6-5） | **本轮修** |

`FirstPersonRenderEvent` 属 r7-fp 作用域，但其 `onRenderHand` 上游 `@SubscribeEvent` 本就注释掉（`tmp/.../FirstPersonRenderEvent.java:23`），本仓注册到 Forge 总线为空转，**无害**（已在该文件 javadoc 登记）。

---

## 五、事件注册差集（上游清单 ↔ 本仓注册清单）

上游客户端订阅类 = `@Mod.EventBusSubscriber(CLIENT)` 全集 + 手工注册类。本仓落点：`client/ClientProxy` 的 `FML_BUS_CLASSES`(6) / `FORGE_BUS_CLASSES`(11) / `BOTH_BUS_CLASSES`(3) + 5 个 `INSTANCE` + `SoundPlayManager.TICK_HANDLER` + `ScreenItemVboContext` + `ClientSetupEvent`(MOD 总线步骤) + `RefitTransform`(懒自注册) + `DisplayCaseLockHintOverlay`(ClientSetupEvent 注册)。

| # | 上游类（事件） | 本仓注册落点 | 差集 |
|---:|---|---|---|
| 1 | `input/GunInputHandler`（RegisterKeyMappings + InputEvent.Key/MouseButton） | `ClientSetupEvent` step + `GunInputHandler.INSTANCE`(FML) | 等价 |
| 2-5 | `input/AimKey`(ClientTick×2)、`CrawlKey`(ClientTick)、`ReloadKey`(PlayerTick)、`ShootKey`(ClientTick) | 各自 `.INSTANCE` → FML 总线 | 等价 |
| 6 | `event/CameraSetupEvent`（Forge×4 + RenderTick） | `BOTH_BUS_CLASSES` | 等价 |
| 7 | `event/ClientGunTimeResetEvent`（LoggingOut） | `FML_BUS_CLASSES` | 等价 |
| 8 | `event/ClientHitMark`（EntityHurtByGunEvent.Post / EntityKillByGunEvent） | `FORGE_BUS_CLASSES` | 等价 |
| 9 | `event/ClientPreventGunClick`（InteractionKeyMappingTriggered） | `FORGE_BUS_CLASSES`（PlayerInteractEvent）+ `MinecraftAttackMixin` | 等价（本轮补） |
| 10 | `event/CommonNetworkCacheEvent`（LoggingIn/Out） | `FML_BUS_CLASSES` | 等价 |
| 11 | `event/FirstPersonRenderEvent`（RenderHandEvent，**上游已注释**） | `FORGE_BUS_CLASSES` | **多 1 项**（空转无害） |
| 12 | `event/FirstPersonRenderGunEvent`（GunFireEvent + RenderItemInHandBobEvent） | `FORGE_BUS_CLASSES` | 等价 |
| 13 | `event/GunDisplayCaseClientInteractionEvent`（InputEvent → tick 替代） | `FML_BUS_CLASSES` | 等价 |
| 14-15 | `event/GunDisplayCaseHighlightEvent` / `GunPegboardHighlightEvent`（DrawBlockHighlightEvent） | `FORGE_BUS_CLASSES` | 等价 |
| 16 | `event/InventoryEvent`（ClientTick + SwapItemWithOffHand + LoggingOut） | `BOTH_BUS_CLASSES` | 等价 |
| 17 | `event/PlayerEnterWorld`（PlayerLoggedInEvent） | `FML_BUS_CLASSES` | 等价 |
| 18 | `event/PlayerHurtByGunEvent`（EntityHurtByGunEvent.Post） | `FORGE_BUS_CLASSES` | 等价 |
| 19 | `event/PreventsHotbarEvent`（RenderGuiOverlayEvent.Pre） | `FORGE_BUS_CLASSES` | 等价（本轮修语义） |
| 20 | `event/RefreshClonePlayerDataEvent`（ClientTick） | `FML_BUS_CLASSES` | 等价 |
| 21 | `event/ReloadResourceEvent`（TextureStitchEvent.Post，MOD 总线） | `FORGE_BUS_CLASSES` | 等价 |
| 22 | `event/RenderCrosshairEvent`（RenderGuiOverlayEvent.Pre + RenderTick） | `BOTH_BUS_CLASSES` | 等价 |
| 23 | `event/RenderHeadShotAABB`（RenderLivingEvent.Post） | `FORGE_BUS_CLASSES` | 等价（本轮修语义） |
| 24 | `event/TickAnimationEvent`（ClientTick + RenderTick） | `FML_BUS_CLASSES` | 等价（本轮修语义） |
| 25 | `event/TooltipEvent`（ItemTooltipEvent） | `FORGE_BUS_CLASSES` | 等价 |
| 26 | `animation/screen/RefitTransform`（RenderTickEvent） | 首次 `GunRefitScreen` 构造时 `init()` → 自注册 FML（幂等） | 等价（懒注册；关闭动画在首次打开后一直有效） |
| 27 | `gui/overlay/DisplayCaseLockHintOverlay`（ClientTick） | `ClientSetupEvent#registerOverlays` 实例注册 FML | 等价 |
| 28 | `init/ClientSetupEvent`（MOD 总线 7 个事件） | `onClientSetup()` 19 个 step（r5-D 已逐个核实有真实实现） | 等价 |
| 29 | `init/GunPegboardColors`（FMLClientSetupEvent） | `ClientSetupEvent` step | 等价 |
| 30 | `init/ModContainerScreen`（FMLClientSetupEvent） | step 为**有据 no-op**（客户端界面分发在 `inventory/GuiHandler#getClientGuiElement`） | 有据 no-op |
| 31 | `init/ModEntitiesRender`（FMLClientSetupEvent） | step → TESR/实体渲染器注册 | 等价 |
| 32 | `init/ParticleFactoryRegistry`（RegisterParticleProvidersEvent） | step 为**有据 no-op**（1.7.10 无 `ParticleType` 注册表） | 有据 no-op |
| 33 | `renderer/item/ScreenItemVboContext`（GuiScreenEvent） | `ClientProxy` 显式 `new` + Forge 总线 | 等价 |
| 34 | `renderer/scope/ScopeThermalRenderer`（RegisterShaders + RenderLevelStage） | `ClientSetupEvent` 的 `init` step + `onRenderWorldLast`（r6-2 起） | 等价 |
| 35 | `sound/SoundPlayManager`（ClientTick） | `SoundPlayManager.TICK_HANDLER`（r5-D 修） | 等价 |

**差集结论：缺失 0 项；多出 1 项（#11，空转无害）。** `mixins.tacz.json` 的 16 个 mixin 与 `mixin/**` 现状一致（3 个归属本任务，均已注册）。

---

## 六、本轮修复（逐条：本仓 ↔ 上游依据）

### 6-1 P2 `RenderHeadShotAABB`：绘制帧错误 + 实体 id 点号（`client/event/RenderHeadShotAABB.java`）

- **位置帧**：1.7.10 `RenderLivingEvent.Post` 在 `RendererLivingEntity#doRender` 的 `glPopMatrix()` **之后**投递（`build/rfg/.../RendererLivingEntity.java:295/297`），矩阵是相机帧；原实现直接画实体局部坐标 AABB ⇒ 框画在相机原点。上游 1.20 事件 `PoseStack` 停在「实体位置」帧（`LivingEntityRenderer.java.patch`：`popPose` 后、`super.render` 后 post，`EntityRenderDispatcher` 的平移仍在）。**修法**：`GL11.glPushMatrix()` + `glTranslatef(event.x, event.y, event.z)` 再画局部 AABB（`event.x/y/z` 已是「实体位置 − renderPos」），`finally` 成对 pop（`:86-107`）。GL 状态同时对齐上游 `RenderType.lines()`：深度测试/写入开、`LEQUAL`、混合开、剔除关、线宽 1（原实现关深度测试 + 线宽 2，会让调试框穿墙）。
- **实体 id**：原 `EntityList.getEntityString(entity).toLowerCase()` 对 FML 模组实体（`"<modid>.<name>"`，`EntityRegistry.java:162-167`）会得到 `minecraft:tacz.bullet`，`HeadShotAABB` 配置永远命中不到。**修法**：改用本仓既有的单一来源 `util/EntityIdHelper#getModernId(Entity)`（`EntityUtil` / `ThermalImagingHelper` / `LootInjectionEvent` / `InteractKeyConfigRead` 已统一到它）：原版驼峰名 → 1.20 snake_case（`ItemFrame→item_frame`、`MushroomCow→mooshroom` 等差异表）+ `modid.name → modid:name`（`:69`）。
- 保留降级：`minecraft.showOnlyReducedInfo()` 无 1.7.10 对应（`// TODO(L2)`）。

### 6-2 P2 `PreventsHotbarEvent`：取消范围不足（`client/event/PreventsHotbarEvent.java`）

上游 `onRenderHotbarEvent` **不判断 overlay id**（`tmp/.../PreventsHotbarEvent.java:14-27`）：只要 `currentScreen instanceof GunSmithTableScreen/GunRefitScreen` 就 `setCanceled(true)`。1.20 Forge 对 `GuiOverlayManager.getOverlays()` 的**每个** layer（含 TACZ 自己注册的）都投递 `RenderGuiOverlayEvent.Pre`（`ForgeGui.java:121-132`）⇒ 等价于「这两类界面打开时取消全部 HUD」。原实现只对 `ElementType.HOTBAR` 取消 ⇒ 生命/饥饿/经验/准星/聊天/计分板仍在合成台/改装界面背后绘制。**修法**：去掉 type 过滤，两类界面下对每个 `RenderGameOverlayEvent.Pre` 直接 `setCanceled(true)`（含 `Pre(ALL)`，`GuiIngameForge.java:105` 整体短路；`Post(ALL)` 不再发 ⇒ TACZ 自己的 HUD 层也不画，与上游「所有 layer 被取消」一致）。与 r7-crosshair 独立结论一致（其报告 §五 F3 / §七 V-8 已改为「r7-input 已修」）。

### 6-3 P2 `ClientPreventGunClick` + `MinecraftAttackMixin`：持枪右键实体未拦截

上游取消整条「使用」输入映射（`ClientPreventGunClick.java:21-41`：交互键放行、主手持枪取消、展示框放行）。1.7.10 的 `Minecraft#func_147121_ag` 只有方块/空气分支投递 `PlayerInteractEvent`（`Minecraft.java:1528/1556`），**实体分支直接 `interactWithEntitySendPacket`（`:1512-1517`）** ⇒ 持枪右键村民/马仍会交互。**修法**：`mixin/client/MinecraftAttackMixin` 新增 `@Inject(method="func_147121_ag", at=HEAD, cancellable, require=0, expect=1, remap=false)`（`:173-201`），只在「主手有枪 + ENTITY 命中 + 未按交互键 + 目标不是 `EntityItemFrame`」时 `ci.cancel()`；方块/空气分支保持原事件层拦截不动。SRG 名 + `remap=false`、`require=0` 沿用本类既有约定（避免 OptiFine/交互优化 mod 改方法名时启动崩）。`ClientPreventGunClick` javadoc 同步登记该分工。

### 6-4 P2 趴下姿态：mcshim 姿势覆盖表未接线（`client/gameplay/LocalPlayerCrawl.java`）

`mcshim/EntityStateHelper` 的 javadoc 明确写「移植后的 crawl 逻辑把自己的状态塞进 `ENTITY_POSE_OVERRIDES`」，但原 `LocalPlayerCrawl` 把姿态写入整段降级、`tickCrawl` 的「姿态被外部改回」检测也降级为恒 false ⇒ **三处消费者全是死分支**：`MouseHandlerMixin` 的趴下俯仰限制、`CameraSetupEvent` 的趴下后坐力系数、`TickAnimationEvent` 的趴下移动动画判定。**修法**（与上游 `LocalPlayerCrawl.java:43-88` 同构）：
- `setCrawling()`：`if (crawling || getForcedPose(player)==SWIMMING) setForcedPose(player, crawling ? SWIMMING : null)`（`:122-125`）；
- `tickCrawl()`：`poseChanged = getForcedPose(player) != SWIMMING`，恢复上游「姿态被外部改回就退出趴下」检测（`:83-86`）。
仍属降级：1.7.10 无姿势驱动的体型/视高（AABB/eyeHeight 需实体 Mixin，未做；见 §7-6）。

### 6-5 P3 `TickAnimationEvent`：`isMovingSlowly` 漏 crawl（`client/event/TickAnimationEvent.java`）

上游 `LocalPlayer#isMovingSlowly()` = `isCrouching() || isVisuallyCrawling()`（`tmp/minecraft-src-1.20.1/.../LocalPlayer.java:622-624`）；原实现只映射 `isSneaking()`，趴下时仍会播 walk/run 动画。**修法**：`movingSlowly = player.isSneaking() || (!player.isInWater() && EntityStateHelper.getPose(player) == EntityPose.SWIMMING)`（与 1.20 `Entity#isVisuallyCrawling` 同义；`:74-87`）。

### 6-6 P2 `MouseHandlerMixin`：内置瞄具灵敏度系数恒 1（`mixin/client/MouseHandlerMixin.java`）

上游 `MouseHandlerMixin` 对 `getZoomNumberFromTag(attachmentTag)` 的 `null` 直接取 0（`AttachmentItemDataAccessor.java:37-45`），即**原厂内置瞄具**（无物品 NBT）用 `zoom[0]`；本仓多了一层 `attachmentTag != null` 守卫 ⇒ 内置瞄具 `zoomLevel` 停在 1.0，开镜灵敏度完全不压低。默认枪包里 `ak47/aug/hk416d/m4a1/p90/svd` 有 builtin SCOPE（`data/tacz/data/guns/*_data.json` 的 `builtin_attachments.SCOPE`）。**修法**：去掉守卫、直接 `zoom[getZoomNumberFromTag(attachmentTag) % zoom.length]`（`:133-138`），与上游逐字一致。

---

## 七、跨模块发现

| # | 位置（非本 scope） | 级别 | 说明 / 建议 | 归属 |
|---:|---|---|---|---|
| C1 | `entity/shooter/LivingEntityCrawl#setCrawling` | P2 | 服务端 crawl 同样不写 `EntityStateHelper` 姿势表 ⇒ 服务端 Lua API `ModernKineticGunScriptAPI#isCrawling()`（`item/ModernKineticGunScriptAPI.java:291-292`，读 `getPose(player)==SWIMMING`）**恒 false**，枪包 Lua 的 `is_crawling` 永远不生效。建议按本任务 §6-4 的同一写法在服务端 `setCrawling` 写/清 `EntityStateHelper.setForcedPose`（只对 `EntityPlayer`；非玩家上游用 `setPose`，1.7.10 无姿势系统可同样只写表）。客户端已由本任务接线，两端可一致 | r7-gun（`entity/**`） |
| C2 | `client/init/ClientSetupEvent#onRenderOverlay` | —（已修） | 曾用 `mc.currentScreen != null → return` 让 TACZ 自己的 HUD 层在任何界面（含聊天框）打开时不画；1.20 上游 HUD 先于 screen 渲染、GUI 打开时仍画（`EntityRenderer.java:1111` / 1.20 `GameRenderer.java:1121-1141`）。**r7-platform 本轮已改为 `hideGUI && currentScreen == null` 才早退，已复核现状正确** | r7-platform（已修） |
| C3 | `api/util/ResourceLocationHelper#tryParseEntityName` / `util/EntityIdHelper#toModernId` | 记录 | r7-api 新增的实体名解析原语语义正确；`EntityIdHelper` 目前自带同一段「点号→冒号」解析。建议 `toModernId` 的解析步骤改为委托 `tryParseEntityName`（保留原版驼峰差异表），纯去重、零语义变化 | r7-util / r7-api |
| C4 | `mixin/client/{LanguageMixin,SoundManagerPreparationsMixin}.java` | P3 | 两个 mixin 类存在但**未登记进 `mixins.tacz.json`**（配置 client 列表只有 12 项）。本仓语言注入改走 `GunPackLoader#injectGunPackLanguage`（`ClientProxy.java:294-305`）、音效重挂改走 `IResourceManagerReloadListener`，二者疑似已无消费者的死代码；建议 r7-platform 判定「补登记」还是「删除」 | r7-platform（`mixin/**`） |
| C5 | `event/LootInjectionEvent` / `config/util/InteractKeyConfigRead` | —（已修） | 两处实体 id 推导均已改为委托 `util/EntityIdHelper`（本轮复核时已在位），与 §6-1 的修法同源；无需再动 | r7-gun / r7-core（已修） |

---

## 八、未修与有据降级

| # | 项 | 级别 | 依据 / 处置 |
|---:|---|---|---|
| 1 | `ConfigKey` 默认键：上游 ALT+T → 本仓未绑定 | P3 | 1.7.10 `KeyBinding` 无修饰键；`T` 是原版聊天键且构造器会把 keyCode→binding 写进静态 hash 顶掉原版（同 §2.1 的 `-100/-99` 问题）。要恢复需把该 binding 也从 `keybindArray` detach（可行但收益极低：1.7.10 无 Cloth Config，`MenuIntegration.LOADED` 恒 false，该键只弹下载提示）。保留降级并在类注释登记 |
| 2 | 输入延迟 ≤1 tick | P3（平台） | 1.7.10 无 `InputEvent`，只能 tick 轮询。半自动/近战/换弹的「按下沿」最多晚 1 tick（50ms）；自动武器（`ShootKey.autoShoot` 本就 tick 轮询）不受影响 |
| 3 | `InteractKey` 的 `mc.startUseItem()` 复刻 | P3 | 1.7.10 无公开 `startUseItem`；直接调 `onPlayerRightClick`/`interactWithEntitySendPacket`（与 `func_147121_ag` 内分支相同），跳过 `rightClickDelayTimer`/`isUsingItem` 守卫——O 键只在按下沿触发一次，实际无可观察差异 |
| 4 | `LocalPlayerDraw/Unload` 的 `ensureHasSentCarriedItem` | P3 | 1.7.10 `PlayerControllerMP#syncCurrentPlayItem()` 是 private，复刻为「每次发包」而非「槽位变化才发」⇒ 多一个 `C09PacketHeldItemChange`，服务端同槽位幂等，无状态影响 |
| 5 | `LocalPlayerUnload` 的 action bar 提示 → 聊天栏 | P3 | 1.7.10 无 `displayClientMessage(..., true)` 的 action bar 通道（`GuiIngame` 只有聊天/标题）；服务端 `ClientMessagePlayerUnloadGun` 已同口径降级（本任务只复核一致性） |
| 6 | 趴下的体型/视高 | P3（平台） | 1.7.10 无 `Pose` 驱动的 AABB/eyeHeight；本轮已让「俯仰限制/后坐力系数/移动动画/服务端同步」按上游语义生效，真正改变碰撞箱需在实体上做 Mixin（属 mixin 工作流，`LivingEntityCrawl` 同类说明） |
| 7 | `RefreshClonePlayerDataEvent` 触发方式 | P3（平台） | 1.7.10 无 `ClientPlayerNetworkEvent.Clone`，改为 tick 引用变化检测（最多晚 1 tick）；数据搬运/基时间戳/延迟刷新与上游一致 |
| 8 | `RenderHeadShotAABB` 的 `showOnlyReducedInfo()` | P3（平台） | 1.7.10 无「减少调试信息」开关，删除该守卫（`// TODO(L2)`） |
| 9 | `ClientPreventGunClick` 左键不放行交互键 | 记录 | 与上游差异：上游交互键按下时放行攻击；但 1.7.10 服务端 `event/PreventGunClick` 对 `LEFT_CLICK_BLOCK` 无条件取消，客户端放行会重新造出幽灵方块。为端一致保留「左键不放行」；**右键已按上游放行**（服务端无对应无条件取消，安全） |
| 10 | `isSpectator` 相关分支 | 记录 | 1.7.10 无旁观者模式，`AimKey/ShootKey/CrawlKey/...` 的 `isSpectator` 守卫删除（恒 false，语义等价） |

---

## 九、实机验证清单（按可判读性排序）

> 已有证据：`run/client/logs/fml-client-latest.log`（2026-11-05 18:38 一次完整客户端启动 + 进世界 + 试射 MP5/AWP）。该日志证明：
> ① `mixins.tacz.json` 16 个 mixin 全部 apply（含本任务 3 个），无 `InjectionError`；
> ② 键位 detach 反射无报错、无 `client handler not registered`；
> ③ 全程 0 条 `at com.tacz` 栈、0 条 `Exception`；
> ④ 有 AWP 拉栓/换弹音效与 MP5 开镜日志 ⇒ 输入 → gameplay 链路真实跑通过。
> 以下为本轮**新改动**需要复验的点。

1. **右键实体拦截**（§6-3）：持枪右键村民/马/（非展示框）实体 → 无交互；按住 `O` 再右键 → 恢复交互；持枪右键展示框 → 仍可旋转/取放物品。判读：`run/client/logs/latest.log` 不应出现新增报错；可临时在 mixin 回调打一条 debug 日志确认注入命中（验证后删除）。
2. **爆头调试框位置**（§6-1）：F3+B 打开命中箱 + `tacz-client.cfg` 里 `HeadShotDebugHitbox=true` → 黄色爆头框应出现在每个生物头部（而不是相机原点/玩家脸上）；在 `tacz-common.cfg`/同步配置 `misc.HeadShotAABB` 写 `minecraft:zombie [-0.3,1.4,-0.3,0.3,1.8,0.3]` 与 `tacz:xxx`（本模组实体）→ 都应生效。
3. **趴下（crawl）**（§6-4）：持可趴下枪械按 `C` → 视角上下被 `CrawlMaxUpAngle/CrawlMaxDownAngle` 限制；开火后坐力明显小于站立（`crawl_recoil_multiplier`）；按住方向键移动时枪械播 idle 而非 walk/run；松开 `C`/离地/切到不可趴下物品 → 立即退出且视角恢复。
4. **内置瞄具灵敏度**（§6-6）：`ak47/aug/hk416d/m4a1/p90/svd` 不装瞄具开镜（原厂内置镜）→ 鼠标灵敏度应明显低于腰射；装同 `zoom[0]` 的外置瞄具对比应接近。
5. **合成台/改装界面 HUD**（§6-2）：打开两个界面 → 原版 HUD（生命/饥饿/经验/准星/聊天/计分板）全部不画；关闭后恢复。
6. **键位全表**（§2）：R 换弹、Alt+R 卸弹（且不会穿透成换弹）、V 未瞄准=近战 / 瞄准=切换倍率、Z 改装（改装界面中再按 Z 关闭）、G 切模式、H 检视、C 趴下、O 交互、右键开镜、左键开火；**同时**原版左键攻击/挖方块、右键使用/放置/进食/开箱全部正常（detach 修复）。
7. **状态清理**：开火中死亡 / 换维度 / 断线重连 → 瞄准进度、切枪、换弹、拉栓、开火冷却全部归零；重连后第一枪正常（`clientBaseTimestamp` 已搬运，服务端时间戳校验通过）。
8. **多人服**：进服枪包同步 + 开火/换弹/切枪包时间戳校验正常（`RefreshClonePlayerDataEvent` 的基时间戳搬运只在「客户端玩家实体被重建」时生效，进服首帧不应误触发）。

---

## 十、改动文件 / 检查结果 / 覆盖清单（诚实）

### 10.1 本轮改动（7 文件，`lint_files(errorsOnly)` 全 0）

| 文件 | 改动 |
|---|---|
| [`client/event/RenderHeadShotAABB.java`](src/main/java/com/tacz/guns/client/event/RenderHeadShotAABB.java) | ① 事件帧修正：`glPushMatrix` + `glTranslatef(event.x,y,z)` 后再画局部 AABB；② GL 状态对齐 `RenderType.lines()`（深度测试/写入、混合、无剔除、线宽 1）；③ 实体 id 改用 `EntityIdHelper#getModernId`（修 FML `modid.name` 点号 + 原版驼峰名）；④ javadoc 修正「MV 已平移到实体」的错误论断 |
| [`client/event/PreventsHotbarEvent.java`](src/main/java/com/tacz/guns/client/event/PreventsHotbarEvent.java) | 去掉 `ElementType.HOTBAR` 过滤，合成台/改装界面下取消所有 `Pre`（含 `ALL`），与上游「取消所有 overlay」等价 |
| [`client/event/TickAnimationEvent.java`](src/main/java/com/tacz/guns/client/event/TickAnimationEvent.java) | `isMovingSlowly` 补 `isVisuallyCrawling`（姿势 SWIMMING 且不在水里） |
| [`client/event/ClientPreventGunClick.java`](src/main/java/com/tacz/guns/client/event/ClientPreventGunClick.java) | javadoc 登记「右键实体分支由 MinecraftAttackMixin 补齐」 |
| [`client/gameplay/LocalPlayerCrawl.java`](src/main/java/com/tacz/guns/client/gameplay/LocalPlayerCrawl.java) | 接线 `EntityStateHelper` 强制姿势（写入/清除 + `poseChanged` 检测恢复上游语义）；更新降级说明 |
| [`mixin/client/MinecraftAttackMixin.java`](src/main/java/com/tacz/guns/mixin/client/MinecraftAttackMixin.java) | 新增 `func_147121_ag` HEAD 注入：持枪右键实体拦截（交互键/展示框放行）；javadoc 补该分工与差异 |
| [`mixin/client/MouseHandlerMixin.java`](src/main/java/com/tacz/guns/mixin/client/MouseHandlerMixin.java) | 去掉 `attachmentTag != null` 守卫，内置瞄具按 `zoom[0]` 计算灵敏度分母（与上游一致） |

### 10.2 覆盖清单

- **逐行精读（39/39 本任务文件）**：`client/input/**` 13、`client/gameplay/**` 13、`client/event/**` 剩余 10、3 个 mixin。
- **上游对读**：上述 39 个文件的上游对应文件全部逐行读过；另外为核对事件时机读了 1.7.10 `Minecraft.java`（runTick/右键/左键/`func_152344_a`）、`EntityRenderer.java`（HUD/相机/mouse）、`GuiIngameForge.java`（Pre/Post 投递点）、`RendererLivingEntity.java`、`RenderManager.java`、`KeyBinding.java`、`EntityPlayerSP.java`、`NetHandlerPlayClient.java`、`Minecraft.java#setDimensionAndSpawnPlayer`、`TickEvent.java`、`FMLNetworkEvent.java`、`EntityRegistry.java`、SBM `AnimationClocks/PausedClientAnimationClock/ClientAnimationClockTicker`；1.20 侧 `LocalPlayer.java`、`ItemStack.java`、`RenderType.java`、`ForgeGui.java`、`Minecraft.java.patch`、`LivingEntityRenderer.java.patch`。
- **只做注册/签名交叉核对（未审语义，归属他人）**：`client/event` 其余 10 个文件（`CameraSetupEvent`/`ClientGunTimeResetEvent`/`ClientHitMark`/`FirstPersonRenderEvent`/`FirstPersonRenderGunEvent`/`RenderCrosshairEvent`/`TooltipEvent`/`GunDisplayCase*`×2/`GunPegboardHighlightEvent`）。
- **未覆盖**：无实机操作（纪律禁止 gradle/runClient）；`client/init/**` 与 `ClientProxy` 只核对注册差集，未逐行审实现（r7-platform 范围）；`client/gameplay` 的底层 `LivingEntity*`（服务端）未审（r7-gun 范围）。
