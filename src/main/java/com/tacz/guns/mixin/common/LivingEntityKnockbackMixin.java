package com.tacz.guns.mixin.common;

import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.tacz.guns.event.KnockbackChange;

/**
 * 把「枪械击退强度」接进 1.7.10 的原版击退计算 —— 补上 {@link KnockbackChange} 在 1.7.10 缺失的消费端。
 *
 * <p>
 * <b>上游依据</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/KnockbackChange.java:10-17}
 * 监听 {@code net.minecraftforge.event.entity.living.LivingKnockBackEvent}，在
 * {@code strength >= 0} 时 {@code event.setStrength((float) strength)}。该事件在 1.20.1 由
 * {@code LivingEntity#knockback(double strength, double x, double z)} 内部派发，而 {@code strength}
 * 就是「击退强度」本身（原版 {@code LivingEntity#hurt} 传 {@code 0.4}）。
 *
 * <p>
 * <b>1.7.10 目标方法的真实位置（已逐字核对 {@code build/rfg/minecraft-src}）</b>：
 * <ul>
 * <li>击退强度<b>不在</b> {@code EntityLivingBase#attackEntityFrom} 里算 —— {@code attackEntityFrom}
 * （{@code EntityLivingBase.java:820-1040}）只在 {@code :921} 转调
 * {@code this.knockBack(entity, amount, d1, d0)}（{@code entity = source.getEntity()}，即射手），
 * 且全程没有 {@code addVelocity} 调用（1.7.10 是直接写 {@code motionX/Y/Z}）。</li>
 * <li>强度的真实落点：{@link EntityLivingBase#knockBack(net.minecraft.entity.Entity, float, double, double)}
 * （{@code EntityLivingBase.java:1050-1069}）—— {@code :1052} 判原版
 * {@code knockbackResistance} 属性；{@code :1056} <b>{@code float f2 = 0.4F;}</b> 就是击退强度；
 * {@code :1060-1062} 把它乘进 {@code motionX/motionZ} 并加到 {@code motionY}；
 * {@code :1064-1067} 把 {@code motionY} 夹到 {@code 0.4}。</li>
 * <li>{@code knockBack} 在 1.7.10 全仓<b>只有一个定义、无任何子类重写</b>
 * （{@code grep "void knockBack(" build/rfg/minecraft-src/java} 仅命中 {@code EntityLivingBase.java:1050}），
 * 因此写在它上面等价于「覆盖所有活体实体的所有伤害源」，与上游 {@code LivingKnockBackEvent}
 * 在 {@code LivingEntity#knockback} 里派发的覆盖面一致。</li>
 * </ul>
 *
 * <p>
 * <b>注入点选择理由（为什么是 {@code @ModifyConstant} 而不是 {@code @ModifyVariable}/{@code @Redirect}）</b>：
 * <ul>
 * <li>{@code @Redirect} 需要一个 INVOKE 目标，而 1.7.10 的击退是<b>内联的 {@code motionX -= ...}</b>
 * （三次 {@code putfield}），要 Redirect 就得硬编码 {@code Entity#motionX} 字段写指令，既脆又要点三次；</li>
 * <li>{@code @ModifyVariable} 需要一个「局部变量槽位/序号」，得假设 javac 把 {@code f1}/{@code f2} 排在
 * 槽 7/8（{@code this}=0、{@code Entity}=1、{@code float}=2、{@code double}×2=3-6、
 * {@code f1}=7、{@code f2}=8）。这依赖目标类编译产物的槽位分配，换编译/打补丁就会漂移；</li>
 * <li>{@code @ModifyConstant} <b>按常量值定位</b>：{@code knockBack} 里唯一的 {@code float} 常量就是
 * {@code 0.4F}（另一处 {@code 0.4000000059604645D} 是 {@code double} 夹取上界，
 * {@code :1064}，类型不同不会误命中）。它不需要描述符、不需要槽位、不需要序号，
 * 是本方法里最稳的定位方式；且「改的就是强度」这一语义与上游 {@code setStrength} 完全同构。</li>
 * </ul>
 *
 * <p>
 * <b>为什么不需要 {@code @Unique} 字段 / 不破坏其它实体</b>：
 * {@link KnockbackChange#modifyStrength(EntityLivingBase, float)} 只在
 * {@code KnockBackModifier#getKnockBackStrength() >= 0} 时覆盖，而该值的默认值是
 * {@code -1}（{@code entity/shooter/ShooterDataHolder.java:121}
 * 「负数表示使用原版击退」），{@code resetKnockBackStrength()} 也写回 {@code -1}
 * （{@code mixin/common/LivingEntityMixin.java:340-353}）。因此：
 * <ul>
 * <li>非枪械伤害：值恒为 {@code -1} → 原样返回 {@code 0.4F}，<b>与原版逐位相同</b>；</li>
 * <li>子弹伤害：{@code entity/EntityKineticBullet.java:510-517} 在
 * {@code tacAttackEntity} 前后 {@code setKnockBackStrength(this.knockback)} /
 * {@code resetKnockBackStrength()}，窗口内生效。</li>
 * </ul>
 *
 * <p>
 * <b>{@code require = 0} + {@code expect = 1} 的取舍（安全阈值）</b>：
 * {@code knockBack} 是「其它 mod（OptiFine、各种战斗/击退调整 mod）最可能改写的方法之一」。
 * 一旦被别人的 ASM/补丁换掉 {@code 0.4F} 常量或重写整个方法，注入就会 0 命中；
 * 若沿用 json 的 {@code injectors.defaultRequire = 1}，这种情况会直接
 * {@code InvalidInjectionException} → <b>启动崩</b>。宁可「击退数据不生效」也不能让游戏起不来，
 * 所以显式 {@code require = 0}。
 *
 * <p>
 * <b>{@code expect = 1} 的真实语义（已核 UniMixins 0.3.1
 * {@code org/spongepowered/asm/mixin/injection/struct/InjectionInfo.java:524-538}，别误以为它会报错）</b>：
 * <ul>
 * <li>{@code require}（默认 {@code -1}）小于注入次数 → 抛 {@code InjectionError}（<b>硬崩</b>）；</li>
 * <li>{@code expect}（默认 {@code 1}）不符 → <b>仅当</b> {@code Option.DEBUG_INJECTORS}
 * （{@code -Dmixin.debug.injectors=true}）开启时才抛 {@code InvalidInjectionException}；
 * 未开调试时<b>完全静默</b>。</li>
 * </ul>
 * 也就是说：默认运行下「注入点没命中」<b>不会崩、也不会打日志</b> —— 这正是本 Mixin 必须做
 * 实机验证的原因。想看硬证据就用
 * {@code -Dmixin.debug.injectors=true} 启动一次（0 命中会立刻抛
 * {@code ... expected 1 invocation(s) but 0 succeeded}）。{@code expect = 1} 保留在此，
 * 一是把这个诊断开关打开时就能生效，二是自解释「本注入点应当恰好命中 1 次」。
 *
 * <p>
 * <b>生效状态</b>：✅ 已接线。注入点命中与否无法静态证明，实机验证见
 * {@code docs/review/r2/08-mixin-fix.md} §三。
 */
@Mixin(EntityLivingBase.class)
public class LivingEntityKnockbackMixin {

    /**
     * 把 {@code knockBack} 里的原版击退强度 {@code 0.4F}（{@code EntityLivingBase.java:1056}）
     * 换成 {@link KnockbackChange#modifyStrength(EntityLivingBase, float)} 的结果。
     *
     * <p>
     * 形参只有一个 {@code float}，符合「{@code @ModifyConstant} 的回调只声明被修改的常量类型」的约束
     * （{@code @Inject} 才要求带全部目标形参 + {@code CallbackInfo}）。
     *
     * @param originalStrength 原版常量 {@code 0.4F}
     * @return 修正后的击退强度；modifier 未设置（{@code < 0}）时原样返回
     */
    @ModifyConstant(method = "knockBack", constant = @Constant(floatValue = 0.4F), require = 0, expect = 1)
    private float tacz$modifyKnockbackStrength(float originalStrength) {
        return KnockbackChange.modifyStrength((EntityLivingBase) (Object) this, originalStrength);
    }
}
