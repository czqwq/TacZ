package com.tacz.guns.client.particle;

import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;

import org.joml.Vector3f;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.pojo.display.ammo.AmmoParticle;
import com.tacz.guns.entity.EntityKineticBullet;

/**
 * 子弹飞行时的弹道粒子（曳光 / 尾迹）生成器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/particle/AmmoParticleSpawner.java}
 * （上游带 {@code @OnlyIn(Dist.CLIENT)}；1.7.10 无 {@code Dist} 注解体系，本类只被客户端代码调用）。
 *
 * <h2>1.7.10 平台替换</h2>
 * <ul>
 * <li>{@code ParticleOptions} / {@code ParticleEngine#createParticle} → 包内 {@link ParticleCompat#spawnNamed}
 * （1.7.10 的粒子按名字派发，见 {@link HitSurfaceParticleSpawner} 类注释的对照表）；</li>
 * <li>{@code Particle#setLifetime(int)} → {@link ParticleCompat#setLifetime}（1.7.10 只能反射写
 * {@code particleMaxAge}）；</li>
 * <li>{@code Entity#getRandom()} → {@code bullet.worldObj.rand}（见 {@link #createParticle} 的 TODO）；</li>
 * <li>{@code Entity#getDeltaMovement()} → {@code motionX/motionY/motionZ}；
 * {@code Entity#getX/Y/Z()} → {@code posX/posY/posZ}；{@code Entity#distanceToSqr(x,y,z)} →
 * {@code Entity#getDistanceSq(x,y,z)}。</li>
 * </ul>
 */
public class AmmoParticleSpawner {

    /**
     * 上游 {@code addParticle(EntityKineticBullet)}。
     *
     * <p>
     * 注意：{@code EntityKineticBullet#getGunDisplayId()/getGunId()/getAmmoId()} 在 1.7.10 侧已经返回
     * {@code ResourceLocation}（与上游一致），所以这里直接把 {@code ResourceLocation} 交给
     * {@code TimelessAPI}，不需要再做字符串转换。
     */
    public static void addParticle(EntityKineticBullet bullet) {
        if (bullet == null) {
            return;
        }
        TimelessAPI.getGunDisplay(bullet.getGunDisplayId(), bullet.getGunId())
            .ifPresent(gunIndex -> {
                AmmoParticle gunParticle = gunIndex.getParticle();
                if (gunParticle == null) {
                    // 如果枪械没有粒子效果，那么调用子弹的
                    TimelessAPI.getClientAmmoIndex(bullet.getAmmoId())
                        .ifPresent(ammoIndex -> {
                            AmmoParticle ammoParticle = ammoIndex.getParticle();
                            if (ammoParticle == null) {
                                return;
                            }
                            spawnParticle(bullet, ammoParticle);
                        });
                } else {
                    // 否则调用枪械的
                    spawnParticle(bullet, gunParticle);
                }
            });
    }

    private static void spawnParticle(EntityKineticBullet bullet, AmmoParticle particle) {
        // 上游 AmmoParticle#getParticleOptions() 返回 1.20 的 ParticleOptions；1.7.10 侧该字段
        // 已降级为「已校验的粒子名字符串」（见 AmmoParticle 类注释），此处直接按名字派发。
        String particleOptions = particle.getParticleOptions();
        if (particleOptions == null) {
            return;
        }
        int count = particle.getCount();
        Vector3f delta = particle.getDelta();
        float particleSpeed = particle.getSpeed();
        if (count == 0) {
            double xSpeed = particleSpeed * delta.x();
            double ySpeed = particleSpeed * delta.y();
            double zSpeed = particleSpeed * delta.z();
            EntityFX result = ParticleCompat.spawnNamed(
                bullet.worldObj,
                particleOptions,
                bullet.posX,
                bullet.posY,
                bullet.posZ,
                xSpeed,
                ySpeed,
                zSpeed);
            ParticleCompat.setLifetime(result, particle.getLifeTime());
        } else {
            Random random = randomOf(bullet);
            Entity owner = bullet.getOwner();
            for (int i = 0; i < count; ++i) {
                createParticle(bullet, particle, random, delta, particleSpeed, owner, particleOptions);
            }
        }
    }

    private static void createParticle(EntityKineticBullet bullet, AmmoParticle particle, Random random, Vector3f delta,
        float particleSpeed, @Nullable Entity owner, String particleOptions) {
        double deltaMovementRandom = random.nextDouble();
        double offsetX = random.nextGaussian() * delta.x() + deltaMovementRandom * bullet.motionX;
        double offsetY = random.nextGaussian() * delta.y() + deltaMovementRandom * bullet.motionY;
        double offsetZ = random.nextGaussian() * delta.z() + deltaMovementRandom * bullet.motionZ;
        double xSpeed = random.nextGaussian() * particleSpeed;
        double ySpeed = random.nextGaussian() * particleSpeed;
        double zSpeed = random.nextGaussian() * particleSpeed;

        double posX = bullet.posX + offsetX;
        double posY = bullet.posY + offsetY;
        double posZ = bullet.posZ + offsetZ;

        // 如果太贴近发射者，不进行粒子生成
        if (owner == null || owner.getDistanceSq(posX, posY, posZ) > 3 * 3) {
            EntityFX result = ParticleCompat
                .spawnNamed(bullet.worldObj, particleOptions, posX, posY, posZ, xSpeed, ySpeed, zSpeed);
            ParticleCompat.setLifetime(result, particle.getLifeTime());
        }
    }

    /**
     * 上游用 {@code bullet.getRandom()}（1.20 的 {@code Entity#getRandom()}）。
     *
     * <p>
     * TODO(L2): 上游 AmmoParticleSpawner.java:57 取的是<b>子弹实体自己的</b>随机源
     * （{@code Entity#getRandom()}）；1.7.10 的 {@code Entity#rand} 是 {@code protected} 字段、无 getter，
     * 而 {@code EntityKineticBullet}（common 包）没有对外暴露它，此处改用
     * {@code bullet.worldObj.rand}（{@code World#rand}，public）近似。差别只在于随机序列是「整个世界共享」
     * 而不是「每颗子弹独立」——粒子的位置/速度分布形态不变。
     */
    private static Random randomOf(EntityKineticBullet bullet) {
        return bullet.worldObj != null ? bullet.worldObj.rand : new Random();
    }
}
