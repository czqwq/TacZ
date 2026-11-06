package com.tacz.guns.api.client.event;

import com.tacz.guns.api.event.common.KubeJSGunEventPoster;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * 玩家交换主副手物品时触发该事件
 */
public class SwapItemWithOffHand extends Event implements KubeJSGunEventPoster<SwapItemWithOffHand> {

    public SwapItemWithOffHand() {
        postClientEventToKubeJS(this);
    }
}
