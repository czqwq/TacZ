# r2-03 · 武器逻辑 / 视角 / 开火 / 换弹审查

> **作用域（写作用域）**：`src/main/java/com/tacz/guns/entity/**`（27：shooter 15 + sync 9 + 顶层 3）、
> `item/**`（12）、`event/**`（19）、`client/gameplay/**`（13）、`client/input/**`（13）、`client/animation/**`（8）。
> 作用域外**只读**核对（发现缺陷只登记）：`client/event/**`、`mixin/**`、`api/**`、`util/**`、`network/**`、`init/**`。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）。
> **1.7.10 准绳**：`build/rfg/minecraft-src/java/**`（`Item` / `ItemStack` / `Entity` / `EntityLivingBase` /
> `EntityPlayer` / `EntityPlayerMP` / `KeyBinding` / `GameSettings` / `Minecraft` / `PlayerControllerMP` /
> `ItemInWorldManager` / `World` / `DamageSource` / `EntityDamageSourceIndirect` / `MovingObjectPosition` /
> `IntHashMap` / `FMLCommonHandler`）。
> **覆盖清单**：
> - **逐行/逐方法对照上游**：`entity/shooter/*`（15，含 `ShooterDataHolder`）、`entity/EntityKineticBullet`(939L)、
>   `entity/TargetMinecart`、`item/ModernKineticGunItem`(784L)、`item/ModernKineticGunScriptAPI`(885L)、
>   `client/gameplay/*`（13，重点 `LocalPlayerShoot`/`LocalPlayerReload`/`LocalPlayerBolt`/`LocalPlayerDataHolder`）、
>   `client/input/*`（13，重点 `GunInputHandler`/`ShootKey`/`AimKey`/`ReloadKey`）、
>   `client/animation/statemachine/GunAnimationStateContext`(605L)、`client/animation/screen/RefitTransform`、
>   `event/{SyncedEntityDataEvent,PlayerRespawnEvent,EntityDamageEvent,KnockbackChange,SyncBaseTimestamp,
>   TravelToDimensionEvent,ServerTickEvent,PreventGunClick,EventHandlers}`、`entity/sync/**`（9，含 core 7）。
> - **速览/契约核对**：`item/{AmmoItem,AttachmentItem,AmmoBoxItem,AmmoStorageBlockItem,GunSmithTableItem,
>   GunPegboardItem,DefaultTableItem,GunTooltipPart,ItemClientHooks,TargetMinecartItem}`、
>   `entity/EntityBulletClientHooks`、`event/{AmmoStorageInteractionEvent,GunDisplayCaseInteractionEvent,
>   GunPegboardInteractionEvent,GunSoundAngerEvent,HitboxHelperEvent,CommonLoadPack,LoadingConfigEvent,
>   ChangeGunPropertyEvent,ammo/BellRing,ammo/DestroyGlassBlock,PreventGunClick}`、
>   `client/animation/{third/*,statemachine/ItemAnimationStateContext,ThrowableAnimationStateContext}`。
> - **链路只读核对**：`client/event/{CameraSetupEvent(474L),ClientPreventGunClick,InventoryEvent,RenderCrosshairEvent}`、
>   `mixin/{common/LivingEntityMixin,common/ServerPlayerMixin,client/LocalPlayerMixin,client/MouseHandlerMixin}`、
>   `util/block/BlockRayTrace`、`util/time/GunTime`、`util/CycleTaskHelper`、`network/message/ClientMessagePlayerShoot`。
> **自查方式**：`mcp__idea__get_file_problems(errorsOnly=true)` + `lint_files(min_severity=error)`，
> 改动 7 文件全部 **0 error**；**未运行任何 gradle 任务**（全程静态源码比对 + 1.7.10 反编译源码取证）。

---

## 结论速览

| 级别 | 本轮已修 | 未修（只报告） |
|---|---|---|
| **P0** | 1 | 0 |
| **P1** | 5 | 2 |
| **P2** | 2 | 3 |
| **P3** | 1 | 3 |

**最关键的 3 条结论**

1. **P0 已修 —— 装 TACZ 后原版左/右键整局失效**：`ShootKey.SHOOT_KEY = -100` 已与「原版攻击键」同码、
   `AimKey.AIM_KEY = -99` 已与「原版使用键」同码；1.7.10 `KeyBinding` 构造器把 `keyCode → binding` 写进
   **静态 `IntHashMap`**（后写覆盖先写），而 `Minecraft#runTick` 只按 keyCode 查这一张表更新
   `pressed`/`pressTime` ⇒ 原版 `keyBindAttack`/`keyBindUseItem` 再也收不到状态，
   **挖掘/攻击、使用/放置/开箱/进食全部失效**。本轮在 `client/input/GunInputHandler` 注册完键位后把两个鼠标绑定
   从 `KeyBinding` 静态 `keybindArray` 摘除并重建 hash（TACZ 自己的轮询走 `GameSettings.isKeyDown`，直接读鼠标硬件，
   不依赖 pressed/pressTime）。上一轮报告只发现了「键盘 T 键」冲突（`docs/PORT_STATUS.md:1240` L4），漏了鼠标键。
2. **P1 已修 —— 子弹伤害源没接线，穿甲/防弹属性全废**：`init/ModDamageTypes` 早已提供
   `Sources.bullet(..., ignoreArmor)` / `BulletDamageSource`（`setDamageBypassesArmor`），`event/EntityDamageEvent`
   也已注册并依赖 `isBulletDamage(source)`，但 `EntityKineticBullet#createDamageSources` 仍返回
   **同一条 `DamageSource#causeThrownDamage`**（普通/穿甲两段相同）⇒ ①`armorIgnore`（穿甲）不生效；
   ②`BULLET_RESISTANCE` 属性永不参与；③死亡消息不是 `death.attack.tacz.bullet`。已改为
   `Sources.bullet(this, owner, false/true)` 两段（末影人保留间接魔法源）。
3. **P1 已修 —— 子弹方块射线用错 API**：`util/block/BlockRayTrace`（上游 DDA + `ClipContext.Block.COLLIDER`
   + `tacz:bullet_ignore` 标签降级 + `canPassThrough` 配置）已存在，但子弹仍调
   `World#rayTraceBlocks`（凡非空气/液体都命中）⇒ 被高草/花/火把挡住、打不穿铁栏杆/栅栏/树叶、无视可穿透配置。
   已改为 `BlockRayTrace.rayTraceBlocks(worldObj, start, end)`。
4. **另**：fidelity-audit 上报的 **D-1（多段实体 part→core 未移植，打末影龙时事件/无敌帧/击退全错）** 已确证并修复，
   见 `F8`；其证据与我的复核一致（1.7.10 `EntityDragonPart`/`EntityDragon#attackEntityFromPart`），
   且 fidelity-audit 已按源码行号**独立复核通过**（记闭环，见 `docs/review/r2/05-fidelity.md` §一 D-1 处置）。

---

## 一、已修问题

### 1.1 本轮修复（8 处，全部 0 error）

> 行号口径：本表按**当前工作区文件**定稿（`EntityKineticBullet.java` 现为 1012 行；F8 的 `MaybeMultipartEntity`
> 在 `:752-777`、`tacAttackEntity` 在 `:789-799`）。F8 已由 fidelity-audit 独立复核（未采信自述，按源码逐项验证）通过。

| # | 文件:行 | 严重度 | 现象 | 修法 | 依据（上游 / 1.7.10） |
|---|---|---|---|---|---|
| F1 | `client/input/GunInputHandler.java:105-111,153-184`（新增 `detachMouseBindingsFromVanillaKeyBindingHash`，`registerKeyMappings` 末尾调用） | **P0** | 客户端一启动，原版左键（攻击/挖方块/连挖）与右键（使用物品/放置/开箱/进食/拉弓）**全部无响应**：1.7.10 `KeyBinding` 构造器执行 `keybindArray.add(this); hash.addKey(keyCode, this)`（`KeyBinding.java:82-91`，同 keyCode 后者覆盖前者）；`Minecraft#runTick` 鼠标分支用 `KeyBinding.setKeyBindState(j-100, state)` / `onTick(j-100)`（`Minecraft.java:1776-1786`），这两个静态方法按 keyCode 从 `hash` 取唯一 binding 改 `pressed`/`pressTime`（`KeyBinding.java:28-52`）。原版 attack/use 的 keyCode 就是 `-100/-99`（`GameSettings.java:213/211`），而 TACZ 的 `SHOOT_KEY/AIM_KEY` 也是 `-100/-99`（`ShootKey.java:40`、`AimKey.java:32`，上游 1.20 就是「左/右键」，不能降级）→ hash 槽位被 TACZ 顶掉，原版 `pressed` 恒 false，`Minecraft.java:2005-2058` 的攻击/使用分支全不执行 | 注册完键位后：反射按**字段类型**（静态 `List`，`KeyBinding` 内唯一）定位静态 `keybindArray`，把 `SHOOT_KEY`/`AIM_KEY` 移除，再 `KeyBinding#resetKeyBindingArrayAndHash()` 重建 hash（原版 `-100/-99` 恢复唯一映射）。TACZ 两个绑定仍留在 `GameSettings#keyBindings`（`ClientRegistry.java:47-50` 追加）→ 控制界面可见/可改键、`options.txt` 照旧保存；后续「控制」界面的 reset（`GuiControls.java:79/97/135`、`GameSettings.java:1079`）从 `keybindArray` 重建时不会再被污染，**无需每 tick 修补**。按字段类型而非字段名反射，规避 MCP/SRG 名差异 | 上游：`ShootKey`/`AimKey` 用 1.20 `KeyMapping`（独立注册表，`KeyMapping#isDown` 直接读硬件，与 vanilla `keyOptions` 不共享单槽状态）⇒ 上游不存在该冲突；1.7.10：`KeyBinding.java:17/28-52/82-91`、`Minecraft.java:1776-1786/2005-2058`、`GameSettings.java:211/213/319-322/1079`、`ClientRegistry.java:47-50`；团队既有认知 `docs/PORT_STATUS.md:1240`（L4，只处理了键盘 `T` 冲突） |
| F2 | `entity/EntityKineticBullet.java:730-741`（`createDamageSources`） | **P1** | 穿甲属性 `armorIgnore`、子弹抗性属性 `BULLET_RESISTANCE`、`death.attack.tacz.bullet` 死亡消息**全部失效**：普通/穿甲两段伤害用同一条 `causeThrownDamage`（都不无视护甲），`ModDamageTypes.isBulletDamage` 恒 false | 普通段 `ModDamageTypes.Sources.bullet(this, owner, false)`、穿甲段 `(..., true)`；末影人保留 `causeIndirectMagicDamage`（上游 `USE_MAGIC_DAMAGE_ON` 硬编码降级） | 上游 `EntityKineticBullet.java:575-590`（`Pair<非穿甲源, 穿甲源>`，`Sources.bullet(..., false/true)`）；本工程 `init/ModDamageTypes.java:76-141`（`BulletDamageSource` + `setDamageBypassesArmor`，注释已写明「语义等价」）；1.7.10 `DamageSource.java:164`（`setDamageBypassesArmor`）、`EntityDamageSourceIndirect.java`（死亡消息 `death.attack.<damageType>[.item]`，lang 键已存在 `assets/tacz/lang/en_US.lang:385-386`） |
| F3 | `entity/EntityKineticBullet.java:333-346`（`onBulletTick` 方块射线） | **P1** | 子弹会被高草/花/火把等**无碰撞箱方块**挡住；打不穿铁栏杆/栅栏/栅栏门/树叶（上游 `tacz:bullet_ignore` 是穿过的）；`AmmoBlockConfigRead.canPassThrough`（可穿透方块）配置被忽略：全部因为用了 `World#rayTraceBlocks`（`World.java:1161-1164` → `func_147447_a(..., ignoreBlockWithoutBoundingBox=false, ...)`，凡非空气/液体都命中，`World.java:1189`） | 改调**已存在的等价实现** `com.tacz.guns.util.block.BlockRayTrace.rayTraceBlocks(worldObj, startVec, endVec)`（上游 DDA 算法 + `ClipContext.Block.COLLIDER`/`Fluid.NONE` 语义 + `isVanillaIgnoredBlock` 覆盖铁栏杆/栅栏/栅栏门/树叶 + `canPassThrough` 配置 + `registerIgnoreBlock` 扩展点） | 上游 `EntityKineticBullet.java:314`（`BlockRayTrace.rayTraceBlocks(level, new ClipContext(..., Block.COLLIDER, Fluid.NONE, this))`）；本工程 `util/block/BlockRayTrace.java:108-146`（同语义实现，此前只有 `ProjectileExplosion` 在用） |
| F4 | `entity/TargetMinecart.java:64-72`（三参构造器） | **P1** | 用物品**放置**出来的标靶车命中盒退回原版矿车的 0.98×0.7（上一轮 G6 声称修好的 0.75×2.4 实际只对 1 参构造器生效）：`TargetMinecartItem.java:76` 走 `new TargetMinecart(world, x, y, z)` | 三参构造器同样 `setSize(0.75F, 2.4F)` + `yOffset = height/2` | 上游 `TargetMinecart.java:41`（`EntityType.Builder#sized(0.75F, 2.4F)`，builder 生成的**所有**实例同一尺寸）；1.7.10 `EntityMinecart.java:70-71`（默认 0.98×0.7 + yOffset=height/2） |
| F5 | `client/animation/screen/RefitTransform.java:19-53,124-153` | **P1** | 改装界面「开镜视角切换」动画完全不动：`tickInterpolation` ①是 `public static` 且带 `@SubscribeEvent`（FML `ASMEventHandler` 用 `INVOKEVIRTUAL`，静态方法会 `IncompatibleClassChangeError`）②全仓**从未注册**到任何总线（注释称由 `ClientProxy.preInit` 注册，实际 `ClientProxy` 的三张类清单里没有它）③注释写的是 Forge 总线，但 `TickEvent.RenderTickEvent` 只从 FML 总线发出（`FMLCommonHandler.java:247/260`）⇒ `refitScreenOpeningProgress` 恒 0、`changeRefitScreenView` 因「opening != 1」恒 false | 增加 `INSTANCE` + `ensureRegistered()`（首次 `init()` 时注册到 `FMLCommonHandler.instance().bus()`，幂等）；`tickInterpolation` 改实例方法；注释改为实际机制 | 上游 `RefitTransform.java:16`（`@Mod.EventBusSubscriber(CLIENT)` 自动注册）+ `:80-107`（实例无关的等价逻辑）；1.7.10 团队自身铁律：处理器必须实例方法（`03-client-ui.md` §2、`ClientSetupEvent.java:99-102` 已写明「传 Class 对象不会注册任何东西」） |
| F6 | `item/ModernKineticGunScriptAPI.java:196-203` | **P2** | 连发/霰弹脚本循环里用 `shooter.isDead` 判死亡：1.7.10 `Entity#isDead` 要等死亡动画结束（约 20 tick）才置位 ⇒ 玩家已死时仍会把这一串 burst 打完（客户端已停、服务端还在减弹药/生成子弹） | 改 `!shooter.isEntityAlive()`（= `isDead || health<=0`，等价上游 `isDeadOrDying()`） | 上游 `ModernKineticGunScriptAPI.java:163`（`shooter.isDeadOrDying()`）；1.7.10 `EntityLivingBase.java:996/356-374`（`onDeath` 只置 `dead`，20 tick 后才 `setDead()`）；与上一轮 G3（`EntityKineticBullet` 击杀判定）同一修法 |
| F7 | `entity/sync/ModSerializers.java:30-46`（`RELOAD_STATE.read(PacketBuffer)`） | **P3** | 「ReloadState 序号越界」的防御分支在**未读 `countDown`(8 字节)** 时提前 return ⇒ 本 `DataEntry` 少读 8 字节，后续 entry 全部按错位字节流解码 → 整包 `DecoderException`（畸形/版本错配包的失败半径从「这一个值」扩大到「整包」） | 先 `long countDown = buf.readLong()` 再判越界，越界时仅返回默认 `ReloadState`（字节流保持对齐） | 上游 `ModSerializers.java`（1.20 用 `SynchedEntityData`，无该防御分支）；本工程 `entity/sync/core/DataEntry.java:67-71`（`readValue` 逐 entry 连续读） |
| F8 | `entity/EntityKineticBullet.java:752-777`（新增私有 `MaybeMultipartEntity`）、`:789-799`（`tacAttackEntity` 重写为吃 parts）、`:491`/`:509-587`（`onHitEntity` 改吃 parts：击退/爆炸/Post-Kill 事件） | **P1** | **多段实体（part→core）机制未移植**（fidelity-audit 的 D-1，本轮已确证并修复）：1.7.10 子弹优先命中 `EntityDragonPart`（`canBeCollidedWith()==true`，`EntityDragonPart.java:38-49`），而移植把 part 当核心 ⇒ ①part 非 `EntityLivingBase` → 打末影龙等部件实体时 `EntityHurtByGunEvent.Post`/`EntityKillByGunEvent`、伤害数字、击杀特效、`ServerMessageGunHurt/Kill` **全部丢失**；②无敌帧（1.7.10 `hurtResistantTime`，`EntityLivingBase.java:854-872`）只清在 part 上，龙本体 20 tick 无敌帧仍在 → **穿甲段被吞**；③击退 strength 设在 part 上（非 living）→ 无效；④爆炸前清无敌帧清错对象 | 新增 1.7.10 版 `MaybeMultipartEntity`：`EntityDragonPart.entityDragonObj instanceof Entity` 时 core=本体、否则 core=hitPart；`onHitEntity` 的「击退/爆炸前清无敌帧/Post/Kill 事件」一律用 `parts.getCore()`，`tacAttackEntity` 把伤害施加到 `parts.getHitPart()`、并在两次伤害前清 `core.hurtResistantTime`（= 上游 `core.invulnerableTime=0`） | 上游 `EntityKineticBullet.java:384-394`（`record MaybeMultipartEntity`）、`:411/:427`（`of(entity)`）、`:448-458`（击退用 core）、`:463/:599/:603`（清 core 无敌帧）、`:467-480`（事件用 core）、`:601/:605`（伤害施加 hitPart）；1.7.10 `EntityDragonPart.java:11,38-49`、`EntityDragon.java:542-563`（`attackEntityFromPart` 对非头部伤害 /4+1；只有 `source.getEntity() instanceof EntityPlayer` 或爆炸才真正扣血） |

### 1.2 上两轮「已修」结论复核（**逐条回代码确认：修复在且正确**）

| 结论（docs/review/04-gameplay.md / PORT_STATUS r2） | 复核结果 | 证据 |
|---|---|---|
| shooter `currentGunItem` null 归一化（22 处） | **在且正确** | `ShooterDataHolder.java:174-185`（`getCurrentGunItem()` 把 supplier=null / get()=null / getItem()=null / stackSize<=0 归一成 null）；全仓 `currentGunItem\.get\(\)` **0 命中**；`shooter/**` 各调用点均为「局部变量+判空」（如 `LivingEntityAim.java:100`、`LivingEntityShoot.java:83-86`、`LivingEntityReload.java:53`） |
| 子弹不移动（补 `setPosition`） | **在且正确** | `EntityKineticBullet.java:283-293`：`onBulletTick()`→旋转→`setPosition(pos+motion)`→摩擦/重力，顺序与上游 `tick()`（`EntityKineticBullet.java:245-292`）一致 |
| 击杀事件用 `!isEntityAlive()` | **在且正确** | `EntityKineticBullet.java:529-588`；`EntityKillByGunEvent`/`ServerMessageGunKill` 分支保留 |
| 背包枪热量冷却判定反转（G2） | **在且正确** | `ModernKineticGunItem.java:140-165`：`stack == player.getHeldItem()` 时 return（手持由 `LivingEntityHeat#tickHeat` 负责）；与上游 `ModernKineticGunItem.java:69-73` 同型 |
| trackingRange 单位（G5） | **在且正确** | `init/ModEntities.java:32-37,40-56`：bullet `5*16=80`、minecart `8*16=128`、bullet updateFrequency 5 / minecart 3、速度同步 true |
| **全自动按住连发**（本轮重点怀疑区） | **成立，且上游根本不靠 `onUseTick`** | 上游 1.20 的 TACZ **不实现** `Item#use`/`onUseTick`/`getUseDuration`（全仓 grep 0 命中）——开火是：`ShootKey` 每客户端 tick 轮询鼠标 → `IClientPlayerGunOperator#shoot()` → `LocalPlayerShoot` 用 `ScheduledExecutorService` 按 `shootInterval` 周期发 `ClientMessagePlayerShoot`。本工程逐行等价：`ShootKey.java:46-90`（AUTO/BURST-continuous 判据、`lastTimeShootSuccess` 半自动单发、松开重置）、`LocalPlayerShoot.java:283-392`（executor、`delay=coolDown`、`period=BURST?burstShootInterval:1ms`、`maxCount=min(ammo, burstCount)`、epoch/state-lock 守卫）、`ShootKey` 已注册 FML 总线（`ClientProxy.java:192-194`）。服务端射速/时间戳校验 `LivingEntityShoot.java:96-122`（SERVER_SHOOT_COOLDOWN_V / SERVER_SHOOT_NETWORK_V）与上游同型，窗口仅 tickTime 固定 50ms（已登记降级） |
| 换弹状态机（start→FEEDING→FINISHING→setAmmoCount） | **在且正确** | `LivingEntityReload.java:51-166` ↔ 上游 `:33-134` 逐行同构；`ModernKineticGunItem.java:494-587`（`defaultTickReload`/`defaultReloadFinishing`，毫秒 vs 上游同口径 `×1000`）；`IGunOperator#getSynReloadState()` null 归一在 `mixin/common/LivingEntityMixin.java:306-315`；客户端 `LocalPlayerReload.java` ↔ 上游 `:26-125` 同构 |
| bolt（拉栓）与 reload 互斥 | **在且正确** | `LivingEntityBolt.java:43-101`（shoot cool down / reloading / drawing / isBolting 四道门）；`LivingEntityReload.java:79-82`（isBolting 门）；客户端 `LocalPlayerBolt.java:40-115` 同构 |
| `GunAnimationStateContext` 空值防护（r2 P0） | **在且正确** | `GunAnimationStateContext.java:68-77`（`iGun && display && gunData` 三守卫）、`:118-136/143-162/232-279/286-299/439-442/473-509/528-541/590-604` 全部判空 |
| 子弹 spawn 数据字段序 | **在且正确** | `EntityKineticBullet.java:802-871` 写/读 20 字段一一对应（pitch/yaw/motion/owner/ammoId/gravity/explosion/ignite/radius/damage/life/speed/friction/pierce/tracer/gunId/gunDisplayId） |
| 弹药扣减 `reduceAmmoOnce`（三段 bolt 分支） | **在且正确** | `ModernKineticGunScriptAPI.java:341-396` ↔ 上游 `:269-329` 逐支线一致（MANUAL→膛内；CLOSED→弹匣优先/膛内兜底；OPEN→弹匣/背包） |
| 服务端「客户端开火」入口 | **在且正确** | `ClientMessagePlayerShoot.java:52-65`（Server 侧 `NetworkTaskQueue.enqueue` + `IGunOperator.shoot(supplier pitch/yaw, timestamp, chargeProgress)`）↔ 上游 `:38-50`；`NetworkTaskQueue` 已挂 FML 总线（`CommonProxy.java:124-129`） |

> 顺带确认（非本作用域，但影响开火链路）：`EventHandlers.registerAll()` 已包含 `SyncBaseTimestamp.register()`（`event/EventHandlers.java:49`），
> 玩家进世界会收到 `ServerMessageSyncBaseTimestamp`（`event/SyncBaseTimestamp.java:35-44`）→ 客户端 `clientBaseTimestamp`、
> 服务端 `ShooterDataHolder.baseTimestamp` 双向对齐（`client/ClientProxy.java:415-426`）。

---

## 二、未修问题（只报告）

### P1

| # | 文件:行 | 现象 | 判定依据（上游 file:line） | 建议修法 | 置信度 |
|---|---|---|---|---|---|
| U1 | `event/KnockbackChange.java:36-45`、`api/entity/KnockBackModifier.java`、`entity/EntityKineticBullet.java:509-516` | **枪械击退数据完全不生效**：`EntityKineticBullet#onHitEntity` 先 `KnockBackModifier#setKnockBackStrength(knockback)` 再 `tacAttackEntity`，但该值**无人消费**（`KnockbackChange.modifyStrength` 全仓 0 调用者；`EventHandlers.java:54-55` 的 `KnockbackChange.register()` 是空实现）→ 1.7.10 `EntityLivingBase#attackEntityFrom` 内部按原版算击退，`BulletData.knockback` / `GunProperties.KNOCKBACK` / 配件击退全被丢弃 | 上游 `event/KnockbackChange.java:12-16` 监听 `LivingKnockBackEvent` 并 `event.setStrength(...)`；上游 `entity/EntityKineticBullet.java:448-458`（`setKnockBackStrength` → `tacAttackEntity` → `resetKnockBackStrength`） | **mixin 作用域**：`@Mixin(EntityLivingBase)` 在 `attackEntityFrom` 计算击退强度处注入，调用 `KnockbackChange.modifyStrength(this, originalStrength)`；1.7.10 `EntityLivingBase#attackEntityFrom` 里击退在 `if (source.isProjectile())`/`knockbackResistance` 计算之后原地 `addVelocity`，需找到精确注入点（建议 `@ModifyVariable`/`@Redirect addVelocity`） | 高（静态确认 0 调用者） |
| U2 | `client/input/GunInputHandler.java:153-184`（F1 的**残留副作用**）+ `Minecraft.java:2005-2058` + `PlayerControllerMP.java:186-301` + `event/PreventGunClick.java:30-40` | **持枪左键点方块 → 客户端幽灵方块/不同步**：F1 恢复原版 attack 键位后，`Minecraft#func_147116_af`/`func_147115_a` 正常执行客户端挖掘预测（`PlayerControllerMP#clickBlock`/`onPlayerDamageBlock` 累计后用 `onPlayerDestroyBlock` 本地 `setBlockToAir`），而服务端 `PlayerInteractEvent.Action.LEFT_CLICK_BLOCK` **只在** `ItemInWorldManager.java:157`（服务端）触发并被 `PreventGunClick` 取消 → 服务端保留方块，客户端已本地移除（创造模式更明显）。**注意这是 P0 修复后「浮出水面」的既有差异，不是 F1 引入的新代码错误**：不修 F1 则原版左/右键整体不可用，显然更糟 | 上游 `client/event/ClientPreventGunClick.java:21-41`（客户端 `InputEvent.InteractionKeyMappingTriggered` 取消 + `setSwingHand(false)`）+ 上游 1.20 的 `PlayerInteractEvent.LeftClickBlock` **客户端也会发**（`Minecraft#startAttack`/`MultiPlayerGameMode#continueDestroyBlock`），因此上游客户端不会开始挖掘；1.7.10 的 `PlayerInteractEvent` 仅服务端 post（`ItemInWorldManager.java:157` 是全仓唯一 `LEFT_CLICK_BLOCK` 派发点） | **mixin 作用域**：`@Mixin(Minecraft)` 在 `func_147116_af`（点击）与 `func_147115_a`（持续挖掘）HEAD 判断 `IGun.mainHandHoldGun(thePlayer)` 直接 `return`；等价于上游客户端拦截。或 `@Mixin(PlayerControllerMP)` 在 `clickBlock`/`onPlayerDamageBlock` HEAD 取消 | 中高（全部为 1.7.10 源码路径推导；需实机确认幽灵方块，建议「持枪左键点方块」用例） |

### P2

| # | 文件:行 | 现象 | 判定依据 | 建议修法 | 置信度 |
|---|---|---|---|---|---|
| U3 | `util/time/GunTime.java:37-38,47-49,71-74` | **「暂停停表」闸门从未注入**：`setTickGate(...)` 全仓 0 调用者（`grep setTickGate` 仅类内定义），`shouldTick()` 恒 true。后果：①单机暂停时 `LocalPlayerShoot` 已排队的开火任务/burst 仍继续（`LocalPlayerShoot.java:319/359` 的 `shouldTick` 守卫失效）；②`SecondOrderDynamics`（`util/math/SecondOrderDynamics.java:86`）与 Lua 状态机时钟继续积分；③`client/animation/statemachine/GunAnimationStateContext.java:173-181` 的 javadoc 明写「单人游戏暂停时此时间不前进」，与实现矛盾 | 上游 `GunTime` 用 simplebedrockmodel 的 `AnimationClocks.client()`（暂停即停）；本类注释自己承认「默认恒 true 是降级点」 | 在客户端初/每 tick 注入：`GunTime.setTickGate(() -> mc.theWorld != null && !mc.isGamePaused)`（或挂在 `ClientTickHelper`）；dedicated server 保持默认 true | 高（0 调用者） |
| U4 | `init/CommonRegistry.java:165-167` | `MinecraftForge.EVENT_BUS.register(CommonRegistry.class)`：1.7.10 `EventBus#register(Object)` 用 `target.getClass()` 反射 → 传 Class 只会扫 `java.lang.Class` 的方法，**不注册任何东西**；且 `onEntityConstructing` 是 `public static`（本项目自身铁律要求实例方法）。当前**功能未受影响**（BULLET_RESISTANCE 由 `init/ModAttributes.java:40-42,52-64` 的实例 `Handler` 挂载），但该行是死代码且与团队文档「传 Class 不会注册」自相矛盾，易误导后续维护 | 1.7.10 `cpw/mods/fml/common/eventhandler/EventBus.java#register`；`client/init/ClientSetupEvent.java:99-102`（本项目自己的说明） | 删除该行，或改成 `MinecraftForge.EVENT_BUS.register(new CommonRegistry.Handler())` 并与 ModAttributes 合并（init 作用域） | 高 |
| U5 | `client/animation/statemachine/GunAnimationStateContext.java:590-604` | `setCurrentGunItem()` 在 `iGun == null`（切到非枪/空手）时**不清空** `display`/`gunData`（上游同构，但 1.7.10「索引缺失/空手」是常态）：之后 `isOverHeat`/`getHeatProgress`/`shouldSlide`/`popShellFrom`/`getStateMachineParams` 这些**直接读 `display`/`gunData`（不经过 `processGunData`）** 的方法会拿到上一把枪的陈旧 display/gunData + `currentGunItem == null` → 潜在 NPE 或错误状态（如空手仍 `shouldSlide()==true`、旧 display 抛壳） | 上游 `GunAnimationStateContext.java:460-471`（同样只在 iGun!=null 时赋值；上游 display/index 恒非 null） | 在 `iGun == null` 分支显式 `display = null; gunData = null; nbtUtil = null;`；对应 getter 已判空故安全 | 中（需 Lua 状态机命中该路径才会触发） |

### P3

| # | 文件:行 | 现象 | 判定依据 | 建议修法 | 置信度 |
|---|---|---|---|---|---|
| U6 | `client/input/GunInputHandler.java:225-255` | 1) 每次「新按下」只派发**第一个**匹配动作后就 `break`（`03-client-ui.md` R5 已提，本轮确认仍在）；2) `PRESS_BINDINGS` 顺序里 `MeleeKey`(V) 在 `ZoomKey`(V) 之前且两者**默认同键 V** ⇒ 按 V 永远只触发近战，**变焦键实际不可用**（上游 1.20 两个 KeyMapping 同键时两个 handler 都会触发） | 上游 `GunInputHandler.java:35-...`（`PRESS_BINDINGS` 同序，但 1.20 `InputEvent.Key` 对每个按下 keyCode 通知所有 handler）；1.7.10 无输入事件，只有轮询 | 拆成「先判 GUI 开关」+「遍历全部新按下 binding 统一派发」，不要因一次命中就中断循环；同时给 ZoomKey 换默认键或明确文档化 V 冲突 | 高 |
| U7 | `api/item/nbt/GunItemDataAccessor.java:348-376`（跨作用域，只读核对） | `getAimingZoom` 对 `zooms[zoomNumber % zooms.length]`：若枪包 JSON 给了**空数组** `"zoom": []` → `% 0` 除零 `ArithmeticException`（或 AIOOBE），崩在 FOV/Rangefinder 每帧路径。上游同构风险，但 1.7.10 枪包 JSON 由第三方提供、畸形概率更高 | 上游 `GunItemDataAccessor.java` 同写法；本工程 `RangefinderManager.java:297`、`CameraSetupEvent.java:266/374` 都是每帧调用 | `if (zooms != null && zooms.length > 0)` 再取模（api 作用域，交 api 负责人） | 中 |
| U8 | `network/message/event/ServerMessageGunKill.java`（客户端 `onKill`）、`ServerMessageGunHurt` | `bulletId`/`killEntityId` 在客户端 `getEntityById` 可能为 null（子弹生命周期短、目标已卸载/超出 tracking），事件里 `bullet`（非空注解但可为 null）、`killedEntity` 为 null 后照常 post；消费方（`ClientHitMark`、成就/统计监听）若解引用 `getBullet()`/`getKilledEntity()` 会 NPE。上游同构（`ServerMessageGunKill.java` 同样传 null），本工程 `ClientHitMark` 已判空（`client/event/ClientHitMark.java`），仅登记为对外 API 语义 | 上游 `ServerMessageGunKill.java#onKill` 同写法；`api/event/common/EntityKillByGunEvent.java:31-49`（`bullet` 无 @Nullable） | 事件类给 `getBullet()` 加 `@Nullable` 并在 `ClientHitMark`/KubeJS 桥接处显式判空（api 作用域） | 中 |

---

## 三、生命周期映射表（上游 1.20.1 方法 → 1.7.10 方法 → 是否语义等价）

### 3.1 `ModernKineticGunItem`（上游继承 `AbstractGunItem extends Item`）

| 上游（1.20.1） | 1.7.10 落点 | 语义等价 | 说明 |
|---|---|---|---|
| `inventoryTick(stack, level, entity, slot, selected)` | `item/ModernKineticGunItem.java:139-165 onUpdate(stack, world, entity, slot, selected)` | ✅ | 1.7.10 仅玩家背包会被调用（`EntityPlayer#onLivingUpdate → InventoryPlayer#decrementAnimations → ItemStack#updateAnimation → Item#onUpdate`，`EntityPlayer.java:610`、`InventoryPlayer.java:341-358`、`ItemStack.java:454-462`）；与上游「只有玩家有 inventory.tick」等价；逻辑同为「跳过手持 → 背包冷却」，`world.isRemote` 替 `level.isClientSide` |
| `use()` / `onUseTick()` / `finishUsingItem()` / `getUseDuration()` | **不存在（上游也不实现）** | ✅ | 全仓 grep 上游 0 命中：TACZ 的开火不依赖原版 use 周期，见 §1.2「全自动按住连发」。1.7.10 因此**无** `EnumAction.BOW` 拉弓副作用（未覆写 `getItemUseAction` 即默认 `EnumAction.NONE`） |
| `releaseUsing` | 不覆写 | ✅ | 上游无 |
| `onEntitySwing(stack, entity)` | `api/item/gun/AbstractGunItem.java:511-514 onEntitySwing(entityLiving, stack)` → 返回 `true` | ✅ | 1.7.10 `EntityLivingBase#swingItem` 在 `EntityLivingBase.java:1329` 判 `item.onEntitySwing(...)` 为 true 即 return（阻止挥臂）；参数顺序与上游相反，语义一致 |
| `hitEntity` | 不覆写 | ✅ | 上游也没有近战 `destroyBlock`/`hurtEnemy` 覆写（近战走独立 `GunMeleeEvent` + `melee()`） |
| `getName` / `getTooltipImage` / `initializeClient` / `getMaxStackSize` | `getItemStackDisplayName`（api 侧）、tooltip 事件、`ClientSetupEvent#registerItemRenderers`、构造器 `setMaxStackSize(1)` | ⚠️ 落点不同 | 1.7.10 无 BEWLR/IClientItemExtensions；渲染走 `IItemRenderer`（`client/ClientSetupEvent.java:226-279`），图标用 `minecraft:bow_standby` 兜底（F5 已修） |
| `fireSelect` / `startReload` / `tickReload` / `interruptReload` / `shoot` / `startBolt` / `tickBolt` / `tickHeat` / `melee` / `unloadAmmo` / `doBulletSpread` / `modifyProperty` | 同名方法逐行翻译（`ModernKineticGunItem.java:167-587`） | ✅ | 见 §1.2 复核 |
| `OnUseItem…`（`Item#use` 交互） | 不覆写 → 原版默认 PASS | ✅ | 右侧交互由 `client/event/ClientPreventGunClick` 取消；1.7.10 侧 `Minecraft.java:1528/1556` 的 `PlayerInteractEvent` 是等价拦截点 |

### 3.2 `LivingEntity` 侧（`mixin/common/LivingEntityMixin` + `entity/shooter/**`）

| 上游 | 1.7.10 落点 | 语义等价 | 说明 |
|---|---|---|---|
| `LivingEntity#tick` RETURN 注入 | `mixin/common/LivingEntityMixin.java:298-338`（`onLivingUpdate` RETURN） | ⚠️ 方法换名，等价 | 1.7.10 每 tick 逻辑在 `onLivingUpdate`；`onLivingUpdate` 单 return（无多重注入）；逻辑顺序与上游逐行一致（reload→aim→sprint→crawl→bolt→melee→speed→heat→同步 8 个 key） |
| `Capability` 挂 `IGunOperator`/`KnockBackModifier` | `@Mixin(EntityLivingBase) implements` + 实例字段（同上游 mixin 形态） | ✅ | 1.7.10 无 capability，改 mixin 实现接口（`IClientPlayerGunOperator#fromLocalPlayer` 的裸强转仍成立） |
| `ModSyncedEntityData`（`AttachCapabilitiesEvent` + `DataHolderCapabilityProvider`） | `entity/sync/**`：静态 `WeakHashMap<Entity, DataHolder>`（`SyncedEntityData.java:66-75,195-214`） | ⚠️ 实现不同，语义等价 | 服务端写→`dirtyEntities`→`SyncedEntityDataEvent#onServerTick` 按 `syncMode` 发 `ServerMessageUpdateEntityData`→客户端 `set`（`ServerMessageUpdateEntityData.java:68-78`）；startTracking/join 全量同步、clone 拷贝、登出由 WeakHashMap 自然回收。**无 `saveToFile`/persistent 的 NBT 持久化**：本工程所有 key 都是 `save=false`（`ModSyncedEntityData`），与上游一致 |
| `Item#onUpdate` 全局 tick | 仅玩家背包（见 3.1） | ✅ 同上 | |
| `Projectile#tick` 位移 | 1.7.10 `Entity#onUpdate` 不位移 → `EntityKineticBullet#onUpdate` 手动 `setPosition` | ✅ | 见 §1.2 |

### 3.3 客户端（`client/gameplay/**` ↔ 上游 `client/gameplay/**`）

| 上游类 | 1.7.10 类 | 语义等价 | 备注 |
|---|---|---|---|
| `LocalPlayerShoot`(381L) | `LocalPlayerShoot.java`(428L) | ✅ 逐行 | 仅 `submitAsync→func_152344_a`、`Pair`、`LocalPlayer→EntityPlayerSP` 替换 |
| `LocalPlayerDataHolder`(213L) | 同(234L) | ✅ 逐行 | 一字不差 |
| `LocalPlayerReload`(125L) / `LocalPlayerBolt`(99L) / `LocalPlayerAim`(115L) / `LocalPlayerDraw`(126L) / `LocalPlayerUnload`(90L) / `LocalPlayerMelee`(133L) / `LocalPlayerFireSelect`(58L) / `LocalPlayerInspect`(61L) / `LocalPlayerSprint`(35L) / `LocalPlayerCrawl`(89L) | 每个 +10~30 行 | ✅（Crawl 姿态见偏离表 D3） | 差异仅平台类型与 TODO(port) 注释 |
| `LocalPlayerMixin`(186L) | 同(282L) | ✅ | 1.7.10 无 `LocalPlayer#respawn`/`aiStep`，改 `onLivingUpdate` HEAD + `@Redirect setSprinting`；**respawn 注入缺失已在 javadoc 说明理由**（1.7.10 重连会新建 `EntityPlayerSP`，状态天然重置） |

### 3.4 视角 / 相机

| 上游事件 | 1.7.10 落点 | 语义等价 | 说明 |
|---|---|---|---|
| `ViewportEvent.ComputeFov`（世界） | `FOVUpdateEvent`（`FOVUpdateEvent` 的 `newfov` 是**倍率**，最终角度 = `fovSetting × 倍率`，`EntityRenderer.java:486-487`） | ✅ 做了量纲换算 | `client/event/CameraSetupEvent.java:246-276`：`currentFovDegrees = newfov*fovSetting` → `magnificationToFov` → `event.newfov = result/fovSetting` |
| `ViewportEvent.ComputeCameraAngles`（相机动画/后坐力） | `TickEvent.RenderTickEvent` START/END + 直接改 `renderViewEntity.rotationPitch/Yaw`（`CameraSetupEvent.java:164-211,404-428`） | ⚠️ 等价性待实机 | 1.7.10 无相机角度事件；每帧 START 施加、END 还原相机动画偏移（`cameraAnimationOrigin*`），后坐力按帧增量持久施加到玩家朝向（与上游 `player.setXRot` 分支同语义）；`roll` 走 `CameraStateCache` + `GameRendererMixin` 的 `glRotatef` 通道 |
| `ComputeFovModifierEvent`（移动属性 FOV） | `FOVUpdateEvent` + `priority=HIGHEST`（`CameraSetupEvent.java:450-465`） | ✅ | 用优先级复刻「modifier 事件早于 fov 事件」 |
| `MouseHandler#turn`（开镜灵敏度） | `@Mixin(Entity) setAngles` 的 `@ModifyVariable`（`mixin/client/MouseHandlerMixin`） | ✅（待实机） | `EntityRenderer#updateCameraAndRender → thePlayer.setAngles`（`EntityRenderer.java:1041-1066`）——1.7.10 的鼠标视角入口 |
| `KeyMapping` / `InputEvent` | `KeyBinding` + `TickEvent.ClientTickEvent` 轮询（`client/input/**`） | ⚠️ 有平台差异 | 见 F1（IntHashMap 单槽）与 U6（一次按下只派发一个动作）；`KeyConflictContext/KeyModifier` 无对应物（`MODIFIER_ALT` 手工判定） |

---

## 四、与上游的偏离登记（非 bug；本轮新增/复核的条目）

| # | 项 | 位置 | 说明 |
|---|---|---|---|
| D1 | 1.7.10 无 `tacz:bullet_ignore` 方块标签 | `util/block/BlockRayTrace.java:87-106` | 铁栏杆/栅栏/栅栏门/树叶按原版类型硬编码（内容与上游 tag 一致）；其它模组方块走 `registerIgnoreBlock` 扩展点（当前 0 调用者）→ 模组方块穿透需自行注册 |
| D2 | 伤害类型从数据驱动退化为 `BulletDamageSource` 子类 | `init/ModDamageTypes.java` + F2 | 语义（无视护甲/绝对伤害/死亡消息）已对齐；`BULLET_VOID`/`PRETEND_MELEE_DAMAGE_ON` 无对应实体的标签还是硬编码（末影人魔法伤害）；伤害类型 id 仅用于日志/成就匹配 |
| D3 | 无 `Pose`/旁观者/鞘翅/三叉戟/游泳 适配 | `entity/shooter/LivingEntityCrawl.java:22-29,44-54`、`client/gameplay/LocalPlayerCrawl.java:77-117` | 趴下只维护 `data.isCrawling` + `EntityStateHelper` 姿态覆盖；`jumping`/`isSwimming`/fall-flying 检查缺省；两端（客户端/服务端）语义保持一致 |
| D4 | 服务端射击时间戳窗口按固定 tickTime=50ms | `entity/shooter/LivingEntityShoot.java:107-122` | 上游用 `MinecraftServer#tickTimes` 动态放宽（±300ms + 2×tickTime）；1.7.10 无每 tick 耗时 → 固定 `300+100`ms 窗口，只会更严格、不会放过异常包 |
| D5 | 子弹初始位置不做 `xOld` 插值 | `entity/EntityKineticBullet.java:244` | 上游 `xOld + (x-xOld)/2 + eyeHeight`；1.7.10 用当前 pos + eyeHeight - 0.1。影响：快速平移射击时枪口位置略偏（视觉/命中极边缘），不崩 |
| D6 | 客户端 `attack/use` 键位状态 | `client/input/GunInputHandler.java:153-184`（F1） | 1.7.10 `KeyBinding` 静态 `hash` 单槽 + `Minecraft#runTick` 按 keyCode 派发是平台差异；本工程改用「TACZ 绑定不进 `keybindArray`」绕开。复现要点写入复查清单 |
| D7 | 换弹打断对「无 Lua 脚本的枪」只在客户端生效 | `item/ModernKineticGunItem.java:252-262`（`interruptReload` 仅调 `interrupt_reload` 脚本）、`entity/shooter/LivingEntityReload.java:117-131` | 上游同构（无默认中断实现）：服务端换弹会继续走完，客户端播取消动画；不是移植缺陷，登记避免误判 |
| D8 | `GunTime#shouldTick` 默认恒 true | `util/time/GunTime.java:37-49`（U3） | 上游 `AnimationClocks.client()` 暂停即停；1.7.10 缺注入点（属实现缺口，已列 U3） |
| D9 | actionbar → 聊天栏 | `TargetMinecart.java:92-97`、`LocalPlayerUnload.java:74-81`、`ClientHitMark` | `displayClientMessage(..., true)` 无对应物，全部降级为聊天消息（多处一致） |
| D10 | `ShooterDataHolder#baseTimestamp` 握手时序 | `event/SyncBaseTimestamp.java` + `network/message/*SyncBaseTimestamp` | 单向延迟/RTT 会造成 ±RTT 级时间戳偏差（上游同样）；1.7.10 `NetworkTaskQueue` 每 tick 排空，额外引入 ≤1 tick（50ms）延迟（仍在 ±300ms 窗口内） |
| D11 | 多段实体只识别原版 `EntityDragonPart` | `entity/EntityKineticBullet.java:752-777`（F8） | 1.7.10 无 Forge `PartEntity`/`PartEntity#getParent`：`EntityDragonPart.entityDragonObj instanceof Entity` 时映射 core，其它多段实现（自定义 `IEntityMultiPart`）退化为「part 即 core」；另保留 1.7.10 原版语义：**非玩家来源**子弹命中龙部件时 `attackEntityFromPart` 不扣血（`EntityDragon.java:557` 只认 `EntityPlayer` 或爆炸，上游 1.20 无此限制）——这是 1.7.10 平台行为，不额外绕过 |

---

## 五、复查清单

### 5.1 本轮改动文件（6 Java + 1 报告，全部 `lint_files(error)` = 0；**未跑 gradle**）

- [x] `src/main/java/com/tacz/guns/client/input/GunInputHandler.java`（F1，P0）
- [x] `src/main/java/com/tacz/guns/entity/EntityKineticBullet.java`（F2/F3/F8）
- [x] `src/main/java/com/tacz/guns/entity/TargetMinecart.java`（F4）
- [x] `src/main/java/com/tacz/guns/client/animation/screen/RefitTransform.java`（F5）
- [x] `src/main/java/com/tacz/guns/item/ModernKineticGunScriptAPI.java`（F6）
- [x] `src/main/java/com/tacz/guns/entity/sync/ModSerializers.java`（F7）
- [x] `docs/review/r2/03-gun-logic.md`（本报告）

### 5.2 构建期需 Lead 复核（本 agent 禁止 gradle）

1. `GunInputHandler` 的反射补丁：`KeyBinding.class.getDeclaredFields()` 里唯一「静态 + `List` 类型」字段 = `keybindArray`（1.7.10 反编译源码 `KeyBinding.java:16`）；`resetKeyBindingArrayAndHash()` 是 public static（`:65`）。**生产环境 reobf 后字段名是 SRG、但字段类型不变**，因此按类型定位是安全的；`resetKeyBindingArrayAndHash` 的调用由 reobf 自动映射（与全仓其它 MC 方法调用同机制）。
2. `ModernKineticGunScriptAPI` 的 `shooter.isEntityAlive()`、`EntityKineticBullet` 的 `ModDamageTypes.Sources.bullet` / `BlockRayTrace` 调用都是编译期解析（`init`/`util` 都在 common 侧，无物理侧问题）。
3. `RefitTransform` 现在会在**首次打开改装界面**时把单例注册到 FML 总线；若 Lead 更希望「客户端 init 统一注册」，可在 `ClientProxy#init` 调一次 `RefitTransform.register()`（本轮为避免跨作用域未改 `ClientProxy`）。

### 5.3 实机回归用例（按优先级）

1. **（F1，最高）** 空手/持普通物品：左键挖方块、左键打怪、右键开箱子/吃东西/放方块/点门 → 全部应正常；再持枪：左键应只开火（不挖方块）。
2. **（U2 已知残留）** 持枪对着方块按住左键 1~2 秒：服务端方块不应消失；若客户端出现「方块先消失又回来」（幽灵方块），即 U2 复现 → 需 mixin 补客户端拦截。
3. **（F2/F3）** 穿甲弹打高护甲目标伤害应更高；防弹属性（若有内容挂 `BULLET_RESISTANCE`）应减伤；子弹穿过铁栏杆/栅栏/树叶/高草；死亡消息应为「被 XX 用枪击倒」。
4. **（F5）** 打开改装界面 → 点配件槽切换视角 → 枪应平滑旋转/转场（此前恒无动画）。
5. **（F4）** 用物品放置标靶车，射其上半部应命中（0.75×2.4 命中盒）。
6. **（F8）** 用枪打末影龙（头/身体/翅膀）：应出现伤害数字与受击/击杀特效（此前事件全丢）；一次扣血应包含穿甲段（此前穿甲段被龙本体无敌帧吞）；打非头部部件按 1.7.10 原版 /4+1 减伤。
7. 全自动按住左键持续连发、松开立刻停；半自动需重新点击；BURST 3 发一串；栓动打一发需拉栓；霰弹多弹丸；开镜 FOV/灵敏度；后坐力抬枪。
8. 换弹：满仓/空仓/战术换弹、切枪/冲刺/开火打断、拉栓与换弹互斥。
9. 多人服：加入后立刻开火（baseTimestamp 握手中）、/tacz reload 后继续开火。

### 5.4 已知未验证（本轮无法在静态审查中闭环）

- 相机后坐力/相机动画/开镜 FOV 的**数值观感**（`CameraSetupEvent` 数学已逐行核对，但 1.7.10 的「每帧改玩家朝向 + 还原」与上游「改相机」在平滑/手感上可能不同）——需实机。
- `SecondOrderDynamics`/`GunTime` 暂停闸门（U3）的体验影响。
- `EntityKineticBullet` 客户端弹道与命中特效（`HitBlockEffectHandler`/`AmmoParticleSpawner`，client 作用域）——只核对了消息字段顺序与调用链，未看渲染细节。
- Lua 脚本枪（若枪包含脚本）：`GunAnimationStateContext` 的 605 行 getter 已加空守卫，但**实际脚本执行**（`GunScriptGuard`/`safeAsyncTask`）未实机跑过。

---

# r3 逻辑/输入收尾（task-13）

> **作用域**：`util/time/GunTime.java`、`loot/**`、`event/**`、`client/input/**`、本报告。
> `client/animation/**` 本轮归 render-material（未动）；`entity/**`、`item/**`、`client/gameplay/**` 未动。
> **自查**：每个改动文件 `lint_files(min_severity=error)` / `get_file_problems(errorsOnly)` 全 0 error；
> **未运行 gradle**；`GunTime` 的暂停/单调性用独立 `javac + java` harness 验证（见 R3-1）。
> **对 r2 结论的更新**：r2 §二 `U1`（击退链 0 调用者）→ **已闭环**（mixin-fixer，见 R3-4）；`U3`（`GunTime` 闸门）
> → **已闭环**（R3-1）；`U5`~`U8` 与本域无关，保持 r2 状态。

## R3-1 【P2】`GunTime.setTickGate` 0 调用者 → 单机暂停时开火队列/动画时钟仍走 —— 已修

**现象**：r2 时 `GunTime.setTickGate(...)` 全仓 0 调用者 → `shouldTick()` 恒 `true`：

1. 单机按 ESC 暂停后，`LocalPlayerShoot` 已排队的连发/burst 任务仍在跑（其守卫失效）——
   依据：`client/gameplay/LocalPlayerShoot.java:319-321`（`if (!GunTime.shouldTick()) return;`）、
   `:356-362`（主线程回调里的同一守卫）；
2. `SecondOrderDynamics`/动画时间戳继续积分（`util/math/SecondOrderDynamics.java:86`）；
3. `client/animation/statemachine/GunAnimationStateContext.java:173-181` 的 javadoc 明写
   「单人游戏暂停时此时间不前进」，但 `:180` 直接返回 `GunTime.nowMillis()` → 文档与实现矛盾（上游
   `AnimationClocks.client()` 是**时钟本身**停走，`tmp/TACZ-1.20.1/.../util/time/GunTime.java:13,30`）。

**修法（`util/time/GunTime.java`）**：

1. 新增**幂等**接线入口 `installClientTickGate(BooleanSupplier)`（`GunTime.java:94-100`）+
   `isClientTickGateInstalled()` 诊断用（`:102-105`）；专用服务端永不调用 → 保持默认「恒走」。
2. **把「暂停停表」做到 `nowNanos()` 里**（只停 `shouldTick` 消费者不够，仍会让动画/网络时间戳跳过暂停时长）：
   - 新增 `pausedNanos`（累计暂停时长，`:42-43`）、`pauseStartNanos`（当前暂停段起点，`:45`）、
     `lastReturnedNanos`（单调钳制，`:47`）；
   - 闸门为 false 期间冻结在暂停起点值；恢复时把暂停时长累加进 `pausedNanos`，时间从冻结值继续
     （`nowNanos()` `:117-143`）；
   - `resetEpoch()` 同步折算暂停累计（暂停中重置 → 值 0 且保持冻结，`:167-180`）；
   - 默认闸门（`() -> true`）下行为与旧实现逐字等价，**无回归面**。
3. **接线（已在作用域内接好，无需 Lead 再动手）**：`client/input/GunInputHandler.java#registerKeyMappings()`
   末尾调用
   `GunTime.installClientTickGate(() -> !Minecraft.getMinecraft().isGamePaused());
   （该方法正是 `client/init/ClientSetupEvent.java:160` 的 `step("GunInputHandler.registerKeyMappings", ...)`，
   客户端初始化期执行）。`Minecraft#isGamePaused` 语义见 `Minecraft.java:1117`（单人 + 会暂停的 screen + 未开 LAN），
   多人恒 false → 多人/专用服务端不受影响。
   **若 Lead 更希望统一放在 `client/init`**，加这一行即可（与现有调用幂等，重复无害）：
   ```java
   step("GunTime.installClientTickGate", () -> GunTime.installClientTickGate(() -> !Minecraft.getMinecraft().isGamePaused()));
   ```

**验证**：

- 独立 harness（把 `GunTime.java` 拷到临时目录 + `javac`/`java`，不跑 gradle）：
  `normal advance 54ms / frozen during pause (100065==100065) / advance after resume 62ms / pause excluded total=126ms /
  frozen after reset 100000==100000 / resume after reset advances 60ms / 200000 次 nowNanos 严格单调` —— 全过。
- **实机**：单机进游戏 → 开火中途按 ESC 暂停 5 秒 → 恢复：连发不会在暂停期间「欠账连喷」，动画/准星动画不会瞬间跳完；
  进服务器（多人）开枪不受影响。

**已知差异/风险**：`nowNanos()` 现在每次调用取 `synchronized (GunTime.class)`（保证暂停/恢复边界状态一致），
渲染路径调用频繁但临界区极短（harness 200k 次无异常）；专用服务端不安装闸门 → 完全不进暂停分支。

## R3-2 【P1】枪包战利品注入 0 调用者 → 默认「奖励箱送 Taurus 943」静默失效 —— 已修

**现象**：`loot/LootTableInjectorModifier#inject(ResourceLocation, List)` r3 前**全仓 0 调用者**
（`init/ModLootModifiers#register()` 空实现，`init/ModLootModifiers.java:17-20`），而数据侧
`resource/manager/LootInjectionManager#reload()` 一直正常解析并保存（`:41-67`）→ 读完没人用：
默认枪包 `.../tacz_default_gun/data/tacz/tacz_loot_injectors/spawn_bonus_chest_taurus943.json`
（`loot_tables: ["minecraft:chests/spawn_bonus_chest"]`，1 把 `tacz:modern_kinetic_gun` NBT
`GunId=tacz:taurus943` + 38~45 发 `tacz:22wmr`）静默失效，任何枪包自定义战利品注入也无日志失败。

**1.7.10 可用钩子调研**（上游是 Forge global loot modifier，1.7.10 无 `LootTable`/`LootContext`）：

| 1.20 表场景 | 1.7.10 等价钩子 | 依据 |
|---|---|---|
| `minecraft:chests/*` | `ChestGenHooks` 候选池（箱子生成时读 `getItems/getCount`） | `WorldServer.java:821`（奖励箱）、`:809`、`WorldGenDungeons.java:133`、`ChestGenHooks.java:51-60,137-141` |
| `minecraft:entities/*` | `LivingDropsEvent`（服务端；`PlayerDropsEvent` 是其子类） | `LivingDropsEvent.java:31-47`、`ForgeHooks.onLivingDrops` |
| `minecraft:blocks/*` | `BlockEvent.HarvestDropsEvent`（服务端；`Block#dropBlockAsItem` 路径） | `BlockEvent.java:49-65`、`Block.java:806`（`fireBlockHarvesting`） |
| **新世界奖励箱**（默认包那一条） | `WorldEvent.CreateSpawnPosition` —— 在 `createBonusChest` **之前**触发 | `WorldEvent.java:124-132`；触发点 `WorldServer.java:772` vs 奖励箱 `:809` |
| `gameplay/fishing` | **无**（1.7.10 无对应事件） | 见类注释「已知不支持」 |
| `conditions` / `empty`/`loot_table`/`tag` entry | **无**（1.7.10 无战利品表体系） | `loot/LootTableInjectorModifier` 类注释子集说明 |

**修法**：新增 `event/LootInjectionEvent.java`（薄事件类，~360 行含注释），并在
`event/EventHandlers.java:55-58` 注册；`loot/LootTableInjectorModifier` 增加
`inject(List<LootTableInjection>, List<ItemStack>)` 重载（原 `inject(ResourceLocation, List)` 保留、
改为查管理器后委托，签名不变）：

1. **箱子**：把 1.20 箱子表 id 映射到 1.7.10 的 10 个 `ChestGenHooks` 分类（含 `village_blacksmith` 旧 id 兼容，
   共 11 条映射，见 `LootInjectionEvent` 静态块），把注入物品加成 `WeightedRandomChestContent` 并
   把该分类的 `countMin/countMax` 增加注入个数；重载时先撤回（`removeItem` + 恢复 count）再重加，幂等。
   **权重推导**：设原总权重 V、原抽取数 C、注入 K 个物品栈，取权重 `w = V / C`，
   则注入条目的期望命中次数 = `(C+K) * K*w/(V+K*w) = K`（期望上与上游「追加 K 个」一致）。
2. **实体/方块**：`LivingDropsEvent` / `HarvestDropsEvent` 里反推 1.20 表 id
   （`EntityList#getEntityString` → `minecraft:entities/<name>`；`Block.blockRegistry#getNameForObject`
   → `minecraft:blocks/<name>`，与 `util/EntityUtil#getEntityId` 同规则），把物品追加到
   `event.drops`（`EntityItem` / `ItemStack`），真·追加语义。
3. **时序（关键坑）**：`CommonAssetsManager` 只在 `FMLServerStartingEvent` 装载（`CommonProxy.java:167`），
   而新世界 `CreateSpawnPosition` 发生在其**之前**（`IntegratedServer.java:96-98`：先 `loadAllWorlds`
   再 `handleServerStarting`）。因此 `LootInjectionEvent#getInjections` 在
   `CommonAssetsManager.getInstance() == null` 时用**独立** `LootInjectionManager` 兜底
   （只扫 `tacz_loot_injectors`，同一实现/同一枪包来源），正式实例可用后立即丢弃兜底并以其为准 →
   奖励箱能拿到注入项，且不会出现两份数据并存。
4. **刷新时机**：`TickEvent.ServerTickEvent(END)` 比较 `CommonAssetsManager` **实例身份**（每次 reload
   都新建实例）→ `/tacz reload`、服务端启动后自动重建注入池；退出世界清空实例时同理。无注入/无匹配时
   **零日志噪声**（仅 debug）；只有兜底加载失败才 warn 一次。

**已知降级（1.7.10 平台能力，非遗漏）**：

- 箱子是「候选池加权抽取」而非上游「结算后追加」：**期望数量**与上游一致（每注入项期望 1 个），但
  **不保证每箱必出**。默认包奖励箱（原池权重和 V=64、抽取 10 个 → 注入权重 6、抽 12 个）：
  单件出现概率 ≈ `1-(1-6/76)^12 ≈ 63%`，两件同时出现 ≈ 40%（多建几个世界即可观察）；若要提高出现率只能
  加大权重（会带来重复出现，与上游语义更远），本轮按要求保持「期望等价」。
- 方块注入只覆盖 `HarvestDropsEvent` 能看到的掉落路径（`Block#dropBlockAsItem`，`Block.java:806`），
  非该路径的自定义掉落覆盖不到。
- 模组实体/方块的 1.20 表 id 推导是 **best-effort**（`modid:entities/name` / `modid:blocks/name`），
  枪包若用非约定 id 则匹配不到（debug 无日志，属静默不匹配）。

**验证**：新建单人世界并勾选「奖励箱」→ 出生点奖励箱内应出现 `tacz:modern_kinetic_gun`
（NBT `GunId:"tacz:taurus943"`）+ `tacz:22wmr` 子弹 38~45 发；`/tacz reload` 后再开新箱子/新建世界仍生效。
无需 Lead 接线（`EventHandlers.registerAll()` 已含注册）。

## R3-3 【P2】变焦键与近战键默认同键 V —— **任务前提不成立**（不改默认键），但修复了真实分发偏离

**前提复核结论**：`「MeleeKey 在 ZoomKey 之前且一次按下只派发第一个 ⇒ 变焦键永远不可用」` **不成立**。
证据链（r3 前代码）：外层循环命中第一个按下的 binding 后调用的 `onPress()` **无 key 过滤**，它遍历
**全部** binding 并逐个判断「当前是否按住」——按 V 时 `MeleeKey` 与 `ZoomKey` 都是 V，
两个 action 都会执行（`MeleeKey.java:42-50` 仅未瞄准时近战、`ZoomKey.java:36-49` 仅瞄准时切换倍率，
二者按瞄准状态互斥）→ 瞄准中按 V 实际会走到 `ZoomKey` 并发 `ClientMessagePlayerZoom`。
且上游 1.20 本来就是**两个 handler 同键 V** 且都会收到（`tmp/.../client/input/GunInputHandler.java:80-92`
的 for 循环无 break；上游 `MeleeKey.java:31`/`ZoomKey.java:31` 同为 `GLFW_KEY_V`）——这是**有意设计**，
不是冲突，因此**不改默认键**（改键反而偏离上游）。

**但复核发现真实偏离（已修，`client/input/GunInputHandler.java`）**：旧实现「一次按下 → 派发当前所有按住的键」，
与上游「一次 key 事件 → 只派发 keyCode = 该事件的 binding（`isActiveAndMatches`）」不一致：

1. 按住某功能键（如右键瞄准）时按任意另一个键 → 会把按住的键**重复触发**（例如切换式瞄准会被意外 toggle）；
2. 同 tick 按下多个键时，`break` 导致第 2 个及之后的键的边缘状态没刷新 → 延迟 1 tick 才派发。

修法（对应上游 `onPress(Key)`）：边缘检测改为**按 keyCode** 记录原始按下状态（`LAST_KEY_DOWN`，
同一 keyCode 的多个 binding 只算一次按下；修饰键在派发阶段判断），移除 `break` 使同 tick 多键都处理；
`onPress(pressed)` 只遍历 **keyCode 相同**且修饰键生效的 binding（`UnloadKey` 早退语义保留）。
V 键行为因此从「恰好两个都跑」变成「结构上按上游语义两个都派发」，**变焦功能正常**（此前也正常，
只是机制偶然）。**r2 的 `detachMouseBindingsFromVanillaKeyBindingHash` 未动一行。**

**默认键位全量冲突检查**（TACZ 默认 vs 1.7.10 原版默认；`client/input/*Key.java`）：

| TACZ 键位 | 默认键 | 原版占用 | 冲突结论 |
|---|---|---|---|
| Shoot | 鼠标左键（-100） | 攻击/挖掘 | **有意同键**（TACZ 轮询直读硬件；原版占用由 r2 hash 修复保证共存） |
| Aim | 鼠标右键（-99） | 使用/放置/开箱 | **有意同键**（同上） |
| Crawl | C | 无 | 无冲突 |
| Reload | R | 无 | 与 Unload 同键（Alt 区分，上游同） |
| Unload | Alt+R | 无 | 同上；`onPress` 里 Unload 先于 Reload 并早退（上游同） |
| Inspect | H | 无 | 无冲突 |
| FireSelect | G | 无 | 无冲突 |
| **Melee** | **V** | 无 | 与 **Zoom** 同键（上游有意，按瞄准状态互斥） |
| **Zoom** | **V** | 无 | 同上 |
| Interact | O | 无 | 无冲突 |
| Refit | Z | 无 | 无冲突 |
| OpenConfig | 未绑定（`Keyboard.KEY_NONE`，上游 T 会顶掉原版聊天键，见 r2） | — | 无冲突 |

**实机验证**：瞄准时按 V → 倍镜倍率切换（画面 FOV/倍率变化）；未瞄准时按 V → 近战挥击；
按住右键瞄准再按 R/G/H 等 → 不会意外切换瞄准；同一 tick 连按两个不同键（如 R、G）→ 两个动作都应触发。

## R3-4 枪械击退链（`KnockbackChange` / `KnockBackModifier`）—— 已闭环（复核 + 文档更新）

- **上游**：`event/KnockbackChange` 监听 `LivingKnockBackEvent`（`tmp/.../event/KnockbackChange.java:10-19`），
  `modifier.getKnockBackStrength() >= 0` 时 `event.setStrength(...)`；`EntityKineticBullet` 在
  `tacAttackEntity` 前后 `setKnockBackStrength(this.knockback)` / `resetKnockBackStrength()`。
- **1.7.10 落点（mixin-fixer 已实现，本报告复核）**：`mixin/common/LivingEntityKnockbackMixin` 用
  `@ModifyConstant` 把 `EntityLivingBase#knockBack` 的原版强度常量 `0.4F`
  （`build/rfg/minecraft-src/java/net/minecraft/entity/EntityLivingBase.java:1056`）替换为
  `KnockbackChange#modifyStrength(this, 0.4F)`（`LivingEntityKnockbackMixin.java:108-110`，唯一调用点 `:110`）；
  `EntityLivingBase#knockBack` 在 1.7.10 **全仓仅 1 处定义、
  无子类重写**（`:1050-1069`），覆盖面等价上游事件（`attackEntityFrom` 只在 `:921` 转调，击退计算不在其中）。
- **语义复核（本 agent 结论：正确）**：`modifyStrength(entity, original)` 仅在
  `KnockBackModifier#getKnockBackStrength() >= 0` 时覆盖，默认 `-1`（`entity/shooter/ShooterDataHolder`
  的初值/`resetKnockBackStrength()` 写回，见 `mixin/common/LivingEntityMixin.java:340-353`）→
  非枪械伤害逐位保持原版 0.4F；子弹窗口内按枪械/弹药 `bullet.knockback` 生效（70 个默认枪 data 全部声明该字段，
  如 `taurus943_data.json:23 = 0.005`）。
- **`event/KnockbackChange.java` 的 `TODO(port)` 已过期，本轮改为「已接线」**（引用
  `mixin/common/LivingEntityKnockbackMixin` 与其 `:110` 调用点），避免下一轮照 TODO 重复实现。
- **r2 §二 `U1` 状态更新：已闭环。** 残留（Lead 自修，不在本报告作用域）：
  `entity/EntityKineticBullet` 的 `set/reset` 不是 try/finally（`tacAttackEntity` 抛异常时强度不会复位），
  Lead 已认领。

## R3-5 本轮改动文件 / 回归清单

**改动**（全部 `lint_files(error)` = 0，未跑 gradle）：

- [x] `src/main/java/com/tacz/guns/util/time/GunTime.java`（R3-1：暂停停表 + `installClientTickGate`）
- [x] `src/main/java/com/tacz/guns/client/input/GunInputHandler.java`（R3-3 分发修复 + R3-1 接线）
- [x] `src/main/java/com/tacz/guns/event/LootInjectionEvent.java`（R3-2 新增）
- [x] `src/main/java/com/tacz/guns/event/EventHandlers.java`（R3-2 注册一行）
- [x] `src/main/java/com/tacz/guns/event/KnockbackChange.java`（R3-4 过期 TODO → 已接线说明）
- [x] `src/main/java/com/tacz/guns/loot/LootTableInjectorModifier.java`（R3-2 新增 `inject(List<>, List<>)` 重载）
- [x] `docs/review/r2/03-gun-logic.md`（本报告追加）

**实机回归（按优先级）**：

1. 新建单人世界勾奖励箱 → 出生点箱子出现 taurus943 + 22wmr（多建 2~3 个世界确认概率≈63%/箱）；
   `/tacz reload` 后新建世界仍生效。
2. 单机开火中 ESC 暂停 5 秒 → 恢复：无「暂停期间欠账连发」，动画不跳过暂停时长；多人服务器开火正常。
3. 瞄准按 V 切换倍率、不瞄准按 V 近战；按住右键再按 R/G 不触发意外瞄准切换；同 tick 按 R+G 两个动作都触发。
4. 开枪打生物：击退按枪械 `bullet.knockback`（如 taurus943=0.005 几乎无击退；霰弹枪/大口径更明显）；
   非枪械伤害（空手/剑/爆炸）击退与原版一致（`0.4`）。
5. 回归 r2 P0：空手左键挖方块/打怪、右键开箱/进食仍正常（本轮**未改** `detachMouseBindingsFromVanillaKeyBindingHash`）。

**已知未验证**：`ChestGenHooks` 权重近似在**多模组共存**时（其它 mod 同时改箱子池权重/数量）的数值偏差；
`LivingDropsEvent`/`HarvestDropsEvent` 注入只有逻辑验证（默认枪包没有 entity/block 注入数据，需自造枪包测试）。

