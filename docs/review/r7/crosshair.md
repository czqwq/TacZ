# r7-crosshair · 准星 / HUD / 命中反馈 / rangefinder 上游等价性审查

任务：`task-14`。对照上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**` 与 1.7.10 原版/Forge
（`build/rfg/minecraft-src/**`）逐文件核对本模块；修真实移植偏离；截图里的「rangefinder 0.0m」专项排查；
清理本作用域每帧 `[TACZ-DBG]`。**未运行 gradle**，静态检查用 IDEA MCP。

写作用域（本任务）：`client/renderer/crosshair/**`、`client/gui/overlay/**`、
`client/event/{RenderCrosshairEvent,ClientHitMark,ClientGunTimeResetEvent}.java`、
`client/model/functional/RangefinderImpactPredictionRender.java`、
`client/resource/pojo/display/gun/{Rangefinder,RangefinderManager,TextShow}.java`。

---

## 一、摘要

| # | 结论 |
|---|---|
| 1 | **截图里的「0.0m + 白色 bracket」不是 rangefinder，而是 `HeatBarOverlay`**：白色 bracket 是 `textures/hud/heat_base.png` 的刻度框，`0.0%` 是 `HEAT_FORMAT_PERCENT`（`DecimalFormat("0.0%")`）在热度为 0 时的输出。像素级比对一致（§二）。 |
| 2 | 截图场景是**第三人称/非开镜**：该场景下 rangefinder 文本**不可能**被画出来（`TextShowRender` 的 `transformType.firstPerson()` 闸门 + SBM 第一人称闸门），这也反证了 §二 的结论。 |
| 3 | 图中真正的异常是「第一人称手模 + 本地玩家身体同屏」（两条原版门控之一被破坏：第一人称不画本地身体 / 第三人称不画第一人称手模）——属 **r7-fp / r7-tp** 作用域，不是准星/HUD 的问题；已在 §五 F4 作为交叉发现转交。 |
| 4 | 本模块发现 **2 个 HUD 层真实功能偏离 + 1 个交叉发现**，均已由对应作用域修复（以工作区为准）：F1「持枪时 `registerAbove(CROSSHAIR)` 层永不渲染」（`Post(CROSSHAIRS)` 被取消后不再发出）由 **r7-platform** 修复；F2「GUI 打开时不画 HUD」由 **r7-platform** 修复；F3「`PreventsHotbarEvent` 取消范围不足」由 **r7-input** 修复。详见 §五。 |
| 5 | 本模块修复 **3 处**：`RangefinderManager` 改回上游同构的 `BlockRayTrace` + `HitboxHelper`（原为占位降级）；`HeatBarOverlay.lerpArgb` 取整语义对齐 1.20 `Mth.lerpInt`；`InteractKeyTextOverlay` 过期注释纠正（§四）。 |
| 6 | 准星本身（替换/隐藏/命中标记/贴图/时序）与上游逐行等价，未发现偏离；上游 1.2.0 **没有**「inaccuracy 动态扩散/旋转/缩放」机制，因此不存在「缺失扩散」的问题。 |
| 7 | 本作用域 `[TACZ-DBG]` / 每帧日志 **0 处**；改动文件 `lint_files(min_severity=error)` = **0 error**。 |

---

## 二、截图专项排查：`a_thirdperson_gun_rangefinder.png`（任务要求的最重点）

截图：`docs/review/r7/screenshots/a_thirdperson_gun_rangefinder.png`（1039×897）。
现象：玩家第三人称画面中央出现白色「[ · · ]」刻度括号 + 下方「0.0」文本。

### 2.1 证据链（结论：`HeatBarOverlay`，不是 rangefinder）

| # | 证据 | 依据 |
|---|---|---|
| E1 | **白色 bracket = `heat_base.png`**。贴图非透明像素 bbox = `(32,73)-(95,79)`：64 px 宽横线（`y=79, x=32..95`）、两端 7 px 竖条（`x=32`/`x=95`, `y=73..79`）、4 个向上刻度（`x=40` 高 2、`x=48` 高 1、`x=79` 高 1、`x=87` 高 2）。截图里括号的竖条/横线/刻度相对位置 = `8,16,47,55`（贴图坐标 = `40-32, 48-32, 79-32, 87-32`）**逐像素一致**；括号带宽 240 physical / 64 texel，两端竖条高 26 physical / 7 texel，比例一致。 | 贴图：`src/main/resources/assets/tacz/textures/hud/heat_base.png`；调用点唯一：`HeatBarOverlay.java:106` `blit(HEATBASE, w/2-64, h/2-44, 0,0,128,128,128,128)`（全仓 grep `heat_base` 只此一处 blit，另一处是 `GunHudOverlay` 里上游本来就有的未使用常量）。 |
| E2 | **文本 = `0.0%`（字形级证明，排除 `0.0m`）**。`HEAT_FORMAT_PERCENT = new DecimalFormat("0.0%")`，热度 0 时输出 `0.0%`（4 个字形）。对截图文本区做逐列像素剖面，最后一个字形拆成 5 个源像素列，其「行集合」为 `c0={0,1,6} / c1={4,5} / c2={3} / c3={1,2} / c4={0,5,6}`（源像素行号 0..6，缩放 ≈3.75 px/源像素）——与 `build/rfg/minecraft-src/resources/assets/minecraft/textures/font/ascii.png` 里 `%` 的位图（`#...#` / `#..#` / `...#` / `..#` / `.#` / `.#..#` / `#...#`）**逐列逐行完全一致**；而 `m` 是 x-height（只有 5 行、无行 0/6 的像素），列型完全不同 ⇒ 可确定是 `%` 而非 `m`。文本 bbox `(498,353)-(568,378)`（宽 71/高 26），三处 `0` 的位图亦与 ascii.png 的 `0` 一致。 | `HeatBarOverlay.java:40`（format）、`:109`（`locked ? "!OVERHEAT!" : HEAT_FORMAT_PERCENT.format(...)`）；字体图集 `build/rfg/minecraft-src/resources/assets/minecraft/textures/font/ascii.png`（1.7.10 默认字体，与 1.20 的 default 字体位图同源）。 |
| E3 | **相对位置与 `HeatBarOverlay` 的坐标公式完全吻合**。`blit` 在 `pose.scale(heatScale)` 之后画：括号横线屏幕 y = `h/2 + (79-44)*heatScale = h/2 + 35*heatScale`，竖条顶 = `h/2 + 29*heatScale`，文字 = `h/2 + 38*heatScale`。截图：竖条顶到横线 22 px、横线到文字顶约 10 px ⇒ 比例 = `(38-35)/6 = 0.5`，与 `3*heatScale : 6*heatScale`（heatScale≈0.75 时 2.25 : 4.5 GUI px）一致；括号水平中心 = 535.5，与 `w/2-64+64`（贴图中心）= 屏幕中心一致。 | `HeatBarOverlay.java:62-66`（scale + `width/heatScale` 传参）、`:105-113`（blit / drawString）。 |
| E4 | **`0.0` 不是 rangefinder 的输出格式**。`RangefinderManager.setDistanceText` 只输出「整数、按 max_distance 位数补零」的字符串，**没有小数点、没有单位后缀**；单位 `m`/`M` 来自枪包 display 的 `text` 模板（`"%rangefinder%m"` / `"%rangefinder% M"`）。未命中时输出 `---`。逐字符检查：`"(0" + repeat + ")" + Integer.toString(...)`，无任何 `%.1f`/`.` 路径。 | 上游 `RangefinderManager.java:171-179`；本仓 `RangefinderManager.java:205-214`（逐行同构）。 |
| E5 | **第三人称/非开镜场景下 rangefinder 文本不可能渲染**（三条闸门叠起来）：① `TextShowRender.render` 第一行 `if (!transformType.firstPerson()) return;`；② 第三人称手持物品走 `AnimateGeoItemRenderer.renderItem(EQUIPPED)`、`RenderHelperCompat.displayContextOf` 映射为 `THIRD_PERSON_*`（不是 FIRST_PERSON）；③ 第一人称手持物的渲染入口 `FirstPersonRenderHandler#onRenderHand` 自己又闸了 `mc.gameSettings.thirdPersonView != 0 → return`。 | `TextShowRender.java:53-55`（上游同）；`RenderHelperCompat.java:434-449`；`simplebedrockmodel/.../FirstPersonRenderHandler.java:428-430`。 |
| E6 | **`RangefinderManager` 的 0.9 瞄准闸门也不满足**：`updateResult` 在 `getClientAimingProgress < 0.9` 时 `clearResult()`（文本变 `---`），截图是腰射/第三人称，不可能得到数码。 | `RangefinderManager.java:142-145`；上游 `RangefinderManager.java:134-137`。 |
| E7 | **默认包里只有 minigun 启用了 heat**（`ak47_data.json` 的 heat 段是注释掉的），因此这条 HUD 只在拿 minigun 时出现；minigun 正是截图底部那把多管枪。heat NBT 一旦开过火就常驻（`hasHeatData` = NBT 有 `HeatAmount` float），所以冷却到 0 后仍显示 `0.0%` —— **上游 `HeatBarOverlay` 同构行为**（`if (gunData.getHeatData() != null && iGun.hasHeatData(stack))` 无「热度>0」条件）。 | `src/main/resources/.../data/guns/minigun_data.json:27-50`；上游 `HeatBarOverlay.java:51-68`；`GunItemDataAccessor.hasHeatData`（port `:423-426` = 上游 `:400-402`）。 |

证据图（上：截图区域 3×；下：`heat_base.png` 的 `(32,72)-(96,80)` 区域 10×）：
`docs/review/r7/screenshots/crosshair_rangefinder_evidence.png`。

### 2.2 排查清单逐项回答（任务点 6）

| 子项 | 结论 |
|---|---|
| `RangefinderManager` 距离是否恒 0？ | **否**。距离来自 `start.distanceTo(hitPos)`（实体）或 `start.distanceTo(blockResult.hitVec)`（方块），无写死 0 的路径；`clearResult()` 只在未命中/未开镜时把 `text` 置 `---`。截图的 0 来自 heat 百分比，与本类无关。 |
| raycast/命中结果坐标系 | 起点 = `player.getPosition(partialTick)`（=1.20 `getEyePosition`，含眼高插值），方向 = `getLook(partialTick)`（=1.20 `getViewVector`），终点 = 起点 + 方向×maxDistance（世界坐标）；方块结果是 `MovingObjectPosition.hitVec`、实体结果是 `AxisAlignedBB.calculateIntercept` 的 `hitVec`，全是世界坐标，无局部/相机空间混用。 |
| `Minecraft.objectMouseOver` / 实体命中 | `RangefinderManager` **不读** `objectMouseOver`（这是 `InteractKey`/准星模块用的指针命中，见 `InteractKey.java`）；它自己按视向量做 block+entity 独立 raycast（上游同）。修正后实体箱走 `HitboxHelper.getFixedBoundingBox`（§四.1）。 |
| `TextShow` 渲染条件（是否应只在第一人称 ADS） | 上游语义 = **只在第一人称**（`firstPerson()`），但**不限 ADS**：未开镜时 `RangefinderManager` 返回 `---`，文本会显示 `---m`/`--- M`。本移植与上游逐行一致，未改（「仅 ADS 显示」不是上游行为）。 |
| `RangefinderImpactPredictionRender` 条件 | 与上游相同：`!transformType.firstPerson() || !RangefinderManager.shouldRenderImpactPrediction()` 直接 return；`shouldRenderImpactPrediction` 还要求 `showImpactPrediction`、最近一次刷新未过期、以及 `getClientAimingProgress >= 0.9`。 |
| bracket 贴图来源与显示条件 | `heat_base.png`，由 `HeatBarOverlay` 在「枪有 heat 数据且枪 NBT 有 `HeatAmount`」时绘制；与 rangefinder 无关。 |
| 为何出现在「非开镜/第三人称」 | HUD 层与相机模式无关（上游 Forge overlay 同样如此）；热条是常驻 HUD。截图里真正的越界是**第一人称手模 + 本地玩家身体同屏**（§五 F4）。 |
| 会不会是「配件 tooltip 的 EffectiveRange `0.0m`」？（r7-util 独立提出的方向） | 不是本截图：① 无 GUI/tooltip 打开，世界 HUD 上不会有 tooltip 文本；② 最后一字形是 `%` 不是 `m`（E2 字形级证明）；③ 文本紧贴 `heat_base` 括号下方（E3），正是 `HeatBarOverlay` 的布局。`EffectiveRangeModifier` 的 `String.format("%.1fm", …)` 是另一条真实链路（改装/属性求值），与本截图无关，但值得在 r7-item/r7-gui 侧另行验证（本模块未涉猎）。 |

### 2.3 截图里其它 HUD 元素核对

- **没有准星**：第三人称时 `RenderCrosshairEvent` 先取消原版 `Pre(CROSSHAIRS)`，再在 `renderCrosshair` 里因
  `thirdPersonView != 0` 提前 return ⇒ 无准星。与上游（取消 `RenderGuiOverlayEvent.Pre(CROSSHAIR)` + `!isFirstPerson` return）**同构**。
- **没有命中标记/连杀**：`hitTimestamp/killTimestamp` 均过期，正常。
- **热条位置**：见 §2.1 E3，公式级吻合，**不是位置错误**。
- **快捷栏可见**：1.20 上游同样只在 `GunSmithTableScreen/GunRefitScreen` 打开时隐藏 HUD 背景（`PreventsHotbarEvent`），世界内持枪不隐藏；1.7.10 侧一致（该类的取消范围问题另见 §五 F3）。

---

## 三、逐文件对照表

图例：`≡` 逐行等价（仅平台 API 替换）｜`≡*` 等价 + 有据降级/移植特有代码｜`修` 本轮修复｜`发现` 跨模块发现（见 §五）。

### 3.1 准星 / 命中反馈事件

| 移植文件 | 上游文件 | 结论 | 说明 |
|---|---|---|---|
| `client/event/RenderCrosshairEvent.java`（241 行） | `client/event/RenderCrosshairEvent.java`（193 行） | `≡*` | 结构/语义逐行同构：`HIT_ICON`/`KEEP_TIME=300`/三个时间戳；`mainHandHoldGun` 门 → `setCanceled(true)` → `renderHitMarker` → 换弹中/改装屏/动画 `shouldHideCrossHair` 检查顺序一致；ADS `aimingProgress>0.9` + `isShowCrosshair` + `ShoulderSurfingCompat.showCrosshair()` 双强制；`thirdPersonView!=0`/`hideGUI` 早退；命中标记四角 `blit` 的 UV/位移/淡出（kill 额外 4 px 位移）/爆头红色与上游逐值一致。平台替换：`RenderGuiOverlayEvent.Pre+VanillaGuiOverlay.CROSSHAIR` → `RenderGameOverlayEvent.Pre+CROSSHAIRS(receiveCanceled=true)`；`getWindow()/getGuiScaledWidth` → 事件 `resolution`；`RenderSystem.blendFunc(枚举)` → `glValue()`；`setShaderColor` 的 tint 在 mcshim 里由 `GuiGraphics#setColor` 承担（两处都设，保证顶点色/batch 两条路径都染色）。降级：`GameType.SPECTATOR`/`player.isSpectator()` 1.7.10 不存在（1.8+ 才有）⇒ 省略。 |
| `client/renderer/crosshair/CrosshairType.java`（49 行） | 同名（44 行） | `≡` | 22 个枚举值逐项一致；`getTextureLocation` 走 `textures/crosshair/normal/<name>.png` + HashMap 缓存。**上游没有**「gun pack 自定义准星贴图」维度（贴图固定在本 mod 的 `assets/tacz`），所以本模块对 r7-respack-client 无依赖/无跨模块发现。 |
| `client/event/ClientHitMark.java`（122 行） | 同名（82 行） | `≡*` | `onEntityHurt`：`logicalSide==CLIENT` 闸、`player.equals(attacker)`、hit/headshot 时间戳、爆头/肉声音效、标靶车伤害累计（`DAMAGE_COUNTER_RESET_TIME` 窗口 + `%.1f`/`%.2f`）全部一致；`onEntityKill`：kill 时间戳 + `KillAmountOverlay.markTimestamp` + 击杀音效 + 爆头时间戳，一致。降级：`LocalPlayer#displayClientMessage(..., true)`（1.20 action bar）在 1.7.10 无对应 API（1.8+ 才有）⇒ 改 `addChatMessage`（注释已标）。 |
| `client/event/ClientGunTimeResetEvent.java`（84 行） | 同名（54 行） | `≡*` | 逐条调用顺序与上游完全一致（`ScopeLensRenderer.close` → dataHolder 清理 → `GunTime.resetEpoch` → 各 `resetTimestamps` …）；平台替换：`ClientPlayerNetworkEvent.LoggingOut` → `FMLNetworkEvent.ClientDisconnectionFromServerEvent`（FML 总线）；类改 public 构造 + 实例处理方法（1.7.10 FML 注册要求）。 |

### 3.2 HUD overlays（`client/gui/overlay/**`，5 个，与上游一一对应）

上游注册（`client/init/ClientSetupEvent.java:55-62`）：

```java
event.registerAboveAll("tac_gun_hud_overlay", new GunHudOverlay());
event.registerAboveAll("tac_heat_bar", new HeatBarOverlay());
event.registerAboveAll("tac_kill_amount_overlay", new KillAmountOverlay());
event.registerAbove(CROSSHAIR.id(), "tac_interact_key_overlay", new InteractKeyTextOverlay());
event.registerAbove(CROSSHAIR.id(), "tac_display_case_lock_hint", new DisplayCaseLockHintOverlay());
```

本仓映射（`client/init/ClientSetupEvent.java:402-435 / 499-533`）：
`registerAboveAll → RenderGameOverlayEvent.Post(ALL)`、`registerAbove → Post(CROSSHAIRS)`。
**`registerAboveAll` 的映射等价；`registerAbove` 的映射有缺陷（§五 F1）**。

| 移植文件 | 上游文件 | 结论 | 说明 |
|---|---|---|---|
| `GunHudOverlay.java`（256 行） | 同名（194 行） | `≡*` | 门条件（`GUN_HUD_ENABLE` / `IClientPlayerGunOperator` / 主手 `IGun` / gunData+display 非空）一致；`useInventoryAmmo`/`useDummyAmmo`/`overheatLocked`、弹药数取整与 `MAX_AMMO_COUNT` 上限、颜色规则（红/青/黄/白，`0xFF5555` 阈值同式）、`PERCENT` 与普通 `DecimalFormat("000")`、备弹 `"0000"`+`∞`、1 s 缓存 `checkAmmoTimestamp`、竖线/字体 1.5×/0.8×/0.5× 布局与全部坐标常量、HUD 图标 39×13 / 空弹变红 / 开火模式图标位置与 `switch(fireMode)` 全部一致；`resetTimestamp` 一致。平台替换：`Font#drawString(float)` → mcshim 的 float 重载内部取整（**降级**：1.7.10 `FontRenderer` 只吃整数坐标，最多 1 px 亚像素差）；版本号 `SharedConstants.getCurrentVersion().getName()` → `Loader.MC_VERSION`、`ModList…versionString()` → `Loader.getIndexedModList().get(modId).getVersion()`；`RenderSystem.setShader(GameRenderer::getPositionTexShader)` 删除（固定管线）。 |
| `HeatBarOverlay.java`（143 行） | 同名（105 行） | `≡*`（本轮小修） | 门条件、`heatScale` 自平滑（`+/0.05`、`-/0.025`、吸附近）、`percent/scaleValue` 公式、`renderOverheat` 的 bar `fill` 矩形、`heat_base` blit 128×128 @ `(w/2-64, h/2-44)`、`!OVERHEAT!` 闪烁、`DecimalFormat("0.0%")`、`getHeatColor` 的 0.4/0.65 分段、`FastColor.ARGB32.lerp` 调用点全部一致。**本轮修复**：内联的 `lerpArgb` 用 `(int)` 截断，1.20 `Mth.lerpInt` 是 `start + floor(delta*(end-start))`（负增量时会差 1）→ 改为 `Mth.floor`（§四.2）。降级：`gui.getGuiTicks()` → `mc.ingameGUI.getUpdateCounter()`（同为 GUI tick 计数）；`fontFilterFishy` → `fontRenderer`（1.7.10 只有一套字体）。 |
| `KillAmountOverlay.java`（91 行） | 同名（85 行） | `≡` | 门条件、超时 `KILL_AMOUNT_DURATION_SECOND`、`"☠ x 0N"` 文本格式、`fadeOutTime=timeout/3*2` 与 alpha 淡出、`Mth.hsvToRgb(hue,0.75,1)+alpha<<24`、`pose.scale(0.5)` 与坐标 `(int)(width-fontWith/2.0f)`、`(height-45)*2-1`、`markTimestamp` 的窗口内累加/窗口外归零、`resetTimestamp` 全部一致。 |
| `InteractKeyTextOverlay.java`（201 行） | 同名（123 行） | `≡*`（本轮注释纠正） | 门条件 `DISABLE_INTERACT_HUD_TEXT`、`objectMouseOver` 的 BLOCK/ENTITY 分支、弹药箱（需持枪）、枪械合成台（持枪 vs 手拿配件/弹药两种提示 + `AUTO_SELECT_GUN_SMITH_TABLE_FILTER`）、展示柜（需持枪、未上锁、命中格判定、格内有枪或手上可放）、实体交互（需持枪）、居中标题 `gui.tacz.interact_key.text.desc` + 可选的灰色副标题、颜色 `0xFFFF55/0xAAAAAA` 全部一致。平台替换：`KeyMapping#getTranslatedKeyMessage` → `Keyboard.getKeyName(keyCode)`；`Component` → `IChatComponent`；`GunDisplayCaseSlotHelper#getSlot(BlockState, BlockPos, Vec3)` → `getSlot(metadata, x,y,z, hitVec)`（签名已落地，旧 TODO 注释过期，本轮已改）。**但渲染入口被 §五 F1 压制，持枪时实际不显示**。 |
| `DisplayCaseLockHintOverlay.java`（84 行） | 同名（43 行） | `≡*` | `DISPLAY_TICKS=40`、`Y_OFFSET=32`、黄色 `#FFFF55`（`ChatFormatting.YELLOW.getColor()`）、居中于 `h/2+32`、`ticksRemaining<=0` 早退、`show(Component)` 重置 40 tick 全部一致。平台替换：静态 `@Mod.EventBusSubscriber` tick → 实例 `@SubscribeEvent` + `ClientSetupEvent` 注册到 FML 总线（FML `ASMEventHandler` 只回调实例方法）；`Component` → `IChatComponent`；**移植兜底**：`expireAt` 墙钟（`40*50ms`）与 tick 计数取先到者（历史 tick 未注册导致「提示不消失」的兜底，保留；见 §六）。 |

### 3.3 rangefinder（`client/resource/pojo/display/gun/**` + 功能渲染器）

| 移植文件 | 上游文件 | 结论 | 说明 |
|---|---|---|---|
| `Rangefinder.java`（48 行） | 同名（46 行） | `≡` | 5 个 `@SerializedName` 字段与默认值（512/2/false/#FFFFFF/1.0）、`getMaxDistance<0→512`、`getRefreshTick<=0→2`、`getImpactPredictionColor`（trim/补 `#`/`0xFF000000|ColorHex`）、`getImpactPredictionScale<=0→1` 全部一致。 |
| `TextShow.java`（67 行） | 同名（65 行） | `≡` | `scale/align/shadow/color/light/text/rangefinder/colorInt` 字段与默认值一致；`setColorInt` 的 volatile 写法一致。 |
| `RangefinderManager.java`（319 行） | 同名（270 行） | `≡*`（本轮修复 2 处） | 门条件/缓存（`refreshTick*50ms` + `lastRefreshTick`）/`MISS_TEXT="---"`/`ENTITY_XZ_EXPAND=0.25`/`ENTITY_Y_EXPAND=8.0`/遮挡与实体取最近/`setDistanceText` 补零/`clearResult`/落点预测（`calculateDrop` 逐行、`getAmmoSpeed`、`getCurrentFov` 的 `AdsFov.Channel` + `CameraSetupEvent.WORLD_FOV_DYNAMICS`）/`shouldRenderImpactPrediction` 逐行一致。**本轮修复**：① 方块射线改回上游同构的 `BlockRayTrace.rayTraceBlocks(World, Vec3, Vec3)`（原来是 `World#func_147447_a` 的「占位降级」，会漏掉「可穿透方块 / `bullet_ignore` 方块」过滤）；② 实体箱改回 `HitboxHelper.getFixedBoundingBox(entity, player)`（原来是 `entity.boundingBox` 的「占位降级」，会漏掉蹲伏/速度修正与延迟补偿扩展）。详见 §四.1。降级：`Entity#isPickable/isSpectator/isAlive` → `!isDead && canBeCollidedWith()`（1.7.10 没有这三个方法；`isSpectator` 无对应，1.8+ 才有）；`new AABB(start,end).inflate(...)` → `AxisAlignedBB.getBoundingBox(min/max ± expand)`；`isPassengerOfSameVehicle` → `ridingEntity` 比较；`Minecraft#options.fov().get()` → `gameSettings.fovSetting`；`mc.getFrameTime()` → `ClientTickHelper.getPartialTick()`。 |
| `RangefinderImpactPredictionRender.java`（219 行） | 同名（144 行） | `≡*` | 类/常量（`MODEL_TEXT_SCALE=2/300`、`FALLBACK_MODEL_PROJECTION_HALF_HEIGHT=80`、`HALF_SIZE=3`、`LINE_WIDTH=1`、`FULL_BRIGHT`）、`firstPerson() && shouldRenderImpactPrediction()` 闸、`pose.mulPose(ZP 180°)` → `rotationZ(π)`、normal/pose 快照 → `delegateRender` → `poseStack2` 复制矩阵 → `scale`、`getCalibratedYOffset`（目标 GUI y = `h/2 + projectionY*h/2`，用投影矩阵反解 local Y 并 clamp ±1000）、`renderBox` 四条边 + ARGB 拆解 全部一致。降级：`RenderType.create(...COMPOSITE with ADDITIVE/NO_CULL/VIEW_OFFSET_Z/LIGHTMAP/LASER_BEAM_TEXTURE)` → 固定管线手工复现（`glBlendFunc(SRC_ALPHA, ONE)` + `glPolygonOffset(-1,-10)` + 关剔除 + `Tessellator.setBrightness(FULL_BRIGHT)`，成对 finally 还原）；`RenderSystem.getProjectionMatrix()`（JOML `Matrix4f` CPU 缓存）→ mcshim 返回 GL 投影矩阵 `FloatBuffer` 快照，列主序手算点积。 |

### 3.4 交叉只读核对（不在本人写作用域，供交叉参考）

| 文件 | 上游 | 结论 |
|---|---|---|
| `client/model/functional/TextShowRender.java` | 同 | `≡*+降级`：`!firstPerson()` 闸、`RangefinderManager.setCurrentConfig/getTextShow/clearCurrentConfig` 的 try/finally、`textShow` 的 scale/align/shadow/color/light、`mulPose(ZP 180°)`、normal/pose 快照、`delegateRender` 代理、`2/300*scale` 缩放一致；`Font#drawInBatch` → `pushToGL + drawString`（1.7.10 字体只能立即模式 + 整数坐标），lightmap 从逐顶点降级为全局 `setLightmapTextureCoords`。r7-model 报告已核（`model.md:116`），本模块只确认「它确实只在第一人称被调用」。 |
| `client/model/BedrockGunModel.java` / `BedrockAttachmentModel.java` | 同 | `≡`：`setTextShowList` 里 `hasRangefinder && modelMap.containsKey("rangefinder_impact_prediction_pos")` → 注册 `RangefinderImpactPredictionRender` 的逻辑逐行一致（枪模型与配件模型两处都在）。 |
| `client/model/papi/{PapiManager,RangefinderPapi}.java` | 同 | `≡`：`%rangefinder%` 占位符注册与替换、缺 key 返回 key、不做 `String.format`。 |
| `client/init/ClientSetupEvent.java`（HUD 注册/渲染、`onRenderOverlay`） | 上游 `RegisterGuiOverlaysEvent` | 见 §五 F1/F2（平台作用域）。 |
| `client/ClientProxy.java` | 1.20 自动注册 | 本模块三个事件类的总线归属正确：`RenderCrosshairEvent` 双总线（Forge：`RenderGameOverlayEvent`；FML：`RenderTickEvent`）、`ClientHitMark` Forge、`ClientGunTimeResetEvent` FML。 |
| `client/event/PreventsHotbarEvent.java` | 同 | 见 §五 F3（input 作用域）。 |

---

## 四、本轮修复

### 4.1 `RangefinderManager`：恢复上游同构的方块/实体命中（2 处）

修复前是两处「占位降级」，理由注释写的是「util 工作流签名尚未落地」；这两个 util 现在已经落地且签名与上游语义一一对应，
继续用占位实现会产生**真实行为差异**（测距值不同）：

| 位置 | 修复前 | 修复后 | 差异后果 |
|---|---|---|---|
| 方块射线 | `player.worldObj.func_147447_a(start, end, false, true, false)` | `BlockRayTrace.rayTraceBlocks(player.worldObj, start, end)` | 原实现不认 `AmmoBlockConfigRead.canPassThrough`（可穿透方块）与 `bullet_ignore` 类方块（铁栏杆/栅栏/树叶等），会把这些方块当遮挡物 → 测距偏短、实体命中提前截断。上游 `BlockRayTrace.rayTraceBlocks(level, ClipContext(COLLIDER, NONE))` 会过滤。 |
| 实体命中箱 | `entity.boundingBox.expand(0.25, 8.0, 0.25)` | `HitboxHelper.getFixedBoundingBox(entity, player).expand(0.25, 8.0, 0.25)` | 原实现漏掉上游的蹲伏高度扩展（0.0625）、按实体速度的 look-back 扩展、`ITargetEntity`/载具的 -2.5×速度偏移、以及玩家延迟补偿；对移动目标/骑行目标测距偏差明显。 |

同时把两段过期 TODO 注释压成「平台替换说明」（实际调用已与上游同构）。
`BlockRayTrace` 与 `HitboxHelper` 均为 `util` 公共类，签名与上游调用点一致，未改它们本身。
依据：上游 `RangefinderManager.java:144-168`；本仓 `util/block/BlockRayTrace.java:115-129`、`util/HitboxHelper.java:118-156`。

### 4.2 `HeatBarOverlay.lerpArgb`：取整语义对齐 1.20

1.20 `FastColor.ARGB32.lerp` = 逐通道 `Mth.lerpInt(delta, start, end)`，而 `Mth.lerpInt` 是
`start + floor(delta * (end - start))`（`tmp/minecraft-src-1.20.1/.../FastColor.java:63-68`、`Mth.java:484-486`）。
移植版内联实现用 `(int)` 截断；当通道增量为负（如 `0x9FFFFF00` 蓝通道）时与 `floor` 差 1。
改为 `Mth.floor(...)`（mcshim 的 `floor(float)` 委托 `MathHelper.floor_float`），颜色逐值对齐。

### 4.3 `InteractKeyTextOverlay`：删除过期注释

旧注释称「`GunDisplayCaseSlotHelper#getSlot` 的 1.7.10 签名未落地 ⇒ 已降级为只判断手上物品」。实际上 r6/block 工作流
已把 helper 落地为 `getSlot(metadata, x, y, z, hitVec)`，**代码本身已经在调用它并读取柜内物品**（`:130-144`），
注释与代码矛盾。已改为按签名对照描述。

---

## 五、跨模块发现（已由对应作用域修复 / 待复核）

> **状态更新（2026-11-05）**：F1/F2 已由 **r7-platform** 在 `client/init/ClientSetupEvent` 修复；F3 已由 **r7-input**
> 在 `client/event/PreventsHotbarEvent` 修复。本节保留「发现时的原始分析 + 建议修法」，并补充实际落地方式；
> 最终以工作区当前代码为准。

### F1（**已被 r7-platform 本轮修复**，`client/init/ClientSetupEvent`）`registerAbove(CROSSHAIR)` 层在持枪时永不渲染

- **现象**：主手拿枪（`IGun.mainHandHoldGun`）时，`InteractKeyTextOverlay` 与 `DisplayCaseLockHintOverlay` 完全不显示。
  前者恰好在「持枪对着弹药箱 / 展示柜 / 枪械合成台 / 可交互实体」这些主场景下需要显示（其门条件就是 `mainHandHoldGun`），
  所以等于该 HUD 功能大部分不可用；后者由 O 键在展示柜上触发时（`InteractKey` 要求持枪）也不显示。
- **根因**：本仓把 `registerAbove(CROSSHAIR.id(), …)` 映射到 `RenderGameOverlayEvent.Post(CROSSHAIRS)`
  （`ClientSetupEvent.java:414-435 / 502-533`）。但 1.7.10 `GuiIngameForge.renderCrosshairs` 是
  `if (pre(CROSSHAIRS)) return; … post(CROSSHAIRS);`（`GuiIngameForge.java:218-228`），
  而 `RenderCrosshairEvent` 持枪时必然 `event.setCanceled(true)`（上游语义：替换原版准星）⇒ `pre()` 返回 true ⇒
  **`Post(CROSSHAIRS)` 根本不会发出**。上游 1.20 不存在该耦合：`ForgeGui.render` 对**每个 layer 单独** post
  `RenderGuiOverlayEvent.Pre`（`ForgeGui.java:118-132`），取消 CROSSHAIR 这一层不影响其它层。
- **实际落地（r7-platform）**：`Post(CROSSHAIRS)` 保留给「未取消」路径（层序 = 原版准星之后），另加
  `@SubscribeEvent(receiveCanceled = true, priority = EventPriority.LOWEST)` 的 `onRenderOverlayPre`：
  **仅在 `event.isCanceled()` 时**补画 `aboveCrosshair`（LOWEST 保证晚于 `RenderCrosshairEvent` 的 NORMAL 替换准星），
  因此两种路径都不重绘、层序均等价上游。`ClientSetupEvent.java:510-546`（r7-platform 最终版，已按本报告建议
  删掉 Post 分支里 `event.isCanceled()` 的死 guard——1.7.10 Post 不可取消；以工作区为准）。
- 影响文件：`src/main/java/com/tacz/guns/client/init/ClientSetupEvent.java`（r7-platform 已改）。

### F2（**已被 r7-platform 本轮修复**，`client/init/ClientSetupEvent`）HUD 在「有 GUI 打开」时不渲染

- 原实现：`onRenderOverlay` 有 `if (mc.currentScreen != null) return;`（旧 `ClientSetupEvent.java:508`）。
  上游 1.20 是 `if (!options.hideGui || screen != null) gui.render(...)`（`GameRenderer.java:1121-1125`），
  1.7.10 同条件（`EntityRenderer.java:1111-1115`）⇒ **GUI 打开时 HUD 层仍然渲染（在 GUI 之下）**；
  旧实现直接跳过 ⇒ 透明 GUI（聊天屏等）后看不到 TACZ HUD。
- **实际落地（r7-platform）**：抽出 `renderHudLayers(...)`，门条件改为
  `if (mc.gameSettings.hideGUI && mc.currentScreen == null) return;`（逐字对齐引擎语义，`ClientSetupEvent.java:548-570`，
  以工作区为准）。
- 影响文件：同上（r7-platform 已改）。

### F3（**已被 r7-input 本轮修复**，`client/event/PreventsHotbarEvent.java`）取消范围不足

- 上游 `PreventsHotbarEvent` **不判断 overlay id**，在 `GunSmithTableScreen/GunRefitScreen` 打开时取消
  **所有** overlay layer（`PreventsHotbarEvent.java:14-27`）；本仓原先只取消了 `ElementType.HOTBAR`
  ⇒ 枪械合成台/改装界面背后，原版生命/饥饿/经验等 HUD 仍会绘制（上游是全隐藏）。
- 修法（r7-input 已落地，2026-11-05 反馈）：去掉 `event.type == ElementType.HOTBAR` 判断，对每个
  `RenderGameOverlayEvent.Pre` 直接 `setCanceled(true)`（第一个是 `Pre(ALL)`，`GuiIngameForge.java:105`
  整体短路，等价上游「全部 overlay layer 被取消」），类 javadoc 同步改为「隐藏全部原版 HUD（不止快捷栏）」。
  本报告复核时请以工作区当前 `PreventsHotbarEvent.java` 为准。
- 影响文件：`src/main/java/com/tacz/guns/client/event/PreventsHotbarEvent.java`（r7-input 作用域，已修）。

### F4（信息，r7-fp / r7-tp）截图里的「第一人称 minigun 模型 + 玩家身体同屏」

- 正常 1.7.10 的两条互斥门控：① 第一人称**不渲染**本地玩家身体（`RenderGlobal.java:524`：
  `entity != mc.renderViewEntity || thirdPersonView != 0 || isPlayerSleeping()` 才渲染）；
  ② 第三人称**不渲染**第一人称手模（`FirstPersonRenderHandler.java:428-430` 判 `thirdPersonView != 0` 返回）。
  截图同时出现「角色身体」与「底部巨大、多管、枪口朝向相机的 minigun 手模」⇒ 上述门控之一被破坏，
  属第一人称/第三人称渲染作用域，不是准星/HUD。
- 本模块只声明：**HUD 元素（热条/准星/命中标记）在该场景下行为与上游一致**，不是本次「0.0m」问题的原因。
- 另外：截图里 `0.0%` 是**上游同构行为**（minigun 是默认包唯一启用 heat 的枪；`hasHeatData` 只看 NBT 是否有
  `HeatAmount` 浮点，开过火后一直有 ⇒ 冷却到 0 仍显示 `0.0%`）。这是上游自身设计（不修）。

---

## 六、未修与有据降级

| 项 | 位置 | 说明 |
|---|---|---|
| 旁观者模式判断省略 | `RenderCrosshairEvent`、`GunHudOverlay`、`HeatBarOverlay`、`KillAmountOverlay`、`ClientHitMark`、`RangefinderManager` | 1.7.10 无旁观者模式（1.8+ 才有）。上游对应分支无从表达。 |
| action bar → 聊天栏 | `ClientHitMark` 标靶车伤害提示 | 1.7.10 无 action bar API（`displayClientMessage(..., true)` 属 1.8+）。 |
| 字体亚像素坐标 | `GunHudOverlay` / `KillAmountOverlay` / `InteractKeyTextOverlay` | 1.7.10 `FontRenderer` 只接受整数坐标（mcshim 的 float 重载内部取整），1.20 Forge 的 `drawString(float,…)` 是逐顶点矩阵，最多 1 px 差异。 |
| `fontFilterFishy` → `fontRenderer` | `HeatBarOverlay` | 1.7.10 只有一套字体渲染器。 |
| `RenderType` → 固定管线 | `RangefinderImpactPredictionRender.renderBox` | 1.7.10 无 RenderType/ShaderState；已按上游 RenderType 的 GL 语义手工复现并成对还原（混合/多边形偏移/剔除/lightmap）。**实机需确认落点框与 Y 轴落点位置**（见 §七 V-3）。 |
| 投影矩阵读取方式 | `RangefinderImpactPredictionRender.projectGuiY` | 上游读 CPU 侧 JOML 投影；本仓读 GL 投影矩阵 `FloatBuffer` 快照（列主序点积）。1.7.10 第一人称模型渲染用的是「手部 pass 投影」（SBM 自建，FOV=ITEM_MODEL_FOV），与 1.20（世界投影）不同 ⇒ `getCalibratedYOffset` 的「自校准」结果需要实机验证（§七 V-3）。 |
| `DisplayCaseLockHintOverlay.expireAt` 墙钟兜底 | `DisplayCaseLockHintOverlay` | 移植特有：tick 注册曾缺失导致提示不消失，现在 tick 已注册（`ClientSetupEvent`），墙钟兜底与 tick 计数取先到者 ⇒ 客户端卡顿时提示可能比上游早消失（最多 2 s 墙钟 vs 40 tick）。保留以避免回归。 |
| 1.7.10 原版准星在第三人称的可见性 | 平台差异 | 1.7.10 vanilla 的 `GuiIngame.renderGameOverlay`/`GuiIngameForge.renderCrosshairs` 不判 `thirdPersonView`；1.20 `Gui.renderCrosshair` 判 `isFirstPerson()`。TACZ 持枪时两侧都会取消/隐藏，但**空手**时 1.7.10 第三人称原版准星仍可见——这是原版平台差异，非 TACZ 移植偏离。 |
| 上游未使用字段/形参 | `RangefinderManager.hitDistance`、`getAmmoSpeed(..., stack)` | 上游本身如此（`hitDistance` 只写不读、`stack` 形参未用），保持签名逐行一致，不修。 |

---

## 七、实机验证清单（按可判读性排序）

| # | 项 | 步骤 / 判读 |
|---|---|---|
| **V-1** | **准星替换** | 主手持任意枪（默认 `DOT_1`）：第一人称屏幕中心应是 `textures/crosshair/normal/dot_1.png`（不再是原版十字）；空手/非枪物品恢复原版准星；第三人称无准星；F1（隐藏 GUI）无准星；按住右键完全开镜后准星消失（`aimingProgress>0.9`）；`AP` 类枪包若 `isShowCrosshair=true` 则开镜仍显示。 |
| **V-2** | **命中反馈** | 击中普通生物：中心四角白色标记 300 ms 内出现并淡出；爆头：红色标记 + 爆头音效；击杀：标记外扩 4 px 位移 + 连杀数 `☠ x 01` 在 3 s 内淡出。击杀后切枪/断线重连不应残留（`ClientGunTimeResetEvent`）。 |
| **V-3** | **rangefinder 距离 + 落点预测**（实机最需要） | 装 `scope_xm157`（`show_impact_prediction=true`、`max_distance=512`）→ 完全开镜：① 对着 10/50/200 格方块应在镜内显示 `010`/`050`/`200` 左右（按位数补零）；② 对准天空显示 `---`；③ 对准生物显示整数距离（走动时应连续刷新，刷新率 = `refresh_tick`）；④ 开镜对远处放一实体，镜内应出现彩色落点框（Y 轴随距离下降）。**判读要点**：距离与 `/execute` 或方块坐标手算对比，偏差 >1 格即说明 `BlockRayTrace/HitboxHelper` 或 `calculateDrop` 仍有问题；落点框明显偏上/偏下则看 `getCalibratedYOffset`（本仓手部投影与 1.20 世界投影不同）。 |
| **V-4** | **HUD 基础** | 持枪：右下角弹药数 `000`（1.5×）/备弹 `0000`（0.8×）/竖线/HUD 枪图标/开火模式图标/版本号（0.5×）位置与上游截图一致；空弹时图标变红；`GUN_HUD_ENABLE=false` 时全部消失。 |
| **V-5** | **热条（本截图场景）** | 持 minigun 开火至接近过热 → 镜外屏幕中心偏下应出现 `heat_base` 刻度 + 填充条 + `xx.x%`，过热锁定显示 `!OVERHEAT!` 红/黄闪烁；冷却到 0 后仍显示 `0.0%`（上游行为）。**注意**：首次拿到 minigun（未开火）时应**不显示**热条（`hasHeatData` 依赖 NBT）；若一拿到就显示，说明 NBT 初始化被移植侧多写了 `HeatAmount`，需查 item 初始化（r7-item）。 |
| **V-6** | **F1 修复验证（r7-platform 已改）** | 持枪对准：① 弹药箱 → 应显示「按 O 交互」+ 灰色副标题；② 枪械合成台 → 「按 O」/「按右键」提示；③ 展示柜（未锁）→ 「按 O」；④ 手持配件/弹药对枪械合成台 → 「按 右键」提示。游标在屏幕中心偏上 25 px。**另需确认不重绘**（持枪/空手各看一次，文字不应出现双份/加粗）。 |
| **V-7** | **连杀/HUD 与 GUI 叠加（F2 修复验证，r7-platform 已改）** | 打开聊天屏（透明背景）：TACZ HUD（热条/弹药数）应仍可见（上游行为）；打开背包：HUD 在 GUI 之下（被背景遮住即可）；F1 隐藏 GUI 时全隐藏。 |
| **V-8** | **F3 修复验证（r7-input 已改）** | 打开枪械合成台/改装界面：生命/饥饿/经验/快捷栏等原版 HUD 全部不可见（上游行为）。 |

---

## 八、改动文件 / 检查结果

1. `src/main/java/com/tacz/guns/client/resource/pojo/display/gun/RangefinderManager.java`
   - 方块射线 → `util/block/BlockRayTrace.rayTraceBlocks`；实体命中箱 → `util/HitboxHelper.getFixedBoundingBox`；
     删除两段「签名未落地」的过期 TODO，改为上游对照说明。
2. `src/main/java/com/tacz/guns/client/gui/overlay/HeatBarOverlay.java`
   - `lerpArgb` 逐通道取整改为 `Mth.floor`（对齐 1.20 `FastColor.ARGB32.lerp` / `Mth.lerpInt`）。
3. `src/main/java/com/tacz/guns/client/gui/overlay/InteractKeyTextOverlay.java`
   - 过期 TODO 注释纠正（`GunDisplayCaseSlotHelper#getSlot` 已落地）。

检查：

- `mcp__idea__lint_files(min_severity="error")`：上述文件 + 本作用域全部 13 个文件 = **0 error**；
- `mcp__idea__get_file_problems`：仅剩与上游同构的警告（`RangefinderManager.hitDistance` 只写不读、`getAmmoSpeed` 形参未用；`HeatBarOverlay` 与 `GunHudOverlay` 的重复门代码），**均与上游一致，不修**；
- 本作用域 `[TACZ-DBG]` / 每帧 `LOGGER` 日志 = **0 处**（`GunHudOverlay` 的 `debugInfo` 是上游 HUD 上的版本号文本，不是日志）；
- **未运行 gradle**；`tmp/**`、`build/**`、`run/**`、`src/main/resources/**` 均未改动；
- 截图证据图：`docs/review/r7/screenshots/crosshair_rangefinder_evidence.png`（本任务生成）。
