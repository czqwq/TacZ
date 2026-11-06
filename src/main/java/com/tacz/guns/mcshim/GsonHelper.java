package com.tacz.guns.mcshim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/**
 * 对应 1.20.1 的 {@code net.minecraft.util.GsonHelper}。
 *
 * <h2>1.7.10 用什么实现</h2>
 * 1.7.10 <b>没有</b> {@code GsonHelper}，只有项目里自带的 <b>gson 2.2.4</b>。
 * 本类就是 1.20 {@code GsonHelper} 的整数原样移植（把 {@code GsonParser} 相关的新 API
 * 换成 gson 2.2.4 有的等价写法）。
 *
 * <h2>行为差异 / 降级点</h2>
 * <ul>
 * <li>1.20 的 {@code fromJson}/{@code fromJson(JsonElement, Class)} 依赖 {@code JsonParser.parseReader}
 * （gson ≥ 2.8.6）。1.7.10 只有 {@code new JsonParser().parse(Reader)}，本类内部用旧 API。</li>
 * <li>{@code convertToBoolean} 的 1.20 实现依赖 {@code JsonPrimitive.isBoolean()}；
 * gson 2.2.4 没有 {@code isBoolean()}，改用「非数字且 equals("true"/"false") 忽略大小写」判定。</li>
 * <li>异常类型保持一致：解析失败抛 {@link JsonSyntaxException}，字段缺失抛 {@link JsonSyntaxException}，
 * 上游大量 {@code catch (JsonSyntaxException)} 的写法可以直接搬。</li>
 * </ul>
 */
public final class GsonHelper {

    private GsonHelper() {}

    // ---------------------------------------------------------------- 基本字段读取

    /** 对应 1.20 {@code GsonHelper#getString(JsonObject, String)}。 */
    public static String getString(JsonObject json, String memberName) {
        return getAsString(json.get(memberName), memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getString(JsonObject, String, String)}。 */
    public static String getString(JsonObject json, String memberName, String defaultValue) {
        if (json != null && json.has(memberName)) {
            return getAsString(json.get(memberName), memberName);
        }
        return defaultValue;
    }

    /** 对应 1.20 {@code GsonHelper#getNullableString(JsonObject, String)}：缺失时返回 null。 */
    @Nullable
    public static String getNullableString(JsonObject json, String memberName) {
        JsonElement element = json.get(memberName);
        if (element != null && !element.isJsonNull()) {
            return element.getAsString();
        }
        return null;
    }

    /** 对应 1.20 {@code GsonHelper#getBoolean(JsonObject, String)}。 */
    public static boolean getBoolean(JsonObject json, String memberName) {
        JsonElement element = json.get(memberName);
        if (!isBooleanValue(element)) {
            throw new JsonSyntaxException("Expected " + memberName + " to be a Boolean, was " + getType(element));
        }
        return convertToBoolean(element, memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getBoolean(JsonObject, String, boolean)}。 */
    public static boolean getBoolean(JsonObject json, String memberName, boolean defaultValue) {
        if (json != null && json.has(memberName)) {
            return getBoolean(json, memberName);
        }
        return defaultValue;
    }

    /** 对应 1.20 {@code GsonHelper#getInt(JsonObject, String)}。 */
    public static int getInt(JsonObject json, String memberName) {
        return getAsInt(json.get(memberName), memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getInt(JsonObject, String, int)}。 */
    public static int getInt(JsonObject json, String memberName, int defaultValue) {
        if (json != null && json.has(memberName)) {
            return getInt(json, memberName);
        }
        return defaultValue;
    }

    /** 对应 1.20 {@code GsonHelper#getLong(JsonObject, String)}。 */
    public static long getLong(JsonObject json, String memberName) {
        return getAsLong(json.get(memberName), memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getLong(JsonObject, String, long)}。 */
    public static long getLong(JsonObject json, String memberName, long defaultValue) {
        if (json != null && json.has(memberName)) {
            return getLong(json, memberName);
        }
        return defaultValue;
    }

    /** 对应 1.20 {@code GsonHelper#getFloat(JsonObject, String)}。 */
    public static float getFloat(JsonObject json, String memberName) {
        return getAsFloat(json.get(memberName), memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getFloat(JsonObject, String, float)}。 */
    public static float getFloat(JsonObject json, String memberName, float defaultValue) {
        if (json != null && json.has(memberName)) {
            return getFloat(json, memberName);
        }
        return defaultValue;
    }

    /** 对应 1.20 {@code GsonHelper#getDouble(JsonObject, String)}。 */
    public static double getDouble(JsonObject json, String memberName) {
        return getAsDouble(json.get(memberName), memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getDouble(JsonObject, String, double)}。 */
    public static double getDouble(JsonObject json, String memberName, double defaultValue) {
        if (json != null && json.has(memberName)) {
            return getDouble(json, memberName);
        }
        return defaultValue;
    }

    // ---------------------------------------------------------------- JsonObject / JsonArray

    /** 对应 1.20 {@code GsonHelper#getAsJsonObject(JsonObject, String)}。 */
    public static JsonObject getAsJsonObject(JsonObject json, String memberName) {
        return convertToJsonObject(json.get(memberName), memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getAsJsonObject(JsonElement, String)}。 */
    public static JsonObject convertToJsonObject(JsonElement element, String name) {
        // 1.20 此方法形参标 @Nullable，字段缺失（element == null）时抛 JsonSyntaxException；
        // 移植版若不判空会在 element.isJsonObject() 处 NPE，而枪包加载只 catch JsonSyntaxException
        // → 缺一个必填 JSON 字段就从「枪包被丢弃」变成「加载期崩溃」。
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            throw new JsonSyntaxException("Expected " + name + " to be a JsonObject, was " + getType(element));
        }
        return element.getAsJsonObject();
    }

    /** 对应 1.20 {@code GsonHelper#getAsJsonArray(JsonObject, String)}。 */
    public static JsonArray getAsJsonArray(JsonObject json, String memberName) {
        return convertToJsonArray(json.get(memberName), memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getJsonArray(JsonObject, String)}（1.20 里两个名字都有，后者标 @Deprecated）。 */
    public static JsonArray getJsonArray(JsonObject json, String memberName) {
        return getAsJsonArray(json, memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getJsonObject(JsonObject, String)}。 */
    public static JsonObject getJsonObject(JsonObject json, String memberName) {
        return getAsJsonObject(json, memberName);
    }

    /** 对应 1.20 {@code GsonHelper#convertToJsonArray(JsonElement, String)}。 */
    public static JsonArray convertToJsonArray(JsonElement element, String name) {
        // 同 convertToJsonObject：字段缺失时 1.20 抛 JsonSyntaxException，不能 NPE
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            throw new JsonSyntaxException("Expected " + name + " to be a JsonArray, was " + getType(element));
        }
        return element.getAsJsonArray();
    }

    /**
     * 对应 1.20 {@code GsonHelper#getArray(JsonObject, String)}：返回的元素流，缺失即抛异常。
     *
     * <p>
     * 注意：gson 2.2.4 的 {@code JsonArray} 没有 {@code asList()}（2.8.2 才加），这里手工收集。
     */
    public static Stream<JsonElement> getArray(JsonObject json, String memberName) {
        JsonElement element = json == null ? null : json.get(memberName);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            throw new JsonSyntaxException("Expected " + memberName + " to be a JsonArray, was " + getType(element));
        }
        List<JsonElement> collected = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            collected.add(child);
        }
        return collected.stream();
    }

    /** 对应 1.20 {@code GsonHelper#getArray(JsonObject, String, JsonArray)}：缺失时返回默认值。 */
    public static JsonArray getArray(JsonObject json, String memberName, JsonArray defaultValue) {
        if (json != null && json.has(memberName)) {
            return getAsJsonArray(json, memberName);
        }
        return defaultValue;
    }

    // ---------------------------------------------------------------- 原始类型转换

    /** 对应 1.20 {@code GsonHelper#convertToBoolean(JsonElement, String)}。 */
    public static boolean convertToBoolean(JsonElement element, String name) {
        if (!isBooleanValue(element)) {
            throw new JsonSyntaxException("Expected " + name + " to be a Boolean, was " + getType(element));
        }
        return element.getAsBoolean();
    }

    /** 对应 1.20 {@code GsonHelper#isBooleanValue(JsonElement)}。 */
    public static boolean isBooleanValue(JsonElement element) {
        return element != null && element.isJsonPrimitive()
            && element.getAsJsonPrimitive()
                .isBoolean();
    }

    /** 对应 1.20 {@code GsonHelper#convertToFloat(JsonElement, String)}。 */
    public static float convertToFloat(JsonElement element, String name) {
        if (element != null && element.isJsonPrimitive()) {
            return element.getAsFloat();
        }
        throw new JsonSyntaxException("Expected " + name + " to be a Float, was " + getType(element));
    }

    /** 对应 1.20 {@code GsonHelper#getAsFloat(JsonElement, String)}。 */
    public static float getAsFloat(JsonElement element, String name) {
        return convertToFloat(element, name);
    }

    /** 对应 1.20 {@code GsonHelper#convertToInt(JsonElement, String)}。 */
    public static int convertToInt(JsonElement element, String name) {
        if (element != null && element.isJsonPrimitive()
            && element.getAsJsonPrimitive()
                .isNumber()) {
            return element.getAsInt();
        }
        throw new JsonSyntaxException("Expected " + name + " to be an Integer, was " + getType(element));
    }

    /** 对应 1.20 {@code GsonHelper#getAsInt(JsonElement, String)}。 */
    public static int getAsInt(JsonElement element, String name) {
        return convertToInt(element, name);
    }

    /** 对应 1.20 {@code GsonHelper#convertToLong(JsonElement, String)}。 */
    public static long convertToLong(JsonElement element, String name) {
        if (element != null && element.isJsonPrimitive()
            && element.getAsJsonPrimitive()
                .isNumber()) {
            return element.getAsLong();
        }
        throw new JsonSyntaxException("Expected " + name + " to be a Long, was " + getType(element));
    }

    /** 对应 1.20 {@code GsonHelper#getAsLong(JsonElement, String)}。 */
    public static long getAsLong(JsonElement element, String name) {
        return convertToLong(element, name);
    }

    /** 对应 1.20 {@code GsonHelper#convertToDouble(JsonElement, String)}。 */
    public static double convertToDouble(JsonElement element, String name) {
        if (element != null && element.isJsonPrimitive()
            && element.getAsJsonPrimitive()
                .isNumber()) {
            return element.getAsDouble();
        }
        throw new JsonSyntaxException("Expected " + name + " to be a Double, was " + getType(element));
    }

    /** 对应 1.20 {@code GsonHelper#getAsDouble(JsonElement, String)}。 */
    public static double getAsDouble(JsonElement element, String name) {
        return convertToDouble(element, name);
    }

    /** 对应 1.20 {@code GsonHelper#convertToString(JsonElement, String)}。 */
    public static String convertToString(JsonElement element, String name) {
        if (element != null && element.isJsonPrimitive()) {
            return element.getAsString();
        }
        throw new JsonSyntaxException("Expected " + name + " to be a string, was " + getType(element));
    }

    /** 对应 1.20 {@code GsonHelper#getAsString(JsonElement, String)}。 */
    public static String getAsString(JsonElement element, String name) {
        return convertToString(element, name);
    }

    /** 对应 1.20 {@code GsonHelper#getAsBoolean(JsonElement, String)}。 */
    public static boolean getAsBoolean(JsonElement element, String name) {
        return convertToBoolean(element, name);
    }

    // ---------------------------------------------------------------- JsonObject 上的 getAs* 便利重载
    // 说明：1.20 的 GsonHelper 只有 getString/getInt/getBoolean(JsonObject,...) 这一组；
    // 上游（以及本工程的 common-resource）还用到 getAsString/getAsInt 的「JsonObject + 成员名」形态，
    // 这里补齐，语义与对应的 get 版本相同。

    /** 便利重载：等价 {@link #getString(JsonObject, String)}。 */
    public static String getAsString(JsonObject json, String memberName) {
        return getString(json, memberName);
    }

    /** 便利重载：等价 {@link #getString(JsonObject, String, String)}。 */
    public static String getAsString(JsonObject json, String memberName, String defaultValue) {
        return getString(json, memberName, defaultValue);
    }

    /** 便利重载：等价 {@link #getInt(JsonObject, String)}。 */
    public static int getAsInt(JsonObject json, String memberName) {
        return getInt(json, memberName);
    }

    /** 便利重载：等价 {@link #getInt(JsonObject, String, int)}。 */
    public static int getAsInt(JsonObject json, String memberName, int defaultValue) {
        return getInt(json, memberName, defaultValue);
    }

    /** 便利重载：等价 {@link #getBoolean(JsonObject, String, boolean)}。 */
    public static boolean getAsBoolean(JsonObject json, String memberName, boolean defaultValue) {
        return getBoolean(json, memberName, defaultValue);
    }

    /** 便利重载：等价 {@link #getBoolean(JsonObject, String)}。 */
    public static boolean getAsBoolean(JsonObject json, String memberName) {
        return getBoolean(json, memberName);
    }

    /** 便利重载：等价 {@link #getFloat(JsonObject, String)}。 */
    public static float getAsFloat(JsonObject json, String memberName) {
        return getFloat(json, memberName);
    }

    /** 便利重载：等价 {@link #getDouble(JsonObject, String)}。 */
    public static double getAsDouble(JsonObject json, String memberName) {
        return getDouble(json, memberName);
    }

    /** 便利重载：等价 {@link #getLong(JsonObject, String)}。 */
    public static long getAsLong(JsonObject json, String memberName) {
        return getLong(json, memberName);
    }

    /** 对应 1.20 {@code GsonHelper#getType(JsonElement)}：错误信息里用的类型名。 */
    public static String getType(@Nullable JsonElement element) {
        if (element == null) {
            return "null";
        }
        if (element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonPrimitive()) {
            return "a " + (element.getAsJsonPrimitive()
                .isNumber() ? "number" : "string");
        }
        if (element.isJsonArray()) {
            return "an array";
        }
        return element.isJsonObject() ? "an object" : "unknown";
    }

    // ---------------------------------------------------------------- 反序列化

    /** 对应 1.20 {@code GsonHelper#fromJson(Gson, Reader, Class)}。 */
    public static <T> T fromJson(Gson gson, Reader reader, Class<T> clazz) {
        return fromJson(gson, reader, clazz, false);
    }

    /**
     * 对应 1.20 {@code GsonHelper#fromJson(Gson, Reader, Class, boolean)}。
     *
     * <p>
     * {@code lenient} 为 true 时用 {@link JsonReader#setLenient(boolean)} 打开宽松模式
     * （枪包/资源 JSON 里可能存在尾逗号、非引号键名）。
     */
    public static <T> T fromJson(Gson gson, Reader reader, Class<T> clazz, boolean lenient) {
        try {
            // gson 2.2.4 没有 Gson#newJsonReader（2.8.6 才有），直接 new。
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(lenient);
            T value = gson.getAdapter(clazz)
                .read(jsonReader);
            if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("JSON reader expected to be at the end of the document");
            }
            return value;
        } catch (IOException e) {
            throw new JsonParseException("Could not read JSON: " + e.getMessage(), e);
        }
    }

    /** 对应 1.20 {@code GsonHelper#fromJson(Gson, InputStream, Class)}。 */
    public static <T> T fromJson(Gson gson, InputStream stream, Class<T> clazz) {
        return fromJson(gson, new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), clazz);
    }

    /** 对应 1.20 {@code GsonHelper#fromJson(Gson, InputStream, Class, boolean)}。 */
    public static <T> T fromJson(Gson gson, InputStream stream, Class<T> clazz, boolean lenient) {
        return fromJson(
            gson,
            new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)),
            clazz,
            lenient);
    }

    /** 对应 1.20 {@code GsonHelper#fromJson(Gson, String, Class)}。 */
    public static <T> T fromJson(Gson gson, String json, Class<T> clazz) {
        return fromJson(gson, new StringReader(json), clazz, false);
    }

    /** 对应 1.20 {@code GsonHelper#fromJson(Gson, String, Class, boolean)}。 */
    public static <T> T fromJson(Gson gson, String json, Class<T> clazz, boolean lenient) {
        return fromJson(gson, new StringReader(json), clazz, lenient);
    }

    /** 对应 1.20 {@code GsonHelper#fromJson(Gson, JsonElement, Class)}。 */
    public static <T> T fromJson(Gson gson, JsonElement element, Class<T> clazz) {
        TypeAdapter<T> adapter = gson.getAdapter(clazz);
        try {
            return adapter.fromJsonTree(element);
        } catch (JsonParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JsonParseException("Could not convert JSON element to " + clazz.getName(), e);
        }
    }

    /** 对应 1.20 {@code GsonHelper#parse(Reader)}（返回 JsonElement）。 */
    public static JsonElement parse(Reader reader) {
        return new JsonParser().parse(reader);
    }

    /** 对应 1.20 {@code GsonHelper#parse(String)}（返回 JsonElement）。 */
    public static JsonElement parse(String json) {
        return new JsonParser().parse(json);
    }

    /** 对应 1.20 {@code GsonHelper#parse(Reader, boolean)}（gson 2.2.4 的 JsonParser 无 lenient 开关，忽略该参数）。 */
    public static JsonElement parse(Reader reader, boolean lenient) {
        return new JsonParser().parse(reader);
    }
}
