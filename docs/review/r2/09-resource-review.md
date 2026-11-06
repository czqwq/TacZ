# r2-09 · `resource/**` 深度审查（r3 / task-14）

- **审查人**：`gui-review`
- **作用域**：`src/main/java/com/tacz/guns/resource/**`（115 文件）+ `client/gui/overlay/InteractKeyTextOverlay.java`
- **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/**`、`item/**`、`event/ChangeGunPropertyEvent.java`
- **1.7.10 准绳**：`build/rfg/minecraft-src/java/**`（`ResourceLocation`、`ChatComponentTranslation`）+ 本工程
  `gson 2.2.4`（`resource/CommonAssetsManager.GSON`）
- **不重复 r2-07**：`.lang` key 差集、贴图存在性、232 个 index 引用完整性、`EnumMap` 审计、
  `LanguageRegistry` 时机、`BlockData`/`BlockIndexPOJO` 的显式 null 覆盖（i18n-icon 已修，本文只在「全表」里回引）
- **自查方式**：`mcp__idea__get_file_problems(errorsOnly=true)`（改动 3 文件全部 `errors: []`）；
  全包 grep 交叉核对调用点；**未运行任何 gradle 任务**

## 覆盖清单

**逐文件精读（38）**：`modifier/{AttachmentPropertyManager,AttachmentCacheProperty}`、
`modifier/custom/**`（18，全部走读 + 逐个 `eval` 调用点定位）、`index/**`（6）、
`network/{CommonNetworkCache,DataType,GunPackSync}`、`manager/{JsonDataManager,CommonDataManager,
GunDataManager,GunIndexManager,AttachmentDataManager,GunSmithTableRecipeManager,RecipeFilterManager,
AttachmentsTagManager,INetworkCacheReloadListener}`、`filter/{RecipeFilter,IFilter,LiteralFilter,RegexFilter}`、
`serialize/**`（15 全部读：4 个 index 反序列化器 + 11 个字段适配器）、`pojo/**`（41 字段级扫描 + 关键类精读）

**结构化产出**：① `DataType` 生产者-消费者矩阵（§二）；② 「非 null 默认值 + 可被写 null」POJO 字段全表（§三）；
③ `checkIndex` 覆盖与失败语义表（§四）；④ modifier 链驱动链（§五）

---

## 结论速览

| 级别 | 已修 | 未修（登记 / 建议下一轮） |
|---|---|---|
| **P1** | 1 | 0 |
| **P2** | 1 | 1 |
| **P3** | 1 | 4 |

**最关键 3 条**：

1. **`DataType` 矩阵只有 1 个空项：`ALLOW_ATTACHMENT_TAGS` —— 但上游同样为空，不是缺陷**（§二）。
   其余 14 项**生产者与消费者齐全**；`RECIPES`（r2 刚补生产者）与 `ATTACHMENT_TAGS` 各有专属消费路径。
2. **`checkIndex` 的「静默丢弃」只有 1 处，而且只能由「json 文件内容字面为 `null`」触发**
   （`JsonDataManager#apply` 的 `if (data != null)` 分支不落日志，§四）。6 个 index 的校验失败**都走
   ERROR 日志 + 单文件跳过**，不是静默。**校验被真正执行**：4 个 index 由 `JsonDeserializer` 调 `getInstance`，
   2 个由 `parseJson` 覆写调 `getInstance`（§四）。
3. **modifier 链是真的被驱动的**（§五）：`CommonProxy:113 registerModifier()`（17 个，注册顺序与上游逐行相同）→
   `postChangeEvent`（14 个调用点）→ `ChangeGunPropertyEvent.internalOnAttachmentPropertyEvent` →
   `AttachmentCacheProperty#eval` → 逐修饰符 `initCache`/`eval`，叠加公式
   （`addend += …; percent += …; multiplier *= max(m,0); value = addend*percent*multiplier` + 脚本二次求值）
   与上游**逐行相同**。

**本轮改动（3 文件）**：

| # | 文件 | 改动 | 级别 |
|---|---|---|---|
| 1 | `client/gui/overlay/InteractKeyTextOverlay.java` | **任务 B**：补回上游展示柜提示的 3 处判定（`isLocked` 守卫 / `getSlot` 槽位命中 / `occupied \|\| canPlaceHeld`） | P1 |
| 2 | `resource/index/CommonGunIndex.java` | `checkRecoil` 补 `keyFrame`/`getValue()` 判空：`{"value":null}` 或 `[null]` 原本会 **NPE 逃出单文件跳过路径** → 服务端起不来；现转成 `IllegalArgumentException`（与同类 reload/fire_mode 的既有做法一致） | P2 |
| 3 | `resource/network/CommonNetworkCache.java` | 类 javadoc 从 GBK/UTF-8 乱码还原为中文（按上游类注释） | P3 |

---

## 一、任务 B：`InteractKeyTextOverlay` 的展示柜提示（P1，已修）

**上游**（`tmp/.../client/gui/overlay/InteractKeyTextOverlay.java:82-95`）：

```java
if (!IGun.mainHandHoldGun(player) || block.getValue(GunDisplayCaseBlock.LOCKED)) return;      // :83
int slot = GunDisplayCaseSlotHelper.getSlot(block, pos, hit.getLocation());                    // :86
if (slot < 0 || !(level.getBlockEntity(pos) instanceof GunDisplayCaseBlockEntity case)) return; // :87
boolean occupied = !displayCase.getItem(slot).isEmpty();                                       // :90
boolean canPlaceHeld = GunPegboardItemConfigRead.canPlace(player.getMainHandItem());           // :91
if (occupied || canPlaceHeld) renderText(...);                                                 // :92-94
```

**1.7.10 前状**：只剩 `canPlace(held)` 一个条件 + 一个 TODO。后果（对上表逐条）：

| 场景 | 上游 | 修复前 | 修复后 |
|---|---|---|---|
| 对着**已有枪的格子**、手上拿不可放入的物品 | 提示「按 F」 | **不提示** | 提示 ✓ |
| 对着**已上锁**的展示柜 | 不提示（按了返回 false） | **照旧提示**（按了没反应，误导） | 不提示 ✓ |
| 点到方块但不在任何格子上（`slot < 0`） | 不提示 | 提示 | 不提示 ✓ |
| 手拿枪 + 空格子（可放入） | 提示 | 提示 | 提示 ✓ |

**修法**（1.7.10 等价物，全部为已实现 API）：

- `block.getValue(LOCKED)` → `GunDisplayCaseBlock.isLocked(metadata)`（`block/GunDisplayCaseBlock.java:113`）；
- `GunDisplayCaseSlotHelper.getSlot(BlockState, pos, Vec3)` →
  `GunDisplayCaseSlotHelper.getSlot(int metadata, int x, int y, int z, Vec3 hitLocation)`
  （`block/GunDisplayCaseSlotHelper.java:30`）；命中向量取 `MovingObjectPosition.hitVec`
  （1.7.10 的 `hitVec` 是 public 字段，等价上游 `getLocation()`）；
- `displayCase.getItem(slot).isEmpty()` → `getItem(slot)` 返回 **可能为 null**（1.7.10 无 `ItemStack.EMPTY`），
  故写成 `stored != null && stored.getItem() != null`。

**为什么这是 P1**：这是玩家发现「展示柜能交互」的唯一入口提示；上锁时给错误提示还会让人反复按键。

---

## 二、`DataType` 生产者-消费者矩阵（15 项，**空项 1 个 = 上游同为空**）

生产者 = 实现 `INetworkCacheReloadListener` 的管理器（`getType()`/`getNetworkCache()`，经
`CommonAssetsManager#getNetworkCache()` 汇总 → `ServerMessageSyncGunPack.createMessages` 下发）；
消费者 = 客户端 `CommonNetworkCache#fromNetwork`。

| DataType | 生产者 | 消费者 | 结论 |
|---|---|---|---|
| `GUN_DATA` | `GunDataManager`（`CommonAssetsManager:176`） | `fromNetwork` → `gunData` | ✅ 齐全 |
| `ATTACHMENT_DATA` | `AttachmentDataManager`（`:177`） | → `attachmentData`（含 `initModifiers`） | ✅ 齐全 |
| `AMMO_INDEX` | `CommonDataManager`（`:199`） | → `ammoIndex` | ✅ 齐全 |
| `GUN_INDEX` | `GunIndexManager`（`:201`） | → `gunIndex`（延迟到 index 全装完） | ✅ 齐全 |
| `ATTACHMENT_INDEX` | `CommonDataManager`（`:202`） | → `attachmentIndex` | ✅ 齐全 |
| `RECIPES` | `GunSmithTableRecipeManager`（`:245`，r2 新增） | `fromNetwork(Map)` 里**单独最后处理** → `registerRecipes`（因产物要查 index） | ✅ 齐全 |
| `RECIPE_FILTER` | `RecipeFilterManager`（`:193`） | → `recipeFilter` | ✅ 齐全 |
| `ATTACHMENT_TAGS` | `AttachmentsTagManager`（`:192`） | `fromNetwork(type,data)` 首行特判 → `resolveAttachmentTags`（路径前缀 `allow_attachments/` 的进 `allowAttachmentTags`） | ✅ 齐全 |
| **`ALLOW_ATTACHMENT_TAGS`** | **无** | **无** | ⚠ **空项**，但 **上游 1.20.1 同样 0 引用**（`grep ALLOW_ATTACHMENT_TAGS` 上游只有枚举声明）→ **保真，非缺陷**，不要"修" |
| `BLOCK_DATA` | `CommonDataManager`（`:195`） | → `blockData` | ✅ 齐全 |
| `BLOCK_INDEX` | `CommonDataManager`（`:236`） | → `blockIndex` | ✅ 齐全 |
| `ATTACHMENT_VARIANT_DATA` | `CommonDataManager`（`:178`） | → `attachmentVariantData` | ✅ 齐全 |
| `ATTACHMENT_VARIANT_INDEX` | `CommonDataManager`（`:209`，覆写 `parseJson`） | → `attachmentVariantIndex` | ✅ 齐全 |
| `SLOT_ADAPTER_DATA` | `CommonDataManager`（`:185`） | → `slotAdapterData` | ✅ 齐全 |
| `SLOT_ADAPTER_INDEX` | `CommonDataManager`（`:223`，覆写 `parseJson`） | → `slotAdapterIndex` | ✅ 齐全 |

**另有 3 项不属于 DataType，但不是空项（已在别处落地）**：

- `LootInjectionManager`（`CommonAssetsManager:194`）：战利品注入**只在服务端生效**（1.7.10 的箱子战利品表是
  世界生成期服务端行为），上游同样不下发 → 无 DataType 是**正确**的。
- `ScriptManager` / `AttachmentScriptManager`：Lua 脚本**不下发**，多人客户端 `getScript()` 返回 null ——
  **上游 `CommonNetworkCache#getScript` 也是 `return null;`**（上游 `:117-119`），而 `GunIndexManager#apply`
  已把 `has_unload_ammo_script` 单独塞进下发的 json（本工程 `:39-46`）来补偿 → 保真。
- `GunPackSync`：只是 gzip + 分片 + 顺序校验的传输层，不参与 DataType 分派。

> 顺带：`CommonNetworkCache#fromNetwork(type, data)` 的 `switch` **没有 `default` 分支**，
> 但 `ALLOW_ATTACHMENT_TAGS` 无生产者，所以不会有人下发它；若日后加生产者，需要同时加 case。
> 建议（不修）在 switch 末尾加 `default -> LOGGER.warn(...)`，让"新枚举值漏消费"变成可见日志。

---

## 三、「非 null 默认值 + 可被写 null」POJO 字段全表（41 文件 / 149 个带初始化的字段）

**机制**（i18n-icon r2-07b 实测 + 我这里按 gson 2.2.4 行为补充一条关键细节）：
gson 读到 `JsonToken.NULL` 会 `field.set(obj, null)` **覆盖**字段初始化值；
**但 `BoundField#read` 有 `if (value != null || !isPrimitive)` 判据** ——
所以**只有引用类型字段会中招，`int/float/boolean` 等基本类型被 JSON null 写到时会被跳过、保留默认值**。
因此风险集 = 「引用类型 + 非 null 初始化」。下表按危险度分组（`null` 默认值的字段天然无风险，已排除）。

### 3.1 高危：显式 null → **NPE**，且消费点密集（**建议下一轮统一在 getter 侧 normalize**）

| 字段 | 文件:行 | null 后第一个 NPE 点 | 消费点计数 |
|---|---|---|---|
| `bulletData` | `pojo/data/gun/GunData.java:55` | `CommonGunIndex#getBulletData()`（枪械 tooltip 第一行就调） | **31** |
| `recoil` | `GunData.java:100` | 已被 `checkData` 挡住（**已修**，见 §四） | — |
| `reloadData` | `GunData.java:85` | 已被 `checkData` 挡住 | — |
| `fireModeSet` | `GunData.java:88` | 已被 `checkData` 挡住 | — |
| `burstData` | `GunData.java:94` | `burstData.getCount()` 等 | 9 |
| `fireSound` | `GunData.java:82` | 开火音效路径 | 6 |
| `moveSpeed` | `GunData.java:109` | 移速倍率路径 | 4 |
| `ignite` | `pojo/data/gun/BulletData.java:37` | `getIgnite().isIgniteEntity()` | 6 |
| `allowAttachments` | `GunData.java:119` | `IGun#allowAttachmentType` 遍历 | 2 |
| `builtInAttachments` | `GunData.java:128` | 内置配件解析 | 6 |
| `exclusiveAttachments` | `GunData.java:122` | `GunDataManager#initExclusiveAttachmentModifiers` 遍历 | 8 |
| `fireModeAdjust` | `GunData.java:91` | `EnumMap` 字段（r2-07 已审计 gson 2.2.4 构造，**显式 null 仍会置 null**） | 多处 |
| `feed` / `cooldown` | `pojo/data/gun/GunReloadData.java:14,17` | 换弹时间路径 | — |
| `value`（float[]） | `pojo/data/gun/GunRecoilKeyFrame.java:13` | `CommonGunIndex#checkRecoil` 的 `value.length` → **NPE 逃出跳过路径** | **本轮已修** |
| `attachments`（EnumMap） | `pojo/data/gun/GunResult.java:17` | 指令/生成器路径 | — |
| `effects` | `pojo/data/attachment/MeleeData.java:29` | 近战效果遍历 | — |
| `chargeData` / `gunHeatData` / `inaccuracy` / `script` / `scriptParam` / `recoilPushData` / `ammoId` | `GunData.java:140,116,106,131,137,52,30` | **默认就是 null** → 显式 null 无变化 | 已判空 |
| `damageAdjust` | `pojo/data/gun/ExtraDamage.java:19` | 距离伤害表遍历 | — |
| `chargeType`（枚举） | `pojo/data/gun/ChargeData.java:8` | `switch(type)` / `== ` 比较（`==` 安全，`switch` NPE） | — |
| `targets` / `targetTags` | `pojo/AttachmentVariantIndexPOJO.java:28,31` | 已由 i18n-icon 修成 `== null ? List.of()` | 已修 |
| `allowedAttachments` | `pojo/SlotAdapterIndexPOJO.java:32` | 同上（已有 getter 判空） | 已修 |
| `filter` / `tabs` | `pojo/data/block/BlockData.java:20,23` | 已由 i18n-icon 修 | 已修 |
| `id` | `pojo/BlockIndexPOJO.java:25` | 已由 i18n-icon 修 | 已修 |
| `itemType` | `pojo/GunIndexPOJO.java:28` | 下游 `GunItemBuilder#build:110-112` 已判空 | 安全 |

### 3.2 判定与建议

- **为什么本轮不全改**：这批字段的"正解"是**在 POJO getter 侧统一 normalize**（i18n-icon 对
  `BlockData`/`BlockIndexPOJO`/`AttachmentVariantIndexPOJO` 采用的做法），一次改 `pojo/data/gun/**` 十几个类会
  与 r3 其它任务有冲突风险，且 `GunData` 是枪包 json 的核心契约（改动面最大）。**按 Lead 要求只登记，写进下一轮建议**。
- **优先级建议**：`GunData.bulletData`（31 个消费点，枪械 tooltip 第一行就会炸）→ `burstData`/`fireSound`/`moveSpeed`
  → `fireModeAdjust`（EnumMap）→ 其余。
- **另一类安全**：`GunRecoilKeyFrame.value` 属"数组字段"，即使 getter 侧 normalize 也要防长度 < 2；
  这类**已经在 `checkRecoil` 里显式校验**（本轮补了 null 判据），比 getter normalize 更合适。
- **口径提醒**：这些都是**上游同源代码**（1.20.1 的 POJO 初始化值一模一样），所以严格说不算移植回归；
  但本工程既有的装载契约是「单文件失败只打日志、不中断整批」，而 NPE 会穿透该契约 →
  **按本工程契约判，该修**。

---

## 四、`checkIndex()` 覆盖与「失败时的行为」

### 4.1 校验确实被执行（4 + 2 两条路径）

| index | 反序列化入口 | `getInstance`/`checkIndex` 是否被调 |
|---|---|---|
| `CommonGunIndex` | `serialize/CommonGunIndexSerializer`（`JsonDeserializer`） | ✅ `getInstance(pojo)` |
| `CommonAmmoIndex` | `CommonAmmoIndexSerializer` | ✅ |
| `CommonAttachmentIndex` | `CommonAttachmentIndexSerializer` | ✅ |
| `CommonBlockIndex` | `CommonBlockIndexSerializer` | ✅ |
| `CommonAttachmentVariantIndex` | `CommonAssetsManager:209-222` 匿名 `parseJson` 覆写 | ✅ `getInstance(pojo)` |
| `CommonSlotAdapterIndex` | `CommonAssetsManager:223-235` 同上 | ✅ |

> 若只注册 `JsonSerializer`（只写不读）会退化成反射填充 → 出现「index 全部是空壳」。这 4 个都是
> `JsonDeserializer`，**没有这个问题**（已逐个读过文件，均 `implements JsonDeserializer<...>`）。

### 4.2 失败语义：ERROR 日志 + 单文件跳过（**不是静默**）

- `JsonDataManager#apply:88-95`：`catch (JsonParseException | IllegalArgumentException)` →
  `LOGGER.error(marker, "Failed to load data file {}", id, e)` → **该文件不进入 dataMap**。
  4 个 index 序列化器把 `IllegalArgumentException` 包成 `JsonParseException`，所以**必然被这条路径接住**。
- 网络路径 `CommonNetworkCache#fromNetwork:297-325`：`catch (IllegalArgumentException | JsonParseException)` →
  `LOGGER.warn(..., "Failed to parse data from network for {} with id {}", type, id, e)`。
- 配方路径 `GunSmithTableRecipeManager#loadRecipes:127-138`：`catch (RuntimeException)` → warn + 计数汇总。

### 4.3 「静默丢弃」**只有 1 处**

`JsonDataManager#apply:90-92`：

```java
T data = parseJson(element);
if (data != null) { dataMap.put(id, data); }   // ← data 为 null 时【无任何日志】就丢掉
```

- 触发条件：**json 文件内容字面为 `null`**（`gson.fromJson("null", X.class)` → null），
  或某个 `parseJson` 覆写返回 null。
- 现有 6 个 index 的 `getInstance` 都不可能返回 null（要么返回实例、要么抛 IAE），
  其余 POJO 走反射也不会返回 null，所以**当前实际不可达**，但它是唯一"无日志丢弃"通道。
- **建议（登记，未改）**：把 `else` 补一行 `LOGGER.warn(marker, "data file {} parsed to null, skipped", id)`。
  这是 1 行改动，不影响行为；本轮未动是为了不与 r3 其它任务抢 `JsonDataManager`（`manager/**` 可能被 recipe 任务碰到）。

### 4.4 校验项 vs 上游

`CommonGunIndex` 比上游**更严**（多出 4 条 `Preconditions`：`reloadData`/`reloadData.getType()`/`fireModeSet`/
`recoil` 的非空判断），其余 5 个 index 的校验项与上游逐条对齐（`type`/`display`/`data`/`itemBlock`/`filter` 等）。
上游缺、本工程也缺的（保持保真）：`GunData.bulletData` 未校验（见 §三 3.1 第 1 行）、
`CommonAmmoIndex` 只校验 POJO 非空（上游同）。

---

## 五、消费环节复核

### 5.1 `resource/modifier/**`（20 文件）——**真的被驱动，叠加语义与上游逐行相同**

驱动链（全部为实测调用点）：

```
CommonProxy:113  AttachmentPropertyManager.registerModifier()      // 17 个修饰符，注册顺序与上游 MODIFIERS.put 顺序逐行相同
   ↓ (14 个 postChangeEvent 调用点：ClientProxy:416 / LocalPlayerDraw:86 / LocalPlayerFireSelect:62 /
      LivingEntityDrawGun:83 / LivingEntityFireSelect:55 / LivingEntityMixin:176 /
      ClientMessage{InstallVirtualAttachment,RefitGun,SetAttachmentVariant,SetSlotAdapter,UnloadAttachment} /
      ClientIndexManager:82 …)
AttachmentPropertyManager#postChangeEvent
   ↓
ChangeGunPropertyEvent#internalOnAttachmentPropertyEvent              // event/**，只读
   ↓  event.getCacheProperty().eval(gunItem, gunIndex.getGunData())
AttachmentCacheProperty#eval
   ↓  ① getModifiers() 逐个 initCache(gunItem, gunData)
   ↓  ② AttachmentDataUtils#getAllAttachmentData → data.getModifier().forEach(id → cacheModifiers[id].add(value))
   ↓  ③ 逐个 modifier.eval(list, cacheValue)  ← 若是空表则跳过（该枪没有该修饰符）
```

- **叠加顺序**：`LinkedHashMap` 迭代序 + `AttachmentDataUtils#getAllAttachmentData` 的附件遍历序（与上游一致）。
- **叠加公式**（`AttachmentPropertyManager#eval`）：`addend` 以默认值为起点累加、`percent` 以 1 为起点累加、
  `multiplier` 连乘且**逐项 `Math.max(m, 0)` 截断负值**、`percent` 最终 `Math.max(,0)`、
  `value = addend*percent*multiplier`，最后按 `Modifier#getCompiledFunction()` 顺序做脚本二次求值 —— **与上游逐行相同**。
- **布尔叠加**：默认 true → `allMatch`；默认 false → `anyMatch`（上游同）。
- **互斥**：不存在"互斥表"，互斥由各 modifier 自身实现（如 `RecoilModifier` 走 `Pair` 双分量、
  `InaccuracyModifier` 按 `InaccuracyType` 分键），与上游一致。
- **上游同样存在的性能小瑕（P3，未改）**：`AttachmentDataManager#initModifiers:41` 把
  `gson.toJson(element)` 放在 `getModifiers().forEach` **内部** → 每个配件 data 文件序列化 17 次、再解析 17 次。
  上游 `AttachmentDataManager.java:27-42` **完全相同**（保真），只登记不改。

### 5.2 `resource/filter/**`（4 文件）——**真的在过滤配方**

- `RecipeFilter(Deserializer)` 注册在 `CommonAssetsManager.GSON:126`；`RecipeFilterManager` 是
  `RECIPE_FILTER` 的生产者；`CommonBlockIndex#checkData:49-50` 要求 `data.getFilter()` 能取到（`recipeFilter != null`），
  否则以「没有对应数据文件」报错（该误导性文案在 `BlockData.java:30` 已有注释，属已知）。
- **实际消费点**：`GunSmithTableScreen#classifyRecipes` → `filter.filter(recipeIds, Pair::getValue)`
  （`client/gui/GunSmithTableScreen.java:213`）→ 工作台只显示通过过滤的配方 ✔。
- `LiteralFilter` / `RegexFilter` / `IFilter` 结构简单，`RecipeFilter#filters` 为**白/黑名单**两段
  （上游同）；非法 `ResourceLocation` 走 `tryParse → null`（对应上游 `ResourceLocation.tryParse` 语义），
  跳过而不抛 —— 这类 `tryParse` 全包共 3 处（`RecipeFilter:161`、`CommonSlotAdapterIndex:96`、
  `GunDataManager:62`），都是**返回值判空**而非吞异常，不属于"静默丢弃"。

### 5.3 `resource/pack/**`（5 文件）——重载时序与缓存失效

- `GunPackLoader` 产 `List<IGunPackSource>`（`DirectoryGunPackSource` / `ZipGunPackSource`），
  `JsonDataManager#packSources()` **每次现取**（`:123-126`）→ 重载后能读到新包列表 ✔。
- `CommonAssetsManager#reloadAndRegister` 的注册顺序（`:174-245`）保证「data 先于 index、index 先于 variant/slotAdapter、
  配方最后」；末尾 `AllowAttachmentTagMatcher.resetCache()`（`:251`）→ `networkSyncMessages` 重建（`:252`）→
  `initLazyRecipes()`（`:253`）三件收尾动作都在同一线程内顺序执行 ✔。
- 客户端侧枪包资源包的挂载/失效问题（每轮 `refreshResources` 被清掉）已在 r2 `03-client-ui` 的 P1 修复，
  本轮复查 `GunPackResourcePack.invalidateCache()` 在 `ClientProxy:460` 有对应调用 ✔。

### 5.4 `resource/serialize/**`（15）与 `pojo/**` 的对称性

- 15 个适配器全部**只实现 `JsonDeserializer`**（读方向）。这是**有意**的：本工程不下发"序列化后的对象"，
  下发的永远是**原始 json 字符串**（`CommonDataManager#apply:42-44` 把 `element.toString()` 存进 networkCache），
  客户端再重新走同一套 `JsonDeserializer`。→ **不存在 read/write 不对称问题**（没有 write 方向）。
  `JsonSerializer` 意义上唯一的"写"是 `Gson#toJson`（`AttachmentDataManager:41`、`GunDataManager` 的
  exclusive 处理），走反射默认输出，与读无关。
- **枚举容错**：`EnumMapTypeAdapterFactory`（`serialize/EnumMapTypeAdapterFactory.java`）专治 gson 2.2.4 不为
  `EnumMap` 字段构造实例的问题（r2-07 已实测 14 个 `*_data.json` 的 `fireModeAdjust` 受影响并修复）；
  `ModifierTypeAdapterFactory` 处理 `Modifier` 多态字段。两者都只做"构造正确容器"，不改变取值语义 ✔。
- **缺字段行为**：缺字段 → 保留 POJO 初始化值（正常路径）；**显式 null → 覆盖**（§三 全表）。
  这与上游 gson 2.10 行为一致，**不是移植差异**，差异只在"本工程的装载契约要求 NPE 不能逃出单文件跳过"。

### 5.5 `resource/manager/**` 交叉复核

- `GunSmithTableRecipeManager`（i18n-icon 自审）：`reload()` 先 `GunSmithTableRecipe#clear()` 再登记 ✔；
  `isGunSmithTableRecipe` 对 `type` 缺失采取宽容（`materials`+`result` 存在即认为是）—— 与
  `GunSmithTableSerializer` 的实际要求一致 ✔；单条失败 warn + 计数 ✔。
- **一处口径差异（P3，未改）**：`CommonNetworkCache#registerRecipes` 解析网络 json 时用
  `catch (RuntimeException)` → warn；而 `CommonNetworkCache#fromNetwork` 的常规分支只 catch
  `IllegalArgumentException | JsonParseException`。两条路径的 catch 宽度不一致，功能上无害（更宽的那条更安全），
  只登记。

---

## 六、只登记 / 建议下一轮

| # | 位置 | 说明 | 建议 |
|---|---|---|---|
| 1 | `pojo/data/gun/**` 共约 20 个引用类型字段 | §三 3.1 全表；显式 `"xxx": null` 会把默认值覆盖成 null → NPE | **统一在 getter 侧 normalize**（抄 i18n-icon 对 `BlockData`/`BlockIndexPOJO` 的做法）；优先级按消费点：`bulletData`(31) > `burstData`(9) > `exclusiveAttachments`(8) > `fireSound`/`builtInAttachments`/`ignite`(6) > `moveSpeed`(4) |
| 2 | `manager/JsonDataManager.java:90-92` | 唯一"无日志丢弃"通道（`data == null` 静默跳过） | 加 1 行 `LOGGER.warn(...)`；当前不可达，属加固 |
| 3 | `network/CommonNetworkCache.java` `switch` 无 `default` | 未来新增 `DataType` 生产者若忘了加 case，会静默不消费 | 加 `default -> LOGGER.warn("unconsumed data type {}", type)` |
| 4 | `manager/AttachmentDataManager.java:41` | `gson.toJson(element)` 在 17 次循环内重复 | 提到循环外；**上游同款，属保真**，可不动 |
| 5 | `network/CommonNetworkCache.java:224-243` vs `:297-325` | 两处 catch 宽度不一致 | 统一为 `RuntimeException`（更安全），纯健康度 |
| 6 | `filter/RecipeFilter` 的 `filters` 语义（白/黑名单） | 与上游一致，未发现问题 | 无 |

**结论**：`resource/**` 的四个环节（解析 / 校验 / 合并 / 消费）**没有发现第二个 P1 级断链**；
`DataType` 的 1 个空项与 `checkIndex` 失败语义都经实测排除；真正成体系的风险是 §三 的「显式 null 覆盖」，
需要在下一轮按 POJO 逐类 normalize。
