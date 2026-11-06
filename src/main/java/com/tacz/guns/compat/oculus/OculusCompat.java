package com.tacz.guns.compat.oculus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import com.tacz.guns.GunMod;
import com.tacz.guns.init.CompatRegistry;
import com.tacz.guns.mcshim.MultiBufferSource;

import cpw.mods.fml.common.Loader;

/**
 * 光影兼容门面（Oculus / Iris · <b>Angelica</b>）。
 *
 * <p>
 * <b>上游完整实现</b>：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/compat/oculus/}
 * —— {@code OculusCompat}（门面：按模组版本分派到 legacy / newly 两套实现）+
 * {@code OculusCompatNewly}（irisshaders 包名）+ {@code OculusCompatLegacy}（coderbot 包名）+
 * {@code IUnflushableWrapper}（配 {@code UnflushableWrapperAccessor} mixin 取出 Iris 内部真正的 BufferSource）。
 *
 * <p>
 * <b>1.7.10 形态</b>：1.7.10 的 Iris 等价物是 <b>Angelica</b>
 * （GTNewHorizons，modid = {@code angelica}，见
 * {@code tmp/angelica-2.2.42-src/com/gtnewhorizons/angelica/AngelicaMod.java:31}；
 * 依据见 {@code docs/ANGELICA_COMPAT.md} 的 P1-3）。Angelica 自带 Iris 实现，并且
 * <b>同时</b>保留 new（{@code net.irisshaders.*}）与 legacy（{@code net.coderbot.*}）两套包名；
 * 若只探测 {@code oculus} modid，Angelica 下 {@link #LOADED} 恒 {@code false} →
 * {@link #isRenderShadow()} 恒 {@code false} → <b>Angelica 光影的 shadow pass 里本仓会重复绘制</b>
 * （枪口火光/弹壳粒子被画进 shadow map，污染影子、模板预算翻倍）。
 * 因此本类按「<b>Oculus 或 Angelica 任一在</b>」探测，其余判定仍只依赖类是否可加载（纯反射、静默降级）。
 *
 * <p>
 * 上游那三个「按版本挑一套 API 直调」的实现类已删除（逐条原因见 {@code docs/PORT_STATUS.md}），
 * 本类改为纯反射版本分派：不 import 任何 Iris 类型，方法名保持与上游一致，
 * 探测不到时静默降级 —— 与上游「没装光影模组时全部返回中性值」的语义等价。
 *
 * <p>
 * 反射的两套包名前缀（上游 legacy / newly 的分界，Iris 1.7.0 由 {@code net.coderbot} 改名 {@code net.irisshaders}）：
 * <ul>
 * <li>{@code net.irisshaders.iris.*} / {@code net.irisshaders.batchedentityrendering.*}（Oculus ≥ 1.7.0、Angelica）</li>
 * <li>{@code net.coderbot.iris.*} / {@code net.coderbot.batchedentityrendering.*}（更早版本、Angelica 的 Iris 实现）</li>
 * </ul>
 */
public final class OculusCompat {

    /** 上游 modid（与 {@code init/CompatRegistry#OCULUS} 同义）。 */
    private static final String OCULUS_MODID = CompatRegistry.OCULUS;
    /**
     * 1.7.10 的 Iris 等价实现：GTNewHorizons <b>Angelica</b>（{@code AngelicaMod.MOD_ID = "angelica"}）。
     *
     * <p>
     * 直接引用 {@code init/CompatRegistry#ANGELICA}（{@code init/CompatRegistry.java:42}，已由
     * {@code init/} 侧补齐），不再自持一份字面量 —— 两处 modid 一旦漂移，
     * 「探测到 Angelica」与「据此算 {@link #LOADED}」就会给出互相矛盾的结论。
     */
    private static final String ANGELICA_MODID = CompatRegistry.ANGELICA;

    private static final String[] IRIS_API_CLASSES = { "net.irisshaders.iris.api.v0.IrisApi",
        "net.coderbot.iris.api.v0.IrisApi" };
    private static final String[] SHADOW_STATE_CLASSES = { "net.irisshaders.iris.shadows.ShadowRenderingState",
        "net.coderbot.iris.shadows.ShadowRenderingState" };
    private static final String[] FULLY_BUFFERED_CLASSES = {
        "net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource",
        "net.coderbot.batchedentityrendering.impl.FullyBufferedMultiBufferSource" };

    /** Angelica 的「启用 Iris 光影」总开关：{@code AngelicaConfig.enableIris}（{@code AngelicaConfig.java:68-71}）。 */
    private static final String ANGELICA_CONFIG_CLASS = "com.gtnewhorizons.angelica.config.AngelicaConfig";
    private static final String ANGELICA_IRIS_SWITCH = "enableIris";

    /** Iris API 的两个判定方法名（{@code net.irisshaders.iris.api.v0.IrisApi:41,62}）。 */
    private static final String API_SHADER_PACK_IN_USE = "isShaderPackInUse";
    private static final String API_RENDERING_SHADOW_PASS = "isRenderingShadowPass";
    /**
     * {@code ShadowRenderingState#areShadowsCurrentlyBeingRendered()}（Angelica {@code ShadowRenderingState.java:7}）。
     */
    private static final String STATE_SHADOWS_BEING_RENDERED = "areShadowsCurrentlyBeingRendered";
    /** 无参 flush 方法名（{@code FullyBufferedMultiBufferSource#endBatch()}）。 */
    private static final String METHOD_END_BATCH = "endBatch";

    /** Oculus/Iris 模组本体是否安装。 */
    private static final boolean OCULUS_MOD = Loader.isModLoaded(OCULUS_MODID);
    /** Angelica（1.7.10 的 Sodium + Iris 实现）是否安装。 */
    private static final boolean ANGELICA_MOD = Loader.isModLoaded(ANGELICA_MODID);

    /** 光影实现是否可用 = (Oculus 或 Angelica) 在，<b>且</b> Iris API 类可加载。 */
    public static final boolean LOADED = (OCULUS_MOD || ANGELICA_MOD) && firstFound(IRIS_API_CLASSES) != null;

    // 懒解析缓存：isRenderShadow()/endBatch() 在渲染热路径上被多处每帧调用，
    // 不能每次都 Class.forName（装 Angelica 时 LOADED 会真的变成 true）。
    private static boolean shadowPassResolved;
    private static Method shadowPassMethod;
    private static boolean irisApiResolved;
    private static Object irisApiInstance;
    private static Method irisApiShaderPackMethod;
    private static Method irisApiShadowPassMethod;
    private static boolean fullyBufferedResolved;
    private static Class<?>[] fullyBufferedTypes = new Class<?>[0];

    private static boolean logged;

    private OculusCompat() {}

    /** 对应上游 {@code OculusCompat#initCompat()}（由 {@code init/CompatRegistry#enqueueCompat()} 反射调用）。 */
    public static void initCompat() {
        if (!LOADED) {
            return;
        }
        // 上游这里按 Oculus 版本号（DefaultArtifactVersion("1.7.0")）挑 legacy / newly；
        // 本移植改为「按包名逐个探测」（见 firstFound(...)），不需要版本号解析。
        if (!logged) {
            logged = true;
            GunMod.LOGGER.info(
                "Detected {}; TACZ will probe the Iris API reflectively (shader pack / shadow pass detection)",
                ANGELICA_MOD ? "Angelica (1.7.10 的 Iris 实现)" : "Oculus/Iris");
        }
    }

    /**
     * 对应上游 {@code OculusCompat#isRenderShadow()}：当前是否处于光影的 <b>shadow pass</b>。
     *
     * <p>
     * 判定顺序（纯反射，失败一律 {@code false}，<b>不抛异常</b>）：
     * <ol>
     * <li>{@code ShadowRenderingState#areShadowsCurrentlyBeingRendered()} —— 最权威；Angelica 与
     * Iris/Oculus 都有（Angelica 版实现返回 {@code ShadowRenderer.ACTIVE}）。</li>
     * <li>{@code IrisApi#isRenderingShadowPass()} —— 上一项不可用时的次选（同一语义）。</li>
     * </ol>
     *
     * <p>
     * <b>注意</b>：这里<b>不能</b>用 {@code IrisApi#isShaderPackInUse()}（「光影包是否启用」）近似 ——
     * 那是「全程为真」的全局开关；并进来会让本方法在「Angelica + 光影包」下恒 {@code true}，
     * 而所有调用点（{@code MuzzleFlashRender}/{@code ShellRender}/{@code ScopeLensRenderer}/
     * {@code ScopeThermalRenderer}/…）都用它「在 shadow pass 里提前 return」，
     * 恒真等于枪口火光、弹壳、手部与镜片整体不画。「是否启用了光影包」请用
     * {@link #isUsingRenderPack()}。
     */
    public static boolean isRenderShadow() {
        if (!OCULUS_MOD && !ANGELICA_MOD) {
            return false;
        }
        Boolean viaState = shadowPassViaState();
        if (viaState != null) {
            return viaState;
        }
        Boolean viaApi = irisApiBoolean(API_RENDERING_SHADOW_PASS);
        return viaApi != null && viaApi;
    }

    /**
     * 光影实现是否安装（Oculus/Iris 或 1.7.10 的 Angelica）。
     *
     * <p>
     * 上游没有这个独立方法（各处直接写 {@code ModList.get().isLoaded(CompatRegistry.OCULUS)}），
     * 移植侧补上是为了让客户端渲染入口不必自己去摸 {@code Loader}。
     * 语义上是「有 Iris 式光影实现」：<b>不要求</b> Iris API 类可加载（只要模组在就 {@code true}）。
     */
    public static boolean isOculusInstalled() {
        return OCULUS_MOD || ANGELICA_MOD;
    }

    /**
     * 对应上游 {@code OculusCompat#isUsingRenderPack()}：是否正在使用光影包。
     *
     * <p>
     * 首选 {@code IrisApi#isShaderPackInUse()}（Oculus 与 Angelica 都提供）；不可用时对 Angelica 回退到
     * {@code AngelicaConfig.enableIris}；都拿不到则 {@code false}（不抛异常）。
     */
    public static boolean isUsingRenderPack() {
        if (!OCULUS_MOD && !ANGELICA_MOD) {
            return false;
        }
        Boolean viaApi = irisApiBoolean(API_SHADER_PACK_IN_USE);
        if (viaApi != null) {
            return viaApi;
        }
        if (ANGELICA_MOD) {
            Boolean enabled = readStaticBoolean(ANGELICA_CONFIG_CLASS, ANGELICA_IRIS_SWITCH);
            return Boolean.TRUE.equals(enabled);
        }
        return false;
    }

    /**
     * 对应上游 {@code OculusCompat#endBatch(MultiBufferSource)}。
     *
     * <p>
     * 上游语义：Iris 第一人称手部 pass 传进来的 {@code UnflushableWrapper} 的 {@code endBatch()} 是空操作，
     * 必须经 {@code IUnflushableWrapper} 取出内部真正的 {@code FullyBufferedMultiBufferSource} 再 flush；
     * 成功接管（返回 {@code true}）时调用方<b>不要</b>再走普通 {@code endBatch}。
     *
     * <p>
     * 1.7.10：{@link MultiBufferSource} 是本工程的 {@code mcshim} 类型，Iris 的
     * {@code FullyBufferedMultiBufferSource} 永远不会是它的实现，因此这里只做「先解包、再判断实例」的
     * 反射流程，实际恒返回 {@code false}（调用方照常走自己的 {@code endBatch}）。
     */
    public static boolean endBatch(MultiBufferSource bufferSource) {
        if (bufferSource == null || !LOADED) {
            return false;
        }
        MultiBufferSource unwrapped = unwrap(bufferSource);
        for (Class<?> fullyBufferedType : fullyBufferedTypes()) {
            if (fullyBufferedType.isInstance(unwrapped)) {
                return invokeNoArg(unwrapped, METHOD_END_BATCH);
            }
        }
        return false;
    }

    /**
     * 对应上游 {@code OculusCompat#getHandBufferSource()}（Iris 手部 pass 正在用的 buffer source）。
     *
     * <p>
     * 上游返回的是 1.20 的 {@code MultiBufferSource}；1.7.10 侧的 {@link MultiBufferSource} 是本工程
     * {@code mcshim} 的独立接口，拿到的对象无法转换，故恒返回 {@code null}。
     * TODO(L2): 若要接 Angelica 的 {@code HandRenderer}，需要为它单独提供一个
     * {@code mcshim.MultiBufferSource} 适配器。
     */
    @Nullable
    public static MultiBufferSource getHandBufferSource() {
        return null;
    }

    /** 若 {@code bufferSource} 是 Iris 的 {@code UnflushableWrapper}，取出内部真正的 buffer source。 */
    private static MultiBufferSource unwrap(MultiBufferSource bufferSource) {
        if (bufferSource instanceof IUnflushableWrapper) {
            MultiBufferSource wrapped = ((IUnflushableWrapper) bufferSource).tacz$getWrapped();
            return wrapped != null ? wrapped : bufferSource;
        }
        return bufferSource;
    }

    /**
     * {@code ShadowRenderingState#areShadowsCurrentlyBeingRendered()} 的缓存反射调用。
     *
     * @return {@code null} 表示该类/方法不可用（调用方再试 Iris API）；否则为真实判定值
     */
    @Nullable
    private static Boolean shadowPassViaState() {
        if (!shadowPassResolved) {
            shadowPassResolved = true;
            for (String className : SHADOW_STATE_CLASSES) {
                Class<?> type = load(className);
                if (type == null) {
                    continue;
                }
                shadowPassMethod = findMethod(type, STATE_SHADOWS_BEING_RENDERED);
                break;
            }
        }
        if (shadowPassMethod == null) {
            return null;
        }
        try {
            Object value = shadowPassMethod.invoke(null);
            return value instanceof Boolean ? (Boolean) value : Boolean.FALSE;
        } catch (Throwable ignored) {
            // 约束变化 / 链接错误：本次当作不可用
            shadowPassMethod = null;
            return null;
        }
    }

    /**
     * {@code IrisApi.getInstance().<method>()} 的缓存反射调用。
     *
     * @return {@code null} 表示 API 或该方法不可用；否则为真实判定值
     */
    @Nullable
    private static Boolean irisApiBoolean(String methodName) {
        if (!irisApiResolved) {
            irisApiResolved = true;
            Class<?> apiType = firstFound(IRIS_API_CLASSES);
            if (apiType != null) {
                try {
                    Method getInstance = findMethod(apiType, "getInstance");
                    irisApiInstance = getInstance != null ? getInstance.invoke(null) : null;
                } catch (Throwable ignored) {
                    irisApiInstance = null;
                }
                if (irisApiInstance != null) {
                    irisApiShaderPackMethod = findMethod(apiType, API_SHADER_PACK_IN_USE);
                    irisApiShadowPassMethod = findMethod(apiType, API_RENDERING_SHADOW_PASS);
                }
            }
        }
        if (irisApiInstance == null) {
            return null;
        }
        Method method = API_RENDERING_SHADOW_PASS.equals(methodName) ? irisApiShadowPassMethod
            : irisApiShaderPackMethod;
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(irisApiInstance);
            return value instanceof Boolean ? (Boolean) value : Boolean.FALSE;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 解析 {@code FullyBufferedMultiBufferSource} 的两套包名（懒解析一次，之后只做 {@code isInstance}）。 */
    private static Class<?>[] fullyBufferedTypes() {
        if (!fullyBufferedResolved) {
            fullyBufferedResolved = true;
            Class<?>[] found = new Class<?>[FULLY_BUFFERED_CLASSES.length];
            int count = 0;
            for (String className : FULLY_BUFFERED_CLASSES) {
                Class<?> type = load(className);
                if (type != null) {
                    found[count++] = type;
                }
            }
            Class<?>[] trimmed = new Class<?>[count];
            System.arraycopy(found, 0, trimmed, 0, count);
            fullyBufferedTypes = trimmed;
        }
        return fullyBufferedTypes;
    }

    private static boolean invokeNoArg(Object target, String methodName) {
        try {
            target.getClass()
                .getMethod(methodName)
                .invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 取第一个可加载的类（{@code null} = 一个都没有）。 */
    @Nullable
    private static Class<?> firstFound(String[] classNames) {
        for (String className : classNames) {
            Class<?> type = load(className);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    private static Method findMethod(Class<?> type, String methodName) {
        try {
            return type.getMethod(methodName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 读取静态 boolean 字段（{@code null} = 字段不存在 / 类型不符 / 链接失败）。 */
    @Nullable
    private static Boolean readStaticBoolean(String className, String fieldName) {
        Class<?> type = load(className);
        if (type == null) {
            return null;
        }
        try {
            Field field = type.getField(fieldName);
            Object value = field.get(null);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, OculusCompat.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
