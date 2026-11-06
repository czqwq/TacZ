# r7-tp · 第三人称 / 玩家持枪层 / 掉落物 / 外部外观 上游等价性审查

> 任务：`task-12`。写作用域：`client/renderer/entity/**`、`client/renderer/other/**`、
> `client/renderer/external/**`、`client/animation/third/**`、
> `mixin/client/{ItemInHandLayerMixin,HumanoidModelMixin,PlayerModelMixin}`、本报告。
> 上游权威：`tmp/TACZ-1.20.1/**`（TACZ 1.20.1）、`tmp/minecraft-src-1.20.1/**`（1.20.1 原版）、
> `tmp/MinecraftForge-1.20.1/**`（Forge 1.20.1 patches）、`build/rfg/minecraft-src/**`（1.7.10）。
> 未运行 gradle（遵守约定），自检只用 IDEA lint。

---

## 一、摘要

| # | 结论 | 位置 | 状态 |
|---|---|---|---|
| **F-1** | **第三人称手持枪整体偏离挂点 0.866 格**（用户截图「枪悬空/枪口朝镜头」的几何真因）：`tacz$buildPaperFrameInverse()` 的目标帧 `F_20` 少一层 1.20 `ItemRenderer#render:129` 的**无条件** `T(-0.5,-0.5,-0.5)` | `client/renderer/item/AnimateGeoItemRenderer.java`（**r7-fp scope**） | **已修（r7-fp 落地）** |
| **F-2** | 掉落物（ENTITY/GROUND）帧缺旋转/浮沉、多一层 `S(0.5)`、缺 `+0.25 y` 与 `-0.5`；展示框应走 FIXED | 同上（r7-fp scope） | **已修（r7-fp 落地）** |
| **F-3** | **挂枪（hotbar_show，FIXED）整体偏 (0.5,0.5,0.5) 格**：直接调 `renderByItem` 绕过了 `renderStatic` 里 1.20 `ItemRenderer#render:129` 的 `-0.5` | `client/renderer/other/HumanoidOffhandRender.java:136-139` | **已修（本任务）** |
| **F-4** | `ItemInHandLayerMixin` 内 2 处 `[TACZ-DBG]` 每帧 GL 矩阵/骨骼日志（属 scope 内必须清理）+ 上游「两手持物皆空时不调用 `renderGun`」没对应 | `mixin/client/ItemInHandLayerMixin.java` | **已修（本任务）** |
| **F-5** | 子弹曳光弹：①「距眼 2 格外」判定用了 `t=0` 的插值位置（上游取当前 tick）；② 第一人称曳光弹缓存的相机 yaw/pitch 未插值（上游 `Camera#getYRot/getXRot` 是 `partialTicks` 插值量）；③ `energySwirl` 的纹理矩阵被写成随时间滚动（上游 `OffsetTexturingStateShard(15,15)` 是**常量**矩阵） | `client/renderer/entity/EntityBulletRenderer.java` | **已修（本任务）** |
| **C-1** | 第三人称枪口粒子**世界矩阵缺实体帧/手臂帧**（1.7.10 的 `captureThirdPersonMuzzle` 把一个只含模型链的空 PoseStack 当相机空间矩阵）⇒ 粒子生成位置/朝向与枪口脱钩 | `client/renderer/item/GunItemRendererWrapper.java:448/464-529`（r7-fp scope）+ `RenderHelperCompat`/`GameRendererMixin` | **已修（r7-fp 落地，VIEW/MODEL 精确分解）** |
| **C-2** | `ThirdPersonManager.MINI_GUN` 把上游 `ModelPart.x/z`（骨骼 pivot）映射成 `ModelRenderer.offsetX/offsetZ`，应为 `rotationPointX/rotationPointZ` | `api/client/other/ThirdPersonManager.java:63-65,70-72`（api scope） | **未修（跨模块，已报）** |
| **C-3** | 枪械挂在**非玩家 mob**（僵尸等）上时，1.7.10 侧没有 `ItemInHandLayerMixin` 对应注入点 ⇒ 无第三人称枪口火光/挂枪；上游该 layer 对所有 humanoid 生效 | 需新增 `RenderBiped` mixin（r7-platform scope） | **未修（低优先，记录）** |

> 截图结论见 §四。整体判定：**第三人称持枪层 = 上游语义等价 + 1 处量级 0.87 格的帧错位（已修）**；
> 掉落物/展示框 = 上游语义等价（修复后）；外部外观/第三人称动画 = 逐文件等价，
> 第三人称枪口粒子世界矩阵（C-1）亦已由 r7-fp 修；仅存平台降级与 C-2/C-3 等低优先跨模块遗留。

---

## 二、逐文件对照表

### 2.1 `client/renderer/entity/**`

| 文件 | 上游对应 | 结论 | 说明 / 动作 |
|---|---|---|---|
| `EntityBulletRenderer.java` | `client/renderer/entity/EntityBulletRenderer.java` | **等价（修 3 处）** | 弹体模型/曳光弹逻辑逐行对照：`render`→`doRender` 的世界坐标平移、`getBlockLightLevel=15`→`fullBlockBrightness`、`getPosition/getEyePosition(partialTicks)`→`interpolatedPosition/eyePosition`、`RenderType.energySwirl`→固定管线纹理矩阵，均语义等价。修 F-5 三处（见 §三 3.3） |
| `TargetMinecartRenderer.java` | `client/renderer/entity/TargetMinecartRenderer.java` | **等价（r6 修复保留）** | r6 的换算 `pre107·inner107 = pre120·inner120` 复核成立（`inner107 = T(0,1.875,0)·S(1.5)·Rx(180)`，上游 `inner120 = T(0.5,1.875,0.5)·S(1.5)·Rz(180)·Ry(-90)`）；`func_145817_o()` gate 触发器在 `entity/TargetMinecart.java:110-112` 保持 `Blocks.stone`，未回退。头颅/皮肤/`yOffset` 见 §五 已知项 |

### 2.2 `client/renderer/other/**`

| 文件 | 上游对应 | 结论 | 说明 / 动作 |
|---|---|---|---|
| `HumanoidOffhandRender.java` | 同名 | **等价（修 1 处）** | 挂枪 `hotbar_show` pose、`getHotbarShow`/`currentItem` 跳过主手、`guns` 过滤、`RenderConfig` 开关逐行等价；缺上游 `renderStatic` 的 `T(-0.5,-0.5,-0.5)` → 已修（F-3）。`renderOffhandGun` 空实现 = 有据降级（1.7.10 无双持槽） |
| `GunHurtBobTweak.java` | 同名 | **等价** | `hurtTime - partialTicks`、`/hurtDuration`（→1.7.10 `maxHurtTime`）、`sin(x⁴π)`、`getHurtDir`（→`attackedAtYaw`）、`markTimestamp/resetTimestamp` 全同；`hurtTime`/`hurtDuration` 的 float/int 差异已注释 |
| `CameraAngles.java` | **无对应类**（替代 `ViewportEvent.ComputeCameraAngles`） | **有据降级（数据容器）** | 1.7.10 无相机角度事件；本类只是 yaw/pitch/roll 的可变三元组，语义与上游 getter/setter 一致，消费方 `CameraSetupEvent` 负责写回。**保留** |

### 2.3 `client/renderer/external/**`

| 文件 | 上游对应 | 结论 | 说明 |
|---|---|---|---|
| `ExternalGunAppearance.java` | 同名 | **等价** | `resolve/copyStack`、`GeometryKey`/`Snapshot`/`Variants`/`Status`、`attachmentModels`/`supported()`/`prepare()` 全同；仅 `ItemStack.EMPTY`→`null`、`List.copyOf`→`Collections.unmodifiableList` 平台改写 |
| `ExternalGunEffects.java` | 同名 | **等价（但 1.7.10 恒不执行）** | 逐行同构；它只被 `ExternalGunItemVboRenderer.tryRender` 调用，而该 VBO 路径在 1.7.10 恒 `UNSUPPORTED`（无 VBO 后端，有据降级） |
| `ExternalShellRenderer.java` | 同名 | **等价（+加固）** | 弹壳队列/寿命/反弹运动/角速度逐行同构；多一处 `gunData == null` 判空（防渲染线程 NPE），不影响等价语义 |

### 2.4 `client/animation/third/**`

| 文件 | 上游对应 | 结论 |
|---|---|---|
| `AnimationRenderContext.java` | 同名 | **逐行相同**（ThreadLocal 作用域、`isFirstPersonHand` 链式查找） |
| `InnerThirdPersonManager.java` | 同名 | **等价**：`isFirstPersonHand` 闸门、`IGunOperator`、`getHeldItem`、`PlayerAnimatorCompat`、`getSynAimingProgress`、hold/aim 分支一致；`ModelPart`→`ModelRenderer` |
| `PlayerModelRenderState.java` | 同名 | **等价 + 有据降级**：1.7.10 无 `PlayerModel`，快照 `ModelBiped` 的 `rotationPoint/rotateAngle/offset/showModel/isHidden` 与 `isSneak/isRiding/isChild/heldItemLeft/Right/aimedBow`；上游 `attackTime/swimAmount/leftArmPose/rightArmPose` 与 `PlayerModel` 独立袖子骨骼无对应（已注释 `TODO(port)`） |

### 2.5 `mixin/client/**`

| 文件 | 上游对应 | 结论 | 说明 |
|---|---|---|---|
| `ItemInHandLayerMixin.java` | `ItemInHandLayerMixin` | **等价改写（清理 + 修 1 处）** | 上游两处注入 `ItemInHandLayer#renderArmWithItem` HEAD/TAIL + `#render` TAIL；1.7.10 无 RenderLayer，按 `RenderPlayer#renderEquippedItems:313-414` HEAD/RETURN 包住（父类 `RendererLivingEntity#renderEquippedItems:380` 空实现 ⇒ 玩家持物**只画一次**，r4 已定案）。`currentEntity`/`isSelf` 置位复位、`HumanoidOffhandRender.renderGun` 调用点、packedLight 取法等价。**已清 2 处 `[TACZ-DBG]`**；补上游「两手持物皆空 ⇒ 不执行 `render`（也就不调用 `renderGun`）」的等价守卫（1.7.10 无双持槽 ⇒ 判主手 `getHeldItem()==null`） |
| `HumanoidModelMixin.java` | `HumanoidModelMixin` | **等价** | 上游 `HumanoidModel#setupAnim` TAIL → 1.7.10 同一 `ModelBiped#setRotationAngles` TAIL；形参映射 `(limbSwing,limbSwingAmount,ageInTicks,netHeadYaw,headPitch,scale,Entity)` 正确，`bipedHead/Body/LeftArm/RightArm` 一一对应 `head/body/leftArm/rightArm`；1.7.10 字段非 final（已正确不写 `@Final`） |
| `PlayerModelMixin.java` | `PlayerModelMixin` | **等价** | 「第一人称手持 + 主手是枪」时清零双臂 `rotateAngleX/Y/Z`；`ItemStack.EMPTY`→`null`；上游 `rightSleeve/leftSleeve.copyFrom` 在 1.7.10 无独立袖子骨骼（袖子与手臂共用骨骼）⇒ 删除后行为等价。两 mixin 同目标类同注入点，顺序靠 `priority 1000/1100`，见 §六 遗留观察 |

### 2.6 只读复核（不在写作用域，仅核对接口/调用点）

| 位置 | 结论 |
|---|---|
| `client/model/RenderHelperCompat#displayContextOf` | `EQUIPPED→THIRD_PERSON_RIGHT_HAND`、`EQUIPPED_FIRST_PERSON→firstPerson(leftHand)`、`ENTITY→GROUND`、`INVENTORY→GUI`、`null→GROUND` 映射正确（1.7.10 `EQUIPPED_FIRST_PERSON` 只有右手，`leftHand` 恒 false） |
| `client/renderer/item/GunItemRendererWrapper#renderByItem` | 与上游逐行同构；对 `FIRST_PERSON_*`/`THIRD_PERSON_LEFT_HAND` 早退、GUI 槽位贴图、LOD/距离、`GunItemTransforms.apply` 一致 |
| `client/renderer/item/GunItemTransforms` | 与上游同序（`T(0.5,2,0.5)`、`scale(-1,-1,1)`、positioning 逆链、`scale` 绕 y=1.5），数值验算：三种定位节点（`thirdperson_hand/ground/fixed`）经它精确落到 (0.5,0.5,0.5)（= 1.20 `-0.5` 的居中目标点），**互相印证 F-1/F-3 的 `-0.5` 确实必须存在** |
| `api/client/other/ThirdPersonManager` | 见 C-2（MINI_GUN 字段映射） |

---

## 三、修复明细

### 3.1 F-3 · 挂枪（FIXED）补 `T(-0.5,-0.5,-0.5)`（本任务，已完成）

`HumanoidOffhandRender#renderGunItem` 为绕开 1.7.10 的 2D 纸片帧，改为直接调
`renderer.renderByItem(stack, FIXED, …)`。但上游入口是
`Minecraft#getItemRenderer().renderStatic(stack, FIXED, …, matrixStack, …)`
（`tmp/TACZ-1.20.1/.../HumanoidOffhandRender.java:91`），`ItemRenderer#render` 在
BEWLR 之前无条件执行 `posestack.translate(-0.5F, -0.5F, -0.5F)`
（`tmp/minecraft-src-1.20.1/.../entity/ItemRenderer.java:128-129`；Forge 补丁
`tmp/MinecraftForge-1.20.1/patches/minecraft/net/minecraft/client/renderer/entity/ItemRenderer.java.patch:16-18`）。
绕过 `renderStatic` 就丢掉了这一层 ⇒ 挂枪在世界里整体偏 `(0.5,0.5,0.5)` 格（乘 `hotbar_show`
的 `scale 0.5` 后约 0.43 格可见偏移）。

落地：`HumanoidOffhandRender.java:139` 在 `renderByItem` 前 `matrixStack.translate(-0.5f,-0.5f,-0.5f)`。
（同型问题在第三人称 EQUIPPED 帧是 F-1，已由 r7-fp 修。）

### 3.2 F-4 · `ItemInHandLayerMixin` 清理与等价守卫（本任务，已完成）

- 删除 `renderArmWithItemHead` 里整段 `[TACZ-DBG]`：每帧 `glGetFloat(GL_MODELVIEW_MATRIX)` +
  `bipedRightArm` 姿态日志（及其 `FloatBuffer/BufferUtils/GL11/ModelRenderer/Render/RenderManager`
  导入与 `RenderHelperCompat` 引用）。scope 内 `[TACZ-DBG]` 现为 **0**。
- 补等价守卫：上游 `ItemInHandLayer#render` 在**两手持物皆空**时直接 `return`（TAIL 也不执行），
  因此 `HumanoidOffhandRender.renderGun(...)` 不会被调用；1.7.10 无双持槽 ⇒ 用
  `livingEntity.getHeldItem() == null` 早退（`ItemInHandLayerMixin.java:77`）。修复前「空手也显示挂枪」。
- `partialTicks` 形参删掉（Mixin 允许省略尾部形参），javadoc 同步。

### 3.3 F-5 · `EntityBulletRenderer` 三处偏离（本任务，已完成）

| 项 | 修复前 | 上游依据 | 修法 |
|---|---|---|---|
| 曳光弹「距眼 2 格」判定 | `eyePosition(shooter, 0f)` = **上一 tick** 眼位 | `tmp/TACZ-1.20.1/.../EntityBulletRenderer.java:124` `shooter.getEyePosition()`（无参 = 当前 tick，`Entity.java:1474-1476`） | `eyePosition(shooter, 1.0F)`（`EntityBulletRenderer.java:190`） |
| 第一人称曳光弹缓存的相机朝向 | `thePlayer.rotationYaw/Pitch`（未插值） | 上游 `Camera#getYRot/getXRot`（`Camera.java:46` 用 `entity.getViewYRot/getViewXRot(partialTicks)`） | `cameraYaw(partialTicks)/cameraPitch(partialTicks)`（`:335/:344`，用 `renderViewEntity` 的 prev→cur 插值，对应 1.7.10 `EntityRenderer#orientCamera:641-642`） |
| `energySwirl` 纹理矩阵 | 随时间滚动 `(timeTicks/20)%1` | 上游 `RenderType.energySwirl(tex,15,15)` → `OffsetTexturingStateShard(15,15)` = **常量** `TextureMat=translation(15,15,0)`（`RenderStateShard.java:554-556`），vsh `texCoord0=(TextureMat*UV0).xy`（`rendertype_energy_swirl.vsh:23`） | `pushEnergySwirlTextureMatrix()` 改为常量 `glTranslatef(15,15,0)`（`:377`），注释更正。GL_REPEAT 下与上游等价 |

### 3.4 未改动但已确认「等价」的关键点（避免下一轮重复排查）

1. `RenderPlayer#renderEquippedItems` 的第三人称持物**每帧只画一次**（父类 `RendererLivingEntity`
   的 `renderEquippedItems` 为空；`RenderBiped` 那段形参是 `EntityLiving`，玩家不可达）。
2. 1.7.10 纸片帧 = `RenderPlayer:317-319`（`bipedRightArm.postRender` + `T(-1/16,7/16,1/16)`）
   + `:377-385`（2D 分支）+ `ForgeHooksClient:219`（`EQUIPPED_BLOCK` 的 `T(-0.5,-0.5,-0.5)`），
   与 `AnimateGeoItemRenderer#tacz$buildPaperFrameInverse` 里 `f` 逐项一致。
3. 1.20 `translateToHand(arm)` = `T(pivot/16)·Rz·Ry·Rx` = 1.7.10 `ModelRenderer#postRender`
   （`ModelRenderer.java:239-278`，`bipedRightArm` pivot `(-5,2,0)`，与 1.20 `HumanoidModel.java:77-86` 相同）。
4. 1.7.10 `ForgeHooksClient#renderEntityItem:89-104` 的 helper 语义 =「请求才施加旋转/浮沉」，
   与 1.20 `ItemEntityRenderer#render:63-69` 的 bob+spin 一一对应；`ELSE` 分支的 `S(0.5)` 已在
   F-2 补偿。

---

## 四、截图判读（`a_thirdperson_gun_rangefinder.png`）

已与 r7-crosshair 的像素级结论对齐，独立复核如下：

| 现象 | 判读 | 依据 |
|---|---|---|
| 中央白色 bracket + `0.0%` | **`HeatBarOverlay`（minigun 热条），不是 rangefinder** | r7-crosshair 报告 §二；`HeatBarOverlay` 只在 gun data `heat` 存在时渲染，默认包只有 minigun；文本格式 `DecimalFormat("0.0%")`。rangefinder 在第三人称/非开镜不渲染（`TextShowRender` firstPerson 闸门 + SBM `thirdPersonView != 0` 早退） |
| 枪 = **minigun** | 选中槽（第 3 格）图标与 `textures/gun/slot/minigun.png` 完全一致（黄+灰机身、多管枪管组）；此前误判为狙击枪 | 本任务逐像素比对；minigun 也解释了为何 HUD 显示热条 |
| 枪「悬空/掉在胯部附近/看起来离镜头更近」 | **第三人称 EQUIPPED 帧偏 0.866 格**（F-1），已由 r7-fp 修 | 数值验算见 §四.1；与 r7-fp 判断一致 |
| 玩家身体可见 + 手部持枪姿态但手上没有枪 | 与 F-1 完全自洽：姿态来自 `InnerThirdPersonManager`（正常），模型挂点在 0.87 格外 | `InnerThirdPersonManager` 只改骨骼、不移动枪 |
| r7-crosshair 提出的「第一人称手模 + 本地玩家身体同屏」 | **静态不成立/待实机复核**：① 1.7.10 第一人称下本地玩家实体不渲染（`RenderGlobal.java:524`）；② SBM `FirstPersonRenderHandler#onRenderHand:428-432` 在 `thirdPersonView != 0` 早退不 cancel；③ `GunItemRendererWrapper#renderByItem` 对 `FIRST_PERSON_*` 直接 return；④ 截图中枪的尺度/位置更符合世界空间的第三人称 held-item（若枪在相机空间，0.87 格的偏移方向不会投影成画面左下）。**结论：第三人称 + F-1**；若实机修复后仍看到「身体与第一人称手模同屏」，再查 ① YSM 的第一人称身体模型（YSM 自身特性）、② `RenderGlobal:524` 的 `renderViewEntity` 是否为本地玩家（若有 mod 改动） |
| HUD 缺 ammo 计数（推测） | 可能是 HUD 布局问题，不属本模块 | 截图无右上/右下 ammo 文本；`GunHudOverlay` 无第三人称闸门、与 `HeatBarOverlay` 共用 `GUN_HUD_ENABLE` ⇒ 若热条在显示而 ammo 不在，交 r7-crosshair/r7-gui 核对 HUD 坐标（本模块不碰） |

### 4.1 F-1 的数值验算（供复核 r7-fp 的修复）

以默认包 `m700` 为例（`display/guns/m700_display.json` 的 `third_person scale=0.6`，
geometry 定位节点 `positioning→thirdperson_hand`），用数值矩阵模拟两条链：

```
上游：P_arm · [Rx(-90)·Ry(180)·T(1/16,0.125,-0.625)·T(-0.5,-0.5,-0.5)]
              · [T(0.5,1.5,0.5)·Rz(180)] · GunItemTransforms(third)
补偿后：同上，但 F_20 无 -0.5
差   ：在 F_display 末端帧多 T(0.5,0.5,0.5)
```

`thirdperson_hand` 挂点与 `muzzle_pos` 的位移 = `(0.497, 0.512, 0.490)` 格（**模长 0.866**；
乘 `RenderPlayer#preRenderCallback` 的 0.9375 后 ≈ 0.81 格），两者位移相同（纯平移，方向随臂姿态旋转）。
同时验证了 `GunItemTransforms` 的定位节点反算：`thirdperson_hand/ground/fixed` 三种定位节点在
`renderByItem` 之后都精确落在 (0.5,0.5,0.5) —— 这正是 1.20 `T(-0.5,-0.5,-0.5)` 要消掉的常量，
所以缺这层时偏移量级恰好是半格的 √3 倍。

---

## 五、跨模块发现（已 `send_message` 给 Lead + r7-fp）

### C-1（已由 r7-fp 修复）第三人称枪口粒子的世界矩阵缺实体帧/手臂帧

`GunItemRendererWrapper#captureThirdPersonMuzzle(PoseStack, BedrockGunModel)`
（`client/renderer/item/GunItemRendererWrapper.java:464-477`）把
`poseStack.last().pose()` 当作**相机空间**矩阵传给 `captureThirdPersonMuzzle(Matrix4f)`
（旧实现 `:496-529`：`viewRotationInv.mul(pose) + camPos`）。

上游成立的前提是：1.20 BEWLR 收到的 PoseStack 已经包含
`EntityRenderDispatcher.render` 的 `translate(entity-camera)` + `RenderPlayer` 的
`Ry(180-yaw)·S(-1,-1,1)·translate(0,-1.501,0)` + 手臂帧
（`tmp/minecraft-src-1.20.1/.../entity/EntityRenderDispatcher.java:148-150`；
`tmp/TACZ-1.20.1/.../GunItemRendererWrapper.java:386-414` 的 `captureThirdPersonMuzzle`
正是直接吃这个 PoseStack）。1.7.10 移植侧 `renderItem` 用的是**新建的空 `PoseStack`**
（只含 `C` 与模型链），实体帧/手臂帧在 **GL_MODELVIEW** 里，因此 `pose` 不含实体帧
⇒ `muzzleWorld` 少了 `T(entity-camera)·Ry·S(-1,-1,1)·P_arm`，世界位置/朝向与枪口脱钩。

**给 Lead 的初版一行级建议（`viewRotationInv·GL_MODELVIEW·pose + 眼位`）不够精确**：r7-fp 推导
指出 1.7.10 第三人称 VIEW 里含 `glTranslatef(0,0,-d7)`（`EntityRenderer.java:629`）、eye-height
（`:645`）、view-bobbing/hurt/roll/传送门扭曲，而 `viewRotationInv` 只抵消旋转部分；且 `camPos`
取眼位（不是后拉后的相机位）会再叠一次 ⇒ 有 ~d7 量级误差。

**最终落地（更彻底，与上游逐项等价）**：
1. `GameRendererMixin` 在 `setupCameraTransform` RETURN 抓**纯视图矩阵**（该函数以
   `glLoadIdentity()` 开头 `:683`，之后只有相机效果 `:690-715`）；
2. `RenderHelperCompat` 新增 `captureViewMatrix/viewMatrixInverse/currentModelViewMatrix`；
3. `captureThirdPersonMuzzle(Matrix4f)` 改为
   `T(RenderManager.renderPos)·VIEW⁻¹·GL_MODELVIEW·pose`：
   `VIEW⁻¹·GL_MODELVIEW = MODEL`（相机相对实体帧，自动含 1.7.10 手臂帧+纸片帧），
   `renderPos`（`RenderGlobal.java:459-482` 的 renderViewEntity 插值位置）正是 MODEL 里
   `T(entity-renderPos)` 的基准 ⇒ 展开 = `T(entity)·R_e·S·T(0,-1.501,0)·P_arm·F20·GIT·chain`，
   与上游 `T(camPos)·R_cam⁻¹·pose` 展开逐项相同。旧的 `cameraYaw/cameraPitch/Vec3/CameraStateCache`
   近似代码已删。
4. `ExternalGunEffects#renderThirdPerson:50-52` 的同类调用点**不需要单独改**：它也在同一 GL 实体帧里
   调同一个 `captureThirdPersonMuzzle(Matrix4f)`，语义一致（该路径仍恒 `UNSUPPORTED`，死代码）。

另核对：上游 `MuzzleFlashRender`/`ShellRender` 只吃模型窗口 PoseStack（没有
`RenderSystem.getModelViewMatrix()`），第三人称世界矩阵的唯一来源就是
`GunItemRendererWrapper#captureThirdPersonMuzzle` —— 所以「准绳 = 上游
`GunItemRendererWrapper.java:386-414`」这一判断成立。改动文件：
`client/model/RenderHelperCompat.java`、`mixin/client/GameRendererMixin.java`、
`client/renderer/item/GunItemRendererWrapper.java`（r7-fp 报告 `docs/review/r7/fp.md` §11，
实机验证项 V-10）。

### C-2（开放，P2）`ThirdPersonManager.MINI_GUN` 字段映射

上游 `ModelPart.x/z` 是**骨骼 pivot**（`tmp/TACZ-1.20.1/.../ThirdPersonManager.java:45-47,52-54`），
1.7.10 对应 `ModelRenderer.rotationPointX/rotationPointZ`；移植写成 `offsetX/offsetZ`
（`src/main/java/com/tacz/guns/api/client/other/ThirdPersonManager.java:63-65,70-72`）。
`ModelRenderer#postRender` 只用 rotationPoint（`ModelRenderer.java:250-274`），`offset*` 不参与
⇒ 手持 minigun 的第三人称持枪姿态会错（或退化）。属 api scope（r7-api）。

### C-3（开放，低）非玩家 humanoid mob 的第三人称持枪层缺失

上游 `ItemInHandLayer` 挂在所有 humanoid renderer 上；移植只在 `RenderPlayer` 上注入了等价逻辑。
僵尸/骷髅等手持 TACZ 枪时：无 `ThirdPersonMuzzleParticleManager.currentEntity`（⇒ 无第三人称
枪口火光）、无挂枪、`MuzzleFlashRender.isSelf` 不置位（与上游一致，非玩家本来就不置位）。
1.7.10 侧 mob 手持物由 `RenderBiped#renderEquippedItems:250-336` 走纸片帧 + 我们的
`IItemRenderer(EQUIPPED)`，几何位置由 F-1 的补偿覆盖，**静态不崩**，仅缺粒子。需新增
`RenderBiped` mixin（r7-platform scope 决定是否做）。是否有实际玩法影响取决于是否有枪能落到 mob 手上。

### 引用 r7-fp 的跨模块发现

`AmmoItemRenderer`/`AttachmentItemRenderer`（弹药盒/配件）：`assets/tacz/models/item/ammo.json`、
`attachment.json` 带 `display` 段，但 1.7.10 侧没有任何代码消费 item model 的 display 变换
⇒ 这两类物品在掉落物/手持/展示框里的位置与大小不等价。与 F-1/F-3 属同一族（缺 1.20 `ItemRenderer#render`
的 display 帧），但需要「item model display 数据加载」这个独立缺口，建议单开任务。

---

## 六、未修 / 有据降级 / 待实机

**有据降级（平台差异，不是缺陷）**

1. 1.7.10 无双持槽 ⇒ 副手挂枪不渲染（`renderOffhandGun` 空实现，保留方法名/签名）。
2. 1.7.10 玩家模型只有 `ModelBiped`（无 `PlayerModel` 独立袖子/裤子/夹克、无 slim 3px 手臂）：
   `PlayerModelRenderState` 只快照存在的骨骼；袖子跟随手臂由 `RenderPlayer` 的皮肤层实现。
3. 1.7.10 实体帧 `translate(0,-24/16-0.0078125,0)` vs 1.20 `(0,-1.501,0)`：**0.0068 格**常量差，
   影响所有第三人称位置 → 属平台固有（小于 0.11 模型像素），不修。
4. 1.7.10 无 `ViewportEvent.ComputeCameraAngles` ⇒ `CameraAngles` 数据容器 + `CameraSetupEvent` 写回。
5. 1.7.10 Forge 无 `FIXED` 渲染入口 ⇒ 挂枪直调 `renderByItem(FIXED)`（F-3 已补 -0.5）。
6. 掉落物/展示框的**外层**帧仍是 1.7.10 原版（`RenderItemFrame:174-189` 的 frame 变换、
   `RenderItem#doRender:95` 的 bob）；TACZ 侧已按 F-2 补偿到与 1.20 同构，但 frame 朝向的
   两版原版差异（1.20 `ItemFrameRenderer` 的 translate/rotate 组合）不可能逐值复现，属平台。
7. 靶车皮肤：1.7.10 无 `DefaultPlayerSkin` 按 UUID 选 Alex/Steve ⇒ 固定 Steve（r6 已记录）。
8. 靶车命中盒 `yOffset`（r6 记录，entity scope，未修）。
9. `EntityBulletRenderer` 的 `energySwirl` 用固定管线纹理矩阵替代 shader；`getBlockLightLevel`
   用「强制 block=15」替代渲染器钩子；弹体拖尾 `S(0.5)` 之类的 1.20 `ItemEntityRenderer` 物品栈
   偏移对子弹不适用（子弹走自定义 model，非 item model）。

**未修（跨模块，见 §五）**：C-2 MINI_GUN pivot（api scope）；C-3 mob 持枪（platform scope，低）；
ammo/attachment 的 display 段（独立缺口）。C-1 已由 r7-fp 修（见 §五）。

**观察（不修，记录）**：`HumanoidModelMixin`(priority 1000) 与 `PlayerModelMixin`(priority 1100)
同目标同注入点，执行顺序依赖 Mixin「低 priority 先 apply ⇒ 同注入点回调先追加」的语义
（`docs/review/07-mixin-compat.md` 已按此定案）。若后续实机发现第一人称手臂归零失效
（= 两个回调顺序反了），建议不要调 priority，而是把「第一人称手持 + 主手是枪 ⇒ 手臂归零」
在 `HumanoidModelMixin` 回调末尾再做一次（幂等），使行为与顺序无关。

---

## 七、实机验证清单（按优先级）

1. **第三人称（F5×1 背后、F5×2 正面）持枪**：枪应贴合双手、枪口沿视线前方；
   这是 F-1（0.866 格）修复的直接验收点。用 minigun（截图那把）+ 一把步枪各看一次。
2. **第三人称开火**：枪口火光/烟雾应贴着枪口（C-1 已由 r7-fp 修：`VIEW⁻¹·GL_MODELVIEW` 精确分解；
   对应其实机项 V-10，见 `docs/review/r7/fp.md` §11）。
3. **挂枪（hotbar_show）**：主手拿 A 枪、快捷栏其它格放枪，第三人称看挂枪是否贴合身体；
   选中**空槽**时不应显示挂枪（F-4 守卫）。
4. **掉落物**：丢一把枪 → 大小应约为玩家的 0.8~1 倍（不再是半大）、有自转与浮沉、不偏移；
   **展示框**里放枪 → 应与 1.20 同向贴合框面（F-2）。
5. **曳光弹**：第一人称与第三人称开火，曳光弹起点应贴合枪口、无 1 tick 滞后抖动；
   弹体纹理应为**静态**（F-5 第三项）。
6. **其他玩家**：第三人称观察网络玩家持枪（位置/姿态/挂枪）；观察其开火时第三人称枪口粒子
   （C-1 已修，与 V-10 一并验收）。
7. **换枪过渡**：`equippedProgress` 0→1 期间第三人称枪的抬枪位移（上游 1.7.10 无此动画，
   记录现象即可，不要当 bug）。
8. **靶车**（r6 回归）：放置后应有完整模型、贴轨道高度、靶面沿轨道方向；命中判定不变。
9. **SBM 第一人称手臂**：`RenderHelperCompat.RENDER_FIRST_PERSON_VANILLA_ARM = true` 时
   手臂应与枪同在镜内（§五 观察项若实机异常，按该条兜底）。

---

## 八、自检

- 运行：**未运行 gradle**（遵守约定）。
- `mcp__idea__lint_files(min_severity="error")` 对本任务全部 scope 文件（含新增/改写文件）：
  **0 error**。
- 本任务改动文件：
  | 文件 | 改动 |
  |---|---|
  | `client/renderer/other/HumanoidOffhandRender.java` | 补 `T(-0.5,-0.5,-0.5)`（F-3）；javadoc/注释精简与依据行号 |
  | `mixin/client/ItemInHandLayerMixin.java` | 清 2 处 `[TACZ-DBG]`（含无用 import）；补空手守卫；javadoc 精简为等价映射 |
  | `client/renderer/entity/EntityBulletRenderer.java` | 三处等价性修复（F-5）；清 1 个未用局部变量 |
- scope 内 `[TACZ-DBG]`：**0 处**（`grep` 已验证 4 个包 + 3 个 mixin）。
- 未触碰：`tmp/**`、`build/**`、`run/**`、`src/main/resources/**`、其它 teammate 的写作用域
  （跨模块项只报告 + 消息，不落笔）。
