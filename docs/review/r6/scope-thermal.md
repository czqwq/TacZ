# r6-B · 瞄具「大黑框」+ 热成像不生效

状态：**代码已改完（lint 0 error，未跑 gradle）**，待实机复验。
实机证据：`docs/review/r6/screenshots/b_awm_fmp13_thermal.png`、`c_scope_orange_dot.png`、
`d_reddot_or_reapir.png`；日志：`run/client/logs/fml-client-latest.log`（2026-11-05 13:01–13:13 那次测试）。

环境事实（从实机日志确认）：

- 运行时是 **lwjgl3ify 3.0.33 + LWJGL 3.4.2**（不是老 LWJGL2），JVM flags 只有 `-Xms1G -Xmx6G`，
  **没有** `-Dforge.forceDisplayStencil=true`；
- 没装 Angelica/Iris；
- 因此 `ForgeHooksClient.stencilBits = 0`（`build/rfg/.../ForgeHooksClient.java:317-341`）⇒ 主 Framebuffer 深度是
  `GL_DEPTH_COMPONENT24`、**没有 stencil 附件**（`build/rfg/.../client/shader/Framebuffer.java:117-130`）。
  GL 规范：无 stencil 附件时模板测试恒通过、模板写入被丢弃。
- 日志里 FMP13 / APM4s / REAP-IR 三个配件都是 `hasIdx=true model=true views=1 scopePos=true`（数据正常，问题在渲染）；
  该次会话没有任何 TACZ WARN/ERROR。

---

## 一、现象与判读（截图）

| 截图 | 瞄具 | 现象 |
| --- | --- | --- |
| `b` 2088×966 | AWM + FMP13（`type=scope`，`scope_lens.vision_mode=THERMAL_IRONBOW`） | 屏幕中央偏右上一大块轴对齐黑色矩形（≈25%×40% 屏），黑框中间有个小矩形窗口，窗口里是「12:57 + 电池图标 + 紫色屏幕 + 绿色圆形准星」 |
| `c` 860×673 | 近景瞄具（橙色发光块 = 该模型 `_uv` 贴图左上角的橙色自发光像素） | 一块**倾斜**的黑色多边形压在镜体后面，跟着枪模透视走 |
| `d` 2560×1400 | REAP-IR（`type=scope`，`vision_mode=THERMAL_WHITE_HOT`） | 黑框最大（≈46%×62% 屏），中间小矩形窗口**直接透出正常世界**，黑框上有红色虚线 |

关键判读：

1. `b`/`d` 黑框是**轴对齐屏幕矩形**、位置不随视角（只随瞄具/姿态）变化；
2. `d` 透出的小窗口里能看到**已经画好的镜体/枪管**（不是只看到世界）⇒ 黑框是在枪模之后才画的；
3. `b` 窗口里的「显示器 UI + 紫屏 + 绿准星」= 镜片区域**底下本来就有的模型/镜片内容**，黑框把周围全盖住了。

⇒ 黑框不是模型几何没被裁剪，而是**一个被延迟到「手部渲染全部结束之后」才画的 3D 面**被当成屏幕坐标画了出来。
这与代码完全对得上（见下节）。

## 二、根因（按重要性）

### R1【主因】reticle（`division` 分割面）延迟绘制时丢了 per-lens 投影矩阵 —— 大黑框就是它

- 上游流程：`BedrockAttachmentModel` 在 `renderOcularAndDivision()` 里把 `division` 节点通过
  `ScopeLensRenderer.capture(...)` **录下来不画**（上游 `BedrockAttachmentModel.java:271-341`），
  由 `ScopeLensRenderer.render()` 在本帧末尾补画；补画前上游会**逐 lens 还原 capture 时刻的
  `projection` / `modelView`**（上游 `ScopeLensRenderer.java:117、184-187`），因为 reticle 顶点是 capture 时
  已经烘进相机空间的坐标。
- 本移植：`capture()` 只存了包围盒，`render()` 把 `GL_PROJECTION`/`GL_MODELVIEW` 都置成 identity 就画
  reticle（修改前 `ScopeLensRenderer.java:302-368`：`glLoadIdentity` 后直接 `drawLens(); drawReticle();`）
  ⇒ reticle 的**相机空间坐标被当成 NDC**：一块按模型坐标（几格大）的面被放大成几 NDC 单位，
  正好是一块贴着屏幕、位置随瞄具偏的巨型黑矩形。
- 与截图逐项吻合：
  - 轴对齐：identity 投影下顶点直接映射 NDC；
  - `d` 的「洞」：REAP-IR 的 `division` 贴图 UV 区域本身就是「黑底 + 中心一个透明白色小方块」
    （`scope_reap_ir_geo.json:4719-4731`，uv `[0,82] size 2.5×2.5`，在 256² 贴图上即黑底小洞）；
  - `d` 的红色虚线：REAP-IR 的 `division_illuminated` 是 `division` 的子节点（`scope_reap_ir_geo.json:4732-4807`），
    贴图是红色十字（UV `[5.5,76.5] 12.5×12.5`），capture 时被一起录进 reticle；
  - `b` 的「显示器 UI」：FMP13 的 `division` 贴图区域 `[5.5,28.5] 1.5×1.5` 为黑底小洞
    （`sight_infitac_fmp13_geo.json:846-857`），洞后面透出来的是模型自带的目镜显示面（`ocular_illuminated`）
    + `division_illuminated` 的绿色准星圆（`sight_infitac_fmp13_geo.json:818-845`）。

### R2【放大因素】无 stencil ⇒ 目镜黑遮罩 / 分割面全部不裁剪

- 本类所有 `GL_EQUAL` / `GL_INVERT` 掩膜都依赖主 framebuffer 的 stencil 附件（上游同款；
  上游 `capture()` 在无 stencil 附件时**直接返回 false**，上游 `ScopeLensRenderer.java:82-83`）。
- 1.7.10 默认没有 stencil（见环境事实）⇒ 模板测试恒通过 ⇒
  `BedrockAttachmentModel.renderOcularStencil()`（`BedrockAttachmentModel.java:287-313`）写入失效、
  `renderOcularAndDivision()` 的「渲染目镜黑色遮罩」（`:397-406`）把**完整**目镜面画出来。
  在没有 R1 的大黑框盖住时，这本身就是另一块黑色面（`c` 截图里压在镜体后的黑多边形即此类）。
- 本移植的 `capture()` 没有做上游这条 stencil 闸门，等于「无 stencil 也照跑延迟 pass」。

### R3 热成像「只有均匀调色板、没有热源高亮」

- 热掩膜几何来源：`ScopeThermalRenderer.wrap(MultiBufferSource, Entity)` —— **全仓 0 个调用点**
  （1.7.10 vanilla 实体渲染走 `Tessellator`/固定管线，没有 `MultiBufferSource` 可包）；
  `beginEntity()` 旧实现是「把实体再画一遍进 mask FBO」，但那次重绘同样不经过 mcshim
  `MultiBufferSource` ⇒ `ACTIVE` 恒空。
- 另外遮罩着色器 `MASK_VERTEX_SOURCE` 用 `uniform mat4 ProjMat/ModelViewMat`，而
  `render()` **从未设置这两个 uniform**（旧 `ScopeThermalRenderer.java:444-461` 只 `glLoadMatrix` 了固定管线矩阵）
  ⇒ `gl_Position = 0`，即使有顶点也画不出来。
- 两者叠加：`ThermalReady=1` 但掩膜贴图全 0 ⇒ `scope_lens.fsh` 的 `heat = 0.06 + luminance*0.24`
  只按场景亮度给 `ironbow` 上色 ⇒ 整片均匀紫/蓝、没有热源高亮（截图 `b` 的紫屏就是这个形态）。

## 三、上游 ↔ 本移植对照

| 机制 | 上游 1.20.1 | 本移植（修改前） | 本移植（修改后） |
| --- | --- | --- | --- |
| reticle 延迟绘制 | 每个 `Lens` 存 `projection`/`modelView`，`render()` 里 `setProjectionMatrix`+`applyModelViewMatrix` 后画（`ScopeLensRenderer.java:117、184-187、196-209`） | 只存包围盒，identity 矩阵下画 | **R1 已修**：`Lens` 存 capture 时刻 `GL_PROJECTION`/`GL_MODELVIEW`，`render()` 逐 lens 还原（`ScopeLensRenderer.java:284-324、396-423、654-700、835-855`） |
| 无 stencil 附件 | `capture()` 直接 `false`（`ScopeLensRenderer.java:82-83`），全部回退原版 | 照跑延迟 pass + 黑遮罩 | **R2 已修**：`capture()` 无 stencil 时只登记镜片圆盘、不登记 reticle；`BedrockAttachmentModel` 在无 stencil 时整组跳过模板遮罩/分割面（`BedrockAttachmentModel.java:337-347、472-549、592-670`） |
| 目镜黑遮罩 | 靠 stencil `GL_EQUAL` 只画圆环外 | 无 stencil 时整块画出 | 无 stencil 时**隐藏目镜遮罩节点**（`hideOcularMasks`，`BedrockAttachmentModel.java:488-495`） |
| 热源掩膜几何 | `EntityRenderDispatcherMixin` 的 `@WrapOperation` 换 `MultiBufferSource`，实体顶点顺路复制（上游 `EntityRenderDispatcherMixin.java:19-27`） | 「重绘实体」+ `wrap()`（无调用点）⇒ 恒空 | **R3 已修**：mixin 在 `func_147939_a` HEAD 把相机相对坐标交给 `ScopeThermalRenderer.beginEntity(entity,x,y,z)`，按实体 AABB 生成白色热源盒子（`EntityRenderDispatcherMixin.java:57-70`、`ScopeThermalRenderer.java:319-417`） |
| 遮罩着色器 | 顶点色 + 贴图 alpha cutout | 声明 `ProjMat/ModelViewMat` 但从不赋值；`Sampler0` 未绑定 | 显式灌矩阵（`ScopeThermalRenderer.java:584-592、886-895`）；片元固定输出白色（`MASK_FRAGMENT_SOURCE`，`:115-131`） |
| thermal 未就绪原因 | 静默 | 静默 | 一次性 WARN + `describeNotReady()`（`ScopeThermalRenderer.java:231-293`） |

## 四、已修（改动文件，均在 task-7 write scope 内；mixin 改动已获 Lead 事先授权）

1. `client/renderer/scope/ScopeLensRenderer.java`（+230 行）
   - `Lens` 增 `stencilValid` / `projection` / `modelView`（`:835-855`）；`capture()` 读 capture 时刻矩阵、
     无 stencil 时不录 reticle（`:284-324`）。
   - `render()` 逐 lens：先 identity 画圆盘，再**还原 capture 矩阵**画 reticle（`:396-423`）；
     新增 `readMatrices/loadIdentityMatrices/loadMatrices`（`:654-700`）。
   - 新增 `isStencilAvailable()`（Forge stencil bits，纯字段读，供模型侧降级判断，`:188-195`）。
   - 一次性诊断：无 stencil WARN（`init()`，`:160-170`）、镜片 pass ACTIVE INFO（`:386-395`）、
     首条圆盘 NDC 参数 INFO（`:397-409`）、`copyScreen` 后 GL error WARN（`:356-368`）、
     热成像瞄具但 `ThermalReady=0` WARN（`:526-535`，原因由 `ScopeThermalRenderer.describeNotReady` 给出）。
2. `client/model/BedrockAttachmentModel.java`（+129 行）
   - `renderScope` / `renderSight` / `renderBoth` 在 `!ScopeLensRenderer.isStencilAvailable()` 时走
     `renderWithoutStencil(...)`：隐藏目镜黑遮罩、跳过模板遮罩与 division，只画外环/镜身；
     scope（及 scope+sight 组合）仍登记镜片圆盘，纯 sight 不登记（上游 `renderSight` 也不跑镜头 pass）
     （`BedrockAttachmentModel.java:592-676`）。
   - `captureLensesWithoutStencil` / `currentLensRadius` / `warnNoStencilOnce`（`:504-549`；
     半径公式与上游 `BedrockAttachmentModel.java:279-283` 一致）。
3. `client/renderer/scope/ScopeThermalRenderer.java`（热掩膜从「恒空」改成可用）
   - `beginEntity(entity,x,y,z)`：按 `entity.boundingBox` + 相机相对坐标生成 6 面热源盒子，
     首条几何时抓 `GL_PROJECTION`/`GL_MODELVIEW`（`:319-417`）；
   - `render()`：显式设置 `ProjMat`/`ModelViewMat` uniform（`:584-592`、`:886-895`）；
     着色器片元固定输出白色（`:115-131`）；
   - 一次性诊断：GL<3.0 / 深度附件探测失败 / FBO 不完整 / FBO 创建失败 四类 WARN，成功打一条 ACTIVE INFO
     （`:480-620`）；`describeNotReady(width,height)` 说明未就绪原因（`:276-293`）。
4. `mixin/client/EntityRenderDispatcherMixin.java`（已登记在 `mixins.tacz.json` 的 `client` 数组，`:25`）
   - HEAD 钩子改为 `beginEntity(livingEntity, x, y, z)`（`:57-70`）；
   - RETURN 钩子保留（`endEntity()` 新实现为 no-op），类 javadoc 同步改写（`:14-55`）。

**保持不变**：`BedrockGunModel#renderUnaccelerated` 的 `GL_GREATER 127`（「镜内不画枪体」）仍是 r3 已登记的
无 stencil 失效项——它只影响「开组合镜时镜内会看到枪体」，不产生黑框，本轮不动（依据链见该类注释）。

## 五、未修 / 降级（明确记录，不是遗漏）

1. **无 stencil 时没有 reticle（准星/分割面）**：`division`（含 `division_illuminated` 子节点）本来靠模板裁剪
   才正确，未裁剪会盖满视野，因此默认环境下不画。代价：红点镜（APM4s）默认环境下看不到红点、
   FMP13/REAP-IR 看不到自带准星盘。**加 JVM 参数 `-Dforge.forceDisplayStencil=true` 可恢复**
   （此时 `stencilBits=8`，主 FBO 变成 `DEPTH24_STENCIL8`，完整路径生效，且 R1 的矩阵修正让 reticle 位置正确）。
   **单点回滚**：若实机觉得无 stencil 下的镜片圆盘位置/观感异常，把
   `ScopeLensRenderer.ALLOW_LENS_WITHOUT_STENCIL` 置 `false`（`ScopeLensRenderer.java:132-142`），
   即完全回退「无 stencil 时什么都不叠加」。
2. **热源轮廓从「精确剪影」降级为「包围盒」**：1.7.10 实体渲染没有可插入的顶点流（上游靠
   `MultiBufferSource` 包装）。盒子的位置/尺寸/遮挡（借用场景深度 + polygon offset）是对的，
   但形态是矩形而不是贴图剪影。后续若要精确剪影，需要拦截 `Tessellator` 顶点或用 Mixin 逐个
   `Render.doRender` 注入，属独立课题（`docs/ANGELICA_COMPAT.md` 同类问题）。
3. **thermal 调色板依赖热掩膜 pass 成功**：若日志出现 `thermal mask DISABLED: ...`，镜片会退化为
   「只有染色/暗角」的普通光学效果（这是可预期降级，不崩）。实机本轮日志没有该 WARN 之前的状态未知，
   下一次实机按 §六 判读。
4. `ScopeLensContour`（JFA 轮廓精修）仍恒 `null`（走上游椭圆兜底），与 r5 记录一致，本轮不动。

## 六、实机验证步骤与日志判读

### 步骤

1. 正常启动（**先不加** `-Dforge.forceDisplayStencil=true`），进创造世界，AWM 分别装
   FMP13 / REAP-IR / APM4s 各看一次：
   - 站立不瞄：枪模正常，**无黑块**（原来 `c` 那种斜黑块也不应再出现）；
   - 右键开镜：**不再出现大黑矩形**；如果镜片圆盘生效，应看到一块带光学效果（热成像镜还会带
     `ironbow`/`white-hot` 调色板）的圆形/椭圆画面，周围是世界。
2. 对着僵尸/动物（热源）开镜：**热源身上应出现过亮的热区（红/黄/白，取决于 vision_mode）**，
   被方块挡住的部分不热（借用场景深度）。
3. 关镜/切枪/断线重连各一次：不应崩（线程规则未改动）。
4. 想测完整模板路径：加 JVM 参数 `-Dforge.forceDisplayStencil=true` 再跑一轮，此时
   reticle/黑遮罩应恢复且位置正确（这是 R1 的回归验证点）。
5. 若仍异常，抓 `run/client/logs/fml-client-latest.log` 里 `TACZ:` 开头的行（下面判读表）。

### 日志判读表

| 日志行（前缀 `TACZ:`） | 含义 | 对应动作 |
| --- | --- | --- |
| `main framebuffer has NO stencil attachment (Forge stencilBits=0 ...)` | 默认降级路径（预期） | 正常；要完整效果加 `-Dforge.forceDisplayStencil=true` |
| `scope attachment stencil masks skipped: ...` | 模型侧已跳过目镜黑遮罩/division（预期） | 正常 |
| `scope lens pass ACTIVE: N lens(es), stencil=false, thermal=?, WxH` | 镜片圆盘确实在画 | `thermal=true` 才应有热成像调色板；`N=0` 说明 capture 被早退（看下面几条） |
| `scope lens disc (NDC): center=..., radius=..., vision=...` | 圆盘位置/尺寸 | `radius` 应在 (0,1]，`center` 在 [-1,1]；明显越界 ⇒ Bounds/matrix 问题，回传这一行 |
| `GL error 0x... right after scope lens copyScreen` | 屏幕拷贝失败 ⇒ 圆盘可能采到黑纹理 | 回传错误码；本轮新加，实机没出现即说明 copy 正常 |
| `thermal scope lens is active but the thermal mask is NOT ready (ThermalReady=0): <原因>` | 热成像镜已识别但掩膜没跑 | `<原因>` 直接给出：`not aiming` / `hasThermalScope=false` / GL 问题 / 尺寸不匹配 |
| `thermal mask pass ACTIVE: WxH, sceneDepth=..., maskBuffers=N` | 热掩膜 pass 成功 | `maskBuffers=N>0` 表示本帧确实录到了热源盒子（N 是格式桶数，不是实体数）；`N=0` 表示视野内无热源（正常）或 `beginEntity` 全被早退 |
| `thermal mask DISABLED: current GL context does not expose GL 3.0 ...` | 环境不支持（非 lwjgl3ify） | 可预期降级 |
| `thermal mask DISABLED: cannot resolve the main framebuffer's depth attachment ...` | 深度附件探测失败（会打印 `GL_FRAMEBUFFER_BINDING` / `main.depthBuffer`） | 回传该行；说明 framebuffer 结构异常（Angelica 等） |
| `thermal mask DISABLED: ... framebuffer incomplete after attaching the scene depth (status=...)` | 借深度失败 | 回传 `depthType/depthObject/status` |
| 完全没有 `TACZ: scope lens pass ACTIVE` | 镜片 pass 从未跑：`program==null`（看更早的 GLSL error）或 `LENSES` 空（半径/aiming 为 0） | 先确认是否真开镜、再看 `ScopeLensProgram` 的 GLSL 编译日志 |
| `Scope lens shader compile/link failed: ...`（ERROR） | 着色器编译失败 | 回传整段 info log；镜片会自动回退（不画） |

## 七、自查

- 修改文件仅限 task-7 write scope（scope 渲染 3 文件 + `BedrockAttachmentModel` + 授权后的 mixin）；
  `client/init/**`、`mcshim/**` 未改。
- `lint_files(min_severity=error)`：`ScopeLensRenderer` / `ScopeThermalRenderer` / `BedrockAttachmentModel` /
  `EntityRenderDispatcherMixin` 全部 **0 error**（剩余 warning 均为历史风格项，如 `getLast()` 建议、
  mixin 注入方法的未用形参）。
- **未运行 gradle**（按要求由主 Agent 统一构建）。
- 线程规则未改动：新代码仍只在渲染线程执行（`beginEntity` 只写 Java 顶点数组 + `glGetFloat`；
  `endEntity` no-op）。

---

# r6-2 · 第二轮（实机 e/f 回归 + Pulsar XL50 开镜）

第一轮修好后实机确认「大黑框消失」，但用户复测报两个新问题（截图
`docs/review/r6/screenshots/e_pulsar_xl50_ads.png`、`f_firstperson_missing_faces.png`）：

- `f`：第一人称镜筒变成「空壳」、大量面不显示（用户描述「面剔除有问题」）；
- `e`：Pulsar XL50 开镜后镜筒占满屏幕、里面是黑色 + 枪体内部件，看不见世界。

## 一、复查：`f`（空壳/缺面）的真实根因不是「漏 super.render」

Lead 先前的定位是 `renderWithoutStencil` 末尾漏了 `super.render(...)`。**复核后该假设不成立**：
`BedrockAttachmentModel#render(...)` 的外层（`:240`）在本帧**无条件**调用 `super.render(...)`，
所以 fallback 提前 return 不会少画「其余几何」。Lead 补上的那一行已按要求保留（与正常路径一致，opaque 上无副作用）。

真实根因：r6 第一轮的 fallback **主动隐藏了 `ocular`（目镜黑遮罩）和 `division`（分割面）**：

- 对 `scope_xl50_geo.json`，`division` 是一块 `80×60`（模型像素）的大平面（`:4719-4731`），物理上就是镜筒内部
  「封住筒口」的成像面；`ocular` 是 4 片目镜玻璃（`:4809-4847`）。隐藏后从侧面/后方看镜筒就是通的
  ⇒ 实机表现为「空壳 + 可见面消失」（截图 f）。
- 修法：**未开镜时把这两类面按模型空间正常画回来**（不再 hide），只把「开镜时才会挡视野」的隐藏逻辑
  收窄到 ADS 分支（见下一节）。见 `BedrockAttachmentModel.java:478-556`。

## 二、复查：`e`（XL50 开镜看不见世界）

两个叠加原因：

1. **镜片圆盘采到了镜身，而不是世界**：`ScopeLensRenderer.render()` 在「手部渲染之后」才
   `copyScreen`，此时准星区已经被（无 stencil 裁剪的）镜身/目镜画满，圆盘采到的是镜身
   （截图 e 里那块黑色内部结构）。1.20 上游不会这样，因为 stencil 把镜身挡在准星区之外。
   修法：新增 `ScopeLensRenderer#prepareWorldCopy()`，在 **`RenderWorldLastEvent`（世界画完、手部未画）**
   先把干净世界画面拷进 copyTexture（调用点 `ClientSetupEvent#onRenderWorldLast`，与热掩膜同一时机），
   镜片圆盘改用这份快照；没有预拷贝时仍回退「手部之后再拷」。
2. **无 stencil 时镜身/目镜遮罩必然盖住准星区**：修法是把降级渲染按开镜进度分两段
   （`BedrockAttachmentModel#renderWithoutStencil`）：
   - 未开镜（aim < 0.5）：完整模型（外环 + 镜身 + 目镜遮罩 + 分割面）⇒ 修 f；
   - 开镜（aim ≥ 0.5）：隐藏 `scope_body` 与 `ocular` 遮罩（否则整片盖住视野），只保留外环并登记镜片圆盘
     ⇒ 修 e。分割面/准星面在开镜时跳过（未裁剪会盖满视野，属已知降级，见 §四）。

### 关于「镜片圆盘 NDC 偏了」

实机日志 `scope lens disc (NDC): center=(0.3916, 0.2946) radius=(0.0530, 0.1165)`。
复核结论：`Lens` 的 `centerU/centerV/radiusU/radiusV` **本来就是**用 capture 时刻的
`GL_PROJECTION × GL_MODELVIEW` 把目镜几何投影后取 AABB 得到的（`ScopeLensRenderer.java:299-301、654-700、
Bounds:900+`），`capture()` 的 `centerX/centerY/radius` 形参从第一轮起就被忽略（上游签名遗留），
不是「估算常数」。该日志的偏位来自**触发时机**：这是整个会话的**第一次** capture，发生在开镜过渡段
（枪模还在从腰射位滑向 Aim 位），此时目镜本来就偏在屏幕一侧。

修法（诊断性）：把该日志改成**只在完全开镜（aimingProgress ≥ 0.99）时打一条**，并在 NDC 中心
超出 ±0.35 时 WARN（`ScopeLensRenderer.java:470-505`）。这样下一次实机的判据才成立：
**开镜正对时 center≈(0.5,0.5)（UV）/ (0,0)（NDC），radius 覆盖目镜内圈**。若那时仍偏，说明是模型/相机帧问题
（r4 已知项），不是 Bounds 数学问题。

## 三、thermal 深度：方案 a（`glBlitFramebuffer`）+ 兜底 b

实机日志：`sceneDepth=renderbuffer (type=36161, object=1)`、`thermal mask DISABLED: cannot resolve ...`。
1.7.10 主 framebuffer 的深度是 **renderbuffer**（`Framebuffer.java:108/117-124`），不能直接采样；
旧实现「查询附件类型 → 把主深度 renderbuffer 挂到自己的 FBO」在实机上探测失败（还偶发成功，导致
`thermal=false` 与 `thermal mask pass ACTIVE` 的日志自相矛盾）。

落地（`ScopeThermalRenderer.java`）：

1. `ensureTarget` 给本类 FBO 增加**自己的 depth texture**（默认 `GL_DEPTH_COMPONENT24`；
   `stencilBits>0` 时用 `GL_DEPTH24_STENCIL8` 以与主深度格式匹配）；
2. 每帧 `blitSceneDepth(main, w, h)`：保存读/写 FBO 绑定 → `glBindFramebuffer(READ=main, DRAW=mask)` →
   `glBlitFramebuffer(..., GL_DEPTH_BUFFER_BIT, GL_NEAREST)` → 恢复绑定。深度测试从此与场景严格对齐
   （**renderbuffer → texture 的深度 blit 是 GL3.0 核心能力**）；
3. blit 失败只 WARN 一次并继续（热源盒子不做遮挡，热成像调色板仍生效）；
   若 blit 不可用，才退回旧的「借用主深度附件」路径（`resolveSceneDepth`/`attachSceneDepth`），
   并保证在 finally 里摘掉借用附件、重挂自己的 depth texture（避免悬空附件）；
4. 遮罩顶点着色器的 `ProjMat/ModelViewMat` 现在显式赋值（第一轮已修），热源盒子为白色 `vec4(1.0)`，
   因此 `ThermalReady=1` 时 `scope_lens.fsh` 的 `ironbow/white-hot` 会按「场景亮度做冷背景 + 盒子做热目标」出图。

预期实机日志：`thermal mask pass ACTIVE: WxH, sceneDepth=blit-ok, maskBuffers=N` +
`scope lens pass ACTIVE: ... thermal=true`，并且热源（僵尸/动物）身上出现热色块、被方块挡住的部分不热。

若实机出现 `thermal mask scene-depth blit FAILED (GL error 0x...)`：说明该驱动的
renderbuffer→texture 深度 blit 不可用，此时会看到「热源隔墙可见」的降级（热成像本身仍生效），
请回传该行（含 `mainFramebuffer/main.depthBuffer`）。

## 四、Pulsar XL50「no adapter 槽位」核查结论（预期行为，不是移植缺陷）

对照上游 `FirstPersonRenderGunEvent.java:153-253` 与 1.7.10 移植版（逐行一致）：

- `mountOffset` 只在以下两种情况非 null：枪上装了**槽位适配器**（`SlotAdapterHelper#getEffectiveSlotAdapter`）
  且适配器 index 带 `mount_offset`，或瞄具有**变体**（variant）带 `mount_offset`
  （移植版同逻辑：`FirstPersonRenderGunEvent.java:491-514`）。
- AWM 的 `allow_attachments` 文件是 `tacz_tags/attachments/allow_attachments/ai_awp.json`，内容包含
  `#tacz:scope`（直连）和 `#tacz:adapter/pistol_riser`、`#tacz:adapter/rail_mid`；
  `scope_xl50` 属于 `tacz_tags/attachments/scope.json` ⇒ **AWM 直连安装 XL50，不需要 adapter**，
  因此 `mountOffset=null` 是正确值，不是缺陷。
- `scope_xl50_data.json` 的 `"ads": {"addend": 0.15}` 是**开镜时间**修正量
  （`AdsModifier`/`ads_addend`，被 `LocalPlayerAim`/`LivingEntityAim` 消费，见
  `LocalPlayerAim.java:127-134`），与 eye-relief/位置无关；
  `idle_scope_height_adjust: 0.75` 在 `FirstPersonRenderGunEvent.java:450` 被消费（腰射高度修正）。
- ⇒ 「no adapter 槽位」属玩家侧预期：该枪该镜**本来就不需要适配器**。若玩家想改变镜位高度，应在改装界面
  装 `rail_mid`/`pistol_riser` 之类的**槽位适配器**（`mount_offset` 只由它/瞄具变体提供）；
  若界面对某枪某镜确实提示需要 adapter，那是该枪 `allow_attachments` 只允许 adapter 直连的正常提示。

## 五、r6-2 改动文件与自查

| 文件 | 改动 |
| --- | --- |
| `client/renderer/scope/ScopeLensRenderer.java` | 新增 `prepareWorldCopy()`（世界渲染末尾的干净快照）；`render()` 优先用快照；圆盘诊断日志改为「完全开镜才打」+ 偏位 WARN；`offCenterWarned` 等 |
| `client/renderer/scope/ScopeThermalRenderer.java` | 新增 depth texture + `blitSceneDepth`（方案 a）；blit 失败兜底回旧借用路径；`depthBorrowed` 生命周期；`describeNotReady`/日志更新 |
| `client/model/BedrockAttachmentModel.java` | `renderWithoutStencil` 按开镜进度分两段：未开镜完整模型（修 f），开镜隐藏镜身/目镜遮罩（修 e）；Lead 补的 `super.render(...)` 保留 |
| `client/init/ClientSetupEvent.java` | `onRenderWorldLast` 先调 `ScopeLensRenderer.prepareWorldCopy()` 再渲染热掩膜（Lead 已预先授权 init 目录） |

- `lint_files(min_severity=error)`：以上 4 文件 **0 error**。
- **未运行 gradle**（由 Lead 统一构建）。
- 未改任何持枪位置/相机/枪模定位代码（用户明确要求；`FirstPersonRenderGunEvent` 未动）。

## 六、r6-2 实机验证步骤与新增日志判据

1. 默认启动（不加 stencil 参数）：
   - **未开镜**看 AWM+Pulsar XL50 / FMP13 / REAP-IR：镜体完整（不再空壳/缺面），枪械持枪位置不变；
   - **开镜**：镜身不再占满屏幕；屏幕中心出现一块椭圆形镜片画面（XL50 = 热成像 white-hot、FMP13 = ironbow、
     REAP-IR = white-hot），画面来自「世界渲染末尾」的干净快照 ⇒ 应能看到放大的世界画面；
   - 对僵尸/动物开热成像镜：热源身上出现热色块（`maskBuffers>0`），被方块挡住的部分不热。
2. 关键日志（`TACZ:` 前缀）：
   - `scope lens disc (NDC, full ADS): center=(...) radius=(...)` ⇒ 判据：**center≈(0,0)**，radius>0 且覆盖目镜内圈；
     若出现 `scope lens disc is far from screen center while fully aimed` ⇒ 回传整行（模型/相机帧问题）。
   - `thermal mask pass ACTIVE: WxH, sceneDepth=blit-ok, maskBuffers=N` ⇒ 深度 blit 成功；
     `sceneDepth=blit-FAILED (no occlusion)` ⇒ 驱动不支持深度 blit，热源隔墙可见（可接受降级，回传 WARN 行）。
   - `thermal mask scene-depth blit FAILED (GL error 0x...)` ⇒ 回传错误码与 `mainFramebuffer/main.depthBuffer`。
   - `scope lens pass ACTIVE: ... thermal=true` ⇒ 热成像调色板生效；`thermal=false` 时看紧随的
     `thermal scope lens is active but ... NOT ready: <原因>`。
3. 想验证完整模板路径：加 `-Dforge.forceDisplayStencil=true`，此时走正常 stencil 分支（reticle/遮罩/镜内不画枪体
   全部恢复），r6-1 的 per-lens 矩阵修复保证 reticle 位置正确。


---

# r6-3 · 第三轮（HAMR 组合镜开镜大黑框）

r6-2 修好后实机确认「XL50 开镜镜内能看到世界画面 ✓」，但 **`tacz:scope_hamr`（Trijicon HAMR，
`scope_hamr_display.json` 里 `"scope": true, "sight": true`）装在 `hk_mp5a5` 上开镜时复现大黑框**。
截图：`docs/review/r6/screenshots/g_hamr_ads_blackframe.png`。

## 一、根因（实机日志 + geo 数据双重锁定，不是「division 一直 visible」）

### 1) 黑框到底是什么

`scope_hamr_geo.json`：

- `divisions`（root）→ `division_2`（parent=divisions，无自身 cube）→ 子节点：
  - `division_bg`（37 个 cube 拼成的**中空「黑边框」**：其中大块为 64×128 的平面，`uv_size=1×1` = 纯单色，
    拼出一个圆形开口）——截图里的黑色矩形就是它，圆形开口就是截图里那个「看到世界的圆」；
  - `scope_division_illuminated`（29 cube，红十字/绿点/弹道刻度，z≈-96，在 `division_bg` 前面）——截图里
    圆内的准星图形就是它（后画，因此在黑底之上）。
- 这解释了截图里「黑框 + 圆内世界 + 圆内准星」同时出现的组合：**`division_2` 整棵子树被未裁剪地画出来了**。

### 2) 为什么只出现在 HAMR、且只在「开镜」时

`docs/review/r6/scope-thermal.md` r6-2 的降级逻辑是「`aimingProgress ≥ 0.5` 才隐藏镜身/目镜并登记镜片」，
**0 < progress < 0.5 的过渡段仍走「未开镜」分支**，而该分支会用 `renderMasksAndDivisionWithoutStencil`
把 division 子树（含 `division_bg` 大黑面）按模型空间**未裁剪**画出来。

实机日志（`run/client/logs/fml-client-latest.log`，18:07:09~18:07:11，HAMR 测试窗口）直接给出过渡段证据：

```
[18:07:09] [TACZ-DBG] scope gun=tacz:hk_mp5a5 ... scopeId=tacz:scope_hamr hasIdx=true model=true views=2 scopePos=true
[18:07:10] [TACZ-DBG] ads-enter aim=-0.00 raw=0.05 fov=60.00 pose=(0.172, 0.562, -0.936)
[18:07:10] [TACZ-DBG] ads aim=0.30 raw=0.46 fov=59.73 pose=(0.060, 0.563, -0.936)      ← 过渡段：division 被画出 = 黑框
[18:07:10] [TACZ-DBG] ads aim=0.97 raw=1.00 fov=43.47 pose=(0.003, 0.567, -0.860)      ← ≥0.5：应是 ADS 分支
[18:07:10] [TACZ-DBG] ads-full aim=1.00 raw=1.00 fov=41.01 pose=(0.004, 0.567, -0.860)
[18:07:10] [TACZ-DBG] ads aim=0.40 raw=0.20 fov=51.92 pose=(0.039, 0.563, -0.931)      ← 收镜：又回到过渡段 = 黑框
```

截图对应的就是 `aim=0.30 / 0.40` 这类过渡帧：黑框铺满屏幕、圆内还能看到世界和准星、镜身仍在（抬枪动画中）。
纯 scope（XL50）有同样的过渡窗口，但它的 `division` 只是 80×60 的平面且贴图大部分透明、面积小，
所以用户只在 HAMR 上注意到。

> 之前怀疑的「两次 attachment model 渲染」/「ctor 没把 division_2 置 hidden」都不成立：
> `BedrockAttachmentModel` 构造器对 `division`/`division_2` 都调了 `setHidden(true)`
> （`BedrockAttachmentModel.java:112-127`），且 `BedrockPart.render` 的 children 在 `if (this.visible)` 内
> （`bedrock/BedrockPart.java:91-113`）⇒ 平时确实不画；**只有过渡段被 `renderTempPart` 临时置
> `visible=true` 画出来后**才是黑框来源。

## 二、修法（r6-3）

`BedrockAttachmentModel#renderWithoutStencil` 改成按三段降级（`BedrockAttachmentModel.java:491-560`）：

1. **腰射**（未按 ADS 且 `aimingProgress ≈ 0`）：完整模型（外环 + 镜身 + 目镜遮罩 + division），
   修 f「空壳/缺面」；
2. **开镜过渡 / 收镜**（按着 ADS 或 `0 < progress < 0.5`）：保留外环/镜身/目镜遮罩（抬枪动画可见），
   但**绝不画 division 子树**（`hideDivisions()` 每帧强制 `visible=false`，避免可见性状态跨调用污染）；
3. **完全开镜**（`progress ≥ 0.5`）：隐藏镜身/目镜遮罩 + division，只保留外环 + 登记镜片圆盘，
   显示「世界渲染末尾」快照（r6-2 的 `prepareWorldCopy`）的（放大的/热成像的）世界画面。

「是否在开镜过程」的判据（`aiming`）：`IClientPlayerGunOperator#isAim()`（布尔，按下 ADS 即 true）
**或** `aimingProgress > 0.001`。这样 ADS 按下瞬间与收镜尾巴都覆盖到，过渡段不会再画黑框。

配套硬化（都在本轮）：

- `ScopeLensRenderer#isAimingNow()` 同样用于 `prepareWorldCopy()` 的开关，避免「已开镜但进度读数异常」
  时跳过预拷贝 → 镜片圆盘采到镜身后的画面；
- `captureLensesWithoutStencil()` 在 `isAimingNow()` 时用 `max(rad, 1.0F)` 作为 `capture()` 的「是否开镜」
  门槛（圆盘尺寸仍由 capture 时刻投影出的目镜几何决定），保证隐藏了镜身就一定有镜片画面；
- **腰射 division 尺寸闸门**：新增 `ScopeLensRenderer#projectUvBounds(...)`（与 `Bounds` 同一套
  `GL_PROJECTION × GL_MODELVIEW + PoseStack` 投影数学），腰射分支里对每个 division 面先算屏幕 UV 包围盒，
  超过 `0.6` 视为「必须模板裁剪的背景大平面」跳过、小面（准星面）照画。原因：HAMR 的 `division_bg`
  在腰射视角下投影高度约 1.1 UV，不拦的话腰射也会糊一整块黑（r6-2 引入的隐患，本轮一并修掉）。
  证据：`scope_hamr.png` 纹理 2× 映射后该 UV 采样到不透明黑 texel A=255/R=0/G=0/B=0
  （geo 声明 128×128、实际贴图 256×256，uv(0,76.5) → 像素 (0,153)）。

**未改动**：Lead 在 `renderWithoutStencil` 末尾补的 `super.render(...)`（保持原样，`BedrockAttachmentModel.java:557`）；
持枪位置/相机/枪模定位（`FirstPersonRenderGunEvent`）未动。

## 三、新增一次性诊断（每模型 1–3 条，不刷屏）

构建后实机日志里会出现（`TACZ:` 前缀，每个附件模型只打一次）：

| 日志行 | 判读 |
| --- | --- |
| `TACZ: attachment first-person render: scope=?, sight=?, transform=?, stencil=?` | HAMR 应为 `scope=true, sight=true`（走 `renderBoth`）；`stencil=false` 表示走降级路径 |
| `TACZ: scope no-stencil fallback: selective=?, captureLens=?, aiming=?, aim=?, branch=?` | `branch` 三值：`ADS(...)` / `ADS-transition(keep body/ocular, divisions HIDDEN)` / `hipfire(full model incl. divisions)` |
| `TACZ: scope no-stencil division plane: drawn=false, divisionPaths=2` | HAMR 应为 `drawn=false, divisionPaths=2`（division + division_2）。若出现 `drawn=true` 则是腰射帧（正常） |

验收判据：

1. HAMR 开镜全过程（含抬枪/收镜过渡）**不再出现黑框**，圆内保留（放大的）世界画面（r6-3 后过渡段没有
   lens disc —— 镜片只在 `progress≥0.5` 后出现，属预期）；
2. 纯 scope 回归：XL50/APM4s/FMP13/REAP-IR 开镜仍无黑框、镜内正常出世界/热成像；
3. 腰射仍不空壳/缺面；4. `lint_files(error)=0`，本轮**未跑 gradle**。
