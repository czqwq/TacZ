package com.tacz.guns.client.model;

import static com.tacz.guns.client.model.GunModelConstant.*;

import java.util.*;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationListener;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.model.functional.*;
import com.tacz.guns.client.model.listener.model.ModelAdditionalMagazineListener;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderSystem;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;

public class BedrockGunModel extends BedrockAnimatedModel {

    private static final String RANGEFINDER_IMPACT_PREDICTION_NODE = "rangefinder_impact_prediction_pos";

    protected final EnumMap<AttachmentType, List<BedrockPart>> refitAttachmentViewPath = Maps
        .newEnumMap(AttachmentType.class);
    private final EnumMap<AttachmentType, ItemStack> currentAttachmentItem = Maps.newEnumMap(AttachmentType.class);
    private final Set<String> adapterToRender = Sets.newHashSet();
    private final ArrayList<ShellRender> shellRenderList = new ArrayList<>();

    // 第一人称机瞄摄像机定位组的路径
    protected @Nullable List<BedrockPart> ironSightPath;
    // 第一人称idle状态摄像机定位组的路径
    protected @Nullable List<BedrockPart> idleSightPath;
    // 第三人称手部物品渲染原点定位组的路径
    protected @Nullable List<BedrockPart> thirdPersonHandOriginPath;
    // 展示框渲染原点定位组的路径
    protected @Nullable List<BedrockPart> fixedOriginPath;
    // 地面实体渲染原点定位组的路径
    protected @Nullable List<BedrockPart> groundOriginPath;
    // 瞄具配件定位组的路径。其他配件不需要存路径，只需要替换渲染。但是瞄具定位组需要用来辅助第一人称瞄准的摄像机定位。
    protected @Nullable List<BedrockPart> scopePosPath;
    // 枪口火焰定位组
    protected @Nullable List<BedrockPart> muzzleFlashPosPath;
    // 根组
    protected @Nullable BedrockPart root;
    // 弹匣定位组
    protected @Nullable BedrockPart magazineNode;
    // 换弹时第二个弹匣定位组
    protected @Nullable BedrockPart additionalMagazineNode;
    protected @Nullable List<BedrockPart> laserBeamPaths;

    private boolean renderHand = true;
    private boolean renderMount;
    private ItemStack currentGunItem;
    private int currentExtendMagLevel = 0;

    public BedrockGunModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);

        this.magazineNode = Optional.ofNullable(modelMap.get(MAG_NORMAL_NODE))
            .map(ModelRendererWrapper::getModelRenderer)
            .orElse(null);
        this.additionalMagazineNode = Optional.ofNullable(modelMap.get(MAG_ADDITIONAL_NODE))
            .map(ModelRendererWrapper::getModelRenderer)
            .orElse(null);

        // 手部渲染：无条件挂 LeftHandRender / RightHandRender（= 上游行为，BedrockGunModel.java:80-83）。
        // FunctionalBedrockPart 有 functionalRenderer 时只执行功能渲染器、不再画自身 cube，
        // 所以「用皮肤手臂还是模型自带手」只能在这里决定，不能在渲染器内部跳过。
        // 平台差异：1.7.10 ModelBiped 手臂截面比 1.20 PlayerModel 略宽，见 docs/review/r5/render-fp.md F-1。
        // 左手手臂
        this.setFunctionalRenderer(LEFTHAND_POS_NODE, bedrockPart -> new LeftHandRender(this));
        // 右手手臂
        this.setFunctionalRenderer(RIGHTHAND_POS_NODE, bedrockPart -> new RightHandRender(this));
        // 枪口火焰
        this.setFunctionalRenderer(MUZZLE_FLASH_ORIGIN_NODE, bedrockPart -> new MuzzleFlashRender(this));
        // 枪管内的子弹，用于闭膛待机枪械
        this.setFunctionalRenderer(
            BULLET_IN_BARREL,
            bedrockPart -> ammoHiddenRender(bedrockPart, iGun -> iGun.hasBulletInBarrel(currentGunItem)));
        // 弹匣内子弹
        this.setFunctionalRenderer(
            BULLET_IN_MAG,
            bedrockPart -> ammoHiddenRender(bedrockPart, iGun -> iGun.getCurrentAmmoCount(currentGunItem) > 0));
        // 机枪弹链
        this.setFunctionalRenderer(
            BULLET_CHAIN,
            bedrockPart -> ammoHiddenRender(bedrockPart, iGun -> iGun.getCurrentAmmoCount(currentGunItem) > 0));
        // 有通用瞄具时显示，用于放瞄具的导轨（如 AKM 的导轨）
        this.setFunctionalRenderer(
            MOUNT,
            bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> isEmpty(scopeItem) == false && renderMount));
        // 无瞄具时可见，通常用于 M4 上
        this.setFunctionalRenderer(CARRY, bedrockPart -> scopeHiddenRender(bedrockPart, BedrockGunModel::isEmpty));
        // 有瞄具时显示，折叠的机械瞄具
        this.setFunctionalRenderer(
            SIGHT_FOLDED,
            bedrockPart -> scopeHiddenRender(bedrockPart, scopeItem -> isEmpty(scopeItem) == false));
        // 无瞄具时可见，机械瞄具
        this.setFunctionalRenderer(SIGHT, bedrockPart -> scopeHiddenRender(bedrockPart, BedrockGunModel::isEmpty));
        // 安装一级扩容弹匣时显示
        this.setFunctionalRenderer(MAG_EXTENDED_1, bedrockPart -> extendedMagHiddenRender(bedrockPart, 1));
        // 安装二级扩容弹匣时显示
        this.setFunctionalRenderer(MAG_EXTENDED_2, bedrockPart -> extendedMagHiddenRender(bedrockPart, 2));
        // 安装三级扩容弹匣时显示
        this.setFunctionalRenderer(MAG_EXTENDED_3, bedrockPart -> extendedMagHiddenRender(bedrockPart, 3));
        // 没有安装扩容弹匣时显示
        this.setFunctionalRenderer(MAG_STANDARD, bedrockPart -> extendedMagHiddenRender(bedrockPart, 0));
        // 部分枪械换弹动画播放时，会同时出现两个弹匣，这个就是程序自动渲染另一个弹匣的代码
        this.setFunctionalRenderer(MAG_ADDITIONAL_NODE, this::renderAdditionalMagazine);
        // 默认护木渲染
        this.setFunctionalRenderer(HANDGUARD_DEFAULT_NODE, this::handguardDefaultRender);
        // 战术护木渲染
        this.setFunctionalRenderer(HANDGUARD_TACTICAL_NODE, this::handguardTacticalRender);
        // 缓存其他定位组
        this.cacheOtherPath();
        // 缓存改装 UI 下各个配件的特写视角定位组
        this.cacheRefitAttachmentViewPath();
        // 缓存抛壳窗
        this.cacheShellOriginNodes();
        // 准备各个配件的渲染
        this.allAttachmentRender();
        // 配件转接口渲染
        this.setFunctionalRenderer(ATTACHMENT_ADAPTER_NODE, this::attachmentAdapterNodeRender);
    }

    private void cacheOtherPath() {
        ironSightPath = getPath(modelMap.get(IRON_VIEW_NODE));
        idleSightPath = getPath(modelMap.get(IDLE_VIEW_NODE));
        thirdPersonHandOriginPath = getPath(modelMap.get(THIRD_PERSON_HAND_ORIGIN_NODE));
        fixedOriginPath = getPath(modelMap.get(FIXED_ORIGIN_NODE));
        groundOriginPath = getPath(modelMap.get(GROUND_ORIGIN_NODE));
        muzzleFlashPosPath = getPath(modelMap.get(MUZZLE_FLASH_ORIGIN_NODE));
        scopePosPath = getPath(
            modelMap.get(
                AttachmentType.SCOPE.name()
                    .toLowerCase() + ATTACHMENT_POS_SUFFIX));
        laserBeamPaths = getPath(modelMap.get("laser_beam"));
        root = Optional.ofNullable(modelMap.get(ROOT_NODE))
            .map(ModelRendererWrapper::getModelRenderer)
            .orElse(null);
    }

    private void cacheRefitAttachmentViewPath() {
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                refitAttachmentViewPath.put(type, getPath(modelMap.get(REFIT_VIEW_NODE)));
                continue;
            }
            String nodeName = REFIT_VIEW_PREFIX + type.name()
                .toLowerCase() + REFIT_VIEW_SUFFIX;
            refitAttachmentViewPath.put(type, getPath(modelMap.get(nodeName)));
        }
    }

    private void cacheShellOriginNodes() {
        ModelRendererWrapper rendererWrapper = modelMap.get(SHELL_ORIGIN_NODE);
        int i = 1;
        while (rendererWrapper != null) {
            ShellRender shellRender = new ShellRender(this);
            this.setFunctionalRenderer(rendererWrapper.getModelRenderer().name, bedrockPart -> shellRender);
            shellRenderList.add(shellRender);
            rendererWrapper = modelMap.get(SHELL_ORIGIN_NODE_PREFIX + i);
            i++;
        }
    }

    @Nullable
    private IFunctionalRenderer attachmentAdapterNodeRender(BedrockPart bedrockPart) {
        for (BedrockPart child : bedrockPart.children) {
            if (child.name == null) {
                child.visible = false;
                continue;
            }
            child.visible = adapterToRender.contains(child.name);
        }
        return null;
    }

    private void allAttachmentRender() {
        for (AttachmentType type : AttachmentType.values()) {
            // 瞄具的渲染需要提前
            if (type == AttachmentType.NONE || type == AttachmentType.SCOPE) {
                continue;
            }
            String positionNodeName = type.name()
                .toLowerCase() + ATTACHMENT_POS_SUFFIX;
            String defaultNodeName = type.name()
                .toLowerCase() + DEFAULT_ATTACHMENT_SUFFIX;
            this.setFunctionalRenderer(positionNodeName, bedrockPart -> {
                bedrockPart.visible = false;
                return new AttachmentRender(this, type);
            });
            this.setFunctionalRenderer(defaultNodeName, bedrockPart -> {
                ItemStack attachmentItem = currentAttachmentItem.get(type);
                if (type == AttachmentType.MUZZLE && checkShowMuzzle(bedrockPart, attachmentItem)) {
                    return null;
                }
                bedrockPart.visible = isEmpty(attachmentItem);
                return null;
            });
        }
    }

    private static boolean checkShowMuzzle(BedrockPart bedrockPart, ItemStack attachmentItem) {
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment != null) {
            ResourceLocation attachmentId = iAttachment.getAttachmentId(attachmentItem);
            var attachmentIndex = TimelessAPI.getClientAttachmentIndex(attachmentId);
            if (attachmentIndex.isPresent()) {
                bedrockPart.visible = attachmentIndex.get()
                    .isShowMuzzle();
                return true;
            }
        }
        return false;
    }

    @Nullable
    private IFunctionalRenderer handguardTacticalRender(BedrockPart bedrockPart) {
        ItemStack laserItem = currentAttachmentItem.get(AttachmentType.LASER);
        ItemStack gripItem = currentAttachmentItem.get(AttachmentType.GRIP);
        bedrockPart.visible = !isEmpty(laserItem) || !isEmpty(gripItem);
        return null;
    }

    @Nullable
    private IFunctionalRenderer handguardDefaultRender(BedrockPart bedrockPart) {
        ItemStack laserItem = currentAttachmentItem.get(AttachmentType.LASER);
        ItemStack gripItem = currentAttachmentItem.get(AttachmentType.GRIP);
        bedrockPart.visible = isEmpty(laserItem) && isEmpty(gripItem);
        return null;
    }

    /**
     * 移植辅助：1.20 {@code ItemStack#isEmpty()} 在 1.7.10 没有，用 {@code stackSize} 判断；
     * 同时容忍 null（1.7.10 侧 {@code IGun#getAttachment} 等接口可能返回 null）。
     */
    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.stackSize <= 0;
    }

    @NotNull
    private IFunctionalRenderer renderAdditionalMagazine(BedrockPart bedrockPart) {
        return new IFunctionalRenderer() {

            @Override
            public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType,
                int light, int overlay) {
                render(
                    poseStack,
                    vertexBuffer,
                    transformType,
                    light,
                    overlay,
                    RenderHelperCompat.getBufferSource(transformType));
            }

            @Override
            public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType,
                int light, int overlay, MultiBufferSource bufferSource) {
                if (bedrockPart.visible) {
                    bedrockPart.compile(poseStack.last(), vertexBuffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                    for (BedrockPart part : bedrockPart.children) {
                        part.render(
                            poseStack,
                            transformType,
                            vertexBuffer,
                            light,
                            overlay,
                            1.0F,
                            1.0F,
                            1.0F,
                            1.0F,
                            bufferSource);
                    }
                    if (magazineNode != null && magazineNode.visible) {
                        magazineNode.compile(poseStack.last(), vertexBuffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
                        for (BedrockPart part : magazineNode.children) {
                            part.render(
                                poseStack,
                                transformType,
                                vertexBuffer,
                                light,
                                overlay,
                                1.0F,
                                1.0F,
                                1.0F,
                                1.0F,
                                bufferSource);
                        }
                    }
                }
            }
        };
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

    public void render(PoseStack matrixStack, ItemStack gunItem, ItemDisplayContext transformType,
        RenderType renderType, int light, int overlay) {
        render(matrixStack, gunItem, transformType, renderType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void render(PoseStack matrixStack, ItemStack gunItem, ItemDisplayContext transformType,
        RenderType renderType, int light, int overlay, MultiBufferSource bufferSource) {
        render(matrixStack, gunItem, transformType, renderType, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F, bufferSource);
    }

    public void render(PoseStack matrixStack, ItemStack gunItem, ItemDisplayContext transformType,
        RenderType renderType, int light, int overlay, float red, float green, float blue, float alpha) {
        render(
            matrixStack,
            gunItem,
            transformType,
            renderType,
            light,
            overlay,
            red,
            green,
            blue,
            alpha,
            RenderHelperCompat.getBufferSource(transformType));
    }

    public void render(PoseStack matrixStack, ItemStack gunItem, ItemDisplayContext transformType,
        RenderType renderType, int light, int overlay, float red, float green, float blue, float alpha,
        MultiBufferSource bufferSource) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return;
        }
        currentGunItem = gunItem;
        currentExtendMagLevel = 0;
        adapterToRender.clear();
        // 更新配件物品的缓存，以供渲染使用
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            ItemStack attachmentItem = iGun.getAttachment(gunItem, type);
            if (isEmpty(attachmentItem)) {
                attachmentItem = iGun.getBuiltinAttachment(gunItem, type);
            }
            currentAttachmentItem.put(type, attachmentItem);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (attachment != null) {
                TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(attachmentItem))
                    .ifPresent(index -> {
                        // 读取扩容等级，为扩容弹匣渲染做准备
                        if (type == AttachmentType.EXTENDED_MAG) {
                            currentExtendMagLevel = index.getData()
                                .getExtendedMagLevel();
                        }
                        // 读取瞄具 Mount 的渲染需求
                        if (type == AttachmentType.SCOPE) {
                            renderMount = index.isShowMount();
                        }
                        // 添加需要渲染的转接口
                        if (index.getAdapterNodeName() != null) {
                            adapterToRender.add(index.getAdapterNodeName());
                        }
                    });
            }
        }
        if (laserBeamPaths != null) {
            BeamRenderer.renderLaserBeam(gunItem, matrixStack, transformType, laserBeamPaths, bufferSource);
        }

        // TODO(port): 上游 BedrockGunModel.java:315-318 在 ARCompat.shouldAccelerate() 时把整支枪交给
        // AcceleratedRendering 的层缓冲管线（renderAccelerated）。该模组是 1.20 专属，1.7.10 不存在，
        // ARCompat.LOADED 恒为 false，因此这里只保留非加速路径；renderAccelerated 也回退到这里。
        renderUnaccelerated(
            matrixStack,
            transformType,
            renderType,
            light,
            overlay,
            red,
            green,
            blue,
            alpha,
            bufferSource);
    }

    /**
     * 1.7.10 上唯一的一条真实渲染路径：模板缓冲 + {@code super.render(...)}。
     */
    private void renderUnaccelerated(PoseStack matrixStack, ItemDisplayContext transformType, RenderType renderType,
        int light, int overlay, float red, float green, float blue, float alpha, MultiBufferSource bufferSource) {
        // 镜子需要先渲染，写入模板值
        ItemStack attachmentItem = currentAttachmentItem.get(AttachmentType.SCOPE);
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (scopePosPath != null && !isEmpty(attachmentItem)) {
            matrixStack.pushPose();
            for (BedrockPart bedrockPart : scopePosPath) {
                bedrockPart.translateAndRotateAndScale(matrixStack);
            }
            AttachmentRender.renderAttachment(
                attachmentItem,
                currentGunItem,
                AttachmentType.SCOPE,
                matrixStack,
                transformType,
                light,
                overlay,
                bufferSource);
            matrixStack.popPose();
            // 开启模板测试，因为镜内不渲染枪体
            //
            // 平台差异（1.7.10 的 stencil 从哪来，结论见 docs/review/r2/01-render-material.md、
            // docs/review/r6/scope-thermal.md）：
            // 1.7.10 的 Framebuffer 默认**不建** depth-stencil 附件
            // （`ForgeHooksClient.stencilBits` 默认 0，见 build/rfg/.../ForgeHooksClient.java:317-341；
            // `Framebuffer.createFramebuffer` 因此只挂 DEPTH_COMPONENT，见 Framebuffer.java:117-130）；
            // 本工程在**运行期**由 client/renderer/scope/MainFramebufferStencil 补挂：每帧在
            // ScopeLensRenderer#beginFrame()（renderWorld HEAD）检测主 FBO，没有 stencil 附件就用
            // GL_DEPTH24_STENCIL8 renderbuffer 同时挂 DEPTH/STENCIL（等价 1.20 Forge
            // RenderTarget#enableStencil），因此下面这段 stencilFunc/stencilOp 在支持的环境下
            // 与上游 1.20 等效生效（组合镜镜内不渲染枪体）。
            // 只有补挂失败才回退为「模板测试恒通过」的降级（GL<3.0 / core profile 光影 /
            // OptiFine / 深度附件已被换成 texture / 光影包占用）：此时镜内仍能看到枪体 —— 有据降级，
            // 不崩不刷屏；用 `-Dforge.forceDisplayStencil=true` 让 Forge 自己建也仍被识别为已就绪、不重复补挂。
            if (iAttachment != null) {
                Optional<ClientAttachmentIndex> attachmentIndex = TimelessAPI
                    .getClientAttachmentIndex(iAttachment.getAttachmentId(attachmentItem));
                attachmentIndex.ifPresent(index -> {
                    if (index.isScope()) { // 组合镜
                        RenderHelperCompat.enableItemEntityStencilTest();
                        RenderSystem.stencilFunc(GL11.GL_GREATER, 127, 0xFF);
                    }
                });
            }
        }
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        super.render(matrixStack, transformType, renderType, light, overlay, red, green, blue, alpha, bufferSource);
        RenderHelperCompat.disableItemEntityStencilTest();
        RenderSystem.clearStencil(0);
        // 移植说明：上游 RenderSystem.clear(mask, Minecraft.ON_OSX)，第二参数是「是否在 macOS 上额外检查 GL 错误」。
        // 1.7.10 直接调 GL11.glClear。
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
    }

    /**
     * TODO(port): 上游 BedrockGunModel.java:348-422 的加速渲染实现（ARCompat 层前/层后任务 + VAO 缓存
     * + BufferUploader.invalidate）在 1.7.10 没有对应能力（无 AcceleratedRendering、无 VAO 缓存失效语义）。
     * 方法保留以维持公开 API，内部直接回退到非加速路径。
     */
    public void renderAccelerated(PoseStack matrixStack, ItemStack gunItem, ItemDisplayContext transformType,
        RenderType renderType, int light, int overlay, MultiBufferSource bufferSource) {
        renderUnaccelerated(
            matrixStack,
            transformType,
            renderType,
            light,
            overlay,
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            bufferSource);
    }

    @Nullable
    private IFunctionalRenderer ammoHiddenRender(BedrockPart bedrockPart, Predicate<IGun> predicate) {
        IGun iGun = IGun.getIGunOrNull(currentGunItem);
        if (iGun != null) {
            bedrockPart.visible = predicate.test(iGun);
        }
        return null;
    }

    @Nullable
    private IFunctionalRenderer scopeHiddenRender(BedrockPart bedrockPart, Predicate<ItemStack> predicate) {
        // 安装瞄具时可见
        ItemStack scopeItem = currentAttachmentItem.get(AttachmentType.SCOPE);
        bedrockPart.visible = predicate.test(scopeItem);
        return null;
    }

    @Nullable
    private IFunctionalRenderer extendedMagHiddenRender(BedrockPart bedrockPart, int level) {
        bedrockPart.visible = currentExtendMagLevel == level;
        return null;
    }

    @Override
    public AnimationListener supplyListeners(String nodeName, ObjectAnimationChannel.ChannelType type) {
        AnimationListener listener = super.supplyListeners(nodeName, type);
        if (listener == null) {
            return null;
        }
        if (nodeName.equals(MAG_ADDITIONAL_NODE)) {
            // 额外弹匣只有当动画中有它的关键帧的时候才渲染
            return new ModelAdditionalMagazineListener(listener, this);
        }
        return listener;
    }

    @Override
    public void cleanAnimationTransform() {
        super.cleanAnimationTransform();
        if (additionalMagazineNode != null) {
            additionalMagazineNode.visible = false;
        }
    }

    public EnumMap<AttachmentType, ItemStack> getCurrentAttachmentItem() {
        return currentAttachmentItem;
    }

    public ItemStack getCurrentGunItem() {
        return currentGunItem;
    }

    @Nullable
    public BedrockPart getAdditionalMagazineNode() {
        return additionalMagazineNode;
    }

    @Nullable
    public List<BedrockPart> getIronSightPath() {
        return ironSightPath;
    }

    @Nullable
    public List<BedrockPart> getIdleSightPath() {
        return idleSightPath;
    }

    @Nullable
    public List<BedrockPart> getThirdPersonHandOriginPath() {
        return thirdPersonHandOriginPath;
    }

    @Nullable
    public List<BedrockPart> getFixedOriginPath() {
        return fixedOriginPath;
    }

    @Nullable
    public List<BedrockPart> getGroundOriginPath() {
        return groundOriginPath;
    }

    @Nullable
    public List<BedrockPart> getMuzzleFlashPosPath() {
        return muzzleFlashPosPath;
    }

    @Nullable
    public List<BedrockPart> getScopePosPath() {
        return scopePosPath;
    }

    @Nullable
    public List<BedrockPart> getRefitAttachmentViewPath(AttachmentType type) {
        return refitAttachmentViewPath.get(type);
    }

    @Nullable
    public ShellRender getShellRender(int index) {
        if (index < 0 || index >= shellRenderList.size()) {
            return null;
        }
        return shellRenderList.get(index);
    }

    @Nullable
    public BedrockPart getRootNode() {
        return root;
    }

    public boolean getRenderHand() {
        return renderHand;
    }

    public void setRenderHand(boolean renderHand) {
        this.renderHand = renderHand;
    }
}
