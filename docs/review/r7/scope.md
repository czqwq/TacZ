# r7-scope · 瞄具 / 镜片 / 热成像系统 上游等价性审查

任务：task-17。作用域：`client/renderer/scope/**`、`client/model/BedrockAttachmentModel.java`、
`mixin/client/EntityRenderDispatcherMixin.java`、`client/resource/pojo/display/attachment/ScopeLensConfig.java`、
本报告。

**未运行 gradle**（按要求由 Lead 统一构建）；本轮改动的 6 个 java 文件（含新增
`client/renderer/scope/MainFramebufferStencil.java`）`mcp__idea__lint_files(min_severity="error")` = **0 error**；
作用域内无 `[TACZ-DBG]` / 每帧 DBG 残留（现有诊断均为一次性 INFO/WARN）。

对比基准：上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`、上游 shader
`tmp/TACZ-1.20.1/src/main/resources/assets/tacz/shaders/core/**`、1.20.1 原版/Forge
`tmp/minecraft-src-1.20.1/**` + `tmp/MinecraftForge-1.20.1/**`、1.7.10 原版/Forge
`build/rfg/minecraft-src/java/**`。r6 定案实现（`docs/review/r6/scope-thermal.md`）逐条复核，
**不推翻其降级架构**，但修正了其中 3 处「已定案但实际不成立」的结论（见 §0.2）。

---

## 0. 结论速览

### 0.1 本轮修复

| # | 类型 | 位置 | 一句话 |
|---|---|---|---|
| 1 | **真实 bug** | `ScopeLensRenderer.java:408-490,492-518,665-714,1243-1348` | stencil 延迟 pass 原来**永远画不出来**：枪体渲染结束时主 framebuffer 的模板缓冲已被清空（`BedrockGunModel#renderUnaccelerated` 与上游同款），而移植侧没有上游的 `Capture#target#enableStencil` + 模板 blit。本轮补上「每 lens 一份模板副本 FBO」，stencil 路径（`-Dforge.forceDisplayStencil=true`，以及 r7 第二轮运行期补挂后的默认环境，见 §0.1-11）才真正可用。 |
| 2 | **真实 bug** | `ScopeLensRenderer.java:725-773`、`ScopeThermalRenderer.java:881-935` | `close()`（断线时在 Netty IO 线程触发）原来会把镜片/热成像的 GLSL 程序置空并加入待删队列 ⇒ **一次断线后整局游戏都没有镜片/热成像效果**（`init()` 只在客户端初始化调一次）。上游 `close()` 只销毁每世界 target、**不销毁 shader**。现改为「只请求、渲染线程释放 target，程序保留」。 |
| 3 | **真实 bug** | `ScopeThermalRenderer.java:561-591,788-830` | 深度「借用」兜底（blit 失败时的 fallback）查询的是**自己刚绑定的 FBO** 而不是主 framebuffer（`resolveSceneDepth` 在绑定本类 FBO 之后调用）⇒ 永远借不到主深度、日志却报 `complete=true`。现改为在绑定前探测主 FBO 深度附件。 |
| 4 | 等价性补漏 | `ScopeLensRenderer.java:665-714,871-884` | stencil 路径的镜片圆盘改为**上游同款几何**：`capture()` 的 `centerX/centerY/radius` 形参（r6 起被忽略）现在用于画「视空间圆」（上游 `buildSurface`，z=-90）+ capture 矩阵，而不是 NDC 椭圆。无 stencil 降级仍用 NDC 椭圆（需要它把圆盘限制在目镜区域内）。 |
| 5 | 等价性补漏 | `BedrockAttachmentModel.java:363-374,671-681`、`ScopeLensRenderer.java:976-991` | 上游 `Minecraft#getFrameTime()` → `ClientTickHelper.getPartialTick()`：目镜模板圆半径 / ADS 进度不再用硬编码 `1.0F`（原 TODO 已过时，`ClientTickHelper` 早已存在）。 |
| 6 | 等价性补漏 | `ScopeLensRenderer.java:1381-1523` | `RenderState` 按上游补齐固定管线状态快照/还原：viewport / scissor / colorMask / depthFunc / polygon offset / clearColor / clearDepth / 读-写 FBO 绑定（原先只存 viewport/depthMask/blend/cull/stencil/textures）。热掩膜的「黑色清屏色 / LEQUAL 深度函数 / 负 polygon offset」不再泄漏给后续手部/GUI 渲染。 |
| 7 | 等价性补漏 | `ScopeLensRenderer.java:418-427,531-568` | `capture()` 补上游的 viewport 检查（上游 `ScopeLensRenderer.java:84-89`：viewport 必须铺满主目标）；`render()`/热掩膜 pass 显式 `glDisable(GL_SCISSOR_TEST)`（上游同款）。 |
| 8 | 等价性补漏 | `EntityRenderDispatcherMixin.java:51-71`、`ScopeThermalRenderer.java:341-360` | 热源捕获参数类型 `EntityLivingBase → Entity`（上游 `wrap(source, Entity)` 同形）；mixin 跳过 `func_147939_a` 的 static-entity display-list pass（`RenderGlobal#rebuildDisplayListEntities`，此时坐标语义不同、不是可见绘制），并把被误命名为 `isShadow` 的形参更正为 `staticEntityDisplayList`。 |
| 9 | 健壮性 | `ScopeThermalRenderer.java:561-575` | 热掩膜 FBO 每帧先清深度（blit 失败时深度不再是未定义内容）；遮罩 pass 显式关 stencil test（上游同款）；去掉无意义的 `glEnable(GL_ALPHA_TEST)`（掩膜片元恒为白、无贴图，原调用只造成状态泄漏）。 |
| 10 | 健壮性 | `ScopeLensRenderer.java:328-366,901-950` | `prepareWorldCopy()` 增加「第一人称 + 非 hideGUI」闸门（省掉第三人称/F1 下的全屏拷贝）；`ensureCopyTexture`/`copyScreen` 只在 unit 0 上操作，避免踩 1.7.10 的 lightmap 单元（unit 1）。 |
| 11 | **平台补齐（实做）** | `client/renderer/scope/MainFramebufferStencil.java`（新增）、`ScopeLensRenderer.java:310-316,246-249,591-604` | **运行期给 1.7.10 主 framebuffer 补 `GL_DEPTH24_STENCIL8` 深度模板附件**（= Forge 1.20.1 `RenderTarget#enableStencil()` 的 1.7.10 等价物）。每帧 `beginFrame()`（`renderWorld` HEAD）检测主 FBO 有无 stencil 附件，没有就补挂；resize/全屏后自动重挂；失败还原旧深度附件并永久回退现有降级。默认启动（不加 `-Dforge.forceDisplayStencil`）从此走完整模板路径。详见 §9。 |
| 12 | **降级分支修正** | `BedrockAttachmentModel.java:500-540,621-632` | 无 stencil 兜底分支的**腰射**段不再画 division/目镜黑遮罩（r7 实机：未裁剪的 HAMR 红叉/红框悬浮在半空、大镜中间糊成黑斑，截图 `docs/review/r7/screenshots/b_hand_scope_artifacts.png`）；只保留外环 + 镜身。删除已无调用者的 `isDivisionTooLarge`/`DIVISION_MAX_UV_SIZE`/`ScopeLensRenderer#projectUvBounds`。 |
| 13 | **实机复核③（A）** | `ScopeLensRenderer.java:778-800,987-1045,1105-1181` | stencil 路径的镜片 **snapshot 语义对齐上游 `drawLens:226-231`**：不再采手部之前的旧 `copyTexture`，改为在 capture target 内把「本帧最终主画面」`glCopyTexSubImage2D` 到它自己的颜色纹理再采样（失败才退回旧快照）；`drawLens` 增加 `screenTexture` 参数。 |
| 14 | **实机复核③（B）** | `ScopeLensRenderer.java:1105-1181` | reticle 绘制按上游 `render():196-209` **每个 reticle 重绑 capture target + 重挂主颜色纹理 + 重设 `GL_EQUAL lens.stencil`**；并新增「视空间圆盘画不出时自动退回 NDC 椭圆」的自愈兜底 + 3 条一次性 probe 诊断（详见 §10）。 |
| 15 | **实机复核④（A+B 共同根因）** | `BedrockAttachmentModel.java:360-444`、`ScopeLensRenderer.java:503-535,1095-1177` | **上游「视空间圆」（`center*16*90`/`z=-90`/`rad=80*progress`）在 1.7.10 手部坐标系下换算不成立**（实机 probe：discNdc 与 ocularNdc 差 ~9.3 倍≈1440=16*90，INVERT 圆完全不在目镜上 ⇒ `stencilAtCenter=1` 而非 254 ⇒ 镜片/reticle 的 `GL_EQUAL` 全失败）。INVERT 圆与镜片圆盘统一改为 **Bounds 投影出的「目镜屏幕 NDC 椭圆」**（INVERT 椭圆半径 × aimProgress 近似上游瞄准圆生长）；删除 `drawViewSpaceDisc`/`SURFACE_Z`。详见 §11。 |
| 16 | **实机复核⑤（Elcan 4x 黑屏）** | `ScopeLensRenderer.java:1440-1535`、`BedrockAttachmentModel.java:354-570` | Elcan 4x 目镜几何（8 cube 八边形盘，z≈+1.83 紧贴 ADS 相机）有顶点跨到相机平面后面 ⇒ 移植侧 `Bounds.isValid()`（要求无顶点在相机后）判无效 ⇒ INVERT 圆跳过 + `capture()` 返回 false（镜片不登记）⇒ 目镜黑遮罩把整个目镜涂黑（截图 `c_elcan_ads_black.png`）。修复：Bounds 拆成 `hasUsableBounds`/`hasBehindCamera`/`useScreenBounds`，异常时退回**整屏范围**（被模板裁到目镜内，等价上游巨大视空间圆）；capture 不再放弃；ADS 时跳过 scope 目镜黑遮罩（上游此时本来就不可见）。详见 §12。 |
| 17 | **实机复核⑥（回退复核⑤全局回归）** | `ScopeLensRenderer.java:463-474,551-572,1499`、`BedrockAttachmentModel.java:488-497` | 复核⑤ 的全局行为改动（Bounds 整屏兜底 / capture 整屏登记 / INVERT 整屏兜底 / ADS 跳过目镜遮罩）**全部回退**到复核④状态；只保留不改变行为的诊断日志（capture/INVERT 失败原因、`scope ocular pass` 一条）。Elcan 继续按复核④ 行为（预期仍黑屏，属已知降级，等上游规格后重做）。详见 §13。 |
| 18 | **按上游规格回归实现（复核⑦）** | `BedrockAttachmentModel.java:398-472`、`ScopeLensRenderer.java:104-112,421-560,695-745,747-816,1100-1230,1495-1525` | 按 `docs/review/r7/scope-upstream-spec.md` §7.2/§7.3 逐条替换：INVERT 圆与镜片 surface 改回**上游视空间圆**（`(16*90*cx,16*90*cy,-90)`、半径 `80*modifier*progress`、用当前 GL 矩阵/ capture 矩阵）；删除 `projectUvBounds`/`drawLensFallback`/`lensViewSpacePathWorks` 等自研近似；补 capture scissor、capture 颜色纹理 `LINEAR+CLAMP_TO_EDGE`；加只读断言日志（proj.m00/m11/expectedF/modelViewIsIdentity/modelDelta/centerPose/ndcCenter/ndcRadius）。详见 §14。 |

### 0.2 对 r6 定案结论的复核

| r6 结论 | 复核结果 |
|---|---|
| R1：reticle 延迟绘制必须逐 lens 还原 capture 矩阵 | **成立且已保留**（`Lens.projection/modelView`，`ScopeLensRenderer.java:1193-1241`）。上游 `ScopeLensRenderer.java:184-187` 同款。 |
| R2：无 stencil 时 capture 只登记镜片、不登记 reticle | **成立且已保留**；`BedrockAttachmentModel` 三段降级架构保留，本轮未改。 |
| R3：热源掩膜几何用「包围盒」替代「实体剪影」 | **成立且已保留**（1.7.10 实体渲染无可插入顶点流）；本轮只修兜底 bug 与状态泄漏。 |
| 「加 `-Dforge.forceDisplayStencil=true` 可恢复完整路径（reticle 位置正确）」 | **不成立**：主模板缓冲在 `BedrockGunModel#renderUnaccelerated` 末尾被清空（`BedrockGunModel.java:463-469`，与上游 `BedrockGunModel.java:341-346` 同款），帧末延迟 pass 的 `GL_EQUAL` 全部失败 ⇒ 镜片/准星都不画。**本轮 §0.1-1 已修**（补上游 capture target 语义）。 |
| 「`close()` 线程安全：只把 GL 对象 id 挪进 volatile 待删槽位」 | **不成立**：原 `close()` 还调 `beginFrame()`（改渲染线程正在遍历的 `LENSES`/`ACTIVE`）并直接写非 volatile 的 `copyTexture`/`framebuffer` 字段 ⇒ Netty 线程与渲染线程数据竞争（可能 `ConcurrentModificationException`）。**本轮 §0.1-2 已修**：`close()` 只写 volatile 标记，清理全部挪到渲染线程 `drainDeletes()`。 |
| 「`capture()` 的 `centerX/centerY/radius` 形参从第一轮起就被忽略（上游签名遗留）」 | 无 stencil 路径仍忽略（NDC 椭圆来自 `Bounds`）；**stencil 路径本轮已恢复使用**（§0.1-4）。 |
| 「`ScopeLensContour` 用 `texelFetch`，GLSL 120 写不出来」 | **结论偏严**：`texelFetch` 可用 `texture2D` + NEAREST + texel 中心 UV 等价替代，三个 contour pass 的 render target 都是 RGBA8（无浮点纹理需求）。真正缺的是「目镜轮廓掩膜目标」，而本轮新增的 `CaptureStencil` 正好提供了它 ⇒ 已从「平台不可能」降级为「可补齐的待办」，见 §4.3。 |

---

## 1. 逐文件对照表

### 1.1 `client/renderer/scope/ScopeLensRenderer.java`（上游 511 行 → 本工程 1519 行）

| 机制 | 上游 1.20.1 | 本工程（本轮后） | 判定 |
|---|---|---|---|
| 着色器注册 | `RegisterShadersEvent`（`ScopeLensRenderer.java:46-58`） | `init()` + `ScopeLensProgram`（GL20/GLSL120），`ScopeLensRenderer.java:185-222` | 平台差异，等价 |
| 帧开始清理 | `beginFrame()`（`:61-70`） | 同（`:301-312`） | 等价 |
| capture 前置闸门 | `shader==null / 配置关 / radius<=0.001 / Oculus 阴影` + `drawFbo==0` + 主目标尺寸 + **viewport 铺满** + stencil 附件存在（`:73-89`） | 同前 4 项 + 主目标尺寸 + **viewport 铺满**（`:408-427`）；stencil 附件检查拆成 `stencilAvailable()`（`:955-974`） | 等价（stencil 缺失时的处理见 §3） |
| 保存 capture 矩阵 | `RenderSystem.getProjectionMatrix()/getModelViewMatrix()`（`:91-93`） | `glGetFloat(GL_PROJECTION_MATRIX/GL_MODELVIEW_MATRIX)`（`:1012-1022`） | 等价（1.7.10 固定管线同一份矩阵） |
| 目镜包围盒 | `Bounds` 喂 ocular 几何（`:93-95`、`:374-418`） | 同（`:1055-1145` 内部类 `Bounds`） | 等价 |
| 模板缓冲副本 | `Capture#target.enableStencil()`（`:298-307`）+ capture 时 `glBlitFramebuffer(STENCIL)`（`:105-110`） | **本轮新增** `CaptureStencil`（`:1243-1348`）+ capture 时 blit（`:492-518`） | 等价（本轮修复） |
| reticle 录制 | `ReticleBuffers`（`MultiBufferSource` 分桶，`:319-343`） | 同（`:1159-1191`） | 等价 |
| 帧末绘制 | 每 lens：`target.bindWrite(false)` + 主颜色纹理挂到 capture target 的 `COLOR_ATTACHMENT0` + 还原 capture 投影/模型视图 + stencil `GL_EQUAL` + 画 surface + 画 reticle（`:162-222`） | 同结构：`drawLensWithStencil`（`:665-714`）；无 stencil 时画 NDC 椭圆（`:617-624`） | stencil 路径等价；无 stencil 为有据降级 |
| 镜片 surface 几何 | 视空间圆（`buildSurface`，`:273-284`：中心+半径，z=-90） | stencil 路径同（`drawViewSpaceDisc`，`:871-882`）；无 stencil 用 NDC 椭圆（`drawNdcDisc`，`:853-869`） | stencil 等价；无 stencil 有据降级 |
| 镜片 shader uniform | `:224-271` | `drawLens`（`:775-851`）逐项一致（见 §2） | 等价 |
| 轮廓通道 | `ScopeLensContour.prepare(...)`（`:173-177`） | 恒 `null` ⇒ `ContourReady=0`（`ScopeLensContour` stub） | 有据降级（见 §4.3） |
| 状态快照 | `RenderState`（`:420-510`） | 本轮补齐（`:1381-1523`） | 等价（除 blendFunc/alpha test 等本 pass 不修改的状态） |
| 关闭 | `close()` 只销毁每世界 target，shader 保留（`:286-296`） | 同语义 + 跨线程安全（`:725-773`） | 等价（本轮修复） |

### 1.2 `client/renderer/scope/ScopeLensProgram.java`

- GLSL 120 重写与上游 `scope_lens.{vsh,fsh}` **数学逐行等价**，差异仅 3 处且都是语言版本：
  `#version 150→120`、`in/out→attribute/varying + gl_FragColor`、`texture()→texture2D()`。
- `distanceToEdge` 的 `ContourReady!=0` 分支被整体删除，只保留上游自己的椭圆兜底分支
  （上游 `scope_lens.fsh:52-55` 注释 "Shader/resource failures retain the previous elliptical approximation"）。
  由于本工程 `ContourReady` 恒 0，**运行期行为与上游 shader 在 ContourReady==0 时逐指令一致**。
- 顶点属性位置显式绑定 0（`glVertexPointer`→generic attribute 0 的 compatibility 别名），
  与 1.7.10 `Tessellator` 提交方式匹配。
- 本轮只删除已无调用者的 `destroy()`（shader 不再在 close 时销毁）。

### 1.3 `client/renderer/scope/ScopeLensContour.java`

保留与上游同形的 `init/prepare/close/Lookup` 外壳，`prepare` 恒 `null`。上游 `ScopeLensContour.java:50-114`
（mask→seed→JFA flood）在 1.7.10 的落点见 §4.3；**不是平台不可能，是本轮未实现**。

### 1.4 `client/renderer/scope/ScopeVisionMode.java` / `ScopeLensConfig.java`

与上游**逐行一致**（枚举顺序即 shader 的 `VisionMode` 序号；`ScopeLensConfig` 的字段名/默认值/clamp 区间/
`hasEdgeEffects` 全部相同）。本轮未改。`vision_mode` 的 JSON 反序列化（Gson 枚举名）也与上游同路径。

### 1.5 `client/renderer/scope/ScopeThermalRenderer.java`（上游 302 行 → 本工程 1196 行）

| 机制 | 上游 1.20.1 | 本工程（本轮后） | 判定 |
|---|---|---|---|
| 启用条件 | `enabled()`（`:77-87`：shader/配置/世界/第一人称/瞄准进度/Oculus） | `enabled()/computeEnabled`（`:234-286`），逐项同 + 整帧缓存（每实体调用需要） | 等价 |
| 热成像瞄具判定 | `hasThermalScope`（`:89-106`，含变体 replace/overlay 选择） | 同（`:447-478`） | 等价 |
| 顶点捕获 | `wrap(source, entity)` 包装 `MultiBufferSource`（`:108-116`） | **有据降级**：`beginEntity(entity,x,y,z)` 按 AABB 生成白色盒子（`:341-411`）；`wrap()` 保留但已无调用点（1.7.10 无顶点流） | 有据降级 |
| 掩膜 pass 时机 | `RenderLevelStageEvent.AFTER_PARTICLES`（`:118-120`） | `RenderWorldLastEvent`（`ClientSetupEvent#onRenderWorldLast`，1.7.10 等价时机：世界画完、手部之前） | 等价 |
| 深度对齐 | 借用主 framebuffer 的**深度附件**（`:128-135,154-156,202-208`） | 每帧 `glBlitFramebuffer` 深度到自建 depth texture（`:712-758`）；失败才走「借用」兜底（本轮修好探测顺序，`:561-591,788-830`） | 等价（1.7.10 主深度是 renderbuffer，不能直接采样，见 r6） |
| 掩膜几何 | 实体顶点 + 贴图 alpha cutout + 顶点色（`MaskVertices`，`:246-301`；`scope_thermal_mask.fsh`） | 白色盒子 + 片元恒白（`MASK_FRAGMENT_SOURCE`，`:128-135`） | 有据降级 |
| 掩膜状态 | 目标 FBO + 深度测试 LEQUAL + polygon offset(-1,-2) + colorMask(true) + 关 blend/stencil（`:136-189`） | 同（`:561-655`，本轮补清深度/关 scissor/关 stencil/去 alpha test） | 等价 |
| 掩膜 uniform | `ShaderInstance` 自动灌 `ProjMat/ModelViewMat` | 显式 `setMatrix4`（`:618-625`） | 等价（r6 已修「从不赋值」bug） |
| 就绪判定 | `isReady(width,height)`（`:210-212`） | 同（`:856-858`） | 等价 |
| 关闭 | `close()` 只销毁 target，shader 保留（`:218-225`） | 同语义 + 跨线程安全（`:881-935`，本轮修复） | 等价（本轮修复） |

### 1.6 `client/model/BedrockAttachmentModel.java`（上游 785 行 → 本工程 918 行）

| 机制 | 上游 1.20.1 | 本工程 | 判定 |
|---|---|---|---|
| 构造期节点路径 | `scope_view/ocular*/division*/scope_body/ocular_ring/laser_beam`（`:67-118`） | 逐行同（`:85-137`） | 等价 |
| `render()` 分支 | firstPerson × (scope/sight/both)；否则 scopeBody+ocularRing（`:168-206`） | 同（`:191-259`） | 等价 |
| `renderOcularStencil` | `:240-258` | `:303-329` | 等价 |
| `renderDivisionOnly` | `:260-269` | `:331-348` | 等价 |
| `renderOcularAndDivision` | `:271-331`（模板圆 + 目镜黑遮罩 + 每 ocular capture reticle） | `:350-463`：stencil 可用时逐行同；`getFrameTime()`→`ClientTickHelper.getPartialTick()`（本轮）；无 stencil 时走 `captureLensesWithoutStencil`（r6 降级） | stencil 路径等价；无 stencil 有据降级 |
| `renderBoth/renderSight/renderScope` | `:367-456`（模板顺序/清空/开关） | `:790-922` 逐行同（模板路径），仅 `RenderSystem.clear(...)`→`glClear`；无 stencil 时三段降级（r6-3） | 等价（模板路径） |
| ARCompat 加速分支 | `render*Accelerated`（`:458-774`） | 回退非加速（1.7.10 无 AcceleratedRendering），方法保留 | 有据降级（文档已记） |
| 无 stencil 三段降级 | 上游不存在（`enableStencil()` 保证有模板） | r6-2/r6-3 实现：腰射完整模型 / 过渡段隐藏 division / ADS 隐藏镜身+目镜遮罩并登记镜片 | 有据降级（本轮保留，§4.1） |

### 1.7 `mixin/client/EntityRenderDispatcherMixin.java`（上游 23 行 → 本工程 73 行）

- 上游 `@WrapOperation` 换 `MultiBufferSource`（`tmp/.../EntityRenderDispatcherMixin.java:14-22`）；
  1.7.10 无顶点流 ⇒ 改在 `RenderManager#func_147939_a` HEAD 交相机相对坐标（同 r6 方案）。
- 本轮：参数类型改 `Entity`（对齐上游 `wrap(source, Entity)`）；跳过 static-entity display-list pass
  （依据 `RenderGlobal.java:581-583`：`renderEntityStatic(entity, 0.0F, true)` 是烘 display list，
  `RenderManager.renderPos*` 为 0，坐标语义与正常渲染不同）；形参 `isShadow → staticEntityDisplayList`。
- RETURN 钩子保留（`endEntity` no-op，为未来重绘方案留成对入口）。

---

## 2. `scope_lens` shader 数学等价性（逐项）

本工程 `ScopeLensProgram.FRAGMENT_SOURCE` vs 上游 `assets/tacz/shaders/core/scope_lens.fsh`：

| 项 | 上游 | 本工程 | 判定 |
|---|---|---|---|
| UV 空间 | `gl_FragCoord.xy / ScreenSize` | 同 | 一致 |
| `clampUv` | `clamp(uv, 0.5/ScreenSize, 1-0.5/ScreenSize)` | 同 | 一致 |
| `luminance` | `dot(color, vec3(0.2126,0.7152,0.0722))` | 同 | 一致 |
| `grain` | `fract(sin(dot(floor(gl_FragCoord), vec2(12.9898,78.233)) + floor(SensorTime*30)*37.719)*43758.5453)-0.5` | 同 | 一致 |
| `ironbow` | cold/purple/red/yellow 四段分段线性（0.35/0.65/0.85） | 同 | 一致 |
| distortion | `sampleUv = uv - (uv-LensCenter) * Distortion * ramp` | 同 | 一致 |
| aberration | `split = digital ? 0 : (uv-LensCenter)*Aberration*ramp`，R/G/B 三采样 | 同 | 一致 |
| digital 判定 | `VisionMode==1||2||(>=3 && ThermalReady!=0)` | 同 | 一致 |
| 夜视 | `1-exp(-pow(max(lum,0),0.65)*VisionGain)` + grain*VisionNoise，绿色/白色 phosphor | 同 | 一致 |
| 热成像 | `heat = mix(0.06+detail*0.24, 0.78+detail*0.20, Sampler1.r)` + grain*VisionNoise；3=白热、4=黑热、5=ironbow | 同 | 一致 |
| tint | `color = scene * LensTint`（LensTint 由 `tint` + `tint_strength` 在 CPU 端算） | 同（`drawLens` 的位运算与 1/255 展开逐行同） | 一致 |
| vignette | `distance/(0.155*VignetteWidth)` 的 `exp(-f^2)*VignetteStrength` | 同 | 一致 |
| edge | `ramp = 1-smoothstep(0, min(0.155*EdgeWidth*3,1), distance)` | 同 | 一致 |
| 输出 | `vec4(color*(1-edge), 1.0)` | `gl_FragColor` 同式 | 一致 |
| `SensorTime` | `Util.getMillis()%60000/1000f` | `System.currentTimeMillis()%60000/1000f` | 等价 |
| `VisionMode` 序号 | `NORMAL, NIGHT_VISION_GREEN, NIGHT_VISION_WHITE, THERMAL_WHITE_HOT, THERMAL_BLACK_HOT, THERMAL_IRONBOW` | 枚举同序，`ordinal()` 直接传 | 一致 |
| `distanceToEdge` | ContourReady 分支 + 椭圆兜底 | 只有椭圆兜底（ContourReady 恒 0） | 有据降级（§4.3） |

`scope_thermal_mask`：顶点着色器与上游 `scope_thermal_mask.vsh` 逐行一致（`ProjMat*ModelViewMat*Position`、
`texCoord/vertexColor` 透传）；片元着色器从「贴图 alpha cutout + 顶点色」改为恒白 —— 因为几何来源已改为
包围盒（§1.5），这是同一处平台降级的必然结果，不是独立偏离。

---

## 3. stencil 语义：有 stencil / 无 stencil 两条路径

### 3.1 有 stencil（`-Dforge.forceDisplayStencil=true`）——本轮后与上游等价

上游流程（`ScopeLensRenderer.java:73-222`）：

1. `renderBoth/renderScope/renderSight` 开模板测试、清模板 → 目镜节点写 stencil（`GL_REPLACE`，后画者覆盖）→
   镜身用 `GL_EQUAL 0` 排除在目镜区外 → 画瞄准圆（`GL_INVERT`）→ 目镜黑遮罩（`GL_EQUAL i+1`）/ division
   （`GL_EQUAL ~(i+1)`）；
2. `capture()` 把主 framebuffer 的模板缓冲 **blit 进每个 lens 自己的 capture target**，并把 division 顶点录进
   reticle buffer（**不立即画**）；
3. 枪体渲染结束（`BedrockGunModel` 末尾清主模板缓冲）；
4. 帧末 `render()`：把主颜色纹理挂到 capture target 的 `COLOR_ATTACHMENT0`、还原 capture 投影/模型视图、
   `GL_EQUAL` 到该 lens 的 stencil 值 → 画镜片 surface + reticle。

本工程本轮后 1:1 对应（`ScopeLensRenderer.capture`/`captureStencil`/`drawLensWithStencil`）。
关键证据：`BedrockGunModel.java:463-469` 的清模板与上游 `BedrockGunModel.java:341-346` 同款，
所以「没有 capture target 就一定画不出」是硬结论；修好后 `-Dforge.forceDisplayStencil=true` 才真正恢复
「目镜黑遮罩 / 准星 / 镜内不画枪体 / 镜片裁剪」四项。

**本轮新增实机判读日志**（一次性 INFO）：
`TACZ: scope lens stencil path ACTIVE: captureTarget=WxH, stencilRef=N, reticles=K, mainColorTexture=ID`
—— 出现即说明 capture FBO + 模板 blit 成功；若出现
`TACZ: scope stencil capture DISABLED (...)` WARN，则该帧退回无模板圆盘（不会崩）。

### 3.2 无 stencil（仅运行期补挂失败时的兜底）——有据降级，未改变 r6 架构

- 模型侧：`BedrockAttachmentModel#renderWithoutStencil` 三段（腰射完整模型 / 过渡段隐藏 division /
  ADS 隐藏镜身+目镜遮罩、只留外环 + 登记镜片圆盘）；
- 镜片侧：`capture()` 只登记镜片、不登记 reticle；`render()` 用「capture 时刻目镜几何的屏幕 AABB 椭圆」
  （`Bounds`）作为圆盘，采样「世界渲染末尾」的干净快照（`prepareWorldCopy`）；
- 热成像照常生效（热掩膜不依赖 stencil）。

**为什么不做「无 stencil 也画 reticle」**：division 几何是给「远焦平面 + 模板裁剪」设计的
（例：HAMR `division_bg` 是 64×128 模型单位的大平面、FMP13/REAP-IR 的 division 在 z≈-96 模型单位），
未裁剪时在开镜过渡段会整屏黑（r6-3 实机截图 g）。要正确恢复 reticle 需要「模板裁剪」，即
§4.1 的主 FBO stencil 方案；本轮不推翻 r6 的降级决定。

---

## 4. 未修 / 有据降级（含「完整等价需要什么」）

### 4.1 无 stencil 环境下的一整组降级 —— 根因只有一个：主 framebuffer 没有 stencil 附件（r7 第二轮已用运行期补挂修掉，仅补挂失败时仍存在，见 §9）

受影响项（全部有据降级，r6 已登记，本轮复核仍成立）：

| 项 | 无 stencil 时的表现 | 完整等价需要 |
|---|---|---|
| 目镜黑遮罩 / 镜身遮罩 | 不画（否则整屏黑） | 主 FBO 有 stencil |
| division / reticle（准星、红点、弹道刻度） | 不画（APM4s 红点、FMP13/REAP-IR 准星盘看不到） | 同上 |
| 镜内不画枪体（`GL_GREATER 127`） | 失效（镜内能看到枪体） | 同上 |
| `ScopeLensContour` JFA 轮廓 | 恒 null（椭圆近似） | 同上（+ §4.3 实现） |
| 镜片圆盘形状 | 目镜 AABB 椭圆（近似） | 同上（stencil 裁剪后可用精确视空间圆） |
| ADS 过渡段镜片 | 从 aim≥0.5 起直接全尺寸（上游随 progress 生长） | 同上（stencil 裁剪天然给出生长） |
| 纯 sight 开镜 | 红点准星消失 | 同上 |

→ **平台升级评估见 §6**。若 §6 方案落地，以上 7 项全部消失，r6 的三段降级分支可以删除。

### 4.2 热源轮廓：精确剪影 → 包围盒（平台降级）

1.7.10 实体渲染是固定管线 `Tessellator` 直接提交，没有 1.20 `MultiBufferSource` 那样可插入的顶点流；
上游 `ScopeThermalRenderer.wrap`（`:108-116`）无法等价实现。当前用实体 AABB 生成白色盒子
（`ScopeThermalRenderer.java:373-411`），位置/尺寸/遮挡（场景深度 blit）正确，但形态是矩形。
**完整等价需要**：拦截实体顶点（Mixin 逐个 `Render.doRender` 注入或包装 `Tessellator`），属独立课题。

> 说明：`ScopeThermalRenderer.wrap()`/`ThermalBuffers`/`TeeVertexConsumer`/`MaskVertices` 仍保留但
> **全仓无调用点**（r6 起就是死代码）。保留是为了维持上游方法形状（上游 `wrap` 是 public）；
> 若 Lead 认为死代码有害，可整段删除，删除不影响任何行为。

### 4.3 `ScopeLensContour`（JFA 镜片轮廓精修）恒 null

当前影响：`scope_lens.fsh` 的 `distanceToEdge` 只用椭圆近似 ⇒ 非椭圆目镜（矩形/切角镜片）的
edge/vignette 过渡形状与上游略有差异（观感项，不影响功能）。

**复核更正**：`ScopeLensContour` 类注释称「`texelFetch`/整数纹理取址是 GLSL 130+，1.7.10 写不出来」，
这**不是硬限制**：
- `texelFetch(s, ivec2 p, 0)` ≡ `texture2D(s, (vec2(p)+0.5)/MapSize)`（纹理 NEAREST，本管线本来就是 NEAREST）；
- `ivec2/lessThan/any/greaterThanEqual` 可用 `float` + `clamp`/`step` 等价改写；
- 上游 3 个 pass 的 target 都是 RGBA8（坐标打包进 4 字节），**不需要浮点纹理**；
- 真正的数据源「目镜轮廓模板掩膜」：上游用 `ocular` target 的 stencil 两次 pass 画成 alpha 掩膜；
  本轮新增的 `CaptureStencil`（模板副本 + 全尺寸颜色附件）正好提供了它。

**完整等价需要的实现步骤**（建议后续轮次做，本轮不做以免在无法实机验证的情况下引入大块新 GL 代码）：
1. `ScopeLensContour.init()`：用 `ScopeLensProgram.compileRaw` 编译 3 个 GLSL 120 pass
   （mask/seed/distance，source 按上述等价改写）；
2. `prepare(...)`：两张 512² RGBA8 FBO（NEAREST）；把 `CaptureStencil` 的模板副本绑为 draw target，
   viewport 铺满，`GL_EQUAL stencil` + `GL_EQUAL ~stencil` 各画一个全屏 quad（mask 着色器）→ 轮廓掩膜；
3. seed pass 读掩膜、flood 循环（`step` 从 highestOneBit 降到 1，与上游 `:105-113` 同循环）；
4. `ScopeLensRenderer.drawLens` 把 `ContourReady/ContourRect/ContourSize` 灌进 shader（uniform 已在
   `ScopeLensProgram` 中声明，`drawLens` 现在写死 0/默认值）；
5. 无 stencil 环境可改为「把目镜几何用纯白片元画进掩膜 FBO」得到 silhouette（不需要 stencil），
   从而默认环境也能用精确轮廓 —— 这是超出上游的等价实现，需实测确认。

### 4.4 其他记录项

| 项 | 状态 |
|---|---|
| shader 资源 `assets/tacz/shaders/core/scope_*.{vsh,fsh,json}` | 上游资源原样保留，但 1.7.10 侧**不加载**（`ScopeLensProgram` 内嵌 GLSL 120）⇒ 资源包无法覆盖镜片 shader（上游可以）。属平台差异，记录于此。 |
| `ScopeLensContour.Lookup` 的 contour 纹理采样 | 随 §4.3 一起未实现。 |
| `scopeViewRadiusModifier` | 恒 1：上游 `setScopeViewRadiusModifier` 也从未被调用（不是移植问题）。 |
| `ScopeLensRenderer.prepareWorldCopy` 的全屏拷贝 | 无 stencil 降级特有开销（每帧一次 `glCopyTexSubImage2D`，仅开镜时）；有 stencil 时也用它作 `Sampler0`，与上游「帧末从主颜色拷」在镜片区域内等价（模板保证镜片区没有手部几何）。 |
| `VisionMode` 的 `NORMAL` 时 `LensTint` 仍生效 | 与上游一致（tint 不依赖 vision mode）。 |

---

## 5. 跨模块发现（未修，需对应 scope / Lead 决策）

1. **`client/init/ClientSetupEvent.java:171-172` 重复初始化热掩膜着色器**：
   `ScopeLensRenderer.init()` 内部已经调用 `ScopeThermalRenderer.init()`（`ScopeLensRenderer.java:220`），
   `ClientSetupEvent` 又单独 `step("ScopeThermalRenderer.init", ...)` 一次 ⇒ 热掩膜 GLSL 被编译两次
   （第一次立即被第二次 `glDeleteProgram` 删掉）。无行为问题，纯浪费 + 日志噪音。
   **建议**：删掉 `ClientSetupEvent.java:172` 那一行（属 r7-platform / client-init scope）。
2. **`util/ThermalImagingHelper` 的标签数据从未加载**：
   上游 `tacz:thermal_imaging/{blacklist,whitelist}` 由数据包驱动（`ThermalImagingHelper.java:13-16`）；
   移植侧 `registerBlacklist/registerWhitelist` **全仓 0 调用者** ⇒ 两张表恒空。
   默认数据里 blacklist = `minecraft:armor_stand`（1.7.10 无此原版实体 ⇒ 原版场景无可见差异），
   whitelist 为空；但「自定义枪包/模组实体改热源规则」的数据驱动能力丢失。
   **建议**：在枪包/资源重载时解析 `data/tacz/tags/entity_types/thermal_imaging/*.json` 并调用 register*
   （属 r7-respack-core / r7-util scope）。
3. **`util/ThermalImagingHelper#isHeatSource` 每实体每帧分配**：
   当前实现先 `EntityList.getEntityString` + `toLowerCase` + `new ResourceLocation`（每次调用），
   而 `beginEntity` 现在对所有实体调用。两张表恒空时可以早退成
   `entity instanceof EntityLivingBase`（零分配）。属 r7-util scope，性能优化建议。
4. **`ClientSetupEvent#onRenderWorldLast` 的调用顺序**：先 `prepareWorldCopy()` 再 `ScopeThermalRenderer.render()`。
   两者都要求「世界画完、手部未画」，顺序无相互依赖（拷贝的是颜色、热掩膜画的是自己的 FBO），保持现状即可。
5. `mixin/client/EntityRenderDispatcherMixin.java` 的修改已与 r7-platform 协调（该文件同时在 task-17/task-24
   写作用域内，本轮由 r7-scope 独占修改）。

---

## 6. 平台升级：给 1.7.10 主 framebuffer 运行期补 stencil（**r7 第二轮已实做，见 §9**；以下为当时的评估依据）

### 6.1 结论

**可行，且代价不大（约 60-100 行 + resize 处理）**，建议作为下一轮的可选增强。落地后 §4.1 的全部降级、
r6 的三段降级分支、`-Dforge.forceDisplayStencil=true` 依赖都会消失。

### 6.2 依据

- 上游能这么做的直接证据：Forge 1.20.1 给 `RenderTarget` 打了 `enableStencil()` 补丁
  （`tmp/MinecraftForge-1.20.1/patches/minecraft/com/mojang/blaze3d/pipeline/RenderTarget.java.patch:34-55`）：
  把深度附件从 `GL_DEPTH_COMPONENT32F` 换成 `GL_DEPTH32F_STENCIL8` 并重新挂 FBO；上游
  `ScopeLensRenderer.Capture`（`:303-306`）与 `RenderHelper#enableItemEntityStencilTest`（`:69`）都靠它。
- 1.7.10 侧 `Framebuffer` 的关键字段全是 **public**：`framebufferObject`、`depthBuffer`、`useDepth`、
  `framebufferTextureWidth/Height`（`build/rfg/.../shader/Framebuffer.java:14-23`）；
  Forge 的 `forceDisplayStencil` 分支就是「用 `GL_DEPTH24_STENCIL8` 建 renderbuffer 并同时挂
  DEPTH+STENCIL 两个 attachment」（`Framebuffer.java:117-130`）—— 运行期补挂与它逐行同构。
- 1.7.10 主 framebuffer 的深度对象**只被 Framebuffer 自己使用**（`Framebuffer.java:69-72,108,119-129`），
  没有其它原版/Forge 代码持有 `main.depthBuffer` 的语义依赖（已全仓 grep `depthBuffer` 确认），
  替换风险低。

### 6.3 实现草案（若做）

```java
// 只在「Forge stencilBits==0 && 未 core profile && 主 FBO 是 vanilla Framebuffer」时启用
static void patchMainFramebufferStencil(Framebuffer main) {
    if (main == null || !main.useDepth || main.framebufferObject < 0) return;
    if (patchedWidth == main.framebufferTextureWidth && patchedHeight == main.framebufferTextureHeight) return;
    int old = main.depthBuffer;
    int rb = GL30.glGenRenderbuffers();
    GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rb);
    GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL_DEPTH24_STENCIL8_EXT, w, h);
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.framebufferObject);
    GL30.glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rb);
    GL30.glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rb);
    if (complete) { GL30.glDeleteRenderbuffers(old); main.depthBuffer = rb; patchedW/W = w/h; }
    else { /* 还原旧深度附件，WARN 一次，回退现有降级 */ }
}
```

调用点：`ScopeLensRenderer.init()`（首帧）与每帧 `beginFrame()` 的「尺寸变化检测」（resize 会走
`Framebuffer.createBindFramebuffer → deleteFramebuffer/createFramebuffer` 重建深度，需要重新补挂；
也可用 Mixin 在 `Framebuffer.createFramebuffer` RETURN 判断 `this == mc.getFramebuffer()` 补挂，
属 r7-platform 的 mixin scope）。

### 6.4 风险 / 边界

| 风险 | 处理 |
|---|---|
| OptiFine / Angelica 接管 framebuffer 或 shader pack（core profile） | 检测 `isCoreProfile()` / `OculusCompat` / 主 FBO 不是 `mc.getFramebuffer().framebufferObject` 时**不补挂**，走现有降级 |
| 驱动/上下文不支持 `GL_DEPTH24_STENCIL8` renderbuffer | FBO 不完整则还原旧附件 + WARN 一次，走现有降级（不崩） |
| 深度格式从 `DEPTH_COMPONENT24` 变 `DEPTH24_STENCIL8` 影响其它模组 | 深度精度不变（24 位）；`glClear(GL_DEPTH_BUFFER_BIT)` 语义不变；1.20 默认就是这个格式 |
| resize / 全屏切换 | 尺寸变化检测或 `Framebuffer.createFramebuffer` mixin 重新补挂 |
| `Framebuffer.framebufferClear` 只清 color/depth | 与上游一致；模板由使用方 `glClear(GL_STENCIL_BUFFER_BIT)`（模型路径已有） |
| 打开后 r6 降级分支不再需要 | 建议下一轮在补挂成功后直接走 stencil 路径；降级分支保留为「补挂失败」兜底 |

### 6.5 验收方法（若实现）

`-Dforge.forceDisplayStencil=true` 那套判读直接适用（见 §7.2），且**不加 JVM 参数**时日志应从
`main framebuffer has NO stencil attachment` 变为「stencil patch 成功」的一次性 INFO；
APM4s 红点 / FMP13 准星盘 / REAP-IR 十字在默认启动下可见。

---

## 7. 实机验证清单

### 7.0 r7 第二轮新增验收（运行期 stencil 实做，重点）

> 默认启动（**不加** `-Dforge.forceDisplayStencil=true`）现在应当走完整模板路径。

1. 启动进世界后日志出现（一次性 INFO，二选一）：
   - `TACZ: main framebuffer stencil ENABLED (...)`：补挂成功 ⇒ 后续应看到
     `scope lens pass ACTIVE: ... stencil=true` 与 `scope lens stencil path ACTIVE: ...`；
   - `TACZ: main framebuffer stencil patch DISABLED (...)` / `... deferred (...)`：补挂失败/延后（含原因），
     走 §3.2 兜底；回传该行。
2. **腰射（不按右键）**：HAMR（MP5A5）不应再出现「悬浮红叉/红框」与「大镜中间黑斑」
   （对照 `docs/review/r7/screenshots/b_hand_scope_artifacts.png` 的两个红框 + 大镜黑圆）；
   外环/镜身正常，允许镜筒内部略显空心（仅兜底分支的代价）。
3. **开镜（ADS）**：日志 `scope lens disc (NDC, full ADS): ... reticles=N` 且 **N>0**（HAMR/纯镜都要 >0）；
   镜内能看到准星/刻度；目镜黑遮罩只在瞄准圆外；镜内看不到枪体；组合镜（HAMR）镜片内不出现枪体。
4. **开镜过渡段**：HAMR 抬枪/收镜过程不应出现黑框（r6-3 验收项，重点回归）。
5. **resize / 全屏切换后**：再开镜仍应 `stencil=true`（补挂会重挂）；若失败回传日志。
6. 断线重连后再开镜：镜片/热成像仍生效（r7 第一轮修复项，一并回归）。

### 7.1 默认启动（无 `-Dforge.forceDisplayStencil`，即用户当前环境）

1. **回归：大黑框/空壳/镜内黑屏**（r6 验收项，本轮未改这些分支，仍应保持）：
   AWM + FMP13 / REAP-IR / Pulsar XL50 / APM4s / HAMR(MP5) 各开镜一次：
   - 未开镜：枪模完整，无黑块；
   - 开镜：无大黑矩形；镜片区域显示（放大的/热成像的）世界画面；HAMR 抬枪/收镜过渡段无黑框。
2. **热成像**（本轮改了点东西，重点回归）：
   - 对僵尸/动物开热成像镜：热源身上出现热色块（红/黄/白取决于 `vision_mode`），被方块挡住的部分不热；
   - 关键日志：`TACZ: thermal mask pass ACTIVE: WxH, sceneDepth=blit-ok, maskBuffers=N`（`N>0` = 本帧录到了热源）
     + `TACZ: scope lens pass ACTIVE: ... thermal=true`；
   - 若出现 `thermal mask scene-depth blit FAILED`：热源隔墙可见（可接受降级），回传该 WARN 行。
3. **断线重连回归（本轮新修，重点）**：
   - 进世界 → 开镜确认镜片/热成像生效 → 退出到主菜单 → 重新进世界 → 再开镜：
     **镜片与热成像必须仍然生效**（修前：一次断线后整局都没有镜片效果，且无任何日志）；
   - 若重连后失效，抓 `TACZ:` 开头的行回传（重点看 `scope lens pass ACTIVE` 是否再次出现）。
4. **状态泄漏回归（本轮补了 RenderState）**：
   - 开热成像镜 → 关镜/切枪/开背包/看 HUD：不应出现「世界或 HUD 变黑/变暗」；
   - 开镜后立刻按 F1 / F5（第三人称）：不应崩、不应残留全屏拷贝开销（无日志）。
5. **日志判读**（`TACZ:` 前缀）：
   - `main framebuffer stencil ENABLED (...)`：运行期补挂成功（默认启动的预期结果，一条；见 §7.0）；
   - `scope attachments use the NO-STENCIL degraded path ...`：模型侧降级（仅在补挂失败/延后时出现）；
   - `scope lens pass ACTIVE: N lens(es), stencil=true, thermal=?, WxH`：镜片确实在画且模板路径生效（补挂失败时才是 `stencil=false`）；
   - `scope lens disc (NDC, full ADS): center≈(0,0), radius>0`：圆盘位置正确；
   - `scope stencil capture DISABLED (...)`：**默认环境不应出现**（只有 forceDisplayStencil 下 capture 失败才出现）。

### 7.2 完整 stencil 路径（`-Dforge.forceDisplayStencil=true`，本轮新修，重点）

1. 启动后确认一次性 WARN 不再出现（或出现 patch 相关提示）；
2. 开镜：应看到
   `TACZ: scope lens stencil path ACTIVE: captureTarget=WxH, stencilRef=..., reticles=K, mainColorTexture=ID`
   （**本轮新增日志；出现 = 模板副本 + blit 成功**）；
3. 验收：目镜黑遮罩只出现在瞄准圆外、准星/分割面（红点、十字、弹道刻度）出现在镜内且位置正确、
   组合镜（HAMR）镜内看不到枪体、镜片边缘无黑框；
4. 若出现 `TACZ: scope stencil capture DISABLED (...)` 或 `scope lens stencil path` 缺失：
   回传该行 + `captureTarget` 相关上下文（说明该驱动上 GL3.0 FBO/blit 不可用，会自动退回无模板圆盘，不崩）。
5. 开镜/关镜/切枪/断线重连各一次（capture target 跨帧复用，重点看是否泄漏 GL 错误）。

### 7.3 崩溃/异常观察点

- 断线（单机退出到主菜单、多人被踢）时不得崩（本轮把 close() 改成渲染线程清理，重点回归）；
- 窗口 resize / 全屏切换后开镜（copy texture / capture target 尺寸变化路径）；
- 光影（Angelica/Iris/OptiFine）下开镜：core profile 时镜片整体禁用（一次性 WARN），不应崩。

---

## 8. 本轮改动文件 / 自查

| 文件 | 改动 |
|---|---|
| `client/renderer/scope/ScopeLensRenderer.java` | stencil capture target（`CaptureStencil`）+ `drawLensWithStencil`；`close()/drainDeletes()` 改为「请求 + 渲染线程清理」且不销毁 shader；`RenderState` 补齐状态还原；`capture()` viewport 检查 + 视空间圆几何；partialTick；scissor/纹理单元/拷贝纹理健壮性；一次性 stencil 路径 INFO/WARN |
| `client/renderer/scope/ScopeThermalRenderer.java` | `beginEntity(Entity)`；深度探测顺序修复 + 深度清理 + 关 scissor/stencil test + 去 alpha test；`close()/drainDeletes()` 线程安全且保留 shader |
| `client/renderer/scope/ScopeLensProgram.java` | 删除无调用者的 `destroy()`（shader 不再销毁） |
| `client/model/BedrockAttachmentModel.java` | 目镜模板圆半径 / ADS 进度改用 `ClientTickHelper.getPartialTick()`（上游 `getFrameTime()`）；无 stencil 兜底的腰射段不再画 division/目镜遮罩（§0.1-12）；删除 `isDivisionTooLarge`/`DIVISION_MAX_UV_SIZE` |
| `mixin/client/EntityRenderDispatcherMixin.java` | `Entity` 参数 + 跳过 static-entity display-list pass + 形参更名 + javadoc 依据 |
| `client/renderer/scope/MainFramebufferStencil.java` | **新增**：运行期给主 framebuffer 补 depth-stencil（§0.1-11 / §9） |
| `client/resource/pojo/display/attachment/ScopeLensConfig.java` | **未改**（与上游逐行一致） |

- `mcp__idea__lint_files(min_severity="error")`：以上 6 个改动 java 文件（含新增 `MainFramebufferStencil.java`）= **0 error**；
- **未运行 gradle**（由 Lead 统一构建）；
- 作用域内无 `[TACZ-DBG]` / 每帧 DBG；新增日志全部是一次性 INFO/WARN；
- 未改 `tmp/**`、`build/**`、`run/**`、`src/main/resources/**`；未改其它 scope 文件
  （`mixin/client/EntityRenderDispatcherMixin.java` 已与 r7-platform 协调）。

---

## 9. 实机复核②：运行期 stencil 实做（r7 第二轮，默认环境修复）

### 9.1 实机证据（用户复测 + 日志）

截图 `docs/review/r7/screenshots/b_hand_scope_artifacts.png`（MP5A5 + HAMR 组合镜，默认启动）：

1. **空中悬浮红色准星**（截图上方红框：红框 + 红十字）：日志 `scope no-stencil fallback: branch=hipfire(full model incl. divisions)` + `division plane: drawn=true, divisionPaths=1/2` ⇒ 腰射把 division 未裁剪画出（HAMR sight division 是透明底红叉/红框）；
2. **大镜中间黑圆斑**：同一 hipfire 分支画出的 division/目镜面未裁剪；
3. **开镜无准星**：`scope lens disc ... reticles=0`（无 stencil 时 `capture()` 不登记 reticle）。

上游 1.20.1 开镜**有**准星（division 由模板裁剪后在镜片 pass 里绘制），以上三项都是无 stencil 降级，不是上游行为。

### 9.2 实现（`client/renderer/scope/MainFramebufferStencil.java`，新增 295 行）

| 环节 | 做法 |
|---|---|
| 补挂 | 生成 `GL_DEPTH24_STENCIL8` renderbuffer → 同时挂 `DEPTH_ATTACHMENT` + `STENCIL_ATTACHMENT`（与 1.7.10 Forge `Framebuffer.java:117-130` 的 forceDisplayStencil 分支、Forge 1.20.1 `RenderTarget.java.patch:34-55` 同构）→ `glCheckFramebufferStatus` 通过后替换 `Framebuffer#depthBuffer`、删旧 RB；失败则摘 stencil、恢复旧深度附件、删新 RB |
| 调用点 | `ScopeLensRenderer#beginFrame()`（`renderWorld` HEAD，主 FBO 已绑定）第一件事；`init()` 末尾也会立即执行一次（1.7.10 `Minecraft.startGame` 在 `finishMinecraftLoading`（mod init）之前就建好了主 FBO，`Minecraft.java:509/597`） |
| resize/全屏 | 每帧查询主 FBO 的 `STENCIL_ATTACHMENT` 对象类型；`Framebuffer#createBindFramebuffer` 重建后查询返回 NONE ⇒ 自动重挂（**不需要 mixin**，也不用改 r7-fp 的 GameRendererMixin / r7-platform 的 ClientSetupEvent） |
| 状态安全 | 保存/恢复 `FRAMEBUFFER_BINDING`、`RENDERBUFFER_BINDING`、`STENCIL_WRITEMASK`、`STENCIL_CLEAR_VALUE`；补挂后清一次 stencil |
| 失败回退 | 一次性 WARN + 永久回退（`failed=true`）；不崩、不每帧重试刷屏 |
| 环境边界 | GL<3.0 → 永久回退；core profile（Angelica/Iris 光影）→ 永久回退；光影包正在使用 → 每 100 帧重试（用户可能关光影）；OptiFine → 永久回退；主 FBO 深度不是 renderbuffer（被光影模组换成 depth texture）→ 永久回退，绝不覆盖 |
| 与 Forge `stencilBits` 共存 | `MinecraftForgeClient.getStencilBits()>0`（`-Dforge.forceDisplayStencil=true`）时直接认为可用、不补挂；`ScopeLensRenderer#isStencilAvailable()` 改为「Forge 有 \|\| 运行期补挂成功」，模型侧/热掩膜自动切换 |

- 热掩膜深度纹理格式同步改为按 `isStencilAvailable()` 选择（D24S8 ↔ 主深度 D24S8，深度 blit 才能成功）。
- 无模板兜底修正（§0.1-12）：腰射不再画 division/目镜黑遮罩（`BedrockAttachmentModel#renderWithoutStencil`）。
- 默认启动日志链：`main framebuffer stencil ENABLED` → `scope lens pass ACTIVE: ... stencil=true` → `scope lens stencil path ACTIVE: ... reticles=K`。

### 9.3 与 r6 降级的取舍

- 补挂成功 = 上游模板路径（§3.1）⇒ §4.1 的 7 项降级全部消失，r6 三段降级分支降为纯兜底（保留不删）。
- 补挂失败（老驱动/GL2.1/OptiFine/光影）仍走 §3.2；腰射 artifact 已按 §0.1-12 去掉。
- 未实机验证前**不删除**任何降级分支（本轮只做「默认走完整路径 + 兜底不产生 artifact」）。

### 9.4 风险 / 边界

| 风险 | 处理 |
|---|---|
| 驱动拒绝 D24S8 renderbuffer | FBO 不完整 ⇒ 还原旧附件 + 一次性 WARN + 永久回退 |
| resize 后旧 RB 泄漏 | 旧 FBO/RB 由 `Framebuffer#deleteFramebuffer` 删除（新 RB 已写进 `depthBuffer` 字段，归 Framebuffer 所有） |
| 其它模组接管主 FBO | 每帧查询到已有 stencil 就直接用；深度是 texture 时不覆盖 |
| `Framebuffer#framebufferClear` 不清 stencil | 与上游一致；模板由模型路径 `glClear(GL_STENCIL_BUFFER_BIT)` 清（已补 `stencilMask(0xFF)` 归位） |
| 光影 / Angelica | shader pack 开着时不补挂（core profile 本来整条镜片链路禁用） |

### 9.5 跨模块发现（本轮实机截图附带）

- `docs/review/r7/screenshots/b_hand_scope_artifacts.png` 左下角红框里是一块**世界空间的大平面**，内容是「弹药类型色带 + `HK-MP5A5` 文字」= `GunHudOverlay` 的 HUD 内容（`ammo/slot/font.png` 色带 + 枪名文字）被画进了 3D 世界 ⇒ **不是瞄具系统问题**，属 HUD/矩阵泄漏（r7-gui / r7-crosshair 或 GuiGraphics 的 pose/矩阵还原），本报告只登记。
- `util/RenderHelper#enableItemEntityStencilTest` 的 javadoc 仍写「1.7.10 没有可补挂 stencil 的主 framebuffer API，只能降级」——已被本轮实现推翻（功能不需要改，注释需 r7-util 更新）。
- `client/model/BedrockGunModel#renderUnaccelerated` 的「r3 登记：默认 FBO 没有 stencil 附件」长注释同理过时（属 r7-fp 写作用域，未改）。
- `client/init/ClientSetupEvent.java:171-172` 重复 `ScopeThermalRenderer.init()`（§5-1）仍存在。

### 9.6 改动文件（本轮增量）

| 文件 | 改动 |
|---|---|
| `client/renderer/scope/MainFramebufferStencil.java` | **新增**（295 行）：运行期补挂/重挂/失败回退/环境边界/一次性日志 |
| `client/renderer/scope/ScopeLensRenderer.java` | `beginFrame()` 调 `ensure()`；`isStencilAvailable()` 纳入运行期补挂；init 日志改写；镜片 pass 兜底 WARN；删除无调用者的 `projectUvBounds` |
| `client/renderer/scope/ScopeThermalRenderer.java` | 热掩膜深度纹理格式改按 `isStencilAvailable()` 选择 |
| `client/model/BedrockAttachmentModel.java` | 兜底腰射段不画 division/目镜遮罩；删除 `isDivisionTooLarge`/`DIVISION_MAX_UV_SIZE`；stencil 路径清模板前 `stencilMask(0xFF)` 归位；过时 r3 注释更新 |

- `lint_files(error)` = 0（4 个 java 文件：3 个改动 + 1 个新增）；未跑 gradle；未改 `tmp/build/run/resources`；未改 r7-fp/r7-platform 的文件（不需要改：attach/resize 全部走本类 + 现有 `beginFrame` hook）。
---

## 10. 实机复核③：ADS 镜中画面 + 准星（stencil=true 路径）

### 10.1 实机现象（用户 23:41–00:31 测试，stencil 补挂已成功）

```
[23:42:36] TACZ: main framebuffer stencil ENABLED (runtime GL_DEPTH24_STENCIL8 renderbuffer 854x480)
[23:44:50] TACZ: scope lens pass ACTIVE: 1 lens(es), stencil=true, thermal=false, size=854x480
[23:44:50] TACZ: scope lens stencil path ACTIVE: captureTarget=854x480, stencilRef=254, reticles=1, mainColorTexture=2
[23:45:09] TACZ: scope lens disc (NDC, full ADS): center=(0.0023,-0.0028), radius=(0.218,0.388), stencilValid=true, reticles=1, vision=NORMAL
```

用户报告（倍镜/非机瞄，ADS）：
- **A：镜中画面无法正常渲染**（未说明是黑/白/不放大/花屏）；
- **B：开镜没有准星**（`reticles=1` 已登记但屏幕上看不到）。

### 10.2 与上游逐行对照（本轮重点核对项）

| 环节 | 上游 1.20.1 | 本轮修复前 | 判定 |
|---|---|---|---|
| **lens pass 的 snapshot** | `drawLens:226-231`：capture target 的 color attachment 已被主颜色纹理替换，它自己的颜色纹理空着 ⇒ 先 `_glCopyTexSubImage2D` 把「当前主画面」拷进自己的纹理，再 `setShaderTexture(0, screenTexture)`；Sampler2 的 fallback 也用同一张 | 采 `prepareWorldCopy()` 在**手部之前**拷的旧 `copyTexture` | **偏离已修**：stencil 路径改为在 capture target 内现拷「本帧最终主画面」到 `dummyColorTexture`，Sampler0/Sampler2 采它（`copyMainColorIntoCaptureSnapshot`）；失败才退回 `copyTexture`。无 stencil 路径保持 `copyTexture`（r6 已验证可见） |
| **reticle 绘制** | `render():196-209`：每个 reticle `draw.type.setupRenderState()` → **重新 `target.bindWrite(false)`** → `disableDepthTest/depthMask(false)` → `BufferUploader.drawWithShader` | 缺「重新绑定 target」；未显式重设模板 func/op | **已修**：每个 reticle 前重绑 capture FBO + 重挂主颜色纹理 + 重设 `GL_EQUAL lens.stencil` + `stencilOp(KEEP)`；再 setup/draw |
| **stencil ref 来源** | `BedrockAttachmentModel:317` `b = ~(i+1) & 0xFF`；写模板：`renderOcularStencil` REPLACE 写 `i+1`、`renderOcularAndDivision` INVERT 得 `b` | 同（`stencilRef=254` = `~(0+1)&0xFF`，合法） | 一致（日志 254 合法） |
| **capture 前置** | `capture():73-123`：drawFbo 非 0、主目标尺寸、viewport 铺满、**主目标有 STENCIL_ATTACHMENT**、模板 blit 到 capture target | 同（本轮补挂后 `stencilAvailable()`=true） | 一致 |
| **prepareCapture** | `prepareCapture():134-160`：按尺寸复用 capture target，`enableStencil()` 建 depth-stencil | `CaptureStencil.ensure` 同构（D24S8 RB + dummy color） | 一致 |
| **view-space 圆几何** | `buildSurface:273-284`：中心/半径 = `center*16*90` / `80*modifier*progress`，`SURFACE_Z=-90` | 同（`drawViewSpaceDisc`） | 一致 |
| **capture 矩阵** | `RenderSystem.getProjectionMatrix()/getModelViewMatrix()` | `glGetFloat(GL_PROJECTION_MATRIX/GL_MODELVIEW_MATRIX)`（同一时刻，等价） | 一致（Bounds NDC 日志也证明矩阵可用） |

### 10.3 本轮修复

1. **snapshot 语义对齐上游**（`copyMainColorIntoCaptureSnapshot`）：stencil 路径的 Sampler0/Sampler2 不再用手部前的旧快照，
   改为「在 capture target 内把本帧最终主画面 `glCopyTexSubImage2D` 到它自己的颜色纹理再采样」；拷贝失败 WARN 一次并退回 `copyTexture`。
   —— 这是 lead 指出的 A 的第一嫌疑点，现与上游逐行同构。
2. **reticle 重绑 target + 重设模板状态**（`drawReticle`）：每个 reticle 前 `glBindFramebuffer(capture)` + 重挂主颜色纹理 +
   `GL_EQUAL lens.stencil` + `stencilOp(KEEP)`，再 `setupRenderState()` / 关深度 / `BufferUploader.draw`。这是 B 的第一嫌疑点。
3. **自愈兜底（A 的保险）**：stencil 路径画完后用 `glReadPixels` 采镜片中心 5 点颜色，与画前比较：
   - 变了 ⇒ 上游视空间圆盘确实写入主画面（`lensViewSpacePathWorks=true`）；
   - 没变 ⇒ 判定该环境下「视空间圆盘」画不出来，**同一帧立刻补画 NDC 椭圆**（与 r6 已验证可见的无 stencil 圆盘同一套几何，
     identity 矩阵 + 关模板测试），保证镜中一定有画面；后续帧直接走该兜底。一次性 WARN 说明原因。
   —— 这保证「即使 A 的根因是圆盘几何/矩阵类问题，用户也能先看到镜中画面」，同时诊断日志给出根因证据。
4. **reticle 顶点包围盒统计**（`ReticleBuffers.BboxConsumer`）：录制时顺带统计顶点相机空间包围盒（不改绘制路径），
   供诊断判读 reticle 是否被投影到屏幕外/为空。

### 10.4 新增一次性诊断日志（每类一条，不刷屏）

| 日志 | 含义 / 判读 |
|---|---|
| `TACZ: scope lens stencil probe (before draw): pixel=..., colorBefore=0x..., stencilAtCenter=N, stencilRef=254, discNdc=[...], ocularNdc=[...], fboStatus=...` | **stencilAtCenter 是否=254**（=254 说明模板副本正确；0/其它说明模板写入/拷贝/读回有问题）；`discNdc` 是否覆盖 `ocularNdc`（判读视空间圆盘几何）；`fboStatus=36053` 才是 COMPLETE |
| `TACZ: scope lens stencil probe (after draw): ... lensDrew=..., reticleDrew=..., glError=0x...` | 区分「镜片圆盘有没有写进主画面」与「reticle 有没有写进主画面」；glError 非 0 说明绘制命令失败 |
| `TACZ: scope reticle probe: draws=..., vertices=..., camera=[...], ndc=[...], stencilRef=..., glErrorBefore=..., glErrorAfter=...` | reticle 顶点数（0=空 buffer）、相机空间/NDC 包围盒（是否在屏幕内）、绘制前后 GL 错误 |
| `TACZ: scope lens stencil view-space disc drew nothing ...` WARN | 自愈兜底触发（A 的根因证据 + 已自动退回 NDC 椭圆） |
| `TACZ: scope stencil capture DISABLED (...)` WARN | capture target / 快照拷贝失败（该 lens 退回无模板圆盘） |

### 10.5 下一轮实机预期（判据）

1. 默认启动（无 JVM 参数）进世界开镜（倍镜 ADS）：
   - 应出现 `main framebuffer stencil ENABLED` → `scope lens pass ACTIVE: ... stencil=true` → `scope lens stencil path ACTIVE: ...`；
   - 应出现上述 3 条 probe 日志，重点看：
     - `stencilAtCenter=254`（期望值）；
     - `lensDrew=true`（期望；若 `false` 会看到自愈 WARN 且镜中仍有 NDC 椭圆画面）；
     - `reticleDrew=true`（期望；若 `false` 而 `vertices>0`、`ndc` 在屏幕内，说明是 reticle 绘制状态问题，回传该行）；
     - `discNdc` 覆盖 `ocularNdc`（若 discNdc 巨大/偏移，回传该行）。
2. 画面判据：镜中有（放大的）世界画面 + 镜内准星/刻度；目镜黑遮罩只在瞄准圆外；镜内看不到枪体。
3. 若镜中画面仍不对但 probe 显示 `lensDrew=true`：说明「画了但内容不对」⇒ 下一轮看 snapshot 来源
   （`copyMainColorIntoCaptureSnapshot` 失败会 WARN 并退回 `copyTexture`）与 Sampler 采样；回传 probe 行 + 是否有 snapshot WARN。
4. 若 `stencilAtCenter != 254`：模板副本/写入链问题，回传该行（含 `fboStatus`），下一轮按「主模板写入值 / blit / 读回」三选一定位。

### 10.6 说明（无法实机验证）

本轮改动无法在本机实机验证（禁止跑 gradle、用户禁用调试桥），只做了静态对照 + IDEA 符号/lint 检查（0 error）。
因此新增了上述**一次性诊断 + 自愈兜底**：下一轮实机日志可直接定位 A/B 根因，且即使视空间圆盘路径仍有问题，镜中画面也不会再是空白/异常。

### 10.7 本轮增量改动

| 文件 | 改动 |
|---|---|
| `client/renderer/scope/ScopeLensRenderer.java` | stencil 路径 snapshot 语义对齐上游（`copyMainColorIntoCaptureSnapshot`）；`drawLens` 增加 `screenTexture` 参数；`drawReticle` 重绑 target + 重设模板 + 顶点包围盒诊断；自愈兜底 `drawLensFallback`（视空间圆盘画不出时退回 NDC 椭圆）；3 条一次性 probe 日志（`glReadPixels` 颜色/模板、圆盘 NDC 包围盒、reticle 顶点/NDC）；`ReticleBuffers` 增加 `BboxConsumer` 顶点包围盒统计；`Lens` 增加 `reticleBbox` |

- `lint_files(error)=0`；未跑 gradle；未改 `tmp/build/run/resources`、未改 HUD/`client/gui/**`（按 lead 更正：HUD 正常，不属本任务）。
---

## 11. 实机复核④：INVERT 圆坐标系错位（A「镜中没画面」+ B「没准星」共同根因，已修）

### 11.1 实机 probe 定量证据（854x480，倍镜 ADS）

```
scope lens stencil probe (before draw): pixel=(552,196), stencilAtCenter=1 (期望254), stencilRef=254,
  discNdc=[2.730,-1.741..2.755,-1.697], ocularNdc=[0.217,-0.315..0.367,-0.051], fboStatus=36053
scope reticle probe: draws=1, vertices=288, camera=[0.054,-0.178,-2.863..0.296,0.054,-0.845],
  ndc=[0.018,-0.365..0.341,0.111], stencilRef=254, glError 0x0
scope lens stencil probe (after draw): lensDrew=false, reticleDrew=false, glError=0x0
WARN: view-space disc drew nothing → fallback NDC ellipse
```

定量结论：
1. **`stencilAtCenter=1`（期望 254 = ~1）** ⇒ 模板 INVERT 圆没有落在目镜上；`GL_EQUAL 254` 对镜片圆盘与 reticle **全部失败**
   ⇒ A（镜中无画面）与 B（无准星）**同一根因**。
2. **`discNdc` vs `ocularNdc` 相差 ~9.3 倍**（两者都是把 camera-space 顶点用同一份 capture 矩阵投影，
   只是前者的输入是 `center*16*90` + `z=-90`、后者是 PoseStack 后的真实目镜几何）：
   上游「视空间圆」依赖 1.20 RenderSystem 的相机空间单位；本移植第一人称是
   **块单位 camera space + 手部投影**（`FirstPersonRenderHandler:463-477` 的 `glLoadIdentity` + 手部投影），
   `*16*90`（=1440）的换算在本移植不成立，圆心/半径被放大到屏幕外 ⇒ INVERT 区域为空。
3. `fboStatus=36053`（COMPLETE）、`glError=0x0` ⇒ capture FBO/绘制链路本身正常；`reticle` 顶点 288 个、
   NDC 在屏幕内、模板 ref 254 ⇒ **reticle 顶点与矩阵都是对的**，只差「模板区域非空」。

### 11.2 修法（1.7.10 坐标系差异的等价实现，不发明上游语义）

上游语义：**镜片 = 目镜 ∩ 瞄准圆**（INVERT 圆半径随 ADS progress 生长，被目镜模板裁剪），准星在镜内。
本移植把「视空间几何」整体换成 **Bounds 投影出的屏幕 NDC 椭圆**（同一套投影数学，r6 已验证可见）：

| 位置 | 修复前 | 修复后 |
|---|---|---|
| `BedrockAttachmentModel#renderOcularAndDivision` 的 INVERT 圆 | `center*16*90` + `z=-90` + `rad=80*progress` 的视空间圆（Tessellator，手部投影/模型视图） | 先在 capture 时刻 GL 矩阵下用 `ScopeLensRenderer#projectUvBounds` 把该 ocular 投影成 `{minU,minV,maxU,maxV}`，再 push identity 投影/模型视图画 **NDC 椭圆**；椭圆中心 = Bounds 中心、半径 = Bounds 半径 × `aimProgress`（近似上游「半径 80*progress 的圆被目镜裁剪」：progress=1 铺满目镜、progress=0 无 INVERT ⇒ 目镜黑遮罩照常画） |
| `ScopeLensRenderer#drawLens`（stencil 路径） | `drawViewSpaceDisc(centerX, centerY, radius)`（视空间圆 + capture 矩阵） | `drawNdcDisc(centerU/V, radiusU/V)`（identity 投影/模型视图，与 INVERT 椭圆同一套 Bounds） |
| `capture()` 的 `centerX/centerY/radius` 参数 | 用于视空间几何 | **只保留上游签名 + 诊断参考**（`Lens.centerX/centerY/radius` 仅进 probe 日志）；几何不再使用 |
| `SURFACE_Z` / `drawViewSpaceDisc` / `projectCircleNdcBounds` | 存在 | **删除**（改用 `projectDrawnDiscNdcBounds` = 实际绘制的 NDC 椭圆包围盒） |
| 自愈兜底 / probe / snapshot 语义 / reticle capture 矩阵 | — | **保留**（probe 期望值更新为：`stencilAtCenter=254`、`lensDrew=true`、`reticleDrew=true`、`discNdc≈ocularNdc`） |

为什么这是「等价实现」而不是降级：INVERT 椭圆与镜片椭圆使用**同一套 Bounds 投影**（capture 时刻 GL 矩阵 ×
PoseStack 后的目镜顶点），因此「镜片被裁到目镜内」的几何关系与上游一致；`aimProgress` 缩放保留了
「瞄准圆随开镜生长」的语义；hipfire（progress≈0）不画 INVERT，目镜黑遮罩照常绘制（与上游一致）。

### 11.3 下一轮实机判据

1. 开镜（倍镜 ADS）后 probe 期望：
   - `stencilAtCenter=254`（关键：修好后 INVERT 椭圆覆盖镜片中心）；
   - `discNdc≈ocularNdc`（现在两者由同一 Bounds 生成，应几乎相同）；
   - `lensDrew=true`、`reticleDrew=true`；
   - 不应再出现 `view-space disc drew nothing` WARN。
2. 画面：镜中出现放大的世界画面（含镜片 shader 的暗角/畸变/染色）+ 镜内准星/刻度；
   目镜黑遮罩只在瞄准圆外；镜内看不到枪体；腰射时目镜仍为黑遮罩（progress=0 不 INVERT）。
3. 若仍异常，回传 probe 行（重点 `stencilAtCenter` / `discNdc` / `lensDrew` / `reticleDrew`）即可继续定位。

### 11.4 本轮增量改动

| 文件 | 改动 |
|---|---|
| `client/model/BedrockAttachmentModel.java` | `renderOcularAndDivision` 的 INVERT 圆改为「Bounds NDC 椭圆 × aimProgress」（先算 Bounds 再 push identity 矩阵画）；`rad` 仅作 capture 门槛；保留 `capture()` 调用（参数不再参与几何） |
| `client/renderer/scope/ScopeLensRenderer.java` | `drawLens` 统一画 NDC 椭圆（删除 `drawViewSpaceDisc`/`SURFACE_Z`/`projectCircleNdcBounds`）；stencil 路径仍保留 capture FBO/主颜色附件/snapshot 语义/reticle capture 矩阵；probe 更新（`discNdc`=实际绘制椭圆、加 `upstreamViewSpace` 原始参数） |

- `lint_files(error)=0`；未跑 gradle；未改 HUD/`client/gui/**`、未改 r7-fp/r7-platform 文件。
---

## 12. 实机复核⑤：Elcan 4x（`scope_elcan_4x`）镜中纯黑

### 12.1 实机证据

- 截图 `docs/review/r7/screenshots/c_elcan_ads_black.png`（856x512 窗口，ADS）：**镜片区域整片纯黑、形状是八边形**，
  颜色均匀 `RGB(17,18,21)`；镜体、目镜环、手部、HUD 全部正常。98k / AUG 纯倍镜在同环境下正常。
- 该纯黑八边形 = Elcan `ocular` 节点的形状（8 个旋转 cube 组成的八边形盘，`scope_elcan_4x_geo.json` 的
  `oculars/ocular`），颜色 = 目镜黑遮罩贴图（暗灰）在光照下的结果 ⇒ **目镜黑遮罩被画出来了**。
- 13:25:42 的 probe（stencilAtCenter=254 / lensDrew=true）无法确定属于 Elcan（该次镜头中心 NDC≈(0.34,-0.07)，
  与截图里居中的目镜不符，更可能是 98k/AUG 的测试）；按截图与「Elcan 会话没有镜片 pass 日志」推断：
  **Elcan 的 INVERT 与 capture 都没有执行**。

### 12.2 根因（file:line）

移植侧把上游的「视空间圆」换成了「投影包围盒椭圆」（r7 实机复核④，见 §11）：

- `ScopeLensRenderer.Bounds.isValid()` 原实现要求 **没有任何顶点在相机平面后面**（`behindCamera == false`）。
- Elcan 的目镜几何（`ocular` 节点 z≈+1.83..2.05，是整个模型里最靠玩家的目镜盘）在 ADS 相机贴近目镜时，
  **部分顶点跨到相机平面后面**（`w <= 0`）⇒ `Bounds` 判无效。
- 于是：
  1. `BedrockAttachmentModel.renderOcularAndDivision` 的 INVERT 椭圆 `projectUvBounds(...)` 返回 `null`
     ⇒ **INVERT 圆被跳过**（`BedrockAttachmentModel.java:396-410`）；
  2. `ScopeLensRenderer.capture(...)` 的 `if (!bounds.isValid()) return false` ⇒ **镜片不登记**
     ⇒ 帧末没有镜片 pass（所以 Elcan 会话没有 `scope lens stencil path ACTIVE` / probe 日志）。
- 结果：目镜区域的模板值仍是 `i+1`（没有反选成 254）⇒ `renderOcularAndDivision` 用 `GL_EQUAL i+1`
  把**目镜黑遮罩**画在整个目镜上 ⇒ 截图里的纯黑八边形；镜片 pass 不运行 ⇒ 没有镜片画面。
- 上游 1.20 用「视空间圆」（`center*16*90` / `z=-90` / 半径 `80*progress`），**不依赖投影包围盒**，
  因此没有这个边界情况 —— 这是移植侧 Bounds 方案特有的问题，不是上游语义。

### 12.3 修复（等价实现，不发明上游语义）

| 位置 | 改动 |
|---|---|
| `ScopeLensRenderer.Bounds` | `isValid()` 拆成 `hasUsableBounds()`（有可见顶点、非退化）+ `hasBehindCamera()` + `useScreenBounds()`（跨相机平面 / 包围盒不可用时退回**整屏** `[0,1]²`） |
| `ScopeLensRenderer.capture()` | bounds 跨相机平面 / 不可用时**不再 `return false`**，改用整屏 bounds 继续登记镜片 ⇒ 镜片 pass 一定运行（镜片椭圆被 `GL_EQUAL 254` 裁到目镜内，视觉上仍完整覆盖目镜） |
| `ScopeLensRenderer.projectUvBounds()` | 同样退回整屏 bounds ⇒ INVERT 圆一定画（INVERT 被 `GL_EQUAL i+1` 裁到目镜内） |
| `BedrockAttachmentModel.renderOcularAndDivision` | **ADS（`aimProgress >= 0.5`）时跳过 scope 目镜黑遮罩**：上游此时 INVERT 圆（半径 `80*0.5=40` @ `z=-90`，屏幕角半径 ≈24°）已完全覆盖目镜（≈8°），遮罩本来就不可见；跳过它是「与上游最终画面等价」的兜底，保证即使 INVERT 再异常也不会黑屏。过渡段（progress < 0.5）仍照上游画遮罩 |
| 一次性诊断 | `TACZ: scope ocular pass (stencil): aim=..., invertNdc=[...], lensNodeVisible=..., captureRegistered=N, divisionDrawnImmediately=N`（每个附件模型在完全开镜时一条）；capture / INVERT 的每种失败原因各 `warnOnce` 一条（program / main fbo / viewport / bounds / behind-camera） |

为什么等价：INVERT 圆与镜片圆盘都会被模板裁剪到目镜区域，整屏范围只影响「椭圆大小」，不影响可见区域；
遮罩跳过只在「上游本来看不见遮罩」的完全开镜阶段生效，过渡段行为不变。

### 12.4 下一轮实机判据

1. 日志（Elcan ADS）应出现：
   - `TACZ: scope ocular pass (stencil): aim=1.00, rad=..., invertNdc=[(c=...,r=...)], lensNodeVisible=..., captureRegistered=1, divisionDrawnImmediately=0`；
   - 若目镜几何跨相机平面，会同时出现 `TACZ: scope lens capture: ... crosses the camera plane ...` 或
     `TACZ: scope lens INVERT circle: ... Using full-screen bounds ...` 各一条（说明兜底生效，属预期）；
   - 随后应出现 `scope lens stencil path ACTIVE` + `scope lens stencil probe` 行。
2. 画面：Elcan ADS 镜中出现（放大的）世界画面（含暗角/畸变/染色）+ 镜内准星/刻度；不再是纯黑。
3. 若仍黑：回传 `scope ocular pass` 行 + 所有 `TACZ:` WARN 行（capture/INVERT 失败原因已各报一条，可直接定位）。
4. **A/B 单点开关（默认 = 修复态）**：`-Dtacz.debug.scopeStrictBounds=true` 恢复「Bounds 跨相机平面就放弃镜片/INVERT」的旧行为；
   `-Dtacz.debug.scopeDrawAdsMask=true` 恢复「完全开镜也画 scope 目镜黑遮罩」的旧行为。用于实机对照确认修复点。

### 12.5 本轮增量改动

| 文件 | 改动 |
|---|---|
| `client/renderer/scope/ScopeLensRenderer.java` | `Bounds` 拆分 + 整屏兜底；`capture()` / `projectUvBounds()` 不再因包围盒异常放弃；`warnOnce` 诊断（capture 各失败原因、bounds 兜底） |
| `client/model/BedrockAttachmentModel.java` | `renderOcularAndDivision`：ADS 时跳过 scope 目镜黑遮罩（等价兜底）；一次性 `scope ocular pass` 诊断（INVERT 椭圆 / capture 结果 / `lens` 节点可见性） |

- `lint_files(error)=0`；未跑 gradle；未改 HUD/`client/gui/**`、未改 r7-fp/r7-platform 文件。
---

## 13. 实机复核⑥：复核⑤ 全局回归回退（只保留诊断）

### 13.1 复核⑤ 的全局行为改动（本轮已全部回退）

| # | 位置 | 复核⑤ 改动 | 为什么是全局 |
|---|---|---|---|
| 1 | `ScopeLensRenderer.Bounds` | `isValid()` 放宽（去掉「有顶点跨相机平面即无效」）+ 新增 `useScreenBounds()` 整屏兜底 | 所有走 `capture()` / `projectUvBounds()` 的瞄具都会受影响 |
| 2 | `ScopeLensRenderer.capture()` | bounds 无效时**不再返回 false**，改用整屏 bounds 登记镜片 | 改变 lens 的 center/radius（变成 0.5/0.5）与「division 是否延迟到 lens pass」 |
| 3 | `ScopeLensRenderer.projectUvBounds()` | bounds 无效/跨相机平面时返回整屏 bounds | 改变所有 bounds 异常的瞄具的 INVERT 椭圆（stencil 254 区域） |
| 4 | `BedrockAttachmentModel.renderOcularAndDivision` | `aimProgress >= 0.5` 跳过 scope 目镜黑遮罩 | 影响所有 scope 类瞄具（**包括 APM4s 这种 `"scope": true` 的反射式红点**） |
| 5 | 两处 A/B 开关 | `-Dtacz.debug.scopeStrictBounds` / `-Dtacz.debug.scopeDrawAdsMask` | 随回退删除 |

### 13.2 回退结果（行为 = 复核④）

- `Bounds.isValid()` 恢复「有顶点在相机平面后面即无效」（`ScopeLensRenderer.java:1499`）。
- `capture()`：bounds 无效 ⇒ `return false`（`:463-474`），**不登记镜片、不用整屏 bounds**。
- `projectUvBounds()`：bounds 无效 ⇒ `return null`（`:560-570`），该 ocular 不画 INVERT。
- `renderOcularAndDivision`：恢复「始终按上游画 scope 目镜黑遮罩」（`BedrockAttachmentModel.java:488-497`），删除 aim 阈值跳过。
- 删除两个 A/B 开关。
- **保留（不改变行为）的诊断**：
  - `ScopeLensRenderer.java:621` `warnOnce`：capture 各失败原因（program / main fbo / viewport / bounds-invalid）+ `projectUvBounds` bounds-invalid（带 `behindCamera` 标记）各一条；
  - `BedrockAttachmentModel.java:364,381,542`：每个附件模型在 `aimProgress >= 0.5` 时一条
    `TACZ: scope ocular pass (stencil): aim=..., rad=..., invertNdc=[...], lensNodeVisible=..., captureRegistered=..., divisionDrawnImmediately=...`。

### 13.3 APM4s 红点 / 全局红点消失的归因（从代码推导）

- **APM4s 走的是 scope 路径，不是 sight 路径**：`sight_apm4s_display.json` 只有 `"scope": true`（没有 `"sight": true`），
  因此 `BedrockAttachmentModel.render()` 走 `renderScope` → `renderOcularAndDivision`，**被复核⑤ 的全局改动覆盖**
  （复核④ 时它和其它 scope 一样正常）。
- **最可能的直接机制 = `capture()` 的整屏 bounds 兜底**：复核④ 时若某瞄具的 ocular bounds 无效
  （反射镜/倍镜目镜紧贴 ADS 相机，很容易有顶点跨相机平面），`capture()` 返回 false ⇒ 调用方**立即画 division**
  （红点/准星）并按复核④ 的 stencil 路径出图；复核⑤ 改成「整屏 bounds 登记镜片」后 ⇒ division 被**延迟**到
  lens pass 用 `GL_EQUAL 254` 画，而同一帧的 INVERT 椭圆也被整屏兜底（中心=屏幕中心、半径还要 ×aimProgress）
  ⇒ **stencil 254 区域与红点/镜片的实际屏幕位置不匹配** ⇒ 红点/准星画不出来。
  实机日志 `stencilAtCenter=1`、`lensDrew=false`、`reticleDrew=false`、`invertNdc` 与实际目镜位置不一致，
  与这个机制一致（探针像素按整屏 bounds 取到屏幕中心，而 INVERT 椭圆在别处）。
- 「大部分倍镜红点/准星没了」同一机制：bounds 跨相机平面的瞄具被整屏兜底改变了 lens 中心 / INVERT 区域，
  延迟 reticle 的 stencil 区域对不上。
- 回退后这些资产回到复核④ 路径：bounds 有效者正常（INVERT/延迟 reticle 同复核④）；bounds 无效者 `capture=false`
  → division 立即画（复核④ 行为）。**我的复核⑤ diff 没有触碰 `renderSight` / `renderDivisionOnly` / sight 专属路径**；
  若回退后 APM4s 仍异常，则来源应是同一 jar 内的其它改动（可用本文件 §13.1 的清单核对我的 diff）。

### 13.4 回退后 Elcan 的预期表现（接受）

- Elcan 继续按复核④ 行为：若其 ocular bounds 确实无效（跨相机平面），则 `capture()` 返回 false + INVERT 跳过
  ⇒ **镜中仍可能是纯黑目镜遮罩**（与 `c_elcan_ads_black.png` 相同），属**已知降级**。
- 本轮目标优先保证「其它瞄具/红点全部恢复正常」；Elcan 的最终修复等 `docs/review/r7/scope-upstream-spec.md`
  的上游等价规格出来后，按规格做**局部**实现（不再用 Bounds→NDC 椭圆/阈值兜底这类近似）。

### 13.5 本轮增量改动

| 文件 | 改动 |
|---|---|
| `client/renderer/scope/ScopeLensRenderer.java` | 回退 Bounds 整屏兜底（恢复 `isValid()`）；`capture()` / `projectUvBounds()` 恢复「bounds 无效即放弃」；删除 A/B 开关；保留失败原因诊断 |
| `client/model/BedrockAttachmentModel.java` | 回退 ADS 跳过目镜遮罩（恢复始终画）；删除 A/B 开关；保留 `scope ocular pass` 诊断 |

- `lint_files(error)=0`；未跑 gradle；未改 HUD/`client/gui/**`、未改 r7-fp/r7-platform 文件。
---

## 14. 按上游规格回归实现（复核⑦；§11/§12 结论更正）

依据：`docs/review/r7/scope-upstream-spec.md`（r7-scope-spec 逐行核对上游 TACZ 1.20.1 + 1.20 原版渲染代码）。
本轮**按 spec §7.2/§7.3 逐条替换**，不再使用「Bounds→NDC 椭圆 / 阈值兜底」这类近似。

### 14.1 结论更正（spec §1.4/§1.5/§11.2）

| 本文件旧结论 | 更正 |
|---|---|
| §11.2「上游视空间圆 `center*16*90`/`z=-90` 依赖 1.20 相机空间单位，本移植换算不成立（≈1440 倍）」 | **不成立**：透视除法把 z 消掉，圆心 NDC ≡ `(m00·16·cx, m11·16·cy)`、半径 NDC ≡ `(m00·rad/90, m11·rad/90)`，与引擎无关；本移植 pose space 同为「格」、`ModelViewMat` 同为单位矩阵（`FirstPersonRenderHandler:463-477,599-616`） |
| §11.1「discNdc 与 ocularNdc 差 ~9.3 倍 ⇒ INVERT 圆不在目镜上」 | 两个量不是同一个东西（前者=固定角半径圆在 `16·(vx−ox)` 偏移下的包围盒，后者=目镜几何投影包围盒）；当时是抬枪早期帧（p≈0.017，圆本来就在屏幕外） |
| §12.2「Elcan 目镜顶点跨相机平面 ⇒ Bounds 无效 ⇒ 黑屏」 | 现象可复现，但根因归属错误：正确 pose 下 Elcan 目镜中心 `(0,0.0007,−0.4734)`、全部在相机前方；`Bounds` 的唯一用途是 shader uniform，**几何从不来自 Bounds**。若实机报 `behindCamera`，应在 §8.2 断言 A/B 上定位（pose/投影与上游不同源），属 r7-fp 域 |

### 14.2 替换清单（spec §7.2，全部落地）

| # | 文件:方法 | 改动 |
|---|---|---|
| 1 | `BedrockAttachmentModel#renderOcularAndDivision`（`:398-472`） | 删除「Bounds→NDC 椭圆 + push identity 矩阵」整块，改回上游 `:284-301` 视空间圆：`centerX = center.x()*16*90`、`centerY = center.y()*16*90`、`z = SURFACE_Z(-90)`、半径 `rad = 80*scopeViewRadiusModifier*aimProgress`，用**当前** GL 矩阵（手部投影 + 单位 modelview）画 TRIANGLE_FAN；`stencilFunc(EQUAL,i+1)`/`stencilOp(INVERT)`/colorMask/depthMask 与上游逐行一致（状态机未改） |
| 2 | `ScopeLensRenderer#drawLens`（`:1100-1187`） | 新增 `viewSpace` 参数：stencil 路径 `ProjMat/ModelViewMat = lens.projection/lens.modelView`（capture 矩阵，**不是 identity**）、几何 = `drawViewSpaceDisc(lens.centerX, lens.centerY, lens.radius)`；无 stencil 降级路径仍用 NDC 椭圆（spec §9-⑩ 白名单） |
| 3 | `ScopeLensRenderer` | 加回 `SURFACE_Z = -90`（`:104-112`）与 `drawViewSpaceDisc`（`:1189-1207`，上游 `buildSurface:273-284`：TRIANGLE_FAN 91 顶点） |
| 4 | `ScopeLensRenderer` | 删除自研项：`projectUvBounds`、`drawLensFallback`、`lensViewSpacePathWorks` 自愈分支、`projectDrawnDiscNdcBounds`；`probeStencilLensAfterSurface` 改为**只读**日志（不再触发兜底）；`Lens.centerX/centerY/radius` 恢复参与几何（javadoc 改为「上游几何参数」） |
| 5 | `ScopeLensRenderer#capture`（`:421-560`） | 加 scissor 保存/关闭/恢复（上游 `capture:99-104,128`）：scissor 开着时 `glBlitFramebuffer` 会被裁，模板副本残缺 ⇒ 镜片/准星局部消失 |
| 6 | `ScopeLensRenderer#CaptureStencil.ensure`（`:1790-1800`） | 颜色纹理 `MIN/MAG = GL_LINEAR`、`WRAP_S/T = GL_CLAMP_TO_EDGE`（上游 `prepareCapture:151` `setFilterMode(GL_LINEAR)` + `RenderTarget#createBuffers:114-115`） |
| 7 | `ScopeLensRenderer#Lens` | javadoc 改为「上游几何参数」；`centerU/V·radiusU/V` 仅用于 shader uniform（上游 :236-243） |

### 14.3 只读断言日志（spec §8.1/§8.2；默认资产期望见 spec §1.4）

| 日志 | 字段 / 期望 |
|---|---|
| `TACZ: scope capture matrices: proj.m00=..., proj.m11=..., expectedF=..., (ITEM_MODEL_FOV=...), modelViewIsIdentity=...` | 断言 B：`proj.m11 = f = 1/tan(ITEM_MODEL_FOV/2)`、`proj.m00 = f/aspect`、`modelViewIsIdentity=true` |
| `TACZ: scope ocular pass (stencil): aim=..., rad=..., invert=[(modelDelta=(vx−ox,vy−oy), centerPose=(cx,cy,cz), rad, ndcCenter=(...), ndcRadius=(...), modelViewIsIdentity=...)], lensNodeVisible=..., captureRegistered=N, divisionDrawnImmediately=N, proj.m00=..., proj.m11=..., modelViewIsIdentity=...` | 断言 A：`modelDelta` 与 spec §1.4 表差 ≤0.2（Elcan `(0,0.01125)`、ACOG `(0,0)`、FMP13 `(0,−0.056)`、HAMR `(0,−0.183)`）；`centerPose.cz ∈ [−0.75,−0.35]`；`ndcRadius_y` 在完全开镜时 ≥2.4（Elcan 2.82） |
| `TACZ: scope lens stencil probe (before draw): ... surfaceViewSpace=(c,r), surfaceNdc=(center,radius), modelViewIsIdentity=..., stencilAtCenter=...` | 断言 C：`surfaceNdc` 应与 `invert` 的 `ndcCenter/ndcRadius` 完全相等（同一份视空间圆）；`stencilAtCenter = lens.stencil = 254` |
| `TACZ: scope lens surface (view-space circle, NDC): center=..., radius=...` | 与上一行 `surfaceNdc` 一致 |
| `probe ... (after draw): ... lensDrew=..., reticleDrew=..., glError=...` | **只读观测**，不触发任何兜底（spec §8.1） |

probe 采样点已从「Bounds 中心」改为**视空间圆心**（NDC 换算后取像素），否则 Elcan/HAMR 这类 `modelDelta` 大的资产会采错点。

### 14.4 预期实机表现（spec §8.3）

1. **Elcan 4x**：镜中出现（放大的）世界画面 + 镜片效果（畸变/暗角/染色）+ 镜内准星/刻度，不再纯黑八边形。
2. 98k / AUG / ACOG / HAMR / APM4s：红点/准星/镜片效果与上游一致（INVERT 圆半径 NDC_y≥2.4 ⇒ 完全覆盖目镜，目镜黑遮罩只在圆外）。
3. 抬枪/收镜：镜片随瞄准圆从中心长出，无整屏黑框；腰射：目镜黑遮罩正常、无悬浮准星。
4. 无 stencil 环境（补挂失败/光影）仍走 `renderWithoutStencil` 三段降级 + NDC 椭圆（白名单，不动）。

### 14.5 断言越界时的处理（spec §7.0/§8.2）

若实机日志出现以下任一越界，**不在镜片代码里兜底**，属 r7-fp 域（`FirstPersonRenderGunEvent` 的 `getPositioningNodeInverse`/`views[]`/mount offset、`GunItemRendererWrapper` 的 `T(0,1.5,0)·Rz180`、`ITEM_MODEL_FOV`、或受伤摇晃写进 `GL_MODELVIEW`）：

- `modelViewIsIdentity=false`（`compensateHurtCameraEffect` 直接 `glRotatef` 到 `GL_MODELVIEW`，`FirstPersonRenderHandler:646-700`）；
- `proj.m11 != expectedF`（手部 FOV 取错：世界投影/zoom 后 FOV）；
- `|modelDelta| > 0.2` 或 `centerPose.cz ∉ [−0.75,−0.35]`（pose 链与上游不同源）。

发现越界时回传日志行 + 资产名给 Lead 转派。

### 14.6 本轮增量改动

| 文件 | 改动 |
|---|---|
| `client/model/BedrockAttachmentModel.java` | INVERT 圆改回上游视空间圆（用当前 GL 矩阵）；加只读断言日志与 `readGlMatrix/readProjectionDiagonal/isIdentityMatrix` 诊断助手；`SURFACE_Z` 常量 |
| `client/renderer/scope/ScopeLensRenderer.java` | `drawLens` 按上游喂 capture 矩阵 + 视空间圆；加回 `SURFACE_Z`/`drawViewSpaceDisc`；删除自研兜底（`projectUvBounds`/`drawLensFallback`/`lensViewSpacePathWorks`）；capture scissor；capture 颜色纹理 LINEAR+CLAMP_TO_EDGE；只读断言日志（capture 矩阵 / surface NDC / probe 采样点=视空间圆心） |

- `lint_files(error)=0`；未跑 gradle / 未用 mcctl；未改 `FirstPersonRenderHandler`/`AnimateGeoItemRenderer`（如需改会先 msg Lead）。
---

## 15. 复核⑧：镜中画面内容静态排查（只读诊断，未改行为）

实机（按上游规格实现后）日志：几何/矩阵断言大部分通过
（`proj.m00=0.974 / proj.m11=1.732 / expectedF=1.732`、`modelViewIsIdentity=true`、
完全开镜 `scope lens surface (view-space circle, NDC): center=(0.010,-0.053), radius=(1.441,2.564)`，
`radius_y≈2.56` 与 spec 预期 Elcan 2.82 同量级）。剩余两类问题：①某倍镜镜中整片半透明绿色；②③两例黑镜。
本轮**只做静态排查 + 加只读日志**，未改任何绘制行为；`lint_files(error)=0`；未跑 gradle / mcctl。

### 15.1 结论摘要

| Lead 的假设 | 静态结论 |
|---|---|
| (a) 镜片快照采到**枪模自己的玻璃/镜筒**（Elcan `lens` 节点等） | **不成立**。默认包所有 `lens`/`lens_illuminated` 都在 `scope_body` 子树里（唯一例外 `scope_lpvo_1_6` 的 `lens` 是 0 cube），而 `renderTempPart(scopeBodyPath)` 把 `scope_body.visible=false`；`BedrockPart.render`（`BedrockPart.java:91-113`）**只在 `this.visible` 时递归 children** ⇒ `super.render` 会整棵跳过 `scope_body` 子树。`super.render` 前**不受模板裁剪**的可见节点已逐资产扫过（见 15.3），默认包 scope 里只有 `scope_qmk152` 的 `bone(32)`。 |
| (b) 绿色来自 lens shader 的 **alpha/通道错位**，或 `LensCenter/LensRadius` uniform 与几何不匹配 | **均不成立**。`drawLens`（`ScopeLensRenderer.java:1136-1224`）的 uniform 与上游 `drawLens:236-263` **逐行同构**：`LensCenter/LensRadius` = `Bounds`（上游 `:237-238` 同样如此，只用于 shader 的暗角/畸变 UV，不参与几何）、`LensTint` 公式相同、`VisionMode` 用 `ordinal()` 且枚举顺序与 `scope_lens.fsh` 一致、`Sampler0/1/2` 绑定相同。快照拷贝是 `glCopyTexSubImage2D`（`ScopeLensRenderer.java:848`）RGBA8→RGBA8，与上游 `:230` 相同 ⇒ 无通道/格式错位。**绿色只可能来自「快照内容」或「lens config（tint/vision）」**，两者本轮都加了只读日志。 |
| (c) 黑镜 | 先分两类：`capture skipped (bounds invalid)` ⇒ r7-fp 矩阵/pose 域；`stencilAtCenter=254 且 lensDrew=true` 仍黑 ⇒ 采样内容（snapshot 回退 / 快照被污染 / config）。 |

### 15.2 静态发现的真实污染机制（均为「上游同代码 + 同资产」路径，需实机日志确认）

**M1（最可疑，绿色/黑镜）：`scope_standard_8x` 的主目镜 `ocular_scope` 在 scope-only 路径被跳过**
- 资产：`scope_standard_8x_display.json` 只有 `"scope": true`（无 `"sight"`），geo 唯一主目镜是
  `ocular_scope`(8 cubes) + `ocular_sight_2`(1 cube)（`oculars` 子节点）。
- 渲染：`isScope=true, isSight=false` ⇒ `renderScope` → `renderOcularStencil(..., false, ...)`
  （`BedrockAttachmentModel.java:1044`）。`renderOcularStencil`（`:390-434`）只画
  `isScope == isScopeOcular.get(i)` 的 ocular ⇒ **`ocular_scope`（isScopeOcular=true）被跳过**，
  主目镜模板值恒 0（只有 `ocular_sight_2` 写了值 2）。
- 后果链：`scope_body` 用 `GL_EQUAL 0`（`:1047`）⇒ 模板 0 的目镜区域**照常通过** ⇒ 镜身被画进目镜区；
  `ocular_scope` 没被 `renderTempPart` 隐藏 ⇒ 末尾 disable stencil 后 `super.render`（`:1058`）**不裁剪画出**；
  `capture()` 仍会成功（bounds 有效）⇒ 登记 lens(stencil=254)，但 254 在模板缓冲里不存在 ⇒ 镜片 surface
  `GL_EQUAL 254` 无像素 ⇒ **镜片不画**，目镜区留下镜身/ocular_scope 纹理（绿/黑由资产纹理决定）。
- **上游核对**：`tmp/TACZ-1.20.1` 的 `scope_standard_8x_display.json` 同样只有 `"scope": true`，
  geo 同样有 `ocular_scope`，`renderScope` 同样传 `false` ⇒ **上游同路径同结果**（不是移植偏差）。
- 判读日志：新加的 `ocular stencil pass (isScope=false): wrote ocular indices [...]` 会显示
  `wrote indices [1:sight]`、`ocularCount=2` ⇒ 证实 M1。

**M2：`scope_qmk152` 的 `bone(32)` 根节点不受裁剪**
- `bone` 是根节点（不在 `scope_body` 子树）⇒ `renderScope` 末尾 disable stencil 后由 `super.render`
  **不裁剪**画出；若其投影覆盖目镜区域，会进快照。默认包 scope 里这是**唯一**这种资产（15.3 扫描结果）。
- 上游同样（同资产同代码）。

**M3：`ocular_ring` 在写模板之前用 `GL_ALWAYS` 画**（`:1038-1042`，上游同序）⇒ 若环几何盖进目镜内会进快照；
资产相关，上游同样。

**M4（正常，非污染）**：枪身 `BedrockGunModel` 用 `GL_GREATER 127` ⇒ 在 254 区域失败，不进镜内。

### 15.3 逐资产扫描结果（`super.render` 前不受模板裁剪的可见 cube）

方法：按 display 的 `scope`/`sight` 决定 renderScope/renderBoth/renderSight，扣除该路径会隐藏的节点
（`scope_body`、`ocular_ring`、本路径绘制的 `ocular*`、`division*` 子树）后统计根节点 cube 数。

| 资产 | 路径 | 主目镜节点 | 未裁剪可见 cube | 备注 |
|---|---|---|---|---|
| `scope_standard_8x` | renderScope | `ocular_scope`(8) | `ocular_scope`(8) 未隐藏 + 镜身画进目镜区 | **M1**；绿色/黑镜头号嫌疑 |
| `scope_qmk152` | renderScope | `ocular` | `bone(32)` | **M2** |
| `scope_amg_10` / `scope_hamr` / `scope_mk5hd` / `scope_vudu` / `scope_xm157` | renderBoth | `ocular_sight` / `ocular_scope` | 0 | 两遍 `renderOcularStencil` 覆盖全部 ocular |
| 其它 scope | renderScope | `ocular` | 0 | 正常 |
| `sight_p90` / `sight_acro_pistol` | renderSight | — | `default_sight(22)` / `bb_main(1)` | 无镜片 pass，不影响 |

（`lens`/`lens_illuminated` 节点父链全部为 `…<- scope_body` 或 `…<- scope_body` 的子树；
`scope_mk4ert`/`scope_mk5hd` 的 `main`、`scope_xm157` 的 `group37`、`scope_lpvo_1_6` 的 `hide_when_ads`
都在 `scope_body` 子树内 ⇒ 随父隐藏。`scope_lpvo_1_6` 的 `lens` 是 0 cube。）

### 15.4 本轮新增只读诊断（不改变行为）

| # | 日志 | 位置 | 判读 |
|---|---|---|---|
| 1 | `TACZ: ocular stencil pass (isScope={}): wrote ocular indices [i:scope/sight ...]; ocularCount=N, isScopeOcular=[...]` | `BedrockAttachmentModel.renderOcularStencil`（`:390-434`，每模型一次） | 若 `ocularCount>1` 但 wrote 的下标缺主目镜 ⇒ M1 |
| 2 | `TACZ: scope unclipped parts before super.render (renderScope/renderSight/renderBoth): [name(cubes) ...]` | `BedrockAttachmentModel.logUnclippedPartsOnce`（`:334-355`，每模型一次） | 非空 ⇒ 该资产有不受裁剪几何（M2/M1） |
| 3 | `TACZ: scope lens snapshot: captureTargetOwnColorTexture={} (textureId=...), fallbackCopyTextureId=..., size=..., glError=...` | `ScopeLensRenderer.drawLensWithStencil`（`:797-808`，一次） | `false` ⇒ 采到的是手部前旧快照（镜中内容必错） |
| 4 | `TACZ: scope lens config: tint=#RRGGBB, tintStrength=..., vision=... (raw=...), thermalEnabled=..., thermalReady=..., distortion=..., edgeWidth=..., vignette=.../...` | `ScopeLensRenderer.drawLens`（`:1193`，一次） | 若绿色资产 tint 为绿色（如 `scope_mk4ert` `#D8FCC6`）或 vision=NIGHT_VISION_GREEN ⇒ 绿色来自 config |
| 5 | 已有：`scope capture matrices`（`:473`）、`scope ocular pass`（`BedrockAttachmentModel:616`）、`stencil probe before draw`（`:909`）、`scope lens surface (view-space circle, NDC)`（`:709`） | — | 断言 A/B/C + 镜片圆心/半径 |

### 15.5 等 r7-fp 修复后的判读流程（黑镜 / 绿色）

1. 看 `scope capture matrices`：`modelViewIsIdentity=false` 或 `proj.m11 != expectedF` ⇒ r7-fp（matrix/pose），本域不动。
2. 看 `scope ocular pass` + `ocular stencil pass`：
   - `stencilAtCenter`（`stencil probe before draw`）应 = `lens.stencil`（254）。若 ≠254 且资产是
     `scope_standard_8x`/`scope_qmk152` ⇒ 15.2 的 M1/M2。
3. 看 `scope lens snapshot`：`captureTargetOwnColorTexture=false` ⇒ 采样源错（镜中内容必错）。
4. 看 `scope lens config`：绿色若与 tint/vision 匹配 ⇒ config 问题；否则 ⇒ 快照内容被污染（M1/M2/M3）。
5. 看 `scope unclipped parts`：非空且投影覆盖目镜 ⇒ 污染源。

### 15.6 下一步（等 Lead/r7-fp）

- **r7-fp 修完 modelView 后复测**：按 15.5 判读。
- 若确认 M1/M2：这是「上游同代码 + 同资产」的结果，**严格上游等价 = 保留同结果**；若要修，属于
  「按上游意图修正资产/路径」的决策（例如 `renderScope` 对 `ocular_scope` 也写模板，或给
  `scope_standard_8x_display.json` 补 `"sight": true`）——涉及 `src/main/resources/**` 或跨域语义，
  需 Lead 决策/批准，我不擅自改。
- 若确认 `snapshotOk=false` / tint/vision ⇒ 采样/config 问题，我按结论在 scope 代码内修（仅只读日志→行为改动需 Lead 同意后做）。
---

## 16. 复核⑨：断言日志改「状态翻转时打一条」（只改日志，不改行为）

r7-fp 已修复「手部 pass 的 GL_MODELVIEW 恒为单位矩阵」（受伤/bob/`BeforeRenderHandEvent` 相机动画全部改写到枪模
PoseStack；等价性：修复前 `GL=H, 顶点=P·v` → 修复后 `PoseStack=H·P, GL=I`，最终 `H·P·v` 相同）。
本轮按 Lead 要求把矩阵断言从「整局只打一条」改成「**状态翻转时打一条**」，并保留复核⑧的全部只读诊断。

### 16.1 改动（只改日志）

| 文件:位置 | 改动 |
|---|---|
| `ScopeLensRenderer.capture`（`:463-497`） | `captureAssertionLogged`（整局一次）→ `lastCaptureModelViewIdentity`（`@Nullable Boolean`，上次状态）。每次 `capture()` 计算 `isIdentityMatrix(modelView)`，**首次取值**（`<first>`）与**每次 true↔false 翻转**都打一条：`TACZ: scope capture matrices (modelViewIsIdentity FLIP <prev> -> <cur>): proj.m00=..., proj.m11=..., expectedF=..., (ITEM_MODEL_FOV=...)`。这样「第一次 false」与「恢复正常」都能在日志里看到。 |
| `BedrockAttachmentModel.renderOcularAndDivision`（`:490-510`） | 新增 `lastOcularModelViewIdentity`（`@Nullable Boolean`）+ **每帧**计算 `isIdentityMatrix(GL_MODELVIEW_MATRIX)`；翻转时打 `TACZ: scope ocular pass modelViewIsIdentity FLIP <prev> -> <cur>: attachment=..., aim=..., proj.m00=..., proj.m11=...`。原「每模型一条」的 `scope ocular pass (stencil)` 保留（仍含 `modelViewIsIdentity`）。 |

保留的复核⑧只读诊断（下一轮实机判读用）：
`ocular stencil pass (isScope=…)`（M1）、`scope unclipped parts before super.render`（M2/M1）、
`scope lens snapshot: captureTargetOwnColorTexture=…`（采样源）、`scope lens config: tint/vision/…`（绿色是否 config）。

### 16.2 实机判读

- 正常（r7-fp 修复后）：两处 FLIP 日志应各只有 **一条** `<first> -> true`，之后不再出现。
- 若出现 `-> false`：说明又有东西把变换写进 `GL_MODELVIEW`（受伤/拖尾/其他模组）——属 r7-fp 域，把日志发 Lead。
- 若 FLIP 日志不出现 `true` 或 `expectedF` 不匹配：手部投影/FOV 取值问题（r7-fp）。

`lint_files(error)=0`；未跑 gradle / 未用 mcctl；本轮**未改任何绘制行为**。
---

## 17. 复核⑩：改为「稳定开镜帧」诊断（只改日志，不改行为）

实机日志显示 r7-fp 的模型矩阵修复已生效（`FLIP <first> -> true`、`modelViewIsIdentity=true`、无
`capture skipped behindCamera`、`captureTargetOwnColorTexture=true`），但用户反馈「问题依旧」。
根因之一是**现有 probe 全在开镜过渡首帧触发**（aim 0.53~0.60，视空间圆在屏幕外/半径小是正常的），
无法判断完全开镜后的实际结果。本轮把诊断改为「**稳定开镜帧**」采样，并补充模板/快照/uniform 观测。

### 17.1 改动（只改日志）

| # | 日志 | 触发条件 | 字段/判读 |
|---|---|---|---|
| 1 | `TACZ: scope lens stencil probe (before draw, stable ADS): pixel=(x,y), colorBefore=0x..., snapshotCenterRgb=#RRGGBB, stencilAtCenter=..., stencilProbe5=[c,u0.1,d0.1,u0.25,d0.25], stencilRef=..., surfaceViewSpace=(c,r), surfaceNdc=(center,radius), modelViewIsIdentity=..., fboStatus=..., aim=...` | 每个 lens **首次** `currentAimProgress() >= 0.99` 帧（按 `lens.stencil` 去重） | 稳定帧模板值 + 多点采样 + 快照中心色 + 视空间圆 NDC |
| 2 | `TACZ: scope lens stencil probe (after draw): pixel=..., sampleBefore=..., sampleAfterSurface=..., sampleAfterReticle=..., lensDrew=..., reticleDrew=..., glError=0x...` | 同上（同一稳定帧，镜片画完 / reticle 画完后各采样一次） | 镜片/reticle 是否真的写进主画面 |
| 3 | `TACZ: scope lens pass stats (stable ADS): lensCount=..., stencil=..., stencilValid=..., reticles=..., snapshotIsCaptureTargetColorTexture=..., aim=...` | 每个 lens 首次稳定帧 | lens pass 统计；`snapshotIsCaptureTargetColorTexture=false` ⇒ 采样源错 |
| 4 | `TACZ: scope lens uniforms (stable ADS): ScreenSize=..., LensCenter=..., LensRadius=..., LensTint=(r,g,b), VisionMode=..., ThermalReady=..., ContourReady=0, Distortion=..., Aberration=..., EdgeWidth=..., Vignette=.../..., aim=...` | 一次性（首次 aim>=0.99 的 drawLens） | 若 1-3 全部正常但仍黑/绿 ⇒ shader 采样/输出问题，用这条判读 uniform |

**多点模板采样说明**：`stencilProbe5` = 视空间圆心 + `(0, +0.1)` / `(0, -0.1)` / `(0, +0.25)` / `(0, -0.25)` NDC
（5 点 = 中心 + 上下各两档）。期望：镜片中心 = **254**（INVERT 后的值），圆半径 NDC_y≈2.2~2.8 时
**5 点全部 = 254**（镜片覆盖整个采样区）。

**快照内容采样说明**：`snapshotCenterRgb` 在 lens pass 里、**画 lens 之前**用 `glReadPixels` 读主画面
（此时 capture target 的 `COLOR_ATTACHMENT0` 挂着主颜色纹理）在视空间圆心的 3x3 平均色。
- 天空/草地/地形色 ⇒ 快照里是**世界**（正确）；
- 纯绿/纯黑/纯单色 ⇒ 快照被枪模几何污染（配合 `scope unclipped parts` / `ocular stencil pass` 判读）。

### 17.2 实机判读流程

1. 先看 `scope lens stencil probe (before draw, stable ADS)`：
   - `stencilAtCenter = 254` 且 `stencilProbe5` 全 254 ⇒ 模板正确；
   - 否则 ⇒ INVERT 圆/模板问题（M1 `ocular_scope` 跳过 / M2 `bone(32)` 不裁剪，见 §15）。
2. 看 `snapshotCenterRgb`：
   - 世界色（地形/天空/草地）⇒ 快照内容正确；
   - 纯绿/纯黑/纯色 ⇒ 快照被污染 ⇒ 看 `scope unclipped parts`（哪个节点）+ `ocular stencil pass`（主目镜是否写模板）。
3. 看 `after draw` 的 `lensDrew/reticleDrew`：
   - `lensDrew=true` 但画面仍黑/绿 ⇒ 问题在 shader 采样/输出 ⇒ 看 `scope lens uniforms`。
   - `lensDrew=false` 且 `stencilAtCenter=254` ⇒ 镜片 surface 没画出来（几何/矩阵/深度）⇒ 看 `surfaceNdc` 与 uniform。
4. `scope lens pass stats` 的 `snapshotIsCaptureTargetColorTexture=false` ⇒ 采样源是回退旧快照（内容必错）。

### 17.3 保留的诊断

复核⑧：`ocular stencil pass (isScope=…)`、`scope unclipped parts before super.render`、
`scope lens snapshot: captureTargetOwnColorTexture=…`、`scope lens config: tint/vision/…`；
复核⑨：`scope capture matrices (modelViewIsIdentity FLIP …)`、`scope ocular pass modelViewIsIdentity FLIP …`；
spec §8.1：`scope lens surface (view-space circle, NDC)`、`scope ocular pass (stencil)`。

`lint_files(error)=0`；未跑 gradle / 未用 mcctl；本轮**未改任何绘制行为**。
---

## 18. 复核⑪：镜片快照「自拷贝」根因与热修（Lead 定位；本轮仅核对 + 扫描）

### 18.1 根因

`ScopeLensRenderer.drawLensWithStencil` 在把**主颜色纹理**挂到 capture target 时，原实现用的是
`glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, ...)`（只挂 DRAW）。而紧接着的
`copyMainColorIntoCaptureSnapshot` 用 `glCopyTexSubImage2D`：

- `glCopyTexSubImage2D` 的**源** = 当前 **READ** framebuffer 的颜色附件；
- 只挂 DRAW ⇒ READ 侧仍是 capture target **自己的颜色纹理**（`dummyColorTexture`）；
- 而拷贝**目标**恰好也是绑在 unit0 的同一张 `dummyColorTexture`
  ⇒ **同一张纹理自拷贝**，内容未定义（残帧 / 垃圾 / 黑）。

镜片 shader 的 `Sampler0/Sampler2` 采这份快照 ⇒ 输出 = `LensTint × 垃圾`，实机表现为镜内
**深蓝 / 纯黑 / 绿单色**。

**证据（截图）**：`docs/review/r7/screenshots/g_mp5_scope_navy.png`（深蓝）、
`h_awm_scope_black2.png`（纯黑）、`i_aug_scope_green.png`（绿色单色）。

### 18.2 代码 diff（热修前 → 后）

| 位置（当前行号） | 热修前 | 热修后 |
|---|---|---|
| `ScopeLensRenderer.java:790-791` `drawLensWithStencil` 挂主颜色 | `glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, COLOR_ATTACHMENT0, …, mainColorTexture)` | **`GL_FRAMEBUFFER`**（READ+DRAW 都挂） |
| `:856-857` 同方法 `finally` 还原 dummy | `glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, …)` | **`GL_FRAMEBUFFER`**（与挂载对称） |
| `:1401-1402` `drawReticle` 挂主颜色 | `GL_DRAW_FRAMEBUFFER` | **`GL_FRAMEBUFFER`**（与上游一致） |
| `:872-875` `copyMainColorIntoCaptureSnapshot` javadoc | 未写前提 | 补「前提：必须挂 `GL_FRAMEBUFFER`，否则自拷贝」 |

### 18.3 上游对照（`tmp/TACZ-1.20.1/.../ScopeLensRenderer.java`）

| 上游 | 语义 |
|---|---|
| `render:178` `target.bindWrite(false)` | `RenderTarget.bindWrite` → `GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, …)`，**READ+DRAW 都指向 capture target** |
| `render:180-181` `GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, main.getColorTextureId(), 0)` | 主颜色纹理挂 **GL_FRAMEBUFFER**（READ 侧也是主颜色） |
| `render:212-213` finally `_glFramebufferTexture2D(GL_FRAMEBUFFER, …, target.getColorTextureId())` | 用 **GL_FRAMEBUFFER** 还原 capture 自己的颜色纹理 |
| `drawLens:226-231` `_glCopyTexSubImage2D(...)` | 源 = READ 颜色附件 = **主颜色纹理**（因 :180 已挂 READ+DRAW），目标 = capture 自己的颜色纹理 ⇒ 无自拷贝 |
| `render:196-209` reticle 循环 `target.bindWrite(false)`（在 `draw.type.setupRenderState()` **之后**） | 保证 RenderType setup 后仍画回 capture target |

**结论**：上游 `_glFramebufferTexture2D(GL_FRAMEBUFFER, …)` 在 1.7.10 手工重写 FBO 绑定时被写成
`glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, …)` ⇒ 等价性偏差。热修后与上游 `:178-214` 一致。

### 18.4 为何只影响「镜片内容」，reticle/准星可能仍正确

- **reticle（division）不采样快照**：`drawReticle` 直接把顶点画到 capture target（主颜色纹理挂着，像素写回主画面），
  不读 `dummyColorTexture` ⇒ 准星/刻度可以是对的（截图里正是「准星正常、镜内黑/单色」）。
- **INVERT 圆 / 目镜遮罩**只写模板或主画面颜色，不读快照 ⇒ 不受影响。
- 只有**镜片 surface 的 shader** 采 `Sampler0/Sampler2`（= 快照）⇒ 只有镜片内容错。

### 18.5 全仓扫描：是否还有同类挂载点（**无**）

范围：`client/renderer/scope/**` + `client/model/BedrockAttachmentModel.java`（并顺带扫全仓 FBO 相关 API）。

| 检查项 | 结果 |
|---|---|
| `glFramebufferTexture2D` 的 target 参数 | 全部为 `GL_FRAMEBUFFER`：`ScopeLensRenderer:790/856/1401/1972`、`ScopeThermalRenderer:676/729/848/957/997` ⇒ **无 DRAW-only 挂载点** |
| `glCopyTexSubImage2D` | 仅 2 处：① 镜片快照（前提已修正）；② `copyScreen`（`main.bindFramebuffer(false)` = `GL_FRAMEBUFFER`，源 = 主画面、目标 = `copyTexture`，**不同纹理** ⇒ 无自拷贝） |
| `glBlitFramebuffer` | 仅 2 处：① `captureStencil` 模板 blit（READ = 主 FBO、DRAW = capture target，与上游 `capture:106-110` 同向）；② `ScopeThermalRenderer` 场景深度 blit（READ = 主 FBO、DRAW = thermal FBO，方向正确） |
| `ScopeThermalRenderer` 对 capture target 的用法 | **不挂主颜色纹理到自己的 FBO**（场景渲染到自己的 color texture；深度 blit 方向正确）；其 FBO 创建 / `attachSceneDepth` 全部 `GL_FRAMEBUFFER` ⇒ 无同类问题 |
| `MainFramebufferStencil` | 只把 depth-stencil renderbuffer 挂到**主 FBO**（`GL_FRAMEBUFFER`），无颜色挂载/拷贝 ⇒ 无同类问题 |
| `ScopeLensContour` | 平台降级 stub（`prepare` 恒 null、`ContourReady=0`），无 FBO 操作 |
| `BedrockAttachmentModel.java` | 无任何 FBO 调用（只调 `ScopeLensRenderer.capture(...)`） |

**次要不一致（非同类 bug、1.7.10 平台等价，保留注释即可）**：

1. `drawReticle` 的绑定时序：上游在 `draw.type.setupRenderState()` **之后** `target.bindWrite(false)`
   （`render:196-200`），我们在其**之前**绑定并挂色（`:1400-1406`）；1.7.10 mcshim 的
   `RenderType#setupRenderState()` 不改 FBO 绑定 ⇒ 行为等价。
2. 我们对每个 `Draw` 都重绑 + 重挂一次主颜色（上游循环外挂一次、循环后还原）⇒ 等价，仅多几次 GL 调用。
3. `drawLensWithStencil` 的完整性检查用 `glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)`：热修后
   `GL_FRAMEBUFFER` 已同时挂 READ+DRAW，检查 DRAW 仍有效（仅查询目标不同，不影响结果）。

**结论：没有其它「只挂 DRAW」的同类挂载点**；复核⑪热修与上游 `ScopeLensRenderer:178-214` 一致。
本轮无代码改动（仅核对 + 本报告）；`lint_files(error)=0`；未跑 gradle / 未用 mcctl。

---

## 19. 复核⑫：Elcan 4x 黑镜根因（1.7.10 固定管线 alpha test / 纹理状态）与修复

### 19.1 症状与已排除项

- **症状**：Elcan 4x 装在任何枪上，完全开镜时镜片纯黑 / 通透无内容、镜内准星不出现；腰射镜片由「黑玻璃」变为「通透漏世界」；
  其它瞄具（AUG / HK / APM4s / XL50 等）正常。
- **排除**：`stencilFunc=0x202(EQUAL) ref=1 writeMask=0xff stencilTest=true glError=0` 均正确；
  `glClear(GL_STENCIL_BUFFER_BIT)` 能写模板，而 INVERT 圆连 A/B（关深度 / 关裁剪 / `func=ALWAYS` / `op=REPLACE` /
  identity 矩阵全屏四边形 / `glBegin` 立即模式）**全部写不进** ⇒ 不是模板开关问题，而是**片元在写模板前被丢弃**。

### 19.2 根因

- 上游 1.20 的 INVERT 圆是 **shader 管线**下的无纹理彩色几何（`BedrockAttachmentModel.java:284-301`，
  `POSITION_COLOR` + `BufferUploader.drawWithShader`），1.20 **没有**固定管线 `GL_TEXTURE_2D` / alpha test 状态。
- 1.7.10 固定管线里，`renderOcularStencil` / 模型批次刚设置过 `GL_TEXTURE_2D` + alpha test（`RenderType` 的 cutout 状态）；
  INVERT 圆没有纹理坐标，Tessellator/BufferUploader 沿用**上一批遗留的纹理坐标**采样上一张纹理；
  当采样点 alpha 低于 `glAlphaFunc` 阈值时，alpha test 判定失败 ⇒ **整批片元在写模板前被丢弃**。
  Elcan 贴图的遗留采样点 alpha 恰好很低，AUG 等资产侥幸通过 —— 这解释了「只有 Elcan 坏」。
- 结论：这是 **1.20（无固定管线）vs 1.7.10（固定管线）的平台差异**，不是上游实现错误。

### 19.3 修复（`client/model/BedrockAttachmentModel.java`）

- 新增 `drawInvertCircleNeutralizingFixedFunctionState(...)`：画 INVERT 圆前临时 `glDisable(GL_TEXTURE_2D)` +
  `glDisable(GL_ALPHA_TEST)`，画完按原状态恢复。几何 / 矩阵 / 模板语义完全不变（仍是视空间圆
  `(16*90*cx, 16*90*cy, -90)`、半径 `80 * modifier * progress`）。
- 同时删除本轮排查用的临时诊断（`debugInvertAgainstStencil`、GL 矩阵读取、模板读回、ADS 对齐 diag、
  `INVERT_FAILED` 降级）；INVERT 失败不再跳过目镜遮罩（回到上游行为）；其余 `TACZ:` 诊断日志降级为 `LOGGER.debug`。

### 19.4 现场证据（IDEA 调试器 + 实机日志）

- 修复前（断点现场读值）：`stencilFunc=0x202(EQUAL) ref=1` 正确，但三种绘制通道都写不进模板；`glClear` 可写。
- 修复后（断点现场读值）：`verifyBefore=1` → `verifyAfter=254`；实机日志：
  `invert verify: attachment=item.tacz.attachment, aim=0.99, pixel=(1280,688), stencil=1->254, wrote=true`、
  `scope lens stencil probe (before draw, stable ADS): stencilAtCenter=254, stencilProbe5=[254,...]`、
  `(after draw): lensDrew=true, reticleDrew=true`。

### 19.5 回归（实机，2026-11-06）

- **Elcan 4x**（AWP / M4A1 等）：开镜 = 镜片世界画面 + 镜内准星/刻度；腰射 = 黑玻璃；改装界面正常。
- **AUG 倍镜、XL50、HK MP5A5、APM4s、HAMR**（`scope_view_2`, `viewIndex=1`）均正常；红点/机瞄不受影响。
- 截图：`screenshots/j_elcan_ads_ok.png`（Elcan 开镜）、`k_elcan_hipfire_black.png`（腰射黑玻璃）、
  `l_aug_scope_ok.png`（AUG 开镜对照）。

### 19.6 上游等价性

- 未改动任何「视空间圆的顶点/半径/矩阵」与「模板值 → 像素」映射；仅补 1.7.10 固定管线状态清理
  （`scope-upstream-spec.md` §9 白名单 ⑪）。
