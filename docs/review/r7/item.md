# r7-item · 物品 / NBT / 改装 / 合成 / 工作台 上游等价性审查

- 审查人：`r7-item`（task-20）
- 写作用域：`src/main/java/com/tacz/guns/item/**`（**排除** r7-block 的 6 个方块物品）、
  `src/main/java/com/tacz/guns/inventory/**`、`src/main/java/com/tacz/guns/crafting/**`、本报告
- 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）
- 1.7.10 准绳：`build/rfg/minecraft-src/java/**`（本文所有「1.7.10 行为」结论都带该目录下的 file:line）
- 纪律：**未运行任何 gradle 任务**；改动文件 `lint_files(min_severity="error")` = **0 error**；
  未改 `tmp/**`、`build/**`、`run/**`、`src/main/resources/**`；未做 git checkout/stash/commit
- 不重复：`docs/review/r4/02-recipes.md`、`r4/03-refit.md`、`r5/nbt-sync.md`、`r6/tooltip.md` 已定案的结论只做**复核**

---

## 1. 结论速览

| 项 | 结论 |
|---|---|
| 包内文件对账 | `item/` 11 上游文件 **全部在**（+ `ItemClientHooks` 平台分端类、`BlockItemWithItemIcon` 属 r7-block）；`inventory/` 4+5 **全部在**（+ `GuiHandler`/`GuiId` 平台替代）；`crafting/` 7 **全部在**（+ `OreDictionaryFallback` 移植特有回退表）。**无上游文件丢失** |
| 公开方法对账 | 上游独有的 public/protected 方法**全部是 1.20 API 的 1.7.10 一一映射**（`inventoryTick→onUpdate`、`getName→getItemStackDisplayName`、`quickMoveStack→transferStackInSlot`、`stillValid→canInteractWith`、`broadcastChanges→detectAndSendChanges`…）。`AmmoBoxItem#overrideStackedOnOther` 已按 Lead 裁决搬到 `AmmoBoxItem#tryStackedOnOther`（mixin 侧由 r7-platform 落地，见 §3.3/§11）；`AttachmentItem#verifyTagAfterLoad`（旧 id 懒修复）仍有据降级见 §7 |
| NBT 键 | gun/ammo/ammo box/attachment 的 **33 个键**逐字段与上游同名同类型同默认值（§5 全表）。`GunLevelExp`/`tacz:tracer_*` 无写端 = **上游同样无写端** |
| 改装链路 | `AttachmentItem`、`AttachmentItemDataAccessor`、`GunItemDataAccessor#installAttachment/unloadAttachment/getAttachmentTag/getAttachmentId/getBuiltinAttachment` **逐行等价**；`SlotAdapterHelper`/`AttachmentPropertyManager` 属 util/resource（r7-util、r7-respack-core），本任务只核接口调用面，签名自洽 |
| 工作台合成 | `GunSmithTableRecipe`（静态登记表替代 `RecipeManager`）/`TableRecipe`/`GunSmithTableSerializer`/`GunSmithTableIngredient`/`RawGunTableResult`/`GunSmithTableResult` 语义等价；r5 修的「配方只能合成一次」**复核仍在且正确**，并纠正 r5 的一处**误判**（上游 `doCraft` 用的是 `getResultItem`，本就带 `copy()`，上游**没有**该缺陷，见 §8） |
| 本轮**真问题** | 4 条，均已修：① 弹药库「点条目取出/存入」后**鼠标手持栈从不下发**（1.7.10 不在槽位同步通道里）⇒ 丢件/复制风险；② 枪械近战 `doPerLivingHurt` 的**伤害粒子整段是死代码**（服务端 `World#spawnParticle` 是空实现）+ 丢了上游的 `doEnchantDamageEffects`（火焰附加/荆棘）与 `knockback` 的击退抗性衰减；③ 弹药盒「物品栏右键点进/点出」缺失 → 按 Lead 裁决用 `tryStackedOnOther` + mixin 落地（item 侧已完成）；④ `applyRecoilPush` 的 `crawling` 少了 `!isSwimming()` 半句 ⇒ **水里疾跑被当成趴下**（r7-gun 发现、Lead 转派） |
| 每帧 DBG | 本包 **0 处**（`grep TACZ-DBG|DBG|System.out` = 无命中） |

---

## 2. 发现表

| # | 级别 | 现象（玩家可见） | 根因 | 证据（本工程 ↔ 上游 ↔ 1.7.10） | 状态 |
|---|---|---|---|---|---|
| **R7-I-1** | **P1** | 弹药库里「点条目取出弹药」后，**鼠标上什么都看不到**（服务端其实已经把弹药放进手持栈）；接着点背包格/再点条目按客户端陈旧状态结算 ⇒ 弹药凭空消失或多出一份 | 1.7.10 的「手持栈」(`InventoryPlayer#itemStack`) **不在 `Container#detectAndSendChanges` 的槽位同步里**，原版只在 `EntityPlayerMP#updateHeldItem()` 里单独发 `S2FPacketSetSlot(-1,-1,stack)`。本界面的交互走自定义包（`ClientMessageAmmoStorageClick`）不经过 `slotClick`，`setCarried` 只改了服务端字段 | 本工程 `inventory/AmmoStorageMenu.java:257-292`（原 `setCarried` 只 `setItemStack`）↔ 上游 `inventory/AmmoStorageMenu.java:116-140`（`getCarried()/setCarried()` 由 `AbstractContainerMenu` 自带同步）↔ 1.7.10 `EntityPlayerMP.java:877-883`（`updateHeldItem` → `S2FPacketSetSlot(-1,-1,…)`）、`NetHandlerPlayServer.java:949-…`（`processClickWindow` 改完手持栈后调 `updateHeldItem`）、`NetHandlerPlayClient.java:1129-1139`（`windowId == -1` 时 `inventory.setItemStack`） | **已修** |
| **R7-I-2a** | P2 | 弹药盒「点进弹药 → 盒内计数 +1」时，若**整栈恰好被吃完**，手里会留下一个 `stackSize = 0` 的「幽灵栈」（图标还在鼠标上、但点不动/点一下消失） | 上游 `held.copyWithCount(0)` 得到的是 1.20 语义的**空栈**（`isEmpty()` 判 `count <= 0`）；1.7.10 的空栈是 `null`，`withCount(held, 0)` 返回的是 `stackSize = 0` 的真栈 | 本工程 `inventory/AmmoStorageMenu.java:272-281` ↔ 上游 `:125-131` | **已修**（与 R7-I-1 同一处） |
| **R7-I-2b** | **P1** | 枪械近战（枪托砸）**完全没有伤害粒子**（上游会向附近玩家广播 DAMAGE_INDICATOR 粒子） | 那段循环包在 `if (!user.worldObj.isRemote)`（服务端）里，而 1.7.10 `World#spawnParticle(String,double×6)` 是「转发给 `worldAccesses`」，服务端的 `IWorldAccess` 是 `WorldManager`，其 `spawnParticle` **方法体为空**（纯 no-op）。也就是说这段代码在两侧都永远不产生任何效果 | 本工程 `item/ModernKineticGunItem.java:692-706` ↔ 上游 `item/ModernKineticGunItem.java:587-590`（`ServerLevel#sendParticles`）↔ 1.7.10 `World.java:1457-1463`（转发）、`WorldManager.java:29-33`（空实现）、`WorldServer.java:1121-1139`（`func_147487_a` → `S2APacketParticles` 广播给 16 格内玩家） | **已修** |
| **R7-I-2c** | P2 | 枪械近战**不触发火焰附加**（附魔枪托砸怪不起火）；对高击退抗性目标（铁傀儡等）击退“打不动”的语义也没跟上 | 上游 `doPerLivingHurt` 里 `user.doEnchantDamageEffects(user, target)`（1.8+ Forge API）与 `target.knockback(...)` 内部 `strength *= 1 - knockbackResistance` 在移植时被整段丢掉了；原注释写「1.7.10 的 `attackEntityFrom` 内部已应用攻击者附魔效果」——**该结论是错的**：1.7.10 `EntityLivingBase#attackEntityFrom` 只做护甲附魔减伤（`EnchantmentHelper.getEnchantmentModifierDamage`，`EntityLivingBase.java:1234`），火焰附加是 `EntityPlayer#attackTargetEntityWithCurrentItem` 里单独施加的（`EntityPlayer.java:1378,1448-1451`） | 本工程 `item/ModernKineticGunItem.java:649-707` ↔ 上游 `:561-591` ↔ 1.7.10 `EntityPlayer.java:1417-1420`（`EnchantmentHelper.func_151384_a(target, this)` / `func_151385_b(this, targetEntity)` = 1.20 `doEnchantDamageEffects`）、`EntityLivingBase.java:178`（注册 `knockbackResistance` 属性） | **已修** |
| R7-I-3 | P2 | 弹药盒**无法在物品栏里把弹药物品「点进」盒子 / 从盒子取出**（上游：手持盒子右键点槽位） | 上游用 `Item#overrideStackedOnOther`（1.20 的物品栏点击钩子），1.7.10 **没有**任何对应钩子（`Container#slotClick` 里不含物品自定义回调，也没有 Forge 事件） | 本工程 `item/AmmoBoxItem.java:275-…`（`tryStackedOnOther`）↔ 上游 `item/AmmoBoxItem.java:102-180` | **item 侧已修**（Lead 裁决走 mixin：`tryStackedOnOther` 已落地并逐行对齐上游；`mixin/common/ContainerSlotClickMixin` 由 r7-platform 写，见 §11） |
| R7-I-4 | P3 | 带 `AttachmentId` 旧 id（`tacz:muzzle_silence_*`）的存档/老枪包数据不会自动迁移 | 上游 `AttachmentItem#verifyTagAfterLoad`（1.20 物品 NBT 加载后钩子）在 1.7.10 不存在；`util/datafixer/AttachmentIdFix` 仍 0 调用者 | 本工程 `item/AttachmentItem.java:111-114`（TODO）、`util/datafixer/AttachmentIdFix.java` ↔ 上游 `item/AttachmentItem.java:103-106` | **未修**（跨域，见 §6-②；r4-C-4 已登记，本轮复核仍在） |
| R7-I-5 | P3 | 物品名染色钩子（`ItemClientHooks#nameColorProvider`/`getNameColor`）恒空转 | 1.7.10 `Item#getItemStackDisplayName` 只返回 `String`，没有「给物品名上色」的 API；两个受影响的物品（弹药盒/弹药库方块物品）已各自用 `EnumChatFormatting` 前缀实现 | 本工程 `item/ItemClientHooks.java:70-76` ↔ 上游 `AmmoBoxItem.java:211-225` 的 `withStyle(...)` | **未修（有据）**，`client/init/ClientSetupEvent.java:336-345` 已有同样说明 |
| R7-I-6 | P3 | `ItemClientHooks#registerRenderer(Item)`（以及它消费的 `registerItemRenderer` 字段）没有调用点 | 客户端已改为在 `ClientSetupEvent#registerItemRendererFor` 里**按物品身份**直接注册（等价上游 `Item#initializeClient` 的分派），这个通用钩子成了多余通道 | 本工程 `item/ItemClientHooks.java:50-54,78-87` ↔ `client/init/ClientSetupEvent.java:222,260-284` | **未改**（保留为对外扩展点，见 §7-④） |
| R7-I-7 | P3 | `AmmoBoxItem#getStatue(ItemStack)` 无调用点 | 上游消费方是 1.20 的 item model predicate（`ammo_statue`），1.7.10 改由 `IItemRenderer` 按 NBT 切图标（当前用单张静态图标兜底） | 本工程 `item/AmmoBoxItem.java:97-121` ↔ 上游 `:62-77` | **未改**（保留上游 API 面 + L2 渲染接入点）；`playInsertSound/playRemoveOneSound` 曾被列为无调用点，本轮已被 `tryStackedOnOther` 使用 |
| **R7-I-8** | P2 | **在水里疾跑**（1.7.10 的 `isInWater && isSprinting`）开枪时，后坐力推挤走了 **crawl 分支**（用 `crawl_strength`，且 `onGround` 被强制置真 ⇒ 水面也走地面强度），而上游只有「真趴下」才走 crawl | `crawling` 判定只抄了上游 `player.getPose() == Pose.SWIMMING` 这半句，**丢了 `!player.isSwimming()`**；1.7.10 的 `EntityStateHelper#getPose` 对「水里疾跑」同样返回 `SWIMMING`（`EntityStateHelper.java:87-89`），于是游泳被当成趴下。次要项：`sneaking` 用 `player.isSneaking()`，上游是 `getPose() == CROUCHING`（趴下/游泳覆盖期间两者不同） | 本工程 `item/ModernKineticGunScriptAPI.java:291-303` ↔ 上游 `item/ModernKineticGunScriptAPI.java:226-230` ↔ 1.7.10 无 Pose：`isSwimming()` 的等价物是 `isInWater()`（仓库既有口径：`client/event/CameraSetupEvent.java:381`、`client/event/TickAnimationEvent.java:79`、`mixin/client/MouseHandlerMixin.java:166`），姿势读 `EntityStateHelper#getPose`（趴下走 `ENTITY_POSE_OVERRIDES`，`EntityStateHelper.java:62-91`） | **已修**（r7-gun 发现、Lead 转派；见 §3.4） |

### 严重度口径

- 本轮无 P0。R7-I-1 定 P1：**物品可能凭空消失/复制**，但只在「弹药库 GUI + 鼠标手持栈」这一条交互上，且需要「取出后再点格子/关界面」才触发。
- R7-I-2b 定 P1 的理由不是严重度而是**确定性**：它是 100% 死代码（两侧都无效果），与「粒子偶尔没看到」不同。

---

## 3. 已修清单（4 文件 / 6 处）

### 3.1 `src/main/java/com/tacz/guns/inventory/AmmoStorageMenu.java`

| 位置 | 改动 | 对应发现 |
|---|---|---|
| `:299-321` | `setCarried(@Nullable ItemStack)` → `setCarried(EntityPlayer player, @Nullable ItemStack stack)`：改完 `playerInventory.setItemStack(...)` 后，服务端额外调 `EntityPlayerMP#updateHeldItem()`（= `S2FPacketSetSlot(-1,-1,stack)`），与原版 `processClickWindow` 同一收尾；javadoc 里写明「自定义包不经过 `slotClick`，必须自己补下发」以及不补的后果 | R7-I-1 |
| `:272-281` | 存入分支：`remaining = held.stackSize - inserted`，`remaining > 0` 时 `withCount(held, remaining)`，否则 `setCarried(player, null)`（1.20 `copyWithCount(0)` = 空栈 = 1.7.10 `null`） | R7-I-2a |
| `:288` | 取出分支改用新的 `setCarried(player, …)`（同样补下发） | R7-I-1 |

### 3.2 `src/main/java/com/tacz/guns/item/ModernKineticGunItem.java`（`doPerLivingHurt`，`:649-707`）

| 位置 | 改动 | 对应发现 |
|---|---|---|
| `:654-667` | 击退强度先按目标 `SharedMonsterAttributes.knockbackResistance` 衰减（`1 - resistance`，夹到 ≥0，为 0 时不再施加速度），对齐 1.20 `LivingEntity#knockback` 的第一步；1.7.10 有该属性（`EntityLivingBase.java:178`） | R7-I-2c |
| `:673-677` | 用 1.7.10 原版 `EntityPlayer#attackTargetEntityWithCurrentItem` 的**同一对调用**替换掉被丢掉的上游 `doEnchantDamageEffects`：`EnchantmentHelper.func_151384_a(target, user)`（受击方装备 `doPostHurt`：荆棘）+ `func_151385_b(user, target)`（攻击方装备 `doPostAttack`：火焰附加） | R7-I-2c |
| `:692-706` | 删除服务端 no-op 的 `spawnParticle` 循环，改为 `WorldServer#func_147487_a("crit", x, y+0.5h, z, (int)(damage*0.5), 0.1, 0, 0.1, 0.2)`——与上游 `ServerLevel#sendParticles(DAMAGE_INDICATOR, …, count, 0.1, 0, 0.1, 0.2)` 逐参对应（1.7.10 无 DAMAGE_INDICATOR，`"crit"` 近似；广播半径 16 格 < 上游 32 格，属平台差异）。写法与 `entity/EntityKineticBullet.java:705-719` 的 `spawnServerParticles` 同一套口径 | R7-I-2b |
| `:673-675` | 删掉错误的「1.7.10 `attackEntityFrom` 内部已应用攻击者附魔效果」注释，换成带 `file:line` 的正确依据 | R7-I-2c |

> 未引入任何新的平台机制：三处都是把「上游已有调用」翻译成 1.7.10 里**原版自己就在用的**等价调用。

### 3.3 `src/main/java/com/tacz/guns/item/AmmoBoxItem.java`（物品栏右键交互，Lead 裁决走 mixin）

| 位置 | 改动 | 对应发现 |
|---|---|---|
| `:275-389` | 新增 `public static boolean tryStackedOnOther(EntityPlayer player, int slotId, int clickedButton, int mode)`：把上游 `Item#overrideStackedOnOther`（`AmmoBoxItem.java:102-180`）整段搬过来（空格取弹 / 弹药格存弹 / 创造与全类型创造分支 / `AmmoBoxStackSize × (level+1)` 上限 / 两个音效），并由 `slot.safeInsert`/`safeTake` 换成 1.7.10 的 `isItemValid + getSlotStackLimit + putStack`、`canTakeStack + decrStackSize + onPickupFromSlot` | R7-I-3 |
| `:225-238` | `playRemoveOneSound/playInsertSound` 的 javadoc 说明调用方改为 `tryStackedOnOther`（不再是「未来 GUI」） | R7-I-3 / R7-I-7 |
| `:41-45` | 类 javadoc 的「重要差异」条目改写：不再写「需要新增 AmmoBox 容器 GUI」，改为「由 `tryStackedOnOther` + `ContainerSlotClickMixin` 承载上游钩子」 | R7-I-3 |
| 删除 | 原 `:240-242` 的 `TODO(port L2/后续): … 需要新增 1.7.10 GUI` 整段删除 | R7-I-3 |

**与上游的逐分支对应**（`false` 一律 = 交回原版 `slotClick`，与上游「返回 false 走 `doClick` 普通放置/交换」等价）：

| 上游分支 | 本工程 | 返回 |
|---|---|---|
| `action != SECONDARY` | `mode != 0 || clickedButton != 1` | false |
| 空格 + 创造/全类型创造盒 | 同 | false |
| 空格 + 盒内无弹种 / 数量 ≤ 0 / 索引缺失 | 同 | false |
| 空格 + `safeInsert` 后 `insertedCount <= 0` | `!slot.isItemValid` 或 `min(takeCount, slotLimit) <= 0` | false |
| 空格 + 取弹成功（`setAmmoCount` / 取空 `setAmmoId(EMPTY)` / `playRemoveOneSound`） | 同 | **true** |
| 弹药格 + 全类型创造盒 / 格内 id 为空 / 与盒内 id 不同 | 同 | false |
| 弹药格 + 盒内 id 为空 → 设为格内 id | 同（**在 `ifPresent` 之前**设置，与上游同序） | — |
| 弹药格 + `ifPresent`：创造盒 → `MAX_VALUE`；普通 → `safeTake(count, needCount)` 后累加 | `needCount > 0 && slot.canTakeStack(player)` → `decrStackSize(min(count, needCount))` → `onPickupFromSlot` → 累加 | **true**（含索引缺失时也返回 true，上游同款怪癖逐行保留） |
| 其它物品格 | 同 | false |

mixin 侧契约（已与 Lead 对齐、由 r7-platform 落地）：`Container#slotClick` HEAD cancellable → `if (AmmoBoxItem.tryStackedOnOther(player, slotId, clickedButton, mode)) { ci.cancel(); return player.inventory.getItemStack(); }`。
返回值必须是「修改后的手持栈」，因为 1.7.10 客户端 `PlayerControllerMP#windowClick`（`PlayerControllerMP.java:475-481`）会把 `slotClick` 的返回值当 `clickedItem` 发给服务端，服务端 `NetHandlerPlayServer#processClickWindow`（`:949` 起）与之比对后才 confirm 并 `updateHeldItem()`。
**音效**：`EntityPlayer#playSound` → `worldObj.playSoundToNearExcept`（`EntityPlayer.java:421-424`），服务端会下发 `S29PacketSoundEffect` 给附近其它玩家（`WorldManager.java:56-59`）、客户端本地播放 ⇒ 两侧各跑一次正好等价于上游 `Player#playSound` 的「所有人听到一次」。

### 3.4 `src/main/java/com/tacz/guns/item/ModernKineticGunScriptAPI.java`（`applyRecoilPush`，`:291-303`）

| 位置 | 改动 | 对应发现 |
|---|---|---|
| `:299` | `crawling` 补回上游丢掉的**前半句**：`!player.isInWater() && EntityStateHelper.getPose(player) == EntityPose.SWIMMING`（上游 `ModernKineticGunScriptAPI.java:227` = `!player.isSwimming() && player.getPose() == Pose.SWIMMING`）。`isSwimming()` 的 1.7.10 等价物取仓库既有口径 `isInWater()`（`CameraSetupEvent.java:381`、`TickAnimationEvent.java:79`、`MouseHandlerMixin.java:166` 三处同写法） | R7-I-8 |
| `:303` | `sneaking` 由 `player.isSneaking()` 改为 `EntityStateHelper.getPose(player) == EntityPose.CROUCHING`（上游 `:229` = `player.getPose() == Pose.CROUCHING`）。1.7.10 **有**等价姿势读取（`EntityStateHelper#isCrouching` 已存在，`mcshim/EntityStateHelper.java:120-123`），因此不记为降级；影响面：`getFinalStrength` 里 `crawling` 优先于 `sneaking`（`GunRecoilPushData.java` `getFinalStrength` 的 `else if` 链），所以该项只在「非趴下」时起作用 | R7-I-8 |
| `:291-298` | 注释改为引上游 `:226-230` 三行原文 + 1.7.10 等价物出处，并写明「缺 `!isInWater()` 会把水里疾跑误判成趴下（`EntityStateHelper.java:87-89`）」 | R7-I-8 |

> 这条是 r7-gun 在审查 `entity/shooter/**` 时发现的（服务端姿势表 `LivingEntityCrawl` 由他们维护，本节只改 `item/**` 侧的读取）。

---

## 4. 逐文件对照表

### 4.1 `item/**`（本任务负责的 7 个源文件）

| 本工程 | 上游 | 结论 |
|---|---|---|
| `ModernKineticGunItem.java`（799L） | `:639L` | ✅ 等价（仅 `inventoryTick→onUpdate`）；`defaultTickReload/defaultTickBolt/defaultReloadFinishing/modifyProperty/DefaultPropertyModification/fireSelect/getLevel·getExp·getMaxLevel`**逐行相同**；`doMelee` 的锥形判定公式（`acos(dot/(len·distance)) < rangeAngle/2`）与 `Vec3` 旋转顺序（先 X 后 Y，`Vec3.rotateAroundX/Y` 与 1.20 `xRot/yRot` 同式）逐行核对通过；本轮修 `doPerLivingHurt`（§3.2） |
| `ModernKineticGunScriptAPI.java`（888L） | `:960L` | ✅ **方法集逐一相同**（`getCachedProperty/shootOnce/applyRecoilPush/handleShootHeat/reduceAmmoOnce/get*Time/adjust*/get*Charge*/set·getShotDamage·ProjectileSpeedMultiplier/getReloadStateType/getFireMode/isShootingNeedConsumeAmmo/isReloadingNeedConsumeAmmo/getNeededAmmoAmount/getAmmoAmount/getMaxAmmoCount/getMagExtentLevel/consumeAmmoFromPlayer/hasAmmoToConsume/put·removeAmmoInMagazine/getAmmoCountInMagazine/unloadAmmo/has·setAmmoInBarrel/cache·getCachedScriptData/getScriptParams/safeAsyncTask/getCurrent·RealSystemTimestamp/getAttachment/getNbt/getEntityUtil/set/ get*/getHeat*/is·setOverheatLocked/getOverheat·CoolingTime/calcHeatReduction/getBoltByInt/getBolt/useInventoryAmmo`）。`applyRecoilPush` 的 `directionFromRotation(pitch,yaw).scale(-strength)` 三角推导与手写 `dirX/dirY/dirZ` **代数等价**（本轮重算过）；`consumeAmmoFromPlayer/hasAmmoToConsume` 的 `ITEM_HANDLER` → `InventoryPlayer` 映射语义一致（非玩家射手返回 0/false = 上游 `orElse(0/false)`）。**本轮修 `applyRecoilPush` 的 `crawling/sneaking` 姿势判定（§3.4）** |
| `AmmoItem.java` | `:107L` | ✅ `getMaxStackSize→getItemStackLimit(ItemStack)`（1.7.10 `ItemStack#getMaxStackSize` 就是调它）、`getName→getItemStackDisplayName`+`ItemClientHooks.itemNameProvider`（客户端索引名已接线，`ClientSetupEvent.java:333-380`）、`fillItemCategory` 排序规则（`ContentPackOrder.byNamespace().thenComparingInt(getSort)`）一致；`appendHoverText` 的 tooltipKey + 枪包名由 `client/event/TooltipEvent#ammoHoverText`（`:301`）接管，与上游同内容；`initializeClient` 由 `ClientSetupEvent#createItemRenderer` 的 `item == ModItems.AMMO` 分支接管 |
| `AttachmentItem.java` | `:107L` | ✅ 同上；`getType` 逐行等价；`getTooltipImage` → `TooltipEvent:241-242` → `ClientAttachmentItemTooltip`（r6 已做图标网格）；**`verifyTagAfterLoad` 缺失 = R7-I-4** |
| `AmmoBoxItem.java` | `:290L` | ✅ NBT（`AmmoId/AmmoCount/Level/Creative/AllTypeCreative` + `display.color`）与 `getStatue/getOpenStatue` 逻辑逐行等价；耐久条：`isBarVisible→showDurabilityBar`、`getBarWidth(1+12p,13)→getDurabilityForDisplay(1-p)`（1.7.10 由原版把 0..1 画成 13 段）、`getBarColor`(固定绿) **无 1.7.10 对应**（原版条色由填充比例决定，记录差异）；`getName` 的 4 个 `withStyle` → `EnumChatFormatting` 前缀（语义等价）；**`overrideStackedOnOther` 由 `tryStackedOnOther` + mixin 落地（§3.3 / §11）** |
| `GunTooltipPart.java` | `:32L` | ✅ 枚举序/掩码/`setHideFlags` 一致；`getHideFlags` 兜底由上游 `Item#getDefaultTooltipHideFlags`（1.8+ Forge API）降级为 `0`（1.7.10 无该 API） |
| `ItemClientHooks.java`（移植新增） | — | ✅ 平台分端类：`itemNameProvider` 已接线；`nameColorProvider`/`registerRenderer` 无消费者（R7-I-5/6，有据） |

> 域外但相邻（属 r7-block，只在跨包调用面核过签名）：`AmmoStorageBlockItem`（唯一调用方 `item/AmmoStorageBlockItem.java:96` 的 `onUpdate` 与 `BlockEntityTag` 搬运，r5 已核）、`BlockItemWithItemIcon`、`DefaultTableItem`、`GunPegboardItem`、`GunSmithTableItem`、`TargetMinecartItem`。

### 4.2 `inventory/**`

| 本工程 | 上游 | 结论 |
|---|---|---|
| `AmmoStorageInventory.java`（590L） | `:339L` | ✅ `IItemHandler→IInventory` 语义映射逐方法等价：`getUsedStacks/getUsedTypes/getRemainingCapacity/insert/insertIntoSlot/extract/extractFromSlot/moveToInventory/getComparatorSignal/save/load/findMatchingSlot/findEmptySlot/stacksFor/saturatedAdd` 与上游**逐行同式**；NBT 键 `Slot`(byte)/`Stack`(compound)/`Count`(long) 同键同类型；`load` 的容错（重复 slot/坏物品/超容保留）与上游一致。差异均为平台性：`simulate` 无槽级对应物（靠同名 `insertItem/extractItem/insert/extract` 保留）、`getInventoryStackLimit()` 固定 64（`IInventory` 无「按槽动态上限」）、`getStackInSlotOnClosing` 返回 null |
| `AmmoStorageMenu.java`（~355L） | `:186L` | ✅ 槽位布局常量（`STORAGE_ROWS/…/HOTBAR_Y`）与上游逐个相同；`detectAndSendChanges` 双通道（标量走 `sendProgressBarUpdate`→`updateProgressBar`，条目列表走 `ContentSender` 钩子，由 `network/NetworkHandler.java:358` 注入 `ServerMessageAmmoStorageContents`）与上游 `broadcastChanges` 等价；`transferStackInSlot` 的 `index<27?27:0 / …36:27` 与上游 `moveItemStackTo` 参数一致；**本轮修手持栈下发 + 0 数量栈（§3.1）** |
| `GunSmithTableMenu.java`（272L） | `:172L` | ✅ `getRecipe`/`canSelectRecipe`/`requestRecipeSelection`/`consumeRecipeSelection` 逐行等价（含 `flag` 反相逻辑）；`doCraft` 的材料记账/扣除/产物实体生成/刷新与上游同序；`getOutput()` 已带 `copy()`（r5 修的 P0，见 §8） |
| `AmmoStorageEntry.java` | `:10L` | ✅ record 字段/`sample` 归一为 1 个（`copyWithCount(1)` → `copy()+stackSize=1`）等价，null 安全 |
| `tooltip/{AmmoBox,AmmoStorage,AttachmentItem,BlockItem,Gun}Tooltip.java` | 5 个 | ✅ 字段/构造器/getter 与上游一一对应，仅去掉 1.7.10 不存在的 `TooltipComponent` 接口；**5 个都有真实消费者**（`client/event/TooltipEvent.java:224/241/267/286/371`），不是死代码 |
| `GuiHandler.java` / `GuiId.java`（移植新增） | `init/ModContainer` + `MenuProvider` + `ModContainerScreen` | ✅ 平台替代：`IGuiHandler` + int id + 方块坐标；服务端/客户端两侧容器重建参数（工作台 `blockId` 从本地 TileEntity 读）与上游「网络包携带 blockId」等价 |

### 4.3 `crafting/**`

| 本工程 | 上游 | 结论 |
|---|---|---|
| `GunSmithTableRecipe.java`（160L） | `:88L` | ✅ 字段/`getId/getInputs/getResult/init/getTab` 同；1.20 `Recipe`/`RecipeType`/`RecipeSerializer`/`matches`/`assemble` 删除有据（1.7.10 的 `IRecipe` 只有 4 方法、且 `CraftingManager#findMatchingRecipe` 会强转 ⇒ 塞进去必 CCE）；`getOutput()` 返回副本（上游 `getResultItem` 语义）；静态登记表 `register/getById/getAll/getAllIds/clear/initAll` 替代 `RecipeManager`，`clear()` 由 `GunSmithTableRecipeManager#loadRecipes:108` 调用，`initAll()` 由 `CommonAssetsManager#initLazyRecipes:476` 调用（**无死代码**） |
| `GunSmithTableIngredient.java`（478L） | `:21L` | ✅ 上游 `Ingredient`+`count` 在 1.7.10 的唯一等价物（`ItemStack` 或矿词名）；匹配语义 `test`/`getMatchingStacks` 两条路径**互相一致**（矿词空 → 比对代表物品），与上游「`Ingredient#test` 与 `#getItems` 同源」的约束等价；`fromJson` 的 `{"tag":…}`/`{"item":…}` 两分支覆盖默认包 **550 tag + 20 item** 全部真实写法 |
| `GunSmithTableSerializer.java`（77L） | `:58L` | ✅ `fromJson` → `deserialize` 等价（`CommonAssetsManager.GSON` 已注册两个自定义适配器）；`fromNetwork/toNetwork` 删除有据（1.7.10 无配方同步管线，客户端与专服共用同一份代码） |
| `AmmoStorageUpgradeRecipe.java`（145L） | `:101L` | ✅ `matches`（3×3、中心 `from`、其余 8 格 `material`）与 `assemble`（搬 `BlockEntityTag`）等价；`getRecipeOutput` 无 NBT、`getCraftingResult` 才带 NBT——与上游 `getResultItem` vs `assemble` 的同一非对称性一致（不是移植缺陷） |
| `GunPegboardDyeRecipe.java`（167L） | `:70L` | ✅ `matches` 同；`assemble` 的调色算法与 1.7.10 原版 `RecipesArmorDyes` **逐行相同**（先按已有自定义色累加、再 `avg/max` 归一、`(r<<8+g)<<8+b`），写同名 NBT `display.color` |
| `result/GunSmithTableResult.java`（165L） | `:54L` | ✅ `init()`（raw → ItemStack、group 兜底 `TAB_EMPTY`）逐行同；`fromJson` 逐行对应上游 `GunSmithTableResultSerializer#deserialize`（`type` 分支 /`count`≥1 /`nbt`/`group` 补 `tacz:` 前缀 /`custom` 走 `CraftingHelper.getItemStack` 的 1.7.10 版 `ItemStackJsonParser`） |
| `result/RawGunTableResult.java`（158L） | `:113L` | ✅ `init` 的 6 个键合并写回（`getOrCreateTag` 手动实现）、`getGunStack`（`setCount/setId/setAmmoCount/setAmmoInBarrel(false)/putAllVirtualAttachment(defaults)/putAllAttachment/setFireMode(fireModeSet.get(0))`）、`getAmmoStack`、`getAttachmentStack` 与上游逐行同 |
| `OreDictionaryFallback.java`（203L） | — | ✅ 移植特有回退表（r4 已定案）：`isRegistered/fallbackItem/oreOrFallback/scaleRequestedCount/findItemStack`，被 `GunSmithTableIngredient` 与 `init/ModRecipe` 共用，口径不分叉 |

---

## 5. NBT 键对照表（gun / attachment / ammo / ammo box）

访问器本体在 `api/item/nbt/**`（r7-api 作用域），本任务按清单**逐字段核对**（读两端），结论：**键名/类型/默认值/存读对称全部等价**，无「写不读 / 读不写」。

| NBT 键 | 类型 | 读 | 写 | 默认值 | 上游 | 结论 |
|---|---|---|---|---|---|---|
| `GunId` | String | `GunItemDataAccessor:112-119` | `:122-127` | `EMPTY_GUN_ID` | 同 | ✅ |
| `GunDisplayId` | String | `:131-138` | `:141-146` | `DEFAULT_GUN_DISPLAY_ID` | 同 | ✅（写端 0 调用点，上游同样 0） |
| `GunFireMode` | String | `:189-195` | `:198-205` | `UNKNOWN`（写 null 时也写 `UNKNOWN`） | 同 | ✅ |
| `GunCurrentAmmoCount` | Int | `:208-214` | `:217-220`（`max(0,·)`） | 0 | 同 | ✅ |
| `HasBulletInBarrel` | Byte | `:379-385` | `:388-391` | false | 同 | ✅ |
| `Attachment<SCOPE/MUZZLE/STOCK/GRIP/LASER/EXTENDED_MAG>` | Compound（内含整份 `ItemStack` 的 `id/Count/Damage/tag`） | `:232-245`（取子 `tag`）+`:274-284`（整栈） | `:320-334`（`installAttachment`） | 无 | 同 | ✅ |
| `Attachment<…>` 卸载占位 | Compound（`id=0`） | `AttachmentItemDataAccessor:30-39` → `EMPTY_ATTACHMENT_ID` | `:337-346` + `ItemStackNbtHelper.writeEmptyTag:78-82` | — | 上游写 `ItemStack.EMPTY.save(tag)` | ✅（1.7.10 空栈无单例，写 `id=0` 等价） |
| `AttachmentLock` | Byte | `:96-102` | `:105-108` | false | 同 | ✅ |
| `DummyAmmo` | Int | `:52-55` | `:58-61`,`:64-75` | 0 | 同 | ✅ |
| `MaxDummyAmmo` | Int | `:84-87` | `:90-93` | 0 | 同 | ✅（写端 0 调用点，上游同样 0） |
| `HeatAmount` | Float | `:436-440` | `:443-446`（`max(0)`） | 0f（`hasHeatData` = 键存在） | 同 | ✅ |
| `OverHeated` | Byte | `:424-427` | `:430-433` | false | 同 | ✅ |
| `LaserColor`（枪） | Int | `:400-406` | `:409-412` | 0xFF0000（键不存在时） | 同 | ✅ |
| `GunLevelExp` | Int | `:149-164` | **无写端** | 0 | **上游同样无写端**（等级系统未落地） | ✅ |
| `AttachmentId` | String | `AttachmentItemDataAccessor:30-39,57-60` | `:63-68` | `EMPTY_ATTACHMENT_ID` | 同 | ✅ |
| `Skin` | String | `:72-78` | `:81-88`（null 时删键） | null | 同 | ✅ |
| `ZoomNumber` | Int | `:91-94` | `:97-100` | 0 | 同 | ✅ |
| `LaserColor`（配件） | Int | `:109-115` | `:118-121` | 0xFF0000 | 同 | ✅ |
| `AmmoId`（弹药） | String | `AmmoItemDataAccessor:25-32` | `:35-42`（null → `DEFAULT_AMMO_ID`） | `EMPTY_AMMO_ID` | 同 | ✅ |
| `AmmoId`（弹药盒） | String | `AmmoBoxItemDataAccessor:22-28` | `:31-34` | `EMPTY_AMMO_ID` | 同 | ✅ |
| `AmmoCount` | Int | `:37-46`（creative → `MAX_VALUE`） | `:49-56`（creative 强制 `MAX_VALUE`） | 0 | 同 | ✅（读写两侧同规则） |
| `Level` | Int | `:90-96` | `:83-87`（`max(0)`） | 0 | 同 | ✅ |
| `Creative` | Byte | `:99-105` | `:117-133`（与 `AllTypeCreative` 互斥） | false | 同 | ✅ |
| `AllTypeCreative` | Byte | `:108-114` | `:117-133` | false | 同 | ✅ |
| `display.color`（弹药盒/挂板染色） | Int（`display` 子 compound） | `AmmoBoxItem:118-125`、`GunPegboardItem#getColor` | `AmmoBoxItem` 无写（上游由 `DyeableLeatherItem` 写）、`GunPegboardDyeRecipe:145-154` 写 | 0x727d6b | 1.20 `DyeableLeatherItem` 同键 | ✅ |
| `HideFlags` | Int | `item/GunTooltipPart:32-39` | `:41-44` | 0（上游取 `getDefaultTooltipHideFlags`，1.8+） | 同键 | ✅（兜底差异见 §7-③） |
| `SlotAdapters` / `VirtualOemAttachment` / `Variant` | Compound(String) / Byte / String | `util/SlotAdapterHelper`/`VirtualOemAttachment`/`VariantHelper` | 同 | — | 同 | ✅（r4 已核，本轮只确认 item 侧不写这些键） |
| 弹药库条目 `Slot`(Byte)/`Stack`(Compound)/`Count`(Long) | — | `AmmoStorageInventory:253-294` | `:236-250` | — | 同 | ✅ |
| `BlockEntityTag` | Compound | `item/AmmoStorageBlockItem:92`、`crafting/AmmoStorageUpgradeRecipe:120-131` | 同 + `block/AmmoStorageBlock:209` | — | 1.20 `BlockItem#get/setBlockEntityData` | ✅（搬运链路三条都在） |

**存储对称性抽查**：`ItemStackNbtHelper` 的 `getOrCreateTag/getTag/hasTag/isEmpty/save/writeEmptyTag/load/isPlaceholderTag` 覆盖了 1.20 的 `getOrCreateTag/getTag/hasTag/isEmpty/save/of`，`null` 栈语义（1.7.10 空栈 = null）在 gun/attachment/ammo/ammo box 四类访问器里**逐处显式判空**，无 NPE 面。

---

## 6. 跨模块发现（只记录 + 已 send_message 给 lead，不在本任务改）

| # | 级别 | 位置（不在我的作用域） | 内容 | 建议动作 |
|---|---|---|---|---|
| ① | **已定案** | `item/AmmoBoxItem.java` | 弹药盒「物品栏点进/点出」（R7-I-3）。**Lead 裁决：走 mixin，不做 GUI**（上游该交互没有界面，做界面等于发明上游不存在的机制）。item 侧 `AmmoBoxItem#tryStackedOnOther` 已落地（§3.3）；`mixin/common/ContainerSlotClickMixin`（`Container#slotClick` HEAD + `mixins.tacz.json` 登记）由 **r7-platform** 写，契约见 §11 | 无需再裁；等 r7-platform 的 mixin 落地后实机验证 |
| ② | P3 | `api/item/nbt/GunItemDataAccessor.java:232-245` + `util/datafixer/AttachmentIdFix` | 旧配件 id 不会迁移（R7-I-4）。修法同 r4-C-4：在 `getAttachmentTag` 取到 `tag` 后调 `AttachmentIdFix.updateAttachmentIdInTag(tag)`（`getCompoundTag` 返回 gun NBT 内的**引用**，就地修复、幂等；`getAttachmentTag` 是 `getAttachmentId` 的唯一入口）。归属 r7-api | lead 转派 r7-api |
| ③ | P3 | `command/sub/HideTooltipPartCommand.java:43` | 注释写「`item/GunTooltipPart` 目前仍是 1.20.1 签名」——**已过期**：`GunTooltipPart` 已是 1.7.10 形态（`NBTTagCompound`/`Constants.NBT`），命令侧调用的 `setHideFlags(stack,int)` 签名未变。归属 r7-core | 转派删/改注释 |
| ④ | P3 | `entity/EntityKineticBullet.java:684,694-699`（`spawnParticle("lava"/…)` 服务端路径）、`block/entity/StatueBlockEntity.java:73`、`debug/GunMeleeDebug.java:40` | 同一类问题：**服务端调 `World#spawnParticle` 是 no-op**（`WorldManager.java:29-33`）。`EntityKineticBullet` 已有一半走 `func_147487_a`（`:519,715-717`），但 `:684`/`:694` 与 `StatueBlockEntity`、`GunMeleeDebug` 仍是 no-op 调用 ⇒ 那些粒子实机看不到。归属 r7-gun / r7-block / r7-core | 各 owner 按 `EntityKineticBullet:705-719` 的口径改 |
| ⑤ | 记录 | `r5/nbt-sync.md:109` | r5 报告写「上游 `GunSmithTableMenu` 同样有『配方只能合成一次』缺陷」——**该判断有误**：上游 `doCraft` 用的是 `recipe.getResultItem(...)`（`GunSmithTableMenu.java:157`），而 `getResultItem` 恒 `copy()`（`GunSmithTableRecipe.java:50-52`）。上游无此缺陷；r5 的修复本身是对的（移植侧当时用 `getOutput()`，返回的是内部栈） | 归档修正，无需改代码 |

---

## 7. 未修 + 有据降级（平台差异，逐条给依据）

| # | 项 | 依据 / 为什么是「有据降级」而不是「没做完」 |
|---|---|---|
| ① | 弹药盒 `overrideStackedOnOther` | **已不再是降级项**：Lead 裁决走 mixin。item 侧逻辑已按上游逐分支搬到 `AmmoBoxItem#tryStackedOnOther`（§3.3），mixin 由 r7-platform 落地（契约 §11）。1.7.10 的 `Container#slotClick`（`Container.java:142`）确实没有任何物品自定义回调，所以 mixin 是唯一不发明新机制的落法 |
| ② | `verifyTagAfterLoad` 缺失 | 1.20 `Item` 的 NBT 加载后钩子，1.7.10 `ItemStack.loadItemStackFromNBT` 无钩子点 ⇒ 只能「读取时懒修复」，见 §6-② |
| ③ | `GunTooltipPart#getHideFlags` 兜底 = 0 | 上游回退 `Item#getDefaultTooltipHideFlags`（1.8+ Forge API）；1.7.10 无该 API，且本模组没有任何物品覆写它（全仓 0 处）⇒ 兜底 0 与上游实际行为一致 |
| ④ | `ItemClientHooks#nameColorProvider` / `registerRenderer` 空转 | 1.7.10 无「物品名文本染色」API、客户端渲染器已按物品身份注册；保留字段是为了不破坏 `ClientSetupEvent` 的注入契约（r2/r5 已定案），不影响行为 |
| ⑤ | 弹药盒耐久条 | `getBarColor` 无法表达（1.7.10 条色由填充比例决定）；`getBarWidth` 的 13 段量化由原版渲染承担。**观感差异**：上游条恒绿色，1.7.10 从绿到红渐变 |
| ⑥ | 弹药盒 / 配件 / 弹药 / 枪械的图标 | 上游 `builtin/entity`（3D）+ `ammo_statue` model predicate；1.7.10 用单张静态 `IIcon` 兜底（`AmmoBoxItem:78`、`AttachmentItem:55`、`AmmoItem:52`、`ModernKineticGunItem:127`），按 NBT 切外观需 `IItemRenderer`（L2）。贴图逐个确认真实存在（如 `assets/tacz/textures/items/ammo_box.png`），不会出 MISSING_ICON |
| ⑦ | 近战药水的 `hideParticles` | 1.7.10 `PotionEffect` 无该位（1.8+ 才有） |
| ⑧ | `MobEffect` 注册表 → `Potion` 静态表 | 1.7.10 药水是 `Potion.potionTypes[id]`，只有原版 23 种；`getPotionFromId` 按 `ResourceLocation` 路径映射，模组药水返回 null 跳过（`ModernKineticGunItem:709-744` 的 javadoc + 映射表）。默认包 `melee.effects` 只用原版名 |
| ⑨ | 近战击退「双层」 | 1.7.10 `EntityLivingBase#attackEntityFrom` 内部会**自己**调 `knockBack(...)`（`EntityLivingBase.java:1050-1069`，按伤害量），而上游的显式 `target.knockback(...)` 是独立机制、必须翻译 ⇒ 移植侧 = 上游显式击退 **+** 1.7.10 内置击退。无法在不破坏 `attackEntityFrom` 的前提下消掉后者，记录为平台差异（手感上比上游略强） |
| ⑩ | 近战粒子：`DAMAGE_INDICATOR`→`"crit"`、广播 32 格→16 格 | 1.7.10 无该粒子类型（`RenderGlobal#doSpawnParticle` 名字表里没有）；`func_147487_a` 固定 256 平方距离 |
| ⑪ | `AmmoStorageInventory` 的 `IInventory` 化 | 无 `simulate`（槽级写恒真写）、`getInventoryStackLimit` 固定 64、`getStackInSlot` 可能返回 >64 的聚合栈；**已知精度差**：外部自动化（漏斗）按「64/格」搬运时，若整库容量已满，`setInventorySlotContents` 的超容部分会被丢弃（`IInventory` 没有「按槽动态上限」这个信息，无法回告调用方）。正式入口是方块 GUI。已在类注释登记 |
| ⑫ | `GunSmithTableRecipe` 不是 `IRecipe` | 1.7.10 `CraftingManager#findMatchingRecipe` 会对列表内每一条强转 `IRecipe` 再 `matches`，把工作台配方塞进去必 `ClassCastException`；改为静态登记表 + `GunSmithTableSerializer#deserializeAndRegister` |
| ⑬ | 采矿 tag → 矿词/回退物品 的折算表 | 1.7.10 无 item tag，只有 `OreDictionary`；r4 已逐条论证（含 `nuggetIron` 9:1 折算、GT/IC2 环境优先走原矿词） |
| ⑭ | `AmmoBoxItem.getStatue` 签名缩减 | 上游待 `ClientLevel/LivingEntity/seed`（model predicate 用），1.7.10 无 predicate ⇒ 只保留 `stack`；**当前 0 调用点**（R7-I-7），等 L2 渲染器接入 |
| ⑮ | `GunSmithTableMenu#requestRecipeSelection/consumeRecipeSelection` 的写端 0 调用点 | 上游这两个方法是给 JEI 的 `IRecipeTransferHandler` 用的；1.7.10 的 `compat/jei/GunModPlugin` 按 AGENTS.md 决策 #2 只保留**反射探测骨架**（只用 `Class.forName` 探 `mezz.jei.api.IModPlugin`，不注册分类/转移器）⇒ 「JEI 里点配方自动填料」在 1.7.10 未接入。属有据降级，不是死代码（`GunSmithTableScreen:395-398` 是读端，`GunSmithTableMenu:146-155` 是 API 面） |

---

## 8. 前几轮结论复核（仍在 / 已订正）

| 历史结论 | 复核结果 | 证据 |
|---|---|---|
| r5 P0：工作台「每个配方只能合成一次」 | ✅ **修复仍在且正确**。`GunSmithTableRecipe#getOutput` 返回副本（`:80-84`）+ `GunSmithTableMenu#doCraft` 取产物后 `splitStack` 前不再需要额外 copy（r5 当时加的 `result = result.copy()` 也已并入 `getOutput`，两处都不丢 NBT——1.7.10 `ItemStack#splitStack` 会 `stackTagCompound.copy()`） | `inventory/GunSmithTableMenu.java:246-263`、`crafting/GunSmithTableRecipe.java:76-84` |
| r5：NBT 键层「基本健康，无单侧键」 | ✅ 复核：本包相关的 33 个键**全部两侧齐全**（§5）；`GunLevelExp` 无写端 = 上游同 | §5 |
| r4-C-4：`AttachmentIdFix` 死代码 | ✅ 仍在（R7-I-4 → §6-②） | `item/AttachmentItem.java:111-114` |
| r4：6 个改装网络包字段/校验顺序等价 | ✅ 复核 `ClientMessageCraft`/`ClientMessageAmmoStorageClick` 与我这两个 Menu 的**契约**（类名、方法签名、`windowId` 校验）仍在且一致 | `network/message/ClientMessageCraft.java:50-64`、`ClientMessageAmmoStorageClick.java:72-87` |
| r6：tooltip 富文本 + 配件图标网格不回退 | ✅ `TooltipEvent:188-…`、`AttachmentGunGridOverlay` 仍挂在 `DrawScreenEvent.Post`；本任务未触碰 | — |
| r6：方块物品 GUI 图标/`BlockItemTransforms` 修复 | ✅ 未触碰（`item/` 里属 r7-block 的 6 个文件一字未改） | `git status` |
| r4/r5 曾认为「上游 `doCraft` 同缺陷」 | ⚠ **订正**：上游用 `getResultItem`（恒 copy），无该缺陷（§6-⑤） | 上游 `GunSmithTableMenu.java:157`、`GunSmithTableRecipe.java:50-52` |

---

## 9. 覆盖清单（诚实）

**逐行读完（25 文件）**
- `item/`：`ModernKineticGunItem`、`ModernKineticGunScriptAPI`、`AmmoItem`、`AttachmentItem`、`AmmoBoxItem`、`GunTooltipPart`、`ItemClientHooks`
- `inventory/`：`AmmoStorageInventory`、`AmmoStorageMenu`、`GunSmithTableMenu`、`AmmoStorageEntry`、`GuiHandler`、`GuiId`、`tooltip/*`（5 个）
- `crafting/`：`GunSmithTableRecipe`、`GunSmithTableIngredient`、`GunSmithTableSerializer`、`AmmoStorageUpgradeRecipe`、`GunPegboardDyeRecipe`、`OreDictionaryFallback`、`package-info`、`result/*`（2 个）
- `api/item/nbt/`（**只读**，属 r7-api）：`GunItemDataAccessor`、`AmmoBoxItemDataAccessor`、`AmmoItemDataAccessor`、`AttachmentItemDataAccessor`、`ItemStackNbtHelper`
- 上游对应 22 个文件全部逐行读过并做过方法级 diff（§4）

**只核接口/调用面（未逐行）**
- `util/SlotAdapterHelper`、`resource/modifier/AttachmentPropertyManager`、`AttachmentCacheProperty`（r4 已逐行审计，本轮只确认 item 侧调用签名未漂移）
- `block/entity/AmmoStorageBlockEntity`（只读容量/`stillValid`/`insertAmmo` 段）、`block/AmmoStorageBlock`（只读容量常量）
- `client/init/ClientSetupEvent`（只读 `registerItemRenderers`/`registerItemNameProviders` 段）
- `network/NetworkHandler:347-362`、`ServerMessageAmmoStorageContents`、`ClientMessageCraft`、`ClientMessageAmmoStorageClick`（只核与本包 Menu 的契约）

**没读**
- `client/gui/AmmoStorageScreen`、`GunSmithTableScreen` 的绘制细节（r7-gui 作用域）
- `util/GunAmmoUnloadHelper` 的完整装入算法（只核到「它能往弹药盒写 `AmmoCount`」，`:142-160`）
- `compat/jei/**`（`requestRecipeSelection`/`consumeRecipeSelection` 的上游消费方；1.7.10 侧接线属 r7-core）

---

## 10. 实机验证清单

**本轮修复的验证（§3.1 / §3.2）**

1. **弹药库取出弹药**（R7-I-1）：开弹药库 → 左键点某条目 → **鼠标上应出现**弹药图标；再左键背包空格应把弹药放进去（服务端/客户端一致）；重复「取出→放下」10 次，统计背包总弹药数**不增不减**；右键点条目应只取半组（`amount/2 + amount%2`）。
2. **弹药库存入弹药**（R7-I-1/2a）：鼠标上拿起 1 个弹药 → 左键点同种条目 → 弹药消失（`stackSize` 归 0）且**鼠标上不再残留图标**；拿 5 个点同种条目同理；盒子/库存计数每次 +1（或 +5）。
3. **枪械近战**（R7-I-2b/c）：给枪（可附魔的创造模式枪）附「火焰附加 I」→ 对僵尸近战 → 目标应**着火**；对猪/僵尸近战 → 目标身上应出现 **"crit" 粒子**且**附近第二个玩家也能看到**（这是 `func_147487_a` 广播是否生效的判据）；对**铁傀儡**（击退抗性 1.0）近战 → 位移应显著小于对猪（击退未衰减 = 修复没生效）。
4. **后坐力推挤的姿势分支**（R7-I-8）：找一把 `gun_data` 里带 `recoil_push`（含 `crawl_strength`）的枪，分别在 **① 陆地站立 / ② 陆地潜行 / ③ 陆地趴下（crawl）/ ④ 水里疾跑** 四种状态各开一枪，感受推挤位移：③ 应明显不同于 ②（crawl 值）；**④ 必须与 ②/① 同一档（地面值），不能出现 crawl 那档的推挤**（修复前 ④ 会走 crawl）；另可用 `/tacz debug` 或临时日志打印 `onGround/sneaking/crawling` 三个布尔确认 ④ 为 `false/false/false`（水中 onGround 通常 false ⇒ 实际取 air 值，也与上游一致）。

**依赖 r7-platform 的 mixin 落地后才能验证（§3.3 / §11）**

5. **弹药盒物品栏右键交互**（R7-I-3）：手持空弹药盒 → 右键点一堆同类弹药 → 盒内 `AmmoCount` 增加、该格相应减少、**有 `random.click` 音效**；容器已满时**不再搬**；手持已装弹的盒子 → 右键点空格 → 空格出现该种弹药（数量 = `min(index.stackSize, 盒内余量)`）、盒内计数减少、有 `random.pop` 音效；取空后盒子的 `AmmoId` 变回 `tacz:empty`（tooltip 回到「空盒」）；创造弹药盒右键任何弹药 → `AmmoCount = Integer.MAX_VALUE`；**手持盒子右键其它物品（如镐子）应什么都不发生**（mixin 返回 false 交回原版，盒子不会被塞进去）；**多人环境二次验证**：两个客户端同时点同一格不应出现「两边都拿到弹药」（服务端权威 + 事务确认）。

**回归（不要因本轮改动回退）**

6. **工作台连续合成**（r5 P0 修复）：同一条配方连做 3 次，材料每次扣、产物每次出，产物 NBT（`GunId`/`FireMode`/`CurrentAmmoCount`/内置配件）完整。
7. **弹药库界面数据**：GUI 里「已用/总量」两根进度条 + 条目列表都要有（列表空 = `ContentSender` 没注入，`NetworkHandler:358` 掉了）；关界面再开、切维度再开都正常。
8. **弹药盒 tooltip/耐久条**：装弹后鼠标悬停显示「内含弹药×N」文字行（r6 tooltip 修复）+ 耐久条比例随剩余量变化；创造弹药盒发光且无耐久条。
9. **枪械名/配件名/弹药名显示**：应为枪包 lang 里的名字（不是 `item.tacz.xxx` 原始 key）——验证 `ItemClientHooks.itemNameProvider` 仍生效。

**需实机才能定论（静态无法证）**

10. 近战「双层击退」（§7-⑨）的实际手感是否可接受；若偏强，可考虑只在 `strength > 0` 时把我们的位移改为 `0` 以完全交给 1.7.10 内置击退（会丢掉 json 里 `melee.knockback` 的独立语义，需产品裁决）。
11. `AmmoStorageInventory` 的超大聚合栈（`getStackInSlot` 可能 >64）被漏斗/其它模组读取时的表现（§7-⑪）。

---

## 11. AmmoBox 物品栏交互（mixin 方案落地）

### 11.1 结论

Lead 裁决：**不做 GUI**，走 mixin。理由与上游一致——上游 `Item#overrideStackedOnOther`（`tmp/TACZ-1.20.1/.../item/AmmoBoxItem.java:102-180`）就是「手持弹药盒在物品栏里右键点格子」这一个动作，1.20 里没有对应界面；给它做 `Container`+`GuiHandler` 等于发明上游不存在的机制。

本任务负责的 **item 侧已完成**（`AmmoBoxItem#tryStackedOnOther`，见 §3.3），mixin 侧由 r7-platform 写。

### 11.2 接口契约（item 侧 ↔ mixin 侧，已与 Lead / r7-platform 对齐）

```java
// src/main/java/com/tacz/guns/item/AmmoBoxItem.java
public static boolean tryStackedOnOther(EntityPlayer player, int slotId, int clickedButton, int mode)
```

- 调用点：`@Mixin(Container.class)` 的 `slotClick(int slotId, int clickedButton, int mode, EntityPlayer player)` **HEAD**，`cancellable = true`。
- 返回 `true` → `ci.cancel(); return player.inventory.getItemStack();`
  （返回「修改后的手持栈」是硬要求：1.7.10 客户端 `PlayerControllerMP#windowClick`（`PlayerControllerMP.java:475-481`）把 `slotClick` 的返回值当 `clickedItem` 发进 `C0EPacketClickWindow`；服务端 `NetHandlerPlayServer#processClickWindow`（`:949` 起）拿自己的返回值与之比对，相等才 confirm 并 `updateHeldItem()`。返回别的东西会让事务永远失配、每点一次就整包重同步。）
- 返回 `false` → 不 cancel，原版 `slotClick` 照常跑（= 上游 `overrideStackedOnOther` 返回 false 时走 `doClick` 普通放置/交换的语义）。
- 方法内已自带前置过滤（`player/slotId/mode/clickedButton/手持栈是否 AmmoBoxItem/槽位越界/非 InventoryPlayer 槽位`），mixin 侧**不需要**再判，直接调即可。
- 两个副作用都由 item 侧自己完成，mixin 不需要再碰槽位/音效：槽位内容用 `slot.putStack/decrStackSize`、音效用 `EntityPlayer#playSound`。
- 需要 `mixins.tacz.json` 登记该 mixin 类（r7-platform 的流程）。

### 11.3 客户端预测 + 服务端权威（mixin 必须两端都生效）

1.7.10 的 `Container#slotClick` 在**客户端**也会执行（`PlayerControllerMP#windowClick` 先本地跑、再发包），所以本方法会被两侧各调一次：客户端跑 = 预测，服务端跑 = 权威。方法只读「手持栈 + 目标槽」这两份两侧一致的状态、不做随机、不发网络包，因此两侧结果一致（`slotStackLimit`/`canTakeStack`/`isItemValid` 对玩家槽位在两侧取值相同）。

### 11.4 与上游的已知等价性边界（静态可证）

- **音效**：`EntityPlayer#playSound` → `World#playSoundToNearExcept`（`EntityPlayer.java:421-424`）⇒ 服务端经 `WorldManager#playSoundToNearExcept`（`WorldManager.java:56-59`）发 `S29PacketSoundEffect` 给**附近其它**玩家；客户端本地播放给自己。两侧各一次 = 所有人各听到一次，与上游 `Player#playSound` 等价。
- **`safeTake` 的 `allowModification` 分支**：1.20 `Slot#tryRemove` 对特殊槽位（`allowModification == false`）有额外拒绝，1.7.10 无此概念；本方法已把作用域限制在 `slot.inventory instanceof InventoryPlayer`，等价于普通玩家槽位，不会踩到该分支。
- **`setByPlayer` vs `putStack`**：1.20 的 `setByPlayer` 额外触发「玩家主动放置」统计/比较，1.7.10 只有 `putStack` + `onSlotChanged`（`inventory.markDirty`）；对玩法无影响（上游该处也没有成就/触发器）。
- **索引缺失的怪癖**：上游在存入分支用 `ifPresent` —— 弹药索引缺失时**什么都不搬，但仍 `playInsertSound` 且 `return true`**。本实现逐行保留（不修上游行为），且**不会**因此丢件（盒内计数未变、格内未减）。

