// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/time/AnimationClocks.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.time;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.PausedClientAnimationClock;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public final class AnimationClocks {

    // 标准的nanotime时钟，适用于服务端
    private static final AnimationClock SYSTEM = System::nanoTime;
    // 在单人游戏中跟随游戏暂停的时钟
    // TODO(L2): 上游 v1/common/time/AnimationClocks.java:15 用 DistExecutor.safeCallWhenOn(Dist.CLIENT, ...)
    // 保证专用服务端不加载客户端类。1.7.10 没有 DistExecutor，改为用 FMLCommonHandler.instance().getSide()
    // 判定物理端；ClientClockSupplier.create() 只在 client 分支被调用，因此专用服务端仍不会触发
    // PausedClientAnimationClock 的类初始化（语义等价）。
    private static final AnimationClock CLIENT_OR_SYSTEM = FMLCommonHandler.instance()
        .getSide() == Side.CLIENT ? ClientClockSupplier.create() : null;

    private AnimationClocks() {}

    public static AnimationClock system() {
        return SYSTEM;
    }

    public static AnimationClock client() {
        return CLIENT_OR_SYSTEM != null ? CLIENT_OR_SYSTEM : SYSTEM;
    }

    public static AnimationClock forEnvironment(boolean clientSide) {
        return clientSide ? client() : system();
    }

    private static final class ClientClockSupplier {

        private static AnimationClock create() {
            return PausedClientAnimationClock.getInstance();
        }
    }
}
