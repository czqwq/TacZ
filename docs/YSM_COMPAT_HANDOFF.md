# YSM（Yes Steve Model）Compat 交接文档

> 面向：日后写 TACZ ↔ YSM 联动适配的人
> 结论先说：**联动由 YSM 主动适配 TACZ，TACZ 侧不需要新增任何接口**。
> 本仓库（TACZ 1.7.10 移植）的唯一义务是：保持上游 `com.tacz.guns.api.*` 的**类名、方法签名、事件语义**与上游一致，
> 因为 YSM 是**编译期直接引用 + 反射探测**，签名漂移会直接 `NoSuchMethodError` / `NoClassDefFoundError`。

---

## 1. 联动方向与现状

| 侧 | 现状 |
|---|---|
| TACZ 上游 1.20.1 | **没有**任何 YSM 相关代码（全仓 grep `ysm` / `YesSteve` 无命中） |
| YSM 上游 1.20.1 | 主动适配 TACZ：`com.elfmcys.ysm.client.compat.tacz`（`TACZCompat` / `TacCompatInner` / `TacCtrlBinding` / `TacEvent`） |
| YSM 1.7.10（YSMU，本机 `E:\IDEA\YesSteveModel-Unofficial`） | **尚未写** TACZ compat；已注册 9 个 `ctrl.tac_*` Molang **占位常量** + `ModDependency("tacz", "ctrl.tac_")` 门控（模组未安装时跳过用 `ctrl.tac_*` 的控制器） |
| TACZ 1.7.10（本仓库） | 移植中；目标是把上游 API 表面原样搬过来，YSM 侧届时照上游 4 个文件改写即可 |

参考路径：

- YSM 上游 compat：`E:\IDEA\YesSteveModel-Unofficial\tmp\YesSteveModel-dev-1.20\src\main\java\com\elfmcys\ysm\client\compat\tacz\`
- YSM 1.7.10 占位注册：`E:\IDEA\YesSteveModel-Unofficial\src\main\java\com\fox\ysmu\client\animation\AnimationRegister.java:319-329`
- YSM 1.7.10 依赖门控：`E:\IDEA\YesSteveModel-Unofficial\src\main\java\com\fox\ysmu\client\ClientProxy.java:73`

---

## 2. YSM 用到的 TACZ 符号（契约清单）

### 2.1 `com.tacz.guns.api.TimelessAPI`

| 符号 | 用途 | 备注 |
|---|---|---|
| `static Optional<CommonGunIndex> getCommonGunIndex(ResourceLocation)` | 取枪械公共索引，读 `getType()` 决定武器大类 | 索引来自枪包加载，未加载时返回 `Optional.empty()` |

### 2.2 `com.tacz.guns.api.item.IGun`（`ItemStack` 上的能力接口）

| 符号 | 用途 |
|---|---|
| `static @Nullable IGun getIGunOrNull(ItemStack)` | 判定「手上是不是 TACZ 枪」的唯一入口 |
| `static boolean mainhandHoldGun(LivingEntity)` | `ctrl.tac_hold_gun` |
| `static FireMode getMainHandFireMode(LivingEntity)` | `ctrl.tac_fire_mode` |
| `ResourceLocation getGunId(ItemStack)` | `ctrl.tac_gun_id`、离手枪渲染分支 |

### 2.3 `com.tacz.guns.api.item.GunTabType`

枚举：`PISTOL` / `RPG` / `RIFLE`（上游以 `tabType.name().toLowerCase(Locale.ENGLISH)` 与 `CommonGunIndex#getType()` 比较，
即索引里的 `type` 字符串是 `"pistol"` / `"rpg"` / `"rifle"`）。

### 2.4 `com.tacz.guns.api.item.gun.FireMode`

`UNKNOWN` 等枚举常量；`fireMode != UNKNOWN ? fireMode.name() : ""`。

### 2.5 `com.tacz.guns.api.entity.IGunOperator`（LivingEntity 上的操作状态）

| 符号 | 用途 |
|---|---|
| `static IGunOperator fromLivingEntity(LivingEntity)` | 拿操作器 |
| `float getSynAimingProgress()` | `> 0` 视为瞄准中 → `ctrl.tac_is_aim` |
| `long getSynShootCoolDown()` | `> 0` 视为开火中 → `ctrl.tac_is_fire` |
| `ReloadState getSynReloadState()` → `.getCountDown()` | `> 0` 视为换弹中 → `ctrl.tac_is_reload` |
| `long getSynMeleeCoolDown()` | `> 0` 视为近战中 → `ctrl.tac_is_melee` |
| `long getSynDrawCoolDown()` | `> 0` 视为拔枪中 → `ctrl.tac_is_draw` |

### 2.6 `com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator`

`getClientShootCoolDown()`，与 `getSynShootCoolDown()` 取 `max` 作为客户端开火后摇判据。

### 2.7 Forge 事件（YSM 监听，用于「打空弹 / 近战 / 换弹后需要重播空枪动画」）

| 事件 | YSM 读取 |
|---|---|
| `com.tacz.guns.api.event.common.GunFireEvent` | `getShooter()` |
| `com.tacz.guns.api.event.common.GunMeleeEvent` | `getShooter()` |
| `com.tacz.guns.api.event.common.GunReloadEvent` | `getEntity()` |

> 1.7.10 侧注意：事件必须仍走 `cpw.mods.fml.common.eventhandler.SubscribeEvent` + `MinecraftForge.EVENT_BUS`，
> 且这三类事件在**双端都会** post（上游就是这样，YSM 客户端也监听）。

### 2.8 客户端静态开关（第一/第三人称枪口火焰、抛壳）

| 符号 | 用途 |
|---|---|
| `com.tacz.guns.client.model.functional.MuzzleFlashRender.isSelf` | `public static boolean`，YSM 在渲染自己的模型时切到 `true`，避免自家模型不显示枪口火焰 |
| `com.tacz.guns.client.model.functional.ShellRender.isSelf` | 同上，抛壳 |

> 这两个字段必须是 **`public static` 且可直接赋值**（不是 setter），YSM 直接写 `MuzzleFlashRender.isSelf = true;`。

---

## 3. YSM 期望的 9 个 Molang 变量语义

| 变量 | 类型 | 取值来源 |
|---|---|---|
| `ctrl.tac_hold_gun` | bool | `IGun.mainhandHoldGun(entity)` |
| `ctrl.tac_gun_type` | string | `CommonGunIndex#getType()`，非枪/未加载为 `""` |
| `ctrl.tac_gun_id` | string | `gun.getGunId(stack).toString()`，非枪为 `""` |
| `ctrl.tac_is_fire` | bool | `getSynShootCoolDown() > 0` |
| `ctrl.tac_is_aim` | bool | `getSynAimingProgress() > 0` |
| `ctrl.tac_is_reload` | bool | `getSynReloadState().getCountDown() > 0` |
| `ctrl.tac_is_melee` | bool | `getSynMeleeCoolDown() > 0` |
| `ctrl.tac_is_draw` | bool | `getSynDrawCoolDown() > 0` |
| `ctrl.tac_fire_mode` | string | `FireMode.name()`，`UNKNOWN` 为 `""` |

### 动画命名约定（YSM 模型包内的 `tac:` 动画）

```
tac:idle        tac:run         tac:walk                  // 主循环动画（tac: 前缀优先，缺失则回退无前缀名）
tac:hold:pistol tac:hold:rifle  tac:hold:rpg
tac:aim:pistol  tac:reload:pistol  tac:melee:pistol
tac:aim:fire:pistol             tac:hold:fire:rifle
tac:climb:pistol                tac:climbing:pistol  tac:climbing:fire:pistol
tac:mainhand:grenade            tac:offhand:grenade        // 上游 TODO：手雷还没有
```

判定顺序（`TacCompatInner#playGunOnceAnimation`）：换弹 > 近战 > 开火；换弹/近战/开火走 `PLAY_ONCE` 频道，
其余走 `LOOP`。攀爬判定 = `!isSwimming() && getPose() == Pose.SWIMMING`（1.20 语义），
1.7.10 对应为「正在爬梯/贴墙攀爬」，需要 YSM 侧自行给出等价判定。

---

## 4. 1.7.10 适配注意点（写给日后的 YSM compat 作者）

1. **包名/类名不变，但内部实现换平台**：`IGun`、`IGunOperator` 等仍是接口 + 静态工具方法，
   但底层 `ItemStack` 是 1.7.10 的（`net.minecraft.item.ItemStack`），NBT 是 `NBTTagCompound`。
2. **`PoseStack` 不存在**：YSM 1.7.10 用自己的 `GeoRenderData` + 固定管线；
   离手枪渲染（`renderOffhandGun`）要改成用 `ItemRenderer`/`RenderItem` + `GL11` 变换，不再有 `Axis.YP.rotationDegrees`。
3. **`ItemDisplayContext` 不存在**：1.7.10 用 `ItemRenderer.renderItem(EntityLivingBase, ItemStack, int renderPass)` 或
   直接 `Tessellator` 绘制；`FIXED` 语义对应「掉落物/固定展示」的等价 transform。
4. **`OverlayTexture.NO_OVERLAY`**：1.7.10 没有 overlay 纹理，直接省略该参数。
5. **事件注册**：`MinecraftForge.EVENT_BUS.register(obj)`，`@SubscribeEvent` 来自 `cpw.mods.fml.common.eventhandler`。
6. **`Pose.SWIMMING` 不存在**：1.7.10 无游泳姿态，攀爬/趴下判定要另立规则（或由 YSM 侧决定）。
7. **能力系统**：YSM 上游用 capability `PlayerAnimatableCapabilityProvider.CAP` 存 `tacGunAnimationNeedReload`；
   1.7.10 用 `IExtendedEntityProperties` 或 YSM 自定义管理器（YSMU 已有 `AppliedModelRegistry` 一类按实体 id 的管理器，
   照该形态存放即可）。
8. **`ItemStack` 空判定**：1.7.10 用 `stack == null || stack.getItem() == null`，不是 `isEmpty()`。
9. **`MuzzleFlashRender.isSelf` / `ShellRender.isSelf`**：1.7.10 移植后仍必须是 `public static boolean` 字段，
   YSM 才能一行赋值切换；但 1.7.10 的渲染顺序（`RenderHandEvent` / `RenderPlayerEvent`）与 1.20 不同，
   YSM 侧切开关的时机需按 1.7.10 渲染钩子重排。

---

## 5. 验收清单（TACZ 侧「已就绪」的判定）

- [ ] `com.tacz.guns.api.TimelessAPI#getCommonGunIndex` 可返回已加载枪包的 `CommonGunIndex`，`getType()` 为 `rifle` / `pistol` / `rpg`
- [ ] `IGun.getIGunOrNull` / `mainhandHoldGun` / `getMainHandFireMode` / `getGunId` 四个静态/实例方法签名与上游一致
- [ ] `IGunOperator` 六个 `getSyn*` 方法 + `fromLivingEntity` 与上游一致，且服务端状态能同步到客户端（IAmmo/同步机制保留）
- [ ] `GunFireEvent` / `GunMeleeEvent` / `GunReloadEvent` 双端 post，getter 名与上游一致
- [ ] `MuzzleFlashRender.isSelf`、`ShellRender.isSelf` 为 `public static boolean`
- [ ] `CommonGunIndex#getType()` 返回的字符串与 `GunTabType.name().toLowerCase(Locale.ENGLISH)` 可比较

任一项不满足，YSM 侧 compat 会在运行期 `NoSuchMethodError` 或静默失效（表现为枪械姿势动画不播放）。
