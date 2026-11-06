package com.tacz.guns.mixin.common;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.KnockBackModifier;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.entity.shooter.GunScriptGuard;
import com.tacz.guns.entity.shooter.LivingEntityAim;
import com.tacz.guns.entity.shooter.LivingEntityAmmoCheck;
import com.tacz.guns.entity.shooter.LivingEntityBolt;
import com.tacz.guns.entity.shooter.LivingEntityCrawl;
import com.tacz.guns.entity.shooter.LivingEntityDrawGun;
import com.tacz.guns.entity.shooter.LivingEntityFireSelect;
import com.tacz.guns.entity.shooter.LivingEntityHeat;
import com.tacz.guns.entity.shooter.LivingEntityMelee;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import com.tacz.guns.entity.shooter.LivingEntitySpeedModifier;
import com.tacz.guns.entity.shooter.LivingEntitySprint;
import com.tacz.guns.entity.shooter.LivingEntityUnload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.time.GunTime;

/**
 * 搬运自上游 1.20.1 {@code mixin/common/LivingEntityMixin.java}。
 *
 * <p>
 * <b>服务端枪械状态机唯一驱动点（L1 必需）</b>。1.7.10 适配：
 * <ul>
 * <li>目标 {@code LivingEntity} → {@code EntityLivingBase}；UniMixins 承载 Mixin，
 * {@code IGunOperator/KnockBackModifier} 仍由 Mixin 让实体实现（{@code fromLivingEntity} 强转语义不变）；</li>
 * <li>每 tick 注入点 {@code LivingEntity#tick}(TAIL) → {@code EntityLivingBase#onLivingUpdate}(TAIL)，
 * 双端调用，仍用 {@code worldObj.isRemote} 判断仅服务端；</li>
 * <li>{@code level().isClientSide()} → {@code worldObj.isRemote}；{@code getMainHandItem()} →
 * {@code getHeldItem()}；</li>
 * <li>状态存 Mixin {@code @Unique} 字段（与上游字段方案同构），同步仍走 {@code entity/sync/**}。</li>
 * </ul>
 */
@SuppressWarnings("all")
@Mixin(EntityLivingBase.class)
public abstract class LivingEntityMixin implements IGunOperator, KnockBackModifier {

    @Unique
    private final EntityLivingBase tacz$shooter = (EntityLivingBase) (Object) this;
    @Unique
    private final ShooterDataHolder tacz$data = new ShooterDataHolder();
    @Unique
    private final LivingEntityDrawGun tacz$draw = new LivingEntityDrawGun(tacz$shooter, tacz$data);
    @Unique
    private final LivingEntityAim tacz$aim = new LivingEntityAim(tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntityCrawl tacz$crawl = new LivingEntityCrawl(tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntityAmmoCheck tacz$ammoCheck = new LivingEntityAmmoCheck(tacz$shooter);
    @Unique
    private final LivingEntityFireSelect tacz$fireSelect = new LivingEntityFireSelect(tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntityMelee tacz$melee = new LivingEntityMelee(tacz$shooter, this.tacz$data, this.tacz$draw);
    @Unique
    private final LivingEntityShoot tacz$shoot = new LivingEntityShoot(tacz$shooter, this.tacz$data, this.tacz$draw);
    @Unique
    private final LivingEntityBolt tacz$bolt = new LivingEntityBolt(
        this.tacz$data,
        this.tacz$shooter,
        this.tacz$draw,
        this.tacz$shoot);
    @Unique
    private final LivingEntityReload tacz$reload = new LivingEntityReload(
        tacz$shooter,
        this.tacz$data,
        this.tacz$draw,
        this.tacz$shoot);
    @Unique
    private final LivingEntityUnload tacz$unload = new LivingEntityUnload(
        tacz$shooter,
        this.tacz$data,
        this.tacz$draw,
        this.tacz$shoot,
        this.tacz$melee);
    @Unique
    private final LivingEntitySpeedModifier tacz$speed = new LivingEntitySpeedModifier(tacz$shooter, tacz$data);
    @Unique
    private final LivingEntitySprint tacz$sprint = new LivingEntitySprint(tacz$shooter, this.tacz$data);
    @Unique
    private final LivingEntityHeat tacz$heat = new LivingEntityHeat(tacz$shooter, this.tacz$data);

    /** {@link #tacz$updateCrawlDimensions()} 的幂等哨兵：当前体型是否已按趴下状态改过。 */
    @Unique
    private boolean tacz$crawlDimensionsApplied;

    @Override
    public long getSynShootCoolDown() {
        return ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.getValue(tacz$shooter);
    }

    @Override
    public long getSynMeleeCoolDown() {
        return ModSyncedEntityData.MELEE_COOL_DOWN_KEY.getValue(tacz$shooter);
    }

    @Override
    public long getSynDrawCoolDown() {
        return ModSyncedEntityData.DRAW_COOL_DOWN_KEY.getValue(tacz$shooter);
    }

    @Override
    public boolean getSynIsBolting() {
        return ModSyncedEntityData.IS_BOLTING_KEY.getValue(tacz$shooter);
    }

    /**
     * 读当前同步的换弹状态。
     *
     * <p>
     * <b>1.7.10 必须在这里归一化 null（上游由 capability 保证非 null）</b>：上游
     * {@code SyncedEntityData#get} 走 Forge capability，{@code ReloadState} 由构造器保证
     * {@code stateType} 非 null（{@code ReloadState.java:18-21}）；本移植改成
     * 「静态 {@code DataHolder} + 网络反序列化 + player clone 搬运」之后，值在链路上任何一处被写成
     * null，下面 3 个<b>裸解引用</b>的消费者就会直接 NPE：
     * <ul>
     * <li>{@code entity/shooter/LivingEntitySprint.java:32-33}
     * （{@code getSynReloadState().getStateType()}，每 tick 由本类 {@code onTickServerSide} 触发）；</li>
     * <li>{@code entity/shooter/LivingEntityAim.java:139-140}（同样每 tick，{@code tickSprint}）；</li>
     * <li>{@code client/gameplay/LocalPlayerSprint.java:42}（客户端每 tick）。</li>
     * </ul>
     *
     * <p>
     * 这与本次实机崩溃（{@code LivingEntityAim.tickAimingProgress} 的 {@code Supplier.get()} 返回
     * null → {@code ItemStack.getItem()} NPE）是<b>同一类问题</b>：1.20 的非 null 哨兵在 1.7.10 变成
     * null，而读取点仍按「恒非 null」写。返回 {@code new ReloadState()}（构造器已把
     * {@code stateType=NOT_RELOADING}、{@code countDown=-1}）在语义上完全等价于上游默认值，
     * 只在数据异常时才有一次分配开销。
     */
    @Override
    public ReloadState getSynReloadState() {
        ReloadState state = ModSyncedEntityData.RELOAD_STATE_KEY.getValue(tacz$shooter);
        return state != null ? state : new ReloadState();
    }

    @Override
    public float getSynAimingProgress() {
        return ModSyncedEntityData.AIMING_PROGRESS_KEY.getValue(tacz$shooter);
    }

    @Override
    public float getSynSprintTime() {
        return ModSyncedEntityData.SPRINT_TIME_KEY.getValue(tacz$shooter);
    }

    @Override
    public boolean getSynIsAiming() {
        return ModSyncedEntityData.IS_AIMING_KEY.getValue(tacz$shooter);
    }

    @Override
    public void initialData() {
        this.tacz$data.initialData();
        this.tacz$data.setCurrentGunItem(() -> tacz$shooter.getHeldItem());
        // 1.7.10 差异：空手时 getHeldItem() 返回 null（1.20 的 getMainHandItem() 恒返回 EMPTY），
        // 而 resource 层 AttachmentPropertyManager#postChangeEvent 直接 gunItem.getItem() 未判空，
        // 因此这里必须先判空，否则任何空手实体在 tick 重置时都会 NPE。
        ItemStack held = tacz$shooter.getHeldItem();
        if (held != null) {
            AttachmentPropertyManager.postChangeEvent(tacz$shooter, held);
        }
    }

    @Override
    public void draw(Supplier<ItemStack> gunItemSupplier) {
        this.tacz$draw.draw(gunItemSupplier);
    }

    @Override
    public void bolt() {
        tacz$runGunAction("start_bolt", () -> this.tacz$bolt.bolt());
    }

    @Override
    public void reload() {
        tacz$runGunAction("start_reload", () -> this.tacz$reload.reload());
    }

    @Override
    public void unloadAmmo() {
        tacz$runGunAction("unload_ammo", () -> this.tacz$unload.unloadAmmo());
    }

    @Override
    public void cancelReload() {
        tacz$runGunAction("interrupt_reload", () -> this.tacz$reload.cancelReload());
    }

    @Override
    public void melee() {
        tacz$runGunAction("melee", () -> this.tacz$melee.melee());
    }

    @Override
    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw) {
        return this.shoot(pitch, yaw, GunTime.nowMillis() - tacz$data.baseTimestamp);
    }

    @Override
    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp) {
        return tacz$runGunAction("shoot", () -> tacz$shoot.shoot(pitch, yaw, timestamp), ShootResult.UNKNOWN_FAIL);
    }

    @Override
    public ShootResult shoot(Supplier<Float> pitch, Supplier<Float> yaw, long timestamp, float chargeProgress) {
        return tacz$runGunAction(
            "shoot",
            () -> tacz$shoot.shoot(pitch, yaw, timestamp, chargeProgress),
            ShootResult.UNKNOWN_FAIL);
    }

    @Override
    public boolean needCheckAmmo() {
        return this.tacz$ammoCheck.needCheckAmmo();
    }

    @Override
    public boolean consumesAmmoOrNot() {
        return this.tacz$ammoCheck.consumesAmmoOrNot();
    }

    @Override
    public boolean getProcessedSprintStatus(boolean sprint) {
        return this.tacz$sprint.getProcessedSprintStatus(sprint);
    }

    @Override
    public void aim(boolean isAim) {
        this.tacz$aim.aim(isAim);
    }

    @Override
    public void crawl(boolean isCrawl) {
        this.tacz$crawl.crawl(isCrawl);
    }

    @Override
    public void updateCacheProperty(AttachmentCacheProperty cacheProperty) {
        this.tacz$data.cacheProperty = cacheProperty;
    }

    @Override
    @Nullable
    public AttachmentCacheProperty getCacheProperty() {
        return this.tacz$data.cacheProperty;
    }

    @Override
    public ShooterDataHolder getDataHolder() {
        return this.tacz$data;
    }

    @Override
    public boolean nextBulletIsTracer(int tracerCountInterval) {
        this.tacz$data.shootCount++;
        if (tracerCountInterval == -1) {
            return false;
        }
        return tacz$data.shootCount % (tracerCountInterval + 1) == 0;
    }

    @Override
    public void fireSelect() {
        tacz$runGunAction("fire_select", () -> this.tacz$fireSelect.fireSelect());
    }

    @Override
    public void zoom() {
        this.tacz$aim.zoom();
    }

    @Unique
    private void tacz$runGunAction(String operation, Runnable action) {
        GunScriptGuard.run(tacz$data, tacz$shooter, tacz$data.currentGunId, operation, action);
    }

    @Unique
    private <T> T tacz$runGunAction(String operation, Supplier<T> action, T fallback) {
        return GunScriptGuard.run(tacz$data, tacz$shooter, tacz$data.currentGunId, operation, action, fallback);
    }

    /** 1.7.10 对应上游 {@code LivingEntity#tick} TAIL 注入。 */
    @Inject(method = "onLivingUpdate", at = @At(value = "RETURN"))
    private void onTickServerSide(CallbackInfo ci) {
        if (!tacz$shooter.worldObj.isRemote) {
            ReloadState reloadState = tacz$runGunAction(
                "tick_reload",
                () -> this.tacz$reload.tickReloadState(),
                new ReloadState());
            // 1.7.10 归一化（与 tickAimingProgress 崩溃同类）：上游 ReloadState 恒非 null，
            // 这里不能假设 action 的返回值非 null —— GunScriptGuard 在非异常路径上原样返回
            // action.get()。若把 null 写进 SyncedEntityData，会在下一个 tick 的
            // DataHolder#set（DataHolder.java:27 entry.getValue().equals(value)）→
            // ReloadState#equals（ReloadState.java:56 stateType.equals）以及
            // ModSerializers.RELOAD_STATE#write（ModSerializers.java:24 getStateType().ordinal()）
            // 三处 NPE，栈同样落在本方法触发的每 tick 服务端路径里。
            if (reloadState == null) {
                reloadState = new ReloadState();
            }
            this.tacz$aim.tickAimingProgress();
            this.tacz$aim.tickSprint();
            this.tacz$crawl.tickCrawling();
            tacz$runGunAction("tick_bolt", () -> this.tacz$bolt.tickBolt());
            tacz$runGunAction("tick_melee", () -> this.tacz$melee.scheduleTickMelee());
            this.tacz$speed.updateSpeedModifier();
            tacz$runGunAction("tick_heat", () -> this.tacz$heat.tickHeat());
            if (this.tacz$data.reloadStateType == null || !this.tacz$data.reloadStateType.isReloading()) {
                reloadState = new ReloadState();
            }
            tacz$shooter.setSprinting(getProcessedSprintStatus(tacz$shooter.isSprinting()));
            ModSyncedEntityData.SHOOT_COOL_DOWN_KEY.setValue(
                tacz$shooter,
                tacz$runGunAction("shoot_cooldown", () -> this.tacz$shoot.getShootCoolDown(), 0L));
            ModSyncedEntityData.MELEE_COOL_DOWN_KEY.setValue(tacz$shooter, this.tacz$melee.getMeleeCoolDown());
            ModSyncedEntityData.DRAW_COOL_DOWN_KEY.setValue(tacz$shooter, this.tacz$draw.getDrawCoolDown());
            ModSyncedEntityData.IS_BOLTING_KEY.setValue(tacz$shooter, this.tacz$data.isBolting);
            ModSyncedEntityData.RELOAD_STATE_KEY.setValue(tacz$shooter, reloadState);
            ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(tacz$shooter, this.tacz$data.aimingProgress);
            ModSyncedEntityData.IS_AIMING_KEY.setValue(tacz$shooter, this.tacz$data.isAiming);
            ModSyncedEntityData.SPRINT_TIME_KEY.setValue(tacz$shooter, this.tacz$data.sprintTimeS);
            tacz$updateCrawlDimensions();
        }
    }

    /**
     * 趴下的**服务端体型/眼高**（1.20 {@code Pose.SWIMMING} 的物理等价物）。
     *
     * <p>
     * 上游：{@code Player#setForcedPose(Pose.SWIMMING)}（Forge 补丁，{@code Player.java.patch} 的
     * {@code forcedPose}）→ {@code updatePlayerPose → setPose → refreshDimensions} ⇒
     * 包围盒 0.6×0.6、眼高 0.4（{@code Player.POSES} / {@code getStandingEyeHeight}）。
     *
     * <p>
     * 1.7.10 落点：{@code Entity.width/height/boundingBox} 是公有字段，且玩家包围盒锚点为
     * {@code [posY-yOffset+ySize, +height]}（服务端玩家 {@code yOffset=0} ⇒ 底边=posY，只缩不抬高）。
     * 眼高用 {@code EntityPlayer.eyeHeight}（{@code getEyeHeight()} 的返回值 = {@code posY + eyeHeight}，
     * 服务端 posY 即脚部）。只在状态变化时写一次，退出时还原站立值。
     *
     * <p>
     * <b>只处理玩家</b>：crawl 的触发源是客户端按键（{@code LocalPlayerCrawl}），非玩家实体不会进入该状态；
     * 且 1.20 中非玩家实体的 {@code setPose} 不改体型/眼高（{@code Entity#getDimensions} 忽略 pose），
     * 故此处不猜测非玩家体型、也不改它们。
     */
    @Unique
    private void tacz$updateCrawlDimensions() {
        if (!(tacz$shooter instanceof EntityPlayer player)) {
            return;
        }
        boolean crawling = this.tacz$data.isCrawling;
        if (crawling == this.tacz$crawlDimensionsApplied) {
            return;
        }
        this.tacz$crawlDimensionsApplied = crawling;
        if (crawling) {
            player.height = 0.6F; // 1.20 Player.POSES: SWIMMING = 0.6 × 0.6
            player.eyeHeight = 0.4F; // 1.20 getStandingEyeHeight(SWIMMING) = 0.4
        } else {
            player.height = 1.8F; // 1.20 Player.STANDING_DIMENSIONS = 0.6 × 1.8
            player.eyeHeight = 1.62F; // 1.20 getStandingEyeHeight(STANDING) = 1.62
        }
        player.boundingBox.maxY = player.boundingBox.minY + (double) player.height;
    }

    @Override
    public void resetKnockBackStrength() {
        this.tacz$data.knockbackStrength = -1;
    }

    @Override
    public double getKnockBackStrength() {
        return this.tacz$data.knockbackStrength;
    }

    @Override
    public void setKnockBackStrength(double strength) {
        this.tacz$data.knockbackStrength = strength;
    }
}
