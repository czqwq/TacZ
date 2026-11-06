package com.tacz.guns.client.particle;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.client.particle.EntityCritFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFlameFX;
import net.minecraft.client.particle.EntityLavaFX;
import net.minecraft.client.particle.EntitySmokeFX;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.world.World;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

/**
 * 1.7.10 粒子平台的薄桥接层（{@code client/particle} 包内部使用）。
 *
 * <p>
 * 上游 1.20.1 的粒子创建走 {@code ParticleEngine#createParticle(ParticleOptions, ...)}，返回的
 * {@code net.minecraft.client.particle.Particle} 有 {@code setLifetime(int)} / {@code scale(float)} /
 * {@code setColor(float,float,float)} 等<b>公开</b>的可调入口。1.7.10 的 {@link EntityFX} 没有这些方法：
 *
 * <ul>
 * <li><b>按名字造粒子</b>：1.7.10 唯一的「按名字派发」入口是
 * {@link RenderGlobal#doSpawnParticle(String, double, double, double, double, double, double)}
 * （{@code World#spawnParticle} 最终也走到它）。它自己会 {@code EffectRenderer#addEffect} 并把粒子返回，
 * 与上游 {@code createParticle} 的「创建 + 加入世界」语义一致。</li>
 * <li><b>粒子名字</b>：1.7.10 的粒子名是无命名空间的 1.7 时代字符串，枪包数据用的是 1.13+ 的粒子 id
 * （{@code minecraft:cloud} / {@code minecraft:campfire_cosy_smoke} …）。因此本类负责
 * <b>id → 1.7.10 名字</b>的映射（{@link #toLegacyParticleName}），否则命中碎屑 / 弹道尾迹会一条都不出。</li>
 * <li><b>寿命</b>：{@code EntityFX#particleMaxAge} 是 {@code protected} 字段且没有 setter，
 * 1.7.10 也没有 {@code Particle#setLifetime}。子类之外的包无法直接赋值，只能用
 * {@link ObfuscationReflectionHelper}（Forge 提供的 SRG 名重映射反射）写。字段名只解析一次并缓存。</li>
 * <li><b>尺寸 / 颜色</b>：{@link EntityFX#multipleParticleScaleBy(float)} 与
 * {@link EntityFX#setRBGColorF(float, float, float)} 是公开方法，语义分别是
 * {@code particleScale *= k} 与直接改 RGB，正好对应上游 {@code Particle#scale(float)}
 * （{@code quadSize *= k}）与 {@code Particle#setColor(...)}，<b>不需要</b>反射。</li>
 * </ul>
 */
final class ParticleCompat {

    private static final String MINECRAFT_NAMESPACE = "minecraft";

    /** 解析一次并缓存；解析失败为 {@code null}，此时寿命改写静默跳过。 */
    @Nullable
    private static final Field PARTICLE_MAX_AGE = resolveField(EntityFX.class, "particleMaxAge");

    private ParticleCompat() {}

    /**
     * 按粒子 id / 名字创建粒子并加入 {@code EffectRenderer}。
     *
     * <p>
     * 上游 AmmoParticleSpawner.java:52 / HitSurfaceParticleSpawner.java:99 用
     * {@code ParticleEngine#createParticle(ParticleOptions, x, y, z, vx, vy, vz)}——不设距离门限。
     * 1.7.10 的等价派发入口 {@code RenderGlobal#doSpawnParticle} 自带 16 格距离门限
     * （{@code RenderGlobal.java:2038-2043}）：子弹尾迹与远处命中点的碎屑会**整片消失**，
     * 因此对常见名字改为直接构造 {@link EntityFX} 后 {@code EffectRenderer#addEffect}（无距离门限），
     * 只有未收录的名字才回退到原版按名派发。
     *
     * <p>
     * TODO(L2): 仍保留 1.7.10 的 {@code gameSettings.particleSetting}（「最少」不出粒子）——
     * 上游 1.20 由玩家侧同一设置控制粒子密度，语义一致。
     *
     * @param name 上游粒子 id（{@code namespace:path}）或 1.7.10 的裸名字
     *             （如 {@code flame} / {@code smoke}）；非 {@code minecraft} 命名空间在 1.7.10 无对应
     * @return 新建的粒子；未创建时为 {@code null}
     */
    @Nullable
    static EntityFX spawnNamed(World level, @Nullable String name, double x, double y, double z, double velocityX,
        double velocityY, double velocityZ) {
        if (level == null || name == null || name.isEmpty()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.effectRenderer == null) {
            return null;
        }
        // 1.7.10 原生的 particleSetting 门限（RenderGlobal.java:2007-2012、2044-2047），保留给玩家按需降载。
        int particleSetting = minecraft.gameSettings.particleSetting;
        if (particleSetting == 1 && level.rand.nextInt(3) == 0) {
            particleSetting = 2;
        }
        if (particleSetting > 1) {
            return null;
        }
        String legacyName = toLegacyParticleName(name);
        if (legacyName == null) {
            // 非 minecraft 命名空间的粒子（1.7.10 没有粒子注册表，也没有枪包自定义粒子注册入口）
            return null;
        }
        // 直接构造：绕过 RenderGlobal.java:2038-2043 的 16 格距离门限，对齐上游
        // ParticleEngine#createParticle（无距离门限、不受 particleSetting 之外的限制）。
        EntityFX particle = createLegacyParticle(level, legacyName, x, y, z, velocityX, velocityY, velocityZ);
        if (particle != null) {
            minecraft.effectRenderer.addEffect(particle);
            return particle;
        }
        // 未收录的名字回退到原版按名派发（16 格门限属平台固有限制，见类注释）
        RenderGlobal renderGlobal = minecraft.renderGlobal;
        return renderGlobal == null ? null
            : renderGlobal.doSpawnParticle(legacyName, x, y, z, velocityX, velocityY, velocityZ);
    }

    /**
     * 把 1.20 的粒子 id（{@code namespace:path}）映射到 1.7.10 的粒子名字。
     *
     * <p>
     * 1.7.10 的粒子名字是无命名空间的字符串，且只有原版那一批（{@code RenderGlobal.java:2019-2200} 的
     * if 链）。枪包数据大量使用 1.13+ 的粒子 id，其中 {@code minecraft:campfire_cosy_smoke}、
     * {@code minecraft:end_rod} 等名字在 1.7.10 完全不存在 —— 不映射就等于「一条粒子都不出」，
     * 这里按「最接近的 1.7.10 原版粒子」降级（TODO(L2)，可在实机比对后单点调整）。
     *
     * @return 1.7.10 的粒子名字；{@code null} 表示该粒子在 1.7.10 无对应（静默跳过）
     */
    @Nullable
    private static String toLegacyParticleName(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int colon = trimmed.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : trimmed.substring(0, colon);
        String path = colon < 0 ? trimmed : trimmed.substring(colon + 1);
        if (!MINECRAFT_NAMESPACE.equals(namespace)) {
            return null;
        }
        if (path.isEmpty()) {
            return null;
        }
        // 1.13+ 的粒子 id → 1.7.10 的粒子名
        return switch (path) {
            // 大团烟雾：campfire 系列与 1.14+ 的 large_smoke 都落在 1.7.10 的 largesmoke
            case "campfire_cosy_smoke", "campfire_signal_smoke", "large_smoke", "largeSmoke" -> "largesmoke";
            case "poof", "ash", "white_ash", "crimson_spore", "warped_spore", "squid_ink" -> "smoke";
            // 亮白火花：1.7.10 没有 end_rod / electric_spark，用 crit 的白色十字火花近似
            case "end_rod", "electric_spark", "glow", "glow_squid_ink" -> "crit";
            case "enchanted_hit" -> "magicCrit";
            case "soul", "soul_fire_flame", "small_flame" -> "flame";
            // 其余（cloud / flame / lava / smoke / crit / reddust / …）1.7.10 同名可用，原样返回；
            // 1.7.10 没有的名字交给调用方回退到按名派发（匹配不到就静默不生成）
            default -> path;
        };
    }

    /**
     * 直接构造 1.7.10 原版粒子（参数逐个照抄 {@code RenderGlobal#doSpawnParticle} 的对应分支，
     * 见 {@code RenderGlobal.java:2076-2154}）。
     *
     * @return 只对「枪包数据会用到的常见名字」返回实例，其余返回 {@code null}（调用方回退到按名派发）
     */
    @Nullable
    private static EntityFX createLegacyParticle(World level, String name, double x, double y, double z,
        double velocityX, double velocityY, double velocityZ) {
        return switch (name) {
            case "smoke" -> new EntitySmokeFX(level, x, y, z, velocityX, velocityY, velocityZ);
            case "largesmoke" -> new EntitySmokeFX(level, x, y, z, velocityX, velocityY, velocityZ, 2.5F);
            case "cloud" -> new EntityCloudFX(level, x, y, z, velocityX, velocityY, velocityZ);
            case "flame" -> new EntityFlameFX(level, x, y, z, velocityX, velocityY, velocityZ);
            case "lava" -> new EntityLavaFX(level, x, y, z);
            case "crit" -> new EntityCritFX(level, x, y, z, velocityX, velocityY, velocityZ);
            default -> null;
        };
    }

    /**
     * 对应上游 {@code Particle#setLifetime(int)}。
     *
     * <p>
     * TODO(L2): 上游 AmmoParticleSpawner.java:54 / HitSurfaceParticleSpawner.java:228 调用
     * {@code Particle#setLifetime(int)}（1.20 的公开方法）；1.7.10 的 {@code EntityFX#particleMaxAge}
     * 是 {@code protected} 字段、无 setter，此处用 Forge 的 SRG 重映射反射写入（字段名解析失败则跳过）。
     */
    static void setLifetime(@Nullable EntityFX particle, int lifetime) {
        if (particle == null || lifetime <= 0 || PARTICLE_MAX_AGE == null) {
            return;
        }
        try {
            PARTICLE_MAX_AGE.setInt(particle, lifetime);
        } catch (Throwable ignored) {
            // 反射被拒时保持 1.7.10 默认寿命，不影响粒子本身生成
        }
    }

    /**
     * 对应上游 {@code Particle#scale(float)}（{@code quadSize *= k}）。
     * 1.7.10 的 {@link EntityFX#multipleParticleScaleBy(float)} 是 {@code particleScale *= k}，语义一致。
     */
    static void scale(@Nullable EntityFX particle, float scale) {
        if (particle == null || scale <= 0.0F) {
            return;
        }
        particle.multipleParticleScaleBy(scale);
    }

    @Nullable
    private static Field resolveField(Class<?> owner, String mcpName) {
        try {
            // remapFieldNames 在开发环境返回原名、生产环境返回 SRG 名，两种情况都能取到真实字段
            String[] remapped = ObfuscationReflectionHelper.remapFieldNames(owner.getName(), mcpName);
            Field field = owner.getDeclaredField(remapped[0]);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
