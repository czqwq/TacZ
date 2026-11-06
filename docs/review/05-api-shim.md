# 05 · api / mcshim / util / 入口 审查报告

作用域：`src/main/java/com/tacz/guns/api/**`、`mcshim/**`、`util/**`、`GunMod.java`、`CommonProxy.java`
（api 117 文件、mcshim 41、util 37、入口 2 —— 共 197 文件）
准绳：上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`；1.7.10 `build/rfg/minecraft-src/java/**`。
方法：`git diff --no-index` 逐文件比对 + 全包「方法名/元数」签名集合机检（脚本 `build/review-tmp/sigcmp.ps1`，已 gitignore）
+ IDEA `get_file_problems` / `lint_files`。

---

## 1. 已修复（本作用域内，均有依据）

| # | 文件 | 问题 | 依据 | 修复 |
|---|---|---|---|---|
| 1 | `mcshim/TessellatorVertexConsumer.java` | **顶点属性整体错位一个顶点（严重）**。原实现在 `vertex(x,y,z)` 里立刻 `Tessellator#addVertex`，而 1.20 的 `VertexConsumer` 语义是「属性写在 `vertex()` 之后、`endVertex()` 之前，`endVertex()` 才提交顶点」。本仓所有调用点都是 `vertex(...).color(...).uv(...).lightmap(...).normal(...).endVertex()`（`TreeGeometryWriter:340-350,373-383`、`BedrockCubeBox:155-161`、`ScopeThermalRenderer:889-892`），于是第 N 个顶点拿到的是第 N-1 个顶点的 uv/color/light/normal，第 1 个顶点拿到 (0,0)/白/无光照 → **所有 bedrock 枪械模型 UV 全部偏移一格** | 1.7.10 `Tessellator#addVertex` 在调用瞬间快照当前属性（`net/minecraft/client/renderer/Tessellator.java:390-433`），故属性必须先设后发；1.20 `BufferBuilder` 允许后设、`endVertex` 提交 | `vertex()` 只记录位置，`endVertex()` 才写属性 + `addVertex`。另：属性改为「当前值跨顶点保留」（1.20 同义，原注释的「每顶点清零」与 1.20 不符）；整缓冲属性集（hasTexture/hasColor/hasBrightness/hasNormals 是**整缓冲**标志，中途变化会让 stride 错位）在第一个顶点锁存 |
| 2 | `mcshim/BufferBuilder.java` | 同一语义错误的第二处实现（`MultiBufferSource.BufferSource` 走它）；且 `vertex()` 里把 hasColor/hasUv/hasNormal 清零，导致「只设一次颜色」的调用点后续顶点掉回白色 | 同上 | `vertex()` 记录位置、`endVertex()` 落数组；属性保留不清零；`begin()/discard()` 复位 pending 顶点 |
| 3 | `mcshim/RenderSystem.java` | `defaultBlendFunc()` 用了 `blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)`（RGB/Alpha 同因子）。1.20.1 实际是 `blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO)`，Alpha 通道行为不同（也与 1.7.10 原版 `OpenGlHelper.glBlendFunc(770,771,1,0)` 一致） | 1.20.1 `RenderSystem#defaultBlendFunc` | 改为 `blendFuncSeparate(..., GL_ONE, GL_ZERO)` |
| 4 | `util/block/BlockRayTrace.java` | **子弹会打到栅栏/铁栏杆/树叶上**。上游 `tacz:bullet_ignore` 标签（`data/tacz/tags/blocks/bullet_ignore.json` = iron_bars + #fences + #fence_gates + #leaves）在 1.7.10 无 tag 系统，原实现只留了个**空集合** + TODO，等于该标签完全失效 | `src/main/resources/data/tacz/tags/blocks/bullet_ignore.json:3-8` | 新增 `isVanillaIgnoredBlock`：`BlockFence`/`BlockFenceGate`/`BlockLeavesBase`/`BlockPane && material==Material.iron`（iron_bars 与 glass_pane 同为 BlockPane，按 Material 区分，同 `BlockPane#opacity` 的做法）；`registerIgnoreBlock` 保留给模组方块 |
| 5 | `CommonProxy.java` | 类注释称 preInit 里 10 个内容注册调用「已注释掉、仍是 DeferredRegister 形态」，但代码 92-101 行实际全部生效（10 个 `Mod*` 类都有 `register()`）——注释与实现矛盾 | 代码事实 + `init/Mod*` 的 `public static void register()` 全存在 | 注释改为描述实际行为 |

改后 `get_file_problems(errorsOnly=true)` + `lint_files(min_severity=error)` 均 0 error（5 个文件）。

---

## 2. YSM 契约核对（`docs/YSM_COMPAT_HANDOFF.md` 逐符号）

| 契约符号 | 结果 | 说明 |
|---|---|---|
| `TimelessAPI#getCommonGunIndex(ResourceLocation)` → `Optional<CommonGunIndex>` | ✅ 一致 | `api/TimelessAPI.java:158`，仅 `ResourceLocation` 为 1.7.10 同名类；`getClientGunIndex` 等全部保留 |
| `IGun#getIGunOrNull(ItemStack)` | ✅ | `api/item/IGun.java:31`，`@Nullable` 语义同上游（1.7.10 `stack==null` 即空） |
| `IGun#mainhandHoldGun(LivingEntity)` | ✅ | `IGun.java:45` 保留小写 h 的 `@Deprecated` 版本 **和** 上游正式名 `mainHandHoldGun`（`:53`）——YSM 两种写法都能编译 |
| `IGun#getMainHandFireMode(LivingEntity)` | ✅ | `:76`（另有 `@Deprecated getMainhandFireMode` `:62`） |
| `IGun#getGunId(ItemStack)` | ✅ | `:141`，`@NotNull ResourceLocation` |
| `GunTabType`（PISTOL/RPG/RIFLE…） | ✅ 逐字一致 | 8 个常量与 `@SerializedName` 与上游完全相同 |
| `FireMode`（UNKNOWN…）/ `fromName` | ✅ 逐字一致 | 含 `unknown` 反序列化 |
| `IGunOperator#fromLivingEntity` + 6 个 `getSyn*` | ✅ 一致 | `api/entity/IGunOperator.java` 与上游 diff **只剩** `LivingEntity→EntityLivingBase`、import 顺序、两处 javadoc 空白；`getSynAimingProgress/getSynShootCoolDown/getSynReloadState/getSynMeleeCoolDown/getSynDrawCoolDown/getSynIsAiming/getSynSprintTime` 全在 |
| `IClientPlayerGunOperator#getClientShootCoolDown()` | ✅ | `@SideOnly` 替换 `@OnlyIn`，其余声明不变 |
| `GunFireEvent#getShooter` / `GunMeleeEvent#getShooter` / `GunReloadEvent#getEntity` | ✅ 全部存在 | 构造参数 `(EntityLivingBase, ItemStack, Side)`，`isCancelable` 语义同上游；**注意** `LogicalSide→cpw Side`（YSM 侧需按 1.7.10 改判据，已在交接文档第 4 节写明） |
| `CommonGunIndex#getType()` 与 `GunTabType.name().toLowerCase` 可比 | ✅ | `resource/index/CommonGunIndex` 类型字符串来自 `GunTabType` 反序列化，取值 `pistol/rifle/rpg…` 一致 |
| `MuzzleFlashRender.isSelf` / `ShellRender.isSelf` 为 `public static boolean` | ⚠️ 非本作用域 | 属 `client/model/functional/**`，请看 client 组结论 |

**结论：YSM 硬约束全部满足，无签名漂移。** 唯一需要 YSM 侧适配的是 `LogicalSide→Side` 与 `LivingEntity→EntityLivingBase`（平台替换，不可避）。

---

## 3. 已核验无误的高风险 shim（逐行对照）

- `PoseStack`：构造器/`translate`/`scale`（法线矩阵 `1/x,1/y,1/z`）/`mulPose`/`rotateAround`/`mulPoseMatrix`（只乘 pose）/`pushPose`/`popPose`/`clear`/`last` 与 1.20.1 逐行一致；`stackTopAsFloatBuffer` 用 JOML `get(float[])` 的列主序，可直接 `glMultMatrixf`。
- `PacketBuffer`（线上格式）：VarInt/VarLong、`writeUtf/readUtf`（`maxLength*3` 双重校验与 1.20 逐行同）、`writeResourceLocation`（UTF `toString()`）、UUID（两个 long）、`writeNbt`（`-1` 表示 null）、`writeItemStack`（`itemId=-1`）、`writeByteArray`、`BlockPos`（3×int，与 1.20 STREAM_CODEC 同格式）。**NBT 走 gzip（`CompressedStreamTools`）而 1.20 是原始流，已记录降级，双端一致即可。**
- `LightTexture`：`pack = sky<<20 | block<<4` 与 1.7.10 `World#getLightBrightnessForSkyBlocks` 一致；`block/sky` 反解、`u/v`、`FULL_BRIGHT=0xF000F0` 正确；`VertexConsumer.lightmap()` 与 `uv2()` 的 16/16 位拆分可往返。
- `BufferUploader`：每顶点重灌 `Tessellator`，属性集由 `format()` 决定后逐顶点一致（修复 #1 后不再错位）。
- `mcshim/ResourceManager` / `FileToIdConverter` / `PreparableReloadListener`：id 推导（丢根段、段 1 = namespace）与上游 `FileToIdConverter` 一致；`DIRECT` 屏障 = 同步等价。
- `EntityUtil`：`findEntityOnPath/findEntitiesOnPath/getHitResult` 与上游逐行同构（爆头默认判定 `eyeHeight±0.25`、队伍友伤、载具过滤、`PartEntity→EntityDragonPart`），4 个降级点均已写在类注释。
- `Mth`：`clamp/lerp/lerp2/lerp3/frac/positiveModulo/wrapDegrees/rotLerp/hsvToRgb` 公式与 1.20 一致（`fastInvSqrt` 用 `1/sqrt` 代替位技巧，已注明）。
- `GunMod` / `CommonProxy` 生命周期：preInit（config → packType → blocks→tabs→items→entities→container→sounds→attributes→particles→lootModifiers→painting → attributes → 默认枪包导出 → 扫包 → 配件修改器 → 事件总线 → GUI handler）→ init（`commonSetup` → `ModRecipe.register`，配方必须晚于矿词注册）→ postInit（`markLoadComplete` → compat 探测）→ serverStarting（重载枪包 → 命令）；与上游 `GunMod()` 构造器顺序 / `onSetupEvent` / `FMLLoadCompleteEvent` / `RegisterCommandsEvent` 一一对应，无漏项。

---

## 4. 未修风险 / 需其它作用域跟进

| 级别 | 位置 | 内容 |
|---|---|---|
| 中 | `mcshim/TessellatorVertexConsumer`（已修但需实测） | 1.7.10 `Tessellator` 只有单一单例、`startDrawing/draw` 不能嵌套；`BufferSource.endBatch()` 逐 RenderType 绘制时若外层已有未 `draw()` 的 Tessellator（例如 `RenderType.setupRenderState()` 内部自己开了 buffered 绘制）会抛 `Already tesselating!`。**需要客户端实机跑一次枪械渲染确认**。 |
| 中 | `client/model/functional/MuzzleFlashRender.isSelf`、`ShellRender.isSelf` | YSM 契约要求 `public static boolean` 可直写；本作用域无法核对（client 组负责）。 |
| 中 | `mcshim/RenderSystem.getModelViewStack()` | 返回与 GL 脱钩的假 `PoseStack`（已注明降级）。任何「pushPose 后靠 GL 自动生效」的上游写法在这里必须改成 `pushToGL()/popFromGL()`；建议客户端组逐一确认调用点。 |
| 低 | `mcshim/ResourceManager` | `getResourceStack()` 顺序 = 注入顺序，**未做** 1.20 的资源包优先级/覆盖语义；`listResources` 不区分 `PackType`。枪包覆盖（同名 gun index 多包）依赖 `resource/**` 侧的注入顺序，需 Lead 侧确认。 |
| 低 | `api/event/server/AmmoHitBlockEvent` | `getHitResult()`（`BlockHitResult`）与 `BlockState` 无法保留，改为 `getHitX/Y/Z()+getSide()+getState():Block+getMetadata()`；`getState()` **同名但返回类型变了**（`BlockState→Block`），已在类注释记录。仅影响该事件的第三方消费者（YSM 不用）。 |
| 低 | `util/EntityUtil#getHitResult` | 由 `protected static (Projectile, Entity, Vec3, Vec3)` 变为 `(bullet, owner, entity, start, end)`（多传 owner，语义等价）；非公开 API。 |
| 低 | `util/SlotAdapterHelper#expandAdapterTag` | 私有方法多一个 `ICommonResourceProvider` 形参，语义等价。 |
| 信息 | `api/item/gun/AbstractGunItem` `getName/getCustomRenderer/initializeClient/getTooltipImage`、`GunItemManager#getGunItemRegistryObject` | 1.20 的 `IClientItemExtensions` / `RegistryObject` 无对应物，已按 1.7.10 形态替换（非 YSM 符号）。 |

---

## 5. 覆盖清单

- **逐符号/逐行核对**：`api/TimelessAPI`、`api/item/IGun`、`api/item/GunTabType`、`api/item/gun/FireMode`、`api/entity/IGunOperator`、`api/client/gameplay/IClientPlayerGunOperator`、`api/event/common/{GunFire,GunMelee,GunReload}Event`、`api/event/server/AmmoHitBlockEvent`、`GunMod`、`CommonProxy`、`mcshim/{PoseStack,RenderSystem,BufferUploader,BufferBuilder,BufferBuilderVertexConsumer,TessellatorVertexConsumer,VertexConsumer,Tesselator,MultiBufferSource,PacketBuffer,LightTexture,ResourceManager,PreparableReloadListener,Mth}`、`util/EntityUtil`、`util/block/BlockRayTrace`、`util/SlotAdapterHelper`。
- **机检覆盖**：api 111 个文件与上游的「方法名+元数」集合比对（`build/review-tmp/sigcmp.ps1`），差异已在上表逐条定性（平台替换 / 已记录降级 / 无问题）。
- **抽样（未逐行）**：`mcshim/{GlStateManager,RenderType,GuiGraphics,GsonHelper,VertexFormat,DefaultVertexFormat,FileToIdConverter,EntityStateHelper,ItemDisplayContext,Axis,AnimationPose,EntityPose,DummyPose,InputConstants,PackSource,Resource,ProfilerFiller,SoundEvent,OverlayTexture,Lighting,HumanoidArm,InteractionHand,BlockEntityRenderers,EntityRenderers,SimplePreparableReloadListener}`；`api/client/animation/**`（glTF 解析、状态机，纯算法移植）；`util/{HitboxHelper,ExplodeUtil,ProjectileExplosion,AttachmentDataUtils,AllowAttachmentTagMatcher,SlotAdapterHelper,math/**}`。**`api/client/animation/**` 与 `mcshim/{RenderType,GuiGraphics}` 是本作用域剩余最大风险面**（体量大、纯渲染/算法，未逐行比对）。
- `lint_files` / `get_file_problems`：改动 5 文件 0 error。

---

# 第二轮：同类崩溃专项（`ItemStack.EMPTY → null` 类）

> 触发：实机 `LivingEntityAim#tickAimingProgress` 处 `Supplier.get()` 返回 null → `ItemStack.getItem()` NPE
> （服务端每 tick）。根因是「上游 1.20 用非 null 哨兵 `ItemStack.EMPTY`，1.7.10 改成 `null`，读取点未判空」。
> 本节只列**会导致崩溃**的同类问题，不做泛化质量审查。
> 判据：上游同文件同方法（`tmp/TACZ-1.20.1/...`）在 1.20 语义下**不可能为 null**，而移植版可以拿到 null。

## A. 已修复（3 处 / 3 文件）

| # | 位置 | 崩溃条件 | 上游语义 | 修法 |
|---|---|---|---|---|
| 1 | `mcshim/GsonHelper.java:145 convertToJsonObject`<br>`:168 convertToJsonArray`<br>`:181 getArray(JsonObject,String)` | 枪包 JSON **缺一个必填字段**（`gun`/`ammo`/`attachment`/`display` 少某个 object/array 节点）→ `json.get(name)` 返回 `null` → `element.isJsonObject()` **NPE**。枪包加载只 `catch (JsonSyntaxException)`（本类 javadoc 第 35-36 行明确写了「上游大量 catch JsonSyntaxException 的写法可以直接搬」），NPE 会**逃出**加载器的 catch → 进服/重载枪包崩溃 | 1.20.1 `convertToJsonObject/convertToJsonArray` 形参标 `@Nullable`，`element == null \|\| element.isJsonNull()` 时**抛 JsonSyntaxException**（枪包被丢弃，不崩游戏）。本类 `getType(@Nullable)` 对 null 返回 `"null"`，说明作者本意是判空，只是漏写 | 三处补 `element == null \|\| element.isJsonNull() \|\|` 前置判断，仍抛 `JsonSyntaxException`（消息复用 `getType(element)`） |
| 2 | `api/item/nbt/AmmoItemDataAccessor.java:46 isAmmoOfGun` | `isAmmoOfGun(null, stack)` / `isAmmoOfGun(gun, null)` → `gun.getItem()` **NPE**。调用方遍布换弹/退弹/弹药匹配路径，1.7.10 空槽位就是 null | 1.20 `ItemStack.EMPTY.getItem()` 返回 `AIR`，`instanceof` 为 false → false | 补 `ItemStackNbtHelper.isEmpty(gun) \|\| ItemStackNbtHelper.isEmpty(ammo)` 早返回 false（同类 `AmmoBoxItemDataAccessor#isAmmoBoxOfGun:60` 本就有该 guard，属补齐一致性） |
| 3 | `api/item/gun/AbstractGunItem.java:520 useInventoryAmmo`<br>`:583 getRPM`<br>`:607 isCanCrawl` | 空手 / 空槽位 / 已丢弃的枪栈传入这三个 public API → `gun.getItem()` **NPE**。`hasInventoryAmmo`→`useInventoryAmmo`、`getRPM` 都在服务端 tick 与射击节奏路径上 | 同 #2（上游三方法同样直接 `gun.getItem()`，但 1.20 参数永不为 null） | 三处改为 `!ItemStackNbtHelper.isEmpty(gun) && gun.getItem() instanceof IGun ...`（仅判空，不改任何非空路径行为） |
| 4 | `mcshim/PacketBuffer.java:386 readEnum`（Lead 裁决后执行） | 移植版对越界 ordinal **返回 null**（1.20 无 null 分支），9 个网络消息把 null 存进枚举字段，随后在服务端 tick 的 `switch`/`EnumMap.put`/`ForgeDirection` 使用点才 NPE —— 失败点更晚、更难定位 | 1.20.1 `FriendlyByteBuf#readEnum` = `enumClass.getEnumConstants()[this.readVarInt()]`，非法 ordinal 由数组下标直接 AIOOBE | 改为越界抛 `IllegalArgumentException("Invalid enum ordinal N for ...")`（同为 RuntimeException，但消息带类名+合法区间，便于定位坏包）。同时删掉 `@Nullable`、改正类/方法 javadoc（原 javadoc 还误写成「1.20 抛 IllegalArgumentException」）。合法包（本端 `writeEnum` 写出的 ordinal）行为完全不变 |

改后 `lint_files(min_severity=error)`：4 文件 0 error；`readEnum` 的 9 个调用点文件 lint 亦 0 error。

### 4 的调用点复核结论（`search_text "readEnum("` 全量 9 处）

| 调用点 | 是否依赖 null | 结论 |
|---|---|---|
| `ClientMessageInstallVirtualAttachment:57`、`ClientMessageUnloadAttachment:49`、`ClientMessageSetSlotAdapter:66`、`ClientMessageRefitGun:62`、`ClientMessageSetAttachmentVariant:62` | 否 | 直接存 `AttachmentType` 字段，handle 里用 `EnumMap`/`equals`；null 只会在更晚处 NPE。改后 = 1.20 行为 |
| `ClientMessageAmmoStorageClick:69`（`AmmoStorageMenu.Action`）、`ServerMessageBulletHitBlock:98`（`ForgeDirection`） | 否 | 同上（switch / 方向查表） |
| `ServerMessageSyncGunPack:162`（`DataType`） | 否 | 紧邻已有 `types` 计数越界检查（`:157`），`readEnum` 结果直接进 `EnumMap.put`（null 会 NPE）。改后解码期即失败，且包内其余字段已有 `DecoderException` 风格检查，行为一致 |
| `ClientMessageLaserColor:77`（`readMap` 的 key reader） | **是（唯一一处）** | 该处显式写了「mcshim 的 readEnum 越界返回 null，坏包越界项直接丢弃」的 `entry.getKey() != null` 过滤（`:78-85`）。改后该过滤变成**死代码**，行为变为「畸形包在解码期失败」。按 Lead 裁决这与 1.20 一致，**但注释已过时、filter 已不可达**，属 `network/**` 作用域 → 已转 data-net-review 清理（只需删掉该 filter 与注释，不需加判空） |

> **解码异常的后果（1.7.10 侧）**：`readEnum` 抛出的 `RuntimeException` 会走 FML simpleimpl → `NetworkManager` 的
> 异常路径（`exceptionCaught` → 记录日志并断开该连接），**不会把服务端 tick 带崩**。1.20.1 在同样位置由
> `getEnumConstants()[ordinal]` 抛 AIOOBE，走的也是同一条「解码失败 → 断连接」路径。也就是说这次修改让
> 1.7.10 侧与 1.20 的失败语义**重新一致**，而不是新增风险。

## B. 已核验「安全 / 与上游一致，无需改」

- `api/item/nbt/{GunItemDataAccessor,AttachmentItemDataAccessor,AmmoBoxItemDataAccessor,BlockItemDataAccessor}`：**全部** NBT 访问经 `ItemStackNbtHelper.getOrCreateTag/getTag`（对 null 栈返回新建/空 tag），无裸 `stack.getTagCompound()`；`getAttachmentTag/getAttachment/getBuiltinAttachment/getBuiltInAttachmentId/getAttachmentId` 均 `@Nullable` 且调用点判空。
- `GunItemDataAccessor#getAimingZoom`（`:349-376`）的 `getZoomNumberFromTag(null)` 与 `zooms.length==0` 的 `/0`：**逐行与上游一致**（上游同样会 NPE/AE），属上游潜在问题 + 依赖 pojo 校验，**非移植引入**，未改。
- `AttachmentDataUtils#getAmmoCountWithAttachment:119` 的 `extendedMagAmmoAmount[level-1]`：与上游逐字相同（长度依赖 pojo 校验），非移植引入，未改。
- `api/item/{IAttachment,IAmmo}.getIAttachmentOrNull/getIAmmoOrNull`、`IGun.getIGunOrNull/mainhandHoldGun/getMainHandFireMode`、`IAnimationItem.matchesIgnoreCount`、`TimelessAPI` 全部 `getXxx`：先判 null / 用 `Optional.ofNullable`。
- `api/client/animation/ObjectAnimationRunner:134/136,192/194` 的 `Optional#get()`：都在 `isPresent()` 分支内。
- `util/{GunAmmoUnloadHelper:115/139,AmmoStorageHelper:36/50,LaserColorUtil:65/91,DefaultGunAttachments,AttachmentDataUtils}`：均有 `== null` / `getItem() == null` / `isEmpty` 前置判断。
- 入口 `GunMod`/`CommonProxy`：只调静态 `register()`；`GunMod.instance` 在 `@Mod` 构造器与 `preInit` 双写，`NetworkRegistry.registerGuiHandler(GunMod.instance, ...)` 不会拿到 null；`Mod*` 静态字段即使未注册也只产出 `new ItemStack(null,n)`（1.7.10 构造器不校验 item，`getItem()` 返回 null，下游 `isEmpty` 判真），**不 NPE**。

## C. 其它作用域的同类风险（只报告，未改）

| 位置 | 风险 | 建议 |
|---|---|---|
| `network/message/ClientMessageLaserColor:76-85` | 该处**显式依赖了**旧 `readEnum` 返回 null：`entry.getKey() != null` 过滤 + 注释「mcshim 的 readEnum 越界返回 null…坏包越界项直接丢弃」。`readEnum` 已改为越界抛异常（见 A.#4），该 filter 现在是**死代码**、注释**已过时** | data-net-review：删掉 `if (entry.getKey() != null)` 与那 3 行注释即可（不要改回判空式宽容；解码期失败 = 1.20 行为） |
| `network/message/ServerMessageAmmoStorageContents:100` `ItemStack sample = buf.readItem()` | `readItem()` 对空槽返回 null（1.20 返回 `ItemStack.EMPTY`），后续直接解引用即 NPE | network 组补 `ItemStackNbtHelper.isEmpty` |
| `network/message/event/ServerMessageGunDraw:45-46`、`entity/sync/core/Serializers:302`（`readItem`）、`:252`（`readNbt`） | 同上：上游 `readItem()` 保证非 null（`ItemStack.EMPTY`）；本端 null 被消费方解引用即 NPE | 各自作用域补判空 |
| `entity/shooter/LivingEntityAim:98`（本次实机崩溃） | 已由 gameplay-review 收口（现文件 `:100-105` 已改为 `data.getCurrentGunItem()` + 判空） | task-8 |
