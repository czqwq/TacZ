package com.tacz.guns.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 字符串配置项，对应 1.20.1 {@code ForgeConfigSpec.ConfigValue<String>}。
 *
 * <p>
 * {@code define(name, def)} → 1.7.10 {@link Configuration#get(String, String, String, String)}。
 * 上游 config/** 目前没有直接用字符串项，保留本类是为了完整性（配置层契约与上游一致）。
 */
public class StringConfigValue extends ConfigValue<String> {

    private final String defaultValue;
    private final String comment;

    public StringConfigValue(TaczConfigSpec spec, String category, String key, String defaultValue, String comment) {
        super(spec, category, key);
        this.defaultValue = defaultValue;
        this.comment = comment;
    }

    public String getDefault() {
        return defaultValue;
    }

    @Override
    protected void write(Property property, String value) {
        property.setValue(value == null ? defaultValue : value);
    }

    @Override
    protected String read(Property property) {
        String value = property.getString();
        return value == null ? defaultValue : value;
    }

    @Override
    protected Property define(Configuration config) {
        return config.get(category, key, defaultValue, comment);
    }
}
