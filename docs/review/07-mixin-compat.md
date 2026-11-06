# 07 · Mixin 与 compat 审查报告（task-7 / mixin-compat-review）

> 审查对象：`src/main/java/com/tacz/guns/mixin/**`（16 文件）、`src/main/java/com/tacz/guns/compat/**`（13 文件）、
> `src/main/resources/mixins.tacz.json`
> 准绳：1.7.10 源码 `build/rfg/minecraft-src/java/**`（逐条抄描述符）；上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`
> 证据：`build/r12build.log`、`build/tmp/mixins/mixins.tacz.refmap.json`、`build/tmp/mixins/mixins.srg`、
> `run/client/crash-reports/*.txt`、UniMixins 0.3.1 内的 Mixin 0.8.7 源码（`unimixins-0.3.1-sources.jar`）
> 写作用域：`mixin/**`、`compat/**`。其它作用域的发现只写进 §9「报告项」。

---

## 1. 结论速览

| 项 | 结论 |
| --- | --- |
| **mixin 能不能正常 apply** | **能**。16 个 `@Mixin` 的 14 个已登记项，目标类/方法/描述符**逐条**在 1.7.10 源码里核对通过，回调签名**全部**精确匹配；`"compatibilityLevel": "JAVA_8"` 与 `jvmDowngrader→8` 一致；refmap 名与产物一致。 |
| **本作用域发现的真 bug** | **1 条（构建期告警声明缺失，已修）**：`GameRendererMixin` 的 `@ModifyArg` 缺 `@At(remap = false)`，导致 AP 每次构建报 `Unable to locate method mapping ... GL11.glRotatef(FFFF)V`。**运行期一直是对的**（GL11 名字不变），属「注解没把事实写清楚」而非功能故障。 |
| **三条 P0 规则** | **全部合规**：① mixin 类内嵌套/匿名类 = 0；② `com.tacz.guns.mixin.**` 下 16/16 都是 `@Mixin`（4 个非 mixin 类已移出/删除，已核实）；③ 回调签名逐条精确匹配（含 `CallbackInfoReturnable<Float/Boolean>` 与「目标有返回值」的强制要求）。 |
| **`mixins.tacz.json` 一致性** | **一致**：`mixins` 3 + `client` 11 = 14，全部是真正的 `@Mixin`；未列入的 2 个（`LanguageMixin`、`SoundManagerPreparationsMixin`）是**零注入的空 `@Mixin`**，留在包里非法风险 = 0（见 §4）。 |
| **`build/r12build.log` 的 `glRotatef` 告警** | **不是真 bug**（运行期匹配的指令名与注解字面量完全相同）；但确实是可修的声明缺失，**已修**（§5）。 |
| **compat/** | 反射探测全部 `initialize=false` + `catch (Throwable)` 静默，**零硬依赖**；Angelica/Oculus/Optifine/Controllable/ShoulderSurfing/AR/KubeJS/JEI/PlayerAnimator/Cloth/CarryOn 语义与上游对齐。**1 处文档/常量漂移已修**（§6）。 |
| **未修风险** | 4 条，均需 Lead 或构建侧裁决（§9）。其中 **P1：`defaultRequire = 1` + `required: true` 在 OptiFine 环境下会把「某个注入点失配」放大成启动崩**。 |

---

## 2. 逐文件核对表（`mixin/**`，16 文件）

「1.7.10 依据」= `build/rfg/minecraft-src/java/` 下的真实声明（行号逐个核对）。

### 2.1 `mixin/common/**`（3，登记在 `mixins`）

| Mixin | 目标类（1.7.10） | 注入点 | 1.7.10 依据 | 回调签名 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `LivingEntityMixin` | `EntityLivingBase` | `@Inject onLivingUpdate` `@At(RETURN)` | `EntityLivingBase.java:1928` `public void onLivingUpdate()`（`RETURN` 唯一，void） | `(CallbackInfo)` | ✅ |
| `ServerPlayerMixin` | `EntityPlayerMP` | `@Inject clonePlayer` `@At(RETURN)` | `EntityPlayerMP.java:990` `public void clonePlayer(EntityPlayer, boolean)` | `(EntityPlayer, boolean, CallbackInfo)` | ✅ |
| `ServerGamePacketListenerImplMixin` | `NetHandlerPlayServer` | `@Redirect processEntityAction` INVOKE `Lnet/minecraft/entity/player/EntityPlayerMP;setSprinting(Z)V` | `NetHandlerPlayServer.java:808` `processEntityAction(C0BPacketEntityAction)`；`setSprinting` 调用点 **2 处**（`:822` true / `:826` false） | `(EntityPlayerMP, boolean)` → `void`（与目标 `setSprinting(Z)V` 同返回值） | ✅ 两处都被 redirect（`allow` 默认不限），与 `LocalPlayerMixin` 客户端侧同构 |

- 共同点：**无 `@Shadow`**、**不引用任何 `client/**` 类**（服务端安全）；`implements` 的 `IGunOperator` / `KnockBackModifier` 与其 manager 全在 `api/**`、`entity/shooter/**`（common）。
- `LivingEntityMixin.initialData()` 对 `getHeldItem() == null` 有判空（`LivingEntityMixin.java:150-153`）——**1.7.10 必需**（1.20 `getMainHandItem()` 恒 `EMPTY`），保留。

### 2.2 `mixin/client/**`（13 文件，11 登记 + 2 未登记）

| Mixin | 目标（1.7.10） | 注入点 | 1.7.10 依据（行号） | 回调签名 | refmap | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| `GameRendererMixin` | `EntityRenderer` | ① `renderWorld` HEAD；② `renderHand` INVOKE `ItemRenderer.renderOverlays(F)V`；③ `hurtCameraEffect` HEAD(cancel) + `@ModifyArg`(GL11.glRotatef ord=2, idx=0)；④ `setupViewBobbing` HEAD(cancel)；⑤ `getFOVModifier` HEAD + RETURN(cancel)；⑥ `orientCamera` RETURN | `renderWorld`=`func_78471_a(FJ)V`；`renderHand`=`func_78476_b(FI)V`（`:751`），`renderOverlays` INVOKE 在 `:805`，位于 `:803` 的 `thirdPersonView==0 && !isPlayerSleeping` 分支内；`getFOVModifier` `:473` `private float`；`hurtCameraEffect` `:507` `private void`；`setupViewBobbing` `:533` `private void`；`orientCamera` `:552` `private void`；`hurtCameraEffect` 内 4 个 `glRotatef`：`:516`(深死)/`:524`(yaw)/**`:525` = `-f1*14.0F` 倾斜**/`:526`(yaw 回正) ⇒ `ordinal=2` **精确命中倾斜角** | ①③④ `(float/int…) + CallbackInfo`；⑤⑥ `CallbackInfoReturnable<Float>`（目标有返回值，**必须**） | 6/6 命中 | ✅ |
| `MouseHandlerMixin` | **`Entity`**（1.7.10 无 `MouseHandler`） | 2× `@ModifyVariable setAngles` `@At(HEAD)` `index=1/2` | `Entity.java:359-379` `@SideOnly(CLIENT) public void setAngles(float yaw, float pitch)`（槽位 0=`this`，1=yaw，2=pitch） | 不涉及回调信息参数，`(float)float` | `setAngles → func_70082_c(FF)V` | ✅ |
| `PlayerModelMixin` | `ModelBiped`（`priority=1100`） | `@Inject setRotationAngles` `@At(TAIL)` | `ModelBiped.java:116` `public void setRotationAngles(FFFFFFLEntity;)V` | `(6×float, Entity, CallbackInfo)` | `func_78087_a` | ✅ |
| `HumanoidModelMixin` | `ModelBiped`（`priority` 默认 1000） | 同上 TAIL（**同目标同注入点**） | 同上；`@Shadow bipedHead/bipedBody/bipedLeftArm/bipedRightArm` = `ModelBiped.java:12,14,15,16` **`public`**（非 final ⇒ **不能写 `@Final`**，现有代码正确地没写） | 同上 | 同上 | ✅ 顺序由 1000→1100 保证（Mixin 按 priority **升序** apply，同注入点回调按 apply 顺序追加） |
| `LocalPlayerMixin` | `EntityPlayerSP` | `onLivingUpdate` HEAD；`@Redirect onLivingUpdate` INVOKE `EntityPlayerSP.setSprinting(Z)V` | `EntityPlayerSP.java:108` `onLivingUpdate()`；`setSprinting` 调用 **4 处**：`:116`(false)/`:217`(true)/`:223`(true)/`:228`(false) | `(CallbackInfo)`；redirect `(EntityPlayerSP, boolean)` | `func_70636_d` / `func_70031_b` | ✅；HEAD 回调内自己调的 `setSprinting` 不受 redirect 影响（redirect 只改原方法体） |
| `ItemInHandRendererMixin` | `ItemRenderer` | `renderItemInFirstPerson` HEAD；`updateEquippedItem` HEAD（**空体**） | `ItemRenderer.java:264` `public void renderItemInFirstPerson(float)`；`:720` `public void updateEquippedItem()`；`@Shadow private ItemStack itemToRender` = `:47` **`private`** ✅ | `(float, CallbackInfo)` / `(CallbackInfo)` | `func_78440_a(F)` / `func_78441_a()` | ✅（空 `@Inject` 见 §9-3） |
| `ItemInHandLayerMixin` | `RenderPlayer` | `renderEquippedItems` HEAD / RETURN，**写全描述符** | `RenderPlayer.java:182` `protected void renderEquippedItems(AbstractClientPlayer, float)`；`:520` 是编译器桥 `renderEquippedItems(EntityLivingBase,F)` → `:522` 转发到真实方法（**只在真实方法上注入，桥不会双触发**） | `(AbstractClientPlayer, float, CallbackInfo)` | `func_77029_c` | ✅ |
| `AbstractButtonMixin` | `GuiButton` | `mousePressed` RETURN | `GuiButton#mousePressed(Minecraft,int,int)Z`（`GuiButton.java:125-128`） | `(Minecraft, int, int, CallbackInfoReturnable<Boolean>)` | `func_146116_c` | ✅ |
| `CreativeModeInventoryScreenMixin` | `GuiContainerCreative`（`implements Runnable`） | `initGui` TAIL；`setCurrentCreativeTab` TAIL；`drawScreen` HEAD + TAIL；`mouseClicked` HEAD(cancel)；`handleMouseInput` HEAD(cancel) | `@Shadow private static int selectedTabIndex` = `GuiContainerCreative.java:43`（`private static`）✅；`@Shadow private float currentScroll` = `:45` ✅；`initGui`/`setCurrentCreativeTab(CreativeTabs)`(`:500` `private`)/`drawScreen`/`mouseClicked`/`handleMouseInput` 5 个选择器**全部**在 refmap 内 | 5 个回调形参与目标逐字一致 | 5/5 命中 | ✅ |
| `CreativeContainerAccessor` | `@Mixin(targets = "…GuiContainerCreative$ContainerCreative")`（**包私有**内部类） | `@Accessor("itemList")` / `@Invoker("scrollTo")`（**interface mixin**，仅 accessor/invoker，合法） | `GuiContainerCreative.java:1003` `static class ContainerCreative`（包私有）；`:1006` `public List<ItemStack> itemList`；`:1038` `public void scrollTo(float)` | — | `itemList → field_148330_a:Ljava/util/List;`、`scrollTo → func_148329_a(F)V`、目标类名 identity | ✅ **不硬编码 SRG 名**，refmap 实证；未 apply 时 `instanceof` 为 false → 调用方回退反射（只降级） |
| `EntityRenderDispatcherMixin` | `RenderManager` | `func_147939_a` HEAD / RETURN | `RenderManager.java:286` `public boolean func_147939_a(Entity,double,double,double,float,float,boolean)`（`renderEntityStatic:254`/`renderEntityWithPosYaw:281`/`renderEntitySimple:249` 三者唯一汇聚点，:286） | `(Entity, double×3, float×2, boolean, CallbackInfoReturnable<Boolean>)` | identity（SRG 名本身即运行期名） | ✅ `ScopeThermalRenderer.endEntity()` 幂等（`ScopeThermalRenderer.java:333-344`），HEAD 早退不影响 RETURN 平衡 |
| `LanguageMixin` | `@Mixin(targets="net.minecraft.client.resources.Locale")` | **无注入**（上游两个 `@Inject` 本身是注释） | — | — | identity | ✅ 空 mixin，**未列入 json**（§4） |
| `SoundManagerPreparationsMixin` | `@Mixin(targets="net.minecraft.client.audio.SoundHandler")` | **无注入**（上游注入点已注释，替代钩子写在 javadoc） | — | — | identity | ✅ 空 mixin，**未列入 json**（§4） |

> 说明：上游 4 个 1.20 专属库 mixin（`mixin/client/ar/**`、`mixin/client/oculus/**`）已删除，门面由
> `compat.ar` / `compat.oculus` 承担 —— 本次 glob 复核 `mixin/**` 恰为上述 16 文件，**无残留**。

---

## 3. 三条 P0 规则复核

### 3.1 P0-1：mixin 类内禁止嵌套 / 内部 / 匿名类（`NESTING` → 要求 `JAVA_11` → 启动崩）

- 扫描 `mixin/**` 全部 16 文件的类型声明：**每个文件恰好 1 个顶层类型**，`class X {}` / `new Foo() {}` **0 处**。
- 唯一「lambda 味」的写法是 `MouseHandlerMixin.java:126` 的 `GunDisplayInstance::getIronZoom`
  —— **不捕获 `this` 的非绑定实例方法引用**，javac 直接用 `REF_invokeVirtual` 作 implMethod，**不生成合成方法**，也不产生 `NestHost/NestMembers`。
- 捕获 `this` 的 lambda 在 `LivingEntityMixin`（`() -> this.tacz$bolt.bolt()` 等）里大量存在且**已在 runClient 实测可用**：
  合成方法随 mixin 一起合入目标类，`invokedynamic` 的 lookup 变成目标类 ⇒ 正常。**结论：不改，保持现状。**
- **当前 `mixin/**` 嵌套/匿名类 = 0。**

### 3.2 P0-2：`com.tacz.guns.mixin.**` 下只能放 `@Mixin` 类

- 16/16 文件都有 `@Mixin`（含 1 个 `interface`、2 个 `abstract class`）。
- 历史上非法的 4 个类已处置且**本次复核确认**：
  - `CreativePackFilterHooks` → `src/main/java/com/tacz/guns/client/gui/components/creative/CreativePackFilterHooks.java` ✅ 已移出
  - `CreativePackFilterContainerAccess` → 同目录 ✅ 已移出
  - `RenderSystemAccessor` / `StairBlockAccessor` → **已删除**（`mixin/**` 16 文件中不存在）✅
- `mixins.tacz.json` 的 `package` 是 `com.tacz.guns.mixin`（含子包），因此**本次结论对将来新增文件同样成立**：辅助类必须放普通包。

### 3.3 P0-3：`@Inject`/`@Redirect`/`@ModifyArg` 回调签名必须与目标描述符**精确**匹配

- 见 §2 两张表的「回调签名」列：**14 个已登记 mixin 的 24 个注入点全部精确匹配**。
- 两条硬规则本次逐条验算过：
  1. **目标有返回值 ⇒ 末尾必须是 `CallbackInfoReturnable<T>`**（不是 `CallbackInfo`）：本仓 4 处
     （`GameRendererMixin` 的 `switchRenderType` / `tacz$overrideHandFov` → `CallbackInfoReturnable<Float>`；
     `AbstractButtonMixin.onClickHead` → `<Boolean>`；`EntityRenderDispatcherMixin` 两处 → `<Boolean>`）全部正确。
  2. **`CallbackInfoReturnable<T>` 的 `T` 必须是包装类型**（`F→Float`、`Z→Boolean`）：本仓 4 处全部正确。
- 形参类型**不做宽化**：`ItemInHandLayerMixin` 首参是 `AbstractClientPlayer`（不是 `EntityLivingBase`），
  与 `RenderPlayer#renderEquippedItems(AbstractClientPlayer,F)V` 逐字一致 —— 这正是历史崩溃
  `crash-2026-11-04_08.01.41` / `08.07.22` 的根因，**已修且本次复核通过**。

---

## 4. `mixins.tacz.json` 与 `@Mixin` 类集合一致性

```
required: true, minVersion 0.8, package com.tacz.guns.mixin,
compatibilityLevel JAVA_8, refmap mixins.tacz.refmap.json,
mixins: 3 (common.*)   client: 11   injectors.defaultRequire: 1
```

| 校验项 | 结果 |
| --- | --- |
| 登记的 14 条是否都是真正的 `@Mixin` | ✅ 14/14 |
| 是否存在「实际是 `@Mixin` 却没登记、但**有注入**」⇒ 会被静默跳过 | ✅ **不存在**。未登记仅 2 个：`LanguageMixin`、`SoundManagerPreparationsMixin`，二者**零注入**（上游本身即注释状态） |
| 上述 2 个留在 mixin 包是否安全 | ✅ 安全：它们**是** `@Mixin`，Mixin 对「mixin 包内的非 mixin 类」才抛 `IllegalClassLoadError`；两者当前**全仓 0 引用**，连加载都不会发生。⚠ 若将来有人写 `LanguageMixin.class` 之类引用，会触发 `IllegalClassLoadError`（§9-4） |
| `compatibilityLevel` | ✅ `JAVA_8` 与 `gradle.properties` 的 `enableModernJavaSyntax = jvmDowngrader` / `downgradeTargetVersion = 8` 一致；嵌套类规则因此仍是硬约束 |
| `refmap` 名与产物 | ✅ `build/resources/main/mixins.tacz.refmap.json`（打包用）+ `build/tmp/mixins/mixins.tacz.refmap.json`（AP tmp）同名 |
| refmap 覆盖度 | ✅ 所有**注解字符串引用**都有条目：26 个 method selector、2 个 accessor/invoker、2 个 `targets` 类名、1 个 INVOKE 目标（`ItemRenderer.renderOverlays`），以及 `GL11.glRotatef`（本应无，见 §5，现已用 `remap=false` 显式声明） |
| `defaultRequire: 1` | ⚠ 生效，**任一点失配即整份 client 配置加载失败 = 启动崩**（§9-1） |

### 4.1 `@Shadow` 字段为什么不在 refmap 里（重要，别误判为 bug）

`build/tmp/mixins/mixins.srg` 有 9 条 `FD:`，正是 4 个 mixin 的 6 个 `@Shadow` 字段：

```
CreativeModeInventoryScreenMixin/selectedTabIndex → field_147058_w
CreativeModeInventoryScreenMixin/currentScroll     → field_147067_x
HumanoidModelMixin/bipedHead|bipedBody|bipedLeftArm|bipedRightArm → field_78116_c|78115_e|78113_g|78112_f
ItemInHandRendererMixin/itemToRender              → field_78453_b
PlayerModelMixin/bipedLeftArm|bipedRightArm       → field_78113_g|78112_f
```

而 refmap 的 `mappings` / `data.searge` **不含**这些字段（只有注解里的字符串引用）。
**这是 Mixin AP 的正常分工**：`@Shadow` 是**字段声明**（会被 reobf 改名），由 AP 输出的 `mixins.srg`
交给 reobf（`build/r12build.log:70,73` 的 `Writing searge output SRGs`）；注解里的字符串引用不会被 reobf 处理，才需要 refmap。
⇒ **不是缺漏**。但**dev 的 `runClient` 用的是 MCP 名、验证不到这条链路**，因此生产包必须在构建侧确认
`mixins.srg` 已被喂给 reobf（§9-2）。

---

## 5. `build/r12build.log` 的 3 条告警逐条判定

| # | 告警 | 判定 | 处置 |
| --- | --- | --- | --- |
| 1 | `GameRendererMixin.java:308: Unable to locate method mapping for @At(INVOKE.<target>) 'Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V'` | **不是真 bug**。`GL11` 属 LWJGL2，不在 `mcp-srg.srg`（只覆盖 MC/Forge）里，其名字在 dev 与生产**完全一致** ⇒ 运行期匹配的指令就是注解字面量本身；历史上的 runClient 已实测 19 个 mixin 全部 apply 成功（含本注入）。但它确实是「注解没声明事实」的缺陷，且 `At#remap` 的官方 javadoc 明确就是为这种情况准备的 | **✅ 已修**：`@At(value="INVOKE", target="...", ordinal=2, remap=false)`，并在注释里引用 `At.java:195-210` 的依据。注入语义、命中位置**零变化** |
| 2 | `LanguageMixin.java:39: Mixin target net.minecraft.client.resources.Locale is public and should be specified in value` | **纯 cosmetic**：目标类 public 时应写 `@Mixin(Locale.class)` 而非 `@Mixin(targets=...)`。该 mixin **未列入 json ⇒ 永不 apply**，且改成 `value` 形式要在 mixin 里 import 客户端类（文档理由：避免编译期依赖） | **不改**，记录。若要清零告警：`@Mixin(Locale.class)`（`Locale` 是 public client 类，导入安全） |
| 3 | `SoundManagerPreparationsMixin.java:70: Mixin target net.minecraft.client.audio.SoundHandler is public and should be specified in value` | 同 #2 | **不改**，记录 |

> 注：3 条都是 `警告`，`build/r12build.log:78` 明确 `3 个警告`、`:89` `check` 通过 —— 不阻塞构建。

---

## 6. `compat/**` 审查（13 文件）

共同形态：**`Class.forName(..., initialize = false, loader)` 探测 + `catch (Throwable)` 静默 + 只反射不 import 对方类型**；
`Loader.isModLoaded(...)` 是唯一「硬」依赖，而 `cpw.mods.fml.common.Loader` 永远在。**零无解硬依赖。**

| 类 | 探测对象 | 缺失时行为 | 与上游语义 | 结论 |
| --- | --- | --- | --- | --- |
| `ar/ARCompat` | modid `acceleratedrendering` + `com.github.argon4w.acceleratedrendering.core.CoreFeature` | `LOADED=false`，全部查询返回中性值/空 `Runnable`，`shouldAccelerate()` 恒 false | 上游 `ARCompat`/`ARCompatImpl` 门面形状保留；1.20 专属故恒降级 | ✅ |
| `ar/BedrockPartLightingMode` | — | 枚举无使用者 | 上游原样（不依赖 AR 类型） | ✅ |
| `oculus/OculusCompat` | **`oculus` 或 `angelica`** + `net.irisshaders.iris.api.v0.IrisApi` / `net.coderbot.…` | `LOADED=false`，`isRenderShadow()`/`isUsingRenderPack()` 恒 false | Angelica 覆盖已落地（`docs/ANGELICA_COMPAT.md` P1-3 的建议修法）；`isRenderShadow` **不用** `isShaderPackInUse` 近似（否则恒真 → 枪口火光/镜片整体不画）—— 判断正确 | ✅ **本次修 1 处**：`ANGELICA_MODID` 改为引用 `CompatRegistry.ANGELICA`（`init/CompatRegistry.java:42` 已存在），删掉「CompatRegistry 里还没有这个常量」的**过期注释** |
| `oculus/IUnflushableWrapper` | — | 无实现者 ⇒ `instanceof` 恒 false | 上游同名接口保留（`tacz$getWrapped` 不漂移）；配套 mixin 已删 | ✅ |
| `optifine/OptifineCompat` | `net.optifine.Config#isShaders()`、`net.optifine.shaders.Shaders#shaderPackLoaded`、`shadersmod.client.Shaders` | 全 false | 1.7.10 **真有** OptiFine ⇒ 真探测；Angelica 强卸 OptiFine 时全 false 是正确行为（已在 javadoc 写明） | ✅ ⚠ 全仓 0 调用点（上游也是 0，属预留工具类） |
| `controllable/ControllableCompat` | modid + `com.mrcrayfish.controllable.Controllable` | 不震动 | `onGunShoot` **真的接上了**（反射 `getController().rumble(float,float,int)`），兜底参数与上游 `ControllableInner#rumbleShoot` 一致（AUTO 0.15/0.25/80ms，其余 0.25/0.5/100ms） | ✅ |
| `shouldersurfing/ShoulderSurfingCompat` | modid + `…api.model.Perspective` | `showCrosshair()` false（= 没装） | `Perspective.current()==SHOULDER_SURFING && !InputHandler.FREE_LOOK.isDown()` 逐条反射复刻 | ✅ |
| `cloth/MenuIntegration` | modid `cloth_config` + `me.shedaniel.clothconfig2.api.ConfigBuilder` | 反射构造本仓 `client/gui/compat/ClothConfigScreen` 提示页；失败返回 `parent` | 1.7.10 无 `registerExtensionPoint` ⇒ 只能 `IModGuiFactory`（TODO L2） | ✅ |
| `carryon/BlackList` | modid `carryon` | 直接 return | **真接线**：`GameData.getBlockRegistry().getKeys()`（`GameData.java:90` 返回 `FMLControlledNamespacedRegistry<Block>`）+ `FMLInterModComms.sendMessage` | ✅ |
| `jei/GunModPlugin` | modid `jei` + `mezz.jei.api.IModPlugin` | 恒 false | 1.7.10 无 JEI（等价物 NEI，API 完全不同）⇒ 13 个纯 JEI 类已删，入口名保留 | ✅ ⚠ `init()` 全仓 0 调用点（仅打日志，无功能影响） |
| `kubejs/TimelessKubeJSPlugin` | modid `kubejs` + `dev.latvian.mods.kubejs.KubeJSPlugin` | `postKubeJSEvent` 恒 true（未处理）；`registerGunType` **真转发**到 `GunItemManager` | 18 个纯 KubeJS 类已删 | ✅ ⚠ `init()` 0 调用点 |
| `playeranimator/PlayerAnimatorCompat` + `AnimationName` | modid `playeranimator` + `dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry` | 各 play/stop 空操作，`hasPlayerAnimator3rd` false | **`canPlayAnimation` 是真逻辑**（上游不依赖库），完整移植；4 个 `public static ResourceLocation *_ANIMATION` **非 final 与上游逐字一致**（`tmp/…/PlayerAnimatorCompat.java:21-24`）⇒ 本次刻意不改 | ✅ ⚠ `init()` 0 调用点 |

**接线现状（`init/**` 属别的 scope，只报告）**
- 已接：`ShoulderSurfingCompat.init` / `ControllableCompat.init` / `ARCompat.init`（`client/init/ClientSetupEvent.java:151,154,157`）；
  `MenuIntegration`（`client/input/ConfigKey.java:76,92`）；`BlackList` / `OculusCompat.initCompat`（含 Angelica）/ `MenuIntegration.registerModsPage`（`init/CompatRegistry.java:52-63`）。
- **未接（3 个，都只打一条日志，功能无影响）**：`TimelessKubeJSPlugin.init()`、`GunModPlugin.init()`、`PlayerAnimatorCompat.init()` — 全仓 0 调用点。

---

## 7. 历次 mixin 崩溃复盘（`run/client/crash-reports/`，8 份）

| 文件 | 根因 | 状态 |
| --- | --- | --- |
| `07.57.52-client` | 栈里只有 gtnhlib/mixinbooter 的上下文，**无 tacz mixin 错误** | — |
| `08.16.07-client` | 同上 | — |
| `08.01.41-client` | `ItemInHandLayerMixin->@Inject::renderArmWithItemHead` 首参宽化成 `EntityLivingBase` ⇒ `InvalidInjectionException` | **已修**（现 `(AbstractClientPlayer, float, CallbackInfo)`，§2.2） |
| `08.07.22-client` | 同上（去掉 `partialTicks` 后仍缺形参 ⇒ 第二形态） | **已修** |
| `08.12.28-client` | `GameRendererMixin->@Inject::switchRenderType` 用 `CallbackInfo`，但目标 `getFOVModifier` 有返回值 ⇒ `CallbackInfoReturnable is required!` | **已修**（现 `CallbackInfoReturnable<Float>`） |
| `13.18.00-client` | `IllegalClassLoadError: com.tacz.guns.mixin.client.CreativePackFilterHooks is in a defined mixin package` ⇒ P0 规则 2 | **已修**（已移出到 `client/gui/components/creative/`） |
| `13.28.20-client` | `NPE: this.packInfo is null @ ClientAssetsManager.getPackInfo` ← `CreativePackFilters.updateTab` ← `CreativeModeInventoryScreenMixin#initGui` | **已修**：`ClientAssetsManager.java:298` 已加 `packInfo == null` 保护，`CreativePackFilters.java:249/258` 已判空 ⇒ 本 mixin 的 `initGui` TAIL 路径不再崩（属 Lead / client-gui 作用域，本次仅复核结论） |
| `12.24.15-server` | `IllegalAccessError: tried to access class com.tacz.guns.init.ModAttributes$1`（`ASMEventHandler_7__onEntityConstructing`）| **未修，非本作用域**（`init/**`）→ §9-8 |

**P0 结论：本作用域当前不存在「会让 mixin 配置加载失败」的残留缺陷。**

---

## 8. 本次修改清单（全部在写作用域内，均 lint `0 error`）

| 文件 | 改动 | 依据 |
| --- | --- | --- |
| `src/main/java/com/tacz/guns/mixin/client/GameRendererMixin.java` | `@ModifyArg` 的 `@At` 增加 `remap = false`（+ 18 行注释说明为什么必需） | `build/r12build.log:65` 的 AP 告警；`unimixins-0.3.1-sources.jar!/org/spongepowered/asm/mixin/injection/At.java:195-210`（`remap` 的官方用途说明）；`GL11` 不在 `mcp-srg.srg` |
| `src/main/java/com/tacz/guns/compat/oculus/OculusCompat.java` | `ANGELICA_MODID` 由字面量 `"angelica"` 改为 `CompatRegistry.ANGELICA`；删除「CompatRegistry 里还没有这个常量」的过期 javadoc | `init/CompatRegistry.java:42` 已有 `public static final String ANGELICA = "angelica";` |

自查：`get_file_problems(errorsOnly=true)` 两文件 0 error；`lint_files(min_severity=error)` 覆盖 `mixin/**` 16 文件 + `compat/**` 13 文件 **0 error**。未执行任何 gradle（遵守 AGENTS.md）。

---

## 9. 未修风险 / 需裁决项

1. **P1（建议裁决）`defaultRequire = 1` + `required = true` 把「单点失配」放大成启动崩。**
   最现实的触发者是 **OptiFine 1.7.10**：它整体重写 `EntityRenderer#renderHand`，无法保证
   `Lnet/minecraft/client/renderer/ItemRenderer;renderOverlays(F)V` 这个 INVOKE 仍存在；
   同理 Hodgepodge / Mixingasm 等优化模组也可能改写 `hurtCameraEffect` / `getFOVModifier`。
   一旦失配 ⇒ **整个 client mixin 配置加载失败**（不是「该注入点静默跳过」）。
   建议（未执行，属行为策略）：对这几个「他人可能改写」的注入点改成 `@Inject(..., require = 0, expect = 1)`
   （`mixin.` 层面上「可选注入」的标准写法），保留 `defaultRequire = 1` 给真正必须生效的
   （`LocalPlayerMixin` 接口注入、`ItemInHandLayerMixin` 的 `isSelf` 置位）。
   依据：`docs/ANGELICA_COMPAT.md` §P0-3（Angelica 在 `isShaderPackInUse()` 时 `@Surround.Skip` 掉
   `renderHand` 里的 `renderItemInFirstPerson` 调用）+ OptiFine 的 `renderHand` 重写。
2. **P1（构建侧确认）`@Shadow` 字段的 remap 链路只有 reobf 一条腿。**
   `mixins.srg` 的 9 条 `FD:` 必须被 reobf 应用到 mixin 类自身的字段声明上（§4.1）。
   `runClient`（dev/MCP 名）验证不到；若生产包丢了这个文件，6 个 `@Shadow` 字段会在 apply 期报
   `@Shadow field … was not located in the target class` ⇒ 启动崩。
   **建议 Lead 在最终 `build` 后抽查 jar 内 `mixins.tacz.refmap.json` 与 reobf 产物的字段名。**
3. **P2 `ItemInHandRendererMixin#cancelEquippedProgress` 是空的 `@Inject`（`updateEquippedItem` HEAD）。**
   上游那段逻辑本身是注释状态，本注入**不改变任何行为**，却新增了一个 `require = 1` 的失败点。
   保留理由（文档价值：注入点位置留档）成立，故**未改**；若要按「最小失败面」原则，可删或加 `require = 0`。
4. **P2 两个未登记的 `@Mixin`（`LanguageMixin` / `SoundManagerPreparationsMixin`）是潜在 `IllegalClassLoadError`。**
   当前全仓 0 引用 ⇒ 不会被加载，安全。**约束**：任何人不得 `Class.forName` / `.class` 引用它们，
   否则命中 P0 规则 2（「mixin 包内的类必须是已注册 mixin」）。
5. **P2 `OculusCompat.isRenderShadow()` 行为已从「恒 false」变成真实值（Angelica 下）。**
   这是 `docs/ANGELICA_COMPAT.md` P1-3 明确要求的变化；副作用是 `MuzzleFlashRender` / `ShellRender` /
   `ScopeLensRenderer` / `ScopeThermalRenderer` / `BedrockModel` 等在 **shadow pass 里会真的早退**（= 上游 1.20 行为）。
   **需实测**（F1/F5 观感应无变化，但 shadow map 里不再有枪口火光/弹壳）。
6. **P2 3 个 compat `init()` 未接线**（§6）：`TimelessKubeJSPlugin.init` / `GunModPlugin.init` / `PlayerAnimatorCompat.init`。
   只影响「检测到对方模组时打一条日志」，无功能影响；接线点在 `client/init/ClientSetupEvent`（属 client 作用域）。
7. **P3 compat 的静态可变状态**：`OculusCompat` 的 6 个懒解析静态字段（`shadowPassResolved` / `shadowPassMethod` / `irisApiInstance` …）
   按「先置 `true` 再写结果」的写法在渲染线程首次命中，语义上依赖「渲染线程 + initCompat 早于首帧」这一单线程假设
   （与上游同样的前提）；`isRenderShadow()` 在 shadow pass 里还会被同线程重入，因此**不建议**改成惰性 `synchronized`
   （会进每帧热路径）。**不改**，仅记录约束：不要从网络/IO 线程调 `isRenderShadow()` / `isUsingRenderPack()`。
8. **报告项（非本作用域，未修）**
   - **P0** `IllegalAccessError: tried to access class com.tacz.guns.init.ModAttributes$1`
     （`crash-2026-11-04_12.24.15-server`，`ASMEventHandler_7__onEntityConstructing.invoke`）：
     `init/ModAttributes` 把**非 public 的匿名内部类**泄漏进了 public API 的签名/字节码，
     FML 在 `cpw.mods.fml.common.eventhandler` 包里生成的 ASM 事件处理器无权访问它 ⇒ 服务端玩家登入即崩。
     修法：把该匿名类提成 `public static` 具名顶层/嵌套类（`init/**` 作用域）。
   - **P3** `client/gui/components/CreativePackFilters.java:265` 的 `items.get(0).copy()`：
     若分组列表为空或首元素为 `null`（`displayItems` 允许含 `null`，见同文件 `:234`）会 `IndexOutOfBounds`/`NPE`。
     **未验证可达性**，交 client-gui 复核。
   - **P3** `docs/PORT_STATUS.md` 的 `mixin` 相关段落（如 `ANGELICA_COMPAT.md:85` 说
     `EntityRenderDispatcherMixin` 未登记）已过期 —— 实际 `mixins.tacz.json:23` 已登记。本次按约束**未改** `PORT_STATUS.md`。

---

# 第二轮：同类崩溃专项（task-14）

> 触发：用户实机 P0 `NullPointerException: Cannot invoke "net.minecraft.item.ItemStack.getItem()"
> because the return value of "java.util.function.Supplier.get()" is null`，
> 栈 `LivingEntityAim.tickAimingProgress` ← `EntityLivingBase.handler$zze000$tacz$onTickServerSide`
> ← `EntityLivingBase.onLivingUpdate`（`run/client/crash-reports/crash-2026-11-04_17.41.35-server.txt`）。
> 根因类别：**上游 1.20 的非 null 哨兵（`ItemStack.EMPTY` / 数据对象）在 1.7.10 变成 `null`，读取点未判空**。
> 本轮只找/修 **硬崩溃（NPE / CCE / AIOOBE）**；shooter 组件的 Supplier-null 修复属 gameplay-review（task-8/15），本报告只复核不抢改。

## R1. 本轮修改（1 文件 2 处，均在写作用域内；lint 0 error）

`src/main/java/com/tacz/guns/mixin/common/LivingEntityMixin.java`：

| # | 位置 | 改动 | 崩溃条件（若不修） |
| --- | --- | --- | --- |
| 1 | `getSynReloadState()` | 取到 `null` 时返回 `new ReloadState()` | 该方法是 **3 个裸解引用消费者的唯一数据源**：`LivingEntitySprint.java:32-33`（`getSynReloadState().getStateType()`，每 tick 由本类 `onTickServerSide` 触发）、`LivingEntityAim.java:139-140`（`tickSprint`，同样每 tick）、`LocalPlayerSprint.java:42`（客户端每 tick）。任一为 null ⇒ 与本次实机崩溃同一条服务端每 tick 路径上的 NPE |
| 2 | `onTickServerSide` | ① 写入 `RELOAD_STATE_KEY` 前把 `reloadState == null` 归一化为 `new ReloadState()`；② `data.reloadStateType == null` 时按「未换弹」处理 | ① 若写入 null，下一个 tick 在 `DataHolder#set`（`entity/sync/core/DataHolder.java:27` 的 `entry.getValue().equals(value)`）→ `ReloadState#equals`（`api/entity/ReloadState.java:56` 的 `stateType.equals`）以及 `ModSerializers.RELOAD_STATE#write`（`entity/sync/ModSerializers.java:24` 的 `getStateType().ordinal()`）三处 NPE；② `reloadStateType.isReloading()` 直接 NPE |

**依据**：上游 `AttachmentPropertyManager` / `SyncedEntityData` 走 Forge capability，`ReloadState` 由构造器
（`ReloadState.java:18-21`）保证 `stateType` 非 null，所有读取点按「恒非 null」写；1.7.10 改成
「静态 `DataHolder` + 网络反序列化 + player clone 搬运」后这条不变式不再由类型系统保证。
**可达性诚实说明**：当前全仓只有 `LivingEntityMixin:323` 一个 `RELOAD_STATE_KEY` 写入点，且
`LivingEntityReload.tickReloadState()` / `ModernKineticGunItem.tickReload()` 目前都恒返回非 null 的
`ReloadState`（`:134`/`:542-545`），所以这两处是**契约级加固（今天不可复现，零行为变化）**，
用来堵住本次崩溃同类问题在这条路径上的最后一个缺口；不影响任何正常路径语义。

## R2. 五类必查项的逐项结论（全部带否定证据）

| # | 检查项 | 范围 | 结论 |
| --- | --- | --- | --- |
| 1 | `@Inject` 方法体对 `thePlayer/theWorld/renderViewEntity/currentScreen/openContainer/@Shadow` 的解引用 | 16 文件 / 24 个注入点 | **无未判空解引用**。全包仅 6 处取 `Minecraft.getMinecraft()` 的可空链：`GameRendererMixin:268`（`renderViewEntity` 后紧跟 `instanceof`）、`:330`（`instanceof` 模式）、`ItemInHandLayerMixin:111`（`player != null &&`）、`ItemInHandRendererMixin:151`（`thePlayer == null` 早退）、`CreativeModeInventoryScreenMixin:320-321`（`playerController != null`）、`MouseHandlerMixin:141`（`gameSettings`，不可空）；`LivingEntityMixin:301` 的 `worldObj.isRemote` 对 ticking 实体不可空 |
| 2 | `@Shadow` 字段为 null 时被解引用 | 6 个 `@Shadow` | **无**。`ItemInHandRendererMixin.itemToRender` 确实可为 null（首帧/清手），但只被**赋值/原样返回**（`keep`/`getCurrentItem`），从不 `getItem()`；其 6 个消费者全部判空（`FirstPersonRenderEvent:71`、`CameraSetupEvent.getItemRenderer:469`、`FirstPersonRenderGunEvent:127`、`PlayerModelMixin:81` 走 `IGun.getIGunOrNull`）。`HumanoidModelMixin`/`PlayerModelMixin` 的 `ModelRenderer` 在 `ModelBiped` 构造器（`:30-`）内初始化，非 null；`CreativeModeInventoryScreenMixin` 的 `selectedTabIndex/currentScroll` 是**基本类型** |
| 3 | `LivingEntityMixin` 每 tick 路径除 shooter 外是否有其它直接解引用 | `onTickServerSide` 全 18 行 | **仅 2 处需处理（已修，见 R1）**。逐项：`data.currentGunId` 可为 null 但 `GunScriptGuard.run` 已 `@Nullable` + 提前返回（`:46-48`）；`ModSyncedEntityData.*_KEY` 是 `static final`（`ModSyncedEntityData.java:24-85`，类初始化即非 null）；其余 7 个写入值全是**基本类型**（`long/boolean/float`，装箱非 null）；7 个被调 shooter 方法已由 gameplay-review 全部改成 `getCurrentGunItem()` + 判空（`LivingEntityBolt:45/109`、`LivingEntityHeat:30`、`LivingEntityMelee:119/130`、`LivingEntityReload:139`、`LivingEntityShoot:279`、`LivingEntityDrawGun:89`、`LivingEntityCrawl:45`、`LivingEntitySpeedModifier:66-78`、`LivingEntityAim:100`），3 个 cooldown getter 空手均 `return 0` |
| 4 | `@ModifyArg/@Redirect/@Invoker/@Accessor` 回调对 null 返回值/参数解引用 | 3 `@Redirect` + 2 `@ModifyVariable` + 1 `@ModifyArg` + 2 accessor/invoker | **无**。3 个 `@Redirect` 目标都是 `void`（`setSprinting(Z)V` ×2、`processEntityAction`），回调返回 `void`，不存在「返回 null」；`@ModifyArg` 回调返回**基本类型** `float`；`@ModifyVariable` 返回 `float`；`@Accessor/@Invoker` 的唯一使用方 `CreativePackFilterHooks.createAccess:100` 用 `instanceof` + `screen == null` 双保险，mixin 未 apply 时降级为旧反射（不 CCE、不崩） |
| 5 | mixin 内的 `@SubscribeEvent` 对 event 字段解引用 | `mixin/**` | **N/A**：`grep -r "@SubscribeEvent" src/main/java/com/tacz/guns/mixin` = 0 命中，mixin 里没有任何事件处理器 |

**compat/**（13 文件）**结论：无硬崩溃，改动 0 处。** 逐类核对：
所有 `Class.forName` / `getMethod` / `invoke(null)` / `field.get(null)` 都在 `try { } catch (Throwable)` 内或前有 `!= null` 判断
（`OculusCompat.java:240-262/270-301/322-331/356-368/370-378`、`OptifineCompat.java:58-130`、
`ControllableCompat.java:70-83`、`ShoulderSurfingCompat.java:51-77`、`MenuIntegration.java:80-94`）；
反射结果 null 后使用点全部先判空（`shadowPassViaState` 返回 `@Nullable Boolean`、`irisApiBoolean` 同、
`onGunShoot` 的 `controller == null`、`showCrosshair` 的 `current instanceof Enum` / `freeLook == null`）；
缺失库路径 `LOADED=false` ⇒ 全部方法返回中性值/空操作，**不会走到任何解引用**。

## R3. 复核但不改（fidelity / 不可达，附理由）

1. **`MouseHandlerMixin:136` 的 `zoom[number % zoom.length]` 可能负索引（AIOOBE）** ——
   只有 NBT 里 `zoom_number` 被外部写成负数才触发；**上游同写法**（`tmp/TACZ-1.20.1/.../MouseHandlerMixin.java:55`），
   属上游行为，按「上游保真」不动。若日后要防，最小改法是 `Math.floorMod(...)`。
2. **`PlayerModelMixin:79` / `ItemInHandRendererMixin.getCurrentItem()` 的 `KeepingItemRenderer.getRenderer()` 可为 null（理论上 NPE）** ——
   `getRenderer()` = `(KeepingItemRenderer) mc.entityRenderer.itemRenderer`：`entityRenderer` 在 `Minecraft.startGame`
   创建一次，`itemRenderer` 在其构造器内 `new ItemRenderer(mc)`，而 `ModelBiped.setRotationAngles` 只可能发生在渲染阶段 ⇒ **不可达**；
   全仓同一调用共 6 处（`CameraSetupEvent` ×4、`ItemInHandRendererMixin`、`PlayerModelMixin`）也都未判空，单独加一处反而是不一致，故不改。

## R4. 报告项（非本作用域，未修）

1. **P2（entity/sync，非崩溃但同类）** `ModSerializers.java:32` `StateType.values()[buf.readInt()]` ——
   网络/存档里序号越界会 **AIOOBE**（不是 null，属同类「无校验的裸索引」）。投递包损坏/跨版本 desync 时才可能触发。
2. **P2（init 作用域）** `CompatRegistry.enqueueCompat():56` 无条件 `invokeOptional("com.tacz.guns.client.gui.compat.ClothConfigScreen", …)`：
   专用服务器上 `GuiScreen` 被 `SideTransformer` 剥离 ⇒ 该 `Class.forName` 抛 `NoClassDefFoundError`，
   被 `invokeOptional` 的 `catch (Throwable)` 吞掉 ⇒ **不崩，但每次开服多一条 WARN**。建议加
   `FMLCommonHandler.instance().getSide().isClient()` 闸门（与 `checkModLoad` 同级）。
3. **P3（client/gui，沿用第一轮）** `CreativePackFilters.java:265` `items.get(0).copy()`：分组列表为空或首元素 null 时
   `AIOOBE`/`NPE`（同文件 `:234` 自己承认 `displayItems` 可能含 null）——仍是本 mixin `initGui` TAIL 回调路径上的
   潜在崩溃点（上一次 `crash-2026-11-04_13.28.20-client` 就是这条路径的 `packInfo` NPE）。
4. **已关闭**：第一轮报告的 P0 `ModAttributes$1 IllegalAccessError` 已由 init 侧修好
   （现为 `init/ModAttributes.java:52` 的 `public static final class Handler`，javadoc 记录了该崩溃）。
