// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/event/EventLog.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event;

/**
 * 日志事件节点。触发时输出日志消息。
 *
 * @param message 日志消息
 */
public record EventLog(String message) implements IEventNode {}
