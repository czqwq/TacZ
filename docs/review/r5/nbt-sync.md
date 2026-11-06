# r5-A · NBT / 存读对称性与 item-entity-block-inventory-crafting 审查

审查人：`r5-nbt-sync`（task-1）
审查范围：`item/**`、`entity/**`、`block/**`、`inventory/**`、`crafting/**`、`loot/**`、`particles/**`、
`api/item/nbt/**`、`resource/serialize/**`（共 107 个 java 文件）
上游对照：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`
1.7.10 依据：`build/rfg/minecraft-src/java/**`（本文所有原版结论都标了该目录下的 file:line）
**未运行 gradle**；改动文件已用 `lint_files(min_severity="error")` 检查，0 error。

---

## 1. 结论速览

| 项 | 结论 |
|---|---|
| NBT **键名/类型** 层面 | **基本健康**：全仓 190+ 处 NBT 读写聚合后，除 `GunLevelExp`（上游同为「只读，写端留待 future」）外，**每一个 key 都有读端和写端**，没有发现键名拼写不一致或 `setInteger` 配 `getString` 的类型错配。 |
| NBT **语义** 层面 | **发现 3 处真问题**（1 个 P0 影响主玩法、1 个 P1 丢物品、1 个 P2 丢自定义名），已全部修好。 |
| 实体 spawn data | `EntityKineticBullet.writeSpawnData/readSpawnData` **逐字段对称**（20 个字段、顺序一一对应：`rotationPitch/Yaw`、`motionX/Y/Z`、`ownerId`、`ammoId`、`gravity`、`explosion`、`igniteEntity`、`igniteBlock`、`explosionRadius`、`explosionDamage`、`life`、`speed`、`friction`、`pierce`、`isTracerAmmo`、`gunId`、`gunDisplayId`）。未下发的 14 个字段全是服务端专用（`damageAmount`/`armorIgnore`/`headShot`/`startPos`/`explosionDelayCount`/`igniteEntityTime` 等，`onBulletTick` 客户端直接 `return`）或客户端渲染自用（`cameraXRot/YRot`、`firstPersonRenderOffset`）。`writeEntityToNBT/readEntityFromNBT` 空实现与上游 `noSave()` 一致。`TargetMinecart` 上游也无自定义 NBT，一致。 |
| TileEntity 存读 | `AmmoStorageBlockEntity` / `GunSmithTableBlockEntity` / `TargetBlockEntity` / `StatueBlockEntity` / `GunPegboardBlockEntity` / `GunDisplayCaseBlockEntity` 的 `writeToNBT/readFromNBT` 与 `getDescriptionPacket/onDataPacket` **全部成对且键一致**；Pegboard 额外正确处理了「网络更新不下发 `SlotShape`」。 |
| 容器增量同步 | `AmmoStorageMenu.detectAndSendChanges` 双通道（`sendProgressBarUpdate` 同步标量 + `ContentSender` 钩子同步条目列表）与 `updateProgressBar` **双向对上**；`GunSmithTableMenu` 无槽位（与上游一致），无需同步。 |
| 配方 / 序列化 | `GunSmithTableResult.fromJson` 读 `type`/`count`/`nbt`/`group`/`id`/`item` 六个字段，全部有消费方；`RawGunTableResult.init` 逐 key 合并 `nbt`；`GunSmithTableIngredientSerializer` 的 `item`/`tag` 两条路径都能解析真实数据（已核对 `assets/tacz/custom/tacz_default_gun/data/tacz/recipes/**` 的实际 JSON 结构）。 |
| 最严重的 3 条 | ① 工作台**每个配方只能合成一次**（产物 ItemStack 被 `splitStack` 拆空且未复制）；② 破坏**雕像**会连带把展示的枪永久销毁（缺 `breakBlock`）；③ 破坏**标靶**丢失自定义名称（`getDrops` 里读已被移除的方块实体）。 |

---

## 2. NBT 对账表

聚合方法：把 `src/main/java` 下所有 `setXxx(key, ...)` / `getXxx(key, ...)` 的 key 实参（含常量解析）
归一成 key 字符串后统计读写端。**`set`/`get` 计数都是 0 或只有单侧的 key 全部列在下表**；
原版保留键（`display`/`color`/`Damage`/`id`/`Count`/`tag`/`HideFlags`）不计。

| NBT key | 写端 file:line | 读端 file:line | 上游两侧都有？ | 结论 |
|---|---|---|---|---|
| `GunId` | `api/item/nbt/GunItemDataAccessor.java:125` | `:115` | 是 | 正常 |
| `GunDisplayId` | `:144` | `:134` | 是 | 正常（`setGunDisplayId` 全仓 0 调用点，上游同样 0） |
| `GunFireMode` | `:201`,`:204` | `:192` | 是 | 正常 |
| `GunCurrentAmmoCount` | `:219` | `:211` | 是 | 正常（7 处调用点） |
| `HasBulletInBarrel` | `:390` | `:382` | 是 | 正常（7 处调用点） |
| `Attachment<Type>` | `:333`,`:345` | `:239`,`:281` | 是 | 正常（`installAttachment`/`unloadAttachment` 成对，卸载写 `id=0` 占位） |
| `AttachmentLock` | `:107` | `:99` | 是 | 正常 |
| `DummyAmmo` / `MaxDummyAmmo` | `:60`,`:74` / `:92` | `:54` / `:86` | 是 | 正常（`setMaxDummyAmmoAmount` 0 调用点，上游亦然） |
| `HeatAmount` | `:445` | `:438` | 是 | 正常（射击/冷却/背包冷却三条路径都在写） |
| `OverHeated` | `:432` | `:426` | 是 | 正常（`ModernKineticGunScriptAPI:332` 置 true，两处冷却路径置 false） |
| `LaserColor`（枪） | `:411` | `:405` | 是 | 正常 |
| `GunLevelExp` | **无写端** | `:152`,`:161` | **上游同样无写端** | 仅标记：等级系统上游未落地（`getLevel(int)`/`getMaxLevel()` 上游也恒 0），非移植缺陷 |
| `AmmoId`（弹药/弹药盒） | `AmmoItemDataAccessor:38,41` / `AmmoBoxItemDataAccessor:33` | `:28` / `:25` | 是 | 正常 |
| `AmmoCount` | `AmmoBoxItemDataAccessor:52,55` | `:43` | 是 | 正常（creative 时强制 `Integer.MAX_VALUE`，读写两侧同规则） |
| `Creative` / `AllTypeCreative` | `:131` / `:124` | `:102` / `:111` | 是 | 正常（互斥写入） |
| `Level`（弹药盒） | `AmmoBoxItemDataAccessor:85` | `:93` | 是 | 正常 |
| `AttachmentId` | `AttachmentItemDataAccessor:66`（另 `util/datafixer/AttachmentIdFix:64` 修旧值） | `:35` | 是 | 正常 |
| `Skin` / `ZoomNumber` | `:84` / `:52`（另 `entity/shooter/LivingEntityAim:92` 走 `setZoomNumberToTag` 滚档） | `:75` / `:46` | 是 | 正常 |
| `LaserColor`（配件） | `:120` | `:114` | 是 | 正常 |
| `BlockId` | `BlockItemDataAccessor:36,39` + `GunSmithTableBlockEntity:93` | `BlockItemDataAccessor:26` + `GunSmithTableBlockEntity:99` | 是 | 正常（物品侧 ↔ 方块实体侧**双向**：`onBlockPlacedBy` 写入、`breakBlock` 写回掉落物） |
| `Entries`（弹药库） | `AmmoStorageBlockEntity:148` | `:141` + `client/event/TooltipEvent:379` | 是 | 正常 |
| `Slot` / `Stack` / `Count`（弹药库条目） | `AmmoStorageInventory:242,244,245`（byte/compound/long） | `:262,270,271` + `TooltipEvent:386,387`（同类型） | 是 | 正常；`Slot` 用 `setByte`、`getInteger` 读，1.7.10 数值 tag 互转安全（`NBTTagCompound.getInteger` 对任意 `NBTPrimitive` 生效，`build/rfg/minecraft-src/java/net/minecraft/nbt/NBTTagCompound.java:224-234`） |
| `BlockEntityTag`（掉落物携带整份方块实体 NBT） | `AmmoStorageBlock:209`、`AmmoStorageUpgradeRecipe:128` | `AmmoStorageBlockItem:92`、`AmmoStorageUpgradeRecipe:130`、`TooltipEvent:375` | 上游用 `BlockItem#get/setBlockEntityData` | 正常（写→放置还原→升级配方搬运→tooltip 预览四条链路都在） |
| `Items` / `Slot` / `Item` / `Rotations`（陈列柜、挂板） | `GunDisplayCaseBlockEntity:234,235,239,240`、`GunPegboardBlockEntity:356,357,361,362` | 同文件 `:254,256,261` / `:382,384,389` | 是 | 正常 |
| `Color` / `InheritedColor` / `SlotShape`（挂板） | `GunPegboardBlockEntity:347,350,343` | `:370,371,394` | 是 | 正常（`SlotShape` 只在存档路径读，网络包显式 `removeTag`，与上游 `getUpdateTag` 一致） |
| `Item`（雕像） | `StatueBlockEntity:131` | `:136` | 是 | 正常 |
| `Owner` / `CustomName` / `OutputPower`（标靶） | `TargetBlockEntity:216,219,222` | `:228,242,244` | 是（`OutputPower` 为移植新增） | 正常（存档 + 网络更新共用 `writePayload/readPayload`） |
| `Owner`/`Name`/`Id`（`GameProfile` 子键） | `TargetBlockEntity:208,211` | `:229,233` | 上游用 `NbtUtils.read/writeGameProfile` | 正常（1.7.10 无 `NbtUtils`，手写 name/uuid 两字段，坏 uuid 不阻塞存档） |
| `StateType` / `CountDown`（`ReloadState` 同步） | `ModSerializers:51,55` | `:63,64` | 是 | 正常（网络版 `write/read` 也成对，且越界 ordinal 归一为 NOT_RELOADING 时**先读满 countDown** 保证字节对齐） |
| `Most` / `Least`（UUID 同步） | `Serializers:281,282` | `:289` | 是 | 正常 |
| `SlotAdapters.<Type>` | `util/SlotAdapterHelper:103`（另 `:136` 空容器写回） | `:115`（另 `:131`） | 是 | 正常（`ClientMessageSetSlotAdapter:183` 为唯一写调用点，服务端校验后落盘） |
| `Variant` | `util/VariantHelper:40` | `:53` | 是 | 正常 |
| `VirtualOemAttachment` | `util/VirtualOemAttachment:72` | `:77` | 是 | 正常 |
| `TacAmmoStorage*`（交互节流） | `network/message/ClientMessageAmmoStorageInteract:120,124,152,153,154` | `:92,145,146,150` | 是 | 正常 |
| `tacz:tracer_override` / `tacz:tracer_size` | **无写端** | `EntityKineticBullet:939,971` | **上游同样无写端**（供第三方模组通过 `getPersistentData()` 写入的对外 API） | 仅标记 |
| `HideFlags` | `item/GunTooltipPart:43` | `:35` | 上游 `Item#getDefaultTooltipHideFlags` | 正常 |

### 2.1 五个 DataAccessor 的 getter ↔ setter 调用点对账

「调用点」= 全仓 `src/main/java` 里该 setter 的实参调用处（不含接口声明与默认实现自身）。

| setter | 移植侧调用点 | 上游调用点 | 结论 |
|---|---|---|---|
| `setGunId` | `GunItemBuilder:89,121` | 同 | 正常 |
| `setFireMode` | 6 处（`ModernKineticGunItem:765` 切火模式、各 builder） | 同 | 正常 |
| `setCurrentAmmoCount` | 7 处（`ModernKineticGunScriptAPI:588,591,602,605`、`AbstractGunItem:493`、builder） | 同 | 正常 |
| `setBulletInBarrel` | 7 处（`ModernKineticGunScriptAPI:358,372,628`、builder） | 同 | 正常 |
| `setHeatAmount` | 5 处（射击升温、两条冷却路径、builder） | 同 | 正常 |
| `setOverheatLocked` | 3 处（`ModernKineticGunScriptAPI:332` 置 true；两条冷却置 false） | 同 | 正常 |
| `setLaserColor` | 2 处（枪 / 配件各 1） | 同 | 正常 |
| `setAttachmentLock` | 2 处 | 同 | 正常 |
| `installAttachment` / `unloadAttachment` | 5 / 1 处 | 同 | 正常 |
| `addDummyAmmoAmount` / `setDummyAmmoAmount` | `GunAmmoUnloadHelper:98` / `DummyAmmoCommand:68` + 内部扣减 | 同 | 正常 |
| `setAmmoId` / `setAmmoCount`（弹药盒） | 4 / 7 处 | 同 | 正常 |
| `setAttachmentId` / `setBlockId` / `setAmmoId`（弹药） | 1 / 2 / 1 处（均在 builder） | 同 | 正常 |
| `setCreative` / `setAmmoLevel` | 2 / 3 处（创造物品栏） | 同 | 正常 |
| **`setMaxDummyAmmoAmount`** | **0** | **0** | 仅标记：对外 API，上游同样无处调用 |
| **`setGunDisplayId`** | **0** | **0** | 仅标记（同上） |
| **`setSkinId`** | **0** | **0** | 仅标记（上游由 `AttachmentNbtFactory` 使用，该类未移植） |
| **`setAllTypeCreative`** | **0** | **0** | 仅标记（没有「全类型创造弹药盒」的获取途径，创造物品栏只给了 `setCreative`） |
| **`setZoomNumber(ItemStack)`** | 0，但等价的 `setZoomNumberToTag` 有 1 处（`entity/shooter/LivingEntityAim:92` 滚档写回枪上配件的子 tag） | 同 | 正常（变体写法不同，语义等价） |
| `setExp` / `setLevel`（枪） | **不存在该方法** | **上游也没有** | 仅标记：等级系统两边都未落地 |

**ItemStack 复制路径 NBT 保全核查**（1.7.10 `ItemStack.copy()` 深拷贝 `stackTagCompound`，
`build/rfg/minecraft-src/java/net/minecraft/item/ItemStack.java`）：
合成产物（`GunSmithTableMenu` 修复后 `copy()`）、弹药库入库（`AmmoStorageInventory.single/withCount/moveToInventory`
全部走 `copy()`）、掉落（`EntityItem` 直接持有原栈）、挂板/展示柜/雕像存放（`stack.copy()`）、
拆半数量（`splitStack` 复制 tag）——**没有一条路径会丢 NBT**。

---

## 3. 发现表

| # | 级别 | 现象（玩家可见） | 根因 | 证据（本工程 ↔ 上游） | 状态 | 修法 |
|---|---|---|---|---|---|---|
| 1 | **P0** | 同一张工作台配方**只能合成一次**；第二次做同一把枪/同一批弹药时材料被扣、产物不出现 | `recipe.getOutput()` 返回配方对象持有的**同一个** `ItemStack`，`splitStack()` 会把它拆成 `stackSize = 0`；下一次 `while (result.stackSize > 0)` 直接不成立 | 本工程 `inventory/GunSmithTableMenu.java:246-256` ↔ 上游 `inventory/GunSmithTableMenu.java:157-165`（`result.split(stackLimit)` 同样修改配方持有的栈；**上游同缺陷**） | **已修** | `result = result.copy();`（`:252`），并加一行注释说明「上游同缺陷」 |
| 2 | **P1** | 破坏**雕像方块**时，放在上面的枪**凭空消失**（不是掉在地上） | `StatueBlock` 没有 `breakBlock`/`onRemove` 等价物，`StatueBlockEntity.dropItem()` 只在「空手右键取下」和「放新枪覆盖」时被调用；1.7.10 的方块实体随方块一起被移除，枪没有任何弃置路径 | 本工程 `block/StatueBlock.java`（原无 `breakBlock`）↔ 上游 `block/StatueBlock.java:126-135`（`onRemove` → `statueBlockEntity.dropItem()`） | **已修** | 新增 `breakBlock`：`world.getBlock(x,y,z) != this` 时取 `StatueBlockEntity` 并 `dropItem()`（判据与上游 `!state.is(newState.getBlock())` 等价；1.7.10 `breakBlock` 时机见 `build/rfg/minecraft-src/java/net/minecraft/world/chunk/Chunk.java:653-664`） |
| 3 | **P2** | 用铁砧命名的**标靶**被破坏后，掉落物**丢失自定义名称**（方块实体里存了 `CustomName`，却回不到物品上） | 掉落写在 `getDrops` 里，而 1.7.10 的 `ItemInWorldManager#tryHarvestBlock` **先** `removeBlock`（触发 `breakBlock` 并清掉方块实体）**再** `harvestBlock`（才调 `getDrops`）→ `world.getTileEntity()` 恒为 null | 本工程 `block/TargetBlock.java:383-395`（原 `getDrops`）↔ 上游 loot 表 `data/tacz/loot_tables/blocks/target.json`（`half=lower` + `copy_name`） | **已修** | 掉落搬到 `breakBlock`（方块实体仍在），`getDrops` 返回空表；新增 `suppressNextDrop` + `onBlockHarvested`（创造不掉落）与 `removeWithoutDrop`（「另一半被拆」的连带移除不掉落），与本包 `AmmoStorageBlock`/`GunPegboardBlock`/`AbstractGunSmithTableBlock` 同一套约定 |
| 4 | P2 | —（无玩家可见影响） | `loot/LootTableInjectorModifier` 的 `set_nbt` 函数直接 `setTagCompound(tag)` **覆盖**整个 tag；上游 1.20 `SetNbtFunction` 是 `getOrCreateTag().merge(tag)` 合并 | 本工程 `loot/LootTableInjectorModifier.java:240-243` ↔ 上游 `net.minecraft.world.level.storage.loot.functions.SetNbtFunction#run` | 未修（记录） | 只在「同一条目出现两个 `set_nbt`」时有差异，默认枪包未使用；改动会让「先 set_count 后 set_nbt」的顺序语义变化，留给产品裁决 |
| 5 | P2 | 标靶矿车可被玩家骑乘、轨道最高速未限制 | `TargetMinecart` 缺 `canBeRidden()=false` / `getMaxCartSpeedOnRail()=0.2F` 两个上游覆写；1.7.10 `EntityMinecart` 无对应可覆写点（需 Mixin 或 `interactFirst` 拦截） | 本工程 `entity/TargetMinecart.java` ↔ 上游 `entity/TargetMinecart.java:94-97,156-159` | 未修（跨域：需 Mixin） | 与 `entity/Mixin` 工作流协同；另 `attackEntityFrom` 未按 `doEntityDrops` 判定掉落（上游 `destroy` 有） |
| 6 | P2 | 标靶矿车的 `onProjectileHit` 未校验 `source.isIndirect()` | 上游 `:60-62` 直接 `return`（非间接伤害不触发提示/事件）；移植侧只判 `source.getEntity() instanceof EntityPlayer` | 本工程 `entity/TargetMinecart.java:89-92` ↔ 上游 `:60-64` | 未修（记录） | 子弹伤害源恒为间接（`ModDamageTypes.Sources.bullet`），当前无可见差异；改动风险 > 收益 |
| 7 | P3 | 弹药库 `getInventoryStackLimit()` 固定返回 64，而单格真实上限是「容量×单组大小」 | 1.7.10 `IInventory` 无「按槽动态上限」概念 | 本工程 `inventory/AmmoStorageInventory.java:406-410` ↔ 上游 `IInventory#getSlotLimit` | 未修（已在类注释登记） | 自动化（漏斗）按 64 搬运，精度差已在注释里说明；改需自研 `ISidedInventory` |

> 历史报告（`docs/review/r2/`、`docs/review/r4/`、`docs/PORT_STATUS.md`）里已登记并已修的 NBT/存读问题
> （`EntityKineticBullet` 位移、`TargetMinecart` 3 参构造器尺寸、`AmmoStorageInventory` `hasNumericKey`、
> knockdown 复位、`PegboardBlockEntity` `SlotShape` 网络剔除等）**复核仍在**，本次未重复计入。

---

## 4. 已修清单（逐文件）

1. **`src/main/java/com/tacz/guns/inventory/GunSmithTableMenu.java`**
   - `doCraft(...)` `:246-256`：在 `splitStack` 之前 `result = result.copy()`（`:252`）。
   - 影响：工作台从「每个配方一次」恢复为「可反复合成」。
   - 上游同缺陷已在注释里点名（`GunSmithTableMenu.java:157-165`），不是静默改语义。

2. **`src/main/java/com/tacz/guns/block/StatueBlock.java`**
   - 新增 `breakBlock(World,int,int,int,Block,int)`（`:198-207`）：方块被真正替换掉时把 `StatueBlockEntity` 里展示的枪丢出。
   - 上半块（`isUpper`）没有方块实体，天然不会重复掉落；间接移除（另一半被拆）也走同一路径，与上游 `onRemove` 无条件 `dropItem()` 一致。

3. **`src/main/java/com/tacz/guns/block/TargetBlock.java`**
   - 新增 `suppressNextDrop` 字段（`:87`）、`breakBlock(...)`（`:405-418`）、`onBlockHarvested(...)`（`:421-426`）、`removeWithoutDrop(...)`（`:260-264`）。
   - `getDrops(...)`（`:429-432`）改为返回空表（掉落已在 `breakBlock` 完成，避免二次掉落）。
   - `onNeighborBlockChange(...)` 的两处 `setBlockToAir`（`:239`、`:255`）改用 `removeWithoutDrop`，保证「拆一半连带移除不掉落」与上游 `updateShape` 返回 `Blocks.AIR` 的语义一致，也不会因破上半块而白送一个标靶。
   - 效果：自定义名称（铁砧命名）能随掉落物一起带出来。

4. **`src/main/java/com/tacz/guns/item/ItemClientHooks.java`**（Lead 于本轮转派的跨域小修）
   - 类 javadoc 里「client 侧注入点」原写 `com.tacz.guns.client.ClientProxy#preInit` 或
     `client/init/ClientItemRendererRegistrar#registerAll()`，而后者**类不存在**；改为真实位置
     `com.tacz.guns.client.init.ClientSetupEvent` 的 `registerItemRenderers()`
     （`ClientSetupEvent.java:259-274`，`registerItemRenderer` 钩子在 `:261`）与
     `registerItemNameProviders()`（`ClientSetupEvent.java:362-363`，`itemNameProvider`）。
   - 同步修掉 `registerItemRenderer` 字段 javadoc 里同源的过期表述（「由 ClientProxy 在客户端 preInit 注入」）。
   - 纯注释改动，无行为变化。

四个改动文件 `lint_files(min_severity="error")` 均为 **0 error**。

> 注：`git diff` 里 `crafting/GunSmithTableIngredient.java` 的改动**不是本轮 r5-A 产生的**
> （是更早一轮矿词回退工作的未提交改动），复核 diff 时请与本次四个文件分开看。

---

## 5. 未修清单 + 原因

| # | 内容 | 为什么不动 |
|---|---|---|
| 4 | `LootTableInjectorModifier` 的 `set_nbt` 用覆盖而非 merge | 需要产品裁决（上游 `merge` 语义 vs 现有覆盖语义）；默认枪包未触发该分支 |
| 5 | `TargetMinecart` 可骑乘 / 轨道限速 / `doEntityDrops` | 1.7.10 无对应覆写点，需 `entity/mixin/**` 跨域改动；且是「多给的便利」不是数据丢失 |
| 6 | `TargetMinecart` 缺 `source.isIndirect()` 校验 | 当前伤害源恒为间接，无可见差异；属防御性收窄 |
| 7 | `AmmoStorageInventory#getInventoryStackLimit` 固定 64 | 1.7.10 `IInventory` 平台限制，已在类注释登记为平台差异 |
| — | `setGunDisplayId` / `setMaxDummyAmmoAmount` / `setSkinId` / `setAllTypeCreative` 等 0 调用点的 setter | **上游同样 0 调用点**（对外 API 或留给未移植的客户端改装界面），不是「写端缺失」 |
| — | `GunLevelExp` 无写端 | 上游 `getLevel(int)`/`getExp(int)`/`getMaxLevel()` 也恒返回 0，等级系统上游未落地 |
| — | `tacz:tracer_override` / `tacz:tracer_size` 无写端 | 上游同样是「第三方模组通过 `getPersistentData()` 写入」的对外 API |
| — | 客户端改装界面（`client/gui/**`）不写 `Skin` 等 | 属 `client/**` 写作用域，不在本任务范围 |

---

## 6. 覆盖清单（诚实）

**逐行读完（38 个文件）**
- `api/item/nbt/`：`GunItemDataAccessor`、`AmmoBoxItemDataAccessor`、`AmmoItemDataAccessor`、
  `AttachmentItemDataAccessor`、`BlockItemDataAccessor`、`ItemStackNbtHelper`、`ItemDataAccessor`（空文件，上游也空）
- `entity/`：`EntityKineticBullet`、`TargetMinecart`、`EntityBulletClientHooks`、
  `sync/ModSerializers`、`sync/ModSyncedEntityData`、`sync/core/{DataEntry,DataHolder,SyncedClassKey,SyncedDataKey,SyncedEntityData,Serializers}`
- `block/`：`AmmoStorageBlock`、`GunPegboardBlock`、`StatueBlock`、`TargetBlock`、`AbstractGunSmithTableBlock`、
  `GunSmithTableBlockA/B/C`、`BlockMetadata`、`entity/{AmmoStorageBlockEntity,TargetBlockEntity,StatueBlockEntity,GunPegboardBlockEntity,GunDisplayCaseBlockEntity,GunSmithTableBlockEntity,DisplayGunSlot,GunDisplayBlockEntity}`
- `item/`：`ModernKineticGunItem`、`AmmoBoxItem`、`AmmoStorageBlockItem`、`AmmoItem`、`AttachmentItem`、
  `GunPegboardItem`、`GunSmithTableItem`、`DefaultTableItem`、`TargetMinecartItem`、`GunTooltipPart`
- `inventory/`：`AmmoStorageInventory`、`AmmoStorageMenu`、`GunSmithTableMenu`、`AmmoStorageEntry`、
  `GuiHandler`、`GuiId`、`tooltip/*`（5 个）
- `crafting/`：`GunSmithTableRecipe`、`GunSmithTableIngredient`、`GunSmithTableSerializer`、
  `AmmoStorageUpgradeRecipe`、`GunPegboardDyeRecipe`、`result/{GunSmithTableResult,RawGunTableResult}`
- `resource/serialize/`：全部 15 个文件（整包逐行读完）
- `loot/LootTableInjectorModifier`、`particles/BulletHoleOption`、`item/ItemClientHooks`（本轮修 javadoc 时逐行读完）

**只抽查（读关键段落 / 只读 grep 结果，未逐行）**
- `block/GunDisplayCaseBlock.java`（634 行）：读 `locked` 相关、`breakBlock`、形状与交互段，未逐行读 540 行之前的模型段
- `item/ModernKineticGunScriptAPI.java`（792 行）：只针对 NBT accessor 调用点 grep 并逐个核对（35 处），未读非 NBT 的 Lua 绑定段
- `entity/shooter/**`（17 个文件）：读 `ShooterDataHolder`、`LivingEntityAim.zoom()`，其余 15 个只确认「无 NBT 读写」
- `block/GunPegboardSlotHelper`、`GunDisplayCaseSlotHelper`、`PegboardShape`：只确认「纯几何/metadata，无 NBT」
- `crafting/OreDictionaryFallback`（184 行）：只读类注释与 `findItemStack`/`fallbackItem` 签名（纯查表，无 NBT）
- `entity/shooter/ShooterDataHolder`：逐行读完（瞬时状态，不入 NBT）

**没读**
- `entity/shooter/` 里除上述两个之外的 15 个状态机文件的实现细节（只 grep 过 NBT/accessor 相关行）
- `inventory/tooltip/*` 的渲染消费方（L2 客户端）
- `block/GunDisplayCaseBlock` 的 1-100 行（属性编码表，已由 `BlockMetadata` 公共类覆盖验证）

---

## 7. 需实机验证项

1. **工作台连续合成**：同一条配方连续做 3 次以上，确认材料每次都扣、产物每次都出，且产物 NBT（`GunId`/`FireMode`/`CurrentAmmoCount`/内置配件）完整（验证发现 #1 的修复，并顺带验证 `GunItemBuilder.build()` 的写入链）。
2. **雕像取枪 / 破雕像**：放枪→破坏方块，枪应掉在方块上方；再验证「空手右键取下」仍正常（不应出现一次取两把）。
3. **标靶掉落**：铁砧命名→放置→破坏，掉落物应保留名称；另验证「破坏上半块不掉落」「创造破坏不掉落」「爆炸破坏按 100% 掉落」三条。
4. **弹药库搬运**：破坏 → 放置 → 升级（铁→金→钻石配方），三个环节后库存条目数与数量（`long`）都不变；tooltip 预览条目与库存一致。
5. **挂板染色往返**：染料配方染色 → 放置 → 破坏，颜色应保留；相邻放置时未染色挂板应继承邻居颜色（`InheritedColor` 不写回物品）。
6. **子弹 spawn data**：多人环境下客户端看到的弹道、曳光、爆炸范围与服务端一致（18 个字段一次性下发，任一错位都会表现为「子弹走歪/不爆炸」）。
7. **热系统**：连射击至过热 → `OverHeated` 为 true → 冷却到 0 后自动解锁；退出重进存档后热度与锁定状态仍在。
8. **配件/皮肤/变体**：给配件设 `Skin`/`Variant` 后装到枪上，拔下再装上，NBT 不应丢；瞄准滚轮档位（`ZoomNumber`）在退出重进后保留。
