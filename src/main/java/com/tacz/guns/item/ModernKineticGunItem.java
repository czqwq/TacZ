package com.tacz.guns.item;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.MarkerManager;
import org.joml.Vector2d;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;

import com.google.common.base.Suppliers;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.command.sub.DebugCommand;
import com.tacz.guns.debug.GunMeleeDebug;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.attachment.EffectData;
import com.tacz.guns.resource.pojo.data.attachment.MeleeData;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.GunDefaultMeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunHeatData;
import com.tacz.guns.resource.pojo.data.gun.GunMeleeData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import com.tacz.guns.util.time.GunTime;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 搬运自上游 1.20.1 {@code item/ModernKineticGunItem.java}（TACZ 1.2.0）。
 *
 * <p>
 * 1.7.10 适配要点：
 * <ul>
 * <li>{@code Item} API：{@code inventoryTick}→{@link Item#onUpdate}；{@code Properties().stacksTo(1)}→构造器里
 * {@code setMaxStackSize(1)}；</li>
 * <li>平台类型：{@code Level}→{@link World}、{@code LivingEntity}→{@link EntityLivingBase}、
 * {@code Player}→{@link EntityPlayer}、{@code Vec3}→{@link Vec3}（1.7.10 无 scale/xRot 链式调用，
 * 改用 {@code createVectorHelper + rotateAroundX/Y}）、{@code AABB}→{@link net.minecraft.util.AxisAlignedBB}；</li>
 * <li>近战：{@code Attributes.ATTACK_DAMAGE}→{@link SharedMonsterAttributes#attackDamage}；
 * {@code target.knockback(...)}（1.20）→ 1.7.10 的 {@code knockBack} 是 protected，按 1.20 语义手写速度施加；
 * {@code user.doEnchantDamageEffects} 1.7.10 无（由 {@code attackEntityFrom} 内部处理），记录差异；</li>
 * <li>药水效果：{@code MobEffect/MobEffectInstance}→{@link Potion}/{@link PotionEffect}，
 * JSON 里的 {@code effectId}（ResourceLocation）→ 1.7.10 的 Potion 实例需要名称映射
 * （见 {@link #getPotionFromId}，只覆盖原版效果，自定义效果无法表达）；</li>
 * <li>粒子：1.7.10 无 {@code sendParticles}/{@code ParticleTypes}，改用 {@code World#spawnParticle} 循环
 * （DAMAGE_INDICATOR 用 "crit" 近似）。</li>
 * </ul>
 *
 * <p>
 * <b>NBT 契约不变</b>：所有 NBT 读写都委托 {@link GunItemDataAccessor}（api 包），key 名与上游一致。
 */
public class ModernKineticGunItem extends AbstractGunItem implements GunItemDataAccessor {

    public static final String TYPE_NAME = "modern_kinetic";

    private static final DoubleFunction<AttributeModifier> AM_FACTORY = amount -> new AttributeModifier(
        UUID.randomUUID(),
        "TACZ Melee Damage",
        amount,
        0 /* AttributeModifier.Operation.ADDITION 在 1.7.10 是 int 0 */
    );

    public ModernKineticGunItem() {
        super();
        this.setMaxStackSize(1);
    }

    /**
     * 1.7.10 必须有 {@link net.minecraft.util.IIcon}：上游 1.20.1 的 item model 是 {@code builtin/entity}
     * （由 BEWLR 3D 渲染，没有 2D 图标），1.7.10 无该机制，若不注册会生成 MISSING_ICON_ITEM 紫黑图标。
     * base 模组里没有枪的 2D png（上游枪图标来自枪包 3D 模型），这里用最接近的原版武器图标兜底。
     *
     * <p>
     * 注意图标名必须是 1.7.10 里真实存在的文件：1.7.10 原版**没有** {@code textures/items/bow.png}
     * （弓只有 {@code bow_standby.png} / {@code bow_pulling_*.png}，见
     * {@code build/rfg/minecraft-src/java/net/minecraft/item/ItemBow.java:159-161}
     * {@code register.registerIcon(this.getIconString() + "_standby")}），
     * 早期写成 {@code minecraft:bow} 会在 TextureMap 里报
     * {@code domain minecraft is missing 1 texture: textures/items/bow.png}（见 build/r7run.log）。
     *
     * <p>
     * TODO(L2, client-port): 枪械按 NBT（gunId）显示不同外观，需由 IItemRenderer 接管物品渲染，此图标仅作背包/JEI 兜底。
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.itemIcon = register.registerIcon("minecraft:bow_standby");
    }

    /**
     * 1.7.10 {@code Item#onUpdate} == 上游 {@code inventoryTick}。
     * <p>
     * <b>语义必须是"只冷却没拿在手上的枪"</b>：上游 {@code ModernKineticGunItem.java:69-73}
     * {@code if (level.isClientSide || ... || stack == player.getMainHandItem()) return;} —— 手持枪的热量由
     * {@code LivingEntityHeat#tickHeat}（每 tick 结算）负责，这里处理的是**背包内**的
     * {@code inventoryCoolingPerTick}。早期移植改成 {@code stack != player.getHeldItem()} 后判断反了，
     * 结果手持枪被双重冷却、背包里的枪永远不过热恢复（已修复，见 docs/review/04-gameplay.md）。
     */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.onUpdate(stack, world, entity, slot, selected);
        if (world.isRemote || !(entity instanceof EntityPlayer player) || stack == player.getHeldItem()) {
            return;
        }
        TimelessAPI.getCommonGunIndex(getGunId(stack))
            .ifPresent(index -> {
                GunHeatData heatData = index.getGunData()
                    .getHeatData();
                if (heatData == null) {
                    return;
                }
                float cooling = heatData.getInventoryCoolingPerTick();
                if (!Float.isFinite(cooling) || cooling <= 0) {
                    return;
                }
                float heat = getHeatAmount(stack);
                if (heat > 0) {
                    heat = Math.max(0, heat - cooling);
                    setHeatAmount(stack, heat);
                }
                if (heat <= 0 && isOverheatLocked(stack)) {
                    setOverheatLocked(stack, false);
                }
            });
    }

    @Override
    public boolean startBolt(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter) {
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null) {
            return false;
        }
        return Optional.ofNullable(gunIndex.getScript())
            .map(script -> checkFunction(script.get("start_bolt")))
            .map(
                func -> func.call(CoerceJavaToLua.coerce(api))
                    .checkboolean())
            .orElse(true);
    }

    @Override
    public boolean tickBolt(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter) {
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null) {
            return false;
        }
        return Optional.ofNullable(gunIndex.getScript())
            .map(script -> checkFunction(script.get("tick_bolt")))
            .map(
                func -> func.call(CoerceJavaToLua.coerce(api))
                    .checkboolean())
            .orElseGet(() -> defaultTickBolt(api));
    }

    @Override
    public void shoot(ShooterDataHolder dataHolder, ItemStack gunItem, Supplier<Float> pitch, Supplier<Float> yaw,
        EntityLivingBase shooter) {
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        api.setPitchSupplier(pitch);
        api.setYawSupplier(yaw);
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null) {
            return;
        }
        Optional.ofNullable(gunIndex.getScript())
            .map(script -> checkFunction(script.get("shoot")))
            .ifPresentOrElse(
                func -> func.call(CoerceJavaToLua.coerce(api)),
                () -> api.shootOnce(api.isShootingNeedConsumeAmmo()));
    }

    @Override
    public boolean startReload(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter) {
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null) {
            return false;
        }
        return Optional.ofNullable(gunIndex.getScript())
            .map(script -> checkFunction(script.get("start_reload")))
            .map(
                func -> func.call(CoerceJavaToLua.coerce(api))
                    .checkboolean())
            .orElse(true);
    }

    @Override
    public ReloadState tickReload(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter) {
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null) {
            return new ReloadState();
        }
        return Optional.ofNullable(gunIndex.getScript())
            .map(script -> checkFunction(script.get("tick_reload")))
            .map(func -> {
                ReloadState reloadState = new ReloadState();
                Varargs varargs = func.invoke(CoerceJavaToLua.coerce(api));
                int typeOrdinary = varargs.arg(1)
                    .checkint();
                long countDown = varargs.arg(2)
                    .checklong();
                reloadState.setStateType(ReloadState.StateType.values()[typeOrdinary]);
                reloadState.setCountDown(countDown);
                return reloadState;
            })
            .orElseGet(() -> defaultTickReload(api));
    }

    @Override
    public void interruptReload(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter) {
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null) {
            return;
        }
        Optional.ofNullable(gunIndex.getScript())
            .map(script -> checkFunction(script.get("interrupt_reload")))
            .ifPresent(func -> func.call(CoerceJavaToLua.coerce(api)));
    }

    @Override
    public void unloadAmmo(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter) {
        if (shooter.worldObj.isRemote || useInventoryAmmo(gunItem)) {
            return;
        }
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null || !gunIndex.canUnloadAmmo()) {
            return;
        }
        Optional.ofNullable(gunIndex.getScript())
            .map(script -> checkFunction(script.get("unload_ammo")))
            .ifPresentOrElse(
                func -> func.call(CoerceJavaToLua.coerce(api)),
                () -> super.unloadAmmo(dataHolder, gunItem, shooter));
    }

    @Override
    public void melee(ShooterDataHolder dataHolder, EntityLivingBase user, ItemStack gunItem) {
        ResourceLocation gunId = this.getGunId(gunItem);
        TimelessAPI.getCommonGunIndex(gunId)
            .ifPresent(gunIndex -> {
                GunMeleeData meleeData = gunIndex.getGunData()
                    .getMeleeData();
                float distance = meleeData.getDistance();

                ResourceLocation muzzleId = this.getAttachmentId(gunItem, AttachmentType.MUZZLE);
                MeleeData muzzleData = getMeleeData(muzzleId);
                if (muzzleData != null) {
                    doMelee(
                        user,
                        distance,
                        muzzleData.getDistance(),
                        muzzleData.getRangeAngle(),
                        muzzleData.getKnockback(),
                        muzzleData.getDamage(),
                        muzzleData.getEffects());
                    return;
                }

                ResourceLocation stockId = this.getAttachmentId(gunItem, AttachmentType.STOCK);
                MeleeData stockData = getMeleeData(stockId);
                if (stockData != null) {
                    doMelee(
                        user,
                        distance,
                        stockData.getDistance(),
                        stockData.getRangeAngle(),
                        stockData.getKnockback(),
                        stockData.getDamage(),
                        stockData.getEffects());
                    return;
                }

                GunDefaultMeleeData defaultData = meleeData.getDefaultMeleeData();
                if (defaultData == null) {
                    return;
                }
                doMelee(
                    user,
                    distance,
                    defaultData.getDistance(),
                    defaultData.getRangeAngle(),
                    defaultData.getKnockback(),
                    defaultData.getDamage(),
                    Collections.emptyList());
            });
    }

    @Override
    public void tickHeat(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter) {
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        long heatTimestamp = dataHolder.heatTimestamp;
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null) {
            return;
        }
        Optional.ofNullable(gunIndex.getScript())
            .map(script -> checkFunction(script.get("tick_heat")))
            .ifPresentOrElse(
                func -> func.call(CoerceJavaToLua.coerce(api), LuaValue.valueOf(heatTimestamp)),
                () -> defaultTickHeat(heatTimestamp, gunItem));
    }

    private void defaultTickHeat(long heatTimestamp, ItemStack gunItem) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return;
        }
        TimelessAPI.getCommonGunIndex(iGun.getGunId(gunItem))
            .map(
                index -> index.getGunData()
                    .getHeatData())
            .ifPresent(heatData -> {
                if (iGun.getHeatAmount(gunItem) <= 0) {
                    return;
                }
                if (iGun.isOverheatLocked(gunItem)) {
                    tickLocked(iGun, gunItem, heatData, heatTimestamp);
                } else {
                    tickNormal(iGun, gunItem, heatData, heatTimestamp);
                }
            });
    }

    public void tickLocked(IGun iGun, ItemStack gunStack, GunHeatData heatData, long heatTimestamp) {
        if (GunTime.nowMillis() - heatTimestamp >= heatData.getOverHeatTime()) {
            float heatAmount = iGun.getHeatAmount(gunStack)
                - ((float) (GunTime.nowMillis() - heatTimestamp) / 10000f) * heatData.getCoolingMultiplier();
            iGun.setHeatAmount(gunStack, heatAmount);
            if (heatAmount <= 0) {
                iGun.setOverheatLocked(gunStack, false);
            }
        }
    }

    public void tickNormal(IGun iGun, ItemStack gunStack, GunHeatData heatData, long heatTimestamp) {
        if (GunTime.nowMillis() - heatTimestamp >= heatData.getCoolingDelay()) {
            float heatAmount = iGun.getHeatAmount(gunStack)
                - ((float) (GunTime.nowMillis() - heatTimestamp) / 10000f) * heatData.getCoolingMultiplier();
            iGun.setHeatAmount(gunStack, heatAmount);
        }
    }

    public <T> T modifyProperty(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter,
        String luaMethodName, String id, Class<T> type, T original) {
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null) {
            return original;
        }
        T afterDefaultModification = defaultPropertyModification.modify(gunItem, shooter, gunIndex, id, original);
        try {
            return Optional.ofNullable(gunIndex.getScript())
                .map(script -> checkFunction(script.get(luaMethodName)))
                .map(
                    func -> func.call(
                        CoerceJavaToLua.coerce(api),
                        LuaValue.valueOf(id),
                        CoerceJavaToLua.coerce(afterDefaultModification)))
                .map(luaValue -> type.cast(CoerceLuaToJava.coerce(luaValue, type)))
                .orElse(afterDefaultModification);
        } catch (Exception exception) {
            GunMod.LOGGER
                .warn(MarkerManager.getMarker("Gun Script"), "Failed to modify gun property {}", id, exception);
            return afterDefaultModification;
        }
    }

    public final DefaultPropertyModification defaultPropertyModification = new DefaultPropertyModification();

    public class DefaultPropertyModification {

        public static final String SLUGS = GunMod.MOD_ID + ":intrinsic/slug";

        @SuppressWarnings("unchecked")
        public <T> T modify(ItemStack gunItem, EntityLivingBase shooter, CommonGunIndex gunIndex, String id,
            T original) {
            if (GunProperties.RuntimeOnly.BULLET_AMOUNT.equals(id)) {
                if (AllowAttachmentTagMatcher
                    .matchTag(new ResourceLocation(SLUGS), getAttachmentId(gunItem, AttachmentType.EXTENDED_MAG))) {
                    return (T) Integer.valueOf(1);
                }
            }
            return original;
        }
    }

    @Override
    public void doBulletSpread(ShooterDataHolder dataHolder, ItemStack gunItem, EntityLivingBase shooter,
        Entity projectile, int bulletCnt, float processedSpeed, float inaccuracy, float pitch, float yaw) {
        if (!(projectile instanceof EntityKineticBullet bullet)) {
            return;
        }
        ModernKineticGunScriptAPI api = createScriptApi(dataHolder, gunItem, shooter);
        CommonGunIndex gunIndex = api.getGunIndex();
        if (gunIndex == null) {
            return;
        }
        Optional.ofNullable(gunIndex.getScript())
            .map(script -> checkFunction(script.get("calcSpread")))
            .map(
                func -> func
                    .call(CoerceJavaToLua.coerce(api), LuaValue.valueOf(bulletCnt), LuaValue.valueOf(inaccuracy)))
            .map(luaValue -> {
                if (luaValue.istable()) {
                    LuaTable table = luaValue.checktable();
                    return new Vector2d(
                        table.get(1)
                            .checkdouble(),
                        table.get(2)
                            .checkdouble());
                }
                return null;
            })
            .ifPresentOrElse(
                vector2d -> { bullet.shootFromRotation(shooter, pitch, yaw, 0.0F, processedSpeed, vector2d); },
                () -> { bullet.shootFromRotation(shooter, pitch, yaw, 0.0F, processedSpeed, inaccuracy); });
    }

    private ModernKineticGunScriptAPI createScriptApi(ShooterDataHolder dataHolder, ItemStack gunItem,
        EntityLivingBase shooter) {
        ModernKineticGunScriptAPI api = new ModernKineticGunScriptAPI();
        api.setItemStack(gunItem);
        api.setShooter(shooter);
        api.setDataHolder(dataHolder);
        return api;
    }

    private boolean defaultTickBolt(ModernKineticGunScriptAPI api) {
        GunData gunData = api.getGunIndex()
            .getGunData();
        long boltActionTime = (long) (gunData.getBoltActionTime() * 1000);
        float rawBoltFeedTime = gunData.getBoltFeedTime();
        long boltFeedTime = rawBoltFeedTime == -1 ? boltActionTime : (long) (gunData.getBoltFeedTime() * 1000);
        if (api.getBoltTime() < boltFeedTime) {
            return true;
        }
        if (!api.hasAmmoInBarrel()) {
            if (api.useInventoryAmmo()) {
                if (api.consumeAmmoFromPlayer(1) == 1) {
                    api.setAmmoInBarrel(true);
                }
            } else if (api.removeAmmoFromMagazine(1) != 0) {
                api.setAmmoInBarrel(true);
            }
        }
        return api.getBoltTime() < boltActionTime;
    }

    private ReloadState defaultTickReload(ModernKineticGunScriptAPI api) {
        CommonGunIndex gunIndex = api.getGunIndex();
        GunData gunData = gunIndex.getGunData();
        GunReloadData reloadData = gunData.getReloadData();
        long countDown;
        ReloadState.StateType stateType;
        ReloadState.StateType oldStateType = ReloadState.StateType.values()[api.getReloadStateType()];
        long progressTime = api.getReloadTime();
        if (oldStateType.isReloadingEmpty()) {
            long feedTime = (long) (reloadData.getFeed()
                .getEmptyTime() * 1000);
            long finishingTime = (long) (reloadData.getCooldown()
                .getEmptyTime() * 1000);
            if (progressTime < feedTime) {
                stateType = ReloadState.StateType.EMPTY_RELOAD_FEEDING;
                countDown = feedTime - progressTime;
            } else if (progressTime < finishingTime) {
                stateType = ReloadState.StateType.EMPTY_RELOAD_FINISHING;
                countDown = finishingTime - progressTime;
            } else {
                stateType = ReloadState.StateType.NOT_RELOADING;
                countDown = ReloadState.NOT_RELOADING_COUNTDOWN;
            }
        } else if (oldStateType.isReloadingTactical()) {
            long feedTime = (long) (reloadData.getFeed()
                .getTacticalTime() * 1000);
            long finishingTime = (long) (reloadData.getCooldown()
                .getTacticalTime() * 1000);
            if (progressTime < feedTime) {
                stateType = ReloadState.StateType.TACTICAL_RELOAD_FEEDING;
                countDown = feedTime - progressTime;
            } else if (progressTime < finishingTime) {
                stateType = ReloadState.StateType.TACTICAL_RELOAD_FINISHING;
                countDown = finishingTime - progressTime;
            } else {
                stateType = ReloadState.StateType.NOT_RELOADING;
                countDown = ReloadState.NOT_RELOADING_COUNTDOWN;
            }
        } else {
            stateType = ReloadState.StateType.NOT_RELOADING;
            countDown = ReloadState.NOT_RELOADING_COUNTDOWN;
        }
        if (oldStateType == ReloadState.StateType.EMPTY_RELOAD_FEEDING && oldStateType != stateType) {
            this.defaultReloadFinishing(api, false);
        }
        if (oldStateType == ReloadState.StateType.TACTICAL_RELOAD_FEEDING && oldStateType != stateType) {
            this.defaultReloadFinishing(api, true);
        }
        ReloadState reloadState = new ReloadState();
        reloadState.setStateType(stateType);
        reloadState.setCountDown(countDown);
        return reloadState;
    }

    private void defaultReloadFinishing(ModernKineticGunScriptAPI api, boolean isTactical) {
        GunData data = api.getGunIndex()
            .getGunData();
        int needAmmoCount = api.getNeededAmmoAmount();
        boolean needConsumeAmmo = api.isReloadingNeedConsumeAmmo();
        boolean infinite = data.getReloadData()
            .isInfinite();
        needConsumeAmmo = needConsumeAmmo && !infinite;
        switch (data.getReloadData()
            .getType()) {
            case MAGAZINE -> {
                if (needConsumeAmmo) {
                    int consumedAmount = api.consumeAmmoFromPlayer(needAmmoCount);
                    api.putAmmoInMagazine(consumedAmount);
                } else {
                    api.putAmmoInMagazine(needAmmoCount);
                }
            }
            case FUEL -> {
                if (needConsumeAmmo) {
                    int consumedAmount = api.consumeAmmoFromPlayer(1);
                    api.putAmmoInMagazine(needAmmoCount * consumedAmount);
                } else {
                    api.putAmmoInMagazine(needAmmoCount);
                }
            }
            default -> {
                // 未实现
            }
        }
        Bolt boltType = api.getGunIndex()
            .getGunData()
            .getBolt();
        if (!isTactical && (boltType == Bolt.MANUAL_ACTION || boltType == Bolt.CLOSED_BOLT)) {
            int i = api.removeAmmoFromMagazine(1);
            if (i != 0) {
                api.setAmmoInBarrel(true);
            }
        }
    }

    private void doMelee(EntityLivingBase user, float gunDistance, float meleeDistance, float rangeAngle,
        float knockback, float damage, List<EffectData> effects) {
        double distance = gunDistance + meleeDistance;
        float xRot = (float) Math.toRadians(-user.rotationPitch);
        float yRot = (float) Math.toRadians(-user.rotationYaw);
        // 1.7.10 Vec3 没有 xRot/yRot 链式 API，且 rotateAroundX/Y 是原地修改
        Vec3 eyeVec = Vec3.createVectorHelper(0, 0, 1);
        eyeVec.rotateAroundX(xRot);
        eyeVec.rotateAroundY(yRot);
        eyeVec = eyeVec.normalize();
        eyeVec = Vec3.createVectorHelper(eyeVec.xCoord * distance, eyeVec.yCoord * distance, eyeVec.zCoord * distance);
        Vec3 userEye = Vec3.createVectorHelper(user.posX, user.posY + user.getEyeHeight(), user.posZ);
        // 注意 1.7.10 的 Vec3#subtract 是「反向减法」：a.subtract(b) 返回 b - a
        // （build/rfg/minecraft-src/java/net/minecraft/util/Vec3.java:55-61 的 javadoc：
        // "Returns a new vector with the result of the specified vector minus this"）。
        // 上游 1.20.1 是 user.getEyePosition().subtract(eyeVec)（= userEye - eyeVec），
        // 因此这里直接按分量写，避免误用反向减法：一旦写成 userEye.subtract(eyeVec)，
        // 球心会落到身前 8 格，整个锥形判定反向 —— 这正是「按 V 打怪时所有 candidate 的
        // degree 在 121°~145° 被拒、没有任何伤害」的根因（见 docs/review/r8/README.md「实机四轮」）。
        Vec3 centrePos = Vec3.createVectorHelper(
            userEye.xCoord - eyeVec.xCoord,
            userEye.yCoord - eyeVec.yCoord,
            userEye.zCoord - eyeVec.zCoord);
        List<?> entityList = user.worldObj
            .getEntitiesWithinAABB(EntityLivingBase.class, user.boundingBox.expand(distance, distance, distance));
        if (DebugCommand.DEBUG) {
            // 移植特有的实机排查日志（上游无对应输出）：只在使用 /tacz debug true 时打印。
            GunMod.LOGGER.info(
                "[TACZ Melee] server doMelee: gunDistance={}, meleeDistance={}, distance={}, rangeAngle={}, damage={}, knockback={}, candidates={}",
                gunDistance,
                meleeDistance,
                distance,
                rangeAngle,
                damage,
                knockback,
                entityList.size());
        }
        com.google.common.base.Supplier<Float> realDamage = Suppliers.memoize(() -> {
            IAttributeInstance instance = user.getEntityAttribute(SharedMonsterAttributes.attackDamage);
            if (instance == null) {
                return damage;
            }
            double oldBase = instance.getBaseValue();
            AttributeModifier modifier = AM_FACTORY.apply(damage);
            try {
                instance.setBaseValue(0);
                instance.applyModifier(modifier);
                return (float) instance.getAttributeValue();
            } finally {
                instance.setBaseValue(oldBase);
                instance.removeModifier(modifier);
            }
        });
        for (Object object : entityList) {
            if (!(object instanceof EntityLivingBase living)) {
                continue;
            }
            Vec3 targetEye = Vec3.createVectorHelper(living.posX, living.posY + living.getEyeHeight(), living.posZ);
            // 同上：上游 targetEye - centrePos；1.7.10 的 Vec3#subtract 反号，故按分量写。
            Vec3 targetVec = Vec3.createVectorHelper(
                targetEye.xCoord - centrePos.xCoord,
                targetEye.yCoord - centrePos.yCoord,
                targetEye.zCoord - centrePos.zCoord);
            double targetLength = targetVec.lengthVector();
            if (targetLength < distance) {
                continue;
            }
            double degree = Math.toDegrees(Math.acos(targetVec.dotProduct(eyeVec) / (targetLength * distance)));
            if (degree < (rangeAngle / 2)) {
                boolean seen = user.canEntityBeSeen(living);
                if (DebugCommand.DEBUG) {
                    GunMod.LOGGER.info(
                        "[TACZ Melee] candidate {}: targetLength={}, degree={} (halfRangeAngle={}), canEntityBeSeen={}",
                        living,
                        targetLength,
                        degree,
                        rangeAngle / 2,
                        seen);
                }
                if (seen) {
                    doPerLivingHurt(user, living, knockback, realDamage.get(), effects);
                }
            } else if (DebugCommand.DEBUG) {
                GunMod.LOGGER.info(
                    "[TACZ Melee] candidate {} rejected: degree={} >= halfRangeAngle={}",
                    living,
                    degree,
                    rangeAngle / 2);
            }
        }

        if (user instanceof EntityPlayer player) {
            player.addExhaustion(0.1F);
        }

        if (DebugCommand.DEBUG) {
            GunMeleeDebug.showRange(user, (int) Math.round(distance), centrePos, eyeVec, rangeAngle);
        }
    }

    private static void doPerLivingHurt(EntityLivingBase user, EntityLivingBase target, float knockback, float damage,
        List<EffectData> effects) {
        if (target.equals(user)) {
            return;
        }
        // 1.7.10 的 knockBack 是 protected，按 1.20 LivingEntity#knockback 的语义手写速度施加：
        // strength 先按目标的击退抗性衰减（1.7.10 有该属性，EntityLivingBase.java:178 注册）。
        double strength = knockback;
        IAttributeInstance resistance = target.getEntityAttribute(SharedMonsterAttributes.knockbackResistance);
        if (resistance != null) {
            strength *= Math.max(0.0D, 1.0D - resistance.getAttributeValue());
        }
        if (strength > 0.0D) {
            double xd = Math.sin(Math.toRadians(user.rotationYaw));
            double zd = -Math.cos(Math.toRadians(user.rotationYaw));
            target.motionX = target.motionX / 2.0D - xd * strength;
            target.motionY = target.onGround ? Math.min(0.4D, target.motionY / 2.0D + strength) : target.motionY;
            target.motionZ = target.motionZ / 2.0D - zd * strength;
        }
        boolean hurtAccepted;
        if (user instanceof EntityPlayer player) {
            hurtAccepted = target.attackEntityFrom(DamageSource.causePlayerDamage(player), damage);
        } else {
            hurtAccepted = target.attackEntityFrom(DamageSource.causeMobDamage(user), damage);
        }
        if (DebugCommand.DEBUG) {
            // 移植特有排查日志：attackEntityFrom 返回 false 表示伤害被目标（或事件监听器）拒绝
            GunMod.LOGGER.info(
                "[TACZ Melee] hurt target {}: damage={}, accepted={}, healthAfter={}",
                target,
                damage,
                hurtAccepted,
                target.getHealth());
        }
        // 上游 user.doEnchantDamageEffects(user, target)（1.8+ Forge API）在 1.7.10 的等价物就是原版
        // EntityPlayer#attackTargetEntityWithCurrentItem 里的这两行（EntityPlayer.java:1417-1420）：
        // 前者跑受击方装备的 doPostHurt（荆棘），后者跑攻击方装备的 doPostAttack（火焰附加）。
        EnchantmentHelper.func_151384_a(target, user);
        EnchantmentHelper.func_151385_b(user, target);

        if (!target.isEntityAlive()) {
            return;
        }
        for (EffectData data : effects) {
            Potion potion = getPotionFromId(data.getEffectId());
            if (potion == null) {
                continue;
            }
            int time = Math.max(0, data.getTime() * 20);
            int amplifier = Math.max(0, data.getAmplifier());
            // 1.7.10 PotionEffect 没有 hideParticles 参数（1.8+ 才有），记录差异
            target.addPotionEffect(new PotionEffect(potion.getId(), time, amplifier));
        }
        // 上游 ServerLevel#sendParticles(DAMAGE_INDICATOR, x, y+0.5h, z, damage*0.5, 0.1, 0, 0.1, 0.2)；
        // 1.7.10 无 DAMAGE_INDICATOR 粒子，用 "crit" 近似；且 World#spawnParticle 在服务端是空实现，
        // 必须走 WorldServer#func_147487_a（打包 S2APacketParticles，WorldServer.java:1121-1139）。
        if (user.worldObj instanceof WorldServer worldServer) {
            worldServer.func_147487_a(
                "crit",
                target.posX,
                target.posY + target.height * 0.5D,
                target.posZ,
                (int) (damage * 0.5),
                0.1D,
                0D,
                0.1D,
                0.2D);
        }
    }

    /**
     * 1.20.1 的 {@code MobEffect} 是注册表对象（JSON 里以 ResourceLocation 引用）；
     * 1.7.10 的药水是 {@code Potion.potionTypes[id]} 静态数组，不支持自定义注册名。
     * 这里对原版效果名做映射，非原版效果（模组药水）无法表达 → 返回 null 并跳过（记录差异）。
     */
    @Nullable
    private static Potion getPotionFromId(@Nullable ResourceLocation effectId) {
        if (effectId == null) {
            return null;
        }
        return switch (effectId.getResourcePath()) {
            case "speed" -> Potion.moveSpeed;
            case "slowness" -> Potion.moveSlowdown;
            case "haste" -> Potion.digSpeed;
            case "mining_fatigue" -> Potion.digSlowdown;
            case "strength" -> Potion.damageBoost;
            case "instant_health" -> Potion.heal;
            case "instant_damage" -> Potion.harm;
            case "jump_boost" -> Potion.jump;
            case "nausea" -> Potion.confusion;
            case "regeneration" -> Potion.regeneration;
            case "resistance" -> Potion.resistance;
            case "fire_resistance" -> Potion.fireResistance;
            case "water_breathing" -> Potion.waterBreathing;
            case "invisibility" -> Potion.invisibility;
            case "blindness" -> Potion.blindness;
            case "night_vision" -> Potion.nightVision;
            case "hunger" -> Potion.hunger;
            case "weakness" -> Potion.weakness;
            case "poison" -> Potion.poison;
            case "wither" -> Potion.wither;
            case "health_boost" -> Potion.field_76434_w;
            case "absorption" -> Potion.field_76444_x;
            case "saturation" -> Potion.field_76443_y;
            default -> null;
        };
    }

    @Nullable
    private MeleeData getMeleeData(ResourceLocation attachmentId) {
        if (DefaultAssets.isEmptyAttachmentId(attachmentId)) {
            return null;
        }
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
            .map(
                index -> index.getData()
                    .getMeleeData())
            .orElse(null);
    }

    @Nullable
    private static LuaFunction checkFunction(LuaValue luaValue) {
        if (luaValue.isfunction()) {
            return (LuaFunction) luaValue;
        } else if (luaValue.isnil()) {
            return null;
        } else {
            throw new LuaError("bad argument: function or nil expected, got " + luaValue.typename());
        }
    }

    @Override
    public void fireSelect(ShooterDataHolder dataHolder, ItemStack gunItem) {
        ResourceLocation gunId = this.getGunId(gunItem);
        TimelessAPI.getCommonGunIndex(gunId)
            .map(gunIndex -> {
                FireMode fireMode = this.getFireMode(gunItem);
                List<FireMode> fireModeSet = gunIndex.getGunData()
                    .getFireModeSet();
                int nextIndex = (fireModeSet.indexOf(fireMode) + 1) % fireModeSet.size();
                FireMode nextFireMode = fireModeSet.get(nextIndex);
                this.setFireMode(gunItem, nextFireMode);
                return nextFireMode;
            });
    }

    @Override
    public int getLevel(int exp) {
        return 0;
    }

    @Override
    public int getExp(int level) {
        return 0;
    }

    @Override
    public int getMaxLevel() {
        return 0;
    }
}
