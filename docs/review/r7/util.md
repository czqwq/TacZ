# r7-util · util 工具层上游等价性审查

> 作用域（写）：`src/main/java/com/tacz/guns/util/**`、`docs/review/r7/util.md`。
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/util/**`（35 文件）+ 1.20.1 原版/Forge
> `tmp/minecraft-src-1.20.1/**`、`tmp/MinecraftForge-1.20.1/**`；1.7.10 准绳：
> `build/rfg/minecraft-src/java/**`。
> 方法：逐文件两侧全读；对「判定为逐行等价」的文件另跑 **token 多重集 diff**（去注释/import 后按
> 标识符+字符串字面量计数），确认差异只来自平台类型替换；解析器类与射线类额外对照 1.7.10 反编译实现。
> **未运行 gradle**（硬规则）；改动文件 `lint_files(min_severity="error")` = 0 error。
> 历史结论（r5-B `fidelity.md`）已登记的不重复报，本轮只做复核与新增。

---

## 1. 结论速览

| 维度 | 结论 |
|---|---|
| 覆盖 | 上游 `util/**` **35/35 文件全部逐行读完**；本仓另有 port-only `RefitDebug.java` 1 个（按 Lead 裁决删除）。`TrackedItemStack`/`GunTooltipPart`/`TimelessAPI`/`StackTrace`/`GsonSerializer` 等在 1.2.0 的 `util/` **不存在**，清单以实际 35 文件为准。 |
| 与上游等价 | 数学（`Easing/PerlinNoise/SecondOrderDynamics`）、NBT 工具（`VariantHelper/VirtualOemAttachment/SlotAdapterHelper/AttachmentDataUtils/AllowAttachmentTagMatcher/DefaultGunAttachments/datafixer`）、JSON/文件（`ResourceScanner/GetJarResources/PathHandler/TacPathVisitor/ContentPackOrder/Md5Utils`）、杂项（`ColorHex/DelayedTask/CycleTaskHelper/InputExtraCheck`）**逐行等价**，差异仅是平台类型替换（列于 §6）。 |
| 本轮修掉 | **6 条**：① 实体注册名 → 1.20 id 的解析错误（`modid.name`/驼峰名，影响头箱/忽略名单/热成像配置）；② `Math.fma` 在 Java 8 运行期不可用；③ 爆炸射线缺世界边界 break；④ 死代码 `RefitDebug` 删除；⑤ 死代码 `describeAttachmentPermission` 删除；⑥ 4 处事实错误的注释（`BlockRayTrace`/`GunTime`/`AmmoStorageInteractionHelper`/`TacHitResult`）。 |
| 有据降级/平台差异 | **15 项**（§6），均「1.7.10 无对应物」并逐条给出语义评估；其中 `Math.fma` 已在本轮修掉、`Mth.fastInvSqrt` 待 r7-platform 裁决，其余为记录项。 |
| 跨模块发现 | **8 条**（§5）：3 条已闭环（r7-core 修复 `interact_key`、r7-block 落地 `unloadAmmoBox` 委托、r7-api 关闭 `ITargetEntity`）、1 条前提被证伪（`rangefinder 0.0m` → 实为 `HeatBarOverlay` 的 `0.0%`）、3 条待对应 owner、1 条为 LOD 实机验证信息。 |
| 需实机 | §7（3 项）。 |

---

## 2. 逐文件对照表

| # | 文件（`util/`） | 上游行 | 本仓行 | 判定 | 关键说明（证据） |
|---|---|---|---|---|---|
| 1 | `AllowAttachmentTagMatcher` | 103 | 164 | ✅ 等价 | `record Cache`→普通嵌套类（方法名同）；`Set.copyOf`→`unmodifiableSet`；新增 `CommonAssetsManager.get()==null` 判空（1.7.10 未同步时为 null）。循环展开 BFS 逐行同构。`resetCache()` 两个调用点（`CommonAssetsManager:250`、`CommonNetworkCache:189`）与上游一致。 |
| 2 | `AmmoStorageHelper` | 49 | 84 | ✅ 等价（+判空） | 新增 1.7.10 空栈（null）判空，返回值与上游「不是弹药」一致；`getStackSize` 空栈回退 64。 |
| 3 | `AmmoStorageInteractionHelper` | 41 | 75 | ✅ 等价（有据降级） | 上游唯一实现；1.7.10 `InteractionResult`→boolean（所有分支都 CONSUME）、动作栏→聊天栏、`BUNDLE_REMOVE_ONE`→`"random.pop"`。**类注释标明「唯一实现、block/event 应委托」；r7-block 已落地 `AmmoStorageBlock` 委托（§5-6）**。 |
| 4 | `AttachmentDataUtils` | 293 | 358 | ✅ 等价 | `var`→显式 `JsonProperty<?>`；`instanceof` 模式匹配保留；modifier id 常量、`eval` 调用顺序逐行一致。 |
| 5 | `ColorHex` | 24 | 34 | ✅ 等价 | 纯 JDK，逐行一致。 |
| 6 | `ContentPackOrder` | 20 | 36 | ✅ 等价 | `getNamespace`→`getResourceDomain`；`Comparator` 链一致（tacz 优先→字典序）。 |
| 7 | `CycleTaskHelper` | 108 | 120 | ✅ 等价 | 逐行一致；依赖 `GunTime` 语义（见 §4.1）。 |
| 8 | `DefaultGunAttachments` | 28 | 43 | ✅ 等价 | `Optional.map/orElseGet` 结构一致。 |
| 9 | `DelayedTask` | 43 | 53 | ✅ 等价 | `@OnlyIn`→`@SideOnly`；逻辑逐行一致。 |
| 10 | `EntityUtil` | 127 | ~239 | ⚠️ **已修 id 解析** | 射线/队伍/载具/部件映射见 §4.2；实体 id 解析错误已修（§3-1）。 |
| 11 | `ExplodeUtil` | 46 | 110 | ✅ 等价（映射） | `Explosion` 两段式映射、`S27PacketExplosion`、`onExplosionStart`、KEEP 清空方块表逐条对应；可见距离平方化用 double 避免溢出。 |
| 12 | `GetJarResources` | 389 | 506 | ✅ 等价 | 纯 JDK 文件 API 逐行一致；`FMLPaths.GAMEDIR`→`Loader.getConfigDir().getParentFile()`（新增回退）；`ModList.versionString`→`Loader.getIndexedModList().get(modId).getVersion()`。 |
| 13 | `GunAmmoUnloadHelper` | 107 | 166 | ✅ 等价（有据降级） | `IItemHandler`能力→`InventoryPlayer.mainInventory`；`giveItemToPlayer`→`addItemStackToInventory`+溢出掉落；判弹/枪膛弹/虚拟弹/无限弹匣/燃料分支逐行一致。 |
| 14 | `HitboxHelper` | 112 | 177 | ✅ 等价（有据降级） | `latency`↔`ping`(ms)、`getVehicle`↔`ridingEntity`、`expandTowards`↔`addCoord`、`move`↔`offset`；`isSpectator`→false（1.7.10 无旁观者）。逐公式一致。 |
| 15 | `InputExtraCheck` | 26 | 53 | ✅ 等价（有据降级） | `Overlay`→`currentScreen`+`theWorld/thePlayer==null`；`MouseHandler.isMouseGrabbed`→LWJGL2 `Mouse.isGrabbed()`；`isWindowActive`→`Display.isActive()`。**只收紧不放宽**。 |
| 16 | `LaserColorUtil` | 66 | 170 | ✅ 等价（hook 已接线） | 上游默认色来源是 client 类；`getLaserColor(stack)` 走 `setClientDefaultColorResolver`（`ClientSetupEvent:135-137` 已注入）；`getLaserColor(stack, LaserConfig)` 形参降为 `Object` + 反射 `getDefaultColor()`（`LaserConfig.java:36` 公共方法，反射必中）。 |
| 17 | `Md5Utils` | 53 | 64 | ✅ 等价 | 静态共享 `MessageDigest` 非线程安全属**上游行为**，保留不修。 |
| 18 | `PathHandler` | 11 | 21 | ✅ 等价 | 纯 JDK。 |
| 19 | `RenderDistance` | 25 | 82 | ⚠️ 有据降级 | `poseStack.last().pose()`→读当前 `GL_MODELVIEW` 平移（1.7.10 无矩阵栈）；`ScreenItemVboContext.isActive()`→`setScreenDrawActive` 推送（`ScreenItemVboContext:80-82` 已接线）。调用点（`AttachmentRender:105`、`AttachmentItemRenderer:139`、`GunItemRendererWrapper:393`）在 GL 变换已建立后调用，数值语义≈到相机距离。 |
| 20 | `RenderHelper` | 141 | 96 | ✅ 等价（迁移已登记） | 只保留 stencil 开关 + OptiFine 探测；`blit/getBufferSource/renderFirstPersonArm/endBatch` 迁到 `client/model/RenderHelperCompat`（r2 报告 177 行已登记，调用点无缺失）。**stencil 缓冲由 r7-scope 落地的 `client/renderer/scope/MainFramebufferStencil` 运行期补挂**（等价 Forge `RenderTarget#enableStencil`，补挂失败才降级；见 §3-5 末条）。 |
| 21 | `ResourceScanner` | 92 | 152 | ✅ 等形（@Deprecated） | 1.7.10 无 datapack ResourceManager；已改用 `mcshim` 垫片，且**0 调用者**（r5 F-6 已登记，机制由 `GunPackResources` 承接）。 |
| 22 | `SlotAdapterHelper` | 183 | ~290 | ✅ 等价（+契约扩展） | `getAvailableSlotAdapters` 返回类型改 `Map<ResourceLocation,SlotAdapter>`（网络包 `ClientMessageSetSlotAdapter` 契约），另加 `getAvailableSlotAdapterIndices`（改装界面）；NBT key `"SlotAdapters"`、`"#"` 前缀、`"adapter/"` 路径前缀一字未改。**本轮删除 0 调用者的 `describeAttachmentPermission`**。 |
| 23 | `TacHitResult` | 17 | 45 | ✅ 等价 | `EntityHitResult`≡`MovingObjectPosition(Entity,Vec3)`（`build/rfg/.../MovingObjectPosition.java:52-57`：ENTITY + entityHit + hitVec）；`isHeadshot` 保留。**可安全暴露给 `api/entity/ITargetEntity`**（§5-4）。 |
| 24 | `TacPathVisitor` | 34 | 49 | ✅ 等价 | `ResourceLocation` 构造/后缀裁剪/路径分隔符逐行一致。 |
| 25 | `ThermalImagingHelper` | 26 | 78 | ⚠️ **已修 id 解析** | 上游 tag（数据包）→两张代码集合；默认 blacklist 只有 `minecraft:armor_stand`（**1.7.10 无盔甲架**）、whitelist 空 ⇒ 对现有实体 `instanceof EntityLivingBase` 即完整判定（有据降级，§6）。id 解析已改走 `EntityIdHelper`（§3-1）。 |
| 26 | `VariantHelper` | 47 | 69 | ✅ 等价 | NBT key `"Variant"`、类型常量 8/TAG_STRING 一致。 |
| 27 | `VirtualOemAttachment` | 50 | 80 | ✅ 等价 | NBT key `"VirtualOemAttachment"`、TAG_Byte 一致；`var`→显式 `BuiltInAttachment`。 |
| 28 | `block/BlockRayTrace` | 116 | 215 | ✅ 等价（重写接口） | 上游 DDA（`BlockGetter.traverseBlocks` 同构）逐行保留，含 `Mth.lerp(-1e-7,...)` 的端点内缩与「start/end 命名相反」的上游原样；`ClipContext(COLLIDER,Fluid.NONE)` 语义 ↔ `getCollisionBoundingBoxFromPool != null`+`calculateIntercept`；`tacz:bullet_ignore` 标签内容 4 项全部由 `isVanillaIgnoredBlock` 硬编码覆盖（iron_bars/fences/fence_gates/leaves，`data/tacz/tags/blocks/bullet_ignore.json` 逐项核对）。差异：1.20 `clipWithInteractionOverride` 的 interaction shape 无 1.7.10 对应（只影响 1.20 新方块，§6）。 |
| 29 | `block/ProjectileExplosion` | 203 | ~291 | ⚠️ **已修边界** | 方块阻力/实体伤害/15 条射线采样/击退/hitPlayers 逐段对应；补 1.20 `!isInWorldBounds → break`（§3-3）。映射差异：`ExplosionDamageCalculator`→`func_145772_a/func_145774_a`、`ProtectionEnchantment`→`EnchantmentProtection.func_92092_a`、`gameEvent` 略过（§6）。 |
| 30 | `datafixer/AttachmentIdFix` | 45 | 74 | ✅ 等价 | 6 条旧 id 映射表逐字一致；`putString`→`setString`。 |
| 31 | `math/Easing` | 17 | 18 | ✅ 等价 | 逐行一致。 |
| 32 | `math/MathUtil` | 493 | ~496 | ⚠️ **已修 fma** | 除 `Math.fma` 外逐行一致（token diff 仅 `x/y/z`↔`xCoord/yCoord/zCoord`）；四元数/矩阵/插值/高斯消元公式全对照。 |
| 33 | `math/PerlinNoise` | 69 | 70 | ✅ 等价 | 逐行一致（含 epoch 重随机、补偿逻辑）。 |
| 34 | `math/SecondOrderDynamics` | 100 | 112 | ✅ 等价（有据降级） | 去 `Minecraft.getInstance()==null` 判断（1.20 启动后恒非 null；1.7.10 该判断会引入 client 类导致专服 `NoClassDefFoundError`），等价于原条件后半段。15 线程池/sleep(6)/NaN 修正逐行一致。 |
| 35 | `time/GunTime` | 71 | 205 | ⚠️ 有据降级（等价） | 上游 `AnimationClocks.client()`（SBM）；本仓自实现「单调时钟+可注入暂停闸门+暂停时长补偿」，语义等价（`shouldTick`/`nowMillis` 单调/epoch/`isWithin`/`hasElapsed` 逐条对照）；客户端闸门由 `GunInputHandler:118-120` 接线。文档已纠正「该库不在本仓库」的错误说法（§3-6）。 |
| 36 | `RefitDebug`（port-only） | — | 181→**删除** | ✅ 死代码删除 | 0 调用者（Lead 裁决，§8）。 |

> 判定图例：✅=语义等价；⚠️=本轮修过或存在已记录降级。

---

## 3. 已修清单（改动 + 依据）

### 3-1 实体注册名 → 1.20 风格 id 解析错误（新建 `util/EntityIdHelper.java`，接入 `EntityUtil`/`ThermalImagingHelper`）
- **问题**：`EntityList.getEntityString` 对 FML 注册实体的格式是 `"<modid>.<entityName>"`（`cpw/mods/fml/common/registry/EntityRegistry.java:162-167`：
  `String.format("%s.%s", mc.getModId(), entityName)`），对原版返回驼峰名（`build/rfg/.../EntityList.java:279-338`，如 `"ItemFrame"`）。
  旧实现 `new ResourceLocation(name.toLowerCase())` 得到 `minecraft:tacz.bullet` / `minecraft:itemframe`，
  **永远匹配不到** 1.20 风格配置键（`minecraft:item_frame`、`tacz:bullet`），影响：
  `SyncConfig.HeadShotAABB`（`config/util/HeadShotAABBConfigRead`）、`AmmoConfig.BULLET_DAMAGE_IGNORE_LIST`
  （默认值就含 `minecraft:item_frame`）、热成像 whitelist/blacklist。
- **修法**：新增 `EntityIdHelper`：
  - 原版驼峰名 → 1.20 id 映射表（只收「小写注册名 ≠ 1.20 路径」项，共 30 项，来自 `EntityList.java:281-337` 与 1.20 `EntityType` 注册名逐项核对；`Skeleton` 按 `getSkeletonType()==1` 映射 `wither_skeleton`）；
  - `"modid.name"` → `"modid:name"`；已含 `:` 的自命名保持并小写；
  - 返回「1.20 风格 + 1.7.10 旧形态」候选列表（旧形态兜底已按旧写法配置的存档），按实体类缓存（`EntitySkeleton` 不缓存）。
  - `EntityUtil`（`getHitResult` 头箱查找、`isBulletDamageIgnored`）与 `ThermalImagingHelper#isHeatSource` 改为对候选逐个匹配。
- **落地位置**：`util/EntityIdHelper.java`（新）、`util/EntityUtil.java:163-170`、`util/EntityUtil.java:184-193`、`util/ThermalImagingHelper.java:65-82`。
  `EntityIdHelper` 的 `':'`/`'.'` 解析已**委托** r7-api 在 `api/util/ResourceLocationHelper#tryParseEntityName(String)`
  （就位后接入；语义：含 `:` 原样 tryParse、FML 的 `modid.name` 把最后一个 `.` 换 `:`、其他 `minecraft:`+小写；
  该函数不做小写化，故本类先整串小写再传入以得到 1.20 风格小写 id）。原版驼峰名→下划线 id 的映射仍留在本类。
  **跨 scope 契约**：r7-core 的 `config/util/InteractKeyConfigRead` 已编译期依赖本类公开的
  `getConfigCandidates(Entity)` / `getModernId(Entity)`，这两个公开名与签名**不得漂移**（后续若要改名需同步 r7-core + Lead）。
- **跨模块联动**：`event/LootInjectionEvent:349-369`、`client/event/RenderHeadShotAABB:98-110` 是同一 bug 的另外两份实现，见 §5-2 / §5-3；`config/util/InteractKeyConfigRead` 已由 r7-core 改用本类（§5-1）。

### 3-2 `MathUtil.mulQuaternion`：`Math.fma` 在 Java 8 运行期不可用
- **问题**：`Math.fma` 是 Java 9+ API。本工程 `gradle.properties:47/55` 是 `jvmDowngrader` + `downgradeTargetVersion=8`；
  实测 JvmDowngrader 2.0.1 的 `jvmDowngrader-java-api` 只有 `j8/j14/j15/j18` 的 `Math` 桩（`J_L_Math.java`），
  **没有 `fma` 桩**，无法降级 ⇒ 在 Java 8 运行时首次调用会 `NoSuchMethodError`（`mulQuaternion` 由
  `api/client/animation/Animations.java:103` 的动画混合调用）。
- **修法**：改为等价普通乘加（单次舍入→两次，差 ≤1ulp；四元数动画无可见影响），注释说明原因。
- **落地位置**：`util/math/MathUtil.java:178-188`、文件头注释。

### 3-3 `ProjectileExplosion` 补世界边界 break
- 上游 1.20 `Explosion#explode` 有 `if (!level.isInWorldBounds(pos)) break;`
  （`tmp/minecraft-src-1.20.1/.../Explosion.java:173`）；1.7.10 版缺该判断会把越界 `ChunkPosition`
  塞进 `affectedBlockPositions`（y<0 / y≥256，爆炸贴世界上下界时）。已补 `posY < 0 || posY >= 256 → break`。
- **落地位置**：`util/block/ProjectileExplosion.java:132-136`。

### 3-4 死代码清理（Lead 裁决，见 §8）
- 删除 `util/RefitDebug.java`（181 行）：`grep RefitDebug src/main/java` 现仅剩注释/自身（删除后 0 引用）。
- 删除 `util/SlotAdapterHelper#describeAttachmentPermission`（51 行）：`grep describeAttachmentPermission` 0 引用。

### 3-5 文档事实纠正（避免下轮误判）
- `block/BlockRayTrace`：`IGNORE_BLOCKS` 注释原写「由 init 层调用 `registerIgnoreBlock` 填充」，实际 **0 调用者**；
  改为「上游默认 tag 内容已由 `isVanillaIgnoredBlock` 覆盖，此 API 仅为对齐 tag 可扩展性保留」。
- `time/GunTime`：原 javadoc 称「1.7.10 侧该库不存在……也不在本仓库」**与事实不符**（本仓库已 vendor SBM，
  `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/common/time/AnimationClocks.java` 存在且 `WorldEmitterManager:55`/`FirstPersonRenderHandler:89` 在用）；
  改为如实说明「SBM 时钟由它的渲染处理器每帧 `update()` 推进，GunTime 用自带等价实现避免 util 反向依赖」。
- `AmmoStorageInteractionHelper`：类注释标注「上游唯一实现，block/event 应委托」；本轮 r7-block 已按裁决把 `AmmoStorageBlock` 改为委托（§5-6）。
- `TacHitResult`：类注释标注「可安全作为 `api/entity/ITargetEntity` 的 `EntityHitResult` 等价物」。
- `util/RenderHelper`（收尾补记，r7-scope 落地后纠错）：`enableItemEntityStencilTest` 旧 javadoc 写的
  「1.7.10 没有可补挂 stencil 的主 framebuffer API，只能降级」**已被推翻**。现改为：默认主 FBO 无 stencil 时，由
  `client/renderer/scope/MainFramebufferStencil` 在运行期给主 FBO 补挂 `GL_DEPTH24_STENCIL8`
  （等价 Forge 1.20.1 `RenderTarget#enableStencil`；失败才降级到「无 stencil」路径）。注释落在
  `RenderHelper.java:41-51,65-78`，`lint_files(error)=0`。

---

## 4. 重点语义核对（详细证据）

### 4.1 数学 / 时间
- `MathUtil`：`toQuaternion`（z→y→x 顺序）、`toEulerAngles`（x→y→z 提取）、`copySign`、`slerp`、`solveEquations`（高斯消元+回代）、`splineCurve`（Catmull-Rom）、`applyMatrixLerp`、`getAngleAndAxis` 全部公式逐项对照一致；`Vec3` 字段 `x/y/z`↔`xCoord/yCoord/zCoord`；`Mth.fastInvSqrt` 差异见 §5-5。
- `PerlinNoise`：`easeInterpolate`、epoch 重随机、`repeat==1/&gt;1` 补偿分支逐行一致。
- `SecondOrderDynamics`：k1/k2/k3、`pyd = pyd + t*(px + k3*xd - py - k1*pyd)/k2`、`get() = py+0.05*pyd` 一致。
- `GunTime`：`EPOCH_OFFSET_NANOS=1e14`ns（保证 legacy `-1` 落在窗口外）、`nowNanos` 的「raw<origin→重锚+epoch++」、`nowMillis=nowNanos/1e6`、`isWithin/hasElapsed` 边界（`timestamp<0` 视为未发生、`duration<=0` false、`elapsed>=0 && <`）逐条与上游一致；额外 API（`setTickGate/installClientTickGate/isClientTickGateInstalled`）为 1.7.10 移植新增，已接线（`GunInputHandler:113-120`，`!Minecraft.isGamePaused()`）。
- `CycleTaskHelper`：`timestamp=-1` 立刻执行、`duration>periodS` 追赶 while 循环、epoch 变化即废弃、cycles 计数位置逐行一致。

### 4.2 射线 / 命中（重点）
- **`BlockRayTrace` DDA**：`traverseBlocks` 变量名与几何方向「相反」是上游原样（`tmp/minecraft-src-1.20.1/.../BlockGetter.java:113-171`），本仓逐行照搬：
  `lerp(-1.0E-7, end, start)` 起点内缩、`floor`、`sign/frac` 步进、`d9/d10/d11` 与 `d12/d13/d14` 比较顺序、
  初始格先测一次。**未发现任何系数/符号/边界漂移**。
- **方块形状/流体**：上游 `ClipContext(Block.COLLIDER, Fluid.NONE, entity)`；1.7.10 无 `VoxelShape`/`ClipContext`，
  等价物 `getCollisionBoundingBoxFromPool`（null=无碰撞箱）+`calculateIntercept`。与 1.20 `AABB.clip` 逐点比对：
  两者都返回「射线与盒面最近交点」（`AABB.clip` 的 `clipPoint` 要求 `0<t<maxT`，起点在盒内时都返回出射面）；
  差异仅 1.20 对另两轴附加 `±1e-7` 容差（`AABB.java:363`）而 1.7.10 `isVecIn*` 用闭区间（`AxisAlignedBB.java:326-433`），
  对子弹命中无可见影响。流体：上游 `Fluid.NONE` 的 `getFluidShape` 返回空 shape ⇒ 恒 miss ⇒ 本仓「不单独处理流体」等价。
- **实体射线**：`EntityUtil.findEntityOnPath/findEntitiesOnPath` 的候选集 = `boundingBox.addCoord(运动).expand(1)`
  （上游 `getBoundingBox().expandTowards(deltaMovement).inflate(1.0)`，1.7.10 `addCoord` 与 `expandTowards` 同义），
  目标谓词 `isPickable() && !isSpectator()` ↔ `canBeCollidedWith()`：已逐类比对 1.20 `isPickable` 覆写表
  （17 处）与 1.7.10 `canBeCollidedWith` 覆写表（13 处），集合一一对应（LivingEntity/矿车/船/水晶/TNT/掉落方块/火球系/悬挂物/龙/龙部件…），
  1.7.10 多出的 `EntityDragon` 覆写对应 1.20 EnderDragon；**映射正确**。载具语义 `isPassengerOfSameVehicle`↔`rootVehicle` 同层近似（1.7.10 无多层载具）。
  头箱判定 `getFixedBoundingBox` + `AABB.clip` ↔ `calculateIntercept` + `AABB.contains`↔`isVecInside`：
  1.20 `contains` 左闭右开、1.7.10 `isVecInside` 双开（仅恰在边界时有差，配置头箱边界命中概率≈0）。
- **`HitboxHelper`**：`ServerPlayer.latency`↔`EntityPlayerMP.ping`(ms)、`(ping/1000.0)*20+0.5` 取整、历史箱/速度缓存
  （位置只留 2 帧、箱/速度留 `SAVE_TICK` 帧）、蹲伏 `+0.0625`、`playerHitboxOffset` ×2 段、`-2.5`/`-5` 定值偏移逐行一致；
  `AABB.move(Vec3)`↔`offset`、`Vec3.multiply(x,y,z)`↔私有 `scale`。`isSpectator` 恒 false（1.7.10 无旁观者）。
- **`TacHitResult`**：见 §2 #23；构造器 `super(entity, hitPos)` 直接落到
  `MovingObjectPosition(Entity, Vec3)`（ENTITY + `entityHit` + `hitVec`），与 1.20 `EntityHitResult(Entity, Vec3)`
  的「实体 + 位置」等价；不含任何 client 依赖，可被 `api/**` 引用。

### 4.3 ItemStack / NBT / 标签
- `VariantHelper`/`VirtualOemAttachment`/`SlotAdapterHelper` 的 NBT key（`"Variant"`/`"VirtualOemAttachment"`/`"SlotAdapters"`）、
  类型常量（TAG_STRING=8、TAG_BYTE=1、TAG_COMPOUND=10）与上游一致；`removeSlotAdapter` 空表删 key 的对称性保留。
- `AttachmentIdFix` 6 条映射字符串逐字一致（旧存档可修）。
- `AllowAttachmentTagMatcher` 的 `#` tag 前缀展开、环检测（visited）、「缓存完整 root 而不是子结果」注释语义保留；
  `DefaultGunAttachments`/`AttachmentDataUtils` 的 `built_in_attachments`/modifier 取值顺序一致。

### 4.4 序列化 / 文件
- `ResourceScanner`：上游 `FileToIdConverter.listMatchingResources`（键=文件路径→`fileToId`）在 1.7.10 垫片里直接返回 id，
  本仓相应少一次 `fileToId`（垫片语义，等价）；`scanDirectoryAll` 的 `listResourceStacks(prefix, null)` 等价。
- `GetJarResources`：指纹算法（`jar` 协议用 size/time/crc，目录用 size/lastModified，再拼 `mod-version`，排序后 MD5）
  逐行一致；备份 zip 固定时间 `BACKUP_TIME`、`.export-state.json` v1、上限 10、临时文件 deleteOnExit 全部保留。
- `TacPathVisitor`/`PathHandler`：`\\`→`/`、去掉后缀、`new ResourceLocation(namespace, path)` 一致。

### 4.5 爆炸 / 颜色 / 杂项
- `ExplodeUtil`/`ProjectileExplosion`：见 §2 #11/#29；`ExplodeUtil` 客户端不执行判断（`world.isRemote`）、
  Forge 事件拦截、KEEP 清方块表、S27 发包距离（平方化 double）、新增边界 break（§3-3）均对照完毕。
- `ColorHex`：正则 `^#([0-9A-Fa-f]{6})$`、失败回退 `0xFFFFFF`、RGB 取位一致。
- `InputExtraCheck`：三项判据均「只收紧不放宽」；被 `client/input/**` 静态导入，签名不变。

---

## 5. 跨模块发现（不在本 scope，已/待 msg Lead）

| # | 级别 | 位置（本仓） | 现象 / 证据 | 建议 | 归属 |
|---|---|---|---|---|---|
| **1** | **P1** | `config/util/InteractKeyConfigRead.java:20-28,44-76` | **`interact_key` 数据包 tag 在 1.7.10 完全失效 → 交互键功能整体不可用**。上游白名单来自数据包标签（`data/tacz/tags/blocks/interact_key/whitelist.json` **非空**：`#minecraft:buttons`/`beds`/`chest`/`furnace`/`tacz:gun_smith_table` 等 37 项；实体白名单也非空），本仓原来只读两条默认空配置（`SyncConfig.java:74-89` 默认 `newArrayList()`）且无 tag 判定 ⇒ `canInteractBlock/Entity` 对默认配置恒 false。影响：手持枪械时「交互键开箱/上矿车/交易」提示与按键动作全部失效（`client/input/InteractKey:104/119`、`InteractKeyTextOverlay:100/155`）。 | **✅ 已由 r7-core 本轮修复**（`InteractKeyConfigRead` 内置默认白名单：原版交互方块/实体按类型+注册名，TACZ 11 个方块按注册名；判定顺序保持上游「配置黑→默认黑→配置白→默认白」；实体 id 换算复用本报告 §3-1 的 `EntityIdHelper.getConfigCandidates`）。本报告作为独立核验，不再重复派工。 | r7-core（已修） |
| 2 | P2 | `event/LootInjectionEvent.java:349-369` | 用同一套旧解析推导「实体→1.20 战利品表 id」：`EntityList.getEntityString` 对模组实体返回 `modid.name`、原版多词驼峰，`new ResourceLocation(lower(name))` 后拼 `entities/<path>` ⇒ 模组实体/多词原版实体的战利品注入查不到表。 | 改用 `util/EntityIdHelper`（或 r7-api 的 `tryParseEntityName`+原版映射表），`modid:name`→`modid:entities/name`。 | r7-gun（`event/**`） |
| 3 | P3 | `client/event/RenderHeadShotAABB.java:98-110` | 同一旧解析（原版多词名/模组实体）⇒ 调试渲染头箱框对不齐配置。 | 改用 `util/EntityIdHelper#getConfigCandidates`。 | r7-input / r7-gun 按当前分工 |
| 4 | ✅ 已关闭 | `api/entity/ITargetEntity.java:24` ↔ `entity/EntityKineticBullet.java:465`、`entity/TargetMinecart.java:115` | r7-api 已把签名改为 `onProjectileHit(Entity, TacHitResult, DamageSource, float)`，并已代 r7-gun 修好两处派发（第二处删除）与 `TargetMinecart` 实现；当前工作区签名一致（lint error=0）。复核结论：`TacHitResult` 语义等价 `EntityHitResult`、可安全入 api（§4.2 末）。 | 无需再动。 | r7-api（已完成） |
| 5 | P3 | `mcshim/Mth.java:150-153` | `Mth.fastInvSqrt(float)` 实现为**精确** `1/sqrt`；上游 `MathUtil.normalizeQuaternion` 调 `Mth.fastInvSqrt(f)` 在 1.20 实际绑定的是 `Mth.fastInvSqrt(double)` = **Quake 魔数近似**（`tmp/minecraft-src-1.20.1/.../Mth.java:395-402`，`@Deprecated`）。差异 ~0.1%（归一化因子），动画归一化方向无可见差；本仓 `MathUtil` 唯一调用者（`:201`）。 | 二选一：a) 不改（现状=精确，优）；b) 若要逐位保真，在 shim 增加 `fastInvSqrt(double)` 魔数实现并让 `MathUtil` 走 double 重载。**建议 a + 注释**，避免把上游误用当契约。 | r7-platform（`mcshim/**`） |
| 6 | ✅ 已落地 | `block/AmmoStorageBlock.java:249-256`（改后）↔ `util/AmmoStorageInteractionHelper.java:41-71` | **`unloadAmmoBox` 重复实现已消除**（原 `AmmoStorageBlock` 私有副本逐行同逻辑，但有 3 处真实行为差异：① 音效用 `"random.bowhit"`（那是 `fillAmmoBox` 的 `ARMOR_EQUIP_IRON` 降级常量，上游 unload 是 `BUNDLE_REMOVE_ONE`）；② 缺 `ammoId == null` / `createAmmoStack(...) == null` 判空（1.7.10 空栈=null，极端数据下 NPE）；③ 双入口 `AmmoStorageInteractionEvent` vs `onBlockActivated` 音效/判空不一致）。**r7-block 本轮已按裁决改为 block→util 委托、删私有副本（净 −28 行）**；`fillAmmoBox` 与 `SOUND_ARMOR_EQUIP` 保留；`grep private.*unloadAmmoBox src/main/java` = 0。 | 已完成，无需再动；`docs/review/r7/block.md` §2.1/§4-3 已同步。 | r7-block（已落地） |
| 7 | ✅ 前提证伪 | `client/resource/pojo/display/gun/RangefinderManager.java:150-158`、`client/resource/pojo/display/gun/Rangefinder.java:11` | **用户所述「rangefinder 0.0m」前提不成立**（r7-crosshair 像素/字形级证伪，`docs/review/r7/crosshair.md §二`）：截图是 `HeatBarOverlay`，文本为 `DecimalFormat("0.0%")` 的 `0.0%`（`%` 字形逐像素与 `ascii.png` 一致，排除 `m`）；`heat_base.png` 刻度框可逐列对上；rangefinder 文本在第三人称/非开镜不出现且只输出补零整数（无 `m` 后缀），minigun 热条常显=上游同构行为。**util 射线层独立核验仍有效**：当前 `RangefinderManager`（r7-crosshair 已把占位降级对齐上游）用 `util/block/BlockRayTrace` + `HitboxHelper.getFixedBoundingBox(...).expand(...)`+`calculateIntercept`，与上游 `:140-145` 同构（`getPosition`≡`getEyePosition`、`getLook`≡`getViewVector`、`0.25/8.0` 同值、取整/补零同算法）；截图真正异常是「第一人称手模+本地身体同屏」，属 r7-fp/r7-tp。 | 无需在 util 侧再动；射线测距链路已确认等价。 | r7-crosshair（已定稿） |
| 8 | 信息 | `util/RenderDistance.java:55-81` + 调用点 `client/renderer/item/GunItemRendererWrapper:393`、`client/model/functional/AttachmentRender:105`、`client/renderer/item/AttachmentItemRenderer:139` | LOD 判定从 `PoseStack.m30/31/32` 改为读当前 `GL_MODELVIEW` 平移。1.7.10 的 TACZ `PoseStack` 是 CPU 侧 JOML 栈，**不保证**等于 GL 矩阵；调用点都在「实体/物品 GL 变换已建立后」，数值≈到相机距离（第三人物/展示柜路径成立，`StaticItemRenderHelper:81`/`ItemInHandRendererMixin:118`/`FirstPersonRenderHandler:707` 会把位姿刷进 GL）。GUI 路径由 `ScreenItemVboContext:80-82` 推送绕过。 | 属有据降级；**实机验证远距离第三人称/展示柜是否正确切低模**（§7）。如要更准，client 调用点可改传已刷 GL 的矩阵或直接在 client 侧算距离。 | r7-model / r7-block 复核调用点 |

> 另（非缺陷，已知）：`resource/**` 根 jar 的 `data/tacz/tags/**` 在 1.7.10 均无 datapack 加载器，逐个核对结论：
> `bullet_ignore` 已在 `BlockRayTrace` 硬编码覆盖全部 4 项 ✅；`thermal_imaging/*` 对 1.7.10 现有实体无影响（盔甲架不存在、whitelist 空）✅；
> `gun_pegboard/*` 两文件都是空数组 ✅；`damage_type/bullets` 4 项已硬编码（`init/ModDamageTypes`）✅；
> `use_magic_damage_on` 的 `enderman` 已硬编码（`EntityKineticBullet:769-778`）✅；`use_void_damage_on`/`pretend_melee_damage_on` 为空 ✅；
> **仅 `blocks|entity_types/interact_key/*` 有真实内容而失效 → 见 §5-1**。

---

## 6. 未修 / 有据降级清单

| 项 | 上游 | 1.7.10 落点 | 语义评估 |
|---|---|---|---|
| 数据包 tag 系统 | `TagKey`/`BlockTags` 等 | 代码硬编码/集合 | 1.13+ 才有；见 §5 逐项核对。 |
| `ClipContext`/`VoxelShape`/`BlockHitResult` | 1.20 方块形状系统 | `AABB`+`MovingObjectPosition`+`Block`+meta | DDA 算法保留；interaction shape（1.20 交互箱覆盖）无对应，只影响 1.20 新方块。 |
| `EntityHitResult` vs `BlockHitResult` 分型 | 两个类 | 统一 `MovingObjectPosition`（`typeOfHit`） | `TacHitResult` 专用于实体命中，语义等价（§4.2）。 |
| `isSpectator` | 1.20 旁观者 | 恒 false | 1.7.10 无旁观者（1.8+）。 |
| `PartEntity#getParent` | Forge 1.20 多段实体 | `EntityDragonPart.entityDragonObj` | 只覆盖原版多段实体；自定义多段退化为按部件自身 id。 |
| `IItemHandler`/`ItemHandlerHelper` | Forge 能力物品栏 | `InventoryPlayer.mainInventory`+`entityDropItem` | 非玩家生物无物品栏（1.7.10 本就没有），溢出掉落等价。 |
| `Component`/动作栏/`SoundEvent` | 1.20 文本与声音 | 聊天栏/字符串音效 | `AmmoStorageInteractionHelper`：`BUNDLE_REMOVE_ONE`→`"random.pop"`；`ExplodeUtil`/`GunAmmoUnloadHelper` 同类降级。 |
| `ExplosionDamageCalculator`/流体阻力 | 1.20 | `func_145772_a`/`func_145774_a`/方块阻力 | 1.7.10 无流体爆炸阻力（上游 `max(block,fluid)` 退化为 block-only），无流体阻力值可损失。 |
| `PoseStack` 渲染矩阵栈 | 1.20 | GL 固定管线 | `RenderDistance`/`RenderHelper` 见 §5-8/§2 #20。 |
| `ProtectionEnchantment` | 1.20 静态方法 | `EnchantmentProtection.func_92092_a` | 1.7.10 SRG 名，语义同「爆炸击退 dampener」。 |
| `GameEvent` 爆炸事件 | 1.20 | 略过 | 纯侦测/统计事件，无玩法结果。 |
| 实体 eyeHeight（蹲伏） | 1.20 `getEyeHeight()` 随 pose 变化 | 1.7.10 恒 1.62 | 只影响「无配置实体的默认爆头窗口」（上游 `eyeHeight±0.25`）；1.7.10 玩家蹲伏不改碰撞箱，判定按 1.62 反而贴近本地视觉；配置项 `HeadShotAABB` 可覆盖。 |
| `Math.fma` | Math.fma（Java 9+） | 普通乘加 | **已修**（§3-2）。 |
| `Mth.fastInvSqrt` | Quake 近似（double 重载） | 精确 `1/sqrt` | 差异 ≤0.1%，本仓仅四元数归一化使用；见 §5-5 裁决建议。 |
| `ResourceScanner` | datapack 扫描 | `@Deprecated` + 0 调用者 | 机制由 `GunPackResources` 承接（r5 F-6）。 |

---

## 7. 实机验证清单（静态无法定论）

1. **实体 id 配置匹配（§3-1）**：装一个 FML 注册实体 id 为 `modid:Name` 的模组（或任意模组生物）→
   `config/tacz-server.cfg` 的 `misc.HeadShotAABB` 写 `modid:name [-0.5, 1.0, -0.5, 0.5, 1.5, 0.5]` →
   子弹打头部应判定爆头（`ClientHitMark` 显示头箱/伤害倍率）；旧写法 `minecraft:modid.name` 也应生效（候选兼容）。
   同时验证 `EnableBulletDamageIgnoreList=true` + 默认值下**弹孔不再伤害物品展示框**（`minecraft:item_frame` 现在能匹配）。
2. **Java 8 运行时（§3-2）**：用 Java 8 启动客户端 → 第一人称持枪动画播放（`Animations` 四元数混合）无
   `NoSuchMethodError: java.lang.Math.fma`。（若 CI/运行环境固定 Java 21，可仅作冒烟。）
3. **爆炸边界（§3-3）**：在 y≈0（基岩层）与 y≈255 用爆炸弹（`ExplosiveAmmoDestroysBlock=true`）炸，
   无异常/无日志刷屏、无越界方块坐标（观察 `affectedBlockPositions` 相关网络包无异常）。
4. **LOD（§5-8）**：第三人称/展示柜（`gun_display_case`）在超过 `Gun_Lod_Render_Distance` 距离应切低模；
   近距离仍高模；改装界面（GUI）始终高模（`ScreenItemVboContext` 推送路径）。
5. **委托后的弹药仓储卸弹（§5-6，r7-block 落地后）**：潜行 + 手持弹匣盒右键弹药库 → 弹药进入仓储、
   聊天栏出现 `message.tacz.ammo_storage.unloaded`、音效为 `random.pop`（与 `AmmoStorageInteractionEvent` 路径一致）。

---

## 8. 死代码清理（Lead 裁决）

| 文件/成员 | 证据 | 处置 |
|---|---|---|
| `src/main/java/com/tacz/guns/util/RefitDebug.java`（181 行） | 原被 `network/message/**`（54 处）与 `client/gui/GunRefitScreen`（6 处）调用；r7-network / r7-gui 已删除全部调用点，`grep RefitDebug src/main/java` 删除后 0 引用（仅历史注释） | **已删除**（`-181 行`）。诊断日志 `[TACZ-DBG] refit` 随之消失。 |
| `util/SlotAdapterHelper#describeAttachmentPermission(ItemStack, AttachmentType, ResourceLocation)`（51 行） | 唯一调用者 `ClientMessageSetSlotAdapter`/`ClientMessageRefitGun`/`GunRefitScreen` 均已随 RefitDebug 删除；`grep describeAttachmentPermission` = 0 | **已删除**（`SlotAdapterHelper` 其余方法未动：NBT 读写/可用适配器/`allows*`/`getEffectiveSlotAdapter`）。 |

> 清理后 `util/**` 每帧 DBG：**0**（`TACZ-DBG` 在 util 内已无；原 `RefitDebug` 为事件触发日志，非每帧）。

---

## 9. 覆盖与验证方式（诚实）

- **逐文件全读**：`util/**` 35/35 上游文件 + port-only `RefitDebug`（删除前）。
- **机检**：对每个两侧都存在的文件跑 token 多重集 diff（去注释/import；标识符、数字、字符串字面量计数），
  确认「判定等价」文件的实际差异只有平台类型名（`ResourceLocation` 构造、`Vec3` 字段、`AABB` 方法、NBT 方法等），
  无静默丢行/改常量；`math/MathUtil` 的差异仅 `x/y/z`→`xCoord/yCoord/zCoord` 与已修的 `fma`。
- **上游/1.7.10 源码交叉引用**（均给出行号）：`EntityList/EntityRegistry/ResourceLocation/EntityPlayer/EntityLivingBase/MovingObjectPosition/AxisAlignedBB/BlockGetter/AABB/Explosion/ExplosionDamageCalculator/ProtectionEnchantment/Mth/AnimationClocks/PausedClientAnimationClock` 等。
- **本仓调用点扫描**：每个 util 类的消费方（`grep`）逐个看语义是否匹配上游调用方（`EntityUtil`/`HitboxHelper`/`BlockRayTrace`/`ExplodeUtil`/`GunAmmoUnloadHelper`/`SlotAdapterHelper`/`LaserColorUtil`/`RenderDistance` 等）。
- **未运行 gradle / runClient**；结论均为静态证据 + 1.7.10 反编译源码对照。改动文件 `lint_files(error)=0`。
