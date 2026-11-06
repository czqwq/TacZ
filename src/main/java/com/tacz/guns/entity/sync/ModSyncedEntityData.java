package com.tacz.guns.entity.sync;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.entity.sync.core.Serializers;
import com.tacz.guns.entity.sync.core.SyncedClassKey;
import com.tacz.guns.entity.sync.core.SyncedDataKey;
import com.tacz.guns.entity.sync.core.SyncedEntityData;

/**
 * 搬运自上游 1.20.1 {@code entity/sync/ModSyncedEntityData.java}。
 *
 * <p>
 * 1.7.10 适配：{@code LivingEntity} → {@link EntityLivingBase}、{@code ResourceLocation} 换 1.7.10 包；
 * key 名、默认值、syncMode 与上游**完全一致**（被 {@code LivingEntityMixin}、{@code shooter}、
 * {@code event/SyncedEntityDataEvent} 直接引用）。
 */
public class ModSyncedEntityData {

    public static final SyncedDataKey<EntityLivingBase, Long> SHOOT_COOL_DOWN_KEY = SyncedDataKey
        .builder(SyncedClassKey.LIVING_ENTITY, Serializers.LONG)
        .id(new ResourceLocation(GunMod.MOD_ID, "shoot_cool_down"))
        .defaultValueSupplier(() -> -1L)
        .syncMode(SyncedDataKey.SyncMode.ALL)
        .build();

    public static final SyncedDataKey<EntityLivingBase, Long> MELEE_COOL_DOWN_KEY = SyncedDataKey
        .builder(SyncedClassKey.LIVING_ENTITY, Serializers.LONG)
        .id(new ResourceLocation(GunMod.MOD_ID, "melee_cool_down"))
        .defaultValueSupplier(() -> -1L)
        .syncMode(SyncedDataKey.SyncMode.ALL)
        .build();

    public static final SyncedDataKey<EntityLivingBase, ReloadState> RELOAD_STATE_KEY = SyncedDataKey
        .builder(SyncedClassKey.LIVING_ENTITY, ModSerializers.RELOAD_STATE)
        .id(new ResourceLocation(GunMod.MOD_ID, "reload_state"))
        .defaultValueSupplier(ReloadState::new)
        .syncMode(SyncedDataKey.SyncMode.ALL)
        .build();

    public static final SyncedDataKey<EntityLivingBase, Float> AIMING_PROGRESS_KEY = SyncedDataKey
        .builder(SyncedClassKey.LIVING_ENTITY, Serializers.FLOAT)
        .id(new ResourceLocation(GunMod.MOD_ID, "aiming_progress"))
        .defaultValueSupplier(() -> 0f)
        .syncMode(SyncedDataKey.SyncMode.ALL)
        .build();

    public static final SyncedDataKey<EntityLivingBase, Long> DRAW_COOL_DOWN_KEY = SyncedDataKey
        .builder(SyncedClassKey.LIVING_ENTITY, Serializers.LONG)
        .id(new ResourceLocation(GunMod.MOD_ID, "draw_cool_down"))
        .defaultValueSupplier(() -> -1L)
        .syncMode(SyncedDataKey.SyncMode.ALL)
        .build();

    public static final SyncedDataKey<EntityLivingBase, Boolean> IS_AIMING_KEY = SyncedDataKey
        .builder(SyncedClassKey.LIVING_ENTITY, Serializers.BOOLEAN)
        .id(new ResourceLocation(GunMod.MOD_ID, "is_aiming"))
        .defaultValueSupplier(() -> false)
        .syncMode(SyncedDataKey.SyncMode.ALL)
        .build();

    public static final SyncedDataKey<EntityLivingBase, Float> SPRINT_TIME_KEY = SyncedDataKey
        .builder(SyncedClassKey.LIVING_ENTITY, Serializers.FLOAT)
        .id(new ResourceLocation(GunMod.MOD_ID, "sprint_time"))
        .defaultValueSupplier(() -> 0f)
        .syncMode(SyncedDataKey.SyncMode.ALL)
        .build();

    public static final SyncedDataKey<EntityLivingBase, Boolean> IS_BOLTING_KEY = SyncedDataKey
        .builder(SyncedClassKey.LIVING_ENTITY, Serializers.BOOLEAN)
        .id(new ResourceLocation(GunMod.MOD_ID, "is_bolting"))
        .defaultValueSupplier(() -> false)
        .syncMode(SyncedDataKey.SyncMode.ALL)
        .build();

    public static final SyncedDataKey<EntityLivingBase, Integer> THROWABLE_USE_TICK = SyncedDataKey
        .builder(SyncedClassKey.LIVING_ENTITY, Serializers.INTEGER)
        .id(new ResourceLocation(GunMod.MOD_ID, "throwable_using"))
        .defaultValueSupplier(() -> -1)
        .syncMode(SyncedDataKey.SyncMode.SELF_ONLY)
        .build();

    public static void init() {
        registerEntityData(SHOOT_COOL_DOWN_KEY);
        registerEntityData(MELEE_COOL_DOWN_KEY);
        registerEntityData(RELOAD_STATE_KEY);
        registerEntityData(AIMING_PROGRESS_KEY);
        registerEntityData(DRAW_COOL_DOWN_KEY);
        registerEntityData(IS_AIMING_KEY);
        registerEntityData(SPRINT_TIME_KEY);
        registerEntityData(IS_BOLTING_KEY);
        registerEntityData(THROWABLE_USE_TICK);
    }

    private static void registerEntityData(SyncedDataKey<? extends Entity, ?> dataKey) {
        SyncedEntityData.instance()
            .registerDataKey(dataKey);
    }
}
