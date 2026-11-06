// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/event/IEventNode.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event;

/**
 * 粒子事件节点接口。
 * <p>
 * 事件节点定义在 {@code particle_effect.events} 中，由生命周期事件组件触发。
 */
public sealed interface IEventNode permits ParticleEffectEvent,SoundEffectEvent,EventSequence,EventRandomize,EventLog,MolangExpressionEvent {
}
