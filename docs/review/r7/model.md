# r7-model · TACZ 模型 / 动画 / 显示变换 上游等价性审查

任务：task-15。作用域：
`client/model/**`（排除 `RenderHelperCompat.java`=r7-fp、`BedrockAttachmentModel.java`=r7-scope、
`functional/RangefinderImpactPredictionRender.java`=r7-crosshair）、
`client/animation/**`（排除 `animation/third/**`=r7-tp）、
`client/resource/pojo/{model,animation,display}/**`（排除 `display/gun/{Rangefinder,RangefinderManager,TextShow}.java`=r7-crosshair、
`display/attachment/ScopeLensConfig.java`=r7-scope）。

**未运行 gradle**；本次改动的 9 个 java 文件 `mcp__idea__lint_files(min_severity="error")` = 0 error；
作用域内 107 个 java 文件已全量 grep，**无 `[TACZ-DBG]` 残留**（唯一命中在排除项 `RenderHelperCompat.java`，属 r7-fp）。

对比基准：上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`、
1.20.1 原版/Forge `tmp/minecraft-src-1.20.1/**`、1.7.10 原版 `build/rfg/minecraft-src/java/**`。
方法：对作用域内每个文件做「去注释 + trim 后逐行比对」（`Compare-Object`），差异行全部人工判定；
几何/渲染/动画路径再逐行读源码确认。

---

## 0. 结论速览

1. **发现并修复 1 处真实移植偏离**：`BlockItemTransforms.ItemTransform#apply` 把 1.20 的**单次合成旋转**
   （`new Quaternionf().rotationXYZ(x, y, z)`）实现成了**三次 `mulPose`（Z→Y→X）**。默认枪包数据里
   `gui: rotation [15, -145, 0]`、`[30, 225, 0]`、`thirdperson *: [75, 45, 0]` 都是**双轴旋转**，
   两种合成的结果不同 ⇒ 工作台类方块物品的 GUI 图标 / 手持 / 地面朝向与上游不一致。见 §1.1。
2. **另外修了 1 处「机制等价但用了不同 GL 手段」的镭射层状态**（`BeamRenderer`）：上游
   `VIEW_OFFSET_Z_LAYERING` 是给 modelview 乘 `scale(0.99975586)`，移植侧原先用 `glPolygonOffset(-1,-10)`；
   上游 `LIGHTNING_ADDITIVE_TRANSPARENCY` 是 `blendFuncSeparate(SRC_ALPHA, ONE, ONE, ZERO)`，移植侧原先用
   `glBlendFunc(SRC_ALPHA, ONE)`。两者都能「看起来差不多」，但既然都能精确还原，就按上游还原（见 §1.2）。
3. **删掉一段移植期遗留死代码**：`BedrockAnimatedModel.hasBuiltinHandModel()` + 缓存字段 + 两个递归辅助方法
   （仅被注释引用，无任何调用点），并同步修正 `LeftHandRender`/`RightHandRender` 里**与现状矛盾**的 javadoc
   （原文写「模型自带手时不注册」，实际 `BedrockGunModel.java:91/93` 是**无条件注册** = 上游行为）。
4. **其余 100+ 文件与上游等价**（几何解析、骨骼层级、UV/mirror、动画关键帧装配、状态机上下文、显示 POJO、
   功能渲染器、papi/listener 全部逐行核对），逐文件结论见 §2。
5. 未发现第二处「漏 ÷16 / 漏 clamp」：作用域内全部 `/16`、`*16`、`Mth.clamp` 调用点已逐个与上游对照（§3.1）。
   1/16 单位问题仍然只存在于 1.20 原版的 `ItemTransform`（r6 已修平移，本轮补旋转）。
6. 有据降级 7 项（stencil 无、ARCompat 加速管线缺、`energySwirl` 缺、`shaderLightDirections` 缺、
   `Font#drawInBatch` 缺、缺贴图 fallback、未知缓动名宽容处理），均为平台能力缺失，不做第三种语义，见 §3.2。

---

## 1. 修复项

### 1.1 `BlockItemTransforms.ItemTransform#apply` 旋转合成顺序（真实偏离）

| 项 | 本工程（修前） | 上游 1.20.1 |
|---|---|---|
| 旋转 | `mulPose(Axis.ZP.rotationDegrees(z*mirror))` → `Axis.YP` → `Axis.XP` 三次右乘 = `Rz·Ry·Rx` | 一次 `mulPose(new Quaternionf().rotationXYZ(x, y*mirror, z*mirror))`<br>`tmp/minecraft-src-1.20.1/src/main/java/net/minecraft/client/renderer/block/model/ItemTransform.java:44` |
| 含义 | 先绕 X、再绕 Y、再绕 Z（列向量约定） | JOML `rotationXYZ(a,b,c)` ≡ `rotationX(a).rotateY(b).rotateZ(c)`（右乘），即**先 Z、再 Y、再 X**<br>JOML 1.10.5 `Quaternionf#rotationXYZ` javadoc |
| 平移 / 缩放 | `translation*1/16` clamp ±5、`scale` clamp ±4 | 反序列化时 `mul(0.0625F)` + clamp、`scale` clamp（`ItemTransform.java:81-84`）——r6 已补齐，本轮复核**正确** |

- 关键点：单轴旋转（`[0,90,0]`、`[0,0,0]`、`[15,0,0]`）两种写法结果相同，所以**只有双轴/三轴旋转的物品会错**。
  默认枪包正好有双轴数据：
  `assets/tacz/custom/tacz_default_gun/assets/tacz/display/blocks/gun_smith_table.json` 的
  `gui: rotation [15, -145, 0]`、`ammo_workbench.json` 的 `gui: rotation [30, 225, 0]` 与
  `thirdperson_righthand/lefthand: [75, 45, 0]`。
- 修法：`BlockItemTransforms.java:127-146` 改为 `new Quaternionf().rotationXYZ(rx*rad, ry*mirror*rad, rz*mirror*rad)`
  （`rad = (float)(Math.PI/180.0)`，与上游同运算、同顺序、同左手取反位置），并去掉不再使用的 `mcshim.Axis` import。
- 消费点（不在本作用域，共享受益）：`client/renderer/item/GunSmithTableItemRenderer.java:71-72`
  （`transforms.getTransform(transformType).apply(false, poseStack)`，与上游 `GunSmithTableItemRenderer.java:39-45` 同构）、
  `client/renderer/item/BlockModelGuiItemRenderer.java:288`（解析原版 block model 的 `display.gui` 复用本类）。

### 1.2 `BeamRenderer.LaserBeamRenderState` 镭射层 GL 状态（等价手段替换为同参数）

| 上游（1.20 `RenderStateShard`） | 本工程（修前） | 本工程（修后） |
|---|---|---|
| `LIGHTNING_ADDITIVE_TRANSPARENCY`：`blendFuncSeparate(SRC_ALPHA, ONE, ONE, ZERO)` | `glBlendFunc(SRC_ALPHA, ONE)`（alpha 通道也按 `(SRC_ALPHA, ONE)` 处理，与上游不同） | `GL14.glBlendFuncSeparate(SRC_ALPHA, ONE, ONE, ZERO)`（`BeamRenderer.java:234`） |
| `VIEW_OFFSET_Z_LAYERING`：modelview `scale(0.99975586)`（`RenderStateShard.java:286-296`） | `glPolygonOffset(-1,-10)` + `GL_POLYGON_OFFSET_FILL`（那是 1.20 的 `POLYGON_OFFSET_LAYERING`，上游镭射**没用**这个） | `pushToGL()` 之后 `glScalef(0.99975586)`（`BeamRenderer.java:160,235-238`）——GL 矩阵右乘，与「modelview × scale」完全等价 |

- 其余状态保持不变（`NO_CULL` → `glDisable(GL_CULL_FACE)`；`LIGHTMAP` → `Tessellator#setBrightness(FULL_BRIGHT)`；
  `OUTPUT(ITEM_ENTITY_TARGET)` → 1.7.10 只有主帧缓冲，无操作；`COLOR_DEPTH_WRITE` → 默认开启）。
- 几何、贴图（`assets/tacz/textures/entity/beam.png` 已存在）、UV、顶点顺序、`fadeOut` 混合前后的 alpha 与上游逐行一致（未改）。
- 混合函数没有可回读状态，`restoreGlState()` 仍统一还原成原版半透明默认（`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`），
  已在注释里点明。

### 1.3 死代码与矛盾注释清理（不改行为）

| 文件 | 处理 |
|---|---|
| `client/model/BedrockAnimatedModel.java` | 删除 `hasBuiltinHandModel()`、`tacz$builtinHandModel` 缓存字段、`tacz$subtreeHasCube(...)` 两个重载（全仓 grep：**零调用点**，只在注释里被提及）。行为无变化：`BedrockGunModel` 构造器无条件注册左右手渲染器。 |
| `client/model/functional/LeftHandRender.java`、`RightHandRender.java` | 类注释原写「本类只是兜底路径，模型自带手时工厂返回 null ⇒ 画自带手」——与现状相反（现状=无条件注册，且 `FunctionalBedrockPart` 有功能渲染器时不画自身 cube）。改成「为什么必须在注册处决定 + 1.7.10 `ModelBiped` 手臂与 1.20 `PlayerModel` 不配套」的简明说明。 |
| `client/model/BedrockGunModel.java` | 两段超长注释压缩为「结论 + 平台依据 + 报告指针」：①构造器手部渲染 30 行 → 4 行；②`renderUnaccelerated` 的 stencil 34 行 → 6 行（保留 `ForgeHooksClient.java:317-341`、`Framebuffer.java:117-130` 依据与 `-Dforge.forceDisplayStencil=true` 恢复条件，**结论与 r6 决策一致，未改行为**）。 |
| `client/animation/screen/RefitTransform.java` | 类注释里的「r2 修复（两处）」历史叙述压成一段「平台差异 + 依据行号」（FML 总线 / 实例处理器），逻辑未动。 |
| `pojo/animation/bedrock/AnimationKeyframes.java`、`pojo/display/gun/AdsCurve.java` | 去掉「机械替换时误写成…」这类过程叙述，保留 fastutil→`TreeMap`、gson 2.2.4 无 `SerializedName#alternate` 这类**仍生效的平台依据**。 |

---

## 2. 逐文件对照表

图例：**等价** = 逐行/语义与上游一致（差异仅注释、import、格式化、`net.minecraft.resources`→`net.minecraft.util` 之类）；
**降级** = 平台能力缺失的有据降级（已在代码注释与 §3.2 记录）；**修复** = 本轮改动。

### 2.1 `client/model/bedrock/**`（geo 解析与几何求值）

| 文件 | 结论 | 说明 / 依据 |
|---|---|---|
| `BedrockModel.java` | 等价 | `loadNewModel`/`loadLegacyModel` 骨骼两遍遍历、`parent` 绑定、`shouldRender`、`_illuminated` 后缀、`mirror` 全部一致；`convertPivot`（有父：xz=本-父，y=父-本；无父：y=24-本）、`convertOrigin`（xz=origin-pivot，y=pivot-origin-size）、`convertRotation`（度→弧度）与上游 `BedrockModel.java:292-348` 逐式相同。`Vec2→org.joml.Vector2f`、`Vec3→net.minecraft.util.Vec3` 仅类型替换（`getOffset()/getSize()` 全仓无消费者，上游同样无）。 |
| `BedrockPart.java` | 等价 | 变换顺序 `translate(offset) → translate(x/16,y/16,z/16) → rotZ → rotY → rotX → additionalQuaternion → scale`（`BedrockPart.java:90-104` ↔ 上游 `:90-104`）；`Axis.ZP.rotation(r)` → `new Quaternionf().rotationZ(r)` 为同一实现。 |
| `BedrockCubeBox.java` | 等价 | 6 面顶点、`delta`（inflate）膨胀、`mirror` 交换 `x/xEnd`、`p1..p9` UV 展开、`dx/dy/dz = (int)` 取整全部一致。 |
| `BedrockCubePerFace.java` | 等价 | per-face `uv/uv_size`、`FaceItem.EMPTY` → 共享退化顶点（上游自身行为，未改）、`mirror=false` 传入（上游同样忽略 per-face 的 mirror）。 |
| `BedrockPolygon.java` | 等价 | UV remap（`u2/W, v1/H` … 与上游同式）、`mirror` 时顶点倒序 + `normal.mul(-1,1,1)`；`Direction.step()` → `ForgeDirection.offsetX/Y/Z`。 |
| `BedrockVertex.java`、`BedrockCube.java`、`ModelRendererWrapper.java` | 等价 | — |
| `UnshadedVertexConsumer.java` | 降级 | 上游从 `RenderSystem#shaderLightDirections` 取主光方向并把法线缩放到饱和；1.7.10 是固定管线无 shader，降级为固定 `(0,1,0)`，亮度由 `LightTexture.pack(15,15)` 提供（注释已述）。 |

### 2.2 `client/model/` 顶层

| 文件 | 结论 | 说明 |
|---|---|---|
| `BedrockAnimatedModel.java` | 等价 + 清理 | `camera`/`constraint`/`root`/`idle_view` 路径、`cleanAnimationTransform`、`setFunctionalRenderer`、`supplyListeners` 分派与上游一致；本轮删除死代码。 |
| `BedrockGunModel.java` | 等价 + 清理 | 功能渲染器注册表（muzzle/bullet_in_*/mount/carry/sight*/mag*/handguard*/additional_magazine/adapter）与上游一一对应；`scopePosPath` 先渲染 + stencil 函数、`renderMount`/`currentExtendMagLevel`/`adapterToRender` 逻辑一致；`isEmpty(stack)` 替代 `ItemStack#isEmpty()`（含 null 容忍）。ARCompat 加速路径有据降级为 `renderUnaccelerated`。 |
| `BedrockAmmoModel.java`、`DisplayGunModelInstance.java`、`ExternalGunModel.java`、`SlotModel.java`、`GunModelConstant.java`、`IFunctionalRenderer.java`、`FunctionalBedrockPart.java` | 等价 | `DisplayGunModelInstance` 的骨骼显隐/挂点索引解析逐行一致；`ExternalGunModel` 的 `CubesItem` 序列化桥（`uv`/`face_uv`/`size`/`origin`/`rotation`/`pivot`/`inflate`/`mirror`）与上游一致。 |
| `functional/AttachmentRender.java` | 等价 + 降级 | 变体 replace/overlay、slot adapter、`mountOffset`（`offset/16`，y 取负）、LOD（`lodModel != null && !inRenderHighPolyModelDistance && !firstPerson`）、`entityCutout`/`entityTranslucent` 选择全部一致；缺贴图 fallback 用方块图集（上游 `MissingTextureAtlasSprite` 无对应常量）。 |
| `functional/MuzzleFlashRender.java` | 等价 + 降级 | `TIME_RANGE=50ms`、`scale*0.5` 与前半段渐入、两段（背景 + 发光）几何/位置/旋转一致；第一人称 `isSelf`、第三人称按实体计时（`TP_SHOOT_TIMESTAMPS`/`currentEntity`）、消音器抑制、`isRenew()` 抑制全部一致。发光层 `energySwirl → entityTranslucent`（见 §3.2）。 |
| `functional/ShellRender.java` | 等价 | 队列上限 128、`lifeTime`、匀变速位移、`XN/YN/ZP` 角速度旋转、`translate(0,-1.5,0)`、`GunTime.epoch()` 失效判定、`data.normal==null && data.pose==null` 复检（上游自身按 `&&` 写，未改）全部一致。 |
| `functional/BeamRenderer.java` | 修复（见 §1.2） | 顶点 16 个/4 面、UV、`endAlpha`、`FULL_BRIGHT`、`LASER_BEAM_TEXTURE` 一致。 |
| `functional/TextShowRender.java` | 等价 + 降级 | `scale(2/300*scale, -2/300*scale, -2/300)`、`align` 偏移、`shadow`、`color`、`textLight` 一致；`Font#drawInBatch`（逐顶点矩阵 + buffer）→ `pushToGL + drawString`（见 §3.2）。 |
| `functional/LeftHandRender.java`、`RightHandRender.java` | 等价 + 注释修正 | `Axis.ZP.rotationDegrees(180)` → `rotationZ((float) Math.PI)`；法线/位姿快照 → `delegateRender` → 新 `PoseStack` 复制矩阵 → `renderFirstPersonArm` 一致。 |
| `listener/camera/*`、`listener/constraint/*`、`listener/model/*` | 等价 | `ModelTranslateListener` 的 `y` 取反与 `initialValue()` 的 `/16` 恢复、`ConstraintTranslate/RotateListener` 的 `abs(*16)`/`toDegrees`、`ModelRotateListener` 的 `MathUtil.toQuaternion` 全部与上游同式。 |
| `papi/*` | 等价 | `PapiManager`（`${...}` 解析 + `StatCollector.translateToLocal` = 上游 `I18n.language.getOrDefault`）、`AmmoCountPapi`（背包直读走 `ClientAmmoCount`）、`PlayerNamePapi`、`RangefinderPapi` 一致。 |

### 2.3 `client/animation/**`（排除 `third/**`）

| 文件 | 结论 | 说明 |
|---|---|---|
| `statemachine/GunAnimationStateContext.java` | 等价 + null 守卫 | 全部 getter（`hasBulletInBarrel`/`getShootInterval`/`getAmmoCount`/`getMagExtentLevel`/`getAimingProgress`/`getReloadStateType`/`getCharge*`/`popShellFrom`/`getStateMachineParams`/`getAttachment`…）与上游同式；平台替换：`cameraEntity→renderViewEntity`、`onGround()→public 字段`、`isCrouching()→isSneaking()`、`walkDist/walkDistO→distanceWalkedModified/prev…`、`ForgeCapabilities.ITEM_HANDLER→IInventory`。**输入语义已核 1.7.10 源码**：`MovementInputFromOptions` 里「移动键左 → moveStrafe++（:33-36）、右 → moveStrafe--（:38-41）」（`build/rfg/minecraft-src/java/net/minecraft/util/MovementInputFromOptions.java`），故 `isInputLeft = moveStrafe > 0` / `isInputRight = moveStrafe < 0` 与 1.20 `input.left/right` 等价。 |
| `statemachine/ItemAnimationStateContext.java`、`ThrowableAnimationStateContext.java`、`GunAnimationConstant.java` | 等价 | `getStackCount()` 以 `stackSize` + 判空替代 `getCount()`（1.7.10 无 `EMPTY` 哨兵）。 |
| `screen/RefitTransform.java` | 等价 | 插值公式（`REFIT_SCREEN_TRANSFORM_TIMES=0.25s`、opening/transform 两条 progress）与上游一致；注册方式平台改写（注释已压）。 |

### 2.4 `client/resource/pojo/model/**`

| 文件 | 结论 | 说明 |
|---|---|---|
| `BedrockModelPOJO.java`、`Description.java`、`GeometryModelNew.java`、`GeometryModelLegacy.java`、`BedrockVersion.java`、`BonesItem.java`、`FaceItem.java` | 等价 | `deco()`（legacy 兼容处理）、`visible_bounds_*` 默认值、`format_version` 判定（`1.8.0`/`1.12.0` 比较逻辑）一致。 |
| `CubesItem.java` | 等价 | 自定义 `Deserializer`：`uv` 数组 / `uv` 对象（→`FaceUVsItem`）/ `mirror`（`hasMirror` 标记）/ `inflate` clamp 与上游一致；`size` 允许浮点。 |
| `FaceUVsItem.java` | 等价 | 六面映射 `Direction→ForgeDirection`，`getFace` 默认 `FaceItem.EMPTY`。 |

### 2.5 `client/resource/pojo/animation/**`

| 文件 | 结论 | 说明 |
|---|---|---|
| `bedrock/AnimationKeyframes.java`、`SoundEffectKeyframes.java`、`ParticleEffectKeyframes.java` | 等价（类型替换） | fastutil `Double2ObjectRBTreeMap` → `TreeMap<Double, …>`：两者都是**按键升序**遍历，`Animations.java` 里 `for (Map.Entry<Double, …> entry : keyframesMap.entrySet())` 语义不变；`put(0.0, …)` 与上游 `put(0, …)` 同一 key。 |
| `bedrock/BedrockAnimation.java`、`AnimationBone.java`、`BedrockAnimationFile.java` | 等价 | `loop`/`animation_length`/`position`/`rotation`/`scale`/`sound_effects`/`particle_effects` 字段名一致（`hold_on_last_frame` 上游本就没有）。 |
| `gltf/**`（18 个） | 等价 | 仅格式化差异（`Accessor`/`AccessorSampler` 的合法性断言文本、`BufferView`、`Node` 等逐行同义）。 |

### 2.6 `client/resource/pojo/display/**`（排除 Rangefinder/TextShow/ScopeLensConfig）

| 文件 | 结论 | 说明 |
|---|---|---|
| `IDisplay.java` | 等价 | `idToFile` = 上游 `FileToIdConverter("textures",".png")#idToFile`（`prefix + "/" + path + ext`）；`PackDirectory` 为纯数据载体。 |
| `gun/GunDisplay.java`、`gun/GunTransform.java`、`gun/AdsFov.java`、`gun/AdsPath.java`、`gun/AdsPreset.java`、`gun/Align.java`、`gun/LayerGunShow.java`、`gun/GunLod.java`、`gun/MuzzleFlash.java`、`gun/ShellEjection.java`、`gun/GunAmmo.java`、`gun/AmmoCountStyle.java`、`gun/DamageStyle.java`、`gun/DefaultAnimationType.java`、`gun/ControllableData.java` | 等价 | 默认值全部一致（`LayerGunShow` pos(-2,20,4)/rotate(0,0,-30)/scale(0.6)、`MuzzleFlash.DEFAULT_*_SMOKE`、`GunLod` 等）；`init()` 的贴图 id→路径转换集合一致。 |
| `gun/AdsCurve.java` | 等价（实现手段替换） | `start=0,end=1,clamp=true`、`isValid()` 与 26 种缓动公式逐式一致；`ease` 由枚举改为字符串 + 手工解析（gson 2.2.4 无 `alternate`），snake/camel 两种写法均可，非法名视为 invalid（上游是整份 display 反序列化失败）。 |
| `ammo/AmmoDisplay.java`、`ammo/AmmoEntityDisplay.java`、`ammo/AmmoTransform.java`、`ammo/ShellDisplay.java`、`ammo/HitSurfaceParticle.java` | 等价 | `init()` 贴图转换一致。 |
| `ammo/AmmoParticle.java` | 降级 | `ParticleOptions` → `String`（1.7.10 无粒子选项对象）；与 `AmmoParticleSpawner.java:70-72`、`ClientAmmoIndex.java:248`、`GunDisplayInstance.java:726` 的字符串解析闭环一致。 |
| `attachment/AttachmentDisplay.java`、`attachment/AttachmentLod.java` | 等价 | `init()` + `lod` 字段一致。 |
| `block/BlockDisplay.java` | 等价（类型替换） | `transforms` 用 `BlockItemTransforms`（1.7.10 无 `ItemTransforms` 包）。 |
| `block/BlockItemTransforms.java` | **修复**（见 §1.1） | 平移 ÷16 + clamp ±5、scale clamp ±4 正确；旋转合成顺序本轮修正。 |
| `LaserConfig.java`、`SoundData.java` | 等价 | `defaultColor = Color.decode(color).getRGB()`；`resolveVolume/Pitch` 的 `(rand*2-1)*range`、`volume/pitch` null 回退、`*_random_range < 0` 归零均一致。 |

### 2.7 排除项（本轮不动，供交叉参考）

`client/model/RenderHelperCompat.java`（r7-fp）、`client/model/BedrockAttachmentModel.java`（r7-scope）、
`client/model/functional/RangefinderImpactPredictionRender.java`（r7-crosshair）、`client/animation/third/**`（r7-tp）、
`pojo/display/gun/{Rangefinder,RangefinderManager,TextShow}.java`（r7-crosshair）、
`pojo/display/attachment/ScopeLensConfig.java`（r7-scope）。

---

## 3. 专项复核

### 3.1 「漏 ÷16 / 漏 clamp」全量扫描（r6 教训的同类问题）

作用域内所有单位换算调用点（`Select-String '0\.0625|/ *16|\* *16|Mth\.clamp'`）逐个与上游对照，结论：**均已一致**。

| 位置 | 换算 | 与上游 |
|---|---|---|
| `BedrockCubeBox/PerFace.compile` | `vertex.pos /16` → 格 | 一致（上游同式） |
| `BedrockPart.translateAndRotateAndScale` | `x/16, y/16, z/16` | 一致 |
| `AttachmentRender.applyMountOffset` | `(x/16, -y/16, z/16)` | 一致（注意 y 取负） |
| `ConstraintTranslateListener.update` | `abs(values*16)` → 约束阈值用 px | 一致 |
| `ModelTranslateListener.initialValue` / `ConstraintTranslateListener.initialValue` | `pivot/16`（y 取负） | 一致 |
| `BlockItemTransforms` | `translation*1/16` clamp ±5、`scale` clamp ±4 | 一致（r6 平移 + 本轮旋转） |
| `ads*`（`AdsCurve`） | `clamp(t,0,1)`、圆弧类恒 clamp | 一致 |

> 备注：`TransformScale`/`GunTransform`/`AmmoTransform`/`LayerGunShow` 的数值是**块单位**（0.6/1.2/0.75/1.5 等），
> 上游同样不做 ÷16；消费点 `GunItemTransforms`（r7-fp）与 `HumanoidOffhandRender`（r7-tp）已各自做 `/16`，与上游同式。

### 3.2 有据降级清单（平台能力缺失，不做第三种语义）

| # | 项 | 上游 1.20.1 | 本工程 | 影响 |
|---|---|---|---|---|
| 1 | 模板缓冲（镜内不画枪体） | 主 framebuffer 有 stencil，`stencilFunc(GL_GREATER,127)` 生效 | 1.7.10 默认 `stencilBits=0`（`ForgeHooksClient.java:317-341`、`Framebuffer.java:117-130`），stencil 调用为 no-op | 组合镜镜内仍可见枪体；`-Dforge.forceDisplayStencil=true` 时部分恢复。**保持 r6 结论，未重开** |
| 2 | ARCompat 加速渲染 | `BedrockGunModel#renderAccelerated`、`BeamRenderer#renderLaserBeamAccelerated` 走 AcceleratedRendering 层缓冲 | 方法保留 → 直接回退到非加速路径 / 返回 `false` | 视觉一致，性能路径缺失 |
| 3 | 枪口火光发光层 | `RenderType.energySwirl`（`ADDITIVE_TRANSPARENCY` + NV 滚动的 UV + bump） | `RenderType.entityTranslucent` | 发光层偏暗、无流动；几何/位置/缩放/旋转一致 |
| 4 | `UnshadedVertexConsumer` | 读 `shaderLightDirections` 造无方向光法线 | 固定 `(0,1,0)` + 满亮度 lightmap | 无光照部分仍有微弱的固定管线方向光分量 |
| 5 | `TextShowRender` | `Font#drawInBatch(...)`：逐顶点矩阵 + 逐顶点 lightmap | `pushToGL` + `FontRenderer#drawString` | lightmap 变全局坐标；坐标为整数像素（浮点偏移用 `glTranslatef` 保留） |
| 6 | 缺贴图 fallback | `MissingTextureAtlasSprite.getLocation()`（黑紫棋盘） | 方块图集贴图 | 仅资源缺失时出现，目的是「画一块显眼的东西」 |
| 7 | 未知缓动名 | gson 枚举反序列化失败 ⇒ 该 display 文件加载失败 | 视为 invalid + 线性兜底（不崩） | 更宽容；`evaluate()` 不会 NPE |

### 3.3 上游自身行为（已确认「不修」）

- `ShellRender.renderSingleShell` 的 `data.normal == null && data.pose == null` 判定（应为 `||`）——上游同式。
- `BedrockCubePerFace` 忽略 `mirror`（`new BedrockPolygon(..., false, direction)`）——上游同式。
- `BedrockModel.getOffset()/getSize()`（visible_bounds）无消费者——上游同式。

---

## 4. 跨模块发现（不在本作用域，建议对应 owner 处理）

1. **`mcshim/RenderType` 缺 `energySwirl`**（属 r7-platform / task-24）：即使补一个 `ADDITIVE_TRANSPARENCY` 的
   RenderType，也无法还原滚动的 UV 与 bump（固定管线无 shader），建议**保持降级**并在 platform 报告登记。
   `MuzzleFlashRender.java:155-159` 已带 TODO(port) 说明。
2. **`client/renderer/item/BlockModelGuiItemRenderer`（r7-block）**：它把原版 block model 的 `display.gui`
   反序列化成 `BlockItemTransforms.ItemTransform`（`:288`），本轮旋转顺序修复会让**原版模型来源**的 GUI 朝向
   一起变得更准；若 r7-block 正在比对 GUI 图标角度，请以此为准。
3. **`client/renderer/item/GunSmithTableItemRenderer`（r7-block）**：`transform.apply(false, poseStack)` 的
   `false`（右手语义）与上游一致，**不要**改成 `leftHand` 判定以外的第三种写法。
4. **`client/resource/ClientAssetsManager`（r7-respack-client）**：若日后把 gson 升到 ≥2.4（本机 gradle cache 里
   同时存在 2.2.4 / 2.8.0 / 2.11+），`AdsCurve.ease` 可以改回枚举 + `@SerializedName(alternate=…)`；当前字符串方案
   在任何 gson 版本下都能工作，无需改。
5. **`AmmoParticle.particleOptions` 的 String 化**：依赖 `AmmoParticleSpawner`/`ClientAmmoIndex`/`GunDisplayInstance`
   三处的字符串解析闭环（r7-gun / r7-respack-client 域），已核对当前闭环一致；请勿单边改回对象。
6. **`animation/third/**`（r7-tp）** 与 `AnimationRenderContext`：`RightHandRender`/`LeftHandRender` 的手臂矩阵依赖
   `RenderHelperCompat#renderFirstPersonArm`（r7-fp）中的作用域处理，本轮未动。

---

## 5. 实机验证清单

> 静态可证的部分已在 §1/§3 给出依据；下面 8 条是**必须进游戏**才能定论的观感/数值项，附判读方法。

1. **方块物品朝向（本轮 §1.1 的主验证点）**：创造栏 OTHER 页签 / 手持 / 地面丢出
   `gun_smith_table`、`ammo_workbench`、`attachment_workbench`。
   - GUI 里模型应「略微俯视 + 侧转 145°/225°」，与上游截图一致；若出现「侧转方向反了」或「前倾/后仰互换」，
     说明旋转合成仍有偏差（修复前正是这个症状，幅度约 10°~30°）。
   - 判读捷径：`gui.rotation` 有 z 分量或 x、y 同时非零的物品最容易暴露；单轴旋转（`target`/`statue` 的
     `item/generated` 2D 图标）不受影响。
2. **镭射（激光）**：装激光配件后观察枪身上的红色光柱——不应被枪体吞掉/闪烁（layer 已改为上游的
   modelview 缩放），末端按 `ENABLE_LASER_FADE_OUT` 淡出；第一人称与第三人称右手都应可见（其它手持不渲染，与上游一致）。
3. **枪口火光**：非 `renew` 枪械开火 50ms 内，枪口出现「半透明底 + 发光芯」两层；发光芯比 1.20 略暗属已知降级（§3.2 #3）。
   装消音器后**不应**出现火光。
4. **抛壳**：开火后弹壳从 `shell`/`shell_1` 挂点弹出、做匀变速位移 + 自转，`shell_ejection.living_time` 后消失；
   检视/换弹不抛壳（`popShellFrom` 只在脚本调用时触发）。
5. **骨骼动画**：开火后坐、换弹、检视、走/跑 idle 切换——重点看**平移通道方向**（`ModelTranslateListener` 的 y 取负）
   与**过渡停帧**（`initialValue()` 的 `/16` 恢复是否让骨骼平滑回位，而不是瞬移）。
6. **文字显示（`TextShow`，如弹药计数 / 测距）**：文字位置随 `align`（左/中/右）、`scale` 缩放；
   `text_light` 非 15 时应明显变暗（lightmap 现在是全局设置，仍在 `try/finally` 里复位）。
7. **第一人称手臂**：左右皮肤手臂仍应显示（本轮删的是死代码，注册路径未变）；若手臂缺失，问题在
   `RenderHelperCompat#renderFirstPersonArm`（r7-fp）而非本模块。
8. **组合镜镜内枪体**：默认启动参数下**仍会看到枪体**（§3.2 #1，预期行为）；用
   `-Dforge.forceDisplayStencil=true` 启动后应部分恢复「镜内干净」，可用于确认 stencil 假设。

---

## 6. 改动文件清单

| 文件 | 类型 |
|---|---|
| `src/main/java/com/tacz/guns/client/resource/pojo/display/block/BlockItemTransforms.java` | 逻辑修复（旋转合成 + import） |
| `src/main/java/com/tacz/guns/client/model/functional/BeamRenderer.java` | 逻辑修复（layer 状态等价化：blendFuncSeparate + modelview scale） |
| `src/main/java/com/tacz/guns/client/model/BedrockAnimatedModel.java` | 删除死代码（无行为变化） |
| `src/main/java/com/tacz/guns/client/model/BedrockGunModel.java` | 注释压缩（无行为变化） |
| `src/main/java/com/tacz/guns/client/model/functional/LeftHandRender.java` | 注释修正 |
| `src/main/java/com/tacz/guns/client/model/functional/RightHandRender.java` | 注释修正 |
| `src/main/java/com/tacz/guns/client/animation/screen/RefitTransform.java` | 注释压缩 |
| `src/main/java/com/tacz/guns/client/resource/pojo/animation/bedrock/AnimationKeyframes.java` | 注释 |
| `src/main/java/com/tacz/guns/client/resource/pojo/display/gun/AdsCurve.java` | 注释 |
| `docs/review/r7/model.md` | 本报告 |

未触碰：`tmp/**`、`build/**`、`run/**`、`src/main/resources/**`、以及其他人作用域内的任何文件。
