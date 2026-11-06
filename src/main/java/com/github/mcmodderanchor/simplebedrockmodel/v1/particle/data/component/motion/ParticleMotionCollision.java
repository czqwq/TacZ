// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/component/motion/ParticleMotionCollision.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.motion;

import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getBoolean;
import static com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleJsonUtils.getMolang;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.molang.runtime.MolangExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IParticleComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IParticleComponentDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 粒子碰撞运动组件。对应 "minecraft:particle_motion_collision"。
 */
public record ParticleMotionCollision(@Nullable MolangExpression enabled, float collisionDrag,
    float coefficientOfRestitution, float collisionRadius, boolean expireOnContact, List<CollisionEvent> events)
    implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return 350;
    }

    @Override
    public boolean requireUpdate() {
        return enabled != null || !events.isEmpty();
    }

    public record CollisionEvent(String event, float minSpeed) {}

    public static ParticleMotionCollision fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        MolangExpression enabled = obj.has("enabled") ? molang.compile(getMolang(obj, "enabled", "1")) : null;
        float drag = obj.has("collision_drag") ? obj.get("collision_drag")
            .getAsFloat() : 0;
        float restitution = obj.has("coefficient_of_restitution") ? obj.get("coefficient_of_restitution")
            .getAsFloat() : 0;
        float radius = obj.has("collision_radius") ? obj.get("collision_radius")
            .getAsFloat() : 0;
        boolean expire = getBoolean(obj, "expire_on_contact", false);
        return new ParticleMotionCollision(enabled, drag, restitution, radius, expire, parseEvents(obj));
    }

    private static List<CollisionEvent> parseEvents(JsonObject obj) {
        if (!obj.has("events") || !obj.get("events")
            .isJsonArray()) return List.of();
        JsonArray arr = obj.getAsJsonArray("events");
        List<CollisionEvent> list = new ArrayList<>();
        for (JsonElement e : arr) {
            if (e.isJsonObject()) {
                JsonObject eventObj = e.getAsJsonObject();
                String event = eventObj.has("event") ? eventObj.get("event")
                    .getAsString() : "";
                float minSpeed = eventObj.has("min_speed") ? eventObj.get("min_speed")
                    .getAsFloat() : 2f;
                if (!event.isEmpty()) list.add(new CollisionEvent(event, minSpeed));
            }
        }
        return list;
    }
}
