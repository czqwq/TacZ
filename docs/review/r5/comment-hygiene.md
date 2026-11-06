# r5-E · 注释精简报告

任务：`task-5`。用户抱怨「代码注释太长、无用信息太多」。本报告只记录**注释与空白**的改动；
本次改动**没有修改任何一行代码逻辑、字符串常量、注解、import、方法名、字段名**，也未删除任何方法或字段。

执行者：`r5-comment-trim`。改动范围：`src/main/java`（**未触碰** `api/**` 与
`simplebedrockmodel/v1/molang/**` 中与上游/库逐字对应的 javadoc）。

---

## 1. 统计

统计口径：逐行 `trim()` 后以 `//`、`/*`、`*` 开头的行 = 注释行。全仓 1147 个 `.java`。

| 指标 | 改前 | 改后 | 变化 |
|---|---|---|---|
| 文件数 | 1147 | 1147 | 0 |
| 总行数 | 150 865 | 149 317 | **−1 548** |
| 注释行数 | 34 757 | 33 209 | **−1 548（−4.5 %）** |
| 处理文件数 | — | **25** | — |

> 说明：本次下降 1548 行 = 25 个文件的净删行数（逐文件明细见下表，总和精确相等，说明没有其它文件被动到）。
> 全部改动均为「整行注释删除 / 多行注释合并为少数行」，因此总行数与注释行数下降量相同。

### 逐文件明细（改前 → 改后）

| 文件 | 注释行 | 降幅 | 总行 |
|---|---|---|---|
| `simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler` | 519 → 239 | **−280** | 1198 → 918 |
| `mixin/client/GameRendererMixin` | 348 → 156 | **−192** | 484 → 292 |
| `client/model/RenderHelperCompat` | 285 → 173 | −112 | 601 → 489 |
| `mixin/client/CreativeModeInventoryScreenMixin` | 185 → 82 | −103 | 324 → 221 |
| `client/sound/GunPackResourcePack` | 302 → 200 | −102 | 759 → 657 |
| `client/animation/statemachine/GunAnimationStateContext` | 304 → 209 | −95 | 700 → 605 |
| `client/renderer/item/AnimateGeoItemRenderer` | 240 → 168 | −72 | 706 → 634 |
| `client/event/TooltipEvent` | 176 → 108 | −68 | 429 → 361 |
| `client/renderer/scope/ScopeLensRenderer` | 259 → 196 | −63 | 916 → 853 |
| `client/event/CameraSetupEvent` | 220 → 165 | −55 | 576 → 521 |
| `client/particle/MuzzleParticleManager` | 282 → 230 | −52 | 794 → 742 |
| `client/init/ClientSetupEvent` | 270 → 219 | −51 | 571 → 520 |
| `crafting/GunSmithTableIngredient` | 204 → 159 | −45 | 523 → 478 |
| `init/CommonRegistry` | 139 → 96 | −43 | 201 → 158 |
| `client/renderer/scope/ScopeThermalRenderer` | 231 → 195 | −36 | 921 → 885 |
| `client/gui/components/CreativePackFilters` | 170 → 140 | −30 | 693 → 663 |
| `client/input/GunInputHandler` | 161 → 131 | −30 | 339 → 309 |
| `client/event/FirstPersonRenderGunEvent` | 170 → 142 | −28 | 770 → 742 |
| `resource/GunPackLoader` | 158 → 135 | −23 | 553 → 530 |
| `mcshim/RenderType` | 239 → 219 | −20 | 903 → 883 |
| `mcshim/PacketBuffer` | 156 → 138 | −18 | 460 → 442 |
| `mcshim/GuiGraphics` | 213 → 201 | −12 | 829 → 817 |
| `mcshim/RenderSystem` | 145 → 138 | −7 | 409 → 402 |
| `resource/CommonAssetsManager` | 137 → 131 | −6 | 568 → 562 |
| `client/ClientProxy` | 183 → 178 | −5 | 501 → 496 |

### 验证

- 每批改动后 `lint_files(min_severity=error)`，最终 25 个文件全部 **0 error**。
- 结束前对 5 个改动最大的文件额外跑了 `get_file_problems(errorsOnly=true)`：
  `FirstPersonRenderHandler` / `RenderHelperCompat` / `ScopeLensRenderer` /
  `CreativeModeInventoryScreenMixin` / `TooltipEvent` —— **全部 0 error**。
- 中途发现并修正一处**真实编译错误**：`GunPackLoader` 注释里写了 `\uXXXX`，
  javac 会在**注释里**也处理 Unicode 转义 ⇒ `非法 Unicode 转义序列`。已改为分写的
  `{@code \n}`、`{@code \t}`、`{@code uXXXX}`。**这条要记一下**：以后不要在注释里写裸 `\u`。

---

## 2. 改前 / 改后样例

### 样例 1：`mixin/client/GameRendererMixin` 类 javadoc（348 → 156 注释行）

改前（节选，全文 140 行，含一张 3 列 × 6 行 HTML 表和一张 4 列 × 3 行「替代钩子契约」表）：

```java
/**
 * 搬运自上游 1.20.1 {@code mixin/client/GameRendererMixin.java}。
 *
 * <p>
 * <b>上游用途</b>：① 镜片（scope lens）渲染的两个时机；② 接管受伤/走路的视角摇晃 …
 *
 * <p>
 * <b>1.7.10 等价改写</b>（对照 build/rfg/…/EntityRenderer.java）：
 * <table border="1">
 * <tr><th>上游目标/注入点</th><th>1.7.10 等价</th><th>状态</th></tr>
 * … （6 行 × 3 列）
 * </table>
 *
 * <p>
 * <b>降级 1：{@code ScopeLensRenderer} 相关的三个注入点全部注释。</b>
 * …（10 行论证）
 * <b>替代钩子 · 契约（给 client-render，可直接接线）</b>：
 * <table border="1"> … （3 行 × 4 列，每格 3~4 行）</table>
 * <p>
 * <b>{@code GunHurtBobTweak} 分支 —— ✅ 已接线（2026-11 放开）</b>：…（含一段 {@code <pre>} 代码示例）
 * <p>
 * <b>生效状态</b>：①③④⑤⑥⑦ 生效；② … <b>有意保持注释</b>，理由见 ② 上方的注释块…
 */
```

改后（全文 15 行，映射逐条压成一行、`file:line` 全部保留）：

```java
/**
 * 搬运自上游 1.20.1 {@code mixin/client/GameRendererMixin.java}，对应 1.7.10 的 EntityRenderer。
 *
 * <p>
 * 注入点对照（上游 → 1.7.10，均已实现）：{@code renderLevel}HEAD → {@code renderWorld}HEAD
 * （EntityRenderer.java:1183）；镜片 → {@code renderHand} 里 {@code ItemRenderer#renderOverlays(F)V}
 * 之前（:805）；{@code bobHurt}HEAD → {@code hurtCameraEffect}HEAD（:507）；…
 * {@code getFOVModifier(float,boolean)}HEAD（:473）。
 *
 * <p>
 * 唯一降级：上游 {@code renderLevel} 的 {@code ProfilerFiller.popPush} ordinal=2（shader 镜片）在
 * 1.7.10 没有对应段名（只有 {@code mcProfiler.endStartSection}，段名也不同），无法复刻 ⇒
 * {@code tacz$drawShaderScopeLenses} 保持注释。它也不能与 {@code tacz$drawVanillaScopeLenses} 同时启用：
 * {@code ScopeLensRenderer.render()} 不清理 {@code LENSES}（只有 {@code beginFrame()} 清理，
 * ScopeLensRenderer.java:131），会双绘 + 多一次 copyScreen。
 *
 * <p>
 * 新增 ⑦ 相机 roll（1.7.10 无相机角度事件）：见 {@link #tacz$applyCameraRoll}。
 */
```

### 样例 2：`client/model/RenderHelperCompat#applyGuiSlotScale` javadoc（63 行 → 20 行）

改前（含「第一轮实现 / 第二轮修正」的逐轴复算过程、6 步 `<ol>` 推导、「回滚说明」段落）：

```java
/**
 * <b>GUI 槽位缩放（移植新增，必须有）</b>：…
 * <p>依据：
 * <ul><li>上游 1.20 … <li>1.7.10 的等价入口 ForgeHooksClient#renderInventoryItem 的非 3D 分支 …
 * （整个分支无 glScalef/glRotatef）… </li></ul>
 * <h2>Y 方向：{@code scale(16, -16, 16)} + {@code translate(0, -1, 0)}（第二轮修正）</h2>
 * <p>第一轮实现只写了 {@code scale(16, 16, 16)}，位置虽然正确，但<b>上下颠倒</b>。逐轴复算：
 * <ol><li>SlotModel（SlotModel.java:32-38）… </li><li>BedrockCubePerFace（:47-57）… </li>
 * <li>BedrockPolygon（:23-26）按固定顶点序写 uv … </li><li>内层矩阵 = … </li>
 * <li>若外层只有 scale(16,16,16) … </li><li>改成 scale(16,−16,16) 后 … </li></ol>
 * <p><b>回滚说明</b>：若实机截图显示图标本来就该颠倒 … 单点回滚。
 * <p><b>作用范围</b>：只在 ItemRenderType.INVENTORY 分支调用 …
 */
```

改后：

```java
/**
 * <b>GUI 槽位缩放（移植新增，必须有）</b>：{@code INVENTORY} 分支要把 SlotModel 的槽位四边形放大 16 倍，
 * 才能铺满 16×16 的图标格；否则枪械/配件/弹药/工作台的背包图标退化成一个 1 像素的点。
 *
 * <p>
 * 依据：上游 1.20 的 {@code ItemRenderer#renderGuiItem} 施加 {@code scale(16, -16, 16)}；1.7.10 等价入口
 * {@code ForgeHooksClient#renderInventoryItem}（ForgeHooksClient.java:187-205）只做
 * {@code glDisable(GL_LIGHTING)} + {@code glTranslatef} + {@code renderItem(INVENTORY, …)}，<b>无任何缩放</b>。
 *
 * <p>
 * Y 方向必须 {@code scale(16, -16, 16)} + {@code translate(0, -1, 0)}：1.7.10 GUI 空间 {@code +Y 向下}，
 * 原版图标映射（RenderItem#renderIcon，RenderItem.java:756-765）要求 {@code v=0} 落在格子顶边；
 * 只写 {@code scale(16,16,16)} 会把图标上下颠倒，只写 −16 则整块落到 {@code y ∈ [-16,0]}。
 * 单点回滚：改回 {@code scale(16,16,16)} 并去掉 translate。
 *
 * <p>
 * 作用范围仅 {@code ItemRenderType.INVENTORY}：唯一派发链是
 * {@code ForgeHooksClient#renderInventoryItem}（:183/:202）← {@code RenderItem#renderItemAndEffectIntoGUI}
 * （:583），调用方全是 GUI/HUD，不含世界内展示框 / 掉落物 / 第三人称手持，16× 缩放不会外溢。
 */
```

### 样例 3：`client/event/CameraSetupEvent` 相机偏移记账字段（33 行 → 14 行）

改前：`【r4-03（现象 #2「换弹/切枪时屏幕剧烈抖动」）· 自愈式记账】/【上游为什么不抖】/【1.7.10 为什么必须绕】/
【旧的记账方式（r3-07）有致命缺陷】/【新写法不依赖配对】` 五段式，共 33 行。

改后：

```java
    /**
     * <b>当前仍挂在 {@code player.rotationYaw/rotationPitch} 上的相机动画偏移</b>（= 我们加上去、还没扣回的量）。
     *
     * <p>
     * 1.7.10 没有相机角度事件，唯一能改相机的通道是实体朝向（{@code EntityRenderer#orientCamera} 读
     * {@code renderViewEntity.rotationYaw/Pitch}），因此只能在 {@code RenderTickEvent.START} 加、
     * {@code END} 减。上游用 {@code ViewportEvent.ComputeCameraAngles}，每帧一次算完立刻消费、不改实体朝向，
     * 故没有跨事件配对状态。
     *
     * <p>
     * <b>记账必须自愈</b>：不能依赖「同一帧 START/END 严格配对一次」—— END 因异常/提前 return 未执行、同帧两次
     * START 只配一次 END，偏移就会永久留在 rotation 上并逐帧叠加 ⇒「换弹/切枪时屏幕剧烈抖动」（抖的是实体朝向，
     * 所以第三人称一样抖）。故 END 无条件扣回记录中的偏移（幂等），START 开头先扣回上一帧可能残留的偏移再计算
     * 本帧新偏移 ⇒ 偏移永不跨帧累积，最坏情况只是本帧少一次动画。
     */
```

---

## 3. 被删除的 HTML 映射表（内容留档）

按 Lead 裁决：`<table border="1">` 整表删除，只在代码里留 1~3 行要点，**对应关系在此留档**。

### 3.1 `mixin/client/GameRendererMixin` —— 上游注入点 → 1.7.10 注入点

| 上游（1.20.1 `GameRenderer`） | 1.7.10（`EntityRenderer`） |
|---|---|
| `renderLevel` HEAD → `ScopeLensRenderer.beginFrame()` | `renderWorld(float,long)` HEAD（:1183） |
| `renderLevel` 的 `ProfilerFiller.popPush` ordinal=2 → `ScopeLensRenderer.render()` | **无对应**（无同名 profiler 段，`mcProfiler.endStartSection` 段名不同）→ 降级 |
| `renderItemInHand` 的 `ScreenEffectRenderer.renderScreenEffect` | `renderHand(float,int)` 里 `ItemRenderer#renderOverlays(F)V` INVOKE 之前（:805） |
| `bobHurt` HEAD cancellable | `hurtCameraEffect(float)` HEAD（:507） |
| `bobHurt` 里 `Axis;rotationDegrees(F)` ordinal=2 的 `@ModifyArg` | `hurtCameraEffect` 第 3 个 `GL11.glRotatef`（:525 `-f1 * 14.0F`），`@ModifyArg(index = 0)` |
| `bobView` HEAD cancellable | `setupViewBobbing(float)` HEAD（:533） |
| `getFov` HEAD（记 `pUseFovSetting`） | `getFOVModifier(float,boolean)` HEAD（:473） |

`ScopeLensRenderer` 三个注入点的落点契约（表格 3 行 × 4 列，已并入类 javadoc 的散文）：
① `renderWorld` HEAD → `ScopeLensRenderer.beginFrame()`（每帧开始收集镜片几何、清空上一帧 buffer）；
② `renderHand` HEAD → `ScopeLensRenderer.render()`（shader 镜片，**当前有意保持注释**）；
③ `renderHand` 内 `renderOverlays` INVOKE 之前 → `ScopeLensRenderer.render()`（无 shader 回退路径，必须先于屏幕特效）。

### 3.2 `client/renderer/scope/ScopeLensRenderer` —— 上游 API → 1.7.10 API

| 上游 | 1.7.10 |
|---|---|
| `RegisterShadersEvent` + `ShaderInstance` | `init()` + `ScopeLensProgram`（GL20 编译 GLSL 120） |
| `Minecraft#getMainRenderTarget()` / `TextureTarget` | `Minecraft#getFramebuffer()`（`Framebuffer`）+ 同尺寸 copy 纹理（`glCopyTexSubImage2D`） |
| `RenderSystem#setProjectionMatrix/applyModelViewMatrix` | `glMatrixMode/glLoadIdentity/glOrtho`（NDC 全屏镜片，投影退化为单位正交） |
| `BufferUploader#drawWithShader` | `BufferUploader#draw(BufferBuilder.RenderedBuffer)` |
| `RenderSystem#stencilFunc/stencilOp` | `GL11.glStencilFunc/glStencilOp` |
| `GlStateManager#_glCopyTexSubImage2D` | `GL11.glCopyTexSubImage2D` |
| `Util.getMillis()` | `System.currentTimeMillis()` |

### 3.3 `client/renderer/scope/ScopeThermalRenderer`

| 上游 | 1.7.10 |
|---|---|
| `TextureTarget(width, height, false, ON_OSX)` | 自建 FBO：`glGenFramebuffers` + RGBA8 颜色纹理挂 `COLOR_ATTACHMENT0` |
| 借场景深度（`glFramebufferAttachmentParameteri` + `glFramebufferRenderbuffer/glFramebufferTexture`） | 同一套调用可用；主深度附件是 renderbuffer（`Framebuffer.java:108` 用 `OpenGlHelper.func_153185_f()` 生成）⇒ 走 `glFramebufferRenderbuffer` 分支 |
| `ShaderInstance scope_thermal_mask` | `ScopeLensProgram#compileRaw` 编译 GLSL 120 |
| `RenderLevelStageEvent.Stage.AFTER_PARTICLES` | 无该事件 ⇒ `RenderWorldLastEvent` 调 `render()` |
| `VertexMultiConsumer.create(...)` | 内部类 `TeeVertexConsumer` |

### 3.4 `mixin/client/CreativeModeInventoryScreenMixin` —— 上游方法 → `GuiContainerCreative`

| 上游 | 1.7.10 |
|---|---|
| `CreativeModeInventoryScreen` | `GuiContainerCreative` |
| `CreativeModeTab selectedTab`（字段） | `private static int selectedTabIndex`（:43，用 `CreativeTabs.creativeTabArray[index]` 反查） |
| `float scrollOffs` | `private float currentScroll`（:45） |
| `init` TAIL | `initGui` TAIL（:258） |
| `selectTab` TAIL | `setCurrentCreativeTab(CreativeTabs)` TAIL（:500） |
| `refreshCurrentTabContents(Collection)` TAIL | **无对应**（`updateFilteredItems` 形参是包私有内部类，:370/:1003）→ 由 `drawScreen` HEAD 每帧对账覆盖，最多推迟 1 帧 |
| `render(GuiGraphics,...)` HEAD | `drawScreen(int,int,float)` HEAD（:634） |
| `render(...)` 画 widget 层 | `drawScreen` TAIL + 自绘 |
| widget 点击派发 | `mouseClicked(int,int,int)` HEAD（:440）+ 自派发 |
| `mouseScrolled(...)` HEAD cancellable | `handleMouseInput()` HEAD cancellable（:596，读 `Mouse.getEventDWheel()`） |
| `() -> scrollOffs = 0` | 本 Mixin `implements Runnable` |

### 3.5 `client/particle/MuzzleParticleManager` —— 上游类型 → 1.7.10

`InteractionHand`/`PoseStack`/`LightTexture` 走 mcshim（LightTexture 打包布局是原生的 `(sky << 20) | (block << 4)`）；
`ResourceLocation` → `net.minecraft.util.ResourceLocation`；`ItemStack.EMPTY` 语义 = `null`；
fastutil `Pair#right()` → commons-lang3 `Pair#getRight()`；
`options.getCameraType().isFirstPerson()` → `gameSettings.thirdPersonView == 0`；
`Minecraft#getInstance()` → `Minecraft.getMinecraft()`。

### 3.6 `client/event/CameraSetupEvent` —— 上游事件 → 1.7.10

| 上游 | 1.7.10 |
|---|---|
| `ViewportEvent.ComputeCameraAngles` | **不存在** ⇒ `TickEvent.RenderTickEvent` + 直接改 `renderViewEntity.rotationPitch/Yaw` |
| `ViewportEvent.ComputeFov`（世界 FOV） | `FOVUpdateEvent`（世界投影走 `getFOVModifier(partial,true)`，:673） |
| `ComputeFovModifierEvent` | 同一个 `FOVUpdateEvent`（`newfov` 即倍率），`priority = HIGHEST` 复刻「modifier 早于 fov」 |
| `ViewportEvent.ComputeFov`（手部，`usedConfiguredFov()==false`） | **无事件** ⇒ 结果放 `ITEM_MODEL_FOV`，由 `GameRendererMixin` 覆盖硬编码 70°（:770/:482） |
| `BeforeRenderHandEvent` | 同名事件，mixin 在 `ItemRenderer#renderItemInFirstPerson` HEAD 抛出 |

### 3.7 `client/event/TooltipEvent` —— 逐物品类型

枪械 `AbstractGunItem.java:398-411` → `ClientGunTooltip`；配件 `AttachmentItem.java:99-101` →
`ClientAttachmentItemTooltip`；弹药 `AmmoItem.java:91-110 appendHoverText`（本就是纯文本）→ `ammoHoverText`；
弹药盒 `AmmoBoxItem.java:259-274` → `ClientAmmoBoxTooltip`；弹药库 `AmmoStorageBlockItem.java:57-85` →
`ClientAmmoStorageTooltip`；其它方块物品 `GunSmithTableItem.java:92-96` → `ClientBlockItemTooltip`。
富信息块插在第 1 个下标（对应上游 `GuiGraphics#gatherTooltipComponents` 的 `list.add(1, …)`）。

### 3.8 `mcshim/GuiGraphics` / `mcshim/RenderType` / `mcshim/PacketBuffer` / `mcshim/RenderSystem`

这四个 mcshim 类的「行为差异」逐条列表已压成单段散文，内容未丢失（差异条目仍在，只是不再用
`<h2>/<ul>` 展开）。要点保留：`setColor` 无全局 tint、`drawString` 取整、`FormattedCharSequence` → `IChatComponent`、
`blitNineSliced` 10 参降级、`renderTooltip` 自实现；`RenderType` 无 shader / 无 `RenderStateShard` / 无 FBO /
无面排序 / `cull` 恒 false（已登记平台偏离）；`PacketBuffer` 的 NBT 走 gzip+short（**线上格式与 1.20 不同**）、
`ItemStack` 编码、`BlockPos` → `int[3]`、`readMap` 返回 `HashMap`；`RenderSystem` 是纯直通门面、矩阵读写会真实改变管线。

### 3.9 其它被删的表格/代码块

- `client/sound/GunPackResourcePack`：类 javadoc 4 个 `<h2>` 段 + 无表格，已压成分段散文（方法映射保留）。
- `resource/GunPackLoader`：类 javadoc 的 `<ol>`「调用时机」→ 一行；`injectAllGunPackLanguages` 里的
  `LanguageManager` 三段代码示例（`<pre>`）→ 一行 `mergeLanguageTable` 语义说明（`file:line` 保留）。
- `crafting/GunSmithTableIngredient`：类 javadoc 里的上游类签名 `<pre>` 代码块 → 一行
  「`Ingredient ingredient` + `int count`」。
- `client/init/ClientSetupEvent`：上游 7 个事件 → 1.7.10 落点的 6 行 × 2 列表格 → 散文（见 §3.10 留档）。
- `client/gui/components/CreativePackFilters`：`containerAccessProvider` 注入示例 `<pre>` 代码块 → 一行散文。
- `client/input/GunInputHandler`、`client/ClientProxy`、`resource/CommonAssetsManager`、`init/CommonRegistry`、
  `client/event/FirstPersonRenderGunEvent`、`client/renderer/item/AnimateGeoItemRenderer`：`<ul>/<ol>` 列表 → 散文。

### 3.10 `client/init/ClientSetupEvent` —— 上游事件 → 1.7.10 落点（留档）

| 上游事件 | 1.7.10 落点 |
|---|---|
| `RegisterShadersEvent` | 无该事件 ⇒ 镜片/热成像着色器由 `ScopeLensRenderer#init()` / `ScopeThermalRenderer#init()` 在 `onClientSetup()` 显式编译 |
| `RegisterKeyMappingsEvent` | `GunInputHandler#registerKeyMappings()`（`ClientRegistry.registerKeyBinding`） |
| `RegisterClientTooltipComponentFactoriesEvent` | 无 tooltip 组件工厂 ⇒ `ItemTooltipEvent` + 手工绘制 |
| `RegisterGuiOverlaysEvent` | `RenderGameOverlayEvent.Post` 订阅（`onRenderOverlay`） |
| `FMLClientSetupEvent` | `onClientSetup()`（由 `ClientProxy.init` 调用） |
| `RegisterClientReloadListenersEvent` | `IResourceManagerReloadListener` + `TextureStitchEvent.Post`（`client/event/ReloadResourceEvent`） |

---

## 4. 保留清单（刻意没删，及理由）

| 类别 | 具体内容 | 为什么必须留 |
|---|---|---|
| **上游依据** | 所有 `tmp/TACZ-1.20.1/…`、`build/rfg/minecraft-src/… file:line`、`上游 Xxx.java:N-M` | AGENTS.md 强制要求；已压到「一行一点」但一处未删 |
| **会崩的说明** | ① `@SubscribeEvent` **必须是实例方法**（`ASMEventHandler` 用 `INVOKEVIRTUAL` ⇒ 静态处理器抛 `IncompatibleClassChangeError` 且被 `EventBus#post` 吞掉）——保留在 6 个类（`FirstPersonRenderHandler`、`GunPackResourcePack`、`TooltipEvent`、`CameraSetupEvent`、`FirstPersonRenderGunEvent`、`CommonRegistry`）；② `EventBus#register(Class)` 在 1.7.10 **什么都不会注册**；③ Mixin `@Shadow` **不遍历父类**（`InvalidMixinException` ⇒ 启动崩）；④ Mixin 类内**不允许嵌套/匿名类**（`Nesting` 与 `compatibilityLevel: JAVA_8` 冲突 ⇒ 启动崩） | 改错就崩，且静态审查看不出来 |
| **Mixin 注入目标与描述符** | `GameRendererMixin` 的 `@At(target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2, remap = false)` + **为何 `remap = false` 必需**；`CreativeModeInventoryScreenMixin` 的 `initGui`/`setCurrentCreativeTab`/`drawScreen`/`mouseClicked`/`handleMouseInput` 行号；`CallbackInfoReturnable<Float>` 必需性 | 描述符/ordinal 错则注入失败，`remap=false` 错则 AP 直接报错 |
| **GL 状态成对** | `FirstPersonRenderHandler` 的 `glPushMatrix/glPopMatrix` + lightmap on/off + 投影矩阵 push/pop；`RenderHelperCompat` 的 `pushToGL/popFromGL`（finally 成对）；`ScopeLensRenderer` 的线程规则（**任何 GL 调用必须在渲染线程，否则 LWJGL2 native abort 整个 JVM**，附断线 Netty 线程事故） | 改错就崩/闪退 |
| **事件总线归属** | FML bus（`TickEvent`/`PlayerEvent`/`FMLNetworkEvent`）vs `MinecraftForge.EVENT_BUS`（渲染/实体事件）——`ClientProxy`、`FirstPersonRenderHandler`、`ClientSetupEvent` 各留一句 | 注册错总线 = 处理器不执行 |
| **平台差异一句话** | 每个类的「1.7.10 无 X → 用 Y」（如无 `RenderSystem`/`PoseStack`/`RenderType`/`ItemDisplayContext`/datapack/`DistExecutor`/`Coroutine`…） | AGENTS.md 要求记录差异 |
| **可行动 TODO** | `TODO(L2)`、`TODO(port, 待评估)`、`TODO(runtime-verify)`（含「缺什么、在哪接」） | 后续移植的接续点 |
| **线上格式不可改** | `PacketBuffer` 的 NBT gzip+short、`ItemStack` 编码、`BlockPos` 三 int | 改了就两侧不兼容 |
| **YSM 契约相关** | `api/**` 的类/签名说明、`FirstPersonRenderHandler#registerRenderer` 等对外入口的语义 | YSM 编译期直接调用 |
| **单文件开关/回滚点** | `NEAR_PLANE = 0.01F`、`ENABLE_EQUIPPED_PAPER_FRAME_FIX`、`MIN_HAND_ANCHOR_DISTANCE`、`RENDER_FIRST_PERSON_VANILLA_ARM`、`DEBUG_RENDER` 的「置 X 即单点回滚/关闭」 | 实机调试必需 |
| **类 javadoc 第一句** | 全部 25 个文件保留 | 说明这个类干什么 |
| **未动** | `api/**`（`GltfConstants` 305、`AccessorDatas` 260、`IGun` 222、`AbstractGunItem` 187、`Accessor*Data` 各 154、`AnimationStateContext` 151、`GunProperties` 117、`IGunOperator` 113 等）与 `simplebedrockmodel/v1/molang/**`（`MochaEngine` 297、`TokenKind` 171、`MolangLexer` 160、`MolangParser` 140、`ExpressionVisitor` 131 等） | 与上游 1.20.1 / 库逐字对应，属保真内容 |

---

## 5. 删掉的可疑内容（请 Lead 复核）

以下是我删掉、但**理论上可能有人需要**的内容。若你认为哪条要恢复，按此清单回滚即可。

1. **`ScopeLensRenderer`「Angelica 三条失效」的完整证据链**（`A/.../MixinForgeHooksClient_CoreProfile.java:66,143`、
   `A/.../ffp/MixinTessellator_CoreProfile.java:17-23` 等 Angelica 源码行号）。我保留了「三条失效 + 对策」，
   删掉了 Angelica 侧的行号。**风险**：日后接 Angelica 兼容要重新查这些行号。
2. **`GameRendererMixin` 里 `ScopeLensRenderer` 三个注入点的「替代钩子契约」表格**（每格写着「该处必须调什么、
   为什么是这个位置」）。已压成散文 + §3.1 留档，但表格里那句「若要画在屏幕叠加层之上，改投
   `RenderGameOverlayEvent.Post(ElementType.ALL)`」我**没保留**（属备选方案论证）。
3. **`RenderHelperCompat#applyGuiSlotScale` 的逐轴复算推导**（6 步，含 `BedrockCubePerFace.java:47-57`、
   `BedrockPolygon.java:23-26`、`FaceItem.java:15-20` 等行号）。保留结论与单点回滚，
   **删掉了推导过程**。风险：若日后图标又颠倒，需要重新推导。
4. **`TooltipEvent` 的「逐物品类型产出的行」明细**（如枪械 tooltip 会出「描述/弹药名/弹药数/等级/枪械类型/
   伤害/穿甲%/爆头倍率/移速惩罚/改装提示/枪包名」11 行）。保留了「上游来源 → 1.7.10 实现」映射，
   **删掉了产出行清单**。风险：实机回归清单要重新整理。
5. **`GunSmithTableIngredient` 里的「为什么这 11 条配方被跳过」的逐条枚举**（`ammo_mod_fmj/he/hp/i/
   overpressure_recoil/slug`…）。保留了计数与两类原因，删掉了逐 id 列表。
6. **`MuzzleParticleManager` 的「触发链路」三点 + `ClientSetupEvent` 的 7 事件表**：已压成散文，
   **实质性内容未丢**，但阅读性从「列表」变成「长句」——如果你更喜欢列表形式可以让我改回来（不增加行数太多）。
7. **`FirstPersonRenderHandler` 里「修复前后的三现象」编号清单**（收枪过渡永不完成 / 粒子冻结 / 世界粒子同源冻结）。
   保留「nowMillis/nowNanos 恒 0 ⇒ 收枪过渡不完成、粒子曲线冻结」一句，删掉了分现象编号与
   `getActiveAnimationInstance()` 的调用链推理。
8. **`r2/r3/r4/r5` 审查编号本身**：全部删除（`r4-02`、`r3-07`、`r3-01`、`r4-10` 等）。这些编号在 `docs/review/r*`
   里有对应文档，代码里不再出现。

### 5.1 未完成项（下一轮可做）

`r2/r3/r4/r5` 审查编号在**另外 44 个文件**里仍有 1~3 行残留（多为「r4-10 诊断」「r3-07 实机 ICCE 修复」
「r3-05 审计发现的 P2」这类标签）。已扫描定位，清单如下（按剩余条数排序）：

`FirstPersonRenderGunEvent`（本轮已清 2 处，余 5）、`GunRefitScreen`（4）、`ClientSetupEvent`（余 3）、
`BedrockAnimatedModel`（3）、`BedrockGunModel`（3）、`HumanoidOffhandRender`（3）、`ScopeLensRenderer`（2）、
`LeftHandRender`、`RightHandRender`、`GunItemRendererWrapper`、`ScreenItemVboContext`、`MuzzleParticleManager`、
`AnimateGeoItemRenderer`、`SoundPlayManager`、`ClientAmmoCount`、`LootInjectionEvent`、`NetworkHandler`、
`ItemInHandLayerMixin`、`MinecraftAttackMixin`、`LivingEntityMixin`、`LivingEntityKnockbackMixin`、
`ClientMessageLaserColor`、`ClientMessageRefitGun`、`ClientMessageInstallVirtualAttachment`、
`ClientMessageSetAttachmentVariant`、`ClientMessageSetSlotAdapter`、`ClientMessageUnloadAttachment`、
`RefitDebug`、`OreDictionaryFallback`、`EntityKineticBullet`、`ShooterDataHolder`、`ModRecipe`、
`GunRefitScreen`、`ThirdPersonManager`、`ParticleEmitterInstance`、`BedrockAttachmentModel` 等。

这些都**不删技术内容**、只是把 `rN-xx` 标签去掉，属机械清理；建议下一轮或由 Lead 统一处理
（每处 1 行，风险极低）。

---

## 6. 方法学备注

- 只用 `read` + `edit`（**没有**用 `Set-Content`/管道整文件重写，避免历史事故）。
- 每批后 `lint_files(error)`；结束前 5 个最大文件 `get_file_problems(errorsOnly=true)`。
- 中途 1 次自伤：`edit` 的 `old_string` 带尾随换行导致两行合并（`shouldLockVanilla` 方法签名与 `return` 同行），
  已立即发现并修复。**教训：`old_string`/`new_string` 的首尾换行必须一致。**
- 中途 1 次真编译错误：注释里的 `\uXXXX`（javac 在注释里也处理 Unicode 转义），已修。

---

# 第二轮（bounded）

按 Lead 裁决只做两块：**A. 再压缩 4 个「差异清单型」文件**；**B. `rN-xx` 审查轮次编号机械清理**。

## 7. 第二轮统计

| 指标 | 第一轮末 | 第二轮末 | 本轮变化 |
|---|---|---|---|
| 注释行 | 33 209 | **33 001** | **−208（−0.6 %）** |
| 总行数 | 149 317 | 149 109 | −208 |

相对最初基线：注释行 **34 757 → 33 001（−1 756，−5.1 %）**。
第二轮触碰 36 个文件（4 个 A 组 + 27 个 rN 清理 + 重叠加集），全部 `lint_files(error)=0`。

### 7.1 A 组：4 个文件的实际降幅

| 文件 | 本轮起 | 本轮末 | 降幅 | 30 % 目标 |
|---|---|---|---|---|
| `client/renderer/scope/ScopeLensRenderer` | 196 | **139** | **−29 %** | 接近（差 1 点） |
| `mcshim/GuiGraphics` | 201 | **153** | **−24 %** | 未达 |
| `client/particle/MuzzleParticleManager` | 230 | **176** | **−23 %** | 未达 |
| `mcshim/RenderType` | 219 | **170** | **−22 %** | 未达 |

**为什么没到 30~50 %（如实说明）**：这 4 个文件剩下的注释已经不是「论证 / 历史 / 清单」形态，而是：

1. **逐方法的「对应 1.20 Xxx」映射行**（`RenderType`/`GuiGraphics` 各约 90~100 行）。每行 1~2 行、是每个 API 的唯一
   说明；删掉后读代码不知道这个方法对应上游哪个重载 —— 按「保留上游依据」规则必须留。
2. **`GuiGraphics` 的 `@param` 块**（11 参 / 9 参重载）。这些不是「复述参数名」而是单位与语义（如「源图 u 起点
   （像素）」）；已把同义的 `width`/`uWidth` 合并成一条，省 3 行，再合并就丢语义。
3. 第一轮已压过一轮的结论句，再压会丢 `file:line` 或结论本身。

若还要再降，只能**删掉「对应 1.20 Xxx」映射行**（每文件可再省 60~90 行），但那等于去掉上游依据，与保留清单冲突。

> **Lead 裁决（已生效）**：到此为止，**不删映射行** —— 那批「逐方法对应 1.20 Xxx」的单行说明是该 shim API 的唯一文档，
> 属于「上游依据」，与保留清单一致。**A 组按现状收工**：`ScopeLensRenderer` 139 / `GuiGraphics` 153（+1 行复核补回）/
> `MuzzleParticleManager` 176 / `RenderType` 170。

### 7.2 B 组：`rN-xx` 标签清理 —— 已完成 100 %

清理前全仓 **40 处 / 27 文件**，清理后 **0 处**（正则 `r2-\d\d|r3-\d\d|r4-\d\d|r5-\d\d` 全仓 0 命中）。样例：

| 改前 | 改后 |
|---|---|
| `// r4-10 诊断：每次 initGui（…）后的最终状态。` | `// [TACZ-DBG] 每次 initGui（…）后的最终状态。` |
| `// 激光颜色「默认色」解析器（r3-05 审计发现的真缺口 P2）。` | `// 激光颜色「默认色」解析器。` |
| `* <b>必须是 {@code public} 无参构造器</b>（r3-07 实机 ICCE 修复）。` | `* <b>必须是 {@code public} 无参构造器</b>（否则实机 ICE）。` |
| `* <b>r4-10 诊断</b>：每个 {@code return} 点有 … 日志` | `* <b>诊断</b>：每个 {@code return} 点有 … 日志` |
| `* <b>生效状态</b>：✅ 已接线（r3-01）。` | `* <b>生效状态</b>：✅ 已接线。` |
| `// ⇒ 这正是 r4-03 现象 #2「换弹/切枪时…」的根因修复：` | `// ⇒ 这正是「换弹/切枪时…」的根因修复：` |

涉及文件（27）：`CameraSetupEvent`、`FirstPersonRenderGunEvent`(5)、`GunRefitScreen`(3)、`ClientSetupEvent`(2)、
`GunInputHandler`、`BedrockAnimatedModel`(2)、`BedrockGunModel`、`LeftHandRender`、`RightHandRender`、
`GunItemRendererWrapper`(2)、`ScreenItemVboContext`、`HumanoidOffhandRender`(2)、`ScopeThermalRenderer`、
`AnimateGeoItemRenderer`、`ClientIndexManager`、`PackInfoText`、`RenderType`、`ItemInHandLayerMixin`、
`MinecraftAttackMixin`、`LivingEntityKnockbackMixin`、`NetworkHandler`、`ClientMessageInstallVirtualAttachment`、
`ClientMessageLaserColor`、`ClientMessageRefitGun`(2)、`ClientMessageSetAttachmentVariant`、
`ClientMessageSetSlotAdapter`、`ClientMessageUnloadAttachment`、`RefitDebug`、`SlotAdapterHelper`、`GunTime`。

**随编号一起去掉的 2 处审查文档引用**（不属上游依据，此处留档）：`GunRefitScreen` 的配件过滤判读表 → 
`docs/review/r4/03-refit.md`；`PackInfoText` 的 gson 行为实测结论 → `i18n-icon r2-07b §6.5`。

## 8. 第二轮保留 / 复核

- **未碰**：`api/**`、`simplebedrockmodel/v1/molang/**`、第一轮 §4 保留清单的全部内容。
- A 组改动**只删「次要差异条目 / 推导过程 / 重复结论」**，逐条确认保留了：GL 状态成对
  （`RenderType.GlStateSnapshot` 的括号语义、`ScopeLensRenderer` 的 unit 0/1/2 快照 + `GL_ACTIVE_TEXTURE` 还原）、
  **必须渲染线程**（`ScopeLensRenderer` 类注释整段，含 LWJGL2 native abort 与 Netty 线程事故）、单点回滚开关
  （`ENABLE_EQUIPPED_PAPER_FRAME_FIX` / `NEAR_PLANE` / `DEBUG_RENDER`，未动）、上游与反编译 `file:line`
  （`EntityRenderer.java:1297`、`Framebuffer.java:117-130`、`MuzzleParticleManager.java:344-349`、`Minecraft.java:200`、
  `ScopeThermalRenderer.java:685-700` 等全部保留）。
- **本轮删掉的可疑内容（请复核）**：
  1. ~~`GuiGraphics` 类注释里 `renderItem` 走 `renderItemAndEffectIntoGUI` + 切换 lighting/`zLevel` 这条差异~~ ——
     **已按 Lead 裁决加回**：类注释现在写着「`renderItem` 走 `RenderItem#renderItemAndEffectIntoGUI`
     （RenderItem.java:575），并自行保存/还原 `zLevel` + 切换 `enableGUIStandardItemLighting` 与
     `GL_RESCALE_NORMAL`（1.20 由 `GuiGraphics#renderItem` 内部完成同样的事）」，`file:line` 依据已带。
  2. `MuzzleParticleManager` 类注释里「`BedrockPart` 已按 mcshim `PoseStack` 实现（签名与上游一致）」的括注 —— 删。
  3. `MuzzleParticleManager#toWorldMuzzleView` 里「SBM 侧从定位器变换读取出生尺寸并在绘制/加速度更新时处理 FOV 比例」
     —— 删（属对 SBM 内部实现的旁述）。
  4. `RenderType` 类注释里「纹理缓存 `ConcurrentHashMap` 保证同一纹理同一实例」的独立性 —— 压成句尾一句。

