// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/event/EventRandomize.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event;

import java.util.List;

/**
 * 加权随机事件节点。按权重随机选择一个子事件执行。
 *
 * @param entries 加权事件条目列表
 */
public record EventRandomize(List<WeightedEntry> entries) implements IEventNode {

    /**
     * 加权事件条目。
     *
     * @param weight 权重
     * @param node   事件节点
     */
    public record WeightedEntry(float weight, IEventNode node) {}
}
