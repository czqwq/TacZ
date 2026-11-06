package com.tacz.guns.client.init;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 粒子「工厂」注册。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/init/ParticleFactoryRegistry.java}，
 * 订阅 {@code RegisterParticleProvidersEvent} 并
 * {@code event.registerSpecial(ModParticles.BULLET_HOLE.get(), new BulletHoleParticle.Provider())}。
 *
 * <p>
 * <b>1.7.10 无对应注册表</b>（这是真实的能力缺失，不是偷懒）：
 * <ul>
 * <li>1.20.1 的 {@code ParticleType}/{@code ParticleOptions}/{@code ParticleProvider} 三元组在 1.7.10
 * <b>完全不存在</b>。1.7.10 只有 {@code net.minecraft.client.particle.EffectRenderer} 的
 * {@code addEffect(EntityFX)}，以及 {@code World#spawnParticle(String, ...)} 的「按字符串名生成原版粒子」；</li>
 * <li>1.7.10 <b>没有</b>自定义粒子的注册/工厂机制（{@code IParticleEffect} 是 1.8 才引入的），
 * 也没有粒子数据包（{@code data/tacz/particles/**} 在 1.7.10 无承载物）。</li>
 * </ul>
 *
 * <p>
 * <b>移植特有条件</b>：TACZ 的弹孔/枪口粒子由 {@code client/particle/**} 里的类<b>直接实例化</b>
 * （{@code Minecraft.getMinecraft().effectRenderer.addEffect(new BulletHoleParticle(...))}）
 * 或由 {@code client/particle/HitBlockEffectHandler} 等入口按需生成，
 * 因此本类在 1.7.10 侧保持零逻辑，只作为上游注册点的定位锚点保留。
 *
 * <p>
 * 对应的 {@code ModParticles} 注册类已由 {@code common-game} 降级为空实现
 * （1.7.10 无 {@code ParticleType} 注册表，见 {@code docs/PORT_STATUS.md}）。
 */
@SideOnly(Side.CLIENT)
public final class ParticleFactoryRegistry {

    private ParticleFactoryRegistry() {}

    /** 对应上游 {@code RegisterParticleProvidersEvent}；1.7.10 无对应物，见类 javadoc。 */
    public static void register() {
        // 1.7.10 无自定义粒子工厂注册表：粒子由 client/particle/** 直接实例化后交给 EffectRenderer
    }
}
