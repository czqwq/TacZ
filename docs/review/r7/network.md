# r7-network · 网络包与客户端-服务端同步 上游等价性审查

对照基准：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/network/**`（47 文件）、1.20.1 原版 `CustomPayload`/`StreamCodec`、
1.20.1 Forge `SimpleChannel`/`PacketDistributor`/`NetworkEvent`、1.7.10 `build/rfg/minecraft-src/java/**`
（`SimpleNetworkWrapper`/`IMessage`/`IMessageHandler`/`MessageContext`/`FMLProxyPacket`/`NetworkRegistry`/`NetworkManager`）。
本轮额外对照：`tmp/MinecraftForge-1.20.1/.../network/PacketDistributor.java`。

本轮读过的上游/平台文件（逐行）：
`network/NetworkHandler.java`、`network/IMessage.java`、`network/LoginIndexHolder.java`、上游 42 个主 channel 消息 +
2 个 handshake 消息；平台 `SimpleNetworkWrapper.java`、`SimpleIndexedCodec.java`、
`FMLIndexedMessageToMessageCodec.java`、`SimpleChannelHandlerWrapper.java`、`MessageContext.java`、
`FMLProxyPacket.java`、`FMLEmbeddedChannel.java`、`NetworkRegistry.java`、`NetworkManager.java`、
`EntityTracker.java`、`EntityTrackerEntry.java`、`FMLCommonHandler.java`、`PacketDistributor.java`。

---

## 1. 摘要

| 结论 | 内容 |
|---|---|
| **包清单** | 上游 47 文件 = 44 个消息/握手类 + 3 个基础设施（`IMessage`/`LoginIndexHolder`/`NetworkHandler`）。移植侧 52 文件 = **44 个上游消息一一对应** + **1 个移植特有消息**（`ServerMessageSyncConfig`）+ **7 个支撑类**（`NetworkHandler`、`MessageBase`(= 上游 `IMessage`) 为对应改造；上游 `LoginIndexHolder` 删除；新增 `SimpleMessageDispatcher`、`NetworkTaskQueue`、`ClientSideHooks`、`SyncConfigSyncManager`、`AbstractShooterGunEventMessage`）。**无遗漏、无重复语义**，见 §2 全量表。 |
| **discriminator** | 主 channel 上游 `ID_COUNT` 从 **1** 起（`NetworkHandler.java:36`），注册顺序 1..42 与移植侧 `NetworkHandler.java:116-332` **逐位一致**；handshake 上游 1(Acknowledge)→2(Mapping)，移植一致；移植侧新增 43 号 `ServerMessageSyncConfig`（追加在末尾，不影响既有编号）。**注**：`docs/review/r5/data-wiring.md:150` 写「`ID_COUNT` 从 0 开始」是笔误，实际从 1 开始（两侧仍逐位相同）。 |
| **方向映射** | 1.7.10 `registerMessage(handler, type, discriminator, Side)` 的 `Side` = **接收侧**，与 1.20 `NetworkDirection.PLAY_TO_SERVER/CLIENT` 同义。平台侧验证：`FMLProxyPacket(C17PacketCustomPayload)`→`target=SERVER`（`FMLProxyPacket.java:44-48`）、`FMLProxyPacket(S3FPacketCustomPayload)`→`target=CLIENT`（`:38-42`），`processPacket` 用 `getChannel(channel, target)`（`:80`）取 `channels.get(target)` 的 pipeline，而 `registerMessage` 正是把 handler 加进 `channels.get(side)`（`SimpleNetworkWrapper.java:129-142`）。**21 个 C2S 全部注册 `Side.SERVER`、22 个 S2C 全部注册 `Side.CLIENT`，45/45 正确。** |
| **字段/编解码** | 45 个消息的 `write`↔`read` 逐字段同序同类型（与 r5 结论一致，本轮逐文件复核）。**无一对错位**。`readEnum` 越界抛 `IllegalArgumentException`（1.20 语义）；带容器/数组的消息都有上界（`ServerMessageUpdateEntityData`、`ServerMessageAmmoStorageContents`、`ServerMessageSyncGunPack#readCache`、`SyncConfig#readList`）。 |
| **处理线程** | **上游 `ctx.enqueueWork` 在 1.7.10 不需要切线程**：1.7.10 的包体由 Netty 解码后进入 `NetworkManager.receivedPacketsQueue`，由**主线程** `processReceivedPackets()` 逐包 `processPacket`（`NetworkManager.java:118-130`、`:236-242`），再 `EmbeddedChannel#writeInbound` → `FMLIndexedMessageToMessageCodec.decode`（`:64-79`）→ `SimpleChannelHandlerWrapper.channelRead0`（`:32-43`）。移植侧 `NetworkTaskQueue` 只承担「延后到本站 tick 末尾」，与上游「处理器返回后再执行」一致。 |
| **本轮修的真实偏离** | **P1：`NetworkTaskQueue` 单队列被两条线程同时排空**（单人/局域网下 C2S 任务可能被客户端 tick 取走、S2C 任务可能被服务端 tick 取走 ⇒ 在**错误线程**改世界状态）。已改为**按接收侧两个队列**（入队带 `ctx.side`），并修正 `NetworkTaskQueue`/`ClientMessagePlayerShoot`/`AbstractShooterGunEventMessage` 里「1.7.10 simpleimpl 在 Netty 线程回调」的事实错误注释。 |
| **诊断噪声** | 按任务要求清掉 `network/**` 的 `[TACZ-DBG]` 诊断：5 个 refit 消息类 **54 处 `RefitDebug.log(...)` 调用 + 每处指纹/`stackInfo` 拼接 + 5 处 javadoc 声明**全部删除（净 −371 行）。`util/RefitDebug` 类本体保留（`client/gui/GunRefitScreen`、`util/SlotAdapterHelper` 仍在用，不属本 scope）。 |
| **Lint** | 变更 38 个文件，`lint_files(min_severity="error")` = **0 error**（52/52 个 network 文件全量复检）；无新增 warning（仅上游同形态的「重复代码段」weak warning）。未运行 gradle。 |

---

## 2. 上游包 ↔ 移植包对照表（全量）

### 2.1 基础设施（上游 3 + 移植新增 7）

| 上游文件 | 移植文件 | 语义对照 / 判定 |
|---|---|---|
| `network/IMessage.java`（`encode/decode/handle` 三方法接口） | `network/MessageBase.java` | 1.20 的「纯数据类 + static 方法引用」在 1.7.10 必须变成「实现 `IMessage` 的类」。`MessageBase` 把 `fromBytes/toBytes`（final）适配成 upstream 形态的 `read/write(PacketBuffer)`，字段语义与上游 `encode/decode` 正文一一对应。**等价**。 |
| `network/LoginIndexHolder.java`（login-packet 索引） | —（删除） | 1.7.10 `SimpleNetworkWrapper` **没有 login packet / `HandshakeHandler`**。`getLoginIndex/setLoginIndex` 无落点，删；握手改由 `SyncConfigSyncManager` 在 `PlayerLoggedInEvent` 发 PLAY 包。**有据降级**（§4-1）。 |
| `network/NetworkHandler.java` | `network/NetworkHandler.java` | 同名同职责：两个 channel（`tacz` / `tacz:handshake`）、5 个发送 helper、`init()` 注册 42+1 条。`SimpleChannel`→`SimpleNetworkWrapper`；`PacketDistributor.{PLAYER,ALL,TRACKING_ENTITY[_AND_SELF],DIMENSION}`→`sendTo/sendToAll/EntityTracker/sendToDimension`。**等价**（见 §3 的 helper 语义核对）。 |
| — | `network/SimpleMessageDispatcher.java` | 方法引用 → `IMessageHandler` 桥。**必须每个注册点用独立匿名子类**（FML 用 `handler.getClass().getName()` 当 pipeline handler 名，重名即 `Duplicate handler name` 崩溃）——`NetworkHandler` 45 个注册点全部满足。 |
| — | `network/NetworkTaskQueue.java` | `ctx.enqueueWork` 的替代。**本轮改为按侧两队列**（修复线程错配）。 |
| — | `network/ClientSideHooks.java` | 替代上游散落在消息包里的 `@OnlyIn(Dist.CLIENT)` / `DistExecutor.unsafeRunWhenOn` 分支：common 不得编译期依赖 client 包，故用注入式接口，由 `ClientProxy.java:168` 注入实现（已核实 `ClientSideHooks.set` + 10 个方法均有实现或有意 no-op）。 |
| — | `network/SyncConfigSyncManager.java` | 替代上游「login 握手 + Forge 自动同步 `ModConfig.Type.SERVER`」两件事（`PlayerLoggedInEvent` 里发 Mapping + SyncConfig）。 |
| — | `network/message/ServerMessageSyncConfig.java` | 替代 Forge SERVER 配置同步；43 号 discriminator；字段顺序 = `config/sync/SyncConfig` 的 21 项，`apply()` 只改内存不落盘（上游「SERVER 配置客户端只读」语义）。**移植特有，上游无对应物**。 |
| 上游 `message/event/` 7 个类各自重复的客户端分发样板 | `network/message/event/AbstractShooterGunEventMessage.java` | 新增基类消除 7×重复（字段 `shooterId`+`gunItemStack`、`write/read`、`handleOnClient`）。**每个子类的 `postEvent` 与上游逐一对应**（GunFire/GunFirePost/GunFireSelect/GunFinishReload/GunMelee/GunReload/GunShoot），无行为差。 |

### 2.2 C2S（客户端 → 服务端）21 条 + `Acknowledge`

| # | discriminator | 上游类 → 移植类 | 字段（W 顺序） | 服务端校验（上游 ↔ 移植） |
|---:|---:|---|---|---|
| 1 | 1 | `ClientMessagePlayerShoot` | long, float | 均为 `IGunOperator.fromLivingEntity(player).shoot(旋转角 supply, timestamp, chargeProgress)`；移植用 `rotationPitch/rotationYaw`（= `getXRot/getYRot`）。**等价** |
| 2 | 2 | `ClientMessagePlayerReloadGun` | 无 | `reload()`。**等价** |
| 3 | 3 | `ClientMessagePlayerCancelReload` | 无 | `cancelReload()`。**等价** |
| 4 | 4 | `ClientMessagePlayerFireSelect` | 无 | `fireSelect()`。**等价** |
| 5 | 5 | `ClientMessagePlayerAim` | bool | `aim(isAim)`。**等价** |
| 6 | 6 | `ClientMessagePlayerCrawl` | bool | `crawl(isCrawl)`（进入条件在服务端趴下逻辑内校验，两侧一致）。**等价** |
| 7 | 7 | `ClientMessagePlayerDrawGun` | 无 | `inventory.currentItem` + `getStackInSlot` 取主手后 `draw(supplier)`。**等价** |
| 10 | 10 | `ClientMessageCraft` | RL, varint | `openContainer.windowId == menuId && container instanceof GunSmithTableMenu` → `doCraft(recipeId, player)`。**等价**（移植多判 `openContainer != null`，防御性） |
| 12 | 12 | `ClientMessagePlayerZoom` | 无 | `zoom()`。**等价** |
| 13 | 13 | `ClientMessageRefitGun` | int, int, enum | 上游：取枪栈→非枪返回→**配件锁**→`allowAttachment`→取配件真实类型→install→trigger→属性刷新→把旧配件放回该槽（虚拟 OEM 标记则清空）→扩容弹匣倒弹→`broadcastChanges`→发 `ServerMessageRefreshRefitScreen`。移植**同一顺序同一分支**（本轮把被 debug 分支切碎的逻辑重排回上游直读顺序），外加**槽位夹紧**（1.7.10 `getStackInSlot` 越界 AIOOBE，上游 `getItem` 返回 `EMPTY`）。**等价 + 平台防护** |
| 14 | 14 | `ClientMessageInstallVirtualAttachment` | int, enum, RL | 上游：锁→`isAllowedVirtualOem`→builder 建虚拟件→类型/`allowAttachment`→**背包无空位且旧配件非虚拟则拒绝**→`mark`→install→旧件入包→属性刷新。移植逐项一致；`getFreeSlot()`→`getFirstEmptyStack()`（见 §4-11）。**等价（更严格）** |
| 16 | 16 | `ClientMessageUnloadAttachment` | int, enum | 上游：锁→取配件→`!isEmpty() && (标记 || inventory.add())` 才卸载→trigger→属性刷新→扩容弹匣倒弹→刷新。移植同判据（`null` 代替 `isEmpty()`）。**等价** |
| 18 | 18 | `ClientMessagePlayerBoltGun` | 无 | `bolt()`。**等价** |
| 24 | 24 | `ClientMessagePlayerMelee` | 无 | `melee()`。**等价** |
| 32 | 32 | `ClientMessageSyncBaseTimestamp` | 无 | 在**入队前**取 `GunTime.nowMillis()` 写入 `dataHolder.baseTimestamp`（上游同样在 netty 线程先取时间再 `enqueueWork`）。**等价** |
| 33 | 33 | `ClientMessageLaserColor` | map(enum→int), bool, int, int | 上游：`gunSlotIndex == -1` 直接返回；按 `colorMap` 给各配件写色 + 可选枪身色。移植同判据 + **槽位夹紧**（`getStackInSlot`）。`HashMap`→`EnumMap`（保枚举序，无语义差）。**等价 + 平台防护** |
| 34 | 34 | `ClientMessageAmmoStorageInteract` | 3×int | 上游：`isSpectator` 拒绝→**5 tick 拾取冷却**→**距离²≤36**→主手必须是枪→按枪包 `ammoId`→方块必须是 `AmmoStorageBlockEntity`→取一组→失败时也写冷却并提示→入包失败则掉落→**累加计数（20 tick 窗口 + 同 ammoId）**→提示 + 音效。移植**全部保留**（仅去掉无对应物的 spectator 判定、action bar→聊天栏、音效名降级）。**等价** |
| 35 | 35 | `ClientMessageSetAttachmentVariant` | int, enum, bool(+RL) | 上游：锁→取该槽配件→`attachment==null \|\| isEmpty()` 返回→`variantId==null` 走 `removeVariant`、否则 **`CommonAttachmentVariantIndex.appliesTo(attachmentId)` 校验**→`setVariant`→`installAttachment` 回写→trigger→属性刷新→刷新界面。移植逐项一致 + 槽位夹紧/null 判据。**等价** |
| 36 | 36 | `ClientMessageSetSlotAdapter` | int, enum, bool(+RL) | 上游：`NONE` 拒绝→`getContainerSize` 夹紧→锁 + `allowAttachmentType`→已装配件类型必须匹配→`adapterId==null` 时要求「已装配件本身就允许直装」→否则**在可用 adapter 表中查找且必须 `allowsAttachment(已装配件)`**→写/删 adapter→属性刷新→trigger→刷新。移植逐项一致（`stream().filter+anyMatch`→`Map.get`，语义同）。**等价** |
| 37 | 37 | `ClientMessageAmmoStorageClick` | varint, varint, enum | `openContainer.windowId == containerId && instanceof AmmoStorageMenu` → `handleInteraction(player, entryId, action)`。**等价**（上游 record → 普通类 + 同名访问器） |
| 41 | 41 | `ClientMessagePlayerUnloadGun` | varint, RL | 上游：存活/非旁观/`slot∈[0,9)`/`selected==slot`→主手枪且 **gunId 与服务端一致**→枪包索引存在→`canUnloadAmmo && !useInventoryAmmo`→**卸载前弹药计数（含膛内 1 发，`Bolt.OPEN_BOLT` 除外）为 0 则提示**→`unloadAmmo`→**卸载后计数真的减少才发 `ServerMessageGunUnload`**，否则提示失败。移植逐项一致（去掉 spectator）。**等价** |
| H1 | H1=1 | `handshake/Acknowledge` | 无 | 上游 `setPacketHandled(true)` + debug 日志。移植同样只打日志（服务端不需要 ACK 做任何事）。**等价** |

### 2.3 S2C（服务端 → 客户端）21 条 + 握手映射

| # | discriminator | 上游类 → 移植类 | 字段（W 顺序） | 客户端落地（上游 ↔ 移植） |
|---:|---:|---|---|---|
| 8 | 8 | `ServerMessageSound` | varint, RL, RL, utf, float, float, int | `SoundPlayManager.playMessageSound` ↔ `ClientSideHooks.playSound`（实现即 `SoundPlayManager.playMessageSound`）。**等价** |
| 9 | 9 | `ServerMessageBulletHitBlock` | 3×double, enum, 3×int, 3×utf, bool | `HitBlockEffectHandler.handle(level, pos, dir, blockPos, ammoId, gunId, displayId, incendiary)`。`Direction`→`ForgeDirection`（`EntityKineticBullet` 用 `ForgeDirection.getOrientation(sideHit)`，1.7.10 sideHit 0..5 与 ForgeDirection 序数同序，**映射正确**）、`BlockPos`→3×int、`Vec3`→`net.minecraft.util.Vec3`。**等价（平台替换）** |
| 11 | 11 | `ServerMessageCraft` | varint | `containerMenu.containerId==id && screen instanceof GunSmithTableScreen` → `updateIngredientCount()` ↔ `openContainer.windowId` + `currentScreen instanceof …`。**等价** |
| 15 | 15 | `ServerMessageRefreshRefitScreen` | 无 | `screen.init()` + `AttachmentPropertyManager.postChangeEvent(player, mainHand)` ↔ `initGui()` + 同调用。**等价** |
| 17 | 17 | `ServerMessageSwapItem` | 无 | 上游 `MinecraftForge.EVENT_BUS.post(new SwapItemWithOffHand())`；移植同。但**上游唯一生产者**是 `mixin/common/ServerPlayNetHandlerMixin`（副手交换）——1.7.10 无双持，移植侧无生产者、client hook no-op。**有据降级**（§4-5） |
| 19 | 19 | `ServerMessageLevelUp` | item, int | 上游 `onLevelUp` 全是注释掉的 TODO（`ServerMessageLevelUp.java:49-63`），且**上游也无生产者**；移植保留字段/getter + 空实现。**等价** |
| 20 | 20 | `ServerMessageGunHurt` | 3×int, 2×RL, float, bool, float | 投递 `EntityHurtByGunEvent.Post(..., null 伤害源对, Side.CLIENT)`；三个实体 id 全部走 `getEntityById`（缺失即 null，与上游 `level.getEntity` 一致）。**等价** |
| 21 | 21 | `ServerMessageGunKill` | 3×int, 2×RL, float, bool, float | 投递 `EntityKillByGunEvent`。**等价**（字段顺序与上游同为 bulletId,killEntityId,attackerId,gunId,gunDisplayId,baseDamage,isHeadShot,multiplier） |
| 22 | 22 | `ServerMessageUpdateEntityData` | varint, varint(size), N×DataEntry | `SyncedEntityData.set(entity, key, value)` 逐条写入。移植加 **size ≤ 可读字节数** 上界（1.20 由 Netty 保证包长，1.7.10 无 ⇒ 防 OOM）。**等价 + 防护** |
| 23 | 23 | `ServerMessageSyncGunPack` | UUID, 3×varint, byte[] | 上游：**收端连接身份校验**（`connection == Minecraft.getConnection().getConnection() && isConnected`）→`Receiver.accept` 收齐→读缓存→**非内存连接时 `CommonAssetsManager.clearInstance()`**→`CommonNetworkCache.fromNetwork`→`ClientIndexManager.reload()`；异常则清分片 + 断线。移植：`Receiver`/`readCache`/`Part` 契约与分片尺寸（256 KiB）与上游一致，`clearInstance` 由 `Minecraft.isSingleplayer()` 判定，收齐后 `readCache` + `ClientIndexManager.reload` + `GunPackResourcePack.invalidateCache()`（移植必要的贴图缓存失效）。**差异**：不校验连接身份（1.7.10 消息里拿不到 `NetworkManager`）、失败只 log 不踢人。**有据降级**（§4-10、§5-2） |
| 25 | 25 | `event/ServerMessageGunDraw` | varint, item, item | `GunDrawEvent(entity, prev, current, Side.CLIENT)`。**等价** |
| 26 | 26 | `event/ServerMessageGunFire` | varint, item | `GunFireEvent`。**等价** |
| 27 | 27 | `event/ServerMessageGunFireSelect` | 同上 | `GunFireSelectEvent`。**等价** |
| 28 | 28 | `event/ServerMessageGunMelee` | 同上 | `GunMeleeEvent`。**等价** |
| 29 | 29 | `event/ServerMessageGunReload` | 同上 | `GunReloadEvent`。**等价** |
| 30 | 30 | `event/ServerMessageGunShoot` | 同上 | `GunShootEvent`。**等价** |
| 31 | 31 | `ServerMessageSyncBaseTimestamp` | 无 | 上游：取 `nowMillis` → `enqueueWork(update clientBaseTimestamp)` → `CHANNEL.reply(new ClientMessageSyncBaseTimestamp())`。移植同（`reply` → 显式 `sendToServer`；1.7.10 simpleimpl 无 context reply）。**等价（机制替换）** |
| 38 | 38 | `ServerMessageAmmoStorageContents` | varint, varint, varlong, varint(size), N×(varint,item,varlong,varlong) | `AmmoStorageMenu.setContents(...)`；`size ≤ STORAGE_SLOT_COUNT` 上界与上游一致。**等价**（record → 普通类 + 同名访问器） |
| 39 | 39 | `event/ServerMessageGunFinishReload` | varint, item | `GunFinishReloadEvent`。**等价** |
| 40 | 40 | `event/ServerMessageGunFirePost` | 同上 | `GunFirePostEvent`。**等价** |
| 42 | 42 | `ServerMessageGunUnload` | RL, RL | 上游 `SoundPlayManager.playUnloadSound(player, gunId, displayId)` ↔ `ClientSideHooks.gunUnload`（实现同一调用）。**等价** |
| **43** | **43** | **（移植特有）** `ServerMessageSyncConfig` | 6×list, 6×bool, 2×int, 6×double | 覆盖 `SyncConfig` 的 21 项；`null` 列表写 0 长；只改客户端内存。**移植特有，上游由 Forge 承担** |
| H2 | H2=2 | `handshake/ServerMessageSyncedEntityDataMapping` | int(size), N×(RL, RL, varint) | 上游：`CountDownLatch` 阻塞网络线程 → `SyncedEntityData.updateMappings` → 失败即 `disconnect` → `reply(Acknowledge)`。移植：写入快照（`snapshot()` 在 `write` 时读活注册表，与上游 `encode` 读注册表同语义）→ 客户端主线程 `updateMappings` → 失败**只记 error**（不踢人）→ 回 `Acknowledge`。**等价（降级见 §4-1/§4-10）** |

### 2.4 发送侧 helper（`PacketDistributor` ↔ 1.7.10）

| 上游 | 移植 | 平台核对 |
|---|---|---|
| `PLAYER.with(player)` | `sendToClientPlayer(msg, player)` | **等价**；移植多 `instanceof EntityPlayerMP` 守卫（上游是裸 `(ServerPlayer) player` 强转）。 |
| `ALL.noArg()` | `sendToAllPlayers(msg)` | **等价**（`SimpleNetworkWrapper#sendToAll`）。 |
| `TRACKING_ENTITY.with(e)` | `sendToTrackingEntity(msg, e)` | **等价**：上游 `ServerChunkCache.broadcast`（不含自己）；1.7.10 `EntityTracker#getTrackingPlayers` 的 `trackingPlayers` 集合**不含实体本尊**（`EntityTrackerEntry.java:366` `if (p != this.myEntity)`），同样不含自己。 |
| `TRACKING_ENTITY_AND_SELF.with(e)` | `sendToTrackingEntityAndSelf(e, msg)` | **等价**：= tracking ∪ self（1.20 `broadcastAndSend` = `broadcast` + `func_151261_b` 给本尊，见 `EntityTrackerEntry.java:335-337`）。参数顺序 `(entity, message)` 与上游一致（上游本身与其它 helper 相反，移植保留该形状）。 |
| `DIMENSION.with(dim)` | `sendToDimension(msg, entity)` | **等价**：上游取 `centerEntity.level().dimension()`，1.7.10 取 `worldObj.provider.dimensionId`。 |

---

## 3. 本轮修复（真实移植偏离）

| # | 级别 | 现象 | 根因（证据） | 修法 | 影响面 |
|---:|---|---|---|---|---|
| 1 | **P1** | 单人 / 局域网内嵌服务端下，网络包的处理任务可能在**错误线程**执行（C2S 包的任务被客户端 tick 取走、S2C 包的任务被服务端 tick 取走）⇒ 跨线程改世界/玩家/背包状态，数据竞争 | `NetworkTaskQueue` 只有**一个**静态队列，`onServerTick`（服务端线程）与 `onClientTick`（客户端线程）都调 `runTasks()` 全量排空。1.7.10 的内嵌服务端跑在自己的 `Server thread` 上，两个 tick 事件并发（`FMLCommonHandler.java:245-248`、`:328-331`）。上游 `enqueueWork` 语义是「在**该逻辑侧**的线程执行」，移植把这条不变量丢了 | 队列按接收侧拆成两份；`enqueue(Side, Runnable)` 用消息处理器里的 `ctx.side` 决定入哪一份；`runTasks(Side)` 只排空自己那一侧。37 个入队点全部改为 `enqueue(ctx.side, …)`（编译期强制传侧，无法漏传） | `network/NetworkTaskQueue.java` + 37 处调用点（38 文件，见 §6） |
| 2 | P3 | 三处注释断言「1.7.10 simpleimpl 在 Netty IO 线程回调 `onMessage`」，与实际不符，会误导后续把 `enqueueWork` 当成线程切换、甚至据此去掉/保留错误的同步 | 实际分发路径：`NetworkManager.channelRead0` 把非优先包放进 `receivedPacketsQueue`（`:118-130`），主线程 `processReceivedPackets()` 逐包 `processPacket`（`:236-242`）→ `FMLProxyPacket.processPacket` → `writeInbound`（`FMLProxyPacket.java:80-86`）→ `FMLIndexedMessageToMessageCodec.decode`（`:64-79`）→ handler | 注释改为「已由主线程分发，本类只做延后到本站 tick 末尾执行」，并写清证据行号 | `NetworkTaskQueue`、`ClientMessagePlayerShoot`、`AbstractShooterGunEventMessage` |
| 3 | P3 | 5 个 refit 消息类里 54 处 `[TACZ-DBG]` 诊断日志（每处带指纹拼接、`stackInfo`/`nbtKeys` 展开，且部分判据枚举 `SlotAdapterHelper.describeAttachmentPermission` 只为打日志而多算一次），逻辑被切得看不出与上游的对应关系 | 上游这 5 个类没有任何诊断代码 | 全部删除（含 `DBG` 常量、`RefitDebug`/`SlotAdapterHelper` 导入与 javadoc 里的 5 处声明），并把被 debug 分支切碎的分支顺序重排回上游直读顺序（行为不变，逐分支核对过 §2.2 #13/#14/#16/#35/#36） | 5 文件，净 −371 行 |
| 4 | P3 | `ClientMessageSetAttachmentVariant` 的 `if (attachment == null \|\| attachmentItem == null)` 被 IDEA 判为「`attachmentItem == null` 恒为 false」 | 判断依赖 `getIAttachmentOrNull(@Nullable)` 的数据流推断 | 把 `attachmentItem == null` 前移成独立判断（= 上游 `!attachmentItem.isEmpty()` 的 1.7.10 形态），告警清零、语义不变 | 1 文件 |

**本轮逐项核查通过、未改动**（避免后续重复劳动）：
`NetworkHandler`（channel/注册顺序/45 个注册点/5 个发送 helper/`AmmoStorageMenu.setContentSender` 注入）、
45 个 `handle` 的侧校验（38 个内联 + 7 个经 `handleOnClient`，**45/45**）、
`init()` 幂等、`MessageBase` 的 final 出入口、全部消息的 `public` 无参构造器（`FMLIndexedMessageToMessageCodec.decode` 用
`Class.newInstance()`，非 public 会 `IllegalAccessException`——**45/45 满足**）、
`ServerMessageSyncedEntityDataMapping.snapshot()` 在 `write` 时读活注册表（= 上游 `encode` 语义）、
`GunPackSync.Part/Receiver/CHUNK_SIZE` 与消息侧 `readByteArray(CHUNK_SIZE)` 的尺寸契约。

---

## 4. 有据降级与未修（含理由）

| # | 项 | 上游 | 移植 | 理由 / 影响 |
|---:|---|---|---|---|
| 1 | login-packet 握手 | `HandshakeHandler` + `loginIndex`，映射表在 **login 阶段**送达 | `PlayerLoggedInEvent` 之后发普通 PLAY 包 | 1.7.10 `SimpleNetworkWrapper` 无 login packet（`LoginIndexHolder` 因此删除）。影响：映射表最多晚 1~2 tick 到达；此前 `SyncedEntityData` 用本地注册表，两侧 key 相同则无可见差异。**实机需验**（§5-6） |
| 2 | 服务端→客户端 action bar | `displayClientMessage(msg, true)` | `addChatMessage`（聊天栏） | 1.7.10 无 action bar。仅影响卸弹/弹药库提示的显示位置 |
| 3 | 旁观者判定 | `ClientMessagePlayerUnloadGun`/`AmmoStorageInteract` 判 `isSpectator()` | 无此判定 | 1.7.10 **没有旁观者模式** |
| 4 | toasts | `GunLevelUpToast` | `ServerMessageLevelUp` 客户端 no-op | 1.7.10 无 toast API；上游该分支本身也是注释掉的 TODO 且**上游无生产者** |
| 5 | 副手交换 | `mixin/common/ServerPlayNetHandlerMixin` 的 `handlePlayerAction` 分支发 `ServerMessageSwapItem` | 该分支未移植 ⇒ `ServerMessageSwapItem` **无生产者**，client hook `swapItemWithOffHand` 为 no-op | 1.7.10 无双持（`ServerGamePacketListenerImplMixin` 只保留了同 mixin 的疾跑分支，属 r7-platform scope） |
| 6 | SERVER 配置同步 | Forge 自动同步 `ModConfig.Type.SERVER` | 移植特有 43 号 `ServerMessageSyncConfig` + `SyncConfigSyncManager` | 1.7.10 无该机制；已做到「只改内存、不落盘」 |
| 7 | record | `ClientMessageAmmoStorageClick` / `ServerMessageAmmoStorageContents` 是 record | 普通类 + **同名访问器**（`containerId()` 等） | 1.7.10 需要无参构造器 + 可写字段做反序列化 |
| 8 | `Direction`/`BlockPos`/`Vec3` | 1.20 类型 | `ForgeDirection` / 3×int / `net.minecraft.util.Vec3` | 1.7.10 无此类；`sideHit`→`ForgeDirection` 序数同序，映射正确 |
| 9 | `sendToClientPlayer(Object, Player)` | 裸 `(ServerPlayer)` 强转 | `(IMessage, EntityPlayer)` + `instanceof EntityPlayerMP` 守卫 | 1.7.10 的 `EntityPlayer` 含客户端玩家；守卫避免误发/CCE |
| 10 | 同步失败处理 | 断线（`Component.literal("Failed to synchronize gun pack data")`） | 只 `LOGGER.error`（枪包同步）/ 只记 error（映射表未知 key） | 1.7.10 消息里拿不到 `NetworkManager`，客户端主动断线需 `NetHandlerPlayClient` 内部路径，且误断线代价高。**副作用**：客户端会带着空/旧枪包缓存继续游戏（枪显示异常而非被踢） |
| 11 | `Inventory#getFreeSlot()` → `getFirstEmptyStack()` | 含护甲 + 副手槽（41 格） | 只扫主背包 36 格 | 移植更严格：主背包满、护甲栏有空位时，上游允许替换已装虚拟件，移植会拒绝。**不会破坏数据**，只影响极端背包状态下能否安装 |
| 12 | 枪包同步的「连接身份」校验 | `connection == 当前连接 && isConnected` | 无 | 同上（拿不到 `NetworkManager`）；乱序/跨连接分片由 `GunPackSync.Receiver` 的 `transferId + index + size` 校验兜住（不一致即 `IllegalArgumentException` → 清分片） |
| 13 | 弹药库拾取音效 | `SoundEvents.ARMOR_EQUIP_IRON` @ `player.blockPosition()`, `SoundSource.PLAYERS` | `"random.pop"` @ 弹药库方块坐标 | 1.7.10 用字符串音效名；位置差 ≤ 6 格（同一距离校验内）、且 `World#playSoundEffect` 在 `WorldServer` 上会广播给 16 格内玩家 |

**未修且需要决策的项**：无。以上均为已记录的有据降级。

---

## 5. 跨模块发现（不在本 scope，仅记录 / 已 send_message 给 lead）

1. **`util/RefitDebug` 的使用面变化**：网络侧 5 个文件已迁出，仅剩 `client/gui/GunRefitScreen`（r7-gui）与
   `util/SlotAdapterHelper`（r7-util）。若这两个模块继续用，类保留；若都清完，`RefitDebug` 可整体删除。
   **注意**：服务端 refit 诊断日志本轮已按任务要求删除，仅剩客户端侧日志（`GunRefitScreen`）。
2. **`client/ClientProxy.java:476-480`**：`syncGunPack` 失败只 log，未像上游那样断开连接（§4-10）。属 r7-core / client scope。
3. **`client/ClientProxy.java:374-381`**：`getEntityById` 以 `thePlayer != null` 为前提，上游只要求 `level != null`。
   极早期（世界刚加载、玩家实体尚未设入 `thePlayer`）的 S2C 事件包会静默丢弃；影响仅限进入世界的头几 tick。
4. **`network/NetworkHandler#getOnlinePlayers` / `#getVersion` / `ClientSideHooks.Impl#syncBaseTimestamp`** 当前全仓无调用点
   （`getVersion` 是 1.20 协议版本常量的对照保留；`syncBaseTimestamp` 由 `ClientProxy` 实现，删除接口方法会破坏 client scope）。
   本轮不动，登记为「可清理候选」。
5. **`mixin/common/ServerGamePacketListenerImplMixin`** 只移植了上游同名 mixin 的疾跑分支（§4-5），属 r7-platform scope。

---

## 6. 变更清单

```
M src/main/java/com/tacz/guns/network/NetworkTaskQueue.java                     (按侧重写：分侧队列 + 证据可信的 javadoc)
M 37 个 message/*.java 的 NetworkTaskQueue.enqueue(...) → enqueue(ctx.side, ...)
  message/{ClientMessagePlayerShoot, ClientMessagePlayerReloadGun, ClientMessagePlayerCancelReload,
           ClientMessagePlayerFireSelect, ClientMessagePlayerAim, ClientMessagePlayerCrawl,
           ClientMessagePlayerDrawGun, ClientMessagePlayerZoom, ClientMessagePlayerBoltGun,
           ClientMessagePlayerMelee, ClientMessagePlayerShoot, ClientMessagePlayerUnloadGun,
           ClientMessageSyncBaseTimestamp, ClientMessageLaserColor, ClientMessageCraft,
           ClientMessageAmmoStorageInteract, ClientMessageAmmoStorageClick, ClientMessageRefitGun,
           ClientMessageInstallVirtualAttachment, ClientMessageSetAttachmentVariant,
           ClientMessageSetSlotAdapter, ClientMessageUnloadAttachment, ServerMessageSound,
           ServerMessageCraft, ServerMessageRefreshRefitScreen, ServerMessageGunUnload,
           ServerMessageBulletHitBlock, ServerMessageAmmoStorageContents, ServerMessageSyncGunPack,
           ServerMessageSyncConfig, ServerMessageUpdateEntityData, ServerMessageSwapItem,
           ServerMessageSyncBaseTimestamp}
  message/event/{AbstractShooterGunEventMessage, ServerMessageGunDraw, ServerMessageGunHurt, ServerMessageGunKill}
  message/handshake/ServerMessageSyncedEntityDataMapping
M 5 个 refit 消息类：删除 54 处 RefitDebug 诊断 + 重排回上游分支顺序
  message/{ClientMessageRefitGun, ClientMessageInstallVirtualAttachment,
           ClientMessageSetAttachmentVariant, ClientMessageSetSlotAdapter, ClientMessageUnloadAttachment}
```
合计 **38 文件，+109 / −480 行**（`git diff --stat`）。`lint_files(error)=0`；未运行 gradle。

---

## 7. 实机验证清单（不可静态验证项）

> 本轮修复触及线程模型，**必须实机**。判读方式：日志级别保持默认（`RefitDebug` 相关路径已移除，不会再有 `[TACZ-DBG]`）。

1. **单人（内嵌服务端，最容易复现本轮修的问题）**
   - 开枪 / 换弹 / 取消换弹 / 开镜 / 近战 / 栓动 / 趴下 / 掏枪 —— 每项都要在**连发和单发**下各试一次；
     期望：与改动前行为一致，且日志**没有** `Error while running a queued network task`。
   - 若出现「按键无反应」：说明该包的任务被排入了另一侧队列（`ctx.side` 与注册侧不符）。这是本轮唯一可能的
     新失效模式，但 45/45 注册侧与本轮入队侧同源（都来自 `ctx.side`），静态上不可能不一致。
2. **局域网双人**（A 开服，B 加入）：A 开枪、B 旁观。期望 B 看到枪口火光/曳光/命中特效/第三人称持枪动作，
   即 S2C 事件包（GunFire/GunShoot/GunDraw/GunMelee/GunReload/GunFinishReload/GunFirePost/GunFireSelect）
   在「非单人」的连接上也能到达（验证 `sendToTrackingEntity` 的 tracker 语义）。
3. **专用服务器**：进服后日志出现
   `Prepared gun pack sync: N bytes -> M compressed bytes, P part(s)`，且枪有名字/模型/图标/音效
   （验证 43 个 discriminator 未串、分片收齐、`ServerMessageSyncConfig` 未把客户端配置改坏）。
4. **改装界面全链路**：普通配件装/卸、变体切换（含非法 variantId 应被服务端拒绝）、槽位 adapter（设/删）、
   虚拟 OEM 配件安装（背包满时应被拒绝）、扩容弹匣卸下应吐出枪内子弹。每次操作后期望收到
   `ServerMessageRefreshRefitScreen` 并刷新界面。
5. **弹药库**：取弹提示与「累加计数（20 tick 内连续取弹）」、容量/已用进度条、条目列表显示（验证
   `ServerMessageAmmoStorageContents` 的 `ContentSender` 注入生效）。
6. **重连**：退出再进入服务器/世界两次。期望 `SyncedEntityData` 映射表每次登录都被重新下发（`Acknowledge`
   在服务端 debug 日志中出现），射击判定/趴下/瞄准状态正常。
7. **异常包鲁棒性**（可选，需客户端造假包或改代码）：畸形 enum ordinal、超大条目数、错序枪包分片，
   期望得到一条 `Invalid enum ordinal …` / `DecoderException` / `Inconsistent or out-of-order gun pack sync part`
   并**断开该连接**，服务端 tick 不崩。
