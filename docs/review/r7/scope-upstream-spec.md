# r7 · 瞄具镜片系统：上游等价规格（逐行移植映射 + 替换清单）

- 任务来源：用户要求「**严格校验上游 TACZ 实现与 1.20 原版渲染代码，按其真实机制做等价实现**」，
  并补充「**尽可能是接近实现，最好不要自己搓实现代码**」⇒ 本文件不是「再设计一套近似」，
  而是**把上游代码逐行映射到 1.7.10 固定管线**，并给出必须删除的自研近似。
- 分析范围：`ScopeLensRenderer` / `BedrockAttachmentModel` 的瞄具部分 / 第一人称手部 pass 的矩阵状态 /
  FBO·stencil 操作 / reticle 顶点空间 / 遮罩可见性。
- 本文只做**只读分析 + 规格**：未改任何代码、未跑 gradle/mcctl、未改 `tmp/**`、`build/**`、`run/**`。
- 上位文档：[scope.md](scope.md)（r7 前几轮实机复核记录）。**本文推翻其中 §11.2 的修法与 §12.2 的根因判断**，
  依据见 §1.4 / §1.5；§0.1-11/12（运行期补 stencil、无 stencil 降级不画 division）与本文不冲突，保留。

---

## 0. 结论速览

1. **镜片几何从来不是「投影包围盒 → NDC 椭圆」。** 上游的镜片/模板圆**都是**同一份
   「视空间圆」：顶点 `(16·90·cx, 16·90·cy, -90)`、半径 `80·modifier·aimProgress`
   （`BedrockAttachmentModel.java:290-299` 与 `ScopeLensRenderer.java:273-284`，后者注释原文
   "Exactly the same view-space circle used by BedrockAttachmentModel's stencil inversion"）。
   `Bounds`（`ScopeLensRenderer.java:374-418`）**只用来算镜片 shader 的 `LensCenter/LensRadius` 两个 uniform**
   （`:236-238`）和轮廓贴图矩形（`:241-243`），**从不参与几何**。
   → 本移植把 `Bounds` 当成几何来源（`ScopeLensRenderer#drawNdcDisc`、`BedrockAttachmentModel` 的 INVERT 椭圆）
   是**根级偏离**，也是 Elcan 黑屏 / 红点消失一类问题的来源。
2. **`center*16*90 / z=-90` 与平台无关，不存在「1440 倍换算不成立」。**
   代入投影后 `z` 被透视除法消掉，圆心的 NDC 精确等于
   `(f/aspect·(vx-ox), f·(vy-oy))`（`v`=模型 `scope_view` 枢轴、`o`=模型 `ocular` 枢轴，单位=模型单位 1/16 格），
   半径 NDC = `(0.8889·m·p·f/aspect, 0.8889·m·p·f)`。推导与真实资产数值见 §1.3 / §1.4。
3. **capture 时刻上游的 `ModelViewMat` 就是单位矩阵**（证据链 §1.2）：手部 pass 的顶点变换 =
   `ProjMat(=手部 FOV 透视) × I`。本移植第一人称已经是「PoseStack 烘顶点 + `glLoadIdentity` + 手部投影」
   （`FirstPersonRenderHandler.java:463-477,599-616`），**天然同构**，只要把 `(GL_PROJECTION, GL_MODELVIEW)`
   当成那一对矩阵读出来即可（现状 `ScopeLensRenderer#readMatrices:1432-1441` 已对）。
4. **stencil 状态机本移植已经与上游逐行一致**（§2 逐行对照；`BedrockAttachmentModel#renderOcularStencil:306-332`、
   `#renderOcularAndDivision:353-551`、`#renderBoth:847-894`、`#renderScope:932-975`、`#renderSight:896-930`）。
   唯一错的是「INVERT 圆的几何」这一块。**不需要重写状态机**。
5. **lens pass 的 FBO 语义本移植也已等价**（`CaptureStencil:1764-1883` ⇔ 上游 `Capture#target#enableStencil`），
   只剩 3 处细节要与上游对齐：capture 期间 `glDisable(GL_SCISSOR_TEST)`、快照纹理过滤 `LINEAR`、wrap `CLAMP_TO_EDGE`
   （§3.3）。
6. **reticle（division）顶点空间**：capture 时按 PoseStack 烘成**相机空间（= 手部 pose space）**，
   帧末用**同一对 capture 矩阵**重画 ⇒ 1.7.10 用 `loadMatrices(lens.projection, lens.modelView)` + 固定管线等价
   （现状 `drawLensWithStencil:824` + `drawReticle:1234-1319` 已对，**不需要改**）。
7. **必须整体替换的是**：`BedrockAttachmentModel` 的 INVERT 椭圆（改回上游视空间圆）、
   `ScopeLensRenderer#drawLens` 的几何（改回 `buildSurface` 视空间圆）、以及删除
   `projectUvBounds` / `drawLensFallback` / `lensViewSpacePathWorks` 自愈兜底 / 一切「画不出就退 NDC 椭圆」的阈值逻辑。
   完整清单见 §7。
8. 允许保留的**平台等价替换**只有一类：1.7.10 没有 `RenderTarget#enableStencil` API ⇒ 运行期给主 framebuffer
   补 depth-stencil（`MainFramebufferStencil`）+ 自己建 capture FBO/stencil 副本。这不算自研渲染逻辑。
   白名单见 §9。

---

## 1. 坐标系与矩阵（问题 1）

### 1.1 三个空间的定义

| 空间 | 单位 | 谁在用 | 依据 |
|---|---|---|---|
| **模型空间**（Bedrock geo 坐标） | 模型单位（16 = 1 格） | `BedrockPart.x/y/z`、geo json 的 `pivot/origin` | `BedrockPart.translateAndRotateAndScale` 里 `x/16.0F`（`tmp/TACZ-1.20.1/.../client/model/bedrock/BedrockPart.java:90-104`） |
| **pose space = 相机空间**（第一人称） | 格 | `PoseStack` 栈顶矩阵、烘进顶点的坐标、`getBedrockPartCenter` 的输出 | 上游 `GameRenderer#renderItemInHand` 先 `posestack.setIdentity()`（`tmp/minecraft-src-1.20.1/.../GameRenderer.java:998`）；本移植对应 `FirstPersonRenderHandler` 里 `glLoadIdentity()`（`:463-465`） |
| **NDC** | 归一化 −1..1 | 模板圆/镜片圆最终落点、`Bounds` 的中间量 | 投影矩阵 |

- `getBedrockPartCenter`（上游 `BedrockAttachmentModel.java:208-216`）= 沿节点路径压完 `translateAndRotateAndScale` 后
  栈顶矩阵的**平移分量**（`m30/m31/m32`）⇒ **pose space（相机空间），单位格**。
  本移植同实现（`client/model/BedrockAttachmentModel.java:264-281`）⇒ 空间相同。
- 上游手部 pose 的起点是「单位矩阵 + 原版视角延滞 `Rx(a)Ry(b)`」（`ItemInHandRenderer.java:332-333`），
  TACZ 紧接着乘 `Rx(-a)Ry(-b)`（`GunItemRendererWrapper.java:202-204`）⇒ **两者精确抵消**，
  所以 TACZ 枪模的实际 pose 起点就是**相机空间单位矩阵**。本移植用
  `applyVanillaFirstPersonHandLag` 补回被跳过的 `+a/+b` 再乘 `-a/-b`（`AnimateGeoItemRenderer.java:417-420`、
  `GunItemRendererWrapper.java:227-229`）⇒ 抵消结果同样为单位矩阵。

### 1.2 capture 时刻的 `projection` / `modelView` 到底是什么（确定答案）

**上游（1.20.1 + Forge 47）**

1. `GameRenderer#renderItemInHand` 第一件事：`resetProjectionMatrix(getProjectionMatrix(getFov(camera, f, false)))`
   （`GameRenderer.java:995-998`）⇒ `RenderSystem.projectionMatrix` =
   `perspective(fov_hand, aspect, 0.05, depthFar)`（`GameRenderer.java:1038-1058`）。
   `getFov(..., false)` 基础值 70°（`GameRenderer.java:926-948`），但**TACZ 会改写它**：
   `CameraSetupEvent#applyGunModelFovModifying`（`tmp/TACZ-1.20.1/.../client/event/CameraSetupEvent.java:122-168`）
   在 `event.usedConfiguredFov()==false`（= 手部 pass）时把 FOV 换成枪的 `model_fov` / 配件 `views_fov`，
   并按 ADS 进度插值。所以 **fov_hand 不是常数**（例：Elcan 4x 的 `views_fov=[35,55]` ⇒ 开镜手部 FOV=35°）。
2. `RenderSystem.modelViewMatrix`：**在整条第一人称手部 pass 里没有任何 `applyModelViewMatrix()` 调用**
   （全仓 `applyModelViewMatrix` 调用点：`Minecraft.java:1441,1528`、`ParticleEngine`、`RenderStateShard:288-295`、
   `LevelRenderer:1371,1416,1448`、`CubeMap`、`GameRenderer:1108,1184`、`Gui`——`ItemInHandRenderer` 一个都没有）。
   `LevelRenderer#renderLevel` 在 `:1414-1448` 做 `pushPose → mulPoseMatrix(云层用的相机位姿) → apply → popPose → apply`
   ⇒ 出方法时 `modelViewMatrix` 回到基线（**单位矩阵**）。随后 `GameRenderer` 才 `renderItemInHand`。
   ⇒ BEWLR/手部渲染期间 `ModelViewMat = I`，顶点变换 = `ProjMat · Position`。
3. 旁证：`GunItemRendererWrapper#captureThirdPersonMuzzle`（`GunItemRendererWrapper.java:398-414`）用
   `viewRotationInv.mul(pose)` + 相机位置把 **pose space 当相机空间**换回世界空间 —— 同一套假设。

**本移植（1.7.10）**

- `FirstPersonRenderHandler#onRenderHand`：`GL_MODELVIEW push + LoadIdentity`（`:463-465`），
  `pushHandProjection()` 里 `GL_PROJECTION push + LoadIdentity + gluPerspective(ITEM_MODEL_FOV, aspect, near, far*2)`
  （`:599-616`）。`ITEM_MODEL_FOV` 由 `CameraSetupEvent#applyGunModelFovModifying`（`client/event/CameraSetupEvent.java:292-333`）
  逐行移植算出（含 `views_fov`/`ads_path`/二阶平滑）。
- 顶点由 mcshim `PoseStack`（JOML，post-multiply，语义与 1.20 相同）烘进 Tessellator ⇒ 一次变换。
- ⇒ **本移植的 `(GL_PROJECTION, GL_MODELVIEW)` 就是上游的 `(ProjMat, ModelViewMat)`**，
  在枪模渲染期间 = **（手部投影, 单位矩阵）**。`capture()` 只需把它们读出来（现状已用
  `glGetFloatv(GL_PROJECTION_MATRIX/GL_MODELVIEW_MATRIX)`，`ScopeLensRenderer.java:1432-1441`）。

**唯一需要守住的约束（重要）**：上游 `ModelViewMat` 恒为单位矩阵，所以「视空间圆公式」的几何语义成立。
本移植若把某个变换放进 `GL_MODELVIEW`，几何看起来对，但**圆的 NDC 落点会变**（缩放/旋转与「先乘 1440、
固定 z=-90」不交换）。目前唯一的这类偏差是**受伤摇晃**：
`FirstPersonRenderHandler#compensateHurtCameraEffect` 直接 `glRotatef` 到 `GL_MODELVIEW`（`:646-700`）。
它在 `hurtTime>=0` / 死亡翻滚时才生效，属短暂状态；建议把这段改成乘进 PoseStack（等价上游
`GameRenderer#bobHurt` 写在 pose 栈上，`GameRenderer.java:950-971`），让枪模 pass 的 `GL_MODELVIEW` 恒为 I。

### 1.3 `center*16*90` / `SURFACE_Z=-90` 的精确推导（含为什么与平台无关）

**已知**（`BedrockAttachmentModel.java:284-300`）：

```
centerX = center.x * 16 * 90
centerY = center.y * 16 * 90
顶点   = (centerX + cos·rad, centerY + sin·rad, −90)      rad = 80 · modifier · aimProgress
```

`ScopeLensRenderer#buildSurface`（`:273-284`）是同一份几何（镜片 surface），`SEGMENTS=90`、`SURFACE_Z=-90`（`:37-38`）。

**推导**：设手部投影为 `perspective(fov, a, n, f0)`。对任意点 `(x,y,z)`（`z<0`）：

```
x_ndc = (f/aspect) · x / (−z),      y_ndc = f · y / (−z),      f = 1/tan(fov/2)
```

代入圆顶点（`x = 16·90·cx`、`z = −90`）：

```
x_ndc(圆心) = (f/a) · (16·90·cx)/90 = (f/a) · 16·cx
y_ndc(圆心) = f      · (16·90·cy)/90 = f      · 16·cy
半径 NDC    = (f/a) · rad/90 ,  f · rad/90          （rad = 80·m·p）
```

**关键点 1：z 被消掉。** `−90` 只用来与 `×90` 配对，任意等比例缩放 `(k·x, k·y, −k·90)` 给出同一 NDC。
所以「1440（=16·90）」既不是「块→模型单位」的换算，也不是某个引擎特有的单位，而是
**「在 z=−90 的平面上、把侧向偏移按 90 放大」**的写法；换成任何透视投影都成立。

**关键点 2：`16·cx` 恰好等于「模型单位下的侧向差」。** 由 §1.1 的 pose 链解析可得（忽略无关的
中间旋转，只用 `getPositioningNodeInverse` 的反向链 `FirstPersonRenderGunEvent.java:259-286`）：

```
cx = (vx − ox)/16,  cy = (vy − oy)/16,  cz = (oz − vz)/16          [单位：格]
v = 模型里 scope_view 枢轴（模型单位），o = ocular 枢轴（模型单位）
```

⇒ `16·cx = vx − ox`（模型单位），于是

```
圆心 NDC = ( (f/aspect)·(vx−ox),  f·(vy−oy) )
半径 NDC = ( 0.8889·m·p·f/aspect, 0.8889·m·p·f )          （0.8889 = 80/90）
```

即：**圆心偏移 = 模型作者写的「瞄具视点 ↔ 目镜」侧向差（模型单位直接当 NDC 用）；
半径 = 固定 41.6°（tan=0.889）的半角圆，随开镜进度线性生长。** 这就是上游的全部语义，
与 1.7.10/1.20 无关。

> 推论：`capture()` 的 `centerX/centerY/radius` 三个形参（本移植现状只当诊断，见
> `ScopeLensRenderer.java:1716-1724` 注释）**必须重新用于几何**，且**必须与上游逐位同源**
> （`center*16*90`、`z=-90`、`rad=80*modifier*progress`）。

### 1.4 数值验证（真实资产，`src/main/resources/.../geo_models/attachment/*_geo.json`）

枢轴取自各附件 geo 的 `scope_view` / `ocular` 骨骼（本文件实测 dump）：

| 附件 | `scope_view` 枢轴 | `ocular` 枢轴 | pose 圆心 `(cx,cy,cz)`（格） | 模型单位侧向差 `(vx−ox, vy−oy)` | ADS `fov_hand` | `f=1/tan(fov/2)` | 圆心 NDC（a=1.78） | 半径 NDC_y @p=1 |
|---|---|---|---|---|---|---|---|---|
| `scope_elcan_4x`（4.25x） | (0, 1.78125, 10.5) | (0, 1.77, 2.925) | (0, 0.000703, −0.4734) | (0, 0.01125) | 35.0° | 3.171 | (0, 0.0357) | **2.82** |
| `scope_acog_ta31` | (0, 1.825, 10) | (0, 1.825, 2.8175) | (0, 0, −0.4489) | (0, 0) | 35.0° | 3.171 | (0, 0) | 2.82 |
| `sight_apm4s`（`scope:true`） | (0, 1.25, 10.5) | (0, 1.25, 0.625) | (0, 0, −0.6172) | (0, 0) | 见 display | — | (0, 0) | — |
| `scope_hamr`（x3 视点 `scope_view`） | (0, 3.5625, 8) | `ocular_sight` (0, 3.7454, −2.7335) | (0, −0.0114, −0.6709) | (0, −0.1829) | 40.0° | 2.747 | (0, −0.502) | 2.44 |
| `sight_infitac_fmp13` | (0, 1.075, 11) | (0, 1.13125, 0.0745) | (0, −0.0035, −0.683) | (0, −0.05625) | 见 display | — | (0, −0.155) | — |
| `scope_pso_1` | (0.2923, 0.4173, 11) | (0.29233, 0.41733, 4.85396) | (0, 0, −0.3841) | (≈0, ≈0) | 见 display | — | (≈0, ≈0) | — |
| `scope_reap_ir` | (0, 1.8125, 10.5) | (0, 1.8125, 3.1375) | (0, 0, −0.4609) | (0, 0) | — | — | (0, 0) | — |
| `scope_98k` | (0, 1.78125, 10) | (0, 1.77646, 2.58211) | (0, 0.0003, −0.4636) | (0, 0.00479) | — | — | (0, 0.015) | — |

`fov_hand` 来自附件 display json 的 `views_fov`（例：`scope_elcan_4x_display.json` 的 `views_fov:[35,55]`，
`views:[2,2]`；`scope_hamr_display.json` 的 `views_fov:[40,60]`、`views:[2,1]`）。

**判读**：
- 所有默认瞄具在完全开镜时 `|vx−ox|`、`|vy−oy|` ≤ 0.19 模型单位 ⇒ 圆心 NDC 偏移 ≤ 0.5（HAMR 的 x3 视点最偏），
  而 **半径 NDC_y ≥ 2.4 ≫ 1**（屏幕半高），所以**圆把整个目镜吃掉** ⇒ 目镜黑遮罩（`GL_EQUAL i+1`）没有像素可画
  ⇒ 不会黑屏（这就是问题 5 的答案）。
- 目镜自身的角半径（以 Elcan 为例：目镜八边形半径 ≈1.1 模型单位 = 0.069 格，位于 z=−0.47）
  ⇒ NDC 半径 ≈ `f·0.069/0.47 = 0.465`；圆半径 2.82 是它的 6 倍 ⇒ 必然全覆盖。
  反解：**圆覆盖目镜所需的开镜进度** `p ≥ 0.465/2.82 ≈ 0.17`；这与「抬枪过程镜片从中心长出」的观感一致。
- 镜片 surface（`buildSurface`）与 INVERT 圆**逐参数相同** ⇒ 帧末镜片精确填满「目镜 ∩ 圆」。

> 本表同时是**验收断言表**：移植后 `capture()` 若打印 `(vx−ox, vy−oy)` 与上表差 >0.5 模型单位，
> 说明 **pose 链**（不是镜片代码）与上游不同源（见 §8.2 断言 A）。

### 1.5 更正 `docs/review/r7/scope.md` §11.2 / §12.2

| scope.md 结论 | 复核结果（依据） |
|---|---|
| §11.2「上游视空间圆 `center*16*90/z=-90` 依赖 1.20 RenderSystem 的相机空间单位，本移植换算不成立（≈1440 倍）」 | **不成立**。z 在透视除法里被消掉（§1.3），公式与引擎无关；本移植的 pose space 与上游同为「格」、`ModelViewMat` 同为 I（§1.2）。 |
| §11.1「`discNdc` 与 `ocularNdc` 差 ~9.3 倍 ⇒ INVERT 圆不在目镜上」 | **两个量本来就不是同一个东西**：`discNdc` 是「41.6°·p 的固定角半径圆」在 `16·(vx−ox)` 偏移下的包围盒，`ocularNdc` 是目镜几何的投影包围盒。把这两者对比不能推出「坐标系不成立」。用实机日志数值反算：`radius_y(discNdc)=0.022 ⇒ p = 0.022/(0.889·f)`；取 f≈1.428（FOV 70，说明**当时不是开镜态**）⇒ **p≈0.017**；`center_x(discNdc)=2.74 ⇒ cx = 2.74/((f/a)·16) ≈ 0.21 格`，而同一帧 `ocularNdc` 中心 ≈(0.29,−0.18)**也不在屏幕中心** ⇒ 该帧是抬枪早期，腰射/过渡姿态下圆心本来就该在屏幕外（上游同样如此）。 |
| §12.2「Elcan 目镜有顶点跨到相机平面后面 ⇒ `Bounds.isValid()` 判无效 ⇒ INVERT 跳过 + capture 返回 false ⇒ 目镜黑遮罩涂黑」 | 现象可复现，但**根因归属错误**：Elcan 在完全开镜时的 pose 空间目镜中心是 `(0, 0.0007, −0.4734)`（§1.4），目镜盘所有顶点 `z ≈ −0.47 ± 0.07`（模型 z 1.83/16 与 `scope_view` z 10.5/16 之差），**全部在相机前方、`w>0`**，上游/正确的移植都不会判「跨相机平面」。⇒ 若移植侧真的报 `behindCamera`，说明**该帧的 pose 或投影与上游不同源**（或该帧不是开镜态），应在 §8.2 断言 A/B 上定位，而不是改镜片代码。 |
| §11.2「改用 Bounds NDC 椭圆是等价实现」 | **不等价**：上游圆心偏移来自「模型单位侧向差」，半径是固定角半径；Bounds 椭圆是「目镜自身投影」。二者只在「完全开镜 + 目镜大致居中」时偶然接近，且在目镜贴近相机平面时数值退化。 |

---

## 2. BEWLR/模型渲染时的 stencil 状态机（问题 2，逐行对照）

上游实现（`tmp/TACZ-1.20.1/.../client/model/BedrockAttachmentModel.java`）与本移植
（`src/main/java/com/tacz/guns/client/model/BedrockAttachmentModel.java`）逐行比对：**状态机已经一致，不需要重写**。

### 2.1 三段入口

| 阶段 | 上游 | 本移植 | 判定 |
|---|---|---|---|
| 开模板 + 清模板 | `renderBoth:374-376` / `renderScope:432-434` / `renderSight:408-410`：`RenderHelper.enableItemEntityStencilTest()` → `clearStencil(0)` → `RenderSystem.clear(GL_STENCIL_BUFFER_BIT)` | `renderBoth:866-871` / `renderScope:950-954` / `renderSight:913-917`：`RenderHelperCompat.enableItemEntityStencilTest()` → `stencilMask(0xFF)` → `clearStencil(0)` → `glClear(GL_STENCIL_BUFFER_BIT)` | 等价（`stencilMask(0xFF)` 是实现细节：掩码为 0 时 `glClear` 不写模板） |
| 关模板 | `renderBoth:396-397` | `renderBoth:890-891` | 等价 |
| 非加速分支 | `renderBoth:367-397`（`ARCompat.shouldAccelerate()` 为假时） | `renderBoth:847-894`（AR 无对应实现，恒走非加速） | 平台降级（上游自己的 fallback） |

### 2.2 `renderOcularStencil`（写目镜模板值）

上游 `:240-258` / 移植 `:306-332`，顺序完全一致：

```
colorMask(F,F,F,F)                      // 只写模板
depthMask(false)                        // 目镜不写深度
stencilMask(0xFF)                       // 允许写全部 8 位
stencilOp(KEEP, KEEP, REPLACE)           // 深度通过 → 用 ref 覆盖
for i = N-1 → 0:                         // 从后往前
    if (isScope == isScopeOcular[i]):
        stencilFunc(GREATER, i+1, 0xFF)  // 只在「当前值 < i+1」处写
        renderTempPart(ocular_i)         // 画目镜几何
stencilOp(KEEP, KEEP, KEEP); depthMask(true); colorMask(T,T,T,T)   // 恢复
```

语义：**重叠区域由「索引大者」独占**（倒序遍历 + `GREATER`）；未被任何目镜覆盖的像素保持 0。
`i+1` 的范围 1..N，因此 0 是「镜身/背景」的专用值。
注意 `renderTempPart` 内部的 `BufferUploader`/`endBatch` 会把顶点真正提交，所以上述 stencil 状态是在**提交时**生效的
（移植侧 `renderTempPart:288-304` 里 `RenderHelperCompat.endBatch` 同位置）。

### 2.3 `renderOcularAndDivision`（INVERT 圆 + 黑遮罩 + division 延迟）

上游 `:271-331` / 移植 `:353-551`：

```
1) stencilOp(KEEP,KEEP,INVERT); colorMask(F,F,F,F); depthMask(false)
2) rad = 80 * scopeViewRadiusModifier * aimProgress        // aimProgress = IClientPlayerGunOperator#getClientAimingProgress(getFrameTime())
3) for i in 0..N-1:
       if (selective && !isScopeOcular[i]) continue
       stencilFunc(EQUAL, i+1, 0xFF)                       // 只在「本目镜」像素上
       center = getBedrockPartCenter(poseStack, ocular_i)   // pose space（格）
       TRIANGLE_FAN: (16*90*cx, 16*90*cy, -90) + 90 段圆，半径 rad
4) depthMask(true); colorMask(T,T,T,T); stencilOp(KEEP,KEEP,KEEP)
5) for i in 0..min(N, M)-1:
       if (selective && !isScopeOcular[i]) { func(EQUAL, i+1); renderTempPart(division_i) }   // 非本镜目镜的 division 立即画
       else {
           func(EQUAL, i+1); renderTempPart(ocular_i)      // ←「目镜黑遮罩」：INVERT 之后这里只剩圆外像素
           b = ~(i+1) & 0xFF
           func(EQUAL, b)
           if (ScopeLensRenderer.capture(16*90*cx, 16*90*cy, rad, b, config, bounds→..., reticle→...)) continue;
           renderTempPart(division_i)                       // capture 失败才立即画 division（无裁剪）
       }
```

**为什么 division 在 ADS 时不立即画**：division 面（准星/刻度/红点）是**给远焦平面设计的巨大平面**
（默认资产里 `division` 的模型 z 达 −3.9 ~ −120，换算到 pose space 是相机前方 0.25 ~ 7.5 格，
例：Elcan `divisions` (−23) + `division` (−75) ⇒ pose z ≈ −6.8 格），**不加模板裁剪会盖满屏幕**（r6-3 实机证据）。
上游把它延迟到帧末镜片 pass，用 `GL_EQUAL b`（= 圆内）裁剪后画在「目镜 ∩ 圆」里。

**INVERT 的位运算**：`INVERT` 是逐位取反全部 8 位。因为外面套了 `GL_EQUAL i+1`，
只有「恰为 i+1」的像素被反成 `~(i+1)&0xFF` = `b`（例：i=0 ⇒ `b=254`，与实机日志 `stencilRef=254` 一致）。
⇒ **b 必须由同一份 i 推出**，移植侧 `:503` 与上游 `:317` 一致。

### 2.4 结论

- 状态机（`colorMask/depthMask/stencilMask/stencilOp/func` 的**顺序与恢复**）**已逐行一致**，
  含「INVERT 前 `stencilOp` 先行、`colorMask` 关色彩、INVERT 后先恢复 `depthMask/colorMask/stencilOp` 再画遮罩」这一串时序。
- 唯一必须改的是 §2.3 第 3 步的顶点（下游方案见 §7）。

---

## 3. lens pass 的 FBO 操作（问题 3）

### 3.1 上游时序（`ScopeLensRenderer.java`，逐行）

```
capture(...)                                              // 在模型渲染中途，:73-131
 ├─ 前置闸门：shader 就绪 / 配置 / radius / Oculus / drawFbo!=0 / 主目标尺寸
 │            / viewport 铺满主目标 / 主目标有 STENCIL_ATTACHMENT        :76-89
 ├─ projection = RenderSystem.getProjectionMatrix(); modelView = getModelViewMatrix()
 │  Bounds(proj*modelView); ocular.accept(bounds); if(!bounds.isValid()) return false;   :91-95
 ├─ 保存并关闭 scissor（blit 期间必须关）：scissor=glIsEnabled; disableScissor()          :99-104
 ├─ prepareCapture(w,h,viewport): 复用/新建 TextureTarget(w,h,true) + enableStencil()
 │            + setFilterMode(GL_LINEAR)；保存/恢复 depthTest、viewport、clearColor、clearDepth  :134-159
 ├─ READ_FBO = 当前 drawFbo, DRAW_FBO = capture.target.frameBufferId                       :106-107
 ├─ glBlitFramebuffer(全屏, mask=GL_STENCIL_BUFFER_BIT, filter=GL_NEAREST)                 :109-110
 ├─ reticle.accept(capture.reticle)      // 只录顶点，不绘制                              :112
 ├─ LENSES.add(new Lens(..., projection, modelView, config, bounds, buildSurface(...), reticle))
 └─ finally: 还原 READ/DRAW FBO、activeTexture+绑定、scissor                             :121-129

render()                                                   // 帧末（手部之后、HUD 之前）  :162-222
 ├─ RenderState 快照（program/3 个纹理单元/viewport/scissor/colorMask/depth/blend/cull/
 │   stencil/polygonOffset/clearColor/clearDepth/读写 FBO）                               :421-474
 ├─ RenderSystem.getModelViewStack().pushPose()
 ├─ disableScissor()
 └─ for each lens:
       target.bindWrite(false)                       // 绑定 capture FBO，不改 viewport
       glFramebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, main.getColorTextureId(), 0)
       viewport(0,0,main.w,main.h)
       setProjectionMatrix(lens.projection); modelView.identity().mulPoseMatrix(lens.modelView); applyModelViewMatrix()
       glEnable(GL_STENCIL_TEST); stencilFunc(EQUAL, lens.stencil, 0xFF); stencilOp(KEEP,KEEP,KEEP); colorMask(T,T,T,T)
       drawLens(lens,...)   // 见 3.2
       for each reticle draw: type.setupRenderState(); target.bindWrite(false);
                              disableDepthTest(); depthMask(false); BufferUploader.drawWithShader(vertices); type.clearRenderState()
       finally: target.bindWrite(false); 颜色附件挂回 capture 自己的颜色纹理
    finally: beginFrame(); modelView.popPose(); applyModelViewMatrix()

drawLens(lens,...)                                                                          :224-271
 ├─ activeTexture(TEXTURE0); bind(capture.target 自己的颜色纹理); glCopyTexSubImage2D(0,0,0,0, w,h)
 │   // ←「先拷后采」：此时该纹理没挂在 FBO 上 ⇒ 不自采样
 ├─ Sampler0 = 该纹理；Sampler1 = 热成像纹理（无则同一张）；Sampler2 = 轮廓纹理（无则同一张）
 ├─ uniforms（ScreenSize/LensCenter/LensRadius/Contour*/Distortion/Aberration/Edge*/Vignette*/LensTint/Vision*/SensorTime/ThermalReady）
 ├─ disableDepthTest(); depthMask(false); disableCull(); disableBlend()
 └─ BufferUploader.drawWithShader(lens.surface)      // buildSurface 的视空间圆
```

### 3.2 关键依赖顺序（三条，错一条就出 bug）

1. **先挂主颜色纹理到 `COLOR_ATTACHMENT0`，再 `glCopyTexSubImage2D` 到 capture 自己的颜色纹理**：
   拷贝读的是 READ framebuffer 的颜色附件（=主颜色纹理）、写的是绑定的纹理（capture 自己的）⇒ **不自采样**。
   顺序反了（先拷再挂主颜色）会拷到上一帧/黑。
2. **stencil 只在 capture 时刻从主 FBO blit 一次**：主模板会在枪模渲染结束时被清空
   （上游 `BedrockGunModel:341-346` 同款；移植 `BedrockGunModel#renderUnaccelerated` 同款）⇒
   帧末 `GL_EQUAL` 只能查这份副本。**capture FBO 必须自带 stencil 附件**（这是 `Capture#target#enableStencil()` 的全部意义）。
3. **capture 期间只允许改「framebuffer 绑定 + scissor + 纹理上传」**，不允许绘制、不允许动 projection/modelview
   （上游注释 `:97`）。移植侧 `capture()` 遵守此点，但**漏了 scissor 的保存/关闭**（见 3.3-①）。

### 3.3 1.7.10 / LWJGL2（本工程）等价写法与差异

现状实现（`CaptureStencil:1764-1883`）已经等价地做到了：

| 上游 | 本移植 | 判定 |
|---|---|---|
| `new TextureTarget(w,h,true,ON_OSX)` + `enableStencil()`：颜色纹理 + `DEPTH32F_STENCIL8` 深度纹理，挂 `DEPTH_STENCIL_ATTACHMENT` 或 `DEPTH+STENCIL` 两份 | `CaptureStencil.ensure()`：`glGenFramebuffers` + `RGBA8` 颜色纹理（`GL_COLOR_ATTACHMENT0`）+ `GL_DEPTH24_STENCIL8` **renderbuffer** 挂 `DEPTH_ATTACHMENT`+`STENCIL_ATTACHMENT` + `glCheckFramebufferStatus` | 等价（renderbuffer 不可采样，但上游也从不采样 capture 的深度） |
| `target.resize(w,h,onOsx)`（尺寸变化重建） | `ensure()` 尺寸不符就 `enqueueDeletion()` 重建 | 等价 |
| `GlStateManager._glBindFramebuffer(READ, drawFbo)` + `(DRAW, capture)` + `_glBlitFrameBuffer(..., STENCIL_BUFFER_BIT, NEAREST)` | `copyStencilFrom()`：`glBindFramebuffer(READ/DRAW)` + `GL30.glBlitFramebuffer(..., GL_STENCIL_BUFFER_BIT, GL11.GL_NEAREST)` | 等价 |
| `target.bindWrite(false)` + `_glFramebufferTexture2D(COLOR_ATTACHMENT0, mainColorTexture)` + `finally` 挂回自己的颜色纹理 | `drawLensWithStencil:785-853` 同构（挂回 `dummyColorTexture`） | 等价 |
| `GlStateManager._glCopyTexSubImage2D(...)` | `copyMainColorIntoCaptureSnapshot:876-898`（`glCopyTexSubImage2D`） | 等价 |
| `RenderSystem.stencilFunc/stencilOp/colorMask` | `GL11.glStencilFunc/glStencilOp/glColorMask`（或 mcshim `RenderSystem`） | 等价 |

**必须补/改的 3 处（仍属「按上游对齐」，不是新机制）**：

- **① capture 期间关闭并恢复 scissor**（上游 `:99,104,128`）。
  移植侧 `capture():420-529` **完全没碰 scissor**。若进入时 scissor 处于开启（HUD/GUI 残留、或光影），
  `glBlitFramebuffer` 会被 scissor 裁掉一部分模板 ⇒ 模板副本残缺 ⇒ 镜片/准星局部消失。
  写法：`boolean scissor = glIsEnabled(GL_SCISSOR_TEST); glDisable(GL_SCISSOR_TEST); try{...} finally{ if(scissor) glEnable(...) }`。
- **② 快照纹理过滤 = `GL_LINEAR`**（上游 `prepareCapture:151` `target.setFilterMode(GL_LINEAR)`，作用在 capture 自己的颜色纹理上，
  即镜片 shader 采样的那张）。移植侧 `CaptureStencil` 的 `dummyColorTexture` 是 `NEAREST`（`:1790-1791`）⇒
  镜中画面被放大时是硬像素（观感差异，且会掩盖真实采样问题）。
- **③ 包装 = `GL_CLAMP_TO_EDGE`**（上游 `RenderTarget#createBuffers:114-115` 设 33071）。
  移植侧未设 wrap（默认 `REPEAT`）。镜片 shader 自己 `clampUv`，所以只是「不变式缺失」，但补上才与上游同源。

**GL API 合法性（回答「`glBlitFramebuffer` 掩码/过滤合法值」）**：

- `mask` = `GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT` 的按位或；
- **`mask` 含 `GL_DEPTH_BUFFER_BIT` 或 `GL_STENCIL_BUFFER_BIT` 时 `filter` 必须是 `GL_NEAREST`**（否则 `GL_INVALID_OPERATION`）；
- 只有 `GL_COLOR_BUFFER_BIT` 时 `filter` 可以是 `GL_LINEAR`；
- 源/目标都非多重采样时不要求尺寸相同（这里同尺寸）；
- 深度与模板**分别** blit（上游只拷 stencil、不拷深度，注释 `:108`「capture does not draw or copy depth」）；
- 源必须有 stencil 附件（否则 `GL_INVALID_OPERATION`）⇒ 这就是 `MainFramebufferStencil` 存在的原因（§9-①）。

---

## 4. reticle（division）的顶点空间与绘制（问题 4）

### 4.1 顶点在什么空间烘出来

- 上游 `captureLensDivision`（`BedrockAttachmentModel.java:353-365`）→ `renderTempPart(poseStack, ...)`
  → `BedrockPart#render` → `BedrockCube#compile(poseStack.last(), consumer, ...)`：
  **顶点 = `pose.last().pose()` 作用后的坐标 = pose space（相机空间，单位格）**。
  同一函数还会把 `IFunctionalRenderer`（文字/测距等）产生的顶点写进同一批 buffer（`:359-361`）。
- 本移植 `ReticleBuffers` 收到的是同一份空间（mcshim `BedrockCube.compile` 把 JOML 矩阵烘进顶点，
  `BufferUploader` 再喂 Tessellator）⇒ **空间一致**。

### 4.2 帧末用什么矩阵画

- 上游 `render():184-187`：`setProjectionMatrix(lens.projection, DISTANCE_TO_ORIGIN)` +
  `modelView.identity().mulPoseMatrix(lens.modelView)` + `applyModelViewMatrix()`
  ⇒ reticle 的 shader 变换 = **capture 时刻的 `ProjMat·ModelViewMat`**，与记录时逐位相同 ⇒ NDC 不变（这是「延迟绘制」能对位的原因）。
- 本移植 `drawLensWithStencil:824` `loadMatrices(lens.projection, lens.modelView)`（`glLoadMatrix` 到
  `GL_PROJECTION/GL_MODELVIEW`），随后 `drawReticle:1234-1319` 用 `BufferUploader.draw` 走固定管线
  ⇒ **固定管线变换 = 同一对矩阵** ⇒ 与上游等价。**这条不需要改**（r6 的 R1 结论成立）。
- 逐 reticle 的 `target.bindWrite(false)` + 重挂主颜色纹理 + 重设 `GL_EQUAL lens.stencil`
  （移植 `:1251-1260`）对应上游 `:196-201`：正确、保留。

### 4.3 `RenderType` 的等价

- 上游 `draw.type.setupRenderState()/clearRenderState()`（`RenderType` 由 `BedrockCube` 写入时选，通常是
  `entityCutout` / `entityTranslucent` / `entitySolid` 等），shader 里的 `ModelViewMat/ProjMat` 由 RenderSystem 给。
- 本移植 mcshim `RenderType#setupRenderState/clearRenderState`（`client/mcshim/RenderType.java:436,504`）把
  「纹理/混合/深度/剔除/alpha」翻译成固定管线调用，**顶点变换交给固定管线**（`BufferUploader.draw` → `Tessellator`）
  ⇒ 只需保证「此时的 GL 矩阵 = capture 矩阵」，而 §4.2 已经做到。⇒ **等价，不需要改**。

### 4.4 注意点（保持现状即可）

- reticle 必须在**镜片 surface 之后**画（上游 `render():193→196`，移植 `:826→830`）——同 `stencilFunc(EQUAL b)`，
  顺序影响「准星是否被镜片 shader 覆盖」。
- `disableDepthTest + depthMask(false)` 必须在 reticle 前（上游 `:201-202`，移植 `:1263-1264`）。
- 镜片 shader 程序必须在 reticle 之前 release（移植 `drawLens` 内 `program.release()` → `glUseProgram(0)`），
  否则固定管线顶点会走 shader 而**不**被 GL 矩阵变换（会双变换/错位）。

---

## 5. 遮罩 / 目镜 / 镜身的最终可见性（问题 5）

### 5.1 模板值 → 画面的映射（`renderScope` / `renderBoth` 完成 `renderOcularAndDivision` 之后）

| 像素 | 模板值 | 谁在这里画 | 依据 |
|---|---|---|---|
| 目镜外 | `0` | 镜身 `scope_body`（`GL_EQUAL 0`）、外环、其余几何（`super.render`） | `BedrockAttachmentModel.java:388-392`（上游）/ `:881-893`（移植） |
| 目镜内 **且圆内** | `b = ~(i+1) & 0xFF` | ① 帧末镜片 surface（`GL_EQUAL lens.stencil`）② 帧末 reticle/division（同 ref） | 上游 `ScopeLensRenderer.java:188-209` |
| 目镜内 **且圆外** | `i+1` | 目镜黑遮罩（`GL_EQUAL i+1` 画 `ocular_i` 本体，暗色贴图） | 上游 `:314-315` |
| 组合镜里「非本镜」的目镜 | `i+1` | 该目镜的 `division_i` **立即**画（`GL_EQUAL i+1`） | 上游 `:309-311`（`selective && !isScopeOcular[i]`） |

**为什么不会黑屏**（这是 Elcan 黑屏问题的唯一判据）：

- 黑遮罩的像素集 = 「目镜内 ∧ 圆外」= 目镜像素中模板值仍为 `i+1` 的那部分。
- 圆半径 NDC_y = `0.8889·p·f`；Elcan 开镜 `fov=35° ⇒ f=3.171` ⇒ p=1 时 **2.82 ≫ 1**（屏幕半高），
  p=0.5 时 1.41 仍 ≫ 目镜自身 NDC 半径 0.465（§1.4）⇒ **p ≥ ~0.17 起圆就完全覆盖目镜** ⇒ 黑遮罩无像素 ⇒ 不黑。
- 反之：只要 INVERT 圆没覆盖目镜（圆太小/圆心跑到屏幕外/圆因坐标系问题画失败），黑遮罩就盖满目镜 ⇒ 纯黑八边形
  （= `docs/review/r7/screenshots/c_elcan_ads_black.png`）。**这就是把所有近似修法排除掉的判据。**

### 5.2 正确的「局部」降级（若真遇到平台级失败）

**不要去改几何/加阈值**。上游自己在 `capture()` 失败时的行为就是天然局部的：

```
capture(...) 返回 false  ⇒  该 ocular 的 division 立即画（仍受 GL_EQUAL b 约束，通常 b 像素集为空 ⇒ 不可见）
                          ⇒  该 lens 不进 LENSES ⇒ 帧末没有它的镜片 pass
                          ⇒  其它 lens / 其它瞄具完全不受影响
```

所以「局部降级」= **让该附件的 `capture()` 返回 false + 一条 WARN(once)**，即上游本来的路径；
而不是「换一套几何把它画出来」（那会引入第二种语义，正是用户明确否掉的）。

补充：在**有模板**的环境里，即使 `capture()` 返回 false，division 也是在 `GL_EQUAL b` 下画的，
b 像素集通常为空 ⇒ **不会出现「整屏黑框」**。r6-3 记录的黑框只出现在
「模板测试被整体关闭」的**无 stencil 降级**路径（移植 `renderWithoutStencil:584-635`），那条分支保持现状即可。

---

## 6. 逐行移植映射表（上游 → 1.7.10 本工程）

### 6.1 `ScopeLensRenderer`（上游 511 行）

| 上游 | 职责 | 1.7.10 落点 | 用什么实现 |
|---|---|---|---|
| `registerShader()` `:46-58` | 注册 `shader`（`DefaultVertexFormat.POSITION`） | `ScopeLensProgram.init()`（GLSL 120） | **shim**：`ShaderInstance`→裸 GLSL 程序（`attribute vec3 Position; gl_Position = ProjMat*ModelViewMat*vec4(Position,1)`；`glVertexPointer` 别名到 generic attribute 0） |
| `beginFrame()` `:61-70` | 每帧清 LENSES / 释放 buffer | `beginFrame():340-366`（`GameRendererMixin#renderWorld` HEAD，`:77-80`） | 直接等价 |
| `capture(...)` `:73-131` | 记录 lens（矩阵、stencil 值、surface、reticle、模板副本） | `capture():420-529` | 矩阵 `RenderSystem.getProjectionMatrix()` → `glGetFloatv(GL_PROJECTION_MATRIX)`；`getModelViewMatrix()` → `glGetFloatv(GL_MODELVIEW_MATRIX)`（`readMatrices:1432-1441`） |
| 同上：`Bounds` 注入 | `ocular.accept(bounds)` | 同（`bounds -> captureLensBounds(poseStack, ocular, bounds)`） | 等价 |
| 同上：stencil 附件检查 | `glGetFramebufferAttachmentParameteri(STENCIL_ATTACHMENT, OBJECT_TYPE) != NONE` | `stencilAvailable():1375-1395` + `MainFramebufferStencil` | **GL30 直接映射**（GL<3.0 时判 false） |
| 同上：`prepareCapture` + blit stencil | 见 §3.1 | `captureStencil():579-604` → `CaptureStencil.ensure/copyStencilFrom:1764-1883` | **GL30**：`glGenFramebuffers/glRenderbufferStorage(GL_DEPTH24_STENCIL8)/glFramebufferRenderbuffer/glBlitFramebuffer(…, GL_STENCIL_BUFFER_BIT, GL_NEAREST)` |
| 同上：scissor 关/恢复 | `:99,104,128` | **缺**（§3.3-①） | `glIsEnabled/glDisable/glEnable(GL_SCISSOR_TEST)` |
| `render()` `:162-222` | 帧末每 lens 一个 pass | `render():629-757` + `drawLensWithStencil():773-854` | `target.bindWrite(false)` → `glBindFramebuffer(FRAMEBUFFER, captureFbo)`；颜色附件 → `glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, COLOR_ATTACHMENT0, …, mainColorTexture, 0)`；矩阵 → `loadMatrices():1460-1465` |
| `drawLens()` `:224-271` | 快照 + uniforms + 画 surface | `drawLens():1143-1202` | uniforms 逐项同；**几何必须改回视空间圆**（§7-②）；`ProjMat/ModelViewMat` uniform 必须传 **capture 矩阵** |
| `buildSurface()` `:273-284` | 视空间圆（TRIANGLE_FAN，91 顶点） | **需要重新加回** `drawViewSpaceDisc`（复核④ 被删） | `Tessellator.startDrawing(GL_TRIANGLE_FAN)`；`SEGMENTS=90`、`SURFACE_Z=-90` 已在 `:37-38` |
| `Capture` `:298-307` | `TextureTarget(w,h,true)+enableStencil` | `CaptureStencil:1764-1883` | 见 §3.3 |
| `Draw` `:309-317` | RenderType + RenderedBuffer | `Draw:1581-1592` | 等价 |
| `ReticleBuffers` `:319-343` | `MultiBufferSource` 分桶录制 | `ReticleBuffers:1593-1703`（+ 诊断用 BboxConsumer） | **mcshim** `MultiBufferSource/BufferBuilder/RenderedBuffer` |
| `Lens` `:345-372` | 一次登记的数据 | `Lens:1706-1753`（已存 `centerX/centerY/radius/projection/modelView/captureStencil`） | 等价（`centerU/V·radiusU/V` 来自 Bounds，**仅 uniform 用**） |
| `Bounds` `:374-418` | 顶点→屏幕 UV 包围盒 | `Bounds:1475-1578` | **JOML**（`Matrix4f/Vector4f` 可用）；`isValid()` 语义与上游相同（含 `w<=0.0001` 判 behind） |
| `RenderState`（GlStateSnapshot）`:420-510` | 快照/还原 GL 状态 | `RenderState:1902-2043` | `glGet*`/`glIsEnabled` + 逐项还原（program/3 纹理单元/viewport/scissor/colorMask/depth/blend/cull/stencil/polygonOffset/clear/读写 FBO） |
| `close()` `:286-296` | 只销毁每世界 target，不销毁 shader | `close()/drainDeletes():1085-1141` | 等价（+ 跨线程安全） |

### 6.2 `BedrockAttachmentModel`（瞄具相关；上游 785 行）

| 上游 | 1.7.10 落点 | 判定 |
|---|---|---|
| `render()` 分支 `:168-206` | `:194-262` | 等价 |
| `getBedrockPartCenter()` `:208-216` | `:264-281`（JOML `m30/m31/m32`） | 等价 |
| `renderTempPart()` `:218-238` | `:283-304` | 等价（`RenderHelper.endBatch` → `RenderHelperCompat.endBatch`） |
| `renderOcularStencil()` `:240-258` | `:306-332` | **等价（逐行）** |
| `renderDivisionOnly()` `:260-269` | `:334-351` | 等价 |
| `renderOcularAndDivision()` `:271-331` | `:353-551` | **等价，除 INVERT 圆几何（§7-①）** |
| `captureLensBounds()` `:333-341` / `captureLensPartBounds()` `:344-350` | `:811-846` 附近 | 等价（只喂 `Bounds`，不绘制） |
| `captureLensDivision()` `:353-365` | `:832-845` | 等价（含 `delegateRenderers` 的 functional renderer 二次渲染） |
| `renderBoth/renderSight/renderScope` `:367-456` | `:847-894 / :896-930 / :932-975` | 等价 |
| `render*Accelerated` `:458-774` | `:977-998`（回退非加速） | 平台降级（上游自己的 fallback） |
| 无 stencil 三段降级 | `renderWithoutStencil():584-635` + `captureLensesWithoutStencil():764-792` | 平台降级（**只在补挂失败时生效**，不要动） |

### 6.3 第一人称手部 pass（决定 capture 矩阵）

| 上游 | 1.7.10 落点 | 判定 |
|---|---|---|
| `GameRenderer#renderItemInHand` `:995-1035`（`resetProjectionMatrix(getProjectionMatrix(getFov(camera,f,false)))` + `posestack.setIdentity()`） | `FirstPersonRenderHandler#pushHandProjection():599-616` + `glLoadIdentity():463-465` | 等价 |
| `getFov(...,false)` 70° + TACZ 改写 | `CameraSetupEvent.ITEM_MODEL_FOV`（`:103,292-333`）+ `pushHandProjection` 用它 | 等价（含 `views_fov`/`ads_path`/平滑） |
| `RenderSystem.modelViewMatrix` = I（§1.2） | `GL_MODELVIEW = I` | 等价（**唯一例外**：受伤摇晃写在 GL 上，建议改到 PoseStack，§1.2 末） |
| `ItemInHandRenderer#renderHandsWithItems:332-333` 的视角延滞 | `applyVanillaFirstPersonHandLag()` + 反向旋转（`AnimateGeoItemRenderer:417-420`、`GunItemRendererWrapper:227-229`） | 等价（精确抵消） |
| `GunItemRendererWrapper#renderFirstPerson:197-219` | `GunItemRendererWrapper#renderFirstPerson:213-244` | 等价 |
| `FirstPersonRenderGunEvent#applyFirstPersonGunTransform` | 同名（含 `getPositioningNodeInverse` 的根节点 `1.5−y/16` 规则） | 等价（**必须逐位保持一致**，否则 §1.3 的 `16·cx` 被放大 16 倍） |

---

## 7. 必须替换的清单（Lead 要求 4）

### 7.0 前提：先验断言，再改几何

本文的几何公式**只在 pose 链与上游逐位同源时**成立（`16·cx` 把 pose 误差放大 16 倍）。
因此实施顺序必须是：

1. 先加 §8.1/§8.2 的**只读**诊断（`invertModelDelta` / `invertCenterPose` / 投影断言 B），
   对 Elcan 4x + 98k、ACOG、APM4s、HAMR 各跑一次完全开镜；
2. 断言通过（`|Δ| ≤ 0.2` 模型单位、`cz ∈ [−0.75,−0.35]`、`modelView = I`、`m00 = f/aspect`）⇒ 直接按 §7.2 替换几何；
3. 断言不通过 ⇒ **先修 pose/投影**（`FirstPersonRenderGunEvent` 的 `getPositioningNodeInverse`/`views[]` 索引/挂载偏移、
   `GunItemRendererWrapper` 的 `T(0,1.5,0)·Rz180`、`ITEM_MODEL_FOV`），**不要**在镜片代码里兜底。
   （Elcan 的 `views:[2,2]` ⇒ `viewIndex=1`、而模型只有 1 个 `scope_view` ⇒ `getScopeViewPath` 回落 `get(0)`；
   这条路径必须与上游 `BedrockAttachmentModel.java:120-129` 完全一致。）

### 7.1 差异表：错误近似 vs 平台降级

| # | 现状（移植） | 上游真实机制 | 判定 | 处理 |
|---|---|---|---|---|
| 1 | `BedrockAttachmentModel#renderOcularAndDivision:382-467`：把 ocular 投影成 **NDC 椭圆**（`projectUvBounds`），push identity 矩阵后画椭圆，半径 ×`aimProgress` | 视空间圆 `(16·90·cx, 16·90·cy, −90)`，半径 `80·m·p`，用**当前**（手部）矩阵画（`:284-301`） | **错误近似**（改了几何语义 + 依赖 Bounds 有效性） | **删除并改回上游几何** |
| 2 | `ScopeLensRenderer#drawLens:1195-1200`：`ProjMat/ModelViewMat` 传 identity，画 `drawNdcDisc`（NDC 椭圆） | `buildSurface` 视空间圆 + `setProjectionMatrix(lens.projection)+applyModelViewMatrix(lens.modelView)`（`:184-193,268-270`） | **错误近似**（同一份几何被换成另一种） | **改回**：uniform 传 capture 矩阵，画 `drawViewSpaceDisc(centerX, centerY, radius)` |
| 3 | `Lens.centerX/centerY/radius` 只用于诊断（`:1716-1724`） | 三个参数是几何的唯一来源 | **错误** | 重新参与几何 |
| 4 | `lensViewSpacePathWorks` + `drawLensFallback:860-864` + `probeStencilLensAfterSurface` 的「画不出就补 NDC 椭圆」 | 上游没有这种自愈；失败原因是可诊断的 | **自研兜底（用户明确否掉）** | **删除行为**（probe 可保留为只读日志） |
| 5 | `ScopeLensRenderer#projectUvBounds:551-572`（公开 API，唯一调用者是 INVERT 椭圆） | 上游无此方法 | 自研 API，且误导后来者 | **删除**（`Bounds` 保留给 uniform） |
| 6 | `capture()` 未关 scissor | 上游 `:99-104,128` | 真实 bug（模板副本可能被裁） | 补上 |
| 7 | `CaptureStencil` 颜色纹理 `NEAREST`（`:1790-1791`） | 上游 `setFilterMode(GL_LINEAR)`（`:151`） | 观感/等价性偏差 | 改 `GL_LINEAR` + `CLAMP_TO_EDGE` |
| 8 | 受伤摇晃写进 `GL_MODELVIEW`（`FirstPersonRenderHandler:646-700`） | 上游写在 pose 栈（`GameRenderer#bobHurt`） | 短暂状态下的等价性偏差（会改变圆的 NDC 落点） | 建议移到 `PoseStack`（可选，标注理由） |
| 9 | 无 stencil 时镜片圆盘仍用 NDC 椭圆（`drawNdcDisc`）、division/遮罩不画 | 上游不存在（`enableStencil` 保证有模板） | **平台降级** | 保留（只在 `MainFramebufferStencil` 失败时生效） |
| 10 | 运行期给主 FBO 补 `GL_DEPTH24_STENCIL8`（`MainFramebufferStencil`） | Forge `RenderTarget#enableStencil`（`RenderTarget.java.patch:34-55`） | **平台等价替换** | 保留 |
| 11 | `ScopeLensContour` 恒 `null`（`ContourReady=0`） | JFA 轮廓（`ScopeLensContour.java:50-114`） | **平台降级**（上游 shader 自己保留椭圆兜底分支，`scope_lens.fsh:52-55`） | 保留 |
| 12 | 热掩膜用 AABB 代替逐顶点剪影 | `ScopeThermalRenderer#wrap` 包装 `MultiBufferSource` | **平台降级**（1.7.10 无顶点流插入点） | 保留，与本文无关 |
| 13 | `ARCompat` 加速层不存在 | `render*Accelerated` | **平台降级**（走上游非加速分支） | 保留 |

### 7.2 替换清单（文件 / 方法 / 删除项）

| 文件 | 改动 | 具体 |
|---|---|---|
| `client/model/BedrockAttachmentModel.java` | **改** `renderOcularAndDivision`（`:353-551`）：删除 `:382-467` 的「Bounds→NDC 椭圆 + identity 矩阵」整块，改为上游 `:284-301` 的视空间圆；`invertText` 诊断保留（只打印上游参数） | 见 §7.3-A |
| 同上 | **保留** `radius<=0.001`/`aimProgress` 计算、`capture()` 调用与「失败则立即画 division」 | `:471-536` 不动 |
| `client/renderer/scope/ScopeLensRenderer.java` | **改** `drawLens`（`:1143-1202`）：`ProjMat/ModelViewMat` 传 `lens.projection/lens.modelView`；几何改 `drawViewSpaceDisc(lens.centerX, lens.centerY, lens.radius)` | 见 §7.3-B |
| 同上 | **加回** `SURFACE_Z`/`drawViewSpaceDisc`（TRIANGLE_FAN，91 顶点，`z=-90`） | 见 §7.3-C |
| 同上 | **删** `projectUvBounds`（`:551-572`）、`drawLensFallback`（`:860-864`）、`drawLensWithStencil` 里的 `lensViewSpacePathWorks` 分支（`:811-819,834-843`）、`probeStencilLensAfterSurface/...After` 的**行为**用途（保留只读日志） | — |
| 同上 | **改** `capture`（`:420-529`）：加 scissor 保存/关闭/恢复（对齐上游 `:99-128`） | 见 §7.3-D |
| 同上 | **改** `CaptureStencil.ensure`（`:1789-1801`）：颜色纹理 `MIN/MAG = GL_LINEAR`、`WRAP_S/T = GL12.GL_CLAMP_TO_EDGE` | — |
| 同上 | **删** `Lens` 的 `r7 复核④` 注释段（`:1716-1724`），改为「上游几何参数」 | 文档一致性 |
| `client/model/BedrockAttachmentModel.java` 的调用点（`capture(center.x()*16*90, center.y()*16*90, rad, b, …)`，`:508-522`） | **保持不变**（签名与参数已经是上游形态） | 无需改 |

> `ScopeLensProgram` 的 `ProjMat/ModelViewMat` uniform 已存在（`ScopeLensProgram.java:54-58`），只改喂值，**不需要改 shader**。

### 7.3 实施伪代码（不写代码，只给等价步骤）

**A. `BedrockAttachmentModel#renderOcularAndDivision`（INVERT 段）**

```java
// 前置（与上游 :271-283 同，现状已一致）
stencilOp(KEEP, KEEP, INVERT); colorMask(false,false,false,false); depthMask(false);
float rad = 80f * scopeViewRadiusModifier * aimProgress;      // aimProgress 用 ClientTickHelper.getPartialTick()（现状 :376-380）

for (int i = 0; i < ocularNodePaths.size(); i++) {
    if (selective && !isScopeOcular.get(i)) continue;         // 上游 :285-287
    stencilFunc(EQUAL, i + 1, 0xFF);                          // 上游 :288
    Vector3f c = getBedrockPartCenter(matrixStack, ocularNodePaths.get(i));   // pose space（格）
    float centerX = c.x() * 16 * 90;                          // 上游 :290  ← 不做任何坐标换算
    float centerY = c.y() * 16 * 90;                          // 上游 :291
    // ★ 用「当前」GL 矩阵（GL_PROJECTION = 手部投影，GL_MODELVIEW = 单位）画，不 push identity
    Tessellator t = Tessellator.instance;
    t.startDrawing(GL_TRIANGLE_FAN);
    t.setColorOpaque(255, 255, 255);                          // 颜色被 colorMask 屏蔽，仅为顶点格式
    t.addVertex(centerX, centerY, -90.0D);                    // 上游 :293  SURFACE_Z = -90
    for (int j = 0; j <= 90; j++) {
        double a = j * Math.PI * 2 / 90;
        t.addVertex(centerX + Math.cos(a) * rad, centerY + Math.sin(a) * rad, -90.0D);  // 上游 :294-299
    }
    t.draw();
}
depthMask(true); colorMask(true,true,true,true); stencilOp(KEEP, KEEP, KEEP);   // 上游 :302-304
```

**B. `ScopeLensRenderer#drawLens`（几何与 uniform）**

```java
program.use();
// 采样源与 Sampler0/1/2（现状 :1145-1154 同上游 :225-234，保留）
// 屏幕/镜片 uniform（现状 :1156-1194 同上游 :236-263，保留）
// ★ 唯一改动：矩阵 uniform 用 capture 矩阵（上游的 ModelViewMat 在 capture 时就是单位矩阵）
program.setMatrix4f("ProjMat",      lens.projection);   // float[16]，列主序
program.setMatrix4f("ModelViewMat", lens.modelView);    // = 单位矩阵（capture 时读到的）
disableDepthTest(); depthMask(false); disableCull(); disableBlend();   // 上游 :264-267
drawViewSpaceDisc(lens.centerX, lens.centerY, lens.radius);            // ★ 上游 buildSurface
program.release();                                                     // = glUseProgram(0)，reticle 用固定管线
```

**C. `drawViewSpaceDisc`（= 上游 `buildSurface`）**

```java
static final int SEGMENTS = 90;          // 已在 :37
static final float SURFACE_Z = -90.0F;   // 已在 :38
void drawViewSpaceDisc(float x, float y, float r) {
    Tessellator t = Tessellator.instance;
    t.startDrawing(GL_TRIANGLE_FAN);
    t.addVertex(x, y, SURFACE_Z);
    for (int i = 0; i <= SEGMENTS; i++) {
        double a = i * Math.PI * 2 / SEGMENTS;
        t.addVertex(x + Math.cos(a) * r, y + Math.sin(a) * r, SURFACE_Z);
    }
    t.draw();
}
```

> 该函数由镜片 shader 绘制（`program.use()` 生效中）⇒ 顶点不会被固定管线再变换，
> `ProjMat*ModelViewMat` 完全由 uniform 决定（这正是上游 `BufferUploader.drawWithShader` 的语义）。
> 同一帧里 `loadMatrices(lens.projection, lens.modelView)` 仍要保留给**固定管线 reticle**（§4.2）。

**D. `capture()` 的 scissor 对齐（上游 `:99-128`）**

```java
int readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE); glActiveTexture(TEXTURE0);
int boundTex = glGetInteger(GL_TEXTURE_BINDING_2D);
try {
    glDisable(GL_SCISSOR_TEST);                       // ← 新增：blit 必须有完整像素网格
    CaptureStencil cs = captureStencil(...);          // 现状 :484-490
    ... blit stencil ...
    ... reticle 录制 ...
} finally {
    glBindFramebuffer(READ_FRAMEBUFFER, readFbo); glBindFramebuffer(DRAW_FRAMEBUFFER, drawFbo);
    glActiveTexture(TEXTURE0); glBindTexture(TEXTURE_2D, boundTex); glActiveTexture(activeTexture);
    if (scissor) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);   // 上游 :128
}
```

---

## 8. 验收（Lead 要求 3）

### 8.1 只读诊断日志（临时或一次性 INFO；**不得改变行为**）

| 日志字段 | 期望值（默认资产，完全开镜） | 判读 |
|---|---|---|
| `invertModelDelta=(vx−ox, vy−oy)` | 见 §1.4 表（Elcan `(0, 0.01125)`、ACOG `(0,0)`、APM4s `(0,0)`、FMP13 `(0,−0.056)`、HAMR `(0,−0.183)`） | 与表差 >0.5 ⇒ **pose 链**与上游不同源（不是镜片代码问题） |
| `invertCenterPose=(cx,cy,cz)`（格） | Elcan `(0, 0.0007, −0.4734)`；全部 `cz ∈ [−0.75, −0.35]` | `cz > 0`（在相机后）⇒ pose 链错（§1.5 更正） |
| `rad=80·modifier·p` 与 `aimProgress` | `p` 与 `IClientPlayerGunOperator#getClientAimingProgress` 一致；`p≈1` 时才比较几何 | `p` 小 ⇒ 圆本来就在屏幕外（上游同） |
| `invertNdcCenter=(f/a·Δx, f·Δy)`、`invertNdcRadius=(0.889·m·p·f/a, 0.889·m·p·f)` | Elcan p=1：center `(0, 0.036)`、radius `(1.58, 2.82)`（a=1.78, fov=35） | 半径 `< 0.5` 且 `p≈1` ⇒ fov/投影取错（是否用了世界投影/zoom 后的 fov？） |
| `surfaceNdc == invertNdc`（同一份几何） | 完全相等 | 不等 ⇒ drawLens 的 uniform 喂错 |
| `stencilAtCenter` | `= b = ~(i+1)&0xFF`（单目镜 i=0 ⇒ 254） | 不等 ⇒ 模板写入/副本/ref 三选一；配合 `fboStatus=36053` 判读 |
| `lensPass: stencil=true, reticles=N` | N>0（有 division 的资产） | N=0 ⇒ `captureLensDivision` 没录到（检查 `divisionNodePaths.size()`） |
| `lensDrew / reticleDrew` | 仅作**只读**观测，**不得**触发兜底 | `false` 且上面断言都通过 ⇒ 记录 glError，下一轮定位 |

### 8.2 数值断言（可写成一次性 ASSERT 日志）

- **A（pose 同源）**：`getBedrockPartCenter(ocular)` 换算成模型单位 = `(vx−ox, vy−oy)`。对默认资产，
  `|Δ| ≤ 0.2` 模型单位；`cz ∈ [−0.75,−0.35]` 格。任一越界 ⇒ 检查 `FirstPersonRenderGunEvent`（`getPositioningNodeInverse`
  的根节点 `1.5−y/16` 规则、`scope_view` 路径/`views[]` 索引、`mount offset`）与 `GunItemRendererWrapper` 的 `T(0,1.5,0)·Rz180`。
- **B（投影同源）**：`capture()` 读到的 `projection[0] (m00) = f/aspect`、`projection[5] (m11) = f`，
  其中 `f = 1/tan(ITEM_MODEL_FOV/2)`；`modelView` 必须是**单位矩阵**（`m00=m11=m22=1`，其余 0）。
  否则说明手部 pass 的 GL 矩阵被别的东西改过（受伤摇晃 / 拖尾 / shader 模组）。
- **C（几何自洽）**：`drawLens` 喂给 shader 的 `ProjMat/ModelViewMat` 与 `loadMatrices` 喂给固定管线的是同一对矩阵；
  圆盘顶点 = `(16·90·cx, 16·90·cy, −90)`，半径 = `capture` 时的 `radius`。（数组相等断言即可。）
- **D（stencil 自洽）**：`lens.stencil == ~(i+1)&0xFF`；`renderOcularStencil` 写入值 `= i+1`；
  capture 副本读回 `stencilAtCenter == lens.stencil`。

### 8.3 画面判据（与上游一致）

1. 完全开镜：镜内是「放大的世界画面 + 镜片效果（畸变/暗角/染色/夜视/热成像）+ 镜内准星/刻度」；
   **目镜黑遮罩只在圆外出现**（p≥0.2 后基本看不到）；**镜内看不到枪体**（`scope_body` 用 `EQUAL 0` 排除）。
2. 抬枪/收镜过程：镜片随瞄准圆「从中心长出来」；**不出现整屏黑框**（黑框只可能来自无 stencil 降级）。
3. 腰射：目镜是黑遮罩（`p≈0` 不 INVERT），外环/镜身正常，**没有悬浮的准星/红框**。
4. 组合镜（HAMR，`scope:true + sight:true` → `renderBoth`，`selective=true`）：x3 镜用 `ocular_scope` 的模板值，
   x1 红点目镜用 `ocular_sight`；两套 `division` 各自被裁在自己的目镜里（`EQUAL i+1` / `EQUAL b`）。
5. 纯红点（APM4s：只有 `"scope": true`，走 `renderScope`）：镜片覆盖整个反射窗，红点可见。
6. Elcan 4x：镜内出现世界画面（不再纯黑八边形）。

### 8.4 回归（不应被本轮改动影响）

- 无 stencil 环境（补挂失败 / GL<3.0 / 光影）：继续走 `renderWithoutStencil` 三段降级（§9）。
- 热成像掩膜、热源 AABB、`ScopeLensContour`（恒 null）、HUD/跨准星系统：不改。
- 断线重连后镜片仍生效（`close()` 不清 shader）。

---

## 9. 允许保留的平台等价替换（白名单）

| # | 项 | 上游 | 1.7.10 为什么必须不同 | 判定 |
|---|---|---|---|---|
| ① | 主 framebuffer 的 stencil 附件 | Forge `RenderTarget#enableStencil()`（`tmp/MinecraftForge-1.20.1/patches/minecraft/com/mojang/blaze3d/pipeline/RenderTarget.java.patch:34-55`；`RenderHelper#enableItemEntityStencilTest:52-72`） | 1.7.10 `Framebuffer` 只在 `MinecraftForgeClient.getStencilBits()!=0` 时挂 stencil（`build/rfg/.../shader/Framebuffer.java:117-131`），没有运行期 API | **平台等价**：`MainFramebufferStencil`（运行期 `GL_DEPTH24_STENCIL8` renderbuffer + 同时挂 DEPTH/STENCIL） |
| ② | capture target | `TextureTarget(w,h,true)+enableStencil()`（纹理深度附件） | 1.7.10 无该类 | **平台等价**：`CaptureStencil`（renderbuffer 深度模板 + RGBA8 颜色纹理） |
| ③ | 镜片 shader | `RegisterShadersEvent` + `ShaderInstance`（`DefaultVertexFormat.POSITION`） | 1.7.10 无 shader 注册体系 | **平台等价**：`ScopeLensProgram`（GLSL 120，数学逐行等价） |
| ④ | `BufferUploader`/`RenderType`/`MultiBufferSource`/`BufferBuilder`/`VertexConsumer`/`PoseStack` | blaze3d + JOML | 1.7.10 无 | **平台等价**：`mcshim/**`（`PoseStack` 用真实 JOML，语义与 1.20 相同） |
| ⑤ | `ScopeLensContour`（JFA 轮廓） | 3 个 pass + 512² RGBA8 | GLSL 130+/整数取址；本轮未实现 | **平台降级**：`ContourReady=0`（上游 shader 自己的椭圆兜底分支） |
| ⑥ | 热源逐顶点剪影 | `MultiBufferSource` 包装 | 无顶点流插入点 | **平台降级**：实体 AABB |
| ⑦ | `ARCompat` 加速层 / Oculus / OptiFine 分支 | 有 | 1.7.10 无 | **平台降级**（走上游自己的非加速/非光影分支） |
| ⑧ | `Minecraft.ON_OSX` | macOS 额外错误检查 | 无意义 | 直接传 `false` |
| ⑨ | 手部投影近裁面 `0.05` | `GameRenderer#getProjectionMatrix` | 移植用 `0.01`（`FirstPersonRenderHandler.NEAR_PLANE`，理由是默认枪包 `lefthand_pos` 锚点仅 0.09 格） | **平台降级**（不影响 x/y NDC；只影响近距离裁剪）。注意：若某瞄具目镜 `|z|<0.05`，上游会裁掉而移植不会 —— 现状资产 `|z|≥0.38`，无影响 |
| ⑩ | 无 stencil 时的镜片圆盘 | 不存在该路径 | 没有模板裁剪，视空间圆会盖满屏幕 | **平台降级**：仅此时用 NDC 椭圆（`drawNdcDisc`），并**不得**登记 reticle |
| ⑪ | INVERT 圆绘制前的固定管线状态清理 | 1.20 为 shader 管线，不存在 `GL_TEXTURE_2D` / alpha test 状态 | 1.7.10 固定管线中，无纹理的 INVERT 圆会沿用遗留纹理坐标，被 alpha test 在写模板前丢弃 ⇒ 模板写不进（Elcan 4x 黑镜根因） | **平台等价**：绘制期间临时 `glDisable(GL_TEXTURE_2D)` + `glDisable(GL_ALPHA_TEST)`，画完恢复；几何/矩阵/模板语义不变 |

> 判据：白名单里的差异都**不改变「有模板时的几何与 stencil 语义」**；一旦某处改动影响了
> 「视空间圆的顶点/半径/矩阵」或「模板值 → 像素」映射，就不再是平台降级，而是必须按 §7 改回上游。

---

## 10. 引用索引（本文所有 file:line 的出处）

### 10.1 上游 TACZ（`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/`）
- `client/renderer/scope/ScopeLensRenderer.java`：`:37-38` SEGMENTS/SURFACE_Z；`:61-70` beginFrame；`:73-131` capture；
  `:82-89` 前置闸门；`:91-95` 矩阵+Bounds；`:99-128` scissor/纹理/FBO 恢复；`:134-159` prepareCapture（含 `:151` LINEAR）；
  `:162-222` render；`:188-209` stencil+reticle；`:224-271` drawLens（`:226-231` 先拷后采）；`:273-284` buildSurface；
  `:298-307` Capture/enableStencil；`:319-343` ReticleBuffers；`:345-372` Lens；`:374-418` Bounds；`:420-510` RenderState。
- `client/model/BedrockAttachmentModel.java`：`:208-216` getBedrockPartCenter；`:240-258` renderOcularStencil；
  `:260-269` renderDivisionOnly；`:271-331` renderOcularAndDivision（`:284-301` 视空间圆、`:305-329` 遮罩/division/capture）；
  `:333-365` captureLensBounds/captureLensDivision；`:367-456` renderBoth/renderSight/renderScope；`:458-774` 加速分支。
- `client/model/bedrock/BedrockPart.java`：`:90-104` `translateAndRotateAndScale`（`x/16`）。
- `client/event/FirstPersonRenderGunEvent.java`：`:130-143` 入口；`:153-253` 定位变换（`:202` `getPositioningNodeInverse`、
  `:249-252` `T(0,1.5)·M·T(0,-1.5)`）；`:259-286` 逆变换（`:278-282` 根节点 `1.5−y/16` 规则）。
- `client/renderer/item/GunItemRendererWrapper.java`：`:173-219` renderFirstPerson（`:197-219` pose 链）；`:398-414` 相机空间旁证。
- `client/event/CameraSetupEvent.java`：`:99-120` 世界 FOV；`:122-168` 手部 FOV（`usedConfiguredFov()`）。
- `mixin/client/GameRendererMixin.java`：`:30-46` beginFrame/render 挂点。
- `util/RenderHelper.java`：`:52-77` enableItemEntityStencilTest / disable；`:89-97` getBufferSource。

### 10.2 上游 1.20.1 原版 / Forge
- `net/minecraft/client/renderer/GameRenderer.java`：`:926-948` getFov；`:995-1035` renderItemInHand；
  `:1038-1058` getProjectionMatrix；`:950-971` bobHurt。
- `net/minecraft/client/renderer/ItemInHandRenderer.java`：`:318-321` applyItemArmTransform；`:323-347` renderHandsWithItems（`:332-333` 视角延滞）。
- `net/minecraft/client/renderer/LevelRenderer.java`：`:1150-1200` renderLevel 开头；`:1368-1372`、`:1414-1448` modelview 栈的 push/apply/pop。
- `com/mojang/blaze3d/systems/RenderSystem.java`：`:877-882` applyModelViewMatrix；`:922-924` getModelViewMatrix。
- `com/mojang/blaze3d/pipeline/RenderTarget.java`：`:91-129` createBuffers（`:104-116` 过滤/wrap/RGBA8）；`:131-138` setFilterMode；
  `:172-186` bindWrite。
- `tmp/MinecraftForge-1.20.1/patches/minecraft/com/mojang/blaze3d/pipeline/RenderTarget.java.patch`：`:34-55` `enableStencil()`。
- `tmp/MinecraftForge-1.20.1/patches/minecraft/net/minecraft/client/renderer/ItemInHandRenderer.java.patch`：`:3-16` `RenderHandEvent` 位置。

### 10.3 1.7.10 原版
- `net/minecraft/client/renderer/EntityRenderer.java`：`:751-786` renderHand（`:755-770` 手部投影、`:778-786` `glLoadIdentity`）；
  `:1739-1747` setupOverlayRendering。
- `net/minecraft/client/renderer/ItemRenderer.java`：`:264-544` renderItemInFirstPerson。
- `net/minecraft/client/shader/Framebuffer.java`：`:97-136` 颜色/深度附件（`:117-131` Forge stencil 分支）；`:138-150` setFramebufferFilter。

### 10.4 本移植（`src/main/java/`）
- `com/tacz/guns/client/renderer/scope/ScopeLensRenderer.java`：`:340-366` beginFrame；`:420-529` capture；`:551-572` projectUvBounds（待删）；
  `:579-604` captureStencil；`:629-757` render；`:773-854` drawLensWithStencil（`:811-843` 待删的自愈分支）；`:860-864` drawLensFallback（待删）；
  `:876-898` copyMainColorIntoCaptureSnapshot；`:1143-1202` drawLens；`:1212-1223` drawNdcDisc；`:1234-1319` drawReticle；
  `:1432-1441` readMatrices；`:1460-1465` loadMatrices；`:1475-1578` Bounds；`:1706-1753` Lens；`:1764-1883` CaptureStencil；`:1902-2043` RenderState。
- `com/tacz/guns/client/model/BedrockAttachmentModel.java`：`:264-281` getBedrockPartCenter；`:306-332` renderOcularStencil；
  `:334-351` renderDivisionOnly；`:353-551` renderOcularAndDivision（`:382-467` 待替换）；`:584-635` renderWithoutStencil；
  `:811-845` captureLens*；`:847-894 / :896-930 / :932-975` renderBoth/renderSight/renderScope。
- `com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java`：`:421-591` onRenderHand；
  `:599-616` pushHandProjection；`:627` NEAR_PLANE；`:646-700` compensateHurtCameraEffect。
- `com/tacz/guns/client/renderer/item/AnimateGeoItemRenderer.java`：`:417-420` applyVanillaFirstPersonHandLag。
- `com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java`：`:213-244` 第一人称 pose 链。
- `com/tacz/guns/client/event/CameraSetupEvent.java`：`:103` ITEM_MODEL_FOV；`:292-333` 手部 FOV 计算。
- `com/tacz/guns/mixin/client/GameRendererMixin.java`：`:77-80` beginFrame 挂点；`:102-107` render 挂点。
- `com/tacz/guns/mcshim/PoseStack.java`：`:113-117` mulPoseMatrix（JOML post-multiply）；`com/tacz/guns/mcshim/BufferUploader.java`：`:48-88` 顶点回放；
  `com/tacz/guns/client/renderer/scope/ScopeLensProgram.java`：`:49-58` 顶点着色器（`ProjMat*ModelViewMat*Position`）。

