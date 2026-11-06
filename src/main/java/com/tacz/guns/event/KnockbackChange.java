package com.tacz.guns.event;

import net.minecraft.entity.EntityLivingBase;

import com.tacz.guns.api.entity.KnockBackModifier;

/**
 * 修正实体被子弹击中后的击退强度。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/KnockbackChange.java}
 * （监听 {@code LivingKnockBackEvent}，{@code event.setStrength((float) strength)}）。
 * <p>
 * 1.7.10 差异（<b>降级</b>）：1.7.10 Forge <b>不存在</b> {@code LivingKnockBackEvent}
 * （击退强度在 {@code EntityLivingBase#attackEntityFrom} 内部按 {@code knockbackResistance}
 * 直接算出并 {@code addVelocity}，没有可监听的事件）。因此：
 * <ul>
 * <li>{@link #register()} 保留为统一调用点，但<b>不</b>注册任何监听器；</li>
 * <li>改用静态钩子 {@link #modifyStrength(EntityLivingBase, float)}：由 Mixin/entity 层在算出击退强度后调用，
 * 语义与上游一致（{@code strength >= 0} 才覆盖，否则保留原值）。</li>
 * </ul>
 * <p>
 * <b>现状（r3 已接线，勿再重复实现）</b>：{@code mixin/common/LivingEntityKnockbackMixin} 用
 * {@code @ModifyConstant} 把 {@code EntityLivingBase#knockBack} 里的原版击退强度常量 {@code 0.4F}
 * （{@code EntityLivingBase.java:1056}）替换为 {@link #modifyStrength(EntityLivingBase, float)}
 * 的返回值（{@code LivingEntityKnockbackMixin.java:108-110}，唯一调用点 {@code :110}）——等价上游
 * {@code LivingKnockBackEvent} 的 {@code setStrength}。{@code EntityLivingBase#knockBack} 在 1.7.10
 * 无子类重写（全仓仅 1 处定义），因此覆盖面与上游事件一致。
 * <p>
 * 语义：{@code strength >= 0} 时覆盖；默认 {@code -1}（{@code ShooterDataHolder#knockBackStrength}
 * 初值，{@code resetKnockBackStrength()} 也写回 {@code -1}）表示沿用原版 {@code 0.4F}，
 * 非枪械伤害因此与原版逐位相同。{@code EntityKineticBullet} 在 {@code tacAttackEntity} 前后
 * set/reset，窗口内生效。
 */
public class KnockbackChange {

    /**
     * 上游 {@code @Mod.EventBusSubscriber} 的 1.7.10 形态。1.7.10 无对应事件，空实现。
     */
    public static void register() {
        // 1.7.10 没有 LivingKnockBackEvent，无监听器可注册；
        // 消费端在 mixin/common/LivingEntityKnockbackMixin（见类注释）。
    }

    /**
     * 1.7.10 降级钩子：把 {@link KnockBackModifier#getKnockBackStrength()} 的结果应用到原始击退强度上。
     *
     * @param entity           被击退的实体
     * @param originalStrength 1.7.10 原版算出的击退强度
     * @return 修正后的击退强度；modifier 未设置（{@code < 0}）时原样返回
     */
    public static float modifyStrength(EntityLivingBase entity, float originalStrength) {
        KnockBackModifier modifier = KnockBackModifier.fromLivingEntity(entity);
        double strength = modifier.getKnockBackStrength();
        return strength >= 0 ? (float) strength : originalStrength;
    }
}
