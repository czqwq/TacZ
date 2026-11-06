# r6-A · 创造模式物品栏图标/模型错位 + 左侧筛选面板

任务：task-6。作用域：`client/renderer/item/**`、`client/gui/components/CreativePackFilters.java`、`client/gui/components/creative/**`、
`client/model/{SlotModel,RenderHelperCompat}.java`、`mcshim/{RenderType,BufferUploader,TessellatorVertexConsumer}.java`、
`mixin/client/{CreativeModeInventoryScreenMixin,CreativeContainerAccessor}.java`、本报告；
**经 Lead 批准追加** `client/resource/pojo/display/block/BlockItemTransforms.java`。

**未运行 gradle**；改动文件 `mcp__idea__lint_files(min_severity="error")` = 0 error。没有新增 mixin、没有改 `mixins.tacz.json`。

---

## 0. 结论速览

1. **「图标散落 / 枪模飘到别处」的唯一位置错乱根因**：`BlockItemTransforms.ItemTransform#apply` **漏了 1.20 反序列化时的
   `translation *= 0.0625`**（JSON 单位是 1/16 格）⇒ 工作台类方块物品的 display transform **平移量 ×16**。
   受影响的是 3 个工作台物品的 3D 图标 + OTHER 页签图标（同一物品）：分别被搬到「左下角 ALL 按钮旁」「第三行」
   「第二行压住 target_minecart」——与用户描述**逐条对上**。已修（1 处，见 §2）。
2. **其余 15 个 2D 图标位置全部正确**（逐格量 bbox 验证，见 §1.2）。「大部分图标错乱」的观感来自两件事：
   (a) 3 个工作台模型被搬走后留下 3 个空槽；(b) 目标/雕像/贴板/弹药箱/展示盒这些 **block 物品**在 1.7.10 退回
   block atlas 贴图（雪花/碎块），而上游是 2D item texture 或 3D block model —— 属**另一个问题**（已转 task-10 第二轮）。
3. **AMMO 页签图标没有位移/溢出**：程序化量测 icon 起点 (≈477,159)px vs 代码预测 (477,161)px（GUI scale 5）。
   观感偏大是因为 `762x39` slot 贴图内容满幅（32×32 里 content bbox (0,3)-(31,30)），不是 bug。
4. **左侧 ALL / 枪包按钮「点了没效果」不是 bug**：本场景只有 1 个内容包（命名空间 `tacz`），ALL 与「tacz 包」
   筛出的列表完全相同；`containerAccessProvider` / `@Accessor` / 点击派发链路已核对且实机日志确认 mixin 应用成功。
5. `applyGuiSlotScale`（GUI 槽位缩放/翻转）经重新推导**与 1.20 完全等价，未改**；`ItemPreviewRenderContext.alpha()`
   与 `RenderType` 的 GL 状态 snapshot/restore **未发现泄漏，未改**（依据见 §3）。

---

## 1. 证据

### 1.1 修复项：工作台物品 GUI transform 平移量 ×16

| 项 | 本工程 | 上游 1.20.1 | 1.7.10 原版 |
|---|---|---|---|
| 解析 JSON `transforms.*.translation` | gson 反射直填 `Vector3f translation`，**不做单位换算**<br>`client/resource/pojo/display/block/BlockItemTransforms.java`（`@SerializedName("translation")`） | `ItemTransform.Deserializer#deserialize`：`vector3f1.mul(0.0625F)` 再 clamp ±5；scale clamp ±4<br>`tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/client/renderer/block/model/ItemTransform.java:79-85`；注册点 `tmp/TACZ-1.20.1/.../client/resource/ClientAssetsManager.java:67-68` | 无该体系（1.8 才有 `block.model` 包） |
| 应用变换 | `BlockItemTransforms.ItemTransform#apply`（`translate(translation)` 原值）→ 平移 ×16 | `ItemTransform#apply`（`translate(...)`，值已在解析时 ÷16）`ItemTransform.java:31-46` | — |
| 唯一消费点 | `client/renderer/item/GunSmithTableItemRenderer.java:68-74` | `tmp/TACZ-1.20.1/.../renderer/item/GunSmithTableItemRenderer.java:39-45`（逐行同构） | — |
| 数据 | `assets/tacz/custom/tacz_default_gun/assets/tacz/display/blocks/{gun_smith_table,ammo_workbench,attachment_workbench}.json` 的 `transforms.gui` | 同一份资源（上游资源全量复制） | — |

**实机截图（`docs/review/r6/screenshots/a_creative_inventory.png`，1306×751，GUI scale=5）逐项吻合**：
推算式：`像素偏移 = (16·tx, −16·ty)`（GUI px），1 GUI px = 5 图 px。

| 物品（OTHER 页签 slot 序） | gui.translation（JSON） | bug 下预测偏移 | 截图实测 |
|---|---|---|---|
| slot0 `GUN_SMITH_TABLE`（gun_smith_table） | (-2.5, -3.75) | (-40, +60) GUI px ≈ (-2.2 列, +3.3 行) | 「悬空枪械贴图」bbox≈(135,615)-(255,710)，即 **左下 ALL 按钮旁、面板外** |
| slot1 `WORKBENCH_121`（attachment_workbench） | (0, -2.5) | (0, +40) GUI px ≈ +2.2 行 | **第三行** col2 的「玻璃盒」模型（bbox≈(430,520)-(515,610)） |
| slot2 `WORKBENCH_111`（ammo_workbench） | (0, -1.5) | (0, +24) GUI px ≈ +1.3 行 | **第二行** col3，与 target_minecart 图标重叠 |
| OTHER 页签图标（= `new ItemStack(ModItems.GUN_SMITH_TABLE)`） | 同上 gun_smith_table | (guiLeft+6, guiTop-19)+(-40,+60) | 页签图标飘到侧栏左侧（x≈95..165, y≈450..550 的彩色残片） |

### 1.2 未改：其余 15 个 2D 图标位置正确（逐格 bbox 量测）

CTM 背景 `slot bg = (139,139,139)`、标题区 `(198,198,198)`；网格：列左边界 `335+90c`、行 `340+90r`（5 图 px/逻辑 px，槽距 18 逻辑 px）。
逐格量测结果（只列有内容的格）：

- row0：c0/c1/c2 **空**（= slot0..2 的工作台模型被搬走）；c3..c8 有内容 = slot3..8（TARGET, STATUE, GUN_PEGBOARD,
  GLOW_GUN_PEGBOARD, AMMO_STORAGE, AMMO_STORAGE_GOLD）。
- row1：c0..c7 有内容 = slot9..16（AMMO_STORAGE_DIAMOND, GUN_DISPLAY_CASE, TARGET_MINECART, 5×AMMO_BOX；
  5 个 ammo box 的像素计数完全相同 = 780，位置连续）。
- row2：c1 有内容 = 被搬下来的 attachment_workbench 模型；其余空。
- 所有 2D 图标 bbox 都落在自己槽位内（如 target_minecart row1 c2 bbox≈(528,442)-(597,511) = 槽位内界）。

⇒ **没有「GL 状态泄漏导致后续图标整体带偏移」**：若存在这种泄漏，2D 图标会成片错位，实际是精确落格。

### 1.3 AMMO 页签图标（未改）

1.7.10 页签图标绘制：`GuiContainerCreative#func_147051_a`（`build/rfg/minecraft-src/.../GuiContainerCreative.java:922-973`）
`l += 6; i1 += 8 ± 1` 后调 `renderItemAndEffectIntoGUI`。按 `guiLeft≈300px / guiTop≈256px`（面板白边实测，GUI scale 5）
预测 icon 左上角 = (477,161)。实测 7.62x39 贴图 content（`textures/ammo/slot/762x39.png` 的 content bbox (0,3)-(31,30)/32）
映射回 icon 原点 = **≈(477,159)**，误差 ≤2 图 px ⇒ 正确。贴图本身内容满幅（含 "7.62x39" 文本 + 黄条），所以视觉上「大」。
对照：scope/muzzle/stock 页签 icon 同样位置规律（槽位贴图 32×32/64×64 内容有大片透明，看起来小）。

### 1.4 左侧筛选面板「点了没效果」（未改，判定为预期）

- 1.7.10 只有一个内容包 `tacz_default_gun`（namespace `tacz`）⇒ `CreativePackFilters#groupItems` 只生成 1 个组
  ⇒ options = [ALL, tacz]；两者列表逐元素相同 ⇒ 点击 ALL / 枪包按钮**本来就没有可见变化**。
- 点击链路静态核对：`mixin/client/CreativeModeInventoryScreenMixin#tacz$clickPackFilterButtons`（mouseClicked HEAD，
  cancellable）→ `PackFilterButton#mousePressed`（`GuiButton#mousePressed` 命中矩形）→ `select()` → `applyFilter()`
  → `showItems()` → `CreativePackFilters.ContainerAccess#itemList/scrollTo`（`@Accessor("itemList")`/`@Invoker("scrollTo")`）。
- 实机日志（`run/client/logs/fml-client-latest.log:13:01:17`、`:13:01:33`）确认 `CreativeModeInventoryScreenMixin` 与
  `CreativeContainerAccessor` 都 **mixin 成功**（`Mixing client.CreativeContainerAccessor from mixins.tacz.json into
  net.minecraft.client.gui.inventory.GuiContainerCreative$ContainerCreative`），且 13:00–13:13 无异常。
- 附带核对：`CreativePackFilters` 在 `initGui` TAIL 注入，此时 `inventorySlots` 已是 `ContainerCreative`、页签列表已填充，
  `showItems` 的 `clear + addAll + scrollTo(0)` 不会破坏 vanilla 的后续 `updateCreativeSearch`（本页签无搜索框）。

---

## 2. 已修

| # | 文件:line | 改动 | 依据 |
|---|---|---|---|
| 1 | `client/resource/pojo/display/block/BlockItemTransforms.java:111-146` | `ItemTransform#apply` 里按**局部量**把 `translation` 乘 `1/16` 并 clamp ±5、`scale` clamp ±4（**不原地改字段** ⇒ 重复调用幂等） | `tmp/minecraft-src-1.20.1/.../ItemTransform.java:79-85`（`mul(0.0625F)` + `MAX_TRANSLATION=5` + `MAX_SCALE=4`）；上游注册点 `ClientAssetsManager.java:67-68` |
| — | 同上（类 javadoc） | 在「行为差异/降级点」登记「上游反序列化换算由 apply 承担」 | — |

**单点回滚**：`ItemTransform.apply` 的 `TRANSLATION_UNIT = 1.0F / 16.0F` 改回 `1.0F` 即恢复旧（×16）行为；
判读：修复后 3 个工作台物品图标应回到**自己槽位内**（GUI 里是小的斜视 3D 工作台），第三/第二行的 `纸片/工作台` 模型消失，
OTHER 页签图标回到页签方块里。

---

## 3. 核对过但**未改**的项（Lead 指定排查点，逐条给结论）

| 排查点 | 结论 | 依据 |
|---|---|---|
| `applyGuiSlotScale` 平移量是否差 (±8,±8) | **不差，与 1.20 等价，不改**。重新推导：内层 `M_inner = T(0.5,1.5,0.5)·Rz180·T(0.5,1.5,-0.625)` 把 SlotModel 南面映射到 `x,y∈[0,1], z≈0.469`；1.20 外框 `T(x+8,y+8,150)·S(16,-16,16)·T(-0.5,-0.5,-0.5)·M_inner` 的 x/y 映射 = `(x+16px, y+16-16py)`；本工程外框 `ForgeHooksClient` 的 `T(x,y,-3+zLevel)` × `S(16,-16,16)·T(0,-1,0)` = `(x+16px, y+16-16py)` ⇒ **逐项相同** | 内层推导见 `docs/review/r2/01-render-material.md` §P1-B 表；1.20 帧来源 `tmp/minecraft-src-1.20.1/.../client/gui/GuiGraphics.java:478-494`（`translate(x+8,y+8,150…)`→`scaling(1,-1,1)`→`scale(16,16,16)`）+ `.../ItemRenderer.java:128-129`（display transform 后 `translate(-0.5,-0.5,-0.5)` 再进 BEWLR）；`builtin/entity` 无 display 段（`ModelBakery.java:94-108` `BLOCK_ENTITY_MARKER = BlockModel.fromString("{\"gui_light\": \"side\"}")`）⇒ GUI display transform = identity。1.7.10 侧 `build/rfg/.../ForgeHooksClient.java:187-205`、`RenderItem.java:575-646` |
| `renderSlotTexture` 是否 snapshot/restore GL 状态 | **已 snapshot/restore，无泄漏，不改**。`RenderType#setupRenderState/clearRenderState` 在进 setup 时捕获 texture2d/cull/depthTest/depthMask/alphaTest/blend+src/dst/lighting/polygonOffset/lineWidth/color，出 clear 原样还原；`MultiBufferSource.BufferSource#endBatch` 对每个 RenderType 严格 `setup → draw → clear` 成对、不嵌套；各 renderer 的 `MultiBufferSource.immediate()` + `endBatch` 在 `finally` 里 | `mcshim/RenderType.java:429-517, 574-632`；`mcshim/MultiBufferSource.java:118-135`；`AnimateGeoItemRenderer.java:192-197`、`AmmoItemRenderer.java:64-69`、`AttachmentItemRenderer.java:55-60`、`GunSmithTableItemRenderer.java:49-54` |
| `ItemPreviewRenderContext.alpha()` 残留 | **无残留，不改**。写点只有 `GunPegboardRenderer.java:203/221`、`GunDisplayCaseRenderer.java:220/238`，两者 `setAlpha(alpha)` 与 `setAlpha(1.0F)` 都在 try/finally 配对；GUI 物品渲染发生在 GUI pass，不在这些块渲染器作用域内 | 同左 |
| `SlotModel` 四边形几何 | 与上游**逐字相同**（bone `(8,24,-10)`、cube `(-16,-16,9.5,16,16,0)` south 面）⇒ 不需要改 | `client/model/SlotModel.java:32-38` ↔ `tmp/TACZ-1.20.1/.../SlotModel.java:16-21` |
| `GunItemRendererWrapper#renderSlotTexture` | 与上游 `:416-421` **逐行相同**（`translate(0.5,1.5,0.5)` + `Rz(-180)` + `SLOT_GUN_MODEL.renderToBuffer`），本身无 bug；位置正确性由 §3 第 1 条与 `applyGuiSlotScale` 共同保证 | 本工程 `GunItemRendererWrapper.java:556-570` |
| 其它物品渲染器 GUI 分支（ammo/attachment） | `AmmoItemRenderer.java:113-128`、`AttachmentItemRenderer.java:71-85` 的 GUI 分支与上游 `AmmoItemRenderer.java:71-75` / `AttachmentItemRenderer.java:40-46` 同构，均走 SlotModel + `applyGuiSlotScale`，无需改 | 同左 |
| 左侧面板位置/尺寸/GL | 按钮 `(guiLeft-28, guiTop+11+29i)` 与 1.20 一致；绘制用 `mcshim.GuiGraphics`（每次 draw 自 glPush/glMult/glPop），`drawButton` 里 `RenderSystem.enableBlend()` 后未 disable —— **与 vanilla `GuiButton` 一致（后续 GUI 绘制本就要求 blend 开）**，不构成污染；`readField(guiLeft/guiTop)` 在生产 SRG 名下会失败，但 fallback `(width-195)/2, (height-136)/2` 与 `GuiContainer` 的默认居中值**相等**，位置仍正确 | `client/gui/components/CreativePackFilters.java:178-204, 548-556`；`build/rfg/.../GuiContainer.java:81-82` |

---

## 4. 未修（+理由 / 去向）

| # | 级别 | 现象 | 根因 | 未修理由 / 去向 |
|---|---|---|---|---|
| 1 | P2（观感） | 目标/雕像/贴板/弹药箱/展示盒等 **block 物品**的图标是雪花/碎块 | 这些物品在 1.7.10 是 `ItemBlock`：`target/statue` 应取 `tacz:item/{target,statue}.png`（16×16），现退回 block atlas；`ammo_storage*/gun_pegboard/glow_gun_pegboard/gun_display_case` 上游是 3D block item model（无 2D 贴图），现退回 block atlas（64×64 UV 图集） | **转 task-10 第二轮**（Lead 已批准 scope：`item/**`、`init/**`、`client/init/**`、`client/renderer/block/**`、`client/resource/**`），修复与验证写 `docs/review/r6/block-item-icons.md` |
| 2 | P3 | 页签图标/工作台图标最终位置仍需实机确认（阈值/观感） | 已是 1.20 同源数据 + 同源变换链，静态无剩余偏差 | 见 §5 实机验证项；单点回滚见 §2 |
| 3 | P3 | 1.7.10 创造栏每页只有 5×9 格，16 个 TACZ 页签 → 分 3 页（截图 `2 / 3`） | 1.7.10 平台限制（`creativeTabArray` 分页），非 bug | 记录，不改 |

---

## 5. 需实机验证项（修完 ÷16 后）

1. 进创造模式 → TACZ 页签：3 个工作台物品（Gun Smith Table / 弹药工作台 / 配件工作台）的 **3D 图标在自己槽位内**，
   「左下 ALL 按钮旁」与「第二/三行乱入」的模型消失；OTHER 页签图标（工作台）回到页签方块内。
2. 手持 / 掉落 / 展示框 里的 3 个工作台方块物品位置也应同时归位（同一 `apply` 服务于 GUI/第三人称/ground/fixed；
   此次修复同时改善 `thirdperson_*`/`ground`/`fixed` 的 ×16 平移）。
3. 回归：枪械/弹药/配件图标（SlotModel 2D）**不应有任何变化** —— 本次未动 `applyGuiSlotScale`、
   `renderSlotTexture`、`RenderType`、`SlotModel`。
4. 左侧面板：单包场景点 ALL / 枪包按钮仍无列表变化（预期）；若装了第二个枪包（不同 namespace），点包按钮应只显示该包条目、
   再点 ALL 恢复全部。

**环境标注（Lead 要求）**：本机是 **lwjgl3ify 3.0.33 + LWJGL 3.4.2**，固定管线经其 compatibility 层重映射。
本次**没有改动任何 GL 状态/矩阵查询代码**，只做 CPU 侧矩阵数值修正 ⇒ 不受该层影响。§3 里唯一与 LWJGL2 行为相关的
论断是 `RenderType.GlStateSnapshot` 用到的 `glGetInteger(GL_BLEND_SRC/GL_BLEND_DST)` 与
`glGetBoolean(GL_DEPTH_WRITEMASK)`（legacy 查询）：在 lwjgl3ify 提供的 **compatibility profile** 下有效；
若日后切换到 core profile，这三处会变成 GL 错误（届时需改为按本类自己写的值回复，而不是查询）。

---

## 6. 覆盖清单（诚实）

- **通读全文**：`client/model/{SlotModel,RenderHelperCompat}.java`、`client/renderer/item/**`(12 文件全部)、
  `mcshim/{RenderType,BufferUploader,TessellatorVertexConsumer,MultiBufferSource,PoseStack,GuiGraphics,RenderSystem}.java`（相关段）、
  `client/gui/components/CreativePackFilters.java`、`client/gui/components/creative/**`、`mixin/client/{CreativeModeInventoryScreenMixin,CreativeContainerAccessor}.java`、
  `client/resource/pojo/display/block/BlockItemTransforms.java`、`client/resource/index/ClientBlockIndex.java`、`init/ModCreativeTabs.java`；
  1.7.10 侧 `ForgeHooksClient.java:140-233`、`RenderItem.java:555-765`、`GuiContainer.java:80-310`、
  `GuiContainerCreative.java:258-720, 900-1105`、`CreativeTabs.java:15-368`；1.20 侧 `ItemTransform.java`、
  `ItemRenderer.java`、`GuiGraphics.java:450-511`、`ModelBakery.java:60-130`、上游 `GunSmithTableItemRenderer.java`、`GunItemRendererWrapper.java:300-423`、
  `SlotModel.java`、`ModCreativeTabs.java`、`GunSmithTableItem.java`、`ClientAssetsManager.java:60-75`。
- **只按需查片段**：`client/renderer/block/{GunPegboardRenderer,GunDisplayCaseRenderer,GunSmithTableRenderer}.java`（仅 alpha/模型来源）、
  `client/resource/InternalAssetLoader.java`（仅常量列表）、`Mcp` 各 `Gui*Screen`（只核 `zLevel` 赋值点）。
- **没查**：block 渲染（`client/renderer/block/**` 的几何/光照，除上面列出的片段）、工作台/弹药台**界面**（`GunSmithTableScreen` 等）、
  资源包热重载后的页签顺序、`GunPackList`（本任务未涉及）。
- **没实机**：本任务全程静态 + 截图量测（截图由 Lead 提供、只读）。所有位置结论都附了像素级实测或矩阵逐项推导。
