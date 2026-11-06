package com.tacz.guns.client.resource.serialize;

import java.lang.reflect.Type;

import org.joml.Vector3f;

import com.google.gson.*;
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
        // TODO(port): 上游 Vector3fSerializer.java:28 用 new JsonArray(3) + JsonArray#set(0/1/2, ...)；
        // 1.7.10 自带的 gson 2.2.4 没有带容量的构造，也没有 JsonArray#set，改为按顺序 add，序列化结果完全一致。
        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive(src.x()));
        array.add(new JsonPrimitive(src.y()));
        array.add(new JsonPrimitive(src.z()));
        return array;
    }
}
