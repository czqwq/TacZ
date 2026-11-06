// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: (new file - 1.7.10 port support, no direct upstream counterpart)
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.serialize;

import java.lang.reflect.Type;

import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.tacz.guns.mcshim.GsonHelper;

/**
 * 1.20.1 的 {@code net.minecraft.resources.ResourceLocation.Serializer} 在 1.7.10 不存在
 * （1.7.10 的 {@link ResourceLocation} 只是一个 domain+path 的不可变值对象，没有自带 Gson 适配器）。
 * <p>
 * 本类逐字复刻 1.20.1 {@code ResourceLocation.Serializer} 的行为：序列化成 {@code "namespace:path"} 字符串，
 * 反序列化时用 {@link ResourceLocation#ResourceLocation(String)} 解析（与 1.20 的构造语义一致：
 * 没有冒号时 namespace 取 {@code minecraft}）。
 * <p>
 * TODO(L2): 上游 {@code GsonUtil} 用 {@code new ResourceLocation.Serializer()}；1.7.10 无该嵌套类，
 * 改为本类。行为一致，唯一差别是 1.20 的构造会做 path/namespace 的字符校验并抛
 * {@code ResourceLocationException}，而 1.7.10 的构造不校验（非法字符会静默保留）。
 */
public class ResourceLocationSerializer
    implements JsonDeserializer<ResourceLocation>, JsonSerializer<ResourceLocation> {

    @Override
    public ResourceLocation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        return new ResourceLocation(GsonHelper.convertToString(json, "location"));
    }

    @Override
    public JsonElement serialize(ResourceLocation src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }
}
