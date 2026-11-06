# r8 · 实机反馈 6 问题修复报告（索引）

任务来源：用户实机反馈（MC 1.7.10 / Forge 10.13.4.1614 移植版，`tacz-5.11.45.140`）：

| # | 问题 | 根因 | 修复 | 详细报告 |
|---|---|---|---|---|
| 1 | Iron Ammo Box 系列贴图错误 | 1.20 `RenderShape.MODEL` 的方块在移植里没有等价渲染；且实机环境里 mod-jar 资源包读贴图异常 | **改用环境常驻的 GTNHLib JSON 模型系统**（`registerModid` + blockstate 属性注册 + `ModelISBRH`），删除自研渲染器 | [block.md](block.md) 顶部「最终方案」 |
| 2 | 标靶矿车浮空、只有下半可命中；人形标靶无碰撞/渲染异常 | ① 矿车 `yOffset=height/2` 把命中盒压低；② 渲染漏算两版帧原点差 0.4375；③ `BlockMetadata` facing 编码错位 | `yOffset=0.4375`、渲染 translate 1.4375、facing 编码改为 1.20 `get2DDataValue` | [target.md](target.md) |
| 3 | 武器盒/钻石弹药箱贴图错误（工作台正常） | 同 #1；工作台/雕像/标靶是 BER 所以正常 | 同 #1（GTNHLib JSON 模型 + BER 方块显式 opt-out） | [block.md](block.md) 顶部「最终方案」 |
| 4 | tooltip 过长不换行、越出屏幕 | 1.7.10 `GuiScreen#drawHoveringText` 不折行 + 无左/上 clamp | `TooltipEvent` 整表折行 + 新增 `GuiScreenMixin`/`GuiTooltipRenderer` clamp | [gui.md](gui.md) §2 |
| 5 | 方块放置朝向异常 | `BlockMetadata.facing2D/facing` 用了 ForgeDirection ordinal 顺序，与 1.20 `Direction#get2DDataValue()` 不一致 | 改为 SOUTH=0/WEST=1/NORTH=2/EAST=3 | [block.md](block.md) §2 |
| 6 | 创造栏左侧两个无用按钮 | 点击链路正常；安装只有 1 个内容包 ⇒ 筛选不改变列表（上游 1.20 单包同样） | 单包时隐藏整个侧栏（多包保留上游筛选） | [gui.md](gui.md) §1 |

## 平台差异/已知降级（需在后续轮次复核）

1. **方块模型渲染**（[block.md](block.md)）：无平滑光照 AO（逐面亮度 + 面着色系数）；`uvlock`/`weight`/元素非 0 旋转未实现（已核对 TACZ 数据均未使用）。`BlockRenderIds.jsonModel()` 默认 0 ⇒ 客户端初始化前/专用服务器退回原版立方体而非方块消失。
2. **朝向编码**（[block.md](block.md)）：metadata 编码表修正后，**旧存档已放置的方块会反向转一次**（一次性影响，新放置正确）。
3. **tooltip**（[gui.md](gui.md)）：`GuiScreenMixin` 注入 `renderToolTip`/`func_146283_a`/`drawCreativeTabHoveringText`（Forge 新增的 `drawHoveringText` 无混淆映射，Mixin AP 会报 error）；x/y clamp 按 1.20 `DefaultTooltipPositioner` 语义 + 顶部 clamp（1.20 只 clamp 底部）。
4. **创造栏侧栏隐藏**（[gui.md](gui.md)）：上游 1.20 单包仍显示侧栏；本移植按可用性隐藏，多包行为与上游一致。

## 构建

`./gradlew spotlessApply build` = **BUILD SUCCESSFUL**（2026-11-06 22:25，`build/libs/tacz-5.11.45.140.jar`）。

## 实机验证清单

1. 弹药库（铁/金/钻石）、武器盒、枪架（普通/发光）世界模型 = 完整 3D + 贴图正确；武器盒 4×4 拼墙出边框、潜行切锁玻璃消失；丢地上 3D。
2. 工作台/雕像/人形标靶/弹药箱四朝向放置：正面朝玩家（旧存档旧方块反向转一次属预期）。
3. 标靶矿车贴轨不浮空；靶面上沿可命中（抖动/音效/聊天栏 damage+distance）；斜轨 2~5 也检查。
4. 人形标靶四朝向、看得见的面可命中（击倒/音效/红石/提示，5 秒复位）；侧面背面不触发。
5. 长 tooltip（配件/带 lore）鼠标在屏幕左下/右下均完整、自动换行不裁切。
6. 创造栏单包：左侧 ALL/枪包按钮消失、列表滚动/点击正常。
7. 回归：枪械/瞄具/原有方块渲染无异常。
## 启动冒烟验证（Lead，2026-11-06 23:08）

用 `./gradlew runClient` 完整启动一次客户端（主菜单到达后关闭），验证 Mixin/初始化不崩：

- **本次新增的 `GuiScreenMixin` 成功混入** `net.minecraft.client.gui.GuiScreen`，3 个 `@Inject` 全部认到
  `CallbackInfo`（`run/client/logs/fml-client-latest.log` 23:08:04
  `Mixing client.GuiScreenMixin ... into net.minecraft.client.gui.GuiScreen`、
  `tacz$renderItemTooltip/tacz$renderListTooltip/tacz$renderCreativeTabTooltip ... does use it's CallbackInfo`）；
- `mixins.tacz.json` 共 preparing 21 项、无 `Mixin apply failed`；`[TACZ] client setup finished`（23:08:52），
  资源包索引 3409 文件，无 TACZ 异常；无新增 crash-report；
  仅有既有的 `org.joml module-info.class` FML 警告（与本次改动无关）。
- 说明：`CreativeModeInventoryScreenMixin` / `CreativeContainerAccessor` / `AbstractButtonMixin` 的目标类
  （`GuiContainerCreative`、`GuiButton`）**在进入创造模式物品栏时才被加载**，冒烟启动（只到主菜单）未触发
  对应 `Mixing` 日志；这三项在上一次实机会话日志中已确认可正常混入，将在实机回归时再次确认。

剩余为**视觉/交互回归**（方块模型、朝向、标靶、tooltip、创造栏），需实机操作确认。

## 实机验证中发现并修复的阻塞问题（Lead，2026-11-06）

1. **加载世界 HUD 画枪必崩**（P0）：LWJGL2 `glGetFloat/glGetInteger/glGetBoolean` 对 buffer 要求容量 ≥16，
   `RenderType.GlStateSnapshot` 等 4 处用了 4 → 崩溃。已全部改为 16（详见 block.md「Lead 追加修正 2」）。
2. **blockstate/model JSON 在实机启动器下 `ResourcePackFileNotFoundException`**（文件确实在 jar 里，已用 ZipFile 验证），
   原实现失败后方块不渲染 = 「空气方块」。已改为 **classpath 优先读取** + 资源管理器兜底，
   并让 `renderWorldBlock` 失败时退回原版立方体（绝不让方块消失）。
3. **方块图集贴图也来自同一个坏掉的 mod-jar 资源包** ⇒ 22:xx 实机里三个方块仍是「空气贴图」（missingno）。
   新增 `client/resource/ModAssetResourcePack`：用类加载器读 `assets/tacz/**` 并挂进原版资源管理器
   （每轮重载重挂、排在 TextureManager stitch 之前、枪包资源包之后挂 ⇒ 枪包仍优先）。
   该包让方块图集贴图/JSON/其它模组 jar 资源全部恢复；23:46 日志已确认 JSON 修复生效。
3. 实机验证约定：**由用户手动启动验证**（Agent 不再自动操作游戏窗口）。

剩余：上述两项 + 6 个问题的最终实机视觉/交互确认。

### 追加（2026-11-07 实机二轮）

| # | 问题 | 根因 | 修复 | 记录 |
|---|------|------|------|------|
| 7 | 所有弹药盒物品贴图错乱（方块正常后仍存在） | 1.20 弹药盒是 JSON 物品模型（`models/item/ammo_box.json` overrides 按 `tacz:ammo_statue` 选 9 个模型），1.7.10 无此体系，移植早期只给了模型贴图集当平面图标 | 新增 `AmmoBoxItemRenderer`：GTNHLib 烘焙 JSON 模型 + `ItemModelDisplayManager` 帧折算 + 物品 tint/方向光；平面图标保留兜底 | [block.md](block.md) 末尾「补丁：弹药盒」 |

### 追加（2026-11-07 二轮 · 自定义枪包）

| # | 问题 | 根因 | 修复 | 记录 |
|---|------|------|------|------|
| 8 | 自定义枪包（zip）的枪完全不渲染（第一人称空手、第三人称只剩物品图标） | 单人游戏客户端/集成服各自重扫枪包列表，第二次扫描关闭了客户端懒加载器仍持有的 zip 句柄 ⇒ 模型/动画读取返回 null，display 被丢弃 | `GunPackLoader.reloadPackList` 跨扫描复用 `IGunPackSource`（zip 用 mtime+大小判定，替换才重建），不再提前关闭在用句柄 | [gunpack.md](gunpack.md) §1 |
| 9 | 自定义枪包物品名/描述/pack 名显示原始键名（`sk25tkm.gun.s25o.name`） | 1.20 枪包语言文件是 JSON（`lang/zh_cn.json`），移植只解析 1.7.10 `.lang`；且语种码大小写不同（`zh_cn` vs `zh_CN`） | `GunPackLoader` 新增 JSON 语言解析 + 语种码归一化，`.lang`/`.json` 两遍注入 | [gunpack.md](gunpack.md) §2 |
