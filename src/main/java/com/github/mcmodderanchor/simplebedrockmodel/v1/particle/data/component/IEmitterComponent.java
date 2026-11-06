// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/IEmitterComponent.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;

/**
 * 发射器运行时组件接口。每个 emitter 持有独立实例，可持有运行时状态。
 * <p>
 * 定义层请使用 {@link IEmitterComponentDefinition}。
 */
public interface IEmitterComponent extends IComponent {

    /**
     * 发射器创建时调用一次。
     */
    default void apply(ParticleEmitterInstance emitter) {}

    /**
     * 发射器每 tick 调用。
     */
    default void update(ParticleEmitterInstance emitter) {}
}
