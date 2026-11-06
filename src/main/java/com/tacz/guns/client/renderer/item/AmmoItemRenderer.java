package com.tacz.guns.client.renderer.item;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import org.joml.Vector3f;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.client.model.BedrockAmmoModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.client.resource.ItemModelDisplayManager;
import com.tacz.guns.client.resource.pojo.TransformScale;
import com.tacz.guns.client.resource.pojo.model.ItemModelDisplay;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;

public class AmmoItemRenderer implements IItemRenderer {

    private static final SlotModel SLOT_AMMO_MODEL = new SlotModel();

    /** 上游物品模型：{@code assets/tacz/models/item/ammo.json}（{@code builtin/entity}，display 见该文件）。 */
    private static final ResourceLocation ITEM_MODEL = new ResourceLocation(GunMod.MOD_ID, "item/ammo");

    public AmmoItemRenderer() {}

    // ================================================================================================
    // 1.7.10 IItemRenderer 入口（1.20 的 BlockEntityWithoutLevelRenderer#renderByItem 无对应）
    // ================================================================================================

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return item != null && item.getItem() instanceof IAmmo;
    }

    /**
     * 渲染 helper：把 1.7.10 的两层「2D 纸片物品」帧换成 1.20 的物品帧（推导见
     * {@link ItemModelDisplayManager} 的类注释与 {@code AnimateGeoItemRenderer#shouldUseRenderHelper}）。
     *
     * <ul>
     * <li>{@code EQUIPPED}/{@code EQUIPPED_FIRST_PERSON}：拿 {@code EQUIPPED_BLOCK} 分支，使 Forge 只补
     * {@code T(-0.5,-0.5,-0.5)}（ForgeHooksClient.java:216-221），而不是再叠一层 2D 纸片帧（:225-233）；</li>
     * <li>{@code ENTITY}：1.20 掉落物始终自转+浮沉（ItemEntityRenderer#render:63-67）⇒ 非展示框时两个 helper 都返回 true，
     * 展示框（1.20 FIXED）两者都不施加。</li>
     * </ul>
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

    /**
     * 1.7.10 的渲染入口：先按分支把 1.20 的物品模型 display 变换折算进 PoseStack（1.7.10 的槽位/手持帧在
     * GL_MODELVIEW 里，故 PoseStack 只承载 display 与帧补偿），再转发给上游同源的 {@link #renderByItem}。
     */
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

    private static void applyPositioningNodeTransform(List<BedrockPart> nodePath, PoseStack poseStack, Vector3f scale) {
        if (nodePath == null) {
            return;
        }
        if (scale == null) {
            scale = new Vector3f(1, 1, 1);
        }
        // 应用定位组的反向位移、旋转，使定位组的位置就是渲染中心
        poseStack.translate(0, 1.5, 0);
        for (int i = nodePath.size() - 1; i >= 0; i--) {
            BedrockPart t = nodePath.get(i);
            poseStack.mulPose(Axis.XN.rotation(t.xRot));
            poseStack.mulPose(Axis.YN.rotation(t.yRot));
            poseStack.mulPose(Axis.ZN.rotation(t.zRot));
            if (t.getParent() != null) {
                poseStack.translate(-t.x * scale.x() / 16.0F, -t.y * scale.y() / 16.0F, -t.z * scale.z() / 16.0F);
            } else {
                poseStack
                    .translate(-t.x * scale.x() / 16.0F, (1.5F - t.y / 16.0F) * scale.y(), -t.z * scale.z() / 16.0F);
            }
        }
        poseStack.translate(0, -1.5, 0);
    }

    /**
     * 上游 {@code BlockEntityWithoutLevelRenderer#renderByItem(stack, transformType, poseStack, buffer, light, overlay)}。
     * 1.7.10 没有该基类，方法体原样保留，由 {@link #renderItem} 转发进来。
     */
    public void renderByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType,
        @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        if (!(stack.getItem() instanceof IAmmo iAmmo)) {
            return;
        }
        ResourceLocation ammoId = iAmmo.getAmmoId(stack);
        poseStack.pushPose();
        TimelessAPI.getClientAmmoIndex(ammoId)
            .ifPresentOrElse(ammoIndex -> {
                // 先获取 3D 模型，如果为空，统一使用 GUI 渲染
                BedrockAmmoModel ammoModel = ammoIndex.getAmmoModel();
                ResourceLocation modelTexture = ammoIndex.getModelTextureLocation();
                // GUI 特殊渲染
                if (transformType == ItemDisplayContext.GUI || ammoModel == null || modelTexture == null) {
                    poseStack.translate(0.5, 1.5, 0.5);
                    poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                    VertexConsumer buffer = pBuffer
                        .getBuffer(RenderType.entityTranslucent(ammoIndex.getSlotTextureLocation()));
                    SLOT_AMMO_MODEL.renderToBuffer(
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
                // 剩下的渲染
                // 移动到模型原点
                poseStack.translate(0.5, 2, 0.5);
                // 反转模型
                poseStack.scale(-1, -1, 1);
                // 应用定位组的变换（位移和旋转，不包括缩放）
                applyPositioningTransform(
                    transformType,
                    ammoIndex.getTransform()
                        .getScale(),
                    ammoModel,
                    poseStack);
                // 应用 display 数据中的缩放
                applyScaleTransform(
                    transformType,
                    ammoIndex.getTransform()
                        .getScale(),
                    poseStack);
                // 渲染子弹盒模型
                float alpha = ItemPreviewRenderContext.alpha();
                RenderType renderType = ItemPreviewRenderContext.isPreview()
                    ? RenderType.entityTranslucent(modelTexture)
                    : RenderType.entityCutout(modelTexture);
                ammoModel.render(
                    poseStack,
                    transformType,
                    renderType,
                    pPackedLight,
                    pPackedOverlay,
                    1.0F,
                    1.0F,
                    1.0F,
                    alpha,
                    pBuffer);
            }, () -> {
                // 没有这个 ammoID，渲染个错误材质提醒别人
                poseStack.translate(0.5, 1.5, 0.5);
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                VertexConsumer buffer = pBuffer
                    .getBuffer(RenderType.entityTranslucent(InternalAssetLoader.ENTITY_EMPTY_TEXTURE));
                SLOT_AMMO_MODEL.renderToBuffer(
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

    private void applyPositioningTransform(ItemDisplayContext transformType, TransformScale scale,
        BedrockAmmoModel model, PoseStack poseStack) {
        switch (transformType) {
            case FIXED -> applyPositioningNodeTransform(model.getFixedOriginPath(), poseStack, scale.getFixed());
            case GROUND -> applyPositioningNodeTransform(model.getGroundOriginPath(), poseStack, scale.getGround());
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> applyPositioningNodeTransform(
                model.getThirdPersonHandOriginPath(),
                poseStack,
                scale.getThirdPerson());
        }
    }

    private void applyScaleTransform(ItemDisplayContext transformType, TransformScale scale, PoseStack poseStack) {
        if (scale == null) {
            return;
        }
        Vector3f vector3f = null;
        switch (transformType) {
            case FIXED -> vector3f = scale.getFixed();
            case GROUND -> vector3f = scale.getGround();
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> vector3f = scale.getThirdPerson();
        }
        if (vector3f != null) {
            poseStack.translate(0, 1.5, 0);
            poseStack.scale(vector3f.x(), vector3f.y(), vector3f.z());
            poseStack.translate(0, -1.5, 0);
        }
    }
}
