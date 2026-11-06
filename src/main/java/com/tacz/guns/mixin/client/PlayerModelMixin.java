package com.tacz.guns.mixin.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.third.AnimationRenderContext;

/**
 * 搬运自上游 1.20.1 {@code mixin/client/PlayerModelMixin.java}。
 *
 * <p>
 * <b>上游用途</b>：在 {@code PlayerModel#setupAnim} 的 TAIL，如果当前正处在「第一人称手持渲染」阶段
 * 且主手是枪，就把两条手臂的旋转归零（并同步左右袖子），避免原版手臂摆动叠在枪械骨骼动画上。
 *
 * <p>
 * <b>1.7.10 等价改写</b>：
 * <ul>
 * <li>1.7.10 <b>没有独立的 {@code PlayerModel} 类</b>：玩家模型就是 {@link ModelBiped}
 * （{@code RenderPlayer} 里 {@code modelBipedMain/modelArmorChestplate/modelArmor} 都是 {@code ModelBiped}）。
 * 因此本 Mixin 与 {@link HumanoidModelMixin} 一样 {@code @Mixin(ModelBiped.class)}，注入同一个
 * {@code setRotationAngles} 的 TAIL；用 {@code priority = 1100}（默认 1000）保证在
 * {@link HumanoidModelMixin} <b>之后</b>应用，从而在其回调之后执行 —— 顺序与上游一致
 * （先套第三人称姿态，再在「第一人称手持」时把手臂清零）。</li>
 * <li>{@code Player} → {@link EntityPlayer}；{@code ItemStack.EMPTY} 语义 → {@code null}（本类未用到）。</li>
 * <li>{@code ModelPart.copyFrom(...)} 在 1.7.10 <b>不存在</b>，且 1.7.10 也没有左右袖子这两个独立
 * {@code ModelRenderer}（袖子是 {@code RenderPlayer} 用 {@code bipedRightArm}/{@code bipedLeftArm} 换贴图重绘的）。
 * 因此上游 {@code PlayerModelMixin.java:49-50} 的两行 {@code rightSleeve.copyFrom(rightArm)} 被删除；
 * 因为袖子与手臂在 1.7.10 是同一份骨骼，手臂归零后袖子自然跟随，<b>行为等价</b>。</li>
 * <li>{@code AnimationRenderContext.isFirstPersonHand(Object)} 已是 1.7.10 形态（形参 {@code Object}），直接可用。</li>
 * </ul>
 *
 * <p>
 * <b>生效状态</b>：功能等价（袖子同步部分因 1.7.10 无独立袖子骨骼而不需要）。
 *
 * <p>
 * <b>⚠ 与 {@link HumanoidModelMixin} 的强耦合（删任何一个都会坏，务必读）</b>：
 * 本类必须与 {@link HumanoidModelMixin} <b>同时</b>写进 {@code mixins.tacz.json} 的 {@code client} 数组。
 * 原因：1.7.10 只有一个 {@code ModelBiped}（上游是 {@code HumanoidModel} + {@code PlayerModel} 两个类），
 * 两个上游 Mixin 在这里落到<b>同一个目标类、同一个注入点</b>
 * （{@code setRotationAngles} 的 {@code TAIL}），执行先后只能靠 {@code priority}：
 * <ul>
 * <li>{@link HumanoidModelMixin} {@code priority = 1000}（默认）→ 先 apply、先执行 →
 * 先套第三人称持枪姿态；</li>
 * <li><b>本类 {@code priority = 1100}</b> → 后 apply、后执行 → 再在
 * 「第一人称手持 + 主手是枪」时把手臂归零。</li>
 * </ul>
 * 所以：<b>删掉 {@link HumanoidModelMixin} → 本类等于把原版手臂归零却没有任何枪械姿态，手臂会僵死在 0 度；
 * 删掉本类 → 丢失第一人称手持的手臂归零；改动任一 priority → 顺序颠倒，本类的归零会被随后的姿态覆盖而失效。
 * 两个类必须同时在 json 里，priority 数值不要动。</b>
 */
@Mixin(value = ModelBiped.class, priority = 1100)
public class PlayerModelMixin {

    @Shadow
    public ModelRenderer bipedLeftArm;
    @Shadow
    public ModelRenderer bipedRightArm;

    @Inject(method = "setRotationAngles", at = @At(value = "TAIL"))
    private void setRotationAnglesTail(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor, Entity entityIn, CallbackInfo ci) {
        if (!(entityIn instanceof EntityPlayer) || !AnimationRenderContext.isFirstPersonHand(entityIn)) {
            return;
        }

        // Only neutralize the vanilla arm rotation during an actual hand draw.
        ItemStack currentItem = KeepingItemRenderer.getRenderer()
            .getCurrentItem();
        if (IGun.getIGunOrNull(currentItem) != null) {
            tacz$resetAll(this.bipedRightArm);
            tacz$resetAll(this.bipedLeftArm);
        }
    }

    /**
     * 将给定模型的旋转角度重置为零
     *
     * <p>
     * 1.7.10 的 {@code ModelRenderer} 没有 {@code xRot/yRot/zRot}，对应字段名是
     * {@code rotateAngleX/rotateAngleY/rotateAngleZ}。
     */
    @Unique
    private void tacz$resetAll(ModelRenderer part) {
        part.rotateAngleX = 0.0F;
        part.rotateAngleY = 0.0F;
        part.rotateAngleZ = 0.0F;
    }
}
