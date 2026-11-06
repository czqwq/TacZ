# 06 · resource / network / config / command 审查报告

作用域：`src/main/java/com/tacz/guns/resource/**`（116 文件）、`network/**`（55）、`config/**`（26）、`command/**`（11）。
准绳：上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`；1.7.10 `build/rfg/minecraft-src/java/**`；
运行期证据 `build/r7run.log`。方法：逐文件 `read` + 上游对照；POJO 与 modifier 用「字段/注解/方法签名集合」机检
（`Compare-Object`）替代逐行 diff；网络包用脚本批量抽取全部 `read()/write()` 方法体做对称性机检；
改后 IDEA `get_file_problems(errorsOnly=true)` + `lint_files(min_severity=error)` 全部 0 error。

---

## 1. 已修复（本作用域内，均有依据）

| # | 文件 | 问题 | 依据 | 修复 |
|---|---|---|---|---|
| 1 | `network/NetworkTaskQueue.java` | **网络层整体失效（严重）**。本类监听的是 `cpw.mods.fml.common.gameevent.TickEvent`，而 1.7.10 的 tick 事件是用 `FMLCommonHandler#bus()` 发出的，`bus()` 返回的是 FML 私有的 `new EventBus()`，**不是** `MinecraftForge.EVENT_BUS`。`CommonProxy:120` 只把它注册到 `MinecraftForge.EVENT_BUS` → `onServerTick/onClientTick` 永不触发 → 队列（`enqueue`）里所有网络任务永不执行，即「所有包都收到了但没有任何效果」 | `FMLCommonHandler.java:90`（`private EventBus eventBus = new EventBus()`）、`:97`（`bus()`）、`:247/:260/:325/:330`（TickEvent 用 `bus().post`）；`EventBus.java:47-50`（同实例重复 register 幂等） | 构造器内兜底 `FMLCommonHandler.instance().bus().register(this)`；并在类/构造器注释写明双总线关系。幂等，调用方后续修正 `CommonProxy` 也不会冲突 |
| 2 | `network/SyncConfigSyncManager.java` | 同上但后果不同：监听 `PlayerEvent.PlayerLoggedInEvent`（也是 FML 总线事件），注册在 Forge 总线 → **玩家登录时既不握手、也不下发枪包数据与 SERVER 配置**（客户端永远拿不到 `CommonNetworkCache`，多人服下枪械数据全空） | `FMLCommonHandler.java:550`（`bus().post(new PlayerEvent.PlayerLoggedInEvent)`）、`:90/:97` | 构造器内兜底注册到 FML 总线（同 #1 的幂等论证），注释引用行号 |
| 3 | `network/message/ServerMessageUpdateEntityData.java` | `read()` 用 `new ArrayList<>(size)` 直接相信报文里的计数：坏包/恶意包给 `size = 2^30` 即按该数字分配数组（OOM/卡服）。同仓其它包（`ServerMessageAmmoStorageContents`、`ServerMessageSyncGunPack#readCache`）都对计数做了上界校验，这里漏了 | 上游 `decode` 也无校验，但 1.7.10 侧已有同类硬化约定（同文件族其它包） | 加 `size < 0 || size > buf.readableBytes()` → `DecoderException`（每条 DataEntry 至少 2 字节，故可读字节数是安全上界），不再按垃圾计数预分配 |
| 4 | `resource/pack/ZipGunPackSource.java` | zip 句柄懒打开的竞态：`zip()` 无同步，`LazyJsonDataManager#getData` 会在渲染线程/服务端线程按需读枪包内文件，两线程同时看到 `null` 会各开一个 `ZipFile`，后写覆盖前一个 → 泄漏句柄（Windows 下删不掉枪包 zip） | `ZipFile` 读操作线程安全但构造不保证；上游用 `SecureJar`（`PathPackResources` 内部缓存）无此问题 | `zipFile` 加 `volatile`、`zip()`/`close()` 加 `synchronized`，注释写明原因 |

改后 lint：`NetworkTaskQueue` / `SyncConfigSyncManager` / `ServerMessageUpdateEntityData` / `ZipGunPackSource` 4 个文件
`get_file_problems(errorsOnly=true)` 与 `lint_files(min_severity=error)` 均 0 error。

---

## 2. 必须由对应作用域修复（本作用域无权改，按严重度排序）

| # | 位置 | 问题 | 依据 |
|---|---|---|---|
| A | `resource/CommonAssetsManager.java:109-130`（Lead 独占） | ✅ **已在审查期间由 Lead 修好，复核通过**：`EnumMapTypeAdapterFactory` 已注册到 `CommonAssetsManager.GSON`（:129），且 gson 2.2.4 的用户 factory 先于内建 `MapTypeAdapterFactory` 被询问，故胜出。需要它覆盖的三处字段已确认都在范围内：`GunData.fireModeAdjust`/`GunData.chargeData`（`GunData.java:91,140`）+ `GunResult.attachments`（`resource/pojo/data/recipe/GunResult.java:17`，声明类型就是 `EnumMap`，由同一 GSON 解析）。`ClientAssetsManager.GSON:99` 也已注册（`GunDisplay.controllableData`）。**唯一提醒**：该工厂只处理「声明类型为 `EnumMap`」的字段，声明为 `Map<Enum,V>` 的字段（如 `GunData.inaccuracy`、`builtInAttachments`）仍由 gson 构造 `LinkedHashMap`——字段类型兼容、本就正常，无需处理 | `build/r7run.log`：`Failed to load data file tacz:b93r_data … Can not set java.util.EnumMap field …GunData.fireModeAdjust to java.util.LinkedHashMap`（`rhino357/taurus500/taurus943` 命中 `chargeData`）；gson 2.2.4 `ConstructorConstructor#newDefaultImplementationConstructor` 只给 Map 构造 `LinkedHashMap` |
| B | `CommonProxy.java:120-121` | `NetworkTaskQueue` / `SyncConfigSyncManager` 注册在 `MinecraftForge.EVENT_BUS`，应为 `FMLCommonHandler.instance().bus()`（两者的监听类型都是 `cpw.mods.fml.common.gameevent.*`）。已有 §1 #1/#2 的构造器兜底，所以**当前不会崩**，但注册点仍是错的语义（且在 Forge 总线上会多注册一次无用监听）。建议直接改成 FML 总线（幂等，不会重复注册） | `FMLCommonHandler.java:90/97/247/550` |
| C | `client/ClientProxy.java:401-419`（Lead 独占） | `Hooks#syncGunPack` 缺少上游的「非内存连接时清空本地资源实例」：上游 `ServerMessageSyncGunPack#doSync` 收齐快照后先 `if (!connection.isMemoryConnection()) CommonAssetsManager.clearInstance();` 再 `CommonNetworkCache.INSTANCE.fromNetwork(cache)`。1.7.10 侧没清 → 连多人服时 `CommonAssetsManager.get()` 仍返回**本地**加载的枪包实例，客户端显示的是自己本地的包而不是服务端的包 | 上游 `ServerMessageSyncGunPack.java:98-101` |
| D | `crafting` / `init` 作用域 | `r7run.log`：`Unknown recipe class! com.tacz.guns.crafting.AmmoStorageUpgradeRecipe` / `GunPegboardDyeRecipe`（需要 `RecipeSorter.register`），否则这两类配方在合成时可能被原版按错误类型处理 | `build/r7run.log`；1.7.10 `CraftingManager:235` / `RecipeSorter` |

---

## 3. 核对通过（逐条与上游一致，无需改动）

| 项 | 结论 |
|---|---|
| **网络 discriminator 顺序** | `NetworkHandler.init()` 1..42 与上游 `NetworkHandler.java:40-127` **逐条同名同向**（含 23 号 `ServerMessageSyncGunPack`、39/40 号 `GunFinishReload/GunFirePost`）；43 号 `ServerMessageSyncConfig` 为移植特有（已在注释声明）。handshake channel 1=`Acknowledge`(C→S)、2=`ServerMessageSyncedEntityDataMapping`(S→C)，与上游 `registerAcknowledge`→`registerHandshakeMessage` 顺序一致 |
| 双 channel | 主 `"tacz"`、握手 `"tacz:handshake"`；上游为 `tacz:network`/`tacz:handshake`，1.7.10 侧改名已记录（无协议协商，仅本地一致性要求） |
| **fromBytes/toBytes 对称性** | 机器抽取 55 个消息类的 `read()/write()` 方法体逐字段比对：**全部字段顺序、类型、可选标记对称**（`ClientMessageSetAttachmentVariant/SetSlotAdapter` 的 `boolean+可选 RL`、`ServerMessageBulletHitBlock` 的 9 字段、`ServerMessageGunHurt/Kill` 的 8 字段、`ClientMessageLaserColor` 的 map+3 标量、`ServerMessageAmmoStorageContents` 的 entries 列表等）。仅 `ServerMessageBulletHitBlock` 写侧 `null→""` 与上游 `writeUtf(null)`（会 NPE）不同，属更宽容，不改 |
| 拆包重组 | `resource/network/GunPackSync.java` 与上游**逐行同构**（CHUNK_SIZE=256KiB、MAX_SYNC_SIZE=64MiB、`Part` 校验、`Receiver` 只接受连续 index 且 index==0 重置、gzip 解压多读 1 字节防放大器）；`ServerMessageSyncGunPack#readCache` 的类型数/条目数/重复键/尾随字节校验与上游一致 |
| 线程模型 | Netty 线程只 `NetworkTaskQueue.enqueue`，主线程每 tick END 排空；异常被捕获记日志（上游 `enqueueWork` 等价物），差异「最多 1 tick 延迟」已在类注释记录 |
| 客户端/服务端分支 | 所有 `ServerMessage*` 的 `handle` 都有 `ctx.side != Side.CLIENT → return`；`ClientSideHooks` 空实现保证专用服务器不加载 client 类（注入式替代 `DistExecutor`），机制合理 |
| **资源 POJO 与上游** | `resource/pojo/**` 60 个文件字段级机检：字段名/`@SerializedName`/默认值/方法签名**全部一致**；差异仅 `Component→IChatComponent`（TabConfig）、`LivingEntity→EntityLivingBase`（GunData/InaccuracyType）、`LootTableInjection` 去掉 1.20 loot serializer、`TabConfig` 用 `GameRegistry.findItem` 取图标。**无 `SerializedName(alternate=…)`、无 `@JsonAdapter`、无 2.2.4 不支持的 `TypeToken` 重载**（`Modifier` 的 `@JsonAdapter` 已改为 `registerTypeAdapterFactory`，语义等价，注释引用 gson 2.3 才加注解） |
| 自定义序列化器对称性 | `PairSerializer`/`Vec3Serializer` 同时实现 `JsonSerializer`+`JsonDeserializer`（对称）；`ResourceLocationSerializer`/`Ignite`/`BuiltInAttachment`/`DistanceDamagePair` 只反序列化（上游同）；索引类 serializer 把 `IllegalArgumentException` 转 `JsonParseException`，与上游一致。`Vec3Serializer` 少数组长度校验（短数组抛 `IndexOutOfBoundsException` 而非 `JsonParseException`），上游同，仅记风险 |
| 加载顺序 | `CommonAssetsManager.reloadAndRegister()` 的注册顺序（gunData→attachmentData→variant→slotAdapter→tags→recipeFilter→lootInjection→blockData→script→**index 全部在后**）与上游「index 依赖 data 必须后置」一致；`CommonNetworkCache#fromNetwork` 的 delayed 两段（先 data 后 index）也与上游逐行同构 |
| `LazyJsonDataManager` | 与上游逐行同构（`PreparedEntry.eager/lazy/failure`、`failedData`、`getData` 的双检锁 + `lazyLoaderMap.remove` 防重复解析） |
| **枪包扫描** | `GunPackLoader` 的目录/zip 扫描、`gunpack.meta.json` 版本依赖校验（`VersionRange.createFromVersionSpec` + `Loader.getIndexedModList`，对应上游 maven `VersionRange` + `ModList`）、默认包导出（`firstLoad` 只导出一次、`PreLoadConfig.override` 短路）与上游一致；额外做了目录项排序（保证「后扫到的包覆盖先扫到的」可复现），已注释 |
| zip 资源释放 | `reloadPackList()` 开头 `closeSources()` 关闭上一批 `ZipGunPackSource`；`fromZipPath` 用 try-with-resources；`DirectoryGunPackSource.resolveInside` 有目录穿越防护（`normalize().startsWith(root)`），zip 侧按 entry 名取（不存在穿越面） |
| **配置项** | 15 个配置类（Key/Render/Resource/Sound/Zoom/Ammo/Gun/Other/Sync 及三个容器 + PreLoad）与上游逐条比对：**项名（大小写与拼写）、push 的 category、默认值、defineInRange 的 min/max、注释文本全部一致**（含 `HitMarketStartPosition` 的拼写、`Zoom` 大写 category、`GlobalBulletSpeedModifier` 的 0.01..20）。上游 `tacz-pre.toml` → 本侧 `tacz-pre.cfg`、`tacz-common/server/client.toml` → `.cfg`，均已注释记录；`ServerShootNetworkCheck`/`ServerShootCooldownCheck`/`EnableDefaultGunSmithTableFilter` 等被 `/tacz config` 修改的键名一致 |
| 配置加载/同步 | COMMON→SERVER→（客户端）CLIENT 顺序；`afterCommonLoad`→`AmmoBlockConfigRead.init()`、`afterServerLoad`→`HeadShotAABB/InteractKey/GunPegboardItem` 重算，对应上游 `LoadingConfigEvent` 的两个分支；SERVER 配置改由 `ServerMessageSyncConfig` 手动同步（43 号包），字段与 `SyncConfig` 一一对应、双向顺序一致，只改内存不落盘（与上游「客户端只读」一致） |
| **命令** | `RootCommand` 子命令名（`attachment_lock/debug/dummy/overwrite/reload/hide_tooltip_part/convert/config`）、下发顺序、`requires hasPermission(2)` → `getRequiredPermissionLevel()=2`、各子命令参数名（`target`/`AttachmentLock`/`dummyAmount`/`mask`/`enable`/`key`/`state`）与上游逐字一致；`/tacz reload` 的客户端/服务端分支（`isClient()` → 注入的 `ClientAssetsManager::reloadAllPack`，否则 `CommonAssetsManager.reloadAllPack`）、成功提示 key `commands.tacz.reload.success`、计时口径（µs→ms 的 double）与上游一致；`/tacz config` 的语言 key `commands.tacz.config.*.enabled|disabled` 与枚举名一致；`ListPackCommand` 上游即空类，未注册，一致 |
| `/tacz reload` 副作用 | `ClientAssetsManager.reloadAllPack()` 里 `Minecraft#refreshResources()` 之后再调 `CommonAssetsManager.reloadAllPack()`，等价上游「客户端重载资源包连带集成服务端 datapack 重载」；`CommonAssetsManager.reloadAllPack()` 多一次 `reloadPackList()`（`reload()` 内部还会再扫一次），幂等、只是多扫一遍盘 |

---

## 4. 未修风险 / 观察项（不构成 bug 或有上游同源行为，仅记录）

1. **空枪包列表会让重载抛异常**：`ServerMessageSyncGunPack.createMessages()` 对 0 字节的缓存（= 一个数据文件都没扫到，例如 `Override=true` 且默认包未导出）会走 `GunPackSync.checkSize(0)` 抛 `IllegalArgumentException`，被 `CommonAssetsManager.reload()` 的 catch 记 `Failed to reload gun pack data` 后重抛。上游 `ServerMessageSyncGunPack.java:38-55` 同构，**未改**（改则需要 invent 新语义）。若用户报「一个包都没有时服务端启动崩」，根因在这里。
2. `ResourceLocationSerializer` 比 1.20 严格：`"path"`（无 namespace）会被判非法，1.20 `ResourceLocation.Serializer` 会补 `minecraft:` 前缀。默认枪包全部写全名，实测无影响；第三方包若省 namespace 会整文件加载失败。已注释声明。
3. `ServerMessageBulletHitBlock` 的空 id 写侧 `null→""`（§3 已述）→ 客户端拿到 `""` 而非 `null`；若 client 侧 `HitBlockEffectHandler` 对空串做 `new ResourceLocation("")` 会抛异常（client 作用域，未核）。
4. `/tacz config` 的 `SyncConfig.*.set()` 只改内存，不 `save()`、也不重算 `ConfigManager.afterServerLoad()` 的派生缓存（`HeadShotAABB` 等）——上游 Forge 在 `.set()` 后也不落盘，行为一致；但如果后续要持久化需补 `ConfigManager.saveAll()`。
5. `NetworkTaskQueue` 是**单例静态队列**，集成服务器下 `ClientTickEvent` 与 `ServerTickEvent` 同一个主线程各排空一次，任务可能在「另一侧」的 tick 里被执行（最多提前/推迟 1 tick）。上游 `enqueueWork` 严格按逻辑侧分线程，1.7.10 集成服下无实际差异。
6. `modifier/custom/**` 与 `modifier/AttachmentPropertyManager` 只做了结构级比对（方法与字段签名、注册表顺序一致），**未逐行审数学**（如 `RecoilModifier` 的曲线插值、`InaccuracyModifier` 的多段状态叠加）；`command/sub/PegboardStressCommand`（16 KB，debug 专用）同样只核了参数解析入口。
7. `PackConvertor` / `VersionChecker` 只核了入口与 gson 用法（`GSON` 自建、不依赖 `CommonAssetsManager.GSON`，不吃 EnumMap 问题），未核转换算法；`ListPackCommand` 上游为空类，一致。

---

## 5. 覆盖清单

| 包 | 文件数 | 覆盖方式 |
|---|---|---|
| `resource/pojo/**` | 60 | 字段/注解/签名机检 vs 上游（唯一真实差异见 §3、§4） |
| `resource/serialize/**` | 15（含 Lead 新建的 `EnumMapTypeAdapterFactory`，只读） | 全部逐行读；对称性、2.2.4 兼容性核对 |
| `resource/manager/**` | 13 | `JsonDataManager`/`LazyJsonDataManager`/`CommonDataManager`/`GunDataManager`/`AttachmentsTagManager`/`ScriptManager` 逐行读 + 上游对照；`AttachmentDataManager`/`AttachmentScriptManager`/`GunIndexManager`/`LootInjectionManager`/`RecipeFilterManager`/`INetworkCacheReloadListener` 结构级比对 |
| `resource/pack/**` | 5 | 全读（zip/目录源、`GunPackResources` 扫描与 id 推导、`PackDirectory`） |
| `resource/network/**` | 3 | 全读（`GunPackSync`、`CommonNetworkCache`、`DataType`） |
| `resource/index/**` | 6 | 结构级比对（字段/校验方法/`getInstance` 形状一致），未逐行审 `checkData` 断言 |
| `resource/modifier/**` | 26 | 结构级比对（见 §4.6） |
| `resource/filter/**`、`GunPackLoader`、`GunPackType`、`ICommonResourceProvider`、`IGunPackReloadListener`、`VersionChecker`、`PackConvertor` | 9 | `GunPackLoader` 逐行；其余结构级/入口级 |
| `network/**` + `network/message/**` | 55 | `read/write` 全量机检；`NetworkHandler`、`NetworkTaskQueue`、`ClientSideHooks`、`SimpleMessageDispatcher`、`SyncConfigSyncManager`、`ServerMessageSyncGunPack`、`ServerMessageSyncConfig` 逐行读 |
| `config/**` | 26 | 15 个配置类逐条 vs 上游；框架类（`ConfigBuilder`/`ConfigValue`/`TaczConfigSpec`/`ConfigManager`/6 个 `*ConfigValue`/`PreLoadConfig`）全读 |
| `command/**` | 11 | 根命令 + 8 个子命令全读并对照上游；`CommandTargets`、`PegboardStressCommand` 参数入口级 |

---

# 第二轮：同类崩溃专项（task-10）

针对实机 P0「上游 1.20 非 null 哨兵 → 1.7.10 变 null → 读取点 NPE」（`LivingEntityAim.tickAimingProgress:98`
`Supplier.get()`→null→`.getItem()`），按同一模式系统扫 `resource/network/config/command/**`，只处理会硬崩的
NPE / CCE / AIOOBE / IOOBE / DecoderException。**10 个文件已修，全部 lint 0 error。**

## 本轮新增修复

| # | 文件 | 崩溃条件 | 1.7.10 vs 1.20 的机制差 | 修复 |
|---|---|---|---|---|
| 1 | `network/message/ClientMessageLaserColor.java:74-95, 96-113` | ①`fromBytes` 期间 NPE（Netty 线程 → 连接被断/客户端崩）：报文里 `AttachmentType` 序号越界 → mcshim `readEnum` 返回 **null** → `EnumMap.putAll` 对 null key 抛 NPE。②服务端主线程 AIOOBE：`gunSlotIndex` 只挡了 `== -1`，`-5` 或 `>=41` 直接 `mainInventory[i]` | 1.20 `readEnum` 越界抛 `IllegalArgumentException`（=DecoderException，由网络层统一处理，不会进业务代码）；1.20 `Inventory#getItem(i)` 对越界返回 `ItemStack.EMPTY` | ①读 map 时丢弃 null key（其余正常写入）；②`gunSlotIndex < 0 \| >= inventory.getSizeInventory()` 直接 return |
| 2 | `network/message/ClientMessageInstallVirtualAttachment.java:70-80` | 同上两类：槽位越界 AIOOBE；`attachmentType` 为 null 时 `VirtualOemAttachment.isAllowedVirtualOem(..., null)` / `getAttachment(gun, null)` 内部 NPE | 同上 | 加槽位夹紧 + `attachmentType == null` 直接 return |
| 3 | `network/message/ClientMessageRefitGun.java:74-84` | `attachmentSlotIndex` / `gunSlotIndex` 两个槽位都来自报文、都无夹紧 → AIOOBE | 同上（1.20 `getItem` 越界返回 EMPTY） | 两个槽位一起夹紧（`< 0 \|\| >= getSizeInventory()`） |
| 4 | `network/message/ClientMessageUnloadAttachment.java:61-72` | 槽位越界 AIOOBE；`attachmentType` null 传入 `getAttachment/unloadAttachment` | 同上 | 槽位夹紧 + `attachmentType == null` return |
| 5 | `network/message/ClientMessageSetAttachmentVariant.java:77-89` | 槽位越界 AIOOBE；`attachmentType` null | 同上 | 同上 |
| 6 | `network/message/ClientMessageSetSlotAdapter.java:77-86` | 原本已有槽位夹紧，但 `attachmentType` 只挡了 `NONE`，`readEnum` 返回的 **null** 会进 `allowAttachmentType(gunItem, null)` | 同上 | 守卫改为 `attachmentType == null \|\| == NONE`（并保留原有 `player == null`） |
| 7 | `network/message/ClientMessageAmmoStorageClick.java:76-88` | `action`（枚举）为 null 时进 `AmmoStorageMenu.handleInteraction` 的 switch → NPE | 1.20 越界枚举在 decode 阶段就抛异常，不会到业务层 | `message.action == null` → return |
| 8 | `network/message/ServerMessageBulletHitBlock.java:154-166` | `direction`（`ForgeDirection`）为 null → 客户端 `HitBlockEffectHandler.handle` 用它决定贴面 → 渲染线程 NPE | 同上 | handle 里 `direction == null` 直接丢弃该效果包 |
| 9 | `resource/index/CommonGunIndex.java:63-84, 100-107` | **服务端起不来**：枪包 json 显式写 `"reload": null` / `"recoil": null` / `"fire_mode": null` 时 `checkData` 里 `data.getReloadData().getType()`、`getFireModeSet().isEmpty()`、`checkRecoil(data.getRecoil().getPitch())` 抛 NPE；NPE 不在 `JsonDataManager#apply` 的 `catch(JsonParseException\|IllegalArgumentException)` 覆盖内 → 一路抛到 `CommonAssetsManager#reload()` 被 `catch(RuntimeException)` 记录后**重抛** → 服务端启动失败 | 上游 POJO 的这些字段带非 null 默认值（`new GunReloadData()`/`new GunRecoil()`），只有 json 显式写 null 才会是 null；上游同输入也 NPE，但本工程的装载契约是「单文件失败只打日志」 | 补 3 个 `Preconditions.checkArgument(..., "reload data is empty" / "fire mode is empty" / "recoil data is empty")`，把 NPE 转成 IAE → 走既有的单文件跳过路径 |
| 10 | `resource/filter/RecipeFilter.java:63-100` | 配方过滤 json 写错（顶层不是对象，或 `"whitelist": "tacz:ak47"` 不是数组）→ gson 2.2.4 `getAsJsonObject()` 抛 **IllegalStateException**、`getAsJsonArray(name)` 抛 **ClassCastException**；两者都不在装载器 catch 范围 → 同 #9 的启动崩溃 | 1.20 侧同样会抛，但 1.20 的 `GsonHelper`/codec 路径把它们归一到 `JsonParseException`（由 reload 管线按文件隔离） | 前置 `isJsonObject()` 校验 + 新增 `getArray()` 帮助方法（缺成员/显式 null → 视为无列表；有成员但不是数组 → `JsonParseException`），彻底消除 CCE/ISE |

## 本轮核对通过（不构成崩溃）

| 项 | 结论 |
|---|---|
| 命令非玩家分支 | `RootCommand`/`DebugCommand`/`OverwriteCommand`/`PegboardStressCommand:150` 全部走 `sender instanceof EntityPlayerMP` 或 `ICommandSender` 通用 API；`CommandTargets.getLivingEntities` 对 `null/空 token`、`@e` 空集合、`PlayerSelector` 返回 null 全部抛 `PlayerNotFoundException`（原版语义），无强转；`CommandBase.getPlayer` 自带 not-found 异常。**无崩溃点** |
| 配置侧 null | 3 个 `config/util/*` 读取器（`InteractKey/GunPegboard/HeadShotAABBConfigRead`）首行就 `configData == null → return`；`AmmoBlockConfigRead.readBlockList` 同时挡 null 列表与 null 元素；`ServerMessageSyncConfig.fromConfig` 用 `safeInt/safeDouble/Boolean.TRUE.equals/copyOf` 兜住 `ConfigValue.get()` 的 null（配置未 load 时不会把 0 当默认值用） |
| 索引/POJO 层 | `CommonAttachmentIndex`/`CommonAmmoIndex`/`CommonAttachmentVariantIndex`/`CommonSlotAdapterIndex`/`CommonBlockIndex` 的 `checkIndex/checkData` 全用 `Preconditions`（IAE → 单文件跳过）；两个 POJO 的列表 getter 已是 `== null ? List.of() : field`（`AttachmentVariantIndexPOJO.getTargets/getTargetTags`、`SlotAdapterIndexPOJO.getAllowedAttachments`）；`SmokeDirectionData` 的每个 `getAsJsonArray/getAsFloat` 都有 `isJsonArray/isJsonPrimitive` 前置判断 |
| `readItem()`/`readNbt()` 的 null 哨兵 | `PacketBuffer.readItem/readItemStack` 空栈返回 **null**（1.20 返回 `ItemStack.EMPTY`）、`readNbt` 返回 null。逐个核对 6 个用到的包：`ServerMessageLevelUp`（客户端 handler 是 no-op）、`ServerMessageGunDraw`（原样进 `GunDrawEvent` 构造器，事件类不 deref）、`AbstractShooterGunEventMessage` 子类 7 个（只投递事件）、`ServerMessageAmmoStorageContents`（已 `sample == null` 判非法）。**消息层无解引用，无需改** |
| `NetworkTaskQueue` handler 的 LocalPlayer/world | 本作用域 55 个包里客户端分支全部只投 `ClientSideHooks`（不直接碰 `Minecraft`）；服务端分支全部 `player == null → return`（含本轮统一补齐）。**唯一残留风险在 Lead 作用域的 `client/ClientProxy.java:342-357`：`bulletHitBlock` 直接把 `Minecraft.getMinecraft().theWorld` 传给 `HitBlockEffectHandler.handle`，而后者第一行就 `level.getBlock(...)`（`HitBlockEffectHandler.java:55`）——排队任务在「玩家退出世界/回到标题界面」后 drain 时会 NPE**。建议加 `World world = ...; if (world == null) return;` |
| `ServerMessageSyncGunPack` 拆包/解压失败 | `Receiver.accept` 的不一致分片抛 IAE、`readCache` 抛 DecoderException、gzip 长度不符抛 IOException —— 三者都在客户端主线程的 `ClientSideHooks.syncGunPack` 里被 `catch (IOException \| RuntimeException)` 兜住（只丢这一份快照），不会崩 |
| 资源管理器 `@Nullable` 返回值 | 本作用域内对 `getGunData/getAmmoIndex/getAttachmentIndex/getAttachmentData/getScript` 的所有调用点都经 `Preconditions` 或显式判空（`CommonGunIndex:54`、`CommonAttachmentIndex:41`、`CommonGunIndex:130-137`）。**跨作用域的高危点见 §2 的 B/C（ClientProxy）与本表最后一条** |

## 本轮未修（需记录 / 需别人处理）

1. **`writeResourceLocation(null)` 会在发送侧 NPE**：mcshim `PacketBuffer.writeResourceLocation` 直接 `value.toString()`。`ServerMessageSound/GunUnload/GunHurt/GunKill` 的 `gunId`/`gunDisplayId` 若为 null（只在服务端自身数据缺失这种本就不一致的状态下出现），发包子线程会抛，表现为「服务端主线程崩在 send」。上游 1.20 写 null 同样 NPE（非 null 哨兵），属同源行为，**未改**（改需 5 个消息类都塞占位 id，属 invent 语义）。
2. `ServerMessageSyncedEntityDataMapping.read` 的 `size`（`readInt`）无上界：不会预分配，只会按可读字节耗尽而抛 DecoderException，属可接受；未改。
3. 客户端在收到 `ServerMessageSyncGunPack` 之前的空窗期里，所有 `ClientSideHooks` 分支都依赖 Lead 侧实现自行判空（`ClientAssetsManager` 已有护栏）；`resource/**` 侧只能保证「不主动给 null 数据」。

---

## 第二轮补充：readItem / readEnum 空值消费方逐个核对（Lead 指派）

前提事实：mcshim `PacketBuffer.readItem/readItemStack` 空槽返回 **null**（1.20 返回 `ItemStack.EMPTY`）；
`docs/YSM_COMPAT_HANDOFF.md:135` 已把「1.7.10 空栈 = `stack == null || stack.getItem() == null`」定为对外约定，
因此**本工程把 null 当作 EMPTY 的等价物是既定语义**，要核的是「消费方有没有按该约定判空」。

### A. 本作用域 5 个 readItem 调用点（全量机检：`readNbt` 在本作用域 0 次调用）

| 位置 | 解码后去向 | 判定 |
|---|---|---|
| `ServerMessageAmmoStorageContents:100` | `readItem()` → `sample` | **安全**：紧随其后的 `if (id < 0 \|\| sample == null \|\| count <= 0 \|\| remaining < 0) throw DecoderException` 已挡；且写入侧 `AmmoStorageHelper.createAmmoStack` 走 `AmmoItemBuilder.build()`，恒返回非空栈（`AmmoItemBuilder.java:31-37`），正常包不会出现 null |
| `ServerMessageGunDraw:45-46` | `previousGunItem`/`currentGunItem` → `GunDrawEvent` | **安全**：`GunDrawEvent` 只存字段（`GunDrawEvent.java:19-25`），构造器里的 `postEventToKubeJS` 是 no-op（`KubeJSGunEventPoster.java:17-19`），仓内无任何 `GunDrawEvent` 监听器解引用栈；`api/event/common/*` 全包机检 **无** `getItem/getTag/copy` 调用。第三方（YSM）按 handoff 文档自带判空 |
| `AbstractShooterGunEventMessage:57` | `gunItemStack` → 7 个 shooter 事件 | **安全**：同上（事件类只存字段） |
| `ServerMessageLevelUp:41` | `gun` | **安全**：客户端 handler 是显式 no-op（`ServerMessageLevelUp.java:114-119`） |

### B. 空槽 null（`inventory.getStackInSlot` = null）的 8 个服务端消费点

`ClientMessageRefitGun:75-76`、`SetSlotAdapter:85`、`SetAttachmentVariant:78`、`UnloadAttachment:62`、
`InstallVirtualAttachment:71`、`LaserColor:94`、`PlayerUnloadGun:76`、`AmmoStorageInteract:99` —— **逐个读上下文后判定全部 null 安全**：

- 3 处显式判空：`AmmoStorageInteract`（`gunStack == null → return`）、`PlayerUnloadGun`（`gun == null → showCannotUnload`）、`LaserColor`（`IGun.getIGunOrNull` 后 `if (iGun != null)`）；
- 其余 5 处第一跳都是 `IGun.getIGunOrNull(stack)` / `IAttachment.getIAttachmentOrNull(stack)`，二者对 null 栈显式返回 null（`IGun.java:31`、`IAttachment.java:17-20`），随后 `if (iGun == null / attachment == null) return`；
- `ClientMessageRefitGun` 的 `attachmentItem` 可能是 null（空槽）→ `iGun.allowAttachment(gunItem, null)` → `getIAttachmentOrNull(null)` 返回 null → **返回 false**（`AbstractGunItem.java:419-428`），与上游「EMPTY 不是 IAttachment → false」行为等价，无需改动；
- 本轮已补的是这些点的**槽位越界**（AIOOBE，见上一节 #1-#6）与**枚举 null**（`attachmentType`/`action`/`direction`），不改正常包语义。

### C. Lead 点名的跨作用域两处（只报告）

1. `entity/sync/core/Serializers:302 (ITEM_STACK.read)/:252 (TAG_COMPOUND.read)`：序列化器返回 null 是**正确的** 1.7.10 空值语义，且**全仓 0 处引用**（`grep Serializers.ITEM_STACK|TAG_COMPOUND` 无命中；`entity/sync/ModSyncedEntityData.java:27-83` 注册的 8 个 key 默认值全是 `-1L/0f/false/-1/new ReloadState()` 这类非 null 值）。
   顺带核了接收链路：`ServerMessageUpdateEntityData.onHandle` → `SyncedEntityData.set` → `DataHolder.set`，其中 `DataHolder.java:27` 的 `!entry.getValue().equals(value)` 是**潜在 NPE**（若某 key 默认值为 null），但按现有 8 个 key 的默认值不会触发；若日后新增「默认 null」的 key，改成 `!Objects.equals(...)` 即可。**属 entity 作用域，未改。**
2. `entity/sync/core/DataEntry.read:29-36` 的 `Validate.notNull(key)` 在「客户端还没收到 `ServerMessageSyncedEntityDataMapping` 映射表」时会抛 NPE，且抛在 `fromBytes`（Netty 线程）→ 包被丢弃/连接异常。上游同源（同 API）。**属 entity 作用域，未改。**

### D. readEnum 改为解码期抛异常的影响（api-shim-review 改动）

结论：**本作用域没有任何消息类依赖 `readEnum` 返回 null 的旧行为**。全部调用点分两类：
① 只在 `read()` 里存字段、消费点在 handle 里判空（本轮补的守卫）→ 改动后守卫变成防御性死代码，无副作用；
② `ServerMessageSyncGunPack:162` 的 `buf.readEnum(DataType.class)` 直接进 `EnumMap` —— 改动后从「null key → EnumMap.put NPE」升级为「干净的 DecoderException」，**是改善**，无需改。
