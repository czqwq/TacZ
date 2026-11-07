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

## 判定「不是 bug」（记录以免下一轮重开）

1. **加密枪包 `Tacz from Tarkov v1.1a` 全部贴图/模型缺失**：该包是 TACZ-Arcana 的「受保护枪包」，模型/贴图/动画/音效在 `recursion/taczpack.dat` 加密容器里；官方 TACZ 1.20.1 本体也读不了，Arcana 明确声明公开源码不含资产保护组件（obfuscated `dist/**` + 原生库）。
   移植侧已改为识别标记后整包跳过 + 单条 warn（[gunpack.md](gunpack.md) §3），**不是移植缺陷**；需换未加密版本枪包。
2. **Applied Armorer 的 `special_melee_task_manager`（进程切·任务管理器）左键无动画/无伤害**：枪包设计为近战专用——
   - `data/applied_armorer/scripts/special_melee_task_manager.lua` 的 `M.shoot(api)` 是**空函数**（作者故意覆盖默认射击逻辑 ⇒ 左键不发射子弹、无伤害）；
   - 该枪的 animation 只有 `static_idle/idle/draw/inspect/melee_stock/melee_bayonet_1~3/put_away/移动系`，**没有射击动画**；display sounds 也只有 `melee_stock`/`melee_bayonet`；
   - 攻击走 TACZ 近战键（默认 **V**，控制 → 按键绑定 → TACZ 分类可改；瞄准时 V 是倍率切换）：
     数据 `melee.distance=3 / cooldown=0.6 / default{animation_type:"melee_stock", damage:13, prep:0.15}` 即近战范围与伤害。
   上游 1.20.1 行为相同，**不是移植缺陷**。
   - **附加说明（2026-11-07 实机四轮）**：V 近战「客户端动画/音效正常但怪物零伤害」曾是**真的移植 bug**
     （1.7.10 `Vec3#subtract` 反向减法导致近战锥形判定镜像），已修，见本节「追加（实机四轮）」#12；
     左键无伤害仍属枪包设计（`M.shoot` 空函数）。
3. **芙宁娜枪包 `script 'furina:furina_book_logic' not found`**：枪包 zip 内 `data/furina/scripts/` 只有 `furina_bow_logic.lua` / `furina_sword_logic.lua`，但 `data/furina/data/guns/furina_book_data.json` 引用了不存在的 `furina_book_logic` ⇒ 枪包数据缺文件，非移植问题。
4. **Applied Armorer 的 `special_melee_task_manager` 用左键开火时刷 `NumberIsTooSmallException: number of points (2)`**（`org.apache.commons.math3` 的 `SplineInterpolator`，栈：`GunRecoil.getSplineFunction` ← `genPitchSplineFunction` ← `CameraSetupEvent.initialCameraRecoil` ← `GunFireEvent`）：
   - 该枪 `recoil.pitch` / `recoil.yaw` 各只有 **1 个关键帧**（`{"time":0,"value":[0,0]}`）；`GunRecoil#getSplineFunction` 会补一个 `(0,0)` 点再做样条，实际只有 **2 个点**，而 commons-math3 的 `SplineInterpolator` 要求 **≥3 点** ⇒ 必然抛异常。
   - 上游 1.20.1 的 `GunRecoil.java` 与本移植**逐字相同**、`CameraSetupEvent#initialCameraRecoil` 同样不判长度 ⇒ **上游也会抛，属枪包数据不合法，不是移植缺陷**（异常被 Forge 事件总线捕获，仅日志刷屏 + 后坐力不生效）。
   - 移植侧仍加了防御：`getSplineFunction` 在 `keyFrames.length < 2` 时与空数组一样返回 `null`（消费方 `applyCameraRecoil` 已判空，合法数据行为不变）。

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
| 10 | 加密枪包（`Tacz from Tarkov v1.1a`）100 个物品全部无模型/贴图，日志刷 `Failed to load texture: eft:textures/...` + `there is no corresponding model file` | 该包是 **TACZ-Arcana 的受保护枪包**：明文只有 data/lang/display，模型/贴图/动画/音效在 `recursion/taczpack.dat` 加密容器里；官方 TACZ 1.20.1 本体也没有该能力（Arcana 明确声明公开源码不含资产保护组件，且该组件是 obfuscated `dist/**` + 原生库）⇒ **1.7.10 无法也不应逆向支持** | `GunPackLoader` 扫描时识别 `recursion/taczpack.dat` → 整包跳过 + 单条明确 warn（不再刷几百行缺失异常）；该包已移到 `tmp/eft-protected-pack/` 待找到未加密版本 | [gunpack.md](gunpack.md) §3 |
| 11 | 第三方枪包 27 条配方 `Unknown item 'heart_of_the_sea' / 'blue_dye' / 'netherite_ingot' / ...` 被整条跳过 | 1.20 用 1.13 扁平化物品 id（`blue_dye`/`oak_planks`/`firework_rocket`），1.7.10 是扁平化前命名（`dye`+damage 等）；且上游对未知注册名的语义是解析成 **AIR 材料**（配方保留但无解），不是跳过整条配方 | 新增 `util/VanillaItemMapping`（1.20 id → 1.7.10 注册名+damage 等价折算，染料/羊毛/木头/改名原版物品等）；`OreDictionaryFallback#findItemStack`、`ItemStackJsonParser`、`LootTableInjectorModifier` 统一走该表；`GunSmithTableIngredient` 扩充材质替代表并把「仍无解」改成上游 AIR 语义（配方照常登记、材料无解、debug 去重） | [gunpack.md](gunpack.md) §4 |

### 追加（2026-11-07 实机四轮 · 枪械近战）

| # | 问题 | 根因 | 修复 | 记录 |
|---|------|------|------|------|
| 12 | Applied Armorer 的 `special_melee_task_manager`（进程切·任务管理器）按 V 近战打怪**完全没伤害、怪物无受击反馈**（客户端动画/音效正常、服务端 prep 计数也正常） | **1.7.10 `Vec3#subtract` 语义与 1.8+ 相反**：`a.subtract(b)` 返回的是 `b - a`（`build/rfg/minecraft-src/java/net/minecraft/util/Vec3.java:55-61` javadoc「Returns a new vector with the result of the specified vector minus this」）。`ModernKineticGunItem#doMelee` 照搬上游 1.20.1 的 `userEye.subtract(eyeVec)` / `targetEye.subtract(centrePos)`，在 1.7.10 实际得到 `eyeVec - userEye` / `centrePos - targetEye` ⇒ 近战球心落到**身前 8 格**、锥形判定整体镜像（实测 `/tacz debug true` 下所有 candidate 的 degree 在 121°~145°，halfRangeAngle=15°） | `ModernKineticGunItem#doMelee` 两处向量差改为按分量显式计算（`userEye - eyeVec`、`targetEye - centrePos`），并加注释说明该语义坑；同时修掉同根因的 `GunMeleeDebug#showRange`（调试火焰锥方向）与 `EntityUtil#getHitResult` 的 `hitBoxPos`（爆头判定局部坐标反号 ⇒ 爆头 AABB/默认眼高判定一直是错的） | 本节 + `docs/PORT_STATUS.md`「r8 追加 · 枪械近战/后坐力」 |
| 13 | 左键开火刷 `NumberIsTooSmallException: number of points (2)`（`org.apache.commons.math3` `SplineInterpolator` ← `GunRecoil.getSplineFunction` ← `CameraSetupEvent.initialCameraRecoil` ← `GunFireEvent`） | 该枪 `recoil.pitch`/`recoil.yaw` 各只有 1 个关键帧（`GunRecoil` 补 `(0,0)` 后 2 点，commons-math3 要求 ≥3 点）。上游 1.20.1 同实现同样会抛 ⇒ **枪包数据不合法，非移植缺陷**（异常被 Forge 事件总线捕获，仅日志刷屏 + 后坐力不生效） | `GunRecoil#getSplineFunction` 对 `keyFrames.length < 2` 与空数组同样返回 `null`（消费方 `applyCameraRecoil` 已判空；合法数据行为不变） | 「判定不是 bug」§4 |
| 14 | 近战链路新增诊断设施（用于本轮定位，保留为调试能力） | 客户端 / 网络 / 服务端任一步静默失败时表现完全相同（按 V 无伤害），静态无法区分 | `/tacz debug true` 门控的 `[TACZ Melee]` 日志：客户端提前返回原因与分支、服务端前置判定值、候选实体距离/角度/可见性、`attackEntityFrom` 返回值与血量变化。平时不输出、无行为变化 | `LocalPlayerMelee` / `LivingEntityMelee` / `ModernKineticGunItem#doMelee` |

### 1.7.10 API 语义坑清单（持续补充，移植时逐条对照）

移植时最容易出的不是「编译不过」，而是**同名 API 语义不同**、编译照过、运行期行为悄悄错。已踩过的：

| API | 1.7.10 语义（`build/rfg/minecraft-src`） | 1.8+ / 1.20.1 语义 | 踩坑点 |
|-----|------------------------------------------|---------------------|--------|
| `net.minecraft.util.Vec3#subtract(Vec3)` | **反向减法**：`a.subtract(b)` 返回 `b - a`（`Vec3.java:55-61`，javadoc 原文 "the specified vector minus this"） | `a.subtract(b)` 返回 `a - b` | `ModernKineticGunItem#doMelee` 近战锥形整体镜像（实机 #12）、`GunMeleeDebug#showRange`、`EntityUtil#getHitResult` 爆头局部坐标反号 |
| `net.minecraft.util.Vec3#rotateAroundX/Y/Z` | **原地修改**，返回 `void` | 1.20 `xRot/yRot/zRot` 返回新 `Vec3`；旋转公式本身一致 | `doMelee` 的视线向量按 1.7.10 原地版改写（已正确） |
| `Vec3#addVector` | 返回新向量（`this + (x,y,z)`，不改自身） | 无同名方法（1.20 用 `add`）；语义等价 | 无 |

> 结论：凡是移植代码里出现 `Vec3#subtract`，必须逐个确认方向（本轮已全部改为按分量显式相减并加注释）。
