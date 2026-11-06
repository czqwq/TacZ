package com.tacz.guns.api.event.common;

import com.tacz.guns.compat.kubejs.TimelessKubeJSPlugin;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * KubeJS 事件转发钩子。
 * <p>
 * 上游（1.20.1）在事件构造器里把事件转发给 KubeJS 的 EventJS 包装（{@code compat.kubejs.events.*}）。
 * 1.7.10 移植说明：KubeJS 只支持 1.16.5+，这里保留上游「KubeJS 存在才转发」的分支结构，转发目标改为
 * {@link TimelessKubeJSPlugin#postKubeJSEvent(Event)}；{@code LOADED} 在 1.7.10 恒为 false，
 * 行为等于上游「未安装 KubeJS」，不产生任何硬依赖。
 */
public interface KubeJSGunEventPoster<E extends Event> {

    default void postEventToKubeJS(E event) {
        if (TimelessKubeJSPlugin.LOADED) {
            TimelessKubeJSPlugin.postKubeJSEvent(event);
        }
    }

    /**
     * 客户端事件应调用此方法
     */
    default void postClientEventToKubeJS(E event) {
        postEventToKubeJS(event);
    }

    /**
     * 服务端事件应调用此方法
     */
    default void postServerEventToKubeJS(E event) {
        postEventToKubeJS(event);
    }
}
