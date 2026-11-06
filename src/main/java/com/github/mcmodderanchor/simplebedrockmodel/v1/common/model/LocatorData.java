// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/model/LocatorData.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.model;

public record LocatorData(float[] offset, float[] rotation) {

    public static final LocatorData EMPTY = new LocatorData(new float[3], new float[3]);
}
