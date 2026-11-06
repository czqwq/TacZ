# r6-D · TargetMinecart 只有阴影、没有模型

任务：`task-9`。实机：创造模式放置 `minecart with target`，命中判定正常，但世界里**只看到实体阴影**。

结论：**根因成立**（装载物渲染钩子被 1.7.10 的 renderType gate 挡掉），已修。另外顺带发现并修掉了**同一处代码里的第二个必现缺陷**：
`renderMinecartContents` 的变换常数照抄的是 **1.20 的实体帧**，在 1.7.10 帧下会把模型转 90° 并偏移 0.5 格 —— 只修 gate 的话，模型会出现但朝向/位置是错的。

改动文件（均在 task 的 write_scopes 内）：

| 文件 | 改动 |
| --- | --- |
| `src/main/java/com/tacz/guns/entity/TargetMinecart.java` | 新增 `func_145817_o()`（gate 触发器）+ 注释 |
| `src/main/java/com/tacz/guns/client/renderer/entity/TargetMinecartRenderer.java` | 变换常数换算到 1.7.10 帧；补 gate 依赖说明 |

未运行 gradle（遵守约定）；`BUILD SUCCESSFUL` 由主 Agent 统一验证。

---

## 一、根因链（带 file:line）

1. `TargetMinecartRenderer` 把模型渲染挂在 1.7.10 的「装载物」钩子上
   —— `client/renderer/entity/TargetMinecartRenderer.java:84 func_147910_a(...)`
   （上游对应 `renderMinecartContents`，`tmp/TACZ-1.20.1/.../TargetMinecartRenderer.java:48`）。
2. 1.7.10 只在 **display block 的 renderType != -1** 时才调这个钩子：
   `build/rfg/minecraft-src/java/net/minecraft/client/renderer/entity/RenderMinecart.java:100-110`
   ```java
   Block block = p_76986_1_.func_145820_n();          // :100
   if (block.getRenderType() != -1) {                 // :103
       ... this.func_147910_a(p_76986_1_, ..., block, j);   // :110
   }
   ```
3. `func_145820_n()` 在**没有自定义装载方块**时返回 `func_145817_o()`：
   `build/rfg/minecraft-src/java/net/minecraft/entity/item/EntityMinecart.java:1086-1097`；
   `func_145817_o()` 默认 `Blocks.air`（`:1099-1102`），而 air 的 `getRenderType()` 是 **-1** ⇒ gate 不通过。
4. 移植侧 `TargetMinecart` 原先**没有**覆写 `func_145817_o()`（grep 全仓无命中）⇒ `block = air` ⇒
   `func_147910_a` **永不执行** ⇒ 只剩 `RenderMinecart` 用
   `InternalAssetLoader.ENTITY_EMPTY_TEXTURE`（透明）画的矿车本体（不可见）+ `RenderManager` 的阴影
   （`RenderManager.java:309 doRenderShadowAndFire`，与 `doRender` 无关）。
5. 上游没这个问题，因为 1.20 的 `TargetMinecart` **覆写了**对应钩子：
   `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/TargetMinecart.java:144-148`
   ```java
   @Override public BlockState getDefaultDisplayBlockState() { return ModBlocks.TARGET.get().defaultBlockState(); }
   ```
   对应 1.20 的 gate 是 `MinecartRenderer.java:84 if (blockstate.getRenderShape() != RenderShape.INVISIBLE)`。

即：**1.7.10 的 `getRenderType() != -1` ⟷ 1.20 的 `getRenderShape() != INVISIBLE`，上游靠「默认 display block 非空气」过 gate；
移植侧漏了这一步的对应实现。**

---

## 二、选型：走方案 1（display block 当触发器），不走方案 2（覆写 `doRender`）

任务书把方案 2 标为推荐，但复核后选**方案 1**，理由：

1. **方案 1 才是上游形态**。上游自己就是用 `getDefaultDisplayBlockState()` 过关的（见上面 :144-148）；
   方案 2 是把 `RenderMinecart#doRender`（`RenderMinecart.java:38-119`，约 80 行：插值、`func_70489_a/func_70495_a`
   轨道姿态、抖动、颜色复位、`bindEntityTexture` 回绑、`scale(-1,-1,1)` + 原版车体）**整段抄进 mod**，
   等于把一份原版代码变成需要长期跟版的副本，且丢掉原版后续修正。
2. **两个方案都要做同一份坐标换算**。方案 2 的所谓「identity PoseStack + GL 已带姿态」其实也必须在 1.7.10
   装载物帧里写常数，因此并不比方案 1 少工作量（见第三节）。
3. 方案 1 的副作用面已核实为**空**：`func_145820_n()` 在 1.7.10 全仓（`build/rfg/minecraft-src`）只有 3 个使用点：

   | 使用点 | 影响 |
   | --- | --- |
   | `RenderMinecart.java:100` | 就是本 gate，正是我们要放行的 |
   | `EntityMinecart#writeEntityToNBT:889` | 外层有 `if (this.hasDisplayTile())`（`:886`）保护；`hasDisplayTile()` 读 dataWatcher byte 22（`:1142-1150`），只有 `func_145819_k`/`setDisplayTileData`/`setDisplayTileOffset`/NBT 读档才会置 1。本类从不调 ⇒ **不写任何 NBT**，存档/同步无变化 |
   | `EntityMinecart#setDisplayTileData:1132` | 本类不调用 |

   装载物偏移仍走 `getDefaultDisplayTileOffset()==6`（`:1119-1122`），与 1.20 `getDefaultDisplayOffset()==6` 一致，无需覆写。
4. **方案 1 的唯一妥协**（已在代码注释里写明）：上游返回的是 TARGET 方块，但本版
   `TargetBlock#getRenderType()` 是 **-1**（`block/TargetBlock.java:505-507`，对应上游
   `RenderShape.ENTITYBLOCK_ANIMATED` → TESR）。1.20 的 gate 放行 ENTITYBLOCK_ANIMATED，1.7.10 的 `-1`
   不放行 —— 这是两个版本 gate 语义的**真实差异**，所以只能换一个 `renderType != -1` 的方块当触发器。
   取 `Blocks.stone`（renderType 0，两侧都非 null）。该方块**永远不会被画**：`func_147910_a` 已被覆写，
   `RenderMinecart:106` 绑的方块图集只是被我们的 `RenderType.setupRenderState()` 覆盖掉。

---

## 三、坐标换算推导（1.20 实体帧 → 1.7.10 实体帧）

### 3.1 两个版本进入「装载物」时的帧

**1.20**（`tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/client/renderer/entity/MinecartRenderer.java`）：

```java
posestack.translate(0.0F, 0.375F, 0.0F);                 // :69
posestack.mulPose(Axis.YP.rotationDegrees(180.0F - f));   // :70
posestack.mulPose(Axis.ZP.rotationDegrees(-f5));          // :71
... 抖动 rotX ...
int k = t.getDisplayOffset();                             // :82  (= 6, AbstractMinecart.java:818-820)
posestack.scale(0.75F, 0.75F, 0.75F);                     // :87
posestack.translate(-0.5F, (k - 8) / 16.0F, 0.5F);        // :88  → (-0.5, -0.125, 0.5)
posestack.mulPose(Axis.YP.rotationDegrees(90.0F));        // :89
this.renderMinecartContents(...);                          // :90
```

**1.7.10**（`RenderMinecart.java`）：

```java
glTranslatef((float)x, (float)y, (float)z);                // :83
glRotatef(180.0F - yaw, 0, 1, 0);                          // :84
glRotatef(-pitch, 0, 0, 1);                                // :85
... 抖动 rotX ...                                          // :96
glScalef(0.75F, 0.75F, 0.75F);                              // :108
glTranslatef(0.0F, (float)k / 16.0F, 0.0F);                // :109  k = getDisplayTileOffset() = 6 → (0, 0.375, 0)
this.func_147910_a(...);                                    // :110
```

两边的「车体帧」只差 `Translate(0, 0.375, 0)`，其余（抖动、`rotY(180-yaw)`、`rotZ(-pitch)`、伤员抖动 rotX）逐项相同
（1.7.10 轨道姿态 `RenderMinecart.java:52-81` 用 `func_70489_a/func_70495_a`，1.20 `MinecartRenderer.java:47-67`
用 `getPos/getPosOffs`，语义与偏移量相同）。

**帧原点取「车体帧」而不是「轨道面」，这是有意的**（已逐项核对两版的原点高度）：

| | 实体 posY（在平直轨道上） | 渲染器送给渲染钩子的 y |
| --- | --- | --- |
| 1.20 | `轨道 y + 0.0625`（`AbstractMinecart#getPos:640` `d4 = j + 0.0625` + `moveAlongTrack:521 setPos`） | 再叠加 `vec3 - posY ≈ 0` ⇒ **轨道 y + 0.0625** |
| 1.7.10 | `轨道 y`（`EntityMinecart#func_145821_a:510 this.posY = p_145821_2_`） | 再叠加 `(vec31.y+vec32.y)/2 - posY`，而 `func_70489_a:815` 给 `d5 = j + 0.5` ⇒ **轨道 y + 0.5** |

即两版的车体帧原点在物理上差 **0.4375**。本修复选择「**保持相对车体帧的偏移不变**」（而不是把上游的轨道相对高度搬过来），
理由：按车体帧对齐后，TACZ 模型最低点 = 轨道 + 0.26、车体底板 = 轨道 + 0.5，与 1.7.10 原版空矿车车体
（帧原点 ±0.31 ⇒ 轨道 + 0.19 ~ +0.81）落在同一高度带；若强行按轨道面对齐（整体 -0.4375），模型最低点会到
轨道 - 0.18（陷进轨道下方方块里）。视觉上「靶车压在原版矿车该在的位置」比「和 1.20 的绝对高度一致」更重要。

记：
- `pre120 = T(0,0.375,0) · S(0.75) · T(-0.5,-0.125,0.5) · Ry(90)`
- `pre107 = S(0.75) · T(0,0.375,0)`

要在 1.7.10 帧里复现上游的**同一帧内偏移**，内层必须满足 `pre107 · inner107 = pre120 · inner120`，即

```
inner107 = pre107⁻¹ · pre120 · inner120
         = T(0,-0.375,0) · S⁻¹ · T(0,0.375,0) · S · T(-0.5,-0.125,0.5) · Ry(90) · inner120
         = T(0,-0.375,0) · T(0,0.5,0) · T(-0.5,-0.125,0.5) · Ry(90) · inner120      [S⁻¹·T·S = T(0, 0.375/0.75, 0)]
         = T(-0.5,0,0.5) · Ry(90) · inner120                                        [y 项 -0.375+0.5-0.125 = 0 精确相消]
```

上游 `inner120 = T(0.5,1.875,0.5) · S(1.5) · Rz(180) · Ry(-90)`（`TargetMinecartRenderer.java:56-59`），代入：

```
inner107 = T(-0.5,0,0.5) · Ry(90) · T(0.5,1.875,0.5) · S(1.5) · Rz(180) · Ry(-90)
         = T(-0.5,0,0.5) · T(Ry90·(0.5,1.875,0.5)) · Ry(90) · S(1.5) · Rz(180) · Ry(-90)
           Ry90 把 (x,y,z) 映成 (z, y, -x) ⇒ (0.5,1.875,-0.5)
         = T(0,1.875,0) · Ry(90) · S(1.5) · Rz(180) · Ry(-90)
           [-0.5+0.5, 0+1.875, 0.5-0.5]
         = T(0,1.875,0) · S(1.5) · Ry(90) · Rz(180) · Ry(-90)          [均匀缩放与旋转可交换]
         = T(0,1.875,0) · S(1.5) · Rx(180)
           [共轭旋转：Ry(90)·Rz(180)·Ry(-90) = R_{Ry90·ẑ}(180) = R_x̂(180)]
```

于是代码里只剩三行：

```java
stack.translate(0, 1.875, 0);
stack.scale(1.5f, 1.5f, 1.5f);
stack.mulPose(Axis.XP.rotationDegrees(180));
```

（180° 对 ±x 等价，`Axis.XN` 同样正确。）

### 3.2 用几何 sanity check 反证换算

模型 `assets/tacz/models/bedrock/target_minecart.json` 的 y 在 `BedrockModel#convertPivot` 里按 `24 - pivot.y`
翻转（`BedrockModel.java:398-400`），即「模型 y 向下」。把上式作用到几个关键部件（单位：格，相对车体帧原点）：

| 部件 | bedrock y | 换算后世界 y（相对帧原点） |
| --- | --- | --- |
| 车体底板（`bottom` 的 6×1×8 等） | 0 ~ 1 | **0 ~ 0.07** |
| 靶面竖板（`bone2`/`bone3`） | 12.5 ~ 25 | **0.81 ~ 1.83** |
| 玩家头颅（`head`/`head2`） | 26 ~ 34 | **1.83 ~ 2.39**（整好压在靶面顶沿） |
| 最低斜撑/轮（含负 bedrock y 的 45° 方块） | -3.42 | **-0.24** |

对照 1.7.10 原版空矿车车体（`ModelMinecart` 在 `scale(-1,-1,1)` 后约 `-0.31 ~ +0.31`，帧原点在轨道 `y+0.5` 处）：
TACZ 模型的车体底板落在帧原点上方 0~0.07、最低点 -0.24，与原版矿车车体落在同一高度带 ⇒ **无需额外 y 修正**。
同一套数字在 1.20 帧下重算得到**相对车体帧完全相同**的偏移（这是 3.1 恒等式的必然结果）。

### 3.3 换算前会看到什么（说明为什么这不算「顺手改」）

旧常数 `T(0.5,1.875,0.5)·S(1.5)·Rz(180)·Ry(-90)` 在 1.7.10 帧下等价于
`T(0.375,1.6875,0.375)·S(1.125)·Ry(-90)·Rx(180)` —— 比正确结果多一层 `Ry(-90)`（模型整体侧转 90°）
外加水平偏移 `(0.375, 0.375)`。只修 gate 不修常数 = 出现一个**横着的、偏 0.5 格的靶车**。

---

## 四、已修 / 未修

**已修**

- ✅ gate：`TargetMinecart#func_145817_o()` 返回 `Blocks.stone`（renderType 0）⇒ `func_147910_a` 会执行。
- ✅ 变换常数换算到 1.7.10 装载物帧（`TargetMinecartRenderer.java:126-135`，含推导注释）。
- ✅ gate 触发器在 `entity/TargetMinecart.java:108-111 func_145817_o()`。
- ✅ 文档：`func_147910_a` 的 javadoc 写明「本钩子有前置条件」，避免下一个人又把它当无条件回调；
  类注释补一条 1.7.10 平台替换项。

**未修（记录，非本任务范围）**

- ⬜ **命中盒与模型错位（建议另开任务）**：`TargetMinecart` 两个构造器里 `this.yOffset = this.height / 2.0F`
  （`entity/TargetMinecart.java:63,73`）是从原版 `EntityMinecart` 构造器照抄的，但两版 boundingBox 锚点语义不同：
  1.20 `EntityDimensions#makeBoundingBox:24` 的 box 是 `[posY, posY+height]`（posY = box 底），
  所以上游 `.sized(0.75F, 2.4F)` 的盒子正好罩住模型（模型相对车体帧 `-0.24 ~ +2.39`）；
  1.7.10 `Entity#setPosition:352` 的 box 是 `[posY-yOffset, posY-yOffset+height]`，取 `yOffset = height/2 = 1.2`
  ⇒ box 变成 `[posY-1.2, posY+1.2]`，**靶面上半截 + 头顶全部落在盒外**，盒子下半 0.96 是空的。
  即：模型修好后会看到「可见的靶面上半打不中」。
  按 3.1 的表，1.7.10 的 posY 比 1.20 的 posY 低 0.4375，故忠实等价值是 `yOffset = -0.5` 左右（需实机确认）。
  **本任务不改**：这会改动命中判定（用户报「命中判定正常」），且影响轨道物理/挤压，风险面比渲染大，应有独立验证。
- ⬜ 头颅皮肤：无皮肤档案时固定 Steve（`TargetMinecartRenderer.java` 的 `resolveSkin`，已有 `TODO(L2)`，
  上游用 `DefaultPlayerSkin` 按 UUID 选 Alex/Steve；1.7.10 无此 API）。本任务未动。
- ⬜ 无自定义名字的靶车不画头颅（`getGameProfile()` 为 null 时直接把 `head/head2` 置为不可见）——**与上游一致**，不是缺陷。
- ⬜ 原版空矿车车体仍会被 `RenderMinecart:116-117` 画一次（贴图 `ENTITY_EMPTY_TEXTURE` 全透明 ⇒ 不可见）。
  现状与上游等价（上游同样先 `super.render` 再画 contents），不动。
- ⬜ 实机外观未能验证（无运行环境；本任务只做静态修改 + 换算推导）。

**自检**：`mcp__idea__lint_files(min_severity="error")` 对两个改动文件 → `{"items":[]}`，**0 error**（未跑 gradle）。
最终 diff 只含：`TargetMinecartRenderer.java` +21 行（1 条注释、1 处常数替换、2 段 javadoc）、
`TargetMinecart.java` +29 行（2 个 import + `func_145817_o()` 及其注释）；**无**调试打印、无临时开关、无死代码。

---

## 五、实机验证步骤

**静态无法定论、必须实机看的项目**（推导能保证「与上游同帧内偏移一致」，不能保证「上游本身在当时是否好看」）：

1. **头颅朝向**：`head`/`head2` 是 0 厚度的双层贴片，靠 `head2` 的 `translate(0,0,0.01)` 制造双面感；
   两侧头像应分别朝外。静态可算出两面在模型 z ≈ ±0.34（乘 1.125 后），朝内/朝外与**贴图 UV 的镜像**有关
   （`target_minecart.json:317-322` 的 `south.uv_size = [-8,8]` 负宽度 = 镜像），只能实机确认。
2. **整体大小**：`S(1.5)·S(0.75) = 1.125`，靶面高 1.83 格；`visible_bounds_height = 3.5` 只是模型自报的可视包围，
   不能反推缩放。若实机觉得过大/过小，单点改 `stack.scale(...)`（上游值 1.5，非 1.5 就是偏离上游）。
3. **靶面与轨道方向**：由共轭恒等式保证「与上游一致」，但上游自己是否让靶面朝前/朝侧面，需要实机沿轨道看。
4. **命中盒与可见模型是否重合**：见 §四第一条（`yOffset`），实机打靶面上半截即可确认。

1. 主 Agent `./gradlew build` 后启动客户端（本任务未运行 gradle）。
2. 创造模式物品栏 → 「其他」页取 `minecart with target`，分别：
   a. 放在**铁轨**上并推动（检查车体与轨道的高度关系、滚动抖动）；
   b. 直接放在**地面**上（检查不依赖轨道姿态）；
   c. 用铁砧/命名牌给它改名后再放置（检查头颅是否出现、是否压在靶面顶沿）。
3. **应当看到**：完整靶车模型 = 车体底板 + 竖直靶面（双面）+ 顶上的玩家头颅；阴影大小不变（`shadowSize=0.25`）。
4. 铺 4 个方向（NS / EW / 斜向）的轨道各放一辆，确认靶面**始终沿轨道方向**（即靶面法线 = 车体局部 z 轴）。

**若外观仍不对，按症状定位（都是单点常数，改一行即可）**

| 症状 | 可能项 | 怎么改 |
| --- | --- | --- |
| 靶面沿轨道 **横向**（侧转 90°） | 共轭那步（`Ry90·Rz180·Ry(-90) = Rx180`）理解反了 | 把 `Axis.XP.rotationDegrees(180)` 换成 `Rz(180)`+`Ry(-90)` 的**未共轭**写法（即回到上游两条 `mulPose`），再整体补 `Ry(90)` |
| 水平偏 0.5 格 | `T(-0.5,0,0.5)·Ry(90)` 的相消项 | 在 `translate(0,1.875,0)` 前面补一条 `stack.translate(dx, 0, dz)` |
| 整体**下沉/悬浮** | y 项（推导结论是 `-0.375+0.5-0.125=0` 精确相消） | `stack.translate(0, 1.875 + DY, 0)`，`DY` 单点可调；正确值 0 |
| 上下颠倒 | `Rx(180)` | `Axis.XP` → 去掉该 `mulPose` 观察 |
| 完全不可见 | gate 又被挡回去 | 检查 `TargetMinecart#func_145817_o()` 返回方块的 `getRenderType()` 是否 != -1（`Blocks.stone` = 0）；确认 `ModEntitiesRender` 仍注册 `TargetMinecartRenderer` |

参考坐标系（便于判读）：帧原点 = 车体实体 `posY`，平直轨道上等于 `轨道方块 y + 0.5`；靶面顶沿在原点 +1.83，
头颅底在原点 +1.83，车体底板在原点 +0 ~ +0.07。
