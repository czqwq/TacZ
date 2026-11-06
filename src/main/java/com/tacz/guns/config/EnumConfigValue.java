package com.tacz.guns.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 枚举配置项，对应 1.20.1 {@code ForgeConfigSpec.EnumValue<T>}。
 *
 * <p>
 * {@code defineEnum(name, def)} → 1.7.10 {@link Configuration#get(String, String, String, String, String[])}，
 * 值以枚举常量名保存，并带上合法值列表。读取时若文件里是无效名（手工改坏/枚举改名），回落到默认值，
 * 与上游 {@code EnumValue} 的容错行为一致。
 */
public class EnumConfigValue<T extends Enum<T>> extends ConfigValue<T> {

    private final Class<T> type;
    private final T defaultValue;
    private final String comment;
    private final String[] validValues;

    public EnumConfigValue(TaczConfigSpec spec, String category, String key, Class<T> type, T defaultValue,
        String comment) {
        super(spec, category, key);
        this.type = type;
        this.defaultValue = defaultValue;
        this.comment = comment;
        T[] constants = type.getEnumConstants();
        this.validValues = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            this.validValues[i] = constants[i].name();
        }
    }

    public T getDefault() {
        return defaultValue;
    }

    @Override
    protected void write(Property property, T value) {
        property.setValue((value == null ? defaultValue : value).name());
    }

    @Override
    protected T read(Property property) {
        String name = property.getString();
        if (name != null) {
            try {
                return Enum.valueOf(type, name.trim());
            } catch (IllegalArgumentException ignored) {
                // 无效枚举名 → 回落默认值（上游 EnumValue 也是这么做的）
            }
        }
        return defaultValue;
    }

    @Override
    protected Property define(Configuration config) {
        return config.get(category, key, defaultValue.name(), comment, validValues);
    }
}
