package com.tacz.guns.api.client.event;

import com.tacz.guns.api.event.common.KubeJSGunEventPoster;
import com.tacz.guns.mcshim.PoseStack;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * 在调用 ItemInHandRenderer#renderHandsWithItems 方法时触发该事件
 * 用于相机动画相关调用
 */
public class BeforeRenderHandEvent extends Event implements KubeJSGunEventPoster<BeforeRenderHandEvent> {

    private final PoseStack poseStack;

    public BeforeRenderHandEvent(PoseStack poseStack) {
        this.poseStack = poseStack;
        postClientEventToKubeJS(this);
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }
}
