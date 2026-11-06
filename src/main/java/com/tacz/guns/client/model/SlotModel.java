package com.tacz.guns.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;

import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.pojo.model.FaceUVsItem;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.PoseStack;
import com.tacz.guns.mcshim.VertexConsumer;

/**
 * 1.7.10 的「模型」基类是 {@link ModelBase}（1.20 的 {@code EntityModel<T>}）。
 *
 * <p>
 * 移植说明：
 * <ul>
 * <li>1.20 {@code EntityModel#renderToBuffer(PoseStack, VertexConsumer, int, int, float, float, float, float)}
 * 在 1.7.10 没有对应方法，但 TACZ 自己所有调用点都直接调 {@code renderToBuffer(...)}（见
 * {@code GunItemRendererWrapper}、{@code AttachmentItemRenderer} 等），与 {@code ModelBase#render(...)}
 * 无关，因此这里把它保留为普通方法，只有注解去掉。</li>
 * <li>1.20 {@code EntityModel#setupAnim(...)} 对应 1.7.10 {@link ModelBase#setRotationAngles}
 * （参数顺序不同、多一个 Entity 参数在最后）。本模型是固定几何、无动画，两边都是空实现；
 * 为保持上游的 public 方法名，这里保留 {@code setupAnim} 但不再标 {@code @Override}。</li>
 * </ul>
 */
public class SlotModel extends ModelBase {

    private final BedrockPart bone;

    public SlotModel(boolean illuminated) {
        bone = new BedrockPart("slot");
        bone.setPos(8.0F, 24.0F, -10.0F);
        bone.cubes.add(
            new BedrockCubePerFace(-16.0F, -16.0F, 9.5F, 16.0F, 16.0F, 0, 0, 16, 16, FaceUVsItem.singleSouthFace()));
        bone.illuminated = illuminated;
    }

    public SlotModel() {
        this(false);
    }

    public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch) {}

    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
        float red, float green, float blue, float alpha) {
        bone.render(poseStack, ItemDisplayContext.GUI, buffer, packedLight, packedOverlay);
    }
}
