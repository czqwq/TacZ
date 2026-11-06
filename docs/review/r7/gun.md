# r7-gun · 开火/弹道/命中/伤害/换弹/事件/实体/粒子/音效 上游等价性审查

> 任务：`task-21`。作用域（读 + 授权写）：`event/**`（common）、`entity/**`、`particles/**`、
> `sound/**`、`client/particle/**`、`client/sound/**`、本报告。
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`；平台准绳：
> `build/rfg/minecraft-src/java/**`（1.7.10 原版 + Forge 10.13.4）。
> **未运行 gradle**；结论来自上下游源码对读 + 归一化 diff + 调用点扫描 + IDEA 静态检查。
> 工作区未提交的修复（r6 的 `TargetMinecart` gate/变换常数）作为基线，**未回退**（§4 复核）。

---

## 1. 结论速览

| 维度 | 结论 |
|---|---|
| **开火全链路**（`entity/shooter/**` 15 类） | ✅ 与上游逐项等价。`shoot()` 的 16 步判定顺序、冷却/网络窗口、蓄力校验、`getShootCoolDown` 的 5ms 窗口、`getBurstData().getMinInterval()` 分支全部逐行对齐；网络窗口的**动态 tick 耗时**也已确认可表达并已修（§3-5，原注释的降级理由不成立）；仅剩 1 处有据降级（§5-D1）。 |
| **换弹/拉栓/切枪/检视状态机** | ✅ `LivingEntityReload` 的 `EMPTY_RELOAD_FEEDING/TACTICAL_RELOAD_FEEDING` 初始化、`GunReloadEvent`→`startReload`→`GunFinishReloadEvent` 时机、`reloadTimestamp=-1` 复位、`draw()` 里「收枪时长补偿」的 `drawTimestamp` 重算全部一致。 |
| **弹道/命中/伤害**（`EntityKineticBullet` 1049 行） | ⚠️ **3 处真实偏离已修**（§3-1/2/3）：出生点、服务端点燃粒子、`noSave`。命中排序/穿甲拆分/爆头倍率/击退窗口/爆炸/事件双段（`Pre`→`Post`/`Kill`）逐段与上游一致。 |
| **实体同步 / NBT 对称** | ✅ `writeSpawnData`/`readSpawnData` 19 个字段**顺序与上游逐字一致**；新增 `writeToNBTOptional=false` 补齐上游 `noSave()`。 |
| **`entity/sync/**`**（9 类） | ✅ 语义等价（capability → 静态 `WeakHashMap<Entity, DataHolder>`，全部 key 均 `save=false`，无持久化缺口）；`hasSyncedDataKey` 的继承链查找（含上游那段可疑的 `while(!targetClass.isAssignableFrom(Entity.class))`）**照抄未改**。 |
| **`event/**`**（19 个上游 + 2 个移植专属） | ✅ 20/20 有注册点，双总线判定正确。子弹防弹属性、击退常量替换、末影人魔法伤害、命中方块连锁（玻璃/靶/铃）逐条对齐。 |
| **音效**（`sound/**` + `client/sound/**`） | ✅ `SoundManager.sendSoundToNearby` 半径/排除自身逻辑一致；`SoundPlayManager` 581 行（上游 334）为 1.7.10 重写但方法集**完整且语义一致**（并发限制、缺失音效告警去重、失效实体清理、`tmpSoundInstance` 停播）。`GunSoundInstance` 的距离衰减 `v = (1-min(1,d/dist))²` 与上游逐位一致。 |
| **粒子**（`particles/**` + `client/particle/**`） | ⚠️ **两个问题已修**（§3-4）：①1.7.10 的按名派发有 **16 格距离门限**（`RenderGlobal.java:2038-2043`）而上游 `ParticleEngine#createParticle` 没有 ⇒ 命中碎屑/弹道尾迹一飞出 16 格就整片消失（默认枪包 38 处粒子名全部受影响）；②`minecraft:campfire_cosy_smoke`（8 处）、`minecraft:end_rod`（1 处）在 1.7.10 **根本不存在**（`cloud`/`flame`/`smoke` 存在）⇒ 这 9 处此前从不生成。 |
| **DBG** | ✅ scope 内 `[TACZ-DBG]` / `System.out` / `printStackTrace` = **0**（全仓命中都在 `client/event`、`client/gui`、`network`、`mixin`、`util`，属他人 scope）。 |
| **lint** | 改动文件 `lint_files(min_severity=error)` = **0 error**。 |

**本轮修掉 5 条真实偏离**（全部在授权写作用域内）：出生点与上游公式不符、点燃特效在服务端**完全无效**、
子弹会被写进区块存档变成「幽灵子弹」、枪包粒子在 1.7.10 大面积失效、
以及一处被误记为「平台降级」的射击网络窗口（1.7.10 其实有 `MinecraftServer#tickTimeArray`）。

---

## 2. 逐文件对照表

判定：✅ 等价 / ⚠️ 有据降级（平台不可表达，已在代码注释登记） / 🔧 本轮已修。

### 2.1 `entity/shooter/**`（开火链路，15 文件）

| 文件 | 判定 | 证据 / 说明 |
|---|---|---|
| `LivingEntityShoot` | 🔧（1 处已修 + 1 处降级） | 逐行比对：16 步判定顺序、`isChargeProgressReasonable` 全套（含 `getMaxReasonableChargeProgress` 的 4 tick 余量）、`hasAmmoInBarrel/useInventoryAmmo/noAmmo` 组合、`ConsumeAmmoFromPlayer` 都一致。§3-5：网络窗口恢复上游「按真实 tick 耗时动态放宽」。D1：背包消耗走 `EntityPlayer#inventory`（上游 `ForgeCapabilities.ITEM_HANDLER`）。 |
| `LivingEntityReload` | ✅ | `reloadStateType` 初始化、`startReload` 失败回滚、`tickReloadState` 的 `finishedReload` 判定（上游多一个 `!isEmpty()`，1.7.10 已被 `instanceof` 前置挡掉，语义等价）逐行一致。 |
| `LivingEntityBolt` | ✅ | `bolt()` 的 8 个前置判定与 `tickBolt()` 的 `isBolting` 清除顺序一致。 |
| `LivingEntityDrawGun` | ✅ | `initialData()`→`drawTimestamp` 补偿→`GunDrawEvent`→`setCurrentGunItem`→`AttachmentPropertyManager.postChangeEvent`→`updatePutAwayTime` 顺序一致；`postChangeEvent` 的空栈跳过等价上游「非 IGun 提前返回」。 |
| `LivingEntityAim` | ✅ | `aim()` 的 `shouldBlockAim`（forceAimExitOnReload/Bolt）、`zoom()` 的 `% (Integer.MAX_VALUE-1)`、`tickAimingProgress` 的 `(now-ts+1)/(aimTime*1000)`、`tickSprint` 的 `isSneaking`（上游 `isCrouching`）全部一致。 |
| `LivingEntityMelee` | ✅ | 枪口→枪托→默认的近战数据优先序、`prepTime*20` 取整、`getTotalCooldownTime` 的 -5ms 一致。 |
| `LivingEntitySpeedModifier` | ✅ | 两个 modifier 的 UUID/`MULTIPLY_BASE`/`MULTIPLY_TOTAL`、`targetSpeed *= -weightFactor`、`getTargetSpeed` 的 reload→aim→base 分支一致；`setSaved(false)` = 上游 `addTransientModifier`。 |
| `LivingEntityUnload` | ✅ | 12 个前置条件与「主手栈 ≤ 当前枪」比较一致。 |
| `LivingEntityCrawl` | ⚠️ | 状态标志与判定链一致；**姿态写入不可表达**（1.7.10 无 `Pose`/`setForcedPose`），`isCrawling` 目前无渲染/体型消费者 → 「趴下」无可见效果（§5-D3）。 |
| `LivingEntityFireSelect` / `LivingEntitySprint` / `LivingEntityHeat` / `LivingEntityAmmoCheck` / `GunScriptGuard` / `ShooterDataHolder` | ✅ | 归一化 diff 只剩平台替换；`ShooterDataHolder` 字段名/可见性/默认值与上游**逐字一致**（Mixin 直接读写）。移植新增 `getCurrentGunItem()` 只做「null 栈归一」，等价上游 `ItemStack.EMPTY`。 |

### 2.2 `entity/**`（实体）

| 文件 | 判定 | 说明 |
|---|---|---|
| `EntityKineticBullet` | 🔧（3 处已修） | 见 §3-1/2/3。其余逐段核对一致：`onBulletTick` 的爆炸延迟→方块射线→实体射线→穿甲排序→`onHitBlock`；`getDamage` 的距离档位（首档用 `distanceAmount` 替换）逐位一致；`createDamageSources` 的末影人魔法伤害对应 `data/tacz/tags/entity_types/use_magic_damage_on.json` = `["minecraft:enderman"]`（另两个 tag 文件为空数组 ⇒ 无需实现）。`MaybeMultipartEntity` 用 `EntityDragonPart#entityDragonObj` 还原上游 `PartEntity#getParent`。 |
| `TargetMinecart` | ✅（r6 未回退） | `func_145817_o()` 仍返回 `Blocks.stone`（gate 触发器）；`onProjectileHit` 的 rolling/hurtMarked/`setDamage(10)`/提示/`TargetHitTrigger`/声音/`EntityHurtByGunEvent.Post`+`ServerMessageGunHurt` 与上游逐条对应；爆炸免伤与 `killMinecart` 掉落名保留。 |
| `EntityBulletClientHooks` | ✅（移植专属） | common 侧不引用客户端类的间接层，`ClientProxy` 赋值 `AmmoParticleSpawner::addParticle`，与上游 `DistExecutor.unsafeRunWhenOn(CLIENT, ...)` 等价。 |

### 2.3 `entity/sync/**`（9 文件）

| 文件 | 判定 | 说明 |
|---|---|---|
| `SyncedEntityData` | ✅ | 注册/内部 id/映射更新/`dirty` 收集一致；capability 由 `WeakHashMap<Entity,DataHolder>` + 按需创建替代（上游 `DataHolderCapabilityProvider` 无 1.7.10 对应），`hasSyncedDataKey` 的继承链判定**照抄**（含上游那段写法可疑的循环条件 —— 按「上游自身问题不修」保留）。 |
| `DataHolder` / `DataEntry` / `Serializers` / `SyncedDataKey` / `SyncedClassKey` / `IDataSerializer` / `ModSerializers` / `ModSyncedEntityData` | ✅ | 只删了 `Serializers.BLOCK_POS`（1.7.10 无 `BlockPos`，全仓无调用点）；9 个 key 的名字/默认值/`syncMode` 与上游一致。 |

### 2.4 `event/**`（21 文件）

| 文件 | 判定 | 说明 |
|---|---|---|
| `SyncedEntityDataEvent` | ✅ | 6 个上游钩子（attach→Join、StartTracking、玩家进世界、Clone、ServerTick 下发）全部有 1.7.10 落点；`Phase.END`、`isSelf/isTracking` 分流、`holder.clean()` 一致。 |
| `EntityDamageEvent` | ✅ | `LivingHurtEvent` + `isBulletDamage` + `BULLET_RESISTANCE` 属性，`EventPriority.LOW` 一致。 |
| `KnockbackChange` | ⚠️→✅ | 1.7.10 无 `LivingKnockBackEvent`：改为 `modifyStrength()` 静态钩子，由 `mixin/common/LivingEntityKnockbackMixin` 用 `@ModifyConstant` 替换 `EntityLivingBase#knockBack` 的 `0.4F`（唯一调用点已核实）。`strength>=0` 才覆盖的语义一致。 |
| `GunSoundAngerEvent` | ✅ | 中立/敌对半径、`fireMultiplier` 上限、消音器判定、`isOnSameTeam`、驯服/铁傀儡白名单一致。 |
| `HitboxHelperEvent` / `ServerTickEvent` / `SyncBaseTimestamp` / `PreventGunClick` / `PlayerRespawnEvent` / `LoadingConfigEvent` / `CommonLoadPack` | ✅ | 判定/顺序一致；`LoadingConfigEvent.reloadConfig()` 的 4 个 `*ConfigRead.init()` 由 `EventHandlers.registerAll()` 在配置加载后调用（上游由 `ModConfigEvent` 驱动），`HitBox` 表不会为空。 |
| `TravelToDimensionEvent` | ⚠️ | 1.7.10 **不存在** `EntityTravelToDimensionEvent`（全仓仅 `Entity#travelToDimension(int)`，无事件）⇒ 只能用 `PlayerEvent.PlayerChangedDimensionEvent`：只覆盖玩家、时机从「传送前」变「传送后」。类注释已写明补齐路径（mixin `Entity#travelToDimension`）。 |
| `GunDisplayCaseInteractionEvent` / `GunPegboardInteractionEvent` | ⚠️ | 左键取枪链（`isLocked`/`getSlot`/`dropItem`/`removeItem`/`random.pop`）等价；展示柜「右键+副手切换上锁」因 1.7.10 无双持无法表达（TODO 已登记）。 |
| `AmmoStorageInteractionEvent` / `DestroyGlassBlock` / `ChangeGunPropertyEvent` / `AmmoStorage…` | ✅ | 潜行+弹药盒交互、玻璃销毁（`playAuxSFX 2001` 粒子/音效）、配件属性重算一致。 |
| `ammo/BellRing` | ⚠️ | 1.7.10 无钟方块（1.14+），保留空监听器作接入点（已登记）。 |
| `EventHandlers` / `LootInjectionEvent` | ✅（移植专属） | 统一注册入口（双总线分流正确）；`LootInjectionEvent` 用 `ChestGenHooks`/`LivingDropsEvent`/`HarvestDropsEvent`/`CreateSpawnPosition` 四路替代 1.20 global loot modifier，权重推导（`w=V/C`、抽取数 `+K`）与幂等撤回逻辑在类注释中有完整推导。 |

### 2.5 音效

| 文件 | 判定 | 说明 |
|---|---|---|
| `sound/SoundManager` | ✅ | `sendSoundToNearby` 的半径平方判定与「排除说话者自己」一致（1.7.10 走 `WorldServer#playerEntities`）。 |
| `client/sound/SoundPlayManager` | ✅（见降级） | 方法集全覆盖：`playClientSound` 的 3 层重载（`SoundData` 音量/音高随机化 → 并发限制 → `useTaczSound`/回退 `sounds.json`/两者皆无则告警）与上游一致；`playShootSound 0.8f, 0.9+rand*0.125`、`playSilenceSound 0.6f`、`playMessageSound` 的 `SHOOT_3P/SILENCE_3P` 走高频并发限制、`cleanupInvalidEntitySounds` 每 5 tick、`SoundKey/TrackedGunSound` 的 record 语义均一致。 |
| `client/sound/GunSoundInstance` | ✅ | `extends PositionedSound`（上游 `AbstractSoundInstance`）；`attenuation=NONE` + 自算距离衰减、`resolve()` 把「枪包原始 ogg」注册进 `SoundHandler#sndRegistry`（反射按**字段类型**定位，抗 SRG），对应上游 `TaczSound#getPath()` 覆写。`canPlay = !entity.isSilent()` → 恒 `true`（1.9+ 才有 isSilent）。 |
| `client/sound/EntityTrackingGunSoundInstance` | ✅ | `ITickableSound`(`update`/`isDonePlaying`) 对应上游 `TickableSoundInstance`；`entity.isRemoved()→isDead`、`isDeadOrDying()→!isEntityAlive()`、`getEyeY()→posY+getEyeHeight()`。 |
| `client/sound/GunSoundPreload` | ✅ | 仅音效 id 常量表，无行为。 |
| `client/sound/GunPackResourcePack(ReloadListener)` | ✅（移植专属） | 上游靠「枪包即资源包」；1.7.10 需自建 `IResourcePack` 把枪包 ogg 挂进 `IResourceManager`。类注释给出了「每次 `refreshResources` 会清空 domain managers」的根因与注册时机推导，本轮只做接口核对（重载监听已由 `ClientGunPackReloader` 注册）。 |

### 2.6 粒子

| 文件 | 判定 | 说明 |
|---|---|---|
| `particles/BulletHoleOption` | ✅（降级） | 去掉 `ParticleOptions` 那层（1.7.10 无粒子注册表/brigadier），字段与构造函数重载保留；`ForgeDirection` 替代 `Direction`、`BlockPos` 拆 x/y/z。 |
| `client/particle/HitBlockEffectHandler` | ✅ | 靶方块跳过、`shouldRemove`（空气/无碰撞箱/命中点不在盒内 ±0.1）、碎屑、`stepSound` 每 tick 同一声只放 1 次（64 格内）、弹孔开关判定全部一致。 |
| `client/particle/HitSurfaceParticleSpawner` | ✅ | 距离门限 `HIT_PARTICLE_MAX_DISTANCE`、`DEFAULT_*` 常量逐个一致（0.03/0.01/3/0.14/0.015/30°）、`randomCount` 的 min/max 交换、`remainingCount` 64 上限、`usesScaleInParticleOptions` 一致；`block/falling_dust/dust/item` 分支 1:1 映射到 `EntityDiggingFX/EntityBlockDustFX/EntityReddustFX/null`。 |
| `client/particle/AmmoParticleSpawner` | ✅ | 「枪械粒子优先、否则弹药粒子」、`count==0` 的单粒子分支、`owner` 3 格内不生成、`life_time` 覆写一致。 |
| `client/particle/BulletHoleParticle` | ✅ | `lifetime = life + rand(life/2)`、`quadSize=0.05`、16×16 随机子格 UV、亮度 `max(15-age/2,0)`、颜色随亮度衰减、`fade_threshold` 透明度衰减、`shouldRemove` 一致；`EffectRenderer` 的 `getFXLayer()==1` 对应上游 `TERRAIN_SHEET`。 |
| `client/particle/MuzzleParticleManager` / `ThirdPersonMuzzleParticleManager` | ✅（结构核对） | PENDING/FLASH_PARAMS/FLASH_EMITTERS/FLASH_START 的时机与键类型一致，`onMuzzleRendered` 的锚点更新/失败回退、`RENDER_MUZZLE_SMOKE` 门限一致；差异集中在矩阵/相机逆变换的 1.7.10 实现（属渲染层）。 |
| `client/particle/ParticleCompat` | 🔧 | 见 §3-4（名字映射 + 绕过 16 格门限）。 |
| `client/particle/ParticleDefinitionReloader` | ✅（移植专属） | 替代 simplebedrockmodel 自己注册的重载监听（1.7.10 无 `RegisterClientReloadListenersEvent`），由 `ClientGunPackReloader` 挂入。 |

---

## 3. 本轮修复（4 项）

### 3-1 子弹出生点与上游公式不符（`EntityKineticBullet`，P2）

- 修前：`setPosition(posX, posY + getEyeHeight() - 0.1, posZ)` —— **取自上游第一个委托构造器的中间值**
  （上游 `EntityKineticBullet.java:174`），而真正的出生点在构造体末尾被重算
  （上游 `:218-221`）：
  `x = xOld + (getX()-xOld)/2`、`y = yOld + (getY()-yOld)/2 + getEyeHeight()`、`z` 同理。
  即移植版**少了「半 tick 插值」并低 0.1 格**。
- 影响：射线的起点/`startPos`（伤害距离衰减基准）整体偏移；走动扫射时弹道比上游更「贴当前帧」。
- 修法：`EntityKineticBullet.java:252-261` 按上游公式重算，用 1.7.10 的等价字段
  `lastTickPosX/Y/Z`（`World#updateEntity:2282-2284` 在 `onUpdate` 前写入，语义 = 上游 `xOld/yOld/zOld`）。
- 未采纳的替代：保留 `-0.1` 会让「站立平射」永远略低于准星中心，属无依据偏离。

### 3-2 点燃特效在服务端**完全无效**（`EntityKineticBullet`，P2）

- 上游 `:439`、`:507`、`:515` 用 `ServerLevel#sendParticles(...)` **由服务端广播**；
  1.7.10 的 `World#spawnParticle` 在服务端只遍历 `worldAccesses`，而 `WorldServer` 只挂了
  `WorldManager`（不转发粒子）⇒ 移植版的三处召唤**什么都不发生**。
  1.7.10 的服务端广播入口是 `WorldServer#func_147487_a`（`WorldServer.java:1121-1139`，打包
  `S2APacketParticles` 发给 16 格内玩家）。
- 修法：新增 `spawnServerParticles()`（`:714`），三处调用改为它；`igniteBlock` 的 4 颗粒子合并为一次
  `count=4` 调用（等价上游 `sendParticles(..., 4, 0,0,0, 0)`，`:685/:694`）。
- 已知平台差：上游广播半径 32 格 → 1.7.10 原版 16 格（写在方法注释里）。

### 3-3 子弹会被写进区块存档（`EntityKineticBullet`，P3）

- 上游 `EntityType.Builder#noSave()`；1.7.10 没有该标志，`AnvilChunkLoader.java:371` 会对区块内每个实体调
  `writeToNBTOptional`，返回非 false 就落盘。而本类 `writeEntityToNBT` 为空 ⇒ 存下来的是**只有基类
  Pos/Motion、没有弹药/伤害字段的幽灵子弹**，读档后继续飞几十 tick。
- 修法：`EntityKineticBullet.java:1036` 覆写 `writeToNBTOptional` 返回 `false`（原版
  `EntityLeashKnot` 同款做法）。核对过剩余两个调用点（`MobSpawnerBaseLogic`、
  `ForgeChunkManager` 的 ENTITY ticket）对子弹均无意义。

### 3-4 枪包粒子在 1.7.10 全体失效（`ParticleCompat`，P2）

两个叠加原因，**修前等于「命中碎屑 / 弹道尾迹大面积失效」**：

1. **名字不存在**（9/38 处）：默认枪包用的粒子 id 是 1.13+ 的 `minecraft:cloud`（27 处）、
   `minecraft:campfire_cosy_smoke`（8 处）、`minecraft:end_rod`（1 处）、`minecraft:flame` / `minecraft:smoke`（各 1 处）。
   已核实 1.7.10 的按名派发 if 链里 `cloud`(:2147)/`flame`(:2123)/`smoke`(:2076) **存在**，
   而 `campfire_cosy_smoke` / `end_rod` **不存在** ⇒ 这 9 处在移植版此前一个粒子都不生成。
2. **16 格距离门限**（38/38 处受影响）：`RenderGlobal.java:2038-2043` 对距离 `renderViewEntity` > 16 格的
   粒子直接返回 `null`；上游走 `ParticleEngine#createParticle` **没有这个门限**。
   子弹飞离 16 格后尾迹即断、远处（>16 格）命中点完全没有碎屑 —— 这是射击手感上最明显的一条。

修法（`ParticleCompat.java:74/121/157`）：
- 新增 `toLegacyParticleName`：`campfire_*`/`large_smoke` → `largesmoke`、`poof`/`ash`/… → `smoke`、
  `end_rod`/`electric_spark`/`glow` → `crit`、`enchanted_hit` → `magicCrit`、`soul*` → `flame`，
  其余同名透传；非 `minecraft` 命名空间返回 `null`（1.7.10 无粒子注册表）。
- 常见名字改为**直接构造** `EntitySmokeFX`/`EntityCloudFX`/`EntityFlameFX`/`EntityLavaFX`/`EntityCritFX`
  后 `EffectRenderer#addEffect`（无距离门限），未收录的名字仍回退到原版按名派发。
- 保留 1.7.10 的 `gameSettings.particleSetting`（「最少」不出粒子），与上游玩家侧同一设置语义一致。

### 3-5 射击网络窗口：**被误记为「平台降级」**，实际可等价（`LivingEntityShoot`，P2）

- 修前代码注释称「1.7.10 的 `MinecraftServer` 不记录每 tick 耗时（`timeOfLastDimensionTick` 已被注释掉）」
  ⇒ 固定按 50ms 估算窗口。**该结论不成立**：1.7.10 的 `MinecraftServer` 有
  `public final long[] tickTimeArray = new long[100]`（`MinecraftServer.java:134`），
  且在 `tick()` 的 tallying 段写入「本 tick 纳秒耗时」
  （`MinecraftServer.java:645` `tickTimeArray[tickCounter % 100] = System.nanoTime() - i`，
  `tickCounter` 在同一次 tick 开头的 `:608` 自增）——**与上游 `server.tickTimes[tickCount % 100]`
  是同一个量、同一个索引方式**；`getTickCounter()`（`:1287`）公开可读，数组初值 0 由 `Math.max(..., 50)` 兜住。
- 影响：卡服/低 TPS 时上游会**动态放宽**接受窗口（`300 + 2×tickTime`），移植版固定 50ms ⇒ 窗口最窄
  ⇒ 高延迟玩家在卡服时会被判 `NETWORK_FAIL`（并被迫重新校准 baseTimestamp），表现为「卡一下之后连发丢枪」。
- 修法（`LivingEntityShoot.java:107-124`）：
  ```java
  MinecraftServer server = MinecraftServer.getServer();
  double tickTime = server == null ? 50.0D
      : Math.max(server.tickTimeArray[server.getTickCounter() % 100] * 1.0E-6D, 50);
  ```
  `server == null` 只在多人**客户端**预测路径出现（上游此处 `Objects.requireNonNull` 隐含必有服务端，
  1.7.10 多人客户端没有集成服），回退 50ms 与上游 `Math.max(..., 50)` 的下限一致。
- 类注释里「1.7.10 降级（两处）」已改为「一处」（另一处是弹药消耗入口，见 §5-D1）。

---

## 4. r6 修复复核（不得回退）

| 项 | 现状 |
|---|---|
| `TargetMinecart#func_145817_o()` 返回 `Blocks.stone`（renderType 0） | ✅ 仍在（`entity/TargetMinecart.java:108-111`），gate 放行 |
| `TargetMinecartRenderer` 的 `T(0,1.875,0)·S(1.5)·Rx(180)` 常数 | ✅ 未改（属渲染 scope，只做只读复核） |
| r6 遗留的 `yOffset` 命中盒错位 | ⬜ 仍未修（r6 报告结论：需要独立实机验证，本轮不动） |

---

## 5. 未修与有据降级（逐条记理由）

**D1 · 背包弹药消耗入口**（`LivingEntityShoot#consumeAmmoFromPlayer`）
上游 `shooter.getCapability(ForgeCapabilities.ITEM_HANDLER)`；1.7.10 直接用 `EntityPlayer#inventory`，
非玩家实体没有背包 ⇒ 不消耗（与上游「取不到 item handler 就不消耗」一致）。

**D2 · 趴下（crawl）无可见效果**（`LivingEntityCrawl`）
1.7.10 无 `Pose`/`setForcedPose`/`isSwimming`/`jumping()`，移植版只维护 `ShooterDataHolder#isCrawling`
（服务端判定链完整），**没有 AABB/eyeHeight 的消费者** ⇒ 目前「按趴下键」只有状态没有视觉。
补齐需要 `EntityLivingBase` 的 Mixin（属 `mixin/**` 与 `client/**` scope）→ **跨模块发现 C-3**。

**D3 · 跨维度刷新只覆盖玩家**（`TravelToDimensionEvent`）
1.7.10 Forge **没有** `EntityTravelToDimensionEvent`（已全仓确认，只有 `Entity#travelToDimension(int)`），
只能用 FML 的 `PlayerEvent.PlayerChangedDimensionEvent`（仅玩家 + 传送后触发）。

**D4 · 无 `LivingKnockBackEvent`**（`KnockbackChange`）
改为 `@ModifyConstant` 替换 `EntityLivingBase#knockBack` 里的 `0.4F`，覆盖 `attackEntityFrom` 链路；
副作用：非 `knockBack()` 路径的击退（模组自算 `addVelocity`）不受影响——与上游事件覆盖面对齐。

**D5 · 粒子层三处表达不了**
`DustColorTransitionOptions`（双色渐变）→ 单色 `EntityReddustFX`；带参数的粒子写法
（`ParticleArgument.readParticle`）→ 该条不生成；`minecraft:item` → `null`（上游同样返回 `null`）。
另有 16 格门限只对「未收录名字」仍生效（已修常见名字，见 §3-4）。

**D6 · `canPlay = !entity.isSilent()`**（`GunSoundInstance`）
`Entity#isSilent` 是 1.9+ API，1.7.10 无任何「按实体静音」机制 ⇒ 恒 `true`。

**D7 · 展示柜/挂板的副手分支**（`GunDisplayCaseInteractionEvent`）
1.7.10 无双持，「副手拿枪切换上锁」无法表达（TODO 已登记，block 层保留了 `toggleLockFromInteraction` 入口）。

**D8 · `BellRing`**：1.7.10 无钟方块（1.14+），保留空监听器作接入点。

**D9 · 音效资源缺失告警**
`SoundPlayManager#warnMissingSound` 与上游一致，`MISSING_SOUND_WARNED` 去重后每 id 只打一次。
r5 已确认「上游自身音效数据缺文件」，**本轮不修数据**（不属代码偏离）。

**未深度核对（诚实声明）**
- `client/particle/MuzzleParticleManager`（33.5 KB）：只做「结构 + 关键分支」核对（PENDING/BOUND 时机、
  emitter 锚点、`RENDER_MUZZLE_SMOKE` 门限、光照档位），未逐行核对其矩阵/相机逆变换数值
  （依赖 `ParticleEffectDefinitionLoader` / `WorldEmitterManager`，属渲染与 client/resource scope）。
- `client/sound/GunPackResourcePack`（30.5 KB）：只核对了对外接口与重载时序推导，未逐行审其 IO 实现。
- `client/particle/ParticleDefinitionReloader`：只核对注册落点。

---

## 6. 跨模块发现（需他人裁决/落地，本任务不改）

| # | 位置 | 发现 | 建议归属 |
|---|---|---|---|
| **C-1** | `api/entity/ITargetEntity` | 签名相对上游漂移：上游 `onProjectileHit(EntityKineticBullet, EntityHitResult, DamageSource, float)`，移植为 `(EntityKineticBullet, Entity, DamageSource, float)`。1.7.10 确无 `EntityHitResult`，但本工程的 `util/TacHitResult`（含 `entityHit/hitVec/isHeadshot`）是上游同名概念的等价物，**保持上游形状是可行的**。当前实现与调用点自洽、语义等价，但属 `api/**` 签名漂移（YSM 不使用该接口，风险低）。**→ 已由 r7-api 解决**（签名改为 `(Entity, TacHitResult, ...)` 并改好两处实现/派发，见 §9-3） | ~~task-28（r7-api）~~ 已解决 |
| **C-2** | `event/LoadingConfigEvent#reloadConfig()` | 只在 `EventHandlers.registerAll()` 调一次。若日后接入运行期配置重载（r5 的 F-4 `ConfigManager.reloadAll()` 目前无调用点），需同步再调，否则 `HeadShotAABBConfigRead`/`AmmoBlockConfigRead` 等静态表会是旧值。 | task-23（r7-core）/ 配置归属 |
| **C-3** | `entity/shooter/LivingEntityCrawl` + `client/gameplay/LocalPlayerCrawl` | 趴下只置标志，**无任何体型/视角消费者** ⇒ 功能不可见。补齐点：`EntityLivingBase` 的 AABB/eyeHeight Mixin（或客户端 `EntityRenderer` 视角）。上游用 `Pose.SWIMMING` 一次性设置，属真正的平台缺口（不是本轮能修的偏离）。 | task-24（r7-platform）/ task-19（r7-input） |
| **C-4** | `TravelToDimensionEvent` | 非玩家实体跨维度不刷新操作数据。补齐点：`mixin` 注入 `Entity#travelToDimension(int)`（1.7.10 无对应 Forge 事件）。 | task-24（r7-platform） |
| **C-5** | `EntityKineticBullet` 的 `PRETEND_MELEE_DAMAGE_ON` / `USE_VOID_DAMAGE_ON` 实体 tag | 1.7.10 无实体 tag。核对资源后**影响面为零**：`pretend_melee_damage_on.json` 与 `use_void_damage_on.json` 都是空数组，`use_magic_damage_on.json` 只有 `minecraft:enderman`（已硬编码）。**不需实现**，仅登记。 | 信息 |
| **C-6** | 粒子 emitter 依赖 | `ThirdPersonMuzzleParticleManager` / `MuzzleParticleManager` 依赖 vendored simplebedrockmodel 的 `ParticleDefinitionLoader`/`WorldEmitterManager`（`client/resource`、`client/model` scope）。本任务未核对其实现。 | task-27（r7-respack-client） |

---

## 7. 实机验证清单（静态无法定论）

**A. 本轮修复项（重点）**

1. **出生点**：站立平射 8 格外的方块中心 → 弹孔应落在准星**中心**（修前整体偏低约 0.1 格）；
   边走边射（侧向移动）→ 弹孔位置与静止射击同一条线（半 tick 插值生效）。
2. **点燃特效**：用带 `"ignite": { "entity": true }` 的弹药物理（或把配置 `AmmoConfig#IgniteEntity` 打开
   的燃烧弹）打生物 → 命中瞬间目标身上出现**火焰粒子**（修前完全没有）；打方块 → 命中点有 1 颗岩浆粒子，
   若该弹药另有 `ignite.block` 且 `AmmoConfig#IgniteBlock` 为真，还会在命中面相邻空气格点火 + 4 颗岩浆粒子。
   ⚠️ 1.7.10 广播半径 16 格（上游 32）⇒ 只能 16 格内可见，属平台差异。
3. **子弹不落盘**：射击中途退出世界（子弹还在飞）→ 重新进入，**不应**再看到持续飞行的子弹/无源伤害。
4. **粒子名字映射 + 距离门限**：用默认枪包对 20 格外的方块开火 →
   墙面出现烟雾/碎屑（`minecraft:cloud`→`EntityCloudFX`、`campfire_cosy_smoke`→`largesmoke`）；
   修前 >16 格**什么都看不到**。连续射击时弹道尾迹可见（`assets/tacz/display/ammo/*_display.json` 的 `particle`）。
   对比项：把 `Options → 粒子效果` 设为「最少」→ 上述粒子消失（预期）。
5. **射击网络窗口**：把游戏跑在明显掉 TPS 的状态（例如一堆刷怪笼/`/tick rate` 类工具不可用时用区块加载压力），
   以 200ms 以上延迟连发 → **不应**出现「卡一下之后连发中途丢枪」；日志里不应频繁出现
   `ServerMessageSyncBaseTimestamp`（修前固定 50ms 窗口会更频繁触发）。

**B. 全链路回归（每次开火都会走，改动集中在这里，务必逐项过）**

5. 各射速档位（单发/连发/三连发/蓄力）射击手感不变；冷却判定（`COOL_DOWN`）、拉栓枪（`NEED_BOLT`）、
   背包直读弹药（`NO_AMMO`）、过热（`OVERHEATED`）提示与修前一致。
6. 换弹（空仓/战术）、拉栓、切枪、检视、刺刀的**动画音效与计时**不变；`forceAimExitOnReload/Bolt` 仍会强制退镜。
7. 命中链：普通伤害/穿甲伤害分段、爆头倍率、距离衰减档位、击退强度（`knockback` 来自枪械数据）与修前一致；
   打靶（`TargetBlock` / `TargetMinecart`）仍触发红石/成就/声音/提示。
8. 末影人：仍能被子弹伤害（间接魔法伤害），且不产生击退异常。
9. 音效：第三人称枪声随实体移动；超量连发时并发限制生效（不会叠成噪音）；缺资源时日志只打一次。

**C. 与其它模块交界**

10. 配件的消音器/枪口/枪托改变音效与近战数据（`SoundManager.*_SOUND` 常量映射）仍生效。
11. 服务端专用服启动：`LootInjectionEvent` 的箱子注入（新世界奖励箱 + 地牢箱）与 `/tacz reload` 后刷新。

---

## 8. 改动文件清单（供 Lead 复核 diff）

| 文件 | 改动 |
|---|---|
| `src/main/java/com/tacz/guns/entity/EntityKineticBullet.java` | ①出生点按上游公式重算（`:252-261`）；②服务端点燃粒子改用 `WorldServer#func_147487_a`，新增 `spawnServerParticles()`（`:712`，3 个调用点）；③新增 `writeToNBTOptional=false`（`:1032`）；④修正 `entityInit` 处重复 javadoc 与「走 SyncedEntityData」的错误说明；⑤水下气泡补 `isRemote` 守卫（`:312-330`，见 §9-2）；⑥散布噪声对齐上游 triangle/0.0172275 并抽 `applyShoot`（`:418-500`，见 §9-4） |
| `src/main/java/com/tacz/guns/client/particle/ParticleCompat.java` | ①`toLegacyParticleName`（1.20→1.7.10 粒子名映射，`:121`）；②`createLegacyParticle` 直接构造常见 `EntityFX`（`:157`），绕过 16 格距离门限；③类/方法注释补充平台差异 |
| `src/main/java/com/tacz/guns/entity/shooter/LivingEntityShoot.java` | 网络接受窗口恢复上游「按真实 tick 耗时动态放宽」：`MinecraftServer#tickTimeArray[getTickCounter()%100]*1e-6`，`getServer()==null` 时回退 50ms（`:107-124`）；类注释「两处降级」改为一处 |
| `src/main/java/com/tacz/guns/event/LootInjectionEvent.java` | 见 §9-1：实体→1.20 战利品表 id 改走 `util/EntityIdHelper#getModernId`（`:356-385`），删掉本类自带的 `new ResourceLocation(name.toLowerCase())` 解析 |
| `docs/review/r7/gun.md` | 本报告 |

**自检**：`lint_files(min_severity=error)` 对 4 个改动文件 → `{"items":[]}`，**0 error**；无新增 DBG、无临时开关、
无死代码、无内联 FQN（新增类型全部走 import）。**未运行 gradle**（遵守约定）。

---

## 9. 跨模块修正（收尾轮，Lead 转派）

### 9-1 P2 · `event/LootInjectionEvent` 的实体 → 1.20 战利品表 id 推导错误

**来源**：r7-util 在 `docs/review/r7/util.md` §5-2 独立发现 → Lead 转派给我（原文件属我 scope）。

**根因**：原实现是
`EntityList.getEntityString(entity)` → `ResourceLocationHelper.tryParse(name.toLowerCase())` → 拼
`entities/<path>`。但 1.7.10 的注册名**两种形态都不是 1.20 的实体 id**：

| 形态 | 1.7.10 实际返回值 | 老写法算出的表 id | 1.20 正确表 id |
|---|---|---|---|
| FML 模组实体 | `"tacz.bullet"`（点号，`EntityRegistry.java:164` `String.format("%s.%s", modid, entityName)`） | `minecraft:entities/tacz.bullet` | `tacz:entities/bullet` |
| 多词原版实体 | `"ItemFrame"` / `"PigZombie"` / `"EntityHorse"` | `minecraft:entities/itemframe` / `pigzombie` / `entityhorse` | `minecraft:entities/item_frame` / `zombified_piglin` / `horse` |

⇒ **模组实体的 `entities/*` 注入 100% 失效**（含 TACZ 自己的 `bullet`/`target_minecart`），
多词原版实体同样失效。箱子类注入不受影响（走 `ChestGenHooks` 分支，表 id 是 `chests/*` 常量）。

**修法**（`event/LootInjectionEvent.java:356-385`）：改为**直接复用 `util/EntityIdHelper#getModernId(Entity)`** ——
它是本工程「1.7.10 注册名 → 1.20 实体 id」的单一来源（差异表在 `util/EntityIdHelper.java:43-80`，31 项，
含 `PigZombie→zombified_piglin`、`ItemFrame→item_frame`、`EntityHorse→horse`、`EnderCrystal→end_crystal`
以及 `Skeleton` 的 wither 变体；内部委托 r7-api 的 `ResourceLocationHelper#tryParseEntityName`）。
本类**不再保留第二份解析/映射表**（Lead 也明确要求「避免两套 helper」），只在解析失败时
`debug` 一次（`UNRESOLVED_ENTITY_NAMES_LOGGED` 按名字去重，`entityLootTableId` 每次掉落都会走到）。
`EntityList` 只用于那条 debug 日志，`Locale`/自建表已删除。

**记录（不改）**：`util/EntityIdHelper` 属 r7-util scope 且已收工；本轮未发现它缺我需要的映射
（战利品注入只用 `getModernId`，不依赖旧形态候选）。

**验证**：①默认枪包只有箱子注入（`spawn_bonus_chest_taurus943.json`），不受影响；
②自建枪包写 `"minecraft:entities/pigzombie"`? 不 —— 应写 `minecraft:entities/zombified_piglin`（1.20 id），
打死僵尸猪灵应掉出注入物；③写 `"tacz:entities/bullet"` 时子弹实体应有注入（此前永远不生效）。

### 9-2 `EntityKineticBullet` 入水气泡：补 `isRemote` 守卫（**不**改服务端广播）

**结论先行**：这条**不是**「服务端该广播却用了 no-op」，而是「缺守卫 + 缺依据说明」。**没有**改成
`spawnServerParticles()` —— 那会比上游**多一倍**粒子。

**证据（服务端在 1.20 也不发）**：
- 上游 `EntityKineticBullet.tick()` `:277-284` 是 `this.level().addParticle(ParticleTypes.BUBBLE, ...)`；
- `Level#addParticle(ParticleOptions, double×6)` 在基类是**空实现**（`tmp/minecraft-src-1.20.1/.../world/level/Level.java:404`，
  带 `force` 的重载 `:407` 同样为空）；只有 `ClientLevel` 覆写并交给 `LevelRenderer`
  （`ClientLevel.java:597`）；
- ⇒ 上游气泡是**各客户端本地**生成（每颗子弹在每个客户端各自 tick），服务端从不广播；
- 1.7.10 的 `World#spawnParticle`（`build/rfg/.../world/World.java:1457-1461`）是同一套分流：客户端
  `WorldClient` → `RenderGlobal.doSpawnParticle`，服务端 → `IWorldAccess`（`WorldManager`，无转发）。

**修法**（`EntityKineticBullet.java:312-330`）：显式加 `if (this.worldObj.isRemote)` 分流 + 把上述依据写进注释
（含「不要改成广播」的原因）。服务端行为等价（原本就是 no-op，现在少 4 次空调用），客户端不变。

**遗留（有据）**：1.7.10 的客户端路径受 `RenderGlobal.java:2038-2043` 的 16 格门限，上游客户端无此门限
⇒ 水下子弹飞出 16 格后气泡不再生成。这是平台门限，和 §3-4 同源；要修需要 `EntityBulletClientHooks`
（common 不能直接 new 客户端 `EntityBubbleFX`）+ `client/ClientProxy` 一行接线（**跨 scope**），
本任务不做，已登记为可选后续。

### 9-3 r7-api 代做的 `ITargetEntity` 对齐（我复核：无冲突、不回退）

r7-api 已把 `api/entity/ITargetEntity#onProjectileHit` 对齐为
`(Entity projectile, TacHitResult result, DamageSource source, float damage)`（等价上游
`(EntityKineticBullet, EntityHitResult, ...)`），并改了 `EntityKineticBullet:465`（派发，此前第二处重复派发已删）
与 `TargetMinecart:115`（实现）——即 §6 的 **C-1 已解决**。我复核：
- 同一文件里我本轮的改动（出生点 `:252-261`、`spawnServerParticles` `:714`、`writeToNBTOptional` `:1046`、
  水下气泡守卫 `:312-330`）都仍在，二者不冲突；
- `result`（`TacHitResult`）本来就带 `entityHit/hitVec/isHeadshot`，与新签名自洽；`getDamage(result.hitVec)` 不变；
- `GET / lint` 两文件均 0 error。

### 9-4 P2 · `EntityKineticBullet#shootFromRotation` 的散布噪声与上游不一致（r7-api 报告发现 → Lead 转派）

**问题**：`shootFromRotation(..., float inaccuracy)` 用的是 1.7.10 `EntityThrowable` 那套
`nextGaussian() * 0.007499999832361937 * inaccuracy`，而上游 1.20 走的是
`Projectile#shoot` → `random.triangle(0, 0.0172275 * inaccuracy)`
（`tmp/minecraft-src-1.20.1/.../world/entity/projectile/Projectile.java:120-131`；
`RandomSource#triangle` = `mean + deviation * (nextDouble() - nextDouble())`，`RandomSource.java:55-57`）。

**后果**：高斯 vs 三角分布、系数 0.0075 vs 0.0172275（差 2.3 倍）⇒ 同一把枪
「真实枪械路径」（`ModernKineticGunItem#doBulletSpread` → `bullet.shootFromRotation(..., inaccuracy)`，
`ModernKineticGunItem.java:463`）与「api 默认路径」（`AbstractGunItem#doBulletSpread`，r7-api 已按上游复刻）
的散布量级不一致；且源作者调的散布参数是按 triangle 调参的，实测表现会偏离上游。

**修法**（`EntityKineticBullet.java:418-500`）：
1. 新增**私有静态** helper `applyShoot(bullet, dirX, dirY, dirZ, velocity, inaccuracy)`，逐行复刻上游
   `Projectile#shoot`：归一化（长度 < 1e-4 归零，避免除零 NaN）→ 每轴 `deviation*(nextDouble-nextDouble)`
   （`deviation = 0.0172275 * inaccuracy`）→ 乘初速 → 写 motion 与 `rotationYaw/Pitch`/`prev*`；
   `deviation == 0` 时跳过抽样（不白耗共用的 `World#rand`）。
2. `shootFromRotation(..., float inaccuracy)` 现在只负责上游 `shootFromRotation` 的事：
   `f5=-sin(yaw)cos(pitch)`、**`f6=-sin((pitch+roll)·deg)`**、`f7=cos(yaw)cos(pitch)` → `applyShoot` →
   末尾继承射击者速度。顺带修掉一处同方法内的偏离：原实现把 `pZ`（roll）丢掉了，y 分量少了 `+pZ`
   （上游 `Projectile.java:135`；当前所有调用点传 `0.0F`，故无行为变化，但参数语义现在与上游一致）。
3. `shoot(pitch, yaw, velocity, Vector2d)` 也改走同一个 helper（`inaccuracy = 0`），两个重载不再各写一份
   「归一化 + 写 motion/rotation」——数值上与改前逐位等价。

**与 r7-api 的并发编辑**：同一文件同时在改（`AmmoHitBlockEvent` 构造点、`ITargetEntity` 派发）。我在改前
重读了工作区最新内容再落笔，改后复核：他们那两处仍在（`:670` 四参数构造、`:474-478` 单次派发），
我的 6 处也在；`EntityKineticBullet` lint error = 0。

**验证（实机）**：同一把枪（如 AK47）连发 30 发的弹着散布直径应与上游观感一致（改前偏小）；
`calcSpread` 脚本返回 `vector2d` 的枪（走 `Vector2d` 重载）散布不受影响，应与改前完全一致。

### 9-5 `AmmoHitBlockEvent` 形状回归后的 scope 内复核（r7-api 改，我只复核、未改文件）

r7-api 按 Lead 裁决把 `api/event/server/AmmoHitBlockEvent` 恢复成上游形状
`(World level, MovingObjectPosition hitResult, Block state, EntityKineticBullet ammo)`，
并在构造时快照 `metadata`（平台附加 `getMetadata()`，`AmmoHitBlockEvent.java:32-42`）；
`EntityKineticBullet` 的构造点随之变为 `new AmmoHitBlockEvent(this.worldObj, result, hitBlock, this)`（`:670`）。

`event/ammo/**` 属我的 scope，事件形状变化后我重新核对了两个监听器，**与上游语义仍然一致**：
- `event/ammo/DestroyGlassBlock`：`AmmoConfig.DESTROY_GLASS` → 硬度阈值
  （`hardness < 0 || > DESTROY_BLOCK_HARDNESS_THRESHOLD`）→ `AmmoBlockConfigRead.canDestroy(block, metadata)`
  的判定链与上游 `DestroyGlassBlock.java:17-30` **逐行一致**；`level.destroyBlock(pos, false, owner)` 的 1.7.10
  等价仍是 `setBlockToAir` + `playAuxSFX(2001, …)`（无掉落 + 破坏特效）。owner/统计差异未变（TODO 仍登记）。
- `event/ammo/BellRing`：1.7.10 无钟方块，保持空监听器（§5-D8）。

三个文件 `lint_files(min_severity=error)` 均 0 error。

### 9-6 P2 · 服务端趴下状态没写进姿势覆盖表（r7-input 发现 → Lead 转派）

**先纠正一处事实（影响症状描述，不影响修法必要性）**：`item/ModernKineticGunScriptAPI` **没有**
`isCrawling()` 方法，本工程与上游也**都不存在** `is_crawling` 这个 Lua 键
（全仓 grep：`isCrawling|is_crawling` 只命中 `LocalPlayerCrawl`（客户端状态机）、
`ShooterDataHolder#isCrawling`、`IClientPlayerGunOperator#isCrawl()`、mixins）。
Lead 指的 `ModernKineticGunScriptAPI.java:291-292` 实际是 **`applyRecoilPush`**：

```java
boolean crawling = EntityStateHelper.getPose(player) == EntityPose.SWIMMING;   // :292
boolean onGround = player.onGround || crawling;                               // :293
double strength  = recoilPush.getFinalStrength(onGround, sneaking, crawling, bulletAmount); // :295
```
上游对应（`ModernKineticGunScriptAPI.java:226-230`）是
`boolean crawling = !player.isSwimming() && player.getPose() == Pose.SWIMMING;` + `onGround() || crawling`。
⇒ 真正的症状是**服务端「趴下」后坐力系数恒为 false**（趴下不减后坐力、也不按「在地面」算），
而不是某个 Lua 读取恒 false。

**根因**：姿势覆盖表 `EntityStateHelper.ENTITY_POSE_OVERRIDES` 只有客户端 `LocalPlayerCrawl` 在写，
且键是**客户端**的 `EntityPlayerSP`；服务端玩家是另一个实例（`EntityPlayerMP`），
服务端从不写 ⇒ 服务端 `getPose(player)` 永远走「sneaking / 水里疾跑 / STANDING」的推导，拿不到 SWIMMING。
上游没这个断层，因为 1.20 的 pose 是**服务端实体状态**（`Player#setForcedPose` 服务端可写、服务端可读）。

**`EntityStateHelper` 是不是纯客户端结构？—— 不是**（这是 Lead 让我先核对的关键点）：
它在 `com.tacz.guns.mcshim`（common 包，不是 `mixin/client`），实现只是
`Collections.synchronizedMap(new WeakHashMap<Entity, EntityPose>())` + 由
`isPlayerSleeping/isSneaking/isInWater+isSprinting/health` 推导，**不引用任何客户端类**；
现有的服务端读端就是 `applyRecoilPush`。`EntityStateHelper` 自己也写明「crawl 逻辑把自己的状态塞进这张表」。
⇒ 不需要 r7-platform 的 mixin，也不需要「服务端独立 cry 状态字段」：直接把同一张表在服务端写上即可。

**修法**（`entity/shooter/LivingEntityCrawl.java:54-65, 98-116`，与客户端同一写法）：
1. `setCrawling()` 在状态变化时写/清姿势：
   `if (crawling || getForcedPose(shooter) == SWIMMING) setForcedPose(shooter, crawling ? SWIMMING : null);`
   —— 与上游 `LivingEntityCrawl.java:59-66` 的「玩家 `setForcedPose`、只在当前强制姿势就是 SWIMMING 时才清除」
   一致；非玩家走同一条写/清路径（等价上游 `setPose(SWIMMING/STANDING)`，1.7.10 用「移除条目」表达回到推导姿势）。
2. `tickCrawling()` 恢复上游 `:28` 的「姿态被外部改回就退出趴下」检测：
   `poseChanged = shooter instanceof EntityPlayer && getForcedPose(shooter) != SWIMMING`
   （与 `client/gameplay/LocalPlayerCrawl#tickCrawl` 逐行同构），原来是恒 false 的 `TODO(port)`。
3. 类注释重写：删掉「本类不再改动实体姿态」的旧结论（已过时——姿势表现在服务端也写；
   体型/眼高早由 `LivingEntityMixin#tacz$updateCrawlDimensions` 处理）。

**读端核对**：`crawl(true)` 的服务端链路是
`ClientMessagePlayerCrawl` → `IGunOperator.crawl` → `LivingEntityMixin:255` → 本类 `setCrawling`；
`tickCrawling` 由 `LivingEntityMixin:323` 每 tick（`!worldObj.isRemote`）调用。
写表后 `applyRecoilPush` 的 `getPose(player)==SWIMMING` 在服务端为真 ⇒ 与上游一致。

**跨模块发现（`item/**` = r7-item，未改）**：`applyRecoilPush` 的 `crawling` 少了上游的
`!player.isSwimming()` 这一半 —— 1.7.10 的等价物是 `!player.isInWater()`（与
`client/event/CameraSetupEvent:381`、`TickAnimationEvent:79`、`MouseHandlerMixin:166` 的既有写法一致）。
现状：**在水里疾跑**时 `getPose` 也会给 SWIMMING（`EntityStateHelper.java:87-89`），
于是水上游泳会被当成趴下（后坐力系数走 crawl 分支）。建议改为
`!player.isInWater() && getPose(player) == SWIMMING`；另 `sneaking` 用 `player.isSneaking()`
而上游用 `getPose()==CROUCHING`（趴下覆盖期间两者会不同，低优先级）。

**验证（实机）**：S 形跑动 + 趴下开火 → 服务端施加的推挤/后坐力应明显小于站立（改前与站立相同）；
趴下时切枪/离地/死亡应自动退出趴下且姿势表被清（服务端视角再看后续后坐力恢复站立体感）。

**lint**：`LivingEntityCrawl` = `{"items":[]}`（0 error）。
