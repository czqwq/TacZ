# 03 — client UI / 事件 / 输入 / 粒子 / 音效 / Gameplay 审查报告

- 审查人：`client-ui-review`（task-3）
- 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`
- 1.7.10 源码准绳：`build/rfg/minecraft-src/java/**`
- 证据：`build/r7run.log`、`run/client/crash-reports/crash-2026-11-04_13.28.20-client.txt`
- 写作用域：`client/gui/**`、`client/event/**`、`client/input/**`、`client/particle/**`、`client/sound/**`、
  `client/tooltip/**`、`client/init/**`、`client/gameplay/**`、`client/ClientTickHelper.java`
- 结论：**本作用域内找到 3 个真 bug（F1 F2 F4，已修）+ 1 个 javadoc ERROR（F3，已修）
  + 1 个 P1 资源重载失效（Lead 转派，已修，配套 preInit 改动已落地）；
  事件总线零错配；GL 线程安全通过；最新崩溃的根因在 Lead 独占的 `ClientAssetsManager`，
  其全部 7 个调用点已确认空安全。**

---

## 1. 已修复（本作用域，直接改）

| # | 文件:行 | 问题 | 根因（证据） | 修法 |
|---|---|---|---|---|
| F1 | `client/gui/components/CreativePackFilters.java:150-176` + `:631-646` | 创造模式「内容包筛选」侧栏选项卡**贴图缺失**（渲染成紫黑/纯白块） | 上游用 `textures/gui/recipe_book.png`（`tmp/TACZ-1.20.1/.../CreativePackFilters.java:29`），该图 **1.13 才加入原版**；实测 1.7.10 `assets/minecraft/textures/gui/` 只有 `book / demo_background / icons / options_background / resource_packs / stream_indicator / widgets.png`，**无 `recipe_book.png`** | 换 `textures/gui/container/creative_inventory/tabs.png`，按原版左侧选项卡 sprite 取 u=0 / v=64（未选中）/ 96（选中）、28×32。依据：`GuiContainerCreative.java:40`（贴图常量）、`:927`（`j = i*28`）、`:933`（选中 `k += 32`）、`:953`（左侧列 `k += 64`）。sprite 从 `y-3` 起画，使 32px 高与原 26px 按钮框同中心（图标 `y+5..y+21` 中心 `y+13`） |
| F2 | `client/gui/components/GunPackList.java:284-303` + `:345-356` | 枪包列表的**复选框贴图缺失**（紫黑方块） | 上游用 Forge 的 `textures/gui/checkbox.png`（`tmp/.../GunPackList.java:202,252`），1.7.10 原版**没有任何复选框控件也没有该贴图**（同 F1 的实测列举） | 不再绑定贴图，用 `GuiGraphics#fill` 画 10×10 外框（悬停变亮）+ 内填充 + 选中勾选块，等价上游 2×2 的 10×10 四态 |
| F3 | `client/gui/compat/ClothConfigScreen.java:24` | IDEA `get_file_problems` 报 **ERROR**：`无法从此处访问符号 'forName'` | javadoc 写了 `{@link Class#forName}`（`Class` 只有静态 `forName`，javadoc 解析器拒绝） | 改为 `{@code Class.forName}`（纯注释，无语义变化） |
| F4 | `client/gui/components/CreativePackFilters.java:288-302` | 分组图标 `items.get(0).copy()` 未判空 → 空组 `IndexOutOfBoundsException` / 首元素 null 时 NPE（P3，mixin-compat-review 提出，已确证） | 同方法开头 `allItems` 的构造明确允许 null 元素（`stack == null ? null : stack.copy()`），可见 `displayItems` 可以含 null；`groupItems` 虽然目前只收非 null 栈，但这是**跨方法隐式契约**，一旦上游/后续改动放进 null 就炸在 GUI 打开路径上（与 crash-2026-11-04 同一族） | 抽出 `ItemStack icon = items.isEmpty() \|\| items.get(0) == null ? null : items.get(0).copy()`；`icon` 为 null 是安全的：只有 logo 为 null 时才会用它，而 `mcshim/GuiGraphics.java:543-545` 的 `renderItem` 对 null 直接 return（等价上游 `ItemStack.EMPTY` 的空槽） |

修后自查：三文件 `lint_files(min_severity=error)` 与 `get_file_problems(errorsOnly=true)` 均为 **0 error**。

---

## 1b. P1 修复（Lead 转派）：枪包资源在每轮原版资源重载里保持挂载

**问题**：`SimpleReloadableResourceManager#reloadResources(List)` 第一步 `clearResources()`
（`SimpleReloadableResourceManager.java:89-97`：`domainResourceManagers.clear()` + `setResourceDomains.clear()`），
随后**只**重挂传入的包列表（同文件 `:111-118`）；而 `Minecraft#refreshResources()` 组出的列表
= `defaultResourcePacks` + 资源包仓库（`Minecraft.java:636-654`），**不含** `GunPackResourcePack`
→ 每一轮原版资源重载（启动期、`F3+T`、`/tacz reload`）都把枪包清掉，贴图 / ogg 全部解析失败。

**关键时序（1.7.10 源码实测）**：

| 行 | 动作 |
|---|---|
| `Minecraft.java:519` | `mcResourceManager = new SimpleReloadableResourceManager(...)` |
| `Minecraft.java:522` | `FMLClientHandler.beginMinecraftLoading` → **mod preInit**（`FMLClientHandler.java:208`），其 `finally` 里 `client.refreshResources()`（`:236-239`） |
| `Minecraft.java:523-524` | `TextureManager` 创建并 `registerReloadListener` |
| `Minecraft.java:585-592` | `new TextureMap(0,"textures/blocks")` → `loadTextureMap` → **★ 图集 stitch**（`TextureStitchEvent`） |
| `Minecraft.java:597` | `finishMinecraftLoading()` → **mod init / postInit** |

`notifyReloadListeners()` 按注册顺序遍历（`reloadListeners` 是插入有序 `ArrayList`，`SimpleReloadableResourceManager.java:28`），
所以**只有在 preInit 注册**，监听器才能排在 `TextureManager`（`:524`）之前，让「重挂枪包」发生在 stitch 之前。
`SoundLoadEvent` 那条兜底必然晚于 stitch（`SoundHandler` 在 `:528` 才注册）—— 这正是 `r7run.log` 12:23:39 的一批 TEXTURE ERRORS。

**改动（本作用域）**：

| 文件 | 内容 |
|---|---|
| `client/sound/GunPackResourcePackReloadListener.java`（**新增**，普通包顶层类，非 mixin） | `implements IResourceManagerReloadListener`，`onResourceManagerReload` → `GunPackResourcePack.onResourceManagerReload(...)` |
| `client/sound/GunPackResourcePack.java:242-259` | `ensureInstalled()` 里新增 `registerReloadListenerOnce()`；末尾加「晚注册补救」 |
| `client/sound/GunPackResourcePack.java:275-323` | 新增 `registerReloadListenerOnce()`：static 标志先置真再注册（`registerReloadListener` 会**同步立刻回调**一次，见 `SimpleReloadableResourceManager.java:125-132`，故必须防递归）；manager 不是 `IReloadableResourceManager` 时降级并 warn；注册成功后做**时机自检**（`getTextureMapBlocks() != null` ⇒ 已晚于 `:585` 图集创建 ⇒ 打 warn 指明应移到 preInit） |
| `client/sound/GunPackResourcePack.java:344-368` | 新增 `onResourceManagerReload(...)`：`installedDomains = null` 后调 **`ensureInstalled()`**（按 Lead 指定形态；`registerReloadListenerOnce` 由 static 标志守住，回调内递归到底即返回，不会无限递归）；**明确不调** `refreshResources()` / `reloadAllPack()` —— 前者会在 `notifyReloadListeners()` 中途再 `clearResources()`（递归重载），后者是另一条独立的枪包数据链（`ClientGunPackReloader` → `ClientProxy.postInit`）。`wasInstalled` 因此为假 → 重载通知链里不会再叠一层 `ProgressManager` |
| `client/sound/GunPackResourcePack.java` 字段/import/javadoc | 新增 `RELOAD_LISTENER` 单例 + `reloadListenerRegistered` / `registeredAfterAtlasCreation` 两个 static 标志、`IReloadableResourceManager` import、类注释第 3 条改写（说明主路径已换成 reload listener，SoundLoadEvent + 40 tick 探针降为兜底） |

**幂等 / 重入**：`registerReloadListenerOnce()` 用 static 标志，二次调用直接返回；同步回调里再走到本方法也被该标志挡住。
`ensureInstalled()` 从「早返回」改成「条件 install」，保证补救分支不会被同步回调填好的快照短路。
轮询路径（`validateInstalled` 40 tick 探针）、`SoundLoadEvent`、`invalidateCache()` 三处联动保持原样（`onResourceManagerReload` 里 `installedDomains = null` 后 `probeLocation` 一并作废在 `invalidateCache()`）。

**✅ Lead 配套改动已落地**：`client/ClientProxy.java:172` 现在位于
`preInit(FMLPreInitializationEvent)` 第 3 步（`init` 里只留注释），正好落在
`Minecraft.java:522` 与 `:585` 之间 —— 因此首轮图集 stitch 之前枪包已完成挂载，
本修复按设计生效；新增的「晚注册补救」在正确时序下不会触发（`getTextureMapBlocks()` 此时为 null）。

---

## 2. 事件总线正确性 —— 通过（重点复核项）

1.7.10 的判定规则：`cpw.mods.fml.common.gameevent.TickEvent.*` / `cpw PlayerEvent.*` / `FMLNetworkEvent.*`
只 post 到 **FML 总线**；`net.minecraftforge.client.event.*` / `net.minecraftforge.event.entity.*` /
TACZ 自己的 `api.event.common.*` 只在 **Forge 总线**。

逐类核对 `client/ClientProxy.java:82-143` 的三张清单 × 每类实际 handler 参数类型（共 20 类 + 13 个自注册处理器）：

| 类 | 实际订阅 | 应注册 | `ClientProxy` 实际 | 结论 |
|---|---|---|---|---|
| `ClientTickHelper` | `TickEvent.RenderTickEvent` | FML | FML（`:176-178`） | OK |
| `SoundPlayManager` | `TickEvent.ClientTickEvent` | FML | FML | OK（另见 §4-R4） |
| `ClientGunTimeResetEvent` | `FMLNetworkEvent.ClientDisconnection…` | FML | FML | OK |
| `CommonNetworkCacheEvent` | `FMLNetworkEvent.*` ×2 | FML | FML | OK |
| `PlayerEnterWorld` | `PlayerEvent.PlayerLoggedInEvent` | FML | FML | OK |
| `RefreshClonePlayerDataEvent` | `TickEvent.ClientTickEvent` | FML | FML | OK |
| `TickAnimationEvent` | `TickEvent.ClientTickEvent` + `RenderTickEvent` | FML | FML | OK |
| `GunDisplayCaseClientInteractionEvent` | `TickEvent.ClientTickEvent` | FML | FML | OK |
| `CameraSetupEvent` | `TickEvent.RenderTickEvent` + `BeforeRenderHandEvent` + `FOVUpdateEvent` + `GunFireEvent` | 双总线 | BOTH | OK |
| `InventoryEvent` | `TickEvent.ClientTickEvent` + `SwapItemWithOffHand` + `FMLNetworkEvent…` | 双总线 | BOTH | OK |
| `RenderCrosshairEvent` | `RenderGameOverlayEvent.Pre` + `TickEvent.RenderTickEvent` | 双总线 | BOTH | OK |
| `ClientHitMark` | `EntityHurtByGunEvent.Post` / `EntityKillByGunEvent` | Forge | Forge | OK |
| `ClientPreventGunClick` | `PlayerInteractEvent` | Forge | Forge | OK |
| `FirstPersonRenderEvent` | `RenderHandEvent`（上游就注释掉） | Forge | Forge | OK |
| `FirstPersonRenderGunEvent` | `RenderItemInHandBobEvent.BobView` + `GunFireEvent` | Forge | Forge | OK |
| `GunDisplayCaseHighlightEvent` / `GunPegboardHighlightEvent` | `DrawBlockHighlightEvent` | Forge | Forge | OK |
| `PlayerHurtByGunEvent` | `EntityHurtByGunEvent.Post` | Forge | Forge | OK |
| `PreventsHotbarEvent` | `RenderGameOverlayEvent.Pre` | Forge | Forge | OK |
| `ReloadResourceEvent` | `TextureStitchEvent.Post` | Forge | Forge | OK |
| `RenderHeadShotAABB` | `RenderLivingEvent.Post` | Forge | Forge | OK |
| `TooltipEvent` | `ItemTooltipEvent` | Forge | Forge | OK |
| `GunPackResourcePack` | `SoundLoadEvent`（Forge）+ `TickEvent.ClientTickEvent`（FML） | 双总线 | 自注册，`registerBusOnce()` 各总线各一次 | OK |
| 12 个 `client/input/*Key` | `TickEvent.*` 或纯静态工具 | FML / 无需 | `ShootKey/AimKey/CrawlKey/ReloadKey/GunInputHandler` 已注册，其余无 handler | OK |

补充核对：
- **无重复注册**：`cpw/mods/fml/common/eventhandler/EventBus.java:47-50`
  `if (listeners.containsKey(target)) return;` —— 同一实例重复 `register` 直接返回，
  所以 `SoundPlayManager` 自注册 + `ClientProxy` 注册同名类不会双跑；
  且 `ClientProxy` 反射构造的是**外层类实例**（无 `@SubscribeEvent` 方法），真正干活的 `TICK_HANDLER` 只被自注册一次。
- **`registerBusHandler` 不会因注册失败崩客户端**：`EventBus.register` 在**没有**任何 `@SubscribeEvent` 方法时
  只是登记 owner 后返回（`EventBus.java:60-94` 无兜底抛异常分支），只有「参数不是单参 / 不是 Event」才抛
  `IllegalArgumentException`；全仓 handler 均为 `public void`（`getMethods()` 可见）单参 Event，不存在该风险。
- 所有 handler 都是**实例方法**（上游的 `public static` 全部改成了 `public void`），符合 `ASMEventHandler` 用
  `INVOKEVIRTUAL` 的要求，无 `IncompatibleClassChangeError` 风险。

---

## 3. GL / 线程安全 —— 通过

- 全作用域 `GL11/GL12/GL13` 直接调用只出现在 4 处，且都在**渲染回调内**：
  `RenderHeadShotAABB`（`RenderLivingEvent.Post`）、`GunSmithTableScreen:1143-1152`（`drawGuiContainerBackgroundLayer`）、
  `GunPegboardHighlightEvent:113-124` / `GunDisplayCaseHighlightEvent:133-144`（`DrawBlockHighlightEvent`）。
- `bindTexture` / `Tessellator` / `getTextureManager` 全部在渲染路径（`model/**`、`gui/**`、`renderer/**`）。
  `client/particle/**`、`client/sound/**`（tick / 网络回调路径）**没有任何 GL 调用**。
- `GunPackResourcePack.scheduleTextureReload()` 用 `Minecraft#func_152344_a`（= `addScheduledTask`）把
  `TextureManager#onResourceManagerReload` 排到主线程执行，**没有**在监听器回调里直接动 GL —— 正确。

---

## 4. 未修风险 / 降级（只报告，不在本作用域或不宜大改）

| # | 位置 | 级别 | 说明 |
|---|---|---|---|
| R1 | `item/ModernKineticGunItem.java:117`（**item 作用域**） | **P2 真 bug** | `registerIcon("minecraft:bow")` → 解析成 `minecraft:textures/items/bow.png`，**1.7.10 原版没有这张图**（实测 `build/rfg/minecraft-src/resources/assets/minecraft/textures/items/` 只有 `bow_standby.png` / `bow_pulling_0..2.png`）。这就是 `build/r7run.log:442` 唯一一条 `minecraft` 域 TEXTURE ERROR 的**代码级根因**。建议改 `"minecraft:bow_standby"`（或换成 `tacz:` 自己的图标）。同处 `AttachmentItem→compass`、`AmmoItem→arrow` 两张图 1.7.10 **存在**，无需改 |
| R2 | `block/*.java` 的 `setBlockTextureName("tacz:block/xxx")`（**block 作用域**） | P3 仅注释残留 | `r7run.log:455-463` 的 9 条 `tacz` 域缺图（`textures/blocks/block/statue.png` …）来自旧的图标注册路径（block 图集会把 sprite 名**再前缀** `textures/blocks/`，所以 `tacz:block/statue` 会去找 `textures/blocks/block/statue.png`）。现各 `registerBlockIcons` 已改成 `registerIcon("tacz:statue")`（对应真实存在的 `assets/tacz/textures/blocks/statue.png`），**应已修复**；但 `setBlockTextureName("tacz:block/…")` 仍是错的字符串，建议后续清扫，避免将来有人去掉 `registerBlockIcons` 时复发。请在下一次构建后复核该 9 条是否消失 |
| R3 | `client/resource/ClientAssetsManager.java:287-305`（**Lead 独占**） | 已修 | 崩溃链 `GuiContainerCreative.initGui → CreativePackFilters.init → updateTab → ClientAssetsManager.getPackInfo` 的 NPE（`this.packInfo` 为 null）已由 Lead 加空判定。本作用域内 **7 个** `getPackInfo` 调用点已在修复后逐一复核为**自身即空安全**：`CreativePackFilters:247`（`info == null` 分支）、`GunSmithTableScreen:585/881/983`、`GunPackList:101`、`ClientGunTooltip:333`、`ClientBlockItemTooltip:66`、`ClientAttachmentItemTooltip:99` |
| R4 | `SoundPlayManager` javadoc（`:584-599`）声称「`EventBus#register` 对同一实例直接返回」 | 无问题 | 已核对源码为**真**（`EventBus.java:47-50`）。保留原样 |
| R5 | `client/input/GunInputHandler.java:139-152` | P3 逻辑瑕疵 | 「按下沿」命中后 `break` 会**跳过后续 binding 的 `LAST_DOWN` 刷新**。若两个新按下的键落在同一 tick，后一个的沿会被推迟到下一 tick 才触发（不会重复触发）。实际影响极小（人手不会同 tick 按两键），改动会变更输入时序，故**只报告不改** |
| R6 | `client/sound/GunPackResourcePack.java:365-392`（行号已变，见 §1b） | **已修（P1）** | 原「靠 SoundLoadEvent + 40 tick 自愈重挂」→ 已改为 `IResourceManagerReloadListener` 主路径，见 §1b |
| R7 | `client/sound/GunPackResourcePack.java` `scheduleTextureReload` | P3 视觉 | `TextureManager#onResourceManagerReload`（`TextureManager.java:163-175`）内部必定 `ProgressManager.push("Reloading Texture Manager")`，会在重挂时闪一次进度条。这是 1.7.10 唯一的公开重载入口，无替代 |
| R8 | `client/gui/GunSmithTableScreen.java:1166` | P3 视觉 | 「手上无匹配材料」空态面板用 `blitNineSliced` 10 参重载，`mcshim` 把它降级为「取源图左上 `19×19` 整块拉伸到 `208×(ySize+1)`」（`mcshim/GuiGraphics.java:348-353`）。上游该调用本身参数就是退化的（`right=107 > textureSize=19`），因此**结果与上游等价**，但观感仍需实机确认，未改 |
| R9 | `client/gui/overlay/HeatBarOverlay.java:69,71` | P3 | `heatMax` 未做 `<= 0` 守卫，异常数据会算出 Infinity/NaN（`fill` 宽度变 0，不崩）。上游同样没有守卫，保真 |
| R10 | `client/gui/AmmoStorageScreen.java:272-274` | P3 | `menu()` 直接强转 `inventorySlots` 为 `AmmoStorageMenu`；若有别处误用本屏幕会 `ClassCastException`。上游 `AbstractContainerScreen<T>` 是泛型天然安全，1.7.10 无法表达。仅当 GUI handler 传错容器才触发 |
| R11 | `SimpleReloadableResourceManager.java:38-54`（原版无去重） | P3 残留 | `reloadResourcePack(IResourcePack)` 不做去重：同一 `IResourcePack` 实例会被重复追加进各 domain 的 `FallbackResourceManager`。一次 `reloadResources` 里新主路径（reload listener）与 `SoundLoadEvent` 兜底各 install 一次 → 该 domain 包列表里本包出现 2 次。查找是「从后往前」且同一实例，功能无影响，且每次重载 `clearResources()` 会整体丢弃重来。若要收口，可让 `onSoundLoad` 在「本轮 reload listener 已生效」时跳过 |

---

## 5. 覆盖清单（本作用域 93 文件）

**逐行精读（23）**
`ClientTickHelper`、`init/ClientSetupEvent`、`init/ModContainerScreen`、`event/ReloadResourceEvent`、
`event/TickAnimationEvent`、`event/RenderCrosshairEvent`、`gui/components/CreativePackFilters`、
`gui/components/GunPackList`、`gui/AmmoStorageScreen`、`gui/GunPackProgressScreen`、`gui/compat/ClothConfigScreen`、
`gui/overlay/HeatBarOverlay`、`input/GunInputHandler`、`sound/GunPackResourcePack`（含 §1b 改动）、
`sound/GunPackResourcePackReloadListener`（新增）、`sound/SoundPlayManager`(后半)、
`particle/MuzzleParticleManager`(前 140 行)、`gui/GunSmithTableScreen`(关键段 345-404 / 585 / 881 / 983 / 1143-1170)、
`gui/GunRefitScreen`(200-249)、`gameplay/*`(经 grep 逐点核对 20 处 `TimelessAPI` / `getPackInfo` 调用)、
`tooltip/*`(4 个文件 `addPackInfo` + `getPackInfo` 点)、`init/ParticleFactoryRegistry`、`init/ModEntitiesRender`

**结构化审查（71）**
- 全部 20 个 `client/event/*` 类：按 §2 方法逐个核对 handler 参数类型 / 总线归属 / 实例方法形态
- 全部 12 个 `client/input/*`：键位注册 + `@SubscribeEvent` 归属 + 边缘检测
- 全部 5 个 `client/gui/overlay/*`：渲染守卫（`hideGUI` / `thePlayer` / `getHeldItem` 空判定）
- 全部 4 个 `client/sound/*`：资源缓存 / 自注册 / 线程
- 全部 7 个 `client/particle/*`：GL 调用位置、空判定
- 全部 8 个 `client/gui/components/**` + 3 个 `toast`/`compat`：`get(0)` 越界（`CreativePackFilters:291` 构造上非空、
  `GunRefitScreen:224` 有 `isEmpty()` 守卫、`GunSmithTableScreen:374` 有 `isEmpty()` 守卫）、`initGui`/`drawButton` 生命周期

**机械验证**
- `lint_files(min_severity=error)` 覆盖全部 93 文件 → 仅 F3 一处 ERROR（已修），其余 **0 error**
- `grep "textures/[a-z/]*\.png"` 全作用域 29 处，逐个 `Test-Path` 与 1.7.10 原版贴图目录比对 →
  仅 F1 / F2 两处缺失（已修）；`textures/gun/uv/ak47.png`（`GunPackResourcePack:427` 诊断探针）不在 jar 内，
  但由枪包 `GunPackResourcePack` 提供，属预期
- `grep GL11/GL12/GL13|bindTexture|Tessellator` → 无渲染线程外调用
- `grep getPackInfo|getPackInfoReloadVersion|ClientAssetsManager.INSTANCE` → 131 命中逐点核对

---

## 6. 未覆盖 / 建议后续

- `gui/GunSmithTableScreen` 1229 行中仅精读关键段（initGui 前后、recipe 筛选、绘制入口、空态）。
  配方按钮 `ResultButton`/`TypeButton` 的点击→合成包发送链路未逐行核；建议下一轮配合 `inventory/**` 作者一起验。
- `gui/GunRefitScreen` 665 行同理，`HSVSliderGroup` / `GunPropertyDiagrams` 只做了 GL/资源路径核对。
- `particle/MuzzleParticleManager` 772 行只精读了入口与前 140 行（SBM snowstorm 接线）——它与
  `simplebedrockmodel` vendor 强耦合，建议由 render 负责人在实机确认枪口火光。
- `FirstPersonRenderGunEvent`(525) / `CameraSetupEvent`(457)：只做了总线归属与目标准确性核对（均正确），
  内部镜片/后坐力数学未验。

---

# 第二轮：同类崩溃专项（上游 `ItemStack.EMPTY` → 1.7.10 空栈=null，读取点未判空）

触发崩溃：`NullPointerException: Cannot invoke "ItemStack.getItem()" because the return value of "Supplier.get()" is null`
@ `entity/shooter/LivingEntityAim.java:98`（Lead 已在该处改用 `getCurrentGunItem()` + 判空）。
本轮只找/修**会导致崩溃**的同类问题，不做泛化美化。

## 2.1 结论速览

| # | 项 | 结论 |
|---|---|---|
| 1 | `getHeldItem()` / `getStackInSlot()` 读取点（本作用域 **84** 处） | **全部已判空**，无新增崩溃点 |
| 2 | `.getItem()` 解引用（**76** 处） | **全部**前置 null 判定或上游语义安全（见 2.2） |
| 3 | `GunDisplayInstance#getAnimationStateMachine()`（**9** 处调用） | **全部已判空** |
| 4 | `getStateMachineParam()` / `getControllableData()` / `getExternalGunModel()` / `getExternalLodModel()` | **本作用域 0 个调用点**（详见 2.3） |
| 5 | `objectMouseOver`（**4** 处非注释解引用） | **全部已判空** |
| 6 | `KeepingItemRenderer.getRenderer().getCurrentItem()`（6 处） | 结果已判空；`getRenderer()` 本身在游戏内恒非 null（见 2.3） |
| 7 | **已修**：`SoundPlayManager` 15 个 `play*` 公共入口 | 补 `entity`/`display`/`gunData` 判空（跨包共享边界，见 2.4-F5） |
| 8 | **已修**：`ClientAmmoCount.get(...)` 公共入口 | 补 `player`/`gun`/`iGun` 判空（见 2.4-F6） |

## 2.2 逐项核对（本作用域，未改动）

**A. `getHeldItem()` / `getStackInSlot()` —— 84 处全部安全。** 判空形态统计：
- `if (stack == null \|\| !(stack.getItem() instanceof IGun x))` 或 `if (!(mainHandItem != null && ...))` —— 绝大多数；
- `IGun.getIGunOrNull(stack)` 后判 `iGun == null` —— `GunRefitScreen:250/343`、`GunAttachmentSlot:82/158/167`、
  `GunPropertyDiagrams:49`、`CrawlKey:77`、`RefitKey:49`、`LocalPlayerDraw:61`、`LocalPlayerShoot:419`；
- 只看是否为空：`GunDisplayCaseClientInteractionEvent:98/110`（`getHeldItem() != null`）、`holdingGun:146`；
- 传给已声明的 null 容忍者：`InventoryEvent:77/80/121` → `IClientPlayerGunOperator#draw(null)`（该分支已显式用 `null`，
  语义就是空手收枪）、`InteractKey:105` → `PlayerControllerMP#onPlayerRightClick(...)`（原版 `Minecraft#rightClickMouse`
  本身就会传 null 空手栈）；
- 逐个复核过的**callee 空安全**（返回值直接进下游、但下游先判空）：
  `ClientIndexManager#warmUpItem(null)`（→ `warmUpItemForUse`，同文件已显式传 `null` 用于跳过副手）、
  `GunPegboardItemConfigRead#canPlace(null)`（`if (stack == null || stack.getItem() == null) return false`）、
  `SlotAdapterHelper#getAvailableSlotAdapterIndices(null, …)`（`getHostGunId` 返回 null → `emptyList`）、
  `SoundPlayManager#playerRefitSound(null, …)`（`getIAttachmentOrNull` null → return）、
  `FirstPersonRenderEvent#matches(null, stack)`、`LocalPlayerInspect#getItemRenderer(null)`（`stack == null` → null）、
  `TickAnimationEvent#getItemRenderer(null)`（同上）、`TimelessAPI.getGunDisplay(null)`（返回 `Optional.empty`）。

**B. `.getItem()` —— 76 处全部有前置判定。** 唯一的"中间表达式"是
`GunRefitScreen:434-435`（`button.getAttachmentItem().getItem()`），其外层 `:425` 已用 `isEmpty(stack)`
（`stack == null || stack.getItem() == null`，`GunRefitScreen:661`）守住，且同一表达式内不会变化。

**C. 状态机 / 数据 null（`GunDisplayInstance` 的 `@Nullable` 取值点）—— 全部已判空：**
`TickAnimationEvent:64`、`RenderCrosshairEvent:139`、`LocalPlayerBolt:91`、`LocalPlayerMelee:108/121/135`、
`LocalPlayerUnload:91`、`LocalPlayerShoot:368`、`LocalPlayerReload:125/142`、`LocalPlayerInspect:78`、
`LocalPlayerFireSelect:65` —— 一律 `if (x != null)` 或先 return。

**D. `orElse(null)` 30 处** —— 全部在使用前判空（`CameraSetupEvent:274/329` 的 `curve == null ||`、
`GunHudOverlay:90`、`HeatBarOverlay:61`、`LocalPlayerBolt/Inspect/Reload/Unload`、`MuzzleParticleManager:151/692`、
`FirstPersonRenderGunEvent:365/373`、`ShellRender:193`、`ClientGunTooltip:206/215/261` 等）。

**E. `objectMouseOver`** —— `ClientPreventGunClick:74`、`GunDisplayCaseClientInteractionEvent:118`、
`InteractKeyTextOverlay:57`、`InteractKey:77` 全部先判 `== null`。

**F. `Minecraft.thePlayer` 直链** —— `GunSmithTableScreen:553/1030`、`AmmoStorageScreen:123` 均已判空；
`HeatBarOverlay:86` 的 `mc.ingameGUI.getUpdateCounter()` 在游戏内恒非 null。

## 2.3 明确排除（不是崩溃点，不改）

- **`KeepingItemRenderer.getRenderer()`**：`(KeepingItemRenderer) Minecraft#entityRenderer.itemRenderer`
  （`api/client/other/KeepingItemRenderer.java:29-31`）。`itemRenderer` 在 `Minecraft.java:545` 创建、`entityRenderer` 在 `:547`，
  而 6 个调用点全在第一人称绘制/`RenderTickEvent` 回调里 → 游戏内恒非 null。其**返回值**已全部判空
  （`FirstPersonRenderEvent:71`、`FirstPersonRenderGunEvent:127` 用 `getIGunOrNull`、`CameraSetupEvent:191/…` 用 `getItemRenderer`）。
- **`getControllableData()`**：本作用域 `client/gui/overlay/**` **没有调用点**（只有 `GunDisplayInstance:426` 自己消费）。
  Lead 提到的 "gson `null` 覆盖默认值" 在本作用域的等价物是 `GunData#getReloadData()`/`getFireSound()`，
  但 `GunData.java:82/85` 有非 null 默认值（`new FireSound()` / `new GunReloadData()`），且与上游同构
  → 只有枪包 JSON 显式写 `"reload": null` 才触发，属**上游同构风险**，非移植 bug，不改。
- **`getExternalGunModel()` / `getExternalLodModel()`**：本作用域 0 个调用点（调用点在
  `client/renderer/external/ExternalGunAppearance:102/108/175`、`client/renderer/block/DisplayGunGeometry:228`，归 render-review）。
- **`getStateMachineParam()`**：唯一消费者 `client/animation/statemachine/GunAnimationStateContext:508` 已做
  `display == null ? null : display.getStateMachineParam()`，非本作用域。

## 2.4 本轮改动（2 个文件）

| # | 文件:行 | 崩溃条件 | 修法 |
|---|---|---|---|
| F5 | `client/sound/SoundPlayManager.java` 15 个公共入口（`playShootSound:319`、`playSilenceSound:337`、`playDryFireSound:355`、`playReloadSound:385`、`playInspectSound:406`、`playBoltSound:427`、`playDrawSound:439`、`playPutAwaySound:451`、`playFireSelectSound:463`、`playMeleeBayonetSound:475`、`playMeleePushSound:488`、`playMeleeStockSound:501`、`playHeadHitSound:521`、`playFleshHitSound:538`、`playKillSound:555`） | `TimelessAPI.getGunDisplay(...)` 在「枪包资源未就绪 / 该枪不在客户端索引」时返回空 → 调用方把 `null` 传进来，方法体内 `display.getSounds(...)` / `entity.getRNG()` / `gunData.getFireSound()` 直接 NPE。本类是**跨包共享入口**（`client/gameplay/**`、`client/renderer/**`、`api/client/animation/**`、`client/event/**` 都调），是唯一合适的收口点 | 每个入口顶部 `if (entity == null \|\| display == null) return;`；带 `gunData` 的两个再加 `gunData == null \|\| gunData.getFireSound() == null`。已核 `playClientSound` 本身接受 `@Nullable SoundData`，所以只需挡住前置解引用 |
| F6 | `client/gameplay/ClientAmmoCount.java:44-52` | `get(player, gun, iGun)` 在 `iGun.useDummyAmmo(gun)` 处解引用；调用方用 `IGun.getIGunOrNull(可能为 null 的手持栈)` 的结果直接传入即 NPE。同属跨包共享入口（`gameplay/overlay/animation/model/papi` 都调） | 入口加 `if (player == null \|\| gun == null \|\| iGun == null) return 0;`（返回 0 = 读不到弹药数，比 `∞` 语义更保守） |

修后：`lint_files(min_severity=error)` 对两文件 **0 error**；未运行任何 gradle 任务。

## 2.5 其它作用域（只报告）

- `client/model/BedrockGunModel.java:73` 的 `currentGunItem` 字段无 `@Nullable` 标注，且被 `:95/:99/:103` 的
  `IFunctionalRenderer` lambda 以 `iGun.hasBulletInBarrel(currentGunItem)` 形式捕获；`iGun` 来自 `:482` 的
  `getIGunOrNull(currentGunItem)`（已判空），但 lambda 执行时机晚于捕获，若期间 `currentGunItem` 被置 null 则边界不清 ——
  建议 render-review 复核 `setCurrentGunItem`/`render` 的生命周期（**未在本作用域**）。
- `api/client/other/KeepingItemRenderer#getRenderer()` 返回强转结果，未判 `entityRenderer`/`itemRenderer`——
  游戏内安全，但若被非渲染路径调用会 NPE（属 api 作用域）。
- `GunData#getReloadData()` / `getFireSound()` 缺 `null` 防护（gson 显式 `null` 可覆盖默认值），
  与上游同构，建议统一在 `resource/pojo/**` 决定是否加 `Objects.requireNonNullElse`。

