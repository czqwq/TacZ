# 第二轮全模块审查 · 汇总（r2 SUMMARY）

> 触发：用户要求按 7 个维度复审（渲染材质 / 绘制GL / 枪械逻辑 / GUI 按钮 / 上游保真 / 移植完整性 / 本地化与图标）。
> 组织：Lead + **7 个 teammate** 并行，写作用域按包互不重叠，共享任务板 `task-1..task-10`。
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）。
> 1.7.10 准绳：`build/rfg/minecraft-src/java/**`（原版 + Forge 反编译源码）。
> 纪律：**teammate 全程禁止运行 gradle**；构建由 Lead 在全部收工后统一执行（见文末）。
> 分报告（8 份）：
> [01-render-material.md](01-render-material.md) · [02-draw-gl.md](02-draw-gl.md) · [03-gun-logic.md](03-gun-logic.md) ·
> [04-gui.md](04-gui.md) · [05-fidelity.md](05-fidelity.md)（+ [05-fidelity-drift-data.md](05-fidelity-drift-data.md)）·
> [06-completeness.md](06-completeness.md) · [07-i18n-icon.md](07-i18n-icon.md)

---

## 一、七个问题的直接回答

| # | 用户的问题 | 结论 | 证据 |
|---|---|---|---|
| 1 | **渲染/材质层是否存在问题（比如不渲染）** | **有两处真会「看不到东西」的根因，均已修**：① 雪暴（SBM）粒子定义**从未装载** → 默认枪包每一把枪都是 `renew` 型，火光/枪烟**完全不显示**；② 第一人称枪**不吃 lightmap** → 亮度随画面里有无生物跳变（不是严格恒亮）。另有 GUI 图标上下颠倒、RenderType 全局 GL 状态污染两类显示错误 | [01](01-render-material.md) §一.1/§一.3、[02](02-draw-gl.md) §二 P1-1/P1-2 |
| 2 | **武器绘制层与 GL 层是否存在问题** | **前轮 3 个 P0 逐行确认「修复在且正确」**（顶点属性错位 / 相机空间基座 / 背包图标 16×）。本轮新增 1 个 P1：`RenderType.clearRenderState()` 把 1.20 的**括号作用域**写成了「强制恢复固定基线」→ 第三人称持枪玩家之后，同帧后续**所有实体丢 `GL_LIGHTING`/`GL_ALPHA_TEST`**（变平变亮、cutout 失效）。已改为进 setup 快照、出 clear 原样还原。**Tessellator 无嵌套、0 处漏 `endVertex`、`glPushMatrix/PopMatrix` 全成对** | [02](02-draw-gl.md) §一/§三（17 个 render 入口的 GL 状态表） |
| 3 | **武器逻辑/视角/开火/换弹是否存在问题** | **有 1 个 P0 级灾难，已修**：TACZ 的 `SHOOT_KEY/AIM_KEY` 与 1.7.10 原版攻击/使用键同为 `-100/-99`，而 `KeyBinding` 构造器把键码写进**静态 `IntHashMap`（后写覆盖）** → 装 TACZ 后**原版左/右键整局失效**（挖掘/攻击/使用/放置/开箱/进食全废）。另修 5 个 P1（伤害源未接线导致穿甲/防弹全废、子弹方块射线用错 API、标靶车放置命中盒、改装界面动画恒 0、多段实体 part→core）。**全自动连发成立**——上游 1.20 根本不靠 `onUseTick`，本工程逐行等价 | [03](03-gun-logic.md) §1.1 F1~F8、§1.2 |
| 4 | **GUI 按钮是否存在错位/显示错误** | **有 1 个 P0，已修**：创造模式物品栏**完全无法滚动**（照搬上游 `sourceItems == displayItems` 身份判等，1.7.10 每次都是新 List → 恒 false → 每帧 `scrollTo(0)`）。另修 4 个 P1 + 4 个 P2，并**撤回 1 条误判**。坐标系逐 Screen 核过（`GuiContainer` 背景=绝对 / 前景=相对），**无漏加或重复加 `guiLeft`**；12 处贴图引用经 PNG IHDR 实测**尺寸全部匹配** | [04](04-gui.md) §一 F1~F9、§三/§四 |
| 5 | **是否偏离上游 / 是否乱搓方法名** | **「自己乱搓方法名」= 0 条，且机械可证**：264 对「上游方法消失 + 本移植出现新名」逐对核对，100% 是 1.20 平台 API → 1.7.10 平台 API（`use→onBlockActivated`、`appendHoverText→addInformation`、`inventoryTick→onUpdate`…）；`entity/shooter` 15 文件方法名差异 **0**。**真漂移仅 1 条**（多段实体 `MaybeMultipartEntity` 未移植）→ 已修并经独立复核闭环。`@SerializedName` 漂移 0、配置键 89/89、YSM 11 组硬契约通过 | [05](05-fidelity.md) 结论速览 + [数据附录](05-fidelity-drift-data.md) |
| 6 | **移植完整性：是否有只有骨架的代码** | **真骨架 5 条 / 半骨架 2 条**，其中最大的一条不是「空方法」而是「**入口没人调**」：枪包 `data/<ns>/recipes/**` **从来没被读过** → 工作台配方恒 0 条、做不出任何枪/配件/弹药 → **已修，199/199 全部注册**。另有 `RefitTransform` 从未注册事件总线（→ 改装界面配件类别按钮**只有第一次点有效**）、`ConvertCommand.setConverter`/`PegboardStressCommand.placeBoards` 两处「入口在、接线不在且 TODO 理由已过期」，均已修 | [06](06-completeness.md) §一/§二、[07](07-i18n-icon.md) §零 |
| 7 | **物品名 / 本地化 / 内置包图标** | **根因两条 P0，均已修**：① `.lang` 没做 1.20.1 → 1.7.10 的**前缀 + `.name` 后缀**转换 → 全部物品/方块显示原始 key（`tile.tacz.target.name`）或带残留后缀（`Minecart with Target.name`）；② `CommonAssetsManager.registerEventHandlers()` **传了 `Class` 而不是实例** → 1.7.10 EventBus 扫不到任何处理器 → `onPlayerLoggedIn` 永不触发 → **多人服客户端收不到枪包数据**（枪既无名字也无模型）。另：**缺失贴图 0、内置包数据加载失败 0** | [07](07-i18n-icon.md) §一 |

---

## 二、本轮修掉的 P0（按影响面排序）

| # | 问题 | 玩家可见后果 | 位置 | 来源 |
|---|---|---|---|---|
| **P0-1** | **TACZ 键位顶掉原版攻击/使用键** | 装 TACZ 后**原版左键（挖掘/攻击/连挖）与右键（使用/放置/开箱/进食/拉弓）整局无响应** | `client/input/GunInputHandler.java` | [03](03-gun-logic.md) F1 |
| **P0-2** | **工作台配方从来没被加载**（199 条 json 无人读）+ 第二层断裂（`initLazyRecipes()` 遍历 `CraftingManager` 恒 0 次） | 工作台能打开但**左侧列表恒空、任何枪/配件/弹药都做不出**；即使显示也因 `getOutput()==null` 静默失败 | `resource/manager/GunSmithTableRecipeManager.java`（新增）、`CommonAssetsManager`、`crafting/**` | [06](06-completeness.md) §一、[07](07-i18n-icon.md) §零 |
| **P0-3** | **`.lang` 缺 1.7.10 键形态** | 全部方块物品显示 `tile.tacz.<name>.name`；`TargetMinecart` 显示 `Minecart with Target.name` | `assets/tacz/lang/*.lang`（20 语种）+ `tools/lang_json_to_lang.py` | [07](07-i18n-icon.md) §一.1 |
| **P0-4** | **`registerEventHandlers(Class)` 注册不到任何处理器** | **多人服**客户端收不到 `ServerMessageSyncGunPack` → 枪无名字、无模型（单人档不受影响） | `resource/CommonAssetsManager.java` | [07](07-i18n-icon.md) §一.2 |
| **P0-5** | **创造模式物品栏无法滚动** | 侧栏分页后列表被每帧拉回第一页，且每帧重建整页 ItemStack | `client/gui/components/CreativePackFilters.java` | [04](04-gui.md) F1 |
| **P0-6** | **雪暴粒子定义从不装载** | 默认枪包**每一把枪**都是 `renew` 型 → 开火**没有火光、没有枪烟** | `client/particle/ParticleDefinitionReloader.java`（新增）+ SBM `ParticleDefinitionLoader` | [01](01-render-material.md) §一.1 |
| **P0-7** | **`PackInfo.name == null` 是真 NPE**（第三方枪包显式写 `"name": null` 时）：`new ChatComponentTranslation(null)` 构造不报错，但 `getFormattedText()` 走 `initializeFromFormat(null)` → `Pattern#matcher(null)` 抛 NPE | **枪械/配件/弹药 tooltip 整套消失**；`GunPackList` **构造期崩溃**（开创造栏即崩） | `client/tooltip/PackInfoText.java`（新增）+ `GunPackList`、`GunSmithTableScreen`、3 个 tooltip、`TooltipEvent` | [04](04-gui.md) §八 |

## 三、本轮修掉的 P1（12 条）

| 域 | 问题 | 位置 |
|---|---|---|
| 开火/弹道 | 子弹伤害源没接线 → **穿甲属性 / `BULLET_RESISTANCE` / 死亡消息全废**（普通与穿甲用同一条 `causeThrownDamage`） | `entity/EntityKineticBullet.java` |
| 开火/弹道 | 子弹方块射线用 `World#rayTraceBlocks` → 被**高草/花/火把**挡住、**打不穿铁栏杆/栅栏/树叶**、无视可穿透配置 | `entity/EntityKineticBullet.java`（改调已有 `BlockRayTrace`） |
| 开火/弹道 | **多段实体 part→core 机制未移植**（[05](05-fidelity.md) 的真漂移 D-1）：打末影龙时受击/击杀事件全丢、穿甲段被本体无敌帧吞、击退失效 | `entity/EntityKineticBullet.java`（新增 `MaybeMultipartEntity`） |
| 逻辑 | 标靶车**用物品放置**时命中盒退回 0.98×0.7（前轮只修了 1 参构造器） | `entity/TargetMinecart.java` |
| GUI | 改装界面**配件类别按钮只有第一次点有效**（`RefitTransform` 从未注册 + `static` handler 会 ICCE + 注册总线错） | `client/animation/screen/RefitTransform.java` |
| GUI | 富 tooltip **全是死代码** → 枪的弹药/伤害/穿甲/等级/类型/枪包名/描述**根本不显示** | `client/event/TooltipEvent.java`（接线 5 个 `Client*Tooltip`） |
| GUI | 描述里的**字面 `\n`** 被当正文画出来（`Locale` 不做转义，全仓 0 处 split） | `client/tooltip/**`、`GunSmithTableScreen` |
| GUI | 「展示柜已上锁」提示**永不消失**（`onClientTick` 从未注册） | `client/gui/overlay/DisplayCaseLockHintOverlay.java` |
| 渲染 | `RenderType.clearRenderState()` 强制基线 → 同帧后续所有实体丢 `GL_LIGHTING`/`ALPHA_TEST` | `mcshim/RenderType.java` |
| 渲染 | GUI/背包图标**上下颠倒**（`scale(16,16,16)` 缺 1.20 的 Y 翻转） | `client/model/RenderHelperCompat.java` |
| 渲染 | **第一人称枪不吃 lightmap** → 亮度随画面里有无生物跳变 | `FirstPersonRenderHandler.java`（`enableLightmap`/`disableLightmap` + `packedLight` 改真实光照） |
| 本地化 | 外部枪包语言只在 `postInit` 注入一次，**切语言后失效**（判定**不需要 mixin**） | `GunPackLoader#injectAllGunPackLanguages`、`ClientSetupEvent` |

## 四、Lead 在收尾阶段自行修掉的（无人认领的作用域）

| 位置 | 内容 |
|---|---|
| `FirstPersonRenderHandler.java` | 补 `EnableLightmap/disableLightmap`（复刻 vanilla `renderItemInFirstPerson` 的 `enable(:796)→画(:797)→disable(:798)`）+ `packedLight` 从写死 `FULL_BRIGHT` 改为 `getLightBrightnessBySkyBlocks(玩家脚部)`（依据 `ItemRenderer.java:287`，该方法就在被 cancel 的 `renderItemInFirstPerson` 内）；`finally` 兜底成对关闭 |
| `mcshim/RenderType.java` | `entityCutoutNoCull` / `entityCutoutNoCullZOffset` 实参错位（`opaqueCutout(cull, polygonOffset)` 第 1 参是 cull）；**可裁剪族的 `cull=false` 故意不改**，登记为平台偏离（安全超集 vs 绕序不符会整片消失） |
| `client/ClientProxy.java` | 注入 `ConvertCommand.setConverter(PackConvertor::convert)`（原 TODO 理由已过期，`/tacz convert` 之前只打 warn） |
| `command/sub/PegboardStressCommand.java` | 实装 `placeBoards`（原恒 `return 0`；`setBlock` → `getTileEntity` 惰性创建 → `setItems`） |
| `client/renderer/block/{GunPegboardRenderer,GunDisplayCaseRenderer}.java` | `ItemPreviewRenderContext.alpha`（**静态**字段）的复位与 `popPose()` 移进 `finally`——异常一次就会让**所有枪永久半透明** |
| `event/SyncedEntityDataEvent.java`、`init/ModRecipe.java`、`command/sub/ConvertCommand.java` | 三处**过期 TODO/注释**清理（理由已完成或对象已不存在，留着会误导下一轮） |
| `block/PortWriteProbe.java` | 删除残留临时探针文件 |

---

## 五、明确未修（需下一轮，均已给 file:line + 修法）

### 需 mixin 作用域（本轮无人持有，且无法实机验证，故不冒险新增注入）

| 级别 | 问题 | 位置 | 修法 |
|---|---|---|---|
| P1 | **枪械击退数据完全不生效**：`KnockBackModifier#setKnockBackStrength` 的值**无人消费**（`KnockbackChange.modifyStrength` 0 调用者），枪包 `BulletData.knockback` / 配件击退全被丢弃 | `event/KnockbackChange.java`、`entity/EntityKineticBullet.java:509-516` | `@Mixin(EntityLivingBase)` 在 `attackEntityFrom` 的击退计算处 `@ModifyVariable` |
| P1 | **持枪左键点方块 → 客户端幽灵方块/不同步**（P0-1 修复后浮出的**既有平台差异**，不是新代码错误）：1.7.10 的 `PlayerInteractEvent.LEFT_CLICK_BLOCK` **只在服务端**派发（`ItemInWorldManager.java:157`，全仓唯一），客户端挖掘预测未被拦截 | `client/input/GunInputHandler` + 1.7.10 `Minecraft#func_147116_af` | `@Mixin(Minecraft)` 在 `clickMouse/clickBlock` HEAD 判 `IGun.mainHandHoldGun` 直接 return（等价上游 `ClientPreventGunClick`） |

### 非 mixin，但本轮未做（影响小或需产品裁决）

| 级别 | 问题 | 位置 |
|---|---|---|
| P1 | 战利品注入 0 调用者（奖励箱送 Taurus 943 静默失效） | `loot/LootTableInjectorModifier.java:77`、`init/ModLootModifiers.java` |
| P1 | 第一人称枪**不吃 `hurtCameraEffect`**（受伤时枪体不晃，`GunHurtBobTweak` 对第一人称失效） | `FirstPersonRenderHandler.java` |
| P2 | `ScopeLensRenderer` 遗弃 texture unit 1/2 绑定；`ScopeThermalRenderer` 用 `glLoadMatrix` 覆盖投影/视图后不还原 | `client/renderer/scope/**` |
| P2 | `GunTime.setTickGate(...)` **0 调用者** → 单机暂停时开火队列/动画时钟仍在走 | `util/time/GunTime.java` |
| P2 | `InteractKeyTextOverlay` 丢了上游的 `occupied` 分支（对着已有枪的展示格没有交互提示） | `client/gui/overlay/InteractKeyTextOverlay.java:115-127` |
| P2 | `CommonRegistry` 里 `EVENT_BUS.register(CommonRegistry.class)` 是死代码（1.7.10 传 Class 注册不到任何东西），与团队自身铁律矛盾 | `init/CommonRegistry.java:165-167` |
| P2 | 组合镜的 stencil 遮挡在 1.7.10 主 framebuffer 上**按 GL 规范恒通过** → 镜内会看到枪体（只登记了「API 缺失」，没写消费端后果） | `client/model/BedrockGunModel.java:445-453`、`BedrockAttachmentModel.java:481/512/532` |
| P2 | `GunAnimationStateContext#setCurrentGunItem` 在切到非枪时不清 `display/gunData`（1.7.10「索引缺失/空手」是常态） | `client/animation/statemachine/GunAnimationStateContext.java:590-604` |
| P2 | 变焦键与近战键**默认同为 V**，而 1.7.10 一次按下只派发第一个匹配动作 → **变焦键实际不可用** | `client/input/GunInputHandler.java:225-255` |
| — | 25 条配方因 1.7.10 原版**无紫水晶/下界合金/铁粒**而永久不可合成（**数据差异，非 bug**）；`laser_peq15`/`sight_p90` 两条 lang key **上游同样缺**（按保真不修） | 数据侧 |

---

## 六、已确认「没问题」的关键项（避免下一轮重复排查）

- **Tessellator**：全仓 8 处 `startDrawing*` 全部自包含，**无单例嵌套**；本作用域 **0 处漏 `endVertex()`**；`glPushMatrix/glPopMatrix`/`glPushAttrib/glPopAttrib`/`glMatrixMode` 全部成对。
- **PoseStack**：`translate/scale/mulPose/rotateAround` 全为右乘，与 1.20 JOML 逐行同构；`stackTopAsFloatBuffer` 列主序可直接 `glMultMatrixf`。
- **全自动按住连发成立**：上游 1.20 的 TACZ **不实现** `use`/`onUseTick`/`getUseDuration`（grep 0 命中），开火是 `ShootKey` 每 tick 轮询 → `LocalPlayerShoot` 用调度线程按 `shootInterval` 发 `ClientMessagePlayerShoot`；本工程逐行等价，**因此也没有 `EnumAction.BOW` 拉弓副作用**。
- **换弹/拉栓状态机**：`LivingEntityReload`、`LivingEntityBolt` ↔ 上游逐行同构；三段 bolt 弹药扣减分支一致。
- **前轮修复复核**：`currentGunItem` null 归一化（22 处）、子弹 `setPosition`、击杀事件 `!isEntityAlive()`、热量冷却、trackingRange 单位、`GunAnimationStateContext` 空值防护、`PoseStack`/`LightTexture` 打包 —— **逐条回代码确认「修复在且正确」**。
- **材质绑定主链路是通的**：`RenderType.setupRenderState → TextureManager.bindTexture → SimpleTexture`（完整路径加载），缺资源走 `TextureUtil.missingTexture`（紫黑棋盘），**不会空绑定或黑图**。
- **bedrock 烘焙链** 9 个类与上游**逐项等价**（六向 `offsetX/Y/Z` 逐分量相同），未发现贴图错位/白模黑模的新增根因。
- **数据侧**：232 个 index（70 枪 + 25 弹 + 128 配件 + 3 方块 + 6 适配器）的 `data`/`display` 引用 **100% 命中**；`EnumMap` 4 处字段全覆盖；**缺失贴图 0**。
- **上游保真**：缺失类 69 = 61 个 1.20 专属兼容模块（按已确认决策只留反射骨架）+ 8 个平台强制替换；新增类 92 全部有类 javadoc（89 明示上游对应物 + 理由）。
- **YSM 契约**：11 组符号**逐符号独立复核全部通过**，api 零签名漂移（仅 `LivingEntity→EntityLivingBase`、`LogicalSide→Side` 平台替换）。

---

## 七、构建与实机回归

**构建**：由 Lead 在 7 个 teammate 全部收工后统一执行 `./gradlew spotlessApply build`
（按 `AGENTS.md`：agent 运行期间禁止 gradle）。

| 轮次 | 结果 | 日志 |
|---|---|---|
| 1 | **BUILD SUCCESSFUL in 5m 51s** | `build/r2-final-build.log` |
| 2（覆盖 gui-review 的 `authors` 判空） | **BUILD SUCCESSFUL in 28s**，`compileJava UP-TO-DATE` | `build/r2-final-build2.log` |
| 3（覆盖 i18n-icon 的 3 处 pojo 空安全） | **BUILD SUCCESSFUL in 2m 52s** | `build/r2-final-build3.log` |
| **4（最终，覆盖 gui-review 的 7 文件 `PackInfo` 空安全扫查）** | **BUILD SUCCESSFUL in 4m 27s** | `build/r2-final-build4.log` |

- 最终产物：`build/libs/tacz-5.11.45.140.jar`（72,056,308 bytes，reobf 后的 release jar，20:08:52）
- 已核：**构建之后工作区无任何源码改动**（全部进入构建）
- 编译仅 2 条**既有**警告（`LanguageMixin` / `SoundManagerPreparationsMixin` 的 `@Mixin(targets=...)`
  应改用 `value`），这两个类**有意不列进 `mixins.tacz.json`**，不影响功能。

> 说明：之所以要跑 4 轮，是因为 teammate 收工后仍在做**跨包交叉核查**并继续提交真实修复
> （r2-04c `PackInfo` 空安全、r2-07b 的 `BlockData`/`BlockIndexPOJO` 空安全）。
> 每一轮都确认了「源码全部进入构建」才结束，最终轮之后未再有任何改动。

---

# 第三轮（r3）：清掉 r2 的「未修清单」

> r2 结束时 `§五` 还剩 13 条未修，其中 2 条标注「需 mixin 作用域」。
> 用户要求「修复未解决的问题」，并按域派出 5 个 teammate 并行；另新增两项专项审查
> （`resource/**` 深度审、**接线未接线 + 死代码**）。
> 任务板 `task-11..15`；全部收工后 Lead 统一 `./gradlew spotlessApply build`。

## r3 完成情况

| 任务 | Owner | 内容 | 结果 |
|---|---|---|---|
| task-11 | **mixin-fixer**（新派） | 枪械击退链 / 持枪挖掘幽灵方块 / `CommonRegistry` 死代码 | **2 个新 mixin**：[08-mixin-fix.md](08-mixin-fix.md) |
| task-12 | render-material | 第一人称受伤晃动 / 镜片 GL 绑定与矩阵 / stencil 注释 / 切枪陈旧状态 | 5 项全修：[01](01-render-material.md) 末尾 |
| task-13 | gun-logic | `GunTime` 暂停时钟 / 战利品注入接线 / 输入分发语义 | 3 项：[03](03-gun-logic.md) 末尾 |
| task-14 | gui-review | **`resource/**` 深度审查** + `InteractKeyTextOverlay` | [09-resource-review.md](09-resource-review.md) |
| task-15 | completeness-audit | **「接线未接线」+ 死代码**专项 | [09-wiring-deadcode.md](09-wiring-deadcode.md) |

## r3 修掉的关键项

| 域 | 问题 | 落点 |
|---|---|---|
| **mixin** | **枪械击退数据完全不生效**（`KnockbackChange.modifyStrength` 0 调用者）。**关键纠正**：击退强度**不在** `attackEntityFrom` 里算 —— `:921` 只转调 `knockBack`，强度是 `knockBack` 里的 `float f2 = 0.4F`（`EntityLivingBase.java:1056`）⇒ 用 `@ModifyConstant(floatValue = 0.4F)`（免描述符、免局部变量槽位） | `mixin/common/LivingEntityKnockbackMixin`（新增） |
| **mixin** | **持枪左键点方块 → 客户端幽灵方块**：1.7.10 客户端**根本不 post** `LEFT_CLICK_BLOCK`（全仓唯一派发点在服务端 `ItemInWorldManager.java:157`），客户端挖掘预测无法用事件拦住 ⇒ 只能源码级注入。打在 `Minecraft` 的 `func_147116_af`/`func_147115_a` HEAD（连「挥手 + 碎屑」一起挡），并在 cancel 前补 `resetBlockRemoving()` 堵「挖到一半切枪」的反向不同步 | `mixin/client/MinecraftAttackMixin`（新增） |
| 渲染 | **第一人称枪不吃 `hurtCameraEffect`**（受伤时枪体不晃，`GameRendererMixin` 的 `BobHurt` 注入连带走不到）。按 vanilla 相对顺序 `hurt(:787) → lightmap(:796) → 手持物(:797)` 精确插入；事件选型独立核实为 `RenderItemInHandBobEvent.BobHurt`（`renderHand` 用 `getFOVModifier(..., false)`）；**只在确定接管并 cancel 的枪械路径**补偿，非枪械路径不重复叠 | `FirstPersonRenderHandler` |
| 渲染 | 镜片 pass 遗弃 **texture unit 1/2** 绑定（unit 1 是 1.7.10 的 **lightmap 单元**）；`ScopeThermalRenderer` 用 `glLoadMatrix` 覆盖投影/视图后不还原 | `ScopeLensRenderer` / `ScopeThermalRenderer` |
| 渲染 | `GunAnimationStateContext` 切到空手时**不清** `display/gunData` → 「非 null 的陈旧值」这一层判空挡不住（空手仍 `shouldSlide()`、旧 display 抛壳） | `GunAnimationStateContext` |
| 逻辑 | **战利品注入 0 调用者** → 做成通用机制：箱子→`ChestGenHooks` 候选池、实体→`LivingDropsEvent`、方块→`HarvestDropsEvent`、新世界→`WorldEvent.CreateSpawnPosition`；并解决「`CommonAssetsManager` 在 `FMLServerStarting` 才装载、奖励箱在其之前生成」的时序 | `event/LootInjectionEvent`（新增） |
| 逻辑 | **`GunTime` 暂停时钟不停走** → 不只补闸门，在 `nowNanos()` 里实现**暂停时长累加补偿**（对齐上游 `AnimationClocks.client()` 的「时钟本身停走」），并自接在客户端 init step | `GunTime` |
| 逻辑 | 输入分发偏离上游：一次按下会**重复触发已按住的键**、同 tick 多键**延迟 1 tick** → 改为上游 `onPress(key)` 语义（keyCode 边缘去重 + 修饰键过滤，移除 `break`） | `GunInputHandler` |
| resource | `CommonGunIndex#checkRecoil` 缺 `keyFrame`/`getValue()` 判空：`{"value":null}` / `[null]` 会让 NPE **逃出单文件跳过路径** → 服务端起不来 | `resource/index/CommonGunIndex` |
| GUI | `InteractKeyTextOverlay` 丢上游 3 处判定（`isLocked` 守卫 / 槽位命中 / `occupied \|\| canPlaceHeld`）→ 上锁的展示柜照旧提示、已有枪的格子不提示 | `InteractKeyTextOverlay` |
| Lead | `EntityKineticBullet` 的击退 `set/reset` **不在 `try/finally`**：r3 起 mixin 会真的消费该值，漏复位会污染该实体之后**所有**伤害源的击退 | `EntityKineticBullet:510-522` |

## r3 的两处「结论纠正」（比修复本身更有价值）

1. **`expect = 1` 在 `require = 0` 下是 100% 静默的**（核 UniMixins `InjectionInfo.java:524-538`：`expect` 只有加
   `-Dmixin.debug.injectors=true` 才抛异常）。⇒ 新 mixin **注入点未命中时什么都不会说**，
   [08-mixin-fix.md](08-mixin-fix.md) §三 的 3 条实机用例因此不可省。
2. **stencil 遮挡不是「绝对失效」**：r2 写「1.7.10 主 FBO 创建时就没有 stencil 附件」不准确。
   实际是 `ForgeHooksClient.stencilBits` **默认 0** 才没有；以 `-Dforge.forceDisplayStencil=true`
   启动且显卡支持时 FBO 会挂 `DEPTH24_STENCIL8`，该机制**部分恢复**（届时需重验阈值）。

## r3 构建

| 轮次 | 结果 | 日志 |
|---|---|---|
| 1（5 个 teammate 全部收工后） | **BUILD SUCCESSFUL in 6m 56s** | `build/r3-build1.log` |
| **2（最终，覆盖审计挖出的 4 条接线缺口）** | **BUILD SUCCESSFUL in 4m 3s** | `build/r3-build2.log` |

- 最终产物 `build/libs/tacz-5.11.45.140.jar`（**72,077,081 bytes，21:34:04**）
- **构建后工作区 0 处源码改动**；r3 改动的 19 + 4 个 Java 文件 `lint_files(errorsOnly)` 全 **0 error**

## r3-05 审计挖出的 4 条「接线未接线」缺口（Lead 已全部修掉）

审计把「接线未接线」按 **5 类 × 全量** 复扫了一遍（而非抽样），规模与结论：

| 扫描面 | 规模 | 结果 |
|---|---|---|
| 事件注册 | 51 个 `@SubscribeEvent` 类 + 上游 62 个事件类 | 50 正确总线 / 1 P3；**`register(Xxx.class)` live 代码 0 处** |
| 静态注入点 | 26 setter + 6 钩子字段 + 1 Impl 接口 | 13 个 0 调用点 → **3 条真缺口** |
| 管理器生命周期 | 120 个 `reload/init/load/clear/initAll/register*/markLoadComplete` | 关键 10 个逐点核了调用行，**0 孤儿** |
| 注册表生产者-消费者 | `DataType` 15 / `KeyBinding` 12 / 46 网络包 / `IItemRenderer` 7 / 渲染器 7 | 仅 1 条真缺口 |
| 配置项 | 88 个 | 仅 2 个 0 读取，且**上游同样 0 读取** |

**死代码**：把 r2 漏掉的**方法引用（`::name`）**计入后，「上游有调用、移植后变死」从 215 条候选降到 **0**；
整类/方法/常量三档里唯一真回归是 `ExternalGunEffects`（上游被调用，本移植因 VBO 路径恒 `UNSUPPORTED`
永不可达，属已登记降级的下游衍生）。

| # | 级别 | 缺口 | Lead 修法 |
|---|---|---|---|
| 1 | **P1** | **弹药库「弹药明细列表」永远为空**：`AmmoStorageMenu.setContentSender(...)` 0 调用者 → `ServerMessageAmmoStorageContents` **全仓无生产者**（已注册进网络、客户端 handler 也在，就是没人发）。玩家只能看到两根进度条，**看不到也取不出具体弹药** | `NetworkHandler#init()` 末尾注入 `ContentSender` → `sendToClientPlayer(new ServerMessageAmmoStorageContents(...))` |
| 2 | P2 | **GUI 内绘制物品不绕过 LOD**：`util/RenderDistance.setScreenDrawActive(...)` 0 调用者（而 `ScreenItemVboContext.isActive()` 有实现但没接上）→ 改装界面/展示柜预览**显示低模**（上游保证 GUI 内恒高模） | `ScreenItemVboContext` 的 `beforeScreen/afterScreen` 推送 `setScreenDrawActive(depth > 0)` |
| 3 | P2 | **改装界面激光颜色滑条默认色恒红**：`LaserColorUtil.setClientDefaultColorResolver(...)` 0 调用者 → 回退 `0xFF0000`。光束本身走另一条重载、颜色正确，只影响滑条初值 | `ClientSetupEvent` 注入 `resolveLaserDefaultColor`（配件走 `getClientAttachmentIndex`、枪械走 `getGunDisplay`，与上游两处静态调用逐条对应） |
| 4 | P3 | `DisplayCaseLockHintOverlay#onClientTick` 从未注册（`ticksRemaining` 永不递减；r2 的墙钟兜底已保证提示会消失，本条修的是「tick 计数是死的」）。同时该方法是 `public static` + `@SubscribeEvent`，**静态处理器会抛 `IncompatibleClassChangeError`** | 改实例方法 + 在 `registerOverlays()` 里注册到 **FML 总线**（`TickEvent.ClientTickEvent` 只从 FML 总线发出） |

## r3 实机验证清单（按优先级，来自各报告）

1. **（mixin · 必验）** 持枪左键按住方块 1s → 方块不动、无进度条、无碎屑、不挥手，且**同一次按住仍能开火**；若方块瞬间消失又被服务端打回 = mixin 未命中。
2. **（mixin · 必验）** 击退：`knockback=0` 的枪打怪应**几乎不位移**、`0.875` 的枪应明显更远；若两者都还是原版 0.4 → 未命中。**注意**：默认包多数枪 `bullet.knockback=0`，修好后「打怪没击退」是**与上游一致的行为**，不是 bug。
3. **（mixin · 必验）** 启动日志：两个新 mixin 无 `InvalidInjectionException` / `Unsupported mixin`；空手/持普通物品的左键挖方块、打怪、右键全部与 r2 一致。
4. **（战利品）** 新建世界开奖励箱 → 应能开出默认枪包配置的 Taurus 943（单箱出现概率约 63%，非必出）；`/tacz reload` 后仍生效。
5. **（暂停时钟）** 单机暂停 → 开火队列/动画时钟应停走；恢复后不应「补跑」被暂停期间的任务。
6. **（受伤晃动）** 被子弹打中时第一人称枪体应随世界一起晃；开镜关镜后枪体亮度回到开镜前水平。
7. **（输入）** 按住某键不应被重复触发；同 tick 按两个键（如 V + 另一键）不应延迟 1 tick；瞄准中按 V 应同时触发近战与变焦（上游同键行为）。
8. **（展示柜）** 上锁的展示柜不应再提示「按 F 交互」；对着已有枪的格子、手上拿不可放置物品时应有提示。

### 收工后追加的两批修复（由交叉核查带出，均已进入最终构建）

| 批次 | 内容 |
|---|---|
| **r2-04c**（[04](04-gui.md) §八） | `PackInfo` 空安全扫查 7 文件。**`name == null` 是真 NPE**：`new ChatComponentTranslation(null)` 构造不报错，但任何 `getFormattedText()` 走 `initializeFromFormat(null)` → `Pattern#matcher(null)` 抛 NPE（`ChatComponentTranslation.java:73-94`）→ 装第三方枪包后**枪械/配件/弹药 tooltip 整套消失**、`GunPackList` **构造期崩**。新增 `PackInfoText`，`name/authors/license/version/date` 全部空安全 |
| **r2-07b 补充**（[07](07-i18n-icon.md) §6.5） | gson 读到显式 `"x": null` 会 `field.set(obj, null)` **覆盖字段初始化值**（内置包 0 命中，仅第三方枪包触发）。修 `BlockData#getTabs()`（每帧 NPE）、`BlockData#getFilter()`、`BlockIndexPOJO#id`（**加载期 NPE 且不在 catch 内 → 服务端起不来**）。另**撤回**两条自报误判（`AttachmentVariantIndexPOJO`/`SlotAdapterIndexPOJO` 的 getter 已有 null 回落） |

**实机回归清单（按优先级）**

1. **（P0-1）** 空手/持普通物品：左键挖方块、左键打怪、右键开箱/进食/放方块 → 全部正常；持枪时左键**只开火**。
2. **（P0-2 / 配方）** 打开工作台：左侧应有配方列表；日志应有 `gun smith table recipes: 199 scanned / 199 registered (199 for network sync)`；用原版材料做一把 ak47 验证**产物非空**。
3. **（P0-3 / P0-4 / 名字）** 创造栏/背包：方块物品名不应是 `tile.tacz.*.name`，标靶车不应是 `Minecart with Target.name`；**多人服**进服后枪应有名字与模型。
4. **（P0-5）** 创造模式侧栏分页后**可以滚动**，列表不被拉回第一页。
5. **（P0-6 / 火光）** 开火应有火光与枪烟（此前完全没有）。
6. **（P1 / 枪图标）** 背包/创造栏枪械图标**正立**（此前上下颠倒）；若反而颠倒，单点回滚 `applyGuiSlotScale` 回 `scale(16,16,16)`。
7. **（P1 / lightmap）** 进洞穴/夜里：第一人称枪应**随环境变暗且亮度稳定**（不随画面里有无生物跳变）。
8. **（P1 / GL）** 第三人称看另一个拿枪的玩家：其后的怪/箱子/告示牌**不应**变平变亮。
9. **（P1 / 弹道）** 子弹应能穿过铁栏杆/栅栏/树叶/高草；穿甲弹对高护甲目标伤害更高；死亡消息为枪械击杀。
10. **（P1 / tooltip）** 悬停枪械应显示弹药/伤害/穿甲/等级/类型/枪包名/描述，且描述中的 `\n` 已正确换行。
11. **（P1 / 改装台）** 打开改装界面 → 切换配件类别**可以反复切换**（此前只有第一次有效），并有转场动画。
12. **（P1 / 末影龙）** 用枪打末影龙：应有伤害数字与受击/击杀特效。
13. 全自动按住连发 / 松开即停 / 半自动需重按 / BURST 3 发 / 栓动需拉栓 / 霰弹多弹丸 / 开镜 FOV 与灵敏度 / 后坐力抬枪 / 换弹各分支打断。
14. **已知不会修好（预期差异）**：持枪对着方块按左键，客户端可能出现**幽灵方块**（需下一轮 mixin，见 §五）；第一人称受伤时枪体**不会晃**。

---

## 八、诚实声明（本轮未闭环的部分）

1. 全程**没有运行 `runClient`**（纪律要求），所有可见性结论都是静态推导 + 1.7.10 反编译源码取证。§七 的每一条都需要实机确认。
2. `client/renderer/scope/**` 的 **GLSL 源码本体**只核了 uniform/attribute 绑定，**着色器数学未逐行对照上游**。
3. `simplebedrockmodel v2`（`v2/client/mesh/**`、`v1/particle/**` 部分）按「恒不走」处理，未逐文件审。
4. [06](06-completeness.md) 的「305 条上游有、移植缺失的方法」只**逐条核对了最可疑的 41 条**，其余按命名规则归类。
5. 相机后坐力/相机动画/开镜 FOV 的**数值观感**、Lua 脚本枪的实际执行，静态审查无法闭环。
