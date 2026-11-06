// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/ParticleExpireIfNotInBlocks.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component;

import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * 粒子方块过期组件。对应 "minecraft:particle_expire_if_not_in_blocks"。
 * <p>
 * 当粒子不在指定方块内时过期。
 *
 * @param blocks 方块 ID 集合（如 "minecraft:water"）
 */
public record ParticleExpireIfNotInBlocks(Set<String> blocks)
    implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return 340;
    }

    @Override
    public boolean requireUpdate() {
        return !blocks.isEmpty();
    }

    public static ParticleExpireIfNotInBlocks fromJson(JsonElement value) {
        Set<String> blocks = new LinkedHashSet<>();
        if (value.isJsonArray()) {
            JsonArray arr = value.getAsJsonArray();
            for (JsonElement elem : arr) {
                blocks.add(elem.getAsString());
            }
        }
        return new ParticleExpireIfNotInBlocks(blocks);
    }
}
