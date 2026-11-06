// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/data/ParticleComponentRegistry.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.EmitterInitialization;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.EmitterLifetimeEvents;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.EmitterLocalSpace;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.FPEmitterLocalSpace;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.IComponent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceBillboard;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleAppearanceLighting;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleExpireIfInBlocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleExpireIfNotInBlocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleInitialSpeed;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleInitialSpin;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleInitialization;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleLifetimeEvents;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleLifetimeExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.ParticleLifetimeKillPlane;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.lifetime.EmitterLifetimeExpression;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.lifetime.EmitterLifetimeLooping;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.lifetime.EmitterLifetimeOnce;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.motion.ParticleMotionCollision;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.motion.ParticleMotionDynamic;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.motion.ParticleMotionParametric;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.rate.EmitterRateInstant;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.rate.EmitterRateManual;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.rate.EmitterRateSteady;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.shape.EmitterShape;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.component.tinting.ParticleTintingFactory;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;

/**
 * 粒子组件注册表。
 */
public final class ParticleComponentRegistry {

    @FunctionalInterface
    public interface ComponentDeserializer {

        IComponent deserialize(String key, JsonElement value, ParticleMolangEnvironment molang);
    }

    private static final Map<String, ComponentDeserializer> DESERIALIZERS = new HashMap<>();

    static {
        // Emitter Rate
        register("minecraft:emitter_rate_instant", EmitterRateInstant::fromJson);
        register("minecraft:emitter_rate_steady", EmitterRateSteady::fromJson);
        register("minecraft:emitter_rate_manual", EmitterRateManual::fromJson);

        // Emitter Lifetime
        register("minecraft:emitter_lifetime_looping", EmitterLifetimeLooping::fromJson);
        register("minecraft:emitter_lifetime_once", EmitterLifetimeOnce::fromJson);
        register("minecraft:emitter_lifetime_expression", EmitterLifetimeExpression::fromJson);

        // Emitter Initialization
        register(
            "minecraft:emitter_initialization",
            (key, elem, molang) -> EmitterInitialization.fromJson(elem.getAsJsonObject(), molang));

        // Emitter Local Space
        register(
            "minecraft:emitter_local_space",
            (key, elem, molang) -> EmitterLocalSpace.fromJson(elem.getAsJsonObject()));
        register(
            "sbm:fp_emitter_local_space",
            (key, elem, molang) -> FPEmitterLocalSpace.fromJson(elem.getAsJsonObject()));

        // Emitter Shape
        register("minecraft:emitter_shape_point", EmitterShape::fromJson);
        register("minecraft:emitter_shape_sphere", EmitterShape::fromJson);
        register("minecraft:emitter_shape_box", EmitterShape::fromJson);
        register("minecraft:emitter_shape_disc", EmitterShape::fromJson);
        register("minecraft:emitter_shape_entity_aabb", EmitterShape::fromJson);

        // Emitter Lifetime Events
        register(
            "minecraft:emitter_lifetime_events",
            (key, elem, molang) -> EmitterLifetimeEvents.fromJson(elem.getAsJsonObject()));

        // Particle Appearance
        register(
            "minecraft:particle_appearance_billboard",
            (key, elem, molang) -> ParticleAppearanceBillboard.fromJson(elem.getAsJsonObject(), molang));
        register("minecraft:particle_appearance_tinting", ParticleTintingFactory::fromJson);
        register(
            "minecraft:particle_appearance_lighting",
            (key, elem, molang) -> ParticleAppearanceLighting.instance());

        // Particle Motion
        register("minecraft:particle_motion_dynamic", ParticleMotionDynamic::fromJson);
        register("minecraft:particle_motion_parametric", ParticleMotionParametric::fromJson);
        register("minecraft:particle_motion_collision", ParticleMotionCollision::fromJson);

        // Particle Lifetime
        register(
            "minecraft:particle_lifetime_expression",
            (key, elem, molang) -> ParticleLifetimeExpression.fromJson(elem.getAsJsonObject(), molang));
        register(
            "minecraft:particle_lifetime_kill_plane",
            (key, elem, molang) -> ParticleLifetimeKillPlane.fromJson(elem));
        register(
            "minecraft:particle_lifetime_events",
            (key, elem, molang) -> ParticleLifetimeEvents.fromJson(elem.getAsJsonObject()));

        // Particle Expire
        register(
            "minecraft:particle_expire_if_in_blocks",
            (key, elem, molang) -> ParticleExpireIfInBlocks.fromJson(elem));
        register(
            "minecraft:particle_expire_if_not_in_blocks",
            (key, elem, molang) -> ParticleExpireIfNotInBlocks.fromJson(elem));

        // Particle Initial
        register(
            "minecraft:particle_initial_speed",
            (key, elem, molang) -> ParticleInitialSpeed.fromJson(elem, molang));
        register(
            "minecraft:particle_initial_spin",
            (key, elem, molang) -> ParticleInitialSpin.fromJson(elem.getAsJsonObject(), molang));
        register(
            "minecraft:particle_initialization",
            (key, elem, molang) -> ParticleInitialization.fromJson(elem.getAsJsonObject(), molang));
    }

    private ParticleComponentRegistry() {}

    private static void register(String key, ComponentDeserializer deserializer) {
        DESERIALIZERS.put(key, deserializer);
    }

    @Nullable
    public static IComponent deserialize(String key, JsonElement value, ParticleMolangEnvironment molang) {
        var deserializer = DESERIALIZERS.get(key);
        if (deserializer == null) return null;
        return deserializer.deserialize(key, value, molang);
    }
}
