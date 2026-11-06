package com.tacz.guns.mcshim;

/**
 * 对应 1.20.1 的 {@code net.minecraft.world.item.ItemDisplayContext}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 的 {@code ItemRenderer} / {@code ForgeHooksClient} 里没有这个枚举，
 * 上下文是「用 int 传的三个布尔 + 一个 {@code ItemRenderType} 字符串」：
 * {@code ItemRenderType.EQUIPPED_FIRST_PERSON / EQUIPPED / ENTITY / INVENTORY} +
 * {@code RenderItem#renderItem(...)} 的 {@code boolean leftHand} 与 {@code TransformType} 数组。
 *
 * <p>
 * 本枚举把 1.7.10 的这些零散信息收敛成 1.20 的 9 个常量，顺序与 1.20 <b>完全一致</b>
 * （{@link #ordinal()} 可用于网络序列化，1.20 也是这么用的）。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.7.10 <b>没有</b> {@code HEAD} 上下文的原生支持（原版只对 {@code ItemFrame} 用固定变换），
 * 映射到 1.7.10 时通常退化为 {@link #FIXED} 或 {@link #GROUND}。</li>
 * <li>{@link #firstPerson(boolean)} 对应 1.20 的同名静态方法，1.7.10 侧用
 * {@code RenderItem} 的 {@code leftHand} 布尔值决定左右。</li>
 * </ul>
 */
public enum ItemDisplayContext {

    /** 无上下文（1.20 里 {@code TransformType.NONE}）。 */
    NONE,
    /** 第三人称左手。 */
    THIRD_PERSON_LEFT_HAND,
    /** 第三人称右手。 */
    THIRD_PERSON_RIGHT_HAND,
    /** 第一人称左手。 */
    FIRST_PERSON_LEFT_HAND,
    /** 第一人称右手。 */
    FIRST_PERSON_RIGHT_HAND,
    /** 头盔槽（1.7.10 无原生支持，通常退化为 {@link #FIXED}）。 */
    HEAD,
    /** GUI / 物品栏。 */
    GUI,
    /** 掉落物 / 地面。 */
    GROUND,
    /** 展示框 / 固定姿态。 */
    FIXED;

    private static final ItemDisplayContext[] VALUES = values();

    /** 对应 1.20 {@code ItemDisplayContext#firstPerson(boolean leftHand)}。 */
    public static ItemDisplayContext firstPerson(boolean leftHand) {
        return leftHand ? FIRST_PERSON_LEFT_HAND : FIRST_PERSON_RIGHT_HAND;
    }

    /** 对应 1.20 {@code ItemDisplayContext#thirdPerson(boolean leftHand)}。 */
    public static ItemDisplayContext thirdPerson(boolean leftHand) {
        return leftHand ? THIRD_PERSON_LEFT_HAND : THIRD_PERSON_RIGHT_HAND;
    }

    /** 是否是第一人称上下文。 */
    public boolean firstPerson() {
        return this == FIRST_PERSON_LEFT_HAND || this == FIRST_PERSON_RIGHT_HAND;
    }

    /** 是否是第三人称上下文。 */
    public boolean thirdPerson() {
        return this == THIRD_PERSON_LEFT_HAND || this == THIRD_PERSON_RIGHT_HAND;
    }

    /** 是否是左手上下文（第一/第三人称通用）。 */
    public boolean leftHand() {
        return this == FIRST_PERSON_LEFT_HAND || this == THIRD_PERSON_LEFT_HAND;
    }

    /**
     * 按 ordinal 反查（用于网络同步，1.20 的 {@code StreamCodec} 就是这么做的）。
     * 越界返回 {@link #NONE}（1.20 会抛异常；此处更宽容，避免坏包导致崩溃）。
     */
    public static ItemDisplayContext byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return NONE;
        }
        return VALUES[id];
    }
}
