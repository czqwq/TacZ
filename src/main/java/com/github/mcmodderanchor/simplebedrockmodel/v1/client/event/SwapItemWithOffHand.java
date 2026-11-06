// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/client/event/SwapItemWithOffHand.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.client.event;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * TODO(L2): 上游继承 1.20 的 {@code net.minecraftforge.eventbus.api.Event}；1.7.10 的 Forge 事件基类是
 * {@link Event}（{@code cpw.mods.fml.common.eventhandler.Event}），语义相同（可 post 到
 * {@code MinecraftForge.EVENT_BUS}）。事件本身无字段，纯标记事件。
 */
public class SwapItemWithOffHand extends Event {

    public SwapItemWithOffHand() {}
}
