package com.tacz.guns.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.ClientTickHelper;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.BeamRenderer;
import com.tacz.guns.client.model.functional.RangefinderImpactPredictionRender;
import com.tacz.guns.client.model.functional.TextShowRender;
import com.tacz.guns.client.renderer.scope.ScopeLensRenderer;
import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.mcshim.BufferBuilder;
import com.tacz.guns.mcshim.BufferUploader;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;
import com.tacz.guns.mcshim.VertexFormat;

public class BedrockAttachmentModel extends BedrockAnimatedModel {

    private static final String SCOPE_VIEW_NODE = "scope_view";
    private static final String SCOPE_BODY_NODE = "scope_body";
    private static final String OCULAR_RING_NODE = "ocular_ring";
    private static final String DIVISION_NODE = "division";
    private static final String OCULAR_NODE = "ocular";
    private static final String OCULAR_SIGHT_NODE = "ocular_sight";
    private static final String OCULAR_SCOPE_NODE = "ocular_scope";
    private static final String RANGEFINDER_IMPACT_PREDICTION_NODE = "rangefinder_impact_prediction_pos";
    private static final Pattern LASER_BEAM_PATTERN = Pattern.compile("^laser_beam(_(\\d+))?$");
    /** 上游 {@code BedrockAttachmentModel} INVERT 圆 / {@code ScopeLensRenderer#buildSurface} 的镜片面 z（-90）。 */
    private static final float SURFACE_Z = -90.0F;

    protected List<List<BedrockPart>> scopeViewPaths;
    protected @Nullable List<BedrockPart> scopeBodyPath;
    protected @Nullable List<BedrockPart> ocularRingPath;
    protected List<List<BedrockPart>> ocularNodePaths;
    protected List<Boolean> isScopeOcular;
    protected List<List<BedrockPart>> divisionNodePaths;
    protected @Nullable List<List<BedrockPart>> laserBeamPaths;

    private @Nullable ItemStack currentGunItem;
    private @Nullable ItemStack attachmentItem;

    private boolean isScope = false;
    private boolean isSight = false;
    private ScopeLensConfig scopeLens = new ScopeLensConfig();
    private float scopeViewRadiusModifier = 1;
    /** r6：无模板附件的降级 WARN 只打一次（见 {@link #warnNoStencilOnce()}）。 */
    private static boolean noStencilWarned;
    /** r6-3：每个附件模型最多打一条「渲染路径」诊断（HAMR「大黑框」定位用，不刷屏）。 */
    private static final Set<BedrockAttachmentModel> LOGGED_RENDER_PATH = Collections
        .newSetFromMap(new IdentityHashMap<>());
    /** r6-3：每个附件模型最多打一条「无模板降级分支」诊断。 */
    private static final Set<BedrockAttachmentModel> LOGGED_FALLBACK = Collections
        .newSetFromMap(new IdentityHashMap<>());
    /** r6-3：每个附件模型最多打一条「division 是否被未裁剪画出」诊断。 */
    private static final Set<BedrockAttachmentModel> LOGGED_DIVISION = Collections
        .newSetFromMap(new IdentityHashMap<>());
    /** r7 实机复核⑤：每个附件模型最多打一条「模板路径诊断」（INVERT 椭圆/capture/lens 节点可见性）。 */
    private static final Set<BedrockAttachmentModel> LOGGED_INVERT = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 复核⑧只读诊断：每个附件模型最多打一条「目镜 stencil 写入覆盖了哪些下标」。 */
    private static final Set<BedrockAttachmentModel> LOGGED_OCULAR_STENCIL = Collections
        .newSetFromMap(new IdentityHashMap<>());
    /** 复核⑧只读诊断：每个附件模型最多打一条「super.render 前仍可见（不受 stencil 裁剪）的节点」。 */
    private static final Set<BedrockAttachmentModel> LOGGED_VISIBLE_PARTS = Collections
        .newSetFromMap(new IdentityHashMap<>());
    /**
     * 复核⑬只读诊断：每个附件模型在**完全开镜帧**（aim≥0.99）打一条 capture 结果/目镜 bounds 诊断。
     *
     * <p>
     * 背景：{@code ScopeLensRenderer#capture} 的失败原因 warnOnce 整局只打第一条，抬枪过渡帧的失败会掩盖
     * 稳定帧的真实原因 ⇒ 无法判断「某个瞄具为何始终没有镜片」。
     */
    private static final Set<BedrockAttachmentModel> LOGGED_STEADY_CAPTURE = Collections
        .newSetFromMap(new IdentityHashMap<>());

    public BedrockAttachmentModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        scopeViewPaths = new ArrayList<>();
        ocularNodePaths = new ArrayList<>();
        isScopeOcular = new ArrayList<>();
        divisionNodePaths = new ArrayList<>();
        laserBeamPaths = new ArrayList<>();
        // 初始化 view 的 node path
        List<BedrockPart> path = getPath(modelMap.get(SCOPE_VIEW_NODE));
        int i = 2;
        while (path != null) {
            scopeViewPaths.add(path);
            path = getPath(modelMap.get(SCOPE_VIEW_NODE + '_' + i++));
        }
        // 初始化 ocular 的 node path
        String ocularRegex = "^(" + OCULAR_NODE + "|" + OCULAR_SIGHT_NODE + "|" + OCULAR_SCOPE_NODE + ")(_(\\d+))?$";
        Pattern ocularPattern = Pattern.compile(ocularRegex);
        TreeMap<Integer, OcularWrapper> map = new TreeMap<>();
        for (Map.Entry<String, ModelRendererWrapper> entry : modelMap.entrySet()) {
            Matcher matcher = ocularPattern.matcher(entry.getKey());
            if (matcher.matches()) {
                int num = 1;
                String numStr = matcher.group(3);
                if (numStr != null) {
                    num = Integer.parseInt(numStr);
                }
                String type = matcher.group(1);
                boolean isScope = OCULAR_SCOPE_NODE.equals(type);
                map.put(num, new OcularWrapper(entry.getValue(), isScope));
            }
            if (LASER_BEAM_PATTERN.matcher(entry.getKey())
                .find()) {
                laserBeamPaths.add(getPath(entry.getValue()));
            }
        }
        for (OcularWrapper wrapper : map.values()) {
            ocularNodePaths.add(getPath(wrapper.renderer));
            isScopeOcular.add(wrapper.isScope);
        }
        // 初始化 division 的 node path
        ModelRendererWrapper divisionModel = modelMap.get(DIVISION_NODE);
        path = getPath(modelMap.get(DIVISION_NODE));
        i = 2;
        while (path != null) {
            divisionNodePaths.add(path);
            divisionModel.setHidden(true);
            divisionModel = modelMap.get(DIVISION_NODE + '_' + i++);
            path = getPath(divisionModel);
        }

        scopeBodyPath = getPath(modelMap.get(SCOPE_BODY_NODE));
        ocularRingPath = getPath(modelMap.get(OCULAR_RING_NODE));
    }

    @Nullable
    public List<BedrockPart> getScopeViewPath(int viewSwitchCount) {
        if (scopeViewPaths.isEmpty()) {
            return null;
        }
        if (viewSwitchCount >= scopeViewPaths.size()) {
            return scopeViewPaths.get(0);
        }
        return scopeViewPaths.get(viewSwitchCount);
    }

    public void setIsScope(boolean isScope) {
        this.isScope = isScope;
    }

    public void setIsSight(boolean isSight) {
        this.isSight = isSight;
    }

    public boolean isScope() {
        return isScope;
    }

    public boolean isSight() {
        return isSight;
    }

    public void setScopeLens(ScopeLensConfig scopeLens) {
        this.scopeLens = scopeLens;
    }

    public void setScopeViewRadiusModifier(float scopeViewRadiusModifier) {
        this.scopeViewRadiusModifier = scopeViewRadiusModifier;
    }

    /**
     * 添加枪械自定义的文本显示
     */
    public void setTextShowList(Map<String, TextShow> textShowList) {
        textShowList.forEach(
            (name, textShow) -> this
                .setFunctionalRenderer(name, bedrockPart -> new TextShowRender(this, textShow, currentGunItem)));
        boolean hasRangefinder = textShowList.values()
            .stream()
            .anyMatch(textShow -> textShow.getRangefinder() != null);
        if (hasRangefinder && modelMap.containsKey(RANGEFINDER_IMPACT_PREDICTION_NODE)) {
            this.setFunctionalRenderer(
                RANGEFINDER_IMPACT_PREDICTION_NODE,
                bedrockPart -> new RangefinderImpactPredictionRender(this));
        }
    }

    public void render(@Nullable ItemStack attachmentItem, ItemStack currentGunItem, PoseStack matrixStack,
        ItemDisplayContext transformType, RenderType renderType, int light, int overlay) {
        render(attachmentItem, currentGunItem, matrixStack, transformType, renderType, light, overlay, 1.0F);
    }

    public void render(@Nullable ItemStack attachmentItem, ItemStack currentGunItem, PoseStack matrixStack,
        ItemDisplayContext transformType, RenderType renderType, int light, int overlay, float alpha) {
        render(
            attachmentItem,
            currentGunItem,
            matrixStack,
            transformType,
            renderType,
            light,
            overlay,
            alpha,
            RenderHelperCompat.getBufferSource(transformType));
    }

    public void render(@Nullable ItemStack attachmentItem, ItemStack currentGunItem, PoseStack matrixStack,
        ItemDisplayContext transformType, RenderType renderType, int light, int overlay, float alpha,
        MultiBufferSource bufferSource) {
        this.currentGunItem = currentGunItem;
        this.attachmentItem = attachmentItem;
        if (transformType.firstPerson()) {
            logRenderPathOnce(transformType);
            if (isScope && isSight) {
                renderBoth(matrixStack, transformType, renderType, light, overlay, bufferSource);
            } else if (isScope) {
                renderScope(matrixStack, transformType, renderType, light, overlay, bufferSource);
            } else if (isSight) {
                renderSight(matrixStack, transformType, renderType, light, overlay, bufferSource);
            }
        } else {
            if (scopeBodyPath != null) {
                renderTempPart(
                    matrixStack,
                    transformType,
                    renderType,
                    light,
                    overlay,
                    scopeBodyPath,
                    alpha,
                    bufferSource);
            }
            if (ocularRingPath != null) {
                renderTempPart(
                    matrixStack,
                    transformType,
                    renderType,
                    light,
                    overlay,
                    ocularRingPath,
                    alpha,
                    bufferSource);
            }
        }
        if (!isScope && !isSight && laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, matrixStack, transformType, entry, bufferSource);
            }
        }
        super.render(matrixStack, transformType, renderType, light, overlay, 1.0F, 1.0F, 1.0F, alpha, bufferSource);
        if ((isScope || isSight) && laserBeamPaths != null) {
            for (var entry : laserBeamPaths) {
                BeamRenderer.renderLaserBeam(attachmentItem, matrixStack, transformType, entry, bufferSource);
            }
        }
    }

    private Vector3f getBedrockPartCenter(PoseStack poseStack, @Nonnull List<BedrockPart> path) {
        poseStack.pushPose();
        for (BedrockPart part : path) {
            part.translateAndRotateAndScale(poseStack);
        }
        Vector3f result = new Vector3f(
            poseStack.last()
                .pose()
                .m30(),
            poseStack.last()
                .pose()
                .m31(),
            poseStack.last()
                .pose()
                .m32());
        poseStack.popPose();
        return result;
    }

    // ================================================================================================
    // r7 实机复核⑫ 临时诊断：INVERT 圆到底有没有写进主 FBO 的模板缓冲（只诊断一次；跑完即删）
    // ================================================================================================

    /**
     * 上游 1.20 {@code BedrockAttachmentModel.java:292-300} 的等价实现：**用 mcshim BufferBuilder +
     * BufferUploader**（= 1.20 的 {@code Tesselator.getInstance().getBuilder() + BufferUploader.drawWithShader}）
     * 画 INVERT 扇面，而不是直接操作 {@code Tessellator.instance}。
     *
     * <p>
     * r7 实机复核⑮：Elcan 4x 的 INVERT 扇面用 raw Tessellator 时**一个片元都画不出来**（A/B 里连
     * ALWAYS+REPLACE / identity 矩阵全屏四边形都写不进模板），而同帧其它瞄具正常；改走模型渲染同一条
     * BufferUploader 通道（与上游一致）以排除 raw Tessellator 的客户端数组状态问题。
     */
    private static void drawInvertCircleViaBufferUploader(float centerX, float centerY, float rad) {
        BufferBuilder builder = new BufferBuilder(4096);
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, VertexFormat.POSITION_COLOR);
        builder.vertex(centerX, centerY, SURFACE_Z)
            .color(255, 255, 255, 255)
            .endVertex();
        for (int j = 0; j <= 90; j++) {
            double angle = j * Math.PI * 2 / 90.0;
            builder.vertex(centerX + Math.cos(angle) * rad, centerY + Math.sin(angle) * rad, SURFACE_Z)
                .color(255, 255, 255, 255)
                .endVertex();
        }
        BufferUploader.draw(builder.end());
    }

    /**
     * r7 实机复核⑰：INVERT 圆是**无纹理彩色几何**，与 1.20 的 shader 管线不同，1.7.10 固定管线里
     * 仍可能开着 {@code GL_TEXTURE_2D} + alpha test；此时顶点用上一批遗留的纹理坐标采样，若采到
     * alpha≈0 的像素，整个 INVERT 扇面会在**写模板之前**被 alpha test 丢弃
     * （实测症状：Elcan 4x 模板写不进 ⇒ 镜片/准星全丢；而 {@code glClear} 能写模板、draw 不能
     * ⇒ 丢弃发生在片元阶段，不是模板被禁）。关掉 {@code GL_TEXTURE_2D} 后再画、画完恢复原状态
     * （1.20 无该固定管线状态，属 1.7.10 平台适配）。
     */
    private static void drawInvertCircleNeutralizingFixedFunctionState(float centerX, float centerY, float rad) {
        boolean texture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        if (texture2D) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
        if (alphaTest) {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
        try {
            drawInvertCircleViaBufferUploader(centerX, centerY, rad);
        } finally {
            if (alphaTest) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
            }
            if (texture2D) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
        }
    }

    /**
     * 只读诊断（复核⑧）：`super.render` 之前列出「仍然 visible 且有 cube 的根节点」。
     *
     * <p>
     * 这些节点是**不受 stencil 裁剪**的绘制（`renderScope/renderSight/renderBoth` 末尾已
     * {@code disableItemEntityStencilTest}），若其投影覆盖目镜区域，会被帧末镜片 pass 的快照采到
     * （镜中内容污染：绿色玻璃/镜身/黑面）。正常资产这里应为空（默认枪包只有 `scope_qmk152` 的
     * `bone(32)` 根节点不在 `scope_body` 子树里）。
     */
    private void logUnclippedPartsOnce(String stage) {
        if (!LOGGED_VISIBLE_PARTS.add(this)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (BedrockPart root : shouldRender) {
            int cubes = countVisibleCubes(root);
            if (cubes > 0) {
                sb.append(root.name)
                    .append('(')
                    .append(cubes)
                    .append(") ");
            }
        }
        GunMod.LOGGER.debug(
            "TACZ: scope unclipped parts before super.render ({}): [{}]",
            stage,
            sb.toString()
                .trim());
    }

    /** 递归统计一个节点（含子节点）里仍 visible 的 cube 数。只读诊断用。 */
    private static int countVisibleCubes(BedrockPart part) {
        if (!part.visible) {
            return 0;
        }
        int count = part.cubes.size();
        for (BedrockPart child : part.children) {
            count += countVisibleCubes(child);
        }
        return count;
    }

    private void renderTempPart(PoseStack poseStack, ItemDisplayContext transformType, RenderType renderType, int light,
        int overlay, @Nonnull List<BedrockPart> path, MultiBufferSource bufferSource) {
        renderTempPart(poseStack, transformType, renderType, light, overlay, path, 1.0F, bufferSource);
    }

    private void renderTempPart(PoseStack poseStack, ItemDisplayContext transformType, RenderType renderType, int light,
        int overlay, @Nonnull List<BedrockPart> path, float alpha, MultiBufferSource bufferSource) {
        poseStack.pushPose();
        for (int i = 0; i < path.size() - 1; ++i) {
            path.get(i)
                .translateAndRotateAndScale(poseStack);
        }
        BedrockPart part = path.get(path.size() - 1);
        part.visible = true;
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
        part.render(poseStack, transformType, vertexConsumer, light, overlay, 1.0F, 1.0F, 1.0F, alpha, bufferSource);
        if (!OculusCompat.endBatch(bufferSource)) {
            RenderHelperCompat.endBatch(bufferSource, renderType);
        }
        part.visible = false;
        poseStack.popPose();
    }

    private void renderOcularStencil(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType,
        int light, int overlay, boolean isScope, MultiBufferSource bufferSource) {
        if (!ocularNodePaths.isEmpty()) {
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            // 只读诊断（复核⑧）：记录本次 stencil 写入覆盖了哪些 ocular 下标。
            // 若某个「主目镜」是 `ocular_scope`（isScopeOcular=true）但本模型按 scope-only 渲染
            // （renderScope 传 isScope=false），它会被跳过 ⇒ 主目镜模板值恒 0 ⇒ scope_body 的
            // `GL_EQUAL 0` 会把它画进目镜区域 ⇒ 帧末镜片快照采到镜身/绿色玻璃。
            boolean logOnce = LOGGED_OCULAR_STENCIL.add(this);
            StringBuilder written = logOnce ? new StringBuilder() : null;
            // 绘制目镜
            for (int i = ocularNodePaths.size() - 1; i >= 0; i--) {
                if (isScope == isScopeOcular.get(i)) {
                    if (logOnce) {
                        written.append(i)
                            .append(':')
                            .append(isScope ? "scope" : "sight")
                            .append(' ');
                    }
                    RenderSystem.stencilFunc(GL11.GL_GREATER, i + 1, 0xFF);
                    renderTempPart(
                        matrixStack,
                        transformType,
                        renderType,
                        light,
                        overlay,
                        ocularNodePaths.get(i),
                        bufferSource);
                }
            }
            if (logOnce) {
                GunMod.LOGGER.debug(
                    "TACZ: ocular stencil pass (isScope={}): wrote ocular indices [{}]; ocularCount={}, "
                        + "isScopeOcular={}",
                    isScope,
                    written.toString()
                        .trim(),
                    ocularNodePaths.size(),
                    isScopeOcular);
            }
            // 恢复渲染状态
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
        }
    }

    private void renderDivisionOnly(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType,
        int light, int overlay, MultiBufferSource bufferSource) {
        if (!divisionNodePaths.isEmpty()) {
            RenderSystem.disableDepthTest();
            for (int i = 0; i < divisionNodePaths.size(); i++) {
                RenderSystem.stencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
                renderTempPart(
                    matrixStack,
                    transformType,
                    renderType,
                    light,
                    overlay,
                    divisionNodePaths.get(i),
                    bufferSource);
            }
            RenderSystem.enableDepthTest();
        }
    }

    private void renderOcularAndDivision(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType,
        int light, int overlay, boolean selective, MultiBufferSource bufferSource) {
        if (!ocularNodePaths.isEmpty()) {
            if (!ScopeLensRenderer.isStencilAvailable()) {
                // r6：无模板附件 ⇒ 目镜黑遮罩/分割面无法裁剪，按完整几何画出来就是整块黑矩形
                // （实机证据 docs/review/r6/screenshots/{b,c,d}*.png）。降级为只登记镜片圆盘。
                captureLensesWithoutStencil(matrixStack, selective);
                return;
            }
            // 只读诊断（spec §8.1/§8.2）：每个附件模型在「完全开镜」时只打一条「模板路径诊断」
            // （视空间圆/pose 断言/capture 是否登记成功/lens 节点可见性）；正常路径不刷屏。
            // 准备渲染圆形模板层
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INVERT);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            // 80 是上游随便找的合适数值（BedrockAttachmentModel.java:279-283）：既是 INVERT 圆半径基数
            // （rad = 80 * scopeViewRadiusModifier * aimProgress），也是 capture() 的「是否开镜」门槛。
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            float aimProgress = 0.0F;
            if (player != null) {
                // 上游 :282 用 Minecraft.getMinecraft().getFrameTime()；
                // 1.7.10 对应 ClientTickHelper.getPartialTick()（RenderTickEvent 缓存的帧间插值系数）。
                aimProgress = IClientPlayerGunOperator.fromLocalPlayer(player)
                    .getClientAimingProgress(ClientTickHelper.getPartialTick());
            }
            aimProgress = Float.isFinite(aimProgress) ? Math.max(0.0F, Math.min(1.0F, aimProgress)) : 0.0F;
            float rad = 80 * scopeViewRadiusModifier * aimProgress;
            boolean probe = aimProgress >= 0.5F && LOGGED_INVERT.add(this);
            // ------------------------------------------------------------------------------------------
            // 上游 BedrockAttachmentModel.java:284-301：INVERT 圆 = **视空间圆**
            // 顶点 (16*90*cx, 16*90*cy, -90)，半径 rad，用**当前** GL 矩阵（手部投影 + 单位 modelview）绘制。
            // 透视除法把 z 消掉 ⇒ 圆心 NDC = (proj.m00*16*cx, proj.m11*16*cy)、半径 = (proj.m00*rad/90, proj.m11*rad/90)，
            // 与引擎无关（spec §1.3）。**不要**改成 Bounds/NDC 椭圆，也**不要** push identity 矩阵
            // —— 镜片 surface（ScopeLensRenderer#drawLens）用的是同一份视空间圆与同一对 capture 矩阵。
            // ------------------------------------------------------------------------------------------
            String invertText = "<aimProgress=0: no INVERT>";
            if (aimProgress > 0.001F) {
                StringBuilder invert = new StringBuilder();
                for (int i = 0; i < ocularNodePaths.size(); i++) {
                    if (selective && !isScopeOcular.get(i)) {
                        continue;
                    }
                    RenderSystem.stencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
                    Vector3f center = getBedrockPartCenter(matrixStack, ocularNodePaths.get(i));
                    float centerX = center.x() * 16 * 90;
                    float centerY = center.y() * 16 * 90;
                    drawInvertCircleNeutralizingFixedFunctionState(centerX, centerY, rad);
                    if (probe) {
                        invert.append(
                            String.format(
                                "(modelDelta=%.4f,%.4f, centerPose=%.4f,%.4f,%.4f, rad=%.2f) ",
                                center.x() * 16,
                                center.y() * 16,
                                center.x(),
                                center.y(),
                                center.z(),
                                rad));
                    }
                }
                if (probe) {
                    invertText = invert.toString()
                        .trim();
                }
            }
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            int captureRegistered = 0;
            int divisionImmediate = 0;
            for (int i = 0; i < ocularNodePaths.size() && i < divisionNodePaths.size(); i++) {
                if (i > Byte.MAX_VALUE) {
                    throw new IllegalArgumentException("Index of oculus is out of range for 127");
                }
                if (selective && !isScopeOcular.get(i)) {
                    RenderSystem.stencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
                    renderTempPart(
                        matrixStack,
                        transformType,
                        renderType,
                        light,
                        overlay,
                        divisionNodePaths.get(i),
                        bufferSource);
                } else {
                    // 渲染目镜黑色遮罩
                    //
                    // r7 实机复核⑥：回退复核⑤的「ADS 跳过遮罩」兜底（那是全局行为改动，造成其它瞄具回归）。
                    // 这里恢复复核④行为：始终按上游画遮罩（INVERT 生效时目镜内已无 i+1 像素，遮罩自然不可见；
                    // INVERT 未生效时该资产表现为黑屏，属已知降级，留给后续按上游规格重做）。
                    RenderSystem.stencilFunc(GL11.GL_EQUAL, i + 1, 0xFF);
                    renderTempPart(
                        matrixStack,
                        transformType,
                        renderType,
                        light,
                        overlay,
                        ocularNodePaths.get(i),
                        bufferSource);
                    // 渲染划分
                    int b = ~(i + 1) & 0xFF;
                    RenderSystem.stencilFunc(GL11.GL_EQUAL, b, 0xFF);
                    Vector3f center = getBedrockPartCenter(matrixStack, ocularNodePaths.get(i));
                    List<BedrockPart> ocular = ocularNodePaths.get(i);
                    List<BedrockPart> division = divisionNodePaths.get(i);
                    if (ScopeLensRenderer.capture(
                        center.x() * 16 * 90,
                        center.y() * 16 * 90,
                        rad,
                        b,
                        scopeLens,
                        bounds -> captureLensBounds(matrixStack, ocular, bounds),
                        buffers -> captureLensDivision(
                            matrixStack,
                            transformType,
                            renderType,
                            light,
                            overlay,
                            division,
                            buffers))) {
                        captureRegistered++;
                        continue;
                    }
                    divisionImmediate++;
                    renderTempPart(
                        matrixStack,
                        transformType,
                        renderType,
                        light,
                        overlay,
                        divisionNodePaths.get(i),
                        bufferSource);
                }
            }
            // 复核⑬只读诊断：**完全开镜帧**报告 capture 结果 + 目镜投影 bounds 可用性（每个附件模型一次）。
            // capture 的 warnOnce 只打第一条（往往是抬枪过渡帧），这里才能在稳定帧定位「为什么这个瞄具没有镜片」。
            if (aimProgress >= 0.99F && LOGGED_STEADY_CAPTURE.add(this)) {
                int firstOcular = -1;
                for (int i = 0; i < ocularNodePaths.size(); i++) {
                    if (!selective || isScopeOcular.get(i)) {
                        firstOcular = i;
                        break;
                    }
                }
                String boundsText = "<none>";
                String modelDelta = "<none>";
                float centerZ = 0.0F;
                if (firstOcular >= 0) {
                    List<BedrockPart> ocular = ocularNodePaths.get(firstOcular);
                    boundsText = ScopeLensRenderer
                        .describeOcularBounds(bounds -> captureLensBounds(matrixStack, ocular, bounds));
                    Vector3f steadyCenter = getBedrockPartCenter(matrixStack, ocular);
                    modelDelta = String.format("%.3f,%.3f", steadyCenter.x() * 16, steadyCenter.y() * 16);
                    centerZ = steadyCenter.z();
                }
                GunMod.LOGGER.debug(
                    "TACZ: scope steady ocular pass (capture diag): aim={}, rad={}, ocularIndex={}, modelDelta=({}), "
                        + "centerPoseZ={}, bounds={}, captureRegistered={}, divisionDrawnImmediately={}, "
                        + "lastCaptureFailure={}",
                    String.format("%.2f", aimProgress),
                    String.format("%.2f", rad),
                    firstOcular,
                    modelDelta,
                    String.format("%.4f", centerZ),
                    boundsText,
                    captureRegistered,
                    divisionImmediate,
                    ScopeLensRenderer.lastCaptureFailureReason());
            }
            if (probe) {
                // 只读诊断（spec §8.1/§8.2 断言 A/B；不改变任何绘制行为）：正常路径只有一条 INFO，不刷屏。
                ModelRendererWrapper lensWrapper = modelMap.get("lens");
                boolean lensVisible = lensWrapper != null && lensWrapper.getModelRenderer().visible;
                GunMod.LOGGER.debug(
                    "TACZ: scope ocular pass (stencil): aim={}, rad={}, invert=[{}], lensNodeVisible={}, "
                        + "captureRegistered={}, divisionDrawnImmediately={}",
                    String.format("%.2f", aimProgress),
                    String.format("%.2f", rad),
                    invertText,
                    lensVisible,
                    captureRegistered,
                    divisionImmediate);
            }
        }
    }

    /**
     * 没有模板缓冲时的瞄具降级渲染（兜底路径；正常情况下 {@code MainFramebufferStencil} 会在运行期给主
     * framebuffer 补 depth-stencil，见 {@code ScopeLensRenderer#isStencilAvailable()}）。
     *
     * <p>
     * <b>为什么不能照抄模板路径</b>：本类的目镜黑色遮罩（{@code ocular}）、镜身遮罩（{@code scope_body} 的
     * {@code GL_EQUAL 0}）与目镜分割面（{@code division}）都靠模板裁剪定位（上游
     * BedrockAttachmentModel.java:240-341）。没有模板附件时模板测试恒通过 ⇒ 这些面按完整几何画出：
     * 开镜时会把整个准星区盖成黑色（实机截图 e），不画又会让镜筒变成空壳（截图 f）。
     *
     * <p>
     * 因此按<b>开镜进度分三段降级</b>：
     * <ul>
     * <li><b>腰射</b>（未按 ADS 且 progress≈0）：只画外环 + 镜身。<b>目镜黑遮罩与 division 一律不画</b> ——
     * 上游这两类面只用于「写模板 + 在模板裁剪下出图」，未裁剪时 HAMR 的红叉/红框会悬浮在半空、大镜的
     * division/目镜面会糊成黑斑（r7 实机截图 docs/review/r7/screenshots/b_hand_scope_artifacts.png）。
     * 代价是镜筒内部可能显得「空心」，但这只是补挂 stencil 失败时的兜底，优先级低于「不画悬浮 artifact」；</li>
     * <li><b>开镜过渡 / 收镜</b>（按着 ADS 或 0&lt;progress&lt;{@link #ADS_HIDE_THRESHOLD}）：保留外环/镜身/
     * 目镜遮罩（抬枪动画可见），但<b>绝不画 division 子树</b> —— 未裁剪的 division 大平面会盖满屏幕
     * （r6-3 HAMR 大黑框的根因，实机截图 g_hamr_ads_blackframe.png）；</li>
     * <li><b>完全开镜</b>（{@link #currentAimProgress()} ≥ {@link #ADS_HIDE_THRESHOLD}）：镜身与目镜黑色遮罩
     * 在没有模板裁剪时必然挡住整个准星区，因此<b>隐藏</b>它们，只保留外环并登记镜片屏幕空间 pass ——
     * 镜片圆盘（采样「世界渲染末尾」的干净快照，见 {@code ScopeLensRenderer#prepareWorldCopy}）负责显示
     * （放大的/热成像的）世界画面。分割面/准星面同样跳过（未裁剪会盖满视野，属已知降级）。</li>
     * </ul>
     *
     * <p>
     * 上游 1.20 没有这条分支：主 framebuffer 由 Forge 的 {@code RenderTarget#enableStencil()} 保证有模板
     * （上游 RenderHelper#enableItemEntityStencilTest 的非 OptiFine 分支）。
     */
    private void renderWithoutStencil(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType,
        int light, int overlay, boolean selective, boolean drawOcularRing, boolean captureLens,
        MultiBufferSource bufferSource) {
        warnNoStencilOnce();
        // r6-3（实机 HAMR「开镜大黑框」根因，日志已确认）：过渡段（0 < aimingProgress < ADS_HIDE_THRESHOLD）走
        // 「未开镜」分支时，未裁剪的 division 子树（HAMR 的 division_bg 是 64×128 大平面）会整屏黑框，
        // 同一子树的 scope_division_illuminated（红十字/绿点/弹道刻度）会画在黑框上（实机截图
        // docs/review/r6/screenshots/g_hamr_ads_blackframe.png）。处理：**只要处于开镜过程（含过渡与收镜），
        // 就绝不画 division 子树**；腰射分支在 r7 实机复核后同样不再画 division/目镜遮罩（见类注释腰射一条，
        // 实机截图 docs/review/r7/screenshots/b_hand_scope_artifacts.png：悬浮红叉/红框 + 大镜黑斑）。
        float aim = currentAimProgress();
        boolean aiming = isAimingNow() || aim > 0.001F;
        boolean ads = captureLens && aim >= ADS_HIDE_THRESHOLD;
        logFallbackOnce(selective, captureLens, aiming, aim, ads);
        if (ads) {
            // 开镜：镜身/目镜黑色遮罩会挡住整个准星区（无 stencil 无法裁剪），隐藏它们；
            // 外环保留作为目镜轮廓，镜片圆盘负责出画面。
            hideOcularMasks();
            hidePart(scopeBodyPath);
            hideDivisions();
            if (drawOcularRing && ocularRingPath != null) {
                renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath, bufferSource);
            }
            captureLensesWithoutStencil(matrixStack, selective);
        } else {
            // 未开镜 / 开镜过渡 / 收镜过程：外环 + 镜身。
            if (drawOcularRing && ocularRingPath != null) {
                renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath, bufferSource);
            }
            if (scopeBodyPath != null) {
                renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath, bufferSource);
            }
            if (aiming) {
                // 过渡段/收镜：保留目镜遮罩（抬枪动画可见），不画 division（否则 = 整屏黑框）。
                logDivisionOnce();
                hideDivisions();
                for (List<BedrockPart> ocularPath : ocularNodePaths) {
                    renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularPath, bufferSource);
                }
            } else {
                // 腰射（r7 实机复核②）：division/目镜遮罩一律不画。上游这两类面只用于「写模板 + 在模板裁剪下
                // 出图」，无模板时画出来只会得到「悬浮的红叉/红框」与「大镜中间的黑斑」（实机截图
                // b_hand_scope_artifacts.png）。代价是镜筒内部可能显得空心 —— 这是补挂 stencil 失败时的兜底。
                logDivisionOnce();
                hideDivisions();
                hideOcularMasks();
            }
        }
        // 渲染附件其余全部几何 —— 正常模板路径的最后一步（renderBoth:643 / renderScope:724）。
        // 少了这一行，镜身/外环以外的模型全都不画 ⇒ 实机表现为「镜筒是空壳、可见面消失」。
        super.render(matrixStack, transformType, renderType, light, overlay, bufferSource);
    }

    /**
     * r6-3：玩家当前是否处于 ADS（按下 ADS 即 true 的客户端布尔状态）。
     *
     * <p>
     * 用途：
     * <ul>
     * <li>与 {@link #currentAimProgress()} 组合成「处于开镜过程（含过渡/收镜）」的判据 —— HAMR 大黑框的根因是
     * <b>过渡段</b>（0&lt;progress&lt;0.5）仍把 division 大平面未裁剪画出来，布尔状态在 ADS 按下/松开的瞬间就翻转，
     * 比插值进度更早/更可靠；</li>
     * <li>{@link #captureLensesWithoutStencil} 用它保证「已开镜但进度读数异常」时仍登记镜片。</li>
     * </ul>
     *
     * <p>
     * 1.7.10 差异：上游 1.20 用 {@code IClientPlayerGunOperator#isAim()}（客户端 ADS 布尔状态，见上游
     * {@code api/client/gameplay/IClientPlayerGunOperator.java:76}）同语义；这里同样走
     * {@link IClientPlayerGunOperator}（{@code EntityPlayerSP} 由 Mixin 实现）。拿不到时返回 {@code false}
     * （安全侧 = 只看进度，与本轮之前的腰射行为一致）。
     */
    private static boolean isAimingNow() {
        try {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return false;
            }
            return IClientPlayerGunOperator.fromLocalPlayer(player)
                .isAim();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /** r6-3：把所有 `division*` 节点（含子节点）隐藏，防止未裁剪的大黑面被 `super.render` 画出来。 */
    private void hideDivisions() {
        for (List<BedrockPart> path : divisionNodePaths) {
            if (!path.isEmpty()) {
                path.get(path.size() - 1).visible = false;
            }
        }
    }

    /** r6-3 诊断：每个附件模型打一条「第一人称渲染路径」（scope/sight 标志 + 变换类型）。 */
    private void logRenderPathOnce(ItemDisplayContext transformType) {
        if (!LOGGED_RENDER_PATH.add(this)) {
            return;
        }
        GunMod.LOGGER.debug(
            "TACZ: attachment first-person render: scope={}, sight={}, transform={}, stencil={}",
            isScope,
            isSight,
            transformType,
            ScopeLensRenderer.isStencilAvailable());
    }

    /** r6-3 诊断：每个附件模型打一条「无模板降级分支」选择。 */
    private void logFallbackOnce(boolean selective, boolean captureLens, boolean aiming, float aim, boolean ads) {
        if (!LOGGED_FALLBACK.add(this)) {
            return;
        }
        String branch;
        if (ads) {
            branch = "ADS(hide body/ocular/division + lens disc)";
        } else if (aiming) {
            branch = "ADS-transition(keep body/ocular, divisions HIDDEN)";
        } else {
            branch = "hipfire(ring+body only; masks/divisions HIDDEN)";
        }
        GunMod.LOGGER.debug(
            "TACZ: scope no-stencil fallback: selective={}, captureLens={}, aiming={}, aim={}, branch={}",
            selective,
            captureLens,
            aiming,
            String.format("%.2f", aim),
            branch);
    }

    /** r6-3 诊断：每个附件模型打一条「division 是否被未裁剪画出」（r7 起降级路径一律不画）。 */
    private void logDivisionOnce() {
        if (!LOGGED_DIVISION.add(this)) {
            return;
        }
        GunMod.LOGGER.debug(
            "TACZ: scope no-stencil division plane: drawn=false, divisionPaths={} (unclipped divisions float in the "
                + "air / paint black blobs; r7 keeps them hidden in the fallback path)",
            divisionNodePaths.size());
    }

    /** 开镜达到该进度后，无模板模式下隐藏「会挡住准星区」的镜身/目镜遮罩。 */
    private static final float ADS_HIDE_THRESHOLD = 0.5F;

    /** 当前开镜进度 0..1（拿不到时返回 0，走「完整模型」分支，安全侧）。 */
    private float currentAimProgress() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return 0.0F;
        }
        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        if (operator == null) {
            return 0.0F;
        }
        float progress = operator.getClientAimingProgress(ClientTickHelper.getPartialTick());
        return Float.isFinite(progress) ? Math.max(0.0F, Math.min(1.0F, progress)) : 0.0F;
    }

    /** 把一个节点（含子节点）设为不可见；后续要画时由 {@code renderTempPart} 临时改回可见。 */
    private static void hidePart(@Nullable List<BedrockPart> path) {
        if (path != null && !path.isEmpty()) {
            path.get(path.size() - 1).visible = false;
        }
    }

    /** 隐藏所有目镜黑色遮罩节点（含子节点）：无模板裁剪时它们会整块画出来（= 大黑框）。 */
    private void hideOcularMasks() {
        for (List<BedrockPart> path : ocularNodePaths) {
            if (!path.isEmpty()) {
                path.get(path.size() - 1).visible = false;
            }
        }
    }

    /**
     * 无模板附件时只登记镜片屏幕空间 pass（圆盘），不登记 reticle。
     *
     * <p>
     * 遮罩圆半径与主路径一致（{@code 80 * scopeViewRadiusModifier * aimingProgress}，上游
     * BedrockAttachmentModel.java:279-283）；{@code rad <= 0.001} 等价于「没开镜」，{@code capture()} 自带同样
     * 早退，这里提前 return 省掉一次几何遍历。
     */
    private void captureLensesWithoutStencil(PoseStack matrixStack, boolean selective) {
        float rad = currentLensRadius();
        // r6-3：ADS 时哪怕开镜进度读数异常为 0 也要登记镜片（否则隐藏了镜身却没有镜内画面 = 一片世界但没镜片）。
        // rad 在这里只作为 {@code capture()} 的「是否开镜」门槛，圆盘尺寸来自 capture 时刻投影出的目镜几何。
        if (isAimingNow()) {
            rad = Math.max(rad, 1.0F);
        }
        if (rad <= 0.001F) {
            return;
        }
        for (int i = 0; i < ocularNodePaths.size(); i++) {
            if (selective && !isScopeOcular.get(i)) {
                continue;
            }
            Vector3f center = getBedrockPartCenter(matrixStack, ocularNodePaths.get(i));
            List<BedrockPart> ocular = ocularNodePaths.get(i);
            // 无模板 ⇒ reticle 不登记（capture 内部也以 stencilAvailable() 为准），这里传空 consumer。
            ScopeLensRenderer.capture(
                center.x() * 16 * 90,
                center.y() * 16 * 90,
                rad,
                -(i + 1),
                scopeLens,
                bounds -> captureLensBounds(matrixStack, ocular, bounds),
                buffers -> {});
        }
    }

    /** 目镜遮罩圆半径（上游 BedrockAttachmentModel.java:279-283）。 */
    private float currentLensRadius() {
        return 80 * scopeViewRadiusModifier * currentAimProgress();
    }

    /** 无模板附件只 WARN 一次（每次进游戏一条，方便判读实机日志）。 */
    private static void warnNoStencilOnce() {
        if (noStencilWarned) {
            return;
        }
        noStencilWarned = true;
        GunMod.LOGGER.warn(
            "TACZ: scope attachments use the NO-STENCIL degraded path (Forge stencilBits=0): eye-piece masks "
                + "/ scope body are hidden while ADS (otherwise they paint over the whole view) and the reticle "
                + "plane is skipped; the lens disc still shows the (thermal) world. Add JVM arg "
                + "-Dforge.forceDisplayStencil=true for the full masked rendering.");
    }

    /** 登记镜片：把目镜节点几何喂给 {@code Bounds} 统计屏幕包围盒（只读几何，不绘制）。 */
    private void captureLensBounds(PoseStack poseStack, List<BedrockPart> path, VertexConsumer bounds) {
        poseStack.pushPose();
        try {
            for (int i = 0; i < path.size() - 1; i++) path.get(i)
                .translateAndRotateAndScale(poseStack);
            captureLensPartBounds(poseStack, path.get(path.size() - 1), bounds);
        } finally {
            poseStack.popPose();
        }
    }

    /** Inspect geometry only; functional renderers must not run a second time for the bounds probe. */
    private void captureLensPartBounds(PoseStack poseStack, BedrockPart part, VertexConsumer bounds) {
        poseStack.pushPose();
        part.translateAndRotateAndScale(poseStack);
        for (BedrockCube cube : part.cubes) cube.compile(poseStack.last(), bounds, 0, 0, 1, 1, 1, 1);
        for (BedrockPart child : part.children) captureLensPartBounds(poseStack, child, bounds);
        poseStack.popPose();
    }

    /** Resolve functional readouts now, before the shared model is animated/reset for another draw. */
    private void captureLensDivision(PoseStack poseStack, ItemDisplayContext context, RenderType type, int light,
        int overlay, List<BedrockPart> division, MultiBufferSource buffers) {
        List<IFunctionalRenderer> previous = delegateRenderers;
        delegateRenderers = new ArrayList<>();
        try {
            renderTempPart(poseStack, context, type, light, overlay, division, buffers);
            for (int i = 0; i < delegateRenderers.size(); i++) {
                delegateRenderers.get(i)
                    .render(poseStack, buffers.getBuffer(type), context, light, overlay, buffers);
            }
        } finally {
            delegateRenderers = previous;
        }
    }

    private void renderBoth(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light,
        int overlay, MultiBufferSource bufferSource) {
        // r6：无模板附件时整组模板裁剪不可用（依据见 renderWithoutStencil 的 javadoc），走降级路径。
        if (!ScopeLensRenderer.isStencilAvailable()) {
            renderWithoutStencil(
                matrixStack,
                transformType,
                renderType,
                light,
                overlay,
                true,
                true,
                true,
                bufferSource);
            return;
        }
        // r7：主 framebuffer 的 stencil 附件由 MainFramebufferStencil 在运行期补挂（等价 Forge 1.20.1
        // RenderTarget#enableStencil），因此下面的模板裁剪默认生效；只有补挂失败时本方法才会被
        // isStencilAvailable() 提前挡到 renderWithoutStencil 降级路径。
        RenderHelperCompat.enableItemEntityStencilTest();
        // 清空模板缓冲区、准备绘制模板缓冲（模板写掩码是固定管线全局状态，先归位保证 glClear 真的写进去）
        RenderSystem.stencilMask(0xFF);
        RenderSystem.clearStencil(0);
        // 移植说明：上游是 RenderSystem.clear(mask, Minecraft.ON_OSX)，第二参数只是「是否在 macOS 上额外检查 GL 错误」。
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        if (ocularRingPath != null) {
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            // 渲染目镜外环
            renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath, bufferSource);
        }
        // 渲染目镜以写入模板桓冲值 (暂时只渲染 ocular_scope)
        renderOcularStencil(matrixStack, transformType, renderType, light, overlay, true, bufferSource);
        // 渲染镜身
        if (scopeBodyPath != null) {
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 0, 0xFF);
            renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath, bufferSource);
        }
        // 渲染目镜以写入模板桓冲值 (渲染其他的目镜)
        renderOcularStencil(matrixStack, transformType, renderType, light, overlay, false, bufferSource);
        // 渲染目镜遮罩和划分
        renderOcularAndDivision(matrixStack, transformType, renderType, light, overlay, true, bufferSource);
        // 关闭模板缓冲
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        RenderHelperCompat.disableItemEntityStencilTest();
        // 只读诊断（复核⑧）：super.render 前仍可见的节点会不受 stencil 裁剪地画进主画面。
        logUnclippedPartsOnce("renderBoth");
        // 渲染其他部分
        super.render(matrixStack, transformType, renderType, light, overlay, bufferSource);
    }

    private void renderSight(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light,
        int overlay, MultiBufferSource bufferSource) {
        // r6：无模板附件时 division（红点/准星面）未裁剪会被画成整块黑面，降级为不画（依据见 renderWithoutStencil）。
        if (!ScopeLensRenderer.isStencilAvailable()) {
            renderWithoutStencil(
                matrixStack,
                transformType,
                renderType,
                light,
                overlay,
                false,
                false,
                false,
                bufferSource);
            return;
        }
        // r7：stencil 附件由 MainFramebufferStencil 运行期补挂（见 renderBoth 同名注释）。
        RenderHelperCompat.enableItemEntityStencilTest();
        // 清空模板缓冲区、准备绘制模板缓冲
        RenderSystem.stencilMask(0xFF);
        RenderSystem.clearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        // 渲染目镜以写入模板桓冲值
        renderOcularStencil(matrixStack, transformType, renderType, light, overlay, false, bufferSource);
        // 渲染划分
        renderDivisionOnly(matrixStack, transformType, renderType, light, overlay, bufferSource);
        // 关闭模板缓冲
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        RenderHelperCompat.disableItemEntityStencilTest();
        // 只读诊断（复核⑧）：super.render 前仍可见的节点会不受 stencil 裁剪地画进主画面。
        logUnclippedPartsOnce("renderSight");
        // 渲染其他部分
        if (scopeBodyPath != null) {
            renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath, bufferSource);
        }
        super.render(matrixStack, transformType, renderType, light, overlay, bufferSource);
    }

    private void renderScope(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType, int light,
        int overlay, MultiBufferSource bufferSource) {
        // r6：无模板附件时目镜黑遮罩/镜身遮罩/division 全部无法裁剪 ⇒ 整块黑矩形（实机截图 b/c/d）。
        // 降级为「只画外环/镜身 + 登记镜片圆盘」。
        if (!ScopeLensRenderer.isStencilAvailable()) {
            renderWithoutStencil(
                matrixStack,
                transformType,
                renderType,
                light,
                overlay,
                false,
                true,
                true,
                bufferSource);
            return;
        }
        // r7：stencil 附件由 MainFramebufferStencil 运行期补挂（见 renderBoth 同名注释）。
        RenderHelperCompat.enableItemEntityStencilTest();
        // 清空模板缓冲区、准备绘制模板缓冲
        RenderSystem.stencilMask(0xFF);
        RenderSystem.clearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        // 渲染目镜外环
        if (ocularRingPath != null) {
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            renderTempPart(matrixStack, transformType, renderType, light, overlay, ocularRingPath, bufferSource);
        }
        // 渲染目镜以写入模板桓冲值
        renderOcularStencil(matrixStack, transformType, renderType, light, overlay, false, bufferSource);
        // 渲染镜身
        if (scopeBodyPath != null) {
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 0, 0xFF);
            renderTempPart(matrixStack, transformType, renderType, light, overlay, scopeBodyPath, bufferSource);
        }
        // 渲染目镜遮罩和划分
        renderOcularAndDivision(matrixStack, transformType, renderType, light, overlay, false, bufferSource);
        // 关闭模板缓冲
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        RenderHelperCompat.disableItemEntityStencilTest();
        // 只读诊断（复核⑧）：super.render 前仍可见的节点会不受 stencil 裁剪地画进主画面。
        logUnclippedPartsOnce("renderScope");
        // 渲染其他部分
        super.render(matrixStack, transformType, renderType, light, overlay, bufferSource);
    }

    public void renderBothAccelerated(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType,
        int light, int overlay) {
        renderBothAccelerated(
            matrixStack,
            transformType,
            renderType,
            light,
            overlay,
            RenderHelperCompat.getBufferSource(transformType));
    }

    /**
     * TODO(port): 上游 BedrockAttachmentModel.java:468-597 把目镜/镜身/遮罩拆成 AcceleratedRendering 的三个渲染层
     * （ARCompat.setRenderLayer/setRenderBeforeFunction/setRenderAfterFunction + VAO 缓存 + BufferUploader.invalidate）。
     * 1.7.10 没有 AcceleratedRendering、也没有「按层重排绘制顺序」的概念，逐层拆分的唯一目的就是喂给那条加速管线，
     * 因此这里直接回退到非加速的 {@link #renderBoth}（绘制顺序、模板值、可见性语义与上游的最终结果一致）。
     * 方法保留以维持公开 API。
     */
    public void renderBothAccelerated(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType,
        int light, int overlay, MultiBufferSource bufferSource) {
        renderBoth(matrixStack, transformType, renderType, light, overlay, bufferSource);
    }

    private static class OcularWrapper {

        public ModelRendererWrapper renderer;
        public boolean isScope;

        public OcularWrapper(ModelRendererWrapper renderer, boolean isScope) {
            this.renderer = renderer;
            this.isScope = isScope;
        }
    }
}
