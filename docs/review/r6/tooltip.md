# r6-C · 配件 tooltip「受支持枪械」Shift 展开过长出屏

任务：task-8。作用域：`client/tooltip/**`、`client/event/TooltipEvent.java`、本报告。
**未运行 gradle**；改动文件 `mcp__idea__lint_files(min_severity="error")` = 0 error。**没有新增 mixin、没有改 `mixins.tacz.json`**（原因见 §2）。

---

## 1. 用户问题「高版本也是这样吗？」——**不是**

上游 1.20.1 **不会**横向溢出：它根本没有把枪名塞进文本，而是把「受支持枪械」做成 tooltip 组件里的**图标网格**，
尺寸由组件自己声明给原版 tooltip 排版器（排版器只按 `getWidth/getHeight` 预留矩形，不做换行）：

| 上游实现（`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/tooltip/ClientAttachmentItemTooltip.java`） | 行为 |
|---|---|
| `getHeight()` :82-87 | 未按 Shift：`components*10 + 28`；按住 Shift：`(showGuns-1)/16*18 + 50 + components*10` → **枪越多越变高** |
| `getWidth(Font)` :90-105 | 取文本最长行；`showGuns.size() > 15` 时**封顶 260 px**；否则 `max(文本宽, n*16 + 4)` → **宽度有硬上限** |
| `renderText(...)` :108-124 | 画文字；Shift 时把 y 偏移 `18*(行数-1) + 32` 让给网格 |
| `renderImage(...)` :127-142 | 画 11 px 蓝底高亮条 + **每行 16 个、每格 16×18 的枪械物品图标** |

也就是说高版本「清单多 → tooltip 变高」，而 1.7.10 移植侧只有 `ItemTooltipEvent` 的纯文本通道，
把整张表折算成了一行逗号枪名（旧 `ClientAttachmentItemTooltip#gunNamesLine`，最多 16 个名字 + "…… (+N)"），
一行几百上千像素、没有任何宽度上限 —— 这才是出屏的根因，**是移植降级产生的 bug，不是高版本行为**。

1.7.10 侧的文本通道事实：
- `ItemStack#getTooltip` 在最后才 post `ItemTooltipEvent`（`build/rfg/minecraft-src/java/net/minecraft/item/ItemStack.java:727`）；
- 物品 tooltip 由 `GuiScreen#drawHoveringText` 统一排版，宽度 = 最长文本行像素宽，**无自动换行**
  （`GuiScreen.java:156-232`，框宽计算 :164-195）；
- 1.7.10 没有 `TooltipComponent` / `ClientTooltipComponent` / `GuiGraphics#renderTooltip(..., TooltipComponent)` 体系。

---

## 2. 方案：A（复刻图标网格）+ 竖直/横向双上限兜底，**不用 mixin**

### 2.1 为什么能不用 mixin

补画时点选在 `GuiScreenEvent.DrawScreenEvent.Post`（Forge 总线）：
- 1.7.10 的 `DrawScreenEvent.Pre/Post` 在 `EntityRenderer#updateCameraAndRender` 里**紧贴** `currentScreen.drawScreen(...)`
  post（`build/rfg/minecraft-src/java/net/minecraft/client/renderer/EntityRenderer.java:1136-1138`）；
- `GuiContainer#drawScreen` 的槽位 tooltip 恰好在 `drawScreen` **末尾**画（`GuiContainer.java:183-187`），
  改装台/枪械工作台等自定义界面同样在 `drawScreen` 末尾调 `renderTooltip`；
- 所以 `Post` 时 tooltip 已经画完且坐标系仍是 GUI 缩放坐标系，可以按同一公式复算出框位置再补画。

本仓 `TooltipEvent` 本来就注册在 `MinecraftForge.EVENT_BUS`（`ClientProxy.java:136-157` 的 `FORGE_BUS_CLASSES`），
`GuiScreenEvent` 也 post 到同一条总线 ⇒ **在 `TooltipEvent` 里加两个 `@SubscribeEvent` 转发方法即可，
不用改 `ClientProxy`、不用 mixin**。这也避免了「mixin 编译过了但运行时注入失败」的、无法在本机验证的风险。

### 2.2 关键难点：纯文本 tooltip 怎么给网格「预留空间」

`drawHoveringText` 的框 = 文本行决定，框宽 = 最长行、框高 = 行数。1.7.10 没法让「图片」参与排版，
所以 `ClientAttachmentItemTooltip#getLegacyTooltipLines()` 在 Shift 展开时往文本里插入：

1. **若干空行**占高度：每个 tooltip 行高 10 px，网格每行 18 px ⇒ 见 §3 的 `R` 公式；
2. **一行空白（空格）撑宽**：空格像素宽 × 数量 = 网格需要的内容宽（`min(16,总数)*16 + 4`），
   该行本身不可见，只负责把 vanilla 的框撑到网格宽度。

`AttachmentGunGridOverlay` 在 `Post` 时重新算框位置（与 `GuiScreen#drawHoveringText` 相同的公式）后，
在预留区域内覆盖画高亮条 + 图标，因此观感与上游一致：**框内是图标，不是枪名文本**。

### 2.3 兜底（任何情况下不出屏）

- 竖直放不下：从 `rows = ceil(total/16)` 逐行递减，直到整框能装进 `scaledHeight - 40`；被截掉的枪用灰色
  `…… (+N)` 行标注（与旧实现的提示写法一致）。
- 连 1 行图标都放不下（配件描述极长 + 小窗口的极端情况）：退化为**单行枪名摘要**（按像素截断 + `…… (+N)`），
  旧 `gunNamesLine` 一行式的替代，但宽度被钳到 `maxLineWidth`。

### 2.4 两个 1.7.10 细节（不处理就会静默不画）

1. **行文本带格式码**：`IChatComponent#getFormattedText()` 是 `ChatComponentStyle#getFormattedText()`
   （`ChatComponentStyle.java:104-119`），每段都会补 `§r`；tooltip 行又被 `GuiScreen#renderToolTip`
   （`GuiScreen.java:126-136`）加了 `§7` 前缀。所以「支持枪械：」行在列表里是 `§7支持枪械：§r`，
   与资源里的 `"支持枪械："` 不相等 —— 定位标题行前**两边都要 `EnumChatFormatting.getTextWithoutFormattingCodes`**。
2. **框宽/框高必须用最终列表复算**：vanilla 的 `renderToolTip` 会就地改写行（前缀色码不改行数/宽度），
   而本仓部分 GUI 走 `GuiGraphics#renderTooltip` 重建了同样行数/内容的新列表 —— 两种路径下
   `ItemTooltipEvent#toolTip` 的行数、像素宽都与真正画出来的一致，因此 overlay 里用该列表复算框位置即可。

---

## 3. 布局公式（像素）

记：
- `k = 描述行数(折行后) + 1`（+1 是「支持枪械：」标题行）；
- `pack = 1/0`（枪包名是否单独一行）；
- `rows` = 图标行数，`columns = min(16, (min(maxLineWidth,260)-4)/16)`（正常屏幕恒 16）；
- `total` = 受支持枪械数，`shown = min(total, rows*columns)`，`trunc = shown < total ? 1 : 0`。

**横向（保证 ≤ scaledWidth - 20）：**
```
maxLineWidth = min(300, scaledWidth - 20 - 6)          // 6 = tooltip 框左右各 3px 边（GuiScreen.java:200-204）
所有自有文本行按 maxLineWidth 折行（TooltipLineUtils.addWrapped）
撑宽行宽     = min(columns, total) * 16 + 4            // 与上游 getWidth 的 max(文本, n*16+4)、>15 封顶 260 对齐
⇒ 框宽 ≤ max(maxLineWidth, 260) + 6 ≤ scaledWidth - 14
```
- 这样 `showGuns ≤ 15` 时框宽与上游一致（`n*16+4`），`> 15` 时与上游一样封顶 260 px。

**竖直（保证不顶出屏幕）：**
```
R(rows)  = (18*rows + 14) / 10                        // 预留空行数（整数除法即 ceil((18rows+5)/10)）
框行数 n = 1(物品名) + k + pack + R + trunc
约束     10 * (k + pack + R + trunc) ≤ scaledHeight - 40   // vanilla 只在底部 clamp：ty = height - 框高 - 6
```
`R` 的推导（都用 1.7.10 实测公式：第 0 行 y=`ty`，第 i≥1 行 y=`ty + 2 + 10i`，框高=`10n`）：
- 高亮条：`barTop = supportTop - 2`，高 11；第一行图标 `firstRowTop = barTop + 15`；
- 最后一行图标底 = `supportTop + 18*rows + 11`；
- 网格后还有一行（枪包名 / `(+N)`）⇒ 需要 `10R ≥ 18rows + 3`；
- 网格后没有行 ⇒ 需要框底再留 2 px ⇒ `10R ≥ 18rows + 5`；
- 取大者即 `R = ceil((18rows+5)/10) = (18rows+14)/10`。已用脚本枚举 `rows=1..8 × k=0,1,3,6,10 × pack/trunc∈{0,1}`
  验证全部满足（更小的 `R=(18rows+13)/10` 在 rows=2/7 时会差 1 px）。

高度上限留 40 px 而不是 20 px 的原因：`k` 只统计了本组件的行，**物品名行**（vanilla 加的）和
`ClientProxy` 里 F3+H 的 TACZ id 行（`TooltipEvent#onTooltip` ②）各占 1 行，10×2 = 20 px 也要留出来。

---

## 4. 改动文件清单

| 文件 | 改动 |
|---|---|
| `src/main/java/com/tacz/guns/client/tooltip/AttachmentGunGridOverlay.java` **新增** | 静态槽位（tooltip/布局/最终行列表/时间戳）、`submit`、`clearFrame`、`beginTooltip`、`render`；框定位公式与 `GuiScreen#drawHoveringText` 相同；画 11px 高亮条 + 16 列 16×18 图标；GL 状态（深度/光照/矩阵/lighting）配对恢复；异常只打一次 warn、首次成功打一条 info，便于实机判读 |
| `src/main/java/com/tacz/guns/client/tooltip/TooltipLineUtils.java` **新增** | `maxLineWidth()/scaledHeight()/addWrapped()/spaces()`：折行与撑宽公用的唯一入口 |
| `src/main/java/com/tacz/guns/client/tooltip/ClientAttachmentItemTooltip.java` | 重写 `getLegacyTooltipLines`（所有行折行 + Shift 展开占位）；新增 `appendGunGridLines/fallbackGunNamesLine/getGridLayout/getShowGuns/getSupportText`；删除 `gunNamesLine`；`getShowGuns` 初始化方法改名 `loadShowGuns`（避免与新 getter 撞名） |
| `src/main/java/com/tacz/guns/client/event/TooltipEvent.java` | 新增 `onDrawScreenPre/onDrawScreenPost` 两个 `@SubscribeEvent`（转发给 overlay）；`onTooltip` 记录 `lastTooltipStack`；配件分支构造 `ClientAttachmentItemTooltip` 后按需 `submit`（持有最终行列表用于 Post 时复算框宽/行数）；类注释同步 |
| `src/main/java/com/tacz/guns/client/tooltip/ClientBlockItemTooltip.java` | 同类「长文本塞单行」风险：方块 tooltip 描述按 `maxLineWidth` 折行（低风险顺带修） |

未改 `ClientGunTooltip` / `ClientAmmoBoxTooltip` / `ClientAmmoStorageTooltip`，理由见 §5。

---

## 5. 已修 / 未修

### 已修
1. **配件 Shift 展开出屏（本任务主问题）**：图标网格 + 蓝色高亮条，横向 ≤266 px（正常屏幕），纵向按屏幕高度自适应；
   超量截断显示 `…… (+N)`；屏幕装不下时退化单行摘要。
2. **配件描述等文本行**：原先是 `.lang` 里一整行原样塞进 tooltip（作者不写 `\n` 时可能超屏）；现在统一按
   `min(300, scaledWidth-26)` 折行。
3. **方块物品 tooltip**（枪械工作台等）描述行同样折行。

### 未修（判断依据）
| 位置 | 风险 | 处置 |
|---|---|---|
| `ClientGunTooltip` 描述 | 上游/本仓都已按 300 px 折行（`listFormattedStringToWidth(segment, 300)`，`ClientGunTooltip.java:196`），与上游一致；其它行是数据驱动的短行 | 不改（与上游同形态） |
| `ClientAmmoBoxTooltip` / `ClientAmmoStorageTooltip` | 内容 = 弹药名 + 数量 + "and N more..."，均短行 | 不改；其 16×16 图标仍是文本降级（无信息损失，见类注释 TODO(L2)） |
| 物品名首行（vanilla 加） | 自定义超长枪名可能出屏 | 属所有物品的通用原版行为，不在本任务范围 |
| F3+H 的 TACZ id 行 | `§8<id>: "<id>"`，宽度小 | 不改；高度预算已预留 1 行 |

### 已知限制（实机可能遇到，非本任务可修）
1. **网格图标走 1.7.10 `IItemRenderer` 的 `INVENTORY` 渲染**（与创造模式物品栏图标同一路径：
   `ForgeHooksClient#renderInventoryItem:153-208` → `AnimateGeoItemRenderer#renderItem` 的 INVENTORY 分支）。
   该路径当前有独立的「图标/模型错位」bug，由 **r6-itemicon（task-6）** 修；修好后本网格同步正确。
   本网格已对每个图标 `glPushMatrix/glPopMatrix` 兜底，防止单个渲染器漏 pop 矩阵导致逐格累积偏移。
2. **缓存 tooltip 的第三方 GUI**（如 NEI 一帧内为很多物品取 `getTooltip`）会让「本帧最后一个 tooltip 才是刚画出来的」
   判据失效 ⇒ 网格**跳过不画**（宁可不画也不错位），表现为预留区域空白。原版/本仓 GUI 一帧只取一次 tooltip，不受影响。
3. **极窄窗口**（GUI 缩放 4 / scaledWidth 320）悬停屏幕右半侧时，260 px 宽的框会被原版 `drawHoveringText`
   左移（vanilla 只做右侧溢出翻转、不做左边界 clamp：`GuiScreen.java:187-195`），可能左侧被裁 ——
   上游 1.20 的 tooltip 定位逻辑同样如此；相比原来的「几千像素一行」已从根上解决。
4. **性能**：图标是 3D 模型渲染（不是 1.20 的 baked 图标），一帧最多 `16×rows`（最小窗口约 80）个，
   仅按住 Shift 悬停配件时出现，可能比 1.20 略重；本任务未做缓存/限流。

---

## 6. 实机验证步骤

1. 进创造模式物品栏 → TACZ 页签；鼠标悬停任一**枪口/瞄具/握把**类配件，**按住 Shift**：
   - 期望：出现「支持枪械：」+ 11 px 蓝底高亮条 + 每行 16 个 16×18 枪械图标；
   - tooltip 宽度不再超过屏幕（最大约 266 px），枪多时向下变高。
2. 松掉 Shift → 回到「按下 [Shift] 键查看支持枪械」提示。
3. 看日志（首次补画成功打一条 info）：
   `[TACZ] attachment gun grid drawn: <shown>/<total> guns, 16 columns, box=(x,y) w x h`
   - 没有这条 → 网格没补画（对照 `GetFileProblems` 无错后，检查是否第三方 GUI 缓存 tooltip）；
   - 出现 `[TACZ] attachment gun grid render failed (skipped)` warn（带栈）→ 补画异常，把栈发回来。
4. 边界：把 GUI 缩放调到 **4**（scaledHeight 最小 240）再悬停，确认 tooltip 顶/底不出屏；
   若该配件支持的枪很多，末尾应出现灰色 `…… (+N)`。
5. 反向验证另外两个界面（同样走 `renderTooltip` → `DrawScreenEvent.Post`）：
   枪械工作台（悬停配方材料里的配件）+ 改装台（悬停配件槽），Shift 展开应出现同样的网格。
6. 若**图标本身位置错乱**（不是网格整体错位）：那是 r6-itemicon 的 `INVENTORY` 渲染 bug，交给 task-6；
   用第 3 步日志里的 `box=(x,y) w x h` 对照可确认网格框位置算对了。

---

## 7. 上游 / 1.7.10 依据索引

- 上游：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/tooltip/ClientAttachmentItemTooltip.java:81-105`（getHeight/getWidth 宽度封顶）、`:127-142`（renderImage 图标网格）。
- 1.7.10：
  - `build/rfg/minecraft-src/java/net/minecraft/client/gui/GuiScreen.java:156-232`（drawHoveringText 框定位/绘制公式）
  - `build/rfg/minecraft-src/java/net/minecraft/client/gui/inventory/GuiContainer.java:183-187`（槽位 tooltip 在 drawScreen 末尾画）
  - `build/rfg/minecraft-src/java/net/minecraft/client/renderer/EntityRenderer.java:1136-1138`（DrawScreenEvent.Pre/Post 与 drawScreen 的先后）
  - `build/rfg/minecraft-src/java/net/minecraft/item/ItemStack.java:727`（ItemTooltipEvent 的 post 点）
  - `build/rfg/minecraft-src/java/net/minecraftforge/client/ForgeHooksClient.java:153-208`（INVENTORY 自定义渲染器入口）
  - 本仓：`client/tooltip/AttachmentGunGridOverlay.java`、`client/tooltip/TooltipLineUtils.java`、`client/event/TooltipEvent.java`、`client/tooltip/ClientAttachmentItemTooltip.java`

## 8. 校验记录

- `mcp__idea__lint_files(min_severity="error")`（5 个改动文件）= **0 error**，未运行 gradle。
- 布局公式用脚本枚举 `rows=1..8 × k∈{0,1,3,6,10} × pack/trunc∈{0,1}` 校验通过（见 §3）。
- 未新增 mixin / 未改 `mixins.tacz.json` / 未改 `client/gui/**`。
