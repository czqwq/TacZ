# r4-01 · 第一人称手持枪械 · 渲染 / 动画 / model 移植完整性审查（模块 A）

> **写作用域**：`client/renderer/item/**`、`client/model/**`、`client/event/{FirstPersonRenderGunEvent,CameraSetupEvent}.java`、
> `client/animation/statemachine/**`、`client/animation/third/**`、
> `com/github/mcmodderanchor/simplebedrockmodel/v1/client/**`、`mixin/client/**`、本报告。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47 / Java 17）。
> **1.7.10 准绳**：`build/rfg/minecraft-src/java/**`（原版 + Forge 反编译源码）。
> **自查**：只用 IDEA MCP（`get_file_problems(errorsOnly)` / `lint_files(min_severity=error)` / `search_*` / `read_file`）。
> **本轮未运行任何 gradle 任务**（含 `compileJava`）；构建由 Lead 在全部 teammate 收工后统一执行。
> 改动的 3 个文件 `lint_files(error)` = **0 error**。
>
> **不重复的历史结论**（r2 / r3 / 第四轮已修且本轮复核仍在，未再报为新发现）：
> `VertexConsumer` 顶点属性错位、第一人称相机空间基座（`glPushMatrix+glLoadIdentity`）、背包图标 16× 与 Y 翻转、
> 第三人称不再画第一人称枪、第一人称吃 lightmap、第一人称吃 `hurtCameraEffect`、镜片 texture unit 1/2 与矩阵还原、
> 切枪清 `display/gunData`、`RenderType.clearRenderState` 快照还原、`FirstPersonRenderHandler#registerRenderer`
> 全仓 0 调用点（r3-07）、`CameraSetupEvent` 每帧丢弃鼠标位移（r3-07）、切枪 `newMain.copy()` NPE（r3-08）、
> `static @SubscribeEvent` + 实例注册 ICCE（r3-06）、粒子定义从不装载、镜片 GLSL ASCII。
> 以上逐条回代码确认**修复仍在**（见 §四）。

---

## 一、结论速览

| 问题 | 结论 |
|---|---|
| **能不能渲染** | **能**。链路 A 的每一跳都有真实调用点（§二 A）。第一人称枪会画、会动、会跟随视角。 |
| **链路是否完整** | **完整**（`IItemRenderer` → `GunItemRendererWrapper` → `AnimateGeoItemRenderer` → SBM `FirstPersonRenderHandler` → `IFPGeoItemRenderer#renderFirstPerson` → `BedrockGunModel` + `functional/**`），**并且与上游语义等价**（除了 1.7.10 无双持 / 无 `RenderHandEvent` 载荷这两个已登记的平台差异）。 |
| **最严重的问题（已修）** | **第一人称手部投影矩阵从未建立**：SBM 在 `RenderHandEvent` 里 cancel 掉了整个 `EntityRenderer#renderHand`，而**手部 FOV 的投影矩阵正是在 `renderHand` 内部创建的**（`EntityRenderer.java:755-770`）⇒ 第一人称枪一直在用**世界 FOV 投影**绘制。腰射时两者都≈70°看不出问题；**开镜时世界 FOV 被压到 `70/zoom`，枪体被整体放大 ~zoom 倍**，并且 `GameRendererMixin#tacz$overrideHandFov`（`ITEM_MODEL_FOV` 的唯一消费者）**整条是死代码**。已修。 |
| 次严重（已修） | 第一人称枪口火光/枪烟**每帧被画两遍**（同一 `BufferSource`、同一 `RenderType`，顶点翻倍）⇒ 火光过曝、烟雾浓度翻倍。已修。 |
| 计数 | **已修 6 项（P0×1 / P1×3 / P2×2）**、**未修 5 项（P1×1 / P2×4，其中 1 项需跨域）**。 |

---

## 二、发现表

> 证据列格式：`本工程 file:line` ↔ `上游 file:line` 或 `1.7.10 build/rfg/minecraft-src/... file:line`。
> 行为列写「玩家看到什么」。

| # | 级别 | 现象（玩家可见） | 根因 | 证据 | 状态 | 修法 |
|---|---|---|---|---|---|---|
| **A-1** | **P0** | **开镜时第一人称枪体（含瞄具）被整体放大到几乎糊满屏幕，且缩放幅度随瞄具倍率变化；腰射正常。** 同时「手部 FOV / `model_fov` / `views_fov`」这一整套枪包数据对第一人称**完全没有作用** | SBM 的 `onRenderHand` 把 `RenderHandEvent` cancel 掉，而 1.7.10 的**手部投影矩阵是在被 cancel 掉的 `EntityRenderer#renderHand` 里建的**（`glLoadIdentity(); Project.gluPerspective(getFOVModifier(p,false), …)`）。cancel 后 GL_PROJECTION 仍是 `setupCameraTransform` 用**世界 FOV** 建的那一份 ⇒ 枪体按世界 FOV 投影；`GameRendererMixin#tacz$overrideHandFov`（把 `ITEM_MODEL_FOV` 写进 `getFOVModifier(...,false)` 返回值）因此**在持枪（= 唯一有意义的场合）时永不执行**（该函数的手部调用点只有 `renderHand:770` 一个，而它被跳过） | 本工程 `simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java:536,714-733`（修复处）<br>1.7.10 `build/rfg/minecraft-src/java/net/minecraft/client/renderer/EntityRenderer.java:1434-1437`（cancel 后 `renderHand` 整段跳过）、`:755-756` `:770`（手部投影在此建立）、`:473`（`getFOVModifier` 定义）、`:673`（世界投影）<br>本工程 `mixin/client/GameRendererMixin.java:423-429`（此前是死代码）<br>上游 `client/event/CameraSetupEvent.java:122-169`（手部 FOV 是独立 `ComputeFov` 通道） | **已修** | 在 `onRenderHand` 里照抄 vanilla `:755-770`：`GL_PROJECTION` push → `LoadIdentity` → `Project.gluPerspective(CameraSetupEvent.ITEM_MODEL_FOV, displayWidth/displayHeight, 0.05F, (renderDistanceChunks*16)*2)` → finally 成对 pop。只 push/pop，不永久改状态 |
| **A-2** | **P1** | 开火时**枪口火光明显过曝、枪烟明显更浓**；`renew` 型枪（默认包全部）的粒子在枪口位置出现「两层」 | 第一人称粒子每帧被渲染**两次**：`onRenderHand` 的 try 块开头有一处**无条件**的 `renderParticlesIfAny(...)`，而 `renderFirstPerson` 内部（枪体模板窗口）又调了一次 `FirstPersonRenderHandler.renderParticlesNow`。两批顶点进**同一个** `BufferSource`/`RenderType`，一起 flush | 本工程（修前）`FirstPersonRenderHandler.java:512` 与 `:609-612`（带闸门的那一处）<br>本工程 `client/renderer/item/GunItemRendererWrapper.java:258-263`（模型窗口内再画一次）<br>本工程 `simplebedrockmodel/v1/particle/render/ParticleRenderer.java:46-93`（`render` 只读粒子、不消费 ⇒ 可重复调用）<br>契约：本工程 `.../renderer/IFPGeoItemRenderer.java:140-154`（「返回 true 后 FirstPersonRenderHandler 将**跳过事件末尾**的自动粒子渲染」） | **已修** | 删掉 try 块开头那处无条件调用；**只保留**事件末尾带 `renderParticlesInModelWindow()` 闸门的那一处（与 `IFPGeoItemRenderer` 的契约文字一致） |
| **A-3** | **P1** | 玩家在「选项 → 视角摇晃」关闭（`viewBobbing = false`）时，**开镜后手部模型 FOV 永远停在 70°**：枪/瞄具不缩放、瞄具目镜与眼睛的相对位置错；配合 A-1 修复后这会让手部投影恒定 70°（相当于 `model_fov` 数据全废） | 上游 `applyGunModelFovModifying(ComputeFov)` 是**独立订阅者**，与 `bobView` 无关；移植把它塞在 `applyLevelCameraAnimation` 的**末尾**，于是继承了该方法的 `!gameSettings.viewBobbing` 早退和 `player == null` 早退 ⇒ 闸门关掉后 `ITEM_MODEL_FOV` 再也没被写过 | 本工程 `client/event/CameraSetupEvent.java:196-205`（修复处）<br>上游 `client/event/CameraSetupEvent.java:67-70`（`applyLevelCameraAnimation` 有 bobView 闸门）vs `:122-169`（`applyGunModelFovModifying` **无**闸门） | **已修** | 把 `applyGunModelFovModifying(player, partialTick)` 调到所有闸门**之前**（`RenderTickEvent.START` 里无条件每帧算一次） |
| **A-4** | **P1** | 按 **F1（隐藏 GUI）** 时第一人称**仍然画着整支枪**（原版不画手）；**睡在床上**时视角里浮着一把枪 | 1.7.10 原版把「画第一人称手持物」这件事写在 `renderHand` 的 `if (thirdPersonView == 0 && !isPlayerSleeping() && !hideGUI && !enableEverythingIsScrewedUpMode())` 里；本 handler 取代了这段判断但只判了 `thirdPersonView` | 本工程 `FirstPersonRenderHandler.java:451-453`（修复处）<br>1.7.10 `EntityRenderer.java:794`<br>上游 1.20 `GameRenderer#renderItemInHand` 的首行 `if (!this.minecraft.options.hideGui)` | **已修** | 补 `hideGUI` 与 `isPlayerSleeping()` 两条守卫（`return` 而**不** cancel ⇒ 原版 `renderHand` 照常跑完、overlay 与状态最干净） |
| **A-5** | **P2** | 极端情况下「第一人称枪口火光/弹壳突然出现在**别人**的第三人称枪上」或「打开改装界面后**手臂永久消失**」或「后续第一人称渲染整体错位」 | `GunItemRendererWrapper#renderFirstPerson` 里 `MuzzleFlashRender.isSelf` / `ShellRender.isSelf`（**全局静态**，YSM 也会写）/ `gunModel.setRenderHand(false)` / `poseStack.pushPose()` 的复位都**不在 `try/finally`** 里；`gunModel.render` 会走到 Lua 状态机 / `IFunctionalRenderer` / 粒子系统，任何一处抛异常就永久跳过复位 | 本工程 `client/renderer/item/GunItemRendererWrapper.java:269-313`（修复处）<br>静态字段消费点：`client/model/functional/MuzzleFlashRender.java:187-195`、`ShellRender.java:188` | **已修** | 把 `setRenderHand` / `popPose` / `cleanAnimationTransform` / 两个 `isSelf = false` 全部移进 `finally`（与 r2 对 `ItemPreviewRenderContext.alpha` 的同类修复一致） |
| **A-6** | **P2** | 边走/边转身开火时，**第三人称**的枪口粒子/曳光弹起点在两帧之间抖动（最多落后 1 tick） | `captureThirdPersonMuzzle` 直接读 `renderViewEntity.posX/posY/posZ` 与 `rotationYaw/rotationPitch`（**未插值**）；上游读的 `Camera#getPosition()/getYRot()/getXRot()` 全部是 `partialTicks` 插值量 | 本工程 `GunItemRendererWrapper.java:472-520`（修复处）<br>上游 `GunItemRendererWrapper.java:398-414`（`camera.getPosition()/getYRot()/getXRot()`）；1.20 `Camera#setup` 用 `Mth.lerp(partialTicks, entity.xOld, entity.getX())` 与 `entity.getViewYRot(partialTicks)` | **已修** | 位置与朝向都改成 `lastTickPos* + (pos* − lastTickPos*) * partialTick`（`partialTick` 取 `ClientTickHelper.getPartialTick()`），yaw/pitch 同步插值，实体改用 `renderViewEntity`（= 上游 `Camera` 的实体） |
| **B-1** | P2 | 第一人称**手臂**看起来是「满亮平贴图、没有方向光」 | 1.7.10 原版在 `renderItemInFirstPerson` 开头调 `RenderHelper.enableStandardItemLighting()`（`:272`）、结尾 `disableStandardItemLighting()`（`:553`），手臂就是在这个光照下画的。本 handler cancel 掉 `renderHand` ⇒ 这两句都不执行；`RenderHelperCompat#renderFirstPersonArm` 自己也**没有**补 | 本工程 `client/model/RenderHelperCompat.java:103-147`（缺 enable/disable）<br>1.7.10 `ItemRenderer.java:272` `:543` `:553` | **未修**（见 §五） | 在 `renderFirstPersonArm` 的 `poseStack.pushToGL()` **之前** `RenderHelper.enableStandardItemLighting()`、`finally` 里 `disableStandardItemLighting()`（灯位置会落在相机空间，等价 vanilla 的「在 pitch/yaw 旋转帧内设灯」）。**刻意未在静态审查里落地**：观感方向无法静态判定，需实机取一张手臂截图对比后再决定 |
| **C-1** | P2 | 断线 / `/tacz reload` 后重新进世界，第一次渲染可能凭空放一次「上一次开火的枪口火光」 | `MuzzleParticleManager` 的 `PENDING`（待创建请求）/ `BOUND`（已绑定发射器）/ `BASE_POSE` 三个静态表**没有任何清理入口**，而 `FirstPersonRenderHandler#reset()`（断线 + 资源重载都会调）只清 SBM 自己的 `PARTICLE_SYSTEM` 与两手状态 | 本工程 `client/particle/MuzzleParticleManager.java:126-130`（三张静态表，全仓无 `reset/clear`）<br>`FirstPersonRenderHandler.java:155-164`（`reset()`）<br>`client/resource/ClientIndexManager.java:128`（reload 调 `reset()`） | **未修**（跨域，见 §五） | 给 `MuzzleParticleManager` 加 `public static void reset()`（清三张表 + `resetMuzzleLight()`），在 `FirstPersonRenderHandler.reset()` 末尾调用。**`client/particle/**` 不在本 teammate 写作用域** ⇒ 已报 Lead |
| **E-1** | P2 | 缺 bone/node 的枪（第三方枪包）第一人称枪「贴脸/在原点」而不是「无模型」 | `BedrockAnimatedModel#getPath` 对缺失 node 返回 `null`，`getPositioningNodeInverse(null)` 返回**单位矩阵** ⇒ 无定位变换 | 本工程 `client/model/BedrockAnimatedModel.java:66,191-193`、`client/event/FirstPersonRenderGunEvent.java:325-353`<br>上游 `client/model/BedrockAnimatedModel.java`（同名逻辑）、上游 `FirstPersonRenderGunEvent.java:202` | **未修（与上游同构，非移植缺陷）** | 不改（保真）。若要改进需产品裁决 |
| **F-1** | P2 | （当前无实际症状）`ItemInHandLayerMixin#renderTail` **无条件**把 `MuzzleFlashRender.isSelf`/`ShellRender.isSelf` 置 `false` | 若将来出现「第三人称渲染嵌套在第一人称枪渲染内部」的路径，会把第一人称的置位误清 | 本工程 `mixin/client/ItemInHandLayerMixin.java:122-127`、`client/renderer/item/GunItemRendererWrapper.java:269-270` | **未修（登记）** | 改成「保存-恢复」而非「无条件置 false」（`@Unique` 字段记录进入时的值）。当前 `RenderPlayer#renderEquippedItems` 与第一人称 pass 不嵌套，故无现网影响 |

---

## 三、逐条问题域回答（A–F）

### A. 链路完整性 —— **完整，每一跳都有真实调用点**

| 跳 | 调用点（本工程 `file:line`） | 与上游同一跳的语义差 |
|---|---|---|
| ① 注册 | `client/init/ClientSetupEvent.java:294` `MinecraftForgeClient.registerItemRenderer(item, renderer)` | 上游是各 `Item#initializeClient` 挂 BEWLR（上游 `AbstractGunItem`/`AmmoItem`/`AttachmentItem`/`GunSmithTableItem`）；1.7.10 无该扩展点 ⇒ 显式注册。**语义等价** |
| ② 第一人称渲染器登记 | `ClientSetupEvent.java:303-306` → `FirstPersonRenderHandler.registerRenderer(item, fpRenderer)` | 上游走 `IClientItemExtensions.of(item).getCustomRenderer()`。**语义等价**（r3-07 修复，现仍在） |
| ③ 事件入口 | `FirstPersonRenderHandler.java:65-71` 静态块把**实例**注册到 `FMLCommonHandler.instance().bus()` **和** `MinecraftForge.EVENT_BUS`；`RenderHandEvent` 属 `net.minecraftforge.client.event.*` ⇒ Forge 总线 ✅；`TickEvent.*`/`FMLNetworkEvent.*` ⇒ FML 总线 ✅；5 个 `@SubscribeEvent` **全部是 `public` 非 `static` 实例方法**（`:151,167,172,365,426`）✅ | 上游 `@Mod.EventBusSubscriber(Dist.CLIENT)` |
| ④ 分派证据 | `AnimateGeoItemRenderer.java:52-53` `implements IFPGeoItemRenderer, IItemRenderer`；`IItemRenderer` 入口 `:86-100` `renderItem(...)` → `renderByItem(...)`；第一人称经 `:425-426` 的 `onRenderHand` → `:652` `renderer.renderFirstPerson(...)` | 上游 `BlockEntityWithoutLevelRenderer` |
| ⑤ 第一人称绘制 | `client/renderer/item/GunItemRendererWrapper.java:189` `renderFirstPerson(player, stack, ctx, poseStack, …)` | **逐行等价**（与上游 `:173-272` 归一化 diff 仅剩：`xBobO/yBobO` 无对应字段已登记 TODO、`Mth.lerp` 由 double 重载改 float 重载） |
| ⑥ 模型 + functional | `GunItemRendererWrapper.java:299-300` `gunModel.render(...)` → `client/model/bedrock/BedrockModel.java:514-539`；functional 挂点 `client/model/BedrockGunModel.java:87-91`（`LeftHandRender`/`RightHandRender`/`MuzzleFlashRender`）、`:175-177`（`ShellRender`）、`:195-207`（`AttachmentRender`）、`:319`（`TextShowRender`）、`:326`（`RangefinderImpactPredictionRender`）、`:396-398`（`BeamRenderer` 激光） | **等价**；`AcceleratedRendering` 加速路径按已确认决策降级（`BedrockGunModel.java:400-413` 恒走非加速路径） |

**F 的双重绘制复核**：`client/event/FirstPersonRenderEvent.java:62` 的 `onRenderHand(RenderHandEvent)` **没有 `@SubscribeEvent`** ——
与上游 `tmp/TACZ-1.20.1/.../FirstPersonRenderEvent.java:23`（`//    @SubscribeEvent` 注释掉）**完全一致**，
因此 `ClientProxy` 里那个注册是空转、**不会**与 SBM handler 双重绘制。✅

### B. 逐帧变换链等价性

| 环节 | 结论 |
|---|---|
| `FirstPersonRenderGunEvent`（`applyFirstPersonGunTransform`） | 与上游**归一化 diff 后语义 0 差异**：`REFIT_OPENING_DYNAMICS` / `AIMING_DYNAMICS` / `applyShootSwayAndRotation` / `applyJumpingSway` / `getPositioningNodeInverse`（含 `scopeMountOffset` + `mountOffsetInsertIndex`）/ `SWITCH_VIEW_DYNAMICS` 双 view 插值 / `adsPath` 三轴分时 / `applyAnimationConstraintTransform`（含 `mulDirection`、`1 - z` 反向）逐行相同。唯一差异是类注释里已写明的平台替换（`Mth.lerp` float、`lastTickPosY`、`onGround` 字段、`EntityPlayerSP`） |
| `CameraSetupEvent`（世界 FOV / 相机动画 / roll） | `applyScopeMagnification`（`FOVUpdateEvent`，量纲 `倍率 ↔ 角度` 换算正确）、`applyLevelCameraAnimation`（相机动画 + `CameraStateCache.setCameraRoll`）、`applyCameraRecoil`、`onComputeMovementFov` 均与上游同构。**本轮修掉 1 处**：`applyGunModelFovModifying` 被 bobView 闸门吃掉（A-3） |
| `GameRendererMixin#getFOVModifier`（手/世界 FOV） | `switchRenderType`（HEAD 记录 `useFovSetting`）+ `tacz$overrideHandFov`（RETURN 覆盖）签名与闸门都正确（`CallbackInfoReturnable<Float>` ✅ `require` 见 `mixins.tacz.json`）。**但 A-1 之前 `tacz$overrideHandFov` 永不执行（死代码）；修 A-1 后它其实也仍不执行 —— 现在是 `pushHandProjection()` 直接读 `ITEM_MODEL_FOV`，二者等价且不再依赖 `renderHand`** |
| `GameRendererMixin#hurtCameraEffect` | HEAD 注入 + `GunHurtBobTweak` + `RenderItemInHandBobEvent.BobHurt`/`RenderLevelBobEvent.BobHurt` 按 `useFovSetting` 分流 ✅；`@ModifyArg(ordinal=2)` 缩放受伤倾斜角 ✅（含 `remap=false` 理由） |
| `GameRendererMixin#setupViewBobbing` | HEAD 注入按 `useFovSetting` 分流 ✅。**顺序核对**：`renderWorld → setupCameraTransform:673 getFOVModifier(true) → setupViewBobbing` ⇒ 世界分支；`renderHand:770 getFOVModifier(false) → setupViewBobbing` ⇒ 手部分支（`EntityRenderer.java:673/694` 与 `:770/789-792`）。所以持枪时只有**手部** bobbing 被取消，世界 bobbing 保留 —— 与上游一致 |
| `GameRendererMixin#orientCamera` roll | `RETURN` 处 `glRotatef(CameraStateCache.getCameraRollDegrees(), 0,0,1)` ✅，值由 `CameraSetupEvent:234` 每帧写 |
| `ItemInHandRendererMixin` | `BeforeRenderHandEvent` 在 `renderItemInFirstPerson` HEAD 派发（`:113-119`）—— 但该方法是**被 cancel 的 `renderHand` 内唯一调用点（`:797`）**，所以持枪时它不执行；**补偿链**是 `FirstPersonRenderHandler#compensateBeforeRenderHandEvent()`（`:650,858-869`，`new PoseStack()` → post → `applyToModelView()`），语义与上游「事件里给的 PoseStack 叠加到相机矩阵」等价 ✅。<br>**这里也是 A-1 的第二个后果：手部投影缺失时 `applyToModelView()` 的乘积会被世界投影解释** |
| `ItemInHandLayerMixin`（第三人称不得再画第一人称枪） | `renderByItem` 对 `FIRST_PERSON_LEFT_HAND`/`FIRST_PERSON_RIGHT_HAND`/`THIRD_PERSON_LEFT_HAND` 直接 `return`（`GunItemRendererWrapper.java:371-376`）✅；`FirstPersonRenderHandler:438-440` 也对 `thirdPersonView != 0` 直接 return 不 cancel ✅ 双保险 |
| **上游有、这里没有** | ① 手部投影矩阵（A-1，已修）；② `hideGUI`/睡觉守卫（A-4，已修）；③ `RenderHelper.enableStandardItemLighting()`（B-1，未修）；④ `Entity#xBobO/xBob` 视角延滞抵消（1.7.10 无可读字段，第一人称晃动整条被关掉 + `blockViewBobbing()` 接管 —— 已登记等价） |
| **这里多做/做错** | ⑤ A-2 双画粒子（已修，纯做错）；⑥ A-6 第三人称枪口锚点不插值（已修，与上游数值差一 tick） |

### C. 动画系统 —— **结构完整，未发现缺失分支**

- `GunAnimationStateContext`（699 行 vs 上游 427 行，差异全部来自判空 + 平台替换）：Lua 暴露的 getter 与上游**逐个对应**；
  `isInputUp/Down/Left/Right/Jumping` 用 `MovementInput.moveForward/moveStrafe/jump`（`MathHelper` 符号与
  `player.input.up/down/left/right/jumping` 等价，`moveStrafe > 0` = 左 ✅）；
  `isOnGround` → `Entity#onGround`、`isCrouching` → `isSneaking`、`anchorWalkDist/getWalkDist` →
  `distanceWalkedModified/prevDistanceWalkedModified` + `partialTicks` 插值 ✅；
  `hasBulletInBarrel`（含 `Bolt.OPEN_BOLT` 反向）、`isOverHeat/getHeatProgress`、`getShootInterval`（`FireMode.BURST`
  用 `burstData.getMinInterval()`）、`popShellFrom`（`externalShellRenderer.addShell` + `gunModel.getShellRender(index)`
  + LOD shell）、`getStateMachineParams`、`getChargeProgress/getMaxCharge/getChargeThreshold`、`getAttachment(type)`
  全部与上游一致。
- `setCurrentGunItem` 的「切到空手清 `display`/`gunData`/`nbtUtil`」（r3-5）**复核仍在**
  （`GunAnimationStateContext.java:587-699` 的 r3 注释块 + `else` 分支清空），且**本轮未发现新的陈旧字段**。
- `ItemAnimationStateContext` / `ThrowableAnimationStateContext` / `GunAnimationConstant` / `util/time/GunTime`：
  `GunTime` 的「暂停停表」实现（`nowNanos` 内累加 `pausedNanos`）语义正确；`AnimationClock` 走 SBM 的
  `AnimationClocks.client()`；`AnimationController#update` 的 track 顺序 / 过渡 runner / `consumePendingParticles` 都有真实消费者
  （`GunItemRendererWrapper.java:205-212`）。
- 骨骼解算：`BedrockPart#translateAndRotateAndScale`（`offset → x/16.. → Rz→Ry→Rx → additionalQuaternion → scale`）
  与 `BedrockAnimatedModel`（归一化 diff **仅格式差异**）与上游逐字相同；`cleanAnimationTransform` 清 offset/quat/scale
  且清 `constraintObject`，`cleanCameraAnimationTransform` 复位 quaternion —— 与上游一致，第一人称渲染完必调
  （`GunItemRendererWrapper.java:308`，现已进 `finally`）。
- **Lua 状态机分支（idle/walk/run/aim/aim_hold/reload/bolt/melee/draw/sprint/heat/inspect）**：状态机本体是
  `client/resource/GunDisplayInstance#getAnimationStateMachine` 从枪包 `scripts/*.lua` 加载并执行，
  **脚本本身不在静态审查可判定范围内**（见 §六）。上下文的输入面已逐个核对齐备。

### D. 渲染正确性

| 项 | 结论 |
|---|---|
| **GL 状态 / 矩阵栈成对** | 本轮新增的两处 push/pop 均 `try/finally`：`FirstPersonRenderHandler.java:497-499`（MODELVIEW）+ `:536,688-691`（PROJECTION）；`GunItemRendererWrapper` 的 `pushPose/popPose`（`:214,308`）现已在 `finally` 内。全仓 `glPushMatrix/glPopMatrix`、`glPushAttrib/glPopAttrib`、`startDrawing*/draw()` 复核后仍成对 |
| **depth buffer / 贴墙可见性** | `onRenderHand` 主动 `glClear(GL_DEPTH_BUFFER_BIT)`（`:541`，等价 `EntityRenderer.java:1436` 被跳过的清深度）✅ |
| **lightmap** | `enableLightmap/disableLightmap` 成对（`:558,676` + finally `:685`），`packedLight` 取脚部 `getLightBrightnessForSkyBlocks` 等价 `ItemRenderer.java:287` ✅（r2 修复仍在） |
| **顶点属性顺序** | `BufferBuilder#endVertex` 落顶点（r2 P0 修复仍在）；本轮作用域 0 处漏 `endVertex()` |
| **muzzle flash / shell / laser / beam / text 锚点** | `MuzzleFlashRender` 第一人称按 `shootTimeStamp` + `!isSelf` 闸门（`:187-199`）；`ShellRender` 用 `isSelf`（`:188`）；`BeamRenderer` 由 `BedrockGunModel.java:396-398` 在**模型渲染之前**画（`transformType` 直接透传，第一人称拿 `FIRST_PERSON_RIGHT_HAND`）；`TextShowRender` 仅 `firstPerson()` 生效（与上游一致）。**锚点本身由 `getMuzzleFlashPosPath()` + `BedrockPart` 路径解算，与上游同构** |
| **GUI / 背包图标朝向** | `applyGuiSlotScale` = `scale(16,-16,16) + translate(0,-1,0)`（r2 修复仍在），4 个 `INVENTORY` 调用点在 |
| **bedrock `texture_width/height` → mcshim UV(0..1)** | `BedrockPolygon` 按 `texWidth/texHeight` 归一（r2 逐轴复算结论仍成立） |
| **`OculusCompat.isRenderShadow()` 闸门** | `MuzzleFlashRender:187`、`ShellRender:185` 用它直接 return ⇒ 光影的 shadow pass 里不画。因为 shadow pass 用 depth-only 材质，**不画火光/弹壳是上游 1.20 的同一行为**（`OculusCompat` 已按 Angelica 的 `areShadowsCurrentlyBeingRendered()` 探测，见 PORT_STATUS 的 Angelica 节）。⚠ **手与镜片**：`RightHandRender`/`LeftHandRender`/`ScopeLensRenderer` 的同类闸门在**非 shadow pass** 下恒 false，不会把第一人称整片挡掉 |
| **RenderType 与直接 GL11 混用** | 第一人称枪体走 `RenderType.entityCutout/entityTranslucent`（`:295-297`），`setupRenderState` 快照 → `clearRenderState` 还原（r2 P1-1 修复仍在）。`MuzzleFlashRender` 就地 `endBatch`（`:143-145,170-172`）避免错帧 |

### E. 数据通路

- `IDisplay` → `PackDirectory.of("textures", ".png")` → `tacz:textures/gun/uv/ak47.png`（r2/r3 已核通）；
  `ClientAssetsManager` 的 `bedrockModel`/`bedrockAnimation`/`gltf` 都显式传 `GunPackResources.ASSETS_ROOT`（r2 修复仍在）。
- **第一人称依赖的 bone/node**：`idle_view`（`BedrockAnimatedModel.java:66`）、`iron_sight`/`scope_pos`（`BedrockGunModel` 的
  `getIronSightPath()/getScopePosPath()`）、`muzzle_flash`（`:91`）、`shell`（`:175-177`）、`constraint`（`BedrockAnimatedModel.java:53`）、
  `camera`（`:48-51`）—— 缺失时的行为：`getPath(...)` 返回 `null` ⇒ 定位变换退化为**单位矩阵**（枪贴在模型原点），
  **与上游同构**（上游同样 `getPath` 返回 null、`getPositioningNodeInverse(null)` 返回 identity）。不是移植缺陷（E-1）。
- **本轮未重跑 232 个 index 的引用命中统计**（r2 §六 已 100% 命中，本轮未触碰 `resource/**`）。

### F. 互不干扰

- `GunItemRendererWrapper#renderByItem` 的 `IItemRenderType` 分派：`FIRST_PERSON_*` → return、`THIRD_PERSON_LEFT_HAND` → return、
  `GUI` → `renderSlotTexture`、其余（`EQUIPPED`/`ENTITY`/`FIXED`）→ 3D 模型；映射表见 `RenderHelperCompat#displayContextOf:216-232`。
  ✅ 与上游 `:326-337` 一致。
- 双重绘制：见 §二 A 末「F 的双重绘制复核」—— **不存在**。
- **静态 `isSelf` 的置位/复位窗口**：
  - 第一人称：`GunItemRendererWrapper.java:269-270` 置、`:311-312` 复位（现已 `finally`）。
  - 第三人称：`mixin/client/ItemInHandLayerMixin.java:110-116` 只在 `livingEntity == thePlayer` 时置 true，`:124-125` 无条件复位
    ⇒ 渲染**其他玩家**时 `isSelf == false`，不会串（与上游 `ItemInHandLayerMixin` 同构）。残留风险见 F-1。
  - `ThirdPersonMuzzleParticleManager.currentEntity` 同样在 HEAD 置 / RETURN 清，窗口精确。
  - 第一人称与第三人称**不会同时**渲染（`FirstPersonRenderHandler:438-440` + `renderByItem` 双向排除）。
- **第一/第三/物品栏共用同一 `display` 实例**：`GunItemRendererWrapper#renderFirstPerson` 渲染完显式 `cleanAnimationTransform()`
  （`GunItemRendererWrapper.java:309`，现已进 `finally`），而第三人称分支 `renderByItem` **不**清 —— 与上游一致（上游也是只有第一人称清）。
  `BedrockModel` 的 `particleWindowRenderer`/`delegateRenderers` 每次渲染后用后置 null + 换新 `ArrayList`（`:527-538`），
  同帧二次渲染不会串（与上游逐行同构，r2 已登记）。

---

## 四、已修清单（逐条说明改了什么）

> 全部改动 `lint_files(min_severity=error)` = **0 error**；**未跑 gradle**。

### 1. `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java`

- **A-1（P0）** 新增 `pushHandProjection()`（`:714-731`）并在 `onRenderHand` 的 `try` 首部调用（`:536`），
  `finally` 里成对 `glPopMatrix`（`:688-691`）。数值：`Project.gluPerspective(CameraSetupEvent.ITEM_MODEL_FOV,
  displayWidth/displayHeight, 0.05F, (renderDistanceChunks*16)*2)`，与 `EntityRenderer.java:657/770` 逐项相同。
- **A-2（P1）** 删除 `try` 块开头那处**无条件**的 `renderParticlesIfAny(...)`，只保留事件末尾带
  `renderer.renderParticlesInModelWindow()` 闸门的那一处（`:664-667`）。
- **A-4（P1）** 新增 `hideGUI` / `isPlayerSleeping()` 守卫（`:451-453`），`return` 不 cancel。

### 2. `src/main/java/com/tacz/guns/client/event/CameraSetupEvent.java`

- **A-3（P1）** `applyGunModelFovModifying(player, ClientTickHelper.getPartialTick())` 从方法末尾（原 `:230`，位于
  `!viewBobbing` 早退之后）提到 `RenderTickEvent.START` 分支的**所有闸门之前**（`:196-205`）。

### 3. `src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java`

- **A-5（P2）** `renderFirstPerson` 的 `MuzzleFlashRender.isSelf/ShellRender.isSelf/setRenderHand/popPose/
  cleanAnimationTransform` 复位全部移进 `finally`（`:269-313`）。
- **A-6（P2）** `captureThirdPersonMuzzle(Matrix4f)` 改用 `partialTicks` 插值的相机位置与朝向；
  `cameraYaw()`/`cameraPitch()` 改为带 `partialTick` 参数、实体取 `renderViewEntity`（`:472-520`）。

---

## 五、未修清单（逐条给修法 + 需要谁配合）

| # | 级别 | 问题 | 修法（具体到代码） | 需要谁配合 |
|---|---|---|---|---|
| **B-1** | P2 | 第一人称手臂没有 `RenderHelper.enableStandardItemLighting()` | `client/model/RenderHelperCompat.java#renderFirstPersonArm`：在 `poseStack.pushToGL()` 之前 `net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting()`，`finally` 里 `disableStandardItemLighting()`（灯位置落在相机空间，等价 vanilla `ItemRenderer.java:272` 的「在 pitch/yaw 帧内设灯」/`:553` 的关灯） | **无（本 teammate 作用域内）**，但**刻意留待实机**：手臂是「满亮平贴图」还是「有方向光」哪个更接近上游，静态无法判定 —— 建议 Lead 在实机取一张带手臂的截图后再决定是否落地 |
| **C-1** | P2 | `MuzzleParticleManager` 三张静态表无清理入口 ⇒ 断线/重载后可能残留一次待创建枪口请求 | `client/particle/MuzzleParticleManager` 新增 `public static void reset()`：`PENDING.clear(); BOUND.clear(); BASE_POSE.clear(); resetMuzzleLight();`；在 `FirstPersonRenderHandler#reset()`（`:155-164`）末尾调用 | **跨域**：`client/particle/**` 不属本 teammate 写作用域 ⇒ 已 `send_message` 报 Lead；改 2 个文件、约 8 行 |
| **E-1** | P2 | 缺 node 的第三方枪第一人称「贴脸在原点」 | 与上游同构（`getPath(null)` → identity），**建议不改**（保真优先）。若要友好化：`applyFirstPersonPositioningTransform` 在 `idleSightPath == null` 时回退到 `getPositioningNodeInverse(root)` | 产品裁决 |
| **F-1** | P2 | `ItemInHandLayerMixin#renderTail` 无条件清 `isSelf` | 用 `@Unique` 字段在 HEAD 记下进入时的 `MuzzleFlashRender.isSelf/ShellRender.isSelf`，RETURN 时**恢复**而不是置 false | **无（本 teammate 作用域内）**；当前无现网症状，按「不引入上游没有的行为」原则未改 |
| **R-1** | — | **组合镜 stencil 遮挡恒失效**（镜内能看到枪体） | 已由 r3-4 完整登记（`BedrockGunModel.java:441-474`），本轮**不重复修**。零代码缓解：`-Dforge.forceDisplayStencil=true` | 无 |

---

## 六、覆盖清单（诚实）

### 逐行读过（本轮）

- **SBM 第一人称**：`v1/client/handler/FirstPersonRenderHandler.java`（1031 → 1128 行，全读）、
  `v1/client/renderer/IFPGeoItemRenderer.java`（158 行，全读）、`v1/particle/firstperson/FirstPersonParticleSystem.java`（287 行，全读）、
  `v1/particle/render/ParticleRenderer.java`（112 行，全读）。
- **item 渲染器**：`client/renderer/item/GunItemRendererWrapper.java`（505 行，全读 + 与上游 423 行归一化 diff）、
  `AnimateGeoItemRenderer.java`（453 行，全读）。
- **event**：`client/event/FirstPersonRenderGunEvent.java`（552 行，全读 + 与上游 478 行归一化 diff）、
  `client/event/CameraSetupEvent.java`（494 行，全读 + 关键段与上游 275 行逐段对照）、
  `client/event/FirstPersonRenderEvent.java`（124 行，全读 + 与上游 71 行对照）。
- **model**：`client/model/BedrockGunModel.java`（632 行，读 `render`/`renderUnaccelerated`/functional 挂点段 + 全文 grep）、
  `BedrockAnimatedModel.java`（194 行，全读 + 与上游归一化 diff）、
  `client/model/bedrock/BedrockModel.java`（576 行，读 `render` 段）、
  `client/model/RenderHelperCompat.java`（318 行，读 `endBatch`/`renderFirstPersonArm`/`displayContextOf`/`applyGuiSlotScale` 段）、
  `client/model/functional/{MuzzleFlashRender,RightHandRender}.java`（关键段）。
- **mixin/client**：`GameRendererMixin.java`（484 行，全读，含 7 处注入的闸门/签名/`remap` 复核）、
  `ItemInHandRendererMixin.java`（164 行，全读）、`ItemInHandLayerMixin.java`（148 行，全读）。
- **animation**：`client/animation/statemachine/GunAnimationStateContext.java`（699 行，读 1-200 / 355-620 + 与上游归一化 diff）、
  `api/client/animation/AnimationController.java`（读 update 段）、`client/animation/third/InnerThirdPersonManager.java`（全读）。
- **util**：`util/time/GunTime.java`（202 行，全读）。
- **1.7.10 准绳**：`EntityRenderer.java`（读 `setupCameraTransform` 655-746、`renderHand` 751-814、`renderWorld` 尾 1320-1450）、
  `ItemRenderer.java`（读 `renderItemInFirstPerson` 264-333 / 441-554、`renderOverlays` 559-617、grep 光照调用）、
  `ForgeHooksClient.java`（`renderFirstPersonHand`、`renderInventoryItem` 段）、`GameSettings.java`（grep `renderDistanceChunks`）、
  `util/glu/Project.java`（确认可解析）。
- **上游**：`client/renderer/item/GunItemRendererWrapper.java`（423 行，全读）、
  `client/event/FirstPersonRenderGunEvent.java`（478 行，读 1-220 / 305-478）、
  `client/event/CameraSetupEvent.java`（读 60-179）、`client/event/FirstPersonRenderEvent.java`（71 行，全读）。

### 没读 / 只抽查（诚实列出）

1. **`client/resource/**`（87 文件）与 232 个 index 的引用命中**：本轮**完全没重跑**，直接沿用 r2 §六「100% 命中」的结论
   （本轮未触碰该包，也没有发现需要反查它的症状）。
2. **`client/model/functional/**` 的其余 5 个**（`BeamRenderer`、`TextShowRender`、`RangefinderImpactPredictionRender`、
   `AttachmentRender`、`ShellRender`）只核了「挂点是否存在 + `firstPerson()` 分支 + `isSelf`/`isRenderShadow` 闸门」，
   **没有逐行对照上游的几何数值**。
3. **`client/model/BedrockAttachmentModel.java`（28 KB）** 未逐行读（r2 已做过 stencil 结论；本轮只确认 `renderOcularStencil` 段落注释在）。
4. **`client/animation/statemachine` 的 `ItemAnimationStateContext` / `ThrowableAnimationStateContext`** 只核了类职责与字段，
   未逐 getter 对照上游。
5. **`client/animation/third/PlayerModelRenderState.java`（6 KB）与 `AnimationRenderContext.java`** 只读了类名/职责，
   未逐行对照上游（第三人称姿态，非本模块重点）。
6. **`client/renderer/scope/**` 的 GLSL 数学**未逐行对照上游（与 r2 的诚实声明一致）。
7. **枪包 Lua 脚本本体**（`assets/tacz/custom/**/scripts/*.lua`）**未逐个审**：状态机分支是否齐全取决于脚本，
   静态审查只能证明「上下文 API 齐备 + 状态机被驱动」，不能证明某个分支的观感。

---

## 七、不可静态验证项（必须实机）

1. **A-1（P0）**：进游戏 → 装 4× 瞄具 → 右键开镜。修复前枪体应**明显放大**；修复后应与上游观感一致
   （枪/瞄具大致停在屏幕下方 1/3，目镜中心对准屏幕中心）。**若开镜后枪体忽大忽小 → `ITEM_MODEL_FOV` 的值本身有问题**
   （那是枪包 `model_fov`/`views_fov` 数据或 `AdsPath` 曲线，不是投影矩阵）。
   副判据：把 `viewBobbing` **关掉**再开镜 —— A-3 修复前手部 FOV 恒 70°，修复后应仍然正确缩放。
2. **A-2**：开火看火光亮度/烟雾浓度。修复前应为「明显过曝的两层叠加」，修复后是单层。
   若修复后**完全看不到火光**，说明 `renderParticlesInModelWindow()` 那条路本身没生效（`MuzzleParticleManager.getBasePose` 为 null），
   需要回头查 `updateParticleEmitterTransforms`。
3. **A-4**：按 F1 → 不应看到枪；睡觉（躺床）→ 不应看到枪；按 F5 切第三人称 → 不应看到第一人称枪。
4. **A-5/A-6**：边走（侧向 + 转身）边开火，第三人称观察自己的枪口粒子是否与枪口严格贴合、无抖动。
5. **B-1**：手臂是否有方向光（需要一张带手臂的第一人称截图）。
6. **C-1**：断线重连 / `/tacz reload` 后第一次开火是否出现「无源火光」。
7. **F-1 / R-1**：组合镜内是否仍看到枪体（**按预期仍会看到**，属 r3-4 已登记失效，不是回归）。
8. **性能**：`BufferBuilder` + `BufferUploader` 每帧逐顶点重灌（无 VBO），第一人称满载粒子时的帧率需实测。

---

# 实机回归（第 2 轮）· 用户报的 5 个现象

> 触发：r4 构建后用户实机反馈 5 个现象。用户已确认：**这 5 个现象「上一版就有」，不是 r4 的回归**
> ⇒ 按长期缺陷查；环境是**纯 Forge + TACZ**（无 OptiFine / Angelica）⇒ 兼容层闸门可以排除。
> r4 已修的手部投影（A-1）保持不动。
> **本轮禁止任何 gradle**；改动 9 个文件（含 Lead 授权的 `HumanoidOffhandRender`），`lint_files(min_severity=error)` = **0 error**。

## 一、现象 → 根因 → 落点 → 是否还需实机

| # | 现象 | 根因（结论） | 落点 | 是否还需实机 |
|---|---|---|---|---|
| **时钟** | 「收枪后手消失 / 枪械模型残留屏幕」+「开枪火光残留」+「切非枪械后枪残留」 | **SBM 的 `ClientAnimationClockTicker` 在 vendor 时整体丢失** ⇒ `PausedClientAnimationClock#update()` 全仓 0 调用点 ⇒ `logicalNanos` 恒 0。三个现象同源（`getSheatheProgress` 恒 0 ⇒ `transitioning` 永真 ⇒ 一直渲染 `previousInstance` 并 cancel 事件；`dt` 恒 0 ⇒ `PARTICLE_SYSTEM.tick(0)` 粒子永不死） | **Lead 已修**（`FirstPersonRenderHandler#tickAnimation` START 首行 `update()`、`reset()` 里 `reset()`）。本报告**复核通过**，并给出「其它时钟消费者」清单（§二） | 修完即验：切空手后手应立即回来；火光应按 `living_time` 消失 |
| **A** | 原版皮肤手臂遮挡大半屏幕 | **已定论（实机数据驱动，既不是「缺摆放」也不是「FOV 放大」）**：手臂确实挂在握把骨骼上，但 1.7.10 的 `ModelBiped` 手臂是 **12 px 长条 + T-pose**，被骨骼链 `axes.y = 1.5` 拉到 1.125 格，而默认枪包 `lefthand_pos` 的锚点**就在眼前 9 cm**（`origin.z = -0.091`）⇒ 竖着横穿整个视锥。上游 1.20 之所以没问题，是因为它用原版手臂**顶替**模型自带的手（`RightHandRender:31-47`），而那套手臂与 1.20 骨骼配套 | **数据驱动修复**：`BedrockAnimatedModel#hasBuiltinHandModel()`（查 `righthand_pos`/`righthand`/`lefthand_pos`/`lefthand` 子树里有没有 cube）→ `BedrockGunModel` 构造器在**注册处**让工厂返回 `null` ⇒ 画模型自带的手；第三方无手模型仍画原版手臂。另加兜底 `RenderHelperCompat.MIN_HAND_ANCHOR_DISTANCE = 0.25F`（锚点贴脸则不画） | **需实机目视一次**：默认枪应看到**模型自带的方块手**、屏幕不再被皮肤手臂遮住；日志应出现 `[TACZ-DBG] arm-builtin gun=tacz:ak47 node=righthand_pos ...`（且**不再**出现 `[TACZ-DBG] arm`） |
| **B** | 开镜后枪模偏到同一侧（常数 X 偏移） | **「只用了 `idle_view`」这条假设被证伪**：`FirstPersonRenderGunEvent#applyFirstPersonPositioningTransform:203-319` 在 idle / aim 两套 view 矩阵之间按 `aimingProgress` 做了两次 `applyMatrixLerp`（`:277` idle、`:282` aim），`aimingNodePath` 取 `getIronSightPath()` 或 `scope_pos`；与上游 `FirstPersonRenderGunEvent.java:153-256` **逐行同构**。只有被 `GunItemRendererWrapper#renderFirstPerson` 覆盖掉、对枪械**永不可达**的基类 `AnimateGeoItemRenderer#applyFirstPersonPositioningTransform:349-365` 才只取 idle | `client/event/FirstPersonRenderGunEvent.java` 末尾新增 `[TACZ-DBG] ads`（`aim` / `raw` / `fov` / 最终姿态 `m30,m31,m32`，数值变化才打） | **需实机一行日志定论**（§三 B 给出判读表）。未改任何变换逻辑（避免盲改） |
| **C** | 第三人称手持枪模型「反了」（上下颠倒/前后翻转） | **1.7.10 把 3D 枪模连续套了两次「2D 扁平物品」变换**：① `RenderPlayer#renderEquippedItems:377-385` 的 2D 纸片分支（`T(0.25,0.1875,-0.1875)·S(0.375)·Rz(60)·Rx(-90)·Rz(20)`，因为 `is3D=false` 且 `Item#isFull3D()` 未被覆写）；② `ForgeHooksClient#renderEquippedItem:225-233` 的又一发 2D 纸片变换（`T(0,-0.3,0)·S(1.5)·Ry(50)·Rz(335)·T(-0.9375,-0.0625,0)`，与 `ItemRenderer.java:124-129` 逐字相同） | **已全部修掉（C-1 + C-2 双段）**：<br>① `AnimateGeoItemRenderer#shouldUseRenderHelper` 对 `EQUIPPED + EQUIPPED_BLOCK` 返回 `true` ⇒ 干掉第 ② 层；<br>② `AnimateGeoItemRenderer#renderItem(EQUIPPED)` 乘常量 `TACZ_EQUIPPED_PAPER_FRAME_INV = inv(F_paper)`（`F_paper` 由 `RenderPlayer:319` + `:380-384` + `ForgeHooksClient:219` 逐句搭出）⇒ 把坐标系拉回**手臂骨骼帧**；<br>③ `HumanoidOffhandRender#renderGunItem` 用 `beginExternalEquippedFrame()/endExternalEquippedFrame()` 作用域标记（`finally` 复位）跳过该补偿。<br>目标帧对枪械是**恒等**的依据：`assets/tacz/models/item/modern_kinetic_gun.json` 只有 `{"parent":"builtin/entity","gui_light":"front"}`，**无 `display` 段**（对照 `attachment.json` 才有，与本次无关）。<br>`BLOCK_3D` 故意仍 false（会引入 X/Y 镜像，与 `GunItemTransforms#apply:25` 的 `scale(-1,-1,1)` 互相抵消） | **仍需一次实机目视确认**（这是纯几何结果，只能看）。若方向差一个 90° 的 X 旋转，说明 1.20 基座帧里还含 `Rx(-90)·Ry(180)`，把目标帧补进 `tacz$buildPaperFrameInverse()` 即可；单点停用：`AnimateGeoItemRenderer.ENABLE_EQUIPPED_PAPER_FRAME_FIX = false` |
| **D** | 切到非枪械物品后背后出现一支枪 | **非 bug，上游同构**：`HumanoidOffhandRender#renderHotbarGun:77-84` 显式 `if (i == inventory.currentItem) continue;` ⇒ **当前手持槽不会被重复画**；且只有该枪的 display 在 `hotbar_show` 里声明了该栏位（`:101-104`）才会画。这正是上游「快捷栏其余格的枪挂在身上」的设计（`RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER` 可关） | 未改 | 不需要（若嫌碍眼，开 `DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER`） |

## 二、时钟复核（Lead 的修法是否完整）

### 2.1 结论：**修法正确、位置正确、覆盖面够**

- `PausedClientAnimationClock#update()` 现在在 `FirstPersonRenderHandler#tickAnimation` 的
  `TickEvent.RenderTickEvent.START` 分支、**`shouldTick()` 早退与任何 `nowNanos()` 读取之前**无条件调用 ⇒
  每个渲染帧推进一次 `logicalNanos`（`update()` 内部用 `isGamePaused()` 自己跳过暂停帧，
  等价上游 `Minecraft#isPaused()`）——**每个渲染帧一次**就够所有消费者用，因为
  `logicalNanos` 累加的是真实流逝时间，与调用频率无关。
- `reset()` 里补 `PausedClientAnimationClock#reset()` 等价上游 `ClientAnimationClockTicker#onLoggingOut`
  （`tmp/libs-src/.../v1/client/event/ClientAnimationClockTicker.java:27-30`）。✅
- 事件总线归属正确：`RenderTickEvent` 只从 **FML** 总线派发，而本类实例是
  `FirstPersonRenderHandler`（`FirstPersonRenderHandler.java:65-71` 静态块同时注册两条总线）。✅
- 类初始化有保证：`ClientSetupEvent#registerItemRenderers:303-306` → `FirstPersonRenderHandler.registerRenderer(...)`
  会触发类初始化并执行静态块；`ClientIndexManager:128` 的 `reset()` 也会。✅

### 2.2 `AnimationClocks.client()` 的**全部**消费者（全仓 grep，只有 2 个）

| 消费者 | 位置 | 是否受影响 | 说明 |
|---|---|---|---|
| `FirstPersonRenderHandler.CLOCK` | `.../v1/client/handler/FirstPersonRenderHandler.java:73` | ✅ 修好 | `onClientTick` 的 `shouldTick()`、`beginSwitch` 的 `switchStartTime`、`getSheatheProgress`、`tickAnimation` 的 `dt` |
| `WorldEmitterManager.CLOCK` | `.../v1/particle/world/WorldEmitterManager.java:54` | ✅ 一并修好 | 同一个单例；一次 `update()`/帧足够，无需在它自己的 handler 里再调 |

**不是**时钟消费者（因此**不受**这次 bug 影响，也**不需要**改）：
- `AnimationController` / `ObjectAnimationRunner` / `AnimationStateMachine` —— 时间源是 **`GunTime`**
  （`api/client/animation/ObjectAnimationRunner.java:12,75,322-323`、`AnimationStateMachine.java:11,145-156`），
  `GunTime` 自己有 `pausedNanos` 停表实现（r3 修过），与 SBM 时钟无关。
- `ItemAnimationStateContext` —— 只有 `partialTicks` / putAwayTime 字段，无时钟。
- `FirstPersonParticleSystem` —— 自己不取时钟，`dt` 由 `FirstPersonRenderHandler#tickAnimation` 传入
  （所以它是**间接**受益者，已随主修好）。
- `MuzzleParticleManager` / `ThirdPersonMuzzleParticleManager` / `MuzzleFlashRender` / `ShellRender` —— 用
  `GunTime`（`util/time/GunTime.java`），非 SBM 时钟。

### 2.3 「切非枪械后枪残留」除时钟外**没有**第二个原因（逐分支核对）

`onClientTick` → `beginSwitch`（`FirstPersonRenderHandler.java:291-329`）→ 若 `oldIsCustom` 为真则进入
收枪过渡：
- `state.previousInstance = activeInstance`（枪的 instance）
- `state.activeInstance = createInstance(newStack, hand)` → `getRenderer(非枪)` = `Optional.empty()` ⇒ **null**
- `transitioning = true; switchStartTime = CLOCK.nowMillis();`
- `previousInstance.triggerPutAway()`（触发 `tryExit` → 状态机 `INPUT_PUT_AWAY`）
- `if (currentSheatheDuration <= 0) finishTransition(...)`

`getActiveAnimationInstance()` 在 `transitioning` 期间返回 `previousInstance`（**枪**），
而 `finishTransition` 只在 `getSheatheProgress() >= 1` 时被 `tickStates` 调用；
`getSheatheProgress = (CLOCK.nowMillis() - switchStartTime) / currentSheatheDuration` —— 修复前分子恒 0
⇒ **永不为 1** ⇒ `previousInstance` 永不被换掉 ⇒ 每帧渲染那把枪并 `event.setCanceled(true)`
⇒ 原版手也不画。**时钟修好后 `finishTransition` 会走 `createInstance(state.realItem = null)` → null
⇒ 两手状态机全空 ⇒ 什么都不画**。✅ 因果闭合，无其它分支能造成同样的残留
（`discardOffhandInstance` 只服务副手；`PARTICLE_SYSTEM.discardEmitters` 只管粒子）。

## 三、仍待实机定论的 3 项 + `[TACZ-DBG]` 判读方法

> 统一前缀 `[TACZ-DBG]`，**只在数值变化时打一行**，不会刷屏。跑一次世界、各做一遍动作后
> `grep "\[TACZ-DBG\]" logs/latest.log` 即可。

### A. 手臂 —— **已结案（实机日志定论）**

用户实机日志（同一批，持枪站定，`itemModelFov=60`）：

```
[TACZ-DBG] arm RIGHT origin=(0.038, -0.560, 0.712) axes=(1.000, 1.500, 1.000) rp=(-5,2,0) rot=(-1.4, -0.3, 0.1) swing=0 equip=1
[TACZ-DBG] arm LEFT  origin=(0.140, -0.638, -0.091) axes=(1.000, 1.500, 1.000) rp=(5,2,0)  rot=(-1.4, 0.8, -0.1) swing=0 equip=1
```

判读（对应报告上一版的「三选一」表）：

| 观察 | 结论 |
|---|---|
| `origin` 不是 (0,0,0) | **不是**「没摆放」⇒ 骨骼链生效，`righthand_pos`/`lefthand_pos` 确实是真骨骼 |
| `axes.y = 1.500` | 骨骼链里 **Y 被放大 1.5 倍**；`ModelBiped` 手臂本体 12 px × 0.0625 = 0.75 ⇒ 实际 **1.125 格**长 |
| `rot ≈ (-1.4°, 0.3°, 0.1°)` | **T-pose**（`setRotationAngles(0,0,0,0,0,…)`），手臂从锚点沿 +Y 竖着长出去 |
| **左**手臂 `origin.z = -0.091` | 锚点在**眼前 9 cm**；右手臂 `z = +0.712` 在相机后方（被近平面裁掉）⇒ **遮挡屏幕的是左手臂** |

数据侧实锤：`ak47_geo.json` 里 `righthand_pos` 与 `lefthand_pos` **各带 1 个 cube**（`size: [3,12,4]`），
即模型作者已经画好了「握枪的手」；原版皮肤手臂是**额外叠上去的**。

**修法（数据驱动，不硬编码枪名）**
1. `BedrockAnimatedModel#hasBuiltinHandModel()`：`righthand_pos` / `righthand` / `lefthand_pos` / `lefthand`
   任一棵子树里至少一个 cube ⇒ `true`（实例级缓存，`BedrockAnimatedModel.java:76-140`）。
2. `BedrockGunModel` 构造器：为真时功能渲染器工厂返回 **`null`**（仍注册工厂，保持「节点缺失」时的退化路径），
   于是 `FunctionalBedrockPart#render:82-108` 走 `renderer == null` 分支、正常 `compile` 出**模型自带的手**；
   为假（第三方枪包没画手）⇒ 保持上游行为，仍 `new LeftHandRender/RightHandRender(...)`。
3. **为什么「跳过」不能写进 `RightHandRender`/`LeftHandRender`**（记录以免下一轮改错）：
   `FunctionalBedrockPart#render` 在 `functionalRenderer` 非 `null` 时**只**执行功能渲染器、
   **不会**再 `compile` 自己的 cube ⇒ 在渲染器里 early-return 只会得到「手整个消失」。
   所以判定必须落在**注册处**（工厂返回 null），两个渲染器类里已写明这段理由。
4. 兜底：`RenderHelperCompat.MIN_HAND_ANCHOR_DISTANCE = 0.25F`
   —— 锚点离眼 < 0.25 格就不画原版手臂（防第三方枪包把 `lefthand_pos` 摆在眼前重现同一症状）。置 0 可关。

**日志变化（下一次实机 grep 什么）**
- 默认枪包：应出现 **`[TACZ-DBG] arm-builtin gun=tacz:ak47 node=righthand_pos -> skip vanilla skin arm, draw model's own hand cubes`**
  （每把枪一行，随枪切换才变）；**不再**出现 `[TACZ-DBG] arm`。
- 第三方无手模型：仍出现 `[TACZ-DBG] arm ...`（`origin` 应与上面同量级）。
### B. `[TACZ-DBG] ads`（开镜常数偏移）—— **仍未取得日志**

r4 第 2 次实机**没有** `[TACZ-DBG] ads` 行（那一轮没开镜，或开镜太短被去重吃掉）。
⇒ **未据此改任何 FOV 代码**（变换逻辑一行未动）。

**打印已放宽**（本类 `tacz$logAds`）：进入 ADS 打 `ads-enter`、离开打 `ads-leave`、
期间每 10 帧打一行 `ads`、**首次完全开镜**那一刻额外打 `ads-full`。这样短按也不会漏。
下一次实机请按住右键开镜保持 1 秒，`grep "\[TACZ-DBG\] ads"`：
```
[TACZ-DBG] ads-enter aim=0.00 raw=0.01 fov=60.00 pose=(…)
[TACZ-DBG] ads       aim=0.53 raw=0.62 fov=52.31 pose=(…)
[TACZ-DBG] ads-full  aim=1.00 raw=1.00 fov=45.00 pose=(-0.001, -0.032, 0.412)
[TACZ-DBG] ads-leave aim=0.00 raw=0.00 fov=60.00 pose=(…)
```
判读表：

| 观察 | 结论 / 下一步 |
|---|---|
| `aim` 到不了 1.00 | `IClientPlayerGunOperator#getClientAimingProgress` 没到 1（shooter 侧，不在本模块） |
| `aim=1.00` 但 `fov` 仍是 `model_fov`（ak47=60） | `CameraSetupEvent#applyGunModelFovModifying` 没跟上（本模块可再查） |
| `aim=1.00`、`fov` 正常、`pose` 的 `x` 是明显非零常数（\|x\| > 0.05） | **模型定位本身偏** ⇒ 下一步查枪包 `iron_view` / `scope_pos` 路径与 `views_fov` 是否配套（数据侧） |
| `aim=1.00`、`pose` 的 `x ≈ 0` 而玩家仍觉得偏 | **手臂遮挡造成的视觉误判**（现象 A）；A 修好后应随之消失 |

### C. C 项已落地（C-1 补偿 + C-2 作用域隔离）
Lead 裁决：枪械的 1.20 物品模型**没有 `display` 段**（`assets/tacz/models/item/modern_kinetic_gun.json` 只有
`{"parent":"builtin/entity","gui_light":"front"}` ⇒ `ItemDisplay` 对枪械是恒等），因此补偿常量就是
**「纸片帧的逆」`inv(F_paper)`**，后面接恒等，不需要解析 JSON、也不需要猜 `thirdperson_righthand`。

`F_paper` 按 1.7.10 的 GL 调用顺序逐句搭出（每句都是右乘，所以 JOML 同序调用即等价）：

| 步 | 调用 | 来源 |
|---|---|---|
| 1 | `translate(-1/16, 7/16, 1/16)` | `RenderPlayer#renderEquippedItems:319` |
| 2 | `translate(0.25, 0.1875, -0.1875)` | `:380` |
| 3 | `scale(0.375, 0.375, 0.375)` | `:381` |
| 4 | `rotateZ(60°)` | `:382` |
| 5 | `rotateX(-90°)` | `:383` |
| 6 | `rotateZ(20°)` | `:384` |
| 7 | `translate(-0.5, -0.5, -0.5)` | `ForgeHooksClient#renderEquippedItem:219`（本轮 `EQUIPPED_BLOCK=true` 后走这条） |

为什么是常量：第 1 步之前的 `bipedRightArm.postRender(0.0625F)`（手臂骨骼帧 + 动画旋转）在矩阵**左端**，
而补偿右乘在末端 ⇒ `F_arm · F_paper · inv(F_paper) = F_arm`，与玩家姿态无关。

落地三处：
1. `AnimateGeoItemRenderer#renderItem(EQUIPPED)`：`poseStack.mulPoseMatrix(TACZ_EQUIPPED_PAPER_FRAME_INV)`；
2. `AnimateGeoItemRenderer` 新增 `ENABLE_EQUIPPED_PAPER_FRAME_FIX`（单点停用开关）与
   `beginExternalEquippedFrame()/endExternalEquippedFrame()`（嵌套深度计数）；
3. `HumanoidOffhandRender#renderGunItem`：`begin…/finally end…` 包住它的 `renderItem(..., EQUIPPED)`
   （它的基座是枪包 `hotbar_show` 的 pose，**不是**手臂帧 ⇒ 必须跳过补偿；`end` 在 `finally` 里 ⇒ 异常也不会让标记泄漏）。

**仍需实机目视一次**（纯几何结果）：若方向差一个 90° 的 X 旋转，说明 1.20 基座帧里还含 `Rx(-90)·Ry(180)`，
把目标帧补进 `tacz$buildPaperFrameInverse()` 即可；单点停用走 `ENABLE_EQUIPPED_PAPER_FRAME_FIX = false`。

## 四、本轮改动的文件（3 个，均 `lint_files(error)` = 0）

1. `src/main/java/com/tacz/guns/client/model/RenderHelperCompat.java`
   —— A：`glColor3f(1,1,1)` + `GL_RESCALE_NORMAL` + `enableStandardItemLighting`/`disable`（三处 vanilla 依据见方法内长注释）
   + `[TACZ-DBG] arm` 一次性诊断；并写明「为什么不能照抄 vanilla 空手摆放」的完整证据链。
2. `src/main/java/com/tacz/guns/client/renderer/item/AnimateGeoItemRenderer.java`
   —— C：`shouldUseRenderHelper` 对 `EQUIPPED + EQUIPPED_BLOCK` 返回 `true`；类内长注释逐行记下
   1.7.10 的**两层**扁平变换（`RenderPlayer:377-385` + `ForgeHooksClient:225-233`）与 `BLOCK_3D` 仍 false 的理由。
3. `src/main/java/com/tacz/guns/client/event/FirstPersonRenderGunEvent.java`
   —— B：仅新增 `[TACZ-DBG] ads`（`aim`/`raw`/`fov`/`pose` 平移列，变化才打）；**变换逻辑一行未改**。
4. `src/main/java/com/tacz/guns/client/model/RenderHelperCompat.java`（续）
   —— A：`[TACZ-DBG] arm` 按 Lead 要求扩到 6 组量（`origin` / `axes` 三轴长 / `rp` 旋转点 / `rot` 三轴角 /
   `swing` / `equip`，其中 `equip` 用反射读 `ItemRenderer#equippedProgress`，读不到打 `n/a`）。
5. `src/main/java/com/tacz/guns/client/renderer/other/HumanoidOffhandRender.java`（**Lead 已授权**）
   —— C-2：`beginExternalEquippedFrame()` / `finally endExternalEquippedFrame()` 隔离「挂枪」路径。
6. `src/main/java/com/tacz/guns/client/model/BedrockAnimatedModel.java`
   —— A：新增 `hasBuiltinHandModel()`（只读查询，子树求 cube，实例级缓存）+ 完整实机证据 javadoc。
7. `src/main/java/com/tacz/guns/client/model/BedrockGunModel.java`
   —— A：构造器里按 `hasBuiltinHandModel()` 决定手部功能渲染器工厂返回 `null`（画模型自带手）
   还是 `new LeftHandRender/RightHandRender(...)`（上游行为兜底）；新增 `tacz$builtinHandDecision(...)`
   打一次 `[TACZ-DBG] arm-builtin`。
8. `src/main/java/com/tacz/guns/client/model/functional/{RightHandRender,LeftHandRender}.java`
   —— 仅补 javadoc：说明本类现在是**兜底路径**、以及「为什么跳过判定必须放在注册处而不是本方法里」。
9. `src/main/java/com/tacz/guns/client/model/RenderHelperCompat.java`（续）
   —— A：新增 `MIN_HAND_ANCHOR_DISTANCE = 0.25F` 兜底（锚点贴脸则不画），诊断打印移到 GL 设置之前并保留。

> 注：挂枪（`hotbar_show`）的姿态也会跟着第 ② 层修复一起变化（同一层变换），方向应更正确
> （原来是被 2D 纸片变换包夹），需实机确认一眼。

## 五、本轮仍未修 / 交给 Lead 决定

| # | 级别 | 项 | 原因 |
|---|---|---|---|
| A-2 | ✅ 已结案 | 手臂成因已由实机日志定论并修好（自带手模型 → 画模型自带手） | 见 §三 A |
| A-3 | P3 | 若某第三方枪包**没有**自带手但仍把 `righthand_pos` 摆得贴脸，靠 `MIN_HAND_ANCHOR_DISTANCE` 兜底 | 兜底已加，置 0 可关 |
| B-1 | P1 | 开镜常数 X 偏移**仍未取得日志** | 等 `[TACZ-DBG] ads` 一次（打印已放宽，短按也不会漏） |
| C-3 | — | 第三人称手持姿态的方向只能目视 | 纯几何结果，已有单点开关 `ENABLE_EQUIPPED_PAPER_FRAME_FIX` |
| — | — | 时钟修复**已由 Lead 完成**，本报告只做复核（结论：完整） | — |




---

# 实机回归（第 3 轮）· T1 / T2 / #1 / #2 / #3

> 用户新给的 3 条 + Lead 从截图读出的第三人称 2 条。**禁 gradle、只 lint**；
> 本轮改动 4 个文件（`AnimateGeoItemRenderer`、`CameraSetupEvent`、`FirstPersonRenderGunEvent`、
> `FirstPersonRenderHandler`），`lint_files(error)` = **0**。
> ⚠ `HumanoidOffhandRender` 已由 Lead 接管，本轮**未再改动**。

## T1 · 第三人称手持枪：变换帧改成「替换」而不是「删除」

**根因**：补偿后坐标系只剩**手臂骨骼帧**，而 1.20 侧 BEWLR 收到的是「手臂骨骼帧 × 物品显示帧」
⇒ 少了 `Rx(-90)·Ry(180)·T(1/16,0.125,-0.625)` 那一组旋转，枪模保持 1.7.10 手臂帧里的竖直姿态
（= 截图里「竖着立在身体右侧半格处的高步枪」，半格 ≈ `T(1/16, 0.125, -0.625)` + 轴交换）。

**落地（`AnimateGeoItemRenderer#tacz$buildPaperFrameInverse`）——与 Lead 给的公式逐项一致**：

```
f  = T(-1/16, 7/16, 1/16)        // RenderPlayer#renderEquippedItems:319
   · T(0.25, 0.1875, -0.1875)    // :380
   · S(0.375,0.375,0.375)        // :381
   · Rz(60°)                     // :382
   · Rx(-90°)                    // :383
   · Rz(20°)                     // :384
   · T(-0.5,-0.5,-0.5)           // ForgeHooksClient#renderEquippedItem:219（EQUIPPED_BLOCK=true 分支）
= F17 · T_block                   // 即 Lead 记号的 F17·T_block

f20 = Rx(-90°) · Ry(180°) · T(1/16, 0.125, -0.625)   // 1.20.1 ItemInHandLayer#renderArmWithItem

C   = inv(f) · f20                // = inv(F17·T_block) · F20  ✔ 与 Lead 公式一致
```

- `S(0.375)` 的逆是 `S(2.6667)`，由 `Matrix4f.invert()` 自动处理，未漏。
- **镜像只保留一份**：`f20` 只含两个旋转 + 一个平移，**不含镜像**；而 `GunItemTransforms#apply:25`
  自带 `scale(-1,-1,1)` ⇒ 不冲突、也不叠两次翻转（javadoc 已写明）。
- 完整链（已写进 javadoc）：
  `P · F_arm · [F17 · T_block] · C · (模型侧 Z180 …) = P · F_arm · F20 · (模型侧 Z180 …)`
  —— 即「1.20 的手臂骨骼帧 × 物品显示帧」，与上游 BEWLR 收到的坐标系一致。
- 一键回退：`AnimateGeoItemRenderer.ENABLE_EQUIPPED_PAPER_FRAME_FIX = false`。

**确定性备选（Lead 更倾向，本轮未采用）**：在 `ItemInHandLayerMixin` 里对枪自己画
`renderByItem(stack, THIRD_PERSON_RIGHT_HAND, P, …)` 并让 `handleRenderType(EQUIPPED)` 对枪返回 `false`。
采用它的前提是先确认「不双画 + 挂枪路径不吃这条链」，而挂枪路径正由 Lead 在改
（他改成 `renderByItem(FIXED, …)` 之后天然绕过，届时切换成本很低）。
本轮的公式法保留了 `beginExternalEquippedFrame/endExternalEquippedFrame` 这对钩子，
切到备选方案时可直接复用。

## T2 · 第三人称持枪姿势没应用（手臂自然下垂）

已排除 2 个嫌疑（都有 `file:line`）：
1. **`ThirdPersonManager.registerDefault` 接线正常**：`ClientSetupEvent.java:125`
   `step("ThirdPersonManager.registerDefault", ThirdPersonManager::registerDefault)`；
   `ThirdPersonManager#registerDefault:85-88` 注册 `default` + `mini_gun`，
   `getAnimation(name):97-99` 缺失时回落 `DEFAULT` ⇒ 一定拿得到动画对象。
2. **`InnerThirdPersonManager` 的早退闸门不是罪魁**：
   `PlayerAnimatorCompat#canPlayAnimation:118-131` 判的是「手上是不是枪 + 姿态是否允许」，
   **不依赖** PlayerAnimator 是否安装 ⇒ 未装库时也返回 `true`；
   `AnimationRenderContext.isFirstPersonHand(...)` 依赖 `AnimationRenderContext.hand(entity)` 建立的
   ThreadLocal 作用域，而全仓 **`AnimationRenderContext.hand(` 0 个调用点** ⇒ 恒 `false`，不会误拦。

⇒ **剩余嫌疑收敛到**：`PlayerModelRenderState` 是否真的每帧把手臂旋转写进
`ModelBiped.bipedRightArm/LeftArm`，以及 `ThirdPersonManager` 里 `DEFAULT.animateGunHold/animateGunAim`
的实现是否真的改含 `ModelRenderer` 的 rotateAngle。**本轮时间用尽，未完成这一项的定位**，
列为下一轮第一优先（不虚构结论）。

## #1 · 第一人称手部缺面 —— 近平面裁剪（已修）

- 依据：手部 pass 近平面原为 `0.05F`（沿用 vanilla `EntityRenderer.java:770`），但默认枪包
  `lefthand_pos` 锚点实测只有 **0.09 格**（`origin.z = -0.091`），手方块本体 0.25 格深
  ⇒ 相当一部分落在 0.05 近平面内、甚至相机背后 ⇒ 被投影裁掉 = 缺面。
- 落地：`FirstPersonRenderHandler.NEAR_PLANE = 0.01F`（`public static`，置回 `0.05F` 即回退）。
  **不会影响世界深度精度**：手部 pass 进入时已 `glClear(GL_DEPTH_BUFFER_BIT)`，只画枪与手，
  世界 pass 仍用 `setupCameraTransform` 自己那份 0.05 投影。
- 已排除：`mcshim.RenderType.entityCutout` 是 `cull = false`（r2 有意为之），
  所以**不存在背面剔除导致的缺面**；UV 落边缘的可能性也因「缺面是整片而不是零星」而低。

## #2 · 换弹/切枪时第一/第三人称都剧烈抖动 —— 相机偏移记账不自愈（已修）

**根因（与「两个视角一起抖」完全吻合）**：抖的是 `player.rotationYaw/rotationPitch` 本身。
`CameraSetupEvent#applyLevelCameraAnimation` 旧写法依赖「同一帧 START 与 END 严格配对一次」：
只要 END 因异常/提前 return 未执行、或 START 跑两次只 END 一次、或跨帧错配，
偏移就**永久留在 rotation 上**，随后每帧再叠新的 delta ⇒ 逐帧累积/振荡。
第三人称相机读同一对字段 ⇒ 一起抖。

**上游依据**：1.20 用 `ViewportEvent.ComputeCameraAngles`，每帧由 `GameRenderer` 算完相机姿态
**立刻消费**，既不修改实体 `rotation*`，也**没有任何跨事件配对状态** ⇒ 天然不可能累积。

**落地（不依赖配对的自愈记账）**：
- `END`：无条件 `revertCameraAnimationOffset()`（幂等哨兵 `cameraAnimationOffsetPresent`）；
- `START` **开头先自愈**：若上一帧偏移还挂着先扣回，保证任何时刻每个偏移最多存在一帧；
- 只记录「真正加上的量」（并对 pitch 用同一处 `Mth.clamp`），保证减掉的等于加上的。
- 诊断：`[TACZ-DBG] cam dYaw=… dPitch=…`（按 0.1° 去重）。期望 |delta| 0~8°；
  若看到 ±30° 以上且逐帧正负跳变 ⇒ 转为动画数据/符号问题（可直接对照枪包 `*.animation.json` 的 `camera` 通道）。

**另两条已核对**：
- `applyCameraRecoil` 也写 `rotation*`，但它**只在开火后的样条窗口内**生效，且与动画偏移是
  「各自独立的增减」；改成自愈记账后，两者无论谁先跑都不会叠加残留（旧写法下若顺序翻转才会振）。
- `GunHurtBobTweak` / `FirstPersonRenderGunEvent#applyAnimationConstraintTransform` 只改
  模型节点 / GL_MODELVIEW（`poseStack.applyToModelView()`），**不写** `rotation*` ✅。

## #3 · 开镜后枪模偏向同一侧 —— 共用逻辑已被证明与上游逐字节相同，剩下是数据/帧

**已证明不是移植逻辑错**（token 级比对，去注释去空白后逐字符相同）：
| 函数 | 结论 |
|---|---|
| `FirstPersonRenderGunEvent#applyFirstPersonPositioningTransform` | 与上游**逐字节相同**（唯一差异 `CompoundTag`→`NBTTagCompound` 平台重命名）⇒ 含 `mountOffsetInsertIndex = scopeNodePath.size() - 1`、`viewIndex = views[zoomNumber % views.length] - 1` 全部与上游一致 |
| `#getPositioningNodeInverse(List, Vector3f, int)` | **完全相同**（含 mountOffset 的 `-x/16, +y/16, -z/16` 三个符号） |
| `#getScopeMountOffset` | **完全相同** |
| `ClientAttachmentIndex` 的 `views` / `views_fov` 读取 | **完全相同**（`views` 取 `display.getViews()`、`viewsFov` 取 `display.getViewsFov()`，没有读串） |
| `CameraRotateListener` / `CameraAnimationObject` | **完全相同** |

**新增诊断**（`[TACZ-DBG] scope viewIndex=… zoomNumber=… mountInsert=… mountOffset=… path=…`，
按内容去重）：一次性给出 `viewIndex` / `zoomNumber` / mountOffset 插入位置 / 读到的 `scope_mount_offset`
/ 最终取逆的节点名链。判读：
- 完全开镜时 `path` 末段应是 `scope_view_N`（`N = viewIndex + 1`）、`mountOffset` 非 `null`；
- 若 `mountInsert = -1` ⇒ `scopeNodePath` 为 `null`（该枪/该瞄具没配 `scope_pos`）⇒ 定位退化为机瞄路径 =
  **常数方向偏移**，与现象吻合，属**数据侧**；
- 若 `path` 里没有 `scope_view` ⇒ 瞄具变体（`scope_view_N`）没被解析 ⇒ 查 `ClientAttachmentVariantIndex` 的加载。

**残留疑点（下一轮）**：`F_20` 是否真的含 `Rx(-90)·Ry(180)`（取决于 1.20 基座帧），
它同时影响 #1 的手部位置与 #3 的「识别到的偏移量」；T1 的公式法已经把 `F_20` 纳入，
若 T1 实机对得上，说明 `F_20` 取值正确，#3 就可以只看 `[TACZ-DBG] scope` 那条。


---

# 实机回归（第 4 轮）

> 本轮改动 1 个文件（`RenderHelperCompat`：关闭 `MIN_HAND_ANCHOR_DISTANCE` 兜底，`lint` = 0）。
> **其余三条按 Lead 要求"不能猜"，本轮明确记为未定论**，并列出下一步需要的日志字段。

## 已核对（token 级 / 行号）

| 项 | 结论 |
|---|---|
| `AdsPath`（`rotation()/x()/y()/z()`） | **与上游逐字节相同**（去注释空白后 520 == 520 字符全等）⇒ 现象 #2「ADS 偏右」**不是** `adsPath.rotation(1) ≠ 1` 造成的 |
| `MIN_HAND_ANCHOR_DISTANCE` 兜底 | **已置 0（关闭）**。实机日志手臂锚点 `z` 全为正（相机背后 9~95 cm），该兜底把手臂整条跳过 = 藏症状。字段 javadoc 已写明这是「症状不是病因」并附原始日志 |
| **第三人称手持物被画两次（新发现，`file:line` 可查）** | `RenderPlayer#renderEquippedItems:187` 调了 `super.renderEquippedItems(...)` ⇒ `RenderBiped#renderEquippedItems` 自己在 `:263-264` 做 `bipedRightArm.postRender(0.0625F) + glTranslatef(-0.0625F, 0.4375F, 0.0625F)`，在 `:266-267` 查 `BLOCK_3D`、`:287` 走 `isFull3D()` 分支 / `:301+` 走 2D 纸片分支，最后 `renderManager.itemRenderer.renderItem(...)` → **我们的 `IItemRenderer.renderItem(EQUIPPED)`**；而 `RenderPlayer` 随后在 `:313-319 + :377-385` **又用另一套帧**再画一次。⇒ 手持枪每帧被画两次、两套不同帧。这与「一把在手上、一把飞出去」高度吻合，**但本轮未定论**（需确认 `RenderBiped` 那一次的帧是否就是「飞出去」的那次）。 |

## 未定论（明确记录，不猜）

### U1 · 第一人称手部骨骼落到相机背后（`origin.z > 0`）
- 现象数据（Lead 提供，AWM，`equip` 0→1）：`RIGHT z=+0.849 / +0.945`，`LEFT z=+0.243 / +0.092`，`y = −0.9 ~ −1.7`。
- 已知：`poseStack` 基座是 `glLoadIdentity()` 后的相机空间（`FirstPersonRenderHandler#onRenderHand:497-499`）；
  `GunItemRendererWrapper#renderFirstPerson` 的变换链与上游 `AnimateGeoItemRenderer#renderFirstPerson` 逐行同构（r4 §二 A 已核）。
- **未定论**。下一步需要的日志字段（已有一半，需补两处）：
  1. 在 `GunItemRendererWrapper#renderFirstPerson` 里，`poseStack.pushPose()` 之后、`applyFirstPersonGunTransform` **之前**打一次 `pose=(m30,m31,m32)`（应恒为 `(0,0,0)`）；
  2. `applyFirstPersonGunTransform` **之后**再打一次（应≈ `(0,0,-0.6~-1.1)` 量级）；
  3. `RightHandRender.render` 入口（`poseStack.mulPose(ZP180)` **之前**）打一次。
  ⇒ 三点定位：若 (2) 正常而 (3) 异常 ⇒ `FunctionalBedrockPart` 的骨骼链/`righthand_pos` 数据问题；若 (2) 本身就异常 ⇒ `FirstPersonRenderGunEvent` 的定位/约束变换问题。
- 另需确认：`equip` 从 0→1 的换枪动画期间，1.7.10 侧我们**是否**故意没有「抬起物品」这一步（`equip=0` 时物品本应在画面外）——
  这与「`origin.z` 为正」是否只是换枪过渡帧的正常表现，必须用上面 (2) 的数值区分，不能凭 z 的符号判定。

### U2 · ADS 枪整体偏右（`pose.x` 只从 0.419 走到 0.183）
- `AdsPath` 已证逐字节相同；`applyFirstPersonPositioningTransform` 已证 token 相同（第 3 轮）。
- **未定论**。下一步需要的日志字段：在 `applyFirstPersonPositioningTransform` 内、
  `applyMatrixLerp(transformMatrix, aimingViewMatrix, …, positioningWeight * rotationProgress)` **前后各打一次**
  `rotationProgress / positioningWeight / transformMatrix.m30 / aimingViewMatrix.m30`。
  ⇒ 若 `rotationProgress` 到不了 1 ⇒ `adsPath.rotation()` 的输入 `pathProgress` 有问题；
  若 `rotationProgress=1` 但 `transformMatrix.m30` 仍明显 ≠ `aimingViewMatrix.m30` ⇒ `applyMatrixLerp` 的权重/矩阵本身有问题；
  若两者相等而屏幕仍偏 ⇒ 判定为数据侧（`aimingNodePath` 选到的 node 不对），此时再用第 3 轮加的
  `[TACZ-DBG] scope … path=…` 一条日志即可定位。

### U3 · 第三人称手持枪飞出身体外
- 见上表「被画两次」。**未定论**：需要确认最终生效的是哪一次，以及是否两次都画。
  下一步：在 `AnimateGeoItemRenderer#renderItem(EQUIPPED)` 入口打一次「调用来源栈深度 + 当前 GL 矩阵 m30~m32」，
  或在 `RenderBiped#renderEquippedItems` / `RenderPlayer#renderEquippedItems` 各打一个计数器，一次实机就能确认是 1 次还是 2 次。


---

# 第 4 轮 · 诊断字段与判读表（一次性收敛 U1/U2/U3）

> 本轮**只加诊断日志 + 关闭兜底**，零行为改动。改动 6 个文件，`lint_files(error)` = **0**，未跑 gradle。
> 总开关：`RenderHelperCompat.DEBUG_RENDER`（语义：`true` = 打开全部节流日志，`false` = 全静默，**不改渲染行为**）。
> 帧号由 `FirstPersonRenderHandler#tickAnimation`（每帧 FML `RenderTickEvent.START`）推进
> `RenderHelperCompat.beginDebugFrame()`，帧内计数器在那里清零。
> 全部日志按「内容指纹变化才打」节流，一次实机应在几百行以内。

## 0. 撤回上一条误判

**「玩家第三人称手持物被画两次」= 误判，已撤回，未据此改任何代码。**
Lead 逐行核过 1.7.10 源码：
- `RenderPlayer` 的父类是 **`RendererLivingEntity`**（`RenderPlayer.java:33`），**不是** `RenderBiped`；
- `RendererLivingEntity#renderEquippedItems(EntityLivingBase,float)` 在 `RendererLivingEntity.java:380` 是**空方法**；
- `RenderBiped:263-410` 那段的形参是 `EntityLiving`，`EntityPlayer` 不继承它 ⇒ **对玩家不可达**。
⇒ 玩家第三人称手持物**只被画一次**（`RenderPlayer:313-419`）。
`equipped-call` / `equipped-entry` 计数保留，但**只作为证伪证据**（期望打出 `call=1`）。

## 1. 日志组与 grep

| grep | 位置 | 内容 |
|---|---|---|
| `[TACZ-DBG] p-entity m=` | `mixin/client/ItemInHandLayerMixin#renderArmWithItemHead`（`RenderPlayer#renderEquippedItems` HEAD） | `P_entity` 的 **16 个数**（`GL_MODELVIEW_MATRIX`，此时**还没**做 `bipedRightArm.postRender` 与任何纸片帧） |
| `[TACZ-DBG] arm-bone` | 同上 | `bipedRightArm` 的 `rotationPoint=(x,y,z) rotateAngle=(x,y,z) showModel= isHidden=` |
| `[TACZ-DBG] equipped-entry call=… m=` | `AnimateGeoItemRenderer#renderItem(EQUIPPED)` 入口 | 入口 `m` 的 **16 个数** + 帧内调用序号（期望 `call=1`） |
| `[TACZ-DBG] equipped-call #…` | 同上 | 调用序号 + `mvT`（GL 平移列）+ `gun` + `equip` + `external` 深度 |
| `[TACZ-DBG] u1-A / u1-B` | `GunItemRendererWrapper#renderFirstPerson` | A：`pushPose()` 之后（期望 `(0,0,0)`）；B：定位+约束之后（期望 `z<0`，量级 −0.6~−1.4） |
| `[TACZ-DBG] ads-mx pre` / `post` | `FirstPersonRenderGunEvent#applyFirstPersonPositioningTransform` | `rotProg / posWeight / idle.m30 / aim.m30 / cur.m30`，以及 lerp 后的 `tx/ty/tz` |
| `[TACZ-DBG] scope` | 同上 | `viewIndex / zoomNumber / mountInsert / mountOffset / path`（第 3 轮加的，仍在） |
| `[TACZ-DBG] cam` | `CameraSetupEvent` | 相机动画偏移 `dYaw/dPitch`（第 3 轮加的，仍在） |

## 2. 判读公式（关键：拿到一次日志就能直接算出修正矩阵 C）

有了 `p-entity`（`P_entity`）与 `equipped-entry`（`m`），**1.7.10 实际叠加的物品帧**是：

```
F_actual = inv(P_entity) · m
```

我们的假设（`AnimateGeoItemRenderer#tacz$buildPaperFrameInverse` 的 javadoc）是：

```
F_assumed = F17 · T_block
F17      = T(-1/16, 7/16, 1/16)                 // RenderPlayer:319
         · T(0.25, 0.1875, -0.1875) · S(0.375)   // :380-381
         · Rz(60) · Rx(-90) · Rz(20)            // :382-384
T_block  = T(-0.5, -0.5, -0.5)                  // ForgeHooksClient:219
```

**逐项比对清单**（按列主序，GL 的 16 个数 = `m[0..15]`，其中 `m[12],m[13],m[14]` 是平移）：

| 项 | 期望（`F_assumed`） | 若不符 ⇒ 结论 |
|---|---|---|
| 平移列 `m[12..14]` | 由 `F17` 的旋转把 `T_block` 的 `(-0.5,-0.5,-0.5)` 与 `T(-1/16,7/16,1/16)`、`T(0.25,0.1875,-0.1875)` 合起来的结果 | 差一个**常数平移** ⇒ 说明 `T_block` 那层不存在（`EQUIPPED_BLOCK` 没生效）或多了一层 |
| 基向量长度（缩放） | **三个都应 = 0.375** | 若 = 1.0 ⇒ `F17` 取错层（走的是 `isFull3D()` 或 Block3D 分支，而不是纸片分支）；若 = 0.625 ⇒ 走的是 `isFull3D()` 分支（`S(0.625,-0.625,0.625)`，注意 Y 负） |
| 旋转部分 | `Rz(60)·Rx(-90)·Rz(20)` 的合成 | 若含 `Rx(-20)` 或 `Ry(45)` ⇒ 走的是 `RenderBiped` 的其它分支或 `ForgeHooks` 的另一条 |

**得到 `F_actual` 后，修正矩阵就是**（把「假设帧」换成「实测帧」）：

```
C = inv(F_actual) · F_20                      // 右乘进 PoseStack，JOML 同序
F_20 = Rx(-90) · Ry(180) · T(1/16, 0.125, -0.625)
```
即把 `AnimateGeoItemRenderer#tacz$buildPaperFrameInverse()` 里的 `f` 换成**实测的 `F_actual`**（或直接改成用
`ItemInHandLayerMixin` 抓到的 `P_entity`/`m` 现算），`F_20` 不变。

**`P_arm` 的核对**：1.20 `translateToHand(arm)` = `T(rotationPoint/16)` + 骨骼旋转。
若 `arm-bone` 打出 `rotationPoint=(−5,2,0)`、`rotateAngle≈0`，则 1.7.10 的 `bipedRightArm.postRender`
与 1.20 的 `translateToHand` **同构** ⇒ `F_actual` 与 `F_20` 可以直接按上式组合；
若 `rotateAngle` 明显非 0（例如换弹姿态），那部分**不该**被 `F_actual` 消掉（它在 `P` 里，两边共有）。

## 3. U1 / U2 / U3 判读表

### U1 · 第一人称手部骨骼落到相机背后（`origin.z > 0`）
| 观察 | 结论 | 下一步 |
|---|---|---|
| `u1-A ≈ (0,0,0)`、`u1-B` 的 `z ≈ −0.6~−1.4` | 定位链正确 | 问题在**骨骼链**（`righthand_pos` 的 pivot/父链），下一步查枪包 `righthand`→`righthand_pos` 的 pivot 与 `BedrockPart#translateAndRotateAndScale` |
| `u1-A ≈ (0,0,0)`、`u1-B` 的 `z > 0` | 定位链本身就画到相机背后 | 问题在 `applyFirstPersonPositioningTransform` 的**输入**（与 U2 同一根因） |
| `u1-A ≠ (0,0,0)` | `poseStack` 基座不是单位阵 | 查 `renderFirstPerson` 之前的 `pushPose` 配对 / 上次异常残留 |
| `u1-B` 正常但 `equip < 1` 期间异常 | **只是换枪过渡帧**（1.7.10 vanilla 的 `equippedProgress` 抬枪位移我们没做） | 若不是 bug，只需在 `equip < 1` 时跳过绘制或补抬枪位移 |

### U2 · ADS 偏右
| 观察 | 结论 | 下一步 |
|---|---|---|
| `ads-mx pre` 的 `rotProg` 到不了 1 | `adsPath.rotation()` 的输入/输出有问题（`AdsPath` 已证与上游逐字节相同 ⇒ 查 `pathProgress`） | 查 `aimingProgress` 与 `adsPath` 数据 |
| `rotProg=1`、`posWeight=1`，但 `post.tx` ≠ `aim.m30` | `applyMatrixLerp` 权重语义与上游不同 | 逐行核 `MathUtil.applyMatrixLerp` |
| `post.tx == aim.m30`，但屏幕仍偏 | `aimingNodePath` 选到的 node 不对（数据侧） | 看 `[TACZ-DBG] scope … path=…`；若 `mountInsert = -1` ⇒ 没配 `scope_pos` |

### U3 · 第三人称手持枪飞出
| 观察 | 结论 | 下一步 |
|---|---|---|
| `equipped-entry call=1` | 只画一次（与 Lead 的源码结论一致，**证伪了我上一轮的误判**） | 直接用 §2 的 `F_actual` 算 `C` |
| `equipped-entry call=2` 且两次 `m` 不同 | 确实双重绘制 | 才需要 `cancel` 其中一个 |
| `F_actual` 的缩放 = 1.0 / 0.625 | 分支取错（纸片 vs full3D vs Block3D） | 按实测值改 `F_actual` 后套 §2 的 `C` |

