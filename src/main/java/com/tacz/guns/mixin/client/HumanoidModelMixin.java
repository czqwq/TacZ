package com.tacz.guns.mixin.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.client.animation.third.InnerThirdPersonManager;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/HumanoidModelMixin.java}。
 *
 * <p>
 * <b>上游用途</b>：在 {@code HumanoidModel#setupAnim(LivingEntity,float,float,float,float,float)} 的 TAIL
 * 把第三人称持枪动画的头部/手臂姿态套到原版骨骼上
 * （{@code InnerThirdPersonManager#setRotationAnglesHead}）。
 *
 * <p>
 * <b>1.7.10 等价改写</b>（对照 {@code build/rfg/minecraft-src/java/net/minecraft/client/model/ModelBiped.java}）：
 * <table border="1">
 * <tr>
 * <th>上游</th>
 * <th>1.7.10</th>
 * </tr>
 * <tr>
 * <td>{@code net.minecraft.client.model.HumanoidModel}</td>
 * <td>{@link ModelBiped}（第 8 行起）</td>
 * </tr>
 * <tr>
 * <td>{@code setupAnim(LivingEntity;FFFFF)V}</td>
 * <td>{@code setRotationAngles(FFFFFFLnet/minecraft/entity/Entity;)V}（第 116 行）</td>
 * </tr>
 * <tr>
 * <td>{@code ModelPart head/body/leftArm/rightArm}</td>
 * <td>{@code ModelRenderer bipedHead/bipedBody/bipedLeftArm/bipedRightArm}（第 12-16 行）</td>
 * </tr>
 * <tr>
 * <td>{@code ModelPart.xRot/yRot/zRot}</td>
 * <td>{@code ModelRenderer.rotateAngleX/Y/Z}</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>注意：1.7.10 的 {@code ModelBiped} 字段不是 {@code final}</b>（上游 {@code @Shadow @Final}），
 * 因此这里不能写 {@code @Final}，否则 Mixin 校验会失败。
 *
 * <p>
 * <b>生效状态</b>：功能等价。第三人称持枪姿态本身由
 * {@link InnerThirdPersonManager}（已移植，形参已是 1.7.10 的 {@link ModelRenderer}）负责，
 * 本 Mixin 只负责「在原版算完姿态之后盖上」。
 *
 * <p>
 * <b>⚠ 与 {@link PlayerModelMixin} 的强耦合（删任何一个都会坏，务必读）</b>：
 * 1.7.10 只有一个 {@code ModelBiped}，而上游有 {@code HumanoidModel} 与 {@code PlayerModel} 两个类，
 * 所以两个上游 Mixin 在这里<b>落到同一个目标类、同一个注入点</b>
 * （{@code setRotationAngles} 的 {@code TAIL}）。执行顺序完全靠
 * <b>{@code @Mixin#priority}</b> 保证：
 * <ul>
 * <li>本类 {@code priority = 1000}（默认值）→ 先 apply → 回调先执行 → <b>先</b>套第三人称持枪姿态；</li>
 * <li>{@link PlayerModelMixin} {@code priority = 1100} → 后 apply → 回调后执行 → <b>后</b>在
 * 「第一人称手持 + 主手是枪」时把手臂归零（覆盖上一步的姿态）。</li>
 * </ul>
 * 这个「先盖姿态、再清零」的先后关系与上游一致。若只把其中一个写进
 * {@code mixins.tacz.json} 的 {@code client} 数组：<b>删 {@link PlayerModelMixin} 会丢掉第一人称手臂归零，
 * 删本类会丢掉全部第三人称持枪骨骼动画</b>；而<b>改动任一 priority 数值会让两者顺序颠倒</b>
 * （手臂归零会被随后的姿态覆盖，等于无效）。<b>两个类必须同时在 json 里，priority 数值不要动。</b>
 */
@Mixin(ModelBiped.class)
public class HumanoidModelMixin {

    @Shadow
    public ModelRenderer bipedHead;
    @Shadow
    public ModelRenderer bipedBody;
    @Shadow
    public ModelRenderer bipedLeftArm;
    @Shadow
    public ModelRenderer bipedRightArm;

    @Inject(method = "setRotationAngles", at = @At(value = "TAIL"))
    private void setRotationAnglesHead(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor, Entity entityIn, CallbackInfo ci) {
        if (!(entityIn instanceof EntityLivingBase livingEntity)) {
            return;
        }
        InnerThirdPersonManager.setRotationAnglesHead(
            livingEntity,
            this.bipedRightArm,
            this.bipedLeftArm,
            this.bipedBody,
            this.bipedHead,
            limbSwingAmount);
    }
}
