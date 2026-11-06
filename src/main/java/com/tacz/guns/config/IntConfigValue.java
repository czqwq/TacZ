package com.tacz.guns.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 整数配置项，对应 1.20.1 {@code ForgeConfigSpec.IntValue}。
 *
 * <p>
 * {@code defineInRange(name, def, min, max)} → 1.7.10
 * {@link Configuration#get(String, String, int, String, int, int)}（超出范围会由 {@link Property} 夹紧，
 * 与上游 {@code defineInRange} 的夹紧语义一致）。
 */
public class IntConfigValue extends ConfigValue<Integer> {

    private final int defaultValue;
    private final int minValue;
    private final int maxValue;
    private final String comment;

    public IntConfigValue(TaczConfigSpec spec, String category, String key, int defaultValue, int minValue,
        int maxValue, String comment) {
        super(spec, category, key);
        this.defaultValue = defaultValue;
        this.minValue = Math.min(minValue, maxValue);
        this.maxValue = maxValue;
        this.comment = comment;
    }

    public int getDefault() {
        return defaultValue;
    }

    @Override
    protected void write(Property property, Integer value) {
        property.setValue(value == null ? defaultValue : value.intValue());
    }

    @Override
    protected Integer read(Property property) {
        return property.getInt(defaultValue);
    }

    @Override
    protected Property define(Configuration config) {
        return config.get(category, key, defaultValue, comment, minValue, maxValue);
    }
}
