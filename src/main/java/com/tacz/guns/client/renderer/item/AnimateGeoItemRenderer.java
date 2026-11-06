package com.tacz.guns.client.renderer.item;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.IFPGeoItemRenderer;
import com.maydaymemory.mae.basic.DummyPose;
import com.maydaymemory.mae.basic.Pose;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.event.BeforeRenderHandEvent;
import com.tacz.guns.api.item.IAnimationItem;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.animation.statemachine.ItemAnimationStateContext;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.renderer.other.CameraAngles;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.util.math.MathUtil;
import com.tacz.guns.util.time.GunTime;

/**
 * 抽象的基岩版动画物品模型BEWLR，包含一些默认实现
 * 
 * @param <M>   基岩版模型
 * @param <CTX> 动画状态机上下文
 */
public abstract class AnimateGeoItemRenderer<M extends BedrockAnimatedModel, CTX extends ItemAnimationStateContext>
    implements IFPGeoItemRenderer, IItemRenderer {

    @Nullable
    protected LuaAnimationStateMachine<CTX> stateMachine;
    protected M model;
    public ResourceLocation textureLocation;

    public AnimateGeoItemRenderer() {}

    // ================================================================================================
    // 1.7.10 IItemRenderer 入口（1.20 的 BlockEntityWithoutLevelRenderer 在 1.7.10 不存在）
    // ================================================================================================

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return item != null;
    }

    /**
     * {@code EQUIPPED} 分支必须声明 {@link ItemRendererHelper#EQUIPPED_BLOCK}，否则第三人称手持的 3D 枪模
     * 会「上下颠倒 / 前后翻转」。
     *
     * <p>
     * 1.7.10 的自定义物品渲染器会被<b>两层</b>「2D 扁平物品」变换包夹，两层都是为贴图方块/纸片设计的：
     * ① {@code RenderPlayer#renderEquippedItems}：{@code is3D == false} 且 {@code Item#isFull3D() == false}
     * 时走 2D 纸片分支 {@code T(0.25,0.1875,-0.1875)·S(0.375)·Rz(60)·Rx(-90)·Rz(20)}
     * （build/rfg/minecraft-src/java/net/minecraft/client/renderer/entity/RenderPlayer.java:377-385）；
     * ② {@code ForgeHooksClient#renderEquippedItem} 的 {@code else} 分支又一次 2D 纸片变换
     * {@code T(0,-0.3,0)·S(1.5)·Ry(50)·Rz(335)·T(-0.9375,-0.0625,0)}（ForgeHooksClient.java:225-233）。
     * 两者叠加即玩家看到的翻转。
     *
     * <p>
     * 这里只修第二层：{@code EQUIPPED_BLOCK} 作用于所有 {@code EQUIPPED} 调用（第三人称手持 +
     * {@code HumanoidOffhandRender} 的挂枪），是两条路径共享的唯一一层。
     * 第一层的 {@code BLOCK_3D} <b>故意仍返回 false</b>：那会引入 {@code S(-0.375,-0.375,0.375)} 的 X/Y 镜像，
     * 而 {@code GunItemTransforms#apply} 自己已有一次 scale(-1,-1,1)，两次镜像互相抵消只会翻回去；
     * 要动第一层必须先把「手持」与「挂枪」两条路径拆开。
     *
     * <p>
     * {@code ENTITY}（掉落物 / 展示框）分支：1.7.10 {@code ForgeHooksClient#renderEntityItem}
     * （{@code ForgeHooksClient.java:89-104}）把「自转」与「浮沉」交给这两个 helper 决定是否施加：
     * {@code ENTITY_ROTATION} → {@code glRotatef(spin)}、{@code ENTITY_BOBBING} → 跳过
     * {@code glTranslatef(0,-bob,0)}。1.20 {@code ItemEntityRenderer.java:66-69} 两种情况都施加
     * ⇒ 非展示框必须返回 {@code true} 才与上游同帧；展示框（1.7.10 {@code RenderItem.renderInFrame}）
     * 在 1.20 走 {@code FIXED}，既不自转也不浮沉 ⇒ 保持 {@code false}。
     *
     * <p>
     * 上游 1.20 不存在该问题：{@code ItemInHandLayer#renderArmWithItem} 只给一次「手臂骨骼帧 + 物品显示帧」，
     * 再交给 {@code ItemDisplayContext.THIRD_PERSON_RIGHT_HAND} 的 display 变换。
     */
    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        if (type == ItemRenderType.EQUIPPED) {
            // 只接管 EQUIPPED_BLOCK；INVENTORY 的 helper 语义不同，保持 false。
            return helper == ItemRendererHelper.EQUIPPED_BLOCK;
        }
        if (type == ItemRenderType.ENTITY && !RenderItem.renderInFrame) {
            return helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING;
        }
        return false;
    }

    /**
     * 1.7.10 的实体/物品渲染入口，转发到上游同名的 {@link #renderByItem(ItemStack, ItemDisplayContext,
     * PoseStack, MultiBufferSource, int, int)}。
     *
     * <p>
     * 槽位变换由 1.7.10 的 {@code ItemRenderer} 施加在 GL_MODELVIEW 上，因此这里用 identity
     * {@link PoseStack}，避免与 GL 矩阵二次相乘。上下文映射见 {@link RenderHelperCompat#displayContextOf}。
     *
     * <p>
     * {@code EQUIPPED} 分支额外乘一次 {@link #TACZ_EQUIPPED_PAPER_FRAME_INV}（1.7.10「2D 纸片物品帧」的
     * 逆矩阵），把第三人称手持的坐标系拉回手臂骨骼帧，也就是 1.20 侧 BEWLR 实际收到的坐标系
     * （推导见该常量的 javadoc）。其余「挂枪」路径（{@code HumanoidOffhandRender}）已改为直接调
     * {@code renderByItem(FIXED, …)}、绕开本入口，因此这里无需再做「外部帧」标记。
     *
     * <p>
     * {@code ENTITY} 分支补 1.7.10 缺的那层「把物品居中 + 半格修正」（推导见方法内注释）。
     */
    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        PoseStack poseStack = new PoseStack();
        ItemDisplayContext displayContext = RenderHelperCompat.displayContextOf(type, false);
        // INVENTORY：1.7.10 的 ForgeHooksClient#renderInventoryItem 只把 GL 平移到图标左上角，
        // 没有 1.20 ItemRenderer#renderGuiItem 那个 scale(16,-16,16)；SlotModel 的四边形只有 1×1 单位，
        // 不补这一步背包图标会退化成 1 个像素。依据见 RenderHelperCompat#applyGuiSlotScale。
        if (type == ItemRenderType.INVENTORY) {
            RenderHelperCompat.applyGuiSlotScale(poseStack);
        } else if (type == ItemRenderType.EQUIPPED) {
            if (ENABLE_EQUIPPED_PAPER_FRAME_FIX) {
                poseStack.mulPoseMatrix(TACZ_EQUIPPED_PAPER_FRAME_INV);
            }
        } else if (type == ItemRenderType.ENTITY) {
            if (RenderItem.renderInFrame) {
                // 展示框：1.20 ItemFrameRenderer.java:107-111 = S(0.5) 之后 FIXED + 无条件的 T(-0.5,-0.5,-0.5)。
                // 1.7.10 RenderItemFrame 已经施加 S(0.5)（ForgeHooksClient.java:147 的 else 分支）⇒ 只补 -0.5。
                displayContext = ItemDisplayContext.FIXED;
                poseStack.translate(-0.5F, -0.5F, -0.5F);
            } else {
                // 掉落物：1.20 帧 = T(0, bob + 0.25, 0)·Ry(spin)·T(-0.5,-0.5,-0.5)
                // （ItemEntityRenderer.java:66-69、:93 + ItemRenderer.java:128-129）。
                // 1.7.10 ForgeHooksClient#renderEntityItem 已施加 Ry(spin) 与 T(0,bob,0)（helper 见
                // shouldUseRenderHelper），但还多一层 S(0.5)（ForgeHooksClient.java:147）、
                // 且缺 +0.25 与 -0.5 ⇒ 补 inv(S0.5)·T(0,0.25,0)·T(-0.5,-0.5,-0.5)。
                poseStack.scale(2.0F, 2.0F, 2.0F);
                poseStack.translate(-0.5F, -0.25F, -0.5F);
            }
        }
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate();
        try {
            renderByItem(
                item,
                displayContext,
                poseStack,
                bufferSource,
                RenderHelperCompat.packedLightFor(type, data),
                0);
        } finally {
            bufferSource.endBatch();
        }
    }

    // ================================================================================================
    // 第三人称 EQUIPPED 的坐标系补偿
    // ================================================================================================

    /**
     * 1.7.10 「2D 纸片物品帧」的逆矩阵，用来把第三人称手持的坐标系拉回<b>手臂骨骼帧</b>。
     *
     * <p>
     * 进入 {@link #renderItem} 时 GL_MODELVIEW 上已叠了三段（逐行抄自 1.7.10 源码）：
     * ① {@code RenderPlayer#renderEquippedItems:317-319} 的 {@code bipedRightArm.postRender(0.0625F)}
     * （手臂骨骼帧，要保留）+ {@code T(-0.0625, 0.4375, 0.0625)}；
     * ② 同文件 {@code :377-385} 的 2D 纸片分支（{@code is3D == false} 且 {@code Item#isFull3D()} 未被覆写）
     * {@code T(0.25,0.1875,-0.1875)·S(0.375)·Rz(60)·Rx(-90)·Rz(20)}；
     * ③ {@code ForgeHooksClient#renderEquippedItem:216-221} 的 {@code T(-0.5,-0.5,-0.5)}。
     *
     * <p>
     * 目标帧不是手臂骨骼帧，而是 1.20 的「手臂骨骼帧 × 物品显示帧」：{@code ItemInHandLayer#renderArmWithItem}
     * 依次 {@code translateToHand} → {@code Rx(-90)} → {@code Ry(180)} → {@code T(±1/16, 0.125, -0.625)}，
     * 然后 {@code ItemRenderer#render} 在 display 变换<b>之后</b>无条件再 {@code T(-0.5,-0.5,-0.5)}
     * （1.20 {@code ItemRenderer.java:128-129}；Forge 补丁
     * {@code patches/.../entity/ItemRenderer.java.patch:16-18} 同样保留）。
     * 枪械物品模型 {@code assets/tacz/models/item/modern_kinetic_gun.json} 只有
     * {@code {"parent":"builtin/entity","gui_light":"front"}}、<b>没有 display 段</b> ⇒ display 恒等，
     * 但那个 {@code -0.5} 照旧有，所以 F_20 必须包含它；否则枪整体偏 {@code T(0.5,0.5,0.5)}
     * （挂点位移模长 0.866 格）。
     *
     * <p>
     * 该显示帧<b>不含镜像</b>，而 {@code GunItemTransforms#apply} 已有 {@code scale(-1,-1,1)} ⇒ 这里不要再补翻转，
     * 否则叠成两次翻转（上一版「上下颠倒」的另一半来源）。补偿 {@code C = inv(F_paper) · F_20} 右乘进 PoseStack。
     * 第 1 步的手臂旋转是可变项但在矩阵左端 ⇒ {@code F_arm · F_paper · inv(F_paper) = F_arm}，故本矩阵可在类初始化
     * 时算一次。
     *
     * <p>
     * <b>若实机仍不对</b>：把 {@link #ENABLE_EQUIPPED_PAPER_FRAME_FIX} 置 {@code false} 即单点停用；若方向差一个
     * 90° 的 X 旋转，说明基座帧里还含 {@code Rx(-90)·Ry(180)}，把目标帧补进本矩阵即可。
     */
    private static final Matrix4f TACZ_EQUIPPED_PAPER_FRAME_INV = tacz$buildPaperFrameInverse();

    /** 单点开关：{@code true} = 启用第三人称手持的纸片帧补偿。 */
    public static boolean ENABLE_EQUIPPED_PAPER_FRAME_FIX = true;

    /**
     * 按 1.7.10 的 GL 调用顺序（每一句都是右乘）逐步搭出纸片帧 {@code F}，再返回 {@code inv(F)}。
     * 行号见 {@link #TACZ_EQUIPPED_PAPER_FRAME_INV} 的 javadoc。
     */
    private static Matrix4f tacz$buildPaperFrameInverse() {
        Matrix4f f = new Matrix4f();
        // RenderPlayer.java:319
        f.translate(-1.0F / 16.0F, 7.0F / 16.0F, 1.0F / 16.0F);
        // RenderPlayer.java:380-384（2D 纸片分支）
        f.translate(0.25F, 0.1875F, -0.1875F);
        f.scale(0.375F, 0.375F, 0.375F);
        f.rotateZ((float) Math.toRadians(60.0F));
        f.rotateX((float) Math.toRadians(-90.0F));
        f.rotateZ((float) Math.toRadians(20.0F));
        // ForgeHooksClient.java:219（EQUIPPED_BLOCK 分支）
        f.translate(-0.5F, -0.5F, -0.5F);
        // 左乘目标帧 F_20 = Rx(-90)·Ry(180)·T(1/16, 0.125, -0.625)·T(-0.5,-0.5,-0.5)
        // （1.20 ItemInHandLayer#renderArmWithItem + ItemRenderer#render:128-129 的无条件 -0.5）。
        // 该帧不含镜像；GunItemTransforms#apply 已有 scale(-1,-1,1)，这里不要再补翻转。
        Matrix4f f20 = new Matrix4f();
        f20.rotateX((float) Math.toRadians(-90.0F));
        f20.rotateY((float) Math.toRadians(180.0F));
        f20.translate(1.0F / 16.0F, 0.125F, -0.625F);
        // 1.20 ItemRenderer#render:129 —— display 变换之后无条件的 -0.5（枪模无 display 段，这层仍然存在）
        f20.translate(-0.5F, -0.5F, -0.5F);
        return f.invert()
            .mul(f20);
    }

    public void setModel(M model) {
        this.model = model;
    }

    public M getModel(ItemStack stack) {
        return model;
    }

    @Nullable
    public LuaAnimationStateMachine<CTX> getStateMachine(ItemStack stack) {
        return stateMachine;
    }

    public ResourceLocation getTextureLocation(ItemStack stack) {
        return textureLocation;
    }

    public RenderType getRenderType(ItemStack stack) {
        return RenderType.entityCutout(getTextureLocation(stack));
    }

    public boolean needReInit(ItemStack stack) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return false;
        }
        return !stateMachine.isInitialized() && stateMachine.getExitingTime() < GunTime.nowMillis();
    }

    public abstract CTX initContext(ItemStack stack, EntityPlayer player, float partialTick);

    public abstract void updateContext(CTX context, ItemStack stack, EntityPlayer player, float partialTick);

    /**
     * 计算并返回切出动画的时长，单位ms
     *
     * @return 保持时间
     */
    public long getPutAwayTime(ItemStack stack) {
        return 0;
    }

    /**
     * 尝试初始化状态机并触发切入信号
     */
    public void tryInit(ItemStack stack, EntityPlayer player, float partialTick) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        if (stateMachine.isInitialized()) {
            stateMachine.exit();
        }

        stateMachine.setContext(initContext(stack, player, partialTick));
        stateMachine.initialize();

        stateMachine.trigger(GunAnimationConstant.INPUT_DRAW);
    }

    /**
     * 尝试退出状态机并触发切出信号
     */
    public void tryExit(ItemStack stack, long putAwayTime) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.processContextIfExist(context -> { context.setPutAwayTime(putAwayTime / 1000F); });
        if (stateMachine.isInitialized()) {
            stateMachine.trigger(GunAnimationConstant.INPUT_PUT_AWAY);

            // KeepingItemRenderer.getRenderer().keep(stack, putAwayTime);
            stateMachine.exit();
            // 需要设置的比动画稍长些，避免意外的重初始化（可能是丢精度了）
            // 延后一tick应该基本没有感知）
            stateMachine.setExitingTime(putAwayTime + 50);
        }
    }

    /**
     * 尝试触发状态机转移
     *
     * @param input 输入信号
     */
    public void triggerAnimation(ItemStack stack, String input) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.trigger(input);
    }

    /**
     * 更新状态机但是不进行模型写入，用于播放音效
     */
    public void visualUpdate(ItemStack stack) {
        var stateMachine = getStateMachine(stack);
        if (stateMachine == null) {
            return;
        }
        stateMachine.visualUpdate();
    }

    /**
     * 应用状态机的世界摄像机动画，暂时只用于玩家
     */
    public void applyLevelCameraAnimation(CameraAngles event, ItemStack stack, EntityPlayerSP player) {
        this.applyLevelCameraAnimation(event, stack, 1);
    }

    public void applyLevelCameraAnimation(CameraAngles event, ItemStack stack, float multiplier) {
        var model = getModel(stack);
        if (model == null) {
            return;
        }
        Quaternionf q = MathUtil.multiplyQuaternion(model.getCameraAnimationObject().rotationQuaternion, multiplier);
        double yaw = Math.asin(2 * (q.w() * q.y() - q.x() * q.z()));
        double pitch = Math.atan2(2 * (q.w() * q.x() + q.y() * q.z()), 1 - 2 * (q.x() * q.x() + q.y() * q.y()));
        double roll = Math.atan2(2 * (q.w() * q.z() + q.x() * q.y()), 1 - 2 * (q.y() * q.y() + q.z() * q.z()));
        yaw = Math.toDegrees(yaw);
        pitch = Math.toDegrees(pitch);
        roll = Math.toDegrees(roll);
        event.setYaw((float) yaw + event.getYaw());
        event.setPitch((float) pitch + event.getPitch());
        event.setRoll((float) roll + event.getRoll());
    }

    /**
     * 应用状态机的手持物品摄像机动画，暂时只用于玩家
     */
    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, EntityPlayerSP player) {
        applyItemInHandCameraAnimation(event, stack, 1);
    }

    public void applyItemInHandCameraAnimation(BeforeRenderHandEvent event, ItemStack stack, float multiplier) {
        var model = getModel(stack);
        if (model == null) {
            return;
        }
        Quaternionf quaternion = MathUtil
            .multiplyQuaternion(model.getCameraAnimationObject().rotationQuaternion, multiplier);
        PoseStack poseStack = event.getPoseStack();
        poseStack.mulPose(quaternion);
    }

    /**
     * 执行额外的变换
     */
    public void doExtraTransforms(PoseStack poseStack, M model, ItemStack stack) {
        applyFirstPersonPositioningTransform(poseStack, model, stack);
    }

    /**
     * 复原 1.20 {@code ItemInHandRenderer#renderHandsWithItems} 在渲染手部前施加的「视角延滞」，使上游
     * {@code renderFirstPerson} 里那对 {@code -0.1Δ} 旋转仍然是<b>抵消项</b>。
     *
     * <p>
     * 上游帧：{@code ItemInHandRenderer.java:332-333} 先在父 PoseStack 上
     * {@code mulPose(XP.rotationDegrees((getViewXRot - xBob) * 0.1F))} + 同式 Y；随后 Forge 的补丁
     * （{@code tmp/MinecraftForge-1.20.1/patches/.../ItemInHandRenderer.java.patch}：
     * {@code renderSpecificFirstPersonHand} 替代 {@code renderArmWithItem}）把 {@code RenderHandEvent}
     * 交给模组 ⇒ TACZ 的 {@code renderFirstPerson} 收到的父帧就是「单位矩阵 + 那对 +0.1Δ」。
     *
     * <p>
     * <b>1.7.10 差异</b>：整个 {@code EntityRenderer#renderHand} 被 {@code RenderHandEvent} 取消
     * （{@code EntityRenderer.java:1434}），SBM 的 {@code FirstPersonRenderHandler} 用
     * {@code glLoadIdentity} 重建相机空间 ⇒ 父帧里<b>没有</b>那对 {@code +0.1Δ}。只保留上游的
     * {@code -0.1Δ} 会把「抵消」变成「附加反向旋转」（快速转视角时枪朝反方向甩）。
     * 这里先把被跳过的原版 pair 补回来，组合仍是上游的 {@code Rx(a)Ry(b)Rx(-a)Ry(-b)}。
     */
    protected static void applyVanillaFirstPersonHandLag(PoseStack poseStack, float xRot, float yRot) {
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot * 0.1F));
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot * 0.1F));
    }

    /**
     * 渲染第一人称，暂时只用于玩家，入口参见 {@link com.tacz.guns.client.event.FirstPersonRenderEvent}
     */
    public void renderFirstPerson(EntityPlayerSP player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack,
        MultiBufferSource bufferSource, int light, float partialTick) {
        M model = getModel(stack);
        if (model != null) {
            // 上游 `LocalPlayer#getViewXRot/getViewYRot`（LocalPlayer.java:190-196）对本地玩家**不插值**
            // （直接返回 getXRot()/getYRot()）；1.7.10 逐值等价物是 rotationPitch/rotationYaw。
            // 只有 `xBob` 那对延滞量才插值（1.7.10 为 prevRenderArmPitch/renderArmPitch，
            // EntityPlayerSP.java:98-101 同为 0.5 一阶滞后）。
            float xRotOffset = Mth.lerp(partialTick, player.prevRenderArmPitch, player.renderArmPitch);
            float yRotOffset = Mth.lerp(partialTick, player.prevRenderArmYaw, player.renderArmYaw);
            float xRot = player.rotationPitch - xRotOffset;
            float yRot = player.rotationYaw - yRotOffset;
            poseStack.pushPose();
            // 补回 1.7.10 手部 pass 被整体跳过的那对原版延滞旋转，使下面的 -0.1 抵消项仍然成立。
            applyVanillaFirstPersonHandLag(poseStack, xRot, yRot);
            poseStack.mulPose(Axis.XP.rotationDegrees(xRot * -0.1F));
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot * -0.1F));
            BedrockPart rootNode = model.getRootNode();
            if (rootNode != null) {
                xRot = (float) Math.tanh(xRot / 25) * 25;
                yRot = (float) Math.tanh(yRot / 25) * 25;
                rootNode.offsetX += yRot * 0.1F / 16F / 3F;
                rootNode.offsetY += -xRot * 0.1F / 16F / 3F;
                rootNode.additionalQuaternion.mul(Axis.XP.rotationDegrees(xRot * 0.05F));
                rootNode.additionalQuaternion.mul(Axis.YP.rotationDegrees(yRot * 0.05F));
            }

            // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)
            poseStack.translate(0, 1.5f, 0);
            // 基岩版模型是上下颠倒的，需要翻转过来。
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            doExtraTransforms(poseStack, model, stack);

            var stateMachine = getStateMachine(stack);
            if (stateMachine != null) {
                stateMachine.processContextIfExist(context -> { updateContext(context, stack, player, partialTick); });
                stateMachine.update();
            }

            model.render(poseStack, ctx, getRenderType(stack), light, OverlayTexture.NO_OVERLAY, bufferSource);

            // 渲染结束后清除动画变换
            model.cleanAnimationTransform();
            poseStack.popPose();
        }
    }

    @ParametersAreNonnullByDefault
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack,
        MultiBufferSource bufferSource, int light, int overlay) {
        if (ctx.firstPerson()) return;
        M model = getModel(stack);
        if (model != null) {
            poseStack.pushPose();
            // 从渲染原点 (0, 24, 0) 移动到模型原点 (0, 0, 0)
            poseStack.translate(0.5, 1.5f, 0.5);
            // 基岩版模型是上下颠倒的，需要翻转过来。
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            model.render(
                poseStack,
                ctx,
                RenderType.entityCutout(getTextureLocation(stack)),
                light,
                overlay,
                bufferSource);
            poseStack.popPose();
        }
    }

    /**
     * 获取摄像机定位组的反相矩阵
     */
    @Nonnull
    public static Matrix4f getPositioningNodeInverse(List<BedrockPart> nodePath) {
        Matrix4f matrix4f = new Matrix4f();
        matrix4f.identity();
        if (nodePath != null) {
            for (int i = nodePath.size() - 1; i >= 0; i--) {
                BedrockPart part = nodePath.get(i);
                // 计算反向的旋转
                matrix4f.rotate(Axis.XN.rotation(part.xRot));
                matrix4f.rotate(Axis.YN.rotation(part.yRot));
                matrix4f.rotate(Axis.ZN.rotation(part.zRot));
                // 计算反向的位移
                if (part.getParent() != null) {
                    matrix4f.translate(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F);
                } else {
                    matrix4f.translate(-part.x / 16.0F, (1.5F - part.y / 16.0F), -part.z / 16.0F);
                }
            }
        }
        return matrix4f;
    }

    public static void applyFirstPersonPositioningTransform(PoseStack poseStack, BedrockAnimatedModel model,
        ItemStack stack) {
        Matrix4f transformMatrix = new Matrix4f();
        transformMatrix.identity();
        // 应用瞄准定位
        List<BedrockPart> idleNodePath = model.getIdleSightPath();

        Matrix4f idleViewMatrix = getPositioningNodeInverse(idleNodePath);

        // 应用瞄准变换
        MathUtil.applyMatrixLerp(transformMatrix, idleViewMatrix, transformMatrix, 1);

        // 应用变换到 PoseStack
        poseStack.translate(0, 1.5f, 0);
        poseStack.mulPoseMatrix(transformMatrix);
        poseStack.translate(0, -1.5f, 0);
    }

    @Override
    public long getPutAwayDuration(ItemStack stack) {
        return this.getPutAwayTime(stack);
    }

    @Nullable
    @Override
    public IFPAnimationInstance createAnimationInstance(ItemStack stack, Entity entity) {
        return new IFPAnimationInstance() {

            private boolean drawn = false;
            private ItemStack lastItem = stack;

            @Override
            public ItemStack currentItem() {
                return lastItem;
            }

            @Override
            public Pose getPose() {
                return DummyPose.INSTANCE;
            }

            @Override
            public void tick(float v) {

            }

            @Override
            public @NotNull Quaternionf getCameraRotation() {
                return new Quaternionf();
            }

            @Override
            public void setCameraRotation(@NotNull Quaternionf quaternionf) {

            }

            @Override
            public Pose getCachedPose() {
                return DummyPose.INSTANCE;
            }

            @Override
            public void updateItem(ItemStack itemStack) {
                lastItem = itemStack;
            }

            @Override
            public void triggerDraw() {
                if (drawn) return;
                drawn = true;
                tryInit(lastItem, Minecraft.getMinecraft().thePlayer, 0);
                if (Minecraft.getMinecraft().thePlayer == null) return;
                TimelessAPI.getGunDisplay(lastItem)
                    .ifPresent(display -> {
                        SoundPlayManager.stopPlayGunSound();
                        SoundPlayManager.playDrawSound(Minecraft.getMinecraft().thePlayer, display);
                    });
            }

            @Override
            public void triggerPutAway() {
                tryExit(lastItem, getPutAwayTime(lastItem));
                if (Minecraft.getMinecraft().thePlayer == null) return;
                TimelessAPI.getGunDisplay(lastItem)
                    .ifPresent(display -> {
                        SoundPlayManager.stopPlayGunSound();
                        SoundPlayManager.playPutAwaySound(Minecraft.getMinecraft().thePlayer, display);
                    });
            }
        };
    }

    @Override
    public boolean isSameItem(ItemStack oldStack, ItemStack newStack) {
        if (oldStack.getItem() instanceof IAnimationItem item) {
            return item.isSame(oldStack, newStack);
        }
        return ItemStack.areItemStacksEqual(oldStack, newStack);
    }

    @Override
    public boolean blockOffhandRender() {
        return true;
    }
}
