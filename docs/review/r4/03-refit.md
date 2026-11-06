# r4-03 · 模块 C：枪械改装（附件 / refit）系统审查

- **审查人**：`r4-refit`
- **作用域**：改装全链路 —— 网络包 / 服务端流程 / 槽位与匹配 / 属性 modifier / 客户端 GUI / 数据加载 / 1.7.10 空栈边界
- **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`（TACZ 1.2.0 / MC 1.20.1 / Forge 47）
- **1.7.10 准绳**：`build/rfg/minecraft-src/java/**`（原版 + Forge 反编译源码）
- **纪律**：全程**未运行任何 gradle 任务**；自查用 IDEA MCP（`read_file` / `search_*` / `analyze_calls` / `lint_files`）；
  改动 1 个文件（`client/gui/GunRefitScreen.java`），`lint_files(min_severity:"error")` = **0 error**
- **不重复前几轮**：r2 的 `RefitTransform` 注册/静态处理器、`ScreenItemVboContext`、`LaserColorUtil` 默认色、
  `refreshRefitScreen` 刷新钩子、`PackInfo.name == null`；r3 的 5 个消息类 public 无参构造器 —— 本文只做**复核**，不重复报。

---

## 一、结论速览

**改装系统整体可用，且与上游等价度高**：6 个改装网络包的字段读写、discriminator（13/14/15/16/35/36）、
服务端校验顺序、NBT 键名（`Attachment<Type>` 下的 `AttachmentId` / `Skin` / `Variant` / `ZoomNumber` / `LaserColor`、
枪上的 `SlotAdapters` / `VirtualOemAttachment`）、17 个 modifier 的 ID 与 `getOptionalFields`、缓存求值公式、
14 个 `postChangeEvent` 调用点、`attachments_tags` 的实际加载与展开、默认枪包 128 配件 / 6 适配器 / 全部
`allow_attachments` 引用 —— **逐项核对无漂移**（证据见 §五）。前几轮修的 4 条改装相关缺陷**全部复核在且正确**（§四）。

**本轮最严重的问题（P2，已修）**：改装界面**漏渲染 `FlatColorButton` 的 tooltip** —— 槽位适配器按钮被禁用时
唯一的原因提示 `gui.tacz.refit.adapter_requires_unload` 永远看不到；另修一条**第三方枪包缺 `name` 时打开改装界面
构造期 NPE**（与 r2 的 `PackInfo.name == null` 同类）与一条 1.20 背景层缺失（P3）。

> 一句话：**没有发现会丢件 / 会崩主流程的新 P0/P1**；两条 P2 都在客户端 GUI 表现层（已修），
> 未修项均为 P3（旧存档 id 数据修复缺钩子、注释漂移、1.7.10 平台级右键派发差异）。

---

## 二、发现表

| # | 严重度 | 现象 | 根因 | 证据（本工程 / 上游 / 1.7.10） | 状态 | 修法 |
|---|---|---|---|---|---|---|
| **R4-C-1** | **P2** | 改装界面里**变体按钮 / 槽位适配器按钮 / 属性图显隐按钮 / 开火模式切换按钮的 tooltip 全部不显示**。最直接的后果：适配器按钮因「当前配件不允许直连」被 `enabled=false` 置灰，玩家**看不到** `gui.tacz.refit.adapter_requires_unload`（「需要先卸下配件」）这条唯一解释，只能反复点 | 1.7.10 把 `FlatColorButton` 的 tooltip 从「widget 自己画」改成「屏幕统一画」（`FlatColorButton.java:30-31` 类注释 + `:77-91` `renderToolTip`），但 `GunRefitScreen#drawScreen` 只遍历了 `IComponentTooltip` / `IStackTooltip` 两种接口，**漏了 `FlatColorButton` 分支**（该类不实现这两个接口）。`GunSmithTableScreen` 有该分支，改装界面没有 | 本工程：`GunRefitScreen.java:196-235`（修复前只有两个循环）、`FlatColorButton.java:82-91`；对照 `GunSmithTableScreen.java:887`；上游：`tmp/.../client/gui/components/FlatColorButton.java:58`（`renderWidget` 末尾直接 `this.renderToolTip(...)`）；调用点：`GunRefitScreen.java:576/604`（`noneButton.setTooltips(adapter_requires_unload)` / `adapterButton.setTooltips(...)`）、`:177`（fire_mode.switch） | **已修** | `GunRefitScreen#drawScreen` 的第一个 tooltip 循环加 `else if (widget instanceof FlatColorButton f) f.renderToolTip(graphics, this, mouseX, mouseY);`（与 `GunSmithTableScreen:883-893` 同一写法） |
| **R4-C-2** | **P2** | 第三方枪包若写了 `index/attachment_variants/*.json` 或 `index/slot_adapters/*.json` 但**省略 `"name"`**，打开改装界面**直接抛 NPE**（`Pattern#matcher(null)`），界面打不开（不是画错，是 `initGui()` 构造按钮时抛） | `AttachmentVariantIndexPOJO.name` / `SlotAdapterIndexPOJO.name` 是无校验字段，`CommonAttachmentVariantIndex#checkIndex` / `CommonSlotAdapterIndex#checkIndex` 只 `Preconditions` 了 `display`（**不校验 name**）→ `GunRefitScreen` 直接 `new ChatComponentTranslation(name)` → `FlatColorButton` 构造器**立刻**调 `message.getFormattedText()` → `ChatComponentTranslation.ensureInitialized()` → `StatCollector.translateToLocal(null)` → `StringTranslate.tryTranslateKey(null)` 返回 null（`HashMap#get(null)`）→ `initializeFromFormat(null)` → `Pattern#matcher(null)` **NPE** | 本工程：`GunRefitScreen.java:487-530`（`addVariantButtons`）、`:536-612`（`addSlotAdapterButtons`）、`FlatColorButton.java:42-45`（构造器即 `getFormattedText()`）；`CommonAttachmentVariantIndex.java:33-39` / `CommonSlotAdapterIndex.java:33-42`（只校验 display）；1.7.10：`ChatComponentTranslation.java:48-67,87-90`、`StringTranslate.java:107-136`；上游同风险：`tmp/.../GunRefitScreen.java:360,408`（`Component.translatable(pojo.getName())`）；同类已修先例：r2 `PackInfo.name == null`（`client/tooltip/PackInfoText.java`） | **已修** | 新增 `GunRefitScreen#indexName(String name, ResourceLocation id)`：name 非空白 → `ChatComponentTranslation`；否则回退 `ChatComponentText(id.toString())`（宁显示 id 不崩，口径与 `PackInfoText` 一致）；两处调用点改用它 |
| **R4-C-3** | **P3** | 改装界面**没有 1.20 的暗色渐变背景**：1.7.10 `GuiScreen#drawScreen` 不画背景，世界/第一人称枪原样透出，界面文字对比度不足，观感与上游不一致 | 上游 `GunRefitScreen extends Screen` 且**未覆写** `renderBackground` → 1.20.1 `Screen#render` 第一步 `renderBackground` → 有世界时走 `renderTransparentBackground` = `fillGradient(0,0,width,height,0xC0101010,0xD0101010)`。本工程 `drawScreen` 修复前直接 `super.drawScreen`，没有这一层 | 本工程：`GunRefitScreen.java:196`（修复前）；上游：`tmp/.../GunRefitScreen.java:40`（`extends Screen`，全文无 `renderBackground`）、1.20.1 `Screen#render/renderBackground/renderTransparentBackground`（颜色常量 `-1072689136 / -804253680`） | **已修** | `drawScreen` 开头 `graphics.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010)`，再 `super.drawScreen`（顺序与 1.20 `renderBackground → renderables` 一致） |
| **R4-C-4** | **P3** | 旧配件 id 数据修复**不会执行**：`tacz:muzzle_silence_*` → `tacz:muzzle_silencer_*` 的存档迁移逻辑是死代码 | 上游 `AttachmentItem#verifyTagAfterLoad(CompoundTag)`（1.20 物品 NBT 加载后钩子）在 1.7.10 **没有对应钩子**；`util/datafixer/AttachmentIdFix` 全仓 **0 调用者**（`search_symbol` 只有类自身 + `AttachmentItem` 的 TODO 注释） | 本工程：`util/datafixer/AttachmentIdFix.java:30-74`（`updateAttachmentIdInTag` 0 调用）、`item/AttachmentItem.java:111-114`（TODO 明说未接线）；上游：`tmp/.../item/AttachmentItem.java:103-106`（`verifyTagAfterLoad` → `AttachmentIdFix.updateAttachmentIdInTag`） | **未修** | 建议（**跨域：`api/item/nbt/**` 属 api 作用域，本任务写作用域不含**）：在 `GunItemDataAccessor#getAttachmentTag` 取到 `tag` 后调用 `AttachmentIdFix.updateAttachmentIdInTag(tag)` 就地修复（1.7.10 `NBTTagCompound#getCompoundTag` 返回的是 gun NBT 内的**引用**，改它即改枪的 NBT；`getAttachmentTag` 本就是所有 `getAttachmentId` 的唯一入口）。风险低、可幂等。**影响面**：只有「带着 1.20 旧档/老枪包 NBT 的枪」才需要；1.7.10 新档不可达，故 P3 |
| **R4-C-5** | **P3** | 4 个改装消息类里「`readEnum` 越界返回 null」的**注释与实现不符**（那些 `attachmentType == null` 检查是死代码） | `mcshim/PacketBuffer#readEnum` 在越界时**抛 `IllegalArgumentException`**（明确按 1.20 语义设计，注释也写了「不要改回返回 null」），而 `ClientMessageUnloadAttachment:67` / `ClientMessageInstallVirtualAttachment:76` / `ClientMessageSetAttachmentVariant:83` / `ClientMessageSetSlotAdapter:81` 的注释仍写「readEnum 越界返回 null」 | 本工程：`mcshim/PacketBuffer.java:380-405`（抛 IAE）vs 上述 4 处注释；`ClientMessageLaserColor.java:91-98` 同 | **未修（仅注释）** | 功能无影响（解码期失败 = 1.20 语义，r3 已定案），只需把 5 处注释改成「readEnum 越界抛 IAE；此处 null 检查为双保险」。纯文档清理，未改以免与本轮构建抢文件 |
| **R4-C-6** | **P3** | 1.7.10 下**鼠标右键**点在改装界面的按钮上也会触发装/卸/变体切换（上游 `AbstractButton#mouseClicked` 要求 `button == 0`） | 1.7.10 `GuiScreen#handleMouseInput` 对**任意**鼠标键按下都调 `mouseClicked`，基类 `GuiButton#mousePressed` 不判按键号；本工程 `GunAttachmentSlot` / `InventoryAttachmentSlot` / `FlatColorButton` / `RefitUnloadButton` 的 `mousePressed` 均未判 `Mouse.getEventButton() == 0` | 1.7.10：`GuiScreen.java:329-345`（`mouseClicked(i, j, k)` 对任意 k）、`GuiButton.java:125`；本工程：`GunAttachmentSlot.java:138-147`、`InventoryAttachmentSlot.java:86-95`、`FlatColorButton.java:164-173`、`RefitUnloadButton.java:53-62` | **未修（平台级差异）** | 属**全局 1.7.10 GUI 行为**（原版按钮同样如此），不是本模块回归；若要修应在各 `mousePressed` 加按键号判断。风险低收益低，登记不修 |

### 严重度口径说明

- 本轮**没有 P0/P1**。判据：所有「丢件 / 崩主流程 / 属性不生效」的候选路径逐条走查后都有上游同构或 1.7.10 额外防护（见 §五）。
- R4-C-1/2 是 P2：不崩服务端、不丢件，但**直接让玩家无法理解/无法进入改装界面**（2 是构造期 NPE，实机表现为「打开改装界面崩溃」）。

---

## 三、已修清单（1 文件）

| # | 文件 | 改动 | 级别 | 验证 |
|---|---|---|---|---|
| 1 | `src/main/java/com/tacz/guns/client/gui/GunRefitScreen.java` | ① `drawScreen` 补 `FlatColorButton#renderToolTip` 遍历（R4-C-1）；② 新增 `indexName(name, id)` 空名回退并替换变体/适配器两处 `new ChatComponentTranslation(name)`（R4-C-2）；③ `drawScreen` 开头补 1.20 `renderTransparentBackground` 暗色渐变（R4-C-3） | P2 ×2 + P3 ×1 | `mcp__idea__lint_files(min_severity:"error")` → `items: []`（0 error） |

改动行（当前文件）：
- `:196-235` `drawScreen`（背景 + FlatColorButton tooltip 分支；背景 `:204`、tooltip 分支 `:216-228`）
- `:521`（变体名）、`:583`（适配器名）改用 `indexName(...)`
- `:703` 起 `indexName(...)` 及其 javadoc（含 NPE 机制与上游依据）

---

## 四、前几轮「已修」的复核（全部在且正确）

| 历史结论 | 复核结果 | 证据 |
|---|---|---|
| r2：`RefitTransform` 从未注册事件总线 + static handler ICCE + 注册错总线（改装界面类别按钮只有第一次点有效） | ✅ 在且正确。`INSTANCE` 单例 + `ensureRegistered()` 幂等注册到 **FML 总线**；`tickInterpolation` 是 **public 实例方法**；由 `GunRefitScreen` 构造器 `RefitTransform.init()` 触发 | `client/animation/screen/RefitTransform.java:41-53,64-70,123-153`；`GunRefitScreen.java:109`；FML 侧证据：`FMLCommonHandler.java:333-341`（RenderTickEvent 只从 `bus()` 发）、`Minecraft.java:1065-1069`（每帧 START/END） |
| r2：`GunRefitScreen` 刷新钩子（`ClientSideHooks#refreshRefitScreen` → `initGui`） | ✅ 在且正确。服务端 5 个改装消息成功后都 `sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player)`；客户端 `Hooks#refreshRefitScreen` 判 `currentScreen instanceof GunRefitScreen` → `initGui()` + `postChangeEvent(player, getHeldItem())`；**时序安全**：1.7.10 客户端 `processReceivedPackets()` 在 `runTick` 早段（`PlayerControllerMP.java:311-327` ← `Minecraft.java:1691-1694`），`NetworkTaskQueue` 在 `ClientTickEvent.END`（`Minecraft.java:2155`）排空 ⇒ 刷新时背包同步已落地 | `network/message/ServerMessageRefreshRefitScreen.java:23-30`、`network/ClientSideHooks.java:54`、`client/ClientProxy.java:410-418`、`network/NetworkTaskQueue.java:90-95`、`ClientMessage{RefitGun,UnloadAttachment,InstallVirtualAttachment,SetAttachmentVariant,SetSlotAdapter}` 各末尾 |
| r3：`ClientMessageRefitGun` 等消息类 **public 无参构造器**（FML 用 `Class.newInstance()`） | ✅ 在且正确。6 个包全部 `public Xxx() {}`（`ServerMessageRefreshRefitScreen` 无字段不需要） | `ClientMessageRefitGun.java:49`、`ClientMessageUnloadAttachment.java:38`、`ClientMessageInstallVirtualAttachment.java:45`、`ClientMessageSetAttachmentVariant.java:47`、`ClientMessageSetSlotAdapter.java:51`；`ClientMessageLaserColor.java:57`（r3-07 实机修的那个） |
| r3：`LaserColorUtil.setClientDefaultColorResolver` 接线（滑条默认色恒红） | ✅ 在且正确。`ClientSetupEvent` 注入 `resolveLaserDefaultColor`（配件走 `getClientAttachmentIndex`、枪械走 `getGunDisplay`，与上游两处静态调用一一对应；任何异常回退 `0xFF0000`） | `client/init/ClientSetupEvent.java:145-161,463-499`；`util/LaserColorUtil.java:49-128`；消费点 `client/gui/components/refit/HSVSliderGroup.java:77-93` |
| r2：`PackInfo.name == null` 空安全 | ✅ 在且正确（tooltip 侧）；**本轮补上改装界面侧同类缺口**（R4-C-2 是 index `name`，不是 PackInfo） | `client/tooltip/PackInfoText.java`；`ClientAttachmentItemTooltip.java:98-107` |

---

## 五、逐项核对结果（任务 A~F，逐条给结论）

### A. 服务端流程等价性（6 包 + 钩子）

| 核对点 | 结论 | 证据 |
|---|---|---|
| 包字段 read/write 对称 | ✅ 6/6 对称（`MessageBase#fromBytes/toBytes` → 子类 `read/write`，字段顺序与上游 `encode/decode` 逐行同序） | `ClientMessageRefitGun.java:52-63` ↔ 上游 `:29-37`；`ClientMessageUnloadAttachment.java:41-50` ↔ `:26-33`；`ClientMessageInstallVirtualAttachment.java:48-59` ↔ `:31-39`；`ClientMessageSetAttachmentVariant.java:50-64` ↔ `:32-46`；`ClientMessageSetSlotAdapter.java:54-68` ↔ `:32-46`；`ServerMessageRefreshRefitScreen` 无字段 |
| discriminator 与上游一致 | ✅ 13/14/15/16、35/36 与上游 `ID_COUNT` 起始 1 的顺序**逐个相同** | `network/NetworkHandler.java:176-196,287-296` ↔ 上游 `NetworkHandler.java:36,64-71,110-113` |
| 服务端校验：真持枪 / 归属 / 槽位 / 已装 / 数量 / 创造模式 | ✅ 与上游逐条同序：`IGun.getIGunOrNull` → `hasAttachmentLock` → `allowAttachment`（→ tag/适配器）→ 取 `oldAttachmentItem` → `beforeRefit=copy()` → `installAttachment` → `GunRefitTrigger.trigger` → `postChangeEvent` → 回填旧配件 → `EXTENDED_MAG` 退弹 → `detectAndSendChanges` → 刷新包。**1.7.10 额外**：槽位越界夹紧、`attachmentType==null` 守卫（因 1.7.10 `getStackInSlot` 越界 AIOOBE；上游 `getItem` 越界返回 EMPTY） | `ClientMessageRefitGun.java:78-115`；`ClientMessageUnloadAttachment.java:64-93`；`ClientMessageInstallVirtualAttachment.java:73-111`；`ClientMessageSetAttachmentVariant.java:80-114`；`ClientMessageSetSlotAdapter.java:82-128`；上游对应文件同段 |
| 使用「配件自身真实类型」而非客户端传参 | ✅ 上游同款 | `ClientMessageRefitGun.java:98` ↔ 上游 `:62` |
| 成功后刷新包 / trigger / 音效 / `AttachmentPropertyEvent` | ✅ 5 个包都 `sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player)` + `GunRefitTrigger.INSTANCE.trigger(...)` + `AttachmentPropertyManager.postChangeEvent(...)`；音效是**客户端**在发包前播（`SoundPlayManager.playerRefitSound(..., INSTALL/UNINSTALL_SOUND)`），上游同样只客户端播；`AttachmentPropertyEvent` 由 `postChangeEvent` 内 `ChangeGunPropertyEvent.internalOnAttachmentPropertyEvent` → `eval` 后 `MinecraftForge.EVENT_BUS.post` | 各消息末尾；`resource/modifier/AttachmentPropertyManager.java:53-90`；`event/ChangeGunPropertyEvent.java:29-43`；`GunRefitScreen.java:312/316/434`（音效） |
| 多校验/少校验 | ✅ 无少校验；多出的只有 1.7.10 崩溃防护（越界/枚举 null），均带注释。**与上游一致地不做**「枪必须在主手/当前选中槽」的服务端校验（依赖客户端），不新增 | 同上 |
| `GunRefitTrigger` 语义 | ✅ 上游 advancement trigger 在 1.7.10 降级为 `ItemStack.areItemStacksEqual(before, after)` 不等才 post `GunRefitEvent`；调用点形状不变 | `advancement/GunRefitTrigger.java:31-36` ↔ 上游 `:26-30` |

### B. 槽位与匹配

| 核对点 | 结论 | 证据 |
|---|---|---|
| `SlotAdapterHelper#getAvailableSlotAdapters` 契约 | ⚠ **有意的签名差异**：上游返回 `List<Map.Entry<ResourceLocation, CommonSlotAdapterIndex>>`，本工程返回 `Map<ResourceLocation, SlotAdapter>`（网络包按 Map 取），另加 `getAvailableSlotAdapterIndices` 供 GUI。**全仓调用点内部自洽**（无第二个消费者按上游 List 契约写） | 本工程 `util/SlotAdapterHelper.java:70-94,145-193,195-241`；调用点：`ClientMessageSetSlotAdapter.java:114-122`、`GunRefitScreen.java:404,521`、`api/item/gun/AbstractGunItem.java:419-428`、`client/renderer/external/ExternalGunAppearance.java:211`、`client/renderer/block/DisplayGunGeometry.java:193`、`client/event/FirstPersonRenderGunEvent.java:360`、`client/model/functional/AttachmentRender.java:148`；上游 `:73-93` |
| `allowsAttachment` / `allowsDirectAttachment` / `setSlotAdapter` / `removeSlotAdapter` | ✅ 逻辑逐行等价；NBT key `SlotAdapters` / `slotKey=type.name()` / 标签前缀 `#` / `adapter/` 路径前缀**一字未改** | `SlotAdapterHelper.java:60-62,96-143,212-237,277-285` ↔ 上游 `:17-19,24-71,111-134,174-182` |
| 1.20 item tag（`attachments_tags`）在 1.7.10 的替代 | ✅ **真的被加载且真的被用**：`AttachmentsTagManager` 扫 `data/<ns>/tacz_tags/attachments`（`DATA_ROOT`+`tacz_tags/attachments`），`allow_attachments/` 前缀单独进 `allowAttachments`；`AllowAttachmentTagMatcher` 做**递归 tag 展开 + 环保护**（`#` 前缀、visited 集合）；`CommonAssetsManager` 重载末尾 `resetCache()`。**数据实测**：默认枪包 128 个附件 index / 129 个 data / 6 个 slot_adapter index / 全部 `allow_attachments`（108 把枪）与 `#tag` 引用**全部可解析**（脚本校验，见 §六） | `resource/manager/AttachmentsTagManager.java:47-131`；`util/AllowAttachmentTagMatcher.java:43-163`；`resource/CommonAssetsManager.java:192,251`；数据：`assets/tacz/custom/tacz_default_gun/data/tacz/tacz_tags/attachments/**` |
| `VirtualOemAttachment`（虚拟 OEM 配件）是否真能用 | ✅ 能用且链路完整：`GunItemBuilder#installAttachments` 对 `built_in_attachments` 的 `default_attached` 先 `mark` 再 `installAttachment`（枪出生即带默认配件）；改装界面 `getVirtualOemAttachments` 按 `isAllowedVirtualOem + shouldShowIcon + 排除已装/背包重复` 列出；安装走 `ClientMessageInstallVirtualAttachment`（校验 OEM 白名单 + 类型 + `allowAttachment` + 背包空位），卸载时 `isMarked` 的配件**不**进背包（虚拟件直接消失） | `util/VirtualOemAttachment.java:34-78`；`api/item/builder/GunItemBuilder.java:130-147`；`GunRefitScreen.java:630-668`；`ClientMessageInstallVirtualAttachment.java:85-111`；`ClientMessageRefitGun.java:105-107`；`ClientMessageUnloadAttachment.java:78-81` |
| `AttachmentDataUtils` | ✅ 逐行等价（`getAllAttachmentData` / `getMagExtendLevel` / `getAmmoCountWithAttachment` / `getWightWithAttachment` / `isExplodeEnabled` / `getArmorIgnoreWithAttachment` / `getHeadshotMultiplier` / `getDamageWithAttachment` / `getModifiers` / `calcBooleanValue`） | `util/AttachmentDataUtils.java` 全 358 行 ↔ 上游 293 行（差异只有 `var`→显式类型、通配符 import 展开） |
| `DefaultGunAttachments` | ✅ 等价（`built_in_attachments` 的 `default_attached` → `EnumMap`） | `util/DefaultGunAttachments.java:27-42` ↔ 上游 `:14-27` |
| `AttachmentIdFix` | ❌ 死代码（见 R4-C-4，P3 未修） | 同上 |

### C. 属性系统

| 核对点 | 结论 | 证据 |
|---|---|---|
| modifier 注册/查找、ID 常量 | ✅ **17 个注册顺序与上游逐行相同**；ID 全部是 `GunProperties.*.name()`，`GunProperties` 的字符串常量（`ads` / `ammo_speed` / `armor_ignore` / `damage` / `effective_range` / `explosion` / `head_shot` / `ignite` / `inaccuracy` / `knockback` / `pierce` / `recoil_push` / `recoil` / `rpm` / `silence` / `weight_modifier` / `movement_speed`）与上游**逐字相同**；`AimInaccuracyModifier` 上游同样**未注册**（其 ID 被重定向到 `InaccuracyModifier`） | `resource/modifier/AttachmentPropertyManager.java:29-47` ↔ 上游 `:27-45`；`api/GunProperties.java:86-166` ↔ 上游 `:62-136`；`resource/modifier/custom/*Modifier.java` 的 `ID` 字段全表逐条比对 |
| `getOptionalFieldList`（旧版字段兼容） | ✅ `ads_addend` / `inaccuracy_addend`(+`aim_inaccuracy`,`sneak_inaccuracy`,`lie_inaccuracy`) / `recoil_modifier` / `weight` 与上游相同；**数据实测**：默认枪包 129 个 `*_data.json` 的顶层键（`weight`116 / `ads`83 / `aim_inaccuracy`53 / `recoil`41 / `inaccuracy`28 / `extended_mag_level`17 / `ads_addend`17 / `sneak_inaccuracy`12 / `lie_inaccuracy`3 / `rpm`4 / …）**全部有对应 modifier 消费**，无「写了但没人读」的键 | 各 `getOptionalFields` 与上游逐条比对；`AttachmentDataManager#initModifiers:38-58`；数据键统计见 §六 |
| `getCache` / `merge` / `ParameterizedCache` 语义 | ✅ `AttachmentCacheProperty#eval`（先 `initCache` 填默认 → 遍历附件把 modifier 值塞 `cacheModifiers[id]` → 逐个 modifier `eval` → `cacheModifiers.clear()`）与上游逐行相同；`AttachmentPropertyManager#eval`（`addend` 从默认值起累加、`percent` 从 1 起、`multiplier` 连乘且 `max(m,0)`、`percent=max(,0)`、`value=addend*percent*multiplier`、最后脚本二次求值）逐行相同；`ParameterizedCache#eval` 两重载逐行相同 | `resource/modifier/AttachmentCacheProperty.java:27-57` ↔ 上游 `:26-54`；`AttachmentPropertyManager.java:92-126` ↔ 上游 `:75-107`；`api/modifier/ParameterizedCache.java:21-72` ↔ 上游 `:20-71` |
| `Pair` 由 fastutil 换成 `org.apache.commons.lang3.tuple.Pair` 的语义漂移 | ✅ 无漂移。`SilenceModifier`：`Pair.of` 构造 + `getLeft()/getRight()` 等价 fastutil `left()/right()`；`RecoilModifier`：`Pair.of(Modifier,Modifier)` + `getLeft()/getRight()`；`ParameterizedCachePair` 上游 `implements it.unimi.dsi.fastutil.Pair` → 本工程**自持两个 `ParameterizedCache` 字段并保留 `left()/right()` 方法名**（对调用方完全同形），构造/默认值语义一致；`GunProperties.SILENCE` 的 `GunProperty<Pair<Integer,Boolean>>` 类型仍成立 | `SilenceModifier.java:26-70` ↔ 上游 `:22-65`；`RecoilModifier.java:36-99` ↔ 上游 `:31-84`；`api/modifier/ParameterizedCachePair.java:14-42` ↔ 上游 `:12-36`；`api/modifier/ParameterizedCache.java`；`api/GunProperties.java:156-166` |
| 缓存失效时机（装/卸/换皮肤/切适配器/枪包重载/玩家重生/切枪/切模式） | ✅ **14 个 `postChangeEvent` 调用点与上游 14 个逐条对应**：5 个改装消息（装/卸/虚拟装/换皮肤/切适配器）、`LivingEntityDrawGun:83`、`LivingEntityFireSelect:55`、`LivingEntityMixin:176`（`initialData`，覆盖登录/重生/跨维度）、`LocalPlayerDraw:86`、`LocalPlayerFireSelect:62`、`ClientIndexManager:82`（**枪包重载**后重算 + `draw(null)` + `FirstPersonRenderHandler.reset()`）、`ClientProxy:416`（刷新包）。`AttachmentCacheProperty` 每次**新建实例**再 `operator.updateCacheProperty(...)` ⇒ **不存在留旧值**；`eval` 内部 `cacheValues` 全量覆盖、`cacheModifiers` 末尾 `clear()` | `grep postChangeEvent`：本工程 14 处 ↔ 上游 14 处（逐行对照）；`AttachmentCacheProperty.java:27-57`；`mixin/common/LivingEntityMixin.java:168-178`；`client/resource/ClientIndexManager.java:80-129`；`client/event/RefreshClonePlayerDataEvent.java:62-83`；`event/TravelToDimensionEvent.java:50`；`mixin/common/ServerPlayerMixin.java:28` |
| `AttachmentPropertyEvent` post 时机与下游消费 | ✅ post 在 `eval` **之后**（监听者可 `getCacheProperty().setCache(...)` 覆盖），随后脚本 `modify_cached_property` 再覆盖，最后 `updateCacheProperty`。下游消费点逐个在：damage/rpm/recoil/recoil_push/ads/inaccuracy/pierce/armor_ignore/ammo_speed/silence/weight/extra_movement/explosion/ignite/knockback/head_shot/effective_range/弹匣容量 | `AttachmentPropertyManager.java:66-89` ↔ 上游 `:57-72`；消费点：`EntityKineticBullet.java:206-241,702-720`、`GunData.java:335-346`（rpm）、`LivingEntityAim.java:117`、`LivingEntitySpeedModifier.java:79-93`、`CameraSetupEvent.java:390`、`LocalPlayerShoot.java:394-402`、`ModernKineticGunScriptAPI.java:150-191`、`AttachmentDataUtils.java:164-249`、`client/gui/components/refit/GunPropertyDiagrams.java:177-232`、各 `custom/*Modifier#getPropertyDiagramsData` |
| 「装了配件但属性没变 / 卸了没恢复」的静态可证根因 | **未发现**。逐条排除了 4 类可能：① modifier 未注册/ID 漂移（17/17 一致）；② 数据键无人读（129 文件全键覆盖）；③ 缓存不重算（14 处调用点覆盖全部变更源）；④ 下游读旧值（消费点全部走 `cacheProperty` 或 `AttachmentDataUtils` 现算）。`RpmModifier`/`DamageModifier` 等 `initCache` 用 `getRoundsPerMinute(fireMode)`/`bulletData` 现算，与上游同 | 见上两行证据 |

### D. 客户端 GUI

| 核对点 | 结论 | 证据 |
|---|---|---|
| 6 类配件分页 / 槽位渲染与图标偏移 | ✅ `AttachmentType` 枚举顺序（SCOPE/MUZZLE/STOCK/GRIP/LASER/EXTENDED_MAG/NONE）与上游**逐项相同**（图标 UV 按 type 映射，与枚举序无关）；`getSlotTextureXOffset` / `getSlotsTextureWidth`（32×7）与上游同；槽位 18×18、非悬停内缩 1px 同 | `GunRefitScreen.java:112-147` ↔ 上游 `:63-96`；`api/item/attachment/AttachmentType.java` ↔ 上游同名；`GunAttachmentSlot.java:58-136` ↔ 上游 `:44-80` |
| 卸下按钮 / 变体 / 适配器选择 | ✅ 布局与判定同上游（`variantListX` 依 `hasSlotAdapters` 位移、禁用条件 `allowsDirectAttachment`/`allowsAttachment` 一致）；**tooltip 不显示**是 R4-C-1（已修） | `GunRefitScreen.java:409-486`（卸载）、`:487-534`（变体）、`:536-612`（适配器）↔ 上游 `:299-422` |
| 激光颜色滑条 | ✅ 两条滑条（H/S）+ 0~1、0.01 步进 + `apply()` 脏写客户端 NBT 实时预览 + 关界面时一次性 `ClientMessageLaserColor` 上传，与上游同；默认色来源 r3 已接线（§四） | `HSVSliderGroup.java:34-173` ↔ 上游 `:24-98`；`GunRefitScreen.java:374-395,457-478`（两处滑条创建）、`:612-627`（关界面上传）↔ 上游 `:256-267,317-327,424-436` |
| 属性图表 / `built_in` 后缀 / `no_space` | ✅ 属性条 17 modifier 的 `getPropertyDiagramsData`/`getDiagramsDataSize` 与上游**逐项相同**（含 Inaccuracy 4 条、Recoil 2 条）；`built_in` 后缀走 `gui.tacz.refit.builtin_suffix` 追加首行；`no_space` 用 `inventory.getFirstEmptyStack()==-1`（= 上游 `getFreeSlot()==-1`，`InventoryPlayer.java:105`），marked 虚拟件豁免 | `GunPropertyDiagrams.java:35-234` ↔ 上游 `:23-159`；`GunRefitScreen.java:237-252`（built_in）、`:409-424`（no_space）↔ 上游 `:138-148,299-311` |
| `initGui` / `mouseClicked` / `keyTyped` | ✅ `initGui` 清空重建（= 上游 `clearWidgets+add`）；`mouseClicked` 走 `GuiScreen` 基类（按钮自回调，等价上游 `Button.OnPress`）；未覆写 `keyTyped`（ESC 由 `GuiScreen` 关屏 = 上游 `Screen` 行为）。**1.7.10 右键也触发**见 R4-C-6（平台差异） | `GunRefitScreen.java:150-193`；1.7.10 `GuiScreen.java:240-258,329-359` |
| `RefitTransform` 与其消费点 | ✅ `init`/`resetTimestamps`/`changeRefitScreenView`/`getOpeningProgress`/`getTransformProgress`/`getOldTransformType` 逻辑与上游**逐行相同**；消费点齐全：`FirstPersonRenderGunEvent:173,300-312`（开合插值 + old→current 视角矩阵 lerp）、`GunItemRendererWrapper:271-275`（开界面时取消手臂渲染）、`RenderCrosshairEvent:112-115,150-154`（隐藏准心，tick 里刷新 `isRefitScreen`）、`PreventsHotbarEvent:37-53`（隐藏快捷栏）、`RefitKey:36-54`（Z 开）、`GunInputHandler:271-332`（Z 关，`GameSettings.isKeyDown` 读**硬件**状态 ⇒ GUI 打开时仍可关） | `client/animation/screen/RefitTransform.java` ↔ 上游同名；各消费点见左；1.7.10 `GameSettings.java:319-321`（`isKeyDown` 读 `Keyboard/Mouse` 原始状态） |
| GUI 能进/能装/能卸/能切皮肤/能切适配器/能刷新/不丢件/不崩 | 静态判定**成立**（装/卸/皮肤/适配器各链路见 A/B；刷新见 §四；丢件面见 F）。两条 P2 已修（tooltip、缺名 NPE）。**唯一静态不可证**：界面视觉（背景/枪体居中/图标）需实机看 | — |

### E. 数据侧

| 核对点 | 结论 | 证据 |
|---|---|---|
| `data/<ns>/data/attachments/**` 真被读 | ✅ `AttachmentDataManager`（`data/attachments`）→ `initModifiers`（17 modifier 逐个 `readJson`+`initComponents`） | `resource/manager/AttachmentDataManager.java:20-58`；`resource/CommonAssetsManager.java:177` |
| `data/<ns>/data/slot_adapters/**`、`index/slot_adapters/**` | ✅ 生产者 `SLOT_ADAPTER_DATA` / `SLOT_ADAPTER_INDEX` 都在 `CommonAssetsManager.reloadAndRegister`（`:185-191,223-235`）；6 个适配器 index 的 `data` 引用**全部命中**（脚本校验） | 同上；`resource/CommonAssetsManager.java:185-191,223-235` |
| `tacz_tags/attachments/**`（`allow_attachments` / `adapter` / `intrinsic`） | ✅ `AttachmentsTagManager` 扫 `tacz_tags/attachments`；`allow_attachments/<gun>.json` 单独拆表；`adapter/` 标签被 `SlotAdapterHelper` 展开成适配器候选；`intrinsic/slug` 被 `ModernKineticGunItem.DefaultPropertyModification` 用于独头弹 | `AttachmentsTagManager.java:50,92-103`；`SlotAdapterHelper.java:180-187,243-267`；`api/item/gun/ModernKineticGunItem.java:415-430` |
| `Common*Index` 与 `client/resource/index/Client*` 字段映射 | ✅ `CommonAttachmentIndex`（data/type/pojo/sort，`checkIndex` 校验 type 非空、`checkData` 要求 data 存在）↔ 上游同；`CommonAttachmentVariantIndex`（display/sort/targets/targetTags + `appliesTo` 走 tag 匹配）↔ 上游同；`CommonSlotAdapterIndex`（display/slot/allowedAttachments + `allowsAttachment` 支持 `#tag`）↔ 上游同；客户端 `Client*Index` 由 common index POJO 二次构建（`ClientIndexManager.loadAttachmentIndex/loadAttachmentVariantIndex/loadSlotAdapterIndex`） | `resource/index/CommonAttachmentIndex.java`、`CommonAttachmentVariantIndex.java`、`CommonSlotAdapterIndex.java` 各自 ↔ 上游同名；`client/resource/ClientIndexManager.java:63-78,132-...` |
| `AttachmentItem` NBT 读写一致性（写入端 vs 读取端） | ✅ 键名/类型两端一致：`AttachmentId`(String) / `Skin`(String) / `ZoomNumber`(Int) / `LaserColor`(Int)（`AttachmentItemDataAccessor`）；枪侧 `Attachment<Type>` 复合标签内存整份 `ItemStack.writeToNBT`，读回 `ItemStackNbtHelper.load`（`id=0` 空栈 → null）；变体 `Variant`(String)、适配器 `SlotAdapters`(Compound→String)、虚拟件 `VirtualOemAttachment`(Boolean) 全部两端一致 | `api/item/nbt/AttachmentItemDataAccessor.java:19-22` ↔ 上游 `:...`（键名逐个相同）；`api/item/nbt/ItemStackNbtHelper.java:30-101`；`api/item/nbt/GunItemDataAccessor.java:232-346` ↔ 上游 `:225-333`；`util/VariantHelper.java:31`；`util/SlotAdapterHelper.java:60`；`util/VirtualOemAttachment.java:30` |
| **注**：任务描述里提到的 `AttachmentLevel` / `AttachmentSkin` / `AttachmentSlotAdaptor` 三个键名 | **上游不存在**（上游实际键是 `Skin` / `Variant` / `SlotAdapters`，无 `AttachmentLevel`）；本工程与上游一致，不是缺失 | 全仓 `grep AttachmentLevel\|AttachmentSkin\|AttachmentSlotAdaptor` 仅命中无关类名（`AttachmentSkin` 是客户端皮肤 POJO，不是 NBT key） |

### F. 边界与崩溃面（1.7.10 空栈 = null 逐点）

| 边界 | 结论 | 证据 |
|---|---|---|
| `ItemStack.copy()` 丢 NBT？ | ✅ 1.7.10 `ItemStack#copy` **深拷贝** NBT（`stackTagCompound.copy()`），`beforeRefit` 只用于 trigger 比较，不参与回写 | `ClientMessageRefitGun.java:100`；1.7.10 `ItemStack.java` copy 实现 |
| `stackSize` 复制 / 数量扣减 | ✅ 配件 `maxStackSize=1`（`AttachmentItem` 构造器 `setMaxStackSize(1)`），装/卸是「整栈交换」而非 `decrStackSize`，与上游一致；`ClientMessageRefitGun` 用 `setInventorySlotContents(slot, oldAttachmentItem)` 完成「新件被消费、旧件回到原格」 | `item/AttachmentItem.java:43-45`；`ClientMessageRefitGun.java:105-107` ↔ 上游 `:69` |
| 创造模式是否消耗 | ✅ 与上游一致：改装消息**无**创造分支（点选的配件被换下、旧件回格；创造栏可再取）。唯一创造分支在 `dropAllAmmo`（创造只补满不卸弹），与上游同 | `ClientMessageRefitGun.java:105-111` ↔ 上游 `:69-74`；`api/item/gun/AbstractGunItem.java:258-318` ↔ 上游 `dropAllAmmo` |
| 背包满时退回逻辑 | ✅ 客户端先判 `getFirstEmptyStack()==-1` 且非 marked → 聊天提示 `gui.tacz.gun_refit.unload.no_space`；服务端再判 `attachmentItem!=null && (isMarked || addItemStackToInventory(...))`，失败则**什么都不做**（不卸件、不丢件）。虚拟 OEM 件不占格（marked 豁免） | `GunRefitScreen.java:409-424`；`ClientMessageUnloadAttachment.java:78-93`；`ClientMessageInstallVirtualAttachment.java:97-101` ↔ 上游 `:64-66` |
| `null` 空栈解引用 | ✅ 改装域逐点走查：`IGun.getIGunOrNull(null)`→null；`IAttachment.getIAttachmentOrNull(null)`→null；`getAttachment` 可返回 null 的消费点全部 `isEmpty` 判空（`GunRefitScreen.java:411,425,434,469,527,660`；`GunAttachmentSlot.java:52,72,116,157-177`；`InventoryAttachmentSlot.java:51-55,101-109`；`HSVSliderGroup.java:60-92`；`ClientMessageLaserColor.java:63-64,126-127`；`ClientMessageRefitGun.java:94-97`）；`ItemStackNbtHelper.getOrCreateTag(null)` 返回脱离栈的临时 tag 不 NPE；`VirtualOemAttachment.isMarked(null)`/`VariantHelper.getVariant(null)` 判空 | 各文件见左 |
| `AttachmentItem` 等级/皮肤/适配器越界 | ✅ `ZoomNumber` 消费 `zoomNumber % zooms.length`（`GunItemDataAccessor.java:361-369`）；`Skin` 走 `ClientAttachmentIndex#getSkinIndex(@Nullable)` 判空；适配器 id 经 `ResourceLocationHelper.tryParse`（非法→null→跳过）；`extended_mag_level` 由 `getMagExtendLevel` `Math.min(level,3)` 夹紧，`extendedMagAmmoAmount[level-1]` 长度 < level 时仍会 AIOOBE（**上游同源**，数据约定 `extended_mag_ammo_amount` 长度 ≥3，默认包满足） | `GunItemDataAccessor.java:309-317,349-369`；`AttachmentDataUtils.java:81-120`；`SlotAdapterHelper.java:270-275` |
| GUI 在枪被拿走 / 切换主手时是否 NPE | ✅ `addAttachmentTypeButtons`/`addInventoryAttachmentButtons` 均先 `IGun.getIGunOrNull(player.getHeldItem())`，null 直接 return；`GunPropertyDiagrams.draw` 判 `iGun==null`/`cacheProperty==null`；`onGuiClosed` 判 `gun!=null && gun.getItem() instanceof IGun`；按钮持有的是 `InventoryPlayer` 引用（玩家实体重建时屏幕会被 `GuiDownloadTerrain`/死亡界面替换 → `onGuiClosed` 正常走），无陈旧引用可达 | `GunRefitScreen.java:259-267,279-285,612-627`；`GunPropertyDiagrams.java:42-58` |
| 枚举/ID 越界防护 | ✅ 5 个改装消息在服务端用 `attachmentType==null` 守卫 + 槽位夹紧（见 A）；`readEnum` 越界本身抛 IAE（解码期失败，不断服务端 tick） | `mcshim/PacketBuffer.java:397-405`；各消息 |

---

## 六、静态/脚本校验结果（可复现）

1. **数据引用完整性**（Python 脚本解析默认枪包 JSON，去注释/尾逗号后加载）：
   - `index/attachments` 128 个 → `data` 引用 **128/128 命中**（`data/attachments` 129 个文件，多出 1 个未被 index 引用：`muzzle_duckbill_sg_data.json`；其 index **上游同样缺失**，属上游未发布内容，非移植丢失）；
   - `index/slot_adapters` 6 个 → data 引用 **6/6 命中**；`allowed_attachments` 的 `#tag` 展开后 **全部命中** 128 个附件；
   - 108 个 `allow_attachments/<gun>.json` 的**全部条目**（直引 + `#tag` 递归展开）都能解析到「附件 index」或「slot_adapter index」；唯一解析不到的是 `tacz:muzzle_duckbill_sg`（同上，上游同样缺 index）；
   - `built_in_attachments`（13 把枪）的 `attachments`/`default_attached`/`display_attachment` 经 `#tag` 展开后 **全部在对应枪的 allow 列表内**（否则「虚拟 OEM 配件」装不上 —— 这是本模块最容易静默失效的规则，实测无缺口）。
2. **附件数据顶层键统计**（129 文件）：`weight` 116 / `ads` 83 / `aim_inaccuracy` 53 / `recoil` 41 / `inaccuracy` 28 / `extended_mag_level` 17 / `ads_addend` 17 / `smoke_directions` 16 / `smoke_spread_angle` 16 / `sneak_inaccuracy` 12 / `melee` 10 / `effective_range` 10 / `flash_hider` 10 / `silence` 9 / `ammo_speed` 6 / `damage` 5 / `head_shot` 5 / `armor_ignore` 4 / `rpm` 4 / `pierce` 3 / `lie_inaccuracy` 3 / `explosion` 1 / `ignite` 1 / `knockback` 1 / `recoil_push` 1 —— **全部键都有对应 modifier 的 `getId()` 或 `getOptionalFieldList()` 消费**，无「数据写了但没人读」。
3. **事件注册/总线**：`RefitTransform` 注册 FML 总线（`TickEvent.RenderTickEvent` 只从 FML 总线发：`FMLCommonHandler.java:333-341`）；`GunRefitScreen` 不注册总线（纯 GUI）；`NetworkTaskQueue` 注册 FML 总线（`NetworkTaskQueue.java:57-61`）。
4. **`lint_files`**：`GunRefitScreen.java` error 级 = 0；另抽查 refit 组件 / modifier / util 11 文件 warning 级只有「未使用形参/可换 record/注释标签」类噪声，无 nullability/类型错误。

---

## 七、未修清单（逐条修法 + 需要谁配合）

| # | 级别 | 项 | 修法 | 需要配合 |
|---|---|---|---|---|
| 1 | P3 | `AttachmentIdFix` 死代码：旧配件 id（`muzzle_silence_*`）不会迁移（R4-C-4） | 在 `api/item/nbt/GunItemDataAccessor#getAttachmentTag` 的 `allItemStackTag.getCompoundTag("tag")` 返回处调 `AttachmentIdFix.updateAttachmentIdInTag(tag)`（返回的是 gun NBT 内引用，就地修复；幂等；`getAttachmentTag` 是所有 `getAttachmentId` 的唯一入口）。**不改 `AttachmentItemDataAccessor#getAttachmentIdFromTag`**：那是「纯读」静态方法，`AttachmentItem` 自身 NBT 也应修，但那是另一个入口，可后续再加 | **跨域**：`api/item/nbt/**` 属 api 作用域（本任务写作用域只到 `item/AttachmentItem.java`）。建议 lead 指派 api 侧，或授权本任务追加 |
| 2 | P3 | 5 处消息类注释写「readEnum 越界返回 null」与实现（抛 IAE）不符（R4-C-5） | 只改注释：`ClientMessageUnloadAttachment:67`、`ClientMessageInstallVirtualAttachment:76`、`ClientMessageSetAttachmentVariant:83`、`ClientMessageSetSlotAdapter:81`、`ClientMessageLaserColor:91-93` → 「readEnum 越界抛 IAE（1.20 语义）；此处 null 检查为双保险」 | 无（同在本任务写作用域 `network/message/**`，本轮为控制构建面未改；lead 若要收可一键改） |
| 3 | P3 | 1.7.10 右键也触发按钮（R4-C-6） | 各 `mousePressed` 首行加 `if (Mouse.getEventButton() != 0) return false;`（`GunAttachmentSlot`/`InventoryAttachmentSlot`/`FlatColorButton`/`RefitUnloadButton`/`RefitTurnPageButton`/`HSVSliderGroup.LaserColorSlider`）。**注意**：这是全仓 GUI 平台差异，只改改装域会造成「改装界面右键无效、其它界面右键有效」的不一致；要改建议全局统一 | 全 client GUI 作用域（超出本任务），登记不修 |
| 4 | — | `muzzle_duckbill_sg` 数据缺 index（`#tacz:muzzle_shotgun` 里被引用但无 index/data 的 index） | 上游同样缺（`tmp/TACZ-1.20.1` 同）；属上游未发布内容。**不修**（保真）；若要修需补 `index/attachments/muzzle_duckbill_sg.json`（display 与 geo 资源**都在**，只缺 index） | 数据侧，非代码 |

---

## 八、覆盖清单（诚实）

**逐行精读（本工程）**
- `network/message/`：`ClientMessageRefitGun`、`ClientMessageUnloadAttachment`、`ClientMessageInstallVirtualAttachment`、`ClientMessageSetAttachmentVariant`、`ClientMessageSetSlotAdapter`、`ServerMessageRefreshRefitScreen`、`ClientMessageLaserColor`（+ `MessageBase`、`mcshim/PacketBuffer` 相关段）
- `network/`：`ClientSideHooks`、`SimpleMessageDispatcher`、`NetworkTaskQueue`、`NetworkHandler`（discriminator 段）
- `client/ClientProxy`（Hooks 段）、`client/animation/screen/RefitTransform`、`client/gui/GunRefitScreen`、`client/gui/components/refit/**`（全 8 文件）、`client/gui/components/FlatColorButton`、`client/gui/components/smith/TypeButton`（对照）
- `client/event/`：`FirstPersonRenderGunEvent`（refit 段）、`RenderCrosshairEvent`、`PreventsHotbarEvent`、`ClientGunTimeResetEvent`、`RefreshClonePlayerDataEvent`（refit 段）
- `client/input/`：`RefitKey`、`GunInputHandler`（输入/关界面段）、`util/InputExtraCheck`
- `client/resource/ClientIndexManager`（reload 段）、`client/init/ClientSetupEvent`（激光 resolver 段）
- `util/`：`SlotAdapterHelper`、`AttachmentDataUtils`、`DefaultGunAttachments`、`AllowAttachmentTagMatcher`、`VirtualOemAttachment`、`VariantHelper`、`LaserColorUtil`、`datafixer/AttachmentIdFix`
- `api/`：`item/IGun`、`item/IAttachment`、`item/nbt/{GunItemDataAccessor,AttachmentItemDataAccessor,ItemStackNbtHelper}`、`item/gun/AbstractGunItem`（配件/弹匣段）、`item/builder/GunItemBuilder`、`item/attachment/AttachmentType`、`api/GunProperties`、`api/modifier/**`（5 文件）、`api/event/common/AttachmentPropertyEvent`、`api/entity/IGunOperator`（cache 段）
- `resource/`：`modifier/{AttachmentPropertyManager,AttachmentCacheProperty}`、`modifier/custom/**`（19 文件）、`manager/{AttachmentDataManager,AttachmentsTagManager}`、`index/{CommonAttachmentIndex,CommonAttachmentVariantIndex,CommonSlotAdapterIndex}`、`pojo/{AttachmentVariantIndexPOJO,SlotAdapterIndexPOJO}`、`pojo/data/attachment/AttachmentData`、`serialize/BuiltInAttachmentSerializer`、`CommonAssetsManager`（reload/网络段）、`network/CommonNetworkCache`（附件段）
- `item/AttachmentItem`、`item/ItemClientHooks`、`advancement/GunRefitTrigger`、`event/ChangeGunPropertyEvent`、`mixin/common/LivingEntityMixin`（initialData/tick 段）
- 1.7.10：`GuiScreen`、`GuiButton`、`Minecraft`（runTick/updateCameraAndRender 时序）、`EntityRenderer`（renderHand/GUI 顺序/setupOverlayRendering）、`InventoryPlayer`、`ItemStack`（copy/save/load）、`ChatComponentTranslation`、`StringTranslate`、`StatCollector`、`FMLCommonHandler`、`PlayerControllerMP`（收包时序）

**逐行对照上游**：上面每个 `network/message/*`、`client/gui/**`、`util/*`、`resource/modifier/**`、`api/item/nbt/**` 均与 `tmp/TACZ-1.20.1` 同名文件对照过（共 ~35 文件）。

**未读 / 未逐行**（诚实声明）
- `client/resource/index/ClientAttachmentIndex` 的**模型/皮肤加载**（属 client-resource 作用域，本任务只核了 `getLaserConfig`/`getName`/`getSkinIndex` 的 refit 消费面）；
- `BedrockGunModel#cacheRefitAttachmentViewPath` 之后的**渲染数学**（属渲染作用域；本任务只确认 refit 视角节点数据存在于 geo 模型且消费点齐全）；
- `AttachmentScriptManager`（Lua 编译）本体：只核了 refit 缓存链对 `CompiledScript` 的调用形状，未审 Lua 运行时；
- 枪包 zip 源 / 网络分片传输（`GunPackSync`）—— 属 resource/网络作用域。

---

## 九、不可静态验证项（需实机）

1. **改装界面观感**：本轮新增的 1.20 暗色背景（R4-C-3）是否与上游观感一致；枪体在界面里是否仍清晰可见（1.20 中枪在背景层之下会被压暗，属预期）。
2. **tooltip 修复的实际效果**（R4-C-1）：悬停变体/适配器/隐藏属性图/开火模式按钮应出现提示；被禁用的适配器按钮应显示「需要先卸下配件」。
3. **变体功能整体**：默认枪包**没有** `index/attachment_variants` 数据（上游同），只有第三方包才有变体；`ClientMessageSetAttachmentVariant` 的完整链路（含客户端渲染换模型）需第三方包实测。
4. **槽位适配器**：默认包 6 个适配器（`riser_pistol_*` / `riser_rail_*`，slot=scope，`allowed_attachments` 指向 `#tacz:pistol_sight` 等）——需实机验证「选适配器后能把 pistol 瞄具装到步枪上」以及「适配器与已装配件冲突时按钮置灰 + 提示」。
5. **属性图表数值**：静态只能证明「公式与上游逐行相同 + 数据键全覆盖」，图表条长度/颜色观感需实机。
6. **1.7.10 右键派发差异**（R4-C-6）的实际可感知程度。

---

## 十、r4-10 配件安装失效（实机：换配件没效果 / 从未出现瞄准镜）

> 本节针对 Lead 转达的实机现象：`[TACZ-DBG] scope ... scopeId=tacz:empty hasIdx=false model=false views=-1 scopePos=false`
> 连续三次出现，`IGun#getAttachmentId(stack, SCOPE)` 恒为空 ⇒ ADS 永远走机瞄。
> **本轮结论：安装/读取链路本身没有发现系统性 bug，且已用实机会话存档 NBT 反证「写路径 + 报文链路都通」；
> 未定论的是「为什么当时没有可装/没点成」，已按 Lead 要求把每个 return 点加上日志，等下一次实机日志一句话定论。**

### 10.1 实机证据链（本次审查新拿到的硬证据）

审查期间读取了 `run/client/logs/fml-client-latest.log`（03:22–03:23 会话）与
`run/client/saves/New World/playerdata/56d04c68-f8f7-467c-93aa-7b3b87769210.dat`（该会话 03:23:56 退出时保存，
即**最后一次实机会话的真实状态**，用自写 NBT 解析脚本 dump）：

| 槽位 | 内容 | 关键 NBT |
|---|---|---|
| 0 | `tacz:hk_mp5a5` | `GunId/HasBulletInBarrel/GunCurrentAmmoCount/GunFireMode/`**`AttachmentSTOCK`**`={id:4098,Count:1,Damage:0,tag:{AttachmentId:"tacz:oem_stock_unfolded",VirtualOemAttachment:1}}` |
| 1 | `tacz:ai_awp` | `GunId/HasBulletInBarrel/GunCurrentAmmoCount/GunFireMode/`**`SlotAdapters`**`={"SCOPE":"tacz:riser_rail_unity"}` |
| 2–4 | 2×vanilla 物品（id 363/334）+ 1×草方块（id 2） | **背包里没有任何 attachment 物品** |

由此得到四条**可复现、非推测**的结论：

1. **NBT 写路径正常**：`AttachmentSTOCK` 的嵌套结构 `{id,Count,Damage,tag:{AttachmentId,...}}` 正是
   `GunItemDataAccessor#installAttachment`（ours `:320-334` / upstream `GunItemDataAccessor.java:307-321`）
   用 `ItemStackNbtHelper.save → ItemStack#writeToNBT` 写出的形状；`getAttachmentTag`（ours `:232-245` /
   upstream `:227-244`）读 `tag` 子 compound、`getAttachmentIdFromTag` 读 `AttachmentId` 都能原样读回。
   （`oem_stock_unfolded` 是 `hk_mp5a5_data.json` 的 `builtin_attachments.stock.default_attached`，
   由枪械创建时写入，说明**服务端写 NBT 这条链在实机跑通过**。）
2. **改装 GUI → 报文 → 服务端 → NBT → 客户端刷新 全链路正常**：`SlotAdapters` 这个 key **只可能**由
   `ClientMessageSetSlotAdapter`（`network/message/ClientMessageSetSlotAdapter.java`）写入。
   它能落进存档，等价于证明了：类别槽按钮点击生效 → `changeRefitScreenView` 返回 true → 适配器按钮回调触发 →
   `NetworkHandler.CHANNEL.sendToServer` 到达 → 服务端 `handle` 过了
   `hasAttachmentLock/allowAttachmentType/getAvailableSlotAdapters` 全部门 → `SlotAdapterHelper#setSlotAdapter`
   写 NBT → `ServerMessageRefreshRefitScreen` 刷新。
   ⇒ **「GUI 按钮点不动 / attachmentEntries 恒空 / 报文不通」这三种假设已被实机数据排除。**
3. **`scopeId=tacz:empty` 对这两把枪是正确结果，不是渲染 bug**：`ai_awp_data.json` 没有 `builtin_attachments`，
   `hk_mp5a5` 只有 stock 内置；存档 NBT 里两把枪**确实都没有 `AttachmentSCOPE`**。
   ⇒ 渲染侧（Lead 在查的 `FirstPersonRenderGunEvent`）读到的空 scope 与 NBT 一致，**问题在「没装上」的上游**。
4. **该会话存档里没有任何瞄具物品**：说明当时背包里没有 scope 可装（或装上后又被卸下，但枪 NBT 无痕）。
   结合第 1/2 条，最可能的实机故事是「**改装列表里没有可点的瞄具**」或「**点了但被 allow/适配器过滤**」，
   而不是「装了读不出来」。二者靠 10.2 的日志一条即可分辨。

### 10.2 本轮新增诊断日志（只加日志，零行为改动）

总开关沿用 `RenderHelperCompat.DEBUG_RENDER`（当前 `false`）。新增文件
`src/main/java/com/tacz/guns/util/RefitDebug.java`：common 侧可调用的日志器，
用**反射**读 client 侧 `RenderHelperCompat.DEBUG_RENDER`（避免 common 编译期依赖 client 包），
按内容指纹节流，并提供 `stackInfo()`（id/item 类/NBT 摘要）与 `nbtKeys()`（根 NBT key+类型）两个描述器。
另在 `util/SlotAdapterHelper.java` 新增 `describeAttachmentPermission(...)`，一行输出
`gun/type/att/allowTags 条数/direct 匹配/selectedAdapter/effectiveAdapter/adapterCandidates/typeAllowed/allows` 九个中间量。

| 位置 | 日志 key | 内容 |
|---|---|---|
| `ClientMessageRefitGun#handle` | `refit-gun` / `refit-gun-readback` | 每个 return 点（side/player/bounds/notGun/locked/**notAllowed**/notAttachment）+ `gate` 行 + `install-begin` + **install 后立刻 `install-done readBackId/readBackStack/gunNbtKeys`** + `success` |
| `ClientMessageUnloadAttachment#handle` | `refit-unload` / `refit-unload-readback` | 同上每个 return 点 + `unload-done readBackId/gunNbtKeys` |
| `ClientMessageInstallVirtualAttachment#handle` | `refit-virtual` / `refit-virtual-readback` | 每个 return 点 + `allowedVirtualOem` + `install-done readBackId` |
| `ClientMessageSetAttachmentVariant#handle` | `refit-variant` / `refit-variant-readback` | 每个 return 点 + `variantCheck` + `variant-done` |
| `ClientMessageSetSlotAdapter#handle` | `refit-adapter` / `refit-adapter-readback` | 每个 return 点 + `gate` + `adapter-done readBackAdapter` |
| `GunRefitScreen#initGui` | `refit-gui-init` | transformType / openingProgress / transformProgress / 按钮数 / 手持枪 stackInfo |
| `GunRefitScreen#addInventoryAttachmentButtons` | `refit-gui-list` | 背包候选逐条 `slot:attId:allow=?:perm=[九项]`、虚拟 OEM 候选、最终 entries 数、当前页 |
| `GunRefitScreen` 类别槽回调 | `refit-gui-category` | `isAllow/isCurrent/targetView/changeViewResult/openingProgress/transformProgress`（**能直接证明「点太早导致 changeRefitScreenView=false」**） |
| `GunRefitScreen` 安装/虚拟/卸载回调 | `refit-gui-click` | `gunSlot/type/attSlot|attId/stackInfo`（证明 mousePressed→回调确实触发） |

**grep 命令**（PowerShell，`run/client/logs/latest.log`）：

```powershell
Select-String run/client/logs/latest.log -Pattern '\[TACZ-DBG\] refit'                 # 全部
Select-String run/client/logs/latest.log -Pattern 'refit (gate|reject|install-|success)' # 服务端判定
Select-String run/client/logs/latest.log -Pattern 'refit (gui-|install-done)'            # GUI + 读回
```

**判读表**（一次点击即可定论）：

| 现象 | 结论 / 下一步 |
|---|---|
| 只有 `refit gui-init`，无 `gui-category` | 类别槽按钮没被点到（坐标/遮挡）或 `mousePressed` 未派发 |
| `gui-category ... changeViewResult=false` + `openingProgress<1` | **点太早**（`RefitTransform` 要求 opening/transform progress 都=1，约开屏 0.25s）；属交互时序，不是安装 bug |
| `gui-category changeViewResult=true` 但无 `gui-list` | `initGui` 后 `getCurrentTransformType()` 又被重置（`RefitTransform.init()` 重入） |
| `gui-list ... invCandidates=0` 且 `virtualOemCandidates=0` | **背包里没有该类型配件**（数据/操作侧，非 bug；与 10.1 第 4 条一致） |
| `gui-list` 里 `allow=false` + `perm=[... direct=false allowTags=N ...]` | 标签/适配器过滤；`allowTags=0/null` ⇒ `allow_attachments` 未加载（数据侧）；`selectedAdapter` 非空且 `allows=false` ⇒ 适配器把该配件挡了（上游语义，非 bug） |
| 有 `gui-click` 但服务端无 `refit-gun gate` | 报文没到服务端（网络层，转 Lead 的 `NetworkTaskQueue`/`SimpleNetworkWrapper` 面） |
| `refit-gun reject:notAllowed` | 服务端 allow 拒绝，`perm=[...]` 直接给出九项原因 |
| `refit-gun install-done readBackId=tacz:empty` 且 `gunNbtKeys` 含 `AttachmentSCOPE:10` | **写成功、读失败** ⇒ 根因在 `getAttachmentTag` 的 `allowAttachmentType` 门或 NBT 结构（届时按 `AttachmentSCOPE:10` 是否存在直接定位） |
| `install-done readBackId=非空` + 客户端 `gui-init held=` 里 NBT 有 `AttachmentSCOPE` | 安装成功；若渲染仍空，才转渲染侧 |

### 10.3 上游逐行对照（本次复核：`allowAttachment/installAttachment/hasAttachmentLock` 无漂移）

| 我方 | 上游 | 结论 |
|---|---|---|
| `api/item/gun/AbstractGunItem.java:419-428` `allowAttachment` | `api/item/gun/AbstractGunItem.java:304-313` | **逐行一致**（`getIAttachmentOrNull`+`getIGunOrNull` 双非空 → `getType`+`getAttachmentId` → `SlotAdapterHelper.allowsAttachment`） |
| `AbstractGunItem.java:434-450` `allowAttachmentType` | 上游 `:319-332` | **逐行一致**（`getCommonGunIndex → getGunData().getAllowAttachments()`，null→false） |
| `api/item/nbt/GunItemDataAccessor.java:320-334` `installAttachment` | `GunItemDataAccessor.java:307-321` | **逐行一致**（`allowAttachment` 再校验 → `getOrCreateTag` → `Attachment<TYPE>` = `writeToNBT` 结果） |
| `GunItemDataAccessor.java:337-346` `unloadAttachment` | 上游 `:324-333` | 一致（`writeEmptyTag` ≡ `ItemStack.EMPTY.save`） |
| `GunItemDataAccessor.java:96-102` `hasAttachmentLock` | 上游 `:91-97` | 一致（`AttachmentLock` TAG_BYTE；实机存档无此 key ⇒ false，非本次原因） |
| `GunItemDataAccessor.java:232-245 / 274-284 / 311-317` | 上游 `:227-244 / 264-273 / 298-303` | 一致；**`getAttachmentTag` 开头的 `allowAttachmentType` 门是上游就有的**（不是移植新增的读失败点） |
| `util/SlotAdapterHelper.java:212-224` `allowsAttachment` | `util/SlotAdapterHelper.java:111-122` | 一致（选中适配器时**替换**直连路径，而不是并集——这是上游语义，不是 bug） |
| `util/AllowAttachmentTagMatcher` / `resource/manager/AttachmentsTagManager` / `resource/pack/GunPackResources#fileToId` | 同名文件 | id 推导 `data/<ns>/tacz_tags/attachments/allow_attachments/ai_awp.json → tacz:allow_attachments/ai_awp → gunId tacz:ai_awp` 正确；实机运行包 `run/client/tacz/tacz_default_gun` 与 `src/main/resources/.../custom/tacz_default_gun` 的 `allow_attachments` **各 67 个文件、逐文件一致**；且 10.1 第 2 条已反证 tag 加载可用（适配器候选非空才能点出 `riser_rail_unity`） |
| `api/item/nbt/AttachmentItemDataAccessor`（`AttachmentId`/`Skin`/`ZoomNumber`/`LaserColor`、`isAttachmentLike`） | 同名文件 | 一致；1.7.10 `getTagCompound()` 可空已由 `ItemStackNbtHelper.getTag/getOrCreateTag` 收口（后者 null 时新建 tag 并 `stack.setTagCompound` 回写，不存在「写了但没挂到 stack 上」） |

**两个容易误判的点（澄清 Lead 的疑点 2）**：
1. `allow_attachments` **不在** `GunIndex`/`ClientGunIndex` 里——上游与本工程都把它放在 `AttachmentsTagManager`
   （`data/<ns>/tacz_tags/attachments/allow_attachments/<gun>.json`），运行期只经 `AllowAttachmentTagMatcher.match` 消费；
   `GunData.allow_attachment_types` 才是 `IGun#allowAttachmentType` 的数据源，两者是**两套不同数据**，本次两套都核过。
2. `getTagCompound()` 为 null 的 1.7.10 边界在 refit 链路里没有裸用：写侧 `getOrCreateTag` 会回写、读侧 `getTag` 判空，
   `AttachmentItemDataAccessor` 的 4 个 getter 都走这两个 helper。

### 10.4 GUI 点击链路静态核对（结论：1.7.10 下会触发）

- `client/gui/components/refit/InventoryAttachmentSlot.java:87-95` 覆写 `mousePressed`：`super.mousePressed(...)` 命中后
  直接 `onPress.accept(this)`；**没有** 1.20 的 `onDrag/onRelease` 拖拽分支，1.7.10 语义更简单。
- `client/gui/components/refit/GunAttachmentSlot.java:139-141` 同样覆写 `mousePressed`。
- 1.7.10 `GuiScreen.mouseClicked`（`build/rfg/minecraft-src/.../GuiScreen.java:237-245`）遍历 `buttonList` 调
  `mousePressed`；`GunRefitScreen#add`（`:206-208`）就是 `buttonList.add`。⇒ 回调会触发。
- 唯一 1.7.10 差异：`GuiScreen.mouseClicked` 对**任意鼠标键**都派发（R4-C-6，右键也会触发按钮），不影响左键安装。
- **行号提醒**：`GunRefitScreen.java` 在 r4-10 期间被并发 `spotless` 重排 + 补了上游的
  `getCurrentTransformType()==NONE` 提前返回（`:275-279`），因此本文档 §三/§五里 r4-10 之前的旧行号整体偏移。
  当前锚点：`drawScreen :211`、暗色背景 `:220`、`FlatColorButton` tooltip 分支 `:243`、`onGuiClosed :751`、
  `indexName :842`、`initGui :150`、`add :206`、`addInventoryAttachmentButtons :275`。

### 10.5 未定论 + 下一次实机需要的最小日志集

**未定论**：为什么当时没装上瞄具。已知排除：写路径、报文链路、GUI 回调、`allow_attachment_types` 加载、tag 加载、
`hasAttachmentLock`。剩余两个候选：
1. **背包里没有 scope 物品**（与存档一致，可能性最高）——`gui-list invCandidates=0` 一行即可确认；
2. **点了但被 allow/适配器过滤**——`gui-list` 的逐条 `perm=[...]` + `reject:notAllowed` 一行即可确认。

**下一次实机请只做一件事**：把 `RenderHelperCompat.DEBUG_RENDER` 置 `true`，进游戏 → 拿 `ai_awp` →
开改装界面 → 点 SCOPE 类别 → 点列表里任意瞄具（若列表为空也请点一次类别）→ 退出。
然后把 `Select-String run/client/logs/latest.log -Pattern '\[TACZ-DBG\] refit'` 的结果贴回来。

### 10.6 本轮改动文件（lint 均 0 error，未跑 gradle）

- 新增 `src/main/java/com/tacz/guns/util/RefitDebug.java`
- `src/main/java/com/tacz/guns/util/SlotAdapterHelper.java`（+`describeAttachmentPermission`，纯诊断）
- `src/main/java/com/tacz/guns/network/message/{ClientMessageRefitGun,ClientMessageUnloadAttachment,ClientMessageInstallVirtualAttachment,ClientMessageSetAttachmentVariant,ClientMessageSetSlotAdapter}.java`（只加日志/守卫式 early-return，逻辑与改前逐行等价；`git diff` 删除行仅注释）
- `src/main/java/com/tacz/guns/client/gui/GunRefitScreen.java`（只加诊断日志；类别槽回调按上游三分支重排为等价的 `targetView` 形式，行为不变）
- 复核方式：`mcp__idea__lint_files(min_severity=error)` → 8 个文件 0 error；**未执行任何 gradle 命令**。
