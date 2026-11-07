# r8 · 自定义枪包（zip）模型不渲染 / 语言键不翻译

**实机反馈（2026-11-07 二轮）**：把 1.20 格式的自定义枪包（含 zip）放进 `run/client/tacz/` 并 `/tacz reload` 后：

1. 自定义枪包的枪**完全不渲染**（第一人称空手，第三人称只剩物品粗略图标）；日志刷
   `Failed to read raw data file assets/<ns>/geo_models/...` 与
   `Failed to load gun model ...: there is no corresponding model file`；
2. 物品名/描述/pack 名显示原始语言键（如 `sk25tkm.gun.s25o.name`、`pack.sk25tkm.name`），
   即使 zip 内自带 `assets/<ns>/lang/zh_cn.json`。

---

## 问题 1：自定义枪包模型读不到（zip 句柄被下一轮重扫提前关闭）

### 根因（1.7.10 单人游戏平台差异）

单人游戏里客户端与集成服**共用同一个 `GunPackLoader` 静态实例**，但 `/tacz reload` 会走两遍扫描：

| 顺序 | 线程 | 入口 | 动作 |
|------|------|------|------|
| ① | Client thread | `ClientAssetsManager#reloadAllPack`（`ReloadCommand` 的客户端分支） | `GunPackLoader.INSTANCE.reloadPackList()` → 新 `ZipGunPackSource` S1；随后 `minecraft.refreshResources()`，客户端数据管理器（`ClientAssetsManager` 的 `LazyJsonDataManager`）扫描 S1 并**只登记**「source + 包内路径」的懒加载器 |
| ② | Server thread | 集成服 `CommonAssetsManager#reloadAllPack` | 又调一次 `reloadPackList()`：旧实现开头 `closeSources()` **关闭 S1 的 zip 句柄**，创建 S2 |
| ③ | Client thread | 服务端数据同步后 `ClientIndexManager#reload` | 首次真正读模型/动画：懒加载器用**已退役的 S1** `open()` → `null` → `GunDisplayInstance#checkTextureAndModel` 抛 `there is no corresponding model file` → display 被丢弃 → 枪无模型（第一人称不渲染、第三人称退化成物品图标） |

模型/动画是**懒加载**（默认包 `tacz` 命名空间在重载时 eager 载入，自定义包只登记路径），因此错误只在
「第一次真正用到该枪」时爆出来，看起来像「新加的包永远不生效」。

日志证据：`03:05:12` Client 扫描 → `03:05:13` refreshResources → `03:05:41` Server 扫描 →
`03:05:46` 起 `Failed to read raw data file assets/sk25tkm/geo_models/...` 连刷。

### 修复

`GunPackLoader#reloadPackList` 改为**跨扫描复用文件源**（新增 `SourceHandle` 表）：

- 目录形态枪包：无句柄、实时读文件 ⇒ 路径不变就复用；
- zip 形态枪包：路径 + `mtime` + 文件大小都没变才复用；被替换/删除的旧 zip 才 `close()`；
- 失败分支用 `closeAllSources()` 统一释放。

这样客户端懒加载器手里的 source 与服务端重扫后的一致，不再出现「句柄已被关」的失效窗口；
同时替换同名 zip 后再 `/tacz reload` 仍能读到新内容。

> 上游 1.20 由 `PackRepository` / `ResourceManager` 统一管理 pack 生命周期，不存在这个问题；1.7.10 的
> 自研枪包系统必须自己保证 source 生命周期覆盖所有「懒加载消费者」。

---

## 问题 2：枪包语言文件不生效（只认 1.7.10 `.lang`，不认 1.20 JSON）

### 根因

1.20 TACZ 枪包的语言文件是 **JSON**（`assets/<ns>/lang/zh_cn.json`、`en_us.json`，顶层 `{"key": "value"}`）；
1.7.10 原版语言表是 `.lang`（`key=value`）。移植早期 `GunPackLoader#injectAllGunPackLanguages` /
  `injectGunPackLanguage` 只解析 `.lang`（正则 `.../lang/<locale>.lang`），自定义 1.20 枪包的 JSON
  语言文件根本没被读 ⇒ 名称回退原始键名。

另外语种码大小写也不同：1.20 用 `zh_cn` / `en_us`，1.7.10 语言表用 `zh_CN` / `en_US`，直接按文件名注入会找不到对应语言表。

### 修复

`GunPackLoader`：

- `parseLangResource(source, path)`：`.lang` 走 `StringTranslate#parseLangFile`（保持原版语义），
  其余按 JSON 解析为 `Map<String,String>`；
- `localeOfLangFile`：同时接受 `.lang` / `.json`，并新增 `normalizeLocaleCode`（`zh_cn → zh_CN`，
  首段小写、其余段大写）；
- `injectAllGunPackLanguages`：两遍扫描（先 `.lang` 再 `.json`，同键 JSON 作为上游权威格式覆盖），
  合并后照旧 `LanguageRegistry#injectLanguage` 注入全部语种；
- `injectGunPackLanguage(locale)`（语言切换/启动自愈用）同样改为按归一化语种码匹配两种格式。

`/tacz reload` 的客户端分支本来就会在 `refreshResources()` 之前重新注入一次全量语言（`ClientAssetsManager#reloadAllPack`），
注入后原版 `LanguageManager` 重载会 `mergeLanguageTable` 到当前语种 ⇒ 不需要重启客户端。

---

## 验证方式

重新生成 jar 后：

1. `run/client/tacz/` 放 zip/目录枪包 → `/tacz reload`（或重启游戏）；
2. 日志应无 `Failed to read raw data file assets/<ns>/geo_models/...`、
   无 `there is no corresponding model file`；
3. 创造栏出现自定义枪 → 第一/第三人称应渲染完整 3D 模型与动画；
4. 物品名/描述/pack 名应翻译成中文（`assets/<ns>/lang/zh_cn.json`）。
5. 受保护枪包（含 `recursion/taczpack.dat`）只应出现一条 `protected/encrypted pack ... Pack skipped` warn，
   不注册任何物品、不再刷贴图/模型/音效缺失。

若仍有 `Failed to read ...`，说明枪包自身路径与 `gunpack.meta.json` 的 namespace 不一致（数据问题，日志会带具体路径）。

---

## 问题 3：受保护（加密）枪包无法加载 —— 属预期，已改为整包跳过（2026-11-07 追加）

**实机反馈**：`Tacz from Tarkov v1.1a-Public Version.zip` 放进 `run/client/tacz/` 后，创造栏出现 100 个物品，
但全部没有模型/贴图；日志刷：

- `Failed to load texture: eft:textures/ammo/slot/*.png`、`eft:textures/gun/slot/*.png`、
  `eft:textures/attachment/slot/*.png`、`eft:textures/gun/hud/qbz95.png`（每个物品一条 FileNotFoundException）；
- `there is no corresponding model file`（`GunDisplayInstance#checkTextureAndModel`、`ClientBlockIndex#checkModel`）；
- `Failed to load gun animation runtime eft:qbz95_display` / `Failed to load gun model eft:qbz95_display`；
- `[TACZ Sound] Missing tacz_sounds resource...`。

### 根因（不是移植 bug）

解包该 zip 后实际结构：

```
gunpack.meta.json                 {"namespace":"eft"}
assets/eft/lang/*.json            明文语言文件
assets/eft/display/**             明文 display json
data/eft/**                       明文数据（index/data/recipes/tacz_tags/scripts）
recursion/taczpack.dat            17 MB、首字节随机、无 zip/gzip/zlib 头 = 加密容器
```

即 **模型、贴图、动画、音效全部在 `recursion/taczpack.dat` 里**，明文只有数据 json。

这个 `recursion/` 容器是第三方附属模组 **TACZ-Arcana**
（<https://github.com/Hamster-Baron/TACZ-Arcana>，该枪包的配套前置）的「资产保护/封装枪包」格式：

- 官方 TACZ 1.20.1 **本体没有**读取它的能力（GitHub `MCModderAnchor/TACZ` 的 `1.20.1`、`1.21.1`
  分支均搜不到 `recursion` / `taczpack` 相关代码，官方 1.2.0 release notes 也没有该功能）；
- Arcana 的 release jar（`Arcana-1.4.2-loader-hybrid.jar`）把这部分放在 **obfuscated 的
  `group/taczexpands/dist/**`（约 1000 个类）+ `META-INF/natives/native_*_x86_64.{dll,so,dylib}` 原生库里**；
- Arcana 源码 README「Notice / 须知」原文：
  > The public source version and its direct builds do not include the ability to load protected gunpacks and skill packs.
  > 公开源码版本及其直接构建版本不具备加载封装拓展资源包（Gunpack）与封装技能包（Expansion）的能力。
  > Except for the asset protection related components, all other features are functionally identical to the official release version.

⇒ 在 1.7.10 上支持该枪包 = 移植一段**闭源 + 混淆 + 原生代码**的解密组件，且它本身是 Forge 1.20.1 mod
（Mixin 进 1.20 TACZ 类），无法移植；也不应逆向其保护格式。**结论：这类枪包在 1.7.10 上不可用，
属于枪包格式/配套前置的限制，不是移植缺陷。**

### 处理（平台差异，避免误报淹没日志）

`GunPackLoader` 增加受保护枪包识别：扫描目录/zip 时检查标记 `recursion/taczpack.dat`，

- 命中 → **整包跳过**，只打一条 warn（同名包在多次 `/tacz reload` 中只警告一次）：
  `Gun pack '<name>' is a protected/encrypted pack (recursion/taczpack.dat). ... Pack skipped; please use an unprotected version.`
- 不跳过的话：物品照常注册（100 个）、但每个物品/模型/贴图/音效都按缺失刷一条异常，几百行报错
  淹没其它真正的问题，且玩家会以为「枪包坏了」。

需要该枪包内容时，只能使用**未加密版本**的枪包；若日后拿到 Arcana 的公开格式规范/解密组件再评估。

---

## 问题 4：1.20 扁平化物品 id 在 1.7.10 解析不到（配方/战利品）

**实机反馈（同一轮日志）**：芙宁娜枪包（`芙宁娜枪包v0.2.8_hotfix2.zip`）27 条配方加载失败：

```
[TACZ] recipe furina:attachment/enchanted_book_sharpness from network skipped: Unknown item 'heart_of_the_sea'
[TACZ] recipe furina:gun/furina_book from network skipped: Unknown item 'blue_dye'
...（blue_dye / lapis_lazuli / firework_rocket / note_block / smooth_stone / netherite_* / prismarine_shard ...）
```

### 根因

上游 1.20.1 的物品 id 是 1.13 **扁平化后**的命名（`minecraft:blue_dye`、`minecraft:oak_planks`、
`minecraft:firework_rocket`），1.20 注册表能直接查到；1.7.10 是扁平化**前**的命名
（`minecraft:dye` + damage 4、`minecraft:planks` + damage 0、`minecraft:fireworks`），
`GameRegistry.findItem` 按名字查不到 ⇒ 材料解析失败。

上游对「完全不存在的注册名」的语义也不是跳过整条配方：`Ingredient.fromJson` 的 item 分支经
`ShapedRecipe#itemFromJson` → `BuiltInRegistries.ITEM.get(id)`，未知 id 返回默认值 **AIR**
（`tmp/minecraft-src-1.20.1/.../Ingredient.java:150-166`）⇒ 配方仍登记，但材料无解。
移植早期直接抛 `JsonSyntaxException` 跳过整条配方，与上游语义不一致（引用未安装附属模组物品的
配方会整条消失）。

### 修复

1. 新增 `util/VanillaItemMapping`：1.20 扁平化 id → 1.7.10 `注册名[#damage]` 的等价折算表
   （16 色染料→`dye`、16 色羊毛/地毯/染色玻璃/陶瓦/床→对应 damage、六种木头→`planks`/`log`/
   `wooden_slab`/`sapling`/`leaves`、`firework_rocket`→`fireworks`、`note_block`→`noteblock`、
   `smooth_stone`→`stone`、`melon`↔`melon_block`、鱼/花/台阶等同名改名的原版物品）。
   折算表**优先于**原样查表（1.20 的 `minecraft:melon` 是方块，1.7.10 的 `minecraft:melon`
   是西瓜片，不能先查表）。
2. 统一解析入口：`OreDictionaryFallback#findItemStack`（`GunSmithTableIngredient` /
   `init/ModRecipe` 共用）、`ItemStackJsonParser`（配方产物/创造栏图标）、
   `LootTableInjectorModifier` 都改为走该折算表。
3. `GunSmithTableIngredient`：
   - `MISSING_VANILLA_ITEM_SUBSTITUTION` 扩到 1.13+ 物品（`netherite_ingot/scrap/sword`、
     `prismarine_shard` 等，替身与 `OreDictionaryFallback` 的材质口径一致）；
   - 仍解析不到的材料：按上游 AIR 语义构造「无解材料」（`getMatchingStacks()` 空表、`test()` 恒
     false），**配方照常登记**，只打一条 debug（按 id 去重），不再整条跳过。

