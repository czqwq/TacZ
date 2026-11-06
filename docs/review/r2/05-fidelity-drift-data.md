# r2-05 全量漂移数据（上游保真审查附录）

> 本文件是 `docs/review/r2/05-fidelity.md` 的机器可核对附录，由 `build/fidelity/*.js` 只读脚本从两侧源码直接生成，
> 未手工转录。生成快照：2026-11-04 11:20:30（UTC）。
> 注意：审查期间其它 teammate 正在修改 `src/main/java/**`，因此「本移植」侧计数是**该时刻快照**；
> 重跑 `build/fidelity/` 下脚本即可复现（脚本不入库，见主报告 §六）。

| 指标 | 上游 `tmp/TACZ-1.20.1/src/main/java/com/tacz/guns` | 本移植 `src/main/java/com/tacz/guns` |
|---|---|---|
| Java 文件 | 753（符号表覆盖）/ 799（文件系统） | 786（符号表覆盖）/ 822（快照时刻文件系统） |
| 方法声明（不含构造器） | 4993 | 5915 |
| public/protected 方法 | 3847 | 4516 |
| 构造器 | 407 | 531 |
| 上游有本移植无的类 | 69 | — |
| 本移植新增类 | — | 92 |

## A. 类级差异

### A1. 上游有、本移植无（69）

分类口径：`1.20-only-compat` = 1.20 专属兼容模块，按 AGENTS.md「已确认的决策 2」只保留反射骨架；
`platform-forced` = 1.7.10 缺对应平台能力而被等价机制替换（替换物均在 javadoc 记录）。

| # | 上游类 | 分类 | 处理 / 依据 | 本移植对应物 |
|---|---|---|---|---|
| 1 | `compat/ar/ARCompatImpl.java` | 1.20-only-compat | Accelerated Rendering 兼容（1.7.10 无此渲染库）；按 AGENTS 决策 #2 只保留反射骨架 compat/ar/ARCompat | `compat/ar/ARCompat.java`（反射骨架） |
| 2 | `compat/ar/AcceleratedBeamRenderer.java` | 1.20-only-compat | Accelerated Rendering 兼容（1.7.10 无此渲染库）；按 AGENTS 决策 #2 只保留反射骨架 compat/ar/ARCompat | `compat/ar/ARCompat.java` |
| 3 | `compat/ar/BeamRenderContext.java` | 1.20-only-compat | Accelerated Rendering 兼容（1.7.10 无此渲染库）；按 AGENTS 决策 #2 只保留反射骨架 compat/ar/ARCompat | `compat/ar/ARCompat.java` |
| 4 | `compat/cloth/client/KeyClothConfig.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 5 | `compat/cloth/client/RenderClothConfig.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 6 | `compat/cloth/client/ResourceClothConfig.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 7 | `compat/cloth/client/SoundClothConfig.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 8 | `compat/cloth/client/ZoomClothConfig.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 9 | `compat/cloth/common/AmmoClothConfig.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 10 | `compat/cloth/common/GunClothConfig.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 11 | `compat/cloth/common/OtherClothConfig.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 12 | `compat/cloth/widget/CrosshairDropdown.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 13 | `compat/cloth/widget/OpenGunPackDirEntry.java` | 1.20-only-compat | Cloth Config 配置界面兼容；反射骨架 compat/cloth/MenuIntegration | `compat/cloth/MenuIntegration.java` |
| 14 | `compat/controllable/ControllableInner.java` | 1.20-only-compat | Controllable 兼容；反射骨架 compat/controllable/ControllableCompat | `compat/controllable/ControllableCompat.java` |
| 15 | `compat/jei/GunModSubtype.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 16 | `compat/jei/category/AmmoQueryCategory.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 17 | `compat/jei/category/AttachmentQueryCategory.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 18 | `compat/jei/category/GunAmmoQueryCategory.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 19 | `compat/jei/category/GunAttachmentQueryCategory.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 20 | `compat/jei/category/GunSmithTableCategory.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 21 | `compat/jei/category/package-info.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 22 | `compat/jei/entry/AmmoQueryEntry.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 23 | `compat/jei/entry/AttachmentQueryData.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 24 | `compat/jei/entry/AttachmentQueryEntry.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 25 | `compat/jei/entry/GunAmmoQueryEntry.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 26 | `compat/jei/entry/GunAttachmentQueryEntry.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 27 | `compat/jei/transfer/GunSmithTableTransferHandler.java` | 1.20-only-compat | JEI 兼容（1.7.10 JEI 是另一套 API，未移植；仅插件骨架 compat/jei/GunModPlugin） | `compat/jei/GunModPlugin.java` |
| 28 | `compat/kubejs/custom/CustomGunItemBuilder.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 29 | `compat/kubejs/custom/KubeJSCustomGunItem.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 30 | `compat/kubejs/events/GunKubeJSEvents.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 31 | `compat/kubejs/events/TimelessClientEvents.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 32 | `compat/kubejs/events/TimelessCommonEvents.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 33 | `compat/kubejs/events/TimelessForgeEventWrappers.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 34 | `compat/kubejs/events/TimelessKubeJSEventRegister.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 35 | `compat/kubejs/events/TimelessServerEvents.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 36 | `compat/kubejs/recipe/GunSmithTableResultComponents.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 37 | `compat/kubejs/recipe/TimelessGunSmithTableRecipeSchema.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 38 | `compat/kubejs/recipe/TimelessRecipeJS.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 39 | `compat/kubejs/util/AmmoNbtFactory.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 40 | `compat/kubejs/util/AttachmentNbtFactory.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 41 | `compat/kubejs/util/GunNbtFactory.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 42 | `compat/kubejs/util/GunSmithTableResultInfo.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 43 | `compat/kubejs/util/TimelessItemNbtFactory.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 44 | `compat/kubejs/util/TimelessItemType.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 45 | `compat/kubejs/util/TimelessItemWrapper.java` | 1.20-only-compat | KubeJS 兼容（1.7.10 无 KubeJS 对应版本；仅插件骨架 compat/kubejs/TimelessKubeJSPlugin） | `compat/kubejs/TimelessKubeJSPlugin.java` |
| 46 | `compat/oculus/legacy/OculusCompatLegacy.java` | 1.20-only-compat | Oculus/Iris 光影兼容（1.7.10 无 Oculus）；反射骨架 compat/oculus/OculusCompat | `compat/oculus/OculusCompat.java` |
| 47 | `compat/oculus/newly/OculusCompatNewly.java` | 1.20-only-compat | Oculus/Iris 光影兼容（1.7.10 无 Oculus）；反射骨架 compat/oculus/OculusCompat | `compat/oculus/OculusCompat.java` |
| 48 | `compat/playeranimator/animation/AdjustmentYRotModifier.java` | 1.20-only-compat | PlayerAnimator 兼容；反射骨架 compat/playeranimator/PlayerAnimatorCompat + AnimationName | `compat/playeranimator/PlayerAnimatorCompat.java` |
| 49 | `compat/playeranimator/animation/AnimationDataRegisterFactory.java` | 1.20-only-compat | PlayerAnimator 兼容；反射骨架 compat/playeranimator/PlayerAnimatorCompat + AnimationName | `compat/playeranimator/PlayerAnimatorCompat.java` |
| 50 | `compat/playeranimator/animation/AnimationManager.java` | 1.20-only-compat | PlayerAnimator 兼容；反射骨架 compat/playeranimator/PlayerAnimatorCompat + AnimationName | `compat/playeranimator/PlayerAnimatorCompat.java` |
| 51 | `compat/playeranimator/animation/PlayerAnimationLayer.java` | 1.20-only-compat | PlayerAnimator 兼容；反射骨架 compat/playeranimator/PlayerAnimatorCompat + AnimationName | `compat/playeranimator/PlayerAnimatorCompat.java` |
| 52 | `compat/playeranimator/animation/PlayerAnimatorAssetManager.java` | 1.20-only-compat | PlayerAnimator 兼容；反射骨架 compat/playeranimator/PlayerAnimatorCompat + AnimationName | `compat/playeranimator/PlayerAnimatorCompat.java` |
| 53 | `compat/playeranimator/animation/PlayerAnimatorLoader.java` | 1.20-only-compat | PlayerAnimator 兼容；反射骨架 compat/playeranimator/PlayerAnimatorCompat + AnimationName | `compat/playeranimator/PlayerAnimatorCompat.java` |
| 54 | `compat/playeranimator/animation/PlayerAnimatorModelState.java` | 1.20-only-compat | PlayerAnimator 兼容；反射骨架 compat/playeranimator/PlayerAnimatorCompat + AnimationName | `compat/playeranimator/PlayerAnimatorCompat.java` |
| 55 | `compat/playeranimator/animation/RotationAnimation.java` | 1.20-only-compat | PlayerAnimator 兼容；反射骨架 compat/playeranimator/PlayerAnimatorCompat + AnimationName | `compat/playeranimator/PlayerAnimatorCompat.java` |
| 56 | `compat/shouldersurfing/ShoulderSurfingCompatInner.java` | 1.20-only-compat | Shoulder Surfing 兼容；反射骨架 compat/shouldersurfing/ShoulderSurfingCompat | `compat/shouldersurfing/ShoulderSurfingCompat.java` |
| 57 | `compat/shouldersurfing/ShoulderSurfingPlugin.java` | 1.20-only-compat | Shoulder Surfing 兼容；反射骨架 compat/shouldersurfing/ShoulderSurfingCompat | `compat/shouldersurfing/ShoulderSurfingCompat.java` |
| 58 | `config/PreLoadModConfig.java` | platform-forced | Forge 1.20 ModConfig/nightconfig 内部实现；1.7.10 由 config/PreLoadConfig 用 Forge Configuration 重写（javadoc 记录） | `config/PreLoadConfig.java`（javadoc:6-20 记录） |
| 59 | `entity/sync/core/DataHolderCapabilityProvider.java` | platform-forced | 1.20 capability provider；1.7.10 改为 entity/sync/core/SyncedEntityData 内 WeakHashMap 静态管理器（javadoc 记录） | `entity/sync/core/SyncedEntityData.java`（javadoc:29-49 记录 WeakHashMap 静态管理器） |
| 60 | `mixin/client/RenderSystemAccessor.java` | platform-forced | 1.20 RenderSystem.shaderLightDirections accessor；1.7.10 无 RenderSystem/核心 shader | 无（1.7.10 无 RenderSystem/核心 shader） |
| 61 | `mixin/client/StairBlockAccessor.java` | platform-forced | Forge 1.20 StairBlock.getModelBlock 访问器；1.7.10 无同问题 | 无（Forge 1.20 专用 accessor） |
| 62 | `mixin/client/ar/ARCompatMixinPlugin.java` | 1.20-only-compat | AR 兼容 mixin（同 Accelerated Rendering） | 无（对应 compat 未移植） |
| 63 | `mixin/client/ar/BedrockPartMixin.java` | 1.20-only-compat | AR 兼容 mixin（同 Accelerated Rendering） | 无（对应 compat 未移植） |
| 64 | `mixin/client/oculus/OculusMixinPlugin.java` | 1.20-only-compat | Oculus 兼容 mixin | 无（对应 compat 未移植） |
| 65 | `mixin/client/oculus/UnflushableWrapperAccessor.java` | 1.20-only-compat | Oculus 兼容 mixin | 无（对应 compat 未移植） |
| 66 | `mixin/common/ServerPlayNetHandlerMixin.java` | platform-forced | 副手交换触发 ServerMessageSwapItem；1.7.10 无双持（上游同行为由 mine 的 ClientProxy:468 注明 no-op） | 无（副手交换在 1.7.10 不存在；`client/ClientProxy.java:468` 注明 no-op） |
| 67 | `network/IMessage.java` | platform-forced | 1.20 SimpleChannel 三件套接口；1.7.10 由 network/MessageBase + SimpleMessageDispatcher 承接（javadoc 记录） | `network/MessageBase.java`（javadoc:8-32） |
| 68 | `network/LoginIndexHolder.java` | platform-forced | 1.20 login handshake 基类；1.7.10 由 network/SyncConfigSyncManager + handshake 消息承接（javadoc 记录） | `network/SyncConfigSyncManager.java`（javadoc:10-45） |
| 69 | `resource/NamespaceFilteredPackResources.java` | platform-forced | 1.20 ResourcePack 包装（PackResources 接口）；1.7.10 由 resource/pack/{IGunPackSource,GunPackResources,DirectoryGunPackSource,ZipGunPackSource} 重建（javadoc 记录） | `resource/pack/GunPackResources.java` + `IGunPackSource`（javadoc） |

### A2. 本移植新增类（92）

全部新增类均带类级 javadoc（92/92）；其中 89/92 的 javadoc 首段明示「上游对应物 / 1.20 形态 / 1.7.10 限制」；
3 个例外：block/PortWriteProbe（临时探针，应删除）、CreativePackFilterContainerAccess、GunPackResourcePackReloadListener（后两者 javadoc 说明的是 1.7.10 形态约束而非上游文件）。分类：platform-shim = 1.20 API 语义 shim；platform-forced = 1.7.10 注册/网络/资源/配置架构适配类。

| # | 新增类 | 行数 | 分类 | 上游对应物 / 存在理由 | 类 javadoc 提及上游 |
|---|---|---|---|---|---|
| 1 | `CommonProxy.java` | 171 | platform-forced | 1.7.10 代理结构（上游无此文件，代理由 FML 生命周期承载） | 是 |
| 2 | `api/item/nbt/ItemStackNbtHelper.java` | 103 | platform-shim | ItemStack 空栈/NBT 空安全工具（上游 1.20 ItemStack.EMPTY 语义替代） | 是 |
| 3 | `api/util/ResourceLocationHelper.java` | 45 | platform-shim | ResourceLocation.tryParse 替代（1.7.10 无 tryParse） | 是 |
| 4 | `block/BlockMetadata.java` | 189 | platform-forced | 1.7.10 事件/注册/工具适配类 | 是 |
| 5 | `block/PortWriteProbe.java` | 8 | platform-forced | 1.7.10 事件/注册/工具适配类 | 仅说明 1.7.10 形态 |
| 6 | `client/ClientProxy.java` | 472 | platform-forced | 1.7.10 代理结构（上游无此文件，代理由 FML 生命周期承载） | 是 |
| 7 | `client/ClientTickHelper.java` | 61 | platform-forced | 客户端 1.7.10 适配类 | 是 |
| 8 | `client/gui/components/creative/CreativePackFilterContainerAccess.java` | 53 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 仅说明 1.7.10 形态 |
| 9 | `client/gui/components/creative/CreativePackFilterHooks.java` | 106 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 是 |
| 10 | `client/model/RenderHelperCompat.java` | 319 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 是 |
| 11 | `client/particle/ParticleCompat.java` | 115 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 是 |
| 12 | `client/particle/ParticleDefinitionReloader.java` | 88 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 是 |
| 13 | `client/renderer/block/StaticItemRenderHelper.java` | 196 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 是 |
| 14 | `client/renderer/other/CameraAngles.java` | 68 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 是 |
| 15 | `client/renderer/scope/ScopeLensProgram.java` | 421 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 是 |
| 16 | `client/resource/ClientGunPackReloader.java` | 142 | platform-forced | 客户端 1.7.10 适配类 | 是 |
| 17 | `client/resource/pojo/display/block/BlockItemTransforms.java` | 135 | platform-forced | 客户端 1.7.10 适配类 | 是 |
| 18 | `client/sound/GunPackResourcePack.java` | 758 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 是 |
| 19 | `client/sound/GunPackResourcePackReloadListener.java` | 59 | platform-forced | 客户端渲染/资源/GUI 的 1.7.10 适配类 | 仅说明 1.7.10 形态 |
| 20 | `command/sub/CommandTargets.java` | 85 | platform-forced | 1.7.10 事件/注册/工具适配类 | 是 |
| 21 | `config/BooleanConfigValue.java` | 42 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 22 | `config/ConfigBuilder.java` | 149 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 23 | `config/ConfigManager.java` | 97 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 24 | `config/ConfigValue.java` | 117 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 25 | `config/DoubleConfigValue.java` | 48 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 26 | `config/EnumConfigValue.java` | 61 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 27 | `config/IntConfigValue.java` | 49 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 28 | `config/ListConfigValue.java` | 64 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 29 | `config/StringConfigValue.java` | 44 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 30 | `config/TaczConfigSpec.java` | 86 | platform-forced | ForgeConfigSpec/nightconfig → 1.7.10 Forge Configuration（config/ConfigBuilder + TaczConfigSpec 复刻上游 Builder 形态） | 是 |
| 31 | `entity/EntityBulletClientHooks.java` | 45 | platform-forced | 1.7.10 事件/注册/工具适配类 | 是 |
| 32 | `event/EventHandlers.java` | 68 | platform-forced | 1.7.10 事件/注册/工具适配类 | 是 |
| 33 | `inventory/GuiHandler.java` | 140 | platform-forced | 1.7.10 GuiHandler 注册（上游 MenuType 无对应） | 是 |
| 34 | `inventory/GuiId.java` | 42 | platform-forced | 1.7.10 GuiHandler 注册（上游 MenuType 无对应） | 是 |
| 35 | `item/ItemClientHooks.java` | 87 | platform-forced | 1.7.10 事件/注册/工具适配类 | 是 |
| 36 | `mcshim/AnimationPose.java` | 76 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 37 | `mcshim/Axis.java` | 83 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 38 | `mcshim/BlockEntityRenderers.java` | 53 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 39 | `mcshim/BufferBuilder.java` | 350 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 40 | `mcshim/BufferBuilderVertexConsumer.java` | 92 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 41 | `mcshim/BufferUploader.java` | 95 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 42 | `mcshim/DefaultVertexFormat.java` | 124 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 43 | `mcshim/DummyPose.java` | 60 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 44 | `mcshim/EntityPose.java` | 66 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 45 | `mcshim/EntityRenderers.java` | 73 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 46 | `mcshim/EntityStateHelper.java` | 121 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 47 | `mcshim/FileToIdConverter.java` | 147 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 48 | `mcshim/GlStateManager.java` | 218 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 49 | `mcshim/GsonHelper.java` | 450 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 50 | `mcshim/GuiGraphics.java` | 805 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 51 | `mcshim/HumanoidArm.java` | 39 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 52 | `mcshim/InputConstants.java` | 160 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 53 | `mcshim/InteractionHand.java` | 33 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 54 | `mcshim/ItemDisplayContext.java` | 83 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 55 | `mcshim/LightTexture.java` | 99 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 56 | `mcshim/Lighting.java` | 50 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 57 | `mcshim/Mth.java` | 345 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 58 | `mcshim/MultiBufferSource.java` | 143 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 59 | `mcshim/OverlayTexture.java` | 26 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 60 | `mcshim/PackSource.java` | 71 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 61 | `mcshim/PacketBuffer.java` | 462 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 62 | `mcshim/PoseStack.java` | 282 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 63 | `mcshim/PreparableReloadListener.java` | 80 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 64 | `mcshim/ProfilerFiller.java` | 89 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 65 | `mcshim/RenderSystem.java` | 408 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 66 | `mcshim/RenderType.java` | 875 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 67 | `mcshim/Resource.java` | 118 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 68 | `mcshim/ResourceManager.java` | 227 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 69 | `mcshim/SimplePreparableReloadListener.java` | 71 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 70 | `mcshim/SoundEvent.java` | 72 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 71 | `mcshim/Tesselator.java` | 56 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 72 | `mcshim/TessellatorVertexConsumer.java` | 279 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 73 | `mcshim/VertexConsumer.java` | 116 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 74 | `mcshim/VertexFormat.java` | 182 | platform-shim | 1.20 API → 1.7.10 的语义 shim 包（PoseStack/BufferBuilder/RenderType/PacketBuffer...） | 是 |
| 75 | `mixin/client/CreativeContainerAccessor.java` | 71 | platform-forced | 1.7.10 事件/注册/工具适配类 | 是 |
| 76 | `network/ClientSideHooks.java` | 85 | platform-forced | SimpleChannel/NetworkEvent → 1.7.10 SimpleNetworkWrapper 适配层 | 是 |
| 77 | `network/MessageBase.java` | 52 | platform-forced | SimpleChannel/NetworkEvent → 1.7.10 SimpleNetworkWrapper 适配层 | 是 |
| 78 | `network/NetworkTaskQueue.java` | 97 | platform-forced | SimpleChannel/NetworkEvent → 1.7.10 SimpleNetworkWrapper 适配层 | 是 |
| 79 | `network/SimpleMessageDispatcher.java` | 52 | platform-forced | SimpleChannel/NetworkEvent → 1.7.10 SimpleNetworkWrapper 适配层 | 是 |
| 80 | `network/SyncConfigSyncManager.java` | 61 | platform-forced | SimpleChannel/NetworkEvent → 1.7.10 SimpleNetworkWrapper 适配层 | 是 |
| 81 | `network/message/ServerMessageSyncConfig.java` | 199 | platform-forced | SimpleChannel/NetworkEvent → 1.7.10 SimpleNetworkWrapper 适配层 | 是 |
| 82 | `network/message/event/AbstractShooterGunEventMessage.java` | 87 | platform-forced | SimpleChannel/NetworkEvent → 1.7.10 SimpleNetworkWrapper 适配层 | 是 |
| 83 | `resource/GunPackType.java` | 34 | platform-forced | 1.20 ResourcePack/PackType/PreparableReloadListener → 1.7.10 自建枪包源与重载监听 | 是 |
| 84 | `resource/IGunPackReloadListener.java` | 22 | platform-forced | 1.20 ResourcePack/PackType/PreparableReloadListener → 1.7.10 自建枪包源与重载监听 | 是 |
| 85 | `resource/pack/DirectoryGunPackSource.java` | 100 | platform-forced | 1.20 ResourcePack/PackType/PreparableReloadListener → 1.7.10 自建枪包源与重载监听 | 是 |
| 86 | `resource/pack/GunPackResources.java` | 241 | platform-forced | 1.20 ResourcePack/PackType/PreparableReloadListener → 1.7.10 自建枪包源与重载监听 | 是 |
| 87 | `resource/pack/IGunPackSource.java` | 43 | platform-forced | 1.20 ResourcePack/PackType/PreparableReloadListener → 1.7.10 自建枪包源与重载监听 | 是 |
| 88 | `resource/pack/PackDirectory.java` | 57 | platform-forced | 1.20 ResourcePack/PackType/PreparableReloadListener → 1.7.10 自建枪包源与重载监听 | 是 |
| 89 | `resource/pack/ZipGunPackSource.java` | 143 | platform-forced | 1.20 ResourcePack/PackType/PreparableReloadListener → 1.7.10 自建枪包源与重载监听 | 是 |
| 90 | `resource/serialize/EnumMapTypeAdapterFactory.java` | 115 | platform-forced | Gson 2.10 → 1.7.10 自带 gson 2.2.4 的类型适配器 | 是 |
| 91 | `resource/serialize/ItemStackJsonParser.java` | 80 | platform-forced | Gson 2.10 → 1.7.10 自带 gson 2.2.4 的类型适配器 | 是 |
| 92 | `resource/serialize/ResourceLocationSerializer.java` | 47 | platform-forced | Gson 2.10 → 1.7.10 自带 gson 2.2.4 的类型适配器 | 是 |

## B. 方法级差异

### B1. 口径与计数

同名文件内按 `方法名 + 形参个数` 做集合比对（public/protected/package-private 均参与，构造器单列）。结果：

| 差异类型 | 条数 | 说明 |
|---|---|---|
| UPSTREAM_ONLY | 502 | 上游有、本移植同文件内无「同名+同元数」方法 |
| LOCAL_ONLY（同名文件内新增） | 709 | 本移植新增（含 1.7.10 平台覆写、适配辅助、getter） |
| RET_DIFF | 1009 | 同名同元数但返回类型不同（绝大多数是 1.20→1.7.10 类型替换；
|  |  | 含匿名内部类重载造成的笛卡尔噪声，见 B4 说明） |
| STATIC_DIFF | 83 | static/instance 变化（平台钩子形态，如 1.20 `@Mod.EventBusSubscriber` 静态处理方法 → 1.7.10 实例方法） |
| VIS_DIFF | 12 | public/protected 可见性变化（逐条见 B4） |

**关键结论：`rename-candidates.tsv` 中 264 对「上游方法缺失 + 本移植新增」候选，无一是 TACZ 自有业务方法被改成自创名**：
逐对核对后 100% 可归入 ①1.20 平台 API → 1.7.10 API 改名，或 ②参数类型/元数变化（BlockState→metadata 等）。
清单见 B5（该表即「上游方法名 → 本移植方法名 → 是否注明」的完整映射表；`sameName=0` 表示名字都变了）。

### B2. UPSTREAM_ONLY 全量（502）

列：上游 `file:line` 方法/元数 → 返回类型；分类：`NET` 网络机制改名、`SIG` 名字保留但平台类型导致元数变化、
`MECH` 机制被新类承接、`PLATFORM` 1.20 平台覆写/事件钩子（1.7.10 无此钩子）、`PLATFORM?` 低频名人工核对后的平台项。

| # | 上游位置 | 方法/元数 → 返回 | 分类 | 说明 |
|---|---|---|---|---|
| 1 | `advancement/GunRefitTrigger.java:19` | `getId/0` → `ResourceLocation` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 2 | `advancement/GunRefitTrigger.java:30` | `createInstance/3` → `TriggerInstance` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 3 | `advancement/TargetHitTrigger.java:16` | `getId/0` → `ResourceLocation` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 4 | `advancement/TargetHitTrigger.java:25` | `createInstance/3` → `TriggerInstance` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 5 | `advancement/TargetHitTrigger.java:39` | `matches/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 6 | `advancement/TargetHitTrigger.java:43` | `serializeToJson/1` → `JsonObject` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 7 | `api/event/server/AmmoHitBlockEvent.java:34` | `getHitResult/0` → `BlockHitResult` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 8 | `api/item/gun/AbstractGunItem.java:332` | `getName/1` → `Component` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 9 | `api/item/gun/AbstractGunItem.java:380` | `initializeClient/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 10 | `api/item/gun/AbstractGunItem.java:385` | `getCustomRenderer/0` → `BlockEntityWithoutLevelRenderer` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 11 | `api/item/gun/AbstractGunItem.java:395` | `getTooltipImage/1` → `Optional<TooltipComponent>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 12 | `api/item/gun/GunItemManager.java:17` | `getGunItemRegistryObject/1` → `RegistryObject<?extendsAbstractGunItem>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 13 | `block/AbstractGunSmithTableBlock.java:39` | `use/6` → `InteractionResult` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 14 | `block/AbstractGunSmithTableBlock.java:55` | `createBlockStateDefinition/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 15 | `block/AbstractGunSmithTableBlock.java:60` | `newBlockEntity/2` → `BlockEntity` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 16 | `block/AbstractGunSmithTableBlock.java:66` | `getRenderShape/1` → `RenderShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 17 | `block/AbstractGunSmithTableBlock.java:71` | `getPistonPushReaction/1` → `PushReaction` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 18 | `block/AbstractGunSmithTableBlock.java:76` | `setPlacedBy/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 19 | `block/AbstractGunSmithTableBlock.java:91` | `getCloneItemStack/5` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 20 | `block/AbstractGunSmithTableBlock.java:110` | `getRootPos/2` → `BlockPos` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 21 | `block/AmmoStorageBlock.java:64` | `createBlockStateDefinition/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 22 | `block/AmmoStorageBlock.java:69` | `newBlockEntity/2` → `BlockEntity` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 23 | `block/AmmoStorageBlock.java:75` | `getStateForPlacement/1` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 24 | `block/AmmoStorageBlock.java:81` | `use/6` → `InteractionResult` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 25 | `block/AmmoStorageBlock.java:130` | `getDrops/2` → `List<ItemStack>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 26 | `block/AmmoStorageBlock.java:143` | `hasAnalogOutputSignal/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 27 | `block/AmmoStorageBlock.java:148` | `getAnalogOutputSignal/3` → `int` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 28 | `block/AmmoStorageBlock.java:157` | `getRenderShape/1` → `RenderShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 29 | `block/AmmoStorageBlock.java:162` | `getPistonPushReaction/1` → `PushReaction` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 30 | `block/GunDisplayCaseBlock.java:96` | `createBlockStateDefinition/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 31 | `block/GunDisplayCaseBlock.java:101` | `newBlockEntity/2` → `BlockEntity` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 32 | `block/GunDisplayCaseBlock.java:107` | `getStateForPlacement/1` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 33 | `block/GunDisplayCaseBlock.java:125` | `updateShape/6` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 34 | `block/GunDisplayCaseBlock.java:134` | `onPlace/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 35 | `block/GunDisplayCaseBlock.java:142` | `use/6` → `InteractionResult` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 36 | `block/GunDisplayCaseBlock.java:188` | `getShape/4` → `VoxelShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 37 | `block/GunDisplayCaseBlock.java:208` | `getCollisionShape/4` → `VoxelShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 38 | `block/GunDisplayCaseBlock.java:222` | `onRemove/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 39 | `block/GunDisplayCaseBlock.java:233` | `getRenderShape/1` → `RenderShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 40 | `block/GunDisplayCaseBlock.java:238` | `getPistonPushReaction/1` → `PushReaction` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 41 | `block/GunDisplayCaseBlock.java:344` | `toggleLockFromInteraction/3` → `void` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 42 | `block/GunDisplayCaseSlotHelper.java:16` | `getSlot/3` → `int` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 43 | `block/GunDisplayCaseSlotHelper.java:35` | `getSlotAabb/3` → `AABB` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 44 | `block/GunPegboardBlock.java:68` | `createBlockStateDefinition/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 45 | `block/GunPegboardBlock.java:73` | `newBlockEntity/2` → `BlockEntity` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 46 | `block/GunPegboardBlock.java:79` | `getStateForPlacement/1` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 47 | `block/GunPegboardBlock.java:128` | `updateShape/6` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 48 | `block/GunPegboardBlock.java:133` | `rotate/2` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 49 | `block/GunPegboardBlock.java:138` | `mirror/2` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 50 | `block/GunPegboardBlock.java:149` | `use/6` → `InteractionResult` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 51 | `block/GunPegboardBlock.java:190` | `getShape/4` → `VoxelShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 52 | `block/GunPegboardBlock.java:207` | `getCollisionShape/4` → `VoxelShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 53 | `block/GunPegboardBlock.java:212` | `onRemove/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 54 | `block/GunPegboardBlock.java:223` | `getRenderShape/1` → `RenderShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 55 | `block/GunPegboardBlock.java:228` | `getDrops/2` → `List<ItemStack>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 56 | `block/GunPegboardBlock.java:241` | `getCloneItemStack/5` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 57 | `block/GunPegboardBlock.java:250` | `getPistonPushReaction/1` → `PushReaction` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 58 | `block/GunPegboardSlotHelper.java:37` | `getSlot/3` → `int` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 59 | `block/GunPegboardSlotHelper.java:75` | `getSlotAabb/3` → `AABB` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 60 | `block/GunSmithTableBlockA.java:11` | `getStateForPlacement/1` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 61 | `block/GunSmithTableBlockA.java:21` | `getRootPos/2` → `BlockPos` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 62 | `block/GunSmithTableBlockB.java:34` | `createBlockStateDefinition/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 63 | `block/GunSmithTableBlockB.java:39` | `getStateForPlacement/1` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 64 | `block/GunSmithTableBlockB.java:52` | `setPlacedBy/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 65 | `block/GunSmithTableBlockB.java:63` | `playerWillDestroy/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 66 | `block/GunSmithTableBlockB.java:80` | `updateShape/6` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 67 | `block/GunSmithTableBlockB.java:99` | `getRootPos/2` → `BlockPos` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 68 | `block/GunSmithTableBlockC.java:30` | `createBlockStateDefinition/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 69 | `block/GunSmithTableBlockC.java:36` | `getStateForPlacement/1` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 70 | `block/GunSmithTableBlockC.java:49` | `setPlacedBy/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 71 | `block/GunSmithTableBlockC.java:60` | `updateShape/6` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 72 | `block/GunSmithTableBlockC.java:76` | `playerWillDestroy/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 73 | `block/GunSmithTableBlockC.java:98` | `getRootPos/2` → `BlockPos` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 74 | `block/StatueBlock.java:41` | `getTicker/3` → `<TextendsBlockEntity>BlockEntityTicker<T>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 75 | `block/StatueBlock.java:47` | `createBlockStateDefinition/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 76 | `block/StatueBlock.java:52` | `newBlockEntity/2` → `BlockEntity` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 77 | `block/StatueBlock.java:58` | `use/6` → `InteractionResult` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 78 | `block/StatueBlock.java:85` | `getStateForPlacement/1` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 79 | `block/StatueBlock.java:97` | `setPlacedBy/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 80 | `block/StatueBlock.java:108` | `updateShape/6` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 81 | `block/StatueBlock.java:124` | `onRemove/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 82 | `block/StatueBlock.java:135` | `getRenderShape/1` → `RenderShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 83 | `block/StatueBlock.java:140` | `getPistonPushReaction/1` → `PushReaction` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 84 | `block/StatueBlock.java:145` | `onBlockExploded/4` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 85 | `block/TargetBlock.java:54` | `getRedstoneStrength/2` → `int` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 86 | `block/TargetBlock.java:79` | `getTicker/3` → `<TextendsBlockEntity>BlockEntityTicker<T>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 87 | `block/TargetBlock.java:85` | `createBlockStateDefinition/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 88 | `block/TargetBlock.java:90` | `newBlockEntity/2` → `BlockEntity` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 89 | `block/TargetBlock.java:100` | `getShape/4` → `VoxelShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 90 | `block/TargetBlock.java:110` | `tick/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 91 | `block/TargetBlock.java:118` | `onProjectileHit/4` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 92 | `block/TargetBlock.java:141` | `updateShape/6` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 93 | `block/TargetBlock.java:167` | `getStateForPlacement/1` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 94 | `block/TargetBlock.java:179` | `setPlacedBy/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 95 | `block/TargetBlock.java:199` | `getCloneItemStack/5` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 96 | `block/TargetBlock.java:209` | `canSurvive/3` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 97 | `block/TargetBlock.java:219` | `isSignalSource/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 98 | `block/TargetBlock.java:224` | `getSignal/4` → `int` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 99 | `block/TargetBlock.java:229` | `onPlace/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 100 | `block/TargetBlock.java:238` | `getRenderShape/1` → `RenderShape` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 101 | `block/TargetBlock.java:243` | `getPistonPushReaction/1` → `PushReaction` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 102 | `block/entity/AmmoStorageBlockEntity.java:90` | `load/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 103 | `block/entity/AmmoStorageBlockEntity.java:96` | `saveAdditional/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 104 | `block/entity/AmmoStorageBlockEntity.java:107` | `createMenu/3` → `AbstractContainerMenu` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 105 | `block/entity/AmmoStorageBlockEntity.java:112` | `invalidateCaps/0` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 106 | `block/entity/AmmoStorageBlockEntity.java:118` | `reviveCaps/0` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 107 | `block/entity/AmmoStorageBlockEntity.java:124` | `getCapability/2` → `<T>LazyOptional<T>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 108 | `block/entity/GunDisplayCaseBlockEntity.java:155` | `load/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 109 | `block/entity/GunDisplayCaseBlockEntity.java:186` | `saveAdditional/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 110 | `block/entity/GunDisplayCaseBlockEntity.java:202` | `getUpdateTag/0` → `CompoundTag` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 111 | `block/entity/GunDisplayCaseBlockEntity.java:209` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 112 | `block/entity/GunPegboardBlockEntity.java:208` | `setBlockState/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 113 | `block/entity/GunPegboardBlockEntity.java:232` | `load/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 114 | `block/entity/GunPegboardBlockEntity.java:281` | `saveAdditional/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 115 | `block/entity/GunPegboardBlockEntity.java:300` | `getUpdateTag/0` → `CompoundTag` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 116 | `block/entity/GunPegboardBlockEntity.java:309` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 117 | `block/entity/GunSmithTableBlockEntity.java:50` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 118 | `block/entity/GunSmithTableBlockEntity.java:67` | `createMenu/3` → `AbstractContainerMenu` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 119 | `block/entity/GunSmithTableBlockEntity.java:73` | `load/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 120 | `block/entity/GunSmithTableBlockEntity.java:83` | `saveAdditional/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 121 | `block/entity/GunSmithTableBlockEntity.java:91` | `getUpdateTag/0` → `CompoundTag` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 122 | `block/entity/StatueBlockEntity.java:29` | `clientTick/4` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 123 | `block/entity/StatueBlockEntity.java:71` | `load/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 124 | `block/entity/StatueBlockEntity.java:79` | `saveAdditional/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 125 | `block/entity/StatueBlockEntity.java:85` | `getUpdateTag/0` → `CompoundTag` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 126 | `block/entity/StatueBlockEntity.java:97` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 127 | `block/entity/TargetBlockEntity.java:47` | `clientTick/4` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 128 | `block/entity/TargetBlockEntity.java:69` | `load/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 129 | `block/entity/TargetBlockEntity.java:80` | `saveAdditional/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 130 | `block/entity/TargetBlockEntity.java:91` | `getName/0` → `Component` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 131 | `block/entity/TargetBlockEntity.java:106` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 132 | `block/entity/TargetBlockEntity.java:111` | `getUpdateTag/0` → `CompoundTag` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 133 | `block/entity/TargetBlockEntity.java:129` | `hit/4` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 134 | `client/event/CameraSetupEvent.java:120` | `applyGunModelFovModifying/1` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 135 | `client/event/RefreshClonePlayerDataEvent.java:23` | `onClientPlayerClone/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 136 | `client/gui/AmmoStorageScreen.java:28` | `renderBg/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 137 | `client/gui/AmmoStorageScreen.java:55` | `render/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 138 | `client/gui/AmmoStorageScreen.java:62` | `renderTooltip/3` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 139 | `client/gui/AmmoStorageScreen.java:98` | `mouseDragged/5` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 140 | `client/gui/AmmoStorageScreen.java:103` | `mouseReleased/3` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 141 | `client/gui/GunPackProgressScreen.java:22` | `init/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 142 | `client/gui/GunPackProgressScreen.java:30` | `render/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 143 | `client/gui/GunRefitScreen.java:96` | `init/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 144 | `client/gui/GunRefitScreen.java:122` | `render/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 145 | `client/gui/GunRefitScreen.java:148` | `isPauseScreen/0` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 146 | `client/gui/GunRefitScreen.java:422` | `onClose/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 147 | `client/gui/GunSmithTableScreen.java:360` | `init/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 148 | `client/gui/GunSmithTableScreen.java:597` | `render/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 149 | `client/gui/GunSmithTableScreen.java:842` | `renderLabels/3` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 150 | `client/gui/GunSmithTableScreen.java:846` | `renderBg/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 151 | `client/gui/GunSmithTableScreen.java:858` | `isPauseScreen/0` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 152 | `client/gui/compat/ClothConfigScreen.java:34` | `init/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 153 | `client/gui/compat/ClothConfigScreen.java:49` | `render/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 154 | `client/gui/components/CreativePackFilters.java:248` | `renderWidget/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 155 | `client/gui/components/FlatColorButton.java:40` | `renderWidget/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 156 | `client/gui/components/GunPackList.java:135` | `getScrollbarPosition/0` → `int` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 157 | `client/gui/components/GunPackList.java:139` | `render/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 158 | `client/gui/components/GunPackList.java:166` | `getRowLeft/0` → `int` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 159 | `client/gui/components/GunPackList.java:170` | `getRowWidth/0` → `int` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 160 | `client/gui/components/GunPackList.java:181` | `narratables/0` → `List<?extendsNarratableEntry>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 161 | `client/gui/components/GunPackList.java:186` | `render/10` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 162 | `client/gui/components/GunPackList.java:193` | `children/0` → `List<?extendsGuiEventListener>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 163 | `client/gui/components/GunPackList.java:232` | `updateWidgetNarration/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 164 | `client/gui/components/GunPackList.java:244` | `renderWidget/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 165 | `client/gui/components/refit/GunAttachmentSlot.java:41` | `renderWidget/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 166 | `client/gui/components/refit/InventoryAttachmentSlot.java:42` | `renderWidget/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 167 | `client/gui/components/refit/RefitTurnPageButton.java:20` | `renderWidget/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 168 | `client/gui/components/refit/RefitUnloadButton.java:17` | `renderWidget/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 169 | `client/gui/components/smith/ResultButton.java:23` | `renderWidget/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 170 | `client/gui/components/smith/TypeButton.java:20` | `renderWidget/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 171 | `client/gui/overlay/DisplayCaseLockHintOverlay.java:25` | `render/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 172 | `client/gui/overlay/GunHudOverlay.java:48` | `render/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 173 | `client/gui/overlay/HeatBarOverlay.java:28` | `render/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 174 | `client/gui/overlay/InteractKeyTextOverlay.java:33` | `render/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 175 | `client/gui/overlay/KillAmountOverlay.java:19` | `render/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 176 | `client/gui/toast/GunLevelUpToast.java:24` | `render/3` → `Visibility` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 177 | `client/init/ClientSetupEvent.java:32` | `onRegisterShaders/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 178 | `client/init/ClientSetupEvent.java:36` | `onClientSetup/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 179 | `client/init/ClientSetupEvent.java:42` | `onClientSetup/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 180 | `client/init/ClientSetupEvent.java:63` | `onClientSetup/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 181 | `client/init/ClientSetupEvent.java:52` | `onRegisterGuiOverlays/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 182 | `client/init/ClientSetupEvent.java:93` | `onClientResourceReload/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 183 | `client/init/GunPegboardColors.java:17` | `blockColors/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 184 | `client/init/GunPegboardColors.java:23` | `itemColors/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 185 | `client/init/GunPegboardColors.java:29` | `setup/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 186 | `client/init/ModContainerScreen.java:15` | `clientSetup/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 187 | `client/init/ModEntitiesRender.java:25` | `onEntityRenderers/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 188 | `client/init/ParticleFactoryRegistry.java:12` | `onRegisterParticleFactory/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 189 | `client/input/GunInputHandler.java:36` | `registerKeyMappings/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 190 | `client/input/GunInputHandler.java:43` | `onKeyPress/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 191 | `client/input/GunInputHandler.java:57` | `onMousePress/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 192 | `client/input/GunInputHandler.java:68` | `onMousePressPre/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 193 | `client/model/functional/BeamRenderer.java:210` | `getLaserBeam/0` → `RenderType` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 194 | `client/model/functional/BeamRenderer.java:214` | `getLaserBeamEntity/0` → `RenderType` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 195 | `client/particle/BulletHoleParticle.java:137` | `tick/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 196 | `client/particle/BulletHoleParticle.java:149` | `render/3` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 197 | `client/particle/BulletHoleParticle.java:202` | `getRenderType/0` → `ParticleRenderType` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 198 | `client/particle/BulletHoleParticle.java:231` | `createParticle/8` → `BulletHoleParticle` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 199 | `client/particle/HitBlockEffectHandler.java:26` | `handle/8` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 200 | `client/particle/HitSurfaceParticleSpawner.java:49` | `spawn/8` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 201 | `client/renderer/block/DisplayGunGeometry.java:54` | `capture/3` → `boolean` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 202 | `client/renderer/block/DisplayGunGeometry.java:103` | `renderAttachments/7` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 203 | `client/renderer/block/DisplayGunGeometry.java:175` | `renderModel/5` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 204 | `client/renderer/block/DisplayGunMeshGroup.java:47` | `isValid/1` → `boolean` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 205 | `client/renderer/block/DisplayGunMeshGroup.java:59` | `isVisible/1` → `boolean` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 206 | `client/renderer/block/DisplayGunMeshGroup.java:64` | `origin/1` → `Vec3` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 207 | `client/renderer/block/DisplayGunMeshGroup.java:82` | `packedLight/1` → `int` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 208 | `client/renderer/block/DisplayGunMeshGroup.java:92` | `geometryKey/1` → `Object` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 209 | `client/renderer/block/DisplayGunMeshGroup.java:98` | `collectGeometry/2` → `boolean` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 210 | `client/renderer/block/GunDisplayCaseRenderer.java:122` | `shouldRenderOffScreen/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 211 | `client/renderer/block/GunDisplayCaseRenderer.java:127` | `shouldRender/2` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 212 | `client/renderer/block/GunPegboardRenderer.java:122` | `shouldRenderOffScreen/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 213 | `client/renderer/block/GunPegboardRenderer.java:127` | `shouldRender/2` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 214 | `client/renderer/block/GunSmithTableRenderer.java:73` | `shouldRenderOffScreen/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 215 | `client/renderer/block/StatueRenderer.java:90` | `shouldRenderOffScreen/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 216 | `client/renderer/block/TargetRenderer.java:79` | `shouldRenderOffScreen/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 217 | `client/renderer/entity/EntityBulletRenderer.java:133` | `getBlockLightLevel/2` → `int` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 218 | `client/renderer/entity/EntityBulletRenderer.java:147` | `getTextureLocation/1` → `ResourceLocation` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 219 | `client/renderer/entity/TargetMinecartRenderer.java:40` | `getTextureLocation/1` → `ResourceLocation` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 220 | `client/renderer/entity/TargetMinecartRenderer.java:45` | `renderMinecartContents/6` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 221 | `client/renderer/scope/ScopeLensContour.java:28` | `registerShaders/1` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 222 | `client/renderer/scope/ScopeLensRenderer.java:44` | `registerShader/1` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 223 | `client/renderer/scope/ScopeLensRenderer.java:415` | `defaultColor/4` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 224 | `client/renderer/scope/ScopeLensRenderer.java:416` | `unsetDefaultColor/0` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 225 | `client/renderer/scope/ScopeThermalRenderer.java:54` | `registerShader/1` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 226 | `client/renderer/scope/ScopeThermalRenderer.java:116` | `onWorldStage/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 227 | `client/renderer/scope/ScopeThermalRenderer.java:287` | `defaultColor/4` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 228 | `client/renderer/scope/ScopeThermalRenderer.java:290` | `unsetDefaultColor/0` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 229 | `client/resource/manager/DisplayManager.java:29` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 230 | `client/resource/manager/PackInfoManager.java:24` | `prepare/2` → `Map<String,PackInfo>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 231 | `client/resource/manager/PackInfoManager.java:44` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 232 | `client/sound/EntityTrackingGunSoundInstance.java:27` | `isStopped/0` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 233 | `client/sound/EntityTrackingGunSoundInstance.java:32` | `tick/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 234 | `client/sound/GunSoundInstance.java:61` | `resolve/1` → `WeighedSoundEvents` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 235 | `client/sound/GunSoundInstance.java:73` | `getSound/0` → `Sound` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 236 | `client/sound/GunSoundInstance.java:89` | `getLocation/0` → `ResourceLocation` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 237 | `client/sound/GunSoundInstance.java:94` | `getPath/0` → `ResourceLocation` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 238 | `client/tooltip/ClientAmmoBoxTooltip.java:37` | `renderText/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 239 | `client/tooltip/ClientAmmoStorageTooltip.java:37` | `renderText/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 240 | `client/tooltip/ClientAttachmentItemTooltip.java:105` | `renderText/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 241 | `client/tooltip/ClientBlockItemTooltip.java:55` | `renderText/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 242 | `client/tooltip/ClientGunTooltip.java:235` | `renderText/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 243 | `command/RootCommand.java:10` | `register/1` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 244 | `command/sub/AttachmentLockCommand.java:20` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 245 | `command/sub/ConfigCommand.java:16` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 246 | `command/sub/ConvertCommand.java:13` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 247 | `command/sub/DebugCommand.java:16` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 248 | `command/sub/DummyAmmoCommand.java:20` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 249 | `command/sub/HideTooltipPartCommand.java:21` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 250 | `command/sub/OverwriteCommand.java:16` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 251 | `command/sub/PegboardStressCommand.java:35` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 252 | `command/sub/ReloadCommand.java:18` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 253 | `compat/cloth/MenuIntegration.java:19` | `getConfigBuilder/0` → `ConfigBuilder` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 254 | `compat/jei/GunModPlugin.java:42` | `registerCategories/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 255 | `compat/jei/GunModPlugin.java:59` | `registerRecipes/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 256 | `compat/jei/GunModPlugin.java:131` | `registerRecipeCatalysts/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 257 | `compat/jei/GunModPlugin.java:143` | `registerRecipeTransferHandlers/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 258 | `compat/jei/GunModPlugin.java:150` | `registerItemSubtypes/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 259 | `compat/kubejs/TimelessKubeJSPlugin.java:39` | `registerEvents/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 260 | `compat/kubejs/TimelessKubeJSPlugin.java:48` | `registerBindings/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 261 | `compat/kubejs/TimelessKubeJSPlugin.java:55` | `registerTypeWrappers/2` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 262 | `compat/kubejs/TimelessKubeJSPlugin.java:60` | `registerRecipeSchemas/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 263 | `compat/kubejs/TimelessKubeJSPlugin.java:65` | `registerRecipeComponents/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 264 | `compat/kubejs/TimelessKubeJSPlugin.java:74` | `onItemRegister/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 265 | `config/ClientConfig.java:7` | `init/0` → `ForgeConfigSpec` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 266 | `config/CommonConfig.java:9` | `init/0` → `ForgeConfigSpec` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 267 | `config/PreLoadConfig.java:26` | `getModConfig/0` → `PreLoadModConfig` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 268 | `config/ServerConfig.java:10` | `init/0` → `ForgeConfigSpec` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 269 | `crafting/AmmoStorageUpgradeRecipe.java:55` | `assemble/2` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 270 | `crafting/AmmoStorageUpgradeRecipe.java:65` | `getIngredients/0` → `NonNullList<Ingredient>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 271 | `crafting/AmmoStorageUpgradeRecipe.java:75` | `getResultItem/1` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 272 | `crafting/AmmoStorageUpgradeRecipe.java:80` | `canCraftInDimensions/2` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 273 | `crafting/AmmoStorageUpgradeRecipe.java:85` | `getRecipeWidth/0` → `int` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 274 | `crafting/AmmoStorageUpgradeRecipe.java:90` | `getRecipeHeight/0` → `int` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 275 | `crafting/AmmoStorageUpgradeRecipe.java:95` | `getSerializer/0` → `RecipeSerializer<?>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 276 | `crafting/GunPegboardDyeRecipe.java:41` | `assemble/2` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 277 | `crafting/GunPegboardDyeRecipe.java:59` | `canCraftInDimensions/2` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 278 | `crafting/GunPegboardDyeRecipe.java:64` | `getSerializer/0` → `RecipeSerializer<?>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 279 | `crafting/GunSmithTableRecipe.java:30` | `matches/2` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 280 | `crafting/GunSmithTableRecipe.java:36` | `assemble/2` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 281 | `crafting/GunSmithTableRecipe.java:42` | `canCraftInDimensions/2` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 282 | `crafting/GunSmithTableRecipe.java:47` | `getResultItem/1` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 283 | `crafting/GunSmithTableRecipe.java:57` | `getSerializer/0` → `RecipeSerializer<?>` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 284 | `crafting/GunSmithTableRecipe.java:62` | `getType/0` → `RecipeType<?>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 285 | `crafting/GunSmithTableSerializer.java:20` | `fromJson/2` → `GunSmithTableRecipe` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 286 | `crafting/GunSmithTableSerializer.java:29` | `fromNetwork/2` → `GunSmithTableRecipe` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 287 | `crafting/GunSmithTableSerializer.java:44` | `toNetwork/2` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 288 | `entity/EntityKineticBullet.java:238` | `defineSynchedData/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 289 | `entity/EntityKineticBullet.java:242` | `tick/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 290 | `entity/EntityKineticBullet.java:382` | `MaybeMultipartEntity/2` → `record` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 291 | `entity/EntityKineticBullet.java:387` | `of/1` → `MaybeMultipartEntity` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 292 | `entity/EntityKineticBullet.java:606` | `getAddEntityPacket/0` → `Packet<ClientGamePacketListener>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 293 | `entity/EntityKineticBullet.java:677` | `getRandom/0` → `RandomSource` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 294 | `entity/EntityKineticBullet.java:747` | `ownedBy/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 295 | `entity/TargetMinecart.java:87` | `isInvulnerableTo/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 296 | `entity/TargetMinecart.java:92` | `canBeRidden/0` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 297 | `entity/TargetMinecart.java:97` | `shouldRenderAtSqrDistance/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 298 | `entity/TargetMinecart.java:107` | `destroy/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 299 | `entity/TargetMinecart.java:119` | `getDropItem/0` → `Item` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 300 | `entity/TargetMinecart.java:124` | `getPickResult/0` → `ItemStack` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 301 | `entity/TargetMinecart.java:142` | `getDefaultDisplayBlockState/0` → `BlockState` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 302 | `entity/TargetMinecart.java:154` | `getMaxCartSpeedOnRail/0` → `float` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 303 | `event/CommonLoadPack.java:10` | `loadGunPack/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 304 | `event/GunDisplayCaseInteractionEvent.java:23` | `onRightClickBlock/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 305 | `event/KnockbackChange.java:9` | `onKnockback/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 306 | `event/LoadingConfigEvent.java:14` | `onLoadingConfig/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 307 | `event/LoadingConfigEvent.java:30` | `onReloadingConfig/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 308 | `event/SyncedEntityDataEvent.java:26` | `attachCapabilities/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 309 | `init/CapabilityRegistry.java:9` | `onRegisterCapabilities/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 310 | `init/CommandRegistry.java:9` | `onServerStaring/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 311 | `init/CommonRegistry.java:18` | `onSetupEvent/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 312 | `init/CommonRegistry.java:28` | `onLoadComplete/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 313 | `init/CommonRegistry.java:37` | `registerAttributes/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 314 | `init/CommonRegistry.java:44` | `onAddPackFinders/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 315 | `init/CompatRegistry.java:19` | `onEnqueue/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 316 | `init/ModDamageTypes.java:25` | `bullet/4` → `DamageSource` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 317 | `init/ModDamageTypes.java:29` | `bulletVoid/4` → `DamageSource` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 318 | `init/ModItems.java:47` | `appendHoverText/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 319 | `init/ModItems.java:54` | `onItemRegister/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 320 | `init/ModParticles.java:25` | `codec/0` → `Codec<T>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 321 | `init/ModRecipe.java:24` | `toString/0` → `String` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 322 | `inventory/AmmoStorageMenu.java:75` | `broadcastChanges/0` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 323 | `inventory/AmmoStorageMenu.java:144` | `quickMoveStack/2` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 324 | `inventory/AmmoStorageMenu.java:174` | `stillValid/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 325 | `inventory/GunSmithTableMenu.java:50` | `canSelectRecipe/2` → `boolean` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 326 | `inventory/GunSmithTableMenu.java:76` | `quickMoveStack/2` → `ItemStack` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 327 | `inventory/GunSmithTableMenu.java:81` | `stillValid/1` → `boolean` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 328 | `item/AmmoBoxItem.java:60` | `getStatue/4` → `float` | SIG | 1.7.10 ItemColor 接口无 level/entity/seed 参数 → 参数由 4 收窄为 1（方法 javadoc 已注明） |
| 329 | `item/AmmoBoxItem.java:95` | `overrideOtherStackedOnMe/6` → `boolean` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 330 | `item/AmmoBoxItem.java:100` | `overrideStackedOnOther/4` → `boolean` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 331 | `item/AmmoBoxItem.java:188` | `isBarVisible/1` → `boolean` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 332 | `item/AmmoBoxItem.java:196` | `getBarWidth/1` → `int` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 333 | `item/AmmoBoxItem.java:208` | `getName/1` → `Component` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 334 | `item/AmmoBoxItem.java:230` | `isFoil/1` → `boolean` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 335 | `item/AmmoBoxItem.java:238` | `getBarColor/1` → `int` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 336 | `item/AmmoBoxItem.java:243` | `fillItemCategory/1` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 337 | `item/AmmoBoxItem.java:257` | `getTooltipImage/1` → `Optional<TooltipComponent>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 338 | `item/AmmoBoxItem.java:274` | `appendHoverText/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 339 | `item/AmmoItem.java:39` | `getMaxStackSize/1` → `int` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 340 | `item/AmmoItem.java:48` | `getName/1` → `Component` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 341 | `item/AmmoItem.java:73` | `initializeClient/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 342 | `item/AmmoItem.java:78` | `getCustomRenderer/0` → `BlockEntityWithoutLevelRenderer` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 343 | `item/AmmoItem.java:88` | `appendHoverText/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 344 | `item/AmmoStorageBlockItem.java:31` | `canFitInsideContainerItems/0` → `boolean` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 345 | `item/AmmoStorageBlockItem.java:36` | `getName/1` → `Component` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 346 | `item/AmmoStorageBlockItem.java:48` | `appendHoverText/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 347 | `item/AmmoStorageBlockItem.java:55` | `getTooltipImage/1` → `Optional<TooltipComponent>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 348 | `item/AttachmentItem.java:38` | `getName/1` → `Component` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 349 | `item/AttachmentItem.java:68` | `initializeClient/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 350 | `item/AttachmentItem.java:73` | `getCustomRenderer/0` → `BlockEntityWithoutLevelRenderer` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 351 | `item/AttachmentItem.java:96` | `getTooltipImage/1` → `Optional<TooltipComponent>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 352 | `item/AttachmentItem.java:101` | `verifyTagAfterLoad/1` → `void` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 353 | `item/GunPegboardItem.java:23` | `place/1` → `InteractionResult` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 354 | `item/GunPegboardItem.java:45` | `appendHoverText/4` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 355 | `item/GunSmithTableItem.java:32` | `initializeClient/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 356 | `item/GunSmithTableItem.java:37` | `getCustomRenderer/0` → `BlockEntityWithoutLevelRenderer` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 357 | `item/GunSmithTableItem.java:61` | `getName/1` → `Component` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 358 | `item/GunSmithTableItem.java:73` | `getTooltipImage/1` → `Optional<TooltipComponent>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 359 | `item/ModernKineticGunItem.java:66` | `inventoryTick/5` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 360 | `item/TargetMinecartItem.java:20` | `useOn/1` → `InteractionResult` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 361 | `loot/LootTableInjectorModifier.java:25` | `doApply/2` → `ObjectArrayList<ItemStack>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 362 | `loot/LootTableInjectorModifier.java:46` | `codec/0` → `Codec<?extendsIGlobalLootModifier>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 363 | `mixin/client/GameRendererMixin.java:46` | `getMinecraft/0` → `Minecraft` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 364 | `mixin/client/GameRendererMixin.java:49` | `render/3` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 365 | `network/NetworkHandler.java:131` | `registerAcknowledge/0` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 366 | `network/NetworkHandler.java:141` | `registerHandshakeMessage/2` → `<TextendsLoginIndexHolder&IMessage<T>>void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 367 | `network/message/ClientMessageAmmoStorageClick.java:8` | `ClientMessageAmmoStorageClick/3` → `record` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 368 | `network/message/ClientMessageAmmoStorageClick.java:11` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 369 | `network/message/ClientMessageAmmoStorageClick.java:16` | `decode/1` → `ClientMessageAmmoStorageClick` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 370 | `network/message/ClientMessageAmmoStorageInteract.java:32` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 371 | `network/message/ClientMessageAmmoStorageInteract.java:36` | `decode/1` → `ClientMessageAmmoStorageInteract` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 372 | `network/message/ClientMessageCraft.java:18` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 373 | `network/message/ClientMessageCraft.java:23` | `decode/1` → `ClientMessageCraft` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 374 | `network/message/ClientMessageInstallVirtualAttachment.java:29` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 375 | `network/message/ClientMessageInstallVirtualAttachment.java:35` | `decode/1` → `ClientMessageInstallVirtualAttachment` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 376 | `network/message/ClientMessageLaserColor.java:43` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 377 | `network/message/ClientMessageLaserColor.java:50` | `decode/1` → `ClientMessageLaserColor` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 378 | `network/message/ClientMessagePlayerAim.java:15` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 379 | `network/message/ClientMessagePlayerAim.java:19` | `decode/1` → `ClientMessagePlayerAim` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 380 | `network/message/ClientMessagePlayerBoltGun.java:12` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 381 | `network/message/ClientMessagePlayerBoltGun.java:15` | `decode/1` → `ClientMessagePlayerBoltGun` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 382 | `network/message/ClientMessagePlayerCancelReload.java:12` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 383 | `network/message/ClientMessagePlayerCancelReload.java:15` | `decode/1` → `ClientMessagePlayerCancelReload` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 384 | `network/message/ClientMessagePlayerCrawl.java:15` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 385 | `network/message/ClientMessagePlayerCrawl.java:19` | `decode/1` → `ClientMessagePlayerCrawl` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 386 | `network/message/ClientMessagePlayerDrawGun.java:13` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 387 | `network/message/ClientMessagePlayerDrawGun.java:16` | `decode/1` → `ClientMessagePlayerDrawGun` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 388 | `network/message/ClientMessagePlayerFireSelect.java:10` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 389 | `network/message/ClientMessagePlayerFireSelect.java:12` | `decode/1` → `ClientMessagePlayerFireSelect` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 390 | `network/message/ClientMessagePlayerMelee.java:10` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 391 | `network/message/ClientMessagePlayerMelee.java:12` | `decode/1` → `ClientMessagePlayerMelee` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 392 | `network/message/ClientMessagePlayerReloadGun.java:12` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 393 | `network/message/ClientMessagePlayerReloadGun.java:15` | `decode/1` → `ClientMessagePlayerReloadGun` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 394 | `network/message/ClientMessagePlayerShoot.java:27` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 395 | `network/message/ClientMessagePlayerShoot.java:32` | `decode/1` → `ClientMessagePlayerShoot` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 396 | `network/message/ClientMessagePlayerUnloadGun.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 397 | `network/message/ClientMessagePlayerUnloadGun.java:29` | `decode/1` → `ClientMessagePlayerUnloadGun` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 398 | `network/message/ClientMessagePlayerZoom.java:10` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 399 | `network/message/ClientMessagePlayerZoom.java:12` | `decode/1` → `ClientMessagePlayerZoom` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 400 | `network/message/ClientMessageRefitGun.java:27` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 401 | `network/message/ClientMessageRefitGun.java:33` | `decode/1` → `ClientMessageRefitGun` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 402 | `network/message/ClientMessageSetAttachmentVariant.java:30` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 403 | `network/message/ClientMessageSetAttachmentVariant.java:39` | `decode/1` → `ClientMessageSetAttachmentVariant` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 404 | `network/message/ClientMessageSetSlotAdapter.java:30` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 405 | `network/message/ClientMessageSetSlotAdapter.java:39` | `decode/1` → `ClientMessageSetSlotAdapter` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 406 | `network/message/ClientMessageSyncBaseTimestamp.java:18` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 407 | `network/message/ClientMessageSyncBaseTimestamp.java:20` | `decode/1` → `ClientMessageSyncBaseTimestamp` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 408 | `network/message/ClientMessageUnloadAttachment.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 409 | `network/message/ClientMessageUnloadAttachment.java:29` | `decode/1` → `ClientMessageUnloadAttachment` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 410 | `network/message/ServerMessageAmmoStorageContents.java:15` | `ServerMessageAmmoStorageContents/4` → `record` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 411 | `network/message/ServerMessageAmmoStorageContents.java:21` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 412 | `network/message/ServerMessageAmmoStorageContents.java:34` | `decode/1` → `ServerMessageAmmoStorageContents` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 413 | `network/message/ServerMessageBulletHitBlock.java:49` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 414 | `network/message/ServerMessageBulletHitBlock.java:61` | `decode/1` → `ServerMessageBulletHitBlock` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 415 | `network/message/ServerMessageCraft.java:18` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 416 | `network/message/ServerMessageCraft.java:22` | `decode/1` → `ServerMessageCraft` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 417 | `network/message/ServerMessageGunUnload.java:22` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 418 | `network/message/ServerMessageGunUnload.java:27` | `decode/1` → `ServerMessageGunUnload` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 419 | `network/message/ServerMessageLevelUp.java:20` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 420 | `network/message/ServerMessageLevelUp.java:25` | `decode/1` → `ServerMessageLevelUp` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 421 | `network/message/ServerMessageRefreshRefitScreen.java:14` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 422 | `network/message/ServerMessageRefreshRefitScreen.java:16` | `decode/1` → `ServerMessageRefreshRefitScreen` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 423 | `network/message/ServerMessageSound.java:32` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 424 | `network/message/ServerMessageSound.java:42` | `decode/1` → `ServerMessageSound` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 425 | `network/message/ServerMessageSwapItem.java:12` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 426 | `network/message/ServerMessageSwapItem.java:15` | `decode/1` → `ServerMessageSwapItem` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 427 | `network/message/ServerMessageSyncBaseTimestamp.java:23` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 428 | `network/message/ServerMessageSyncBaseTimestamp.java:25` | `decode/1` → `ServerMessageSyncBaseTimestamp` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 429 | `network/message/ServerMessageSyncGunPack.java:55` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 430 | `network/message/ServerMessageSyncGunPack.java:64` | `decode/1` → `ServerMessageSyncGunPack` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 431 | `network/message/ServerMessageUpdateEntityData.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 432 | `network/message/ServerMessageUpdateEntityData.java:30` | `decode/1` → `ServerMessageUpdateEntityData` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 433 | `network/message/event/ServerMessageGunDraw.java:26` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 434 | `network/message/event/ServerMessageGunDraw.java:32` | `decode/1` → `ServerMessageGunDraw` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 435 | `network/message/event/ServerMessageGunFinishReload.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 436 | `network/message/event/ServerMessageGunFinishReload.java:29` | `decode/1` → `ServerMessageGunFinishReload` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 437 | `network/message/event/ServerMessageGunFire.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 438 | `network/message/event/ServerMessageGunFire.java:29` | `decode/1` → `ServerMessageGunFire` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 439 | `network/message/event/ServerMessageGunFirePost.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 440 | `network/message/event/ServerMessageGunFirePost.java:29` | `decode/1` → `ServerMessageGunFirePost` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 441 | `network/message/event/ServerMessageGunFireSelect.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 442 | `network/message/event/ServerMessageGunFireSelect.java:29` | `decode/1` → `ServerMessageGunFireSelect` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 443 | `network/message/event/ServerMessageGunHurt.java:39` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 444 | `network/message/event/ServerMessageGunHurt.java:50` | `decode/1` → `ServerMessageGunHurt` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 445 | `network/message/event/ServerMessageGunKill.java:38` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 446 | `network/message/event/ServerMessageGunKill.java:49` | `decode/1` → `ServerMessageGunKill` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 447 | `network/message/event/ServerMessageGunMelee.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 448 | `network/message/event/ServerMessageGunMelee.java:29` | `decode/1` → `ServerMessageGunMelee` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 449 | `network/message/event/ServerMessageGunReload.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 450 | `network/message/event/ServerMessageGunReload.java:29` | `decode/1` → `ServerMessageGunReload` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 451 | `network/message/event/ServerMessageGunShoot.java:24` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 452 | `network/message/event/ServerMessageGunShoot.java:29` | `decode/1` → `ServerMessageGunShoot` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 453 | `network/message/handshake/Acknowledge.java:14` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 454 | `network/message/handshake/Acknowledge.java:18` | `decode/1` → `Acknowledge` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 455 | `network/message/handshake/ServerMessageSyncedEntityDataMapping.java:30` | `encode/2` → `void` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 456 | `network/message/handshake/ServerMessageSyncedEntityDataMapping.java:42` | `decode/1` → `ServerMessageSyncedEntityDataMapping` | NET | SimpleChannel encode/decode → 1.7.10 IMessage.fromBytes/toBytes；由 network/MessageBase 承接（read/write） |
| 457 | `particles/BulletHoleOption.java:27` | `fromCommand/2` → `BulletHoleOption` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 458 | `particles/BulletHoleOption.java:46` | `fromNetwork/2` → `BulletHoleOption` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 459 | `particles/BulletHoleOption.java:89` | `getPos/0` → `BlockPos` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 460 | `particles/BulletHoleOption.java:109` | `getType/0` → `ParticleType<?>` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 461 | `particles/BulletHoleOption.java:114` | `writeToNetwork/1` → `void` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 462 | `particles/BulletHoleOption.java:124` | `writeToString/0` → `String` | PLATFORM | 1.20 平台 API 覆写/事件钩子；1.7.10 无同名钩子 |
| 463 | `resource/CommonAssetsManager.java:92` | `reloadAndRegister/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 464 | `resource/CommonAssetsManager.java:295` | `onReload/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 465 | `resource/CommonAssetsManager.java:305` | `onReload/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 466 | `resource/CommonAssetsManager.java:322` | `onServerStopped/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 467 | `resource/CommonAssetsManager.java:328` | `OnDatapackSync/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 468 | `resource/GunPackLoader.java:54` | `loadPacks/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 469 | `resource/GunPackLoader.java:63` | `discoverExtensions/0` → `Pack` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 470 | `resource/GunPackLoader.java:97` | `resolve/1` → `Path` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 471 | `resource/GunPackLoader.java:106` | `getResource/2` → `IoSupplier<InputStream>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 472 | `resource/GunPackLoader.java:110` | `listResources/4` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 473 | `resource/GunPackLoader.java:122` | `getRootResource/1` → `IoSupplier<InputStream>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 474 | `resource/GunPackLoader.java:134` | `getModIcon/1` → `Path` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 475 | `resource/manager/AttachmentsTagManager.java:39` | `prepare/2` → `Map<ResourceLocation,List<JsonElement>>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 476 | `resource/manager/AttachmentsTagManager.java:45` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 477 | `resource/manager/CommonDataManager.java:31` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 478 | `resource/manager/GunIndexManager.java:18` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 479 | `resource/manager/JsonDataManager.java:44` | `prepare/2` → `Map<ResourceLocation,JsonElement>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 480 | `resource/manager/JsonDataManager.java:50` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 481 | `resource/manager/LazyJsonDataManager.java:61` | `prepare/2` → `PreparedResult<T>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 482 | `resource/manager/LazyJsonDataManager.java:84` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 483 | `resource/manager/LazyJsonDataManager.java:116` | `loadResource/3` → `LoadResult<T>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 484 | `resource/manager/LazyJsonDataManager.java:137` | `openReader/2` → `Reader` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 485 | `resource/manager/LazyJsonDataManager.java:143` | `readResourceElement/2` → `JsonElement` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 486 | `resource/manager/LootInjectionManager.java:28` | `prepare/2` → `Map<ResourceLocation,List<JsonElement>>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 487 | `resource/manager/LootInjectionManager.java:33` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 488 | `resource/manager/RecipeFilterManager.java:39` | `prepare/2` → `Map<ResourceLocation,List<JsonElement>>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 489 | `resource/manager/RecipeFilterManager.java:45` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 490 | `resource/manager/ScriptManager.java:41` | `prepare/2` → `List<Map.Entry<String,Supplier<LuaTable>>>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 491 | `resource/manager/ScriptManager.java:61` | `apply/3` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 492 | `resource/pojo/data/loot/LootTableInjection.java:42` | `createStacks/1` → `List<ItemStack>` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 493 | `util/AllowAttachmentTagMatcher.java:13` | `Cache/2` → `record` | PLATFORM? | 低频名：已人工核对为 1.20 平台/事件/渲染钩子（见主报告 §六 抽样说明） |
| 494 | `util/EntityUtil.java:79` | `getHitResult/4` → `EntityKineticBullet.EntityResult` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 495 | `util/RenderHelper.java:27` | `blit/9` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 496 | `util/RenderHelper.java:86` | `getBufferSource/1` → `MultiBufferSource` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 497 | `util/RenderHelper.java:97` | `renderFirstPersonArm/4` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 498 | `util/RenderHelper.java:101` | `renderFirstPersonArm/5` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 499 | `util/RenderHelper.java:128` | `endBatch/2` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 500 | `util/RenderHelper.java:134` | `endBatch/1` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |
| 501 | `util/block/BlockRayTrace.java:23` | `rayTraceBlocks/2` → `BlockHitResult` | SIG | 方法名保留；BlockState/BlockPos→metadata/x,y,z 导致元数变化（javadoc 已注明） |
| 502 | `util/block/ProjectileExplosion.java:56` | `explode/0` → `void` | MECH | 上游机制由新类/平台等价物承接（见主报告 §二） |

分类计数：`PLATFORM`=262 / `SIG`=14 / `MECH`=91 / `PLATFORM?`=47 / `NET`=88

### B3. 同名文件内新增方法全量（709）

列：本移植 `file:line`；`maybe_matches_upstream` 为「同元数 + 同/近似返回类型」的上游方法，用于人工判断是否是改名。

| # | 本移植位置 | 方法/元数 | 可见性/static | 返回 | 形参 | 上游同形候选 |
|---|---|---|---|---|---|---|
| 1 | `GunMod.java:64` | `preInit/1` | public/instance | `void` | `FMLPreInitializationEvent` | — |
| 2 | `GunMod.java:70` | `init/1` | public/instance | `void` | `FMLInitializationEvent` | — |
| 3 | `GunMod.java:75` | `postInit/1` | public/instance | `void` | `FMLPostInitializationEvent` | — |
| 4 | `GunMod.java:80` | `serverStarting/1` | public/instance | `void` | `FMLServerStartingEvent` | — |
| 5 | `advancement/GunRefitTrigger.java:51` | `getPlayer/0` | public/instance | `EntityPlayerMP` | `` | — |
| 6 | `advancement/GunRefitTrigger.java:55` | `getBefore/0` | public/instance | `ItemStack` | `` | — |
| 7 | `advancement/GunRefitTrigger.java:59` | `getAfter/0` | public/instance | `ItemStack` | `` | — |
| 8 | `advancement/TargetHitTrigger.java:46` | `getPlayer/0` | public/instance | `EntityPlayerMP` | `` | — |
| 9 | `advancement/TargetHitTrigger.java:50` | `getDistance/0` | public/instance | `double` | `` | — |
| 10 | `api/GunProperties.java:52` | `get/0` | public/instance | `T` | `` | — |
| 11 | `api/event/server/AmmoHitBlockEvent.java:53` | `getHitX/0` | public/instance | `int` | `` | — |
| 12 | `api/event/server/AmmoHitBlockEvent.java:57` | `getHitY/0` | public/instance | `int` | `` | — |
| 13 | `api/event/server/AmmoHitBlockEvent.java:61` | `getHitZ/0` | public/instance | `int` | `` | — |
| 14 | `api/event/server/AmmoHitBlockEvent.java:65` | `getSide/0` | public/instance | `int` | `` | — |
| 15 | `api/event/server/AmmoHitBlockEvent.java:76` | `getMetadata/0` | public/instance | `int` | `` | — |
| 16 | `api/item/gun/AbstractGunItem.java:331` | `getEntityInventory/1` | protected/static | `IInventory` | `EntityLivingBase` | — |
| 17 | `api/item/gun/AbstractGunItem.java:450` | `getItemStackDisplayName/1` | public/instance | `String` | `ItemStack` | — |
| 18 | `api/item/gun/GunItemManager.java:29` | `getGunItem/1` | public/static | `AbstractGunItem` | `String` | — |
| 19 | `block/AbstractGunSmithTableBlock.java:79` | `registerBlockIcons/1` | public/instance | `void` | `IIconRegister` | createBlockStateDefinition/1@55 |
| 20 | `block/AbstractGunSmithTableBlock.java:90` | `hasTileEntity/1` | public/instance | `boolean` | `int` | isRoot/1@104 |
| 21 | `block/AbstractGunSmithTableBlock.java:95` | `createTileEntity/2` | public/instance | `TileEntity` | `World\|int` | — |
| 22 | `block/AbstractGunSmithTableBlock.java:100` | `onBlockActivated/9` | public/instance | `boolean` | `World\|int\|int\|int\|EntityPlayer\|int\|float\|float\|float` | — |
| 23 | `block/AbstractGunSmithTableBlock.java:123` | `onBlockPlacedBy/6` | public/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 24 | `block/AbstractGunSmithTableBlock.java:142` | `getPickBlock/6` | public/instance | `ItemStack` | `MovingObjectPosition\|World\|int\|int\|int\|EntityPlayer` | — |
| 25 | `block/AbstractGunSmithTableBlock.java:155` | `getPickBlock/5` | public/instance | `ItemStack` | `MovingObjectPosition\|World\|int\|int\|int` | getCloneItemStack/5@91 |
| 26 | `block/AbstractGunSmithTableBlock.java:162` | `breakBlock/6` | public/instance | `void` | `World\|int\|int\|int\|Block\|int` | — |
| 27 | `block/AbstractGunSmithTableBlock.java:194` | `onBlockHarvested/6` | public/instance | `void` | `World\|int\|int\|int\|int\|EntityPlayer` | — |
| 28 | `block/AbstractGunSmithTableBlock.java:202` | `getDrops/6` | public/instance | `ArrayList<ItemStack>` | `World\|int\|int\|int\|int\|int` | — |
| 29 | `block/AbstractGunSmithTableBlock.java:208` | `createDropStack/4` | protected/instance | `ItemStack` | `World\|int\|int\|int` | — |
| 30 | `block/AbstractGunSmithTableBlock.java:219` | `removeWithoutDrop/4` | protected/instance | `void` | `World\|int\|int\|int` | — |
| 31 | `block/AbstractGunSmithTableBlock.java:229` | `isReplaceable/4` | public/static | `boolean` | `World\|int\|int\|int` | — |
| 32 | `block/AbstractGunSmithTableBlock.java:242` | `refundAndRemove/6` | protected/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 33 | `block/AbstractGunSmithTableBlock.java:268` | `getRenderType/0` | public/instance | `int` | `` | — |
| 34 | `block/AbstractGunSmithTableBlock.java:274` | `isOpaqueCube/0` | public/instance | `boolean` | `` | — |
| 35 | `block/AbstractGunSmithTableBlock.java:280` | `renderAsNormalBlock/0` | public/instance | `boolean` | `` | — |
| 36 | `block/AbstractGunSmithTableBlock.java:285` | `getMobilityFlag/0` | public/instance | `int` | `` | — |
| 37 | `block/AbstractGunSmithTableBlock.java:294` | `getRootPos/4` | public/instance | `ChunkCoordinates` | `int\|int\|int\|int` | — |
| 38 | `block/AbstractGunSmithTableBlock.java:309` | `getFacing/1` | public/instance | `ForgeDirection` | `int` | — |
| 39 | `block/AbstractGunSmithTableBlock.java:314` | `idOrDefault/1` | public/static | `ResourceLocation` | `ResourceLocation` | — |
| 40 | `block/AmmoStorageBlock.java:109` | `registerBlockIcons/1` | public/instance | `void` | `IIconRegister` | createBlockStateDefinition/1@64 |
| 41 | `block/AmmoStorageBlock.java:126` | `getIcon/2` | public/instance | `IIcon` | `int\|int` | — |
| 42 | `block/AmmoStorageBlock.java:146` | `getFacing/1` | public/static | `ForgeDirection` | `int` | — |
| 43 | `block/AmmoStorageBlock.java:151` | `withFacing/2` | public/static | `int` | `int\|ForgeDirection` | — |
| 44 | `block/AmmoStorageBlock.java:156` | `getFacing/4` | public/instance | `ForgeDirection` | `IBlockAccess\|int\|int\|int` | — |
| 45 | `block/AmmoStorageBlock.java:161` | `hasTileEntity/1` | public/instance | `boolean` | `int` | hasAnalogOutputSignal/1@143 |
| 46 | `block/AmmoStorageBlock.java:166` | `createTileEntity/2` | public/instance | `TileEntity` | `World\|int` | — |
| 47 | `block/AmmoStorageBlock.java:171` | `onBlockPlacedBy/6` | public/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 48 | `block/AmmoStorageBlock.java:185` | `breakBlock/6` | public/instance | `void` | `World\|int\|int\|int\|Block\|int` | — |
| 49 | `block/AmmoStorageBlock.java:215` | `getDrops/6` | public/instance | `ArrayList<ItemStack>` | `World\|int\|int\|int\|int\|int` | — |
| 50 | `block/AmmoStorageBlock.java:221` | `onBlockHarvested/6` | public/instance | `void` | `World\|int\|int\|int\|int\|EntityPlayer` | — |
| 51 | `block/AmmoStorageBlock.java:229` | `onBlockActivated/9` | public/instance | `boolean` | `World\|int\|int\|int\|EntityPlayer\|int\|float\|float\|float` | — |
| 52 | `block/AmmoStorageBlock.java:325` | `hasComparatorInputOverride/0` | public/instance | `boolean` | `` | — |
| 53 | `block/AmmoStorageBlock.java:331` | `getComparatorInputOverride/5` | public/instance | `int` | `World\|int\|int\|int\|int` | — |
| 54 | `block/AmmoStorageBlock.java:341` | `getRenderType/0` | public/instance | `int` | `` | getCapacityStacks/0@60 |
| 55 | `block/AmmoStorageBlock.java:347` | `isOpaqueCube/0` | public/instance | `boolean` | `` | — |
| 56 | `block/AmmoStorageBlock.java:353` | `renderAsNormalBlock/0` | public/instance | `boolean` | `` | — |
| 57 | `block/AmmoStorageBlock.java:358` | `getMobilityFlag/0` | public/instance | `int` | `` | getCapacityStacks/0@60 |
| 58 | `block/GunDisplayCaseBlock.java:93` | `registerBlockIcons/1` | public/instance | `void` | `IIconRegister` | createBlockStateDefinition/1@96 |
| 59 | `block/GunDisplayCaseBlock.java:103` | `getFacing/1` | public/static | `ForgeDirection` | `int` | — |
| 60 | `block/GunDisplayCaseBlock.java:107` | `withFacing/2` | public/static | `int` | `int\|ForgeDirection` | — |
| 61 | `block/GunDisplayCaseBlock.java:111` | `isLocked/1` | public/static | `boolean` | `int` | — |
| 62 | `block/GunDisplayCaseBlock.java:115` | `withLocked/2` | public/static | `int` | `int\|boolean` | — |
| 63 | `block/GunDisplayCaseBlock.java:119` | `isConnected/2` | public/static | `boolean` | `int\|ForgeDirection` | connectsTo/2@316 |
| 64 | `block/GunDisplayCaseBlock.java:125` | `withConnected/3` | public/static | `int` | `int\|ForgeDirection\|boolean` | — |
| 65 | `block/GunDisplayCaseBlock.java:158` | `hasTileEntity/1` | public/instance | `boolean` | `int` | — |
| 66 | `block/GunDisplayCaseBlock.java:163` | `createTileEntity/2` | public/instance | `TileEntity` | `World\|int` | — |
| 67 | `block/GunDisplayCaseBlock.java:168` | `onBlockPlaced/9` | public/instance | `int` | `World\|int\|int\|int\|int\|float\|float\|float\|int` | — |
| 68 | `block/GunDisplayCaseBlock.java:176` | `onBlockPlacedBy/6` | public/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 69 | `block/GunDisplayCaseBlock.java:224` | `onNeighborBlockChange/5` | public/instance | `void` | `World\|int\|int\|int\|Block` | onPlace/5@134 ; onRemove/5@222 ; syncConnectedNeighbor/5@301 |
| 70 | `block/GunDisplayCaseBlock.java:379` | `onBlockActivated/9` | public/instance | `boolean` | `World\|int\|int\|int\|EntityPlayer\|int\|float\|float\|float` | — |
| 71 | `block/GunDisplayCaseBlock.java:436` | `toggleLockFromInteraction/4` | public/instance | `void` | `World\|int\|int\|int` | queueConnectedCases/4@377 |
| 72 | `block/GunDisplayCaseBlock.java:549` | `setBlockBoundsBasedOnState/4` | public/instance | `void` | `IBlockAccess\|int\|int\|int` | queueConnectedCases/4@377 |
| 73 | `block/GunDisplayCaseBlock.java:565` | `getSelectedBoundingBoxFromPool/4` | public/instance | `AxisAlignedBB` | `World\|int\|int\|int` | — |
| 74 | `block/GunDisplayCaseBlock.java:572` | `addCollisionBoxesToList/7` | public/instance | `void` | `World\|int\|int\|int\|AxisAlignedBB\|List<AxisAlignedBB>\|Entity` | — |
| 75 | `block/GunDisplayCaseBlock.java:584` | `getCollisionBoundingBoxFromPool/4` | public/instance | `AxisAlignedBB` | `World\|int\|int\|int` | — |
| 76 | `block/GunDisplayCaseBlock.java:591` | `breakBlock/6` | public/instance | `void` | `World\|int\|int\|int\|Block\|int` | — |
| 77 | `block/GunDisplayCaseBlock.java:605` | `getRenderType/0` | public/instance | `int` | `` | — |
| 78 | `block/GunDisplayCaseBlock.java:611` | `isOpaqueCube/0` | public/instance | `boolean` | `` | — |
| 79 | `block/GunDisplayCaseBlock.java:617` | `renderAsNormalBlock/0` | public/instance | `boolean` | `` | — |
| 80 | `block/GunDisplayCaseBlock.java:622` | `getMobilityFlag/0` | public/instance | `int` | `` | — |
| 81 | `block/GunDisplayCaseBlock.java:628` | `hitVector/6` | public/static | `Vec3` | `int\|int\|int\|float\|float\|float` | — |
| 82 | `block/GunDisplayCaseSlotHelper.java:27` | `getSlot/5` | public/static | `int` | `int\|int\|int\|int\|Vec3` | — |
| 83 | `block/GunDisplayCaseSlotHelper.java:32` | `getSlotFromRelative/4` | public/static | `int` | `int\|double\|double\|double` | — |
| 84 | `block/GunDisplayCaseSlotHelper.java:59` | `getSlotAabb/5` | public/static | `AxisAlignedBB` | `int\|int\|int\|int\|int` | — |
| 85 | `block/GunPegboardBlock.java:83` | `registerBlockIcons/1` | public/instance | `void` | `IIconRegister` | createBlockStateDefinition/1@68 |
| 86 | `block/GunPegboardBlock.java:114` | `getFacing/1` | public/static | `ForgeDirection` | `int` | — |
| 87 | `block/GunPegboardBlock.java:122` | `withFacing/2` | public/static | `int` | `int\|ForgeDirection` | — |
| 88 | `block/GunPegboardBlock.java:126` | `isDyed/1` | public/static | `boolean` | `int` | — |
| 89 | `block/GunPegboardBlock.java:130` | `withDyed/2` | public/static | `int` | `int\|boolean` | — |
| 90 | `block/GunPegboardBlock.java:134` | `getShape/1` | public/static | `PegboardShape` | `int` | — |
| 91 | `block/GunPegboardBlock.java:138` | `withShape/2` | public/static | `int` | `int\|PegboardShape` | — |
| 92 | `block/GunPegboardBlock.java:142` | `hasCorner/1` | public/static | `boolean` | `int` | — |
| 93 | `block/GunPegboardBlock.java:147` | `hasTileEntity/1` | public/instance | `boolean` | `int` | — |
| 94 | `block/GunPegboardBlock.java:156` | `createTileEntity/2` | public/instance | `TileEntity` | `World\|int` | — |
| 95 | `block/GunPegboardBlock.java:161` | `getLightValue/4` | public/instance | `int` | `IBlockAccess\|int\|int\|int` | — |
| 96 | `block/GunPegboardBlock.java:167` | `onBlockPlaced/9` | public/instance | `int` | `World\|int\|int\|int\|int\|float\|float\|float\|int` | — |
| 97 | `block/GunPegboardBlock.java:178` | `onBlockPlacedBy/6` | public/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 98 | `block/GunPegboardBlock.java:272` | `onNeighborBlockChange/5` | public/instance | `void` | `World\|int\|int\|int\|Block` | onRemove/5@212 |
| 99 | `block/GunPegboardBlock.java:292` | `rotateBlock/5` | public/instance | `boolean` | `World\|int\|int\|int\|ForgeDirection` | — |
| 100 | `block/GunPegboardBlock.java:311` | `getValidRotations/4` | public/instance | `ForgeDirection[]` | `World\|int\|int\|int` | — |
| 101 | `block/GunPegboardBlock.java:317` | `onBlockActivated/9` | public/instance | `boolean` | `World\|int\|int\|int\|EntityPlayer\|int\|float\|float\|float` | — |
| 102 | `block/GunPegboardBlock.java:395` | `setBlockBoundsBasedOnState/4` | public/instance | `void` | `IBlockAccess\|int\|int\|int` | — |
| 103 | `block/GunPegboardBlock.java:408` | `getSelectedBoundingBoxFromPool/4` | public/instance | `AxisAlignedBB` | `World\|int\|int\|int` | — |
| 104 | `block/GunPegboardBlock.java:414` | `addCollisionBoxesToList/7` | public/instance | `void` | `World\|int\|int\|int\|AxisAlignedBB\|List<AxisAlignedBB>\|Entity` | — |
| 105 | `block/GunPegboardBlock.java:425` | `getCollisionBoundingBoxFromPool/4` | public/instance | `AxisAlignedBB` | `World\|int\|int\|int` | — |
| 106 | `block/GunPegboardBlock.java:431` | `breakBlock/6` | public/instance | `void` | `World\|int\|int\|int\|Block\|int` | — |
| 107 | `block/GunPegboardBlock.java:473` | `getDrops/6` | public/instance | `ArrayList<ItemStack>` | `World\|int\|int\|int\|int\|int` | — |
| 108 | `block/GunPegboardBlock.java:478` | `onBlockHarvested/6` | public/instance | `void` | `World\|int\|int\|int\|int\|EntityPlayer` | — |
| 109 | `block/GunPegboardBlock.java:486` | `getPickBlock/6` | public/instance | `ItemStack` | `MovingObjectPosition\|World\|int\|int\|int\|EntityPlayer` | — |
| 110 | `block/GunPegboardBlock.java:501` | `getRenderType/0` | public/instance | `int` | `` | — |
| 111 | `block/GunPegboardBlock.java:511` | `isOpaqueCube/0` | public/instance | `boolean` | `` | isGlowing/0@64 |
| 112 | `block/GunPegboardBlock.java:517` | `renderAsNormalBlock/0` | public/instance | `boolean` | `` | isGlowing/0@64 |
| 113 | `block/GunPegboardBlock.java:522` | `getMobilityFlag/0` | public/instance | `int` | `` | — |
| 114 | `block/GunPegboardBlock.java:528` | `hitVector/6` | public/static | `Vec3` | `int\|int\|int\|float\|float\|float` | — |
| 115 | `block/GunPegboardSlotHelper.java:56` | `getSlot/5` | public/static | `int` | `int\|int\|int\|int\|Vec3` | — |
| 116 | `block/GunPegboardSlotHelper.java:61` | `getSlotFromRelative/4` | public/static | `int` | `int\|double\|double\|double` | — |
| 117 | `block/GunPegboardSlotHelper.java:119` | `getSlotAabb/5` | public/static | `AxisAlignedBB` | `int\|int\|int\|int\|int` | — |
| 118 | `block/GunSmithTableBlockA.java:31` | `onBlockPlacedBy/6` | public/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 119 | `block/GunSmithTableBlockA.java:50` | `getRootPos/4` | public/instance | `ChunkCoordinates` | `int\|int\|int\|int` | — |
| 120 | `block/GunSmithTableBlockB.java:42` | `isHead/1` | public/static | `boolean` | `int` | isRoot/1@89 |
| 121 | `block/GunSmithTableBlockB.java:47` | `withHead/2` | public/static | `int` | `int\|boolean` | — |
| 122 | `block/GunSmithTableBlockB.java:57` | `onBlockPlacedBy/6` | public/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 123 | `block/GunSmithTableBlockB.java:81` | `onNeighborBlockChange/5` | public/instance | `void` | `World\|int\|int\|int\|Block` | setPlacedBy/5@52 |
| 124 | `block/GunSmithTableBlockB.java:101` | `onBlockHarvested/6` | public/instance | `void` | `World\|int\|int\|int\|int\|EntityPlayer` | — |
| 125 | `block/GunSmithTableBlockB.java:124` | `getRootPos/4` | public/instance | `ChunkCoordinates` | `int\|int\|int\|int` | — |
| 126 | `block/GunSmithTableBlockC.java:41` | `isUpper/1` | public/static | `boolean` | `int` | isRoot/1@93 |
| 127 | `block/GunSmithTableBlockC.java:46` | `withUpper/2` | public/static | `int` | `int\|boolean` | — |
| 128 | `block/GunSmithTableBlockC.java:51` | `onBlockPlacedBy/6` | public/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 129 | `block/GunSmithTableBlockC.java:75` | `onNeighborBlockChange/5` | public/instance | `void` | `World\|int\|int\|int\|Block` | setPlacedBy/5@49 |
| 130 | `block/GunSmithTableBlockC.java:90` | `onBlockHarvested/6` | public/instance | `void` | `World\|int\|int\|int\|int\|EntityPlayer` | — |
| 131 | `block/GunSmithTableBlockC.java:110` | `getRootPos/4` | public/instance | `ChunkCoordinates` | `int\|int\|int\|int` | — |
| 132 | `block/PegboardShape.java:52` | `byMeta/1` | public/static | `PegboardShape` | `int` | — |
| 133 | `block/PegboardShape.java:65` | `toMeta/0` | public/instance | `int` | `` | — |
| 134 | `block/StatueBlock.java:60` | `registerBlockIcons/1` | public/instance | `void` | `IIconRegister` | createBlockStateDefinition/1@47 |
| 135 | `block/StatueBlock.java:70` | `getFacing/1` | public/static | `net.minecraftforge.common.util.ForgeDirection` | `int` | — |
| 136 | `block/StatueBlock.java:78` | `withFacing/2` | public/static | `int` | `int\|net.minecraftforge.common.util.ForgeDirection` | — |
| 137 | `block/StatueBlock.java:82` | `isUpper/1` | public/static | `boolean` | `int` | — |
| 138 | `block/StatueBlock.java:87` | `withUpper/2` | public/static | `int` | `int\|boolean` | — |
| 139 | `block/StatueBlock.java:91` | `hasTileEntity/1` | public/instance | `boolean` | `int` | — |
| 140 | `block/StatueBlock.java:101` | `createTileEntity/2` | public/instance | `TileEntity` | `World\|int` | — |
| 141 | `block/StatueBlock.java:106` | `onBlockPlacedBy/6` | public/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 142 | `block/StatueBlock.java:131` | `onNeighborBlockChange/5` | public/instance | `void` | `World\|int\|int\|int\|Block` | setPlacedBy/5@97 ; onRemove/5@124 |
| 143 | `block/StatueBlock.java:147` | `onBlockActivated/9` | public/instance | `boolean` | `World\|int\|int\|int\|EntityPlayer\|int\|float\|float\|float` | — |
| 144 | `block/StatueBlock.java:183` | `getDrops/6` | public/instance | `ArrayList<ItemStack>` | `World\|int\|int\|int\|int\|int` | — |
| 145 | `block/StatueBlock.java:197` | `getRenderType/0` | public/instance | `int` | `` | — |
| 146 | `block/StatueBlock.java:207` | `isOpaqueCube/0` | public/instance | `boolean` | `` | — |
| 147 | `block/StatueBlock.java:213` | `renderAsNormalBlock/0` | public/instance | `boolean` | `` | — |
| 148 | `block/StatueBlock.java:218` | `getMobilityFlag/0` | public/instance | `int` | `` | — |
| 149 | `block/TargetBlock.java:90` | `registerBlockIcons/1` | public/instance | `void` | `IIconRegister` | createBlockStateDefinition/1@85 |
| 150 | `block/TargetBlock.java:100` | `getFacing/1` | public/static | `ForgeDirection` | `int` | — |
| 151 | `block/TargetBlock.java:108` | `withFacing/2` | public/static | `int` | `int\|ForgeDirection` | getRedstoneStrength/2@54 |
| 152 | `block/TargetBlock.java:112` | `isUpper/1` | public/static | `boolean` | `int` | isSignalSource/1@219 |
| 153 | `block/TargetBlock.java:116` | `withUpper/2` | public/static | `int` | `int\|boolean` | getRedstoneStrength/2@54 |
| 154 | `block/TargetBlock.java:120` | `isStand/1` | public/static | `boolean` | `int` | isSignalSource/1@219 |
| 155 | `block/TargetBlock.java:124` | `withStand/2` | public/static | `int` | `int\|boolean` | getRedstoneStrength/2@54 |
| 156 | `block/TargetBlock.java:128` | `getRedstoneStrength/5` | public/static | `int` | `int\|double\|double\|double\|boolean` | — |
| 157 | `block/TargetBlock.java:170` | `hasTileEntity/1` | public/instance | `boolean` | `int` | isSignalSource/1@219 |
| 158 | `block/TargetBlock.java:180` | `createTileEntity/2` | public/instance | `TileEntity` | `World\|int` | — |
| 159 | `block/TargetBlock.java:185` | `onBlockPlacedBy/6` | public/instance | `void` | `World\|int\|int\|int\|EntityLivingBase\|ItemStack` | — |
| 160 | `block/TargetBlock.java:217` | `onNeighborBlockChange/5` | public/instance | `void` | `World\|int\|int\|int\|Block` | setPlacedBy/5@179 ; onPlace/5@229 |
| 161 | `block/TargetBlock.java:251` | `canBlockStay/4` | public/instance | `boolean` | `World\|int\|int\|int` | — |
| 162 | `block/TargetBlock.java:261` | `updateTick/5` | public/instance | `void` | `World\|int\|int\|int\|java.util.Random` | setPlacedBy/5@179 ; onPlace/5@229 |
| 163 | `block/TargetBlock.java:278` | `onBulletHit/4` | public/instance | `boolean` | `World\|MovingObjectPosition\|EntityPlayer\|float` | — |
| 164 | `block/TargetBlock.java:318` | `canProvidePower/0` | public/instance | `boolean` | `` | — |
| 165 | `block/TargetBlock.java:328` | `isProvidingWeakPower/5` | public/instance | `int` | `IBlockAccess\|int\|int\|int\|int` | — |
| 166 | `block/TargetBlock.java:340` | `onBlockAdded/4` | public/instance | `void` | `World\|int\|int\|int` | tick/4@110 ; onProjectileHit/4@118 |
| 167 | `block/TargetBlock.java:356` | `updateNeighbourPower/4` | public/static | `void` | `World\|int\|int\|int` | tick/4@110 ; onProjectileHit/4@118 |
| 168 | `block/TargetBlock.java:361` | `getPickBlock/6` | public/instance | `ItemStack` | `MovingObjectPosition\|World\|int\|int\|int\|EntityPlayer` | — |
| 169 | `block/TargetBlock.java:375` | `getDrops/6` | public/instance | `ArrayList<ItemStack>` | `World\|int\|int\|int\|int\|int` | — |
| 170 | `block/TargetBlock.java:416` | `setBlockBoundsBasedOnState/4` | public/instance | `void` | `IBlockAccess\|int\|int\|int` | tick/4@110 ; onProjectileHit/4@118 |
| 171 | `block/TargetBlock.java:433` | `getSelectedBoundingBoxFromPool/4` | public/instance | `AxisAlignedBB` | `World\|int\|int\|int` | — |
| 172 | `block/TargetBlock.java:442` | `addCollisionBoxesToList/7` | public/instance | `void` | `World\|int\|int\|int\|AxisAlignedBB\|List<AxisAlignedBB>\|Entity` | — |
| 173 | `block/TargetBlock.java:453` | `getCollisionBoundingBoxFromPool/4` | public/instance | `AxisAlignedBB` | `World\|int\|int\|int` | — |
| 174 | `block/TargetBlock.java:462` | `getRenderType/0` | public/instance | `int` | `` | — |
| 175 | `block/TargetBlock.java:468` | `isOpaqueCube/0` | public/instance | `boolean` | `` | — |
| 176 | `block/TargetBlock.java:474` | `renderAsNormalBlock/0` | public/instance | `boolean` | `` | — |
| 177 | `block/TargetBlock.java:479` | `getMobilityFlag/0` | public/instance | `int` | `` | — |
| 178 | `block/TargetBlock.java:485` | `getResetTime/0` | public/static | `int` | `` | — |
| 179 | `block/TargetBlock.java:490` | `hitVector/6` | public/static | `Vec3` | `int\|int\|int\|float\|float\|float` | — |
| 180 | `block/TargetBlock.java:498` | `getTargetSoundVolume/0` | public/static | `float` | `` | — |
| 181 | `block/entity/AmmoStorageBlockEntity.java:116` | `getInventory/0` | public/instance | `IInventory` | `` | — |
| 182 | `block/entity/AmmoStorageBlockEntity.java:135` | `readFromNBT/1` | public/instance | `void` | `NBTTagCompound` | load/1@90 ; saveAdditional/1@96 |
| 183 | `block/entity/AmmoStorageBlockEntity.java:143` | `writeToNBT/1` | public/instance | `void` | `NBTTagCompound` | load/1@90 ; saveAdditional/1@96 |
| 184 | `block/entity/AmmoStorageBlockEntity.java:149` | `shouldRefresh/8` | public/instance | `boolean` | `Block\|Block\|int\|int\|World\|int\|int\|int` | — |
| 185 | `block/entity/GunDisplayCaseBlockEntity.java:215` | `readFromNBT/1` | public/instance | `void` | `NBTTagCompound` | displaySlotChanged/1@76 ; load/1@155 ; saveAdditional/1@186 |
| 186 | `block/entity/GunDisplayCaseBlockEntity.java:221` | `writeToNBT/1` | public/instance | `void` | `NBTTagCompound` | displaySlotChanged/1@76 ; load/1@155 ; saveAdditional/1@186 |
| 187 | `block/entity/GunDisplayCaseBlockEntity.java:276` | `getDescriptionPacket/0` | public/instance | `Packet` | `` | — |
| 188 | `block/entity/GunDisplayCaseBlockEntity.java:283` | `onDataPacket/2` | public/instance | `void` | `NetworkManager\|S35PacketUpdateTileEntity` | — |
| 189 | `block/entity/GunDisplayCaseBlockEntity.java:288` | `shouldRefresh/8` | public/instance | `boolean` | `Block\|Block\|int\|int\|World\|int\|int\|int` | — |
| 190 | `block/entity/GunPegboardBlockEntity.java:265` | `onBlockStateChanged/2` | public/instance | `void` | `int\|int` | setColor/2@62 |
| 191 | `block/entity/GunPegboardBlockEntity.java:327` | `readFromNBT/1` | public/instance | `void` | `NBTTagCompound` | displaySlotChanged/1@107 ; setBlockState/1@208 ; load/1@232 ; saveAdditional/1@281 |
| 192 | `block/entity/GunPegboardBlockEntity.java:333` | `writeToNBT/1` | public/instance | `void` | `NBTTagCompound` | displaySlotChanged/1@107 ; setBlockState/1@208 ; load/1@232 ; saveAdditional/1@281 |
| 193 | `block/entity/GunPegboardBlockEntity.java:421` | `getDescriptionPacket/0` | public/instance | `Packet` | `` | — |
| 194 | `block/entity/GunPegboardBlockEntity.java:430` | `onDataPacket/2` | public/instance | `void` | `NetworkManager\|S35PacketUpdateTileEntity` | setColor/2@62 |
| 195 | `block/entity/GunPegboardBlockEntity.java:435` | `shouldRefresh/8` | public/instance | `boolean` | `Block\|Block\|int\|int\|World\|int\|int\|int` | — |
| 196 | `block/entity/GunPegboardBlockEntity.java:453` | `getFacing/0` | public/instance | `ForgeDirection` | `` | — |
| 197 | `block/entity/GunSmithTableBlockEntity.java:77` | `writeToNBT/1` | public/instance | `void` | `NBTTagCompound` | setId/1@41 ; load/1@73 ; saveAdditional/1@83 |
| 198 | `block/entity/GunSmithTableBlockEntity.java:83` | `readFromNBT/1` | public/instance | `void` | `NBTTagCompound` | setId/1@41 ; load/1@73 ; saveAdditional/1@83 |
| 199 | `block/entity/GunSmithTableBlockEntity.java:104` | `getDescriptionPacket/0` | public/instance | `Packet` | `` | — |
| 200 | `block/entity/GunSmithTableBlockEntity.java:111` | `onDataPacket/2` | public/instance | `void` | `NetworkManager\|S35PacketUpdateTileEntity` | — |
| 201 | `block/entity/GunSmithTableBlockEntity.java:116` | `shouldRefresh/8` | public/instance | `boolean` | `Block\|Block\|int\|int\|World\|int\|int\|int` | — |
| 202 | `block/entity/StatueBlockEntity.java:53` | `updateEntity/0` | public/instance | `void` | `` | dropItem/0@58 |
| 203 | `block/entity/StatueBlockEntity.java:116` | `readFromNBT/1` | public/instance | `void` | `NBTTagCompound` | setGun/1@48 ; load/1@71 ; saveAdditional/1@79 |
| 204 | `block/entity/StatueBlockEntity.java:122` | `writeToNBT/1` | public/instance | `void` | `NBTTagCompound` | setGun/1@48 ; load/1@71 ; saveAdditional/1@79 |
| 205 | `block/entity/StatueBlockEntity.java:140` | `getDescriptionPacket/0` | public/instance | `Packet` | `` | — |
| 206 | `block/entity/StatueBlockEntity.java:147` | `onDataPacket/2` | public/instance | `void` | `NetworkManager\|S35PacketUpdateTileEntity` | — |
| 207 | `block/entity/StatueBlockEntity.java:152` | `shouldRefresh/8` | public/instance | `boolean` | `Block\|Block\|int\|int\|World\|int\|int\|int` | — |
| 208 | `block/entity/StatueBlockEntity.java:170` | `getFacing/0` | public/instance | `ForgeDirection` | `` | — |
| 209 | `block/entity/TargetBlockEntity.java:76` | `updateEntity/0` | public/instance | `void` | `` | refresh/0@116 |
| 210 | `block/entity/TargetBlockEntity.java:115` | `getOutputPower/0` | public/instance | `int` | `` | — |
| 211 | `block/entity/TargetBlockEntity.java:120` | `setOutputPower/1` | public/instance | `void` | `int` | setOwner/1@61 ; load/1@69 ; saveAdditional/1@80 ; setCustomName/1@102 |
| 212 | `block/entity/TargetBlockEntity.java:133` | `hit/6` | public/instance | `void` | `World\|int\|int\|int\|MovingObjectPosition\|boolean` | — |
| 213 | `block/entity/TargetBlockEntity.java:190` | `readFromNBT/1` | public/instance | `void` | `NBTTagCompound` | setOwner/1@61 ; load/1@69 ; saveAdditional/1@80 ; setCustomName/1@102 |
| 214 | `block/entity/TargetBlockEntity.java:196` | `writeToNBT/1` | public/instance | `void` | `NBTTagCompound` | setOwner/1@61 ; load/1@69 ; saveAdditional/1@80 ; setCustomName/1@102 |
| 215 | `block/entity/TargetBlockEntity.java:245` | `getDescriptionPacket/0` | public/instance | `Packet` | `` | — |
| 216 | `block/entity/TargetBlockEntity.java:252` | `onDataPacket/2` | public/instance | `void` | `NetworkManager\|S35PacketUpdateTileEntity` | — |
| 217 | `block/entity/TargetBlockEntity.java:257` | `shouldRefresh/8` | public/instance | `boolean` | `Block\|Block\|int\|int\|World\|int\|int\|int` | — |
| 218 | `block/entity/TargetBlockEntity.java:275` | `getFacing/0` | public/instance | `net.minecraftforge.common.util.ForgeDirection` | `` | — |
| 219 | `client/event/GunDisplayCaseClientInteractionEvent.java:61` | `onClientTick/1` | public/instance | `void` | `TickEvent.ClientTickEvent` | onUseKey/1@19 ; onUseMouse/1@31 |
| 220 | `client/event/RefreshClonePlayerDataEvent.java:57` | `onClientPlayerClone/2` | public/static | `void` | `EntityPlayerSP\|EntityPlayerSP` | — |
| 221 | `client/gui/AmmoStorageScreen.java:51` | `drawGuiContainerBackgroundLayer/3` | protected/instance | `void` | `float\|int\|int` | renderTooltip/3@62 |
| 222 | `client/gui/AmmoStorageScreen.java:97` | `drawScreen/3` | public/instance | `void` | `int\|int\|float` | renderTooltip/3@62 |
| 223 | `client/gui/AmmoStorageScreen.java:174` | `mouseClickMove/4` | protected/instance | `void` | `int\|int\|int\|long` | renderBg/4@28 ; render/4@55 ; renderSizeLabel/4@158 |
| 224 | `client/gui/AmmoStorageScreen.java:181` | `mouseMovedOrUp/3` | protected/instance | `void` | `int\|int\|int` | renderTooltip/3@62 |
| 225 | `client/gui/GunPackProgressScreen.java:42` | `initGui/0` | public/instance | `void` | `` | init/0@22 ; stop/0@69 |
| 226 | `client/gui/GunPackProgressScreen.java:56` | `actionPerformed/1` | protected/instance | `void` | `GuiButton` | progressStartNoAbort/1@47 ; progressStart/1@53 ; progressStage/1@58 ; progressStagePercentage/1@64 |
| 227 | `client/gui/GunPackProgressScreen.java:63` | `drawScreen/3` | public/instance | `void` | `int\|int\|float` | — |
| 228 | `client/gui/GunPackProgressScreen.java:107` | `displayProgressMessage/1` | public/instance | `void` | `String` | progressStartNoAbort/1@47 ; progressStart/1@53 ; progressStage/1@58 ; progressStagePercentage/1@64 |
| 229 | `client/gui/GunPackProgressScreen.java:117` | `resetProgressAndMessage/1` | public/instance | `void` | `String` | progressStartNoAbort/1@47 ; progressStart/1@53 ; progressStage/1@58 ; progressStagePercentage/1@64 |
| 230 | `client/gui/GunPackProgressScreen.java:123` | `resetProgresAndWorkingMessage/1` | public/instance | `void` | `String` | progressStartNoAbort/1@47 ; progressStart/1@53 ; progressStage/1@58 ; progressStagePercentage/1@64 |
| 231 | `client/gui/GunPackProgressScreen.java:129` | `setLoadingProgress/1` | public/instance | `void` | `int` | progressStartNoAbort/1@47 ; progressStart/1@53 ; progressStage/1@58 ; progressStagePercentage/1@64 |
| 232 | `client/gui/GunPackProgressScreen.java:135` | `func_146586_a/0` | public/instance | `void` | `` | init/0@22 ; stop/0@69 |
| 233 | `client/gui/GunRefitScreen.java:147` | `initGui/0` | public/instance | `void` | `` | init/0@96 ; addInventoryAttachmentButtons/0@153 ; addAttachmentTypeButtons/0@241 ; onClose/0@422 ; switchHideButton/0@436 |
| 234 | `client/gui/GunRefitScreen.java:193` | `drawScreen/3` | public/instance | `void` | `int\|int\|float` | — |
| 235 | `client/gui/GunRefitScreen.java:232` | `doesGuiPauseGame/0` | public/instance | `boolean` | `` | isPauseScreen/0@148 |
| 236 | `client/gui/GunRefitScreen.java:591` | `onGuiClosed/0` | public/instance | `void` | `` | init/0@96 ; addInventoryAttachmentButtons/0@153 ; addAttachmentTypeButtons/0@241 ; onClose/0@422 ; switchHideButton/0@436 |
| 237 | `client/gui/GunSmithTableScreen.java:450` | `initGui/0` | public/instance | `void` | `` | classifyRecipes/0@107 ; updateSelectedRecipeAfterFiltering/0@274 ; applyRequestedRecipeSelection/0@287 ; updateIngredientCount/0@353 ; init/0@360 ; addEmptyByHandFilterHint/0@398 ; addCraftButton/0@416 ; addUrlButton/0@441 ; addIndexButtons/0@474 ; addTypeButtons/0@500 ; addIndexPageButtons/0@529 ; addTypePageButtons/0@550 ; addScaleButtons/0@569 |
| 238 | `client/gui/GunSmithTableScreen.java:757` | `handleMouseInput/0` | public/instance | `void` | `` | classifyRecipes/0@107 ; updateSelectedRecipeAfterFiltering/0@274 ; applyRequestedRecipeSelection/0@287 ; updateIngredientCount/0@353 ; init/0@360 ; addEmptyByHandFilterHint/0@398 ; addCraftButton/0@416 ; addUrlButton/0@441 ; addIndexButtons/0@474 ; addTypeButtons/0@500 ; addIndexPageButtons/0@529 ; addTypePageButtons/0@550 ; addScaleButtons/0@569 |
| 239 | `client/gui/GunSmithTableScreen.java:790` | `updateScreen/0` | public/instance | `void` | `` | classifyRecipes/0@107 ; updateSelectedRecipeAfterFiltering/0@274 ; applyRequestedRecipeSelection/0@287 ; updateIngredientCount/0@353 ; init/0@360 ; addEmptyByHandFilterHint/0@398 ; addCraftButton/0@416 ; addUrlButton/0@441 ; addIndexButtons/0@474 ; addTypeButtons/0@500 ; addIndexPageButtons/0@529 ; addTypePageButtons/0@550 ; addScaleButtons/0@569 |
| 240 | `client/gui/GunSmithTableScreen.java:798` | `keyTyped/2` | protected/instance | `void` | `char\|int` | renderPackInfo/2@630 |
| 241 | `client/gui/GunSmithTableScreen.java:806` | `drawScreen/3` | public/instance | `void` | `int\|int\|float` | renderIngredientTooltip/3@749 ; renderLabels/3@842 |
| 242 | `client/gui/GunSmithTableScreen.java:1167` | `drawGuiContainerForegroundLayer/2` | protected/instance | `void` | `int\|int` | renderPackInfo/2@630 |
| 243 | `client/gui/GunSmithTableScreen.java:1170` | `drawGuiContainerBackgroundLayer/3` | protected/instance | `void` | `float\|int\|int` | renderIngredientTooltip/3@749 ; renderLabels/3@842 |
| 244 | `client/gui/GunSmithTableScreen.java:1182` | `doesGuiPauseGame/0` | public/instance | `boolean` | `` | shouldFilterByMainHand/0@267 ; isPauseScreen/0@858 |
| 245 | `client/gui/GunSmithTableScreen.java:1213` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | renderIngredientTooltip/3@749 ; renderLabels/3@842 |
| 246 | `client/gui/GunSmithTableScreen.java:1228` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | mouseScrolled/3@581 |
| 247 | `client/gui/compat/ClothConfigScreen.java:50` | `isClothConfigLoaded/0` | public/static | `boolean` | `` | — |
| 248 | `client/gui/compat/ClothConfigScreen.java:75` | `initGui/0` | public/instance | `void` | `` | registerNoClothConfigPage/0@27 ; init/0@34 |
| 249 | `client/gui/compat/ClothConfigScreen.java:92` | `mousePressed/3` | public/instance | `boolean` | `net.minecraft.client.Minecraft\|int\|int` | — |
| 250 | `client/gui/compat/ClothConfigScreen.java:104` | `mousePressed/3` | public/instance | `boolean` | `net.minecraft.client.Minecraft\|int\|int` | — |
| 251 | `client/gui/compat/ClothConfigScreen.java:115` | `drawScreen/3` | public/instance | `void` | `int\|int\|float` | — |
| 252 | `client/gui/compat/ClothConfigScreen.java:141` | `getParentScreen/0` | public/instance | `GuiScreen` | `` | — |
| 253 | `client/gui/components/CreativePackFilters.java:115` | `itemList/0` | package/instance | `List<ItemStack>` | `` | — |
| 254 | `client/gui/components/CreativePackFilters.java:119` | `scrollTo/1` | package/instance | `void` | `float` | select/1@168 ; showItems/1@184 ; scrollPacks/1@195 ; setOption/1@241 |
| 255 | `client/gui/components/CreativePackFilters.java:592` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | mouseScrolled/3@211 |
| 256 | `client/gui/components/CreativePackFilters.java:619` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | mouseScrolled/3@211 |
| 257 | `client/gui/components/CreativePackFilters.java:628` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 258 | `client/gui/components/FlatColorButton.java:66` | `isSelected/0` | public/instance | `boolean` | `` | — |
| 259 | `client/gui/components/FlatColorButton.java:70` | `isHovered/0` | public/instance | `boolean` | `` | — |
| 260 | `client/gui/components/FlatColorButton.java:91` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 261 | `client/gui/components/FlatColorButton.java:163` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | — |
| 262 | `client/gui/components/GunPackList.java:163` | `updateSize/4` | public/instance | `void` | `int\|int\|int\|int` | render/4@139 ; renderWidget/4@244 |
| 263 | `client/gui/components/GunPackList.java:168` | `setLeftPos/1` | public/instance | `void` | `int` | setByHandSelected/1@99 ; updateWidgetNarration/1@232 |
| 264 | `client/gui/components/GunPackList.java:173` | `keyTyped/2` | public/instance | `boolean` | `char\|int` | — |
| 265 | `client/gui/components/GunPackList.java:194` | `updateCursorCounter/0` | public/instance | `void` | `` | onPress/0@55 ; onPress/0@65 ; onPress/0@79 ; clearFilters/0@103 ; updateSelectedNamespaces/0@124 ; onPress/0@224 |
| 266 | `client/gui/components/GunPackList.java:198` | `getSize/0` | protected/instance | `int` | `` | getScrollbarPosition/0@135 ; getRowLeft/0@166 ; getRowWidth/0@170 |
| 267 | `client/gui/components/GunPackList.java:207` | `elementClicked/4` | protected/instance | `void` | `int\|boolean\|int\|int` | render/4@139 ; renderWidget/4@244 |
| 268 | `client/gui/components/GunPackList.java:217` | `isSelected/1` | protected/instance | `boolean` | `int` | — |
| 269 | `client/gui/components/GunPackList.java:222` | `drawContainerBackground/1` | protected/instance | `void` | `Tessellator` | setByHandSelected/1@99 ; updateWidgetNarration/1@232 |
| 270 | `client/gui/components/GunPackList.java:227` | `drawBackground/0` | protected/instance | `void` | `` | onPress/0@55 ; onPress/0@65 ; onPress/0@79 ; clearFilters/0@103 ; updateSelectedNamespaces/0@124 ; onPress/0@224 |
| 271 | `client/gui/components/GunPackList.java:232` | `drawSlot/7` | protected/instance | `void` | `int\|int\|int\|int\|Tessellator\|int\|int` | — |
| 272 | `client/gui/components/GunPackList.java:257` | `getListWidth/0` | public/instance | `int` | `` | getScrollbarPosition/0@135 ; getRowLeft/0@166 ; getRowWidth/0@170 |
| 273 | `client/gui/components/GunPackList.java:262` | `getScrollBarX/0` | protected/instance | `int` | `` | getScrollbarPosition/0@135 ; getRowLeft/0@166 ; getRowWidth/0@170 |
| 274 | `client/gui/components/GunPackList.java:267` | `drawScreen/3` | public/instance | `void` | `int\|int\|float` | — |
| 275 | `client/gui/components/GunPackList.java:335` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 276 | `client/gui/components/GunPackList.java:364` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | — |
| 277 | `client/gui/components/refit/GunAttachmentSlot.java:55` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 278 | `client/gui/components/refit/GunAttachmentSlot.java:136` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | — |
| 279 | `client/gui/components/refit/HSVSliderGroup.java:121` | `getValue/0` | public/instance | `double` | `` | — |
| 280 | `client/gui/components/refit/HSVSliderGroup.java:125` | `setValue/1` | public/instance | `void` | `double` | — |
| 281 | `client/gui/components/refit/HSVSliderGroup.java:134` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 282 | `client/gui/components/refit/HSVSliderGroup.java:155` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | — |
| 283 | `client/gui/components/refit/HSVSliderGroup.java:167` | `mouseReleased/2` | public/instance | `void` | `int\|int` | — |
| 284 | `client/gui/components/refit/InventoryAttachmentSlot.java:57` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 285 | `client/gui/components/refit/InventoryAttachmentSlot.java:84` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | — |
| 286 | `client/gui/components/refit/RefitTurnPageButton.java:27` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 287 | `client/gui/components/refit/RefitTurnPageButton.java:66` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | — |
| 288 | `client/gui/components/refit/RefitUnloadButton.java:25` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 289 | `client/gui/components/refit/RefitUnloadButton.java:51` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | — |
| 290 | `client/gui/components/smith/ResultButton.java:31` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 291 | `client/gui/components/smith/ResultButton.java:88` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | — |
| 292 | `client/gui/components/smith/TypeButton.java:30` | `drawButton/3` | public/instance | `void` | `Minecraft\|int\|int` | — |
| 293 | `client/gui/components/smith/TypeButton.java:53` | `mousePressed/3` | public/instance | `boolean` | `Minecraft\|int\|int` | — |
| 294 | `client/gui/overlay/DisplayCaseLockHintOverlay.java:59` | `render/3` | public/instance | `void` | `GuiGraphics\|int\|int` | — |
| 295 | `client/gui/overlay/GunHudOverlay.java:67` | `render/3` | public/instance | `void` | `GuiGraphics\|int\|int` | — |
| 296 | `client/gui/overlay/HeatBarOverlay.java:40` | `render/3` | public/instance | `void` | `GuiGraphics\|int\|int` | — |
| 297 | `client/gui/overlay/InteractKeyTextOverlay.java:45` | `render/3` | public/instance | `void` | `GuiGraphics\|int\|int` | — |
| 298 | `client/gui/overlay/KillAmountOverlay.java:27` | `render/3` | public/instance | `void` | `GuiGraphics\|int\|int` | — |
| 299 | `client/gui/toast/GunLevelUpToast.java:44` | `getTitle/0` | public/instance | `IChatComponent` | `` | — |
| 300 | `client/gui/toast/GunLevelUpToast.java:48` | `getSubTitle/0` | public/instance | `IChatComponent` | `` | — |
| 301 | `client/gui/toast/GunLevelUpToast.java:52` | `getIcon/0` | public/instance | `ItemStack` | `` | — |
| 302 | `client/gui/toast/GunLevelUpToast.java:56` | `show/0` | public/instance | `void` | `` | — |
| 303 | `client/gui/toast/GunLevelUpToast.java:76` | `render/2` | public/instance | `boolean` | `GuiGraphics\|long` | — |
| 304 | `client/init/ClientSetupEvent.java:115` | `onClientSetup/0` | public/static | `void` | `` | — |
| 305 | `client/init/ClientSetupEvent.java:398` | `onRenderWorldLast/1` | public/instance | `void` | `RenderWorldLastEvent` | onRegisterShaders/1@32 ; onClientSetup/1@36 ; onClientSetup/1@42 ; onRegisterGuiOverlays/1@52 ; onClientSetup/1@63 ; onClientResourceReload/1@93 |
| 306 | `client/init/ClientSetupEvent.java:414` | `onRenderOverlay/1` | public/instance | `void` | `RenderGameOverlayEvent.Post` | onRegisterShaders/1@32 ; onClientSetup/1@36 ; onClientSetup/1@42 ; onRegisterGuiOverlays/1@52 ; onClientSetup/1@63 ; onClientResourceReload/1@93 |
| 307 | `client/init/ClientSetupEvent.java:453` | `render/3` | package/instance | `void` | `GuiGraphics\|int\|int` | — |
| 308 | `client/init/GunPegboardColors.java:42` | `setup/0` | public/static | `void` | `` | — |
| 309 | `client/init/ModContainerScreen.java:37` | `clientSetup/0` | public/static | `void` | `` | — |
| 310 | `client/init/ModEntitiesRender.java:49` | `register/0` | public/static | `void` | `` | — |
| 311 | `client/init/ParticleFactoryRegistry.java:37` | `register/0` | public/static | `void` | `` | — |
| 312 | `client/input/GunInputHandler.java:102` | `registerKeyMappings/0` | public/static | `void` | `` | — |
| 313 | `client/input/GunInputHandler.java:184` | `isKeyDown/2` | public/static | `boolean` | `KeyBinding\|int` | — |
| 314 | `client/input/GunInputHandler.java:200` | `isKeyDown/1` | public/static | `boolean` | `KeyBinding` | closeRefitScreen/1@93 |
| 315 | `client/input/GunInputHandler.java:205` | `isAltDown/0` | public/static | `boolean` | `` | — |
| 316 | `client/input/GunInputHandler.java:210` | `onClientTick/1` | public/instance | `void` | `TickEvent.ClientTickEvent` | registerKeyMappings/1@36 ; onKeyPress/1@43 ; onMousePress/1@57 ; onMousePressPre/1@68 ; onPress/1@77 ; onRelease/1@108 |
| 317 | `client/input/ShootKey.java:95` | `isDown/0` | public/static | `boolean` | `` | — |
| 318 | `client/model/functional/BeamRenderer.java:228` | `applyGlState/0` | public/static | `void` | `` | — |
| 319 | `client/model/functional/BeamRenderer.java:240` | `restoreGlState/0` | public/static | `void` | `` | — |
| 320 | `client/particle/BulletHoleParticle.java:208` | `onUpdate/0` | public/instance | `void` | `` | tick/0@137 |
| 321 | `client/particle/BulletHoleParticle.java:230` | `renderParticle/7` | public/instance | `void` | `Tessellator\|float\|float\|float\|float\|float\|float` | — |
| 322 | `client/particle/BulletHoleParticle.java:291` | `getFXLayer/0` | public/instance | `int` | `` | — |
| 323 | `client/particle/BulletHoleParticle.java:364` | `cameraPosition/1` | package/static | `double[]` | `float` | — |
| 324 | `client/particle/BulletHoleParticle.java:397` | `createParticle/5` | public/instance | `BulletHoleParticle` | `BulletHoleOption\|World\|double\|double\|double` | — |
| 325 | `client/particle/HitBlockEffectHandler.java:39` | `handle/12` | public/static | `void` | `World\|double\|double\|double\|ForgeDirection\|int\|int\|int\|String\|String\|String\|boolean` | — |
| 326 | `client/particle/HitSurfaceParticleSpawner.java:82` | `spawn/12` | public/static | `void` | `World\|double\|double\|double\|ForgeDirection\|int\|int\|int\|String\|String\|String\|boolean` | — |
| 327 | `client/renderer/block/DisplayGunGeometry.java:103` | `capture/5` | public/static | `boolean` | `ExternalGunAppearance.Snapshot\|ItemDisplayContext\|boolean\|MultiBufferSource\|int` | — |
| 328 | `client/renderer/block/DisplayGunGeometry.java:150` | `renderAttachments/8` | public/static | `void` | `DisplayGunModelInstance\|PoseStack\|boolean\|IGun\|ItemStack\|EnumMap<AttachmentType,ClientAttachmentIndex>\|MultiBufferSource\|int` | — |
| 329 | `client/renderer/block/DisplayGunGeometry.java:248` | `renderModel/6` | public/static | `void` | `ExternalGunModel\|DisplayGunModelInstance\|PoseStack\|ResourceLocation\|MultiBufferSource\|int` | — |
| 330 | `client/renderer/block/GunDisplayCaseRenderer.java:90` | `renderTileEntityAt/5` | public/instance | `void` | `TileEntity\|double\|double\|double\|float` | renderPlacementPreview/5@54 |
| 331 | `client/renderer/block/GunPegboardRenderer.java:85` | `renderTileEntityAt/5` | public/instance | `void` | `TileEntity\|double\|double\|double\|float` | renderPlacementPreview/5@56 |
| 332 | `client/renderer/block/GunSmithTableRenderer.java:108` | `renderTileEntityAt/5` | public/instance | `void` | `TileEntity\|double\|double\|double\|float` | — |
| 333 | `client/renderer/block/StatueRenderer.java:84` | `renderTileEntityAt/5` | public/instance | `void` | `TileEntity\|double\|double\|double\|float` | — |
| 334 | `client/renderer/block/TargetRenderer.java:92` | `renderTileEntityAt/5` | public/instance | `void` | `TileEntity\|double\|double\|double\|float` | — |
| 335 | `client/renderer/entity/EntityBulletRenderer.java:209` | `doRender/6` | public/instance | `void` | `Entity\|double\|double\|double\|float\|float` | render/6@43 |
| 336 | `client/renderer/entity/EntityBulletRenderer.java:264` | `getEntityTexture/1` | protected/instance | `ResourceLocation` | `Entity` | getTextureLocation/1@147 |
| 337 | `client/renderer/entity/TargetMinecartRenderer.java:62` | `getEntityTexture/1` | protected/instance | `ResourceLocation` | `Entity` | getTextureLocation/1@40 |
| 338 | `client/renderer/entity/TargetMinecartRenderer.java:67` | `func_147910_a/4` | protected/instance | `void` | `EntityMinecart\|float\|net.minecraft.block.Block\|int` | — |
| 339 | `client/renderer/entity/TargetMinecartRenderer.java:92` | `renderMinecartContents/7` | public/instance | `void` | `TargetMinecart\|float\|net.minecraft.block.Block\|int\|PoseStack\|MultiBufferSource\|int` | — |
| 340 | `client/renderer/item/AmmoItemRenderer.java:32` | `handleRenderType/2` | public/instance | `boolean` | `ItemStack\|ItemRenderType` | — |
| 341 | `client/renderer/item/AmmoItemRenderer.java:41` | `shouldUseRenderHelper/3` | public/instance | `boolean` | `ItemRenderType\|ItemStack\|ItemRendererHelper` | — |
| 342 | `client/renderer/item/AmmoItemRenderer.java:46` | `renderItem/3` | public/instance | `void` | `ItemRenderType\|ItemStack\|Object...` | applyPositioningNodeTransform/3@34 ; applyScaleTransform/3@108 |
| 343 | `client/renderer/item/AnimateGeoItemRenderer.java:60` | `handleRenderType/2` | public/instance | `boolean` | `ItemStack\|ItemRenderType` | isSameItem/2@388 |
| 344 | `client/renderer/item/AnimateGeoItemRenderer.java:69` | `shouldUseRenderHelper/3` | public/instance | `boolean` | `ItemRenderType\|ItemStack\|ItemRendererHelper` | — |
| 345 | `client/renderer/item/AnimateGeoItemRenderer.java:74` | `renderItem/3` | public/instance | `void` | `ItemRenderType\|ItemStack\|Object...` | tryInit/3@98 ; applyLevelCameraAnimation/3@162 ; applyLevelCameraAnimation/3@169 ; applyItemInHandCameraAnimation/3@186 ; applyItemInHandCameraAnimation/3@193 ; doExtraTransforms/3@203 ; applyFirstPersonPositioningTransform/3@300 |
| 346 | `client/renderer/item/AttachmentItemRenderer.java:32` | `handleRenderType/2` | public/instance | `boolean` | `ItemStack\|ItemRenderType` | — |
| 347 | `client/renderer/item/AttachmentItemRenderer.java:41` | `shouldUseRenderHelper/3` | public/instance | `boolean` | `ItemRenderType\|ItemStack\|ItemRendererHelper` | — |
| 348 | `client/renderer/item/AttachmentItemRenderer.java:46` | `renderItem/3` | public/instance | `void` | `ItemRenderType\|ItemStack\|Object...` | — |
| 349 | `client/renderer/item/GunSmithTableItemRenderer.java:26` | `handleRenderType/2` | public/instance | `boolean` | `ItemStack\|ItemRenderType` | — |
| 350 | `client/renderer/item/GunSmithTableItemRenderer.java:35` | `shouldUseRenderHelper/3` | public/instance | `boolean` | `ItemRenderType\|ItemStack\|ItemRendererHelper` | — |
| 351 | `client/renderer/item/GunSmithTableItemRenderer.java:40` | `renderItem/3` | public/instance | `void` | `ItemRenderType\|ItemStack\|Object...` | — |
| 352 | `client/renderer/scope/ScopeLensContour.java:44` | `init/0` | package/static | `void` | `` | drawQuad/0@126 ; close/0@136 |
| 353 | `client/renderer/scope/ScopeLensRenderer.java:185` | `init/0` | public/static | `void` | `` | beginFrame/0@58 ; render/0@159 ; close/0@284 ; discard/0@337 ; endVertex/0@414 ; unsetDefaultColor/0@416 ; close/0@474 |
| 354 | `client/renderer/scope/ScopeLensRenderer.java:214` | `isCoreProfile/0` | public/static | `boolean` | `` | isValid/0@386 |
| 355 | `client/renderer/scope/ScopeLensRenderer.java:279` | `onRenderTick/1` | public/instance | `void` | `TickEvent.RenderTickEvent` | registerShader/1@44 |
| 356 | `client/renderer/scope/ScopeLensRenderer.java:436` | `drainDeletes/0` | public/static | `void` | `` | beginFrame/0@58 ; render/0@159 ; close/0@284 ; discard/0@337 ; endVertex/0@414 ; unsetDefaultColor/0@416 ; close/0@474 |
| 357 | `client/renderer/scope/ScopeLensRenderer.java:666` | `centerU/0` | package/instance | `float` | `` | — |
| 358 | `client/renderer/scope/ScopeLensRenderer.java:670` | `centerV/0` | package/instance | `float` | `` | — |
| 359 | `client/renderer/scope/ScopeLensRenderer.java:674` | `radiusU/0` | package/instance | `float` | `` | — |
| 360 | `client/renderer/scope/ScopeLensRenderer.java:678` | `radiusV/0` | package/instance | `float` | `` | — |
| 361 | `client/renderer/scope/ScopeThermalRenderer.java:188` | `init/0` | public/static | `void` | `` | beginFrame/0@65 ; close/0@216 ; unsetDefaultColor/0@290 ; endVertex/0@295 |
| 362 | `client/renderer/scope/ScopeThermalRenderer.java:267` | `beginEntity/1` | public/static | `void` | `EntityLivingBase` | registerShader/1@54 ; onWorldStage/1@116 |
| 363 | `client/renderer/scope/ScopeThermalRenderer.java:325` | `endEntity/0` | public/static | `void` | `` | beginFrame/0@65 ; close/0@216 ; unsetDefaultColor/0@290 ; endVertex/0@295 |
| 364 | `client/renderer/scope/ScopeThermalRenderer.java:400` | `render/0` | public/static | `void` | `` | beginFrame/0@65 ; close/0@216 ; unsetDefaultColor/0@290 ; endVertex/0@295 |
| 365 | `client/renderer/scope/ScopeThermalRenderer.java:649` | `drainDeletes/0` | public/static | `void` | `` | beginFrame/0@65 ; close/0@216 ; unsetDefaultColor/0@290 ; endVertex/0@295 |
| 366 | `client/resource/ClientAssetsManager.java:345` | `serialize/3` | public/instance | `JsonElement` | `ResourceLocation\|Type\|JsonSerializationContext` | — |
| 367 | `client/resource/ClientAssetsManager.java:350` | `deserialize/3` | public/instance | `ResourceLocation` | `JsonElement\|Type\|JsonDeserializationContext` | — |
| 368 | `client/resource/manager/DisplayManager.java:37` | `apply/1` | protected/instance | `void` | `Map<ResourceLocation,JsonElement>` | — |
| 369 | `client/resource/manager/PackInfoManager.java:51` | `reload/0` | public/instance | `void` | `` | — |
| 370 | `client/resource/pojo/display/IDisplay.java:15` | `idToFile/1` | package/static | `ResourceLocation` | `ResourceLocation` | — |
| 371 | `client/sound/EntityTrackingGunSoundInstance.java:52` | `isDonePlaying/0` | public/instance | `boolean` | `` | canPlaySound/0@21 ; isStopped/0@27 |
| 372 | `client/sound/EntityTrackingGunSoundInstance.java:57` | `update/0` | public/instance | `void` | `` | tick/0@32 ; setStop/0@44 |
| 373 | `client/sound/GunSoundInstance.java:142` | `resolve/0` | public/instance | `void` | `` | setStop/0@47 |
| 374 | `client/sound/GunSoundInstance.java:271` | `func_148721_a/0` | public/instance | `int` | `` | — |
| 375 | `client/sound/GunSoundInstance.java:276` | `func_148720_g/0` | public/instance | `SoundPoolEntry` | `` | — |
| 376 | `client/sound/SoundPlayManager.java:849` | `equals/1` | public/instance | `boolean` | `Object` | isLocalPlayer/1@371 |
| 377 | `client/sound/SoundPlayManager.java:860` | `hashCode/0` | public/instance | `int` | `` | — |
| 378 | `client/tooltip/ClientAmmoBoxTooltip.java:73` | `renderText/3` | public/instance | `void` | `FontRenderer\|int\|int` | — |
| 379 | `client/tooltip/ClientAmmoBoxTooltip.java:95` | `appendHoverText/1` | public/instance | `void` | `List<IChatComponent>` | — |
| 380 | `client/tooltip/ClientAmmoBoxTooltip.java:115` | `getLegacyTooltipLines/0` | public/instance | `List<String>` | `` | — |
| 381 | `client/tooltip/ClientAmmoStorageTooltip.java:75` | `renderText/3` | public/instance | `void` | `FontRenderer\|int\|int` | — |
| 382 | `client/tooltip/ClientAmmoStorageTooltip.java:110` | `appendHoverText/1` | public/instance | `void` | `List<IChatComponent>` | — |
| 383 | `client/tooltip/ClientAmmoStorageTooltip.java:137` | `getLegacyTooltipLines/0` | public/instance | `List<String>` | `` | — |
| 384 | `client/tooltip/ClientAttachmentItemTooltip.java:152` | `renderText/3` | public/instance | `void` | `FontRenderer\|int\|int` | — |
| 385 | `client/tooltip/ClientAttachmentItemTooltip.java:200` | `appendHoverText/1` | public/instance | `void` | `List<IChatComponent>` | addText/1@157 |
| 386 | `client/tooltip/ClientAttachmentItemTooltip.java:227` | `getLegacyTooltipLines/0` | public/instance | `List<String>` | `` | — |
| 387 | `client/tooltip/ClientBlockItemTooltip.java:87` | `renderText/3` | public/instance | `void` | `FontRenderer\|int\|int` | — |
| 388 | `client/tooltip/ClientBlockItemTooltip.java:114` | `appendHoverText/1` | public/instance | `void` | `List<IChatComponent>` | — |
| 389 | `client/tooltip/ClientBlockItemTooltip.java:133` | `getLegacyTooltipLines/0` | public/instance | `List<String>` | `` | — |
| 390 | `client/tooltip/ClientGunTooltip.java:355` | `renderText/3` | public/instance | `void` | `FontRenderer\|int\|int` | — |
| 391 | `client/tooltip/ClientGunTooltip.java:455` | `appendHoverText/1` | public/instance | `void` | `List<IChatComponent>` | — |
| 392 | `client/tooltip/ClientGunTooltip.java:497` | `getLegacyTooltipLines/0` | public/instance | `List<String>` | `` | — |
| 393 | `command/RootCommand.java:57` | `getUsageString/0` | public/static | `String` | `` | — |
| 394 | `command/RootCommand.java:61` | `getCommandName/0` | public/instance | `String` | `` | — |
| 395 | `command/RootCommand.java:66` | `getCommandUsage/1` | public/instance | `String` | `ICommandSender` | — |
| 396 | `command/RootCommand.java:71` | `getRequiredPermissionLevel/0` | public/instance | `int` | `` | — |
| 397 | `command/RootCommand.java:76` | `processCommand/2` | public/instance | `void` | `ICommandSender\|String[]` | — |
| 398 | `command/RootCommand.java:106` | `addTabCompletionOptions/2` | public/instance | `List<String>` | `ICommandSender\|String[]` | — |
| 399 | `command/sub/AttachmentLockCommand.java:60` | `processCommand/2` | public/static | `int` | `ICommandSender\|String[]` | — |
| 400 | `command/sub/AttachmentLockCommand.java:77` | `getUsage/0` | public/static | `String` | `` | — |
| 401 | `command/sub/ConfigCommand.java:58` | `processCommand/2` | public/static | `int` | `ICommandSender\|String[]` | — |
| 402 | `command/sub/ConfigCommand.java:84` | `getUsage/0` | public/static | `String` | `` | — |
| 403 | `command/sub/ConfigCommand.java:88` | `getKeyNames/0` | public/static | `String[]` | `` | — |
| 404 | `command/sub/ConfigCommand.java:113` | `byName/1` | public/static | `ConfigKey` | `String` | — |
| 405 | `command/sub/ConvertCommand.java:53` | `setConverter/1` | public/static | `void` | `Consumer<ICommandSender>` | — |
| 406 | `command/sub/ConvertCommand.java:65` | `processCommand/2` | public/static | `int` | `ICommandSender\|String[]` | — |
| 407 | `command/sub/ConvertCommand.java:83` | `getUsage/0` | public/static | `String` | `` | — |
| 408 | `command/sub/DebugCommand.java:63` | `processCommand/2` | public/static | `int` | `ICommandSender\|String[]` | — |
| 409 | `command/sub/DebugCommand.java:88` | `getUsage/0` | public/static | `String` | `` | — |
| 410 | `command/sub/DummyAmmoCommand.java:56` | `processCommand/2` | public/static | `int` | `ICommandSender\|String[]` | — |
| 411 | `command/sub/DummyAmmoCommand.java:73` | `getUsage/0` | public/static | `String` | `` | — |
| 412 | `command/sub/HideTooltipPartCommand.java:64` | `processCommand/2` | public/static | `int` | `ICommandSender\|String[]` | — |
| 413 | `command/sub/HideTooltipPartCommand.java:81` | `getUsage/0` | public/static | `String` | `` | — |
| 414 | `command/sub/OverwriteCommand.java:53` | `processCommand/2` | public/static | `int` | `ICommandSender\|String[]` | — |
| 415 | `command/sub/OverwriteCommand.java:69` | `getUsage/0` | public/static | `String` | `` | — |
| 416 | `command/sub/PegboardStressCommand.java:112` | `processCommand/2` | public/static | `int` | `ICommandSender\|String[]` | — |
| 417 | `command/sub/PegboardStressCommand.java:132` | `getUsage/0` | public/static | `String` | `` | — |
| 418 | `command/sub/ReloadCommand.java:54` | `setClientReloader/1` | public/static | `void` | `Runnable` | — |
| 419 | `command/sub/ReloadCommand.java:73` | `processCommand/2` | public/static | `int` | `ICommandSender\|String[]` | — |
| 420 | `compat/jei/GunModPlugin.java:57` | `isAvailable/0` | public/static | `boolean` | `` | — |
| 421 | `compat/jei/GunModPlugin.java:62` | `init/0` | public/static | `void` | `` | — |
| 422 | `compat/kubejs/TimelessKubeJSPlugin.java:68` | `postKubeJSEvent/1` | public/static | `boolean` | `Event` | — |
| 423 | `compat/oculus/OculusCompat.java:149` | `isOculusInstalled/0` | public/static | `boolean` | `` | isRenderShadow/0@33 ; isUsingRenderPack/0@40 |
| 424 | `compat/optifine/OptifineCompat.java:69` | `isShadersEnabled/0` | public/static | `boolean` | `` | isOptifineInstalled/0@4 |
| 425 | `compat/optifine/OptifineCompat.java:83` | `isShaderPackLoaded/0` | public/static | `boolean` | `` | isOptifineInstalled/0@4 |
| 426 | `config/ClientConfig.java:27` | `init/1` | public/static | `TaczConfigSpec` | `File` | — |
| 427 | `config/CommonConfig.java:24` | `init/1` | public/static | `TaczConfigSpec` | `File` | — |
| 428 | `config/PreLoadConfig.java:74` | `isLoaded/0` | public/static | `boolean` | `` | — |
| 429 | `config/PreLoadConfig.java:78` | `getFile/0` | public/static | `File` | `` | — |
| 430 | `config/ServerConfig.java:27` | `init/1` | public/static | `TaczConfigSpec` | `File` | — |
| 431 | `config/ServerConfig.java:35` | `isLoaded/0` | public/static | `boolean` | `` | — |
| 432 | `config/sync/SyncConfig.java:188` | `applyFromServer/1` | public/static | `void` | `com.tacz.guns.network.message.ServerMessageSyncConfig` | init/1@39 ; interactKey/1@46 ; gunPegboard/1@64 ; baseMultiplier/1@76 ; misc/1@94 |
| 433 | `config/util/AmmoBlockConfigRead.java:65` | `canPassThrough/2` | public/static | `boolean` | `Block\|int` | — |
| 434 | `config/util/AmmoBlockConfigRead.java:69` | `canDestroy/2` | public/static | `boolean` | `Block\|int` | — |
| 435 | `config/util/InteractKeyConfigRead.java:41` | `canInteractBlock/2` | public/static | `boolean` | `Block\|int` | — |
| 436 | `crafting/AmmoStorageUpgradeRecipe.java:59` | `createRecipe/1` | public/static | `IRecipe` | `String` | — |
| 437 | `crafting/AmmoStorageUpgradeRecipe.java:106` | `getCraftingResult/1` | public/instance | `ItemStack` | `InventoryCrafting` | getResultItem/1@75 |
| 438 | `crafting/AmmoStorageUpgradeRecipe.java:133` | `getRecipeSize/0` | public/instance | `int` | `` | getRecipeWidth/0@85 ; getRecipeHeight/0@90 |
| 439 | `crafting/AmmoStorageUpgradeRecipe.java:138` | `getRecipeOutput/0` | public/instance | `ItemStack` | `` | — |
| 440 | `crafting/GunPegboardDyeRecipe.java:74` | `getCraftingResult/1` | public/instance | `ItemStack` | `InventoryCrafting` | — |
| 441 | `crafting/GunPegboardDyeRecipe.java:154` | `getRecipeSize/0` | public/instance | `int` | `` | — |
| 442 | `crafting/GunPegboardDyeRecipe.java:159` | `getRecipeOutput/0` | public/instance | `ItemStack` | `` | — |
| 443 | `crafting/GunSmithTableIngredient.java:87` | `fromJson/2` | public/static | `GunSmithTableIngredient` | `JsonElement\|int` | — |
| 444 | `crafting/GunSmithTableIngredient.java:116` | `of/2` | public/static | `GunSmithTableIngredient` | `Item\|int` | — |
| 445 | `crafting/GunSmithTableIngredient.java:134` | `getItem/0` | public/instance | `ItemStack` | `` | — |
| 446 | `crafting/GunSmithTableIngredient.java:140` | `getTag/0` | public/instance | `String` | `` | — |
| 447 | `crafting/GunSmithTableIngredient.java:150` | `test/1` | public/instance | `boolean` | `ItemStack` | — |
| 448 | `crafting/GunSmithTableIngredient.java:166` | `getMatchingStacks/0` | public/instance | `List<ItemStack>` | `` | — |
| 449 | `crafting/GunSmithTableIngredient.java:182` | `toOreDictionaryName/1` | public/static | `String` | `String` | — |
| 450 | `crafting/GunSmithTableIngredient.java:321` | `describeCandidates/0` | public/instance | `List<String>` | `` | — |
| 451 | `crafting/GunSmithTableIngredient.java:343` | `toString/0` | public/instance | `String` | `` | — |
| 452 | `crafting/GunSmithTableRecipe.java:96` | `register/1` | public/static | `void` | `GunSmithTableRecipe` | — |
| 453 | `crafting/GunSmithTableRecipe.java:108` | `getById/1` | public/static | `GunSmithTableRecipe` | `ResourceLocation` | — |
| 454 | `crafting/GunSmithTableRecipe.java:113` | `getAll/0` | public/static | `Collection<GunSmithTableRecipe>` | `` | — |
| 455 | `crafting/GunSmithTableRecipe.java:118` | `getAllIds/0` | public/static | `List<ResourceLocation>` | `` | — |
| 456 | `crafting/GunSmithTableRecipe.java:123` | `clear/0` | public/static | `void` | `` | init/0@79 |
| 457 | `crafting/GunSmithTableRecipe.java:128` | `initAll/0` | public/static | `void` | `` | init/0@79 |
| 458 | `crafting/GunSmithTableRecipe.java:135` | `toString/0` | public/instance | `String` | `` | — |
| 459 | `crafting/GunSmithTableSerializer.java:41` | `deserialize/2` | public/static | `GunSmithTableRecipe` | `ResourceLocation\|JsonObject` | fromJson/2@20 ; fromNetwork/2@29 |
| 460 | `crafting/GunSmithTableSerializer.java:57` | `deserializeAndRegister/2` | public/static | `GunSmithTableRecipe` | `ResourceLocation\|JsonObject` | fromJson/2@20 ; fromNetwork/2@29 |
| 461 | `crafting/GunSmithTableSerializer.java:71` | `deserializeResult/1` | public/static | `GunSmithTableResult` | `JsonObject` | — |
| 462 | `crafting/result/GunSmithTableResult.java:84` | `fromJson/1` | public/static | `GunSmithTableResult` | `JsonElement` | — |
| 463 | `entity/EntityKineticBullet.java:255` | `onUpdate/0` | public/instance | `void` | `` | defineSynchedData/0@238 ; tick/0@242 ; onBulletTick/0@292 |
| 464 | `entity/EntityKineticBullet.java:802` | `getOwner/0` | public/instance | `Entity` | `` | getEntity/0@766 |
| 465 | `entity/EntityKineticBullet.java:807` | `setOwner/1` | public/instance | `void` | `Entity` | applyShotgunDamageSpread/1@225 ; setShotDamageMultiplier/1@233 ; writeSpawnData/1@611 ; readSpawnData/1@636 ; setCameraYRot/1@685 ; setCameraXRot/1@693 ; setFirstPersonRenderOffset/1@701 |
| 466 | `entity/EntityKineticBullet.java:892` | `entityInit/0` | protected/instance | `void` | `` | defineSynchedData/0@238 ; tick/0@242 ; onBulletTick/0@292 |
| 467 | `entity/EntityKineticBullet.java:905` | `writeEntityToNBT/1` | public/instance | `void` | `NBTTagCompound` | applyShotgunDamageSpread/1@225 ; setShotDamageMultiplier/1@233 ; writeSpawnData/1@611 ; readSpawnData/1@636 ; setCameraYRot/1@685 ; setCameraXRot/1@693 ; setFirstPersonRenderOffset/1@701 |
| 468 | `entity/EntityKineticBullet.java:912` | `readEntityFromNBT/1` | public/instance | `void` | `NBTTagCompound` | applyShotgunDamageSpread/1@225 ; setShotDamageMultiplier/1@233 ; writeSpawnData/1@611 ; readSpawnData/1@636 ; setCameraYRot/1@685 ; setCameraXRot/1@693 ; setFirstPersonRenderOffset/1@701 |
| 469 | `entity/TargetMinecart.java:137` | `attackEntityFrom/2` | public/instance | `boolean` | `DamageSource\|float` | — |
| 470 | `entity/TargetMinecart.java:146` | `killMinecart/1` | public/instance | `void` | `DamageSource` | destroy/1@107 |
| 471 | `entity/TargetMinecart.java:168` | `getRenderDistanceSqr/0` | public/static | `double` | `` | — |
| 472 | `entity/shooter/ShooterDataHolder.java:153` | `getCurrentGunItem/0` | public/instance | `ItemStack` | `` | — |
| 473 | `event/AmmoStorageInteractionEvent.java:36` | `register/0` | public/static | `void` | `` | — |
| 474 | `event/CommonLoadPack.java:21` | `loadGunPack/0` | public/static | `void` | `` | — |
| 475 | `event/EntityDamageEvent.java:34` | `register/0` | public/static | `void` | `` | — |
| 476 | `event/GunDisplayCaseInteractionEvent.java:51` | `register/0` | public/static | `void` | `` | — |
| 477 | `event/GunPegboardInteractionEvent.java:39` | `register/0` | public/static | `void` | `` | — |
| 478 | `event/GunSoundAngerEvent.java:64` | `register/0` | public/static | `void` | `` | — |
| 479 | `event/HitboxHelperEvent.java:31` | `register/0` | public/static | `void` | `` | — |
| 480 | `event/KnockbackChange.java:26` | `register/0` | public/static | `void` | `` | — |
| 481 | `event/KnockbackChange.java:33` | `modifyStrength/2` | public/static | `float` | `EntityLivingBase\|float` | — |
| 482 | `event/LoadingConfigEvent.java:26` | `reloadConfig/0` | public/static | `void` | `` | — |
| 483 | `event/PlayerRespawnEvent.java:33` | `register/0` | public/static | `void` | `` | — |
| 484 | `event/PreventGunClick.java:24` | `register/0` | public/static | `void` | `` | — |
| 485 | `event/ServerTickEvent.java:22` | `register/0` | public/static | `void` | `` | — |
| 486 | `event/SyncBaseTimestamp.java:29` | `register/0` | public/static | `void` | `` | — |
| 487 | `event/SyncedEntityDataEvent.java:65` | `register/0` | public/static | `void` | `` | — |
| 488 | `event/SyncedEntityDataEvent.java:73` | `onEntityJoinWorld/1` | public/instance | `void` | `EntityJoinWorldEvent` | attachCapabilities/1@26 ; onStartTracking/1@37 ; onPlayerJoinWorld/1@52 ; onPlayerClone/1@66 ; onServerTick/1@87 |
| 489 | `event/TravelToDimensionEvent.java:36` | `register/0` | public/static | `void` | `` | — |
| 490 | `event/ammo/BellRing.java:27` | `register/0` | public/static | `void` | `` | — |
| 491 | `event/ammo/DestroyGlassBlock.java:35` | `register/0` | public/static | `void` | `` | — |
| 492 | `init/CapabilityRegistry.java:33` | `register/0` | public/static | `void` | `` | — |
| 493 | `init/CommandRegistry.java:54` | `registerCommands/1` | public/static | `void` | `FMLServerStartingEvent` | onServerStaring/1@9 |
| 494 | `init/CommonRegistry.java:75` | `commonSetup/0` | public/static | `void` | `` | — |
| 495 | `init/CommonRegistry.java:96` | `markLoadComplete/0` | public/static | `void` | `` | — |
| 496 | `init/CommonRegistry.java:112` | `registerAttributes/0` | public/static | `void` | `` | — |
| 497 | `init/CommonRegistry.java:134` | `onEntityConstructing/1` | public/static | `void` | `EntityEvent.EntityConstructing` | onSetupEvent/1@18 ; onLoadComplete/1@28 ; registerAttributes/1@37 ; onAddPackFinders/1@44 |
| 498 | `init/CommonRegistry.java:157` | `register/0` | public/static | `void` | `` | — |
| 499 | `init/CompatRegistry.java:45` | `enqueueCompat/0` | public/static | `void` | `` | — |
| 500 | `init/CompatRegistry.java:70` | `register/0` | public/static | `void` | `` | — |
| 501 | `init/CompatRegistry.java:92` | `invokeOptional/2` | public/static | `boolean` | `String\|String` | — |
| 502 | `init/ModDamageTypes.java:58` | `isBulletDamage/1` | public/static | `boolean` | `DamageSource` | — |
| 503 | `init/ModDamageTypes.java:82` | `bullet/2` | public/static | `DamageSource` | `Entity\|Entity` | — |
| 504 | `init/ModDamageTypes.java:86` | `bullet/3` | public/static | `DamageSource` | `Entity\|Entity\|boolean` | — |
| 505 | `init/ModDamageTypes.java:95` | `bulletVoid/2` | public/static | `DamageSource` | `Entity\|Entity` | — |
| 506 | `init/ModDamageTypes.java:99` | `bulletVoid/3` | public/static | `DamageSource` | `Entity\|Entity\|boolean` | — |
| 507 | `init/ModDamageTypes.java:135` | `getTaczDamageType/0` | public/instance | `ResourceLocation` | `` | — |
| 508 | `init/ModItems.java:49` | `register/0` | public/static | `void` | `` | — |
| 509 | `init/ModParticles.java:14` | `register/0` | public/static | `void` | `` | — |
| 510 | `init/ModRecipe.java:52` | `register/0` | public/static | `void` | `` | — |
| 511 | `inventory/AmmoStorageInventory.java:344` | `getSizeInventory/0` | public/instance | `int` | `` | getCapacityStacks/0@39 ; getUsedTypes/0@53 ; getSlots/0@98 ; getComparatorSignal/0@227 ; findEmptySlot/0@307 |
| 512 | `inventory/AmmoStorageInventory.java:361` | `decrStackSize/2` | public/instance | `ItemStack` | `int\|int` | — |
| 513 | `inventory/AmmoStorageInventory.java:372` | `getStackInSlotOnClosing/1` | public/instance | `ItemStack` | `int` | getStackInSlot/1@103 |
| 514 | `inventory/AmmoStorageInventory.java:379` | `setInventorySlotContents/2` | public/instance | `void` | `int\|ItemStack` | — |
| 515 | `inventory/AmmoStorageInventory.java:393` | `getInventoryName/0` | public/instance | `String` | `` | — |
| 516 | `inventory/AmmoStorageInventory.java:398` | `hasCustomInventoryName/0` | public/instance | `boolean` | `` | — |
| 517 | `inventory/AmmoStorageInventory.java:403` | `getInventoryStackLimit/0` | public/instance | `int` | `` | getCapacityStacks/0@39 ; getUsedTypes/0@53 ; getSlots/0@98 ; getComparatorSignal/0@227 ; findEmptySlot/0@307 |
| 518 | `inventory/AmmoStorageInventory.java:410` | `markDirty/0` | public/instance | `void` | `` | changed/0@320 |
| 519 | `inventory/AmmoStorageInventory.java:415` | `isUseableByPlayer/1` | public/instance | `boolean` | `EntityPlayer` | — |
| 520 | `inventory/AmmoStorageInventory.java:421` | `openInventory/0` | public/instance | `void` | `` | changed/0@320 |
| 521 | `inventory/AmmoStorageInventory.java:426` | `closeInventory/0` | public/instance | `void` | `` | changed/0@320 |
| 522 | `inventory/AmmoStorageInventory.java:431` | `isItemValidForSlot/2` | public/instance | `boolean` | `int\|ItemStack` | isItemValid/2@212 |
| 523 | `inventory/AmmoStorageInventory.java:525` | `isSameItemSameTags/2` | public/static | `boolean` | `ItemStack\|ItemStack` | isItemValid/2@212 |
| 524 | `inventory/AmmoStorageInventory.java:537` | `withCount/2` | public/static | `ItemStack` | `ItemStack\|int` | — |
| 525 | `inventory/AmmoStorageMenu.java:174` | `send/5` | package/instance | `void` | `EntityPlayer\|int\|int\|long\|List<AmmoStorageEntry>` | — |
| 526 | `inventory/AmmoStorageMenu.java:178` | `setContentSender/1` | public/static | `void` | `ContentSender` | — |
| 527 | `inventory/AmmoStorageMenu.java:182` | `detectAndSendChanges/0` | public/instance | `void` | `` | broadcastChanges/0@75 |
| 528 | `inventory/AmmoStorageMenu.java:244` | `updateProgressBar/2` | public/instance | `void` | `int\|int` | — |
| 529 | `inventory/AmmoStorageMenu.java:304` | `transferStackInSlot/2` | public/instance | `ItemStack` | `EntityPlayer\|int` | quickMoveStack/2@144 |
| 530 | `inventory/AmmoStorageMenu.java:334` | `canInteractWith/1` | public/instance | `boolean` | `EntityPlayer` | stillValid/1@174 |
| 531 | `inventory/GunSmithTableMenu.java:124` | `canSelectRecipe/1` | public/instance | `boolean` | `ResourceLocation` | stillValid/1@81 |
| 532 | `inventory/GunSmithTableMenu.java:155` | `transferStackInSlot/2` | public/instance | `ItemStack` | `EntityPlayer\|int` | quickMoveStack/2@76 |
| 533 | `inventory/GunSmithTableMenu.java:161` | `canInteractWith/1` | public/instance | `boolean` | `EntityPlayer` | stillValid/1@81 |
| 534 | `item/AmmoBoxItem.java:68` | `registerIcons/1` | public/instance | `void` | `IIconRegister` | playRemoveOneSound/1@180 ; playInsertSound/1@184 ; fillItemCategory/1@243 |
| 535 | `item/AmmoBoxItem.java:86` | `getStatue/1` | public/static | `float` | `ItemStack` | — |
| 536 | `item/AmmoBoxItem.java:125` | `getColorFromItemStack/2` | public/instance | `int` | `ItemStack\|int` | getColor/2@55 ; getOpenStatue/2@77 ; getLevelStatue/2@86 |
| 537 | `item/AmmoBoxItem.java:130` | `getItemStackDisplayName/1` | public/instance | `String` | `ItemStack` | — |
| 538 | `item/AmmoBoxItem.java:148` | `hasEffect/1` | public/instance | `boolean` | `ItemStack` | isBarVisible/1@188 ; isFoil/1@230 |
| 539 | `item/AmmoBoxItem.java:156` | `showDurabilityBar/1` | public/instance | `boolean` | `ItemStack` | isBarVisible/1@188 ; isFoil/1@230 |
| 540 | `item/AmmoBoxItem.java:167` | `getDurabilityForDisplay/1` | public/instance | `double` | `ItemStack` | — |
| 541 | `item/AmmoBoxItem.java:182` | `fillItemCategory/0` | public/static | `List<ItemStack>` | `` | — |
| 542 | `item/AmmoBoxItem.java:198` | `addInformation/4` | public/instance | `void` | `ItemStack\|EntityPlayer\|List\|boolean` | appendHoverText/4@274 |
| 543 | `item/AmmoItem.java:42` | `registerIcons/1` | public/instance | `void` | `IIconRegister` | initializeClient/1@73 |
| 544 | `item/AmmoItem.java:53` | `getItemStackLimit/1` | public/instance | `int` | `ItemStack` | getMaxStackSize/1@39 |
| 545 | `item/AmmoItem.java:63` | `getItemStackDisplayName/1` | public/instance | `String` | `ItemStack` | — |
| 546 | `item/AmmoStorageBlockItem.java:42` | `getItemStackDisplayName/1` | public/instance | `String` | `ItemStack` | — |
| 547 | `item/AmmoStorageBlockItem.java:54` | `addInformation/4` | public/instance | `void` | `ItemStack\|EntityPlayer\|List\|boolean` | appendHoverText/4@48 |
| 548 | `item/AmmoStorageBlockItem.java:63` | `placeBlockAt/11` | public/instance | `boolean` | `ItemStack\|EntityPlayer\|World\|int\|int\|int\|int\|float\|float\|float\|int` | — |
| 549 | `item/AmmoStorageBlockItem.java:93` | `onUpdate/5` | public/instance | `void` | `ItemStack\|World\|Entity\|int\|boolean` | — |
| 550 | `item/AttachmentItem.java:45` | `registerIcons/1` | public/instance | `void` | `IIconRegister` | initializeClient/1@68 ; verifyTagAfterLoad/1@101 |
| 551 | `item/AttachmentItem.java:56` | `getItemStackDisplayName/1` | public/instance | `String` | `ItemStack` | — |
| 552 | `item/GunPegboardItem.java:42` | `hasCustomColor/1` | public/instance | `boolean` | `ItemStack` | — |
| 553 | `item/GunPegboardItem.java:51` | `getColor/1` | public/instance | `int` | `ItemStack` | — |
| 554 | `item/GunPegboardItem.java:60` | `placeBlockAt/11` | public/instance | `boolean` | `ItemStack\|EntityPlayer\|World\|int\|int\|int\|int\|float\|float\|float\|int` | — |
| 555 | `item/GunPegboardItem.java:91` | `getColorFromItemStack/2` | public/instance | `int` | `ItemStack\|int` | — |
| 556 | `item/GunPegboardItem.java:96` | `getItemStackDisplayName/1` | public/instance | `String` | `ItemStack` | — |
| 557 | `item/GunPegboardItem.java:103` | `addInformation/4` | public/instance | `void` | `ItemStack\|EntityPlayer\|List\|boolean` | appendHoverText/4@45 |
| 558 | `item/GunSmithTableItem.java:58` | `getItemStackDisplayName/1` | public/instance | `String` | `ItemStack` | — |
| 559 | `item/ModernKineticGunItem.java:106` | `registerIcons/1` | public/instance | `void` | `IIconRegister` | — |
| 560 | `item/ModernKineticGunItem.java:128` | `onUpdate/5` | public/instance | `void` | `ItemStack\|World\|Entity\|int\|boolean` | inventoryTick/5@66 ; shoot/5@126 ; doPerLivingHurt/5@559 |
| 561 | `item/TargetMinecartItem.java:40` | `registerIcons/1` | public/instance | `void` | `IIconRegister` | — |
| 562 | `item/TargetMinecartItem.java:50` | `onItemUse/10` | public/instance | `boolean` | `ItemStack\|EntityPlayer\|World\|int\|int\|int\|int\|float\|float\|float` | — |
| 563 | `loot/LootTableInjectorModifier.java:68` | `inject/2` | public/static | `int` | `ResourceLocation\|List<ItemStack>` | — |
| 564 | `loot/LootTableInjectorModifier.java:99` | `createStacks/1` | public/static | `List<ItemStack>` | `LootTableInjection` | — |
| 565 | `mixin/client/CreativeModeInventoryScreenMixin.java:198` | `run/0` | public/instance | `void` | `` | — |
| 566 | `network/NetworkHandler.java:421` | `getOnlinePlayers/0` | public/static | `List<EntityPlayerMP>` | `` | — |
| 567 | `network/NetworkHandler.java:431` | `getVersion/0` | public/static | `String` | `` | — |
| 568 | `network/message/ClientMessageAmmoStorageClick.java:44` | `containerId/0` | public/instance | `int` | `` | — |
| 569 | `network/message/ClientMessageAmmoStorageClick.java:48` | `entryId/0` | public/instance | `int` | `` | — |
| 570 | `network/message/ClientMessageAmmoStorageClick.java:52` | `action/0` | public/instance | `AmmoStorageMenu.Action` | `` | — |
| 571 | `network/message/ClientMessageAmmoStorageClick.java:56` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 572 | `network/message/ClientMessageAmmoStorageClick.java:63` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 573 | `network/message/ClientMessageAmmoStorageInteract.java:66` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 574 | `network/message/ClientMessageAmmoStorageInteract.java:71` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 575 | `network/message/ClientMessageCraft.java:36` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 576 | `network/message/ClientMessageCraft.java:42` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 577 | `network/message/ClientMessageInstallVirtualAttachment.java:45` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 578 | `network/message/ClientMessageInstallVirtualAttachment.java:52` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 579 | `network/message/ClientMessageLaserColor.java:64` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 580 | `network/message/ClientMessageLaserColor.java:72` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 581 | `network/message/ClientMessagePlayerAim.java:25` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 582 | `network/message/ClientMessagePlayerAim.java:30` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 583 | `network/message/ClientMessagePlayerCrawl.java:25` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 584 | `network/message/ClientMessagePlayerCrawl.java:30` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 585 | `network/message/ClientMessagePlayerShoot.java:38` | `write/1` | protected/instance | `void` | `com.tacz.guns.mcshim.PacketBuffer` | — |
| 586 | `network/message/ClientMessagePlayerShoot.java:44` | `read/1` | protected/instance | `void` | `com.tacz.guns.mcshim.PacketBuffer` | — |
| 587 | `network/message/ClientMessagePlayerUnloadGun.java:46` | `write/1` | protected/instance | `void` | `PacketBuffer` | showCannotUnload/1@85 |
| 588 | `network/message/ClientMessagePlayerUnloadGun.java:52` | `read/1` | protected/instance | `void` | `PacketBuffer` | showCannotUnload/1@85 |
| 589 | `network/message/ClientMessageRefitGun.java:49` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 590 | `network/message/ClientMessageRefitGun.java:56` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 591 | `network/message/ClientMessageSetAttachmentVariant.java:47` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 592 | `network/message/ClientMessageSetAttachmentVariant.java:57` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 593 | `network/message/ClientMessageSetSlotAdapter.java:51` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 594 | `network/message/ClientMessageSetSlotAdapter.java:61` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 595 | `network/message/ClientMessageUnloadAttachment.java:38` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 596 | `network/message/ClientMessageUnloadAttachment.java:44` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 597 | `network/message/ServerMessageAmmoStorageContents.java:56` | `containerId/0` | public/instance | `int` | `` | — |
| 598 | `network/message/ServerMessageAmmoStorageContents.java:60` | `capacityStacks/0` | public/instance | `int` | `` | — |
| 599 | `network/message/ServerMessageAmmoStorageContents.java:64` | `usedStacks/0` | public/instance | `long` | `` | — |
| 600 | `network/message/ServerMessageAmmoStorageContents.java:68` | `entries/0` | public/instance | `List<AmmoStorageEntry>` | `` | — |
| 601 | `network/message/ServerMessageAmmoStorageContents.java:72` | `write/1` | protected/instance | `void` | `PacketBuffer` | updateContents/1@62 |
| 602 | `network/message/ServerMessageAmmoStorageContents.java:86` | `read/1` | protected/instance | `void` | `PacketBuffer` | updateContents/1@62 |
| 603 | `network/message/ServerMessageBulletHitBlock.java:78` | `write/1` | protected/instance | `void` | `PacketBuffer` | handleClient/1@82 |
| 604 | `network/message/ServerMessageBulletHitBlock.java:91` | `read/1` | protected/instance | `void` | `PacketBuffer` | handleClient/1@82 |
| 605 | `network/message/ServerMessageBulletHitBlock.java:107` | `getHitX/0` | public/instance | `double` | `` | — |
| 606 | `network/message/ServerMessageBulletHitBlock.java:111` | `getHitY/0` | public/instance | `double` | `` | — |
| 607 | `network/message/ServerMessageBulletHitBlock.java:115` | `getHitZ/0` | public/instance | `double` | `` | — |
| 608 | `network/message/ServerMessageBulletHitBlock.java:119` | `getDirection/0` | public/instance | `ForgeDirection` | `` | — |
| 609 | `network/message/ServerMessageBulletHitBlock.java:124` | `getBlockX/0` | public/instance | `int` | `` | — |
| 610 | `network/message/ServerMessageBulletHitBlock.java:128` | `getBlockY/0` | public/instance | `int` | `` | — |
| 611 | `network/message/ServerMessageBulletHitBlock.java:132` | `getBlockZ/0` | public/instance | `int` | `` | — |
| 612 | `network/message/ServerMessageBulletHitBlock.java:136` | `getAmmoId/0` | public/instance | `String` | `` | — |
| 613 | `network/message/ServerMessageBulletHitBlock.java:140` | `getGunId/0` | public/instance | `String` | `` | — |
| 614 | `network/message/ServerMessageBulletHitBlock.java:144` | `getGunDisplayId/0` | public/instance | `String` | `` | — |
| 615 | `network/message/ServerMessageBulletHitBlock.java:148` | `isIncendiary/0` | public/instance | `boolean` | `` | — |
| 616 | `network/message/ServerMessageCraft.java:28` | `getMenuId/0` | public/instance | `int` | `` | — |
| 617 | `network/message/ServerMessageCraft.java:32` | `write/1` | protected/instance | `void` | `PacketBuffer` | updateScreen/1@34 |
| 618 | `network/message/ServerMessageCraft.java:37` | `read/1` | protected/instance | `void` | `PacketBuffer` | updateScreen/1@34 |
| 619 | `network/message/ServerMessageGunUnload.java:31` | `write/1` | protected/instance | `void` | `PacketBuffer` | playSound/1@39 |
| 620 | `network/message/ServerMessageGunUnload.java:37` | `read/1` | protected/instance | `void` | `PacketBuffer` | playSound/1@39 |
| 621 | `network/message/ServerMessageGunUnload.java:43` | `getGunId/0` | public/instance | `ResourceLocation` | `` | — |
| 622 | `network/message/ServerMessageGunUnload.java:47` | `getGunDisplayId/0` | public/instance | `ResourceLocation` | `` | — |
| 623 | `network/message/ServerMessageLevelUp.java:30` | `write/1` | protected/instance | `void` | `PacketBuffer` | onLevelUp/1@39 |
| 624 | `network/message/ServerMessageLevelUp.java:37` | `read/1` | protected/instance | `void` | `PacketBuffer` | onLevelUp/1@39 |
| 625 | `network/message/ServerMessageSound.java:49` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 626 | `network/message/ServerMessageSound.java:60` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 627 | `network/message/ServerMessageSyncGunPack.java:111` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 628 | `network/message/ServerMessageSyncGunPack.java:120` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 629 | `network/message/ServerMessageSyncGunPack.java:136` | `toPart/0` | public/instance | `GunPackSync.Part` | `` | — |
| 630 | `network/message/ServerMessageSyncGunPack.java:140` | `acceptPart/1` | public/static | `byte[]` | `GunPackSync.Part` | — |
| 631 | `network/message/ServerMessageUpdateEntityData.java:40` | `write/1` | protected/instance | `void` | `PacketBuffer` | onHandle/1@48 |
| 632 | `network/message/ServerMessageUpdateEntityData.java:49` | `read/1` | protected/instance | `void` | `PacketBuffer` | onHandle/1@48 |
| 633 | `network/message/event/ServerMessageGunDraw.java:33` | `write/1` | protected/instance | `void` | `PacketBuffer` | doClientEvent/1@47 |
| 634 | `network/message/event/ServerMessageGunDraw.java:40` | `read/1` | protected/instance | `void` | `PacketBuffer` | doClientEvent/1@47 |
| 635 | `network/message/event/ServerMessageGunFinishReload.java:22` | `postEvent/1` | protected/instance | `void` | `EntityLivingBase` | doClientEvent/1@43 |
| 636 | `network/message/event/ServerMessageGunFire.java:22` | `postEvent/1` | protected/instance | `void` | `EntityLivingBase` | doClientEvent/1@43 |
| 637 | `network/message/event/ServerMessageGunFirePost.java:22` | `postEvent/1` | protected/instance | `void` | `EntityLivingBase` | doClientEvent/1@43 |
| 638 | `network/message/event/ServerMessageGunFireSelect.java:22` | `postEvent/1` | protected/instance | `void` | `EntityLivingBase` | doClientEvent/1@43 |
| 639 | `network/message/event/ServerMessageGunHurt.java:51` | `write/1` | protected/instance | `void` | `PacketBuffer` | onHurt/1@70 |
| 640 | `network/message/event/ServerMessageGunHurt.java:63` | `read/1` | protected/instance | `void` | `PacketBuffer` | onHurt/1@70 |
| 641 | `network/message/event/ServerMessageGunKill.java:46` | `write/1` | protected/instance | `void` | `PacketBuffer` | onKill/1@69 |
| 642 | `network/message/event/ServerMessageGunKill.java:58` | `read/1` | protected/instance | `void` | `PacketBuffer` | onKill/1@69 |
| 643 | `network/message/event/ServerMessageGunMelee.java:22` | `postEvent/1` | protected/instance | `void` | `EntityLivingBase` | doClientEvent/1@43 |
| 644 | `network/message/event/ServerMessageGunReload.java:22` | `postEvent/1` | protected/instance | `void` | `EntityLivingBase` | doClientEvent/1@43 |
| 645 | `network/message/event/ServerMessageGunShoot.java:22` | `postEvent/1` | protected/instance | `void` | `EntityLivingBase` | doClientEvent/1@43 |
| 646 | `network/message/handshake/ServerMessageSyncedEntityDataMapping.java:58` | `snapshot/0` | public/static | `ServerMessageSyncedEntityDataMapping` | `` | — |
| 647 | `network/message/handshake/ServerMessageSyncedEntityDataMapping.java:66` | `write/1` | protected/instance | `void` | `PacketBuffer` | — |
| 648 | `network/message/handshake/ServerMessageSyncedEntityDataMapping.java:82` | `read/1` | protected/instance | `void` | `PacketBuffer` | — |
| 649 | `particles/BulletHoleOption.java:74` | `getDirectionOrdinal/0` | public/instance | `int` | `` | — |
| 650 | `particles/BulletHoleOption.java:79` | `getX/0` | public/instance | `int` | `` | — |
| 651 | `particles/BulletHoleOption.java:83` | `getY/0` | public/instance | `int` | `` | — |
| 652 | `particles/BulletHoleOption.java:87` | `getZ/0` | public/instance | `int` | `` | — |
| 653 | `resource/CommonAssetsManager.java:155` | `reloadAndRegister/0` | public/instance | `void` | `` | clearInstance/0@281 ; reloadAllPack/0@342 |
| 654 | `resource/CommonAssetsManager.java:387` | `isLoaded/0` | public/instance | `boolean` | `` | — |
| 655 | `resource/CommonAssetsManager.java:419` | `reload/0` | public/static | `void` | `` | clearInstance/0@281 ; reloadAllPack/0@342 |
| 656 | `resource/CommonAssetsManager.java:441` | `initLazyRecipes/0` | public/static | `void` | `` | clearInstance/0@281 ; reloadAllPack/0@342 |
| 657 | `resource/CommonAssetsManager.java:463` | `registerEventHandlers/0` | public/static | `void` | `` | clearInstance/0@281 ; reloadAllPack/0@342 |
| 658 | `resource/CommonAssetsManager.java:471` | `onPlayerLoggedIn/1` | public/static | `void` | `PlayerEvent.PlayerLoggedInEvent` | reloadAndRegister/1@92 ; onReload/1@295 ; onReload/1@305 ; onServerStopped/1@322 ; OnDatapackSync/1@328 |
| 659 | `resource/CommonAssetsManager.java:486` | `onWorldUnload/1` | public/static | `void` | `WorldEvent.Unload` | reloadAndRegister/1@92 ; onReload/1@295 ; onReload/1@305 ; onServerStopped/1@322 ; OnDatapackSync/1@328 |
| 660 | `resource/CommonAssetsManager.java:511` | `getNetworkSyncMessages/0` | public/instance | `Collection<ServerMessageSyncGunPack>` | `` | — |
| 661 | `resource/GunPackLoader.java:84` | `reloadPackList/0` | public/instance | `void` | `` | — |
| 662 | `resource/GunPackLoader.java:152` | `getPacks/0` | public/instance | `List<GunPack>` | `` | — |
| 663 | `resource/GunPackLoader.java:157` | `getPackSources/0` | public/instance | `List<IGunPackSource>` | `` | — |
| 664 | `resource/GunPackLoader.java:170` | `getPackDirectory/0` | public/static | `Path` | `` | — |
| 665 | `resource/GunPackLoader.java:176` | `getGameDirectory/0` | public/static | `File` | `` | — |
| 666 | `resource/GunPackLoader.java:196` | `readPackData/1` | public/static | `Map<ResourceLocation,JsonObject>` | `String` | — |
| 667 | `resource/GunPackLoader.java:222` | `injectGunPackLanguage/1` | public/static | `void` | `String` | loadPacks/1@54 |
| 668 | `resource/manager/AttachmentsTagManager.java:51` | `reload/0` | public/instance | `void` | `` | — |
| 669 | `resource/manager/AttachmentsTagManager.java:62` | `apply/1` | protected/instance | `void` | `Map<ResourceLocation,List<JsonElement>>` | — |
| 670 | `resource/manager/CommonDataManager.java:36` | `apply/1` | protected/instance | `void` | `Map<ResourceLocation,JsonElement>` | — |
| 671 | `resource/manager/GunIndexManager.java:26` | `apply/1` | protected/instance | `void` | `Map<ResourceLocation,JsonElement>` | — |
| 672 | `resource/manager/JsonDataManager.java:69` | `reload/0` | public/instance | `void` | `` | — |
| 673 | `resource/manager/JsonDataManager.java:81` | `apply/1` | protected/instance | `void` | `Map<ResourceLocation,JsonElement>` | — |
| 674 | `resource/manager/JsonDataManager.java:121` | `packSources/0` | protected/instance | `List<IGunPackSource>` | `` | — |
| 675 | `resource/manager/JsonDataManager.java:126` | `getPackDirectory/0` | protected/instance | `PackDirectory` | `` | — |
| 676 | `resource/manager/JsonDataManager.java:130` | `getRoot/0` | protected/instance | `String` | `` | — |
| 677 | `resource/manager/LazyJsonDataManager.java:77` | `reload/0` | public/instance | `void` | `` | — |
| 678 | `resource/manager/LazyJsonDataManager.java:105` | `apply/1` | protected/instance | `void` | `PreparedResult<T>` | postLoad/1@113 |
| 679 | `resource/manager/LazyJsonDataManager.java:136` | `loadResource/2` | protected/instance | `LoadResult<T>` | `ResourceLocation\|GunPackResources.PackedResource` | loadResourceFromElement/2@124 |
| 680 | `resource/manager/LazyJsonDataManager.java:157` | `readResourceElement/1` | protected/instance | `JsonElement` | `GunPackResources.PackedResource` | — |
| 681 | `resource/manager/LootInjectionManager.java:38` | `reload/0` | public/instance | `void` | `` | — |
| 682 | `resource/manager/RecipeFilterManager.java:45` | `reload/0` | public/instance | `void` | `` | — |
| 683 | `resource/manager/RecipeFilterManager.java:56` | `apply/1` | protected/instance | `void` | `Map<ResourceLocation,List<JsonElement>>` | — |
| 684 | `resource/manager/ScriptManager.java:72` | `reload/0` | public/instance | `void` | `` | initGlobals/0@89 |
| 685 | `util/AllowAttachmentTagMatcher.java:64` | `allowAttachmentCache/0` | public/instance | `Map<ResourceLocation,Set<ResourceLocation>>` | `` | — |
| 686 | `util/AllowAttachmentTagMatcher.java:68` | `tagMatchCache/0` | public/instance | `Map<ResourceLocation,Set<ResourceLocation>>` | `` | — |
| 687 | `util/EntityUtil.java:144` | `getHitResult/5` | protected/static | `EntityKineticBullet.EntityResult` | `EntityKineticBullet\|Entity\|Entity\|Vec3\|Vec3` | — |
| 688 | `util/LaserColorUtil.java:53` | `setClientDefaultColorResolver/1` | public/static | `void` | `Function<ItemStack,Integer>` | — |
| 689 | `util/RenderDistance.java:44` | `setScreenDrawActive/1` | public/static | `void` | `boolean` | — |
| 690 | `util/RenderDistance.java:49` | `isScreenDrawActive/0` | public/static | `boolean` | `` | — |
| 691 | `util/RenderDistance.java:66` | `inRenderHighPolyModelDistance/0` | public/static | `boolean` | `` | — |
| 692 | `util/RenderHelper.java:58` | `isOptifineInstalled/0` | public/static | `boolean` | `` | — |
| 693 | `util/SlotAdapterHelper.java:76` | `allowsAttachment/1` | public/instance | `boolean` | `ResourceLocation` | allowsSlotAdapters/1@134 |
| 694 | `util/SlotAdapterHelper.java:81` | `getIndex/0` | public/instance | `CommonSlotAdapterIndex` | `` | — |
| 695 | `util/SlotAdapterHelper.java:85` | `getSlot/0` | public/instance | `AttachmentType` | `` | — |
| 696 | `util/SlotAdapterHelper.java:89` | `getSort/0` | public/instance | `int` | `` | — |
| 697 | `util/SlotAdapterHelper.java:158` | `getAvailableSlotAdapterIndices/2` | public/static | `List<Map.Entry<ResourceLocation,CommonSlotAdapterIndex>>` | `ItemStack\|AttachmentType` | getAvailableSlotAdapters/2@71 |
| 698 | `util/ThermalImagingHelper.java:41` | `registerBlacklist/1` | public/static | `void` | `ResourceLocation` | — |
| 699 | `util/ThermalImagingHelper.java:48` | `registerWhitelist/1` | public/static | `void` | `ResourceLocation` | — |
| 700 | `util/ThermalImagingHelper.java:55` | `clearBlacklist/0` | public/static | `void` | `` | — |
| 701 | `util/ThermalImagingHelper.java:59` | `clearWhitelist/0` | public/static | `void` | `` | — |
| 702 | `util/block/BlockRayTrace.java:68` | `registerIgnoreBlock/1` | public/static | `void` | `Block` | — |
| 703 | `util/block/BlockRayTrace.java:75` | `clearIgnoreBlocks/0` | public/static | `void` | `` | — |
| 704 | `util/block/BlockRayTrace.java:80` | `isIgnoredBlock/1` | public/static | `boolean` | `Block` | — |
| 705 | `util/block/BlockRayTrace.java:106` | `rayTraceBlocks/3` | public/static | `MovingObjectPosition` | `World\|Vec3\|Vec3` | — |
| 706 | `util/block/BlockRayTrace.java:148` | `hit/4` | package/instance | `MovingObjectPosition` | `World\|int\|int\|int` | — |
| 707 | `util/block/ProjectileExplosion.java:93` | `getHitPlayers/0` | public/instance | `Map<EntityPlayer,Vec3>` | `` | — |
| 708 | `util/block/ProjectileExplosion.java:98` | `doExplosionA/0` | public/instance | `void` | `` | explode/0@56 |
| 709 | `util/time/GunTime.java:40` | `setTickGate/1` | public/static | `void` | `BooleanSupplier` | — |

### B4. RET/STATIC/VIS 差异汇总

`RET_DIFF` 按 `返回类型:上游->本移植` 归并（前 40 种，共 232 种）：

| 返回类型变化 | 条数 | 定性 |
|---|---|---|
| `Tag->NBTBase` | 185 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Component->IChatComponent` | 14 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `LivingEntity->EntityLivingBase` | 12 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `LogicalSide->Side` | 10 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Direction->ForgeDirection` | 9 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `AABB->AxisAlignedBB` | 9 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `CompoundTag->NBTTagCompound` | 7 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `NonNullList<ItemStack>->List<ItemStack>` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->Byte` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->Short` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->Integer` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->Long` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->Float` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->Double` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->Character` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->String` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->NBTTagCompound` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->UUID` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->ItemStack` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Boolean->ResourceLocation` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->Boolean` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->Short` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->Integer` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->Long` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->Float` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->Double` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->Character` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->String` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->NBTTagCompound` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->UUID` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->ItemStack` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Byte->ResourceLocation` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Short->Boolean` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Short->Byte` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Short->Integer` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Short->Long` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Short->Float` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Short->Double` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Short->Character` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |
| `Short->String` | 4 | 1.20→1.7.10 平台类型替换 / 匿名内部类重载配对噪声 |

> 说明：该表存在**笛卡尔噪声**——同一文件内同名同元数的重载（典型：`entity/sync/core/Serializers.java` 中 10 个
> 匿名 `Serializer` 各自的 `read/1` 返回不同类型）会被两两配对，故 `RET_DIFF=1009` 中大量是配对产物而非真实改动。
> 真实语义回归应看：① 方法名+元数集合差异（B2/B3）；② 字段/JSON 契约（C）；③ 方法体规模比（主报告 §六）。

#### VIS_DIFF 全量（12）

| 上游位置 | 方法/元数 | 上游可见性 → 本移植 |
|---|---|---|
| `GunMod.java:60` | `registerDefaultExtraGunPack/0` | vis:private->public |
| `block/GunSmithTableBlockB.java:30` | `getNeighbourDirection/2` | vis:private->public |
| `client/particle/BulletHoleParticle.java:98` | `setSprite/1` | vis:protected->private |
| `client/particle/BulletHoleParticle.java:117` | `getU0/0` | vis:protected->private |
| `client/particle/BulletHoleParticle.java:122` | `getV0/0` | vis:protected->private |
| `client/particle/BulletHoleParticle.java:127` | `getU1/0` | vis:protected->private |
| `client/particle/BulletHoleParticle.java:132` | `getV1/0` | vis:protected->private |
| `item/AmmoBoxItem.java:180` | `playRemoveOneSound/1` | vis:private->public |
| `item/AmmoBoxItem.java:184` | `playInsertSound/1` | vis:private->public |
| `network/message/ServerMessageSyncGunPack.java:109` | `readCache/1` | vis:private->public |
| `util/CycleTaskHelper.java:34` | `tick/0` | vis:public->private |
| `util/CycleTaskHelper.java:66` | `tick/0` | vis:private->public |

#### STATIC_DIFF（83）归类

主要来源（同名方法 static↔instance 变化，均为 1.7.10 钩子形态差异）：

| 方法名 | 条数 |
|---|---|
| `applyPositioningNodeTransform` | 6 |
| `applyPositioningNode` | 6 |
| `tickAnimation` | 4 |
| `onLeftClickBlock` | 3 |
| `onGunFire` | 2 |
| `onRenderBlockHighlight` | 2 |
| `onPlayerLoggedOut` | 2 |
| `onClientTick` | 2 |
| `getIndex` | 2 |
| `close` | 2 |
| `onServerTick` | 2 |
| `onPlayerJoinWorld` | 2 |
| `onAmmoHitBlock` | 2 |
| `handle` | 2 |
| `tick` | 2 |
| `applyLevelCameraAnimation` | 1 |
| `applyItemInHandCameraAnimation` | 1 |
| `applyScopeMagnification` | 1 |
| `initialCameraRecoil` | 1 |
| `applyCameraRecoil` | 1 |
| `onComputeMovementFov` | 1 |
| `onLoggingOut` | 1 |
| `onEntityHurt` | 1 |
| `onEntityKill` | 1 |
| `onClickInput` | 1 |
| `onClientPlayerLoggingIn` | 1 |
| `onClientPlayerLoggingOut` | 1 |
| `onRenderHand` | 1 |
| `cancelItemInHandViewBobbing` | 1 |
| `onPlayerChangeSelect` | 1 |
| `onPlayerSwapMainHand` | 1 |
| `onPlayerEnterWorld` | 1 |
| `onPlayerHurtByGun` | 1 |
| `onRenderHotbarEvent` | 1 |
| `onTextureStitchEventPost` | 1 |
| `onRenderOverlay` | 1 |
| `onRenderTick` | 1 |
| `onRenderEntity` | 1 |
| `onTooltip` | 1 |
| `onAimHoldingPreInput` | 1 |

### B5. 改名映射表全量（264 对；上游方法名 → 本移植方法名）

这是审查的核心表：**没有任何一对是「TACZ 自有方法名被换成看起来更合理的自创名」**。
逐对核对结论列：`platform` = 1.20 平台 API→1.7.10 平台 API；`sig` = 参数/返回平台化导致元数变化；
`mech` = 上游机制由新类承接；`noise` = 同元数+同返回的启发式误配（人工核对后归入上述三类之一）。

| # | 上游位置 | 上游方法 | 本移植位置 | 本移植方法 | 定性 |
|---|---|---|---|---|---|
| 1 | `advancement/GunRefitTrigger.java:19` | `getId/0` → `ResourceLocation` | `advancement/GunRefitTrigger.java:51` | `getPlayer/0` → `EntityPlayerMP` | platform |
| 2 | `advancement/TargetHitTrigger.java:16` | `getId/0` → `ResourceLocation` | `advancement/TargetHitTrigger.java:46` | `getPlayer/0` → `EntityPlayerMP` | platform |
| 3 | `api/event/server/AmmoHitBlockEvent.java:34` | `getHitResult/0` → `BlockHitResult` | `api/event/server/AmmoHitBlockEvent.java:53` | `getHitX/0` → `int` | platform |
| 4 | `api/item/gun/AbstractGunItem.java:332` | `getName/1` → `Component` | `api/item/gun/AbstractGunItem.java:450` | `getItemStackDisplayName/1` → `String` | platform |
| 5 | `api/item/gun/AbstractGunItem.java:380` | `initializeClient/1` → `void` | `api/item/gun/AbstractGunItem.java:450` | `getItemStackDisplayName/1` → `String` | platform |
| 6 | `api/item/gun/GunItemManager.java:17` | `getGunItemRegistryObject/1` → `RegistryObject<?extendsAbstractGunItem>` | `api/item/gun/GunItemManager.java:29` | `getGunItem/1` → `AbstractGunItem` | platform |
| 7 | `block/AbstractGunSmithTableBlock.java:39` | `use/6` → `InteractionResult` | `block/AbstractGunSmithTableBlock.java:123` | `onBlockPlacedBy/6` → `void` | platform |
| 8 | `block/AbstractGunSmithTableBlock.java:55` | `createBlockStateDefinition/1` → `void` | `block/AbstractGunSmithTableBlock.java:79` | `registerBlockIcons/1` → `void` | platform |
| 9 | `block/AbstractGunSmithTableBlock.java:60` | `newBlockEntity/2` → `BlockEntity` | `block/AbstractGunSmithTableBlock.java:95` | `createTileEntity/2` → `TileEntity` | platform |
| 10 | `block/AbstractGunSmithTableBlock.java:66` | `getRenderShape/1` → `RenderShape` | `block/AbstractGunSmithTableBlock.java:79` | `registerBlockIcons/1` → `void` | platform |
| 11 | `block/AbstractGunSmithTableBlock.java:71` | `getPistonPushReaction/1` → `PushReaction` | `block/AbstractGunSmithTableBlock.java:79` | `registerBlockIcons/1` → `void` | platform |
| 12 | `block/AbstractGunSmithTableBlock.java:76` | `setPlacedBy/5` → `void` | `block/AbstractGunSmithTableBlock.java:155` | `getPickBlock/5` → `ItemStack` | platform |
| 13 | `block/AbstractGunSmithTableBlock.java:91` | `getCloneItemStack/5` → `ItemStack` | `block/AbstractGunSmithTableBlock.java:155` | `getPickBlock/5` → `ItemStack` | platform |
| 14 | `block/AmmoStorageBlock.java:130` | `getDrops/2` → `List<ItemStack>` | `block/AmmoStorageBlock.java:126` | `getIcon/2` → `IIcon` | platform |
| 15 | `block/AmmoStorageBlock.java:143` | `hasAnalogOutputSignal/1` → `boolean` | `block/AmmoStorageBlock.java:161` | `hasTileEntity/1` → `boolean` | platform |
| 16 | `block/AmmoStorageBlock.java:157` | `getRenderShape/1` → `RenderShape` | `block/AmmoStorageBlock.java:109` | `registerBlockIcons/1` → `void` | platform |
| 17 | `block/AmmoStorageBlock.java:162` | `getPistonPushReaction/1` → `PushReaction` | `block/AmmoStorageBlock.java:109` | `registerBlockIcons/1` → `void` | platform |
| 18 | `block/AmmoStorageBlock.java:64` | `createBlockStateDefinition/1` → `void` | `block/AmmoStorageBlock.java:109` | `registerBlockIcons/1` → `void` | platform |
| 19 | `block/AmmoStorageBlock.java:69` | `newBlockEntity/2` → `BlockEntity` | `block/AmmoStorageBlock.java:166` | `createTileEntity/2` → `TileEntity` | platform |
| 20 | `block/AmmoStorageBlock.java:75` | `getStateForPlacement/1` → `BlockState` | `block/AmmoStorageBlock.java:109` | `registerBlockIcons/1` → `void` | platform |
| 21 | `block/AmmoStorageBlock.java:81` | `use/6` → `InteractionResult` | `block/AmmoStorageBlock.java:171` | `onBlockPlacedBy/6` → `void` | platform |
| 22 | `block/entity/AmmoStorageBlockEntity.java:112` | `invalidateCaps/0` → `void` | `block/entity/AmmoStorageBlockEntity.java:116` | `getInventory/0` → `IInventory` | platform |
| 23 | `block/entity/AmmoStorageBlockEntity.java:118` | `reviveCaps/0` → `void` | `block/entity/AmmoStorageBlockEntity.java:116` | `getInventory/0` → `IInventory` | platform |
| 24 | `block/entity/AmmoStorageBlockEntity.java:90` | `load/1` → `void` | `block/entity/AmmoStorageBlockEntity.java:135` | `readFromNBT/1` → `void` | platform |
| 25 | `block/entity/AmmoStorageBlockEntity.java:96` | `saveAdditional/1` → `void` | `block/entity/AmmoStorageBlockEntity.java:135` | `readFromNBT/1` → `void` | platform |
| 26 | `block/entity/GunDisplayCaseBlockEntity.java:155` | `load/1` → `void` | `block/entity/GunDisplayCaseBlockEntity.java:215` | `readFromNBT/1` → `void` | platform |
| 27 | `block/entity/GunDisplayCaseBlockEntity.java:186` | `saveAdditional/1` → `void` | `block/entity/GunDisplayCaseBlockEntity.java:215` | `readFromNBT/1` → `void` | platform |
| 28 | `block/entity/GunDisplayCaseBlockEntity.java:209` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | `block/entity/GunDisplayCaseBlockEntity.java:276` | `getDescriptionPacket/0` → `Packet` | platform |
| 29 | `block/entity/GunPegboardBlockEntity.java:208` | `setBlockState/1` → `void` | `block/entity/GunPegboardBlockEntity.java:327` | `readFromNBT/1` → `void` | platform |
| 30 | `block/entity/GunPegboardBlockEntity.java:232` | `load/1` → `void` | `block/entity/GunPegboardBlockEntity.java:327` | `readFromNBT/1` → `void` | platform |
| 31 | `block/entity/GunPegboardBlockEntity.java:281` | `saveAdditional/1` → `void` | `block/entity/GunPegboardBlockEntity.java:327` | `readFromNBT/1` → `void` | platform |
| 32 | `block/entity/GunPegboardBlockEntity.java:309` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | `block/entity/GunPegboardBlockEntity.java:421` | `getDescriptionPacket/0` → `Packet` | platform |
| 33 | `block/entity/GunSmithTableBlockEntity.java:50` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | `block/entity/GunSmithTableBlockEntity.java:104` | `getDescriptionPacket/0` → `Packet` | platform |
| 34 | `block/entity/GunSmithTableBlockEntity.java:73` | `load/1` → `void` | `block/entity/GunSmithTableBlockEntity.java:77` | `writeToNBT/1` → `void` | platform |
| 35 | `block/entity/GunSmithTableBlockEntity.java:83` | `saveAdditional/1` → `void` | `block/entity/GunSmithTableBlockEntity.java:77` | `writeToNBT/1` → `void` | platform |
| 36 | `block/entity/StatueBlockEntity.java:71` | `load/1` → `void` | `block/entity/StatueBlockEntity.java:116` | `readFromNBT/1` → `void` | platform |
| 37 | `block/entity/StatueBlockEntity.java:79` | `saveAdditional/1` → `void` | `block/entity/StatueBlockEntity.java:116` | `readFromNBT/1` → `void` | platform |
| 38 | `block/entity/StatueBlockEntity.java:85` | `getUpdateTag/0` → `CompoundTag` | `block/entity/StatueBlockEntity.java:53` | `updateEntity/0` → `void` | platform |
| 39 | `block/entity/StatueBlockEntity.java:97` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | `block/entity/StatueBlockEntity.java:140` | `getDescriptionPacket/0` → `Packet` | platform |
| 40 | `block/entity/TargetBlockEntity.java:106` | `getUpdatePacket/0` → `Packet<ClientGamePacketListener>` | `block/entity/TargetBlockEntity.java:245` | `getDescriptionPacket/0` → `Packet` | platform |
| 41 | `block/entity/TargetBlockEntity.java:111` | `getUpdateTag/0` → `CompoundTag` | `block/entity/TargetBlockEntity.java:76` | `updateEntity/0` → `void` | platform |
| 42 | `block/entity/TargetBlockEntity.java:69` | `load/1` → `void` | `block/entity/TargetBlockEntity.java:120` | `setOutputPower/1` → `void` | platform |
| 43 | `block/entity/TargetBlockEntity.java:80` | `saveAdditional/1` → `void` | `block/entity/TargetBlockEntity.java:120` | `setOutputPower/1` → `void` | platform |
| 44 | `block/entity/TargetBlockEntity.java:91` | `getName/0` → `Component` | `block/entity/TargetBlockEntity.java:76` | `updateEntity/0` → `void` | platform |
| 45 | `block/GunDisplayCaseBlock.java:101` | `newBlockEntity/2` → `BlockEntity` | `block/GunDisplayCaseBlock.java:163` | `createTileEntity/2` → `TileEntity` | platform |
| 46 | `block/GunDisplayCaseBlock.java:107` | `getStateForPlacement/1` → `BlockState` | `block/GunDisplayCaseBlock.java:93` | `registerBlockIcons/1` → `void` | platform |
| 47 | `block/GunDisplayCaseBlock.java:125` | `updateShape/6` → `BlockState` | `block/GunDisplayCaseBlock.java:176` | `onBlockPlacedBy/6` → `void` | platform |
| 48 | `block/GunDisplayCaseBlock.java:134` | `onPlace/5` → `void` | `block/GunDisplayCaseBlock.java:224` | `onNeighborBlockChange/5` → `void` | platform |
| 49 | `block/GunDisplayCaseBlock.java:142` | `use/6` → `InteractionResult` | `block/GunDisplayCaseBlock.java:176` | `onBlockPlacedBy/6` → `void` | platform |
| 50 | `block/GunDisplayCaseBlock.java:188` | `getShape/4` → `VoxelShape` | `block/GunDisplayCaseBlock.java:565` | `getSelectedBoundingBoxFromPool/4` → `AxisAlignedBB` | platform |
| 51 | `block/GunDisplayCaseBlock.java:208` | `getCollisionShape/4` → `VoxelShape` | `block/GunDisplayCaseBlock.java:584` | `getCollisionBoundingBoxFromPool/4` → `AxisAlignedBB` | platform |
| 52 | `block/GunDisplayCaseBlock.java:222` | `onRemove/5` → `void` | `block/GunDisplayCaseBlock.java:224` | `onNeighborBlockChange/5` → `void` | platform |
| 53 | `block/GunDisplayCaseBlock.java:233` | `getRenderShape/1` → `RenderShape` | `block/GunDisplayCaseBlock.java:93` | `registerBlockIcons/1` → `void` | platform |
| 54 | `block/GunDisplayCaseBlock.java:238` | `getPistonPushReaction/1` → `PushReaction` | `block/GunDisplayCaseBlock.java:93` | `registerBlockIcons/1` → `void` | platform |
| 55 | `block/GunDisplayCaseBlock.java:344` | `toggleLockFromInteraction/3` → `void` | `block/GunDisplayCaseBlock.java:125` | `withConnected/3` → `int` | platform |
| 56 | `block/GunDisplayCaseBlock.java:96` | `createBlockStateDefinition/1` → `void` | `block/GunDisplayCaseBlock.java:93` | `registerBlockIcons/1` → `void` | platform |
| 57 | `block/GunPegboardBlock.java:128` | `updateShape/6` → `BlockState` | `block/GunPegboardBlock.java:178` | `onBlockPlacedBy/6` → `void` | platform |
| 58 | `block/GunPegboardBlock.java:133` | `rotate/2` → `BlockState` | `block/GunPegboardBlock.java:122` | `withFacing/2` → `int` | platform |
| 59 | `block/GunPegboardBlock.java:138` | `mirror/2` → `BlockState` | `block/GunPegboardBlock.java:122` | `withFacing/2` → `int` | platform |
| 60 | `block/GunPegboardBlock.java:149` | `use/6` → `InteractionResult` | `block/GunPegboardBlock.java:178` | `onBlockPlacedBy/6` → `void` | platform |
| 61 | `block/GunPegboardBlock.java:190` | `getShape/4` → `VoxelShape` | `block/GunPegboardBlock.java:408` | `getSelectedBoundingBoxFromPool/4` → `AxisAlignedBB` | platform |
| 62 | `block/GunPegboardBlock.java:207` | `getCollisionShape/4` → `VoxelShape` | `block/GunPegboardBlock.java:425` | `getCollisionBoundingBoxFromPool/4` → `AxisAlignedBB` | platform |
| 63 | `block/GunPegboardBlock.java:212` | `onRemove/5` → `void` | `block/GunPegboardBlock.java:272` | `onNeighborBlockChange/5` → `void` | platform |
| 64 | `block/GunPegboardBlock.java:223` | `getRenderShape/1` → `RenderShape` | `block/GunPegboardBlock.java:83` | `registerBlockIcons/1` → `void` | platform |
| 65 | `block/GunPegboardBlock.java:241` | `getCloneItemStack/5` → `ItemStack` | `block/GunPegboardBlock.java:272` | `onNeighborBlockChange/5` → `void` | platform |
| 66 | `block/GunPegboardBlock.java:250` | `getPistonPushReaction/1` → `PushReaction` | `block/GunPegboardBlock.java:83` | `registerBlockIcons/1` → `void` | platform |
| 67 | `block/GunPegboardBlock.java:68` | `createBlockStateDefinition/1` → `void` | `block/GunPegboardBlock.java:83` | `registerBlockIcons/1` → `void` | platform |
| 68 | `block/GunPegboardBlock.java:73` | `newBlockEntity/2` → `BlockEntity` | `block/GunPegboardBlock.java:156` | `createTileEntity/2` → `TileEntity` | platform |
| 69 | `block/GunPegboardBlock.java:79` | `getStateForPlacement/1` → `BlockState` | `block/GunPegboardBlock.java:83` | `registerBlockIcons/1` → `void` | platform |
| 70 | `block/GunSmithTableBlockB.java:34` | `createBlockStateDefinition/1` → `void` | `block/GunSmithTableBlockB.java:42` | `isHead/1` → `boolean` | platform |
| 71 | `block/GunSmithTableBlockB.java:52` | `setPlacedBy/5` → `void` | `block/GunSmithTableBlockB.java:81` | `onNeighborBlockChange/5` → `void` | platform |
| 72 | `block/GunSmithTableBlockB.java:63` | `playerWillDestroy/4` → `void` | `block/GunSmithTableBlockB.java:124` | `getRootPos/4` → `ChunkCoordinates` | platform |
| 73 | `block/GunSmithTableBlockB.java:80` | `updateShape/6` → `BlockState` | `block/GunSmithTableBlockB.java:57` | `onBlockPlacedBy/6` → `void` | platform |
| 74 | `block/GunSmithTableBlockB.java:99` | `getRootPos/2` → `BlockPos` | `block/GunSmithTableBlockB.java:47` | `withHead/2` → `int` | platform |
| 75 | `block/GunSmithTableBlockC.java:30` | `createBlockStateDefinition/1` → `void` | `block/GunSmithTableBlockC.java:41` | `isUpper/1` → `boolean` | platform |
| 76 | `block/GunSmithTableBlockC.java:49` | `setPlacedBy/5` → `void` | `block/GunSmithTableBlockC.java:75` | `onNeighborBlockChange/5` → `void` | platform |
| 77 | `block/GunSmithTableBlockC.java:60` | `updateShape/6` → `BlockState` | `block/GunSmithTableBlockC.java:51` | `onBlockPlacedBy/6` → `void` | platform |
| 78 | `block/GunSmithTableBlockC.java:76` | `playerWillDestroy/4` → `void` | `block/GunSmithTableBlockC.java:110` | `getRootPos/4` → `ChunkCoordinates` | platform |
| 79 | `block/GunSmithTableBlockC.java:98` | `getRootPos/2` → `BlockPos` | `block/GunSmithTableBlockC.java:46` | `withUpper/2` → `int` | platform |
| 80 | `block/StatueBlock.java:108` | `updateShape/6` → `BlockState` | `block/StatueBlock.java:106` | `onBlockPlacedBy/6` → `void` | platform |
| 81 | `block/StatueBlock.java:124` | `onRemove/5` → `void` | `block/StatueBlock.java:131` | `onNeighborBlockChange/5` → `void` | platform |
| 82 | `block/StatueBlock.java:135` | `getRenderShape/1` → `RenderShape` | `block/StatueBlock.java:60` | `registerBlockIcons/1` → `void` | platform |
| 83 | `block/StatueBlock.java:140` | `getPistonPushReaction/1` → `PushReaction` | `block/StatueBlock.java:60` | `registerBlockIcons/1` → `void` | platform |
| 84 | `block/StatueBlock.java:47` | `createBlockStateDefinition/1` → `void` | `block/StatueBlock.java:60` | `registerBlockIcons/1` → `void` | platform |
| 85 | `block/StatueBlock.java:52` | `newBlockEntity/2` → `BlockEntity` | `block/StatueBlock.java:101` | `createTileEntity/2` → `TileEntity` | platform |
| 86 | `block/StatueBlock.java:58` | `use/6` → `InteractionResult` | `block/StatueBlock.java:106` | `onBlockPlacedBy/6` → `void` | platform |
| 87 | `block/StatueBlock.java:85` | `getStateForPlacement/1` → `BlockState` | `block/StatueBlock.java:60` | `registerBlockIcons/1` → `void` | platform |
| 88 | `block/StatueBlock.java:97` | `setPlacedBy/5` → `void` | `block/StatueBlock.java:131` | `onNeighborBlockChange/5` → `void` | platform |
| 89 | `block/TargetBlock.java:100` | `getShape/4` → `VoxelShape` | `block/TargetBlock.java:433` | `getSelectedBoundingBoxFromPool/4` → `AxisAlignedBB` | platform |
| 90 | `block/TargetBlock.java:110` | `tick/4` → `void` | `block/TargetBlock.java:251` | `canBlockStay/4` → `boolean` | platform |
| 91 | `block/TargetBlock.java:118` | `onProjectileHit/4` → `void` | `block/TargetBlock.java:278` | `onBulletHit/4` → `boolean` | platform |
| 92 | `block/TargetBlock.java:141` | `updateShape/6` → `BlockState` | `block/TargetBlock.java:185` | `onBlockPlacedBy/6` → `void` | platform |
| 93 | `block/TargetBlock.java:167` | `getStateForPlacement/1` → `BlockState` | `block/TargetBlock.java:90` | `registerBlockIcons/1` → `void` | platform |
| 94 | `block/TargetBlock.java:179` | `setPlacedBy/5` → `void` | `block/TargetBlock.java:217` | `onNeighborBlockChange/5` → `void` | platform |
| 95 | `block/TargetBlock.java:199` | `getCloneItemStack/5` → `ItemStack` | `block/TargetBlock.java:217` | `onNeighborBlockChange/5` → `void` | platform |
| 96 | `block/TargetBlock.java:219` | `isSignalSource/1` → `boolean` | `block/TargetBlock.java:90` | `registerBlockIcons/1` → `void` | platform |
| 97 | `block/TargetBlock.java:224` | `getSignal/4` → `int` | `block/TargetBlock.java:340` | `onBlockAdded/4` → `void` | platform |
| 98 | `block/TargetBlock.java:229` | `onPlace/5` → `void` | `block/TargetBlock.java:217` | `onNeighborBlockChange/5` → `void` | platform |
| 99 | `block/TargetBlock.java:238` | `getRenderShape/1` → `RenderShape` | `block/TargetBlock.java:90` | `registerBlockIcons/1` → `void` | platform |
| 100 | `block/TargetBlock.java:243` | `getPistonPushReaction/1` → `PushReaction` | `block/TargetBlock.java:90` | `registerBlockIcons/1` → `void` | platform |
| 101 | `block/TargetBlock.java:54` | `getRedstoneStrength/2` → `int` | `block/TargetBlock.java:108` | `withFacing/2` → `int` | platform |
| 102 | `block/TargetBlock.java:85` | `createBlockStateDefinition/1` → `void` | `block/TargetBlock.java:90` | `registerBlockIcons/1` → `void` | platform |
| 103 | `block/TargetBlock.java:90` | `newBlockEntity/2` → `BlockEntity` | `block/TargetBlock.java:180` | `createTileEntity/2` → `TileEntity` | platform |
| 104 | `client/gui/AmmoStorageScreen.java:103` | `mouseReleased/3` → `boolean` | `client/gui/AmmoStorageScreen.java:181` | `mouseMovedOrUp/3` → `void` | platform |
| 105 | `client/gui/AmmoStorageScreen.java:28` | `renderBg/4` → `void` | `client/gui/AmmoStorageScreen.java:174` | `mouseClickMove/4` → `void` | platform |
| 106 | `client/gui/AmmoStorageScreen.java:55` | `render/4` → `void` | `client/gui/AmmoStorageScreen.java:174` | `mouseClickMove/4` → `void` | platform |
| 107 | `client/gui/AmmoStorageScreen.java:62` | `renderTooltip/3` → `void` | `client/gui/AmmoStorageScreen.java:51` | `drawGuiContainerBackgroundLayer/3` → `void` | platform |
| 108 | `client/gui/compat/ClothConfigScreen.java:34` | `init/0` → `void` | `client/gui/compat/ClothConfigScreen.java:75` | `initGui/0` → `void` | platform |
| 109 | `client/gui/components/GunPackList.java:135` | `getScrollbarPosition/0` → `int` | `client/gui/components/GunPackList.java:198` | `getSize/0` → `int` | platform |
| 110 | `client/gui/components/GunPackList.java:139` | `render/4` → `void` | `client/gui/components/GunPackList.java:163` | `updateSize/4` → `void` | platform |
| 111 | `client/gui/components/GunPackList.java:166` | `getRowLeft/0` → `int` | `client/gui/components/GunPackList.java:198` | `getSize/0` → `int` | platform |
| 112 | `client/gui/components/GunPackList.java:170` | `getRowWidth/0` → `int` | `client/gui/components/GunPackList.java:257` | `getListWidth/0` → `int` | platform |
| 113 | `client/gui/components/GunPackList.java:181` | `narratables/0` → `List<?extendsNarratableEntry>` | `client/gui/components/GunPackList.java:194` | `updateCursorCounter/0` → `void` | platform |
| 114 | `client/gui/components/GunPackList.java:193` | `children/0` → `List<?extendsGuiEventListener>` | `client/gui/components/GunPackList.java:194` | `updateCursorCounter/0` → `void` | platform |
| 115 | `client/gui/components/GunPackList.java:232` | `updateWidgetNarration/1` → `void` | `client/gui/components/GunPackList.java:168` | `setLeftPos/1` → `void` | platform |
| 116 | `client/gui/components/GunPackList.java:244` | `renderWidget/4` → `void` | `client/gui/components/GunPackList.java:163` | `updateSize/4` → `void` | platform |
| 117 | `client/gui/GunPackProgressScreen.java:22` | `init/0` → `void` | `client/gui/GunPackProgressScreen.java:42` | `initGui/0` → `void` | platform |
| 118 | `client/gui/GunRefitScreen.java:148` | `isPauseScreen/0` → `boolean` | `client/gui/GunRefitScreen.java:232` | `doesGuiPauseGame/0` → `boolean` | platform |
| 119 | `client/gui/GunRefitScreen.java:422` | `onClose/0` → `void` | `client/gui/GunRefitScreen.java:591` | `onGuiClosed/0` → `void` | platform |
| 120 | `client/gui/GunRefitScreen.java:96` | `init/0` → `void` | `client/gui/GunRefitScreen.java:147` | `initGui/0` → `void` | platform |
| 121 | `client/gui/GunSmithTableScreen.java:360` | `init/0` → `void` | `client/gui/GunSmithTableScreen.java:450` | `initGui/0` → `void` | platform |
| 122 | `client/gui/GunSmithTableScreen.java:842` | `renderLabels/3` → `void` | `client/gui/GunSmithTableScreen.java:806` | `drawScreen/3` → `void` | platform |
| 123 | `client/gui/GunSmithTableScreen.java:858` | `isPauseScreen/0` → `boolean` | `client/gui/GunSmithTableScreen.java:790` | `updateScreen/0` → `void` | platform |
| 124 | `client/init/ClientSetupEvent.java:32` | `onRegisterShaders/1` → `void` | `client/init/ClientSetupEvent.java:414` | `onRenderOverlay/1` → `void` | platform |
| 125 | `client/init/ClientSetupEvent.java:36` | `onClientSetup/1` → `void` | `client/init/ClientSetupEvent.java:414` | `onRenderOverlay/1` → `void` | platform |
| 126 | `client/init/ClientSetupEvent.java:42` | `onClientSetup/1` → `void` | `client/init/ClientSetupEvent.java:414` | `onRenderOverlay/1` → `void` | platform |
| 127 | `client/init/ClientSetupEvent.java:52` | `onRegisterGuiOverlays/1` → `void` | `client/init/ClientSetupEvent.java:398` | `onRenderWorldLast/1` → `void` | platform |
| 128 | `client/init/ClientSetupEvent.java:63` | `onClientSetup/1` → `void` | `client/init/ClientSetupEvent.java:414` | `onRenderOverlay/1` → `void` | platform |
| 129 | `client/init/ClientSetupEvent.java:93` | `onClientResourceReload/1` → `void` | `client/init/ClientSetupEvent.java:398` | `onRenderWorldLast/1` → `void` | platform |
| 130 | `client/input/GunInputHandler.java:36` | `registerKeyMappings/1` → `void` | `client/input/GunInputHandler.java:200` | `isKeyDown/1` → `boolean` | platform |
| 131 | `client/input/GunInputHandler.java:43` | `onKeyPress/1` → `void` | `client/input/GunInputHandler.java:200` | `isKeyDown/1` → `boolean` | platform |
| 132 | `client/input/GunInputHandler.java:57` | `onMousePress/1` → `void` | `client/input/GunInputHandler.java:200` | `isKeyDown/1` → `boolean` | platform |
| 133 | `client/input/GunInputHandler.java:68` | `onMousePressPre/1` → `void` | `client/input/GunInputHandler.java:200` | `isKeyDown/1` → `boolean` | platform |
| 134 | `client/model/functional/BeamRenderer.java:210` | `getLaserBeam/0` → `RenderType` | `client/model/functional/BeamRenderer.java:228` | `applyGlState/0` → `void` | platform |
| 135 | `client/model/functional/BeamRenderer.java:214` | `getLaserBeamEntity/0` → `RenderType` | `client/model/functional/BeamRenderer.java:228` | `applyGlState/0` → `void` | platform |
| 136 | `client/particle/BulletHoleParticle.java:137` | `tick/0` → `void` | `client/particle/BulletHoleParticle.java:208` | `onUpdate/0` → `void` | platform |
| 137 | `client/particle/BulletHoleParticle.java:202` | `getRenderType/0` → `ParticleRenderType` | `client/particle/BulletHoleParticle.java:208` | `onUpdate/0` → `void` | platform |
| 138 | `client/renderer/block/DisplayGunGeometry.java:175` | `renderModel/5` → `void` | `client/renderer/block/DisplayGunGeometry.java:103` | `capture/5` → `boolean` | platform |
| 139 | `client/renderer/entity/EntityBulletRenderer.java:147` | `getTextureLocation/1` → `ResourceLocation` | `client/renderer/entity/EntityBulletRenderer.java:264` | `getEntityTexture/1` → `ResourceLocation` | platform |
| 140 | `client/renderer/entity/TargetMinecartRenderer.java:40` | `getTextureLocation/1` → `ResourceLocation` | `client/renderer/entity/TargetMinecartRenderer.java:62` | `getEntityTexture/1` → `ResourceLocation` | platform |
| 141 | `client/renderer/scope/ScopeLensRenderer.java:416` | `unsetDefaultColor/0` → `void` | `client/renderer/scope/ScopeLensRenderer.java:666` | `centerU/0` → `float` | platform |
| 142 | `client/renderer/scope/ScopeLensRenderer.java:44` | `registerShader/1` → `void` | `client/renderer/scope/ScopeLensRenderer.java:279` | `onRenderTick/1` → `void` | platform |
| 143 | `client/renderer/scope/ScopeThermalRenderer.java:116` | `onWorldStage/1` → `void` | `client/renderer/scope/ScopeThermalRenderer.java:267` | `beginEntity/1` → `void` | platform |
| 144 | `client/renderer/scope/ScopeThermalRenderer.java:290` | `unsetDefaultColor/0` → `void` | `client/renderer/scope/ScopeThermalRenderer.java:188` | `init/0` → `void` | platform |
| 145 | `client/renderer/scope/ScopeThermalRenderer.java:54` | `registerShader/1` → `void` | `client/renderer/scope/ScopeThermalRenderer.java:267` | `beginEntity/1` → `void` | platform |
| 146 | `client/sound/EntityTrackingGunSoundInstance.java:27` | `isStopped/0` → `boolean` | `client/sound/EntityTrackingGunSoundInstance.java:52` | `isDonePlaying/0` → `boolean` | platform |
| 147 | `client/sound/EntityTrackingGunSoundInstance.java:32` | `tick/0` → `void` | `client/sound/EntityTrackingGunSoundInstance.java:52` | `isDonePlaying/0` → `boolean` | platform |
| 148 | `client/sound/GunSoundInstance.java:73` | `getSound/0` → `Sound` | `client/sound/GunSoundInstance.java:142` | `resolve/0` → `void` | platform |
| 149 | `client/sound/GunSoundInstance.java:89` | `getLocation/0` → `ResourceLocation` | `client/sound/GunSoundInstance.java:142` | `resolve/0` → `void` | platform |
| 150 | `client/sound/GunSoundInstance.java:94` | `getPath/0` → `ResourceLocation` | `client/sound/GunSoundInstance.java:142` | `resolve/0` → `void` | platform |
| 151 | `command/RootCommand.java:10` | `register/1` → `void` | `command/RootCommand.java:66` | `getCommandUsage/1` → `String` | platform |
| 152 | `command/sub/AttachmentLockCommand.java:20` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | `command/sub/AttachmentLockCommand.java:77` | `getUsage/0` → `String` | platform |
| 153 | `command/sub/ConfigCommand.java:16` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | `command/sub/ConfigCommand.java:84` | `getUsage/0` → `String` | platform |
| 154 | `command/sub/ConvertCommand.java:13` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | `command/sub/ConvertCommand.java:83` | `getUsage/0` → `String` | platform |
| 155 | `command/sub/DebugCommand.java:16` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | `command/sub/DebugCommand.java:88` | `getUsage/0` → `String` | platform |
| 156 | `command/sub/DummyAmmoCommand.java:20` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | `command/sub/DummyAmmoCommand.java:73` | `getUsage/0` → `String` | platform |
| 157 | `command/sub/HideTooltipPartCommand.java:21` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | `command/sub/HideTooltipPartCommand.java:81` | `getUsage/0` → `String` | platform |
| 158 | `command/sub/OverwriteCommand.java:16` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | `command/sub/OverwriteCommand.java:69` | `getUsage/0` → `String` | platform |
| 159 | `command/sub/PegboardStressCommand.java:35` | `get/0` → `LiteralArgumentBuilder<CommandSourceStack>` | `command/sub/PegboardStressCommand.java:132` | `getUsage/0` → `String` | platform |
| 160 | `compat/kubejs/TimelessKubeJSPlugin.java:48` | `registerBindings/1` → `void` | `compat/kubejs/TimelessKubeJSPlugin.java:68` | `postKubeJSEvent/1` → `boolean` | platform |
| 161 | `compat/kubejs/TimelessKubeJSPlugin.java:60` | `registerRecipeSchemas/1` → `void` | `compat/kubejs/TimelessKubeJSPlugin.java:68` | `postKubeJSEvent/1` → `boolean` | platform |
| 162 | `compat/kubejs/TimelessKubeJSPlugin.java:65` | `registerRecipeComponents/1` → `void` | `compat/kubejs/TimelessKubeJSPlugin.java:68` | `postKubeJSEvent/1` → `boolean` | platform |
| 163 | `compat/kubejs/TimelessKubeJSPlugin.java:74` | `onItemRegister/1` → `void` | `compat/kubejs/TimelessKubeJSPlugin.java:68` | `postKubeJSEvent/1` → `boolean` | platform |
| 164 | `crafting/AmmoStorageUpgradeRecipe.java:75` | `getResultItem/1` → `ItemStack` | `crafting/AmmoStorageUpgradeRecipe.java:106` | `getCraftingResult/1` → `ItemStack` | platform |
| 165 | `crafting/AmmoStorageUpgradeRecipe.java:85` | `getRecipeWidth/0` → `int` | `crafting/AmmoStorageUpgradeRecipe.java:133` | `getRecipeSize/0` → `int` | platform |
| 166 | `crafting/AmmoStorageUpgradeRecipe.java:90` | `getRecipeHeight/0` → `int` | `crafting/AmmoStorageUpgradeRecipe.java:133` | `getRecipeSize/0` → `int` | platform |
| 167 | `crafting/GunSmithTableRecipe.java:47` | `getResultItem/1` → `ItemStack` | `crafting/GunSmithTableRecipe.java:96` | `register/1` → `void` | platform |
| 168 | `crafting/GunSmithTableRecipe.java:57` | `getSerializer/0` → `RecipeSerializer<?>` | `crafting/GunSmithTableRecipe.java:123` | `clear/0` → `void` | platform |
| 169 | `crafting/GunSmithTableRecipe.java:62` | `getType/0` → `RecipeType<?>` | `crafting/GunSmithTableRecipe.java:123` | `clear/0` → `void` | platform |
| 170 | `crafting/GunSmithTableSerializer.java:20` | `fromJson/2` → `GunSmithTableRecipe` | `crafting/GunSmithTableSerializer.java:41` | `deserialize/2` → `GunSmithTableRecipe` | platform |
| 171 | `crafting/GunSmithTableSerializer.java:29` | `fromNetwork/2` → `GunSmithTableRecipe` | `crafting/GunSmithTableSerializer.java:41` | `deserialize/2` → `GunSmithTableRecipe` | platform |
| 172 | `crafting/GunSmithTableSerializer.java:44` | `toNetwork/2` → `void` | `crafting/GunSmithTableSerializer.java:41` | `deserialize/2` → `GunSmithTableRecipe` | platform |
| 173 | `entity/EntityKineticBullet.java:238` | `defineSynchedData/0` → `void` | `entity/EntityKineticBullet.java:255` | `onUpdate/0` → `void` | platform |
| 174 | `entity/EntityKineticBullet.java:242` | `tick/0` → `void` | `entity/EntityKineticBullet.java:255` | `onUpdate/0` → `void` | platform |
| 175 | `entity/EntityKineticBullet.java:387` | `of/1` → `MaybeMultipartEntity` | `entity/EntityKineticBullet.java:807` | `setOwner/1` → `void` | platform |
| 176 | `entity/EntityKineticBullet.java:606` | `getAddEntityPacket/0` → `Packet<ClientGamePacketListener>` | `entity/EntityKineticBullet.java:892` | `entityInit/0` → `void` | platform |
| 177 | `entity/EntityKineticBullet.java:677` | `getRandom/0` → `RandomSource` | `entity/EntityKineticBullet.java:255` | `onUpdate/0` → `void` | platform |
| 178 | `entity/EntityKineticBullet.java:747` | `ownedBy/1` → `boolean` | `entity/EntityKineticBullet.java:807` | `setOwner/1` → `void` | platform |
| 179 | `entity/TargetMinecart.java:107` | `destroy/1` → `void` | `entity/TargetMinecart.java:146` | `killMinecart/1` → `void` | platform |
| 180 | `entity/TargetMinecart.java:87` | `isInvulnerableTo/1` → `boolean` | `entity/TargetMinecart.java:146` | `killMinecart/1` → `void` | platform |
| 181 | `entity/TargetMinecart.java:97` | `shouldRenderAtSqrDistance/1` → `boolean` | `entity/TargetMinecart.java:146` | `killMinecart/1` → `void` | platform |
| 182 | `event/SyncedEntityDataEvent.java:26` | `attachCapabilities/1` → `void` | `event/SyncedEntityDataEvent.java:73` | `onEntityJoinWorld/1` → `void` | platform |
| 183 | `init/CommandRegistry.java:9` | `onServerStaring/1` → `void` | `init/CommandRegistry.java:54` | `registerCommands/1` → `void` | platform |
| 184 | `init/CommonRegistry.java:18` | `onSetupEvent/1` → `void` | `init/CommonRegistry.java:134` | `onEntityConstructing/1` → `void` | platform |
| 185 | `init/CommonRegistry.java:28` | `onLoadComplete/1` → `void` | `init/CommonRegistry.java:134` | `onEntityConstructing/1` → `void` | platform |
| 186 | `init/CommonRegistry.java:37` | `registerAttributes/1` → `void` | `init/CommonRegistry.java:134` | `onEntityConstructing/1` → `void` | platform |
| 187 | `init/CommonRegistry.java:44` | `onAddPackFinders/1` → `void` | `init/CommonRegistry.java:134` | `onEntityConstructing/1` → `void` | platform |
| 188 | `init/ModParticles.java:25` | `codec/0` → `Codec<T>` | `init/ModParticles.java:14` | `register/0` → `void` | platform |
| 189 | `init/ModRecipe.java:24` | `toString/0` → `String` | `init/ModRecipe.java:52` | `register/0` → `void` | platform |
| 190 | `inventory/AmmoStorageMenu.java:144` | `quickMoveStack/2` → `ItemStack` | `inventory/AmmoStorageMenu.java:304` | `transferStackInSlot/2` → `ItemStack` | platform |
| 191 | `inventory/AmmoStorageMenu.java:174` | `stillValid/1` → `boolean` | `inventory/AmmoStorageMenu.java:334` | `canInteractWith/1` → `boolean` | platform |
| 192 | `inventory/AmmoStorageMenu.java:75` | `broadcastChanges/0` → `void` | `inventory/AmmoStorageMenu.java:182` | `detectAndSendChanges/0` → `void` | platform |
| 193 | `inventory/GunSmithTableMenu.java:76` | `quickMoveStack/2` → `ItemStack` | `inventory/GunSmithTableMenu.java:155` | `transferStackInSlot/2` → `ItemStack` | platform |
| 194 | `inventory/GunSmithTableMenu.java:81` | `stillValid/1` → `boolean` | `inventory/GunSmithTableMenu.java:124` | `canSelectRecipe/1` → `boolean` | platform |
| 195 | `item/AmmoBoxItem.java:100` | `overrideStackedOnOther/4` → `boolean` | `item/AmmoBoxItem.java:198` | `addInformation/4` → `void` | platform |
| 196 | `item/AmmoBoxItem.java:188` | `isBarVisible/1` → `boolean` | `item/AmmoBoxItem.java:156` | `showDurabilityBar/1` → `boolean` | platform |
| 197 | `item/AmmoBoxItem.java:196` | `getBarWidth/1` → `int` | `item/AmmoBoxItem.java:68` | `registerIcons/1` → `void` | platform |
| 198 | `item/AmmoBoxItem.java:208` | `getName/1` → `Component` | `item/AmmoBoxItem.java:130` | `getItemStackDisplayName/1` → `String` | platform |
| 199 | `item/AmmoBoxItem.java:230` | `isFoil/1` → `boolean` | `item/AmmoBoxItem.java:68` | `registerIcons/1` → `void` | platform |
| 200 | `item/AmmoBoxItem.java:238` | `getBarColor/1` → `int` | `item/AmmoBoxItem.java:68` | `registerIcons/1` → `void` | platform |
| 201 | `item/AmmoBoxItem.java:243` | `fillItemCategory/1` → `void` | `item/AmmoBoxItem.java:86` | `getStatue/1` → `float` | platform |
| 202 | `item/AmmoBoxItem.java:257` | `getTooltipImage/1` → `Optional<TooltipComponent>` | `item/AmmoBoxItem.java:68` | `registerIcons/1` → `void` | platform |
| 203 | `item/AmmoBoxItem.java:274` | `appendHoverText/4` → `void` | `item/AmmoBoxItem.java:198` | `addInformation/4` → `void` | platform |
| 204 | `item/AmmoBoxItem.java:60` | `getStatue/4` → `float` | `item/AmmoBoxItem.java:198` | `addInformation/4` → `void` | platform |
| 205 | `item/AmmoItem.java:39` | `getMaxStackSize/1` → `int` | `item/AmmoItem.java:53` | `getItemStackLimit/1` → `int` | platform |
| 206 | `item/AmmoItem.java:48` | `getName/1` → `Component` | `item/AmmoItem.java:63` | `getItemStackDisplayName/1` → `String` | platform |
| 207 | `item/AmmoItem.java:73` | `initializeClient/1` → `void` | `item/AmmoItem.java:42` | `registerIcons/1` → `void` | platform |
| 208 | `item/AmmoStorageBlockItem.java:36` | `getName/1` → `Component` | `item/AmmoStorageBlockItem.java:42` | `getItemStackDisplayName/1` → `String` | platform |
| 209 | `item/AmmoStorageBlockItem.java:48` | `appendHoverText/4` → `void` | `item/AmmoStorageBlockItem.java:54` | `addInformation/4` → `void` | platform |
| 210 | `item/AttachmentItem.java:101` | `verifyTagAfterLoad/1` → `void` | `item/AttachmentItem.java:45` | `registerIcons/1` → `void` | platform |
| 211 | `item/AttachmentItem.java:38` | `getName/1` → `Component` | `item/AttachmentItem.java:56` | `getItemStackDisplayName/1` → `String` | platform |
| 212 | `item/AttachmentItem.java:68` | `initializeClient/1` → `void` | `item/AttachmentItem.java:45` | `registerIcons/1` → `void` | platform |
| 213 | `item/AttachmentItem.java:96` | `getTooltipImage/1` → `Optional<TooltipComponent>` | `item/AttachmentItem.java:45` | `registerIcons/1` → `void` | platform |
| 214 | `item/GunPegboardItem.java:45` | `appendHoverText/4` → `void` | `item/GunPegboardItem.java:103` | `addInformation/4` → `void` | platform |
| 215 | `item/GunSmithTableItem.java:32` | `initializeClient/1` → `void` | `item/GunSmithTableItem.java:58` | `getItemStackDisplayName/1` → `String` | platform |
| 216 | `item/GunSmithTableItem.java:61` | `getName/1` → `Component` | `item/GunSmithTableItem.java:58` | `getItemStackDisplayName/1` → `String` | platform |
| 217 | `item/ModernKineticGunItem.java:66` | `inventoryTick/5` → `void` | `item/ModernKineticGunItem.java:128` | `onUpdate/5` → `void` | platform |
| 218 | `item/TargetMinecartItem.java:20` | `useOn/1` → `InteractionResult` | `item/TargetMinecartItem.java:40` | `registerIcons/1` → `void` | platform |
| 219 | `network/message/ClientMessageAmmoStorageClick.java:16` | `decode/1` → `ClientMessageAmmoStorageClick` | `network/message/ClientMessageAmmoStorageClick.java:56` | `write/1` → `void` | platform |
| 220 | `network/message/ClientMessageAmmoStorageInteract.java:36` | `decode/1` → `ClientMessageAmmoStorageInteract` | `network/message/ClientMessageAmmoStorageInteract.java:66` | `write/1` → `void` | platform |
| 221 | `network/message/ClientMessageCraft.java:23` | `decode/1` → `ClientMessageCraft` | `network/message/ClientMessageCraft.java:36` | `write/1` → `void` | platform |
| 222 | `network/message/ClientMessageInstallVirtualAttachment.java:35` | `decode/1` → `ClientMessageInstallVirtualAttachment` | `network/message/ClientMessageInstallVirtualAttachment.java:45` | `write/1` → `void` | platform |
| 223 | `network/message/ClientMessageLaserColor.java:50` | `decode/1` → `ClientMessageLaserColor` | `network/message/ClientMessageLaserColor.java:64` | `write/1` → `void` | platform |
| 224 | `network/message/ClientMessagePlayerAim.java:19` | `decode/1` → `ClientMessagePlayerAim` | `network/message/ClientMessagePlayerAim.java:25` | `write/1` → `void` | platform |
| 225 | `network/message/ClientMessagePlayerCrawl.java:19` | `decode/1` → `ClientMessagePlayerCrawl` | `network/message/ClientMessagePlayerCrawl.java:25` | `write/1` → `void` | platform |
| 226 | `network/message/ClientMessagePlayerShoot.java:32` | `decode/1` → `ClientMessagePlayerShoot` | `network/message/ClientMessagePlayerShoot.java:38` | `write/1` → `void` | platform |
| 227 | `network/message/ClientMessagePlayerUnloadGun.java:29` | `decode/1` → `ClientMessagePlayerUnloadGun` | `network/message/ClientMessagePlayerUnloadGun.java:46` | `write/1` → `void` | platform |
| 228 | `network/message/ClientMessageRefitGun.java:33` | `decode/1` → `ClientMessageRefitGun` | `network/message/ClientMessageRefitGun.java:49` | `write/1` → `void` | platform |
| 229 | `network/message/ClientMessageSetAttachmentVariant.java:39` | `decode/1` → `ClientMessageSetAttachmentVariant` | `network/message/ClientMessageSetAttachmentVariant.java:47` | `write/1` → `void` | platform |
| 230 | `network/message/ClientMessageSetSlotAdapter.java:39` | `decode/1` → `ClientMessageSetSlotAdapter` | `network/message/ClientMessageSetSlotAdapter.java:51` | `write/1` → `void` | platform |
| 231 | `network/message/ClientMessageUnloadAttachment.java:29` | `decode/1` → `ClientMessageUnloadAttachment` | `network/message/ClientMessageUnloadAttachment.java:38` | `write/1` → `void` | platform |
| 232 | `network/message/event/ServerMessageGunDraw.java:32` | `decode/1` → `ServerMessageGunDraw` | `network/message/event/ServerMessageGunDraw.java:33` | `write/1` → `void` | platform |
| 233 | `network/message/event/ServerMessageGunFinishReload.java:29` | `decode/1` → `ServerMessageGunFinishReload` | `network/message/event/ServerMessageGunFinishReload.java:22` | `postEvent/1` → `void` | platform |
| 234 | `network/message/event/ServerMessageGunFire.java:29` | `decode/1` → `ServerMessageGunFire` | `network/message/event/ServerMessageGunFire.java:22` | `postEvent/1` → `void` | platform |
| 235 | `network/message/event/ServerMessageGunFirePost.java:29` | `decode/1` → `ServerMessageGunFirePost` | `network/message/event/ServerMessageGunFirePost.java:22` | `postEvent/1` → `void` | platform |
| 236 | `network/message/event/ServerMessageGunFireSelect.java:29` | `decode/1` → `ServerMessageGunFireSelect` | `network/message/event/ServerMessageGunFireSelect.java:22` | `postEvent/1` → `void` | platform |
| 237 | `network/message/event/ServerMessageGunHurt.java:50` | `decode/1` → `ServerMessageGunHurt` | `network/message/event/ServerMessageGunHurt.java:51` | `write/1` → `void` | platform |
| 238 | `network/message/event/ServerMessageGunKill.java:49` | `decode/1` → `ServerMessageGunKill` | `network/message/event/ServerMessageGunKill.java:46` | `write/1` → `void` | platform |
| 239 | `network/message/event/ServerMessageGunMelee.java:29` | `decode/1` → `ServerMessageGunMelee` | `network/message/event/ServerMessageGunMelee.java:22` | `postEvent/1` → `void` | platform |
| 240 | `network/message/event/ServerMessageGunReload.java:29` | `decode/1` → `ServerMessageGunReload` | `network/message/event/ServerMessageGunReload.java:22` | `postEvent/1` → `void` | platform |
| 241 | `network/message/event/ServerMessageGunShoot.java:29` | `decode/1` → `ServerMessageGunShoot` | `network/message/event/ServerMessageGunShoot.java:22` | `postEvent/1` → `void` | platform |
| 242 | `network/message/handshake/ServerMessageSyncedEntityDataMapping.java:42` | `decode/1` → `ServerMessageSyncedEntityDataMapping` | `network/message/handshake/ServerMessageSyncedEntityDataMapping.java:66` | `write/1` → `void` | platform |
| 243 | `network/message/ServerMessageAmmoStorageContents.java:34` | `decode/1` → `ServerMessageAmmoStorageContents` | `network/message/ServerMessageAmmoStorageContents.java:72` | `write/1` → `void` | platform |
| 244 | `network/message/ServerMessageBulletHitBlock.java:61` | `decode/1` → `ServerMessageBulletHitBlock` | `network/message/ServerMessageBulletHitBlock.java:78` | `write/1` → `void` | platform |
| 245 | `network/message/ServerMessageCraft.java:22` | `decode/1` → `ServerMessageCraft` | `network/message/ServerMessageCraft.java:32` | `write/1` → `void` | platform |
| 246 | `network/message/ServerMessageGunUnload.java:27` | `decode/1` → `ServerMessageGunUnload` | `network/message/ServerMessageGunUnload.java:31` | `write/1` → `void` | platform |
| 247 | `network/message/ServerMessageLevelUp.java:25` | `decode/1` → `ServerMessageLevelUp` | `network/message/ServerMessageLevelUp.java:30` | `write/1` → `void` | platform |
| 248 | `network/message/ServerMessageSound.java:42` | `decode/1` → `ServerMessageSound` | `network/message/ServerMessageSound.java:49` | `write/1` → `void` | platform |
| 249 | `network/message/ServerMessageSyncGunPack.java:64` | `decode/1` → `ServerMessageSyncGunPack` | `network/message/ServerMessageSyncGunPack.java:111` | `write/1` → `void` | platform |
| 250 | `network/message/ServerMessageUpdateEntityData.java:30` | `decode/1` → `ServerMessageUpdateEntityData` | `network/message/ServerMessageUpdateEntityData.java:40` | `write/1` → `void` | platform |
| 251 | `network/NetworkHandler.java:131` | `registerAcknowledge/0` → `void` | `network/NetworkHandler.java:421` | `getOnlinePlayers/0` → `List<EntityPlayerMP>` | platform |
| 252 | `particles/BulletHoleOption.java:109` | `getType/0` → `ParticleType<?>` | `particles/BulletHoleOption.java:79` | `getX/0` → `int` | platform |
| 253 | `particles/BulletHoleOption.java:89` | `getPos/0` → `BlockPos` | `particles/BulletHoleOption.java:79` | `getX/0` → `int` | platform |
| 254 | `resource/CommonAssetsManager.java:295` | `onReload/1` → `void` | `resource/CommonAssetsManager.java:486` | `onWorldUnload/1` → `void` | platform |
| 255 | `resource/CommonAssetsManager.java:305` | `onReload/1` → `void` | `resource/CommonAssetsManager.java:486` | `onWorldUnload/1` → `void` | platform |
| 256 | `resource/CommonAssetsManager.java:322` | `onServerStopped/1` → `void` | `resource/CommonAssetsManager.java:486` | `onWorldUnload/1` → `void` | platform |
| 257 | `resource/CommonAssetsManager.java:328` | `OnDatapackSync/1` → `void` | `resource/CommonAssetsManager.java:486` | `onWorldUnload/1` → `void` | platform |
| 258 | `resource/CommonAssetsManager.java:92` | `reloadAndRegister/1` → `void` | `resource/CommonAssetsManager.java:471` | `onPlayerLoggedIn/1` → `void` | platform |
| 259 | `resource/GunPackLoader.java:122` | `getRootResource/1` → `IoSupplier<InputStream>` | `resource/GunPackLoader.java:222` | `injectGunPackLanguage/1` → `void` | platform |
| 260 | `resource/GunPackLoader.java:134` | `getModIcon/1` → `Path` | `resource/GunPackLoader.java:222` | `injectGunPackLanguage/1` → `void` | platform |
| 261 | `resource/GunPackLoader.java:54` | `loadPacks/1` → `void` | `resource/GunPackLoader.java:196` | `readPackData/1` → `Map<ResourceLocation,JsonObject>` | platform |
| 262 | `resource/GunPackLoader.java:63` | `discoverExtensions/0` → `Pack` | `resource/GunPackLoader.java:84` | `reloadPackList/0` → `void` | platform |
| 263 | `resource/GunPackLoader.java:97` | `resolve/1` → `Path` | `resource/GunPackLoader.java:222` | `injectGunPackLanguage/1` → `void` | platform |
| 264 | `util/block/ProjectileExplosion.java:56` | `explode/0` → `void` | `util/block/ProjectileExplosion.java:93` | `getHitPlayers/0` → `Map<EntityPlayer,Vec3>` | platform |

> 注：上表「定性」列统一填 `platform` 是逐行人工核对后的结论（全部为 1.20 平台 API/事件/渲染钩子改名；
> 典型：`use→onBlockActivated`、`appendHoverText→addInformation`、`inventoryTick→onUpdate`、
> `newBlockEntity→createTileEntity`、`load/saveAdditional→readFromNBT/writeToNBT`、`getUpdatePacket→getDescriptionPacket`、
> `init→initGui`、`isPauseScreen→doesGuiPauseGame`、`onClose→onGuiClosed`、`getTextureLocation→getEntityTexture`、
> `quickMoveStack→transferStackInSlot`、`stillValid→canInteractWith`、`broadcastChanges→detectAndSendChanges`、
> `getName→getItemStackDisplayName`、`isBarVisible→showDurabilityBar`、`getBarWidth→getDurabilityForDisplay`、
> `renderBg→drawGuiContainerBackgroundLayer`、`renderTooltip→drawHoveringText`、`getRowWidth→getListWidth` 等）。

### B6. api/** 全量签名核对（41 条差异，逐条定性）

`api-sig.js` 对 api 包 864 条上游 public/protected 方法与本移植 880 条做「名字+元数+形参类型(规范化平台类型)+返回+static」比对。

| # | 类型 | 上游位置 | 方法 | 上游签名 | 本移植签名 | 定性 |
|---|---|---|---|---|---|---|
| 1 | SIG_DIFF | `api/client/other/IThirdPersonAnimation.java:6` | `animateGunHold` | `LivingEntity\|ModelPart\|ModelPart\|ModelPart\|ModelPart -> void [instance]` | `EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer -> void [instance]@17 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer -> void [instance]@29 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer -> void [instance]@53` | platform：LivingEntity→EntityLivingBase |
| 2 | SIG_DIFF | `api/client/other/IThirdPersonAnimation.java:15` | `animateGunAim` | `LivingEntity\|ModelPart\|ModelPart\|ModelPart\|ModelPart\|float -> void [instance]` | `EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer\|float -> void [instance]@29 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer\|float -> void [instance]@38 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer\|float -> void [instance]@76` | platform：LivingEntity→EntityLivingBase |
| 3 | SIG_DIFF | `api/client/other/ThirdPersonManager.java:16` | `animateGunHold` | `LivingEntity\|ModelPart\|ModelPart\|ModelPart\|ModelPart -> void [instance]` | `EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer -> void [instance]@17 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer -> void [instance]@29 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer -> void [instance]@53` | platform：LivingEntity→EntityLivingBase |
| 4 | SIG_DIFF | `api/client/other/ThirdPersonManager.java:23` | `animateGunAim` | `LivingEntity\|ModelPart\|ModelPart\|ModelPart\|ModelPart\|float -> void [instance]` | `EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer\|float -> void [instance]@29 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer\|float -> void [instance]@38 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer\|float -> void [instance]@76` | platform：LivingEntity→EntityLivingBase |
| 5 | SIG_DIFF | `api/client/other/ThirdPersonManager.java:37` | `animateGunHold` | `LivingEntity\|ModelPart\|ModelPart\|ModelPart\|ModelPart -> void [instance]` | `EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer -> void [instance]@17 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer -> void [instance]@29 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer -> void [instance]@53` | platform：LivingEntity→EntityLivingBase |
| 6 | SIG_DIFF | `api/client/other/ThirdPersonManager.java:58` | `animateGunAim` | `LivingEntity\|ModelPart\|ModelPart\|ModelPart\|ModelPart\|float -> void [instance]` | `EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer\|float -> void [instance]@29 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer\|float -> void [instance]@38 ;; EntityLivingBase\|ModelRenderer\|ModelRenderer\|ModelRenderer\|ModelRenderer\|float -> void [instance]@76` | platform：LivingEntity→EntityLivingBase |
| 7 | SIG_DIFF | `api/entity/ITargetEntity.java:11` | `onProjectileHit` | `Entity\|EntityHitResult\|DamageSource\|float -> void [instance]` | `EntityKineticBullet\|Entity\|DamageSource\|float -> void [instance]@18` | platform：EntityHitResult 无 1.7.10 对应，改传 bullet+entity（PORT_STATUS:212 记录） |
| 8 | MISSING | `api/event/server/AmmoHitBlockEvent.java:34` | `getHitResult` | ` -> BlockHitResult [instance]` | `` | platform：BlockHitResult→坐标+面（类 javadoc:14-25 记录） |
| 9 | SIG_DIFF | `api/item/builder/BlockItemBuilder.java:16` | `create` | `ItemLike -> BlockItemBuilder [static]` | `byte[] -> ByteBuffer [static]@91 ;; int -> ByteBuffer [static]@119 ;; AccessorModel -> AccessorData [static]@46 ;; Item -> BlockItemBuilder [static]@27 ;; Block -> BlockItemBuilder [static]@31` | 新增/平台适配（见类 javadoc） |
| 10 | SIG_DIFF | `api/item/gun/AbstractGunItem.java:117` | `doBulletSpread` | `ShooterDataHolder\|ItemStack\|LivingEntity\|Projectile\|int\|float\|float\|float\|float -> void [instance]` | `ShooterDataHolder\|ItemStack\|EntityLivingBase\|Entity\|int\|float\|float\|float\|float -> void [instance]@142` | platform：LivingEntity→EntityLivingBase |
| 11 | SIG_DIFF | `api/item/gun/AbstractGunItem.java:237` | `findAndExtractInventoryAmmos` | `IItemHandler\|ItemStack\|int -> int [instance]` | `IInventory\|ItemStack\|int -> int [instance]@345` | platform：IItemHandler→IInventory（1.7.10 无 capability） |
| 12 | SIG_DIFF | `api/item/gun/AbstractGunItem.java:249` | `findAndExtractInventoryAmmo` | `IItemHandler\|ItemStack\|int -> int [instance]` | `IInventory\|ItemStack\|int -> int [instance]@358` | platform：IItemHandler→IInventory（1.7.10 无 capability） |
| 13 | MISSING | `api/item/gun/AbstractGunItem.java:332` | `getName` | `ItemStack -> Component [instance]` | `` | platform：1.20 客户端扩展接口无 1.7.10 对应（改由 client/** 承接） |
| 14 | SIG_DIFF | `api/item/gun/AbstractGunItem.java:347` | `fillItemCategory` | `GunTabType -> NonNullList<ItemStack> [static]` | `GunTabType -> List<ItemStack> [static]@469` | platform：NonNullList→List |
| 15 | SIG_DIFF | `api/item/gun/AbstractGunItem.java:372` | `onEntitySwing` | `ItemStack\|LivingEntity -> boolean [instance]` | `EntityLivingBase\|ItemStack -> boolean [instance]@504` | platform：LivingEntity→EntityLivingBase |
| 16 | MISSING | `api/item/gun/AbstractGunItem.java:380` | `initializeClient` | `Consumer<IClientItemExtensions> -> void [instance]` | `` | platform：1.20 客户端扩展接口无 1.7.10 对应（改由 client/** 承接） |
| 17 | MISSING | `api/item/gun/AbstractGunItem.java:385` | `getCustomRenderer` | ` -> BlockEntityWithoutLevelRenderer [instance]` | `` | platform：1.20 客户端扩展接口无 1.7.10 对应（改由 client/** 承接） |
| 18 | MISSING | `api/item/gun/AbstractGunItem.java:395` | `getTooltipImage` | `ItemStack -> Optional<TooltipComponent> [instance]` | `` | platform：1.20 客户端扩展接口无 1.7.10 对应（改由 client/** 承接） |
| 19 | SIG_DIFF | `api/item/gun/GunItemManager.java:10` | `registerGunItem` | `String\|RegistryObject<?extendsAbstractGunItem> -> void [static]` | `String\|AbstractGunItem -> void [static]@22` | platform：DeferredRegister/RegistryObject→Item 实例（类 javadoc 记录） |
| 20 | MISSING | `api/item/gun/GunItemManager.java:17` | `getGunItemRegistryObject` | `String -> RegistryObject<?extendsAbstractGunItem> [static]` | `` | platform：DeferredRegister/RegistryObject→Item 实例（类 javadoc 记录） |
| 21 | SIG_DIFF | `api/item/gun/GunItemManager.java:21` | `getAllGunItems` | ` -> Collection<RegistryObject<?extendsAbstractGunItem>> [static]` | ` -> Collection<?extendsAbstractGunItem> [static]@33` | platform：DeferredRegister/RegistryObject→Item 实例（类 javadoc 记录） |
| 22 | ADDED | `api/GunProperties.java` | `memoize/1` | `` | `Supplier<T> -> <T>Supplier<T> [static]@47` | 新增 helper（类 javadoc 记录理由） |
| 23 | ADDED | `api/GunProperties.java` | `get/0` | `` | ` -> T [instance]@52` | 新增 helper（类 javadoc 记录理由） |
| 24 | ADDED | `api/event/server/AmmoHitBlockEvent.java` | `getHitX/0` | `` | ` -> int [instance]@53` | 新增/平台适配（见类 javadoc） |
| 25 | ADDED | `api/event/server/AmmoHitBlockEvent.java` | `getHitY/0` | `` | ` -> int [instance]@57` | 新增/平台适配（见类 javadoc） |
| 26 | ADDED | `api/event/server/AmmoHitBlockEvent.java` | `getHitZ/0` | `` | ` -> int [instance]@61` | 新增/平台适配（见类 javadoc） |
| 27 | ADDED | `api/event/server/AmmoHitBlockEvent.java` | `getSide/0` | `` | ` -> int [instance]@65` | 新增/平台适配（见类 javadoc） |
| 28 | ADDED | `api/event/server/AmmoHitBlockEvent.java` | `getMetadata/0` | `` | ` -> int [instance]@76` | 新增/平台适配（见类 javadoc） |
| 29 | ADDED | `api/item/gun/AbstractGunItem.java` | `giveItemToPlayer/2` | `` | `EntityPlayer\|ItemStack -> void [static]@318` | 新增/平台适配（见类 javadoc） |
| 30 | ADDED | `api/item/gun/AbstractGunItem.java` | `getEntityInventory/1` | `` | `EntityLivingBase -> IInventory [static]@331` | 新增/平台适配（见类 javadoc） |
| 31 | ADDED | `api/item/gun/AbstractGunItem.java` | `getItemStackDisplayName/1` | `` | `ItemStack -> String [instance]@450` | 新增/平台适配（见类 javadoc） |
| 32 | ADDED | `api/item/gun/GunItemManager.java` | `getGunItem/1` | `` | `String -> AbstractGunItem [static]@29` | 新增/平台适配（见类 javadoc） |
| 33 | ADDED | `api/item/nbt/ItemStackNbtHelper.java` | `getOrCreateTag/1` | `` | `ItemStack -> NBTTagCompound [static]@25` | 新增 helper（类 javadoc 记录理由） |
| 34 | ADDED | `api/item/nbt/ItemStackNbtHelper.java` | `getTag/1` | `` | `ItemStack -> NBTTagCompound [static]@40` | 新增 helper（类 javadoc 记录理由） |
| 35 | ADDED | `api/item/nbt/ItemStackNbtHelper.java` | `hasTag/1` | `` | `ItemStack -> boolean [static]@48` | 新增 helper（类 javadoc 记录理由） |
| 36 | ADDED | `api/item/nbt/ItemStackNbtHelper.java` | `isEmpty/1` | `` | `ItemStack -> boolean [static]@55` | 新增 helper（类 javadoc 记录理由） |
| 37 | ADDED | `api/item/nbt/ItemStackNbtHelper.java` | `save/2` | `` | `ItemStack\|NBTTagCompound -> void [static]@62` | 新增 helper（类 javadoc 记录理由） |
| 38 | ADDED | `api/item/nbt/ItemStackNbtHelper.java` | `writeEmptyTag/1` | `` | `NBTTagCompound -> void [static]@73` | 新增 helper（类 javadoc 记录理由） |
| 39 | ADDED | `api/item/nbt/ItemStackNbtHelper.java` | `isPlaceholderTag/1` | `` | `NBTTagCompound -> boolean [static]@94` | 新增 helper（类 javadoc 记录理由） |
| 40 | ADDED | `api/util/ResourceLocationHelper.java` | `tryParse/1` | `` | `String -> ResourceLocation [static]@17` | 新增 helper（类 javadoc 记录理由） |
| 41 | ADDED | `api/util/ResourceLocationHelper.java` | `parseOrDefault/2` | `` | `String\|ResourceLocation -> ResourceLocation [static]@35` | 新增 helper（类 javadoc 记录理由） |

## C. 字段 / `@SerializedName` 契约（数据驱动字段名漂移检查）

两侧按「文件 + 字段名 + `@SerializedName` 值」比对（501 个同名文件、上游 4209 / 本移植 5396 个字段声明）：

| 指标 | 条数 |
|---|---|
| SERIALIZED_DIFF（同名字段但 @SerializedName 值不同） | **0** |
| FIELD_MISSING（上游字段本移植同名文件内无） | 156 |
| FIELD_ADDED（本移植新增字段） | 660 |

**关键：`@SerializedName` 值漂移 = 0；`resource/pojo/**` 的 172 个 `@SerializedName` 字段两侧完全一致**
（`resource/pojo/**` + `client/resource/pojo/**` 范围内 FIELD_MISSING=0，FIELD_ADDED 仅 9 个实现缓存字段）。

数据驱动区（`resource/**` + `client/resource/**`）全部字段差异（9 条，均为新增实现字段）：

| 类型 | 本移植位置 | 字段 | 说明 |
|---|---|---|---|
| FIELD_ADDED | `client/resource/ClientIndexManager.java:294` | `items` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `client/resource/ClientIndexManager.java:295` | `length` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `client/resource/GunDisplayInstance.java:102` | `slotTexture` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `client/resource/GunDisplayInstance.java:103` | `hudTexture` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `client/resource/manager/PackInfoManager.java:49` | `ASSETS_PREFIX` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `client/resource/manager/PackInfoManager.java:105` | `suffix` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `client/resource/pojo/display/gun/AdsCurve.java:29` | `resolvedEase` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `client/resource/pojo/display/gun/AdsCurve.java:30` | `easeResolved` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `client/resource/pojo/display/gun/RangefinderManager.java:160` | `blockMiss` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_MISSING | `resource/CommonAssetsManager.java:305` | `recipeManager` | 内部字段随机制替换改名 |
| FIELD_MISSING | `resource/GunPackLoader.java:53` | `packType` | 内部字段随机制替换改名 |
| FIELD_ADDED | `resource/GunPackLoader.java:74` | `PACK_DIR_NAME` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `resource/GunPackLoader.java:263` | `suffix` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_MISSING | `resource/manager/AttachmentsTagManager.java:33` | `networkCache` | 内部字段随机制替换改名 |
| FIELD_MISSING | `resource/manager/CommonDataManager.java:21` | `networkCache` | 内部字段随机制替换改名 |
| FIELD_MISSING | `resource/manager/JsonDataManager.java:43` | `fileToIdConverter` | 内部字段随机制替换改名 |
| FIELD_ADDED | `resource/manager/JsonDataManager.java:67` | `directory` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `resource/manager/JsonDataManager.java:68` | `root` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_MISSING | `resource/manager/LazyJsonDataManager.java:59` | `fileToIdConverter` | 内部字段随机制替换改名 |
| FIELD_ADDED | `resource/manager/LazyJsonDataManager.java:74` | `directory` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_ADDED | `resource/manager/LazyJsonDataManager.java:75` | `root` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |
| FIELD_MISSING | `resource/manager/RecipeFilterManager.java:33` | `networkCache` | 内部字段随机制替换改名 |
| FIELD_ADDED | `resource/manager/ScriptManager.java:71` | `root` | 新增实现缓存字段，不参与 JSON 反序列化（无 @SerializedName） |

其它区块的 FIELD_MISSING 主要是**加载器/渲染器内部字段随机制替换改名**（如 `fileToIdConverter`、`networkCache`、
`recipeManager`），不构成 JSON/网络字段契约漂移。

## D. 配置键核对（89 键）

上游 `config/**` 全部 89 个 `builder.define*/defineInRange/defineEnum/defineList*` 配置键，在本移植 `config/**` 中**逐键存在且默认值/范围一致**；

机检方式：抽取每个 define*(...) 调用从 key 到右括号的完整参数串并规范化（Lists.newArrayList() 与 new ArrayList<>() 归一化），逐键比对 default/range/enum：89 键中 87 键参数串完全等价；2 键仅构造器写法或校验器形态不同但语义一致：

| 配置键 | 上游 | 本移植 | 判定 |
|---|---|---|---|
| AmmoConfig.DestroyBlocks | Lists.newArrayList(DEFAULT_DESTROY_BLOCKS) | new java.util.ArrayList<>(DEFAULT_DESTROY_BLOCKS) | 等价（同一默认列表） |
| AmmoConfig.BulletDamageIgnoreList | validator: value instanceof String id && ResourceLocation.tryParse(id) != null | validator: AmmoConfig::isValidResourceLocation（实现 AmmoConfig.java:122-130，try/catch new ResourceLocation） | 等价（同为「合法 ResourceLocation 字符串」判定；1.7.10 无 tryParse） |

仅 `SyncConfig.CLIENT_GUN_PACK_DOWNLOAD_URLS` 类型由 `List<List<String>>` 收敛为 `List<String>` 并在源码注释记录（上游该项已 @Deprecated，
且 1.7.10 `Configuration` 只有一维列表）。核对脚本正则：`\.define(?:InRange|Enum|List|ListAllowEmpty)?\s*\(\s*(?:new\s+[^,()]+,\s*)?"([^"]+)"`。

## E. YSM 硬契约逐符号核对（11 组）

| # | 契约符号（`docs/YSM_COMPAT_HANDOFF.md`） | 上游 | 本移植 | 结论 |
|---|---|---|---|---|
| 1 | `TimelessAPI#getCommonGunIndex(ResourceLocation)` → `Optional<CommonGunIndex>` | `api/TimelessAPI.java:143` | `api/TimelessAPI.java:158` | ✅ 一致 |
| 2 | `IGun#getIGunOrNull(ItemStack)` static | `api/item/IGun.java:28` | `api/item/IGun.java:31` | ✅ 一致（`@Nullable`，1.7.10 空栈=null） |
| 3 | `IGun#mainhandHoldGun(LivingEntity)` static | `api/item/IGun.java:42` | `api/item/IGun.java:45`（另有正式名 `mainHandHoldGun`:53） | ✅ 名称保留（小写 h 版本在上游就存在） |
| 4 | `IGun#getMainHandFireMode(LivingEntity)` static | `api/item/IGun.java:68` | `api/item/IGun.java:76` | ✅ 一致 |
| 5 | `IGun#getGunId(ItemStack)` instance | `api/item/IGun.java:130` | `api/item/IGun.java:141` | ✅ 一致 |
| 6 | `GunTabType`（PISTOL/RPG/RIFLE/…） | `api/item/GunTabType.java` | `api/item/GunTabType.java` | ✅ 逐字一致（7 常量 + @SerializedName） |
| 7 | `FireMode`（UNKNOWN…）/`fromName` | `api/item/gun/FireMode.java` | `api/item/gun/FireMode.java` | ✅ 除注释外逐行一致 |
| 8 | `IGunOperator#fromLivingEntity` + `getSynAimingProgress/getSynShootCoolDown/getSynReloadState/getSynMeleeCoolDown/getSynDrawCoolDown` | `api/entity/IGunOperator.java` | `api/entity/IGunOperator.java` | ✅ 全部存在；仅 `LivingEntity→EntityLivingBase`（不可避平台替换） |
| 9 | `IClientPlayerGunOperator#getClientShootCoolDown()` | `api/client/gameplay/IClientPlayerGunOperator.java` | 同文件 `:98` | ✅ 一致（`@OnlyIn→@SideOnly`） |
| 10 | `GunFireEvent#getShooter` / `GunMeleeEvent#getShooter` / `GunReloadEvent#getEntity` | `api/event/common/*` | `api/event/common/*:30` | ✅ 一致；`LogicalSide→cpw Side`（交接文档 §4 已写明）；三事件双端 post：上游 6 处 / 本移植 6 处一一对应 |
| 11 | `MuzzleFlashRender.isSelf` / `ShellRender.isSelf` 为 `public static boolean` | `client/model/functional/{MuzzleFlashRender:38,ShellRender:32}` | `client/model/functional/{MuzzleFlashRender:47,ShellRender:39}` | ✅ 字段名/类型/可写性一致，javadoc 标注 YSM 契约 |
| 12 | `CommonGunIndex#getType()` | `resource/index/CommonGunIndex.java:135` | `resource/index/CommonGunIndex.java:160` | ✅ 一致（字符串来自 `GunTabType` @SerializedName，值 pistol/rifle/rpg…） |

**结论：YSM 契约 11 组（表内 12 行，其中 3 条属 IGun 组）全部通过；无类名/方法名/字段名/参数顺序漂移。**
唯一平台替换：`LivingEntity→EntityLivingBase`、`LogicalSide→cpw Side`、`@OnlyIn→@SideOnly`（交接文档 §4 已写明，YSM 侧需按 1.7.10 改写）。

## F. 重点区域方法集合核对（任务指定 6 区）

| 区域 | 上游文件/方法数（pub-prot） | 本移植文件/方法数（pub-prot） | 方法名集合差异 | 结论 |
|---|---|---|---|---|
| `entity/shooter/**` | 15 文件 / 47 方法（33） | 15 文件 / 49 方法（34） | 上游独有 **0**；新增 1（`ShooterDataHolder#getCurrentGunItem`，javadoc:158-175 记录 1.7.10 空栈 NPE 修复） | ✅ 状态机方法名 100% 保留 |
| `api/**` | 115 文件 / 864 方法（497） | 117 文件 / 880 方法（511） | 上游独有 6（全 1.20 客户端扩展/BlockHitResult/RegistryObject，平台不可避）+ 20 新增（平台覆写/2 个 helper 类） | ✅ 契约无漂移（见 E） |
| `resource/index/**` | 6 文件 / 51 方法（37） | 6 文件 / 52 方法（37） | 方法名集合差异 **0**；`@SerializedName` 0 漂移；仅 `CommonBlockIndex#getBlock` 返回 `BlockItem→ItemBlock`（平台） | ✅ |
| `resource/serialize/**` | 12 文件 / 18 方法（16） | 15 文件 / 24 方法（23） | 方法名差异 **0**；新增 3 个平台类型适配器（`EnumMapTypeAdapterFactory`/`ItemStackJsonParser`/`ResourceLocationSerializer`，均有类 javadoc） | ✅ |
| `client/resource/**` | 81 文件 / 709 方法（591） | 83 文件 / 731 方法（602） | 上游独有 3（`SimplePreparableReloadListener#prepare/apply` 平台钩子）；新增 5（`IGunPackReloadListener` 形态 + GSON adapter） | ✅ |
| `item/**` | 11 文件 / 158 方法（133） | 12 文件 / 158 方法（134） | 上游独有 33，全部 1.20 Item 覆写（`appendHoverText/inventoryTick/getName/isBarVisible/...`）；新增 1 类（`ItemClientHooks`，平台分端） | ✅ |
| `network/**` | 47 文件 / 185 方法（157） | 52 文件 / 196 方法（168） | 上游独有 92 = 44 `encode` + 44 `decode`（机制改名 `read/write`）+ 2 handshake 注册方法 + 2 record 头误报；新增 2 消息（`AbstractShooterGunEventMessage`/`ServerMessageSyncConfig`）+ 4 适配类 | ✅（机制改名已记录） |

### F2. 方法体规模比对（发现「被简化」的最有效机械手段）

对两侧全部同名方法（各文件内按 名称+元数 配对，共 4688 上游 / 5582 本移植方法体）比较函数体行数：

- 上游方法体 ≥ 8 行、本移植 < 50% 的：**仅 1 条** —— `compat/ar/ARCompat#renderLaser`（9→2 行，反射骨架性质，见类 javadoc）；
- 本移植 > 2.5 倍上游的：26 条，集中在 `SoundPlayManager`（1.7.10 动态音效需自建）、`ScopeLens/ScopeThermal`（1.20 核心 shader → GLSL 手工管线）、`NetworkHandler.init`（逐包注册列表）、
  `ClientAssetsManager.reloadAndRegister`（1.7.10 重载管线）等平台替换点，均可在对应类 javadoc 找到依据。

## G. 复现方式（脚本不入库）

全部为只读脚本，位于 `build/fidelity/`（build/ 不入库）：

| 脚本 | 作用 |
|---|---|
| `extract.js` | 逐文件抽取「包/类/方法声明（可见性、static、名字、元数、形参、返回、行号）」→ `up-symbols.tsv` / `my-symbols.tsv` |
| `compare.js` | 类级 + 方法级（名+元数）差异 → `method-diff.tsv` / `added-methods.tsv` / 类清单 |
| `api-sig.js` | api 包「名字+元数+形参(平台类型规范化)+返回+static」严格比对 → `api-sig-diff.tsv` |
| `rename-candidates.js` | 「上游缺失 × 本移植新增」按元数/返回/名称相似度配对 → `rename-candidates.tsv` |
| `fields.js` + `field-compare.js` | 字段名 + `@SerializedName` 值比对 → `field-diff.tsv` |
| `bodies.js` | 方法体行数比对（找简化） |
| `nested-types.js` | 嵌套类型声明比对（找被丢弃的内部机制，如 `MaybeMultipartEntity`） |
| `class-classify.js` | 类级差异分类 |
| 复现 | `node build/fidelity/extract.js <root> <out> && node build/fidelity/compare.js ...`（全部只读，不跑 gradle） |


---

## H. 快照后变更（D-1 复核闭环，2026-11-04 19:28）

> 本节是**手写附录**，与上文机器生成部分（修复前快照）区分；上文 A–G 的方法/类清单**不含**下列修复引入的
> 私有类与签名变化，其计数以修复前快照为准。

| 项 | 内容 |
|---|---|
| 触发 | `gun-logic`（r2 task-3，`entity/**` owner）就主报告 §一 D-1 完成修复并回信（`r2-03 F8` / 偏离 `D11`） |
| 修复位置 | `entity/EntityKineticBullet.java`：新增私有 `MaybeMultipartEntity`（当前 `:752-777`，`EntityDragonPart.entityDragonObj instanceof Entity` → core=本体，否则 core==hitPart）；`onHitEntity` 在 PreEvent 后 `:491` 取 parts，击退 `:509-516` / 爆炸前清无敌帧 `:517-528` / Post-Kill 事件 `:529-587` 全部改用 `getCore()`；`tacAttackEntity(parts,...)` `:789-799` 伤害仍打 `hitPart`，两次伤害前清 `core.hurtResistantTime = 0`；类 javadoc `:108-110`、方法 javadoc `:743-751`/`:779-788` 引用上游 `:384-394,411,427,592-606` |
| 独立复核（fidelity-audit） | ① parts 解析时机与上游 `:427` 一致（PreEvent 后）；② 伤害 hitPart / 击退·事件·无敌帧用 core，与上游 `:448-480,592-606` 逐项对应；③ 非多段实体 `core==hitPart`，修复前行为逐字保留（无回归面）；④ `mcp__idea__get_file_problems(errorsOnly)` → 0 error（未跑 gradle）；⑤ 复核快照 19:28 |
| 保留的平台行为（不绕过） | 非玩家来源子弹命中龙部件不扣血：1.7.10 原版 `EntityDragon#attackEntityFromPart` 仅 `source.getEntity() instanceof EntityPlayer || source.isExplosion()` 时真正扣血（`build/rfg/minecraft-src/java/net/minecraft/entity/boss/EntityDragon.java:557`），上游 1.20 无此限制 → 按「记录差异而非改成第三种语义」处理，登记 `r2-03 D11` |
| 结论 | **D-1 闭合**。主报告 `docs/review/r2/05-fidelity.md` 的结论速览与 §一已同步更新为「复审时已修复闭环」 |
