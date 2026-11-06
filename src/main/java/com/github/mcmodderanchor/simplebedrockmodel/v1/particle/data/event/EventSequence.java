// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/event/EventSequence.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event;

import java.util.List;

/**
 * 顺序事件节点。按顺序执行子事件列表。
 *
 * @param nodes 子事件节点列表
 */
public record EventSequence(List<IEventNode> nodes) implements IEventNode {}
