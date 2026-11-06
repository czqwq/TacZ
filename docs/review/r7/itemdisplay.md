# r7-itemdisplay · item model `display` 变换（ammo/attachment）与方块渲染器光照

> 任务：`task-29`（r7 审查缺口补修）。写作用域：`client/renderer/item/{AmmoItemRenderer,AttachmentItemRenderer,
> GunSmithTableItemRenderer,BlockModelGuiItemRenderer}.java`、`client/resource/**`、本报告。
> 上游权威：`tmp/TACZ-1.20.1/**`（TACZ 1.20.1）、`tmp/minecraft-src-1.20.1/**`（1.20.1 原版）、
> `build/rfg/minecraft-src/**`（1.7.10 反编译源码）。
> 未运行 gradle（遵守约定）；改动文件 `lint_files(min_severity="error")` = 0 error。

---

## 一、摘要

| # | 结论 | 位置 | 状态 |
|---|---|---|---|
| **D-1** | `assets/tacz/models/item/{ammo,attachment}.json` 的 `display` 段在 1.7.10 侧**无任何读端**（r7-fp K1 / r7-tp §5 指出）⇒ 弹药盒/配件在手持、掉落、展示框里的位置与大小 ≠ 上游 | 新增 `client/resource/ItemModelDisplayManager.java` + `client/resource/pojo/model/ItemModelDisplay.java` | **已修（本任务）** |
| **D-2** | 1.7.10 侧不仅缺 display，各分支外层帧也与 1.20 不同（`INVENTORY` 无 GUI 缩放、`EQUIPPED`/第一人称叠 2D 纸片帧、`ENTITY` 多 `S(0.5)`、展示框/展示柜走 `ENTITY`）。只补 display 会套错坐标系 | `AmmoItemRenderer`/`AttachmentItemRenderer` + 管理类 | **已修（本任务）**：逐分支 `F_1.7.10 · A == F_1.20` |
| **D-3** | `shouldUseRenderHelper` 全返回 false ⇒ 1.7.10 掉落物**不自转、不浮沉、多一层 S(0.5)**；第三人称/第一人称还叠 Forge 的 2D 纸片帧（上下颠倒/旋转 45°） | 同上 | **已修（本任务）** |
| **D-4** | `GunSmithTableItemRenderer` 写死 `0xF000F0` light（r7-fp K2）⇒ 工作台在世界里恒满亮 | `GunSmithTableItemRenderer.java:58` → `RenderHelperCompat#packedLightFor` | **已修（本任务）** |
| **D-5** | K2 的另半句「`BlockModelGuiItemRenderer` 也写死 `0xF000F0`」**在当前代码里不成立**：该类（r6 新增）不消费 `packedLight`，用 GL 固定管线光照画 GUI 方块图标；全仓 `grep 0xF000F0` 只剩 `LightTexture.FULL_BRIGHT` 常量与该工作台一处 | `BlockModelGuiItemRenderer.java` | 无需改动（见 §五-3） |
| **C-1** | 展示柜/挂板/雕像里的非枪物品由 `StaticItemRenderHelper`（1.20 `ItemRenderer#renderStatic(FIXED)` 的替身）驱动，它把 FIXED 退化成 `ENTITY` 且先加 `S(0.5)`，且**无法把 `packedLight` 传给 `IItemRenderer`** | `client/renderer/block/StaticItemRenderHelper.java` | **已修（第二轮，Lead 授权）**：ThreadLocal 传亮度 + 注释更新，见 §八-2 |
| **C-2** | `GunSmithTableItemRenderer` 走展示柜/挂板/雕像路径时仍用旧帧（半尺寸/偏移），未接入本管理器 | `client/renderer/item/GunSmithTableItemRenderer.java`（本 scope） | **已修（第二轮）**：接 `applyDisplayTransform`+`displayContextOf`，GUI 保持 r6 `BlockItemTransforms` 用法，见 §八-1 |
| **C-3** | `BlockItemTransforms` 缺 1.20 左手回退（`ItemTransforms.java:91-101`） | `client/resource/pojo/display/block/BlockItemTransforms.java` | **已修（第二轮）**：`getTransform` 懒回退；当前消费端恒右手 ⇒ 行为不变，见 §八-3 |

> 结论：弹药盒/配件现在与 1.20 逐分支同构（公式级复核见 §三-4，矩阵误差 < 1e-4）；
> 枪械本身无 display 段，未受影响（`modern_kinetic_gun.json` 只有 `parent`/`gui_light`）。

---

## 二、上游依据（file:line）

| 环节 | 依据 |
|---|---|
| display 数据格式 / 左手回退 | `tmp/minecraft-src-1.20.1/.../block/model/ItemTransforms.java:69-116`（`getTransform` :69-81；反序列化左手继承右手 :91-101） |
| 变换语义（translation÷16、clamp、rotationXYZ、scale clamp） | `tmp/minecraft-src-1.20.1/.../block/model/ItemTransform.java:31-46,77-85`（`apply` :31-46；`mul(0.0625)` :81；clamp :82,84） |
| display 施加点 + **无条件** -0.5 | `tmp/minecraft-src-1.20.1/.../entity/ItemRenderer.java:128-129`（`apply(flag, poseStack)` 之后 `translate(-0.5F,-0.5F,-0.5F)`） |
| 物品模型 JSON 的 display 段 | `src/main/resources/assets/tacz/models/item/ammo.json:5-16`（首/左手 scale 0.5）、`attachment.json:4-25`（第三人称 T(0,1,1) S(0.55)；第一人称 R(0,-90,25) T(1.13,3.2,1.13) S(0.68)；ground T(0,2,0) S(0.5)；head；fixed R(0,180,0)） |
| 枪械无 display（不受影响） | `assets/tacz/models/item/modern_kinetic_gun.json`（仅 `parent: builtin/entity` + `gui_light`） |
| 1.20 掉落物帧 | `tmp/minecraft-src-1.20.1/.../entity/ItemEntityRenderer.java:54-102`（bob :63；`translate(0, bob+0.25*ground.scale.y)` :64-65；`Ry(spin)` :66-67；`render(GROUND)` :93） |
| 1.20 展示框帧 | `tmp/minecraft-src-1.20.1/.../entity/ItemFrameRenderer.java:83-116`（:88 `T(0,0,0.4375)`；:109 `S(0.5)`；:110-111 `renderStatic(FIXED)`） |
| 1.20 第三人称手持帧 | `tmp/minecraft-src-1.20.1/.../layers/ItemInHandLayer.java:44-63`（`translateToHand` :55；`Rx(-90)`/`Ry(180)` :56-57；`T(±1/16,0.125,-0.625)` :58-59） |
| 1.20 第一人称帧 | `tmp/minecraft-src-1.20.1/.../ItemInHandRenderer.java:332-333`（视角延滞 `±0.1Δ`）、`:318-321`（`applyItemArmTransform`T(±0.56,-0.52,-0.72)）、`:533-550`（待机分支） |
| 1.20 GUI 帧 | `tmp/minecraft-src-1.20.1/.../gui/GuiGraphics.java:478-494`（`T(i+8,j+8,150+z)`+`S(1,-1,1)`+`S(16,16,16)`；full bright 15728880） |
| 1.7.10 GUI 帧 | `build/rfg/.../ForgeHooksClient.java:153-208`（else 分支 :189-205：`glDisable(LIGHTING)` + `T(x,y,-3+zLevel)`） |
| 1.7.10 第一人称帧（2D 纸片） | `build/rfg/.../ItemRenderer.java:403-444`（:434 `T(0.7*0.8,-0.65*0.8,-0.9*0.8)`；:435 `Ry(45)`；:443 `S(0.4)`） |
| 1.7.10 第三人称帧（2D 纸片） | `build/rfg/.../RenderPlayer.java:313-414`（:318-319 手臂 postRender + `T(-1/16,7/16,1/16)`；:377-385 纸片分支 `T(0.25,0.1875,-0.1875)·S(0.375)·Rz(60)·Rx(-90)·Rz(20)`） |
| 1.7.10 Forge 手持帧 | `build/rfg/.../ForgeHooksClient.java:214-236`（`EQUIPPED_BLOCK`→仅 `T(-0.5,-0.5,-0.5)` :216-221；否则 2D 纸片帧 :225-233） |
| 1.7.10 掉落物帧 | `build/rfg/.../RenderItem.java:59-155`（:95 `T(x,y+bob,z)`；:101 `renderEntityItem`）+ `ForgeHooksClient.java:84-151`（`ENTITY_ROTATION` :92-95、`ENTITY_BOBBING` :96-99、非 3D 分支 `S(0.5)` :145-149） |
| 1.7.10 展示框路径 | `build/rfg/.../tileentity/RenderItemFrame.java:248-250`（`RenderItem.renderInFrame=true` 后复用 ENTITY 入口） |
| 1.7.10 展示柜/挂板/雕像路径 | `StaticItemRenderHelper.java:73-146`（`S(0.5)` + `renderItem(ENTITY)`）；三处调用点都包在 `DisplayItemRenderContext.render(...)` 里：`GunDisplayCaseRenderer:222-231`、`GunPegboardRenderer:205-214`、`StatueRenderer:149-158` |
| 1.7.10 lightmap 状态 | `build/rfg/.../OpenGlHelper.java:65-67,705-719`（`setLightmapTextureCoords` 同时写 `lastBrightnessX/Y`；第一轮曾据此还原亮度，第二轮改为 `pushPackedLight` ThreadLocal，见 §八-2） |

---

## 三、逐分支推导与验证

记号：`F_17` = 进入 `renderByItem` 前 1.7.10 侧累积的（相对）帧，`F_20` = 1.20 对应帧，
`A` = 本实现在 `renderByItem` 之前插入的补偿（`T`/`R`/`S` 均右乘，和 GL / PoseStack 语义一致）。
目标：`F_17 · A == F_20`，于是 `renderByItem` 内上游原样的 body 变换即可得到与 1.20 相同的结果。
手臂骨骼帧（`ModelRenderer.postRender` vs 1.20 `translateToHand`）两侧同在矩阵最左端，按 r7-tp/r7-fp
对枪械同类问题的既有裁决视为同源、相消（平台差异见 §六）。

### 3.1 公式（实现即 `ItemModelDisplayManager` 的 5 个私有方法）

| 分支 | `F_17`（1.7.10） | `F_20`（1.20） | `A`（本实现） |
|---|---|---|---|
| INVENTORY/GUI | `applyGuiSlotScale`（已含 r6 校准） | `T(x+8,y+8,150+z)·S(1,-1,1)·S(16,16,16)·D_gui·T(-0.5)` | `T(0.5)·D_gui·T(-0.5)`（z 只差 GUI 深度常量 145，不影响屏幕位置） |
| EQUIPPED（第三人称） | `T(-1/16,7/16,1/16)·T(0.25,0.1875,-0.1875)·S(0.375)·Rz(60)·Rx(-90)·Rz(20)·T(-0.5)` | `Rx(-90)·Ry(180)·T(1/16,0.125,-0.625)·D_3rd·T(-0.5)` | `inv(F_17纸片)·[Rx(-90)·Ry(180)·T(1/16,0.125,-0.625)]·D_3rd·T(-0.5)`（预计算常量 `EQUIPPED_COMPENSATION`） |
| EQUIPPED_FIRST_PERSON | `T(0.56,-0.52,-0.72)·Ry(45)·S(0.4)·T(-0.5)` | `T(0.56,-0.52,-0.72)·D_fp·T(-0.5)` | `T(0.5)·S(2.5)·Ry(-45)·D_fp·T(-0.5)` |
| ENTITY→GROUND（掉落物） | `T(0,bob,0)·Ry(spin)·S(0.5)` | `T(0,bob+0.25·gy,0)·Ry(spin)·D_ground·T(-0.5)`，`gy=D_ground.scale.y` | `T(0,0.5·gy,0)·S(2)·D_ground·T(-0.5)` |
| ENTITY→FIXED（展示框） | `S(0.5)`（frame 基座两侧同有） | `S(0.5)·D_fixed·T(-0.5)` | `D_fixed·T(-0.5)` |
| ENTITY→FIXED（展示柜/挂板/雕像） | `S(0.5)`（`StaticItemRenderHelper` 的 ENTITY 退化） | `D_fixed·T(-0.5)`（1.20 是 `renderStatic(FIXED)`，无 S(0.5)） | `S(2)·D_fixed·T(-0.5)` |

说明：
- 第一人称的 `-0.5`/`Ry(45)`/`S(0.4)` 来自 1.7.10 原版纸片帧（`ItemRenderer:434-444`）+ Forge
  `EQUIPPED_BLOCK` 补的 `T(-0.5)`；1.20 的 `applyItemArmAttackTransform` 在待机（attackAnim=0）时两次
  `Ry(±45)` 相消，故只剩 `applyItemArmTransform` 的 `T(0.56,-0.52,-0.72)`，与 1.7.10 的
  `T(0.56,-0.52,-0.72)` 逐值相同 ⇒ 中间项全部相消。
- 掉落物的 `+0.25·gy` 是 1.20 `ItemEntityRenderer:64-65` 的 `0.25F * ground.scale.y`；`gy` 取自
  display 数据（ammo 无 ground 段 ⇒ 1；attachment ground scale 0.5 ⇒ 0.125 格）。
- 展示框/展示柜用 `D_fixed`；`GROUND`（ground）只用于真正掉在地上的 `EntityItem`。
- 左手：1.7.10 无双持槽，`IItemRenderer` 只有主手一条路径（r7-tp §六-1 已判定），因此 `apply(false, …)`
  的镜像参数恒 false；`ItemModelDisplay` 已按 1.20 语义实现左手回退（attachment 的
  `firstperson_lefthand` 缺失 ⇒ 继承右手），将来若出现左手入口可直接用。

### 3.2 `shouldUseRenderHelper` 映射（帧选择的另一半）

| type | 1.7.10 Forge 行为（helper=全 false 时） | 本实现 | 理由 |
|---|---|---|---|
| `EQUIPPED` / `EQUIPPED_FIRST_PERSON` | 走 `:225-233` 的 2D 纸片帧 | `EQUIPPED_BLOCK`=true（只走 `:216-221` 的 `T(-0.5)`） | 3D 模型不该被纸片帧再转 45°/缩放 0.4；与 `AnimateGeoItemRenderer` 对枪械的既有处理一致 |
| `ENTITY`（非展示框） | 不自转、`T(0,-bob,0)` 抵消浮沉、`S(0.5)` | `ENTITY_ROTATION`/`ENTITY_BOBBING`=true | 1.20 掉落物始终自转+浮沉（`ItemEntityRenderer:63-67`） |
| `ENTITY`（展示框/展示柜） | 同上 | 两 helper 均 false | 1.20 FIXED 既不自转也不浮沉 |
| `INVENTORY` | `INVENTORY_BLOCK`=false 走 else 分支 | 保持 false | GUI 帧由 `applyGuiSlotScale` 处理 |

### 3.3 数值复核（joml-1.10.5，与工程 `dependencies.gradle` 同版本）

用临时 JVM 程序（未提交，`javac -cp joml-1.10.5.jar`）按 §3.1 逐式搭矩阵，做两项检查：

1. **一致性**：`max|F_17·A − F_20|`（含真实 display 数据；第一人称附取样 sway 角、第三人称附任意
   手臂帧）：全部 ≤ **1.2e-7**（float 误差内）⇒ 补偿后与 1.20 帧同构。
2. **修正前后差异**（`F_17` 无 display+旧 helper → `F_20` 等价帧）：

| 物品 | 分支 | 轴向尺度 修正前→后 | 入口原点 (0,0,0) 位移 | 模型中心 (0.5,0.5,0.5) 位移 |
|---|---|---|---|---|
| ammo | 第一人称 | 0.400 → **0.500** | 25.7 cm | 0 cm（两版都把模型中心映射到同一点；差异在尺寸与 45° 旋转） |
| attachment | 第一人称 | 0.400 → **0.680** | 70.2 cm | 22.4 cm |
| ammo | 第三人称 | 0.375 → **1.000** | 75.2 cm | 25.0 cm |
| attachment | 第三人称 | 0.375 → **0.550** | 41.2 cm | 26.5 cm |
| ammo | 掉落物 | 0.500 → **1.000** | 75.0 cm | 35.4 cm |
| attachment | 掉落物 | 0.500 → 0.500 | 35.4 cm | 35.4 cm |
| ammo | 展示框 | 0.500 → 0.500 | 43.3 cm | 43.3 cm |
| attachment | 展示框 | 0.500 → 0.500 | 43.3 cm | 43.3 cm |
| ammo | 展示柜/挂板/雕像 | 0.500 → **1.000** | 86.6 cm | 43.3 cm |
| attachment | 展示柜/挂板/雕像 | 0.500 → **1.000** | 86.6 cm | 43.3 cm |

> 上表是「本实现补齐后」与「修正前（无 display 消费）」的差；第三列是 1.7.10 Forge 帧外层缩放，
> 与实际显示的净尺寸还乘上 TACZ 自有 `display.ammo/attachments` 的 `transform.scale`（body 内不变）。
> 关键量级：**弹药盒在第三人称/掉落物里此前只有正确尺寸的 37.5%/50%**；配件第一人称此前旋转 45°
> 且尺寸 0.4（应为 0.68）；掉落物/展示框此前整体偏半格（0.5 格 = 25cm 量级）。

### 3.4 与已有实现的取舍

- **不新增变换数学**：display 变换本体复用 `BlockItemTransforms.ItemTransform`（1.20 `ItemTransform`
  的逐行等价物，r6/r7-model 已复核 `apply` 正确）。该类属于 block display 语义，但内部 `ItemTransform`
  就是 1.20 的 `ItemTransform` 类本身（translation÷16 + clamp + rotationXYZ 单四元数），直接复用最省事。
- **新增 `ItemModelDisplay`**：1.20 item model 的容器语义还差一条 **`ItemTransforms.Deserializer` 的左手回退**
  （`ItemTransforms.java:91-101`；`BlockItemTransforms` 无此逻辑，其 JSON 由调用方自行处理）⇒ 新建容器类补回，
  不修改已复核的 `BlockItemTransforms`（r7-model scope 文件，且其消费端当前只有右手上下文）。
- 读取走 `IResourceManager#getResource("models/item/<name>.json")`（1.7.10 `FallbackResourceManager`
  → `AbstractResourcePack#locationToName` 自动加 `assets/<domain>/` 前缀），与
  `BlockModelGuiItemRenderer` 读 block model JSON 的既有写法一致；`InternalAssetLoader#onResourceReload`
  清缓存，资源包覆盖可生效。

---

## 四、改动清单

| 文件 | 类型 | 内容 |
|---|---|---|
| `src/main/java/com/tacz/guns/client/resource/ItemModelDisplayManager.java` | **新增**（312 行） | ① 读取/缓存 `models/item/*.json` 的 `display`（`InternalAssetLoader` 重载时清缓存）；② `displayContextOf`：`ENTITY`+（`RenderItem.renderInFrame`/`DisplayItemRenderContext.isActive()`）→ `FIXED`；③ `applyDisplayTransform`：§3.1 的分支补偿；④ `packedLightFor` + `pushPackedLight/popPackedLight`：static 路径的亮度经 ThreadLocal 传递（第二轮，见 §八-2） |
| `src/main/java/com/tacz/guns/client/resource/pojo/model/ItemModelDisplay.java` | **新增**（72 行） | 1.20 `ItemTransforms` 等价 POJO：8 个条目 + `getTransform(ctx)` + 左手回退 |
| `src/main/java/com/tacz/guns/client/renderer/item/AmmoItemRenderer.java` | 修改 | `ITEM_MODEL` 常量；`shouldUseRenderHelper` 按 §3.2；`renderItem` 先 `applyDisplayTransform`、context/light 走管理器 |
| `src/main/java/com/tacz/guns/client/renderer/item/AttachmentItemRenderer.java` | 修改 | 同上（`item/attachment`） |
| `src/main/java/com/tacz/guns/client/renderer/item/GunSmithTableItemRenderer.java` | 修改（第二轮扩写） | ① `0xF000F0` → `packedLightFor`（第一轮 K2）；② 第二轮接入 `applyDisplayTransform`+`displayContextOf`+`shouldUseRenderHelper`（物品模型按注册名取 `models/item/<path>.json`），`renderByItem` 内 TACZ `transforms` 层与 r6 GUI 逻辑保持原样（见 §八-1） |
| `src/main/java/com/tacz/guns/client/renderer/block/StaticItemRenderHelper.java` | 修改（第二轮，Lead 授权） | `renderStatic` 在调用自定义渲染器前后 `pushPackedLight/popPackedLight`（ThreadLocal、finally 恢复、支持嵌套）；类注释的「不读物品模型 JSON / FIXED 退化」过期描述更新（见 §八-2） |
| `src/main/java/com/tacz/guns/client/resource/pojo/display/block/BlockItemTransforms.java` | 修改（第二轮，Lead 授权） | `getTransform` 补 1.20 左手回退（`ItemTransforms.java:91-101`）；未动 translation÷16/clamp/rotationXYZ（见 §八-3） |
| `src/main/java/com/tacz/guns/client/resource/InternalAssetLoader.java` | 修改 | `onResourceReload()` 首行清 item model display 缓存 |
| `src/main/java/com/tacz/guns/client/renderer/item/BlockModelGuiItemRenderer.java` | **未改** | 无 `0xF000F0` 字面量（见 §五-3） |

未运行 gradle；以上文件 IDEA `lint_files(min_severity="error")` = 0 error（第一轮 + 第二轮各复查一次）。

---

## 五、跨模块发现（第一轮结论 + 第二轮处置）

> 第一轮的 3 条跨模块项已由 Lead 裁决转本任务收尾（追加授权），处置见 §八；以下保留原始发现与结论。

1. **`StaticItemRenderHelper`（`client/renderer/block`）传递不了 `packedLight`**：
   1.20 的 `renderStatic(stack, FIXED, packedLight, …)` 有亮度参数，而 1.7.10 `IItemRenderer#renderItem`
   没有。它现在只能 `OpenGlHelper.setLightmapTextureCoords`（会写 `lastBrightnessX/Y`），但 mcshim
   `Tessellator` 是**逐顶点** uv2，模型渲染仍然吃掉显式传入的 packedLight ⇒ 之前展示柜/挂板/雕像里的
   配件恒满亮（非发光挂板夜里也亮）。第一轮在 `ItemModelDisplayManager#packedLightFor` 里从
   `OpenGlHelper.lastBrightnessX/Y` 还原（只对 ammo/attachment 生效，方案临时）；第二轮按根因改为
   `ItemModelDisplayManager#pushPackedLight/popPackedLight` 的 ThreadLocal 传递，并删掉 lightmap 还原 hack（§八-2）。
2. **`StaticItemRenderHelper` 的类注释已过期**：`StaticItemRenderHelper.java:52-53` 写「本类不读物品模型 JSON」，
   现在 ammo/attachment/工作台经它 → `IItemRenderer` → 本管理器，**会**读并消费 `display`；第二轮已更新该注释
   并写明「调用方必须包在 `DisplayItemRenderContext.render(...)` 里」（§八-2）。
3. **`BlockModelGuiItemRenderer` 的 K2 结论不成立**：全仓 `grep -n "0xF000F0"` 仅命中
   `LightTexture.FULL_BRIGHT` 常量、注释与 `GunSmithTableItemRenderer:51`（已修）；该类是 r6 新增的
   GUI-only 渲染器（`handleRenderType` 仅 `INVENTORY`），用 `Tessellator`+GL 固定管线光照绘制方块图标，
   不消费 `packedLight`，没有可替换的写死值。若要求它也在世界上下文中使用，需要先让它接管非 INVENTORY
   分支，属另一项工作。
4. **`GunSmithTableItemRenderer` 原先只做了 light**：它的物品模型 `gun_smith_table.json`/`workbench_*.json`
   无 `display` 段（display 恒等），但外层帧补偿/上下文仍需处理；第二轮已接入 `applyDisplayTransform`+
   `displayContextOf`+helper 映射（§八-1）。
5. **`BlockItemTransforms` 缺 1.20 左手回退**（`ItemTransforms.java:91-101`）：第二轮已在 `getTransform`
   补上（懒回退，语义与上游反序列化阶段回退等价）；当前消费端恒传右手 ⇒ 行为不变（§八-3）。
6. **`AmmoStorageBlock` 没有 TESR/静态渲染路径**：全仓 `StaticItemRenderHelper` 只有展示柜/挂板/雕像
   三个调用点；弹药库的弹药盒显示走 GUI（`BlockModelGuiItemRenderer` 的 INVENTORY 分支），不经过
   static 路径，因此无需统一。

---

## 六、平台差异 / 已知降级（不修，记录）

1. **左手/副手**：1.7.10 无双持槽、`IItemRenderer` 无左手入口 ⇒ `FIRST_PERSON_LEFT_HAND`/
   `THIRD_PERSON_LEFT_HAND` 的 display 数据（含左手回退）已解析但无渲染路径触发（与 r7-tp §六-1 一致）。
2. **HEAD 上下文**：1.7.10 没有「物品戴头上」的原版渲染 ⇒ `display.head` 仅解析不使用
   （`ItemModelDisplay.head` 字段保留）。
3. **掉落物堆叠**：1.7.10 Forge 的 `renderEntityItem` 只在 3D 方块分支按 `count` 循环；自定义渲染器
   只被调用一次 ⇒ 掉落一堆弹药（stackSize>1，1.20 会画多份带随机 3D 偏移的迷你物品）在 1.7.10 只画一份。
   与 display 无关，属 Forge 渲染入口差异。
4. **动画瞬态**：§3.1 的相消依赖「1.7.10 equip 进度 `1-f1` == 1.20 `f3`」与待机（attackAnim=0）。
   切枪升降/挥动过渡期间两版帧的中间项不完全相同 ⇒ 过渡动画有轻微差异，静止后精确。
5. **手臂骨骼帧**：`F_17`/`F_20` 均不含 `postRender(0.0625)` 与 `translateToHand` 的差（r7-tp 对枪械
   采用同一裁决）。mob 持物（`RenderBiped:263-264` 的基座与 `RenderPlayer` 相同，纸片分支也逐值相同）理论上
   同样适用；但**幼年生物**的外层帧（1.7.10 `T(0,0.625,0)·Rx(-20)·S(0.5)` vs 1.20 `T(0,0.75,0)·S(0.5)`）
   不同 ⇒ 幼年 mob 手里/展示柜里的物品有平台差异（枪械同理）。
6. **展示框外层帧**：1.20 `ItemFrameRenderer` 的 `T(0,0,0.4375)`/`Rz(k*45°)` 与 1.7.10 `RenderItemFrame`
   的 `T(-0.453125·dir)`/`Rz(-90·rotation)` 摆位组合不同；本任务只对齐「物品自身帧」（S(0.5)/display/-0.5），
   外层朝向沿用两版原版，属平台差异（与 r7-tp §六-6 对枪械的判定一致）。
7. **FP 纸片帧的 `S(2.5)` vs 2D 物品**：ammo/attachment 的 `INVENTORY` GUI 图标不受影响（display.gui 均缺省、
   `applyGuiSlotScale` 逻辑未变）。

---

## 七、实机验证清单（按优先级）

1. **第一人称手持弹药盒/配件**（主手）：弹药盒应贴着手部、大小约 0.5 模型单位（旧：旋转 45°、明显更小）；
   配件应与枪械瞄具槽/握把方向自然，缩放 0.68（旧：45° 歪斜、0.4）。**这是 D-1/D-2 的直接验收点**。
2. **第三人称（F5×1/F5×2）手持弹药盒/配件**：弹药盒尺寸应约为旧版 2.7 倍（0.375→1.0 外层），
   配件 0.375→0.55；位置贴合右手视觉。与同机位枪械的 r7-tp F-1/F-3 修复一起看是否同一套坐标系。
3. **掉落物**：弹药盒/配件落地后应居中、自转、有浮沉；弹药盒为全尺寸（旧：半尺寸、不自转）。
   与枪械（AnimateGeoItemRenderer 的 F-4 修复）对比，两者落点/大小基准应一致。
4. **物品展示框**：放弹药盒/配件，应居中（旧：整体偏 0.5 格）；配件应有 `fixed` 的 `Ry(180)` 朝向
   （旧：无）。
5. **展示柜 / 挂板 / 雕像**：放配件，尺寸应恢复为全模型（旧：半尺寸）、位置居中；**非发光挂板夜里**
   配件不应发亮（第二轮改成 `pushPackedLight` ThreadLocal 透传，见 §8.2/§8.5）。
6. **GUI 回归**：背包/创造栏里弹药盒、配件的图标（Slot 贴图）与 r6 修复后一致（display.gui 缺省 ⇒ 不应有变化）。
7. **切枪过渡**：切到/切走弹药盒/配件时位置过渡是否自然（§六-4 的瞬态差异，可接受）。
8. **mob 持物（可选）**：`/summon Zombie ~ ~ ~ {Equipment:[{id:"tacz:attachment",...}]}` 观察第三人称持物
   是否与玩家一致（幼年 mob 会有平台差异，§六-5）。

**必须实机才能定/需要日志的项**：
- 第三人称/第一人称的 display 旋转方向（JOML `rotationXYZ` 与 1.20 相同库同版本，静态等价；但最终观感需看图）；
- 展示柜/挂板补偿 `S(2)` 的视觉落点（依赖 `StaticItemRenderHelper` 的 `S(0.5)` 恰为唯一外层缩放，静态已核对源码）；
- 非发光挂板的亮度是否与周围方块一致（第二轮 ThreadLocal 透传，静态已核对传递链；实机确认无其它批次覆盖）。

> 第二轮（工作台 / StaticItemRenderHelper 亮度 / 左手回退）的追加改动与实机判据见 **§八**。

---

## 八、第二轮（Lead 裁决追加：工作台、静态渲染器亮度、左手回退）

> 背景：第一轮报告 §五 的 3 条跨模块项，Lead 裁决全部在本任务收尾（追加授权，r7-block/r7-model 已 inactive，
> 无并发冲突）。约束同前：未跑 gradle，`lint_files(error)=0`。

### 8.1 `GunSmithTableItemRenderer` 接入 display/context（本 scope）

**上游依据**：
- 物品模型 `assets/tacz/models/item/{gun_smith_table,workbench_a,workbench_b,workbench_c}.json` 均只有
  `parent: builtin/entity`（无 `display` 段）⇒ 1.20 外层 display 恒等，只余 `ItemRenderer.java:129` 的无条件 `T(-0.5)`；
- 上游 `GunSmithTableItemRenderer.java:30-58`（BEWLR）自己再施加 TACZ 展示数据
  `display/blocks/*.json` 的 `transforms`（1/16 单位、按 `ItemDisplayContext` 取条目，
  `gun_smith_table.json:6-42` 有 thirdperson/ground/gui/fixed 等），本仓由 `renderByItem` 的
  `BlockItemTransforms` 段逐行承担 —— 这段**保持不变**。

**改动**（`client/renderer/item/GunSmithTableItemRenderer.java`）：
- 物品模型位置：1.20 按注册名取 `models/item/<path>.json`，1.7.10 用 `Item.itemRegistry.getNameForObject(item)`
  反查得到 `tacz:gun_smith_table` / `workbench_a/b/c`，再映射成 `tacz:item/<path>`（兜底 `item/gun_smith_table`）；
- `shouldUseRenderHelper`：与 `AmmoItemRenderer` 完全相同（EQUIPPED/FP → `EQUIPPED_BLOCK`；ENTITY 非展示框 →
  `ENTITY_ROTATION`/`ENTITY_BOBBING`）；
- `renderItem`：`ItemModelDisplayManager.get(itemModel)` + `applyDisplayTransform(type, display, poseStack)` +
  `displayContextOf(type)` + `packedLightFor(type, data)`；
- `renderByItem`（TACZ `transforms` + 模型本体）：**逐行未改**。

**GUI 保持 r6 用法（逐值证明）**：改前净变换 = `F17·applyGuiSlotScale·[T(0.5)·taczGui·T(-0.5)]`
（`BlockModelGuiItemRenderer`/r6 校准的 `F17·applyGuiSlotScale = F20·T(-0.5)` 见 `RenderHelperCompat#applyGuiSlotScale`）；
改后 = `F17·applyGuiSlotScale·T(0.5)·display.gui(恒等)·T(-0.5)·[T(0.5)·taczGui·T(-0.5)]`
= `F20·T(-0.5)·T(0.5)·T(-0.5)·T(0.5)·taczGui·T(-0.5)` = `F20·taczGui·T(-0.5)` —— **与改前完全相同**。
即：`T(0.5)/T(-0.5)` 各多一次但成对抵消，r6 的 GUI 图标数学不变。

### 8.2 `StaticItemRenderHelper` 传递 packedLight（Lead 授权修改）

**问题**：1.20 `ItemRenderer#renderStatic(...)` 有 `packedLight` 参数，1.7.10 `IItemRenderer#renderItem` 没有；
静态路径（展示柜/挂板/雕像）里模型的逐顶点 uv2 由 TACZ 渲染器显式写入，`setLightmapTextureCoords` 被覆盖
⇒ 非发光挂板/夜里的物品恒满亮。第一轮用「读 `OpenGlHelper.lastBrightnessX/Y`」兜住（只对 ammo/attachment），
属临时方案。

**根因修法（Lead 建议的方案 B）**：
- `ItemModelDisplayManager` 新增 `ThreadLocal<Integer> CURRENT_PACKED_LIGHT` +
  `pushPackedLight(int) → Integer(旧值)` / `popPackedLight(Integer)`（保存旧值 ⇒ **嵌套调用安全**；
  ThreadLocal ⇒ 客户端渲染线程隔离）。`packedLightFor` 优先取该覆盖值，否则回退
  `RenderHelperCompat#packedLightFor`；**第一轮的 lightmap 还原 hack 已删除**（简化统一）。
- `StaticItemRenderHelper#renderStatic`：在调用自定义渲染器/原版图标的 `try` 里
  `pushPackedLight`、`finally` 里 `popPackedLight`（与既有 `pushToGL/popFromGL` 的 finally 同构）。
  `setLightmapTextureCoords` 保留（固定管线/原版图标路径仍依赖它）。
- 类注释更新：删掉「本类不读物品模型 JSON」等过期描述，写明 display 由 `ItemModelDisplayManager` 在
  `IItemRenderer` 侧消费、调用方必须包 `DisplayItemRenderContext.render(...)` 以便上下文还原为 `FIXED`、
  非 3D 分支 `S(0.5)` 会被 TACZ 渲染器按 `display` 帧补偿（§8.1/§三-3.1）。

**影响面**：经 `ItemModelDisplayManager#packedLightFor` 取亮度的渲染器（Ammo/Attachment/GunSmithTable）
全部吃到正确亮度；其它 mod 的 `IItemRenderer` 不调用本管理器，行为与之前一致（仍走 lightmap 纹理坐标）。
`AmmoStorageBlock` 无 TESR/静态路径（弹药盒显示走 GUI 的 `BlockModelGuiItemRenderer`），无需改动（§五-6）。

### 8.3 `BlockItemTransforms` 左手回退（Lead 授权修改）

- 1.20 `ItemTransforms.Deserializer`（`ItemTransforms.java:91-101`）在反序列化阶段把缺失的
  `thirdperson_lefthand`/`firstperson_lefthand` 复制为同组右手条目；本仓 `BlockItemTransforms` 由 GSON 直填字段，
  故在 `getTransform(context)` 里做等价懒回退：字段仍是 `ItemTransform.NO_TRANSFORM` 单例（= 缺失）时返回同组右手。
- 「显式写成 `{}` 的左手条目」不会回退：GSON 会新建恒等实例（`!= NO_TRANSFORM`）——与上游语义逐值一致。
- 未改 r6/r7-model 已复核的 `ItemTransform.apply`（translation÷16 + clamp + rotationXYZ 单次四元数）。
- **当前消费端**只有 `GunSmithTableItemRenderer`（恒传右手上下文、`apply(false, …)`）⇒ 行为不变；
  若将来 1.7.10 出现左手渲染入口（如 mod 双持），语义已正确，不需要再改。

### 8.4 第二轮数值复核（joml-1.10.5 临时程序，同一套矩阵）

工作台物品 model display 恒等 ⇒ 其外层帧补偿与 ammo 的恒等分支同一组公式，逐分支一致性检查结果：

| 检查 | `max|F_17·A − F_20|` | 结论 |
|---|---|---|
| workbench 第一人称（display 恒等） | 1.2e-7 | OK |
| workbench 第三人称/掉落物/展示框/展示柜 | 与 ammo 恒等分支相同（≤1.2e-7，第一轮已验） | OK |

修正前后（工作台外层帧；TACZ `transforms` 内层倍数两侧不变，未计入）：

| 分支 | 外层尺度 修正前→后 | 入口原点位移 | 模型中心位移 |
|---|---|---|---|
| 第一人称 | 0.400 → **1.000** | 62.2 cm | 0 |
| 第三人称 | 0.375 → **1.000** | 75.2 cm | 25.0 cm |
| 掉落物 | 0.500 → **1.000** | 75.0 cm | 35.4 cm |
| 展示框 | 0.500 → 0.500（只补 -0.5） | 43.3 cm | 43.3 cm |
| 展示柜/挂板/雕像 | 0.500 → **1.000** | 86.6 cm | 43.3 cm |
| GUI | 16.0 → 16.0（不变） | 0 | 0（§8.1 逐值证明） |

> 实际观感尺寸还要乘 TACZ `display/blocks` 的 `transforms.scale`（如 `gun_smith_table` thirdperson 0.24、
> firstperson 0.25、ground 0.25、fixed 0.5）——内层两侧相同，不影响「修正前 vs 修正后」结论。

### 8.5 第二轮实机判据（新增）

1. **创造栏/背包工作台图标**：与第二轮前逐像素一致（§8.1 GUI 逐值证明；若变化即回归）。
2. **手持工作台（第一/第三人称）**：外层尺寸应明显变大（第一人称 0.4→1.0、第三人称 0.375→1.0），
   方向由 TACZ `transforms` 决定，应与上游 1.20 同姿态。
3. **掉落物（工作台）**：全尺寸、居中、自转 + 浮沉（旧：半尺寸、不自转）。
4. **展示框（工作台）**：居中 + TACZ `fixed` transforms 生效（translation [4,-4,-3.5]/16、scale 0.5）。
5. **展示柜/挂板/雕像（工作台需先在 `gun_pegboard` 白名单配置里加入 `tacz:gun_smith_table` 等）**：
   全尺寸 + 居中 + `FIXED` transforms；**非发光挂板夜里** 物品亮度应与环境一致，发光挂板/展示柜仍满亮。
6. **非发光挂板放配件/弹药盒（第二轮亮度链路）**：夜里应与周围方块一致（`pushPackedLight` ThreadLocal 生效）；
   若仍满亮，判读点：`StaticItemRenderHelper#renderStatic` 是否走到 push/pop、`packedLightFor` 是否被调用
   （可在两处临时打一次日志，验证后删除）。
7. **嵌套调用安全性**：展示柜/挂板 TESR 内若再触发一次 `renderStatic`（理论上不会），ThreadLocal 保存/恢复
   旧值 ⇒ 内层结束后外层亮度不串（可临时用两处嵌套调用验证）。
