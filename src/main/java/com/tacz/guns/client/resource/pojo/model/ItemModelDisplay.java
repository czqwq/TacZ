package com.tacz.guns.client.resource.pojo.model;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.client.resource.ItemModelDisplayManager;
import com.tacz.guns.client.resource.pojo.display.block.BlockItemTransforms;
import com.tacz.guns.mcshim.ItemDisplayContext;

/**
 * 对应 1.20.1 的 {@code net.minecraft.client.renderer.block.model.ItemTransforms}：物品模型 JSON 的
 * {@code "display"} 段（{@code assets/tacz/models/item/*.json}）。
 *
 * <p>
 * 上游消费点：{@code ItemRenderer#render} 的 {@code bakedmodel.getTransforms().getTransform(ctx).apply(leftHand, poseStack)}
 * （tmp/minecraft-src-1.20.1/.../entity/ItemRenderer.java:128）。1.7.10 没有物品模型体系，本类承载该数据，
 * 变换本体复用 1.20 {@code ItemTransform} 的等价实现 {@link BlockItemTransforms.ItemTransform#apply}；
 * 消费端（读取 + 各渲染分支帧补偿）见 {@link ItemModelDisplayManager}。
 */
public class ItemModelDisplay {

    /** 无 {@code display} 段时的默认值（全部恒等），字段缺失也用该语义。 */
    public static final ItemModelDisplay NO_DISPLAY = new ItemModelDisplay();

    @SerializedName("thirdperson_lefthand")
    private BlockItemTransforms.ItemTransform thirdPersonLeftHand = BlockItemTransforms.ItemTransform.NO_TRANSFORM;
    @SerializedName("thirdperson_righthand")
    private BlockItemTransforms.ItemTransform thirdPersonRightHand = BlockItemTransforms.ItemTransform.NO_TRANSFORM;
    @SerializedName("firstperson_lefthand")
    private BlockItemTransforms.ItemTransform firstPersonLeftHand = BlockItemTransforms.ItemTransform.NO_TRANSFORM;
    @SerializedName("firstperson_righthand")
    private BlockItemTransforms.ItemTransform firstPersonRightHand = BlockItemTransforms.ItemTransform.NO_TRANSFORM;
    @SerializedName("head")
    private BlockItemTransforms.ItemTransform head = BlockItemTransforms.ItemTransform.NO_TRANSFORM;
    @SerializedName("gui")
    private BlockItemTransforms.ItemTransform gui = BlockItemTransforms.ItemTransform.NO_TRANSFORM;
    @SerializedName("ground")
    private BlockItemTransforms.ItemTransform ground = BlockItemTransforms.ItemTransform.NO_TRANSFORM;
    @SerializedName("fixed")
    private BlockItemTransforms.ItemTransform fixed = BlockItemTransforms.ItemTransform.NO_TRANSFORM;

    /** 对应 1.20 {@code ItemTransforms#getTransform(ItemDisplayContext)}。 */
    public BlockItemTransforms.ItemTransform getTransform(ItemDisplayContext context) {
        if (context == null) {
            return BlockItemTransforms.ItemTransform.NO_TRANSFORM;
        }
        return switch (context) {
            case THIRD_PERSON_LEFT_HAND -> thirdPersonLeftHand;
            case THIRD_PERSON_RIGHT_HAND -> thirdPersonRightHand;
            case FIRST_PERSON_LEFT_HAND -> firstPersonLeftHand;
            case FIRST_PERSON_RIGHT_HAND -> firstPersonRightHand;
            case HEAD -> head;
            case GUI -> gui;
            case GROUND -> ground;
            case FIXED -> fixed;
            default -> BlockItemTransforms.ItemTransform.NO_TRANSFORM;
        };
    }

    /**
     * 1.20 {@code ItemTransforms.Deserializer}（ItemTransforms.java:91-101）的左手回退：左手键缺失时
     * 直接继承右手变换（{@code attachment.json} 就只写了 {@code firstperson_righthand}）。
     */
    public ItemModelDisplay resolveLeftHandFallback() {
        if (thirdPersonLeftHand == BlockItemTransforms.ItemTransform.NO_TRANSFORM) {
            thirdPersonLeftHand = thirdPersonRightHand;
        }
        if (firstPersonLeftHand == BlockItemTransforms.ItemTransform.NO_TRANSFORM) {
            firstPersonLeftHand = firstPersonRightHand;
        }
        return this;
    }

}
