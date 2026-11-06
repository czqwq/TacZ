package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.TargetMinecart;

import cpw.mods.fml.common.registry.EntityRegistry;

/**
 * 搬运自上游 1.20.1 {@code init/ModEntities.java}。
 *
 * <p>
 * 1.7.10 适配：{@code DeferredRegister<EntityType<?>>} → {@link EntityRegistry#registerModEntity}。
 * 1.7.10 没有 {@code EntityType} 对象：实体 id/分类/tracking 范围/更新频率/是否同步速度
 * 都在 {@code registerModEntity} 参数里表达。上游 {@code EntityType.Builder} 的参数对应关系：
 * bullet = MISC、trackingRange 5、updateInterval 5、不同步速度（<b>但子弹需要速度</b>，
 * 上游实体本身不发速度包，速度由 spawn 数据带给客户端，这里按上游语义 sendVelocityUpdates=false）；
 * target_minecart = MISC、trackingRange 8、速度同步 true。
 *
 * <p>
 * <b>trackingRange 的单位不同（已修正的历史 bug）</b>：
 * 1.20 的 {@code EntityType.Builder#clientTrackingRange(int)} 单位是<b>区块</b>（5 → 80 格、8 → 128 格），
 * 而 1.7.10 的 {@code EntityTracker#addEntityToTracker(entity, int range, int updateFreq, boolean velocity)}
 * 单位是<b>方块</b>（原版同类实体：箭 64 格/20 tick/不发速度，矿车 80 格/3 tick/发速度，
 * 见 build/rfg/minecraft-src/java/net/minecraft/entity/EntityTracker.java:89-93、135-138）。
 * 早期移植把区块数直接当成方块数用（bullet=5、minecart=8），结果是子弹飞出 5 格、
 * 标靶车开出 8 格就停止向客户端同步（曳光弹/标靶凭空消失）。
 * 这里按 1 区块 = 16 格换算：bullet 5×16=80，target_minecart 8×16=128。
 */
public class ModEntities {

    /** 上游 {@code EntityKineticBullet.TYPE} 的 {@code clientTrackingRange(5)}（区块）→ 1.7.10 的方块数。 */
    private static final int BULLET_TRACKING_RANGE_BLOCKS = 5 * 16;
    /** 上游 {@code TargetMinecart.TYPE} 的 {@code clientTrackingRange(8)}（区块）→ 1.7.10 的方块数。 */
    private static final int TARGET_MINECART_TRACKING_RANGE_BLOCKS = 8 * 16;
    /** 上游 {@code updateInterval(5)}（tick）→ 1.7.10 的 {@code updateFrequency}。 */
    private static final int BULLET_UPDATE_FREQUENCY = 5;

    public static void register() {
        EntityRegistry.registerModEntity(
            EntityKineticBullet.class,
            "bullet",
            0,
            GunMod.instance,
            BULLET_TRACKING_RANGE_BLOCKS,
            BULLET_UPDATE_FREQUENCY,
            false);
        // 1.20 的 EntityType.Builder 默认 updateInterval = 3，与原版 1.7.10 矿车一致
        EntityRegistry.registerModEntity(
            TargetMinecart.class,
            "target_minecart",
            1,
            GunMod.instance,
            TARGET_MINECART_TRACKING_RANGE_BLOCKS,
            3,
            true);
    }
}
