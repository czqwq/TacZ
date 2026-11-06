# 04 · common gameplay 审查报告（item / entity / event / block / inventory / crafting / init）

> 工作流：`gameplay-review`（task-4）
> 作用域：`src/main/java/com/tacz/guns/{item,entity,event,block,inventory,crafting,loot,particles,sound,advancement,debug,init}/**`（124 个文件）
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`；1.7.10 准绳：`build/rfg/minecraft-src/java/**`
> 证据日志：`build/r7run.log`（12:23 段）、`run/client/logs/fml-client-latest.log`（13:27 段）、`run/client/logs/latest.log`（13:28 崩溃 = Lead 修的 ClientAssetsManager）
> 本轮**未运行任何 gradle**（AGENTS.md）。自查仅用 IDEA MCP：`get_file_problems(errorsOnly)` + `lint_files(min_severity=error)`，7 个改动文件均为 **0 error**。

---

## 附：P0 hotfix（task-8，用户实机崩溃）

**崩溃**：`NPE: … Supplier.get() is null @ LivingEntityAim.tickAimingProgress(LivingEntityAim.java:98)` ← `EntityLivingBase.handler$tacz$onTickServerSide`（服务端每 tick 每玩家必经路径）。

**根因**：`ShooterDataHolder.currentGunItem` 是 `Supplier<ItemStack>`；上游 1.20 的 supplier 恒返回 `ItemStack.EMPTY`（非 null），故上游各处只判 `data.currentGunItem == null`（判的是 supplier 是否设置）。1.7.10 空栈=null，supplier 常是 `() -> shooter.getHeldItem()`，空手/切枪过渡 tick 里 `get()` 返回 null → 紧随的 `.get().getItem()` 必 NPE。

**修复**（作用域 `entity/shooter/**`，11 文件，lint 0 error）：
1. `ShooterDataHolder` 新增 `@Nullable ItemStack getCurrentGunItem()`：supplier 为 null / `get()` 为 null / `getItem()==null` / `stackSize<=0` → 统一归一成 `null`（等价上游 `isEmpty()`）；
2. **22 处** `currentGunItem.get()` 全部改为「局部变量 + 判空」（Aim 5、Bolt 2、DrawGun 3、FireSelect 1、Melee 3、Reload 3、Shoot 2、Unload 1、Crawl 1、GunScriptGuard 1）；
3. `setCurrentGunItem(supplier)` 与 `LivingEntityDrawGun.draw(supplier).gunItemSupplier.get()` 各加 supplier 判空（上游此处为 EMPTY）；
4. 复核：`search_regex("currentGunItem\\.get\\(\\)")` 在 shooter/** 与全 `src/main/java/com/tacz/guns` 均已 **0 命中**，`.get().` 链式解引用 0 命中；mixin `LivingEntityMixin` 的 tick 全链路（reload/aim/sprint/crawl/bolt/melee/speed/heat/shoot/melee/draw 冷却）已逐一核对 null-safe。

---

## 第二轮：同类崩溃专项（task-15）

同一根因的第二轮系统排查：**上游 1.20 的非 null 哨兵（`ItemStack.EMPTY`、数据对象）在 1.7.10 变成 `null`，读取点未判空**。作用域：item/entity/event/block/inventory/crafting/init + loot/particles/sound/advancement/debug。

### 新发现并修复的硬崩溃（1 处）

| 文件:行 | 崩溃条件 | 依据 | 修复 |
|---|---|---|---|
| `event/PlayerRespawnEvent.java:58-61`、`:68-71` | 玩家重生且 `AUTO_RELOAD_WHEN_RESPAWN=true` 时遍历背包每把 `IGun` 并调用 `ModernKineticGunScriptAPI#getGunIndex().getGunData()`。`getGunIndex()` 在「枪械数据未加载 / 未知 gunId / 自定义枪包缺失」时返回 **null**（`ModernKineticGunScriptAPI#initGunItem`：`TimelessAPI.getCommonGunIndex(gunId).orElse(null)`）→ **NPE**；当前 14 个 gun data 反序列化失败的状态下，背包里有这些枪时重生必崩 | 上游 `PlayerRespawnEvent.java:25-29` 同样未判空，但上游「有 IGun 物品必有索引」；1.7.10 会出现「有物品、无索引」。另：上游此处为 `return`（会漏掉后续枪），本移植已是 `continue`，更正确 | 取 `getGunIndex()` 后判空 → `continue` 跳过该物品，并在注释里写明依据；索引存在时行为不变 |

### 逐模式复核结论（**均无剩余硬崩溃**）

| 模式 | 全量命中 | 结论 |
|---|---|---|
| `getHeldItem()` / `getCurrentEquippedItem()` 直接解引用 | 11 处：`ModernKineticGunItem:142`（仅引用比较）、`ModernKineticGunScriptAPI:202`、`EntityKineticBullet:678`、`GunScriptGuard:52`（→`hasGunId` 内判空）、`LivingEntityCrawl:75`、`LivingEntityHeat:30`、`LivingEntitySpeedModifier:66`、`LivingEntityUnload:57`、`AmmoStorageInteractionEvent:51`、`PreventGunClick:36`、`TravelToDimensionEvent:47` | **全部下一行即判空**，无危 |
| `IInventory#getStackInSlot` 解引用 | 8 处：`ModernKineticGunScriptAPI:554`、`AmmoStorageInventory:196`、`GunSmithTableMenu:212`、`AmmoStorageUpgradeRecipe:93/115`、`GunPegboardDyeRecipe:58/85`、`AmmoStorageInventory:357`（实现本身） | 全部先判 `null` / `getItem()`，无危；`api/**` 的 `AbstractGunItem` 四处弹药扫描统一走 `ItemStackNbtHelper.isEmpty`（`ItemStackNbtHelper:60-62` 对 null 安全） |
| `Supplier#get()` 可能为 null | shooter 已由 task-8 修；其余：`ModernKineticGunScriptAPI:225-226` pitch/yaw（调用方 `ClientMessagePlayerShoot:63` 传 `() -> entity.rotationPitch`，基本类型装箱永非 null）、`ShooterDataHolder:150/180`（本轮判空）、`ModRecipe:332`（`recipe == null` 已判）、`SyncedDataKey/ModSyncedEntityData` 默认值 | `ModSyncedEntityData:24-86` 默认值全是 `() -> -1L / 0f / false` 或 `ReloadState::new`，**永不为 null**；`ModCreativeTabs.icon` 可能为 null（`GunItemBuilder.build()` 索引缺失返回 null），但 `RenderItem#renderItemAndEffectIntoGUI`（`RenderItem.java:577`）对 null 栈判空，**不崩**（仅图标退化） |
| `LivingEntityDrawGun.draw(Supplier<ItemStack>)` 链路 | 调用方 `network/message/ClientMessagePlayerDrawGun:37` 传 `() -> inventory.getStackInSlot(selected)`（**空槽=null**） | **task-8 已修**：`draw` 内 supplier/get() 双判空、`setCurrentGunItem` 判空、`postChangeEvent` 仅 `drawnItem != null` 时调用、`ServerMessageGunDraw` 走 `PacketBuffer#writeItemStackToBuffer`（`PacketBuffer.java:124-127` 空栈写 -1）→ 全链路安全 |
| `Optional#get()` 未判 | `LivingEntityAim:69/113/160`、`LivingEntityShoot:95` 等 | 全部紧跟 `isPresent()/isEmpty()` 分支，无危 |
| 可空 `getGunIndex()` 的其它使用者 | `ModernKineticGunItem` 内 12 处 | 11 处显式 `if (gunIndex == null) return`；3 处链式（`defaultTickBolt`/`defaultReloadFinishing`）都在已判空的调用方内 |
| `getTileEntity(..)` / `getItemFromBlock(..)` / `GameRegistry.findItem(..)` 内联解引用 | 0 命中 | 均先落局部变量 + `instanceof` / `== null` |
| `getTagCompound().` / `.copy().` / `getItem().` 链式解引用 | 0 命中（task-8 修复后全仓 `entities+shooter` 亦 0） | — |

### 本轮未改但需登记（非崩溃 / 跨作用域）

- `entity/EntityKineticBullet.java:174` `Objects.requireNonNull(IGunOperator.fromLivingEntity(thrower).getCacheProperty())`：与上游 `EntityKineticBullet.java:178` **逐字一致**（上游视作不变量），不改。
- `entity/sync/core/SyncedEntityData.java:123-145` `set()` 对**未注册 key 抛 `IllegalArgumentException`**（上游同源）；`mixin/common/LivingEntityMixin` 每 tick `setValue(...)`，依赖 `CommonRegistry.commonSetup()` → `ModSyncedEntityData.init()` 已完成。当前时序正确（init 早于玩家 tick），仅登记。
- `network/message/**`、`client/**`、`api/**` 只读核查，未改（非本作用域）。

---


## 0. 交付摘要

| 项 | 数量 |
|---|---|
| 已修复 bug（本作用域） | **8** |
| 其中「道具/玩法完全不工作」级 | 3（子弹不移动、枪械背包冷却反了、击杀事件不触发） |
| 已记录降级（上游有、1.7.10 无法表达） | 见 §3 |
| 未修但需决策/跨作用域 | 见 §4 |

改动文件（7 个，全部在本作用域内）：

1. `item/ModernKineticGunItem.java` — 背包冷却判定反转 + 占位图标缺失贴图
2. `entity/EntityKineticBullet.java` — **子弹位移缺失**（致命） + 击杀判定 `isDead`→`!isEntityAlive()`
3. `entity/TargetMinecart.java` — 补上游 `sized(0.75, 2.4)`（命中盒）
4. `init/ModRecipe.java` — 自研 `IRecipe` 注册进 `RecipeSorter`
5. `init/ModEntities.java` — trackingRange 单位错（区块当方块）
6. `init/ModCreativeTabs.java` — 9 个枪械/配件标签页标题 key 缺失前缀
7. `inventory/GuiHandler.java` — `getClientGuiElement` 返回 null → 接上两个已完成的 GuiContainer

---

## 1. 已修复 bug（发现表）

| # | 文件:行（修复前） | 症状 / 影响 | 依据 | 修复 |
|---|---|---|---|---|
| G1 | `entity/EntityKineticBullet.java:276`（`onUpdate`） | **子弹永远停在枪口**：`onUpdate` 里注释「Entity#onUpdate 已经按 motion 移动过」是错的，子弹只做射线判定不改坐标 → 每次 tick 从同一原点短射线，打不中远处目标、飞不到墙边；`EntityUtil.getEntitiesOnPath` 用 `boundingBox` 搜索，盒也永远不动 | `Entity.java:384-387`（`onUpdate` → `onEntityUpdate()`，只记 prev 位姿/火焰/传送门，**不移动**）；原版 `EntityArrow#onUpdate` 自己 `setPosition`；上游 `EntityKineticBullet.java:277-280` `this.setPos(nextPosX, nextPosY, nextPosZ)` | 在 `onUpdate` 里按上游顺序补 `setPosition(posX+motion…)`（位置更新放在 `onBulletTick` 与旋转之后、摩擦/重力之前，与上游一致）；水中气泡粒子改用 `nextPos`（同上游） |
| G2 | `item/ModernKineticGunItem.java:123`（`onUpdate` 守卫） | **热度过热永远不恢复 / 手持枪被双重冷却**：`if (… stack != player.getHeldItem()) return;` 判断反了，`inventoryCoolingPerTick` 只作用于**手持**枪，背包里的枪热量永不下降（`isOverheatLocked` 永不复位） | 上游 `ModernKineticGunItem.java:69-73`：`stack == player.getMainHandItem()` 时 **return**（手持由 `LivingEntityHeat#tickHeat` 每 tick 结算）；`entity/shooter/LivingEntityHeat.java:29-34` 证实了这条分工 | `stack != held` → `stack == held`，并在方法注释里写明「只冷却没拿在手上的枪」 |
| G3 | `entity/EntityKineticBullet.java:514` | **击杀事件永不触发**：用 `livingCore.isDead` 判致死，但 1.7.10 的 `Entity#isDead` 要等死亡动画结束（20 tick）才置位 → `EntityKillByGunEvent` / `ServerMessageGunKill` 永远走不到 else 分支，击杀特效/统计/Kill 事件监听全部失效 | 上游用 `living.isDeadOrDying()`（`isDead() \|\| health<=0`）；1.7.10 `onDeath` 只置 `EntityLivingBase.dead`（`EntityLivingBase.java:996`），`setDead()` 在 `onDeathUpdate` 20 tick 后（`:356-374`） | 改为 `!livingCore.isEntityAlive()`（= `isDead \|\| getHealth()<=0`，与上游 `isDeadOrDying()` 等价） |
| G4 | `init/ModRecipe.java:53`（`register()`） | `r7run.log:  Unknown recipe class! com.tacz.guns.crafting.AmmoStorageUpgradeRecipe / GunPegboardDyeRecipe Modder please refer to net.minecraftforge.oredict.RecipeSorter`；两条自研配方优先级 0 → 排序时排到所有已登记配方**之后**（原版 shaped 先命中策略失效），且日志刷警告 | `RecipeSorter.java:192-218`（`getPriority` 未登记 → 警告 + 0）；排序只在 `FMLLoadCompleteEvent` 跑一次（`ForgeModContainer.java:295-300`）；登记必须在 init 阶段（`CommonProxy#init` 调 `ModRecipe.register()`） | 新增 `registerRecipeSorters()`：upgrade=SHAPED `after:minecraft:shaped`、dye=SHAPELESS `after:minecraft:shapeless`（依赖串取自 `RecipeSorter.java:115-124` 的既有约定） |
| G5 | `init/ModEntities.java:23-24` | **曳光弹/标靶车飞出 5 格就消失**：把 1.20 `clientTrackingRange(5/8)`（单位=**区块**）直接当 1.7.10 的**方块**数用；1.7.10 `EntityTracker` 的 range 是方块，超出即停止同步（tracker entry 被移除） | `EntityTracker.java:89-93`（原版箭 64 格/20tick/不发速度）、`:135-138`（矿车 80 格/3tick/发速度）；上游 `EntityKineticBullet.java:90` `clientTrackingRange(5)`、`TargetMinecart.java:42` `clientTrackingRange(8)` | bullet 5×16=**80** 格、minecart 8×16=**128** 格（1 区块=16 格换算）；minecart `updateFrequency` 1→3（1.20 `EntityType.Builder` 默认 updateInterval=3，与 1.7.10 原版矿车一致） |
| G6 | `entity/TargetMinecart.java:53-59` | 标靶车命中盒是原版矿车的 0.98×0.7，而上游是 0.75×**2.4**（细高靶面）→ 子弹命中判定区域明显偏小/偏矮 | 上游 `TargetMinecart.java:41` `.sized(0.75F, 2.4F)`；1.7.10 `EntityMinecart.java:70-71` 默认 `setSize(0.98F,0.7F)` + `yOffset=height/2` | 两个构造器补 `setSize(0.75F,2.4F)` + `yOffset=height/2`（沿用原版约定）。**中风险**，见 §4 R3 |
| G7 | `init/ModCreativeTabs.java:142-173`（`TaczTab`） | 9 个枪械/配件标签页标题显示 `itemGroup.tacz.type.rifle.name` 原文：1.7.10 `getTranslatedTabLabel()` 默认返回 `"itemGroup." + label`，而 `tacz.type.*.name` 在枪包 lang 里**不带** `itemGroup.` 前缀 | `CreativeTabs.java:186-189`（默认实现）；`GuiContainerCreative.java:433/750/913` 用 `I18n.format(getTranslatedTabLabel())`；lang 实际键：`assets/tacz/lang/en_US.lang:71-72`（`itemGroup.tab.tacz.*`）vs `custom/tacz_default_gun/assets/tacz/lang/en_US.lang:12-20`（`tacz.type.*.name`） | `TaczTab` 记录原始 label 并覆写 `getTranslatedTabLabel()`：`tab.*` 前缀补 `itemGroup.`，其余原样返回 |
| G8 | `item/ModernKineticGunItem.java:117` | `domain minecraft is missing 1 texture: textures/items/bow.png`（MISSING_ICON 紫黑图标）——1.7.10 原版**没有** `bow.png` | `ItemBow.java:159-161` `registerIcon(getIconString() + "_standby")`；jar 内只有 `bow_standby.png` / `bow_pulling_*`（Lead 已核对） | 改 `minecraft:bow_standby`，注释写明 1.7.10 原版贴图清单差异 |
| G9 | `inventory/GuiHandler.java:93-101` | 工作台/弹药库**界面打不开**（客户端 `getClientGuiElement` 返回 null）：服务端开了容器、客户端没有 GUI（`FMLNetworkHandler` 客户端分支 `showGuiScreen(null)`），界面功能等于不可用 | `FMLNetworkHandler.java:openGui`（服务端只发 windowId + `FMLMessage.OpenGui`，客户端走 `getClientGuiElement`）；`client/gui/{AmmoStorageScreen,GunSmithTableScreen}.java` 已完成且构造签名 `(Menu, InventoryPlayer, IChatComponent)`；`client/init/ModContainerScreen.java:28-32` 明确把该 TODO 交给本作用域 | 按 guiId 返回 `new GunSmithTableScreen(new GunSmithTableMenu(inv, 客户端 TE 的 blockId), …)` / `new AmmoStorageScreen(new AmmoStorageMenu(inv), …)` |

### 顺带核对的另一项 Lead 转派项（无 bug）

- `AmmoItem` → `minecraft:arrow`、`AttachmentItem` → `minecraft:compass`：jar 内 `assets/minecraft/textures/items/{arrow,compass}.png` 均存在 ✔
- `AmmoBoxItem` → `tacz:ammo_box`、`TargetMinecartItem` → `tacz:target_minecart`：`assets/tacz/textures/items/{ammo_box,target_minecart}.png` 均存在 ✔（`textures/items/` 实际内容：ammo_box、target_minecart、target、statue、3 组 ring）

---

## 2. 逐包核对结论（无 bug 但已读过关键路径）

| 包 | 核对内容 | 结论 |
|---|---|---|
| `item/` | `ModernKineticGunItem` 全量方法（开火/换弹/拉栓/近战/射击模式/热量）、`ModernKineticGunScriptAPI#shootOnce`/`reduceAmmoOnce` 逐行对照上游、`AmmoItem` 堆叠上限走 `getItemStackLimit(ItemStack)`（`ItemStack.java:212-215` 确实走 Forge 版本）、`AmmoBoxItem`/`AttachmentItem`/`AmmoStorageBlockItem`/`GunPegboardItem` 图标与 NBT | `fireSelect` 的 `fireModeSet.indexOf+1 % size` 除零与上游同源（保留上游行为）；`reduceAmmoOnce` 的三段 bolt 分支与上游 `ModernKineticGunScriptAPI.java:177-210` 完全一致 |
| `entity/` | `EntityKineticBullet`（伤害衰减 `getDamage`、穿透排序、爆头、点燃、爆炸、`writeSpawnData`/`readSpawnData` 字段序）、`shooter/**` 13 个类、`sync/**` | spawn 数据写入/读取字段顺序与类型**逐项一致**（20 个字段）；`LivingEntityShoot` 的 tick 窗口/蓄力校验与上游同型；`TargetMinecart#onProjectileHit` 用 `source.getEntity()` 取射手（`EntityDamageSourceIndirect.getEntity()` 返回 thrower，正确） |
| `block/` | 6 个方块实体方块的 `hasTileEntity`/`createTileEntity` 全覆盖；`AmmoStorageBlock` 的 `breakBlock` 掉落写 `BlockEntityTag` + `getDrops` 返回空 + 创造模式抑制位；`TargetBlock#onBulletHit` 的朝向判定；`StatueBlock` 上下半拆解 | `TargetBlock:297` `hitFace.getOpposite() != FACING` 与上游 `TargetBlock.java:120-122` `hit.getDirection().getOpposite().equals(FACING)` **一致**，非 bug |
| `inventory/` | `AmmoStorageInventory` 的 NBT 键（`Slot`/`Stack`/`Count` 与上游同键）、`AmmoStorageMenu` 进度条同步（capacity/used）、`GunSmithTableMenu` 无槽位+材料从背包扣 | `AmmoStorageMenu`/`GunSmithTableMenu` 都按 `@Nullable` 数据源设计，客户端可空构造 —— 与 G9 的修复吻合 |
| `crafting/` | `AmmoStorageUpgradeRecipe`（3×3、中心源方块、复制 `BlockEntityTag`）、`GunPegboardDyeRecipe`（`display.color`、`fleeceColorTable` 调色、`getRecipeOutput()=null`） | `getRecipeOutput()` 返回 null 与 1.7.10 原版 `RecipesArmorDyes.java:152-155` 同型，安全 |
| `init/` | 注册顺序（`CommonProxy.preInit`：ModBlocks→ModCreativeTabs→ModItems→ModEntities→ModContainer→ModSounds→ModAttributes→ModParticles→ModLootModifiers）、`ModItems` 用 `Item.getItemFromBlock` 取回方块物品、`GunItemManager.registerGunItem` 在尾部 | 顺序正确；`ModRecipe.register()` 在 `init` 阶段（矿词已注册）正确；声音名 `tacz:gun` / `tacz:target_block_hit` 与 `SoundHandler.java:90`（`new ResourceLocation(domain, sounds.json key)`）的寻址规则匹配 ✔ |
| `event/` | `EventHandlers.registerAll()` 的 12 个监听器分总线情况（Forge bus vs `FMLCommonHandler.bus()`） | 与各类自身 `register()` 一致；`ChangeGunPropertyEvent` 属「被 post 的自定义事件」，无需注册 |

---

## 3. 已记录降级（上游行为在 1.7.10 无法表达，**非 bug**，不重复记录明细，仅索引）

| 项 | 位置 | 说明 |
|---|---|---|
| 穿甲/普通两种伤害源退化为同一 `causeThrownDamage` | `entity/EntityKineticBullet.java` 类注释 + `createDamageSources` | 1.7.10 无数据包 damage_type；`armorIgnore` 仍按两段伤害拆分（`tacAttackEntity`：普通 + 穿甲比例），只是伤害源类型相同 |
| 末影人/实体标签硬编码 | 同上 | `use_magic_damage_on` 等 tag 无 1.7.10 对应物 |
| `tacz:bullet_ignore` 方块标签缺失 | 同上 | 1.7.10 无 tag 系统 |
| actionbar 提示 → 聊天栏 | `TargetMinecart.java:86`、`TargetBlock.java:311` | `displayClientMessage(..., true)` 无对应物 |
| 炼药锅染色（挂板） | `init/CommonRegistry.java:88-95` | `CauldronInteraction` 注册表不存在 |
| 药水 id → 原版 Potion 映射 | `ModernKineticGunItem#getPotionFromId` | 自定义 MobEffect 无法表达；未命中返回 null 跳过 |
| 枪械/配件/弹药 2D 图标为原版占位（bow_standby / arrow / compass） | `ModernKineticGunItem`/`AmmoItem`/`AttachmentItem` | 上游走 3D BEWLR，1.7.10 需 `IItemRenderer`（L2）；此图标只用于背包/JEI |
| 服务端射击 tick 窗口按固定 50ms 估算 | `LivingEntityShoot.java:104-119` | 1.7.10 `MinecraftServer` 不记每 tick 耗时 |
| `user.doEnchantDamageEffects` 缺失 | `ModernKineticGunItem#doPerLivingHurt` | 1.7.10 `attackEntityFrom` 内部已应用附魔 |
| `setBlockTextureName("tacz:block/xxx")` 与镜像目录不一致 | `block/{Statue,Target,AmmoStorage,GunPegboard,GunDisplayCase}Block` | 目前**无实际影响**：方块图标一律由 `registerBlockIcons()` 注册为 `tacz:xxx`（→ `assets/tacz/textures/blocks/xxx.png`，asset-port 已镜像，存在）。`setBlockTextureName` 只是语义残留；若将来有代码走 `Block#getTextureName()`（`Block.java:1458-1461`，返回的就是 `tacz:block/xxx` → `textures/blocks/block/xxx.png`，**不存在**）会重新出现 12:23 那 9 个缺失贴图。建议统一改成 `tacz:xxx`（本轮未改，避免与 asset-port 作用域交叉） |

---

## 4. 未修风险 / 跨作用域待办

| # | 风险 | 说明与建议 |
|---|---|---|
| R1 | **G9 引入 `inventory → client` 编译耦合** | `GuiHandler` 现在 import `client/gui/{AmmoStorageScreen,GunSmithTableScreen}`。这正是 1.7.10 的常规写法（`IGuiHandler` 的客户端分支才调用），但**客户端作用域若改这两个类的构造器签名会直接编译失败**。请 Lead 在最终构建前确认签名仍为 `(Menu, InventoryPlayer, IChatComponent)`；若要彻底解耦，可改为 `ClientGuiRegistry` 反射注入（成本更高） |
| R2 | 客户端侧缺失的必同步字段 | `EntityKineticBullet` 的 `armorIgnore / headShot / knockback / damageAmount / distanceAmount / explosionDelayCount` 不进 `writeSpawnData`（上游走 `SynchedEntityData`）。服务端判定不受影响；客户端伤害数字/曳光参数走网络消息，若发现客户端数值与服务器不一致，优先看这里 |
| R3 | G6（标靶车 2.4 高命中盒）**中风险** | 1.7.10 矿车会参与方块碰撞（`noClip=false`），2.4 高的实体在低矮空间/隧道内可能被挤住或无法被推动。上游 1.20 同样 2.4，属上游保真；若实机出现矿车卡住，回退为 `setSize(0.75F, 1.2F)` 之类的折中并记录 |
| R4 | `ModernKineticGunScriptAPI.java:198` `shooter.isDead` | 同 G3 的同类漂移（上游 `isDeadOrDying()`）。影响极小（死亡后最多 20 tick 内的 cycle task 不中止），未改，避免和 G3 之外的语义扩张 |
| R5 | `GunPackResourcePack` / 枪包数据加载 | 14 个 gun data 的 `EnumMap` 反序列化失败 + 客户端资源未加载属 Lead 正在修的 `resource/CommonAssetsManager` / `EnumMapTypeAdapterFactory` / `ClientAssetsManager`，本作用域不触碰；本报告的所有枪械行为修复都建立在「gun index 能被加载」之上 |
| R6 | `docs/PORT_STATUS.md` 的 `general-render` 侧条目 | 未改动（规则要求） |

---

## 5. 覆盖清单（124 文件 ÷ 逐包）

| 包 | 文件数 | 覆盖方式 | 深读（全读/逐行对照上游） | 速览（关键方法/类注释核对） |
|---|---|---|---|---|
| `item/` | 12 | 全量 | `ModernKineticGunItem`（765L）、`ModernKineticGunScriptAPI`（`shootOnce`/`reduceAmmoOnce` 段）、`AmmoItem` | `AmmoBoxItem`、`AttachmentItem`、`AmmoStorageBlockItem`、`GunPegboardItem`、`DefaultTableItem`、`GunSmithTableItem`、`GunTooltipPart`、`ItemClientHooks`、`TargetMinecartItem` |
| `entity/` | 27 | 全量 | `EntityKineticBullet`（923L 全读）、`TargetMinecart`（全读）、`LivingEntityShoot`（全读）、`LivingEntityHeat`、`EntityUtil` 契约 | `shooter/**` 其余 12 类、`sync/**` 8 类、`EntityBulletClientHooks` |
| `event/` | 19 | 全量 | `EventHandlers`、`PreventGunClick` | 其余 17 类（注册总线/语义） |
| `block/` | 24 | 全量 | `AmmoStorageBlock`（右击/破坏/掉落/创造抑制）、`StatueBlock`、`TargetBlock#onBulletHit`、`AmmoStorageBlockEntity` | `GunPegboardBlock`、`GunDisplayCaseBlock`、`AbstractGunSmithTableBlock`、`GunSmithTableBlock{A,B,C}`、其余 block entity + SlotHelper/PegboardShape/BlockMetadata |
| `inventory/` | 11 | 全量 | `GuiHandler`（全读+改）、`AmmoStorageMenu`、`GunSmithTableMenu` | `AmmoStorageInventory`（NBT 段）、`AmmoStorageEntry`、`GuiId`、5 个 tooltip |
| `crafting/` | 8 | 全量 | `AmmoStorageUpgradeRecipe`、`GunPegboardDyeRecipe` | `GunSmithTableRecipe`/`Serializer`/`Ingredient`、`result/*` |
| `init/` | 16 | 全量 | `ModRecipe`、`ModEntities`、`ModBlocks`、`ModItems`、`ModCreativeTabs`、`CommonRegistry`、`ModSounds` | `ModAttributes`、`ModDamageTypes`、`ModParticles`、`ModLootModifiers`、`ModPainting`、`ModContainer`、`CommandRegistry`、`CompatRegistry`、`CapabilityRegistry` |
| `loot/` `particles/` `sound/` `advancement/` `debug/` | 1+2+1+2+1 | 全量 | — | `LootTableInjectorModifier`、`BulletHoleOption`、`SoundManager`、`GunRefitTrigger`/`TargetHitTrigger`、`GunMeleeDebug` |

（另：为核对契约读了作用域外的 `util/EntityUtil.java`、`api/item/gun/AbstractGunItem.java`、`CommonProxy.java`、`client/init/ModContainerScreen.java`、`client/gui/*Screen` 构造签名 —— **只读，未改**。）

---

## 6. 日志对照（本作用域相关）

| 日志行 | 归因 | 状态 |
|---|---|---|
| `Unknown recipe class! com.tacz.guns.crafting.AmmoStorageUpgradeRecipe` / `GunPegboardDyeRecipe` | `RecipeSorter` 未登记 | **已修**（G4） |
| `domain minecraft is missing 1 texture: textures/items/bow.png` | `ModernKineticGunItem` 占位图标 | **已修**（G8） |
| `DOMAIN tacz is missing 9 textures: textures/blocks/block/*.png`（12:23 旧构建） | 旧构建未覆盖 `registerBlockIcons`；13:27 构建已消失 | 已随 `block/**` 的 icon 注册解决；残留隐患见 §3 末行 |
| `Failed to load data file tacz:xxx_data … EnumMap … LinkedHashMap`（14 条） | gson 2.2.4 vs `EnumMap`（`resource/**`） | Lead 负责 |
| `NullPointerException … PackInfoManager.getData … this.packInfo is null`（13:28 崩溃） | `client/resource/ClientAssetsManager`（客户端资源从未加载） | Lead 负责 |
| `原版材质配方注册失败，已跳过该批次` + `NullPointerException`（12:23） | `ModRecipe` preInit 阶段用 String 矿词（已由「配方移到 init 阶段 + `ShapedOreRecipe`」修掉） | 已在既有实现中解决，核对无回归 |
