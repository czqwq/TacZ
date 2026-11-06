# r7-respack-core · 枪包（资源包）加载链路 上游等价性审查

> Owner：`r7-respack-core`（共享任务板 `task-26`）
> 写作用域：`src/main/java/com/tacz/guns/resource/**`、`docs/review/r7/respack-core.md`、`docs/review/r7/test-pack/`
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）
> 平台准绳：`tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/server/packs/**`、`tmp/MinecraftForge-1.20.1/**`、
> `build/rfg/minecraft-src/java/**`（1.7.10 原版 + FML/Forge）、本工程 `mcshim/**`
> 纪律：**未运行任何 gradle 任务**；改动文件 `lint_files(min_severity=error)` = **0 error**；未改 `tmp/**`、`build/**`、`run/**`、
> `src/main/resources/**`；未做 git checkout/stash/reset/commit。
> 先行报告：`docs/review/r2/09-resource-review.md`、`docs/review/r5/data-wiring.md` 已覆盖的部分本文只复核结论、不重复展开。

---

## 1. 结论速览

| # | 链路 | 结论 | 关键证据 |
|---|---|---|---|
| 1 | **包发现**（目录/zip、`gunpack.meta.json`、版本依赖、默认包导出、排序） | **等价**（1 处有据差异：排序确定化） | §2.1、§3.2 实机日志 `Found 1 possible gunpack(s)` |
| 2 | **1.20 `PackResources`/`ResourceManager` 语义**（`listMatchingResources` / `listResourceStacks` / `getResource`） | **等价**（且忠实复刻了上游 `getResource` 与 `listResources` 的**方向不一致**） | §3.1 |
| 3 | **描述符/版本/旧格式转换**（`PackMeta`/`VersionChecker`/`PackConvertor`） | **等价** | §2.2 |
| 4 | **索引与数据管理器**（6 index + 8 data manager + 配方/标签/脚本/战利品） | **等价**（2 处崩溃防护比上游更严，已记录） | §2.3 |
| 5 | **过滤器 / 配件修饰器** | **等价**（纯 API 换名，叠加公式 `addend*percent*multiplier` 未变） | §2.4 |
| 6 | **序列化 / POJO** | **等价**（gson 2.2.4 兼容层 3 个新增适配器有据） | §2.5 |
| 7 | **服务端→客户端同步** | **本轮修 P1**：`/tacz reload` 后不重发快照（多人服客户端索引不更新） | §4.1、上游 `CommonAssetsManager.java:330-342` |
| 8 | **重载流程 / 旧数据清理 / 两端顺序** | **等价**（串行折叠两段式，顺序与上游注册顺序一致；每轮先 clear） | §3.3 |
| 9 | **zip 句柄并发（单人游戏）** | **本轮修 P2**：重扫时立刻关 `ZipFile` 会打断客户端正在读的流（已复现 `IOException: Stream closed`，修复后验证通过） | §4.3 |
| 10 | **jar 内嵌 assets/data 是否参与扫描** | **有据降级（P2）**：只扫 `<gamedir>/tacz/`；附属模组须 `registerExportResource`（api 侧 javadoc 已改） | §6.1 |
| 11 | **自定义枪包能否实机生效** | **能**：交付最小枪包 `docs/review/r7/test-pack/`，离线端到端验证 `ALL PASS` | §7 |
| 12 | 每帧 DBG | **scope 内 0 处**（无 `DBG`/`System.out`/`printStackTrace`） | grep 全包 |

**最关键 3 条**

1. **P1（已修）**：多人服 `/tacz reload` 只重载服务端数据，**不向在线玩家重发** `networkSyncMessages`。上游
   `server.reloadResources(...)` 完成后原版会触发 `OnDatapackSyncEvent`（`event.getPlayer() == null` 分支 → 广播所有玩家），
   本仓只有 `PlayerLoggedIn`（进服）这一条发送路径 ⇒ 热更新后已连接客户端仍用旧索引，必须重连。修法见 §4.1。
2. **P2（已修）**：单人游戏里客户端与服务端共用 `GunPackLoader` 的同一份 `packSources`。`/tacz reload` 的服务端分支
   重扫时 `closeSources()` 立刻关闭 zip 句柄，而客户端线程可能正在读同一 zip（贴图/模型懒加载）⇒ `zip file closed`。
   旧实现已实测复现 `IOException: Stream closed`；改为「借出流计数 + 最后读者关闭」后实测：跨 `close()` 的在读流能读完、
   且最后读者离开后句柄真的释放（Windows 上文件可删除）。修法见 §4.3。
3. **有据降级（未修，需用户知）**：1.20 的枪包内容走原版资源管理器，因此**任何** `data/<ns>/index|data/...`、
   `assets/<ns>/display|geo_models|animations/...` 只要出现在资源栈里（普通数据包/资源包、任意模组 jar）都会被扫到；
   1.7.10 侧只扫 `<gamedir>/tacz/`。结论：**自定义枪包能用，但必须放 `<gamedir>/tacz/`、用枪包格式**，
   不能是 1.20 的 resourcepack/datapack 目录。见 §6.1、§7。

---

## 2. 逐文件对照表

上游 `resource/**` 共 **105** 个 java 文件：**104 个在端口存在**，`NamespaceFilteredPackResources.java` 有意删除（§6.2）；
另有 **10 个移植新增**文件。以下按职责分组，表里的「差异」列若写「机械」表示逐处核过、只是 API 换名/格式化。

### 2.1 包发现层（`GunPackLoader` + `pack/**`）

| 上游 | 端口 | 差异与核对结论 |
|---|---|---|
| `GunPackLoader`（`RepositorySource` + `Pack.readMetaAndCreate` + `PathPackResources`/`SecureJar` + `NamespaceFilteredPackResources`） | `GunPackLoader`（enum，暴露 `List<IGunPackSource>`） | **结构性替换**。保留：`<gamedir>/tacz` 扫描（`FMLPaths.GAMEDIR` → `FMLInjectionData.data()[6]`）、`gunpack.meta.json` 必读、`dependencies` 版本区间校验（`ModList` → `Loader#getIndexedModList` + `cpw.mods.fml.common.versioning`，同一套 maven 语义）、`firstLoad` 只导出一次默认包、`PreLoadConfig#override` 语义、目录/zip 两种形态、日志文案。**差异**：① 不再产出 `Pack`/`PackResources`（1.7.10 无该体系），改为 `IGunPackSource` 列表交给两侧 manager + `mcshim.ResourceManager`；② `scanExtensions` 增加了**按包名排序**（上游是 `DirectoryStream` 顺序，不确定）——有据：保证「后扫到的覆盖先扫到的」可复现；③ `getPackSources()` 在 `firstLoad` 前被调用时兜底自扫一次（防静默失败）。 |
| — | `pack/IGunPackSource` | 新增。上游 `PackResources` 的「列举 + 打开」最小子集；按 lead 裁决继承 L0 `mcshim.PackSource`，全仓共用一份枪包视图。 |
| — | `pack/DirectoryGunPackSource` | 新增。对应上游 `PathPackResources`+`SecureJar`（目录）；`Files.walk` + 相对路径 `'/'` 归一 + 排序；**额外守目录穿越**（`resolveInside`，枪包是用户可放内容）。 |
| — | `pack/ZipGunPackSource` | 新增。对应上游 zip → `SecureJar`；复用 `ZipFile` 句柄、懒打开加锁；本轮新增**延迟关闭**（§4.3）。 |
| — | `pack/GunPackResources` | 新增。复刻 `util/ResourceScanner`：`scanDirectory`↔`listMatchingResources`（同 id 后覆盖前）、`scanDirectoryAll`↔`listResourceStacks`（保多份，顺序低→高）、`scanDirectoryResources`↔`scanDirectoryResources`（懒加载登记）、id 推导与 `FileToIdConverter#fileToId` 一致、lenient 解析。 |
| — | `pack/PackDirectory` | 新增。等价 `net.minecraft.resources.FileToIdConverter` 的纯数据载体（prefix+extension）。 |
| — | `pack/GunPackShimBridge` | 新增（本轮）。`resource → mcshim` 的唯一接线点，消除内联全限定名（见 §4.2）。 |
| — | `GunPackType` | 新增。上游 `PackType.CLIENT_RESOURCES/SERVER_DATA` 的 1.7.10 记名替身；**只用于记录当前侧**（加载行为两侧一致）。 |

### 2.2 描述符 / 版本 / 旧格式转换

| 上游 | 端口 | 结论 |
|---|---|---|
| `PackMeta` | `PackMeta` | **逐字一致**：只有 `namespace`（→字段名 `name`）与 `dependencies`；**没有版本/格式字段**。 |
| `VersionChecker`（旧 `<ns>/pack.json` 布局的版本校验） | 同 | 机械：`ModList`→`Loader`、`JsonSyntaxException|JsonIOException`→`JsonParseException`。**上游与端口都是 0 调用点**（死代码，保真保留）。 |
| `PackConvertor`（旧布局 → 新布局 zip 转换） | 同 | 机械：`CommandSourceStack`→`ICommandSender`、`Component`→`IChatComponent`/`ChatComponentTranslation`、`FMLPaths.GAMEDIR`→`GunPackLoader#getPackDirectory`、gson 2.2.4 可用 API。转换规则（`PACK_INFO_PATTERN`、display/index/data 路径重写、`type` 注入、`gunpack.meta.json` 生成）逐条相同。仅耗时数字精度不同（整 ms vs 小数 ms，纯提示文本）。调用方：`command/sub/ConvertCommand` + `client/event/PlayerEnterWorld`。 |

### 2.3 索引 / 数据管理器（`index/**` + `manager/**`）

| 上游 | 端口 | 结论 |
|---|---|---|
| `index/CommonGunIndex` | 同 | 机械 + **4 条额外 `Preconditions`**（`reloadData`/`reloadData.getType()`/`fireModeSet`/`recoil` 非空、recoil 帧判空）。原因：上游这些字段有非 null 默认值，只有 json 显式写 `null` 才为 null，上游在那条路径上同样 NPE；本工程契约是「单文件失败只打日志、不整批中断」，NPE 会穿透 → 转成 `IllegalArgumentException`。**比上游严，但只在坏 json 上体现**（r2-09 已登记）。 |
| `index/{CommonAmmoIndex,CommonAttachmentIndex,CommonAttachmentVariantIndex,CommonBlockIndex,CommonSlotAdapterIndex}` | 同 | 机械。`CommonBlockIndex` 的 `ForgeRegistries.ITEMS.getValue(id) instanceof BlockItem` → `GameRegistry.findItem(domain,path) instanceof ItemBlock`（1.7.10 对应物，`GameData#findItem` 查不到返回 null 不抛，等价）。 |
| `manager/JsonDataManager` | 同 | **形态替换**：`SimplePreparableReloadListener`（prepare/apply 两段）→ 同步 `reload()`（`apply(scanDirectory(...))`）；`apply` 的 `dataMap.clear()`、`data != null` 才 put、`catch(JsonParseException|IllegalArgumentException)` 逐行相同。 |
| `manager/LazyJsonDataManager` | 同 | 同上。懒加载语义（`dataMap`/`lazyLoaderMap`/`failedData`/`allResourceIds`/`shouldEagerLoad`/并发保护）逐行保留；`GsonHelper.fromJson(...,true)` → `GunPackResources#readElement`。 |
| `manager/CommonDataManager` | 同 | 机械：`apply` 里额外把原始 json 存网络缓存（逐字相同）。 |
| `manager/{GunDataManager,AttachmentDataManager,GunIndexManager}` | 同 | 机械：`ResourceLocation.tryParse` → 端口 `parseResourceLocation`（非法返回 null）；`JsonObject#deepCopy` → 手工浅拷贝（只追加一个布尔字段，等价）。 |
| `manager/{AttachmentsTagManager,RecipeFilterManager,LootInjectionManager}` | 同 | 机械：`ResourceScanner.scanDirectoryAll` → `GunPackResources.scanDirectoryAll`；`scanDirectoryResources` 同理；`ResourceLocation#withPath` → 同 namespace 重建 id。 |
| `manager/ScriptManager` | 同 | 机械：`FileToIdConverter("scripts",".lua")`→`PackDirectory`；`prepare` 里 `package.preload` 注册 + `apply` 求值折叠为一次 `reload()`，顺序相同（先登记后求值）。 |
| `manager/AttachmentScriptManager` | 同 | 仅格式。 |
| `manager/INetworkCacheReloadListener` | 同 | 上游 `extends PreparableReloadListener` → 端口 `extends IGunPackReloadListener`（自研等价接口）。 |
| — | `manager/GunSmithTableRecipeManager` | 新增（移植特有，上游由原版 `RecipeManager` 承担）：扫 `data/<ns>/recipes/**` → `GunSmithTableSerializer#deserializeAndRegister` → 静态表；`type` 过滤（缺 `type` 时宽容，非 `tacz:gun_smith_table_crafting` 跳过）；单条失败 warn 不中断；同时作为 `DataType.RECIPES` 的生产者。 |
| — | `IGunPackReloadListener` | 新增。`PreparableReloadListener` 的同步单段替身（客户端/服务端共用的监听器契约）。 |

**重载顺序**（`CommonAssetsManager#reloadAndRegister`，端口 `:174`）与上游 `reloadAndRegister`（`:94`）逐项相同：
`gunData → attachmentData → attachmentVariantData → slotAdapterData → attachmentsTag → recipeFilter → lootInjection →
blockData → script → ammoIndex → gunIndex → attachmentIndex → attachmentVariantIndex → slotAdapterIndex → blockIndex（→ 端口追加 recipes）`；
末尾 `AllowAttachmentTagMatcher.resetCache()` + `networkSyncMessages = createMessages(...)`（上游是 barrier 之后同一顺序），
端口再补 `initLazyRecipes()`（上游是 `TagsUpdatedEvent`，语义等价）。

### 2.4 过滤器 / 配件修饰器

| 上游 | 端口 | 结论 |
|---|---|---|
| `filter/{IFilter,LiteralFilter,RegexFilter}` | 同 | 仅格式。 |
| `filter/RecipeFilter` | 同 | 机械 + 防御：`getAsJsonObject`/`getAsJsonArray` 的 `IllegalStateException`/`ClassCastException` 转 `JsonParseException`（会逃出单文件跳过契约）；`ResourceLocation.tryParse` → 本地 `tryParse`。白/黑名单两段语义未变。 |
| `modifier/AttachmentPropertyManager` | 同 | 机械（`LivingEntity`→`EntityLivingBase`、`Mth`→`mcshim.Mth`）；`postChangeEvent` 补 `ItemStack == null/Item == null` 判空（1.7.10 空手 `getHeldItem()` 返回 null，上游恒 `ItemStack.EMPTY`）。叠加公式 `addend/percent/multiplier`、布尔 `allMatch`/`anyMatch`、脚本二次求值**逐行相同**。 |
| `modifier/AttachmentCacheProperty` | 同 | 机械；`"…".formatted(...)`（jvmDowngrader 可降级）。 |
| `modifier/custom/**`（18 个） | 18/18 同 | 全部为机械换名：`Component#translatable(...).withStyle(ChatFormatting.X)` → `new ChatComponentTranslation(...).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.X))`、`OnlyIn(Dist.CLIENT)`→`SideOnly(Side.CLIENT)`、fastutil `Pair.left()/right()`→commons-lang3 `Pair.getLeft()/getRight()`、`Mth`→`mcshim.Mth`。逐个 `eval`/`initCache`/`readJson`/`getPropertyDiagramsData` 比过，叠加语义未变；`AimInaccuracyModifier` 上游 `eval` 本就是空方法（`@Deprecated`），端口一致。 |

### 2.5 序列化 / POJO

| 上游 | 端口 | 结论 |
|---|---|---|
| 4 个 index `JsonDeserializer`（`CommonGunIndexSerializer` 等） | 同 | 机械；**确认仍是 `JsonDeserializer`**（`getInstance` 被调用，不会退化成反射空壳；r2-09 已实测）。 |
| `serialize/{PairSerializer,DistanceDamagePairSerializer,Vec3Serializer,IgniteSerializer,BuiltInAttachmentSerializer}` | 同 | 机械：`Vec3` → `Vec3.createVectorHelper`/`xCoord…`；`distance:"infinite"` 处理逐行相同。 |
| `serialize/GunSmithTableResultSerializer` | 同 | **形态替换**：解析体委托给 `crafting/result/GunSmithTableResult#fromJson`（1.7.10 无 `CraftingHelper`/`CompoundTag`/`ItemStack.EMPTY`），本类只留 Gson 适配。 |
| `serialize/GunSmithTableIngredientSerializer` | 同 | 同上：材料解析委托 `crafting/GunSmithTableIngredient#fromJson`（`item` 直物品 或 `tag` 矿物词典）。 |
| — | `serialize/ResourceLocationSerializer` | 新增。`ResourceLocation.Serializer`（1.20 内置）的 1.7.10 替身。 |
| — | `serialize/ItemStackJsonParser` | 新增。`CraftingHelper#getItemStack` 的最小子集（`GameRegistry.findItem` + `count` + `nbt`）。 |
| — | `serialize/EnumMapTypeAdapterFactory` | 新增。gson 2.2.4 不为 `EnumMap` 字段构造 `EnumMap`（会构造 `LinkedHashMap` 导致字段 set 失败）——14 个 `*_data.json` 的 `fireModeAdjust` 实测受影响。 |
| `pojo/**`（41 文件） | 41/41 同 | 机械：`ResourceLocation` getter 名、`Component`→`IChatComponent`、`Vec3`、`ItemStack.EMPTY`→null 判空、`record`→`class`/`instanceof` 模式保持（jvmDowngrader 支持）。字段级 `@SerializedName` 与上游一致（枪包 json 契约不变）。 |

`serialize/**` 全部只实现 `JsonDeserializer`（无写方向）——这是上游同样形态，端口下发的永远是**原始 json 字符串**，
客户端重新走同一套反序列化器，因此不存在读写不对称。

### 2.6 网络同步（`network/**`）

| 上游 | 端口 | 结论 |
|---|---|---|
| `network/DataType` | 同 | 枚举常量与顺序一致（15 项）。 |
| `network/GunPackSync` | 同 | `CHUNK_SIZE=256KiB`、`MAX_SYNC_SIZE=64MiB`、gzip + 分片、`Receiver` 的 transferId/顺序/大小校验、`clear()` 逐行相同。 |
| `network/CommonNetworkCache` | 同 | 机械 + 1.7.10 差异：`fromNetwork(Map)` 开头 `clear()`（15 类数据 + `GunSmithTableRecipe.clear()`，避免换服残留）；`REGULAR` 类型先装、index 延后、配方最后（上游由客户端 `RecipeManager` 承担）；`ALLOW_ATTACHMENT_TAGS` 上游 0 引用，端口保真。 |
| （`network/message/ServerMessageSyncGunPack`，属 r7-network） | 同 | 字段序/分片/失败断开（`closeChannel`）已由 r5 逐包核对；本文只核它与 `GunPackSync` 的尺寸约定一致。 |

---

## 3. 加载流程与 1.20 语义等价性

### 3.1 1.20 `ResourceManager`/`PackRepository` 语义映射（逐条）

上游枪包链路实际只用到原版资源管理器的 5 个语义。逐条对照（关键源码行已核）：

| 1.20 语义（源码） | 1.7.10 落点 | 等价性 |
|---|---|---|
| `FileToIdConverter#listMatchingResources`（同 id 只取**一份**；`FallbackResourceManager#listResources` 用 `map.put` **后写覆盖** ⇒ **后扫描的包优先**） | `GunPackResources#scanDirectory`（`output.put(id, element)`，同样后覆盖前） | **等价** |
| `#listMatchingResourceStacks`（同名**多份**；`FallbackResourceManager#listResourceStacks` 按 `fallbacks` 正序 append ⇒ 列表 = 低优先→高优先） | `GunPackResources#scanDirectoryAll`（按 source 顺序 append） | **等价** |
| `#getResource`（`FallbackResourceManager` **倒序**取第一个 ⇒ 最后入栈者优先；但**单包内部** `DelegatingPackResources#getResource` 是**正序取第一个** ⇒ 第一个 delegate 优先） | 客户端二进制资源走 `mcshim.ResourceManager#getResource` = `stack.get(0)`（注入顺序第一个） | **等价（含上游自相矛盾）**：同一批枪包，`getResource` 是「先扫到者优先」、`listResources` 是「后扫到者优先」。**端口把这两条分别原样复刻**（`GunPackResources` 后覆盖前；`mcshim` 取第一个），没有自作主张统一。 |
| 路径解析：`data/<ns>/<dir>/**`、`assets/<ns>/<dir>/**`，id = 去前缀去扩展名 | `GunPackResources#fileToId` + `listNamespaces`（逐 namespace 展开） | **等价**（含 `data/<ns>/data/attachment_variants` 这类「目录名本身带 `data/` 前缀」的上游怪路径，端口 `PackDirectory.json("data/attachment_variants")` 一致） |
| 失败回退/异常隔离：`ResourceScanner` 单文件 `catch` → `LOGGER.error` 跳过 | `GunPackResources#readElement` 同；`JsonDataManager#apply` 同 | **等价**，端口另把会穿透契约的 `IllegalStateException`/`NPE` 转成 `IllegalArgumentException`/`JsonParseException`（§2.3/§2.4，偏严） |
| 包优先级模型：上游 = `Pack.Position.BOTTOM` 的单一 `tacz_resources` 包，**包内 delegate 顺序 = 目录遍历顺序（不确定）** | 端口 = `scanExtensions` 按包名 `compareToIgnoreCase` 排序 | **有据差异**：端口把不确定顺序确定化。副作用（用户需知，见 §7 README §5）：data/json 是「包名靠后者覆盖靠前者」，png/ogg 是「包名靠前者优先」——两边方向相反（这正是上一行上游矛盾的直接后果）。 |

### 3.2 实机日志证据（不是推演）

`run/client/logs/fml-client-latest.log`（2026-11-05 18:38 与 18:51 两次启动）真实输出：

```
[18:38:22] [Client thread/INFO] [tacz/tacz]: Start scanning for gun packs in E:\IDEA\TacZ\run\client\tacz
[18:38:22] [Client thread/INFO] [tacz/tacz]: Found 1 possible gunpack(s) and added them to resource set.
[18:38:27] [Client thread/INFO] [tacz/tacz]: Injected gun pack localization for 20 locale(s): [...]
[18:51:18] [Server thread/INFO] [tacz/tacz]: Start scanning for gun packs in E:\IDEA\TacZ\run\client\tacz
[18:51:21] [Server thread/INFO] [tacz/tacz]: Prepared gun pack sync: 260530 bytes -> 31118 compressed bytes, 1 part(s)
```

⇒ 结论：**发现 → 解析 → 语言注入 → 服务端打包同步** 四条链路在实机跑通；且**游戏根目录 = `run/client`**
（用户/验收的安装路径以这个为准，见 §7）。

### 3.3 重载流程（`IGunPackReloadListener` 等价性）

- 上游：`AddReloadListenerEvent` 逐个 `event.addListener(...)`（两段式，可能有后台线程）→ barrier 后
  `resetCache + createMessages` → `TagsUpdatedEvent` 里 `recipe.init()`。
- 端口：`reloadAndRegister()` 同步串行（无后台线程池）→ 末尾同样 `resetCache + createMessages + initLazyRecipes`。
  **顺序与上游注册顺序逐项相同**（§2.3），单文件失败隔离语义相同。
- 旧数据清理：每个 manager 的 `apply` 第一件事是 `dataMap.clear()`（`JsonDataManager`/`LazyJsonDataManager` 同）；
  `CommonNetworkCache#fromNetwork` 先 `clear()`（含 `GunSmithTableRecipe.clear()`）；`CommonAssetsManager#reload()`
  每次 **new** 一个实例（旧实例随之丢弃）⇒ `/tacz reload` 后不会残留已删除/改名的枪包数据。
- 客户端/服务端顺序差：客户端 `ClientAssetsManager#reloadAndRegister` 顺序 = 上游（data→index→display/model/animation），
  服务端 `CommonAssetsManager` 先 data 后 index（也与上游一致）；两端各自独立扫描同一批 `IGunPackSource`，互不依赖。

---

## 4. 本轮改动（4 个文件：3 改 + 1 新增）

> 自检手段：`lint_files(min_severity=error)` = 0 error；另外**禁止 gradle** 的约束下，用从
> `run/client/logs/fml-client-latest.log` 取出的**真实运行期 classpath + org.jetbrains.annotations** 单独
> `javac` 编译了这 4 个文件（`javac ... GunPackShimBridge.java` / `ZipGunPackSource.java` / `GunPackLoader.java` /
> `CommonAssetsManager.java`）⇒ **exit 0**；并用这套新编译的 class 重跑了 test-pack 离线验证 ⇒ `ALL PASS`。
> 最终构建仍以 lead 统一执行的 `./gradlew spotlessApply build` 为准。

### 4.1 P1：`/tacz reload` 后向所有在线玩家重发同步快照

- **上游**：`CommonAssetsManager#OnDatapackSync`（`tmp/.../resource/CommonAssetsManager.java:330-342`）：
  `event.getPlayer() != null` → 发给该玩家；否则 `event.getPlayerList().getPlayers()` **广播**。`reloadAllPack()`
  （`:344-354`）走 `server.reloadResources()`，完成即触发上述事件。
- **修复前端口**：`reloadAllPack()` 只有 `GunPackLoader.reloadPackList(); reload();`；发送点只有
  `onPlayerLoggedIn`（只覆盖进服）⇒ 多人服 `/tacz reload` 后在线客户端仍是旧索引（新枪看不见、改过的数值不生效），
  必须重连。r7-respack-client 已把这一条列为 P1 跨模块发现。
- **修法**（`CommonAssetsManager.java:560-582`）：`reloadAllPack()` → `reload(); broadcastNetworkSync();`，
  `broadcastNetworkSync()` 遍历 `MinecraftServer.getServer().getConfigurationManager().playerEntityList`
  逐个 `NetworkHandler.sendToClientPlayer(message, player)`（只调用 network 侧既有发送 helper，不改包体），
  并打一行 `Re-synced gun pack data to N player(s) after reload`。
- **顺带**：`reloadAllPack()` 原来 `reloadPackList()` + `reload()`（后者内部又重扫一次）= 每次 `/tacz reload` 扫两遍
  （zip 重开/关闭两轮）。现在只调 `reload()`，一次扫描。
- **风险核对**：① 服务端 shutdown 时 `MinecraftServer.getServer()` 为 null → 已判空；② 单人游戏会广播给本地玩家，
  `ClientProxy#syncGunPack` 有 `!Minecraft#isSingleplayer()` 守卫（不会 `clearInstance` 掉集成服实例），
  `CommonNetworkCache#fromNetwork` 先 clear 再装 → 幂等、无残留；③ 分片消息复用同一份 `networkSyncMessages`
  （登录路径本来就这样用）。

### 4.2 P3：`GunPackLoader` 内联全限定名收口

`GunPackLoader` 同时需要 `com.tacz.guns.api.resource.ResourceManager`（读 `EXTRA_ENTRIES`）与
`com.tacz.guns.mcshim.ResourceManager`（注入枪包来源），两类同名无法同文件 import，原先用内联全限定名（违反仓库规则）。
新增 `resource/pack/GunPackShimBridge`（单方法 `install(Supplier<List<PackSource>>)`）收口这处跨层接线，
`GunPackLoader` 改为 `GunPackShimBridge.install(GunPackLoader::packSourcesForShim)`。

### 4.3 P2：`ZipGunPackSource` 延迟关闭（修单人游戏 zip 句柄竞态）

- **问题**（r7-respack-client 跨模块发现，本轮确认成立）：单人游戏客户端/服务端共用 `GunPackLoader` 的同一份
  `packSources`。`/tacz reload` 时客户端分支先 `reloadPackList()`（关旧 zip、建新 source）并 `refreshResources()`；
  随后服务端分支 `CommonAssetsManager.reloadAllPack()` 又 `reloadPackList()` → `closeSources()` **立刻关闭** 客户端
  刚在用/正在懒加载的 zip。客户端线程此时若在读该 zip 的贴图/模型 → `IOException: zip file closed`，表现为
  贴图丢失/索引缺项。目录包无此问题（无长生命周期句柄）。
- **实证**：把修复前实现单独编译跑了一个复现脚本 —— 读到第 8 字节后 `close()`，继续读：
  `[old] RESULT: 跨 close() 读取失败 -> java.io.IOException: Stream closed`。
  （脚本与两次运行的原始输出已留在 `docs/review/r7/test-pack/verify/zip-close/`。）
- **修法**（`ZipGunPackSource.java:88-186`）：借出的 `InputStream` 用 `TrackedInputStream` 包装并 `activeReaders++`；
  `close()` 只置 `closeRequested`，`activeReaders == 0` 才真正 `zip.close()`；最后一个读者 close 时回调
  `releaseReader()` 补关。已退役 source 不再借出新流（`open()` 返回 null，调用方按「文件不存在」处理——它已不在
  当前 pack 列表里）。`listFiles`/`open` 全程 `synchronized` 在同一把锁上，消除「拿到句柄后立刻被关」的窗口。
- **验证**（同一脚本换成修复后实现）：`listFiles` 命中 2 条 / `open` 成功 / `close()` 后不再借出新流 /
  **跨 `close()` 的在读流读到 EOF（58 字节）** / **最后读者离开后句柄释放（Windows 上 zip 文件可删除）** ⇒ ALL PASS。
  延迟窗口 = 最后一次读取的时长（毫秒级），不会长期占住文件（用户仍可正常替换/删除 zip）。

---

## 5. 跨模块发现

| # | 级别 | 发现 | 状态 / 去向 |
|---|---|---|---|
| 1 | **P1** | 多人服 `/tacz reload` 不重发同步快照 | **本轮已修**（§4.1，属 `resource/**`）；r7-network 无需改包体 |
| 2 | **P2** | 单人游戏 client/server 共用 `packSources` 的 zip 句柄竞态 | **本轮已修**（§4.3，属 `resource/pack/**`）；目录包不受影响 |
| 3 | **P2** | 模组 jar 内嵌 assets/data 不参与扫描（上游资源栈可见） | **裁决：不新增 builtin source，登记有据降级（§6.1）**。理由见下。api 侧已把 `api/resource/ResourceManager` javadoc 改成「必须 `registerExportResource`」（r7-api，`docs/review/r7/api.md` §4-9/§8-7），与本结论一致。 |
| 4 | **P3** | `util/ResourceScanner` + `mcshim.{FileToIdConverter,Resource,ResourceManager}` 构成第二套扫描实现；`util.ResourceScanner` **0 调用点**、`@Deprecated`，与「全仓不存在第二套枪包扫描逻辑」表述相左 | 跨模块（`util/**`、`mcshim/**`）。本轮已在 `GunPackLoader` 注释里点明「唯一生效路径 + 已废弃孪生实现」；建议下一轮删除 `util.ResourceScanner`（或明确保留理由）。 |
| 5 | **P3** | 客户端二进制资源（`mcshim` first-wins）与 common json 数据（`GunPackResources` last-wins）的**覆盖方向相反**（见 §3.1 末行） | 忠实复刻上游矛盾，**不建议统一**（改了才是偏离上游）。已写进 `test-pack/README.md §5.4` 供用户规避。 |
| 6 | **P3** | `client/resource/ClientAssetsManager.java:341,360` 注释「1.7.10 的 `CommonAssetsManager#reloadAllPack` 目前不重发同步包（跨模块）」在 §4.1 之后**过时**；且单人游戏里显式补的 `ClientIndexManager::reload` 变为重复动作（幂等，无害） | 跨模块（`client/**`）。已 `send_message` 给 lead 转 r7-respack-client 顺手改注释/去掉重复 reload。 |
| 7 | 记录 | 运行期 gson 实际版本 ≠ 编译期依赖：dev/GTNH 运行期 **lwjgl3ify 在 classpath 最前且 shade 了 gson**（`JsonParser` 报错栈显示来自 `lwjgl3ify-3.0.33-forgePatches.jar`），而源码多处以「gson 2.2.4」为由规避 API（`EnumMap`/`deepCopy`/`fromJson(TypeToken)`/lenient）。**这些规避必须保留**（无 lwjgl3ify 的纯 1.7.10 环境才是 2.2.4），但注释里的「gson 2.2.4 不支持…」在 dev 运行时不成立 | 仅记录，不改代码。 |

### 为什么**不**给 `GunPackLoader` 加 builtin（jar 内嵌）source —— 技术裁决

r7-api 建议的 (b) 方案（新增只读 `BuiltinGunPackSource`，最低优先级）在当前架构下有**无法在禁用构建/实机条件下验证的回归面**：

1. **优先级方向冲突**：`mcshim.ResourceManager#getResource` 取 `stack.get(0)` ⇒ **注入顺序第一个=最高优先**；
   而 `GunPackResources#scanDirectory` 是**后写覆盖前** ⇒ **最后一个=最高优先**。同一份 `packSources` 被两个消费者
   以相反方向解释（§3.1），「把 builtin 放最低优先级」无法同时对两者成立 —— 放队首会让 jar 在 `mcshim` 侧变最高优先，
   放队尾会让 jar 在数据侧变最高优先。
2. **`GunPackResourcePack` 是「最高优先」挂载的**（该类 javadoc `:59-61` 自述「上游 Position.BOTTOM，本包后挂 ⇒ 最高」，
   且实测只与 mod jar 在 `assets/tacz/lang/*` 重叠）。一旦 jar 进入 `packSources`，TACZ jar 的
   `assets/tacz/**`（含 1 字节占位 `lang/*.lang`）会被该 pack 以最高优先级serve ⇒ 可能压过用户资源包/默认枪包的语言与贴图。
3. **与默认枪包导出去重**（r7-api 提醒的第 2 点）：jar 里还有 `assets/tacz/custom/tacz_default_gun`，若按「jar 根 =
   pack source」加入，会与 `<gamedir>/tacz/tacz_default_gun` **重复**（同一 namespace/路径两份），优先级不可控。
4. **真实缺口比想象小**：内置默认动画 `animations/{rifle,pistol}_default.animation.json` **不是**缺口 ——
   `InternalAssetLoader` 走的是原版 `IResourceManager`（`Minecraft#getResourceManager()`），而 1.7.10 Forge 会把每个模组
   注册成资源包（`FMLClientHandler#beginMinecraftLoading` 里 `resourcePackList.add(pack)`，1.7.10 源码 :598），
   所以 `use_default_animation: rifle/pistol` 这条**默认内容全走的路是通的**（默认枪包 70 个 display 里
   `default_animation` 显式引用数 = **0**，实测 grep）。缺口只剩「枪包作者显式写 `"default_animation": "tacz:rifle_default"`
   之类引用 jar 内资产」这一假想场景（上游能过、端口会 `animation not found` 整份 display 被丢弃）。
5. 若日后要做，**最小且低风险**的形态是：`BuiltinGunPackSource` 只追加给「json 索引扫描」这一路
   （`ClientAssetsManager` 的 `bedrockAnimation`/`geo_models`/`display` 与 common 的 data 扫描），
   **不**接入 `mcshim.ResourceManager`/`GunPackResourcePack`；且 jar 内 `assets/tacz/custom/**` 必须排除。
   这需要 client 侧配合（跨模块），建议单开一轮、带实机验证。

> 结论：本轮选「登记有据降级 + api 侧 javadoc 明确 `registerExportResource` 契约」，不改扫描来源。

---

## 6. 未修与有据降级

### 6.1 果：jar 内嵌 assets/data 不参与枪包索引（有据降级，P2）

- **上游**：资源管理器 = 原版 pack 栈（含所有模组 jar 的 `assets/`+`data/`、数据包、资源包），因此
  `data/<ns>/index|data/...`、`assets/<ns>/display|geo_models|animations/...` 只要在栈里就被 `ResourceScanner`/客户端索引器扫到。
- **端口**：只在 `<gamedir>/tacz/` 下按目录/zip 扫描（`GunPackLoader` + `IGunPackSource`），因为 1.7.10 没有
  datapack、也没有「统一资源/数据包栈」概念（原版 `IResourceManager` 只能看 `assets/`，看不到 `data/`）。
- **后果与规避**（已由 r7-api 落到 API javadoc）：
  ① 附属模组的枪包**必须**调 `api.resource.ResourceManager#registerExportResource(modMainClass, jar内目录)`，首启由
  `GetJarResources#copyModDirectory` 解压到 `<gamedir>/tacz/<目录名>` 后再被扫描（TACZ 自己的
  `assets/tacz/custom/tacz_default_gun` 正是这么做的：`GunMod#registerDefaultExtraGunPack`）；
  ② jar 根的 `assets/tacz/**`（内部模型/默认动画/贴图/语言）不受影响，仍由原版资源包机制提供；
  ③ 自定义枪包**必须**放 `<gamedir>/tacz/`，不能是 1.20 的 resourcepack/datapack 目录。
- 详见 §5 末尾的「不新增 builtin source」裁决。

### 6.2 删除：`NamespaceFilteredPackResources`

上游这个类只做一件事：让 `tacz_resources` 包**只列举枪包拥有的 namespace**。1.7.10 没有 `PackResources`/namespace 枚举
概念，端口由 `GunPackResources#listNamespaces`（从枪包目录推导 namespace 集合再逐 namespace 展开）承担同一职责，
效果等价。**副作用**：只扫枪包自身 namespace（这正是 §6.1 的另一面）。

### 6.3 其他未修（登记）

| # | 项 | 理由 |
|---|---|---|
| 1 | 包优先级「确定化」差异（上游 = `DirectoryStream` 顺序不确定，端口 = 包名排序） | 有据：不排序则「后覆盖前」不可复现；排序后行为可写进文档（`test-pack/README.md §5.4`）。 |
| 2 | 上游 `ResourceScanner` 的 `IllegalStateException("Duplicate data file ignored")` 分支 | 上游自身不可达（`listMatchingResources` 已去重），端口用覆盖写，行为等价；不引入不可达分支。 |
| 3 | `VersionChecker` 0 调用点（上游/端口都是） | 上游死代码，保真保留（旧布局枪包已由 `PackConvertor` 转换）。 |
| 4 | `PackConvertor` 耗时数字精度（整 ms vs 小数 ms） | 只影响 `message.tacz.converter.finish` 的展示数字。 |
| 5 | 0 调用点的 port 便利方法：`PackDirectory#toIdPath`、`GunPackResources#scanAssetsDirectory`、`GunPackResources#openReader(List,String)`、`GunPackLoader#getPacks`、`CommonAssetsManager#isLoaded` / `#getNetworkSyncMessages` | 无行为风险，删了也只是 churn；`getPacks/isLoaded` 是诊断/侧判工具，保留。**建议下一轮清理**前 3 个纯重复便利方法。 |
| 6 | `CommonAssetsManager` 的 `gunSmithTableRecipeManager` 字段只赋值不读取（IDE 警告） | 它作为 `INetworkCacheReloadListener` 注册进列表即可（配方类型/网络缓存由列表驱动），字段仅为「持有并按上游命名」，保留。 |
| 7 | client 侧 `ClientIndexManager::reload` 在单人 `/tacz reload` 变重复（§5 #6） | 跨模块（`client/**`），幂等无害；建议 r7-respack-client 顺手简化。 |

---

## 7. 实机验证清单（含 test-pack 用法）

### 7.1 交付物

- 最小枪包：`docs/review/r7/test-pack/tacz_review_pack/`（`gunpack.meta.json` + 1 枪 index/data/display + 1 弹药 index/display
  + 1 个 16×16 基岩模型 + 3 张 PNG + 2 个 `.lang`，总共 ~5 KB）。
- 说明：`docs/review/r7/test-pack/README.md`（安装路径、期望日志、成功/失败判据、约束与坑）。
- 离线验证：`docs/review/r7/test-pack/verify/R7PackVerify.java` + `verify-output.txt`（**`RESULT: ALL PASS`，33 项断言**）。
  用**真实 1.7.10 运行期 classpath** 直接调用生产代码：`GunPackResources#scanDirectory`（含 id 推导）→
  `CommonAssetsManager.GSON` 反序列化 → `CommonGunIndex` 的 `checkIndex/checkData` 全通过 → 客户端
  `ClientAssetsManager.GSON` 解析 display/geo/贴图索引 → zip 版与目录版 id 一致 → 带 `//` 注释 json lenient 解析通过。
  （脚本不经过 FML 启动，`GunPackLoader` 枚举初始化依赖 `FMLCommonHandler`，这部分由 §3.2 的实机日志证明。）
- zip 句柄并发验证：`docs/review/r7/test-pack/verify/zip-close/`（修复前复现 + 修复后验证，`output.txt` 为原始输出）。

### 7.2 用户实机步骤（以本仓库 dev 环境为例，**已由实机日志确认路径**）

1. 拷贝：`docs/review/r7/test-pack/tacz_review_pack/` → `run/client/tacz/tacz_review_pack/`
   （正式环境 = `<游戏根目录>/tacz/tacz_review_pack/`；zip 版把**内容**压成 `run/client/tacz/tacz_review_pack.zip`，
   `gunpack.meta.json` 必须在 zip 根）。
2. 启动客户端进存档（或单人游戏内 `/tacz reload`）。
3. 看日志（§3.2 的格式）：应出现 `Found 2 possible gunpack(s)`、`- tacz_review_pack, Main namespace: tacz_review_pack`。
4. 创造模式物品栏找 `Review Pistol` / `测试手枪`（红色手枪图标）→ 拿在手上应看到红色方块手枪 → 左键能开火、消耗 `9mm Review`。
5. 失败判据见 `test-pack/README.md §3/§4`（`Failed to load data file` / `there is no corresponding model file` /
   `statemachine not found` / `Found 1 possible gunpack(s)` 各自的指向）。

### 7.3 本轮改动需要实机确认的点

1. **多人服热更新**：服端 `/tacz reload` 后，**已连接**客户端应看到新枪/新数值（不重连）；服务端日志出现
   `Re-synced gun pack data to N player(s) after reload`。
2. **单人游戏 zip 枪包热更新**：用 zip 形态的枪包、持枪状态（贴图已加载）反复 `/tacz reload` 5~10 次，
   日志不应出现 `zip file closed`/`Failed to read zip gun pack entry`，枪身贴图不应变紫黑。
3. **目录版与 zip 版**都各验一遍（§7.2），确认发现/解析/索引/同步都通。
4. 替换 zip 文件（关闭游戏→替换→启动）仍应正常（延迟关闭只延迟到「最后一个读者读完」，不会长期锁文件）。

### 7.4 建议回归（与本轮改动相邻、非必做）

`/tacz reload` 后：改装界面属性数值、工作台配方列表、语言（切语种）都仍是新的；断开重进/换服不残留旧数据。

---

## 8. 覆盖清单（诚实）

**逐文件读过**：`resource/**` 全部 74 个文件（105 个上游对应文件经 `git diff --no-index` 全量比过，逐类结论见 §2；
大改文件 `GunPackLoader`/`CommonAssetsManager`/`PackConvertor`/`VersionChecker`/`JsonDataManager`/`LazyJsonDataManager`/
`ScriptManager`/全部 modifier/全部 serialize/index/manager/network 逐行）；另读上游
`util/ResourceScanner`、`resource/NamespaceFilteredPackResources`、1.20 `MultiPackResourceManager`/`FallbackResourceManager`/
`DelegatingPackResources`（行号见 §3.1）、1.7.10 `FMLClientHandler`/`GameRegistry`/`GameData`/`FMLInjectionData`/`FMLTweaker`。

**未证 / 未做**：
- **没有运行游戏**（纪律禁止 gradle/运行）：所有实机结论来自**已有运行日志**（`run/client/logs/`）+ 用运行期 classpath
  跑的离线脚本；多人服广播（§4.1）与 zip 延迟关闭（§4.3）**需要 §7.3 的两条实机确认**。
- 客户端渲染/贴图/动画的最终视觉不在本任务范围（r7-respack-client 静态推演 + 本任务 test-pack 离线验证已覆盖「加载」，
  不覆盖「渲染出来对不对」）。
- `docs/review/r7/test-pack/` 的 zip 版安装说明按代码路径给出，未实机装配（离线脚本已用同内容 zip 验证扫描等价）。
