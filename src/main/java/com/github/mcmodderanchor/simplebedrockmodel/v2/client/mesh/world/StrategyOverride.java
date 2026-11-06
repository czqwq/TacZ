// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v2/client/mesh/world/StrategyOverride.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world;

/** 客户端配置中的策略选择；AUTO 使用渲染组默认值。 */
public enum StrategyOverride {

    AUTO,
    INSTANCE,
    SECTION;

    public WorldMeshStrategy strategy() {
        return this == AUTO ? null : WorldMeshStrategy.valueOf(name());
    }
}
