# AGENTS.md

## 项目概览

本仓库是 **Timeless & Classics Guns: Zero (TACZ)** 的 Minecraft **1.7.10 / Forge 10.13.4.1614** 移植工程。

- 模组 id：`tacz`
- 根包：`com.tacz`（源码位于 `src/main/java/com/tacz/**`，与上游目录保持一致）
- 构建：GTNH Gradle 约定插件（`com.gtnewhorizons.gtnhconvention` + `com.github.ElytraServers.elytra-conventions`），
  通过 `settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` / `dependencies.gradle` 配置
- `gradle.properties`：`minecraftVersion = 1.7.10`、`forgeVersion = 10.13.4.1614`、MCP `stable` / `12`、
  `enableModernJavaSyntax = jvmDowngrader`（`downgradeTargetVersion = 8`），因此源码可直接使用 **Java 9+ 语法与标准库**
- GTNHLib 已在依赖库中（`dependencies.gradle` 的 `gtnhDev("GTNHLib")`）

## 移植来源（上游）

- 上游源码：`tmp/TACZ-1.20.1`（TACZ `1.2.0`，Minecraft 1.20.1 / Forge 47 / Java 17）
- 规模：799 个 Java 文件、约 6.7 万行；4497 个资源文件、约 98 MB
- 当前仓库已把上游 `src/main/java/com/tacz` 与 `src/main/resources` 全量复制进来，再逐包改造为 1.7.10 可编译可运行
- **移植目录必须使用上游目录**（`src/main/java/com/tacz/guns/...`），**不得使用**原模板目录 `com/Lilith/mymodid`
  （模板类已删除）

### 上游保真

- 上游已有的机制，按上游形态实现；不要为上游已解决的问题自行发明机制
- 改动行为前先读上游对应文件，并在注释 / 文档 / 提交说明里引用所依据的上游文件与行号
- 「看起来合理」不是完成理由
- 1.20.1 有、1.7.10 没有的平台能力，**记录差异**（`docs/` 或类注释）而不是悄悄改成第三种语义
- 移植特有代码必须带移植特有理由（1.7.10 限制，或移植自身架构）

## 1.7.10 环境事实

- 反编译后的 Minecraft / Forge 源码在 `build/rfg/minecraft-src`（Java 源码 + resources），
  1.7.10 原版与 Forge 类都在这里，**定位原版 API 时优先查这里**
- 1.7.10 关键差异：无 `PoseStack` / `RenderSystem`（1.17+）/ `VertexConsumer` / `MultiBufferSource` /
  `RenderType` / `ItemDisplayContext` / 数据包(datapack) / `DeferredRegister` / `SimpleChannel` / JOML；
  渲染走 OpenGL 固定管线 + `Tessellator`，注册走 `GameRegistry`，网络走 `SimpleNetworkWrapper`
- 允许新增依赖、改 `dependencies.gradle`；`jvmDowngrader` 会把 Java 9+ 语法与标准库 API 降级，
  **不需要**因为「Java 8 加载不到 META-INF 版本代码」而回避现代写法

## 构建规则（重要）

- **禁止在 Agent 运行期间执行 gradle 构建**（`gradlew` 任何任务都不行，包括 `compileJava`）
- 构建由**主 Agent 在所有任务结束后统一执行**：`./gradlew spotlessApply build`
- 快速构建（仅主线自检）：`./gradlew compileJava`
- Agent 自查手段：IDEA MCP 的 `get_file_problems` / `lint_files` 检查文件级错误；`search_symbol`、
  `analyze_calls`、`search_text`、`read_file` 定位代码

## MCP 工具使用

- 优先使用 MCP 工具搜索代码 / 定位块 / 找错误：`mcp__idea__search_symbol`、`mcp__idea__analyze_calls`、
  `mcp__idea__search_text`、`mcp__idea__search_regex`、`mcp__idea__read_file`（可读 `build/rfg/minecraft-src` 里的原版类）、
  `mcp__idea__get_file_problems`
- **非必要不要翻 gradle cache**；必须翻时也只用 MCP 工具，不要用 pwsh 遍历

## 协作规则

- 使用 **Agent Teams**（teammate），不是 subagent；每个 teammate 负责互不重叠的写作用域（按包划分）
- 共享任务板记录写作用域；有先后依赖时用 task 依赖
- 主 Agent 负责最终 diff 复核与统一构建
- Agent 之间通过共享文件系统同步，改动立即对所有人可见

## 已确认的决策（用户确认）

1. **推进方式**：分层推进——先主干（API / 资源加载 / 配置 / 网络 / 实体 / 物品逻辑 / 数据驱动），
   再客户端渲染与动画，最后收尾；每层结束保证 `compileJava` 通过
2. **1.20.1 专属兼容模块**（KubeJS、Oculus/Iris、Accelerated Rendering、PlayerAnimator、Cloth Config、
   Controllable、JEI、CarryOn、拔刀剑、Sound Physics、ShoulderSurfing、Create、OptiFine）：
   **保留 `com.tacz.guns.compat` 的反射式可选兼容骨架**——用反射探测，缺失库时静默跳过，不引入无解硬依赖
3. **第三方库策略**：内置移植——把库源码 vendor 进仓库自行改造为 1.7.10 可用；
   能从 Maven 正常取得的库（JOML、luaj、commons-math3 等）直接加依赖并 shade
4. **资源与注册**：`assets/tacz` 全量复制；`mods.toml` / `pack.mcmeta` 换成 1.7.10 的 `mcmod.info`；
   注册表与网络层全部重写为 1.7.10 API
5. **命名**：`modId = tacz`、`modGroup = com.tacz`、源码 `src/main/java/com/tacz/guns/**`、
   显示名 `Timeless & Classics Guns: Zero`；启用 Mixin（`usesMixins = true`，包 `com.tacz.guns.mixin`，
   由 1.7.10 的 UniMixins 承载）

## YSM（Yes Steve Model）联动

- 联动方向是 **YSM 主动适配 TACZ**（YSM 侧 `com.elfmcys.ysm.client.compat.tacz.*` 直接引用 TACZ 的类），
  因此 **我方不需要为 YSM 新增或开放任何接口**，也不需要改 YSM 仓库
- 我方义务只有一条：**保持上游 `com.tacz.guns.api.*` 的类名、方法签名、事件语义与上游一致**
  （YSM 是编译期直接调用，签名漂移就会 `NoSuchMethodError`）
- 交接文档：`docs/YSM_COMPAT_HANDOFF.md`，列出 YSM 侧用到的全部 TACZ 符号与语义，方便日后 compat 适配
- YSM 仓库参考路径：`E:\IDEA\YesSteveModel-Unofficial\src`（1.7.10 的 YSMU）；
  上游 YSM 参考 `E:\IDEA\YesSteveModel-Unofficial\tmp\YesSteveModel-dev-1.20`
  （注意：**不是** `tmp/YSM-dev-1.20.1`）

## 目录布局

```
src/main/java/com/tacz/guns/
  api/        对外 API（YSM 等外部模组编译期依赖，签名不得漂移）
  client/     客户端渲染、GUI、动画、模型、音效、输入
  compat/     可选模组兼容（反射式，缺失静默跳过）
  config/     配置
  entity/     实体与同步数据
  event/      Forge 事件
  init/       注册入口
  item/       物品与 NBT 逻辑
  mixin/      Mixin（1.7.10 需要重写目标类）
  network/    网络包
  resource/   枪械数据包加载、索引、序列化
  util/       工具
  block/ inventory/ crafting/ command/ advancement/ loot/ particles/ sound/ debug/
src/main/resources/
  assets/tacz/   上游资源全量（含 custom/tacz_default_gun 默认枪包）
  data/          上游数据包资源（1.7.10 无 datapack，需改为代码注册或自研加载）
docs/            移植计划、差异记录、交接文档
```
