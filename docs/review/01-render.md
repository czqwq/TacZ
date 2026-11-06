# 渲染管线审查报告（task-1 / render-review）

范围：`src/main/java/com/tacz/guns/client/renderer/**`、`client/model/**`、
`com/github/mcmodderanchor/**`、`com/maydaymemory/**`。
基准：上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`；1.7.10 原版/Forge：
`build/rfg/minecraft-src/java/**`。证据日志：`build/r7run.log`（12:23）、
`run/client/crash-reports/*`。

结论速览：**2 个已修 P0（第一人称枪体画错空间、背包图标退化成 1 像素）+ 1 个已修 P1
（第三人称误画第一人称枪）**，其余为 P1/P2 报告项（多数在别人 scope 或属已记录的平台降级）。
增量回归（新增节，见文末「增量回归」）：mcshim 顶点语义变更后 **0 处漏 `endVertex()`**、
**0 处 Tessellator 嵌套**、**0 处 `getModelViewStack()` 误用**；另新发现 1 个 P1
（cancel `RenderHandEvent` 连带丢失镜片/overlay/手持摄像机动画，需 mixin 侧修）。

---

## 一、已修问题

### P0-1 第一人称手持枪体没有 GL 相机空间基座 → 枪被画到世界原点并随视角自转

- **文件**：`src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java:407`
  （`onRenderHand(RenderHandEvent)`）
- **证据 / 推理链**：
  1. 本工程的 `mcshim.PoseStack` 是**纯 CPU** 矩阵栈；顶点在
     `BedrockPart#translateAndRotateAndScale` / `BedrockCubeBox#compile`
     （`client/model/bedrock/BedrockCubeBox.java:147-161`）里被烘成**相机空间**坐标，
     最终由 `BufferUploader#draw` → `Tessellator#draw()` 用**当前 GL_MODELVIEW** 投影
     （`mcshim/BufferUploader.java:48-88`）。
  2. 1.7.10 的 `RenderHandEvent` 由 `ForgeHooksClient#renderFirstPersonHand`
     （`ForgeHooksClient.java:264-266`）在 `EntityRenderer#renderWorld` 末尾派发
     （`EntityRenderer.java:1434`）；此刻 GL_MODELVIEW 仍是「世界+相机」视图矩阵。
  3. 原版 1.7.10 的手部 pass 在 `renderItemInFirstPerson` **之前**先
     `glMatrixMode(GL_MODELVIEW); glLoadIdentity();`（`EntityRenderer.java:778-786`），
     而本方法 `event.setCanceled(true)` 会跳过整个 `renderHand`。
  4. 上游 1.20 的 `RenderHandEvent#getPoseStack()` 基座本身就是相机空间
     （`GameRenderer#renderItemInHand` 以 bobView 矩阵起头，不叠世界视图矩阵）。
  ⇒ 修补前：相机空间顶点被当成世界坐标再乘一次视图矩阵，枪体会出现在世界原点附近、
  随视角旋转自转，且不会跟随手部。这是「第一人称看不到枪 / 枪飞走」的直接原因。
- **修法（已修）**：在 `onRenderHand` 里把整个渲染块包进
  `GL11.glPushMatrix() + glLoadIdentity()` / `finally { glPopMatrix(); }`
  （modelview）。同时补 `GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)` —— 因为原版是先清深度再
  `renderHand`（`EntityRenderer.java:1436`），事件被 cancel 后没人清，贴墙/草地时枪体会被
  世界几何裁掉。
- **风险 / 未验证**：投影矩阵未动（1.7.10 世界投影与手部投影同为近平面 0.05，仅 FOV 修饰器
  `getFOVModifier(partialTicks, true)` 不同）。若实机发现 FOV 不匹配，需在
  `CameraSetupEvent`/`GameRendererMixin` 一侧对齐 `ITEM_MODEL_FOV_DYNAMICS`，属 P2 调优。

### P1-1（已修）第三人称下也会画第一人称枪，并 cancel 掉原版 `renderHand`

- **文件**：同一个 `onRenderHand(RenderHandEvent)`。
- **证据**：`RenderHandEvent` 在 `EntityRenderer#renderWorld` 里是**无条件**派发的
  （`EntityRenderer.java:1434`），而 1.7.10 只在第一人称才画手持物
  （`EntityRenderer.java:794` 的 `thirdPersonView == 0` 守卫写在 `renderHand` 内部）。
  本方法 `event.setCanceled(true)` ⇒ 第三人称下既多画一把相机空间枪、又丢掉原版 `renderHand`
  （含 `renderOverlays`）。
- **修法（已修）**：入口处 `mc.gameSettings.thirdPersonView != 0` 直接 return（不 cancel）。

### P0-2 GUI / 背包图标：`INVENTORY` 分支缺 16× 缩放 → 枪械图标退化成 1 像素点

- **文件**：`client/model/RenderHelperCompat.java`（新增 `applyGuiSlotScale`），
  调用点 4 处：`client/renderer/item/AnimateGeoItemRenderer.java:86`（覆盖
  `GunItemRendererWrapper`）、`AmmoItemRenderer.java:58`、`AttachmentItemRenderer.java:49`、
  `GunSmithTableItemRenderer.java:43`。
- **证据**：
  1. 1.7.10 的 `ForgeHooksClient#renderInventoryItem` 非 3D 分支**只**
     `glTranslatef(x, y, -3 + zLevel)` + `glDisable(GL_LIGHTING)`
     （`ForgeHooksClient.java:187-203`），**没有任何缩放**。
  2. 上游 `SlotModel`（`client/model/SlotModel.java:32-38`）的四边形在模型空间是 1×1 单位
     （所有 cube 顶点在 `compile` 里都 `/16`），1.20 由
     `ItemRenderer#renderGuiItem` 的 `scale(16,-16,16)` 放大成 16×16 像素。
  3. 逐顶点核算（`translate(0.5,1.5,0.5)` + `Rz180` + bone `(8,24,-10)` + cube `[-16,0]²`）：
     修补前四边形落在 GUI 像素空间的 `x,y ∈ [0,1]` ⇒ 1 像素；
     乘 16 后正好是 `(x,y)..(x+16,y+16)` 的图标格。
- **修法（已修）**：新增 `RenderHelperCompat#applyGuiSlotScale(PoseStack)`（`scale(16,16,16)`），
  在 4 个 `renderItem` 的 `INVENTORY` 分支里对 identity PoseStack 施加。
  取 `+16` 而非 1.20 的 `-16`：1.7.10 GUI 空间同为「+Y 向下」但没有 1.20 那次 Y 翻转，
  `-16` 会把四边形甩到图标格左上方格外的 `[-1,0]²`。
- **未验证**：图标**朝向**（是否上下/左右镜像）未在实机确认；`SlotModel` 的 `Rz(180)`
  是为 1.20 的翻转空间写的，实机若发现图标倒置，改 `applyGuiSlotScale` 的 y 符号即可
  （单点修改）。

---

## 二、未修（报告 / 转派）

### P1：`ModernKineticGunItem` 的占位图标指向不存在的贴图（scope: item/）

- `item/ModernKineticGunItem.java:117` `register.registerIcon("minecraft:bow")`。
  实测 `build/rfg/recompiled_minecraft-1.7.10.jar` 里
  **没有** `assets/minecraft/textures/items/bow.png`（只有 `arrow.png` / `compass.png`）。
  ⇒ 每次资源加载都会刷一屏 TEXTURE ERRORS（`build/r7run.log:429-445` 的最后一条），
  且该图标会变成紫黑缺失贴图。
- 建议改 `minecraft:compass` 或 `minecraft:arrow`（与 `AttachmentItem`/`AmmoItem` 一致）。
  我没有改：文件不在本任务写作用域。

### P1：`build/r7run.log` 里的 9 条 `textures/blocks/block/*.png` 缺失已过期

- 日志记的是 `textures/blocks/block/statue.png` 这种双子目录路径，说明当时 `registerBlockIcons`
  传的是 `tacz:block/xxx`（1.7.10 的 block atlas 会前缀 `textures/blocks/`）。
  当前代码已改成 `tacz:statue` / `tacz:pegboard` 等
  （`block/StatueBlock.java:69`、`TargetBlock.java:99`、`GunPegboardBlock.java:93`、
  `GunDisplayCaseBlock.java:102`、`AmmoStorageBlock.java:123-124`、
  `AbstractGunSmithTableBlock.java:89`），而 `assets/tacz/textures/blocks/*.png` 齐备。
  ⇒ 无需处理，等下一次实机日志复核。

### P2：`RenderHelperCompat#getBufferSource` 的 per-context 缓存

- `client/model/RenderHelperCompat.java:47-62`：按 `ItemDisplayContext` 缓存一个
  `BufferSource` 并跨调用复用。上游 1.20 每次 `RenderHelper.getBufferSource(...)` 都新建
  `MultiBufferSource.immediate(...)`。1.7.10 侧因为 `endBatch()` 会清空 `builders`/`drawOrder`
  所以**当前不会串批**；但 `ClientGunPackReloader` 之外没有人调用 `clear()`，
  若将来出现「取 buffer → 中途异常 → 没 endBatch」的路径，残留批次会在下一次同 context
  的渲染里被画到错误的 GL 矩阵下。建议在 `renderByItem` 的 `finally` 里兜一次 `clear()`，
  或改为不缓存（`immediate()` 只是两个空集合）。**未改**：属性能/健壮性权衡，需 Lead 决策。

### P2：`BedrockModel#render` 的 `delegateRenderers` / `particleWindowRenderer` 是模型级可变状态

- `client/model/bedrock/BedrockModel.java:527-539`：`particleWindowRenderer` 用后置 null、
  `delegateRenderers` 渲染后整个换新 `ArrayList`。模型对象在**所有同 id 枪械/实体间共享**
  （`GunDisplayInstance#getGunModel()`），并且 `GunItemRendererWrapper#renderFirstPerson`
  会在渲染前 `setParticleWindowRenderer(...)`。若同一帧内同一模型被两个上下文渲染
  （例如第一人称 + 第三人称同时可见的第三人称"自己"），第二次会看到第一次遗留的
  delegate/particle 渲染器。上游同样是这形态，**不是移植引入**，记录以免误判。

### P2：`ScopeThermalRenderer` GL 状态 `glEnable` 与 `glDisable` 计数不等

- `client/renderer/scope/ScopeThermalRenderer.java`：`glEnable` 3 处、`glDisable` 2 处。
  已人工核对为「进入时读 `glIsEnabled` 再条件恢复」模式（与 `TextShowRender` 同款），
  属正常写法，**不是 bug**；在此记录，避免后续重复排查。

### P2：`ExternalGunItemVboRenderer#tryRender` 恒返回 `UNSUPPORTED`

- `client/renderer/item/ExternalGunItemVboRenderer.java:53-58`。已记录的平台降级
  （本工程无 VBO 后端），行为等价于「关掉静态网格缓存、走完整模型渲染」。
  `RenderConfig.STATIC_ITEM_GUN_VBO` 因此对画面无影响 —— 若用户反馈「开了静态 VBO 没变化」，
  这是预期。

---

## 三、核对通过（无问题）

逐文件与上游比对或按上游语义核算，**未发现语义漂移**：

| 文件 | 核对方式 | 结论 |
|---|---|---|
| `renderer/item/GunItemRendererWrapper.java` | 逐行对上游 1:1（505 行） | 一致（仅 `xBob/yBob`→0 的已记录降级、`Vec3`/`Camera`→1.7.10 等价物） |
| `renderer/item/AnimateGeoItemRenderer.java` | 逐行对上游 | 一致（除本次新增的 GUI 缩放） |
| `renderer/item/GunItemTransforms.java` | diff 上游 | **完全相同** |
| `client/model/bedrock/BedrockModel.java` | 对上游 `render` 段逐行 | 一致（`RenderHelper.endBatch`→`RenderHelperCompat.endBatch`） |
| `client/model/bedrock/BedrockPart.java` | 对上游 | 一致（`Axis`→JOML 等价构造，注释已给依据） |
| `client/model/bedrock/BedrockCubeBox.java` / `BedrockCubePerFace.java` | 对上游 UV/顶点表 | 一致（顶点烘焙 + `/16` 对上游 `matrix.transformPosition` 等价） |
| `client/model/bedrock/UnshadedVertexConsumer.java` | 对上游 | 一致（`RenderSystemAccessor` 主光方向→固定 `(0,1,0)`，已记录） |
| `client/model/bedrock/BedrockPolygon.java` / `ModelRendererWrapper.java` / `BedrockCube.java` / `BedrockVertex.java` | 脚本 diff | 仅 import/格式差异 |
| `client/model/SlotModel.java` / `BedrockAmmoModel.java` | 对上游 | 一致 |
| `client/model/RenderHelperCompat.java` | 语义核算 + 1.7.10 源码对照 | `renderFirstPersonArm` 的 GL 保存/恢复成对；新增 GUI 缩放见 P0-2 |
| `client/renderer/item/ExternalGunItemVboRenderer.java` | 读 + 交叉 mcshim | 有意降级，注释依据充分 |
| `renderer/block/StaticItemRenderHelper.java` | 对 1.7.10 `ForgeHooksClient` | `pushToGL/popFromGL` 成对；缩放/绑定贴图与 Forge 一致 |
| `renderer/block/StatueRenderer.java` / `GunSmithTableRenderer.java` | `glPushMatrix`↔`glPopMatrix` 计数 | 成对（1 对，其余为 javadoc 文本） |
| `renderer/block/GunDisplayCaseRenderer.java` / `GunPegboardRenderer.java` | 同上 | 成对 |
| `model/functional/TextShowRender.java` | GL 状态保存/恢复 | 正确（`glIsEnabled` 快照 + finally 恢复） |
| `client/model/functional/ShellRender.java` / `MuzzleFlashRender.java` / `AttachmentRender.java` / `BeamRenderer.java` / `RangefinderImpactPredictionRender.java` | push/pop + `glEnable/glDisable` 配对核算 | 平衡 |
| `com/github/mcmodderanchor/**/FirstPersonRenderHandler.java` | 全文读 + 1.7.10 `RenderHandEvent` 时序 | 除 P0-1 外一致；状态机/收枪过渡/粒子生命周期与上游同构 |
| `v1/client/renderer/IFPGeoItemRenderer.java` / `BedrockModelRenderTypes.java` | 读 | 接口形态与上游一致（`isSameItem`/`blockOffhandRender` 已按 1.7.10 语义改写并注依据） |
| `v1/common/ResourceLocationKeyframe.java`、`resource/serialize/*`、`v1/molang/**`、`com/maydaymemory/mae/**` | 抽样 + 只读审查 | 与渲染无直接耦合；未发现 GL/线程问题（纯 CPU） |

---

## 四、未覆盖 / 未验证风险（诚实清单）

1. **`vendor simplebedrockmodel v2`（`v2/client/mesh/**`，约 30 文件）未逐个通读**：
   该套 GPU 网格设施在本工程恒不走（无 VBO 后端、`ARCompat.LOADED=false`），
   故按「聚焦实际调用链」的要求只做了抽样。若日后启用 v2 路径，需重新审查。
2. **`v1/particle/**`（snowstorm 粒子，约 40 文件）只审查了第一人称渲染入口与
   `ParticleRenderer`/`BillboardHelper` 的 GL 用法**，曲线/molang 求值未逐行核对。
3. **P0-1 / P0-2 的修复都只做了静态验证**（`get_file_problems` + `lint_files` 均 0 error），
   没有实机渲染确认。当前 `runClient` 仍卡在
   `ClientAssetsManager#getPackInfo` NPE（Lead 在修）与 gun data EnumMap 反序列化失败，
   修完后才能看画面。建议实机重点看三条：(a) 第一人称枪是否被正确放在眼前并跟随视角；
   (b) 背包图标是否 16×16 且朝向正确；(c) 贴墙时枪体是否仍可见。
4. **GUI 图标的朝向**：`SlotModel` 的 `Rz(180°)` 是为 1.20 的 Y 翻转空间写的，
   1.7.10 下是否需要去掉/改符号只能实机确认（见 P0-2「未验证」）。
5. `client/renderer/scope/**`（镜片/热成像，GLSL→固定管线改写，最近改动最多）只核对了
   GL 状态的成对性（`glPushMatrix`/`glPopMatrix`、`glMatrixMode` 还原），**着色器数学与
   FBO 语义未验证**；该类属高风险但需要实机+截图才能判定。

---

## 五、本轮改动文件清单

1. `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java`
   —— P0-1（modelview 归位到相机空间 + 清深度）与 P1-1（第三人称守卫）
2. `src/main/java/com/tacz/guns/client/model/RenderHelperCompat.java`
   —— P0-2 新增 `applyGuiSlotScale(PoseStack)`
3. `src/main/java/com/tacz/guns/client/renderer/item/AnimateGeoItemRenderer.java` —— P0-2 调用点
4. `src/main/java/com/tacz/guns/client/renderer/item/AmmoItemRenderer.java` —— P0-2 调用点
5. `src/main/java/com/tacz/guns/client/renderer/item/AttachmentItemRenderer.java` —— P0-2 调用点
6. `src/main/java/com/tacz/guns/client/renderer/item/GunSmithTableItemRenderer.java` —— P0-2 调用点

全部文件 `mcp__idea__get_file_problems(errorsOnly=true)` 与
`mcp__idea__lint_files(min_severity=error)` 均为 **0 error**；未运行任何 gradle 命令。

---

# 增量回归（api-shim 改了 `TessellatorVertexConsumer`/`BufferBuilder` 语义之后）

**背景变化**：`mcshim.TessellatorVertexConsumer#vertex()` / `BufferBuilder#vertex()` 从
「立即 `addVertex`」改为 1.20 正确语义 —— **`vertex()` 只记位置，`endVertex()` 才落顶点**，
属性（uv/color/normal/light）跨顶点保留（依据 `build/rfg/minecraft-src/.../Tessellator.java:390-433`）。
隐含前提变成「每条 `.vertex(` 必须配对 `.endVertex()`，否则顶点静默丢失」。

## 增量-1 `vertex()`/`endVertex()` 配对审计 —— ✅ 无需修改

审计方法：本 scope 全量统计（`vertex()` 与 `endVertex()` 计数）+ 逐条链人工核对
（含 25 行前瞻扫描，排除「`.vertex(` 后直接 `draw()`/`end()`」）。

| 文件 | vertex / endVertex | 结论 |
|---|---|---|
| `client/model/bedrock/BedrockCubeBox.java:155-161` | 1 / 1 | 完整链 `vertex→color→uv→overlay→lightmap→normal→endVertex` ✓ |
| `client/model/bedrock/BedrockCubePerFace.java:127-133` | 1 / 1 | 同上 ✓ |
| `v2/.../tree/TreeGeometryWriter.java:341-350, 374-383` | 2 / 2 | 两处都是完整链 ✓ |
| `v2/.../baked/BakedGeometryChunkRenderer.java:174-180, 191-197` | 2 / 2 | 完整链 ✓ |
| `v2/.../gpu/MeshVertexFormat.java:35-44` | 1 / 1 | 完整链（format probe）✓ |
| `v1/particle/render/BillboardHelper.java:151-160` | 1 / 1 | 完整链 ✓ |
| `v1/particle/world/SnowStormParticle.java:521-526` | 1 / 1 | 完整链 ✓ |
| `v2/.../capture/MeshSink.java` | 2 / 3 | 2 条链 + 1 个 `endVertex()` 实现（flush 到自身数组，无 delegate）✓ |
| `v2/.../capture/LightPassVertexRouter.java` | 1 / 2 | 转发型：`vertex()` 只缓存，`endVertex()` 写完整链到 target ✓ |
| `client/model/bedrock/UnshadedVertexConsumer.java` | 1 / 2 | 装饰器：`vertex`/`endVertex` 各自直转 delegate（新语义下正确）✓ |
| `renderer/scope/ScopeThermalRenderer.java` | 3 / 5 | `TeeVertexConsumer`（vertex/属性/endVertex 双双转发）✓；`MaskVertices.endVertex()` 写完整链 ✓ |
| `renderer/scope/ScopeLensRenderer.java` | 0 / 1 | `Bounds implements VertexConsumer` 只做包围盒统计，`endVertex()` 故意空实现 ✓ |

**伪阳性**：`v2/.../tree/TreeBedrockModelBaker.java:312` 的 `triangle.vertex(i)` 是 record
访问器，不是 `VertexConsumer`。

scope 外扫描（只报告）：`client/particle/BulletHoleParticle.java` 1/1 ✓；
其余全在 `mcshim/**`（基础设施自身的实现/转发）。**未发现漏 `endVertex()` 的调用点。**

## 增量-2 1.7.10 `Tessellator` 单例不可嵌套 —— ✅ 未发现确定会嵌套的路径（2 处 P2 提示）

`Tessellator.instance` 全局唯一，`startDrawing` 未 `draw` 时再 `startDrawing` 会抛
`Already tesselating!`。本 scope 内直接 `startDrawing` 的 4 处全部**自包含**
（`startDrawing → 纯 setter/addVertex → draw()`，中间不触发任何 `endBatch`/`BufferUploader`）：

| 位置 | 结论 |
|---|---|
| `renderer/scope/ScopeLensRenderer.java:525-536`（`drawDisc`） | 自包含 ✓ |
| `client/model/functional/BeamRenderer.java:160-188`（`stringVertex`） | 自包含（`beamVertex` 只调 setter）✓ |
| `client/model/functional/RangefinderImpactPredictionRender.java:188-196` | **先** `RenderHelperCompat.endBatch(bufferSource)`(179 前) 再 `pushToGL`+`startDrawing`，顺序正确 ✓ |
| `client/model/BedrockAttachmentModel.java:367-377`（目镜遮罩三角扇） | 自包含 ✓ |

反向（外层已 `startDrawing`、内层又 flush）也已核对：`mcshim/GuiGraphics` 的
`fill/fillGradient/drawString` 三处都是自包含，不会在被调用方持有开启状态的
`startDrawing` 期间执行。（P2 提示：`RenderHelperCompat#getBufferSource` 用
per-context 缓存 source，若将来出现「写一半 → 嵌套调用同 context 的 `endBatch`」，
`BufferBuilder` 会抛 `not building`；当前 4 个使用缓存的调用点
（`GunSmithTableRenderer:164`、`StatueRenderer:134`、`TargetRenderer:137`、
`TargetMinecartRenderer:122`）都是「写→立即 flush、无嵌套」，安全。）

## 增量-3 `RenderSystem.getModelViewStack()` 假 PoseStack —— ✅ 无调用点

全仓 `getModelViewStack(` 只有 4 处命中，且全部在 `mcshim/RenderSystem.java` 自身
（定义 + javadoc）与 `client/gui/GunSmithTableScreen.java:1122` 的**注释**里。
⇒ **没有任何生产代码走「pushPose 后没进 GL」的失效路径**，无需修改。

顺带核对 `pushToGL()`/`popFromGL()` 全仓 7 处调用点的配对：

| 调用点 | 配对方式 |
|---|---|
| `client/renderer/block/StaticItemRenderHelper.java:81/95` | try/finally ✓ |
| `client/renderer/block/DisplayGunMeshGroup.java:132/138` | try/finally ✓ |
| `renderer/other/HumanoidOffhandRender.java:126/130` | try/finally ✓ |
| `client/model/functional/TextShowRender.java:114/131` | try/finally ✓ |
| `client/model/functional/BeamRenderer.java:158/190` | try/finally ✓ |
| `client/model/functional/RangefinderImpactPredictionRender.java:179/209` | try/finally ✓ |
| `client/model/RenderHelperCompat.java:130/136`（`renderFirstPersonArm`） | **原来是裸配对，已补 try/finally**（本增量唯一改动） |

`applyToModelView()`（无 push/pop 的 `glMultMatrix`）两处混入点
`mixin/client/GameRendererMixin.java:273`（`hurtCameraEffect`，随后 `ci.cancel()`）
与 `mixin/client/ItemInHandRendererMixin.java:118`（`renderItemInFirstPerson` HEAD，
位于 `renderHand` 的 `glLoadIdentity` 之后）：都是在「本来就会被外层 push/pop 的矩阵区间」内
追加乘法，语义正确（只读审计，未改）。

## 增量-4 【P1 已按 Lead 裁决修复】cancel `RenderHandEvent` 的三个连带损失 → 「保留 cancel + 补偿」

只读审计 `mixin/**` 后确认：`FirstPersonRenderHandler#onRenderHand` 里的
`event.setCanceled(true)` 会让 `EntityRenderer#renderWorld` 整个跳过 `renderHand`
（`EntityRenderer.java:1434` 的 `if (!renderFirstPersonHand(...)) { glClear; renderHand(...); }`），
而 1.7.10 的 `renderHand` 除了画手还负责三件事，于是持枪时：

1. **镜片不渲染**：`ScopeLensRenderer.render()` 唯一调用点是
   `mixin/client/GameRendererMixin.java:237-242`，注入在 `EntityRenderer#renderHand` 的
   `itemRenderer.renderOverlays(F)V` INVOKE 之前 —— `renderHand` 不跑 ⇒ 镜片永不绘制
   （手里没枪时反而正常，因为那时本 handler 提前 return 不 cancel）。
2. **丢失 `ItemRenderer#renderOverlays`**（`EntityRenderer.java:805`）：水下/火焰/传送门/
   方块内等全屏 overlay 在持枪时消失。
3. **`BeforeRenderHandEvent` 永不派发**：`mixin/client/ItemInHandRendererMixin.java:113-119`
   注入在 `renderItemInFirstPerson` HEAD，而该方法在 `renderHand` 内部（`EntityRenderer.java:797`），
   被跳过 ⇒ `CameraSetupEvent#applyItemInHandCameraAnimation` 链路失效（枪械手持摄像机动画）。

### 裁决与落地方式

Lead 裁决：**保留 cancel，在 `FirstPersonRenderHandler#onRenderHand` 内最小补偿**
（不改 `mixin/**`、不重排第一人称渲染管线 —— T7 的「不 cancel、只抑制 `itemToRender`」方案需要
改 mixin 且本轮无法实机验证，风险过高）。

已在 `onRenderHand` 的 **cancel 确定路径**内按 vanilla 顺序补齐三处，每处独立 `try/catch`
（单个失败不打断第一人称渲染）+ `TODO(runtime-verify)` 注释：

| 补偿 | 调用位置 | 依据 |
|---|---|---|
| (a) `BeforeRenderHandEvent` | `updateParticleEmitterTransforms` 之后、`renderFirstPerson` **之前** | 复刻 `ItemInHandRendererMixin.java:113-119` 的三步（new PoseStack → post → `applyToModelView()`）；此刻本 handler 已 `glLoadIdentity`，基座 = 相机空间，等价上游 |
| (b) `ScopeLensRenderer.render()` | 枪体渲染完 + `setCanceled(true)` + 粒子之后 | `ScopeLensRenderer.java:370-375` 自包含 PROJECTION/MODELVIEW 的 push+identity，外层 identity 下调用无害；`LENSES` 空/未编译时直接 return |
| (c) `ItemRenderer#renderOverlays(partialTicks)` | (b) 之后（最后） | vanilla 顺序「手(:797) → 镜片/overlay(:805)」；且 vanilla 是在 `glPopMatrix()`(:801) 之后调用，进入时 modelview 就是 identity —— 与本 handler 当前状态一致；该方法只画 fire/block/water/portal/helmet，**不会**重画手持物 |

**只在 cancel 路径调用**（`inst == null` / 无渲染器等提前 return 的分支不 cancel，
vanilla `renderHand` 照常执行 → mixin 注入仍然生效），因此不会出现「两层镜片 / 两次 overlay」。

### 遗留：`GameRendererMixin:237-242` 的镜片注入在「持枪」路径永不再命中

`renderHand` 被 cancel 跳过 ⇒ 该注入点只在**空手**路径生效；持枪时镜片改由
`FirstPersonRenderHandler#compensateScopeLenses()` 补偿调用。mixin 侧注释**保持不动**
（由 Lead 记录该分工）。若日后改成「不 cancel」方案，必须同时移除本补偿，否则镜片会画两遍。

---

# 第二轮：同类崩溃专项（`@Nullable` / 空栈 = null 直接解引用）

**背景**：用户实机 P0 `Supplier.get() 返回 null → ItemStack.getItem() NPE`
（`LivingEntityAim#tickAimingProgress`）。根因类：上游 1.20 用非 null 哨兵
（`ItemStack.EMPTY` / 已加载完成的数据对象），1.7.10 用 `null` 表示空栈/缺失数据，
读取点若照抄上游就会 NPE。渲染路径**每帧**执行，同类漏洞 = 每帧崩溃 / 黑屏。
本轮**只处理硬崩溃**（NPE / CCE / ISE），不处理降级与观感问题。

## 二轮-已修（2 处）

### R2-1 【P0】`TargetRenderer` 直接解引用 `BedrockModel#getNode(...)` 的 `@Nullable` 返回值

- **文件**：`client/renderer/block/TargetRenderer.java:125-135`
- **崩溃条件**：`BedrockModel#getNode` 在 `modelMap` 里找不到骨骼名时返回 `null`
  （`client/model/bedrock/BedrockModel.java:472-479`）。上游 1.20（`TargetRenderer.java:44-48`）
  直接 `upperModel.xRot = ...` / `headModel.visible = false`。本移植的 bedrock 模型走
  「legacy / new 版本二选一」加载分支（`BedrockModel.java:262-367` / `:90-260`），
  模型文件版本与 `getGeometryModelNew/Legacy` 不匹配时两个 `load*Model` 都会提前 return，
  得到**空 `modelMap`** ⇒ `getNode` 全为 `null` ⇒ **靶方块 TESR 每帧 NPE**（放下靶子就崩）。
- **修法（已修）**：取出两个节点后 `if (headModel == null || upperModel == null) return;`。
  与同包 `TargetMinecartRenderer.java:108-112` 已有的判空保持一致。
- **备注**：内置 `assets/tacz/models/bedrock/target.json` 确实含 `head` / `target_upper`，
  所以正常资源下不会触发；本修复是「资源异常/半加载时不崩」的硬防护。

### R2-2 【P0】`ExternalShellRenderer` 直接解引用 `ClientGunIndex#getGunData()` 的 `@Nullable` 返回值

- **文件**：`client/renderer/external/ExternalShellRenderer.java:55-70`
- **崩溃条件**：`ClientGunIndex#getGunData()` 是 `@Nullable`
  （`client/resource/index/ClientGunIndex.java:66-69` → `CommonAssetsManager.get().getGunData(id)`
  在 gun data 缺失/**反序列化失败**时返回 null —— 正是 `build/r7run.log` 里
  「gun data EnumMap 反序列化失败」那条的产物）。当 gun 的 display 没有
  `shell_ejection` 模型/贴图时，代码走 `TimelessAPI.getClientAmmoIndex(gunData.getAmmoId())`
  兜底分支 ⇒ **有弹壳在飞时每帧 NPE**（一开火就崩）。
- **修法（已修）**：`gunData == null ? null : TimelessAPI.getClientAmmoIndex(...)`，取不到就 return。

## 二轮-审计通过（未改，逐点核对，全部已有判空/不可为 null）

| 关注点 | 结论 |
|---|---|
| `GunPegboardRenderer:172-188`、`GunDisplayCaseRenderer:178-193` | `held == null` 已判空后才 `held.copy()` ✓ |
| `ScopeThermalRenderer:264`（`player.getHeldItem()`） | `hasThermalScope(...)`（同文件 :346-380）逐层判空 `IGun`/attachment/`ClientAttachmentIndex`/variant ✓ |
| `client/model/papi/AmmoCountPapi:39` | 只做 `== stack` 比较；`gunData == null` 已在 :32 提前 return ✓ |
| `HumanoidOffhandRender:81/88/98` | `inventory`/`itemStack`/`hotbarShow` 全部判空 ✓ |
| `EntityBulletRenderer:92-138`、`TargetMinecartRenderer:103-112` | `Optional.isPresent` + 模型节点判空 ✓ |
| 4 个 item renderer 的 `renderByItem` | `stack.getItem() instanceof IXxx` 模式匹配 + 缺失时回退 `ENTITY_EMPTY_TEXTURE`（非 null 常量）✓ |
| `GunItemRendererWrapper:394-412`（`gunIndex.getModelTexture()/getSlotTexture()/getTransform()`） | `GunDisplayInstance` 在加载期用 `Preconditions.checkArgument` 保证 model/texture 非 null（:473-489）、`checkTransform` 保证 transform+scale 非 null（:626-633）、`checkSlotTexture` 用 `requireNonNullElseGet` 兜底（:635-640）✓ |
| `BedrockGunModel.checkShowMuzzle:220-232` | `isPresent()` 后才 `.get()` ✓ |
| `GunItemRendererWrapper:198-212` / `AnimateGeoItemRenderer:141-198` | `display.getAnimationStateMachine()` 与 `getStateMachine(stack)` 返回值**全部**先判空（`!= null` / `== null return`）✓ |
| `getRootNode()`（`AnimateGeoItemRenderer:272`、`GunItemRendererWrapper:225`） | 均 `rootNode != null` 判定 ✓ |
| `getCameraAnimationObject()` / `cameraAnimationObject.rotationQuaternion` | 前者 `final` 构造即赋值；后者字段默认 `new Quaternionf(0,0,0,1)`（`CameraAnimationObject.java:17`）✓ |
| `port.Camera.getMainCamera(...)`（`FirstPersonRenderHandler:500`） | 恒返回非 null；内部 `renderViewEntity` 为 null 时各 getter 返回 0 / `Vec3.ZERO` ✓ |
| `Framebuffer`（`ScopeLensRenderer:318-322`、`ScopeThermalRenderer:419-423/542-548`） | 全部 `main == null` + 尺寸 ≤0 双重早退 ✓ |
| `getExternalGunModel()` / `getExternalLodModel()`（二轮补充项 2） | 本 scope 4 处调用点全部判空：`ExternalGunAppearance:102-111`、`:175`、`DisplayGunGeometry:228-232`（`lod != null` 后才 `getLeft/getRight`），`visitOptionalModel:237-241` 再兜一次 ✓ |
| `getStateMachineParam()` / `getControllableData()`（二轮补充项 1、3） | 本 scope **0 个调用点**（只在 `client/event`、`client/gui`）→ 只报告，见下 |
| `ExternalGunModel.displayInstance()` | `final` 字段、构造器内 new，永不为 null ✓ |

## 二轮-只报告（其他 scope / 非崩溃）

1. **`getStateMachineParam()` 判空**（二轮补充项 1）：调用点在
   `client/event/TickAnimationEvent`、`client/event/FirstPersonRenderGunEvent` —— 不在本 scope，
   请 Lead 转派给对应 owner。本 scope 的 `getAnimationStateMachine()` 调用点已全部判空。
2. **`getControllableData()` 判空**（二轮补充项 3）：调用点在 `client/gui`（HUD），不在本 scope。
   光是把 `"controllable": null` 反序列化进 `GunDisplay.controllableData` 就会让
   `getControllableData()` 返回 null，HUD 里 `.get(fireMode)` 会 NPE —— 属每帧 HUD 绘制。
3. **`IClientPlayerGunOperator.fromLocalPlayer(player)`** 是
   `(IClientPlayerGunOperator) player` 的**硬转换**（`api/client/gameplay/IClientPlayerGunOperator.java:22-24`），
   不是 null 问题而是 **CCE 风险**：若给 `EntityPlayerSP` 加接口的那个 mixin 未 apply，
   本 scope 的 `GunItemRendererWrapper:158/175`（每帧相机动画）与
   `ScopeThermalRenderer:258` 都会抛 `ClassCastException`。当前 `ScopeThermalRenderer`
   对它做了 `operator == null` 判空（对 CCE 无效）。建议改 `instanceof` 模式匹配 ——
   但该 API 签名属 `api/**`（YSM 硬约定），本轮**不动**，只报告。



