// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/serialize/ParticleEffectKeyframesSerializer.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.serialize;

import java.lang.reflect.Type;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectKeyframes;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.mcshim.GsonHelper;

import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;

public class ParticleEffectKeyframesSerializer implements JsonDeserializer<ParticleEffectKeyframes> {

    @Override
    public ParticleEffectKeyframes deserialize(JsonElement json, Type type, JsonDeserializationContext context)
        throws JsonParseException {
        Double2ObjectRBTreeMap<ParticleEffectData> keyframes = new Double2ObjectRBTreeMap<>();
        if (json != null && json.isJsonObject()) {
            JsonObject jsonObject = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                double time = Double.parseDouble(entry.getKey());
                JsonElement value = entry.getValue();
                if (!value.isJsonObject()) {
                    continue;
                }
                JsonObject object = value.getAsJsonObject();
                ResourceLocation effect = new ResourceLocation(GsonHelper.getAsString(object, "effect"));
                String locator = GsonHelper.getAsString(object, "locator", "");
                String preEffectScript = GsonHelper.getAsString(object, "pre_effect_script", "");
                keyframes.put(time, new ParticleEffectData(effect, locator, preEffectScript));
            }
        }
        return new ParticleEffectKeyframes(keyframes);
    }
}
