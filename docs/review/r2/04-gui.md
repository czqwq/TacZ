# r2-04 · GUI 按钮审查

> 两批：**批 1** = task-4（GUI 按钮 / 错位 / 命中区 / 贴图 / tooltip 显示）；
> **批 2** = task-8（Lead 派单：把 `client/tooltip/**` 接回运行期 + mcshim `drawString` 阴影默认值）。

- **作用域（批 1）**：`src/main/java/com/tacz/guns/client/gui/**`（26 文件）+ `client/tooltip/**`（6 文件）
- **作用域（批 2，Lead 扩）**：`client/event/TooltipEvent.java`、`mcshim/GuiGraphics.java`
  （两者原属 draw-gl，它已收工，无冲突）
- **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gui/**`、`client/tooltip/**`、
  `client/event/TooltipEvent.java`、`item/**`（`appendHoverText` / `getTooltipImage` 的构造点）（MC 1.20.1）
- **1.7.10 准绳**：`build/rfg/minecraft-src/java/**`（`GuiScreen`、`GuiContainer`、`GuiButton`、`GuiSlot`、
  `GuiTextField`、`FontRenderer`、`Locale`、`GuiContainerCreative`、`ItemStack`、
  `net/minecraftforge/client/GuiIngameForge`）
- **贴图准绳**：`src/main/resources/assets/tacz/textures/**`（PNG IHDR 实测）+
  `build/rfg/minecraft-src/resources/assets/minecraft/textures/gui/**`（1.7.10 原版实测列举）
- **自查方式**：`mcp__idea__get_file_problems(errorsOnly=true)` + `lint_files(min_severity=error)`，
  两批共 **10** 个改动文件全部 **0 error**；PNG 头只读命令读真实像素；
  **未运行任何 gradle 任务**（grep 全仓、读源码静态判定）

## 覆盖清单

**逐行精读（18）**：`FlatColorButton`、`refit/{GunAttachmentSlot,InventoryAttachmentSlot,RefitTurnPageButton,
RefitUnloadButton,HSVSliderGroup,GunPropertyDiagrams,IComponentTooltip}`、`smith/{ResultButton,TypeButton}`、
`GunPackList`、`CreativePackFilters`（含 `creative/{Hooks,ContainerAccess}`）、`GunRefitScreen`、
`GunSmithTableScreen`、`AmmoStorageScreen`、`GunPackProgressScreen`、`compat/ClothConfigScreen`、
`overlay/{GunHudOverlay,HeatBarOverlay,InteractKeyTextOverlay,KillAmountOverlay,DisplayCaseLockHintOverlay}`、
`toast/GunLevelUpToast`、`tooltip/{ClientGunTooltip,ClientAttachmentItemTooltip,ClientBlockItemTooltip,
ClientAmmoBoxTooltip,ClientAmmoStorageTooltip}`、`inventory/tooltip/**`（5 个数据类，接线用）、
`event/TooltipEvent`、`mcshim/GuiGraphics`（`drawString`/`blit`/`blitNineSliced`/`drawHoveringText` 段）

**结构化审查**：全部自绘控件的「绘制矩形 vs 命中矩形」逐项列表（见 §三）；全部 PNG 尺寸核对（见 §四）；
全部 `textures/*.png` 引用 12 处逐个 `Test-Path` + 与 1.20 上游同名图尺寸比对（全部一致）；
「物品类型 → tooltip 行」对照表（见 §六）

**未覆盖 / 未实机**：所有 hover/尺寸/tooltip 行序结论基于源码与贴图像素静态推导，未 `runClient`；
`GunLevelUpToast`（降级为聊天栏，无 GUI 尺寸问题）只看未改。

---

## 结论速览

> 分三批：**批 1**（task-4）修 5 项；**批 2**（task-8，Lead 派单）把 `client/tooltip/**` 接回运行期，
> 并顺手收掉 2 条自己作用域内的 P2 + 1 条 mcshim 阴影默认值；**批 3**（i18n-icon 交叉复核回复）
> 处理 `PackInfo` null 字段与「配方数据上线后新可达路径」，修 3 项。合计已修 **12** 项。

| 级别 | 已修 | 未修（只报告 / 需 Lead 修） |
|---|---|---|
| **P0** | 1 | 0 |
| **P1** | 5 | 0 |
| **P2** | 5 | 2 |
| **P3** | 0 | 6 |

**最关键的 3 条结论**：

1. **P0 已修 —— 创造模式物品栏完全无法滚动**：`CreativePackFilters.updateTab` 照搬了上游
   `sourceItems == displayItems` 的**身份判等**，而 1.7.10 的等价物
   `CreativeTabs#displayAllReleventItems(List)` 每次调用都产生**新 List + 新 ItemStack 实例**，
   判等恒为 false；mixin 又在 `drawScreen` HEAD **每帧**调它（`force=false`），于是每帧跑
   `showItems → resetScroll.run() + scrollTo(0)`，把创造栏物品列表**永久钉在第一页**，
   并且每帧重建整页 ItemStack。配置 `RenderCreativePackSidebar` 默认 `true`
   （`RenderConfig.java:118`），且 `isFilterableTab` 在 1.7.10 退化为「除搜索/背包页外全部生效」，
   所以**所有创造页签都中招**。已改为「页签身份 + 枪包版本」两个真实信号。
2. **P1 已修 —— `.lang` 里的字面 `\n` 全链路没有 split**：1.7.10 的
   `Locale#loadLocaleData`（`Locale.java:106-126`）只做 `IOUtils.readLines` + `Splitter.on('=')`，
   **不处理任何转义**，所以上游 JSON 的真换行在本工程是「反斜杠 + n」两个可打印字符。
   `PORT_STATUS.md:39/190` 的裁决要求「client 显示前 `split("\\n")`」，但**全仓 0 处实现**
   （`grep 'split('` 只命中两个把真换行当分隔符的 `split("\n")`，对字面量是空操作）。
   结果是枪械描述、配件描述、工台空态提示把 `\n` 当正文画出来。已修 5 处（含本轮新接线的弹药行）。
3. **P1 已修（批 2）—— `client/tooltip/**` 5 个类原本全是死代码**：0 实例化点、0
   `appendHoverText` 调用者 → 枪械的弹药/伤害/穿甲/爆头/移速/等级/类型/改装提示/枪包名与描述
   **在游戏里完全不显示**。现已接到 `client/event/TooltipEvent#onTooltip(ItemTooltipEvent)`，
   按「枪械 / 配件 / 弹药 / 弹药盒 / 弹药库 / 其它方块物品」6 类分派（对照表见 §六），
   `renderImage`（位图）类明确降级并登记。

---

## 一、已修问题

| # | 文件:行 | 现象 | 修法 | 依据 |
|---|---|---|---|---|
| **F1**（**P0**） | `client/gui/components/CreativePackFilters.java:243-257` | 创造模式**物品栏无法滚动**（每帧被拉回第一页）+ 每帧重建整页 ItemStack。侧栏默认开启，且 1.7.10 的 `isFilterableTab` 退化为「所有页签」→ **全部创造页签都受影响** | 把上游的 `!force && currentTab == tab && sourceItems == displayItems && packInfoVersion == reloadVersion` 改为 `!force && currentTab == tab && sourceItems != null && packInfoVersion == reloadVersion`，并把 `getDisplayItems(tab)` 移到早返回**之后**（不再每帧收集） | ① 1.20 `CreativeModeTab#getDisplayItems()` 返回 tab 缓存的同一 Collection 实例（身份判等有效）；② 1.7.10 `CreativeTabs#displayAllReleventItems(List)`（`CreativeTabs.java`）与 `Item#getSubItems(Item,CreativeTabs,List)` 都是**往调用方列表里灌** `new ItemStack(...)` → 恒不等；③ `CreativeModeInventoryScreenMixin.java:235-240` 在 `drawScreen` HEAD 每帧 `updateTab(tab,false)`；④ `applyFilter → showItems` 无条件 `resetScroll.run() + scrollContainerTo(0)`（1.7.10 等价上游 `menu.scrollTo(0)`，`GuiContainerCreative.java:1038-1057` 会把 45 个可见槽位按 j=0 重建）；⑤ `RenderConfig.java:118` 默认 `true`；刷新时机由 `init()`（`force=true`，`initGui` TAIL）与 `setCurrentCreativeTab` TAIL（`force=true`）覆盖，不依赖每帧轮询 |
| **F2**（P1） | `client/tooltip/ClientGunTooltip.java:181-206` | 枪械 tooltip 的**描述**把字面 `\n` 当正文显示（并按宽度乱折行） | 译文先按字面量 `split("\\\\n", -1)`，再对每段 `listFormattedStringToWidth(seg, 300)` 后拼接 | `Locale.java:106-126` 不处理转义；`PORT_STATUS.md:39/190` 裁决「client 显示前 split("\\n")」；`custom/tacz_default_gun/.../lang/en_US.lang:141`（`tacz.gun.timeless50.desc` 含 `\n`）、`zh_CN/en_US` 同族 |
| **F3**（P1） | `client/tooltip/ClientAttachmentItemTooltip.java:328-349`、`client/tooltip/ClientBlockItemTooltip.java:155-172` | 配件/方块的**描述**把字面 `\n` 当正文显示（配件描述大量含 `\n`，是最常见的一处） | `text.split("\n")`（真换行，对字面量是空操作）→ `text.split("\\\\n|\\r?\\n")`（两种形态都接受） | 上游 1.20 `I18n.get()` 返回 JSON 解析后的**真换行**；1.7.10 `.lang` 是字面量（同上）。实测 `custom/tacz_default_gun/.../lang/en_US.lang:227,261-266` 共 6 个配件 `.desc` 含 `\n`；方块 `.desc` 目前无 `\n`（属同类隐患） |
| **F4**（P1） | `client/gui/overlay/DisplayCaseLockHintOverlay.java:44-58,60-63` | 「展示柜已上锁」提示**一旦出现就永久留在屏幕中央**（提示不会消失） | 增加与帧/事件无关的墙钟截止 `expireAt = now + 40*50ms`，`render` 同时判 `ticksRemaining > 0 && now < expireAt` | 该类的 `@SubscribeEvent onClientTick` **全仓从未注册**（`grep DisplayCaseLockHintOverlay` = 5 命中：只有 `ClientSetupEvent.java:397` 取 `::render`、`GunDisplayCaseClientInteractionEvent.java:132` 调 `show`），所以 `ticksRemaining` 永不递减。上游用 `ForgeGui#getGuiTicks()`，墙钟等价（20 tick/s = 50ms） |
| **F5**（P2） | `client/gui/components/FlatColorButton.java:152-163` | 枪械工作台的 9×9「F」过滤按钮**文字永远不可见**（按钮渲染成空白灰条）；同类窄按钮同样 | 从「无条件按 `width-4` 截断」改为「`getStringWidth(text) <= width` 就原样画，否则才截断」 | `width=9`（`GunSmithTableScreen.java:482`）→ 上限 5px，而字母 `F` 在 1.7.10 默认字体宽 **6px** → `FontRenderer#trimStringToWidth("F", 5)` 返回空串。上游 `AbstractButton#renderScrollingString` 是「裁剪窗口内滚动」，文字一定可见 |
| **F6**（**P1**，批 2） | `client/event/TooltipEvent.java:267-410`（新增 `appendRichTooltip`/`buildRichTooltip`/`ammoHoverText`/`ammoStorageTooltip`/`mergeAmmoStorageEntry`/`saturatedAdd`）+ `:232-244`（`onTooltip` 里调用） | **`client/tooltip/**` 5 个类 + `inventory/tooltip/**` 5 个数据类全仓 0 实例化点、0 `appendHoverText` 调用者** → 枪械/配件/弹药盒/弹药库/方块物品的富 tooltip（弹药类型与数量、等级、类型、伤害、穿甲、爆头倍率、移速惩罚、改装提示、枪包名、描述）**在游戏里完全不显示** | 在 `ItemTooltipEvent` 里按 `IGun`/`IAttachment`/`IAmmoBox`/`IAmmo`/`AmmoStorageBlockItem`/`BlockItemDataAccessor` 6 类分派，取各 `Client*Tooltip#getLegacyTooltipLines()` 插到 **index 1**（\(= 1.20 `gatherTooltipComponents` 的 `list.add(1, …)`\)）。全程 `try/catch(Throwable)` + 空索引/null id 静默跳过。逐物品的行对照见 §六 | ① 1.20 的富 tooltip 走 `getTooltipImage()` → `TooltipComponent` → `ClientTooltipComponent`（`RegisterClientTooltipComponentFactoriesEvent`），1.7.10 两条都没有，唯一可行入口是 `ItemTooltipEvent`；② `item/AmmoItem.java:95-97` 的 TODO 明确要求「客户端通过 tooltip 事件接回」；③ 上游构造点：`AbstractGunItem.java:398-411`、`AttachmentItem.java:99-101`、`AmmoBoxItem.java:259-274`、`AmmoStorageBlockItem.java:57-85`（含 `MAX_TOOLTIP_ITEMS=5` 与按同 NBT 合并）、`GunSmithTableItem.java:92-96`；④ 上游 `TooltipEvent`（`tmp/.../client/event/TooltipEvent.java`）只加 id 行，与本类的 ① 段并存 |
| **F7**（P2，批 2） | `client/gui/components/CreativePackFilters.java:647-666` | 创造侧栏选项卡：**绘制矩形 `(x, y-3, 28×32)` ≠ 命中矩形 `(x, y, 32×26)`**（上下各 3px 不可点、右侧 4px 空白可点），且间距 29 < sprite 32 → **相邻选项卡重叠 3px**，重叠区「看到的是后一个、点到的是前一个」 | 把整张 28×32 sprite **纵向压进** 28×26 的绘制框（`blit(..., x, y, 28, 26, u, v, 28, 32, 256, 256)`）：绘制矩形 == 命中矩形、相邻不再重叠；图标仍在 `y+5..y+21`（26px 框的竖直中心） | `GuiButton#mousePressed` 用 `xPosition/yPosition/width/height`（`GuiButton.java:125-127`）；`GuiScreen#mouseClicked` 按 `buttonList` 顺序**首个命中即返回**（`GuiScreen.java:241-256`），而 mixin 的自绘顺序是 0→3，两者在重叠区必然打架。代价仅是 32→26 的纵向压缩（无信息损失） |
| **F8**（P2，批 2） | `client/gui/components/smith/TypeButton.java:29-46,82-88`（新增 `IComponentTooltip` + `tooltip` 字段）+ `GunSmithTableScreen.java:666-680`（传 `tabConfig.getName()`）、`:870-878`（统一绘制） | 工作台左侧**分类页签没有悬停 tooltip**（只剩图标，靠图辨认枪械/弹药/配件/方块…） | `TypeButton implements IComponentTooltip`，构造期接名称；在 `drawScreen` 的 widget 循环里加 `else if (widget instanceof IComponentTooltip)` 分支，在所有内容画完后统一 `renderComponentTooltip` | 上游 `GunSmithTableScreen.java:523` `typeButton.setTooltip(Tooltip.create(tabConfig.getName(), tabConfig.getName()))`；1.7.10 `GuiButton` 无控件级 tooltip → 复用本工程已有的 `IComponentTooltip` 契约（`FlatColorButton` 同套路） |
| **F9**（P2，批 2，**mcshim**） | `mcshim/GuiGraphics.java:485-504`（String 5 参）、`:525-533`（`IChatComponent` 5 参） | 「只给颜色」的 `drawString` 重载默认 **不带阴影**，与 1.20 相反 → 少数文字少一层阴影 | 两处默认值 `false` → `true` | 1.20.1 `GuiGraphics#drawString(Font,String,int,int,int)` 内部就是 `drawString(..., true)`，真正不带阴影只能显式传 `false`（内部 `Font#drawInBatch(..., boolean dropShadow, ...)`）。**旁证 1**：本类自己的 `drawCenteredString`（`:536-538`）已用 `true`，而 1.20 的 `drawCenteredString` 正是委托给 5 参重载；**旁证 2**：上游想让文字不带阴影的 40+ 处**一律显式写 `false`**，而**上游**「只给颜色」的 4 处（上游行号：`KillAmountOverlay:66` 连杀数、`GunHudOverlay:143` 版本号、`GunPackList:255` 复选框标签、`ClientAttachmentItemTooltip:134` Shift 提示）恰好都是画在世界/深色底上的文字，同类场景的 `HeatBarOverlay:89` 还显式传了 `true` —— 只有默认值是 `true` 才自洽 |

| **F10**（**P1**，批 3） | 新增 `client/tooltip/PackInfoText.java`；调用点：`GunPackList.java:100-107`、`GunSmithTableScreen#renderPackInfo`（name/version/license/date/authors）、`ClientGunTooltip:347-354`、`ClientAttachmentItemTooltip:98-106`、`ClientBlockItemTooltip:65-73`、`TooltipEvent#ammoHoverText` | 第三方枪包 `gunpack_info.json` 显式写 `"name": null` 会被 gson **覆盖**成 null（i18n-icon r2-07b 实测的机制）→ `new ChatComponentTranslation(null).getFormattedText()` 在 `initializeFromFormat(null)` 抛 **NPE**（`ChatComponentTranslation.java:73-94`）。两处是**每帧/构造期**必崩（`GunSmithTableScreen#renderPackInfo`、`GunPackList` ctor 内立即 `getFormattedText()`），四处是富 tooltip 被 F6 的 catch 吞掉后**整套消失**；另有 `license/version/date` 为 null 时显示字面 `null` / `vnull` | 6 个调用点统一走 `PackInfoText.name(packInfo, namespace)`（name 缺失/空白/等于占位键 → 用 namespace → 再没有才用 `custom.tacz.error.no_name`，20 语种都有该键）与 `PackInfoText.safe(...)`（null → 空串） | 逐字段消费点见 §七 7.5 表；回落顺序与 `CreativePackFilters:275-283` 的既有处理一致；`description` / `url` / `logo` 三个字段经核原本就 null-safe，未改 |
| **F11**（P2，批 3） | `GunSmithTableScreen#isNameMatch`（原 `:317-320`） | **在筛选框里打字就崩**：`recipe.getResult().getResult().getDisplayName()` 两层都没判空，而第三方枪包写出无法解析的 `result.id` 时 `getResult()` 就是 null；本方法在搜索框**每输入一个字符**都跑（`GunPackList#keyTyped` → `parent.initGui()`） | 判空后退回用 `recipe.getId()` 参与匹配（信息不丢、不崩） | 同族另 3 处（`getRecipeSortKey`、`renderPackInfo`、`drawScreen` 的 `resultStack.stackSize`）原本就有判空；本条由 IDE `可能产生 NullPointerException` 警告定位，再结合「199 条配方上线后该路径首次可达」判定为真 bug |

修后自查（三批共 **13** 个改动文件，含 1 个新增）：`get_file_problems(errorsOnly=true)` **全部 `errors: []`**
（`GunSmithTableScreen` 单独复核，批量 lint 对该文件 `timedOut` 已重跑单文件）。
`lint_files(min_severity=warning)` 的剩余项全部是**改动前就有**的风格警告（未使用形参、`@Beta` cache、
可换 switch 等），无 error。未运行 gradle。

---

## 二、未修问题

#### P1-N1 · ~~`client/tooltip/**` 全是死代码 → 富 tooltip 完全不显示~~ → **已修（§一 F6）**

原判定依据（保留作记录，grep 全仓 0 命中）：`new ClientGunTooltip(` / `.appendHoverText(` 等**无任何调用者**；
`item/**` 也没有 `addInformation` 覆写（`item/AmmoItem.java:95-97` 的 TODO 明确要求「客户端通过 tooltip 事件接回」）。
现已在 `client/event/TooltipEvent#onTooltip` 接线，逐物品对照表见 §六。


### P2

#### P2-N1 · ~~`PackFilterButton` 绘制矩形 ≠ 命中矩形（3px 垂直错位 + 4px 宽度冗余）~~ → **已修（§一 F7）**

#### P2-N2 · ~~侧栏包名的悬停文字会被创造栏物品图标覆盖~~ → **复核后无问题（本条原判断有误，撤回）**

- 原判断：包名文字画在 `PackFilterButton#drawButton` 里，而 `GuiButton` 由
  `GuiContainer#drawScreen` 的 `super.drawScreen` 阶段绘制，会被随后在 translate 内绘制的 45 个槽位物品压住。
- **复核结论：不成立。** 侧栏这几个按钮**根本不走 `buttonList`**：`CreativeModeInventoryScreenMixin`
  用 `@Inject(method = "drawScreen", at = @At("TAIL"))` 自己遍历 `tacz$buttons` 调
  `GuiButton#drawButton`（`mixin/client/CreativeModeInventoryScreenMixin.java:243-252`），
  而 `GuiContainer.drawScreen` 的顺序是
  背景 → `super.drawScreen`（`buttonList`）→ **push+translate 画 45 个槽位物品** →
  `drawGuiContainerForegroundLayer` → pop → 拖拽栈 → 槽位 tooltip（`GuiContainer.java:88-192`），
  TAIL 注入点在**全部之后** → 包名文字画在物品图标**之上** ✔。
- 残留（P3-7）：TAIL 也晚于 `GuiContainer` 末尾的槽位 tooltip，若两者重叠包名会盖在物品 tooltip 上
  （上游 `Tooltip.create` 是最顶层，方向相反）。
- 依据：`CreativeModeInventoryScreenMixin.java:234-252`、`GuiContainer.java:88-192`。

#### P2-N3 · ~~工作台左侧分类页签（`TypeButton`）没有悬停 tooltip~~ → **已修（§一 F8）**

#### P2-N4 · ~~5 参 `drawString` 的阴影默认值与 1.20 相反~~ → **已修（§一 F9）**，核实结论见该行「依据」列

#### P2-N5 · `ItemPreviewRenderContext.alpha` 的复位不在 `try/finally` 里（**client/renderer，需 Lead 修**）

- **文件:行**：`client/renderer/block/GunPegboardRenderer.java:196-218`
  （写入 `setAlpha(alpha)` 在 `:201`、复位在 `:214`）、
  `client/renderer/block/GunDisplayCaseRenderer.java:212-235`（写入 `:218`、复位 `:231`）
- **现象**：`ItemPreviewRenderContext.alpha` 是 **static** 字段
  （`client/renderer/item/ItemPreviewRenderContext.java:7`）。`DisplayItemRenderContext.render(...)` 若抛异常，
  `setAlpha(1.0F)` / `setShaderColor(1,1,1,1)` / `disableBlend()` 全部被跳过 →
  静态 alpha 永久停在 `PREVIEW_ALPHA`，此后**整个进程**内所有消费者
  （`renderer/model/AttachmentRender:110/161/190`、`renderer/item/AmmoItemRenderer:126/148/177`、
  `renderer/item/AttachmentItemRenderer:84/117/144/180`、`renderer/item/GunItemRendererWrapper:410/502`）
  都按「预览态」走 `entityTranslucent`，GL 混合状态也一并残留。同理 `poseStack.popPose()` 也在
  `render(...)` 之后（`:218`），异常时栈会失衡。
- **为什么只报告**：这两个文件属 `client/renderer`（draw-gl 的 scope，且其已收工），我没动。
- **建议修法**：把 `DisplayItemRenderContext.render(...)` 与该 `if (alpha < 1.0F) { … }` 复位块、
  以及 `poseStack.popPose()` 一起放进 `try { … } finally { … }`。
  参考形态见 `docs/review/r2/02-draw-gl.md` 的 P2-5 补充（`GunSmithTableRenderer.java:130-134` 已是正确写法）。
- **置信度**：高（代码即证据；draw-gl 自己在 `02-draw-gl.md:302-330` 标了「异常路径的隐患」）。
- 与我作用域的关系：`client/gui/**` **没有任何** `ItemPreviewRenderContext` 消费者（全仓 grep 已核），
  GUI 侧无需配合修。


### P3（只记录）

| # | 文件:行 | 说明 |
|---|---|---|
| P3-1 | `GunPackList.java:235-257`（`GuiSlot#drawSlot`） | 列表**没有 scissor**：滚动到中间时首/末行只有部分可见，而 1.7.10 `GuiSlot` 仅靠 `overlayBackground` 渐变遮挡，行内容（复选框/搜索框）会溢出到面板边界外。上游 `ContainerObjectSelectionList` 有 `enableScissor` |
| P3-2 | `GunPackList.java:270-274` | 滚轮依赖 `GuiSlot#drawScreen` 里的 `Mouse.next()` 轮询（`GuiSlot.java:312-329`）。1.7.10 的 `GuiScreen#handleInput` 会先抽干事件队列，而 `runTick`(20Hz) 慢于渲染(60Hz)，所以只有约 2/3 的帧能收到滚轮 → 滚动响应**不稳定**（上游是标准 `mouseScrolled` 回调）。scrollbar 可拖拽，功能不至于不可用。未实机 |
| P3-3 | `GunPackList.java:284-374`（`Checkbox`） | 命中区只有 10×10 方框，`x+24` 起绘制的**标签文字不可点**（与上游 `AbstractButton` 10×10 一致，属保真）|
| P3-4 | `GunSmithTableScreen.java:774-800` | `mouseScrolled` 的命中带是 `guiTop+66 .. +66+85`，而 6 行配方列表实际占到 `+66+5*17+16 = +167` → **最后一行上的滚轮无效**（上游同一组常量，属保真）|
| P3-5 | `FlatColorButton.java:64-70`、`:104` | `setSelect/isSelected` **无调用者**（上游 `setSelect` 也是死代码）；另外上游 `renderWidget` 的 `isSelect` 两个分支**颜色完全相同**（都是 `0xAF222222`），本移植改成 `0xAF3A3A3A` 是**偏离上游**的小改动（当前无调用者，无实际影响）|
| P3-6 | `client/init/ClientSetupEvent.java:420-427`（**非本作用域**） | `onRenderOverlay` 在 `mc.currentScreen != null` 时直接 return。上游 `IGuiOverlay` 是在 `Gui#render` 里无条件渲染（被打开的屏幕盖住而已）。当前行为「开 GUI 时隐藏 HUD」更符合直觉，但这个分支也会**连带隐藏 `aboveCrosshair` 的两层**；另外首帧 `mc.gameSettings.hideGUI` 与 GUI scale 处理正确（`ScaledResolution`，`EventRenderer.renderGameOverlay` 的 `post(ALL)` 在 `GuiIngameForge.java:179`、`setupOverlayRendering` 已按缩放建正交投影）|
| P3-7 | `CreativePackFilters.java:665-677` + `CreativeModeInventoryScreenMixin.java:243-252` | 侧栏选项卡的悬停包名在 `drawScreen` **TAIL** 绘制，晚于 `GuiContainer` 末尾的槽位 tooltip（`GuiContainer.java:183-187`）→ 若两者重叠，包名会盖在物品 tooltip 上（上游 `Tooltip.create` 在工具提示层，方向相反）。包名位置在 `guiLeft+6` 附近，与槽位 tooltip 重叠概率低；未修（要修得把自绘层前移到槽位 tooltip 之前，需动 mixin）|

---

## 三、控件坐标 / 命中区对照表

> 坐标语义校准（**本轮最关键的坐标系结论**，逐条核过 1.7.10 源码）：
> - `GuiScreen#drawScreen`（`GuiScreen.java:57-70`）在**无 translate** 状态下画 `buttonList` → `GuiButton#xPosition/yPosition`
>   是**屏幕绝对坐标**，必须自己加 `guiLeft/guiTop`；等价 1.20 `AbstractContainerScreen#render` 里 `super.render(...)` 也在 translate 之前。
> - `GuiContainer#drawGuiContainerBackgroundLayer`（`GuiContainer.java:93`）在**无 translate** 下调用 → 绝对坐标
>   （等价 1.20 `renderBg`，vanilla 自己也写 `k = guiLeft; l = guiTop`）。
> - `GuiContainer#drawGuiContainerForegroundLayer`（`:134`）在 `glPushMatrix + glTranslatef(guiLeft, guiTop)`
>   **之内**调用（`:100-101` push、`:181` pop）→ **相对坐标**（等价 1.20 `renderLabels`）。
> - `GuiContainer#func_146978_c(x,y,w,h,mx,my)`（`:660-667`）内部**已减** `guiLeft/guiTop` → 传绝对鼠标坐标即可（等价 1.20 `isHovering`）。

| 控件 | 文件:行 | 绘制矩形 | 命中矩形 | 结论 |
|---|---|---|---|---|
| `FlatColorButton`（属性图 显示/隐藏、变体、适配器） | `FlatColorButton.java:93-149`；布局 `GunRefitScreen.java:159/180/493/508/544/571`、`GunSmithTableScreen.java:482/527` | `(x, y, w, h)` 绝对 | `(x, y, w, h)`（`GuiButton#mousePressed`） | ✅ 一致（hover 判定同矩形）；文字截断问题见 F5 |
| `RefitTurnPageButton` | `RefitTurnPageButton.java:30-66`；布局 `GunRefitScreen.java:314/320` | 悬停 `(x, y, 18×8)` 整图取样；非悬停 `(x+1, y+1, 16×6)` 内缩 | `(x, y, 18, 8)` | ✅ 一致（内缩 1px 与上游逐字节相同） |
| `RefitUnloadButton` | `RefitUnloadButton.java:28-51`；布局 `:409` | `(x, y, 8×8)`，悬停换 `u=0` / 非悬停 `u=80` | `(x, y, 8, 8)` | ✅ 一致 |
| `ResultButton` | `ResultButton.java:34-88`；布局 `GunSmithTableScreen.java:639` | 选中/悬停 4 态，悬停时 `(x-1, y-1, 96×18)` 外扩 1px | `(x, y, 94, 16)` | ✅ 一致（外扩 1px 与上游同） |
| `TypeButton` | `TypeButton.java:33-53`；布局 `GunSmithTableScreen.java:666` | `(x, y, 24×25)`，`v=204`(+25 悬停) | `(x, y, 24, 25)` | ✅ 一致；悬停 tooltip 已补（§一 F8） |
| `SmithImageButton`（合成/URL/缩放/翻页） | `GunSmithTableScreen.java:1196-1240`；布局 `:544/580/687/693/708/714/725/736/747` | `(x, y, w, h)`，`u,v`(+hoverVOffset) | `(x, y, w, h)` | ✅ 一致；`craftButton.enabled=false` 时不可点、不加灰（与上游 `active` 同） |
| `GunAttachmentSlot` | `GunAttachmentSlot.java:58-136`；布局 `:373` | `(x, y, 18×18)`；非悬停 `(x+1,y+1,16×16)` | `(x, y, 18, 18)` | ✅ 一致 |
| `InventoryAttachmentSlot` | `InventoryAttachmentSlot.java:60-84`；布局 `:285/299` | 同上 18×18 | `(x, y, 18, 18)` | ✅ 一致 |
| `LaserColorSlider`（HSV 拖拽） | `HSVSliderGroup.java:137-172`；布局 `:358/441` | 轨道 `(x, y, 120×16)`；旋钮 `[knobX-4, knobX+4]`，`knobX = x+4+v*(120-8)` | `(x, y, 120, 16)`；拖拽期间越界仍生效（`dragging` 标志，同 `GuiOptionSlider`） | ✅ 一致（拖拽值 clamp 0~1） |
| `GunPackList.Checkbox` | `GunPackList.java:338-373`；行布局 `:235-257` | 方框 `(x, y, 10×10)`，标签 `x+24` | `(x, y, 10, 10)`（`elementClicked` 传原始鼠标坐标） | ✅ 一致（标签不可点 = 上游同）；未加 scissor → P3-1 |
| `GunPackList` 搜索框 | `GuiTextField x=x, y=行y, 94×10`（`:238-240`） | 同上 | 同上（`field.mouseClicked`） | ✅ 一致 |
| `PackFilterButton`（创造侧栏） | `CreativePackFilters.java:610,647-666` | `(x, y, 28×26)`（整张 28×32 sprite 纵向压入） | `(x, y, 32, 26)` | ✅ 绘制矩形落在命中矩形内、垂直方向完全一致；右侧 4px 是透明余量（F7 已修） |
| `ActionButton`（▲/▼ 翻包） | `CreativePackFilters.java:210-225`（无 `drawButton` → 走 `GuiButton#drawButton`） | `(x, y, 20×12)` 原版 widgets.png | `(x, y, 20, 12)` | ✅ 一致 |
| `GunPackProgressScreen` 后台下载按钮 | `GunPackProgressScreen.java:47-55` | `((w-200)/2, 120, 200×20)` | 同 | ✅ 一致 |
| `ClothConfigScreen` 下载/返回 | `ClothConfigScreen.java:85-114` | `((w-200)/2, h/2-15 / h/2+50, 200×20)` | 同 | ✅ 一致 |

**逐个 Screen 的 guiLeft/guiTop 偏移核对（无漏加、无重复加）**：

| Screen | 结论 | 依据 |
|---|---|---|
| `GunRefitScreen`（`GuiScreen`，非容器） | ✅ 全部绝对坐标，与上游 `Screen` 语义相同（`width/height` 都是逻辑分辨率） | `:196-215` 覆写 `drawScreen` 后 `super` 画按钮；`HSVSliderGroup` 用 `width-140/height-64`；无 guiLeft 概念 |
| `GunSmithTableScreen`（`GuiContainer`） | ✅ 背景 `drawGuiContainerBackgroundLayer` 用 `guiLeft+` 绝对；`drawGuiContainerForegroundLayer` **空实现**；其余文字/图标在 `drawScreen`（`super` 之后、矩阵已 pop）用绝对 → 与上游 `renderBg` / `renderLabels`(空) / `render`(绝对) 一一对应 | `:1172-1182`（bg 绝对）、`:1169-1170`（fg 空）、`:809-877`（文字绝对） |
| `AmmoStorageScreen`（`GuiContainer`） | ✅ 同上；tooltip 在 `drawScreen` 末尾（矩阵已恢复）；`func_146978_c` 内部减 guiLeft → 传绝对鼠标正确 | `:53-97`、`:99-103`、`:192-201` |
| `CreativePackFilters`（寄生在 `GuiContainerCreative`） | ✅ 用 `guiLeft/guiTop` 算绝对坐标，按钮由 mixin 在 `drawScreen` TAIL 自绘（绝对） | `:571-578`、mixin `:243-252`；注意 `guiLeft` 是**反射按 MCP 名**取的，生产期失败 → 回退 `(width-195)/2 / (height-136)/2`，与 `GuiContainerCreative`(xSize=195,ySize=136) 的真实值**数值相同**（P3 说明，无实际影响） |

**5 个 overlay 的接线核对**：`ClientSetupEvent.java:389-450`。`registerAboveAll` → `Post(ElementType.ALL)`
（`GuiIngameForge.java:179` 在 `renderGameOverlay` **最末尾**投递，此时 GL 仍是 `setupOverlayRendering()`
建的正交投影：`0..scaledWidth / scaledHeight..0`）→ 用 `event.resolution.getScaledWidth/Height` 作
`width/height` 传入 ✅；`registerAbove(CROSSHAIR)` → `Post(CROSSHAIRS)`（`:227`）✅；
F1（`hideGUI`）与 GUI scale 都由 `ScaledResolution` + `gameSettings.hideGUI` 正确覆盖 ✅。

---

## 四、贴图尺寸核对表（PNG IHDR 实测）

| 贴图 | 实际像素 | 使用它的布局常量 | 结论 |
|---|---|---|---|
| `tacz:textures/gui/refit_slot.png` | **18 × 18** | `SLOT_SIZE = 18`（`GunRefitScreen.java:96`）；非悬停 `blit(x+1,y+1, 1,1, w-2,h-2, 18,18)` | ✅ 匹配 |
| `tacz:textures/gui/refit_slot_icons.png` | **224 × 32** | `ICON_UV_SIZE=32`、`getSlotsTextureWidth()=32*7=224`（`:95-147`）| ✅ 7 个 32px 图标正好铺满 |
| `tacz:textures/gui/refit_turn_page.png` | **180 × 160** | 取样 `180×80`（上半/下半）→ 画 18×8；非悬停取样 `(10,±10, 160×60)` → 画 16×6 | ✅ 匹配（与上游同参数） |
| `tacz:textures/gui/refit_unload.png` | **160 × 80** | `u∈{0,80}`，取样 `80×80` → 画 8×8 | ✅ 匹配 |
| `tacz:textures/gui/gun_smith_table.png` | **256 × 256** | 面板 `208×160`；`ResultButton` u=52/53、v=211/212/229/230、94×16；`TypeButton` u=0/26、v=204(+25)、24×25；翻页 `96×6`、`18×20`、`10×10`；`u,v` 最大 `222/230` | ✅ 全部落在 256 内 |
| `tacz:textures/gui/gun_smith_table_side.png` | **256 × 256** | `blit(guiLeft, guiTop, 0,0, 134, 187)`；`ySize=186` → 底部溢出 1px（上游同参数） | ⚠ 与上游一致（`ySize+1=187` 是上游的刻意行为） |
| `tacz:textures/gui/creative_pack_all.png` | **64 × 64** | `blit(..., 0,0, 16,16, 16,16)`（整图 UV 铺满 16×16） | ✅ 匹配 |
| `minecraft:textures/gui/container/creative_inventory/tabs.png` | **256 × 256** | 取样 `u=0, v=64/96`（源 28×32）→ 画进 28×**26**（纵向压缩，F7）；按钮高 `TAB_HEIGHT=26`、间距 `TAB_SPACING=29` | ✅ 绘制矩形 == 命中矩形、相邻不再重叠。u/v 取样值**与 vanilla 完全一致**：`GuiContainerCreative.java:927`(u=i*28)、`:933`(选中 +32)、`:953`(第二排 +64)、`:960`(`drawTexturedModalRect(l, i1, j, k, 28, 32)`) → **03-client-ui 那轮的 F1（换掉不存在的 `recipe_book.png`）结论成立** |
| `tacz:textures/hud/{fire_mode_semi,auto,burst}.png` | **128 × 128** | `blit(..., 0,0, 10,10, 10,10)`（整图 UV 铺满 10×10） | ✅ 匹配 |
| `tacz:textures/hud/heat_base.png` | **128 × 128** | `blit(w/2-64, h/2-44, 0,0, 128,128, 128,128)` | ✅ 匹配（`heat_bar.png` 128×128 与上游一样声明但未使用） |
| `minecraft:textures/gui/container/generic_54.png` | **256 × 256** | `AmmoStorageScreen` 4 段拼合：`176×17` / `176×STATUS_HEIGHT`(v=7) / `176×rows*18`(v=17) / `176×96`(v=126) | ✅ 匹配（与上游同参数） |

**「误用 1.13+/Forge 专属贴图」复核**：1.7.10 原版 `assets/minecraft/textures/gui/` 实测只有
`book / demo_background / icons / options_background / resource_packs / stream_indicator / widgets.png`
（+ `container/**`、`achievement/**`、`title/**`），**确实没有 `recipe_book.png` / `checkbox.png`**。
本作用域全部 12 处 `textures/*.png` 引用已逐个 `Test-Path`：`recipe_book.png` / `checkbox.png`
只出现在**解释修复原因的 javadoc 注释**里（`CreativePackFilters.java:154`、`GunPackList.java:291`），
**代码中已无引用** → 03-client-ui 的 F1/F2 结论**经复核成立**。

---

## 五、复查清单

**已修，需在下次构建后回归（未跑 gradle，静态自查 0 error）**

1. 创造模式物品栏：进创造 → 切到「建筑方块」等页签 → **滚轮应能滚动**、右侧滚动条不弹回顶部（F1）；
   左侧侧栏 4 个选项卡应显示枪包 logo（不是紫黑块）、**上下边缘也能点到**、相邻不再重叠（F7）；
   点击后列表只列该包物品，再点「全部」恢复。
2. 枪械 tooltip（F6 + F2）：把鼠标放在枪上应出现整套属性（见 §六表格第 1 行），描述**分多行、无 `\n` 字样**
   （用 `tacz:timeless50` 验，其 `.desc` 含字面 `\n`）。
   ⚠ 先决条件：枪包 `.lang` 已注入（`PORT_STATUS.md:39` 的 `LanguageRegistry` 链）；
   未注入时描述会显示成 key 本身——那是另一条链的问题，不是本轮的。
3. 配件 tooltip（F6 + F3）：任意 `ammo_mod_*` / `scope_xm157` 应出现多行描述（无 `\n`）；
   **按住 Shift** 应换成「受支持枪械」一行列表（原先这里什么都看不到）。
4. 弹药 / 弹药盒 / 弹药库 / 工作台方块 tooltip（F6）：按 §六表格逐行核对；弹药库需先用过弹药库方块
   （挖掉后掉落物带 `BlockEntityTag`）才会出现条目块。
   **弹药配方请去弹药工作台（`tacz:workbench_a`）做**——`pd_cartridges` 等分组只在弹药工作台的页签里，
   默认枪械工作台看不到它们是上游设计（见 §七 7.3）。
5. **工作台配方列表（F11）**：现在有 199 条真实配方（i18n-icon r2-07b）。重点回归
   ① 材料计数是否正确；② 合成按钮点击后产物进入背包 / 掉落、材料数刷新；
   ③ **在筛选框里连续输入文字不再崩**（这条原先会 NPE，见 §七 7.6）；
   ④ 悬停材料格的道具 tooltip。
6. 工作台：手持物品筛选后配方全空 → 空态提示应是**两行**「没有匹配配方 / 手持物品筛选已开启」，无 `\n`（F2）。
7. 工作台左上角 9×9 按钮上应能看到字母 **F**（F5）；左侧**分类页签悬停应显示分类名**（F8）。
8. 展示柜上锁提示应约 **2 秒**后自动消失（连续上锁会重新计时）（F4）。
9. 阴影（F9）：连杀数、HUD 版本号（`1.7.10-<ver>`）、工台复选框标签、配件 Shift 提示
   这几处「只给颜色」的文字现在**有阴影**（与 1.20 一致）；若实机觉得某处不该有，改回显式传 `false` 即可，
   不要改默认值。
10. **第三方枪包 null 字段（F10）**：可造一个测试包，把 `gunpack_info.json` 改成
    `{"name": null, "authors": null, "license": null, "version": null, "date": null}`，
    回归 ① 工作台能正常打开（`GunPackList` 构造不再崩）；② 选中配方右侧信息栏不崩、`v`/License/Date 行
    显示空值而不是 `null`；③ 该包枪械/配件/弹药/方块的 tooltip **仍完整显示**（枪包名行回落成 namespace）。

**遗留 / 待其他作用域**

11. **P2-N5（需 Lead 修）**：`client/renderer/block/{GunPegboardRenderer,GunDisplayCaseRenderer}` 的
    `ItemPreviewRenderContext.setAlpha` 复位不在 `try/finally` 里 → 异常会让**静态 alpha 永久停在预览值**，
    之后所有物品渲染走 `entityTranslucent`。GUI 侧无消费者，我无需配合。
12. `GunPackList`（`GuiSlot`）的滚轮稳定性与缺 scissor（P3-1/P3-2），需实机。
13. 工作台配方列表最后一行与滚轮命中带的关系（P3-4，上游同构）、侧栏包名盖住槽位 tooltip（P3-7）。
14. 若日后补 `AmmoItem#addInformation`，记得删掉 `TooltipEvent#ammoHoverText` 那一段，避免重复行。

---

## 六、tooltip 接线对照表（**实机回归清单**，批 2 / F6）

> 插入位置：`ItemTooltipEvent.toolTip` 的 **index 1**（紧跟第 0 行物品名），对应 1.20
> `GuiGraphics#gatherTooltipComponents` 的 `list.add(1, <imageComponent>)`。
> 行序即下表顺序；「降级」列写明 1.7.10 表达不了的部分。

| 物品（判定条件） | 上游来源 | 1.7.10 渲染类 | 会出现哪些行（含颜色） | 降级 |
|---|---|---|---|---|
| **枪械** `item instanceof IGun`（`ModernKineticGunItem`） | `api/item/gun/AbstractGunItem.java:398-411 getTooltipImage()` | `ClientGunTooltip` | ① 描述 N 行（GRAY，按 300px 折行、最多 3 行，字面 `\n` 已切分）② 弹药名 ③ 弹药数（`当前/上限`、百分比模式、或黄字「背包直读」）④ 等级（`Lv N (xx%)` 或 `(MAX)`，DARK_PURPLE/YELLOW）⑤ 枪械类型（AQUA）⑥ 伤害（AQUA，含 `+ 爆炸` 后缀）⑦ 穿甲% ⑧ 爆头倍率 ⑨ 移速惩罚（RED）⑩「按 Z 改装」提示（YELLOW+ITALIC）⑪ 枪包名（BLUE+ITALIC） | ②③⑦⑧ 上游用绘制色（gold / gray），这里是默认白 |
| **配件** `item instanceof IAttachment`（`AttachmentItem`） | `item/AttachmentItem.java:99-101 getTooltipImage()` | `ClientAttachmentItemTooltip` | ① 描述 N 行（GRAY，字面 `\n` 已切分）② 修饰符行（后坐 / 消焰增·减 / 消烟增·减 / 火控，AQUA·GREEN·RED）③ 激光颜色行（近似到 16 色）④ 未按 Shift 时是 `tooltip.tacz.attachment.yaw.shift` 提示；按住 Shift 时换成 `yaw.support` + 一行逗号分隔的「受支持枪械」名 ⑤ 枪包名（BLUE+ITALIC） | Shift 态上游画 16 列 × 16px 枪械图标网格，这里折算成一行文字（超 16 把标注 `…… (+N)`）；激光真彩色退化到最近 16 色 |
| **弹药** `item instanceof IAmmo`（`AmmoItem`） | `item/AmmoItem.java:91-110 appendHoverText()`（**纯文本**，不是 tooltip 组件） | 本类 `TooltipEvent#ammoHoverText` | ① 弹药描述 N 行（GRAY，字面 `\n` 已切分）② 枪包名（BLUE+ITALIC） | 无 |
| **弹药盒** `item instanceof IAmmoBox`（`AmmoBoxItem`） | `item/AmmoBoxItem.java:259-274 getTooltipImage()` | `ClientAmmoBoxTooltip` | ① 内部弹药名 ② 数量（创造模式为 `∞`）。**条件**：`ammoId != tacz:empty` 且 `count > 0`，否则整块不出现（与上游同） | 上游文字左侧有 16×16 弹药图标（`renderImage`），这里没有 → 只保留文字（弹药名已在第 ① 行） |
| **弹药盒用法提示**（同上） | `item/AmmoBoxItem.java:277-289 appendHoverText()` | 已由 `item/AmmoBoxItem.java:202-218 addInformation` 提供（本轮未动） | 创造 / 全体创造：`usage.all_type_creative`（GOLD）或 `usage.creative.1/2`（YELLOW）；普通：`usage.deposit` / `usage.remove`（GRAY） | 无（行序上用法提示在富块**之后**，上游也是 appendHoverText 在组件之后） |
| **弹药库** `item instanceof AmmoStorageBlockItem` | `item/AmmoStorageBlockItem.java:57-85 getTooltipImage()` | `ClientAmmoStorageTooltip` | 每条目 2 行：弹药名 / `tooltip.tacz.ammo_box.count`（最多 5 条，按同物品同 NBT 合并计数）；末尾若有剩余：`and N more...`（ITALIC）。**条件**：物品 NBT `BlockEntityTag.Entries` 非空，否则整块不出现 | 上游每条目左侧 16×16 图标（`renderImage`）→ 无 |
| **弹药库说明**（同上） | `item/AmmoStorageBlockItem.java:51-55 appendHoverText()` | 已由 `item/AmmoStorageBlockItem.java:58-63 addInformation` 提供（本轮未动） | `tooltip.tacz.ammo_storage.description` / `.description.extra`（GRAY） | 无 |
| **其它方块物品** `item instanceof BlockItemDataAccessor`（本工程只有 `GunSmithTableItem`） | `item/GunSmithTableItem.java:92-96 getTooltipImage()` | `ClientBlockItemTooltip` | ① 方块描述（GRAY，字面 `\n` 已切分；`tacz.block.gun_smith_table.desc` = "Where it all began"）② 枪包名（BLUE+ITALIC）。**条件**：`BlockId != tacz:empty` | 无 |

**共同行为**：

- 全程 `try/catch (Throwable)` + 空索引 / `null` gunId / 索引未加载 → 静默跳过（tooltip 每帧重算，绝不外抛）。
- 高级提示（F3+H）且 `ENABLE_TACZ_ID_IN_TOOLTIP` 为真时，仍照旧在**末尾**追加一行 `§8<key>: "<id>"`
  （上游 `TooltipEvent` 原逻辑，未改）。
- 依赖前一轮的 `\n` 修复：`ClientGunTooltip` 描述、`ClientAttachmentItemTooltip` / `ClientBlockItemTooltip`
  描述、本轮新增的弹药描述（`TooltipEvent#ammoHoverText`）都按字面 `split("\\\\n", …)` 切行。
- 若日后有人把 `AmmoItem#addInformation` 补上（会与 `ammoHoverText` 重复），删掉 `TooltipEvent` 里那一段即可。

---

## 七、跨包复核（i18n-icon r2-07 / r2-07b 提报事项）

> 三条提报 + 一个「分栏风险」，我逐条回代码核过。**结论：无一条构成本作用域缺陷**，
> 但其中两条会改变别人写 tooltip 时的假设，另外发现「配方数据上线」后
> `GunSmithTableScreen` 有 5 条此前不可达、现在每帧都会跑的路径需要空安全确认（已全部核过，补了 1 处防御）。

### 7.1 物品名不落 `super`（`AmmoBoxItem`）—— 与本作用域无耦合

- i18n-icon 结论（已核）：`item/AmmoBoxItem.java:133-148` 的 `getItemStackDisplayName` 四个分支
  **全部 return**，永不调 `super.getItemStackDisplayName`；显示的键恒为
  `item.tacz.ammo_box.{iron|gold|diamond|creative|all_type_creative}`。
- 对我的影响：**无**。`ClientAmmoBoxTooltip` 的第 ① 行是**内部弹药**的名字
  （`AmmoItemBuilder…build()` 出的弹药栈 → `ItemStack#getDisplayName`），不是弹药盒自己的名字；
  本轮接线也**不**触碰 tooltip 首行（index 0 仍是原版 `Item#getItemStackDisplayName`）。
- 保留提醒：日后任何「tooltip 首行」相关改动都**不要**假设 `super` 会兜底。

### 7.2 方块名改用 `tile.tacz.<name>.name`（r2-07）—— 我的接线不拼名字键

- 本轮 `ClientBlockItemTooltip` 只吃 `blockId`（`BlockItemDataAccessor#getBlockId`）去取
  **描述**（`tacz.block.<x>.desc`，见 §六最后一行）与枪包名，**不**自行拼任何名字键，
  所以 r2-07 的改名与本轮接线无耦合、也不会被我的代码绕过。
- 名字键与描述键是两套（`tile.tacz.gun_smith_table.name` vs `tacz.block.gun_smith_table.desc`），
  不冲突（两者在 20 个语种里都存在，已由 i18n-icon 补齐）。

### 7.3 「`pd_cartridges` 分栏不可见」风险 —— **核实后不是缺陷**

- i18n-icon 的担心：`GunSmithTableScreen:195-200` 用 `recipe.getResult().getGroup()` 匹配 block index 的
  `tabs`，而 `ammo/*.json` 写的是 `"group": "pd_cartridges"`（→ `tacz:pd_cartridges`，命名空间补全见
  `crafting/result/GunSmithTableResult.java:113-118`），`TabConfig.DEFAULT_TABS` 里没有它。
- **核实结果（两点都不成立）**：
  1. `data/tacz/data/blocks/ammo_workbench.json`（`index` 里 `id` = **`tacz:workbench_a`**，即弹药工作台）
     的 `tabs` 第 2 项**就是** `tacz:pd_cartridges`（同表还有 `tacz:ifp_rifle_cartridges` /
     `tacz:lc_specialized` / `tacz:explosives` / `tacz:shotgun_shells` / `tacz:alternative_proj`）。
  2. `TabConfig.DEFAULT_TABS`（`resource/pojo/data/block/TabConfig.java`）**不是**「只有 `tacz:ammo`」——
     它有 **16** 个页签：ammo / pistol / sniper / dmr / rifle / shotgun / smg / rpg / mg /
     scope / muzzle / stock / grip / extended_mag / laser / misc。所以即使
     `SyncConfig.ENABLE_TABLE_FILTER=false`（`SyncConfig.java:171`，默认 **true**）回落到默认表，也不会丢页签。
- **真正的语义（= 上游设计，不是 bug）**：页签按**方块类型**分，`group` 只在该方块自己的 `tabs` 里匹配
  （`classifyRecipes` 的 `if (recipeKeys.containsKey(groupName))` 会丢弃不在本方块页签里的配方）。
  默认枪械工作台（`gun_smith_table.json`，tabs = pistol…misc，**无 ammo 类页签**）本来就看不到弹药配方；
  `9mm` 等 `pd_cartridges` 配方要到**弹药工作台**（`tacz:workbench_a`）的 `pd_cartridges` 页签做。
- **给实机回归的指令**：验证弹药配方请用弹药工作台；在默认枪械工作台上看不到它们是预期行为。

### 7.4 配方数据上线后，「此前不可达、现在每帧跑」的路径空安全复核（5 项）

`selectedRecipe` 从此非 null，`GunSmithTableScreen` 里以下路径首次可达（1.7.10 空槽 = `null`，必须逐个确认）：

| # | 路径 | 结论 |
|---|---|---|
| 1 | `getPlayerIngredientCount` 遍历 `inventory.mainInventory`（36 格，大量 null）→ `ingredient.test(stack)` | ✅ 安全：`crafting/GunSmithTableIngredient#test(@Nullable)` 开头就 `stack == null \|\| stack.getItem() == null → false` |
| 2 | `renderIngredient` → `getMatchingStacks()` 可能为空（矿词无候选且代表物品为 null） | ✅ 安全：`items.isEmpty() → continue` |
| 3 | `renderIngredientTooltip` → `items.get(idx)` | ✅ 安全：`items.isEmpty() → return` + `item != null && item.getItem() != null` |
| 4 | `drawScreen` 的 `recipe.getResult().getResult().stackSize`（`RawGunTableResult` 可能解析失败） | ✅ 安全：`resultStack == null ? 0 : resultStack.stackSize` |
| 5 | `renderPackInfo` → `packInfo.getAuthors()` 后直接 `!authors.isEmpty()` | ⚠ **本轮补了防御**：`PackInfo` 字段默认非 null，但 `gunpack_info.json` 显式写 `"authors": null` 会被 gson 覆盖成 null，而该方法选中配方后**每帧**都跑 → 改为 `authors != null && !authors.isEmpty()`（`GunSmithTableScreen:952-956`） |

顺带核过（非本作用域，仅确认不阻塞 GUI）：
- **合成按钮的 windowId 链路没问题**：客户端的 `menu().windowId` 由 FML `OpenGuiHandler.java:17`
  （`player.openContainer.windowId = msg.windowId`）在 `displayGuiScreen` 之后写入，
  所以 `ClientMessageCraft(recipeId, windowId)` 能过服务端 `ClientMessageCraft.java:59` 的
  `openContainer.windowId == message.menuId` 校验，且 `ServerMessageCraft` 回包能被
  `ClientProxy.java:402-405` 认领 → 合成后材料数会刷新。
- `crafting/GunSmithTableRecipe.getAll()` / `getById()` 已被 `classifyRecipes` / `getSelectedRecipe` 使用，
  `getOutput()` 现在非 null。

---

### 7.5 `PackInfo` 全字段自查（i18n-icon 转派；已修，归口 `PackInfoText`）

i18n-icon 在 r2-07b §6.5 指出 `PackInfo` 属 `client/resource/pojo/**`（其作用域外），要我自查其余字段的消费点。
**机制**（i18n-icon 实测）：gson 读到 `JsonToken.NULL` 会 `field.set(obj, null)`，**覆盖**字段初始化值；
只有「键不存在」时默认值才生效。内置包 `custom/tacz_default_gun` 全部 json 扫 `:\s*null` = 0 命中，
所以只有**第三方枪包**显式写 null 才会触发。

关键前提（我实读 1.7.10 源码确认）：`new ChatComponentTranslation(null)` 本身不报错，但
`getFormattedText()` → `initializeFromFormat(null)` → `Pattern#matcher(null)` 会抛 **NPE**
（`net/minecraft/util/ChatComponentTranslation.java:73-94`）。所以「名字」类字段是**崩**，其余是**显示垃圾**。

| 字段 | 消费点（本作用域） | 原行为 | 结论 |
|---|---|---|---|
| `name` | `GunPackList:103`（**构造器内**立即 `getFormattedText()`） | **NPE** → `GunSmithTableScreen.initGui` 抛异常 → 界面开不出来 | ✅ 已修 |
| `name` | `GunSmithTableScreen#renderPackInfo`（选中配方后**每帧**） | **NPE 每帧** → 工作台崩溃 | ✅ 已修 |
| `name` | `ClientGunTooltip:349` / `ClientAttachmentItemTooltip:101` / `ClientBlockItemTooltip:68`（tooltip ctor） | NPE 被 F6 的 `catch(Throwable)` 兜住 → **整套富 tooltip 静默消失** | ✅ 已修 |
| `name` | `client/event/TooltipEvent#ammoHoverText` | 同上（弹药 tooltip 消失） | ✅ 已修 |
| `name` | `CreativePackFilters:275-283` | 已自带判空（含 `custom.tacz.error.no_name` 占位符判断） | ✅ 原就安全，无需改 |
| `authors` | `GunSmithTableScreen#renderPackInfo`（每帧） | `!authors.isEmpty()` → **NPE 每帧** | ✅ 已修（前一批） |
| `description` | 同上，`StringUtils.isNoneBlank(descKey)` | null-safe（`isBlank(null)=true` → 跳过整段） | ✅ 原就安全 |
| `license` | 同上，`+ getLicense()` 字符串拼接 | 显示字面 **`null`** | ✅ 已修（`safe()` → 空串） |
| `version` | 同上，`"v" + getVersion()` | 显示 **`vnull`** | ✅ 已修 |
| `date` | 同上，`+ getDate()` | 显示字面 **`null`** | ✅ 已修 |
| `url` | `openUrl(packInfo.getUrl())` → `StringUtils.isBlank(null)=true` | 静默跳过按钮动作 | ✅ 原就安全 |
| `logo` | `PackInfo#getLogo(namespace)` 自身判 `null/isBlank` → 返回 null；`CreativePackFilters` 再按 `resourceExists` 兜底 | 无异常 | ✅ 原就安全 |

**修法**：新增 [`client/tooltip/PackInfoText.java`](src/main/java/com/tacz/guns/client/tooltip/PackInfoText.java)
（`name(PackInfo, namespace)` → 恒返回可安全渲染的组件：name 缺失/空白/等于占位键 → 用 namespace →
再没有才用 `custom.tacz.error.no_name`（该键 20 个语种都有，`assets/tacz/lang/en_US.lang:18`）；
`safe(String)` → null 归一成空串）。回落顺序与 `CreativePackFilters` 既有处理一致。
**6 个调用点全部改用它**，不再各写一份判空。

### 7.6 顺带修掉一处「打字即崩」：`isNameMatch` 的 result 判空（P2）

IDE 的 `可能产生 NullPointerException` 警告把我带到 `GunSmithTableScreen#isNameMatch`
（原 `:317-320`）：`recipe.getResult().getResult().getDisplayName()` **两层都没判空**。

- **可达性**：`GunSmithTableRecipe` 的 `result`（`GunSmithTableResult`）与 `result.getResult()`（{@code ItemStack}）
  都可能为 null——第三方枪包写出无法解析的 `result.id` 时后者就是 null；
  而本方法在**搜索框每输入一个字符**都会跑到（`GunPackList#keyTyped` → `parent.initGui()`）。
  配方数据上线（199 条）之后这条路径首次可达 → **在筛选框里打字就崩**。
- **修法**：判空后退回用 `recipe.getId()` 参与匹配（信息不丢、不崩）。
  同族的另外 3 处（`getRecipeSortKey`、`renderPackInfo`、`drawScreen` 的 `resultStack.stackSize`）原本就有判空，未动。

---

## 八、r2-04c · PackInfo 空安全扫查（收口）

> 触发：i18n-icon r2-07b §6.5 指出 `client/resource/pojo/PackInfo` 的字段可被 gson 的
> `JsonToken.NULL` **覆盖成 null**（实测机制：`field.set(obj, null)` 覆盖初始化值，只有「键不存在」时默认值才生效；
> 2.2.4 与 2.10 一致，不是移植差异；内置包 `custom/tacz_default_gun` 全部 json 扫 `:\s*null` = 0 命中，
> 只有第三方枪包会触发）。
>
> **本轮范围**：只做「本作用域内已经动过的 6 个消费文件 + 1 个新增 helper」。
> `resource/**`（`PackInfo` 本体、是否加载入期归一化）属 i18n-icon / client-resource owner，**未改**，
> 建议下一轮在他们的作用域内决定要不要统一 normalize（见文末）。

### 8.1 关键结论：`name == null` 到底会怎样？（决定它是不是 P0）

**是抛异常，不是显示 `null`，也不是空白。** 实读 1.7.10 源码：

- `new ChatComponentTranslation(null)` 构造本身**不报错**（只把 key 存进字段，`ChatComponentTranslation.java:31-40`）；
- 但任何 `getFormattedText()` / 渲染 都会走 `initializeFromFormat(StatCollector.translateToLocal(null))`
  → `initializeFromFormat(null)` → **`stringVariablePattern.matcher(null)` 抛 `NullPointerException`**
  （`net/minecraft/util/ChatComponentTranslation.java:73-94`）；
- `StatCollector.translateToLocal(null)` 本身不抛（`HashMap#get(null)` 返回 null），所以问题 100% 落在 `matcher(null)`。

因此 `name == null` 的两处消费点是**真 P0 级**后果：
`GunSmithTableScreen#renderPackInfo`（选中配方后**每帧**）与 `GunPackList` ctor（`Checkbox` 构造期立即 `getFormattedText()`）。
其余 4 处（3 个 tooltip ctor + `TooltipEvent#ammoHoverText`）会被 §一 F6 的 `catch(Throwable)` 兜住 →
不崩，但**整套富 tooltip 静默消失**（表现为「装了第三方枪包后枪械 tooltip 全没了」）。

### 8.2 逐字段：null 时的原后果与实际处理

| 字段 | 本作用域消费点 | `null` 时的**原**后果 | 处理 |
|---|---|---|---|
| `name` | `GunPackList:103`、`GunSmithTableScreen#renderPackInfo`、`ClientGunTooltip:349`、`ClientAttachmentItemTooltip:101`、`ClientBlockItemTooltip:68`、`TooltipEvent#ammoHoverText` | **NPE**（见 8.1）。2 处每帧/构造期必崩；4 处富 tooltip 整套消失 | ✅ **改**：6 点统一走 `PackInfoText.name(packInfo, namespace)` |
| `authors` | `GunSmithTableScreen#renderPackInfo`（每帧） | **NPE**（`!authors.isEmpty()`） | ✅ **改**：`authors != null && !authors.isEmpty()`（Lead 已复核在代码里） |
| `license` | 同上（字符串拼接） | 显示字面 **`null`** | ✅ **改**：`PackInfoText.safe(...)` → 空串 |
| `version` | 同上（`"v" + getVersion()`） | 显示 **`vnull`** | ✅ **改**：同上 |
| `date` | 同上（字符串拼接） | 显示字面 **`null`** | ✅ **改**：同上 |
| `description` | 同上，`StringUtils.isNoneBlank(descKey)` | **无后果**（`isNoneBlank(null)` 为 false → 整段跳过） | ⬜ **只登记不改**：原就 null-safe |
| `url` | `GunSmithTableScreen#openUrl` → `StringUtils.isBlank(url)` | **无后果**（blank → 直接 return，按钮点击无动作） | ⬜ **只登记不改**：原就 null-safe |
| `logo` | `CreativePackFilters:275-283` → `PackInfo#getLogo(namespace)` | **无后果**（getter 自判 `null/blank` 返回 null，调用方再按 `resourceExists` 兜底） | ⬜ **只登记不改**：原就 null-safe（`CreativePackFilters` 对 `name` 也已有判空 + 占位符判断） |

**回落顺序**（与 `CreativePackFilters` 既有处理一致）：`name` 缺失 / 空白 / 等于占位键
`custom.tacz.error.no_name` → 用目标物品/方块的 **namespace** → 连 namespace 都没有才用
`custom.tacz.error.no_name`（该键 20 个语种都有，`assets/tacz/lang/en_US.lang:18`）。

### 8.3 本轮改动清单（7 个文件，全部 `client/gui/**` + `client/tooltip/**` + `client/event/**`）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `client/tooltip/PackInfoText.java`（**新增 1 文件**） | `name(PackInfo, namespace)` / `safe(String)` / `isBlank(String)`；类注释记录 gson 覆盖机制与 NPE 机理 |
| 2 | `client/gui/components/GunPackList.java` | 枪包复选框名称改用 `PackInfoText.name(packInfo, namespace)`（消除 ctor 期 NPE） |
| 3 | `client/gui/GunSmithTableScreen.java` | ① `renderPackInfo` 的 name/version/license/date/authors 全部空安全（含 `id` 判空）；② `isNameMatch` 的 `getResult().getResult()` 判空（见 §七 7.6）；③ `TypeButton` 传 `tabConfig.getName()`（§一 F8） |
| 4 | `client/tooltip/ClientGunTooltip.java` | 枪包名行改用 `PackInfoText.name(...)` |
| 5 | `client/tooltip/ClientAttachmentItemTooltip.java` | 同上 |
| 6 | `client/tooltip/ClientBlockItemTooltip.java` | 同上（并移除因此不再使用的 `ChatComponentTranslation` import） |
| 7 | `client/event/TooltipEvent.java` | 弹药描述块的枪包名行改用 `PackInfoText.name(...)`（并移除不再使用的 import） |

自查：这 7 个文件 `get_file_problems(errorsOnly=true)` **全部 `errors: []`**；未运行 gradle。
`lint_files(min_severity=warning)` 的剩余项全是**改动前就有**的风格警告（未使用形参、Guava `@Beta` cache、
`if` 可换 `switch`、`b ->` lambda 形参未用等），无 error、无新增。

### 8.4 只登记、留给下一轮

1. **`PackInfo` 本体是否统一 normalize**（`client/resource/pojo/**`，i18n-icon / client-resource owner 作用域）：
   与其在 6 个消费点判空，更彻底的做法是在载入期（`ClientAssetsManager` / gson `JsonDeserializer`）把
   `null` 归一成各自的默认值，这样连未来新增的消费点也自动安全。**未改，避免跨作用域冲突。**
2. `CreativePackFilters`（侧栏包名/占位符判断）**原就 null-safe**，本轮未动。
3. 第三方枪包若把 `name` 写成**空串**（而非 null），现有回落会退到 namespace —— 与上游「显示空名字」不同，
   属刻意加固，若实机觉得该显示原样空串再调。
