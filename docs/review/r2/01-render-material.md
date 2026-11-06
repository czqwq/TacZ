# r2-01 · 渲染/材质层审查

> **作用域**：`src/main/java/com/tacz/guns/client/model/**`（41 文件）、`client/resource/**`（87 文件）、
> `client/particle/**`（7 文件）；只读复核 `com/github/mcmodderanchor/**`（vendor simplebedrockmodel）、
> `mcshim/**`、`client/renderer/**`。写作用域外只报告不动手。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**` + `tmp/TACZ-1.20.1/src/main/resources/**`。
> **1.7.10 准绳**：`build/rfg/minecraft-src/java/**`（原版 + Forge 反编译源码）。
> **覆盖清单**：见 §四 复查清单（逐项写明「核了什么 / 结论是什么」）。
> **自查方式**：只用 IDEA MCP `get_file_problems(errorsOnly=true)` / `lint_files(min_severity=error)`
> —— 本轮全部改动文件 **0 error**。**未运行任何 gradle 命令**（含 `compileJava` / `runClient`），
> 所有结论来自源码对照与逐轴数值复算；标注 `runtime-verify` 的项需实机确认。

## 结论速览

**P0 = 0（本轮未发现「完全不渲染 / 每帧崩溃」的新增 P0）；已修 P1 = 2、已修 P2 = 2；未修 P1 = 1（Lead 自修）、P2 = 6。**

1. **【P1 已修】雪暴（snowstorm）粒子定义从不装载** —— 默认枪包**每一把枪**都是
   `"muzzle_flash": {"type": "renew"}`，renew 型旧四边形火光被 `MuzzleFlashRender` 直接 `return`
   跳过、改由 SBM 粒子系统表现；而装载这些定义的 `ParticleDefinitionLoader` 在本工程**没有任何驱动方**
   （上游是 simplebedrockmodel 模组自己在 `RegisterClientReloadListenersEvent` 里注册的，1.7.10 无该事件，
   vendor 时漏了）⇒ **第一/第三人称枪口火光与枪烟完全不显示**，每次开火刷一条 warn。
   另有第二处断点：该 loader 的 `prepare()` 过滤条件与本工程 `mcshim.ResourceManager` 的 id 规则不兼容
   （扩展名已被剥掉），即使驱动了也会扫到 0 条。已修（见 §一 P1-A）。
2. **【P1 已修】GUI / 背包图标上下颠倒** —— draw-gl 报的 P1，我用本仓源码 + 1.7.10 原版
   `RenderItem#renderIcon` 独立复算确认成立并已修（`scale(16,-16,16) + translate(0,-1,0)`），
   复算过程见 §一 P1-B 与 §四.7「与 draw-gl 报告的交叉核对」。
3. **【P2 已修】additive/emissive 粒子 RenderType 未把贴图纳入 `name`** ⇒ 不同贴图的粒子被
   `BufferSource` 合并进同一批次、最终只绑定第一张贴图（§一 P2-A）。
4. **【P2 已修，降级恢复】重载后的 `FirstPersonRenderHandler.reset()`** —— 上一轮因 SBM 未 vendor 而留成
   no-op 的 TODO，本轮 SBM 已 vendor，已按上游原位置恢复（§一 P2-B）。不恢复的后果是
   **F3+T / 换包后第一人称仍持有旧 `GunDisplayInstance` 的动画包装实例**，且
   **粒子时间步 `dt` 会被重载耗时撑到秒级**（下一帧粒子爆量/瞬移）。
5. **【未修 P1，Lead 自修】第一人称枪体 lightmap 失效** —— 本帧没有渲染过任何
   `RendererLivingEntity` 时，lightmap 纹理单元（unit1）的 `GL_TEXTURE_2D` 是关闭的
   （`disableLightmap` 关了、`renderHand` 被 cancel 后没人再 `enableLightmap`），顶点 lightmap 坐标被 GL 忽略
   ⇒ 枪体呈现「无光照全亮」。**结论方向与 Lead 一致，但触发是条件性的**（不是严格恒亮），
   证据与修正后的表述见 §二 P1-1 与 §四.7。
6. **材质绑定主链路是通的**：`RenderType#setupRenderState` → `TextureManager#bindTexture`
   → `SimpleTexture` 按完整路径 `assets/<ns>/<path>` 加载；缺资源时 `TextureManager#loadTexture` 的
   `IOException` 分支回退 `TextureUtil.missingTexture`（紫黑棋盘），**不会**出现空绑定或全黑。
   枪包贴图另有一套「合成 `.mcmeta`（clamp + no blur）」机制（`GunPackResourcePack`），专项领域正确。


---

## 一、已修问题（本轮实际改动）

### P1-A 【已修】雪暴粒子定义从不装载 → renew 型枪械枪口火光/枪烟全不显示

**现象（用户可见）**：开火时**看不到火光、看不到枪烟**（第一人称与第三人称都没有），`latest.log` 里每次
开火刷一条 `Muzzle particle definition tacz:muzzle_flash not found, skipped`。

**证据链（5 条，全部有文件:行）**

1. 默认枪包的每把枪都是 renew 型：
   `src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns/aa12_display.json`
   里 `"muzzle_flash": { "type": "renew", "texture": "tacz:flash/common_muzzle_flash", ... }`。
   （`renew` 是本轮直接从资源文件读到的字面值；`MuzzleFlash.Type` 的 `@SerializedName("renew")` 见
   `client/resource/pojo/display/gun/MuzzleFlash.java:13-15`。）
2. renew 型下旧四边形火光被显式跳过：`client/model/functional/MuzzleFlashRender.java:226-228`
   ```java
   if (muzzleFlash.isRenew()) {
       return;
   }
   ```
   ⇒ 火光只剩 SBM 粒子一条路。
3. 粒子发射器创建时若拿不到定义就丢弃：
   `client/particle/MuzzleParticleManager.java:569-574`（火焰）与 `:595-600`（枪烟）：
   ```java
   ParticleEffectDefinition definition = ParticleDefinitionLoader.getInstance().getDefinition(definitionId);
   if (definition == null) { GunMod.LOGGER.warn("Muzzle particle definition {} not found, skipped", definitionId); return; }
   ```
   第三人称同型：`client/particle/ThirdPersonMuzzleParticleManager.java:158,208`。
4. **`ParticleDefinitionLoader` 在本工程没有任何驱动方**（这是根因）：
   `grep ParticleDefinitionLoader` 全仓只有「定义」+「4 个读取点」，**0 处** `reload(...)` / `prepare`+`apply`
   调用；`com/github/mcmodderanchor/simplebedrockmodel/SimpleBedrockModel.java:12-26` 的类注释明确写着：
   上游那个模组入口（`@Mod("simplebedrockmodel")`）被 vendor 成库时只保留了常量，
   SBM 的 `RegisterClientReloadListenersEvent` 注册**没有搬过来**；1.7.10 也没有这个事件。
   ⇒ 缓存恒空，`getDefinition(...)` 恒 `null`。
5. 即便补上驱动，原 `prepare()` 也扫不到东西（第二处独立断点）：
   - `mcshim/ResourceManager.java:120-151` 的 `idFromPath` **已经把扩展名剥掉**，且 id 的 path 仍带目录段：
     `assets/tacz/particle_definitions/muzzle_flash.particle.json` → `tacz:particle_definitions/muzzle_flash.particle`。
   - 而 `ParticleDefinitionLoader.prepare()` 原过滤条件写的是
     `loc -> loc.getResourcePath().endsWith(".json")` ⇒ **恒 false**，全部定义被过滤掉。
   - 名字推导 `path.substring(DIRECTORY.length() + 1, path.length() - 5)`（按「保留 .json」假设写）
     在 1.7.10 的 id 上会**多截 5 个字符**，得到 `tacz:particle_definitions/muzzle_fl` 之类的错名。

**修法（3 处）**

| 文件:行 | 改动 | 依据 |
|---|---|---|
| `client/particle/ParticleDefinitionReloader.java`（**新增**，58-83 行） | `implements IGunPackReloadListener`，用 `GunPackResources.scanDirectory(GunPackLoader.INSTANCE.getPackSources(), GunPackResources.ASSETS_ROOT, "particle_definitions", ".json", GSON)` 扫包，再灌进 SBM 缓存 | 与 `ClientAssetsManager` 里 `bedrockModel`/`bedrockAnimation` 用 `GunPackResources.ASSETS_ROOT`（`ClientAssetsManager.java:191-209`）完全同构；id 形如 `tacz:muzzle_flash`，正是 `MuzzleFlash.DEFAULT_FIRST_PERSON_FLASH` 等默认常量期望的键（`MuzzleFlash.java:21-31`） |
| `client/resource/ClientGunPackReloader.java:68-74` | `LISTENERS.add(ParticleDefinitionReloader.INSTANCE);`（追加在末尾，**不改**已定稿的「数据管理器 → `ClientIndexManager::reload`」顺序） | 1.7.10 侧「资源重载监听器」的唯一落点是 `IGunPackReloadListener` + `ClientGunPackReloader`（`ClientGunPackReloader.java:96-112`）；上游等价的注册点是 `RegisterClientReloadListenersEvent` |
| `com/github/.../v1/particle/resource/ParticleDefinitionLoader.java:69-145`（**SBM vendor，标红**） | ① `prepare()` 改用新私有 `nameOf(ResourceLocation)`（剥 `particle_definitions/` 前缀 + 可选剥 `.json`），不再假设扩展名存在；② 抽出 `applyPrepared(Map<ResourceLocation, JsonElement>)` 公开入口，`apply(...)` 转调它 | vendor 层缺陷、必须改才能上驱动；`nameOf` 同时兼容「保留扩展名」的 1.20 形态与「已剥扩展名」的本工程形态 |

**核对**：`get_file_problems(errorsOnly=true)` 三个文件均 0 error。
**验证方法（runtime-verify）**：进世界开火，`latest.log` 应出现一次 `Loaded 2 particle definitions`
（默认包恰好两个 `*.particle.json`），且不再出现 `Muzzle particle definition ... not found`；
第一/第三人称应看到火光（`energy_swirl`/additive）与枪烟（`particles_blend`）。

---

### P1-B 【已修】GUI / 背包图标上下颠倒（draw-gl P1 独立复核后采纳）

**文件**：`client/model/RenderHelperCompat.java:302-317`（`applyGuiSlotScale`），
调用点 4 处（`client/renderer/item/AnimateGeoItemRenderer.java:91-93` 等，前一轮已接好）。

**现状 → 改动**

```java
// 改前（位置对、朝向反）
poseStack.scale(GUI_SLOT_SCALE, GUI_SLOT_SCALE, GUI_SLOT_SCALE);
// 改后
poseStack.scale(GUI_SLOT_SCALE, -GUI_SLOT_SCALE, GUI_SLOT_SCALE);
poseStack.translate(0.0F, -GUI_SLOT_TRANSLATE_Y, 0.0F);   // GUI_SLOT_TRANSLATE_Y = 1.0F
```

**独立复算（不依赖 1.20 源码，逐轴数值；详见 §五）**

内层（`renderToBuffer` 之前累积的矩阵）：
`M_inner = T(0.5,1.5,0.5) · Rz(180°) · T(0.5,1.5,−0.625)`（`GunItemRendererWrapper#renderSlotTexture:491-493`
的 `translate`+`mulPose`，与 `BedrockPart#translateAndRotateAndScale:116-133` 的 `bone.pos/16`）。
`SlotModel` 的南面 4 顶点（`SlotModel.java:32-38` + `BedrockCubePerFace.java:47-57`，depth=0 ⇒
xy = `(−16,−16),(0,−16),(0,0),(−16,0)`）先 `/16`（`BedrockCubePerFace.java:121-125`）再走 `M_inner`，得

| 顶点 | 内层结果 `(x, y, z)` | uv `(u, v)` |
|---|---|---|
| v5 | `( 1.0,  1.0, 0.46875)` | `(1, 0)` |
| v6 | `( 0.0,  1.0, 0.46875)` | `(0, 0)` |
| v7 | `( 0.0,  0.0, 0.46875)` | `(0, 1)` |
| v8 | `( 1.0,  0.0, 0.46875)` | `(1, 1)` |

（uv 由 `BedrockPolygon.java:23-26` 写死：`[0]→(u2,v1) [1]→(u1,v1) [2]→(u1,v2) [3]→(u2,v2)`；
`singleSouthFace` 的 uv=[0,0]/uvSize=[16,16]，见 `FaceUVsItem.java:22-31`、`FaceItem.java:15-20`。）

- 改前 `S(16,16,16)`：`v=0` 的两个顶点到 `y=16`，`v=1` 的到 `y=0`。
- **1.7.10 原版硬证据**：标准图标 quad 的映射在
  `build/rfg/minecraft-src/java/net/minecraft/client/renderer/entity/RenderItem.java:756-765`（`renderIcon`）：
  `(x, y+h) ↔ (minU, maxV)`、`(x, y) ↔ (minU, minV)` ⇒ **`v=0` 必须落在 y（格顶）**，`+Y` 向下。
  ⇒ 改前 `v=0` 在格底 ⇒ **上下颠倒，确认成立**。水平方向本就正确（`u=0` 在 `x=0`）。
- 改后 `S(16,−16,16)·T(0,−1,0)`：`y_out = 16·(1−w)`，`w∈[0,1]` ⇒ `y∈[0,16]` 且 `v=0` 回到 `y=0`。
  `z` 仍为 `16×0.46875 = 7.5`，位置与原来完全一致。

**回滚说明**（已写进方法注释）：若实机截图显示图标本来就该颠倒（即枪包 `slot` 贴图本身是倒着画的），
把方法体改回单行 `poseStack.scale(GUI_SLOT_SCALE, GUI_SLOT_SCALE, GUI_SLOT_SCALE);` 并删掉 `translate` 即单点回滚。

**1.7.10 侧事实核对（Lead 要求的第 4 条，全部核实 ✓）**

- `ForgeHooksClient#renderInventoryItem` 非 3D 分支
  （`build/rfg/minecraft-src/java/net/minecraftforge/client/ForgeHooksClient.java:187-205`）逐行：
  `glDisable(GL_LIGHTING)` → `glPushMatrix()` → `glTranslatef(x, y, −3.0F + zLevel)` → 可选 `glColor4f`
  → `customRenderer.renderItem(INVENTORY, item, renderBlocks)` → `glPopMatrix()` → `glEnable(GL_LIGHTING)`。
  **整个分支无 `glScalef` / `glRotatef`。**
- `INVENTORY` 唯一派发点 = `ForgeHooksClient.java:183/202`；其唯一调用点 =
  `RenderItem.java:583 renderItemAndEffectIntoGUI`。我另查了 `renderItemAndEffectIntoGUI` 的**全部 14 个调用点**：
  `GuiContainer:202/289`、`GuiContainerCreative:968`、`GuiIngame:973`（快捷栏）、`GuiBeacon:218-221`、
  `GuiMerchant:167/172/176`、`GuiAchievement:138`、`GuiAchievements:508` —— **全部是 GUI/HUD，无世界内入口**。
  ⇒ 16× 缩放不会外溢到物品展示框 / 掉落物 / 第三人称手持物。

---

### P2-A 【已修】additive / emissive 粒子 RenderType 未把贴图纳入 `name` → 不同贴图粒子串批、只绑第一张贴图

**文件**：`com/github/mcmodderanchor/simplebedrockmodel/v1/particle/render/ParticleRenderType.java:41-113`
（**SBM vendor，标红**）。

**证据链**

1. `ParticleRenderType.createAdditiveParticle/createEmissiveParticle` 用
   `RenderType.create("particle_additive", …)` / `("particle_emissive", …)` —— 而
   `mcshim/RenderType.java:317-320` 的 `create(...)` 直接 `new RenderType(...)`，**不经过**
   `RenderType.CACHE`（`:339-346`）。
2. `mcshim/RenderType.java:738-753` 的 `hashCode/equals` 只比较 `name/format/mode/bufferSize`
   （`equals` 是 `bufferSize == && Objects.equals(name, ...)`）⇒ **两种不同贴图的 `particle_additive`
   互相 `equals`**。
3. `mcshim/MultiBufferSource.java:72-81` 的 `BufferSource#getBuffer` 用
   `LinkedHashMap<RenderType, BufferBuilder>` 作键 ⇒ 第二种贴图**复用**第一种贴图的 builder；
   而 `drawBatch`（`:118-135`）只对**第一个** `RenderType` 调一次
   `setupRenderState()`，`:416-424` 里 `bindTexture` 只绑第一个类型自己的贴图
   ⇒ 所有 additive/emissive 粒子都被画成第一张贴图。

**修法**：`name` 拼上贴图（新增 `nameOf(String kind, ResourceLocation texture)`，`RenderType.create` 的
第一个实参改成 `nameOf("particle_additive", texture)` / `nameOf("particle_emissive", texture)`）。
`name` 只参与 equals/hashCode 与调试输出，不参与 GL 语义，零行为风险。

**影响面诚实说明**：当前默认包只有 `flash_strip.png`（`energy_swirl` → additive）与
`smoke_strip.png`（`particles_blend` → `entityTranslucent`，走 `RenderType.CACHE`，不受影响），
**单包下暂时看不出问题**；但任何枪包一旦有第二种 `energy_swirl`/emissive 贴图，第二个贴图的粒子就会用错贴图。
属「已修防御性 P2」。

---

### P2-B 【已修 · 降级恢复】资源重载后恢复 `FirstPersonRenderHandler.reset()`（task-9）

**文件**：`client/resource/ClientIndexManager.java:80-141`（`reload()` 收尾处）。
上一轮这里留的是「SBM 未 vendor ⇒ 降级为 no-op」的 `TODO(port)`。

**为什么现在能恢复**：SBM 已 vendor，`FirstPersonRenderHandler.reset()` 是真实方法
（`com/github/.../v1/client/handler/FirstPersonRenderHandler.java:142-151`），它清 8 项静态状态：
`realSelectedSlot / forceHandSwapFlag / mainOccupiedOffhand / wasFirstPerson /
MAIN_STATE.reset() / OFF_STATE.reset() / PARTICLE_SYSTEM.clear() / lastParticleTickNanos`。

**上游依据（逐行引用，不是我推测的位置）**：
`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/resource/ClientIndexManager.java:76-83`
```java
LocalPlayer player = Minecraft.getInstance().player;
if (player != null && IGun.mainHandHoldGun(player)) {
    AttachmentPropertyManager.postChangeEvent(player, player.getMainHandItem());
    IClientPlayerGunOperator.fromLocalPlayer(player).draw(ItemStack.EMPTY);
    FirstPersonRenderHandler.reset();      // ← 就在 draw 之后、同一个 if 内
}
```
本轮把调用放在**完全相同的位置**（`reload()` 末尾、`player != null && IGun.mainHandHoldGun(player)`
门内、`draw(null)` 之后）。

**位置理由（为什么不是「方法最前」也不是「无条件的方法末尾」）**

1. **不能放最前**（`clear()` 之前）：`reset()` 要清的是 `MAIN_STATE/OFF_STATE` 里指向**旧**
   `GunDisplayInstance` 动画实例的 `activeInstance`；旧 display 是 `clear()`（`:63` → `GUN_DISPLAY.clear()`）
   才被丢弃、新 display 是 `loadGunDisplay()`（`:65`）才创建 ⇒ 必须在这两步之后才有「清旧」的意义。
2. **不能无条件放在方法末尾**：上游把调用放在 `player != null && IGun.mainHandHoldGun(player)` 门内。
   玩家不在世界 / 主手不是枪时**根本不存在**第一人称枪械渲染状态，无条件调用只会白清一次
   （且把 `PARTICLE_SYSTEM.hasPrevCam` 打掉、多一次 `dt` 归零）。
3. **必须在 `draw(null)` 之后**：`draw()` 要在**新建**的 `GunDisplayInstance` 的状态机上重触发抽枪过渡，
   而 `reset()` 丢弃的是旧 display 的包装实例；顺序反了会把刚驱动起来的抽枪过渡再清掉。
   ⇒ 与上游 `:81-82` 的先后顺序一致。
   （注：Lead 提示的「热身之前」与上游不符 —— `warmUpInventoryModels()` 在**上游与移植版都在
   `try` 块内**（上游 `:70`、本仓 `:73`），把它挪到热身之前就等于挪到 `draw(null)` 之前，会破坏第 3 条。
   故按上游位置落在末尾。）

**物理侧守卫：确认无专服路径，因此不需要额外守卫（证据链）**

- 本类 `@SideOnly(Side.CLIENT)`（`ClientIndexManager.java:29`）；
- `reload()` **早已**在 `:80` 无条件使用 `Minecraft.getMinecraft()`（专服没有该类）⇒ 本方法本来就
  不可在专服执行，新增的 client 类引用**不引入任何新的物理侧类加载风险**；
- 两个调用点都只在客户端：
  `client/resource/ClientAssetsManager.java:216`（`register(ClientIndexManager::reload)`；
  `ClientAssetsManager` 本身 `@SideOnly(CLIENT)`，`:78`；其唯一 caller 是
  `ClientGunPackReloader.init()`，该类 `@SideOnly(CLIENT)`，`:48-49`）与
  `client/ClientProxy.java:446`（`ClientProxy` 只在 CLIENT 侧实例化）；
- 全仓 `grep ClientIndexManager` 的 38 处命中里，非 `client/` 包内的引用**全部是注释/javadoc**
  （`item/AmmoItem.java:95`、`item/ItemClientHooks.java:17,38`、`resource/GunPackLoader.java:57`、
  `resource/GunPackType.java:17`），**没有一处是代码调用**。

**清 `PARTICLE_SYSTEM` 是期望行为（3 条依据）**

1. `FirstPersonRenderHandler.reset()` → `PARTICLE_SYSTEM.clear()`
   （`v1/particle/firstperson/FirstPersonParticleSystem.java:221-224` = `discardEmitters(); hasPrevCam=false;`），
   丢弃的是**第一人称本机载体发射器**；已投递到原版 `ParticleEngine` 的世界空间粒子**不受影响**
   （同文件 `discardEmitters` 的 javadoc 明写）。重载后 `ParticleEffectDefinition` 整表被
   `client/particle/ParticleDefinitionReloader` 重建、贴图也被 `TextureManager` 重载，继续保留旧发射器
   只会拿旧定义/旧纹理继续喷 ⇒ 清掉正确。
2. `reset()` 把 `lastParticleTickNanos` 归零是**必须**的：F3+T / 换包的重载耗时可达数秒，
   不归零则下一帧 `dt = now - lastParticleTickNanos` 是「秒级」
   （消费点：`FirstPersonRenderHandler.java:355-357`），粒子会按巨大时间步积算出爆量/瞬移。
3. 「资源/世界上下文失效 → 清第一人称状态」是本仓既有约定：同一文件的
   `onPlayerLoggedOut`（`:137-140`）就是断线时调 `reset()`。

**核对**：`get_file_problems(errorsOnly=true)` = 0 error。**未跑 gradle**。
**验证方法（runtime-verify）**：F3+T 重载后手持枪 → 第一人称模型/动画应立即按新资源重建、
不会出现「旧枪模型残留一帧」；开火粒子不应爆量。


---

## 二、未修问题

### P0

**无。** 本轮未发现「完全不渲染 / 崩溃」的新增 P0；前两轮已修的 P0（第一人称相机空间基座、
`RenderHandEvent` 补偿、`TargetRenderer`/`ExternalShellRenderer` 判空、顶点语义）在本轮复查中
**全部仍在且正确**（见 §四）。

### P1-1 【未修 · Lead 自修】第一人称枪体 lightmap 条件性失效（「无光照全亮」）

- **文件**：`com/github/.../v1/client/handler/FirstPersonRenderHandler.java:409-535`（`onRenderHand`）；
  相关原版：`build/rfg/minecraft-src/java/net/minecraft/client/renderer/EntityRenderer.java:796,798,819-847,1434`。
- **现象**：第一人称枪体在暗处不暗（像自发光），且亮度**随画面里有没有生物而跳变**。
- **判定依据（逐条）**：
  1. 1.7.10 的 lightmap 走 GL_TEXTURE1：`Tessellator#draw()` 只在 `hasBrightness` 时
     `setClientActiveTexture(lightmapTexUnit)` → `glTexCoordPointer` → `glEnableClientState`
     （`Tessellator.java:122-129`），**它从不调用 `glEnable(GL_TEXTURE_2D)`**。
     真正打开该单元的是 `EntityRenderer#enableLightmap`（`:829-847`：`setActiveTexture(lightmapTexUnit)`
     → TEXTURE 矩阵 `scale(1/256)+translate(8,8,8)` → `bindTexture(locationLightMap)` → **`glEnable(GL_TEXTURE_2D)`**）。
  2. 原版手部 pass 在 `renderItemInFirstPerson` 之前调 `enableLightmap`（`EntityRenderer.java:796`）、
     之后调 `disableLightmap`（`:798`）；`disableLightmap`（`:819-825`）会 `glDisable(GL_TEXTURE_2D)`。
  3. `FirstPersonRenderHandler#onRenderHand` 在 `:1434` 的事件里 `event.setCanceled(true)`（`:518`），
     原版 `renderHand` 整个不执行 ⇒ **`enableLightmap/disableLightmap` 都不执行**。
  4. 事件前最后一次对 unit1 的操作是 `disableLightmap`（`:1352`，litParticles 之后）——即**关闭**；
     唯一的「补救」是 `RendererLivingEntity#doRender` 末尾无条件的
     `setActiveTexture(lightmapTexUnit); glEnable(GL_TEXTURE_2D); setActiveTexture(defaultTexUnit);`
     （`RendererLivingEntity.java:291-293`）。
  - ⇒ **精确表述**：本帧只要渲染过**任意** `RendererLivingEntity`（怪物/其他玩家），unit1 就恰好是开着的，
    lightmap 反而生效；一颗生物都没有时（如纯空旷地形/单机无怪）则 unit1 关闭，
    顶点 lightmap 坐标被 GL 忽略 ⇒ 枪体变成纯贴图色（相当于满亮）。
    **不是严格恒亮，而是「随画面内容跳变」**——对用户而言同样是「枪的亮度不对」。
- **建议修法**（Lead 已认领）：在 `onRenderHand` 的 cancel 路径内，按 vanilla 顺序补
  `EntityRenderer#enableLightmap` / `disableLightmap` 的等价操作
  （不能直接调私有方法，需在 `renderFirstPerson` 之前
  `OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit); GL11.glEnable(GL11.GL_TEXTURE_2D);`，
  并保证 TEXTURE 矩阵与 lightmap 贴图已由 `EntityRenderer` 更新；收尾再 `glDisable` 还原）。
  只补 `GL_TEXTURE_2D` 是最小改动，但 TEXTURE 矩阵是否处于 `enableLightmap` 设置过的状态依赖
  `:1345` 那次调用（同一帧内未被复位）⇒ 建议先按最小改动实机验证亮度是否随光照变化。
- **置信度**：**高**（1.7.10 源码逐行）。

### P1-2 【已由 Lead 裁决 + 自修】`RenderType` 两个 cutout 工厂的实参错位

- **文件**：`mcshim/RenderType.java:59-71`（`entityCutoutNoCull`）、`:74-81`（`entityCutoutNoCullZOffset`），
  与 `State.opaqueCutout(boolean cull, boolean polygonOffset)`（`:632-638`）。
- **问题**：
  - `entityCutoutNoCull` 传 `State.opaqueCutout(false, true)` ⇒ `polygonOffset = true`（1.20 的
    `entityCutoutNoCull` **没有** polygon offset）。
  - `entityCutoutNoCullZOffset` 传 `State.opaqueCutout(true, true)` ⇒ **`cull = true`**（1.20 的
    `entityCutoutNoCullZOffset` 顾名思义是 **NoCull**）。
  ⇒ 两个工厂的 (cull, polygonOffset) 两元组被写成了「同一个」，与命名相反。
- **当前影响**：`grep` 全仓这两个工厂**0 个调用点** ⇒ 无现网影响，仅潜在。属 api-shim 写作用域。
- **裁决与落地（Lead，本轮）**：Lead 确认 `State.opaqueCutout` 第 1 参是 `cull`、第 2 参是 `polygonOffset`，
  已把这两个 0 调用点的工厂改成 `(false, false)` / `(false, true)`。
- **⚠️ 与 Lead 裁决对齐的**关键一点**（本报告原「建议修法」已按裁决修正）**：
  `entityCutout`（20 处调用）、`entityTranslucentCull`（2 处）、`entityTranslucent`（25 处）的
  **`cull = false` 故意不改**。理由（Lead 裁决，我复核同意）：
  1. `cull = false` 是**安全超集** —— 只会多画背面、不会缺面；
  2. 在没有实机逐模型验绕序（triangle winding）的前提下把 cull 打开，一旦绕序不符就是**整片模型消失**，
     正是用户报的「不渲染」——风险方向与收益相反；
  3. 因此这两类只**登记为平台偏离**（见 §三 3.2），要改必须实机逐模型验绕序。
- **置信度**：**高**（1.20 命名语义 + 本仓 `State` 字段序）。


### P2-1 【未修】`MISSING_TEXTURE = minecraft:missingno` 在 1.7.10 不存在对应资源

- **文件**：`client/resource/index/ClientAmmoIndex.java:30-32,192`、
  `client/resource/index/ClientAttachmentIndex.java:46-51,221`。
- **现象**：`new ResourceLocation("minecraft", "missingno")` 被当作纹理 id 传给
  `RenderType.entityTranslucent/hr` → `TextureManager.bindTexture`。1.7.10 的
  `SimpleTexture#loadTexture`（`build/rfg/minecraft-src/.../texture/SimpleTexture.java:28-37`）
  **按完整路径原样打开** `ResourceLocation`（`assets/minecraft/missingno`），既没有 `textures/` 前缀补全、
  也没有 `.png` 后缀补全 ⇒ 必然 `IOException`。
- **为什么不算 P0/P1**：`TextureManager#loadTexture` 的 `catch (IOException)` 分支会把该 id 映射到
  `TextureUtil.missingTexture`（紫黑棋盘）并缓存，所以**视觉结果正是注释期望的「显眼占位」**，
  只是每次首用会刷一条 `Failed to load texture: minecraft:missingno`。属「行为对、日志脏」。
- **建议修法**（二选一，都不必急）：① 注释改成「靠 IOException 回退到 missingTexture」以正视听；
  ② 直接复用 `AttachmentRender.java:130` 已有的降级写法 `TextureMap.locationBlocksTexture`
  （画一块方块图集贴图，同样显眼且不刷 warn）。
- **置信度**：**高**。

### P2-2 【未修】`BulletHoleParticle` 在方块没有图标时静默不渲染

- **文件**：`client/particle/BulletHoleParticle.java:88-91,172-188,236-242`。
- **现象**：`getSprite()` 用 `Block#getIcon(side, metadata)`；拿不到时 `particleIcon` 保持 `null`，
  而 `renderParticle` 第一行就是 `if (!validAttachment || particleIcon == null || ...) return;` ⇒
  该弹孔**永不显示**。1.7.10 里 `getIcon` 返回 `null` 的方块并不罕见（纯 TESR/自定义渲染方块、
  未走 `registerBlockIcons` 的方块）。
- **判定**：上游用 `BlockModelShaper#getTexture(BlockState, Level, BlockPos)`（1.20 按烘焙模型取贴图，
  几乎必有值），1.7.10 无该设施，属**已记录降级**（`:166-170` 有 TODO(L2)），
  但「静默消失」这一点没有记录入档。
- **建议修法**：`getSprite()` 返回 `null` 时回退到 `Blocks.stone.getIcon(0,0)` 之类的固定图标
  （保证弹孔一定能看见），或至少 `GunMod.LOGGER.debug` 记一条。
- **置信度**：**高**（代码路径确定）；实际影响面取决于用户世界的方块种类，**中**。

### P2-3 【未修】`RenderType#clearRenderState()` 每批结束后强制改写全局 GL 状态

- **文件**：`mcshim/RenderType.java:488-499`，由 `mcshim/MultiBufferSource.java:127-133` 在**每个**
  batch 后调用。
- **问题**：1.7.10 固定管线是**全局**状态，而 `clearRenderState()` 无条件
  `glDisable(GL_BLEND)` / `glDisable(GL_ALPHA_TEST)` / `glEnable(GL_DEPTH_TEST)` / `glDepthMask(true)` /
  `glEnable(GL_CULL_FACE)` / `glDisable(GL_LIGHTING)` / `glEnable(GL_TEXTURE_2D)` / `glColor4f(1,1,1,1)`。
  本工程在 `RenderItem#renderItem`（世界内物品）与 TESR / GUI 内部都会触发 flush，
  ⇒ 之后**原版紧接着的绘制**会继承这套被改写的状态（典型是 `GL_LIGHTING` 被关掉后
  下一段原版实体/TESR 变暗或变亮）。
- **判定**：属「1.7.10 没有状态隔离」的架构性风险，静态无法证明一定出问题（原版多数入口会自行
  `RenderHelper.enableStandardItemLighting()` 重置），**未实机验证**。登记为已知风险，不建议本轮改。
- **建议修法（后续）**：在每批 flush 前用 `glIsEnabled` 快照、flush 后按快照还原（与
  `ScopeLensRenderer.RenderState`、`TextShowRender` 同款写法）。
- **置信度**：**中**。

### P2-4 【未修】`BedrockModel` 的模型级可变状态（`delegateRenderers` / `particleWindowRenderer`）

- **文件**：`client/model/bedrock/BedrockModel.java:39,44,527-539`。
- **问题**：`particleWindowRenderer` 用后置 `null`、`delegateRenderers` 渲染后整个换新 `ArrayList`。
  模型对象在同 id 枪械/实体之间**共享**（`GunDisplayInstance#getGunModel()`），同一帧内若同一模型
  被两个上下文渲染，第二次会看到第一次遗留的 delegate/particle 渲染器。
- **判定**：**与上游 1.20 逐行同构**（上游 `BedrockModel.java:391-402` 完全一样），不是移植引入。
  记录以免后续误判。
- **置信度**：**高**（已与上游对照）。

### P2-5 【未修】`GunDisplayInstance` 的懒加载会在**渲染线程 join 后台任务**

- **文件**：`client/resource/GunDisplayInstance.java:210-228`（`ensureModelLoaded` → `task.join()`）、
  `:769-772`（`getGunModel`）、`:896-900`（`getModelTexture`）、`:864-867`（`getAnimationStateMachine`）；
  后台线程池 `client/resource/ClientAssetLoadDispatcher.java:19-29`（**单线程** daemon）。
- **问题**：首次拿到某把枪（或资源重载后第一帧）时，渲染线程会阻塞等 `tacz-client-asset-preload`
  线程把该枪的 bedrock 模型/动画加载完 ⇒ **掉帧/卡顿**（不是不渲染）。
  单线程池的存在还意味着「一次进服预热多把枪」时后续枪械会排队。
- **判定**：上游 1.20 用 `CompletableFuture` + 后台线程且渲染侧同样会 `join`（`getGunModel` 也同步），
  语义同构；**1.7.10 全同步的代价就是主线程阻塞**，属已记录的架构差异。
- **建议修法**：`ClientIndexManager.warmUpInventoryModels()` 已经是「重载后预热手持+快捷栏+背包」，
  理论上前几帧就该就绪；若要彻底消除卡顿，可在 `ClientSetupEvent` 之后对默认包做一次全量预热。
- **置信度**：**高**（代码路径确定）；是否被用户感知 **中**。

### P2-6 【未修】`UnshadedVertexConsumer` 的法线降级依赖固定管线语义

- **文件**：`client/model/bedrock/UnshadedVertexConsumer.java`（`FALLBACK_NORMAL = (0,1,0)`）、
  `client/model/bedrock/BedrockPart.java:84-90`（`illuminated` → `LightTexture.pack(15,15)` + wrap）。
- **判定**：上游 1.20 靠 `RenderSystemAccessor` 读 shader 主光方向把法线缩放到满亮
  （`minecraft_mix_light` 饱和）；1.7.10 无 shader，法线只在 `GL_LIGHTING` 开启时影响方向性成分，
  而枪体 RenderType 的 `State.lighting` 恒 `false` ⇒ 法线对亮度**无影响**。
  真正的「满亮」由 `LightTexture.pack(15,15)` 的 lightmap 承担 ⇒ **语义等价**，
  但前提是 lightmap 单元可用（见 §二 P1-1）——即 `_illuminated` 部件与普通部件的亮度差
  在 lightmap 单元关闭时会一起消失。
- **置信度**：**高**（代码 + 1.7.10 固定管线语义）。

---

## 三、与上游的偏离登记（含 1.7.10 合理降级）

### 3.1 bedrock 烘焙链（与上游 `client/model/bedrock/**` 逐项对照，结论：**语义逐项等价**）

| 文件 | 对照方式 | 结论 |
|---|---|---|
| `bedrock/BedrockCubeBox.java` | 逐行对上游 98 行 | **等价**。uv 表 `p1..p9`、6 个 `polygons[]` 的面/顶点序/`dx dy dz` 取整、`mirror` 交换 `x/xEnd`、`inflate` 的 `−delta/+delta` 全部逐字相同；`compile` 的 `pose.pose()/pose.normal()` + `pos/16` + `Vector4f.mul(matrix4f)` 与上游逐字相同 |
| `bedrock/BedrockCubePerFace.java` | 逐行对上游 95 行 | **等价**。`getTexturedQuad` 的 `uv/uvSize → u1/v1/u2/v2`、`FaceItem.EMPTY → EMPTY_VERTEX` 分支、面序全部相同 |
| `bedrock/BedrockPolygon.java` | 逐行对上游 | **等价**。`vertices[0..3].remap(u2/W, v1/H)` 等的 4 条赋值逐字相同；`mirror` 反转顶点序 + `normal.mul(-1,1,1)` 相同；`normal` 来源 `Direction#step()` → `new Vector3f(ForgeDirection.offsetX/Y/Z)` |
| `bedrock/BedrockVertex.java` | 逐行对上游 | **等价**（`remap` 共享 `pos` 引用，与上游一致） |
| `bedrock/BedrockPart.java` | 逐行对上游 | **等价**。`translate(offset) → translate(x/16,y/16,z/16) → Rz→Ry→Rx → additionalQuaternion → scale` 的顺序与上游相同；`render` 的 `illuminated → pack(15,15)+wrap`、`visible` 门控、`push/compile/children/pop` 相同 |
| `bedrock/BedrockModel.java` | `render` 段 + `load*Model` 段逐行 | **等价**。`delegateRenderers`/`particleWindowRenderer` 的执行顺序与上游 `:379-402` 相同；`convertPivot/convertOrigin/convertRotation` 三组换算公式逐字相同 |
| `bedrock/ModelRendererWrapper.java` / `BedrockCube.java` | 归一化 diff | 仅 import / 换行差异 |
| `client/model/SlotModel.java` | 逐行对上游 35 行 | **等价**（`EntityModel` → `ModelBase` 换基类，`renderToBuffer` 保留为普通方法，几何逐字相同） |
| `client/model/BedrockAmmoModel.java` | 归一化 diff | **0 差异** |
| `client/model/FunctionalBedrockPart.java` | 逐行对上游 | **等价**（`functionalRenderer` 非 null 时**先执行**、`visible` 只在 null 分支生效，与上游注释一致） |

**`ForgeDirection` ↔ `net.minecraft.core.Direction` 严格核对**（本轮实测，不是"看起来一致"）：
`ForgeDirection.{DOWN,UP,NORTH,SOUTH,WEST,EAST}.offset{X,Y,Z}` =
`(0,-1,0),(0,1,0),(0,0,-1),(0,0,1),(-1,0,0),(1,0,0)`，与 1.20 `Direction#step()` 的
`Vec3i` 逐分量相同；`BedrockPolygon` 只用 `.offsetX/Y/Z`，所以**几何零差异**。

**`FaceUVsItem#getFace` 的「交叉映射」是上游怪癖，不是移植 bug**：
`client/resource/pojo/model/FaceUVsItem.java:38-54` 里 `EAST → west 字段`、`UP → down 字段`，
与上游 `FaceUVsItem.java:31-47` **逐字符相同**（已 diff 确认）。核对时不要把它当错。

### 3.2 `RenderType` / `MultiBufferSource` / `VertexConsumer`（1.7.10 合理降级）

| 项 | 1.20 | 本工程 | 判定 |
|---|---|---|---|
| 无 shader / RenderStateShard / CompositeState | 有 | 用 `RenderType.State` + `GL11/GL12/GL14` 固定管线 | 合理降级（`RenderType` 类注释 :31-48 已逐条记录） |
| 无 VBO / index buffer / 面排序 | 有 | `BufferBuilder` 存 Java 数组，`BufferUploader` 逐顶点重灌 `Tessellator` | 合理降级；顶点量大的枪械有明显开销（性能，不是正确性） |
| overlay（受伤红闪）顶点通道 | 有 | 1.7.10 顶点格式无该槽位，`overlayCoords()` 丢弃 | 合理降级（`BufferBuilder.java:278-282`） |
| `energySwirl` / glint / `entityShadow` 等附属 RenderType | 有 | 未移植 / 近似 | 合理降级；枪口火光的 `energy_swirl` 材质已降级为 additive 混合 |
| AcceleratedRendering / Oculus(Iris) / Angelica 加速与光影路径 | 有 | `ExternalGunItemVboRenderer` 恒 `UNSUPPORTED`、`renderLaserBeamAccelerated` 恒 `false`、`OculusCompat#endBatch` 实际恒 `false` | 合理降级，效果等价于「关掉静态网格缓存/加速，走完整模型渲染」 |
| `Arrow`/`ARCompat` 层缓冲 | 有 | 无 | 合理降级（`BedrockGunModel.java:400-402` 有 TODO(port)） |
| **背面裁剪（本项按 Lead 裁决登记为平台偏离，不改）** | `entityCutout` / `entityTranslucentCull` / `entityTranslucent` 均为 `CULL` | 本工程统一 `cull = false`（`RenderType.State.opaqueCutout(false, …)` / `State.translucent(false, …)`） | **有意偏离**：1.7.10 固定管线 + bedrock 模型绕序在**没有实机逐模型验证**前，`cull = false` 是**安全超集**（只多画背面、不缺面）；反过来若绕序不符而开了 cull，就是**整片模型消失**（正是用户报的「不渲染」）。要改必须实机逐模型验绕序。**Lead 已裁决：本轮不改，登记为偏离** |


### 3.3 材质绑定链（结论：**通**）

```
GunDisplay.modelTexture (ResourceLocation, e.g. tacz:textures/gun/ak47.png)
  → RenderType.entityCutout(texture)             // 按纹理缓存，见 RenderType.cached :339-346
  → MultiBufferSource.BufferSource.endBatch()     // :91-97
    → RenderType.setupRenderState()               // :416-424
      → Minecraft.getTextureManager().bindTexture(texture)   // 1.7.10 自动按需加载
        → SimpleTexture.loadTexture 用完整路径打开 assets/<ns>/<path>（SimpleTexture.java:28-37）
        → 失败 → TextureManager.loadTexture catch(IOException) → TextureUtil.missingTexture
```

- **枪包贴图能被解析**：`client/sound/GunPackResourcePack`（`IResourcePack`）挂进原版
  `IReloadableResourceManager`，每轮重载后重挂（`GunPackResourcePackReloadListener`
  + `GunPackResourcePack.ensureInstalled`），并为枪包 `.png` **合成**
  `{"texture":{"blur":false,"clamp":true}}` 的 `.mcmeta`（1.7.10 的
  `uploadTextureImageAllocate(clamp=false)` 默认 `GL_REPEAT`，会让零厚度面贴图边缘出杂色亮线）。
- **缺失资源不会黑图/空绑定**：`TextureManager#loadTexture` 的 `IOException` → `TextureUtil.missingTexture`。
- **`InternalAssetLoader` 的 5 个内置模型**固定用 `BedrockVersion.NEW`（`InternalAssetLoader.java:130-138`）：
  已核 `assets/tacz/models/bedrock/{target,target_minecart,basic_bullet,statue,gun_smith_table}.json`
  头两行全是 `"format_version": "1.12.0"` + `"minecraft:geometry": [` ⇒ **版本匹配，正确**。
- **`bedrockAnimation` / `bedrockModel` 的 `ASSETS_ROOT`**（前一轮 P0-2/P1-1 修点）复查仍在：
  `ClientAssetsManager.java:191-209` 两个 manager 都显式传 `GunPackResources.ASSETS_ROOT`，
  `GltfManager.java:30` 同样 ✓。

### 3.4 client/resource 加载完整性（结论：**主链路无断链**；已修 2 处缺口）

- `ClientAssetsManager.reloadAndRegister` 的注册顺序与上游逐项一致（gunDisplay → adsPresets →
  ammoDisplay → attachmentDisplay → attachmentVariantDisplay → slotAdapterDisplay → blockDisplay →
  bedrockModel → bedrockAnimation → gltf → scriptManager → packInfo → `ClientIndexManager::reload`），
  索引排在最后 ✓。
- 每个 `DisplayManager`/`LazyJsonDataManager` 都传了 `GunPackResources.ASSETS_ROOT` ✓；
  目录名与默认枪包实际布局一致（`gunpack_info.json` / `ads_presets` / `animations` / `display` /
  `geo_models` / `scripts` / `sounds` 均存在）✓。
- 缓存/代次：`ClientIndexManager.clear()` → `ClientAssetLoadDispatcher.invalidate()`（`GENERATION++`）
  → 同一轮内新建的 `ClientAssetLoadTask` 捕获的是**新**代次 ⇒ 不会「刚建就过期」✓
  （注意 `RESOURCE_REVISION` 与 `GENERATION` 是**两个**计数器，不要混）。
- **断链 1（已修）**：`particle_definitions` 没有任何 manager 负责 —— 见 §一 P1-A。
- **断链 2（已修，task-9）**：`ClientIndexManager.reload()` 末尾的
  `FirstPersonRenderHandler.reset()` 上一轮因 SBM 未 vendor 而留成 no-op；SBM 现已 vendor，
  `reset()` 是真实方法，本轮已按上游原位置恢复调用 —— 见 §一 P2-B。
- **热重载失效**：模型/贴图实例挂在 `GunDisplayInstance` 上，每次 reload 由
  `ClientIndexManager.clear()` 整体重建 ⇒ 旧实例不残留 ✓；但 `RenderType.CACHE`
  （`RenderType.java:56,339-346`）与 `ParticleRenderType.ADDITIVE_CACHE/EMISSIVE_CACHE`
  **跨 reload 不失效** —— 它们只缓存「贴图 id → RenderType」，`setupRenderState` 每次都会
  `bindTexture`，因此贴图换掉也能生效（`TextureManager` 自身在 reload 时重载）。
  结论：**不需要失效**，记录以免误判。

- **热重载失效**：模型/贴图实例挂在 `GunDisplayInstance` 上，每次 reload 由
  `ClientIndexManager.clear()` 整体重建 ⇒ 旧实例不残留 ✓；但 `RenderType.CACHE`
  （`RenderType.java:56,339-346`）与 `ParticleRenderType.ADDITIVE_CACHE/EMISSIVE_CACHE`
  **跨 reload 不失效** —— 它们只缓存「贴图 id → RenderType」，`setupRenderState` 每次都会
  `bindTexture`，因此贴图换掉也能生效（`TextureManager` 自身在 reload 时重载）。
  结论：**不需要失效**，记录以免误判。

### 3.5 序列帧动画材质 / Papi 文本

- **序列帧动画材质：不适用**。全仓 `src/main/resources` 里 `*.mcmeta` = **0 个**（上游 1 个是 `pack.mcmeta`），
  且 1.7.10 的 `SimpleTexture` 只读 `texture`（blur/clamp）段、**不支持** `animation` 段
  （`SimpleTexture.java:41-57` 只取 `TextureMetadataSection`）⇒ 本仓没有序列帧需求，无降级。
- **Papi 文本渲染：有真实输出**。`client/model/functional/TextShowRender.java:51-134`：
  仅 `transformType.firstPerson()` 生效（与上游一致），`PapiManager.getTextShow` → 经
  `bedrockModel.delegateRender(...)` 代理到模型批次之后，用
  `poseStack2.pushToGL()` + `font.drawString(...)` 直接画（`:109-132`），
  try/finally 里成对 `popFromGL` + 还原 lightmap/光照/混合。
  逐项核过 3 个 Papi（`PlayerNamePapi` / `AmmoCountPapi` / `RangefinderPapi`）注册在
  `PapiManager` 静态块（`:20-24`）✓；`GetTextShow` 用 `StatCollector.translateToLocal`
  （不用 `...Formatted`，避免 `%` key 被格式化）✓。

---

## 四、复查清单（逐项：核了什么 → 结论）

### 4.1 【必查 1】bedrock 模型烘焙链（9 个类）

| 检查点 | 核了什么 | 结论 |
|---|---|---|
| cube UV 映射 | `BedrockCubeBox` 的 `p1..p9` / 6 面 `(u1,v1)-(u2,v2)`；`BedrockCubePerFace` 的 `uvSize` 相加；`BedrockPolygon` 的 4 条 `remap` | ✅ 与上游逐字相同 |
| `uvRotation` | 上游本分支**没有** uvRotation（`BedrockCubeBox` 不接收该参数）；本仓也没有 | ✅ 无此维度，不存在遗漏 |
| `flip`（`flip_u/flip_v`） | 上游 `FaceItem` 只有 `uv` / `uv_size`，**无 flip 字段**；本仓 `FaceItem.java` 相同 | ✅ 无此维度 |
| 面裁剪（`inflate` / `size`） | `delta` 的 `−delta/+delta`；`(int)width/height/depth` 取整 | ✅ 相同 |
| 镜像 | `mirror` 的 `x↔xEnd` 交换（Box）、顶点序反转 + `normal.mul(-1,1,1)`（Polygon） | ✅ 相同 |
| 旋转 | `translate → Rz → Ry → Rx → additionalQuaternion → scale` 顺序 | ✅ 相同（`BedrockPart.java:116-133`） |
| 顶点颜色 | `color(red,green,blue,alpha)` 从 `BedrockPart#render` 透传；`SlotModel` 忽略传入颜色（上游同） | ✅ 相同 |
| 光照 / lightmap | `light` 透传；`illuminated` → `LightTexture.pack(15,15)` | ✅ 相同（固定管线差异见 §二 P2-6） |
| 法线 | `pose.normal()` 变换 `polygon.normal` | ✅ 相同；`PoseStack.scale/mulPose/rotateAround` 都同步维护 normal（`PoseStack.java:69-114`），`mulPoseMatrix` 不动 normal（1.20 同） |
| `visible` / 骨骼矩阵应用顺序 | `if (visible)` 门控 + `push → translateAndRotateAndScale → compile → children → pop` | ✅ 相同；`FunctionalBedrockPart` 的「visible 优先级低于 functionalRenderer」也相同 |
| `UnshadedVertexConsumer` | 固定法线 `(0,1,0)` 降级 | ✅ 已记录降级，语义在 1.7.10 下等价（§二 P2-6） |
| `ModelRendererWrapper` | 纯 getter/setter 包装 | ✅ 相同 |

**结论：未发现「贴图错位 / 白模 / 黑模 / 只有部分面」的新增根因。**
（前一轮已修的 P0「`vertex()` 立即落顶点导致 UV 错位一格」在本轮复查中确认修复仍在：
`get_file_problems(errorsOnly=true)` 0 error。）

### 4.2 【必查 2】材质绑定全链路

| 检查点 | 核了什么 | 结论 |
|---|---|---|
| `ResourceLocation` → GL texture id | `RenderType.setupRenderState` → `TextureManager#bindTexture`；`SimpleTexture` 按完整路径打开 | ✅ 通 |
| `IIcon` 路径 | 只有 `BulletHoleParticle`（方块图集）与 `AttachmentRender` 的缺失占位用 `IIcon`/图集；枪械走独立 PNG | ✅ 通 |
| 缺失回退 | `TextureManager#loadTexture` 的 `IOException` → `TextureUtil.missingTexture` | ✅ 紫黑棋盘，不是空绑定/黑图 |
| 枪包贴图可达性 | `GunPackResourcePack` 挂 `IReloadableResourceManager` + 每轮重挂 + 合成 `.mcmeta` | ✅ 通 |
| 内置贴图 | `InternalAssetLoader` 的 `ENTITY_EMPTY_TEXTURE` 等 5 个常量路径在 `assets/tacz/textures/**` 存在 | ✅ 通 |
| `MISSING_TEXTURE` 常量 | `minecraft:missingno` 靠 IOException 间接回退 | ⚠️ P2-1（行为对、日志脏） |

### 4.3 【必查 3】渲染开关 / 混合模式 / alpha test

| 检查点 | 核了什么 | 结论 |
|---|---|---|
| 不透明 | `entityCutout` → `State.opaqueCutout(false,false)` → `glDisable(BLEND)` + `glEnable(ALPHA_TEST)` + `glAlphaFunc(GREATER,0.1)` + `glDepthMask(true)` | ✅ **alpha test 确实被显式打开**（这是"模型完全看不见"的经典根因，本仓没有踩） |
| 半透明 | `entityTranslucent` → `State.translucent(false,false)` → `glEnable(BLEND)` + `depthWrite=false` | ✅ 与 1.20 语义一致 |
| 发光 | `eyes`/`beaconBeam` → `State.emissive()`；`additive` → `glBlendFunc(SRC_ALPHA, ONE, ...)` | ✅ 落到对应 GL 模式 |
| 加法混合 | `ParticleRenderType.additive/emissive` + `State.additive` | ✅ 同上 |
| 光照 | `State.lighting` 默认 `false` → `glDisable(GL_LIGHTING)` | ✅ 与 1.20 entity shader 一致（靠顶点色 + lightmap） |
| 背面裁剪 | `entityCutout` / `entityTranslucentCull` / `entityTranslucent` 实际都是 `cull=false`（1.20 是 CULL） | **有意偏离（Lead 裁决：登记不改）**。`cull=false` 是安全超集（只多画背面、不缺面）；没实机验绕序就开 cull 会导致整片模型消失 = 用户报的「不渲染」。详见 §二 P1-2 与 §三 3.2 |
| 批次结束还原 | `clearRenderState()` 强制改写全局状态 | ⚠️ P2-3 风险 |

### 4.4 【必查 4】client/resource 加载完整性

逐项核了：6 个 `DisplayManager` 的目录/根/顺序、`LazyJsonDataManager` 的懒加载与 `getData` 并发保护、
`ClientIndexManager` 的 clear→load→preload 顺序与两个代次计数器、`ClientAssetLoadTask` 的
`warmUp/getIfReady/isPending` 三者语义、`ExternalModelAssets` 的 `prepare/preload` 差异、
`GunDisplayInstance` 的 4 组 `ensure*Loaded` 与失败处理（`handle*LoadFailure` 幂等）、
`InternalAssetLoader` 的 5 个内置模型 + 2 个默认动画、`PackInfoManager`/`GltfManager` 的根目录、
`FaceUVsItem`/`FaceItem`/`BedrockModelPOJO`/`BedrockVersion` 的 POJO 契约。
**结论：主链路无断链；唯一缺口是 `particle_definitions`（已修，§一 P1-A）。**

前两轮修点本轮**逐条复查仍在且正确**：`PackInfoManager` 的 namespace 键（`:47,68,80,104-110`）、
`GltfManager` 的 `ASSETS_ROOT`（`:30`）、`ClientAmmoIndex/GunDisplayInstance` 的
`isNoneBlank(name)`（`ClientAmmoIndex.java:243`、`GunDisplayInstance.java:722`）、
`GunDisplayInstance` 默认动画 for-each 判空（`:558-559,567-568`）、
`ClientAssetsManager` 的 `bedrockAnimation` / `bedrockModel` 双 `ASSETS_ROOT`（`:191-209`）。

### 4.5 【必查 5】粒子（`client/particle/**` 7 文件，逐个读）

| 文件 | 核了什么 | 结论 |
|---|---|---|
| `BulletHoleParticle.java` | 基类映射（`TextureSheetParticle→EntityFX`）、`getFXLayer()==1` 是否真指向方块图集、`renderParticle` 的 quad/uv/颜色/淡出、`rotationForDirection` 几何 | ✅ `getFXLayer()==1` 正确（`EffectRenderer#renderParticles` 的 `case 1` 绑 `locationBlocksTexture`，`EffectRenderer.java:137-141`）；几何与上游等价；⚠️ icon 为 null 时静默消失（P2-2） |
| `ParticleCompat.java` | 反射写 `particleMaxAge`、`doSpawnParticle` 的门限语义 | ✅ 降级已记录（1.7.10 有 16 格 + `particleSetting` 门限，上游没有） |
| `AmmoParticleSpawner.java` | `getParticle()==null` 回退链、count==0/≠0 两分支、`getDistanceSq>9` 门限 | ✅ 与上游同构 |
| `HitSurfaceParticleSpawner.java` | `ParticleSpec` 的四类映射（block/falling_dust/dust/named）、`EntityDiggingFX` 的 icon 来源、`countMin/Max` 交换 | ✅ `EntityDiggingFX` 自设 icon（`EntityDiggingFX.java:26`）；`EntityBlockDustFX extends EntityDiggingFX` 继承 `getFXLayer()==1` ✓ |
| `HitBlockEffectHandler.java` | `ModBlocks.TARGET` 判断、`shouldRemove` 的 AABB 相交、音效去重 | ✅ 与上游同构；1.7.10 无 `BlockState` 的替换合理 |
| `MuzzleParticleManager.java` | 4 个粒子 id 来源、`v.smoke_*` 变量命名空间隔离、`getWorldParticleScale` 的 FOV 补偿 | ✅ 逻辑严密；**依赖的 `ParticleDefinitionLoader` 缓存此前恒空 → 已修（§一 P1-A）** |
| `ThirdPersonMuzzleParticleManager.java` | 同上 + 第三人称锚点跟随 | ✅ 同上 |

**结论：粒子「不显示」的根因不在本包本身，而是上游 SBM 侧的定义加载缺口（已修）。**

### 4.6 【必查 6】动画材质 / Papi

见 §3.5：序列帧**不适用**（0 个 `.mcmeta`）；Papi 文本**有真实输出**。

### 4.7 「与 draw-gl 报告的交叉核对」专节

- **draw-gl P1（GUI 图标上下颠倒）：裁决 = 成立，已改。** 独立复算见 §一 P1-B。
  它给的主证据（`BedrockPolygon` 的 uv 顶点序 + 南面顶点 + `singleSouthFace`）我逐条复核**全部正确**；
  我补充了它缺的决定性一环——**1.7.10 侧 `v` 方向的硬证据**
  （`RenderItem#renderIcon`：`(x,y)↔minV`、`(x,y+h)↔maxV` ⇒ `v=0` 在格顶），
  以及「位置对/朝向反」这个组合的完整逐轴数值。
  它给的补丁 `scale(16,-16,16) + translate(0,-1,0)` 我也独立验算过（`y_out = 16(1−w) ∈ [0,16]`，
  `z` 仍 7.5，`u` 方向不受影响）⇒ **数值正确，已原样采纳**。
- **draw-gl 未覆盖的一点（我补核）**：`applyGuiSlotScale` 的**作用范围**。
  1.7.10 的 `INVENTORY` 唯一派发点是 `ForgeHooksClient#renderInventoryItem`，
  其唯一调用点是 `RenderItem#renderItemAndEffectIntoGUI`，而后者的 14 个调用点**全是 GUI/HUD**
  （`GuiContainer` / `GuiContainerCreative` / `GuiIngame` 快捷栏 / `GuiBeacon` / `GuiMerchant` /
  `GuiAchievement(s)`）⇒ 16× 缩放**不会**外溢到世界内渲染（物品展示框/掉落物/第三人称）。
- **Lead 转达的 lightmap 信息：登记 + 修正表述。** 见 §二 P1-1：方向正确（`enableLightmap` 不执行、
  `Tessellator` 不开 `GL_TEXTURE_2D`），但**不是严格「恒亮」**——`RendererLivingEntity#doRender` 末尾
  会无条件 `glEnable(GL_TEXTURE_2D)` 到 unit1（`RendererLivingEntity.java:291-293`），
  所以表现是「亮度随画面里有没有生物而跳变」。修法不变，但**验收标准应写成「亮度随环境光照变化且稳定」**，
  而不是「从恒亮变为响应光照」。
- 我按 Lead 要求在核模型侧光照代码时把「lightmap 单元是否被打开」当成检查维度：
  `mcshim/LightTexture` 的打包/拆包（`pack=(sky<<20)|(block<<4)`、`block/sky` 取位、`u=packed&0xFFFF`、
  `v=packed>>16`）与 1.7.10 `Tessellator#setBrightness→shortBuffer.position(14)→glTexCoordPointer(2,32,…)`
  的布局**逐位核对一致**（`LightTexture.java:66-90` vs `Tessellator.java:122-129,413-416`），
  `BufferUploader#draw` 的 `setBrightness` 也正确（`BufferUploader.java:79-81`）
  ⇒ **CPU 侧亮度值是对的，问题只在 GL 单元开关**，与 Lead 的判断吻合。

---

## 五、本轮改动文件清单

1. `src/main/java/com/tacz/guns/client/model/RenderHelperCompat.java`
   —— **P1-B** `applyGuiSlotScale`：`scale(16,16,16)` → `scale(16,-16,16) + translate(0,-1,0)`
   （`GUI_SLOT_TRANSLATE_Y`），注释含逐轴复算与单点回滚说明。
2. `src/main/java/com/tacz/guns/client/particle/ParticleDefinitionReloader.java`（**新增**）
   —— **P1-A** 雪暴粒子定义的 1.7.10 装载驱动（`IGunPackReloadListener`）。
3. `src/main/java/com/tacz/guns/client/resource/ClientGunPackReloader.java`
   —— **P1-A** 把上述 listener 追加进 `LISTENERS`（`:12` import、`:68-74`）。
4. `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/resource/ParticleDefinitionLoader.java`
   —— **P1-A（SBM vendor，需 Lead 知悉）** `prepare()` 适配本工程 `ResourceManager` 的 id 规则；
   新增公开入口 `applyPrepared(Map)`（`:69-145`）。
5. `src/main/java/com/github/mcmodderanchor/simplebedrockmodel/v1/particle/render/ParticleRenderType.java`
   —— **P2-A（SBM vendor，需 Lead 知悉）** `name` 拼上贴图，避免跨贴图串批（`:41-113`）。
6. `src/main/java/com/tacz/guns/client/resource/ClientIndexManager.java`（**task-9**）
   —— **P2-B** 恢复上游的「重载后 `FirstPersonRenderHandler.reset()`」调用
   （`reload()` 末尾、`player != null && IGun.mainHandHoldGun(player)` 门内、`draw(null)` 之后，
   与上游 `ClientIndexManager.java:76-83` 同位置）；删掉原 `TODO(port)`、
   把位置理由 / 物理侧证据 / 清粒子依据写进就地注释；新增 `:12` import。

全部文件 `mcp__idea__get_file_problems(errorsOnly=true)` = **0 error**；
**未运行任何 gradle 命令**。

---

## 六、本轮之后的待实机（runClient）验证清单

1. **renew 型枪口火光/枪烟**：开火应看到火光（additive）与枪烟（`particles_blend`）；
   `latest.log` 应出现一次 `Loaded 2 particle definitions`，且**不再**出现
   `Muzzle particle definition ... not found, skipped`。（对应 §一 P1-A）
2. **GUI / 背包 / 快捷栏图标**：16×16 且**上下朝向正确**；若实机发现本来就该倒置，
   按 §一 P1-B 的回滚说明改回单行 `scale(16,16,16)`。（对应 §一 P1-B）
3. **第一人称亮度**：进暗处/亮处，枪体亮度应随环境光照变化**且稳定**（不随画面里有无生物跳变）；
   Lead 的补丁同时把 `packedLight` 从写死的 `FULL_BRIGHT` 改成按脚部方块位置取
   （依据 `ItemRenderer.java:287` 在被 cancel 的那段里）—— 验收时要**同时**看「暗处是否变暗」与
   「走动时是否稳定」。（对应 §二 P1-1）
4. **F3+T / `/tacz reload` 后**：第一人称枪械应立即按新资源重建（无旧模型残留一帧）、
   开火粒子不爆量/不瞬移。（对应 §一 P2-B）
5. **多贴图粒子的枪包**（若后续引入第二种 `energy_swirl`/emissive 贴图）：各贴图粒子应各用各的贴图。
   （对应 §一 P2-A）

---
---

# r3 渲染收尾（task-12）

> **作用域**：`com/github/.../v1/client/handler/FirstPersonRenderHandler.java`、
> `client/renderer/scope/**`、`client/model/**`、`client/animation/statemachine/GunAnimationStateContext.java`。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`；
> **1.7.10 准绳**：`build/rfg/minecraft-src/java/**`。
> **自查**：本轮 5 个改动文件 `get_file_problems(errorsOnly=true)` 全部 **0 error**；**未运行任何 gradle 命令**。
> 本节的项都来自 r2 汇总「已登记但未修」清单。

## 结论速览（r3）

**5 项全部落地：1 个 P1（第一人称枪不吃受伤晃动）+ 4 个 P2。P1 已修并复刻了 mixin 的同等语义
（**没有新增任何 mixin**）；3 个 P2 是 GL 状态/矩阵的实体修复；1 个 P2 是「登记消费端后果」。

其中 **r3-4 有一条对 r2 结论的修正**（重要）：r2 说「1.7.10 主 FBO 创建时就没有 stencil 附件」——
更准确的说法是「**默认**没有，因为 `ForgeHooksClient.stencilBits` 的默认值是 0；
若用户以 `-Dforge.forceDisplayStencil=true` 启动且显卡支持，`stencilBits = 8`，
FBO 会挂 `DEPTH24_STENCIL8` 从而**部分恢复**该机制」。详见 r3-4。

---

## r3-1 【P1 已修】第一人称枪体不吃 `hurtCameraEffect`（世界在晃、枪不晃）

**文件**：`com/github/mcmodderanchor/simplebedrockmodel/v1/client/handler/FirstPersonRenderHandler.java`
—— 新增调用 `:563`，新增方法 `compensateHurtCameraEffect` `:620-720`（含注释）。

### 现象
被子弹打中时**世界相机在晃，第一人称枪体纹丝不动**。

### 1.7.10 依据（逐条 file:line）
1. **原版手部 pass 自己会调一次**：
   `build/rfg/minecraft-src/java/net/minecraft/client/renderer/EntityRenderer.java`
   ```
   :778-779  glMatrixMode(GL_MODELVIEW); glLoadIdentity();
   :786      glPushMatrix();
   :787      this.hurtCameraEffect(partialTicks);      ← 手部 pass 的受伤摇晃（本项根因）
   :789-792  if (viewBobbing) setupViewBobbing(partialTicks);
   :796      enableLightmap(partialTicks);
   :797      itemRenderer.renderItemInFirstPerson(partialTicks);
   :801      glPopMatrix();
   ```
2. 本 handler 在 `RenderHandEvent` 里 `event.setCanceled(true)` ⇒
   `EntityRenderer.java:1434-1437` 的
   `if (!ForgeHooksClient.renderFirstPersonHand(...)) { glClear; renderHand(...); }` **整个 renderHand 被跳过**
   ⇒ `:787` 不再执行 ⇒ 枪体不晃。
3. **连带失效的两个 mixin 注入**（目标方法都是 `hurtCameraEffect`）：
   - `mixin/client/GameRendererMixin.java:266-281` `@Inject(hurtCameraEffect, HEAD, cancellable)`
     → `GunHurtBobTweak.onHurtBobTweak` + `RenderItemInHandBobEvent.BobHurt`；
   - 同文件 `:324-342` `@ModifyArg`（第 3 个 `glRotatef` = 受伤倾斜角）
     → `RenderConfig.GUN_HURT_BOB_MULTIPLIER × 方向倍率`。
   世界 pass 的调用在 `setupCameraTransform` 里照常执行（`EntityRenderer.java:673` 附近
   `getFOVModifier(..., true)` 那一路），所以症状才是「**世界晃、枪不晃**」而不是「都不晃」。
4. `hurtCameraEffect` 本体：`EntityRenderer.java:507-528`（死亡翻滚 `:516`；受伤三段旋转 `:524-526`）。

### 修法（不新增 mixin，复刻 GameRendererMixin 同款三步）
在 `onRenderHand` 中「已确定要接管并 cancel 的枪械路径」上调用一次
`compensateHurtCameraEffect(player, event.partialTicks)`（`:563`，位于 `canRenderInHand` 判定之后、
`updateParticleEmitterTransforms` / `compensateBeforeRenderHandEvent` 之前）：

| 步 | 动作 | 与 mixin 的对应 |
|---|---|---|
| 1 | `GunHurtBobTweak.onHurtBobTweak(player, new PoseStack(), partialTicks)`；命中则 `applyToModelView()` 并**结束** | `GameRendererMixin.java:269-277`（`health > 0` 守卫一致） |
| 2 | 未命中 → 派发 `RenderItemInHandBobEvent.BobHurt`；被 cancel 则**结束**（不再叠原版） | 同文件 `:278-289`；选「手部」事件的依据：`tacz$postBobHurt` 按 `useFovSetting` 分流，而 1.7.10 的 `renderHand` 用 `getFOVModifier(partialTicks, false)`（`EntityRenderer.java:770`）⇒ `useFovSetting == false` ⇒ 手部事件 |
| 3 | 复刻 `hurtCameraEffect` 本体（含死亡翻滚），并把**受伤倾斜角**按 `GUN_HURT_BOB_MULTIPLIER × 方向倍率` 缩放 | 同文件 `:324-342` 的 `@ModifyArg`（`frontBackWeight` / `directionMultiplier` 公式逐行同构） |

**明确**不做**的事**：不复刻 `:789-792` 的 `setupViewBobbing` —— r2 已裁决第一人称 viewBobbing 由枪械动画接管
（`FirstPersonRenderEvent` 主动 cancel），加回来会两套晃动叠加。

**为什么只在枪械路径上补偿**：非枪械路径本方法会提前 return 且**不** cancel，
原版 `renderHand` 自己仍会执行 `:787`，此时再补偿就重复了。

### 风险 / 未验证
- `GunHurtBobTweak` 的 500 ms 窗口、方向倍率公式均与 mixin 一致，但**幅度观感需实机确认**。
- 死亡翻滚一并复刻了（原版手部 pass 也有），若实机觉得死亡时枪晃得过份，可单独去掉那一分支。

### 实机验证方法
1. 进世界，被僵尸/子弹打中：**世界与枪体应同向、同幅晃动**；
2. 被**枪械**打中（`GunHurtBobTweak` 路径）：应是**微幅**（`GUN_HURT_BOB_MULTIPLIER` 默认 0.1）而非原版大幅；
3. 走路时**不应**出现原版 viewBobbing 叠加（枪械动画接管，r2 已定）；
4. 看向墙壁被打中：枪体不应穿进墙（摇晃是旋转，不改变枪的位置基座）。

---

## r3-2 【P2 已修】`ScopeLensRenderer.RenderState` 遗弃 texture unit 1/2 绑定

**文件**：`client/renderer/scope/ScopeLensRenderer.java`（`RenderState` `:828-905`；`drawLens` 绑定见 `:467-478`）。

### 现象
镜片 pass 之后，同一帧后续「带 lightmap 的绘制」颜色发花/变亮变暗，且 unit 0 的贴图明明是对的
（所以很难定位）。

### 依据
1. `drawLens`（`:467-478`）依次把 **unit 0/1/2** 绑成镜片纹理（`copyTexture` / 热成像纹理 / `copyTexture`），
   最后只把活动单元切回 0（`:475`）。
2. 修复前 `RenderState`（旧 `:805-875`）只快照 **unit 0** 的 `GL_TEXTURE_BINDING_2D`，
   且 `close()` 把活动单元硬留在 `TEXTURE0`。
3. **1.7.10 的 unit 1 就是 lightmap 单元**（`OpenGlHelper.lightmapTexUnit`；
   `EntityRenderer#enableLightmap` 在 unit 1 上 `bindTexture(locationLightMap)` +
   `glEnable(GL_TEXTURE_2D)`，见 `EntityRenderer.java:829-848`）。unit 1 被换成镜片纹理后，
   凡「unit 1 开着 + 逐顶点 lightmap 坐标」的绘制都会采错图
   （第一人称枪体 — 本轮 r3-1 所在路径、粒子、实体）。
4. `ScopeThermalRenderer#ensureTarget`（`ScopeThermalRenderer.java:685-700`）也会在**当前活动单元**上
   `glBindTexture` 自己的 FBO 颜色纹理，同样需要还原。

### 修法
`RenderState` 增加 `texture1` / `texture2` / `activeTexture` 三个字段；构造函数按 unit 0/1/2 逐个
`glGetInteger(GL_TEXTURE_BINDING_2D)`；`close()` 先还原三张绑定，再 `glActiveTexture(activeTexture)`
还原活动单元（不再硬留在 TEXTURE0）。其余状态原样保留。
`ScopeThermalRenderer` 里两处 `new ScopeLensRenderer.RenderState()`（`:314` entityCapture、`:438`）自动受益。

### 实机验证方法
开镜（含热成像镜）→ 关镜 → 立刻看第一人称枪体/粒子的亮度与颜色是否正常；
对比「开镜前」与「关镜后」同一光照位置的枪体亮度应一致。
另一条更硬的判据：把 `scope_thermal` 与普通组合镜都试一遍（thermal 会额外跑 `ScopeThermalRenderer` 的 FBO 路径）。

---

## r3-3 【P2 已修】`ScopeThermalRenderer` 用 `glLoadMatrix` 覆盖投影/视图后不还原

**文件**：`client/renderer/scope/ScopeThermalRenderer.java`（`:445` 新增 `matricesPushed`；
`:462-473` push+load；`:500-509` finally 里成对 pop）。

### 现象
当前**没有可见症状** —— 因为调用顺序恰好让下一个使用者（镜片 pass）自己会再 `push + loadIdentity`。
属**顺序侥幸**：一旦有人在这两步之间插一次带矩阵的绘制、或镜片 pass 提前 return，
后续绘制就会用到残留的「录制场景矩阵」。

### 依据
`ScopeThermalRenderer` 旧 `:456-461` 用 `glLoadMatrix` **替换** `GL_PROJECTION` / `GL_MODELVIEW`；
而 `ScopeLensRenderer.RenderState` 只快照视口/开关类状态、**不碰矩阵栈**（本轮 r3-2 也只加了纹理单元），
所以覆盖后没有任何还原点。

### 修法
照抄同包已正确成对的写法（`ScopeLensRenderer.java:370-375` 的 push/load + `:399-404` 的 pop）：
- push 两张矩阵（PROJECTION + MODELVIEW）→ 再 load，保证异常/提前 return 也能成对还原；
- `matricesPushed` 标志 + 既有 `finally` 里成对 `glPopMatrix`（先 PROJECTION 后 MODELVIEW），
  与该文件其它 `finally` 清理（`attachSceneDepth` / builder release）同域，任何路径都能执行。

### 实机验证方法
热成像镜开关一轮后，世界/手部是否出现「相机矩阵残留」类症状（画面整体倾斜/透视突变/枪体贴脸）；
若出现，说明还有别处泄漏矩阵 —— 本次修复应能排除这一处。

---

## r3-4 【P2 已登记 · 只加注释】组合镜 stencil 遮挡在 1.7.10 默认恒失效

**文件**：`client/model/BedrockGunModel.java`（`:442` 起，长注释，含完整依据链）；
`client/model/BedrockAttachmentModel.java`（`:481` / `:517` / `:539`，三处短注释指向长注释）。

### 现象（消费端后果 —— r2 只记了「API 缺失」，没记用户看得见什么）
开组合镜后，**镜内会看到枪体**（上游 1.20 是镜内干净、只有镜片图像）；
同时 `BedrockAttachmentModel` 里目镜之间 / 划分的 `GL_EQUAL` 互相遮挡也一并失效。

### 依据（本轮把 r2 的结论**修正得更精确**）
1. `Framebuffer.createFramebuffer` 挂不挂 stencil 附件，取决于
   `MinecraftForgeClient.getStencilBits()`（= `ForgeHooksClient.stencilBits`）：
   `build/rfg/minecraft-src/java/net/minecraft/client/shader/Framebuffer.java:117-130`
   - `getStencilBits() == 0` → 只挂 `GL_DEPTH_COMPONENT` 的 depth renderbuffer（**无 stencil 附件**）；
   - `!= 0` → `DEPTH24_STENCIL8` 同时挂 `GL_DEPTH_ATTACHMENT` + `GL_STENCIL_ATTACHMENT`。
2. 该值**默认是 0**：`ForgeHooksClient.java:317-341` —— 只有 JVM 参数
   `-Dforge.forceDisplayStencil=true`（`Boolean.parseBoolean(System.getProperty(...))`）且
   `Display.create(format.withStencilBits(8))` 成功，才置 `stencilBits = 8`；否则 `stencilBits = 0`。
3. 按 GL 规范：帧缓冲**没有** stencil 附件时，**模板测试恒通过、模板写入被丢弃** ⇒
   `RenderSystem.stencilFunc(GL_GREATER, 127, 0xFF)`（「只在 stencil > 127 处画枪体」=「镜内不画枪体」）
   对每个片元都通过 ⇒ 枪体照画。
4. `GL11.glClear(GL_STENCIL_BUFFER_BIT)` 在无 stencil 附件的 FBO 上是无害 no-op。

### 对 r2 结论的**修正**
- r2 写「1.7.10 的 main framebuffer **创建时就没有** stencil 附件」——不够准确。
  正确表述：**默认没有**（`stencilBits == 0`）；**有 JVM 参数时可变成 `DEPTH24_STENCIL8`**。
  届时本机制会**部分恢复**，那时才需要重新验 `GL_GREATER/127` 的阈值与
  `BedrockAttachmentModel#renderOcularStencil` 写入的 ref（`GL_REPLACE` + `i+1`）是否匹配。
- `util/RenderHelper.java:42-51` 只记了「1.7.10 没有 `enableStencil()` 可补挂」这一 **API 层**缺失；
  本轮补的是**消费端后果**（镜内看到枪体、目镜遮挡失效）。

### 为什么本轮不改
- **不自建 FBO**（超出本轮范围，且 Lead 已明确要求不要）：自建带 stencil 的 FBO 会与
  Angelica/光影的 framebuffer 管理冲突（参见 `ScopeThermalRenderer` 对 `GL_FRAMEBUFFER_BINDING`
  的「不得硬编码回 0」处理），风险远高于收益。
- **下一轮建议**（只记录，不在本轮做）：
  1. 让玩家以 `-Dforge.forceDisplayStencil=true` 启动即可**零代码**部分恢复；
  2. 若要无条件生效，需要在 `client/renderer/scope/**` 自建带 `DEPTH24_STENCIL8` 的镜片 FBO，
     并把镜片几何写进同一 stencil 值域，且必须实机验 Angelica 兼容性。

### 实机验证方法
开组合镜看镜内是否仍有枪体（**按预期会有**，属已知失效，不是回归）；
若给 JVM 加 `-Dforge.forceDisplayStencil=true` 后再看，镜内枪体应消失（且目镜遮挡恢复）——这同时验证了本条结论。

---

## r3-5 【P2 已修】`GunAnimationStateContext#setCurrentGunItem` 切枪时不清旧 `display`/`gunData`/`nbtUtil`

**文件**：`client/animation/statemachine/GunAnimationStateContext.java:587-652`
（`display = null; gunData = null;` 在 `:642-643`，`nbtUtil = null;` 在 `:650`）。

### 现象
- 切到空手后 **`shouldSlide()` 仍可能是 `true`**（蹲下时上一把枪的「可斜握」还在）；
- 切到空手后动画仍可能回调 `popShellFrom(index)`，于是**用上一把枪的 `display` 抛壳**
  （弹壳凭空飞出）；
- Lua 脚本 `getNbtAccessor()` 在切到无 NBT 的物品后会读到**上一把枪的 NBT**。

### 依据
1. 修复前 `setCurrentGunItem`（旧 `:590-604`）只在 `iGun != null` 分支刷新 `display`/`gunData`，
   `else` 分支**什么都不做** ⇒ 字段保留旧值。
2. `processGunData`（`:68-77`）已经有 `iGun != null && display != null && gunData != null` 守卫，
   所以「经 processGunData 的 getter」是安全的；**但直接读字段的 getter 挡不住「非 null 的陈旧值」**：
   - `isOverHeat` `:118-125`、`getHeatProgress` `:127-136`、`shouldSlide` `:439-442` → 读 `gunData`；
   - `popShellFrom` `:473-496` → 读 `display`；
   - `getStateMachineParams` `:506-510` → 读 `display`；
   - `getNbtAccessor` `:519-521` → 读 `nbtUtil`。
3. 上游 1.20 同构（也不清），差异在于**上游 `display`/index 由资源重载保证恒非 null**、
   且上游 `currentGunItem` 与索引同源 ⇒ 不存在本移植的「陈旧窗口」。故这里必须补清空。

### 修法
`iGun == null` 分支显式 `display = null; gunData = null;`；`nbtUtil` 的 `else` 分支显式 `nbtUtil = null;`。

### 直接读这三个字段的全部 getter 复核清单（判空已齐备，本轮只补「陈旧非 null」这一层）

| 方法 | 读的字段 | 现有守卫 | 结论 |
|---|---|---|---|
| `processGunData`（唯一聚合入口） | iGun / display / gunData | `iGun != null && display != null && gunData != null`（`:73`） | ✓ |
| `hasBulletInBarrel` / `getAmmoCount` / `getMaxAmmoCount` / `getMagExtentLevel` / `getFireMode` / `getMaxCharge` / `getChargeThreshold` | 经 lambda 读 gunData / iGun | 全部经 `processGunData` | ✓ |
| `isOverHeat` | gunData, iGun | `gunData == null \|\| iGun == null` 早退（`:121`） | ✓ |
| `getHeatProgress` | gunData, iGun | 同上（`:128`） | ✓ |
| `getShootInterval` | iGun, gunData | `iGun == null \|\| gunData == null` 早退（`:145`） | ✓ |
| `hasAmmoToConsume` | iGun | `iGun == null` 早退（`:237`） | ✓ |
| `getAttachment` | iGun | `iGun == null` ⇒ 返回 `tacz:empty`（`:531`） | ✓ |
| `shouldSlide` | gunData | `gunData != null && …`（`:441`） | ✓ |
| `popShellFrom` | display | `display != null && display.getShellEjection() != null`（`:476`） | ✓ |
| `getStateMachineParams` | display | `display == null ? null : …`（`:508`） | ✓ |
| `getNbtAccessor` | nbtUtil | **故意**允许 null（Lua 侧读到 nil，`:520`） | ✓ |

⇒ 判空齐备；叠加本轮的「陈旧值清空」后，空手/非枪时所有枪械语义都退化为安全默认值
（`shouldSlide=false`、`popShellFrom` 空操作、`getNbtAccessor=null`）。

### 实机验证方法
1. 持枪蹲下 → 切到空手 → 蹲下：**枪不应斜握**（空手本来也没枪，重点看切回另一把枪的第一帧不闪旧姿态）；
2. 打完一枪立刻切空手并播放收枪动画：**不应有弹壳凭空飞出**；
3. Lua 状态机里 `context:getNbtAccessor()` 在空手时应为 nil（可临时打印）。

---

## r3 汇总

| # | 级别 | 状态 | 文件 | 一句话 |
|---|---|---|---|---|
| r3-1 | **P1** | ✅ 已修 | `FirstPersonRenderHandler.java:563, 620-720` | 复刻 `hurtCameraEffect`（三步同 GameRendererMixin），不新增 mixin；**未**加回 viewBobbing |
| r3-2 | P2 | ✅ 已修 | `ScopeLensRenderer.java:828-905` | `RenderState` 补 unit 0/1/2 绑定 + `GL_ACTIVE_TEXTURE` 快照/还原 |
| r3-3 | P2 | ✅ 已修 | `ScopeThermalRenderer.java:445, 462-473, 500-509` | `glLoadMatrix` 改 push/pop（finally 成对还原） |
| r3-4 | P2 | ✅ 已登记 | `BedrockGunModel.java:442+`、`BedrockAttachmentModel.java:481/517/539` | stencil 恒失效的**消费端后果**入注释；**修正 r2 表述**（默认无、可被 JVM 参数恢复）；不自建 FBO |
| r3-5 | P2 | ✅ 已修 | `GunAnimationStateContext.java:587-652` | `iGun == null` 清 `display`/`gunData`；无 NBT 清 `nbtUtil`；附 11 项 getter 判空复核表 |

**未修 / 转下一轮**：仅 r3-4 的「自建带 stencil 的镜片 FBO」与「无条件恢复镜内遮挡」——
已记为**下一轮建议**，本轮按 Lead 指示不动。

复查方式：5 个文件 `mcp__idea__get_file_problems(errorsOnly=true)` 全部 **0 error**；
**未运行任何 gradle 命令**（含 `compileJava` / `runClient`），上述「实机验证方法」均待 runClient 执行。


