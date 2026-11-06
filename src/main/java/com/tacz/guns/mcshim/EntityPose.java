package com.tacz.guns.mcshim;

/**
 * 对应 1.20.1 的 {@code net.minecraft.world.entity.Pose}。
 *
 * <h2>命名说明</h2>
 * 1.20 里这个枚举的 FQN 是 {@code net.minecraft.world.entity.Pose}，而 {@code PoseStack.Pose}
 * 是另一个东西。为了在 {@code com.tacz.guns.mcshim} 里不冲突，本类命名为 <b>{@code EntityPose}</b>
 * （团队裁决）。{@code com.maydaymemory.mae.basic.Pose}（关节姿态）另见 {@link AnimationPose}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 <b>没有</b>姿势系统：只有 {@code Entity#isSneaking()}、{@code EntityPlayer#isPlayerSleeping()}、
 * {@code Entity#isInWater()}、{@code EntityLivingBase#isRiding()} 等零散布尔。
 * 本体只是枚举，真正的「由实体状态推导姿势」在 {@link EntityStateHelper#getPose}。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 里 {@code Entity#getPose()}/{@code setPose(Pose)} 是实体字段且有同步机制；
 * 1.7.10 没有这两个方法，也<b>不能</b>通过 {@code DataWatcher} 之外的途径同步。
 * 需要在 1.7.10 上「设置姿势」的地方（例如 {@code LivingEntityCrawl}）应改为
 * 维护自己的「是否趴下」状态字段，并用 {@link EntityStateHelper} 反查。</li>
 * <li>{@link #CROUCHING} 是 1.20 的真名（不是 {@code SNEAKING}）。为方便移植额外提供
 * {@link #SNEAKING} 作为 {@link #CROUCHING} 的别名常量 —— 注意它与 {@code CROUCHING}
 * <b>不是</b>同一个枚举值，{@code ==} 比较会失败，请优先用 {@link #CROUCHING}。</li>
 * <li>{@link #FALL_FLYING}（鞘翅）：1.7.10 无鞘翅，永远不会出现。</li>
 * <li>{@link #LONG_JUMPING}（山羊/蛙跳）：1.7.10 无，永远不会出现。</li>
 * <li>{@link #SPIN_ATTACK}（三叉戟）：1.7.10 无，永远不会出现。</li>
 * </ul>
 */
public enum EntityPose {

    /** 站立。 */
    STANDING,
    /** 鞘翅滑翔（1.7.10 无）。 */
    FALL_FLYING,
    /** 睡觉。 */
    SLEEPING,
    /** 游泳 / 爬行。 */
    SWIMMING,
    /** 三叉戟旋转攻击（1.7.10 无）。 */
    SPIN_ATTACK,
    /** 潜行 / 蹲下。1.20 的真名就是这个。 */
    CROUCHING,
    /** 山羊冲撞 / 蛙跳（1.7.10 无）。 */
    LONG_JUMPING,
    /** 死亡。 */
    DYING,
    /** {@link #CROUCHING} 的别名（1.7.10 侧惯用词；<b>不是同一个枚举值</b>）。 */
    SNEAKING;

    private static final EntityPose[] VALUES = values();

    /** 按 ordinal 反查，越界返回 {@link #STANDING}。 */
    public static EntityPose byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return STANDING;
        }
        return VALUES[id];
    }

    /** 是否属于「趴下/爬行」类姿势（含潜行与游泳）。 */
    public boolean isLowProfile() {
        return this == CROUCHING || this == SNEAKING || this == SWIMMING;
    }
}
