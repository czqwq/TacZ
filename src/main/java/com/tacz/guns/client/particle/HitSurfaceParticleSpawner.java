package com.tacz.guns.client.particle;

import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.particle.EntityBlockDustFX;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityLavaFX;
import net.minecraft.client.particle.EntityReddustFX;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.StringUtils;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.pojo.display.ammo.HitSurfaceParticle;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.util.ColorHex;

/**
 * 子弹命中方块表面的碎屑粒子生成器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/particle/HitSurfaceParticleSpawner.java}
 *
 * <h2>1.7.10 平台替换（{@code ParticleOptions} 这一层）</h2>
 * 上游的核心抽象是 1.20 的 {@code ParticleOptions}（「粒子类型 + 参数」的对象）与
 * {@code ParticleEngine#createParticle(options, ...)}。1.7.10 <b>完全没有</b>这一层：没有
 * {@code ParticleOptions} / {@code ParticleType} / {@code ParticleEngine}，粒子按<b>名字字符串</b>派发
 * （{@code RenderGlobal#doSpawnParticle(String, ...)}，见 {@code build/rfg/minecraft-src/java/net/minecraft/
 * client/renderer/RenderGlobal.java:2003}）。因此本类用包内自建的 {@link ParticleSpec} 承载「解析后的粒子描述」，
 * 用 {@link #spawnParticle} 把它变成真正的 {@link EntityFX}：
 *
 * <pre>
 *   上游 ParticleOptions                            →  1.7.10
 *   BlockParticleOption(ParticleTypes.BLOCK)        →  new EntityDiggingFX(...).applyRenderColor(meta)
 *   BlockParticleOption(ParticleTypes.FALLING_DUST) →  new EntityBlockDustFX(...).applyRenderColor(meta)
 *   ParticleTypes.LAVA                              →  new EntityLavaFX(world, x, y, z)
 *   DustParticleOptions(color, scale)               →  new EntityReddustFX(world, x, y, z, scale, r, g, b)
 *   DustColorTransitionOptions                      →  （无对应，降级为单色 EntityReddustFX）
 *   ParticleTypes.ITEM                              →  null（上游也返回 null）
 *   其它（按名字）                                   →  RenderGlobal#doSpawnParticle(name, ...)
 *   ParticleArgument.readParticle(...)              →  （无 brigadier，见 parseParticle 的 TODO）
 * </pre>
 *
 * <h2>降级点（4 处，均已在方法处标注 TODO(L2)）</h2>
 * <ul>
 * <li>{@code ParticleArgument.readParticle} + {@code BuiltInRegistries.PARTICLE_TYPE}（带参数 / 带命名空间的
 * 粒子写法）→ 1.7.10 无 brigadier、无粒子注册表，只支持 {@code minecraft} 命名空间的裸名字；</li>
 * <li>{@code DustColorTransitionOptions}（双色渐变尘）→ 1.7.10 只有单色 {@code EntityReddustFX}；</li>
 * <li>{@code Particle#setLifetime} → 反射写 {@code particleMaxAge}，见 {@link ParticleCompat#setLifetime}；</li>
 * <li>{@code ParticleEngine#createParticle} 无门限 → {@code RenderGlobal#doSpawnParticle} 有 16 格与
 * particleSetting 门限，见 {@link ParticleCompat#spawnNamed}。</li>
 * </ul>
 */
public final class HitSurfaceParticleSpawner {

    private static final int MAX_BLOCK_PARTICLE_COUNT = 32;
    private static final int MAX_DISPLAY_PARTICLE_COUNT_PER_HIT = 64;
    private static final float DEFAULT_SURFACE_OFFSET = 0.03f;
    private static final float DEFAULT_RANDOM_OFFSET = 0.01f;
    private static final int DEFAULT_BLOCK_PARTICLE_COUNT = 3;
    private static final float DEFAULT_BLOCK_NORMAL_SPEED = 0.14f;
    private static final float DEFAULT_BLOCK_RANDOM_SPEED = 0.015f;
    private static final float DEFAULT_BLOCK_SPREAD_ANGLE = 30.0f;
    private static final String DUST = "minecraft:dust";
    private static final String DUST_COLOR_TRANSITION = "minecraft:dust_color_transition";
    private static final String BLOCK = "minecraft:block";
    private static final String FALLING_DUST = "minecraft:falling_dust";
    private static final String ITEM = "minecraft:item";
    /** 1.7.10 的粒子名字没有命名空间，{@code minecraft} 前缀需要剥掉。 */
    private static final String MINECRAFT_PREFIX = "minecraft:";

    private HitSurfaceParticleSpawner() {}

    /**
     * 上游 {@code spawn(ClientLevel, Vec3, Direction, BlockPos, String, String, String, boolean)} 的 1.7.10 形态。
     * 实参顺序与上游一致，只把复合类型拆成标量。
     */
    public static void spawn(World level, double hitX, double hitY, double hitZ, ForgeDirection direction, int blockX,
        int blockY, int blockZ, String ammoId, String gunId, String gunDisplayId, boolean incendiary) {
        if (level == null) {
            return;
        }
        Vec3 hitPos = Vec3.createVectorHelper(hitX, hitY, hitZ);
        if (!RenderConfig.HIT_PARTICLE_ENABLE.get() || !isCloseEnough(hitPos)) {
            return;
        }
        Block block = level.getBlock(blockX, blockY, blockZ);
        if (block == null) {
            return;
        }
        int metadata = level.getBlockMetadata(blockX, blockY, blockZ);
        Random random = level.rand;
        Integer blockParticleCount = getAmmoBlockParticleCount(ammoId);
        spawnBlockParticles(level, random, block, metadata, hitPos, direction, blockParticleCount, incendiary);
        if (!RenderConfig.HIT_EXTRA_PARTICLE_ENABLE.get()) {
            return;
        }
        List<HitSurfaceParticle> particles = getDisplayParticles(ammoId, gunId, gunDisplayId);
        if (particles == null || particles.isEmpty()) {
            return;
        }
        int remainingCount = MAX_DISPLAY_PARTICLE_COUNT_PER_HIT;
        for (HitSurfaceParticle particle : particles) {
            remainingCount -= spawnDisplayParticle(
                level,
                random,
                block,
                metadata,
                hitPos,
                direction,
                particle,
                remainingCount);
            if (remainingCount <= 0) {
                return;
            }
        }
    }

    private static boolean isCloseEnough(Vec3 hitPos) {
        int maxDistance = RenderConfig.HIT_PARTICLE_MAX_DISTANCE.get();
        if (maxDistance <= 0) {
            return true;
        }
        EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        double maxDistanceSqr = (double) maxDistance * (double) maxDistance;
        return player != null && player.getDistanceSq(hitPos.xCoord, hitPos.yCoord, hitPos.zCoord) <= maxDistanceSqr;
    }

    private static void spawnBlockParticles(World level, Random random, Block block, int metadata, Vec3 hitPos,
        ForgeDirection direction, @Nullable Integer configuredCount, boolean incendiary) {
        int count = configuredCount == null ? DEFAULT_BLOCK_PARTICLE_COUNT
            : Math.min(configuredCount, MAX_BLOCK_PARTICLE_COUNT);
        if (count <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Vec3 pos = randomPosition(random, hitPos, direction, DEFAULT_SURFACE_OFFSET, DEFAULT_RANDOM_OFFSET);
            Vec3 speed = randomVelocity(
                random,
                direction,
                DEFAULT_BLOCK_NORMAL_SPEED,
                DEFAULT_BLOCK_RANDOM_SPEED,
                DEFAULT_BLOCK_SPREAD_ANGLE);
            if (incendiary) {
                // 上游 incendiary ? ParticleTypes.LAVA : new BlockParticleOption(ParticleTypes.BLOCK, state)
                addEffect(new EntityLavaFX(level, pos.xCoord, pos.yCoord, pos.zCoord));
            } else {
                addEffect(
                    new EntityDiggingFX(
                        level,
                        pos.xCoord,
                        pos.yCoord,
                        pos.zCoord,
                        speed.xCoord,
                        speed.yCoord,
                        speed.zCoord,
                        block,
                        metadata).applyRenderColor(metadata));
            }
        }
    }

    @Nullable
    private static Integer getAmmoBlockParticleCount(String ammoId) {
        try {
            return TimelessAPI.getClientAmmoIndex(new ResourceLocation(ammoId))
                .map(ammoIndex -> ammoIndex.getHitBlockParticleCount())
                .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static List<HitSurfaceParticle> getDisplayParticles(String ammoId, String gunId, String gunDisplayId) {
        try {
            ResourceLocation gunDisplayLocation = new ResourceLocation(gunDisplayId);
            ResourceLocation gunLocation = new ResourceLocation(gunId);
            var gunDisplay = TimelessAPI.getGunDisplay(gunDisplayLocation, gunLocation);
            if (gunDisplay.isPresent() && gunDisplay.get()
                .getHitSurfaceParticles() != null) {
                return gunDisplay.get()
                    .getHitSurfaceParticles();
            }
            return TimelessAPI.getClientAmmoIndex(new ResourceLocation(ammoId))
                .map(ammoIndex -> ammoIndex.getHitSurfaceParticles())
                .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int spawnDisplayParticle(World level, Random random, Block block, int metadata, Vec3 hitPos,
        ForgeDirection direction, HitSurfaceParticle particle, int maxCount) {
        ParticleSpec spec = parseParticle(block, metadata, particle);
        if (spec == null || maxCount <= 0) {
            return 0;
        }
        int count = Math.min(randomCount(random, particle), maxCount);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Vec3 pos = randomPosition(
                random,
                hitPos,
                direction,
                particle.getSurfaceOffset(),
                particle.getRandomOffset());
            Vec3 speed = randomVelocity(
                random,
                direction,
                particle.getNormalSpeed(),
                particle.getRandomSpeed(),
                particle.getSpreadAngle());
            EntityFX result = spawnParticle(level, spec, pos, speed);
            if (result != null) {
                applyParticleOverrides(result, particle);
                spawned++;
            }
        }
        return spawned;
    }

    private static int randomCount(Random random, HitSurfaceParticle particle) {
        int min = particle.getCountMin();
        int max = particle.getCountMax();
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }
        if (max == min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    /**
     * 上游 {@code parseParticle(BlockState, HitSurfaceParticle)}：把 display 配置里的粒子名字解析成
     * {@code ParticleOptions}。1.7.10 形态见类注释的对照表。
     *
     * <p>
     * TODO(L2): 上游 HitSurfaceParticleSpawner.java:174 与 :200 用
     * {@code ParticleArgument.readParticle(new StringReader(name), BuiltInRegistries.PARTICLE_TYPE.asLookup())}
     * 解析「带参数的粒子写法」（如 {@code minecraft:dust 1.0 0.5 0.2 1.0}）与任意命名空间粒子。
     * 1.7.10 没有 brigadier、没有粒子注册表、粒子也没有参数，此处按下列规则降级：
     * 带参数（按空白切分后 token 数 &gt; 1）→ 返回 {@code null}（该条不生成）；
     * 非 {@code minecraft} 命名空间的裸名字 → 原样交给 {@code RenderGlobal#doSpawnParticle}
     * （1.7.10 无此粒子，静默不生成）；{@code minecraft} 命名空间的裸名字剥掉前缀后派发。
     */
    @Nullable
    private static ParticleSpec parseParticle(Block block, int metadata, HitSurfaceParticle particle) {
        String name = particle.getName();
        if (StringUtils.isBlank(name)) {
            return null;
        }
        String trimmedName = name.trim();
        boolean hasArguments = trimmedName.split("\\s+", 2).length > 1;
        if (hasArguments) {
            // 1.7.10 的粒子没有参数，带参数的写法无法表达
            return null;
        }
        String particleId = normalizeParticleId(trimmedName);
        if (BLOCK.equals(particleId)) {
            return ParticleSpec.block(block, metadata);
        }
        if (FALLING_DUST.equals(particleId)) {
            return ParticleSpec.fallingDust(block, metadata);
        }
        if (DUST.equals(particleId)) {
            float[] color = colorOrDefault(particle.getColor(), 1.0f, 0.35f, 0.1f);
            return ParticleSpec.dust(color, positiveScale(particle));
        }
        if (DUST_COLOR_TRANSITION.equals(particleId)) {
            // TODO(L2): 上游 HitSurfaceParticleSpawner.java:190 用 DustColorTransitionOptions(fromColor,
            // toColor, scale) 生成双色渐变尘；1.7.10 只有单色 EntityReddustFX，此处降级为「取 fromColor 的单色尘」。
            float[] color = colorOrDefault(particle.getColor(), 1.0f, 0.35f, 0.1f);
            return ParticleSpec.dust(color, positiveScale(particle));
        }
        if (ITEM.equals(particleId)) {
            return null;
        }
        return ParticleSpec.named(toLegacyParticleName(particleId));
    }

    private static String normalizeParticleId(String particleName) {
        String firstToken = particleName.split("\\s+", 2)[0];
        // 上游用 ResourceLocation.tryParse（非法返回 null）；1.7.10 的 ResourceLocation 构造不做校验、也不返回 null
        ResourceLocation id = new ResourceLocation(firstToken);
        return id.toString();
    }

    /**
     * 把 1.20 的 {@code namespace:path} 粒子名转成 1.7.10 {@code RenderGlobal#doSpawnParticle} 认识的裸名字。
     *
     * <p>
     * 1.7.10 的粒子名字是 {@code flame} / {@code smoke} / {@code reddust} / {@code blockcrack_1_0} 这种
     * 无命名空间的字符串，且只有原版那一批；非 {@code minecraft} 命名空间在 1.7.10 没有注册机制，
     * 原样返回（{@code doSpawnParticle} 匹配不到就静默不生成）。
     */
    private static String toLegacyParticleName(String particleId) {
        if (particleId.startsWith(MINECRAFT_PREFIX)) {
            return particleId.substring(MINECRAFT_PREFIX.length());
        }
        return particleId;
    }

    private static float positiveScale(HitSurfaceParticle particle) {
        Float scale = particle.getScale();
        return scale == null ? 1.0f : Math.max(scale, 0.0f);
    }

    private static float[] colorOrDefault(@Nullable String color, float red, float green, float blue) {
        if (StringUtils.isBlank(color)) {
            return new float[] { red, green, blue };
        }
        return ColorHex.colorTextToRbgFloatArray(color);
    }

    /**
     * 上游 {@code applyParticleOverrides(Particle, HitSurfaceParticle)} 的 1.7.10 形态。
     *
     * <p>
     * TODO(L2): 上游 HitSurfaceParticleSpawner.java:228 直接调用 {@code Particle#setLifetime} /
     * {@code Particle#scale}（1.20 的公开方法）；1.7.10 的 {@code EntityFX} 只有
     * {@code setRBGColorF} / {@code multipleParticleScaleBy} 是公开的，寿命要反射写，见 {@link ParticleCompat}。
     */
    private static void applyParticleOverrides(EntityFX result, HitSurfaceParticle particle) {
        ParticleCompat.setLifetime(result, particle.getLifeTime());
        String color = particle.getColor();
        if (StringUtils.isNotBlank(color)) {
            float[] rgb = ColorHex.colorTextToRbgFloatArray(color);
            result.setRBGColorF(rgb[0], rgb[1], rgb[2]);
        }
        Float scale = particle.getScale();
        if (scale != null && scale > 0 && !usesScaleInParticleOptions(particle)) {
            ParticleCompat.scale(result, scale);
        }
    }

    /**
     * 上游判断「scale 是否已经被粒子参数本身消费掉」（{@code DustParticleOptions} 自带 size 参数）。
     * 1.7.10 里 {@code EntityReddustFX} 的构造器第一参数就是尺寸，语义完全相同。
     */
    private static boolean usesScaleInParticleOptions(HitSurfaceParticle particle) {
        String name = particle.getName();
        if (StringUtils.isBlank(name)) {
            return false;
        }
        String particleId = normalizeParticleId(name.trim());
        return DUST.equals(particleId) || DUST_COLOR_TRANSITION.equals(particleId);
    }

    private static Vec3 randomPosition(Random random, Vec3 hitPos, ForgeDirection direction, float surfaceOffset,
        float randomOffset) {
        Vec3 normal = Vec3.createVectorHelper(direction.offsetX, direction.offsetY, direction.offsetZ);
        double offsetX = randomOffset == 0 ? 0 : random.nextGaussian() * randomOffset;
        double offsetY = randomOffset == 0 ? 0 : random.nextGaussian() * randomOffset;
        double offsetZ = randomOffset == 0 ? 0 : random.nextGaussian() * randomOffset;
        return hitPos
            .addVector(normal.xCoord * surfaceOffset, normal.yCoord * surfaceOffset, normal.zCoord * surfaceOffset)
            .addVector(offsetX, offsetY, offsetZ);
    }

    private static Vec3 randomVelocity(Random random, ForgeDirection direction, float normalSpeed, float randomSpeed,
        float spreadAngle) {
        Vec3 normal = Vec3.createVectorHelper(direction.offsetX, direction.offsetY, direction.offsetZ)
            .normalize();
        Vec3 tangent = Math.abs(normal.yCoord) > 0.9 ? Vec3.createVectorHelper(1, 0, 0)
            : normal.crossProduct(Vec3.createVectorHelper(0, 1, 0))
                .normalize();
        Vec3 bitangent = normal.crossProduct(tangent)
            .normalize();
        double spread = Math.tan(Math.toRadians(spreadAngle));
        Vec3 cone = add(
            add(normal, scale(tangent, random.nextGaussian() * spread)),
            scale(bitangent, random.nextGaussian() * spread)).normalize();
        cone = scale(cone, normalSpeed);
        Vec3 randomVector = scale(
            Vec3.createVectorHelper(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()),
            randomSpeed);
        return add(cone, randomVector);
    }

    // ==================== 1.7.10 辅助 ====================

    /** 1.7.10 的 {@link Vec3} 没有 {@code scale}，上游用的是 {@code Vec3#scale(double)}。 */
    private static Vec3 scale(Vec3 vec, double factor) {
        return Vec3.createVectorHelper(vec.xCoord * factor, vec.yCoord * factor, vec.zCoord * factor);
    }

    /** 1.7.10 的 {@link Vec3} 没有 {@code add(Vec3)}，上游用的是 {@code Vec3#add(Vec3)}。 */
    private static Vec3 add(Vec3 left, Vec3 right) {
        return left.addVector(right.xCoord, right.yCoord, right.zCoord);
    }

    private static void addEffect(EntityFX particle) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.effectRenderer != null) {
            minecraft.effectRenderer.addEffect(particle);
        }
    }

    @Nullable
    private static EntityFX spawnParticle(World level, ParticleSpec spec, Vec3 pos, Vec3 speed) {
        switch (spec.kind) {
            case BLOCK:
                return new EntityDiggingFX(
                    level,
                    pos.xCoord,
                    pos.yCoord,
                    pos.zCoord,
                    speed.xCoord,
                    speed.yCoord,
                    speed.zCoord,
                    spec.block,
                    spec.metadata).applyRenderColor(spec.metadata);
            case FALLING_DUST:
                return new EntityBlockDustFX(
                    level,
                    pos.xCoord,
                    pos.yCoord,
                    pos.zCoord,
                    speed.xCoord,
                    speed.yCoord,
                    speed.zCoord,
                    spec.block,
                    spec.metadata).applyRenderColor(spec.metadata);
            case DUST:
                return new EntityReddustFX(
                    level,
                    pos.xCoord,
                    pos.yCoord,
                    pos.zCoord,
                    spec.scale,
                    spec.color[0],
                    spec.color[1],
                    spec.color[2]);
            case NAMED:
            default:
                return ParticleCompat.spawnNamed(
                    level,
                    spec.name,
                    pos.xCoord,
                    pos.yCoord,
                    pos.zCoord,
                    speed.xCoord,
                    speed.yCoord,
                    speed.zCoord);
        }
    }

    /**
     * 1.7.10 侧自建的「已解析的粒子描述」，替代 1.20 的 {@code ParticleOptions}。
     * 只在 {@link #parseParticle} 与 {@link #spawnParticle} 之间传递。
     */
    private static final class ParticleSpec {

        private enum Kind {
            /** 按名字派发（{@code RenderGlobal#doSpawnParticle}）。 */
            NAMED,
            /** {@code minecraft:block} → {@code EntityDiggingFX}。 */
            BLOCK,
            /** {@code minecraft:falling_dust} → {@code EntityBlockDustFX}。 */
            FALLING_DUST,
            /** {@code minecraft:dust} → {@code EntityReddustFX}。 */
            DUST
        }

        private final Kind kind;
        @Nullable
        private final String name;
        @Nullable
        private final Block block;
        private final int metadata;
        @Nullable
        private final float[] color;
        private final float scale;

        private ParticleSpec(Kind kind, @Nullable String name, @Nullable Block block, int metadata,
            @Nullable float[] color, float scale) {
            this.kind = kind;
            this.name = name;
            this.block = block;
            this.metadata = metadata;
            this.color = color;
            this.scale = scale;
        }

        private static ParticleSpec named(String name) {
            return new ParticleSpec(Kind.NAMED, name, null, 0, null, 1.0f);
        }

        private static ParticleSpec block(Block block, int metadata) {
            return new ParticleSpec(Kind.BLOCK, null, block, metadata, null, 1.0f);
        }

        private static ParticleSpec fallingDust(Block block, int metadata) {
            return new ParticleSpec(Kind.FALLING_DUST, null, block, metadata, null, 1.0f);
        }

        private static ParticleSpec dust(float[] color, float scale) {
            return new ParticleSpec(Kind.DUST, null, null, 0, color, scale);
        }
    }
}
