// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/client/animation/PausedClientAnimationClock.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation;

import net.minecraft.client.Minecraft;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;

/**
 * 客户端自动暂停时钟的实现
 */
public final class PausedClientAnimationClock implements AnimationClock {

    private static final PausedClientAnimationClock INSTANCE = new PausedClientAnimationClock();

    private volatile long logicalNanos;
    private long lastRealNanos;
    private boolean initialized;

    private PausedClientAnimationClock() {}

    public static PausedClientAnimationClock getInstance() {
        return INSTANCE;
    }

    public void update() {
        long realNow = System.nanoTime();
        if (!initialized) {
            initialized = true;
            lastRealNanos = realNow;
            return;
        }
        // TODO(L2): 1.20 的 Minecraft#isPaused() → 1.7.10 Minecraft#isGamePaused()（同一语义：isGamePaused 即isSingleplayer &&
        // !theIntegratedServer.isGamePaused 的反面）。
        if (Minecraft.getMinecraft()
            .isGamePaused()) {
            lastRealNanos = realNow;
            return;
        }
        logicalNanos += realNow - lastRealNanos;
        lastRealNanos = realNow;
    }

    public void reset() {
        logicalNanos = 0L;
        lastRealNanos = 0L;
        initialized = false;
    }

    @Override
    public long nowNanos() {
        return logicalNanos;
    }

    @Override
    public boolean shouldTick() {
        return !Minecraft.getMinecraft()
            .isGamePaused();
    }
}
