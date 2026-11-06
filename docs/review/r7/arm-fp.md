# r7-arm-fp · 第一人称左右手渲染最终修复（task-30 / task-32）

> **写作用域**：`client/model/RenderHelperCompat.java`、`client/model/functional/{RightHandRender,LeftHandRender}.java`、本报告。
> 未改 `AnimateGeoItemRenderer` / `BedrockGunModel`（结论见 §3 方案 B 的否决理由），未改 HUD / `client/gui/**` / `resources`。
> **上游准绳**：`tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/**`；MC 1.20.1 `tmp/minecraft-src-1.20.1/**`；
> 1.7.10 `build/rfg/minecraft-src/java/**`。
> **自查**：IDEA MCP `lint_files(min_severity="error")` = **0 error**（3 个改动文件）。
> **未运行任何 gradle 任务**；**未使用截图定位根因**（按 Lead 指示，截图只能证明 bug 存在）。
> 用户澄清：截图红框是**第一人称手部贴图/模型异常**（不是 HUD），本报告只处理这一条。

---

## 一、结论速览

| 项 | 结论 |
|---|---|
| **根因类别** | **② 手臂盒摆放方式错误（移植特有）**，不是 ① 动画/骨骼数据错、也不是 ③ 缺面真因（剔除/绕序）。 |
| **一句话根因** | 上游把头/手位姿写成「手骨帧」，1.20 的 `PlayerModel` 手臂与 1.7.10 `ModelBiped` 手臂**几何逐值相同**，但 1.7.10 照抄时会把手**肩端**一起塞进手骨帧 —— 静态重放默认包 7 把枪，肩端落在相机附近甚至**相机平面背后**（右臂 z=+0.44~+0.76、左臂 z=−0.11~−0.49，相机空间 −Z 为前）⇒ 12px 刚性臂盒整体跨过相机平面，被近平面（SBM 手部 pass `NEAR_PLANE=0.01`）裁成巨大多边形（「缺面」）、4×12 texel 的皮肤被放大成色块。 |
| **修法** | **腕端仍取上游手骨帧**（= 动画作者摆放的手位：右手握把、左手护木，跟随腰射/开镜/换弹/跑动），**肩端改用原版 1.7.10 第一人称空手手臂的相机空间肩点**（`ItemRenderer#renderItemInFirstPerson:516-543` 的固定手部帧），在两点之间重建手臂位姿（方向 + 长度）。整条手臂恒在相机前方、长度有界 ⇒ 不再被近平面裁、纹理不再被极端放大。 |
| **改了什么** | `RenderHelperCompat`：新增「原版空手手臂肩点」常量 + `tacz$armPartPoint/tacz$buildArmFrame`；`renderFirstPersonArm` 改为「上游腕点 + 原版肩点」重建手臂帧，并在画之前显式把手臂三轴旋转归零（上游 `renderHand:200-204` 语义）。`RightHandRender`/`LeftHandRender` 只更新类注释（位姿仍由它们传出的手骨帧驱动，签名/调用链不变）。 |
| **保留的开关** | `RENDER_FIRST_PERSON_VANILLA_ARM`（默认 `true`=正常画手；`false` 仍是单点 kill switch）、`MIN_HAND_ANCHOR_DISTANCE`（默认 0=关闭，仅给第三方枪包病理数据兜底）。 |
| **平台差异（保留，不当作 bug）** | 1.7.10 只画手臂本体一层（`RenderPlayer#renderFirstPersonArm:455-462` 只画 `bipedRightArm`），没有 1.20 的 sleeve 半透明外层；手臂光照沿用 1.7.10 原版 `enableStandardItemLighting`。 |

---

## 二、链路事实（逐跳 file:line）

| 环节 | 本工程 | 上游 1.20 | 结论 |
|---|---|---|---|
| 手骨功能渲染器注册 | `BedrockGunModel.java:91-93`（`lefthand_pos`/`righthand_pos`） | `BedrockGunModel.java:81-83` | 同构 |
| 手骨帧 → 手臂调用 | `RightHandRender.java:54-88`、`LeftHandRender.java:54-88`：`poseStack.mulPose(ZP(180°))` 后把「手骨帧·Rz180」交给手臂渲染 | `RightHandRender.java:30-49`、`LeftHandRender.java:30-49` | 同构（仅平台 API 替换） |
| 手臂渲染 | `RenderHelperCompat#renderFirstPersonArm:257-329` | `util/RenderHelper#renderFirstPersonArm:99-128` → `PlayerRenderer#renderRightHand/renderHand:187-212` | **上游是「在给定 PoseStack 里画 `PlayerModel` 手臂」；1.7.10 是「在给定 PoseStack 里画 `ModelBiped` 手臂」——几何相同，但 1.7.10 手臂的肩端连同整体一起落位，未做相机空间约束（即本次要修的差异）** |
| 手臂几何 | `ModelBiped.java:57-63`：右 `addBox(-3,-2,-2,4,12,4)` + pivot(-5,2,0)、左 `addBox(-1,-2,-2,4,12,4)` + pivot(5,2,0) | `HumanoidModel.createMesh:77-86` / `PlayerModel.createMesh:64-102`（classic 分支） | **逐值相同（4×12×4px、同 pivot）** |
| 动画/骨骼链 | `BedrockModel.convertPivot:382-406`、`BedrockPart.translateAndRotateAndScale:116-133`、`FirstPersonRenderGunEvent#applyFirstPersonGunTransform:160-309` | 同名同 token（r7-fp 已做 token 级复核；本轮再核 `BedrockAnimatedModel` ratio=1.0000 / `ModelTranslateListener` ratio=1.0000） | **等价，不改** |
| 手臂 12px 长条/1.5 拉伸 | 手骨 `righthand/lefthand` 的 `scale:[1,1.5,1]`（`hk_mp5a5.animation.json:7-16` 等默认包全部枪） | 同资源同动画 | 上游同源；本次重建时不再让这段 scale 叠加到手臂长度上（臂长改为「肩点→腕点」距离） |

**静态重放（默认包 8 把枪，`static_idle`，完整 1.7.10 链，输出「臂盒肩端/腕端在相机空间的位置」）**：

| 枪 | 右臂肩端 z / 腕端 z | 左臂肩端 z / 腕端 z |
|---|---|---|
| hk_mp5a5 | **+0.47** / −0.64 | −0.26 / −1.23 |
| ak47 | **+0.55** / −0.55 | −0.56 / −1.53 |
| m4a1 | **+0.41** / −0.70 | −0.48 / −1.46 |
| m16a4 | **+0.46** / −0.66 | −0.50 / −1.48 |
| type_81 | **+0.51** / −0.60 | −0.39 / −1.36 |
| glock_17 | **+0.15** / −0.96 | **+0.08** / −0.99 |
| m870 | **+0.71** / −0.37 | −0.51 / −1.42 |
| mk14 | **+0.75** / −0.37 | −0.18 / −1.21 |

⇒ **8/8 把枪的右臂肩端都在相机背后（z>0）**，左臂肩端也在眼前 0.1~0.6 格（glock 连左臂肩端都在背后）。
12px 臂盒（0.75 格）必然跨相机平面：近平面裁剪把面切成巨大多边形（= 报告里的「缺面」），
皮肤 UV 被拉伸到屏宽量级（=「大色块」）。
这就是「不是 r7 回归、r6 起就有」的原因：它由**动画数据 + 1.7.10 手臂刚性盒**共同决定，每一版都会重现。

---

## 三、方案对比（为什么选当前修法）

### A（否决）直接对齐上游 `ItemInHandRenderer#renderArmWithItem/renderPlayerArm` 的相机空间手部变换
上游 1.20 的「物品」确实在 `applyItemArmTransform` 的相机空间帧里（`ItemInHandRenderer.java:318-321`），
但 TACZ 的枪走 Forge `RenderHandEvent` **替换掉** `renderArmWithItem`
（`tmp/MinecraftForge-1.20.1/patches/.../ItemInHandRenderer.java.patch:3-16`：事件命中就跳过 `m_109371_`），
所以上游枪/手收到的 PoseStack 是**相机空间 + 手骨帧**，不是 `applyItemArmTransform`。
若照搬 `applyItemArmTransform`，手会脱离枪的握把/护木（验收第 1 条直接失败）。

### B（否决）改 `AnimateGeoItemRenderer`/`BedrockGunModel` 的模型链
手骨帧本身是 TACZ 动画作者的数据（左手腕点静态重放就落在护木节点上，误差 0.09 格），
且 `BedrockAnimatedModel`/`ModelTranslateListener` 与上游 token 等价（§二）⇒ 动模型链等于改上游语义，
风险大且不解决「1.7.10 手臂盒是刚性 12px」这个平台差异。

### C（采纳）腕点保上游、肩点用原版第一人称手臂锚点，两点重建
- **腕点**：`poseStack.last().pose() · tacz$armPartPoint(hand, +10px)` = 上游手骨帧给出的手臂腕端
  ⇒ 手永远在动画作者摆放的位置（握把/护木），腰射/开镜/跑动/换弹时随枪动画移动。
- **肩点**：原版 1.7.10 第一人称空手手臂的肩端（**相机空间常量**），
  帧逐句取 `ItemRenderer.java:516-543` 空手分支（静止时 swing=0、equip=1）：
  `T(0.64,-0.6,-0.72)·Ry(45)·T(-1,3.6,3.5)·Rz(120)·Rx(200)·Ry(-135)·T(5.6,0,0)`，左臂 x 镜像。
- **重建**：`M = T(S)·R(局部+Y→(W−S))·S(1,|W−S|/0.75,1)·T(−Q肩)`，使 `M·Q肩=S`、`M·Q腕=W`
  （`Q` = 含 `ModelRenderer` 的 `rotationPoint` 的臂盒端点，见 `tacz$armPartPoint`）。
  整条手臂落在相机前方（S 的 z=−0.525、W 的 z<0），长度 = 两点距离（0.45~1.5 格），
  皮肤 texel 投到屏上的尺寸回到「前臂」量级；方向由肩→腕自然给出，不需要额外朝向常量。
- **兜底**：腕点 z≥0（手到镜头背后/眼前，必被近平面裁）或两点重合/非有限 → 该帧不画；
  `MIN_HAND_ANCHOR_DISTANCE>0` 时腕点过近仍可跳过（默认 0，不藏症状）。

> 依据等级：`ItemRenderer.java:516-543` 是 1.7.10 原版**唯一**的第一人称皮肤手臂相机空间摆放；
> 1.20 的 `renderPlayerArm`（`ItemInHandRenderer.java:238-264`）同样是「相机空间固定常量 + PlayerModel 手臂」，
> 只是常量不同（`applyItemArmTransform` 体系）。本修法取 1.7.10 侧的原版常量，观感与该平台原版一致。

---

## 四、改动清单（全部 `lint_files(error)=0`）

1. `client/model/RenderHelperCompat.java`
   - 新增 `ARM_BOX_LENGTH=0.75F`、`ARM_LOCAL_AXIS=(0,1,0)`、`ARM_SHOULDER_RIGHT/LEFT`（原版空手手臂肩端，
     由 `tacz$vanillaArmShoulder()` 逐句搭帧求得）、`tacz$armPartPoint(HumanoidArm,float)`、`tacz$buildArmFrame(...)`。
   - `renderFirstPersonArm`：改为「上游腕点 + 原版肩点」重建手臂帧（新 `PoseStack` 承载帧后 `pushToGL`），
     画前显式把 `armRenderer.rotateAngleX/Y/Z` 归零；`MIN_HAND_ANCHOR_DISTANCE` 判断改看腕点；
     原 GL 状态（贴图/lightmap/RESCALE_NORMAL/标准光/关剔除/混合/depthMask）与 `try/finally` 不变。
   - 两个开关 javadoc 同步改写（旧的「T-pose/1.5 拉伸」解释已过时）。
2. `client/model/functional/RightHandRender.java`、`LeftHandRender.java` — 仅类注释：说明这里传出去的仍是
   「手骨帧·Rz(180°)」，位姿重建在 `RenderHelperCompat`（签名/调用链/行为不变）。
3. `docs/review/r7/arm-fp.md` — 本报告。

**未运行 gradle**（构建由 Lead 统一收尾）。

---

## 五、实机验证清单（构建后）

| # | 场景 | 期望 |
|---|---|---|
| V-1 | 第一人称持默认包任意步枪（HK-MP5A5 / AK47 / M4A1）站定腰射 | **右手**在握把/扳机、**左手**在护木；两条手臂是正常前臂（≈屏宽 1/6~1/4），**没有**横贯屏幕的巨面/色块；皮肤纹理是袖口/手部纹路而不是被放大成数条纯色带 |
| V-2 | 同上，快速环视 + 走动 | 手臂跟随枪体摆动，不抖、不闪、不脱离握把；肩端始终在画面外（手臂从屏幕下缘进入） |
| V-3 | 开镜（ADS，任意瞄具） | 双手随枪抬到镜位，右手仍在握把、左手仍在护木；无缺面/穿插 |
| V-4 | 换弹 / 拉栓 / 切枪 | 手随动画轨迹移动（去弹匣/拉栓/收枪），不出现「手停在枪外」或「巨面一闪」；切枪过渡帧手臂消失可接受（`tacz$buildArmFrame` 的 z≥0 兜底） |
| V-5 | 空手 / 非枪械物品 | 走原版 `renderItemInFirstPerson`，本修法不参与（`RENDER_FIRST_PERSON_VANILLA_ARM` 只影响 TACZ 枪械的走手路径），原版手臂观感应不变 |
| V-6 | 第三人称（F5）| 不受影响：本修法只在第一人称手骨功能渲染器里执行 |
| V-7 | 单点回退 | `RenderHelperCompat.RENDER_FIRST_PERSON_VANILLA_ARM=false` → 只显示枪模、无皮肤手臂（kill switch） |

---

## 六、风险 / 已知残留

1. **手臂 roll（绕自身长轴的滚转）取「+Y 到肩→腕方向」的最小旋转**：4×4px 方形截面下滚转只影响
   可见侧面/UV 朝向，不影响位置/尺寸；若实机觉得某一面朝向不自然，可在 `tacz$buildArmFrame` 末尾乘一个
   绕长轴的固定角（一个常量），不需要改链路。
2. **没有 sleeve 外层**：1.7.10 `ModelBiped`/`RenderPlayer#renderFirstPersonArm` 只有本体一层（上游 1.20 画
   `rightArm + rightSleeve` 两层）—— 平台模型差异，保留（加 sleeve 需要新几何，属「发明机制」，不在本轮）。
3. **第三方枪包若把手骨腕点摆到相机背后**：该帧不画手（比画一个跨相机平面的巨面更安全）；
   若枪包动画把腕点摆得极远（>2 格），手臂会被拉长 —— 属数据侧，不在代码兜底。
4. **动画的 `scale:[1,1.5,1]` 不再作用于手臂长度**：这是有意的（臂长改由两点距离决定）；
   若某枪包依赖该 scale 让手臂「够到」护木，腕点位置仍来自手骨帧，不受影响。
5. 本修法**不改变**枪体/瞄准/粒子链路的任何行为；`AnimationRenderContext.hand(player)` 与
   `PlayerModelRenderState` 作用域保留（第三人称姿态隔离不破）。
