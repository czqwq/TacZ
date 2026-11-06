package com.tacz.guns.resource.serialize;

import java.lang.reflect.Type;

import net.minecraft.util.Vec3;

import com.google.gson.*;
import com.tacz.guns.mcshim.GsonHelper;

public class Vec3Serializer implements JsonDeserializer<Vec3>, JsonSerializer<Vec3> {

    @Override
    public Vec3 deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            JsonElement xElement = array.get(0);
            JsonElement yElement = array.get(1);
            JsonElement zElement = array.get(2);
            double x = GsonHelper.convertToDouble(xElement, "(array i=0)");
            double y = GsonHelper.convertToDouble(yElement, "(array i=1)");
            double z = GsonHelper.convertToDouble(zElement, "(array i=2)");
            return Vec3.createVectorHelper(x, y, z);
        } else {
            throw new JsonSyntaxException("Expected " + json + " to be a Vec3 because it's not an array");
        }
    }

    @Override
    public JsonElement serialize(Vec3 src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive(src.xCoord));
        array.add(new JsonPrimitive(src.yCoord));
        array.add(new JsonPrimitive(src.zCoord));
        return array;
    }
}
