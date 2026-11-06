package com.tacz.guns.api.client.animation;

import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.client.sound.SoundPlayManager;

public class ObjectAnimationSoundChannel {

    public AnimationSoundChannelContent content;

    public ObjectAnimationSoundChannel() {}

    public ObjectAnimationSoundChannel(AnimationSoundChannelContent content) {
        this.content = content;
    }

    /**
     * 播放区间内的所有声音。时间区间左开右闭
     */
    public void playSound(double fromTimeS, double toTimeS, Entity entity, int distance, float volume, float pitch) {
        if (content == null) {
            return;
        }
        if (fromTimeS == toTimeS) {
            return;
        }
        if (fromTimeS > toTimeS && fromTimeS <= getEndTimeS()) {
            playSound(0, toTimeS, entity, distance, volume, pitch);
            toTimeS = getEndTimeS();
        }
        int to = computeIndex(toTimeS, false);
        int from = computeIndex(fromTimeS, true);
        float mixVolume = volume;
        // 根据实体位置计算音量
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            // 1.7.10 无 Entity#distanceToSqr(Vec3)/getPosition(float)（部分插值坐标），改用实体间距离平方（不插值）。
            mixVolume = mixVolume
                * (1.0F - Math.min(1.0F, (float) Math.sqrt(player.getDistanceSqToEntity(entity)) / distance));
            mixVolume *= mixVolume;
        }
        for (int i = from + 1; i <= to; i++) {
            ResourceLocation name = content.keyframeSoundName[i];
            SoundPlayManager.playAnimationSound(entity, name, mixVolume, pitch, distance);
        }
    }

    public double getEndTimeS() {
        return content.keyframeTimeS[content.keyframeTimeS.length - 1];
    }

    private int computeIndex(double timeS, boolean open) {
        int index = Arrays.binarySearch(content.keyframeTimeS, timeS);
        if (index >= 0) {
            return open ? index - 1 : index;
        }
        return -index - 2;
    }
}
