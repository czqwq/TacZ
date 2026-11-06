package com.tacz.guns.resource.filter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import com.google.gson.*;
import com.tacz.guns.GunMod;

public class RecipeFilter {

    private final List<IFilter<ResourceLocation>> whitelist = new ArrayList<>();
    private final List<IFilter<ResourceLocation>> blacklist = new ArrayList<>();

    public void merge(RecipeFilter other) {
        this.whitelist.addAll(other.whitelist);
        this.blacklist.addAll(other.blacklist);
    }

    public boolean contains(ResourceLocation location) {
        boolean allowed = whitelist.isEmpty();
        for (IFilter<ResourceLocation> filter : this.whitelist) {
            if (filter.test(location)) {
                allowed = true;
                break;
            }
        }
        for (IFilter<ResourceLocation> filter : this.blacklist) {
            if (filter.test(location)) {
                allowed = false;
                break;
            }
        }
        return allowed;
    }

    public List<ResourceLocation> filter(List<ResourceLocation> input) {
        List<ResourceLocation> output = new ArrayList<>();
        for (ResourceLocation location : input) {
            if (contains(location)) {
                output.add(location);
            }
        }
        return output;
    }

    public <T> List<T> filter(List<T> input, Function<T, ResourceLocation> getter) {
        List<T> output = new ArrayList<>();
        for (T entry : input) {
            if (contains(getter.apply(entry))) {
                output.add(entry);
            }
        }
        return output;
    }

    public static class Deserializer implements JsonDeserializer<RecipeFilter>, JsonSerializer<RecipeFilter> {

        @Override
        public RecipeFilter deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            RecipeFilter filter = new RecipeFilter();
            // 1.7.10 差异（崩溃防护）：gson 2.2.4 的 JsonElement#getAsJsonObject() 对非对象抛
            // IllegalStateException，JsonObject#getAsJsonArray(name) 对「同名成员不是数组」抛 ClassCastException。
            // 两者都不在 JsonDataManager#apply 的 catch(JsonParseException|IllegalArgumentException) 范围内，
            // 会一路抛到 CommonAssetsManager#reload 并重抛 → 服务端起不来。这里统一转成 JsonParseException，
            // 让写坏一个 filters 文件只导致该文件被跳过（与装载器既有契约一致）。
            if (!json.isJsonObject()) {
                throw new JsonParseException("Expected " + json + " to be a recipe filter object");
            }
            JsonArray wl = getArray(json.getAsJsonObject(), "whitelist");
            if (wl != null) {
                loadFilters(wl, filter.whitelist);
            }
            JsonArray bl = getArray(json.getAsJsonObject(), "blacklist");
            if (bl != null) {
                loadFilters(bl, filter.blacklist);
            }
            return filter;
        }

        private static JsonArray getArray(JsonObject object, String member) {
            JsonElement element = object.get(member);
            if (element == null || element.isJsonNull()) {
                return null;
            }
            if (!element.isJsonArray()) {
                throw new JsonParseException("Expected recipe filter '" + member + "' to be an array: " + element);
            }
            return element.getAsJsonArray();
        }

        private void loadFilters(JsonArray array, @NotNull List<IFilter<ResourceLocation>> list) {
            LiteralFilter.Builder<ResourceLocation> builder = new LiteralFilter.Builder<>();
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    String entry = element.getAsString();
                    if (entry.startsWith("^")) {
                        try {
                            list.add(new RegexFilter<>(entry));
                        } catch (PatternSyntaxException e) {
                            GunMod.LOGGER.error("Failed to parse regex filter: {}", entry, e);
                        }
                    } else {
                        ResourceLocation rl = tryParse(entry);
                        if (rl != null) {
                            builder.add(rl);
                        }
                    }
                } else {
                    throw new JsonParseException("Invalid recipe filter entry: " + element);
                }
            }
            list.add(builder.build());
        }

        @Override
        public JsonElement serialize(RecipeFilter filter, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = new JsonObject();

            json.add("whitelist", context.serialize(toList(filter.whitelist)));
            json.add("blacklist", context.serialize(toList(filter.blacklist)));
            return json;
        }

        private List<String> toList(List<IFilter<ResourceLocation>> filter) {
            List<String> list = new ArrayList<>();
            for (var f : filter) {
                if (f instanceof LiteralFilter<ResourceLocation>literalFilter) {
                    list.addAll(
                        literalFilter.getSet()
                            .stream()
                            .map(ResourceLocation::toString)
                            .toList());
                } else if (f instanceof RegexFilter<ResourceLocation>regexFilter) {
                    list.add(
                        regexFilter.getPattern()
                            .pattern());
                }
            }
            return list;
        }
    }

    /**
     * 1.7.10 差异：没有 {@code ResourceLocation.tryParse(String)}。
     * 1.7.10 的 {@code new ResourceLocation(String)} 不校验非法字符，
     * 这里补上「非法输入返回 null」的上游语义。
     */
    private static ResourceLocation tryParse(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
