package com.tacz.guns.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import com.tacz.guns.entity.EntityKineticBullet;

/**
 * 子弹射线路径上的实体命中检测（含爆头箱与队伍友伤过滤）。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li><b>参数类型变化</b>：上游形参是 {@code net.minecraft.world.entity.projectile.Projectile}；
 * 1.7.10 <b>没有</b> {@code Projectile} 基类（只有 {@code EntityThrowable}/{@code EntityArrow} 这类具体类，
 * TACZ 的子弹也不继承它们），因此这里改为 {@link EntityKineticBullet}（1.7.10 侧 {@code extends Entity}），
 * 射击者仍从 {@code bulletEntity.getOwner()} 取，<b>与上游语义完全一致</b>。</li>
 * <li>{@code Vec3} → {@link Vec3}（{@code x/y/z} → {@code xCoord/yCoord/zCoord}）；
 * {@code AABB} → {@link AxisAlignedBB}。</li>
 * <li>{@code clip(startVec, endVec).orElse(null)}（返回 {@code Optional<Vec3>}）→
 * {@link AxisAlignedBB#calculateIntercept(Vec3, Vec3)}，它返回 {@link MovingObjectPosition}
 * （无命中返回 {@code null}），命中的绝对坐标在 {@code hitVec} 字段里。</li>
 * <li>{@code world.getEntities(Entity, AABB, Predicate)} →
 * {@code worldObj.getEntitiesWithinAABBExcludingEntity(Entity, AxisAlignedBB)} ＋ 本地
 * {@link #PROJECTILE_TARGETS} 过滤（1.7.10 没有带 Predicate 的重载）。</li>
 * <li>{@code isPickable()} → {@link Entity#canBeCollidedWith()}；{@code isAlive()} →
 * {@link Entity#isEntityAlive()}；{@code getVehicle()} → {@code ridingEntity}；
 * {@code getBoundingBox()} → 同名字段 {@code boundingBox}；
 * {@code AABB.expandTowards(x,y,z)} → {@code addCoord(x,y,z)}；
 * {@code AABB.inflate(1.0)} → {@code expand(1,1,1)}。</li>
 * <li><b>降级点 1：{@code isSpectator()}</b> —— 1.7.10 没有旁观者模式，恒为 {@code false}。</li>
 * <li><b>降级点 2：{@code PartEntity}</b> —— Forge 1.7.10 没有 {@code net.minecraftforge.entity.PartEntity}
 * （多段实体只有 {@code EntityDragonPart}）。这里改为识别 {@code EntityDragonPart}
 * 并取其 {@code entityDragonObj}（若它是 {@link Entity}）作为核心实体，
 * 其它多段实体实现（例如自定义 {@code IEntityMultiPart}）会退化为「按部件实体自身的 id 判断」。</li>
 * <li><b>降级点 3：队伍判定</b> —— 1.20 的 {@code Team#isAlliedTo} / {@code isAllowFriendlyFire}
 * 在 1.7.10 叫 {@link Team#isSameTeam(Team)} / {@link Team#getAllowFriendlyFire()}；
 * 且 {@code getTeam()} 定义在 {@link EntityLivingBase} 上（{@code Entity} 没有），
 * 故本文件用 {@link #getTeam(Entity)} 做类型收窄。</li>
 * <li><b>降级点 4：实体 id</b> —— 1.20 用 {@code ForgeRegistries.ENTITY_TYPES.getKey(type)}；
 * 1.7.10 的注册名形态不同（原版驼峰名 / FML 的 {@code modid.name}），由
 * {@link EntityIdHelper} 转成 1.20 风格 id、并同时给出 1.7.10 旧形态候选，配置比对两者都查。</li>
 * </ul>
 */
public class EntityUtil {

    private static final Predicate<Entity> PROJECTILE_TARGETS = input -> input != null && input.canBeCollidedWith()
        && !isSpectator(input);

    @Nullable
    public static EntityKineticBullet.EntityResult findEntityOnPath(EntityKineticBullet bulletEntity, Vec3 startVec,
        Vec3 endVec) {

        Vec3 hitVec = null;
        Entity hitEntity = null;
        boolean headshot = false;
        // 获取子弹 tick 路径上所有的实体
        List<Entity> entities = getEntitiesOnPath(bulletEntity);
        double closestDistance = Double.MAX_VALUE;
        Entity owner = bulletEntity.getOwner();
        for (Entity entity : entities) {
            // 禁止对自己造成伤害（如有需要可以增加 Config 开启对自己的伤害）
            if (!entity.equals(owner)) {
                // 射击无视自己的载具和该载具上的其他乘客
                if (owner != null && isPassengerOfSameVehicle(entity, owner)) {
                    continue;
                }
                EntityKineticBullet.EntityResult result = getHitResult(bulletEntity, owner, entity, startVec, endVec);
                if (result == null) {
                    continue;
                }
                Vec3 hitPos = result.getHitPos();
                double distanceToHit = startVec.distanceTo(hitPos);
                if (entity.isEntityAlive()) {
                    if (distanceToHit < closestDistance) {
                        hitVec = hitPos;
                        hitEntity = entity;
                        closestDistance = distanceToHit;
                        headshot = result.isHeadshot();
                    }
                }
            }
        }
        return hitEntity != null ? new EntityKineticBullet.EntityResult(hitEntity, hitVec, headshot) : null;
    }

    @NotNull
    public static List<EntityKineticBullet.EntityResult> findEntitiesOnPath(EntityKineticBullet bulletEntity,
        Vec3 startVec, Vec3 endVec) {
        List<EntityKineticBullet.EntityResult> hitEntities = new ArrayList<>();
        List<Entity> entities = getEntitiesOnPath(bulletEntity);
        Entity owner = bulletEntity.getOwner();
        for (Entity entity : entities) {
            if (!entity.equals(owner)) {
                if (owner != null && entity.equals(owner.ridingEntity)) {
                    continue;
                }
                EntityKineticBullet.EntityResult result = getHitResult(bulletEntity, owner, entity, startVec, endVec);
                if (result == null) {
                    continue;
                }
                if (entity.isEntityAlive()) {
                    hitEntities.add(result);
                }
            }
        }
        return hitEntities;
    }

    /**
     * 子弹 tick 路径附近的候选实体（等价上游 {@code level.getEntities(bullet, expandedAABB, PROJECTILE_TARGETS)}）。
     */
    private static List<Entity> getEntitiesOnPath(EntityKineticBullet bulletEntity) {
        Vec3 motion = Vec3.createVectorHelper(bulletEntity.motionX, bulletEntity.motionY, bulletEntity.motionZ);
        AxisAlignedBB searchBox = bulletEntity.boundingBox.addCoord(motion.xCoord, motion.yCoord, motion.zCoord)
            .expand(1.0, 1.0, 1.0);
        List<Entity> candidates = bulletEntity.worldObj.getEntitiesWithinAABBExcludingEntity(bulletEntity, searchBox);
        List<Entity> entities = new ArrayList<>(candidates.size());
        for (Entity entity : candidates) {
            if (PROJECTILE_TARGETS.test(entity)) {
                entities.add(entity);
            }
        }
        return entities;
    }

    @Nullable
    protected static EntityKineticBullet.EntityResult getHitResult(EntityKineticBullet bulletEntity,
        @Nullable Entity owner, Entity entity, Vec3 startVec, Vec3 endVec) {
        if (isBulletDamageIgnored(entity)) {
            return null;
        }
        // Filter before picking the nearest hit, so an ignored ally cannot hide an enemy behind it.
        if (AmmoConfig.IGNORE_TEAM_FRIENDLY_FIRE.get() && owner != null) {
            Team team = getTeam(owner);
            if (team != null && team.isSameTeam(getTeam(entity)) && !team.getAllowFriendlyFire()) {
                return null;
            }
        }
        AxisAlignedBB boundingBox = HitboxHelper.getFixedBoundingBox(entity, owner);
        // 计算射线与实体 boundingBox 的交点
        MovingObjectPosition intercept = boundingBox.calculateIntercept(startVec, endVec);
        Vec3 hitPos = intercept == null ? null : intercept.hitVec;
        // 爆头判定
        if (hitPos == null) {
            return null;
        }
        Vec3 hitBoxPos = hitPos.subtract(Vec3.createVectorHelper(entity.posX, entity.posY, entity.posZ));
        // 有配置的调用配置（1.20 风格 id 与 1.7.10 旧形态都查）
        for (String entityId : EntityIdHelper.getConfigCandidates(entity)) {
            AxisAlignedBB aabb = HeadShotAABBConfigRead.getAABB(entityId);
            if (aabb != null) {
                return new EntityKineticBullet.EntityResult(entity, hitPos, aabb.isVecInside(hitBoxPos));
            }
        }
        // 没有配置的默认给一个
        boolean headshot = false;
        float eyeHeight = entity.getEyeHeight();
        if ((eyeHeight - 0.25) < hitBoxPos.yCoord && hitBoxPos.yCoord < (eyeHeight + 0.25)) {
            headshot = true;
        }
        return new EntityKineticBullet.EntityResult(entity, hitPos, headshot);
    }

    public static boolean isBulletDamageIgnored(@Nullable Entity entity) {
        if (entity == null || !AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.get()) {
            return false;
        }
        Entity core = getPartParent(entity);
        List<String> ignoreList = AmmoConfig.BULLET_DAMAGE_IGNORE_LIST.get();
        for (String entityId : EntityIdHelper.getConfigCandidates(core)) {
            if (ignoreList.contains(entityId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 1.7.10 没有 {@code PartEntity}，多段实体只有 {@link net.minecraft.entity.boss.EntityDragonPart}。
     * 其它多段实现退化为「按部件实体自身判断」。
     */
    private static Entity getPartParent(Entity entity) {
        if (entity instanceof EntityDragonPart) {
            Object parent = ((EntityDragonPart) entity).entityDragonObj;
            if (parent instanceof Entity) {
                return (Entity) parent;
            }
        }
        return entity;
    }

    /** 1.7.10 没有旁观者模式，恒为 {@code false}。 */
    private static boolean isSpectator(Entity entity) {
        return false;
    }

    /** {@code getTeam()} 在 1.7.10 定义于 {@link EntityLivingBase}，{@code Entity} 上没有。 */
    @Nullable
    private static Team getTeam(@Nullable Entity entity) {
        return entity instanceof EntityLivingBase ? ((EntityLivingBase) entity).getTeam() : null;
    }

    /**
     * 1.7.10 没有 {@code PartEntity#getParent()}，用「同乘一辆载具」近似上游
     * {@code isPassengerOfSameVehicle}（1.7.10 只有一层 riding）。
     */
    private static boolean isPassengerOfSameVehicle(Entity entity, Entity other) {
        if (entity == null || other == null) {
            return false;
        }
        if (entity.ridingEntity == null && other.ridingEntity == null) {
            return false;
        }
        return rootVehicle(entity) == rootVehicle(other);
    }

    private static Entity rootVehicle(Entity entity) {
        return entity.ridingEntity == null ? entity : entity.ridingEntity;
    }
}
