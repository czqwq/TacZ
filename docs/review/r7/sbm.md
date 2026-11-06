# r7-sbm · vendored SBM / MAE 库 1.20 → 1.7.10 改造 等价性审查

任务：task-16。作用域：`src/main/java/com/github/mcmodderanchor/**`（SBM，277 个 java 文件）、
`src/main/java/com/maydaymemory/**`（MAE，42 个 java 文件）、本报告。

**未运行任何 gradle 任务**。改动 3 个文件，`mcp__idea__lint_files(min_severity="error")` = **0 error**；
作用域内 `[TACZ-DBG]` 残留 **0**（唯一 1 处已在本次清理，见 §1.3）。

**上游准绳**：SBM `tmp/libs-src/simplebedrockmodel/com/github/mcmodderanchor/simplebedrockmodel/**`（343 个 java，
含 `example/`）、MAE `tmp/sbm-port/mae/src/com/maydaymemory/mae/**`、
1.20.1 原版 `tmp/minecraft-src-1.20.1/src/main/java/**`、1.7.10 原版/Forge `build/rfg/minecraft-src/java/**`。
（`tmp/sbm-port/` 下除 `mae/` 外的移植期脚本/json 未参与比对。）

**方法**：① 逐文件 `git diff --no-index` 取**去注释 + 去全部空白**后的语句级差异（消除 spotless 重排噪音）；
② 对「除 import / `@OnlyIn` 之外仍有差异」的 56 个文件逐块人工判定；
③ 对 `com.tacz` 实际引用的 35 处 SBM import + 2 处 MAE import（14 个调用方文件）逐符号核对；
④ 几何/动画/粒子/渲染路径再读源码逐行确认，必要时回 1.20 原版核算法。

---

## 0. 结论速览

| 项 | 结论 |
|---|---|
| 规模对账 | 上游 SBM 343 个类 → vendored **277**；**72 个上游类未 vendor**（全是可选兼容/示例/资源管线/盔甲/mixin 入口，逐条见 §4.1），**6 个新增**（`port/{AABB,Camera,SoundSource,Vec3}`、`serialize/ResourceLocationSerializer`、`v2/client/mesh/gpu/VertexBuffer`）。 |
| 改造形态 | **绝大多数是机械替换**：271 个同名文件中 **142 个只差 import / `@OnlyIn` 删除**（另 73 个连 import 都相同），仅 **56 个**有实质差异；其中 **18 个属于 TACZ 完全不用的 `v2/client/**`（世界静态网格批渲染，全仓仅注释提及）**。 |
| 语义结论 | 被 `com.tacz` 引用的 **16 个 SBM 类（35 处 import）+ 2 个 MAE 类型**全部**语义等价**（§2），差异要么是平台 API 同名替换，要么是有注释/TODO(L2) 记录的降级。 |
| 真实偏离 | **发现并修复 1 处真 bug**：`SnowStormParticle#move` 碰撞位移被**重复应用**（X/Y 双倍、Z 漏用，§1.1）。另修 2 处：`FirstPersonRenderHandler` 早退分支漏 `renderParticlesIfAny`（回到上游行为，§1.2）、每帧诊断计数器改为仅在 `DEBUG_RENDER` 下执行。 |
| 有据降级 | 12 项，全部有 `TODO(L2)` + 依据行号（§4.2）。上游库自身缺陷（`System.err.println` 调试输出等）**按要求不修**（§4.3）。 |
| 结构偏离（记录） | vendored 库反向依赖 `com.tacz.guns.mcshim.*` / `ScopeLensRenderer` / `MuzzleParticleManager`（§3.3）——这是本移植选定的「库→垫片」方向，非语义问题，但属于架构事实，需要 Lead 知晓。 |
| 跨模块发现 | 3 项，均**不影响玩法**，不需要 `com.tacz` 侧改动（§5）。 |

---

## 1. 修复项

### 1.1 `SnowStormParticle#move` 碰撞位移重复应用（真实偏离，P1）

文件：`src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/world/SnowStormParticle.java:353-414`

**上游语义**（`tmp/libs-src/.../v1/particle/world/SnowStormParticle.java:255-308`）：
`move(x,y,z)` 用 `Entity.collideBoundingBox(...)` 做**纯扫掠**（不改包围盒），得到 `collided`，
再把 `x/y/z` 覆盖成 `collided.x/y/z`，最后**统一调用一次**
`setBoundingBox(getBoundingBox().move(x,y,z))` ⇒ 每轴位移只应用**一次**。

**移植侧原实现**：把扫掠改写成 1.7.10 `Entity#moveEntity` 风格的
「`calculateYOffset/X/ZOffset` 解算 + `offsetAabb` 应用」——**但保留了上游末尾那次统一应用**：

```
offsetAabb(0, y, 0)          // Y 已应用
offsetAabb(x, 0, 0)          // X 已应用
（Z 只解算未应用）
setAabb(getOffsetBoundingBox(x, y, z))   // ← 再应用一次 x、y，并首次应用 z
```

净效果：**X、Y 位移各 2 倍、Z 位移 1 倍**（开阔地带 `getCollidingBoundingBoxes` 返回空 ⇒ 三轴都走满，
X/Y 一定双倍）。该粒子是第一人称枪口粒子被「投递到世界」后的载体（`FirstPersonParticleSystem#deliverWorldSpaceParticle`
→ `WorldEmitterManager#deliverParticle`），表现为世界里的枪口烟/火星**横竖飘得比 Z 方向快一倍**。

**修法**（保持 1.7.10 `moveEntity` 的 Y→X→Z 顺序，去掉重复应用）：

- 新增 `syncPosFromAabb()`（与 1.20 `setLocationFromBoundingbox()` 等价，`:228`），`setAabb` 复用它；
- 三轴各解算并 `offsetAabb` 应用一次（含此前**漏掉的 `offsetAabb(0,0,z)`**，`:388`）；
- 去掉末尾那次 `setAabb(getOffsetBoundingBox(x,y,z))`，改为统一 `syncPosFromAabb()`；
- 速度超限（`velSqr >= 10000`）不走碰撞解算的分支：整段位移**一次**应用（与原上游一致）。

判据不变：`origX != x` 等仍表示「该轴被裁剪」，反弹/`fireCollisionEvents`/`onGround` 语义未动。

### 1.2 `FirstPersonRenderHandler` 早退分支漏画粒子（回到上游行为，P2）

文件：`.../v1/client/handler/FirstPersonRenderHandler.java:493-533`

上游 `onRenderHand(RenderHandEvent)` 在 **5 条早退分支**上都先 `renderParticlesIfAny(event)` 再 `return`
（`tmp/libs-src/.../v1/client/handler/FirstPersonRenderHandler.java:236/242/246/252/257`）。
移植侧把这 5 处写成只有 `bufferSource.endBatch()`，并留了一句「故意不调，否则双画」的注释 ——
该理由不成立：早退后事件末尾那次调用**根本不会执行**。后果是
「发射器仍存活但 instance/renderer 已消失」的帧（收枪过渡、切枪、副手被霸占）里第一人称粒子整帧不画。

已按上游逐条补回 `renderParticlesIfAny(hand, poseStack, bufferSource, packedLight, event.partialTicks)`
（5 处，`:498/506/513/520/529`），并改掉误导性注释。

### 1.3 每帧诊断计数器移出发布路径

同文件 `:369-372`：原本无条件每帧执行 `RenderHelperCompat.beginDebugFrame()`（`[TACZ-DBG]` 帧号推进 +
清空帧内计数器）。改为 `if (RenderHelperCompat.DEBUG_RENDER)` 保护 ⇒ 发布版零开销，打开诊断开关时
`countInFrame` 语义不变。作用域内 `[TACZ-DBG]` 至此为 0。

### 1.4 文档纠正（无语义改动）

`port/AABB.java:14` 的 javadoc 把 1.20↔1.7.10 方法名映射写反/写错（原写 `expand → inflate`、
`calculateXOffset → distanceToSqr`），改为 `inflate → expand`、`move → getOffsetBoundingBox`、
`contains → isVecInside`。

---

## 2. `com.tacz` 实际引用符号逐类核对（35 处 SBM import + 2 处 MAE import）

`grep 'import com.github.mcmodderanchor'` = 35 处 / `import com.maydaymemory` = 2 处，分布在 14 个调用方文件。

| # | 被引用类（本仓 file:line 为首个调用方） | 上游对照结论 |
|---|---|---|
| 1 | `port.Vec3`（`client/particle/ThirdPersonMuzzleParticleManager.java:17`） | **新增**1.7.10 适配类；`Vec3.java:20-127` 逐字复刻 1.20 `Vec3`（不可变 + `x()/y()/z()` + 同名运算；`normalize()` 的 `1.0E-4` 零向量保护与 1.20 一致）。等价。 |
| 2 | `v1.particle.data.ParticleEffectDefinition` | 仅 import 差异（通配→逐个）。等价。 |
| 3 | `v1.particle.resource.ParticleDefinitionLoader` | `ResourceManager` 改 mcshim；`listResources` 的路径过滤由「`.json` 后缀」改为 `nameOf(loc)`（等价判定）+ 新增 `applyPrepared` 供 1.7.10 重载线程复用。等价。 |
| 4 | `v1.particle.runtime.ParticleEmitterInstance` | 仅 import 差异。等价（粒子发射/生命周期/molang 绑定全部逐行相同）。 |
| 5 | `v1.particle.world.WorldEmitterManager` | 1.7.10 `EffectRenderer` 无法逐粒子换纹理 ⇒ 新增 `spawnedParticles` 列表 + `renderWorldParticles()` + `RenderWorldLastEvent` 自绘（`:79-100`）；粒子仍 `addEffect` 进 `EffectRenderer` 以被 tick。见 §3.1 论证。等价（有据降级）。 |
| 6 | `v1.common.resource.pojo.ParticleEffectData`（8 处引用，含 `api/client/animation/**`） | 仅 import 差异（`ResourceLocation` 包名）。等价。 |
| 7 | `v1.particle.firstperson.FirstPersonParticleSystem` | `Minecraft#level/particleEngine` → `theWorld/effectRenderer`、`Camera` 改 `port.Camera`。等价。 |
| 8 | `v1.particle.render.CameraStateCache` | 上游两个 Forge 事件采样被替换为显式 `setCameraRoll()`（由 `com.tacz` 的 `CameraSetupEvent:170/175/211/214/499` 写入）；`handFov/worldFov` 恒 70 ⇒ `getFirstPersonToWorldDepthScale()==1`，与上游「handFov==worldFov」一致。降级有 `TODO(L2)` 记录。等价。 |
| 9 | `v1.client.handler.FirstPersonRenderHandler` | 差异最大（252 行）。逐块核对后：GL 归位/手部投影/lightmap/hurt 补偿/`BeforeRenderHandEvent`/镜片/overlay 均为 **1.7.10 平台补偿**（有依据行号注释，见 §3.2）；真实偏离仅 §1.2/§1.3 两处，已修。 |
| 10 | `v1.client.renderer.IFPGeoItemRenderer` | `LocalPlayer`→`EntityPlayerSP`、`ViewportEvent` 形参去掉、`ItemStack.isSameItem` 手写（null-safe，1.7.10 空栈是 `null` 不是 `EMPTY`）。等价。 |
| 11 | `v2.common.model.baked.BakedBedrockModel` | 参数签名逐个相同，`@OnlyIn` 删除，`AABB`/`PoseStack`/`VertexConsumer`/`RenderType`/`MultiBufferSource` 走 port/mcshim。等价。 |
| 12 | `v2.common.model.runtime.BakedModelInstance` | 同上（`renderToBuffer`/`renderSingleBone*` 全签名一致）。等价。 |
| 13 | `v2.common.model.runtime.BoneState` | `PoseStack` 换 mcshim；`translateAndRotateAndScale` 的矩阵读取顺序 `last().pose()/normal()` 未变。等价。 |
| 14 | `v1.common.resource.GsonUtil` | 唯一实质改动：`new ResourceLocation.Serializer()` → 新增 `ResourceLocationSerializer`（序列化 `"ns:path"`、反序列化走 1.7.10 构造；仅少 1.20 的字符校验，已注释）。三个 Gson 实例的 adapter 组合与上游逐项相同。等价。 |
| 15 | `v2.common.model.baked.BakerOptions` | record 字段顺序/默认值/`ARMOR_BONES`/`collectAnimatedBones` 完全相同。等价。 |
| 16 | `v1.client.animation.IFPAnimationInstance` | 仅 import 差异。等价。 |
| 17 | MAE `basic.Pose` / `basic.DummyPose`（`AnimateGeoItemRenderer.java:27-28`） | 见 §6。等价。 |

**间接依赖但 TACZ 必经的 SBM 内部链路**（无直接 import，由上表 16 个类驱动，已逐个核对）：

| 链路 | 文件 | 结论 |
|---|---|---|
| 模型解析/烘焙 | `BedrockModelBaker`(36 行差异)/`BedrockGeometryBaker`(13)/`TreeBedrockModelBaker`(6)/`TreeBedrockModel`(18)/`TreeGeometryWriter`(8) | 差异全在 `AABB`/`EnumFacing`/`Arrays`/`indices.size()==0` 等价替换；`BedrockGeometryBaker` 新增 `FACE_ORDER={DOWN,UP,NORTH,SOUTH,WEST,EAST}`，与 1.20 `Direction.values()` 及 1.7.10 `EnumFacing.values()` **同序**（后者未改动，仍 `EnumFacing.values()`）。**几何语义不变**。 |
| 烘焙结果绘制 | `BakedGeometryChunkRenderer`(26)/`BakedGeometryChunk`(7)/`BakedModelInstance`/`TreeModelInstance` | 顶点 API 由「一次性 `vertex(...)` 传 13 参」改成 mcshim 链式；`cull` 判据 `RenderSystem.getModelViewMatrix().m32()==0` 用 `modelViewTranslationZ()`（列主序 index 14 = m32）精确等价；Sodium/加速渲染快路径删除（1.7.10 无）。等价。 |
| 动画 | `BedrockAnimation`(0 实质)/`MolangVector3fKeyframe`/`MolangRotationKeyframe`/`AnimationClock(s)`/`PausedClientAnimationClock` | 除 `DistExecutor`→`FMLCommonHandler.getSide()` 与 `Minecraft#isPaused()`→`isGamePaused()` 外全同。 |
| molang 运行时 | `MochaEngine(Impl)`/`MolangParser(Impl)`/`ExpressionInterpreter`/`MolangCompilingVisitor`/`IsConstantExpression`/`value.*`/`AsmUtil`/`MochaMath` | **除 1 处必须改的字节码版本外全同**：`MolangCompiler:192-196` 把 `Opcodes.V17` → `Opcodes.V1_8`（否则 Java 8 上加载 ASM 生成的表达式类会 `UnsupportedClassVersionError`），已带 TODO(L2) 依据。`AsmUtil` 仅把 FQN `org.objectweb.asm.Label` 改 import。 |
| molang 查询 | `MolangEntityContext`(56) | 逐查询核对；4 个查询降级为常量 0（游泳/静默/无重力/发光，1.7.10 无对应状态），其余 `onGround→字段`、`isAlive→isEntityAlive`、`tickCount→ticksExisted`、`getDayTime→getWorldTime()%24000`、`getGameTime→getTotalWorldTime`、`isDay/isNight→isDaytime()`、moonPhase/raining/thundering 全部有据。 |
| 粒子 | `ParticleEmitterInstance`/`EventExecutor`/`ParticleRenderer`/`BillboardHelper`/`MolangWorldParticleRenderType`/`ParticleRenderType`/`SnowStormParticle` | 除 `RenderType` 组合式构造改「State 结构体 + mcshim 立即模式」、`playLocalSound` 降级、`energySwirl` 降级、Sodium/ARCompat 快路径删除外全同；`SnowStormParticle` 见 §1.1/§3.1。 |
| 渲染类型 | `BedrockModelRenderTypes`(21) | `RENDERTYPE_ENTITY_CUTOUT` 语义（alpha test、无混合、无剔除、lightmap、写深度）在 mcshim State 里逐项映射，等价。 |

---

## 3. `port/` 与平台改造层：是否悄悄换语义

### 3.1 逐类结论

| 类 | 改造 | 语义等价性 |
|---|---|---|
| `port.Vec3` | 新增不可变 Vec3 | 逐字复刻 1.20，**等价**（含 `normalize` 零向量保护）。 |
| `port.AABB` | 新增不可变 AABB（+`toAxisAlignedBB()`） | 构造 `min/max` 归一、`inflate/move/getCenter/getSize/contains/intersects/distanceToSqr` 与 1.20 逐个相同，**等价**。 |
| `port.Camera` | 新增「按需读 `renderViewEntity`」适配器 | 关键点 `rotation()`= `rotationYXZ(-yaw, pitch, 0)`，与 1.20 `Camera#setup`（`tmp/minecraft-src-1.20.1/.../Camera.java:106`）**逐参相同**；`getPosition()` 取眼位插值（第三人不含 1.20 的第三人位移补偿，已注释）。**等价（第一人称）/有据降级（第三人称）**。 |
| `port.SoundSource` | 保留常量名、无用途 | 1.7.10 无声音分类，**等价降级**（`EventExecutor` 只用名字）。 |
| `WorldEmitterManager` 世界粒子自绘 | `spawnedParticles` + `RenderWorldLastEvent` + 相机相对坐标 | 1.7.10 `EffectRenderer#renderParticles` 只遍历图层 **0..2**（`build/rfg/.../EffectRenderer.java:127`），本类粒子 `getFXLayer()==3` ⇒ 原版不画、不被双画；`addEffect` 仍让它被 `updateEffects` tick（同文件 `:47/67` 遍历 4 层）。**语义等价**（渲染时机从 `ParticleEngine` 改为 `RenderWorldLastEvent`，见 §7 实机项）。 |
| `ParticleRenderType` / `MolangWorldParticleRenderType` / `BedrockModelRenderTypes` | 1.20 `CompositeState.builder()` → mcshim `RenderType.State` | 逐字段核对（blend 因子、depthWrite、cull、alphaTest、lightmap、additive）与上游各 `material` 分支一一对应；`Util.memoize` 换成 `ConcurrentHashMap.computeIfAbsent`（键含纹理，等价）。**等价**。 |
| `BedrockModelBaker#bounds` | `net.minecraft.world.phys.AABB` → `port.AABB` | 构造参数顺序与半宽/半高换算一致。**等价**。 |
| `TreeGeometryWriter` / `ISodiumVertexWriter` | Sodium 快路径返回 `false` / 抛 `UnsupportedOperationException` | 1.7.10 无 Sodium；`ISodiumVertexWriter` 已无调用点（全仓 grep 仅定义处），**等价降级**。 |

### 3.2 明确记录的平台补偿（都在注释里给了依据行号）

`FirstPersonRenderHandler#onRenderHand` 相对上游多出的 5 个动作，均为「1.7.10 的对应 vanilla 逻辑在
`EntityRenderer#renderHand` 里、而本模组必须 `cancel` 该事件」所导致，属**必然后果**而非新语义：

| 补偿 | 依据（1.7.10 `build/rfg/minecraft-src/java/net/minecraft/client/renderer/EntityRenderer.java`） |
|---|---|
| `pushHandProjection()`（`gluPerspective(ITEM_MODEL_FOV)`，`finally` 成对 pop） | `:755-770` |
| `glClear(GL_DEPTH_BUFFER_BIT)` | `:1436` |
| `enableLightmap/disableLightmap`（成对，`finally` 兜底） | `:796/:798`、`:819-824` |
| `compensateHurtCameraEffect()`（`RenderItemInHandBobEvent.BobHurt` + `GunHurtBobTweak` + vanilla hurt 公式） | `:787`、`:507-528` |
| `compensateBeforeRenderHandEvent()` / `compensateScopeLenses()` / `compensateVanillaOverlays()`（顺序：手 → 镜片 → overlay） | `:797`、`:805` |

另：vanilla 的两处守卫（`:794` 的 `thirdPersonView/hideGUI/isPlayerSleeping` 三个条件）移植侧逐条复刻；
第四个条件 `playerController.enableEverythingIsScrewedUpMode()` 在 1.7.10 恒为 `false`
（`PlayerControllerMP.java:95-98` 直接 `return false`），故无需分支。

### 3.3 结构事实：vendored 库反向依赖模组代码（记录，不改）

vendored SBM 现在 import `com.tacz.guns.mcshim.*`（PoseStack/VertexConsumer/RenderType/MultiBufferSource/
VertexFormat/DefaultVertexFormat/GsonHelper/ResourceManager/SimplePreparableReloadListener/ProfilerFiller/
LightTexture/Mth/OverlayTexture/SoundEvent/InteractionHand/ItemDisplayContext/RenderSystem/BufferBuilder），
以及 2 处**具体模组类**：

- `v1/client/handler/FirstPersonRenderHandler.java:47-49,569` → `ScopeLensRenderer.render()`；
- 同文件 `:49,106` → `MuzzleParticleManager.reset()`。

即「库」不再是可独立复用的库。这是本移植「vendor + 垫片」决策的既定形态（`AGENTS.md` 决策 3），
语义上等价（镜片 pass 上游确实在 `renderHand` 之后、由 TACZ 的 `GameRendererMixin` 触发），但**属于架构偏离**，
记录给 Lead；若要恢复「库不依赖模组」，需把这 2 处改成库内回调接口（不建议在收尾阶段动）。

---

## 4. 未改动 / 有据降级 / 未 vendor 范围

### 4.1 未 vendor 的 72 个上游类（全部与 TACZ 无关或已被 TACZ 侧重写）

| 上游包 | 数量 | 未 vendor 内容与处置 |
|---|---|---|
| `v1/client/compat/**` + `v2/client/compat/**` | 14 | Accelerated Rendering(7) / Sodium(5) / EpicFight(2)。1.7.10 无这些库（`AGENTS.md` 决策 2：可选兼容只在 TACZ 自己的 `compat` 里用反射骨架）。 |
| `v1/client/{model,renderer}/**` + `ClientSetupEvent` | 12 | `BedrockModelBase`/`EntityModel`/`HandedBedrockModel`/`PositionableModel`/`BedrockArmorModel`/`SlotModel`/`TransformScale`、`AbstractGeoItemRenderer`/`GeoArmorRenderer`/`ICustomArmorRenderer`/`IFPArmorHandRenderer`。由 `com.tacz.guns.client.model.*` + `AnimateGeoItemRenderer` 重写承接（r7-model/r7-scope 作用域）。 |
| `v1/client/event/**` + `handler/{CameraEventHandler,FirstPersonArmorHandler}` | 6 | 事件改由 `com.tacz.guns.api.client.event.*` 提供；注入点改由 `com.tacz.guns.mixin.*` 承担。 |
| `v1/common/animation/**` + `v1/common/model/**` + `MolangLivingContext` | 11 | `SimpleAnimationState`/`SimpleTransition`/`AnimationRateLimiter`、`BedrockModel`/`BedrockCubeBox`/`BedrockCubePerFace`/`BedrockPolyMesh`(+Sodium 3 变体)。几何树由 `com.tacz.guns.client.model.bedrock.*` **同名重写**；动画状态机由 TACZ 自有实现承接。 |
| `v1/event/**` + `v1/resource/**` + `v1/mixin/**` + `v2/event/**` | 16 | SBM 的注册事件 / 资源重载监听 / 5 个 mixin。TACZ 有自研资源管线（r7-respack-*）与自己的 mixin 集（r7-platform）。 |
| `v2/client/{command,mesh,renderer}` + `v2/common/model/runtime/{BedrockArmorInstance,TreeArmorModelInstance}` | 7 | v2 世界网格命令/盔甲实例；TACZ 不使用（`v2/client/mesh` 只保留被引用的运行时类）。 |
| `v1/particle/debug/**` + `v1/util/{RenderDistance,RenderHelper}` + 2 个 `package-info` | 6 | 调试命令/调试渲染、距离与渲染工具（TACZ 侧有 `com.tacz.guns.util.RenderDistance` 等同名实现）。 |

**核验方式**：把 72 个未 vendor 类的简单类名全仓 grep（`BedrockModelBase|HandedBedrockModel|AbstractGeoItemRenderer|
GeoArmorRenderer|ICustomArmorRenderer|IFPArmorHandRenderer|SimpleAnimationState|SimpleTransition|AnimationRateLimiter|
MolangLivingContext|SodiumCompat|PositionableModel|CameraEventHandler|FirstPersonArmorHandler|ReloadListenersRegister|
RawResourceLoaders|BedrockModelResource*|BedrockAnimationResource*|WorldMeshCommands|AcceleratedRendering*|
AcceleratedBedrockBoneCache|MeshLifecycle|ParticleDebug*` …）：**只命中注释里的 TODO 说明**，无任何存活引用。
少数同名命中是 `com.tacz` **自己的同名类**（`com.tacz.guns.client.model.bedrock.{BedrockCubeBox,BedrockCubePerFace,BedrockModel}`、
`com.tacz.guns.client.resource.pojo.TransformScale`、`com.tacz.guns.util.RenderDistance`），
即上游 SBM 的 v1 几何树/距离工具已由 TACZ 侧同名重写承接，不会出现 `NoClassDefFoundError` 式悬空依赖。

### 4.2 有据降级（12 项，均有 `TODO(L2)` + 依据）

1. `MolangEntityContext`：`is_swimming`/`is_silent`/`is_no_gravity`/`is_glowing` 恒 0（1.7.10 无该状态）。
2. `MolangEntityContext#is_in_water_or_rain`：用「在水中 或 露天且下雨」近似（无气泡柱）。
3. `MolangCompiler`：ASM 生成类版本 `V17 → V1_8`（Java 8 运行期必需）。
4. `CameraStateCache`：无相机 roll 事件、无独立手部 FOV ⇒ 由外部写入 roll、深度比例恒 1。
5. `port.Camera`：无真正的 `Camera` 快照；第三人称的相机位移补偿未移植。
6. `SnowStormParticle`：`TextureSheetParticle`→`EntityFX`；碰撞走 1.7.10 `moveEntity` 算法；
   渲染移到 `WorldEmitterManager`（原因见 §3.1）；Sodium 快路径删除。**另注**：1.7.10 的
   `World#getCollidingBoundingBoxes` 会连**实体**包围盒一起返回，而上游 `Entity.collideBoundingBox(null,…)`
   只碰方块 ⇒ 世界粒子可能被附近实体弹开。差异极小（仅 `ParticleMotionCollision` 开启时可见），
   **未改**：改成「只碰方块」需要另写一套方块扫掠，收益不抵风险，此处登记。
7. `ParticleRenderer`：`material == ENERGY_SWIRL` 从 `RenderType.energySwirl` 降级为 additive 粒子
   （无 shader/滚动 UV）。
8. `EventExecutor`：`playLocalSound(..., SoundSource.AMBIENT, …)` → `World#playSound(..., distanceDelay=false)`（无分类）。
9. `MolangWorldParticleRenderType`：`ParticleRenderType.begin/end` → mcshim `RenderType`（blend/depth 逐项映射）。
10. `v1/network/**`（`NetworkHandler` + `ServerMessageSwapItem`）：1.7.10 无副手 ⇒ 注册/收发全空实现，
    `handle` 保留「post `SwapItemWithOffHand`」。**与上游一致地属于死路径**：上游 TACZ 也只在 SBM 自己的
    网络包里 post SBM 的该事件，TACZ 用的是 `com.tacz.guns.api.client.event.SwapItemWithOffHand`。
11. `BedrockVersion`：Maven `VersionRange` → FML `cpw.mods.fml.common.versioning.VersionRange`（同源实现，
    `[1.10.0]`/`[1.12.0,)` 语法一致）。已用 IDEA 校验该 API 可解析。
12. `v2/client/mesh/**` + `v2/client/config/WorldMeshConfig`（世界静态网格批渲染，**TACZ 完全不用**）：
    `RenderSystem`/`VertexBuffer`/`VertexFormat` 全走 mcshim，`MeshRenderDebug` 在发布版直接抛异常
    （上游 `FMLEnvironment.production` 时同样禁 instrumentation），`MeshDrawTransform` 多了一次防御性拷贝。
    该子系统 18 个文件未逐行深核 —— **不需要**：无任何 `com.tacz` 代码引用（只在
    `client/renderer/block/DisplayGun{Geometry,MeshGroup}.java` 的注释里被提到「1.7.10 降级为立即绘制」），
    且不影响枪械。

### 4.3 上游库自身问题（按要求**不修**）

- `MolangCompilingVisitor:439`、`ExpressionInterpreter:343` 的 `System.err.println` 调试输出：
  **上游同位置就有**（`tmp/libs-src/.../MolangCompilingVisitor.java:385`、`.../ExpressionInterpreter.java:305`），
  属上游行为，保持原样（若 Lead 要「发布版不刷 stderr」，属产品决策，不在等价性审查范围）。
- `MAE`：`tmp/sbm-port/mae/src` 本身就是 1.1.4 的**反编译产物**（含 `var5` 风格临时变量与
  `$values()` 合成方法），本仓只做重排版，未「修」任何上游逻辑。

---

## 5. 跨模块发现（不需要 `com.tacz` 侧改动，仅备案）

1. `SnowStormParticle#getFXLayer()` 返回 **3**（自定义层）——`com.tacz` 侧不要依赖
   `EffectRenderer.renderParticles` 会画这些粒子；世界粒子由 SBM 自己在 `RenderWorldLastEvent` 画。
   若后续 `com.tacz` 新增自己的世界粒子渲染，注意**不要**再画一遍（避免双画）。
2. `FirstPersonRenderHandler#registerRenderer(Item, IFPGeoItemRenderer)` 的静态表已由
   `com.tacz.guns.client.init.ClientSetupEvent:279` 正确填充（另有一条 `item instanceof IFPGeoItemRenderer`
   的快路径），无需额外注册。
3. `port.SoundSource` / `v1.network.NetworkHandler` / `ISodiumVertexWriter` 在 1.7.10 是**无行为占位**；
   若未来有代码「发 SBM 网络包」或「调 Sodium writer」，会静默无效/抛异常——目前全仓无调用点。

---

## 6. MAE（`com/maydaymemory/mae/**`，42 文件）对照

| 项 | 结论 |
|---|---|
| 差异性质 | 去空白后 **42/42 文件的差异 = 每文件 5 行 vendored 头注释 + 重排版**；唯一实质性代码差异 1 处：`BasicAnimation#clip` 增加 `(Iterable<Keyframe<T>>) (Iterable<?>)` 强制转换（反编译泛型擦除的修补，运行时无操作）。 |
| 语义核对 | `Pose`/`PoseBuilder`/`DummyPose`/`ArrayPose(Builder)`/`LinkedListPose(Builder)`/`Animation`/`BasicAnimation`/`AnimationChannel`/`ArrayAnimationChannelBase`/`InterpolatableChannel`/`ClipChannel`/`Keyframe`/`BaseKeyframe`/`InterpolatableKeyframe`/`RotationKeyframe`/`Vector3fKeyframe`/`DynamicVector3fKeyframe`/`StringKeyFrame`/`BoneTransform`/`BoneTransformFactory`(YXZ/ZYX)/`Rotation`/`RotationView`(YXZ/ZYX)/`RotationVelocityRotationView`/`Interpolator`/`Vector3fLinearInterpolator`/`Vector3fCubicSplineInterpolator`/`EulerAnglesRotationInterpolator`/`QuaternionRotationInterpolator`/`Skeleton`/`IEvaluationContext`/`util.{DirtyTrackingArrayList,MathUtil,FloatSupplier,LongSupplier}` —— **逐字形到义全同**（关键帧二分查找边界、`cubicSpline` 系数、YXZ/ZYX 欧拉合成顺序、四元数插值归一化、`MathUtil.toNanos/toSecond` 时间单位全部未动）。 |
| TACZ 用量 | 仅 `basic.Pose` / `basic.DummyPose` 被 `com.tacz` 直接引用（`AnimateGeoItemRenderer`）；`BasicAnimation`/通道/插值器只被 SBM 的 `BedrockAnimation` 使用（TACZ 用自研动画系统）。 |
| 未 vendor 的 MAE 包 | 上游 MAE 共 117 个类，本仓 vendor **42**（`basic/**` 39 + `util/{DirtyTrackingArrayList,MathUtil,FloatSupplier,LongSupplier}` 4），未 vendor **75**：`blend/**`(22)、`control/**`(42)、`util/{Easing,Iterables,MergedSortedIterable,triangulation/**}`(11)。全仓 grep 确认**零引用**（唯一引用它们的是未 vendor 的 `control/statemachine` 与 `example/`）；TACZ 自己的状态机是 `com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine`（自有实现，r7-api/r7-model 范围）。 |
| 有据降级 | `control`/`blend` 缺失 ⇒ 「LayerBlend / BlendSpace / Montage / 动画 runner」这几套 MAE 能力在 1.7.10 侧**不可用**。这与上游 TACZ 一致（上游 TACZ 也不用它们），故**不算移植偏离**，但要在文档里记住：想让 TACZ 用上 MAE 的 blend 能力需先补 vendor。 |

---

## 7. 实机验证清单（静态无法定论）

| # | 项 | 判读方法 |
|---|---|---|
| V-1 | **§1.1 世界粒子位移修正** | 对着墙连发，看**枪口烟在世界里的漂移速度**：修正后 X/Y/Z 三轴速度应与粒子定义一致（此前 X/Y 快一倍）。可对比「朝正上方开枪」与「朝正前方开枪」的烟团扩散半径——两者应接近。 |
| V-2 | **§1.2 早退分支粒子** | 开火后**立刻**切枪/收枪（`R` 装填后马上切到空手）：枪口烟应继续燃烧到生命周期结束（此前会瞬间消失）。 |
| V-3 | **§1.3 诊断开关** | 默认不出现任何 `[TACZ-DBG]`；把 `RenderHelperCompat.DEBUG_RENDER` 置 `true` 后，`[TACZ-DBG] equipped-call` 之类的「本帧第 N 次」计数仍应从 1 开始（证明 `beginDebugFrame` 语义未破坏）。 |
| V-4 | **世界粒子渲染时机（§3.1）** | 第一人称开火产生「跨帧世界粒子」时，粒子应贴着世界位置（不随视角抖动/不飘到相机空间）；转视角时粒子不应跟着屏幕转。若出现「粒子跟着准星走」，说明 `RenderWorldLastEvent` 的相机相对坐标假设不成立，需改挂在 `RenderWorldEvent` 之后并显式 `glLoadIdentity`。 |
| V-5 | **首帧/极端值** | 断线重连（`ClientDisconnectionFromServerEvent` 清空世界发射器 + `spawnedParticles`）后进新世界开火：不应残留旧粒子、不应 NPE。 |
| V-6 | **模型版本解析（§3.2-11）** | 用默认枪包（`format_version: 1.12.0`）+ 任一 legacy 包（`1.10.0`）各加载一次：两者都应能出模型；控制台不应出现 `Failed to parse version spec`。 |
| V-7 | **molang 表达式编译（§3.2-3）** | 带 molang 的粒子/动画（默认枪包枪口火焰即含 molang）在 Java 8 运行：不应出现 `UnsupportedClassVersionError`/`ClassFormatError`。若出现，说明还有别处生成 V17 字节码。 |

---

## 8. 本次改动文件

| 文件 | 改动 |
|---|---|
| `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/world/SnowStormParticle.java` | §1.1 碰撞位移只应用一次 + 抽出 `syncPosFromAabb`（真实 bug 修复） |
| `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java` | §1.2 早退分支补 `renderParticlesIfAny`（5 处，回到上游）；§1.3 诊断帧号只在 `DEBUG_RENDER` 下推进 |
| `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/port/AABB.java` | §1.4 javadoc 方法名映射纠正（无语义改动） |

3 个文件 `mcp__idea__lint_files(min_severity="error")` 均 **0 error**；另抽检 12 个关键 vendored 文件（含
`BedrockVersion` 的 FML `VersionRange`、`port/*`、`MolangCompiler`、`BakedGeometryChunkRenderer`）同样 0 error。
**未运行 gradle**；未修改 `tmp/**`、`build/**`、`run/**`、`src/main/resources/**`。
