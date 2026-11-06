// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/runtime/EventExecutor.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime;

import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.port.Vec3;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event.EventLog;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event.EventRandomize;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event.EventSequence;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event.IEventNode;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event.MolangExpressionEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event.ParticleEffectEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.event.SoundEffectEvent;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.WorldEmitterManager;
import com.tacz.guns.mcshim.SoundEvent;

/**
 * 粒子事件执行引擎。
 */
public final class EventExecutor {

    private EventExecutor() {}

    /**
     * 事件执行上下文。
     */
    public record EventContext(ParticleEmitterInstance emitter, ParticleMolangEnvironment molang,
        @Nullable WorldClient level, @Nullable Vec3 position, Random random // 本地随机源，用于 EventRandomize
    ) {

        public EventContext(ParticleEmitterInstance emitter, ParticleMolangEnvironment molang,
            @Nullable WorldClient level, @Nullable Vec3 position) {
            this(emitter, molang, level, position, new Random());
        }
    }

    public static void fireEvents(List<String> eventNames, ParticleEffectDefinition definition, EventContext ctx) {
        if (eventNames.isEmpty()) return;
        Map<String, List<IEventNode>> allEvents = definition.getEvents();
        if (allEvents.isEmpty()) return;

        for (String name : eventNames) {
            List<IEventNode> nodes = allEvents.get(name);
            if (nodes == null) continue;
            for (IEventNode node : nodes) {
                execute(node, ctx);
            }
        }
    }

    public static void execute(IEventNode node, EventContext ctx) {
        if (node instanceof EventSequence seq) {
            for (IEventNode child : seq.nodes()) {
                execute(child, ctx);
            }
        } else if (node instanceof EventRandomize randomize) {
            executeRandomize(randomize, ctx);
        } else if (node instanceof ParticleEffectEvent effect) {
            executeParticleEffect(effect, ctx);
        } else if (node instanceof SoundEffectEvent sound) {
            executeSoundEffect(sound, ctx);
        } else if (node instanceof MolangExpressionEvent expr) {
            executeMolangExpression(expr, ctx);
        } else if (node instanceof EventLog log) {
            executeLog(log, ctx);
        }
    }

    private static void executeRandomize(EventRandomize randomize, EventContext ctx) {
        List<EventRandomize.WeightedEntry> entries = randomize.entries();
        if (entries.isEmpty()) return;

        float totalWeight = 0;
        for (EventRandomize.WeightedEntry entry : entries) {
            totalWeight += entry.weight();
        }
        if (totalWeight <= 0) return;

        float roll = ctx.random()
            .nextFloat() * totalWeight;
        float cumulative = 0;
        for (EventRandomize.WeightedEntry entry : entries) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                execute(entry.node(), ctx);
                return;
            }
        }
        execute(
            entries.get(entries.size() - 1)
                .node(),
            ctx);
    }

    private static void executeParticleEffect(ParticleEffectEvent effect, EventContext ctx) {
        if (ctx.level == null || ctx.position == null) return;

        // 执行预编译的 pre_effect_expression
        if (effect.compiledPreEffect() != null) {
            try {
                effect.compiledPreEffect()
                    .evaluate(ctx.molang.getContext());
            } catch (Exception e) {
                SimpleBedrockModel.LOGGER
                    .warn("Failed to evaluate pre_effect_expression: {}", effect.preEffectExpression(), e);
            }
        }

        ResourceLocation effectId = new ResourceLocation(effect.effect());
        ParticleEffectDefinition childDef = ParticleDefinitionLoader.getInstance()
            .getDefinition(effectId);
        if (childDef == null) {
            SimpleBedrockModel.LOGGER.debug("Particle effect event references unknown effect: {}", effect.effect());
            return;
        }

        WorldEmitterManager.getInstance()
            .addEmitter(ctx.level, ctx.position, Vec3.ZERO, childDef);
    }

    private static void executeSoundEffect(SoundEffectEvent sound, EventContext ctx) {
        if (ctx.level == null || ctx.position == null) return;
        String eventName = sound.eventName();
        if (eventName == null || eventName.isEmpty()) return;

        try {
            ResourceLocation soundId = new ResourceLocation(eventName);
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundId);
            // TODO(L2): 1.20 的 `Level#playLocalSound(x,y,z,SoundEvent,SoundSource,vol,pitch,distanceDelay)`
            // 在 1.7.10 无对应（无 SoundEvent/SoundSource 形参，也没有 distanceDelay 开关）。
            // 1.7.10 等价调用是 `World#playSound(x,y,z,name,volume,pitch,distanceDelay)`，
            // 名字取 mcshim SoundEvent 的 `getName()`（即 `namespace:path`）；
            // SoundSource.AMBIENT 在 1.7.10 没有可设置的音量分类，忽略（见 port.SoundSource 注释）。
            ctx.level
                .playSound(ctx.position.x, ctx.position.y, ctx.position.z, soundEvent.getName(), 1.0f, 1.0f, false);
        } catch (Exception e) {
            SimpleBedrockModel.LOGGER.warn("Failed to play sound effect: {}", eventName, e);
        }
    }

    private static void executeMolangExpression(MolangExpressionEvent expr, EventContext ctx) {
        // 使用预编译的表达式
        if (expr.compiledExpression() == null) return;
        try {
            expr.compiledExpression()
                .evaluate(ctx.molang.getContext());
        } catch (Exception e) {
            SimpleBedrockModel.LOGGER.warn("Failed to evaluate event molang expression: {}", expr.expression(), e);
        }
    }

    private static void executeLog(EventLog log, EventContext ctx) {
        SimpleBedrockModel.LOGGER.info("[ParticleEvent] {}", log.message());
    }
}
