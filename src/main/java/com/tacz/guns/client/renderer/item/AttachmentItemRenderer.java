package com.tacz.guns.client.renderer.item;

import javax.annotation.Nonnull;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.client.resource.ItemModelDisplayManager;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.pojo.model.ItemModelDisplay;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;
import com.tacz.guns.util.RenderDistance;

public class AttachmentItemRenderer implements IItemRenderer {

    public static final SlotModel SLOT_ATTACHMENT_MODEL = new SlotModel();

    /** 上游物品模型：{@code assets/tacz/models/item/attachment.json}（display 段最全的一个）。 */
    private static final ResourceLocation ITEM_MODEL = new ResourceLocation(GunMod.MOD_ID, "item/attachment");

    public AttachmentItemRenderer() {}

    // ================================================================================================
    // 1.7.10 IItemRenderer 入口（上游是 BlockEntityWithoutLevelRenderer#renderByItem）
    // ================================================================================================

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return item != null && item.getItem() instanceof IAttachment;
    }

    /**
     * 渲染 helper：与 {@code AmmoItemRenderer#shouldUseRenderHelper} 同一套映射（推导见 {@link ItemModelDisplayManager}）：
     * EQUIPPED/EQUIPPED_FIRST_PERSON 走 Forge 的 {@code EQUIPPED_BLOCK} 分支（只补 {@code T(-0.5,-0.5,-0.5)}），
     * ENTITY 非展示框时施加 1.20 掉落物的自转/浮沉。
     */
    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return switch (type) {
            case EQUIPPED, EQUIPPED_FIRST_PERSON -> helper == ItemRendererHelper.EQUIPPED_BLOCK;
            case ENTITY -> !RenderItem.renderInFrame
                && (helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING);
            default -> false;
        };
    }

    /** 1.7.10 渲染入口：先把 1.20 物品模型 display 变换按分支折算进 PoseStack，再转给上游同源的 renderByItem。 */
    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        PoseStack poseStack = new PoseStack();
        ItemModelDisplay display = ItemModelDisplayManager.get(ITEM_MODEL);
        ItemModelDisplayManager.applyDisplayTransform(type, display, poseStack);
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate();
        try {
            // light：世界内手持/掉落物取实体亮度；展示柜/挂板从 lightmap 还原（见 ItemModelDisplayManager#packedLightFor）
            renderByItem(
                item,
                ItemModelDisplayManager.displayContextOf(type),
                poseStack,
                bufferSource,
                ItemModelDisplayManager.packedLightFor(type, data),
                0);
        } finally {
            bufferSource.endBatch();
        }
    }

    public void renderByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType,
        @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        if (stack.getItem() instanceof IAttachment iAttachment) {
            ResourceLocation attachmentId = iAttachment.getAttachmentId(stack);
            poseStack.pushPose();
            TimelessAPI.getClientAttachmentIndex(attachmentId)
                .ifPresentOrElse(attachmentIndex -> {
                    // GUI 特殊渲染
                    if (transformType == ItemDisplayContext.GUI) {
                        poseStack.translate(0.5, 1.5, 0.5);
                        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                        VertexConsumer buffer = pBuffer
                            .getBuffer(RenderType.entityTranslucent(attachmentIndex.getSlotTexture()));
                        SLOT_ATTACHMENT_MODEL.renderToBuffer(
                            poseStack,
                            buffer,
                            pPackedLight,
                            pPackedOverlay,
                            1.0F,
                            1.0F,
                            1.0F,
                            ItemPreviewRenderContext.alpha());
                        return;
                    }
                    poseStack.translate(0.5, 2, 0.5);
                    // 反转模型
                    poseStack.scale(-1, -1, 1);
                    if (transformType == ItemDisplayContext.FIXED) {
                        poseStack.mulPose(Axis.YN.rotationDegrees(90f));
                    }
                    ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(stack)
                        .orElse(null);
                    this.renderDefaultAttachment(
                        transformType,
                        poseStack,
                        pBuffer,
                        pPackedLight,
                        pPackedOverlay,
                        attachmentIndex,
                        variant);
                }, () -> {
                    // 没有这个 attachmentId，渲染黑紫材质以提醒
                    poseStack.translate(0.5, 1.5, 0.5);
                    poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                    VertexConsumer buffer = pBuffer
                        .getBuffer(RenderType.entityTranslucent(InternalAssetLoader.ENTITY_EMPTY_TEXTURE));
                    SLOT_ATTACHMENT_MODEL.renderToBuffer(
                        poseStack,
                        buffer,
                        pPackedLight,
                        pPackedOverlay,
                        1.0F,
                        1.0F,
                        1.0F,
                        ItemPreviewRenderContext.alpha());
                });
            poseStack.popPose();
        }
    }

    private void renderDefaultAttachment(@NotNull ItemDisplayContext transformType, @NotNull PoseStack poseStack,
        @NotNull MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay, ClientAttachmentIndex attachmentIndex,
        ClientAttachmentVariantIndex variant) {
        BedrockAttachmentModel model = attachmentIndex.getAttachmentModel();
        ResourceLocation texture = attachmentIndex.getModelTexture();
        if (variant != null && variant.isReplaceMode()
            && variant.getAttachmentModel() != null
            && variant.getModelTexture() != null) {
            model = variant.getAttachmentModel();
            texture = variant.getModelTexture();
        }
        // 有模型？正常渲染
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
            RenderType renderType = ItemPreviewRenderContext.isPreview() ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutout(texture);
            model
                .render(null, null, poseStack, transformType, renderType, pPackedLight, pPackedOverlay, alpha, pBuffer);
        }
        // 否则，以 GUI 形式渲染
        else {
            poseStack.translate(0, 0.5, 0);
            // 展示框里显示正常
            if (transformType == ItemDisplayContext.FIXED) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
            VertexConsumer buffer = pBuffer.getBuffer(RenderType.entityTranslucent(attachmentIndex.getSlotTexture()));
            SLOT_ATTACHMENT_MODEL.renderToBuffer(
                poseStack,
                buffer,
                pPackedLight,
                pPackedOverlay,
                1.0F,
                1.0F,
                1.0F,
                ItemPreviewRenderContext.alpha());
        }
        if (variant != null && variant.isOverlayMode()) {
            renderVariantOverlay(transformType, poseStack, pPackedLight, pPackedOverlay, variant, pBuffer);
        }
    }

    private static void renderVariantOverlay(@NotNull ItemDisplayContext transformType, @NotNull PoseStack poseStack,
        int pPackedLight, int pPackedOverlay, ClientAttachmentVariantIndex variant, MultiBufferSource pBuffer) {
        BedrockAttachmentModel model = variant.getAttachmentModel();
        ResourceLocation texture = variant.getModelTexture();
        if (model == null || texture == null) {
            return;
        }
        float alpha = ItemPreviewRenderContext.alpha();
        RenderType renderType = ItemPreviewRenderContext.isPreview() ? RenderType.entityTranslucent(texture)
            : RenderType.entityCutout(texture);
        model.render(null, null, poseStack, transformType, renderType, pPackedLight, pPackedOverlay, alpha, pBuffer);
    }
}
