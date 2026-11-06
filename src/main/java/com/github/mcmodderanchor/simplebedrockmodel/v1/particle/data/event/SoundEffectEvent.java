// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/event/SoundEffectEvent.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event;

/**
 * 音效事件节点。触发时播放音效。
 *
 * @param eventName 音效事件名称
 */
public record SoundEffectEvent(String eventName) implements IEventNode {}
