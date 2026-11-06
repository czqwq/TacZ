package com.tacz.guns.client.resource.pojo.display.gun;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.ClientTickHelper;
import com.tacz.guns.client.event.CameraSetupEvent;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.HitboxHelper;
import com.tacz.guns.util.block.BlockRayTrace;
import com.tacz.guns.util.math.Easing;
import com.tacz.guns.util.math.MathUtil;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class RangefinderManager {

    private static final String MISS_TEXT = "---";
    private static final double ENTITY_XZ_EXPAND = 0.25;
    private static final double ENTITY_Y_EXPAND = 8.0;
    private static final long MILLIS_PER_TICK = 50L;
    // TODO(port): 上游 RangefinderManager.java:45 用 Entity#isPickable()/isSpectator()/isAlive()（1.20）。
    // 1.7.10 的 Entity 没有这三个方法：isAlive() → !isDead，isPickable() → canBeCollidedWith()；
    // 观察者模式是 1.8 才有的，1.7.10 不存在（EntityPlayer 也没有 isSpectator()），该条判断直接省略。
    private static final Predicate<Entity> RANGEFINDER_TARGETS = entity -> entity != null && !entity.isDead
        && entity.canBeCollidedWith();

    private static Rangefinder currentConfig = null;
    private static long lastUpdateTimestamp = 0L;
    private static int lastRefreshTick = 2;
    private static String text = MISS_TEXT;
    private static boolean hitEntity = false;
    private static double hitDistance = 0.0;
    private static float impactProjectionY = 0.0f;
    private static int impactPredictionColor = 0xFFFFFFFF;
    private static float impactPredictionScale = 1.0f;
    private static boolean showImpactPrediction = false;

    private RangefinderManager() {}

    public static void resetTimestamps() {
        currentConfig = null;
        lastUpdateTimestamp = 0L;
        lastRefreshTick = 2;
        clearResult();
    }

    public static void setCurrentConfig(@Nullable Rangefinder config) {
        currentConfig = config;
    }

    public static void clearCurrentConfig() {
        currentConfig = null;
    }

    public static String getText(ItemStack stack) {
        if (currentConfig == null) {
            return "";
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || stack == null || stack.stackSize <= 0) {
            lastUpdateTimestamp = 0L;
            clearResult();
            return MISS_TEXT;
        }
        int refreshTick = currentConfig.getRefreshTick();
        long now = GunTime.nowMillis();
        if (lastUpdateTimestamp != 0L && GunTime.isWithin(lastUpdateTimestamp, refreshTick * MILLIS_PER_TICK)
            && refreshTick == lastRefreshTick) {
            return text;
        }
        lastUpdateTimestamp = now;
        lastRefreshTick = refreshTick;
        updateResult(player, stack, currentConfig);
        return text;
    }

    public static boolean shouldRenderImpactPrediction() {
        if (!showImpactPrediction) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !GunTime.isWithin(lastUpdateTimestamp, (lastRefreshTick + 1L) * MILLIS_PER_TICK)) {
            showImpactPrediction = false;
            return false;
        }
        if (!(player instanceof IClientPlayerGunOperator operator)
            || operator.getClientAimingProgress(ClientTickHelper.getPartialTick()) < 0.9f) {
            showImpactPrediction = false;
            return false;
        }
        return true;
    }

    public static float getImpactProjectionY() {
        return impactProjectionY;
    }

    public static int getImpactPredictionColor() {
        return impactPredictionColor;
    }

    public static float getImpactPredictionScale() {
        return impactPredictionScale;
    }

    private static void updateResult(EntityPlayerSP player, ItemStack stack, Rangefinder config) {
        impactPredictionColor = config.getImpactPredictionColor();
        impactPredictionScale = config.getImpactPredictionScale();
        if (!(stack.getItem() instanceof IGun iGun) || !(player instanceof IClientPlayerGunOperator operator)) {
            clearResult();
            return;
        }
        if (operator.getClientAimingProgress(ClientTickHelper.getPartialTick()) < 0.9f) {
            clearResult();
            return;
        }

        float maxDistance = config.getMaxDistance();
        // 上游 Entity#getEyePosition(partialTicks)：1.7.10 EntityPlayer#getPosition(float) 返回插值后的眼睛位置
        // （posY + eyeHeight - defaultEyeHeight），语义等价。
        Vec3 start = player.getPosition(ClientTickHelper.getPartialTick());
        // 上游 Entity#getViewVector(partialTicks)：1.7.10 对应 EntityLivingBase#getLook(partialTicks)。
        Vec3 look = player.getLook(ClientTickHelper.getPartialTick());
        Vec3 end = start.addVector(look.xCoord * maxDistance, look.yCoord * maxDistance, look.zCoord * maxDistance);

        // 上游 BlockRayTrace.rayTraceBlocks(level, new ClipContext(COLLIDER, Fluid.NONE))；
        // 1.7.10 版 util BlockRayTrace 同算法（含可穿透方块 / 子弹忽略方块过滤）。
        MovingObjectPosition blockResult = BlockRayTrace.rayTraceBlocks(player.worldObj, start, end);
        double blockDistance = blockResult == null ? Double.MAX_VALUE : start.distanceTo(blockResult.hitVec);
        EntityHit entityHit = findEntityHit(player, start, end, blockDistance).orElse(null);

        if (entityHit != null) {
            double distance = start.distanceTo(entityHit.hitPos());
            setDistanceText(distance, true, maxDistance);
            updateImpactPrediction(player, stack, iGun, config, distance);
        } else if (blockDistance != Double.MAX_VALUE) {
            setDistanceText(blockDistance, false, maxDistance);
            showImpactPrediction = false;
        } else {
            clearResult();
        }
    }

    private static Optional<EntityHit> findEntityHit(EntityPlayerSP player, Vec3 start, Vec3 end, double maxDistance) {
        // TODO(port): 上游用 new AABB(start, end).inflate(...)（1.20）。1.7.10 的 AxisAlignedBB 没有「两点构造」，
        // 这里按 min/max 手工构造同样的盒子。
        AxisAlignedBB scanBox = AxisAlignedBB.getBoundingBox(
            Math.min(start.xCoord, end.xCoord) - ENTITY_XZ_EXPAND,
            Math.min(start.yCoord, end.yCoord) - ENTITY_Y_EXPAND,
            Math.min(start.zCoord, end.zCoord) - ENTITY_XZ_EXPAND,
            Math.max(start.xCoord, end.xCoord) + ENTITY_XZ_EXPAND,
            Math.max(start.yCoord, end.yCoord) + ENTITY_Y_EXPAND,
            Math.max(start.zCoord, end.zCoord) + ENTITY_XZ_EXPAND);
        return player.worldObj.getEntitiesWithinAABB(Entity.class, scanBox)
            .stream()
            // 上游把该谓词作为 World#getEntities 的选择器传入；1.7.10 这里在流上过滤，语义一致
            .filter(RANGEFINDER_TARGETS)
            .filter(entity -> !entity.equals(player))
            // TODO(port): 上游用 Entity#isPassengerOfSameVehicle(other)（1.20）→ 1.7.10 判断 ridingEntity 相同。
            .filter(entity -> entity.ridingEntity == null || entity.ridingEntity != player.ridingEntity)
            // 上游用 HitboxHelper.getFixedBoundingBox(entity, player)（命中箱延迟补偿 + 蹲伏/速度修正）。
            .map(entity -> clipEntity(entity, player, start, end))
            .filter(hit -> hit != null && start.distanceTo(hit.hitPos()) <= maxDistance)
            .min(Comparator.comparingDouble(hit -> start.distanceTo(hit.hitPos())));
    }

    /** 用（上游 HitboxHelper 修正后的）实体命中箱与射线求交。 */
    @Nullable
    private static EntityHit clipEntity(Entity entity, EntityPlayerSP player, Vec3 start, Vec3 end) {
        AxisAlignedBB box = HitboxHelper.getFixedBoundingBox(entity, player)
            .expand(ENTITY_XZ_EXPAND, ENTITY_Y_EXPAND, ENTITY_XZ_EXPAND);
        MovingObjectPosition intercept = box.calculateIntercept(start, end);
        return intercept == null ? null : new EntityHit(intercept.hitVec);
    }

    private static void setDistanceText(double distance, boolean entityHit, float maxDistance) {
        hitDistance = distance;
        hitEntity = entityHit;
        int roundedDistance = Mth.floor(distance + 0.5);
        // 与距离使用相同的取整规则，按测距上限的位数补零。
        int digits = Integer.toString(Mth.floor(maxDistance + 0.5))
            .length();
        String distanceText = Integer.toString(roundedDistance);
        text = "0".repeat(Math.max(0, digits - distanceText.length())) + distanceText;
    }

    private static void clearResult() {
        text = MISS_TEXT;
        hitEntity = false;
        hitDistance = 0.0;
        impactProjectionY = 0.0f;
        showImpactPrediction = false;
    }

    private static void updateImpactPrediction(EntityPlayerSP player, ItemStack stack, IGun iGun, Rangefinder config,
        double distance) {
        showImpactPrediction = false;
        if (!hitEntity || !config.isShowImpactPrediction() || MISS_TEXT.equals(text) || distance <= 0.0) {
            return;
        }
        ResourceLocation gunId = iGun.getGunId(stack);
        ClientGunIndex gunIndex = TimelessAPI.getClientGunIndex(gunId)
            .orElse(null);
        if (gunIndex == null) {
            return;
        }
        GunData gunData = gunIndex.getGunData();
        if (gunData == null || gunData.getBulletData() == null) {
            return;
        }
        BulletData bulletData = gunData.getBulletData();
        float speed = getAmmoSpeed(player, stack, bulletData);
        if (speed <= 0.0f) {
            return;
        }
        Vec3 look = player.getLook(ClientTickHelper.getPartialTick());
        double drop = calculateDrop(
            distance,
            look.yCoord,
            speed / 20.0f,
            bulletData.getGravity(),
            bulletData.getFriction());
        if (!Double.isFinite(drop)) {
            return;
        }

        float fov = getCurrentFov(player, stack, iGun);
        double angle = Math.atan2(-drop, Math.max(distance, 0.001));
        impactProjectionY = (float) (Math.tan(angle) / Math.tan(Math.toRadians(fov) / 2.0));
        showImpactPrediction = Float.isFinite(impactProjectionY);
    }

    private static float getAmmoSpeed(EntityPlayerSP player, ItemStack stack, BulletData bulletData) {
        float speed = bulletData.getSpeed();
        AttachmentCacheProperty cacheProperty = IGunOperator.fromLivingEntity(player)
            .getCacheProperty();
        if (cacheProperty != null) {
            try {
                speed = cacheProperty.getCache(GunProperties.AMMO_SPEED);
            } catch (RuntimeException ignored) {
                speed = bulletData.getSpeed();
            }
        }
        return Math.max(
            0.0f,
            speed * AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.get()
                .floatValue());
    }

    private static float getCurrentFov(EntityPlayerSP player, ItemStack stack, IGun iGun) {
        // TODO(port): 上游用 Minecraft#options.fov().get()（1.20）→ 1.7.10 Minecraft#gameSettings.fovSetting。
        float configuredFov = Minecraft.getMinecraft().gameSettings.fovSetting;
        float aimingProgress = IClientPlayerGunOperator.fromLocalPlayer(player)
            .getClientAimingProgress(ClientTickHelper.getPartialTick());
        AdsFov.Channel curve = TimelessAPI.getGunDisplay(stack)
            .map(GunDisplayInstance::getAdsFov)
            .map(AdsFov::getWorld)
            .orElse(null);
        float fovProgress = curve == null ? (float) Easing.easeInExpo(aimingProgress) : curve.evaluate(aimingProgress);
        float zoom = iGun.getAimingZoom(stack);
        float targetFov = (float) MathUtil.magnificationToFov(1 + (zoom - 1) * fovProgress, configuredFov);
        if (curve != null && curve.isSmooth()) {
            float actualFov = CameraSetupEvent.WORLD_FOV_DYNAMICS.get();
            if (Float.isFinite(actualFov) && actualFov > 0) {
                return actualFov;
            }
        }
        return targetFov;
    }

    private static double calculateDrop(double distance, double lookY, float speedPerTick, float gravity,
        float friction) {
        double traveled = 0.0;
        double vertical = 0.0;
        double speed = speedPerTick;
        double verticalVelocity = lookY * speedPerTick;
        int maxTicks = 20 * 30;
        for (int i = 0; i < maxTicks && traveled < distance && speed > 1.0E-4; i++) {
            double step = Math.min(speed, distance - traveled);
            double ratio = step / speed;
            vertical += verticalVelocity * ratio;
            traveled += step;
            speed *= 1.0f - friction;
            verticalVelocity = verticalVelocity * (1.0f - friction) - gravity;
        }
        return vertical - lookY * distance;
    }

    private record EntityHit(Vec3 hitPos) {}
}
