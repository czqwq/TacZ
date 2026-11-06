package com.tacz.guns.compat.optifine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * OptiFine 兼容探测。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/optifine/OptifineCompat.java}
 * —— 只有「类探测」两个方法（{@code isOptifineInstalled()} / {@code isClassFound(String)}），
 * 上游没有任何调用点（属于给附属模组 / 日后渲染路径预留的工具类）。
 *
 * <p>
 * <b>1.7.10 形态</b>：<b>OptiFine 在 1.7.10 上是存在的</b>（而且自带 Shaders Mod），
 * 所以这里不是空壳 —— {@link #isOptifineInstalled()} 与新增的
 * {@link #isShadersEnabled()} / {@link #isShaderPackLoaded()} 都是<b>真探测</b>
 * （纯反射，不引入任何硬依赖；没装 OptiFine 时全部静默返回 {@code false}）。
 *
 * <p>
 * 1.7.10 OptiFine 的相关类（<b>不要</b>写进 import，只用字符串反射）：
 * <ul>
 * <li>{@code net.optifine.Config#isShaders()} —— 是否启用了光影。</li>
 * <li>{@code net.optifine.shaders.Shaders#shaderPackLoaded} —— 光影包是否加载成功
 * （更老的「Shaders Mod」独立版是 {@code shadersmod.client.Shaders}，一并探测）。</li>
 * </ul>
 *
 * <p>
 * <b>注意（Angelica）</b>：{@code docs/ANGELICA_COMPAT.md} 的 P2-6 指出，Angelica 在启动期会
 * <b>强制卸载 OptiFine</b>（{@code IncompatibleModsDisablerTweaker}）并让
 * {@code FMLClientHandler#hasOptifine()} 恒 {@code false}。因此<b>装 Angelica 时本类全部返回
 * {@code false} 是正确行为</b>，此时真正的光影由 Angelica 的 Iris 实现提供 ——
 * 判断「有没有光影 / 是不是在 shadow pass」请用
 * {@link com.tacz.guns.compat.oculus.OculusCompat}（已覆盖 Angelica）。
 */
public final class OptifineCompat {

    private static final String OPTIFINE_CONFIG_CLASS = "net.optifine.Config";
    /** 1.7.10 后期 OptiFine 内置 Shaders Mod 的包名。 */
    private static final String OPTIFINE_SHADERS_CLASS = "net.optifine.shaders.Shaders";
    /** Shaders Mod 独立版（OptiFine 合并之前）的包名。 */
    private static final String SHADERS_MOD_CLASS = "shadersmod.client.Shaders";
    private static final String SHADER_PACK_LOADED_FIELD = "shaderPackLoaded";

    private static final boolean IS_OPTIFINE_INSTALLED = isClassFound(OPTIFINE_CONFIG_CLASS);

    private static Boolean shadersEnabled;
    private static Boolean shaderPackLoaded;

    private OptifineCompat() {}

    /** OptiFine 是否装在客户端（探测 {@code net.optifine.Config}）。 */
    public static boolean isOptifineInstalled() {
        return IS_OPTIFINE_INSTALLED;
    }

    /** 通用类探测（上游同签名，改为不抛 {@code Throwable}：链接错误也静默）。 */
    public static boolean isClassFound(String className) {
        try {
            Class.forName(
                className,
                false,
                Thread.currentThread()
                    .getContextClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 是否启用了光影（对应 1.7.10 OptiFine 的 {@code Config.isShaders()}）。
     *
     * <p>
     * 结果缓存：OptiFine 的 {@code isShaders()} 在游戏内切换光影包后会变，若需要实时值请传
     * {@code false} 重查 —— 1.7.10 的切换入口在 GuiVideoSettings 内，我方无钩子，故默认缓存。
     */
    public static boolean isShadersEnabled() {
        if (shadersEnabled == null) {
            shadersEnabled = invokeStaticBoolean(OPTIFINE_CONFIG_CLASS, "isShaders");
        }
        return shadersEnabled;
    }

    /**
     * 光影包是否已加载（对应 1.7.10 OptiFine / Shaders Mod 的静态字段 {@code shaderPackLoaded}）。
     *
     * <p>
     * 找不到该字段时回退到 {@link #isShadersEnabled()}（1.7.10 OptiFine 里两者通常等价）。
     */
    public static boolean isShaderPackLoaded() {
        if (shaderPackLoaded == null) {
            Boolean loaded = readStaticBoolean(OPTIFINE_SHADERS_CLASS, SHADER_PACK_LOADED_FIELD);
            if (loaded == null) {
                loaded = readStaticBoolean(SHADERS_MOD_CLASS, SHADER_PACK_LOADED_FIELD);
            }
            shaderPackLoaded = loaded != null ? loaded : isShadersEnabled();
        }
        return shaderPackLoaded;
    }

    private static boolean invokeStaticBoolean(String className, String methodName) {
        try {
            Class<?> type = Class.forName(
                className,
                false,
                Thread.currentThread()
                    .getContextClassLoader());
            Method method = type.getMethod(methodName);
            Object value = method.invoke(null);
            return Boolean.TRUE.equals(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Boolean readStaticBoolean(String className, String fieldName) {
        try {
            Class<?> type = Class.forName(
                className,
                false,
                Thread.currentThread()
                    .getContextClassLoader());
            Field field = type.getField(fieldName);
            Object value = field.get(null);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
