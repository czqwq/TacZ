# r7-respack-client · 客户端资源加载 / 索引 / 自定义资源包生效 上游等价性审查

> 任务：`task-27`（r7 轮，客户端资源包）。写作用域：`src/main/java/com/tacz/guns/client/resource/**`
> （排除 `pojo/model/**`、`pojo/animation/**`、`pojo/display/**`，其中 `display/gun/{Rangefinder,RangefinderManager,TextShow}.java`
> 属 r7-crosshair、`display/attachment/ScopeLensConfig.java` 属 r7-scope、其余 `pojo/{model,animation,display}` 属 r7-model）
> + 本报告。
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/resource/**`（85 文件）及被引用到的
> `client/init/**`、`resource/**`、`network/message/**`；1.20.1 原版 `tmp/minecraft-src-1.20.1/**`；
> 平台准绳：`build/rfg/minecraft-src/java/**`（1.7.10 + Forge 10.13.4.1614 反编译源）。
>
> **纪律**：未运行任何 gradle 任务；未改 `tmp/**`、`build/**`、`run/**`、`src/main/resources/**`；
> 未 git checkout/stash/reset/commit。改动文件 `mcp__idea__lint_files(min_severity="error")` = **0 error**；
> 本 scope **27 个 java 全量 lint** = **0 error**；scope 内 `[TACZ-DBG]`/`System.out`/每帧打印 = **0**。
> 历史报告（`docs/review/02-client-resource.md`、`r5/data-wiring.md` 等）已定案项不重复展开，只在结论表/复核行引用。

---

## 1. 结论速览

| 链路 | 结论 | 关键证据 |
|---|---|---|
| 客户端数据管理器（display×7 / geo_models / animations / gltf / scripts / packInfo / ads_presets） | **与上游等价**（仅差有据平台替换，见 §6） | 上游 `ClientAssetsManager.java:98-120` ↔ 本地 `:142-225`；注册顺序逐项一致；上游 `DisplayManager.apply` ↔ 本地 `:39-55` |
| 客户端索引 7 类（gun/ammo/attachment/attachment_variant/slot_adapter/block + display 实例） | **等价**；加载顺序 = 上游 `loadGunDisplay → loadGunIndex → … → preloadExternalModels → warmUpInventoryModels` | 上游 `ClientIndexManager.java:59-84/86-310` ↔ 本地 `:63-130/132-388` |
| 加载时序（首次进世界前是否就绪） | **就绪**：客户端枪包数据在加载期最后一次 `refreshResources`（FML `finishMinecraftLoading:327`）里装完，早于主菜单/进世界；索引由登录同步包重建 | §4.1 |
| 重载时旧资源释放 | **闭合**：`ClientIndexManager.clear()` 作废 asset 代次 + 清 7 张表；数据管理器先清后装；旧 `GunDisplayInstance` 释放；首人称状态 reset | 本地 `ClientIndexManager.java:46-61`、`:63-84`；`ClientAssetLoadDispatcher.invalidate` |
| `/tacz reload`（单人） | **本轮修复 3 处真实偏离**：①客户端重载跑在集成服 Server thread（1.7.10 API 非线程安全）②缺 1.20 `PackRepository#reload`⇒枪包重扫（新增包不生效）③服务端数据重载后客户端索引不重建（上游靠 `OnDatapackSyncEvent`） | §2.1 |
| 语言（自定义包 lang 覆盖/新增） | **本轮修复**：枪包 `.lang` 早先只能「补缺」不能覆盖本体；现按上游语义（枪包优先） | §2.2 |
| 自定义枪包客户端生效链路 | **目录/zip 枪包在启动期可生效**（默认枪包本身就是导出到 `<gamedir>/tacz/` 的外部包，运行日志证明贴图/音效/模型/语言/粒子全链可用）；`/tacz reload` 热加载在本轮修复后应生效（需实机验证） | §5、`fml-client-latest.log:3086-3105/4416-4418` |
| 动画粒子名归一 | 修正 `ClientAmmoIndex` 与 `GunDisplayInstance` 不一致（namespace 前缀处理） | §2.3 |
| 跨模块缺口 | 3 项（服务端 reload 不回传 sync；模组内嵌 assets/data 不被扫描；zip 句柄跨端共享） | §5.4 |

**本轮改动 3 个文件**：`ClientAssetsManager`、`ClientGunPackReloader`、`index/ClientAmmoIndex`（见 §2）。
**需 Lead 转发给其它 scope 的事项**见 §5.4（尤其第 1 条影响多人服自定义包热更新）。

---

## 2. 本轮修复（真实移植偏离）

### 2.1 `ClientAssetsManager#reloadAllPack`：`/tacz reload` 客户端路径的三处偏离（P1）

**上游形态**（`tmp/.../client/resource/ClientAssetsManager.java:206-217`）：

```java
Minecraft.getInstance().reloadResourcePacks().get();          // ① 客户端资源重载（阻塞）
if (ServerLifecycleHooks.getCurrentServer() != null) {        // ② 集成服存在时
    CommonAssetsManager.reloadAllPack();                      //    服务端 datapack 重载
}
```

**移植前实现**（git 基线）：`Minecraft.getMinecraft().refreshResources();` + 同样的 `if` 分支。

三处偏离：

1. **线程（P1，会崩/会污染状态）**：`/tacz reload` 的命令对象是 1.7.10 `CommandBase`，注册在
   `CommonProxy#serverStarting` 的**服务端**命令表（`CommonProxy.java:182-186` → `CommandRegistry`），
   单人游戏里由集成服 **Server thread** 执行；而 `Minecraft#refreshResources` 会直接改客户端状态并触发
   `TextureManager#onResourceManagerReload`（`TextureManager.java:163-175`，内部 `TextureUtil` 做 **GL 上传**）
   与 `renderGlobal.loadRenderers()`（`Minecraft.java:669-672`）。1.7.10 这些都不是线程安全的；
   上游 1.20 的 `reloadResourcePacks()` 则把重载提交给 client 线程的 `gameExecutor`、调用线程只 `.get()` 等待
   （`tmp/minecraft-src-1.20.1/.../Minecraft.java:846-882`：`setOverlay(new LoadingOverlay(..., this /*gameExecutor*/ ...))`）。
   ⇒ 1.7.10 的等价物：`Minecraft.func_152344_a(Runnable)`（= `addScheduledTask`，
   `Minecraft.java:2978-3015`：已在 client 线程则立即执行、否则入队；队列由 client 线程每 tick 开头清空，
   `Minecraft.java:1658-1669`）+ `.get()` 阻塞等待。
2. **枪包重扫缺失（P1，新增/删除包不生效）**：1.20 `reloadResourcePacks()` 先 `PackRepository#reload()`
   （`Minecraft.java:857`），后者回调 `AddPackFindersEvent` → `GunPackLoader#loadPacks` 重新扫描
   `<gamedir>/tacz/**`（上游 `GunPackLoader.java:57-63/65-134`）。1.7.10 没有资源包自动发现，移植前只重载
   **旧**枪包列表 ⇒ 新放进 `tacz/` 的包不会出现在客户端（服务器数据侧因 `CommonAssetsManager.reloadAllPack`
   里会重扫而"半生效"，客户端则完全没有 display/model/贴图）。
   ⇒ 显式 `GunPackLoader.INSTANCE.reloadPackList()` + `GunPackResourcePack.invalidateCache()`
   （否则其 `packIndex` 里的 namespace/文件集合是旧的，新包 namespace 不会被挂进 `IResourceManager`）。
   同时补 `GunPackLoader.injectAllGunPackLanguages()`：新包 namespace 的 `.lang` 不在本轮 `LanguageManager`
   重载的 domain 集合里（`LanguageManager` 比枪包重挂先跑，见 §2.2），不注入就只能等下一次冷启动。
3. **客户端索引不重建（P1，数据新旧不一致）**：上游服务端重载后由 `OnDatapackSyncEvent`
   （上游 `CommonAssetsManager.java:331-341`）把新同步包发给所有玩家，客户端 `syncGunPack`
   （上游 `ServerMessageSyncGunPack.java:87-109`）`CommonNetworkCache.fromNetwork(...)` + `ClientIndexManager.reload()`。
   移植的 `CommonAssetsManager.reloadAllPack()`（本地 `:560-563`）只重扫+`reload()`，**没有重发同步包**（跨模块，§5.4#1），
   于是单人游戏里客户端 `GUN_INDEX` 停留在旧数据。⇒ 服务端数据重建后，在 client 线程显式补一次
   `ClientIndexManager.reload()`（幂等；多人服路径不经过本方法，见 §4.4）。

**修后形态**（`ClientAssetsManager.java:324-383`）：

```
reloadAllPack()
 ├─ client 线程（addScheduledTask + get）:
 │    GunPackLoader.reloadPackList()            // ① 等价 PackRepository#reload → GunPackLoader#loadPacks
 │    GunPackResourcePack.invalidateCache()     // ① 枪包 assets 索引作废
 │    GunPackLoader.injectAllGunPackLanguages() // ① 新 namespace 的 lang 兜底
 │    Minecraft#refreshResources()              // ① 原版重载（内含语言补偿/数据管理器/索引，§4.3）
 └─ if (MinecraftServer.getServer() != null):
      CommonAssetsManager.reloadAllPack()       // ② 服务端数据重载（Server thread，与上游同线程语义）
      client 线程: ClientIndexManager.reload()  // ② 补上游 OnDatapackSync → syncGunPack 的缺环
```

* **回滚点**：整段替换回原 5 行实现即可；新增的私有 helper `runOnClientThreadAndWait` 一并删除。
* **风险/边界**：单人游戏 `/tacz reload` 期间集成服 Server thread 会阻塞在 `.get()`（与上游 `.get()` 同语义），
  客户端 tick 照常推进 ⇒ 不会死锁（重载链内不回调服务端线程/不等待服务端锁；`ClientIndexManager.reload()`
  里的发包只是 `NetHandler#addToSendQueue` 入队，`LocalPlayerDraw.java:92-98`）。

### 2.2 `ClientGunPackReloader#refreshGunPackLanguages`：枪包 `.lang` 的覆盖顺序（P2）

**问题**：1.7.10 `Minecraft` 构造器里注册的**第一个** reload listener 是 `LanguageManager`
（`Minecraft.java:520-521`），而枪包 `IResourcePack` 只能在 mod **preInit** 里挂载（`ClientProxy.java:186` ⇒
`GunPackResourcePackReloadListener`，注册晚于 `LanguageManager`）。`SimpleReloadableResourceManager#notifyReloadListeners`
按注册顺序回调（`:134-145`），所以每轮 `refreshResources` 里 `LanguageManager#onResourceManagerReload`
（`LanguageManager.java:78-90` → `Locale#loadLocaleDataFiles`，`Locale.java:32-59）执行时，
枪包资源包还没被重新挂上（`clearResources()` 已清空，`:89-118`）⇒ **本轮 Locale 只读到模组 jar 里的基础 lang**。
`injectAllGunPackLanguages` 走的是 `LanguageRegistry#injectLanguage` + `mergeLanguageTable`
（`LanguageRegistry.java:244-254/160-190`），后者 **只补缺不覆盖** ⇒ 自定义包想覆盖本体已有 key
（如改名默认枪）、或只存在于包内的翻译，原本都到不了语言表。
上游 1.20 同一次重载中枪包就在资源包列表里，语言按「枪包覆盖本体」优先级加载。

**修法**（`ClientGunPackReloader.java:102-143`）：`reloadAll()` 开头（此时枪包已被
`GunPackResourcePackReloadListener` 重挂、`setResourceDomains` 已含枪包 namespace）重跑一次
`LanguageManager#onResourceManagerReload(Minecraft#getResourceManager())`。`Locale#loadLocaleDataFiles`
对每个 domain 走 `getAllResources`（低→高优先级，`FallbackResourceManager.java:68-93`），枪包最后挂载 ⇒
包里 lang 最后写入 ⇒ 覆盖基础 lang，与上游一致；随后数据管理器里的 `StatCollector` 翻译判定
（如 `ClientAttachmentIndex.java:176-182` 的 `%ammo_count%`）也能看到包内文本。

* **回滚点**：删除 `reloadAll()` 中 `refreshGunPackLanguages()` 调用及该私有方法。
* **成本**：每次原版资源重载多一次语言装载（读 ~60 个 `.lang`，毫秒级；仅在启动/手动重载/F3+T 发生）。

### 2.3 `ClientAmmoIndex#checkParticle`：粒子名 namespace 归一（P3）

上游用 `ParticleArgument.readParticle(...)` 解析 `name`（`minecraft:smoke` 与 `smoke` 等价，都会解析成
`minecraft:smoke`）。移植后 `GunDisplayInstance#parseParticleName`（`GunDisplayInstance.java:688-702`）会去掉
可选 `namespace:` 前缀（1.7.10 `EntityFX` 只认 path），但 `ClientAmmoIndex` 直接保存原始名字 ⇒
带 namespace 的粒子名走枪械路径正常、走弹药路径失效（且不再回退到枪械粒子）。本轮统一为同样的归一；
path 为空时按上游「`readParticle` 抛 `CommandSyntaxException` → 不装配粒子」的语义直接不装配
（`ClientAmmoIndex.java:235-262`）。默认数据（裸名 `smoke`/`mycelium`）行为不变。

* **回滚点**：恢复 `particle.setParticleOptions(name)`。

---

## 3. 逐文件对照（本 scope 27 文件）

上游 `client/resource` 共 85 个 java；本地 87 个（新增 `ClientGunPackReloader`（移植特有）与
`pojo/display/block/BlockItemTransforms`（替代 1.20 `ItemTransforms`））。对全部同名文件做归一化 diff
（去注释/import/空白后逐 hunk），有代码差异的文件全部逐 hunk 回溯上游原文与 1.7.10 源码行。

| 文件 | 结论 | 差异/依据 |
|---|---|---|
| `ClientAssetLoadDispatcher` | 等价 | 单线程 daemon executor + `AtomicLong` 代次作废，逐行同；`getNamespace()`→`getResourceDomain()` |
| `ClientAssetLoadTask` | 等价 | 逐行同（懒提交/失败回调/`getIfReady`/`isPending`） |
| `ClientAssetsManager` | **本轮修** + 有据替换 | 上游 `:98-120` ↔ `:142-225` 注册顺序逐项一致；GSON 适配器差异见 §6.6；`getXxx` 空安全护栏是移植安全网（非上游；已登记 r1 P1-2）；`ResourceLocationAdapter` 见 §6.7 |
| `ClientGunPackReloader` | 移植特有 | 上游 `RegisterClientReloadListenersEvent` 等价物；时序核对 §4.1；**本轮加语言补偿** |
| `ClientIndexManager` | 等价 | 差异仅：副手 `getOffhandItem`→无（传 null 跳过）、`draw(ItemStack.EMPTY)`→`draw(null)`（`LocalPlayerDraw` 已判空）、`Int2ObjectArrayMap`→`HashMap`、`FirstPersonRenderHandler.reset()` 位置有据（注释保留） |
| `ExternalModelAssets` | 等价 | record + 异步预加载逐行同 |
| `GunDisplayInstance` | 等价 | 差异：`MissingTextureAtlasSprite`→`ENTITY_EMPTY_TEXTURE`（§6.1）、`hotbarShow` 类型、brigadier 粒子解析→字符串（§6.2）、`Set.of/copyOf`→Collections 等价式、`InternalAssetLoader` 默认动画 null 护栏（r1 R-2）、`ParticleArgument` 上游笔误按等价语义补参 |
| `InternalAssetLoader` | 等价 | 上游 `Minecraft#getResourceManager#open`→`IResourceManager#getResource#getInputStream` 适配；`fillInStackTrace` 上游 bug 原样保留 |
| `index/ClientGunIndex` | 等价 | 仅格式/`CommonAssetsManager.get()` 链式 |
| `index/ClientAmmoIndex` | 等价 + **本轮粒子归一** | `MissingTextureAtlasSprite`→`minecraft:missingno`（§6.1）；`isNoneBlank(name)` 上游笔误修正 |
| `index/ClientAttachmentIndex` | 等价 | `Component`→`IChatComponent`、`I18n`→`StatCollector`、missingno、hiddenTextBones 等价式 |
| `index/ClientAttachmentVariantIndex` | 等价 | 同上 |
| `index/ClientSlotAdapterIndex` | 等价 | 同上 |
| `index/ClientBlockIndex` | 等价 | `ItemTransforms`→`BlockItemTransforms`（r7-model 负责 deserialize/apply 语义） |
| `index/ClientAttachmentSkinIndex` | 等价 | 仅格式 |
| `manager/DisplayManager` | 等价 | 上游两段式 `prepare/apply` 折叠为 `apply`；解析/`data.init()`/失败日志逐行一致 |
| `manager/GltfManager` | 等价 | `ASSETS_ROOT` 修正已在 r1 P1-1 落地（本轮复核在） |
| `manager/PackInfoManager` | 等价 | r1 P0-1（路径/namespace 键）复核在；`getNamespaces()+getResource(assets/<ns>/gunpack_info.json)` ↔ `listFiles("assets/")+namespaceOfPackInfo` 等价 |
| `serialize/AnimationKeyframesSerializer` | 等价 | fastutil `Double2ObjectRBTreeMap`→`TreeMap<Double,…>`（有序语义一致，§6.3） |
| `serialize/ItemStackSerializer` | 有据降级 | `CraftingHelper#getItemStack`→`item/count/damage/nbt` 手工解析（§6.4）；无 display POJO 使用该类型（实测 grep） |
| `serialize/ParticleEffectKeyframesSerializer` | 等价 | `ResourceLocation.tryParse`→本地 tryParse 适配 |
| `serialize/SoundEffectKeyframesSerializer` | 等价 | 同上；裸 `effect`→`minecraft:` 与上游一致（见 §5.2 日志说明） |
| `serialize/Vector3fSerializer` | 等价 | gson 2.2.4 无 `JsonArray(int)/set` → `add`（序列化输出相同） |
| `pojo/CommonTransformObject` / `pojo/TransformScale` / `pojo/PackInfo` / `pojo/skin/attachment/AttachmentSkin` | 等价 | `PackInfo` 的 `tryParse` 适配 1.20 API；其余仅格式 |

接口层（只读核对，属 r7-model/r7-crosshair/r7-scope）：`IDisplay#idToFile` 与 1.20
`FileToIdConverter#idToFile` 等价（`textures/<path>.png`）；`GunDisplay.init()` 的 texture/lod/muzzle 路径转换、
`BlockItemTransforms`、`AdsCurve`、`RangefinderManager` 均已在各自 r7 报告中处理，本轮只在调用点核对。

---

## 4. 加载时序（文字图，均带源码行）

### 4.1 客户端启动（关键结论：进世界前数据已就绪）

```
[Minecraft.startGame]
 ├ :519 mcResourceManager = new SimpleReloadableResourceManager
 ├ :520-521 LanguageManager 创建并 registerReloadListener   ← 第 1 个 reload listener
 ├ :522 FMLClientHandler#beginMinecraftLoading
 │   ├ :208 Loader#loadMods（构造 mod）
 │   ├ :238 client.refreshResources()            ← 第 1 次 reload（此时 mod 尚未 preInit，枪包没挂）
 │   └ :243 Loader#preinitializeMods()
 │        ├ CommonProxy#preInit :127-133
 │        │   GunMod.registerDefaultExtraGunPack（导出 jar 内默认枪包）
 │        │   GunPackLoader.INSTANCE.reloadPackList()   ← 扫描 <gamedir>/tacz/**（目录/zip）
 │        │   CommonAssetsManager.registerEventHandlers()
 │        └ ClientProxy#preInit :186
 │            GunPackResourcePack.ensureInstalled()
 │            → 注册 GunPackResourcePackReloadListener（此刻 textureMapBlocks==null ⇒ 早于 TextureManager）
 │            → SimpleReloadableResourceManager#reloadResourcePack（挂枪包 IResourcePack）
 ├ :523-524 TextureManager 创建并注册 reload listener
 ├ :527-528 SoundHandler 创建并注册
 ├ :539-548 FontRenderer / EntityRenderer 注册
 ├ :585-592 textureMapBlocks + blocks/items 图集 stitch
 │           → TextureStitchEvent.Post → ReloadResourceEvent#onTextureStitchEventPost
 │             → InternalAssetLoader.onResourceReload()（默认模型/默认动画）+ SoundPlayManager 缓存清理
 └ :597 FMLClientHandler#finishMinecraftLoading
     ├ :311 Loader#initializeMods()（init + postInit）
     │   ├ ClientProxy#init :197-270
     │   │   ClientSetupEvent.onClientSetup（渲染器/键位/HUD/语言注入 GunPackLoader.injectAllGunPackLanguages）
     │   │   ReloadCommand.setClientReloader(ClientAssetsManager::reloadAllPack)   ← /tacz reload 客户端入口
     │   └ ClientProxy#postInit :292 ClientGunPackReloader.init()
     │       ① ClientAssetsManager.reloadAndRegister（建 7 display + bedrockModel + bedrockAnimation + gltf
     │          + scriptManager + packInfo + ClientIndexManager::reload，顺序同上游 :98-120）
     │       ② 追加 ParticleDefinitionReloader（SBM 粒子定义，上游由 SBM 模组自行注册）
     │       ③ 注册 IResourceManagerReloadListener（register 的同步首回调被 skipNextCallback 丢弃）
     │       ④ FML 总线注册 tick 兜底（loadedOnce）
     └ :327 client.refreshResources()            ← 第 2 次 reload（加载期最后一次）
         reload listeners 按注册顺序：
           LanguageManager → GunPackResourcePackReloadListener（重挂枪包）
           → TextureManager（图集 + 已缓存 2D 贴图重载）
           → SoundHandler（SoundLoadEvent → GunPackResourcePack.onSoundLoad：重挂 + invalidateCache）
           → … FontRenderer / EntityRenderer …
           → ClientGunPackReloader#onResourceManagerReload
                [本轮] refreshGunPackLanguages()          ← 枪包 lang 按上游优先级装载（§2.2）
                逐 listener reload()：
                  gunDisplay/adsPresets/ammoDisplay/attachmentDisplay/attachmentVariantDisplay/
                  slotAdapterDisplay/blockDisplay/bedrockModel/bedrockAnimation/gltf/scriptManager/packInfo
                  → ClientIndexManager.reload()（本地 display + 当前 common 数据建索引）
                  → ParticleDefinitionReloader
```

⇒ **进主菜单/进世界之前**，客户端 display/model/animation/script/packInfo/索引已全部装载完毕；
单人游戏进世界时 `CommonProxy#serverStarting`（`:182-186`）才 `CommonAssetsManager.reload()` 建服务端数据，
玩家登录（`CommonAssetsManager#onPlayerLoggedIn :524-531`）下发同步包 → `ClientProxy.Hooks#syncGunPack :454-480`
→ `CommonNetworkCache.fromNetwork` + `ClientIndexManager.reload()`（用服务端 common index + 本地 display 重建索引）。

### 4.2 原版资源重载（F3+T / 语言切换 / `/tacz reload` 内部）

链路同 §4.1 的第 2 次 `refreshResources`，监听器顺序固定：
LanguageManager → 枪包重挂 → TextureManager（图集/缓存纹理）→ SoundHandler → … → `ClientGunPackReloader`。
「数据管理器 → 索引」的顺序由 `ClientAssetsManager#reloadAndRegister` 的注册顺序保证
（上游用 `barrier.wait(...).thenRunAsync(ClientIndexManager::reload, gameExecutor)` 实现同一语义）。
**旧资源释放**：`ClientAssetLoadDispatcher.invalidate()`（旧代次后台任务作废）+ 7 张索引表 clear +
各数据管理器 `apply` 先 clear 再装 + 旧 `GunDisplayInstance` 引用丢弃。

### 4.3 `/tacz reload`（单人游戏，本轮修复后）

```
[Server thread] RootCommand/ReloadCommand#processCommand
 └ isClient() → ClientAssetsManager#reloadAllPack
     ├ [client 线程，阻塞等待] GunPackLoader.reloadPackList → GunPackResourcePack.invalidateCache
     │                        → injectAllGunPackLanguages → Minecraft#refreshResources（§4.2 全链）
     └ [Server thread] CommonAssetsManager#reloadAllPack（重扫 + 重建 INSTANCE / networkSyncMessages）
         └ [client 线程] ClientIndexManager.reload（重建索引，补同步包缺环）
```

专用服务端执行 `/tacz reload` 只走 `CommonAssetsManager.reloadAllPack()`；**客户端不会刷新**（上游靠同步包，
见 §5.4#1）。

### 4.4 多人服登录/重连

`ServerMessageSyncGunPack#handle` → `ClientProxy.Hooks#syncGunPack`（`:454-480`）：
`acceptPart` 收齐全部分片 → 非单人（`Minecraft#isSingleplayer()`，等价上游 `!Connection#isMemoryConnection`）
时 `CommonAssetsManager.clearInstance()`（让 `get()` 走 `CommonNetworkCache`）→ `fromNetwork(cache)`
→ `ClientIndexManager.reload()` → `GunPackResourcePack.invalidateCache()`（移植补充，正确）。
`CommonNetworkCacheEvent` 在退出世界时清网络缓存。⇒ 断线重连/换服时索引会按新服务端数据重建。

---

## 5. 自定义资源包客户端侧生效链路（重点）

### 5.1 链路总表

| 内容 | 客户端加载器 | 数据源/路径 | 生效点 | 结论 |
|---|---|---|---|---|
| `gunpack_info.json` | `PackInfoManager`（`:53-97`） | `<pack>/assets/<ns>/gunpack_info.json`，键=namespace | 创造侧栏/工作台过滤器显示包名/logo | ✓ |
| gun/ammo/attachment/attachment_variant/slot_adapter/block display | `DisplayManager`（`JsonDataManager#reload`） | `<pack>/assets/<ns>/display/<kind>/**.json`，id=相对路径去 `.json` | `ClientIndexManager.loadGunDisplay` → `GunDisplayInstance.create` | ✓ |
| 基岩模型 | `LazyJsonDataManager<BedrockModelPOJO>` | `<pack>/assets/<ns>/geo_models/**.json` | `GunDisplayInstance.checkTextureAndModel`/`Index` 模型装配 | ✓ |
| 基岩动画 / gltf | `LazyJsonDataManager<BedrockAnimationFile>` / `GltfManager` | `assets/<ns>/animations/**.animation.json` / `**.gltf` | `GunDisplayInstance.checkAnimation` → `AnimationController` | ✓ |
| 动画状态机脚本 | `ScriptManager`（ASSETS_ROOT） | `assets/<ns>/scripts/**.lua` | `checkAnimation` 末尾 `LuaStateMachineFactory` | ✓ |
| 粒子定义（snowstorm） | `ParticleDefinitionReloader` | `assets/<ns>/particle_definitions/**.json` | SBM `ParticleDefinitionLoader` → 枪口火光/枪烟 | ✓（移植补充，见该类注释） |
| 模型/GUI/HUD 贴图、武器图标 | `IDisplay#init` 把 id 转成 `textures/**.png` → `GunPackResourcePack`（`IResourcePack`） | `<pack>/assets/<ns>/textures/**.png` | `TextureManager#bindTexture`（RenderType/GuiGraphics） | ✓ |
| 音效 ogg | `GunPackResourcePack#hasGunSoundResource` / `SoundPlayManager` | `<pack>/assets/<ns>/tacz_sounds/**.ogg` | `SoundPlayManager#playClientSound` | ✓ |
| 语言 | `GunPackLoader#injectAllGunPackLanguages` + **[本轮] `LanguageManager` 重跑** | `<pack>/assets/<ns>/lang/<locale>.lang` | `StatCollector`/`StringTranslate` | ✓（覆盖/新增都生效） |
| 服务端 index/data（枪械/弹药/配件数据） | 不是客户端本地加载：由服务端下发 `ServerMessageSyncGunPack` | `<pack>/data/<ns>/index/**`、`data/**` | `CommonNetworkCache` → `ClientIndexManager.loadGunIndex`… | ✓（多人服/单人服同一套） |

**跨包引用可用**：客户端数据管理器按「全枪包来源合并后的 id」建表，所以自定义包可以引用默认包里的共享资源
（如 `"state_machine": "tacz:default_state_machine_v2"`、`"ads_preset": "tacz:renew"`、`"use_default_animation": "pistol"`
→ `InternalAssetLoader` 的内置默认动画）。§5.3 的审查样例包正是这种形态。

### 5.2 运行日志证据（默认枪包 = 外部包）

`run/client/logs/fml-client-latest.log`（基线构建，未含本轮改动）：

- `:3086-3088` 启动扫描 `run/client/tacz` → Found 1 gunpack（**默认包是导出到磁盘后按外部包扫描的**）。
- `:3099-3103` `[TACZ ResourcePack] Installed … packs=1 [tacz] domains=[tacz] indexedFiles=3409` +
  4 个 probe 全 HIT（`textures/gun/uv/ak47.png`、其 `.mcmeta`、`tacz_sounds/dry_fire.ogg`、`textures/items/ammo_box.png`）
  ⇒ 枪包 `IResourcePack` 暴露、贴图/音效/合成 mcmeta 全链可用。
- `:4416-4418` SBM 粒子定义 `Loaded particle definition: tacz:muzzle_flash*.particle` → 枪口粒子链可用。
- `:3975` / `:4168` 20 个语种 340 条 key 注入 → 语言链可用。
- `:5819/5949/5970/6267/7712/8837` `Missing tacz_sounds resource and fallback sound event, skipped;
  sound=minecraft:p02_sm_mpapa5_raise…` —— **不是 bug**：这些来自基岩动画 `sound_effects` 的
  `"effect": "p02_sm_mpapa5_raise"`（裸名）。上游 `new ResourceLocation(裸名)` 同样默认 `minecraft:` 命名空间，
  枪包里也没有该 ogg ⇒ 上游同样「跳过播放」。与任何资源包加载失败无关。

### 5.3 审查样例包（`docs/review/r7/test-pack/tacz_review_pack`，r7-respack-core 提供）静态核验

按客户端加载器逐文件推演（未实机）：

| 包内文件 | 客户端解析结果 | 判定 |
|---|---|---|
| `gunpack.meta.json`（`namespace:tacz_review_pack`） | `GunPackLoader#fromDirPath` 识别为合法包 | ✓ |
| `assets/tacz_review_pack/display/guns/review_pistol_display.json` | id `tacz_review_pack:review_pistol_display`；`model→geo_models/gun/review_pistol_geo`、`texture→tacz_review_pack:textures/gun/uv/review_pistol.png`、`slot→…/textures/gun/slot/review_pistol.png`（`IDisplay#idToFile` 换算）；`transform.scale` 对应 `TransformScale.thirdperson/ground/fixed` | ✓ |
| `…/geo_models/gun/review_pistol_geo.json` | `format_version 1.12.0` → `BedrockVersion.NEW` + `BedrockModelPOJO` | ✓ |
| `…/display/ammo/review_9mm_display.json` | `slot→textures/ammo/slot/review_9mm.png`；`tracer_color:#FFCC66`（`ColorHex` 只接受 `#RRGGBB`，匹配） | ✓ |
| `…/lang/{en_US,zh_CN}.lang` | `injectAllGunPackLanguages` + [本轮] LanguageManager 重跑 → 名称/描述可翻译 | ✓ |
| `data/tacz_review_pack/index/guns|ammo/*.json`、`data/guns/review_pistol_data.json` | 服务端 `CommonAssetsManager` 装载后经同步包下发；客户端索引按 id 关联本地 display | ✓（common 侧由 r7-respack-core 负责） |
| 缺 `hud`/`sounds`/`lod` | `hud`→`ENTITY_EMPTY_TEXTURE`（§6.1，HUD 侧不显示）；`sounds` 空→`checkSounds` 直接返回（静音，上游同）；`lod`→无低模 | ✓（不阻断） |

⇒ 该包在客户端**应能**完整生效（枪有模型/贴图/图标/本地化名字）。已知外部依赖：
`tacz:default_state_machine_v2` + `tacz:renew` 来自内置默认枪包，若同时删除默认包才会失败（与上游同）。

### 5.4 失败点与跨模块发现（要 Lead 转发）

| # | 级别 | 现象 | 根因/证据 | 归属 scope / 建议 |
|---|---|---|---|---|
| 1 | **P1** | 专用服务端执行 `/tacz reload` 后，**已连接客户端**的枪/弹药/配件索引（名字、数据、display id 映射）不更新，直到重连；单人游戏靠本轮 §2.1 的客户端补调兜住 | 移植 `CommonAssetsManager#reloadAllPack`（`:560-563`）只 `reloadPackList()+reload()`；上游是 `server.reloadResources(...)`（上游 `:342-352`）→ `OnDatapackSyncEvent`（上游 `:331-341`）→ 对每个玩家重发同步包 → 客户端 `syncGunPack` 重建索引。移植只有 `onPlayerLoggedIn`（`:524-531`）会发，`reload` 后不会 | `resource/CommonAssetsManager`（r7-respack-core）/ `network`（r7-network）。建议 `reloadAllPack()` 末尾把新的 `networkSyncMessages` 发给所有在线玩家（等价上游 null-player 分支），并处理“reload 后新玩家”一致性 |
| 2 | **P2** | 模组 jar 内**直接内嵌**的 `assets/**`/`data/**` 枪包内容不被任何管理器扫描到（贴图/音效因 FMLFileResourcePack 仍可见；但 display/model/动画/index/data 都不会被读） | 上游客户端资源管理器 = mod jar 资源 + 用户资源包 + 枪包（`ClientModLoader#clientPackFinder` 把 mod 资源塞进 `mod_resources` pack），服务端 datapack 同理；移植 `GunPackResources#scanDirectory` 只扫 `GunPackLoader` 的 `<gamedir>/tacz/**` 来源（`IGunPackSource`），`api/resource/ResourceManager` 的 javadoc 仍写「可以直接将 assets 和 data 内置在模组中」 | `resource/GunPackLoader`+`pack/**`（r7-respack-core）、`api/resource/ResourceManager`（r7-api）、`client/sound/GunPackResourcePack`（r7-gun）。二选一：①增加一个只读的「mod jar/Builtin PackSource」以最低优先级参与扫描（客户端+服务端各自扫对应根）；②不改行为，把 API javadoc/文档改成「1.7.10 必须 `registerExportResource` 导出到 `<gamedir>/tacz/`」并登记为平台差异。顺带：客户端 `bedrockAnimation` 因此看不到模组 jar 的 `animations/{rifle,pistol}_default.animation.json`（上游可被 display json 以 `"default_animation": "tacz:rifle_default"` 引用；默认包不用该字段，走 `use_default_animation`） |
| 3 | P2（风险） | 单人游戏 `/tacz reload` 时 `CommonAssetsManager#reloadAllPack` 在 Server thread 重扫枪包，`GunPackLoader#reloadPackList` 会 `closeSources()` 关掉旧 `ZipGunPackSource` 句柄；此时 client 线程可能正在读同一个 zip（渲染贴图/懒加载模型）⇒ 可能出现 `zip file closed` / 纹理丢失 | 上游 client/server 各有一套 pack 资源对象（`MultiPackResourceManager` 各自 open）；移植 SP 下两端**共用同一份** `GunPackLoader.packSources`（`GunPackLoader.java:87-88/94-151`）。目录形态枪包无影响，`.zip` 枪包有窗口期 | `resource/GunPackLoader`（r7-respack-core）。建议：reload 时对 zip 句柄“延迟关闭/引用计数”，或重扫后**不立即 close** 旧句柄（等旧代次任务 invalidate 后再关）；最低限度在文档/日志里登记该窗口 |
| 4 | P3 | `GunPackResourcePack#scheduleTextureReload`（`:495-513`）声称「重挂后重载一遍就能让之前缓存成 missing 的贴图重试」——实际不成立 | `TextureManager#loadTexture` 失败时把 `TextureUtil.missingTexture`（`DynamicTexture`）**缓存在 `mapTextureObjects`**（`TextureManager.java:82-116`），`onResourceManagerReload`（`:163-175`）只是用缓存里的 `ITextureObject` 重新 `loadTexture`，而 `DynamicTexture#loadTexture` 是空实现（`DynamicTexture.java:34`）⇒ 不会重试。正常启动路径不触发（枪包在首次 bind 前已挂好）；仅“先缺后补”场景（包内后加文件/后加包且贴图已被 bind 过）会残留紫黑块到重启 | `client/sound/GunPackResourcePack`（r7-gun）。若要修：重载时把枪包 namespace 的 `TextureManager#mapTextureObjects` 条目清掉（反射）或改为自管贴图加载 |
| 5 | P3 | `syncGunPack` 未做上游的「连接一致性」校验（上游 `doSync` 先查 `Minecraft#getConnection()==connection && isConnected`，`ServerMessageSyncGunPack.java:88-91`） | 移植 `ClientProxy.Hooks#syncGunPack :454-480` 收到的分片直接入 `CommonNetworkCache`/重建索引；极端时序（断线瞬间的最后分片）可能污染新连接状态 | `client/ClientProxy`（r7-input/r7-platform） |
| 6 | P3（记录） | `GunDisplayInstance` 的 missing-texture 兜底用透明 `tacz:textures/entity/empty.png`，而 `ClientAmmoIndex`/`ClientAttachmentIndex` 用上游 `minecraft:missingno`（紫黑棋盘） | `GunDisplayInstance.java:102-103/646-656/894-896` ↔ 上游 `MissingTextureAtlasSprite.getLocation()`；§6.1。选择本身是有据降级（渲染层一贯用 `ENTITY_EMPTY_TEXTURE`），但同一个常量在两处实现不同，上游语义是「可见的缺图占位」 | 记录，不改（避免动渲染层既有约定）；若要统一建议随 r7-model/r7-gun 一起评估 |
| 7 | P3 | **用户资源包（vanilla resourcepack）覆盖枪包贴图/lang 的优先级与上游相反**：移植里枪包 `IResourcePack` 最后 `reloadResourcePack` ⇒ 最高优先级，会盖掉用户资源包；上游枪包是 `Pack.Position.BOTTOM` ⇒ 最低优先级，用户资源包应盖过枪包 | `GunPackResourcePack` 类注释只论证了「与模组 jar 的 lang 重叠」这一种情况，未覆盖用户资源包；1.7.10 `FallbackResourceManager.java:38-66` 从后往前找（后挂者胜） | `client/sound/GunPackResourcePack`（r7-gun）。低优先场景（用户想换某枪贴图），可先记录；彻底修需要把枪包塞进 `Minecraft#defaultResourcePacks` 的**首部**（`FMLClientHandler` 的 `resourcePackList`）而不是 `reloadResourcePack` 追加 |

---

## 6. 未修 / 有据降级（本 scope 记录项）

| # | 项 | 上游 | 移植 | 判定 |
|---|---|---|---|---|
| 1 | 缺失贴图占位 | `MissingTextureAtlasSprite#getLocation()`= `minecraft:missingno`（紫黑棋盘） | index 类保留 `minecraft:missingno`（绑定时由 `TextureManager` 回退到 missing 纹理）；`GunDisplayInstance`/item renderer 用透明 `ENTITY_EMPTY_TEXTURE` | 有据降级（渲染层约定），§5.4#6 记录不一致 |
| 2 | 粒子参数对象 | `ParticleOptions`（类型+参数，brigadier 解析） | 名称字符串，`spawnParticle` 按 1.7.10 名字派发；带 namespace 时去前缀（本轮统一）；非法名字在生成端静默 | 有据降级（1.7.10 无粒子注册表）；`AmmoParticle`/`ParticleCompat` 注释已登记 |
| 3 | fastutil 有序表 | `Double2ObjectRBTreeMap` | `TreeMap<Double,…>`（同升序语义） | 等价替换 |
| 4 | `ItemStack` 反序列化 | `CraftingHelper#getItemStack`（支持 `nbt` 为 SNBT 字符串或 JsonObject） | `item/count/damage/nbt` 手工解析；`nbt` 只接受字符串；解析失败返回 null（上游抛异常） | 有据降级；**当前无 display POJO 使用 ItemStack 字段**（grep 确认），实际是死代码 |
| 5 | Java 9/10 集合 API | `Set.of/Set.copyOf/List.of` | 多数处直接用（jvmDowngrader 支持），少处换 `Collections.*`（等价） | 等价 |
| 6 | GSON 适配器 | `ItemTr/ItemTransform.Deserializer`、`ResourceLocation.Serializer`、gson 2.10 EnumMap | 无 ItemTr 体系（`BlockItemTransforms` 替代）；自研 `ResourceLocationAdapter`；`EnumMapTypeAdapterFactory` 补 gson 2.2.4 | 有据平台替换（r1 已登记） |
| 7 | `ResourceLocation` 裸字符串默认命名空间 | `minecraft:`（`new ResourceLocation(s)`） | `tacz:`（`ClientAssetsManager.ResourceLocationAdapter:393-421`） | **宽松扩展**：上游能工作的数据都显式写命名空间，因此不破坏上游数据；但若某包写裸 `textures/...` 意指 minecraft，移植会解析成 tacz（上游本来也加载失败）。记录，不改（改回 minecraft 会让移植期编写的包失效） |
| 8 | 两段式后台重载 | `PreparableReloadListener` prepare(background)/apply(game) | 同步单段（`IGunPackReloadListener`） | 有据降级（1.7.10 无该管线）；加载期阻塞可接受 |
| 9 | 同 namespace 多包覆盖方向 | 1.20 pack 优先级（后注册/高优先） | 扫描顺序后覆盖前（`GunPackLoader` 按包名排序，可复现） | 已在 `GunPackResources:44-47` 登记（r1） |

---

## 7. 需实机验证清单（本轮修复后的重点）

1. **（P1 修复）单人游戏 `/tacz reload` 不崩**：进世界持枪 → `/tacz reload` → 观察日志无跨线程异常、
   无 `Caught error stitching, removing all assigned resourcepacks`（该分支会把玩家资源包选择清空，见
   `Minecraft.java:656-665`）；重载后枪仍可渲染/开火，UI 正常。
2. **（P1 修复）新增自定义枪包热加载**：把 `docs/review/r7/test-pack/tacz_review_pack` 复制到
   `<run>/client/tacz/` → 不重启游戏，`/tacz reload` → 创造栏出现 Review Pistol/9mm Review、图标/模型/贴图/名字正常
   （验证 §2.1 重扫 + §2.2 语言 + 索引重建三段）。再分别用**目录**与**zip** 形态各验一次（zip 重点看 §5.4#3 的窗口）。
3. **（P1 修复）修改现有包热生效**：改默认包某贴图/`ak47_display.json` 的 `slot`/`lang/en_US.lang` 里某键 →
   `/tacz reload` → 立即反映（贴图重绘、图标换图、文本换字）。**反向**：删除某包后 `/tacz reload`，
   该包物品/display 应消失、无残留（`ClientIndexManager.clear` 已清表）。
4. **（P2 修复）语言覆盖**：在自定义包 `lang/en_US.lang` 里写与本体**同名**的 key（如
   `tacz.gun.ak47.name=Custom AK47`）→ 重载后应显示自定义值（验证 §2.2；移植前只会“补缺”）。
5. **多人服（已知缺口 §5.4#1）**：服务端 `/tacz reload` 后，已在线的客户端**预期仍用旧 index**（当前实现），
   重连后恢复；若 Lead 采纳 §5.4#1 修复，则改为在线即刷新。
6. F3+T：贴图刷新、枪不消失、语言按当前语种正确；`ClientGunPackReloader` 日志不刷 error。
7. 断线重连 / 切世界：无旧枪包残留（`GUN_DISPLAY`/`GUN_INDEX` 按新服务端数据重建）。
8. 默认链路回归（防止本轮改动回归）：进世界枪械/配件/弹药图标、模型、开火音效、枪口粒子正常；
   日志 **无** `Gun display … is unavailable` / `display file read fail` / `animation not found`。

---

## 8. 覆盖清单（诚实）

- **逐文件对读上游 `client/resource/**` 全部 85 个 java**：归一化 diff（去注释/import/空行后逐 hunk）+
  有代码差异者回溯上游源码行；本 scope 27 文件全部逐行读过；`pojo/{model,animation,display}` 属 r7-model/r7-crosshair/r7-scope，
  只在调用点/接口层核对（`IDisplay#idToFile`、`GunDisplay.init()`、`TransformScale`、`BlockItemTransforms` 调用面）。
- **平台侧逐行/按段读**：`Minecraft.java`（500-680、1658-1669、2978-3015、2847/2857 等）、`FMLClientHandler.java:180-351`、
  `LanguageManager.java`、`Locale.java`、`LanguageRegistry.java:160-320`、`TextureManager.java`、
  `FallbackResourceManager.java`、`SimpleReloadableResourceManager.java`、`DynamicTexture.java`、
  `FMLModContainer#getCustomResourcePackClass`/`FMLCommonHandler#addModAsResource`；
  1.20.1：`Minecraft#reloadResourcePacks`、`PackRepository`、`Pack.Position`、`MultiPackResourceManager`/`FallbackResourceManager`、
  `CraftingHelper#getItemStack/getNBT`、`ResourceLocation.Serializer`、`ClientModLoader#clientPackFinder`。
- **本地关联代码**：`ClientProxy`（preInit/init/postInit/syncGunPack）、`ClientSetupEvent`、`ReloadResourceEvent`、
  `CommonProxy`、`ReloadCommand`/`RootCommand`、`CommonAssetsManager`、`GunPackLoader`、`GunPackResources`、
  `IGunPackSource`/`DirectoryGunPackSource`/`ZipGunPackSource`、`mcshim.ResourceManager`/`PackSource`、
  `JsonDataManager`/`LazyJsonDataManager`/`ScriptManager`、`ParticleDefinitionReloader`、
  `GunPackResourcePack`+`GunPackResourcePackReloadListener`、`SoundPlayManager`（调用点）、`AmmoParticleSpawner`/`ParticleCompat`（调用点）。
- **有运行日志证据**：启动期枪包扫描/挂载/probe、粒子定义、语言注入、纹理重载（`fml-client-latest.log`）；**未实机运行**本轮改动，
  所有修复结论来自源码时序推导。未运行 gradle（纪律）。
- **没做**：真实进游戏/开服；GL 线程行为实测；zip 枪包热重载实测；多人服双端联调；`pojo/{model,animation,display}` 逐字段审计（属他人 scope）；
  `/tacz reload` 之外的 GUI/渲染行为。
