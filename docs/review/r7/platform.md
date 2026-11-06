# r7-platform · mcshim / mixin / client-init 平台垫片语义等价性审查（task-24）

> 写作用域：`src/main/java/com/tacz/guns/mcshim/**`、`src/main/java/com/tacz/guns/mixin/**`（不含 r7-fp / r7-tp /
> r7-input / r7-scope 的既有文件）、`src/main/java/com/tacz/guns/client/init/**`、本报告。
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/**`；1.20.1 原版：`tmp/minecraft-src-1.20.1/src/main/java/**`
> （含 Forge patch：`tmp/MinecraftForge-1.20.1/patches/**`）；1.7.10 原版/Forge：`build/rfg/minecraft-src/java/**`。
> 自查：只用 IDEA MCP（`read_file`/`search_*`/`grep`/`lint_files`）。**未运行任何 gradle 任务**（含 compileJava）。
> 改动文件共 12 个（含 `mixins.tacz.json`，Lead 明确转派），全部 `lint_files(min_severity=error)` = **0 error**。

---

## 1. 结论速览

| 维度 | 结论 |
|---|---|
| **mcshim 39 类** | 逐类对照 1.20.1 原版实现。**发现并修复 3 处真实语义偏离**：① `RenderType#equals/hashCode` 把「同名不同纹理/状态」合并成同一批次（1.20 不覆写 equals，按实例身份）；② `PoseStack#scale` 的法线矩阵缺 1.20 的「等比缩放不变 + `fastInvCubeRoot` 归一」；③ `PoseStack#clear()` 被写成「真弹栈」，1.20 是纯查询（`size()==1`）。另补齐 1.20 API `PoseStack#setIdentity()`、`Mth#fastInvCubeRoot`。 |
| **RenderType 分批 bug 的实际影响** | 修复前 `EmissiveMeshRenderTypes`（SBM v2 自发光网格，常量名 `sbm_emissive_cutout`）、`MolangWorldParticleRenderType`（常量名 `molang_world_particle_<material>`）、`DisplayGunRenderType`（常量名 `tacz_display_gun_emissive`）等**多纹理共用同一 name** 的 RenderType 会被 `MultiBufferSource.BufferSource` 的 `HashMap` 合并成一批，`endBatch()` 只绑第一个纹理 ⇒ 第二张以后的自发光网格/世界粒子被画成第一张贴图。**已在 mcshim 根因修复**（r7-sbm/r7-block 不必改自己的类，详见 §5）。 |
| **mixin 注册** | `mixins.tacz.json` 现注册 **20** 个/仓内共 **22** 个 `@Mixin` 类；未注册的 2 个是**有意禁用**的空 mixin（`LanguageMixin` 上游即注释死代码、`SoundManagerPreparationsMixin` 的等价实现已在 `client/sound/GunSoundInstance#resolve`，见 §4.2）。refmap `mixins.tacz.refmap.json` 由构建生成（`build/resources/main/mixins.tacz.refmap.json` 存在）；**新增 4 个 mixin 后必须重新构建生成新 refmap**（当前 build 产物是旧的）。 |
| **client/init** | 5 个类与上游 `client/init/**` 注册面逐一对应：物品渲染器 4 类 + 3 个工作台变体、实体渲染器 2、TESR 5（与上游 `ModEntitiesRender` **完全一致**）、HUD 5 层、键位/着色器/粒子/容器界面/染色各有等价落点。**修掉 2 个 HUD 事件映射 bug（r7-crosshair F1/F2）**；登记 1 处上游注册点无 1.7.10 等价物导致的图标降级（ammo box 变体，属 r7-item/r7-gui）。 |
| **本轮 Lead 转派落地** | ① HUD F1/F2（已修）；② 弹药盒「物品栏右键点进/点出」`Container#slotClick` mixin（已按最终契约落地）；③ 趴下 AABB/eyeHeight/相机（服务端+客户端 mixin 落地，见 §3.1）；④ 非玩家实体跨维度刷新 mixin（落地，附 1.7.10 语义注记）；⑤ `energySwirl` 保持降级并登记；⑥ `ServerGamePacketListenerImplMixin` 差集 = 上游 `ServerPlayNetHandlerMixin`（双持，1.7.10 无副手 ⇒ 有据降级，登记）；⑦ `Mth.fastInvSqrt` 裁决 = 保留精确实现（§3.9）；⑧ `RenderDistance` 用 GL_MODELVIEW 代替 PoseStack → 登记不改。 |
| **未修（有据降级）** | energySwirl（shader UV 滚动）、shader color 对逐顶点色几何无染色（调用点已逐点适配）、`MultiBufferSource` 的 A→B→A 交错绘制顺序与 1.20 不同、`RenderType.lines()` 线宽 2.0、非玩家实体 crawl 体型、非玩家跨维度实例重建、无副手 swap-item、`LightTexture` 1.7.10 打包布局等（§6）。 |

---

## 2. mcshim 逐类对照表（39 类）

> 判定：**等价** = 与 1.20 实现逐行同构/语义等价；**降级** = 平台能力缺失，有实现或记录替代；**修复** = 本轮改过。
> 1.20 原版类路径均在 `tmp/minecraft-src-1.20.1/src/main/java/**`（下文省略前缀）。

| shim | 1.20 对照 | 1.7.10 实现 | 判定 |
|---|---|---|---|
| `PoseStack` | `com/mojang/blaze3d/vertex/PoseStack` | JOML 右乘矩阵栈 + 法线矩阵栈；`applyToModelView`=`glMultMatrix`（后乘语义与 1.20 的 CPU 顶点变换等价）；`pushToGL/popFromGL` 用 GL 矩阵栈 | **修复**：`scale()` 改成 1.20 逐行同构（等比缩放不动法线、非等比用 `fastInvCubeRoot` 归一）；`clear()` 改回纯查询；补 `setIdentity()`。矩阵序/组合顺序与 1.20 全一致（`translate/scale/mulPose/mulPoseMatrix/rotateAround` 逐一核对） |
| `RenderSystem` | `com/mojang/blaze3d/systems/RenderSystem` | 直通门面：颜色/混合/深度/裁剪/stencil/纹理单元/矩阵读取 | **等价（含降级）**：`blendFuncSeparate`→`OpenGlHelper.glBlendFunc`、`defaultBlendFunc`=`SRC_ALPHA/ONE_MINUS_SRC_ALPHA/ONE/ZERO`（与 1.20 同）、`enableScissor` 直接原始像素（`GuiGraphics` 负责换算）；无 shader/线程/no-op 方法（r5 已证**0 外部调用者**）。`getModelViewMatrix/getProjectionMatrix` 直读 GL 矩阵（SBM v2 world mesh 在 `RenderWorldLastEvent` 用，此刻 GL_MODELVIEW 即视图矩阵 ✓）；`setShaderColor` 在逐顶点色几何上无 tint 效果 = **登记降级**（见 §3.7） |
| `RenderType` | `client/renderer/RenderType` + `RenderStateShard` | 「GL 状态包 + 顶点格式」；`setupRenderState/clearRenderState` 用 GL 状态快照做严格括号还原 | **修复**：删除 `equals/hashCode` 覆写（1.20 不覆写 ⇒ 按实例身份；这是多纹理批次合并 bug 的根因）；`clearRenderState` 快照还原（r2 已修）；`State.text` 重复赋值清理；补 `energySwirl` 降级登记。状态映射见 §3.2 |
| `RenderType.State` | `CompositeState` | 字段化状态包（alpha/cull/depth/blend/lighting/polygonOffset/lineWidth/lightmap） | **降级（有据）**：无 shader/FBO/面排序；`mask()` 未实现 colorMask（`waterMask` 0 调用点）；cull 族恒 false 是 r2 已登记的安全超集 |
| `BufferBuilder` | `blaze3d/vertex/BufferBuilder` | Java float[] 攒顶点，`end()` 出 `RenderedBuffer` 快照；属性「保留最后一次设置值」 | **等价（降级）**：无 VBO/native/索引；overlay 丢弃（1.7.10 顶点格式无槽位）；`end()` 空/未 building 返回 null 与 1.20 一致 |
| `BufferBuilder.RenderedBuffer` | 同名内部类 | 数组快照 + `release()` no-op | **等价（降级）**：无 native 内存可释放 |
| `BufferUploader` | `blaze3d/vertex/BufferUploader` | `Tessellator.startDrawing` → 逐顶点 `setTextureUV/setColorRGBA/setBrightness/setNormal/addVertex` → `draw()` | **等价（降级）**：shader→固定管线；overlay 丢弃；颜色/亮度/法线按 `format.has*()` 与 1.20 的顶点格式一一映射（`uv2` 打包与 `setBrightness` 布局匹配） |
| `MultiBufferSource`/`BufferSource` | `client/renderer/MultiBufferSource` | 按 RenderType **实例**各攒一个 builder，`endBatch()` 按「首次 getBuffer 顺序」依次 `setup→draw→clear` | **等价（降级）**：顺序 = 类型首次出现顺序（1.20 是「类型切换时 flush」，A→B→A 交错时顺序不同，已登记）；`immediateWithBuffers` 忽略预置 buffer（0 调用者）；`endLastBatch/endBatch(type)` 等价 |
| `VertexConsumer` | `blaze3d/vertex/VertexConsumer` | 保留链式 API + `defaultColor/defaultNormal/unsetDefaultColor` + `overlay(int)/lightmap(int)` 打包重载 | **等价**（`overlay` 打包公式与 1.20 逐字一致） |
| `TessellatorVertexConsumer` | 1.20 无（`BufferBuilder` 承担） | 立即写 Tessellator；`endVertex` 才 `addVertex`（避免属性错位）；整缓冲属性集锁定 | **等价（降级）**：overlay 丢弃；属性缺省值取 1.20 语义（uv=(0,0)、白、FULL_BRIGHT） |
| `BufferBuilderVertexConsumer` | 同上 | 纯转发 | **等价** |
| `Tesselator` | `blaze3d/vertex/Tesselator` | 单例包 1 个 `BufferBuilder` | **降级（无外部调用者）** |
| `VertexFormat`/`DefaultVertexFormat` | 同名 | 描述性元素清单 + `Mode.glMode()/indexCount()`；`NEW_ENTITY` 元素序 = POSITION,COLOR,UV0,UV2,UV1,NORMAL（与 1.20 一致，1.20 多一个 PADDING 无影响） | **等价**（1.7.10 顶点布局固定 8 槽，描述对象只影响 shim 内部取值） |
| `LightTexture` | `client/renderer/LightTexture` | 静态 `pack/block/sky/u/v`，布局 `(sky<<20)|(block<<4)`（1.7.10 原生，可直接喂 `Tessellator#setBrightness`）；`FULL_BRIGHT=0xF000F0` | **等价（有据降级）**：布局与 clamp 0..15 已记录；无实例类/lightmap 纹理/flicker |
| `Mth` | `net/minecraft/util/Mth` | 委托 `MathHelper` + 补缺（lerp/frac/sign/atan2/hsvToRgb/rotLerp/lerp2/lerp3…） | **修复**：补 `fastInvCubeRoot`（1.20 逐行）、`nextInt` 的 `min>=max` 守卫；`floor/ceil` 保留 1.7.10 语义（注释已写）；`fastInvSqrt` 裁决见 §3.9 |
| `Axis` | `com/mojang/math/Axis`（1.20.1 是**接口+lambda**） | 枚举 + `new Quaternionf().rotationAxis(rad, x,y,z)` | **等价**：`rotationAxis(θ,-1,0,0) ≡ rotationX(-θ)`（四元数公式逐项验算）；上游只用到 `XP/XN/.../ZN.rotation(r)` 与 `rotationDegrees` |
| `ItemDisplayContext` | `world/item/ItemDisplayContext` | 9 常量，序与 1.20 完全一致（网络序列化依赖 ordinal）；`firstPerson/thirdPerson/leftHand` | **等价** |
| `EntityPose` | `world/entity/Pose` | 枚举（`CROUCHING` 真名 + `SNEAKING` 别名） | **等价（记录）**：`SNEAKING` 与 `CROUCHING` 非同值，注释已警告；由 `EntityStateHelper` 推导 |
| `EntityStateHelper` | `Entity#getPose` 逻辑 | 离散布尔推导 + `ENTITY_POSE_OVERRIDES`（WeakHashMap 强制姿势钩子） | **等价（移植特有）**：本轮起客户端 crawl 会真正写入该表（§3.1），`MouseHandlerMixin`/`CameraSetupEvent` 两个消费者随之生效 |
| `HumanoidArm`/`InteractionHand` | 同名 | 枚举 + 转换 | **等价（降级）**：OFF_HAND 1.7.10 无（文档已写） |
| `OverlayTexture` | 同名 | `NO_OVERLAY=0` 常量 | **降级（有据）**：1.7.10 无 overlay 通道 |
| `Lighting` | `client/renderer/Lighting` | `RenderHelper` 直通；`setupForEntityInInventory` 退化为标准光照 | **等价（降级）**：GUI 光照角度略不同（已记录） |
| `GlStateManager` | `platform/GlStateManager` | 直通（无状态缓存）+ `SourceFactor/DestFactor` 枚举 | **等价（降级）**：少缓存、少部分 GL30 封装（调用方直接用 LWJGL） |
| `GuiGraphics` | `client/gui/GuiGraphics` | fill/fillGradient/blit/blitNineSliced/enableScissor/drawString/renderItem/renderItemDecorations/renderTooltip/drawHoveringText；矩阵用「per-draw glPushMatrix + glMultMatrix(pose)」桥接；`setColor` 手动乘进顶点色 | **等价（降级）**：`setColor`≡shader tint 的替代（文字不受影响）；10 参 `blitNineSliced` 降级为整图拉伸；`tooltipImage` 忽略；`bufferSource()/flush()/drawManaged` 空实现（1.20 批次概念不存在）；tooltip 手绘算法照抄 `GuiScreen#drawHoveringText` |
| `ProfilerFiller` | `util/profiling/ProfilerFiller` | 包 1.7.10 `Profiler` | **等价**（section 名 30 字符截断已记录） |
| `GsonHelper` | `util/GsonHelper` | 整数移植（gson 2.2.4 API 适配） | **等价（记录）**：`fromJson/parse` 用旧 API；异常类型一致 |
| `PacketBuffer` | `network/FriendlyByteBuf` | extends 1.7.10 `PacketBuffer`，补 1.20 方法名；线上格式 = 1.7.10（NBT gzip+short、ItemStack itemId/count/damage） | **等价（有据降级）**：两侧都是本模组 ⇒ 自洽；`readBlockPos→int[3]`、`readEnum` 越界更宽容（已记录，r7-network 复核） |
| `SoundEvent` | `sounds/SoundEvent` | `ResourceLocation` 薄包装 + `getName()` | **等价（降级）**：无 Registry/Holder，1.7.10 按名字播 |
| `InputConstants` | `platform/InputConstants` | LWJGL2 `Keyboard/Mouse` + GLFW 键码数值等价 | **等价（降级）**：SCANCODE 兜底；显示名反查 try/catch |
| `Resource`/`ResourceManager`/`FileToIdConverter`/`PackSource` | `server/packs/resources/*` | 自建枪包资源索引（`PackSource` 注入、`data/<ns>/...` → id 规则与 1.20 一致） | **等价（有据降级）**：无 PackType/mcmeta/优先级栈（点击本节 r7-respack-core 报告的逐条核对；本类只做接口语义抽核） |
| `PreparableReloadListener`/`SimplePreparableReloadListener` | `server/packs/resources/*` | 同步模板方法（prepare→barrier→apply），`PreparationBarrier.DIRECT` | **等价（降级）**：无后台线程/依赖排序（注释已写） |
| `EntityRenderers`/`BlockEntityRenderers` | `client/renderer/*` | 转接登记表（`register/registerAll`），**0 外部调用者** | **no-op（无功能损失）**：真实注册走 `client/init/ModEntitiesRender`（§4.1） |
| `AnimationPose`/`DummyPose` | `com.maydaymemory.mae.basic.Pose/DummyPose` | 骨名→Matrix4f 容器 / 不可变空单例 | **等价**（只用到读取/容器语义） |

> **覆盖说明（诚实）**：39 类全部读过源码并对照；其中 `PacketBuffer`/`ResourceManager`/`FileToIdConverter`/`Resource`/
> `GsonHelper` 的**内部逐方法数值语义**以 r7-network / r7-respack-core 的专门审查为准（本报告只核对 shim 接口/平台映射，
> 未重复逐行比对 JSON/网络细节）。

---

## 3. 本轮修复与裁决（逐条）

### 3.1 趴下的体型 / 眼高 / 相机（Lead 转派 C-3，P2）——已实现

上游 1.20：`Player#setForcedPose(Pose.SWIMMING)`（Forge patch `Player.java.patch`）→
`updatePlayerPose → setPose → onSyncedDataUpdated(DATA_POSE) → refreshDimensions`；
`Player.POSES` 的 SWIMMING = **0.6×0.6**、`getStandingEyeHeight(SWIMMING)` = **0.4**（站立 0.6×1.8 / 1.62）。

1.7.10 的等价落点拆成三段（本仓 mixin scope）：

| 部分 | 落地 | 依据 |
|---|---|---|
| **服务端碰撞箱/眼高** | `mixin/common/LivingEntityMixin#tacz$updateCrawlDimensions`：在既有 `onLivingUpdate` RETURN（`onTickServerSide`）末尾，状态变化时写 `player.height=0.6 / eyeHeight=0.4`（退出还原 1.8/1.62）并同步 `boundingBox.maxY` | 1.7.10 `Entity#setPosition`：box = `[posY-yOffset+ySize, +height]`（`Entity.java:352`）；服务端玩家 `yOffset=0` ⇒ 底边=posY，只缩不抬高，等价 1.20「对 Player 不做 findFreePosition」。`getEyeHeight()` 返回 `eyeHeight` 字段（`EntityPlayer.java:1087`），服务端语义 = 相对 posY（脚部） |
| **客户端碰撞箱/姿势** | `mixin/client/PlayerCrawlPoseMixin`（`@Mixin(EntityPlayer)`，client 列表）：`onLivingUpdate` RETURN 读 `IClientPlayerGunOperator#isCrawl()`；写 `EntityStateHelper.ENTITY_POSE_OVERRIDES`（SWIMMING）+ 客户端 `height/boundingBox` | 客户端玩家 `yOffset=1.62` ⇒ box 底 = posY-1.62（脚），只改 height 同样只缩不抬；`ENTITY_POSE_OVERRIDES` 是仓内既有钩子（`MouseHandlerMixin` 的俯仰限制、`CameraSetupEvent` 的趴下后坐力系数都读它，此前**无人写入 ⇒ 两处恒不生效**，本轮一并接通） |
| **客户端相机眼高** | `mixin/client/EntityRendererCrawlCameraMixin`（`@Mixin(EntityRenderer)`）：`@ModifyVariable(method="orientCamera", at=@At("STORE"), ordinal=0)` 把 `f1 = yOffset-1.62` 加 `1.62-0.4` | 1.7.10 相机高度 = boxBottom + 1.62 **写死**（`EntityRenderer.java:555/645`，见下注）；f1 是唯一局部量，改动只影响本帧、无需还原；第一/第三人称同源 |

> **为什么不能靠改 `yOffset` 降相机**：`Entity#setPosition` 的包围盒锚点也用 `yOffset`（`Entity.java:352`），
> 改 yOffset 会同时移动 posY 与包围盒，相机相对包围盒底仍恒为 1.62 ⇒ 必须在 `orientCamera` 里改眼高。
>
> **限制（未做，登记）**：非玩家实体 crawl 只保留状态标志（1.20 里 `setPose(SWIMMING)` 对非玩家不改体型/眼高，
> `Entity#getDimensions` 忽略 pose；且 crawl 触发源是客户端本地玩家按键）。4 个新 mixin 已在 `mixins.tacz.json` 登记。

### 3.2 `RenderType` 状态分片 → 1.7.10 固定管线映射（等价性核对）

| 1.20 RenderType | shim State | GL 映射 | 核对 |
|---|---|---|---|
| `entityCutout`/`entityCutoutNoCull(ZOffset)` | opaqueCutout(cull=false 安全超集, polygonOffset) | disable blend、alphaTest(GREATER,0.1)、depth LEQUAL/write、cull off | 等价（cull=false 是 r2 登记的平台偏离） |
| `entitySolid` | solid() | 无 alphaTest、depth write | 等价 |
| `entityTranslucent(Cull)`/`itemEntityTranslucentCull` | translucent(cull,alphaTest) | blend SRC_ALPHA/ONE_MINUS_SRC_ALPHA、depthWrite=false | 等价（`State.translucent` 的 `depthWrite=false` 与 1.20 `TRANSLUCENT_TRANSPARENCY` 一致） |
| `lines`/`debugLine*` | lines(lineWidth,depthTest) | blend + lineWidth(2.0) + depthTest | 线宽 2.0 是移植选择（1.20 `LineStateShard(empty)` 不改线宽），登记 |
| `eyes`/`beaconBeam`/`lightning` | emissive/additive | lighting off / additive blend | 等价（无 shader，自发光 = 关光照 + additive） |
| `text`/`gui` | text/gui | alphaTest off、translucent、depthTest 视 seeThrough/overlay | 等价 |
| `energySwirl` | **未提供** | — | 有据降级：1.20 的 UV 滚动在 shader，1.7.10 由调用方用 `GL_TEXTURE` 矩阵实现（`EntityBulletRenderer`/`MuzzleFlashRender` 各自 TODO 已记录；r7-model 转派项） |

`setupRenderState` 采用「进入快照 / 退出还原」而非固定基线（r2 已修），这是 1.7.10 全局管线必需的括号语义；
`GlStateSnapshot` 覆盖它实际改写的每一项（tex/cull/depth/depthMask/alpha/blend+因子/lighting/polygonOffset/lineWidth/color）。

### 3.3 `RenderType` 按实例身份比较（修复多纹理批次合并，P1 级渲染正确性）

- 1.20.1 `RenderType` **不覆写** `equals/hashCode`（`RenderType.java` 全文无覆写）⇒ `MultiBufferSource` 按实例身份分批。
- 修复前 shim：`equals` 只比 `name+bufferSize`、`hashCode` 含 `name/format/mode`（还违反了 equals/hashCode 契约）。
- 后果（修复前的真实 bug，非理论）：`EmissiveMeshRenderTypes.create` 恒用 `"sbm_emissive_cutout"`、
  `MolangWorldParticleRenderType.create` 恒用 `"molang_world_particle_<material>"`（纹理只存在 State 里）、
  `DisplayGunRenderType.emissive` 恒用 `"tacz_display_gun_emissive"` —— 不同纹理的实例 `equals` 为 true ⇒
  `BufferSource` 的 `LinkedHashMap<RenderType,BufferBuilder>` 只用第一个实例的纹理**绑一次**，第二张纹理以后的
  自发光网格 / Molang 粒子被画成第一张贴图。
- 修复：删除两个覆写（保留 `toString`），与 1.20 同构。按纹理缓存的静态工厂仍返回同一实例，分批效率不变。
- **对其它工作流的影响**：`client/particle/render/ParticleRenderType.java:44-57`（r7-sbm scope）里
  「把纹理拼进 name」的 workaround 及其注释是基于旧行为的，现在**已非必需**（代码无害，注释已过时，建议 r7-sbm 顺手更新，
  见 §5-C3）。

### 3.4 HUD 事件映射（r7-crosshair F1/F2）——已修

- **F1**：`registerAbove(CROSSHAIR.id(), …)` 原映射到 `Post(CROSSHAIRS)`；但 1.7.10 `GuiIngameForge#renderCrosshairs`
  是 `if (pre(CROSSHAIRS)) return; … post(CROSSHAIRS);`（`:218-228`），而 `RenderCrosshairEvent` 持枪时 cancel
  `Pre(CROSSHAIRS)`（上游语义：替换准星）⇒ **Post 永不触发** ⇒ `InteractKeyTextOverlay`/`DisplayCaseLockHintOverlay`
  完全不显示。修复：新增 `Pre(CROSSHAIRS)` + `receiveCanceled=true` + `EventPriority.LOWEST` 处理器，**仅在 canceled 时**
  补画（此时已晚于 NORMAL 优先级的替换准星，层序等价上游「紧跟 CROSSHAIR 层之后」）；未取消时仍由 Post 画（原版准星之后）。
  两条路径互斥、不重绘（r7-crosshair 已复核）。
- **F2**：原 `if (mc.thePlayer == null || mc.currentScreen != null) return;` 与引擎不符。1.7.10
  `EntityRenderer.java:1111` / 1.20 `GameRenderer.java:1121` 均为 `if (!hideGui || screen != null)` 才调 `renderGameOverlay`
  ⇒ **GUI 打开时 HUD 仍渲染（画在 GUI 之下）**。修复为 `if (hideGUI && currentScreen == null) return;`。
- 附带：Post 分支原 `if (event.isCanceled()) return;` 是死代码（1.7.10 Post 不可取消），已删并留 1 行注释（r7-crosshair/Lead 提醒）。

### 3.4a HUD F3 回归：取消 `Pre(ALL)` 必须自己补 GUI 帧（平台层结论；Lead 已修、本报告登记）

> 与本节的 F2 相邻但**不是同一个问题**：这是「1.7.10 `Pre(ALL)` 语义 ≠ 1.20 的 overlay layer」的平台层陷阱。

- **现象（实机确认）**：打开枪械改装界面（Z 界面）/ 枪械工作台时，界面里**只剩枪械模型，按钮/槽位全部不可见**
  （两个界面都中招）。
- **根因**：`PreventsHotbarEvent` 被改成「取消所有 `RenderGameOverlayEvent.Pre`（含 ALL）」以对齐上游「这两类界面
  不画任何原版 overlay」；但 1.7.10 `GuiIngameForge#renderGameOverlay` 的 `Pre(ALL)` 是**整个 HUD 渲染方法的短路开关**
  （`GuiIngameForge.java:105` `if (pre(ALL)) return;`）——它**不是** 1.20 那种「某一个 overlay layer 的 Pre」。
  短路点在 `:108 setupOverlayRendering()`（建立 GUI 正交帧）与 `:109/:118`（GUI 混合基线）**之前** ⇒ 被取消后
  GUI 帧/混合基线从未建立，随后 `currentScreen.drawScreen` 仍在世界透视矩阵与实体渲染混合状态下绘制
  ⇒ 界面元素画到错误坐标系/不可见，只剩世界空间里的枪械模型。
- **修复（`client/event/PreventsHotbarEvent.java`，Lead 已落地）**：在 `event.setCanceled(true)` 之前，若
  `event.type == ElementType.ALL` 则先手动补建 GUI 帧 + 混合基线：
  `mc.entityRenderer.setupOverlayRendering(); GL11.glEnable(GL_BLEND); OpenGlHelper.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);`。
  保留上游语义（这两类界面不画任何原版 overlay，含 vignette/chat；GUI 侧不再有 per-screen workaround）。
- **平台层教训（供后续 HUD/事件工作流引用）**：1.7.10 的 `Pre(ALL)` 是**整体开关**——取消它不会「只少画一个元素」，
  而是连 GUI 帧初始化一起跳过；任何取消 `Pre(ALL)` 的代码都必须自己补 `setupOverlayRendering()`（及后续 GUI 绘制
  依赖的混合基线），否则 GUI 变不可见。反之，**不要**用取消 `Pre(ALL)` 实现「隐藏某个 overlay 元素」，应按 `event.type`
  精确匹配。
- **实机判据（并入 V-3）**：打开改装界面/工作台 → 按钮、槽位、文字、tooltip 全部可见且可交互；原版 HUD
  （快捷栏/血条/vignette/chat）仍被隐藏；关闭界面后 HUD 恢复。

### 3.5 弹药盒「物品栏内右键点进/点出」（Lead 转派 P1，r7-item 已实现 item 侧）——已接线

- 反射点：`Container#slotClick(int slotId,int clickedButton,int mode,EntityPlayer)` HEAD（`Container.java:142`）——
  1.7.10 没有 1.20 的 `Item#overrideStackedOnOther` 物品级钩子，物品栏点击全部集中在该方法。
- 新 mixin `mixin/common/ContainerMixin`（common 列表：**客户端预测 + 服务端权威两侧都会执行**，与
  `PlayerControllerMP#windowClick:475-481` → `NetHandlerPlayServer#processClickWindow:949` 的事务比对契约一致）：
  `if (AmmoBoxItem.tryStackedOnOther(player, slotId, clickedButton, mode)) cir.setReturnValue(player.inventory.getItemStack());`
  —— 返回值必须是「修改后的手持栈」，否则客户端预测与服务端 `processClickWindow` 比对失配（丢件/复制风险）。
- 前置过滤（只处理右键/mode 0/slotId 界内/玩家背包槽/inventorySlots 越界）全部在 item 侧完成；mixin 不再判空/改槽。
- `mixins.tacz.json` 已登记（Lead 明确转派改 resources）。

### 3.6 非玩家实体跨维度数据刷新（Lead 转派 C-4）——已实现（附 1.7.10 语义注记）

- 上游：`event/TravelToDimensionEvent` 监听 `EntityTravelToDimensionEvent`，**任意 LivingEntity** 主手持枪时 `initialData()`。
- 1.7.10 无该事件；玩家走 FML `PlayerChangedDimensionEvent`（`event/TravelToDimensionEvent`，r7-core scope，已覆盖）。
- 新 mixin `mixin/common/EntityTravelToDimensionMixin`：`@Inject(method="travelToDimension", at=HEAD)`（`Entity.java:2369`），
  服务端且 `instanceof EntityLivingBase` 且主手 `IGun` 时 `IGunOperator.fromLivingEntity(...).initialData()`。
  `EntityPlayerMP` 重写该方法（`:545`，玩家走 `ServerConfigurationManager` 专用路径）⇒ 只覆盖非玩家、不与玩家路径重复。
- **1.7.10 差异（记录）**：非玩家跨维度是「移除旧实例 + `EntityList.createEntityByName` 重建 + NBT 拷贝」
  （`Entity.java:2386-2400`），1.20 Forge `ITeleporter` 保留原实例 ⇒ 本注入对旧实例的刷新随实例销毁而失效，
  新实例的 `ShooterDataHolder` 本来就是默认值（等价「已刷新」）。保留注入以对齐上游覆盖面。

### 3.7 `PoseStack#scale` 法线矩阵：修复为 1.20 逐行同构

- 1.20.1：`pose.scale` + 等比且 >0 时 **提前 return（法线不动）**、等比 ≤0 时 `normal.scale(-1)`；
  非等比：`invCubeRoot = Mth.fastInvCubeRoot(1/(xyz))`，再 `normal.scale(invCubeRoot/x, invCubeRoot/y, invCubeRoot/z)`。
- 修复前 shim：直接 `normal.scale(1/x,1/y,1/z)`（等比缩放也改法线长度、且缺负等比特判）。
  法线矩阵在 `client/model/bedrock/BedrockCubeBox.java:138` / `BedrockCubePerFace.java:111` 被用于逐顶点法线
  （当前多数 RenderType `lighting=false`，故此前无可观测差异；但这是全仓渲染正确性基础，属真实偏离，已修）。
- 新增 `Mth.fastInvCubeRoot`（1.20.1 `Mth.java:404-410` 逐行）。

### 3.8 `PoseStack#clear()`/`setIdentity()`

- 1.20.1 `clear()` 是**纯查询** `return this.poseStack.size() == 1;`（不弹栈）；修复前 shim 会真的清栈并把返回值反过来。
  现在无调用者，但语义错误会误导后续调用点，已改回 1.20 语义并加注释。
- 补 `setIdentity()`（1.20 有、上游 `ScopeThermalRenderer:159`/`ScopeLensRenderer:185` 用到；shim 缺失）。

### 3.9 `Mth.fastInvSqrt` 裁决（Lead 转派 r7-util 项）

- 上游 `util/math/MathUtil#normalizeQuaternion`（`MathUtil.java:192`）写 `float f1 = (float) Mth.fastInvSqrt(f);`
  —— 1.20.1 `Mth` **只有** `fastInvSqrt(double)`（`Mth.java:395-402`，`@Deprecated` 的 Quake 魔数近似），
  float 实参被隐式加宽 ⇒ 上游是「误用弃用近似函数」，误差约 1e-3。
- **裁决：保留精确 `1/sqrt`，不做魔数近似。** 理由：归一化后误差只落在四元数末位，远小于动画/渲染可观测阈值；
  精确实现更稳定、可复现、不把上游 bug 当契约。已在 `Mth#fastInvSqrt` javadoc 写明该语义差异（有据偏离）。
- 顺带核对其余「近似/弃用被当契约」：`Mth.invSqrt(float)` 1.20 = JOML `invsqrt`（精确），port = `1/sqrt` 等价；
  `Mth.sin/cos` 两版都是 65536 项查表（算法一致）；`Mth.frac(double)` 1.20 用 `lfloor`（long），port 用
  `MathHelper.floor_double`（int）—— 仅在 |x|>2^31 时不同，**无调用点**，已记录。
  `MathUtil#normalizeQuaternion` 之外未见其它 `fast*` 被当契约。

### 3.10 `RenderDistance` 用 GL_MODELVIEW 代替 PoseStack（r7-util 转派，登记不改）

`util/RenderDistance` 用当前 `GL_MODELVIEW` 求相机距（3 个调用点已由 r7-util 核对为实体/物品 GL 帧已建立之后；
GUI 有 `ScreenItemVboContext` 绕过）。与 1.20 `RenderSystem.getModelViewMatrix()` + PoseStack 语义在「GL 帧已建立」的
调用点等价；**需要实机确认远距离第三人称/展示柜 LOD 判定**（见 §8 V-6），本轮不改代码。

---

## 4. mixin 对照表与注册表核对

### 4.1 `mixins.tacz.json` ↔ `@Mixin` 类（22 个文件 / 20 条注册）

| # | JSON 条目 | 1.7.10 目标 | 上游对应 / 等价说明 | 状态 |
|---|---|---|---|---|
| 1 | `common.LivingEntityMixin` | `EntityLivingBase` | 上游 `LivingEntityMixin`（`tick` TAIL→`onLivingUpdate` RETURN；每 tick 状态机） | ✅ 已注册；本轮加 crawl 体型 |
| 2 | `common.LivingEntityKnockbackMixin` | `EntityLivingBase` | 上游无 mixin：补 `KnockbackChange` 的消费端（`@ModifyConstant(knockBack 的 0.4F)`，`require=0` 防崩） | ✅ |
| 3 | `common.ServerPlayerMixin` | `EntityPlayerMP` | 上游 `ServerPlayer#restoreFrom` → `clonePlayer` RETURN（重生刷新） | ✅ |
| 4 | `common.ServerGamePacketListenerImplMixin` | `NetHandlerPlayServer` | 上游同类：疾跑 `@WrapOperation`→`@Redirect`（`processEntityAction` 的 2 处 setSprinting 全 redirect，等价 wrap） | ✅（差集见下） |
| 5 | `common.ContainerMixin`（**本轮新增**） | `Container` | 上游 `AmmoBoxItem#overrideStackedOnOther` 的落点（§3.5） | ✅ 新 |
| 6 | `common.EntityTravelToDimensionMixin`（**本轮新增**） | `Entity` | 上游 `event/TravelToDimensionEvent`（非玩家部分，§3.6） | ✅ 新 |
| 7 | `client.LocalPlayerMixin` | `EntityPlayerSP` | 上游 `LocalPlayerMixin`（r7-input scope） | ✅ |
| 8 | `client.MouseHandlerMixin` | `Entity` | 上游 `MouseHandlerMixin`（`setAngles` 双 `@ModifyVariable`；r7-input scope） | ✅ |
| 9 | `client.MinecraftAttackMixin` | `Minecraft` | 1.7.10 特有（攻击键拦截；r7-input scope） | ✅ |
| 10 | `client.ItemInHandRendererMixin` | `ItemRenderer` | 上游同名（r7-fp scope） | ✅ |
| 11 | `client.ItemInHandLayerMixin` | `RenderPlayer` | 上游 `ItemInHandLayer`（r7-tp scope） | ✅ |
| 12 | `client.GameRendererMixin` | `EntityRenderer` | 上游 `GameRendererMixin`（r7-fp scope） | ✅ |
| 13 | `client.HumanoidModelMixin` | `ModelBiped` | 上游 `HumanoidModelMixin`（r7-tp scope） | ✅ |
| 14 | `client.PlayerModelMixin` | `ModelBiped` | 上游 `PlayerModelMixin`（r7-tp scope，priority 1100） | ✅ |
| 15 | `client.CreativeModeInventoryScreenMixin` | `GuiContainerCreative` | 上游 `CreativeModeInventoryScreenMixin`（内容包筛选侧栏；本轮复核目标成员/注入点均存在） | ✅ |
| 16 | `client.AbstractButtonMixin` | `GuiButton` | 上游 `AbstractButtonMixin`（`mousePressed` RETURN + 返回值判定，语义等价 `onClick` HEAD） | ✅ |
| 17 | `client.CreativeContainerAccessor` | `GuiContainerCreative$ContainerCreative` | 移植特有 `@Accessor/@Invoker`（反射→refmap 映射，缺失时 instanceof 降级） | ✅ |
| 18 | `client.EntityRenderDispatcherMixin` | `RenderManager` | 上游同名（r7-scope 正在改，本报告不改该文件） | ✅ |
| 19 | `client.PlayerCrawlPoseMixin`（**本轮新增**） | `EntityPlayer` | crawl 姿态/体型（§3.1） | ✅ 新 |
| 20 | `client.EntityRendererCrawlCameraMixin`（**本轮新增**） | `EntityRenderer` | crawl 相机眼高（§3.1；与 r7-fp 的 `GameRendererMixin` 同目标但注入点不同） | ✅ 新 |
| — | `client.LanguageMixin` | `Locale` | 上游即**注释死代码**；1.7.10 枪包语言改走 `LanguageRegistry`（`GunPackLoader#injectAllGunPackLanguages`） | ⚠ **有意未注册**（空 `@Mixin`，注册也无行为） |
| — | `client.SoundManagerPreparationsMixin` | `SoundHandler` | 上游功能 mixin；1.7.10 等价实现在 `client/sound/GunSoundInstance#resolve`（反射定位 `SoundRegistry`，注册 `SoundPoolEntry`→`tacz_sounds/**.ogg`，幂等 + 重载自愈，`REGISTERED_TACZ_SOUNDS` 身份比对；r7-gun 复核 ✅） | ⚠ **有意未注册**（空 `@Mixin`，替代实现已接线） |
| — | 上游 `client.RenderSystemAccessor` / `client.StairBlockAccessor` | — | 均未移植：前者目标类 1.7.10 不存在（使用方 `UnshadedVertexConsumer` 已降级为兜底法线）；后者上游全仓 0 使用（死代码） | ✅ 有据不移植 |
| — | 上游 `common.ServerPlayNetHandlerMixin` | `ServerGamePacketListenerImpl` | 副手交换 `handlePlayerAction`→`ServerMessageSwapItem`；**1.7.10 无副手**（唯一交换途径 1.9+），消息类与 `SwapItemWithOffHand` API 保留、`ClientProxy` 处理为 no-op | ✅ 有据降级（r7-network 转派核对一致） |

**注册核对结论**：JSON ↔ 文件一一对应；`package`/`refmap` 名与构建产物一致（`build/resources/main/mixins.tacz.{json,refmap.json}`，
构建期由 mixin AP 生成 refmap；`mixins.srg` 亦在 `build/tmp/mixins`）。**新增 4 条注册后 refmap 需随下次 `build` 重新生成**
（当前 `build/` 里是旧产物；`compileJava`/`build` 由 Lead 统一执行）。

### 4.2 逐 mixin 注入点等价性（重点不在他人 scope 的类只做目标/成员存在性核对）

- **`LivingEntityMixin`**：上游 `LivingEntity#tick` TAIL → 1.7.10 `EntityLivingBase#onLivingUpdate` RETURN；
  已核对 `onLivingUpdate`（`EntityLivingBase.java:1928-2033`）**无提前 return** ⇒ 每 tick 恰好一次。
  全部 `@Unique` 工具类/字段与上游同构；`initialData()` 的判空、`getSynReloadState()` 的 null 归一、
  `onTickServerSide` 的 `reloadStateType` 判空是移植防御（上游 null 哨兵在 1.7.10 变 null，r5 已记录）。
- **`ServerGamePacketListenerImplMixin`**：`@Redirect` 命中 `processEntityAction` 里 2 处 `EntityPlayerMP#setSprinting(Z)` 调用
  （`NetHandlerPlayServer.java:822/826`），与上游 `@WrapOperation` 覆盖面一致（`getProcessedSprintStatus` 语义不变）。
- **`AbstractButtonMixin`**：1.7.10 无 `onClick` 回调，注入 `GuiButton#mousePressed` RETURN 且**只在返回 true 时**记录时间戳
  （等价上游 `onClick` HEAD；避免「点在按钮外」误记）。子类重写 `mousePressed` 且不调用 super 的按钮不覆盖（无观感影响，登记）。
- **`CreativeModeInventoryScreenMixin`**：`initGui` TAIL / `setCurrentCreativeTab` TAIL / `drawScreen` HEAD+TAIL /
  `mouseClicked` HEAD / `handleMouseInput` HEAD 五处注入点全部已在 `GuiContainerCreative` 源码核对存在；
  `@Shadow selectedTabIndex`/`currentScroll` 是目标类**自有**字段（`GuiContainerCreative.java:43/45`），不违反「@Shadow 不遍历父类」；
  自持 `@Unique` 按钮表 + 自绘/自派发（不用父类 `buttonList`，理由类注释已写）；类内无嵌套类（Mixin `compatibilityLevel=JAVA_8`）。
- **`CreativeContainerAccessor`**：`@Accessor("itemList")`/`@Invoker("scrollTo")` 目标存在于
  `GuiContainerCreative$ContainerCreative`（`:1006/:1038`）；`instanceof` 缺失降级路径存在。
- **`LivingEntityKnockbackMixin`**：`@ModifyConstant(method="knockBack", floatValue=0.4F, require=0, expect=1)`；
  `EntityLivingBase#knockBack`（`:1050-1069`）全仓唯一实现、无子类重写 ⇒ 覆盖面 = 上游 `LivingKnockBackEvent`。
- **`ServerPlayerMixin`**：`EntityPlayerMP#clonePlayer(EntityPlayer, boolean)`（`:990`）是 1.7.10 唯一的
  「重生/征服末地后重建玩家」路径（`ServerConfigurationManager.java:484` 唯一调用点）⇒ 与上游 `restoreFrom` 覆盖面一致。
- **新增 4 个**：目标方法/成员均已在 `build/rfg` 源码逐行核对（`Container#slotClick:142`、`Entity#travelToDimension:2369`、
  `EntityPlayer#onLivingUpdate:598`、`EntityRenderer#orientCamera:552`）；均无 `@Shadow`、无嵌套类。

---

## 5. client/init 注册清单核对（vs 上游 `FMLClientSetupEvent` / 各 MOD 总线事件）

上游 `client/init/ClientSetupEvent` 共 7 个订阅点，逐一核对落点（本仓 `ClientSetupEvent#onClientSetup` 顺序执行）：

| 上游事件 | 1.7.10 落点 | 核对 |
|---|---|---|
| `RegisterShadersEvent`（`ScopeLensRenderer.registerShader`） | `ScopeLensRenderer.init()` / `ScopeThermalRenderer.init()`（显式 init） | ✅ GL 上下文在 `FMLInitializationEvent` 时已存在（`Minecraft` 构造器 `Display.create():494` 早于 `FMLClientHandler.beginMinecraftLoading:522`，`initializeMods()`（=init）在 `finishMinecraftLoading:311`） |
| `RegisterKeyMappingsEvent` | `GunInputHandler.registerKeyMappings()` | ✅ 且在 `FMLClientHandler:350 gameSettings.loadOptions()` 之前注册，键位可持久化 |
| `RegisterClientTooltipComponentFactoriesEvent`（5 个工厂） | `ItemTooltipEvent`（`client/event/TooltipEvent`，5 个 `Client*Tooltip` 全部接回）+ 手绘 | ✅ 等价路径存在（`renderImage` 仅配件网格可绘，已记录降级） |
| `RegisterGuiOverlaysEvent`（5 层） | `aboveAll` = GunHud/HeatBar/KillAmount（Post(ALL)）；`aboveCrosshair` = InteractKeyText/DisplayCaseLockHint（Post(CROSSHAIRS) + **新增 Pre(canceled) 路径**） | ✅ **F1 修复**；层序 = 上游（aboveAll 在最后、aboveCrosshair 紧跟准星之后） |
| `FMLClientSetupEvent`（ThirdPersonManager/ItemColors/ItemProperties/ItemBlockRenderTypes/compat） | `ThirdPersonManager.registerDefault` ✅；`AmmoBoxItem::getColor` → 1.7.10 类方法 `getColorFromItemStack`（等价）；`ItemProperties ammo_statue` → 无 JSON 模型谓词（见 §5-C2）；`ItemBlockRenderTypes.setRenderLayer(GUN_DISPLAY_CASE)` → 1.7.10 `Block#getRenderType`（r7-block 核对）；compat 4 个 init ✅；`PlayerAnimatorCompat.init` 时机从「资源重载监听」提前到 setup（反射骨架、缺失库静默，无差异） | ✅ 除 C2 外等价 |
| `RegisterClientReloadListenersEvent`（SoundPlayManager 缓存清理 + `ClientAssetsManager.reloadAndRegister`） | `client/resource/ClientGunPackReloader`（`IResourceManagerReloadListener`）+ `ReloadResourceEvent`（`TextureStitchEvent.Post` → `SoundPlayManager.clearSoundResourceCache`） | ✅ 等价路径存在（r7-respack-client 复核） |
| —（上游 `client/init/ModContainerScreen`/`GunPegboardColors`/`ParticleFactoryRegistry`） | `IGuiHandler#getClientGuiElement`（GUI 分发）/ 方块物品类方法染色 / `EffectRenderer#addEffect` 直接实例化 | ✅ 三个类保留为零逻辑锚点，等价路径存在（类注释均已写明） |

**物品渲染器注册（影响「枪械是否出现」）**：`ClientSetupEvent#registerItemRenderers` 覆盖
`McKinetic gun/Ammo/Attachment`（3 个精确物品）+ `GunSmithTableItem` 类级匹配（含 3 个 workbench 变体）= 上游 4 个
`Item#initializeClient` 的全部；同时把实例登记进 SBM `FirstPersonRenderHandler.registerRenderer`（否则第一人称不画枪）。
`BlockModelGuiItemRenderer` 覆盖 6 个方块物品的 GUI 3D 图标。**未注册**：ammo box 的 9 个 `ammo_statue` 模型变体（C2）。
**实体/TESR**：`ModEntitiesRender#register` 的 2 实体渲染器 + 5 TESR 与上游 `ModEntitiesRender` **逐项相同**；
`RenderingRegistry.loadEntityRenderers` 在 init 之后（`FMLClientHandler:328`）消费 ⇒ 注册时机正确。

---

## 6. 跨模块发现（交给对应工作流 / Lead）

| # | 发现 | 影响 | 建议 owner / 处理 |
|---|---|---|---|
| **C1** | `RenderType` 已改为「按实例身份」，修复多纹理批次合并（§3.3） | **正面影响**：SBM v2 自发光网格、Molang 世界粒子、曳光弹等多纹理场景不再错贴图 | r7-sbm/r7-block：**不需要改代码**；但 `client/particle/render/ParticleRenderType.java:44-57` 的「name 带纹理」注释/实现依据已过时（代码仍正确），建议更新注释 |
| **C2** | ammo box 的 9 个 `item model overrides`（`tacz:ammo_statue` 谓词 → `ammo_box/iron|gold|diamond|creative|all_type_creative_*`）在 1.7.10 无承载：`AmmoBoxItem#getStatue` **0 调用者**、单图标 `registerIcons("tacz:ammo_box")` | 物品栏/手持外观只显示基础铁盒 + 等级染色，**不显示等级/创造/开关盖模型**（上游是完整 3D 物品模型） | r7-item / r7-gui（`AmmoBoxItem` 类注释已标 `TODO(L2, client-port)`）；可用 `IItemRenderer` + `BlockModelGuiItemRenderer` 同类方案补 |
| **C3** | 同上 C1：若 r7-sbm 需要，可把 `EmissiveMeshRenderTypes`/`MolangWorldParticleRenderType` 的 name 加纹理仅作调试可读性（非必需） | 无 | r7-sbm |
| **C4** | `MultiBufferSource` 的 A→B→A 交错绘制顺序与 1.20 不同（1.20 在类型切换时 flush，shim 延迟到 endBatch 按类型首次出现顺序） | 透明面在极端交错调用下排序可能与上游不同（当前调用点均为「按类型整段绘制」，未观测到差异） | 本报告登记；如需严格等价需改成「切换即 flush」，但会破坏「持有旧 consumer 继续写」的调用（如 SBM baked model 双 consumer），不建议本轮改 |
| **C5** | `RenderSystem.setShaderColor` 对逐顶点色几何无染色（1.7.10 固定管线颜色数组覆盖当前色） | 渲染代码里凡「只 setShaderColor 想 tint」的调用点都必须改写成写顶点色/alpha（仓内主要调用点已适配：持枪准星、HUD 图标、枪板预览 alpha 等） | 本报告登记；`RenderSystem#setShaderColor` javadoc 已写明；后续渲染工作流改动时注意 |
| **C6** | `Mth.fastInvSqrt` 保留精确实现（上游误用弃用近似） | 无数值可观测差异 | 已裁决（§3.9），r7-util 可知会 |
| **C7** | `RenderDistance` 用 GL_MODELVIEW 代替 PoseStack | 需实机看远距离第三人称/展示柜 LOD | r7-util 已核对 3 个调用点 GL 帧；本报告登记，不改 |
| **C8** | `EntityRenderDispatcherMixin` 由 r7-scope 修改（热源捕获 API），本报告未改该文件 | — | 已与 r7-scope 协调（其负责） |
| **C9** | `EntityRendererCrawlCameraMixin` 与 r7-fp 的 `GameRendererMixin` **同目标类**（不同注入点：本类 `orientCamera` STORE 首个 float；r7-fp 是 `renderWorld/renderHand/hurtCameraEffect/setupViewBobbing/getFOVModifier` 等） | 无冲突（Mixin 允许多个 mixin 同目标）；但改 `orientCamera` 局部量的人需知道 camera drop 已在此叠加 | r7-fp 知悉（实机若相机高度异常，先看本 mixin） |
| **C10** | **非玩家 mob 持枪无第三人称 `ItemInHandLayer` 等价注入点**（r7-tp 报告 C-3）：1.7.10 第三人称「持物层」是按渲染器内联的——玩家走 `RenderPlayer#renderEquippedItems(AbstractClientPlayer,F)`（`:182`，已由 `ItemInHandLayerMixin` 包住），其他生物走 `RenderBiped#renderEquippedItems(EntityLiving,F)`（`:194`，只画原版手持物品）+ 泛型桥 `:360`；TACZ 的枪模型渲染（`HumanoidOffhandRender#renderGun(EntityLivingBase,…)`）与第三人称动画/枪口状态机都按玩家链路搭建 | 用命令/外部模组给僵尸/骷髅等装备 TACZ 枪时：**无第三人称枪模型、无第三人称枪口粒子/弹壳**（第一人称永远只有玩家，不受影响）。TACZ 自身逻辑从不给 mob 装枪 ⇒ 实际玩家可见影响≈0 | **判定：有据降级，本轮不做**（加 `@Mixin(RenderBiped.class)` 的 `renderEquippedItems(EntityLiving,F)` HEAD/RETURN 即可接上 `HumanoidOffhandRender.renderGun`，但需为 mob 模型补 arm-bone 锚点与 `ThirdPersonManager` 动画状态，收益/风险比低；bridge 方法同名同描述符需写全 `(Lnet/minecraft/entity/EntityLiving;F)V` 防歧义）。若产品要求再单开任务 |
| **C11** | `HumanoidModelMixin`/`PlayerModelMixin` 同目标（`ModelBiped#setRotationAngles` TAIL）依赖 `priority`（1000/1100）顺序（r7-tp 提醒） | 已核对：两者都是**幂等绝对赋值**（`PlayerModelMixin#tacz$resetAll` 写 0；`InnerThirdPersonManager#setRotationAnglesHead` 写绝对角度，不做累加），因此不存在「重复执行放大」问题；但**执行顺序**仍决定最终值（归零必须先于「套姿态」还是之后，与上游一致=姿态先、归零后），current priority 1100 语义正确，**无需改成「末尾再算一次」**（当前实现已是「后应用=后执行」的末尾语义） | r7-tp 知悉；若实机第一人称手臂归零失效，先查 `AnimationRenderContext.isFirstPersonHand` 作用域（r5 render-fp F-1 的同类根因），不要先动 priority |

---

## 7. 未修 / 有据降级清单（逐条理由）

1. **`energySwirl` 未提供**（r7-model 转派）：1.20 UV 滚动在 shader；1.7.10 由调用方用 `GL_TEXTURE` 矩阵实现
   （`EntityBulletRenderer`/`MuzzleFlashRender` 已各有 TODO 说明）。**保持降级并已在 `mcshim/RenderType` javadoc 登记**。
2. **无 shader / FBO 输出目标 / 面排序 / `sortOnUpload`**：1.7.10 固定管线能力缺失（1.20 的 shader 状态分片无法表达）。
3. **`RenderType` cull 族恒 false**（r2 已登记安全超集，改回 cull 需逐模型验绕序）。
4. **`RenderType.lines()` 线宽 2.0F**：1.20 `lines()` 不改线宽（GL 默认 1）；当前用于枪板/展示柜高亮，属移植观感选择，未改。
5. **`MultiBufferSource` 交错顺序**（C4）。
6. **`BufferUploader`/`TessellatorVertexConsumer` overlay 丢弃**：1.7.10 顶点格式无 overlay 通道（受伤红闪无法逐顶点表达）。
7. **shader color 语义**（C5）。
8. **`LightTexture` 打包布局 / `PacketBuffer` 线上格式**：两侧都是本模组、1.7.10 原生布局（已记录）。
9. **非玩家实体 crawl 不改体型**（§3.1 注）；**非玩家跨维度实例重建**（§3.6 注）。
10. **无副手（swap item）**：`ServerMessageSwapItem`/`SwapItemWithOffHand` API 保留、处理 no-op；上游 `ServerPlayNetHandlerMixin` 不移植。
11. **`GlStateSnapshot` 只保存 RGB 混合因子**（对称 alpha 因子在还原时写回 RGB 值）：外层若使用 `glBlendFuncSeparate` 的
    非对称 alpha 因子，`clearRenderState` 后可能被覆盖成对称 —— 当前无调用方依赖该组合（`defaultBlendFunc` 是唯一非对称使用者且
    自身成对设置），登记。
12. **`EntityStateHelper.getPose` 顺序与 1.20 不同**（先判 sleep 再判 fall-flying）：1.7.10 无可出现的 FALL_FLYING/SPIN_ATTACK，
    对可出现的组合无差异（类注释已写）。
13. **1.7.10 无 `Pose` 的「姿态被外部改回」检测**：`LivingEntityCrawl`/`LocalPlayerCrawl` 的 `getForcedPose()!=SWIMMING` 条件恒 false
    （r5/r7-gun 已登记；本轮新增的 pose 覆盖表是**输出**，不回写 crawl 状态机，避免状态环）。
14. **`RenderType` 的 `mask()`/`waterMask` 未实现 colorMask**：0 调用点。
15. **`ProfilerFiller` 30 字符截断 / `GsonHelper` 旧 gson API**：平台差异（已记录）。
16. **非玩家 mob 持枪的第三人称渲染/枪口粒子**（r7-tp C-3）：判定为有据降级，理由与将来实现路径见 §6-C10（TACZ 自身不给
    mob 装枪；接 `RenderBiped#renderEquippedItems(EntityLiving,F)` 需另行补 mob arm-bone 锚点与动画状态）。

---

## 8. 实机验证清单（本轮改动相关，按优先级）

| # | 项 | 判读方法 |
|---|---|---|
| **V-1（P1）弹药盒点进/点出** | 物品栏手持/光标持弹药盒：右键**空槽** → 应取出弹药；右键**弹药槽** → 应存入并计数；创造盒不能取、全类型创造盒不能存；连续快速点击无「回弹/丢件/复制」（客户端预测与服务端事务必须一致）；退出重进后 NBT 正确 | 观察物品栏变化；若出现回弹/复制立即回滚该 mixin 并检查返回值契约 |
| **V-2（P2）趴下可见性** | 按趴下键（默认未绑定，需先在控制里绑定 Crawl）：相机高度应下降约 1.22（眼高 0.4）；可钻进 1 格高缝；鼠标上下俯仰被限制在配置角度内；趴下时枪械后坐力/灵敏度表现变化；松开恢复站立且不被卡住；睡觉/骑乘/水里无法趴下 | 与 1.20 上游对照相机高度/碰撞箱；若相机不动 ⇒ 检查 `EntityRendererCrawlCameraMixin` 是否命中（`-Dmixin.debug.injectors=true` 看 `orientCamera` 注入）；若可穿墙/卡方块 ⇒ 检查体型 mixin |
| **V-3（P1）HUD F1/F2 + F3** | 持枪时按 O 交互提示与展示柜上锁提示应出现（此前完全无）；非持枪时也在。打开聊天框（透明 GUI）时 TACZ HUD 应画在聊天框之下（可见）；打开背包时被 GUI 覆盖（正常）；打开枪械工作台/改装界面时：**按钮/槽位/文字/tooltip 全部可见可交互**（§3.4a 的 F3 修复判据）、原版 HUD（含快捷栏/vignette/chat）整体隐藏，关闭后 HUD 恢复 | 持枪无提示 ⇒ 检查 `Pre(CROSSHAIRS)` 被 cancel 时 LOWEST 处理器是否执行（`-Dmixin.debug.injectors=true`）；Z/工作台界面元素不可见 ⇒ 检查 `PreventsHotbarEvent` 是否仍在取消 `Pre(ALL)` 前补了 `setupOverlayRendering()` + 混合基线（§3.4a） |
| **V-4（P1）多纹理自发光/粒子** | 多个不同贴图的 SBM v2 自发光网格（如不同枪的发光瞄具/装饰）同屏：**不应**全部变成第一把枪的贴图；Molang 世界粒子（snowstorm，不同 material+texture）同理 | 修复前会「所有自发光壳/粒子同贴图」，修复后各自正确；若仍错 ⇒ 检查是否还有代码 `new RenderType` 每帧新建（identity 下会多批但不串图） |
| **V-5（P2）跨维度** | 玩家跨维度后枪械数据正常（既有 `PlayerChangedDimensionEvent` 路径不被新 mixin 重复刷新：无双重 `initialData()` 副作用）；若有持枪非玩家实体（命令/mod 提供）跨维度后不崩 | 日志无 `initialData` 异常；玩家跨维度后瞄准/换弹状态复位正常 |
| **V-6（P2）渲染 LOD/相机** | 远距离第三人称/展示柜 LOD 判定正常（`RenderDistance` 用 GL_MODELVIEW 的登记项）；趴下时第一/第三人称相机高度都正确 | 对照 1.20 观感 |
| **V-7（P2）PoseStack 法线** | 非均匀缩放模型（枪械挂件/纸片）在开启光照/受光渲染时明暗方向正确、无过亮/过暗 | 与 1.20 对照；若异常看 `BedrockCubeBox/BedrockCubePerFace` 的 normal 变换 |
| **V-8（回归）** | 正常开服/关服、进出世界、F3+T 资源重载无 mixin 相关异常；`mixins.tacz.json` 新增 4 条后启动无 `InvalidInjectionException`（`-Dmixin.debug.verbose=true` 可看每类 apply 结果） | 重点看 `ContainerMixin`/`EntityTravelToDimensionMixin`/`PlayerCrawlPoseMixin`/`EntityRendererCrawlCameraMixin` 是否成功 apply |

---

## 9. 改动文件清单（12 个，`lint_files(min_severity=error)` 全 0）

| 文件 | 改动 |
|---|---|
| `src/main/java/com/tacz/guns/mcshim/RenderType.java` | 删除 `equals/hashCode` 覆写（1.20 实例身份语义，修复多纹理批次合并）；`State.text` 重复赋值清理；`energySwirl` 降级登记；类尾注释说明 |
| `src/main/java/com/tacz/guns/mcshim/PoseStack.java` | `scale()` 改 1.20 逐行同构（等比特判 + `fastInvCubeRoot` 归一）；`clear()` 改回纯查询；补 `setIdentity()` |
| `src/main/java/com/tacz/guns/mcshim/Mth.java` | 补 `fastInvCubeRoot`（1.20 逐行）；`nextInt` 的 `min>=max` 守卫；`fastInvSqrt` 语义裁决注释 |
| `src/main/java/com/tacz/guns/mcshim/RenderSystem.java` | `setShaderColor` 的语义差异/降级说明（固定管线颜色数组覆盖 glColor，不改行为） |
| `src/main/java/com/tacz/guns/mcshim/MultiBufferSource.java` | 修正「绘制顺序与调用顺序一致」的错误 javadoc，如实记录 A→B→A 顺序偏离 |
| `src/main/java/com/tacz/guns/client/init/ClientSetupEvent.java` | HUD F1（Pre(CROSSHAIRS)+receiveCanceled+LOWEST 补画）+ F2（引擎门条件）+ 死 guard 清理 + 注释/import |
| `src/main/java/com/tacz/guns/mixin/common/LivingEntityMixin.java` | crawl 服务端体型/眼高（`tacz$updateCrawlDimensions` + `@Unique` 哨兵） |
| `src/main/java/com/tacz/guns/mixin/common/ContainerMixin.java`（新） | 弹药盒 `slotClick` HEAD cancel + 返回修改后的手持栈 |
| `src/main/java/com/tacz/guns/mixin/common/EntityTravelToDimensionMixin.java`（新） | 非玩家活体跨维度刷新 |
| `src/main/java/com/tacz/guns/mixin/client/PlayerCrawlPoseMixin.java`（新） | 客户端 crawl 姿势覆盖表 + 体型 |
| `src/main/java/com/tacz/guns/mixin/client/EntityRendererCrawlCameraMixin.java`（新） | 客户端 crawl 相机眼高 1.62→0.4 |
| `src/main/resources/mixins.tacz.json` | 登记 4 个新 mixin（Lead 明确转派改 resources；其余内容未动） |

> 未改 `docs/` 之外的其它文件；`tmp/**`/`build/**`/`run/**` 未改；未运行 gradle。
>
> 另：`src/main/java/com/tacz/guns/client/event/PreventsHotbarEvent.java` 的 F3 回归修复由 **Lead 直接落地**
> （不在上表 12 个改动计数内），本报告 §3.4a 登记其根因/实现/判据；该文件属 `client/event`（input scope），
> 平台层只做语义核对。

---

## 10. 覆盖清单（诚实）

- **逐行/逐段读过**：`mcshim` 全部 39 类（`PacketBuffer`/`ResourceManager`/`FileToIdConverter`/`Resource`/`GsonHelper`
  以接口与语义抽核为主，数值细节以 r7-network/r7-respack-core 报告为准）；`mixin/**` 本 scope 的 9 个既有类
  （`LivingEntityMixin`/`LivingEntityKnockbackMixin`/`ServerPlayerMixin`/`ServerGamePacketListenerImplMixin`/
  `AbstractButtonMixin`/`CreativeContainerAccessor`/`CreativeModeInventoryScreenMixin`/`LanguageMixin`/
  `SoundManagerPreparationsMixin`）+ 本轮新增 4 类；他人 scope 的 9 个 mixin 只做「目标/成员存在性 + 注册」核对，
  未审内部语义（属 r7-fp/r7-tp/r7-input/r7-scope）；`client/init` 5 类全读 + 上游 5 类逐项对照。
- **上游对照源**：`tmp/minecraft-src-1.20.1`（PoseStack/RenderType/MultiBufferSource/VertexConsumer/BufferBuilder/
  RenderSystem/LightTexture/Axis/Mth/Player/Entity/SynchedEntityData 等）、`tmp/MinecraftForge-1.20.1/patches`
  （`Player.java.patch` 的 forcedPose）、`build/rfg/minecraft-src`（EntityRenderer/GuiIngameForge/Entity/EntityPlayer/
  EntityPlayerSP/EntityLivingBase/Container/NetHandlerPlayServer/ServerConfigurationManager/FMLClientHandler 等）。
- **未动态验证**：本轮全部结论为静态证据（未运行 gradle/未启动客户端）；相机注入点（`orientCamera` 首个 float STORE）
  与 `Container#slotClick` 的 refmap 生成需下次构建后确认（见 V-2/V-8）；`mixin.debug.injectors=true` 可作为注入命中判读。
