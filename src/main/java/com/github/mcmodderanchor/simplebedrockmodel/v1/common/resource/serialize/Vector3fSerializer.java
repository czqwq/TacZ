// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: v1/common/resource/serialize/Vector3fSerializer.java
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.serialize;

import java.lang.reflect.Type;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.tacz.guns.mcshim.GsonHelper;

public class Vector3fSerializer implements JsonDeserializer<Vector3f>, JsonSerializer<Vector3f> {

    @Override
    public Vector3f deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            JsonElement xElement = array.get(0);
            JsonElement yElement = array.get(1);
            JsonElement zElement = array.get(2);
            float x = GsonHelper.convertToFloat(xElement, "(array i=0)");
            float y = GsonHelper.convertToFloat(yElement, "(array i=1)");
            float z = GsonHelper.convertToFloat(zElement, "(array i=2)");
            return new Vector3f(x, y, z);
        } else {
            throw new JsonSyntaxException("Expected " + json + " to be a Vector3f because it's not an array");
        }
    }

    @Override
    public JsonElement serialize(Vector3f src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive(src.x()));
        array.add(new JsonPrimitive(src.y()));
        array.add(new JsonPrimitive(src.z()));
        return array;
    }
}
