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

若仍有 `Failed to read ...`，说明枪包自身路径与 `gunpack.meta.json` 的 namespace 不一致（数据问题，日志会带具体路径）。
