package com.tacz.guns.client.resource.pojo.display.block;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.mcshim.ItemDisplayContext;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.mcshim.PoseStack;

/**
 * 对应 1.20.1 的 {@code net.minecraft.client.renderer.block.model.ItemTransforms}，
 * 承载方块展示 JSON 里的 {@code "transforms"} 段。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 <b>没有</b> {@code net.minecraft.client.renderer.block.model} 包（该包是 1.8 为数据驱动物品模型才加的），
 * 既没有 {@code ItemTransforms} 也没有 {@code ItemTransform} / {@code ItemCameraTransforms}。
 * 1.7.10 的物品变换是 {@code RenderItem} / {@code ForgeHooksClient} 里写死的。
 * 因此这里提供 TACZ 自有的等价 POJO：字段名与 1.20 的 JSON 键完全一致，
 * 反序列化只需要 gson 反射 + 已注册的 {@code Vector3fSerializer}（{@code Vec3Serializer}），不需要自定义 TypeAdapter。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code ItemTransforms} 还有 {@code Deserializer}（处理 {@code thirdperson}/{@code firstperson}
 * 这类旧键别名）与 {@code equals/hashCode}，1.7.10 侧均未移植；旧键别名在 1.20 也只是兼容用。</li>
 * <li>1.20 的 {@code ItemTransform} 内部用 {@code Vector3f}，做法一致。但上游反序列化时
 * {@code translation *= 0.0625}（1/16 格）并 clamp，本类无该 Deserializer ⇒ 由
 * {@link ItemTransform#apply} 以局部量完成（见该方法注释）。</li>
 * </ul>
 *
 * @see BlockDisplay#getTransforms()
 */
public class BlockItemTransforms {

    /** 全部为默认变换（无位移、无旋转、缩放 1）。 */
    public static final BlockItemTransforms NO_TRANSFORMS = new BlockItemTransforms();

    @SerializedName("thirdperson_lefthand")
    private ItemTransform thirdPersonLeftHand = ItemTransform.NO_TRANSFORM;
    @SerializedName("thirdperson_righthand")
    private ItemTransform thirdPersonRightHand = ItemTransform.NO_TRANSFORM;
    @SerializedName("firstperson_lefthand")
    private ItemTransform firstPersonLeftHand = ItemTransform.NO_TRANSFORM;
    @SerializedName("firstperson_righthand")
    private ItemTransform firstPersonRightHand = ItemTransform.NO_TRANSFORM;
    @SerializedName("head")
    private ItemTransform head = ItemTransform.NO_TRANSFORM;
    @SerializedName("gui")
    private ItemTransform gui = ItemTransform.NO_TRANSFORM;
    @SerializedName("ground")
    private ItemTransform ground = ItemTransform.NO_TRANSFORM;
    @SerializedName("fixed")
    private ItemTransform fixed = ItemTransform.NO_TRANSFORM;

    /**
     * 对应 1.20 {@code ItemTransforms#getTransform(ItemDisplayContext)}。
     *
     * <p>
     * 左手条目缺失（字段保持 {@link ItemTransform#NO_TRANSFORM} 单例）时回退到同组右手变换：1.20
     * {@code ItemTransforms.Deserializer}（ItemTransforms.java:91-101）在反序列化阶段就把
     * {@code thirdperson_lefthand → thirdperson_righthand}、{@code firstperson_lefthand → firstperson_righthand}
     * 复制过去，因此「显式写成 {@code {}} 的左手条目」不会回退（它是新建的恒等实例，不是 NO_TRANSFORM）——
     * 这里的 {@code ==} 判定与上游逐值等价。
     */
    public ItemTransform getTransform(ItemDisplayContext context) {
        if (context == null) {
            return ItemTransform.NO_TRANSFORM;
        }
        return switch (context) {
            case THIRD_PERSON_LEFT_HAND -> thirdPersonLeftHand == ItemTransform.NO_TRANSFORM ? thirdPersonRightHand
                : thirdPersonLeftHand;
            case THIRD_PERSON_RIGHT_HAND -> thirdPersonRightHand;
            case FIRST_PERSON_LEFT_HAND -> firstPersonLeftHand == ItemTransform.NO_TRANSFORM ? firstPersonRightHand
                : firstPersonLeftHand;
            case FIRST_PERSON_RIGHT_HAND -> firstPersonRightHand;
            case HEAD -> head;
            case GUI -> gui;
            case GROUND -> ground;
            case FIXED -> fixed;
            default -> ItemTransform.NO_TRANSFORM;
        };
    }

    /**
     * 对应 1.20.1 的 {@code net.minecraft.client.renderer.block.model.ItemTransform}。
     */
    public static class ItemTransform {

        /** 对应 1.20 {@code ItemTransform#NO_TRANSFORM}。 */
        public static final ItemTransform NO_TRANSFORM = new ItemTransform(
            new Vector3f(),
            new Vector3f(),
            new Vector3f(1.0F, 1.0F, 1.0F));

        @SerializedName("rotation")
        private Vector3f rotation = new Vector3f();
        @SerializedName("translation")
        private Vector3f translation = new Vector3f();
        @SerializedName("scale")
        private Vector3f scale = new Vector3f(1.0F, 1.0F, 1.0F);

        public ItemTransform() {}

        public ItemTransform(Vector3f rotation, Vector3f translation, Vector3f scale) {
            this.rotation = new Vector3f(rotation);
            this.translation = new Vector3f(translation);
            this.scale = new Vector3f(scale);
        }

        public Vector3f getRotation() {
            return rotation;
        }

        public Vector3f getTranslation() {
            return translation;
        }

        public Vector3f getScale() {
            return scale;
        }

        /**
         * JSON 里 {@code translation} 的单位是 1/16 格；1.20 在反序列化时乘 0.0625 并 clamp 到 ±5
         * （{@code ItemTransform.Deserializer#deserialize}，{@code ItemTransform.java:81-82}）。
         * 1.7.10 侧没有那个 Deserializer（gson 反射直填），故在这里按局部量换算：每帧调用幂等、
         * 不改字段（apply 可被重复调用）。{@code scale} 同样按上游 clamp 到 ±4（:84）。
         */
        private static final float TRANSLATION_UNIT = 1.0F / 16.0F;

        /**
         * 对应 1.20 {@code ItemTransform#apply(boolean leftHand, PoseStack)}：把本变换叠加到矩阵栈。
         * <p>
         * 顺序与 1.20 一致：平移（左手把 X 取反）→ 一次合成的 {@code rotationXYZ} 四元数
         * （左手把 Y/Z 旋转取反）→ 缩放。1.20 用的是**单个** {@code rotationXYZ}
         * （{@code ItemTransform.java:44}），不是三次 {@code mulPose}：默认枪包的 gui 变换含双轴旋转
         * （{@code [15, -145, 0]}），拆成三次 {@code mulPose} 合成顺序变成 Z→Y→X，朝向会不同。
         */
        public void apply(boolean leftHand, PoseStack poseStack) {
            if (this == NO_TRANSFORM) {
                return;
            }
            int mirror = leftHand ? -1 : 1;
            float tx = Mth.clamp(translation.x() * TRANSLATION_UNIT, -5.0F, 5.0F);
            float ty = Mth.clamp(translation.y() * TRANSLATION_UNIT, -5.0F, 5.0F);
            float tz = Mth.clamp(translation.z() * TRANSLATION_UNIT, -5.0F, 5.0F);
            poseStack.translate((float) mirror * tx, ty, tz);
            float rad = (float) (Math.PI / 180.0);
            poseStack.mulPose(
                new Quaternionf().rotationXYZ(
                    rotation.x() * rad,
                    rotation.y() * (float) mirror * rad,
                    rotation.z() * (float) mirror * rad));
            poseStack.scale(
                Mth.clamp(scale.x(), -4.0F, 4.0F),
                Mth.clamp(scale.y(), -4.0F, 4.0F),
                Mth.clamp(scale.z(), -4.0F, 4.0F));
        }
    }
}
