package com.tacz.guns.client.sound;

import java.lang.ref.WeakReference;

import javax.annotation.Nullable;

import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.mcshim.SoundEvent;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 跟随实体位置的世界音效（用于第三人称枪声等）。
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/sound/EntityTrackingGunSoundInstance.java}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 上游实现 {@code TickableSoundInstance}（{@code tick()} / {@code isStopped()} / {@code setStop()}）；
 * 1.7.10 的对应接口是 {@link ITickableSound}（{@code extends ISound, IUpdatePlayerListBox}）：
 * <ul>
 * <li>{@code tick()} → {@link #update()}（{@code IUpdatePlayerListBox#update()}，由
 * {@code SoundManager#updateAllSounds} 每 tick 调用）</li>
 * <li>{@code isStopped()} → {@link #isDonePlaying()}（{@code ITickableSound#isDonePlaying()}）</li>
 * <li>{@code setStop()} → 同名，覆写父类 {@link GunSoundInstance#setStop()}</li>
 * </ul>
 * 参考现成实现：{@code net.minecraft.client.audio.MovingSound}/{@code MovingSoundMinecart}。
 */
// TODO(L2): 上游 EntityTrackingGunSoundInstance.java:14 `extends GunSoundInstance implements TickableSoundInstance`
// （tick()/isStopped()/setStop()）；1.7.10 无 TickableSoundInstance，此处改用
// net.minecraft.client.audio.ITickableSound（tick()→update()、isStopped()→isDonePlaying()）。
@SideOnly(Side.CLIENT)
public class EntityTrackingGunSoundInstance extends GunSoundInstance implements ITickableSound {

    private final WeakReference<Entity> entityRef;
    private boolean stopped;

    public EntityTrackingGunSoundInstance(SoundEvent soundEvent, SoundCategory source, float volume, float pitch,
        Entity entity, int soundDistance, @Nullable ResourceLocation registryName, boolean mono, boolean useTaczSound) {
        super(soundEvent, source, volume, pitch, entity, soundDistance, registryName, mono, false, useTaczSound);
        this.entityRef = new WeakReference<>(entity);
    }

    @Override
    public boolean canPlaySound() {
        Entity entity = this.entityRef.get();
        return super.canPlaySound() && entity != null && !entity.isDead;
    }

    @Override
    public boolean isDonePlaying() {
        return this.stopped;
    }

    @Override
    public void update() {
        Entity entity = this.entityRef.get();
        // 上游 tick()：实体被移除或已死亡则停止。
        // 1.7.10 的 Entity#isRemoved() → isDead；LivingEntity#isDeadOrDying() → EntityLivingBase#isEntityAlive() 取反
        // （1.7.10 的 isEntityAlive = !isDead && getHealth() > 0）。
        if (entity == null || entity.isDead
            || entity instanceof EntityLivingBase livingEntity && !livingEntity.isEntityAlive()) {
            this.setStop();
            return;
        }
        this.xPosF = (float) entity.posX;
        // 上游 entity.getEyeY()；1.7.10 没有 getEyeY()，等价物是 posY + getEyeHeight()。
        this.yPosF = (float) (entity.posY + entity.getEyeHeight());
        this.zPosF = (float) entity.posZ;
    }

    @Override
    public void setStop() {
        this.stopped = true;
        super.setStop();
    }
}
