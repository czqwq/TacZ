# common-game 工作流约定（item / entity / event / block / inventory / crafting / 杂项）

> 面向：common-game 工作流内部（含委派的 subagent）。**先读** `AGENTS.md`、`docs/PORT_PLAN.md` §3/§4/§5、`docs/PORT_STATUS.md`。
> 上游源码：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（只读）
> 1.7.10 原版/Forge 源码：`build/rfg/minecraft-src/java/**`（**定位 API 时优先查这里**，别猜）

---

## 0. 铁律

1. **禁止跑任何 gradle 命令**（构建由主 Agent 统一执行）。
2. **只改自己分到的目录**。不要改 `init/`、`api/`、`resource/`、`util/`、`network/`、`config/`、`client/`、`mixin/`、`compat/`。
   需要别的包提供东西 → 写进 `docs/PORT_STATUS.md` 的「阻塞」段 + 汇报给 common-game（Lead）。
3. 上游 NBT key / JSON key / 类名 / 公开方法语义**不得改**（存档、资源包、外部模组兼容性）。
4. 文件里**不允许出现 1.20.1 专属 import**（`net.minecraft.core.*`、`net.minecraft.world.*`、`com.mojang.blaze3d.*`、
   `net.minecraftforge.registries.*`、`net.minecraftforge.eventbus.api.*` 等）。
5. **L1 边界（重要）**：本工作流的服务端包**不允许 import `com.tacz.guns.client.**`**。
   上游的客户端渲染/名称/粒子代码：删掉并留 `// TODO(port L2, client-port): 上游 <文件>:<行> 做的是……` 注释；
   需要客户端在 L2 接回来的地方，用「common 侧静态钩子字段」的形式预留（例子见 `item/ItemClientHooks`）。
6. 每改完一个文件用 IDEA MCP `get_file_problems` 自检：**自己文件里的错误**必须消灭；
   其它尚未移植的包（api/resource/mcshim/client）引发的未解析符号可以暂时存在，但必须是「按契约预期会存在」的类。

## 1. 全仓统一映射（lead 已裁决）

- `ItemStack.EMPTY` → **`null`**；判空 `stack == null || stack.getItem() == null`。
  读写 NBT 用 `com.tacz.guns.api.item.nbt.ItemStackNbtHelper`（common-api 提供：getOrCreateTag/isEmpty/hasTag）。
- fastutil **一律不用**：`it.unimi.dsi.fastutil.Pair` → `org.apache.commons.lang3.tuple.Pair`（`.getLeft()/.getRight()`），
  其余集合 → `java.util`。
- `LogicalSide` → `cpw.mods.fml.relauncher.Side`；事件对象里 `getLogicalSide()` **方法名保留**，返回 `Side`。
- 事件基类：`cpw.mods.fml.common.eventhandler.Event` / `@Cancelable` / `@SubscribeEvent`；注册走 `MinecraftForge.EVENT_BUS`。
- `ModList` → `cpw.mods.fml.common.Loader#isModLoaded`。
- `IItemHandler/ItemStackHandler` → `net.minecraft.inventory.IInventory`（玩家背包 `InventoryPlayer`）。
- 常用包名替换见 `docs/PORT_PLAN.md` §3.1（ResourceLocation/ItemStack/EntityLivingBase/World/Block/NBTTagCompound/Vec3/AxisAlignedBB/MovingObjectPosition…）。
- shim（`com.tacz.guns.mcshim.**`，由 platform-shim 提供）：
  - `Mth`：clamp/lerp/floor/ceil/abs/sin/cos/sqrt/frac/atan2/hsvToRgb/DEG_TO_RAD 等，签名同 1.20 `net.minecraft.util.Mth`；
  - `PacketBuffer`：1.20 `FriendlyByteBuf` 的等价物，方法名对齐 1.20（readUtf/writeUtf/readVarInt/readResourceLocation/readNbt/readItem…）；
  - 其它 shim 服务端基本用不到，不要引入。

### 事件/注册/容器（1.7.10 惯例）

- 上游 `@Mod.EventBusSubscriber` 类 → 改造成**普通类**，提供 `public static void register()`，内部
  `MinecraftForge.EVENT_BUS.register(new Xxx())`（实例方法）或 `register(Xxx.class)`（静态 @SubscribeEvent）。
  实例注册的类不要用 `@SideOnly`。汇总入口：`com.tacz.guns.event.EventHandlers.registerAll()`，由 CommonProxy 调用；
  **`event/` 之外不要自行注册事件**，统一经 `EventHandlers`。
- 物品：`Item` 构造器里 `setMaxStackSize(n)` / `setHasSubtypes(...)` / `setCreativeTab(...)`（unlocalized name 由注册处
  `GameRegistry.registerItem(item, name)` 处理）。
- 方块：`Block` 子类无参构造（个别带参数的见各自类注释），朝向等状态用 **metadata**；`hasTileEntity(int metadata)` +
  `createTileEntity(World, int)`；TileEntity 注册名 = `"tacz:" + 名字`，由 `init/ModBlocks` 统一注册。
- 容器：`Container`/`Slot`/`IInventory`；GUI id 常量在 `com.tacz.guns.inventory.GuiId`，打开 GUI 统一走
  `com.tacz.guns.inventory.GuiHandler.openGui(...)`（common-game 提供，CommonProxy 注册）。
- 实体：`EntityRegistry.registerModEntity`，注册由 `init/ModEntities` 统一做。

## 2. 跨包契约（已与各工作流对齐）

- `api/**`（common-api 负责，正在落盘）：类名/方法名保持上游；平台类型换成 1.7.10 对应物。
  与我们直接相关：`IGun/IAmmo/IAmmoBox/IAttachment/IAnimationItem/IBlock`、`AbstractGunItem`、`api/item/nbt/**`、
  `api/item/builder/**`、`api/entity/IGunOperator/ReloadState/ShootResult/ITargetEntity/KnockBackModifier`、
  `api/event/common/**`（构造器 `(EntityLivingBase, ItemStack, Side)`，post 用 `MinecraftForge.EVENT_BUS.post(event)`）、
  `api/event/server/AmmoHitBlockEvent`、`api/util/LuaEntityAccessor/LuaNbtAccessor`、`api/DefaultAssets/GunProperties/GunProperty/TimelessAPI`。
  `IGunOperator/KnockBackModifier.fromLivingEntity`：**与上游一致走 Mixin 方案**（common-api 已确认）：
  client-port 把 `mixin/common/LivingEntityMixin` 移植为 `@Mixin(EntityLivingBase) implements IGunOperator, KnockBackModifier`，
  内部 new 我们 `entity/shooter/**` 的类并驱动每 tick 的服务端状态机。因此我们 **不新增** 注册表/tick 事件，
  只保证 `entity/shooter/**` 的类名、构造器参数顺序、`ShooterDataHolder` 字段名与 `entity/sync/ModSyncedEntityData` 的 key 名不变。
- `resource/**`（common-resource 负责）：`CommonGunIndex/GunData/...` 的 getter 名与类型保持上游；
  `TimelessAPI` 的 common 侧索引方法保持。**JSON 解析器仍在上游类里（我们只改解析 API 调用）**。
  数据包（`data/tacz/recipes|advancements|loot_tables|tags`）在 1.7.10 无 datapack：由 common-resource 的枪包加载器
  提供「枚举 pack 内 data 目录原始 JSON」的入口（进行中）；在它落地前，需要数据加载的类先实现好解析/注册 API，
  加载接线留 `// TODO(port): 等 resource 层提供 data 文件枚举`，并把该依赖写进 PORT_STATUS 阻塞段。
- `init/**`（common-infra 负责，但**以下内容注册类由 common-game 直接改写**，common-infra 只调用 `register()`）：
  `ModItems / ModBlocks / ModEntities / ModContainer / ModSounds / ModParticles / ModAttributes / ModRecipe / ModLootModifiers`。
  CommonProxy 期望调用顺序：`ModBlocks.register()` → `ModItems.register()` → `ModEntities.register()` →
  `ModContainer.register()` → `ModSounds.register()` → `ModRecipe.register()` → 其它；`event/EventHandlers.registerAll()`。
  `CommonProxy`/`ClientProxy` 的 GUI handler 注册与 `GunMod.instance` 由 common-infra 提供。
- `client/**`（client-port 负责）：L1 我们不依赖；L2 渲染由它们通过 `MinecraftForgeClient.registerItemRenderer`
  等 1.7.10 钩子接回，钩子点见各文件 `TODO(port L2)` 注释与 `item/ItemClientHooks`。

## 3. 目录与委派

| 目录 | 负责人 | 说明 |
|---|---|---|
| `item/**`、`entity/EntityKineticBullet`、`entity/TargetMinecart`、`entity/sync/**` | common-game（Lead） | 物品逻辑、弹道实体、实体同步 |
| `entity/shooter/**` | subagent-shooter | IGunOperator 的各操作逻辑（Mixin 调用） |
| `event/**` | subagent-event | Forge 事件（统一经 `event/EventHandlers.registerAll()`） |
| `block/**` | subagent-block | Block+TileEntity+metadata |
| `inventory/**`、`crafting/**`、`advancement/**`、`loot/**`、`particles/**`、`sound/**`、`debug/**` | subagent-data | 容器/配方/成就/掉落/粒子/声音 |

## 4. 交付格式（每个包写完汇报）

1. 完成文件清单（相对路径）。
2. **设计决定与 1.7.10 差异**（尤其：状态/朝向怎么存 metadata、TileEntity 同步方式、配方如何注册、成就/掉落如何降级）。
3. **需要别的包提供的符号**（类名+方法签名，越具体越好）。
4. 需要在 `init/` 注册的东西（方块/物品/TileEntity/容器/实体/配方/声音）。
5. 遗留 TODO（带上游文件:行号）。
