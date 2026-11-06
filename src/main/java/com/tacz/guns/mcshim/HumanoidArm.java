package com.tacz.guns.mcshim;

/**
 * 对应 1.20.1 的 {@code net.minecraft.world.entity.HumanoidArm}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 没有这个枚举；「哪只手」在 {@code RenderItem#renderItem} 里是一个
 * {@code boolean leftHand}，在 {@code EntityLivingBase} 里靠 {@code getPrimaryHand()}（1.7.10 没有）。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code HumanoidArm#getOpposite()} 已提供。</li>
 * <li>1.7.10 侧要拿到「实体用哪只手」只能靠配置或渲染回调的 {@code leftHand} 布尔，
 * {@link #fromLeftHand(boolean)} 用于把那个布尔转成本枚举。</li>
 * </ul>
 */
public enum HumanoidArm {

    /** 左手。 */
    LEFT,
    /** 右手。 */
    RIGHT;

    /** 对应 1.20 {@code HumanoidArm#getOpposite()}。 */
    public HumanoidArm getOpposite() {
        return this == LEFT ? RIGHT : LEFT;
    }

    /** 1.7.10 渲染回调里 {@code leftHand} 布尔 → 本枚举。 */
    public static HumanoidArm fromLeftHand(boolean leftHand) {
        return leftHand ? LEFT : RIGHT;
    }

    /** 本枚举 → 1.7.10 渲染回调里的 {@code leftHand} 布尔。 */
    public boolean isLeft() {
        return this == LEFT;
    }
}
