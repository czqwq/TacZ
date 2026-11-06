# r8 · 方块贴图 / 朝向问题报告

> **⚠ 最终方案（2026-11-07 更新，取代下文早期「自研 JSON 模型渲染器」方案）**
>
> 本仓运行环境自带 **GTNHLib**，它提供完整的 1.7.10 JSON 模型系统
> （`com.gtnewhorizon.gtnhlib.client.model`：blockstate variants/multipart、模型父子链、x/y 旋转、
> 逐面 UV/cullface/tint、自动把模型贴图注册进方块图集、`ModelISBRH` 世界/物品渲染）。
> 因此最终**删除**了 r8 早期自研的 `JsonBlockModelRenderer`/`BlockModelQuads`/`BlockStateModels`/
> `JsonResourceReader`/`BlockModelGuiItemRenderer`，改为直接使用 GTNHLib：
>
> 1. `ClientProxy#preInit` → `ModelRegistry.registerModid("tacz")`：允许 GTNHLib 扫描本模组 jar 的
>    `assets/tacz/blockstates/**` + `models/**` 并自动加载模型引用的贴图（走 1.20 的
>    `assets/tacz/textures/block/**` 路径，绕开 1.7.10 的 `textures/blocks/**` 约定）；
> 2. 新增 `block/TaczBlockStateProperties`：把 1.20 BlockState 属性映射成 GTNHLib `BlockProperty`
>    注册到方块上（弹药库 `facing`；枪架 `facing/shape/dyed`；武器盒 `facing/locked/left/right/up/down`），
>    取值与 blockstate JSON 完全一致（已用脚本核对：弹药库 4/4、枪架 24/24 变体、武器盒 168/168 multipart 条件全部可命中）；
> 3. `ClientSetupEvent` 里把 `ModelISBRH.JSON_ISBRH_ID` 写入 `BlockRenderIds`，三个方块的
>    `getRenderType()` 返回它；
> 4. 工作台/雕像/标靶仍是 BER（`RenderShape.ENTITYBLOCK_ANIMATED`），显式实现 GTNHLib
>    `BlockModelInfo` 返回 `nhlib$isModeled()=false` 做 **opt-out**，避免 GTNHLib 按 blockstate 文件把它们
>    误接管（否则它们的物品图标/方块图标会被空模型覆盖）。
>
> 早期自研方案（下文 §1-§3 与「Lead 追加修正」1/2/3）保留作**历史记录与平台踩坑证据**，
> 其中「classpath 资源包」`ModAssetResourcePack` 仍然保留（作为其它模组 jar 资源的兜底），
> 但方块模型/贴图不再依赖它。
>
> 待实机确认：三个方块世界内模型/贴图、四朝向、武器盒玻璃/拼接、物品图标。若仍有问题，可用
> `run/client/config/gtnhlib.cfg` 的 `B:enableModelDebugLogs=true`（已默认打开）抓详细模型选择日志。

# r8-1 方块贴图与放置朝向修复

任务：`task-34`（r8-1）。实机问题三组：

- **A** Iron Ammo Box 系列（`block/AmmoStorageBlock` 三档材质）贴图错误；
- **B** 武器盒（`block/GunDisplayCaseBlock`）与钻石弹药箱贴图错误；三个工作台（`GunSmithTable A/B/C`）模型与贴图正常；
- **C** 方块放置朝向异常：放下的工作台/雕像等默认正面朝向左/右。

结论：**A/B 是同一个根因**（1.20 `RenderShape.MODEL` 的 JSON 方块模型在移植里退化成了「单 `IIcon` 立方体」），
**C 是另一个根因**（`BlockMetadata` 的水平朝向 2 bit 编码表与 1.20 `Direction#get2DDataValue()` 不一致）。
两者都是移植偏差，不是贴图资产问题（资产与上游哈希一致，见 §1.1）。

---

## 1. 根因

### 1.1 先排除资产：贴图本身没问题

- `src/main/resources/assets/tacz/textures/block/*.png` 与上游一致（Lead 已逐文件比对哈希）；
- 上游 1.20 的贴图在 `textures/block/`（复数 `blocks/` 才是 1.7.10 的图集目录），移植已把同名文件复制到
  `textures/blocks/` 共 45 个，`registerIcon("tacz:<name>")` 因此能解析到；
- 所以**本轮不改任何资产**。`ammo_storage.png` 是 64×64、`display_case_all.png` 是 128×128、
  `pegboard.png` 是 64×64 —— 它们是「4×4 / 8×8 张 16×16 小图拼成的大图」，靠模型的逐面 `uv` 裁剪取用。

### 1.2 根因 A/B：`RenderShape.MODEL` 没有 1.7.10 等价渲染

上游按 `RenderShape` 分成两类，**这正是「工作台正常、弹药箱/武器盒错」的分界线**：

| 方块 | 上游 `getRenderShape` | 上游渲染方 | 移植侧 | 实机 |
|---|---|---|---|---|
| `AmmoStorageBlock` | `MODEL`（`AmmoStorageBlock.java:159-162`） | 原版 baked JSON 模型 | `getRenderType()==0` → `RenderBlocks` 立方体 | **错** |
| `GunDisplayCaseBlock` | `MODEL`（`GunDisplayCaseBlock.java:236-239`） | 同上（multipart 模型） | 同上 | **错** |
| `GunPegboardBlock` | `MODEL`（`GunPegboardBlock.java:226-229`） | 同上 | 同上 | 错（用户未单独报） |
| 工作台 A/B/C | `ENTITYBLOCK_ANIMATED`（`AbstractGunSmithTableBlock.java:69-71`） | BER | `getRenderType()==-1` + `GunSmithTableRenderer`（TESR） | 正常 |
| 雕像 / 人形标靶 | `ENTITYBLOCK_ANIMATED`（`StatueBlock.java:138-140`、`TargetBlock.java:241-243`） | BER | `-1` + `StatueRenderer`/`TargetRenderer` | 正常 |

退化后的外观：`RenderBlocks` 用 `registerBlockIcons` 的**单个 IIcon** 铺满立方体每个面，UV 是整张 sprite
（0..1）⇒ 64×64 / 128×128 的大图被压到 1 个面上，得到截图里「黑面板 + 白色/青色线条」的碎块外观
（`display_case_all.png` 是玻璃 + 透明区，alpha test 之后只剩边缘）。

上游模型事实（逐文件核对）：

- `models/block/ammo_storage.json`：**1 个元素 = 完整立方体**，`texture_size:[64,64]`，六面各取 `uv:[0,0,4,4]`
  等 4×4 区域（= 64×64 贴图里的一个 16×16 小图）；
- `models/block/gun_pegboard*.json`：1~2 个薄板元素（3/16 厚），逐面 UV；
- `models/block/display_case_*.json`：背板 / 8 个角 / 4 个边 / 玻璃单双片，共 29 个模型，
  `blockstates/gun_display_case.json` 用 **multipart** 按 `facing` + `locked` + `left/right/up/down` 组合挑选。

### 1.3 根因 C：水平朝向 2 bit 编码表错位

- 上游 `Direction#get2DDataValue()` 的取值由枚举构造器第 3 个参数 `data2d` 决定：
  `tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/core/Direction.java:28-31`

  ```
  NORTH(2, 3, 2)  SOUTH(3, 2, 0)  WEST(4, 5, 1)  EAST(5, 4, 3)
                  ^data2d=2      =0             =1            =3
  ⇒ get2DDataValue():  SOUTH=0  WEST=1  NORTH=2  EAST=3
  ```

- 移植 `block/BlockMetadata.java` 旧实现是 `NORTH=0, SOUTH=1, WEST=2, EAST=3`
  （= `ForgeDirection` ordinal 顺序减 2），注释却写着「顺序与 1.20.1 一致」——**注释与实现都反了**。
- 由此所有「`facing2D` → 旋转角」的渲染都错位。以工作台为例
  （上游 `AbstractGunSmithTableBlock.java:108-110`：
  `parseRotation = 90*(3-get2DDataValue())-90`；移植 `AbstractGunSmithTableBlock.java:301-310` 同一公式）：

  | FACING | 上游角度 | 旧移植角度 | 偏差 |
  |---|---|---|---|
  | NORTH | `90*(3-2)-90 = 0` | `90*(3-0)-90 = 180` | 180° |
  | SOUTH | `90*(3-0)-90 = 180` | `90*(3-1)-90 = 90` | 90° |
  | WEST | `90*(3-1)-90 = 90` | `90*(3-2)-90 = 0` | 90° |
  | EAST | `90*(3-3)-90 = -90` | `90*(3-3)-90 = -90` | 0° |

  ⇒ 表现为「放下来正面朝向左/右」（West/East 一档对、其它三档错位），与用户描述完全吻合。

**注意**：`BlockMetadata.horizontalDirection(placer)`（yaw → 水平方向）本来就是对的
（等价 `Direction.fromYRot`，`Direction.java:285-287`），错的是它的逆表 `facing2D`/`facing`。
解码表本身自洽（`withFacing` 写、`facing` 读同一张表），所以只改表即可，不需要改任何调用方。

---

## 2. 改动清单

### 2.1 朝向（C）

| 文件 | 改动 | 上游依据 |
|---|---|---|
| `block/BlockMetadata.java` | `facing2D(ForgeDirection)` / `facing(int)` 改为 `SOUTH=0, WEST=1, NORTH=2, EAST=3`；类注释与两个方法的 javadoc 改写为正确的表并标注「不能用 ForgeDirection ordinal 顺序」 | `tmp/minecraft-src-1.20.1/.../core/Direction.java:28-31`、`:123-125`、`:252-254` |
| `block/{AbstractGunSmithTableBlock,AmmoStorageBlock,GunDisplayCaseBlock,GunPegboardBlock,GunSmithTableBlockA/B/C,StatueBlock,TargetBlock}.java` | 只改类注释里的编码表（`0=NORTH 1=SOUTH 2=WEST 3=EAST` → `0=SOUTH 1=WEST 2=NORTH 3=EAST`），**无逻辑改动** | 同上 |

连带自动修正的消费点（都是逐字照抄上游公式，输入变对即结果变对，**未改一行代码**）：
| 消费点 | 上游 |
|---|---|
| `AbstractGunSmithTableBlock.parseRotation` (`:301-310`) | `AbstractGunSmithTableBlock.java:108-110` |
| `GunSmithTableBlockB.parseRotation` (`:137-140`) | `GunSmithTableBlockB.java:96-99` |
| `client/renderer/block/StatueRenderer.java:128`（`(facing2D+2)%4*90`） | `StatueRenderer.java:51` |
| `client/renderer/block/TargetRenderer.java:142`（`facing2D*90`） | `TargetRenderer.java:52` |
| `client/renderer/block/DisplayItemTransforms.java:31`（`180 - facing2D*90`） | `DisplayItemTransforms.java:13` |
| `block/{AmmoStorage,Statue,GunDisplayCase,GunPegboard,Target,AbstractGunSmithTable}Block#onBlockPlacedBy` 的 `horizontalDirection(placer).getOpposite()` / `clockwise(...)` | 各上游 `getStateForPlacement(...)` |
| `block/entity/{Statue,Target,GunPegboard,GunDisplayCase}BlockEntity#getFacing()` | 上游 `getBlockState().getValue(FACING)` |

**旧存档影响**：当前为未发布移植，不做 metadata 迁移。已放置方块的朝向会「按新表重新解读一次」，
即旧世界里放下的工作台/雕像/枪架/展示柜/弹药库会旋转 90/180°；重新放置即正确。

> **交叉引用（task-35 / r8-targets，已由其确认）**：同一个 `facing2D` 错位也解释了「人形标靶整块打不中」——
> 标靶的「模型角度（`TargetRenderer.java:142` 读 `facing2D`）+ 碰撞薄板方向（`TargetBlock.java:439` 读
> `facing().offsetX`）+ 射击面校验（`TargetBlock.java:312` 读 `facing()`）」三处都读同一组 helper，
> 旧表下三者互相错开 90°，导致对着看得见的靶面开枪时 `hit.sideHit` 落在垂直侧、`onBulletHit` 直接 `return false`。
> 详见 `docs/review/r8/target.md` §2.1。`Target*` 三个文件本身逐行等价上游、由 r8-targets 判定零改动；
> 本任务只在 `TargetBlock.java` 同步了 1 行编码表注释。

### 2.2 贴图/模型（A+B）

新增 4 个文件 + 改 3 个方块的 renderType：

| 文件 | 作用 |
|---|---|
| `client/renderer/block/BlockModelQuads.java`（新） | 1.20 JSON **方块模型**烘成四边形：`parent` 链合并、`textures` 引用解析、逐面 `uv` + `rotation`、`cullface`、`tintindex` |
| `client/renderer/block/BlockStateModels.java`（新） | 1.20 `blockstates/*.json` 的 `variants` / `multipart` 解析与匹配（含 `x`/`y` 模型旋转） |
| `client/renderer/block/JsonBlockModelRenderer.java`（新） | `ISimpleBlockRenderingHandler`：世界内逐面绘制 + 物品帧绘制；注册并写入 `BlockRenderIds` |
| `block/BlockRenderIds.java`（新） | 渲染 id 的双端安全持有者（默认 `0` = 原版立方体兜底） |
| `block/AmmoStorageBlock.java:317-329` | `getRenderType()` `0` → `BlockRenderIds.jsonModel()` |
| `block/GunDisplayCaseBlock.java:481-494` | 同上 |
| `block/GunPegboardBlock.java:560-572` | 同上；`registerBlockIcons` 额外注册折面模型的 4 张贴图 |
| `client/renderer/item/BlockModelGuiItemRenderer.java:117` | `register(...)` 里调用 `JsonBlockModelRenderer.install()`（客户端初始化接线，见 §4） |

上游依据（逐段）：

- 每面 4 顶点顺序：`net/minecraft/client/renderer/FaceInfo.java:10-45`；
- 逐面 UV 与 `rotation`：`.../block/model/BlockFaceUV.java:25-45`；
- 顶点 i ↔ 位置/UV：`.../block/model/FaceBakery.java:146-162`；
- `parent` 合并规则：`.../block/model/BlockModel.java`；
- 模型旋转绕 `(0.5,0.5,0.5)` 且 `y` 取负角：`.../resource/model/BlockModelRotation`；
- `multipart` 语义：`.../block/MultiPartBlockStateModel`。

### 2.3 完整「模型 → 方块」映射表

| 方块（注册名） | blockstates | 选中的模型 | 贴图（1.7.10 图集 icon 名） |
|---|---|---|---|
| `tacz:ammo_storage` | `ammo_storage.json`（4 个 variant，仅 `facing`） | `tacz:block/ammo_storage` | `tacz:ammo_storage` |
| `tacz:ammo_storage_gold` | `ammo_storage_gold.json` | `tacz:block/ammo_storage_gold`（parent = ammo_storage，换 `textures.0`） | `tacz:ammo_storage_gold` |
| `tacz:ammo_storage_diamond` | `ammo_storage_diamond.json` | 同上 | `tacz:ammo_storage_diamond` |
| `tacz:gun_pegboard` | `gun_pegboard.json`（24 个 variant：`facing`×`dyed`×`shape`） | `gun_pegboard` / `gun_pegboard_dyed` / `gun_pegboard_corner` / `gun_pegboard_corner_dyed` | `tacz:pegboard`、`tacz:pegboard_grayscale`、`tacz:peg_board_cornor`、`tacz:peg_board_cornor_grayscale` |
| `tacz:glow_gun_pegboard` | `glow_gun_pegboard.json` | 同族 glow 模型 | `tacz:glow_pegboard`、`tacz:glow_pegboard_grayscale`、`tacz:glow_pegboard_cornor`、`tacz:glow_pegboard_cornor_grayscale` |
| `tacz:gun_display_case` | `gun_display_case.json`（multipart，`facing`+`locked`+`left/right/up/down`） | `display_case_core`（elements 为空）、`display_case_back_panel`、`display_case_corner_{left,right}_{up,down}`、`display_case_edge_{left,right,up,down}`、`display_case_glass_*`（12 个） | `tacz:display_case_all` |

属性值来源（1.7.10 metadata → 1.20 属性字符串）：

| 属性 | 来源 |
|---|---|
| `facing` | `BlockMetadata.facing(metadata)` → 小写方向名（`north/south/west/east`） |
| `dyed` | `GunPegboardBlock.pegboardColor(world,x,y,z) >= 0`（方块实体颜色，`-1` = 未染色） |
| `shape` | `GunPegboardBlock.getShape(metadata).getSerializedName()`（`straight/corner_left/corner_right`） |
| `locked` | `GunDisplayCaseBlock.isLocked(metadata)` |
| `left` / `right` / `up` / `down` | `GunDisplayCaseBlock.isConnected(world,x,y,z, counterClockwise(facing) / clockwise(facing) / UP / DOWN)`（现算等价上游缓存的 LEFT/RIGHT/UP/DOWN） |

物品帧（`renderBlockAsItem`，无世界上下文）取 1.20 `defaultBlockState`：
`facing=north` / `locked=true` / 四周未连接 / `dyed=false`；枪架的 `shape` 是方块自身状态，如实读出。

---

## 3. 平台差异（1.7.10 vs 1.20.1）

| 项 | 1.20.1 | 1.7.10 实现 | 判定 |
|---|---|---|---|
| 方块模型体系 | `BakedModel` + `BakedQuad` + `VertexConsumer` | `ISimpleBlockRenderingHandler` + `Tessellator#addVertexWithUV` + `IIcon#getInterpolatedU/V` | 等价（`IIcon.getInterpolatedU(u)` 与 `TextureAtlasSprite.getU(u)` 同为 `minU+(maxU-minU)*u/16`） |
| 光照 | 逐顶点 AO + 方块光 | 逐面亮度（`Block#getMixedBrightnessForBlock` 沿面法线取邻居）+ 面着色系数（up 1.0 / down 0.5 / N-S 0.8 / E-W 0.6） | **降级：无平滑光照（AO）** |
| `texture_size`（`[64,64]`） | 影响 `uvlock` 与 Blockbench 预览；UV 仍是 0..16 覆盖整图 | 忽略（UV 直接交给 `IIcon`） | 等价 |
| `uvlock` | 有（`BlockMath#getUVLockTransform`） | 无等价物；TACZ 的 blockstates 里**未出现**，出现时忽略并告警一次 | 降级（未触发） |
| 模型 `weight`（随机模型） | 有 | 未有等价物；TACZ 里未出现，取第一个匹配项 | 降级（未触发） |
| 元素 `rotation` 角度 | 支持任意角度 + `rescale` | 只支持 0（**TACZ 全部模型角度都是 0**，已全量核对 `models/block/*.json`）；非 0 时跳过该元素 | 降级（未触发） |
| 面缺 `uv` | 按包围盒自动推导 | 跳过该面（TACZ 每个面都写了 `uv`） | 降级（未触发） |
| `cullface` | `Block#shouldRenderFace` 等 | 邻居 `isOpaqueCube()` 时不画 | 近似等价 |
| `RenderType` | `solid` / `cutout` | pass 0 + `GL_ALPHA_TEST`（等价 cutout） | 等价 |
| GUI 图标 | `display.gui` 模型变换 | 沿用 r6 的 `BlockModelGuiItemRenderer`（按 1.20 GUI 帧烘模型画），**未改动** | 等价 |
| 注册时机 | `DeferredRegister` / `RegisterClientRenderersEvent` | `JsonBlockModelRenderer.install()`（客户端初始化期，见 §4） | 平台替换 |

未改动的既有差异（本轮不处理，登记备查）：
`RenderShape.MODEL` 在 1.20 也参与原版「区块烘焙缓存」，1.7.10 的 ISBRH 每帧按 chunk 重建时逐方块调用，
性能上等价（都进 chunk 顶点批次），无差异需要处理。

---

## 4. 接线（需要 Lead 复核的一处）

`JsonBlockModelRenderer.install()` 必须在客户端初始化期间调用。本任务写作用域不含 `client/init/**`，
因此挂在**已存在的客户端初始化链路**上：

- 位置：`src/main/java/com/tacz/guns/client/renderer/item/BlockModelGuiItemRenderer.java:117`
  （`register(Item, ResourceLocation)` 的第一行）；
- 触发方：`src/main/java/com/tacz/guns/client/init/ClientSetupEvent.java:242-251`
  （6 次 `BlockModelGuiItemRenderer.register(...)`，第一个调用即完成安装；`install()` 幂等）；
- 若 Lead 希望更直观，可把这一行**移动**到 `ClientSetupEvent` 的 `clientSetup(...)` 里
  （建议紧跟 `ClientSetupEvent.java:251` 之后加 `JsonBlockModelRenderer.install();`），
  然后把 `BlockModelGuiItemRenderer.register` 里那一行删掉即可，两处行为完全一致。

顺序保证：`install()` 只做 `RenderingRegistry` 登记 + 写 `BlockRenderIds.jsonModel`，
不解析任何资源；模型 JSON 在首次绘制时懒加载（此时 `Minecraft#getResourceManager` 必然就绪）。

---

## 5. 待 Lead 构建验证点

1. **编译**：`./gradlew compileJava`（本轮 4 个新文件 + 10 个改动文件，`lint_files(error)=0` 已自查）。
2. **不引入对 gradle 之外的隐式依赖**：新代码只用 `net.minecraft.*`、Forge 1.7.10 的
   `cpw.mods.fml.client.registry.*`、`net.minecraftforge.client.event.TextureStitchEvent`、`org.lwjgl.opengl.GL11`、
   Gson（`BlockModelGuiItemRenderer` 已在用）与 `org.joml`（未使用）。
3. **`BlockRenderIds.jsonModel()` 默认 0**：专用服务器 / 客户端初始化前调用 `getRenderType()` 仍返回原版立方体，
   不会因为「没有渲染器」而让方块消失。
4. **spotlessApply** 后确认没有格式回退。

## 6. 仍需实机确认的点

1. **铁/金/钻石弹药库**：放下后应是一个完整的金属箱（六面为 64×64 贴图里的 16×16 小图），
   不再是「雪花/碎块」；四个朝向（FACING）应各自旋转 90°（正面朝玩家）。
2. **武器盒**：单格（未连接、默认 locked）应显示「背板 + 上下左右 4 个角框 + 一片玻璃」；
   4×4 拼成一面墙后，相邻格应出现「边框」而不是全部角框（multipart 的 LEFT/RIGHT/UP/DOWN 生效）；
   潜行右键切锁 ⇒ 玻璃片消失/出现（`locked` 位切换）。
   判读：整墙都只有角框 ⇒ `isConnected` 的 left/right/up/down 映射错；玻璃不出现 ⇒ `locked` 没进属性表。
3. **枪架**：直板是 3/16 厚的板（不是实心方块）；沿一侧再放一块应出现折面（两面板）；
   炼药锅染色后应为「grayscale 贴图 × 颜色」。
4. **朝向（C）**：面朝北/东/南/西各放一次 `workbench_a` / `gun_smith_table` / `statue` / `target` / `ammo_storage`，
   正面应始终朝向玩家。判读：仍偏 90° ⇒ `facing2D` 又被动过；完全反向 ⇒ `horizontalDirection` 被改。
5. **物品帧**：把弹药库/枪架/武器盒丢到地上（`Q`）应看到 3D 模型（`shouldRender3DInInventory=true` 生效），
   而不是扁平图标；创造栏里的图标应仍与 r6 一致（`BlockModelGuiItemRenderer` 未改）。
6. **旧存档**：本改动前放置过的这 5 类方块会「反向旋转一次」，重新放置即正常（未做迁移，见 §2.1）。
7. **性能冒烟**：放 20 个武器盒连成墙、20 个枪架，观察帧率无异常（ISBRH 每方块最多 ~30 个四边形，
   `display_case` 满连接时最多 7 个模型）。

---

## 7. 覆盖清单（诚实）

- **逐行对照上游并改动**：`block/BlockMetadata.java`、`block/AmmoStorageBlock.java`、
  `block/GunDisplayCaseBlock.java`、`block/GunPegboardBlock.java`；
- **只改注释（编码表）**：`block/AbstractGunSmithTableBlock.java`、`block/GunSmithTableBlockA/B/C.java`、
  `block/StatueBlock.java`、`block/TargetBlock.java`（后者的**逻辑**归 `r8-targets`，本任务只同步了 1 行注释，
  并已 send_message 通知对方）；
- **新增**：`block/BlockRenderIds.java`、`client/renderer/block/BlockModelQuads.java`、
  `client/renderer/block/BlockStateModels.java`、`client/renderer/block/JsonBlockModelRenderer.java`；
- **改动 1 行接线**：`client/renderer/item/BlockModelGuiItemRenderer.java`；
- **未改**：`item/AmmoStorageBlockItem.java`、`item/BlockItemWithItemIcon.java`（复核后与贴图问题无关：
  物品图标走 r6 的 `BlockModelGuiItemRenderer` / `textures/items/*.png`，二者均正常）；
  `client/resource/index/ClientBlockIndex.java` 与 `client/resource/pojo/display/block/**`
  （那是**枪包自定义方块**的数据驱动索引，与内置的弹药库/枪架/展示柜无关，复核后无需改动）；
  `client/renderer/block/{GunSmithTableRenderer,StatueRenderer,GunDisplayCaseRenderer,GunPegboardRenderer,TargetRenderer}.java`
  （BER 侧本轮只随 `facing2D` 修正自动变对，未改）。

---

## Lead 追加修正（静态度假核对，2026-11-06）

**武器盒放置后默认 `LOCKED` 未写成 true**（`GunDisplayCaseBlock#onBlockPlacedBy`）：

- 上游 `GunDisplayCaseBlock.java:90-91` 的 `defaultBlockState()` 是
  `.setValue(FACING, Direction.NORTH).setValue(LOCKED, true)` ⇒ 放置出来的展示柜默认**锁定**（玻璃 + 厚板形态）。
- 移植的 `onBlockPlacedBy` 只写了 `withFacing(...)`，metadata 的 `LOCKED` 位保持 0（= 打开形态）⇒
  `blockstates/gun_display_case.json` 里所有 `"locked": "true"` 的 `glass_*` 部件全部不生效（看不到玻璃），
  碰撞/选取形状也退化成打开形态（`GunDisplayCaseBlock.java:192-197` 的 `*_LOCKED_SHAPE`）。
- 修复：`int metadata = withLocked(withFacing(world.getBlockMetadata(x, y, z), facing), true);`
  （`GunDisplayCaseBlock.java:225`，成年/潜行放置一致；之后玩家潜行右键仍可 `toggleLinkedCases` 解锁）。
- 该问题与「武器盒贴图错误」是叠加关系：即使 JSON 模型渲染器正确，缺 `LOCKED` 时玻璃那一组部件也不会画。

**物品帧（掉落物/手持/展示框）改用 1.20 item model**：`JsonBlockModelRenderer#renderInventoryBlock` 之前用
blockstate 解析出的世界模型，而 1.20 `RenderItem` 的等价路径画的是 **item model**
（{@code models/item/<x>.json}；例如 `gun_display_case` 的 item model parent 是
`tacz:block/display_case_item`，只有 6 个元素，而世界模型是 core/back_panel/corner/edge/glass_* 的 30+ 个
multipart 部件）。现改为优先复用 `BlockModelGuiItemRenderer` 已登记的「方块物品 → item model」映射
（`getBlockModel(Item)`），未登记时退回 blockstate 的 defaultBlockState 等价物。GUI 图标本来就走 item model，
本次是让掉落物/手持与 GUI 一致。

---

## Lead 追加修正 2（实机验证发现的阻塞问题，2026-11-06 23:2x-23:4x）

### ① 加载世界必崩：LWJGL2 `glGetFloat` 缓冲区容量

实机（`run/client/crash-reports/crash-2026-11-06_23.17.34-client.txt`）加载世界后 HUD 热栏画枪时必崩：

```
java.lang.IllegalArgumentException: Number of remaining buffer elements is 4, must be at least 16.
  at org.lwjgl.opengl.GL11.glGetFloat(GL11.java:1358)
  at com.tacz.guns.mcshim.RenderType$GlStateSnapshot.capture(RenderType.java:620)
  ... AnimateGeoItemRenderer.renderItem -> MultiBufferSource.endBatch -> setupRenderState
```

**根因**：LWJGL2 的 `GL11.glGetFloat(int, FloatBuffer)` / `glGetInteger(int, IntBuffer)` / `glGetBoolean(int, ByteBuffer)`
对传入 buffer 做「最多 16 个返回值」的保守容量校验（`BufferChecks.checkBuffer(params, 16)`），
即使 `GL_CURRENT_COLOR`/`GL_VIEWPORT`/`GL_COLOR_WRITEMASK` 只写 4 个分量。容量 4 的 buffer 第一次捕获状态就抛异常。
**修复**（4 处，全部改为 16）：`mcshim/RenderType.GlStateSnapshot.color`、
`ScopeLensRenderer` 的 `RenderState.colorMask/clearColor`、`ScopeLensRenderer#capture` 的 viewport IntBuffer。
这是 1.7.10/LWJGL2 平台差异，非上游语义问题。

### ② blockstate/model JSON 读取在实机启动器下失败 → 方块变成「空气方块」

实机日志（`run/client/logs/fml-client-latest.log` 23:29/23:35）反复出现：

```
[TACZ] failed to load blockstate tacz:glow_gun_pegboard / tacz:gun_display_case / tacz:ammo_storage_diamond
net.minecraft.client.resources.ResourcePackFileNotFoundException:
  'assets/tacz/blockstates/xxx.json' in ResourcePack '...tacz-5.11.45.140-dev.jar'
```

但同一时刻用 `java.util.zip.ZipFile` / `jar tf` 直接读该 jar，条目确实存在（已用 JDK8 `ZipFile.getEntry` 验证），
说明是 1.7.10 资源管理器/启动器层对该 mod-jar 资源包的不一致，不是资产缺失。原实现（`BlockStateModels.readJson` 等）
走 `Minecraft#getResourceManager()`，失败后 `renderWorldBlock` 直接 `return false` ⇒ 方块整体不渲染。

**修复**：
1. 新增 `client/renderer/block/JsonResourceReader`：**优先**用类加载器读 `assets/tacz/**`（模组 jar 必在 classpath、
   与 mod 版本严格一致），资源管理器只作资源包覆盖兜底；blockstate/model/GUI item model 三处读取统一走它；
   第一次启用 classpath 路径打一条 INFO。
2. `JsonBlockModelRenderer#renderWorldBlock` 在 entries 为空时不再「不渲染」，改为**退回 1.7.10 原版立方体**
   （`renderStandardBlock`）+ 一次性 WARN，保证任何资源异常下方块至少是带正确贴图的实心块，而不是空气方块。

### ③ 方块图集贴图缺失（「空气贴图」）→ classpath 资源包

23:45 实机日志显示 ② 的 JSON 修复已生效（`[TACZ] resource manager missed ... models/block/glow_gun_pegboard.json,
read it from classpath instead`，且不再有 blockstate 加载失败、无 vanilla 回退 WARN），但三个方块仍显示「空气贴图」。
根因是同一平台问题延伸到**贴图**：`registerBlockIcons(...)` 注册的方块图集 sprite
（`assets/tacz/textures/blocks/*.png`）也只存在于模组 jar，而该 jar 的 `FMLFileResourcePack` 在本运行环境
（RFB + lwjgl3ify / Java 25）读不到/读抛异常 ⇒ 图集里这些 sprite 被替换成 missingno（透明）⇒「空气贴图」。
对照证据：同一日志里 TACZ 自己的 `GunPackResourcePack`（自定义 `IResourcePack`）probe 全部 HIT，
说明资源管理器本身正常，问题只在 Forge 的 mod-jar 资源包。

**修复**：新增 `client/resource/ModAssetResourcePack`（`IResourcePack`，域 `tacz`，用**类加载器**读
`assets/tacz/**`），并挂进原版资源管理器：
- 挂载点：`GunPackResourcePackReloadListener#onResourceManagerReload`（preInit 注册、排在 `TextureManager`
  之前，保证每轮 stitch 前已挂载）；**每轮重载都重挂**（`reloadResources` 会 `clearResources()`）。
- 顺序：本包先挂、枪包后挂 ⇒ `FallbackResourceManager` 从后往前查 pack，枪包仍覆盖本体资源（与上游
  `Pack.Position.BOTTOM` 语义一致）。
- 收益：blockstate/model JSON、方块图集贴图、模组 jar 内其它 1.7.10 资源都走同一条可靠路径；
  `JsonResourceReader` 的 classpath 优先读取仍保留为第二道保险。

## 补丁：弹药盒（AmmoBoxItem）物品图标错乱 —— GTNHLib JSON 物品模型桥

**实机反馈（2026-11-07）**：三个方块正常后，创作栏里**所有弹药盒**（铁/金/钻石/创造，开/关状态）物品贴图仍异常；
其它物品（枪械/弹药/配件/工作台）正常。

**根因（上游 1.20 实现）**：
- `assets/tacz/models/item/ammo_box.json` 只有 `overrides`：按物品属性 `tacz:ammo_statue`（0..8）选择
  `assets/tacz/models/item/ammo_box/*.json`；9 个模型是普通 JSON 元素模型（`elements`/`faces`/`tintindex:0`）；
- 物品属性值由上游 `AmmoBoxItem#getStatue` 计算（开/关 × 铁/金/钻石/创造 + 全类型创造）；
- 物品颜色由 `AmmoBoxItem#getColor`（染料色，默认 `0x727d6b`）走物品 tint 通道。
- 1.7.10 **没有** JSON 物品模型系统；移植早期只在 `AmmoBoxItem#registerIcons` 注册了平面图标
  `tacz:ammo_box`，而那是 64×64 的**模型贴图集**而不是 16×16 图标 ⇒ 实机表现为所有弹药盒图标错乱。

**1.7.10 实现（平台桥接，非新机制）**：新增
`client/renderer/item/AmmoBoxItemRenderer implements IItemRenderer`：
1. 按 `AmmoBoxItem#getStatue` 选模型路径（顺序与上游 `overrides` 一一对应）；
2. 用 GTNHLib 现有 1.20 模型管线加载/烘焙：`ModelRegistry.getJSONModel(ModelLoc)` → `JSONModel#bake(BakeData.IDENTITY)`
   （贴图已由 `ModelRegistry.registerModid("tacz")` 的扫描自动注册进方块图集，见上文「最终方案」）；
3. 用 `ItemModelDisplayManager#applyDisplayTransform(..., ItemModelDisplay.NO_DISPLAY, ...)`
   把 1.20 物品显示帧折算到 1.7.10 的 GUI / 手持 / 掉落物帧（9 个模型均无 `display` 段）；
4. `shouldUseRenderHelper` 与 `GunSmithTableItemRenderer` 同一套映射（EQUIPPED→`EQUIPPED_BLOCK`、
   ENTITY 申请自转/浮沉），避免再叠 1.7.10 的 2D 纸片帧；
5. 绘制：`RenderType.entityCutoutNoCull(方块图集)` 一趟画完（贴图都在块图集），
   顶点色 = 物品 tint × 逐面方向光（`ModelISBRH#diffuseLight`，等价 1.20 物品模型默认的 3D GUI 光照）；
6. 缓存按模型路径（资源重载时由 `InternalAssetLoader#onResourceReload` 清空）；
7. `registerItemRendererFor(ModItems.AMMO_BOX)` 在 `ClientSetupEvent` 注册；平面图标保留作兜底。

**平台差异记录**：1.20 由 `ItemRenderer` 直接消费 JSON 物品模型 + item property predicates；1.7.10 无此体系，
本桥接只复用 GTNHLib 的模型解析/烘焙，不改变模型格式、贴图路径与 tint 语义（9 个模型、颜色计算与上游一致）。

**顺带修复**：GTNHLib 扫描模型时会把 `particle` 引用的 1.20 原版路径（`minecraft:block/iron_block` /
`oak_planks` / `stone`）注册进图集，而 1.7.10 原版贴图在 `textures/blocks/`（复数）⇒ 3 条 missing texture
（只影响破坏粒子的 particle）。`ModAssetResourcePack` 现对这种「1.20 → 1.7.10」路径做单复数别名。

