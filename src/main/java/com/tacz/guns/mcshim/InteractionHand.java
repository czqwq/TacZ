package com.tacz.guns.mcshim;

/**
 * 对应 1.20.1 的 {@code net.minecraft.world.InteractionHand}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 <b>没有</b>主/副手的枚举（副手是 1.9 引入的），{@code EntityLivingBase#getHeldItem()} 只返回主手。
 * 本枚举只是让上游的形状能编译；<b>所有 {@link #OFF_HAND} 分支在 1.7.10 上都拿不到物品</b>。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>{@link #OFF_HAND}：1.7.10 玩家没有副手槽，对应实现应返回 {@code null} / 空 ItemStack。</li>
 * <li>{@link #MAIN_HAND} 对应 1.7.10 里唯一的手（{@code EntityLivingBase#getHeldItem()}）。</li>
 * </ul>
 */
public enum InteractionHand {

    /** 主手。 */
    MAIN_HAND,
    /** 副手（1.7.10 无，降级为 {@code null}）。 */
    OFF_HAND;

    private static final InteractionHand[] VALUES = values();

    /** 按 ordinal 反查，越界返回 {@link #MAIN_HAND}。 */
    public static InteractionHand byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return MAIN_HAND;
        }
        return VALUES[id];
    }
}
