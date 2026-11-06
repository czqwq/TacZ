# r2-05 · 上游保真审查

> **作用域**：只读全仓。准绳 = 上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（799 文件）+ 1.7.10 平台
> `build/rfg/minecraft-src/java/**`；审查对象 = `src/main/java/com/tacz/guns/**`（快照 822 文件；审查期间其他
> teammate 正在改 `client/**` 等（`git status` 57 条工作区状态；仓库只有 1 个初始提交，绝大多数移植文件未提交），且观察到最后计数前的 10 分钟里本移植文件数仍从 821 变到 822，故下列计数均为**快照值**）。
> **方法**：自写只读符号抽取/比对脚本（`build/fidelity/*.js`，不跑 gradle、不改源码），产出
> ① 类清单 ② 方法声明清单（可见性/static/名字/元数/形参/返回/行号）③ 字段 + `@SerializedName` 清单
> ④ 嵌套类型清单 ⑤ 方法体行数；再做重点区域人工逐方法核对与 api/YSM 契约逐符号核对。脚本与复现方式见
> `docs/review/r2/05-fidelity-drift-data.md` §G。
> **覆盖统计**：上游 799 类 / 4993 方法声明（public+protected 3847）；本移植 822 类 / 5915 方法声明（4516）。
> 逐条定性：上游独有方法 **502** 条、同名文件内新增 **709** 条、改名候选 **264** 对、api 严格签名差异 **41** 条、
> 字段差异 816 条（`@SerializedName` 漂移 **0**）、配置键 89 个、YSM 契约 12 行（11 组）、方法体规模配对 4688/5584。
> 全量机器清单：**[05-fidelity-drift-data.md](05-fidelity-drift-data.md)**。

---

## 结论速览

| 类别 | 计数 | 判定 |
|---|---|---|
| (a) 合法平台改名 / 平台化 | 上游独有 502 条**全部可归因**：网络机制改名 88 + 名字保留但参数平台化 14 + 机制由新类承接 91 + 1.20 平台钩子 309 | ✅ 无问题（映射表 §三 + 数据文件 B2/B5） |
| (b) 移植特有新增 | 新增类 **92**（全部带类 javadoc，89/92 明示上游对应物+理由）；同名文件内新增方法 **709**（绝大多数是 1.7.10 平台覆写/getter/适配辅助） | ✅ 有依据；3 个文档/卫生问题见 §一 O-2/O-3/O-4 |
| (c) **真漂移 / 乱搓** | **1 条**（D-1：多段实体机制未移植且未记录） | ✅ **复审时已由 gun-logic 修复并经我独立复核闭环**（见 §一「D-1 处置」）；保留的 1 处 1.7.10 原版行为差异已登记 `r2-03 D11` |
| TACZ 自有方法名被改成「看起来更合理的自创名」 | **0 条**（264 对改名候选逐对核对，100% 是 1.20→1.7.10 平台 API 改名） | ✅ 用户疑虑基本排除 |
| api/** 硬契约（YSM 11 组） | 12/12 符号逐项通过；类名/方法名/字段名/参数顺序零漂移 | ✅ 独立复核确认前轮结论 |

**最关键的 3 条结论**：

1. **「自己乱搓方法名」在本移植里基本不存在，且是机械可证的**：`entity/shooter/**`（最容易「顺手简化」的状态机）
   15 文件 47 个方法，上游方法名集合差异 **0**，仅新增 1 个有完整 javadoc 的 `ShooterDataHolder#getCurrentGunItem`
   （`entity/shooter/ShooterDataHolder.java:153`，修 1.7.10 空栈 NPE）。全部 264 对「上游方法消失 + 本移植出现新方法」
   的候选，逐对都能对应到 1.20 平台 API→1.7.10 平台 API（`use→onBlockActivated`、`appendHoverText→addInformation`、
   `inventoryTick→onUpdate`、`newBlockEntity→createTileEntity`、`load/saveAdditional→readFromNBT/writeToNBT`…），
   没有一对是 TACZ 业务方法被换成自创名。方法体规模比对也佐证没有大段逻辑被「简化」：
   上游 ≥8 行而本移植 <50% 的方法**全仓只有 1 个**（`compat/ar/ARCompat#renderLaser`，反射骨架性质）。
2. **唯一真漂移是「机制级遗漏」而不是「乱搓」**：上游 `EntityKineticBullet` 的 `MaybeMultipartEntity`
   （上游 `:384-394`）——「命中部件实体 → 分离 hitPart/core，伤害打 part、事件/击退/无敌帧用 core」这套机制
   在初版审查时完全没有移植，本移植 `onHitEntity` 直接把 part 当普通实体处理，且**类 javadoc 与 PORT_STATUS 均无记录**。
   后果集中在末影龙（以及其他 1.7.10 `IEntityMultiPart`）：`EntityHurtByGunEvent.Post`/`EntityKillByGunEvent` 不触发
   （伤害数字/击杀特效缺失）、穿甲伤害段被龙本体无敌帧吞掉、击退失效、非玩家来源子弹对龙完全不掉血。
   **【复审更新】gun-logic 已按本报告 §一 的改法在 `entity/**` 修复（`r2-03 F8`），我于 19:28 独立复核通过：**
   机制缺口（①②③）闭合；④「非玩家子弹打龙部件不扣血」是 1.7.10 原版 `EntityDragon#attackEntityFromPart` 的平台行为
   （`EntityDragon.java:557`），按要求**不绕过**、已登记 `r2-03 D11`。详见 §一「D-1 处置」。
3. **整体保真度高于同类移植项目的常见水平**：69 个「上游有本移植无」的类 = 61 个 1.20 专属兼容模块（按 AGENTS.md
   「已确认决策 2」只保留反射骨架，属**范围裁剪而非漂移**）+ 8 个平台强制替换（每个都能指到替换类与 javadoc 依据）；
   92 个新增类全部有类 javadoc 且 89 个明示上游对应物；代码里 222 处「降级」、91 处 `TODO(L2)` 主动记录 1.7.10 差异；
   配置键 89/89 逐键一致（含默认值与范围）；数据驱动 POJO 的 `@SerializedName` 漂移 = 0
   （`resource/pojo/**` 172 个序列化字段两侧完全一致）。**是否需要立刻修**：D-1 已在复审时修复闭环，无需其它阻塞项
   （仅建议顺手清理 §一 O-4 的临时探针文件、O-3 的过期 TODO）。

---

## 一、真漂移 / 乱搓清单

### D-1【中｜高置信】多段实体（part→core）机制未移植且无记录

| 项 | 内容 |
|---|---|
| 本移植位置 | `entity/EntityKineticBullet.java:441`（`onHitEntity` 入口）、`:457`（`createDamageSources(entity)` 直接吃 part）、`:498-504`（`entity instanceof EntityLivingBase` 判 core）、`:507`（爆炸时只重置 part 的 `hurtResistantTime`）、`:518-565`（`entity instanceof EntityLivingBase` 才发 Post/Kill 事件）、`:731-737`（`tacAttackEntity(Entity hitEntity, ...)` 只操作 part）、`util/EntityUtil.java:199-208`（`getPartParent` 只被 `isBulletDamageIgnored` 用，`:189`）<br>⚠️ 该文件正被 r2 task-3（gun-logic）并发修改，行号会漂移；以方法名 `onHitEntity` / `createDamageSources(Entity)` / `tacAttackEntity(Entity,float,Pair)` 定位为准（本行号快照 2026-11-04 19:15） |
| 上游对应位置 | `entity/EntityKineticBullet.java:384-394`（`record MaybeMultipartEntity(Entity hitPart, Entity core)` + `of(Entity)`）、`:396`、`:411`（`createDamageSources(MaybeMultipartEntity.of(entity))`）、`:427`（`var parts = MaybeMultipartEntity.of(entity)`）、`:448-458`（`parts.core() instanceof LivingEntity` 才做击退）、`:463`/`:599`/`:603`（`parts.core().invulnerableTime = 0`）、`:467-480`（Post/Kill 事件用 `parts.core()`）、`:601`/`:605`（伤害施加到 `parts.hitPart()`） |
| 差异 | 上游把「命中部件」与「核心实体」分离：伤害源按 **hitPart 的类型** 选（entity tag：magic/void/pretend-melee），伤害施加到 **hitPart**，而**无敌帧重置、击退、Hurt/Kill 事件、爆炸无敌帧都作用在 core**。本移植无 core 概念，全部直接用 part。 |
| 1.7.10 平台事实（判断依据） | `build/rfg/minecraft-src/java/net/minecraft/entity/boss/EntityDragonPart.java:46-48`：`attackEntityFrom` → `entityDragonObj.attackEntityFromPart(this, source, amount)`；`EntityDragon.java:542`：`attackEntityFromPart` 内 (a) 非头部部件伤害 `= damage/4+1`，(b) **仅当 `source.getEntity() instanceof EntityPlayer || source.isExplosion()` 才 `func_82195_e()`（真正扣血）**。`EntityDragonPart.canBeCollidedWith()` 返回 true，所以子弹**能命中**部件（不是打不到）。 |
| 危害（玩家可见） | ① 对末影龙：`EntityHurtByGunEvent.Post` / `EntityKillByGunEvent` 永远不触发（part 不是 `EntityLivingBase`）→ 伤害数字 HUD、击杀特效、`ServerMessageGunHurt/GunKill` 全部缺失；上游是拿 core 触发。② 普通+穿甲两段伤害之间只在 **part** 上清零 `hurtResistantTime`，龙本体无敌帧未清 → 第二段（穿甲）被龙的无敌帧吸收，实际伤害低于预期。③ `KnockBackModifier` 只在 `entity instanceof EntityLivingBase` 分支生效 → 对龙不生效。④ 非玩家来源子弹（其他生物开枪、脚本 API `shoot`）打龙：1.7.10 平台只在 player/爆炸时扣血 → **完全不掉血**；上游对 core 调 `hurt` 没有此限制。 |
| 建议改法 | 在 `entity/` 或 `util/EntityUtil` 增一个 1.7.10 版 `MaybeMultipartEntity`（`hitPart` + `core`；`EntityDragonPart.entityDragonObj instanceof Entity` 时取 parent，其它 `IEntityMultiPart` 退化为自身，复用现有 `EntityUtil.getPartParent:199`），`onHitEntity` 与 `tacAttackEntity` 签名改吃 parts：伤害 `hitPart.hurt`、`core.invulnerableTime=0`、击退/事件/爆炸用 `core`；`createDamageSources` 用 `hitPart` 的实体 id（末影人硬编码可改为按 core 判定保持现状）。类 javadoc 补一段「1.7.10 无 `PartEntity`，只识别 `EntityDragonPart`」差异记录（与 `EntityUtil` 现有 javadoc 口径一致）。 |
| 置信度 | **高**（机制缺失、未记录均可直接 grep 验证：`MaybeMultipart`/`多部件`/`DragonPart` 在 `EntityKineticBullet.java` 0 命中）；对具体伤害数值影响程度为**中**（取决于是否玩家射击、命中部位）。 |
| 是否阻塞 | 不阻塞编译/主线；**复审时已被 gun-logic 修复（见下）**。末影龙是原版 boss，玩家可感知。 |

#### D-1 处置（复审闭环，2026-11-04 19:28 快照）

| 项 | 内容 |
|---|---|
| 修复 | gun-logic（`entity/**` owner，`r2-03 F8` + 偏离 `D11`）。修复后我**独立复核**（未采信对方自述）：① `MaybeMultipartEntity` 私有类已加在 `entity/EntityKineticBullet.java:752-777`，`of()` 用 `EntityDragonPart.entityDragonObj instanceof Entity` 取本体、否则 `core==hitPart`；② `onHitEntity` 在 PreEvent 之后 `:491` 取 `parts`（与上游 `:427` 时机一致），击退分支 `:509-516`、爆炸前清无敌帧 `:517-528`、Post/Kill 事件 `:529-587` **全部改用 `getCore()`**；③ `tacAttackEntity(parts,...)` `:789-799`：伤害仍施加 `hitPart.attackEntityFrom`，两次伤害前都清 `core.hurtResistantTime = 0`（= 上游 `:599/:603` 的 `core().invulnerableTime = 0`）；④ 非多段实体 `core==hitPart` → 行为与修复前逐字等价（无回归面）。|
| 记录 | 类 javadoc `:108-110` 新增「多段实体：上游 `MaybeMultipartEntity`（`PartEntity#getParent`）→ 1.7.10 只识别 `EntityDragonPart`」偏离登记；方法 javadoc `:743-751`/`:779-788` 引用上游 `:384-394,411,427,592-606` 行号。`r2-03` §一 F8 / §四 D11 同步登记。 |
| 保留的平台行为（正确做法） | 「非玩家来源子弹命中龙部件不扣血」**未绕过**：1.7.10 原版 `EntityDragon#attackEntityFromPart` 只在 `source.getEntity() instanceof EntityPlayer \|\| source.isExplosion()` 时真正扣血（`build/rfg/.../EntityDragon.java:557`），上游 1.20 无此限制。这属于平台语义差异，按 AGENTS「记录差异而不是悄悄改成第三种语义」处理（`r2-03 D11`）。 |
| 结论 | **D-1 闭合**。初版证据（上表）保留作审查记录；本节行号基于 19:26-19:28 修复后快照。 |

### 观察项（不是乱搓，但登记以免日后误判）

- **O-1【低｜已核实无实际风险】fastutil 容器 → `java.util` 的类型替换改变了迭代序语义，但本仓调用点不依赖顺序。**
  例：上游 `client/resource/GunDisplayInstance.java:884` `Int2ObjectArrayMap`（插入序）→ 本移植 `:956` `HashMap`；
  唯一调用点 `client/renderer/other/HumanoidOffhandRender.java:97-105` 只用 `containsKey/get`，无遍历。
  `Double2ObjectRBTreeMap`（有序）→ `TreeMap<Double,V>`（有序保持）。该替换是**项目级已记录裁决**
  （`docs/PORT_PLAN.md:123`、`docs/PORT_GAME.md:27`、`docs/PORT_CLIENT_CONVENTIONS.md:65`「fastutil 一律不用」，
  理由：不为 22MB 依赖付出代价），不属于未记录漂移。
- **O-2【低｜文档卫生】少量 TACZ 自有方法签名收窄/加参，方法处有注释但未在类级汇总。**
  `item/AmmoBoxItem.java:92` `getStatue(ItemStack)` ← 上游 `AmmoBoxItem.java:63` `getStatue(ItemStack, ClientLevel, LivingEntity, int)`
  （方法 javadoc 已注明原因：1.7.10 `ItemColor` 无 level/entity/seed）；
  `block/GunPegboardSlotHelper.java:59` / `block/GunDisplayCaseSlotHelper.java:30` 的 `getSlot(BlockState,BlockPos,Vec3)`
  → `getSlot(int metadata,int x,int y,int z,Vec3)`，并拆出 `getSlotFromRelative(...)`（方法 javadoc 注明「上游 getSlot 的方块内相对坐标版本」）；
  `util/EntityUtil.java:147` `getHitResult` 增加 owner 形参（类 javadoc 已记录）。均不改变语义。
- **O-3【低｜文档陈旧，易误导后续对齐】`event/SyncedEntityDataEvent.java` 的 `TODO(port)` 已过期。**
  `:38-64` 仍写「1.7.10 无 capability：DataHolder 的创建与生命周期**需要** entity/sync 层提供（**建议**在
  `SyncedEntityData#getDataHolder` 内按需建立，用 `WeakHashMap<Entity, DataHolder>` 保存）」——
  该实现**已经存在**（`entity/sync/core/SyncedEntityData.java:29-49` 类 javadoc + `:200-214` `getDataHolder` 实现，
  且 `SyncedEntityDataEvent.java:80-90` 已在 `EntityJoinWorldEvent` 里调用）。`:138-140` 的 capability TODO 同样过期。
  建议把 TODO 改成「已完成」指针，避免后续读者以为机制缺失。
- **O-4【低｜仓库卫生】`block/PortWriteProbe.java` 是残留的临时探针文件，应删除。**
  `src/main/java/com/tacz/guns/block/PortWriteProbe.java:3` 原文：「临时探针：验证本会话的写入是否真正落盘（写完立即删除）」；
  空类、全仓 0 引用、无上游对应物。属移植特有新增里的「用途不明」类（92 个新增类中唯一一个）。
- **O-5【信息｜非 YSM 符号，已记录】api/** 里有 3 处**已记录**的签名适配**，不影响 YSM（YSM 不用这些符号）：
  ① `api/event/server/AmmoHitBlockEvent.getHitResult(): BlockHitResult` → `getHitX/Y/Z()/getSide()/getState()/getMetadata()`
  （类 javadoc `AmmoHitBlockEvent.java:11-21` 记录；`docs/review/05-api-shim.md §4` 亦记录）；
  ② `api/entity/ITargetEntity.onProjectileHit(Entity, EntityHitResult, DamageSource, float)` →
  `(EntityKineticBullet, Entity, DamageSource, float)`（`docs/PORT_STATUS.md:212` 记录）；
  ③ `api/item/gun/AbstractGunItem` 的 `getName/initializeClient/getCustomRenderer/getTooltipImage` 删除，
  改由 1.7.10 `getItemStackDisplayName` + `client/**` 承接（类 javadoc / 前轮报告 §4 记录）。
  另 `api/item/gun/GunItemManager#getGunItemRegistryObject` → `getGunItem`，类 javadoc `GunItemManager.java:6-17` 记录。

### 已确认「不是漂移」的机制替换（全部有上游对应物 + 理由 + 差异记录）

| 上游机制 | 本移植形态 | 依据（文件:行） |
|---|---|---|
| Forge capability 挂 `DataHolder`（`DataHolderCapabilityProvider` + `AttachCapabilitiesEvent`） | 静态 per-entity `WeakHashMap<Entity,DataHolder>` 管理器 | `entity/sync/core/SyncedEntityData.java:29-49,200-214`；`event/SyncedEntityDataEvent.java:16-50`；`init/CapabilityRegistry.java:1-40` |
| `ForgeConfigSpec`/nightconfig（`PreLoadModConfig`） | `config/ConfigBuilder` + `TaczConfigSpec`（1.7.10 Forge `Configuration`） | `config/PreLoadConfig.java:6-20`；`config/ConfigBuilder.java`、`config/TaczConfigSpec.java` |
| `SimpleChannel`/`IMessage`/`LoginIndexHolder` | `network/MessageBase`（read/write）+ `SimpleMessageDispatcher` + `NetworkTaskQueue` + `SyncConfigSyncManager` | `network/MessageBase.java:8-32`；`network/NetworkTaskQueue.java:10-26`；`network/SyncConfigSyncManager.java:10-45` |
| `PackResources`/`PackType`/`NamespaceFilteredPackResources` 把枪包注册进原版资源仓库 | `resource/pack/{IGunPackSource,GunPackResources,DirectoryGunPackSource,ZipGunPackSource}` + `client/sound/GunPackResourcePack`（1.7.10 `IResourcePack`） | `resource/GunPackLoader.java:37-66`；`resource/GunPackType.java:1-20`；`client/sound/GunPackResourcePack.java:1-90` |
| 1.20 核心 shader（`RegisterShadersEvent`/`ShaderInstance`） | `client/renderer/scope/ScopeLensProgram`（裸 GLSL program 封装） | `client/renderer/scope/ScopeLensProgram.java:3-40` |
| 1.20 datapack loot（`LootTable`/`LootModifier`） | 保留 json、由 `loot/**` 掉落事件降级实现 | `resource/pojo/data/loot/LootTableInjection.java:7-19` |
| Accelerated Rendering 的 VBO 网格组（`WorldMeshGroup`/`MeshCachePolicy`） | 立即绘制（每帧重算顶点），几何/外观/LOD/光照逻辑保留 | `client/renderer/block/DisplayGunMeshGroup.java:27-71`（含上游行号与 `TODO(L2)`） |
| 1.20 `SoundInstance#resolve/getSound` 动态音效 | `client/sound/GunPackResourcePack` + `SoundPlayManager` 自建动态音效链 | `client/sound/GunPackResourcePack.java:1-90`；`client/sound/SoundPlayManager.java` |

---

## 二、移植特有新增清单（摘要；全量 92 类见数据文件 §A2）

**分类结果**：`platform-shim` 41（几乎全在 `mcshim/**`：1.20 API 的语义 shim）+ `platform-forced` 51
（1.7.10 注册/网络/资源/配置/渲染架构适配类）。**92/92 有类级 javadoc，89/92 明示「上游对应物 + 为什么必须新增」**。
抽查 15 个机制类（`SyncedEntityData`、`MessageBase`、`NetworkTaskQueue`、`ClientSideHooks`、`SyncConfigSyncManager`、
`ConfigBuilder`、`TaczConfigSpec`、`GunPackResources`、`GunPackLoader`、`ClientGunPackReloader`、`GunPackResourcePack`、
`ScopeLensProgram`、`BlockMetadata`、`CommandTargets`、`RenderHelperCompat`、`DisplayGunMeshGroup`），
14 个把「上游 file:line + 1.7.10 缺什么 + 本实现语义 + 丢掉的语义」写全（质量最高：`DisplayGunMeshGroup` 甚至给了
上游 `DisplayGunMeshGroup.java:40-43,50-115,148-165` 的逐段对照 + `TODO(L2)`）；唯一「未写明上游对应物」的是
`block/PortWriteProbe.java`（临时探针，见 O-4）。

**同名文件内新增方法 709 条**（数据文件 §B3 全量）——按性质分：

| 性质 | 典型 | 是否需要「上游对应物」注释 |
|---|---|---|
| 1.7.10 平台覆写（引擎派发） | `Item#appendHoverText→addInformation`、`Item#inventoryTick→onUpdate`、`TileEntity#load/saveAdditional→readFromNBT/writeToNBT`、`Block#use→onBlockActivated`、`GuiScreen#init→initGui`、`Container#quickMoveStack→transferStackInSlot`、`Recipe#getResultItem→getCraftingResult`、`ICommand` 的 `getCommandName/getCommandUsage/processCommand` | 不需要（覆写即证据；`@Override` 已核对 5 组抽样：`block/*:onBlockActivated` 5 处、`block/entity/*:readFromNBT` 6 处、`getDescriptionPacket` 5 处、`onBlockPlacedBy` 9 处、`item/*:onUpdate` 2 处均带 `@Override`） |
| 平台缺口辅助 | `api/item/nbt/ItemStackNbtHelper`（`ItemStack.EMPTY` 语义替代）、`api/util/ResourceLocationHelper`（`tryParse` 替代）、`EntityKineticBullet#getOwner/setOwner`、`AmmoBoxItem#fillItemCategory()` | 已在类/方法 javadoc 说明（2 个 helper 类 javadoc 完整） |
| 数据 getter | `network/message/**` 的 `containerId()/entries()/getHitX()` 等 record→class 访问器、`TargetBlockEntity#getOutputPower` | 自解释，无需 |
| 适配辅助 | `AbstractGunItem#giveItemToPlayer/getEntityInventory`（`ItemHandlerHelper`/capability 替代）、`resource/manager/*#reload/apply`（1.7.10 重载监听钩子） | 已写方法 javadoc（`getEntityInventory` 明确写「其它实体返回 null（记录为移植差异）」） |

**未写明依据的新增方法核查**：对 195 条业务包新增方法做「声明前 14 行内是否有 javadoc/注释 + 是否 `@Override`」机检，
80 条既无注释又无 `@Override` —— 逐条看过，全部是 getter/平台覆写/自解释辅助（清单见数据文件 §B3），
**没有「上游机制被自创方法替代且无说明」的情形**。

---

## 三、合法平台改名登记表（摘要；全量 264 对映射见数据文件 §B5）

> 「调用点是否接上」的判据：① 平台钩子 → 验证本移植方法带 `@Override` 且签名与 1.7.10 父类一致（引擎派发，
> 已抽样 5 组共 27 处）；② 上游 TACZ 内部方法改名 → 全仓 grep 新名的调用点；③ 无调用点的 = 机制被降级，
> 必须在类 javadoc 记录（已逐条核对，见 §一「已确认不是漂移的机制替换」）。

| # | 上游方法（file:line） | 本移植方法（file:line） | 1.7.10 平台依据 | 登记结论 |
|---|---|---|---|---|
| 1 | `Block#use(BlockState,Level,BlockPos,Player,InteractionHand,BlockHitResult)` ×5（如 `block/GunDisplayCaseBlock.java:142`） | `onBlockActivated(World,int,int,int,EntityPlayer,int,float,float,float)`（如 `block/GunDisplayCaseBlock.java:389`） | `Block#onBlockActivated`（1.7.10 方块交互唯一入口） | 合法；`@Override` 已核 |
| 2 | `Item#appendHoverText(ItemStack,Level,List,TooltipFlag)` ×5 | `addInformation(ItemStack,EntityPlayer,List,boolean)`（如 `item/AmmoBoxItem.java:202`） | `Item#addInformation` | 合法 |
| 3 | `Item#inventoryTick(ItemStack,Level,Entity,int,boolean)` ×4 | `onUpdate(ItemStack,World,Entity,int,boolean)`（`item/ModernKineticGunItem.java:140`） | `Item#onUpdate` | 合法；`@Override` 已核 |
| 4 | `Item#getName(ItemStack):Component` ×7 | `getItemStackDisplayName(ItemStack):String` | `Item#getItemStackDisplayName` | 合法（`Component` 在 1.7.10 无，用 `IChatComponent` 仅在需要处） |
| 5 | `Item#getMaxStackSize` / `isBarVisible` / `getBarWidth` / `getBarColor` / `isFoil` / `fillItemCategory` | `getItemStackLimit` / `showDurabilityBar` / `getDurabilityForDisplay` / `hasEffect` / `fillItemCategory()` | 1.7.10 `Item` 对应钩子 | 合法（`fillItemCategory` 名字也保留了，只是 1.7.10 用 `getSubItems` 形态由 `ModItems` 调） |
| 6 | `BlockEntity#load(CompoundTag)` / `saveAdditional` ×6 | `readFromNBT(NBTTagCompound)` / `writeToNBT`（如 `block/entity/AmmoStorageBlockEntity.java:138`） | `TileEntity#readFromNBT/writeToNBT` | 合法；`@Override` 已核 |
| 7 | `BlockEntity#getUpdatePacket` / `getUpdateTag` ×5 | `getDescriptionPacket()` / `onDataPacket(...)`（如 `block/entity/GunPegboardBlockEntity.java:424`） | `TileEntity#getDescriptionPacket` + `S35PacketUpdateTileEntity` | 合法；`@Override` 已核 |
| 8 | `Block#newBlockEntity(BlockPos,BlockState)` ×6 | `createTileEntity(World,int)` + `hasTileEntity(int)` | 1.7.10 方块实体创建钩子 | 合法 |
| 9 | `Block#setPlacedBy` / `getStateForPlacement` / `updateShape` / `onPlace` / `onRemove` | `onBlockPlacedBy` / `onBlockPlaced` / `onNeighborBlockChange` / `onBlockAdded` / `breakBlock` | 1.7.10 `Block` 同名钩子族 | 合法（meta/BlockState 差异导致参数形态不同） |
| 10 | `Block#getShape/getCollisionShape:VoxelShape` | `getSelectedBoundingBoxFromPool` / `getCollisionBoundingBoxFromPool` / `addCollisionBoxesToList` / `setBlockBoundsBasedOnState` | 1.7.10 AABB 碰撞钩子族 | 合法 |
| 11 | `Block#getRenderShape` / `getPistonPushReaction` / `hasAnalogOutputSignal` / `getAnalogOutputSignal` / `getCloneItemStack` | `getRenderType` / `getMobilityFlag` / `hasComparatorInputOverride` / `getComparatorInputOverride` / `getPickBlock` | 1.7.10 `Block` 同名钩子 | 合法 |
| 12 | `AbstractContainerMenu#quickMoveStack/stillValid/broadcastChanges/createMenu` | `transferStackInSlot/canInteractWith/detectAndSendChanges` + `createContainer` | 1.7.10 `Container/GuiHandler` 机制 | 合法 |
| 13 | `Screen#init/isPauseScreen/onClose/renderBg/renderLabels/renderTooltip/mouseReleased` | `initGui/doesGuiPauseGame/onGuiClosed/drawGuiContainerBackgroundLayer/…/drawHoveringText/mouseMovedOrUp` | 1.7.10 `GuiScreen/GuiContainer` 钩子 | 合法 |
| 14 | `Entity#tick()` / `getName():Component` / `getAddEntityPacket` / `getRandom` | `onUpdate()` / `getCustomName():String` / `IEntityAdditionalSpawnData`+`ModEntities` 注册 / `worldObj.rand` | 1.7.10 `Entity` 钩子 | 合法 |
| 15 | `Recipe#matches/getResultItem/getSerializer/getType/fromJson/toNetwork` | `matches/getCraftingResult` + 无 serializer（1.7.10 `IRecipe` 注册制）/`GunSmithTableSerializer.deserialize` | 1.7.10 `IRecipe` 接口 | 合法 |
| 16 | 消息类 `static encode/decode` ×44+44 | `MessageBase#write/read`（如 `network/message/ServerMessageSound.java:49/57`），`handle` 保留原名（`:106`） | 1.7.10 `IMessage#fromBytes/toBytes`；`MessageBase` javadoc:8-32 记录 | 合法（机制改名，已记录） |
| 17 | `SimplePreparableReloadListener#prepare/apply`（`client/resource/manager/{DisplayManager,PackInfoManager}`） | `IGunPackReloadListener#reload` + `apply(Map)`（`resource/IGunPackReloadListener.java`；`PackInfoManager.java:30-60` 记录） | 1.7.10 `IResourceManagerReloadListener` | 合法 |
| 18 | `GunPackLoader#loadPacks/discoverExtensions/getResource/listResources/getRootResource/getModIcon`（上游 `resource/GunPackLoader.java:58-136`） | `resource/GunPackLoader#reloadPackList/readPackData/injectGunPackLanguage` + `resource/pack/GunPackResources#scanDataDirectory/readElement/openReader` | 1.7.10 无 `RepositorySource/PackRepository/PackResources/SecureJar/IoSupplier` | 合法（`GunPackLoader.java:37-66` 大段记录；`getModIcon` 用途是资源包选择界面图标，1.7.10 枪包不进原版资源包 UI → 功能 N/A） |
| 19 | `Entity#getTextureLocation → Render#getTextureLocation` | `getEntityTexture`（`client/renderer/entity/EntityBulletRenderer.java:264` 等） | 1.7.10 `Render#getEntityTexture` | 合法 |
| 20 | `RenderHelper#getBufferSource/endBatch/renderFirstPersonArm`（上游 `util/RenderHelper.java:86-128`） | `client/model/RenderHelperCompat#getBufferSource/endBatch/renderFirstPersonArm` | 1.7.10 `util/**` 编译边界不允许引用 mcshim 渲染类型（类 javadoc 记录） | 合法（同类内方法名保留，跨类迁移已记录） |
| 21 | `BeamRenderer#getLaserBeam()/getLaserBeamEntity():RenderType`（上游 `:212-218`） | `BeamRenderer.LaserBeamRenderState#applyGlState()/restoreGlState()` | 1.7.10 无可返回的 RenderType 类型 | 合法（方法 javadoc `:210-220` 明写「故不再提供」，并列出 6 条 GL 状态语义对应） |
| 22 | `DisplayGunMeshGroup` 的 `isValid/isVisible/origin/localTransform/packedLight/geometryKey/collectGeometry`（上游 `:49-107`） | `tryRender/snapshot/localTransform` + 立即绘制 | 1.7.10 无 VBO 网格组/MeshCachePolicy/世界绘制阶段批量提交 | 合法（类 javadoc `:27-71` 记录，并保留几何/外观/LOD/光照选择逻辑） |

**未发现的类别（这是本次审查的关键否定结论）**：不存在「上游 TACZ 自有方法名 → 本移植自创名」的条目。
`entity/shooter/**`、`resource/modifier/**`、`api/modifier/**`、`util/AllowAttachmentTagMatcher` 等业务核心
甚至连方法名都逐字保留（`AllowAttachmentTagMatcher.Cache` 由 record 改普通类，但 `allowAttachmentCache()/tagMatchCache()`
访问器同名保留，类 javadoc `:28-29` 记录）。

---

## 四、api/** 契约核对表

### 4.1 YSM 硬契约（`docs/YSM_COMPAT_HANDOFF.md` §2，独立逐符号复核，未采信前轮报告）

| # | 契约符号 | 上游 | 本移植 | 结论 |
|---|---|---|---|---|
| 1 | `TimelessAPI#getCommonGunIndex(ResourceLocation)` → `Optional<CommonGunIndex>` | `api/TimelessAPI.java:143` | `api/TimelessAPI.java:158` | ✅ 一致 |
| 2 | `IGun#getIGunOrNull(ItemStack)` static @Nullable | `api/item/IGun.java:28` | `api/item/IGun.java:31` | ✅ 一致 |
| 3 | `IGun#mainhandHoldGun(LivingEntity)` static | `api/item/IGun.java:42` | `api/item/IGun.java:45`（正式名 `mainHandHoldGun`:53 亦在） | ✅ 一致（小写 h 版本上游本就有，非移植杜撰） |
| 4 | `IGun#getMainHandFireMode(LivingEntity)` static | `api/item/IGun.java:68` | `api/item/IGun.java:76` | ✅ 一致 |
| 5 | `IGun#getGunId(ItemStack)` instance | `api/item/IGun.java:130` | `api/item/IGun.java:141` | ✅ 一致 |
| 6 | `GunTabType` 枚举（PISTOL/SNIPER/RIFLE/SHOTGUN/SMG/RPG/MG） | `api/item/GunTabType.java` | `api/item/GunTabType.java` | ✅ 忽略注释后逐行一致（含 `@SerializedName`） |
| 7 | `FireMode`（AUTO/SEMI/BURST/UNKNOWN）+ `fromName` | `api/item/gun/FireMode.java` | 同文件 | ✅ 忽略注释后逐行一致 |
| 8 | `IGunOperator#fromLivingEntity` + 6 个 `getSyn*` | `api/entity/IGunOperator.java` | 同文件 | ✅ 只差 `LivingEntity→EntityLivingBase` + import（平台不可避） |
| 9 | `IClientPlayerGunOperator#getClientShootCoolDown()` | `api/client/gameplay/IClientPlayerGunOperator.java` | 同文件 `:98` | ✅ 一致（`@OnlyIn→@SideOnly`） |
| 10 | `ReloadState#getCountDown()` | `api/entity/ReloadState.java:41` | `api/entity/ReloadState.java:42` | ✅ 一致（IGunOperator 契约链路完整） |
| 11 | 事件 `GunFireEvent#getShooter` / `GunMeleeEvent#getShooter` / `GunReloadEvent#getEntity` | `api/event/common/*` | `api/event/common/*:30` | ✅ getter 名一致；`LogicalSide→cpw Side`（交接文档 §4 已写明）；**双端 post 6 处对 6 处一一对应**（服务端 `entity/shooter/{Shooter/LivingEntity}` + 客户端 `client/gameplay/LocalPlayer{Melee,Reload,Shoot}`（`LocalPlayerMelee:97`/`LocalPlayerReload:108`/`LocalPlayerShoot:364`） 经 `ServerMessageGun{Fire,Melee,Reload}` 转发） |
| 12 | `MuzzleFlashRender.isSelf` / `ShellRender.isSelf` `public static boolean` 可直写 | 上游 `:38` / `:32` | 本移植 `:47` / `:39` | ✅ 字段名/类型/可写性一致，javadoc 标注 YSM 契约 |

> **复核结论：YSM 契约 11 组 12 行全部通过，未发现 `NoSuchMethodError` 风险。**
> 前轮 `docs/review/05-api-shim.md §2` 的「YSM 硬约束全部满足，无签名漂移」结论**独立复核成立**；
> 需要补正的只是其表述范围——api/** 整体并非「零签名变化」（见 O-5 的 3 处已记录适配），
> 但都不在 YSM 使用的符号集合内，因此不影响该结论。另前轮报告里 IGun 中部分行号（`:31/:45/:53/:76/:141`）
> 与本轮快照一致，可信。

### 4.2 api/** 全量机检（非 YSM 符号也在内）

- 文件：上游 115 → 本移植 117（新增 `api/item/nbt/ItemStackNbtHelper.java`、`api/util/ResourceLocationHelper.java`，均带类 javadoc）。
- 方法：上游 864（public+protected 497）→ 本移植 880（511）。严格签名（名字+元数+形参规范化+返回+static）
  比对差异 41 条 = `MISSING` 6 + `SIG_DIFF` 15 + `ADDED` 20（全量见数据文件 §B6）：
  - 6 MISSING 全部是 1.20 客户端扩展/`BlockHitResult`/`RegistryObject`（`AbstractGunItem#getName/initializeClient/getCustomRenderer/getTooltipImage`、
    `AmmoHitBlockEvent#getHitResult`、`GunItemManager#getGunItemRegistryObject`）→ 平台不可避，已记录（O-5）。
  - 15 SIG_DIFF：`ModelPart→ModelRenderer`、`EntityHitResult`、`IItemHandler→IInventory`、`NonNullList→List`、
    `RegistryObject` 去除、`onEntitySwing` 参数顺序（1.7.10 `Item#onEntitySwing(EntityLivingBase,ItemStack)` 的形参顺序本来如此，
    属平台覆写）→ 全部平台。
  - 20 ADDED：2 个 helper 类的 11 个方法 + 平台覆写 6 个（`getItemStackDisplayName`/`fillItemCategory`/
    `giveItemToPlayer`/`getEntityInventory` 等）+ `GunProperties.memoize`（`Suppliers.memoize` 的 1.7.10 Guava 17 兼容替代，
    源码注释记录）。
- **结论：api/** 类名、公开方法名、参数顺序对 YSM 符号零漂移；其余差异均为平台替换且 100% 有记录。**

---

## 五、缺失类 / 新增类清单一览（全量见数据文件 §A）

### 5.1 上游有、本移植无：69 个

| 分类 | 数量 | 明细与判定 |
|---|---|---|
| `1.20-only-compat`（按 AGENTS「已确认决策 2」只保留反射骨架，**范围裁剪非漂移**） | 61 = 57 个上游 compat 类 + 4 个 AR/Oculus mixin | Accelerated Rendering 3、Cloth Config 9、Controllable 1、JEI 11、KubeJS 19、Oculus 2(+2 mixin)、PlayerAnimator 8、ShoulderSurfing 2、AR mixin 2。本移植 `compat/` 仍保留 **13 个反射骨架文件/10 个模块**，与上游同名文件 13 个一对一存在（如 `compat/jei/GunModPlugin.java`、`compat/kubejs/TimelessKubeJSPlugin.java` 均在上游存在且保留） |
| `platform-forced`（1.7.10 缺平台能力，等价机制替换，全部 javadoc 记录） | 8 | `config/PreLoadModConfig`、`entity/sync/core/DataHolderCapabilityProvider`、`network/IMessage`、`network/LoginIndexHolder`、`resource/NamespaceFilteredPackResources`、`mixin/client/RenderSystemAccessor`、`mixin/client/StairBlockAccessor`、`mixin/common/ServerPlayNetHandlerMixin` |

### 5.2 本移植新增：92 个

| 分类 | 数量 | 说明 |
|---|---|---|
| `platform-shim` | 41 | `mcshim/**` 39 + `api/item/nbt/ItemStackNbtHelper` + `api/util/ResourceLocationHelper`（1.20 API → 1.7.10 语义 shim，含 `PoseStack`/`BufferBuilder`/`RenderType`/`PacketBuffer`/`GsonHelper`/`Mth` 等） |
| `platform-forced` | 51 | `CommonProxy`/`ClientProxy`（1.7.10 代理）、`config/**` 11（ConfigBuilder 体系）、`network/**` 7（MessageBase/NetworkTaskQueue/ClientSideHooks/SyncConfigSyncManager…）、`resource/pack/**` 7 + `GunPackType`/`IGunPackReloadListener`、`block/BlockMetadata`、`command/sub/CommandTargets`、`inventory/{GuiHandler,GuiId}`、`client/{ClientTickHelper,ClientProxy,model/RenderHelperCompat,particle/ParticleCompat,renderer/**}`、`item/ItemClientHooks`、`event/EventHandlers`、`init/Mod*` 平台注册类等 |
| `用途不明 / 应清理` | **1** | `block/PortWriteProbe.java`（临时探针，0 引用，见 O-4）→ 建议删除 |

> 另有嵌套类型级差异：上游 10 个嵌套类型在本移植缺失 —— 9 个为平台/已记录（`ImpactPredictionRenderState`、
  `Capture`、`BlockList`、`ItemGeometryKey`、`TaczSound`、2 个 advancement `TriggerInstance`、`GunPackList.Entry`、
  `ModParticleType`），**1 个是真漂移 `MaybeMultipartEntity`（D-1）**；本移植新增 22 个嵌套类型，全部是平台适配/
  缓存载体（`SlotAdapter`、`ParticleSpec/Kind`、`DepthAttachment`、`TeeVertexConsumer`、`BulletDamageSource` 等，均有注释）。

---

## 六、复查与未覆盖说明

### 6.1 本轮独立复核的对象

- `docs/review/05-api-shim.md`（第一轮结论）**不是采信而是重新验证**：其 YSM 11 组逐符号结论（§2）用
  「读上游/本移植双向 `DiffFiles` 忽略注释」+ 行号定位独立重做，结论一致；其「api 111 文件机检」本轮改为
  文件系统全量（上游 115 / 本移植 117）重机检，未发现该报告漏报的新漂移；唯一需要补正的是 §2 结论的
  **适用范围**（该结论只对 YSM 符号成立，api 内另有 3 处已记录适配 —— 原报告 §4 已列，只是速览易误读）。
- `docs/YSM_COMPAT_HANDOFF.md` 全部 12 行契约逐符号核对（§四），无缺口；交接文档 §4 已列平台适配点
  （`LivingEntity→EntityLivingBase`、`LogicalSide→Side`、`PoseStack`/`ItemDisplayContext` 不存在等）与实际代码一致。

### 6.2 未覆盖 / 置信度限制（必须知道）

1. **未编译、未运行**：遵守「禁止 gradle」纪律，全部结论基于静态符号/源码核对；`@Override` 是否真的能编译
   只有 Lead 的统一 `compileJava` 能最终确认（本轮抽样的 27 个 `@Override` 签名与 1.7.10 父类逐字比对过）。
2. **平台改名的语义等价性未逐行验证**：264 对改名中，平台钩子类（22 类）只验证了「名字映射合理 + 存在 `@Override` +
   上游同位置有对应调用点」；钩子内部实现是否逐行等价属于各包 owner 的审查范围（01/02/03/04 报告），本轮不重复。
3. **方法级机检的已知噪声**：`RET_DIFF=1009` 中大量是「同文件同名同元数重载（匿名内部类 × 类型）」配对产物
   （典型 `entity/sync/core/Serializers.java`），不代表 1009 处真实改动；本报告用「名字+元数集合差」+「方法体规模比」+
   「字段/JSON 契约」三条独立证据交叉验证，结论不依赖该噪声表。
4. **快照漂移**：审查期间其他 teammate 在改 `src/main/java/com/tacz/guns/client/**` 等（`git status` 57 条工作区状态），
   我观察到文件数 821→822、`client/particle/ParticleDefinitionReloader.java` 等新增；数据文件头部记录快照时刻，
   计数可重跑脚本复现。**D-1 涉及的文件在审查期间被 r2 task-3（gun-logic，写作用域含 `entity/**`）并发修改**：
   19:15/19:22 两次复查时机制缺失结论均成立（`MaybeMultipart`/`DragonPart`/`多部件` 0 命中）；
   gun-logic 于 19:26 提交修复后，我于 19:28 第三次复查并独立验证闭环（见 §一「D-1 处置」）。
   数据文件中的方法/类清单仍是**修复前快照**（新增的私有 `MaybeMultipartEntity` 不在其统计内），以 §一 处置节为准。
5. **未审 private 方法名漂移**：机检剔除了 private/package-private 方法集合差（private 内部方法改名不构成契约漂移，
   且方法体规模比对已覆盖这些方法的「是否被简化」）。
6. **兼容层运行时行为**（`compat/**` 13 个反射骨架）未实机验证；只核对了「上游对应类是否仍有同名骨架文件、
   类 javadoc 是否说明」。
7. **残留风险最高的 3 个区域**（供后续轮次参考）：① `mcshim/{RenderType,GuiGraphics}`（1.20 渲染语义 shim 的
   行为等价性，非本报告范围但体量大）；② `client/resource/**` 的模型/动画反序列化（字段契约已证一致，
   但 `BedrockModel` 烘焙语义属 01 轮）；③ 事件总线的 segment 语义（`api/event/**` 双端 post 已核对，
   但 `GunShootEvent`/`GunFirePostEvent` 等非 YSM 事件的取消语义未逐事件验证）。
