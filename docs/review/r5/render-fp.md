# r5 · 模型渲染 + 第一人称手持枪械 pos 专项审查（task-3）

> **写作用域**：`client/model/**`、`client/renderer/**`、`client/event/**`、`client/animation/**`、
> `client/particle/**`、`com/github/mcmodderanchor/**`、`com/maydaymemory/**`、`mixin/**`、本报告。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1）。
> **1.20 vanilla 准绳**：`tmp/minecraft-src-1.20.1/src/main/java/**`。
> **1.7.10 准绳**：`build/rfg/minecraft-src/java/**`。
> **自查**：只用 IDEA MCP（`get_file_problems(errorsOnly)` / `read_file` / `search_*`）。
> **未运行任何 gradle 任务**。改动的 4 个文件 `get_file_problems(errorsOnly)=true` → **0 error**。
>
> **不重复的历史结论**（r2/r4 已登记，本轮只做复核，见 §五）：
> 手部投影矩阵缺失（r4 A-1）、粒子双画（A-2）、`applyGunModelFovModifying` 被 bobView 闸门吃掉（A-3）、
> F1/睡觉守卫（A-4）、`isSelf` 进 `finally`（A-5）、第三人称枪口锚点插值（A-6）、
> `VertexConsumer` 顶点属性错位、GUI 图标 Y 翻转、`RenderType` 括号状态还原、
> 第三人称 2D 纸片帧补偿 `C = inv(F17·T_block)·F20`、粒子定义装载、`PausedClientAnimationClock#update()`。
> **以上逐条回代码确认仍在。**

---

## 一、结论速览

| 问题 | 结论 |
|---|---|
| **第一人称链路完整吗** | **完整**。`IItemRenderer`/`FirstPersonRenderHandler` → `AnimateGeoItemRenderer` → SBM `FirstPersonRenderHandler#onRenderHand` → `GunItemRendererWrapper#renderFirstPerson` → `FirstPersonRenderGunEvent.applyFirstPersonGunTransform` 每一跳都有真实调用点，GL 的 MODELVIEW/PROJECTION push-pop 全部成对（`try/finally`）。 |
| **`FirstPersonRenderGunEvent` 与上游有差异吗** | **没有**。归一化（去注释/去 import）后逐行逐字相同：`REFIT_OPENING_DYNAMICS`/`AIMING_DYNAMICS`/`applyShootSwayAndRotation`/`applyJumpingSway`/`getPositioningNodeInverse`（含 `scopeMountOffset` + `mountOffsetInsertIndex` 的三个符号）/`SWITCH_VIEW_DYNAMICS` 双 view 插值/`adsPath` 三轴分时/`applyAnimationConstraintTransform` 全部一致。**问题不在这一层。** |
| **最严重的问题** | **① 第一人称手臂被画成「第三人称持枪姿态」（-1.4 rad ≈ -80°）——这就是玩家报的「大半屏幕被手臂遮挡」的真因**；**② 第一人称枪的位姿随「玩家朝向」变化（r4 挂了三轮的「开镜后枪模偏向同一侧 / 常数偏移」的根因）**。两条都已修，且都是「上游有这层调用/这层插值，移植侧丢了」的类型。 |
| **计数** | **已修 2 项（P0×2）**，**复核仍在 9 项**，**未修 3 项（P1×1 / P2×2）**，**不可静态验证 4 项**。 |

**为什么前几轮没找到**：r4 已经把 `FirstPersonRenderGunEvent` 逐字节证成与上游相同，于是把偏差归给「数据侧」。
但 `applyFirstPersonGunTransform` 的**输入**（`poseStack` 基座）不对 —— 基座由 `GunItemRendererWrapper#renderFirstPerson`
的两处 1.7.10 特化产生：一处把 `xBob` 延滞写成常量 0（变成绝对角），一处漏了 `AnimationRenderContext` 作用域。

---

## 二、发现表

> 证据列：`本工程 file:line` ↔ `上游 file:line` / `1.20 vanilla file:line` / `1.7.10 file:line`。

| # | 级别 | 现象（玩家可见） | 根因 | 证据 | 状态 | 修法 |
|---|---|---|---|---|---|---|
| **F-1** | **P0** | **第一人称手臂糊满大半个屏幕 / 位置完全不对**（r4 报的「一大半屏幕被原版皮肤手臂遮挡」；r4-06 的规避办法是干脆不画手，于是变成「第一人称没有手」） | `RenderHelperCompat#renderFirstPersonArm` **没有开 `AnimationRenderContext.hand(player)` 作用域**。上游该作用域的唯一调用点就是这里，缺了它两条链路同时失效：① `InnerThirdPersonManager#setRotationAnglesHead` 的 `isFirstPersonHand` 闸门永不成立 ⇒ 画第一人称手臂时把**第三人称持枪姿态**写进共享 `ModelBiped`；② `PlayerModelMixin#setRotationAnglesTail`（把两条手臂 `rotateAngleX/Y/Z` 归零）永不执行 ⇒ 手臂带着第三人称姿态被渲染 | 本工程 `client/model/RenderHelperCompat.java:104-250`（修复处）<br>上游 `util/RenderHelper.java:113-114`（`try (PlayerModelRenderState; AnimationRenderContext.hand(player))`）<br>本工程 `client/animation/third/InnerThirdPersonManager.java:25`（闸门）<br>本工程 `mixin/client/PlayerModelMixin.java:71-85`（归零，死代码）<br>本工程 `api/client/other/ThirdPersonManager.java:34-37`（`rotateAngleX = -1.4F`、`rotateAngleY = -0.3F/0.8F`）<br>1.7.10 `client/model/ModelBiped.java:116-176` | **已修** | `renderFirstPersonArm` 里加 `try (PlayerModelRenderState state = new PlayerModelRenderState(armModel); AnimationRenderContext scope = AnimationRenderContext.hand(player))`（与上游同形；顺带把自造的 `tacz$saveBipedPose/Restore` 换成上游同形的 `PlayerModelRenderState`，多覆盖 `heldItemLeft/Right`、`aimedBow`、`isSneak/isRiding`、`offset*/showModel`）。`RENDER_FIRST_PERSON_VANILLA_ARM` 恢复 `true`（kill switch 保留） |
| **F-2** | **P0** | **第一人称枪（含瞄具）的位姿随「玩家朝向」变化**：面朝不同方向时枪在屏幕上的横向/俯仰偏移不同 ⇒ 被读成「开镜后枪模偏到同一侧、方向固定」（r4 U2 / 现象 #3 挂了三轮） | `GunItemRendererWrapper#renderFirstPerson` 把上游的 `xBobO/xBob`、`yBobO/yBob` 延滞量写成常量 `0`（理由写的是「1.7.10 没有可读字段」，**该判断是错的**）。于是 `xRot/yRot` 从「最近一小段时间的 pitch/yaw 变化量」退化成**绝对** pitch / yaw（yaw ∈ ±180、pitch ∈ ±90），再被 `×-0.1`（poseStack 旋转）、`tanh(x/25)*25 ×0.1/16/3`（root 平移）、`×0.05°`（root 四元数）四处放大成**与朝向绑定的常数偏移**（yaw 方向最多 ≈18° 整体旋转） | 本工程 `client/renderer/item/GunItemRendererWrapper.java:215-228`（修复处）<br>同型同缺陷 `client/renderer/item/AnimateGeoItemRenderer.java:509-522`（修复处）<br>上游 `GunItemRendererWrapper.java:199-213` / `AnimateGeoItemRenderer.java:220-234`<br>1.7.10 `client/entity/EntityPlayerSP.java:70-73`（4 个 `public float`）+ `:98-101`（一阶滞后 **0.5**）<br>1.20 `client/player/LocalPlayer.java:633-636`（`xBob += (getXRot()-xBob)*0.5F`，**同为 0.5**） | **已修** | `xRotOffset = Mth.lerp(partialTick, player.prevRenderArmPitch, player.renderArmPitch)`，`yRotOffset` 同理取 `prevRenderArmYaw / renderArmYaw`。**这是逐值等价替换，不改任何观感常数** |
| **F-3** | P1 | 装瞄具开镜时枪模位置对不上（`mountInsert=-1`、`path` 只有 `iron_view`） | 代码侧已证与上游逐字相同；日志显示当时 `scopeId=tacz:empty`（没装镜），装镜样本至今未采到 | 本工程 `client/event/FirstPersonRenderGunEvent.java:307-362`（与上游 `:163-201` 逐字相同）；r4-09 日志 | **未修（数据/日志侧）** | 需要一次 `scopeId` 非空的日志（见 §六判读表）。**不改代码** |
| **F-4** | P2 | `FirstPersonRenderHandler#onRenderHand` 的 3 条早退分支（`inst==null` / 空栈 / 无渲染器）**不调用 `renderParticlesIfAny`**，上游调用 | 移植时早退分支只 `endBatch + return`。上游会让第一人称粒子继续走完生命周期 | 本工程 `.../FirstPersonRenderHandler.java:616-632`<br>上游 `.../FirstPersonRenderHandler.java:364-380`（每处都 `renderParticlesIfAny(event)` 后 return） | **未修（无可观测差异）** | 这三条分支对枪械不可达（枪一定有 instance + 渲染器），且 `finishTransition` 已 `discardEmitters`；改了只是「更保真」，观测不到。留给后续 |
| **F-5** | P2 | 组合镜镜内仍能看到枪体 | 1.7.10 主 framebuffer 默认无 stencil 附件（`ForgeHooksClient` 的 `stencilBits` 默认 0） | 本工程 `client/model/BedrockGunModel.java:466-499`（r3-4 已完整登记） | **未修（= r3-4，平台限制）** | 零代码缓解：`-Dforge.forceDisplayStencil=true` |

---

## 三、已修清单（逐条说明改了什么）

### 1. `src/main/java/com/tacz/guns/client/model/RenderHelperCompat.java` —— F-1（P0）

- `renderFirstPersonArm` 用 `try (PlayerModelRenderState; AnimationRenderContext.hand(player))` 包住
  「`setRotationAngles` → 打诊断 → 兜底判断 → GL 设置 → `armRenderer.render` → GL 还原」。
  作用域用 `try-with-resources` 而不是 `finally`，因此贴脸兜底的提前 `return` 也会正确还原姿态。
- 删除自造的 `tacz$saveBipedPose` / `tacz$restoreBipedPose`（只快照 7 个 part × 6 个 float + `onGround`），
  改用**仓内已有**、且与上游同形的 `client/animation/third/PlayerModelRenderState`
  （多覆盖 `isSneak/isRiding/isChild/heldItemLeft/heldItemRight/aimedBow` + `offset*/showModel/isHidden`）。
  该类的 `captureModelState` 是幂等 no-op（无 PlayerAnimator 库），无风险。
- `RENDER_FIRST_PERSON_VANILLA_ARM`：`false` → **`true`**。r4-06 置 false 的依据是那段被误读的日志，
  根因既已修好，就恢复上游「画皮肤手臂」的行为；置 false 仍是单点 kill switch。
- `[TACZ-DBG] arm` 的判读注释改成正确口径（`rotateAngle*` 单位是**弧度**）。

### 2. `src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java` —— F-2（P0）

`renderFirstPerson` 里两行 `xRotOffset/yRotOffset = 0.0F` 改成
`Mth.lerp(partialTick, player.prevRenderArmPitch, player.renderArmPitch)` /
`Mth.lerp(partialTick, player.prevRenderArmYaw, player.renderArmYaw)`。
之后的 `×-0.1F` 旋转与 `tanh/offset/quaternion` 四行**一行未改**（它们是上游的常数，不动）。

### 3. `src/main/java/com/tacz/guns/client/renderer/item/AnimateGeoItemRenderer.java` —— F-2（同型）

基类 `renderFirstPerson` 有完全相同的两行与相同的错误 TODO。虽然它对枪械不可达
（被 `GunItemRendererWrapper#renderFirstPerson` 覆盖），但对其他 `AnimateGeoItemRenderer` 子类可达，
且留着会继续误导下一轮，故一并改成同一修法。

### 4. `src/main/java/com/tacz/guns/client/model/BedrockAnimatedModel.java` —— 文档纠正

`hasBuiltinHandModel()` 的 javadoc 仍写着「因此本移植改成数据驱动：模型自带手就不挂皮肤手臂」，
而 r4-04 已回退该行为。改成「r4-04 起不再用于渲染决策；r5 已定位真因是 `AnimationRenderContext` 作用域缺失」。

---

## 四、未修清单（+ 需谁配合）

| # | 级别 | 项 | 修法 | 需要谁配合 |
|---|---|---|---|---|
| F-3 | P1 | 装镜 ADS 定位（`scopeId` 非空样本从未采到） | 无需改代码；按下表判读一次日志 | **用户实机**：装 4× 镜 → 按住右键开镜 1 秒 → `grep "\[TACZ-DBG\] scope"` |
| F-4 | P2 | 早退分支不画粒子 | 三处 `return` 前补 `renderParticlesIfAny(hand, poseStack, bufferSource, packedLight, event.partialTicks)` | 无（`com/github/mcmodderanchor/**` 在本作用域内）；但观测不到差异，建议不改 |
| F-5 | P2 | 组合镜 stencil 恒失效 | r3-4 已登记；需自建 `GL_DEPTH24_STENCIL8` FBO，与 Angelica/光影冲突 | 产品裁决（不建议） |

**顺手复核但没改的既有登记项**（与上游同构 / 有意为之，不要当 bug）：
- `getPath(null)` → 单位矩阵（缺 node 的枪「贴在原点」）—— 与上游同构（r4 E-1）。
- `ItemInHandLayerMixin#renderTail` 无条件清 `isSelf`（r4 F-1）—— 与上游 `ItemInHandLayer#render` TAIL 同序，且其后的
  `HumanoidOffhandRender.renderGun` 本就不该有火光/弹壳。
- `RenderType.entityCutout/entityTranslucent` 的 `cull=false`（r2 有意偏离，安全超集）。
- `BedrockPart#translateAndRotateAndScale` 的 `offset → pos/16 → Rz→Ry→Rx → quaternion → scale`、
  `BedrockAnimatedModel#cleanAnimationTransform` 的复位集合 —— 与上游逐行相同。

---

## 五、复核清单（历史结论是否仍在）

| 历史项 | 位置 | 结论 |
|---|---|---|
| r4 A-1 手部投影矩阵 | `FirstPersonRenderHandler#pushHandProjection:753-770` + `finally:727-730` | ✅ 在且 `push/pop` 成对（`projectionPushed` 标志位保护异常路径） |
| r4 A-2 粒子双画 | 同文件 `:600-607` 只剩事件末尾带闸门的那一处 | ✅ 仍在 |
| r4 A-3 手部 FOV 闸门 | `CameraSetupEvent.java:211-220`（在 `!viewBobbing` / `player==null` 早退之前） | ✅ 仍在 |
| r4 A-4 F1/睡觉守卫 | `FirstPersonRenderHandler.java:490-492` | ✅ 仍在（`return` 不 cancel） |
| r4 A-5 `isSelf` 进 finally | `GunItemRendererWrapper.java:287-318` | ✅ 仍在 |
| r4 A-6 第三人称枪口锚点插值 | `GunItemRendererWrapper.java:494-527` | ✅ 仍在（`lastTickPos* + Δ*partialTick`） |
| r4 时钟（`PausedClientAnimationClock#update()`） | `FirstPersonRenderHandler.java:401-402`（`shouldTick()` 早退之前） | ✅ 仍在；`AnimationClocks.client()` 全仓仍只有 `FirstPersonRenderHandler:73` 与 `WorldEmitterManager:54` 两个消费者 |
| r2 GUI 图标 Y 翻转 | `RenderHelperCompat#applyGuiSlotScale:629-631` = `scale(16,-16,16)·translate(0,-1,0)` | ✅ 仍在，4 个 `INVENTORY` 调用点在 |
| r2 `RenderType` 括号状态还原 | `mcshim/RenderType.java`（不在本作用域，只读复核） | ✅ 仍在（`setup` 快照 / `clear` 还原） |
| 第三人称纸片帧补偿 | `AnimateGeoItemRenderer.java:304-347`（`C = inv(F17·T_block)·F20`，单项开关 `ENABLE_EQUIPPED_PAPER_FRAME_FIX`） | ✅ 仍在，与 r4-06 独立数值验算（`F·C=F20`，误差 1.2e-16）一致 |
| 模型渲染 GL/UV/挂点 | 见 §六覆盖清单 | ✅ 未发现新增结构性缺陷 |

---

## 六、覆盖清单（诚实）

### 逐行 / 逐段读过（本轮）

- **event**：`FirstPersonRenderGunEvent.java`（769 行，全读 + 与上游 478 行做**去注释/去 import 的逐行集合比对**）、
  `CameraSetupEvent.java`（575 行，全读 + 与上游 275 行逐段对照）、`FirstPersonRenderEvent.java`（124 行，全读）、
  `TickAnimationEvent.java`（115 行，全读 + 与上游归一化 diff）。
- **renderer/item**：`GunItemRendererWrapper.java`（573 行，全读 + 与上游 423 行逐段对照）、
  `AnimateGeoItemRenderer.java`（700 行，全读）、`GunItemTransforms.java`（逐行=上游）、
  `SbmGunTransforms`（只查引用面）、`ExternalGunItemVboRenderer`（只查 `UNSUPPORTED` 语义）。
- **model**：`BedrockGunModel.java`（657/482 行，全读）、`BedrockAnimatedModel.java`（265 行，全读）、
  `bedrock/BedrockModel.java`（576/440，全读 `load*/render/getPath`）、`bedrock/BedrockPart.java`（逐行=上游）、
  `RenderHelperCompat.java`（657 行，全读，**本轮主要修改点**）、`FunctionalBedrockPart.java`、
  `functional/{MuzzleFlashRender,ShellRender,AttachmentRender,RightHandRender,LeftHandRender}.java`（全读 + 归一化 diff）。
- **animation**：`third/{InnerThirdPersonManager,AnimationRenderContext,PlayerModelRenderState}.java`（全读）、
  `screen/RefitTransform.java`（逐行=上游）、`api/client/animation/statemachine/AnimationStateMachine.java`（全读）、
  `util/time/GunTime.java`（202 行，全读）。
- **particle**：`MuzzleParticleManager.java`（794 行，全读）、`ThirdPersonMuzzleParticleManager.java`（231 行，全读）、
  `ParticleDefinitionReloader.java`（87 行，全读）。
- **mixin/client**：`GameRendererMixin.java`（484 行，全读）、`ItemInHandLayerMixin.java`（215 行，全读）、
  `ItemInHandRendererMixin.java`（164 行，全读）、`PlayerModelMixin.java` / `HumanoidModelMixin.java`（全读）。
- **SBM vendor**：`v1/client/handler/FirstPersonRenderHandler.java`（1185 行，全读）、
  `v1/client/animation/PausedClientAnimationClock.java`（62 行，全读）。
- **1.7.10**：`EntityRenderer.java`（`setupCameraTransform:655-746`、`renderHand:751-814`、`renderWorld:1320-1449`、
  `enableLightmap/disableLightmap:819-848`）、`ItemRenderer.java`（`renderItemInFirstPerson:264-333/500-554`、
  `updateEquippedItem:720-759`）、`RenderPlayer.java:455-462`、`ModelBiped.java:110-200`、
  `EntityPlayerSP.java:60-102`、`ForgeHooksClient.java`（`renderFirstPersonHand:264-266`）。
- **1.20 vanilla**：`ItemInHandRenderer.java:322-347`、`LocalPlayer.java:626-642`、`PlayerRenderer.java:187-212`。

### 只抽查 / 没读（诚实列出）

1. **`client/renderer/scope/**`（5 文件）**：只核了 `ScopeLensRenderer.beginFrame/render/capture` 的调用点与
   GL 状态成对性（`compensateScopeLenses` 补偿链完好），**GLSL 数学未逐行对照上游**（与 r2 的诚实声明一致）。
2. **`client/renderer/block/**`（12 文件）与 `client/renderer/external/**`**：只核了 r2 已确认的
   `try/finally` 成对性与「`ExternalGunItemVboRenderer` 恒 UNSUPPORTED ⇒ LOD 路径不可达」，未逐行重读。
3. **`client/model/functional/{BeamRenderer,RangefinderImpactPredictionRender,TextShowRender}.java`**：
   做了「上游独有行 / 移植独有行」集合比对（结论：差异全部是 `Tessellator`↔`VertexConsumer`、
   `RenderType.energySwirl`↔固定管线的平台替换），**没有逐行核几何数值**。
4. **`client/model/BedrockAttachmentModel.java`（498 行）**：只核了 `ScopeLensRenderer.capture` 的挂点与
   `renderOcularStencil` 段落存在性，未逐行读（r2/r3 已做过 stencil 结论）。
5. **`client/particle/{BulletHoleParticle,AmmoParticleSpawner,HitSurfaceParticleSpawner,HitBlockEffectHandler,ParticleCompat}.java`**：
   沿用 r2 §4.5 的逐文件结论，本轮未重读。
6. **枪包 Lua 状态机脚本本体**（`assets/tacz/custom/**/scripts/*.lua`）未审 —— 静态审查只能证明上下文 API 齐备
   与状态机被驱动，不能证明某个分支的观感。
7. **`com/maydaymemory/**`（mae 动画库）**：只核了 `ObjectAnimationRunner` 的时间源是 `GunTime`（不是 SBM 时钟），
   插值数学未逐行读（上游同库，vendor 后未改）。

---

## 七、不可静态验证项（必须实机，含判读方法）

| # | 项 | 判读方法 |
|---|---|---|
| **V-1** | **F-1 的手臂姿态修复**（本轮最重要的观感项） | 持枪站定 → 手臂应是**沿枪的握把方向**从骨骼锚点伸出，**不再朝相机方向横插**。打开 `RenderHelperCompat.DEBUG_RENDER=true` 后 `grep "\[TACZ-DBG\] arm"`：`rot=` 应≈`(0.000, 0.000, 0.000)`；**若仍看到 `rot=(-1.400, -0.300, …)` ⇒ `AnimationRenderContext` 作用域又失效了**。若姿态正确但手臂位置/长度仍不对，才需要考虑 `MIN_HAND_ANCHOR_DISTANCE`（当前 0 = 关闭）。一键回退：`RENDER_FIRST_PERSON_VANILLA_ARM = false`（回到「无手」） |
| **V-2** | **F-2 的朝向依赖偏移** | 站定，转向 4 个方位（正北/东/南/西）各看一次，再抬头/低头到极限：第一人称枪在**屏幕上的位置和角度不应随朝向变化**（静止时应当与朝向完全无关）。开镜后同理。`grep "\[TACZ-DBG\] ads"` 的 `pose=(x,y,z)`：同一把枪、完全不同朝向下，完全开镜的 `pose` 应**数值相同**（此前会随 yaw 变化）。若仍有随朝向的偏移，则退回 `applyFirstPersonPositioningTransform` 的输入侧（`adsPath` / 枪包数据），不是这两行 |
| **V-3** | **F-3 装镜 ADS 定位** | 装 4× 镜 → 按住右键 1 秒 → `grep "\[TACZ-DBG\] scope"`。判读：`scopeId` 非空 + `hasIdx=false` ⇒ 资源加载侧；`hasIdx=true + model=false` ⇒ 瞄具模型没加载（只能对齐 `scope_pos` 安装点 ⇒ 恒定方向偏移）；`model=true + scopePos=false` ⇒ 该枪模型缺 `scope_pos` 骨骼；`path` 里应出现 `scope_view_N`（`N = viewIndex + 1`） |
| **V-4** | **手臂光照**（`RenderHelper.enableStandardItemLighting` 的帧内位置） | 修 F-1 后手臂会真的被画出来。若手臂呈现「满亮平贴图无方向光」，再加/减灯（`renderFirstPersonArm` 内 `enableStandardItemLighting/disable` 已按 vanilla `ItemRenderer:272/:553` 成对放置）；方向对不对只能看截图 |

---

## 八、本轮改动的文件

1. `src/main/java/com/tacz/guns/client/model/RenderHelperCompat.java`
2. `src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java`
3. `src/main/java/com/tacz/guns/client/renderer/item/AnimateGeoItemRenderer.java`
4. `src/main/java/com/tacz/guns/client/model/BedrockAnimatedModel.java`（仅文档纠正）

全部 `get_file_problems(errorsOnly=true)` = **0 error**；**未运行 gradle**。
