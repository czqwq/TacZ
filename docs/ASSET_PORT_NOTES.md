# 资源移植说明（1.20.1 → 1.7.10）

> 工作流：`assets-port`（task-7）
> 作用域：`assets/tacz/lang/**`、`assets/tacz/textures/{items,blocks}/**`、`assets/tacz/sounds.json`、
> `assets/tacz/custom/tacz_default_gun/assets/tacz/lang/**`、`tools/**`
> 转换脚本：`tools/lang_json_to_lang.py`、`tools/port_textures_and_sounds.py`（都可重复运行、幂等）
> 1.7.10 依据源码：`build/rfg/minecraft-src/java/**`（行号在下文逐条标注）

---

## 0. 交付摘要

| 产出 | 数量 | 说明 |
|---|---|---|
| `assets/tacz/lang/*.lang` | **20** | 由 21 个 `*.json` 生成（`tr-TR.json` 合并进 `tr_TR.lang`） |
| `custom/tacz_default_gun/assets/tacz/lang/*.lang` | **20** | 同上，枪包语言 |
| `assets/tacz/textures/items/*.png` | **21** | 由 `textures/item/**` 镜像（含 `ammo_box/` 子目录） |
| `assets/tacz/textures/blocks/*.png` | **45** | 由 `textures/block/**` 镜像 |
| `assets/tacz/sounds.json` | 1（已改） | 删掉 1.9+ 的 `subtitle` 字段，其余原样；8 个 ogg 全部校验存在 |

自校验结果（两条命令，均可复跑）：

```
python tools/lang_json_to_lang.py --check              ->  CHECK: 0 problem(s)
python tools/port_textures_and_sounds.py --check       ->  OK: textures mirrored, sounds.json 1.7.10-canonical
```

`--check` 的强校验口径：把生成的 `.lang` **按 1.7.10 的解析规则回读**
（`split('=', 2)` + 跳过 `#` 行），逐 key 与源 JSON 的期望值比对 —— key 集合与 value 全等，
不只是数个数。

额外做了一次 Java 侧实证：写了个复刻 `Locale#loadLocaleData` 的探针程序（UTF-8 读行 →
`charAt(0)==35` 跳过注释 → `Splitter.on('=').limit(2)` 等价切分 → `%d/%f` 正则规范化），
对 **40 个 .lang 全量跑过**，结果是每个文件 `dropped lines = 0`、loaded keys 与 JSON 完全一致。
Probe 源码在 `%TEMP%` 跑完即弃，逻辑已在 `tools/lang_json_to_lang.py:parse_lang()` 里保留等价实现。

---

## 1. 语言文件（JSON → `.lang`）

### 1.1 文件名映射与合并

1.7.10 的 `.lang` 文件名必须是 `<语言小写>_<国家大写>`，路径固定
`assets/<domain>/lang/<Code>.lang`：

```
Locale#loadLocaleDataFiles   (build/rfg/minecraft-src/java/net/minecraft/client/resources/Locale.java:32-59)
    String s1 = String.format("lang/%s.lang", s);        // 第 40 行
```

| 源 JSON | 目标 `.lang` | key 数（本体 / 枪包） |
|---|---|---|
| `en_us.json` | `en_US.lang` | 454 / 340 |
| `zh_cn.json` | `zh_CN.lang` | 454 / 338 ⚠ |
| `zh_tw.json` | `zh_TW.lang` | 454 / 340 |
| `ja_jp` / `ko_kr` / `ru_ru` / `de_de` / `fr_fr` / `es_es` / `pt_br` / `pl_pl` / `it_it` / `uk_ua` / `vi_vn` / `th_th` / `id_id` / `ca_es` / `eu_es` / `da_dk` | `*_XX.lang` | 454 / 340 |
| `tr_tr.json` **+** `tr-TR.json` | `tr_TR.lang` | **465** / **342** |

- **`tr-TR.json` 合并规则**：同目录同时存在 `tr_tr.json`（全小写，454/340 key，完整）与
  `tr-TR.json`（大小写混写，152/87 key，内容更旧、部分值还是英文残留）。脚本约定
  **全小写文件名为权威源**，其余文件只补它缺少的 key、**不覆盖**已有值：
  - 本体：`tr_tr` 454 key + `tr-TR` 独有 11 key = **465**
  - 枪包：`tr_tr` 340 key + `tr-TR` 独有 2 key = **342**
- ⚠ 枪包 `zh_cn.json` 只有 338 key，比其它语区少 `config.tacz.client.render.enable_tooltip_id`
  与 `config.tacz.client.render.enable_tooltip_id.desc`（上游自身不一致，其它语区有）。**未擅自补**。
- 缺 key 在 1.7.10 会自动回退：`Locale` 永远先加载 `en_US`，再叠加当前语区
  （`LanguageManager#onResourceManagerReload`，`LanguageManager.java:78-90` 里
  `arraylist = ["en_US"]` + `currentLanguage`），所以少 2 个 key 只是回落成英文，不会显示裸 key。

### 1.2 ⚠ 关键判定：必须是**真 UTF-8**，不是 `\uXXXX` 转义

任务书里写的是「非 ASCII 转 `\uXXXX`，1.7.10 语言文件按 latin1 读」。**这一条与 1.7.10 实际实现不符**，
我按源码实证后改成输出真 UTF-8 字符。依据（同一个文件，三处互相印证）：

1. `Locale#loadLocaleData(InputStream)` 第 108 行
   `IOUtils.readLines(p_135021_1_, Charsets.UTF_8)` —— **按 UTF-8 解码**；
2. 同方法 114-123 行：只做「跳过 `#` 行」「按第一个 `=` 切」「`%d`/`%f` 规范成 `%s`」三件事，
   **完全没有 `\uXXXX` 反转义**。写 `\u00a7` 会原样显示成 8 个字符 `\u00a7`；
3. `Locale#checkUnicode()`（同文件 66-90 行）统计 value 里 `char >= 256` 的比例，>10% 就打开
   Unicode 字形回退 `field_135029_d`；这个 flag 经 `LanguageManager#isCurrentLocaleUnicode()`
   → `Minecraft#func_152349_b()`（`Minecraft.java:534,633`）→ `FontRenderer#setUnicodeFlag`
   （`GameSettings.java:480`、`GuiLanguage.java:125`）。只有「文件里存的就是真正的非 ASCII 字符」
   这条路径才解释得通。

「按 latin1 读、必须转义」是 **1.6.4 及更早**的行为（那时用 `java.util.Properties#load(InputStream)`，
该方法本身就是 ISO-8859-1 + `\uXXXX`）。1.7.10 换成 UTF-8 直读后，转义反而是 bug。

实测（Java 探针，复刻上面 106-126 行的逻辑跑 `zh_CN.lang` / `ja_JP.lang`）：

```
== zh_CN.lang      loaded keys 454   dropped 0   chars>=256 ratio 0.797 -> unicode font ON
   commands.tacz.reload.success = [§a§l[TACZ]§r 枪包已重载，耗时：%s ms]
== ja_JP.lang      loaded keys 454   dropped 0   chars>=256 ratio 0.860 -> unicode font ON
== en_US.lang      loaded keys 454   dropped 0   chars>=256 ratio 0.000 -> unicode font off
```

`§` 原样生效、中文/日文正常、比例正确触发 Unicode 字体 —— 三者都只有在真 UTF-8 下成立。

脚本保留 `--escape-unicode` 开关（默认关），只在将来对接老式 `Properties` 读取器时才用；
**默认行为是正确的那个**。若主 Agent 复核后仍要转义版，一条命令即可重出：

```
python tools/lang_json_to_lang.py --escape-unicode
```

### 1.3 换行：JSON 的 `\n` 回写成两字符 `\n`

`Locale#loadLocaleData` 是**逐行**解析（第 108 行 `IOUtils.readLines`）。若把 JSON 里的真实换行
原样写进 `.lang`，value 会被截断；更糟的是截断后的残行若含 `=`，会**凭空多出一个假 key**。
所以脚本把所有真实换行回写成字面量两字符 `\n`（反斜杠 + n）：

- 信息无损、可逆、不破坏按行解析；
- 源码里受影响的 value：本体 95 处、枪包 156 处（每个语区 3~8 条，例：
  `gui.tacz.gun_smith_table.filter.hand.empty`、`tacz.attachment.ammo_mod_he.desc`）；
- ⚠ **1.7.10 不会把 `\n` 再解释成换行**（探针实测加载后 value 里就是 `\n` 两字符）。
  渲染端要自己拆。**留给 client-port / common-infra 的动作项**：
  TACZ 自己的 tooltip / config GUI 文本在显示前需要
  `I18n.format(key).split("\\\\n")`（或 `.replace("\\n", "\n")` 后自行按行绘制）。
  若希望完全不动渲染代码，可把这几条 value 里的 `\n` 换成空格（脚本一改即可），代价是丢掉原作者的分行意图。
- 顺带一个归一化：枪包 `zh_tw.json` 里 `tacz.gun.mk18.desc` / `tacz.gun.urgi.desc` 上游写的是**字面量**
  `\n`（JSON 里写成 `\\n`），其它语区写的是真实换行。两边输出后形态一致，都变成 `\n`。

### 1.4 `%` 占位符

1.7.10 `Locale` 第 121 行 `Pattern.compile("%(\\d+\\$)?[\\d\\.]*[df]")` 会把
`%d` / `%.3f` / `%1$d` 这类**数值格式**统一改写成 `%s`，所以：

| 源 JSON | 1.7.10 加载后 | 结论 |
|---|---|---|
| `%s` | `%s` | 原样 |
| `%.3f`（`commands.tacz.reload.success`） | `%s` | 自动转换，传 float 也能 format |
| `%1$s`…`%4$s`（`commands.tacz.debug.pegboard.success`） | `%1$s`…`%4$s` | 原样保留 |
| `%%` | `%%` | 原样 |

脚本对这些**一律原样保留**，不预处理。

⚠ **已知上游缺陷（未修，只记录）**：`eu_ES.json` 有 3 处把 `%s` 写成了 `% s`（多一个空格）：
`message.tacz.converter.pack.finish`、`message.tacz.converter.pack.failed`、
`message.tacz.converter.pack.folder`。Java `String.format("% s", arg)` 会抛
`FormatFlagsConversionMismatchException`（已实测），1.7.10 的 `Locale#formatMessage`
（`Locale.java:140-152`）捕获后返回 `"Format error: <原文>"`。1.20 也一样坏（同样走 `String.format`），
属于**上游既有缺陷**，不是本次转换引入的。默认保留原文；要修可加开关：

```
python tools/lang_json_to_lang.py --fix-percent-space     # 只把 "% s" 改成 "%s"
```

### 1.5 空值

上游有 3 处空字符串 value（本体 `da_dk: message.tacz.converter.init_failed`、
`th_th: item.tacz.painting.blood_strike_1`；枪包 `da_dk: tacz.type.pistol.name`、
`it_it: tacz.gun.spas_12.desc`）。1.7.10 用 Guava `Splitter.on('=').limit(2)`，
`key=` 会切成 `["key", ""]` 两段（Guava 的 `Splitter` 默认**不** `omitEmptyStrings()`），
**空值能正常表达**，所以脚本照样写 `key=`，不丢 key。

实证：1.7.10 原版自己的 `assets/minecraft/lang/en_US.lang` 里就有 4 条空值 key
（`options.aoDesc1=`、`options.viewBobbingDesc1=`、`options.renderCloudsDesc1=`、`potion.potency.0=`）。

### 1.6 文件格式细节

- 无 BOM（UTF-8 no-BOM；有 BOM 时首行会多一个 `\uFEFF`，若首行是 key 就会让这个 key 查不到）；
- LF 换行（`.gitattributes` 是 `* text eol=lf`）；
- 前两行是 `#` 注释头，1.7.10 第 114 行 `charAt(0) == 35` 会跳过；
- key 顺序沿用源 JSON 的出现顺序，diff 稳定。

### 1.7 两套语言目录的加载路径不同（重要）

| 目录 | 1.7.10 谁来读 |
|---|---|
| `assets/tacz/lang/*.lang` | 原版 `Locale`（`lang/<Code>.lang` 对**所有 domain** 都扫），选中文语言时自动生效 |
| `custom/tacz_default_gun/assets/tacz/lang/*.lang` | **原版 `Locale` 不读**（不在 `<domain>/lang/` 根下）；但 Forge `LanguageRegistry` 的正则**能匹配到**，且 FML 对每个 mod 自动调 `loadLanguagesFor` → **内置枪包会被自动注入**，见 §5.1 第 4 点 |

⚠ **已裁决（见 §5.1）**：走 `LanguageRegistry` 注入。上游 1.2.0 快照里这条链路其实**没有接线**——
`mixin/client/LanguageMixin.java` 整个类体的两个 `@Inject` 全是注释掉的
（目标是 1.20 的 `ClientLanguage#getOrDefault` / `#has`），里面引用的
`ClientAssetManager.INSTANCE.getLanguages(code)` 这个类在仓库里已不存在
（现在只有 `client/resource/ClientAssetsManager`，且**不含**任何语言逻辑）。
实测枪包 140 个 `tacz.gun.*`、26 个 `tacz.ammo.*` key 在模组本体语言里**一个都没有**，
也就是说在没有接线的情况下枪械名会显示裸 key。

我这边已经把枪包语言转成 `.lang` 数据源备好。**已裁决走 `LanguageRegistry` 注入**
（`common-resource` + `common-infra` 实施），具体 API 与编码要求见 §5.1；
（更正 §1.7 上表第 2 行：`LanguageRegistry` 的正则其实**能**匹配到这个路径，
内置枪包会被 Forge 自动注入，只有运行期外部枪包才需要自己读 —— 见 §5.1 第 4 点。）

### 1.8 重复运行

```
python tools/lang_json_to_lang.py            # 覆盖生成 40 个 .lang（幂等）
python tools/lang_json_to_lang.py --check    # 只校验，不写盘
python tools/lang_json_to_lang.py --dry-run  # 只报计划
```

脚本自己处理了：嵌套 JSON 结构直接报错停下（本项目实测 21+21 个文件**全是扁平** `{key: string}`，
无嵌套）、`*.json` 里非 string 值报错、同 code 多源文件合并、语区不在 1.7.10 语言列表时告警
（当前 20 个 code 全部命中 1.7.10 `pack.mcmeta` 的语言列表，无告警）。

---

## 2. 物品 / 方块贴图目录

1.7.10 的图集基路径是**复数**：

```
Minecraft.java:585   this.textureMapBlocks = new TextureMap(0, "textures/blocks", true);
Minecraft.java:592   ... loadTextureMap(locationItemsTexture, new TextureMap(1, "textures/items", true));
TextureMap.java:316-319  completeResourceLocation -> "<basePath>/<path>.png"
```

所以 `registerIcon("tacz:ammo_box")` 只认 `assets/tacz/textures/items/ammo_box.png`。

脚本把 `textures/item/**` → `textures/items/**`、`textures/block/**` → `textures/blocks/**`
做**镜像复制**（子目录一并保留，目前只有 `ammo_box/`），**原目录不动**：

| 源 | 目标 | 文件数 |
|---|---|---|
| `textures/item/**` | `textures/items/**` | 21（含 `ammo_box/` 下 9 个） |
| `textures/block/**` | `textures/blocks/**` | 45 |

**为什么保留原目录**：客户端代码里有一批是**直接 `bindTexture` 全路径**的，不走图集，例如
`InternalAssetLoader.java:37,40,43` 的
`new ResourceLocation("tacz", "textures/block/target.png")` / `.../statue.png` / `.../gun_smith_table.png`。
1.7.10 的 `ResourceLocation` 可以指到 `textures/` 下任意路径，这些引用**继续有效**，
删掉原目录会直接打断它们。所以两个目录并存：

- **走图集**（`IIconRegister#registerIcon("tacz:xxx")`）→ 用 `textures/items|blocks/`
- **走 direct bindTexture**（`textures/block/target.png`）→ 继续用原路径

**`_n.png` / `_s.png` 在 1.7.10 用不到**：它们是 labPBR/metallness 的 normal map 与 specular map
（1.20 光影用）。共 `item` 侧 6+6、`block` 侧 15+15，脚本照样镜像（无害、保持两边一致），
清单见 §3。

`textures/{gui,hud,crosshair,entity,painting}` 全部保持原样（1.7.10 GUI 直接按路径 bindTexture 即可）。

---

## 3. `sounds.json`

### 3.1 1.7.10 认的字段

`SoundListSerializer`（`build/rfg/minecraft-src/java/net/minecraft/client/audio/SoundListSerializer.java`）：

- 事件级：`replace`（bool，23 行）、`category`（`SoundCategory` 名，缺省 **`master`**，24-26 行）、
  `sounds`（数组，28 行）
- 条目级：`name`（必填，44 行）、`type`（`file` / `event`，46-51 行）、`volume`（55）、
  `pitch`（62）、`weight`（69）、`stream`（76）

它是**手写取字段**的反序列化器，多出来的字段被**静默忽略**、不抛错。1.7.10 **没有 `subtitle`**
（字幕是 1.9 才有的）。

ogg 路径拼法（`SoundHandler.java:126-134`）：`name` 的 `path` 部分 → `sounds/<path>.ogg`，
domain 缺省取当前 `sounds.json` 所在 domain。8 个 ogg 逐个校验存在：

```
tacz:gun/empty                                     -> assets/tacz/sounds/gun/empty.ogg            OK
tacz:block/target_block_hit/target_block_hit_1..7   -> assets/tacz/sounds/block/target_block_hit/*.ogg  OK ×7
```

### 3.2 本次改动

**唯一改动：删掉 `gun.subtitle` 与 `target_block_hit.subtitle` 两个字段**，并把文件按 2 空格缩进规范化。
`replace` / `category` / `sounds` 结构、条目顺序、`name` 全部原样。

- `subtitle` 是 1.9+ 字段，1.7.10 反序列化器会忽略它，留着只是误导；删掉后 `assets/tacz/lang/*.lang`
  里的 `subtitle.tacz.gun` / `subtitle.tacz.target_block_hit` 成为**死 key**（保留，见 §3.3 清单）。
- **没有**添加 `category`：上游 1.20 的 `sounds.json` 也没写，1.20 与 1.7.10 的缺省都是 `master`，
  两边语义一致。如果移植后希望枪声跟随「玩家」音量滑杆，在事件里加 `"category": "players"`
  即可（1.7.10 音量按 `SoundEventAccessorComposite#getSoundCategory()` 取，
  `SoundManager.java:368-369,428-430`），但那会偏离上游，所以没擅自加。
- `sounds/**/*.ogg` 未动。

### 3.3 脚本

```
python tools/port_textures_and_sounds.py            # 复制贴图 + 校准 sounds.json（幂等）
python tools/port_textures_and_sounds.py --check    # 只校验
python tools/port_textures_and_sounds.py --dry-run  # 只报计划
```

它同时做三件事：镜像贴图（含清理目标目录里的过期 png，保证收敛）、按上表校验 `sounds.json`
字段（未知字段报错、`category` 合法性、每个 `name` 的 ogg 是否存在）、把结果写成规范 JSON。

---

## 4. 1.7.10 用不到的资源清单（**已裁决：全部保留，一个都不删**）

按要求「保留文件但记录」，以下全部**原样保留**，只是 1.7.10 不会加载：

| 路径 | 文件数 | 是什么 | 1.7.10 情况 |
|---|---|---|---|
| `assets/tacz/models/item/**` | 27 | 1.20 JSON 物品模型（含 `ammo_box/` 9 个） | 1.7.10 无 JSON 模型系统 → 等 client-port 决定删/留 |
| `assets/tacz/models/block/**` | 42 | 1.20 JSON 方块模型 | 同上 |
| `assets/tacz/blockstates/**` | 12 | 1.20 blockstate → 模型映射 | 同上 |
| `assets/tacz/shaders/core/**` | 13 | 1.20 core shader（`.json` + `.vsh`/`.fsh`，瞄准镜镜片/热成像） | 1.7.10 无 core shader；镜片效果需用固定管线/GLSL 自建 |
| `assets/tacz/particles/bullet_hole.json` | 1 | 1.20 粒子定义 | 1.7.10 无该格式；弹孔要用实体/贴图面片实现 |
| `textures/**/*_n.png`、`textures/**/*_s.png` | 42（item 12 + block 30） | PBR normal / specular map | 1.7.10 不用 |
| `sounds.json` 的 `subtitle` | 2 处（已删） | 1.9+ 字幕 | 已移除 |
| `lang` 里的 `subtitle.tacz.*` | 2 key × 20 语区 | 字幕文案 | 因上一条成为死 key |
| `assets/tacz/textures/painting/blood_strike_1.png` | 1 | 自定义画贴图 | 1.7.10 画作贴图必须放 `assets/minecraft/textures/painting/` 且需 `PaintingRegistry` 注册，放这里读不到 |
| `lang/*.json`（21 + 21） | 42 | 1.20 语言源 | **保留**：脚本的输入源，删了就没法重跑转换 |
| `lang/tr-TR.json`（2 个） | 2 | 重复的土耳其语（大小写错名） | 已合并进 `tr_TR.lang`；**可删**，但删了合并规则就退化成单源 |

**明确要继续用的（勿删）**：

- `models/bedrock/**`（5 个）：TACZ **自研**基岩版模型格式，自己的代码读，与 1.20 模型系统无关；
- `animations/**`（2 个）：TACZ 自研动画 JSON；
- `custom/tacz_default_gun/**` 的贴图 / 音频（`tacz_sounds/**` 共 1704 个文件）/ 模型 / JSON 数据：
  路径由枪包 JSON 定义，**整体不动**（本次只在该目录新增了 `assets/tacz/lang/*.lang`）；
- `data/**`（模组本体与枪包的 datapack 资源）：1.7.10 无数据包，需改代码注册或并入
  TACZ 自研加载器 —— **不在本工作流作用域**，列在这里避免被误当成「资源转换遗漏」。

---

## 5. 裁决结果与接线须知

> 主 Agent 已裁决（2026-xx）：① `.lang` 用真 UTF-8，不用 `\uXXXX`；② value 内 `\n` 保留字面量，
> 由 client 侧显示前 `split("\\n")`（已进 `PORT_STATUS.md` 的跨包契约，交 client-port）；
> ③ 枪包语言走 **(b) `LanguageRegistry` 注入**，不用 mixin；④ 「1.7.10 用不到」清单**全部保留不删**；
> ⑤ 上游缺陷（`eu_ES` 的 `% s`、枪包 `zh_cn` 缺 2 key）**不修，保真**。
> 因此 (a) mixin 与 (c) 自研 `I18n` 两条路**已废弃**，(a) 节内容仅留作历史参考。

### 5.1 枪包语言接线（已定：`LanguageRegistry`）——4 个必须注意的点

枪包 140 个 `tacz.gun.*` / 26 个 `tacz.ammo.*` key 在模组本体语言里一个都没有，
上游 1.2.0 快照又没有加载路径（`LanguageMixin` 全注释、`ClientAssetManager` 已不存在），
所以不接线枪名会显示裸 key。走 `LanguageRegistry` 时请按下面四点实现：

1. **API 名字对不上，别照抄**：`cpw.mods.fml.common.registry.LanguageRegistry` 没有
   `injectLanguage(String, Properties)`。可用的是
   （`build/rfg/minecraft-src/java/cpw/mods/fml/common/registry/LanguageRegistry.java`）：
   - `addStringLocalization(Properties langPackAdditions, String lang)` — 115 行；
   - `injectLanguage(String language, HashMap<String, String> parsedLangFile)` — 244 行；
   - `loadLocalization(URL|String localizationFile, String lang, boolean isXML)` — 187 / 212 行。
2. **⚠ 绝对不要用 `new Properties().load(InputStream)`**：这个重载是 **ISO-8859-1**，
   会把本项目的真 UTF-8 中文 / `§` 变成乱码。安全的两条路（FML 自己就是用这两条）：
   - `Properties.load(new InputStreamReader(in, StandardCharsets.UTF_8))` —— `loadLocalization` 第 224 行；
   - `StringTranslate.parseLangFile(in)` —— 第 57 行 `IOUtils.readLines(in, Charsets.UTF_8)`，
     解析规则与客户端 `Locale` 一致（跳过 `#` 行、首个 `=` 切分、`%d|%f`→`%s`）。
3. **事务性差异：`Properties` 路径会**多**解一层转义**。`StringTranslate.parseLangFile` 不解转义
   （与 `Locale` 一致），而 `Properties.load(Reader)` **会**处理 `\t \n \r \f \uXXXX` 与行尾续行：
   - 本项目 value 里的字面 `\n` 在 `Properties` 路径下会变成**真实换行**（在 `parseLangFile` 路径下保持字面量）；
   - 只有 2 个 value 含反斜杠（枪包 `zh_tw.json` 的 `tacz.gun.mk18.desc` / `tacz.gun.urgi.desc`，
     上游把 `\n` / `\"` 写成了字面量），走 `Properties` 路径时这些反斜杠会被吃掉
     （`\"` → `"`），视觉上反而更干净。
   - **建议统一走 `StringTranslate.parseLangFile`**，这样注入结果与本体 `.lang` 经 `Locale` 加载的
     结果语义完全一致，客户端只需要一套 `split("\\n")` 规则。
4. **内置默认枪包其实不用写代码 —— Forge 会自动注入**：
   `LanguageRegistry.assetENUSLang`（第 54 行）=
   `assets/(.*)/lang/(?:.+/|)([\w_-]+).lang`，`(.*)` 可跨 `/`，所以
   `assets/tacz/custom/tacz_default_gun/assets/tacz/lang/en_US.lang` **会被匹配上**；
   而 `FMLClientHandler.java:591` / `FMLServerHandler.java:221` 在 mod 装填时会对**每个** mod 调
   `LanguageRegistry.instance().loadLanguagesFor(container, side)`，客户端与服务端都会扫。
   也就是说：**我把 `.lang` 放进枪包目录这一步，已经让内置枪包的语言自动生效**。
   `GunPackLoader` 需要自己做的只有**运行期从游戏目录/配置目录加载的外部枪包**
   （那些文件不在 mod 源里，Forge 扫不到），把它们的 `<pack>/assets/tacz/lang/*.lang`
   用第 2 点的安全方式读进来再 `addStringLocalization`。

   优先级也不用担心：客户端 `LanguageManager` 在刷完资源后会调
   `LanguageRegistry.instance().mergeLanguageTable(currentLocale.field_135032_a, currentLanguage)`
   （`LanguageManager.java:88`），而 `mergeLanguageTable`（160-170 行）走的是
   `mergeWithoutOverwrite`（173 行起）—— **注入的数据只补缺、不覆盖资源 `.lang` 已加载的 key**。
   注入完成后 `StringTranslate.replaceWith(map)`（第 89 行），所以 `I18n` / `StatCollector`
   客户端服务端都能看到枪包语言。

### 5.2 其余已裁决项（记录，无需动作）

- **`\uXXXX` vs 真 UTF-8**（§1.2）：已采纳真 UTF-8。`tools/lang_json_to_lang.py --escape-unicode`
  作为备用开关保留，默认不用。
- **`\n` 拆行**（§1.3）：保留字面量、数据保真。显示端 `split("\\n")` 由 client-port 负责。
  **不再产出「改写成空格」的版本。**
- **「1.7.10 用不到」清单**（§4）：**全部保留，一个都不删**（不占构建时间、便于日后回取）。
- **上游缺陷不修**（§1.1 / §1.4）：枪包 `zh_cn.json` 缺的 2 个 key 保持缺失
  （1.7.10 会自动回退 `en_US`）；`eu_ES` 的 3 处 `% s` 保持原文
  （`String.format` 会抛异常并由 `Locale#formatMessage` 兜成 `Format error: ...`，1.20 同样如此）。
  `--fix-percent-space` 开关保留但**不要使用**。


---

## 6. 复核命令速查

```bash
# 语言：全量回读校验（key 集合 + value 全等）
python tools/lang_json_to_lang.py --check

# 贴图镜像 + sounds.json 规范性与 ogg 存在性
python tools/port_textures_and_sounds.py --check

# 只想看会用哪些改动
python tools/lang_json_to_lang.py --dry-run
python tools/port_textures_and_sounds.py --dry-run
```

> 本工作流**没有跑过任何 gradle 命令**（按 AGENTS.md 约定，构建由主 Agent 统一执行），
> 也没有改 `src/main/java/**`。
