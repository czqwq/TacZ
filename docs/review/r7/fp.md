# r7-fp · 第一人称渲染 / 位置 / 相机 / FOV / 手臂 / 手持动画 上游等价性审查（task-11）

> **写作用域**：`client/renderer/item/**`、`client/event/{FirstPersonRenderGunEvent,FirstPersonRenderEvent,CameraSetupEvent}.java`、
> `client/model/RenderHelperCompat.java`、`mixin/client/{GameRendererMixin,ItemInHandRendererMixin}.java`、本报告。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）；
> MC 1.20.1：`tmp/minecraft-src-1.20.1/src/main/java/**`；Forge 1.20.1：`tmp/MinecraftForge-1.20.1/{src,patches}/**`；
> 1.7.10：`build/rfg/minecraft-src/java/**`（原版 + Forge 反编译）。
> **自查**：IDEA MCP `read_file/search_text/lint_files/get_file_problems`。**未运行任何 gradle 任务**。
> 改动文件 `lint_files(min_severity="error")` = **0 error**。历史结论（r2/r4/r5/r6 已定案）不翻案，只在「新上游证据」处修订并注明。
>
> 与 r7 同轮队友的交接：`AnimateGeoItemRenderer` 的 P0/P1（第三人称手持/掉落物帧）由 Lead 裁决**交给本模块落地**，
> 技术依据来自 r7-tp；本报告 §2 的 F-3/F-4 记录了复核结论与落地位置，**修法在本文件**。

---

## 一、结论速览

| 项 | 结论 |
|---|---|
| **第一人称链路是否等价** | **等价**。`RenderHandEvent`（1.7.10 `EntityRenderer.java:1434`）→ SBM `FirstPersonRenderHandler#onRenderHand` → `GunItemRendererWrapper#renderFirstPerson` → `FirstPersonRenderGunEvent.applyFirstPersonGunTransform` → `BedrockGunModel#render` 每一跳都有真实调用点；`FirstPersonRenderGunEvent` 在去掉注释/DBG 后与上游 `:130-478` **逐行相同**；`GunItemRendererWrapper#renderFirstPerson` 与上游 `:173-272` 语义相同（差异全部是有据降级/移植加固，见 §3）。 |
| **本轮找到的真偏离** | **3 项**：① 第一人称枪的「原版视角延滞抵消对」在 1.7.10 缺一半（净效果多了一次反向旋转）；② 世界内物品（第三人称手持/掉落物/GUI 之外的 context）light 写死满亮，夜里发光；③ 第三人称手持枪整体偏 `T(0.5,0.5,0.5)`（模长 **0.866 格**，r7 截图 a 的真因）+ 掉落物偏小一半/不转不浮/不居中。 |
| **修了什么** | **5 项**（P0×1 / P1×3 / P2×1）+ 清理本 scope 全部逐帧 `[TACZ-DBG]`（29 → 0）。第 5 项 = **C-1**（第三人称枪口粒子的世界坐标缺 GL_MODELVIEW，r7-tp 发现、Lead 指派本模块落地，见 §11）。 |
| **未修** | **2 项**：弹药盒/配件的「item model `display` 变换」在 1.7.10 完全无消费端（独立缺口，跨模块）；相机动画走 `player.rotation*` 导致第一人称 sway 额外吃到该偏移（1.7.10 无相机角度事件，残留量级 = 动画幅度 0~8°）。 |
| **有据降级** | 6 项（手部投影 FOV、相机动画/recoil 通道、世界 FOV zoom 的额外一阶平滑、无独立袖子骨骼、`ItemInHandRendererMixin` 对枪械不可达、VBO 快速路径整体关闭），逐条给证据（§4）。 |
| **截图线索归属** | `docs/review/r7/screenshots/a_thirdperson_gun_rangefinder.png` = **第三人称（F5 前视角）+ HeatBarOverlay + 准星**，不是第一人称、也不是 rangefinder；图里的枪异常 = 第三人称 EQUIPPED 帧偏移（已由本轮 P0 修）。详见 §5。 |

---

## 二、发现表（逐条：本工程 file:line ↔ 上游/1.7.10 file:line）

### F-1（P1）第一人称枪多了一次「反向视角延滞」旋转 —— 上游 `-0.1Δ` 的抵消对象在 1.7.10 不存在

**现象**：快速转视角（甩枪/急转）时，第一人称枪会朝**与视角移动相反**的方向多甩一下；静止、慢速转视角看不出来。
上游不会有这个动作（那对旋转是**互相抵消**的）。

**根因链（三份证据）**：
1. 上游 1.20 的手部父帧里有原版施加的一对延滞旋转：
   `ItemInHandRenderer#renderHandsWithItems` 先
   `mulPose(Axis.XP.rotationDegrees((getViewXRot - xBob) * 0.1F))` + 同式 Y
   （`tmp/minecraft-src-1.20.1/.../ItemInHandRenderer.java:332-333`）。
2. Forge 把 `RenderHandEvent` 判给模组时，**替代**掉了 `renderArmWithItem`（父帧不再有其他变换）：
   `tmp/MinecraftForge-1.20.1/patches/minecraft/net/minecraft/client/renderer/ItemInHandRenderer.java.patch`
   （`renderSpecificFirstPersonHand(...)` 放在 `renderArmWithItem` 调用**之前**，返回 true 就整段跳过）。
   ⇒ 上游 `renderFirstPerson` 收到的 poseStack = `单位矩阵 · Rx(+0.1Δ)·Ry(+0.1Δ)`。
3. 上游 `GunItemRendererWrapper#renderFirstPerson` 随后施加 `Rx(-0.1Δ)·Ry(-0.1Δ)`（上游 `:203-204`），
   注释写明「**逆转**原版施加在手上的延滞效果」⇒ 上游净效果 ≈ 单位矩阵（精确表达式 `Rx(a)Ry(b)Rx(-a)Ry(-b)`）。
4. **1.7.10**：`RenderHandEvent` 在 `EntityRenderer.java:1434` 派发，SBM handler cancel 后
   `EntityRenderer#renderHand`（含 `:778-792` 的 `glLoadIdentity` 与 `ItemRenderer#renderItemInFirstPerson:277-278`
   的原版 `+0.1Δ`）**整段跳过**；SBM 用 `GL11.glLoadIdentity()` + `new PoseStack()` 重建相机空间
   （`.../v1/client/handler/FirstPersonRenderHandler.java:461-463,438`）⇒ 父帧里**没有**那对 `+0.1Δ`。
   只保留上游的 `-0.1Δ` ⇒ 「抵消」退化为「附加反向旋转」。

**修法**（保持上游两行原样，把被平台跳过的输入补回来，与 r5 F-2「补 xBob 而不是改公式」同一思路）：
`AnimateGeoItemRenderer#applyVanillaFirstPersonHandLag(...)`（`:411`）在 `GunItemRendererWrapper#renderFirstPerson:229`
与基类 `AnimateGeoItemRenderer#renderFirstPerson:433` 里、上游 `-0.1Δ` 之前补 `+0.1Δ` 的 X/Y 两转。
组合恢复成上游的 `Rx(a)Ry(b)Rx(-a)Ry(-b)`，净效果与 1.20 逐值一致。

> 该修正**不改任何观感常数**；只在「快速转视角」时把多出来的一次反向甩动去掉。

### F-2（P1）世界内物品 light 写死 `0xF000F0` ⇒ 第三人称手持枪/掉落物/手持弹药夜晚满亮

**证据**：
- 上游：`BlockEntityWithoutLevelRenderer#renderByItem` 的 `packedLight` 由调用方给 ——
  `ItemInHandLayer#renderArmWithItem` 传 `LivingEntityRenderer` 的实体亮度；`ItemEntityRenderer#render:93` 传实体位置亮度；
  `ItemRenderer#render:128-129` 之后才进 BEWLR。
- 1.7.10：实体渲染前 `RenderManager#doRender` 已把实体亮度写进 lightmap 纹理单元
  （`build/rfg/.../RenderManager.java:267-276`）；`Tessellator#setBrightness` 是**逐顶点覆盖**
  （`Tessellator.java:122-129,413-416`）⇒ 传 `0xF000F0` 会盖掉实体亮度。
- 本工程（修前）：`AnimateGeoItemRenderer#renderItem:194`、`AmmoItemRenderer#renderItem`、`AttachmentItemRenderer#renderItem`
  全部写死 `0xF000F0`（第 4 参）。

**修法**：`RenderHelperCompat#packedLightFor(ItemRenderType, Object[] data)`（`:315`）：
`EQUIPPED`/`EQUIPPED_FIRST_PERSON`/`ENTITY` 且 `data[0] instanceof Entity` → `entity.getBrightnessForRender(ClientTickHelper.getPartialTick())`；
其余（`INVENTORY`/`FIRST_PERSON_MAP`）→ `LightTexture.FULL_BRIGHT`（GUI 阶段 lightmap 纹理单元未启用，值无影响）。
三处 `renderItem` 改为调用它。

### F-3（P0）第三人称手持枪整体偏 `T(0.5,0.5,0.5)`（模长 0.866 格）—— 纸片帧逆补偿的目标帧少了无条件的 `-0.5`

**证据**（r7-tp 定位，本模块复核）：
- 1.20 `ItemRenderer#render`：`bakedmodel.getTransforms().getTransform(ctx).apply(flag, poseStack);`
  **之后**无条件 `posestack.translate(-0.5F,-0.5F,-0.5F);` 才 `blockEntityRenderer.renderByItem(...)`
  （`tmp/minecraft-src-1.20.1/.../entity/ItemRenderer.java:128-129`）；Forge 47 补丁保留该行
  （`tmp/MinecraftForge-1.20.1/patches/minecraft/net/minecraft/client/renderer/entity/ItemRenderer.java.patch`，`m_252880_(-0.5F,-0.5F,-0.5F)` 未被改）。
- 枪的物品模型 `assets/tacz/models/item/modern_kinetic_gun.json` 只有 `{"parent":"builtin/entity","gui_light":"front"}`，
  **无 display 段** ⇒ display 变换恒等，但 `-0.5` 照旧存在。
- 1.20 的完整物品帧：`ItemInHandLayer#renderArmWithItem`：`translateToHand` → `Rx(-90)` → `Ry(180)`
  → `T(±1/16, 0.125, -0.625)`（`tmp/minecraft-src-1.20.1/.../layers/ItemInHandLayer.java:54-61`），再叠 `T(-0.5,-0.5,-0.5)`。
- 1.7.10 侧基座帧 `F_paper`（`T(-1/16,7/16,1/16) · T(0.25,0.1875,-0.1875) · S(0.375) · Rz(60) · Rx(-90) · Rz(20) · T(-0.5,-0.5,-0.5)`）
  是 r5 C-1 逐句抄出来的（`AnimateGeoItemRenderer#tacz$buildPaperFrameInverse`）。
  `C = inv(F_paper)·F_20` 只有在 **F_20 = 1.20 BEWLR 实收帧** 时才使 `F_arm·F_paper·C == F_arm·F_20`；
  修前 F_20 少了 `T(-0.5)` ⇒ 净差 `T(0.5,0.5,0.5)`（挂点/枪口世界位移模长 0.866 格，再乘以实体 0.9375 缩放 ≈ 0.81 格）。

**修法**：`AnimateGeoItemRenderer#tacz$buildPaperFrameInverse` 的 `f20` 末尾补 `translate(-0.5F,-0.5F,-0.5F)`
（`AnimateGeoItemRenderer.java:235`），javadoc 的 F_20 公式同步补该平移并引用 `ItemRenderer.java:128-129`。
`ENABLE_EQUIPPED_PAPER_FRAME_FIX=false` 的单点回退语义不变。

**与 r7 截图的关系**：见 §5。

### F-4（P1）掉落物（ENTITY）偏小一半 / 不自转不浮沉 / 没居中；展示框（FIXED）没居中

**证据**（1.7.10 ↔ 1.20 逐段）：
| 环节 | 1.7.10 | 1.20 |
|---|---|---|
| 父帧 | `RenderItem#doRender:95` `T(x, y+bob, z)`；`ForgeHooksClient#renderEntityItem:89-104`：`ENTITY_ROTATION` 时 `glRotatef(spin)`、`ENTITY_BOBBING` 时**跳过** `T(0,-bob,0)`；`:147` 固定 `S(0.5)`（非方块物品走 `else` 分支） | `ItemEntityRenderer#render:66-69` `T(0, bob+0.25, 0)·Ry(spin)`（无 S(0.5)）；`:93` → `ItemRenderer#render(GROUND)` → 恒等 display + `T(-0.5,-0.5,-0.5)` |
| 展示框 | `RenderItemFrame` 置 `RenderItem.renderInFrame=true` 后复用 ENTITY 入口（`RenderItemFrame.java:248-250`），`ForgeHooksClient:118-124` 的 frame 分支会加 `S(1.25)/T(0,0.05)/Ry(-90)`（仅 `is3D` 分支） | `ItemFrameRenderer.java:107-111` `S(0.5)` + `renderStatic(FIXED)`（display 恒等 + `T(-0.5,-0.5,-0.5)`） |

修前本工程：`shouldUseRenderHelper` 只认 `EQUIPPED+EQUIPPED_BLOCK` ⇒ ENTITY 既不转也不浮（bob 被抵消）、还多 `S(0.5)`；
`renderItem(ENTITY)` 也没补 `+0.25`/`-0.5`/居中。

**修法**（`AnimateGeoItemRenderer.java:105-114`、`:148-162`）：
- `shouldUseRenderHelper`：`ENTITY && !RenderItem.renderInFrame` → `ENTITY_ROTATION`/`ENTITY_BOBBING` 返回 true；
  展示框模式两者返回 false（1.20 FIXED 不转不浮）。
- `renderItem(ENTITY)` 非展示框：`scale(2,2,2); translate(-0.5,-0.25,-0.5)`（= `inv(S0.5)·T(0,0.25,0)·T(-0.5,-0.5,-0.5)`）→ `renderByItem(GROUND)`；
  展示框：`translate(-0.5,-0.5,-0.5)` → `renderByItem(FIXED)`（1.7.10 的 `S(0.5)` 已在 `ForgeHooksClient:147` 施加）。
- 顺带删掉已无调用者的 `beginExternalEquippedFrame/endExternalEquippedFrame` 作用域标记：
  r7-tp 已把 `HumanoidOffhandRender` 改成直接 `renderByItem(FIXED, …)`，该标记成为死代码；
  `renderItem(EQUIPPED)` 现在无条件做纸片帧补偿（正是真实 `RenderPlayer` 路径需要的）。

### F-5（P2）清理逐帧 `[TACZ-DBG]` 日志（29 → 0，保留 1 个无日志共享钩子）

| 文件 | 清理前 | 清理后 | 处理 |
|---|---|---|---|
| `client/event/FirstPersonRenderGunEvent.java` | 12 | 0 | 删 `tacz$logAds`（ADS 逐帧/边沿）、`tacz$logScopePath`（开镜节点链）+ `RenderHelperCompat.debugLog` ×2 + 只服务日志的 3 个局部量 |
| `client/model/RenderHelperCompat.java` | 8 | 0 | 删 `tacz$logArmPose`/`tacz$equippedProgress`（反射读 `ItemRenderer#equippedProgress`）/`currentModelViewTranslation`/`countInFrame`/`debugLog`/`debugFrameId` 的日志体；**保留** `DEBUG_RENDER` + `beginDebugFrame()`（`FirstPersonRenderHandler` 仍调用，r7-sbm 侧） |
| `client/event/CameraSetupEvent.java` | 3 | 0 | 删 `tacz$logCameraAnimation` + `tacz$lastCamLog` |
| `client/renderer/item/AnimateGeoItemRenderer.java` | 3 | 0 | 删 EQUIPPED 双重绘制/入口矩阵诊断整段（含 `GL_MODELVIEW` 读取） |
| `client/renderer/item/GunItemRendererWrapper.java` | 3 | 0 | 删 `tacz$dbgPoint`（U1 A/B 点） |

### F-6（P3，文档纠正）`GameRendererMixin#tacz$applyCameraRoll` 的 javadoc 说「当前无人写入 ⇒ roll 恒 0」

实际写入端是 `CameraSetupEvent#applyLevelCameraAnimation`（每帧算完 roll 后 `CameraStateCache.setCameraRoll`，无动画时清零），
消费者 3 处（`mixin` 的 `orientCamera` RETURN、`MuzzleParticleManager`、`GunItemRendererWrapper`）。已改正注释，无行为变化。

---

## 三、逐文件对照表（本模块全量）

### 3.1 `client/event/FirstPersonRenderGunEvent.java`（542 行 ↔ 上游 478 行）

| 本工程 | 上游 | 结论 |
|---|---|---|
| `cancelItemInHandViewBobbing` `:108-121` | `:83-92` | ≡（`LocalPlayer`→`EntityPlayerSP`；事件总线由 `ClientProxy` 注册） |
| `onGunFire` `:123-152` | `:94-121` | ≡（含 `ThirdPersonMuzzleParticleManager.onShoot`/`MuzzleFlashRender.onEntityShoot`，上游同有） |
| `applyFirstPersonGunTransform` `:160-183` | `:130-143` | ≡（`REFIT_OPENING_DYNAMICS`/`AIMING_DYNAMICS`/`adsPath` 消费顺序一致） |
| `applyFirstPersonPositioningTransform` `:193-310` | `:153-253` | **≡**：`scopeId`/`getBuiltInAttachmentId`/`views[zoomNumber % len]`/双 view 插值（`SWITCH_VIEW_DYNAMICS`）/`idleScopeHeightAdjust/16`/`adsPath.rotation`+三轴分时修正/`translate(0,±1.5,0)` 全部逐行相同（本轮只删 DBG，未改任何算式） |
| `getPositioningNodeInverse` `:315-344` | `:259-286` | ≡（含 `scopeMountOffset` 的 `(-x, +y, -z)/16` 与 `mountOffsetInsertIndex`） |
| `getScopeMountOffset` `:349-371` | `:288-310` | ≡（slot adapter + variant 两份 `mount_offset` 叠加，`-1` 插入位） |
| `applyShootSwayAndRotation` `:370-382` | `:312-324` | ≡（`SHOOT_X_SWAY_NOISE/16·progress·(1-aiming)`、`-SHOOT_Y_SWAY/16`、`Axis.YP` noise） |
| `applyJumpingSway` `:384-429` | `:326-370` | ≡；平台替换 `yOld/getY()`→`lastTickPosY/posY`（`Mth.lerp` float 重载）。**上游自身缺陷**：`partialTicks==0` 时 `velocityY` 除零，两边同样存在，保真未修 |
| `resetTimestamps` / `getAnimationConstraintTransform` / `applyAnimationConstraintTransform` | `:372-477` | ≡ |
| ⚠ `bulletFromPlayer` `:153-157` | `:123-128` | ≡，且**上游同样无调用者**（上游自身死代码，按「保真」保留） |

对照 1.20 `ItemInHandRenderer.java:277-278` + `EntityRenderer.java:1434` 的另一处：**F-1 见 §2**。

### 3.2 `client/renderer/item/GunItemRendererWrapper.java`（547 行 ↔ 上游 423 行）

| 本工程 | 上游 | 结论 |
|---|---|---|
| `initContext/updateContext/tryInit/tryExit/getPutAwayTime/getStateMachine/getModel/getTextureLocation` | `:72-131` | ≡ |
| `applyLevelCameraAnimation(CameraAngles…)` `:147-166` | `:133-151` | ≡；平台替换 `ViewportEvent.ComputeCameraAngles`→1.7.10 自建 `CameraAngles` 载体（消费方 `CameraSetupEvent#applyLevelCameraAnimation`） |
| `applyItemInHandCameraAnimation` `:168-187` | `:153-170` | ≡（`event.getPoseStack().mulPose(quaternion)` + `cleanCameraAnimationTransform`） |
| `renderFirstPerson` `:190-321` | `:172-272` | **语义 ≡ + 2 处本轮修订**：F-1（补回原版延滞对）、xRot 改为**不插值**的 `rotationPitch/rotationYaw`（1.20 `LocalPlayer#getViewXRot/getViewYRot` 对本地玩家不插值，`LocalPlayer.java:190-196`；`xBob` 那对仍插值）<br>**移植加固**（保留）：整段 `try/finally` 复位 `MuzzleFlashRender.isSelf/ShellRender.isSelf/setRenderHand/popPose/cleanAnimationTransform`（r4 A-5）；`MuzzleParticleManager.spawnAndBind`/`getFirstPersonModelLight` 参数与上游同 |
| `renderParticlesInModelWindow` `:329-332` | `:278-281` | ≡（`true`，粒子在枪体模板窗口内画一次） |
| `cacheMuzzlePosition` `:334-354` | `:283-306` | ≡（item/world FOV 做 `tan` 横向补偿，z 保真） |
| `updateParticleEmitterTransforms` `:359-366` | `:311-314` | ≡ |
| `renderByItem` `:368-462` | `:316-383` | ≡（FIRST_PERSON_*/THIRD_PERSON_LEFT_HAND 早退、GUI 走 slot texture、VBO→回退→LOD、`GunItemTransforms.apply`、第三人称枪口捕获）；平台替换 `MissingTextureAtlasSprite`→`InternalAssetLoader.ENTITY_EMPTY_TEXTURE` |
| `captureThirdPersonMuzzle` `:463-520` | `:386-414` | **本轮修 C-1（§11）**：世界矩阵改为 `T(RenderManager.renderPos)·VIEW⁻¹·GL_MODELVIEW·pose`（1.7.10 实体/手臂帧在 GL 里、PoseStack 是空栈）；展开后与上游 `T(camPos)·R_cam⁻¹·pose` 逐项相同。原实现用「相机 yaw/pitch + 眼位」近似，漏掉实体/手臂帧且第三人称后拉不抵消。 |
| `cameraYaw/cameraPitch` `:479-495` | —（移植新增） | 1.7.10 无 `Camera#setup` 的等价取值器，逐参等价 |

### 3.3 `client/renderer/item/AnimateGeoItemRenderer.java`（617 行 ↔ 上游 402 行）

| 本工程 | 上游 | 结论 |
|---|---|---|
| `handleRenderType` `:66-69` | —（BEWLR 无此概念） | 移植新增入口，`item != null` |
| `shouldUseRenderHelper` `:104-114` | —（1.20 无 helper 概念） | **本轮修 F-4**：EQUIPPED→EQUIPPED_BLOCK；ENTITY(非展示框)→ROTATION/BOBBING |
| `renderItem` `:134-169` | —（1.20 `ItemRenderer#render` 的槽位/显示帧由原版施加） | **本轮修 F-2/F-3(部分)/F-4**：INVENTORY GUI 缩放、EQUIPPED 纸片帧逆补偿、ENTITY 居中/半格、light 取实体亮度 |
| `tacz$buildPaperFrameInverse` `:215-252` | 1.20 `ItemInHandLayer:54-61` + `ItemRenderer:128-129` | **本轮修 F-3**：F_20 补 `T(-0.5,-0.5,-0.5)` |
| `setModel/getModel/getStateMachine/getTextureLocation/getRenderType/needReInit` | `:58-85` | ≡ |
| `initContext/updateContext`（abstract）、`getPutAwayTime`、`tryInit`、`tryExit`、`triggerAnimation`、`visualUpdate` | `:87-152` | ≡ |
| `applyLevelCameraAnimation` `:390-409` / `applyItemInHandCameraAnimation` `:413-426` | `:167-203` | ≡（四元数→yaw/pitch/roll 的同一公式；载体改 `CameraAngles`，见 3.2） |
| `applyVanillaFirstPersonHandLag` `:411` | 1.20 `ItemInHandRenderer:332-333` 的对偶 | **本轮新增（F-1）** |
| `renderFirstPerson` `:419-472` | `:215-256` | **语义 ≡ + F-1 + 不插值 xRot**；`doExtraTransforms`/状态机 update/`cleanAnimationTransform`/`popPose` 顺序一致 |
| `renderByItem` `:475-503` | `:259-275` | ≡（`ctx.firstPerson()` 早退、`translate(0.5,1.5,0.5)`+`Rz(180)`） |
| `getPositioningNodeInverse` / `applyFirstPersonPositioningTransform` | `:280-317` | ≡（基类版本只用于非枪械子类） |
| `createAnimationInstance` / `isSameItem` / `blockOffhandRender` / `getPutAwayDuration` | `:319-402` | ≡（`Minecraft#player`→`thePlayer`、`ItemStack.matches`→`areItemStacksEqual`） |

### 3.4 `client/model/RenderHelperCompat.java`（361 行 ↔ 上游 `util/RenderHelper.java:88-141` + 1.7.10 `RenderPlayer`/`ItemRenderer`）

| 本工程 | 上游 / 1.7.10 | 结论 |
|---|---|---|
| `getBufferSource/endBatch` `:57-79` | `util/RenderHelper:89-140` | 语义 ≡（1.7.10 无全局 `RenderBuffers`，按 context 缓存 `MultiBufferSource.immediate()`；同一 context 共用后统一 flush） |
| `renderFirstPersonArm` `:96-187` | `util/RenderHelper:99-128` + 1.7.10 `RenderPlayer#renderFirstPersonArm:455-462` | ≡ + r5 F-1 修复（`PlayerModelRenderState` + `AnimationRenderContext.hand(player)` 作用域；`rotateAngleX/Y/Z` 归零由 `PlayerModelMixin` 完成）；平台差异见 §4-D |
| `packedLightFor` `:315-334` | `ItemInHandLayer`/`ItemEntityRenderer` 的 `packedLight` 来源 | **本轮新增（F-2）** |
| `captureViewMatrix/viewMatrixInverse/currentModelViewMatrix` `:57-135` | 上游无对应（1.20 BEWLR 的 PoseStack 自带相机+实体帧） | **本轮新增（C-1，§11）**：每帧记录纯视图矩阵并读回 `GL_MODELVIEW`，供 1.7.10 还原「相机相对实体帧」 |
| `applyGuiSlotScale` `:348-361` | 1.20 `ItemRenderer#renderGuiItem` 的 `scale(16,-16,16)` | r6 定案，保持（`scale(16,-16,16)+translate(0,-1,0)`） |
| `displayContextOf/isFirstPerson` | —（1.20 `ItemDisplayContext` 有 9 值，1.7.10 只有 5 种 `ItemRenderType`） | 有据降级映射（`FIRST_PERSON`→`FIRST_PERSON_RIGHT_HAND`，`FIXED/HEAD`→`GROUND`） |
| `enableItemEntityStencilTest/disable…/isOptifineInstalled` | 上游同名（转 `util/RenderHelper`） | ≡ |
| `DEBUG_RENDER/beginDebugFrame/debugFrameId` | —（诊断钩子，非上游） | 保留：`FirstPersonRenderHandler` 仍调用；**无日志输出** |

### 3.5 `client/event/CameraSetupEvent.java`（508 行 ↔ 上游 275 行）

| 本工程 | 上游 | 结论 |
|---|---|---|
| `applyLevelCameraAnimation` `:144-216` | `:66-81`（`ViewportEvent.ComputeCameraAngles`） | 有据降级：1.7.10 无相机角度事件 ⇒ `RenderTickEvent.START` 把角度叠到 `renderViewEntity.rotation*/prevRotation*`、END 幂等扣回（r5 P0 定案，**不回退**）；roll 走 `CameraStateCache` → `GameRendererMixin#tacz$applyCameraRoll`。残留差异见 §4-B |
| `applyItemInHandCameraAnimation` `:222-237` | `:83-97` | 语义 ≡：事件由 SBM `compensateBeforeRenderHandEvent()` 派发（`ItemInHandRendererMixin` 的那条对枪械不可达，见 §4-E），委托/参数一致 |
| `applyScopeMagnification` `:248-279` | `:99-120` | 语义 ≡：1.7.10 `FOVUpdateEvent.newfov` 是**倍率**（`EntityPlayerSP#getFOVMultiplier` = `fovSetting` 的乘数，`EntityRenderer#getFOVModifier:486-487` 再乘回角度）⇒ 先 `×fovSetting` 换角度、算完 `÷fovSetting` 还原；`AdsFov.Channel`/`magnificationToFov`/`isSmooth` 分支一致。差异见 §4-C |
| `applyGunModelFovModifying` `:290-333` | `:122-169`（`ComputeFov` 非 configured 那半） | 语义 ≡：`modelFov`/`viewsFov[zoomNumber % len]`/`zoom_model_fov` 回落/lower 平滑/`ads_path` 曲线全部一致；结果写 `ITEM_MODEL_FOV` 供手部投影（上游写 `event.setFOV`）。早期 return 条件：上游 `!LivingEntity` 才 return，本实现每帧无条件先算（r4 修复：不能挂在 viewBobbing 闸门后） |
| `getFovProgress` `:335-345` | `:171-179` | ≡ |
| `initialCameraRecoil` `:347-396` | `:181-220` | ≡（含 `aimingRecoilModifier`、`min(sqrt(zoom),1.5)`、趴下 `crawl_recoil_multiplier`；`isSwimming/getPose`→`!isInWater && EntityStateHelper.getPose==SWIMMING`） |
| `applyCameraRecoil` `:400-431` | `:223-250` | ≡（`pitchSplineFunction/yawSplineFunction`、`xRotO/yRotO` 增量回滚）。通道差异见 §4-B |
| `revertCameraAnimationOffset` `:433-458` | —（移植新增，r5 P0） | 幂等回滚；`rotation*`+`prevRotation*` 同步（不许回退） |
| `resetTimestamp` `:461-474` | `:252-258` | ≡ + roll 清零（断线不把上一局 roll 带过来） |
| `onComputeMovementFov` `:480-504` | `:260-274` | ≡（`priority=HIGHEST` 复刻 `ComputeFovModifierEvent` 早于 `ComputeFov`） |

### 3.6 `client/event/FirstPersonRenderEvent.java`（124 行 ↔ 上游 71 行）

| 项 | 结论 |
|---|---|
| `onRenderHand(RenderHandEvent)` | **上游的 `@SubscribeEvent` 在 1.7.10 源码里就是注释掉的**（上游 `FirstPersonRenderEvent.java:23`），本类同样**不加注解** ⇒ 注册到 Forge 总线也只是空转。1.7.10 的实际绘制入口是 vendored SBM `FirstPersonRenderHandler#onRenderHand`（同一个事件、同一个 `renderFirstPerson`、并负责 cancel 原版手持物），本类再画会双重绘制 ⇒ 只保留状态机生命周期 + `OculusCompat.endBatch`。**语义等价** |
| `matches(ItemStack,ItemStack)` | 1.20 `ItemStack.matches`（count+item/metadata+NBT）的等价实现 |

### 3.7 `mixin/client/GameRendererMixin.java`（291 行 ↔ 上游 113 行）

| 上游注入 | 本工程 | 结论 |
|---|---|---|
| `renderLevel` HEAD（镜片 beginFrame） | `renderWorld` HEAD `:76-79` | ≡（`EntityRenderer.java:1183`） |
| `renderLevel` 的 `ProfilerFiller.popPush` ordinal=2（shader 镜片） | **保持注释** `:81-91` | 有据降级：1.7.10 无 shader 路径与对应段名；且与 vanilla 镜片挂点不能同时启用（会双绘） |
| `renderItemInHand` 的 `ScreenEffectRenderer` 之后（vanilla 镜片） | `renderHand` 的 `ItemRenderer#renderOverlays` 之前 `:101-106` | ≡（持枪时 `renderHand` 被跳过，改由 SBM `compensateScopeLenses()`；非枪械路径仍走本注入，两边互斥） |
| `bobHurt` HEAD | `hurtCameraEffect` HEAD `:125-140` | ≡（先 `GunHurtBobTweak`，再按 pass 派发 `RenderItemInHandBobEvent/ RenderLevelBobEvent.BobHurt`；顺序与上游同） |
| `bobHurt` 第 3 个 `rotationDegrees` 的 `@ModifyArg` | `hurtCameraEffect` 第 3 个 `glRotatef` `:168-186` | ≡ + 逐参数换算（`isFirstPerson`→`thirdPersonView==0`、`getHurtDir`→`attackedAtYaw`）；`remap=false` 必需 |
| `bobView` HEAD | `setupViewBobbing` HEAD `:193-204` | ≡（pass 分流同上；顺序核对：世界 `getFOVModifier(true):673`→`setupViewBobbing:694`；手部 `getFOVModifier(false):770`→`:791`） |
| `getFov` HEAD（pass 判定） | `getFOVModifier` HEAD `:220-223` | ≡（第二参数同义） |
| — | `getFOVModifier` RETURN 手部 FOV 覆盖 `:248-254` | 上游是 `ComputeFov` 事件的副作用；1.7.10 无事件 ⇒ 用 `ITEM_MODEL_FOV` 覆盖硬编码 70°。**持枪时该函数的手部调用点（`renderHand:770`）被跳过 ⇒ 这行对枪械不可达，真正的手部投影由 SBM `pushHandProjection()` 直接读 `ITEM_MODEL_FOV` 建立 ⇒ 等价**（见 §4-A） |
| — | `orientCamera` RETURN 相机 roll `:284-290` | 上游 `ComputeCameraAngles#setRoll` 的等价通道（1.7.10 无相机角度事件/无 `Camera` 对象），消费 `CameraStateCache`；只在世界 pass 生效（`orientCamera` 唯一调用点 `setupCameraTransform:715`） |
| — | setupCameraTransform RETURN 抓纯视图矩阵 :283-310 | 上游无对应（1.20 BEWLR 的 PoseStack 自带相机+实体帧） | **本轮新增（C-1，§11）**：RenderHelperCompat.captureViewMatrix()。此处 GL_MODELVIEW 只含相机（1.7.10 EntityRenderer.java:683 起 glLoadIdentity，:690-715 只有 hurt/view-bob/传送门/orientCamera）⇒ 第三人称枪口世界矩阵可用 VIEW⁻¹·GL_MODELVIEW 还原相机相对实体帧 |

### 3.8 `mixin/client/ItemInHandRendererMixin.java`（164 行 ↔ 上游 93 行）

| 项 | 结论 |
|---|---|
| `@Mixin(ItemRenderer)` + `@Shadow itemToRender` | 1.7.10 的 `ItemRenderer`（第一人称手持渲染器，等于上游 `ItemInHandRenderer`）；`mainHandItem`→`itemToRender`。上游另两个 `@Shadow`（`mainHandHeight/oMainHandHeight`）只在被注释掉的 `tick()` 里用 ⇒ 不声明（少一个运行期校验点） |
| `@Inject(renderItemInFirstPerson, HEAD)` 派发 `BeforeRenderHandEvent` | 语义 ≡ 上游 `renderHandsWithItems` HEAD。**但持枪时 `renderHand` 被整段取消 ⇒ 该注入不可达**，实际由 SBM `compensateBeforeRenderHandEvent()` 派发（§4-E） |
| `@Inject(updateEquippedItem, HEAD)` 空方法 | ≡ 上游 `tick()` 注入体**整段被注释掉**（保真：不引入上游没打开的行为） |
| `keep/getCurrentItem`（`KeepingItemRenderer`） | ≡（`GunTime` epoch/时限、`itemToRender` 顶替） |

### 3.9 `client/renderer/item/` 其余文件

| 文件 | 对照 | 结论 |
|---|---|---|
| `GunItemTransforms.java` | 上游 65 行 | **逐行 ≡**（`T(0.5,2,0.5)·S(-1,-1,1)`、FIXED/GROUND/THIRD_PERSON 定位组反向链、`*scale` 与 `(1.5-y/16)*scaleY`） |
| `SbmGunTransforms.java` | 上游 66 行 | **逐行 ≡**（`bedrockToSbm`、`XP/YP/ZN` 绑定欧拉、`bindX/scale/16` 与 `pivotX*scale` 根节点、`applyBonePivot`） |
| `StaticGunRenderResult.java` | 上游 13 行 | ≡（record + Status + 三个常量） |
| `ExternalGunItemVboRenderer.java` | 上游 86 行 | **有据降级**：1.7.10 无 VBO 后端（`mcshim.BufferBuilder` 是 `float[]`、`BufferUploader` 每批重灌 `Tessellator`）⇒ 恒 `UNSUPPORTED`，走完整 `BedrockGunModel#render` 保真路径；`RenderConfig.STATIC_ITEM_GUN_VBO` 仍被读取保留开关语义 |
| `ItemPreviewRenderContext.java` | 上游 20 行 | ≡（`alpha`/`isPreview`；r6 的 `setAlpha` try/finally 不在本模块但调用点一致） |
| `DisplayItemRenderContext.java` | 上游 ~25 行 | ≡（展示柜/挂板渲染期标记，`isActive()` 语义同） |
| `ScreenItemVboContext.java` | 上游 31 行 | **有据降级**：`ScreenEvent.Render.Pre/Post`→`GuiScreenEvent.DrawScreenEvent.Pre/Post`；额外「推送 LOD bypass」到 `util/RenderDistance`（util 不能反查 client 类），语义等价（推送窗口 = 整个 GUI 绘制期） |
| `AmmoItemRenderer.java` | 上游 126 行 | `renderByItem` 逐行 ≡（GUI slot texture / 定位组 / display scale / preview alpha）；`renderItem` 为本轮 F-2 改 light。**`display` 变换缺口见 §6-K1** |
| `AttachmentItemRenderer.java` | 上游 `AttachmentItemRenderer` | 同上；LOD/变体（replace/overlay）分支一致。**`display` 变换缺口见 §6-K1** |
| `GunSmithTableItemRenderer.java`、`BlockModelGuiItemRenderer.java` | — | **不在本模块**（r7-block 写作用域），本轮只读不改 |

---

## 四、有据降级清单（平台差异，不当作 bug）

**A. 手部投影 FOV 的建立位置**
- 上游：`ItemRenderer#render` 时由 `GameRenderer#renderItemInHand` 先 `resetProjectionMatrix(getProjectionMatrix(getFov(camera, f, false)))`（1.20 `GameRenderer.java:997`），`ComputeFov` 的「非 configured」分支由 `CameraSetupEvent#applyGunModelFovModifying` 改。
- 1.7.10：手部投影硬编码在 `EntityRenderer#renderHand:755-770` 的 `gluPerspective(getFOVModifier(p,false))`，而 `getFOVModifier(p,false)` 恒 70°（`:482`），**没有任何事件能改**；且 `renderHand` 被 `RenderHandEvent` 取消 ⇒ 投影根本不建立。
- 降级：SBM `FirstPersonRenderHandler#pushHandProjection()` 直接读 `CameraSetupEvent.ITEM_MODEL_FOV` 建同一份投影（r4 A-1 定案）。
- 结论：**数值等价**；`GameRendererMixin#tacz$overrideHandFov` 对枪械不可达但无害（非枪械路径仍需要它把 `ITEM_MODEL_FOV`（≈70）覆盖回去，两者等价）。

**B. 相机动画 / 相机后坐力的通道**
- 上游：`ViewportEvent.ComputeCameraAngles` 的 `setYaw/setPitch/setRoll`（只改相机，不改实体朝向）；recoil 用 `player.setXRot/setYRot`（永久改朝向）。
- 1.7.10：`EntityViewRenderEvent` 只有 Fog 三件套，**没有相机角度事件**；`EntityRenderer` 的相机朝向 = `renderViewEntity` 的插值 `rotationPitch/Yaw`（`EntityRenderer.java:641-642`）。
- 降级（r5 P0 定案，不许回退）：`RenderTickEvent.START` 把动画角度**同时**加到 `rotation*` 与 `prevRotation*`，END 幂等扣回（只加 `rotation*` 会被 `partialTicks` 每 tick 抹一次 = 「换弹剧烈抖动」）。
- **残留差异（未修，量级小）**：第一人称枪的 sway 输入是 `player.rotationPitch/Yaw`（`GunItemRendererWrapper#renderFirstPerson`），因此比上游多吃一份「本帧相机动画偏移」（上游 `getViewXRot` 读实体朝向，不含 `ComputeCameraAngles` 的偏移）。量级 = 相机动画幅度（枪包 `camera` 通道，典型 0~8°）⇒ 只表现在换弹/拉栓时的枪体微动。
  修法代价：把 yaw/pitch 偏移改成 `orientCamera` RETURN 的 `glRotatef`（像 roll 通道那样），但要同时改 `MuzzleParticleManager`/第三人称枪口的世界变换取值来源（现在读 `renderViewEntity` 朝向），**风险 > 收益**，建议保留现状并在实机确认观感（§7 V-3）。

**C. 世界 FOV zoom 的额外一阶平滑**
- 上游：`ComputeFov` **每帧**调用 ⇒ `WORLD_FOV_DYNAMICS`（内部 20 Hz 异步弹簧，`SecondOrderDynamics#update` 的 `t=0.05`，与调用频率无关）+ `event.setFOV` 立即生效。
- 1.7.10：`FOVUpdateEvent` 只在 `EntityRenderer#updateFovModifierHand()`（**每 tick**，由 `EntityPlayerSP#getFOVMultiplier` 触发）派发，算出的倍率再被 vanilla `fovModifierHand += (target - hand)*0.5` 插值一次 ⇒ 开镜 zoom 比上游多 ~1–2 tick 滞后。
- 判定：**有据降级**（1.7.10 没有帧级 FOV 事件；要消除需把整个 zoom 计算挪进 `getFOVModifier` 的 mixin 并绕过 `fovModifierHand`，会与死亡/水下/加速 FOV 修正打架）。视觉上是「开镜稍软」，不是错值。

**D. 第一人称手臂的外观来源**
- 上游：`PlayerRenderer#renderHand` 渲染 `rightArm`+`rightSleeve`（`PlayerRenderer.java:195-209`，渲染后 `xRot=0`；`attackTime=0/crouching=false/swimAmount=0`），`RenderType.entitySolid`（**无 GL_LIGHTING**，只有 lightmap）。
- 1.7.10：`ModelBiped` 没有独立袖子骨骼（`ModelBiped` 只有 `bipedHeadwear`，皮肤袖子是同一张贴图的区域，`RenderPlayer.java:455-462` 只画 `bipedRightArm`）；`RenderPlayer#renderFirstPersonArm` 在 `ItemRenderer` 的 `enableStandardItemLighting()` 区间内被调用（`ItemRenderer.java:272/:553`）⇒ 手臂有 `GL_LIGHTING` 方向光。
- 降级：只画手臂（无袖子上层）+ 保留 vanilla 1.7.10 的手臂光照（与 1.7.10 原版观感一致；改成 1.20 的纯 lightmap 反而与原版不一致）。
- 关键等价点：`xRot=0` 由 `PlayerModelMixin#setRotationAnglesTail`（第一人称手持 + 持枪时把两条手臂 `rotateAngleX/Y/Z` 归零）承担，`isSneak/isRiding/heldItemRight` 的附加角因此不会漏进来；`onGround=0` 的 swing 分支在 progress=0 时对 `rotationPoint*`/角度均为恒等（已逐行核对 `ModelBiped.java:156-176`）。

**E. `ItemInHandRendererMixin` 的两条注入在持枪时不可达**
- `renderItemInFirstPerson`/`updateEquippedItem` 都只在 `EntityRenderer#renderHand` 内被调用（`:797`/`:752`），而持枪时 `RenderHandEvent` 被 SBM cancel ⇒ 整段 `renderHand` 跳过。
- 补偿链：`BeforeRenderHandEvent` 由 SBM `compensateBeforeRenderHandEvent()` 派发（`new PoseStack()` → post → `applyToModelView()`），语义与上游「事件里给的 PoseStack 叠加到相机矩阵」等价；`updateEquippedItem` 的上游注入体本身就是空方法（注释掉），不可达无影响。
- 非枪械路径两条注入正常执行 ⇒ 与上游行为一致。

**F. VBO 快速路径整体关闭**：见 §3.9 `ExternalGunItemVboRenderer`。

---

## 五、截图线索 `docs/review/r7/screenshots/a_thirdperson_gun_rangefinder.png` 的归属

**判定：第三人称（不是第一人称，也不是 rangefinder 问题）**，证据：
1. **相机模式**：画面里能看到本地玩家的**正面**（脸/眼睛/红鼻），1.7.10 只有 `thirdPersonView == 2`（F5 前视角）才会在相机与玩家之间看到自己的正面；世界画面里枪在**左下角世界空间**（约在玩家胯部高度、离身体约半格），不是第一人称的「贴脸/屏幕中下方」形态。
2. **第一人称不可能泄漏到第三人称**（两道闸门）：
   - `FirstPersonRenderHandler#onRenderHand:428-430`：`mc.gameSettings.thirdPersonView != 0` → `return`（**不 cancel**，原版路径照常）；
   - `GunItemRendererWrapper#renderByItem:379-385`：`FIRST_PERSON_LEFT_HAND`/`FIRST_PERSON_RIGHT_HAND`/`THIRD_PERSON_LEFT_HAND` 直接 `return`；
   - `EntityRenderer#renderHand:794`：第三人称下 `renderItemInFirstPerson` 本来就不执行。
3. **HUD 不是 rangefinder**：r7-crosshair 已逐像素对上 `textures/hud/heat_base.png` 的刻度框 + `DecimalFormat("0.0%")` 的 `0.0%`（HeatBarOverlay，只有 minigun 且开过火才显示；0% 仍显示是上游同构行为）。rangefinder 文本在该场景被三闸门挡住（`TextShowRender` 的 `!firstPerson()`、EQUIPPED→THIRD_PERSON context、`thirdPersonView != 0` 早退），且未开镜只会出 `---`。
4. **图里真正的异常** = 第三人称 EQUIPPED 枪的位置/朝向异常 ⇒ 本轮 **F-3（P0，0.866 格偏移）** 的实机表现；修完应回到手上。若实机仍有一把「贴脸的大枪」+ 身体同屏，才是第一人称路径泄漏，可按下表 V-1 的判读法抓。

> 结论：本截图交给 r7-tp（第三人称）+ r7-crosshair（HUD）对口；第一人称侧本轮给出的贡献是「F-3 已修 + 静态排除第一人称泄漏」。

---

## 六、跨模块发现（不在本模块写作用域，只报不改）

**K1（重要，弹药盒/配件世界内渲染不等价）**
`AmmoItemRenderer`/`AttachmentItemRenderer`（属我 scope，但成因是「物品模型 display 变换」这个独立缺口）：
- 它们的物品模型**有 display 段**：`assets/tacz/models/item/ammo.json` 有 `firstperson_righthand/lefthand` 的 `scale 0.5`；
  `attachment.json` 有 `thirdperson_righthand`（`translation [0,1,1]`、`scale 0.55`）/`firstperson_righthand`（`rotation [0,-90,25]`、`translation [1.13,3.2,1.13]`、`scale 0.68`）/`ground`（`translation [0,2,0]`、`scale 0.5`）/`head`/`fixed`。
- 1.20 这些 display 由原版 `ItemRenderer#render` 在进 BEWLR 前施加（`bakedmodel.getTransforms().getTransform(ctx).apply(...)`，`ItemRenderer.java:126-129`）。
- **1.7.10 侧没有任何代码消费这份 display 数据**（全仓无 `models/item/*.json` 的 display 解析；`AnimateGeoItemRenderer`/`AmmoItemRenderer`/`AttachmentItemRenderer` 里的 `translate(0.5,2,0.5)+scale+定位组` 是 TACZ 自己的 `transform` 数据，不是 item model display）。
- ⇒ 弹药盒/配件在第三人称手持 / 掉落物 / 展示框 / 第一人称手里的**位置与大小**与上游不等价（枪械没有该问题：`modern_kinetic_gun.json` 无 display 段，display 恒等）。
- 建议：新增「item model display 读取器」（从 `assets/tacz/models/item/*.json` 读 `display.{thirdperson_righthand,ground,fixed,firstperson_righthand,…}` 的 rotation/translation/scale，`translation × 1/16`），在三个 `renderItem` 的 context 里施加；再做一次帧补偿。工作量 = 一个独立子任务，本轮不做。

**K2** `GunSmithTableItemRenderer` / `BlockModelGuiItemRenderer`（`renderer/item/` 内，但属 r7-block 写作用域）也写死 `0xF000F0`，同样不吃世界 light；请 r7-block 决定是否套 `RenderHelperCompat#packedLightFor`。

**K3** `FirstPersonRenderHandler`（r7-sbm scope）里 `beginDebugFrame()` 仍被调用（r7-sbm 已改成 `if (DEBUG_RENDER)` 包裹）。本模块保留了该空日志钩子；若 r7-sbm 最终删掉调用，可把 `RenderHelperCompat#beginDebugFrame/debugFrameId/DEBUG_RENDER` 一并删除。

**K4** r7-crosshair 的「第一人称手模出现在第三人称」怀疑：本报告 §5 给出两道静态闸门的证据，认为不是第一人称泄漏；若实机复现，排查顺序建议 `FirstPersonRenderHandler:428-430` → `GunItemRendererWrapper#renderByItem` 的 FIRST_PERSON 早退 → 是否有第三方（YSM/Oculus）绕过。

**K5** r7-tp 已把 `HumanoidOffhandRender` 改为直接 `renderByItem(FIXED, …)`（不再走 `renderItem(EQUIPPED)`），因此本模块删除了 `beginExternalEquippedFrame/endExternalEquippedFrame` 这对作用域标记（已无调用者）。若后续有别的路径需要「外部自备坐标系」，需要重新加回（1 个静态深度 + 2 个方法 + 1 个判断）。

---

## 七、未修清单（理由 + 证据）

| # | 项 | 理由 | 证据/位置 |
|---|---|---|---|
| U-1 | 弹药盒/配件的 item model `display` 变换未消费 | 独立缺口（需要新写 JSON display 读取器），不是一行修；且涉及 r7-model/r7-tp 交界 | §6-K1 |
| U-2 | 相机动画偏移被第一人称 sway 额外消费 | 见 §4-B 残留差异；修法牵动 `MuzzleParticleManager`/世界枪口变换取值来源，风险 > 收益 | `CameraSetupEvent:144-216`、`GunItemRendererWrapper:226` |
| U-3 | 世界 FOV zoom 比上游多一阶平滑（~1–2 tick） | 见 §4-C；1.7.10 无帧级 FOV 事件 | `CameraSetupEvent:248-279`、`EntityRenderer.java:445-468,486-487` |
| U-4 | `FirstPersonRenderGunEvent#bulletFromPlayer` 无调用者 | **上游自身死代码**（上游 `:123-128` 同样无调用点）⇒ 按「保真优先」保留，不顺手修 | 上游 `FirstPersonRenderGunEvent.java:123-128` |
| U-5 | `applyJumpingSway` 在 `partialTicks == 0` 时除零 | 上游同样（`FirstPersonRenderGunEvent.java:332-333`）⇒ 保真 | 同上 |
| U-6 | `views[zoomNumber % views.length]` 在 `views` 为空时 NPE | 上游同样（`:192`）⇒ 保真；数据侧保证非空 | 上游 `:189-192` ↔ 本工程 `:243-246` |
| U-7 | `RenderHelperCompat#localPlayer()` 当前无调用者 | 保留为上游 idiom 小工具（4 行，客户端单例读取）；如 Lead 要求可删 | `RenderHelperCompat:257-260` |
| U-8 | `ItemInHandRendererMixin#beforeHandRender/cancelEquippedProgress` 对枪械不可达 | 上游注入体本身就是空方法；补偿链由 SBM handler 承担（§4-E）⇒ 保留以保签名/注册形态 | `ItemInHandRendererMixin:113-134` |

---

## 八、实机验证清单（必须构建后跑）

| # | 验证项 | 判读方法 |
|---|---|---|
| **V-1** | **F-3 第三人称手持枪归位**（0.866 格偏移） | 第三人称（F5 一次，背后视角）持任意枪：**枪应握在右手**（枪身贴着手掌，不是浮在胯侧/膝前）。侧面看（F5 两次前视角）枪身轴线应与手臂自然延伸一致；枪口不应朝镜头。若还偏，先单点关 `AnimateGeoItemRenderer.ENABLE_EQUIPPED_PAPER_FRAME_FIX=false` 对比偏移方向 |
| **V-2** | **F-1 反向延滞消失** | 原地站立，快速左右甩视角（一次 ~60°）再回正：第一人称枪应**只跟着准星方向轻微滞后（模型自身 sway，≤1.25°）**；不应有「朝反方向多甩一下」的手感。对比：把 `AnimateGeoItemRenderer.applyVanillaFirstPersonHandLag` 的调用注释掉即为修前行为 |
| **V-3** | **F-2 世界光照** | 夜晚/洞穴里第三人称看自己或别的玩家持枪：枪模应随环境变暗（不再恒亮）；丢一把枪到地上（Q）：掉落物亮度应跟环境。GUI/创造栏图标仍应满亮（lightmap 在 GUI 无效，视觉不变） |
| **V-4** | **F-4 掉落物** | 丢一把枪：应**自转 + 上下浮沉 + 大小与上游观感一致**（原来是「小一半、不转不浮、偏到角上」）。展示框/物品框里放枪：应居中（只补 -0.5，不自转） |
| **V-5** | **开镜（ADS）全链路** | 装镜/机瞄：按住右键，枪模应平滑移到镜位（`CameraSetupEvent.ITEM_MODEL_FOV` 从 `model_fov`→`zoom_model_fov`），世界 zoom 同步；**枪不应被世界 FOV 放大**（手部投影已独立）。左右手持枪（1.7.10 无双持，只测主手）+ 切枪过渡（切枪动画期间枪不应瞬移/闪） |
| **V-6** | **手臂** | 第一人称持枪站定：皮肤手臂应从骨骼锚点沿握把方向伸出（不是 T-pose 横插、不满屏、不缺失）；`isSneak`/骑马/受伤时手臂不应带第三人称持枪姿态（`rotateAngleX ≈ 0`）。一键对照：`RenderHelperCompat.RENDER_FIRST_PERSON_VANILLA_ARM=false` = 无手 |
| **V-7** | **相机动画/后坐力** | 换弹/拉栓时屏幕应跟随枪包 `camera` 通道微动（不抖、不甩）；连发时后坐力应把准星往上推且**不累积漂移**（`revertCameraAnimationOffset` 幂等）；受伤时视角摇晃与世界同向同幅 |
| **V-8** | **`[TACZ-DBG]` 已清零** | 构建后 `grep "\[TACZ-DBG\]" logs/latest.log`：持枪进出 ADS/开火/换弹/切枪/丢枪/第三人称一圈后应为 **0 行**（历史 r4/r5 的 ADS/scope/equipped/arm/cam 诊断行都不再出现） |
| **V-9** | （r7-sbm 联动）`DEBUG_RENDER=true` 时无异常 | 把 `RenderHelperCompat.DEBUG_RENDER=true` 后跑一轮：不应有 NPE/日志洪泛（本模块只保留 `beginDebugFrame` 空推进，r7-sbm 侧诊断自行验证） |
| **V-10** | **C-1 第三人称枪口粒子贴枪口** | 自己 F5 第三人称开火：火光应在**枪口**且随枪口后坐/转身/走动一起动（不是固定在世界某点/脚边/漂移）；看其他玩家开火：火光应在其枪口上；F5 **前视角**（相机在角色前方）与快速转视角时仍贴合。详见 §11 |
| **V-11** | **第一人称手臂正常形态**（HUD 泄漏修完后判） | 站定持枪：右手臂从右下伸到握把/扳机、支撑手（左手）从左下伸到护木，均为正常前臂粗细（≈屏宽 1/6~1/4），无横贯屏幕的巨平面/巨幅放大皮肤块、无缺面；ADS/收枪过渡中手臂随枪正常。若仍异常先按 12.3 第 3 条区分「HUD 泄漏」vs「手臂锚点贴脸」。详见 §12.3 |

---

## 九、本轮改动文件清单（全部 `lint_files(error)=0`）

1. `src/main/java/com/tacz/guns/client/model/RenderHelperCompat.java`
   - 新增 `packedLightFor(...)`；删 `tacz$logArmPose`/`tacz$equippedProgress`（反射）/`currentModelViewTranslation`/`countInFrame`/`debugLog`/`debugFrameId` 日志体；
   - 保留 `DEBUG_RENDER` + `beginDebugFrame()`（r7-sbm 仍调用）+ `debugFrameId()`；`renderFirstPersonArm` 的兜底判断改为内联读 `poseStack` 平移（`MIN_HAND_ANCHOR_DISTANCE` 默认 0，行为不变）。
2. `src/main/java/com/tacz/guns/client/event/FirstPersonRenderGunEvent.java` — 删全部 DBG（ADS/scope/矩阵 3 段），算法零改动；清 4 个只服务日志的 import/局部量。
3. `src/main/java/com/tacz/guns/client/event/CameraSetupEvent.java` — 删 `tacz$logCameraAnimation` 与字段；其余不动。
4. `src/main/java/com/tacz/guns/client/renderer/item/AnimateGeoItemRenderer.java`
   - **F-3**：`f20` 补 `T(-0.5,-0.5,-0.5)`；**F-4**：`shouldUseRenderHelper` + `renderItem(ENTITY)`；**F-1**：新增 `applyVanillaFirstPersonHandLag` 并在 `renderFirstPerson` 调用；**F-2**：light 走 `packedLightFor`；xRot 改为不插值；
   - 删 EQUIPPED 诊断整段；删已无调用者的 `beginExternalEquippedFrame/endExternalEquippedFrame`。
5. `src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java` — 删 `tacz$dbgPoint`；F-1（补延滞对）；xRot 不插值；其余不动。
6. `src/main/java/com/tacz/guns/client/renderer/item/AmmoItemRenderer.java`、`AttachmentItemRenderer.java` — F-2（light 取实体亮度）；未修 K1。
7. `src/main/java/com/tacz/guns/mixin/client/GameRendererMixin.java` — ① 改正 `tacz$applyCameraRoll` 的过期 javadoc（roll 已有写入端）；② **C-1**：新增 `tacz$captureViewMatrix`（`setupCameraTransform` RETURN），每帧记录纯视图矩阵。
8. `src/main/java/com/tacz/guns/client/model/RenderHelperCompat.java`（C-1 追加）— 新增 `captureViewMatrix/viewMatrixInverse/currentModelViewMatrix`（GL 列主序→JOML 行主序）。
9. `src/main/java/com/tacz/guns/client/renderer/item/GunItemRendererWrapper.java`（C-1 追加）— `captureThirdPersonMuzzle(Matrix4f)` 重写为 `T(RenderManager.renderPos)·VIEW⁻¹·GL_MODELVIEW·pose`；删掉只服务旧近似算法的 `cameraYaw/cameraPitch` 与 `Vec3/CameraStateCache` 依赖。
10. `docs/review/r7/fp.md` — 本报告（含 §11 C-1）。

**未运行任何 gradle 任务**（构建由 Lead 统一收尾）。

---

## 十、覆盖清单（诚实）

- **逐行/逐段读过**：`FirstPersonRenderGunEvent`（542 行，与上游 478 行逐段对照）、`CameraSetupEvent`（508↔275）、
  `FirstPersonRenderEvent`（124↔71）、`GunItemRendererWrapper`（547↔423）、`AnimateGeoItemRenderer`（617↔402）、
  `RenderHelperCompat`（361，全读）、`GameRendererMixin`（291↔113）、`ItemInHandRendererMixin`（164↔93）、
  `GunItemTransforms` / `SbmGunTransforms` / `StaticGunRenderResult` / `ExternalGunItemVboRenderer` /
  `ItemPreviewRenderContext` / `DisplayItemRenderContext` / `ScreenItemVboContext` / `AmmoItemRenderer` / `AttachmentItemRenderer`（全读）。
- **1.20 准绳**：`ItemInHandRenderer.java`（`renderHandsWithItems:323-345`、`renderArmWithItem`）、`GameRenderer.java:995-1032, 1270-1320`、
  `ItemRenderer.java`（`entity/` 与 `ItemInHandRenderer` 两个）、`ItemInHandLayer.java`、`ItemEntityRenderer.java`、
  `ItemFrameRenderer.java`、`PlayerRenderer.java:187-212`、`LocalPlayer.java:190-200`、`ItemTransforms`/`Transform`（`ItemRenderer#render` 的 display 施加点）。
- **1.20 Forge**：`patches/.../ItemInHandRenderer.java.patch`、`patches/.../entity/ItemRenderer.java.patch`、`ForgeHooksClient.java:296-308`。
- **1.7.10**：`EntityRenderer.java`（`getFOVModifier:473-505`、`hurtCameraEffect:507-528`、`setupViewBobbing:533-547`、`orientCamera:552-745`、`renderHand:751-814`、`renderWorld:1320-1449`、`enableLightmap/disableLightmap:819-848`）、
  `ItemRenderer.java`（`renderItemInFirstPerson:264-554`、`updateEquippedItem`）、`RenderPlayer.java:313-390,455-462`、
  `RenderItem.java:59-130,780-812`、`ForgeHooksClient.java:84-150,153-208,214-236`、`RenderItemFrame.java:195-270`、
  `RenderManager.java:262-278`、`RenderGlobal.java:465-512,999-1012`、`ModelBiped.java:110-200`、`Tessellator.java:95-180,400-440`、
  `RenderHelper.java`（enable/disableStandardItemLighting）、`OpenGlHelper.java:702-721`、`IItemRenderer.java`（枚举）、`FOVUpdateEvent`、`ForgeHooksClient#getOffsetFOV`、`EntityPlayerSP.java:70-101,312-347`。
- **没做**：① 未运行实机（所有观感项进 §8 验证清单）；② 未逐行读 `client/model/functional/**`（r7-model 已核，`model.md`）、
  `BedrockGunModel`（r7-model/r7-scope）、`client/particle/**`（r7-gun）、`client/renderer/scope/**`（r7-scope）——只核了它们的调用接口（light/overlay/buffer/transform 参数）；
  ③ `GunSmithTableItemRenderer`/`BlockModelGuiItemRenderer` 属 r7-block 未读。

---

## 十一、C-1 收尾（本轮新增修复）：第三人称枪口粒子的**世界坐标**缺 GL_MODELVIEW

> 来源：r7-tp 发现、Lead 指派本模块落地（`client/renderer/item/GunItemRendererWrapper.java` 在 r7-fp 写作用域内）。
> 上游准绳：`tmp/TACZ-1.20.1/.../client/renderer/item/GunItemRendererWrapper.java:386-414`（`captureThirdPersonMuzzle`）
> + `client/particle/ThirdPersonMuzzleParticleManager.java`（消费端）。

### 现象
第三人称（F5）开火 / 看其他玩家开火时，枪口火光、烟雾**不贴着枪口**：世界锚点固定/漂移，与枪口脱钩。
（第一人称枪口效果不受影响：它走 `MuzzleParticleManager` 的相机空间通道。）

### 根因
`GunItemRendererWrapper#captureThirdPersonMuzzle(PoseStack, BedrockGunModel)` 把 `poseStack.last().pose()` 当成
「相机空间矩阵」交给 `captureThirdPersonMuzzle(Matrix4f)`，后者按上游公式
`muzzleWorld = T(camPos)·R_cam⁻¹·pose` 还原世界矩阵。

上游公式成立的前提是：BEWLR 拿到的 PoseStack **已经含「相机旋转 × 相机相对实体帧」** ——
1.20 `EntityRenderDispatcher#render`（`:148-150`）push `T(entity - cam)` + `Ry(180-yawEntity)`，
`LivingEntityRenderer` 叠 `S(-1,-1,1)` + `T(0,-1.501,0)`，
`ItemInHandLayer#renderArmWithItem`（`:54-61`）再叠手臂帧（+ `ItemRenderer#render:128-129` 的 `T(-0.5,-0.5,-0.5)`）。

**1.7.10**：`AnimateGeoItemRenderer#renderItem` 用的是**新建空 PoseStack**（只含物品帧：纸片帧逆补偿 + `GunItemTransforms`
+ 骨骼链），实体帧/手臂帧/纸片帧全在 `GL_MODELVIEW` 里 ⇒ `pose` 缺
`T(entity-camPos)·R_entity·S(-1,-1,1)·T(0,-1.501,0)·P_arm`，世界矩阵自然是错的。

### 修法（比「把 GL_MODELVIEW 乘进去」更彻底：连相机后拉/晃动一起精确抵消）
1. `RenderHelperCompat` 新增（`:57-135`）：
   - `captureViewMatrix()`：记录本帧**纯视图矩阵** `VIEW`（世界→相机，含 hurt/view-bob/roll/传送门扭曲/第三人称后拉）；
   - `viewMatrixInverse()`、`currentModelViewMatrix()`（GL 列主序 → JOML 行主序转置，直接调用 `GL11.glGetFloat`）。
2. `GameRendererMixin` 新增 `tacz$captureViewMatrix`（`:283-310` 附近，`@Inject(method = "setupCameraTransform", at = @At("RETURN"))`）：
   `setupCameraTransform` 以 `glLoadIdentity()` 开头（1.7.10 `EntityRenderer.java:683`），之后只叠加相机效果
   （`hurtCameraEffect`:690 / `setupViewBobbing`:694 / 传送门扭曲 / `orientCamera`:715，含第三人称后拉 `:629` 与本类 ⑦ 的 roll）
   ⇒ RETURN 处 `GL_MODELVIEW` **只含相机**，此时抓 `VIEW` 最准。
3. `GunItemRendererWrapper#captureThirdPersonMuzzle(Matrix4f)`（`:496-520` 重写）：
   `MODEL = VIEW⁻¹·GL_MODELVIEW`（= 相机相对实体帧，含 1.7.10 的手臂帧与纸片帧）；
   `muzzleWorld = T(RenderManager.renderPos)·MODEL·pose`。
   `RenderManager.renderPos` 是 `renderViewEntity` 的插值位置（1.7.10 `RenderGlobal.java:459-482` 每帧写入），
   正是 `MODEL` 里 `T(entity - renderPos)` 的基准（`RenderManager.java:262-278`）⇒ 与上游 `+ camPos` 同一合成方式，得到**绝对世界坐标**。

### 为什么**不**采用 r7-tp 建议的 `viewRotationInv·GL_MODELVIEW·pose + 眼位`
1. 1.7.10 第三人称的 `VIEW` 里含 `glTranslatef(0, 0, -d7)`（`EntityRenderer.java:629`，d7 = 第三人称距离，默认 4 格）
   以及 eye-height 平移（`:645`）、view-bob（`:694`）、hurt（`:690`）、roll、传送门扭曲（`:697-713`）；
   `viewRotationInv`（`Ry(-(yaw+180))·Rx(-pitch)·Rz(-roll)`）只抵消**旋转**部分 ⇒ 会残留与 d7 同量级（最多约 4 格）的错位。
2. 该做法的 `+camPos` 用的是**眼位**，不是第三人称后拉后的真实相机位置，再叠一次 `d7` 误差。
3. `VIEW⁻¹·GL_MODELVIEW` 把这些因子（后拉、eye-height、bob、hurt、roll、anaglyph 平移）**全部精确消掉**，
   且完全不需要相机位置/朝向 —— 只依赖「此时 GL_MODELVIEW = VIEW·MODEL」这一条不变式。

### 等价性展开（与上游逐项相同）
- 上游：`T(camPos)·R_cam⁻¹·pose`，其中 `pose = R_cam·T(entity-camPos)·R_e·S(-1,-1,1)·T(0,-1.501,0)·Arm·F20·GIT·chain`
  ⇒ 展开 = `T(entity)·R_e·S(-1,-1,1)·T(0,-1.501,0)·Arm·F20·GIT·chain`。
- 本修：`T(renderPos)·(VIEW⁻¹·GL_MODELVIEW)·pose`
  = `T(renderPos)·MODEL_entity_arm·F_paper·inv(F_paper)·F20·GIT·chain`
  = `T(renderPos)·T(entity-renderPos)·R_e·S(-1,-1,1)·T(0,-1.501,0)·P_arm·F20·GIT·chain`
  = `T(entity)·R_e·S(-1,-1,1)·T(0,-1.501,0)·P_arm·F20·GIT·chain` ✅ 与上游同式。
  （`TACZ_EQUIPPED_PAPER_FRAME_INV = inv(F_paper)·F20` 与 GL 里的 `F_paper` 正好相消，见 §2 F-3。）

### 说明
- VBO 快速路径的同类调用点 `ExternalGunEffects#renderThirdPerson`（r7-sbm scope）也在同一个 GL 实体帧里
  调用本方法（先 `DisplayGunGeometry.applyTransform` 再取枪口骨骼），语义一致；该路径 1.7.10 恒 `UNSUPPORTED`（§3.9），
  属死代码但不再有第二套错误语义。
- 代价：每帧 1 次 `glGetFloat(GL_MODELVIEW_MATRIX)`（`setupCameraTransform` RETURN）+ 每次第三人称枪口捕获 1 次；
  与本工程既有的 `util/RenderDistance`（每次物品渲染读 GL_MODELVIEW）同一量级。
- 未改消费端（`ThirdPersonMuzzleParticleManager` / SBM 世界粒子，属 r7-gun/r7-sbm）：它们只消费 `muzzleWorld` 的平移列
  （`new Vec3(m30, m31, m32)`）与 `updateEmitterAnchor(..., muzzleWorld)` 的方向列，本修同时修正了平移与旋转。

### 实机验证（补 §8）
| # | 验证项 | 判读方法 |
|---|---|---|
| **V-10** | **C-1 第三人称枪口粒子贴枪口** | ① 自己 F5 第三人称开火：火光应出现在**枪口**（不是世界某点/脚边/漂移），且随枪口后坐、转身、走动一起动；② 其他玩家（第二客户端/假人）开火：其枪口火光应在**他的**枪口上；③ 开火瞬间快速转视角 + 走动：粒子锚点不应滞后/抖到别处；④ 第三人称**前视角**（F5 两次，相机在角色前方）：仍需贴合枪口（view 里含 180° 翻转与后拉，本修会精确抵消）。 |

---

## 十二、实机复核①：第一人称矩阵栈平衡审计 + 手臂复核判据

> 触发：实机复核截图 `docs/review/r7/screenshots/b_hand_scope_artifacts.png`（1215×933）里左下出现
> 「米/黑/青色带 + `HK-MP5A5` 文字的巨平面」。r7-scope 判定不是手模，而是 **HUD 内容被画进 3D 世界空间**
> （`client/gui/overlay/**` 方向，已转 r7-crosshair 主查）。本节回答两件事：
> ① 本 scope 的 r7 改动**有没有**留下 GL 矩阵栈不平衡（会不会成为 HUD 泄漏的源头）；
> ② 第一人称手臂本身是否被 r7 改动带坏，以及 HUD 泄漏修完后手臂的实机判据。

### 12.1 结论（先说答案）

- **本 scope 的 r7 改动不含任何 GL 矩阵栈写入**：唯一的 `pushToGL()/popFromGL()` 在
  `RenderHelperCompat#renderFirstPersonArm:225/231` 且被 `try/finally` 包住；其余 r7 新增/改动的矩阵相关代码
  （`tacz$captureViewMatrix`、`viewMatrixInverse`、`currentModelViewMatrix`）**只读** `GL_MODELVIEW`（`glGetFloat`）。
- **被删掉的 `beginExternalEquippedFrame()/endExternalEquippedFrame()`** 原实现只是静态 int
  `tacz$externalEquippedFrameDepth` 的 `++/--`（git HEAD 可查），**零 GL / 零姿态栈操作** ⇒ 删除不可能造成栈不平衡，
  且全仓已无引用。
- **全仓 8 处 `pushToGL()` 全部与 `popFromGL()` 成对，且 8 句 `popFromGL()` 都在 `finally` 里**
  （含第一人称路径内的 4 处：`RenderHelperCompat:225/231`、`BeamRenderer:159/192`、
  `RangefinderImpactPredictionRender:179/209`、`TextShowRender:114/131`）⇒ 不存在「push 无 pop」。
- **HUD 阶段的矩阵一定是从零开始**的：HUD 由 `RenderGameOverlayEvent.Post(ALL/CROSSHAIRS)` 派发
  （`ClientSetupEvent:521-532`），而该阶段入口 `EntityRenderer#setupOverlayRendering()`
  （`build/rfg/.../EntityRenderer.java:1739-1752`，调用点 `:1126`/`:1176`）先 `glMatrixMode(PROJECTION); glLoadIdentity();
  glOrtho(...)`，再 `glMatrixMode(MODELVIEW); glLoadIdentity(); glTranslatef(0,0,-2000)`。
  ⇒ 世界/手部 pass 遗留的任何 MODELVIEW 残留在进入 HUD 前都会被 `glLoadIdentity` 清掉，
  **本 scope（乃至整个世界渲染侧）不可能把相机矩阵带进 HUD**；HUD 被画进 3D 只能是 **HUD 阶段内部**
  （`client/gui/overlay/**` / `GuiGraphics`）自己建立的矩阵/投影，与 §12.1 的审计结论一致，归 r7-crosshair。

### 12.2 逐项审计（r7 在我 scope 内的每一处矩阵相关改动）

| # | r7 改动 | 是否碰 GL 矩阵栈 | 结论 |
|---|---|---|---|
| 1 | `AnimateGeoItemRenderer#applyVanillaFirstPersonHandLag`（新增，`AnimateGeoItemRenderer:417-420` / `GunItemRendererWrapper:227`） | **否**：只是 `PoseStack.mulPose` ×2（CPU 侧 JOML） | 与上游 `ItemInHandRenderer:332-333` 同形；与紧随其后的上游 `-0.1Δ` 两项合成 `Rx(a)Ry(b)Rx(-a)Ry(-b)`（a=0.1ΔxRot、b=0.1ΔyRot）——**这正是上游自身的合成式**。数值残差（=两轴不可交换的二阶项）：Δ=20°/tick → 0.07°，Δ=45°/tick → 0.35°，Δ=90°/tick → 1.4°；人眼不可见，且上游同式 ⇒ **不是移植偏差** |
| 2 | 同 1 的「套到手臂骨链」疑问 | — | 手骨链在 `gunModel.render` 内部、位于该 poseStack **之后**，因此它和上游一样吃到同一个净旋转（≈恒等）⇒ **r7 不会让手臂异常**（手臂位置只由 `static_idle` 动画写的 `righthand/lefthand` 帧决定，见 §12.3） |
| 3 | `xRot/yRot` 改「不插值」（F-2） | 否 | 只改数值来源，等价上游 `LocalPlayer#getViewXRot/getViewYRot`（1.20 `LocalPlayer.java:190-196` 对本地玩家不插值） |
| 4 | `GameRendererMixin#tacz$captureViewMatrix`（新增） | **只读**：`GL11.glGetFloat(GL_MODELVIEW_MATRIX, buf)` | 无 `glPushMatrix/glPopMatrix/glMultMatrix/glMatrixMode`；注入点 `setupCameraTransform` RETURN 也不改栈 |
| 5 | `RenderHelperCompat#captureViewMatrix/viewMatrixInverse/currentModelViewMatrix`（新增） | **只读**（同上） | 无栈操作；`VIEW_MATRIX`/`GL_MATRIX_BUFFER` 是普通静态字段 |
| 6 | `AnimateGeoItemRenderer` F-1/F-2/`ENTITY` 分支、`tacz$buildPaperFrameInverse` | 否 | `renderItem` 每次**新建** `PoseStack`（无 push 需求）；纸片帧逆矩阵是纯 JOML 静态常量；`renderByItem` 内部 `pushPose/popPose` 成对 |
| 7 | 删除 `begin/endExternalEquippedFrame` | 否 | 原实现仅静态计数器（见 12.1） |
| 8 | `RenderHelperCompat#packedLightFor`（新增，F-2） | 否 | 纯 CPU 计算 |
| 9 | 删除全部 `[TACZ-DBG]` | 否 | 只删日志语句/局部量 |

**唯一「故意不平衡」的 GL 写（在我 scope、且是上游语义）**：
`ItemInHandRendererMixin#beforeHandRender`（`:112-119`）在 `ItemRenderer#renderItemInFirstPerson` HEAD
把 `BeforeRenderHandEvent` 的 PoseStack 用 `PoseStack#applyToModelView()` = `glMultMatrix` **不回滚**地乘进
MODELVIEW —— 这是**必须**的：1.20 里该事件拿到的就是手部 pass 的 PoseStack，相机动画的修改要「持续到本次手部渲染结束」。
残留被两处 `glLoadIdentity` 截断：(a) 手部 pass 是 `renderWorld` 的**最后一节**
（1.7.10 `EntityRenderer.java:1436-1440`：`FRenderLast` → `hand`），之后直接进 GUI 的
`setupOverlayRendering`；(b) 下一帧世界 `setupCameraTransform` 的 `glLoadIdentity`（`:683`）。
⇒ 不会污染 HUD 或下一帧世界；**保留现状**（不要为了「对称」加 `glPushMatrix/glPopMatrix`，那会破掉上游语义）。

### 12.3 手臂复核（r7 是否带坏 + 实机判据）

**复算（本轮用 `hk_mp5a5_geo.json` + `static_idle` 动画 + 1.7.10 完整变换链，脚本化重放）**：
- 手骨帧（`righthand_pos`/`lefthand_pos`）在相机空间：右手 ≈ `(-0.01, -0.63, +0.53)`、
  左手 ≈ `(+0.04, -0.68, -0.10)`（格；+z = 相机背后，-z = 前方）。
- 1.7.10 `ModelBiped` 手臂盒（4×12×4 px，pivot=肩）跟着该帧走：左手盒 z 跨度 ≈ `-1.31..-0.17`
  ⇒ **近端仅 0.17 格（17 cm）**，在 60° 手部 FOV 下会占掉屏幕左下很大一块（r4 日志 `origin.z=-0.091` 同源）。
  这是 **1.7.10 皮肤手臂 + 1.20 手骨帧/动画数据** 的既有适配问题，**r6 截图
  `docs/review/r6/screenshots/f_firstperson_missing_faces.png` 已有同现象**，**不是 r7 回归**（§12.2 第 1/2 项已证）。
- r7 对手臂唯一的影响 = 第 1 项那对延滞旋转，净效果与上游相同（≤0.35° @45°/tick）。

**等 r7-crosshair 修掉 HUD 泄漏后的实机判据（正常形态）**：
1. 第一人称持枪站定：能看见**右手皮肤手臂**从屏幕右下伸到握把/扳机，**左手（支撑手）**从屏幕左下伸到护木/前握把；
   两条手臂都应是**正常前臂粗细**（约屏幕宽度的 1/6~1/4），不应有一条横贯屏幕的巨平面。
2. 手臂上不应出现「整块皮肤被放大几十倍」（texel 失真/缺面）：若还有缺面，判据是「手背/袖口边缘是否完整」，并
   `grep "\[TACZ-DBG\]"` 确认无残留诊断（本 scope 已清零）。
3. 若 HUD 泄漏修好后**仍**见「巨型放大皮肤块 + 纹理条带」：那是 12.3 所述的**手臂锚点贴脸**问题（与 HUD 无关），
   处置优先级：先按 `RenderHelperCompat.RENDER_FIRST_PERSON_VANILLA_ARM=false` 单点确认（只显示枪模），
   再决定是否启用 `MIN_HAND_ANCHOR_DISTANCE` 兜底或立专项（默认值仍为 0 = 关闭，不藏症状）。
4. 顺带确认：`F1` 调试屏下手臂不应出现在第三人称；开镜（ADS）时手臂应随枪一起被手部 FOV/镜内剔除正常处理。

### 12.4 顺手更新的过期注释（Lead 指派）

`client/model/BedrockGunModel#renderUnaccelerated`（`renderUnaccelerated` 内、`scopePosPath` 分支之后）的
「1.7.10 默认没有模板缓冲 ⇒ stencilFunc/stencilOp 是空操作」长注释已过时，改为：
**运行期由 `client/renderer/scope/MainFramebufferStencil`（`ScopeLensRenderer#beginFrame()` 每帧 `ensure()`）
给主 FBO 补挂 `GL_DEPTH24_STENCIL8` renderbuffer（等价 1.20 Forge `RenderTarget#enableStencil`），
支持环境下与上游等效生效；只有补挂失败（GL<3.0 / core profile 光影 / OptiFine / 深度附件已被换成 texture /
光影占用）才降级为「模板测试恒通过」**；并注明 `-Dforge.forceDisplayStencil=true`（Forge 自建）会被
`MainFramebufferStencil` 识别为已就绪、不重复补挂。


---

## 十三、scope capture 前置：手部 pass 的 `GL_MODELVIEW` 恒为单位矩阵（r7 实机复核⑥）

> 触发：上游规格实现后实机日志发现「受伤/相机动画类枪械」帧上 `ScopeLensRenderer.capture` 读到的
> `ModelViewMat` 不是单位矩阵 ⇒ 目镜圆盘投影 bounds `behindCamera=true` ⇒ 镜片不登记 = **开镜黑屏**。
> 归属：第一人称渲染 pass（本模块 scope），只改 `simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java`。

### 13.1 实机证据（16:55 日志，2560×1377 窗口 / 854×480 内部）

| 帧 | 日志 | 判读 |
|---|---|---|
| 正常 | `scope capture matrices: proj.m00=0.974, proj.m11=1.732, expectedF=1.732 (ITEM_MODEL_FOV=60), modelViewIsIdentity=true` | 规范态：手部投影 + ModelView = I |
| 坏帧（受伤/相机动画） | `scope lens capture skipped: ... behindCamera=true`；`scope ocular pass (stencil): aim=0.56, ..., modelViewIsIdentity=false, proj.m00=0.9522, proj.m11=1.7702` | ModelView ≠ I ⇒ bounds 全在相机后面 ⇒ 镜片丢失 |

### 13.2 根因：两处「相机补偿」把变换乘进了 GL_MODELVIEW

上游 1.20 的第一人称手部 pass 里，**ModelViewMat 恒为单位矩阵**（证据链：
`GameRenderer#renderItemInHand:998-1003` 先 `posestack.setIdentity()` 再 `bobHurt(posestack)`；
`ItemInHandRenderer#renderHandsWithItems` 全程不 `applyModelViewMatrix()`，所有相机效果都写在手部 PoseStack 上）。
本工程 `FirstPersonRenderHandler#onRenderHand` 取消 `RenderHandEvent` 后要自行补这些效果，但当时把它们
**直接乘到了 GL_MODELVIEW**：

1. `compensateHurtCameraEffect`（原 `:646-700`）：`GL11.glRotatef(...)` 三处 + `GunHurtBobTweak` 临时
   PoseStack → `applyToModelView()`（= `glMultMatrix`，不回滚）；
2. `compensateBeforeRenderHandEvent`（原 `:710-719`）：`new PoseStack()` → 事件（相机动画 `mulPose`）→
   `applyToModelView()`。

⇒ 受伤 / 有相机动画的帧上 `ModelViewMat = 受伤·相机动画 ≠ I`。`ScopeLensRenderer.capture` 按 spec §8.2
断言 B 把「模型顶点坐标系 == 相机空间」当作前提（顶点由 PoseStack 烘出、GL 矩阵应为 I），于是目镜圆盘
顶点（模型空间）被多乘一次相机矩阵 ⇒ 投影到相机后面。

### 13.3 修法（只改 `FirstPersonRenderHandler.java`，语义 = 上游把效果写在 PoseStack 上）

1. **`compensateHurtCameraEffect(player, partialTicks, poseStack)`**：改为把三种旋转**右乘进枪模
   PoseStack**。`glRotatef(angle, axis)` 与 `poseStack.mulPose(Axis.XP/Y...rotationDegrees(angle))` 都是
   右乘、角度符号相同 ⇒ 逐句等价：
   - 死亡翻滚：`glRotatef(41-8000/(deathTicks+200), Z)` → `Axis.ZP.rotationDegrees(...)`；
   - 受击回正：`glRotatef(-attackedYaw, Y)` → `Axis.YP`；`glRotatef(tilt, Z)` → `Axis.ZP`；
     `glRotatef(attackedYaw, Y)` → `Axis.YP`（顺序不变）；
   - `GunHurtBobTweak.onHurtBobTweak(player, poseStack, partialTicks)`：**直接写枪模 PoseStack**
     （上游同款；不再建临时栈 + `applyToModelView()`）。
2. **`compensateBeforeRenderHandEvent(poseStack)`**：事件直接拿到枪模用的 **同一个** PoseStack
   （1.20 中 `BeforeRenderHandEvent` 拿到的就是手部 PoseStack；订阅者
   `CameraSetupEvent#applyItemInHandCameraAnimation` → `GunItemRendererWrapper#applyItemInHandCameraAnimation`
   只在上面 `mulPose` 一次，无 push/pop）⇒ 相机动画落在模型顶点上，GL_MODELVIEW 保持 I。
3. **顺序（对齐上游）**：`受伤摇晃 → BeforeRenderHandEvent 相机动画 → updateParticleEmitterTransforms →
   renderFirstPerson`。把粒子 `basePose` 的捕获挪到相机补偿**之后**：上游入口 PoseStack 本身就含
   `bobHurt` 与相机动画 ⇒ 上游 `basePose = 受伤·相机动画`。这样
   `emitterTransform = basePoseInv·muzzleView = (H·P)⁻¹·(H·P) = P` 不变，粒子视觉与修复前完全一致。
4. **`compensateVanillaOverlays(partialTicks, poseStack)`**：vanilla 的 overlay（火焰/水下/传送门/南瓜头）
   是在「带 hurt 旋转的手部矩阵」下绘制的；本实现把 hurt 挪进 PoseStack 后，这里临时
   `glPushMatrix() → poseStack.applyToModelView() → renderOverlays() → glPopMatrix()`，保持视觉等价。
   仍是**临时 push/pop**，不破坏「capture 时 ModelView = I」。
5. **异常兜底**：两处补偿在进入时快照 PoseStack 栈顶的 `pose` + `normal`，订阅者/工具抛异常时回滚
   （避免半套旋转把这一帧画歪；法线矩阵一并恢复，避免光照跳变）。

**没有改**：`pushHandProjection()`（GL_PROJECTION = 手部投影 / `ITEM_MODEL_FOV`，已经是正确形态）、
`compensateScopeLenses()`（`ScopeLensRenderer.render()` 自带 push/loadIdentity/pop，且发生在 capture 之后）、
以及 pass 的 `glPushMatrix/glLoadIdentity/popMatrix` 骨架。

### 13.4 为什么视觉等价（矩阵推导）

- 修复前：`GL_MODELVIEW = H`（受伤·相机动画），顶点由 PoseStack 烘成 `P·v` ⇒ 最终 `H·P·v`。
- 修复后：`poseStack = H·P`（先右乘 H，再叠 `renderFirstPerson` 的 P），`GL_MODELVIEW = I` ⇒ 最终 `H·P·v`。**逐项相同**。
- 粒子：修复前 `basePose = I`、GL = H ⇒ 最终 `H·v`；修复后 `basePose = H`、GL = I ⇒ 最终 `H·v`；发射器变换
  `basePoseInv·muzzleView` 两侧同时约掉 H ⇒ `P` 不变。
- overlay：临时把 H 乘回 GL 再画 ⇒ 与 vanilla「hurt 矩阵下画 overlay」一致。

### 13.5 实机判据（验收）

1. **所有**持枪 FirstPerson 帧：`TACZ: scope capture matrices ... modelViewIsIdentity=true`；
2. 受伤（被怪打）/开镜/跑动/换弹时：`TACZ: scope ocular pass ... modelViewIsIdentity=true`；
3. 不再出现 `TACZ: scope lens capture skipped: ... behindCamera=true`（真正的退化资产除外）；
4. 视觉回归（与修复前逐项对比）：
   - 受伤时枪体仍与 **世界同向同幅** 摇晃（现在来自 PoseStack，不再是 GL 矩阵）；
   - 抬枪/收枪动画、跑动晃动、开镜后坐力轨迹不变；
   - 受伤+燃烧/水下/传送门时，屏幕 overlay 仍随受伤摇晃（临时 push 路径）；
   - 开镜（ADS）镜片位置/大小、镜内成像、reticle 与修复前一致（capture 矩阵恢复为 I 后，bounds 重新有效）。

### 13.6 改动文件与自查

- 仅 `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java`
  （未改 `AnimateGeoItemRenderer` / `ScopeLensRenderer` / `GunHurtBobTweak`）；
- `lint_files(min_severity=error)` = **0 error**；本轮**未跑 gradle / mcctl**；
- 残留的 `GL_MODELVIEW` 写入盘点：pass 内只剩自身成对的 `pushToGL/popFromGL`（`RenderHelperCompat`、
  `TextShowRender`、`BeamRenderer`、`RangefinderImpactPredictionRender` 均 try/finally）与 13.3-④ 的临时
  overlay push/pop；`ItemInHandRendererMixin#beforeHandRender` 的不回滚 `glMultMatrix` 属于**vanilla 手部
  pass**（枪械路径被 `RenderHandEvent#setCanceled` 跳过，永不执行），见 §12.2。
