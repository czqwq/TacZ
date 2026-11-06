# r2-06 · 移植完整性审查（骨架 / 半骨架）

> **作用域**：`src/main/java/com/tacz/**` 全仓**只读**（821 文件 / 108,979 行），唯一写入本文件。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（799 文件），凡「这里该有逻辑」的判定一律以上游代码为准。
> **方法**：只读脚本全量扫描（脚本与中间产物都在 `build/completeness/`，未进 `src` / `docs`），
> 4 类扫描 + 人工复核：
> ① TODO 族关键词全量；② 结构化空实现（空方法体 / 假返回 / 静默 catch / 恒真恒假 / 空循环）；
> ③ 与上游**同名方法体长度**逐方法对比（找「半骨架」）；④ 上游有、移植**缺失**的方法 + 死代码（`refs<=1` 的 public 方法，找「没人调的功能入口」）。
> **未跑 gradle**（按铁律）；结论全部靠读代码 + 上游对照。

## 扫描覆盖统计

| 扫描项 | 命中 | 处置 |
|---|---|---|
| `.java` 文件 / 行数 | 821 / 108,979 | 全部进扫描；与上游 799 文件按**相对路径一一配对** |
| TODO 族（`TODO\|FIXME\|未实现\|待移植\|占位\|stub\|XXX:\|HACK`） | **263 行命中**（其中 7 行是正则误命中：`toDouble` / `convertToDouble` 里的 "todo"）→ **256 条真标记** | 全部 263 行逐条读过并归类：**(a) 真骨架 4 / (b) 已裁决降级 240 / (c) 无害或上游原样 12 / 误命中 7**，见第五节矩阵 |
| 结构化方法体归类 | 1,520 条方法体归行 | 去掉 1,096 条普通 getter 型 `return x;` → **424 条非平凡候选**（空体 336 / `return false` 31 / `return true` 25 / `return 0` 15 / `return null` 13 / 空集合 4） |
| 424 条与上游同名方法交叉比对 | 上游有实现 **88** / 上游也空 115 / 上游无同名方法 110 / 上游无此文件 84 / 上游平凡 27 | 88 条**逐条读过**：绝大多数是**构造器**（1.20 `super(TYPE,pos,state)` 无对应）、**接口空重载**（上游同一个 5 参 `render` 也是空）、或**已登记降级**（ARCompat / PlayerAnimatorCompat / KubeJS / scope 模板测试）。只有 1 条落在「非 compat、非文档化」区间 → 见第二节 |
| 方法体长度差（port ≤ 上游 55% 且上游 ≥120 字符） | **68 条** | 57 条自带「1.7.10 依据」注释；11 条无注解 → **逐条打开读过**：9 条是等价改写 / 构造器 / 上游把逻辑拆到 helper，**2 条确认问题**（`InteractKeyTextOverlay` 丢 `occupied` 分支 → 第二节；`BedrockGunModel` 的 stencil 门控恒失效 → 第四节） |
| 上游有、移植缺失的方法（同名文件内） | **305 条** | 41 条（最可疑的文件）逐条核对 1.7.10 落点 = **全部有等价实现或属 1.20 专有 override**；其余 264 条按命名规则归类（第六节说明未逐条读） |
| 空 `catch` | 11 条（含同行） | **11/11 都有注释说明为什么吞**，无骨架 |
| `if (true)` / `if (false)` / `\|\| true` / `&& false` | **0** | — |
| `throw new UnsupportedOperationException` / `RuntimeException("TODO")` | 4 条 | 3 条是 `api/event/common/*Event` 的 client 侧 `getDamageSource()`（**上游原样**）；1 条是 `mcshim/DummyPose` 注释 |
| 死代码：public 方法全仓引用数 ≤1（排除 `api/**`，外部模组会调） | 343 条 | 重点核对 24 条「功能入口」（register/init/load/inject/apply 命名）→ 发现 **3 条真骨架**（第一/二/三节） |

**与任务书的数字对账**：任务书给的是「约 282 处」。本报告用窄口径关键词集得 **263 行**（7 行误命中 → 256 条真标记）；
若把 `降级/简化/暂不/省去/空实现` 也计入（这些多是**说明性文字**而非待办标记），命中会到 512 行。
**处置原则：263 行全部有归属（逐一读过），512 宽口径中的「降级/简化」类文字按第三节处理，不重复计数。**

---

## 结论速览

**真骨架（功能实际缺失）5 条 / 半骨架 2 条 / 已裁决降级 240 条（TODO 族）**
（另有 424 条结构化空实现候选，其中「上游有实现在此为空」的 88 条已逐条复核 → **0 条属于真骨架、1 条属于半骨架**，其余全是构造器 / 接口空重载 / 已登记的平台降级）

最关键的 3 条结论：

1. **P0 —— 枪械工作台配方从来没被加载过（不是「部分缺失」，是 0 条）。**
   枪包里的 `data/<ns>/recipes/**`（默认包 **199 个 json**：枪 70 / 配件 104 / 弹药 25）**没有任何代码去读**：
   `resource/GunPackLoader#readPackData(String)`（`GunPackLoader.java:206`）与
   `crafting/GunSmithTableSerializer#deserializeAndRegister(...)`（`GunSmithTableSerializer.java:65`）
   **全仓 0 调用者**，`GunSmithTableRecipe.REGISTRY`（`GunSmithTableRecipe.java:55`）永远为空。
   `init/ModRecipe.java:294` 的 TODO 自己承认「等 resource 层接线」——但类注释
   （`GunSmithTableRecipe.java:40-42`）却说「由 init/ModRecipe 登记进来」，两处说法互相矛盾，而代码是空的那一边。
   后果：工作台界面能打开，但左侧配方列表恒空，**无法合成任何枪械 / 配件 / 弹药**。

2. **P1 —— `RefitTransform` 的插值 tick 从未注册到事件总线。**
   `client/animation/screen/RefitTransform.java:93` 是 `@SubscribeEvent public static void tickInterpolation(RenderTickEvent)`，
   但全仓**没有一处** `register(...RefitTransform...)`，而类注释 `:20-22` 明确写「注册改为在 `ClientProxy.preInit` 里
   `MinecraftForge.EVENT_BUS.register(RefitTransform.class)`」——这行不存在。
   后果：改装界面的「开合动画 / 配件类别切换动画」永远不推进；`refitScreenTransformProgress` 被
   `changeRefitScreenView`（`:88`）置 0 后**再也不回 1**，导致 `:83` 的守卫恒假 ⇒ **改装界面里配件类别按钮只有第一次点有效**。

3. **P2 ×3 —— 三处「入口在、接线不在」，且三处的 TODO 理由都已过期（会误导后续接手人）：**
   (a) 战利品注入 `loot/LootTableInjectorModifier#inject` 0 调用者（默认枪包的「奖励箱送 Taurus 943」静默失效）；
   (b) `/tacz convert` 的 `ConvertCommand.setConverter` 0 调用者（而 TODO 说「PackConvertor 仍是 1.20 签名」——**已经改完了**）；
   (c) `/tacz debug pegboard` 的 `placeBoards` 恒返回 0（而 TODO 说「挂板尚未移植」——**`block/GunPegboardBlock` / `GunPegboardBlockEntity` 都已完成**）。

---

## 一、真骨架清单

> 「真骨架」定义：上游有完整实现、本移植**调用链断裂**或**逻辑为空**，且功能对玩家/集成方实际不可用。
> 每行都给出「上游 file:line」原件对照与「建议补法」。

| 严重度 | 文件:行 | 上游 file:line | 缺的具体逻辑 | 玩家可见后果 | 建议补法 |
|---|---|---|---|---|---|
| **P0** | `crafting/GunSmithTableSerializer.java:65`（`deserializeAndRegister`）+ `resource/GunPackLoader.java:206`（`readPackData`）+ `init/ModRecipe.java:294`（TODO）+ `crafting/GunSmithTableRecipe.java:55`（`REGISTRY` 无填充者） | `init/ModRecipe.java:18-24`（`RECIPE_SERIALIZERS` 注册 `gun_smith_table_crafting` → 原版 `RecipeManager` 解析 `data/<ns>/recipes/**`）；`crafting/GunSmithTableSerializer.java:22-71`（`RecipeSerializer#fromJson`） | 枪包 `recipes/` 目录**从不被扫描**：`readPackData("recipes")` 与 `deserializeAndRegister(...)` 全仓 0 调用者，没有任何 manager（`GunDataManager`/`AttachmentDataManager`/`GunIndexManager`/`RecipeFilterManager`/`LootInjectionManager` 都不管 recipes）读它。`GunSmithTableRecipe.register()` 的唯一调用点就在这个没人调的 `deserializeAndRegister` 里。旁证：`GunPackLoader.java:202` 的 javadoc 明说 `readPackData` 就是「供 1.7.10 无 datapack 的数据（`recipes`、`advancements`、`loot_tables`…）」用——设计意图清楚，接线没做；`resource/network/DataType.java:12` 的 `RECIPES` 缓存类型同样无生产者（多人服侧也没同步） | 工作台（`client/gui/GunSmithTableScreen.java:183` 遍历 `GunSmithTableRecipe.getAll()`）**配方列表恒空**：看不到也做不出任何枪械/配件/弹药。默认枪包 199 条配方（枪 70 / 配件 104 / 弹药 25）全部无效；连模组自带的 `data/tacz/recipes/misc/blood_strike_1.json`（把枪）也没注册 | ① 在 `resource/` 加 `GunSmithTableRecipeManager`（仿 `LazyJsonDataManager`）或直接在 `CommonAssetsManager#reload()` 尾部：`GunPackLoader.readPackData("recipes")` → 对每个 `(id, json)` 调 `GunSmithTableSerializer.deserializeAndRegister(id, json)`；② **重载前必须调 `GunSmithTableRecipe.clear()`**（该公开方法当前也 0 调用者，`:126`），否则重载后旧配方残留；③ 之后 `CommonAssetsManager#initLazyRecipes()` 才能对它们跑 `init()`；④ 多人服还要把配方挂进 `DataType.RECIPES` 的网络缓存，否则客户端列表仍空 |
| **P1** | `client/animation/screen/RefitTransform.java:93`（`tickInterpolation`） | `client/animation/screen/RefitTransform.java:16`（`@Mod.EventBusSubscriber(value = DIST.CLIENT, modid = MOD_ID)` 自动注册）+ `:93-123`（同一段插值 tick 正文） | 类注释承诺的 `MinecraftForge.EVENT_BUS.register(RefitTransform.class)`（`ClientProxy.preInit`）不存在；`ClientProxy` 的 3 个 handler 清单（`FML_BUS_CLASSES`/`BOTH_BUS_CLASSES`/`FORGE_BUS_CLASSES`，`client/ClientProxy.java:82/105/122`）里也没有它 | ① `refitScreenOpeningProgress` 恒 0 ⇒ 开镜/开界面动画不跑，`GunItemRendererWrapper.java:273` 的「打开改装界面隐藏手臂」永不触发；② `refitScreenTransformProgress` 被置 0 后不再回升（`:83`）⇒ **改装界面里切换配件类别只有第一次生效**，之后点类别按钮无反应、模型也不再切换（`FirstPersonRenderGunEvent.java:300-312` 的插值恒取旧类别） | `ClientProxy#preInit` 增加 `MinecraftForge.EVENT_BUS.register(RefitTransform.class)`（1.7.10 cpw EventBus 支持 `public static` + `@SubscribeEvent`；`RenderTickEvent` 走 FML 总线，故实际应 `FMLCommonHandler.instance().bus().register(RefitTransform.class)`——按 `EventHandlers` 的分线规则） |
| **P2** | `loot/LootTableInjectorModifier.java:77`（`inject`）+ `init/ModLootModifiers.java:18`（空 `register`） | `init/ModLootModifiers.java`（`LootModifierSerializer` 注册）+ `data/tacz/loot_modifiers/loot_table_injector.json` + `data/forge/loot_modifiers/global_loot_modifiers.json`；默认包 `data/tacz/tacz_loot_injectors/spawn_bonus_chest_taurus943.json` | `inject(ResourceLocation, List<ItemStack>)` **0 调用者**；类注释 `:56-58` 自称「由 `event/**` 决定哪个 1.7.10 事件对应哪个表 id 后调用本类」，但 `event/**` 里没有任何调用点（`EventHandlers.registerAll()` 也没有）。数据侧的 `LootInjectionManager` 照常解析并保存了配置（`CommonAssetsManager.java:190`），但**配置读完就没人用** | 奖励箱（spawn bonus chest）里不会出现默认枪包配置的 Taurus 943；任何枪包自定义战利品注入全部静默失效（无日志、无报错） | 在 `event/` 新增薄事件类（如 `LootInjectionEvent`），挂 `LivingDropsEvent` / `ChestGenHooks` 对应场景，把「表 id + 产出列表」传给 `inject(...)`；或在 `ChestGenHooks` 初始化处按 1.7.10 分类名映射到枪包配置的表 id |
| **P2** | `command/sub/ConvertCommand.java:63`（`setConverter`，0 调用者）+ `client/ClientProxy.java:241-242`（TODO） | `command/sub/ConvertCommand.java`（`DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PackConvertor.convert(source))`） | 客户端转换器注入缺失。**TODO 的理由已过期**：`:59` 说「`PackConvertor` 仍是 1.20.1 签名」，但 `resource/PackConvertor.java:62` 已经是 `public static void convert(ICommandSender source)`（401 行，完整移植） | `/tacz convert` 只打一行 warn（`ConvertCommand.java:79`「客户端枪包转换器尚未接线」），老格式枪包无法命令转换 | `ClientProxy#preInit`（或 `ClientSetupEvent`）加一行 `ConvertCommand.setConverter(PackConvertor::convert);`，并删掉两处过期 TODO |
| **P2** | `command/sub/PegboardStressCommand.java:220-234`（`placeBoards` 恒 `return 0`） | `command/sub/PegboardStressCommand.java:50-...`（`world.setBlock(pos, state, 3)` + `setItems(contents)` 真放置） | 真正的放置分支没写，只做位置校验 + warn。**TODO 的理由已过期**：`:222` 说「依赖 `init/ModBlocks`、`block/GunPegboardBlock`、`block/entity/GunPegboardBlockEntity` 的 1.7.10 移植」——这三个都已交付（`GunPegboardBlock.java` 全量实现含染色/连接/掉落，`GunPegboardBlockEntity` 完成） | `/tacz debug pegboard` 压力测试只输出「枪械挂板尚未移植」，永远放不下任何挂板（调试命令，玩家影响小） | 按该文件 `:209-216` 已写好的「1.7.10 对照代码」实装 `world.setBlock(x,y,z,ModBlocks.GUN_PEGBOARD,meta,3)` + `((GunPegboardBlockEntity)tile).setItems(contents)`，并把返回值改成真实 `placed` |

---

## 二、半骨架清单

> 「半骨架」= 方法在、调用链在，但**上游的某个分支/循环/回写被删掉了**，比空方法更隐蔽。

| 严重度 | 文件:行 | 上游 file:line | 缺的具体逻辑 | 玩家可见后果 | 建议补法 | 登记状态 |
|---|---|---|---|---|---|---|
| **P2** | `client/renderer/item/GunItemRendererWrapper.java:273`（`RefitTransform.getOpeningProgress() != 0` 分支） | `client/renderer/item/GunItemRendererWrapper.java:246-249` | 分支代码是照抄的，但**它依赖的 `getOpeningProgress()` 恒 0**（真骨架 #2）。等价于「打开改装界面时隐藏手臂」这条逻辑被静默删除 | 改装界面打开过程中手臂不消失（GUI 覆盖下多数场景看不出来，第一人称瞬间可见） | 修真骨架 #2 后自动恢复；无需改本行 | **无注释**（源码照抄上游，看不出失效） |
| **P2** | `client/gui/overlay/InteractKeyTextOverlay.java:115-127`（`renderDisplayCaseText`） | `client/gui/overlay/InteractKeyTextOverlay.java:82-95` | 上游条件 `occupied \|\| canPlaceHeld`（`:92`）在这里只剩 `canPlaceHeld`（`:123-124`）；上游的 `block.getValue(GunDisplayCaseBlock.LOCKED)` 守卫（`:83`）与 `GunDisplayCaseSlotHelper.getSlot(...)` 槽位命中（`:86`）都没了 | 对着**已有枪的展示格**、手上拿不可放置物品时没有「按 F 交互」提示；上锁的展示柜也照旧提示（但按了没反应） | `GunDisplayCaseSlotHelper.getSlot(...)` 与 `GunDisplayCaseBlock.isLocked(meta)` **都已经实现**，把三处判定补回即可 | **已登记**（`:120-122` 写明降级为「手上物品能放上去就提示」，但没写「occupied 分支丢失」的后果） |

> 复核过但**判定为等价实现、不进清单**的代表项（避免误报，供后续复用）：
> `block/GunPegboardSlotHelper#getSlot` 与 `block/GunDisplayCaseSlotHelper#getSlot`（与上游逐行同构，含第二面/角块列屏蔽）、
> `util/SlotAdapterHelper#getAvailableSlotAdapters`（与上游同逻辑，仅替 `stream` 为循环）、
> `network/message/event/ServerMessageGunXxx#handle`（8 个包共用的 `AbstractShooterGunEventMessage` 抽基类，非删除）、
> `statemachine`/`gltf` POJO 的 getter（Gson/Lua 反射用，静态引用数天然为 1）、
> `sound/SoundManager#sendSoundToNearby`（1.7.10 用 `worldServer.playerEntities` 按距离过滤 = 上游 `chunkMap.getPlayers` 的**超集**，不会漏发）。

---

## 三、已裁决降级清单（白名单）

> 240 条 TODO 族标记属此类：**代码或文档里写明了 1.7.10 平台依据**，本轮不重复报警。
> 逐行归属见 `第五节` 矩阵；完整逐行清单（file:line + 类别）在只读产物 `build/completeness/todo_classified.txt`。

| 文件:行（代表） | 降级内容 | 依据出处 |
|---|---|---|
| `advancement/TargetHitTrigger.java:27`、`GunRefitTrigger.java:27` | criterion trigger 注册空实现，改 Forge 事件广播 | 1.7.10 无 `CriteriaTriggers`；类注释 + `docs/PORT_STATUS.md`（CommonProxy 契约「trigger 注册（1.7.10 空实现）」） |
| `api/event/common/KubeJSGunEventPoster.java:17/24/31` | 三个 KubeJS 派发方法空 | 用户裁决「compat 反射式可选兼容，缺库静默」；上游 `ModList.get().isLoaded("kubejs")` |
| `compat/ar/ARCompat.java`（12 处空 / 2 处 `return false`） | AcceleratedRendering 全链空 | `docs/PORT_STATUS.md` §compat 保留清单（13 文件）；1.7.10 无该模组 |
| `compat/playeranimator/PlayerAnimatorCompat.java`（10 处） | PlayerAnimator 全链空 | 同上 |
| `compat/oculus/OculusCompat.java:221`（`getHandBufferSource` → `null`） | 手部缓冲源降级 | 1.7.10 无 Iris/Oculus；`docs/ANGELICA_COMPAT.md` |
| `client/renderer/scope/ScopeLensContour.java:57`（`prepare` → `null`）+ `:62`（`close` 空） | JFA 距离场需 `texelFetch`(GLSL130) + 主 FBO stencil ⇒ 走上游自己预留的椭圆近似分支 | `docs/PORT_STATUS.md:1316` 逐条写明（含降级 ①②③） |
| `util/RenderHelper.java:76`（stencil 只开关）+ `:47/69` TODO | 1.7.10 主 framebuffer 无 stencil 附件、无 `enableStencil()` API | 类 javadoc `:42-51`（含 OptiFine 分支不可移植理由） |
| `client/model/functional/BeamRenderer.java:109`（`return false`）、`BedrockGunModel.java:465`（回退非加速）、`BedrockAttachmentModel.java:558` | AR 加速路径回退 | 各方法 `TODO(port)` + 上游行号 |
| `client/renderer/item/ExternalGunItemVboRenderer.java:53` | 无 VBO 后端，改立即模式 | 类注释 `TODO(L2)`（GL15 需自建） |
| `entity/shooter/LivingEntityCrawl.java:48/62/67/92`、`client/gameplay/LocalPlayerCrawl.java:77/99/117` | 趴下姿态/鞘翅/三叉戟判定缺失（`Pose`/`setForcedPose` 无对应） | 两文件 `TODO(port)` 逐条带上游行号 |
| `event/KnockbackChange.java:31-32` | `register()` 空（无 `LivingKnockBackEvent`） | 类注释 + `EventHandlers.java:54` 注释 |
| `event/ammo/BellRing.java:34` | 敲钟无操作（1.7.10 无 `BellBlock`，1.14+） | 类注释 `TODO(port)` |
| `event/TravelToDimensionEvent.java:31` | 只处理玩家 | 类注释（上游对全部 `LivingEntity` 生效） |
| `entity/EntityKineticBullet.java:692`（`createDamageSources`） | 普通/穿甲同用 `causeThrownDamage`，仅末影人走魔法 | 方法 javadoc（上游 data-driven `damage_type` 标签 → 硬编码） |
| `init/ModParticles.java:16`、`init/ModLootModifiers.java:18`、`init/ModContainer.java:20`、`init/ModSounds.java:19`、`init/CapabilityRegistry.java:38` | 各类注册表在 1.7.10 无对应物（空实现 + 说明） | 每类的类 javadoc（`ModParticles`「无 ParticleType 注册表」等） |
| `init/CommonRegistry.java:94`（水炼药锅染色）、`:131`（属性挂载改 `EntityConstructing`） | 平台无对应 | 类/方法 javadoc（`CommonRegistry.java:88-95`、`:114-134`） |
| `item/AmmoBoxItem.java:235`（`overrideStackedOnOther`） | 1.20.5+ 物品栏点击钩子 | 方法内 `TODO(port L2/后续)` |
| `client/tooltip/**` 12 处 | tooltip 位图网格降级为纯文本 | 各方法 `TODO(L2)` + 上游行号（1.7.10 无 `renderImage`） |
| `client/particle/**` 17 处 | 粒子参数/相机/寿命 API 差异 | 各方法 `TODO(L2)` + 上游行号 |
| `client/resource/**` 40 处 | `ParticleArgument`/`ItemTransforms`/`Set.of`/`MissingTextureAtlasSprite` 等换名 | 各方法 `TODO(port)` + 上游行号 |
| `network/message/ServerMessageLevelUp.java:57` | 无 toast API，升级提示不弹 | 方法内 `TODO(port)`（上游展示代码本身也注释着） |
| `util/block/BlockRayTrace.java:54` | 不单独处理流体形状 | 类 javadoc `:51-55`（上游调用方全用 `ClipContext.Fluid.NONE`） |
| `block/AbstractGunSmithTableBlock.java:249`（放置撤销/退款兜底） | 上游 `getStateForPlacement` 返回 null 取消放置；1.7.10 改「ItemBlock 预检 + 兜底退款」 | 方法 javadoc `:244-252` |

---

## 四、无注释的静默简化（最危险的一类）

> AGENTS.md 要求「移植特有代码必须带移植特有理由 / 1.20 有而 1.7.10 没有的能力要记录差异」。
> 下面是**行为被简化、但代码里读不出「为什么」或「后果是什么」**的位置。

| 文件:行 | 说明 | 危险点 | 建议 |
|---|---|---|---|
| `client/model/BedrockGunModel.java:445-453`、`client/model/BedrockAttachmentModel.java:481/512/532` | 用「模板测试」把枪体挡在组合镜镜片区域外（`RenderSystem.stencilFunc(GL_GREATER, 127, 0xFF)` + `glClear(GL_STENCIL_BUFFER_BIT)`）。1.7.10 主 framebuffer **没有 stencil 附件**，按 GL 规范此时模板测试**恒通过** ⇒ 遮挡完全失效 | `util/RenderHelper.java:42-51` 只记了「API 缺失」，**没记消费端后果**；读 `BedrockGunModel` 的人会以为镜片遮挡是好的 | 在这 4 处各加一句「1.7.10 无模板缓冲 ⇒ 本机制恒失效，镜内会看到枪体」；若要真做，需自建带 `GL_DEPTH24_STENCIL8` 的 FBO（`ScopeThermalRenderer` 已有 FBO 经验可复用） |
| `command/sub/ConvertCommand.java:59-61` + `client/ClientProxy.java:241-242` | TODO 说「`PackConvertor` 仍是 1.20.1 签名，待改」 | **理由已过期**（`PackConvertor.java:62` 已是 1.7.10 签名）。后来者会去「移植 PackConvertor」（已完成），而不是补那一行 `setConverter` | 删过期 TODO，改成「接线缺失」并指向 `ConvertCommand.setConverter` |
| `command/sub/PegboardStressCommand.java:222-231` | TODO 说「挂板尚未移植，`GunPegboardBlockEntity` 仍是 1.20.1 形态」 | **理由已过期**（两个类都已完成）。日志还会在运行期打印「枪械挂板尚未移植」，误导排查方向 | 更新理由为「`placeBoards` 放置分支未写」，并按 `:209-216` 的对照代码实装 |
| `inventory/GuiHandler.java:37-38` | 类注释还说「本工作流只做 L1，`getClientGuiElement` 返回 null 并留 TODO」 | **注释已过期**：`:114-138` 已完整实现（返回 `GunSmithTableScreen`/`AmmoStorageScreen`）。r1 已修，注释没跟着改 | 删掉该段，避免下轮 review 重复报「GUI 打不开」 |
| `network/ClientSideHooks.java:56`（`syncBaseTimestamp`） | `Impl` 的空默认方法，`ClientProxy.Hooks` 里也覆写成空注释「由 updateBaseTimestamp 承担」 | 全仓 **0 调用者**（真路径走 `updateBaseTimestamp`，见 `ServerMessageSyncBaseTimestamp.java:46`）⇒ 死接口；外部/后续代码若调它，会静默什么都不做 | 删除该接口方法，或改成 `@Deprecated` + 说明「真正的入口是 updateBaseTimestamp」 |
| `resource/GunPackLoader.java:206`（`readPackData`） | 通用的「按子目录读全枪包 json」工具，**0 调用者** | 它是 P0 那条链路本该用的入口；空着不留痕，容易被当成「已接好」 | 见第一节 P0 补法（它就是补法要用的函数） |
| `crafting/GunSmithTableRecipe.java:126`（`clear()`） | 重载前清空静态表的入口，**0 调用者** | 一旦把配方接线（P0 补法），不调 `clear()` 会在 `/tacz reload` 后残留被删除枪包的配方 | 接线时同步加 `clear()` |
| `api/TimelessAPI.java:193/231`（`getRecipe` / `getAllRecipes`） | 恒返回空（`Optional.empty()` / `Collections.emptyMap()`） | **上游原样**（两个方法上游都 `@Deprecated`，注释说「请用 `RecipeManager`」）——但 1.7.10 里 `RecipeManager` 不存在，注释指向空气。外部模组（YSM 等）若调这两个 API 会永远拿空 | 保留签名（YSM 契约），但把注释改成「1.7.10 无 RecipeManager；请用 `GunSmithTableRecipe.getAll()`」，并考虑在接线 P0 后直接委托静态表 |
| `client/gui/GunPackProgressScreen.java`（5 个方法 0 调用者） | 枪包加载进度界面从未显示 | **上游同样 0 调用者**（已核对），属上游遗留死代码，非本移植引入 | 可保留；若想用它，需在 `ClientGunPackReloader` 里接管加载进度 |

---

## 五、TODO 族 263 行分级统计（按目录 × 类别计数矩阵）

类别：**(a)** 真骨架 / **(b)** 已裁决降级（有 1.7.10 依据）/ **(c)** 无害（上游原样注释、过期注释、文档性文字）/ **fp** 正则误命中

| 目录 | (a) | (b) | (c) | fp | 合计 |
|---|---:|---:|---:|---:|---:|
| `api/**`（`client`/`event`/`item`/`util`） | 0 | 6 | 6 | 0 | 12 |
| `block/**` + `block/entity` | 0 | 8 | 0 | 0 | 8 |
| `client/**`（`animation` 2 / `event` 14 / `gameplay` 3 / `gui` 14 / `init` 4 / `input` 1 / `model` 9 / `particle` 17 / `renderer` 21 / `resource` 40 / `sound` 10 / `tooltip` 12 / `ClientProxy` 1） | **1** | 147 | 1 | 0 | 149 |
| `command/**` | **2** | 2 | 1 | 0 | 5 |
| `compat/**`（`ar`1 / `cloth`4 / `controllable`3 / `jei`1 / `kubejs`1 / `oculus`1 / `playeranimator`10 / `shouldersurfing`1） | 0 | 22 | 0 | 0 | 22 |
| `entity/**`（含 `entity/shooter` 9） | 0 | 9 | 0 | 0 | 9 |
| `event/**`（含 `event/ammo`） | 0 | 18 | 0 | 0 | 18 |
| `init/**` | **1** | 1 | 0 | 0 | 2 |
| `inventory/**` | 0 | 0 | 1 | 0 | 1 |
| `item/**` | 0 | 14 | 1 | 0 | 15 |
| `mcshim/**` | 0 | 5 | 1 | 3 | 9 |
| `mixin/**` | 0 | 2 | 0 | 0 | 2 |
| `network/**` | 0 | 3 | 0 | 0 | 3 |
| `resource/**` | 0 | 0 | 0 | 4 | 4 |
| `util/**` | 0 | 3 | 1 | 0 | 4 |
| **合计** | **4** | **240** | **12** | **7** | **263** |

**(a) 的 4 行**（= 第一节真骨架的 TODO 侧入口）：
`init/ModRecipe.java:294`、`client/ClientProxy.java:241`、`command/sub/ConvertCommand.java:59`、`command/sub/PegboardStressCommand.java:222`

**(c) 的 12 行**（逐条核对过上游）：
`api/client/animation/interpolator/Spline.java:9/14/20`（上游同文件同位置也是 `// TODO`，整类 23 行完全一致）、
`api/client/animation/statemachine/AnimationConstant.java:8`（上游 `// todo` 原样）、
`api/item/gun/AbstractGunItem.java:263`（上游注释原文）、`api/item/nbt/ItemStackNbtHelper.java:97`（「占位」是业务含义：空栈标记）、
`item/ModernKineticGunItem.java:575`（`switch` 的 `default -> // 未实现` **上游原样**，`ReloadType` 只有 MAGAZINE/FUEL 两值）、
`client/event/PreventsHotbarEvent.java:42`（上游 `// todo 需要测试行为` 原样）、
`command/sub/ListPackCommand.java:9`（上游遗留未实现类，`/tacz list` 上游就没做）、
`util/LaserColorUtil.java:157`（「占位方法对象」是业务语义）、`mcshim/OverlayTexture.java:12`（语义占位说明）、
`inventory/GuiHandler.java:38`（**过期注释**，代码已实现 —— 见第四节）

**fp 的 7 行**：`mcshim/GsonHelper.java:265/266/277`、`resource/manager/AttachmentScriptManager.java:76`、`resource/serialize/Vec3Serializer.java:20/21/22`（均为 `toDouble`/`convertToDouble` 里的字面 "todo"）

**(b) 240 行的共同特征**（全部 263 行逐条读过后的结论）：每一条都至少满足以下一项——
① 带 `TODO(port)` / `TODO(L2)` 并引用上游文件名 + 行号；② 明确写出 1.7.10 缺失的类/API 名（`PoseStack`/`ParticleArgument`/`RecipeManager`/`ItemTransforms`/`BellBlock`/`CriteriaTriggers`/`ARCompat`…）；
③ 属 compat 反射可选兼容（用户已裁决「缺失库静默跳过」）。
代表性条目与依据见第三节表格；逐行归属文件在 `build/completeness/todo_classified.txt`。

---

## 六、未覆盖说明

1. **上游 305 条「移植缺失的方法」只逐条核对了 41 条**（`block/TargetBlock`、`block/GunDisplayCaseBlock`、`block/GunPegboardBlock`、`block/StatueBlock`、`block/GunSmithTableBlockB/C`、`item/AmmoBoxItem`、`item/AmmoItem`、`item/AttachmentItem`、`item/AmmoStorageBlockItem`、`api/item/gun/AbstractGunItem`、`entity/EntityKineticBullet`、`entity/TargetMinecart`、`resource/GunPackLoader`、`client/input/GunInputHandler`、`particles/BulletHoleOption`、`util/RenderHelper`）。
   结论：全部有 1.7.10 等价落点（`use`→`onBlockActivated`、`getShape/updateShape/onRemove`→`setBlockBoundsBasedOnState`+`breakBlock`、`appendHoverText/getName/isFoil/isBarVisible`→`Item` 侧钩子或客户端 tooltip、`fromNetwork/writeToNetwork/fromCommand/readParticle`→1.7.10 无粒子网络序列化、`initializeClient/getCustomRenderer`→`IItemRenderer` 注册）。
   **其余 264 条按命名规则归类，未逐条读代码**——这些命名集中在 1.20 专有 override（`getStateForPlacement`/`setPlacedBy`/`playerWillDestroy`/`getTicker`/`newBlockEntity`/`getCloneItemStack`/`mirror`/`propertyForDirection`/`canSurvive`/`onProjectileHit`…），风险相对低，但**不排除有个别真实现被整体删掉**。
2. **512 行宽口径（含「降级/简化/暂不/省去/空实现」）只做了抽样**：这些多为说明性散文，不是待办标记。抽查了其中 30+ 行，全部是「说明自己为什么这么写」的解释性文字。
3. **`client/renderer/**`（36 文件 7,197 行）、`client/model/**`（41 文件 5,731 行）没有逐文件细读**：只做了结构性扫描（空体/假返回/长度差/上游缺失方法）与关键路径抽读（stencil、AR 回退、VBO 回退、scope 管线）。渲染的**运行期**正确性不在本轮证明范围（r1 的 01-render.md 已覆盖）。
4. **`mcshim/**`（39 文件 7,140 行，上游无对应）只按「有没有被消费」检查**：`RenderType.sortOnUpload`、`RenderSystem.setShader`、`OverlayTexture.NO_OVERLAY`、`TessellatorVertexConsumer.overlayCoords` 四处降级都有注释，未逐个验证「调用方是否依赖这些语义」。这是下一轮可以单独做的一块（mcshim 语义审计）。
5. **`data/` 目录（1.7.10 无 datapack）的其它类别**只核了 `recipes`（P0）与 `tacz_loot_injectors`（P2）；`advancements`（已登记为不支持）、`tacz_tags`/`recipe_filters`/`scripts` 未逐一验证加载完整性。
6. **未验证运行期**：本轮禁止跑 gradle / runClient，所有真骨架结论都是**静态调用链证据**（0 调用者 / 空实现 / 未注册）。P0 那条的实际表现建议 Lead 用「打开工作台看列表是否为空」一条实机用例确认。
