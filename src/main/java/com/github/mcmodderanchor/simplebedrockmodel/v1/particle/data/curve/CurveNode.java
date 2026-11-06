// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/curve/CurveNode.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.curve;

/**
 * Bezier Chain 曲线节点。
 *
 * @param value 节点值
 * @param slope 节点斜率
 */
public record CurveNode(float value, float slope) {}
