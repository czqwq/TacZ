# NEI（NotEnoughItems）联动手册（1.7.10 版 JEI 等价物）

> 目的：上游 1.20.1 用 **JEI** 提供「配方显示 + 枪械/弹药/配件兼容性查询」。1.7.10 没有 JEI，
> 社区等价物是 **NEI（NotEnoughItems）**。本文档记录 1.7.10 侧的实装方案、与上游 JEI 的逐项映射、
> 差异与实机验证清单。
>
> 代码位置：`src/main/java/com/tacz/guns/compat/nei/`（11 个文件）。

---

## 1. 依赖与加载方式

| 项 | 内容 |
|----|------|
| 依赖坐标 | `implementation(gtnhDev("NotEnoughItems"))`（`dependencies.gradle`，由用户添加；GTNH 2.9.0-RC-2 清单版本，GTNH 仓库解析） |
| 传递依赖 | **CodeChickenCore**（NEI 的 `api` 依赖，Gradle 自动带出）。本模组代码用到的 CCC API：`codechicken.lib.gui.GuiDraw`（`drawRect/drawString/changeTexture` 等） |
| 运行期要求 | NEI（+ CCC）为**可选**依赖：本模组 jar 不打包 NEI，NEI 缺席时 `com.tacz.guns.compat.nei.**` 不会被任何类加载器加载（服务端/无 NEI 客户端零风险） |
| 插件发现 | NEI 的 `ClientHandler#loadPluginsList` 用 CCC `ClassDiscoverer` 扫描所有 mod jar：**类名以 `NEI` 开头、以 `Config` 结尾、实现 `codechicken.nei.api.IConfigureNEI`**。因此入口类名固定为 `NEITaczConfig`（不可改），`loadConfig()` 在「进入世界后、NEI 插件加载线程」被调用 |
| 加载时机保证 | NEI 在世界加载后加载插件；此时客户端枪包索引/配方已由 `CommonAssetsManager` 同步并 `initAll()` 完毕。所有 handler 的查询本身是惰性的（玩家按 R/U 时才取数据），不依赖加载瞬间快照 |

### 为什么不用 `TemplateRecipeHandler` 自带的 recipebg 贴图

NEI 默认 `drawBackground` 会从 `guiTexture` 偏移 (5,11) 画 166×65 的原版工作台背景，只适合 3×3 固定配方。
本模组的配方材料数量 1~11 个、查询页还要 9×2 物品格，因此所有 handler **自绘 MC 风格面板 + 物品槽**
（`NeiRender`，纯 `GuiDraw.drawRect`，不新增任何 png 资产），尺寸保持 NEI 默认 `HandlerInfo` 的 **166×65**，
避免改 NEI 的 `handlers.csv` / 反射 `HandlerInfo`。

> 注意 CCC `GuiDraw#drawRect(int,int,int,int,int)` 的参数是 `(x, y, width, height, color)`，
> 不是 x2/y2 —— 这是 1.7.10 生态里反复踩的坑，`NeiRender` 里已封装。

---

## 2. 上游 JEI → 1.7.10 NEI 映射

上游实现：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/jei/`（14 文件，`@JeiPlugin` 入口 +
6 个 `IRecipeCategory` + 5 个 entry 数据类 + `IRecipeTransferHandler`）。

| 上游 JEI | 1.7.10 侧 | 说明 |
|----------|-----------|------|
| `GunModPlugin`（`IModPlugin`） | `NEITaczConfig`（`IConfigureNEI`） | 唯一的注册入口；按「类名规则」被 NEI 发现 |
| `category/GunSmithTableCategory`（每个 `CommonBlockIndex` 一个） | `GunSmithTableRecipeHandler`（每个工作台一个实例） | 过滤规则逐条对齐：`RecipeFilter` + `BlockData#getTabs()` 匹配 `result.group` |
| `category/GunAmmoQueryCategory` | `GunAmmoQueryHandler` | 产物=枪，材料=其弹药（R 枪看弹药；U 弹药看所有适用枪） |
| `category/AmmoQueryCategory` | `AmmoQueryHandler` | 产物=弹药，材料=适用枪械列表（每 18 把一页） |
| `category/AttachmentQueryCategory` | `AttachmentQueryHandler` | 产物=配件，材料=兼容枪械（按 `GunTabType` 分组，与上游 `AttachmentQueryEntry` 一致） |
| `category/GunAttachmentQueryCategory` | `GunAttachmentQueryHandler` | 产物=枪，材料=该槽位类型兼容配件（按 `AttachmentType` 分组、分页） |
| `transfer/GunSmithTableTransferHandler` | **未实现** | NEI 的 GUI overlay/transfer 体系（`IOverlayHandler` + `IStackPositioner`）与 JEI 不同，且工作台 GUI 有 3 种布局；本轮只做「看配方」，不做「一键填材料」。见 §5 |
| `registerRecipeCatalysts`（工作台） | `API.addRecipeCatalyst(tableIcon, handler)` | 对工作台物品按 U → 显示它支持的全部配方 |
| `registerItemSubtypes`（枪/弹药/配件/工作台/弹药盒的 NBT 子类型） | `NeiItemVariants`（`API.setItemListEntries`） | NEI 面板里按 NBT 变体展示；对比规则见 §3.1 |
| `AmmoStorageUpgradeRecipe` 展开成原版 shaped 配方合入 CRAFTING | `AmmoStorageUpgradeHandler` | 1.7.10 里它是自定义 `IRecipe`，NEI 内置工作台 handler 不认 ⇒ 单开一页展示同样的 3×3 |
| `entry/*` 数据类（5 个） | `NeiQueryData`（1 个，带指纹缓存） | 数据全部由 `TimelessAPI` 的公共索引派生，不依赖任何 JEI 类型 |
| `IRecipeCategory#getIcon` / category 归属的 mod 名 | `NeiHandlerInfos`（NEI `HandlerInfo`） | 页签图标 + mod 名（否则 NEI 显示 `Unknown`）；注册时机/线程约束见 §7.2 |

---

## 3. 实现细节

### 3.1 物品匹配（1.7.10 没有 JEI 的 subtype 系统）

`NeiStacks#sameResult(pageResult, focused)`：

- 双方都是枪械（`IGun`）→ 比 **gun id**；
- 双方都是弹药（`IAmmo`）→ 比 **ammo id**；
- 双方都是配件（`IAttachment`）→ 比 **attachment id**；
- 其它物品 → item + damage（+ NBT 全等，若双方都有 NBT）。

原因：枪械/弹药/配件的 NBT 含运行期状态（当前弹药数、开火模式、已装配件…），NBT 全等会让
「物品面板里的 X 枪」和「配方产物 X 枪」对不上；上游 JEI 的 `GunModSubtype` 同样只按 id 判定。

### 3.2 页面布局（`NeiRender` 常量）

| 页面 | 布局 |
|------|------|
| 枪械工作台 | 产物格 (144,24)，材料从 (8,24) 起每格 +20（≤6 个一行）；>6 个分两行（y=7/27，每行 6 个）；箭头 (126,27) |
| 枪械→弹药 | 枪格 (74,0)，弹药格 (74,39)，中间文字「使用弹药」 |
| 列表页（弹药/配件/枪械查询） | 主物品格 (2,0) + 左上文字标签；材料/候选物品 9 列 × 2 行（(2,20)/(2,38) 起，每格 18）；右下角「第 x/y 页」（`jei.tacz.*.page`） |
| 弹药库升级 | 原版工作台坐标：3×3 从 (25,6)、箭头 (90,27)、产物 (119,24) |

每页最多 18 个列表物品（`NeiQueryData.PAGE_SIZE = 9*2`），超过分页；上游 JEI 单页 54 个，NEI 面板高度
固定 65 所以更小，属可接受差异。

### 3.3 R/U 双向语义

NEI 与 JEI 一样区分「crafting（R，看这个产物怎么做）」与「usage（U，看这个材料用在哪）」。
每个 handler 同时注册到两边（`API.registerRecipeHandler` + `API.registerUsageHandler`），
`loadCraftingRecipes(ItemStack)` / `loadUsageRecipes(ItemStack)` 各自实现：

- 工作台 handler 的 usage = 材料出现在配方里；
- 查询 handler 的 crafting = 悬停物是「产物」，usage = 悬停物是「材料列表中的一项」
  （例如 U 枪械会列出「该枪兼容配件」页，U 配件会列出「兼容该配件的枪」页）。

### 3.4 数据缓存与失效

`NeiQueryData`：

- 枪↔弹药：一次遍历枪索引 + `IAmmo#isAmmoOfGun` 校验，双向共用同一份映射；
- 枪↔配件：**枪数 × 配件数** 次 `IGun#allowAttachment`（默认+示例枪包约 1.7 万次），缓存为不可变快照；
- 指纹 = 枪/弹药/配件索引的 id 哈希 + `sort` + 数量；`/tacz reload` 后 id 集合变化会自动重建；
- 插件加载时**预热**一次（`NEITaczConfig#loadConfig`），避免玩家首次按 R/U 卡顿；
- **已知限制**：同 id 集合下只改数据内容（例如只调整某配件兼容性）不会触发重建 —— 重开一次
  NEI 查询（重进世界即可）恢复；正常 `/tacz reload` 增删枪包会命中指纹变化。

---

## 4. 验证清单（实机）

前置：`run/client/mods` 里有 **NotEnoughItems**（dev 环境由 `implementation(gtnhDev("NotEnoughItems"))`
自动进运行期 classpath；正式环境把 NEI+CCC 放进 mods）。

1. 进入世界后日志应出现（`run/client/logs/latest.log` 搜 `[TACZ]`）：
   - `[TACZ] loading NEI integration (gun smith table recipes + item queries)`
   - `[TACZ] NEI item variants: gun x<N>` / `ammo x<N>` / `attachment x<N>` / `ammo_box x3` / `gun smith table x<N>`
   - `[TACZ] NEI gun smith table handlers: 3`（默认包；名称与 handler id 依次打印）
   - `[TACZ] NEI handler infos applied: N`（页签图标 / mod 名，**不能**再出现 Unknown）
   - `[TACZ] NEI query data rebuilt in <X> ms (...)`（预热）
   - 不应再出现 `NoSuchMethodException: com.tacz.guns.compat.nei.*.<init>()` 或
     NEI 的 `Error while looking up crafting recipe`（见 §7）。
2. NEI 物品面板搜 `AK47`/`M4A1` 等：能搜到具体枪械（不是空 NBT 的 `modern_kinetic_gun`）；
   搜弹药/配件同理。页签 tooltip 的 mod 名应为 `Timeless and Classics Guns: Zero`，且页签有物品图标。
3. **R 键**：
   - 对一把可制作的枪（如 AK47）→ 出现「枪械工作台」页，材料数量正确（和枪械工作台 GUI 里一致）；
     同一把枪还应出现「枪械弹药查询」「枪械配件查询」两个 tab；
   - 对弹药 → 出现「弹药适用枪械」+ 工作台配方页；
   - 对配件 → 出现「配件查询」+ 工作台配方页；
   - 对弹药库 → 出现「弹药库」3×3 升级页。
4. **U 键**：
   - 对铁锭/木板等材料 → 出现用到它的枪械工作台配方；
   - 对弹药 → 出现使用它的枪（每把枪一页，分页文字「第 x/y 页」正确）；
   - 对枪 → 出现它使用的弹药 / 兼容配件 / 适用弹药页；
   - 对工作台方块 → 显示该工作台支持的全部配方（催化剂）。
5. 分页：对列表页点下一页/翻页，页脚 `第 x/y 页` 与实际数量一致；不越界、不重复。
6. 三张工作台各自只显示自己的配方（枪械工作台不显示弹药/配件工作台配方；tab 过滤与 `RecipeFilter` 生效）。
7. 未装 NEI / 专用服务端：无 `NoClassDefFoundError`、无 TACZ 报错（模组正常启动）。

---

## 5. 未实现 / 已知差异（记录，避免重复排查）

1. **工作台 GUI 内嵌配方 overlay + 一键转移（JEI transfer handler 的等价物）未实现**：
   需要 NEI 的 `IOverlayHandler`/`IStackPositioner`/`registerGuiOverlay` 体系，并为 3 种工作台 GUI
   各算一套「配方格 → GUI 槽位」映射；本轮目标是「方便看配方」（R/U 页面），overlay 留作后续增强。
2. **`AmmoStorageUpgradeHandler` 只展示形状，不展示 `BlockEntityTag` 库存 NBT**（上游 JEI 展开成
   shaped 配方同样不展示 NBT；实际合成仍由 `AmmoStorageUpgradeRecipe` 负责搬运库存）。
3. **查询页每页 18 个**（上游 JEI 54 个）、**页面尺寸固定 166×65**（NEI `HandlerInfo.DEFAULT_*`）：
   本移植的布局本身就是按 166×65 绘制的，所以不改 `handlers.csv` / 不调 `HandlerInfo#setHandlerDimensions`
   （`NeiHandlerInfos` 只登记图标 / mod 名 / 按钮开关）；上游 JEI 的分类背景宽度是 160~166，观感一致。
4. **未实现 `GunPegboardDyeRecipe` 的配方页**：上游 JEI 插件同样不展示它（1.20 该逻辑不在 JEI 插件里）。
5. NEI 的 **item panel 子类型**靠 `API.setItemListEntries` 覆盖（每个物品一份列表），因此
   「未注册变体的裸物品」不会再出现在面板里（这正是期望行为）；若枪包数据未同步，代码不会覆盖
   （空列表绝不调用，避免物品消失）。
6. 页签的 **overlay（?）/收藏（★）按钮隐藏**（`HandlerInfo#setShowOverlayButton(false)` /
   `setShowFavoritesButton(false)`）：这两个按钮画在面板右下角、会压住列表格子，而本移植没有实现
   overlay 转移逻辑（上游 JEI 的 overlay/书签能力见本节第 1 条）。NEI 的 handler ordering /
   hidden handlers 配置文件仍可用 handler id（见 §4 日志）当键。

---

## 6. 构建 / 自检

- `./gradlew spotlessApply compileJava` = **BUILD SUCCESSFUL**（2026-11-07，NEI 2.8.x GTNH 编译期依赖）；
  §7 的实机问题修复后由用户侧统一构建验证（Lead 不再跑 gradle）。
- 代码清单（`src/main/java/com/tacz/guns/compat/nei/`，12 文件）：
  - `NEITaczConfig.java`（`IConfigureNEI` 入口，类名规则不可改）
  - `NeiRender.java`（面板/物品槽/箭头/文字/网格坐标）
  - `NeiStacks.java`（产物匹配、材料展开、物品栈构建）
  - `NeiQueryData.java`（枪↔弹药、枪↔配件共享快照 + 指纹缓存）
  - `NeiItemVariants.java`（`setItemListEntries` 注入 NBT 变体）
  - `NeiHandlerInfos.java`（`HandlerInfo`：页签图标 / mod 名）
  - `GunSmithTableRecipeHandler.java`（工作台配方，按工作台实例化）
  - `GunAmmoQueryHandler.java` / `AmmoQueryHandler.java`
  - `AttachmentQueryHandler.java` / `GunAttachmentQueryHandler.java`
  - `AmmoStorageUpgradeHandler.java`

---

## 7. 实机问题修复（2026-11-07 · 首次进游戏）

第一轮实机（NEI 2.8.x + YSMU）暴露的问题、根因与修复：

### 7.1 `NoSuchMethodException: GunSmithTableRecipeHandler.<init>()`（配方查询全挂）

- 现象：NEI 每个 worker 线程都刷 `RuntimeException: java.lang.NoSuchMethodException:
  com.tacz.guns.compat.nei.GunSmithTableRecipeHandler.<init>()`，聊天栏 `There was error during
  loading recipe`(×8)，工作台配方页、催化剂 U 查询全部不可用（"category 没有绑定到工作台"）。
- 根因：NEI 的 `TemplateRecipeHandler#newInstance()` 用**反射无参构造器**复制 handler
  （`getClass().getConstructor().newInstance()`），`getAllRecipeHandler()` /
  `getRecipeHandler()` / `getUsageAndCatalystHandler()` 都走它；本 handler 是「一个实例 = 一个工作台」，
  构造器带 `blockId`，没有无参构造器。
- 修复：覆写 `newInstance()` 返回携带同一 `blockId` 的新实例（NEI 官方扩展点；GT5U
  `gregtech/nei/GTNEIDefaultHandler#newInstance()` 同款写法）。
- 连带：催化剂 U 查询还要求 handler 有 `transferRects` —— `getUsageAndCatalystHandler()` 命中催化剂后
  遍历 `transferRects`，为空会打
  `failed to load catalyst handler, implement loadTransferRects for your handler` 并退化成空 usage 页。
  因此构造时登记一条「箭头区域 → 本工作台全部配方」的 `RecipeTransferRect`（outputId = handler id），
  同时支持点击箭头查看该工作台全部配方。
- 注意坑：`TemplateRecipeHandler` 构造器会调用 `loadTransferRects()`，那时子类实例字段还没初始化
  （Java 先 `super()` 后字段赋值），所以 table 的 rect 在子类构造器体内补登；
  `AmmoStorageUpgradeHandler` 只用静态常量，可直接覆写 `loadTransferRects()`。

### 7.2 页签 mod 显示 `Unknown`、页签无图标

- 根因：NEI 的页签信息来自 `GuiRecipeTab.handlerMap`（默认 handlers.csv / config 填充），未登记的
  handler 落到 `DEFAULT_HANDLER_INFO`（`modName = "Unknown"`，无图标 ⇒ 文字兜底）。
- 时序坑：NEI 在 `FMLLoadCompleteEvent` 就 `loadHandlerInfo()` 并发 `NEIRegisterHandlerInfosEvent`，
  而本模组的 NEI 插件要等**进世界**才由 NEI 后台线程实例化（`bootNEI → PluginLoader → loadConfig`），
  事件早已发过；且 `handlerMap` 只能由客户端线程读写。
- 修复：`NeiHandlerInfos` ——
  ① 事件监听器在 `ClientProxy#init`（mod 加载主线程，NEI 已安装时经 `CompatRegistry.checkModLoad` +
  `invokeOptional` 反射调用 `NeiHandlerInfos#registerListener()`）提前挂到 Forge 总线，接住 FML
  LoadComplete 的 `NEIRegisterHandlerInfosEvent`；
  ② `loadConfig()` 里再投递一个客户端主线程任务，用真实枪包数据刷新页签图标（LoadComplete 时枪包索引
  还没加载，`TimelessAPI` 为空）；
  ③ 不用 `EventBus#register` 在 NEI 插件线程/世界加载时注册监听器：Forge 1.7.10 的该调用会读
  `Loader#activeModContainer()`，在 mod 加载期之外调用会刷 "Unable to determine registrant mod" 的 ERROR。
- 图标：工作台用 `BlockItemBuilder`（索引未加载时退回 `GameRegistry#findItem`，方块物品 preInit 已注册）；
  枪/弹药/配件取各索引的第一个（hidden 配件跳过）；弹药库用方块物品本身。

### 7.3 其它同时修掉的小问题

- `AmmoStorageUpgradeHandler#getRecipeName()` 用的 `tacz.block.ammo_storage.name` 在 1.7.10 的 `.lang`
  里不存在（1.7.10 键是 `tile.tacz.ammo_storage.name`）⇒ 页名会显示原始键。改为取方块物品
  `getDisplayName()`（随语言表变化）；同时补 §7.1 的 transfer rect 与 `all` 页加载。
- `AttachmentQueryHandler` 页脚原来错用 `jei.tacz.ammo_query.page`（上游没有 `attachment_query.page`）⇒
  改为上游的 `jei.tacz.attachment_query.more`（还有更多页时显示 "More..."，与上游
  `AttachmentQueryCategory#draw` 对齐）。
