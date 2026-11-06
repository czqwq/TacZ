package com.tacz.guns.client.resource.pojo.display.ammo;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.google.gson.annotations.SerializedName;

public class AmmoParticle {

    private static final Vector3f ZERO = new Vector3f(0, 0, 0);
    @SerializedName("name")
    private String name;

    @SerializedName("delta")
    private Vector3f delta = ZERO;

    @SerializedName("speed")
    private float speed = 0f;

    @SerializedName("life_time")
    private int lifeTime = 20;

    @SerializedName("count")
    private int count = 1;

    // 不进行序列化，而是需要 deco 的
    // TODO(port): 上游 AmmoParticle.java:27 是 transient net.minecraft.core.particles.ParticleOptions
    // （1.20 把 "name" 解析成「粒子类型 + 参数」的对象）。1.7.10 没有 ParticleOptions，
    // 粒子一律按名字字符串生成（World#spawnParticle(String, ...) / ParticleEngine 按名字查 EntityFX），
    // 因此这里降级为保存已校验的粒子名字符串；生成端直接按名字调用即可。
    private transient String particleOptions;

    public String getName() {
        return name;
    }

    public Vector3f getDelta() {
        return delta;
    }

    public float getSpeed() {
        return speed;
    }

    public int getCount() {
        return count;
    }

    public int getLifeTime() {
        return lifeTime;
    }

    @Nullable
    public String getParticleOptions() {
        return particleOptions;
    }

    public void setParticleOptions(String particleOptions) {
        this.particleOptions = particleOptions;
    }
}
