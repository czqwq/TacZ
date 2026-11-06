package com.tacz.guns.api.modifier;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.IChatComponent;

import com.google.common.collect.Lists;

/**
 * 配件从 Json 读取的数据
 *
 * @param <T> Json 读取后转换成的中间数据类型
 */
public abstract class JsonProperty<T> {

    /**
     * 1.7.10 移植：{@code Component}（1.20.1）→ {@link IChatComponent}。
     */
    protected List<IChatComponent> components = Lists.newArrayList();
    private @Nullable T value;

    public JsonProperty(@Nullable T value) {
        this.value = value;
    }

    @Nullable
    public T getValue() {
        return value;
    }

    public void setValue(@Nullable T value) {
        this.value = value;
    }

    public List<IChatComponent> getComponents() {
        return components;
    }

    /**
     * 初始化文本提示，用于配件的描述文本
     */
    public abstract void initComponents();
}
