# r8-2 · 标靶修复：标靶矿车浮空/下半命中 + 人形标靶命中与朝向

任务：`task-35`。实机症状（用户反馈，1.7.10 / Forge 10.13.4 移植版）：

- **A. 标靶矿车**（`TargetMinecart` + `TargetMinecartRenderer`）：**浮空**（不贴轨道），且**只有下半截**能被子弹命中。
- **B. 人形标靶**（`TargetBlock` + `TargetBlockEntity` + `TargetRenderer`）：**整块都没有命中体积**（开枪毫无反应），且**朝向/位置异常**。

实测依据：上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`；1.7.10 反编译源码
`build/rfg/minecraft-src/java/**`；1.20.1 反编译源码 `tmp/minecraft-src-1.20.1/src/main/java/**`。

---

## 0. 结论表

| 症状 | 根因 | 落点 | 状态 |
| --- | --- | --- | --- |
| A1 矿车**浮空** | r6 只换算了「车体帧内的装载物偏移」，**没扣两版帧原点的高度差 0.4375**：平直轨道上 1.7.10 渲染帧原点 = `轨道 y + 0.5`，1.20 = `轨道 y + 0.0625` | `TargetMinecartRenderer.java:134` | 已修 |
| A2 矿车**只有下半可命中** | `yOffset` 沿用原版矿车构造器的 `height/2 = 1.2`，但 1.7.10 的 AABB 底面 = `posY - yOffset`，轨道上 posY = `轨道 y + 0.5` ⇒ 盒子 = `[轨道 y-0.7, 轨道 y+1.7]`，而模型占 `[轨道 y+0.26, 轨道 y+2.93]` | `TargetMinecart.java:74,79-91` | 已修 |
| B1 人形标靶**看得见却打不中** + **朝向异常** | `BlockMetadata` 的 facing 编码用了 ForgeDirection ordinal 顺序（0=N 1=S 2=W 3=E），而 1.20 是 `Direction#get2DDataValue()`（0=SOUTH 1=WEST 2=NORTH 3=EAST）。**模型旋转**读 `facing2D`（错）、**命中盒薄板方向**读 `facing(...).offsetX`（自洽）、**射击面校验**读 `facing(...)` ⇒ 可见靶面与「会被判定的面」错开 90° | `block/BlockMetadata.java`（**r8-blocks 作用域，已由其修复**） | 已修（他人 scope，本文只复核 + 记录依赖） |
| B2 人形标靶三个 Target\* 文件本身 | 与上游逐行对照**无偏差**（见 §2.2） | — | 无需改动 |

改动文件（2 个，全部在本任务写作用域内）：

1. `src/main/java/com/tacz/guns/entity/TargetMinecart.java`
2. `src/main/java/com/tacz/guns/client/renderer/entity/TargetMinecartRenderer.java`

自检：`mcp__idea__lint_files(min_severity="error")` → `{"items":[]}`（0 error）；未运行 gradle。

---

## 1. 标靶矿车

### 1.1 两个版本「车体帧原点」与「AABB 锚点」的差异（数值）

**AABB 锚点**

| 版本 | AABB 底面的定义 | 平直轨道上 posY | 2.4 高实体的盒子 |
| --- | --- | --- | --- |
| 1.20 | `EntityDimensions#makeBoundingBox` ⇒ **posY 就是底面** | `轨道 y + 0.0625`（`AbstractMinecart.java:521` 的 `setPos(getX(), vec33.y, getZ())` + `:640` 的 `d4 = j + 0.0625`） | `[轨道 y+0.0625, 轨道 y+2.4625]` |
| 1.7.10（本次修复前） | `Entity#setPosition` ⇒ **底面 = `posY - yOffset`**（`Entity.java:352`） | `轨道 y + 0.5`（见下） | `yOffset=1.2` ⇒ `[轨道 y-0.7, 轨道 y+1.7]` ❌ |
| 1.7.10（修复后） | 同上 | 同上 | `yOffset=0.4375` ⇒ `[轨道 y+0.0625, 轨道 y+2.4625]` ✅ |

1.7.10 轨道上 posY = `轨道 y + 0.5` 的推导（三步，都在 `EntityMinecart.java`）：

1. `func_145821_a:510` `this.posY = p_145821_2_`（= 轨道方块 y）；
2. `:642` `setPosition(posX, posY + yOffset, posZ)`；
3. `:669` 收尾再 `setPosition(posX, vec31.yCoord, posZ)`，而 `func_70489_a:815` 对平直轨道给出
   `d5 = (double)j + 0.5D`（`matrix[0] = {{0,0,-1},{0,0,1}}` ⇒ `aint[0][1]=0`、`d11=0`）⇒ **posY = 轨道 y + 0.5**。

**渲染帧原点**（`RenderMinecart#doRender`）

`RenderMinecart.java:83` 的 `glTranslatef(p_76986_2_, p_76986_4_, p_76986_6_)` 里 `p_76986_4_` 已被
`:71` 改写成 `(vec31.yCoord + vec32.yCoord) / 2.0D`；`func_70495_a:760` 同样把 y 归到 `j`（上升轨 `j+1`）
⇒ **帧原点 = `轨道 y + 0.5`**。1.20 侧同一步（`MinecartRenderer.java:60`）用的 `getPos/getPosOffs`
给的是 `轨道 y + 0.0625`（`AbstractMinecart.java:640`）。

⇒ 两版**帧原点相差 0.4375**，而 r6 的换算只保证「帧内偏移一致」（`pre107⁻¹·pre120`），
模型整体因此被抬高 0.4375 格 = 7 px，视觉上就是「浮空」。

**模型相对帧原点的 y 区间**（用 `BedrockModel#convertPivot` 的 `24 - y` 与 `BedrockModelPOJO`
的 16 单位/格换算，见 §1.3）：车体底板 `0 ~ +0.07`、靶面 `+0.81 ~ +1.83`、头颅 `+1.83 ~ +2.39`、
最低斜撑 `-0.24`。修复前整段抬到 `轨道 y+0.26 ~ 轨道 y+2.93`（原版空矿车车体在轨道 `+0.19 ~ +0.81`），
修复后落到 `轨道 y-0.18 ~ 轨道 y+2.49` —— 与 1.20 完全一致（上游本身就让斜撑插进轨道块 0.18 格）。

### 1.2 改动

| 文件 | 改动 | 依据 |
| --- | --- | --- |
| `entity/TargetMinecart.java:53-55` | 抽出 `TARGET_WIDTH=0.75F` / `TARGET_HEIGHT=2.4F` 两个常量 | 上游 `TargetMinecart.java:41` `.sized(0.75F, 2.4F)` |
| `entity/TargetMinecart.java:57-74` | 新增 `Y_OFFSET = 0.4375F`（= `0.5 - 0.0625`）并写明两版锚点差异 | 上游 `EntityDimensions#makeBoundingBox` + `AbstractMinecart.java:521/640`；1.7.10 `Entity.java:352`、`EntityMinecart.java:510/642/669/815` |
| `entity/TargetMinecart.java:79-91` | 两个构造器都改成 `setSize(TARGET_WIDTH, TARGET_HEIGHT)` + `yOffset = Y_OFFSET`（替换原 `height / 2`） | 两个入口（1 参构造 = 存档/刷怪蛋；3 参构造 = `TargetMinecartItem#onItemUse` 放置）必须同尺寸同锚点 |
| `client/renderer/entity/TargetMinecartRenderer.java:127-134` | 装载物内层 `translate(0, 1.875, 0)` → `translate(0, **1.4375**, 0)`（扣掉帧原点差），推导注释同步改写 | 见 §1.3 |

`1.875 - 0.4375 = 1.4375`：r6 那条 `inner107 = T(0,1.875,0)·S(1.5)·Rx(180)` 是**相对帧原点**的正确值，
再叠加帧原点自身的高度差即得上游的世界坐标高度。

### 1.3 渲染常数复核（把 r6 的结论重算一遍，确认 1.875 只差帧原点）

```
pre120 = T(0,0.375,0)·S(0.75)·T(-0.5,(k-8)/16,0.5)·Ry(90)      # MinecartRenderer.java:69,87-89, k=6
pre107 = S(0.75)·T(0,6/16,0)                                    # RenderMinecart.java:105-109
inner120 = T(0.5,1.875,0.5)·S(1.5)·Rz(180)·Ry(-90)              # 上游 TargetMinecartRenderer.java:56-59

inner107 = pre107⁻¹ · pre120 · inner120
         = T(0,-0.375,0)·S(4/3)·T(0,0.375,0)·S(0.75)·T(-0.5,-0.125,0.5)·Ry(90)·inner120
         = T(-0.5,0,0.5)·Ry(90)·inner120                        # y 项 -0.375+0.5-0.125=0 精确相消
         = T(0,1.875,0)·S(1.5)·Rx(180)                          # Ry90·Rz180·Ry-90 = Rx180
```

模型坐标空间：`BedrockModel#convertPivot(24 - pivot.y)`（`BedrockModel.java:382-405`）与
`convertOrigin`（`:432-446`）把基岩的 y-up 折进 Java 的 y-down，`Rx(180)` 再翻回来，
两者抵消 ⇒ **模型脚底 y_bedrock=0 对应帧原点**、世界 y = `1.125 · y_bedrock / 16`
（`S(0.75)·S(1.5)`；`T(0,1.6875,0)` 与 `24/16` 项的 1.6875 相消）。用这条核对
`target_minecart.json` 的极值：y_bedrock ∈ `[-3.42, 34.5]`（`origin [-4.56586,-3.41942,…]` 与 `head` 的
`26+8`）⇒ 相对帧原点 `[-0.24, +2.43]`，与 r6 §3.2 的表一致。

### 1.4 副作用面核对

- `yOffset` 在 1.7.10 的消费者只有：`Entity#setPosition:352`（AABB 锚点）、`#isOffsetPositionInLiquid:464`、
  `#moveEntity` 收尾 `:591/:824`（`posY = box.minY + yOffset - ySize`）、`#setLocationAndAngles:1287`、
  `EntityRenderer#orientCamera`（相机，仅 `renderViewEntity` 为玩家时）。矿车不做相机 ⇒ 只影响 AABB。
  三条自洽式在任意 `yOffset` 下都成立，改动不会让实体「陷地/弹起」。
- 轨道检测 `func_70489_a:795` / `func_70495_a:745` 有 `floor(posY) - 1` 兜底：`yOffset=1.2` 时 posY 落在
  `轨道 y+1`，全靠这条兜底才找回轨道；`yOffset=0.4375` 时 `floor(posY)` 直接就是轨道层 ⇒ 比原来更稳。
- 骑乘高度不变：`EntityMinecart#getMountedYOffset:168-171` 返回常量 `-0.3`，骑手脚底 = `posY-0.3`
  = `轨道 y + 0.2`（与原版空矿车一致）；轨道上 posY 与 `yOffset` 无关，所以本次不改骑乘。
- 掉落/攻击/音效/`EntityHurtByGunEvent.Post`/`ServerMessageGunHurt` 链路未动。
- `func_145817_o()` 仍返回 `Blocks.stone` 当渲染 gate（r6 结论，未改）。

---

## 2. 人形标靶

### 2.1 「看得见却打不中」的根因链（跨 scope：`BlockMetadata`，已由 r8-blocks 修复）

旧编码（修复前）：

```
facing2D(): NORTH→0, SOUTH→1, WEST→2, EAST→3        # ForgeDirection ordinal 顺序
facing():   0→NORTH, 1→SOUTH, 2→WEST, 3→EAST        # 与 facing2D 互逆，所以「自洽」
```

1.20 的真值（`tmp/minecraft-src-1.20.1/.../core/Direction.java:28-31`）：
`NORTH.data2d=2`、`SOUTH=0`、`WEST=1`、`EAST=3`。

以「玩家面朝 SOUTH 放置」为例（`BlockMetadata.horizontalDirection(placer)` 本来就是对的，返回 SOUTH）：

| 环节 | 旧编码下的取值 | 1.20 应有 | 后果 |
| --- | --- | --- | --- |
| metadata | `facing2D(SOUTH) = 1` | 0 | — |
| 命中盒薄板方向 `TargetBlock.shapeBoxes` 的 `facing(1).offsetX` | `SOUTH.offsetX = 0` ⇒ **Z 向薄板** | Z 向薄板 | ✅ 正确 |
| 模型旋转 `TargetRenderer.java:142` `Axis.YN.rotationDegrees(facing2D(facing)*90)` | `1*90` ⇒ `Ry(-90°)` | `0` ⇒ `Ry(0)` | ❌ **模型整体转错 90°** |
| 射击面校验 `TargetBlock.java:312` `hitFace.getOpposite() != facing(metadata)` | 只认 `SOUTH` 面（从 z 小侧射入） | 同上（语义没错） | 与「看得见的面」错开 90° |

⇒ 玩家对着**屏幕上看得见的靶面**（此时其法线已被转成沿 X）从东/西开枪：`hit.sideHit` = EAST/WEST，
`getOpposite()` 也是 EAST/WEST ≠ `facing()=SOUTH` ⇒ `onBulletHit` 立刻 `return false`，
红石、`tacz:target_block_hit` 音效、击倒动画、`message.tacz.target_minecart.hit` 提示、`TargetHitTrigger`
**全部不触发** ⇒ 表现就是「整块没有命中体积、开枪毫无反应」，同时「朝向异常」。
（若打到判定面那一侧则会正常反应——用户不会去试那一侧。）

r8-blocks 已把 `BlockMetadata.facing2D/facing` 改成 1.20 的 `get2DDataValue()` 语义
（0=SOUTH 1=WEST 2=NORTH 3=EAST），三个环节重新对齐。**Target\* 侧不需要任何代码改动**
（`TargetRenderer.java:142` / `TargetBlock.java:312,439` / `TargetBlockEntity#getFacing` 全部只调 helper；
本任务已 grep 确认 `*Target*.java` 内没有硬编码方向字面量）。新编码下的自检：

| 环节 | 新编码下的取值 | 1.20 真值 | 一致 |
| --- | --- | --- | --- |
| metadata（面朝 SOUTH 放置） | 0（= 1.20 `SOUTH.data2d`） | 0 | ✅ |
| 命中盒薄板方向 | `facing(0) = SOUTH` ⇒ `offsetX=0` ⇒ Z 向薄板；1.20 `FACING.getAxis()=Z` ⇒ Z 向薄板 | Z 向薄板 | ✅ |
| 模型旋转角度 | `facing2D(SOUTH)*90 = 0` | `get2DDataValue(SOUTH)*90 = 0` | ✅ |
| 射击面校验 | `hitFace.getOpposite() == FACING` | 同 | ✅（`04-gameplay.md:105` 已核过语义等价） |

**旧存档**：当前为未发布移植，不做 metadata 迁移；已放置的标靶朝向会「反向旋转一次」。

### 2.2 Target\* 三文件逐行核对（结论：无需改动）

| 文件 | 核对内容 | 结论 |
| --- | --- | --- |
| `block/TargetBlock.java` | 5 个形状常量 ↔ 上游 `:45-49`、`shapeBoxes` ↔ `getShape:102-110`（含 `!STAND` 的上半块返回空）、`addCollisionBoxesToList` / `getCollisionBoundingBoxFromPool`（先核了 1.7.10 真签名 `Block.java:661/675`，`@Override` 是真覆写）、`canCollideCheck/isCollidable`（默认 true，未覆写）、`getRedstoneStrength` ↔ `:56-79`（中心 0.32、轴映射 Y→max(x,z)/Z→max(x,y)/X→max(y,z)）、`onBlockPlacedBy` ↔ `:169-199`、`updateTick` 复位、`onNeighborBlockChange` 的上下半 STAND 同步（`hit()`/`updateTick` 都用 flag=3 ⇒ 会 `notifyBlockChange` ⇒ 邻居回调链闭合）、掉落（`breakBlock` + `suppressNextDrop`） | 与上游等价；本次未改 |
| `block/entity/TargetBlockEntity.java` | NBT `Owner`/`CustomName`（JSON 文本）+ 移植新增 `OutputPower`；`updateEntity` 的 `oRot/rot` 插值（STAND? −18 : +45，夹 0..90）↔ 上游 `clientTick:49-56`；`hit()` 的 `setBlockMetadataWithNotify(STAND=false, flag=3)` + 红石强度存 TE + `scheduleBlockUpdate(5s)` + `playSoundEffect` ↔ 上游 `hit:131-148`；`getRenderBoundingBox` ↔ 上游 `:127-129`（`[x-2,y,z-2]..[x+2,y+2,z+2]`） | 与上游等价；本次未改 |
| `client/renderer/block/TargetRenderer.java` | 4 行变换与上游 `:50-54` **逐字一致**：`translate(0.5,0.225,0.5)` → `YN(facing2D*90)` → `ZN(180)` → `translate(0,-1.275,0.0125)`；`deg=-lerp(oRot,rot)`；`head.visible` 只对有 owner 的标靶置 true；皮肤走 `SkinManager` + Steve 兜底 | 与上游等价；本次未改 |

**渲染坐标空间验收**（证明 `TargetRenderer` 的模型脚底确实落在方块底面，不存在「位置异常」）：
令模型空间 `p.y = (24 - y_bedrock)/16`，则
`y_world = 0.225 + [ -(p.y - 1.275) ] = 1.5 - p.y = y_bedrock/16` ⇒ 脚底 `y_bedrock=0` → `y_world=0`
（= 下方方块底面），顶（`head` 上沿 `y_bedrock=34.5`）→ `2.156` 格。
这与同目录「实机正常」的 `GunSmithTableRenderer.java:158-160` 的 `translate(0.5,1.5,0.5)` + `ZN(180)` 是
同一个恒等式（`1.5 - (24-y)/16 = y/16`），可交叉验证。

**资源核对**：`textures/block/target.png`、`textures/block/target_n/s.png`、
`textures/entity/target_minecart(_n/_s).png`、`models/bedrock/{target,target_minecart}.json` 均存在；
`target.json` 与上游文件哈希一致（`915FB1B2…`）。

### 2.3 记录（未改，非本任务范围）

1. `target.json` 的 `head`/`head2` 骨骼 UV 用到 32~64，而 `description.texture_width/height = 16/16`；
   这两个骨骼是 0 厚贴片、渲染时绑的是**玩家皮肤**（64×64）。上游同一份资产也是这个组合，属上游既有形态，
   只在「带自定义名字/owner 的标靶」上可见，实机若觉得头像贴图错位，先确认上游同样表现再决定是否处理。
2. 1.7.10 无 `BlockEntity` 的「客户端 tick 数据同步」问题：本方块需要同步的三样东西恰好都不在 TE 里
   （`FACING/HALF/STAND` 在 metadata，随 `S23PacketBlockChange` 下发；`Owner/CustomName/OutputPower` 走
   `getDescriptionPacket`），`rot/oRot` 是客户端按 metadata 自行插值的派生量 ⇒ 不需要额外同步。
3. `TargetMinecart` 仍缺上游 `getMaxCartSpeedOnRail()=0.2F`（需 Mixin 拦 `getCurrentCartSpeedCapOnRail`），
   与本次两个症状无关，继续挂在 `r5` 记录里。

---

## 3. 待 Lead 构建验证点

1. `TargetMinecart.java` 两个构造器都只剩常量引用（`TARGET_WIDTH/TARGET_HEIGHT/Y_OFFSET`），无遗留 `height / 2`；
   `Y_OFFSET` 是 `float` 字面量 `0.4375F`（可精确表示，`0.5-0.0625` 无舍入误差）。
2. `TargetMinecartRenderer` 的 `translate(0, 1.4375, 0)` 与 `scale(1.5f…)`、`mulPose(XP.rotationDegrees(180))` 顺序未变。
3. `block/BlockMetadata.java` 的新 facing 表已由 r8-blocks 落地；确认合并后
   `TargetBlock.java:44` 的 javadoc 表（0=SOUTH 1=WEST 2=NORTH 3=EAST）没有被任何一侧回滚。
4. 编译期不需要新 import（本次改动未新增类型）。

## 4. 实机验证步骤（命中测试）

**A. 标靶矿车**

1. 创造模式「其他」页取 `minecart with target`，放在**平直铁轨**上：
   - ✅ 车体底板压在轨道面上（不再悬空 ~7px）；最低斜撑会插进轨道块一小截（与 1.20 一致）。
   - ✅ 站在轨道一端平视靶面连开数枪：**靶面上沿（头颅下方）也要有反应**（击退抖动 + 音效 + 聊天栏
     `message.tacz.target_minecart.hit` 提示）；修复前只有轨面以上 1.2 格内的射击有反应。
   - ✅ 用 `/tacz targets`？不需要；可直接看聊天栏的伤害/距离数字是否随命中点变化。
2. 沿斜轨（metadata 2~5 的上升轨）再放一辆，确认模型与轨道坡度贴合（`func_70495_a` 的倾斜帧）。
3. 把玩家推到车前 Eye 高度打靶面顶部，确认「上半截也判中」。

**B. 人形标靶**

1. 站在地面**面朝任意一个方向**放下 `target`（两格高），退后 3~5 格，对着**看得见的靶面**开一枪：
   - ✅ 靶面被「打翻」（`rot` 0→90 动画）、有 `target_block_hit` 音效、聊天栏提示、红石输出按命中点强度。
   - ✅ 5 秒后自动复位（`updateTarget` 计划刻）。
   - 修复前：这一步完全无反应（可见面与判定面错开 90°）。
2. 换成东西南北四个朝向各放一个，逐个从「看得见的那一面」射击：**四个都应命中**。
3. 侧面/背面射击：应**不**触发（上游语义：只有 `FACING` 对面那一侧算靶面，见 §2.1 表；
   这条是上游行为，不是缺陷）。
4. 站到远处 64~128 格看标靶是否仍渲染（`TargetRenderDistance` 默认 128）。
5. 铁砧命名后再放一个：顶部出现玩家头像（无皮肤档案 ⇒ Steve），确认朝向与靶面同侧。

## 5. 未修 / 需实机才能定论

1. 矿车头颅双面贴片朝向（0 厚度 + `head2` 的 `translate(0,0,0.01)`，UV 镜像由负 `uv_size` 表达）——
   r6 已登记为「只能实机确认」，本次未动。
2. 斜轨上的模型姿态（依赖 `func_70495_a` 的坡度帧）——推导上等价，需实机看一次。
3. 人形标靶 `target.json` 头部的皮肤 UV（见 §2.3-1）。
