package com.tacz.guns.resource.serialize;

import java.io.IOException;

import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * {@code ResourceLocation} 的 gson 适配器。
 *
 * <p>
 * 上游直接用原版自带的 {@code ResourceLocation.Serializer}
 * （{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/resource/CommonAssetsManager.java:58}）。
 * <b>1.7.10 差异</b>：{@code net.minecraft.util.ResourceLocation} 没有内置 gson 适配器，
 * 因此这里按原版 {@code ResourceLocation.Serializer} 的语义手写一份：
 * 序列化为 {@code "namespace:path"} 字符串，反序列化只接受 {@code "namespace:path"} 形式。
 */
public class ResourceLocationSerializer extends TypeAdapter<ResourceLocation> {

    @Override
    public void write(JsonWriter out, ResourceLocation value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }

    @Override
    public ResourceLocation read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String raw = in.nextString();
        // 1.20 语义：必须含 ':'，否则视为非法
        if (raw == null || !raw.contains(":")) {
            throw new JsonSyntaxException("Invalid resource location: " + raw);
        }
        return new ResourceLocation(raw);
    }
}
