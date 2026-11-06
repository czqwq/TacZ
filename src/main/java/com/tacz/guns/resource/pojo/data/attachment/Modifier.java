package com.tacz.guns.resource.pojo.data.attachment;

import javax.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.resource.manager.AttachmentScriptManager;

/*
 * 1.7.10 差异：上游用 {@code @JsonAdapter(ModifierTypeAdapterFactory.class)} 挂适配器，
 * 但 gson 2.2.4 没有 {@code com.google.gson.annotations.JsonAdapter}（2.3 才加）。
 * 改为在 {@link com.tacz.guns.resource.CommonAssetsManager#GSON} 上
 * {@code registerTypeAdapterFactory(new ModifierTypeAdapterFactory())} 全局注册，语义等价。
 */
public class Modifier {

    @SerializedName("addend")
    private double addend = 0;

    @SerializedName("percent")
    private double percent = 0;

    @SerializedName("multiplier")
    private double multiplier = 1;

    @Nullable
    @SerializedName("function")
    private String function = null;

    @Nullable
    private transient AttachmentScriptManager.CompiledScript compiledFunction;

    public double getAddend() {
        return addend;
    }

    public double getPercent() {
        return percent;
    }

    public double getMultiplier() {
        return multiplier;
    }

    @Nullable
    public String getFunction() {
        return function;
    }

    public void compileFunction() {
        compiledFunction = AttachmentScriptManager.compile(function);
    }

    @Nullable
    public AttachmentScriptManager.CompiledScript getCompiledFunction() {
        return compiledFunction;
    }

    @Deprecated
    public void setAddend(double addend) {
        this.addend = addend;
    }

    @Deprecated
    public void setPercent(double percent) {
        this.percent = percent;
    }
}
