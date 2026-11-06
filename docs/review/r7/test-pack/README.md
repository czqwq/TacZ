# 最小可加载枪包（r7-respack-core 交付样例）

一个不依赖任何第三方内容的最小 TACZ 1.7.10 枪包，用来验证「**自定义枪包/资源包能不能被正常发现、解析、索引、
同步，并在游戏里真的出现一把枪**」。命名空间 `tacz_review_pack`，全部内容均由本审查轮手工编写（贴图是脚本生成的
16×16/32×32 PNG），没有红石/音效等可选资源。

## 1. 目录结构

```
tacz_review_pack/
├── gunpack.meta.json                         # 枪包描述符（必需；文件名不是 gunpack.mcmeta！）
├── data/tacz_review_pack/                    # 服务端数据（data/ 根）
│   ├── index/guns/review_pistol.json         # 枪械索引（type/display/data/sort）
│   ├── index/ammo/review_9mm.json            # 弹药索引（名称/展示/堆叠数）
│   └── data/guns/review_pistol_data.json     # 枪械数值（伤害/射速/换弹/后坐/散布…）
└── assets/tacz_review_pack/                  # 客户端资源（assets/ 根）
    ├── display/guns/review_pistol_display.json   # 展示配置：模型/贴图/槽位图标/状态机/动画
    ├── display/ammo/review_9mm_display.json      # 弹药展示配置（只给了槽位图标与曳光色）
    ├── geo_models/gun/review_pistol_geo.json     # 基岩版 1.12.0 模型（3 根骨骼、2 个 cube）
    ├── textures/gun/uv/review_pistol.png         # 枪身贴图 16×16（uv 与模型描述一致）
    ├── textures/gun/slot/review_pistol.png       # 物品栏图标 32×32
    ├── textures/ammo/slot/review_9mm.png         # 弹药图标 16×16
    └── lang/en_US.lang, zh_CN.lang               # 1.7.10 语言文件（上游 1.20 是 <locale>.json）
```

`gunpack.meta.json` 内容（对应上游 `PackMeta`：只有 `namespace` 与可选的 `dependencies`，**没有版本/格式字段**）：

```json
{ "namespace": "tacz_review_pack" }
```

- `namespace` 只作为日志里的「Main namespace」，加载时**不按它过滤**（实际命名空间由 `data/<ns>/`、`assets/<ns>/`
  目录名决定）；建议仍保持一致。
- `dependencies` 可选，例如 `{"tacz": "[1.0,)"}`；写了的模组必须存在且版本落在区间内，否则该枪包被整体跳过
  （日志 `Mod version mismatch`）。本样例没写。

## 2. 怎么装（实机验证方法）

枪包根目录 = **游戏根目录下的 `tacz/`**（代码：`GunPackLoader#getPackDirectory` →
`FMLInjectionData.data()[6]`（`--gameDir`）+ `"tacz"`）。

- **本工程开发环境**：游戏根目录是 `run/client`（已由实机日志确认：
  `Start scanning for gun packs in E:\IDEA\TacZ\run\client\tacz`），因此放到
  `run/client/tacz/tacz_review_pack/`。
- **正式 1.7.10 客户端/服务端**：`.minecraft/tacz/tacz_review_pack/`、服务端根目录 `tacz/tacz_review_pack/`。

两种形态（**目录版**与 **zip 版**二选一，不要同时放）：

1. **目录版**：把整个 `tacz_review_pack/` 目录拷进 `<gamedir>/tacz/`，最终
   `<gamedir>/tacz/tacz_review_pack/gunpack.meta.json` 可读。
2. **zip 版**：把 `tacz_review_pack/` **里面的内容**压成 `<gamedir>/tacz/tacz_review_pack.zip`，
   要求 `gunpack.meta.json` 位于 zip **根**（不要多套一层 `tacz_review_pack/` 目录）。
   zip 文件名即日志里的包名。

改完内容后：

- 单人：游戏内 `/tacz reload`（会重扫 `tacz/`、重载客户端资源 + 服务端数据 + 重建索引）。
- 专用服务器：控制台/游戏内 `/tacz reload`（服务端）；连着的客户端会收到重新同步的枪包快照
  （上游 `OnDatapackSyncEvent` 广播语义，本轮补上），客户端自己的 `assets/` 仍需客户端 `/tacz reload` 或重进。
- 最保险：完全重启客户端 + 存档。

## 3. 期望日志（判定加载链路是否打通）

按出现顺序（`<gamedir>` 换成实际路径）：

| 日志 | 含义 | 不出现说明 |
|---|---|---|
| `Start scanning for gun packs in <gamedir>\tacz` | 开始扫枪包目录 | preInit 没跑到 |
| `- tacz_review_pack, Main namespace: tacz_review_pack` | **本包被发现**（目录名 + meta 的 namespace） | 目录放错层 / 缺 `gunpack.meta.json` / zip 多套一层 |
| `Found 2 possible gunpack(s) and added them to resource set.` | 默认包 + 本包 = 2 | 只有 1 说明本包没被发现 |
| `Injected gun pack localization for 20 locale(s): [...]` | 语言注入（本包只补已有语种，数量不变） | 客户端 preInit 没跑到 |
| `[TACZ] gun smith table recipes: N scanned / N registered (N for network sync)` | 配方加载器跑过（本包无配方，数字可不变） | 服务端数据重载没跑 |
| `Prepared gun pack sync: <X> bytes -> <Y> compressed bytes, 1 part(s)` | 服务端把数据打包准备下发（X 应比只装默认包时大一点） | 服务端 reload 没跑 |
| 出错时：`Failed to load data file <id>` / `there is no corresponding data file` / `there is no corresponding model file` / `statemachine not found: ...` | 单文件解析/校验失败 | — |

## 4. 怎么判断「真的加载成功了」

1. **创造模式物品栏**：出现一把名为 `Review Pistol` / `测试手枪` 的枪（`tacz.gun.review_pistol.name` 被语言表命中），
   物品图标是一个红色手枪轮廓（`textures/gun/slot/review_pistol.png`）。
   - 名字显示为原始 key `tacz_review_pack.gun.review_pistol.name` → `.lang` 注入失败（语言链路）。
   - 图标是紫黑棋盘格 → `slot` 贴图没被索引（`display/guns` 或 `textures/` 路径不对）。
2. **拿在手上**：第一人称/第三人称能看到一个红色的方块手枪（16×16 贴图 + 2 个 cube 的模型）。
   看不见但有名字 → `geo_models`/`textures` 索引或 `display` 校验失败（看日志的 `there is no corresponding model file`）。
3. **开火**：左键能射击，消耗名为 `9mm Review` 的弹药（创造模式直接给）；因为没有配 `sounds`，开枪**没有声音**，
   这是本样例的已知简化，不是加载失败。
4. **改装/数据库查询**：`/tacz` 相关指令或改装界面能看到这把枪的索引（类型 pistol）。

## 5. 重要约束与坑

1. **描述符文件名是 `gunpack.meta.json`，不是 `gunpack.mcmeta`**。上游 TACZ 1.2.0 就用这个文件名
   （`GunPackLoader#fromDirPath/fromZipPath`）；`pack.mcmeta` 是 1.20 原版资源包的格式，TACZ 枪包从不读它，
   1.7.10 也没有原版数据包/资源包描述符机制。
2. **本样例依赖默认枪包**：`display` 里的 `"state_machine": "tacz:default_state_machine_v2"` 是 lua 脚本，
   位于默认枪包 `tacz_default_gun/assets/tacz/scripts/default_state_machine_v2.lua`；`ads_preset: tacz:renew` 同理。
   删掉 `<gamedir>/tacz/tacz_default_gun` 后本包 display 会以 `statemachine not found` 失败。
   这与上游枪包一致（官方枪包都引用 `tacz:default_state_machine_v2`），不是本样例的缺陷。
3. **1.7.10 用 `assets/<ns>/lang/<locale>.lang`**（如 `en_US.lang`）；上游 1.20 是 `<locale>.json`。两种都放不冲突。
4. **覆盖同名内容时的包优先级（有据平台差异，见报告 §5）**：
   - 数据（`data/<ns>/**`，含 index/data/display/geo/anim 等 json）→ 按包名排序后**后扫到的覆盖先扫到的**；
   - 二进制资源（png/ogg，经 `GunPackResourcePack` 走原版资源栈）→ **先扫到的优先**。
   - 本样例只「新增」不「覆盖」，不受影响。若要覆盖默认枪包的 data，目录名要排在 `tacz_default_gun` 之后
     （如 `z_custom`）；要覆盖默认枪包的贴图/音效，目录名要排在它之前（如 `a_custom`）。
5. **zip 版要求**：`gunpack.meta.json` 在 zip 根；条目路径用 `/`；不要包含外层目录。
6. 本样例**不含**：音效（`sounds`）、动画文件（`animation`）、瞄具/配件、配方、战利品注入、脚本。全部是可选的，
   用来把「加载是否成功」的变量降到最少。

## 6. 离线校验（已跑过，可复跑）

`verify/` 下是本轮用**真实 1.7.10 运行期 classpath** 跑的一次性验证脚本与输出：

- `verify/R7PackVerify.java`：直接调用生产代码 `GunPackResources.scanDirectory`、`CommonAssetsManager.GSON`、
  `CommonGunIndex` 校验、客户端 `ClientAssetsManager.GSON`（display/geo 反序列化器），断言每个环节；
  另有 zip 版一致性、`//` 注释 lenient 解析两项。
- `verify/verify-output.txt`：`RESULT: ALL PASS`（33 项断言）。
- `verify/zip-close/`：本轮修复「重扫时关闭 zip 句柄会打断并发读取」的复现与验证脚本（修复前 `IOException: Stream closed`
  → 修复后跨 `close()` 的在读流读完、且最后读者离开后句柄释放）；`output.txt` 是两次运行的原始输出。

> 客户端的**渲染层**（模型/贴图能否正确显示）由 r7-respack-client 静态推演复核，结论是「客户端可完整生效」，
> 与本 README 的依赖说明一致：display 引用的 `state_machine`/`ads_preset` 来自默认枪包（上游枪包同样如此），
> 其余模型/贴图/语言全部由本包自带。

复跑方法（在仓库根、PowerShell）：

```powershell
# 1) 从上次实机日志里取出运行期 classpath
$m  = Select-String -Path run/client/logs/fml-client-latest.log -Pattern "classpath at launch" | Select-Object -First 1
$cp = $m.Line.Substring($m.Line.IndexOf("classpath at launch is ") + "classpath at launch is ".Length).Trim()
# 2) 准备临时工作目录：gamedir/tacz/<pack> + 目录版/zip 版
$w = Join-Path $env:TEMP "r7pack"; New-Item -ItemType Directory -Force "$w\tacz" | Out-Null
Copy-Item -Recurse -Force docs/review/r7/test-pack/tacz_review_pack "$w\tacz\tacz_review_pack"
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory("$w\tacz\tacz_review_pack", "$w\tacz_review_pack.zip")
# 3) 编译并运行
javac -cp $cp -d "$w\out" docs/review/r7/test-pack/verify/R7PackVerify.java
Set-Location $w; java -cp "$cp;$w\out" R7PackVerify
```

注意：脚本**不经过 FML 启动**（`GunPackLoader` 的枚举初始化依赖 `FMLCommonHandler`，脱离 launchwrapper 会
`ClassCastException`），因此它从「枪包目录」这一层开始验证扫描/解析/索引，而「枪包目录发现 + 默认包导出」由
§3 的实机日志证明。
