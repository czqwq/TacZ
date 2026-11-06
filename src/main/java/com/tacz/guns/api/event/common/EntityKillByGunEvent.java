package com.tacz.guns.api.event.common;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.tuple.Pair;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.Side;

/**
 * 生物被枪械子弹击杀时触发的事件
 */
public class EntityKillByGunEvent extends Event implements KubeJSGunEventPoster<EntityKillByGunEvent> {

    private final Entity bullet;
    private final @Nullable EntityLivingBase killedEntity;
    private final @Nullable EntityLivingBase attacker;
    private final ResourceLocation gunId;
    private final ResourceLocation gunDisplayId;
    private final float baseDamage;
    private final DamageSource nonApPartDamageSource;
    private final DamageSource apPartDamageSource;
    private final boolean isHeadShot;
    private final float headshotMultiplier;
    private final Side logicalSide;

    public EntityKillByGunEvent(Entity bullet, @Nullable EntityLivingBase hurtEntity,
        @Nullable EntityLivingBase attacker, ResourceLocation gunId, ResourceLocation gunDisplayId, float baseDamage,
        @Nullable Pair<DamageSource, DamageSource> sources, boolean isHeadShot, float headshotMultiplier,
        Side logicalSide) {
        this.bullet = bullet;
        this.killedEntity = hurtEntity;
        this.attacker = attacker;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.baseDamage = baseDamage;
        this.nonApPartDamageSource = Optional.ofNullable(sources)
            .map(Pair::getLeft)
            .orElse(null);
        this.apPartDamageSource = Optional.ofNullable(sources)
            .map(Pair::getRight)
            .orElse(null);
        this.isHeadShot = isHeadShot;
        this.headshotMultiplier = headshotMultiplier;
        this.logicalSide = logicalSide;
        postEventToKubeJS(this);
    }

    /**
     * 在逻辑客户端不保证能用
     */
    public Entity getBullet() {
        return bullet;
    }

    @Nullable
    public EntityLivingBase getKilledEntity() {
        return killedEntity;
    }

    @Nullable
    public EntityLivingBase getAttacker() {
        return attacker;
    }

    public ResourceLocation getGunId() {
        return gunId;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public DamageSource getDamageSource(GunDamageSourcePart part) {
        if (logicalSide.isClient()) {
            throw new UnsupportedOperationException("DamageSource about gun hit is not available on client side!");
        }
        return part == GunDamageSourcePart.ARMOR_PIERCING ? apPartDamageSource : nonApPartDamageSource;
    }

    public boolean isHeadShot() {
        return isHeadShot;
    }

    public float getHeadshotMultiplier() {
        return headshotMultiplier;
    }

    public Side getLogicalSide() {
        return logicalSide;
    }

    public ResourceLocation getGunDisplayId() {
        return gunDisplayId;
    }
}
