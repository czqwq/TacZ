package com.tacz.guns.util.time;

import java.util.function.BooleanSupplier;

/**
 * The time source for gun gameplay and animation. On a physical client this
 * follows the single-player pause; on a dedicated server it keeps running.
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>上游用 {@code com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks#client()}
 * 提供的 {@code AnimationClock}——它把「单机暂停时时钟也停」这件事做进了库内部
 * （{@code shouldTick()} 由客户端 tick 状态驱动）。</li>
 * <li>本仓库已 vendor 该库（{@code src/main/java/com/github/mcmodderanchor/**}，含
 * {@code AnimationClocks}/{@code PausedClientAnimationClock}），但 SBM 的时钟只在它的渲染处理器
 * 每帧 {@code update()} 时推进；GunTime 被 common 侧多处调用，这里仍用本类自带的
 * <b>「单调时钟 + 可注入的 shouldTick 供应器 + 暂停时长补偿」</b>（语义等价，且 util 不反向依赖
 * SBM 渲染路径）：</li>
 * <ul>
 * <li>时间源 {@code System.nanoTime()}（单调、不受系统墙钟调整影响，与上游一致）；</li>
 * <li>{@code shouldTick()} 默认恒为 {@code true}（专用服务器语义），
 * 由客户端层通过 {@link #installClientTickGate(BooleanSupplier)}（幂等，推荐）或
 * {@link #setTickGate(BooleanSupplier)} 注入「暂停时返回 false」的实现；</li>
 * <li>闸门返回 false 期间 {@link #nowNanos()} 冻结不前进（暂停时长累加到
 * {@code pausedNanos}，恢复后从冻结值继续）——即复刻上游
 * {@code AnimationClocks.client()} 的「时钟本身停走」，而不只是停 {@code shouldTick} 消费者。</li>
 * </ul>
 * </li>
 * <li>因此 {@link #nowNanos()} / {@link #nowMillis()} / {@link #epoch()} /
 * {@link #resetEpoch()} 的语义与上游一致（epoch 在时钟回退时自增、
 * {@code EPOCH_OFFSET_NANOS} 保证 legacy 的 -1 永远落在窗口之外）；
 * 唯一降级点是「闸门默认恒真」：未接线时等同于旧行为（不暂停）。</li>
 * <li>{@code nowMillis()} 是网络包与 {@code CycleTaskHelper} 的调用契约，<b>必须存在且单调</b>，
 * 此处保持不变（暂停期间冻结，恢复后继续单调）。</li>
 * </ul>
 */
public final class GunTime {

    // Keep the legacy -1 "never happened" timestamp well outside ordinary Lua time windows.
    private static final long EPOCH_OFFSET_NANOS = 100_000L * 1_000_000L;
    /** 当前 epoch 的时间基准（{@link System#nanoTime()} 域，已扣除暂停累计 {@link #pausedNanos}）。 */
    private static long originNanos = System.nanoTime();
    private static volatile long epoch;
    /** 累计暂停时长（nanos）：对应上游 {@code AnimationClocks.client()} 的「暂停时停表」。 */
    private static long pausedNanos;
    /** 当前暂停段的起点（{@link System#nanoTime()} 域）；{@code -1} 表示未暂停。 */
    private static long pauseStartNanos = -1L;
    /** 上一次返回值：保证暂停/恢复边界严格单调（时钟只能前进）。 */
    private static long lastReturnedNanos;
    /** 1.7.10 降级点：默认恒为 true；客户端层可注入「暂停时返回 false」的实现。 */
    private static volatile BooleanSupplier tickGate = () -> true;
    /** {@link #installClientTickGate(BooleanSupplier)} 的幂等标记。 */
    private static volatile boolean clientTickGateInstalled = false;

    private GunTime() {}

    /**
     * 1.7.10 注入点：由客户端层提供「游戏时钟是否在走」。
     *
     * @param gate 为 {@code null} 时恢复默认的「永远在走」
     * @see #installClientTickGate(BooleanSupplier) 客户端接线入口（幂等，推荐）
     */
    public static void setTickGate(BooleanSupplier gate) {
        tickGate = gate == null ? () -> true : gate;
    }

    /**
     * 客户端接线入口（幂等）：安装「单机暂停时时钟停走」闸门。
     *
     * <p>
     * <b>为什么需要它</b>：上游 {@code AnimationClocks.client()} 自带暂停语义；1.7.10 的
     * {@link #setTickGate(BooleanSupplier)} 默认恒真 → 单机按 ESC 暂停时 {@link #shouldTick()} 仍为 true：
     * ①{@code LocalPlayerShoot} 已排队的开火/burst 任务继续跑（{@code client/gameplay/LocalPlayerShoot.java:319/359}
     * 的 {@code shouldTick} 守卫失效）；②客户端动画 L2 时钟（{@code SecondOrderDynamics}、Lua 状态机）继续积分，
     * 与 {@code client/animation/statemachine/GunAnimationStateContext} 的 javadoc
     * 「单人游戏暂停时此时间不前进」矛盾。
     *
     * <p>
     * <b>调用方必须是客户端类</b>（lambda 中要读 {@code Minecraft}）：本类位于 {@code util/time}，
     * 会被专用服务端加载；若在此直接 import 客户端类，专用服务端会因 client-only 类缺失而崩溃。
     * 专用服务端不调用本方法 → 保持默认「永远在走」的服务端语义。
     *
     * <p>
     * <b>接线（1 行，由客户端初始化 owner 调用）</b>：
     * 
     * <pre>
     * {@code
     * GunTime.installClientTickGate(() -> !Minecraft.getMinecraft().isGamePaused());
     * }
     * </pre>
     * 
     * 建议放在 {@code client/init/ClientSetupEvent#onClientSetup} 的 {@code step(...)} 内，或
     * {@code ClientProxy#init}。{@code Minecraft#isGamePaused} 的语义已包含
     * 「单人 + 当前 screen 暂停游戏 + 未开放 LAN」（{@code Minecraft.java:1117}），多人恒 false，
     * 因此这一行同时覆盖 SP/MP。
     *
     * <p>
     * 幂等：重复调用只生效第一次（避免不同初始化路径互相覆盖）。
     */
    public static void installClientTickGate(BooleanSupplier clientTickGate) {
        if (clientTickGateInstalled) {
            return;
        }
        clientTickGateInstalled = true;
        setTickGate(clientTickGate);
    }

    /** 供诊断/测试：客户端暂停闸门是否已安装。 */
    public static boolean isClientTickGateInstalled() {
        return clientTickGateInstalled;
    }

    /**
     * A nonnegative, monotonic timestamp within the current clock epoch.
     *
     * <p>
     * <b>暂停停表</b>——上游 {@code AnimationClocks.client()} 在单机暂停期间让时钟本身停走
     * （{@code nowMillis()} 不前进）。这里用「暂停时长累加补偿」实现同一语义：闸门返回 false 期间
     * 时间值冻结在暂停起点，恢复后从该值继续；默认（专用服务端，闸门恒真）行为与旧实现一致。
     * 因此 {@code GunAnimationStateContext#getCurrentTimestamp()} 的 javadoc
     * 「单人游戏暂停时此时间不前进」在接线后成立。
     */
    public static long nowNanos() {
        long raw = System.nanoTime();
        boolean ticking = shouldTick();
        synchronized (GunTime.class) {
            if (ticking) {
                if (pauseStartNanos >= 0L) {
                    pausedNanos += raw - pauseStartNanos;
                    pauseStartNanos = -1L;
                }
            } else if (pauseStartNanos < 0L) {
                pauseStartNanos = raw;
            }
            long anchor = pauseStartNanos >= 0L ? pauseStartNanos : raw;
            long value = anchor - originNanos - pausedNanos;
            if (value < 0L) {
                // 时间源回退（防御）：重新锚定基准并提升 epoch，语义同旧实现
                originNanos = anchor - pausedNanos;
                epoch++;
                value = 0L;
            }
            if (value < lastReturnedNanos) {
                value = lastReturnedNanos;
            }
            lastReturnedNanos = value;
            return EPOCH_OFFSET_NANOS + value;
        }
    }

    public static long nowMillis() {
        return nowNanos() / 1_000_000L;
    }

    public static boolean shouldTick() {
        BooleanSupplier gate = tickGate;
        return gate == null || gate.getAsBoolean();
    }

    /** Changes when the client leaves a world and its animation clock resets. */
    public static long epoch() {
        nowNanos();
        return epoch;
    }

    /**
     * 重置 epoch（切枪/离开世界/重进维度）。暂停中重置时同样保持「时间值 0 + 冻结」。
     *
     * <p>
     * 上游 {@code AnimationClocks.client()} 的 reset 语义；1.7.10 版本需额外把暂停累计
     * （{@link #pausedNanos}）折进基准，否则恢复后时间会整体偏移。
     */
    public static synchronized void resetEpoch() {
        long raw = System.nanoTime();
        if (shouldTick()) {
            if (pauseStartNanos >= 0L) {
                pausedNanos += raw - pauseStartNanos;
                pauseStartNanos = -1L;
            }
        } else if (pauseStartNanos < 0L) {
            pauseStartNanos = raw;
        }
        originNanos = (pauseStartNanos >= 0L ? pauseStartNanos : raw) - pausedNanos;
        lastReturnedNanos = 0L;
        epoch++;
    }

    /** An unset or future timestamp is outside the window. */
    public static boolean isWithin(long timestamp, long durationMillis) {
        if (timestamp < 0 || durationMillis <= 0) {
            return false;
        }
        long elapsed = nowMillis() - timestamp;
        return elapsed >= 0 && elapsed < durationMillis;
    }

    public static boolean hasElapsed(long timestamp, long durationMillis) {
        if (timestamp < 0) {
            return false;
        }
        long elapsed = nowMillis() - timestamp;
        return elapsed >= durationMillis;
    }
}
