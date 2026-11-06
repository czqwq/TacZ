package com.tacz.guns.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 布尔配置项，对应 1.20.1 {@code ForgeConfigSpec.BooleanValue}。
 *
 * <p>
 * {@code define(name, def)} → 1.7.10 {@link Configuration#get(String, String, boolean, String)}。
 */
public class BooleanConfigValue extends ConfigValue<Boolean> {

    private final boolean defaultValue;
    private final String comment;

    public BooleanConfigValue(TaczConfigSpec spec, String category, String key, boolean defaultValue, String comment) {
        super(spec, category, key);
        this.defaultValue = defaultValue;
        this.comment = comment;
    }

    public boolean getDefault() {
        return defaultValue;
    }

    @Override
    protected void write(Property property, Boolean value) {
        property.setValue(value != null && value);
    }

    @Override
    protected Boolean read(Property property) {
        return property.getBoolean(defaultValue);
    }

    @Override
    protected Property define(Configuration config) {
        return config.get(category, key, defaultValue, comment);
    }
}
