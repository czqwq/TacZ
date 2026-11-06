package com.tacz.guns.client.model.functional;

import java.util.EnumMap;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.client.model.RenderHelperCompat;
import com.tacz.guns.client.renderer.item.AttachmentItemRenderer;
import com.tacz.guns.client.renderer.item.ItemPreviewRenderContext;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.index.ClientSlotAdapterIndex;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;
import com.tacz.guns.util.RenderDistance;
import com.tacz.guns.util.SlotAdapterHelper;

public class AttachmentRender implements IFunctionalRenderer {

    private final BedrockGunModel bedrockGunModel;
    private final AttachmentType type;

    public AttachmentRender(BedrockGunModel bedrockGunModel, AttachmentType type) {
        this.bedrockGunModel = bedrockGunModel;
        this.type = type;
    }

    /**
     * 旧入口在光影手部 pass 中选择 hand buffer；内部调用优先显式传递 bufferSource。
     */
    public static void renderAttachment(ItemStack attachmentItem, ItemStack gunItem, AttachmentType type,
        PoseStack poseStack, ItemDisplayContext transformType, int light, int overlay) {
        renderAttachment(
            attachmentItem,
            gunItem,
            type,
            poseStack,
            transformType,
            light,
            overlay,
            RenderHelperCompat.getBufferSource(transformType));
    }

    public static void renderAttachment(ItemStack attachmentItem, ItemStack gunItem, AttachmentType type,
        PoseStack poseStack, ItemDisplayContext transformType, int light, int overlay, MultiBufferSource bufferSource) {
        if (isEmpty(attachmentItem)) {
            return;
        }
        poseStack.translate(0, -1.5, 0);
        if (attachmentItem.getItem() instanceof IAttachment iAttachment) {
            ResourceLocation attachmentId = iAttachment.getAttachmentId(attachmentItem);
            TimelessAPI.getClientAttachmentIndex(attachmentId)
                .ifPresentOrElse(attachmentIndex -> {
                    BedrockAttachmentModel model = attachmentIndex.getAttachmentModel();
                    ResourceLocation texture = attachmentIndex.getModelTexture();
                    ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(attachmentItem)
                        .orElse(null);
                    if (variant != null && variant.isReplaceMode()
                        && variant.getAttachmentModel() != null
                        && variant.getModelTexture() != null) {
                        model = variant.getAttachmentModel();
                        texture = variant.getModelTexture();
                    }
                    ClientSlotAdapterIndex slotAdapter = getSlotAdapter(gunItem, type, attachmentId);
                    if (slotAdapter != null) {
                        poseStack.pushPose();
                        renderSlotAdapter(slotAdapter, gunItem, poseStack, transformType, light, overlay, bufferSource);
                        poseStack.popPose();
                        applyMountOffset(poseStack, slotAdapter.getMountOffset());
                    }
                    if (variant != null && variant.isOverlayMode()) {
                        renderVariantOverlay(
                            variant,
                            attachmentItem,
                            gunItem,
                            poseStack,
                            transformType,
                            light,
                            overlay,
                            bufferSource);
                        applyMountOffset(poseStack, variant.getMountOffset());
                    }
                    // 这里是枪械里的配件渲染，没有模型材质就不渲染
                    if (model != null && texture != null) {
                        // 调用低模
                        Pair<BedrockAttachmentModel, ResourceLocation> lodModel = attachmentIndex.getLodModel();
                        // 有低模、在高模渲染范围外、不是第一人称
                        if (lodModel != null && !RenderDistance.inRenderHighPolyModelDistance(poseStack)
                            && !transformType.firstPerson()) {
                            model = lodModel.getLeft();
                            texture = lodModel.getRight();
                        }
                        float alpha = ItemPreviewRenderContext.alpha();
                        RenderType renderType = ItemPreviewRenderContext.isPreview()
                            ? RenderType.entityTranslucent(texture)
                            : RenderType.entityCutout(texture);
                        model.render(
                            attachmentItem,
                            gunItem,
                            poseStack,
                            transformType,
                            renderType,
                            light,
                            overlay,
                            alpha,
                            bufferSource);
                    }
                }, () -> {
                    // 没有对应的 attachmentIndex，渲染黑紫材质以提醒
                    // TODO(port): 上游 AttachmentRender.java:88 用 MissingTextureAtlasSprite.getLocation()（1.20 的
                    // missingno 贴图位置常量）。1.7.10 没有这个常量（缺贴图是 TextureMap 内部私有的 "missingno" sprite），
                    // 此处降级为方块图集贴图：该分支只在枪包资源缺失时出现，目的是「画一块显眼的东西」而不是精确的黑紫棋盘。
                    RenderType renderType = RenderType.entityTranslucent(TextureMap.locationBlocksTexture);
                    VertexConsumer buffer = bufferSource.getBuffer(renderType);
                    AttachmentItemRenderer.SLOT_ATTACHMENT_MODEL.renderToBuffer(
                        poseStack,
                        buffer,
                        light,
                        overlay,
                        1.0F,
                        1.0F,
                        1.0F,
                        ItemPreviewRenderContext.alpha());
                    RenderHelperCompat.endBatch(bufferSource, renderType);
                });
        }
    }

    private static ClientSlotAdapterIndex getSlotAdapter(ItemStack gunItem, AttachmentType type,
        ResourceLocation attachmentId) {
        ResourceLocation adapterId = SlotAdapterHelper.getEffectiveSlotAdapter(gunItem, type, attachmentId);
        return adapterId == null ? null
            : TimelessAPI.getClientSlotAdapterIndex(adapterId)
                .orElse(null);
    }

    private static void renderSlotAdapter(ClientSlotAdapterIndex adapter, ItemStack gunItem, PoseStack poseStack,
        ItemDisplayContext transformType, int light, int overlay, MultiBufferSource bufferSource) {
        BedrockAttachmentModel model = adapter.getAdapterModel();
        ResourceLocation texture = adapter.getModelTexture();
        if (model == null || texture == null) {
            return;
        }
        float alpha = ItemPreviewRenderContext.alpha();
        RenderType renderType = ItemPreviewRenderContext.isPreview() ? RenderType.entityTranslucent(texture)
            : RenderType.entityCutout(texture);
        // 移植说明：上游传 null（1.20 的空栈单例）。1.7.10 没有 null，
        // 目标参数 @Nullable ItemStack attachmentItem 传 null 即「无配件」语义，下游 render 逻辑等价。
        model.render(null, gunItem, poseStack, transformType, renderType, light, overlay, alpha, bufferSource);
    }

    private static void applyMountOffset(PoseStack poseStack, Vector3f offset) {
        if (offset != null) {
            poseStack.translate(offset.x() / 16.0F, -offset.y() / 16.0F, offset.z() / 16.0F);
        }
    }

    /**
     * 移植辅助：1.20 {@code ItemStack#isEmpty()} 在 1.7.10 没有，用 {@code stackSize} 判断，并容忍 null。
     */
    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.stackSize <= 0;
    }

    private static void renderVariantOverlay(ClientAttachmentVariantIndex variant, ItemStack attachmentItem,
        ItemStack gunItem, PoseStack poseStack, ItemDisplayContext transformType, int light, int overlay,
        MultiBufferSource bufferSource) {
        BedrockAttachmentModel model = variant.getAttachmentModel();
        ResourceLocation texture = variant.getModelTexture();
        if (model == null || texture == null) {
            return;
        }
        float alpha = ItemPreviewRenderContext.alpha();
        RenderType renderType = ItemPreviewRenderContext.isPreview() ? RenderType.entityTranslucent(texture)
            : RenderType.entityCutout(texture);
        model
            .render(attachmentItem, gunItem, poseStack, transformType, renderType, light, overlay, alpha, bufferSource);
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light,
        int overlay) {
        render(
            poseStack,
            vertexBuffer,
            transformType,
            light,
            overlay,
            RenderHelperCompat.getBufferSource(transformType));
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light,
        int overlay, MultiBufferSource bufferSource) {
        EnumMap<AttachmentType, ItemStack> currentAttachmentItem = bedrockGunModel.getCurrentAttachmentItem();
        ItemStack attachmentItem = currentAttachmentItem.get(type);
        if (!isEmpty(attachmentItem)) {
            Matrix3f normal = new Matrix3f(
                poseStack.last()
                    .normal());
            Matrix4f pose = new Matrix4f(
                poseStack.last()
                    .pose());
            // 和枪械模型共用顶点缓冲的都需要代理到渲染结束后渲染
            bedrockGunModel.delegateRender((poseStack1, vertexBuffer1, transformType1, light1, overlay1) -> {
                PoseStack poseStack2 = new PoseStack();
                poseStack2.last()
                    .normal()
                    .mul(normal);
                poseStack2.last()
                    .pose()
                    .mul(pose);
                // 渲染配件
                renderAttachment(
                    attachmentItem,
                    bedrockGunModel.getCurrentGunItem(),
                    type,
                    poseStack2,
                    transformType,
                    light,
                    overlay,
                    bufferSource);
            });
        }
    }
}
