package com.tacz.guns.client.renderer.item;

import javax.annotation.Nonnull;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.block.GunSmithTableRenderer;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.client.resource.ItemModelDisplayManager;
import com.tacz.guns.client.resource.pojo.display.block.BlockItemTransforms;
import com.tacz.guns.client.resource.pojo.model.ItemModelDisplay;
import com.tacz.guns.mcshim.Axis;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.RenderType;
import com.tacz.guns.mcshim.VertexConsumer;

/**
 * 工作台方块物品（{@code gun_smith_table} / {@code workbench_a/b/c}）的 {@code IItemRenderer}。
 *
 * <p>
 * 上游是 {@code BlockEntityWithoutLevelRenderer}（{@code GunSmithTableItem#initializeClient}），由 1.20
 * {@code ItemRenderer#render} 在外层施加**物品模型 display**（{@code models/item/gun_smith_table.json} 等只有
 * {@code parent: builtin/entity}，display 恒等）+ 无条件 {@code T(-0.5,-0.5,-0.5)}；BEWLR 自己再施加 TACZ
 * 展示数据里的 {@code transforms}（{@code display/blocks/*.json}，见 {@link #renderByItem}）。
 * 1.7.10 的两层帧都与 1.20 不同 ⇒ {@link #renderItem} 用 {@link ItemModelDisplayManager} 做外层帧补偿，
 * {@code renderByItem} 内的 {@code transforms} 层保持上游原样。
 */
public class GunSmithTableItemRenderer implements IItemRenderer {

    private static final SlotModel SLOT_BLOCK_MODEL = new SlotModel();

    /** 取不到物品注册名时的兜底物品模型（4 个物品模型都只有 {@code parent: builtin/entity}，display 恒等）。 */
    private static final ResourceLocation FALLBACK_ITEM_MODEL = new ResourceLocation(
        GunMod.MOD_ID,
        "item/gun_smith_table");

    public GunSmithTableItemRenderer() {}

    // ================================================================================================
    // 1.7.10 IItemRenderer 入口（上游是 BlockEntityWithoutLevelRenderer#renderByItem）
    // ================================================================================================

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return item != null;
    }

    /**
     * 渲染 helper：与 {@code AmmoItemRenderer#shouldUseRenderHelper} 同一套映射（推导见 {@link ItemModelDisplayManager}）：
     * EQUIPPED/EQUIPPED_FIRST_PERSON 走 Forge 的 {@code EQUIPPED_BLOCK} 分支（只补 {@code T(-0.5,-0.5,-0.5)}，
     * 避免再叠 2D 纸片帧）；ENTITY 非展示框时施加 1.20 掉落物的自转/浮沉。
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

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        PoseStack poseStack = new PoseStack();
        // 1.20 物品模型 display 的外层帧补偿（GUI 里同时补 applyGuiSlotScale，r6 的 BlockItemTransforms 用法不变）
        ItemModelDisplay display = ItemModelDisplayManager.get(itemModelOf(item));
        ItemModelDisplayManager.applyDisplayTransform(type, display, poseStack);
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate();
        try {
            // light：GUI 满亮度，世界内手持/掉落物取实体亮度，展示柜/挂板/雕像由 StaticItemRenderHelper 经
            // ItemModelDisplayManager#pushPackedLight 传入（见 ItemModelDisplayManager#packedLightFor）
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

    /**
     * 工作台物品的 item model 位置：1.20 里物品模型按注册名取 {@code models/item/<path>.json}
     * （{@code gun_smith_table} / {@code workbench_a/b/c}），1.7.10 用 {@link Item#itemRegistry} 反查。
     */
    private static ResourceLocation itemModelOf(ItemStack stack) {
        Item item = stack == null ? null : stack.getItem();
        if (item == null) {
            return FALLBACK_ITEM_MODEL;
        }
        String itemId = Item.itemRegistry.getNameForObject(item);
        if (itemId == null || itemId.isEmpty()) {
            return FALLBACK_ITEM_MODEL;
        }
        try {
            ResourceLocation location = new ResourceLocation(itemId);
            return new ResourceLocation(location.getResourceDomain(), "item/" + location.getResourcePath());
        } catch (RuntimeException e) {
            return FALLBACK_ITEM_MODEL;
        }
    }

    /**
     * 上游 {@code BlockEntityWithoutLevelRenderer#renderByItem}：TACZ {@code display/blocks} 的 {@code transforms}
     * 按 1.20 上下文取条目并夹在半格之间（{@code T(0.5)·transform·T(-0.5)}），之后是模型本体。
     */
    public void renderByItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext transformType,
        @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource pBuffer, int pPackedLight, int pPackedOverlay) {
        GunSmithTableRenderer.getIndex(stack)
            .ifPresentOrElse(index -> {
                BedrockModel model = index.getModel();
                ResourceLocation texture = index.getTexture();
                if (model == null) {
                    return;
                }
                poseStack.pushPose();

                BlockItemTransforms transforms = index.getTransforms();
                if (transforms != null) {
                    poseStack.translate(0.5F, 0.5F, 0.5F);
                    BlockItemTransforms.ItemTransform transform = transforms.getTransform(transformType);
                    transform.apply(false, poseStack);
                    poseStack.translate(-0.5F, -0.5F, -0.5F);
                }

                poseStack.translate(0.5, 1.5, 0.5);
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                RenderType renderType = RenderType.entityTranslucent(texture);
                model.render(poseStack, transformType, renderType, pPackedLight, pPackedOverlay, pBuffer);
                poseStack.popPose();
            }, () -> {
                poseStack.translate(0.5, 1.5, 0.5);
                poseStack.mulPose(Axis.ZN.rotationDegrees(180));
                VertexConsumer buffer = pBuffer
                    .getBuffer(RenderType.entityTranslucent(InternalAssetLoader.ENTITY_EMPTY_TEXTURE));
                SLOT_BLOCK_MODEL.renderToBuffer(poseStack, buffer, pPackedLight, pPackedOverlay, 1, 1, 1, 1);
            });
    }
}
