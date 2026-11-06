# R4-B · 配方中「1.7.10 不存在」物品/材料的审查与等价物替换

> 模块 B 交付物。写作作用域：`crafting/**`、`init/ModRecipe.java`、本文件。
> 纪律：**未运行任何 gradle 任务**；未改任何枪包/数据包 json；未改 `docs/PORT_STATUS.md`。
> 自查手段：`mcp__idea__lint_files`（error 级 0 项）、`read_file`、`search_text`、`search_regex` +
> 只读文件扫描（PowerShell `Get-ChildItem`/`Select-String`，不涉及构建）。

---

## 0. 结论速览

| # | 类别 | 1.7.10 有没有 | 处理 | 受影响配方 |
|---|---|---|---|---|
| 1 | `minecraft:crying_obsidian`（item 字段） | ❌ 无 | → `minecraft:obsidian`（上一轮已加，本轮复核保留） | 6 |
| 2 | `minecraft:shroomlight` | ❌ 无 | → `minecraft:glowstone`（同上） | 3 |
| 3 | `minecraft:observer` | ❌ 无 | → `minecraft:daylight_detector`（同上） | 3 |
| 4 | `minecraft:end_crystal` | ❌ 无（1.7.10 只有实体） | → `minecraft:ender_eye`（同上） | 1 |
| 5 | `minecraft:copper_block` | ❌ 无 | → `minecraft:iron_block`（同上） | 1 |
| 6 | **`forge:gems/amethyst` → 矿词 `gemAmethyst`** | ❌ 原版无该矿词 | **本轮新增**：矿词空 → `minecraft:quartz` | **20** |
| 7 | **`forge:ingots/netherite` → 矿词 `ingotNetherite`** | ❌ 原版无该矿词 | **本轮新增**：矿词空 → `minecraft:gold_ingot` | **4** |
| 8 | **`forge:nuggets/iron` → 矿词 `nuggetIron`** | ❌ 原版无该矿词 | **本轮新增**：矿词空 → `minecraft:iron_ingot` + **9:1 折数量** | **1** |
| 9 | **`forge:ingots/copper` → 矿词 `ingotCopper`** | ❌ 原版无该矿词 | **本轮新增**：矿词空 → `minecraft:iron_ingot`（`GunSmithTableIngredient` + `ModRecipe` 双路径） | **30（枪包）+ 1（`ammo_workbench`）** |
| 10 | `forge:gunpowder` / `forge:leather` / `forge:rods/blaze` | ❌ 无对应矿词，但物品有 | 上一轮已硬折成 `minecraft:gunpowder`/`leather`/`blaze_rod`，本轮复核保留 | 25 / 32 / 21 |
| 11 | `minecraft:wool`（tag 原样） | ✅ 方块物品存在（`ItemCloth`） | 走 item registry 兜底；**已知差异：只匹配白色羊毛**（见 §5.3） | 1 |

- **第 6~9 项是本轮唯一「必须落地替换」的实质缺口**：上一轮它们是「能加载、永远做不出」——
  `resolveTagItem` 返回 `null` → `test()` 恒 `false` → 玩家在工作台里看得到配方却永远凑不齐料。
  55 处材料槽（30+20+4+1）受影响。
- 替换全部落在**代码层**（`crafting/OreDictionaryFallback.java` 新增 + `GunSmithTableIngredient` +
  `init/ModRecipe`），**没有改任何一个 json**。
- 保 GTNH 兼容：回退只在「矿词此刻没有任何候选」时启用；装了 GT/IC2（注册 `ingotCopper`/`gemAmethyst`/
  `ingotNetherite`/`nuggetIron`）时**完全走原矿词，成本与上游一致**。
- `init/ModRecipe` 的 18 条手写配方逐条核对完毕：除 `ingotCopper`（现已回退，不再整条跳过）外，
  其余材料全部是 1.7.10 原版确有之物，见 §2.2。

---

## 1. 覆盖清单（扫了哪些、怎么扫的）

### 1.1 数据侧（配方 json 全量）

| 对象 | 文件数 | 扫法 | 结论 |
|---|---|---|---|
| 枪包工作台配方 `src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/recipes/**` | **199**（gun 70 / attachments 104 / ammo 25） | 逐文件正则 `"(item\|tag\|block)"\s*:\s*"([^"]+)"` 聚合 | 材料槽 **570** 处：`item` 20 处（9 种）+ `tag` 550 处（19 种）；`result.type` 只有 gun/ammo/attachment（无 `custom`，产物侧不含物品 id） |
| jar 根数据包 `src/main/resources/data/tacz/recipes/**` | 18 | 全量 dump 比对 `ModRecipe` | 17 条 = `ModRecipe` 代码注册的 17 条（14 原版 shaped/shapeless + 3 custom）；第 18 条 `misc/blood_strike_1.json` **无任何消费者**（见 §5.1） |
| `data/blocks/*.json`（`ammo_workbench`/`attachment_workbench`/`gun_smith_table`） | 3 | 逐文件抽 `"item"` | 里面是**槽位过滤 id**（`tacz:modern_kinetic_gun`/`tacz:ammo`/`tacz:attachment`/`tacz:gun_smith_table`/`minecraft:arrow`），不是配方材料，全部存在于本模组或原版 |
| `data/advancements/*.json` | 23 | 逐文件抽 `"item"` | 1.7.10 无 advancement 体系，**不加载**；其中 `minecraft:elytra`(gunship.json) 在 1.7.10 不存在，但不影响配方（见 §5.2） |
| `tacz_loot_injectors/*.json` | 1 | 抽 `"tag"` | 命中的是 NBT 字符串（`{GunId:"`），**不是** item tag，误报 |
| 全 `src/main/resources/**` 里含 `"item"`/`"tag"` 的 json 目录普查 | 1454 个 json | 正则命中目录直方图 | 只有上面 5 类目录命中，无其它「被当材料使用」的 data 目录 |

### 1.2 代码侧

| 对象 | 扫法 | 结论 |
|---|---|---|
| `init/ModRecipe.java` 17 条代码注册配方 | 逐条读 | §2.2 全量核对 |
| `crafting/*.java`（8 个文件） | 全读 | `AmmoStorageUpgradeRecipe`（`Items.diamond`/`gold_ingot` ✅）、`GunPegboardDyeRecipe`（`Items.dye` ✅）无缺失物品；`GunSmithTableRecipe`/`GunSmithTableResult`/`RawGunTableResult`/`GunSmithTableSerializer` 只读 `GunSmithTableIngredient` 的 API，不含硬编码物品 |
| 谁在用 `GunSmithTableIngredient` 的 API | `Select-String -Pattern 'getMatchingStacks\|GunSmithTableIngredient'` | `client/gui/GunSmithTableScreen`（:441/:569/:1042/:1103）、`inventory/GunSmithTableMenu#doCraft`（:208）、`resource/serialize/GunSmithTableIngredientSerializer`（:42）、`resource/pojo/data/recipe/TableRecipe`。全部只读 `test()`/`getMatchingStacks()`/`getCount()`/`getIngredient()` |
| 是否有代码把 `forge:*` tag 重写 | `Select-String -Pattern 'forge:ingots\|forge:gems'`（全 java） | 只命中 `config/AmmoBlockConfigRead`（方块 tag，与配方无关）与 `crafting/**`；`resource/PackConvertor` 不重写配方 tag |

### 1.3 1.7.10 事实核对（这是「是否存在」的唯一依据，不靠记忆）

| 依据 | 位置 | 用途 |
|---|---|---|
| 原版物品注册名全表 | `build/rfg/minecraft-src/java/net/minecraft/item/Item.java:102-275`（`itemRegistry.addObject(...)`） | 逐个物品 id 核实 |
| 方块 → ItemBlock 登记 | `build/rfg/minecraft-src/java/net/minecraft/item/Item.java:277-424`（按**方块注册名**为每个方块登记 ItemBlock，`:285-287` wool → `ItemCloth`，`:423` `itemRegistry.addObject(Block.getIdFromBlock(block), s, object)`） | 证明 `GameRegistry.findItem("minecraft","wool")` 能命中方块物品 |
| 原版方块注册名全表 | `build/rfg/minecraft-src/java/net/minecraft/block/Block.java:239-419`（`blockRegistry.addObject(id, "name", ...)`） | 逐个方块 id 核实 |
| 原版矿词全表 | `build/rfg/minecraft-src/java/net/minecraftforge/oredict/OreDictionary.java:59-134`（`initVanillaEntries()` 静态注册块） | 证明 `gemAmethyst`/`ingotNetherite`/`nuggetIron`/`ingotCopper` **不在**其中（该块是完整清单，无省略） |
| `GameRegistry.findItem` 实现 | `build/rfg/minecraft-src/java/cpw/mods/fml/common/registry/GameData.java:186`（`iItemRegistry.getObject(modId + ":" + name)`） | 证明按 `ns:path` 查的是 item registry |
| `ShapedOreRecipe` 材料项类型 | `build/rfg/minecraft-src/java/net/minecraftforge/oredict/ShapedOreRecipe.java:87-111`（`ItemStack`/`Item`/`Block`/`String` 四类都接受，String 走 `OreDictionary.getOres`） | 证明 `ModRecipe` 可以把「矿词名」换成 `ItemStack` 而不破坏配方 |

---

## 2. 替换前后逐条对照表

### 2.1 枪包工作台配方（199 条，`GunSmithTableIngredient` 解析）

#### 表 A｜`"item"` 字段：1.7.10 完全不存在的注册名（5 种 / 12 处 / 11 条配方）

| 原材料 | 处数 | 1.7.10 是否存在（证据） | 原处理 | 新处理（本轮） | 理由 | 成本变化 | 受影响配方 |
|---|---|---|---|---|---|---|---|
| `minecraft:crying_obsidian` | 6 | ❌ 无。`Item.java:102-275` 与 `Block.java:239-419` 均无 `crying_obsidian`（命中 0） | `findItemStack` 返回 null → 抛 `JsonSyntaxException` → **整条配方被跳过** | → `minecraft:obsidian`（`Block.java:292`，id 49） | 哭泣的黑曜石本就是黑曜石的一种 | 黑曜石本身稀有度相同（均为“钻石镐+水+岩浆”），成本基本不变 | `ammo_mod_fmj/he/hp/i/overpressure_recoil/slug` |
| `minecraft:shroomlight` | 3 | ❌ 无（命中 0） | 同上 | → `minecraft:glowstone`（`Block.java:334`，id 89） | 1.7.10 唯一「发光方块」类材料 | 差不多（都要跑下界） | `scope_reap_ir / scope_xl50 / sight_infitac_fmp13` |
| `minecraft:observer` | 3 | ❌ 无（命中 0） | 同上 | → `minecraft:daylight_detector`（`Block.java:399`，id 151） | 1.7.10 唯一「光照探测」元件，用在镜类配方语义最接近 | 略便宜（无需下界石英） | `scope_xl50 / scope_xm157 / sight_uh1` |
| `minecraft:end_crystal` | 1 | ❌ 无物品（1.7.10 只有 `EntityEnderCrystal` 实体；`Item.java` 命中 0） | 同上 | → `minecraft:ender_eye`（`Item.java:230`，id 381） | 同属末地系材料，且是 1.7.10 末地唯一可合成的“水晶感”物品 | 略便宜（末影水晶需 7 玻璃+末影之眼+恶魂之泪） | `ammo_mod_he` |
| `minecraft:copper_block` | 1 | ❌ 无（命中 0） | 同上 | → `minecraft:iron_block`（`Block.java:284`，id 42） | 同为「9 锭 = 1 块」的金属块，纯原版唯一可替代的贱金属块 | 上升一档（铁 > 铜） | `ammo_mod_slug` |

> 这 5 项是上一轮（`docs/PORT_STATUS.md` P1-13 / r3-07#4）已落地的替身，本轮**复核保留**并补正了
> 证据条目（原先注释漏记 `observer` 的 `scope_xl50`、以及 `end_crystal`/`copper_block` 的配方名）。
> 去重后正好 **11 条**配方，与 P1-13 记录的「11 条工作台配方加载失败」一致。

#### 表 B｜`"tag"` 字段：1.7.10 矿词**有**候选（9 种 / 361 处）→ 无需替换

| 原材料 tag | 处数 | 折算矿词 | 1.7.10 是否有该矿词（证据） |
|---|---|---|---|
| `forge:ingots/iron` | 170 | `ingotIron` | ✅ `OreDictionary.java:101` |
| `forge:ingots/gold` | 68 | `ingotGold` | ✅ `OreDictionary.java:102` |
| `forge:dusts/redstone` | 45 | `dustRedstone` | ✅ `OreDictionary.java:109` |
| `forge:gems/lapis` | 39 | `gemLapis` | ✅ `OreDictionary.java:111` |
| `forge:gems/diamond` | 30 | `gemDiamond` | ✅ `OreDictionary.java:106` |
| `minecraft:logs` | 26 | `logWood` | ✅ `OreDictionary.java:63-64` |
| `forge:gems/quartz` | 14 | `gemQuartz` | ✅ `OreDictionary.java:108` |
| `forge:glass` | 12 | `blockGlass` | ✅ `OreDictionary.java:94-95` |
| `forge:dusts/glowstone` | 10 | `dustGlowstone` | ✅ `OreDictionary.java:110` |

#### 表 C｜`"tag"` 字段：折算出的矿词在原版**没有**候选 → **本轮新增回退**（4 种 / 55 处 / 55 条配方）

| 原材料 tag / 折算矿词 | 处数 | 1.7.10 是否存在（证据） | 原处理（上一轮） | 新处理（本轮） | 理由 | 成本变化 | 受影响配方 |
|---|---|---|---|---|---|---|---|
| `forge:gems/amethyst` → `gemAmethyst` | **20** | ❌ `OreDictionary.java:59-134` 全量清单无 `gemAmethyst`；`Item.java` 无 `amethyst_shard`（命中 0） | 折成矿词名后 `resolveTagItem` 返回 null → `item=null` → **`test()` 恒 false：能显示、永远做不出** | 矿词判空 → `minecraft:quartz`（`Item.java:255`，id 406） | 1.7.10 里与「紫水晶碎片」同属**非金属水晶类宝石**的只有下界石英（钻石/绿宝石贵重一个数量级、青金石在原版主要是染料矿）；且 1.20 的光学元件（observer / daylight_detector）本身就是石英系，用在 19 个瞄准镜/激光配件上最贴近 | 基本持平：紫水晶可无限再生（略便宜）→ 石英要跑下界（略贵）；**数量不变** | 19 个瞄准镜/激光配件 + `gun/aug` |
| `forge:ingots/netherite` → `ingotNetherite` | **4** | ❌ 同上清单无 `ingotNetherite`；无 `netherite_ingot`（命中 0） | 同上（恒 false） | 矿词判空 → `minecraft:gold_ingot`（`Item.java:114`，id 266） | 下界合金锭的**上游合成材料本身就是「4 金锭 + 4 下界合金碎片」**，同属 ingot 形态，是 1.7.10 里唯一「与下界合金同源」的锭；退到钻石形态不同且是宝石不是金属 | **显著下降**（省掉远古残骸/下界挖掘整段），工艺等级下降；GTNH 走原矿词时不变 | `m95 / m107 / hm50b / minigun`（3/3/3/10 个） |
| `forge:nuggets/iron` → `nuggetIron` | **1** | ❌ 同上清单无 `nuggetIron`；无 `iron_nugget`（命中 0；1.7.10 只有 `nuggetGold`，`OreDictionary.java:105`） | 同上（恒 false） | 矿词判空 → `minecraft:iron_ingot`（`Item.java:113`，id 265），并**按 9:1 折算需求数量**（json 的 18 → 2 个铁锭） | 1.7.10 没有铁粒，同材质最小单位就是锭；`9 nugget = 1 ingot`，折算后**成本完全不变**（不折算是 18 锭 = 原成本 ×9，属明显失衡） | **不变**（18 铁粒 = 2 铁锭） | `ammo/12g` |
| `forge:ingots/copper` → `ingotCopper` | **30** | ❌ 同上清单无 `ingotCopper`；无任何铜质原版材料（`Item.java` 无 `copper_ingot`、`Block.java` 无 `copper_block`，命中 0） | 同上（恒 false） | 矿词判空 → `minecraft:iron_ingot`（`Item.java:113`，id 265） | 1.7.10 原版没有任何铜质材料，弹壳/线管的「贱金属」位只能由铁锭接替（金锭贵重一个档次，且已被 `ingotNetherite` 占用） | **上升一档**（铁比铜贵约 1~2 倍），数量不变；GT/IC2 环境走原矿词时不变 | 25 个弹药物 + 4 个瞄准镜 + `gun/m1866` |

#### 表 D｜`"tag"` 字段：无对应矿词、但有唯一原版物品（3 种 / 78 处）→ 上一轮已折算，本轮保留

| 原材料 tag | 处数 | 1.7.10 是否有矿词 | 折算 | 证据 |
|---|---|---|---|---|
| `forge:gunpowder` | 25 | ❌ 无（`OreDictionary.java:59-134` 无 gunpowder 类矿词） | → `minecraft:gunpowder` | `Item.java:137`（id 289） |
| `forge:leather` | 32 | ❌ 无 | → `minecraft:leather` | `Item.java:183`（id 334） |
| `forge:rods/blaze` | 21 | ❌ 无（原版只有 `stickWood`） | → `minecraft:blaze_rod` | `Item.java:218`（id 369） |

#### 表 E｜`"tag"` 字段：tag 原样等于注册名（2 种 / 27 处）

| 原材料 tag | 处数 | 1.7.10 是否存在 | 处理 | 证据 |
|---|---|---|---|---|
| `minecraft:planks` | 1 | ✅ | `toOreDictionaryName` → `plankWood` | `OreDictionary.java:65` |
| `forge:rods/wooden` | 1 | ✅ | → `stickWood` | `OreDictionary.java:73` |
| `minecraft:wool` | 1 | ✅ 方块物品（`ItemCloth`） | 无矿词 → `minecraft:` 前缀兜底 `GameRegistry.findItem("minecraft","wool")` | `Item.java:285-287`（`new ItemCloth(Blocks.wool)`）+ `:423`（按方块注册名登记 ItemBlock）；`Block.java:277`（`"wool"`） |

> `minecraft:wool` 的**已知差异**：1.20 的 tag 覆盖 16 色羊毛，1.7.10 兜底拿到的是 damage 0
> （白色）。白色羊毛可由 4 线合成，配方仍可完成；详见 §5.3。

---

### 2.2 代码注册的原版工作台配方（`init/ModRecipe`，17 条代码 + 1 条数据）

逐条核对产物与材料（产物皆为本模组物品，材料逐一验证）：

| # | 配方 | 材料（as 代码） | 1.7.10 是否存在（证据） | 本轮处理 |
|---|---|---|---|---|
| 1 | `ammo_storage` | `Blocks.iron_block` ×8 | ✅ `Block.java:284`（id 42） | 不改 |
| 2 | `gun_smith_table` | `logWood`×3 / `ingotIron`×4 / `Blocks.iron_block` | ✅ `OreDictionary.java:63-64` / `:101` / `Block.java:284` | 不改 |
| 3 | **`ammo_workbench`** | `ingotCopper`×2 / `ingotIron`×2 / `tacz:ammo_box` | ⚠️ `ingotCopper` 原版**无**（`OreDictionary.java:59-134` 无此项）；其余 ✅ | **本轮改**：`OreDictionaryFallback.oreOrFallback("ingotCopper")` —— GT/IC2 环境返回矿词名（原行为），纯原版返回 `Items.iron_ingot`；**不再整条跳过** |
| 4 | `attachment_workbench` | `blockGlass`×4 / `Blocks.redstone_torch` / `ingotIron`×2 / `Blocks.chest` | ✅ `OreDictionary.java:94-95` / `Block.java:320` / `:101` / `Block.java:297` | 不改 |
| 5 | `iron_ammo_box` | `ingotIron` / `plankWood`×8 | ✅ `:101` / `:65` | 不改 |
| 6 | `gold_ammo_box` | `ingotGold` / `plankWood`×8 | ✅ `:102` / `:65` | 不改 |
| 7 | `diamond_ammo_box` | `gemDiamond` / `plankWood`×8 | ✅ `:106` / `:65` | 不改 |
| 8 | `gun_pegboard` | `Items.iron_ingot`×4 / `Blocks.iron_bars`×4 / `Blocks.iron_block` | ✅ `Item.java:113` / `Block.java:347` / `Block.java:284` | 不改（上游 `iron_trapdoor` 1.7.10 无，上一轮已换铁块） |
| 9 | `glow_gun_pegboard` | 同上 + `Items.glowstone_dust`×4 | ✅ `Item.java:197` | 不改 |
| 10 | `gun_display_case` | `ingotIron`×8 / `blockGlass` | ✅ | 不改 |
| 11 | `target` | `ingotIron`×4 / `Items.redstone` / `plankWood` | ✅ `Item.java:180` | 不改 |
| 12 | `target_minecart` | `tacz:target` / `Items.minecart` | ✅ `Item.java:177`（id 328） | 不改 |
| 13 | `statue` | `Blocks.stone_slab` meta **7** ×1 / `Blocks.quartz_stairs`×2 / `Blocks.quartz_block` meta **1** ×2 | ✅ 上游 `quartz_slab`/`chiseled_quartz_block` 在 1.7.10 无此 id，但 meta 语义对得上：`BlockStoneSlab.java:17` 第 8 项 = `quartz`（meta 7）、`BlockQuartz.java:17` 第 2 项 = `chiseled`（meta 1）、`Block.java:405` `quartz_stairs` | 不改（上一轮已折算，本轮**验证 meta 正确**） |
| 14 | `gunpowder`（shapeless） | `Items.flint` / `Items.sugar`×2 / `Items.coal` meta **1** ×3 → 3 火药 | ✅ `Item.java:166` / `:202` / 上游 `charcoal` 在 1.7.10 无此 id，等价物是 `ItemCoal` 的 meta 1（`ItemCoal.java:29`：`damage==1 → item.charcoal`） | 不改（已折算，本轮验证 meta 正确） |
| 15 | `ammo_storage_gold` | 自研 `IRecipe`：`Items.gold_ingot` | ✅ `Item.java:114` | 不改 |
| 16 | `ammo_storage_diamond` | 自研 `IRecipe`：`Items.diamond` | ✅ `Item.java:112` | 不改 |
| 17 | `gun_pegboard_dye` | 自研 `IRecipe`：`Items.dye`（damage 0..15） | ✅ `Item.java:200`；调色算法对齐 `RecipesArmorDyes` | 不改 |
| 18 | `misc/blood_strike_1`（数据） | `forge:rods/wooden`×8 + `minecraft:wool`×1 → `minecraft:painting` | 材料 ✅（见 §2.1 表 E）；`minecraft:painting` ✅ `Item.java:169` | **不注册**（见 §5.1：不是物品缺失问题，是数据路径无人加载） |

> 结论：18 条里**只有 `ammo_workbench` 存在真实的材料缺口**（`ingotCopper`），已按统一策略改为
> 「矿词优先 + 纯原版回退铁锭」，而不是整条跳过。

---

## 3. 实现说明（全部代码层，未改任何 json）

### 3.1 新增 `crafting/OreDictionaryFallback.java`

单一职责：**「1.7.10 矿词没有候选」时的等价原版物品回退表**，被枪包材料解析（`GunSmithTableIngredient`）
与代码注册配方（`init/ModRecipe`）共用，避免两处口径漂移。

```java
EMPTY_ORE_FALLBACK = { gemAmethyst → minecraft:quartz,
                       ingotNetherite → minecraft:gold_ingot,
                       nuggetIron → minecraft:iron_ingot,
                       ingotCopper → minecraft:iron_ingot }
FALLBACK_COUNT_DIVISOR = { nuggetIron → 9 }   // 18 铁粒 = 2 铁锭
```

API：
- `isRegistered(oreName)` —— 此刻矿词是否有候选（一次 `OreDictionary.getOres(...).isEmpty()`）
- `fallbackItem(oreName)` —— 无候选时给回退 `ItemStack`；**首次命中打一条 info 日志**（同一矿词只打一次，
  避免 `/tacz reload` 后刷 55 行）
- `oreOrFallback(oreName)` —— 给 `ShapedOreRecipe` 用：已注册 → 返回矿词名 `String`（保持原成本/GT 兼容）；
  未注册 → 返回回退 `ItemStack`；两者都不成立 → `null`（调用方跳过该配方）
- `scaleRequestedCount(oreName, requested)` —— 面额折算（仅 `nuggetIron` 9:1，向上取整，至少 1）
- `findItemStack(registryName)` —— `GameRegistry.findItem(ns, path)`（方块物品同样命中，见 §1.3）

### 3.2 `crafting/GunSmithTableIngredient.java`

- `resolveTagItem(oreName)`：矿词有候选 → 第一个候选（**原行为不变**）；否则 →
  `OreDictionaryFallback.fallbackItem(oreName)`；再否则 → `minecraft:` 前缀查物品。
- 构造函数 `GunSmithTableIngredient(String tag, int count)`：当该矿词**此刻无候选**时，
  `count = scaleRequestedCount(tag, count)`（`nuggetIron` 18→2），其余矿词原样；装 GT 时完全不折算。
- `test()` / `getMatchingStacks()` **逻辑未改**（原本就有「矿词空 → 比对代表物品」分支），
  因此回退路径自动可用；本轮只补了「两条路径必须一致」的注释。
- 文档：三级解析链（tag→具体物品 / tag→矿词 / 矿词→回退）写进类 javadoc；
  `MISSING_VANILLA_ITEM_SUBSTITUTION` 的 javadoc 补正证据（`observer`×3 含 `scope_xl50`、
  `end_crystal`/`copper_block` 的配方名）并说明与 `OreDictionaryFallback` 的分工。

### 3.3 `init/ModRecipe.java`

- `ammo_workbench`：`ingotCopper` 从 `hasAll(...)` 前置检查里移除，改 `oreOrFallback("ingotCopper")`；
  `ShapedOreRecipe` 接受 `ItemStack` 材料项（`ShapedOreRecipe.java:87-111`），形状/产物/NBT 完全不变。
- 类 javadoc 补：18 条配方的材料核对结论 + 其余矿词（`logWood/plankWood/ingotIron/ingotGold/gemDiamond/blockGlass`）
  全部由 `OreDictionary#initVanillaEntries` 注册的依据行号。

### 3.4 为什么不是「删掉 tag→矿词映射、直接改 json」

- 改 json 会同时破坏 GTNH（那里 `ingotCopper`/`gemAmethyst` 是**真材料**，改成铁锭会把 GT 的配方成本改掉），
  且 199 个 json 是上游数据，改动会与后续上游同步冲突。
- 现在这套是「**矿词优先、纯原版才回退**」，两条世界线都正确。

---

## 4. 验证

### 4.1 静态检查

| 检查 | 结果 |
|---|---|
| `mcp__idea__lint_files`（3 个改动文件，`min_severity:"error"`） | `items: []` → **0 error** |
| 同上（`min_severity:"warning"`） | 仅剩既有告警（`itemMatches` 前 null 检查多余、`describeCandidates` 未使用等，均在本次改动之前就存在）；**新增代码 0 告警** |
| 未运行 gradle | ✅（构建由 Lead 统一执行） |

### 4.2 解析/匹配链逐路径推演（对应任务要求 4）

| 路径 | 解析（`fromJson` → 构造器） | `test(stack)` | `getMatchingStacks()` | `getIngredient()` | GUI/扣料 |
|---|---|---|---|---|---|
| 纯原版 `forge:gems/amethyst` | `gemAmethyst` 无候选 → `item = Items.quartz`，`count` 不变 | 矿词空 → 比对 `item`（quartz, damage 0）✅ | `[quartz]`（非空 → GUI 画图标）✅ | 非 null ✅ | `GunSmithTableScreen#getPlayerIngredientCount` 用 `test()` 计数、`getCount()` 比较 ✅；`doCraft` 同源 ✅ |
| 纯原版 `forge:nuggets/iron`（18） | → `item = iron_ingot`，`count = ceil(18/9) = 2` | 矿词空 → 比对铁锭 ✅ | `[iron_ingot]` ✅ | 非 null ✅ | GUI 显示「2/已有铁锭数」✅ |
| 纯原版 `forge:ingots/copper` | → `item = iron_ingot`，`count` 不变 | ✅ | ✅ | ✅ | ✅ |
| GTNH（矿词已注册） | 不触发回退，`item = 矿词首个候选`，`count` = json 原值 | 走 `candidates` 遍历（**原逻辑**）✅ | 全部候选 ✅ | 非 null ✅ | 与上游一致 ✅ |
| `minecraft:wool` | 无矿词 → `item = ItemCloth(wool)`（damage 0） | 矿词空 → 比对 ✅（仅白色） | `[wool(0)]` ✅ | ✅ | ✅（见 §5.3） |
| `ammo_workbench`（`ModRecipe`） | `oreOrFallback("ingotCopper")` → 纯原版给 `ItemStack(iron_ingot)`；GT 给 `"ingotCopper"` | 由 `ShapedOreRecipe#checkMatch` 处理（`ShapedOreRecipe.java:92-106` 支持 `ItemStack`/`Item`/`Block`/`String`） | —（原版配方不走此 API） | — | 原版工作台 ✅ |

- `getIngredient()` 在所有路径下只可能为 `null` 的情形是「矿词无候选 **且** 无回退 **且** 非 `minecraft:` 前缀」
  （例如第三方枪包写了 `forge:ingots/tin` 且环境没装锡）—— 与改前一致，GUI 会跳过绘制该项而不崩。

### 4.3 不能在本模块内验证的部分

- 「199 scanned / 199 registered」这条日志只有在 Lead 构建后实机启动才能看到；但本模块**不增加**任何
  可能失败的解析路径（回退只把 `null` 换成非 `null`，异常分支反而更少）。

---

## 5. 未处理项 / 已知差异（附理由）

### 5.1 `data/tacz/recipes/misc/blood_strike_1.json` 无人加载（跨模块，非物品缺失）

- 现象：jar 根 `src/main/resources/data/tacz/recipes/**`（18 个 json）是上游 datapack 资源；1.7.10 无 datapack，
  `ModRecipe` 用代码重新注册了其中 17 条，但**第 18 条 `misc/blood_strike_1.json`（8 木棍 + 1 羊毛 → 血染画）
  没有任何代码注册**。
- 证据链：`resource/manager/GunSmithTableRecipeManager#reload` 只调 `GunPackLoader.readPackData("recipes")`，
  而 `GunPackLoader.readPackData`（`GunPackLoader.java:206-222`）只扫**枪包来源**
  （`GetJarResources.copyModDirectory` 导出的 `assets/tacz/custom/<pack>/…`），jar 根 `data/` 不在包来源里；
  旁证：实机日志 `199 scanned` 恰好等于枪包 recipes 数（70+104+25），不含这 18 个。
- **未改**：① 它不在本模块写作用域（要动 `resource/**` 或 `ModRecipe` 的注册列表）；② 它的材料
  （`forge:rods/wooden` → `stickWood`、`minecraft:wool`）在本轮修改后**都已可解析**，一旦有人把这条 json
  接进加载链就能直接用。→ 建议 Lead 单开一张任务卡（把 data 目录作为第 4 类包来源，或在 `ModRecipe`
  里补一条 `registerCustom`）。

### 5.2 `data/advancements/**` 里的 `minecraft:elytra` 等 1.16+ 物品

- 1.7.10 无 advancement 体系（`advancement/TargetHitTrigger.java:13-17` 已记录为已知差异），这 23 个 json
  **不会被加载**，其 `"item"` 字段只用于图标展示 → 不构成配方材料缺失。**未改**。

### 5.3 `minecraft:wool` 只匹配白色羊毛（已知差异，1 条配方）

- 1.20 tag `minecraft:wool` 覆盖 16 色；1.7.10 无对应矿词，兜底拿到 `ItemCloth` damage 0（白色）
  （`Item.java:285-287` + `:423`）。
- 影响：`attachments/muzzle_silencer_wraith.json` 需 2 个**白色**羊毛（1.20 里任意颜色都行）。
  白色羊毛可由 4 线合成，配方可完成。
- **未改成「任意 damage」**：1.7.10 的 `ItemStack` 用 `WILDCARD_VALUE(32767)` 表示通配，但
  `getMatchingStacks()` 的返回值同时用于 **GUI 图标绘制**（`GunSmithTableScreen#renderIngredient:1061`
  `gui.renderFakeItem`），把 damage 写成 32767 会让图标走 `getIconFromDamage` 的越界路径，
  风险大于收益。若日后要修，正确做法是给 `ItemCloth` 的 16 个 meta 逐个生成代表栈（`getMatchingStacks`
  返回 16 项，`test` 按「同 item 任意 damage」匹配），属独立小改进。

### 5.4 `Map` 结构的矿词「快照」语义（既有差异，非本轮引入）

- 1.7.10 的 `ShapedOreRecipe` 在**构造时**就把 `OreDictionary.getOres(name)` 的结果存进配方
  （`ShapedOreRecipe.java:106`），之后其它模组再往同一矿词追加物品不会反映到已注册配方。
  本轮的 `oreOrFallback` 只在「注册该配方的那一刻」判空，因此**不会**把「晚注册的矿词」误判为空
  （`ModRecipe.register()` 由 `CommonProxy#init` 调用，晚于所有模组 preInit）。**不改**（属 1.7.10 Forge 行为）。

### 5.5 `nuggetIron` 的 9:1 折算是移植特有机制

- 上游 1.20 没有「面额不同」的替换，本移植为了**不改变配方成本**才引入 `FALLBACK_COUNT_DIVISOR`。
  已在 `OreDictionaryFallback` javadoc 与 `GunSmithTableIngredient#getCount()` javadoc 里写明
  「这是平台差异，不是上游语义」。若 Lead/用户认为「GUI 显示的数量应始终等于 json 原值」，
  可把该除数删掉（一行），代价是 `ammo/12g` 成本 ×9。

---

## 6. 实机验证建议

1. **构建后启动**，`grep` 日志里的配方装载行：
   - 期望：`[TACZ] gun smith table recipes: 199 scanned / 199 registered`
   - 若仍是 199/199（上一轮已达），本轮改动的价值体现在下面第 3 条，而不是这一行。
2. **`grep` 回退日志**（本轮新增的唯一实机证据；同一矿词只打印一次）：
   ```
   [TACZ] ore dictionary gemAmethyst has no entry in this environment, recipe material falls back to minecraft:quartz
   [TACZ] ore dictionary ingotCopper has no entry in this environment, recipe material falls back to minecraft:iron_ingot
   [TACZ] ore dictionary ingotNetherite has no entry in this environment, recipe material falls back to minecraft:gold_ingot
   [TACZ] ore dictionary nuggetIron has no entry in this environment, recipe material falls back to minecraft:iron_ingot
   ```
   - 装了 GT 的环境**不应**出现这 4 行（走矿词）。
   - 也不应出现 `[TACZ] 1.7.10 环境既无矿词 ingotCopper 也无回退物`（现在纯原版也会注册 `ammo_workbench`）。
3. **纯原版（不装 GT）逐条点开工作台验证材料可凑齐**：
   - 弹药页：`9mm`（10 铁锭 + 2 火药）、`12g`（15 铁锭 + 6 火药 + **2 铁锭**）→ 期望材料格显示铁锭、数字 2 而不是 18。
   - 配件页：任一紫水晶瞄准镜（如 `scope_standard_8x`）→ 期望材料格显示**下界石英**。
   - 枪械页：`m107`/`m95`/`hm50b`/`minigun` → 期望下界合金那格显示**金锭**；`m1866` 期望铜锭那格显示**铁锭**。
   - 原版工作台：`ammo_workbench`（工作台 A）现在应可在纯原版合成（2 铁锭 + 2 铁锭 + 弹药盒）。
4. **GTNH/GT 环境回归**：同样 4 条配方应显示铜锭/下界合金锭/紫水晶碎片/铁粒（原成本），且日志无回退行 ——
   这是「没有破坏 GT 兼容」的判据。
5. 若工作台里某条配方材料格是**空的**（`getMatchingStacks()` 返回空），说明有第三方枪包用了本表未覆盖的
   矿词（如 `forge:ingots/tin`），届时按同样口径在 `OreDictionaryFallback.EMPTY_ORE_FALLBACK` 加一行即可。

---

## 7. 变更文件清单

| 文件 | 变更 |
|---|---|
| `src/main/java/com/tacz/guns/crafting/OreDictionaryFallback.java` | **新增**（回退表 + `isRegistered`/`fallbackItem`/`oreOrFallback`/`scaleRequestedCount`/`findItemStack`） |
| `src/main/java/com/tacz/guns/crafting/GunSmithTableIngredient.java` | `resolveTagItem` 接入回退；tag 构造器按面额折算数量；javadoc/证据补正；`findItemStack` 委托到新类 |
| `src/main/java/com/tacz/guns/init/ModRecipe.java` | `ammo_workbench` 的 `ingotCopper` 改为「矿词优先 + 回退铁锭」，不再整条跳过；javadoc 补 18 条配方材料核对结论 |
| `docs/review/r4/02-recipes.md` | 本报告 |

**没有修改任何 `.json`**（配方数据保持上游原样）。
