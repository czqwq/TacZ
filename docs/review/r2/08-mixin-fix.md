# r3-01 · mixin 域修复

> **写作用域**（严格限定）：`src/main/java/com/tacz/guns/mixin/**`、
> `src/main/resources/mixins.tacz.json`、`src/main/java/com/tacz/guns/init/CommonRegistry.java`、
> 本文件 `docs/review/r2/08-mixin-fix.md`。
>
> **未运行 gradle 声明**：本轮**全程没有执行** `gradlew` / `gradle` 的任何任务（含 `compileJava`）。
> 全部自检手段为：`mcp__idea__get_file_problems`（逐文件 0 error）、`mcp__idea__read_file`
> 读 1.7.10 反编译源码核对方法名/描述符、`grep` 证明调用链、以及把 UniMixins 0.3.1 的
> Mixin 源码（`unimixins-0.3.1-sources.jar`）读出来核对注入器语义。**构建由 Lead 统一执行。**
>
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）
> 1.7.10 准绳：`build/rfg/minecraft-src/java/**`（原版 + Forge 反编译源码）

---

## 结论速览

| # | 条目 | 级别 | 状态 | 落点 |
|---|---|---|---|---|
| 1 | 枪械击退数据完全不生效（`KnockbackChange.modifyStrength` 0 调用者） | P1 | **已修**（新增 common mixin） | `mixin/common/LivingEntityKnockbackMixin.java` |
| 2 | 持枪左键点方块 → 客户端幽灵方块 / 不同步 | P1 | **已修**（新增 client mixin） | `mixin/client/MinecraftAttackMixin.java` |
| 3 | `CommonRegistry#register()` 里 `register(Class)` 是死代码 | P2 | **已修**（删除 + 留警示注释） | `init/CommonRegistry.java:160-189` |
| — | `mixins.tacz.json` | — | **3+11=14 → 4+12=16**（新增 2 条），与 `@Mixin` 类集合一致 | `mixins.tacz.json:7-26` |

**未修**（不在本作用域或不在本轮任务内）：4 条，见 §四。其中 **1 条需要别人改一行注释**
（`event/KnockbackChange.java:22-24` 的 TODO 已过期，现在会误导人）。

---

## 一、修复清单

### 1. P1 · 枪械击退数据完全不生效 → 新增 `LivingEntityKnockbackMixin`

#### 问题（r2 U1 原文的证据链，本轮复核确认仍在）

`entity/EntityKineticBullet.java:510-517`：

```java
if (parts.getCore() instanceof EntityLivingBase livingCore) {
    KnockBackModifier modifier = KnockBackModifier.fromLivingEntity(livingCore);
    modifier.setKnockBackStrength(this.knockback);   // 写进 ShooterDataHolder.knockbackStrength
    tacAttackEntity(parts, damage, sources);
    modifier.resetKnockBackStrength();               // 写回 -1
}
```

这个值**没有任何消费端**：

- `event/KnockbackChange.java:42-46` 的 `modifyStrength(EntityLivingBase, float)` 在修复前
  全仓 **0 个调用者**（只有它自己和 javadoc 里提到）；
- `event/KnockbackChange.java:31-33` 的 `register()` 是空实现（1.7.10 没有 `LivingKnockBackEvent`），
  由 `event/EventHandlers.java:55` 调用，等于什么都没做；
- 于是 1.7.10 的 `EntityLivingBase#attackEntityFrom` 内部按原版算击退，
  `BulletData.knockback` / `GunProperties.KNOCKBACK` / 配件击退**全部被丢弃**。

#### 上游 file:line

| 内容 | 上游位置 |
|---|---|
| 监听 `LivingKnockBackEvent`，`strength >= 0` 时 `event.setStrength((float) strength)` | `event/KnockbackChange.java:10-17` |
| 子弹命中时 set → attack → reset 的三段式 | `entity/EntityKineticBullet.java:446-459`（`setKnockBackStrength`:451、`resetKnockBackStrength`:455） |
| 该事件的派发点在 1.20.1 的 `LivingEntity#knockback(double strength, double x, double z)` 内部，`strength` 就是击退强度本身（原版 `hurt` 传 `0.4`） | — |

#### 1.7.10 目标方法 file:line（**逐字核对过 `build/rfg/minecraft-src`，与任务描述的假设不同，务必看**）

任务描述假设「击退强度在 `attackEntityFrom` 内的 `knockbackResistance` 计算与 `addVelocity` 调用」。
**实测不是**：

| 事实 | 证据 |
|---|---|
| `attackEntityFrom` 只**转调**，不算强度 | `build/rfg/minecraft-src/java/net/minecraft/entity/EntityLivingBase.java:820` 起，`:921` `this.knockBack(entity, amount, d1, d0);`（`entity = source.getEntity()`，即射手） |
| `attackEntityFrom` 里**没有** `addVelocity` | 1.7.10 的击退是直接写 `motionX/motionY/motionZ` 三个字段 |
| **强度的真实落点是 `knockBack(Entity, float, double, double)`** | `EntityLivingBase.java:1050-1069` |
| 原版击退阻力判定 | `EntityLivingBase.java:1052` `if (this.rand.nextDouble() >= ...knockbackResistance...)` |
| **强度常量本体 `float f2 = 0.4F;`** | **`EntityLivingBase.java:1056`** |
| 强度被乘进速度 | `EntityLivingBase.java:1060-1062`（`motionX -= d1/f1*f2`、`motionY += f2`、`motionZ -= d0/f1*f2`） |
| 竖直方向夹取上界 | `EntityLivingBase.java:1064-1067`（`0.4000000059604645D`，**double**，不会与 `0.4F` 混淆） |
| `knockBack` 无子类重写 | `grep "void knockBack(" build/rfg/minecraft-src/java` → 仅 1 处命中（`:1050`） |

因此注入点定在 **`EntityLivingBase#knockBack`** —— 它就是 1.20.1
`LivingEntity#knockback(double,double,double)` 的 1.7.10 同名同位对应物，
**覆盖面与上游 `LivingKnockBackEvent` 完全一致**（所有活体 × 所有伤害源）。

#### 注入点选择理由

| 候选 | 判定 | 理由 |
|---|---|---|
| `@Redirect` 三次 `motionX/Y/Z` 写入 | ❌ | 没有 INVOKE 可 Redirect，是三次内联 `putfield`（要写 3 处、还要硬编码字段描述符） |
| `@Redirect` `attackEntityFrom` 里的 `knockBack(...)` 调用 | ❌ | 只能换调用目标，换不了 `knockBack` 内部那个 `0.4F`；且漏洞：不进 `knockBack` 的路径管不到 |
| `@ModifyVariable`（`at = @At("STORE")`, `index = 8` / `ordinal = 1`） | △ | 任务首选，但需要假设 javac 把 `f1`/`f2` 排在槽 7/8（`this`=0、`Entity`=1、`float`=2、`double`×2=3-6、`f1`=7、`f2`=8）。**槽位/序号都会随目标类编译产物漂移**，是本项目「宁可失效不可崩」原则下最不该赌的一环 |
| **`@ModifyConstant(floatValue = 0.4F)`** ✅ | **采用** | **按常量值定位**：`knockBack` 里唯一的 `float` 常量就是 `0.4F`（夹取上界是 `double`，类型不同，不会误命中）。不需要描述符、不需要槽位、不需要序号，是该方法里最稳的定位方式；语义上「改的就是强度」，与上游 `setStrength` 同构。已核 UniMixins 0.3.1 的 `ModifyConstantInjector#injectConstantModifier`（`Bytecode.getConstantType` → `Type.FLOAT`）与 `ModifyConstantInjectionInfo#parseInjectionPoints`（`atKey = "constant"`），float 常量走 `LDC 0.4f` 被正常识别 |

> 说明：`@ModifyConstant` 与任务给的「优先 `@ModifyVariable`」在本条上**目标一致**——
> 都是「不写 INVOKE 描述符、不 Redirect」的低脆性方案，只是定位维度从「槽位」换成「常量值」，更稳。

#### `require` 取舍

`require = 0` + `expect = 1`：

- `knockBack` 是「OptiFine / 各类战斗与击退调整 mod」最可能改写的方法之一。一旦 `0.4F` 被换成
  `0.4000000059604645`（double）或整段重写，注入就 0 命中。若沿用 json 的
  `injectors.defaultRequire = 1`（`mixins.tacz.json:27-29`），会直接
  `InjectionError` → **启动崩**。宁可「击退数据不生效」也不能起不来。
- **`expect` 的真实语义（本轮读源码纠正，很重要）**：已核 UniMixins 0.3.1 的
  `org/spongepowered/asm/mixin/injection/struct/InjectionInfo.java:524-538`：

  ```java
  if (this.mixin.getOption(Option.DEBUG_INJECTORS) && this.injectedCallbackCount < this.expectedCallbackCount) {
      throw new InvalidInjectionException(... "expected %d invocation(s) but %d succeeded" ...);
  } else if (this.injectedCallbackCount < this.requiredCallbackCount) {
      throw new InjectionError(...);
  }
  ```

  即：`require` 不满足 → **硬崩**；`expect` 不满足 → **仅当 `-Dmixin.debug.injectors=true`
  时才抛异常，默认完全静默**。所以「`expect = 1` 会在 0 命中时打日志」是**错的** ——
  **默认配置下注入点没命中什么都不会说**，这正是 §三 必须做实机确认的根本原因。
  `expect = 1` 仍然保留：① 一旦有人开了调试开关，它立刻把「静默失效」变成可见的
  `InvalidInjectionException`；② 自解释「本注入点应当恰好命中 1 次」。

#### 修法

新增 `src/main/java/com/tacz/guns/mixin/common/LivingEntityKnockbackMixin.java`：

```java
@Mixin(EntityLivingBase.class)
public class LivingEntityKnockbackMixin {
    @ModifyConstant(method = "knockBack", constant = @Constant(floatValue = 0.4F), require = 0, expect = 1)
    private float tacz$modifyKnockbackStrength(float originalStrength) {
        return KnockbackChange.modifyStrength((EntityLivingBase) (Object) this, originalStrength);
    }
}
```

**未改动**（按要求）：`event/KnockbackChange.java`、`api/entity/KnockBackModifier.java`、
`mixin/common/LivingEntityMixin.java`（它已经 `implements ... KnockBackModifier`，
`resetKnockBackStrength`/`getKnockBackStrength`/`setKnockBackStrength` 三方法在
`LivingEntityMixin.java:340-353`，默认值 `-1` 见 `entity/shooter/ShooterDataHolder.java:118-121`）。

**为什么不会误伤非枪械伤害**：`modifyStrength` 只在 `getKnockBackStrength() >= 0` 时覆盖，默认是
**`-1`**（`ShooterDataHolder.java:121` 注释「负数表示使用原版击退」），`resetKnockBackStrength()`
也写回 `-1`。所以非子弹伤害下返回值与原版 `0.4F` **逐位相同**。

**验收（`grep` 证据）**：`KnockbackChange.modifyStrength` 现在有真实调用者 ——
`mixin/common/LivingEntityKnockbackMixin.java:97`（唯一生产调用点；`event/KnockbackChange.java:18,24`
两处只是 javadoc 提及）。

**行为变化预告（会在实机被看到，不是 bug）**：默认枪包里绝大多数枪的 `bullet.knockback` 是 `0`
（如 `ak47_data.json:85`、`kar98_data.json:28`、`m4a1_data.json:47`），接入后这些枪打人**不再有击退**；
而 `taurus500_data.json:29` = `0.5`、`db_short_data.json:23` = `0.875` 会比原版 `0.4` 更猛。
这与上游 1.20 完全一致（上游同样 `event.setStrength(0)` → 无击退）。详见 §三 第 1 条。

---

### 2. P1 · 持枪左键点方块 → 客户端幽灵方块 → 新增 `MinecraftAttackMixin`

#### 问题（r2 U2 原文的证据链，本轮复核确认仍在）

r2 修好「TACZ 键位顶掉原版攻击键」（P0-1）之后，`Minecraft#func_147116_af` / `func_147115_a`
重新正常执行，于是浮出一条**既有的平台差异**：

1. 客户端本地预测挖掘：`Minecraft#func_147116_af`（`Minecraft.java:1455-1496`）→
   `PlayerControllerMP#clickBlock`（`:186-229`）→ 创造模式 `clickBlockCreative`（`:73-79`）→
   `onPlayerDestroyBlock` → **本地 `setBlockToAir`**；持续挖掘走 `Minecraft#func_147115_a`
   （`:1422-1453`）→ `PlayerControllerMP#onPlayerDamageBlock`（`:249-301`），
   本地累计 `curBlockDamageMP`（`:275`）到 1.0 就自行破坏（`:284-292`）。
2. 服务端**不认**：`PlayerInteractEvent.Action.LEFT_CLICK_BLOCK` 在全仓唯一的派发点是
   `build/rfg/minecraft-src/java/net/minecraft/server/management/ItemInWorldManager.java:157`
   （`onBlockClicked`，服务端），它被 `event/PreventGunClick.java:30-40` 取消后只回一个
   `S23PacketBlockChange`（`:160`）「事后纠错」。
3. 结果：方块在客户端消失 / 进度条走完，服务端还在 → **幽灵方块 + 不同步**（创造模式最明显）。

#### 上游 file:line

| 内容 | 上游位置 |
|---|---|
| `InputEvent.InteractionKeyMappingTriggered` 里持枪即 `setSwingHand(false)` + `setCanceled(true)` | `client/event/ClientPreventGunClick.java:21-41` |
| 取消的是**输入映射**，所以上游客户端根本不会进入 `startAttack` / `continueDestroyBlock` | 同上 |
| 上游放行分支：`InteractKey` 按下时放行；展示框（`ItemFrame`）放行 | `:27-29`、`:34-37` |

#### 1.7.10 目标方法 file:line（逐字核对）

| 方法 | 位置 | 签名 | 回调信息类型 |
|---|---|---|---|
| 「开始挖掘 / 攻击」 | `Minecraft.java:1455-1496`（调用点 `:2037-2040`） | `private void func_147116_af()` | `CallbackInfo` |
| 「持续挖掘」 | `Minecraft.java:1422-1453`（调用点 `:2058`） | `private void func_147115_a(boolean leftClick)` | `CallbackInfo` |

配套事实：`PlayerControllerMP#resetBlockRemoving` = `PlayerControllerMP.java:234-244`（会补发
`C07PacketPlayerDigging(1, ...)` 中止包）；`sameToolAndBlock` = `:329-340`（换物品不主动中止挖掘，
只在下次调用时比对）；`thePlayer` 是 `public EntityClientPlayerMP`（`Minecraft.java:205`）、
`playerController` 是 `public PlayerControllerMP`（`:193`），都可直接字段访问，**不需要 `@Shadow`**。

#### 注入点选择理由

| 候选 | 判定 | 理由 |
|---|---|---|
| `@Mixin(PlayerControllerMP)` + `clickBlock` / `onPlayerDamageBlock` HEAD | △ 可行但**不采用** | 能止住 desync，但 `Minecraft#func_147115_a` 在 `onPlayerDamageBlock` **之后**还会无条件执行 `effectRenderer.addBlockHitEffects(...)` + `thePlayer.swingItem()`（`Minecraft.java:1441-1445`）→ 持枪挖方块会一直冒方块碎屑粒子并挥手。这两个副作用正是上游 `setSwingHand(false)` 要抑制的 |
| `@Mixin(Minecraft)` + `func_147116_af` / `func_147115_a` HEAD | ✅ **采用** | 打在「客户端挖掘的唯一两个入口」上，连挥手 + 碎屑一起挡掉，语义等价上游「客户端根本不会开始挖掘」（上游取消的是输入映射，比 `PlayerControllerMP` 更上游，与打到 `Minecraft` 同一层）。两个方法的调用点各只有 1 处，覆盖面精确 |

**为什么不碰右键**：右键侧**已有**等价拦截 —— `Minecraft#func_147121_ag` 在调 `onPlayerRightClick` /
`sendUseItem` 之前会 post `PlayerInteractEvent`（`Minecraft.java:1528`、`:1556`，客户端也 post），
已被 `client/event/ClientPreventGunClick.java:61-82` 取消。本 Mixin 只管左键，避免重复/越界。

#### 方法名与 `remap` 的取舍（任务要求写清）

**`func_147116_af` / `func_147115_a` 不是 MCP 友好名 —— 它们就是 SRG 名本身。**
MCP stable 12 没有给这两个私有方法起名字，`build/rfg/minecraft-src` 里只能看到 `func_` 形式；
1.7.10 的 Forge 运行期是 SRG 反混淆状态（崩溃栈里都是 `func_` 名）。

**选择：SRG 名原文 + `remap = false`。**

- `remap = false` 让 Mixin 注解处理器**完全跳过**这个选择器的混淆表查找 —— 既不会报
  `Unable to locate method mapping`（该报错的实证形式见 `mixin/client/GameRendererMixin.java:309-322`
  对 `GL11` 的说明），也保证运行期拿到的就是同一个名字（SRG 名在 dev 与生产两个环境都不变）。
- 若用默认 `remap = true`：MCP→SRG 表里查不到这两个名字，结果虽然同样是「原样使用」，
  但会多一条 AP 警告，且语义靠「查不到」这个副作用成立，不如显式 `false` 清楚。
- 全仓已有的同类先例：`mixin/client/EntityRenderDispatcherMixin.java:75,92` 直接写 SRG 名
  `func_147939_a`（r5 `runClient` 实测「mixin 19 个全部 apply 成功」，
  见 `docs/PORT_STATUS.md:1997-2004`），证明 SRG 名在本工程运行期是有效目标。
- 项目自身对 SRG 名的书写规则也一致：`docs/PORT_STATUS.md:2356-2364`
  「`field_150149_b` 是 SRG 名…**必须 `remap = false`**」。

#### `require` 取舍

`require = 0` + `expect = 1`，理由同 §一.1：`Minecraft#func_147115_a` / `func_147116_af` 是
「OptiFine、各种挖掘/连点优化 mod」会改写的目标，方法名/签名被换掉就 0 命中；
`defaultRequire = 1` 会让它变成**启动崩**。`expect = 1` 仅在调试开关 `-Dmixin.debug.injectors=true`
下才把 0 命中变成硬失败（`InjectionInfo.java:524-538`），默认静默 —— 见 §一.1 的源码引用。
**因此构建后必须用 §三 的行为用例确认它真的命中，不能只看"启动没崩"。**

#### 修法

新增 `src/main/java/com/tacz/guns/mixin/client/MinecraftAttackMixin.java`：

```java
@Mixin(Minecraft.class)
public class MinecraftAttackMixin {
    @Inject(method = "func_147116_af", at = @At("HEAD"), cancellable = true, require = 0, expect = 1, remap = false)
    private void tacz$preventGunLeftClick(CallbackInfo ci) {
        if (tacz$mainHandHoldGun()) { ci.cancel(); }
    }

    @Inject(method = "func_147115_a", at = @At("HEAD"), cancellable = true, require = 0, expect = 1, remap = false)
    private void tacz$preventGunBlockDamage(boolean leftClick, CallbackInfo ci) {
        if (!leftClick || !tacz$mainHandHoldGun()) { return; }
        PlayerControllerMP controller = ((Minecraft) (Object) this).playerController;
        if (controller != null) { controller.resetBlockRemoving(); }   // 见下
        ci.cancel();
    }

    @Unique private boolean tacz$mainHandHoldGun() { ... IGun.mainHandHoldGun(thePlayer) ... }
}
```

四个实现细节：

1. **`leftClick == false` 必须放行**：`func_147115_a(false)` 是原版「没在挖」的收尾
   （`leftClickCounter = 0` + `playerController.resetBlockRemoving()`，`Minecraft.java:1424-1427`、
   `:1448-1451`），拦掉会让「松手后清除挖掘进度」也失效。
2. **cancel 前补一次 `resetBlockRemoving()`**：`func_147116_af` 只在**按下沿**触发，而「挖到一半时
   把工具切成枪」在 1.7.10 不会自动中止挖掘（`PlayerControllerMP#sameToolAndBlock` 只在下次调用时
   才比对物品，`:329-340`）。若此时直接 return，服务端 `ItemInWorldManager.updateBlockRemoving`
   会继续自行把方块挖掉 → 变成「服务端没了、客户端还在」的**反向**不同步。
   `resetBlockRemoving()` 会补发 `C07PacketPlayerDigging(1 /*ABORT*/)` 并清本地进度（`:234-244`），
   把这条路径闭合。
3. **不加 `InteractKey` 放行分支，也不特判展示框**（与上游的**有意差异**，已核对）：
   - 上游 `ClientPreventGunClick.java:27-29` 在交互键按下时放行；但本工程**服务端**
     `event/PreventGunClick.java:30-40` **没有**这个放行分支，仍然会取消 `LEFT_CLICK_BLOCK`。
     客户端若照抄放行，就会把本次要修的幽灵方块**重新造出来**。客户端必须与服务端一致 → 不放行。
   - 上游放行展示框（`:34-37`）是为了让「攻击映射」能点展示框；1.7.10 的展示框交互走**右键**
     （`client/event/ClientPreventGunClick.java:73-77` 已特判），左键对展示框没有合法语义 → 无需放行。
4. **附带的行为变化（预期，与上游一致）**：`func_147116_af` 同时负责「左键攻击实体」，
   所以持枪时左键**不再**对生物造成近战伤害 —— 上游取消攻击映射后同样是「持枪左键只开火」，
   近战走 `MeleeKey`（`client/input/GunInputHandler.java:92` 的 press binding）。
「**不改 `client/input/**`**」的纪律也已遵守：本 Mixin 不 import `client/input` 下的任何类
（近战/开火/交互键的轮询仍全部在 `client/input` 内，本 Mixin 只读 `IGun.mainHandHoldGun`）。

---

### 3. P2 · `init/CommonRegistry` 的死代码

#### 问题

`init/CommonRegistry.java`（修复前 `:165-167`）：

```java
public static void register() {
    MinecraftForge.EVENT_BUS.register(CommonRegistry.class);   // 死代码
}
```

1.7.10 的 `EventBus#register(Object target)` 用 `target.getClass()` 反射扫处理器
（`build/rfg/minecraft-src/java/cpw/mods/fml/common/eventhandler/EventBus.java:45`、
`:59-60` `TypeToken.of(target.getClass())` / `target.getClass().getMethods()`）。
传 `Class` 对象时扫的是 `java.lang.Class` 自己的方法 → **一个 `@SubscribeEvent` 都注册不到**。
这正是本工程写死的铁律（`client/init/ClientSetupEvent.java:99-103`、
`client/event/ClientPreventGunClick` 类注释、README/`docs/PORT_STATUS.md:2319-2320`）。

当前功能未受影响：`BULLET_RESISTANCE` 的挂载由 `init/ModAttributes.java:40-42`
（`register(new Handler())`）与 `:52-64`（实例处理器，幂等）承担，而 `CommonProxy.java:95`
（`ModAttributes.register()`）早于 `:104`（`CommonRegistry.register()`）。

#### 修法

- 删除 `MinecraftForge.EVENT_BUS.register(CommonRegistry.class)`，`register()` 变成显式 no-op，
  并把「**永远不要** `register(Class)`」+ 为什么 `onEntityConstructing` 不需要再注册
  写进 `register()` 的 javadoc（`init/CommonRegistry.java:160-189`）；
- 顺带删掉已不再使用的 `import net.minecraftforge.common.MinecraftForge;`；
- 同步修正类 javadoc 里「1.7.10 必须显式 `register(...)`，因此本类提供 `register()`」的过期表述
  （`init/CommonRegistry.java:52-57`）。
- **保留** `onEntityConstructing(EntityEvent.EntityConstructing)` 静态方法本身不动
  （它现在是「与上游 `EntityAttributeModificationEvent` 对应的语义说明 + 幂等备份」，
  删掉会丢掉与 `ModAttributes.Handler` 的对照关系；任务只要求删死代码那一行）。
- `get_file_problems` = **0 error**。

---

## 二、`mixins.tacz.json` 与 `@Mixin` 类集合一致性核对

**最终条目数：`mixins`（common）4 条 + `client` 12 条 = 16 条**（修复前 3 + 11 = 14）。

| # | json 数组 | 条目 | 文件 | `@Mixin` 目标 | 本轮 |
|---|---|---|---|---|---|
| 1 | mixins | `common.LivingEntityMixin` | `mixin/common/LivingEntityMixin.java` | `EntityLivingBase` | 未改 |
| 2 | mixins | **`common.LivingEntityKnockbackMixin`** | `mixin/common/LivingEntityKnockbackMixin.java` | `EntityLivingBase` | **新增** |
| 3 | mixins | `common.ServerPlayerMixin` | `mixin/common/ServerPlayerMixin.java` | `EntityPlayerMP` | 未改 |
| 4 | mixins | `common.ServerGamePacketListenerImplMixin` | `mixin/common/ServerGamePacketListenerImplMixin.java` | `NetHandlerPlayServer` | 未改 |
| 5 | client | `client.LocalPlayerMixin` | `mixin/client/LocalPlayerMixin.java` | `EntityPlayerSP` | 未改 |
| 6 | client | `client.MouseHandlerMixin` | `mixin/client/MouseHandlerMixin.java` | `Entity` | 未改 |
| 7 | client | **`client.MinecraftAttackMixin`** | `mixin/client/MinecraftAttackMixin.java` | `Minecraft` | **新增** |
| 8 | client | `client.ItemInHandRendererMixin` | `mixin/client/ItemInHandRendererMixin.java` | `ItemRenderer` | 未改 |
| 9 | client | `client.ItemInHandLayerMixin` | `mixin/client/ItemInHandLayerMixin.java` | `RenderPlayer` | 未改 |
| 10 | client | `client.GameRendererMixin` | `mixin/client/GameRendererMixin.java` | `EntityRenderer` | 未改 |
| 11 | client | `client.HumanoidModelMixin` | `mixin/client/HumanoidModelMixin.java` | `ModelBiped` | 未改 |
| 12 | client | `client.PlayerModelMixin` | `mixin/client/PlayerModelMixin.java` | `ModelBiped` | 未改 |
| 13 | client | `client.CreativeModeInventoryScreenMixin` | `mixin/client/CreativeModeInventoryScreenMixin.java` | `GuiContainerCreative` | 未改 |
| 14 | client | `client.AbstractButtonMixin` | `mixin/client/AbstractButtonMixin.java` | `GuiButton` | 未改 |
| 15 | client | `client.CreativeContainerAccessor` | `mixin/client/CreativeContainerAccessor.java` | `ContainerCreative` | 未改 |
| 16 | client | `client.EntityRenderDispatcherMixin` | `mixin/client/EntityRenderDispatcherMixin.java` | `RenderManager` | 未改 |

**一致性核对（脚本化，非目测）**：把 json 两个数组拼起来 vs
`grep '^@Mixin' -l` 扫出的 `src/main/java/com/tacz/guns/mixin/**/*.java`（共 **18** 个），
差集**恰好只有 2 个**，都是**有意不登记**的既有项（`docs/PORT_STATUS.md:2366-2376` 已登记）：

| 未登记但仍是合法 `@Mixin` 的类 | 原因 |
|---|---|
| `client.LanguageMixin` | 上游本身就是整段注释掉的死代码；1.7.10 走 `LanguageRegistry` 注入 |
| `client.SoundManagerPreparationsMixin` | 上游空实现（死代码）；留在包里无害（`@Mixin` 不会被 Mixin 拦截） |

其它两条硬约束复查（`mixin/**` 全 18 文件）：

| 检查项 | 结果 |
|---|---|
| `@Mixin` 类里嵌套/内部/匿名类（`NESTING` 启动崩） | **0**（两个新文件均无 `class X {}` / `new Foo() {}` / 捕获 `this` 的 lambda；`MinecraftAttackMixin` 的 `tacz$mainHandHoldGun()` 是 `@Unique` private 方法，不是嵌套类） |
| `com.tacz.guns.mixin.**` 下非 `@Mixin` 的类（`IllegalClassLoadError`） | **0** |
| json 里列出的条目在文件系统上不存在 | **0** |
| json 语法 | 合法（`ConvertFrom-Json` 通过；`compatibilityLevel` 仍为 `JAVA_8`，未抬高） |

---

## 三、构建后**必须实机确认**的 3 件事（mixin 注入点无法静态证明命中）

> 两个新 mixin 都用 `require = 0`，**注入点不命中时是 100% 静默失效**
> （`expect` 默认不报任何东西，只在 `-Dmixin.debug.injectors=true` 时才抛异常 —— 源码依据见 §一.1）。
> 下面 3 条都是「**只有注入真的命中才会出现**」的可见行为，实测不完就不能算修完。
> 强烈建议顺手做一次 `-Dmixin.debug.injectors=true` 的启动（把静默失效变成启动期硬失败）。

### 1. 击退链命中（`LivingEntityKnockbackMixin` → `EntityLivingBase#knockBack` 的 `0.4F`）

用**两把对照枪**打同一个生物（僵尸/牛，观察位移方向与距离）：

| 用例 | 数据依据 | 期望（命中） | 若**未**命中会看到 |
|---|---|---|---|
| **AK47**（`bullet.knockback = 0`，`ak47_data.json:85`；`fire_mode_adjust` 整段被注释，`:229-269`） | `GunProperties.KNOCKBACK` 缓存 = 0 | 目标**几乎没有位移**（`modifyStrength` 返回 0 → `f2 = 0`，`motionX/Y/Z` 三项都乘 0） | 目标仍被推 ~0.4 格的**原版**击退 |
| **双管霰弹 db_short**（`bullet.knockback = 0.875`，`db_short_data.json:23`） | 缓存 = 0.875 | 目标被推得**明显比原版远**（>2× 原版距离） | 与空手/原版伤害的击退**完全一样** |

> 数值链路已核对：`resource/modifier/custom/KnockbackModifier#initCache`（该文件 `:49-61`）
> 的 base = `bulletData.getKnockback()` + `fireModeAdjustData.getKnockback()`，
> 再经附件 `Modifier` 求值（`eval`，`:63-67`）→ `EntityKineticBullet.java:191-197` 取
> `GunProperties.KNOCKBACK` 缓存并 `Math.max(knockback, 0f)` → `:512` 写入。
>
> 排查线索：同一次实机测试**再单独启动一次**，加 JVM 参数 `-Dmixin.debug.injectors=true`
> （RFG `runClient` 需要临时在 `build.gradle.kts` 里加 `runClient { jvmArgs '-Dmixin.debug.injectors=true' }`，
> 属 Lead 的构建文件作用域）。该开关打开后，若本注入点 0 命中会直接抛
> `InvalidInjectionException: ... expected 1 invocation(s) but 0 succeeded`（见 §一.1 的源码引用），
> 这是**唯一**能静态/启动期证明注入命中的手段；不开调试时 0 命中完全静默。

### 2. 幽灵方块链命中（`MinecraftAttackMixin` → `Minecraft#func_147116_af` / `func_147115_a`）

| 用例 | 期望（命中） | 若**未**命中会看到 |
|---|---|---|
| **创造模式**持枪对着石头左键**按住 1 秒**（不要按 O） | 方块**完全不动**：无破坏进度、无方块碎屑粒子、手臂**不挥**；方块不会在客户端消失 | 方块在客户端**瞬间消失**（`setBlockToAir`），随后被服务端 `S23PacketBlockChange` 打回来 → 闪烁/幽灵方块 |
| **生存模式**持枪对石头按住左键 2 秒 | 无破坏进度条、无碎屑、无挥手；松手后手上物品仍是枪 | 进度条走到头、方块被破坏、出现幽灵方块 |
| **持枪左键仍能开火**（同一次按住） | 子弹正常打出（开火走 `ShootKey` 每 tick 轮询 `GameSettings#isKeyDown`，与本 Mixin 无关） | 若不射击 → 说明误伤了别的链路，需查 `client/input` |
| **挖到一半切枪**（先空手挖石头到 ~50%，滚轮切到枪，继续按住） | 破坏进度立即中止、服务端不继续挖（`resetBlockRemoving()` 的 ABORT 包生效）；不发散 | 服务端把方块挖掉而客户端还在（反向不同步） |

> 排查线索：同上，开 `-Dmixin.debug.injectors=true` 再启动一次；本 Mixin 的两处 `@Inject`
> 各自 `expect = 1`，任一 0 命中都会抛 `expected 1 invocation(s) but 0 succeeded`
> （两条是两个独立 InjectionInfo，要分别确认都过了）。

### 3. 无回归 + 两条新 mixin 都成功 apply

| 用例 | 期望 |
|---|---|
| 启动日志 | 出现两条新 mixin 的 apply 记录，**没有** `InvalidInjectionException` / `Invalid descriptor` / `Unsupported mixin`。`@Mixin` 类总数 **16 → 18**（json 条目 14 → 16），关键是**没有任何 apply 失败**（r2 基线「19 个全部 apply 成功」见 `docs/PORT_STATUS.md:1997-2004`，本轮应在该数字基础上 +2） |
| **空手 / 持普通物品**：左键挖方块、左键打怪、右键开箱/进食/放方块 | 全部与 r2 基线**完全一致**（本 Mixin 只在 `IGun.mainHandHoldGun(thePlayer)` 为真时 cancel） |
| **持枪右键**方块（箱子/展示格/弹药箱）、展示框 | 与 r2 基线一致（右键侧归 `client/event/ClientPreventGunClick`，本 Mixin 不碰） |
| **持枪左键打生物** | **预期变化**：不再造成近战伤害（只有开火）。近战走 `MeleeKey`（默认 V）—— 若认为不可接受，回滚方案见 §四 第 3 条 |
| 击退回归 | 非枪械伤害（空手打、摔落、爆炸）的击退手感与 r2 完全一致（`knockbackStrength` 默认 `-1` → 原样返回 `0.4F`） |

---

## 四、未修 / 建议下一轮

| # | 级别 | 内容 | 位置 | 为何本轮不修 / 建议 |
|---|---|---|---|---|
| 1 | **P2（文档）** | `event/KnockbackChange` 的类 javadoc 与 `TODO(port)` 已经**过期**：现在写「由 Mixin/entity 层在算出击退强度后调用…在该 Mixin 落地前，本修正不生效」，但 Mixin **本轮已落地**。留着会误导下一轮审查者以为还没接 | `event/KnockbackChange.java:22-24`、`:7-25` 类注释 | **不在我的写作用域**（`event/**` 属 gun-logic）。请 Lead 转派：把那段 TODO 改成「已由 `mixin/common/LivingEntityKnockbackMixin` 在 `EntityLivingBase#knockBack` 的 `0.4F` 常量上接线」，并说明「1.7.10 没有 `LivingKnockBackEvent`，`register()` 保持空实现是**预期形态**而非降级」 |
| 2 | P2（健壮性） | `EntityKineticBullet` 的 `setKnockBackStrength` / `resetKnockBackStrength` **不是 try/finally**。若 `tacAttackEntity` 内部抛异常（mod 不兼容/事件处理器炸），`knockbackStrength` 会**永久残留**在该实体上。**修复前这个残留是隐形的（没人消费），修复后它会变成可见错误**（该实体之后所有击退都被覆盖） | `entity/EntityKineticBullet.java:510-517` | 不在我的写作用域（`entity/**`）。建议改成 `try { tacAttackEntity(...) } finally { modifier.resetKnockBackStrength(); }`。风险等级中（需要先有异常路径才会触发） |
| 3 | P3（产品裁决） | 持枪左键**不再近战攻击生物**（与上游一致）。若实机体验上认为「必须保留左键近战」，最小改动是只在 `objectMouseOver.typeOfHit == BLOCK` 时 cancel（`Minecraft.objectMouseOver` 是 public 字段），代价是与上游偏离、且手臂仍会挥 | `mixin/client/MinecraftAttackMixin.java:115-120` | 本轮按上游保真选择「全拦」。留此条供裁决 |
| 4 | P2（范围外） | 上游 `ClientPreventGunClick` 也拦**右键**；本工程的右键拦截走 `PlayerInteractEvent`（已有），但**没有**上游的 `InteractKey` 放行分支与服务端对称问题核查 | `client/event/ClientPreventGunClick.java:61-82` vs `event/PreventGunClick.java:30-40` | 右键链路 r2 已修且实机通过，本轮不动；仅提示：客户端有 `InteractKey` 放行、服务端没有，理论上存在「按 O + 右键 = 客户端用、服务端也放（RIGHT_CLICK 不被 PreventGunClick 拦）」—— 两侧其实一致，仅记录 |
| 5 | P3（范围外） | `MouseHandlerMixin` 用 `@Mixin(Entity.class)` 承载「鼠标灵敏度」，同一目标类 `Entity` 与新 mixin 无关但值得注意：`Entity` 上没有任何 `LivingEntityMixin` 等价物，`IGunOperator.fromLivingEntity` 只对 `EntityLivingBase` 有效 | — | 无需动作，仅登记 |

**如果 §三 第 1 条（击退）实机未命中**，备用注入方案（按稳定性排序，供 Lead 直接改）：

1. `@ModifyVariable(method = "knockBack", at = @At(value = "STORE", ordinal = 1))` —— `knockBack` 里只有
   2 个局部变量 STORE（`f1` 槽 7、`f2` 槽 8，见 `EntityLivingBase.java:1055-1056`），`ordinal = 1` 即 `f2`；
2. `@ModifyVariable(..., at = @At("STORE"), index = 8)` —— 显式槽位；
3. 兜底：`@Inject(method = "attackEntityFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;knockBack(Lnet/minecraft/entity/Entity;FDD)V"), cancellable = true)` +
   自己算后重放（最脏，不建议）。

**如果 §三 第 2 条（幽灵方块）实机未命中**，备用方案：改 `@Mixin(PlayerControllerMP)` 拦
`clickBlock(int,int,int,int)` / `onPlayerDamageBlock(int,int,int,int)` 的 HEAD（这两个是 **MCP 名**、
`public`，`remap = true` 默认即可，refmap 一定能映射；代价是保留挥手 + 碎屑副作用）。

---

## 附：本轮改动文件清单

| 文件 | 动作 | `get_file_problems` |
|---|---|---|
| `src/main/java/com/tacz/guns/mixin/common/LivingEntityKnockbackMixin.java` | **新增** | 0 error |
| `src/main/java/com/tacz/guns/mixin/client/MinecraftAttackMixin.java` | **新增** | 0 error |
| `src/main/resources/mixins.tacz.json` | 改（+2 条） | — （JSON 合法性用 `ConvertFrom-Json` 校验通过） |
| `src/main/java/com/tacz/guns/init/CommonRegistry.java` | 改（删死代码 + 注释） | 0 error |
| `docs/review/r2/08-mixin-fix.md` | **新增**（本文件） | — |
