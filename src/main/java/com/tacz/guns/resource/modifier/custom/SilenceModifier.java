package com.tacz.guns.resource.modifier.custom;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.attachment.Modifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;

public class SilenceModifier implements IAttachmentModifier<Pair<Modifier, Boolean>, Pair<Integer, Boolean>> {

    public static final String ID = GunProperties.SILENCE.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    @SuppressWarnings("deprecation")
    public SilenceJsonProperty readJson(String json) {
        SilenceModifier.Data data = CommonAssetsManager.GSON.fromJson(json, SilenceModifier.Data.class);
        Silence silence = data.getSilence();
        if (silence == null) {
            return new SilenceJsonProperty(Pair.of(new Modifier(), false));
        }
        Modifier distance = silence.getDistance();
        // 兼容旧版本
        if (distance == null) {
            distance = new Modifier();
            distance.setAddend(silence.getDistanceAddend());
        }
        return new SilenceJsonProperty(Pair.of(distance, silence.isUseSilenceSound()));
    }

    @Override
    public CacheValue<Pair<Integer, Boolean>> initCache(ItemStack gunItem, GunData gunData) {
        int defaultDistance = GunConfig.DEFAULT_GUN_FIRE_SOUND_DISTANCE.get();
        return new CacheValue<>(Pair.of(defaultDistance, false));
    }

    @Override
    public void eval(List<Pair<Modifier, Boolean>> modifiedValues, CacheValue<Pair<Integer, Boolean>> cache) {
        List<Modifier> distanceModifiers = Lists.newArrayList();
        List<Boolean> useSilenceSoundModifiers = Lists.newArrayList();
        modifiedValues.forEach(v -> {
            distanceModifiers.add(v.getLeft());
            useSilenceSoundModifiers.add(v.getRight());
        });
        Pair<Integer, Boolean> cacheValue = cache.getValue();
        double evalDistance = AttachmentPropertyManager.eval(distanceModifiers, cacheValue.getLeft());
        boolean useSilenceSound = AttachmentPropertyManager.eval(useSilenceSoundModifiers, cacheValue.getRight());
        cache.setValue(Pair.of((int) Math.round(evalDistance), useSilenceSound));
    }

    public static class SilenceJsonProperty extends JsonProperty<Pair<Modifier, Boolean>> {

        public SilenceJsonProperty(Pair<Modifier, Boolean> value) {
            super(value);
        }

        @Override
        public void initComponents() {
            Pair<Modifier, Boolean> value = this.getValue();
            if (value != null) {
                int defaultDistance = GunConfig.DEFAULT_GUN_FIRE_SOUND_DISTANCE.get();
                double eval = AttachmentPropertyManager.eval(value.getLeft(), defaultDistance);
                int distance = (int) Math.round(eval);
                if (distance > defaultDistance) {
                    components.add(
                        new ChatComponentTranslation("tooltip.tacz.attachment.sound_distance.increase")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
                } else if (distance < defaultDistance) {
                    components.add(
                        new ChatComponentTranslation("tooltip.tacz.attachment.sound_distance.increase")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
                }
                if (value.getRight()) {
                    components.add(
                        new ChatComponentTranslation("tooltip.tacz.attachment.silence")
                            .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)));
                }
            }
        }
    }

    private static class Data {

        @Nullable
        @SerializedName("silence")
        private Silence silence = null;

        @Nullable
        public Silence getSilence() {
            return silence;
        }
    }

    private static class Silence {

        @Deprecated
        @SerializedName("distance_addend")
        private int distanceAddend = 0;

        @Nullable
        @SerializedName("distance")
        private Modifier distance = null;

        @SerializedName("use_silence_sound")
        private boolean useSilenceSound = false;

        @Deprecated
        public int getDistanceAddend() {
            return distanceAddend;
        }

        @Nullable
        public Modifier getDistance() {
            return distance;
        }

        public boolean isUseSilenceSound() {
            return useSilenceSound;
        }
    }
}
