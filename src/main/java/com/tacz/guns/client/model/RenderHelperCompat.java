package com.tacz.guns.client.model;

import java.nio.FloatBuffer;
import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.IItemRenderer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.tacz.guns.client.ClientTickHelper;
import com.tacz.guns.client.animation.third.AnimationRenderContext;
import com.tacz.guns.client.animation.third.PlayerModelRenderState;
import com.tacz.guns.mcshim.HumanoidArm;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.LightTexture;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;

/**
 * 客户端侧补完 {@code com.tacz.guns.util.RenderHelper} 在 1.7.10 缺失的 3 个渲染批次方法。
 *
 * <p>
 * 上游 {@code util/RenderHelper.java} 的 {@code getBufferSource(ItemDisplayContext)} /
 * {@code endBatch(...)} / {@code renderFirstPersonArm(...)} 都直接依赖 1.20 的 {@code MultiBufferSource} /
 * {@code PoseStack} / {@code HumanoidArm} 渲染外壳，而 {@code util} 包在 L1 编译边界上不允许引用 mcshim
 * 渲染类型（见 {@code util/RenderHelper} 类注释）⇒ 由 client 层自建，即本类。
 *
 * <p>
 * 1.7.10 实现：{@link #getBufferSource} 按 {@code ItemDisplayContext} 缓存一个
 * {@link MultiBufferSource.BufferSource}（同一上下文下枪体/配件/功能渲染器/粒子窗口共用，最后统一
 * {@code endBatch()}，GL 切换最少、批次内顶点顺序稳定）；{@link #endBatch} 直接转给 mcshim
 * {@code BufferSource#endBatch()}（1.7.10 没有 VBO 上传，走 Tessellator 逐批重放）；
 * {@link #renderFirstPersonArm} 见方法注释。
 */
public final class RenderHelperCompat {

    private static final Map<ItemDisplayContext, MultiBufferSource.BufferSource> BUFFER_SOURCES = new EnumMap<>(
        ItemDisplayContext.class);

    private RenderHelperCompat() {}

    // ================================================================================================
    // 视图矩阵（GL_MODELVIEW 的「纯相机部分」）—— 第三人称枪口世界坐标用
    // ================================================================================================

    /** GL 列主序矩阵读回用的复用缓冲（渲染线程独占）。 */
    private static final FloatBuffer GL_MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    /** 本帧视图矩阵（世界→相机）；由 {@link #captureViewMatrix()} 在相机矩阵建立后写入。 */
    private static final Matrix4f VIEW_MATRIX = new Matrix4f();

    /**
     * 每帧由 {@code mixin/client/GameRendererMixin}（{@code EntityRenderer#setupCameraTransform} RETURN）调用，
     * 记录<b>纯视图矩阵</b>：{@code setupCameraTransform} 以 {@code glLoadIdentity()} 开头
     * （{@code EntityRenderer.java:683}），之后只叠加相机效果（hurt :690 / view-bob :694 / 传送门扭曲 /
     * {@code orientCamera} :715，含第三人称后拉与 roll），世界几何与实体都是随后才 push 的 MODEL 变换。
     */
    public static void captureViewMatrix() {
        readGlModelView(VIEW_MATRIX);
    }

    /**
     * 本帧视图矩阵的逆（相机空间→世界空间）。
     *
     * <p>
     * 用途：1.7.10 的实体/手臂帧在 {@code GL_MODELVIEW} 里，而 TACZ 的 {@code PoseStack} 是新建空栈 ⇒
     * 相机相对实体帧 = {@code VIEW⁻¹ · GL_MODELVIEW}（见 {@code GunItemRendererWrapper#captureThirdPersonMuzzle}）。
     */
    public static Matrix4f viewMatrixInverse() {
        return new Matrix4f(VIEW_MATRIX).invert();
    }

    /** 当前 {@code GL_MODELVIEW} 的 JOML 矩阵（列主序 → 行主序转置）。 */
    public static Matrix4f currentModelViewMatrix() {
        Matrix4f matrix = new Matrix4f();
        readGlModelView(matrix);
        return matrix;
    }

    /** 读 16 个 float 的 GL 矩阵并转成 JOML（GL 列主序，JOML setter 是行主序 ⇒ 转置）。 */
    private static void readGlModelView(Matrix4f dest) {
        GL_MATRIX_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, GL_MATRIX_BUFFER);
        dest.set(
            GL_MATRIX_BUFFER.get(0),
            GL_MATRIX_BUFFER.get(4),
            GL_MATRIX_BUFFER.get(8),
            GL_MATRIX_BUFFER.get(12),
            GL_MATRIX_BUFFER.get(1),
            GL_MATRIX_BUFFER.get(5),
            GL_MATRIX_BUFFER.get(9),
            GL_MATRIX_BUFFER.get(13),
            GL_MATRIX_BUFFER.get(2),
            GL_MATRIX_BUFFER.get(6),
            GL_MATRIX_BUFFER.get(10),
            GL_MATRIX_BUFFER.get(14),
            GL_MATRIX_BUFFER.get(3),
            GL_MATRIX_BUFFER.get(7),
            GL_MATRIX_BUFFER.get(11),
            GL_MATRIX_BUFFER.get(15));
    }

    /**
     * 对应上游 {@code RenderHelper#getBufferSource(ItemDisplayContext)}。同一 {@code transformType} 反复调用
     * 返回<b>同一个</b> {@code BufferSource}，调用方渲染完必须 {@link #endBatch(MultiBufferSource)} flush。
     */
    public static MultiBufferSource.BufferSource getBufferSource(ItemDisplayContext transformType) {
        ItemDisplayContext key = transformType == null ? ItemDisplayContext.NONE : transformType;
        return BUFFER_SOURCES.computeIfAbsent(key, ignored -> MultiBufferSource.immediate());
    }

    /**
     * 对应上游 {@code RenderHelper#endBatch(MultiBufferSource)}：flush 该 source 的全部批次。
     * 只对 mcshim 的 {@link MultiBufferSource.BufferSource} 有效（其它实现无法 flush，直接忽略）。
     */
    public static void endBatch(MultiBufferSource bufferSource) {
        if (bufferSource instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
    }

    /**
     * 对应上游 {@code RenderHelper#endBatch(MultiBufferSource, RenderType)}：只 flush 指定类型。
     */
    public static void endBatch(MultiBufferSource bufferSource, RenderType renderType) {
        if (bufferSource instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(renderType);
        }
    }

    // ================================================================================================
    // 第一人称手臂的相机空间重建（1.7.10 移植特有；动机见 renderFirstPersonArm 的 javadoc）
    // ================================================================================================

    /** 手臂盒长轴：{@code ModelBiped} 的 arm box y∈[-2,10]px = 12px = 0.75 格。 */
    private static final float ARM_BOX_LENGTH = 0.75F;

    /** 手臂长轴在手部局部空间的单位向量（臂盒从肩端 y=-2px 指向腕端 y=+10px）。 */
    private static final Vector3f ARM_LOCAL_AXIS = new Vector3f(0.0F, 1.0F, 0.0F);

    /**
     * 相机空间肩部锚点（右臂）：1.7.10 原版第一人称「空手」手臂帧里上臂肩端的中点。
     *
     * <p>
     * 帧逐句取自 {@code ItemRenderer#renderItemInFirstPerson:516-543}（空手分支，静止：swing=0、equip=1）：
     * {@code T(0.8*0.8, -0.75*0.8, -0.9*0.8) · Ry(45) · T(-1, 3.6, 3.5) · Rz(120) · Rx(200) · Ry(-135) · T(5.6,0,0)}。
     * 1.20 没有等价常量：它的手臂直接挂在枪的手骨帧里（{@code util/RenderHelper#renderFirstPersonArm:99-128}
     * → {@code PlayerRenderer#renderRightHand/renderHand:187-212}）。
     */
    private static final Vector3f ARM_SHOULDER_RIGHT = tacz$vanillaArmShoulder();

    /** 相机空间肩部锚点（左臂）= 右臂的 x 镜像（原版空手分支只有右臂，左臂按 {@code HumanoidArm} 镜像）。 */
    private static final Vector3f ARM_SHOULDER_LEFT = new Vector3f(
        -ARM_SHOULDER_RIGHT.x,
        ARM_SHOULDER_RIGHT.y,
        ARM_SHOULDER_RIGHT.z);

    /**
     * 臂盒端点在「arm part 局部坐标」里的位置：{@code armRenderer.render(0.0625F)} 之前、
     * 但已含 {@link ModelRenderer} 自己那层 {@code glTranslatef(rotationPoint*scale, ...)} 的坐标。
     *
     * <p>
     * {@code ModelBiped} 的臂盒：右臂 {@code addBox(-3,-2,-2,4,12,4)} + pivot {@code (-5,2,0)}px；
     * 左臂 {@code addBox(-1,-2,-2,4,12,4)} + pivot {@code (5,2,0)}px（{@code ModelBiped.java:24-40}）。
     * 盒中轴 x = ±1px，y 端 = -2px（肩）/ +10px（腕）。
     */
    private static Vector3f tacz$armPartPoint(HumanoidArm arm, float yPx) {
        float pivotX = arm == HumanoidArm.LEFT ? 5.0F : -5.0F;
        float boxCenterX = arm == HumanoidArm.LEFT ? 1.0F : -1.0F;
        return new Vector3f((pivotX + boxCenterX) / 16.0F, (2.0F + yPx) / 16.0F, 0.0F);
    }

    /** 按 {@link #ARM_SHOULDER_RIGHT} 的 javadoc 逐句搭出原版空手手臂帧，取右臂肩端。 */
    private static Vector3f tacz$vanillaArmShoulder() {
        Matrix4f frame = new Matrix4f();
        frame.translate(0.8F * 0.8F, -0.75F * 0.8F, -0.9F * 0.8F);
        frame.rotateY((float) Math.toRadians(45.0F));
        frame.translate(-1.0F, 3.6F, 3.5F);
        frame.rotateZ((float) Math.toRadians(120.0F));
        frame.rotateX((float) Math.toRadians(200.0F));
        frame.rotateY((float) Math.toRadians(-135.0F));
        frame.translate(5.6F, 0.0F, 0.0F);
        return frame.transformPosition(tacz$armPartPoint(HumanoidArm.RIGHT, -2.0F));
    }

    /**
     * 重建一条手臂的相机空间矩阵：肩端钉在 {@link #ARM_SHOULDER_RIGHT} / {@link #ARM_SHOULDER_LEFT}，
     * 腕端钉在调用方（枪的手骨帧）算出的 {@code wrist} 上；12px 臂盒沿两点连线摆放、长度按两点距离缩放。
     *
     * @return {@code null} 表示这一帧不画（非有限值 / 两点重合 / 手腕已到相机背后 —— 那必被近平面裁）
     */
    @Nullable
    private static Matrix4f tacz$buildArmFrame(HumanoidArm arm, Vector3f wrist) {
        if (!Float.isFinite(wrist.x) || !Float.isFinite(wrist.y) || !Float.isFinite(wrist.z) || wrist.z >= -1.0e-3F) {
            return null;
        }
        Vector3f shoulder = arm == HumanoidArm.LEFT ? ARM_SHOULDER_LEFT : ARM_SHOULDER_RIGHT;
        Vector3f direction = new Vector3f(wrist).sub(shoulder);
        float length = direction.length();
        if (!(length > 1.0e-3F) || !Float.isFinite(length)) {
            return null;
        }
        direction.div(length);
        Quaternionf rotation = new Quaternionf().rotationTo(ARM_LOCAL_AXIS, direction);
        Vector3f shoulderLocal = tacz$armPartPoint(arm, -2.0F);
        // M · Q_肩 = shoulder、M · Q_腕 = wrist（Q = 含 rotationPoint 的臂盒端点，见 tacz$armPartPoint）。
        return new Matrix4f().translation(shoulder)
            .rotate(rotation)
            .scale(1.0F, length / ARM_BOX_LENGTH, 1.0F)
            .translate(-shoulderLocal.x, -shoulderLocal.y, -shoulderLocal.z);
    }

    /**
     * 对应上游 {@code RenderHelper#renderFirstPersonArm(LocalPlayer, HumanoidArm, PoseStack, int, MultiBufferSource)}：
     * 重画一条手臂，让手臂与枪体共用同一套位姿。
     *
     * <p>
     * 上游 1.20：{@code util/RenderHelper#renderFirstPersonArm:99-128} → {@code PlayerRenderer#renderRightHand}
     * → {@code renderHand:195-212}，手臂就是画在调用方给的 PoseStack 里；调用方
     * {@code RightHandRender:35-43} / {@code LeftHandRender:35-43} 传的是「枪的手骨帧 × Rz(180°)」，
     * 即上游由动画作者摆放的手骨决定手臂位置（1.20 {@code PlayerModel} 手臂与 1.7.10 {@code ModelBiped}
     * 同为 4×12×4px、同 pivot：{@code PlayerModel.createMesh:86-102} ↔ {@code ModelBiped.java:24-40}）。
     *
     * <p>
     * <b>1.7.10 不能照抄这个位姿</b>：手骨帧里再叠上臂盒的 pivot 之后，给出的是手臂「肩端」的位置，
     * 而枪包动画把这个点摆在相机附近甚至背后（默认包静止姿态静态重放：右臂肩端 z=+0.44~+0.76，
     * 左臂 z=-0.11~-0.49；相机空间 -Z 为前）⇒ 12px 的臂盒整体跨过相机平面，被近平面裁成巨大多边形
     * （丢面），皮肤 4×12 texel 被放大成色块。修法：<b>腕端仍取上游手骨帧</b>（= 动画作者摆放的手位：
     * 右手在握把、左手在护木），<b>肩端改用原版第一人称空手手臂的相机空间肩点</b>
     * （{@link #ARM_SHOULDER_RIGHT}），两点之间重建手臂（{@link #tacz$buildArmFrame}）：方向自然、
     * 长度有界、整条手臂都在相机前方，不再被近平面裁。
     *
     * <p>
     * 1.7.10 的等价入口 {@code RenderPlayer#renderFirstPersonArm:455-462} 只有
     * {@code setRotationAngles} + {@code bipedRightArm.render(0.0625F)}，本方法照抄并补上 1.7.10 侧
     * 必需的手工状态（绑皮肤、lightmap、GL_RESCALE_NORMAL、enableStandardItemLighting、关背面裁剪、
     * glColor3f —— 依据 1.7.10 {@code ItemRenderer.java:272/:526/:552-553}、{@code RenderPlayer.java:458}）。
     * 平台差异保留：1.7.10 只画手臂本体一层，没有 1.20 的 sleeve 外层。
     */
    public static void renderFirstPersonArm(EntityPlayerSP player, HumanoidArm arm, PoseStack poseStack, int light,
        MultiBufferSource bufferSource) {
        if (player == null) {
            return;
        }
        if (!RENDER_FIRST_PERSON_VANILLA_ARM) {
            return;
        }
        Render renderer = RenderManager.instance.getEntityRenderObject(player);
        if (!(renderer instanceof RenderPlayer renderPlayer)) {
            return;
        }
        ModelBiped armModel = renderPlayer.modelBipedMain;
        // AnimationRenderContext.hand(player)：等价上游 renderFirstPersonArm:113-114 的 try-with-resources。
        // 缺了它 InnerThirdPersonManager#setRotationAnglesHead 的 isFirstPersonHand 闸门失效 ⇒ 第三人称
        // 持枪姿态（animateGunHold 的 rotateAngleX=-1.4F）写进共享 ModelBiped。
        try (PlayerModelRenderState tacz$modelState = new PlayerModelRenderState(armModel);
            AnimationRenderContext tacz$firstPersonHandScope = AnimationRenderContext.hand(player)) {
            armModel.onGround = 0.0F;
            armModel.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, player);
            ModelRenderer armRenderer = arm == HumanoidArm.LEFT ? armModel.bipedLeftArm : armModel.bipedRightArm;
            // 上游 renderHand:200-204 是 setupAnim(全 0) + xRot=0（ArmPose 恒 EMPTY）⇒ 三轴全 0。
            // 1.7.10 ModelBiped 是共享实例，这里显式归零，不依赖 PlayerModelMixin 的归零条件。
            armRenderer.rotateAngleX = 0.0F;
            armRenderer.rotateAngleY = 0.0F;
            armRenderer.rotateAngleZ = 0.0F;

            // 腕点 = 上游手骨帧 × 臂盒腕端（已含 ModelBiped 的 rotationPoint）。
            Vector3f wrist = poseStack.last()
                .pose()
                .transformPosition(tacz$armPartPoint(arm, 10.0F));
            // 兜底（可调）：腕点离眼睛过近就不画。默认 0 = 关闭（历史锚点日志见字段 javadoc）。
            if (MIN_HAND_ANCHOR_DISTANCE > 0.0F && -wrist.z < MIN_HAND_ANCHOR_DISTANCE) {
                return;
            }
            Matrix4f armFrame = tacz$buildArmFrame(arm, wrist);
            if (armFrame == null) {
                return;
            }
            PoseStack armPoseStack = new PoseStack();
            armPoseStack.last()
                .pose()
                .set(armFrame);

            GL11.glColor3f(1.0F, 1.0F, 1.0F);
            // 1.7.10 的 lightmap 是「两段打包值」，直接喂 setLightmapTextureCoords（见 mcshim.LightTexture 布局）。
            OpenGlHelper
                .setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, LightTexture.u(light), LightTexture.v(light));
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(player.getLocationSkin());
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GL11.glDepthMask(false);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableStandardItemLighting();

            armPoseStack.pushToGL();
            try {
                armRenderer.render(0.0625F);
            } finally {
                // 必须成对：GL 固定管线 MODELVIEW 栈深有限，一次泄漏就会污染后续所有世界渲染的相机矩阵；
                // ModelBase#render 走显示列表，出错会抛异常，故用 finally 保证还原。
                armPoseStack.popFromGL();
                RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            }

            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    /**
     * 手臂<b>腕点</b>到眼睛的最小距离（格）。小于它就跳过绘制。
     *
     * <p>
     * <b>默认 0 = 关闭。</b>历史实机日志里手骨锚点的相机空间 {@code z} 全为正（相机背后 9~95cm），
     * 一旦据此 return 就是「整条手臂不画」= 藏症状；现在腕点从上游手骨帧取、再经
     * {@link #tacz$buildArmFrame} 重建（腕点已到相机背后时会自动不画，见该方法），
     * 该值是给第三方枪包把腕点摆到眼前的病理数据留的单点兜底。
     */
    public static float MIN_HAND_ANCHOR_DISTANCE = 0.0F;

    /**
     * 第一人称是否绘制<b>原版皮肤手臂</b>（{@code RenderPlayer#renderFirstPersonArm} 等价物）。
     *
     * <p>
     * 默认 {@code true} = 上游行为；置 {@code false} 是单点 kill switch（只显示枪模、不画手）。
     * 手臂位姿见 {@link #renderFirstPersonArm}：腕端取上游手骨帧、肩端取原版空手手臂的相机空间肩点，
     * 12px 臂盒在两点之间重建 —— 这条路径自身不会把手塞进近平面。
     */
    public static boolean RENDER_FIRST_PERSON_VANILLA_ARM = true;

    // ================================================================================================
    // 共享诊断钩子（DEBUG_RENDER 默认 false ⇒ 零日志；r7 起各 scope 已清掉自己的逐帧调用点，
    // 这些入口保留给跨 scope 的调用方复用，最后一个调用方删除后可整体移除）
    // ================================================================================================

    /** 第一人称手持时的诊断开关（{@code FirstPersonRenderHandler} 用它决定是否推进诊断帧号）。 */
    public static boolean DEBUG_RENDER = false;

    /** 诊断帧序号：由 {@code FirstPersonRenderHandler#tickAnimation}（每帧 FML {@code RenderTickEvent.START}）推进。 */
    private static long tacz$debugFrameId;

    /** 每帧调用一次：推进帧号。 */
    public static void beginDebugFrame() {
        tacz$debugFrameId++;
    }

    /** 当前帧号。 */
    public static long debugFrameId() {
        return tacz$debugFrameId;
    }

    /** 清空缓存（资源重载时调用，避免持有旧的缓冲）。 */
    public static void clear() {
        for (MultiBufferSource.BufferSource source : BUFFER_SOURCES.values()) {
            source.endBatch();
        }
        BUFFER_SOURCES.clear();
    }

    // ================================================================================================
    // 直接转给 util/RenderHelper（它们不碰 mcshim 渲染外壳，所以留在 util 侧）
    // ================================================================================================

    /**
     * 对应上游 {@code RenderHelper#enableItemEntityStencilTest()}：转给
     * {@link com.tacz.guns.util.RenderHelper#enableItemEntityStencilTest()}。
     *
     * <p>
     * 保留在这里只是为了让 client/model/** 的调用点只 import 一个类。
     */
    public static void enableItemEntityStencilTest() {
        com.tacz.guns.util.RenderHelper.enableItemEntityStencilTest();
    }

    /** 对应上游 {@code RenderHelper#disableItemEntityStencilTest()}。 */
    public static void disableItemEntityStencilTest() {
        com.tacz.guns.util.RenderHelper.disableItemEntityStencilTest();
    }

    /** 对应上游 {@code RenderHelper#isOptifineInstalled()}。 */
    public static boolean isOptifineInstalled() {
        return com.tacz.guns.util.RenderHelper.isOptifineInstalled();
    }

    /** 1.7.10 的客户端玩家实例；上游直接写 {@code Minecraft.getInstance().player}。 */
    public static EntityPlayerSP localPlayer() {
        return Minecraft.getMinecraft().thePlayer;
    }

    // ================================================================================================
    // ItemDisplayContext ←→ 1.7.10 IItemRenderer.ItemRenderType 映射
    // ================================================================================================

    /**
     * 1.20 的 {@code ItemDisplayContext} 与 1.7.10 {@code IItemRenderer.ItemRenderType} 的映射。
     *
     * <p>
     * 1.20 有 9 个上下文，1.7.10 只有 5 个渲染类型，且「左手」是 {@code RenderItem#renderItem(..., leftHand)}
     * 的布尔参数：{@code EQUIPPED_FIRST_PERSON} → FIRST_PERSON_RIGHT/LEFT_HAND；{@code EQUIPPED} →
     * THIRD_PERSON_RIGHT/LEFT_HAND；{@code ENTITY} → GROUND；{@code INVENTORY} → GUI；
     * {@code FIRST_PERSON} → FIRST_PERSON_RIGHT_HAND；{@code FIXED} / {@code HEAD} 无自定义渲染入口 ⇒ 退化为 GROUND。
     *
     * @param type     1.7.10 的渲染类型，{@code null} 视为 {@code ENTITY}
     * @param leftHand 是否左手（1.7.10 由 {@code RenderItem} 传入）
     */
    public static ItemDisplayContext displayContextOf(IItemRenderer.ItemRenderType type, boolean leftHand) {
        if (type == null) {
            return ItemDisplayContext.GROUND;
        }
        switch (type) {
            case EQUIPPED_FIRST_PERSON:
                return ItemDisplayContext.firstPerson(leftHand);
            case EQUIPPED:
                return ItemDisplayContext.thirdPerson(leftHand);
            case INVENTORY:
                return ItemDisplayContext.GUI;
            case ENTITY:
            default:
                return ItemDisplayContext.GROUND;
        }
    }

    /** {@link #displayContextOf} 的反向判定：该渲染类型是否是第一人称。 */
    public static boolean isFirstPerson(IItemRenderer.ItemRenderType type) {
        return type == IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON;
    }

    /**
     * 1.7.10 {@code IItemRenderer#renderItem} 各渲染类型应使用的打包亮度（lightmap）。
     *
     * <p>
     * 上游 1.20 的 {@code BlockEntityWithoutLevelRenderer#renderByItem} 由调用方传 {@code packedLight}：
     * 世界内的手持/掉落物用<b>实体亮度</b>（{@code ItemInHandLayer} 来自 {@code LivingEntityRenderer} 的
     * {@code getLightColor}；{@code ItemEntityRenderer} 取实体位置亮度），GUI 用满亮度。
     * 1.7.10 的 {@code RenderManager#doRender}（{@code RenderManager.java:267-276}）同样把实体亮度写进
     * lightmap 纹理单元，{@code Tessellator#setBrightness} 则是逐顶点覆盖 ⇒ 移植侧必须显式传实体亮度，
     * 否则写死 {@link LightTexture#FULL_BRIGHT} 会让世界里的枪/配件/弹药恒满亮（夜里、洞穴里发光）。
     *
     * @param type 1.7.10 渲染类型
     * @param data {@code renderItem} 的 data 参数（EQUIPPED/ENTITY 的 {@code data[0]} 是被渲染的实体）
     * @return 可直接喂给 {@code VertexConsumer#uv2} / {@link LightTexture} 的打包亮度
     */
    public static int packedLightFor(IItemRenderer.ItemRenderType type, Object[] data) {
        if ((type == IItemRenderer.ItemRenderType.EQUIPPED || type == IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON
            || type == IItemRenderer.ItemRenderType.ENTITY) && data != null
            && data.length > 0
            && data[0] instanceof Entity entity) {
            // 与 RenderManager 同款（Entity#getBrightnessForRender 即 sky<<20 | block<<4）
            return entity.getBrightnessForRender(ClientTickHelper.getPartialTick());
        }
        // GUI（INVENTORY）/ 地图：原版走 GUI 光照，lightmap 纹理单元此时未启用，取满亮度
        return LightTexture.FULL_BRIGHT;
    }

    /**
     * <b>GUI 槽位缩放（移植新增，必须有）</b>：{@code INVENTORY} 分支要把 SlotModel 的槽位四边形放大 16 倍，
     * 才能铺满 16×16 的图标格；否则枪械/配件/弹药/工作台的背包图标退化成一个 1 像素的点。
     *
     * <p>
     * 依据：上游 1.20 的 {@code ItemRenderer#renderGuiItem} 施加 {@code scale(16, -16, 16)}；1.7.10 等价入口
     * {@code ForgeHooksClient#renderInventoryItem}（ForgeHooksClient.java:187-205）只做
     * {@code glDisable(GL_LIGHTING)} + {@code glTranslatef} + {@code renderItem(INVENTORY, …)}，<b>无任何缩放</b>。
     *
     * <p>
     * Y 方向必须 {@code scale(16, -16, 16)} + {@code translate(0, -1, 0)}：1.7.10 GUI 空间 {@code +Y 向下}，
     * 原版图标映射（RenderItem#renderIcon，RenderItem.java:756-765）要求 {@code v=0} 落在格子顶边；
     * 只写 {@code scale(16,16,16)} 会把图标上下颠倒，只写 −16 则整块落到 {@code y ∈ [-16,0]}。
     * 单点回滚：改回 {@code scale(16,16,16)} 并去掉 translate。
     *
     * <p>
     * 作用范围仅 {@code ItemRenderType.INVENTORY}：唯一派发链是
     * {@code ForgeHooksClient#renderInventoryItem}（:183/:202）← {@code RenderItem#renderItemAndEffectIntoGUI}
     * （:583），调用方全是 GUI/HUD，不含世界内展示框 / 掉落物 / 第三人称手持，16× 缩放不会外溢。
     */
    public static void applyGuiSlotScale(PoseStack poseStack) {
        poseStack.scale(GUI_SLOT_SCALE, -GUI_SLOT_SCALE, GUI_SLOT_SCALE);
        poseStack.translate(0.0F, -GUI_SLOT_TRANSLATE_Y, 0.0F);
    }

    /** 见 {@link #applyGuiSlotScale(PoseStack)}。 */
    private static final float GUI_SLOT_SCALE = 16.0F;

    /**
     * {@link #applyGuiSlotScale(PoseStack)} 里「缩放之后」的 Y 平移量（模型空间单位）：
     * 配合 {@code scale(16, -16, 16)} 把 {@code y ∈ [-16, 0]} 抬到 {@code [0, 16]}。
     */
    private static final float GUI_SLOT_TRANSLATE_Y = 1.0F;
}
