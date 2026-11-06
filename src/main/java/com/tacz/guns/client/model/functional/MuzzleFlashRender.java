package com.tacz.guns.client.model.functional;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.particle.ThirdPersonMuzzleParticleManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.util.time.GunTime;

public class MuzzleFlashRender implements IFunctionalRenderer {

    private static final SlotModel MUZZLE_FLASH_MODEL = new SlotModel(true);
    /**
     * 50ms 显示时间
     */
    private static final long TIME_RANGE = 50;
    /**
     * YSM 契约：外部模组在渲染自己的模型时直接写 {@code MuzzleFlashRender.isSelf = true}，
     * 避免自家模型不显示枪口火焰。必须是 public static 字段，不能改成 setter。
     * 见 docs/YSM_COMPAT_HANDOFF.md §2.8。
     */
    public static boolean isSelf = false;
    private static long shootTimeStamp = -1;
    private static float muzzleFlashRandomRotate = 0;
    private static Matrix3f muzzleFlashNormal = new Matrix3f();
    private static Matrix4f muzzleFlashPose = new Matrix4f();

    /**
     * 第三人称开火时间戳（按实体）：让附着在枪口骨骼上的四边形火光对任意射手生效，
     * 随枪械渲染逐帧附着（跟随枪口），其他玩家视角也始终可见。WeakHashMap 避免实体泄漏。
     * 移植说明：上游用 LivingEntity，1.7.10 对应 EntityLivingBase。
     */
    private static final Map<EntityLivingBase, Long> TP_SHOOT_TIMESTAMPS = new WeakHashMap<>();

    private final BedrockGunModel bedrockGunModel;

    public MuzzleFlashRender(BedrockGunModel bedrockGunModel) {
        this.bedrockGunModel = bedrockGunModel;
    }

    public static void onShoot() {
        // 记录开火时间戳
        shootTimeStamp = GunTime.nowMillis();
        // 随机给予枪口火焰的旋转
        muzzleFlashRandomRotate = (float) (Math.random() * 360);
    }

    /**
     * 记录某实体（任意射手）的第三人称开火时间戳，用于附着在枪口骨骼上的火光。
     */
    public static void onEntityShoot(EntityLivingBase entity) {
        TP_SHOOT_TIMESTAMPS.put(entity, GunTime.nowMillis());
    }

    public static void resetTimestamps() {
        shootTimeStamp = -1;
        TP_SHOOT_TIMESTAMPS.clear();
    }

    private static void renderMuzzleFlash(GunDisplayInstance display, PoseStack poseStack, BedrockModel bedrockModel,
        long time, MultiBufferSource bufferSource) {
        MuzzleFlash muzzleFlash = display.getMuzzleFlash();
        if (muzzleFlash == null) {
            return;
        }
        // 每帧捕获当前枪口变换，使火光逐帧跟随枪口（后坐/摆动/瞄准均跟随）
        muzzleFlashNormal = new Matrix3f(
            poseStack.last()
                .normal());
        muzzleFlashPose = new Matrix4f(
            poseStack.last()
                .pose());
        bedrockModel.delegateRender(
            (poseStack1, vertexConsumer1, transformType1, light,
                overlay) -> doRender(light, overlay, muzzleFlash, time, bufferSource));
    }

    private static void doRender(int light, int overlay, MuzzleFlash muzzleFlash, long time,
        MultiBufferSource bufferSource) {
        if (muzzleFlashNormal != null && muzzleFlashPose != null) {
            float scale = 0.5f * muzzleFlash.getScale();
            float scaleTime = TIME_RANGE / 2.0f;
            scale = time < scaleTime ? (scale * (time / scaleTime)) : scale;
            MultiBufferSource multiBufferSource = bufferSource;

            // 推送到指定位置
            PoseStack poseStack2 = new PoseStack();
            poseStack2.last()
                .normal()
                .mul(muzzleFlashNormal);
            poseStack2.last()
                .pose()
                .mul(muzzleFlashPose);

            // 移植说明：上游用 com.mojang.math.Axis.ZP.rotationDegrees(deg)，JOML 等价写法为
            // new Quaternionf().rotationZ((float) Math.toRadians(deg))。
            Quaternionf rotate = new Quaternionf().rotationZ((float) Math.toRadians(muzzleFlashRandomRotate));

            // 先渲染一遍半透明背景
            poseStack2.pushPose();
            {
                poseStack2.scale(scale, scale, scale);
                poseStack2.mulPose(rotate);
                poseStack2.translate(0, -1, 0);
                RenderType renderTypeBg = RenderType.entityTranslucent(muzzleFlash.getTexture());
                MUZZLE_FLASH_MODEL.renderToBuffer(
                    poseStack2,
                    multiBufferSource.getBuffer(renderTypeBg),
                    light,
                    overlay,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F);
                // 1.7.10 的 shim MultiBufferSource 走 Tessellator / BufferBuilder 的立即模式，
                // 上游（1.20）这里靠外层的 RenderBuffers 在帧末统一 endBatch；这里必须就地冲刷，
                // 否则这批顶点会留在缓冲里直到下一个不相干的 endBatch，导致火光错帧。
                if (!OculusCompat.endBatch(multiBufferSource)) {
                    RenderHelperCompat.endBatch(multiBufferSource, renderTypeBg);
                }
            }
            poseStack2.popPose();

            // 然后渲染发光效果
            poseStack2.pushPose();
            {
                poseStack2.scale(scale / 2, scale / 2, scale / 2);
                poseStack2.mulPose(rotate);
                poseStack2.translate(0, -0.9, 0);
                // TODO(port): 上游 MuzzleFlashRender.java:115 用 RenderType.energySwirl(texture, 1, 1)：
                // 那是一个「绿色能量漩涡」RenderType，混合模式为 ADDITIVE_TRANSPARENCY + NO_CULL + 偏移纹理坐标。
                // 1.7.10 的 RenderType shim 不提供 energySwirl（需要 RenderStateShard/CompositeState 那一整套，
                // 平台层不搬），此处降级为 entityTranslucent，即从「加法混合发光」变成「普通 alpha 混合」。
                // 视觉差异：发光层亮度更低、不会叠加过曝；几何、位置、缩放、旋转完全一致。
                RenderType renderTypeLight = RenderType.entityTranslucent(muzzleFlash.getTexture());
                MUZZLE_FLASH_MODEL.renderToBuffer(
                    poseStack2,
                    multiBufferSource.getBuffer(renderTypeLight),
                    light,
                    overlay,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F);
                if (!OculusCompat.endBatch(multiBufferSource)) {
                    RenderHelperCompat.endBatch(multiBufferSource, renderTypeLight);
                }
            }
            poseStack2.popPose();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light,
        int overlay) {}

    @Override
    @SuppressWarnings("unchecked")
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light,
        int overlay, MultiBufferSource bufferSource) {
        if (OculusCompat.isRenderShadow()) {
            return;
        }
        long time;
        if (transformType.firstPerson()) {
            // 第一人称：仅本机玩家开火；renew 枪械由 FP 粒子系统表现
            if (!isSelf) {
                return;
            }
            if (!GunTime.isWithin(shootTimeStamp, TIME_RANGE + 1)) {
                return;
            }
            time = GunTime.nowMillis() - shootTimeStamp;
        } else {
            // 第三人称：任意实体（本机 F5 + 其他玩家），按实体计时，附着枪口渲染（跟随枪口）
            EntityLivingBase entity = ThirdPersonMuzzleParticleManager.currentEntity;
            if (entity == null) {
                return;
            }
            Long ts = TP_SHOOT_TIMESTAMPS.get(entity);
            if (ts == null) {
                return;
            }
            time = GunTime.nowMillis() - ts;
            if (time < 0 || time > TIME_RANGE) {
                TP_SHOOT_TIMESTAMPS.remove(entity);
                return;
            }
        }
        ItemStack currentGunItem = bedrockGunModel.getCurrentGunItem();

        TimelessAPI.getGunDisplay(currentGunItem)
            .ifPresent(display -> {
                MuzzleFlash muzzleFlash = display.getMuzzleFlash();
                if (muzzleFlash == null) {
                    return;
                }
                // renew 枪械的枪口火光由 snowstorm 粒子表现（第一人称 FP 粒子 / 第三人称锚点跟随世界粒子），
                // 不再渲染旧四边形火光；default 枪械保留四边形火光（第一人称本机 + 第三人称按实体）
                if (muzzleFlash.isRenew()) {
                    return;
                }
                ItemStack muzzleAttachment = bedrockGunModel.getCurrentAttachmentItem()
                    .get(AttachmentType.MUZZLE);
                IAttachment iAttachment = IAttachment.getIAttachmentOrNull(muzzleAttachment);
                if (iAttachment != null) {
                    ResourceLocation attachmentId = iAttachment.getAttachmentId(muzzleAttachment);
                    TimelessAPI.getCommonAttachmentIndex(attachmentId)
                        .ifPresent(index -> {
                            var modifier = index.getData()
                                .getModifier();
                            if (modifier.containsKey(SilenceModifier.ID) && modifier.get(SilenceModifier.ID)
                                .getValue() instanceof Pair<?, ?>pair) {
                                // 如果安装了消音器，则不渲染枪口火光
                                if (((Pair<Integer, Boolean>) pair).getRight()) {
                                    return;
                                }
                            }
                            renderMuzzleFlash(display, poseStack, bedrockGunModel, time, bufferSource);
                        });
                } else {
                    renderMuzzleFlash(display, poseStack, bedrockGunModel, time, bufferSource);
                }
            });
    }
}
