package com.tacz.guns.client.renderer.block;

import java.util.EnumMap;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.joml.Vector3f;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.DisplayGunModelInstance;
import com.tacz.guns.client.model.ExternalGunModel;
import com.tacz.guns.client.renderer.external.ExternalGunAppearance;
import com.tacz.guns.client.renderer.item.SbmGunTransforms;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.index.ClientSlotAdapterIndex;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.OverlayTexture;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;
import com.tacz.guns.util.SlotAdapterHelper;

/**
 * 展示类方块（展示柜 / 挂板）里「枪械 + 配件」几何的采集与绘制。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/renderer/block/DisplayGunGeometry.java}
 *
 * <h3>1.7.10 差异</h3>
 * <ul>
 * <li>{@code GeometryCollector} 是 simplebedrockmodel v2 的 GPU 几何收集器
 * （{@code v2.client.mesh.capture.GeometryCollector}），1.7.10 <b>没有</b>这类「先采集顶点、
 * 之后合并上传 VBO」的库（本工程按 {@code docs/PORT_STATUS.md} 的裁决把
 * {@code v2.client.mesh.**} 降级为立即绘制），因此本类把出口参数换成
 * {@link MultiBufferSource}：上游 {@code collector.buffer(ordinary, emissive, QUADS)}
 * 只提供服务「往哪个顶点流写」这一件事，对应 1.7.10 的
 * {@code mcshim.MultiBufferSource#getBuffer(RenderType)}。</li>
 * <li>上游 {@code renderBoneTree(..., light = 0, ...)} 把「场景光照」推迟到 VBO 绘制时按实例
 * lightmap 施加（0 在 SBM 的捕获语义里表示「跟随实例」）。1.7.10 的
 * {@code Tessellator#setBrightness(0)} 就是「最暗」，没有「跟随实例」这个语义，
 * 因此 {@link #renderModel} 多一个 {@code packedLight} 参数，直接传真实光照。
 * {@code bone.illuminated} 的骨骼由 SBM 内部改成 {@code MAX_LIGHT_TEXTURE}，
 * 自发光效果在固定管线上由逐顶点亮度天然达成 —— 这也是 1.7.10 不需要
 * {@link DisplayGunRenderType#emissive(ResourceLocation)} 那个独立材质通道的原因。</li>
 * <li>{@code ItemStack.EMPTY} → {@code null}（见 {@code docs/PORT_GAME.md} 第 1 节）。</li>
 * <li>{@code PoseStack} / {@code RenderType} / {@code ItemDisplayContext} / {@code VertexFormat}
 * 换成 {@code mcshim} 同名类。</li>
 * </ul>
 *
 * <p>
 * 依赖 {@code client/renderer/external/ExternalGunAppearance}、
 * {@code client/renderer/item/SbmGunTransforms}、{@code client/model/ExternalGunModel}
 * 三处仍为 1.20 形态（尚未移植），以及 simplebedrockmodel v2 的
 * {@code BakedBedrockModel#renderBoneTree}（未 vendor）。
 */
public class DisplayGunGeometry {

    private DisplayGunGeometry() {}

    @FunctionalInterface
    public interface AttachmentModelVisitor {

        void accept(ExternalGunModel model, ResourceLocation texture, ItemStack stack, PoseStack pose);
    }

    public static EnumMap<AttachmentType, ClientAttachmentIndex> resolveAttachments(IGun gun, ItemStack stack) {
        EnumMap<AttachmentType, ClientAttachmentIndex> indexes = new EnumMap<>(AttachmentType.class);
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack attachment = effectiveAttachment(gun, stack, type);
            if (attachment == null) continue;
            IAttachment item = IAttachment.getIAttachmentOrNull(attachment);
            if (item == null) continue;
            ResourceLocation id = item.getAttachmentId(attachment);
            ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id)
                .orElse(null);
            if (index == null) return null;
            indexes.put(type, index);
        }
        return indexes;
    }

    public static ItemStack effectiveAttachment(IGun gun, ItemStack stack, AttachmentType type) {
        ItemStack attached = gun.getAttachment(stack, type);
        return attached == null ? gun.getBuiltinAttachment(stack, type) : attached;
    }

    /**
     * 对应上游 {@code capture(Snapshot, ItemDisplayContext, GeometryCollector)}。
     *
     * <p>
     * {@code packedLight} 是 1.7.10 增补的参数，理由见类注释第 2 条。
     */
    public static boolean capture(ExternalGunAppearance.Snapshot snapshot, ItemDisplayContext context,
        MultiBufferSource buffers, int packedLight) {
        return capture(snapshot, context, false, buffers, packedLight);
    }

    /** compensateItemRenderer is true for an immediate BEWLR capture after ItemRenderer's -0.5 translation. */
    public static boolean capture(ExternalGunAppearance.Snapshot snapshot, ItemDisplayContext context,
        boolean compensateItemRenderer, MultiBufferSource buffers, int packedLight) {
        ItemStack stack = snapshot.stack();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return false;
        EnumMap<AttachmentType, ClientAttachmentIndex> attachments = resolveAttachments(gun, stack);
        if (attachments == null) return false;
        ExternalGunModel external = snapshot.model();
        DisplayGunModelInstance instance = external.displayInstance();
        instance.configureGun(gun, stack, attachments);

        PoseStack pose = new PoseStack();
        if (!applyTransform(snapshot, context, compensateItemRenderer, pose)) return false;
        renderModel(external, instance, pose, snapshot.texture(), buffers, packedLight);
        renderAttachments(instance, pose, snapshot.useLod(), gun, stack, attachments, buffers, packedLight);
        return true;
    }

    /** 静态几何与动态特效必须使用同一份外观、显示原点和缩放。 */
    public static boolean applyTransform(ExternalGunAppearance.Snapshot snapshot, ItemDisplayContext context,
        boolean compensateItemRenderer, PoseStack pose) {
        ExternalGunModel external = snapshot.model();
        var scales = snapshot.display()
            .getTransform()
            .getScale();
        switch (context) {
            case FIXED -> {
                // 展示槽位绕过 ItemRenderer；即时 FIXED 的 BEWLR 则收到其 -0.5 居中变换。
                if (compensateItemRenderer) pose.translate(0.5, 0.5, 0.5);
                SbmGunTransforms.applyFixed(pose, external, scales == null ? null : scales.getFixed());
            }
            case GROUND -> {
                pose.translate(0.5, 0.5, 0.5);
                SbmGunTransforms.applyGround(pose, external, scales == null ? null : scales.getGround());
            }
            case THIRD_PERSON_RIGHT_HAND -> {
                pose.translate(0.5, 0.5, 0.5);
                SbmGunTransforms.applyThirdPerson(pose, external, scales == null ? null : scales.getThirdPerson());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void renderAttachments(DisplayGunModelInstance instance, PoseStack pose, boolean useLod, IGun gun,
        ItemStack gunStack, EnumMap<AttachmentType, ClientAttachmentIndex> attachments, MultiBufferSource buffers,
        int packedLight) {
        forEachAttachmentModel(
            instance,
            pose,
            useLod,
            gun,
            gunStack,
            attachments,
            (model, texture, stack,
                modelPose) -> renderModel(model, model.displayInstance(), modelPose, texture, buffers, packedLight));
    }

    /** 共用本体与特效的挂点判定、转接口偏移以及变体/LOD 选择。 */
    public static void forEachAttachmentModel(DisplayGunModelInstance instance, PoseStack pose, boolean useLod,
        IGun gun, ItemStack gunStack, EnumMap<AttachmentType, ClientAttachmentIndex> attachments,
        AttachmentModelVisitor visitor) {
        for (var entry : attachments.entrySet()) {
            AttachmentType type = entry.getKey();
            ClientAttachmentIndex index = entry.getValue();
            ItemStack attached = effectiveAttachment(gun, gunStack, type);
            IAttachment attachmentItem = IAttachment.getIAttachmentOrNull(attached);
            if (attachmentItem == null) continue;
            ResourceLocation attachmentId = attachmentItem.getAttachmentId(attached);
            int position = instance.attachmentPosition(type);
            // LOD 模型可能省略挂点；旧渲染路径此时也不会绘制该配件。
            if (position < 0) continue;
            PoseStack attachmentPose = new PoseStack();
            attachmentPose.last()
                .pose()
                .mul(
                    pose.last()
                        .pose());
            attachmentPose.last()
                .normal()
                .mul(
                    pose.last()
                        .normal());
            SbmGunTransforms.applyBonePivot(attachmentPose, instance, position);

            ResourceLocation adapterId = SlotAdapterHelper.getEffectiveSlotAdapter(gunStack, type, attachmentId);
            ClientSlotAdapterIndex adapter = adapterId == null ? null
                : TimelessAPI.getClientSlotAdapterIndex(adapterId)
                    .orElse(null);
            if (adapter != null) {
                visitOptionalModel(
                    adapter.getExternalModel(),
                    adapter.getModelTexture(),
                    null,
                    attachmentPose,
                    visitor);
                applyMountOffset(attachmentPose, adapter.getMountOffset());
            }

            ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(attached)
                .orElse(null);
            if (variant != null && variant.isOverlayMode()) {
                visitOptionalModel(
                    variant.getExternalModel(),
                    variant.getModelTexture(),
                    attached,
                    attachmentPose,
                    visitor);
                applyMountOffset(attachmentPose, variant.getMountOffset());
            }

            ExternalGunModel attachment = index.getExternalModel();
            ResourceLocation attachmentTexture = index.getModelTexture();
            if (variant != null && variant.isReplaceMode()
                && variant.getAttachmentModel() != null
                && variant.getModelTexture() != null) {
                attachment = variant.getExternalModel();
                attachmentTexture = variant.getModelTexture();
            }
            // 与 AttachmentRender 一致：普通配件的远距模型在 replace 变体之后选择。
            var lod = index.getExternalLodModel();
            if (useLod && lod != null) {
                attachment = lod.getLeft();
                attachmentTexture = lod.getRight();
            }
            visitOptionalModel(attachment, attachmentTexture, attached, attachmentPose, visitor);
        }
    }

    private static void visitOptionalModel(ExternalGunModel model, ResourceLocation texture, ItemStack stack,
        PoseStack pose, AttachmentModelVisitor visitor) {
        if (model == null || texture == null) return;
        visitor.accept(model, texture, stack, pose);
    }

    private static void applyMountOffset(PoseStack pose, Vector3f offset) {
        if (offset != null) {
            // AttachmentRender 的偏移处在旧模型坐标；SBM 副本的 X/Y 轴与之反向。
            pose.translate(-offset.x() / 16.0F, offset.y() / 16.0F, offset.z() / 16.0F);
        }
    }

    /**
     * 对应上游 {@code renderModel(ExternalGunModel, DisplayGunModelInstance, PoseStack, ResourceLocation,
     * GeometryCollector)}。
     *
     * <p>
     * // TODO(L2): 上游 DisplayGunGeometry.java:210-216 做的是
     * // {@code collector.buffer(RenderType.entityCutout(texture), DisplayGunRenderType.emissive(texture),
     * // VertexFormat.Mode.QUADS)} 拿到一个按顶点 UV2 分流到「普通 / 自发光」两种材质的 sink，
     * // 再用 {@code light = 0}（SBM 捕获语义里的「跟随实例」）调 {@code renderBoneTree}；
     * // 1.7.10 无 GPU 几何采集、{@code renderBoneTree} 也只接受单个 sink（无法按顶点 UV2 分流），
     * // 此处改用 {@code MultiBufferSource#getBuffer(RenderType.entityCutout(texture))} 单通道，
     * // 并传入真实 {@code packedLight}（1.7.10 没有「跟随实例」的 0 亮度语义）；
     * // {@code bone.illuminated} 的骨骼由 SBM 内部改成 MAX_LIGHT_TEXTURE，
     * // 自发光在固定管线上由逐顶点亮度直接达成，不再需要独立的 emissive 材质通道
     * // （{@link DisplayGunRenderType#emissive(ResourceLocation)} 保留给日后需要单独一遍的调用方）。
     */
    public static void renderModel(ExternalGunModel external, DisplayGunModelInstance instance, PoseStack pose,
        ResourceLocation texture, MultiBufferSource buffers, int packedLight) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(texture));
        external.model()
            .renderBoneTree(instance, pose, buffer, packedLight, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1, true, true);
    }
}
