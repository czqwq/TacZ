package com.tacz.guns.client.resource.serialize;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.google.gson.*;
import com.tacz.guns.client.resource.pojo.animation.bedrock.ParticleEffectKeyframes;
import com.tacz.guns.mcshim.GsonHelper;

/**
 * 解析动画 JSON 中的 {@code "particle_effects"}：
 * 
 * <pre>
 * "particle_effects": {
 *     "0.0": { "effect": "tacz:muzzle_flash_rifle", "locator": "muzzle_flash" },
 *     "0.25": "tacz:reload_smoke"
 * }
 * </pre>
 * 
 * 值为字符串时仅指定效果 id，locator 留空（渲染时回退到枪口骨骼）。
 */
public class ParticleEffectKeyframesSerializer implements JsonDeserializer<ParticleEffectKeyframes> {

    @Override
    public ParticleEffectKeyframes deserialize(JsonElement json, Type type, JsonDeserializationContext context)
        throws JsonParseException {
        TreeMap<Double, ParticleEffectData> keyframes = new TreeMap<>();
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                double time;
                try {
                    time = Double.parseDouble(entry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                JsonElement value = entry.getValue();
                ResourceLocation effect = null;
                String locator = null;
                String preEffectScript = null;
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive()
                    .isString()) {
                    effect = tryParse(value.getAsString());
                } else if (value.isJsonObject()) {
                    JsonObject obj = value.getAsJsonObject();
                    effect = tryParse(GsonHelper.getAsString(obj, "effect"));
                    if (obj.has("locator")) {
                        locator = GsonHelper.getAsString(obj, "locator");
                    }
                    if (obj.has("pre_effect_script")) {
                        preEffectScript = GsonHelper.getAsString(obj, "pre_effect_script");
                    }
                }
                if (effect != null) {
                    keyframes.put(time, new ParticleEffectData(effect, locator, preEffectScript));
                }
            }
        }
        return new ParticleEffectKeyframes(keyframes);
    }

    /**
     * TODO(port): 上游 ParticleEffectKeyframesSerializer.java:41 用 {@code ResourceLocation.tryParse}（1.20 API）。
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
