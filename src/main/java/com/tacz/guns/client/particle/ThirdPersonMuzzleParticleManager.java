package com.tacz.guns.client.particle;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.joml.Matrix4f;

import com.github.mcmodderanchor.simplebedrockmodel.port.Vec3;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.data.ParticleEffectDefinition;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.resource.ParticleDefinitionLoader;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v1.particle.world.WorldEmitterManager;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.time.GunTime;

/**
 * 第三人称枪口粒子管理器。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/particle/ThirdPersonMuzzleParticleManager.java}。
 *
 * <h2>1.7.10 映射</h2>
 * <table border="1">
 * <caption>上游 → 1.7.10</caption>
 * <tr>
 * <th>上游</th>
 * <th>1.7.10</th>
 * </tr>
 * <tr>
 * <td>{@code net.minecraft.world.entity.LivingEntity}</td>
 * <td>{@code net.minecraft.entity.EntityLivingBase}</td>
 * </tr>
 * <tr>
 * <td>{@code ClientLevel}</td>
 * <td>{@code net.minecraft.client.multiplayer.WorldClient}</td>
 * </tr>
 * <tr>
 * <td>{@code Minecraft.getInstance().level}</td>
 * <td>{@code Minecraft.getMinecraft().theWorld}</td>
 * </tr>
 * <tr>
 * <td>{@code Minecraft.getInstance().player}（{@code LocalPlayer}）</td>
 * <td>{@code Minecraft.getMinecraft().thePlayer}（{@code EntityPlayerSP}）</td>
 * </tr>
 * <tr>
 * <td>{@code Minecraft.getInstance().options.getCameraType().isFirstPerson()}</td>
 * <td>{@code Minecraft.getMinecraft().gameSettings.thirdPersonView == 0}</td>
 * </tr>
 * <tr>
 * <td>{@code net.minecraft.world.phys.Vec3}</td>
 * <td>{@code com.github.mcmodderanchor.simplebedrockmodel.port.Vec3}（SBM 移植层复刻的 1.20 不可变
 * {@code Vec3}，含 {@code ZERO} 常量与 {@code x/y/z} 字段，见该类的 javadoc）</td>
 * </tr>
 * <tr>
 * <td>{@code net.minecraft.resources.ResourceLocation}</td>
 * <td>{@code net.minecraft.util.ResourceLocation}</td>
 * </tr>
 * <tr>
 * <td>{@code net.minecraft.world.item.ItemStack}</td>
 * <td>{@code net.minecraft.item.ItemStack}</td>
 * </tr>
 * </table>
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>{@code Vec3} 用 SBM 移植层的 {@code port.Vec3}（上游 SBM 的 API 就是它），
 * 与 1.7.10 可变的 {@code net.minecraft.util.Vec3} 不是同一个类型。</li>
 * <li>{@code options.getCameraType()}：1.7.10 用 {@code gameSettings.thirdPersonView == 0}
 * 表达「第一人称」。</li>
 * <li>其余逻辑（火光窗口期、锚点跟随、烟雾一次性消费、视角门控）逐行照搬上游。</li>
 * </ul>
 */
public final class ThirdPersonMuzzleParticleManager {

    private static final long FLASH_WINDOW_MS = 150;

    private static final Map<EntityLivingBase, MuzzleParticleManager.MuzzleShot> PENDING = new WeakHashMap<>();

    private static final Map<EntityLivingBase, MuzzleParticleManager.MuzzleShot> FLASH_PARAMS = new WeakHashMap<>();

    private static final Map<EntityLivingBase, ParticleEmitterInstance> FLASH_EMITTERS = new WeakHashMap<>();

    private static final Map<EntityLivingBase, Long> FLASH_START = new WeakHashMap<>();

    /**
     * 当前正在渲染第三人称持枪层的实体。由 {@code ItemInHandLayerMixin} 维护，
     * 供 BEWLR 渲染时关联到对应实体。
     */
    @Nullable
    public static EntityLivingBase currentEntity = null;

    private ThirdPersonMuzzleParticleManager() {}

    public static void resetTimestamps() {
        PENDING.clear();
        FLASH_PARAMS.clear();
        FLASH_EMITTERS.clear();
        FLASH_START.clear();
        currentEntity = null;
    }

    /**
     * 客户端开火事件调用：任何射手（本机玩家 + 服务器广播的其他玩家）。
     * <p>
     * 视角门控：本机玩家在第一人称视角下不渲染其第三人称持枪层，若此时入队会在
     * 切回第三人称时一次性堆积全发；因此本机玩家的请求仅在非第一人称视角时入队。
     * 其他玩家（服务器广播）始终通过其第三人称持枪渲染消费，与本机视角无关。
     */
    public static void onShoot(EntityLivingBase shooter, ItemStack gunItem) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (shooter.equals(player) && minecraft.gameSettings.thirdPersonView == 0) {
            return;
        }
        MuzzleParticleManager.MuzzleShot shot = MuzzleParticleManager.resolveShot(gunItem);
        if (shot == null) {
            return;
        }
        PENDING.put(shooter, shot);
        FLASH_PARAMS.put(shooter, shot);
        FLASH_START.put(shooter, GunTime.nowMillis());
    }

    /**
     * 第三人称持枪渲染时调用：更新该实体枪口效果。
     * <p>
     * 火光：窗口期内每帧创建/更新锚点跟随发射器（位置+朝向跟随枪口），
     * 窗口结束清理发射器维护（粒子自然消亡）；不依赖烟雾的一次性消费。
     * 烟雾：一次性，首次渲染时在枪口世界坐标发射。
     *
     * @param muzzleWorld 当前枪口世界矩阵（旋转+平移）
     */
    public static void onMuzzleRendered(EntityLivingBase entity, Matrix4f muzzleWorld) {
        WorldClient level = Minecraft.getMinecraft().theWorld;
        if (level == null) {
            return;
        }
        long now = GunTime.nowMillis();

        Long flashStart = FLASH_START.get(entity);
        if (flashStart != null) {
            if (now < flashStart || now - flashStart >= FLASH_WINDOW_MS) {
                FLASH_START.remove(entity);
                FLASH_PARAMS.remove(entity);
                FLASH_EMITTERS.remove(entity);
            } else {
                ParticleEmitterInstance flashEmitter = FLASH_EMITTERS.get(entity);
                if (flashEmitter == null) {
                    MuzzleParticleManager.MuzzleShot shot = FLASH_PARAMS.get(entity);
                    if (shot != null && shot.thirdPersonFlashId() != null && shot.flashScale() > 0) {
                        ParticleEffectDefinition def = ParticleDefinitionLoader.getInstance()
                            .getDefinition(shot.thirdPersonFlashId());
                        if (def != null) {
                            Vec3 spawnPos = new Vec3(muzzleWorld.m30(), muzzleWorld.m31(), muzzleWorld.m32());
                            flashEmitter = WorldEmitterManager.getInstance()
                                .addEmitter(level, spawnPos, Vec3.ZERO, def);
                            if (flashEmitter != null) {
                                flashEmitter.setVariable("muzzle_scale", shot.flashScale());
                                flashEmitter.setVariable("muzzle_speed", shot.speedScale());
                                flashEmitter.setFollowAnchor(true);
                                FLASH_EMITTERS.put(entity, flashEmitter);
                                WorldEmitterManager.getInstance()
                                    .updateEmitterAnchor(flashEmitter, muzzleWorld);
                            }
                        }
                    }
                } else {
                    if (!WorldEmitterManager.getInstance()
                        .updateEmitterAnchor(flashEmitter, muzzleWorld)) {
                        FLASH_EMITTERS.remove(entity);
                    }
                }
            }
        }

        // 烟雾：一次性消费（与火光生命周期无关）
        MuzzleParticleManager.MuzzleShot shot = PENDING.remove(entity);

        if (shot != null && RenderConfig.RENDER_MUZZLE_SMOKE.get()
            && shot.thirdPersonSmokeId() != null
            && shot.smokeScale() > 0) {
            spawnEffect(
                level,
                muzzleWorld,
                shot.thirdPersonSmokeId(),
                shot.smokeScale(),
                shot.speedScale(),
                shot.smokeParticleCount(),
                shot.smokeSize(),
                shot.smokeDirection());
        }
    }

    /**
     * @param muzzleWorld 枪口世界矩阵（含旋转），不能只取平移分量——粒子的发射方向要经它变换到世界空间，
     *                    丢掉旋转后方向就成了世界坐标里定死的一条，枪一转就跟不上。
     */
    private static void spawnEffect(WorldClient level, Matrix4f muzzleWorld, ResourceLocation definitionId, float scale,
        float speedScale, float smokeParticleCount, float smokeSize,
        @Nullable MuzzleParticleManager.SmokeDirection smokeDirection) {
        ParticleEffectDefinition definition = ParticleDefinitionLoader.getInstance()
            .getDefinition(definitionId);
        if (definition == null) {
            return;
        }

        ParticleEmitterInstance emitter = WorldEmitterManager.getInstance()
            .addEmitter(
                level,
                new Vec3(muzzleWorld.m30(), muzzleWorld.m31(), muzzleWorld.m32()),
                Vec3.ZERO,
                definition);
        if (emitter == null) {
            return;
        }

        WorldEmitterManager.getInstance()
            .updateEmitterAnchor(emitter, muzzleWorld);
        emitter.setVariable("smoke_size", smokeSize);
        emitter.setVariable("smoke_vel", speedScale);
        emitter.setVariable("smoke_count", smokeParticleCount);
        MuzzleParticleManager.writeSmokeDirectionVariables(emitter, smokeDirection);
    }
}
