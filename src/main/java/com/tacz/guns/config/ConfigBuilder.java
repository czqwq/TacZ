package com.tacz.guns.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

/**
 * 1.7.10 版 {@code net.minecraftforge.common.ForgeConfigSpec.Builder} 的等价物。
 *
 * <p>
 * 上游 config/** 的写法是：
 * 
 * <pre>
 * ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
 * builder.push("gun");
 * builder.comment("...");
 * DEFAULT_GUN_FIRE_SOUND_DISTANCE = builder.defineInRange("DefaultGunFireSoundDistance", 64, 0, Integer.MAX_VALUE);
 * builder.pop();
 * </pre>
 * 
 * 这里保持完全一样的方法名与签名（{@code push/pop/comment/define/defineInRange/defineEnum/
 * defineListAllowEmpty/build}），只把 {@code ForgeConfigSpec.*} 换成 {@link TaczConfigSpec} 那一套配置项对象，
 * 于是 config/** 下的类只需要换类型名，逻辑一行不用重写。
 *
 * <p>
 * 语义映射：{@code push} 的名字用 '.' 连接成 1.7.10 {@code Configuration} 的 category；
 * {@code comment(...)} 里的多行文本合并成一项的注释（1.7.10 的 {@code Property} 只有单个 comment 字符串）。
 */
public class ConfigBuilder {

    private final TaczConfigSpec spec;
    private final Deque<String> path = new ArrayDeque<>();
    private final List<String> pendingComment = new ArrayList<>();

    public ConfigBuilder(TaczConfigSpec spec) {
        this.spec = spec;
    }

    public ConfigBuilder push(String name) {
        path.addLast(name);
        return this;
    }

    public ConfigBuilder pop() {
        if (!path.isEmpty()) {
            path.removeLast();
        }
        return this;
    }

    public ConfigBuilder comment(String... lines) {
        if (lines != null) {
            Collections.addAll(pendingComment, lines);
        }
        return this;
    }

    public BooleanConfigValue define(String key, boolean defaultValue) {
        BooleanConfigValue value = new BooleanConfigValue(spec, category(), key, defaultValue, consumeComment());
        spec.addValue(value);
        return value;
    }

    public IntConfigValue defineInRange(String key, int defaultValue, int minValue, int maxValue) {
        IntConfigValue value = new IntConfigValue(
            spec,
            category(),
            key,
            defaultValue,
            minValue,
            maxValue,
            consumeComment());
        spec.addValue(value);
        return value;
    }

    public DoubleConfigValue defineInRange(String key, double defaultValue, double minValue, double maxValue) {
        DoubleConfigValue value = new DoubleConfigValue(
            spec,
            category(),
            key,
            defaultValue,
            minValue,
            maxValue,
            consumeComment());
        spec.addValue(value);
        return value;
    }

    public StringConfigValue define(String key, String defaultValue) {
        StringConfigValue value = new StringConfigValue(spec, category(), key, defaultValue, consumeComment());
        spec.addValue(value);
        return value;
    }

    public <T extends Enum<T>> EnumConfigValue<T> defineEnum(String key, T defaultValue) {
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) defaultValue.getDeclaringClass();
        EnumConfigValue<T> value = new EnumConfigValue<>(spec, category(), key, type, defaultValue, consumeComment());
        spec.addValue(value);
        return value;
    }

    /**
     * 对应上游 {@code defineListAllowEmpty(name, defaultValues, validator)}。
     * <p>
     * 1.7.10 {@code Property} 没有逐元素校验器，validator 只做保留（不参与运行时校验，语义等价于「全部接受」）。
     */
    public ListConfigValue defineListAllowEmpty(String key, List<String> defaultValues, Predicate<Object> validator) {
        ListConfigValue value = new ListConfigValue(spec, category(), key, defaultValues, consumeComment());
        spec.addValue(value);
        return value;
    }

    /**
     * 对应上游 {@code defineList(name, defaultValues, validator)}（不允许空列表）。1.7.10 侧无差别处理。
     */
    public ListConfigValue defineList(String key, List<String> defaultValues, Predicate<Object> validator) {
        return defineListAllowEmpty(key, defaultValues, validator);
    }

    /**
     * 收尾：把所有登记过的配置项写进底层 {@code Configuration}（此时还没有读文件，读到的是默认值）。
     * 真正的文件加载由 {@link TaczConfigSpec#load()} 完成。
     */
    public TaczConfigSpec build() {
        for (ConfigValue<?> value : spec.getValues()) {
            value.reload();
        }
        return spec;
    }

    private String category() {
        return String.join(".", path);
    }

    private String consumeComment() {
        if (pendingComment.isEmpty()) {
            return null;
        }
        String comment = String.join("\n", pendingComment);
        pendingComment.clear();
        return comment;
    }
}
