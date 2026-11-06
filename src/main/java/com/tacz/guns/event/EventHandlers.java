package com.tacz.guns.event;

import com.tacz.guns.event.ammo.BellRing;
import com.tacz.guns.event.ammo.DestroyGlassBlock;

/**
 * {@code event/} 包的统一注册入口。
 * <p>
 * 上游（1.20.1）每个事件类自己用 {@code @Mod.EventBusSubscriber} 自动注册；1.7.10 没有这套机制，
 * 改为 {@code CommonProxy} 在最末调用 {@link #registerAll()}（见 {@code docs/PORT_GAME.md} §1：
 * 「{@code event/} 之外不要自行注册事件，统一经 {@code EventHandlers}」）。
 * <p>
 * 注意 1.7.10 有<b>两个</b>事件总线，注册位置不能混：
 * <ul>
 * <li>{@code MinecraftForge.EVENT_BUS}：{@code PlayerInteractEvent}、{@code LivingHurtEvent}、
 * {@code EntityJoinWorldEvent}、Forge 的 {@code PlayerEvent.*}、TACZ 自己的 {@code GunFireEvent} /
 * {@code AmmoHitBlockEvent} 等；</li>
 * <li>{@code FMLCommonHandler.instance().bus()}：{@code cpw.mods.fml.common.gameevent.TickEvent.*} 与
 * {@code cpw.mods.fml.common.gameevent.PlayerEvent.*}（FML 自己 {@code bus().post} 的）。</li>
 * </ul>
 * 具体注册到哪条总线由各事件类的 {@code register()} 自己决定，本类只负责调用顺序。
 * <p>
 * 调用时机（{@code CommonProxy}）：配置加载（{@code ConfigManager}/{@code TaczConfigSpec.load()}）之后、
 * 其余 {@code init/Mod*.register()} 之后。{@link LoadingConfigEvent#reloadConfig()} 依赖配置已加载，
 * 因此放在这里调用。
 */
public final class EventHandlers {

    private static boolean registered = false;

    private EventHandlers() {}

    /**
     * 注册 {@code event/} 包内全部事件监听器 + 生命周期回调。重复调用无副作用。
     */
    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;

        // —— MinecraftForge.EVENT_BUS ——
        AmmoStorageInteractionEvent.register();
        EntityDamageEvent.register();
        GunDisplayCaseInteractionEvent.register();
        GunPegboardInteractionEvent.register();
        GunSoundAngerEvent.register();
        PreventGunClick.register();
        SyncBaseTimestamp.register();
        // SyncedEntityDataEvent 同时注册到 FML 总线（内部处理）
        SyncedEntityDataEvent.register();
        DestroyGlassBlock.register();
        BellRing.register();
        // 1.7.10 无 LivingKnockBackEvent → 空实现，仅保留统一调用点
        KnockbackChange.register();
        // 枪包战利品注入（1.7.10 无 global loot modifier：箱子 ChestGenHooks / 实体 LivingDrops /
        // 方块 HarvestDrops + 新世界奖励箱 CreateSpawnPosition，见 LootInjectionEvent 类注释）
        LootInjectionEvent.register();

        // —— FMLCommonHandler.instance().bus() ——
        HitboxHelperEvent.register();
        ServerTickEvent.register();
        PlayerRespawnEvent.register();
        TravelToDimensionEvent.register();

        // —— 非事件监听器（上游由 mod 生命周期/配置事件驱动） ——
        CommonLoadPack.loadGunPack();
        LoadingConfigEvent.reloadConfig();
    }
}
