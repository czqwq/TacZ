# client/resource + client/animation 审查报告（task-2 / client-resource-review）

范围：`src/main/java/com/tacz/guns/client/resource/**`（86 文件）、`client/animation/**`（8 文件），共 94 文件。
基准：上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/{resource,animation}/**`；
1.7.10 原版/Forge：`build/rfg/minecraft-src/java/**`。
证据：`run/client/logs/latest.log`、`run/client/crash-reports/crash-2026-11-04_13.28.20-client.txt`、`build/r7run.log`。

结论速览：**3 个已修 bug（1 个 P0：枪包元数据永远读不到）**，
**2 个 P0 在 Lead 独占文件（`ClientAssetsManager`）**，其余为 P1/P2 报告项或已记录的平台降级。

---

## 一、已修问题（本 scope 内）

### P0-1 `PackInfoManager` 读错路径 + 用「枪包目录名」当键 → 枪包元数据永远为空

- **文件**：`src/main/java/com/tacz/guns/client/resource/manager/PackInfoManager.java:51-112`
- **证据 / 推理链**：
  1. `gunpack_info.json` 的实际位置是 `<pack>/assets/<namespace>/gunpack_info.json`
     （`src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/gunpack_info.json`；
     导出后的 `run/client/tacz/tacz_default_gun/assets/tacz/gunpack_info.json` 同形）。
  2. 上游 `tmp/TACZ-1.20.1/.../manager/PackInfoManager.java:30-31`：
     `for (String ns : manager.getNamespaces())` + `getResource(new ResourceLocation(ns, "gunpack_info.json"))`。
     1.20 的 `ResourceManager#getResource` 用 **完整 path** 直接找文件（只有列表类 API 才经
     `FileToIdConverter` 剥扩展名），因此命中 `assets/<ns>/gunpack_info.json`，且 **键是 namespace**。
  3. 移植版原来 `source.open("gunpack_info.json")` 读的是**枪包根目录**（该文件不存在 → `null` → `continue`），
     并且 `output.put(source.getName(), ...)` 用的是枪包目录名（`tacz_default_gun`）。
  4. 调用方全部按 **namespace** 查询：`CreativePackFilters.java:247`、`GunPackList.java:101`、
     `ClientGunTooltip.java:333`、`ClientAttachmentItemTooltip.java:99`、`ClientBlockItemTooltip.java:66`、
     `GunSmithTableScreen.java:585/881/983` → 永远是 `null`，
     表现是「创造模式侧栏 / 改装台过滤器只显示裸 namespace，不显示枪包名/logo」。
- **修法（已修）**：遍历 `PackSource#listFiles("assets/")`，只接受
  `assets/<namespace>/gunpack_info.json` 形态（`namespaceOfPackInfo`），以 namespace 为键；
  同 namespace 由后扫到的包覆盖先扫到的（与 `GunPackResources` 全仓覆盖约定一致）；
  `try-with-resources` 关流，保留上游「单个包解析失败只记日志」的语义。

### P1-1 `GltfManager` 扫描根错成 `data/` → `.gltf` 动画永远扫不到

- **文件**：`src/main/java/com/tacz/guns/client/resource/manager/GltfManager.java:20-32`
- **证据**：上游 `GltfManager.java:16-20` 只在客户端注册、用 `new FileToIdConverter("animations", ".gltf")`
  → 1.20 客户端资源管理器只看 `assets/`。移植版走 `LazyJsonDataManager` 的 5 参构造，
  该构造默认 `root = GunPackResources.DATA_ROOT`（`LazyJsonDataManager.java:61-64`），于是去
  `data/<ns>/animations/*.gltf` 找，必然为空。同一文件里 `bedrockModel`（`ClientAssetsManager.java:193`）
  已显式传 `ASSETS_ROOT`，两者不一致即证据。
- **修法（已修）**：改用 6 参构造并显式传 `GunPackResources.ASSETS_ROOT`。
- **备注**：仓库内当前 0 个 `*.gltf`（`src/main/resources` 与上游均无），属「当前无数据但路径必须正确」的修法；
  一旦有包用 gltf 动画，`GunDisplayInstance.checkAnimation`（`:525`）才不会误判 "animation not found"。

### P2-1 两处「无参 `StringUtils.isNoneBlank()`」（上游笔误）导致粒子装配判断失效

- **文件**：`client/resource/index/ClientAmmoIndex.java:235-250`、`client/resource/GunDisplayInstance.java:704-716`
- **证据**：`StringUtils.isNoneBlank(CharSequence...)` 是可变参数，无参调用恒为 `true`（编译期不报错）。
  上游 `ClientAmmoIndex.java:238` / `GunDisplayInstance.java:658` 写的就是无参形态，显然是
  `isNoneBlank(name)` 的笔误。
- **影响**：
  - `ClientAmmoIndex`：`name == null` 时仍 `setParticleOptions(null)` 并 `index.particle = particle`，
    于是 `AmmoParticleSpawner.java:52-65` 认为「弹药自带粒子」→ 不再回退到枪械粒子，
    而 `spawnParticle` 又因 `particleOptions == null` 直接 return → **子弹完全没有粒子**。
    上游在同一路径上会抛异常 → 不装配 → 回退（语义不同）。
  - `GunDisplayInstance`：包在 `parseParticleName` 外的判断恒真，但 `parseParticleName` 自己会拒绝
    空名（`IllegalArgumentException` 被 `catch IllegalArgumentException` 吞掉），行为与上游一致，属清理。
- **修法（已修）**：两处都补成 `StringUtils.isNoneBlank(name)`，并就地注释依据。

---

## 二、Lead 独占文件里发现的问题（仅报告，未改）

### P0-2 `ClientAssetsManager` 的 `bedrockAnimation` 缺少 `ASSETS_ROOT`

- **位置**：`src/main/java/com/tacz/guns/client/resource/ClientAssetsManager.java:197-205`
  `new LazyJsonDataManager<>(BedrockAnimationFile.class, GSON, PackDirectory.of("animations", ".animation.json"),
  "BedrockAnimationLoader", id -> ...)` —— 5 参构造 ⇒ `root = data/`。
- **为什么是 P0**：基岩动画实际在 `assets/<ns>/animations/*.animation.json`
  （`src/main/resources/assets/tacz/animations/rifle_default.animation.json` 等 340+ 个文件）。
  扫不到 ⇒ `GunDisplayInstance.checkAnimation`（`:526`）拿到 `null`，
  `:534` 抛 `IllegalArgumentException("animation not found: …")` ⇒
  `ClientIndexManager.loadGunDisplay`（`:113-116`）只打一条 warn 并**丢弃该 display**
  ⇒ `GUN_DISPLAY` 为空 ⇒ 枪械无法渲染/无动画。这一条与 EnumMap 是**互相独立**的两个失败原因，
  EnumMap 修好后仍然会踩。
- **建议**：与同文件 `bedrockModel`（`:187-194`）一致，改用 6 参构造并传 `GunPackResources.ASSETS_ROOT`。

### P1-2 其余 display/model getter 在 `reloadAndRegister` 之前仍会 NPE

- **位置**：`ClientAssetsManager.java:222-291`（`getGunDisplay` / `getAdsPreset` / `getAttachmentDisplay` /
  `getAttachmentVariantDisplay` / `getSlotAdapterDisplay` / `getAmmoDisplay` / `getBlockDisplay` /
  `getBedrockModelPOJO` / `getBedrockAnimations` / `getScript` / `getGltfAnimation`）。
- **证据**：`getGunDisplays()` / `getPackInfo(...)` 已经加了 `null` 护栏（`:231-246, :293-314`），
  但上面这批没有；任何早于 `ClientGunPackReloader` 首次重载的路径（例如 `ClientSetupEvent` 里的
  `ItemTooltipEvent`、`GunPackResourcePack` 的贴图探测）都会 NPE。
  已实测同类崩溃：`crash-2026-11-04_13.28.20-client.txt:7-10`
  （`getPackInfo` 的 `this.packInfo is null`）。
- **建议**：整批 getter 加同样的 `if (字段 == null) return null;` 护栏（语义与上游「没有数据就是 null」一致）。

### P2-2 重载监听器的注册时机（1.7.10 时序与 1.20 不同）

- 1.7.10 `Minecraft.refreshResources()`（`build/rfg/minecraft-src/java/net/minecraft/client/Minecraft.java:636`）
  只有三个触发点：启动期（`startGame` 里 `beginMinecraftLoading` 之后两次，实测日志
  `run/client/logs/latest.log:3` 与 `:7`）、`scheduleResourcesRefresh`（`:2581`）→
  `runGameLoop`（`:1028-1031`）、F3+T/F3+S 热键（`:1885/:1890`）。
  **`launchIntegratedServer`（`:2164`）不刷新资源**，这与 1.20「进入世界时客户端重载一次」完全不同。
- ⇒ (a) `IResourceManagerReloadListener` 必须在 **preInit/init** 注册（`beginMinecraftLoading` 在
  `Minecraft.java:522`，先于两次启动期 reload；日志显示第二次 reload 与 `postInit` 落在同一毫秒，
  放 `postInit` 有漏掉的窗口）。
  (b) 单人首次进世界**不会**自动重建索引，`GUN_INDEX` 依赖
  `CommonAssetsManager.onPlayerLoggedIn` → `ServerMessageSyncGunPack` →
  `ClientSideHooks.syncGunPack` → `ClientIndexManager.reload()`（`client/ClientProxy.java:401-419`）；
  当前链路可用，但「不依赖网络包」的做法需要额外的显式调用点。
  (c) 重入：`ClientGunPackReloader` 内部**不能**再调 `ClientAssetsManager.reloadAllPack()`/
  `Minecraft.refreshResources()`（后者会重新进 reload → 递归）；`reloadAllPack` 只应由
  `/tacz reload`（`ReloadCommand`）与网络换包路径触发。
- **顺序核对结论（与上游等价）**：`ClientAssetsManager.reloadAndRegister` 的注册顺序与上游
  `ClientAssetsManager.java:101-117` 逐项一致：`gunDisplay → adsPresets → ammoDisplay →
  attachmentDisplay → attachmentVariantDisplay → slotAdapterDisplay → blockDisplay → bedrockModel →
  bedrockAnimation → gltf → scriptManager → packInfo → ClientIndexManager::reload`，
  索引（`ClientIndexManager`）排在最后，`packInfo` 在索引之前，`scriptManager` 在
  `checkAnimation` 依赖它之前 ⇒ 只要 `ClientGunPackReloader` 按列表顺序**串行**驱动即可。
  唯一上游有、移植没有的是 `CompletableFuture` 的 barrier/后台线程（1.7.10 全同步），
  因此不存在「`ClientIndexManager` 先跑」的竞态，但代价是重载在主线程阻塞（可接受，同 `CommonAssetsManager`）。

---

## 三、gson 2.2.4 兼容性逐项核对（除 EnumMap 外没有别的静默失败点）

已确认 **只有** 下面两类不兼容，其余 POJO 全部可反序列化：

| 项 | 位置 | 上游写法 | 2.2.4 结果 | 状态 |
|---|---|---|---|---|
| `EnumMap` 字段 | `pojo/display/gun/GunDisplay.java:114`（`@SerializedName("controllable")`） | 1.20 用 `EnumMap` | 2.2.4 `ConstructorConstructor.get` 对具体类 `EnumMap` 既无无参构造、也不在 `newDefaultImplementationConstructor` 的 Map 分支（该分支要求 `type.isInterface()`），最终走 `UnsafeAllocator` 兜底 → `EnumMap.keyType` 为 `null` → 首次 `put` 抛 NPE → `DisplayManager.apply` 只打 error 并丢掉该 gun display | Lead 正在加 `EnumMapTypeAdapterFactory`（`resource/serialize/`），**核对通过**：工厂需在 `TypeToken.getRawType() == EnumMap.class` 时给出 `new EnumMap<>(keyEnumClass)`，否则原问题不变 |
| `@SerializedName(alternate=…)` 枚举常量 | 上游 `AdsCurve.Ease`（26 个常量） | 2.4+ 才支持 `alternate` | 只认 `value` | **已记录降级**：`AdsCurve.java:11-74` 改成 `String ease` + `resolveEase()` 双写法解析，`isValid()/evaluate()` 语义与上游逐 case 一致（已逐 case 对照 `tmp` 版 `AdsCurve.java:24-60`） |

其余已核对项（均无问题）：

- `@SerializedName` 单值（含带点的键 `"geometry.model"`、`"minecraft:geometry"`）——2.2.4 支持；
  `pojo/model/**` 6 个 POJO 的注解与上游逐字段一致。
- `@Expose` + `excludeFieldsWithoutExposeAnnotation`（`CubesItem.Deserializer`）——2.2.4 支持。
- 枚举字段（`AmmoCountStyle`/`DamageStyle`/`Align`/`DefaultAnimationType`/`MuzzleFlash.Type`/`ScopeLensConfig.VisionMode`）
  都是「枚举常量 + `@SerializedName`」，2.2.4 的 `EnumTypeAdapter` 读常量注解 ⇒ 支持。
- `Map<String, X>` / `Map<String, Object>`（`stateMachineParam`）/ `List<Float>` / `HashMap<Integer, X>`（内部构造）⇒ 支持；
  `stateMachineParam` 的 `Object` 走 `ObjectTypeAdapter` → `String/Boolean/Double/LinkedTreeMap`，与 1.20 行为相同。
- `record`：`AnimationKeyframes.Keyframe`、`EntityHit` 只由**代码构造**，不经 gson；`BedrockAnimationFile` 的
  `Map<String, BedrockAnimation>`、`BedrockAnimation` 的 `Map<String, AnimationBone>` 都经自定义
  serializer/适配器，无 `record` 反序列化。
- `JsonParser.parseReader`、`JsonArray(int)`、`JsonArray#set` 等 2.2.4 缺失 API 已在
  `GunPackResources.readElement`、`Vector3fSerializer.serialize`、`InternalAssetLoader.loadAnimations` 处替换（有注释）。
- 自定义 `JsonDeserializer` 注册表（`ClientAssetsManager.GSON`）覆盖了全部嵌套自定义类型：
  `ResourceLocation` / `CubesItem` / `Vector3f` / `CommonTransformObject` / `ItemStack` /
  `AnimationKeyframes` / `SoundEffectKeyframes` / `ParticleEffectKeyframes` / `SoundData`。
  上游还有的 `ItemTransforms/ItemTransform` 两项已记录移除（1.7.10 无该体系，`BlockItemTransforms` 替代）。

---

## 四、未修风险 / 已记录降级

1. **已记录降级**：`ClientIndexManager.reload()` 末尾的 `simplebedrockmodel FirstPersonRenderHandler.reset()`
   为 no-op（`ClientIndexManager.java:86-89`，库未 vendor）。
2. **已记录降级**：`InternalAssetLoader` 的 `MISSING_TEXTURE` 用 `minecraft:missingno` 顶替
   `MissingTextureAtlasSprite.getLocation()`（`ClientAmmoIndex:30-35`、`ClientAttachmentIndex:46-51`）。
3. **已记录降级**：`ItemStackSerializer` 用 `item+count+damage+nbt` 解析替代 `CraftingHelper.getItemStack`
   （`serialize/ItemStackSerializer.java:27-29`）；`GunDisplayInstance`/`ClientAmmoIndex` 的粒子名只做字符串校验。
4. **时序依赖（需 Lead 保证）**：`ClientIndexManager.reload()` 必须在 `CommonAssetsManager.reload()` 之后跑，
   否则 SP 首帧 `GUN_INDEX` 为空（不崩，只是空列表）。当前靠 `syncGunPack` 网络路径兜住。
5. **`ClientIndexManager.getOrCreateGunDisplay` 仍可能返回 `null`**（与上游一致，未加防护）。
6. **`PackInfoManager` 的 `sources != null` 恒真**（`ResourceManager.currentPackSources()` 永不返回 null）——
   IDE warning，保留不动以免扩大 diff。
7. **覆盖方向差异**：同 namespace 多包时「后扫到的覆盖先扫到的」，与 1.20 的「高优先级包胜出」相反
   （`GunPackResources.java:44-47` 已有同样约定，非本 scope 引入）。
8. **未实机验证**：本轮禁止跑 gradle/runClient，所有结论来自源码对照 + 既有日志；`PackInfoManager` 的修复
   需在 Lead 统一构建后用「创造模式侧栏显示枪包名/logo」验证。

---

## 五、覆盖清单（94/94 文件逐一过目）

**client/resource（86）**

- 顶层（7）：`ClientAssetLoadDispatcher` `ClientAssetLoadTask` `ClientAssetsManager`(Lead 独占，只读)
  `ClientIndexManager` `ExternalModelAssets` `GunDisplayInstance` `InternalAssetLoader`
- `manager/`（3）：`DisplayManager` `GltfManager` `PackInfoManager`
- `index/`（7）：`ClientAmmoIndex` `ClientAttachmentIndex` `ClientAttachmentSkinIndex`
  `ClientAttachmentVariantIndex` `ClientBlockIndex` `ClientGunIndex` `ClientSlotAdapterIndex`
- `serialize/`（5）：`AnimationKeyframesSerializer` `ItemStackSerializer`
  `ParticleEffectKeyframesSerializer` `SoundEffectKeyframesSerializer` `Vector3fSerializer`
- `pojo/`（64）：`CommonTransformObject` `PackInfo` `TransformScale`；
  `pojo/animation/bedrock`（6）`AnimationBone` `AnimationKeyframes` `BedrockAnimation`
  `BedrockAnimationFile` `ParticleEffectKeyframes` `SoundEffectKeyframes`；
  `pojo/animation/gltf`（13）`Accessor` `AccessorSparse` `AccessorSparseIndices` `AccessorSparseValues`
  `Animation` `AnimationChannel` `AnimationChannelTarget` `AnimationSampler` `Buffer` `BufferView`
  `Node` `RawAnimationStructure`；
  `pojo/display`（3）`IDisplay` `LaserConfig` `SoundData`；
  `pojo/display/ammo`（6）`AmmoDisplay` `AmmoEntityDisplay` `AmmoParticle` `AmmoTransform`
  `HitSurfaceParticle` `ShellDisplay`；
  `pojo/display/attachment`（3）`AttachmentDisplay` `AttachmentLod` `ScopeLensConfig`；
  `pojo/display/block`（2）`BlockDisplay` `BlockItemTransforms`（移植新增，替代 `ItemTransforms`）；
  `pojo/display/gun`（20）`AdsCurve` `AdsFov` `AdsPath` `AdsPreset` `Align` `AmmoCountStyle`
  `ControllableData` `DamageStyle` `DefaultAnimationType` `GunAmmo` `GunDisplay` `GunLod`
  `GunTransform` `LayerGunShow` `MuzzleFlash` `Rangefinder` `RangefinderManager` `ShellEjection`
  `TextShow`；
  `pojo/model`（10）`BedrockModelPOJO` `BedrockVersion` `BonesItem` `CubesItem` `Description`
  `FaceItem` `FaceUVsItem` `GeometryModelLegacy` `GeometryModelNew`；
  `pojo/skin/attachment`（1）`AttachmentSkin`

**client/animation（8）**

- `screen/RefitTransform`
- `statemachine/`（4）：`GunAnimationConstant` `GunAnimationStateContext` `ItemAnimationStateContext`
  `ThrowableAnimationStateContext`
- `third/`（3）：`AnimationRenderContext` `InnerThirdPersonManager` `PlayerModelRenderState`

核对方法：对 94 个文件逐一做「上游 ↔ 移植版」定向 diff（`-U0`/`-U3`），
凡语义变化一律回溯到上游原文与 1.7.10 源码行；动画状态机（`GunAnimationStateContext` 576 行）逐 hunk 核对
`MovementInput`/`onGround`/`isSneaking`/`distanceWalkedModified`/`IInventory` 五处平台替换，与上游语义等价。

---

## 第二轮：同类崩溃专项（task-12，「1.20 非 null 哨兵 → 1.7.10 null，读取点未判空」）

背景：用户实机 P0 `Supplier.get()` 返回 null → `ItemStack.getItem()` NPE（shooter tick）。
本轮只找**硬崩溃**，按同一模式把 `client/resource/**` + `client/animation/**` 过了一遍。

### 已修

#### R-1 `GunAnimationStateContext`：Lua 状态机 getter 直接解引用可能为 null 的 `gunData`/`iGun`/`display`

- **文件**：`src/main/java/com/tacz/guns/client/animation/statemachine/GunAnimationStateContext.java`
- **崩溃条件（tick 路径）**：`setCurrentGunItem()`（`:585-599`）只在 `iGun != null` 时刷新
  `display`/`gunData`，且两者都用 `.orElse(null)`——**1.7.10 侧「索引/展示数据缺失」是常态**
  （首次 `syncGunPack` 之前、某个枪包文件解析失败、`GUN_DISPLAY` 被 `ClientIndexManager.loadGunDisplay`
  丢弃时），而 1.20 由资源重载保证索引必非 null。
  默认状态机脚本每次 tick 都会调用这些 getter，于是：
  - `isOverHeat()`（原 `:115`）/`getHeatProgress()`（原 `:119`）：`gunData.getHeatData()` 直接 NPE；
  - `getShootInterval()`（原 `:132`）：闭包里 `iGun.getFireMode`/`gunData.getBurstData` NPE；
  - `processGunData`（原 `:68`）只守了 `iGun != null && display != null`，**漏守 `gunData`**，
    导致 `hasBulletInBarrel()`/`getMaxAmmoCount()`/`getFireMode()`/`getMaxCharge()`/`getChargeThreshold()`
    闭包内的 `gunData.*` 同样 NPE；
  - `hasAmmoToConsume()`（原 `:220`）：`iGun.useDummyAmmo(...)` NPE（空手/未就绪）；
  - `shouldSlide()`（原 `:420`）：`gunData.canSlide()` NPE；
  - `popShellFrom()`（原 `:453`）：`display.getShellEjection()` NPE（枪械 display 解析失败时）；
  - `getStateMachineParams()`（原 `:484`）：`display.getStateMachineParam()` NPE；
  - `getAttachment(String)`（原 `:504`）：`iGun.getAttachmentId(...)` NPE（`try/catch` 只捕
    `IllegalArgumentException`，NPE 会穿透）。
- **修法**：`processGunData` 增加 `gunData != null`；`isOverHeat`/`getHeatProgress`/`getShootInterval`/
  `hasAmmoToConsume`/`shouldSlide`/`getAttachment` 各加「未知即返回安全默认值」的前置判断
  （`false`/`0f`/`0L`/`0`/`空配件 ID`，与各自 `orElse` 默认值一致）；`popShellFrom`/`getStateMachineParams`
  增加 `display != null` 判断（后者回退空 `LuaTable`，脚本读到 nil 而非崩）。

#### R-2 `GunDisplayInstance.checkAnimation`：`InternalAssetLoader.getDefaultRifle/PistolAnimations()` 返回 null 时 for-each NPE

- **文件**：`src/main/java/com/tacz/guns/client/resource/GunDisplayInstance.java:548-580`
- **崩溃条件**：`InternalAssetLoader.defaultRifleAnimations/defaultPistolAnimations` 只在
  `InternalAssetLoader.onResourceReload()`（由 `TextureStitchEvent.Post` 触发，
  `client/event/ReloadResourceEvent.java:49`）里赋值。1.20 的 `TextureManager` 是**第一个**
  reload listener，所以上游一定能先填好；1.7.10 的 `SimpleReloadableResourceManager` 按**注册顺序**
  回调，枪包重载监听器注册在 `renderEngine`（`Minecraft.java:524`）之前时会先跑，
  此刻字段仍是 `null`（上游是非 null 列表）⇒ `for (ObjectAnimation animation : null)` NPE。
  该 NPE 会被 `ClientIndexManager.loadGunDisplay` 的 `catch IllegalArgumentException` **漏掉**
  （NPE 不是 IAE）→ 直接冒泡。
- **修法**：两处 for-each 前取局部变量并判空，null 时跳过默认动画（等价于「本次没有默认动画可填」）。

### 已核对无崩溃（不需改）

- `ClientIndexManager`：`GUN_DISPLAY.get(...)` 之后统一走 `getOrCreateGunDisplay` 的 null + 一次性 warn；
  `warmUpItemModel/warmUpItemForUse` 都先判 `stack == null / getItem() == null / stackSize <= 0`；
  `preloadExternalModels` 的 `CompletableFuture.allOf` 只 join 不取值；`reload()` 里
  `postChangeEvent/postChangeEvent(getHeldItem())` 受 `IGun.mainHandHoldGun(player)` 保护（手上有枪 ⇒ stack 非 null）。
- 7 个 client index 的 `getInstance(...)`：`Preconditions.checkArgument(pojo != null)` **都是第一条语句**，
  `checkDisplay` 也是「先判 null 再解引用」，缺失统一抛 `IllegalArgumentException` →
  被 `ClientIndexManager` 的 catch 吞成 warn（与上游一致）。
- `LazyJsonDataManager.getData`（resource/manager，非本 scope）：`lazyLoaderMap.get(id)` 返回 null 时
  直接 `return null`，已在复查中确认无 NPE。
- `ClientAssetLoadTask`/`ExternalModelAssets`/`ClientAssetLoadDispatcher`：`loader.get()` 的 null 结果走
  `handle` 分支返回 null；`resourceId().get()` 返回 null 时 `ClientAssetLoadDispatcher.preload` 显式判空。
- `RangefinderManager`：`gunIndex == null` / `gunData == null` / `getBulletData() == null` 全部 pre-check；
  `getAmmoSpeed` 的 `getCacheProperty()` 与 `getCache(...)` 都有 null/try-catch 保护。
- `InnerThirdPersonManager`：`ThirdPersonManager.getAnimation(name)` 是
  `CACHE.getOrDefault(name, DEFAULT)`（`api/client/other/ThirdPersonManager.java:97-99`），
  默认值 `"empty"` 不会得到 null。
- `PlayerModelRenderState`/`RefitTransform`/`AnimationRenderContext`/`ThrowableAnimationStateContext`：
  部件/实体/栈顶全部判空；`getCount()` 已按 `null → 0` 处理。
- `GunDisplayInstance` 的 `checkLod`/`checkShellModel`/`bakeExternalLodModel`：`lodModel != null` 已隐含
  `gunLod != null`；`getBedrockModelPOJO(null)` 在 `HashMap`/`LazyJsonDataManager` 上都安全返回 null。

### 报告（非本 scope，硬崩溃但未修）

1. `GunDisplayInstance.getAnimationStateMachine()`（`:873`）/`getStateMachineParam()`（`:878`）返回
   `@Nullable`，`client/event/TickAnimationEvent`、`client/event/FirstPersonRenderGunEvent`、
   `client/renderer/**` 的调用点需自行判空（上游 1.20 同样可返回 null，属调用方责任）。
2. `GunDisplayInstance.getExternalGunModel()/getExternalLodModel()` 未就绪返回 null（设计如此），
   渲染侧已有 `isPending()` 分支，若某路径直接 `getExternalGunModel().bake(...)` 会 NPE —— 建议 render 侧复查。
3. `GunDisplayInstance.getControllableData()`：JSON 写 `"controllable": null` 时字段会被 gson 置为 null
   （默认值是 `new EnumMap<>(FireMode.class)`），消费方（渲染/HUD）直接 `.get(fireMode)` 会 NPE。
   与上游同构，暂不改（若要加固，建议在 `getControllableData()` 里回退空 EnumMap）。
