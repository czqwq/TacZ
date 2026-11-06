package com.tacz.guns.util.block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.HitboxHelper;

/**
 * 子弹/弹药产生的爆炸：<b>用「射线采样命中箱」代替原版的「方块密度」</b>来算爆心到实体的遮挡，
 * 从而让伤害与击退贴合实际命中箱，并额外支持配置化的击退与忽略名单。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>上游继承 1.20 的 {@code net.minecraft.world.level.Explosion}，覆盖 {@code explode()}
 * （方块集合 + 实体伤害），再由调用方调用 {@code finalizeExplosion(true)} 破坏方块。</li>
 * <li>1.7.10 的 {@code Explosion} 只有 {@code doExplosionA()}（方块集合 + 实体伤害）与
 * {@code doExplosionB(boolean)}（破坏方块 + 音效/粒子）两段。因此本类映射为：
 * 上游 {@code explode()} → {@link #doExplosionA()}；
 * 上游 {@code finalizeExplosion(true)} → {@code doExplosionB(false)}（服务端不发粒子，靠 S27 包）。</li>
 * <li><b>1.7.10 缺 {@code Explosion.BlockInteraction}</b>（1.20 才有）：{@code KEEP}/{@code DESTROY}
 * 在这里退化为父类的 {@code isSmoking} 布尔量（1.7.10 里它同时决定「破不破坏方块」与「发不发方块粒子」），
 * 由构造参数 {@code destroyBlocks} 设置，语义与上游 KEEP/DESTROY 一致。</li>
 * <li><b>1.7.10 缺 {@code ExplosionDamageCalculator}</b>：上游的 {@code getBlockExplosionResistance /
 *       shouldBlockExplode} 两个钩子，1.7.10 的等价物就是原版 {@code Explosion#doExplosionA} 里用的
 * {@code block.getExplosionResistance(exploder, world, x, y, z, ...)} 与
 * {@code exploder.canExplosionDestroyBlock(...)}，本类按同样顺序调用。</li>
 * <li><b>1.7.10 缺 {@code DamageSource} 的 data-pack 定义</b>：上游用 {@code this.getDamageSource()}；
 * 这里退化为原版 {@link DamageSource#setExplosionSource(Explosion)}
 * （与 1.7.10 原版 {@code Explosion#doExplosionA} 完全一致）。</li>
 * <li>{@code ProtectionEnchantment.getExplosionKnockbackAfterDampener} →
 * {@link EnchantmentProtection#func_92092_a(Entity, double)}（1.7.10 的同名实现，仅方法名是 SRG 名）。</li>
 * <li>{@code level.gameEvent(...)}（1.20 的 GameEvent）：1.7.10 无对应物，直接略过（不产生任何语义）。</li>
 * <li>{@code AABB} → {@link AxisAlignedBB}；{@code BlockPos} → 三个 int；
 * {@code level.getEntities} → {@code worldObj.getEntitiesWithinAABBExcludingEntity}；
 * {@code entity.setDeltaMovement(...)} → 直接写 {@code motionX/Y/Z}；
 * {@code getBoundingBox()} → 同名字段 {@code boundingBox}；
 * {@code ignoreExplosion()} → 1.7.10 无对应（1.20 的语义是「旁观者不受伤」，1.7.10 没有旁观者），恒 {@code false}。</li>
 * <li><b>降级点</b>：1.7.10 的方块集合用 {@link ChunkPosition}（上游是 {@code BlockPos}），
 * 因此 {@code ExplodeUtil#getToBlow()} 对应 {@code affectedBlockPositions}（父类 public 字段）。</li>
 * </ul>
 */
public class ProjectileExplosion extends Explosion {

    private final World world;
    private final double x;
    private final double y;
    private final double z;
    private final float power;
    private final float radius;
    private final boolean knockback;
    private final Entity owner;
    private final Entity exploder;
    /** 等价上游 {@code getHitPlayers()}：受击玩家 → 客户端要应用的速度增量（S27 包用）。 */
    private final Map<EntityPlayer, Vec3> hitPlayers = new HashMap<>();

    public ProjectileExplosion(World world, Entity owner, Entity exploder, double x, double y, double z, float power,
        float radius, boolean knockback, boolean destroyBlocks) {
        super(world, exploder, x, y, z, radius);
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = power;
        this.radius = radius;
        this.owner = owner;
        this.exploder = exploder;
        this.knockback = knockback;
        // 1.20 的 Explosion.BlockInteraction.KEEP / DESTROY → 1.7.10 的 isSmoking
        this.isSmoking = destroyBlocks;
        this.isFlaming = AmmoConfig.EXPLOSIVE_AMMO_FIRE.get();
    }

    /** 等价上游 {@code getHitPlayers()}。 */
    public Map<EntityPlayer, Vec3> getHitPlayers() {
        return this.hitPlayers;
    }

    @Override
    public void doExplosionA() {
        // 防止直接调用时使用无效半径进行爆炸计算
        if (!Float.isFinite(this.radius) || this.radius <= 0) {
            return;
        }
        // 1.20: this.level.gameEvent(this.exploder, GameEvent.EXPLODE, BlockPos.containing(...))
        // 1.7.10 没有 GameEvent 体系，这一行没有任何等价物（不影响玩法结果）。
        Set<ChunkPosition> set = new HashSet<>();
        int i = 16;

        for (int x = 0; x < i; ++x) {
            for (int y = 0; y < i; ++y) {
                for (int z = 0; z < i; ++z) {
                    if (x == 0 || x == i - 1 || y == 0 || y == i - 1 || z == 0 || z == i - 1) {
                        double d0 = ((float) x / (i - 1) * 2.0F - 1.0F);
                        double d1 = ((float) y / (i - 1) * 2.0F - 1.0F);
                        double d2 = ((float) z / (i - 1) * 2.0F - 1.0F);
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float f = this.radius * (0.7F + this.world.rand.nextFloat() * 0.6F);
                        double blockX = this.x;
                        double blockY = this.y;
                        double blockZ = this.z;

                        for (float f1 = 0.3F; f > 0.0F; f -= 0.22500001F) {
                            int posX = MathHelper.floor_double(blockX);
                            int posY = MathHelper.floor_double(blockY);
                            int posZ = MathHelper.floor_double(blockZ);
                            // 上游 Explosion#explode 的 `!level.isInWorldBounds(pos) -> break`
                            // （tmp/minecraft-src-1.20.1/.../Explosion.java:173）；1.7.10 世界高度是 0..255。
                            if (posY < 0 || posY >= 256) {
                                break;
                            }
                            Block block = this.world.getBlock(posX, posY, posZ);
                            if (block != null && block.getMaterial() != Material.air) {
                                // 1.20: damageCalculator.getBlockExplosionResistance(...)
                                float resistance = this.exploder != null
                                    ? this.exploder.func_145772_a(this, this.world, posX, posY, posZ, block)
                                    : block.getExplosionResistance(
                                        this.exploder,
                                        this.world,
                                        posX,
                                        posY,
                                        posZ,
                                        this.x,
                                        this.y,
                                        this.z);
                                f -= (resistance + f1) * f1;
                            }

                            // 1.20: damageCalculator.shouldBlockExplode(...)
                            if (f > 0.0F && (this.exploder == null
                                || this.exploder.func_145774_a(this, this.world, posX, posY, posZ, block, f))) {
                                set.add(new ChunkPosition(posX, posY, posZ));
                            }

                            blockX += d0 * (double) f1;
                            blockY += d1 * (double) f1;
                            blockZ += d2 * (double) f1;
                        }
                    }
                }
            }
        }

        // 1.20: this.getToBlow().addAll(set)
        this.affectedBlockPositions.addAll(set);
        float radius = this.radius;
        int minX = MathHelper.floor_double(this.x - (double) radius - 1.0D);
        int maxX = MathHelper.floor_double(this.x + (double) radius + 1.0D);
        int minY = MathHelper.floor_double(this.y - (double) radius - 1.0D);
        int maxY = MathHelper.floor_double(this.y + (double) radius + 1.0D);
        int minZ = MathHelper.floor_double(this.z - (double) radius - 1.0D);
        int maxZ = MathHelper.floor_double(this.z + (double) radius + 1.0D);
        radius *= 2;
        List<Entity> entities = this.world.getEntitiesWithinAABBExcludingEntity(
            this.exploder,
            AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
        ForgeEventFactory.onExplosionDetonate(this.world, this, entities, radius);
        Vec3 explosionPos = Vec3.createVectorHelper(this.x, this.y, this.z);

        for (Entity entity : entities) {
            if (isIgnoringExplosion(entity) || EntityUtil.isBulletDamageIgnored(entity)) {
                continue;
            }

            AxisAlignedBB boundingBox = HitboxHelper.getFixedBoundingBox(entity, this.owner);
            double strength;
            double deltaX;
            double deltaY;
            double deltaZ;
            double minDistance = radius;

            Vec3[] d = new Vec3[15];

            if (!(entity instanceof EntityLivingBase)) {
                strength = Math
                    .sqrt(entity.getDistanceSq(explosionPos.xCoord, explosionPos.yCoord, explosionPos.zCoord)) * 2
                    / radius;
                deltaX = entity.posX - this.x;
                deltaY = (entity instanceof EntityTNTPrimed ? entity.posY : entity.posY + entity.getEyeHeight())
                    - this.y;
                deltaZ = entity.posZ - this.z;
            } else {
                deltaX = (boundingBox.maxX + boundingBox.minX) / 2;
                deltaY = (boundingBox.maxY + boundingBox.minY) / 2;
                deltaZ = (boundingBox.maxZ + boundingBox.minZ) / 2;
                d[0] = Vec3.createVectorHelper(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
                d[1] = Vec3.createVectorHelper(boundingBox.minX, boundingBox.minY, boundingBox.maxZ);
                d[2] = Vec3.createVectorHelper(boundingBox.minX, boundingBox.maxY, boundingBox.minZ);
                d[3] = Vec3.createVectorHelper(boundingBox.maxX, boundingBox.minY, boundingBox.minZ);
                d[4] = Vec3.createVectorHelper(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ);
                d[5] = Vec3.createVectorHelper(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
                d[6] = Vec3.createVectorHelper(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ);
                d[7] = Vec3.createVectorHelper(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ);
                d[8] = Vec3.createVectorHelper(boundingBox.minX, deltaY, deltaZ);
                d[9] = Vec3.createVectorHelper(boundingBox.maxX, deltaY, deltaZ);
                d[10] = Vec3.createVectorHelper(deltaX, boundingBox.minY, deltaZ);
                d[11] = Vec3.createVectorHelper(deltaX, boundingBox.maxY, deltaZ);
                d[12] = Vec3.createVectorHelper(deltaX, deltaY, boundingBox.minZ);
                d[13] = Vec3.createVectorHelper(deltaX, deltaY, boundingBox.maxZ);
                d[14] = Vec3.createVectorHelper(deltaX, deltaY, deltaZ);
                for (int s = 0; s < 15; s++) {
                    MovingObjectPosition result = BlockRayTrace.rayTraceBlocks(this.world, explosionPos, d[s]);
                    boolean hitsBlock = result != null
                        && result.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
                    minDistance = hitsBlock ? minDistance : Math.min(minDistance, explosionPos.distanceTo(d[s]));
                }
                strength = minDistance * 2 / radius;
                deltaX -= this.x;
                deltaY -= this.y;
                deltaZ -= this.z;
            }

            if (strength > 1.0D) {
                continue;
            }

            double distanceToExplosion = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

            if (distanceToExplosion != 0.0D) {
                deltaX /= distanceToExplosion;
                deltaY /= distanceToExplosion;
                deltaZ /= distanceToExplosion;
            }

            double damage = 1.0D - strength;
            entity.attackEntityFrom(DamageSource.setExplosionSource(this), (float) damage * this.power);

            if (entity instanceof EntityLivingBase) {
                damage = EnchantmentProtection.func_92092_a(entity, damage);
            }

            float multiplier = this.power * radius / 500;
            // 启用击退效果
            if (AmmoConfig.EXPLOSIVE_AMMO_KNOCK_BACK.get() && this.knockback) {
                entity.motionX += deltaX * damage * multiplier;
                entity.motionY += deltaY * damage * multiplier;
                entity.motionZ += deltaZ * damage * multiplier;
                if (entity instanceof EntityPlayer) {
                    EntityPlayer player = (EntityPlayer) entity;
                    if (!isSpectator(player)
                        && (!player.capabilities.isCreativeMode || !player.capabilities.isFlying)) {
                        this.hitPlayers.put(
                            player,
                            Vec3.createVectorHelper(
                                deltaX * damage * multiplier,
                                deltaY * damage * multiplier,
                                deltaZ * damage * multiplier));
                    }
                }
            }
        }
    }

    /**
     * 1.20 的 {@code Entity#ignoreExplosion()} 语义是「旁观者不被爆炸影响」。
     * 1.7.10 没有旁观者模式（1.8 才加入），因此恒为 {@code false}。
     */
    private static boolean isIgnoringExplosion(Entity entity) {
        return false;
    }

    /** 1.7.10 没有旁观者模式，恒为 {@code false}。 */
    private static boolean isSpectator(EntityPlayer player) {
        return false;
    }
}
