// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/particle/world/WorldEmitterManager.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.github.mcmodderanchor.simplebedrockmodel.SimpleBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.port.Camera;
import com.github.mcmodderanchor.simplebedrockmodel.port.Vec3;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClock;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.time.AnimationClocks;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.EventExecutor;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.tacz.guns.mcshim.MultiBufferSource;
import com.tacz.guns.mcshim.RenderType;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * 用来管理世界中的粒子发射器。
 *
 * <p>
 * TODO(L2)（三处 1.20 → 1.7.10 平台差异，均已按「能力等价」改写）：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber(modid = ..., value = Dist.CLIENT)}：1.7.10 没有自动注册，
 * 改为在实例构造时 {@code FMLCommonHandler.instance().bus().register(this)}（EventBus 会扫描静态
 * {@code @SubscribeEvent} 方法，语义与 1.20 的类级订阅一致）。</li>
 * <li>{@code TickEvent.ClientTickEvent}：1.7.10 在 {@code cpw.mods.fml.common.gameevent.TickEvent}，
 * {@code phase} 字段同名同义。</li>
 * <li>{@code ClientPlayerNetworkEvent.LoggingOut}：1.7.10 用
 * {@code FMLNetworkEvent.ClientDisconnectionFromServerEvent}（同样是「客户端断线」时机）。</li>
 * </ul>
 * 另外 1.7.10 的字段名差异：{@code Minecraft#level → theWorld}、
 * {@code Minecraft#particleEngine → effectRenderer}（{@code addEffect} 即上游的 {@code add}）。
 */
public class WorldEmitterManager {

    private static final AnimationClock CLOCK = AnimationClocks.client();

    private static WorldEmitterManager INSTANCE;

    private final List<ActiveWorldEmitter> emitters = new ArrayList<>();
    /**
     * 由本管理器创建的世界粒子（1.7.10 版新增：EffectRenderer 无法按 RenderType 分组渲染，
     * 见 SnowStormParticle 类注释）。注销/断线时一并清空。
     */
    private final List<SnowStormParticle> spawnedParticles = new ArrayList<>();

    private WorldEmitterManager() {
        // TODO(L2): 替代 1.20 的 @Mod.EventBusSubscriber 自动注册（见类注释）。
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        // TODO(L2): 1.7.10 的渲染事件（RenderWorldLastEvent）走 MinecraftForge.EVENT_BUS，
        // 而 tick/网络事件走 FMLCommonHandler 的 bus，因此同一个实例要在两个 bus 上都注册。
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static WorldEmitterManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WorldEmitterManager();
        }
        return INSTANCE;
    }

    /**
     * 添加一个世界粒子发射器。
     *
     * @param level      客户端世界
     * @param pos        发射器世界坐标
     * @param velocity   发射器世界速度（blocks/second）
     * @param definition 粒子效果定义
     * @return 创建的发射器实例，可用于后续控制；如果 definition 为 null 则返回 null
     */
    @Nullable
    public ParticleEmitterInstance addEmitter(WorldClient level, Vec3 pos, Vec3 velocity,
        @Nullable ParticleEffectDefinition definition) {
        if (definition == null) {
            SimpleBedrockModel.LOGGER.warn("Attempted to add world emitter with null definition");
            return null;
        }

        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        ParticleEmitterInstance emitter = new ParticleEmitterInstance(definition, molang);

        // 设置事件上下文
        EventExecutor.EventContext eventCtx = new EventExecutor.EventContext(emitter, molang, level, pos);
        emitter.setEventContext(eventCtx);

        // 触发创建事件（必须在 setEventContext 之后调用）
        emitter.fireCreationEvents();

        ActiveWorldEmitter active = new ActiveWorldEmitter();
        active.emitter = emitter;
        active.molang = molang;
        active.level = level;
        active.worldX = pos.x;
        active.worldY = pos.y;
        active.worldZ = pos.z;
        active.velocityX = velocity.x;
        active.velocityY = velocity.y;
        active.velocityZ = velocity.z;
        active.definition = definition;

        updateEmitterTransform(active);

        // 设置外部粒子管理：新生成的粒子通过回调投递到 ParticleEngine
        emitter.setExternalParticleManagement(particle -> deliverParticle(active, particle));

        emitters.add(active);
        return emitter;
    }

    // 事件处理器：**必须是实例方法**（构造器里 `register(this)` 注册的是实例，而 FML 1.7.10 的
    // `ASMEventHandler` 用 `INVOKEVIRTUAL` 回调；`public static` 会在派发时抛
    // `IncompatibleClassChangeError: Expecting non-static method`，且被 `EventBus#post` 吞掉
    // 只打一条 error ⇒ 表现为「注册成功但永不执行」）。
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!CLOCK.shouldTick()) {
            return;
        }
        getInstance().tick();
    }

    @SubscribeEvent
    public void onLoggingOut(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        getInstance().clear();
    }

    /**
     * 世界粒子的渲染 pass。
     *
     * <p>
     * TODO(L2): 1.20 由 {@code ParticleEngine} 按每个粒子的 {@code RenderType} 分组绘制；
     * 1.7.10 的 {@code EffectRenderer} 只有 4 个固定图层、同一图层内无法逐粒子换纹理
     * （见 {@code SnowStormParticle} 类注释）。因此本类自己持有粒子列表，在
     * {@code RenderWorldLastEvent} 里按 {@link RenderType} 分组、逐组绑定纹理并批量提交
     * （mcshim {@code MultiBufferSource}），语义与上游一致：同一个 RenderType 的粒子一次 draw call。
     * <p>
     * 坐标：{@link SnowStormParticle#render} 输出的是<b>相机相对坐标</b>；{@code RenderWorldLastEvent}
     * 时 GL_MODELVIEW 已经包含相机位移，因此不需要额外的平移（与 1.7.10 原生
     * {@code EntityFX#renderParticle} 用 {@code x - interpPosX} 的做法完全一致）。
     */
    public void renderWorldParticles(float partialTicks) {
        if (this.spawnedParticles.isEmpty()) {
            return;
        }
        Camera camera = Camera.getMainCamera(partialTicks);
        MultiBufferSource.BufferSource source = MultiBufferSource.immediate();
        for (int i = this.spawnedParticles.size() - 1; i >= 0; i--) {
            SnowStormParticle particle = this.spawnedParticles.get(i);
            if (particle.isDead) {
                this.spawnedParticles.remove(i);
                continue;
            }
            particle.render(source.getBuffer(particle.getRenderType()), camera, partialTicks);
        }
        source.endBatch();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        getInstance().renderWorldParticles(event.partialTicks);
    }

    /**
     * 每 tick 驱动所有发射器。
     * <p>
     * 应在客户端 tick 事件中调用。
     */
    public void tick() {
        float dt = 1f / 20f; // 固定 tick 步长

        for (int i = emitters.size() - 1; i >= 0; i--) {
            ActiveWorldEmitter active = emitters.get(i);

            // 检查世界是否仍然有效
            if (active.level != Minecraft.getMinecraft().theWorld) {
                emitters.remove(i);
                continue;
            }

            active.worldX += active.velocityX * dt;
            active.worldY += active.velocityY * dt;
            active.worldZ += active.velocityZ * dt;
            updateEmitterTransform(active);

            // 更新事件上下文中的位置
            Vec3 currentPos = new Vec3(active.worldX, active.worldY, active.worldZ);
            active.emitter.setEventContext(
                new EventExecutor.EventContext(active.emitter, active.molang, active.level, currentPos));

            active.emitter.tick(dt);

            if (active.emitter.isFinished()) {
                emitters.remove(i);
            }
        }
    }

    /**
     * 将发射器产出的粒子转换为 {@link SnowStormParticle} 并投递到 ParticleEngine。
     */
    private void deliverParticle(ActiveWorldEmitter active, ParticleInstance particle) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.effectRenderer == null || active.level == null) {
            return;
        }

        if (active.emitter.isLocalVelocity()) {
            particle.vx += active.velocityX;
            particle.vy += active.velocityY;
            particle.vz += active.velocityZ;
        }

        // 粒子坐标已经在世界空间中（ParticleEmitterInstance 的 applyLocalSpaceOnSpawn 已处理）
        // 如果粒子是世界空间的，坐标已经是绝对世界坐标
        // 如果粒子是局部空间的（localPosition=true），需要加上发射器位置
        double px;
        double py;
        double pz;
        if (particle.worldSpace) {
            px = particle.x;
            py = particle.y;
            pz = particle.z;
        } else {
            px = active.worldX + particle.x;
            py = active.worldY + particle.y;
            pz = active.worldZ + particle.z;
        }

        // 更新粒子位置为世界坐标
        particle.x = (float) px;
        particle.y = (float) py;
        particle.z = (float) pz;

        SnowStormParticle worldParticle = new SnowStormParticle(
            active.level,
            particle,
            active.definition,
            active.emitter);
        // TODO(L2): 1.20 的 ParticleEngine#add → 1.7.10 EffectRenderer#addEffect(EntityFX)。
        mc.effectRenderer.addEffect(worldParticle);
        this.spawnedParticles.add(worldParticle);
    }

    private void updateEmitterTransform(ActiveWorldEmitter active) {
        Matrix4f identity = new Matrix4f();
        Matrix4f worldTransform;
        if (active.anchorMatrix != null) {
            // 完整锚点（含旋转）：由调用方每帧提供，粒子位置与朝向均跟随
            worldTransform = active.anchorMatrix;
        } else {
            worldTransform = new Matrix4f()
                .translation((float) active.worldX, (float) active.worldY, (float) active.worldZ);
        }
        active.emitter.setEmitterTransform(identity, worldTransform);
    }

    /**
     * 更新发射器的世界锚点（仅平移）。配合发射器的 {@code followAnchor} 让已生成的
     * 世界粒子随锚点平移跟随。
     *
     * @return 是否找到并更新成功（发射器可能已结束并从管理器移除）
     */
    public boolean updateEmitterPosition(ParticleEmitterInstance emitter, Vec3 pos) {
        for (ActiveWorldEmitter active : emitters) {
            if (active.emitter == emitter) {
                active.worldX = pos.x;
                active.worldY = pos.y;
                active.worldZ = pos.z;
                active.anchorMatrix = null;
                // 立即同步 worldTransform，粒子渲染（SnowStormParticle.render）无需等待下一次
                // 管理器的 client tick 即可读到新锚点——实现渲染帧级跟随
                updateEmitterTransform(active);
                return true;
            }
        }
        return false;
    }

    /**
     * 更新发射器的完整世界锚点（旋转 + 平移）。配合 {@code followAnchor}，已生成的世界粒子
     * 每帧按此矩阵重新定位——位置与朝向均跟随锚点（如持枪者的枪口）。
     * <p>
     * 立即同步 {@code worldTransform}：本方法由渲染线程调用（渲染帧频率可能高于 client tick），
     * 若等待管理器下次 tick 再刷新，粒子跟随会滞后最多一个 tick（50ms）。
     *
     * @return 是否找到并更新成功（发射器可能已结束并从管理器移除）
     */
    public boolean updateEmitterAnchor(ParticleEmitterInstance emitter, Matrix4f anchor) {
        for (ActiveWorldEmitter active : emitters) {
            if (active.emitter == emitter) {
                active.anchorMatrix = new Matrix4f(anchor);
                active.worldX = anchor.m30();
                active.worldY = anchor.m31();
                active.worldZ = anchor.m32();
                // 关键：立即写入 emitter.worldTransform，SnowStormParticle.render 每帧读到的
                // 就是本帧最新锚点（含旋转+平移），无需等下一次 manager tick
                updateEmitterTransform(active);
                return true;
            }
        }
        return false;
    }

    /**
     * 清空所有发射器。
     */
    public void clear() {
        emitters.clear();
        spawnedParticles.clear();
    }

    public List<ActiveWorldEmitter> getEmitters() {
        return emitters;
    }

    /**
     * 获取当前活跃的发射器数量。
     */
    public int getEmitterCount() {
        return emitters.size();
    }

    /**
     * 活跃的世界发射器上下文。
     */
    public static class ActiveWorldEmitter {

        public ParticleEmitterInstance emitter;
        public ParticleMolangEnvironment molang;
        public WorldClient level;
        public double worldX, worldY, worldZ;
        public double velocityX, velocityY, velocityZ;
        public ParticleEffectDefinition definition;
        /** 完整锚点矩阵（含旋转），非 null 时优先于 worldX/Y/Z 平移锚点。 */
        @Nullable
        public Matrix4f anchorMatrix;
    }
}
