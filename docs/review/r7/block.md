# r7-block · 方块 / 方块实体 / 方块物品 上游等价性审查

任务：task-13（r7 轮）。写作用域：`block/**`、`client/renderer/block/**`、
`client/renderer/item/{BlockModelGuiItemRenderer,GunSmithTableItemRenderer}.java`、
`item/{AmmoStorageBlockItem,BlockItemWithItemIcon,DefaultTableItem,GunPegboardItem,GunSmithTableItem,TargetMinecartItem}.java`、
`client/event/{GunDisplayCaseClientInteractionEvent,GunDisplayCaseHighlightEvent,GunPegboardHighlightEvent}.java`、
`event/{AmmoStorageInteractionEvent,GunDisplayCaseInteractionEvent,GunPegboardInteractionEvent}.java`、
`client/init/GunPegboardColors.java`、本报告。

**未运行 gradle**。改动文件 `mcp__idea__lint_files(min_severity="error")` = **0 error**；
对**本模块全部 46 个 java 文件**（`block/**`、`client/renderer/block/**`、6 个方块物品、
3 个 client 事件、3 个 common 事件、`GunPegboardColors`、2 个 item renderer）整体 lint 也是 **0 error**。
scope 内 `grep TACZ-DBG` = **0 处**（既有 DBG 全部落在其它 teammate 的 scope）。

---

## 0. 结论速览

| # | 问题 | 性质 | 处置 |
|---|---|---|---|
| 1 | `GunPegboardBlock` 把 `SHAPE` 编到 metadata **bit 3-4**，而 1.7.10 metadata 只有 **4 bit** ⇒ `CORNER_RIGHT`(2) 放弃高位变 `STRAIGHT`，且每次邻居更新都重新写一次（自激） | **真实移植错误**（折面枪架功能失效） | 已修：FACING 0-1 + SHAPE 2-3 |
| 2 | `GunDisplayCaseBlock` 把 `LEFT/RIGHT/UP/DOWN` 编到 bit 3-6（共 7 bit）⇒ RIGHT/UP/DOWN 全部丢失，「连成一面墙后锁定联动」只剩左侧一格 | **真实移植错误**（多格墙锁定联动失效） | 已修：连接标记改为按需现算 + 新增 `ISOLATED` 位复刻「潜行放置」 |
| 3 | 挂板 `DYED`（染色）状态**写了没人读**：方块在世界内永远用未染色贴图；`client/init/GunPegboardColors` 的注释声称「已落在 `GunPegboardBlock#colorMultiplier`」但该类里根本没有该方法 | **真实移植缺口**（上游染色外观完全缺失） | 已修：`getIcon(IBlockAccess,…)` + `colorMultiplier` + `getRenderColor` |
| 4 | 雕像「手持枪上台」在创造模式不扣物品 | 语义偏离（上游 `stack.shrink(1)` 无创造判断） | 已改回上游语义 |
| 5 | 标靶 / 雕像 / 2 格工作台：第二格被占时方块先放下再撤销，**标靶与雕像会把物品吞掉**（上游 `getStateForPlacement` 返回 null ⇒ 不放置、不扣物品） | **真实移植错误**（吞物品） | 已修：`ItemBlock#placeBlockAt` 预检 |
| 6 | 标靶矿车物品的自定义名称被丢弃（注释称 1.7.10 无等价物，实际 `EntityMinecart#setMinecartName` 就是等价物） | 移植缺口 | 已修 |
| 7 | 4 个展示类 BER 的上游 `getViewDistance()`（`RenderConfig.TARGET_RENDER_DISTANCE`，默认 **128**）在 1.7.10 未接：TESR 默认 64 格就剔除了 | 平台差异被误当成「无法实现」 | 已修：TE 覆盖 `getMaxRenderDistanceSquared()` |

其余逐文件对照结论：**保真**（详见 §2），r6 的方块物品图标修复**未回退**（§3）。

---

## 1. 本轮修复

### 1.1 枪架 `SHAPE` metadata 溢出 4 bit（`block/GunPegboardBlock.java`）

**上游**：`GunPegboardBlock.java:49` `SHAPE = EnumProperty.create("shape", PegboardShape.class)`，
`getConnectedShape(...)`（:107-123）与 `PegboardShape.STRAIGHT/CORNER_LEFT/CORNER_RIGHT`（`PegboardShape.java`）三方状态。

**移植前**（错误）：

```java
private static final int DYED_BIT = 2;
private static final int SHAPE_SHIFT = 3;   // ← SHAPE 占 bit 3-4
private static final int SHAPE_MASK = 0x3;
```

**为什么错**：1.7.10 的 metadata 存在 `NibbleArray` 里，`set()` 用 `& 15` 截断
（`build/rfg/minecraft-src/java/net/minecraft/world/chunk/NibbleArray.java:44-58`），
`Read` 端 `get()` 也只返回 4 bit。`CORNER_RIGHT.toMeta()==2` ⇒ `2 << 3 == 16` ⇒ 存进去变 **0**：

* 折面枪架「右转角」永远退化成直板（第二面 16 个槽位永久不可用，`GunPegboardSlotHelper#isSlotActive` 也按直板算）；
* 更糟：`onNeighborBlockChange` 里 `updated != metadata` 判断看到的是截断后的值，
  于是**每次邻居更新都重新写一遍 16**，并因 flag=2 再次通知邻居 ⇒ 一个自激的 metadata 写入环
  （方块被推动/旁边放置方块时可观察到持续更新）。

**修法**：`FACING` 保 bit 0-1、`SHAPE` 搬到 bit 2-3（最大 `0b1011 = 11`，4 bit 内）。
`DYED` 退出 metadata（见 1.2）。编解码函数 `getShape/withShape` 不变，调用方（`GunPegboardSlotHelper`、
`GunPegboardBlockEntity`、BER）零改动。

### 1.2 挂板染色（`DYED`）补实现

**上游**：`blockstates/gun_pegboard.json` 里 `dyed=true` 选 `tacz:block/gun_pegboard_dyed`
（`models/block/gun_pegboard_dyed.json` = `tacz:block/pegboard_grayscale` + 每个面 `tintindex:0`），
颜色来自 `GunPegboardBlockEntity#getColor()`（`GunPegboardBlockEntity.java:56-74`）。

**移植前**：`withDyed(isDyed)` 只在 `GunPegboardBlockEntity#setColor` 里被写入，**全仓无读取点**；
`client/init/GunPegboardColors.setup()` 的 javadoc 却写着「染色由 `GunPegboardBlock#colorMultiplier`
与 `GunPegboardItem#getColorFromItemStack` 承担」—— 后者存在，前者不存在 ⇒ 世界内的染色挂板一直是原色。

**修法**（1.7.10 等价物，全部是原版既有钩子，不发明机制）：

* `registerBlockIcons` 额外注册 `tacz:pegboard_grayscale` / `tacz:glow_pegboard_grayscale`
  （`assets/tacz/textures/blocks/` 两份贴图都已存在）；
* `getIcon(IBlockAccess, x, y, z, side)`（`Block.java:643` 的钩子，`RenderBlocks` 实际走的就是它）
  在 `GunPegboardBlockEntity#getColor() >= 0` 时返回 grayscale 图标；
* `colorMultiplier(IBlockAccess, x, y, z)`（`Block.java:1155`）返回该颜色 ——
  `RenderBlocks#renderStandardBlock` 会读它作为顶点颜色（`RenderBlocks.java:4410-4412`），
  与上游 `tintindex:0` 同义；
* `getRenderColor(int)`（`Block.java:1145`）返回白色：物品/3D 图标路径没有坐标，取不到 TE
  （染色物品图标的等价物是 `item/GunPegboardItem#getColorFromItemStack`，已存在）。
* `GunPegboardBlockEntity#setColor` 不再写 metadata（DYED 完全由该字段派生），只 `update()`
  —— `update()` → `markBlockForUpdate` 既把颜色同步给客户端
  （`PlayerManager.java:628-639` 在该块 `hasTileEntity` 时发 `getDescriptionPacket`），
  也触发该方块的 render update。

### 1.3 展示柜连接标记溢出 + 潜行放置语义（`block/GunDisplayCaseBlock.java`）

**上游**：`LEFT/RIGHT/UP/DOWN` 四个 `BooleanProperty`（`GunDisplayCaseBlock.java:43-46`），
用途只有两个：① `updateConnections`/`shouldAutoConnectTo`/`syncConnectedNeighbors` 维护；
② `toggleLinkedCases` 用它们做 BFS 决定「按一次潜行右键联锁哪些柜子」（:346-392）。
**几何完全由 `FACING` 决定**（`getShape`/`getCollisionShape` 只用 FACING，:190-222）。

**移植前**（错误）：`LOCKED=bit2, LEFT=3, RIGHT=4, UP=5, DOWN=6` —— 7 bit 塞 4 bit，
`RIGHT/UP/DOWN` 写入即被截断。后果：只有「左侧邻居」参与连接，`toggleLinkedCases` 的 BFS
最多只能往左扩一格 ⇒ **多格展示墙的锁定联动基本失效**（同时也是 metadata 反复重写的诱因）。

**修法**：四个连接标记**从 metadata 里退出**，改为按需现算
（`GunDisplayCaseBlock#isConnected(IBlockAccess, x, y, z, ForgeDirection)`，:136）：
`= 该方向上是同面向 Block 且不是孤立柜`。这与上游的缓存值等价：

* 上游 `updateShape` 只负责在邻居变化时**清**位、`onPlace`/放置时**补**位，
  即「缓存 = 邻居是否仍是同朝向展示柜」；现算得到同一个值；
* 上游 `shouldAutoConnectTo` 里「邻居也要认我」的互相确认条件（:271-272）在 facings 相等时恒成立，
  所以「现算」不会引入额外的非对称连接。

唯一的例外是上游 `context.isSecondaryUseActive()`（潜行放置）：那种情况下上游既不给自己补标记、
也不给邻居补标记，于是该柜子**永久孤立**（之后邻居放置时也因对侧标记为 false 而拒绝连接）。
1.7.10 用一个新位复刻这个语义：`ISOLATED_BIT = 3`（`withFlag`），
`isConnected` 对「自己是孤立」或「邻居是孤立」都返回 false。

连带删除（其职责已被现算取代，注释里指向上游行号）：
`updateConnections` / `shouldAutoConnectTo` / `collectConnectedCases` / `syncConnectedNeighbors` /
`onNeighborBlockChange`（上游 `updateShape` 的职责）。`toggleLinkedCases` 与
`queueConnectedCases` 改用现算连接，BFS 拓扑与上游逐条一致。

### 1.4 雕像：创造模式也扣物品（`block/StatueBlock.java`）

上游 `StatueBlock.java:72-76`：`statueBlockEntity.setGun(stack); stack.shrink(1);` —— **没有**创造模式判断。
移植侧原本加了 `if (!player.capabilities.isCreativeMode)`，属语义偏离，已去掉
（`--held.stackSize` + 归零时清空手持槽，等价 1.20 `ItemStack#shrink` 的观感）。

### 1.5 标靶 / 雕像 / 2 格工作台：第二格被占时不再吞物品

**上游**：`getStateForPlacement` 在第二格不可替换时返回 `null`，放置直接取消、物品不消耗
（`TargetBlock.java:169-179`、`StatueBlock.java:87-97`、`GunSmithTableBlockB.java:43-52`、
`GunSmithTableBlockC.java:40-49`）。

**移植前**：在 `onBlockPlacedBy` 里补救 —— 方块已经放好、物品已经扣掉，才发现上面被占：
`TargetBlock`/`StatueBlock` 直接 `setBlockToAir` **不退款 ⇒ 物品凭空消失**；
工作台用 `refundAndRemove` 退款（不丢物品，但会有一帧「放下又消失」+ 掉落物）。

**修法**（同一个 1.7.10 落点，两处）：

* `item/BlockItemWithItemIcon#placeBlockAt`（:65，target/statue 共用）：上方不可替换 ⇒ 返回 `false`；
* `item/GunSmithTableItem#placeBlockAt`（:82，workbench_b/c 共用）：按各自朝向算出第二格坐标再判定
  （`GunSmithTableBlockC` 是 y+1；`GunSmithTableBlockB` 是 `clockwise(horizontalDirection(placer))` 方向）。

为什么是 `placeBlockAt`：1.7.10 `ItemBlock#onItemUse` 用它的返回值决定「是否扣物品」
（`build/rfg/minecraft-src/java/net/minecraft/item/ItemBlock.java:116-119`），而它的入参
**已经是最终放置坐标**（`snow_layer`/可替换方块/点击面偏移都在 :60-97 里换算完了）。
返回 `false` ⇒ 方块不放、物品不扣，与上游「返回 null」逐点等价。
`onBlockPlacedBy` 里原有的兜底退款分支保留（防御性，正常路径不再触发）。

### 1.6 标靶矿车的自定义名称（`item/TargetMinecartItem.java`）

上游 `TargetMinecartItem.java:39-41`：`if (itemstack.hasCustomHoverName()) targetMinecart.setCustomName(...)`。
移植侧原本注释「1.7.10 `EntityMinecart` 只有 `setMinecartName`，语义不等价」而跳过 —— 实际上
`EntityMinecart#setMinecartName` 就是等价物：同一个 `"CustomName"` 存档键
（`build/rfg/minecraft-src/java/net/minecraft/entity/item/EntityMinecart.java:875,894-896`），
并且用于矿车被破坏时的掉落物显示名（:223-225）与容器标题。已接上：

```java
if (stack.hasDisplayName()) {
    minecart.setMinecartName(stack.getDisplayName());
}
```

### 1.7 展示类 BER 的视距（4 个 `block/entity/*.java`）

上游 4 个渲染器都 `getViewDistance() = RenderConfig.TARGET_RENDER_DISTANCE`（默认 **128**，
`TargetRenderer.java:76-79`、`StatueRenderer.java:87-100`、`GunDisplayCaseRenderer.java:119-132`、
`GunPegboardRenderer.java:119-132`）。1.7.10 的 TESR 没有 `getViewDistance()`，
但距离剔除由 **`TileEntity#getMaxRenderDistanceSquared()`** 承担
（`TileEntityRendererDispatcher.java:119`，默认值只有 `4096 = 64²`，`TileEntity.java:193-196`）
⇒ 移植前半透明/展示方块**64 格外就消失**，而上游能画到 128 格。

修法：`TargetBlockEntity:290`、`StatueBlockEntity:180`、`GunDisplayCaseBlockEntity:316`、
`GunPegboardBlockEntity:457` 覆盖 `getMaxRenderDistanceSquared()` 返回
`TARGET_RENDER_DISTANCE²`（`RenderConfig` 在专用服务器未初始化时回退父类默认值，
该方法只在客户端 TESR 路径被调用）。

---

## 2. 逐文件对照表

图例：`≡` 语义等价；`≡*` 等价但有注释记录的平台替换；`固` 本轮修复；`降` 有据降级（§5）。

### 2.1 方块（`block/`）

| 本工程 | 上游 | 结论 | 依据 / 备注 |
|---|---|---|---|
| `TargetBlock.java` | `TargetBlock.java` | ≡* | 双格结构/朝向/击倒/5 秒复位/红石强度公式（:56-79 ↔ `getRedstoneStrength` :149-169，中心 0.32 一致）/`PushReaction.DESTROY`/创造掉落抑制/`getPickBlock` 名称 全部对齐。`onProjectileHit` 无 1.7.10 钩子，改由 `EntityKineticBullet:632` 显式调用（已接线）。`OUTPUT_POWER`(4 bit) 无处安放 ⇒ 存 TE（`OutputPower` 键）——**这是唯一的能力降级，语义等价**。 |
| `StatueBlock.java` | `StatueBlock.java` | ≡*+固 | 双格/朝向/拆半随动/`onRemove`→`breakBlock` 掉落枪/仅 lower 掉方块 全部对齐；1.4 修复创造模式扣减。 |
| `GunPegboardBlock.java` | `GunPegboardBlock.java` | 固 | 见 §1.1/§1.2。放置朝向（含「面对板面延续朝向」）、折面判定、旋转/镜像、右键放置/旋转、左键取枪、掉落带色、`getMobilityFlag` 均等价。**`!context.replacingClickedOnBlock()` 与「点击方块是枪架」在 1.7.10 等价**（枪架 `Properties` 没有 `replaceable()` ⇒ `replaceCanBeReplaced` 恒 false），类注释已据实改写。 |
| `GunDisplayCaseBlock.java` | `GunDisplayCaseBlock.java` | 固 | 见 §1.3。朝向规则（点击柜子→沿用；侧面→点击面；上下→玩家朝向反向）、开/锁两套形状（`LOCKED/OPEN/BACK_PANEL` 逐盒一致）、右键放置/旋转/潜行切锁、左键取出、拆柜掉枪 均等价。 |
| `AmmoStorageBlock.java` | `AmmoStorageBlock.java` | ≡* | 三档容量（216/432/864）、朝向、`use` 两分支（弹药盒装填/卸载、否则开界面）、比较器信号、掉落带 `BlockEntityTag`、创造抑制 全部对齐。`unloadAmmoBox` 与上游一样**委托** `util/AmmoStorageInteractionHelper`（上游 `AmmoStorageBlock.java:95`；本类私有副本已按 r7-util 裁决删除，见 §4.3）；`fillAmmoBox` 音效 `ARMOR_EQUIP_IRON` 无 1.7.10 资源 ⇒ `random.bowhit`（已注释 + TODO）。 |
| `AbstractGunSmithTableBlock.java` | 同名 | ≡* | GUI 打开（`NetworkHooks.openScreen` → `GuiHandler`）、`setPlacedBy` 写 `BlockId`、`getCloneItemStack`、三种风味的掉落（`part=foot`/`half=lower` + `copy_nbt`）全部对齐。掉落改在 `breakBlock` 做（1.7.10 `getDrops` 在 TE 移除之后，`ItemInWorldManager.java:265-269,322-326`）——注释已记录副作用。 |
| `GunSmithTableBlockA/B/C.java` | 同名 | ≡* | A: facing=玩家朝向反向；B: `facing=clockwise(horizontal)` + foot→head；C: facing 反向 + lower→upper。`playerWillDestroy` 的创造模式连锁移除 → `onBlockHarvested`+`removeWithoutDrop`（与 `BlockBed`/`BlockDoor` 同构）。`parseRotation` 逐式一致（`90*(3-2D)-90` / `90-90*2D`）。 |
| `BlockMetadata.java` | （移植新增） | ≡ | `Direction#get2DDataValue()` 编码顺序（N=0,S=1,W=2,E=3）与 1.20 一致，这是 `parseRotation`/`DisplayItemTransforms` 数值不变的前提；`box()` 的 1/16 换算与 `Block.box` 一致。 |
| `PegboardShape.java` | `PegboardShape.java` | ≡ | 三个枚举 + `getCornerFacing(facing)` = `getCounterClockWise/getClockWise`；`getSerializedName()` 保留（NBT `SlotShape` 存档兼容）。 |
| `GunPegboardSlotHelper.java` | 同名 | ≡ | 逐位一致（`PLATE_THICKNESS/LINE_OFFSET/LINE_DEPTH`、`onDisplayFace` 容差 0.001、`isSlotActive` 的 blockedColumn 规则、`getSlotAabb().inflate(0.002)`）。 |
| `GunDisplayCaseSlotHelper.java` | 同名 | ≡ | 同上（4x4、u/y 换算、四朝向的 AABB 端点逐位一致）。 |

### 2.2 方块实体（`block/entity/`）

| 本工程 | 上游 | 结论 | 备注 |
|---|---|---|---|
| `TargetBlockEntity.java` | 同名 | ≡*+固 | NBT 键 `Owner`/`CustomName`（JSON 文本）+ 移植新增 `OutputPower`；`clientTick` 角度插值（-18/+45，0..90）一致；`hit()` 的红石/复位/音效/提示一致；`getRenderBoundingBox` 与上游一致；§1.7 视距。`SkullBlockEntity.updateGameprofile`（异步补皮肤）1.7.10 无等价物 ⇒ 只存名字/UUID（已注释）。 |
| `StatueBlockEntity.java` | 同名 | ≡*+固 | NBT 键 `Item`；`setGun` 先丢后放、`dropItem` 掉在「朝向那一格的上方」；客户端每 100 tick 冒粒子（`END_ROD` 无 1.7.10 资源 ⇒ `flame`，已注释）；§1.7 视距。 |
| `GunDisplayCaseBlockEntity.java` | 同名 | ≡*+固 | NBT 键 `Items`(Slot byte + Item compound)/`Rotations`(byte[16]) 完全一致；`$1..7` 旋转档位；`dropItem`/`dropAllItems` 掉在朝向前方；§1.7 视距。 |
| `GunPegboardBlockEntity.java` | 同名 | ≡*+固 | NBT 键 `Items`/`Rotations`/`SlotShape`/`Color`/`InheritedColor` 完全一致；32 槽位 + 折面；`setBlockState` 钩子（1.7.10 改 metadata 不通知 TE）→ `onBlockStateChanged`，逻辑逐行对应（含 `removedFace` 丢物品）；`getUpdateTag` 去 `SlotShape` 的镜像保护同样保留；§1.2 染色、§1.7 视距。 |
| `GunSmithTableBlockEntity.java` | 同名 | ≡ | NBT 键 `BlockId`；缺失时回退 `DefaultAssets.DEFAULT_BLOCK_ID`；`getRenderBoundingBox` 一致。`setId` 额外 `markDirty`+`markBlockForUpdate`（上游靠放置时的 block update 上报，1.7.10 补一次，无害）。 |
| `AmmoStorageBlockEntity.java` | 同名 | ≡* | NBT 键 `Entries`（`AmmoStorageInventory` 负责条目格式）；`MAX_AMMO_TYPES=27`；`stillValid` 距离 64；内容只经容器同步、不走 BE 同步（与上游注释一致）。`Capability`/`LazyOptional` 无 1.7.10 对应 ⇒ 直接暴露 `IInventory`。 |
| `DisplayGunSlot.java` | 同名 | ≡ | revision/appearanceRevision/consumeChange 逐行一致。 |
| `GunDisplayBlockEntity.java` | 同名 | ≡* | 接口契约；`Direction`→`ForgeDirection`、`ItemStack.EMPTY`→`null`（已注释）。 |

### 2.3 方块渲染（`client/renderer/block/`）

| 本工程 | 上游 | 结论 | 备注 |
|---|---|---|---|
| `TargetRenderer.java` | 同名 | ≡* | 变换序列逐行一致（`translate(0.5,0.225,0.5)` → `YN(facing*90)` → `ZN(180)` → `translate(0,-1.275,0.0125)`）；`deg = -lerp(oRot,rot)`；`head.visible=false` → 有 owner 才画头像；皮肤解析照抄 `TileEntitySkullRenderer`（`DefaultPlayerSkin` 无 1.7.10 对应 ⇒ Steve 兜底）。额外 `getNode` 判空（防内置模型缺失时每帧 NPE）。 |
| `StatueRenderer.java` | 同名 | ≡* | `translate(0.5,1.5,0.5)` → `YN((2D+2)%4*90)` → `ZN(180)`；`BLOCK_ENTITY_TRANSLUCENT` 选 translucent/cutout；枪械 `scale(0.5)`+`translate(0,-0.875,-1.2)`+`ZP(180)`+`sin(now/500)*0.1` 悬浮、`LightTexture.pack(15,15)` 满亮 —— 逐行一致。`renderStatic` 由 `StaticItemRenderHelper` 承担（§5）。 |
| `GunSmithTableRenderer.java` | 同名 | ≡* | `isRoot` 才画、`translate(0.5,1.5,0.5)` → `ZN(180)` → `YN(parseRotation(facing))`，translucent/cutout 一致。 |
| `GunDisplayCaseRenderer.java` | 同名 | ≡* | 逐槽 `DisplayItemTransforms.apply(facing, x, y, ITEM_OFFSET=0.4375-3/16, rotation)`；`DisplayGunMeshGroup.tryRender` 优先；空格放置预览（`PREVIEW_ALPHA=0.65`，瞄准本块 + 非潜行 + 手持可放置物品 + 槽空）；预览期 blend + `setShaderColor(alpha)` + `ItemPreviewRenderContext`（finally 复位，防永久半透明）。 |
| `GunPegboardRenderer.java` | 同名 | ≡* | 同上；`row = (slot % SLOTS_PER_FACE)/4`、发光挂板用满亮 lightmap。 |
| `DisplayItemTransforms.java` | 同名 | ≡ | 逐行一致（`translate(0.5)`→`YP(180 - 2D*90)`→`translate(x,y,offset)`→`ZP(rotation*45)`→`scale(0.5)`）。 |
| `DisplayGunMeshGroup.java` | 同名 | 降 | 上游是 SBM v2 的 `WorldMeshRenderer` 网格组（VBO 缓存 + 世界阶段批量提交）；1.7.10 无该体系 ⇒ 立即绘制。外观解析 / LOD 选择 / 光照选择 / 实例矩阵**全部保留**，只丢跨帧 VBO 复用（注释含上游行号）。 |
| `DisplayGunGeometry.java` | 同名 | 降 | `GeometryCollector`（按 UV2 分流普通/自发光双材质）⇒ 单通道 `MultiBufferSource` + 真实 lightmap；`bone.illuminated` 由 SBM 内部转 `MAX_LIGHT_TEXTURE` 保证自发光观感。配件遍历/变体/LOD/mountOffset 逐行一致。 |
| `DisplayGunRenderType.java` | 同名 | ≡* | `entityCutout`/`emissive` 映射到 mcshim（emissive 通道在 1.7.10 由逐顶点亮度承担，见上）。 |
| `StaticItemRenderHelper.java` | （上游无，`ItemRenderer#renderStatic` 替身） | 降 | 见 §5 第 2 条。 |
| `TargetMinecartRenderer`（他人 scope） | 同名 | — | r6 修过（`func_145817_o` 触发器），本轮未复查/未改。 |

### 2.4 方块物品 + 交互事件

| 本工程 | 上游 | 结论 | 备注 |
|---|---|---|---|
| `item/GunPegboardItem.java` | `GunPegboardItem.java` | ≡*+固关联 | 放置时「未染色则继承相邻挂板颜色」逐行对应（`relinking` 判定用「点击面反推的相邻格是挂板」）；tooltip 两行一致；`getColorFromItemStack` 提供物品图标染色（§1.2 的配套）。1.7.10 无炼药锅染色（1.8+）⇒ 已记录。 |
| `item/DefaultTableItem.java` | 同名 | ≡ | `getBlockId` 锁定 `tacz:gun_smith_table`，`setBlockId` 空实现（上游同样注释「默认块 id 无效」）。 |
| `item/GunSmithTableItem.java` | 同名 | ≡*+固 | 创造栏填充（按 namespace 排序）、`getName`→`getItemStackDisplayName`（走 `ItemClientHooks`）、§1.5 放置预检。`initializeClient`/BEWLR 由 `MinecraftForgeClient.registerItemRenderer` + `GunSmithTableItemRenderer` 承担。 |
| `item/AmmoStorageBlockItem.java` | 同名 | ≡* | 金/钻石配色名、两行 tooltip、`BlockEntityTag` 回写 TE（含坐标修正）。上游 `getTooltipImage`（内容预览）缺失，见 §4/§5。 |
| `item/TargetMinecartItem.java` | 同名 | ≡*+固 | 铁轨判定（`BlockRailBase` ↔ `BlockTags.RAILS`）、上升轨 yOffset 0.5（普通/充能铁轨 metadata 2..5）、坐标 `+0.5 / +0.0625` 一致；§1.6 名称。 |
| `item/BlockItemWithItemIcon.java` | （r6 新增） | ≡+固 | r6 的「sprite 切到 items 图集」修复保留；本轮加 §1.5 放置预检。 |
| `event/GunPegboardInteractionEvent.java` | 同名 | ≡* | 左键带槽位判定取枪；`shouldDrop = !creative \|\| sneaking`；创造模式 `removeItem`（不落地）；音效降级 `ITEM_FRAME_REMOVE_ITEM`→`random.pop`。`Items.DEBUG_STICK` 1.7.10 无（已注释）。 |
| `event/GunDisplayCaseInteractionEvent.java` | 同名 | ≡* | 左键同构（额外要求 `!LOCKED`）；上游「右键切换上锁」依赖副手拿枪（`getOffhandItem() instanceof IGun`），1.7.10 无双持 ⇒ 该分支不移植（已记录），改由 `GunDisplayCaseBlock#onBlockActivated` 的「潜行 + 非枪」路径提供等价入口。 |
| `event/AmmoStorageInteractionEvent.java` | 同名 | ≡* | 潜行 + 弹药盒右键 → `unloadAmmoBox`；成功/失败都 cancel（1.7.10 cancel = `useBlock/useItem DENY`，等价 `setCancellationResult`）。 |
| `client/event/GunPegboardHighlightEvent.java` | 同名 | ≡* | 瞄准格高亮（占用=红/空=绿）、潜行高亮全部占用格；`RenderHighlightEvent.Block`→`DrawBlockHighlightEvent`；`NO_DEPTH_TEST/NO_CULL/COLOR_WRITE` 用显式 GL 开关 + `glPushAttrib` 配对。 |
| `client/event/GunDisplayCaseHighlightEvent.java` | 同名 | ≡* | 与上一个对称（多一个 LOCKED 判断）。 |
| `client/event/GunDisplayCaseClientInteractionEvent.java` | 同名 | ≡* | 空手对展示柜按「使用」→ HUD 提示「潜行 + 使用」；`InputEvent` 不存在 ⇒ ClientTick 按下沿检测（`currentScreen != null` 时挡掉，等价上游「有 GUI 时不派发」）；`isSpectator` 1.7.10 无。 |
| `client/init/GunPegboardColors.java` | 同名 | ≡ | 三项注册（Block/Item 颜色 + `tacz:dyed` 物品属性）在 1.7.10 都无对应注册表，染色是 `Block`/`Item` 上的方法 —— §1.2 补上 block 侧后，该类 javadoc 的声明**现在成立**。 |

---

## 3. r6 修复复核（不得回退）

| r6 项 | 现状 | 结论 |
|---|---|---|
| `BlockItemTransforms.ItemTransform#apply` 的 `translation × 1/16` + clamp | `client/resource/pojo/display/block/BlockItemTransforms.java:117,132-134` 仍是 `1/16` + `±5`，`scale` clamp `±4`，旋转用单个 `rotationXYZ` 四元数 | 与 1.20 `ItemTransform.Deserializer`（`ItemTransform.java:81-84`）+ `ItemTransform.java:44` 一致，**未回退**，本轮复核通过 |
| `BlockModelGuiItemRenderer`（6 个 3D 方块物品的 GUI 图标） | `client/renderer/item/BlockModelGuiItemRenderer.java` + `ClientSetupEvent:239-247` 注册 6 个 | **未回退**；注册集合与 `GunSmithTableItemRenderer`（4 个工作台）**不重叠**，不存在两个 `IItemRenderer` 抢同一 `Item` 的问题；`handleRenderType` 只接管 `INVENTORY`，其它 context 仍走原版 |
| `BlockItemWithItemIcon`（target/statue 的 2D 图标） | `spriteNumber=1` + items 图集 icon | **未回退**，本轮只在其上追加放置预检 |
| `TargetMinecartRenderer` + `TargetMinecart#func_145817_o` 触发器 | 在 `client/renderer/entity/TargetMinecartRenderer.java`、`entity/TargetMinecart.java`（他人 scope） | 本模块未触碰；`item/TargetMinecartItem` 的生成路径（`new TargetMinecart(world,x,y,z)` + `spawnEntityInWorld`）未变，与 r6 的渲染触发条件无关 |
| `BlockItemTransforms`/`BlockModelGuiItemRenderer` 之外的 pegboard/ammo/display_case 图标 | 我改了 `GunPegboardBlock#registerBlockIcons`，**保留了 `tacz:pegboard`/`tacz:glow_pegboard` 两个 icon 名**（只是额外注册 grayscale） | `BlockModelGuiItemRenderer` 的贴图名映射（`tacz:block/pegboard`→`tacz:pegboard`）因此不受影响 |

---

## 4. 跨模块发现（只记录，未改）

1. **`entity/EntityKineticBullet.java:627-632`（r7-gun）**：命中标靶时调用
   `TargetBlock#onBulletHit(world, mop, ownerPlayer, damage)` —— 接线正确；`onBulletHit` 内部会自己把
   「上/下半块」归一到下半块并读 TE。**无问题，仅登记为已验证的跨模块契约**（若 r7-gun 改这段，请保持
   `MovingObjectPosition` 的 `sideHit`（面）与 `hitVec`（绝对命中点）语义，红石强度公式依赖它们）。
2. **`item/AmmoStorageBlockItem` 缺「内容预览」tooltip**（上游 `getTooltipImage` →
   `inventory/tooltip/AmmoStorageTooltip`）。1.7.10 没有 `TooltipComponent`；若要等价，
   应在 `client/tooltip/ClientBlockItemTooltip`（r7-gui 的 scope）里读物品 NBT 的
   `BlockEntityTag.Entries`（`Slot/Stack/Count`）并用 `tooltip.tacz.ammo_storage.*` 文案列出前 5 项。
   **代码位置已在 `block/AmmoStorageBlock` 的掉落侧保证该 NBT 存在**（`BLOCK_ENTITY_TAG`）。
3. **`util/AmmoStorageInteractionHelper`（r7-util）与 `block/AmmoStorageBlock` 各有一份 `unloadAmmoBox`**
   —— **已按 Lead 裁决落地（r7-block 收尾补丁）**：`block/AmmoStorageBlock` 删除私有副本，改为委托
   `util/AmmoStorageInteractionHelper.unloadAmmoBox(...)`（上游 `AmmoStorageBlock.java:95` 就是 block→util 调用）。
   顺带修掉两份副本的漂移：block 副本音效误用了 `fillAmmoBox` 的 `SOUND_ARMOR_EQUIP`（`random.bowhit`），
   上游 unload 是 `BUNDLE_REMOVE_ONE`（util 侧降级 `random.pop`，与 `AmmoStorageInteractionEvent` 路径一致）；
   block 副本还缺 `ammoId == null` / `createAmmoStack(...) == null` 判空。`fillAmmoBox` 与 `SOUND_ARMOR_EQUIP` 保留不动。
4. **`init/ModBlocks.java`（r7-core）**：`GUN_SMITH_TABLE`(BlockB + `DefaultTableItem`) 与
   `WORKBENCH_211`(BlockB + `GunSmithTableItem`) 是同一个方块类的两个实例，1.7.10 掉落无法区分
   （上游 loot 表一个有 `copy_nbt`、一个没有）—— 已在 `AbstractGunSmithTableBlock` 注释里记录为已知差异。
   本轮我新增的 `GunSmithTableItem#placeBlockAt` 预检**不影响**这两个注册。
5. **`config/client/RenderConfig.TARGET_RENDER_DISTANCE`（默认 128）**：本轮在 4 个 TE 上接上了
   `getMaxRenderDistanceSquared()`。若 r7-core/r7-platform 有「配置项必须全部被消费」的检查，这一项现在
   也满足了。

---

## 5. 未修 / 有据降级（诚实登记）

| # | 项 | 说明 | 判定 |
|---|---|---|---|
| 1 | **世界内方块形状**：挂板/展示柜/弹药箱/标靶/雕像/工作台 在 1.7.10 都是「整块立方体 + 方块图集贴图」，上游是 JSON 多部件薄板/框架模型 | r6 报告 §4 第 1 条已登记；本轮不引入 ISBRH（要给 7 个方块写 `ISimpleBlockRenderingHandler` 才行，属独立工作量） | 降级（既知） |
| 2 | `StaticItemRenderHelper`（`ItemRenderer#renderStatic` 替身） | 展示柜/挂板/雕像里画物品时：1.20 的 `ItemTransforms`（`display.fixed`）与 `FIXED` context 在 1.7.10 不存在 ⇒ 退化为「Forge `IItemRenderer`（`ENTITY`）/ 原版扁平图标」。**枪械本身**走的是 `DisplayGunMeshGroup`/`DisplayGunGeometry`（真实模型），退化只影响「非枪物品」的展示外观 | 降级（有注释） |
| 3 | 上游 `shouldRenderOffScreen()`（这几个 BER 都返回 true） | 1.7.10 没有「跳过视锥剔除」的 TESR 钩子，只有 `getRenderBoundingBox()` 的 AABB 剔除；本仓 AABB 已放大到 5x5x5（`±2..+3`），常规视角下不会误剔 | 降级（记于此） |
| 4 | 展示柜「右键切换上锁」的副手拿枪条件 | 1.7.10 无双持；由 block 层「潜行 + 手持非枪」提供等价入口（上游 `use()` 里本来也有潜行切锁分支） | 降级（有注释） |
| 5 | `TargetBlock` 的红石强度存 TE（`OutputPower`）而非 BlockState | 4 bit metadata 被 facing/half/stand 占满；语义等价，存档键新增 | 降级（语义等价） |
| 6 | 挂板**物品**（GUI/手持）的染色外观 | GUI 图标由 `BlockModelGuiItemRenderer` 固定用未染色模型（`ITEM_MODELS` 不随 NBT 变）；手持走 `getColorFromItemStack`（会染色）→ **GUI 里染色挂板看起来是原色** | 降级（新增登记，低优先） |
| 7 | 挂板在工作台染色（炼药锅 `CauldronInteraction.DYED_ITEM`） | `init/CommonRegistry.java:71-72` 已注释掉；1.7.10 无炼药锅染色机制（1.8+） | 降级（既知） |
| 8 | 音效 | `ITEM_FRAME_ADD_ITEM`/`ITEM_FRAME_ROTATE_ITEM`/`IRON_DOOR_OPEN`/`ARMOR_EQUIP_IRON`/`BUNDLE_REMOVE_ONE` 在 1.7.10 无资源 ⇒ `random.pop`/`random.click`/`random.door_open`/`random.bowhit`；`target_block_hit` **有** `sounds.json` 条目（`tacz:target_block_hit`，可用） | 降级（有注释） |
| 9 | 上游 loot 表的 `survives_explosion` 概率 | 1.7.10 掉落改在 `breakBlock` 做（TE 才能读到 `BlockId`/染色）⇒ 爆炸按 100% 掉落，`BlockEvent.HarvestDropsEvent` 看不到这份掉落 | 降级（有注释） |
| 10 | `SkullBlockEntity.updateGameprofile` 异步补皮肤（标靶 owner） | 1.7.10 无对应工具；`TargetRenderer` 用 `SkinManager` 同步查表 + Steve 兜底 | 降级（有 TODO） |
| 11 | 雕像粒子用 `ParticleTypes.END_ROD` | 1.7.10 无末地烛（1.9+）⇒ `flame`（`StatueBlockEntity.java:44` 注释 + TODO） | 降级（既知） |

### 5.1 已复核但**不改**：雕像粒子为什么必须留在客户端

收尾核对项（Lead 提出「`World#spawnParticle` 在服务端是 no-op ⇒ 雕像粒子永远不广播」）。**结论：该判断不适用于本调用点，保持现状**，
理由逐条有据：

1. 调用点在 `isRemote` 早退之后（`StatueBlockEntity.java:61`）：服务端根本走不到 `spawnParticle`。
   而 1.7.10 的 `World#updateEntities` **两端都 tick 方块实体**（`build/rfg/minecraft-src/java/net/minecraft/world/World.java:2148-2160`，
   无 `isRemote` 判断）⇒ 这条早退是**必需的**，正是上游 `clientTick` 的落点。
2. 上游雕像粒子本身也是客户端专用：`StatueBlock#getTicker` 只在 `level.isClientSide()` 时挂
   `StatueBlockEntity::clientTick`（`tmp/TACZ-1.20.1/.../StatueBlock.java:45-47`），
   `clientTick` 里 `level.addParticle(...)` 是**每个客户端各自加一个本地粒子**（`StatueBlockEntity.java:31-44`）。
   1.7.10 的客户端 `World#spawnParticle` → `RenderGlobal.spawnParticle` 与它同语义，
   因此**可见距离 = 该玩家的渲染距离**，与上游一致。
3. 改用 `WorldServer#func_147487_a`（= `ServerLevel#sendParticles` 的 1.7.10 等价物）反而更差：
   它只发给 **16 格内**的玩家（`WorldServer.java:1134` 的 `d10 <= 256.0D`，且坐标来自 `getPlayerCoordinates()` 的取整值），
   并且若同时保留客户端 `spawnParticle` 会出现「16 格内每 5 秒双份粒子」。
4. `WorldManager#spawnParticle` 为空实现（`WorldManager.java:29`）这条事实本身没错 —— 它才是
   「**服务端权威**粒子必须走 `func_147487_a`」的依据。本仓已在真正需要服务端广播的地方用它：
   `entity/EntityKineticBullet.java:711-715`（子弹入水/点燃）、`item/ModernKineticGunItem.java:693-696`（伤害指示）、
   `debug/GunMeleeDebug.java:48`。

⇒ 本轮只把这段结论写进 `StatueBlockEntity#updateEntity` 的 javadoc（含上面 4 个 `file:line`），**行为不变**。
若日后要改成服务端权威（例如换自定义末地烛粒子并接受 16 格门限），删除 `isRemote` 早退 + 调
`((WorldServer) worldObj).func_147487_a("…", x, y, z, 1, 0, 0, 0, 0)` 即可，但需要同时删掉客户端那条以免双份。

---

## 6. 实机验证清单

1. **挂板折面（§1.1）**
   - 放一块挂板，再沿它的一侧放第二块 ⇒ 应出现折面（两面板，共 32 格）；
   - 朝折面那一侧**右键检查**：靠近转角的那一列应「无槽位」（`isSlotActive` 的 blockedColumn 规则）；
   - 分别测 `CORNER_LEFT` 与 `CORNER_RIGHT` 两种转角（以前者为准对照：两者现在都应能挂物品）。
   - 判读：若**只有左转角**能出折面、右转角仍是直板 ⇒ SHAPE 位域仍有问题（`getShape`/`withShape`）。
2. **挂板染色（§1.2）**：给挂板物品写 `display.color`（或从已染色挂板拆下来再放）⇒ 世界内方块应为
   **grayscale 贴图 × 颜色**（不是原色）；相邻放置的未染色挂板应继承颜色（`InheritedColor`）。
   判读：若颜色不生效，查是否 `getDescriptionPacket` 到达客户端（`GunPegboardBlockEntity#getColor()` 客户端侧是否 >= 0）。
3. **展示柜多格墙（§1.3）**
   - 4x4 拼一面墙（同朝向），潜行右键其中**任意一格** ⇒ 整面墙应同时上锁/解锁（以前只有左侧链能联动）；
   - 潜行状态下放置一格新柜子 ⇒ 它应**孤立**：对它按潜行右键只切换它自己。
   - 判读：整墙不同步 ⇒ `isConnected` 的 facing/方向映射有问题；潜行新柜子仍联动 ⇒ `ISOLATED_BIT` 没写进去。
4. **标靶 / 雕像放置（§1.5）**：在「上方被占」的位置（例如贴天花板）手持 target/statue 右键 ⇒
   **既不放方块也不扣物品**（背包数量不变）；工作台 workbench_b（2x1x1）朝向头部那一格被占时同理。
5. **雕像创造模式（§1.4）**：创造模式手持枪右键雕像 ⇒ 枪应被消耗（手持槽清空），与你手上是否创造无关。
6. **标靶矿车命名（§1.6）**：铁砧命名「minecart with target」后放到铁轨 ⇒ 破坏矿车掉出的物品
   应保留该名字（`CustomName` 存档键）；`/data`（若有）或 NBT 查看实体应有 `CustomName`。
7. **BER 视距（§1.7）**：站在离展示柜/挂板/雕像/标靶 **64~128 格**处（把 `TargetRenderDistance`
   保持默认 128）⇒ 展示的物品/模型应仍可见；把配置改成 32 ⇒ 超过 32 格应消失。
8. **雕像粒子（§5.1）**：雕像里放了枪后，**每 5 秒**在枪上方冒一次 `flame` 粒子；
   站到 **32 格外**看仍应有（若只在 16 格内可见 ⇒ 有人把它改成了 `func_147487_a` 服务端广播，见 §5.1）。
9. **回归**：创造栏 OTHER 页签的 6 个 3D 方块图标（r6）与 target/statue 2D 图标仍正常；
   3 个工作台 GUI 图标仍在槽位内（`BlockItemTransforms` 的 ÷16 未被回退）。

---

## 7. 覆盖清单（诚实）

* **逐行通读并对照上游**：`block/{TargetBlock,StatueBlock,GunPegboardBlock,GunDisplayCaseBlock,AmmoStorageBlock,
  AbstractGunSmithTableBlock,GunSmithTableBlockA,GunSmithTableBlockB,GunSmithTableBlockC,PegboardShape,
  GunPegboardSlotHelper,GunDisplayCaseSlotHelper}`、`block/entity/{Target,Statue,GunDisplayCase,GunPegboard,
  GunSmithTable, AmmoStorage}BlockEntity`、`block/entity/{DisplayGunSlot,GunDisplayBlockEntity}`、
  `client/renderer/block/{TargetRenderer,StatueRenderer,GunSmithTableRenderer,GunDisplayCaseRenderer,
  GunPegboardRenderer,DisplayItemTransforms,DisplayGunMeshGroup,StaticItemRenderHelper,
  DisplayGunGeometry(含 renderModel/配件遍历),DisplayGunRenderType}`、
  `client/renderer/item/{GunSmithTableItemRenderer,BlockModelGuiItemRenderer(渲染主路径)}`、
  6 个方块物品、3 个 client 交互/高亮事件、3 个 common 交互事件、`client/init/GunPegboardColors`。
* **按需查片段**：1.7.10 `NibbleArray`、`Chunk#func_150807_a`、`ItemInWorldManager`、
  `ItemBlock#onItemUse`、`Block`（`getIcon`/`colorMultiplier`/`getRenderColor`/`registerBlockIcons`/`isReplaceable`）、
  `RenderBlocks`（`renderStandardBlock`/`getBlockIcon`）、`TileEntityRendererDispatcher`、`TileEntity`、
  `PlayerManager`（TE 同步）、`EntityMinecart`（`CustomName`）、`ForgeHooksClient`（r6 已核对）、
  1.20.1 `ItemTransform`；上游 `mods/*.json`（`blockstates/gun_pegboard.json`、`models/block/gun_pegboard*`、
  `gun_pegboard_corner`、`pegbard_dyed` 父链）。
* **没覆盖**：
  * `client/renderer/block/DisplayGunGeometry` 的**逐字段**几何复核（只核了出口、配件遍历、LOD/变体、
    `applyMountOffset`；顶点级数据流属 r7-model/r7-sbm 的范围，因为 `renderBoneTree` 是 vendored SBM/MAE 的代码）；
  * `TargetMinecartRenderer` / `entity/TargetMinecart`（他人 scope，r6 已修）；
  * 工作台 GUI（`inventory/GunSmithTableMenu`）与 `AmmoStorageMenu` 的界面逻辑（`inventory/**` 属 r7-item）；
  * 上游 `data/tacz/loot_tables/blocks/*.json` 的逐表比对（只在 `AbstractGunSmithTableBlock` 注释里引用了结论）。
* **没实机**：本轮全部为静态对照 + 1.7.10 源码行为推导；实机验证步骤见 §6。
