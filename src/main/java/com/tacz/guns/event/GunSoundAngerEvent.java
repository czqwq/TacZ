package com.tacz.guns.event;

import java.util.List;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * 枪声吸引/激怒附近怪物（可被消音器与配置关闭）。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/GunSoundAngerEvent.java}
 * <p>
 * 1.7.10 差异（1.7.10 没有 1.20 的实体分类接口，用最接近的原版类型近似，<b>语义已降级</b>）：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}；{@code LogicalSide} → {@link Side}；</li>
 * <li>{@code ServerPlayer} → {@link EntityPlayerMP}；{@code player.isCreative()} →
 * {@code player.capabilities.isCreativeMode}；</li>
 * <li>{@code Level} → {@link World}（{@code player.level()} → {@code player.worldObj}）；</li>
 * <li>{@code Level#getEntitiesOfClass(Class, AABB, Predicate)} →
 * {@code World#getEntitiesWithinAABB(Class, AxisAlignedBB)} + 逐个判定；
 * {@code AABB#inflate(r)} → {@code AxisAlignedBB#expand(r,r,r)}；</li>
 * <li>{@code Mob} → {@link EntityLiving}（1.7.10 有 AI 目标/复仇目标的实体基类）；
 * {@code NeutralMob} → 1.7.10 无该接口，用「可驯服动物 + 铁傀儡 + 僵尸猪人」近似
 * （见 {@link #isNeutralMob}）；{@code Enemy} → {@link IMob}；
 * {@code TamableAnimal#isTame()} → {@code EntityTameable#isTamed()}；
 * {@code IronGolem#isPlayerCreated()} → {@link EntityIronGolem#isPlayerCreated()}（同名）；</li>
 * <li>{@code mob.setLastHurtByMob(player)} → {@code EntityLivingBase#setRevengeTarget(player)}；
 * {@code mob.setTarget(player)} → {@code EntityLiving#setAttackTarget(player)}；</li>
 * <li>{@code mob.isAlliedTo(player)} → 1.20 的 Team/Alliance 概念 1.7.10 没有，退回计分板队伍
 * {@code EntityLivingBase#isOnSameTeam(EntityLivingBase)}；</li>
 * <li>{@code mob.canAttack(player)} → {@code EntityLiving#canAttackClass(Class)}（按类型判断，
 * 对铁傀儡的 PlayerCreated 分支恰好等价）；</li>
 * <li>{@code distanceToSqr(player)} → {@code Entity#getDistanceSqToEntity(Entity)}；</li>
 * <li>{@code ItemStack.EMPTY} → {@code null} 判空。</li>
 * </ul>
 * <p>
 * TODO(port): 上游 GunSoundAngerEvent.java:35 还判断 {@code player.isSpectator()}（旁观者）；1.7.10 无旁观者模式，
 * 已删除。
 * TODO(port): 上游 GunSoundAngerEvent.java:83 用 {@code NeutralMob} 接口判定中立生物，1.7.10 没有该接口，
 * 用 {@link EntityTameable}/{@link EntityIronGolem}/{@link EntityPigZombie} 近似；1.7.10 里其它「被打才反击」的
 * 生物（如带 AI 的末影人、蜜蜂类模组生物）不会进入中立分支，需要模组自行扩展时再补。
 */
public class GunSoundAngerEvent {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new GunSoundAngerEvent());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGunFire(GunFireEvent event) {
        if (event.isCanceled() || !GunConfig.ENABLE_GUN_SOUND_ANGER.get()) {
            return;
        }
        if (event.getLogicalSide() != Side.SERVER || !(event.getShooter() instanceof EntityPlayerMP player)) {
            return;
        }
        if (player instanceof FakePlayer || player.capabilities.isCreativeMode) {
            return;
        }

        ItemStack gunItem = event.getGunItemStack();
        if (gunItem == null || gunItem.getItem() == null) {
            return;
        }
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null) {
            return;
        }

        TimelessAPI.getCommonGunIndex(gun.getGunId(gunItem))
            .ifPresent(gunIndex -> {
                GunData gunData = gunIndex.getGunData();
                if (hasSilencer(gunItem, gunData)) {
                    return;
                }

                float fireMultiplier = gunData.getFireSound()
                    .getFireMultiplier();
                int neutralRadius = getCappedRadius(
                    GunConfig.DEFAULT_GUN_SOUND_NEUTRAL_MOB_ANGER_DISTANCE.get(),
                    fireMultiplier);
                int hostileRadius = getCappedRadius(
                    GunConfig.DEFAULT_GUN_SOUND_HOSTILE_MOB_ANGER_DISTANCE.get(),
                    fireMultiplier);
                if (neutralRadius <= 0 && hostileRadius <= 0) {
                    return;
                }

                angerMobs(player, neutralRadius, hostileRadius);
            });
    }

    private static boolean hasSilencer(ItemStack gunItem, GunData gunData) {
        final boolean[] found = { false };
        AttachmentDataUtils.getAllAttachmentData(gunItem, gunData, attachmentData -> {
            if (attachmentData.getModifier()
                .containsKey(SilenceModifier.ID)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static int getCappedRadius(int baseRadius, float multiplier) {
        return Math.min(Math.round(baseRadius * multiplier), GunConfig.MAX_GUN_SOUND_MOB_ANGER_DISTANCE);
    }

    private static void angerMobs(EntityPlayerMP player, int neutralRadius, int hostileRadius) {
        World level = player.worldObj;
        int searchRadius = Math.max(neutralRadius, hostileRadius);
        double neutralRadiusSqr = (double) neutralRadius * neutralRadius;
        double hostileRadiusSqr = (double) hostileRadius * hostileRadius;
        AxisAlignedBB searchBox = player.boundingBox.expand(searchRadius, searchRadius, searchRadius);
        List<EntityLiving> mobs = level.getEntitiesWithinAABB(EntityLiving.class, searchBox);
        for (EntityLiving mob : mobs) {
            if (!shouldAnger(player, mob, neutralRadiusSqr, hostileRadiusSqr)) {
                continue;
            }
            if (isNeutralMob(mob)) {
                mob.setRevengeTarget(player);
            } else {
                mob.setAttackTarget(player);
            }
        }
    }

    private static boolean shouldAnger(EntityPlayerMP player, EntityLiving mob, double neutralRadiusSqr,
        double hostileRadiusSqr) {
        if (!mob.isEntityAlive() || mob.getAttackTarget() != null) {
            return false;
        }
        if (mob.isOnSameTeam(player) || !mob.canAttackClass(player.getClass())) {
            return false;
        }
        if (isNeutralMob(mob)) {
            if (mob instanceof EntityTameable tameable && tameable.isTamed()) {
                return false;
            }
            if (mob instanceof EntityIronGolem golem && golem.isPlayerCreated()) {
                return false;
            }
            return mob.getDistanceSqToEntity(player) <= neutralRadiusSqr;
        }
        return mob instanceof IMob && mob.getDistanceSqToEntity(player) <= hostileRadiusSqr;
    }

    /**
     * 1.7.10 的「中立生物」近似集合（上游用 1.20 的 {@code NeutralMob} 接口）。
     */
    private static boolean isNeutralMob(EntityLiving mob) {
        return mob instanceof EntityTameable || mob instanceof EntityIronGolem || mob instanceof EntityPigZombie;
    }
}
