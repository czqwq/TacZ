# r6-A2 · 方块物品（ItemBlock）GUI 图标修复

任务：task-10（r6-A 第二轮）。作用域：`client/renderer/item/**`、`client/renderer/block/**`、`client/resource/**`、
`item/**`、`init/**`、`client/init/**`、`docs/review/r6/block-item-icons.md`。

**未运行 gradle**；改动文件 `mcp__idea__lint_files(min_severity="error")` = 0 error。没有新增 mixin、没有改 `mixins.tacz.json`。

---

## 0. 结论速览

用户说「Timeless & Classics Guns: Zero 的大部分物品贴图都不正常」。第一轮（`docs/review/r6/item-icon.md`）已证明
**15 个 2D 图标位置都正确**；真正坏的是 **block 物品（ItemBlock）的图标**：1.7.10 的 `ItemBlock` 默认拿方块图集里的
**64×64 UV 图集整图上脸**，于是显示成雪花/碎块。上游分两类，本轮按两类分别修：

| 类 | 物品 | 上游 1.20 的图标形态 | 本轮修法 | 状态 |
|---|---|---|---|---|
| A | `target`、`statue` | `item/generated` + `tacz:item/{target,statue}.png`（16×16，本仓已存在） | 新的 `ItemBlock` 子类把 sprite 切到 **items 图集**的 `tacz:target`/`tacz:statue`，渲染回到 `RenderItem` 2D 分支 | ✅ 已修 |
| B | `gun_pegboard`、`glow_gun_pegboard`、`ammo_storage`、`ammo_storage_gold`、`ammo_storage_diamond`、`gun_display_case` | item model `parent = tacz:block/<x>`（**3D 方块模型图标**，无 2D item 贴图） | 新 `BlockModelGuiItemRenderer`：读 1.20 block model JSON → 烘四边形 → 按 1.20 GUI 帧画进槽位 | ✅ 已修（GUI）；手持/地面为登记的平台降级，见 §4 |
| C | `target_minecart` | `item/generated` + `tacz:item/target_minecart` | **本来就是对的**（`TargetMinecartItem#registerIcons` 已注册 items 图集图标） | ✅ 无需改 |

另：第一轮修的 `BlockItemTransforms.ItemTransform#apply` 翻译 `×1/16` 是 3 个工作台物品 GUI 模型归位的前提；
本轮的 B 类渲染器**复用**了同一个 `ItemTransform#apply`（display.gui），所以两轮是叠加生效的。

---

## 1. A 类：target / statue 的 2D 物品图标

### 1.1 现状根因

1.7.10 `ItemBlock` 的图标链路：

- `ItemBlock.getSpriteNumber()` = `block.getItemIconName() != null ? 1 : 0`
  （`build/rfg/minecraft-src/java/net/minecraft/item/ItemBlock.java:39-43`）；`Block#getItemIconName()`
  默认返回 `null`（`Block.java:1477-1482`）⇒ spriteNumber = 0；
- `ItemBlock.registerIcons` 只在 `getItemIconName()` 非空时注册物品图集图标（`ItemBlock.java:210-219`）；
- `ItemBlock.getIconFromDamage` 返回**方块图集**图标（`ItemBlock.java:48-52`）；
- `RenderItem.renderItemIntoGUI` 的方块 3D 分支条件是 `getItemSpriteNumber()==0 && renderItemIn3d(renderType)`
  （`RenderItem.java:426`）⇒ `getRenderType()==0` 的 target/statue 走 3D 方块分支，图标其实是
  `textures/blocks/target.png`（64×64 UV 图集）贴到一个等轴测方块上 → 「雪花/碎块」。

上游 1.20：`models/item/target.json` / `statue.json` 是 `item/generated` + `layer0: tacz:item/<x>`
⇒ 2D 图标（本仓已把贴图复制到 `assets/tacz/textures/items/{target,statue}.png`，16×16）。

### 1.2 修法（`item/BlockItemWithItemIcon.java` 新增）

```java
getSpriteNumber() -> 1;                                  // 走 items 图集
registerIcons(register) { itemIcon = register.registerIcon("tacz:target"/"tacz:statue"); }
getIconFromDamage(meta) { return this.itemIcon; }        // ItemBlock 默认返回方块图标，这里改回物品图标
```

`init/ModBlocks.java:69-70`：`TARGET`/`STATUE` 的 ItemBlock 类从 `ItemBlock.class` 换成
`BlockItemWithItemIcon.TargetBlockItem/StatueBlockItem`（`GameRegistry.registerBlock` 的 `itemclass` 参数，
构造器 `(Block)`，符合 FML 的反射要求）。

**副作用核对**：`getSpriteNumber()==1` 同时影响「手持 / 掉落物」路径（`RenderItem.renderItemIntoGUI:426`、
`RenderItem.renderDroppedItem`、`ItemRenderer.renderItem` 的 sprite 分支）⇒ target/statue 在手持/掉落/展示框里
由 3D 方块变成 2D 纸片。这**正是上游 1.20 的外观**（`item/generated` 的道具在手上/地上就是平片），
所以是修正而不是回归。方块放置/世界内渲染不受影响（方块仍注册并 `registerBlockIcons`）。

---

## 2. B 类：6 个方块物品的 3D 模型 GUI 图标

### 2.1 现状根因

这些物品的上游 item model 是 `parent: tacz:block/<x>`（见 `assets/tacz/models/item/{gun_pegboard,glow_gun_pegboard,
ammo_storage,ammo_storage_gold,ammo_storage_diamond,gun_display_case}.json`）⇒ 1.20 用 block model 的 elements
画 3D 方块图标。1.7.10 没有 JSON 方块模型体系 ⇒ `ItemBlock` 退回「方块图集贴图 × 64×64 UV 图集」⇒ 碎块。

### 2.2 修法（`client/renderer/item/BlockModelGuiItemRenderer.java` 新增 + `client/init/ClientSetupEvent.java` 注册）

只接管 `IItemRenderer.ItemRenderType.INVENTORY`（`handleRenderType`），`shouldUseRenderHelper(...)` 返回 `false`
⇒ 走 `ForgeHooksClient#renderInventoryItem` 的 else 分支（`glDisable(LIGHTING)` + `glTranslatef(x, y, -3+zLevel)`，
`ForgeHooksClient.java:187-205`），帧与第一轮已核对过的 `applyGuiSlotScale` 同一套等价推导。

1. **模型加载**：`Minecraft.getResourceManager().getResource("tacz:models/block/<x>.json")`
   （内置资源，运行期不变 ⇒ 几何缓存；纹理图标每次渲染按名字重新取，资源重载后 Sprite 换实例也不失效）。
   解析并合并 `parent` 链：`textures` 子覆盖父、`elements` 子空则取父、`display` 逐 context 取链上第一个
   （与 1.20 `BlockModel#getElements/getTransform` 语义一致：`tmp/minecraft-src-1.20.1/.../BlockModel.java:108,318-321`）。
   父模型 `minecraft:block/block` 在 1.7.10 没有对应资源 ⇒ 用 1.20.1
   `assets/minecraft/models/block/block.json` 的 `display.gui`（rotation [30,225,0] / scale 0.625）作常量（`BLOCK_BLOCK_GUI`）。
2. **烘焙**（只用 TACZ 实际用到的子集）：`elements.from/to`（0..16 → ÷16）；每个 face 的 `uv`（0..16，直接喂
   `IIcon#getInterpolatedU/V`，与 1.20 `TextureAtlasSprite#getU` 同语义）+ `texture: "#n"`；
   顶点顺序/UV 取值逐项照抄 1.20 `FaceInfo` 与 `BlockFaceUV`（`tmp/minecraft-src-1.20.1/.../FaceInfo.java:9-45`、
   `BlockFaceUV.java:25-45`）。元素 `rotation.angle != 0` 时**跳过该元素**（当前 TACZ 数据全为 0，宁缺勿错；日志不刷屏）。
3. **纹理名转换**：`tacz:block/pegboard` → 1.7.10 方块图集 icon 名 `tacz:pegboard`
   （去掉 1.20 才有的 `block/` 路径层；根目录同时存在 `textures/block/`(1.20) 与 `textures/blocks/`(1.7.10) 两份，
   `textures/blocks/*.png` 是移植时复制的 1.7.10 版）。这些 icon 都由对应方块类的 `registerBlockIcons` 注册
   （`GunPegboardBlock:92-96`、`GunDisplayCaseBlock:101-102`、`AmmoStorageBlock:122-128`），故 `TextureMap#getAtlasSprite(name)` 可取到。
4. **GUI 帧**：`applyGuiSlotScale` → `translate(0.5,0.5,0.5)` → `display.gui.apply()` → `translate(-0.5,-0.5,-0.5)`，
   顶点在 CPU 侧乘 `PoseStack` 矩阵（位置用 `Matrix4f`、法线用 `Matrix3f` 并归一化）后交 `Tessellator`。
   **为什么中间要包 `±0.5`**：1.20 外框以槽位中心 `(x+8,y+8)` 为原点、显示变换以方块中心为原点
   （`GuiGraphics.java:478-494` + `ItemRenderer.java:128-129`），而 1.7.10 的 Forge 入口只平移到槽位左上角
   `(x,y)`；差出的 8 像素 = +0.5 方块单位，由这层平移提供（推导与第一轮 `applyGuiSlotScale` 等价性推导同源）。
5. **GL 状态**：draw 期间临时 `TEXTURE_2D/ALPHA_TEST(0.1)/BLEND(SRC_ALPHA,ONE_MINUS_SRC_ALPHA)/LIGHTING` 开、
   `CULL_FACE` 关，结束后按进入时的快照逐个还原（draw 全程 try/finally）。
6. **注册**：`ClientSetupEvent#registerItemRenderers` 末尾 `BlockModelGuiItemRenderer.register(item, tacz:block/<x>)` ×6
   （`MinecraftForgeClient.registerItemRenderer` 是**按 Item 实例**登记，与现有 4 个物品渲染器一致）。

### 2.3 离线数值验证（不能实机，先算）

用 python 按 1.20 的 GUI 帧 + 1.20 `ItemTransform.apply`（含第一轮修的 translation ÷16）复算 6 个模型在
16×16 槽位内的投影包围盒（单位 GUI 像素，槽位 = [0,16]²）：

| 模型 | gui.rotation | x 范围 | y 范围 | 结论 |
|---|---|---|---|---|
| gun_pegboard | [30,135,0] | [0.43, 14.54] | [6.43, 17.95] | 在槽内（y 下溢 ~2px，系上游 gui 数据本身：薄板 13..16 经 30°X 旋转后的投影） |
| glow_gun_pegboard | [30,135,0] | [0.43, 14.54] | [6.42, 17.95] | 同上 |
| ammo_storage | [30,225,0]（继承 block/block） | [-0.37, 16.37] | [1.17, 14.83] | 在槽内（±0.4px） |
| ammo_storage_gold | 同上 | 同上 | 同上 | 在槽内 |
| ammo_storage_diamond | 同上 | 同上 | 同上 | 在槽内 |
| display_case_item | [30,135,0] | [0.17, 15.37] | [2.92, 15.33] | 在槽内 |

⇒ 修完不会出现「新的一次整体错位」；pegboard 的下溢 2px 与上游同源（同一份 display 数据），不是移植错误。

---

## 3. C 类：target_minecart（无需改）

`TargetMinecartItem#registerIcons`（`item/TargetMinecartItem.java:46-50`）已注册 `tacz:target_minecart`
（`assets/tacz/textures/items/target_minecart.png` 16×16 + `models/item/target_minecart.json` 的 `item/generated`），
第一轮截图里它也是唯一显示正常的 2D 图标。本轮不动。

---

## 4. 未修 / 降级（诚实登记）

| # | 项 | 说明 | 处置 |
|---|---|---|---|
| 1 | B 类 6 个方块物品的**手持 / 掉落物 / 展示框**外观 | 这些 context 继续走 1.7.10 原版方块渲染（等轴测方块 + 方块图集贴图）。上游 1.20 用 block model + `display.thirdperson/ground/fixed` 逐 context 渲染，需要 1.20 的手臂骨骼帧/物品显示帧（1.7.10 侧对应的是 `ForgeHooksClient#renderEquippedItem` 的写死纸片帧 + `RenderPlayer` 手臂帧，第一轮 `AnimateGeoItemRenderer` 只对**枪械**做了那种补偿） | **明确降级**，不在本轮范围（task 验收允许「明确降级并记录」）。GUI 已修好；手持仍是「一眼能看出是方块」的可用外观 |
| 2 | A 类 2D 图标在手持/掉落变平片 | 非回归：上游 `item/generated` 本就是平片 | 记录 |
| 3 | `minecraft:block/block` 的 `display` 常量 | 1.7.10 资源里没有 1.20 的模型 JSON，硬编码 rotation [30,225,0]/scale 0.625 | 已注释来源（1.20.1 `assets/minecraft/models/block/block.json`） |
| 4 | block model 的 `element.rotation` / `uvlock` / `tintindex` / `cullface` | 当前 TACZ 6 个模型未使用（rotation 全 0）；非 0 旋转元素会被跳过 | 记录（宁缺勿错） |

---

## 5. 需实机验证项

1. **OTHER 页签**：`target`/`statue` 显示真正的 16×16 2D 图标（靶子/雕像），不再雪花；
   `ammo_storage`/`gold`/`diamond`、`gun_pegboard`、`glow_gun_pegboard`、`gun_display_case` 显示**3D 方块图标**
   （弹药箱 / 挂板 / 展示盒），且在槽位内（对照 §2.3 的范围）。
2. 第一轮修的 3 个工作台图标仍在正确槽位（两轮叠加，不应回退）。
3. 手持/掉落这些方块物品：仍是 1.7.10 原版方块外观（已知降级，见 §4），不应变成紫黑块或消失。
4. 若某个 B 类图标显示为**紫黑缺失纹理**：查 `run/client/logs/latest.log` 是否有
   `[TACZ] block model icon missing: <id>` 或 `failed to load block model`（说明 JSON 没读到或图标名没注册）；
   比如 `textures/blocks/<x>.png` 缺失时会落到 `missingno`（≠ 崩溃，只是图标错）。
   该 warn 只在首次未命中时打印一次（模型缓存）。
5. 若某个 B 类图标整体**偏移/飘出槽位**：先确认第一轮对 `BlockItemTransforms` 的 `×1/16` 修复仍在
   （`ItemTransform.TRANSLATION_UNIT`），再核对 `display.gui` 是否取自正确的父链（日志可临时加一行打印模型路径）。

---

## 6. 覆盖清单（诚实）

- **通读**：`item/BlockItemWithItemIcon.java`（新）、`client/renderer/item/BlockModelGuiItemRenderer.java`（新）、
  `init/ModBlocks.java`、`client/init/ClientSetupEvent.java`（注册段）、`client/resource/pojo/display/block/BlockItemTransforms.java`；
  1.7.10 `ItemBlock.java` 全文、`Block.java` 的 `getItemIconName/registerBlockIcons/setBlockTextureName`、
  `RenderItem.java:409-569`（GUI/2D/方块分支）、`TextureMap.java:262-434`（stitch/registerIcon/getAtlasSprite/registerIcons）、
  `ForgeHooksClient.java:70-208`、`MinecraftForgeClient.java:29-40`；
  1.20 侧 `tmp/minecraft-src-1.20.1/.../FaceInfo.java`、`BlockFaceUV.java`、`FaceBakery.java`、`BlockModel.java`、
  `assets/minecraft/models/block/block.json`、`GuiGraphics.java:450-511`、`ItemRenderer.java:105-170`。
- **按需查片段**：`block/{TargetBlock,StatueBlock,GunPegboardBlock,GunDisplayCaseBlock,AmmoStorageBlock}.java`
  （只看 `registerBlockIcons`/`getRenderType`）、`assets/tacz/models/{item,block}/*.json`（6 组 + 父链）、
  `TargetMinecartItem.java`、`Vector3fSerializer.java`。
- **没查**：B 类方块在世界内/展示框里的**方块模型**渲染（1.7.10 仍是方块图集等轴测方块，属另一处平台降级，
  若要修需给这些方块写 custom `Block#getRenderType` + `IBlockRenderer`/TESR 方块模型，超出本任务）；
  附件 tooltip 的图标网格（另一位 teammate 的 task-8，他改的是布局，图标本身此时已由本轮修复——
  target/statue → 2D 图标、B 类 → 3D 模型；`docs/review/r6/tooltip.md` 可引用本报告）。
- **没实机**：本轮全部为静态实现 + 离线数值复算；实机验证步骤见 §5。
