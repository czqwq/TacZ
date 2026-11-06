# Angelica 2.2.42 × TACZ 1.7.10 兼容性审查

> 审查对象：**Angelica 2.2.42**（GTNewHorizons，1.7.10 的 Sodium + Iris 式渲染改造）
> × 本仓 **TACZ 1.7.10 移植**（`com.tacz.guns.**` + vendored `simplebedrockmodel` / `mae`）。
>
> 产出目的：**只给结论与修法，不做重构**。所有判断均带源码依据（`文件:行`），
> 无法从源码确定的写「**待实测**」。
>
> 路径前缀约定：
> - `A/` = `tmp/angelica-2.2.42-src/`（Angelica 反编译产物，1584 个 `.java`）
> - `T/` = `src/main/java/`（本仓）；`R/` = 仓库根（用于 `src/main/resources/*`）

---

## 0. 结论速览

| 项 | 结论 |
| --- | --- |
| **P0 数量** | **3 条**（P0-1 core profile 上下文 / P0-2 主 framebuffer 深度附件 / P0-3 第一人称手部渲染时序） |
| P1 | 7 条 |
| P2 | 6 条 |
| 最关键的 3 条 | ① Angelica **强制 OpenGL 3.3 core + forward-compatible** 上下文，本仓所有 compatibility-profile 假设（`glVertexPointer`→attribute 0 别名、`glPushAttrib`、`glGetFloat(GL_MODELVIEW_MATRIX)`）都需要 GLSM 模拟；② 开了 shader pack 时主 framebuffer 的深度附件从 **renderbuffer 变成 depth texture**，`ScopeThermalRenderer` 的 `main.depthBuffer > 0` 硬闸直接把热成像打死；③ 开 shader pack 时 `EntityRenderer#renderHand` 里的 `ItemRenderer#renderItemInFirstPerson` 被**跳过**、手部改在**关卡 pass 内**由 Iris `HandRenderer` 重绘，本仓的手部/镜片时序假设随之改变 |
| **是否需要 compat 门面** | **需要，但很小**（净新增约 60 行，且不改既有架构）：① `CompatRegistry` 加 `ANGELICA = "angelica"`；② `OculusCompat.LOADED` 增加 `angelica` 探测（否则 `isRenderShadow()` 在 Angelica 光影 shadow pass 里恒 `false`，见 P1-3）；③ `ScopeThermalRenderer` 的深度附件探测改成「查 GL 附件类型」而不是「查 `depthBuffer` 字段」（见 P0-2）。**不需要**为此重构渲染管线；但 P0-1 的「自建 VBO+VAO」若实测确认失效，需要一次**局部**改造（约 60~80 行，只在 `client/renderer/scope/**` 内） |

**重要产物缺口（限制本次审查确定性）**：`A/com/gtnewhorizons/angelica/glsm/**`（Angelica 的 GL 状态管理器 / FFP 模拟器 / `ModRedirector`）**不在反编译产物里**
（1584 个文件中不存在 `GLStateManager.java`，也无 `glsm` 目录；`org.embeddedt.embeddium.*`、`com.gtnewhorizon.gtnhlib.*` 同样是外部依赖）。
因此「Angelica 的 FFP 模拟到底覆盖哪些固定管线调用」只能从**调用方代码 + 公开 API 注释**反推，具体覆盖度一律标 **待实测**（见 §4）。

---

## 1. Angelica 实际接管了什么

### 1.1 入口级（最致命，决定其它一切）

| # | Angelica 行为 | 依据 | 对本仓的意义 |
| --- | --- | --- | --- |
| A1 | **`@Overwrite ForgeHooksClient#createDisplay()`，强制请求 `ContextAttribs(3,3).withProfileCore(true).withForwardCompatible(true)`**；拿不到就抛 `RuntimeException("Angelica requires an OpenGL 3.3 core profile context...")` | `A/com/gtnewhorizons/angelica/mixins/early/angelica/MixinForgeHooksClient_CoreProfile.java:45-66`、`:143-148` | **装了 Angelica 就没有 compatibility profile**。`glBegin/glEnd`、`glVertexPointer/glTexCoordPointer/glColorPointer`、`glEnableClientState`、`glMatrixMode/glPushMatrix/glLoadMatrix/glOrtho`、`glPushAttrib/glPopAttrib`、`glAlphaFunc`、`glEnable(GL_ALPHA_TEST/GL_LIGHTING/GL_FOG/GL_TEXTURE_2D)`、`glCallList` 全部**不在 core 3.3**（forward-compatible 连"已废弃但仍在表里"的也没有） |
| A2 | 其它模组的 GL 调用在**类加载期被改写**成 GLSM 调用，注释原文："Angelica's bytecode transformer redirects GL calls (e.g. `GL11.glPushMatrix()`) to the GLSM classes for state tracking and **core profile compatibility**" | `A/com/gtnewhorizons/angelica/api/GLRedirects.java:11-17`；`A/com/gtnewhorizons/angelica/loading/shared/transformers/AngelicaRedirector.java:6-15`（`new ModRedirector(..., "com.gtnewhorizons.angelica.transform")`）；`A/.../loading/fml/transformers/AngelicaRedirectorTransformer.java:7-14`（对**所有**类生效） | 本仓 `mcshim`/`client/**` 里成千上万条 `GL11.*` 都会被换成 GLSM。**这是唯一让 1.7.10 固定管线在 core profile 上还能画的原因**，但覆盖度不可见（glsm 源码缺失） |
| A3 | 拦截 `Tessellator#draw()` → 走 `ModelPartBatcher.drawTessellator`（"streaming VBO+VAO for core profile rendering"） | `A/com/gtnewhorizons/angelica/mixins/early/angelica/ffp/MixinTessellator_CoreProfile.java:17-23`；路由逻辑 `A/com/gtnewhorizons/angelica/rendering/tesr/ModelPartBatcher.java:852-905` | 本仓**所有顶点提交都走 `Tessellator`**（见 P1-4），因此这条拦截是本仓渲染的必经之路 |
| A4 | 启动期**彻底禁用** OptiFine / Fastcraft / BetterFPS（移除 transformer、mod 容器、mixin 配置），并 `@Overwrite FMLClientHandler#hasOptifine()` 恒返回 `false`、`detectOptifine()` 空实现 | `A/.../loading/fml/tweakers/IncompatibleModsDisablerTweaker.java:24-37,50-66`；`A/.../mixins/early/angelica/MixinFMLClientHandler.java:17-29` | 本仓若走 `FMLClientHandler#hasOptifine()` 判断光影，会恒 `false`（本仓实际用的是自建反射 `OptifineCompat`，见 P2-6） |
| A5 | modid = `angelica`，另注册子 mod `embeddium` / `notfine` / `mcpatcherforge` | `A/com/gtnewhorizons/angelica/config/AngelicaConfig.java:7`（`@Config(modid = "angelica")`）；`A/mcmod.info:29-76` | compat 探测依据：**`Loader.isModLoaded("angelica")`** |

### 1.2 与本仓重叠的原版类：逐方法接管表

> 「也注入」= 与本仓 mixin 落在同一方法；「改写」= `@Overwrite`/`@Redirect` 使得方法体不再是原版语义。

#### `net.minecraft.client.renderer.EntityRenderer`（本仓 `GameRendererMixin` 目标）

| Angelica 注入点 | 依据 | 与本仓的关系 |
| --- | --- | --- |
| 整方法 `@Surround renderWorld(FJ)V`（置 `RenderingState.isRenderingWorld`） | `A/.../mixins/early/angelica/MixinEntityRenderer.java:37-46` | 与本仓 `renderWorld` HEAD（`T/com/tacz/guns/mixin/client/GameRendererMixin.java:192`）**同点共存**，不冲突 |
| `renderWorld` @INVOKE `ClippingHelperImpl.getInstance` AFTER：`Iris` 管线 begin、`Program.unbind()`、`GLSMCurrentAttribs.setShaderColor(1,1,1,1)` | `A/.../mixins/early/shaders/MixinEntityRenderer.java:46-66` | 世界渲染前把 shader color 归 1 |
| `renderWorld` @INVOKE `ForgeHooksClient.dispatchRenderLast` **BEFORE**：`pipeline.finalizeLevelRendering()`、`Program.unbind()`、`glDepthMask(true)`、`disableBlend()`、`glAlphaFunc(GL_GREATER,0.1)`、`enableAlphaTest()` | 同上 `:68-81` | **关键**：本仓热成像是 `RenderWorldLastEvent` 驱动的（`T/com/tacz/guns/client/init/ClientSetupEvent.java:303`、`T/.../ScopeThermalRenderer.java:377`），而 `dispatchRenderLast` 正是投递该事件的地方 → 本仓热成像 pass 落在 Angelica **归一化 GL 状态之后**（这个顺序反而是好事，见 P0-2） |
| `renderWorld` @Redirect `GameSettings.renderDistanceChunks`（≥4）、@ModifyConstant `128.0D`（云层顺序） | 同上 `:100-110` | 与本仓无关 |
| `renderWorld` @Surround/@Inject 包裹 `renderSky`/`renderCloudsCheck`/`renderRainSnow`/`drawBlockDamageTexture`/`renderLitParticles`/`renderParticles`（切 Iris phase、按 shader 配置 Skip、`GbufferPrograms.begin/endParticles`） | 同上 `:112-194` | 粒子阶段与方块破坏阶段被包在 Iris gbuffer program 内；本仓光影/粒子相关绘制（`MuzzleFlashRender`、`AttackParticle` 等）若在这些阶段内提交，会跑到 gbuffer program 下 |
| **`renderHand` @Surround `ItemRenderer#renderItemInFirstPerson`，`isShaderPackInUse()` 时 `@Surround.Skip`** | 同上 `:83-87` | **P0-3** |
| `hurtCameraEffect` @ModifyConstant `14.0F`（NotFine 受伤摇晃强度） | `A/.../mixins/early/angelica/MixinEntityRenderer.java:69-73` | 与本仓对同一常量的 `@ModifyArg`（`T/.../GameRendererMixin.java:302-320`）**共存**：Mixin 的 `@ModifyConstant` 与 `@ModifyArg` 分别改常量与被调参数，**不冲突**，但两者叠加顺序会影响最终角度（待实测观感） |
| `setupCameraTransform` / `renderHand` 里 `setupViewBobbing` 的 `@WrapWithCondition`（NotFine 的 bobview 模式） | `A/.../mixins/early/angelica/MixinEntityRenderer.java:75-83` | 与本仓 `setupViewBobbing` HEAD cancel（`T/.../GameRendererMixin.java:327-338`）**共存**：Angelica 包住调用点，本仓在方法内 HEAD cancel，**都不冲突** |
| `getFOVModifier` @ModifyReturnValue（Zoom 除算） | `A/.../mixins/early/angelica/zoom/MixinEntityRenderer_Zoom.java:17-21` | **P2-1**（与本仓 `getFOVModifier` RETURN cancel 抢返回点） |
| `updateCameraAndRender` @Redirect `GuiIngame#renderGameOverlay` → `HUDCaching.renderCachedHud` | `A/.../mixins/early/angelica/hudcaching/MixinEntityRenderer_HUDCaching.java:13-18` | **P1-6** |
| `renderRainSnow` / `addRainParticles` HEAD cancel（自己的实例化天气） | `A/.../mixins/early/angelica/rendering/MixinEntityRenderer_Weather.java:13-21` | 无关 |
| `setupCameraTransform` @ModifyArg `gluPerspective`（把 fov 存进 `RenderingState`）、`ActiveRenderInfo.updateRenderInfo` AFTER（存相机矩阵/位置） | `A/.../mixins/early/angelica/MixinEntityRenderer.java:48-67` | 只读，不影响本仓 |

#### `net.minecraft.client.renderer.RenderGlobal`（区块渲染管线整体替换）

| Angelica 行为 | 依据 |
| --- | --- |
| `@Overwrite sortAndRender(...)`：区块只画一次、按 Iris phase 分层（SOLID / CUTOUT_MIPPED / TRANSLUCENT） | `A/.../mixins/early/celeritas/terrain/MixinRenderGlobal.java:139-202` |
| `@Overwrite clipRenderersByFrustum(...)`（Celeritas viewport + `RenderingState.fov` 变化时重排） | 同上 `:208-245` |
| `@Overwrite markBlocksForUpdate(...)`、`updateRenderers(...)`、`getDebugInfoRenders()` | 同上 `:251-254,301-304,314-339` |
| **`renderEntities`：`@ModifyExpressionValue` 把 `renderGlobal.tileEntities` 改成空列表**（原版 TESR 循环被删），改由 `celeritas$renderTileEntities → CeleritasWorldRenderer#renderBlockEntities` 自己走 `TileEntityRendererDispatcher` | 同上 `:275-295` |
| `renderEntities` 里 `RenderHelper.enableStandardItemLighting()` 前后 `GbufferPrograms.begin/endBlockEntities` + `setBlockEntityDefaults()` | 同上 `:275-290` |
| 只分配 3 个 display list、`loadRenderers` 里 `renderDistanceChunks`/常量 `16` 归零（旧 `WorldRenderer` 资源清零） | 同上 `:89-105` |
| shaders 包：`renderSky` 逐阶段切 Iris phase、`drawSelectionBox` 包 outline program、`renderEntities` 包 entity loop | `A/.../mixins/early/shaders/MixinRenderGlobal.java:21-146` |

#### `net.minecraft.client.renderer.entity.RenderManager`（本仓 `EntityRenderDispatcherMixin` 目标）

| Angelica 注入点 | 依据 |
| --- | --- |
| `func_147939_a` @Redirect `Render#doRenderShadowAndFire`（translucency declaration） | `A/.../mixins/early/shaders/MixinRenderManager.java:23-35` |
| `func_147939_a` @Surround `Render#doRender`（`EntityRenderScope.begin/end`、`TesrAttribution.currentRenderable`） | 同上 `:37-53` |
| `func_147939_a` **另一个** `@Redirect` 同一处 `Render#doRender`：`BatchEligibility.begin/end` + `EntityShadowReplay.beginRenderer/endRenderer` + 渲染器 batch 状态机 | `A/.../mixins/early/angelica/entity/MixinRenderManager_BatchEligibility.java:18-36` |

> 本仓 `EntityRenderDispatcherMixin` 只在 `func_147939_a` 的 **HEAD/RETURN** 注入（`T/com/tacz/guns/mixin/client/EntityRenderDispatcherMixin.java:75-96`），与上面三个 INVOKE 级注入**字节码层面不冲突**。
> ⚠️ 但该 mixin **没有登记在 `R/mixins.tacz.json` 的 `client` 数组里**（`R/mixins.tacz.json:12-23` 只有 10 项），**当前不生效** → 热成像的实体重绘窗口永远不开。这是本仓自身的接线缺口（见 §4-9）。

#### `net.minecraft.client.renderer.ItemRenderer`（本仓 `ItemInHandRendererMixin` 目标）

| Angelica 注入点 | 依据 | 与本仓关系 |
| --- | --- | --- |
| `renderItemIn2D` 整方法 `@Surround`（命中 `ItemRenderListManager` 缓存 VBO 时 Skip 原实现） | `A/.../mixins/early/angelica/itemrenderer/MixinItemRenderer.java:44-56` | 本仓不调用 `renderItemIn2D` → 只影响原版物品 |
| `renderItemIn2D` 内 5 处 `Tessellator.draw/startDrawingQuads` `@Redirect`（`require = 5`） | 同上 `:58-84` | 同上；但**注意**：这是 `require=5` 的强绑定，任何一个前提被别的 mod 改动都会让 Angelica 直接崩（不是本仓问题，但是共存风险） |
| `renderItemInFirstPerson` HEAD **cancellable**：shader pack 生效时按 solid/translucent pass 跳过 | `A/.../mixins/early/shaders/MixinItemRenderer.java:26-36` | **P0-3** |
| `renderItemInFirstPerson` RETURN：`ItemIdManager.resetItemId()` | 同上 `:41-47` | 与本仓 `renderItemInFirstPerson` HEAD（`T/.../ItemInHandRendererMixin.java:113-119`）共存 |
| `dynamiclights/MixinItemRenderer`（手持物动态光源） | `A/.../mixins/early/angelica/dynamiclights/MixinItemRenderer.java:10` | 无关 |
| `notfine/glint/MixinItemRenderer`、`bugfixes/MixinItemRenderer_EdgeDepth`（priority 1100）、`mcpatcherforge/**/MixinItemRenderer`（CIT/CC） | `A/.../mixins/early/notfine/glint/MixinItemRenderer.java:9`、`A/.../mixins/early/angelica/bugfixes/MixinItemRenderer_EdgeDepth.java:27` | 都只改附魔光效/边缘深度 |

#### `net.minecraft.client.renderer.entity.RenderPlayer`（本仓 `ItemInHandLayerMixin` 目标）

| Angelica 注入点 | 依据 | 与本仓关系 |
| --- | --- | --- |
| `shaders/MixinRenderPlayerArmor`（护甲 gbuffer 材质） | `A/.../mixins/early/shaders/MixinRenderPlayerArmor.java:28` | 不冲突 |
| `notfine/glint/MixinRenderPlayer`（护甲光效） | `A/.../mixins/early/notfine/glint/MixinRenderPlayer.java:10` | 不冲突 |
| `mcpatcherforge/cit/.../MixinRenderPlayer`（CIT 随机皮肤） | `A/.../mixins/early/mcpatcherforge/cit/client/renderer/entity/MixinRenderPlayer.java:19` | 不冲突 |
| `RendererLivingEntity#doRender` 里 `renderEquippedItems` 调用点被 `@Surround` 包成「期望 draw 数」窗口 | `A/.../mixins/early/angelica/entity/MixinRendererLivingEntity_EquippedDraws.java:13-24` | **P1-2**：本仓在 `renderEquippedItems` 里画枪，会被算成 "foreign draws" |

#### `ModelBiped` / `Entity` / `GuiContainerCreative` / `GuiButton` / `EntityPlayerSP`

| 原版类 | Angelica 是否接管 | 依据 | 本仓对应 mixin |
| --- | --- | --- | --- |
| `ModelBiped` | 只注入 `render(...)` RETURN（玩家反射贴图捕获），**不碰 `setRotationAngles`** | `A/.../mixins/early/shaders/MixinModelBiped.java:18-32` | `HumanoidModelMixin` / `PlayerModelMixin` 都注 `setRotationAngles` TAIL（`T/.../mixin/client/HumanoidModelMixin.java:86`、`PlayerModelMixin.java:71`）→ ✅ **无冲突** |
| `Entity` | `onEntityUpdate` TAIL（动态光源）+ `isInRangeToRender3d` 的 `@ModifyArg`（Sodium 实体渲染距离倍率） | `A/.../mixins/early/angelica/dynamiclights/MixinEntity.java:84-87`；`A/.../mixins/early/rendering/MixinEntity_RenderDist.java:12-16` | 本仓 `MouseHandlerMixin` 注 `Entity#setAngles`（`T/.../mixin/client/MouseHandlerMixin.java:84,98`）→ ✅ **无冲突** |
| `GuiContainerCreative` | **不接管**（全 lint 无 mixin 目标） | grep 结果：`A/com/gtnewhorizons/angelica/mixins/**` 中无 `GuiContainerCreative` 目标 | `CreativeModeInventoryScreenMixin` / `CreativeContainerAccessor` → ✅ **无冲突** |
| `GuiButton` | **不接管**（只有 `MixinGuiOptions` 里 `buttonList.removeIf` 引用了 `GuiButton` 类型） | `A/.../mixins/early/angelica/gui/MixinGuiOptions.java:3,20` | `AbstractButtonMixin` → ✅ **无冲突** |
| `EntityPlayerSP` | **不接管** | grep 无命中 | `LocalPlayerMixin`（`EntityPlayerSP#onLivingUpdate`）→ ✅ **无冲突** |

#### `Framebuffer` / `TextureMap` / `TextureManager` / `FMLClientHandler` / `SimpleReloadableResourceManager` / `TileEntity` / `TileEntityRendererDispatcher`

| 原版点 | Angelica 行为 | 依据 |
| --- | --- | --- |
| `Framebuffer#createFramebuffer` | **`@Redirect` 把 `useDepth` 读成 `false`（不再建深度 renderbuffer）**，改成自建 **depth texture** 并挂 `GL_DEPTH_ATTACHMENT`（有 stencil 时同时挂 `GL_STENCIL_ATTACHMENT`，内部格式 `DEPTH24_STENCIL8`） | `A/.../mixins/early/shaders/MixinFramebuffer.java:63-66`、`:69-91` |
| `Framebuffer#bindFramebuffer` | HEAD 可取消：HUD 缓存捕获期间把「主 framebuffer」的绑定切到 HUD 缓存 FBO | `A/.../mixins/early/angelica/hudcaching/MixinFramebuffer_HUDCaching.java:14-21` |
| `Framebuffer#framebufferRender` | `@Overwrite`：FFP 全屏四边形 → `PostProcessingHelper` 静态 VAO | `A/.../mixins/early/angelica/vbo/MixinFramebuffer.java:28-51` |
| `TextureMap#loadTextureAtlas` | `@Surround`/`@WrapOperation` 并行预取 + 并行 mipmap（`AtlasLoadSession`） | `A/.../mixins/early/angelica/textures/MixinTextureMap_ParallelLoad.java:27-112` |
| 并行图集**信任策略** | **自定义 `IResourcePack` 类默认「不可信」→ 该 domain 整体退化为串行**，并写进 `atlasObservedPacks` + 打日志 | `A/.../textures/atlas/AtlasPackPolicy.java:92-151`、`:153-171` |
| `TextureManager#onResourceManagerReload` TAIL | PBR 纹理索引重建 + `incrementTextureReloadCount()` | `A/.../mixins/early/shaders/MixinTextureManager_ReloadCount.java:21-31` |
| `SimpleReloadableResourceManager#reloadResources` RETURN | 只投递 GLSM loading checkpoint（`requiresSync`） | `A/.../mixins/early/angelica/MixinSimpleReloadableResourceManager.java:16-23` |
| `FMLClientHandler` | `@Overwrite detectOptifine(){}` / `hasOptifine()` 恒 `false`；`processWindowMessages` RETURN 投 checkpoint | `A/.../mixins/early/angelica/MixinFMLClientHandler.java:17-36` |
| `TileEntity#getRenderBoundingBox` | `@Overwrite`（不再一律 `INFINITE_EXTENT_AABB`），并加**按 (x,y,z,blockType,metadata) 键的缓存** `angelica$getCachedRenderBoundingBox` | `A/.../mixins/early/rendering/MixinTileEntity.java:33-47`、`:86-114` |
| `TileEntityRendererDispatcher#renderTileEntityAt` | `@Redirect`：`TesrBatchRenderer.beginScope()` → 若 TESR 实现 `TesrMeshProvider` 则走 Angelica 网格缓存，否则 `BatchEligibility.begin/end` 包一层状态机 | `A/.../mixins/early/angelica/tesr/MixinTileEntityRendererDispatcher.java:19-44` |
| `TileEntityRendererDispatcher` 构造 | `mapSpecialRenderers` 换成 `ConcurrentHashMap`（区块网格线程会读） | `A/.../mixins/early/rendering/MixinTileEntityRendererDispatcher.java:25-42` |
| `RenderHelper` / `ForgeHooksClient`（并发） | `celeritas/threading/MixinForgeHooksClient` `@Overwrite` | `A/.../mixins/early/celeritas/threading/MixinForgeHooksClient.java:17` |

### 1.3 与本仓 mixin 的重叠矩阵（`R/mixins.tacz.json:12-23` 的 10 个 client mixin）

| 本仓 mixin | 目标 | Angelica 是否注入同一方法 | 风险等级 |
| --- | --- | --- | --- |
| `LocalPlayerMixin` | `EntityPlayerSP#onLivingUpdate` | ❌ 不注入 | 无 |
| `MouseHandlerMixin` | `Entity#setAngles` | ❌（只注 `onEntityUpdate` / `isInRangeToRender3d`） | 无 |
| `ItemInHandRendererMixin` | `ItemRenderer#renderItemInFirstPerson` HEAD / `updateEquippedItem` HEAD | ✅ 同方法（HEAD cancellable + RETURN） | **P0-3** |
| `ItemInHandLayerMixin` | `RenderPlayer#renderEquippedItems` HEAD/RETURN | ⚠️ 上游调用点被 `BatchEligibility` 包成 draw 计数窗口 | **P1-2** |
| `GameRendererMixin` | `EntityRenderer#renderWorld` HEAD、`renderHand`(INVOKE `renderOverlays`)、`hurtCameraEffect` HEAD+`@ModifyArg`、`setupViewBobbing` HEAD、`getFOVModifier` HEAD+RETURN、`orientCamera` RETURN | ✅ 前四个同方法、`getFOVModifier` 同返回点 | **P2-1 / P2-2**（`renderWorld`/`renderHand` 本身不冲突） |
| `HumanoidModelMixin` / `PlayerModelMixin` | `ModelBiped#setRotationAngles` TAIL | ❌（Angelica 注的是 `render`） | 无 |
| `CreativeModeInventoryScreenMixin` | `GuiContainerCreative` | ❌ | 无 |
| `AbstractButtonMixin` | `GuiButton#mousePressed` | ❌ | 无 |
| `CreativeContainerAccessor` | `GuiContainerCreative$ContainerCreative` | ❌ | 无 |
| `EntityRenderDispatcherMixin`（**未登记到 json**） | `RenderManager#func_147939_a` HEAD/RETURN | ✅ 同方法但只在 INVOKE 点 | **P1-3**（半死）+ 需接线 |

---

## 2. 冲突清单

> 严重度：**P0** = 装了就坏（功能级/可能崩）；**P1** = 特定条件下明显坏或严重退化；**P2** = 观感/性能/边缘条件。
> 每条给：Angelica 依据 / 本仓依据 / 冲突机制 / **建议修法（优先改本仓，不依赖 Angelica 内部实现）**。

### P0-1｜core profile 上下文：本仓「固定管线 + attribute 0 别名」的镜片/热成像着色路径不再成立

- **Angelica 依据**：`A/.../mixins/early/angelica/MixinForgeHooksClient_CoreProfile.java:66`（`withProfileCore(true).withForwardCompatible(true)`）、`:143-148`（要求 GL 3.3 core）；`A/.../mixins/early/angelica/ffp/MixinTessellator_CoreProfile.java:17-23`（`Tessellator#draw` 被接管）；`A/api/GLRedirects.java:11-17`（其它模组的 GL 调用被 GLSM 改写）。
- **本仓依据**：
  - `T/com/tacz/guns/client/renderer/scope/ScopeLensProgram.java:204-205`（`glBindAttribLocation(program, 0, "Position")`）+ `:317-319`（javadoc：「compatibility profile 下 `glVertexPointer`→0、`glColorPointer`→3、`glMultiTexCoord0Pointer`→8」）
  - `T/.../ScopeLensRenderer.java:443-454`（`drawDisc` 用 `Tessellator` TRIANGLE_FAN 提交）+ `:440`（同一句注释）
  - `T/.../ScopeLensRenderer.java:551-560`（`glGetFloat(GL_PROJECTION_MATRIX …)` / `glGetFloat(GL_MODELVIEW_MATRIX …)`）
  - `T/.../GunDisplayCaseHighlightEvent.java:133,144`、`T/.../GunPegboardHighlightEvent.java:113,124`、`T/.../RenderHeadShotAABB.java:84,94`（`glPushAttrib/glPopAttrib`）
  - `T/.../mcshim/GlStateManager.java:213-216`（`glAlphaFunc`）、`T/.../mcshim/RenderSystem.java:244-251`（`GL_LIGHTING`）、`:270-279`（`glPolygonMode`）、`T/.../mcshim/RenderType.java:441,463-465,496-498`、`T/.../client/model/RenderHelperCompat.java:120`
- **冲突机制**：
  1. **attribute 0 别名不存在**：core profile 里没有 `glVertexPointer`，`Tessellator#draw()` 又被 Angelica 换成 `TessellatorStreamingDrawer`（走它自己的 VBO/VAO/程序）。于是「我们的 program + 我们的 attribute 0」这条链**在任何情况下都不可能成立**——即使 GLSM 让 `glUseProgram(ourProgram)` 生效，顶点也不是从 `Tessellator` 的客户端数组喂到 attribute 0 的。表现：镜片 shader 静默失效（画成纯色/无畸变圆盘）或整个 disc 不出现，**编译期不一定报错**。
  2. **`glPushAttrib/glPopAttrib`、`glGetFloat(GL_MODELVIEW_MATRIX)`、`glAlphaFunc`、`GL_LIGHTING`、`glPolygonMode` 全是 compatibility-only**。如果 GLSM 的改写/模拟覆盖不到，最坏情况是 LWJGL2 在 core 上下文里调用空函数指针 → 直接异常/崩溃；较轻则是静默无效 → **状态泄漏**（本仓 3 处高亮/描边渲染用 pushAttrib 做「整段隔离」，一旦 Pop 无效，后续所有渲染吃到错误的 blend/line/depth 状态）。
  3. `glGetFloat(GL_MODELVIEW_MATRIX)` 在 core profile 是 `GL_INVALID_ENUM`（矩阵栈不存在，除非 GLSM 完整模拟）→ 返回全 0 → `ScopeLensRenderer.Bounds` 得到 0 尺寸包围盒 → `!bounds.isValid()` 直接不登记镜片（**静默无镜片**）。
- **建议修法（本仓侧，按性价比排序）**：
  1. **【必做·小】** 把 3 处 `glPushAttrib/glPopAttrib` 换成显式状态保存/恢复（`glIsEnabled` + `glGetInteger/glGetFloat` + 手工回写），或直接复用已有的 `ScopeLensRenderer.RenderState` 模式。这是局部改动，且**两个 profile 都合法**。
  2. **【必做·小】** `currentProjectionModelView()` 不要查 GL 矩阵：改为从 `PoseStack`/调用方传入（`ScopeLensRenderer.addScope` 已在上层持有 `PoseStack`），或用我们自己维护的 `glGetFloat` 之外的状态。查 `GL_FRAMEBUFFER_BINDING`/`GL_STENCIL_ATTACHMENT` 这类**core 合法**查询可以保留。
  3. **【实测后再决定】** `ScopeLensRenderer.drawDisc` + `ScopeThermalRenderer` 的顶点提交从 `Tessellator` 改为**自建 VBO + VAO + `glVertexAttribPointer(0, …)` + `glDrawArrays`**（约 60~80 行，只在 `client/renderer/scope/**`；`GL20` 的 VBO/VAO 接口在 1.7.10 的 LWJGL2 里都有）。这条路在 compat 与 core 两种上下文都合法，且**不再依赖 Angelica 的 FFP 模拟**——是唯一稳的写法。若实测发现 GLSM 已经把 `Tessellator + 自建 program` 组合跑通，可以不做。
  4. **【文档级】** 在类注释里把「compatibility profile 假设」标注成「Angelica 下不成立」，避免后人再写 `glVertexPointer` 别名依赖。

### P0-2｜主 framebuffer 的深度附件变成 depth texture → 热成像整条 pass 直接早退

- **Angelica 依据**：`A/.../mixins/early/shaders/MixinFramebuffer.java:63-66`（`useDepth` 被 Redirect 成 `false`，**不再创建 depth renderbuffer**）、`:69-91`（改挂 depth **texture**，`iris$depthTextureId`）。
- **本仓依据**：
  - `T/.../client/renderer/scope/ScopeThermalRenderer.java:390-394`：`Framebuffer main = mc.getFramebuffer(); if (main == null || main.depthBuffer <= 0) return;` ← **`depthBuffer` 在 Angelica 下恒为 0**
  - `T/.../ScopeThermalRenderer.java:463-481`（`attachSceneDepth`：把 `main.depthBuffer` 当 renderbuffer 挂到自己的 FBO；`type == GL_TEXTURE` 分支已存在但没人用）
  - `T/.../ScopeThermalRenderer.java:549-581`（`ensureTarget`：自建 color texture + FBO，`glFramebufferTexture2D`）
  - `T/.../ScopeLensRenderer.java:515-519`（`copyScreen`：`main.bindFramebuffer(false)` + `glCopyTexSubImage2D`）、`:530-548`（`stencilAvailable`：查**当前绑定** FBO 的 stencil 附件）
- **冲突机制**：热成像的做法是「借主 framebuffer 的深度附件给自己 FBO 用」。Angelica（Iris 生效时）把深度换成 texture：① `depthBuffer <= 0` 直接 `return` → **热成像视觉模式整体失效（静默）**；② 即使绕过闸门，`glFramebufferRenderbuffer(..., GL_RENDERBUFFER, 0)` 会把深度附件**摘掉** → `glCheckFramebufferStatus` 报 incomplete → 仍然 `return`；③ `stencilAvailable()` 查的是「当前绑定的 FBO」而不是主 FBO，在 Angelica 的 FBO 归属会变的前提下不可靠；④ 我们的 lens/thermal 顺手 `main.bindFramebuffer(false)` 收尾时 `bindFramebuffer(0)`，这在与 HUD 缓存/后处理链共存时会改变「谁该是当前 FBO」。
- **建议修法（本仓侧，不依赖 Angelica 内部字段）**：
  1. **把「有没有可借的深度」改成 GL 附件探测**：绑定主 FBO 后查
     `glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE / _OBJECT_NAME)`，
     - `GL_RENDERBUFFER` → 走现有 `glFramebufferRenderbuffer` 分支（`main.depthBuffer`）；
     - `GL_TEXTURE` → 走**已经写好但未使用**的 `type == GL_TEXTURE` 分支（`ScopeThermalRenderer.java:470-476`）。
     这样 Angelica 与否都成立，删掉 `depthBuffer <= 0` 这个硬闸（改为「两种附件都查不到才早退」）。
  2. `stencilAvailable()` 改成「先 `main.bindFramebuffer(false)`，再查主 FBO 的 stencil 附件」；或干脆把「主 FBO 的附件信息」抽成一个每帧算一次的小缓存（`mainFboAttachments()`）。
  3. 我们的自建 FBO 进出**不要写死 `glBindFramebuffer(GL_FRAMEBUFFER, 0)`**，改成保存/恢复进入时的 `GL_FRAMEBUFFER_BINDING`（`ScopeThermalRenderer.java:441`、`:578` 两处）。这与 HUD 缓存、Iris 后处理链共存时才安全。
  4. 热成像采样 depth texture 时注意 `GL_TEXTURE_COMPARE_MODE`：Angelica 已显式设成 0（`MixinFramebuffer.java:79`），因此直接 `texture2D(depthTex, uv).r` 可用——但要在我们 FBO 的 depth 附件是 texture 时改用 `sampler2D` 语义（我们目前是「挂到 FBO 当深度用」，不是采样，所以暂不涉及）。

### P0-3｜开 shader pack 后第一人称手部渲染被搬家：`renderItemInFirstPerson` 在 `renderHand` 里被跳过，改在关卡 pass 内由 Iris `HandRenderer` 调用

- **Angelica 依据**：
  - `A/.../mixins/early/shaders/MixinEntityRenderer.java:83-87`（`isShaderPackInUse()` → `@Surround.Skip` 掉 `renderHand` 里的 `ItemRenderer#renderItemInFirstPerson(F)V`）
  - `A/net/coderbot/iris/pipeline/HandRenderer.java:127-181`（`renderSolid`：`GLSMAttribStack.pushState(StateSet.HAND)` + `GbufferPrograms.setCutoutDefaults/setBlockEntityDefaults` + `setupGlState` + `itemRenderer.renderItemInFirstPerson(tickDelta)`）、`:183-235`（`renderTranslucent` 同构）
  - `A/net/coderbot/iris/pipeline/HandRenderer.java:40-78`（`setupGlState`：自己 `gluPerspective(getFOVModifier(tickDelta,false), …)` + `hurtCameraEffect` + `setupViewBobbing`）
  - `A/.../mixins/early/shaders/MixinEntityRenderer.java:68-81`（`endLevelRender` 里 `HandRenderer.INSTANCE.renderTranslucent(...)` 后重置 blend/alpha/depthMask）
- **本仓依据**：
  - `T/.../mixin/client/ItemInHandRendererMixin.java:113-119`（在 `renderItemInFirstPerson` HEAD 抛 `BeforeRenderHandEvent` 并 `PoseStack.applyToModelView()`）
  - `T/.../mixin/client/GameRendererMixin.java:231-236`（镜片画在 `renderHand` 的 `ItemRenderer#renderOverlays(F)V` INVOKE 之前）
  - `T/com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java:406-472`（真正的第一人称枪模型绘制：在 **Forge 的 `RenderHandEvent`** 里画，然后 `setCanceled(true)`）
  - `T/.../client/event/FirstPersonRenderEvent.java:62-108`（只做状态机生命周期）
- **冲突机制**：
  1. 本仓的枪模型挂在 **Forge `RenderHandEvent`** 上（由 `ForgeHooksClient.renderFirstPersonHand` 投递，`build/rfg/minecraft-src/java/net/minecraftforge/client/ForgeHooksClient.java:266`）。开 shader pack 后 `renderItemInFirstPerson` 被搬进 Iris 的 hand pass，`RenderHandEvent` 也会在那时投递 → **枪还会画出来**，但此时：
     - 处于 Iris 的 **gbuffer program** 下（`setBlockEntityDefaults`），且 `StateSet.HAND` 的 GL 状态被 push/pop；
     - 时间点从「世界画完之后」提前到「关卡内的手部 pass」——所有「世界已画完」的假设（例如粒子/贴花/镜片依赖的主 framebuffer 颜色）都不再成立。
  2. 我们的 `BeforeRenderHandEvent`＋`PoseStack.applyToModelView()`（`ItemInHandRendererMixin:113-119`）在 Iris hand pass 里也会执行，但 Iris 在 `setupGlState` 里**已经把投影/模型视图重置过**（`HandRenderer.java:43-66`），两者的矩阵语义要重新核对。
  3. 镜片（`renderHand` 里的 `renderOverlays` INVOKE）**仍然在原位置执行**：此时主 framebuffer 里是「已合成 + 已画手」的图，`copyScreen` 拿到的是含手的画面 → 镜片叠加位置/深度观感与原版路径不同。
  4. 反向风险：Iris 的 `canRender` 还要求 `RenderHandEvent.post()`（**Angelica 自己的**事件，`A/com/gtnewhorizons/angelica/event/RenderHandEvent.java:18-24`）未被取消——本仓不订阅它，所以这条暂时安全；但任何第三方模组取消它 → 开 shader pack 时手部**完全不画**。
- **建议修法**：
  1. **【必做·文档+实测】** 在 `ItemInHandRendererMixin` / `GameRendererMixin` / `FirstPersonRenderHandler` 的 javadoc 里补一条「Angelica + shader pack：本 pass 落在关卡内 hand pass，不得假设世界已绘制完成」。这是**已知差异**，不是 bug。
  2. **【必做·小】** 镜片：`ScopeLensRenderer.render()` 里对「当前是否在 Iris hand/关卡内」不敏感是好事，但 `copyScreen` 依赖「当前绑定 = 主 FBO」。建议在 `copyScreen` 里先 `int bound = glGetInteger(GL_FRAMEBUFFER_BINDING)`，非 0 时先 `main.bindFramebuffer(false)` 再拷贝、完事恢复原绑定（现在只有 `main.bindFramebuffer(false)`、没有恢复）。
  3. **【实测后再决定】** 如果实测发现 shader pack 下枪模型位置/缩放错，检查点是 `HandRenderer.setupGlState` 的 `getFOVModifier(tickDelta,false)` —— 本仓对这个调用有 RETURN 覆盖（`GameRendererMixin.java:401-407`，返回 `CameraSetupEvent.ITEM_MODEL_FOV`），因此**理论上仍然生效**；若失效说明 Angelica 改了调用点（`require` 会崩而不是静默）。
  4. **不要**去 mixin Iris 的 `HandRenderer`：那是 Angelica 内部实现，版本一升就断。

### P1-1｜TESR 从 `RenderGlobal#renderEntities` 里被摘掉，改由 Angelica 自己 dispatch

- **Angelica 依据**：`A/.../mixins/early/celeritas/terrain/MixinRenderGlobal.java:292-295`（`tileEntities` → `Collections.emptyList()`）、`:275-290`（在 `RenderHelper.enableStandardItemLighting()` 的 INVOKE 点做 `GbufferPrograms.beginBlockEntities()` + `celeritas$renderer.renderBlockEntities()`）、`:112-120`（`setWorldAndLoadRenderers` 后 `RenderDevice.enterManagedCode()`）。
- **本仓依据**：`T/.../client/renderer/block/{GunDisplayCaseRenderer,GunPegboardRenderer,GunSmithTableRenderer,TargetRenderer,StatueRenderer}.java`（5 个 TESR，全部 `extends TileEntitySpecialRenderer`）；`T/.../client/init/ModContainerScreen.java`（注册点）；`T/.../mcshim/BlockEntityRenderers.java:9`。
- **冲突机制**：我们的 TESR 仍然会被调用（走 `TileEntityRendererDispatcher`），但：① 调用时机/顺序由 Celeritas 决定（不再在 `RenderGlobal` 的 vanilla 循环里）；② 被包在 Iris 的 block-entity gbuffer program 与 `setBlockEntityDefaults()` 之下；③ 被 `TesrBatchRenderer.INSTANCE.beginScope()` + `BatchEligibility.begin/end` 包住（`A/.../tesr/MixinTileEntityRendererDispatcher.java:19-44`）→ 我们的 TESR 只要「既画模型部件又自己发 draw」，就会被判 `DENIED`（`A/.../rendering/tesr/BatchEligibility.java:97-109`），并打日志 `"{} draws its own geometry alongside model parts ({} foreign draws, ...) - excluding it from model part batching"`（`:162-170`）。
- **建议修法**：**只需接受 + 文档化**：`DENIED` 只是「该 TESR 不进 Angelica 的模型部件批处理」，**不影响正确性**，只影响性能与影子 pass 的 replay。若日后性能敏感，可选（非必须）实现 Angelica 的 `api.tesr.TesrMeshProvider`（`A/com/gtnewhorizons/angelica/api/tesr/TesrMeshProvider.java`）让我们的网格进它的缓存——**但这会把本仓绑到 Angelica 内部 API，与「不依赖 Angelica 内部实现」冲突，故列为"不做"**。

### P1-2｜`RenderPlayer#renderEquippedItems` 里画枪 → 整个 `RenderPlayer` 被踢出模型部件批处理

- **Angelica 依据**：`A/.../mixins/early/angelica/entity/MixinRendererLivingEntity_EquippedDraws.java:13-24`（把 `renderEquippedItems` 调用点包成 `beginExpectedDraws/endExpectedDraws`）；`A/.../entity/MixinRenderManager_BatchEligibility.java:18-36`（`BatchEligibility.end` 决定 SAFE/DENIED，并写回**渲染器实例**的 batchState）；`A/.../rendering/tesr/BatchEligibility.java:97-109`。
- **本仓依据**：`T/.../mixin/client/ItemInHandLayerMixin.java:106-130`（`RenderPlayer#renderEquippedItems` HEAD/RETURN 里画第三人称手里的枪）。
- **冲突机制**：`batchState` 是**每个 `Render` 实例**一份（`A/.../entity/MixinRender_BatchEligibility.java:9-22`），而 `RenderPlayer` 是全局单例 → 本仓多画的那几次 draw 会让 `foreignDraws > 0` → `RenderPlayer` 永久 `DENIED`（`BailReason`/`foreignDraws` 只在 `onStateChange` 时打一次日志）。影响：**所有玩家的模型退出 Angelica 的实例化批处理**（性能）+ `EntityShadowReplay`（实验性，默认关，`AngelicaConfig.java:166-169`）不再录制玩家。
- **建议修法**：**接受，不修**（正确性无风险）。可在文档/issue 里注明「装 Angelica 后玩家模型批处理失效，属预期」。若确实想保住批处理：把第三人称枪模型改为在 `RenderPlayer#doRender` 之外（例如 `RenderWorldLastEvent` 或自己的 `Render` 子类）绘制——**不建议**，会推翻上游结构。

### P1-3｜`OculusCompat` 探测不到 Angelica → 光影 shadow pass 里本仓仍会渲染/重绘

- **Angelica 依据**：modid = `angelica`（`A/.../config/AngelicaConfig.java:7`）；shadow pass 由 Iris 驱动：`A/.../mixins/early/shaders/MixinEntityRenderer.java:89-97`（`pipeline.renderShadows(...)`）、`A/net/coderbot/iris/shadows/ShadowRenderingState.java:7`（`areShadowsCurrentlyBeingRendered()`）。
- **本仓依据**：`T/.../compat/oculus/OculusCompat.java:44-45`（`LOADED = Loader.isModLoaded("oculus") && IrisApi 可加载`，`CompatRegistry.java:30` 里 `OCULUS = "oculus"`）、`:65-71`（`isRenderShadow()`）；使用点：`T/.../ScopeLensRenderer.java:233`、`T/.../ScopeThermalRenderer.java:226,378`、`T/.../model/functional/MuzzleFlashRender.java:143,170,187`、`ShellRender.java:185`、`RightHandRender.java:71`、`LeftHandRender.java:71`、`BedrockModel.java:523,530`、`BedrockAttachmentModel.java:280`、`ExternalShellRenderer.java:43`。
- **冲突机制**：Angelica 提供的是 `net.irisshaders.iris.api.v0.IrisApi`（**new** 包名，`A/net/irisshaders/iris/api/v0/IrisApi.java:11-41`，本仓反射的 `getInstance()/isShaderPackInUse()` 都有）+ `net.coderbot.iris.shadows.ShadowRenderingState`（**legacy** 包名，本仓也探测）。**类名都能找到，唯独 `Loader.isModLoaded("oculus")` 为 `false`** → `LOADED=false` → `isRenderShadow()` 恒 `false`。后果：Iris 的 shadow pass 期间我们不会早退 → 热源实体重绘、枪口/弹壳粒子在 **shadow map** 里被再画一遍（污染影子 / 模板预算翻倍）；`isUsingRenderPack()` 也恒 `false`（本仓目前只用于日志与手部 buffer，影响小）。
- **建议修法（**这一条是本次审查唯一推荐"值得做"的代码改动**）**：
  1. `T/com/tacz/guns/init/CompatRegistry.java:30` 附近加 `public static final String ANGELICA = "angelica";`
  2. `OculusCompat.LOADED` 改为 `(Loader.isModLoaded(OCULUS) || Loader.isModLoaded(ANGELICA)) && firstFound(IRIS_API_CLASSES) != null`；`isRenderShadow()`/`isUsingRenderPack()` 逻辑不动（它们已经只依赖类是否可加载）。
  3. 顺手把注释里的「1.7.10 上恒为 false」「1.7.10 没有对应版本」改成「1.7.10 由 **Angelica** 提供 Iris 实现」（`OculusCompat.java:21-24,43,74`）。
  - 副作用检查：`isRenderShadow()` 从「恒 false」变成「真实值」后，`MuzzleFlashRender` 等在 shadow pass 里会**跳过**绘制——这正是上游 1.20 的行为，`F1`/`F5` 观感应无变化；**必须实测**（见 §4-7）。

### P1-4｜`Tessellator#draw` 被接管 → 本仓自建 GLSL program 的「顶点来源」不再受我们控制

- **Angelica 依据**：`A/.../mixins/early/angelica/ffp/MixinTessellator_CoreProfile.java:17-23`；`A/.../rendering/tesr/ModelPartBatcher.java:852-905`（`routeTessellatorDraw`：`DRAW_NOW` / `DRAW_NOW_EXPECTED` / `QUEUE`，默认 `TessellatorStreamingDrawer.draw(tess)`）。
- **本仓依据**：`T/.../ScopeLensProgram.java:308-309`（`use()` 内部 `GL20.glUseProgram`）、`ScopeLensRenderer.java:385-433`（设统一变量后 `drawDisc`）、`ScopeThermalRenderer.java:421-439`（`GL20.glUseProgram(program)` + 逐缓冲 `BufferUploader.draw`）、`T/.../mcshim/BufferUploader.java:48-88`（`BufferUploader.draw` 最终也是 `Tessellator.startDrawing/addVertex/draw`）。
- **冲突机制**：`TessellatorStreamingDrawer` 在 glsm 里（**源码缺失**），从调用方看它**必然要**在 `draw()` 里绑定自己的 FFP 模拟程序（core profile 里没有固定管线可用）。若是这样 → 我们刚 `glUseProgram(ourProgram)` 就被替换 → 镜片/热掩膜的 shader 效果**静默不生效**（顶点仍画出，见 P0-1 表现）。这与是否开 shader pack 无关（core profile 恒定）。**待实测**：查看 `glGetInteger(GL_CURRENT_PROGRAM)` 在 `drawDisc` 前后是否被改。
- **建议修法**：与 P0-1 第 3 条同一件事 —— 镜片/热掩膜的顶点提交改**自建 VBO+VAO + `glDrawArrays`**（不经过 `Tessellator`），这样 `Tessellator` 拦截对我们无影响。若实测确认 GLSL 程序仍生效（GLSM 尊重当前 program），则**不做**。

### P1-5｜`glGetFloat(GL_PROJECTION_MATRIX/GL_MODELVIEW_MATRIX)` 在 core profile 下不可靠

见 P0-1 机制 ③ 与修法 ②。单独列出是因为它**只影响镜片有没有画出来**（不会崩），实测时表现为「瞄准时什么都没有」，很容易被误判为「镜片资源问题」。

### P1-6｜HUD 缓存会 ASM 早退所有 `RenderGameOverlayEvent` 接收者（含本仓 7 个 HUD 元素）

- **Angelica 依据**：`A/.../loading/fml/compat/transformers/generic/HUDCachingEarlyReturnTransformer.java:19-40`（在订阅方法**开头**插 `if (HUDCachingHooks.shouldReturnEarly()) return;`）；`A/.../hudcaching/HUDCaching.java:302-307`（`shouldReturnEarly()`）；开关 `A/.../config/AngelicaConfig.java:119-130`（`enableHudCaching` 默认 **true**、`hudCachingActive` 默认 **false**）。
- **本仓依据**：`T/.../client/gui/overlay/{GunHudOverlay,HeatBarOverlay,KillAmountOverlay,InteractKeyTextOverlay,DisplayCaseLockHintOverlay}.java`、`T/.../client/event/ClientHitMark.java`、`CrosshairType`/`RenderCrosshairEvent` 使用者。
- **冲突机制**：默认 `hudCachingActive=false`（`HUDCaching.java:168` 直接走原路径）→ **默认无影响**。用户一旦打开 `hudCachingActive`，我们的 HUD 元素会被抓进缓存帧，**弹药数/热量条/击杀提示最多 20 FPS 更新**（`hudCachingFPS` 默认 20），表现为「数字跳动/滞后」。这是 Angelica 的设计取舍，不是本仓 bug。
- **建议修法**：文档写明「弹药数/热量条/击杀提示这类**逐帧动态** HUD 与 Angelica HUD Caching 语义冲突，建议用户保持 `hudCachingActive=false`」；若要更稳，可在我们的 HUD 渲染里检查「本帧是否正在被缓存」——但 Angelica 未提供公开 API，**不做**。

### P1-7｜自定义 `IResourcePack` 让整个 resource domain 退化为串行图集加载

- **Angelica 依据**：`A/.../textures/atlas/AtlasPackPolicy.java:92-151`（只有 `Default/Folder/File/FMLFolder/FMLFileResourcePack` 与内置白名单可信；其它自定义 pack → `eligible=false`）、`:153-171`（日志 `"... is not trusted for parallel texture loading; domains {...} load serially"`，并写入 `atlasObservedPacks`）；`A/.../mixins/early/angelica/textures/MixinTextureMap_ParallelLoad.java:27-51`；`A/.../config/AngelicaConfig.java:28-50`（`enableParallelAtlasDecode/Mipmaps` 默认 true、`atlasTrustedPacks`）。
- **本仓依据**：`T/.../client/sound/GunPackResourcePack.java:120`（自定义 `IResourcePack` 实现）、`:339-362`（`SimpleReloadableResourceManager#reloadResourcePack` 挂载）、`:540-556`（`getInputStream` 每次遍历 `ResourceManager.currentPackSources()`）、`:473-501`（`index()` 懒建 + 缓存）。
- **冲突机制**：**不会崩**，两条影响：① 该 domain（枪包 `tacz` + 若枪包内含 `assets/minecraft/**` 则含 `minecraft`）退出并行图集解码 → 启动慢一点；② 我们的 pack 被**串行**读取 → `getInputStream` 的线程安全性反而不成问题。
  ⚠️ 反例：若枪包**包含 `assets/minecraft/` 资源**，会把整个 `minecraft` domain 拖成串行（默认枪包只有 `assets/tacz/**`，`GunPackResourcePack.java:495` 的 domains 是从索引里收集的）。
- **建议修法**：① 文档写明「可在 `视频设置 > 性能 > Texture loading` 把 `com.tacz.guns.client.sound.GunPackResourcePack` 标为 trusted」（Angelica 给的官方开关，`AtlasLoadPolicy.setTrusted`，`:212-228`）；② 本仓**不要**去自证可信（需要依赖 Angelica 内部类），坚持「不被信任即串行」这条安全默认；③ 若日后发现某个枪包带 `assets/minecraft/**`，提醒作者改命名空间。

### P2-1｜`getFOVModifier` RETURN 抢返回点：Angelica 的 zoom 除算与本仓的手部 fov 覆盖互相旁路

- **Angelica 依据**：`A/.../mixins/early/angelica/zoom/MixinEntityRenderer_Zoom.java:17-21`（`@ModifyReturnValue(method="getFOVModifier", at=@At("RETURN"))`，`original / zoom`）。
- **本仓依据**：`T/.../mixin/client/GameRendererMixin.java:401-407`（`@Inject(method="getFOVModifier", at=@At("RETURN"), cancellable=true)`，`!useFovSetting` 时 `cir.setReturnValue(ITEM_MODEL_FOV)`）。
- **冲突机制**：两者都锚在 `RETURN`。`@Inject` 的 `setReturnValue` 会**立即 return**，而 `@ModifyReturnValue` 改的是 `RETURN` 指令本身 → 谁先谁后决定结果。最可能的两种结果：① 本仓先 return → **Angelica 的 zoom 对手部无效**（世界缩、手不缩，观感撕裂）；② Angelica 先包 → 我们的值被再除一次 zoom（手部 fov 过小）。**待实测**（GLSL 无关，纯 Mixin 顺序）。
- **建议修法**：本仓把「手部 fov 覆盖」从 `RETURN` 改成 **`@ModifyReturnValue`**（MixinExtras 在 1.7.10 的 UniMixins 里可用）或改用 `@ModifyVariable`/`@Redirect` 在计算后、返回前改；语义等价但**不再抢占返回点**，与 Angelica/其它模组可组合。若不想引 MixinExtras，退一步：保留 `@Inject(RETURN)`，但把「是否覆盖」交给配置（默认可关），并在文档里写明与 zoom 不兼容。

### P2-2｜`getFOVModifier` 被当成「手/世界 pass 判定」——Angelica 的 `HandRenderer` 会**额外**调一次

- **Angelica 依据**：`A/net/coderbot/iris/pipeline/HandRenderer.java:58`（`setupGlState` 里 `getFOVModifier(tickDelta, false)`），且这次调用发生在**关卡渲染内部**（`renderSolid`/`renderTranslucent`）。
- **本仓依据**：`T/.../mixin/client/GameRendererMixin.java:360-363`（HEAD 记录 `tacz$useFovSetting`）、`:278-283`（`tacz$postBobHurt()` 用它选手部/世界事件）、`:328-338`（`onBobView` 同）。
- **冲突机制**：开 shader pack 时，世界 pass 中会插入一次 `useFovSetting=false` 的调用 → 之后世界侧的 `hurtCameraEffect`/`setupViewBobbing` 会被判成「手部 pass」，于是抛的是 `RenderItemInHandBobEvent` 而不是 `RenderLevelBobEvent`（订阅者语义漂移）。我们的 `orientCamera` roll（`:454-460`）不受影响（`renderHand` 不调 `orientCamera`）。
- **建议修法**：把标志从「最近一次调用」改成**显式区间**：在 `renderWorld` HEAD 置 `WORLD`、`renderHand` HEAD 置 `HAND`（本仓已有这两个注入点），`getFOVModifier` 只读不写。改动 < 20 行，且去掉对调用顺序的隐式依赖。

### P2-3｜`TileEntity#getRenderBoundingBox` 被 `@Overwrite` 且带缓存

- **Angelica 依据**：`A/.../mixins/early/rendering/MixinTileEntity.java:33-47`（基类重写）、`:86-114`（`angelica$getCachedRenderBoundingBox`：以 `(x,y,z,blockType,blockMetadata)` 为键缓存）。
- **本仓依据**：`T/.../block/entity/{GunDisplayCaseBlockEntity,GunPegboardBlockEntity,GunSmithTableBlockEntity,TargetBlockEntity,StatueBlockEntity}.java` 全部**自己重写** `getRenderBoundingBox()`（虚分派 → 我们的实现优先，**不会被 Angelica 的 Overwrite 顶掉**），例如 `T/.../GunDisplayCaseBlockEntity.java:298-306`（固定 `±2/±3` 盒）。
- **冲突机制**：只有「渲染包围盒随运行时状态变化」的 TE 才会中招（缓存键不含 NBT/内容/朝向字段）。当前 5 个 TE 的盒子都是**常量偏置**，因此**安全**。若日后出现随内容/旋转变化的盒子，会被缓存成旧值 → 该 TESR 被错误剔除。
- **建议修法**：保持盒子「只由坐标决定」；若必须变化，登记到 Angelica 的 `AngelicaConfig.dynamicBoundsTileEntities`（`A/.../config/AngelicaConfig.java:357-362`）——**属于用户侧配置，不要写进本仓代码**。

### P2-4｜`Entity#isInRangeToRenderDist` 被 Sodium 的渲染距离倍率缩放（影响子弹/弹壳实体的可见距离）

- **Angelica 依据**：`A/.../mixins/early/rendering/MixinEntity_RenderDist.java:12-16`（`@ModifyArg`，`d / (mult*mult)`）；`A/.../mixins/early/angelica/optimizations/MixinRenderGlobal_ItemRenderDist.java`（掉落物动态渲染距离，`AngelicaConfig.java:263-272` 默认开、上限 256）。
- **本仓依据**：`T/.../renderer/entity/EntityBulletRenderer.java`、`T/.../client/renderer/external/ExternalShellRenderer.java`、`T/.../entity/**`。
- **冲突机制**：`entityRenderDistanceMultiplier` 玩家可调（默认 1.0 无影响）；掉落物上限 256 只影响 `EntityItem`。子弹类实体若继承自定义 `Entity` 且依赖 `isInRangeToRender3d`，距离会被 `multiplier` 再缩一次 → 远距离子弹「突然消失」。**待实测**（看子弹实体的 `isInRangeToRenderDist` 覆写值）。
- **建议修法**：不改代码；若实测有消失问题，把子弹实体的 `isInRangeToRenderDist` 显式放大一个常数（局部 1 行）。

### P2-5｜资源重载链：`reloadResourcePack` 之后 Angelica 会**同步**等 GLSM checkpoint

- **Angelica 依据**：`A/.../mixins/early/angelica/MixinSimpleReloadableResourceManager.java:16-23`（`reloadResources` RETURN → `loadingCheckpointEvent.requiresSync = true; post`）。
- **本仓依据**：`T/.../client/sound/GunPackResourcePack.java:353`（`reloadResourcePack(INSTANCE)`）、`:359-361` + `:425-448`（重挂后延迟一 tick 调 `TextureManager#onResourceManagerReload`）。
- **冲突机制**：两条链**互不排斥**（`reloadResourcePack` 不触发 `reloadResources`，Angelica 的 checkpoint 只在整包重载时发）。风险点在我们的 `scheduleTextureReload` 会在游戏中触发一次**全量纹理重载**：若撞上 Angelica 的并行图集/`TextureFormatLoader.reload`（`A/.../shaders/MixinTextureManager_ReloadCount.java:21-31`），会有一次明显卡顿 + 可能的纹理闪烁。**待实测（枪包热重载时）**。
- **建议修法**：文档写明「首次进服后重挂枪包会触发一次全量纹理重载，装 Angelica 时可能多一次卡顿」；不需要改代码。

### P2-6｜OptiFine 探测：Angelica 让 `FMLClientHandler#hasOptifine()` 恒 false（本仓**不受影响**）

- **Angelica 依据**：`A/.../mixins/early/angelica/MixinFMLClientHandler.java:17-29`；`A/.../loading/fml/tweakers/IncompatibleModsDisablerTweaker.java:24-37`。
- **本仓依据**：`T/.../compat/optifine/OptifineCompat.java:70-89`（纯反射探 `net.optifine.Config#isShaders()` / `Shaders#shaderPackLoaded`，并兼容 `shadersmod.client.Shaders`）。
- **冲突机制**：无直接冲突；但要注意「**装 Angelica 时 OptiFine 被强制卸载**，`OptifineCompat.isShadersEnabled()` 恒 false 是**正确**的」；真正的光影由 Angelica 提供 → 见 P1-3。
- **建议修法**：在 `OptifineCompat` 的 javadoc 里补一句「Angelica 会禁用 OptiFine；判断光影请用 `OculusCompat`（已覆盖 Angelica）」。

---

## 3. 明确「不冲突」的项（避免后续重复排查）

| 项 | 结论 |
| --- | --- |
| 本仓 `HumanoidModelMixin` / `PlayerModelMixin`（`ModelBiped#setRotationAngles`） | Angelica 只注 `ModelBiped#render` → 无重叠（`A/.../shaders/MixinModelBiped.java:18`） |
| 本仓 `CreativeModeInventoryScreenMixin` / `CreativeContainerAccessor` / `AbstractButtonMixin` | Angelica **完全不碰** `GuiContainerCreative` / `GuiButton`（grep 无 mixin 目标） |
| 本仓 `LocalPlayerMixin`（`EntityPlayerSP#onLivingUpdate`） | Angelica 不碰 `EntityPlayerSP` |
| 本仓 `MouseHandlerMixin`（`Entity#setAngles`） | Angelica 对 `Entity` 只注 `onEntityUpdate` 与 `isInRangeToRender3d` |
| `EntityRenderer#renderWorld` HEAD / `renderHand`(INVOKE `renderOverlays`) / `hurtCameraEffect` HEAD / `setupViewBobbing` HEAD / `orientCamera` RETURN | Angelica 用 `@Surround`/`@Inject`/`@ModifyConstant`/`@WrapWithCondition`，**不与 HEAD/RETURN 注入互斥**；`hurtCameraEffect` 的 `@ModifyConstant(14.0F)` 与我们的 `@ModifyArg(glRotatef ordinal=2)` 打的是两个不同位置 |
| 本仓 `RenderManager#func_147939_a` HEAD/RETURN | Angelica 的两个 `@Redirect` 都在 **INVOKE 点**，不冲突（但该 mixin 目前未登记，见 §4-9） |
| 本仓 `GunPackResourcePack` 与 Angelica 的 `AbstractTexture`/`TextureMap` 启动链 | 枪包贴图是**独立 `SimpleTexture`**、不进图集（`GunPackResourcePack.java:96-113` 的自述），Angelica 的 `AbstractTexture`/图集并行只影响 stitch 过程 |

---

## 4. 实测验证清单（装 Angelica 2.2.42 后必须人工确认的 12 项）

> 判定原则：**先用「关掉 Angelica 功能」复现 → 能定位到是 Angelica 的哪一档开关**。
> 每项的「归属判定」都给到具体日志/开关，避免"看起来像 TACZ 的锅"。

| # | 检查点 | 预期现象（装了 Angelica） | 怎么判断是本仓问题还是 Angelica 问题 |
| --- | --- | --- | --- |
| 1 | **游戏能起来** | 启动日志出现 `Created GL 3.3+ core profile context`（`MixinForgeHooksClient_CoreProfile.java:92/108/132`）；若出现 `Angelica requires an OpenGL 3.3 core profile context` 则直接启动失败 | 启动失败 = 环境/驱动问题，**与本仓无关** |
| 2 | **进入世界不崩** | 无 `NoSuchMethodError` / `InvalidInjectionException` / LWJGL `Function is not supported`（后者说明 GLSM 没覆盖某个 compat 函数，**归属 Angelica**，把栈顶的 GL 调用方法名截图） | 若 `InvalidInjectionException` 指向 `com.tacz.guns.mixin.**` → **本仓 mixin 注入点被 Angelica 改写**；指向 `com.gtnewhorizons.angelica` → Angelica 自身 |
| 3 | **枪模型能看见（第一人称）** | 手持枪、无 shader pack：完全正常 | 若有异常：先 `AngelicaConfig.enableIris=false` 或不开 shader pack 复现一次；同时开/关 shader pack 表现不同 → 走 #4 |
| 4 | **第一人称 + 开 shader pack** | 枪仍在手部、位置/缩放基本正确（`HandRenderer` 走 `getFOVModifier(tickDelta,false)`，本仓对其有 RETURN 覆盖） | 枪**消失** → 检查是否别的模组取消了 Forge `RenderHandEvent`（Iris `canRender` 会因此拒绝画手，见 P0-3 机制 4）；枪**位置错** → 本仓 `BeforeRenderHandEvent` 的 `PoseStack` 与 Iris `setupGlState` 矩阵语义冲突（本仓侧修） |
| 5 | **瞄准（镜片）** | 有镜片圆 + 畸变/色散/边缘暗角；开/关 shader pack 都在 | **镜片完全没画** → 先看日志有没有 `Scope lens ... shader compile failed`（有 = 本仓 shader，见 P0-1/P1-4）；没有日志但没画面 → 用 `glGetInteger(GL_CURRENT_PROGRAM)` / `glGetFloat(GL_MODELVIEW_MATRIX)` 打点（本仓侧修，改 VBO 或改用 PoseStack 矩阵） |
| 6 | **热成像视觉模式** | 镜片内出现热掩膜（铁红/白热/黑热） | **没热掩膜** → 99% 是 P0-2（`main.depthBuffer<=0`）；开 shader pack 时必然发生（`MixinFramebuffer` 换深度纹理）；不开 shader pack 若是也坏，看 `GL_FRAMEBUFFER_COMPLETE` 是否失败 |
| 7 | **光影 shadow pass 不重复绘制** | 开 shader pack 且有影子时，枪口火光/弹壳不会出现在影子贴图上、无「影子翻倍」 | 打开 `AngelicaConfig.enableDebugLogging=true` 看 shadow 相关日志；若本仓粒子在 shadow pass 里被画 → P1-3（`isRenderShadow()` 恒 false），修 `OculusCompat.LOADED` 后复测 |
| 8 | **第三人称手里的枪** | 正常显示、跟着手臂走 | 若玩家模型本体出现「错位/少零件」→ P1-2（`RenderPlayer` 被 `DENIED`，会在日志打 `draws its own geometry alongside model parts`）；**只有性能变差 = 预期** |
| 9 | **展示柜/枪架/靶子/雕像/枪械工作台（TESR）** | 内容完整、旋转/朝向正确、远离时正常剔除 | ⚠️ 先看本仓：`EntityRenderDispatcherMixin` **未登记到 `mixins.tacz.json`**（`R/mixins.tacz.json:12-23`），需要先补上再测热成像实体重绘。TESR 若「贴图变白/变紫黑」→ 查 Angelica 日志有无 `not trusted for parallel texture loading`（P1-7） |
| 10 | **HUD（弹药数/热量条/击杀提示/准星）** | 默认（`hudCachingActive=false`）完全正常 | 数字跳变/滞后 → 用户开了 `hudCachingActive`，关掉即恢复（P1-6，**归属 Angelica 配置**） |
| 11 | **枪包资源（贴图/音效）** | 枪模型贴图正常、开枪有声音 | 紫黑块 → 看 `GunPackResourcePack.logDiagnostics` 的 `probe HIT/MISS`（本仓自带，`GunPackResourcePack.java:397-415`）；全部 MISS = 资源包体系问题（本仓）；HIT 但显示紫黑 = 纹理上传/图集（Angelica） |
| 12 | **缩放（zoom）与瞄准叠加** | 用 Angelica 的 zoom 键（`AngelicaConfig.enableZoom`，默认 true）时，世界与**手部/镜片**缩放一致 | 世界缩、手不缩（或手缩过头）= P2-1（`getFOVModifier` RETURN 抢点）→ 本仓侧修 |

**测试矩阵建议**：`{Angelica 关} × {Angelica 开 + 无 shader pack} × {Angelica 开 + Complementary/BSL 等 shader pack}` 三档，每档跑 1~12。
其中「Angelica 开 + 无 shader pack」这一档最关键：它能区分 P0-1/P1-4（core profile 恒定生效，与 shader pack 无关）和 P0-2/P0-3（只在 shader pack 下出现）。

---

## 5. 结论：要不要写 compat 门面？

### 5.1 结论

**要，但只做 3 个小改动（净新增约 60 行），不新建大 façade、不碰渲染管线结构。**

| 改动 | 位置 | 规模 | 理由 | 是否阻塞上线 |
| --- | --- | --- | --- | --- |
| **① `CompatRegistry` 增加 `ANGELICA = "angelica"`；`OculusCompat.LOADED` 接受 Angelica** | `T/com/tacz/guns/init/CompatRegistry.java:30` 附近；`T/.../compat/oculus/OculusCompat.java:44-45` | ~5 行 | **P1-3**：否则 `isRenderShadow()` 恒 false，Angelica 光影下 shadow pass 里会重复画粒子/热源 | 是（廉价且确定） |
| **② `ScopeThermalRenderer` 的深度附件探测改 GL 查询** | `T/.../client/renderer/scope/ScopeThermalRenderer.java:390-394` + `:463-481` + `:578` | ~30 行 | **P0-2**：`main.depthBuffer > 0` 在 Angelica 下恒假，热成像直接死 | 是 |
| **③ 去掉 `glBindFramebuffer(GL_FRAMEBUFFER, 0)` 硬编码，改为保存/恢复进入时的绑定；`copyScreen` 同样保存/恢复** | `T/.../ScopeThermalRenderer.java:441,578`；`T/.../ScopeLensRenderer.java:515-519` | ~10 行 | 与 Angelica 的 HUD 缓存 / Iris 后处理共存时才不会破坏「谁该是当前 FBO」 | 是（低风险） |

**可选（实测后决定）**：
- **④ 镜片/热掩膜的顶点提交改自建 VBO+VAO**（`ScopeLensRenderer.drawDisc` + `ScopeThermalRenderer` 的 4 个 `BufferUploader.draw`），~60-80 行，只在 `client/renderer/scope/**`。解决 P0-1 的「attribute 0 别名」与 P1-4 的「Tessellator 被接管」。**若 §4-5/§4-6 实测证明镜片 shader 仍生效，则不做。**
- **⑤ `glPushAttrib/glPopAttrib`（3 处）改显式状态保存/恢复**，~20 行/处。这是 core profile 下的健壮性问题（P0-1 机制 2）。若实测无状态泄漏，可延后。
- **⑥ `tacz$useFovSetting` 改显式区间标记**（P2-2）、**`getFOVModifier` 改 `@ModifyReturnValue`**（P2-1），各 < 20 行，纯本仓内部正确性，与 Angelica 版本无关，可顺手做。

### 5.2 不建议做的事（都能换来一时的"看起来好了"，但会把本仓焊死到 Angelica 内部）

| 反模式 | 为什么不做 |
| --- | --- |
| `@Mixin` Iris 的 `HandRenderer` / `ShadowRenderingState` / `GLSM*` | Angelica 内部实现，2.2.42 的 `glsm` 甚至不在源码包里；版本一升就 `NoSuchMethodError`/`InvalidInjectionException` |
| 读 `AngelicaConfig.*` 静态字段 / `iris$depthTextureId` 等 mixin 注入字段 | 同上；且配置项会改名（本仓已有一套「纯反射 + 缺失静默」的 compat 约定，见 AGENTS.md 决策 2） |
| 实现 `com.gtnewhorizons.angelica.api.tesr.TesrMeshProvider` 让 TESR 进它的批处理 | 会把 5 个 TESR 绑到 Angelica 内部 API；与 P1-1 的收益（性能）不成比例 |
| 用 `@Redirect`/`@Overwrite` 去"修正" Angelica 引入的行为 | 两个模组都会在别人的方法上打架，最终的注入顺序不可控 |

### 5.3 一句话交给 lead

> **P0 共 3 条**：① Angelica 强制 **GL 3.3 core + forward-compatible**，本仓镜片/热成像依赖的「`glVertexPointer` → attribute 0 别名 + `glPushAttrib` + `glGetFloat(GL_MODELVIEW_MATRIX)`」全部不再成立（`Tessellator.draw` 还被 Angelica 接管，`A/.../ffp/MixinTessellator_CoreProfile.java:17-23`）；② 开 shader pack 时主 framebuffer 的深度从 renderbuffer 换成 **depth texture**（`A/.../shaders/MixinFramebuffer.java:63-91`），`ScopeThermalRenderer.java:392` 的 `main.depthBuffer > 0` 硬闸让热成像**静默失效**；③ 开 shader pack 时 `EntityRenderer#renderHand` 的 `ItemRenderer#renderItemInFirstPerson` 被**跳过**、手部改在关卡 pass 内由 Iris `HandRenderer` 重绘（`A/.../shaders/MixinEntityRenderer.java:83-87` + `A/net/coderbot/iris/pipeline/HandRenderer.java:127-181`），本仓「世界已画完」的时序假设失效。
> **需要 compat 门面**：需要，但是**小的**——`CompatRegistry` 加 `angelica`、`OculusCompat.LOADED` 接受 Angelica（否则 Angelica 光影 shadow pass 里本仓会重复绘制）、`ScopeThermalRenderer` 改查 GL 深度附件类型 + 不再硬编码绑 0 号 FBO；共约 60 行、3 个文件，**不需要**动渲染管线结构。

---

## 附：本次审查的已知盲区（后续补测用）

1. `A/com/gtnewhorizons/angelica/glsm/**`（`GLStateManager` / `GLSMTextures` / `GLSMMatrices` / `TessellatorStreamingDrawer` / `ModRedirector` / `GLSMAttribStack`）**不在反编译产物里** → 「哪些 compat 函数被模拟」无法从源码确认，只能靠 §4 实测 + 日志。
2. `A/com/gtnewhorizons/angelica/mixins/*.json` 的 `mixins`/`client` 列表**是空的**（只有 `package`/`refmap`/`injectors`），因此「`shaders` 包是否只在 `enableIris` 时应用」「`notfine` 包是否只在 `enableNotFineFeatures` 时应用」无法确认；本次按「默认开启」处理并标注。
3. 未展开审阅的子包（与本仓重叠面小）：`mcpatcherforge/**`（CIT/CTM/CC，只影响原版方块与生物皮肤）、`dynamiclights/**`（只影响光照贴图）、`hudcaching/**` 的 `MixinGuiIngame*`、`celeritas/**` 的区块网格化。
4. `AngelicaConfig` 只有部分字段被引用（`enableEntityBatching` / `enableTESRProviderDispatch` / `enableEntityShadowReplay` / `hudCachingActive` 默认值已确认），其余与本仓无关。
