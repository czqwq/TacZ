// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/serialize/SoundEffectKeyframesSerializer.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.serialize;

import java.lang.reflect.Type;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.SoundEffectKeyframes;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.mcshim.GsonHelper;

import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;

@SuppressWarnings("ALL")
public class SoundEffectKeyframesSerializer implements JsonDeserializer<SoundEffectKeyframes> {

    @Override
    public SoundEffectKeyframes deserialize(JsonElement json, Type type, JsonDeserializationContext context)
        throws JsonParseException {
        Double2ObjectRBTreeMap<ResourceLocation> keyframes = new Double2ObjectRBTreeMap<>();
        // 如果是对象
        if (json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entrySet : jsonObject.entrySet()) {
                double time = Double.parseDouble(entrySet.getKey());
                JsonElement value = entrySet.getValue();
                if (value.isJsonObject()) {
                    String soundId = GsonHelper.getAsString(value.getAsJsonObject(), "effect");
                    keyframes.put(time, new ResourceLocation(soundId));
                }
            }
            return new SoundEffectKeyframes(keyframes);
        }
        return new SoundEffectKeyframes(keyframes);
    }
}
