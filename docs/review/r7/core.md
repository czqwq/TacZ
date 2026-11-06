# r7-core · 注册 / 配置 / 命令 / 兼容 / 掉落 / 成就 / 入口 上游等价性审查

> Owner：`r7-core`（共享任务板 `task-23`）。写作用域：`init/**`、`config/**`、`command/**`、`compat/**`、
> `loot/**`、`advancement/**`、`debug/**`、入口 `GunMod` / `CommonProxy` / `client/ClientProxy`、本报告。
> 上游准绳：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）。
> 1.7.10 平台准绳：`build/rfg/minecraft-src/java/**`（反编译源码）、`tmp/angelica-2.2.42-src/**`（1.7.10 光影实现）。
> 纪律：**未运行任何 gradle 任务**；改动文件 `lint_files(min_severity="error")` = **0 error**（`get_file_problems` 复核）。
> 工作区有 r5/r6 与并行 teammate 的未提交改动，属基线；本报告只把 **r7-core 本轮自己的改动** 计入「已修」。

---

## 1. 结论速览

| 维度 | 结论 |
|---|---|
| **注册清单（`init/**`）** | 上游 29 项注册（13 个 `DeferredRegister` 内容 + 4 组 mod 总线事件 + 命令/兼容/capability + 数据驱动项）逐项对账，**无静默缺失**；11 项有等价落地、6 项为有据降级/空实现（每项都写明等价物在哪）。见 §4。 |
| **初始化顺序 / 生命周期** | 上游「构造器 → `FMLCommonSetupEvent` → `FMLLoadCompleteEvent` → `InterModEnqueueEvent` → `RegisterCommandsEvent`」→ 1.7.10「`GunMod` → `CommonProxy.preInit` → `init` → `postInit` → `serverStarting`」，顺序逐条对应；两处时序差异（Compat 探测从 `InterModEnqueue` 后移到 `postInit`、`LOAD_COMPLETE` 置位从 `FMLLoadComplete` 前移到 `postInit`）均不影响语义（§5）。 |
| **配置** | **89 键**机检（13 个定义类，另 4 个 `config/util` 读取器）：键名 / 默认值 / 取值范围与上游**逐项一致**（仅 `Lists.newArrayList` → `new ArrayList` 写法差）；`ConfigManager.saveAll`（r5 F-1）与 `PreLoadConfig.save`（r5 F-2）挂钩**复核仍在且正确**；本轮修掉 CLIENT 配置的重复加载路径（`ConfigManager.initClient` 原为死代码）。 |
| **命令** | 8 个子命令 + `debug pegboard`：命令名 / 参数名 / 参数顺序 / 权限（root=2）/ 输出语言 key / 副作用与上游逐字一致；`/tacz overwrite` 反向语义保留且补了落盘；本轮修 `pegboard` 压测的 `setBlock` flag（3→上游 `UPDATE_CLIENTS`=2）。 |
| **兼容层（`compat/**`）** | 13 个骨架全部「探测 + 缺失静默」；反射目标逐个核对（**Angelica 源码级核对**：`IrisApi`/`ShadowRenderingState`/`AngelicaConfig.enableIris` 全部命中；`FullyBufferedMultiBufferSource` Angelica 不存在 → `endBatch` 恒 false，已记录）。无硬依赖、无类加载期崩溃路径。 |
| **战利品注入** | `loot/LootTableInjectorModifier` 的「1.20 GLM → 1.7.10 静态注入器」语义核验：支持默认数据用到的全部子集（`rolls/entries:item/weight/functions:set_count+set_nbt`）；箱子注入是**有据近似**（期望值等价、非逐字），实体/方块是真·追加。见 §9。 |
| **成就 / 调试** | `advancement/**` 2 个 trigger 降级为 Forge 事件广播（有调用点、有记录）；`debug/GunMeleeDebug` 本轮修真 bug（服务端 `World#spawnParticle` no-op → `WorldServer#func_147487_a`）。 |
| **本轮最严重 3 条（均已修）** | ① **`interact_key` 数据包 tag 失效 → 交互键功能对默认数据整体不可用**（1.7.10 无 tag 系统；修前 `canInteractBlock/Entity` 恒 false，只有用户手写配置才生效）；② **伤害来源未映射 `exhaustion`**（上游 4 个 damage_type json 都是 0.05，1.7.10 默认 0.3 ≈ 6 倍饥饿消耗）且多了上游没有的 `is_projectile` 标志（保护类附魔会额外生效）；③ **`BULLET_RESISTANCE` 未设 `shouldWatch`**（上游 `.setSyncable(true)`）→ 客户端恒读 0。 |

**本轮改动 8 个文件**（全部 lint error=0）：

| 文件 | 级别 | 一句话 |
|---|---|---|
| `config/util/InteractKeyConfigRead.java` | P1 | 内置上游默认 tag 白名单（方块/实体）+ 判定顺序对齐 + 实体 id 走 `EntityIdHelper` 候选 |
| `init/ModDamageTypes.java` | P2 | 覆写 `getHungerDamage()=0.05`（上游 exhaustion）；删除上游没有的 `setProjectile()`；虚空伤害降级注释写实 |
| `init/ModAttributes.java` | P2 | `BULLET_RESISTANCE.setShouldWatch(true)`（对齐上游 `setSyncable(true)`） |
| `client/ClientProxy.java` | P2 | 枪包同步失败按上游断开连接；`getEntityById` 只依赖客户端世界；CLIENT 配置改走 `ConfigManager.initClient`（消死代码） |
| `init/ModBlocks.java` | P3 | 展示柜物品补上游 `tooltip.tacz.gun_display_case` |
| `debug/GunMeleeDebug.java` | P2 | 服务端粒子广播改用 `WorldServer#func_147487_a`（`sendParticles` 等价） |
| `command/sub/PegboardStressCommand.java` | P3 | `setBlock` flags 3→2（上游 `Block.UPDATE_CLIENTS`） |
| `command/sub/HideTooltipPartCommand.java` | P3 | 删除过期 TODO（`GunTooltipPart` 已是 1.7.10 签名） |

---

## 2. 逐文件对照表

### 2.1 入口（3 文件）

| 上游 | 本仓 | 判定 |
|---|---|---|
| `GunMod()` 构造器：`registerConfig(COMMON/SERVER/CLIENT)` → `packType` → 13×`DeferredRegister.register(bus)`（顺序 blocks→tile_entities→tabs→items→entity_types→recipe_serializers→recipe_types→loot_modifier_serializers→container→sounds→particle_types→attributes→paintings）→ kubejs → `registerDefaultExtraGunPack()` → `AttachmentPropertyManager.registerModifier()` | `GunMod`（`@Mod`/`@SidedProxy`/四段生命周期转发 + `instance`/`Tags.VERSION`）+ `CommonProxy.preInit` 逐条同位重排；顺序见 `CommonProxy.java:93-151` | ✅ 顺序一致；kubejs 分支在 1.7.10 无库（`compat/kubejs` 骨架，见 §8） |
| `init/CommonRegistry` 4 个 mod 总线事件 | `CommonRegistry.commonSetup()`（`FMLCommonSetupEvent`）/`markLoadComplete()`（`FMLLoadCompleteEvent`）/`registerAttributes()`+`EntityConstructing`（`EntityAttributeModificationEvent`）/preInit 里 `GunPackLoader.reloadPackList()`（`AddPackFindersEvent`） | ✅ 等价；坩埚交互 2 项降级（见 §4） |
| `init/CompatRegistry#onEnqueue(InterModEnqueueEvent)` | `CommonProxy.postInit → CompatRegistry.register()/enqueueCompat()`（+ Angelica 探测） | ✅ 时序差异记录（§5.2） |
| `init/CommandRegistry#onServerStaring(RegisterCommandsEvent)` | `CommonProxy.serverStarting → CommandRegistry.registerCommands(FMLServerStartingEvent)` | ✅ |
| — | `GunMod.serverStopping → CommonProxy.serverStopping → ConfigManager.saveAll()`（r5 F-1 修复，本轮复核仍在） | ✅ |
| — | `client/ClientProxy`（上游无代理；`@SidedProxy` 必需）：`preInit` 配置/钩子/枪包资源包 → `init` 注册面 → `postInit` 重载监听/语言注入 | ✅ 本轮 3 处修复见 §3 |

### 2.2 `init/**`（16/16 文件）

| 文件 | 上游对应 | 判定 |
|---|---|---|
| `ModBlocks` | 12 方块 + 6 BE 类型 + `BULLET_IGNORE_BLOCKS` 标签 | ✅ 12+6 逐一对齐；标签 → `util/block/BlockRayTrace` 硬编码（类注释已记录）；本轮补展示柜物品 tooltip |
| `ModItems` | 17 物品 + `RegisterEvent` 里 `GunItemManager.registerGunItem` | ✅ 17/17；方块物品经 `Item.getItemFromBlock` 取回（1.7.10 ItemBlock 与 Block 共用数字 id，`Item.java:97-100` + `GameData.registerItem:817-847`，可安全取回）；gun type 注册在 `register()` 尾部 |
| `ModCreativeTabs` | 16 个 `CreativeModeTab`，`withTabsBefore` 链序 = other→ammo→scope→muzzle→stock→grip→extended_mag→laser→pistol→sniper→dmr→rifle→shotgun→smg→rpg→mg | ✅ 16/16 同序（1.7.10 自定义 tab 按构造顺序扩容）；标题 key 规则正确（`tab.` 前缀 → `itemGroup.`，`tacz.type.*` 原样）；内容懒求值 |
| `ModEntities` | `EntityType`×2 | ✅ trackingRange 换算 5 区块=80 格 / 8 区块=128 格（`EntityTracker.java:89-93`），updateInterval=5/3，velocity=false/true 与上游 Builder 一致 |
| `ModRecipe` | 3 个 recipe serializer + 2 个 recipe type + 18 个配方 json | ✅ 17/18 → 代码注册（14 shaped/shapeless + 3 custom），`RecipeSorter` 已登记；第 18 条 `misc/blood_strike_1.json` 为「1.20 画变体」不可表达（r4-02 §5.1 已裁决，`ModRecipe.java:308-315` 注释已纠正）；详见 r4-02（本报告不重复逐条配方） |
| `ModDamageTypes` | 4 个 `DamageType` + `BULLETS_TAG` | ⚠ 本轮修 2 处（exhaustion / projectile），虚空 tag 降级记录（§3-F2） |
| `ModAttributes` | `RangedAttribute(...).setSyncable(true)` | ⚠ 本轮补 `setShouldWatch(true)`（§3-F3） |
| `ModCreativeTabs`（同上） | | |
| `ModContainer` | 2 个 `MenuType` | ✅ 空实现 + 等价物 `GuiHandler`（common 侧 preInit 注册）/`GuiId`；类注释完整 |
| `ModSounds` | 2 个 `SoundEvent` | ✅ 字符串常量 + `sounds.json`（r5 已验证 2 个 key）；`SoundPlayManager`/`TargetBlock` 有真实调用点 |
| `ModParticles` | 1 个 `ParticleType` | ✅ 空实现 + 等价物 = `client/particle/HitBlockEffectHandler` 直接 `addEffect`；`ParticleFactoryRegistry` 的 no-op 已记录 |
| `ModLootModifiers` | 1 个 GLM serializer | ✅ 空实现 + 等价物 = `loot/LootTableInjectorModifier`（静态注入器）+ `event/LootInjectionEvent` 事件接线（§9） |
| `ModPainting` | 1 个 `PaintingVariant` | ✅ 有据降级：1.7.10 `EntityPainting.EnumArt` 硬编码 26 项，无法新增；保留资源 id + info 日志 |
| `CommonRegistry` | 见 §2.1 | ✅ |
| `CompatRegistry` | 见 §2.1 | ✅ |
| `CommandRegistry` | 见 §2.1 | ✅ |
| `CapabilityRegistry` | `RegisterCapabilitiesEvent`（`DataHolder` capability） | ✅ 空实现：1.7.10 无 capability；实体同步改静态管理器（`entity/sync/core/SyncedEntityData`），类注释写明第三方只能直连 |

### 2.3 `config/**`（27 文件）

| 分组 | 判定 |
|---|---|
| 基础设施 `TaczConfigSpec` / `ConfigBuilder` / `ConfigValue` + 6 个 typed value | ✅ 语义核对：`push/comment/define/defineInRange/defineEnum/defineListAllowEmpty/pop` ↔ 1.7.10 `Configuration`；`reload()` 重新登记 Property（1.7.10 `Configuration#load` 会重建 category）、`set()` 走 `Property#setValue`（置 `changed`）、`save()` = `Configuration#save`（无条件写盘，首启默认值会落盘）。偏差：list 逐元素 validator 无等价物（保留签名、不参与过滤，注释已写）。 |
| `ConfigManager` | ✅ COMMON→SERVER→CLIENT 顺序；`afterCommonLoad`/`afterServerLoad` 对应上游 `LoadingConfigEvent` 两分支；`saveAll` 由 `serverStopping` 调；`reloadAll` 无调用者（r5 F-4 已登记）。**本轮**：CLIENT 加载统一走 `initClient`（原为 0 调用死代码，`ClientProxy` 自己又写了一份） |
| 定义类 13 个（COMMON 3 / SERVER `SyncConfig` 1 / CLIENT 5 / PRELOAD 1 + common/client 3） | ✅ 机检 89 键：键名 + 默认值 100% 一致（`define/defineInRange/defineEnum/defineList*` 逐条 Compare-Object 仅 `Lists.newArrayList` 写法差）；`PreLoadConfig` 文件扩展名 `.toml→.cfg` 已记录 |
| `config/util/*Read` 4 个 | ✅ `AmmoBlockConfigRead`（`#forge:glass` 等 tag → 内置展开表）、`HeadShotAABBConfigRead`（正则同上游）、`GunPegboardItemConfigRead`（tag 全空，列表 + 类型判定）、`InteractKeyConfigRead`（**本轮修**，§3-F1） |

### 2.4 `command/**`（12 文件）

| 上游子命令 | 本仓 | 参数名/权限/返回 | 判定 |
|---|---|---|---|
| `attachment_lock <target> <AttachmentLock:bool>` | `AttachmentLockCommand` | 一致 / 继承 root=2 / 返回 cnt | ✅ |
| `debug <enable:bool>` + `debug pegboard …` | `DebugCommand` + `PegboardStressCommand` | 一致 / 2 | ✅ 字面量/参数两分支分发正确；DEBUG 消息英文字面量与上游一致 |
| `dummy <target> <dummyAmount:int>=0` | `DummyAmmoCommand` | 一致 | ✅ |
| `overwrite <enable:bool>` | `OverwriteCommand` | 一致；`override.set(!enable)` 反向语义保留；r5 补 `PreLoadConfig.save()` 复核仍在 | ✅ |
| `reload` | `ReloadCommand` | 客户端→客户端回调（`ClientProxy` 注入 `ClientAssetsManager::reloadAllPack`，反射）、专用服务端→`CommonAssetsManager.reloadAllPack`；计时 μs/1000 与上游同式 | ✅ |
| `hide_tooltip_part <target> <mask:int>=0` | `HideTooltipPartCommand` | 一致；`GunTooltipPart.setHideFlags` 调用有效 | ✅（本轮修过期注释） |
| `convert` | `ConvertCommand` | 仅客户端（side 判断），转换器由 `ClientProxy` 注入 `PackConvertor::convert` | ✅ |
| `config <key:enum> <state:bool>` | `ConfigCommand` | 3 个枚举名/语言 key 逐字一致；写 `SyncConfig` 内存 + 服务器停止统一落盘 | ✅ |
| `ListPackCommand`（上游空壳） | 同 | 上游未挂进命令树，本仓保持空壳 | ✅ |
| — | `RootCommand`（`CommandBase`，权限 2，`args[0]` 分发 8 子命令 + 一级 tab 补全） | | ✅ |
| — | `CommandTargets`（移植特有：`EntityArgument.entities()` → `PlayerSelector`；`@e` 退化为当前维度全部活体） | | ✅ 有据降级（类注释完整） |

### 2.5 `compat/**`（13 文件，上游 70 文件）

| 骨架 | 上游机制 | 1.7.10 处置 | 判定 |
|---|---|---|---|
| `cloth/MenuIntegration` | Cloth Config 屏幕 | 无 1.7.10 Cloth；探测 + 反射缺库提示页；「配置界面扩展点」= 1.7.10 `IModGuiFactory`（编译期声明，未接） | ✅ 有据降级（L2 TODO） |
| `oculus/OculusCompat` | Oculus/Iris API（按版本分派 legacy/newly） | 纯反射探测 `net.irisshaders|net.coderbot` 两套包名 + Angelica；`isRenderShadow()`/`isUsingRenderPack()` 有真实调用点 | ✅ **源码级核对**（§8.2）；`endBatch` 恒 false（Angelica 无 `FullyBufferedMultiBufferSource`，文档已写） |
| `oculus/IUnflushableWrapper` | Iris `UnflushableWrapper` accessor mixin | 接口保留（`tacz$getWrapped` 名不漂移）；1.7.10 无注入目标，无 mixin 引用 | ✅ 有据降级 |
| `carryon/BlackList` | `InterModComms.sendTo("carryon","blacklistBlock", ...)` 遍历 tacz 方块 | `GameData.getBlockRegistry().getKeys()` + `FMLInterModComms.sendMessage` | ✅ 真接线（上游 1.20 行为等价；未装 CarryOn 无副作用） |
| `ar/ARCompat` + `BedrockPartLightingMode` | Accelerated Rendering（1.20 专属） | 门面保留 + 恒中性值（`LOADED=false`）；3 个 AR 绑定实现类已删 | ✅ 有据降级（1.7.10 无该模组/无等价 API） |
| `controllable/ControllableCompat` | Controllable 手柄绑定/震动 | `init` 探测；`onGunShoot` 反射 `Controllable.getController().rumble(low,high,ms)`（兜底参数与上游一致） | ✅ 有据降级（无 1.7.10 版） |
| `shouldersurfing/ShoulderSurfingCompat` | SS `Perspective.SHOULDER_SURFING && !FREE_LOOK.isDown()` | 反射同语义，失败 false | ✅ 有据降级 |
| `playeranimator/PlayerAnimatorCompat` + `AnimationName` | Player Animator 动画层（8 文件） | 保留全部公开签名；`canPlayAnimation` 是真逻辑完整移植（`EntityStateHelper` 推导 pose）；其余无库分支中性 | ✅ 有据降级；`canPlayAnimation` 与上游逐条件对齐 |
| `kubejs/TimelessKubeJSPlugin` | KubeJS 插件（19 文件） | 探测 + `postKubeJSEvent` 恒「未处理」+ `registerGunType` 转发 `GunItemManager`；`init()` 无调用点（1.7.10 无 KubeJS） | ✅ 有据占位 |
| `jei/GunModPlugin` | JEI 插件（14 文件） | 探测 + `getPluginUid`；1.7.10 无 JEI（等价物 NEI 是另一套 API） | ✅ 有据占位 |
| `optifine/OptifineCompat` | 类探测 2 方法（上游 0 调用点） | 2 方法保留 + 新增 `isShadersEnabled/isShaderPackLoaded` 真探测（当前 0 调用点） | ✅ 记录（新增方法无风险，见 §6） |

### 2.6 `loot/**`（1 文件）、`advancement/**`（2 文件）、`debug/**`（1 文件）

| 文件 | 判定 |
|---|---|
| `loot/LootTableInjectorModifier` | ✅ 上游 `LootModifier#doApply` → 静态注入器；子集实现覆盖默认枪包用到的全部 JSON 结构（`pools[].rolls` 数字/区间、`entries[].{type:item,name,weight,functions}`、`set_count` 数字/区间、`set_nbt` 解析）；不支持项（`conditions`/`empty`/`loot_table`/`tag` entry/其它 function）类注释已列。**事件接线**（箱子/实体/方块）属 `event/LootInjectionEvent`（r7-gun scope），本报告 §9 只核语义 |
| `advancement/TargetHitTrigger` / `GunRefitTrigger` | ✅ 有据降级：1.7.10 无 criterion/advancement 体系 → Forge 事件广播（`TargetHitEvent`/`GunRefitEvent`），调用点保留（`TargetBlock`/`TargetMinecart`/5 个 `ClientMessage*`）；`register()` 空实现按上游顺序被 `CommonRegistry.commonSetup` 调用 |
| `debug/GunMeleeDebug` | ⚠ 本轮修（§3-F6） |

---

## 3. 本轮修复（8 文件，file:line 证据）

| # | 级别 | 现象 / 根因 | 证据（本仓 ↔ 上游/平台） | 修法 |
|---|---|---|---|---|
| **F1** | **P1** | **`interact_key` 数据包 tag 失效 → 交互键对默认数据整体不可用**：`canInteractBlock/canInteractEntity` 只查 4 条默认全空的配置列表（`SyncConfig.java:74-89`）。手持枪械按交互键开箱/上矿车/交易、以及「按 O 交互」HUD 对全部默认目标恒不触发 | 上游 `config/util/InteractKeyConfigRead.java:23-26,46-53,65-72` 在配置列表外还查 `BlockTags/EntityTypeTags`；默认数据**非空**：`data/tacz/tags/blocks/interact_key/whitelist.json`（37 项：buttons/beds/anvil/signs/chest/furnace/crafting_table/ender_chest/lever/smoker… + 11 个 tacz 方块）与 `.../entity_types/interact_key/whitelist.json`（minecart/chest_minecart/item_frame/boat/villager/horse/mule…）。消费方 `client/input/InteractKey:104,119`、`client/gui/overlay/InteractKeyTextOverlay:100,155` | `config/util/InteractKeyConfigRead.java`：① 把默认 tag 内容按 1.7.10 可表达性内置（方块按类型：`BlockChest/Workbench/Furnace/EnchantmentTable/BrewingStand/EnderChest/Hopper/Beacon/Lever/Anvil/Bed/FenceGate/Sign/Button` + 木门/木活板门按 `Material.wood`；tacz 11 个方块按注册名集合；实体按类：`EntityMinecartEmpty/Chest/ItemFrame/Boat/Horse/Villager`）；② 判定顺序保持上游「配置黑名单 → 默认黑名单（空）→ 配置白名单 → 默认 tag 白名单」；③ 实体 id 候选统一走 `util/EntityIdHelper.getConfigCandidates`（见 §7 依赖）。1.7.10 不存在的 1.20 项（barrel/blast_furnace/smithing_table/shulker_boxes/hanging_signs/bell、camel/glow_item_frame/interaction/chest_boat/wandering_trader）在类注释逐项登记 |
| **F2** | **P2** | **伤害类型语义两处偏离**：① 上游 4 个 damage_type json 都是 `"exhaustion": 0.05`，本仓 `BulletDamageSource` 未映射 → `DamageSource.hungerDamage` 默认 **0.3**，被子弹打中一次扣 6 倍饥饿；② 上游 damage_type **不在** `is_projectile` tag，本仓 `setProjectile()` 却让「弹射物保护」附魔类型 4 对子弹额外生效 | 上游：`data/tacz/damage_type/{bullet,bullet_ignore_armor,bullet_void,bullet_void_ignore_armor}.json` 均 `exhaustion:0.05`、`scaling:never`；`data/minecraft/tags/damage_type/{bypasses_armor,bypasses_invulnerability}.json` 是 TACZ 仅有的两个 damage_type tag 覆盖（**无** `is_projectile`）。平台：`DamageSource.java:31,131,164-184`（默认 hunger 0.3；`getHungerDamage` 在 131；`setDamageBypassesArmor`/`setDamageIsAbsolute` 会把它清零）、`EntityPlayer.java:1234`（唯一消费者 `addExhaustion(getHungerDamage())`）、`EnchantmentProtection.java:74`（`isProjectile()` 只影响保护附魔 type 4） | `init/ModDamageTypes.java:157-164` 覆写 `getHungerDamage()=0.05F`（4 种伤害一视同仁，与上游 4 份 json 一致）；删除 `setProjectile()` 并写明依据；`BULLET_VOID*` 的 `bypasses_invulnerability` 无 1.7.10 等价标志 → 保留 `absolute+magic` 降级并注释「默认数据 `use_void_damage_on` 为空标签、不可达」（§6） |
| **F3** | **P2** | `BULLET_RESISTANCE` 未同步：1.7.10 只有 `getShouldWatch()==true` 的属性进 `ServersideAttributeMap#getWatchedAttributes` 随属性包下发；上游是 `.setSyncable(true)`，本仓漏设 → 客户端侧读到的抗性恒默认值 | 上游 `init/ModAttributes.java:14`（`.setSyncable(true)`）；平台 `ServersideAttributeMap.java:60,80` + `BaseAttribute.java`（`shouldWatch` 默认 false） | `init/ModAttributes.java:38-44` 加 `.setShouldWatch(true)`（1.7.10 `setSyncable` 等价物） |
| **F4** | P3 | 枪械展示柜物品缺上游 tooltip `tooltip.tacz.gun_display_case`（上游注册时匿名 `BlockItem#appendHoverText`；本仓裸 `ItemBlock`） | 上游 `init/ModItems.java:47-52`；lang key 在 `assets/tacz/lang/en_US.lang:85`；1.7.10 对应钩子 `Item#addInformation`（`Item.java:739-740`） | `init/ModBlocks.java:113-135` 新增 `GunDisplayCaseBlockItem extends ItemBlock` 覆写 `addInformation`，`registerBlock` 改用它（`GameRegistry.registerBlock` 反射构造 `(Block)` 构造器 ✓） |
| **F5** | P2 | **枪包同步失败只打日志**，上游直接踢出连接（否则双方枪包数据不一致、后续注册 id/贴图全错位） | 上游 `network/message/ServerMessageSyncGunPack.java:104-107`（`RECEIVER.clear(); log; connection.disconnect(...)`）；1.7.10 等价物：`NetHandlerPlayClient#getNetworkManager()`（`NetHandlerPlayClient.java:1865`）+ `NetworkManager#closeChannel(IChatComponent)`（`NetworkManager.java:261-267`） | `client/ClientProxy.java:475-489`：catch 分支追加 `Minecraft#getNetHandler()` → `getNetworkManager().closeChannel(new ChatComponentText("Failed to synchronize gun pack data"))`；handler 为 null 时静默跳过（不覆盖上游主路径） |
| **F6** | P3 | `getEntityById` 以 `thePlayer != null` 为前提：世界已创建但本地玩家实体尚未落地的早期几 tick，S2C 实体事件包（gun draw/hurt/kill/updateEntityData）会被静默丢弃 | 上游 `ClientLevel#getEntity(int)` 只要求 level 非空；消费方 `ServerMessageUpdateEntityData:70`、`ServerMessageGunDraw:51`、`AbstractShooterGunEventMessage:70`、`ServerMessageGun{Hurt,Kill}:80-85/75-81` | `client/ClientProxy.java:373-380`：改为使用 `Minecraft#theWorld`（`World#getEntityByID`，`World.java:3575`），world 为 null 才返回 null |
| **F7** | P2 | **服务端 `World#spawnParticle` 是 no-op**：`WorldServer` 的 `IWorldAccess` 是 `WorldManager`，其 `spawnParticle` 空实现 → 上游 `ServerLevel#sendParticles(FLAME,…)`（近战范围调试）在 1.7.10 端完全看不见 | 平台 `WorldManager.java:29`（空实现）、`WorldServer.java:1121-1139`（`func_147487_a` 打包 `S2APacketParticles` 广播）；上游 `debug/GunMeleeDebug.java:34` `serverLevel.sendParticles(...)` | `debug/GunMeleeDebug.java:11-15,21-25,44-49`：`!isRemote && instanceof WorldServer` 后按点调 `func_147487_a("flame", x,y,z, 1, 0,0,0, 0)`（参数序 = name,x,y,z,count,dx,dy,dz,speed） |
| **F8** | P3 | `debug pegboard` 放挂板用 `World#setBlock(..., 3)`；上游是 `Block.UPDATE_CLIENTS`（=2，只下发给客户端、不触发邻块更新），flag 3 多了邻块更新 | 平台 `World.java:547-563`（`markAndNotifyBlock`：`flag&2`=markBlockForUpdate、`flag&1`=notifyBlockChange）；上游 `PegboardStressCommand.java:81-85`（`level.setBlock(pos, state, Block.UPDATE_CLIENTS)`） | `command/sub/PegboardStressCommand.java:234-239`：flag 改 2 + 注释写明映射 |
| **F9** | P3 | 两处「注释与事实不符」/重复路径：`HideTooltipPartCommand` 的 TODO 仍称 `GunTooltipPart` 是 1.20.1 签名；`ConfigManager.initClient()` 0 调用者而 `ClientProxy` 自己复制了一份 CLIENT 配置加载 | `item/GunTooltipPart.java:32-44`（已是 `net.minecraft.item.ItemStack` 签名）；`ConfigManager.java:45-52`（方法 javadoc 声称由 `ClientProxy#preInit` 调用） | `HideTooltipPartCommand.java:42-44` 删 TODO 改事实；`client/ClientProxy.java:162-165` 改 `ConfigManager.initClient(...)`（行为逐字节等价：`ClientConfig.init(file).load(); spec.save()`） |

---

## 4. 注册清单差集（上游 `init/**` 的每一条注册 vs 本仓落地）

> 说明：「缺失=功能死代码」在这里**不存在**：上游每一条注册/事件都在本仓有对应行或明确的等价物。

| # | 上游注册（GunMod 构造器 / CommonRegistry / 各 init 类） | 本仓等价落地 | 状态 |
|---:|---|---|---|
| 1 | `registerConfig(COMMON/SERVER/CLIENT)` | `ConfigManager.initCommon`（preInit，`CommonProxy.java:96`）+ `ConfigManager.initClient`（`ClientProxy.java:165`） | ✅ |
| 2 | `GunPackLoader.INSTANCE.packType` | `GunPackLoader.packType = GunPackType.current()`（`CommonProxy.java:100`） | ✅ |
| 3 | `ModBlocks.BLOCKS`（12 方块） | `ModBlocks.register()`（`GameRegistry.registerBlock`） | ✅ 12/12 |
| 4 | `ModBlocks.TILE_ENTITIES`（6 BE 类型） | `GameRegistry.registerTileEntity` ×6，注册名同上游 | ✅ 6/6 |
| 5 | `ModCreativeTabs.TABS`（16 tab） | `ModCreativeTabs.register()`，同序 | ✅ 16/16 |
| 6 | `ModItems.ITEMS`（17 物品） | `ModItems.register()` | ✅ 17/17 |
| 7 | `ModEntities.ENTITY_TYPES`（2） | `EntityRegistry.registerModEntity` ×2 | ✅ 2/2 |
| 8 | `ModRecipe.RECIPE_SERIALIZERS`（3）+ `RECIPE_TYPES`（1） | 无注册表：17 条 `IRecipe` 直接注册 + `RecipeSorter` 2 条（`ModRecipe.java:89-100`）；`GunSmithTableRecipe` 走静态表（`resource/manager/GunSmithTableRecipeManager`） | ✅ 等价 |
| 9 | `ModLootModifiers.LOOT_MODIFIER_SERIALIZERS`（1） | `loot/LootTableInjectorModifier` 静态注入器 + `event/LootInjectionEvent` | ✅ 等价 |
| 10 | `ModContainer.CONTAINER_TYPE`（2） | `GuiHandler` + `GuiId`（`CommonProxy.java:150` 注册） | ✅ 等价 |
| 11 | `ModSounds.SOUNDS`（2） | 字符串常量 + `sounds.json`；`SoundPlayManager`/`TargetBlockEntity` 消费 | ✅ 等价 |
| 12 | `ModParticles.PARTICLE_TYPES`（1） | 空实现；`client/particle/HitBlockEffectHandler` 直接 `addEffect` | ✅ 等价（类注释记录） |
| 13 | `ModAttributes.ATTRIBUTES`（1） | `IAttribute` 常量 + `EntityConstructing` 挂载（`ModAttributes.Handler` + `CommonRegistry#onEntityConstructing` 双保险幂等） | ✅（本轮补 sync 标志） |
| 14 | `ModPainting.PAINTINGS`（1） | 空实现 + `BLOOD_STRIKE_1` 资源 id 常量 + info 日志 | ⚠ 有据降级 |
| 15 | `FMLJavaModLoadingContext` 上 kubejs 插件注册 | `compat/kubejs/TimelessKubeJSPlugin` 骨架（无 1.7.10 KubeJS） | ⚠ 有据占位 |
| 16 | `registerDefaultExtraGunPack()` | `GunMod.registerDefaultExtraGunPack()`（preInit 调，`CommonProxy.java:127`） | ✅ |
| 17 | `AttachmentPropertyManager.registerModifier()` | `CommonProxy.java:131` | ✅ |
| 18 | `FMLCommonSetupEvent`: `NetworkHandler::init` | `CommonRegistry.commonSetup()` 首项 | ✅ |
| 19 | 同上：`ModSyncedEntityData::init` | 同 | ✅ |
| 20 | 同上：`TargetHitTrigger::register` / `GunRefitTrigger::register` | 同（空实现 + 事件广播） | ✅ 降级 |
| 21 | 同上：`CauldronInteraction.WATER.put(GUN_PEGBOARD/GLOW_GUN_PEGBOARD, DYED_ITEM)` | 无对应：`BlockCauldron#onBlockActivated` 写死（类注释 + TODO 已记录） | ⚠ 有据降级 |
| 22 | `FMLLoadCompleteEvent` → `LOAD_COMPLETE=true` | `CommonRegistry.markLoadComplete()`（postInit 调） | ✅（时序 §5） |
| 23 | `EntityAttributeModificationEvent` → 全实体加属性 | `EntityConstructing` 逐个 `registerAttribute`（等价） | ✅ |
| 24 | `AddPackFindersEvent` → `GunPackLoader` 作为 pack source | `GunPackLoader.reloadPackList()`（preInit 显式）+ `GunPackResourcePack.ensureInstalled()`（client preInit） | ✅ 等价 |
| 25 | `InterModEnqueueEvent`（Cloth/Oculus/CarryOn） | `CompatRegistry.enqueueCompat()`（postInit；+ Angelica） | ✅（时序 §5） |
| 26 | `RegisterCommandsEvent` | `FMLServerStartingEvent` | ✅ |
| 27 | `RegisterCapabilitiesEvent(DataHolder)` | 空实现；`SyncedEntityData` 静态管理器 | ✅ 等价 |
| 28 | `ModItems#onItemRegister` → `GunItemManager.registerGunItem` | `ModItems.register()` 尾部调用 | ✅ |
| 29 | `data/tacz/**` 数据（damage_type 4、loot modifier 1、tags 10 类、recipes 18、loot_tables 9、advancements 23） | 逐类见下：recipes→代码注册（17/18）；loot_tables→1.7.10 方块默认自掉落（r7-block 核）；advancements→事件广播降级；tags→按类核查（§4.1）；damage_type→`ModDamageTypes` | 见 §4.1 |

### 4.1 上游 `data/tacz/tags/**` 逐类核对（1.7.10 无 tag 系统）

| tag | 默认内容 | 本仓落地 | 判定 |
|---|---|---|---|
| `blocks/bullet_ignore` | iron_bars / #fences / #fence_gates / #leaves | `util/block/BlockRayTrace#isVanillaIgnoredBlock` 按类型硬编码 | ✅ |
| `blocks/interact_key/whitelist` | 37 项（见 F1） | **本轮**内置默认白名单 | ✅ 已修 |
| `blocks/interact_key/blacklist` | 空 | 无需落地 | ✅ |
| `entity_types/interact_key/whitelist` | minecart/chest_minecart/item_frame/boat/mule/villager/horse/wandering_trader/glow_item_frame/camel/interaction/chest_boat | **本轮**内置 1.7.10 可表达子集 | ✅ 已修 |
| `entity_types/interact_key/blacklist` | 空 | — | ✅ |
| `entity_types/use_magic_damage_on` | enderman | `EntityKineticBullet#createDamageSources` 硬编码末影人（r7-gun） | ✅ |
| `entity_types/use_void_damage_on` | 空 | 无需落地（`Sources.bulletVoid` 保留但不可达，F2 注释） | ✅ |
| `entity_types/pretend_melee_damage_on` | 空 | 无需落地 | ✅ |
| `entity_types/thermal_imaging/{whitelist,blacklist}` | 空 / armor_stand（**1.7.10 无盔甲架**） | `util/ThermalImagingHelper` 两张代码集合（默认空 = 上游语义等价） | ✅ |
| `items/gun_pegboard/{whitelist,blacklist}` | 空 / 空 | `GunPegboardItemConfigRead` 列表 + TACZ 物品类型判定 | ✅ |
| `damage_type/bullets` | 4 个子弹伤害类型 | `ModDamageTypes.isBulletDamage`（`instanceof BulletDamageSource`） | ✅ |

---

## 5. 初始化顺序 / 生命周期对照（失败隔离）

### 5.1 阶段映射

| 上游时机 | 本仓落点 | 内容与顺序 |
|---|---|---|
| `GunMod()` 构造器（mod 构造期） | `GunMod` 构造器（只置 `instance`）+ `CommonProxy.preInit` | 配置 → packType → blocks → tabs → items → entities → container → sounds → attributes → particles → loot modifiers → paintings → attributes 事件注册 → capability（空）→ 默认枪包导出 → 枪包扫描 → 属性 modifier → 资源事件 → 事件总线（Forge/FML 双总线）→ GUI handler |
| `FMLCommonSetupEvent`（enqueueWork 6 项） | `FMLInitializationEvent → CommonProxy.init → CommonRegistry.commonSetup()` | `NetworkHandler.init → ModSyncedEntityData.init → TargetHitTrigger.register → GunRefitTrigger.register`（坩埚 2 项降级）；随后 `ModRecipe.register()`（依赖矿词已注册，含 `RecipeSorter`——排序在 `FMLLoadCompleteEvent` 跑，必须在此之前，已核 `ForgeModContainer.java:295-300`） |
| `FMLLoadCompleteEvent` | `FMLPostInitializationEvent → CommonProxy.postInit → markLoadComplete()` | `LOAD_COMPLETE` 置位（唯一消费者 `SyncedEntityData.java:101`，玩家登录前必然已置位 ⇒ 语义等价） |
| `InterModEnqueueEvent` | `CommonProxy.postInit → CompatRegistry.register()` | 其它模组构造完成后探测；`Loader.isModLoaded` 自 preInit 起可用 ⇒ 探测结果一致。差异：比上游晚一个阶段（postInit vs init 并行阶段），对「探测 + 注册回调」无影响 |
| `RegisterCommandsEvent` | `FMLServerStartingEvent` | 每世界启动 new `RootCommand`（`CommandHandler` 每世界重建，不重复注册） |
| 服务端停止落盘（Forge `ConfigTracker`） | `FMLServerStoppingEvent → ConfigManager.saveAll()`（r5 F-1） | 复核仍在：`GunMod.java:92-95`、`CommonProxy.java:195-197`、`ConfigManager.java:68-78` |

### 5.2 失败隔离

- 所有 `Mod*#register()` 均由 `CommonProxy.preInit` 顺序直调，无 try/catch —— 与上游 DeferredRegister 在构造期失败即崩的语义一致（配置/注册表错误应显式暴露，不吞）。
- `compat/**` 全部反射 + `invokeOptional`（`ClassNotFoundException` 静默、其它 `Throwable` warn），不阻断启动。
- `client/ClientProxy` 对跨工作流事件类用反射构造 + warn 跳过（缺类不影响客户端启动）。
- 枪包加载失败隔离属 `resource/**`（r7-respack-core scope），本报告不覆盖。

---

## 6. 未修 / 有据降级清单

| # | 项 | 级别 | 处置与理由 |
|---:|---|---|---|
| 1 | `CauldronInteraction.WATER`（挂板用水洗色）| 差异 | 1.7.10 `BlockCauldron#onBlockActivated` 硬编码支持物品，无注册表；要实现只能 mixin/`PlayerInteractEvent` 模拟（新增机制），`CommonRegistry.commonSetup()` 已记 TODO 待裁决 |
| 2 | `ModPainting` 自定义画变体 | 差异 | 1.7.10 `EntityPainting.EnumArt` 硬编码 26 项，无注册表；保留资源 id + info 日志 |
| 3 | `interact_key` tag 的**枪包自定义**内容 | 差异 | 默认数据已内置（F1）；第三方枪包对 `data/<ns>/tags/**` 的追加无法加载（1.7.10 无 tag 系统），类注释已记录 |
| 4 | Cloth Config 配置界面扩展点 | 差异 | 1.7.10 对应物是 `IModGuiFactory`（需编译期声明在 `mcmod.info`/`@Mod(guiFactory=)`），未接；`KeyModifier.ALT+T` 默认键降级为未绑定（1.7.10 `KeyBinding` 无修饰键概念，占 T 会顶掉原版聊天键）——两点均在 `compat/cloth/MenuIntegration`、`client/input/ConfigKey` 类注释 |
| 5 | `ConfigManager.reloadAll()` 无调用者 | 信息 | r5 F-4 已登记（1.7.10 无配置文件监视器；接入 `/tacz reload` 有「客户端用本地文件覆盖服务端同步值」回归风险） |
| 6 | `ARCompat`/`ControllableCompat`/`ShoulderSurfingCompat`/`PlayerAnimatorCompat`/`KubeJS`/`JEI` 的真实功能 | 差异 | 均无 1.7.10 对应模组/API：保留同名门面 + 探测 + 中性值，不引入硬依赖（AGENTS 决策 2）；`canPlayAnimation`/`onGunShoot` 等无库依赖部分已真移植 |
| 7 | `OptifineCompat.isShadersEnabled/isShaderPackLoaded` 当前 0 调用点 | 信息 | 上游 `OptifineCompat` 本身 0 调用点（工具类）；1.7.10 OptiFine 存在且反射目标真实（`net.optifine.Config#isShaders`、`Shaders#shaderPackLoaded`），保留无风险；未来渲染降级路径可直接用 |
| 8 | `TimelessKubeJSPlugin.init()` / `postKubeJSEvent()` 0 调用点 | 信息 | 1.7.10 无 KubeJS；`postKubeJSEvent` 的 L2 接点（`api/event/common/KubeJSGunEventPoster`）已在类注释指明 |
| 9 | `CommandTargets` 的 `@e` 语义 | 差异 | 1.7.10 只有玩家选择器（`PlayerSelector`）；`@e` 退化为「当前维度全部活体」，类注释完整（上游 `type=`/`nbt=`/`scores=` 无法表达） |
| 10 | 箱子战利品注入的「期望等价」而非「逐字等价」 | 差异 | 1.7.10 箱子是「加权池抽 N 个」，无法在结算后追加；权重推导（`w=V/C` + 抽取数 +K ⇒ 期望命中 K）写在 `event/LootInjectionEvent.refreshChestInjections()` 注释；默认数据 = 新世界奖励箱必定期望 1 枪 + 1 弹药（上游是必得，实际是概率近似）——已在 §9 明确 |
| 11 | `ModRecipe` 注册时机（init）与模组化矿词注册顺序 | 信息 | 上游配方来自 datapack（所有 mod 初始化完成后）；1.7.10 在 `FMLInitializationEvent` 注册，若其它模组在更晚的 init 里注册 `ingotCopper` 等矿词，`OreDictionaryFallback` 已回退铁锭（不影响默认枪包：工作台配方在服务端启动时由 resource 层解析，那时矿词已全）。仅 `ammo_workbench` 一条受影响，r4-02 已裁决 |
| 12 | `ModLootModifiers`/`ModParticles`/`ModSounds`/`ModContainer`/`CapabilityRegistry` 的空 `register()` | 信息 | 均为有据空实现（每类 javadoc 写明 1.7.10 无对应注册表 + 等价物在哪），非半成品 |

---

## 7. 跨模块发现（不在本 scope，已/需 msg Lead 或对应 owner）

| # | 位置 | owner | 结论 / 建议 | 状态 |
|---:|---|---|---|---|
| 1 | 实体注册名 → 1.20 id 解析：`EntityList.getEntityString` 对 FML 模组实体返回 `"modid.name"`（`EntityRegistry.java:164`），不是 `"modid:name"` | r7-util / r7-gun / r7-input | 应统一走 `util/EntityIdHelper`（r7-util 已新建；`getConfigCandidates` 同时给 1.20 id 与旧形态）。**`config/util/InteractKeyConfigRead` 已改用它（本仓首次跨 scope 依赖，公开名契约见 §7.1）**；`util/EntityUtil`、`util/ThermalImagingHelper`、`client/event/RenderHeadShotAABB`、`event/LootInjectionEvent` 已由对应 owner 改完（review 时已复核） | ✅ 已闭环 |
| 2 | **Lead 转派的 4 处「服务端 spawnParticle no-op」核对结论**：只有 `debug/GunMeleeDebug` 是**真 bug**（上游 `ServerLevel#sendParticles` = 服务端广播）。`block/entity/StatueBlockEntity:81` 与 `entity/EntityKineticBullet:315`（入水气泡）**不是 bug**：上游这两处分别是 `level.isClientSide` 分支 / `Level#addParticle`（1.20 `addParticle` 本身就是客户端专用），1.7.10 `World#spawnParticle` 的服务端 no-op + 客户端生成恰好等价；`StatueBlockEntity` 注释（`WorldServer#func_147487_a` 只发 16 格、比客户端自渲染更差）成立 | r7-gun / r7-block | **不要改**为 `func_147487_a`；已在报告 §3-F7 与本节留档 | 已核 |
| 3 | `GunDisplayCaseBlockItem` 使 `GUN_DISPLAY_CASE` 物品类从裸 `ItemBlock` 变为嵌套子类 | r7-item / r7-block | 仍 `instanceof ItemBlock`；渲染器按 Item 实例注册（`ClientSetupEvent:248`），无影响 | 信息 |
| 4 | `config/util/InteractKeyConfigRead` → `util/EntityIdHelper` 编译期依赖 | r7-util → r7-core | r7-util 已确认 `getConfigCandidates(Entity)`/`getModernId(Entity)` 签名不漂移（其报告 §3-1 已登记跨 scope 契约） | ✅ |
| 5 | 1.7.10 的 `EntityKineticBullet` 水下 bubble 粒子（r7-gun scope） | r7-gun | 见 #2：与上游 `addParticle` 等价，无需修改 | 信息 |
| 6 | `docs/PORT_STATUS.md` 中 `init/config/command/compat` 段落的过时描述（如「PegboardStressCommand 未动」等） | Lead | 本报告不回写 PORT_STATUS（文档归 Lead）；如需同步请按本报告 §4/§6 更新 | 待裁 |

### 7.1 跨 scope 依赖登记（编译期契约）

- `config/util/InteractKeyConfigRead.java` → `util/EntityIdHelper#getConfigCandidates(Entity)`（r7-util scope）。
  r7-util 已确认本轮及后续不改名/改签名；`InteractKeyConfigRead` 同时被 `client/input/InteractKey`、`client/gui/overlay/InteractKeyTextOverlay` 调用（签名未变）。

---

## 8. 兼容层反射目标核对（重点：Angelica/1.7.10 真实存在的实现）

### 8.1 `compat/oculus/OculusCompat`（唯一在 1.7.10 上「真的可能 LOADED」的兼容门面）

| 反射目标 | 1.7.10 实际类（Angelica 2.2.42 源码） | 判定 |
|---|---|---|
| `net.irisshaders.iris.api.v0.IrisApi` | `tmp/angelica-2.2.42-src/net/irisshaders/iris/api/v0/IrisApi.java`（`getInstance()` 静态接口方法 / `isShaderPackInUse()` / `isRenderingShadowPass()` 全命中） | ✅ |
| `net.coderbot.iris.shadows.ShadowRenderingState#areShadowsCurrentlyBeingRendered()` | `tmp/angelica-2.2.42-src/net/coderbot/iris/shadows/ShadowRenderingState.java:5-7` | ✅ 优先用它（最权威） |
| `net.coderbot.batchedentityrendering.impl.FullyBufferedMultiBufferSource` | **Angelica 无此类**（只有 `AngelicaBufferSource`） | `endBatch` 恒 false；且 1.7.10 的 `mcshim.MultiBufferSource` 不可能是 Iris 类型 —— 已文档化；调用方回退自带 endBatch |
| `com.gtnewhorizons.angelica.config.AngelicaConfig#enableIris` | `AngelicaConfig.java:69-71`（`@Config.DefaultBoolean(true) public static boolean enableIris`） | ✅ 作为 API 不可用时的回退 |
| 模组 id | `AngelicaMod.java:31` `MOD_ID="angelica"` | ✅ 与 `CompatRegistry.ANGELICA` 一致 |
| `UnflushableWrapperAccessor` mixin | 1.7.10 无注入目标，`mixins.tacz.json` 已不含该 mixin | ✅ 无类加载风险 |

### 8.2 其它骨架

- `invokeOptional` 的失败路径：`ClassNotFoundException` 静默、其它 `Throwable` warn → 无启动失败路径（`CompatRegistry.java:100-113`）。
- `MenuIntegration` 反射构造缺库提示页（`getDeclaredConstructor(GuiScreen.class)` + `setAccessible`）与 `ClothConfigScreen` 的 `protected` 构造器匹配 ✓；但 1.7.10 无 `IModGuiFactory` 挂点 ⇒ 提示页当前不可达（记录，不影响启动）。
- `BlackList` 的 `GameData.getBlockRegistry().getKeys()` 是 1.7.10 `FMLControlledNamespacedRegistry` 的公开方法（返回 key 集合，元素为 `ResourceLocation`）；IMC 使用 `FMLInterModComms.sendMessage`（语义等价 `InterModComms.sendTo`）。
- `PlayerAnimatorCompat.canPlayAnimation` 与上游逐条件一致（`IGun.getIGunOrNull(getHeldItem()) != null && pose != SLEEPING && !isOnLadder && pose != SWIMMING && pose != FALL_FLYING`）；`SWIMMING` 由 `EntityStateHelper` 用「在水 + 疾跑」推导（mcshim 层已记录）。

---

## 9. 战利品注入语义核验（`loot/**` + 消费方）

1. **默认数据**：`assets/tacz/custom/tacz_default_gun/data/tacz/tacz_loot_injectors/spawn_bonus_chest_taurus943.json` → `loot_tables: ["minecraft:chests/spawn_bonus_chest"]`，2 个 pool（taurus943 枪 + `22wmr` 弹药 38..45 发）。
2. **解析子集**：`LootTableInjectorModifier#createStacks` 支持 `pools[].rolls`（数字 / `{min,max}`）、`entries[]`（`minecraft:item` + `weight`）、`functions[]`（`set_count` 数字/区间、`set_nbt` 字符串 → `JsonToNBT`）——默认数据用到的结构 **100% 覆盖**；`weight` 缺省 1、`rolls` 缺省 1 与上游原版语义一致。
3. **落地钩子**（`event/LootInjectionEvent`，r7-gun scope）：箱子 → `ChestGenHooks` 候选池 + 抽取数；实体 → `LivingDropsEvent`；方块 → `HarvestDropsEvent`（均为真·追加语义）。箱子是唯一近似点：1.7.10 无法「结算后追加」，按「权重 `V/C` + 抽取数 +K ⇒ 期望命中 K」推导（`refreshChestInjections()` 注释含完整推导），默认数据下期望 1 枪 + 1 弹药/奖励箱（上游必得；差异已记录，见 §6 #10）。
4. `LootInjectionManager` 兜底数据源处理「新世界 `CreateSpawnPosition` 早于 `FMLServerStartingEvent`」的时序（`LootInjectionEvent` 类注释），`/tacz reload` 后由 `ServerTickEvent` 检测实例变化重建注入池 —— 无「注册了不加载/加载了不生效」断链。

---

## 10. 既有报告勘误

| # | 出处 | 原结论 | 更正 | 依据 |
|---:|---|---|---|---|
| 1 | `docs/review/r5/nbt-sync.md:109` | 「上游 `GunSmithTableMenu` 同样有『配方只能合成一次』缺陷」 | **误判**：上游 `doCraft` 用 `GunSmithTableRecipe#getResultItem(...)`（`tmp/TACZ-1.20.1/.../inventory/GunSmithTableMenu.java:157`），而 `getResultItem` 恒 `ItemStack.copy()`（`crafting/GunSmithTableRecipe.java:50-52`）⇒ 上游无该缺陷。r5 的修复本身正确（移植侧当时用 `getOutput()`，返回内部栈）。与 r7-item 报告 §5-⑤/§8-162 结论一致，本报告独立复核确认 | 上游源码 + `docs/review/r7/item.md:162,192` |
| 2 | Lead 转派「4 处服务端 spawnParticle no-op」 | 4 处均为 bug | 实际只有 `debug/GunMeleeDebug` 1 处是真 bug；`StatueBlockEntity`（客户端分支）与 `EntityKineticBullet` 气泡（上游 `addParticle` 客户端专用）**现状即等价**，不应改（见 §7 #2） | 上游源码 + 平台 `WorldManager.java:29`、`WorldClient` IWorldAccess |
| 3 | 本报告 §3-F1 与 r7-util `docs/review/r7/util.md §5-1` | 同一发现（interact_key tag 失效）由两个 scope 独立复核 | 已修；r7-util 已把该条改标「已由 r7-core 修复」，不作为待派工项 | 双方报告交叉引用 |

---

## 11. 实机验证清单（静态无法定论）

> 以下每条都给出「看什么/判读方法」，供集成测试按序执行。

1. **（F1 交互键）** 手持任意枪（默认枪包），准星对准：箱子 / 熔炉 / 工作台 / 附魔台 / 铁砧 / 铁栏杆旁的按钮 / 木门 / 告示牌 / 村民 / 马 / 矿车 / 物品展示框 → 屏幕中央应出现「按 O 交互」提示；按 O 应打开对应 GUI / 上马 / 上矿车。
   - 反例：对准 `tacz:target`（不在上游 tag）不应出现提示；`F3` 打开渲染调试后确认背后无异常。
   - 回归：在 `config/tacz-server.cfg` 的 `interact_key` 黑名单里加入 `minecraft:chest` 并 `/tacz reload`（或重启）后，箱子提示应消失（配置黑名单优先）。
2. **（F2 伤害）** 玩家满饥饿时被子弹命中 20 次：饥饿条消耗应约为 **1 点**（20×0.05），而不是约 6 点；穿甲/普通两段伤害各计一次 exhaustion（上游同式）。装「弹射物保护 IV」胸甲被子弹打 vs 不装，伤害减免幅度应与上游一致（不应出现「弹射物保护额外生效」）。
3. **（F3 属性同步）** 用其它模组/命令给玩家加 `BULLET_RESISTANCE` modifier（或 Lua 脚本修改），客户端侧读取该属性应等于服务端值（`/attribute` 类工具或调试输出），不是恒 0；`S20PacketEntityProperties` 日志/抓包可见该属性条目。
4. **（F4 tooltip）** 创造栏 OTHER 页签悬停「Gun Display Case」→ 应显示灰色 `Can be used to display your private collection, and is very secure.`（lang key `tooltip.tacz.gun_display_case`，zh_CN 等其它语种同样应显示译文）。
5. **（F5 同步失败踢出）** 人造条件：客户端与服务端枪包数据不一致（如仅服务端有非法枪包 json）→ 客户端应收到 `Failed to synchronize gun pack data` 断开提示并回主菜单（而不是静默继续进服）。
6. **（F6 早期实体包）** 进服/传送后最初几 tick 的枪械事件（开火音效起点、命中标记、枪械 HUD 蓄力）不应丢失；若仍丢，用 `-Dmixin.debug.verbose=true` 或临时在 `Hooks.getEntityById` 打日志确认 `theWorld` 状态。
7. **（F7 近战调试粒子）** `/tacz debug true` 后持枪对空气近战：服务端玩家应看到火焰粒子锥形（多人服里所有附近玩家都能看到）。
8. **（F8 pegboard 压测）** `/tacz debug pegboard 8 same`：挂板正常放下且填充；邻块（如已放置的红石/火把）**不应**因放置而被触发更新（上游 `UPDATE_CLIENTS` 语义）。
9. **（配置落盘，r5 项复核）** `/tacz config serverShootCooldownCheck false` → 关服 → `config/tacz-server.cfg` 中键值为 false；`/tacz overwrite true` → 关服 → `<gamedir>/tacz/tacz-pre.cfg` `[gunpack] DefaultPackDebug=true`（反向语义）。
10. **（兼容）** 不装 Oculus/Angelica：启动日志无 `Detected Angelica/Oculus`；装 Angelica 2.x + 光影包：日志出现 `Detected Angelica`，进游戏开镜/枪口粒子在 shadow pass 不应重复绘制/污染（`isRenderShadow()` 生效）。装 OptiFine 时 `OptifineCompat.isShadersEnabled()` 可被调试命令读出真值（当前无消费方）。

---

## 12. 覆盖清单（诚实）

- **逐文件读完**：`init/**` 16/16；`config/**` 27/27（含基础设施 10 + 定义 13 + util 4）；`command/**` 12/12；`compat/**` 13/13；`loot/**` 1/1；`advancement/**` 2/2；`debug/**` 1/1；`GunMod`/`CommonProxy` 全文；`client/ClientProxy` 全文（505 行，含 `Hooks` 全部 13 个方法）。
- **上游逐文件对读**：`init/**` 16/16、`config/**` 18/18、`command/**` 11/11、选读 `compat/**`（Oculus/Cloth/CarryOn/AR/Controllable/PlayerAnimator/ShoulderSurfing/Optifine/KubeJS/JEI 的门面与 Inner 类）、`loot/**` 1/1、`advancement/**` 2/2、`debug/**` 1/1、`network/message/ServerMessageSyncGunPack`、`data/**` 关键 json（damage_type 4 + tags 10 + loot modifier + interactive tags）。
- **机检脚本**：配置键/默认值 Extract+Compare-Object（9 个定义文件）；注册项清点；`@Override`/FQN/import 由 IDEA `lint_files` 兜底。
- **未做**：未运行 gradle/runClient；未做全服多人实机；Angelica 反射仅静态源码核对（未在运行时装载 Angelica 验证）；`config/**` 89 键的**注释文本**未逐字 diff（只核键/默认值/范围——r2-05 §D 已做过默认值机检，本轮独立复跑）。
