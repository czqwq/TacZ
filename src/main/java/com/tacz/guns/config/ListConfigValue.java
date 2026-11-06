package com.tacz.guns.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 字符串列表配置项，对应 1.20.1 {@code ForgeConfigSpec.ConfigValue<List<String>>} +
 * {@code defineListAllowEmpty / defineList}。
 *
 * <p>
 * 1.7.10 侧用 {@link Configuration#getStringList(String, String, String[], String)}，落到配置文件里就是
 * 普通的「key 后跟一个尖括号包裹的多行列表」语法。
 *
 * <p>
 * 与上游的差异：上游 {@code ConfigValue<List<? extends String>>} 的泛型通配在这里统一收敛成
 * {@code List<String>}；上游的逐元素校验器（{@code defineListAllowEmpty(name, def, validator)}）在
 * 1.7.10 的 {@link Property} 上没有等价物，因此校验器参数被忽略，只在 {@link ConfigBuilder} 里保留签名，
 * 语义等价于「全部接受」。
 */
public class ListConfigValue extends ConfigValue<List<String>> {

    private final List<String> defaultValue;
    private final String comment;

    public ListConfigValue(TaczConfigSpec spec, String category, String key, List<String> defaultValue,
        String comment) {
        super(spec, category, key);
        this.defaultValue = Collections.unmodifiableList(new ArrayList<>(defaultValue));
        this.comment = comment;
    }

    public List<String> getDefault() {
        return defaultValue;
    }

    @Override
    protected void write(Property property, List<String> value) {
        property.setValues((value == null ? defaultValue : value).toArray(new String[0]));
    }

    @Override
    protected List<String> read(Property property) {
        String[] values = property.getStringList();
        if (values == null) {
            return defaultValue;
        }
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
    }

    @Override
    protected Property define(Configuration config) {
        // 1.7.10 的 Configuration#getStringList 返回 String[]（其它 getXxx 返回 Property）：
        // 先调用它把列表项登记进配置（带默认值与注释），再从 category 里取回 Property 以便 set() 时能写入。
        config.getStringList(key, category, defaultValue.toArray(new String[0]), comment);
        return config.getCategory(category)
            .get(key);
    }
}
