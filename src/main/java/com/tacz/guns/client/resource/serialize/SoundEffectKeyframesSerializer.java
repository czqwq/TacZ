package com.tacz.guns.client.resource.serialize;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.google.gson.*;
import com.tacz.guns.client.resource.pojo.animation.bedrock.SoundEffectKeyframes;
import com.tacz.guns.mcshim.GsonHelper;

@SuppressWarnings("ALL")
public class SoundEffectKeyframesSerializer implements JsonDeserializer<SoundEffectKeyframes> {

    @Override
    public SoundEffectKeyframes deserialize(JsonElement json, Type type, JsonDeserializationContext context)
        throws JsonParseException {
        TreeMap<Double, ResourceLocation> keyframes = new TreeMap<>();
        // 如果是对象
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entrySet : jsonObject.entrySet()) {
                double time = Double.parseDouble(entrySet.getKey());
                JsonElement value = entrySet.getValue();
                if (value.isJsonObject()) {
                    String soundId = GsonHelper.getAsString(value.getAsJsonObject(), "effect");
                    ResourceLocation soundLocation = tryParse(soundId);
                    if (soundLocation != null) {
                        keyframes.put(time, soundLocation);
                    }
                }
            }
            return new SoundEffectKeyframes(keyframes);
        }
        return new SoundEffectKeyframes(keyframes);
    }

    /**
     * TODO(port): 上游 SoundEffectKeyframesSerializer.java:25 用 {@code ResourceLocation.tryParse}（1.20 API）。
     * 1.7.10 没有 tryParse，约定用 try/catch 包 {@code new ResourceLocation(s)}，解析失败返回 null。
     */
    @Nullable
    private static ResourceLocation tryParse(@Nullable String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(location);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
