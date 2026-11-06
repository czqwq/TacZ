# r8-3 · 创造栏左侧 pack-filter「无用按钮」+ 物品 tooltip 过长不换行

任务：task-36。写作用域：`client/gui/components/CreativePackFilters.java`、`client/gui/components/creative/**`、
`client/event/TooltipEvent.java`、`client/tooltip/**`、`mixin/client/CreativeModeInventoryScreenMixin.java`、
`client/resource/manager/PackInfoManager.java`、本报告。
**经 Lead 明确授权追加两处**：新增 `mixin/client/GuiScreenMixin.java`、在 `src/main/resources/mixins.tacz.json`
的 `client` 数组登记 `"client.GuiScreenMixin"`（见 §3）。
**未运行 gradle / 未运行 git 写操作**；改动文件 `mcp__idea__lint_files(min_severity="error")` = **0 error**。

---

## 0. 结论速览

| 现象 | 根因 | 处置 |
|---|---|---|
| **A. 创造栏左侧「ALL + 枪包图标」两个按钮点了没反应** | **不是点击链路坏了**：mixin/refmap/`@Accessor` 桥/点击派发/列表刷新全部生效（§1.1 有实机日志铁证）。真正原因是本安装只有 **1 个内容包**（namespace `tacz`），ALL 与「tacz 包」筛出的 `ItemStack` 列表**逐元素相同** ⇒ 点击只改变页签高亮、永远不改变列表。上游 1.20 单包安装下也是同样数据面（r6 已登记）。 | 按 Lead 裁决 **①**：当前页签可筛内容包 ≤1 时**隐藏整个侧栏**（隐藏后点击/滚轮完全不派发），≥2 个包时**完整保留**上游筛选行为与全部代码。见 §2。 |
| **B. 部分物品 tooltip 过长、超出屏幕且不换行** | 1.7.10 原版 `GuiScreen#drawHoveringText` 有**两处平台差异**：① **完全不折行**（框宽 = 最长行）；② **只有右侧溢出翻转 + 底部 clamp，没有左侧/顶部 clamp**（翻转后 `j2` 可为负 ⇒ 框左半被裁掉）。r6 只给「TACZ 富 tooltip 的部分行」做了折行，**非 TACZ 物品行、原版物品名、自定义 lore、F3+H 行**没有任何宽度约束；且所有 tooltip 都会因 ② 被屏幕裁切。 | 两道防线：① `TooltipEvent#onTooltip` 末尾对**最终整表**按 `min(300, scaledWidth-26)` 折行（覆盖所有物品/所有行）；② 新增 `GuiScreenMixin` 在 `drawHoveringText` HEAD 折行 + 按 1.20 `DefaultTooltipPositioner` 语义 clamp 到屏幕内（**对所有 GUI/所有模组的文本 tooltip 生效**）。见 §3。 |

**最硬的实机证据（本轮新读到的真实运行日志）**：

- `run/client/logs/fml-client-latest.log:5844` `Mixing client.CreativeModeInventoryScreenMixin from mixins.tacz.json into net.minecraft.client.gui.inventory.GuiContainerCreative`
- 同文件 `:5890` `Mixing client.CreativeContainerAccessor from mixins.tacz.json into net.minecraft.client.gui.inventory.GuiContainerCreative$ContainerCreative`
- 同文件 `:5849-5870` 逐个 remap `func_73866_w_()`(initGui) / `func_147050_b`(setCurrentCreativeTab) / `func_73863_a`(drawScreen) / **`func_73864_a`(mouseClicked)** / `func_146274_d`(handleMouseInput)
  ⇒ **点击注入确实在目标方法上**（不是「画出来了但没挂 hitbox」）。
- 同文件 `:6016` `[TACZ] attachment gun grid drawn: 49/49 guns, 16 columns, box=(-22,122) 260x140`
  ⇒ tooltip 框被算到 **x = -22**，左侧被裁 22 px —— 这是 §3.2 定位缺陷的直接实机证据（折行救不了它）。

---

## 1. A · 创造栏 pack filter：根因与证据

### 1.1 点击链路逐段核对（结论：全部生效）

| 环节 | 本工程位置 | 证据 |
|---|---|---|
| mixin 注册 | `src/main/resources/mixins.tacz.json:24`（`client.CreativeModeInventoryScreenMixin`）、`:26`（`client.CreativeContainerAccessor`） | 运行日志 `:5844` / `:5890` 显示两个 mixin 都被应用到目标类 |
| refmap（生产/开发运行期名映射） | `build/tmp/mixins/mixins.tacz.refmap.json` → `mouseClicked:**func_73864_a(III)V**`、`initGui:func_73866_w_()V`、`drawScreen:func_73863_a(IIF)V`、`handleMouseInput:func_146274_d()V`；`itemList:**field_148330_a**`、`scrollTo:**func_148329_a(F)V**` | 文件内容；运行日志 `:5849-5870` 逐条 remap 成功 |
| 按钮绘制 | `CreativeModeInventoryScreenMixin#tacz$drawPackFilterButtons`（`drawScreen` TAIL）→ `PackFilterButton#drawButton` | 用户截图里 ALL / 枪包按钮**确实被画出来**（说明本条注入生效） |
| 点击派发 | `CreativeModeInventoryScreenMixin#tacz$clickPackFilterButtons`（`mouseClicked` HEAD，cancellable）→ `GuiButton#mousePressed`（命中矩形）→ `PackFilterButton#mousePressed` → `select()` | 运行时日志 `:5849-5863` 显示 `mouseClicked` 目标已 remap；r6 已核对过方法体（`docs/review/r6/item-icon.md` §1.4） |
| 列表刷新 | `select → applyFilter → showItems → containerAccess().itemList().clear()+addAll()` + `scrollTo(0)`；桥实现 `CreativePackFilterContainerAccess`（`@Accessor("itemList")` / `@Invoker("scrollTo")`） | 1.7.10 原版载体：`GuiContainerCreative$ContainerCreative#itemList`（`GuiContainerCreative.java:1006`）、`#scrollTo(float)`（`:1038`） |
| 滚轮派发 | `tacz$scrollPackFilters`（`handleMouseInput` HEAD）→ `CreativePackFilters#mouseScrolled` | 同日志 `:5861` |

### 1.2 「点了没反应」的真正原因：单包 ⇒ 筛选无信息量

- 本安装只有默认枪包：`run/client/tacz/tacz_default_gun`（其内容 namespace 就是 `tacz`；资源路径
  `assets/tacz/custom/tacz_default_gun/assets/tacz/**`，与上游 `tmp/TACZ-1.20.1/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/**` 一致）。
- TACZ 的 16 个创造页签（`init/ModCreativeTabs.java:60-127`）里的物品 content id 全部是 `tacz:*`
  （枪 `tacz:ak47`、弹药 `tacz:762x39`、配件 `tacz:scope_acog_ta31`、方块 `tacz:target` …）。
- `CreativePackFilters#groupItems`（`CreativePackFilters.java:332-351` 附近）按 content id 的 namespace 分组
  ⇒ 只产生 1 组 `tacz` ⇒ `options = [ALL, tacz]`。
- `ALL.items` = `displayItems` 全量副本；`tacz.items` = 同一批（唯一一组）副本 ⇒ **列表逐元素相同**
  ⇒ 点 ALL 无变化（本来就选中 ALL），点「tacz 包」只改变页签选中高亮（`TAB_TEXTURE_V_SELECTED`），列表不变。
- 上游 1.20.1 在**同样单包安装**下也是这个数据面：`tmp/TACZ-1.20.1/.../client/gui/components/CreativePackFilters.java:71-126`
  的 `updateTab` 同样只建 `[ALL, tacz]` 两项；所以这不是 1.7.10 移植 bug，而是「单包时该 UI 本身没有可表达的信息」。
  （r6 已按「不是 bug」登记：`docs/review/r6/item-icon.md` §1.4、`docs/review/r6/tooltip.md`；本轮 Lead 要求消除用户可见的“无用按钮”。）

### 1.3 处置（Lead 裁决 ①）：可筛内容包 ≤1 时隐藏整个侧栏

改动只在 `client/gui/components/CreativePackFilters.java`：

| 改动 | 位置 | 说明 |
|---|---|---|
| 新增 `sidebarVisible` 状态字段 | 字段区 | 唯一可见性事实源；隐藏后 `mouseScrolled` 直接返回 false |
| `updateTab` 在**建完 options 之后**判定 `options.size() <= 2` | `updateTab`（原 `:220-309` 区域） | `options` = ALL + 本页签实际出现的各内容包组；`size() <= 2` ⇔ 本页签能筛出的包 ≤1 ⇔ 筛选不改变任何条目 |
| 命中时调用 `hideWidgetControls()` 并 return | 同上 | **不清** `currentTab/sourceItems/packInfoVersion`：`drawScreen` HEAD 每帧调 `updateTab`，清掉这些标记会重新枚举整页物品并把列表钉回第一页（r6 修过的「创造栏无法滚动」回归，见类内原注释） |
| `hideWidgetControls()` 抽出 | 原 `hideWidgets` 主体 | 只负责：`sidebarVisible=false`、所有按钮 `setOption(null)`（⇒ `visible=enabled=false`）、▲▼ 隐藏/禁用 |
| `mouseScrolled` 增加 `!sidebarVisible` 早退 | `mouseScrolled` | 隐藏后滚轮不再被侧栏吃掉 |
| 点击派发侧无需改 | `mixin/.../CreativeModeInventoryScreenMixin#tacz$clickPackFilterButtons` | 隐藏按钮 `visible/enabled=false` ⇒ `GuiButton#mousePressed` 恒 false（`GuiButton.java:125-128`），不会误吃点击；**按钮对象仍保留在列表里**（不删除上游能力） |

**判定依据的选取（与 Lead 指令的差异，需说明）**：Lead 建议「依据 `PackInfoManager` 的包数量」。
本实现用的是「当前页签 `groupItems` 实际分出的组数」（`options.size()-1`），它 = `PackInfoManager` 包集合 ∩ 本页签有内容的包，
比全局包数更精确：

- 全局 2 个包、但本页签只有 A 包有物品 ⇒ 点 B 包按钮也不会改变列表（B 组不存在、按钮根本不会画），仍应隐藏；
- 全局 1 个包但某第三方包内容 id 借用了 `tacz` namespace ⇒ 实际仍只有 1 组，隐藏正确。

即判定条件就是「筛选是否可能改变列表」本身，比包计数更贴近用户可见语义。该判定写在 `CreativePackFilters` 的刷新逻辑里（Lead 要求的落点）。

**保留能力**：多包（`options.size() >= 3`）时 `sidebarVisible=true`，`updateButtons()/applyFilter()/showItems()/▲▼ 翻页`
与上游 `CreativePackFilters` 完全一致，一行未删。为了实机可判读，`select()` 末尾新增一条 INFO 日志：
`[TACZ] creative pack filter selected: <all|namespace> (<N> items)`。

**隐藏后的命中行为核对**：

- 点击：`Visible=false` 的 `GuiButton#mousePressed` 不命中（`GuiButton.java:125-128`：`enabled && visible && 坐标`），
  `PackFilterButton#mousePressed` 还额外要求 `option != null`（`hideWidgetControls` 已置 null）⇒ 双保险不派发。
- 滚轮：`CreativePackFilters#mouseScrolled` 第一行 `!sidebarVisible` 早退 ⇒ `handleMouseInput` HEAD 注入不会 cancel，
  滚轮照常滚物品列表。
- 悬停 tooltip：`PackFilterButton#drawButton` 在 `!visible` 时直接 return ⇒ 不会画悬停框。

**上游差异（须记录）**：上游 1.20.1 单包安装下**仍显示**侧栏（因为它在语义上代表“选择内容包”，即便只有 1 个）。
本移植为消除“点了没反应的按钮”按可用性隐藏；属 **UI 可用性 / 数据差异**，不是平台 API 差异。
若日后要 100% 对齐上游观感，把 `updateTab` 里 `if (this.options.size() <= 2) { ... }` 整块删掉即可回滚（单点回滚）。

---

## 2. B · tooltip 折行 + 边界 clamp

### 2.1 根因（两处，均为 1.7.10 平台差异）

1. **不折行**：`build/rfg/minecraft-src/java/net/minecraft/client/gui/GuiScreen.java:156-232`
   （`drawHoveringText`）的框宽 = 最长行像素宽（`:164-176`），逐行 `font.drawStringWithShadow(s1, j2, k2, -1)`（`:212-223`），
   **没有任何 wrap**。上游 1.20.1 的 tooltip 组件按 `getWidth(Font)` 预留、由排版器统一约束
   （`tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/client/gui/GuiGraphics.java:575-616` 的 `renderTooltipInternal`），
   不会横向出屏（r6 已用 `ClientAttachmentItemTooltip` 的 `getWidth` 封顶 260 论证过，见 `docs/review/r6/tooltip.md` §1）。
2. **无左侧/顶部 clamp**：同文件 `:187-195` 只有
   ```java
   if (j2 + k > this.width)  j2 -= 28 + k;              // 右侧溢出：翻到鼠标左侧
   if (k2 + i1 + 6 > this.height) k2 = this.height - i1 - 6; // 只有底部 clamp
   ```
   翻转到左侧后 **`j2` 不 clamp**，鼠标位于屏幕中/右侧时 `j2 = x + 12 - (28 + 框宽)` 可以为负 ⇒ 框左侧被裁。
   上游 1.20.1 `DefaultTooltipPositioner#positionTooltip`
   （`tmp/minecraft-src-1.20.1/.../tooltip/DefaultTooltipPositioner.java:22-31`）在翻转后还有
   `vector2i.x = Math.max(vector2i.x - 24 - width, 4)` —— 框与左边缘至少留 **4 px**。
   **实机证据**：`run/client/logs/fml-client-latest.log:6016` 里 r6 网格 overlay 自己按同一公式算出的框是
   `box=(-22,122) 260x140`（见 `AttachmentGunGridOverlay` 的 `x` 计算）—— 即该帧 tooltip 真的被裁掉左侧 22 px。

### 2.2 修法（两道防线）

#### 防线 1：`TooltipEvent#onTooltip` 对最终整表折行（覆盖所有物品）

`ItemTooltipEvent` 在 `ItemStack#getTooltip` 的**最后**触发
（`build/rfg/minecraft-src/java/net/minecraft/item/ItemStack.java:727` 的 `ForgeEventFactory.onItemTooltip`），
此时行表已包含「物品名 + `addInformation` 行 + 附魔/属性/自定义 lore 行 + 本模组富 tooltip 行 + F3+H id 行」。
因此在 `TooltipEvent#onTooltip` 末尾调用

```java
TooltipLineUtils.wrapLines(event.toolTip, font, TooltipLineUtils.maxLineWidth());
```

- 宽度上限 `TooltipLineUtils#maxLineWidth()` = `min(300, scaledWidth - 26)`（300 对齐上游
  `ClientGunTooltip` 的 `font.split(..., 300)`；`-26` = 屏幕左右各 20 安全边 + 框左右各 3 px 边框）。
- 逐行 `FontRenderer#listFormattedStringToWidth` 折行（保留 § 格式码）。
- **纯空白行不折**（`isLayoutBlank`）：r6 配件图标网格用「空行占高 + 空格撑宽」预留区域，
  这些布局行只参与框宽/行高计算，不能断行（否则网格行索引/框宽会变）。
- 覆盖范围：**所有**经 `ItemStack#getTooltip` 的路径 —— TACZ 富 tooltip、原版物品名（超长的自定义名）、
  附魔/属性行、**自定义 lore**、以及**其它模组物品**（`ItemTooltipEvent` 对所有 `ItemStack` 都触发）。
- 绝不抛异常：`tooltip` 每帧重算，异常会被吞掉并 `debug` 一次（`wrapTooltipLines`）。

**接线确认（原任务要求项）**：`TooltipEvent` 由 `client/ClientProxy.java:158-159` 注册进
`MinecraftForge.EVENT_BUS`（`FORGE_BUS_CLASSES` 里注释就是 `// ItemTooltipEvent`），
`ItemTooltipEvent` 在 `ItemStack#getTooltip` 末尾触发（`ItemStack.java:727`）⇒ 折行点正确。
注意 1.7.10 的 `@SubscribeEvent` 处理方法必须是**实例方法**（FML `ASMEventHandler` 用 `INVOKEVIRTUAL` 回调），
本类原有 `onTooltip(ItemTooltipEvent)` 已是实例方法，未改签名。

#### 防线 2：新增 `mixin/client/GuiScreenMixin`（折行 + 1.20 等价 clamp，全局 tooltip 绘制路径）

**为什么还要 mixin**：折行只能保证「每行 ≤ min(300, 屏宽-26)」，救不了 §2.1 第 2 条的**位置越界**；
而 1.7.10 的 tooltip 位置是在 `GuiScreen#drawHoveringText` **方法内部**算的，除改写该方法没有别的钩子
（`renderToolTip` 只是转调，没有坐标参数可改；`DrawScreenEvent` 在绘制之后）。

**mixins.tacz.json 登记**（Lead 授权）：`"client": [ ... "client.GuiScreenMixin" ... ]`。

**目标方法**：`GuiScreen#drawHoveringText(List, int, int, FontRenderer)` —— 1.7.10 所有文本 tooltip 的唯一落点：
- `GuiScreen#renderToolTip`（背包/容器/槽位 tooltip）→ `drawHoveringText`
- `GuiScreen#drawCreativeTabHoveringText` → `func_146283_a(List,int,int)` → `drawHoveringText`（创造栏页签 tooltip）
- 其它模组对以上方法的调用同样经过它。
（`GuiContainerCreative#renderToolTip` 的搜索页签分支也是走 `func_146283_a`。）

**关键命名事实（决定 mixin 能否注入，已核实）**：`drawHoveringText` 是 **Forge 新增方法**，不是原版方法 ——
`D:\gradle_cache\caches\minecraft\...\unpacked\forgepatches.zip` 里 `net/minecraft/client/gui/GuiScreen.java.patch`
的 `@@ -121,6 +126,11 @@` 段把原版 `func_146283_a` 的落点改成了 `drawHoveringText(p_146283_1_, p_146283_2_, p_146283_3_, font)`
并新增该 4 参方法；`mcp_stable/12` 的 `mcp-srg.srg` 中**没有** `GuiScreen/drawHoveringText` 条目
（只有 `drawCreativeTabHoveringText`/`func_146283_a`/`func_146285_a` 等）。后果：

- Mixin AP 不会为它生成 refmap 条目（构建时若出现 `No obfuscation mapping for ... drawHoveringText` 的 warning
  属**预期**，不是错误）；
- 运行期按**字面名** `drawHoveringText` 解析。dev 运行期（`build/rfg/recompiled_minecraft-1.7.10.jar`，
  `javap` 实测 `protected void drawHoveringText(List, int, int, FontRenderer)`）+ 生产期（Forge 二进制补丁给
  混淆类加成员时，Forge 新增成员同样保留字面名）两套命名空间下都成立。
- handler 形参 `FontRenderer` 是普通代码引用（dev 用 MCP 类名、生产被 reobf 成 notch 类名），与目标方法描述的
  类型命名空间一致；`List/int/int` 无映射问题。

**实现**（`mixin/client/GuiScreenMixin.java` 只有 1 个 `@Inject`，逻辑放在普通类 `client/tooltip/GuiTooltipRenderer.java`）：

1. `@Inject(method = "drawHoveringText", at = @At("HEAD"), cancellable = true)`；
2. 调 `GuiTooltipRenderer.renderHoveringText(textLines, mouseX, mouseY, font)`：
   1. 复制行表 → `TooltipLineUtils.wrapLines(..., maxLineWidth())`；
   2. 用折行后的行表复算 **框宽**、**框高**（高 = `GuiScreen.java:180-185` 的同公式 `boxHeight`）；
   3. 先按 1.7.10 原公式算 `(j2, k2)`，再按 1.20 语义 `Math.max(x, 4)` / `Math.max(y, 4)`
      —— 位置计算集中在 `TooltipLineUtils#hoveringTextOrigin`（**单点真源**，`AttachmentGunGridOverlay` 也复用，见 §2.3）；
   4. 用移植层 `mcshim.GuiGraphics#drawHoveringText`（1.7.10 原算法的等价实现：同样的渐变框/边框色/文字阴影/GL 状态配对）
      绘制，入参回推成 `(originX - 12, originY + 12)` 使 shim 内部的翻转/底部 clamp 不再触发 ⇒ 画在 clamp 后的位置；
   5. 成功返回 `true` ⇒ mixin `ci.cancel()`（不再走原方法）；异常/字体为空返回 `false` ⇒ 不 cancel，**原版照常绘制**（只降级不崩，异常只 warn 一次）。
3. 混入类内**没有任何嵌套/内部/匿名类**（`compatibilityLevel: JAVA_8` 硬约束），也未 `@Shadow` 父类成员；
   目标方法 `drawHoveringText` 是 `GuiScreen` **自身声明**的方法（`GuiScreen.java:156`），不是父类方法。

### 2.3 r6 配件图标网格 overlay 的一致性（必须改，否则 clamp 后网格错位）

`client/tooltip/AttachmentGunGridOverlay#draw` 原先自己重算「与 `drawHoveringText` 相同」的框位置来补画
高亮条 + 枪械图标网格。现在 tooltip 本体被 mixin clamp 到 `x >= 4`，overlay 若仍用旧公式（不 clamp）就会把图标画在框外。
已改为复用同一入口：

```java
int[] origin = TooltipLineUtils.hoveringTextOrigin(mouseX, mouseY, maxWidth,
        TooltipLineUtils.boxHeight(lines.size()), screen.width, screen.height);
```

- `lines` 就是 `ItemTooltipEvent#toolTip` 的**同一个 List 引用**（r6 `submit` 时保存的），
  防线 1 的折行是**就地**修改该表 ⇒ overlay 复算用的行宽/行数与真正画出来的 tooltip 一致；
- 撑宽/占高行不被折行 ⇒ r6 的 `reservedLines` 公式与行索引约定不变。

### 2.4 顺带对齐的一处（`ClientGunTooltip`）

`ClientGunTooltip#getText` 里枪械描述原来用**固定 300** 折行（照抄上游 `font.split(..., 300)`），
但 1.7.10 没有 1.20 的排版器兜底：小窗口 / 大 GUI 缩放下 300 px 可能比可用宽度还宽（`scaledWidth <= 326` 时）。
已改用 `TooltipLineUtils#maxLineWidth()`（= `min(300, scaledWidth-26)`），注释里记录「300 是上游固定值，1.7.10 需按屏幕收窄」。
其它行（弹药名/数量/等级/类型/伤害/穿甲/爆头/移速/Z 键/枪包名）是数据驱动短行，由防线 1 兜底，不再逐类改。

### 2.5 对其它模组 tooltip 的影响面（Lead 要求写明）

| 路径 | 影响 |
|---|---|
| 任何模组物品的 `ItemStack#getTooltip` 结果 | **会被按屏幕宽度折行**（仅当某行真的超宽；正常短行原样不动）。这是本任务的目标：非 TACZ 物品/长 lore 也要不出屏。行内容/颜色/顺序不变，只插入断行。 |
| 任何 GUI 的 `GuiScreen#renderToolTip` / `drawCreativeTabHoveringText` | **绘制被 `GuiScreenMixin` 接管**：折行 + clamp 后由 `mcshim.GuiGraphics` 重画（视觉等价于原版：同框色/同边框/同阴影/同 GL 状态），异常自动回退原版方法。 |
| 其它模组在 `GuiScreen#drawHoveringText` 上的 `@Inject` | 本 mixin 在 HEAD 命中后 `cancel()` ⇒ **它们在该方法上的注入不会执行**（本整合包内 hodgepodge/lwjgl3ify/GTNHLib 的 `MixinGuiScreen` 经日志核对只有 `mouseGrabbing` 等注入、不涉及 `drawHoveringText`；运行日志无 `drawHoveringText` 相关注入记录）。这是本修法的已知代价：与「原版 tooltip 绘制路径」互斥。 |
| 第三方自绘 tooltip（不走 `GuiScreen#drawHoveringText`，例如 NEI/JEI 自己的窗口或直接 `mcshim.GuiGraphics#drawHoveringText`） | 不受 `GuiScreenMixin` 影响；但若其行来自 `ItemStack#getTooltip`，仍被防线 1 折行。 |
| TACZ 自定义 GUI（改装台/枪械工作台/弹药库界面） | 它们的 tooltip 走 `mcshim.GuiGraphics#drawHoveringText`（`GunRefitScreen.java:213/250/262`、`GunSmithTableScreen.java:912/918/1151`、`AmmoStorageScreen.java:142/166`、`FlatColorButton.java:89`）⇒ 行宽被防线 1 保证，但**这些路径没有边界 clamp**（`mcshim` 属平台层，不在本任务 scope）。它们都是全屏/居中界面，tooltip 越界概率低；如需一并 clamp，正确落点是后续给 `mcshim/GuiGraphics#drawHoveringText` 复用 `TooltipLineUtils#hoveringTextOrigin`（已留单点真源，改动量 1 行）。 |

---

## 3. 改动文件清单

| # | 文件 | 改动 | 依据 |
|---|---|---|---|
| 1 | `src/main/java/com/tacz/guns/client/gui/components/CreativePackFilters.java` | 新增 `sidebarVisible`；`updateTab` 在建完 options 后判定 `options.size() <= 2` → 隐藏侧栏并恢复完整列表；抽出 `hideWidgetControls()`（不清对账标记）；`mouseScrolled` 加 `sidebarVisible` 早退；`select()` 加一条 INFO 日志；类/字段 javadoc 记录单包差异 | Lead 裁决 ①；上游单包数据面 `tmp/TACZ-1.20.1/.../CreativePackFilters.java:71-126`；1.7.10 `GuiButton.java:125-128`（visible/enabled 命中条件） |
| 2 | `src/main/java/com/tacz/guns/client/tooltip/TooltipLineUtils.java` | 新增 `TOOLTIP_MARGIN=4`、`boxHeight(int)`、`hoveringTextOrigin(...)`（1.7.10 公式 + 1.20 clamp）、`wrapLines(...)` + `wrapLine/isLayoutBlank`（就地整表折行，跳过布局行） | 1.7.10 `GuiScreen.java:164-232`；1.20 `DefaultTooltipPositioner.java:22-31` |
| 3 | `src/main/java/com/tacz/guns/client/tooltip/GuiTooltipRenderer.java` **新增** | `renderHoveringText(...)`：折行 → 复算框宽高 → `hoveringTextOrigin` → 反推入参交给 `mcshim.GuiGraphics#drawHoveringText`；异常 warn 一次并返回 false（回退原版） | 同上 + `tmp/minecraft-src-1.20.1/.../GuiGraphics.java:575-616`（排版器职责） |
| 4 | `src/main/java/com/tacz/guns/mixin/client/GuiScreenMixin.java` **新增**（Lead 授权） | `@Mixin(GuiScreen.class)` + `@Inject(method="drawHoveringText", at=HEAD, cancellable)`；无嵌套类、无 `@Shadow` | 见 §2.2 |
| 5 | `src/main/resources/mixins.tacz.json`（Lead 授权） | client 数组新增 `"client.GuiScreenMixin"`（共 15 项） | 同上 |
| 6 | `src/main/java/com/tacz/guns/client/event/TooltipEvent.java` | `onTooltip` 末尾新增 `wrapTooltipLines(event.toolTip)`（整表折行）；新增 `wrapTooltipLines` 私有方法（吞异常）；类 javadoc 更新 | `ItemStack.java:727`（事件触发点）、`GuiScreen.java:156-232`（不折行） |
| 7 | `src/main/java/com/tacz/guns/client/tooltip/AttachmentGunGridOverlay.java` | 框定位改为复用 `TooltipLineUtils#boxHeight/hoveringTextOrigin`（与 tooltip 本体 clamp 一致，避免网格错位） | 1.7.10 `GuiScreen.java:178-195` + 1.20 `DefaultTooltipPositioner.java:22-31` |
| 8 | `src/main/java/com/tacz/guns/client/tooltip/ClientGunTooltip.java` | 枪械描述折行宽度 `300` → `TooltipLineUtils#maxLineWidth()`（= min(300, scaledWidth-26)），注释记录平台差异 | 上游 `ClientGunTooltip` 的 `font.split(..., 300)`；1.7.10 无排版器兜底 |

未改（有意）：`inventory/tooltip/**`、`client/gui` 其它文件、`mcshim/**`、`PackInfoManager`（判定不需要它新增 API，理由见 §1.3）。

---

## 4. 校验记录

- `mcp__idea__lint_files(min_severity="error")`：上述 7 个 java 文件 = **0 error**（改动后复跑两次）。
- `mixins.tacz.json` 用 JSON 解析器校验通过（client 数组 15 项，含既有 `client.AbstractButtonMixin` 未丢失）。
- **未运行 gradle / git 写操作**（遵守任务硬性规则）。
- 静态核对：`GuiScreen#drawHoveringText` 是 `GuiScreen` 自身方法（非父类），handler 签名
  `(List, int, int, FontRenderer, CallbackInfo)` 与目标描述符 `(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V` 一致；
  refmap 由构建期 Mixin AP 生成（同仓既有 `func_73864_a` 等映射证明 MCP→SRG 链路可用，`drawHoveringText` 是 MCP 命名方法）。

---

## 5. 待 Lead 构建验证点

1. `./gradlew compileJava` 通过。**注意 `GuiScreenMixin` 的目标 `drawHoveringText` 是 Forge 新增方法、无 MCP→SRG 映射**：
   - 构建期若 Mixin AP 打 `No obfuscation mapping for ... drawHoveringText` 之类 **warning 属预期**（非 error，见 §2.2「关键命名事实」）；
   - `build/tmp/mixins/mixins.tacz.refmap.json` 里不会出现该方法的映射条目，运行期按字面名解析；
   - 若反而出现 **error**（AP 把“找不到映射”当错误），备选方案：把注入点换成映射确定的
     `GuiScreen#renderToolTip`（`func_146285_a`，1.7.10 物品 tooltip 入口）+ `func_146283_a`（创造栏页签/搜索页签）
     各注入一次，逻辑不变（`GuiTooltipRenderer` 已把折行/clamp 独立成普通类，换注入点只改 mixin 两行）。
2. 启动客户端**不崩**（`required: true`，若 `GuiScreenMixin` 注入失败会直接崩；崩了先看目标名/描述符）。
3. 进创造模式物品栏：本安装（单包）**左侧不再出现 ALL/枪包按钮**，且不报异常。

## 6. 实机验证步骤

1. **创造栏（当前安装 = 单包）**：打开创造模式物品栏任一 TACZ 页签 → 左侧**没有** ALL / 枪包按钮；
   物品列表 = 该页签全部物品；在这个区域左键/滚轮 = 正常操作物品列表（不被隐藏按钮吃掉）。
2. **多包场景（验证保留能力）**：把任一符合 TACZ 枪包格式的第二个包（不同 namespace）放进 `run/client/tacz/`（如
   `docs/review/r7/test-pack/tacz_review_pack`，注意其 namespace 为 `tacz_review_pack`）→ 重启 → 进有该包物品的页签：
   侧栏应重新出现 ALL + 各包按钮；点「包按钮」→ 列表只剩该包条目 + 页签高亮切换；
   点 ALL → 恢复全部；日志出现 `[TACZ] creative pack filter selected: <namespace> (<N> items)`。
3. **tooltip 换行**：悬停长 tooltip 物品（推荐 F3+H 开高级提示后悬停任意带描述的**枪械/配件**；
   或任意其它模组的超长 lore 物品）→ 长行应换行成多行，**不再横向出屏**。
4. **tooltip 边界 clamp（本次修的重点之一）**：把鼠标移到屏幕**中部/右侧**再悬停上述物品
   （此前会翻转后左半被裁）→ tooltip 框整体仍在屏幕内，左边缘不越界；
   对照日志：r6 网格日志里的 `box=(x,y)` 的 `x` 不再出现负值（`AttachmentGunGridOverlay` 首次成功 INFO 行）。
5. **回归**：
   - 创造栏 16 个页签/分页（`2 / 3`）正常，TACZ 图标（r6 修复）不变化；
   - 背包/容器/工作台/改装台悬停 tooltip 的框样式（渐变底色 + 紫边 + 阴影文字）与原版一致（GuiScreenMixin 用 shim 重画）；
   - 配件 Shift 展开的图标网格（r6）位置仍贴合 tooltip 框（overlay 已同步 clamp）；
   - 其它模组（hodgepodge/GT 等）tooltip 不崩、不异常换行（只对超宽行断行）。
6. **判读「为什么之前单包没有效果」**（如需回复用户）：单包安装下 ALL 与唯一包筛选结果逐元素相同；
   本版已改为单包隐藏侧栏，多包装枪包即可看到筛选效果。

## 7. 风险 / 单点回滚

| 风险 | 说明 | 回滚 |
|---|---|---|
| `GuiScreenMixin` 注入失败 → 启动崩（`required:true`） | 目标方法是 `GuiScreen` 自身方法，refmap 由 AP 生成；Lead 构建/启动验证第一项就是它 | 从 `mixins.tacz.json` 删除 `"client.GuiScreenMixin"` + 删除 `GuiScreenMixin.java`，tooltip 仍有防线 1（只是失去 clamp） |
| 其它模组在 `drawHoveringText` 上的 HEAD/TAIL 注入被跳过 | 见 §2.5；本整合包经日志核对无相关注入 | 同上 |
| `GuiTooltipRenderer` 中 `mcshim` 绘制与原版细微差异（若渲染异常已被 catch 回退原版） | 框色/边框色/文字阴影/GL 状态逐项照抄原版；实机第 5 项回归确认 | 删除 mixin 即回退 |
| 单包隐藏让用户“看不到筛选入口” | Lead 裁决 ① 的预期行为；多包时保留；配置项 `RenderCreativePackSidebar` 仍可整体关闭 | 删除 `updateTab` 中 `if (this.options.size() <= 2)` 整块即回上游观感 |
| tooltip 折行改变其它模组物品 tooltip 行数（如依赖行数的第三方 GUI） | 只对**超宽**行断行（正常物品不受影响）；`ItemTooltipEvent` 的官方语义就是允许修改行表 | `TooltipEvent#onTooltip` 删掉 `wrapTooltipLines(...)` 一行 |

---

## 8. Lead 构建修正（Mixin 注入点调整，2026-11-06）

首轮 `./gradlew spotlessApply build` 在 `:compileJava` 失败：

```
GuiScreenMixin.java:55: 错误: Unable to locate obfuscation mapping for @Inject target drawHoveringText
```

原因与 §5 预判一致：`drawHoveringText(List,int,int,FontRenderer)` 是 **Forge 补丁新增成员**，
`mcp-srg.srg` 里没有该条目（`GuiScreen` 只有 `func_146283_a (Ljava/util/List;II)V` 等），
Mixin AP 把它当 error 而不是 warning。

**修正（Lead 已实施）**：`GuiScreenMixin` 改为注入**已映射入口**，`GuiTooltipRenderer` 逻辑不变：
- `renderToolTip`（MCP 名，srg `func_146285_a`）—— 物品 tooltip 唯一落点，行内容/稀有度着色按
  `GuiScreen.java:122-140` 逐行复刻后交给折行/clamp 渲染器；
- `func_146283_a`（srg 名本身，`GuiScreen.java:151-154`）—— 原版 List tooltip 入口；
- `drawCreativeTabHoveringText`（MCP 名，srg `func_146279_a`，`GuiScreen.java:146-149`）—— 创造栏页签兜底。

构建后 refmap（`build/tmp/mixins/mixins.tacz.refmap.json`）确认三个映射均生成：
```
GuiScreenMixin: renderToolTip -> func_146285_a
                func_146283_a  -> func_146283_a
                drawCreativeTabHoveringText -> func_146279_a
```
第二次 `./gradlew spotlessApply build` **BUILD SUCCESSFUL**（jar 22:25:55）。
