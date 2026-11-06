package com.tacz.guns.init;

/**
 * 搬运自上游 1.20.1 {@code init/ModLootModifiers.java}。
 *
 * <p>
 * <b>1.7.10 无 global loot modifier 体系</b>（没有 {@code LootModifier}/{@code IGlobalLootModifier}/
 * {@code LootContext}，也没有 {@code data/tacz/loot_modifiers} 数据包）：
 * 上游 {@code tacz:loot_table_injector} 的「往指定战利品表注入物品」语义在 1.7.10
 * 由 {@code loot/LootTableInjectorModifier} 改成**掉落事件注入器**（{@code LivingDropsEvent} 等）实现，
 * 不注册 Forge 序列化器。因此本类 {@code register()} 为空实现（保留 CommonProxy 调用点顺序）。
 *
 * <p>
 * 上游依据：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/init/ModLootModifiers.java}。
 */
public class ModLootModifiers {

    public static void register() {
        // 1.7.10 无 global loot modifier 注册表；掉落注入由 loot/LootTableInjectorModifier 的事件实现承担
    }
}
