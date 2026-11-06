package com.tacz.guns.item;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import org.apache.commons.lang3.tuple.Pair;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.GunProperty;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.util.LuaEntityAccessor;
import com.tacz.guns.api.util.LuaNbtAccessor;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.shooter.GunScriptGuard;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.mcshim.EntityPose;
import com.tacz.guns.mcshim.EntityStateHelper;
import com.tacz.guns.mcshim.Mth;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunFire;
import com.tacz.guns.network.message.event.ServerMessageGunFirePost;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.RecoilPushModifier;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ChargeData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilPushData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.sound.SoundManager;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.util.CycleTaskHelper;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.relauncher.Side;

/**
 * 搬运自上游 1.20.1 {@code item/ModernKineticGunScriptAPI.java}（Lua 枪械脚本 API）。
 *
 * <p>
 * 1.7.10 适配要点：
 * <ul>
 * <li>平台类型：{@code LivingEntity}→{@link EntityLivingBase}、{@code Player}→{@link EntityPlayer}、
 * {@code ServerPlayer}→{@link EntityPlayerMP}、{@code Level}→{@link World}、{@code Vec3}→{@link Vec3}；</li>
 * <li>1.7.10 无 {@code getCapability(ITEM_HANDLER)}：背包弹药操作改为 {@link IInventory}
 * （{@link AbstractGunItem#findAndExtractInventoryAmmo(IInventory, ItemStack, int)}，玩家用
 * {@code player.inventory}）；</li>
 * <li>1.7.10 无 {@code Pose}/{@code isSwimming}/薄冰滑行语义：
 * 趴下判定改用平台 shim 的 {@link EntityStateHelper#getPose} + {@link EntityPose#SWIMMING}
 * （{@code entity/shooter/LivingEntityCrawl} 通过 {@code setForcedPose} 写入）；</li>
 * <li>后坐力推挤：{@code setDeltaMovement} → 直接改 {@code motionX/Y/Z}，并用 1.7.10
 * {@link S12PacketEntityVelocity} 同步给客户端（替代 1.20 的 {@code ClientboundSetEntityMotionPacket}）；</li>
 * <li>事件 post：{@code MinecraftForge.EVENT_BUS.post(new GunFireEvent(shooter, item, Side.SERVER))}，
 * 1.7.10 的 post 返回 boolean 表示被取消；</li>
 * <li>其余资源/配置类（GunData/BulletData/SoundManager/CycleTaskHelper…）按上游类名与方法名调用。</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class ModernKineticGunScriptAPI {

    public static String MARKER = "ScriptAPI";

    private EntityLivingBase shooter;
    private ShooterDataHolder dataHolder;
    private ItemStack itemStack;
    private AbstractGunItem abstractGunItem;
    private CommonGunIndex gunIndex;
    private ResourceLocation gunId;
    private ResourceLocation gunDisplayId;
    private Supplier<Float> pitchSupplier;
    private Supplier<Float> yawSupplier;
    private LuaNbtAccessor nbtUtil;
    private LuaEntityAccessor entityAccessor;
    private float shotDamageMultiplier = 1f;
    private float projectileSpeedMultiplier = 1f;

    /**
     * 获取玩家的属性缓存中缓存的值。请勿获取拥有复杂数据结构的属性值，该行为是未定义的。
     *
     * @param id 属性 id，请参阅 {@link GunProperties}
     * @return 属性的值
     */
    public LuaValue getCachedProperty(String id) {
        GunProperty<?> property = GunProperties.all()
            .get(id);
        if (property == null) {
            throw new LuaError("unknown gun property: " + id);
        }
        AttachmentCacheProperty cache = IGunOperator.fromLivingEntity(shooter)
            .getCacheProperty();
        if (cache == null) {
            return LuaValue.NIL;
        }
        return CoerceJavaToLua.coerce(cache.getCache(id));
    }

    /**
     * 执行一次完整的射击逻辑，会考虑玩家的状态(是否在瞄准、是否在移动、是否在匍匐等)、配件数值影响、多弹丸散射、连发，播放开火音效。
     *
     * @param consumeAmmo 本次射击是否消耗弹药
     */
    public void shootOnce(boolean consumeAmmo) {
        GunData gunData = gunIndex.getGunData();
        BulletData bulletData = gunIndex.getBulletData();
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(shooter);
        final float shotDamageMultiplier = this.shotDamageMultiplier;
        final float projectileSpeedMultiplier = this.projectileSpeedMultiplier;

        AttachmentCacheProperty cacheProperty = gunOperator.getCacheProperty();
        if (cacheProperty == null) {
            return;
        }

        // Handle Heat Data
        float heatInaccuracy = 1f;
        if (hasHeatData()) {
            GunHeatData heatData = Objects.requireNonNull(
                gunIndex.getGunData()
                    .getHeatData());
            float heatMax = modifyProperty(GunProperties.RuntimeOnly.MAX_HEAT, Float.class, heatData.getHeatMax());
            float heatPercentage = (getHeatAmount() / heatMax);
            heatInaccuracy *= Mth.lerp(heatPercentage, heatData.getMinInaccuracy(), heatData.getMaxInaccuracy());
        }

        // 散射影响
        InaccuracyType inaccuracyType = InaccuracyType.getInaccuracyType(shooter);
        final float unmodifiedInaccuracy = cacheProperty.getCache(GunProperties.INACCURACY)
            .get(inaccuracyType) * heatInaccuracy;
        final float inaccuracy = Math
            .max(0, modifyProperty(GunProperties.INACCURACY, Float.class, unmodifiedInaccuracy));

        // 消音器影响（消音对射手来说在客户端处理，脚本改了没用，因此只允许改声音距离）
        Pair<Integer, Boolean> silence = cacheProperty.getCache(SilenceModifier.ID);
        final int soundDistance = modifyProperty(
            GunProperties.RuntimeOnly.SOUND_DISTANCE,
            Integer.class,
            silence.getLeft());
        final boolean useSilenceSound = silence.getRight();

        // 子弹飞行速度
        float speed = modifyProperty(
            GunProperties.AMMO_SPEED,
            Float.class,
            cacheProperty.getCache(GunProperties.AMMO_SPEED));
        speed *= AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.get();
        float processedSpeed = Mth.clamp((speed / 20) * projectileSpeedMultiplier, 0, Float.MAX_VALUE);
        // 弹丸数量
        int bulletAmount = modifyProperty(
            GunProperties.RuntimeOnly.BULLET_AMOUNT,
            Integer.class,
            Math.max(bulletData.getBulletAmount(), 1));

        // 连发数量
        FireMode fireMode = abstractGunItem.getFireMode(itemStack);
        int cycles = modifyProperty(
            GunProperties.RuntimeOnly.BURST_COUNT,
            Integer.class,
            fireMode == FireMode.BURST ? gunData.getBurstData()
                .getCount() : 1);
        // 连发间隔
        long period = modifyProperty(
            GunProperties.RuntimeOnly.BURST_SHOOT_INTERVAL,
            Long.class,
            fireMode == FireMode.BURST ? gunData.getBurstShootInterval() : 1);

        CycleTaskHelper.addCycleTask(() -> GunScriptGuard.run(dataHolder, shooter, gunId, "shoot", () -> {
            // 如果射击者死亡，取消射击
            // 1.7.10 对齐：上游 `shooter.isDeadOrDying()`（= isDead || health<=0）→ 1.7.10 的
            // `Entity#isDead` 要等死亡动画结束（20 tick）才置位，直接用它会让连发/霰弹在死亡后继续打完整串；
            // `!isEntityAlive()` 与上游 isDeadOrDying() 等价（同 EntityKineticBullet 击杀判定的修法）。
            if (!shooter.isEntityAlive()) {
                return false;
            }
            // 如果武器变了，取消射击
            ItemStack held = shooter.getHeldItem();
            if (held == null || !held.equals(itemStack)) {
                return false;
            }
            // 触发击发事件（1.7.10 的 EVENT_BUS.post 返回 true 表示被取消）
            boolean fire = !MinecraftForge.EVENT_BUS.post(new GunFireEvent(shooter, itemStack, Side.SERVER));
            if (fire) {
                NetworkHandler
                    .sendToTrackingEntity(new ServerMessageGunFire(shooter.getEntityId(), itemStack), shooter);
                // 削减弹药
                if (consumeAmmo) {
                    if (!this.reduceAmmoOnce()) {
                        return false;
                    }
                }
                // Handle Heat Data
                if (gunIndex.getGunData()
                    .hasHeatData()) {
                    Optional.ofNullable(gunIndex.getScript())
                        .map(script -> checkFunction(script.get("handle_shoot_heat")))
                        .ifPresentOrElse(func -> func.call(CoerceJavaToLua.coerce(this)), this::handleShootHeat);
                }
                // 获取射击方向（pitch 和 yaw）
                float pitch = pitchSupplier != null ? pitchSupplier.get() : shooter.rotationPitch;
                float yaw = yawSupplier != null ? yawSupplier.get() : shooter.rotationYaw;
                applyRecoilPush(cacheProperty, bulletAmount, pitch, yaw);
                // 生成子弹
                World world = shooter.worldObj;
                ResourceLocation ammoId = gunData.getAmmoId();
                for (int i = 0; i < bulletAmount; i++) {
                    boolean isTracer = bulletData.hasTracerAmmo()
                        && gunOperator.nextBulletIsTracer(bulletData.getTracerCountInterval());
                    EntityKineticBullet bullet = new EntityKineticBullet(
                        world,
                        shooter,
                        itemStack,
                        ammoId,
                        gunId,
                        gunDisplayId,
                        isTracer,
                        gunData,
                        bulletData);
                    bullet.applyShotgunDamageSpread(bulletAmount);
                    bullet.setShotDamageMultiplier(shotDamageMultiplier);
                    abstractGunItem.doBulletSpread(
                        dataHolder,
                        itemStack,
                        shooter,
                        bullet,
                        i,
                        processedSpeed,
                        inaccuracy,
                        pitch,
                        yaw);
                    world.spawnEntityInWorld(bullet);
                }
                // 播放枪声
                if (soundDistance > 0) {
                    String soundId = useSilenceSound ? SoundManager.SILENCE_3P_SOUND : SoundManager.SHOOT_3P_SOUND;
                    SoundManager.sendSoundToNearby(
                        shooter,
                        soundDistance,
                        gunId,
                        gunDisplayId,
                        soundId,
                        0.8f,
                        0.9f + shooter.getRNG()
                            .nextFloat() * 0.125f);
                }
                // 仅成功击发才发布后置事件并通知旁观客户端。
                MinecraftForge.EVENT_BUS.post(new GunFirePostEvent(shooter, itemStack, Side.SERVER));
                NetworkHandler
                    .sendToTrackingEntity(new ServerMessageGunFirePost(shooter.getEntityId(), itemStack), shooter);
            }
            return true;
        }, false), period, cycles);
    }

    private void applyRecoilPush(AttachmentCacheProperty cacheProperty, int bulletAmount, float pitch, float yaw) {
        GunRecoilPushData recoilPush = cacheProperty.getCache(RecoilPushModifier.ID);
        if (recoilPush == null) {
            return;
        }
        if (!(shooter instanceof EntityPlayer player) || player.isRiding()) {
            return;
        }
        // 上游 ModernKineticGunScriptAPI.java:226-230：
        // boolean crawling = !player.isSwimming() && player.getPose() == Pose.SWIMMING;
        // boolean onGround = player.onGround() || crawling;
        // boolean sneaking = player.getPose() == Pose.CROUCHING;
        // 1.7.10 无 Pose 系统：姿势由 EntityStateHelper 给出（趴下写进 forcedPose 表，真游泳由
        // isInWater+sprinting 推出）。`isSwimming()` 的等价物是 `isInWater()`（仓库既有口径，见
        // client/event/CameraSetupEvent.java:381、client/event/TickAnimationEvent.java:79）。
        // 缺了 `!isInWater()` 会把「在水里疾跑」误判成趴下（EntityStateHelper.java:87-89 在此也会给 SWIMMING）。
        boolean crawling = !player.isInWater() && EntityStateHelper.getPose(player) == EntityPose.SWIMMING;
        boolean onGround = player.onGround || crawling;
        // 上游 `player.getPose() == Pose.CROUCHING`；用同一份姿势读取，趴下/游泳时不会被当成蹲下
        // （getFinalStrength 里 crawling 优先于 sneaking，因此该项只影响「非趴下的非水面蹲姿」）
        boolean sneaking = EntityStateHelper.getPose(player) == EntityPose.CROUCHING;
        double strength = recoilPush.getFinalStrength(onGround, sneaking, crawling, bulletAmount);
        if (strength <= 0.0D) {
            return;
        }
        // 1.7.10 Vec3 无 directionFromRotation/scale：按 (pitch,yaw) 手工计算方向向量并取反
        float pitchRad = (float) Math.toRadians(pitch);
        float yawRad = (float) Math.toRadians(yaw);
        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);
        player.motionX += dirX * -strength;
        player.motionY += dirY * -strength;
        player.motionZ += dirZ * -strength;
        if (player instanceof EntityPlayerMP serverPlayer) {
            serverPlayer.playerNetServerHandler.sendPacket(new S12PacketEntityVelocity(serverPlayer));
        }
    }

    private <T> T modifyProperty(GunProperty<?> property, Class<T> type, T value) {
        return abstractGunItem.modifyProperty(dataHolder, itemStack, shooter, property, type, value);
    }

    private <T> T modifyProperty(String id, Class<T> type, T value) {
        return abstractGunItem.modifyProperty(dataHolder, itemStack, shooter, id, type, value);
    }

    /** 处理一次射击的过热变化 */
    public void handleShootHeat() {
        GunHeatData heatData = gunIndex.getGunData()
            .getHeatData();
        if (heatData == null) {
            return;
        }
        float newHeat = Math
            .min(abstractGunItem.getHeatAmount(itemStack) + heatData.getHeatPerShot(), heatData.getHeatMax());
        abstractGunItem.setHeatAmount(itemStack, newHeat);
        if (newHeat >= heatData.getHeatMax()) {
            abstractGunItem.setOverheatLocked(itemStack, true);
        }
    }

    /**
     * 让枪械内的子弹减少一发。会遵从栓动、闭膛待击和开膛待机的规律，消耗枪管内子弹或者弹匣内子弹。
     *
     * @return 是否成功减少子弹
     */
    public boolean reduceAmmoOnce() {
        Bolt boltType = TimelessAPI.getCommonGunIndex(abstractGunItem.getGunId(itemStack))
            .map(
                index -> index.getGunData()
                    .getBolt())
            .orElse(null);
        boolean hasAmmoInBarrel = abstractGunItem.hasBulletInBarrel(itemStack) && boltType != Bolt.OPEN_BOLT;
        boolean hasInventoryAmmo = abstractGunItem.hasInventoryAmmo(shooter, itemStack, isReloadingNeedConsumeAmmo());
        boolean noAmmo = useInventoryAmmo() && !hasInventoryAmmo
            || !useInventoryAmmo() && abstractGunItem.getCurrentAmmoCount(itemStack) < 1;
        if (boltType == null) {
            return false;
        }
        if (boltType == Bolt.MANUAL_ACTION) {
            if (!hasAmmoInBarrel) {
                return false;
            }
            abstractGunItem.setBulletInBarrel(itemStack, false);
            return true;
        }
        if (boltType == Bolt.CLOSED_BOLT) {
            if (!noAmmo) {
                if (useInventoryAmmo()) {
                    return consumeAmmoFromPlayer(1) == 1;
                }
                abstractGunItem.reduceCurrentAmmoCount(itemStack);
                return true;
            }
            if (!hasAmmoInBarrel) {
                return false;
            }
            abstractGunItem.setBulletInBarrel(itemStack, false);
            return true;
        }
        if (boltType == Bolt.OPEN_BOLT) {
            if (noAmmo) {
                return false;
            }
            if (useInventoryAmmo()) {
                return consumeAmmoFromPlayer(1) == 1;
            }
            abstractGunItem.reduceCurrentAmmoCount(itemStack);
            return true;
        }
        return false;
    }

    /** @return 从开始换弹到现在经历的时间，单位 ms */
    public long getReloadTime() {
        if (dataHolder.reloadTimestamp == -1) {
            return 0;
        }
        return GunTime.nowMillis() - dataHolder.reloadTimestamp;
    }

    /** @return 从开始拉栓到现在经历的时间，单位 ms */
    public long getBoltTime() {
        if (!dataHolder.isBolting) {
            return 0;
        }
        return GunTime.nowMillis() - dataHolder.boltTimestamp;
    }

    /** @return 枪械的射击间隔，单位毫秒 */
    public long getShootInterval() {
        FireMode fireMode = abstractGunItem.getFireMode(itemStack);
        if (fireMode == FireMode.BURST) {
            long coolDown = (long) (gunIndex.getGunData()
                .getBurstData()
                .getMinInterval() * 1000f);
            coolDown = coolDown - 5;
            return Math.max(coolDown, 0L);
        }
        long coolDown = gunIndex.getGunData()
            .getShootInterval(this.shooter, fireMode, itemStack);
        coolDown = coolDown - 5;
        return Math.max(coolDown, 0L);
    }

    public long getLastShootTimestamp() {
        return dataHolder.lastShootTimestamp < 0 ? -1 : dataHolder.lastShootTimestamp + dataHolder.baseTimestamp;
    }

    /** 调整射击间隔（客户端与服务端分别计算，脚本里也要重复调用）。 */
    public void adjustShootInterval(long alpha) {
        dataHolder.shootTimestamp += alpha;
    }

    /** 调整换弹时间 */
    public void adjustReloadTime(long alpha) {
        dataHolder.reloadTimestamp -= alpha;
    }

    /** 调整拉栓时间 */
    public void adjustBoltTime(long alpha) {
        dataHolder.boltTimestamp -= alpha;
    }

    public float getAimingProgress() {
        return dataHolder.aimingProgress;
    }

    public float getChargeProgress() {
        return dataHolder.chargeProgress;
    }

    public boolean hasChargeData() {
        return getChargeData() != null;
    }

    public float getMaxCharge() {
        ChargeData chargeData = getChargeData();
        return chargeData == null ? 0f : chargeData.getMaxCharge();
    }

    public float getFireThreshold() {
        ChargeData chargeData = getChargeData();
        return chargeData == null ? 0f : chargeData.getFireThreshold();
    }

    public float getChargeRatio() {
        float maxCharge = getMaxCharge();
        if (maxCharge <= 0f) {
            return 0f;
        }
        return Math.max(0f, Math.min(getChargeProgress() / maxCharge, 1f));
    }

    public void setShotDamageMultiplier(float multiplier) {
        this.shotDamageMultiplier = clampMultiplier(multiplier);
    }

    public float getShotDamageMultiplier() {
        return shotDamageMultiplier;
    }

    public void setProjectileSpeedMultiplier(float multiplier) {
        this.projectileSpeedMultiplier = clampMultiplier(multiplier);
    }

    public float getProjectileSpeedMultiplier() {
        return projectileSpeedMultiplier;
    }

    private float clampMultiplier(float multiplier) {
        return Mth.clamp(multiplier, 0f, 256f);
    }

    public int getReloadStateType() {
        return dataHolder.reloadStateType.ordinal();
    }

    public int getFireMode() {
        return abstractGunItem.getFireMode(itemStack)
            .ordinal();
    }

    public boolean isShootingNeedConsumeAmmo() {
        return IGunOperator.fromLivingEntity(shooter)
            .consumesAmmoOrNot();
    }

    public boolean isReloadingNeedConsumeAmmo() {
        return IGunOperator.fromLivingEntity(shooter)
            .needCheckAmmo()
            && !gunIndex.getGunData()
                .getReloadData()
                .isInfinite();
    }

    public int getNeededAmmoAmount() {
        int maxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(itemStack, gunIndex.getGunData());
        int currentAmmoCount = abstractGunItem.getCurrentAmmoCount(itemStack);
        return maxAmmoCount - currentAmmoCount;
    }

    public int getAmmoAmount() {
        return abstractGunItem.getCurrentAmmoCount(itemStack);
    }

    public int getMaxAmmoCount() {
        return AttachmentDataUtils.getAmmoCountWithAttachment(itemStack, gunIndex.getGunData());
    }

    public int getMagExtentLevel() {
        return AttachmentDataUtils.getMagExtendLevel(itemStack, gunIndex.getGunData());
    }

    /** 尽可能多地从玩家身上（或者虚拟备弹）消耗弹药，返回消耗数量。 */
    public int consumeAmmoFromPlayer(int neededAmount) {
        if (useInventoryAmmo() && !isReloadingNeedConsumeAmmo()) {
            return neededAmount;
        }
        if (abstractGunItem.useDummyAmmo(itemStack)) {
            return abstractGunItem.findAndExtractDummyAmmo(itemStack, neededAmount);
        }
        IInventory inventory = getEntityInventory(shooter);
        if (inventory == null) {
            return 0;
        }
        return abstractGunItem.findAndExtractInventoryAmmo(inventory, itemStack, neededAmount);
    }

    /** 检查玩家身上（或者虚拟备弹）是否有弹药可以消耗，用于循环换弹的打断。 */
    public boolean hasAmmoToConsume() {
        if (!isReloadingNeedConsumeAmmo()) {
            return true;
        }
        if (abstractGunItem.useDummyAmmo(itemStack)) {
            return abstractGunItem.getDummyAmmoAmount(itemStack) > 0;
        }
        IInventory inventory = getEntityInventory(shooter);
        if (inventory == null) {
            return false;
        }
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack checkAmmoStack = inventory.getStackInSlot(i);
            if (checkAmmoStack == null || checkAmmoStack.getItem() == null) {
                continue;
            }
            if (checkAmmoStack.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(itemStack, checkAmmoStack)) {
                return true;
            }
            if (checkAmmoStack.getItem() instanceof IAmmoBox iAmmoBox
                && iAmmoBox.isAmmoBoxOfGun(itemStack, checkAmmoStack)) {
                return true;
            }
        }
        return false;
    }

    /** 1.7.10：只有玩家有背包可读（EntityLivingBase 没有物品栏）。 */
    private static IInventory getEntityInventory(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer player) {
            return player.inventory;
        }
        return null;
    }

    public int putAmmoInMagazine(int amount) {
        if (amount < 0) {
            return 0;
        }
        int maxAmmoCount = AttachmentDataUtils.getAmmoCountWithAttachment(itemStack, gunIndex.getGunData());
        int currentAmmoCount = abstractGunItem.getCurrentAmmoCount(itemStack);
        int newAmmoCount = currentAmmoCount + amount;
        if (maxAmmoCount < newAmmoCount) {
            abstractGunItem.setCurrentAmmoCount(itemStack, maxAmmoCount);
            return newAmmoCount - maxAmmoCount;
        } else {
            abstractGunItem.setCurrentAmmoCount(itemStack, newAmmoCount);
            return 0;
        }
    }

    public int removeAmmoFromMagazine(int amount) {
        if (amount < 0) {
            return 0;
        }
        int currentAmmoCount = abstractGunItem.getCurrentAmmoCount(itemStack);
        if (currentAmmoCount < amount) {
            abstractGunItem.setCurrentAmmoCount(itemStack, 0);
            return currentAmmoCount;
        } else {
            abstractGunItem.setCurrentAmmoCount(itemStack, currentAmmoCount - amount);
            return amount;
        }
    }

    public int getAmmoCountInMagazine() {
        return abstractGunItem.getCurrentAmmoCount(itemStack);
    }

    public int unloadAmmo(int barrel, int amount) {
        if ((barrel != 0 && barrel != 1) || amount < -1) {
            return 0;
        }
        return abstractGunItem.unloadAmmo(itemStack, shooter, barrel == 1, amount);
    }

    public boolean hasAmmoInBarrel() {
        Bolt boltType = gunIndex.getGunData()
            .getBolt();
        return boltType != Bolt.OPEN_BOLT && abstractGunItem.hasBulletInBarrel(itemStack);
    }

    public void setAmmoInBarrel(boolean ammoInBarrel) {
        abstractGunItem.setBulletInBarrel(itemStack, ammoInBarrel);
    }

    public void cacheScriptData(LuaValue luaValue) {
        this.dataHolder.scriptData = luaValue;
    }

    public LuaValue getCachedScriptData() {
        return dataHolder.scriptData;
    }

    public LuaTable getScriptParams() {
        LuaTable param = gunIndex.getScriptParam();
        return param == null ? new LuaTable() : param;
    }

    public void safeAsyncTask(LuaValue value, long delayMs, long periodMs, int cycles) {
        LuaFunction func = value.checkfunction();
        CycleTaskHelper.addCycleTask(
            () -> GunScriptGuard.run(
                dataHolder,
                shooter,
                gunId,
                "safe_async_task",
                () -> func.call()
                    .checkboolean(),
                false),
            delayMs,
            periodMs,
            cycles);
    }

    public long getCurrentTimestamp() {
        return GunTime.nowMillis();
    }

    /** 获取真实系统时间（Unix 毫秒时间戳），不受游戏暂停影响。 */
    public long getRealSystemTimestamp() {
        return System.currentTimeMillis();
    }

    public String getAttachment(String type) {
        try {
            AttachmentType t = AttachmentType.valueOf(type);
            return abstractGunItem.getAttachmentId(itemStack, t)
                .toString();
        } catch (IllegalArgumentException e) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID.toString();
        }
    }

    public LuaNbtAccessor getNbt() {
        return nbtUtil;
    }

    public LuaEntityAccessor getEntityUtil() {
        if (entityAccessor == null) {
            entityAccessor = new LuaEntityAccessor(shooter);
        }
        return entityAccessor;
    }

    public void setShooter(EntityLivingBase shooter) {
        this.shooter = shooter;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
        initGunItem();
    }

    public void setPitchSupplier(Supplier<Float> pitchSupplier) {
        this.pitchSupplier = pitchSupplier;
    }

    public void setYawSupplier(Supplier<Float> yawSupplier) {
        this.yawSupplier = yawSupplier;
    }

    public EntityLivingBase getShooter() {
        return shooter;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public AbstractGunItem getAbstractGunItem() {
        return abstractGunItem;
    }

    public CommonGunIndex getGunIndex() {
        return gunIndex;
    }

    public void setHeatAmount(float amount) {
        abstractGunItem.setHeatAmount(itemStack, amount);
    }

    public float getHeatAmount() {
        return abstractGunItem.getHeatAmount(itemStack);
    }

    public boolean hasHeatData() {
        return gunIndex.getGunData()
            .getHeatData() != null;
    }

    public float getHeatMinRpm() {
        if (hasHeatData()) {
            return gunIndex.getGunData()
                .getHeatData()
                .getMinRpmMod();
        }
        return 0f;
    }

    public float getHeatMaxRpm() {
        if (hasHeatData()) {
            return gunIndex.getGunData()
                .getHeatData()
                .getMaxRpmMod();
        }
        return 0f;
    }

    public float getHeatMinInaccuracy() {
        if (hasHeatData()) {
            return gunIndex.getGunData()
                .getHeatData()
                .getMinInaccuracy();
        }
        return 0f;
    }

    public float getHeatMaxInaccuracy() {
        if (hasHeatData()) {
            return gunIndex.getGunData()
                .getHeatData()
                .getMaxInaccuracy();
        }
        return 0f;
    }

    public float getHeatMax() {
        if (hasHeatData()) {
            return gunIndex.getGunData()
                .getHeatData()
                .getHeatMax();
        }
        return 0f;
    }

    public float getHeatPerShot() {
        if (hasHeatData()) {
            return gunIndex.getGunData()
                .getHeatData()
                .getHeatPerShot();
        }
        return 0f;
    }

    public boolean isOverheatLocked() {
        return abstractGunItem.isOverheatLocked(itemStack);
    }

    public void setOverheatLocked(boolean locked) {
        abstractGunItem.setOverheatLocked(itemStack, locked);
    }

    public long getOverheatTime() {
        if (hasHeatData()) {
            return gunIndex.getGunData()
                .getHeatData()
                .getOverHeatTime();
        }
        return 0;
    }

    public long getCoolingDelay() {
        if (hasHeatData()) {
            return gunIndex.getGunData()
                .getHeatData()
                .getCoolingDelay();
        }
        return 0;
    }

    public float calcHeatReduction(long heatTimestamp) {
        GunHeatData heatData = gunIndex.getGunData()
            .getHeatData();
        if (heatData != null) {
            return ((float) (GunTime.nowMillis() - heatTimestamp) / 10000f) * heatData.getCoolingMultiplier();
        }
        return 0f;
    }

    public int getBoltByInt() {
        Bolt bolt = gunIndex.getGunData()
            .getBolt();
        if (bolt == Bolt.MANUAL_ACTION) {
            return 1;
        }
        if (bolt == Bolt.CLOSED_BOLT) {
            return 2;
        }
        if (bolt == Bolt.OPEN_BOLT) {
            return 3;
        }
        return 0;
    }

    public Bolt getBolt() {
        return gunIndex.getGunData()
            .getBolt();
    }

    private ChargeData getChargeData() {
        FireMode fireMode = abstractGunItem.getFireMode(itemStack);
        return gunIndex.getGunData()
            .getChargeData(fireMode);
    }

    public void setDataHolder(ShooterDataHolder dataHolder) {
        this.dataHolder = dataHolder;
    }

    public boolean useInventoryAmmo() {
        return abstractGunItem.useInventoryAmmo(itemStack);
    }

    ShooterDataHolder getDataHolder() {
        return this.dataHolder;
    }

    private void initGunItem() {
        if (itemStack == null || !(itemStack.getItem() instanceof AbstractGunItem gunItem)) {
            gunIndex = null;
            abstractGunItem = null;
            return;
        }
        gunId = gunItem.getGunId(itemStack);
        gunDisplayId = gunItem.getGunDisplayId(itemStack);
        Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(gunId);
        gunIndex = gunIndexOptional.orElse(null);
        abstractGunItem = gunItem;
        if (itemStack.hasTagCompound()) {
            nbtUtil = new LuaNbtAccessor(itemStack.getTagCompound());
        }
    }

    private LuaFunction checkFunction(LuaValue luaValue) {
        if (luaValue.isfunction()) {
            return (LuaFunction) luaValue;
        } else if (luaValue.isnil()) {
            return null;
        } else {
            throw new LuaError("bad argument: function or nil expected, got " + luaValue.typename());
        }
    }
}
