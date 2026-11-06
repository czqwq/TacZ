# r2-02 · 武器绘制层与 GL 层审查

> **作用域（写作用域）**：`src/main/java/com/tacz/guns/mcshim/**`（39 文件）、
> `src/main/java/com/tacz/guns/client/event/**`（20 文件）、
> `src/main/java/com/tacz/guns/client/renderer/**`（36 文件）、本报告。
> 越界但必读（只报告不改）：`client/model/**`（`RenderHelperCompat`/`BedrockModel`/`SlotModel`/bedrock 立方体）、
> `com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler`、
> `mixin/client/**`。
>
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（MC 1.20.1 / Forge 47 / Java 17）。
> **1.7.10 准绳**：`build/rfg/minecraft-src/java/**` —— 本报告所有原版结论均给出该目录下的 `文件:行`
> （重点：`net/minecraft/client/renderer/Tessellator.java`、`EntityRenderer.java`、`RenderGlobal.java`、
> `ItemRenderer.java`、`entity/RenderItem.java`、`entity/RendererLivingEntity.java`、
> `net/minecraftforge/client/ForgeHooksClient.java`、`client/shader/Framebuffer.java`）。
>
> **覆盖清单**（逐类读过代码，不是抽样）：
> - mcshim 渲染面：`PoseStack`、`VertexConsumer`、`TessellatorVertexConsumer`、`BufferBuilder`、
>   `BufferBuilderVertexConsumer`、`BufferUploader`、`Tesselator`、`MultiBufferSource`、`RenderType`、
>   `GlStateManager`、`RenderSystem`、`Lighting`、`LightTexture`、`OverlayTexture`、`VertexFormat`、
>   `DefaultVertexFormat`、`GuiGraphics`、`ItemDisplayContext`。
> - client/renderer：`item/**` 全部 14 文件、`block/**` 全部 12 文件、`entity/**` 2、`scope/**` 5、
>   `external/**` 3、`other/**` 3、`crosshair/**` 1。
> - client/event：`FirstPersonRenderEvent`、`FirstPersonRenderGunEvent`、`CameraSetupEvent`（GL 相关段）、
>   `RenderCrosshairEvent`、`RenderHeadShotAABB`、`GunPegboardHighlightEvent`、`GunDisplayCaseHighlightEvent`
>   逐行读；其余 13 个文件按「是否触碰 GL / 矩阵」筛过（无 GL 调用）。
> - 读过的（不在作用域、只用于核对）：`FirstPersonRenderHandler`、`RenderHelperCompat`、`SlotModel`、
>   `BedrockPart`、`BedrockCubePerFace`、`BedrockPolygon`、`BedrockModel`、`BedrockAttachmentModel`、
>   `mixin/client/GameRendererMixin`。
>
> **自查方式**：IDEA MCP `get_file_problems(errorsOnly=true)` + `lint_files(min_severity=error)`
> （改动文件与 mcsim 渲染面 8 文件均 **0 error**）；`mcp__idea__search_text` / `search_regex` 全仓扫
> `startDrawing` / `glPushMatrix` / `vertex(` / `endVertex()` / `setAlpha` 计数。
> **本轮未运行任何 gradle 任务**（`gradlew`/`gradle` 一次都没跑，构建由 Lead 统一做）。

---

## 结论速览

| 级别 | 计数 | 说明 |
|---|---|---|
| P0 | **0 新增** | 前轮 3 个 P0 的修复**逐行确认都在、且语义正确**（详见 §一），本轮没有再找到会崩/黑屏的 P0 |
| P1 | **3**（已修 1 / 只报告 2） | ① `RenderType.clearRenderState` 强制基线 → 外层实体 pass 丢 `GL_LIGHTING`/`ALPHA_TEST`（**已修**）；② 第一人称枪不吃光照贴图（恒亮，`FirstPersonRenderHandler` 越界，只报告）；③ GUI/背包图标**垂直镜像**（`RenderHelperCompat` 越界，只报告） |
| P2 | **7** | 见 §二，全部带 `文件:行` |

**最关键的 3 条结论**

1. **前轮 3 个 P0 确认有效**：`VertexConsumer` 顶点属性错位（`endVertex()` 才落顶点）在
   `TessellatorVertexConsumer.java:216-248` 与 `BufferBuilder.java:301-323` **两处都已改对**；
   第一人称相机空间基座（`glPushMatrix + glLoadIdentity + glClear(DEPTH)` + `finally glPopMatrix`）
   在 `FirstPersonRenderHandler.java:450-534` **在且正确**；背包图标 16× 缩放 4 处调用点**都在**。
   ⚠ 但其中 **1 个是死代码**：`TessellatorVertexConsumer` / `BufferBuilderVertexConsumer` / `mcshim.Tesselator`
   在全仓 **0 个生产调用点**（`search_text` 只命中自身与 javadoc），真正跑的只有 `BufferBuilder` +
   `BufferUploader`。前轮报告把「TessellatorVertexConsumer 修好了」当成结论依据，实际**生效的是
   `BufferBuilder` 那一处**，两者都改对了才没出事。
2. **本轮唯一在作用域内的 P1 已修**：`mcshim/RenderType.java` 的 `clearRenderState()` 原来是
   「强制恢复到一个固定基线」（关混合/关 alpha test/开裁剪/关光照/纯白），而 1.20 的 `RenderType`
   状态是**括号作用域**。后果：第三人称持枪玩家渲染完之后，**同一帧后续所有实体（含该玩家自己的盔甲层）
   丢掉 `GL_LIGHTING` 与 `GL_ALPHA_TEST`** → 变平、变亮、cutout 失效。已改为「进 `setupRenderState()`
   快照、出 `clearRenderState()` 原样还原」（`RenderType.java:381-387`、`:445-447`、`:524-537`、`:611-676`），
   无快照时仍退回旧基线行为。
3. **背包图标 16× 缩放的 Y 符号是错的**（P1，越界只报告）：`scale(16,16,16)` 的**位置**算对了
   （四边形正好落在 `[x,x+16]×[y,y+16]`），但**朝向上下颠倒**——1.20 的 GUI 链是
   `translate(x,y,100)·translate(8,8,0)·scale(1,-1,1)·scale(16,16,16)`，那次 Y 翻转被
   `ItemRenderer#render` 的 `translate(-0.5,-0.5,-0.5)` 抵消位移后仍然**保留镜像**；
   1.7.10 补的 `+16` 没有对应翻转。唯一改动点是一条 2 行补丁（见 §二 P1-3）。

---

## 一、已修问题（逐行确认「修复在、且正确」）

### P0-1（前轮）`VertexConsumer` 顶点属性错位 —— ✅ 修复在、正确，但**只有 `BufferBuilder` 那条是活的**

| 证据 | 结论 |
|---|---|
| `mcshim/TessellatorVertexConsumer.java:156-165` `vertex()` 只记 `vertexX/Y/Z` + `hasVertex`；`:216-248` `endVertex()` 才 `setTextureUV/setColorRGBA/setBrightness/setNormal/addVertex` | ✅ 语义正确 |
| `mcshim/BufferBuilder.java:241-253` `vertex()` 只记 pending；`:300-323` `endVertex()` 才写数组 | ✅ 语义正确 |
| 1.7.10 依据：`Tessellator.java:407-433` —— `hasTexture/hasBrightness/hasColor/hasNormals` 在 `addVertex()` 瞬间快照，属性必须先设后发 | 与 1.7.10 一致 |
| 1.20 依据：`VertexConsumer#vertex/done` 允许属性写在 `vertex()` 之后 | 与 1.20 一致 |
| `BufferUploader.java:48-88`：`startDrawing(mode)` → 每顶点 `setTextureUV → setColorRGBA → setBrightness → setNormal → addVertex` → `draw()` | ✅ 顺序正确；`Tessellator.java:115-171` 的整缓冲 flag 由 `format()` 统一决定，不会中途变 stride |
| `TessellatorVertexConsumer:221-226` 用「第一个顶点锁存属性 mask」解决 1.7.10「整缓冲 flag」问题 | ✅ 与 `Tessellator.java:253-270`（`startDrawing` 才清 flag）+ `:390-433`（`addVertex` 读 flag）匹配 |

**回归核对**：`search_text "vertex("` / `"endVertex()"` 在 `client/renderer/**` 与 `client/event/**`
内计数 —— `ScopeThermalRenderer` 5/5、`ScopeLensRenderer` 1/1（`Bounds` 只做包围盒统计，`endVertex()` 故意空）、
其余文件 0 个 `VertexConsumer` 调用点（都走 `BedrockModel#render` → `BedrockCubeBox#compile`，
前轮已核 1/1）。**本作用域 0 处漏 `endVertex()`。**

**新发现（P2）**：`TessellatorVertexConsumer` / `BufferBuilderVertexConsumer` / `mcshim/Tesselator`
是**死代码**（`search_text` 只命中自身与 javadoc，无生产调用点）。`BufferUploader` 也是被
`MultiBufferSource.BufferSource#drawBatch`、`ScopeLensRenderer#drawReticle/drawLens`、
`ScopeThermalRenderer#render` 直接调，`BuilderVertexConsumer` 完全没人用。
→ 后续若再改顶点语义，**只需改 `BufferBuilder` + `BufferUploader`**，别再拿另外两个类当验收依据。

### P0-2（前轮）第一人称 GL 相机空间基座 —— ✅ 修复在、正确（但有 2 个遗留，见 §二 P1-2 / P2-1）

- `FirstPersonRenderHandler.java:450-452`：`glMatrixMode(GL_MODELVIEW); glPushMatrix(); glLoadIdentity();`
- `:457`：`GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)`（依据 `EntityRenderer.java:1436`：事件未 cancel 时
  原版先清深度再 `renderHand`）
- `:531-534`：`finally { glMatrixMode(GL_MODELVIEW); glPopMatrix(); }` —— **异常路径也补齐**（`endBatch`
  抛异常不会漏 `glPopMatrix`）
- `:421-423`：`thirdPersonView != 0` 直接 return（不 cancel），依据 `EntityRenderer.java:794`
  （第一人称守卫写在 `renderHand` 内部，而事件在 `:1434` 无条件派发）
- `:518` `setCanceled(true)` 只在「真的画了枪」的路径上；`:469-484` 的早退分支不 cancel，
  让原版 `renderHand` 照常执行（`GameRendererMixin.java:237-242` 的镜片注入在空手路径仍生效）
- 三处补偿齐全且**只在 cancel 路径**：`(a) compensateBeforeRenderHandEvent(:507 / :557-568)`、
  `(b) compensateScopeLenses(:529 / :589-596)`、`(c) compensateVanillaOverlays(:530 / :615-628)`；
  顺序复刻 `EntityRenderer.java:797`（手）→ `:805`（镜片/overlay）。每处独立 `try/catch`。

**GL 状态核对**：进入时 GL_MODELVIEW = 「世界+相机」视图矩阵（`EntityRenderer.java:1434` 处），
`glLoadIdentity` 后 = 相机空间（与 1.20 `RenderHandEvent#getPoseStack()` 的基座等价）；
退出时 `glPopMatrix` 把视图矩阵还回去。✅ 无泄漏。

### P0-3（前轮）GUI/背包图标 16× 缩放 —— ✅ 调用点在、位置对、**朝向错（新 P1）**

| 调用点 | 行 |
|---|---|
| `client/renderer/item/AnimateGeoItemRenderer.java` | `:91-93` |
| `client/renderer/item/AmmoItemRenderer.java` | `:61-63` |
| `client/renderer/item/AttachmentItemRenderer.java` | `:52-54` |
| `client/renderer/item/GunSmithTableItemRenderer.java` | `:46-48` |

实现：`client/model/RenderHelperCompat.java:266-271` → `poseStack.scale(16,16,16)`。

- **位置核算（对）**：顶点在 `BedrockCubePerFace#compile` 里 `/16`（`BedrockCubePerFace.java:121-123`），
  `SlotModel` 的南面四边形模型空间 `x,y ∈ [-16,0]`（`SlotModel.java:36`），
  链 `T(0.5,1.5,0.5)·Rz180·T_bone(0.5,1.5,-0.625)` 把它映射到 `[0,1]²`，
  再 `×16` → `[0,16]²`，加上 `ForgeHooksClient#renderInventoryItem` 的
  `glTranslatef(x, y, -3+zLevel)`（`ForgeHooksClient.java:191`）正好是 `[x,x+16]×[y,y+16]` 的图标格。✅
- **朝向（错）**：见 §二 **P1-3**。
- 1.7.10 依据确认：`ForgeHooksClient.java:187-205` 非 3D 分支**只**做 `glTranslatef` + `glDisable(GL_LIGHTING)`，
  **没有任何缩放**；`INVENTORY` 类型的唯一来源是 `RenderItem#renderItemAndEffectIntoGUI`
  （`RenderItem.java:583`，`search_text "renderInventoryItem"` 全仓仅此一个调用点）—— 所以
  「物品展示框也走 INVENTORY 被放大 16 倍」这个担心**不成立**。

---

## 二、未修问题

### P1-1【已修·本轮】`RenderType.clearRenderState()` 强制基线 → 外层实体 pass 丢 `GL_LIGHTING` / `GL_ALPHA_TEST`

- **文件**：`src/main/java/com/tacz/guns/mcshim/RenderType.java:488-499`（修前）
- **现象**：多人/单人世界里，**只要有一个第三人称持枪的玩家（或任意拿枪的 living entity）被渲染**，
  同一帧里**排在它后面的所有实体**（包括该玩家自己的盔甲层、后面的怪、掉落物）丢掉方向光 →
  变平、变亮；同时 `GL_ALPHA_TEST` 被关 → cutout 贴图不再裁剪（树叶/盔甲装饰/告示牌等）。
  方块实体（箱子/牌子等）同理。
- **判定依据（完整链条）**：
  1. `EntityRenderer.java:1297` 在 `renderEntities` **之前只调一次** `RenderHelper.enableStandardItemLighting()`
     —— 整个实体 pass 共用这一次开的 `GL_LIGHTING`。
  2. `RendererLivingEntity#doRender` **自己不重开光照**（`grep GL_LIGHTING RendererLivingEntity.java`
     只命中 `:198/:218`（箭矢，条件分支）与 `:491/:509`（`shouldRenderPass` 内部局部开关））。
  3. 我们的第三人称枪渲染在 `ForgeHooksClient#renderEquippedItem`（`ForgeHooksClient.java:232`）里，
     `GunItemRendererWrapper#renderByItem`（`client/renderer/item/GunItemRendererWrapper.java:414-425`）
     → `BedrockModel#render`（`client/model/bedrock/BedrockModel.java:523-525`）→
     `RenderHelperCompat#endBatch` → `MultiBufferSource.BufferSource#drawBatch`
     （`mcshim/MultiBufferSource.java:118-135`）→ 修前的 `clearRenderState()` **无条件**
     `GL11.glDisable(GL_LIGHTING); GL11.glDisable(GL_ALPHA_TEST); GL11.glEnable(GL_CULL_FACE); glDepthMask(true)`。
  4. 1.20 侧 `RenderType` 的状态是**括号作用域**（`setupRenderState`/`clearRenderState` 之间生效，
     unbind 后由下一层状态包接管），不会有「把外层基线改掉」这种副作用。移植版把 unbind 写成了
     「强制恢复到一个自造基线」，语义漂移。
- **修法（已实施）**：
  - `RenderType.java:381-387` 新增 `GlStateSnapshot savedState` + `stateSaved` 标记。
  - `:445-447` `setupRenderState()` 首行 `savedState.capture()`。
  - `:524-537` `clearRenderState()`：有快照 → `savedState.restore()` 后 return；**无快照**（历史/外部
    单边调用）→ 退回旧的固定基线，保证异常路径不会留下更糟的状态。
  - `:611-676` 新增内部类 `GlStateSnapshot`，覆盖 **`setupRenderState` 会改的每一项**：
    `GL_TEXTURE_2D`、`GL_CULL_FACE`、`GL_DEPTH_TEST`、`glDepthMask`、`GL_ALPHA_TEST`、
    `GL_BLEND`（含 `GL_BLEND_SRC/DST` 因子）、`GL_LIGHTING`、`GL_POLYGON_OFFSET_FILL`、
    `glLineWidth`、`glColor4f` 的当前颜色。
  - **配对性核对**：全仓 `setupRenderState()`/`clearRenderState()` 的真实调用点只有 4 处，全部严格成对
    （`MultiBufferSource.java:127/131` try/finally、`ScopeLensRenderer.java:540/550` try/finally、
    `GunPegboardHighlightEvent.java:115/123` + `GunDisplayCaseHighlightEvent.java:135/143`
    在外面还套了 `glPushAttrib/glPopAttrib`）。`MeshBatchRenderer.java:78/156/173`（v2，恒不走）是
    「setup next → clear previous」形态，快照会被覆盖，但那条路径本工程从未启用。
  - 未还原项（有意，写在这里防误判）：`glAlphaFunc` 参考值（本类恒写 `GL_GREATER,0.1`，
    与 1.7.10 实体基线一致）、`stencil`/`colorMask`/视口（不是本类改的）。
- **依据文件行**：`RenderType.java:381-387 / 445-447 / 524-537 / 611-676`；1.7.10：
  `EntityRenderer.java:1297`、`RendererLivingEntity.java`（无 `GL_LIGHTING` 重开）、`RenderGlobal.java:532`。
- **置信度**：**高**（链条完整、可逐行复核）。风险：本轮无法实机验证；若某处调用点实际是
  「setup→画→不再 clear」，该处会保持 TACZ 的材质状态（与修前相同的失败模式，不会更糟）。
- **自查**：`get_file_problems(RenderType.java)` + `lint_files(error)` = 0 error。

### P1-2【只报告·越界】第一人称枪**不吃光照贴图**（夜里/洞里恒亮）

- **位置**：`com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java:450-534`
  （`onRenderHand` 渲染块）；`client/renderer/scope/**` 之外无第二调用点。
- **现象**：第一人称手里的枪/瞄具/弹匣在暗处**和白天一样亮**，没有明暗过渡；
  第三人称、掉落物、方块展示都正常。
- **判定依据**：
  1. 1.7.10 的 lightmap 走 **texture unit 1**：`Tessellator#draw()` 只在 unit 1 上
     `glEnableClientState(GL_TEXTURE_COORD_ARRAY)`（`Tessellator.java:122-129 / 156-161`），
     **不**打开 unit 1 的 `GL_TEXTURE_2D` —— 那一步由 `EntityRenderer#enableLightmap`
     负责（`EntityRenderer.java:829-848`：`setActiveTexture(lightmapTexUnit)` + `glEnable(GL_TEXTURE_2D)`
     + 绑 lightmap 纹理 + 设 `GL_TEXTURE` 矩阵 `scale(1/256)·translate(8,8,8)`）。
  2. 原版手部 pass：`renderHand` 里 `enableLightmap`(:796) → `renderItemInFirstPerson`(:797) →
     `disableLightmap`(:798)，`disableLightmap` 会 `glDisable(GL_TEXTURE_2D)`（:819-824）。
  3. 我们 `setCanceled(true)` 之后 `renderHand` 整体不执行 ⇒ **`:796` 的 `enableLightmap` 也不会执行**。
     而在此之前最后一次 `disableLightmap` 是 `EntityRenderer.java:1352`（手持物之前、粒子之后），
     `grep enableLightmap` 全 EntityRenderer 只有 `:796 / :1345 / :1567` 三处，`:1434` 处**必然是关的**。
  4. ⇒ 顶点里写的 lightmap 坐标（`BufferUploader.java:79-81` `setBrightness`）被 GL 忽略，
     最终颜色 = 贴图 × 顶点色（恒 1）⇒ 恒亮。1.20 上游不存在这个问题（entity shader 恒应用 lightmap）。
- **建议修法**（最小、与 vanilla 完全一致，1 行×2）：
  在 `FirstPersonRenderHandler.onRenderHand` 的 `try` 块开头（`glClear(GL_DEPTH_BUFFER_BIT)` 之后）
  `Minecraft.getMinecraft().entityRenderer.enableLightmap(event.partialTicks)`，
  在 `finally` 里 `disableLightmap(event.partialTicks)`；**并且**在 `compensateVanillaOverlays` 之前
  先 `disableLightmap`（vanilla 的 `renderOverlays` 是在 `:798` 之后跑的，无 lightmap）。
  → 这样同时修好 P2-1（见下，muzzle/曳光弹亮度也一致）。
- **置信度**：**高**（原版调用链完整可复核）；唯一未验证的是「实机上暗处是否明显」。

### P1-3【只报告·越界】GUI/背包图标**垂直镜像（上下颠倒）**

- **位置**：`client/model/RenderHelperCompat.java:266-271`（`applyGuiSlotScale`）+ 4 处调用点
  （`client/renderer/item/{AnimateGeoItemRenderer:92, AmmoItemRenderer:62, AttachmentItemRenderer:53,
  GunSmithTableItemRenderer:47}`）。**修复文件不在本 teammate 写作用域**，故只给补丁。
- **现象**：枪/配件/弹药/工作台在物品栏、创造栏、快捷栏里的图标**上下颠倒**（左右是对的）。
- **判定依据（逐轴核算，1.20 与 1.7.10 各算一遍）**：
  - **主证据（自洽，不依赖 1.20 源码）**：`SlotModel` 南面四边形的 uv 由
    `BedrockPolygon`（`client/model/bedrock/BedrockPolygon.java:23-26`）按顶点序写死：
    `vertices[0] → (u2,v1)`、`[1] → (u1,v1)`、`[2] → (u1,v2)`、`[3] → (u2,v2)`；
    南面顶点序是 `{vertex5, vertex6, vertex7, vertex8}` = `(-16,-16,9.5), (0,-16,9.5), (0,0,9.5), (-16,0,9.5)`
    （`BedrockCubePerFace.java:51-54, 86-91`）。`singleSouthFace` 的 `uv=[0,0]`、`uvSize=[16,16]`
    （`FaceUVsItem.java:22-31` → `FaceItem.single16X()`，`FaceItem.java:15-20`；texW/H=16）
    ⇒ **模型 `y = -16` 的两个顶点 uv `v = 0`**。
  - **现状 `scale(16,16,16)`**：内层链 `T(0.5,1.5,0.5)·Rz180·T_bone(0.5,1.5,-0.625)` 把模型 `y=-16`
    映射到内层 `w=1`，`w=0` 对应模型 `y=0`；最终屏幕 `y_out = y + 16·w`
    ⇒ **`v=0` 落在图标格「底部」**（屏幕 y 向下增大）。而 MC 贴图 `v=0` 是图像**顶行**
    ⇒ 图标**上下颠倒**。x 轴：`x_out = x + 16·u`，`u=0` 在左 ✔ 左右正常。
  - **佐证（1.20 侧应正立）**：1.20 `ItemRenderer#renderGuiItem` 的 GUI 链是
    `translate(x, y, 100+blit)` → `translate(8,8,0)` → `scale(1,-1,1)` → `scale(16,16,16)`（等效
    `scale(16,-16,16)`），再叠 `ItemRenderer#render` 的 `translate(-0.5,-0.5,-0.5)` 与 BEWLR 内层链：
    `y_out = y + 16 − 16·w` ⇒ `v=0` 落在**顶部** ⇒ **正立**。
    镜像与否只取决于有没有那次 Y 翻转，周围平移只改位置 —— 因此「位置对、朝向反」这个组合是自洽的。
    ⚠ 诚实标注：1.20.1 的原版 `ItemRenderer` 源码**不在本仓库**，这一侧是按 1.20 公开实现推导的
    （前轮把**位置**算对了，恰好说明内层链与 `+16` 的量级没问题，只是漏了翻转）。
- **建议修法**（`RenderHelperCompat.java:266-268`，2 行）：
  ```java
  public static void applyGuiSlotScale(PoseStack poseStack) {
      // Y 取负（与 1.20 的 scale(1,-1,1) 对齐，否则图标上下颠倒），
      // 再 translate(0,-1,0) 把 [−1,0] 拉回 [0,1]：M = S(16,-16,16)·T(0,-1,0)
      poseStack.scale(GUI_SLOT_SCALE, -GUI_SLOT_SCALE, GUI_SLOT_SCALE);
      poseStack.translate(0.0F, -1.0F, 0.0F);
  }
  ```
  核算：`w → T(0,−1,0) → w−1 ∈ [−1,0] → ×(−16) → 16−16w ∈ [0,16]` ✔ 位置不变、
  `v=0` 回到顶部 ✔ 正立。`x`、`z` 完全不变（仍是 `[0,16]` 与 `7.5`）。
- **置信度**：**高**（两条链都是纯仿射，数值可复算）。实机若发现「本来就该颠倒」，改回原样即可。

### P2-1 `hurtCameraEffect` 完全没有作用到第一人称枪

- **位置**：`FirstPersonRenderHandler.java:447-452`（注释明确写了「这里只做 `glLoadIdentity` ——
  viewBobbing 已被取消」），但**受伤摇晃没提**。
- **依据**：原版 `renderHand` 的 `renderItemInFirstPerson` 之前依次有
  `hurtCameraEffect(partialTicks)`（`EntityRenderer.java:787`）与 `setupViewBobbing`（:789-792）；
  `RenderHandEvent` 在 `:1434` 派发时这两个都还没压进矩阵。
  于是 `mixin/client/GameRendererMixin.java:266-281` 的 `hurtCameraEffect HEAD` 注入
  （`GunHurtBobTweak` + `RenderItemInHandBobEvent.BobHurt`）与 `:324-342` 的
  `@ModifyArg`（持枪受伤倾斜倍率）在**持枪第一人称下都不会触发**（`hurtCameraEffect` 只被
  `renderHand`/`setupCameraTransform` 调用，前者被 cancel）。
- **现象**：被子弹打中时，第一人称枪体不晃（世界相机仍在晃）；`RenderConfig.GUN_HURT_BOB_MULTIPLIER`
  的倾斜倍率对第一人称枪无效。
- **建议**：在 `onRenderHand` 的 `glLoadIdentity()` 之后、`compensateBeforeRenderHandEvent()` 之前
  补一次 `hurtCameraEffect(partialTicks)`（它是 `private`，需 mixin 暴露或改用
  `MinecraftForge.EVENT_BUS.post(new RenderItemInHandBobEvent.BobHurt())` + `GunHurtBobTweak`
  自算一次 `applyToModelView()`，与 `GameRendererMixin` 同款三步）。
- **置信度**：中高（原版顺序证据确凿；「晃不晃」是观感问题，非崩溃）。

### P2-2 `ScopeLensRenderer` 遗弃 texture unit 1/2 的绑定，`RenderState` 只存了 unit 0

- **位置**：`client/renderer/scope/ScopeLensRenderer.java:467-475`（`drawLens` 依次绑
  `GL_TEXTURE0=copyTexture`、`GL_TEXTURE1=ScopeThermalRenderer.textureOr(...)`、`GL_TEXTURE2=copyTexture`，
  最后只把活动单元切回 0）；`:805-875`（`RenderState` 只快照 `texture0`、program、viewport、
  depth/blend/cull/stencil）。
- **影响**：镜片 pass 结束后 unit 1（1.7.10 的 **lightmap 单元**）仍绑着镜片的临时纹理。
  同帧内若还有带 lightmap 的绘制（例如 `compensateVanillaOverlays` 之后的东西、HUD 里的 3D 物品），
  会**采样错误的 lightmap** → 颜色/亮度异常。跨帧会被 `EntityRenderer#enableLightmap` 的
  `bindTexture(locationLightMap)` 自愈（`EntityRenderer.java:838`），所以只在同帧内短窗口可见。
- **建议**：`RenderState` 增加 unit 1/2 的 `TEXTURE_BINDING_2D` 与 `GL_ACTIVE_TEXTURE` 快照/还原
  （构造函数里已经读了 `active`，只是没存成字段、也没存 unit1/2）。
- **置信度**：中（机制确定，可见性依赖调用顺序）。

### P2-3 `ScopeThermalRenderer` / `ScopeLensRenderer` 覆盖 `GL_PROJECTION`/`GL_MODELVIEW` 后不还原

- **位置**：`client/renderer/scope/ScopeThermalRenderer.java:456-461`
  （`glLoadMatrix(projection)` / `glLoadMatrix(modelView)`）；`ScopeLensRenderer.java:370-375`
  （`glPushMatrix+glLoadIdentity` 的 **成对**版本是对的）。
- **说明**：`ScopeThermalRenderer` 用 `glLoadMatrix` 直接**替换**两个矩阵，`RenderState`（`:805-875`）
  **不**保存矩阵栈，所以退出后 GL 里是「录制时（开镜那一帧）的世界投影/视图」。
  当前唯一调用点在 `renderWorld` 尾部附近，后面紧跟 `renderHand`（自己 `glLoadIdentity` +
  `gluPerspective`）与 `setupOverlayRendering`，因此**未观察到症状**；但这是顺序依赖的隐性契约。
- **建议**：改用 `glPushMatrix`/`glPopMatrix` 包住（Lens 那条已经是这么写的，可以照抄）。
- **置信度**：中（当前无害，属健壮性）。

### P2-4 `RenderType#equals` 与 `hashCode` 不一致

- **位置**：`mcshim/RenderType.java:859-872` —— `hashCode()` 用 `Objects.hash(name, format, mode)`，
  `equals()` 只比 `bufferSize` + `name`。
- **影响**：`MultiBufferSource.BufferSource`（`LinkedHashMap`）与
  `ScopeLensRenderer.ReticleBuffers`（`Map<RenderType, BufferBuilder>`）按 `RenderType` 分桶。
  同名但 `format/mode` 不同的两个 `RenderType`（只能由 `create(...)` 造出来，`cached(...)` 的 key
  已含全部维度）会出现「`equals` 相等但 `hashCode` 不同」→ 分桶错乱/重复桶。
  当前 `create(...)` 的调用点只有 `EntityBulletRenderer.java:339-353`（名字唯一），**尚未触发**。
- **建议**：`equals` 里补 `format`/`mode`，或 `hashCode` 里去掉 `format/mode` 只留 `name`。

### P2-5 `RenderHelperCompat` 的 per-context `BufferSource` 缓存没有 `finally` 兜底 → 残留顶点串到下一帧

- **位置**：`client/model/RenderHelperCompat.java:47-62`（`EnumMap` 缓存）、`:71-84`（`endBatch` 只转调）、
  `:149-155`（`clear()` 只被资源重载调）。走这条缓存路径的调用点共 8 处
  （`GunSmithTableRenderer.java:164`、`StatueRenderer`、`TargetRenderer`、`GunDisplayCaseRenderer`、
  `GunPegboardRenderer`、`EntityBulletRenderer.java:116/122`、`TargetMinecartRenderer.java:122`、
  `BedrockPart.java:79` 的 9 参重载）。
- **影响**：`BedrockModel#render`（`client/model/bedrock/BedrockModel.java:514-525`）**不是** `try/finally`
  —— 若中途抛异常（`BedrockPart#render` / `IFunctionalRenderer#render` 都可能抛），
  `RenderHelperCompat.endBatch` 不执行 ⇒ `BufferSource.builders` 里留着一个**还在 building 的**
  `BufferBuilder`。下一次同 context 的 `getBuffer`（`MultiBufferSource.java:72-81`）会**直接返回它**
  （不会 `begin`、也**不会抛** `already building`），新顶点**追加在旧顶点后面**，随后
  `drawBatch`（`:118-135`）一次性把「上一帧残留 + 本帧新顶点」用**本帧的 GL 矩阵**画出去
  ⇒ 一串飞到错误位置的三角形（不是崩溃，是画面脏）。
- **建议**：`BedrockModel#render` 的 `finally` 里调一次 `RenderHelperCompat.endBatch(bufferSource)`
  （`endBatch` 幂等：`drawOrder` 空时什么也不做）；或更彻底地在
  `MultiBufferSource.BufferSource#getBuffer` 里对「已存在但 `building()` 的 builder」先
  `discard()` 再 `begin()`。属 `client/model/**` / `mcshim/*` 交界，本轮只报告（改 `getBuffer`
  需先确认没有「同一 batch 内多次 getBuffer 同一个 type」的调用点，`ScopeLensRenderer.ReticleBuffers`
  就依赖这一点，所以**不要**直接改 `getBuffer`）。
- **补充**：`GunPegboardRenderer.java:192-219` / `GunDisplayCaseRenderer.java` 的
  `ItemPreviewRenderContext.setAlpha(PREVIEW_ALPHA)` 到 `setAlpha(1.0F)` 的复位同样不在
  `finally` 里（`:213-217` 在 `DisplayItemRenderContext.render` 之后顺序执行）—— 异常路径会把
  `alpha=0.55` 之类的值**永久留在静态字段上**，之后所有枪（世界/第三人称）都变半透明。
  → 建议两个 `alpha<1.0F` 分支都包 `try/finally`。

### P2-6 展示框 / 头盔槽的上下文降级与注释不一致（文档级）

- **位置**：`client/model/RenderHelperCompat.java:216-232`（`displayContextOf`）—— `switch` 的
  `default` 返回 `GROUND`，但同文件 `:209-210` 的 javadoc 映射表写的是
  「（无）← FIXED / HEAD … 统一退化为 **EQUIPPED**」。
- **说明**：1.7.10 的物品展示框走 `RenderItemFrame#func_82402_b` → `EntityItem` 渲染路径
  （`RenderItemFrame.java:164-264`，即 `ENTITY` → `GROUND`），确实**不会**产生 `FIXED`；
  `FIXED` 只由我方 `GunPegboardRenderer.java:206` / `GunDisplayCaseRenderer` 显式传入。
  ⇒ 行为是对的，**只有注释是错的**（会误导后来者以为展示框走 EQUIPPED）。建议改成 `GROUND`。

### P2-7 `mcshim/RenderSystem.getModelViewStack()` 的「假 PoseStack」仍是潜在陷阱（当前 0 调用点）

- **位置**：`mcshim/RenderSystem.java:299-325`。
- **本轮复核**：`search_text "getModelViewStack"` 全仓 10 处命中，**全部**在 `RenderSystem` 自身
  （定义/javadoc）与 `client/gui/GunSmithTableScreen.java:1122` 的**注释**里。⇒ **0 个生产调用点**，
  前轮结论仍然成立。
- 保留提示：若日后有人照上游写 `RenderSystem.getModelViewStack().pushPose(); ... mulPoseMatrix(m);`，
  在 1.7.10 下**完全无效**（不碰 GL）。必须改用 `pushToGL()/popFromGL()` 或 `applyToModelView()`。

### 已核「无问题」的关键项（避免重复排查）

| 项 | 证据 |
|---|---|
| **1.7.10 `Tessellator` 单例嵌套**：全仓 `startDrawing*` 只有 8 个真实调用点，全部**自包含** | `mcshim/GuiGraphics.java:188/231/315`、`mcshim/BufferUploader.java:55`（配套 `:87 draw()`）、`mcshim/TessellatorVertexConsumer.java:148`（死代码）、`client/renderer/scope/ScopeLensRenderer.java:527`（`:535 draw()`）、`client/model/BedrockAttachmentModel.java:368`（`:377 draw()`）、`client/model/functional/BeamRenderer.java:161`、`client/model/functional/RangefinderImpactPredictionRender.java:189`。**无一处**在「外层已 `startDrawing` 未 `draw`」期间启动第二份 ⇒ 不会 `Already tesselating!`（`Tessellator.java:255-258`） |
| **`PoseStack` 矩阵乘序** | `PoseStack.java:51-114` 的 `translate/scale/mulPose/rotateAround` 全部是 **右乘**（JOML `M.mul/translate/rotate`），与 1.20 `PoseStack` 逐行同构；`scale` 同步维护法线矩阵 `1/x,1/y,1/z`（`:69-77`）、`mulPose` 同步 `normal.rotate`（`:82-89`）、`rotateAround` 只对 normal 做 `rotate`（`:107-114`）—— 与 1.20 一致。`stackTopAsFloatBuffer`（`:244-252`）用 JOML `get(float[])` 的**列主序**，可直接 `glMultMatrixf` ✔ |
| **`LightTexture` 打包布局** | `LightTexture.java:66-90` `pack(block,sky) = sky<<20 \| block<<4`，与 `World#getLightBrightnessForSkyBlocks` 一致；`FULL_BRIGHT=0xF000F0` = 15<<20\|15<<4 ✔；`u/v` 拆分与 `OpenGlHelper.setLightmapTextureCoords` 一致，`BufferUploader.java:79-81` 直接把 packed 喂 `setBrightness` ✔（`Tessellator.java:413-416` 写到槽位 7，`:122-129` 在 unit1 以 `shortBuffer.position(14)` 取 → 与 `packed` 高/低 16 位对齐） |
| **`RenderSystem.defaultBlendFunc`** | `RenderSystem.java:108-110` 已是 `blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO)`，与 1.20 一致（前轮修的 #3 仍在） |
| **GUI 反锯齿/热点** | `GuiGraphics.fill/fillGradient/blit/drawString`（`GuiGraphics.java:157-327/486-535`）每个绘制都是 `glPushMatrix → 画 → glPopMatrix` 的 `try/finally`，并且 `Tessellator` 用法是 `startDrawingQuads → addVertex* → draw()` 自包含 ✔ |
| **TESR 的 `glPushMatrix/glPopMatrix`** | `GunSmithTableRenderer.java:126/133`、`StatueRenderer.java:103/109`、`TargetRenderer.java:110/116`、`GunPegboardRenderer.java:109/115`、`GunDisplayCaseRenderer.java:114/120` —— 全部 `try/finally` 成对，并在 `finally` 里再兜一次 `RenderHelperCompat.endBatch(bufferIn)`（`GunSmithTableRenderer.java:130-134`）✔ |
| **方块高亮事件的 `glPushAttrib/glPopAttrib`** | `RenderHeadShotAABB.java:84-95`、`GunPegboardHighlightEvent.java:113-125`、`GunDisplayCaseHighlightEvent.java:133-145` —— 都是 `GL_ENABLE_BIT|COLOR_BUFFER_BIT|LINE_BIT|DEPTH_BUFFER_BIT` 快照 + `finally pop` ✔ |
| **`EntityBulletRenderer` 的纹理矩阵** | `EntityBulletRenderer.java:364-378` `glMatrixMode(GL_TEXTURE)+push+loadIdentity+translate` / `pop` 严格成对，且**只**包住 `model.render(...)`（`:191-205`），纹理矩阵在 `Tessellator#draw` 期间生效 ✔ |
| **`TargetMinecartRenderer` 无需 `glTranslate`** | `func_147910_a`（`:72-92`）由 `RenderMinecart#doRender` 调用，此时 GL_MODELVIEW 已平移到矿车位置（`Render.java` 契约），与 `EntityBulletRenderer#doRender` 的 `poseStack.translate(x,y,z)`（`:224-246`）等价 ✔ |
| **`ExternalGunItemVboRenderer#tryRender` 恒 `UNSUPPORTED`** | `ExternalGunItemVboRenderer.java:53-58`，有意降级（无 VBO 后端），行为 = 关掉静态网格缓存走完整模型渲染 ✔ |
| **`ItemPreviewRenderContext.alpha()` 的跨场景污染** | 只有 `GunPegboardRenderer.java:201/214` 与 `GunDisplayCaseRenderer.java:218/231` 成对写入并复位。异常路径的隐患见 P2-5 补充 |

---

## 三、GL 状态表（每个 render 入口：进入假设 → 退出恢复 → 结论）

> 缩略：**MV**=`GL_MODELVIEW`、**LT**=lightmap 纹理单元（`OpenGlHelper.lightmapTexUnit`）、
> **AT**=`GL_ALPHA_TEST`、**LTG**=`GL_LIGHTING`、**BL**=`GL_BLEND`、**CF**=`GL_CULL_FACE`、
> **DT**=`GL_DEPTH_TEST`、**DM**=`glDepthMask`。

| # | 入口 | 进入前假设（由原版提供） | 退出后恢复 | 结论 |
|---|---|---|---|---|
| 1 | `FirstPersonRenderHandler#onRenderHand`（第一人称枪/瞄具/弹匣） | MV=世界视图矩阵；**LT 关闭**（`EntityRenderer.java:1352` 之后无 `enableLightmap`）；AT=开；LTG=开（实体 pass 的 `:1297`，但这里没用） | MV：`glPushMatrix`+`glLoadIdentity`+`finally glPopMatrix`（`:450-452 / 531-534`）✔；深度：进入时 `glClear(DEPTH)` 主动清（`:457`）✔；`RenderType` 走新快照还原 ✔ | ⚠ **LT 未开 → 枪恒亮**（P1-2）；`hurtCameraEffect` 未叠（P2-1）；其余 OK |
| 2 | `AnimateGeoItemRenderer#renderItem` → `GunItemRendererWrapper#renderByItem`（第三人称手持 / 掉落物 / 展示柜 / 挂板 / GUI 图标） | 由 `ForgeHooksClient` 提供槽位/手持 GL 变换；GUI 分支另有 `RenderItem#renderItemAndEffectIntoGUI`（`RenderItem.java:575-586`） | `MultiBufferSource.immediate()` + `finally endBatch()`（`AnimateGeoItemRenderer.java:94-99`），`RenderType` 快照还原 ✔；`poseStack.pushPose/popPose` 成对（`GunItemRendererWrapper.java:354/441`）✔ | OK（GUI 朝向见 P1-3；`hurtCamera` 无关） |
| 3 | `AmmoItemRenderer#renderItem` | 同上 | 同上（`:64-69`）+ `pushPose/popPose`（`:106/179`）✔ | OK（GUI 朝向同 P1-3） |
| 4 | `AttachmentItemRenderer#renderItem` | 同上 | 同上（`:55-58`） | OK |
| 5 | `GunSmithTableItemRenderer#renderItem` | 同上 | 同上（`:49-54`）；`else` 分支**不** `pushPose` 但也不 `pop`（`:81-87`），因为 `poseStack` 是当场新建的（`:44`），无泄漏 | OK |
| 6 | `GunSmithTableRenderer#renderTileEntityAt` | LT=开、LTG=开（`RenderGlobal.java:483 enableLightmap` / `:532 enableStandardItemLighting`）；MV 未平移 | MV：`glPushMatrix`+`glTranslatef(x,y,z)`+`finally pop`（`:126-134`）✔；`RenderType` 快照还原 ✔ | OK（修 P1-1 前会在这里关掉 LTG/AT 污染后续 TESR） |
| 7 | `StatueRenderer` / `TargetRenderer` / `GunPegboardRenderer` / `GunDisplayCaseRenderer` | 同 6 | MV：`try/finally` 成对（见 §二「已核无问题」表）；`RenderType` 快照 ✔ | OK |
| 8 | `EntityBulletRenderer#doRender` | LT=开（`RenderGlobal.java:483`）；MV 已在实体循环里 | `MultiBufferSource.immediate()`+`finally endBatch()`（`:240-245`）；`GL_TEXTURE` 矩阵 `push/pop` 成对（`:191-205 / 364-378`）；`poseStack.pushPose/popPose` 成对（`:148/208`、`:113/122`）✔ | OK |
| 9 | `TargetMinecartRenderer#func_147910_a` | LT/矩阵由 `RenderMinecart#doRender` 提供 | `finally endBatch()`（`:89-91`）+ `pushPose/popPose`（`:116/145`）✔ | OK |
| 10 | `ScopeLensRenderer#render`（镜片全屏 pass） | MV/投影 = 手部 pass（identity + 手部 FOV 投影）；LT 关闭；DT 开 | 投影/MV：`glPushMatrix`…`finally glPopMatrix`（`:370-404`）✔；program/viewport/DT/DM/BL/CF/stencil/unit0 绑定：`RenderState`（`:822-871`）✔；**颜色掩码 `glColorMask(true,true,true,true)`（`:382`）未还原**（P2，1.7.10 GUI/HUD 基线就是全 true，实际无害） | ⚠ unit1/2 绑定遗留（P2-2）；其余 OK |
| 11 | `ScopeThermalRenderer#render`（热成像遮罩） | 同 10 | viewport/DT/DM/BL/CF/stencil/unit0：`RenderState` ✔；FBO 绑定恢复（`:441 / :482`）；深度附件在 `finally` 解绑（`:485`）；**`GL_PROJECTION`/`GL_MODELVIEW` 用 `glLoadMatrix` 覆盖后不还原**（P2-3） | ⚠ 矩阵（P2-3） |
| 12 | `ScopeLensRenderer#drawReticle` | 已在 10 的 NDC pass 内 | `type.setupRenderState()` / `type.clearRenderState()` 在 `try/finally`（`:540-551`），修 P1-1 后为快照还原 ✔ | OK |
| 13 | `GuiGraphics`（准心/命中标记/提示框/所有 GUI 绘制） | GUI 正交投影 + identity modelview | 每个绘制 `glPushMatrix`+`glPopMatrix`（`:140-150`），`blit` 里 `disableLighting` 不还原（GUI 基线本来就关，且下一个 `RenderHelper.enableGUIStandardItemLighting` 会重开） | OK（`zLevel` 与 `fill(...,z,...)` 的双重偏移是 1.20→1.7.10 的等价折算，非 bug） |
| 14 | `RenderHeadShotAABB#onRenderEntity`（爆头调试框） | MV 已平移到实体；DT 开 | `glPushAttrib(ENABLE\|COLOR_BUFFER\|LINE\|DEPTH)` + `finally glPopAttrib`（`:84-95`）✔ | OK（**本作用域唯一用 `glPushAttrib` 的模范写法**） |
| 15 | `GunPegboardHighlightEvent` / `GunDisplayCaseHighlightEvent`（槽位高亮线框） | 世界 pass | `glPushAttrib` + `finally { lines.clearRenderState(); glPopAttrib(); }`（`:113-125` / `:133-145`）✔ | OK |
| 16 | `RenderCrosshairEvent#onRenderOverlay` | HUD pass；cancel 掉原版准心 | 只经 `GuiGraphics`（自带 push/pop）；`enableBlend`+`blendFunc` 设了不还原（HUD 基线就是开混合的 `SRC_ALPHA/ONE_MINUS_SRC_ALPHA`） | OK |
| 17 | 所有 `RenderType.setup/clear` 包裹的批次（`MultiBufferSource#drawBatch`） | 任意 | **修 P1-1 后**：`setup` 快照 → `clear` 还原（`RenderType.java:445-447 / 524-537 / 611-676`）；无快照时退回旧基线 | ✅ 已修 |

**成对性总账**（`client/renderer/**` + `mcshim/**` 机检 + 人工核对）：
`glPushMatrix/glPopMatrix` 全部成对（含 `try/finally`）；`glMatrixMode` 全部切回 `GL_MODELVIEW`
（唯一例外见 P2-3 的 `glLoadMatrix` 覆盖内容，模式本身是对的）；
`glPushAttrib/glPopAttrib` 3 处全成对；`startDrawing/draw` 8 处全成对；
`vertex()/endVertex()` 本作用域 0 处漏配对。

---

## 四、与上游的偏离登记

| # | 偏离点 | 位置 | 判定 |
|---|---|---|---|
| 1 | `RenderType` 从 1.20 的「RenderStateShard 组合 + 括号作用域」退化为「一组 GL 字段 + 手写开关」，本轮补上「括号还原」 | `mcshim/RenderType.java:416-537` | 平台必需；本轮已把语义拉回 1.20 的括号语义 |
| 2 | 1.20 的 `MultiBufferSource.BufferSource` 有 per-`RenderType` 的 `BufferBuilder` + VBO 上传；本工程改为 Java `float[]` + 每次 `endBatch` 逐顶点重灌 `Tessellator` | `mcshim/BufferBuilder.java`、`BufferUploader.java` | 平台必需（1.7.10 无 VBO 后端）；**性能**显著低于上游，枪械网格每帧重灌，属已知代价 |
| 3 | `VertexConsumer#overlayCoords` 丢弃（1.7.10 顶点格式无 overlay 槽位） | `mcshim/TessellatorVertexConsumer.java:193-197`、`BufferBuilder.java:279-282` | 平台必需；受伤红闪/附魔光效在顶点级不可表达（上游 `OverlayTexture` 恒 0，实际影响小） |
| 4 | 1.20 手部 pass 的 `PoseStack` 基座 = bobHurt+bobView；1.7.10 改成 `glLoadIdentity` + 主动 cancel `RenderItemInHandBobEvent.BobView` | `FirstPersonRenderHandler.java:447-452` + `client/event/FirstPersonRenderGunEvent.java:119-130`（`cancelItemInHandViewBobbing`） | 语义等价（viewBobbing 由枪械动画接管）；**但 hurt 通道漏了**（P2-1） |
| 5 | 1.20 的 `energySwirl` 用 shader 做 UV 滚动；1.7.10 用 `GL_TEXTURE` 矩阵 | `client/renderer/entity/EntityBulletRenderer.java:355-378` | 等价实现，且 push/pop 严格成对 |
| 6 | `ExternalGunItemVboRenderer` 恒 `UNSUPPORTED`（关掉静态网格缓存） | `client/renderer/item/ExternalGunItemVboRenderer.java:53-58` | 平台必需；`RenderConfig.STATIC_ITEM_GUN_VBO` 因此**对画面无影响** |
| 7 | 镜片/热成像在 core profile（Angelica shader pack）下整体禁用 | `client/renderer/scope/ScopeLensRenderer.java:194-250`（`isCoreProfile()`） | 合理降级，有 `docs/ANGELICA_COMPAT.md` 支撑 |
| 8 | `ItemDisplayContext` 由「1.7.10 的 `ItemRenderType` + `leftHand` 布尔」合成，`FIXED`/`HEAD` 无原生来源 | `mcshim/ItemDisplayContext.java`、`client/model/RenderHelperCompat.java:216-232` | 平台必需；注释写错（P2-6） |
| 9 | `Lighting.setupForEntityInInventory()` 退化为 `enableStandardItemLighting()` | `mcshim/Lighting.java:40-43` | 已记录，光照角度略有差异 |
| 10 | `RenderSystem` 无状态缓存 / 无 shader / `getModelViewStack()` 返回脱钩的假 PoseStack | `mcshim/RenderSystem.java` | 平台必需；`getModelViewStack()` 当前 0 调用点（P2-7） |

---

## 五、复查清单（给 Lead / 实机）

**构建前**
1. 只有 1 个文件被本轮改动：`src/main/java/com/tacz/guns/mcshim/RenderType.java`
   （`get_file_problems` + `lint_files(error)` 均 0 error）。
2. 若采纳 P1-3 的补丁，改的是 `client/model/RenderHelperCompat.java:266-268`（**不在本 teammate 作用域**）。
3. 若采纳 P1-2 的补丁，改的是 `FirstPersonRenderHandler.java`（vendored SBM，**不在本 teammate 作用域**）。

**实机必看（按优先级）**
1. **P1-1 回归**：第三人称看另一个拿枪的玩家 → 他/它之后的怪、箱子、告示牌**不应**变平变亮；
   盔甲层纹理**不应**出现「透明区变不透明」。
2. **P1-2 回归**：关掉夜视进洞穴/夜里 → 第一人称手里的枪应随环境变暗（修前恒亮）。
3. **P1-3 回归**：创造栏/背包/快捷栏的枪图标**正立**（修前上下颠倒）；若改后反而颠倒，说明
   `SlotModel` 的 `Rz180` 语义与我的推算不符，把 `applyGuiSlotScale` 改回 `+16` 即可。
4. 开镜：镜片位置/大小/遮挡、开镜全黑、镜片糊满屏（本轮只核了 GL 状态成对性，**着色器数学与
   FBO 语义仍未验证**）。
5. 热成像：遮罩与场景深度对齐（1.7.10 主 framebuffer 的深度附件类型见 `Framebuffer.java:108-130`）。
6. 弩/枪在墙边贴脸时第一人称枪体**不应**被世界几何裁掉（`glClear(DEPTH)` 补偿是否够）。
7. 被子弹打中时第一人称枪体**目前不会晃**（P2-1，预期行为差异，不是新 bug）。

**未覆盖 / 诚实清单**
1. `client/renderer/scope/**` 的 **GLSL 源码本体**（`ScopeLensProgram` 的 vertex/fragment 字符串）
   只核了 uniform 名与 `attribute vec3 Position` 绑定 0 这一点，**着色器数学未逐行对照上游**。
2. `client/renderer/external/**` 的 `ExternalGunAppearance`/`ExternalGunEffects` 只核了 null 安全与
   上下文映射，外部（LOD）模型的几何仍委托给未启用的 v2 网格设施，实际画面等价于「永远走内置模型」。
3. `simplebedrockmodel v2`（`v2/client/mesh/**`）与 `v1/particle/**` 不在本作用域，按「恒不走」
   处理，未审。
4. 本轮**禁止跑 `runClient`**，所有结论均为静态推导；P1-2 / P1-3 的可见性必须实机确认。
