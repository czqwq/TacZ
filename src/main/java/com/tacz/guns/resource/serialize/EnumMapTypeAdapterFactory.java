package com.tacz.guns.resource.serialize;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * {@link EnumMap} 的 gson 反序列化适配器。
 *
 * <p>
 * <b>为什么需要（1.7.10 平台差异，不是数据问题）</b>：1.7.10 运行时携带的 gson 是 <b>2.2.4</b>
 * （Minecraft 1.7.10 内置），它的 {@code ConstructorConstructor#newDefaultImplementationConstructor}
 * 只给 Map 字段构造 {@code LinkedHashMap}，<b>没有</b> {@code EnumMap} 分支；于是
 * {@code ReflectiveTypeAdapterFactory} 把 {@code LinkedHashMap} 赋给 {@code EnumMap} 字段时抛
 * {@code IllegalArgumentException: Can not set java.util.EnumMap field ... to java.util.LinkedHashMap}，
 * 整个 JSON 装载失败（实测：14 个枪械 {@code data/*_data.json} 的 {@code fireModeAdjust}、
 * 以及客户端 {@code display/guns/*.json} 的 {@code controllableData}）。
 * 上游 1.20.1 用的是 gson 2.10，原生支持 EnumMap，所以这是移植差异。
 *
 * <p>
 * <b>做法</b>：注册到 {@link com.tacz.guns.resource.CommonAssetsManager#GSON} 与
 * {@link com.tacz.guns.client.resource.ClientAssetsManager#GSON} 的 {@link TypeAdapterFactory} 链上，
 * 对所有声明类型为 {@code EnumMap<K extends Enum, V>} 的字段生效：从 {@code ParameterizedType}
 * 取 K/V 的 adapter，读出真实的 {@code EnumMap}（key 用现有 enum adapter，保留 {@code @SerializedName}
 * 语义）。
 *
 * <p>
 * 只处理「声明类型就是 EnumMap」的字段；声明为 {@code Map<K,V>} 的字段仍由 gson 默认链路构造
 * {@code LinkedHashMap}（字段类型兼容，本来就不会报错）。
 */
public final class EnumMapTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Class<? super T> rawType = typeToken.getRawType();
        if (rawType != EnumMap.class) {
            return null;
        }
        Type type = typeToken.getType();
        if (!(type instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length != 2) {
            return null;
        }
        TypeToken<?> keyType = TypeToken.get(arguments[0]);
        if (!keyType.getRawType()
            .isEnum()) {
            // 非 enum key 的 EnumMap 在 Java 里本身就无法构造，交给默认链路报错即可。
            return null;
        }
        TypeToken<?> valueType = TypeToken.get(arguments[1]);
        // 统一擦除成 Object 适配器：EnumMap 的 K/V 由 ParameterizedType 给出，读写端都是 Object，
        // 避免 gson 2.2.4 的 TypeAdapter<?> 通配符在 toJsonTree/write/read 上的 capture 不兼容。
        TypeAdapter<Object> keyAdapter = (TypeAdapter<Object>) gson.getAdapter(keyType);
        TypeAdapter<Object> valueAdapter = (TypeAdapter<Object>) gson.getAdapter(valueType);
        Class<? extends Enum<?>> keyClass = (Class<? extends Enum<?>>) keyType.getRawType();

        return new TypeAdapter<T>() {

            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }
                out.beginObject();
                for (Map.Entry<?, ?> entry : ((EnumMap<?, ?>) value).entrySet()) {
                    // 用 key adapter 序列化出 JSON 名（保留 enum 上的 @SerializedName）
                    JsonElement keyElement = keyAdapter.toJsonTree(entry.getKey());
                    out.name(keyElement.getAsString());
                    valueAdapter.write(out, entry.getValue());
                }
                out.endObject();
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                // 实际类型必须是 EnumMap（字段反射 set 时要做 instanceof 校验），这里用 Map 变量规避擦除。
                Map<Object, Object> map = new EnumMap(keyClass);
                in.beginObject();
                while (in.hasNext()) {
                    // 注意：JSON object 的 key 是 NAME token，不能直接 keyAdapter.read(in)（gson 的
                    // EnumTypeAdapter#read 走 nextString，在 NAME 上会抛 "Expected a string but was NAME"）。
                    // 先取 name，再把它当 JSON 字符串交给 key adapter，保留 @SerializedName 语义。
                    String name = in.nextName();
                    Object key = keyAdapter.fromJsonTree(new JsonPrimitive(name));
                    Object value = valueAdapter.read(in);
                    map.put(key, value);
                }
                in.endObject();
                return (T) map;
            }
        };
    }
}
