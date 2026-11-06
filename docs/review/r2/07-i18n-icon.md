# r2-07 · 本地化 / 物品名 / 内置枪包图标审查

> **作用域（只读审查 + 本作用域内修复）**
> 写作用域：`src/main/java/com/tacz/guns/resource/**`、`src/main/java/com/tacz/guns/client/init/**`、
> `src/main/resources/assets/tacz/lang/**`、`src/main/resources/assets/tacz/custom/tacz_default_gun/**`；
> r2-07b 追加：`src/main/java/com/tacz/guns/crafting/**`、`client/init/ClientSetupEvent.java`。
> 只读参考（问题只记录不改）：`client/renderer/**`、`client/gui/**`、`client/model/**`、`item/**`、`api/**`、
> `init/**`、`mixin/**`。
>
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`、`tmp/TACZ-1.20.1/src/main/resources/assets/tacz/**`。
> **1.7.10 准绳**（全部实读 `build/rfg/minecraft-src`）：
> `net/minecraft/item/Item.java:605-609/614-626/742-745`、`net/minecraft/block/Block.java:1294-1297`、
> `net/minecraft/item/ItemBlock.java:179-189`、`net/minecraft/client/resources/Locale.java:32-59/106-126`、
> `net/minecraft/client/resources/LanguageManager.java:78-90`、
> `net/minecraft/client/Minecraft.java:520-548/636-673`、`net/minecraft/util/StatCollector.java:16-19`、
> `cpw/mods/fml/common/registry/LanguageRegistry.java:54/160-170/244-254/256-323`、
> `cpw/mods/fml/client/FMLClientHandler.java:238/302-327/588-612`、`cpw/mods/fml/common/FMLModContainer.java:586-597`。
>
> **覆盖清单**：① 物品/方块名解析链路（5 个 Item + 12 个 Block）；② 20 个本体 `.lang` × 20 个枪包 `.lang` 的 key 差集；
> ③ `LanguageRegistry` 注入时机与时序；④ 12 个 `registerIcon` 调用点（14 个图标名）+ 4 个 `IItemRenderer` 的贴图存在性；
> ⑤ `custom/tacz_default_gun` 全部 `data/tacz/**`（232 个 index + 209 个 data）的引用完整性与 gson 2.2.4 字段类型审计；
> ⑥ 创造标签页 / 改装台 / tooltip 的包名·枪名取值链。
>
> **自查方式**：**未运行任何 gradle 任务**（含 `runClient`/`compileJava`）。使用的都是只读手段：
> `read`/`grep` 读源码与资源、`mcp__idea__read_file` 读 1.7.10 反编译源码、
> 只读 pwsh 脚本做批量文本比对（`build/i18n/lang_key_diff.ps1`、`build/i18n/gunpack_acceptance.ps1`，
> 产物 `build/i18n/code-key-diff.csv`、`build/i18n/gunpack-acceptance.csv`、`build/i18n/index-name-keys.csv`）、
> 用 `python tools/lang_json_to_lang.py --check` 回读校验 40 个 `.lang`（**CHECK: 0 problem(s)**）、
> `mcp__idea__get_file_problems(errorsOnly=true)` / `lint_files(min_severity=error)` 校验改动后的 Java 文件（**0 error**）。
> 配方相关的数字（199 / 147 / 27 / 25）全部由只读脚本对磁盘上的 json 统计得出，不是估算。

---

## 结论速览

| 指标 | 数量 | 说明 |
|---|---|---|
| **短缺 lang key（本体）** | **17 → 0** | 1.7.10 名称解析实际查询的 17 个键（12 个 `tile.*.name` + 5 个 `item.*.name`，其中 `item.tacz.ammo_box.name` 当前代码不可达）修复前**全部**不存在；本次已全部补齐（§一.1） |
| **短缺 lang key（枪包）** | **2** | `tacz.attachment.laser_peq15`、`tacz.attachment.sight_p90.name`；**上游 1.20.1 同样缺**，按保真不修（§四） |
| **缺失贴图** | **0** | 12 个 `registerIcon` 调用点 / 13 次调用 / **14 个不同图标名**全部命中真实 png（§五） |
| **内置包数据加载失败** | **0** | 232 个 index 的 `data`/`display` 引用 100% 命中；`EnumMap` 适配器已覆盖全部 4 个 EnumMap 字段（§六） |
| **工作台配方注册数**（r2-07b） | **0 → 199 / 199** | `data/<ns>/recipes/**` 的 199 个 json 全部进入 `GunSmithTableRecipe.REGISTRY`；其中 **147 条**原版即可合成、**27 条**需铜矿词、**25 条**因 1.7.10 无紫水晶/下界合金/铁粒而永久不可合成（§零） |

**最关键的 5 条结论：**

1. **【已修 · P0】`.lang` 没有做 1.20.1 → 1.7.10 的「前缀 + `.name` 后缀」转换，导致全部物品/方块显示原始 key 或 `V.name`。**
   1.7.10 查的是 `item.tacz.<name>.name` / `tile.tacz.<name>.name`，而 `.lang` 里只有上游的 `item.tacz.<name>` / `block.tacz.<name>`。
   已按 §一.1 的映射补齐 12 个 `tile.*.name` + 4 个物品兜底键，并把会产出 `Minecart with Target.name` 的裸键改名；
   同时改 `tools/lang_json_to_lang.py`（重跑脚本不会回退），20 个语种全部重新生成并回读校验通过。
2. **【已修 · P0】`CommonAssetsManager.registerEventHandlers()` 传的是 `Class` 而不是实例 → 多人服客户端永远收不到枪包数据。**
   `MinecraftForge.EVENT_BUS.register(CommonAssetsManager.class)` 在 1.7.10 注册的是 `java.lang.Class`，
   一个 `@SubscribeEvent` 都扫不到 → `onPlayerLoggedIn` 永不触发 → `ServerMessageSyncGunPack` 一包都不下发。
   这是「多人服里枪既没名字也没模型」的直接根因（单人档走 `CommonAssetsManager.INSTANCE`，不受影响）。
3. **【已修 · P0】枪包 `data/<ns>/recipes/**` 从来没被读过 → 工作台左侧配方列表恒空、且就算显示也做不出东西（r2-06 报的 P0）。**
   新增 `GunSmithTableRecipeManager` 接进重载管线：199/199 全部登记，同时把原始 json 挂进 `DataType.RECIPES` 下发给多人服客户端。
   并确认了 Lead 点名的**第二层断裂**：`initLazyRecipes()` 原先遍历 `CraftingManager#getRecipeList()` 找
   `GunSmithTableRecipe`，而后者**根本不实现 `IRecipe`、从不进 CraftingManager**（设计如此）→ 那个循环恒 0 次、
   `init()` 永不执行 → `getOutput()` 恒 `null` → `doCraft` 在「产物为空」处直接 return。已改为 `GunSmithTableRecipe#initAll()`。详见 §零。
4. **【已修 · P1】外部枪包语言只在 `postInit` 注入「当前语种」一次，切语言后失效 —— 且**不需要** mixin。**
   1.7.10 的 `LanguageManager#onResourceManagerReload` 是**第一个**注册的资源重载监听器，mod 的监听器只能排它之后，
   所以「切完语言再注入」在 1.7.10 不可达；但 `mergeLanguageTable(map, currentLanguage)` 是**按当前语种**取表的，
   因此把每个枪包的**全部语种**在启动时一次性灌进 `LanguageRegistry` 即可彻底消除时序依赖。
   已在 `GunPackLoader` 新增 `injectAllGunPackLanguages()` 并在 `ClientSetupEvent` 里接线（`mixin/**` 一字未动）。
5. **【新发现 · 数据层面】25 条配方在纯 1.7.10 里永久不可合成**：`forge:gems/amethyst`（20 条）、
   `forge:ingots/netherite`（4 条）、`forge:nuggets/iron`（1 条）在 1.7.10 原版既无物品也无矿词。
   另有 27 条依赖 `forge:ingots/copper`（有铜模组即可，GTNH 类整合包满足）。详见 §零.5。

---

## 零、r2-07b 追加修复：枪械工作台配方加载（P0 / task-10）

### 0.1 现象与复验（r2-06 的结论成立）

| 事实 | 复验方式 | 结果 |
|---|---|---|
| 枪包 `data/<ns>/recipes/**` 共 **199** 个 json（gun 70 / attachments 104 / ammo 25） | `Get-ChildItem -Recurse -Filter *.json` | ✅ 与 r2-06 一致（**没有** `misc/blood_strike_1.json`，也没有根级 json；只有这 3 个子目录） |
| `GunPackLoader#readPackData(String)`（`:206`）0 调用者 | 全仓 grep `readPackData(` | ✅ 修复前只有定义 |
| `GunSmithTableSerializer#deserializeAndRegister`（`:65`）0 调用者 | 同上 | ✅ 修复前只有定义 |
| `GunSmithTableRecipe.REGISTRY`（`:55`）恒空 | 静态表无写入者 | ✅ |
| `GunSmithTableRecipe.clear()`（`:126`）0 调用者 | 同上 | ✅ 一旦接线会残留已删枪包的配方 |
| `DataType.RECIPES`（`resource/network/DataType.java:12`）无生产者 | `CommonNetworkCache#fromNetwork` 的 switch 无 `RECIPES` 分支 | ✅ 多人服侧也不下发 |

后果链：`client/gui/GunSmithTableScreen.java:183` 遍历 `GunSmithTableRecipe.getAll()`（恒空）
→ 左侧配方列表恒空；即使有列表，`inventory/GunSmithTableMenu.java:246-249` 拿 `recipe.getOutput()`
（`= result.getResult()`，未 `init()` 时为 `null`）→ 直接 `return`，**合成静默失败**。

### 0.2 修法：选 (b)「新增管理器」而不是 (a)「内联一段」

新增 `src/main/java/com/tacz/guns/resource/manager/GunSmithTableRecipeManager.java`
（`implements INetworkCacheReloadListener`），在 `CommonAssetsManager#reloadAndRegister()` 末尾
`register(...)`（`CommonAssetsManager.java:245`）。

**为什么是 (b)**：`INetworkCacheReloadListener` 会同时被
`CommonAssetsManager#getNetworkCache()`（`:264-270`）收进快照，于是
`DataType.RECIPES` 自动获得生产者 → `ServerMessageSyncGunPack.createMessages` 下发 →
客户端 `CommonNetworkCache#fromNetwork`。若按 (a) 内联，服务端本地能读到配方，但
**多人服客户端的列表仍然是空的**（客户端在 MP 下不跑 `CommonAssetsManager.reload()`，只吃服务端快照）。
这正是任务里「若 `reloadOrder`/`INetworkCacheReloadListener` 机制能顺便解决多人服同步，就用 (b)」。

顺序契约（`clear()` → 逐条注册 → `initAll()`）落实为三处：

1. `GunSmithTableRecipeManager#reload()`：`GunPackLoader.readPackData("recipes")` → `loadRecipes(...)`
   （`loadRecipes` 第一件事就是 `GunSmithTableRecipe.clear()`）→ 写 `networkCache`；
2. `CommonAssetsManager#reloadAndRegister()` 的重载循环跑完 → `networkSyncMessages = createMessages(...)`；
3. 循环末尾 `initLazyRecipes()` → **`GunSmithTableRecipe.initAll()`**（见 §0.3）。

**容错**：`loadRecipes` 逐条 `try/catch (RuntimeException)`，失败 `warn` 并带配方 id 与来源，绝不中断整轮重载；
非工作台配方类型（`type` 存在且 ≠ `tacz:gun_smith_table_crafting`）跳过并计数。

### 0.3 Lead 点名的「第二层断裂」：确认成立

`CommonAssetsManager#initLazyRecipes()` 原先遍历 `CraftingManager.getInstance().getRecipeList()`
找 `GunSmithTableRecipe` 实例。但 `GunSmithTableRecipe` **不实现 `IRecipe`**、也**从不进 `CraftingManager`**——
这是设计决策，写在 `crafting/GunSmithTableRecipe.java:32-47`：1.7.10 的
`CraftingManager#findMatchingRecipe` 会把表里每一个条目强转成 `IRecipe` 再调 `matches`，
塞进去会让**每次打开原版工作台都 ClassCastException**。

于是那个循环**恒执行 0 次** → `init()` 永不执行 → `GunSmithTableRecipe#getOutput()` 恒 `null` →
`GunSmithTableMenu#doCraft` 在产物判空处 `return`（`:247-249`）。**即使只做 §0.2 的接线，工作台依然做不出东西**，
这就是第二层断裂。已改为直接对静态登记表调用 `GunSmithTableRecipe#initAll()`（`CommonAssetsManager.java:475-483`），
并给 `initAll()` 补了逐条 `try/catch`（`GunSmithTableRecipe.java:138-147`）：某把枪的 index 异常不该让**其余**
配方的产物一起变空。

### 0.4 顺带修掉的两个「材料永远匹配不上」缺陷（`crafting/GunSmithTableIngredient.java`）

接线后才暴露出来的两个问题，都会表现成「配方显示正常、材料也够，就是做不出来」：

1. **`toOreDictionaryName` 只处理「有矿词」的 tag，而三个 tag 在 1.7.10 根本没有矿词。**
   实读 `build/rfg/minecraft-src/java/net/minecraftforge/oredict/OreDictionary.java` 的静态注册块确认：
   1.7.10 原版/Forge 只注册了 `ingotIron/ingotGold/gemDiamond/gemLapis/gemQuartz/dustRedstone/dustGlowstone/
   blockGlass/stickWood/logWood/plankWood/nuggetGold …`，**没有** gunpowder / leather / blaze 的矿词。
   而默认枪包里 `forge:gunpowder` 出现在 **25 条**配方、`forge:leather` **32 条**、`forge:rods/blaze` **21 条**。
   修法：新增 `TAG_TO_VANILLA_ITEM` 折算表，把这三个 tag 直接解析成对应的 1.7.10 原版物品
   （`minecraft:gunpowder` / `minecraft:leather` / `minecraft:blaze_rod`），走「具体物品」分支。
   **刻意不折算** `forge:gems/amethyst`、`forge:ingots/netherite`、`forge:nuggets/iron`（1.7.10 无等价物，
   宁可做不出也不改配方成本，见 §0.5）。
2. **`test()` 与 `getMatchingStacks()` 语义不一致。** `getMatchingStacks()` 在「矿词无候选」时会回退到代表物品
   （`:169-182`），而 `test()` 在同样情况下直接 `return false`（旧 `:157-166`）→ 解析阶段明明拿到了 `item`，
   匹配阶段却恒不成立。已让 `test()` 在矿词候选为空时回退到 `itemMatches(item, stack)`，
   与 `getMatchingStacks()` 对齐（`minecraft:wool` 这类只靠物品名兜底解析出来的 tag 因此可用了）。

### 0.5 验收数字：199 条配方现在实际进入多少条

**注册层面：199 / 199 全部进入 `GunSmithTableRecipe.REGISTRY`。** 静态核对依据：

- 199 个 json **全部**带 `"type": "tacz:gun_smith_table_crafting"`（缺标记的 = 0 个）；
- 199 个都有 `materials` 与 `result`，且 `result` 都有 `type` 与 `id`（缺失 = 0 个）；
- 材料 **100% 用 `tag` 写法**（没有任何 `"item": "ns:path"` 具体物品写法）→ 不可能触发
  `GunSmithTableIngredient.fromJson` 的 `Unknown item` 异常；
- `result.type` 只有 gun / ammo / attachment（**没有** custom）→ 不经过 `ItemStackJsonParser`；
- **199** 个 id 全部唯一（`tacz:<子目录>/<文件名>`）。

**可合成性（材料是否能在 1.7.10 兑现）用同一套折算规则静态分类：**

| 分类 | 条数 | 说明 |
|---|---|---|
| 材质全部可解析（原版 1.7.10 + Forge 即可合成） | **147** | `ingotIron/ingotGold/gemDiamond/gemLapis/gemQuartz/dustRedstone/dustGlowstone/blockGlass/stickWood/logWood/plankWood` + §0.4 折算进来的 gunpowder/leather/blaze_rod + wool |
| 仅差 `ingotCopper`（有任意铜模组即可，GTNH 类整合包满足） | **27** | 24 条弹药 + 3 条其它（`scope_1866_4x`/`scope_1873_6x`/`m1866`）。纯原版环境下这 27 条不可合成 —— 与 `init/ModRecipe.java:126-129` 里「缺 `ingotCopper` 就跳过 ammo_workbench」是同一条已知限制 |
| **1.7.10 原版无等价物 → 永久不可合成** | **25** | `forge:gems/amethyst` **20 条**（19 个瞄具 + `gun/aug`）、`forge:ingots/netherite` **4 条**（`gun/m95`、`gun/m107`、`gun/hm50b`、`gun/minigun`）、`forge:nuggets/iron` **1 条**（`ammo/12g`） |

> 这 25 条**仍会出现在列表里**（数据保真：上游就是需要紫水晶/下界合金/铁粒），选中后合成会因材料不足而静默失败
> （`doCraft` 在扣材料前就 `return`，不会误扣）。若要它们可用，需要另开「1.7.10 材质替代表」议题，属数据决策，不属移植 bug。

### 0.6 多人服路径（哪条生效、哪条降级）

| 侧 | 生效路径 | 降级路径 |
|---|---|---|
| 单人 / 内置服务端 | `CommonProxy#serverStarting` → `CommonAssetsManager#reload()` → `GunSmithTableRecipeManager#reload()` → 静态表 | — |
| 专用服务器 | 同上（服务端权威） | — |
| 多人服客户端 | 登录 → `onPlayerLoggedIn` 下发 `ServerMessageSyncGunPack`（含 `DataType.RECIPES`）→ `CommonProxy.Hooks#syncGunPack` → `CommonNetworkCache#fromNetwork`：**先装全部 index，最后** `registerRecipes(...)` + `initAll()`（`CommonNetworkCache.java:186-245`） | 若服务端未装本模组/未下发 → 客户端静态表为空 → 列表为空（与上游「服务端权威」语义一致），不会崩 |
| 断开 / 换服 | `CommonNetworkCache#clear()` 里一起 `GunSmithTableRecipe.clear()`（`CommonNetworkCache.java:182`） | — |

### 0.7 本节遗留（都不是我的作用域）

1. `init/ModRecipe.java:292-295` 仍留着过期的 `TODO(port): 等 resource 层提供 GunPackLoader#readPackData("recipes") 的接线后调用`
   —— 现在已经接线了，这条注释需要删（**`init/**` 不在我作用域**）。且它引用的 `tacz:misc/blood_strike_1` 在磁盘上**不存在**。
2. `DataType.RECIPES` 现在会让同步载荷从 ~1 个 type 的几十 KB 增加到 199 条配方 json（未压缩约 60–80 KB，
   压缩后 1 个 256 KB 分片内）。`GunPackSync.MAX_SYNC_SIZE` 是 64 MB、`CHUNK_SIZE` 256 KB，
   余量充足，**无需改协议**；但若日后枪包变多，建议关注首次登录的下发耗时。
3. ~~`GunSmithTableScreen` 的分栏依赖 `recipe.getResult().getGroup()` 命中 block index 的 `tabs`；`ammo/*.json` 里显式写了
   `"group": "pd_cartridges"`，而 `TabConfig.DEFAULT_TABS` 里只有 `tacz:ammo`，可能丢页签。~~
   **—— 已由 gui-review 核实，我这条判断错了，撤回。** 实测两条事实：
   （a）`data/tacz/data/blocks/ammo_workbench.json` 的 `tabs` **确实列了** `tacz:pd_cartridges`（以及
   `ifp_rifle_cartridges` / `lc_specialized` / `explosives` / `shotgun_shells` / `alternative_proj`）；
   （b）`TabConfig.DEFAULT_TABS` 有 **16** 项（ammo/pistol/sniper/dmr/rifle/shotgun/smg/rpg/mg/scope/muzzle/stock/
   grip/extended_mag/laser/misc），不是只有 `tacz:ammo`。
   真实语义（= 上游设计）：页签**按方块类型**分，`classifyRecipes` 的 `recipeKeys.containsKey(groupName)` 会丢弃
   不在**本方块**页签里的配方 —— 默认枪械工作台 `data/blocks/gun_smith_table.json` 的 `tabs` 是
   `pistol/sniper/dmr/rifle/shotgun/smg/rpg/mg/misc`（**无** ammo 类页签），所以弹药配方只在**弹药工作台**可见，属预期。
   **回归时弹药配方要去弹药工作台验。**

---

## 一、已修问题

### 1.1【P0】`.lang` 键形态缺 1.7.10 的 `tile.` 前缀与 `.name` 后缀（本次修复）

**现象（全部可从源码推出，逐条落到文件）**

1.7.10 的名称解析链（实读源码）：

| 位置 | 行为 |
|---|---|
| `Item.java:614-626` | `getUnlocalizedName()` / `getUnlocalizedName(ItemStack)` = `"item." + unlocalizedName` |
| `Item.java:605-609` | `getUnlocalizedNameInefficiently(stack)` = `StatCollector.translateToLocal(getUnlocalizedName(stack))` ← **先单独翻译一次不带 `.name` 的键** |
| `Item.java:742-745` | `getItemStackDisplayName` = `translateToLocal(getUnlocalizedNameInefficiently(stack) + ".name")` |
| `Block.java:1294-1297` | `getUnlocalizedName()` = `"tile." + unlocalizedName` |
| `ItemBlock.java:179-189` | `getUnlocalizedName()` 直接返回**方块的** unlocalizedName，且**不覆盖** `getItemStackDisplayName` |

本工程的注册处：`init/ModItems.java:88` `item.setUnlocalizedName("tacz." + name)`；`init/ModBlocks.java:96` `block.setBlockName("tacz." + name)`。
于是 1.7.10 真正查询的键是 `item.tacz.<name>.name` / `tile.tacz.<name>.name`，而 `assets/tacz/lang/*.lang`
（`tools/lang_json_to_lang.py` 从上游 JSON 直转）里只有 `item.tacz.<name>` / `block.tacz.<name>`：

- `grep '^tile\.tacz' en_US.lang` → **0 命中**（修复前）
- 任何 `\.name=` 结尾的 key → **0 命中**（修复前）

三个可见症状（与 Lead 交叉检查的结论一致，其中第 3 条我核后发现不成立）：

| 物品 | 代码路径 | 修复前显示 |
|---|---|---|
| 12 个方块物品（`TargetBlock`/`StatueBlock`/`GunDisplayCaseBlock`/`AmmoStorageBlock`×3/`GunPegboardBlock`×2/`AbstractGunSmithTableBlock`×4 的 ItemBlock） | `ItemBlock#getUnlocalizedName` → `tile.tacz.<name>` 未命中 → `+".name"` → 仍未命中 | 字面量 `tile.tacz.<name>.name` |
| `TargetMinecartItem`（`ModItems.java:65`，**无** `getItemStackDisplayName` 覆盖） | `translateToLocal("item.tacz.target_minecart")` **命中** → 得到 `"Minecart with Target"` → `+".name"` → 再译为 `"Minecart with Target.name"` | **`Minecart with Target.name`**（最典型的残留后缀） |
| ~~`AmmoBoxItem` 非 creative 分支走 `super`~~ | 实读 `item/AmmoBoxItem.java:133-148`：`if(allTypeCreative)` / `if(creative)` / `switch` 的 `default` **四个分支全部 return**，**永不调用 `super.getItemStackDisplayName`** | 不受影响（`item.tacz.ammo_box.iron|gold|diamond` 在 lang 里都有）；新增的 `item.tacz.ammo_box.name` 只是防御性兜底 |

**修法（只动我作用域内的 `.lang`，代码零改动）**

写进 `tools/lang_json_to_lang.py` 新增的 `apply_1710_name_keys()`（脚本内注释引用了上面全部行号），只对
`src/main/resources/assets/tacz/lang` 这一个目录生效，枪包 lang 目录**不动**（枪包的键是被 `LanguageRegistry`
按原样注入的完整键，见 §三.3）。

1. **方块：新增 12 个 `tile.tacz.<unlocalizedName>.name`**（与 `ModBlocks.java:64-85` 的
   `registerBlock("<name>", ...)` 一一对应）。值取同语种的 `block.tacz.<name>`；
   上游没有 `block.tacz.*` 的三个工作台，值取默认枪包 index 名（`index/blocks/*.json` 的 `id` 与
   `tacz.block.ammo_workbench.name` / `gun_smith_table` / `attachment_workbench` 的对应关系：
   `workbench_a`←`ammo_workbench`、`workbench_b`←`gun_smith_table`、`workbench_c`←`attachment_workbench`）：
   - `gun_smith_table` `workbench_a` `workbench_b` `workbench_c` `target` `statue`
     `gun_pegboard` `glow_gun_pegboard` `ammo_storage` `ammo_storage_gold` `ammo_storage_diamond` `gun_display_case`
   - `block.tacz.*` **保留不删**：枪包 index/tooltip 仍按上游语义引用它，且在 1.7.10 永远不会被名称解析命中，无害。
2. **物品：改名 2 条、新增 4 条兜底**
   - `item.tacz.target_minecart` → **改名**为 `item.tacz.target_minecart.name`（**不能两个都留**，否则又是 `V.name`）。
   - `item.tacz.ammo_box` → 改名为 `item.tacz.ammo_box.name`。该裸键只出现在上游历史遗留的
     `lang/tr-TR.json`（`"item.tacz.ammo_box": "Mühimmat Kutusu"`），与新增兜底键同形，一并改名让土耳其语也能用上。
   - 新增兜底 `item.tacz.modern_kinetic_gun.name` / `item.tacz.ammo.name` / `item.tacz.attachment.name` /
     `item.tacz.ammo_box.name`。它们只在「枪包客户端索引缺失」时被查到
     （`AbstractGunItem.java:460-468`、`item/AmmoItem.java:67-68`、`item/AttachmentItem.java:60-61` 的 `super` 回退）。
     值：`en_US`/`zh_CN`/`zh_TW` 手写（Gun/枪械/槍械、Ammo/弹药/彈藥、Attachment/配件/配件、Ammo Box/弹药盒/彈藥盒），
     其余 17 个语种用英文——**这是本工程新增的键，不是上游译文**，如需各语种母语译文请 Lead 指派本地化补充。
3. 重跑脚本生成 20 个本体 `.lang`（`en_US` 454→**470** key；`tr_TR` 465→**481**，多出的 11 个来自 `tr-TR.json` 合并，与原状一致）。

**依据 / 验证**

- `python tools/lang_json_to_lang.py --check` → **`CHECK: 0 problem(s)`**（脚本用 `Locale#loadLocaleData` 的同一套规则
  回读 40 个 `.lang`，逐 key 比对值与集合）。
- 独立校验脚本：20/20 语种均满足「12 个 `tile.*.name` 齐 + 4 个兜底键齐 + 无 `item.tacz.*` 与 `item.tacz.*.name` 并存」。
- 枪包 20 个 `.lang` 未被改动（key 数仍为 `en_US`=340 / `zh_CN`=338 / `tr_TR`=342，且无任何 `tile.tacz.*`）。

**改动文件**：`tools/lang_json_to_lang.py`（新增 `MOD_LANG_REL`/`BLOCK_UNLOCALIZED_NAMES`/
`WORKBENCH_TILE_FROM_GUNPACK`/`ITEM_KEY_RENAMES`/`ITEM_FALLBACK_NAMES`/`load_gunpack_name_values`/
`apply_1710_name_keys`，并接进 `run()`）+ `src/main/resources/assets/tacz/lang/*.lang`（20 个重新生成）。
> 注：`tools/**` 原本不在我的写作用域，是 Lead 明确指派（「更新 `tools/lang_json_to_lang.py` 让后续重新生成时也产出 1.7.10 形态的键」）。
> 改前 `git status` 确认该目录无他人未提交改动。

### 1.2【P0】`CommonAssetsManager.registerEventHandlers()` 传 `Class`，事件处理器从未注册（本次修复）

**现象**：`resource/CommonAssetsManager.java` 原 `registerEventHandlers()` 是
`MinecraftForge.EVENT_BUS.register(CommonAssetsManager.class)` + `FMLCommonHandler.instance().bus().register(CommonAssetsManager.class)`。
1.7.10 的 `EventBus#register(Object)` 取 `object.getClass()` 上的 `@SubscribeEvent`，传 `Class` 等于注册
`java.lang.Class`（同一个坑本仓 `docs/PORT_STATUS.md` 已记录两次：「`MinecraftForge.EVENT_BUS.register(XxxClass.class)`
什么都不会注册……一律传实例」）。

**后果链（玩家可见，正是本次要查的「无名字」）**：
`ClientProxy.preInit:115` 调它 → 两个处理器都没注册 → `onPlayerLoggedIn`（`CommonAssetsManager.java:499-506`，
`PlayerEvent.PlayerLoggedInEvent`）永不执行 → `ServerMessageSyncGunPack` 一包都不发（全仓唯一发送点就是它，
`grep ServerMessageSyncGunPack` 确认 `SyncConfigSyncManager:58` 只发 config）→ 多人服客户端
`CommonNetworkCache` 恒空 → `TimelessAPI.getCommonGunIndex` 为空 → `ClientIndexManager.loadGunIndex():93-105`
遍历空集合 → `GUN_INDEX` 空 → `AbstractGunItem#getItemStackDisplayName` 走 `super` → 显示原始 key
（修复 1.1 之前是 `item.tacz.modern_kinetic_gun.name`，之后退化为 "Gun"）；同时也意味着**没有任何 display/model**。
附带影响：`onWorldUnload` 也不触发 → 退出存档后 `INSTANCE` 残留。

**修法**：新增 `private static CommonAssetsManager eventHandler;` 作注册载体，`registerEventHandlers()` 幂等，
注册实例而不是 `Class`（处理器本身是 `static`，载体只用于让 EventBus 扫到方法）。
改动位置：`resource/CommonAssetsManager.java:110-113`、`487-497`。`get_file_problems(errorsOnly)` → **0 error**。
**置信度：高**（源码逐行可推，且与仓内既有结论一致）。

### 1.3 复核：PORT_STATUS 记录的 4 条线索当前状态

| # | PORT_STATUS 记录 | 复核结论 | 证据 |
|---|---|---|---|
| 1 | 14 个 `data/*_data.json` 因 gson 2.2.4 不支持 `EnumMap` 反序列化失败 → 新增 `EnumMapTypeAdapterFactory` | **已修且覆盖完整**。全仓 `EnumMap` 字段共 4 处，全部被覆盖：`GunData.fireModeAdjust`(:91)、`GunData.chargeData`(:140) 走 `CommonAssetsManager.GSON`(:128 注册工厂)；`GunDisplay.controllableData`(:114) 走 `ClientAssetsManager.GSON`(:99 注册工厂)；`GunResult.attachments`(:17) 由 `GunSmithTableResult.fromJson:133` 用同一个 `CommonAssetsManager.GSON` 反序列化。其余 209 个 data json 的字段类型审计见 §六，**无第二种踩 gson 2.2.4 的类型** | `resource/serialize/EnumMapTypeAdapterFactory.java:43-113`；`CommonAssetsManager.java:126-128`；`client/resource/ClientAssetsManager.java:97-99` |
| 2 | `ClientAssetsManager.reloadAndRegister` 全仓 0 调用者 → 客户端枪包数据从未装载 → `packInfo` 恒 null 崩溃 | **已修，时机正确**。`ClientGunPackReloader.init()` 由 `ClientProxy.postInit:264` 调；FML 时序 `FMLClientHandler.finishMinecraftLoading` = `Loader#initializeMods()`(:311，即 init+postInit) → `client.refreshResources()`(:327)，所以监听器注册后**一定**被加载期最后一次 `refreshResources` 回调。首次 `registerReloadListener` 的同步回调被 `skipNextCallback` 正确丢弃（此时 `TextureStitchEvent.Post` 未发生）。失败隔离：`reloadAll()` 对每个 listener `try/catch(Throwable)` 并 `LOGGER.error`，另加首个 client tick 兜底（`loadedOnce`）。`getPackInfo` / 各 getter 均已空安全 | `client/resource/ClientGunPackReloader.java:62-87/96-120`；`ClientProxy.java:246-264`；`FMLClientHandler.java:302-327`；`ClientAssetsManager.java:226-318` |
| 3 | 创造标签页标题显示原始 key | **已修**。`TaczTab#getTranslatedTabLabel()` 只给 `tab.*` 前缀补 `itemGroup.`，`tacz.type.*.name` 原样返回；`itemGroup.tab.tacz.other` / `itemGroup.tab.tacz.ammo` 在**本体** `en_US.lang:71-72`；13 个 `tacz.type.*.name` 在**枪包** lang（各 23 个 `tacz.type.*`）→ 依赖枪包语言注入（§三） | `init/ModCreativeTabs.java:140-175`；`assets/tacz/lang/en_US.lang:71-72`；`custom/tacz_default_gun/.../lang/en_US.lang` |
| 4 | `assets/tacz/lang/*.json` 21→20 转 `.lang`；枪包同样 21→20 | **两批都在加载路径上，且是两条独立路径**（详见 §三.3）；本次又对本体这批做了键形态修正 | `assets/tacz/lang/` 20 个 `.lang` + `custom/tacz_default_gun/assets/tacz/lang/` 20 个 `.lang`，均实际存在 |

---

## 二、未修问题

### 2.1【P1 → **已在 r2-07b 修复**】外部/第三方枪包的 `.lang` 只在 `postInit` 注入一次，游戏内切语言后失效

> **结论更新**：不需要 mixin。1.7.10 的 `mergeLanguageTable(map, currentLanguage)` 是**按当前语种**取表的，
> 所以只要启动时把每个枪包的**全部语种**一次性灌进 `LanguageRegistry`，之后玩家怎么切语言都能取到对应表。
> 已新增 `GunPackLoader#injectAllGunPackLanguages()`（`resource/GunPackLoader.java:224-327`（实现在 `:261-327`））并在
> `client/init/ClientSetupEvent.java:154` 作为一个 setup step 接线（`mixin/**` 一字未动）。
> 下面是当时的分析，保留作为依据。

- **位置**：`resource/GunPackLoader.java:253-287`（`injectGunPackLanguage`），唯一调用点
  `client/ClientProxy.java:269-277`（`postInit`，取启动时的 `getCurrentLanguage().getLanguageCode()`）。
- **判定依据**：
  1. 1.7.10 的语言表装配发生在 `LanguageManager#onResourceManagerReload:78-90`，顺序是
     `Locale#loadLocaleDataFiles`（读资源包里的 `assets/*/lang/<locale>.lang`）→
     `LanguageRegistry#mergeLanguageTable(currentLocale.field_135032_a, currentLanguage)` → `StringTranslate.replaceWith(...)`。
     其中 `mergeLanguageTable:160-170` 走 `mergeWithoutOverwrite`，**只补 locale 表里没有的 key**。
  2. 语言切换 / F3+T / `/tacz reload` 都只走 `Minecraft#refreshResources`（`Minecraft.java:636-673`，`GuiLanguage` 与
     键位 1885/1890 也调它），**不会**再调 `injectGunPackLanguage` → `LanguageRegistry` 里没有新语种的外部枪包 lang
     → `mergeLanguageTable` 补不出东西 → `tacz.gun.*` / `tacz.ammo.*` / `tacz.type.*.name` 全部回落成原始 key。
  3. **本体语言不受影响**：mod 本身是 FML 资源包（`FMLModContainer.getCustomResourcePackClass():586-597` 返回
     `FMLFolderResourcePack`/`FMLFileResourcePack`，`FMLClientHandler.addModAsResource:588-612` 把它加进
     `defaultResourcePacks`），所以 `Locale#loadLocaleDataFiles` 能按语种直接读到 `assets/tacz/lang/<locale>.lang`。
  4. **内置默认枪包也不受影响**：它的 lang 在 jar 里位于
     `assets/tacz/custom/tacz_default_gun/assets/tacz/lang/*.lang`，`LanguageRegistry` 的正则
     `assets/(.*)/lang/(?:.+/|)([\w_-]+).lang`（`LanguageRegistry.java:54`，`(.*)` 可跨 `/`）在启动时会把它
     **全部 20 个语种**一次性注入（`FMLClientHandler:591` → `LanguageRegistry.loadLanguagesFor:256-323`）。
- **为什么「切完语言再注入」这条常规思路在 1.7.10 不可行**：`LanguageManager` 是
  `Minecraft` 构造器（`Minecraft.java:520-521`）里注册的**第一个**资源重载监听器，mod 的监听器只能排在它之后，
  所以在自己的监听器里注入，只对**下一次**重载生效（玩家要切两次语言）。唯一能抢到它前面的手段是
  mixin `Locale#loadLocaleDataFiles` / `LanguageManager#onResourceManagerReload` —— 而 `LanguageMixin`
  目前有意未列入 `mixins.tacz.json`，故**放弃该路线**，改用「全语种预注入」。
- **修法（已落地，零 mixin）**：`injectAllGunPackLanguages()` 遍历全部 {@code IGunPackSource} 的
  `assets/<ns>/lang/*.lang`，按语种分组解析后 `LanguageRegistry#injectLanguage` 一次灌满；
  语种码做 `[A-Za-z0-9_]` 形状校验（避免把任意 `.lang` 当语种注入）；**一个语种都没找到时打 `warn`**
  （顺带解决 §2.2 的可观测性问题）。
- **置信度：高**（时序与取表方式全部按 1.7.10 源码逐行核对）。

### 2.2【P2 · 部分已修】枪包语言注入缺「失败可观测」

- **位置**：`resource/GunPackLoader.java`（`injectGunPackLanguage` 仍静默）。
- **判定依据**：`injectGunPackLanguage` 只在 `merged` 非空时打 `info`；若某语种在所有枪包里都没有 `.lang`，
  **一条日志都不打**；`parseLangFile` 返回 `null` 时静默 `continue`。
- **r2-07b 进展**：新的 `injectAllGunPackLanguages()` 在**一个语种都没找到**时会打
  `warn("No gun pack .lang file found; gun/attachment names will fall back to raw keys")`，成功时 `info` 带语种列表。
  旧的单语种方法已降级为「补充手段」，保持原样。
- **置信度：中高**（可观测性，非功能缺陷）。

### 2.3【P2】`JsonDataManager` / `LazyJsonDataManager` 只捕获 `JsonParseException | IllegalArgumentException`，NPE 会串成整批失败

- **位置**：`resource/manager/JsonDataManager.java:88-96`、`resource/manager/LazyJsonDataManager.java:148-155`。
- **判定依据**：单个文件解析成功但**后续校验**抛 `NullPointerException`（例如
  `ClientIndexManager.loadGunIndex():97-98` 在 try 之外取 `index.getValue().getPojo()`）不会被这两个 catch 接住，
  异常会一路冒到 `CommonAssetsManager.reloadAndRegister()` → `reload()` 打 `Failed to reload gun pack data`
  并 `clearInstance()` + 重新抛出 → **服务端起不来 / 客户端整批无数据**，与「坏一个 json 只丢一个物品」的意图相反。
  同理 `ClientIndexManager.load*` 只 catch `IllegalArgumentException`，而 `pojo` 取用发生在 try 之外。
- **建议修法**：把 catch 放宽为 `RuntimeException`（上游是 `JsonParseException | IllegalArgumentException`，
  放宽属于移植特有的健壮性增强，需在注释里记差异）；`ClientIndexManager` 各 `load*` 把 `pojo` 取值移进 try。
- **r2-07b 补充**：本轮新增的 `GunSmithTableRecipeManager#loadRecipes` 与
  `GunSmithTableRecipe#initAll` 都按「逐条 `try/catch (RuntimeException)`」写，**没有**沿用这个更窄的 catch ——
  可作为后续统一时参照的写法。
- **置信度：中**（是否真会触发取决于未来坏包，当前内置包不会）。

### 2.4【P2 → **已在 r2-07b 修复**】3 个 `GunSmithTableItem` 方块物品没有注册 `IItemRenderer`（只有 `gun_smith_table` 注册了）

- **位置**：`client/init/ClientSetupEvent.java:226-279`。`registerItemRenderers()` 只按**对象身份**注册
  `ModItems.MODERN_KINETIC_GUN / AMMO / ATTACHMENT / GUN_SMITH_TABLE`；而
  `init/ModBlocks.java:65-67` 的 `WORKBENCH_111/211/121` 也是 `GunSmithTableItem` 实例。
- **判定依据**：上游是按**类**注册的（`GunSmithTableItem.java:35 initializeClient`，IP 层对所有该类实例生效），
  1.7.10 的 `MinecraftForgeClient.registerItemRenderer(Item, IItemRenderer)` 是**按 Item 实例**登记的 →
  这 3 个物品只能退化成扁平方块图标（`AbstractGunSmithTableBlock.java:89` 的 `blockIcon`）。
  名字不受影响（`GunSmithTableItem#getItemStackDisplayName:62-63` 是类级覆写，走 block index）。
- **建议修法（已落地）**：`registerItemRenderers()` 补齐 `WORKBENCH_111/211/121` 三次注册，
  `createItemRenderer` 里把 `item == ModItems.GUN_SMITH_TABLE` 换成 `item instanceof GunSmithTableItem`
  （`DefaultTableItem extends GunSmithTableItem`，因此 default 台也命中同一支），
  位置 `client/init/ClientSetupEvent.java:238-300`。
  **渲染器安全性已核**：`client/renderer/item/GunSmithTableItemRenderer.java:57-88` 在
  `GunSmithTableRenderer.getIndex(stack)` 取不到 index 时走 `orElse` 分支，用 `SLOT_BLOCK_MODEL` 画一个占位方块，
  **不会 NPE、也不会渲染成空**；`instanceof` 分派最坏情况就是「占位方块」而不是「2D 图标」。
  （`ModernKineticGunItem/AmmoItem/AttachmentItem` 仍是精确匹配 —— 这三个类没有子类。）
- **置信度：高**（注册面缺失确定；渲染器兜底分支已实读）。
- **作用域**：`client/init/**` 是我的作用域，已在本轮直接修。

### 2.5【P2】`client/gui/**` 里有两个跨 1.7.10 的 key 隐患（只记录）

- `client/gui/compat/ClothConfigScreen.java:104` 用 `gui.back`：1.7.10 原版 `en_US.lang` **有**该键（已核
  `build/rfg/minecraft-src/resources/assets/minecraft/lang/en_US.lang`），**不是问题**。
- `mixin/client/CreativeModeInventoryScreenMixin.java:273` 用 `gui.button.press`：1.7.10 原版 **没有**这个键
  （1.8+ 才有）→ 会显示原始 key。建议改成 1.7.10 存在的 `gui.button.press` 替代物（如直接 `ChatComponentText`）或补 lang。

### 2.6【P2】上游缺陷（保真不修）：2 个配件的 name key 不存在

见 §四.2。`laser_peq15` 的 `name` 字段是 `tacz.attachment.laser_peq15`（连 `.name` 后缀都没有），
`sight_p90` 的 `tacz.attachment.sight_p90.name` 也缺；`upstream en_us.json` 与 `zh_cn.json` 里都查不到，
port 侧 index 与上游逐字相同（脚本比对 128 个 `name` 字段，全部一致）。显示结果是**原始 key**。

### 2.7【信息】等级提升提示链路未接线，`gui.tacz.gun_level_up.toast` 是「不可达的死 key」

`client/gui/toast/GunLevelUpToast.java:74` 用 `ChatComponentTranslation("gui.tacz.gun_level_up.toast", icon名字)`，
但该类**全仓无任何 `new GunLevelUpToast(...)`**（`network/message/ServerMessageLevelUp.java:57` 留的是
`TODO(port)`）→ 该 key 当前不可达，两个 lang 文件里也都没有。将来接线时需要同时补 key（值需含 `%s`）与调用点。
另：`toast.tacz.level_up` / `toast.tacz.sub.*`（4 个）目前也无消费者。

### 2.8【信息】`config.tacz.*`（137 个 key）在 1.7.10 无消费者

1.7.10 没有 Forge 配置 GUI，本工程也没有 `GuiConfig`/`IConfigGuiFactory`（全仓 grep = 0），
`mcmod.info` 里也没有 `guiFactory` 字段（已读 `build/resources/main/mcmod.info`）。
这 137 个 key 是上游 `guiFactory` 用的，1.7.10 侧永远不会被查到——**死 key，但无害**，不建议删（保真 + 未来若加配置 GUI 可复用）。
同理 `advancement.tacz.*`（56 个）需要枪包 `data/**/advancements/*.json` 的消费者。
> **r2-07b 更新**：`GunPackLoader.readPackData(String)` 现在有了 **1 个**调用者
> （`GunSmithTableRecipeManager` 读 `recipes`），但 `advancements` / `loot_tables` / `tags` 三类仍无人读 →
> 那 56 个 `advancement.tacz.*` 键目前依然是死 key。后者属 completeness 审查范畴，此处仅记录「死 key 方向」。

---

## 三、内置包逐项验收表

### 3.1 物品 / 方块（17 项，全部由 `init/ModItems.java` / `init/ModBlocks.java` 注册）

**「1.7.10 实际查询键」= 从源码推出来的键，不是「应该有的键」；「修复前/后」以本次改动为界。**

| # | 物品/方块 | 注册处 | 1.7.10 查询键（主路径 → 兜底） | 修复前 | 修复后 | 贴图 | 数据加载 |
|---|---|---|---|---|---|---|---|
| 1 | `tacz:modern_kinetic_gun` | `ModItems.java:58-61` | `AbstractGunItem:460-468` → `ClientGunIndex.getName()`（枪包 key `tacz.gun.<id>.name`）→ 兜底 `item.tacz.modern_kinetic_gun.name` | 兜底缺 | ✅ 兜底已补 | `minecraft:bow_standby`（仅无渲染器时） | ✅ |
| 2 | `tacz:ammo` | `ModItems.java:62` | `AmmoItem:67-68` → `ClientAmmoIndex.getName()` → 兜底 `item.tacz.ammo.name` | 兜底缺 | ✅ | `minecraft:arrow` | ✅ 25 个 ammo index 全命中 |
| 3 | `tacz:attachment` | `ModItems.java:63` | `AttachmentItem:60-61` → `ClientAttachmentIndex.getName()` → 兜底 `item.tacz.attachment.name` | 兜底缺 | ✅ | `minecraft:compass` | ✅ 128 个 index 全命中（2 个 upstream key 缺失） |
| 4 | `tacz:ammo_box` | `ModItems.java:64` | `AmmoBoxItem:133-148` 四分支全 return（不落 super）→ `item.tacz.ammo_box.iron/gold/diamond/creative/all_type_creative` | ✅ 本来就有 | ✅ +防御性 `item.tacz.ammo_box.name` | `tacz:ammo_box` → `textures/items/ammo_box.png` ✅ | ✅ |
| 5 | `tacz:target_minecart` | `ModItems.java:65` | `item.tacz.target_minecart.name`（无覆写） | ❌ 显示 `Minecart with Target.name` | ✅ 已改名 | `tacz:target_minecart` ✅ | ✅ |
| 6 | `tacz:gun_smith_table` | `ModBlocks.java:64` + `DefaultTableItem` | `GunSmithTableItem:62` → `ClientBlockIndex.getName()`（`tacz.block.gun_smith_table.name`）→ 兜底 `tile.tacz.gun_smith_table.name` | 兜底缺 | ✅ | `tacz:gun_smith_table`（`textures/blocks/gun_smith_table.png`）✅ + `GunSmithTableItemRenderer` | ✅ 3 个 block index 全命中 |
| 7 | `tacz:workbench_a` (`WORKBENCH_111`) | `ModBlocks.java:65` | 同 #6 → 兜底 `tile.tacz.workbench_a.name` | 兜底缺 | ✅（值取枪包 `tacz.block.ammo_workbench.name`） | `tacz:gun_smith_table` ✅（三个工作台共用同一 blockIcon） | ✅ |
| 8 | `tacz:workbench_b` (`WORKBENCH_211`) | `ModBlocks.java:66` | → 兜底 `tile.tacz.workbench_b.name` | 兜底缺 | ✅（取 `gun_smith_table.name`） | 同上 ✅ | ✅ |
| 9 | `tacz:workbench_c` (`WORKBENCH_121`) | `ModBlocks.java:67` | → 兜底 `tile.tacz.workbench_c.name` | 兜底缺 | ✅（取 `attachment_workbench.name`） | 同上 ✅ | ✅ |
| 10 | `tacz:target` | `ModBlocks.java:69`（裸 `ItemBlock`） | `tile.tacz.target.name` | ❌ 原始 key | ✅ | `tacz:target` → `textures/blocks/target.png` ✅ | ✅ |
| 11 | `tacz:statue` | `ModBlocks.java:70` | `tile.tacz.statue.name` | ❌ | ✅ | `tacz:statue` ✅ | ✅ |
| 12 | `tacz:gun_pegboard` | `ModBlocks.java:71` | `GunPegboardItem:99-101` 自拼 `getUnlocalizedName(stack)+".name"` = `tile.tacz.gun_pegboard.name` | ❌ | ✅ | `tacz:pegboard` ✅ | ✅ |
| 13 | `tacz:glow_gun_pegboard` | `ModBlocks.java:72` | 同上 → `tile.tacz.glow_gun_pegboard.name` | ❌ | ✅ | `tacz:glow_pegboard` ✅ | ✅ |
| 14 | `tacz:ammo_storage` | `ModBlocks.java:73-76` | `AmmoStorageBlockItem:45-46` 只加颜色前缀，名字取 `super` → `tile.tacz.ammo_storage.name` | ❌ | ✅ | `tacz:ammo_storage` ✅ | ✅ |
| 15 | `tacz:ammo_storage_gold` | `ModBlocks.java:77-80` | → `tile.tacz.ammo_storage_gold.name` | ❌ | ✅ | ✅ | ✅ |
| 16 | `tacz:ammo_storage_diamond` | `ModBlocks.java:81-84` | → `tile.tacz.ammo_storage_diamond.name` | ❌ | ✅ | ✅ | ✅ |
| 17 | `tacz:gun_display_case` | `ModBlocks.java:85`（裸 `ItemBlock`） | `tile.tacz.gun_display_case.name` | ❌ | ✅ | `tacz:display_case_all` ✅ | ✅ |

> 另注：`item.tacz.gun_display_case`（上游 1.20 的 block-item 模型名）在 1.7.10 **永远不会被查到**
> （ItemBlock 走 `tile.*`），保留无害；`item.tacz.painting.blood_strike_1/2` 同样无消费者
> （`init/ModPainting.java:26-29` 已明确「1.7.10 不支持自定义画变体，仅作为资源 id 保留」）。

### 3.2 内置枪包 232 个 index 条目（一次脚本全量核对，见 `build/i18n/gunpack-acceptance.csv`）

| 类别 | 数量 | name key 命中枪包 lang | tooltip key 命中 | `display` 资源存在 | `data` 资源存在 |
|---|---|---|---|---|---|
| guns | **70** | 70/70 | 70/70 | 70/70 | 70/70（`data/tacz/data/guns/*_data.json`） |
| ammo | **25** | 25/25 | — | 25/25（`display/ammo`） | —（上游 1.2.0 起 ammo 无独立 data 文件） |
| attachments | **128** | **126/128**（2 个上游缺陷，见 §四.2） | 128/128 | 128/128 | 128/128 |
| blocks | 3 | 3/3 | 3/3 | 3/3 | 3/3 |
| slot_adapters | 6 | 6/6 | 6/6 | 6/6 | 6/6 |
| **合计** | **232** | 230/232 | — | **100%** | **100%** |

> ⚠️ **任务描述里的「14 把枪」与磁盘不符**：`data/tacz/index/guns/` 实际有 **70** 个 json，
> `data/tacz/data/guns/` 也有 **70** 个 `*_data.json`，一一对应（脚本逐条比对 `index.data` 引用）。
> PORT_STATUS 里的「14 个 `data/*_data.json` 反序列化失败」指的是**当时只有 14 个文件**被扫到/踩到 EnumMap；
> 现规模是 70 把枪 + 25 种弹药 + 128 个配件。本报告按磁盘实际数量验收。

**70 把枪的 name key 命名规律**（抽查 + 脚本全查）：index 的 `name` 字段形如 `tacz.gun.<id>.name`，
均能在枪包 `lang/en_US.lang`（340 key）/ `zh_CN.lang`（338 key）/ ... 20 个语种里查到；`tooltip` 字段形如
`tacz.gun.<id>.desc`，同样 70/70 命中。

### 3.3 两批 `.lang` 的加载路径（回答「是否都在加载路径上」）

| 批次 | 磁盘位置 | 加载机制 | 是否在路径上 | 证据 |
|---|---|---|---|---|
| **本体 20 个** | `src/main/resources/assets/tacz/lang/*.lang` | ① mod 自身是 FML 资源包 → `Locale#loadLocaleDataFiles` 按语种直读；② FML `LanguageRegistry` 启动时扫 jar 内 `assets/*/lang/*.lang` 注入 | ✅ **两条独立路径都在** | `FMLModContainer.java:586-597`（`getCustomResourcePackClass` 恒返回 Folder/File pack）；`FMLClientHandler.java:588-612`；`Locale.java:32-59`；`LanguageRegistry.java:54/256-323` |
| **枪包 20 个** | `.../custom/tacz_default_gun/assets/tacz/lang/*.lang` | ① FML 正则能匹配 `assets/tacz/custom/tacz_default_gun/assets/tacz/lang/en_US.lang`（`(.*)` 跨 `/`）→ 启动时**全语种**注入；② `GunPackLoader.injectGunPackLanguage(locale)` 从导出目录 `<gamedir>/tacz/tacz_default_gun/assets/tacz/lang/<locale>.lang` 再注入一次 | ✅ **两条都在** | `LanguageRegistry.java:54`；`CLIENT` 侧 `FMLClientHandler:591`；`GunPackLoader.java:253-287`；`ClientProxy.java:274`；磁盘证据 `run/client/tacz/tacz_default_gun/assets/tacz/lang/` 下 **20 个 `.lang` 实际存在**（默认包导出成功） |

> `injectGunPackLanguage` 的路径过滤已核：`GunPackResources.ASSETS_ROOT="assets"`，
> `DirectoryGunPackSource#listFiles` 返回 `/\` 归一化的包内相对路径，`startsWith("assets/") && endsWith("/lang/<locale>.lang")` 命中；
> zip 包同理（`ZipGunPackSource`）。
> **注入值本身是完整键**（Lead 点名要确认的那条）：`injectGunPackLanguage` 用
> `StringTranslate.parseLangFile` 解析后原样 `putAll` 进 `LanguageRegistry`，**不做任何键改写**；
> 枪包 index 的 `name` 字段是完整 lang key（如 `tacz.gun.ak47.name`），
> 调用方 `ClientGunIndex/ClientAmmoIndex/ClientAttachmentIndex/ClientBlockIndex#getName()` 取出后由
> `StatCollector.translateToLocal(...)` 翻译 —— 全程键形态一致，**不受 1.1 那个 bug 影响**，✅ 确认。

---

## 四、缺失 lang key 清单

### 4.1 本体 `.lang`（本次已全部补齐，此表为「修复前缺口」存档）

统一说明：修复前**所有** 1.7.10 派生键都不存在，`grep '^tile\.tacz'` 与 `grep '\.name$'` 在 20 个文件里均为 0 命中。

| key（1.7.10 实际查询） | 代码引用位置 | 影响 | 状态 |
|---|---|---|---|
| `tile.tacz.gun_smith_table.name` | `init/ModBlocks.java:64,96` → `Item.java:742` | 方块物品名显示原始 key | ✅ 已补 |
| `tile.tacz.workbench_a.name` | `ModBlocks.java:65` | 同上 | ✅ 已补 |
| `tile.tacz.workbench_b.name` | `ModBlocks.java:66` | 同上 | ✅ 已补 |
| `tile.tacz.workbench_c.name` | `ModBlocks.java:67` | 同上 | ✅ 已补 |
| `tile.tacz.target.name` | `ModBlocks.java:69` | 同上 | ✅ 已补 |
| `tile.tacz.statue.name` | `ModBlocks.java:70` | 同上 | ✅ 已补 |
| `tile.tacz.gun_pegboard.name` | `ModBlocks.java:71` + `item/GunPegboardItem.java:99-101`（自拼 `+".name"`） | 同上 | ✅ 已补 |
| `tile.tacz.glow_gun_pegboard.name` | `ModBlocks.java:72` + 同 | 同上 | ✅ 已补 |
| `tile.tacz.ammo_storage.name` | `ModBlocks.java:73` + `item/AmmoStorageBlockItem.java:45-46` | 同上 | ✅ 已补 |
| `tile.tacz.ammo_storage_gold.name` | `ModBlocks.java:77` | 同上 | ✅ 已补 |
| `tile.tacz.ammo_storage_diamond.name` | `ModBlocks.java:81` | 同上 | ✅ 已补 |
| `tile.tacz.gun_display_case.name` | `ModBlocks.java:85` | 同上 | ✅ 已补 |
| `item.tacz.target_minecart.name` | `ModItems.java:65`（无覆写）→ `Item.java:742` | 修复前显示 `Minecart with Target.name` | ✅ 已改键 |
| `item.tacz.ammo_box.name` | `item/AmmoBoxItem.java:133`（防御性；当前不可达） | 无（不可达） | ✅ 已补 |
| `item.tacz.modern_kinetic_gun.name` | `api/item/gun/AbstractGunItem.java:460-468` 的 `super` 回退 | 索引缺失时显示原始 key | ✅ 已补 |
| `item.tacz.ammo.name` | `item/AmmoItem.java:67-68` 的 `super` 回退 | 同上 | ✅ 已补 |
| `item.tacz.attachment.name` | `item/AttachmentItem.java:60-61` 的 `super` 回退 | 同上 | ✅ 已补 |

### 4.2 枪包 `.lang` 缺失（**上游缺陷，按保真不修**）

| key | 引用位置 | 判定 |
|---|---|---|
| `tacz.attachment.laser_peq15` | `data/tacz/index/attachments/laser_peq15.json` 的 `"name"` | 上游 `en_us.json`/`zh_cn.json` 都没有；且该字段**连 `.name` 后缀都没写**（上游手误）。显示为原始 key |
| `tacz.attachment.sight_p90.name` | `data/tacz/index/attachments/sight_p90.json` 的 `"name"` | 同上，上游缺 |

依据：`Select-String tmp/TACZ-1.20.1/.../lang/en_us.json -Pattern 'laser_peq15|sight_p90'` → 0 命中；
port 侧 128 个 attachment index 的 `name` 字段与上游**逐字相同**（脚本比对）。

### 4.3 代码字面量 key × `.lang` 差集（`build/i18n/code-key-diff.csv`，225 个字面量 key）

- 命中本体 `en_US.lang`：**194 / 225**；命中本体或枪包：**211 / 225**。
- 剩余 **14** 条**全部**是「动态拼接 / 原版 key / 非翻译键 / 不可达路径」的误报，逐条核过（下表 13 行，其中前两行合并了两个 key）：

| key | 位置 | 判定 |
|---|---|---|
| `tooltip.tacz.attachment.%s` | `client/gui/components/refit/GunAttachmentSlot.java:43-46` | `String.format` 模板，实键 `tooltip.tacz.attachment.<type>`（scope/muzzle/stock/...）全部存在 |
| `tooltip.tacz.attachment.%s.increase` / `.decrease` | `resource/modifier/custom/ExtraMovementModifier.java:69,73` | 同上，实键 `tooltip.tacz.attachment.movement_speed.increase/decrease` 存在 |
| `tacz.type.` | `client/tooltip/ClientGunTooltip.java:263` | 前缀拼接（`+ type + ".name"`），枪包 lang 有 23 个 `tacz.type.*.name` |
| `commands.tacz.config.default_table_limit` / `.server_shoot_network_check` / `.server_shoot_cooldown_check` | `command/sub/ConfigCommand.java:105-107` | 拼 `.enabled`/`.disabled`，三对 key 都在 |
| `tacz.bullet` | `init/ModDamageTypes.java:56` | 伤害类型名，实键 `death.attack.tacz.bullet` / `.item` 都在 |
| `gui.back` / `commands.generic.num.invalid` | `client/gui/compat/ClothConfigScreen.java:104`、`command/sub/PegboardStressCommand.java:309` | 1.7.10 原版 key，**已实测存在**（`build/rfg/minecraft-src/resources/assets/minecraft/lang/en_US.lang`） |
| `key.keyboard` / `key.mouse` | `mcshim/InputConstants.java:57,59` | `Type#displayId` 前缀，全仓无 `getDisplayName` 消费者（死路径） |
| `gui.button.press` | `mixin/client/CreativeModeInventoryScreenMixin.java:273` | 1.7.10 原版**没有**该键（§2.5） |
| `gui.tacz.gun_level_up.toast` | `client/gui/toast/GunLevelUpToast.java:74` | 该类全仓无实例化点 → 不可达（§2.7） |

### 4.4 反方向（`.lang` 有、代码不引用）—— 仅供参考，不建议删

- 本体 `en_US.lang` **470** 个 key 中，能被本轮字面量扫描命中的只有 **194** 个，剩余 **276** 按类别：
  `config.tacz.*`（137，无配置 GUI，§2.8）、`advancement.tacz.*`（56，`readPackData` 无调用者，§2.8）、
  `jei.tacz.*`（11，JEI 恒未加载：`compat/jei/GunModPlugin` 只有反射探针 `mezz.jei.api.IModPlugin`，1.7.10 恒 `false`）、
  `toast.tacz.*`（4，§2.7）、`item.tacz.painting.*`（2，画变体不支持）+ 其余数据驱动/上下文限定 key
  —— 都是**结构性死 key**，不是拼写错误，保留以便未来接线。

---

## 五、缺失 / 错位贴图清单

**结论：缺失 = 0。** 全仓 12 个 `registerIcon` 调用点（13 次调用、14 个不同图标名，含 `GunPegboardBlock:93` 的三元 2 个与
`AmmoStorageBlock:123-124` 数组里的 3 个）全部指向真实存在的 png，逐一落到磁盘验证：

| 物品/方块 | `registerIcon` 参数 | 期望磁盘路径 | 实际 | 结论 |
|---|---|---|---|---|
| `ModernKineticGunItem` | `minecraft:bow_standby`（`item/ModernKineticGunItem.java:127`） | `assets/minecraft/textures/items/bow_standby.png` | `build/rfg/minecraft-src/resources/.../items/bow_standby.png` ✅ | 存在（注释里已解释：1.7.10 没有 `bow.png`，早期写 `minecraft:bow` 会 MISSING） |
| `AmmoItem` | `minecraft:arrow`（:52） | `.../items/arrow.png` | ✅ | 存在 |
| `AttachmentItem` | `minecraft:compass`（:55） | `.../items/compass.png` | ✅ | 存在 |
| `AmmoBoxItem` | `tacz:ammo_box`（:78） | `assets/tacz/textures/items/ammo_box.png` | ✅ 1493 B | 存在 |
| `TargetMinecartItem` | `tacz:target_minecart`（:49） | `assets/tacz/textures/items/target_minecart.png` | ✅ 264 B | 存在 |
| `TargetBlock` | `tacz:target`（block/TargetBlock.java:99） | `assets/tacz/textures/blocks/target.png` | ✅ | 存在 |
| `StatueBlock` | `tacz:statue`（:69） | `.../blocks/statue.png` | ✅ | 存在 |
| `GunPegboardBlock` | `tacz:pegboard` / `tacz:glow_pegboard`（:93） | `.../blocks/pegboard.png`、`glow_pegboard.png` | ✅ | 存在 |
| `GunDisplayCaseBlock` | `tacz:display_case_all`（:102） | `.../blocks/display_case_all.png` | ✅ | 存在 |
| `AmmoStorageBlock` | `tacz:ammo_storage` / `_gold` / `_diamond`（:123-124） | 三个 png | ✅ | 存在 |
| `AbstractGunSmithTableBlock` | `tacz:gun_smith_table`（:89） | `.../blocks/gun_smith_table.png` | ✅ | 存在 |

补充事实：

- `textures/item/**`（1.20 布局，6 个 png + `ammo_box/` 子目录）与 `textures/items/**`（1.7.10 布局）
  **两套并存**，`registerIcons` 全部指向 `items/`；`textures/block/**` 与 `textures/blocks/**` 同理（各 45 个文件）。
  1.7.10 的 `TextureMap` 只认 `textures/items/` + `textures/blocks/`，`item/`+`block/` 是死目录（约 45+21 个 png 冗余，无害）。
- **缺失时的表现**：1.7.10 `TextureMap#registerIcon` 在找不到文件时返回 `missingImage`（粉黑格）并打
  `domain ... is missing N texture(s)` 警告，**不会 NPE**；所以即使将来漏贴图也只是粉黑格而非崩溃。
- **物品图标的真实来源**：4 个物品由 `IItemRenderer` 接管（`client/init/ClientSetupEvent.java:226-279`
  注册 `GunItemRendererWrapper`/`AmmoItemRenderer`/`AttachmentItemRenderer`/`GunSmithTableItemRenderer`，
  `MinecraftForgeClient.registerItemRenderer`），`registerIcons` 只作背包/JEI 兜底 —— 这与上游
  `Item#initializeClient` 的 4 处一一对应（`AbstractGunItem:383`/`AmmoItem:76`/`AttachmentItem:71`/`GunSmithTableItem:35`）。
  见 §2.4：3 个 `GunSmithTableItem` 实例漏注册。

---

## 六、数据加载失败清单

**结论：内置包 0 项加载失败。** 以下是对 gson 2.2.4 兼容性的全量审计与风险点。

### 6.1 引用完整性（脚本全量，`build/i18n/gunpack-acceptance.csv`）

- `data/tacz/index/{guns,ammo,attachments,blocks,slot_adapters}` 共 **232** 个 index；其 `display` 字段
  全部能在 `.../assets/tacz/display/<kind>/` 找到；`data` 字段全部能在 `.../data/tacz/data/<kind>/` 找到 → **0 缺失**。
- `gunpack.meta.json`（pack 根）与 `gunpack_info.json`（`assets/tacz/gunpack_info.json`）均存在；
  其 `name`/`desc` 字段是 `pack.tacz.tacz_default_gun.name/.desc`，在枪包 lang 里**存在**（20 语种）。
  `PackInfoManager` 的扫描前缀 `assets/`（`PackInfoManager.java:47-49`）与实际位置一致 ✅。

### 6.2 gson 2.2.4 字段类型审计（`resource/pojo/**` + `client/resource/pojo/**` + `index/**` + `modifier/**`）

脚本枚举了全部 POJO 字段类型，逐个判定：

| 类型 | 出现次数 | 2.2.4 兼容性 | 处置 |
|---|---|---|---|
| `float`/`int`/`double`/`long`/`boolean` 及数组、`Integer`/`Number[]` | 240+ | ✅ 原生 | — |
| `String`/`ResourceLocation`/`List`/`Map`/`LinkedList`/`TreeMap`/`JsonElement`/`JsonObject` | 170+ | ✅ 原生（`ResourceLocation` 有自定义 adapter；`TreeMap` 由 `@JsonAdapter` 等价的显式注册覆盖） | `CommonAssetsManager.java:110-123` |
| **`EnumMap`** | **4** | ❌ 2.2.4 会构造 `LinkedHashMap` 再 set 字段 → `IllegalArgumentException` | ✅ 已由 `EnumMapTypeAdapterFactory` 覆盖（§1.3 #1） |
| `AttachmentScriptManager.CompiledScript` / `Ease` / `CompletableFuture` | 6 | ✅ 全部 `transient`，gson 不碰 | — |
| `IChatComponent`（`ClientAttachmentVariantIndex:name`） | 2 | ✅ 非 JSON 字段（运行期构造），由 `CommonTransformObject.Serializer`/checkName 赋值 | 人工确认 |
| `GunSmithTableResult`/`GunResult` | 2 | ✅ 走 `GunSmithTableResultSerializer` + `GunSmithTableResult.fromJson:98-145` | 人工确认 |
| `Ignite`/`BuiltInAttachment`/`ExtraDamage.DistanceDamagePair`/`Vec3`/`Pair` | 6 | ✅ 均有显式 `registerTypeAdapter` | `CommonAssetsManager.java:110-123` |
| `Modifier`（21 处） | 21 | ✅ `ModifierTypeAdapterFactory` 显式注册（2.2.4 无 `@JsonAdapter`） | `CommonAssetsManager.java:125` |
| record：`SmokeDirectionData.Config`、`LootTableInjection`、`TabConfig`、`AnimationKeyframes.Keyframe` | 4 | `TabConfig` 有 `TabConfig.Deserializer`（已注册，`:123`）；`Keyframe` 有 `AnimationKeyframesSerializer`；`SmokeDirectionData.Config`/`LootTableInjection` **反射反序列化** —— jvmDowngrader 会把 record 降级成普通 final 字段类，2.2.4 走 `Unsafe.allocateInstance` + `Field#set` 通常可行，但**未经运行验证** | 记为**低风险待观察**（不在内置包的关键路径上：`smokeDirection` 是 `AttachmentData` 的 `JsonElement` 字段，默认数据里没有 record 实例化） |
| `AdsCurve`（enum 别名） | 4 | ✅ 上游 `@SerializedName(alternate=...)` 已改成手工 `parseEase()`（`AdsCurve.java:49-70`，兼容 snake_case 与 camelCase），**不依赖 gson 的 enum 别名能力** | 人工确认 |

- **`@SerializedName` 的 `alternate`**：全仓 POJO 无使用（grep `alternate\s*=` 只有注释）→ 无风险。
- **`ExclusionPolicy`/`@JsonAdapter`**：无使用（`Modifier` 的 `@JsonAdapter` 已改为显式工厂）。

### 6.3 必填字段缺失时的行为（「物品无模型无名字」的失败模式）

- `JsonDataManager.apply:83-97` / `LazyJsonDataManager:148-155`：单个文件 `JsonParseException | IllegalArgumentException`
  → 打 `error`（带 marker 与 id）→ **丢弃该条目**，其余继续。失败后果：该枪/弹/配件不在 index 里 → 物品仍是
  `tacz:modern_kinetic_gun` 但**没有 display**（无 3D 模型）+ 名字落到兜底键。**这不是「无图标」**：背包图标来自
  `registerIcons` 的占位图标（§五），仍会显示。
- `ClientGunIndex/ClientAmmoIndex/ClientAttachmentIndex/ClientBlockIndex#checkName`：`name` 空/空白 →
  `custom.tacz.error.no_name`（本体 lang 里有 `custom.tacz.error.no_name` 20 语种齐全）→ **不会显示原始 key**，
  显示 "Error: No Name" / "错误：没有名字"。✅ 这是正确的降级。
- `GunIndexPOJO` 的 `item_type` 有默认值 `"modern_kinetic"`、`PackInfo` 的 `name` 默认 `custom.tacz.error.no_name`、
  `TabConfig.Deserializer:182` 的 `name` 默认 `tacz.type.unknown.name`（枪包 lang 有）→ 缺字段都有安全默认值。

### 6.4 资源路径前缀

- `DisplayManager`/`LazyJsonDataManager` 全部显式传 `GunPackResources.ASSETS_ROOT`（`ClientAssetsManager.java:141-213`），
  其中 `bedrockAnimation` 的 `ASSETS_ROOT` 是 PORT_STATUS 记录过的坑（曾在 `data/` 找动画 → display 被丢弃 → 枪无模型），
  **现已补上**（`ClientAssetsManager.java:199-209`，注释也写明了历史）。✅

### 6.5 「显式 null 覆盖 gson 字段默认值」专项复查（r2-07b 追加）

**机制**：字段上写了 `= 某个非 null 默认值` 并不等于安全 —— json 里出现**显式 null**（`"x": null`）时，
gson 的 `ReflectiveTypeAdapterFactory` 读到 `JsonToken.NULL` 会直接 `field.set(obj, null)`，**覆盖掉字段初始化值**
（2.2.4 与 2.10 行为一致，所以这不是移植差异）。只有「json 里没这个键」时默认值才生效。

**内置默认包不受影响**：对 `custom/tacz_default_gun` 下**全部** json 做 `:\s*null` 扫描 → **0 命中**。
所以下面的问题只可能在第三方枪包写出显式 null 时触发。

**我按这个模式复查了 common 侧全部 179 个「带非 null 默认值」的 POJO 字段，只挑「会被无条件解引用」的组合，修了 3 处**
（都在我的作用域 `resource/**`）：

| # | 字段 | 显式 null 后的真实后果 | 处置 |
|---|---|---|---|
| 1 | `data/block/BlockData#tabs` | `getTabs()` 里 `tabs.isEmpty()` → **每帧渲染路径 NPE**（`GunSmithTableScreen#classifyRecipes`） | ✅ 已加 `tabs == null \|\|` 判定 |
| 2 | `data/block/BlockData#filter` | `@NotNull` 违背；`CommonBlockIndex#checkData:48-50` 拿它查 filter，null 会以「没有对应数据文件」的误导信息失败 | ✅ 已回落默认值 |
| 3 | `BlockIndexPOJO#id` | `CommonBlockIndex#checkIndex:31-35` 直接 `id.getResourceDomain()` → **加载期 NPE**，且该 NPE 不在 `JsonDataManager#apply` 的 `catch (JsonParseException \| IllegalArgumentException)` 范围内 → 冒到 `CommonAssetsManager#reload()` → **服务端起不来** | ✅ 已回落默认值 |

**复查后判定「不需要改」的（都有下游防御或本身不是问题）**：

- `AttachmentVariantIndexPOJO#targets/#targetTags`、`SlotAdapterIndexPOJO#allowedAttachments`：
  getter 已经写了 `== null ? List.of() : ...`（`:53-59` / `:58-60`），`List.copyOf(null)` 不会发生。✅
- `GunIndexPOJO#itemType`：`GunItemBuilder#build:110-112` 已判 `itemType == null → return null`（= 空栈），不 NPE。✅
- `AttachmentIndexPOJO` 的默认值字段是 `boolean`（`builtinAttachment` / `hidden`）：基本类型不可能为 null。✅
- `AttachmentDisplay#textShows/#sounds`（`Maps.newHashMap()` 默认值）：显式 null 会返回 null，但消费方在
  `client/gui/**`、`client/renderer/**`（非我作用域），且内置包不写 null → 记录，**建议对应 owner 按此模式自查**。
- `PackInfo` 各字段（`client/resource/pojo/**`，非我作用域）：`authors` 已由 **gui-review** 在
  `GunSmithTableScreen#renderPackInfo` 补 `authors != null &&`；其余 6 个字段
  （`version` / `name` / `description` / `license` / `date` / `url` / `logo`）**建议按同一模式自查**，
  尤其 `name` 为 null 时 `new ChatComponentTranslation(packInfo.getName())`（`GunPackList:103`、`ClientGunTooltip:349`）
  的渲染行为。

> 改动文件：`resource/pojo/data/block/BlockData.java`、`resource/pojo/BlockIndexPOJO.java`（lint error = 0）。

---

## 七、复查清单（给 Lead / 后续轮次）

1. **构建后验证「名字」端到端**（我无法跑客户端）：进游戏看 → ① 创造标签页 16 个页签标题；② 拿一把 `ak47`、
   一盒 `ammo_box`、一个 `scope_acog_ta31`、一个 `gun_smith_table`、一个 `target` 方块，看 tooltip 首行；
   ③ 换成 `zh_CN` 再看一遍；④ **多人服**（这是本次 1.2 修复的关键验证点）确认客户端日志出现
   `Injected N gun pack localization keys` 且枪有名字与模型。
2. **确认 1.2 的修复没有引起重复同步**：`registerEventHandlers()` 现在是幂等的（`eventHandler != null` 提前返回），
   但要确认 `CommonProxy.preInit:115` 只被调用一次（`CommonProxy.preInit` 本身是否可能被热重载调用两次）。
3. **`tools/lang_json_to_lang.py` 的两种模式都要复跑**：`--check`（已过）与 `--escape-unicode`（未跑，
   若有人改用 `Properties` 读取则需要）。另：`run/client/tacz/tacz_default_gun/` 里的导出副本是**旧快照**，
   下次启动按 `.export-state.json` 的 hash 决定是否覆盖；本体 lang 的修复不受它影响（两批键不重叠）。
4. ~~**`§2.1`（外部枪包切语言）需要指派**~~ → **r2-07b 已在 `GunPackLoader` 内解决（全语种预注入），无需 mixin**。
   构建后验证：装一个带 `<locale>.lang` 的第三方枪包 → 进游戏切语言 → 枪名/配件名跟着变（不再出现 `tacz.gun.*` 原始 key），
   日志应有 `Injected gun pack localization for N locale(s): [...]`。
5. ~~**`§2.4`（3 个工作台物品漏 `IItemRenderer`）**~~ → **r2-07b 已按 `instanceof` 修好**；
   渲染器在取不到 block index 时会画占位方块（`GunSmithTableItemRenderer:81-87` 的 `orElse` 分支），不会崩。
   构建后只需目视确认 `workbench_a/b/c` 三个物品在背包里是 3D 台子而不是占位方块。
6. **待办确认项（我标了置信度但无法运行验证）**：① record 在 jvmDowngrader 降级后的反射反序列化；
   ② `AbstractGunItem.java:459` 的 `@SideOnly(Side.CLIENT)` 标在 `getItemStackDisplayName` 覆写上——1.7.10 服务端
   会剥离该方法（回落到 `Item` 的实现，靠本次新增的 `item.tacz.modern_kinetic_gun.name` 才不至于暴露原始 key），
   同时 FML 可能对「client-only 覆写非 client-only 方法」告警，建议 api 负责人去掉该注解（`getItemStackDisplayName`
   在 1.7.10 双端都会调用，例如死亡消息/进度）。③ `§4.2` 两个上游缺陷是否要「顺手修」（当前按保真未修）。
7. **r2-07b 配方链路的构建后验证**：① 服务端日志出现
   `gun smith table recipes: 199 scanned / 199 registered (199 for network sync)`；
   ② 打开枪械工作台，左侧列表应出现 70 把枪 / 104 个配件 / 25 种弹药三类分栏；
   ③ 拿齐铁/金/钻石/木头等原版材料做一把 `ak47` 验证**产物非空**（这一条最关键：它同时验证了 `initAll()` 已生效）；
   ④ 多人服：客户端 `CommonNetworkCache` 收到 `DataType.RECIPES` 后列表同样非空；
   ⑤ 预期仍做不出的 25 条（紫水晶 20 / 下界合金 4 / 铁粒 1）能被搜索到但合成不生效，属**已知数据差异**、不是 bug。
8. **`init/ModRecipe.java:292-295` 的过期 TODO 需删**（`init/**` 不在我作用域）：它写着「等 resource 层提供
   `readPackData("recipes")` 的接线后调用」，该接线已在 r2-07b 完成；且它引用的 `tacz:misc/blood_strike_1` 在磁盘上不存在。
9. **回归检查**：`assets/tacz/lang/*.lang` 现在每个文件多了 16 行（12 `tile.*` + 4 兜底），
   若 Lead 需要 `en_US.lang` 与上游 JSON 的严格 1:1，请以 `tools/lang_json_to_lang.py` 为唯一生成源
   （它已内置这次转换，`--check` 可复核）。
10. **r2-07b 之后仍需人眼确认的 2 件事**：① 玩家背包里 `workbench_a/b/c` 是否渲染成 3D 台子；
    ② 配方列表分栏 —— **去弹药工作台看弹药配方**（默认枪械工作台的页签里没有 ammo 类，属上游设计，见 §0.7 第 3 条更正）。
11. **「显式 null 覆盖 gson 字段默认值」这类防御（§六.5）**：我按 gui-review 的反送线索修了 common 侧 3 处
    （`BlockData.getTabs/getFilter`、`BlockIndexPOJO.getId`）；`PackInfo` 属 `client/resource/pojo/**`（不在我作用域），
    `authors` 已由 gui-review 在 GUI 侧补判空，**其余 6 个字段（version/name/description/license/date/url/logo）建议他们或
    client-resource owner 按同一模式自查一遍**（尤其 `getName()` 为 null 时 `new ChatComponentTranslation(null)` 的渲染行为）。
