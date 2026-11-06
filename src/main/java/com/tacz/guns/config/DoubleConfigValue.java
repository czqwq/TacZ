package com.tacz.guns.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 浮点（double）配置项，对应 1.20.1 {@code ForgeConfigSpec.DoubleValue}。
 *
 * <p>
 * {@code defineInRange(name, def, min, max)} → 1.7.10
 * {@link Configuration#get(String, String, double, String, double, double)}。
 */
public class DoubleConfigValue extends ConfigValue<Double> {

    private final double defaultValue;
    private final double minValue;
    private final double maxValue;
    private final String comment;

    public DoubleConfigValue(TaczConfigSpec spec, String category, String key, double defaultValue, double minValue,
        double maxValue, String comment) {
        super(spec, category, key);
        this.defaultValue = defaultValue;
        this.minValue = Math.min(minValue, maxValue);
        this.maxValue = maxValue;
        this.comment = comment;
    }

    public double getDefault() {
        return defaultValue;
    }

    @Override
    protected void write(Property property, Double value) {
        property.setValue(value == null ? defaultValue : value.doubleValue());
    }

    @Override
    protected Double read(Property property) {
        return property.getDouble(defaultValue);
    }

    @Override
    protected Property define(Configuration config) {
        return config.get(category, key, defaultValue, comment, minValue, maxValue);
    }
}
