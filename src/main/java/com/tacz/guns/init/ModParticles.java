package com.tacz.guns.init;

/**
 * 搬运自上游 1.20.1 {@code init/ModParticles.java}。
 *
 * <p>
 * <b>1.7.10 无粒子注册表</b>（没有 {@code ParticleType}/{@code ParticleOptions}/Codec）：
 * 1.7.10 的粒子是客户端 {@code EntityFX} + {@code World#spawnParticle(String,...)} 字符串名机制，
 * 服务端不需要注册。弹孔粒子数据类 {@code particles/BulletHoleOption} 已改为纯数据类，
 * 由 L2 客户端在弹孔效果处理器里按需构造 {@code EntityFX}。
 * 因此本类 {@code register()} 为空实现（保留 CommonProxy 调用点顺序）。
 * 上游依据：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/ModParticles.java}。
 */
public class ModParticles {

    public static void register() {
        // 1.7.10 无 ParticleType 注册表；客户端 EntityFX 由 client-port 在 L2 处理
    }
}
