# r7-api · API 签名与语义 上游等价性审查（YSM 兼容）

> **作用域（只读 + 写权）**：`src/main/java/com/tacz/guns/api/**`（117 文件）、`docs/review/r7/api.md`；
> 另含 Lead 授权的跨模块编辑点：`entity/TargetMinecart.java`、`entity/EntityKineticBullet.java`（r7-gun 区域之外）、
> `event/ammo/{DestroyGlassBlock,BellRing}.java` 消费者（见 §4-4/§4-10/§8.1）。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/api/**`（115 文件，逐行读）；
> 1.20.1 原版 `tmp/minecraft-src-1.20.1/**`；1.7.10 原版+Forge `build/rfg/minecraft-src/java/**`。
> **YSM 契约**：`docs/YSM_COMPAT_HANDOFF.md` §2–§3。
> **未运行 gradle**；结论均来自 IDEA 索引 + 源码逐行/机检对读 + 全仓调用点扫描。

---

## 1. 结论速览

| 维度 | 结论 |
|---|---|
| **文件集合** | 上游 115 个 `api/**` 文件**全部存在**；移植多 2 个移植辅助类（`item/nbt/ItemStackNbtHelper`、`util/ResourceLocationHelper`，均为新增、不删除/不改上游签名）。`item/nbt/ItemDataAccessor.java` 两侧都是**空文件**（上游即空），非缺失。 |
| **类型声明** | 机检（class/interface/enum/record/@interface 及其嵌套类型）：**115/115 文件类型声明集合与上游完全一致**（仅 2 个新增辅助类例外）。 |
| **成员签名** | 机检 public/protected 方法/字段/枚举常量的名称、参数个数、返回类型、`static/final/abstract`：**除 §5 列出的平台类型替换外，无缺失、无多余、无改序**。 |
| **注解** | 机检 `@OnlyIn→@SideOnly`（19 处→21 处：19 一一对应 + 2 处移植侧防御性 `@SideOnly`）、`@Deprecated` 11/11 同位置、`@Cancelable` 5/5、`@Nullable/@Nonnull/@NotNull` 只多不少（新增均为 1.7.10 空栈/空 tag 防御）。 |
| **YSM 编译期硬契约（12 行）** | **12/12 通过**；无 `NoSuchMethodError` / `NoClassDefFoundError` 风险（平台类型按交接文档 §4 预期替换）。§2 逐符号。 |
| **事件语义** | 20 个事件类的字段/getter/可取消性/构造参数顺序与上游一致；`GunFire/GunMelee/GunReload` 等 9 个 post 点**双端** post（与上游 9 个 post 点一一对应）。上游自身 bug（`EntityHurtByGunEvent.gunDisplayId` 未赋值）**按规则保持不修、与上游等价**。 |
| **本轮修复** | 10 项（§4）：子弹散布数学、`IAnimationItem` 比较语义、第三人称 minigun 骨骼字段映射、`ITargetEntity` 上游形状对齐（含 2 个跨模块编辑点）、`AttachmentIdFix` 接线、KubeJS 转发分支、2 处错误注释、实体名解析公共函数（lead 追加）、`ResourceManager` 内置资源契约 javadoc 纠正（§4-9）、**`AmmoHitBlockEvent` 恢复上游形状（Lead 追加授权，含跨 scope 改 post/消费者，§4-10/§8.1）**。 |
| **lint** | 本任务全部改动文件 `lint_files(min_severity="error")` = **0**；`api/**` 全包无 error。 |
| **未修/降级** | 全部登记于 §9（均为「1.7.10 平台无等价能力」或「上游自身 bug」或「依赖 r7-gun/r7-core 消费方切换」，无 YSM 契约项）。 |

---

## 2. YSM 编译期硬契约逐符号核对（独立复核，未采信前轮结论）

| # | 契约符号（交接文档 §2） | 移植侧位置（最终行号） | 结论 |
|---|---|---|---|
| 1 | `TimelessAPI#getCommonGunIndex(ResourceLocation): Optional<CommonGunIndex>` | `api/TimelessAPI.java:159` | ✅ 签名/语义一致（空索引→`Optional.empty()`） |
| 2 | `IGun#getIGunOrNull(ItemStack)` static `@Nullable` | `api/item/IGun.java:31` | ✅（1.7.10 空栈=`null`，已判空） |
| 3 | `IGun#mainhandHoldGun(LivingEntity)` static | `api/item/IGun.java:45`（`@Deprecated`，上游同名同注解） | ✅ 另有 `mainHandHoldGun:53`（上游同样两个名字） |
| 4 | `IGun#getMainHandFireMode(LivingEntity)` static | `api/item/IGun.java:76`（`@Deprecated` 的 `getMainhandFireMode:62` 也在） | ✅ 主手空栈返回 `FireMode.UNKNOWN`（上游对 AIR 亦返回 UNKNOWN） |
| 5 | `IGun#getGunId(ItemStack)` instance `@NotNull` | `api/item/IGun.java:141` | ✅ |
| 6 | `GunTabType` 枚举（`PISTOL/SNIPER/RIFLE/SHOTGUN/SMG/RPG/MG/DMR` + `@SerializedName`） | `api/item/GunTabType.java:5-29` | ✅ 常量名/顺序/序列化名与上游完全一致（文件 token 集 100%） |
| 7 | `FireMode`（`AUTO/SEMI/BURST/UNKNOWN` + `fromName`） | `api/item/gun/FireMode.java:9-43` | ✅ 常量顺序/`@SerializedName`/`fromName` 分支一致 |
| 8 | `IGunOperator#fromLivingEntity` + `getSynShootCoolDown/getSynMeleeCoolDown/getSynDrawCoolDown/getSynReloadState/getSynAimingProgress`（另 `getSynIsBolting/getSynIsAiming/getSynSprintTime`） | `api/entity/IGunOperator.java:18/25/30/35/45/50`（另 :40/:57/:63） | ✅ 仅 `LivingEntity→EntityLivingBase`（平台不可避） |
| 9 | `IClientPlayerGunOperator#getClientShootCoolDown()` | `api/client/gameplay/IClientPlayerGunOperator.java:98` | ✅（类级 `@SideOnly(CLIENT)` 与上游 `@OnlyIn(CLIENT)` 对应） |
| 10 | `ReloadState#getCountDown()`（`NOT_RELOADING=-1`） | `api/entity/ReloadState.java:42`（常量 :8） | ✅ 文件 token 集 100% 一致 |
| 11 | `GunFireEvent#getShooter` / `GunMeleeEvent#getShooter` / `GunReloadEvent#getEntity`，**双端 post** | 类在 `api/event/common/**`；9 个 post 点 = 上游 9 个（服务端 `ModernKineticGunScriptAPI:210`、`LivingEntityMelee:73`、`LivingEntityReload:89`；客户端 `LocalPlayerShoot:364`、`LocalPlayerMelee:97`、`LocalPlayerReload:108` + 3 个 `ServerMessageGun{Fire,Melee,Reload}:26`） | ✅ getter 名/参数顺序与上游一致；9 个 post 点全部存在且两侧都 post |
| 12 | `MuzzleFlashRender.isSelf` / `ShellRender.isSelf` = **`public static boolean`**（YSM 直接赋值） | `client/model/functional/MuzzleFlashRender.java:47`、`ShellRender.java:39`（只读复核，属他人 scope） | ✅ 可直接赋值 |
| — | `CommonGunIndex#getType()` 返回 `pistol/rifle/rpg…` 字符串 | `resource/index/CommonGunIndex.java:168`（只读复核） | ✅ 值来自枪包 `type` 字段，与 `GunTabType` 序列化名同域 |
| — | 能力接口挂载：`EntityLivingBase implements IGunOperator, KnockBackModifier`；`EntityPlayerSP implements IClientPlayerGunOperator` | `mixin/common/LivingEntityMixin.java:57`、`mixin/client/LocalPlayerMixin.java:104`（均在 `mixins.tacz.json` 生效列表） | ✅ `fromLivingEntity`/`fromLocalPlayer` 强转链完整 |
| — | 事件监听总线：`cpw.mods.fml.common.eventhandler.SubscribeEvent` + `MinecraftForge.EVENT_BUS` | 全部 `api/event/**` 为 FML `Event` 子类 | ✅（交接文档 §4 已写明） |

> **结论**：YSM 侧照交接文档改写 1.7.10 compat 不会遇到签名/类缺失；只需按 §4 把平台类型换成 `EntityLivingBase`/`EntityPlayerSP`/`Side`/`NBTTagCompound`。

---

## 3. 逐类对照表

> 机检口径：两侧提取「嵌套类型声明 + public/protected 成员声明 + 注解多重集 + token 多重集（平台类型归一后）」，逐文件 diff；
> 下表的「✅」= 0 处非格式差异，或差异全部落在 §5 平台类型替换/§4 已修/§9 已登记降级内。

### 3.1 根包 `api/`（6）

| 上游 API | 移植判定 |
|---|---|
| `TimelessAPI`（27 个方法） | ✅ 逐方法一致；客户端 14 个方法 `@OnlyIn→@SideOnly` 一一对应；`getRecipe/getAllRecipes` 已废弃返回空（与上游 `Optional.empty()/Map.of()` 同义）；移植对 `ItemStack` 增加 1.7.10 空栈判空（`getGunDisplay`/`getClientAttachmentVariantIndex`），语义不变 |
| `DefaultAssets`（9 常量 + `isEmptyAttachmentId`） | ✅ 值/命名一致 |
| `GunProperties`（6+19 常量 + `all()/allCacheModifiableByScript()`） | ✅ 键名/类型/注解/`RuntimeOnly` 嵌套类一致；仅 `Suppliers.memoize`（Guava 17 的 `com.google.common.base.Supplier` 与字段 `java.util.function.Supplier` 不兼容）→ 等价自实现惰性缓存（含 volatile 双检；Guava 同语义）。**注**：上游 `ALL_CACHE_MODIFIABLE_BY_SCRIPT` 生成顺序/内容一致 |
| `GunProperty`（record） | ✅ `of(String,Class)`/`of(String,TypeToken)` 语义一致；`Util.make(...)` 展开为显式 put+return（等价） |
| `CacheModifiableByScript` / `ValueModifiableAtRuntime`（含 `Holder`） | ✅ 注解元数据（`@Target(FIELD)`/`@Retention(SOURCE)`/`@Repeatable`）逐项一致 |
| *（新增）* `ItemStackNbtHelper` | 移植辅助类：1.20 `ItemStack#getOrCreateTag/getTag/hasTag/isEmpty/save/load` 的等价物（1.7.10 无这些 API）；null 栈语义已在类注释写明 |

### 3.2 `api/client/**`（51）

| 上游 API | 移植判定 |
|---|---|
| `client/gameplay/IClientPlayerGunOperator`（12 方法） | ✅ 仅 `LocalPlayer→EntityPlayerSP`；`fromLocalPlayer` 保留 |
| `client/event/{BeforeRenderHandEvent, RenderItemInHandBobEvent, RenderLevelBobEvent, SwapItemWithOffHand}` | ✅ 字段/getter/Cancelable 与上游一致；`PoseStack→mcshim.PoseStack`（形状保留）；均 `implements KubeJSGunEventPoster` 并调用 `postClientEventToKubeJS` |
| `client/other/{GunModelTypeManager, KeepingItemRenderer, IThirdPersonAnimation, ThirdPersonManager}` | ✅ 语义对齐；`ModelPart→ModelRenderer`（字段映射见 §4-3）；`KeepingItemRenderer#getRenderer` 改 `Minecraft.getMinecraft().entityRenderer.itemRenderer`（1.7.10 等价）；`IThirdPersonAnimation`/`ThirdPersonManager` 增加类级 `@SideOnly(CLIENT)`（防御性，见 §9-2） |
| `client/animation/**`（46 文件：Animations/AnimationController/AnimationPlan/监听器/ObjectAnimation\*/statemachine/gltf(\*)/interpolator(\*)） | ✅ 逐文件 token 集对比：**44 个 100% 等价**；2 个仅平台 API 替换：`Animations`（`Double2ObjectRBTreeMap`→`java.util.TreeMap`，均按 double 键排序、语义等价）、`ObjectAnimationSoundChannel/ObjectAnimationRunner`（`Minecraft.getInstance().player`→`getMinecraft().thePlayer`、`Entity#distanceToSqr(getPosition(0))`→`getDistanceSqToEntity`，后者差 1 tick 插值已在代码注释记录）。`particle_effects` 通道的 `ParticleEffectData`（vendored SBM 类型）与上游一致 |

### 3.3 `api/entity/**`（5）

| 上游 API | 移植判定 |
|---|---|
| `IGunOperator`（29 方法） | ✅ 仅 `LivingEntity→EntityLivingBase`；方法集合顺序一致 |
| `ITargetEntity` | ✅ **本轮改为上游形状**（§4-4）：`(Entity, TacHitResult, DamageSource, float)` |
| `KnockBackModifier` | ✅ 仅类型替换（文件 token 集差异仅 import/类型名） |
| `ReloadState` / `ShootResult` | ✅ 文件 token 集 100% 一致（常量、`StateType` 枚举顺序、`equals` 语义） |

### 3.4 `api/event/**`（14）

| 上游 API | 移植判定 |
|---|---|
| `common/{GunFireEvent, GunFirePostEvent, GunFireSelectEvent, GunMeleeEvent, GunReloadEvent, GunFinishReloadEvent, GunDrawEvent, GunShootEvent}` | ✅ 构造参数顺序/字段/getter/`getLogicalSide()` 一致；`LogicalSide→Side` 平台替换 |
| `common/EntityHurtByGunEvent`（+嵌套 `Pre/Post`） | ✅ 字段名/可见性（`protected` 可被外部子类读）/setter/`@Cancelable(Pre)`/`getAmount@Obsolete` 一致；**上游 `gunDisplayId` 构造器未赋值（1.20.1 自身 bug）移植侧同样未赋值 ⇒ 保持等价，按规则不修** |
| `common/EntityKillByGunEvent` | ✅ 一致（含 `gunDisplayId` 正常赋值） |
| `common/AttachmentPropertyEvent` / `GunDamageSourcePart` | ✅ 一致 |
| `common/KubeJSGunEventPoster` | ✅ 3 个 default 方法名/签名与上游一致；**本轮把条件转发分支接回 compat 门面**（§4-6） |
| `server/AmmoHitBlockEvent` | ✅ **形状已恢复上游**（§4-10/§8.1）：`(World, MovingObjectPosition, Block, EntityKineticBullet)` + `getLevel/getHitResult/getState/getAmmo`（`getMetadata()` 平台附加） |

### 3.5 `api/item/**`（20）

| 上游 API | 移植判定 |
|---|---|
| `IGun`（47 方法） | ✅ 方法集合/`@NotNull/@Nullable/@Deprecated` 一致；平台替换：`Player→EntityPlayer`、`LivingEntity→EntityLivingBase`、`CompoundTag→NBTTagCompound`；`getMainHandItem()`→`getHeldItem()`（含 1.7.10 空栈判空） |
| `IAttachment` / `IAmmo` / `IAmmoBox` / `IBlock` / `IAnimationItem` | ✅ 方法集合一致；`IAnimationItem.matchesIgnoreCount` 本轮修正（§4-2） |
| `GunTabType` / `gun/FireMode` / `attachment/AttachmentType` | ✅ token 集 100%，枚举常量顺序/@SerializedName 一致 |
| `gun/AbstractGunItem`（21 方法 + 静态 `fillItemCategory`） | ✅ 逐方法对读：能力接口（`ForgeCapabilities.ITEM_HANDLER`）→ 1.7.10 `IInventory`（仅玩家，已注释）；`initializeClient/getTooltipImage` 删除（1.7.10 无 `IClientItemExtensions`/`TooltipComponent`，渲染注册走 client 侧）；`getName→getItemStackDisplayName`；`NonNullList→List`；`doBulletSpread` 本轮修正（§4-1）；`isSame` 注释修正（§4-7） |
| `gun/GunItemManager` | ⚠ 平台降级（`RegistryObject→AbstractGunItem`，方法名 `getGunItemRegistryObject→getGunItem`；§9-3） |
| `builder/{GunItemBuilder, AmmoItemBuilder, AttachmentItemBuilder, BlockItemBuilder}` | ✅ 语义一致；`ItemLike→Item`（`BlockItemBuilder` 另加 `create(Block)` 便利重载，加法）；`build()` 无索引时返回 `null`（1.7.10 无 `ItemStack.EMPTY`，已 `@Nullable`+javadoc）；`RegistryObject.get()→实例` |
| `nbt/{ItemDataAccessor, AttachmentItemDataAccessor, AmmoItemDataAccessor, AmmoBoxItemDataAccessor, BlockItemDataAccessor, GunItemDataAccessor}` | ✅ NBT API 平移逐条等价（`getOrCreateTag/getTag/contains→hasKey/getX→getInteger/putX→setX/remove→removeTag`，r5-nbt-sync 已逐项覆盖 `GunId/AmmoId/AttachmentId` 等 20+ 键）；`getAttachmentTag` 本轮加旧 id 懒修复（§4-5） |
| *（新增）* `nbt/ItemStackNbtHelper` | 见 §3.1 |

### 3.6 `api/modifier/**`（5）

| 上游 API | 移植判定 |
|---|---|
| `CacheValue` / `ParameterizedCache` | ✅ token 集 100% |
| `IAttachmentModifier`（含 `DiagramsData` record） | ✅ 3 个客户端方法 `@SideOnly` 对应 `@OnlyIn`；record 字段一致 |
| `JsonProperty` | ✅ 仅 `Component→IChatComponent` |
| `ParameterizedCachePair` | ⚠ 平台降级（上游 `implements it.unimi.dsi.fastutil.Pair`；`left()/right()/of()` 保留，§9-4） |

### 3.7 `api/resource/**`、`api/util/**`、`api/vmlib/**`（8）

| 上游 API | 移植判定 |
|---|---|
| `resource/JsonResourceLoader` / `resource/ResourceManager` | ✅ 仅格式化/`ResourceLocation` import 差异；`ExtraEntry` record 保留 |
| `util/LuaEntityAccessor` | ⚠ 有据降级：`sendActionBar`→聊天消息（1.7.10 无 action bar，§9-6）；`Component→IChatComponent`、`DamageSource.generic` 等价 |
| `util/LuaNbtAccessor` | ✅ NBT API 平移（`getBoolean/getCompoundTag/getInteger/setBoolean/...`）逐条等价 |
| *（新增）* `util/ResourceLocationHelper` | 移植辅助 + **本轮新增实体名解析函数**（§7） |
| `vmlib/{LuaAnimationConstant, LuaGunAnimationConstant, LuaGunLogicConstant, LuaLibrary}` | ✅ token 集 100%（仅格式化）；常量大写表与上游一致 |

---

## 4. 本轮修复（10 项，全部 `lint error=0`）

| # | 文件:行 | 问题 | 修复 | 上游依据 |
|---|---|---|---|---|
| 1 | `api/item/gun/AbstractGunItem.java:156-199` | 默认 `doBulletSpread` 声称「与 1.20 Projectile 一致」，实际用 **1.7.10 `EntityThrowable` 的高斯噪声 `0.0075×inaccuracy`** 且**加噪声后又归一化**一次 ⇒ 与上游的分布/速度模长均不同 | 逐行复刻 1.20.1 `Projectile#shootFromRotation/#shoot`：`Vec3.normalize`（len<1e-4 归零）→ 每轴加 `RandomSource.triangle(0, 0.0172275×inaccuracy)` → `.scale(processedSpeed)` → 写 motion/rotation/prev → 叠加射击者速度。**注**：真实枪械路径 `EntityKineticBullet#shootFromRotation` 已由 r7-gun 并发改为同一公式/系数（`applyShoot`，见 §8-2），两条路径现已一致 | `tmp/minecraft-src-1.20.1/.../projectile/Projectile.java:120-140`；`RandomSource#triangle` 默认实现 `RandomSource.java:55-57` `mean + (nextDouble()-nextDouble())*maxDev` |
| 2 | `api/item/IAnimationItem.java:17-36` | `matchesIgnoreCount`（= 1.20 `ItemStack.isSameItemSameTags`）多比较了 `getItemDamage()` | 删除 damage 比较，改为 item + `areItemStackTagsEqual`；javadoc 写明依据 | 1.20.1 `ItemStack.isSameItemSameTags` 只比较 item + tag（`tmp/minecraft-src-1.20.1/.../ItemStack.java:429-435`） |
| 3 | `api/client/other/ThirdPersonManager.java:53-83` + `IThirdPersonAnimation.java:12-15` | MINI_GUN 动画把上游 `ModelPart.x/z`（= 骨骼 pivot/位置）写成 1.7.10 `offsetX/offsetZ`；`offset*` 不参与 `postRender` ⇒ 手持 minigun 第三人称姿态**不生效** | `x/z → rotationPointX/rotationPointZ`（左右臂都改）；类注释/接口注释同步；全 api grep 无其它同类映射错 | 1.20.1 `HumanoidModel#setupAttackAnimation:363-366`（`rightArm.x/z = ±cos/sin(body.yRot)*5`）↔ 1.7.10 `ModelBiped#setRotationAngles:160-163`（`rotationPointX/Z` 同公式）；`ModelRenderer#postRender` 用 `rotationPoint*` |
| 4 | `api/entity/ITargetEntity.java:1-29`（+ lead 授权代改 `entity/TargetMinecart.java:115-121`、`entity/EntityKineticBullet.java:465/494`） | 上游签名 `onProjectileHit(Entity, EntityHitResult, DamageSource, float)` 漂移成 `(EntityKineticBullet, Entity, ...)`；且 `EntityKineticBullet` 移植侧**多出**一次 Pre 事件后的二次派发 | api 改 `(Entity projectile, TacHitResult result, DamageSource, float)`（`TacHitResult extends MovingObjectPosition`，= EntityHitResult 等价物）；`TargetMinecart` 恢复上游实现形态（签名 + `instanceof EntityKineticBullet projectile`）；`EntityKineticBullet:465` 传 `result`；删除 :494-497 二次派发（上游只派发一次） | 上游 `api/entity/ITargetEntity.java:18`、`entity/EntityKineticBullet.java:400-405`、`entity/TargetMinecart.java:55-87`；`r7-util` 复核 `TacHitResult` 可安全暴露（common 包、仅子弹命中断构造） |
| 5 | `api/item/nbt/GunItemDataAccessor.java:244-245` | `util/datafixer/AttachmentIdFix` 全仓 0 调用者：旧配件 id（`tacz:muzzle_silence_*`）不会迁移（r4-C-4 / R7-I-4） | `getAttachmentTag` 取到 gun NBT 内子 tag 后就地 `AttachmentIdFix.updateAttachmentIdInTag(tag)`（`getCompoundTag` 返回引用、幂等；`getAttachmentTag` 是 `getAttachmentId` 唯一入口）。等价上游「物品 NBT 读入时修复」，1.7.10 无 `verifyTagAfterLoad` 故改懒修复 | 上游 `item/AttachmentItem.java:103-106`（`verifyTagAfterLoad`→`updateAttachmentIdInTag`） |
| 6 | `api/event/common/KubeJSGunEventPoster.java:1-35` | 3 个 default 方法为纯 no-op + `TODO(port,L2)`，与上游「KubeJS 存在才转发」的分支结构不等价 | 接回 `com.tacz.guns.compat.kubejs.TimelessKubeJSPlugin`：`if (LOADED) postKubeJSEvent(event)`（client/server 两方法委托 common）；`LOADED=Loader.isModLoaded("kubejs") && probe()` 在 1.7.10 恒 false ⇒ 行为 = 上游「未装 KubeJS」，无硬依赖 | 上游 `KubeJSGunEventPoster.java:9-29`（`ModList.get().isLoaded("kubejs")` 分支）；本工程 `TimelessKubeJSPlugin` 类注释已预留该接线点 |
| 7 | `api/item/gun/AbstractGunItem.java:632`、`api/util/LuaEntityAccessor.java:19/33` | 2 处事实错误/过时注释：`areItemStacksEqual`「忽略堆叠数量」实际**比较** stackSize（与上游 `ItemStack.matches` 等价）；action bar 的 `TODO(port)` 应写明平台无能力 | 注释改为事实陈述（`areItemStacksEqual` 同样比较 StackSize/item/damage/NBT）；`sendActionBar` 降级说明改为「1.7.10 网络包 `S02PacketChat` 无 1.8+ 的 position 字段」 | 1.7.10 `build/rfg/.../ItemStack.java:areItemStacksEqual/isItemStackEqual`；1.7.10 `S02PacketChat`（仅 `(IChatComponent, boolean)` 构造器，无 position） |
| 8 | `api/util/ResourceLocationHelper.java:44-91` | lead 追加：4 处消费者用 `tryParse(EntityList.getEntityString().toLowerCase())` 解析 FML 实体名会得到 `minecraft:tacz.bullet`（永久不匹配） | 新增公共函数 `tryParseEntityName`（+ 兼容别名 `tryParseNormalized`），语义见 §7 | FML `EntityRegistry.java:164` `String.format("%s.%s", modId, entityName)`；1.7.10 `EntityList.getEntityString:258-261` 返回原版驼峰类名 |
| 9 | `api/resource/ResourceManager.java:1-76` | r7-respack-client 跨模块发现：类/方法 javadoc 仍写「可以把 assets/data 直接内置在模组 jar 中」，但移植的枪包扫描器只扫 `<gamedir>/tacz/**`（`IGunPackSource`），jar 内直接内嵌的枪包/`assets/tacz/animations/*_default.animation.json` 不会被扫描 ⇒ 契约会误导附属模组作者 | **裁决 (a)**：javadoc 改为明确 1.7.10 契约——附属模组必须 `registerExportResource` 让首启从 jar 解压到 `<gamedir>/tacz/<目录名>` 后再扫描；`registerExtraGunPack` 的 deprecated 说明同步纠正；`registerExportResource` 补写落地实现与参数语义。**(b)（core 层加 builtin PackSource）见 §8-7 的跨模块结论，交 r7-respack-core 裁决** | 上游 `ResourceManager.java:20-33`；移植落地 `resource/GunPackLoader.java:116-128`（`GetJarResources.copyModDirectory`）；r7-respack-client 报告 §5.4#2 |
| 10 | `api/event/server/AmmoHitBlockEvent.java:1-88`（+ Lead 追加授权的跨 scope：`entity/EntityKineticBullet.java:663-670`、`event/ammo/DestroyGlassBlock.java`、`event/ammo/BellRing.java`） | 公开形状拍平：上游 `(Level, BlockHitResult hitResult, BlockState state, EntityKineticBullet)` + `getHitResult()/getState()/getLevel()/getAmmo()`；移植把 `hitResult` 拆成 `hitX/hitY/hitZ/side` 4 个 int、构造器 8 参，**丢失精确命中点 `hitVec`**，且 `getHitX/Y/Z/getSide` 是上游没有的公开 API | 恢复上游形状：构造器 `(World level, MovingObjectPosition hitResult, Block state, EntityKineticBullet ammo)`；getter 只保留 `getLevel()/getHitResult()/getState()/getAmmo()` + **平台附加** `getMetadata()`（BlockState 的 metadata 部分，构造时快照，报告标注）；删除拍平的 `getHitX/Y/Z()/getSide()`；post 点改传整个 `MovingObjectPosition`；消费者改 `getHitResult().blockX/Y/Z`（详见 §8.1） | 上游 `api/event/server/AmmoHitBlockEvent.java:13-47`；上游 post 点 `entity/EntityKineticBullet.java:490-496`；1.7.10 `MovingObjectPosition` = `BlockHitResult` 等价物（`blockX/Y/Z`/`sideHit`/`hitVec`） |

> 另：`api/modifier/ParameterizedCachePair` 注释中「fastutil 不随包分发」的表述修正为「仅经 GTNHLib 传递依赖出现在编译类路径、本工程未显式声明/未 shade」（事实核对：IDEA 依赖列表含 `fastutil-8.5.18.jar`；`dependencies.gradle` 未声明、未 shadow）。

---

## 5. 平台类型替换语义等价性（API 形状保持）

| 1.20.1（上游） | 1.7.10（移植） | 等价性判定 |
|---|---|---|
| `LivingEntity` | `EntityLivingBase` | ✅ 1.7.10 的生物基类；`IGunOperator`/`IThirdPartyAnimation`/事件实体参数统一替换 |
| `Player` / `ServerPlayer` | `EntityPlayer` / `EntityPlayerMP` | ✅ |
| `LocalPlayer` | `EntityPlayerSP` | ✅ |
| `ItemStack`（不可空） | `ItemStack`（可空，空栈=null） | ✅ 约定一致（全仓 `ItemStackNbtHelper.isEmpty`/`==null` 判定）；public API 的参数/返回值形状不变 |
| `CompoundTag` / `Tag` | `NBTTagCompound` / `Constants.NBT.TAG_*` | ✅ `getX/putX/contains/remove` 一一映射；上界判 `hasKey(key, type)` 等价 |
| `Component` | `IChatComponent` | ✅ `literal/translatable/getString` 等价（`ChatComponentText/ChatComponentTranslation`） |
| `ResourceLocation` | `ResourceLocation`（1.7.10 同名类） | ✅；`tryParse`（1.20 返回 null）→ `ResourceLocationHelper.tryParse`；非法输入语义已对齐 |
| `LogicalSide` | `cpw.mods.fml.relauncher.Side` | ✅ 均 {CLIENT, SERVER}；`isClient()` 语义相同 |
| `ForgeRegistries` / `RegistryObject` | `GameRegistry` 注册结果（Item 实例） | ⚠ 见 §9-3（`GunItemManager`） |
| `ForgeCapabilities.ITEM_HANDLER` / `IItemHandler` | `IInventory` | ⚠ 仅玩家背包可访问（1.7.10 无实体 capability），`AbstractGunItem#getEntityInventory` 已注释记录 |
| `NonNullList` | `java.util.List` | ✅ 仅容器接口差别（`fillItemCategory`） |
| `Item.Properties` | `Item#setMaxStackSize(1)`（基类构造器） | ✅ 等价于上游 `new Item.Properties().stacksTo(1)` |
| `ItemDisplayContext` / `PoseStack` / `ParticleOptions` / `ItemHitResult` 等 | `mcshim.*` shim / 字符串化 / `MovingObjectPosition|TacHitResult` | ✅ 逐项按 r2/r5/r7-model 结论；API 形状不变（`PoseStack` shim 同名同方法） |
| `EntityHitResult` | `TacHitResult`（`extends MovingObjectPosition`） | ✅ 实体 + 命中位置 + `entityHit`；ITargetEntity 本轮恢复上游参数顺序（§4-4） |
| `BlockHitResult` / `BlockState` | `MovingObjectPosition` / `Block`+metadata | ✅ 已恢复上游形状：`AmmoHitBlockEvent` 用 `getHitResult()` 整对象（含 `hitVec/sideHit/blockX/Y/Z`），`BlockState`→`Block`+`getMetadata()`（平台附加，§4-10） |

---

## 6. 事件语义核对（`api/event/**`，14 类）

| 维度 | 核对结果 |
|---|---|
| 字段/可见性 | 与上游逐字段一致（`EntityHurtByGunEvent` 的 `protected` 字段可被外部子类读取；`Pre` 的 `final` setter 集合一致） |
| getter 名 | `getShooter/getEntity/getBullet/getAttacker/getKilledEntity/getGunId/getGunDisplayId/getBaseAmount/getAmount(@Obsolete)/getDamageSource/getHeadshotMultiplier/isHeadShot/getLogicalSide` 全部一致 |
| 可取消性 | `@Cancelable` 5/5：`EntityHurtByGunEvent.Pre` + `RenderLevelBobEvent`×2 + `RenderItemInHandBobEvent`×2；`AmmoHitBlockEvent#isCancelable()=true`（上游同，类上无 `@Cancelable`，保持） |
| KubeJS 转发时机 | 与上游一致（构造器内 `postXxxEventToKubeJS(this)`；本轮接回条件分支，§4-6） |
| 触发点（只读核对，属 r7-gun） | `GunFire/GunMelee/GunReload` 的 9 个上游 post 点全部存在，且服务端（`entity/shooter/*`、`ModernKineticGunScriptAPI`）与客户端（`client/gameplay/LocalPlayer*`、`network/message/event/ServerMessageGun*`）**双端 post**（YSM 读客户端事件依赖此语义） |
| 上游 bug | `EntityHurtByGunEvent` 构造器**未赋值** `gunDisplayId`（上游 :34-48 亦未赋值）⇒ `getGunDisplayId()` 恒 null。**按「上游自身 bug 不修」保持等价**（勿在后续轮次当缺陷修） |

---

## 7. 公共命名解析（lead 追加项，已落地）

`api/util/ResourceLocationHelper.java:44-91` 新增：

```java
@Nullable public static ResourceLocation tryParseEntityName(@Nullable String entityName)
@Nullable public static ResourceLocation tryParseNormalized(@Nullable String entityName)   // 等价别名
```

语义：
1. `null`/空/trim 后空 → `null`；
2. 含 `:` → 直接 `tryParse`（原样，不做大小写变换）；
3. 含 `.` 且无 `:` → 把**最后一个** `.` 换成 `:`（FML `"<modid>.<entityName>"`）；
4. 其他 → `"minecraft:" + toLowerCase(Locale.ROOT)`（1.7.10 原版驼峰类名；如 `"ItemFrame"→item_frame` 的**多词名映射由调用方**按表补齐，不在本函数内）。

依据：FML `EntityRegistry.java:164`（`String.format("%s.%s", mc.getModId(), entityName)` 注册进 `EntityList`）与 1.7.10 `EntityList#getEntityString`（`classToStringMapping`，原版返回抛驼峰类名）。
幂等：对已带命名空间的返回结果再次调用不变；边界（`"modid."`/`".name"`/`":"`）经 `tryParse` 返回 null。
**协作**：已在消息里把最终签名/大小写注意点发给 r7-util；其 `util/EntityIdHelper` 直接委托本函数（含 `:`/`.` 分支），并在此之前自行 `toLowerCase` 以保持现有「全小写 1.20 风格」行为。lead 转派的 4 处消费者（`RenderHeadShotAABB`、`InteractKeyConfigRead`、`LootInjectionEvent`、`EntityIdHelper`）改用本函数后，`tacz.bullet → tacz:bullet` 才能正确命中配置/热成像/战利品表 id。

---

## 8. 跨模块发现（分工/未修，供 Lead 裁决）

| # | 级别 | 现象 | 证据 | 建议/状态 |
|---|---|---|---|---|
| 1 | 已修 | `api/event/server/AmmoHitBlockEvent` 公开形状漂移：上游 `(Level, BlockHitResult hitResult, BlockState state, EntityKineticBullet)` + `getHitResult()/getState()`；移植曾拍平为 `(World, int hitX, int hitY, int hitZ, int side, Block, int metadata, ...)` + 4 个 int getter，**丢失精确 `hitVec`** | 上游 `api/event/server/AmmoHitBlockEvent.java:13-42` ↔ 移植旧版 :22-85；post 点 `entity/EntityKineticBullet.java:626-634` | ✅ **Lead 追加授权后已修（§4-10/§8.1）**：api 类恢复 4 参构造 + `getHitResult(): MovingObjectPosition`；post 点改传整对象；`DestroyGlassBlock`/`BellRing` 消费者同步；`lint error=0` |
| 2 | 已修 | `EntityKineticBullet#shootFromRotation(..., float inaccuracy)` 散布噪声曾是 1.7.10 `EntityThrowable` 形态（`gaussian × 0.007499999832361937×inaccuracy`），与上游 1.20.1 `Projectile#shoot` 的 `triangle(0, 0.0172275×inaccuracy)` 分布不同；`ModernKineticGunItem#doBulletSpread` 实际走这里 ⇒ 与 api 默认实现（§4-1）分叉 | 移植 `entity/EntityKineticBullet.java:434-501` ↔ 上游 `Projectile.java:120-140` | ✅ **r7-gun 并发修复（已落盘）**：新增 `private static applyShoot`（normalize→triangle→scale→motion/rotation/prev），重载补回 `(pX+pZ)`；与 api 默认实现同公式/系数 ⇒ 两条路径一致。双方已互相报备文件区域、无冲突 |
| 3 | 信息 | 上游 `GunMod` 构造器在 KubeJS 装载时 `bus.register(new TimelessKubeJSPlugin())`；移植 `GunMod` 未调用 `TimelessKubeJSPlugin.init()` | 上游 `GunMod.java:54-55`；移植 `GunMod.java`（只读） | 1.7.10 无 KubeJS ⇒ 零影响；若日后出现 1.7.10 KubeJS 再接线（`TimelessKubeJSPlugin` 已备好 `init/postKubeJSEvent`）。**不修** |
| 4 | 限制 | 独立「配件物品栈」自身 NBT 里的旧配件 id 不会迁移（懒修复入口 `getAttachmentId` 覆盖主要路径：枪内嵌配件读取即修；独立物品的 `getAttachmentId` 纯读不写回） | `api/item/nbt/AttachmentItemDataAccessor#getAttachmentIdFromTag`（保持上游「纯读」语义）；r4-C-4 明确「另一入口可后续」 | **保留为已记录限制**（Lead 已裁决） |
| 5 | 确认 | `AmmoParticle#{name→particleOptions}` 等「1.20 对象类型字符串化」的 API 面：**不在 `api/**`**（属 `client/resource/pojo/display/ammo` + `client/particle`，r7-model scope）；`api/**` 仅有动画通道的 `ParticleEffectData`（vendored SBM 类型，上游同形状） | 全仓：`AmmoParticle` 2 个 set 点（`GunDisplayInstance:726`、`ClientAmmoIndex:256`，均剥 namespace）+ 1 个 get 点（`AmmoParticleSpawner:72`）闭环一致 | ✅ 确认 r7-model 结论；api 域无同类问题 |
| 6 | 已办 | `ITargetEntity` 签名对齐需要改 3 处跨模块编辑点；lead 已授权本任务代改（r7-gun inactive） | `entity/EntityKineticBullet.java:465`（改传 `result`）、`:494`（删二次派发）；`entity/TargetMinecart.java:115-121`（签名+instanceof） | ✅ **已改完**，`lint error=0`；**请 Lead 在最终 diff 复核这 2 个文件时注明「r7-gun 已复核/本改动由 r7-api 代改」**（lead 消息原话） |
| 7 | P2 | 模组 jar 内直接内嵌的枪包 `assets/**`/`data/**` 不被扫描（`GunPackLoader` 只扫 `<gamedir>/tacz/**` 来源）；连带 TACZ 自身 jar 根的 `assets/tacz/animations/{rifle,pistol}_default.animation.json` 对「显式 `default_animation` 引用」不可见（r7-respack-client §5.4#2 同口径） | 移植 `resource/GunPackLoader`+`resource/pack/**`（r7-respack-core）、`client/resource/ClientAssetsManager`（r7-respack-client）、`api/resource/ResourceManager`（本任务） | **本任务裁决 (a)**：api 契约已改为「必须 `registerExportResource` 导出」（§4-9；导出路径已实测可用：默认枪包即经 `GetJarResources.copyModDirectory` 解压后生效）。**(b) 建议（交 r7-respack-core 裁决，Lead 并存）**：若要恢复上游「jar 内嵌即生效」，应在 **core 层**新增只读的 `BuiltinGunPackSource`（最低优先级，扫 jar 内 `assets/tacz/**` 与各模组的枪包目录；zip 来源复用 `ZipGunPackSource` 的读取方式），并注意与「默认枪包导出到 gamedir」去重（同 packId 以外部目录优先）。若 core 只想要最小修复，可先做一个 **targeted** 版：只把 TACZ 自身 jar 根的 `assets/tacz/animations/**` 注入 `ClientAssetsManager.bedrockAnimation`（修显式 `default_animation` 引用），第三方内嵌包仍要求导出 |

### 8.1 `AmmoHitBlockEvent` 形状对齐（Lead 追加授权跨 scope 改 `EntityKineticBullet`）

| 项 | 上游（1.20.1） | 移植（修复前） | 移植（修复后，本轮） |
|---|---|---|---|
| 构造器 | `(Level, BlockHitResult hitResult, BlockState state, EntityKineticBullet ammo)` | `(World, int hitX, int hitY, int hitZ, int side, Block, int metadata, EntityKineticBullet)`（拍平 8 参） | `(World level, MovingObjectPosition hitResult, Block state, EntityKineticBullet ammo)` ← 上游 4 参、顺序一致 |
| getter | `getLevel()/getHitResult()/getState()/getAmmo()` | `getLevel/getHitX/getHitY/getHitZ/getSide/getState/getMetadata/getAmmo`（多 4 个上游没有的公开 getter） | `getLevel()/getHitResult()/getState()/getAmmo()` + **平台附加** `getMetadata()`（BlockState 的 metadata 部分，构造期从 world 快照；报告标注） |
| 命中信息 | `BlockHitResult`（`getBlockPos()/getDirection()/getLocation()`） | 4 个 int（坐标+面），**丢失 `hitVec`** | `MovingObjectPosition`：`blockX/Y/Z` + `sideHit` + `hitVec`（BlockHitResult 的 1.7.10 等价物，信息完整） |
| 平台类型 | — | — | `Level→World`、`BlockHitResult→MovingObjectPosition`、`BlockState→Block`（+metadata） |
| post 点 | `EntityKineticBullet.java:490-496` | `EntityKineticBullet.java:626-634`（拆 int 传参） | `EntityKineticBullet.java:663-670`：`new AmmoHitBlockEvent(this.worldObj, result, hitBlock, this)`（整对象传入；删掉已无用的 `int metadata` local） |
| 消费者 | `DestroyGlassBlock` 用 `event.getHitResult().getBlockPos()` + `getState()`；`BellRing` 用 `getHitResult()` | `DestroyGlassBlock` 用 `getHitX/Y/Z()`；`BellRing` 无操作（仅注释） | `DestroyGlassBlock`：`MovingObjectPosition hitResult = event.getHitResult()` → `blockX/Y/Z` + `getState()` + `getMetadata()`；`BellRing`：注释更新，实现仍为降级空实现 |
| 兼容性 | — | — | 删除的 `getHitX/Y/Z()/getSide()` 为**上游不存在**的移植期公开 getter，仅本仓库消费者使用，已全部改完（全仓 grep 0 残留）；YSM 不使用该事件。`getHitResult()/getState()/getLevel()/getAmmo()` 返回类型为 1.7.10 等价物 |
| 验证 | — | — | 4 个文件 `lint_files(error)` = **0**；r7-gun 在事件形状变更后**只读复核** `event/ammo/**` 判定链与上游逐行一致、确认无 scope 内回归（其报告 §9-5），两方文件区域已互相报备无冲突 |

---

## 9. 未修/有据降级清单（其余均与上游等价）

| # | 项 | 级别 | 有据降级理由 |
|---|---|---|---|
| 1 | ~~`AmmoHitBlockEvent` 形状（§8-1）~~ | — | **已解除**：Lead 追加授权后已恢复上游形状（§4-10/§8.1），不再属未修项 |
| 2 | `IThirdPersonAnimation`/`ThirdPersonManager` 增加类级 `@SideOnly(CLIENT)` | 信息 | 上游无 `@OnlyIn`，但两类的参数类型是 client-only `ModelRenderer`；专服本不该加载。副作用：专服调用 `TimelessAPI#registerThirdPersonAnimation` 会 `NoClassDefFoundError`（上游 1.20 专服不会，但该 API 只可能被客户端调用；r5 已登记） |
| 3 | `GunItemManager`：`Map<String, AbstractGunItem>` 取代 `RegistryObject<? extends AbstractGunItem>`；`getGunItemRegistryObject→getGunItem`；`getAllGunItems` 返回 `Collection<? extends AbstractGunItem>` | P3 | 1.7.10 无 `DeferredRegister`/`RegistryObject`，注册结果就是 Item 实例（`GameRegistry.registerItem`）。语义等价（调用方少一层 `.get()`），类注释已写明 |
| 4 | `ParameterizedCachePair` 不再 `implements fastutil.Pair`（保留 `left()/right()/of()`） | P3 | fastutil 8.5.18 仅作为 GTNHLib 传递依赖出现在编译类路径，本工程未声明/未 shade；不把打包范围外类型暴露到公开 API。外部代码经 `left()/right()` 使用不受影响 |
| 5 | `IAnimationItem.matchesIgnoreCount` 增加 null 判空（上游不可空） | 信息 | 1.7.10 空栈=null；不改变非空栈语义（本轮 remove damage 比较后与 `isSameItemSameTags` 完全一致） |
| 6 | `LuaEntityAccessor#sendActionBar` 降级为聊天消息 | P3 | 1.7.10 无 action bar：`S02PacketChat` 只有 `(IChatComponent, boolean)` 构造器，**没有 1.8+ 的 position 字段**；`Player#displayClientMessage(..., true)` 客户端渲染入口也不存在。仅影响 Lua 脚本视觉 |
| 7 | `AbstractGunItem#getEntityInventory` 只返回玩家背包 | P3 | 1.7.10 无实体 capability/统一背包抽象；非玩家 shooter 的 `canReload/hasInventoryAmmo/卸弹` 返回 false（上游 mob 有 capability 时可为 true，但 TACZ 玩家为主） |
| 8 | `ItemStackNbtHelper.save/load` 用 1.7.10 ItemStack NBT 布局（`id` short/`Damage` short） | 信息 | 1.20.1 的 `id` 字符串/`Count` 布局在 1.7.10 不可读；本 helper 只服务 1.7.10 自身 NBT 往返（枪内嵌配件 tag），自洽即可。类 javadoc 已写明 |
| 9 | `GunItemBuilder#build()`/`AbstractGunItem#getBuiltinAttachment` 等返回 `null` 表示空栈（上游 `ItemStack.EMPTY`） | 信息 | 1.7.10 无 `ItemStack.EMPTY` 单例；`@Nullable` + javadoc 已标注，内部调用点均 `ItemStackNbtHelper.isEmpty` 判定 |
| 10 | `ObjectAnimationSoundChannel` 的 1-tick 位置差 | 信息 | `Entity#getPosition(0)`（上一次 tick 插值位置）→ 1.7.10 实体间距离（当前 tick）；已在代码注释记录，音量的差异不可感知 |
| 11 | `KubeJSGunEventPoster` 转发目标只剩 1 个 compat 门面（上游 3 个 EventGroup 类已随 KubeJS 绑定删除） | 信息 | KubeJS 只支持 1.16.5+，1.7.10 无该库；门面 `TimelessKubeJSPlugin` 保留 `LOADED` 探测与 `postKubeJSEvent` 入口（§8-3） |
| 12 | 上游自身 bug | — | `EntityHurtByGunEvent.gunDisplayId` 未赋值（上游 :34-48）——**不修**，保持与上游等价。`Spline`/`AbstractGunItem` 等处上游 `TODO` 注释也按「上游自身」保留 |

---

## 10. 覆盖清单（诚实）

- **机检（115/115 上游文件）**：① 文件集合；② 嵌套类型声明集合；③ public/protected 成员声明集合（名称/参数个数/返回类型/`static/final/abstract`）；④ 注解多重集（`@OnlyIn→@SideOnly`、`@Cancelable`、`@Deprecated`、`@Nullable/@Nonnull/@NotNull` 等）；⑤ 全文 token 多重集（先统一平台类型名，再比对）——⑤ 的结果用于把「仅格式化/import 差异」与「真实语义修改」分离（结论：44 个 `client/animation/**` 文件 100% token 等价，其余差异全部落到 §4/§5/§9）。
- **逐行读两侧（人工）**：`TimelessAPI`、`DefaultAssets`、`GunProperties`、`GunProperty`、`IGun`、`ITargetEntity`、`IGunOperator`、`IClientPlayerGunOperator`、`ReloadState`、`GunTabType`、`FireMode`、`AttachmentType`、`CacheModifiableByScript`、`ValueModifiableAtRuntime`、`AbstractGunItem`、`IAnimationItem`、`GunItemManager`、`ItemStackNbtHelper`、`ResourceLocationHelper`、`KubeJSGunEventPoster`、`LuaEntityAccessor`、`ThirdPersonManager`、`IThirdPersonAnimation`、`KeepingItemRenderer`、`BeforeRenderHandEvent`、`EntityHurtByGunEvent`、`EntityKillByGunEvent`、`AmmoHitBlockEvent`、`ObjectAnimationSoundChannel`、`Animations`、`ObjectAnimationRunner`、`ParameterizedCachePair`、`JsonProperty`、`ResourceManager`、`JsonResourceLoader`、`ObjectAnimationSoundChannel`。
- **完整 normalized line-diff 逐文件（非仅机检）**：`event/common/**` 12 文件、`item/**` 20 文件、`client/{event,other,gameplay}/**` 9 文件、`client/animation/gltf/**` 与 `interpolator/**`（差异仅格式化）、`vmlib/**` 4 文件、`modifier/**` 5 文件、`entity/**` 5 文件、`resource/**` 2 文件。
- **未逐行读**：`client/animation/gltf/**` 的数学/字节解析内部实现（已由 token 集与 normalized line-diff 证明与上游等价，仅格式化差异；r2 亦已机检）。
- **未运行 gradle / runClient**：全部结论为静态证据；§11 为需实机确认项。

---

## 11. 需实机验证清单

1. **第三人称 minigun 姿态**（§4-3）：手持 `minigun` 类枪进入第三人称，持枪/瞄准时双臂位置随身体转动（修复前 offset 恒 0 → 无该姿态）；普通枪姿态无回归。
2. **子弹散布**（§4-1 + §8-2）：步枪/霰弹连续射击，弹着点仍呈合理散布（api 默认实现与 `EntityKineticBullet#shootFromRotation` 已同为上游 triangle×0.0172275，两条路径一致）。
3. **打靶实体**（§4-4）：`tacz:target_minecart` 被子弹命中 → 音效/聊天提示/`TargetHitTrigger`/`ServerMessageGunHurt` 正常；命中后**无重复**事件（二次派发已删）。
4. **旧配件 id 迁移**（§4-5）：用带 `tacz:muzzle_silence_*` 的枪 NBT 进游戏 → 读取后枪 NBT 内 `AttachmentId` 变为 `muzzle_silencer_*`（NBT 查看/调试命令）；不迁移也不会崩（仅 id 查不到索引）。
5. **实体名配置键**（§7）：`bulletDamageIgnoreList`/热成像白黑名单等配置 `tacz:xxx`（FML 实体）与 `minecraft:item_frame` 能命中（需 r7-util 消费者已切换新函数）。
6. **KubeJS 条件分支**（§4-6）：无 KubeJS 环境下事件正常 post、无异常日志；专服启动无 `NoClassDefFoundError`。
7. **YSM 契约**（§2）：按 `docs/YSM_COMPAT_HANDOFF.md` 写的 compat 编译期可直接引用全部符号（12 项），运行期 `getCommonGunIndex/getIGunOrNull/getSyn*` 返回正常。

---

## 12. 改动文件 + lint

`lint_files(min_severity="error")` 对下表全部文件 = **0**（`api/**` 全包无 error）。

| 文件 | 改动 |
|---|---|
| `api/entity/ITargetEntity.java` | 签名恢复上游形状 `(Entity, TacHitResult, DamageSource, float)`（§4-4） |
| `api/item/gun/AbstractGunItem.java` | `doBulletSpread` 按 1.20.1 `Projectile#shootFromRotation/#shoot` 逐行复刻；`isSame` 注释纠正（§4-1/§4-7） |
| `api/item/IAnimationItem.java` | `matchesIgnoreCount` 去掉 damage 比较（§4-2） |
| `api/client/other/ThirdPersonManager.java` | MINI_GUN `x/z→rotationPointX/Z` + 类注释（§4-3） |
| `api/client/other/IThirdPersonAnimation.java` | 字段映射注释同步（§4-3） |
| `api/item/nbt/GunItemDataAccessor.java` | `getAttachmentTag` 懒修复旧配件 id（§4-5） |
| `api/event/common/KubeJSGunEventPoster.java` | 接回 `TimelessKubeJSPlugin` 条件转发（§4-6） |
| `api/util/LuaEntityAccessor.java` | action bar 降级说明更正（§4-7） |
| `api/modifier/ParameterizedCachePair.java` | fastutil 事实性注释更正 |
| `api/util/ResourceLocationHelper.java` | 新增 `tryParseEntityName` / `tryParseNormalized`（§4-8/§7） |
| `api/resource/ResourceManager.java` | 1.7.10 内置资源契约 javadoc 纠正（§4-9/§8-7） |
| `api/event/server/AmmoHitBlockEvent.java` | **恢复上游形状**：4 参构造 `(World, MovingObjectPosition, Block, EntityKineticBullet)` + `getHitResult()`，删拍平 getter，`getMetadata()` 标注平台附加（§4-10/§8.1） |
| `entity/TargetMinecart.java`（lead 授权代 r7-gun 改） | ITargetEntity 新签名 + `instanceof EntityKineticBullet projectile` + 类注释（§4-4/§8-6） |
| `entity/EntityKineticBullet.java`（lead 授权代 r7-gun 改，未碰 r7-gun 区域 `:252-261/:312-330/:418-500/:712/:1032`） | ① `:465` ITargetEntity 派发传 `result`；删除上游没有的二次派发（§4-4/§8-6）；② `:670` `AmmoHitBlockEvent` 改传整个 `MovingObjectPosition`、删无用 `int metadata` local（§4-10/§8.1）。**注**：同文件的散布噪声对齐由 r7-gun 并发完成（§8-2），双方已报备区域、lint 全 0 |
| `event/ammo/DestroyGlassBlock.java`（Lead 授权同步消费者） | `getHitX/Y/Z()` → `getHitResult().blockX/Y/Z`；import `MovingObjectPosition`（§8.1） |
| `event/ammo/BellRing.java`（Lead 授权同步消费者） | 类注释更新为恢复后的 `getHitResult()` 形状；实现仍为无钟方块降级空实现（§8.1） |

> 报告生成时的改动证据：`git status` 显示上述文件为 `M`；未触碰 `tmp/**`/`build/**`/`run/**`/`src/main/resources/**`，未执行任何 git checkout/stash/reset/commit，未运行 gradle。
