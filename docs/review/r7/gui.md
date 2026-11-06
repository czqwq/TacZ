# r7-gui · GUI/界面/tooltip 上游等价性审查

任务：task-18。写作用域：`client/gui/**`（**不含** `client/gui/overlay/**`，属 r7-crosshair）、
`client/tooltip/**`、`client/init/ModContainerScreen.java`、本报告。

- 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/{gui,tooltip}/**`、`client/init/ModContainerScreen`、
  上游 1.20.1 `tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/client/**`（Screen/GuiGraphics/AbstractSelectionList/AbstractWidget…）
- 1.7.10 准绳：`build/rfg/minecraft-src/java/net/minecraft/client/**`（GuiScreen/GuiContainer/GuiSlot/GuiButton/GuiTextField/FontRenderer…）
- 纪律：**未运行任何 gradle 任务**；改动文件 `mcp__idea__lint_files(min_severity="error")` = **0 error**；
  未改 `tmp/**` / `build/**` / `run/**` / `src/main/resources/**`；未新增 mixin。

---

## 0. 结论速览

GUI/tooltip 整体与上游**同构**：文件一一对应、本地化 key 集合一致、控件坐标/尺寸/命中区与上游常量逐项相同、
tooltip 行序与上游 `renderText`/`renderImage` 一致、r6 的图标网格（`AttachmentGunGridOverlay`/`TooltipLineUtils`）与
r6 的创造栏图标修复均**未回退**。本轮发现并修复 **11 项移植偏离**（含 3 项功能性真 bug），登记 **9 项有据降级/未修**。

| 级别 | 已修 | 未修/降级（只记录） |
|---|---|---|
| **P1** | 0 | 0 |
| **P2** | 4 | 2（跨模块 1 + 位图降级 1） |
| **P3** | 7 | 7 |

**最关键的 4 条**

1. **P2 真 bug：工作台「过滤面板」整块点不动**。1.7.10 `GuiSlot#drawScreen` 的行点击判定用
   `k1 = this.width/2 - getListWidth()/2`（**漏了 `this.left`**，`GuiSlot.java:243-244`），而本列表用
   `setLeftPos(guiLeft)` 挂在屏幕中部（left≈(width-344)/2，通常 >134）⇒ 绝对鼠标 X 永远落在 `[0,134]` 之外，
   `elementClicked` 永不触发 → 搜索框/复选框/ALL/枪包按钮**全都点不动**（滚轮/滚动条/键盘还能用）。
   1.7.10 原版带 left 偏移的列表（资源包界面）走的是事件路径 `GuiListExtended#func_148179_a`，所以原版没暴露。
   已补事件驱动派发（`func_148124_c` 是正确带 `this.left` 的命中计算），并做同帧轮询去重（§2 F1）。
2. **P2 真偏离：改装界面多补了一层 1.20 不存在的暗色背景**。上游 `GunRefitScreen#render` 只调 `super.render`，
   而 1.20.1 `Screen#render` **只遍历 renderables、不调 `renderBackground`**（`Screen.java:113-118`；
   GUI 由 `GameRenderer.java:1139-1141` 直接渲染，无自动背景层）⇒ 世界与第一人称枪原样透出。
   r4 的 R4-C-3 基于错误前提补了 `fillGradient(0,0,width,height,0xC0101010,0xD0101010)`，会把界面中央
   正在预览的枪体压暗（alpha 0xC0≈75% 黑）。**已删**，恢复上游观感（§2 F2）。
3. **P2 真偏离：右键会误触按钮**（r4-C-6 登记未修的平台差异）。上游 `AbstractWidget#isValidClickButton` 只认左键
   （`AbstractWidget.java:199-201`），1.7.10 `GuiScreen#handleMouseInput` 对任意键都调 `mouseClicked`
   （`GuiScreen.java:329-345`）、`GuiButton#mousePressed` 不判键号（`GuiButton.java:125`）⇒ 右键会装/卸/切变体/切适配器/
   合成。两个**无容器槽位**的界面（改装台、枪械工作台）已按上游只放行左键（§2 F3；弹药库界面保留右键，因为上游那里
   右键有明确语义：`PICKUP_HALF`）。
4. **P3：tooltip 绘制色未折算成文本颜色**（r2-04 登记为降级）。上游 `ClientGunTooltip#renderText` 的行绘制色是
   `0xffaa00`/`0x777777`/`0xffffff`，1.7.10 纯文本 tooltip 完全可以用 `ChatStyle` 表达同一颜色（格式码不占宽度）。
   已把弹药名（GOLD）、弹药数（DARK_GRAY）、等级/类型/伤害基础色（DARK_GRAY）、穿甲/爆头（GOLD）、
   爆炸后缀（AQUA）、builtin 配件行（GOLD）折算成 `ChatStyle`（§2 F8/F9）。

**三条 Lead 转派事项的裁决**（详见 §5）：① 弹药库 tooltip 预览**已有实现**（r2 F6 接线，r7-block 的 P2 不成立，
只缺装饰性 16×16 图标）；② `AmmoBoxItem#overrideStackedOnOther` 应走 **mixin `Container#slotClick`**（GUI 方案会发明
上游不存在的机制），需求已给 r7-item/r7-platform 分工；③ 客户端 `RefitDebug` 残留**已全部删除**（util/RefitDebug 已被
其它 teammate 删除，现全仓 0 引用）。

---

## 1. 逐文件对照表

> 「上游」= `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/...` 同名文件；未特别标注时本行结论为「语义等价」。

### 1.1 `client/gui/`（排除 overlay）

| 本地文件 | 上游文件 | 结论 | 备注 |
|---|---|---|---|
| `GunRefitScreen.java` | 同名 | 等价（修 3 项） | 背景层/右键/调试日志，见 §2 F2/F3/F10 |
| `GunSmithTableScreen.java` | 同名 | 等价（修 2 项） | 过滤列表尺寸参数/右键，见 §2 F3/F4；`renderLeftModel` 2D 降级见 §4 D1 |
| `AmmoStorageScreen.java` | 同名 | 等价（修 1 项） | `renderBg/renderTooltip/mouseClicked/mouseDragged/mouseReleased` 逐段同构；补容器/背包标题，见 §2 F11；右键=上游 PICKUP_HALF 保留 |
| `GunPackProgressScreen.java` | 同名 | 等价 | `IProgressUpdate` 适配是 1.7.10 必需；`drawDefaultBackground` = 上游 `renderBackground` |
| `compat/ClothConfigScreen.java` | 同名 | 有据降级 | 1.7.10 无 `ConfigScreenHandler`/`ModLoadingContext` 扩展点，`registerNoClothConfigPage` 为探测式 no-op（类注释已记录） |
| `components/CreativePackFilters.java` | 同名 | 等价（修 3 项） | 页签识别/悬停 tooltip/▲▼ tooltip，见 §2 F5/F6 |
| `components/FlatColorButton.java` | 同名 | 等价（修 1 项） | 禁用文字色，见 §2 F7 |
| `components/GunPackList.java` | 同名 | 等价（修 3 项） | 行点击真 bug/裁剪/hint，见 §2 F1/F4/F7 |
| `components/creative/*` | 无（移植特有） | 闭合 | r6 引入的 `@Accessor` 桥；`containerAccessProvider` 注入/回退反射链路完整 |
| `components/refit/GunAttachmentSlot.java` | 同名 | 等价 | 悬停名位置（y+20/y+30）、外框/图标 UV、`isAllow` 同上游 |
| `components/refit/InventoryAttachmentSlot.java` | 同名 | 等价 | 18×18 外框、图标、`getItemStack` 空栈语义（null ↔ EMPTY） |
| `components/refit/RefitTurnPageButton.java` | 同名 | 等价 | 18×8、悬停/非悬停 UV 与上游逐字相同 |
| `components/refit/RefitUnloadButton.java` | 同名 | 等价 | 8×8、u=0/80 与上游相同 |
| `components/refit/GunPropertyDiagrams.java` | 同名 | 等价 | 段高/条宽/颜色/文本位置逐项相同（`0xFF55FF55` 等） |
| `components/refit/HSVSliderGroup.java` | 同名 | 等价（有据降级） | 1.7.10 无 `ForgeSlider`，用 `GuiButton` 复刻 0~1/0.01 步进 + 拖动；轨道/旋钮尺寸与上游相同 |
| `components/refit/IComponentTooltip.java` | 同名 | 等价 | 1.20 `getTooltipLines` → `ItemStack#getTooltip` 再包 `ChatComponentText` |
| `components/refit/IStackTooltip.java` | 同名 | 等价 | 空栈用 null，接口注释已说明 |
| `components/smith/ResultButton.java` | 同名 | 等价（修 1 项） | 结果名居中，见 §2 F7 |
| `components/smith/TypeButton.java` | 同名 | 等价 | 24×25、vOffset 204(+25)、图标 x+4/y+5；悬停 tooltip 为 r2 F8 修复（上游 `setTooltip` 等价物） |
| `toast/GunLevelUpToast.java` | 同名 | 有据降级 | 1.7.10 无 toast 系统；上游该类 `render` 本身也是**全注释的空实现**（上游 :29「没有实际使用」）→ 双方都无可见行为，非回归 |

### 1.2 `client/tooltip/`

| 本地文件 | 上游文件 | 结论 | 备注 |
|---|---|---|---|
| `ClientGunTooltip.java` | 同名 | 等价（修 1 项） | 行序/行高/段高/`maxWidth`/折行 300/隐藏 part 与上游相同；绘制色折算见 §2 F8 |
| `ClientAttachmentItemTooltip.java` | 同名 | 等价（修 1 项） | 文本组件/修饰符行/激光色近似/zoom/rangefinder/热成像/弹匣等级逐项相同；Shift 网格 = r6 实现（未回退）；builtin 行色见 §2 F9 |
| `ClientAmmoBoxTooltip.java` | 同名 | 等价 | 28px 高、`max+22` 宽、GOLD/0x666666、图标 x/y 同上游 |
| `ClientAmmoStorageTooltip.java` | 同名 | 等价（有据降级） | 每条目 2 行 + `and N more...`；1.7.10 无 `container.shulkerBox.more` 翻译 → 回落同格式英文（类注释已记录） |
| `ClientBlockItemTooltip.java` | 同名 | 等价 | 描述 GRAY + 枪包名 BLUE/ITALIC；弹药库不走本类（见 §5 A） |
| `AttachmentGunGridOverlay.java` | 无（移植特有，r6） | 保持 | 框定位公式与 `GuiScreen#drawHoveringText` 逐项相同（`GuiScreen.java:156-232`），r6 结论复核仍在 |
| `TooltipLineUtils.java` | 无（移植特有，r6） | 保持 | 行宽上限/折行/撑宽空白，r6 结论复核仍在 |
| `PackInfoText.java` | 无（移植特有，r2） | 保持 | PackInfo null 字段回落（r2 F10 修复复核仍在） |
| `package-info.java` | 同名 | 等价（有据降级） | 1.7.10 无 `@MethodsReturnNonnullByDefault`/`@FieldsAreNonnullByDefault`（1.13+ 原版注解），只留 JSR305 |

### 1.3 `client/init/ModContainerScreen.java`

| 本地 | 上游 | 结论 |
|---|---|---|
| `ModContainerScreen`（零逻辑 no-op） | `MenuScreens.register(GunSmithTableMenu.TYPE, GunSmithTableScreen::new)` + `AMMO_STORAGE_MENU` | **有据等价**：1.7.10 无 `MenuType`/`MenuScreens`，等价注册点是 `inventory/GuiHandler#getClientGuiElement`（已实现 `GunSmithTableScreen`/`AmmoStorageScreen`，`GuiHandler.java:116-138`）。r5 修过的 javadoc 仍正确（不再声称返回 null） |

### 1.4 本地化 key 对账（脚本，逐字）

对「上游 client/gui（去 overlay）+ client/tooltip」与「本地同范围」抽取所有 `"[a-z0-9_.]+"` 形态的 key 并做集合差：

- 上游有、本地无：`narration.checkbox.usage.focused/hovered`（1.7.10 无旁白系统，`GunPackList.Checkbox` 已按降级删 narration）；
- 本地有、上游无：`gui.back`（1.7.10 原版「返回」键，替代上游 `CommonComponents.GUI_BACK`）、
  `gui.tacz.gun_level_up.toast`（`GunLevelUpToast` 的移植聊天回退，该类当前 0 调用者）、
  `me.shedaniel.clothconfig2.ClothConfig`（反射探测字符串，非翻译 key）。
- **其余全部一致**（含 `gui.tacz.gun_refit.*`、`gui.tacz.gun_smith_table.*`、`tooltip.tacz.*`、`gui.tacz.creative_pack_filter.*`）。

---

## 2. 已修（10 项）

| # | 级别 | 文件:行 | 改动 | 依据 |
|---|---|---|---|---|
| **F1** | **P2 真 bug** | `components/GunPackList.java`（`mouseClicked`/`elementClicked`/`drawScreen`）+ `GunSmithTableScreen.mouseClicked` | 新增事件驱动行点击：`func_148124_c(mouseX,mouseY)`（正确带 `this.left`）→ `elementClicked`；`pollClickSuppressed` 丢弃同帧 `GuiSlot` 轮询的重复派发；屏幕 `mouseClicked` 先派发给过滤列表，未命中则让搜索框失焦（对应 1.20 焦点切换） | ① 1.7.10 `GuiSlot.java:243-244` 的 k1/l1 **漏 `this.left`**，而 `GuiSlot.java:129-130`（`func_148124_c`）是正确的 → 原版带 left 偏移的列表都走事件路径 `GuiListExtended#func_148179_a:38-60`（`GuiScreenResourcePacks.mouseClicked:191-196` 显式转发）；② 1.20 列表是子控件，点击由 `Screen#mouseClicked` 派发（`AbstractSelectionList#mouseClicked:297+`） |
| **F2** | **P2 偏离** | `GunRefitScreen.java:197-204` | 删除移植新增的整屏暗色渐变层，恢复「世界+第一人称枪透出」 | 上游 `GunRefitScreen#render:124-136` 只调 `super.render`；1.20.1 `Screen#render:113-118` 仅遍历 renderables（无 `renderBackground`），GUI 由 `GameRenderer#render:1139-1141` 直接渲染；1.7.10 `GuiScreen#drawScreen:57-70` 同样不画背景 |
| **F3** | **P2 偏离** | `GunRefitScreen.mouseClicked`、`GunSmithTableScreen.mouseClicked` | 只把左键继续下发（右键不再误触按钮） | 上游 `AbstractWidget#mouseClicked:172-201`/`isValidClickButton:199-201` 只认 `button == 0`；1.7.10 `GuiScreen#handleMouseInput:329-345` 对任意键派发、`GuiButton#mousePressed:125` 不判键号。两界面无容器槽位（`GunSmithTableMenu` 无 `addSlotToContainer`），不影响原版右键分堆 |
| **F4** | **P2 观感/越界** | `GunSmithTableScreen.java:470-490`、`components/GunPackList.java:drawScreen` | ① `GunPackList` 的 `height` 参数由 `ySize(186)` 改为**屏幕逻辑高度**；② `drawScreen` 用 `enableScissor(left,top,left+width,bottom)` 把整个 `GuiSlot` 绘制裁进列表矩形 | ① 1.7.10 `GuiSlot#drawScreen:351-352` 用 `height` 画底部 `overlayBackground`（dirt 纹理带）；原版所有 `GuiSlot` 子类都传 `Screen#width/height`（`GuiLanguage.java:100`、`GuiSelectWorld.java:244`…），上游 1.20 传 `imageHeight` 是因为 `AbstractSelectionList.height` 只用于已禁用的背景（`AbstractSelectionList.java:222`）；② 上游列表用 `enableScissor`（`AbstractSelectionList.java:211,247-248`，`GunPackList` 上游 `:148-150`），1.7.10 无裁剪 → 首/末行溢出 |
| **F5** | **P3 偏离** | `components/CreativePackFilters.java:isFilterableTab` | 由「除搜索/背包页外全部生效」改为**按页签 label 精确识别 TACZ 页签**（`tab.tacz.*` / `tacz.type.*.name`），与上游一致：侧栏只在 TACZ 页签显示 | 上游 `CreativeModeInventoryScreen#getTabId` 判 namespace==`tacz` 且 `type==CATEGORY`（上游 `CreativePackFilters.java:80-84`）；1.7.10 可用等价信息是 `CreativeTabs#getTabLabel()`（`CreativeTabs.java:177-180`），本模组 16 个页签 label 恒为上述两种（`init/ModCreativeTabs.java:64-127`） |
| **F6** | **P3 偏离** | `components/CreativePackFilters.java`（`PackFilterButton`/`ActionButton`） | ① 页签悬停改为**标准 tooltip 框**（`GuiGraphics#drawHoveringText`，对应上游 `Tooltip.create(name)`）；删除上游没有的悬停白框；② ▲▼ 翻包按钮补悬停 tooltip（上游 `Button.builder(...).tooltip(Tooltip.create(...))`） | 上游 `PackFilterButton#setOption:243-248`、`init:60-67`；1.20 `AbstractWidget#render` 的 tooltip 层 |
| **F7** | **P3 偏离** | `components/FlatColorButton.java:143-149`、`components/smith/ResultButton.java:78-99`、`components/GunPackList.java:drawSlot` | ① `FlatColorButton` 禁用态文字色改回上游常量 `0xF3EFE0`（原为自加的 `0x7F7F7F`）；② `ResultButton` 结果名按上游 `renderScrollingString` 语义**在 x+20..x+92 区域内居中**（原为左对齐且 y 差 1）；③ 过滤列表搜索框 hint 只在「空且未聚焦」时画、颜色改 `0xFFA0A0A0` | ① 上游 `FlatColorButton#renderWidget:57` 固定 `renderScrollingString(graphics, font, 2, 0xF3EFE0)`；② 上游 `ResultButton:46` → `AbstractWidget#renderScrollingString:126-142`（装得下 `drawCenteredString((i+k)/2, k1)`，`k1=(y+4+y+13-9)/2+1=y+5`）；③ 1.20 `EditBox#renderWidget:407-408`（hint 仅空且未聚焦）与 hint 色 `-6250336`(0xA0A0A0) |
| **F8** | **P3 偏离** | `client/tooltip/ClientGunTooltip.java:getText` | 行绘制色折算成 `ChatStyle`：弹药名 GOLD、弹药数 DARK_GRAY、等级/类型/伤害基础色 DARK_GRAY（彩色片段保持 AQUA/DARK_PURPLE/YELLOW）、爆炸后缀 AQUA、穿甲/爆头 GOLD；`renderText` 保留原色参数（drawString 的 color 仅是无样式时的兜底） | 上游 `ClientGunTooltip#renderText:238-313` 的 `drawInBatch(..., 0xffaa00/0x777777/0xffffff, ...)`（有样式的片段覆盖绘制色）；`ChatStyle` 格式码不占宽度（`FontRenderer#getStringWidth` 忽略 §），`getWidth/maxWidth` 不变 |
| **F9** | **P3 偏离** | `client/tooltip/ClientAttachmentItemTooltip.java:addText` | `tooltip.tacz.attachment.builtin` 行改 GOLD（上游无样式组件用 0xffaa00 绘制色） | 上游 `renderText:108-124` 的 `drawInBatch(component, ..., 0xffaa00, ...)` |
| **F10** | **清理** | `client/gui/GunRefitScreen.java`（6 处） | 删除全部 `RefitDebug.log` 客户端调试调用（initGui/list/click/category/unload），恢复上游形态 | r7-network 已删网络侧；`util/RefitDebug.java` 已被删除（现全仓 0 引用），客户端侧如保留会编译失败/成死代码。调试结论已固化在 r4 报告中，不再需要运行时日志 |
| **F11** | **P3 偏离** | `client/gui/AmmoStorageScreen.java` | 补 `drawGuiContainerForegroundLayer`：容器标题 (8,6) + 玩家背包标题 (8, `ySize-94`)，颜色 `0x404040` | 上游 `AmmoStorageScreen` 未覆写 `renderLabels` → 走 1.20.1 `AbstractContainerScreen#renderLabels:176-178` 默认实现（title + `playerInventoryTitle`）；1.7.10 `GuiContainer#drawGuiContainerForegroundLayer` 默认空实现 → 移植侧原先**两行标题都不显示**（与 `GunSmithTableScreen` 不同，上游那里 `renderLabels` 被覆写为空，所以本地工作台不画标题是对的） |

**单点回滚**：F1 删 `GunSmithTableScreen.mouseClicked` 里的 filterList 转发 + `GunPackList#mouseClicked` 即回旧行为（回旧 = 过滤面板点不动）；
F2 重新加回 `graphics.fillGradient(...)` 即回旧观感；F3 删两个 `mouseClicked` 覆写即回「右键也生效」。

---

## 3. 核对过但**未改**的项（结论与依据）

| 项 | 结论 | 依据 |
|---|---|---|
| `AmmoStorageScreen` 全部逻辑 | 等价（标题见 F11），其余未改 | `renderBg` 四段 `generic_54` 拼合/容量条/虚拟条目/数量标签缩放 0.666、`renderTooltip` 的容量区与虚拟格、`mouseClicked` 三动作（`QUICK_MOVE/PICKUP_HALF/PICKUP`）、`mouseDragged` 抑制、`getVirtualCell`（`func_146978_c` 内部已减 guiLeft）逐段与上游同构 |
| `GunPackProgressScreen` | 等价，未改 | 上游 `renderBackground → 居中文本 → super.render`；本地 `drawDefaultBackground → 居中文本 → super.drawScreen`。`IProgressUpdate` 的 4 个方法均为上游公开方法的等价适配（注释已列映射） |
| `ModContainerScreen` | 有据等价，未改 | 见 §1.3 |
| `GunLevelUpToast` | 上游本就无可见行为，未改 | 上游 `render` 全注释、类注释「没有实际使用」；本地 `show()` 聊天回退 0 调用者。双方均无可见行为 → 非回归 |
| `ClothConfigScreen` | 有据降级，未改 | 1.7.10 无 Forge `ConfigScreenHandler` 扩展点；`registerNoClothConfigPage` 只做反射探测（`CompatRegistry.java:56-62` 仍调用），`compat/cloth/MenuIntegration` 有反射兜底页 |
| `HSVSliderGroup` 拖动 | 等价，未改 | `ForgeSlider(min=0,max=1,step=0.01)` → `GuiButton` 每帧按 mouseX 更新 + clampStep；`apply()` 的「客户端脏写 NBT 实时预览 + 关界面时发包」与上游逐行相同（`onGuiClosed` 发 `ClientMessageLaserColor`） |
| `GunRefitScreen` 附件列表分页/变体/适配器/虚拟原厂配件 | 等价，未改 | `getVirtualOemAttachments` 的过滤顺序/排序/`allowAttachment`、`addVariantButtons`（`> ` 前缀/排序）、`addSlotAdapterButtons`（`allowsDirectAttachment` 禁用 + `adapter_requires_unload` tooltip）、分页 `totalPage=(count-1)/8` 与上游逐行相同 |
| `GunSmithTableScreen` 配方分类/排序/筛选/搜索/分页/合成校验 | 等价，未改 | `RECIPE_ORDER`（namespace→sort→recipeId）、`RecipeFilter`、`isSuitableForMainHand`/`shouldFilterByMainHand`/`isNameMatch`、`applyRequestedRecipeSelection`（`consumeRecipeSelection`/`canSelectRecipe` 的 1.7.10 单参版语义等价，见下）、合成按钮的创造模式豁免/材料计数、`mouseScrolled` 命中带（guiTop+66..+151）与上游常量相同 |
| `GunSmithTableMenu.canSelectRecipe(recipeId)` 单参 | 语义等价 | 上游另做 `filter.contains(recipeId)` 前置判断；本地 `applyRequestedRecipeSelection` 在配方不在列表时会 `clearFilters + classifyRecipes` 再判（块级 filter 仍在 `classifyRecipes` 生效）→ 最终「不可选」结果相同，只是多走一次重建 |
| tooltip 行序/隐藏 part/宽度 | 等价，未改 | `GunTooltipPart` 掩码判定、`getHeight/getWidth` 段高、折行 300、`desc` 最多 3 行、弹药/等级/类型/伤害/穿甲/爆头/移速/提示/枪包名顺序全部与上游相同 |
| r6 图标网格 | **未回退** | `AttachmentGunGridOverlay`/`TooltipLineUtils`/`TooltipEvent` 接线复核：`getLegacyTooltipLines` 预留空行+撑宽行、`GridLayout` 高度上限 `(scaledHeight-40)/10`、`TooltipEvent` 在 `DrawScreenEvent.Post` 补画、`indexOfLine` 去格式码比对——与 r6 报告一致 |
| r6 创造栏图标/侧栏位置 | **未回退** | `CreativePackFilters` 未动 `guiLeft-28`/`guiTop+11+29i`/28×26 压缩绘制/命中矩形；未动 `BlockItemTransforms`/`SlotModel`/`RenderHelperCompat`（属 r6/r7-model 范围，本轮未触碰） |

---

## 4. 未修 / 有据降级（逐条）

| # | 级别 | 项 | 未修理由 / 去向 |
|---|---|---|---|
| **D1** | P3 | `GunSmithTableScreen#renderLeftModel`：上游是**绕 Y 轴旋转的 3D 静态模型预览**（`ItemRenderer#renderStatic(..., FIXED)` + `Lighting` + `ModelViewStack`），本地降级为「裁剪区内放大的 2D 物品图标」，无旋转/光照 | 1.7.10 无 `ItemRenderer#renderStatic`/`Lighting`/`PoseStack`；要真 3D 需重写一条 FIXED 上下文物品渲染链（风险高、收益是观感）。类注释已记录降级；缩放按钮（`scale 10..200`）仍生效 |
| **D2** | P3 | `GunSmithTableScreen#openUrl`：上游用 `ConfirmLinkScreen` 二次确认，本地直接 `Desktop#browse` | 1.7.10 无 `ConfirmLinkScreen`；类注释已记录 |
| **D3** | P3 | `GunSmithTableScreen#getIngredientTooltip`：上游遍历 `CreativeModeTabRegistry.getSortedCreativeModeTabs()` 找「包含该物品的页签」并插到第 1 行；本地只看物品自己声明的 `getCreativeTab()` | 1.7.10 `CreativeTabs` 无 `contains(ItemStack)`（要遍历所有页签的 `displayAllReleventItems` 才能模拟，成本高且会触发大量物品枚举）；已 TODO 记录 |
| **D4** | P3 | 位图 tooltip 图标缺失：枪械 tooltip 的弹药图标、弹药盒图标、弹药库每条目图标（上游 `renderImage`） | 1.7.10 纯文本 tooltip 无位图能力；图标信息与同组文字重复（弹药名/数量），**无信息损失**；r2-04 §六已登记，本轮裁决「不做 overlay 复刻」（收益仅装饰性，需再引一套 r6-grid 式补画机制） |
| **D5** | P3 | 创造模式页签 1.7.10 每页 5×9、16 个 TACZ 页签分 3 页（截图「2/3」） | 1.7.10 平台限制（`creativeTabArray` 分页），非 bug（r6 已登记） |
| **D6** | P3 | `GunPackList` 滚轮依赖 `GuiSlot#drawScreen` 的 `Mouse.next()` 轮询（`GuiScreen#handleInput` 每 tick 先抽干事件队列 → 约 1/3 事件被 `GuiSlot` 拿到，滚动响应不稳定）；滚动条可拖拽 | 1.7.10 `GuiSlot` 机制限制；若要修需给 `GunPackList` 加事件式 `mouseScrolled` 转发，但会与 `GuiSlot` 内部轮询**双重滚动**（无法在不复制整个 `drawScreen` 的前提下精确去重），风险大于收益。r2-04 P3-2 已登记 |
| **D7** | P3 | `CreativePackFilters` 侧栏页签悬停 tooltip 的**绘制顺序**：在 `drawScreen` TAIL 画（晚于 `GuiContainer` 的槽位 tooltip） | r2-04 P3-7 已登记；侧栏在 `guiLeft-28`，与槽位 tooltip 重叠概率极低，未改 |
| **D8** | 记录 | `AmmoStorageScreen` 保留右键语义（右键=取半组 `PICKUP_HALF`） | 上游 `AmmoStorageScreen#mouseClicked` 明确把 `button==1` 映射为 `PICKUP_HALF`，**不能**按 F3 的「只认左键」处理；F3 只作用于无槽位的改装台/工作台 |
| **D9** | 记录 | `AmmoBoxItem#overrideStackedOnOther`（弹药盒物品栏内点进/点出）缺失 | **跨模块**：正确落点是 mixin `Container#slotClick`（详见 §5.B）；本轮未做，需求已给 r7-item/r7-platform |

---

## 5. Lead 转派三项的裁决（证据）

### A. 「AmmoStorageBlockItem 缺内容预览 tooltip」→ **不成立，已有实现**（建议销项）

- 现有实现：`client/event/TooltipEvent.java:275-278` 对 `instanceof AmmoStorageBlockItem` 调
  `ammoStorageTooltip(stack)`；`:343-375` 读物品 NBT `BlockEntityTag.Entries`（每项 `Stack`/`Count`，与
  `inventory/AmmoStorageInventory#save()` 同格式），`:377-401` 按「同物品+同 NBT」合并计数、`:369-374` 截前
  `MAX_TOOLTIP_ITEMS=5` 条 + `ClientAmmoStorageTooltip` 的 `and N more...`（ITALIC）——即上游
  `AmmoStorageBlockItem#getTooltipImage`（上游 :57-85）的等价物，r2 F6 已接线并有回归清单（`docs/review/r2/04-gui.md` §六）。
- r7-block 建议的落点 `ClientBlockItemTooltip` 不可行：`AmmoStorageBlockItem extends ItemBlock` 且**不实现**
  `BlockItemDataAccessor`（`item/AmmoStorageBlockItem.java:35`），该分支到不了；即便实现，`buildRichTooltip` 的
  AmmoStorage 分支在前会先命中。
- 唯一真实差距是每条目左侧 16×16 图标（位图降级，见 §4 D4）。已在 `ClientBlockItemTooltip` 类注释加锚点，
  避免下轮再误报。

### B. 「AmmoBoxItem 缺 overrideStackedOnOther」→ **走 mixin（非 GUI 方案）**

- 上游语义（`item/AmmoBoxItem.java:102-180`）：**物品栏内右键**点进/点出——目标槽空则从盒取出（创造/全类型创造盒拒绝、
  按 `AmmoIndex#getStackSize` 限量、取空后 `setAmmoId(EMPTY)`、`playRemoveOneSound`）；目标槽是 `IAmmo` 则存入
  （全类型创造拒绝、盒 id 空则设为目标 id、id 不同拒绝、创造盒直接 `Integer.MAX_VALUE`、普通按
  `stackSize * AMMO_BOX_STACK_SIZE * (level+1)` 上限 `safeTake`、`playInsertSound`）；其它 return false 交回原版。
  `overrideOtherStackedOnMe`（上游 :97-100）是纯 `super` 调用 → **无行为，不需要移植**。
- 落点：mixin `Container#slotClick(int slotId,int clickedButton,int mode,EntityPlayer)`（`Container.java:142`）
  HEAD cancellable，条件 `mode==0 && clickedButton==1 && slotId>=0 && player.inventory.getItemStack() instanceof IAmmoBox`。
  注意该方法在**客户端预测 + 服务端权威**两侧都执行（`PlayerControllerMP#windowClick:475-481` 先本地 `slotClick` 再发 C0E），
  两侧必须同语义；cancel 时返回 `player.inventory.getItemStack()`（修改后的盒），随 C0E/`S2FPacketSetSlot(-1,-1,...)` 同步光标栈。
- 依赖已具备：`IAmmoBox` 已有 get/set AmmoId/AmmoCount、`getAmmoLevel`、`isCreative`、`isAllTypeCreative`
  （`api/item/IAmmoBox.java:17-77`）；`AmmoBoxItem` 已有 `playInsertSound/playRemoveOneSound`（:224-233）。
- **分工**：`item/AmmoBoxItem` 新增 `tryStackedOnOther(...)`（搬上游逻辑，替换 `item/AmmoBoxItem.java:235-238` 那条
  「需要新增 GUI」的错误 TODO）→ r7-item；`Container#slotClick` mixin + `mixins.tacz.json` 登记 → r7-platform。
  本轮未改这两处 scope（已另行 msg Lead）。

### C. 客户端 `RefitDebug` 残留 → **已删（F10）**

- r7-network 删网络侧后，`util/RefitDebug.java` 已被删除、`SlotAdapterHelper#describeAttachmentPermission` 也已被移除
  （当前全仓 0 引用）；本轮的客户端 6 处调用一并删除，`GunRefitScreen` 恢复上游形态。
- 调试结论固化在 `docs/review/r4/03-refit.md`，不再需要运行时日志。

---

## 6. 需实机验证清单（按优先级）

1. **改装台（Z）**：背景**透明**（能看到世界与中央预览的枪，枪不被压暗）；**左键**装/卸/变体/适配器/属性图开关正常，
   **右键**点这些按钮**无反应**；翻页/激光滑条拖动正常。
2. **枪械工作台（F 过滤面板）**：
   - 点击搜索框能聚焦并输入（输入时触发配方重筛）；点复选框能勾选/取消（by-hand / ALL / 各枪包）；点 ALL 与枪包按钮列表切换；
   - 列表区域**不再有 dirt 纹理条带**，首/末行不溢出面板；滚动条可拖拽；
   - 点击列表外区域后搜索框失焦（再打字不应继续进搜索框）；
   - 右键点 F/缩放/翻页/合成按钮无反应；关闭过滤面板后缩放/URL/合成按钮正常。
3. **创造模式物品栏**：侧栏只在 TACZ 页签出现（切到原版「建筑方块」等页签**不应**出现 ALL/枪包/▲▼）；
   TACZ 页签内 ALL 与 tacz 包按钮列表仍无差异（单内容包，预期，r6-1 结论不翻案）；悬停页签出现标准 tooltip 框；
   ▲▼ 悬停有 tooltip；点选/滚动翻包正常。r6 的 3D 工作台图标/2D 图标位置**不应变化**。
4. **tooltip**：
   - 枪械：弹药名金色、数量深灰、等级/类型/伤害深灰（等级括号彩色、类型/伤害 AQUA）、穿甲/爆头金色、移速红、
     「按 Z 改装」黄斜体、枪包名蓝斜体；描述分多行无字面 `\n`；
   - 配件：Shift 展开仍是图标网格（r6 行为）+「支持枪械：」蓝条；builtin 原厂配件首行金色；
   - 弹药盒/弹药库：弹药库方块（挖掉后带 `BlockEntityTag` 的掉落物）悬停出现前 5 条 + `and N more...`；
   - 工作台/改装台内悬停材料/配件槽，富 tooltip 与网格仍显示。
5. **工作台其它**：配方结果名在按钮内**居中**、超长截断；分类页签悬停显示名称；合成按钮在材料不足/无配方时不可点；
   搜索框输入不崩（r2-04 §七 7.6 回归）。
6. **弹药库界面**：顶部「Ammo Storage」标题 + 玩家背包「Inventory」标题显示（F11 新增），容量条/容量 tooltip/虚拟格
   点击（左键取组/右键取半组/shift 快速移动）正常。
7. **回归 r6**：创造栏图标网格/工作台模型位置/配件 tooltip 网格不得变化；`r6/screenshots` 场景重拍对比。

---

## 6.5 r7 实机回归修复：按 Z 后改装界面只剩枪模、看不到任何 GUI 按钮（P0，已修：Lead 事件侧根修）

**现象**（用户实机回传）：按 Z 打开改装界面后只有第一人称 refit 预览的枪械模型，没有任何按钮/配件槽位；
用户确认由本轮未 commit 改动引入。

**根因链（静态确定，非猜测）**

1. 本轮未 commit 改动把 `client/event/PreventsHotbarEvent`（平台层 scope）从「只取消
   `RenderGameOverlayEvent.Pre(HOTBAR)`」改成「对**所有** `Pre` 事件（**含 `ALL`**）`setCanceled(true)`」，
   意图是照抄上游「这两个界面打开时取消全部原版 overlay」。
2. 1.7.10 Forge 的 `GuiIngameForge#renderGameOverlay` 里 `Pre(ALL)` 不是「一个 overlay」，而是**整体短路开关**：
   `GuiIngameForge.java:105` `if (pre(ALL)) return;` —— 它在 `:108` 的
   `mc.entityRenderer.setupOverlayRendering()` **之前** return。
3. `setupOverlayRendering()`（`EntityRenderer.java:1739-1749`）正是 GUI 帧的建立点：`glOrtho(0..scaledW, scaledH..0)`
   + `MODELVIEW = identity + translate(0,0,-2000)`；`:109` 的 `GL_BLEND` 与 `:118` 的 `blendFunc` 也一并不再执行。
4. 于是随后 `EntityRenderer.java:1136-1137` 的 `currentScreen.drawScreen(...)` **仍在世界透视投影/视图矩阵下绘制**：
   GUI 顶点（0..width, 0..height）被当成世界坐标 → 全部画到相机外/被裁掉 = 界面不可见。
5. 枪模是**世界渲染阶段**（`FirstPersonRenderGunEvent` + `RefitTransform`）画的，发生在 GUI 之前、不依赖 GUI 帧
   ⇒ 实机表现就是「只剩枪械模型」。同一机制也会让**枪械工作台界面**整体不可见（本类同样被
   `PreventsHotbarEvent` 取消 `Pre(ALL)`），只是用户先测的是 Z。

**修复（Lead 落地，事件侧根修；平台层 scope）**：在 `client/event/PreventsHotbarEvent#onRenderHotbarEvent`
里，当 screen 是 `GunSmithTableScreen`/`GunRefitScreen` 且 `event.type == ALL` 时，**先**补上
`GuiIngameForge#renderGameOverlay:108-118` 被 `:105` 短路跳过的 GUI 帧与混合基线，**再**取消事件：

```java
if (event.type == RenderGameOverlayEvent.ElementType.ALL) {
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.entityRenderer != null) {
        mc.entityRenderer.setupOverlayRendering();   // GUI 正交投影 + 单位 MODELVIEW
    }
    GL11.glEnable(GL11.GL_BLEND);                    // GuiIngameForge:109
    OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0); // GuiIngameForge:118
}
event.setCanceled(true);
```

- **为什么放事件侧**：GUI 帧缺失是「取消 `Pre(ALL)`」的直接后果，在事件侧补一次即可对**任何**受影响界面通用
  （未来新增「取消整套 HUD」的界面不用重复补），同时保留上游「这两类界面连 vignette/chat 都不画」的语义。
- **per-screen workaround 已回退**：r7-gui 本轮曾在 `GunRefitScreen#drawScreen`/`GunSmithTableScreen#drawScreen`
  开头加过同样的三行兜底，Lead 已移除（连同因此 unused 的 `GL11`/`OpenGlHelper` import；
  `GunSmithTableScreen` 的 `GL11` 仍被模型预览使用，保留）。当前两个 screen **不再有** per-screen GUI 帧补丁，
  修复点唯一（`PreventsHotbarEvent`）。
- **不是**「本轮删掉的暗色背景层」导致：背景层删除只影响观感（多一层 75% 黑），不可能让按钮消失；
  即使保留背景层，GUI 帧缺失时整屏（含背景）都不可见。背景层删除的正确性依据见 §2 F2。
- **验证状态**：Lead 已统一构建通过（BUILD SUCCESSFUL）；实机判据见下。

**下一轮实机判据（按顺序）**：
1. Z 界面：**按钮/配件槽位全部出现**；背景透明、世界与枪模可见；HUD（快捷栏/准星/聊天）隐藏；左键装/卸/变体/
   适配器正常，右键无反应。
2. 工作台界面：整体可见（同样的修复）；F 过滤面板搜索/复选框可点。
3. 若仍不可见：先看是否只有这两个界面异常（则回到本条根因链，检查 `PreventsHotbarEvent` 是否仍取消 `ALL`、
   `setupOverlayRendering` 是否被其它 mixin 跳过），再加一次性投影矩阵日志（`GL_PROJECTION_MATRIX` 的
   `m[15]==1` 表示正交）。

---

## 7. 校验记录与覆盖（诚实）

- **未运行 gradle**。r7-gui 本轮改动 10 个文件（`GunRefitScreen`、`GunSmithTableScreen`、`AmmoStorageScreen`、`GunPackList`、
  `FlatColorButton`、`ResultButton`、`CreativePackFilters`、`ClientGunTooltip`、`ClientAttachmentItemTooltip`、
  `ClientBlockItemTooltip`），加 `ModContainerScreen` 复核；**全部** `lint_files(min_severity="error")` = **0 error**
  （对 scope 内 30 个文件分两批全量复跑）。§6.5 的回归修复最终由 Lead 落在事件侧
  `client/event/PreventsHotbarEvent`（**不属于**上述 10 个文件；r7-gui 加在两个 screen 的 workaround 已由 Lead 回退），
  Lead 已统一构建通过（BUILD SUCCESSFUL）。
- 静态证据：逐文件与上游对照（见 §1）；本地化 key 集合脚本对账（见 §1.4）；`ClientMessage*` 构造签名与 GUI 调用点
  逐一对齐（`ClientMessageRefitGun/InstallVirtualAttachment/UnloadAttachment/SetAttachmentVariant/SetSlotAdapter/LaserColor/Craft/AmmoStorageClick`）；
  `ServerMessageRefreshRefitScreen` 的客户端刷新路径核对（`ClientProxy.java:427-435` ↔ 上游 :31-38，含 `initGui`+`postChangeEvent`）。
- **逐行精读**：`client/gui/**`（去 overlay）22 文件、`client/tooltip/**` 9 文件、`ModContainerScreen`；
  上游对应 22+6 文件；1.7.10 侧 `GuiScreen/GuiContainer/GuiSlot/GuiButton/GuiTextField/FontRenderer/CreativeTabs/
  GuiContainerCreative/GuiListExtended/GuiScreenResourcePacks` 相关段；1.20.1 侧 `Screen/GameRenderer/AbstractWidget/
  AbstractSelectionList/AbstractContainerScreen/ItemTransform` 相关段。
- **未覆盖/未实机**：`client/gui/overlay/**`（r7-crosshair scope）；所有结论基于源码/贴图常量静态推导，
  未 `runClient`；§6 全部需要实机确认。r2/r4/r6 已登记且仍成立的项只在 §3 复核一行，不重复报。
