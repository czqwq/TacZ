# TACZ 1.7.10 移植计划（工作流契约）

上游：`tmp/TACZ-1.20.1`（MC 1.20.1 / Forge 47 / Java 17）
目标：本仓库（MC 1.7.10 / Forge 10.13.4.1614 / Java 8 字节码 + jvmDowngrader）

源码已全量复制到 `src/main/java/com/tacz/guns/**`、`src/main/resources/**`，本计划说明「怎么把它变成能编译能跑的东西」，
以及**每条工作流负责哪些包、遵守哪些约定**。

---

## 0. 铁律

1. **禁止跑 gradle**（`gradlew` 任何任务）。构建由主 Agent 统一在全部任务结束后执行。
2. 自查用 IDEA MCP：`get_file_problems` / `lint_files` / `search_symbol` / `analyze_calls` / `read_file`。
3. **只改自己工作流的包**（见 §2 的写作用域）。别人包里的编译错误不是你的活，不要改别人的文件。
   跨包签名问题在 `docs/PORT_STATUS.md` 的「阻塞」段记录，由主 Agent 裁决。
4. 上游已有机制按上游形态实现；1.7.10 缺失的能力写 **shim** 或**记录差异**，不要发明第三种语义。
5. 每改完一个文件，用 `get_file_problems` 确认该文件自身不再有「未解析符号 / 未知方法」类错误
   （别的包引发的级联错误可以暂时存在）。
6. 产出硬性要求：**文件里不允许出现 1.20.1 专属 import**（`net.minecraft.core.*`、`net.minecraft.world.level.*`、
   `com.mojang.blaze3d.*` 等，除 §3 的 shim 之外）。宁可先 `// TODO(port)` + 抛 `UnsupportedOperationException` 也不要留坏 import。
7. 需要新依赖 → 告诉主 Agent（改 `dependencies.gradle` 是主 Agent / 指定人的职责）。

---

## 1. 分层与优先级

| 层 | 内容 | 状态目标 |
|---|---|---|
| **L0 平台层** | `com/tacz/guns/mcshim/**`（PoseStack / RenderSystem / VertexConsumer / MultiBufferSource / RenderType / ItemDisplayContext / Pose / Mth / GsonHelper / OverlayTexture / LightTexture / BufferBuilder…）、`util/math`、NBT / 资源定位辅助 | 所有工作流的地基，最先完成 |
| **L1 主干（服务端 + 数据）** | `api/**`、`resource/**`、`config/**`、`network/**`、`util/**`、`item/**`、`entity/**`、`event/**`、`init/**`、`block/**`、`inventory/**`、`crafting/**`、`command/**`、`advancement/**`、`loot/**` | **本次会话主目标：编译通过、枪械数据能加载、能开火（无渲染）** |
| **L2 客户端** | `client/**`（257 文件）、`mixin/**`（22）、`compat/**`（反射骨架） | 次目标；按包逐步解封 |
| **L3 收尾** | spotless、mcmod.info、mixins json、运行期验证 | 主 Agent |

`build.gradle.kts` 里有 `pendingPortExcludes` 列表：**尚未适配完的包先排除出编译**，保证任何阶段 `build` 都能过。
每完成一个包，把对应 exclude 行删掉（主 Agent 统一维护）。

---

## 2. 工作流与写作用域（互不重叠）

| 工作流 | 写作用域 | 规模 |
|---|---|---|
| `platform-shim` | `src/main/java/com/tacz/guns/mcshim/**`、`util/math/**` | 新增 ~15 类 |
| `common-api` | `src/main/java/com/tacz/guns/api/**` | 115 文件 / 4k 行 |
| `common-resource` | `src/main/java/com/tacz/guns/resource/**` | 105 文件 / 7.4k 行 |
| `common-infra` | `config/**`、`network/**`、`util/**`(除 math)、`command/**`、`init/**`、`GunMod.java`、`CommonProxy.java`、`client/ClientProxy.java` | 111 文件 |
| `common-game` | `item/**`、`entity/**`、`event/**`、`block/**`、`inventory/**`、`crafting/**`、`advancement/**`、`loot/**`、`particles/**`、`sound/**` | 130 文件 |
| `client-port` | `client/**`、`mixin/**`、`compat/**` | 349 文件 |

---

## 3. 平台替换表（1.20.1 → 1.7.10）

### 3.1 包名/类型改名（直接换 import，不用 shim）

| 1.20.1 | 1.7.10 |
|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.util.ResourceLocation` |
| `net.minecraft.world.item.ItemStack` / `Item` | `net.minecraft.item.ItemStack` / `Item` |
| `net.minecraft.world.item.ItemDisplayContext` | shim：`com.tacz.guns.mcshim.ItemDisplayContext` |
| `net.minecraft.world.entity.Entity/LivingEntity/player.Player/player.Inventory` | `net.minecraft.entity.Entity/EntityLivingBase/entity.player.EntityPlayer/inventory.InventoryPlayer` |
| `net.minecraft.world.entity.player.Player`（客户端） | `net.minecraft.client.entity.EntityPlayerSP`（客户端）/ `EntityPlayer`（通用） |
| `net.minecraft.client.player.LocalPlayer` | `net.minecraft.client.entity.EntityPlayerSP` |
| `net.minecraft.server.level.ServerPlayer` | `net.minecraft.entity.player.EntityPlayerMP` |
| `net.minecraft.world.level.Level` | `net.minecraft.world.World` |
| `net.minecraft.world.level.LevelAccessor` | `net.minecraft.world.IBlockAccess` / `World` |
| `net.minecraft.world.level.block.*` | `net.minecraft.block.*` |
| `net.minecraft.world.level.block.entity.BlockEntity` | `net.minecraft.tileentity.TileEntity` |
| `net.minecraft.world.level.block.state.BlockState` | `net.minecraft.block.Block` + `IBlockState`（1.7.10 无）→ 用 `Block` + `metadata` |
| `net.minecraft.world.level.block.state.StateDefinition` | 1.7.10 无 → 用 `Block` 的 `getSubBlocks` / metadata |
| `net.minecraft.core.BlockPos` | `int x, y, z`（1.7.10 无 BlockPos） |
| `net.minecraft.core.Direction` | `net.minecraftforge.common.util.ForgeDirection`（方块侧）/ `net.minecraft.util.MovingObjectPosition` |
| `net.minecraft.world.phys.Vec3` | `net.minecraft.util.Vec3` |
| `net.minecraft.world.phys.AABB` | `net.minecraft.util.AxisAlignedBB` |
| `net.minecraft.world.phys.BlockHitResult` / `EntityHitResult` / `HitResult` | `net.minecraft.util.MovingObjectPosition`（含 `typeOfHit`、`entityHit`、`blockX/Y/Z`、`sideHit`） |
| `net.minecraft.nbt.CompoundTag` | `net.minecraft.nbt.NBTTagCompound` |
| `net.minecraft.nbt.Tag` | `net.minecraft.nbt.NBTBase` |
| `net.minecraft.nbt.ListTag` / `StringTag` / `IntTag` | `NBTTagList` / `NBTTagString` / `NBTTagInt` |
| `net.minecraft.network.chat.Component` / `MutableComponent` | `net.minecraft.util.IChatComponent` / `ChatComponentText` / `ChatComponentTranslation` / `EnumChatFormatting` |
| `net.minecraft.util.Mth` | shim：`com.tacz.guns.mcshim.Mth`（内部转 `net.minecraft.util.MathHelper`） |
| `net.minecraft.util.GsonHelper` | shim：`com.tacz.guns.mcshim.GsonHelper` |
| `net.minecraft.util.RandomSource` | `java.util.Random` |
| `net.minecraft.client.Minecraft` | `net.minecraft.client.Minecraft`（同名，API 不同：`player` → `thePlayer`、`level` → `theWorld`、`screen` → `currentScreen`、`getItemRenderer()` → `getRenderItem()`） |
| `net.minecraft.client.gui.screens.Screen` | `net.minecraft.client.gui.GuiScreen` |
| `net.minecraft.client.gui.components.Button` | `net.minecraft.client.gui.GuiButton` |
| `net.minecraft.client.gui.GuiGraphics` | 1.7.10 无 → shim `com.tacz.guns.mcshim.GuiGraphics`（封装 ` Tessellator` + `FontRenderer`） |
| `net.minecraft.client.KeyMapping` | `net.minecraft.client.settings.KeyBinding` |
| `net.minecraft.client.renderer.texture.OverlayTexture` | shim 常量 `com.tacz.guns.mcshim.OverlayTexture.NO_OVERLAY = 0` |
| `net.minecraft.client.renderer.LightTexture` | 1.7.10 光照是 `brightness` 两段打包值 → shim `com.tacz.guns.mcshim.LightTexture` |
| `net.minecraft.sounds.SoundSource` | 1.7.10 `World#playSound(x,y,z, name, volume, pitch)`，无 source → 忽略/映射注释 |
| `net.minecraft.sounds.SoundEvents` | `net.minecraft.init.Blocks`/自定义 `SoundEvent` 字符串 |
| `net.minecraft.network.FriendlyByteBuf` | shim：`com.tacz.guns.mcshim.PacketBuffer`（基于 `io.netty.buffer.ByteBuf`，写出/读取方法名对齐 1.7.10 的 `ByteBufUtils`） |
| `net.minecraft.server.packs.*` | `net.minecraft.client.resources.IResourceManager` / `IResourceManagerReloadListener`（1.7.10 有，API 不同） |
| `net.minecraft.commands.*` / `com.mojang.brigadier.*` | `net.minecraft.command.ICommand` + `CommandBase`（1.7.10 无 brigadier） |
| `net.minecraft.core.NonNullList` | `java.util.List` / `com.google.common.collect.Lists` |
| `net.minecraft.world.entity.EntityType` | `cpw.mods.fml.common.registry.EntityRegistry` |
| `net.minecraft.world.damagesource.DamageSource` | 同名 `net.minecraft.util.DamageSource`（构造用 `DamageSource#causePlayerDamage`、自定义 `DamageSource` 子类） |

### 3.2 用 shim 的（1.7.10 完全没有对应概念）

| 1.20.1 | shim（`com.tacz.guns.mcshim`） | 说明 |
|---|---|---|
| `com.mojang.blaze3d.vertex.PoseStack`（56 文件） | `PoseStack` + `PoseStack.Pose` | **JOML 支撑的矩阵栈**，API 与 1.20 完全一致：`pushPose/popPose/last()/translate/scale/mulPose/last().pose()` 返回 `Matrix4f`、`last().normal()` 返回 `Matrix3f`。渲染时把 `last().pose()` 交给固定管线（`GL11.glMultMatrix`）或 `Tessellator` 顶点变换 |
| `com.mojang.blaze3d.vertex.VertexConsumer`（31） | `VertexConsumer` | `vertex(x,y,z)` → `color(r,g,b,a)` → `uv(u,v)` → `overlay()` → `lightmap()` → `normal()` → `endVertex()`；实现类 `TessellatorVertexConsumer`、`BufferBuilderVertexConsumer` |
| `net.minecraft.client.renderer.MultiBufferSource`（47） | `MultiBufferSource` + `MultiBufferSource.BufferSource` | `getBuffer(RenderType)` / `immediate()`；实现走 `Tessellator` |
| `net.minecraft.client.renderer.RenderType`（28） | `RenderType` | 一个「GL 状态包」：`entityCutoutNoCull`、`entitySolid`、`entityTranslucent`、`translucent`、`lines`、`debugLineStrip`、`eyes`、`beaconBeam` 等静态工厂 + `begin/end` 应用 GL 状态 |
| `com.mojang.blaze3d.systems.RenderSystem`（25） | `RenderSystem` | 只做 GL11/GL12 直通：`enableBlend/disableBlend/blendFunc/depthMask/enableDepthTest/disableDepthTest/enableCull/disableCull/setShaderColor/setShaderTexture/colorMask/polygonOffset/depthFunc/clear/lineWidth/assertOnRenderThread(no-op)` |
| `com.mojang.blaze3d.platform.InputConstants`（13） | `InputConstants` | 键码常量 |
| `net.minecraft.world.entity.Pose`（9） | `EntityPose` | enum（STANDING/SNEAKING/SWIMMING…）+ `EntityStateHelper#getPose(EntityLivingBase)` 由 1.7.10 状态推导 |
| `com.maydaymemory.mae.basic.Pose` / `DummyPose` | `AnimationPose` | 动画姿态（joml 关节表），与上表的 `EntityPose` 不是一回事，不要混用 |
| `net.minecraft.world.InteractionHand` | `InteractionHand` | enum `MAIN_HAND` / `OFF_HAND`（1.7.10 无双持，`OFF_HAND` 分支按降级处理并注释） |
| `net.minecraft.world.entity.HumanoidArm` | `HumanoidArm` | enum `LEFT` / `RIGHT` |
| `com.mojang.blaze3d.vertex.VertexFormat` / `DefaultVertexFormat` / `Tesselator` / `BufferBuilder`（6） | 同名 shim | 包在 `net.minecraft.client.renderer.Tessellator` 上 |
| `com.mojang.math.Axis`（client） | `com.tacz.guns.mcshim.Axis` | `XP/YP/ZP/XN/YN/ZN.rotationDegrees(f)` → `Quaternionf`（JOML） |

**JOML 用真的依赖**（`org.joml:joml:1.10.5`，已在 `dependencies.gradle` 里 shade），不要 shim。

### 3.3 上游第三方库替换

| 上游 | 处理 |
|---|---|
| `it.unimi.dsi.fastutil.*`（12 种类型 / 22 文件） | **换成 `java.util` / commons-lang3**（`Int2ObjectArrayMap`→`HashMap<Integer,V>`、`ObjectArrayList`→`ArrayList`、`Object2ObjectOpenHashMap`→`HashMap`、`Double2ObjectRBTreeMap`→`TreeMap`、`Int2IntArrayMap`→`HashMap`、**`Pair`→`org.apache.commons.lang3.tuple.Pair`**（`.left()/.right()` → `.getLeft()/.getRight()`））。理由：不引入 22MB 依赖，且 commons-lang3 3.3.2 是 1.7.10 自带 |
| `com.maydaymemory.mae.basic.Pose` / `DummyPose`（1 文件） | 用 `com.tacz.guns.mcshim.Pose`（joml 关节姿态）自实现 |
| `com.github.mcmodderanchor.simplebedrockmodel.*`（40 文件） | **源码 vendor 进仓库**（`tmp/libs-src/simplebedrockmodel`，382 文件/35k 行）→ `com/github/mcmodderanchor/simplebedrockmodel/**`，逐类改 1.7.10（`client-port` 工作流负责） |
| `org.luaj`（19 文件） | 保留依赖（已 shade），luaj 是 Java 8 兼容的 |
| `org.apache.commons.math3`（2 文件） | 保留依赖 |
| `com.google.gson`（153 文件） | 用 MC 1.7.10 自带的 gson **2.2.4** API 子集：不要用 `JsonParser.parseString/parseReader`（用 `new JsonParser().parse(reader)`）、不要用 `JsonElement.deepCopy` 之外的新 API；缺的用 `mcshim.GsonHelper` 补 |
| `org.apache.logging.log4j` | 保留（1.7.10 自带） |
| `org.jetbrains.annotations` | `compileOnly` 依赖已加 |

---

## 4. 运行时机制替换约定

1. **注册**：`DeferredRegister` → 静态字段 + `GameRegistry.registerItem/registerBlock/registerTileEntity`、
   `EntityRegistry.registerModEntity`、`GameRegistry.addRecipe`、`LanguageRegistry`。
   每个 `Mod*` 类提供 `public static void register()`，由 `CommonProxy.preInit` 按顺序调用。
2. **网络**：`SimpleChannel` → `SimpleNetworkWrapper`（`NetworkRegistry.INSTANCE.newSimpleChannel(MOD_ID)`）。
   包体用 `IMessage` + `IMessageHandler`，序列化用 `io.netty.buffer.ByteBuf`（1.7.10 的 `ByteBufUtils`）。
   1.20 的 `StreamCodec` / `FriendlyByteBuf` 一律替换。
3. **事件**：`net.minecraftforge.eventbus.api.SubscribeEvent` → `cpw.mods.fml.common.eventhandler.SubscribeEvent`；
   `@Mod.EventBusSubscriber` 类改为在 `CommonProxy`/`ClientProxy` 里 `MinecraftForge.EVENT_BUS.register(...)`；
   mod 生命周期事件（`FMLCommonSetupEvent` 等）→ `FMLPreInitializationEvent` / `FMLInitializationEvent`。
4. **配置**：`ForgeConfigSpec` → `Configuration`（`net.minecraftforge.common.config.Configuration`）+ 每项 `getInt/getBoolean/getString`；
   `ModConfig.Type.SERVER` 的同步机制 1.7.10 无 → 用网络包自行同步（记录差异）。
5. **数据包（datapack）**：1.20 `data/tacz/**` 的 recipes/loot/tags 没有 1.7.10 对应 → 改为代码注册（`GameRegistry.addRecipe`）
   或并入 TACZ 自研的枪包加载器（`GunPackLoader` 本来就自己扫 `assets/tacz/custom/**`）。
6. **Capability**：`Capability`/`ICapabilityProvider` → 1.7.10 用 `IExtendedEntityProperties`（`EntityConstructing` + `RegisterExtendedPropertiesEvent`）
   或按实体 id 的静态管理器（推荐，参考 YSMU 的 `AppliedModelRegistry` 形态）。
7. **属性（Attribute）**：`AttributeRegistry` 1.7.10 有 `EntityLivingBase#getEntityAttribute`，自定义属性用
   `net.minecraft.entity.ai.attributes.RangedAttribute` + `ServerEventHandler`。上游 `ModAttributes.BULLET_RESISTANCE` 照此改造。
8. **数据同步**：上游 `entity/sync/**`（SynchedEntityData 风格）→ 用 `DataWatcher`（1.7.10 内建，25 个槽位限制要记录）
   或自研网络同步。

---

## 5. 判定「文件移植完成」

- [ ] 没有 1.20.1 专属 import（`get_file_problems` 无未解析符号）
- [ ] 公开 API 的类名 / 方法签名 / 语义与上游一致（YSM 等外部模组编译期依赖，见 `docs/YSM_COMPAT_HANDOFF.md`）
- [ ] 1.7.10 无法表达的能力：要么 shim，要么 `// TODO(port): 上游 Xxx.java:NN 做的是……1.7.10 无对应，此处{降级/抛异常/记录}` 
- [ ] 该文件在本工作流内自洽（不引用尚未移植的其它包的新类）

## 6. 进度记录

每个工作流结束（或每完成一个包）就在 `docs/PORT_STATUS.md` 追加一行：
`包名 | 文件数 | 状态(完成/部分/未动) | 已完成内容 | 阻塞点`。
