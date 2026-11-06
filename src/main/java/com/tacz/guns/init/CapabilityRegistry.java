package com.tacz.guns.init;

/**
 * 移植说明：{@code Capability} 注册入口。
 *
 * <p>
 * <b>上游形态</b>（{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/CapabilityRegistry.java}）：
 * 
 * <pre>
 * &#64;Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
 * public class CapabilityRegistry {
 * 
 *     &#64;SubscribeEvent
 *     public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
 *         event.register(DataHolder.class); // 把 entity/sync/core/DataHolder 注册成 capability
 *     }
 * }
 * </pre>
 *
 * <p>
 * <b>1.7.10 形态</b>：1.7.10 <b>没有 Capability 系统</b>（{@code Capability}/{@code ICapabilityProvider}/
 * {@code RegisterCapabilitiesEvent} 都是 1.8.9+ 才有的）。本工程的实体同步改用
 * 「按实体 id 的静态管理器」（{@code entity/sync/core/SyncedEntityData}，见 common-game 的实现），
 * 不再需要 capability 承载 {@code DataHolder}。
 *
 * <p>
 * 因此本类只保留类名与一个空实现的 {@link #register()}，由 {@code CommonProxy#preInit} 调用（保持上游
 * 「注册顺序里有一个 capability 环节」的形状，便于日后对照）。<b>丢掉的语义</b>：上游第三方模组可以通过
 * capability 查询/挂载 {@code DataHolder}；1.7.10 侧只能通过 {@code SyncedEntityData.instance()} 直连。
 */
public class CapabilityRegistry {

    private CapabilityRegistry() {}

    /**
     * 等价上游 {@code onRegisterCapabilities(RegisterCapabilitiesEvent)}：1.7.10 无事可做（无 capability 系统）。
     */
    public static void register() {
        // no-op：见类注释
    }
}
