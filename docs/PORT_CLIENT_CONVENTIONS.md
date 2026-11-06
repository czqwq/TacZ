# 客户端移植约定（client-port 工作流内部契约）

> 面向本工作流（`client/**`、`mixin/**`、`compat/**`、`com.github.mcmodderanchor/**`）的所有参与者。
> 总规则见 `docs/PORT_PLAN.md`，本文只写**客户端特有**的、容易踩错的约定。
> **禁止运行任何 gradle 命令。** 自查用 IDEA MCP `get_file_problems` / `lint_files`。

---

## 0. 已完成的全仓机械替换（不要再重做）

以下替换已经对所有 `client/**`、`mixin/**`、`compat/**` 文件跑过，如果看见还有未替换的，**说明那个文件是特例，需要人工判断**，不要盲目批量再替换一遍。

| 1.20.1 | 1.7.10 / shim |
|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.util.ResourceLocation` |
| `net.minecraft.world.item.ItemStack` / `Item` | `net.minecraft.item.ItemStack` / `Item` |
| `net.minecraft.world.item.ItemDisplayContext` | `com.tacz.guns.mcshim.ItemDisplayContext` |
| `net.minecraft.world.entity.Entity` | `net.minecraft.entity.Entity` |
| `net.minecraft.world.entity.LivingEntity` | `net.minecraft.entity.EntityLivingBase` |
| `net.minecraft.world.entity.player.Player` | `net.minecraft.entity.player.EntityPlayer` |
| `net.minecraft.world.entity.player.Inventory` | `net.minecraft.entity.player.InventoryPlayer` |
| `net.minecraft.world.entity.Pose` | `com.tacz.guns.mcshim.EntityPose` |
| `com.maydaymemory.mae.basic.Pose` | `com.tacz.guns.mcshim.AnimationPose` |
| `net.minecraft.world.InteractionHand` | `com.tacz.guns.mcshim.InteractionHand` |
| `net.minecraft.world.entity.HumanoidArm` | `com.tacz.guns.mcshim.HumanoidArm` |
| `net.minecraft.world.level.Level` | `net.minecraft.world.World` |
| `net.minecraft.world.level.GameType` | `net.minecraft.world.WorldSettings.GameType` |
| `net.minecraft.world.phys.Vec3` | `net.minecraft.util.Vec3` |
| `net.minecraft.world.phys.Vec2` | `org.joml.Vector2f` |
| `net.minecraft.world.phys.AABB` | `net.minecraft.util.AxisAlignedBB` |
| `net.minecraft.nbt.CompoundTag` | `net.minecraft.nbt.NBTTagCompound` |
| `net.minecraft.util.Mth` | `com.tacz.guns.mcshim.Mth` |
| `net.minecraft.util.GsonHelper` | `com.tacz.guns.mcshim.GsonHelper` |
| `net.minecraft.util.RandomSource` | `java.util.Random`（**方法**：`nextInt(bound)` 同名、`nextFloat()` 同名、`nextDouble()` 同名；`nextInt()` 有） |
| `net.minecraft.ChatFormatting` | `net.minecraft.util.EnumChatFormatting`（用法差异：`ChatFormatting.RED` → `EnumChatFormatting.RED`；`getColor()`→`getColorIndex()`；不再有 `withBold`→用 `EnumChatFormatting.BOLD`） |
| `net.minecraft.client.gui.Font` | `net.minecraft.client.gui.FontRenderer`（`width(s)` 同名、`drawShadow(s,x,y,c)`→`drawStringWithShadow`、`draw(s,x,y,c)`→`drawString`） |
| `net.minecraft.client.gui.screens.Screen` | `net.minecraft.client.gui.GuiScreen` |
| `net.minecraft.client.gui.GuiGraphics` | `com.tacz.guns.mcshim.GuiGraphics`（**client-port 自建**） |
| `net.minecraft.client.KeyMapping` | `net.minecraft.client.settings.KeyBinding` |
| `net.minecraft.client.Options` | `net.minecraft.client.settings.GameSettings` |
| `net.minecraft.client.player.LocalPlayer` | `net.minecraft.client.entity.EntityPlayerSP` |
| `net.minecraft.client.player.AbstractClientPlayer` | `net.minecraft.client.entity.AbstractClientPlayer` |
| `net.minecraft.client.multiplayer.ClientLevel` | `net.minecraft.client.multiplayer.WorldClient` |
| `net.minecraft.client.renderer.texture.OverlayTexture` | `com.tacz.guns.mcshim.OverlayTexture`（`NO_OVERLAY=0`） |
| `net.minecraft.client.renderer.LightTexture` | `com.tacz.guns.mcshim.LightTexture` |
| `net.minecraft.client.renderer.MultiBufferSource` | `com.tacz.guns.mcshim.MultiBufferSource` |
| `net.minecraft.client.renderer.RenderType` | `com.tacz.guns.mcshim.RenderType` |
| `net.minecraft.client.renderer.texture.TextureAtlasSprite` | `net.minecraft.util.IIcon` |
| `net.minecraft.client.renderer.entity.EntityRenderDispatcher` | `net.minecraft.client.renderer.entity.RenderManager` |
| `net.minecraft.client.renderer.entity.EntityRenderer` | `net.minecraft.client.renderer.entity.Render` |
| `net.minecraft.client.resources.language.I18n` | `net.minecraft.util.StatCollector`（`get(key,args)`→`translateToLocalFormatted`、`get(key)`→`translateToLocal`） |
| `net.minecraft.world.damagesource.DamageSource` | `net.minecraft.util.DamageSource` |
| `net.minecraft.sounds.SoundEvent` | **没有等价物**：用 `String` sound name + `World#playSound(x,y,z,name,vol,pitch)`；记录差异 |
| `net.minecraftforge.api.distmarker.Dist/OnlyIn` | `cpw.mods.fml.relauncher.Side/SideOnly`（`@OnlyIn(Dist.CLIENT)` → `@SideOnly(Side.CLIENT)`） |
| `net.minecraftforge.fml.LogicalSide` | `cpw.mods.fml.relauncher.Side` |
| `net.minecraftforge.fml.ModList` | `cpw.mods.fml.common.Loader`（`ModList.get().isLoaded(id)` → `Loader.isModLoaded(id)`） |
| `net.minecraftforge.eventbus.api.SubscribeEvent/Event/Priority` | `cpw.mods.fml.common.eventhandler.*` |
| `net.minecraftforge.client.event.RenderPlayerEvent` | 同名 1.7.10 Forge 类，直接用 |
| `com.mojang.blaze3d.vertex.PoseStack` | `com.tacz.guns.mcshim.PoseStack` |
| `com.mojang.blaze3d.vertex.VertexConsumer` | `com.tacz.guns.mcshim.VertexConsumer` |
| `com.mojang.blaze3d.systems.RenderSystem` | `com.tacz.guns.mcshim.RenderSystem` |
| `com.mojang.blaze3d.platform.InputConstants` | `com.tacz.guns.mcshim.InputConstants` |
| `com.mojang.blaze3d.vertex.VertexFormat`/`DefaultVertexFormat` | `com.tacz.guns.mcshim.*` |
| `com.mojang.math.Axis` | `com.tacz.guns.mcshim.Axis` |
| `it.unimi.dsi.fastutil.*` | `java.util.*`（`ObjectArrayList`→`ArrayList`、`ObjectList`→`List`、`Object2ObjectOpenHashMap`→`HashMap`、`Int2ObjectArrayMap`→`HashMap`、`Int2IntArrayMap`→`HashMap`、`Double2ObjectRBTreeMap`→`TreeMap`） |
| `it.unimi.dsi.fastutil.Pair` | `org.apache.commons.lang3.tuple.Pair`（`pair.right()`→`getRight()`、`pair.left()`→`getLeft()`、`Pair::value`→`Pair::getValue`、`Pair.of(...)` 同名） |
| `net.minecraft.server.level.ServerPlayer` | `net.minecraft.entity.player.EntityPlayerMP` |

---

## 1. mcshim 归属（不要越界）

`src/main/java/com/tacz/guns/mcshim/**` 归 **platform-shim** 所有。client-port 只能**读取/import/调用**，**唯一例外**：

- `com/tacz/guns/mcshim/GuiGraphics.java` —— client-port 自建。

已确认由 platform-shim 提供（可直接 import 使用）：

`PoseStack`(+`Pose`) / `MultiBufferSource`(+`BufferSource`) / `VertexConsumer`(+`TessellatorVertexConsumer`,`BufferBuilderVertexConsumer`) /
`RenderType` / `RenderSystem` / `ItemDisplayContext` / `Mth` / `InputConstants`(+`Key`,`Type`) / `OverlayTexture` /
`LightTexture` / `Axis` / `GsonHelper` / `EntityPose` / `AnimationPose`+`DummyPose` / `InteractionHand` / `HumanoidArm` /
`EntityStateHelper` / `VertexFormat`+`DefaultVertexFormat` / `Tesselator` / `BufferBuilder` / `BufferUploader` /
`PacketBuffer` / `FileToIdConverter` / `ResourceManager` / `Resource` / `ProfilerFiller` /
`PreparableReloadListener` / `SimplePreparableReloadListener`

**不提供**、需要客户端自己解决：

| 1.20 | 处理 |
|---|---|
| `com.mojang.blaze3d.platform.GlStateManager` | **不存在**（1.7.10 是 1.8+ 才有）。用 `org.lwjgl.opengl.GL11.glPushMatrix()/glPopMatrix()`，或 `PoseStack#pushToGL()/popFromGL()` |
| `com.mojang.blaze3d.platform.Lighting` | 用 `net.minecraft.client.renderer.RenderHelper#enableStandardItemLighting/disableStandardItemLighting` |
| `net.minecraft.client.renderer.entity.EntityRenderers` | 用 `net.minecraft.client.renderer.entity.RenderingRegistry` 或直接在 `ClientProxy` 里 `RenderingRegistry.instance().registerEntityRenderingHandler(...)` |
| `net.minecraft.client.renderer.texture.OverlayTexture` | 用 `mcshim.OverlayTexture`（有） |

**LightTexture packing 裁决（platform-shim 给的，务必照此）**：
```
pack(block, sky) = (sky << 20) | (block << 4)   // 1.7.10 原生打包，可直接给 Tessellator#setBrightness / OpenGlHelper.setLightmapTextureCoords
block(packed)    = (packed & 0xFFFF) >>> 4
sky(packed)      = (packed >>> 16) >>> 4
```
**不要**把 1.20 的 `block | sky<<16` 布局搬过来。

---

## 2. 客户端渲染移植的基本套路

1. **矩阵**：`PoseStack` 里累积的变换在进入实际绘制前必须刷到 GL 固定管线。用法：
   ```java
   poseStack.pushToGL();     // glPushMatrix + glMultMatrix(stackTop)
   ... GL11.glRotatef / Tessellator 绘制 ...
   poseStack.popFromGL();
   ```
   或直接 `GL11.glPushMatrix()` + 自己 `glTranslatef/glRotatef/glScalef` + `GL11.glPopMatrix()`。
2. **顶点**：`VertexConsumer` / `MultiBufferSource` 的 shim 实现最终也是走 `Tessellator`。
   如果一个方法里只有一条简单的贴图四边形，**优先直接写 `Tessellator`**，别绕 `RenderType`——语义更清楚、不会有 GL 状态泄漏。
3. **GL 状态**：1.7.10 固定管线是**全局状态**。每一次 `disableBlend/enableBlend`、`depthMask(false)`、
   `disableCull`、`glDepthFunc`、`colorMask`、`OpenGlHelper.setLightmapTextureCoords`、`glAlphaFunc`
   **必须成对恢复**。推荐模板：
   ```java
   RenderSystem.enableBlend(); ... try { ... } finally { RenderSystem.disableBlend(); }
   ```
   或用一个 `try/finally` 把进入前的状态存下来再还原（`GL11.glGetInteger(GL11.GL_BLEND)` 之类代价高，
   更常见的是**按已知默认状态硬还原**：`RenderSystem.disableBlend(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.setShaderColor(1,1,1,1); RenderSystem.colorMask(true,true,true,true);`）。
   1.7.10 `RenderHelper.enableGUIStandardItemLighting()` / `disableStandardItemLighting()` 负责光照。
4. **GUI**：1.7.10 `GuiScreen` 没有 `render(GuiGraphics, int, int, float)`，改 `drawScreen(int mouseX, int mouseY, float partialTicks)`；
   `renderBackground(guiGraphics, ...)` → `drawDefaultBackground()`；`renderTooltip` 用 `GuiScreen#func_146283_a`（1.7.10 名称 `drawHoveringText`）。
   绘制一律 `this.mc.getTextureManager().bindTexture(ResourceLocation)` + `Tessellator` 画 quad。
   把 `GuiGraphics` 的 `blit/fill/drawString/renderTooltip/renderItem/renderItemTooltip` 等方法保留在 `mcshim.GuiGraphics` 里，
   让上游 GUI 代码改动最小。
5. **物品渲染**：1.7.10 是 `net.minecraft.client.renderer.entity.RenderItem`
   （`renderItemAndEffectIntoGUI(FontRenderer, TextureManager, ItemStack, int, int)`、
   `renderItemIntoGUI`、`renderItemOverlayIntoGUI`）。`ItemRenderer`（`net.minecraft.client.renderer.ItemRenderer`）是另一回事（手持物品）。
   上游 `ItemRenderer#renderStatic`（1.20）→ 1.7.10 `RenderItem#renderItem(EntityLivingBase, ItemStack, int, int)` 或
   `RenderItem` 实例的 `renderItemIntoGUI`。
6. **工具类**：1.7.10 世界光照 `world.getLightBrightnessForSkyBlocks(x,y,z,0)` 返回打包亮度，能直接
   `Tessellator.instance.setBrightness(packed)`。

---

## 3. 客户端注册（不要改 `ClientProxy.java`）

`client/ClientProxy.java` 归 **common-infra**。客户端需要在 preInit/init 阶段注册的东西，
**由本工作流提供 `public static void` 入口方法**，把签名报给 common-infra。

建议形态（每个子包一个 `XxxClientRegistry.register()`）：
- `client/init/ClientSetupEvent` 里的注册 → 拆成 `ClientKeyRegistrar.register()`（=`client/input/**` 的 KeyBinding）、
  `ClientRenderRegistrar.register()`（=`ItemRenderer`/`TileEntitySpecialRenderer`/`EntityRenderer` 注册）、
  `ClientParticleRegistrar.register()`、`ClientReloadListenerRegistrar.register()`。
- 统一在 `client/init/ClientRegistry.java` 暴露 `public static void registerAll()`，
  内部按顺序调上面四个。**common-infra 只需要在 `ClientProxy.preInit` 里调一行。**
- 网络包客户端处理器的注册（1.20 的 `NetworkConstants` client handler）也走这里。

---

## 4. 硬性禁止

- **不要跑 gradle**（`gradlew` 任何任务）。
- **不要建 `mcshim` 下的类**（除 `GuiGraphics`）。
- **不要改别人的包**（`api/**`、`resource/**`、`item/**`、`entity/**`、`config/**`、`network/**`、`util/**`、`init/**`、`event/**`、`block/**`、`GunMod.java`、`CommonProxy.java`、`client/ClientProxy.java`）。
- **不要留 1.20.1 专属 import**。宁可 `// TODO(port)` + 降级/抛异常，也不要留坏 import。
- **`MuzzleFlashRender.isSelf` 和 `ShellRender.isSelf` 必须是 `public static boolean` 字段**（外部模组 YSM 直接赋值），不能改 setter。
- **YSM 契约**：`com.tacz.guns.api.*` 的类名/方法签名与上游一致（这部分是 common-api 的职责，客户端只需不破坏调用）。

---

## 5. 判断「可以收工」

- `get_file_problems` 里本文件无 **ERROR**（`无法解析符号`/`无法解析方法`）。
  来自其他工作流尚未完成的类的级联错误可以暂时存在，但必须确认那是**别人的**类。
- 没有 1.20 专属 import。
- 有降级的地方留 `// TODO(port): 上游 <文件>:<行> 做的是……`。
